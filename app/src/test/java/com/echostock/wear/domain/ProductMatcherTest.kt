package com.echostock.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMatcherTest {

    private val catalog = listOf(
        Product("CJA-001", "Cajas tipo A", "A-01-03", "cajas", 50),
        Product("CJB-002", "Cajas tipo B", "A-01-04", "cajas", 30),
        Product("FLM-010", "Rollos de film estirable", "B-02-01", "rollos", 12),
        Product("GNT-005", "Guantes de nitrilo talla M", "C-03-02", "pares", 200),
    )

    @Test fun `nombre dictado`() {
        val hits = ProductMatcher.search(listOf("cajas tipo a"), catalog)
        assertEquals("CJA-001", hits.first().product.sku)
        assertEquals(MatchedBy.NAME, hits.first().matchedBy)
    }

    @Test fun `SKU deletreado tiene prioridad máxima`() {
        val hit = ProductMatcher.search(listOf("c j a cero cero uno"), catalog).first()
        assertEquals("CJA-001", hit.product.sku)
        assertEquals(MatchedBy.SKU, hit.matchedBy)
        assertEquals(1.0, hit.score, 0.0)
    }

    @Test fun `ubicación dictada`() {
        val hit = ProductMatcher.search(listOf("a uno tres"), catalog).first()
        assertEquals("CJA-001", hit.product.sku)
        assertEquals(MatchedBy.LOCATION, hit.matchedBy)
    }

    @Test fun `tolera erratas del reconocedor`() {
        val hit = ProductMatcher.search(listOf("guante de nitrilo"), catalog).first()
        assertEquals("GNT-005", hit.product.sku)
    }

    @Test fun `usa la mejor de varias alternativas`() {
        val hits = ProductMatcher.search(listOf("rodillos de filme", "rollos de film"), catalog)
        assertEquals("FLM-010", hits.first().product.sku)
    }

    @Test fun `consulta sin relación no devuelve nada`() {
        assertTrue(ProductMatcher.search(listOf("helicóptero"), catalog).isEmpty())
    }

    @Test fun `merge conserva el mejor score por SKU`() {
        val p = catalog[0]
        val merged = ProductMatcher.merge(
            listOf(
                listOf(SearchHit(p, 0.5, MatchedBy.NAME)),
                listOf(SearchHit(p, 0.9, MatchedBy.LOCATION)),
            ),
        )
        assertEquals(1, merged.size)
        assertEquals(0.9, merged[0].score, 0.0)
    }
}
