package com.echostock.wear.data

import com.echostock.wear.data.local.PendingAuditDao
import com.echostock.wear.data.local.PendingAuditEntity
import com.echostock.wear.data.local.ProductDao
import com.echostock.wear.data.local.ProductEntity
import com.echostock.wear.data.local.QueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePendingAuditDao : PendingAuditDao {
    val rows = MutableStateFlow<Map<String, PendingAuditEntity>>(emptyMap())

    override suspend fun insert(audit: PendingAuditEntity) {
        rows.value = rows.value + (audit.clientAuditId to audit)
    }

    override suspend fun pending(limit: Int) =
        rows.value.values.filter { it.state == QueueState.PENDING }.sortedBy { it.recordedAtEpochMs }.take(limit)

    override fun pendingCount(): Flow<Int> = rows.map { r -> r.values.count { it.state == QueueState.PENDING } }
    override fun failedCount(): Flow<Int> = rows.map { r -> r.values.count { it.state == QueueState.FAILED } }

    override suspend fun delete(ids: List<String>) {
        rows.value = rows.value - ids.toSet()
    }

    override suspend fun markFailed(id: String, reason: String) {
        rows.value[id]?.let { rows.value = rows.value + (id to it.copy(state = QueueState.FAILED, lastError = reason)) }
    }

    override suspend fun recordAttempt(ids: List<String>, reason: String?) {
        ids.forEach { id ->
            rows.value[id]?.let { rows.value = rows.value + (id to it.copy(attempts = it.attempts + 1, lastError = reason)) }
        }
    }
}

class FakeProductDao(initial: List<ProductEntity> = emptyList()) : ProductDao {
    val rows = initial.associateBy { it.sku }.toMutableMap()
    override suspend fun all() = rows.values.toList()
    override suspend fun bySku(sku: String) = rows[sku]
    override suspend fun count() = rows.size
    override suspend fun upsert(products: List<ProductEntity>) { products.forEach { rows[it.sku] = it } }
    override suspend fun clear() = rows.clear()
}
