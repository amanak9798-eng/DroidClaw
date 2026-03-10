package com.droidclaw.app

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.droidclaw.core.AppConfig
import com.droidclaw.orchestrator.AgentState
import com.droidclaw.ui.viewmodels.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that wraps the agent loop so Android does not kill
 * the native llama-server process when the app is backgrounded.
 *
 * Started via [AppConfig.ACTION_START_AGENT] intent with [AppConfig.EXTRA_MODEL_PATH].
 * Stopped via [AppConfig.ACTION_STOP_AGENT] intent.
 */
class AgentForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConfig.ACTION_START_AGENT -> {
                val modelPath = intent.getStringExtra(AppConfig.EXTRA_MODEL_PATH)
                    ?: return START_NOT_STICKY

                // Show the persistent notification immediately so Android doesn't ANR.
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, buildNotification("Agent starting…"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification("Agent starting…"))
                }

                // Sync tool enable/disable state from SharedPrefs before the agent uses tools.
                ServiceLocator.syncPermissionsFromPrefs(applicationContext)

                // Read agent config toggles from SharedPreferences.
                val prefs = getSharedPreferences(AppConfig.PREFS_MAIN, MODE_PRIVATE)
                val autoRetry = prefs.getBoolean(AppConfig.PREFS_KEY_AGENT_AUTO_RETRY, true)
                val saveFailedToMemory = prefs.getBoolean(AppConfig.PREFS_KEY_AGENT_SAVE_FAILED, false)

                val agentLoop = ServiceLocator.getAgentLoopService(applicationContext as Application)

                // Keep notification text in sync with AgentState.
                agentLoop.state.onEach { state ->
                    val text = when (state) {
                        AgentState.STARTING_LLM      -> "Loading LLM model…"
                        AgentState.RUNNING           -> "Agent running"
                        AgentState.ERROR             -> "Agent error — open app to retry"
                        AgentState.IDLE              -> return@onEach
                    }
                    updateNotification(text)
                }.launchIn(serviceScope)

                serviceScope.launch {
                    agentLoop.start(modelPath, autoRetry, saveFailedToMemory)
                }
            }

            AppConfig.ACTION_STOP_AGENT -> {
                val agentLoop = ServiceLocator.getAgentLoopService(applicationContext as Application)
                serviceScope.launch {
                    agentLoop.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Best-effort: stop native llama-server when the service is killed so
        // it doesn't linger as an orphaned process consuming RAM/CPU.
        val agentLoop = ServiceLocator.getAgentLoopService(applicationContext as Application)
        runBlocking {
            withTimeoutOrNull(8_000) { agentLoop.stop() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(AppConfig.ACTION_STOP_AGENT).apply {
                setClassName(packageName, AppConfig.FOREGROUND_SERVICE_CLASS)
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, DroidClawApp.CHANNEL_AGENT)
            .setContentTitle("DroidClaw Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
