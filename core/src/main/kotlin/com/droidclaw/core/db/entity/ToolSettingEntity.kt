package com.droidclaw.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tool_settings")
data class ToolSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
