package com.droidclaw.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidclaw.llm.DeviceTierDetector
import com.droidclaw.llm.DownloadState
import com.droidclaw.llm.ModelManager
import com.droidclaw.llm.ModelRecommendation
import com.droidclaw.ui.components.DroidClawToggleSwitch
import com.droidclaw.ui.theme.AccentCyan
import com.droidclaw.ui.theme.BackgroundDark
import com.droidclaw.ui.theme.BorderDark
import com.droidclaw.ui.theme.DangerRed
import com.droidclaw.ui.theme.StatusGreen
import com.droidclaw.ui.theme.SurfaceDark
import com.droidclaw.ui.theme.TextMuted
import com.droidclaw.ui.theme.TextPrimary
import com.droidclaw.ui.theme.WarningAmber
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.droidclaw.ui.viewmodels.ModelDownloadViewModel
import com.droidclaw.core.AppConfig
import com.droidclaw.orchestrator.AgentState
import com.droidclaw.orchestrator.HeartbeatScheduler
import com.droidclaw.ui.viewmodels.ServiceLocator
import android.content.Intent

import androidx.navigation.NavHostController

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "CONFIGURATION",
            style = MaterialTheme.typography.titleLarge,
            color = AccentCyan
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSectionTitle("LLM MODEL")
        ModelSelectorCard()
        
        SettingsSectionTitle("AGENT MEMORY & TOOLS")
        Button(
            onClick = { navController.navigate(com.droidclaw.ui.Screen.Memory.route) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = AccentCyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
        ) {
            Text("Memory Vault")
        }
        Button(
            onClick = { navController.navigate(com.droidclaw.ui.Screen.Tools.route) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = AccentCyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
        ) {
            Text("Tool Registry")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSectionTitle("AGENT CONFIG")
        PersistentToggleRow(prefs, AppConfig.PREFS_KEY_AGENT_HEARTBEAT, "Enable HEARTBEAT Scheduler", true) {
            HeartbeatScheduler.schedule(context)
        }
        PersistentToggleRow(prefs, AppConfig.PREFS_KEY_AGENT_AUTO_RETRY, "Auto-retry on Tool Failure", true)
        PersistentToggleRow(prefs, AppConfig.PREFS_KEY_AGENT_SAVE_FAILED, "Save Failed Tasks to Memory", false)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SettingsSectionTitle("SYSTEM PERMISSIONS")
        PersistentToggleRow(prefs, "perm_accessibility", "Accessibility Service (Required)", true)
        PersistentToggleRow(prefs, "perm_notification", "Notification Listener", true)
        PersistentToggleRow(prefs, "perm_sms", "SMS Read/Write", false) { enabled ->
            val guard = ServiceLocator.permissionGuard
            if (enabled) { guard.enableTool("send_sms"); guard.enableTool("read_sms") }
            else { guard.disableTool("send_sms"); guard.disableTool("read_sms") }
        }
        PersistentToggleRow(prefs, "perm_camera", "Camera Access", false) { enabled ->
            if (enabled) ServiceLocator.permissionGuard.enableTool("camera_capture")
            else ServiceLocator.permissionGuard.disableTool("camera_capture")
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = TextMuted
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun ModelSelectorCard() {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    val selectedModelId by com.droidclaw.core.ModelRegistry.selectedModelId.collectAsState()
    val selectedModelName by com.droidclaw.core.ModelRegistry.selectedModelName.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    
    val detector = remember { DeviceTierDetector(context) }
    val tier = remember { detector.detectTier() }
    val totalRamGb = remember { detector.getTotalRamGb() }
    val recommendations = remember { detector.getTopRecommendations(5) }
    val allModels = remember { detector.getAllModels() }

    val modelsDir = remember { context.getExternalFilesDir("models") ?: context.filesDir.resolve("models").also { it.mkdirs() } }
    val modelManager = remember { ModelManager(modelsDir) }
    val factory = remember { viewModelFactory { initializer { ModelDownloadViewModel(modelManager) } } }
    val viewModel: ModelDownloadViewModel = viewModel(factory = factory)

    val downloadState by viewModel.downloadState.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val lastRegistered by viewModel.lastRegisteredModel.collectAsState()
    val catalogNormIds = remember(allModels) { allModels.map { ModelManager.normalizeId(it.id) }.toSet() }
    val localOnlyModels = remember(downloadedModels, catalogNormIds) {
        downloadedModels.filter { it.modelId !in catalogNormIds }
    }

    // Agent loop state for model-load feedback
    val agentLoop = remember { ServiceLocator.getAgentLoopService(application) }
    val agentState by agentLoop.state.collectAsState()

    // Resolve selected model to the actual file on disk
    val modelFile = remember(selectedModelId, downloadedModels) {
        selectedModelId?.let { modelManager.getModelPath(it) }
    }
    val modelFileExists = modelFile?.exists() == true

    // Auto-select model when download or import completes
    LaunchedEffect(lastRegistered) {
        val model = lastRegistered ?: return@LaunchedEffect
        val displayName = viewModel.getLastDownloadDisplayName()
        com.droidclaw.core.ModelRegistry.setModel(context, model.modelId, displayName)
        viewModel.consumeLastRegisteredModel()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importFromUri(context.contentResolver, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        // Header: model name + tier + CHANGE button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(selectedModelName ?: "None", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text("${tier.name} Tier", style = MaterialTheme.typography.bodyMedium, color = AccentCyan)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
            ) {
                Text("CHANGE")
            }
        }

        // Selected model file info + load controls
        if (selectedModelId != null) {
            Spacer(modifier = Modifier.height(12.dp))

            if (modelFileExists && modelFile != null) {
                // File info pill
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BackgroundDark,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\uD83D\uDCC4", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                modelFile.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                formatFileSize(modelFile.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Loading status feedback
                ModelLoadStatus(agentState)

                Spacer(modifier = Modifier.height(8.dp))

                // LOAD / STOP / RETRY button
                ModelLoadButton(
                    agentState = agentState,
                    onLoad = {
                        val intent = Intent(AppConfig.ACTION_START_AGENT).apply {
                            setClassName(context.packageName, AppConfig.FOREGROUND_SERVICE_CLASS)
                            putExtra(AppConfig.EXTRA_MODEL_PATH, modelFile.absolutePath)
                        }
                        context.startForegroundService(intent)
                    },
                    onStop = {
                        val intent = Intent(AppConfig.ACTION_STOP_AGENT).apply {
                            setClassName(context.packageName, AppConfig.FOREGROUND_SERVICE_CLASS)
                        }
                        context.startService(intent)
                    }
                )
            } else {
                // Model selected but file missing on disk
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DangerRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "\u26A0 Model file not found on disk \u2014 download or import it first",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }
            }
        }

        // Show active download progress inline
        val currentDownload = downloadState
        if (currentDownload is DownloadState.Downloading) {
            Spacer(modifier = Modifier.height(12.dp))
            DownloadProgressRow(currentDownload) { viewModel.cancelDownload() }
        }
    }

    if (showDialog) {
        ModelPickerDialog(
            allModels = allModels,
            recommendations = recommendations,
            totalRamGb = totalRamGb,
            downloadState = downloadState,
            downloadedModelIds = downloadedModels.map { it.modelId }.toSet(),
            localOnlyModels = localOnlyModels,
            isImporting = isImporting,
            availableStorageBytes = viewModel.availableStorageBytes.collectAsState().value,
            modelsPath = modelsDir.absolutePath,
            onSelectModel = { model ->
                com.droidclaw.core.ModelRegistry.setModel(context, model.id, model.name)
                showDialog = false
            },
            onSelectLocalModel = { modelId, displayName ->
                com.droidclaw.core.ModelRegistry.setModel(context, modelId, displayName)
                showDialog = false
            },
            onDownload = { model -> viewModel.startDownload(model) },
            onDownloadCustom = { name, url -> viewModel.downloadCustomModel(customName = name, url = url) },
            onCancelDownload = { viewModel.cancelDownload() },
            onDeleteModel = { modelId -> viewModel.deleteModel(modelId) },
            onSync = { viewModel.syncStorage() },
            onBrowseFile = { importLauncher.launch(arrayOf("*/*")) },
            importError = importError,
            onClearImportError = { viewModel.clearImportError() },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun DownloadProgressRow(state: DownloadState.Downloading, onCancel: () -> Unit) {
    val animatedProgress by animateFloatAsState(targetValue = state.progress, label = "download_progress")
    val downloadedMb = state.bytesDownloaded / (1024.0 * 1024.0)
    val totalMb = state.totalBytes / (1024.0 * 1024.0)
    val speedMbps = state.speedBytesPerSec / (1024.0 * 1024.0)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Downloading… %.0f%%".format(animatedProgress * 100),
                style = MaterialTheme.typography.bodySmall,
                color = AccentCyan
            )
            TextButton(onClick = onCancel) {
                Text("CANCEL", color = DangerRed, style = MaterialTheme.typography.labelSmall)
            }
        }
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = AccentCyan,
            trackColor = BorderDark
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "%.1f / %.1f MB  •  %.1f MB/s".format(downloadedMb, totalMb, speedMbps),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
private fun ModelLoadStatus(agentState: AgentState) {
    when (agentState) {
        AgentState.IDLE -> { /* nothing to show */ }
        AgentState.STARTING_LLM -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = AccentCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading model\u2026", style = MaterialTheme.typography.bodySmall, color = AccentCyan)
            }
        }
        AgentState.RUNNING -> {
            Text("\u2713 Model loaded successfully", style = MaterialTheme.typography.bodySmall, color = StatusGreen)
        }
        AgentState.ERROR -> {
            Text("\u2717 Failed to load model", style = MaterialTheme.typography.bodySmall, color = DangerRed)
        }
    }
}

@Composable
private fun ModelLoadButton(
    agentState: AgentState,
    onLoad: () -> Unit,
    onStop: () -> Unit
) {
    when (agentState) {
        AgentState.IDLE -> {
            Button(
                onClick = onLoad,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BackgroundDark)
            ) {
                Text("LOAD MODEL", fontWeight = FontWeight.Bold)
            }
        }
        AgentState.STARTING_LLM -> {
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = SurfaceDark,
                    disabledContentColor = TextMuted
                )
            ) {
                Text("LOADING\u2026")
            }
        }
        AgentState.RUNNING -> {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextPrimary)
            ) {
                Text("STOP MODEL", fontWeight = FontWeight.Bold)
            }
        }
        AgentState.ERROR -> {
            Button(
                onClick = onLoad,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = BackgroundDark)
            ) {
                Text("RETRY LOAD", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun ModelPickerDialog(
    allModels: List<ModelRecommendation>,
    recommendations: List<ModelRecommendation>,
    totalRamGb: Double,
    downloadState: DownloadState,
    downloadedModelIds: Set<String>,
    localOnlyModels: List<com.droidclaw.llm.DownloadedModel>,
    isImporting: Boolean,
    availableStorageBytes: Long,
    modelsPath: String,
    onSelectModel: (ModelRecommendation) -> Unit,
    onSelectLocalModel: (modelId: String, displayName: String) -> Unit,
    onDownload: (ModelRecommendation) -> Unit,
    onDownloadCustom: (String, String) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onSync: () -> Unit,
    onBrowseFile: () -> Unit,
    importError: String?,
    onClearImportError: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredModels = remember(searchQuery, allModels) {
        if (searchQuery.isBlank()) allModels
        else allModels.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val availableGb = availableStorageBytes / (1024.0 * 1024.0 * 1024.0)
    var syncFeedback by remember { mutableStateOf(false) }
    if (syncFeedback) {
        LaunchedEffect(Unit) {
            delay(2000)
            syncFeedback = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Model", color = TextPrimary) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Storage info + Sync button row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Storage: %.1f GB available".format(availableGb),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        if (syncFeedback) {
                            Text("✓ Synced", style = MaterialTheme.typography.labelSmall, color = StatusGreen)
                        } else {
                            TextButton(
                                onClick = { onSync(); syncFeedback = true },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("⟳ SYNC STORAGE", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                            }
                        }
                    }
                }
                
                // Manual Placement Instructions + Browse button
                item {
                    Text(
                        "To add models manually, place .gguf files in:\n$modelsPath",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isImporting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = AccentCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Importing file…", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                        }
                    } else {
                        Button(
                            onClick = onBrowseFile,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan.copy(alpha = 0.12f),
                                contentColor = AccentCyan
                            )
                        ) {
                            Text("📂  BROWSE & IMPORT .GGUF", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (importError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(DangerRed.copy(alpha = 0.12f))
                                .clickable { onClearImportError() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "⚠ $importError  (tap to dismiss)",
                                style = MaterialTheme.typography.labelSmall,
                                color = DangerRed
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
                
                // Custom URL Download
                item {
                    var customName by remember { mutableStateOf("") }
                    var customUrl by remember { mutableStateOf("") }
                    
                    Text("Custom URL Download", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Model Name (e.g. Llama-3-8B)", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentCyan,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = BackgroundDark
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Direct GGUF URL", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentCyan,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = BackgroundDark
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { 
                            if (customName.isNotBlank() && customUrl.isNotBlank()) {
                                onDownloadCustom(customName, customUrl)
                                customName = ""
                                customUrl = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.15f), contentColor = AccentCyan),
                        enabled = customName.isNotBlank() && customUrl.isNotBlank() && downloadState !is DownloadState.Downloading
                    ) {
                        Text("DOWNLOAD CUSTOM MODEL", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Browse Recommended Models", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search models...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentCyan,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = BackgroundDark
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                
                items(filteredModels, key = { it.id }) { model ->
                    ModelListItem(
                        model = model,
                        isRecommended = recommendations.contains(model),
                        exceedsRam = model.minRamGb > totalRamGb,
                        isDownloaded = downloadedModelIds.contains(ModelManager.normalizeId(model.id)),
                        downloadState = downloadState,
                        onSelect = { onSelectModel(model) },
                        onDownload = { onDownload(model) },
                        onCancelDownload = onCancelDownload,
                        onDelete = { onDeleteModel(model.id) }
                    )
                }

                if (filteredModels.isEmpty()) {
                    item {
                        Text("No models found matching your search.", color = DangerRed, modifier = Modifier.padding(top = 8.dp))
                    }
                }

                // Local Files section — .gguf files present on disk but not in the catalog
                if (localOnlyModels.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "LOCAL FILES",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(localOnlyModels, key = { it.modelId }) { localModel ->
                        val sizeMb = localModel.sizeBytes / (1024.0 * 1024.0)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    localModel.modelId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    "%.0f MB  •  Local file".format(sizeMb),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                            TextButton(
                                onClick = { onSelectLocalModel(localModel.modelId, localModel.modelId) },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("SELECT", color = AccentCyan, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AccentCyan)
            }
        },
        containerColor = BackgroundDark,
        titleContentColor = TextPrimary,
        textContentColor = TextMuted
    )
}

@Composable
private fun ModelListItem(
    model: ModelRecommendation,
    isRecommended: Boolean,
    exceedsRam: Boolean,
    isDownloaded: Boolean,
    downloadState: DownloadState,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val isCurrentlyDownloading = downloadState is DownloadState.Downloading && downloadState.modelId == model.id
    val itemAlpha = if (exceedsRam) 0.5f else 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(itemAlpha)
            .clickable(enabled = isDownloaded) { onSelect() }
            .padding(vertical = 12.dp)
    ) {
        // Row 1: Name + badges
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isDownloaded) {
                Text("✅ ", style = MaterialTheme.typography.bodyMedium)
            }
            Text(model.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            if (isRecommended) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(color = WarningAmber.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text("Recommended", color = WarningAmber, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
            if (model.curated) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(color = AccentCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                    Text("Curated", color = AccentCyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Specs
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Size: ${model.parameterCount}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text("Quant: ${model.quantization}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text("RAM: ${model.minRamGb}GB", style = MaterialTheme.typography.bodySmall, color = if (exceedsRam) DangerRed else TextMuted)
        }

        if (exceedsRam) {
            Spacer(modifier = Modifier.height(2.dp))
            Text("⚠ Exceeds device RAM", color = DangerRed, style = MaterialTheme.typography.bodySmall)
        }

        // Row 3: Download actions
        Spacer(modifier = Modifier.height(6.dp))
        when {
            isCurrentlyDownloading && downloadState is DownloadState.Downloading -> {
                val animatedProgress by animateFloatAsState(targetValue = downloadState.progress, label = "item_progress")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = AccentCyan,
                        trackColor = BorderDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("%.0f%%".format(animatedProgress * 100), style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onCancelDownload, modifier = Modifier.height(28.dp)) {
                        Text("CANCEL", color = DangerRed, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            isDownloaded -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Downloaded", color = StatusGreen, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Row {
                        TextButton(onClick = onSelect, modifier = Modifier.height(28.dp)) {
                            Text("SELECT", color = AccentCyan, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = onDelete, modifier = Modifier.height(28.dp)) {
                            Text("DELETE", color = DangerRed, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            model.downloadUrl != null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sizeText = model.fileSizeMb?.let { "${it} MB" } ?: "Size unknown"
                    Text(sizeText, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.15f), contentColor = AccentCyan),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("DOWNLOAD", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            else -> {
                Text("Not available offline", style = MaterialTheme.typography.bodySmall, color = TextMuted.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun PersistentToggleRow(
    prefs: android.content.SharedPreferences,
    key: String,
    title: String,
    defaultValue: Boolean,
    onToggle: ((Boolean) -> Unit)? = null
) {
    var checked by remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        DroidClawToggleSwitch(
            checked = checked,
            onCheckedChange = {
                checked = it
                prefs.edit().putBoolean(key, it).apply()
                onToggle?.invoke(it)
            }
        )
    }
}

@Composable
fun SettingsToggleRow(title: String, initialChecked: Boolean) {
    var checked by remember { mutableStateOf(initialChecked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        DroidClawToggleSwitch(checked = checked, onCheckedChange = { checked = it })
    }
}
