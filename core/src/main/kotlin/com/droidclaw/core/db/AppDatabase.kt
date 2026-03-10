package com.droidclaw.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.droidclaw.core.db.dao.ChatMessageDao
import com.droidclaw.core.db.dao.ChatSessionDao
import com.droidclaw.core.db.dao.GeneDao
import com.droidclaw.core.db.dao.MemoryCapsuleDao
import com.droidclaw.core.db.dao.TaskDao
import com.droidclaw.core.db.dao.ToolSettingDao
import com.droidclaw.core.db.entity.ChatMessageEntity
import com.droidclaw.core.db.entity.ChatSessionEntity
import com.droidclaw.core.db.entity.GeneEntity
import com.droidclaw.core.db.entity.MemoryCapsuleEntity
import com.droidclaw.core.db.entity.TaskEntity
import com.droidclaw.core.db.entity.ToolCallEntity
import com.droidclaw.core.db.entity.ToolSettingEntity

@Database(
    entities = [
        TaskEntity::class,
        ToolCallEntity::class,
        MemoryCapsuleEntity::class,
        ToolSettingEntity::class,
        GeneEntity::class,
        ChatMessageEntity::class,
        ChatSessionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun memoryCapsuleDao(): MemoryCapsuleDao
    abstract fun toolSettingDao(): ToolSettingDao
    abstract fun geneDao(): GeneDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
}
