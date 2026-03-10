package com.droidclaw.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.droidclaw.core.db.entity.ToolSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolSettingDao {
    @Query("SELECT * FROM tool_settings")
    fun getAllSettings(): Flow<List<ToolSettingEntity>>

    @Query("SELECT * FROM tool_settings WHERE key = :key LIMIT 1")
    suspend fun getSetting(key: String): ToolSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: ToolSettingEntity)

    @Query("DELETE FROM tool_settings WHERE key = :key")
    suspend fun delete(key: String)
}
