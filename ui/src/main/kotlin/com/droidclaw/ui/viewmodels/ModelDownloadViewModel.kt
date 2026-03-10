package com.droidclaw.ui.viewmodels

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidclaw.llm.DownloadState
import com.droidclaw.llm.DownloadedModel
import com.droidclaw.llm.ModelManager
import com.droidclaw.llm.ModelRecommendation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.droidclaw.ui.state.UiScreenState

class ModelDownloadViewModel(
    private val modelManager: ModelManager
) : ViewModel() {

    private data class DownloadRequest(
        val modelId: String,
        val displayName: String,
        val url: String
    )

    val downloadState: StateFlow<DownloadState> = modelManager.downloadState

    private val _downloadedModels = MutableStateFlow<List<DownloadedModel>>(emptyList())
    val downloadedModels: StateFlow<List<DownloadedModel>> = _downloadedModels.asStateFlow()

    private val _availableStorageBytes = MutableStateFlow(0L)
    val availableStorageBytes: StateFlow<Long> = _availableStorageBytes.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    val screenState: StateFlow<UiScreenState> = combine(
        downloadState,
        downloadedModels,
        isImporting,
        importError
    ) { download, models, importing, importFailure ->
        when {
            importFailure != null -> UiScreenState.Error(importFailure)
            download is DownloadState.Error -> UiScreenState.Error(download.message)
            importing -> UiScreenState.Loading
            download is DownloadState.Downloading && models.isEmpty() -> UiScreenState.Loading
            models.isEmpty() -> UiScreenState.Empty("No models downloaded")
            else -> UiScreenState.Content
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiScreenState.Loading)

    /** Set when an import or download completes successfully — consumed by the UI to auto-select. */
    private val _lastRegisteredModel = MutableStateFlow<DownloadedModel?>(null)
    val lastRegisteredModel: StateFlow<DownloadedModel?> = _lastRegisteredModel.asStateFlow()

    private var downloadJob: Job? = null

    /** Tracks the human-readable name for the model currently being downloaded. */
    private var pendingDownloadName: String? = null
    private var lastDownloadRequest: DownloadRequest? = null

    init {
        refreshDownloadedModels()
        refreshStorage()
    }

    fun startDownload(model: ModelRecommendation) {
        val url = model.downloadUrl ?: return
        if (_isImporting.value) return

        _importError.value = null
        modelManager.resetState()
        pendingDownloadName = model.name
        lastDownloadRequest = DownloadRequest(
            modelId = model.id,
            displayName = model.name,
            url = url
        )

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            try {
                modelManager.downloadModel(model.id, url)
                // If download completed, expose the result for auto-selection
                val state = modelManager.downloadState.value
                if (state is DownloadState.Completed) {
                    val normalized = ModelManager.normalizeId(model.id)
                    _lastRegisteredModel.value = DownloadedModel(
                        modelId = normalized,
                        file = state.file,
                        sizeBytes = state.file.length()
                    )
                }
            } catch (_: CancellationException) {
                // cancellation is expected during user-driven cancel/restart
            } finally {
                refreshDownloadedModels()
                refreshStorage()
            }
        }
    }

    fun downloadCustomModel(customName: String, url: String) {
        if (_isImporting.value) return

        val modelId = customName.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_").trim('_')
        if (modelId.isBlank()) {
            _importError.value = "Model name is invalid. Use letters, numbers, '.', '-' or '_'."
            return
        }
        if (!isValidDownloadUrl(url)) {
            _importError.value = "Download URL must start with http:// or https://"
            return
        }

        _importError.value = null
        modelManager.resetState()
        pendingDownloadName = customName
        lastDownloadRequest = DownloadRequest(
            modelId = modelId,
            displayName = customName,
            url = url
        )
        
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            try {
                modelManager.downloadModel(modelId, url)
                val state = modelManager.downloadState.value
                if (state is DownloadState.Completed) {
                    _lastRegisteredModel.value = DownloadedModel(
                        modelId = ModelManager.normalizeId(modelId),
                        file = state.file,
                        sizeBytes = state.file.length()
                    )
                }
            } catch (_: CancellationException) {
                // cancellation is expected during user-driven cancel/restart
            } finally {
                refreshDownloadedModels()
                refreshStorage()
            }
        }
    }

    fun retryLastDownload() {
        val request = lastDownloadRequest ?: return
        pendingDownloadName = request.displayName
        _importError.value = null
        modelManager.resetState()

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            try {
                modelManager.downloadModel(request.modelId, request.url)
                val state = modelManager.downloadState.value
                if (state is DownloadState.Completed) {
                    _lastRegisteredModel.value = DownloadedModel(
                        modelId = ModelManager.normalizeId(request.modelId),
                        file = state.file,
                        sizeBytes = state.file.length()
                    )
                }
            } catch (_: CancellationException) {
                // expected on cancel
            } finally {
                refreshDownloadedModels()
                refreshStorage()
            }
        }
    }

    /** Returns the display name for the last completed operation (download or import). */
    fun getLastDownloadDisplayName(): String {
        return pendingDownloadName
            ?: _lastRegisteredModel.value?.modelId
            ?: "Unknown"
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        modelManager.resetState()
        refreshDownloadedModels()
        refreshStorage()
    }

    fun deleteModel(modelId: String) {
        modelManager.deleteModel(modelId)
        refreshDownloadedModels()
        refreshStorage()
    }

    fun isModelDownloaded(modelId: String): Boolean {
        return modelManager.isModelCached(modelId)
    }

    fun syncStorage() {
        refreshDownloadedModels()
        refreshStorage()
    }

    fun importFromUri(contentResolver: ContentResolver, uri: Uri) {
        if (_isImporting.value) return
        if (downloadState.value is DownloadState.Downloading) {
            _importError.value = "Cancel the active download before importing a model file."
            return
        }
        _importError.value = null
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val result = modelManager.importFromUri(contentResolver, uri)
                if (result == null) {
                    _importError.value = "Selected file is not a .gguf model file."
                } else {
                    _lastRegisteredModel.value = result
                }
            } catch (e: Exception) {
                _importError.value = "Import failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isImporting.value = false
                refreshDownloadedModels()
                refreshStorage()
            }
        }
    }

    fun clearImportError() {
        _importError.value = null
    }

    fun consumeLastRegisteredModel() {
        _lastRegisteredModel.value = null
        pendingDownloadName = null
    }

    private fun isValidDownloadUrl(url: String): Boolean {
        val normalized = url.trim().lowercase()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }

    private fun refreshDownloadedModels() {
        _downloadedModels.value = modelManager.getDownloadedModels()
    }

    private fun refreshStorage() {
        _availableStorageBytes.value = modelManager.getAvailableStorageBytes()
    }
}
