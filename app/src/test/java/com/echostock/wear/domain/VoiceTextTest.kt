package com.echostock.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTextTest {

    @Test fun `quita tildes, mayúsculas y signos`() {
        assertEquals("cajas tipo a", VoiceText.basic("  Cajás, TIPO-A! "))
    }

    @Test fun `deletreo de SKU se compacta igual que el SKU`() {
        assertEquals("cja001", VoiceText.compact("c j a cero cero uno"))
        assertEquals("cja001", VoiceText.compact("CJA-001"))
    }

    @Test fun `ubicación dictada coincide por segmentos`() {
        assertEquals("a|1|3", VoiceText.segmentKey("A-01-03"))
        assertEquals("a|1|3", VoiceText.segmentKey("a uno tres"))
        assertEquals("a|1|3", VoiceText.segmentKey("a 1 3"))
        assertEquals("a|1|3", VoiceText.segmentKey("pasillo a uno tres"))
    }

    @Test fun `ignora palabras de relleno al dictar un código`() {
        assertEquals("cja001", VoiceText.compact("sku c j a cero cero uno"))
        assertEquals("cja001", VoiceText.compact("código c j a guion cero cero uno"))
    }

    @Test fun `números compuestos`() {
        assertEquals(48, VoiceText.parseQuantity("cuarenta y ocho"))
        assertEquals(21, VoiceText.parseQuantity("veintiuno"))
        assertEquals(102, VoiceText.parseQuantity("ciento dos"))
        assertEquals(100, VoiceText.parseQuantity("cien"))
        assertEquals(350, VoiceText.parseQuantity("trescientos cincuenta"))
        assertEquals(2500, VoiceText.parseQuantity("dos mil quinientos"))
        assertEquals(0, VoiceText.parseQuantity("cero"))
        assertEquals(48, VoiceText.parseQuantity("48"))
        assertEquals(15, VoiceText.parseQuantity("son quince cajas"))
    }

    @Test fun `unidades consecutivas no se suman`() {
        assertEquals("0 0 1", VoiceText.normalize("cero cero uno"))
        assertEquals("1 3", VoiceText.normalize("uno tres"))
    }

    @Test fun `sin número devuelve null`() {
        assertNull(VoiceText.parseQuantity("no sé"))
    }
}
