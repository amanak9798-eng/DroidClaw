package com.droidclaw.core.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String,      // "USER", "ASSISTANT", "SYSTEM", "TOOL"
    val content: String,
    val taskId: String?,   // Nullable, if associated with a background task
    val timestamp: Long,
    val contextConsumedTokens: Int = 0,
    val toolName: String? = null,
    val toolCallId: String? = null, // Used if role == "tool"
    val toolCallsJson: String? = null // Used if role == "assistant" and it invoked tools
)
