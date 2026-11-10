package com.echostock.wear.data

import com.echostock.wear.data.local.ProductDao
import com.echostock.wear.data.local.ProductEntity
import com.echostock.wear.data.remote.CreateProductRequestDto
import com.echostock.wear.data.remote.EchoStockApi
import com.echostock.wear.domain.Product
import com.echostock.wear.domain.ProductMatcher
import com.echostock.wear.domain.SearchHit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class SearchOutcome(val hits: List<SearchHit>, val offline: Boolean)

class ProductRepository(
    private val api: EchoStockApi,
    private val cache: ProductDao,
    private val status: ServerStatus,
) {
    /** Cambios locales para garantizar consistencia inmediata incluso sin conexión. */
    private val localCreated = ConcurrentHashMap<String, Product>()
    private val localDeleted = ConcurrentHashMap.newKeySet<String>()

    /**
     * Busca con todas las alternativas que dio el reconocedor de voz (RNF-004: si el ruido
     * deformó la mejor hipótesis, otra suele acertar) y se queda con el mejor score por SKU.
     * Sin servidor, busca en el catálogo cacheado con las mismas reglas.
     */
    suspend fun search(alternatives: List<String>, limit: Int = 5): SearchOutcome {
        val queries = alternatives.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_ALTERNATIVES)
        if (queries.isEmpty()) return SearchOutcome(emptyList(), offline = false)
        return try {
            val lists = coroutineScope {
                queries.map { q ->
                    async {
                        api.search(q, limit).results
                            .map { it.toDomain() }
                            .filterNot { it.product.sku in localDeleted }
                    }
                }.awaitAll()
            }
            status.reachable()
            val remoteHits = ProductMatcher.merge(lists, limit)
            cache.upsert(remoteHits.map { ProductEntity.from(it.product) })

            // Combinar con productos agregados localmente que aún no estén en los resultados remotos
            val localHits = ProductMatcher.search(queries, localCreated.values.toList(), limit)
            val combined = (remoteHits + localHits)
                .filterNot { it.product.sku in localDeleted }
                .groupBy { it.product.sku }
                .map { (_, group) -> group.maxBy { it.score } }
                .sortedByDescending { it.score }
                .take(limit)

            SearchOutcome(combined, offline = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            status.unreachable()
            val localCatalog = (cache.all().map { it.toDomain() } + localCreated.values)
                .distinctBy { it.sku }
                .filterNot { it.sku in localDeleted }
            SearchOutcome(ProductMatcher.search(queries, localCatalog, limit), offline = true)
        }
    }

    /** Catálogo local, para construir el vocabulario del reconocedor offline. */
    suspend fun cached(): List<Product> =
        cache.all().map { it.toDomain() }.filterNot { it.sku in localDeleted }

    /** Lista para elegir sin voz (respaldo si el micrófono no está disponible). */
    suspend fun browse(): List<Product> {
        if (cache.count() == 0) runCatching { refreshCatalog() }
        return cache.all()
            .map { it.toDomain() }
            .filterNot { it.sku in localDeleted }
            .sortedBy { it.locationCode }
    }

    /** Crea un producto en el catálogo local y lo sincroniza con el servidor si está disponible. */
    suspend fun create(
        name: String,
        locationCode: String,
        unit: String,
        systemQuantity: Int,
        customSku: String? = null,
    ): Product {
        val cleanName = name.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
        val cleanLocation = locationCode.trim().uppercase(Locale.ROOT).ifEmpty { "A-01-01" }
        val cleanUnit = unit.trim().lowercase(Locale.ROOT).ifEmpty { "unidades" }
        val cleanQty = systemQuantity.coerceAtLeast(0)
        val existingSkus = cache.all().map { it.sku }.toSet() + localCreated.keys
        val sku = customSku?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            ?: generateSku(cleanName, existingSkus)

        val localProduct = Product(
            sku = sku,
            name = cleanName,
            locationCode = cleanLocation,
            unit = cleanUnit,
            systemQuantity = cleanQty,
        )
        localDeleted.remove(sku)
        localCreated[sku] = localProduct
        cache.upsert(listOf(ProductEntity.from(localProduct)))

        return try {
            val saved = api.createProduct(
                CreateProductRequestDto(
                    sku = sku,
                    name = cleanName,
                    locationCode = cleanLocation,
                    unit = cleanUnit,
                    systemQuantity = cleanQty,
                ),
            ).toDomain()
            status.reachable()
            localCreated.remove(sku)
            localCreated[saved.sku] = saved
            cache.upsert(listOf(ProductEntity.from(saved)))
            saved
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            status.unreachable()
            localProduct
        }
    }

    /** Elimina un producto por su SKU tanto de la caché local como del servidor. */
    suspend fun delete(sku: String) {
        val cleanSku = sku.trim().uppercase(Locale.ROOT)
        localCreated.remove(cleanSku)
        localDeleted.add(cleanSku)
        cache.deleteBySku(cleanSku)
        try {
            val response = api.deleteProduct(cleanSku)
            if (response.isSuccessful) {
                localDeleted.remove(cleanSku)
                status.reachable()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            status.unreachable()
        }
    }

    /** Sincroniza con el servidor los productos creados o eliminados mientras estaba sin conexión. */
    suspend fun syncLocalChanges() {
        for (sku in localDeleted.toList()) {
            val resp = runCatching { api.deleteProduct(sku) }.getOrNull()
            if (resp != null && (resp.isSuccessful || resp.code() == 404)) {
                localDeleted.remove(sku)
            }
        }
        for ((sku, product) in localCreated.entries.toList()) {
            val saved = runCatching {
                api.createProduct(
                    CreateProductRequestDto(
                        sku = product.sku,
                        name = product.name,
                        locationCode = product.locationCode,
                        unit = product.unit,
                        systemQuantity = product.systemQuantity,
                    ),
                ).toDomain()
            }.getOrNull()
            if (saved != null) {
                localCreated.remove(sku)
                cache.upsert(listOf(ProductEntity.from(saved)))
            }
        }
    }

    /** Descarga el catálogo completo para poder trabajar sin conexión. */
    suspend fun refreshCatalog() {
        runCatching { syncLocalChanges() }
        val all = mutableListOf<ProductEntity>()
        var page = 0
        do {
            val result = api.products(page)
            all += result.content
                .filterNot { it.sku in localDeleted }
                .map { ProductEntity.from(it.toDomain()) }
            page++
        } while (page < result.totalPages)
        localCreated.values.forEach { local ->
            if (all.none { it.sku == local.sku }) {
                all += ProductEntity.from(local)
            }
        }
        cache.replaceAll(all)
        status.reachable()
    }

    suspend fun ping(): Boolean = try {
        val ok = api.health().isSuccessful
        if (ok) {
            status.reachable()
            runCatching { syncLocalChanges() }
        } else {
            status.unreachable()
        }
        ok
    } catch (e: Exception) {
        status.unreachable()
        false
    }

    private fun generateSku(name: String, existingSkus: Set<String>): String {
        val normalized = Normalizer.normalize(name.uppercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Z0-9]"), "")
        val consonants = normalized.replace(Regex("[AEIOU0-9]"), "")
        val prefix = (if (consonants.length >= 3) consonants else (normalized + "PRD")).take(3)
        for (i in 1..999) {
            val candidate = "%s-%03d".format(Locale.ROOT, prefix, i)
            if (candidate !in existingSkus) return candidate
        }
        return "$prefix-${System.currentTimeMillis() % 1000}"
    }

    private companion object {
        const val MAX_ALTERNATIVES = 3
    }
}
