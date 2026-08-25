package net.mustafaer.quickqr.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity): Long

    @Delete
    suspend fun deleteScan(scan: ScanEntity): Int

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteScanById(id: Int): Int

    @Query("DELETE FROM scans")
    suspend fun clearAllScans(): Int

    @Query("SELECT * FROM scans WHERE text = :text LIMIT 1")
    suspend fun getScanByText(text: String): ScanEntity?

    @Query("DELETE FROM scans WHERE id NOT IN (SELECT id FROM scans ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun capHistorySize(limit: Int): Int

    @Transaction
    suspend fun insertAndCap(scan: ScanEntity, limit: Int): Long {
        val existing = getScanByText(scan.text)
        val id = if (existing != null) {
            val updated = existing.copy(timestamp = scan.timestamp, type = scan.type)
            insertScan(updated)
            updated.id.toLong()
        } else {
            insertScan(scan)
        }
        capHistorySize(limit)
        return id
    }
}
