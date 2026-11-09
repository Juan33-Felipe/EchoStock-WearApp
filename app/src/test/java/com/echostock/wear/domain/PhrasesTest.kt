package com.echostock.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PhrasesTest {

    @Test fun `estado del sistema como pide RF-002`() {
        val p = Product("CJA-001", "Cajas tipo A", "A-01-03", "cajas", 50)
        assertEquals(
            "Cajas tipo A, pasillo A, estante 1, nivel 3. Sistema indica 50 cajas.",
            Phrases.systemStatus(p),
        )
    }

    @Test fun `singular cuando hay una unidad`() {
        val p = Product("PLT-001", "Pallet europeo", "D-10-01", "pallets", 1)
        assertEquals("Pallet europeo, pasillo D, estante 10, nivel 1. Sistema indica 1 pallet.", Phrases.systemStatus(p))
    }

    @Test fun `resultado de discrepancia indica faltantes o sobrantes`() {
        assertEquals("Discrepancia registrada, faltan 2.", Phrases.outcome(AuditResult.DISCREPANCY, -2, SubmitOutcome.Synced))
        assertEquals("Discrepancia registrada, sobran 3.", Phrases.outcome(AuditResult.DISCREPANCY, 3, SubmitOutcome.Synced))
    }

    @Test fun `ubicación con formato inesperado se lee tal cual`() {
        assertEquals("ZONA 4", Phrases.location("ZONA-4"))
    }
}
