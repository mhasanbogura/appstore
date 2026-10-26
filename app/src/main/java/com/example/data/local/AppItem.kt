package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppItem(
    @PrimaryKey val id: String,
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val size: Long,
    val driveFileId: String,
    val fileName: String = "",
    val downloadUrl: String,
    val iconUrl: String,
    val description: String,
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val permissions: String = "",
    val category: String = "Utility",
    val isFavorite: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val driveModifiedTime: Long = 0L,
    val patchVersion: String = "",
    val updateCheck: Boolean = true,
    val platform: String = "Android"
)
