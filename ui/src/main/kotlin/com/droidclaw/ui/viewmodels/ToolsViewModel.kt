package com.droidclaw.ui.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droidclaw.core.AppConfig
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.ToolRegistry
import com.droidclaw.ui.state.UiScreenState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

enum class RiskLevel(val label: String, val weight: Int) {
    HIGH("High Risk", 2),
    MEDIUM("Medium Risk", 1),
    LOW("Low Risk", 0)
}

fun permissionRiskLevel(permission: String): RiskLevel = when (permission) {
    "SEND_SMS", "READ_SMS", "CALL_PHONE", "CAMERA",
    "BIND_ACCESSIBILITY_SERVICE", "WRITE_EXTERNAL_STORAGE" -> RiskLevel.HIGH
    "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
    "BIND_NOTIFICATION_LISTENER_SERVICE", "READ_EXTERNAL_STORAGE" -> RiskLevel.MEDIUM
    else -> RiskLevel.LOW
}

data class ToolUiState(
    val name: String,
    val description: String,
    val category: ToolCategory,
    val permissions: List<String>,
    val isEnabled: Boolean,
    val riskLevel: RiskLevel = RiskLevel.LOW
)

class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREF_KEY_GLOBAL_HIGH_RISK_LOCK = "global_high_risk_lock"
    }

    private val registry = ToolRegistry()
    private val prefs: SharedPreferences = application.getSharedPreferences(AppConfig.PREFS_TOOLS, Context.MODE_PRIVATE)
    private val permissionGuard = ServiceLocator.permissionGuard

    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _tools = MutableStateFlow<List<ToolUiState>>(emptyList())
    val tools: StateFlow<List<ToolUiState>> = _tools.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ToolCategory?>(null)
    val selectedCategory: StateFlow<ToolCategory?> = _selectedCategory.asStateFlow()

    private val _filteredTools = MutableStateFlow<List<ToolUiState>>(emptyList())
    val filteredTools: StateFlow<List<ToolUiState>> = _filteredTools.asStateFlow()

    private val _globalHighRiskLocked = MutableStateFlow(false)
    val globalHighRiskLocked: StateFlow<Boolean> = _globalHighRiskLocked.asStateFlow()

    private val _pendingEnableTool = MutableStateFlow<ToolUiState?>(null)
    val pendingEnableTool: StateFlow<ToolUiState?> = _pendingEnableTool.asStateFlow()

    val screenState: StateFlow<UiScreenState> = combine(
        _isLoading,
        _errorMessage,
        _filteredTools,
        _selectedCategory
    ) { loading, error, tools, selectedCategory ->
        when {
            loading -> UiScreenState.Loading
            error != null -> UiScreenState.Error(error)
            tools.isEmpty() -> {
                val hint = if (selectedCategory == null) {
                    "No tools registered"
                } else {
                    "No tools in this category"
                }
                UiScreenState.Empty(hint)
            }
            else -> UiScreenState.Content
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiScreenState.Loading
    )

    init {
        _globalHighRiskLocked.value = prefs.getBoolean(PREF_KEY_GLOBAL_HIGH_RISK_LOCK, false)
        loadTools()
    }

    fun refreshTools() {
        loadTools()
    }

    private fun loadTools() {
        _isLoading.value = true
        _errorMessage.value = null

        runCatching {
            val lockHighRisk = _globalHighRiskLocked.value
            registry.getAllTools().map { tool ->
                val permissions = tool.requiresPermission()
                val riskLevel = permissions.map { permissionRiskLevel(it) }
                    .maxByOrNull { it.weight } ?: RiskLevel.LOW

                val persistedEnabled = prefs.getBoolean("tool_enabled_${tool.name}", false)
                val effectiveEnabled = persistedEnabled && !(lockHighRisk && riskLevel == RiskLevel.HIGH)

                if (persistedEnabled != effectiveEnabled) {
                    prefs.edit().putBoolean("tool_enabled_${tool.name}", effectiveEnabled).apply()
                }

                if (effectiveEnabled) permissionGuard.enableTool(tool.name) else permissionGuard.disableTool(tool.name)

                ToolUiState(
                    name = tool.name,
                    description = tool.description,
                    category = tool.category,
                    permissions = permissions,
                    isEnabled = effectiveEnabled,
                    riskLevel = riskLevel
                )
            }
        }.onSuccess { allTools ->
            _tools.value = allTools
            _pendingEnableTool.value = _pendingEnableTool.value
                ?.takeIf { pending -> allTools.any { it.name == pending.name } }
            applyFilter()
        }.onFailure { error ->
            _tools.value = emptyList()
            _filteredTools.value = emptyList()
            _errorMessage.value = error.message ?: "Failed to load tools"
        }.also {
            _isLoading.value = false
        }
    }

    fun setCategory(category: ToolCategory?) {
        _selectedCategory.value = category
        applyFilter()
    }

    fun toggleTool(toolName: String, enabled: Boolean) {
        prefs.edit().putBoolean("tool_enabled_$toolName", enabled).apply()
        if (enabled) permissionGuard.enableTool(toolName) else permissionGuard.disableTool(toolName)

        _tools.value = _tools.value.map { tool ->
            if (tool.name == toolName) tool.copy(isEnabled = enabled) else tool
        }
        applyFilter()
    }

    private fun applyFilter() {
        val category = _selectedCategory.value
        _filteredTools.value = if (category == null) {
            _tools.value
        } else {
            _tools.value.filter { it.category == category }
        }
    }

    fun toggleGlobalLock() {
        val nowLocked = !_globalHighRiskLocked.value
        _globalHighRiskLocked.value = nowLocked
        prefs.edit().putBoolean(PREF_KEY_GLOBAL_HIGH_RISK_LOCK, nowLocked).apply()
        if (nowLocked) {
            _pendingEnableTool.value = null
            _tools.value = _tools.value.map { tool ->
                if (tool.riskLevel == RiskLevel.HIGH && tool.isEnabled) {
                    prefs.edit().putBoolean("tool_enabled_${tool.name}", false).apply()
                    permissionGuard.disableTool(tool.name)
                    tool.copy(isEnabled = false)
                } else tool
            }
            applyFilter()
        }
    }

    fun requestToggle(toolName: String, enabled: Boolean) {
        val tool = _tools.value.find { it.name == toolName } ?: return
        if (enabled == tool.isEnabled) return

        if (!enabled) {
            if (_pendingEnableTool.value?.name == toolName) {
                _pendingEnableTool.value = null
            }
            toggleTool(toolName, false)
            return
        }

        if (tool.riskLevel == RiskLevel.HIGH) {
            if (_globalHighRiskLocked.value) {
                _pendingEnableTool.value = null
                return
            }
            _pendingEnableTool.value = tool
            return
        }
        toggleTool(toolName, true)
    }

    fun confirmEnableTool() {
        val tool = _pendingEnableTool.value ?: return
        _pendingEnableTool.value = null
        toggleTool(tool.name, true)
    }

    fun dismissEnableConfirmation() {
        _pendingEnableTool.value = null
    }
}
