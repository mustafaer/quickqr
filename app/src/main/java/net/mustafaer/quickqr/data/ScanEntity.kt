package net.mustafaer.quickqr.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Pinned scans are exempt from the [AppDatabase.HISTORY_LIMIT] cap. Added in schema 2. */
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false
)
