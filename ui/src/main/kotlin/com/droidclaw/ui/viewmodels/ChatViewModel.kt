package com.droidclaw.ui.viewmodels

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droidclaw.core.AppConfig
import com.droidclaw.core.ModelRegistry
import com.droidclaw.core.db.entity.ChatSessionEntity
import com.droidclaw.llm.DownloadedModel
import com.droidclaw.llm.ModelManager
import com.droidclaw.orchestrator.AgentState
import com.droidclaw.orchestrator.ChatMessage
import com.droidclaw.ui.state.UiScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val agentLoopService = ServiceLocator.getAgentLoopService(application)

    // ── Cached ModelManager — avoids re-instantiation on every call ──────────
    private val modelManager: ModelManager by lazy {
        val context = getApplication<Application>()
        val modelsDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        ModelManager(modelsDir)
    }

    // ── StateFlows — WhileSubscribed stops upstream when UI is not visible ───
    val chatMessages: StateFlow<List<ChatMessage>> = agentLoopService.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val agentState: StateFlow<AgentState> = agentLoopService.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentState.IDLE)

    val isProcessing: StateFlow<Boolean> = agentLoopService.isProcessing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val selectedModelName: StateFlow<String?> = ModelRegistry.selectedModelName
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModelRegistry.selectedModelName.value)

    /** All chat sessions for the sidebar. */
    val sessions: StateFlow<List<ChatSessionEntity>> = agentLoopService.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Active session ID. */
    val currentSessionId: StateFlow<String?> = agentLoopService.currentSessionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** LLM context window limit (tokens). */
    val contextLimit: StateFlow<Int> = agentLoopService.contextLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2048)

    /** Tokens used by the last prompt. */
    val contextUsed: StateFlow<Int> = agentLoopService.contextUsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private var lastSubmittedPrompt: String? = null

    private val _availableModels = MutableStateFlow<List<DownloadedModel>>(emptyList())
    val availableModels: StateFlow<List<DownloadedModel>> = _availableModels.asStateFlow()

    /** Whether a model file is currently available on disk and selected. */
    val isModelLoaded: StateFlow<Boolean> = combine(
        agentState,
        selectedModelName
    ) { state, name ->
        name != null && (state == AgentState.RUNNING || state == AgentState.IDLE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        loadAvailableModels()
    }

    fun loadAvailableModels() {
        // Run disk I/O off the main thread
        viewModelScope.launch(Dispatchers.IO) {
            val models = modelManager.getDownloadedModels()
            _availableModels.value = models
        }
    }

    fun selectModel(modelId: String, modelName: String) {
        val context = getApplication<Application>()
        ModelRegistry.setModel(context, modelId, modelName)
        // Invalidate cached system prompt so the next agent call uses updated tool list
        agentLoopService.invalidatePromptCache()
        if (agentState.value == AgentState.RUNNING || agentState.value == AgentState.ERROR) {
            stopAgent()
        }
    }

    val screenState: StateFlow<UiScreenState> = combine(
        chatMessages,
        isProcessing,
        errorMessage
    ) { messages, processing, error ->
        when {
            error != null -> UiScreenState.Error(error)
            processing && messages.isEmpty() -> UiScreenState.Loading
            messages.isEmpty() -> UiScreenState.Empty("Ready for commands")
            else -> UiScreenState.Content
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiScreenState.Empty("Ready for commands"))

    fun submitPrompt(prompt: String): Boolean {
        if (prompt.isBlank()) return false
        if (isProcessing.value) return false
        _errorMessage.value = null
        lastSubmittedPrompt = prompt

        val context = getApplication<Application>()
        val modelId = ModelRegistry.selectedModelId.value
        if (modelId == null) {
            _errorMessage.value = "No model selected. Please load a model from storage first."
            return false
        }

        // Resolve model path on IO thread before starting the service
        viewModelScope.launch(Dispatchers.IO) {
            val modelFile = modelManager.getModelPath(modelId)
            if (modelFile == null || !modelFile.exists()) {
                _errorMessage.value = if (modelFile == null)
                    "Model '$modelId' not found. Please load a model from storage first."
                else
                    "Model file was deleted. Please load a model from storage first."
                return@launch
            }

            if (agentState.value == AgentState.IDLE || agentState.value == AgentState.ERROR) {
                val intent = Intent(AppConfig.ACTION_START_AGENT).apply {
                    setClassName(context.packageName, AppConfig.FOREGROUND_SERVICE_CLASS)
                    putExtra(AppConfig.EXTRA_MODEL_PATH, modelFile.absolutePath)
                }
                context.startForegroundService(intent)
            }
            agentLoopService.executePrompt(prompt)
        }
        return true
    }

    fun retryLastPrompt() {
        val prompt = lastSubmittedPrompt ?: return
        submitPrompt(prompt)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /** Creates a new chat session. */
    fun newSession() {
        agentLoopService.clearMessages()
    }

    /** Loads an existing session by ID. */
    fun switchSession(sessionId: String) {
        agentLoopService.loadSession(sessionId)
    }

    /** Deletes a session. */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            agentLoopService.deleteSession(sessionId)
        }
    }

    fun stopAgent() {
        val context = getApplication<Application>()
        val intent = Intent(AppConfig.ACTION_STOP_AGENT).apply {
            setClassName(context.packageName, AppConfig.FOREGROUND_SERVICE_CLASS)
        }
        context.startService(intent)
    }
}
