package com.droidclaw.orchestrator

import android.content.Context
import android.util.Log
import com.droidclaw.bridge.NativeToolExecutor
import com.droidclaw.bridge.ToolExecuteRequest
import com.droidclaw.core.AppConfig
import com.droidclaw.core.db.AppDatabase
import com.droidclaw.core.db.entity.ChatMessageEntity
import com.droidclaw.core.db.entity.ChatSessionEntity
import com.droidclaw.llm.LlamaServer
import com.droidclaw.llm.ServerStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val THINK_TAG_REGEX = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)

enum class AgentState {
    IDLE, STARTING_LLM, RUNNING, ERROR
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,       // "user", "assistant", "tool"
    val content: String,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val contextConsumedTokens: Int = 0,
    val toolCallsJson: String? = null
)

class AgentLoopService(
    private val context: Context,
    private val database: AppDatabase,
    private val nativeToolExecutor: NativeToolExecutor
) {
    companion object {
        private const val TAG = "AgentLoop"
        private const val MAX_ITERATIONS = 10
        private const val MAX_RETRIES = 2
    }

    // ── Performance caches ──────────────────────────────────────────────────
    /** Cached system prompt — rebuilt only when tool configuration changes. */
    @Volatile private var cachedSystemPrompt: String? = null
    /** Cached tools JSON array — rebuilt together with system prompt. */
    @Volatile private var cachedToolsSchema: org.json.JSONArray? = null
    /** Active session-loading coroutine — cancelled before switching sessions. */
    private var sessionLoadJob: Job? = null

    @Volatile private var serviceScope: CoroutineScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    @Volatile private var agentJob: kotlinx.coroutines.Job? = null
    @Volatile private var activeCall: Call? = null

    val llamaServer = LlamaServer(context)

    private val _state = MutableStateFlow(AgentState.IDLE)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /** The currently active chat session ID. */
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    /** All chat sessions, ordered newest first. */
    private val _sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val sessions: StateFlow<List<ChatSessionEntity>> = _sessions.asStateFlow()

    /** Context window limit reported by the loaded model (tokens). Default to 2048 conservative. */
    private val _contextLimit = MutableStateFlow(2048)
    val contextLimit: StateFlow<Int> = _contextLimit.asStateFlow()

    /** Tokens used by the last prompt (tracks how full context window is). */
    private val _contextUsed = MutableStateFlow(0)
    val contextUsed: StateFlow<Int> = _contextUsed.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.MINUTES)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val llmApiUrl = "http://${AppConfig.LOCALHOST}:${AppConfig.LLM_PORT}/v1/chat/completions"

    /**
     * Builds the system prompt dynamically, injecting the list of currently enabled tools
     * so the LLM knows exactly what capabilities it has.
     */
    private fun isSmallModel(): Boolean {
        val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)
        val modelName = prefs.getString(AppConfig.PREFS_KEY_SELECTED_MODEL_NAME, "")?.lowercase() ?: ""
        val smallPatterns = listOf("270m", "0.5b", "135m", "360m", "500m", "350m", "0.5-b", "qwen2.5-0.5b")
        return smallPatterns.any { modelName.contains(it) }
    }

    /**
     * Returns the system prompt, rebuilding it only when the set of enabled tools has changed.
     * This avoids re-computing the large string on every agent loop iteration.
     */
    private fun getSystemPrompt(): String {
        cachedSystemPrompt?.let { return it }
        val prompt = if (isSmallModel()) {
             """
You are DroidClaw, a helpful and concise AI assistant running locally on an Android device. 
Answer the user's questions clearly, concisely, and directly.
You do not have access to any external tools or device control capabilities.
""".trimIndent()
        } else {
             val toolsSummary = nativeToolExecutor.getEnabledToolsSummary()
             """
You are DroidClaw, an AI agent with direct control over this Android device. You act on behalf of the user by calling native tools. You are precise, efficient, and autonomous.

# How You Operate (OODA Loop)
Every request follows this cycle. You may repeat it multiple times:
1. **Observe** — Use `read_screen` to see what is currently on screen. Never guess UI state.
2. **Orient** — Analyze the screen tree. Identify the element IDs, text, and layout.
3. **Decide** — Pick the correct tool and parameters to accomplish the next step.
4. **Act** — Call the tool. Read the result. If it failed, adapt and retry.

Always Observe before Acting. Never tap or type without first reading the screen.

# Tool Calling Rules
- You MUST call tools to perform actions. Do NOT describe what you "would" do — actually do it.
- Always call `read_screen` FIRST to see the current UI before tapping, typing, or scrolling.
- Call ONE tool at a time. Wait for its result before deciding the next action.
- If a tool returns an error, read the error code, adapt parameters, and retry once.
- If a tool says "SERVICE_NOT_RUNNING", tell the user to enable the Accessibility Service in Settings.
- Provide ALL required parameters. Never send empty or placeholder values.

# Tactical Patterns
- **Open an app**: `run_intent` with action="android.intent.action.MAIN" and the app's package_name.
- **Tap a button**: First `read_screen` → find the node ID → `tap_element` with that ID.
- **Type into a field**: First `tap_element` the input field → then `type_text` with the text.
- **Scroll to find content**: `scroll` direction="down" → then `read_screen` again to check.
- **Navigate back**: `press_key` with key="BACK".
- **Go home**: `press_key` with key="HOME".
- **Send SMS**: `send_sms` with number and message. No need to open the SMS app.
- **Read files**: `file_read` with the absolute path. Use `file_list` first if path is unknown.

# Error Recovery
| Error Code | Meaning | Fix |
|---|---|---|
| SERVICE_NOT_RUNNING | Accessibility Service off | Tell user to enable it |
| NODE_NOT_FOUND | Element ID doesn't match | Re-read screen, use correct ID |
| NO_ACTIVE_WINDOW | No foreground app | Press HOME, then open target app |
| MISSING_* | You forgot a parameter | Resend with all required params |
| PERMISSION_DENIED | Tool not enabled by user | Tell user to enable it in Tools |

# Response Rules
- Be concise. State what you did, not how you decided.
- After completing a multi-step task, give a one-line summary.
- If you cannot accomplish something, explain why in one sentence.
- Never ask the user to repeat themselves — use conversation history.
- When the user asks a knowledge question (no action needed), answer directly without tools.

# Currently Enabled Tools
${toolsSummary}
""".trimIndent()
        }
        cachedSystemPrompt = prompt
        return prompt
    }

    /** Invalidates both the system prompt and tools schema caches (call when tool config changes). */
    fun invalidatePromptCache() {
        cachedSystemPrompt = null
        cachedToolsSchema = null
    }

    /** Returns the tools JSON schema, rebuilding only when the cache is empty. */
    private fun getToolsSchema(): org.json.JSONArray {
        cachedToolsSchema?.let { return it }
        val schema = if (isSmallModel()) org.json.JSONArray() else nativeToolExecutor.getToolsSchemaJson()
        cachedToolsSchema = schema
        return schema
    }

    init {
        // Load sessions list
        serviceScope.launch {
            database.chatSessionDao().getAllSessions().collect { entities ->
                _sessions.value = entities
            }
        }
        // Create or load initial session
        serviceScope.launch {
            val existingSessions = database.chatSessionDao().getAllSessions()
            // Collect once to check if there are sessions
            var loaded = false
            existingSessions.collect { list ->
                if (!loaded) {
                    loaded = true
                    if (list.isNotEmpty()) {
                        loadSession(list.first().id)
                    } else {
                        createNewSession()
                    }
                }
            }
        }
    }

    /** Creates a brand new chat session and makes it active. */
    suspend fun createNewSession(): String {
        val sessionId = UUID.randomUUID().toString()
        val session = ChatSessionEntity(
            id = sessionId,
            title = "New Chat",
            createdAt = System.currentTimeMillis()
        )
        database.chatSessionDao().insertSession(session)
        loadSession(sessionId)
        return sessionId
    }

    /** Switches to an existing session and loads its messages. */
    fun loadSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _chatMessages.value = emptyList()
        // Cancel any previously running session-load collector to avoid duplicates
        sessionLoadJob?.cancel()
        sessionLoadJob = serviceScope.launch {
            database.chatMessageDao().getMessagesForSession(sessionId).collect { entities ->
                val msgs = entities.map {
                    val standardizedRole = when (it.role.uppercase()) {
                        "USER" -> "user"
                        "AGENT" -> "assistant"
                        "ASSISTANT" -> "assistant"
                        "SYSTEM" -> "system"
                        "TOOL" -> "tool"
                        else -> it.role.lowercase()
                    }
                    ChatMessage(
                        id = it.id,
                        role = standardizedRole,
                        content = it.content,
                        toolName = it.toolName,
                        toolCallId = it.toolCallId,
                        contextConsumedTokens = it.contextConsumedTokens,
                        toolCallsJson = it.toolCallsJson
                    )
                }
                _chatMessages.value = msgs
            }
        }
    }

    /** Deletes a session and all its messages (cascade). */
    suspend fun deleteSession(sessionId: String) {
        database.chatSessionDao().deleteSession(sessionId)
        // If the deleted session was active, switch to latest or create new
        if (_currentSessionId.value == sessionId) {
            val remaining = _sessions.value.filter { it.id != sessionId }
            if (remaining.isNotEmpty()) {
                loadSession(remaining.first().id)
            } else {
                createNewSession()
            }
        }
    }

    /** Updates the session title (e.g., derived from first user message). */
    private suspend fun autoTitleSession(sessionId: String, firstMessage: String) {
        val title = firstMessage.take(40).let { if (firstMessage.length > 40) "$it…" else it }
        val session = database.chatSessionDao().getSessionById(sessionId) ?: return
        database.chatSessionDao().updateSession(session.copy(title = title))
    }

    suspend fun start(
        modelPath: String,
        autoRetry: Boolean = true,
        saveFailedToMemory: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (_state.value != AgentState.IDLE && _state.value != AgentState.ERROR) return@withContext

        agentJob?.cancel()

        try {
            _state.value = AgentState.STARTING_LLM
            Log.d(TAG, "Starting Llama Server natively...")
            
            val modelCtx = getModelMaxContext()
            Log.d(TAG, "Model config defines max context as $modelCtx")
            llamaServer.start(modelPath, port = AppConfig.LLM_PORT, contextSize = modelCtx)

            if (llamaServer.status.value != ServerStatus.RUNNING && llamaServer.status.value != ServerStatus.STARTING) {
                delay(2000)
            }
            if (llamaServer.status.value == ServerStatus.ERROR) {
                Log.e(TAG, "Llama Server failed to reach RUNNING state.")
                _state.value = AgentState.ERROR
                return@withContext
            }

            var ready = false
            for (i in 1..30) {
                try {
                    val req = Request.Builder()
                        .url("http://${AppConfig.LOCALHOST}:${AppConfig.LLM_PORT}/health")
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        ready = true
                        resp.close()
                        break
                    }
                    resp.close()
                } catch (e: Exception) { /* retry */ }
                delay(1000)
            }

            if (!ready) {
                Log.e(TAG, "Llama server did not respond to health check within 30s.")
                _state.value = AgentState.ERROR
                return@withContext
            }

            // Fetch context window size from server props
            fetchContextLimit()

            _state.value = AgentState.RUNNING
            Log.d(TAG, "Agent orchestrator is RUNNING")
            nativeToolExecutor.broadcastLog("[System] Agent started — LLM server is ready")
            
            // Prime the KV cache with the system prompt immediately upon startup
            warmupCache()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start agent", e)
            _state.value = AgentState.ERROR
        }
    }

    /** Tries to read the context window size from the llama-server /props endpoint. */
    private suspend fun fetchContextLimit() {
        try {
            val req = Request.Builder()
                .url("http://${AppConfig.LOCALHOST}:${AppConfig.LLM_PORT}/props")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val ctxSize = json.optInt("default_generation_settings.n_ctx", 0)
                if (ctxSize > 0) {
                    _contextLimit.value = ctxSize
                } else {
                    // Try alternative path
                    val genSettings = json.optJSONObject("default_generation_settings")
                    val nCtx = genSettings?.optInt("n_ctx", 0) ?: 0
                    if (nCtx > 0) _contextLimit.value = nCtx
                    else {
                        // Fallback to static lookup if /props doesn't report it
                        val staticCtx = getModelMaxContext()
                        if (staticCtx > 0) _contextLimit.value = staticCtx
                    }
                }
            }
            resp.close()
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch context limit from server: ${e.message}")
        }
    }

    fun executePrompt(prompt: String) {
        activeCall?.cancel()
        activeCall = null
        agentJob?.cancel()
        agentJob = serviceScope.launch {
            val sessionId = _currentSessionId.value ?: createNewSession()

            // Auto-title the session from the first user message
            val msgCount = database.chatMessageDao().getMessageCountForSession(sessionId)
            val userMsg = ChatMessage(role = "user", content = prompt)
            saveMessage(userMsg, sessionId)

            if (msgCount == 0) {
                autoTitleSession(sessionId, prompt)
            }

            _isProcessing.value = true
            try {
                var waited = 0
                while (_state.value != AgentState.RUNNING) {
                    if (_state.value == AgentState.ERROR) break
                    delay(500)
                    waited++
                    if (waited > 120) {
                        Log.e(TAG, "Timed out waiting for agent to reach RUNNING")
                        break
                    }
                }

                if (_state.value == AgentState.RUNNING) {
                    runAgentLoop(sessionId)
                } else {
                    Log.w(TAG, "Agent state is ${_state.value}, cannot run loop")
                    nativeToolExecutor.broadcastLog("[Error] Agent is not running. State: ${_state.value}")
                    saveMessage(ChatMessage(role = "assistant", content = "Error: Agent is offline or failed to start. Check your model in Config."), sessionId)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "executePrompt cancelled (new prompt submitted)")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in executePrompt", e)
                nativeToolExecutor.broadcastLog("[Error] Unexpected: ${e.message}")
                saveMessage(ChatMessage(role = "assistant", content = "Error: ${e.message ?: "Something went wrong."}"), sessionId)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun runAgentLoop(sessionId: String) {
        Log.d(TAG, "Entering agent loop")
        nativeToolExecutor.broadcastLog("[Loop] Starting OODA cycle")

        val toolMessages = mutableListOf<JSONObject>()

        for (iteration in 1..MAX_ITERATIONS) {
            if (_state.value != AgentState.RUNNING) {
                Log.w(TAG, "Agent state is ${_state.value}, exiting loop")
                break
            }

            Log.d(TAG, "Loop iteration $iteration/$MAX_ITERATIONS")
            nativeToolExecutor.broadcastLog("[Loop] Iteration $iteration/$MAX_ITERATIONS")

            val msgsJson = JSONArray()
            msgsJson.put(JSONObject().apply { put("role", "system"); put("content", getSystemPrompt()) })

            _chatMessages.value.forEach { msg ->
                msgsJson.put(JSONObject().apply {
                    put("role", msg.role)
                    if (msg.content.isNotEmpty()) put("content", msg.content)
                    if (msg.role == "tool" && msg.toolCallId != null) {
                        put("tool_call_id", msg.toolCallId)
                    }
                    if (msg.role == "assistant" && msg.toolCallsJson != null) {
                        put("tool_calls", JSONArray(msg.toolCallsJson))
                    }
                })
            }

            toolMessages.forEach { msgsJson.put(it) }

            val requestBodyJson = JSONObject().apply {
                put("messages", msgsJson)
                val toolsSchema = getToolsSchema()
                if (toolsSchema.length() > 0) {
                    put("tools", toolsSchema)
                    put("tool_choice", "auto")
                }
            }

            val responseJson = sendLlmRequest(requestBodyJson) ?: break

            // Update context usage from usage stats
            val usage = responseJson.optJSONObject("usage")
            val promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0
            _contextUsed.value = promptTokens

            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                Log.e(TAG, "Invalid LLM response: no choices")
                nativeToolExecutor.broadcastLog("[Error] Invalid LLM response: no choices")
                break
            }

            val choice = choices.getJSONObject(0)
            val message = choice.optJSONObject("message") ?: break
            val finishReason = choice.optString("finish_reason", "stop")
            val content = message.optString("content", "")
            val toolCalls = message.optJSONArray("tool_calls")

            if (toolCalls != null && toolCalls.length() > 0) {
                Log.d(TAG, "LLM requested ${toolCalls.length()} tool call(s)")
                nativeToolExecutor.broadcastLog("[Loop] LLM requested ${toolCalls.length()} tool call(s)")

                val assistantToolMsg = JSONObject().apply {
                    put("role", "assistant")
                    if (content.isNotEmpty()) put("content", content)
                    put("tool_calls", toolCalls)
                }
                toolMessages.add(assistantToolMsg)

                saveMessage(ChatMessage(
                    role = "assistant",
                    content = content,
                    toolCallsJson = toolCalls.toString(),
                    contextConsumedTokens = promptTokens
                ), sessionId)

                for (i in 0 until toolCalls.length()) {
                    val toolCall = toolCalls.getJSONObject(i)
                    val toolCallId = toolCall.optString("id", "call_$i")
                    val function = toolCall.optJSONObject("function") ?: continue
                    val toolName = function.optString("name", "")
                    val argsStr = function.optString("arguments", "{}")

                    Log.d(TAG, "Executing tool: $toolName")
                    nativeToolExecutor.broadcastLog("[Execute] Tool: $toolName | Args: $argsStr")

                    val params: JsonObject = try {
                        Json.parseToJsonElement(argsStr).jsonObject
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse tool args: $argsStr", e)
                        kotlinx.serialization.json.buildJsonObject { }
                    }

                    val result = nativeToolExecutor.executeTool(
                        ToolExecuteRequest(name = toolName, params = params)
                    )

                    val toolResultMsg = JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", toolCallId)
                        put("content", result.toString())
                    }
                    toolMessages.add(toolResultMsg)

                    saveMessage(ChatMessage(
                        role = "tool",
                        content = result.toString(),
                        toolName = toolName,
                        toolCallId = toolCallId
                    ), sessionId)
                }
                continue
            }

            if (content.isNotEmpty()) {
                Log.d(TAG, "LLM returned final text response")
                nativeToolExecutor.broadcastLog("[Loop] Agent responded with text")
                saveMessage(ChatMessage(role = "assistant", content = content, contextConsumedTokens = promptTokens), sessionId)
            } else {
                Log.w(TAG, "LLM returned empty response with finish_reason: $finishReason")
                nativeToolExecutor.broadcastLog("[Warning] Empty LLM response (finish: $finishReason)")
            }

            break
        }

        nativeToolExecutor.broadcastLog("[Loop] OODA cycle completed")
        val lastMsg = _chatMessages.value.lastOrNull()
        if (lastMsg != null && lastMsg.role != "assistant") {
            val sessionId = _currentSessionId.value ?: return
            saveMessage(ChatMessage(role = "assistant", content = "I completed several tool actions but couldn't form a final answer. Please try rephrasing your request."), sessionId)
        }
    }

    private suspend fun sendLlmRequest(requestBodyJson: JSONObject): JSONObject? {
        for (attempt in 1..MAX_RETRIES) {
            try {
                val body = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(llmApiUrl)
                    .post(body)
                    .build()

                Log.d(TAG, "Sending inference request (attempt $attempt)")

                val responseBodyStr = suspendCancellableCoroutine<String> { cont ->
                    val call = httpClient.newCall(request)
                    activeCall = call
                    cont.invokeOnCancellation {
                        Log.d(TAG, "Cancelling in-flight LLM HTTP call")
                        call.cancel()
                        activeCall = null
                    }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            activeCall = null
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            activeCall = null
                            try {
                                val str = response.body?.string() ?: ""
                                if (!response.isSuccessful) {
                                    if (cont.isActive) cont.resumeWithException(
                                        IOException("LLM HTTP ${response.code}: $str")
                                    )
                                } else {
                                    if (cont.isActive) cont.resume(str)
                                }
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resumeWithException(
                                    IOException("Failed to read LLM response body: ${e.message}", e)
                                )
                            } finally {
                                response.close()
                            }
                        }
                    })
                }

                return JSONObject(responseBodyStr)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "LLM request error (attempt $attempt)", e)
                nativeToolExecutor.broadcastLog("[Error] LLM request failed: ${e.message}")
                if (attempt < MAX_RETRIES) {
                    delay(1000L * attempt)
                    continue
                }
            }
        }
        return null
    }

    private suspend fun saveMessage(msg: ChatMessage, sessionId: String) {
        val entity = ChatMessageEntity(
            id = msg.id,
            sessionId = sessionId,
            role = msg.role.lowercase(),
            content = msg.content,
            taskId = null,
            timestamp = System.currentTimeMillis(),
            contextConsumedTokens = msg.contextConsumedTokens,
            toolName = msg.toolName
        )
        database.chatMessageDao().insertMessage(entity)
        // Avoid full list copy — append with structural sharing
        if (_chatMessages.value.none { it.id == msg.id }) {
            _chatMessages.value = _chatMessages.value + msg
        }
    }

    private fun stripThinkTags(text: String): String {
        return THINK_TAG_REGEX.replace(text, "").trim()
    }

    /** Pre-computes the initial system prompt by sending it silently to the LLM. 
     * This warms up the K-V Cache, ensuring that the first user prompt is processed instantly.
     */
    private suspend fun warmupCache() {
        if (_state.value != AgentState.RUNNING || isSmallModel()) return
        Log.d(TAG, "Pre-warming system prompt in KV Cache...")
        try {
            val msgsJson = JSONArray()
            msgsJson.put(JSONObject().apply { put("role", "system"); put("content", getSystemPrompt()) })

            val requestBodyJson = JSONObject().apply {
                put("messages", msgsJson)
                put("max_tokens", 1) // Just force prompt evaluation
                put("temperature", 0.0) // Deterministic
            }
            val responseJson = sendLlmRequest(requestBodyJson) ?: return
            
            // Update context usage right away, so the UI updates to show the token baseline consumed.
            val usage = responseJson.optJSONObject("usage")
            val promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0
            _contextUsed.value = promptTokens
            Log.d(TAG, "Warmed KV cache with $promptTokens tokens")
            nativeToolExecutor.broadcastLog("[System] Cached system prompt (${promptTokens} tokens)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to warm cache: ${e.message}")
        }
    }

    /** 
     * Queries the local hf_models.json database to look up the exact context_length limit 
     * defined for this downloaded model.
     */
    private fun getModelMaxContext(): Int {
        try {
            val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)
            val selectedName = prefs.getString(AppConfig.PREFS_KEY_SELECTED_MODEL_NAME, "") ?: ""
            if (selectedName.isEmpty()) return 2048

            val jsonString = context.assets.open("hf_models.json").bufferedReader().use { it.readText() }
            val modelsArray = JSONArray(jsonString)
            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)
                if (modelObj.getString("name") == selectedName) {
                    return modelObj.optInt("context_length", 2048)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up model max context", e)
        }
        return 2048 // Conservative default
    }

    fun clearMessages() {
        activeCall?.cancel()
        activeCall = null
        agentJob?.cancel()
        agentJob = null
        _isProcessing.value = false
        _chatMessages.value = emptyList()
        _contextUsed.value = 0

        serviceScope.launch {
            createNewSession()
            // As soon as a session clears, inject system prompt to prep cache for instant chat start.
            warmupCache()
        }
        Log.d(TAG, "New session created")
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Stopping AgentLoop...")
        nativeToolExecutor.broadcastLog("[System] Agent stopping...")
        _isProcessing.value = false
        llamaServer.stop()
        agentJob?.cancel()
        agentJob = null
        _state.value = AgentState.IDLE
    }
}
