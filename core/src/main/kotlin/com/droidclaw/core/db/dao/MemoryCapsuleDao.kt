package com.droidclaw.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.droidclaw.core.db.entity.MemoryCapsuleEntity
import com.droidclaw.core.db.entity.GeneEntity
import com.droidclaw.core.db.entity.ToolSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryCapsuleDao {
    @Query("SELECT * FROM memory_capsules ORDER BY importance DESC, timestamp DESC")
    fun getAllMemoryCapsules(): Flow<List<MemoryCapsuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryCapsule(capsule: MemoryCapsuleEntity)

    @Query("DELETE FROM memory_capsules WHERE id = :id")
    suspend fun deleteMemoryCapsule(id: String)

    @Query("SELECT * FROM genes ORDER BY timestamp ASC")
    fun getAllGenes(): Flow<List<GeneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGene(gene: GeneEntity)

    @Query("SELECT * FROM tool_settings WHERE `key` = :key LIMIT 1")
    fun getToolSetting(key: String): Flow<ToolSettingEntity?>

    @Query("SELECT * FROM tool_settings")
    fun getAllToolSettings(): Flow<List<ToolSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolSetting(setting: ToolSettingEntity)
}
