package com.echostock.wear.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGrammarTest {

    private fun words(json: String) = Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.content }

    @Test fun `gramática de búsqueda incluye catálogo, números, letras y desconocido`() {
        val g = words(VoiceGrammar.forSearch(listOf(Product("CJA-001", "Cajas tipo A", "A-01-03", "cajas", 50)))!!)
        assertTrue(g.containsAll(listOf("cajas", "tipo", "a", "cuarenta", "jota", "pasillo", "[unk]")))
        assertEquals("sin duplicados", g.size, g.toSet().size)
        assertEquals("[unk]", g.last())
    }

    @Test fun `sin catálogo local se dicta libre`() {
        assertNull(VoiceGrammar.forSearch(emptyList()))
    }

    @Test fun `gramática de cantidad solo tiene números`() {
        val g = words(VoiceGrammar.forQuantity())
        assertTrue("cuarenta" in g && "y" in g && "ocho" in g)
        assertTrue("cajas" !in g)
    }

    @Test fun `lee alternativas y descarta vacías o desconocidas`() {
        val json = """{"alternatives":[{"confidence":1.2,"text":"cajas tipo a"},{"confidence":0.8,"text":"[unk]"},{"confidence":0.1,"text":""}]}"""
        assertEquals(listOf("cajas tipo a"), VoiceGrammar.parseResult(json))
    }

    @Test fun `lee resultado simple y parcial`() {
        assertEquals(listOf("cuarenta y ocho"), VoiceGrammar.parseResult("""{"text":"cuarenta y ocho"}"""))
        assertEquals("cajas", VoiceGrammar.parsePartial("""{"partial":"cajas [unk]"}"""))
        assertEquals(emptyList<String>(), VoiceGrammar.parseResult("no es json"))
    }
}
