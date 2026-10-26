package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "update_history")
data class UpdateHistory(
    @PrimaryKey val packageName: String,
    val appName: String,
    val oldVersionName: String,
    val oldVersionCode: Int,
    val newVersionName: String,
    val newVersionCode: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncToCloud: Boolean = false
)
