package net.mustafaer.quickqr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)
