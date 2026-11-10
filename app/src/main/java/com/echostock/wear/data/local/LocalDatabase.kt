package com.echostock.wear.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.echostock.wear.domain.AuditDraft
import com.echostock.wear.domain.Product
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Copia local del catálogo para buscar sin conexión. */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val sku: String,
    val name: String,
    val locationCode: String,
    val unit: String,
    val systemQuantity: Int,
) {
    fun toDomain() = Product(sku, name, locationCode, unit, systemQuantity)

    companion object {
        fun from(p: Product) = ProductEntity(p.sku, p.name, p.locationCode, p.unit, p.systemQuantity)
    }
}

enum class QueueState { PENDING, FAILED }

/** Auditoría pendiente de enviar al servidor (RF-006). */
@Entity(tableName = "pending_audits")
data class PendingAuditEntity(
    @PrimaryKey val clientAuditId: String,
    val sku: String,
    val operatorCode: String,
    val deviceId: String,
    val systemQuantity: Int,
    val countedQuantity: Int,
    val recordedAtEpochMs: Long,
    val state: QueueState = QueueState.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
) {
    fun toDraft() = AuditDraft(
        clientAuditId, sku, operatorCode, deviceId, systemQuantity, countedQuantity,
        Instant.ofEpochMilli(recordedAtEpochMs),
    )

    companion object {
        fun from(d: AuditDraft) = PendingAuditEntity(
            d.clientAuditId, d.sku, d.operatorCode, d.deviceId, d.systemQuantity, d.countedQuantity,
            d.recordedAt.toEpochMilli(),
        )
    }
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products")
    suspend fun all(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE sku = :sku")
    suspend fun bySku(sku: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE sku = :sku")
    suspend fun deleteBySku(sku: String)

    @Query("DELETE FROM products")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(products: List<ProductEntity>) {
        clear()
        upsert(products)
    }
}

@Dao
interface PendingAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: PendingAuditEntity)

    @Query("SELECT * FROM pending_audits WHERE state = 'PENDING' ORDER BY recordedAtEpochMs LIMIT :limit")
    suspend fun pending(limit: Int): List<PendingAuditEntity>

    @Query("SELECT COUNT(*) FROM pending_audits WHERE state = 'PENDING'")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_audits WHERE state = 'FAILED'")
    fun failedCount(): Flow<Int>

    @Query("DELETE FROM pending_audits WHERE clientAuditId IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("UPDATE pending_audits SET state = 'FAILED', lastError = :reason WHERE clientAuditId = :id")
    suspend fun markFailed(id: String, reason: String)

    @Query("UPDATE pending_audits SET attempts = attempts + 1, lastError = :reason WHERE clientAuditId IN (:ids)")
    suspend fun recordAttempt(ids: List<String>, reason: String?)
}

@Database(entities = [ProductEntity::class, PendingAuditEntity::class], version = 1, exportSchema = true)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun pendingAudits(): PendingAuditDao

    companion object {
        fun create(context: Context): LocalDatabase =
            Room.databaseBuilder(context, LocalDatabase::class.java, "echostock.db").build()
    }
}
