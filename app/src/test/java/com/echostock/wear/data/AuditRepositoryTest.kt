package com.echostock.wear.data

import com.echostock.wear.data.local.QueueState
import com.echostock.wear.data.remote.EchoStockApi
import com.echostock.wear.domain.AuditDraft
import com.echostock.wear.domain.Connection
import com.echostock.wear.domain.SubmitOutcome
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AuditRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var queue: FakePendingAuditDao
    private lateinit var status: ServerStatus
    private var syncRequests = 0
    private lateinit var repo: AuditRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        queue = FakePendingAuditDao()
        status = ServerStatus()
        syncRequests = 0
        val api = EchoStockApi.create(server.url("/api/v1/").toString(), "test-key", debug = false)
        repo = AuditRepository(api, queue, { syncRequests++ }, status)
    }

    @After fun tearDown() = server.shutdown()

    private fun draft(id: String, counted: Int = 48, at: Long = 0) = AuditDraft(
        clientAuditId = id, sku = "CJA-001", operatorCode = "OP-07", deviceId = "wear-test",
        systemQuantity = 50, countedQuantity = counted, recordedAt = Instant.ofEpochMilli(at),
    )

    private fun auditJson(id: String) =
        """{"id":1,"clientAuditId":"$id","sku":"CJA-001","status":"PENDING_REVIEW","difference":-2,"receivedAt":"2026-11-09T18:35:00Z"}"""

    @Test fun `envío en tiempo real vacía la cola y respeta el contrato`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(auditJson("a1")))

        val outcome = repo.submit(draft("a1"))

        assertEquals(SubmitOutcome.Synced, outcome)
        assertTrue(queue.rows.value.isEmpty())
        assertEquals(Connection.ONLINE, status.connection.value)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/audits", request.path)
        assertEquals("test-key", request.getHeader("X-Api-Key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("a1", body["clientAuditId"]!!.jsonPrimitive.content)
        assertEquals("DISCREPANCY", body["result"]!!.jsonPrimitive.content)
        assertEquals(48, body["countedQuantity"]!!.jsonPrimitive.int)
        assertEquals("1970-01-01T00:00:00Z", body["recordedAt"]!!.jsonPrimitive.content)
    }

    @Test fun `200 por duplicado también cuenta como enviada`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(auditJson("a1")))
        assertEquals(SubmitOutcome.Synced, repo.submit(draft("a1")))
        assertTrue(queue.rows.value.isEmpty())
    }

    @Test fun `sin servidor queda en cola y pide sincronizar`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val outcome = repo.submit(draft("a1"))

        assertEquals(SubmitOutcome.QueuedOffline, outcome)
        assertEquals(QueueState.PENDING, queue.rows.value.getValue("a1").state)
        assertEquals(1, syncRequests)
        assertEquals(Connection.OFFLINE, status.connection.value)
    }

    @Test fun `error 503 se trata como offline`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(SubmitOutcome.QueuedOffline, repo.submit(draft("a1")))
        assertEquals(1, queue.rows.value.size)
    }

    @Test fun `rechazo 404 se marca fallida con el detalle del servidor`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"title":"No encontrado","status":404,"detail":"SKU CJA-001 no existe"}"""),
        )

        val outcome = repo.submit(draft("a1"))

        assertEquals(SubmitOutcome.Rejected("SKU CJA-001 no existe"), outcome)
        assertEquals(QueueState.FAILED, queue.rows.value.getValue("a1").state)
        assertEquals(0, syncRequests)
    }

    @Test fun `sincronización por lotes procesa aceptadas, duplicadas y rechazadas`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        repo.submit(draft("a1", at = 1))
        repo.submit(draft("a2", at = 2))
        repo.submit(draft("a3", at = 3))
        repeat(3) { server.takeRequest() }

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"accepted":["a1"],"duplicates":["a2"],"rejected":[{"clientAuditId":"a3","reason":"SKU no encontrado"}]}""",
            ),
        )

        val report = repo.syncPending()

        assertEquals(SyncReport(sent = 2, rejected = 1, needsRetry = false), report)
        assertEquals(setOf("a3"), queue.rows.value.keys)
        assertEquals("SKU no encontrado", queue.rows.value.getValue("a3").lastError)

        val batch = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val ids = batch["audits"]!!.jsonArray.map { it.jsonObject["clientAuditId"]!!.jsonPrimitive.content }
        assertEquals(listOf("a1", "a2", "a3"), ids)
    }

    @Test fun `sincronización sin red pide reintentar y no pierde nada`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        repo.submit(draft("a1"))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val report = repo.syncPending()

        assertTrue(report.needsRetry)
        assertEquals(QueueState.PENDING, queue.rows.value.getValue("a1").state)
    }

    @Test fun `cola vacía no llama al servidor`() = runTest {
        assertEquals(SyncReport(0, 0, false), repo.syncPending())
        assertEquals(0, server.requestCount)
    }
}
