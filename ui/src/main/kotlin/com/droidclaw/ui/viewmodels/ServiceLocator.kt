package com.droidclaw.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.droidclaw.bridge.PermissionGuard
import com.droidclaw.bridge.ToolRegistry
import com.droidclaw.bridge.NativeToolExecutor
import com.droidclaw.core.AppConfig
import com.droidclaw.core.db.AppDatabase
import com.droidclaw.orchestrator.AgentLoopService

object ServiceLocator {
    @Volatile private var db: AppDatabase? = null
    @Volatile private var agentLoopService: AgentLoopService? = null
    @Volatile private var nativeToolExecutor: NativeToolExecutor? = null

    val permissionGuard: PermissionGuard = PermissionGuard()

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolCallId TEXT")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolCallsJson TEXT")
        }
    }

    fun getDatabase(context: Application): AppDatabase {
        return db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, "droidclaw_db"
            )
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build().also { db = it }
        }
    }

    fun getNativeToolExecutor(context: Application): NativeToolExecutor {
        return nativeToolExecutor ?: synchronized(this) {
            nativeToolExecutor ?: NativeToolExecutor(
                ToolRegistry(),
                permissionGuard
            ).also { nativeToolExecutor = it }
        }
    }

    fun getAgentLoopService(context: Application): AgentLoopService {
        return agentLoopService ?: synchronized(this) {
            agentLoopService ?: AgentLoopService(
                context.applicationContext,
                getDatabase(context),
                getNativeToolExecutor(context)
            ).also { agentLoopService = it }
        }
    }

    /**
     * Populates [permissionGuard] from the tool SharedPreferences so that the agent
     * respects toggle states even when [ToolsViewModel] has not been instantiated yet.
     * Called by AgentForegroundService before starting the agent loop.
     */
    fun syncPermissionsFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(AppConfig.PREFS_TOOLS, Context.MODE_PRIVATE)
        ToolRegistry().getAllTools().forEach { tool ->
            if (prefs.getBoolean("tool_enabled_${tool.name}", false))
                permissionGuard.enableTool(tool.name)
            else
                permissionGuard.disableTool(tool.name)
        }
    }
}
