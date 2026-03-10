package com.droidclaw.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val description: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
