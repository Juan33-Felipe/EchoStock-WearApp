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
        return joinSpelledLetters(convertLetterNames(withNumbers)).joinToString(" ")
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

    sealed interface VoiceCommand {
        data class Add(val details: String) : VoiceCommand
        data class Delete(val query: String) : VoiceCommand
        data class Search(val query: String) : VoiceCommand
    }

    private val ADD_PREFIXES = setOf("agregar", "agrega", "nuevo", "nueva", "crear", "crea")
    private val DELETE_PREFIXES = setOf("eliminar", "elimina", "borrar", "borra", "quitar", "quita")
    private val KNOWN_UNITS = mapOf(
        "caja" to "cajas", "cajas" to "cajas",
        "rollo" to "rollos", "rollos" to "rollos",
        "par" to "pares", "pares" to "pares",
        "pallet" to "pallets", "pallets" to "pallets", "pale" to "pallets", "pales" to "pallets",
        "paquete" to "paquetes", "paquetes" to "paquetes",
        "bolsa" to "bolsas", "bolsas" to "bolsas",
        "unidad" to "unidades", "unidades" to "unidades",
    )

    /** Detecta si la frase dictada es un comando para agregar, eliminar o buscar un producto. */
    fun parseCommand(text: String): VoiceCommand {
        val tokens = basic(text).split(" ").filter { it.isNotEmpty() }
        val first = tokens.firstOrNull() ?: return VoiceCommand.Search(text)
        val restTokens = tokens.drop(1).let {
            if (it.firstOrNull() == "producto") it.drop(1) else it
        }
        val rest = restTokens.joinToString(" ").trim()
        return when (first) {
            in ADD_PREFIXES -> VoiceCommand.Add(rest)
            in DELETE_PREFIXES -> if (rest.isNotEmpty()) VoiceCommand.Delete(rest) else VoiceCommand.Search(text)
            else -> VoiceCommand.Search(text)
        }
    }

    data class NewProductDraft(
        val name: String,
        val locationCode: String,
        val unit: String,
        val quantity: Int,
    )

    /**
     * Extrae nombre, ubicación, unidad y cantidad de una frase dictada para un nuevo producto.
     * Ej: "guantes azules veinte pares" → NewProductDraft("Guantes azules", "A-01-01", "pares", 20)
     * Ej: "cajas grandes pasillo b dos uno treinta" → NewProductDraft("Cajas grandes", "B-02-01", "cajas", 30)
     */
    fun parseNewProduct(
        rawText: String,
        defaultName: String = "",
        defaultLocation: String = "A-01-01",
        defaultUnit: String = "unidades",
        defaultQuantity: Int = 10,
    ): NewProductDraft {
        val cleaned = parseCommand(rawText).let { cmd ->
            when (cmd) {
                is VoiceCommand.Add -> cmd.details
                else -> basic(rawText)
            }
        }
        val normalizedTokens = convertLetterNames(convertNumbers(basic(cleaned).split(" ").filter { it.isNotEmpty() }))
        if (normalizedTokens.isEmpty()) {
            return NewProductDraft(defaultName, defaultLocation, defaultUnit, defaultQuantity)
        }

        var location = defaultLocation
        var quantity: Int? = null
        var unit: String? = null
        val nameTokens = mutableListOf<String>()

        var i = 0
        while (i < normalizedTokens.size) {
            val t = normalizedTokens[i]
            // Detectar "pasillo X N M" o "ubicacion X N M"
            if (t in setOf("pasillo", "ubicacion") && i + 1 < normalizedTokens.size) {
                val aisle = normalizedTokens.getOrNull(i + 1)?.uppercase()?.takeIf { it.length == 1 && it[0] in 'A'..'Z' }
                val shelf = normalizedTokens.getOrNull(i + 2)?.toIntOrNull()
                val level = normalizedTokens.getOrNull(i + 3)?.toIntOrNull()
                if (aisle != null && shelf != null && level != null) {
                    location = "%s-%02d-%02d".format(aisle, shelf, level)
                    i += 4
                    continue
                } else if (aisle != null && shelf != null) {
                    location = "%s-%02d-01".format(aisle, shelf)
                    i += 3
                    continue
                }
            }
            if (t in setOf("cantidad", "stock") && normalizedTokens.getOrNull(i + 1)?.toIntOrNull() != null) {
                quantity = normalizedTokens[i + 1].toInt()
                i += 2
                continue
            }
            val asInt = t.toIntOrNull()
            // Si hay un número al final (o seguido de una unidad al final), lo tomamos como cantidad inicial
            val isTrailingNumber = asInt != null && (
                i == normalizedTokens.lastIndex ||
                    (i == normalizedTokens.lastIndex - 1 && normalizedTokens.last() in KNOWN_UNITS)
                )
            if (isTrailingNumber && nameTokens.isNotEmpty()) {
                quantity = asInt
                val nextUnit = normalizedTokens.getOrNull(i + 1)?.let { KNOWN_UNITS[it] }
                if (nextUnit != null) {
                    unit = nextUnit
                    i += 2
                } else {
                    i += 1
                }
                continue
            }
            nameTokens += t
            i++
        }

        // Inferir unidad si la primera palabra del nombre es una unidad conocida ("cajas...", "rollos...", "guantes...")
        val inferredUnit = unit
            ?: nameTokens.firstOrNull()?.let { firstWord ->
                when (firstWord) {
                    "guante", "guantes" -> "pares"
                    else -> KNOWN_UNITS[firstWord]
                }
            }
            ?: defaultUnit

        val finalName = nameTokens.joinToString(" ").trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .ifEmpty { defaultName }

        return NewProductDraft(
            name = finalName,
            locationCode = location,
            unit = inferredUnit,
            quantity = quantity ?: defaultQuantity,
        )
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

    /**
     * Nombres de letras tal como los transcribe el reconocedor al deletrear:
     * "c j a" llega como "se jota a".
     */
    private val LETTER_NAMES = mapOf(
        "be" to "b", "ce" to "c", "se" to "c", "efe" to "f", "ge" to "g", "hache" to "h",
        "jota" to "j", "cota" to "j", "ka" to "k", "ele" to "l", "eme" to "m", "ene" to "n",
        "pe" to "p", "cu" to "q", "erre" to "r", "ese" to "s", "te" to "t", "uve" to "v",
        "ve" to "v", "equis" to "x", "ye" to "y", "zeta" to "z",
    )

    private fun isSpelledToken(t: String?): Boolean =
        t != null && (t.length == 1 || t in LETTER_NAMES || t.all { it.isDigit() })

    private fun convertLetterNames(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                // "doble ve" / "uve doble" → w
                t == "doble" && tokens.getOrNull(i + 1) in setOf("ve", "uve") -> { out += "w"; i += 2; continue }
                (t == "uve" || t == "ve") && tokens.getOrNull(i + 1) == "doble" -> { out += "w"; i += 2; continue }
                t in LETTER_NAMES -> out += LETTER_NAMES.getValue(t)
                // "de" es casi siempre la preposición; solo es la letra d dentro de un deletreo.
                t == "de" && (isSpelledToken(tokens.getOrNull(i - 1)) || isSpelledToken(tokens.getOrNull(i + 1))) -> out += "d"
                else -> out += t
            }
            i++
        }
        return out
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
