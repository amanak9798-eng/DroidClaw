package com.droidclaw.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class ServerStatus {
    STOPPED, STARTING, RUNNING, ERROR
}

class LlamaServer(private val context: Context) {

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var serverProcess: Process? = null
    private var serverPort: Int = DEFAULT_PORT

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun start(
        modelPath: String,
        port: Int = DEFAULT_PORT,
        contextSize: Int? = null
    ) = withContext(Dispatchers.IO) {
        if (_status.value == ServerStatus.RUNNING) return@withContext

        _status.value = ServerStatus.STARTING
        serverPort = port
        _logs.value = emptyList()

        // Validate model file exists before attempting to start the native binary
        val modelFile = java.io.File(modelPath)
        if (!modelFile.exists()) {
            _status.value = ServerStatus.ERROR
            val msg = "Model file not found: $modelPath"
            appendLog(msg)
            android.util.Log.e("LlamaServer", msg)
            return@withContext
        }
        if (!modelFile.canRead()) {
            _status.value = ServerStatus.ERROR
            val msg = "Model file not readable: $modelPath"
            appendLog(msg)
            android.util.Log.e("LlamaServer", msg)
            return@withContext
        }
        android.util.Log.d("LlamaServer", "Model file verified: ${modelFile.absolutePath} (${modelFile.length()} bytes)")

        try {
            // Kill any orphaned llama-server process from a previous app session.
            // Without this, the health check would pass against the stale server,
            // and new requests would compete with old occupied slots.
            killOrphanedProcesses()

            val binary = NativeBinaryHelper.findBinary(context, BINARY_NAME)

            // detect memory (Android ActivityManager)
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalRam = memInfo.totalMem // bytes
            val availRam = memInfo.availMem // bytes

            // reserve some RAM for system (20%)
            val reservedForSystem = (totalRam * 0.20).toLong()
            val usableRam = (totalRam - reservedForSystem).coerceAtLeast(128L * 1024 * 1024)

            // Dynamic Context Size based on Memory
            val memoryCtxSize = when {
                usableRam >= 8L * 1024 * 1024 * 1024 -> 16384  // Lots of RAM (8GB+ usable)
                usableRam >= 4L * 1024 * 1024 * 1024 -> 8192
                else -> 4096
            }
            // Clamp the requested context to what memory safely allows, or fallback to memory heuristic
            val ctxSize = if (contextSize != null) {
                kotlin.math.min(contextSize, memoryCtxSize)
            } else {
                memoryCtxSize
            }

            val detector = DeviceTierDetector(context)
            val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            
            // Threads: Using too many threads on Android actually slows down memory bandwidth
            // heavily on big.LITTLE architectures. Generally 4 performance cores is optimal.
            val threads = kotlin.math.min(4, cpuCount).toString()

            // Batch tuned to cores & RAM to use processor as much as it can efficiently
            val batchSize = when {
                cpuCount >= 8 && usableRam >= 6L * 1024 * 1024 * 1024 -> 256
                cpuCount >= 4 && usableRam >= 3L * 1024 * 1024 * 1024 -> 128
                else -> 64
            }

            val command = mutableListOf(
                binary.absolutePath,
                "-m", modelPath,
                "--port", port.toString(),
                "--host", "127.0.0.1",
                "-c", ctxSize.toString(),
                "-t", threads,
                "-b", batchSize.toString(),
                "--parallel", "1",  // Single slot
                "-fa", "auto" // Flash Attention
            )

            // If there's enough RAM to load the whole model into RAM, request no-mmap + mlock
            val modelSize = try { modelFile.length() } catch(e: Exception) { 0L }
            val targetModelRam = (usableRam * 0.75).toLong() // 75% of usable
            if (modelSize > 0 && targetModelRam >= modelSize) {
                // Pin model to RAM to prevent swapping latency
                command.add("--no-mmap")
                command.add("--mlock")
            }
            
            // Set Cache RAM
            val targetCacheRamMb = ((usableRam * 0.15).toLong() / (1024 * 1024)).coerceAtLeast(128L)
            command.add("--cache-ram")
            command.add(targetCacheRamMb.toString())

            // Only add -ngl if the device likely supports some acceleration
            if (detector.detectTier() == DeviceTier.FULL) {
                command.add("-ngl")
                command.add("99")
            }

            // Skip the warmup step on lower-tier devices to reduce startup latency.
            if (detector.detectTier() != DeviceTier.FULL) {
                command.add("--no-warmup")
            }

            android.util.Log.d("LlamaServer", "Executing: ${command.joinToString(" ")}")
            appendLog("Starting llama-server on port $port (ctx=$ctxSize)...")

            val processBuilder = ProcessBuilder(command)
                .redirectErrorStream(true)
                .directory(context.filesDir)

            serverProcess = processBuilder.start()
            val localProcess = serverProcess!!

            // Monitor output in background
            val reader = BufferedReader(InputStreamReader(localProcess.inputStream))
            Thread {
                try {
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            android.util.Log.d("LlamaServer", "[STDOUT/ERR] $line")
                            appendLog(line)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LlamaServer", "Log reader error: ${e.message}")
                } finally {
                    val exitCode = runCatching { localProcess.exitValue() }.getOrNull()
                    if (exitCode != null) {
                        android.util.Log.e("LlamaServer", "Process exited with code $exitCode")
                        appendLog("Process exited with code $exitCode")
                    }
                }
            }.start()

            // Poll health endpoint until responsive
            val ready = waitForHealth(maxAttempts = HEALTH_CHECK_ATTEMPTS)
            if (ready) {
                _status.value = ServerStatus.RUNNING
                appendLog("Server is ready.")
                android.util.Log.i("LlamaServer", "Server reached RUNNING state on port $port")
            } else {
                val exitCode = runCatching { localProcess.exitValue() }.getOrNull()
                _status.value = ServerStatus.ERROR
                val errorMsg = if (exitCode != null) "Server exited with code $exitCode" else "Server failed to respond within timeout."
                appendLog(errorMsg)
                android.util.Log.e("LlamaServer", errorMsg)
                stop()
            }
        } catch (e: Exception) {
            _status.value = ServerStatus.ERROR
            appendLog("Start failed: ${e.message}")
            android.util.Log.e("LlamaServer", "Start failed", e)
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        val process = serverProcess ?: return@withContext
        appendLog("Stopping server...")

        try {
            process.destroy()
            val exited = process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                appendLog("Force-killed server process.")
            }
        } catch (_: Exception) {
            process.destroyForcibly()
        } finally {
            serverProcess = null
            _status.value = ServerStatus.STOPPED
            appendLog("Server stopped.")
        }
    }

    fun healthCheck(): Boolean {
        return try {
            val request = Request.Builder()
                .url("http://127.0.0.1:$serverPort/health")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                response.isSuccessful && body.contains("ok", ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isRunning(): Boolean {
        val process = serverProcess ?: return false
        return process.isAlive && healthCheck()
    }

    private suspend fun waitForHealth(maxAttempts: Int): Boolean {
        repeat(maxAttempts) {
            delay(HEALTH_CHECK_INTERVAL_MS)
            if (healthCheck()) return true
        }
        return false
    }

    private fun contextSizeForDevice(): Int {
        val detector = DeviceTierDetector(context)
        // The native DroidClaw system prompt + tool definitions need ~3200 tokens minimum.
        // These sizes must leave headroom for user messages and LLM output.
        return when (detector.detectTier()) {
            DeviceTier.NANO -> 2048
            DeviceTier.STANDARD -> 4096
            DeviceTier.FULL -> 8192
        }
    }

    /**
     * Kills any orphaned llama-server processes from previous app sessions.
     * This is critical: if the app is killed/restarted, the native process can survive
     * and keep listening on the port. New health checks would pass against the stale server,
     * but old requests would still occupy its inference slots.
     */
    private fun killOrphanedProcesses() {
        // First, kill our tracked process if it exists
        serverProcess?.let { proc ->
            try {
                proc.destroyForcibly()
                android.util.Log.d("LlamaServer", "Killed tracked server process")
            } catch (_: Exception) {}
            serverProcess = null
        }

        // Kill any lingering llama-server native processes by name
        try {
            val killProc = Runtime.getRuntime().exec(arrayOf("killall", BINARY_NAME))
            val exitCode = killProc.waitFor()
            if (exitCode == 0) {
                android.util.Log.w("LlamaServer", "Killed orphaned $BINARY_NAME process(es)")
                appendLog("Cleaned up orphaned server process")
                // Give the OS a moment to release the port
                Thread.sleep(500)
            }
        } catch (e: Exception) {
            android.util.Log.d("LlamaServer", "No orphaned processes to kill: ${e.message}")
        }

        // Also try pkill as fallback (some Android builds may not have killall)
        try {
            Runtime.getRuntime().exec(arrayOf("pkill", "-f", BINARY_NAME)).waitFor()
        } catch (_: Exception) {}
    }

    private fun appendLog(line: String) {
        synchronized(this) {
            val updated = (_logs.value + line).let {
                if (it.size > MAX_LOG_LINES) it.takeLast(MAX_LOG_LINES) else it
            }
            _logs.value = updated
        }
    }

    companion object {
        private const val BINARY_NAME = "llama-server"
        private const val DEFAULT_PORT = 11434
        private const val HEALTH_CHECK_ATTEMPTS = 90
        private const val HEALTH_CHECK_INTERVAL_MS = 1000L
        private const val STOP_TIMEOUT_SECONDS = 5L
        private const val MAX_LOG_LINES = 500
    }
}
