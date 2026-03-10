package com.droidclaw.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.droidclaw.core.db.entity.GeneEntity

@Dao
interface GeneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gene: GeneEntity)

    @Query("SELECT * FROM genes ORDER BY timestamp DESC")
    suspend fun getAll(): List<GeneEntity>

    @Query("SELECT * FROM genes WHERE id = :id")
    suspend fun getById(id: String): GeneEntity?

    @Query("DELETE FROM genes WHERE id = :id")
    suspend fun deleteById(id: String)
}
