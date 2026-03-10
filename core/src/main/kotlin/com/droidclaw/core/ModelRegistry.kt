package com.droidclaw.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the currently selected LLM model.
 *
 * All model IDs are **normalized** (slashes replaced with underscores) before storage
 * so that the ID stored here always matches the filename produced by ModelManager.
 *
 * Call [init] once at app startup (e.g. in Application.onCreate) to seed from disk.
 * Use [setModel] to persist a new selection atomically.
 */
object ModelRegistry {

    private val _selectedModelId = MutableStateFlow<String?>(null)
    val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _selectedModelName = MutableStateFlow<String?>(null)
    val selectedModelName: StateFlow<String?> = _selectedModelName.asStateFlow()

    /** Seed in-memory state from SharedPreferences. Must be called before any reads. */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)
        _selectedModelId.value = prefs.getString(AppConfig.PREFS_KEY_SELECTED_MODEL, null)
            ?.let { normalizeId(it) }
        _selectedModelName.value = prefs.getString(AppConfig.PREFS_KEY_SELECTED_MODEL_NAME, null)
    }

    /**
     * Update the selected model in memory and persist to SharedPreferences atomically.
     * The [modelId] is normalized before storage so it always matches disk filenames.
     */
    fun setModel(context: Context, modelId: String, modelName: String) {
        val normalized = normalizeId(modelId)
        _selectedModelId.value = normalized
        _selectedModelName.value = modelName
        context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)
            .edit()
            .putString(AppConfig.PREFS_KEY_SELECTED_MODEL, normalized)
            .putString(AppConfig.PREFS_KEY_SELECTED_MODEL_NAME, modelName)
            .apply()
    }

    /** Returns true when a model has been selected (ID is non-null). */
    val hasModel: Boolean get() = _selectedModelId.value != null

    /** Same normalization logic as ModelManager — slashes become underscores. */
    private fun normalizeId(id: String): String =
        id.replace("/", "_").replace("\\", "_")
}
