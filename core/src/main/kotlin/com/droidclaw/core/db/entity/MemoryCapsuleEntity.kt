package com.droidclaw.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_capsules")
data class MemoryCapsuleEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val importance: Int,
    val timestamp: Long
)
