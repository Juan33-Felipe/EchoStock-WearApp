package com.echostock.wear.data

import com.echostock.wear.data.local.ProductEntity
import com.echostock.wear.data.remote.EchoStockApi
import com.echostock.wear.domain.MatchedBy
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var cache: FakeProductDao
    private lateinit var repo: ProductRepository

    private fun product(sku: String, name: String, loc: String, qty: Int) =
        """{"sku":"$sku","name":"$name","locationCode":"$loc","unit":"cajas","systemQuantity":$qty,"updatedAt":"2026-11-09T18:30:00Z"}"""

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        cache = FakeProductDao(listOf(ProductEntity("CJA-001", "Cajas tipo A", "A-01-03", "cajas", 50)))
        val api = EchoStockApi.create(server.url("/api/v1/").toString(), "k", debug = false)
        repo = ProductRepository(api, cache, ServerStatus())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `combina las alternativas de voz quedándose con el mejor score`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val q = request.requestUrl!!.queryParameter("q")
                val body = when (q) {
                    "cajas tipo a" -> """{"query":"$q","results":[{"product":${product("CJA-001", "Cajas tipo A", "A-01-03", 50)},"score":0.97,"matchedBy":"NAME"}]}"""
                    else -> """{"query":"$q","results":[{"product":${product("CJB-002", "Cajas tipo B", "A-01-04", 30)},"score":0.6,"matchedBy":"NAME"}]}"""
                }
                return MockResponse().setBody(body)
            }
        }

        val outcome = repo.search(listOf("cajas tipo b", "cajas tipo a"))

        assertFalse(outcome.offline)
        assertEquals(listOf("CJA-001", "CJB-002"), outcome.hits.map { it.product.sku })
        assertTrue("guarda en caché lo encontrado", cache.rows.containsKey("CJB-002"))
    }

    @Test fun `sin servidor busca en el catálogo local`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val outcome = repo.search(listOf("c j a cero cero uno"))

        assertTrue(outcome.offline)
        assertEquals("CJA-001", outcome.hits.single().product.sku)
        assertEquals(MatchedBy.SKU, outcome.hits.single().matchedBy)
    }

    @Test fun `descarga todas las páginas del catálogo`() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[${product("P-1", "Uno", "A-01-01", 1)}],"page":0,"size":1,"totalElements":2,"totalPages":2}"""))
        server.enqueue(MockResponse().setBody("""{"content":[${product("P-2", "Dos", "A-01-02", 2)}],"page":1,"size":1,"totalElements":2,"totalPages":2}"""))

        repo.refreshCatalog()

        assertEquals(setOf("P-1", "P-2"), cache.rows.keys)
    }
}
