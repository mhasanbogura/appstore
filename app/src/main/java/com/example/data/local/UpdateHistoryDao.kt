package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateHistoryDao {
    @Query("SELECT * FROM update_history ORDER BY updatedAt DESC")
    fun getAllUpdateHistory(): Flow<List<UpdateHistory>>

    @Query("SELECT * FROM update_history WHERE packageName = :packageName LIMIT 1")
    suspend fun getUpdateHistory(packageName: String): UpdateHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpdate(update: UpdateHistory)

    @Query("UPDATE update_history SET syncToCloud = 1 WHERE packageName = :packageName")
    suspend fun markSynced(packageName: String)

    @Query("SELECT * FROM update_history WHERE syncToCloud = 0")
    suspend fun getUnsyncedUpdates(): List<UpdateHistory>

    @Query("DELETE FROM update_history WHERE packageName = :packageName")
    suspend fun deleteUpdate(packageName: String)

    @Query("DELETE FROM update_history")
    suspend fun clearAll()
}
