package com.echostock.wear.data.remote

import com.echostock.wear.domain.AuditDraft
import com.echostock.wear.domain.MatchedBy
import com.echostock.wear.domain.Product
import com.echostock.wear.domain.SearchHit
import kotlinx.serialization.Serializable

/** DTOs del contrato de API v1 (docs/api-contract.md). */

@Serializable
data class ProductDto(
    val sku: String,
    val name: String,
    val locationCode: String,
    val unit: String,
    val systemQuantity: Int,
    val updatedAt: String? = null,
) {
    fun toDomain() = Product(sku, name, locationCode, unit, systemQuantity)
}

@Serializable
data class CreateProductRequestDto(
    val sku: String? = null,
    val name: String,
    val locationCode: String,
    val unit: String,
    val systemQuantity: Int,
)

@Serializable
data class SearchResultDto(val product: ProductDto, val score: Double, val matchedBy: String) {
    fun toDomain() = SearchHit(
        product = product.toDomain(),
        score = score,
        matchedBy = runCatching { MatchedBy.valueOf(matchedBy) }.getOrDefault(MatchedBy.NAME),
    )
}

@Serializable
data class SearchResponseDto(
    val query: String,
    val normalized: String? = null,
    val results: List<SearchResultDto>,
)

@Serializable
data class PageDto<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

@Serializable
data class AuditRequestDto(
    val clientAuditId: String,
    val sku: String,
    val operatorCode: String,
    val deviceId: String,
    val systemQuantity: Int,
    val countedQuantity: Int,
    val result: String,
    val recordedAt: String,
)

fun AuditDraft.toRequest() = AuditRequestDto(
    clientAuditId = clientAuditId,
    sku = sku,
    operatorCode = operatorCode,
    deviceId = deviceId,
    systemQuantity = systemQuantity,
    countedQuantity = countedQuantity,
    result = result.name,
    recordedAt = recordedAt.toString(),
)

@Serializable
data class AuditResponseDto(
    val id: Long,
    val clientAuditId: String,
    val sku: String,
    val status: String,
    val difference: Int? = null,
    val receivedAt: String? = null,
)

@Serializable
data class BatchRequestDto(val audits: List<AuditRequestDto>)

@Serializable
data class RejectedDto(val clientAuditId: String, val reason: String)

@Serializable
data class BatchResponseDto(
    val accepted: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val rejected: List<RejectedDto> = emptyList(),
)

@Serializable
data class ProblemDto(
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
)
