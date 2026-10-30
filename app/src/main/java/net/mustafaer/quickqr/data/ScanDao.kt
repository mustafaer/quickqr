package net.mustafaer.quickqr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM scans ORDER BY pinned DESC, timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity): Long

    @Query("DELETE FROM scans WHERE id IN (:ids)")
    suspend fun deleteScans(ids: List<Int>): Int

    @Query("DELETE FROM scans")
    suspend fun clearAllScans(): Int

    @Query("UPDATE scans SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Int, pinned: Boolean): Int

    @Query("SELECT * FROM scans WHERE text = :text LIMIT 1")
    suspend fun getScanByText(text: String): ScanEntity?

    /** Keeps the newest [limit] unpinned scans; pinned ones never count and never go. */
    @Query(
        "DELETE FROM scans WHERE pinned = 0 AND id NOT IN " +
            "(SELECT id FROM scans WHERE pinned = 0 ORDER BY timestamp DESC LIMIT :limit)"
    )
    suspend fun capHistorySize(limit: Int): Int

    @Transaction
    suspend fun insertAndCap(scan: ScanEntity, limit: Int): Long {
        val existing = getScanByText(scan.text)
        val id = if (existing != null) {
            // Re-scanning moves the entry to the top and keeps its pin.
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
