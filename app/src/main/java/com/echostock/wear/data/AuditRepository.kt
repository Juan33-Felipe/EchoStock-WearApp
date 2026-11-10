package com.echostock.wear.data

import com.echostock.wear.data.local.PendingAuditDao
import com.echostock.wear.data.local.PendingAuditEntity
import com.echostock.wear.data.remote.BatchRequestDto
import com.echostock.wear.data.remote.EchoStockApi
import com.echostock.wear.data.remote.ProblemDto
import com.echostock.wear.data.remote.toRequest
import com.echostock.wear.domain.AuditDraft
import com.echostock.wear.domain.SubmitOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

fun interface SyncScheduler {
    fun requestSync()
}

data class SyncReport(val sent: Int, val rejected: Int, val needsRetry: Boolean)

/**
 * Registro de auditorías con garantía de no perder datos:
 * 1. Se guarda primero en la cola local (Room).
 * 2. Se intenta enviar en tiempo real (RF-005).
 * 3. Si no hay servidor, queda en cola y WorkManager la envía al volver la red (RF-006).
 * El servidor es idempotente por clientAuditId, así que reenviar nunca duplica.
 */
class AuditRepository(
    private val api: EchoStockApi,
    private val queue: PendingAuditDao,
    private val scheduler: SyncScheduler,
    private val status: ServerStatus,
) {
    private val syncLock = Mutex()

    val pendingCount: Flow<Int> = queue.pendingCount()
    val failedCount: Flow<Int> = queue.failedCount()

    suspend fun submit(draft: AuditDraft): SubmitOutcome {
        queue.insert(PendingAuditEntity.from(draft))
        return try {
            val response = api.submit(draft.toRequest())
            when {
                response.isSuccessful -> {
                    status.reachable()
                    queue.delete(listOf(draft.clientAuditId))
                    SubmitOutcome.Synced
                }
                response.code() in REJECTION_CODES -> {
                    status.reachable()
                    val reason = problemDetail(response.errorBody()?.string()) ?: "Rechazada (${response.code()})"
                    queue.markFailed(draft.clientAuditId, reason)
                    SubmitOutcome.Rejected(reason)
                }
                else -> {
                    status.unreachable()
                    queued(draft, "HTTP ${response.code()}")
                }
            }
        } catch (e: Exception) {
            if (e.isTransient()) {
                status.unreachable()
                queued(draft, e.message)
            } else {
                throw e
            }
        }
    }

    /** Envía la cola en lotes. Devuelve si conviene reintentar más tarde. */
    suspend fun syncPending(batchSize: Int = 50): SyncReport = syncLock.withLock {
        var sent = 0
        var rejected = 0
        while (true) {
            val batch = queue.pending(batchSize)
            if (batch.isEmpty()) return SyncReport(sent, rejected, needsRetry = false)

            val ids = batch.map { it.clientAuditId }
            val response = try {
                api.submitBatch(BatchRequestDto(batch.map { it.toDraft().toRequest() }))
            } catch (e: Exception) {
                queue.recordAttempt(ids, e.message)
                if (e.isTransient()) {
                    status.unreachable()
                    return SyncReport(sent, rejected, needsRetry = true)
                }
                // Error permanente del lote (4xx): se marcan para no bloquear la cola para siempre.
                val reason = (e as? HttpException)?.let { "HTTP ${it.code()}" } ?: e.javaClass.simpleName
                ids.forEach { queue.markFailed(it, reason) }
                return SyncReport(sent, rejected + ids.size, needsRetry = false)
            }
            status.reachable()

            val done = response.accepted + response.duplicates
            if (done.isNotEmpty()) queue.delete(done)
            response.rejected.forEach { queue.markFailed(it.clientAuditId, it.reason) }
            sent += done.size
            rejected += response.rejected.size

            val answered = done.toSet() + response.rejected.map { it.clientAuditId }
            val unanswered = ids.filterNot { it in answered }
            if (unanswered.isNotEmpty()) {
                // El servidor no se pronunció sobre algunas: reintentar luego en vez de girar en bucle.
                queue.recordAttempt(unanswered, "Sin respuesta en el lote")
                return SyncReport(sent, rejected, needsRetry = true)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("inalcanzable")
    }

    private suspend fun queued(draft: AuditDraft, reason: String?): SubmitOutcome {
        queue.recordAttempt(listOf(draft.clientAuditId), reason)
        scheduler.requestSync()
        return SubmitOutcome.QueuedOffline
    }

    private fun problemDetail(body: String?): String? = body?.let {
        runCatching { EchoStockApi.json.decodeFromString(ProblemDto.serializer(), it) }
            .getOrNull()?.let { p -> p.detail ?: p.title }
    }

    private companion object {
        val REJECTION_CODES = setOf(400, 404, 409, 422)
    }
}
