package com.droidclaw.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tool_calls")
data class ToolCallEntity(
    @PrimaryKey
    val id: String,
    val taskId: String,
    val toolName: String,
    val inputJson: String,
    val resultJson: String?,
    val status: String,
    val timestamp: Long
)
