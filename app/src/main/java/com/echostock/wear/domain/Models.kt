package com.echostock.wear.domain

import java.time.Instant

data class Product(
    val sku: String,
    val name: String,
    val locationCode: String,
    val unit: String,
    val systemQuantity: Int,
)

enum class MatchedBy { SKU, LOCATION, NAME }

data class SearchHit(val product: Product, val score: Double, val matchedBy: MatchedBy)

enum class AuditResult { MATCH, DISCREPANCY }

/** Auditoría tal como la registra el reloj (campos de cliente del contrato). */
data class AuditDraft(
    val clientAuditId: String,
    val sku: String,
    val operatorCode: String,
    val deviceId: String,
    val systemQuantity: Int,
    val countedQuantity: Int,
    val recordedAt: Instant,
) {
    init {
        require(countedQuantity >= 0) { "La cantidad contada no puede ser negativa" }
    }

    val result: AuditResult
        get() = if (countedQuantity == systemQuantity) AuditResult.MATCH else AuditResult.DISCREPANCY

    val difference: Int get() = countedQuantity - systemQuantity
}

/** Dónde quedó una auditoría tras registrarla. */
enum class SubmitOutcome {
    /** Enviada al servidor en tiempo real (RF-005). */
    SYNCED,
    /** Guardada en el reloj; se enviará al recuperar señal (RF-006). */
    QUEUED_OFFLINE,
}
