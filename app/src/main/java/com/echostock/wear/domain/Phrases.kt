package com.echostock.wear.domain

import kotlin.math.abs

/** Frases habladas por el reloj. Cortas: el operario está trabajando, no escuchando un discurso. */
object Phrases {

    /** "A-01-03" → "pasillo A, estante 1, nivel 3". */
    fun location(code: String): String {
        val parts = code.split('-', ' ').filter { it.isNotBlank() }
        if (parts.size != 3) return code.replace("-", " ")
        val (aisle, shelf, level) = parts
        return "pasillo $aisle, estante ${shelf.trimStart('0').ifEmpty { "0" }}, nivel ${level.trimStart('0').ifEmpty { "0" }}"
    }

    /** Etiqueta visual formateada para la interfaz ("Pasillo A · Estante 1 · Nivel 3"). */
    fun locationLabel(code: String): String {
        val parts = code.split('-', ' ').filter { it.isNotBlank() }
        if (parts.size != 3) return code
        val (aisle, shelf, level) = parts
        return "Pasillo $aisle · Estante ${shelf.trimStart('0').ifEmpty { "0" }} · Nivel ${level.trimStart('0').ifEmpty { "0" }}"
    }

    /** Solo el pasillo para listas resumidas ("Pasillo A"). */
    fun aisleOnly(code: String): String {
        val parts = code.split('-', ' ').filter { it.isNotBlank() }
        return if (parts.isNotEmpty()) "Pasillo ${parts[0]}" else code
    }

    fun multipleResults(query: String, count: Int, firstLocation: String): String =
        "Encontré $count resultados para $query. El primero en ${location(firstLocation)}."

    /** RF-002: "Cajas tipo A, pasillo A, estante 1, nivel 3. Sistema indica 50 cajas." */
    fun systemStatus(p: Product): String =
        "${p.name}, ${location(p.locationCode)}. Sistema indica ${p.systemQuantity} ${unit(p.unit, p.systemQuantity)}."

    fun outcome(result: AuditResult, difference: Int, outcome: SubmitOutcome): String = when (outcome) {
        is SubmitOutcome.Rejected -> "No se pudo registrar. ${outcome.reason}"
        SubmitOutcome.QueuedOffline -> "Guardado en el reloj. Se enviará al recuperar señal."
        SubmitOutcome.Synced -> when (result) {
            AuditResult.MATCH -> "Conteo confirmado."
            AuditResult.DISCREPANCY ->
                "Discrepancia registrada, ${if (difference > 0) "sobran" else "faltan"} ${abs(difference)}."
        }
    }

    const val NOT_UNDERSTOOD = "No entendí. Intenta de nuevo."

    fun notFound(query: String) = "No encontré $query."

    fun countSet(n: Int) = "$n."

    const val PROMPT_NEW_PRODUCT = "Dicta el nombre y la cantidad del producto."

    fun productAdded(p: Product) = "${p.name} agregado con ${p.systemQuantity} ${unit(p.unit, p.systemQuantity)}."

    fun productDeleted(name: String) = "$name eliminado."

    fun confirmDelete(name: String) = "¿Eliminar $name?"

    private fun unit(unit: String, quantity: Int): String =
        if (quantity == 1 && unit.endsWith("s") && unit.length > 2) unit.dropLast(if (unit.endsWith("es")) 2 else 1) else unit
}
