package com.echostock.wear.domain

import java.text.Normalizer

/**
 * Normaliza lo que entiende el reconocedor de voz para poder compararlo con nombres,
 * SKUs y códigos de ubicación. Replica las reglas del servidor (ver docs/api-contract.md)
 * para que la búsqueda offline se comporte igual que la online.
 */
object VoiceText {

    private val UNITS = mapOf(
        "cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
    )
    private val TEENS = mapOf(
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20, "veintiun" to 21, "veintiuno" to 21, "veintiuna" to 21, "veintidos" to 22,
        "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
    )
    private val TENS = mapOf(
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90,
    )
    private val HUNDREDS = mapOf(
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "doscientas" to 200,
        "trescientos" to 300, "trescientas" to 300, "cuatrocientos" to 400, "cuatrocientas" to 400,
        "quinientos" to 500, "quinientas" to 500, "seiscientos" to 600, "seiscientas" to 600,
        "setecientos" to 700, "setecientas" to 700, "ochocientos" to 800, "ochocientas" to 800,
        "novecientos" to 900, "novecientas" to 900,
    )

    /** Minúsculas, sin tildes, solo letras/dígitos/espacios, espacios colapsados. */
    fun basic(text: String): String {
        val noAccents = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return noAccents.replace(Regex("[^a-z0-9ñ]+"), " ").trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Normalización completa: [basic], números en palabras → dígitos y letras sueltas
     * deletreadas unidas ("c j a cero cero uno" → "cja 0 0 1").
     */
    fun normalize(text: String): String {
        val tokens = basic(text).split(" ").filter { it.isNotEmpty() }
        val withNumbers = convertNumbers(tokens)
        return joinSpelledLetters(withNumbers).joinToString(" ")
    }

    /** Palabras que el operario antepone a un código y no forman parte de él. */
    private val CODE_FILLERS = setOf(
        "pasillo", "ubicacion", "estante", "nivel", "rack", "sku", "codigo", "referencia",
        "ref", "producto", "numero", "guion",
    )

    private fun withoutFillers(text: String): String =
        basic(text).split(" ").filterNot { it in CODE_FILLERS }.joinToString(" ")

    /** Solo letras y dígitos: sirve para comparar SKUs ("CJA-001" ≡ "sku c j a cero cero uno"). */
    fun compact(text: String): String = normalize(withoutFillers(text)).replace(" ", "")

    /**
     * Clave por segmentos para códigos de ubicación: grupos de letras y de dígitos,
     * dígitos sin ceros a la izquierda. "A-01-03" → "a|1|3" y "pasillo a uno tres" → "a|1|3".
     */
    fun segmentKey(text: String): String {
        val groups = Regex("[a-zñ]+|[0-9]+").findAll(normalize(withoutFillers(text))).map { it.value }.toList()
        return groups.joinToString("|") { g -> if (g[0].isDigit()) g.trimStart('0').ifEmpty { "0" } else g }
    }

    /** Interpreta una cantidad dictada: "cuarenta y ocho" → 48, "48" → 48, "ciento dos" → 102. */
    fun parseQuantity(text: String): Int? {
        val numbers = normalize(text).split(" ").mapNotNull { it.toIntOrNull() }
        return numbers.firstOrNull()
    }

    private fun convertNumbers(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val parsed = parseNumberAt(tokens, i)
            if (parsed == null) {
                out += tokens[i]
                i++
            } else {
                out += parsed.first.toString()
                i = parsed.second
            }
        }
        return out
    }

    /** Lee un número compuesto desde [start]. Devuelve (valor, índice siguiente) o null. */
    private fun parseNumberAt(tokens: List<String>, start: Int): Pair<Int, Int>? {
        var i = start
        var total = 0
        var consumed = false

        fun peek(offset: Int = 0) = tokens.getOrNull(i + offset)

        // [unidades] mil
        val unitBeforeMil = peek()?.let { UNITS[it] ?: TEENS[it] ?: TENS[it] }
        if (peek() == "mil") {
            total = 1000; i++; consumed = true
        } else if (unitBeforeMil != null && peek(1) == "mil") {
            total = unitBeforeMil * 1000; i += 2; consumed = true
        }
        // centenas
        HUNDREDS[peek()]?.let { total += it; i++; consumed = true }
        // decenas [y unidades] | 10-29 | unidades
        val tens = TENS[peek()]
        val teen = TEENS[peek()]
        val unit = UNITS[peek()]
        when {
            tens != null -> {
                total += tens; i++; consumed = true
                if (peek() == "y" && UNITS[peek(1)] != null && peek(1) != "cero") {
                    total += UNITS.getValue(peek(1)!!); i += 2
                }
            }
            teen != null -> { total += teen; i++; consumed = true }
            unit != null && !(consumed && unit == 0) -> { total += unit; i++; consumed = true }
        }
        return if (consumed) total to i else null
    }

    /** Une letras sueltas consecutivas ("c j a" → "cja"), típico al deletrear un SKU. */
    private fun joinSpelledLetters(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) { out += buffer.toString(); buffer.clear() }
        }
        for (t in tokens) {
            if (t.length == 1 && t[0].isLetter()) buffer.append(t) else { flush(); out += t }
        }
        flush()
        return out
    }
}
