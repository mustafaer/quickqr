package net.mustafaer.quickqr.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE text LIKE :searchQuery OR type LIKE :searchQuery ORDER BY timestamp DESC")
    fun searchScans(searchQuery: String): Flow<List<ScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity): Long

    @Delete
    suspend fun deleteScan(scan: ScanEntity): Int

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteScanById(id: Int): Int

    @Query("DELETE FROM scans")
    suspend fun clearAllScans(): Int

    /**
     * Optional: Keep only the last N scans.
     * We can call this after inserting a new scan if we want to cap history size.
     */
    @Query("DELETE FROM scans WHERE id NOT IN (SELECT id FROM scans ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun capHistorySize(limit: Int): Int
}
