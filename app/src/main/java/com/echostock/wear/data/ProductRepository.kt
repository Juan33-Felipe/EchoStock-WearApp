package com.echostock.wear.data

import com.echostock.wear.data.local.ProductDao
import com.echostock.wear.data.local.ProductEntity
import com.echostock.wear.data.remote.EchoStockApi
import com.echostock.wear.domain.Product
import com.echostock.wear.domain.ProductMatcher
import com.echostock.wear.domain.SearchHit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SearchOutcome(val hits: List<SearchHit>, val offline: Boolean)

class ProductRepository(
    private val api: EchoStockApi,
    private val cache: ProductDao,
    private val status: ServerStatus,
) {
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
                queries.map { q -> async { api.search(q, limit).results.map { it.toDomain() } } }.awaitAll()
            }
            status.reachable()
            val hits = ProductMatcher.merge(lists, limit)
            cache.upsert(hits.map { ProductEntity.from(it.product) })
            SearchOutcome(hits, offline = false)
        } catch (e: Exception) {
            if (!e.isTransient()) throw e
            status.unreachable()
            SearchOutcome(ProductMatcher.search(queries, cache.all().map { it.toDomain() }, limit), offline = true)
        }
    }

    /** Lista para elegir sin voz (respaldo si el micrófono no está disponible). */
    suspend fun browse(): List<Product> {
        if (cache.count() == 0) runCatching { refreshCatalog() }
        return cache.all().map { it.toDomain() }.sortedBy { it.locationCode }
    }

    /** Descarga el catálogo completo para poder trabajar sin conexión. */
    suspend fun refreshCatalog() {
        val all = mutableListOf<ProductEntity>()
        var page = 0
        do {
            val result = api.products(page)
            all += result.content.map { ProductEntity.from(it.toDomain()) }
            page++
        } while (page < result.totalPages)
        cache.replaceAll(all)
        status.reachable()
    }

    suspend fun ping(): Boolean = try {
        api.health().isSuccessful.also { if (it) status.reachable() else status.unreachable() }
    } catch (e: Exception) {
        status.unreachable()
        false
    }

    private companion object {
        const val MAX_ALTERNATIVES = 3
    }
}
