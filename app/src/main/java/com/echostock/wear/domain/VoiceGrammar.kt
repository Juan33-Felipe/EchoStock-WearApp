package com.echostock.wear.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Vocabulario cerrado para el reconocedor offline (RNF-004). Limitar las palabras posibles a
 * las del catálogo, números y letras hace que el ruido del almacén no se convierta en palabras
 * inventadas: el reconocedor solo puede "oír" cosas que existen en el inventario.
 * "[unk]" absorbe lo que no encaja (voces de fondo, maquinaria).
 */
object VoiceGrammar {

    // Con tildes: así están en el vocabulario del modelo; el normalizador las quita después.
    private val NUMBER_WORDS = listOf(
        "cero", "un", "uno", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho",
        "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco",
        "veintiséis", "veintisiete", "veintiocho", "veintinueve", "treinta", "cuarenta", "cincuenta",
        "sesenta", "setenta", "ochenta", "noventa", "cien", "ciento", "doscientos", "trescientos",
        "cuatrocientos", "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos",
        "mil", "y",
    )

    private val LETTER_WORDS = listOf(
        "a", "be", "ce", "se", "de", "e", "efe", "ge", "hache", "i", "jota", "ka", "ele", "eme", "ene",
        "o", "pe", "cu", "erre", "ese", "te", "u", "uve", "doble", "equis", "ye", "zeta",
    )

    private val FILLER_WORDS = listOf("pasillo", "estante", "nivel", "ubicación", "sku", "código", "producto", "guion")

    private const val UNKNOWN = "[unk]"

    /** Gramática para buscar productos: palabras del catálogo + números + letras + relleno. */
    fun forSearch(catalog: List<Product>): String? {
        if (catalog.isEmpty()) return null // sin catálogo local: dictado libre
        val catalogWords = catalog.flatMap { words(it.name) + words(it.unit) }
        return toJson(catalogWords + NUMBER_WORDS + LETTER_WORDS + FILLER_WORDS)
    }

    /** Gramática para dictar una cantidad: solo números. */
    fun forQuantity(): String = toJson(NUMBER_WORDS)

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.length > 1 || it.firstOrNull()?.isLetter() == true }

    private fun toJson(words: List<String>): String =
        JsonArray((words.distinct().sorted() + UNKNOWN).map { JsonPrimitive(it) }).toString()

    /**
     * Interpreta el JSON de resultado de Vosk:
     * `{"alternatives":[{"text":"...","confidence":..}]}` o `{"text":"..."}`.
     * Descarta hipótesis vacías y las que solo contienen "[unk]".
     */
    fun parseResult(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyList()
        val texts = obj["alternatives"]?.jsonArray?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?: listOfNotNull(obj["text"]?.jsonPrimitive?.content)
        return texts.map { it.replace(UNKNOWN, "").trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun parsePartial(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return runCatching { Json.parseToJsonElement(json).jsonObject["partial"]?.jsonPrimitive?.content }
            .getOrNull().orEmpty().replace(UNKNOWN, "").trim()
    }
}
