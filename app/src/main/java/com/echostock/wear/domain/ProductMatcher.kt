package com.echostock.wear.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Búsqueda local tolerante a voz sobre el catálogo cacheado. Se usa cuando no hay
 * conexión (RF-006) y sigue el mismo ranking que el servidor:
 * SKU exacto > ubicación exacta > similitud de nombre.
 */
object ProductMatcher {

    private const val MIN_SCORE = 0.35

    fun search(queries: List<String>, catalog: List<Product>, limit: Int = 5): List<SearchHit> {
        val best = mutableMapOf<String, SearchHit>()
        for (q in queries.filter { it.isNotBlank() }) {
            for (hit in searchOne(q, catalog)) {
                val current = best[hit.product.sku]
                if (current == null || hit.score > current.score) best[hit.product.sku] = hit
            }
        }
        return best.values.sortedByDescending { it.score }.take(limit)
    }

    /** Une resultados de varias alternativas del reconocedor quedándose con el mejor score por SKU. */
    fun merge(hitLists: List<List<SearchHit>>, limit: Int = 5): List<SearchHit> =
        hitLists.flatten()
            .groupBy { it.product.sku }
            .map { (_, hits) -> hits.maxBy { it.score } }
            .sortedByDescending { it.score }
            .take(limit)

    private fun searchOne(query: String, catalog: List<Product>): List<SearchHit> {
        val qCompact = VoiceText.compact(query)
        val qSegments = VoiceText.segmentKey(query)
        val qTokens = VoiceText.normalize(query).split(" ").filter { it.isNotEmpty() }.toSet()
        if (qCompact.isEmpty()) return emptyList()

        return catalog.mapNotNull { p ->
            when {
                VoiceText.compact(p.sku) == qCompact -> SearchHit(p, 1.0, MatchedBy.SKU)
                VoiceText.segmentKey(p.locationCode) == qSegments -> SearchHit(p, 0.9, MatchedBy.LOCATION)
                else -> {
                    val s = nameScore(qTokens, qCompact, p.name)
                    if (s >= MIN_SCORE) SearchHit(p, s, MatchedBy.NAME) else null
                }
            }
        }
    }

    private fun nameScore(qTokens: Set<String>, qCompact: String, name: String): Double {
        val nTokens = VoiceText.normalize(name).split(" ").filter { it.isNotEmpty() }.toSet()
        if (nTokens.isEmpty()) return 0.0
        // Cada token de la consulta cuenta si se parece a algún token del nombre (tolera erratas del reconocedor).
        val tokenHits = qTokens.sumOf { qt -> nTokens.maxOf { similarity(qt, it) } }
        val overlap = tokenHits / max(qTokens.size, nTokens.size)
        val whole = similarity(qCompact, VoiceText.compact(name))
        return (0.6 * overlap + 0.4 * whole) * 0.85
    }

    /** 1 - distancia de Levenshtein normalizada. */
    internal fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = curr; curr = t
        }
        return 1.0 - prev[b.length].toDouble() / max(a.length, b.length)
    }
}
