package com.droidclaw.orchestrator

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.droidclaw.core.AppConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class HeartbeatWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        return try {
            val url = URL("http://127.0.0.1:${AppConfig.LLM_PORT}/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            val responseCode = conn.responseCode
            conn.disconnect()
            if (responseCode in 200..299) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object HeartbeatScheduler {
    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(AppConfig.PREFS_KEY_AGENT_HEARTBEAT, true)) {
            WorkManager.getInstance(context).cancelUniqueWork("heartbeat")
            return
        }
        val workRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
