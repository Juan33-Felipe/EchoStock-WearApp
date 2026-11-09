package com.echostock.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AuditDraftTest {

    private fun draft(system: Int, counted: Int) = AuditDraft(
        clientAuditId = "id", sku = "CJA-001", operatorCode = "OP-07", deviceId = "dev",
        systemQuantity = system, countedQuantity = counted, recordedAt = Instant.EPOCH,
    )

    @Test fun `coincide cuando el conteo es igual`() {
        assertEquals(AuditResult.MATCH, draft(50, 50).result)
        assertEquals(0, draft(50, 50).difference)
    }

    @Test fun `discrepancia con diferencia firmada`() {
        val d = draft(50, 48)
        assertEquals(AuditResult.DISCREPANCY, d.result)
        assertEquals(-2, d.difference)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza cantidades negativas`() {
        draft(50, -1)
    }
}
