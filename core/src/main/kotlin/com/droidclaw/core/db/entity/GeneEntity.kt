package com.droidclaw.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "genes")
data class GeneEntity(
    @PrimaryKey
    val id: String,
    val description: String,
    val origin: String,
    val timestamp: Long
)
