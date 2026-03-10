package com.droidclaw.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.droidclaw.core.ModelRegistry
import com.droidclaw.orchestrator.HeartbeatScheduler

class DroidClawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ModelRegistry.init(this)
        createNotificationChannels()
        HeartbeatScheduler.schedule(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_AGENT,
                    "Agent Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "DroidClaw on-device AI agent running" }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DOWNLOAD,
                    "Model Download",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "LLM model download progress" }
            )
        }
    }

    companion object {
        const val CHANNEL_AGENT = "droidclaw_agent"
        const val CHANNEL_DOWNLOAD = "droidclaw_download"
    }
}
