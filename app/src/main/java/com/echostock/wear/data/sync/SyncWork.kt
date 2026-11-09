package com.echostock.wear.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.echostock.wear.EchoStockApp
import com.echostock.wear.data.SyncScheduler
import java.util.concurrent.TimeUnit

/** Envía la cola offline al servidor en cuanto hay red (RF-006). */
class AuditSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as EchoStockApp).container
        val report = container.audits.syncPending()
        return if (report.needsRetry) Result.retry() else Result.success()
    }
}

/** Mantiene fresco el catálogo local para buscar sin conexión. */
class CatalogRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as EchoStockApp).container.products.refreshCatalog()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

/**
 * Programación de trabajos en segundo plano. Todo va con restricción de red: WorkManager
 * despierta el proceso solo cuando hay conexión, sin sondeos que gasten batería (RNF-005).
 */
class WorkManagerSyncScheduler(context: Context) : SyncScheduler {
    private val workManager = WorkManager.getInstance(context)
    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<AuditSyncWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(AUDIT_SYNC, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleCatalogRefresh() {
        val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .build()
        workManager.enqueueUniquePeriodicWork(CATALOG_REFRESH, ExistingPeriodicWorkPolicy.KEEP, request)
        workManager.enqueueUniqueWork(
            "$CATALOG_REFRESH-now",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CatalogRefreshWorker>().setConstraints(needsNetwork).build(),
        )
    }

    private companion object {
        const val AUDIT_SYNC = "audit-sync"
        const val CATALOG_REFRESH = "catalog-refresh"
    }
}
