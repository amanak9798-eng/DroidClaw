package com.droidclaw.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.droidclaw.llm.DeviceTierDetector
import com.droidclaw.llm.ModelManager
import com.droidclaw.llm.ModelRecommendation
import com.droidclaw.llm.DownloadState
import com.droidclaw.ui.theme.*
import com.droidclaw.ui.viewmodels.ModelDownloadViewModel
import com.droidclaw.core.AppConfig
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 3
    var selectedModel by remember { mutableStateOf<ModelRecommendation?>(null) }
    val context = LocalContext.current

    // Instantiate detector and get models once
    val detector = remember { DeviceTierDetector(context) }
    val tier = remember { detector.detectTier() }
    val totalRamGb = remember { detector.getTotalRamGb() }
    val recommendations = remember { detector.getTopRecommendations(5) }
    val allModels = remember { detector.getAllModels() }

    // Called when the user skips model selection entirely — marks onboarding complete with no model.
    val completeWithoutModel = {
        val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AppConfig.PREFS_KEY_ONBOARDING_COMPLETE, true).apply()
        onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "DroidClaw Setup",
            style = MaterialTheme.typography.headlineLarge,
            color = AccentCyan,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Step $currentStep of $totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(32.dp))

        when (currentStep) {
            1 -> StepOnePermissions { currentStep = 2 }
            2 -> StepTwoModelSelection(
                allModels = allModels,
                recommendations = recommendations,
                totalRamGb = totalRamGb,
                selectedModel = selectedModel,
                onSelect = { selectedModel = it },
                onNext = { if (selectedModel != null) currentStep = 3 },
                onSkip = completeWithoutModel
            )
            3 -> StepThreeDownload(
                model = selectedModel,
                onBack = { currentStep = 2 },
                onComplete = {
                    val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean(AppConfig.PREFS_KEY_ONBOARDING_COMPLETE, true)
                        .apply()
                    selectedModel?.let { model ->
                        com.droidclaw.core.ModelRegistry.setModel(context, model.id, model.name)
                    }
                    onComplete()
                }
            )
        }
    }
}

@Composable
fun StepOnePermissions(onNext: () -> Unit) {
    var permissionError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filterValues { !it }.keys
        if (denied.isEmpty()) {
            permissionError = null
            onNext()
        } else {
            permissionError = "Permissions denied: ${denied.joinToString { it.substringAfterLast('.') }}. " +
                    "Please grant them in Settings or tap Skip to continue anyway."
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "DroidClaw needs Accessibility and Storage permissions to function as an autonomous agent.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
        if (permissionError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = permissionError ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = DangerRed,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                // READ/WRITE_EXTERNAL_STORAGE were removed on API 33 (Android 13).
                // Only request them on API 32 and below.
                val storagePerms = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                } else {
                    emptyArray()
                }
                launcher.launch(
                    storagePerms + arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BackgroundDark),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Grant Permissions & Continue", style = MaterialTheme.typography.labelLarge)
        }
        if (permissionError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { onNext() }) {
                Text("Skip for now", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun StepTwoModelSelection(
    allModels: List<ModelRecommendation>,
    recommendations: List<ModelRecommendation>,
    totalRamGb: Double,
    selectedModel: ModelRecommendation?,
    onSelect: (ModelRecommendation) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredModels = remember(searchQuery, allModels) {
        if (searchQuery.isBlank()) allModels
        else allModels.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Hardware Analysis",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Based on your device RAM, we highlighted recommended local models, but you can search and choose any model:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(16.dp))

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
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredModels, key = { it.id }) { model ->
                val isSelected = selectedModel == model
                val isRecommended = recommendations.contains(model)
                val exceedsRam = model.minRamGb > totalRamGb
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) SurfaceDark else BackgroundDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(
                            1.dp,
                            if (isSelected) AccentCyan else if (isRecommended) WarningAmber.copy(alpha=0.5f) else BorderDark,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = model.downloadUrl != null) { onSelect(model) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(model.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            if (isRecommended) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = WarningAmber.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("Recommended", color = WarningAmber, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                            if (model.downloadUrl == null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = TextMuted.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("No URL", color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Size: ${model.parameterCount}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text("Quant: ${model.quantization}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text("RAM: ${model.minRamGb}GB", style = MaterialTheme.typography.bodySmall, color = if (exceedsRam) DangerRed else TextMuted)
                        }
                    }
                }
            }
            if (filteredModels.isEmpty()) {
                item {
                    Text(
                        text = "No models found matching your search.",
                        color = DangerRed,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedModel != null && selectedModel.minRamGb > totalRamGb) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Warning: Model requires more RAM than available. Device may crash or run slowly.", color = DangerRed, style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = onNext,
            enabled = selectedModel != null && selectedModel.downloadUrl != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan, 
                contentColor = BackgroundDark,
                disabledContainerColor = SurfaceDark,
                disabledContentColor = TextMuted
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Download Selected Model", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Skip for now — I'll add a model later",
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun StepThreeDownload(
    model: ModelRecommendation?,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val cacheDir = remember { context.getExternalFilesDir("models") ?: File(context.filesDir, "models") }
    val manager = remember { ModelManager(cacheDir) }

    val factory = viewModelFactory {
        initializer { ModelDownloadViewModel(manager) }
    }
    val viewModel: ModelDownloadViewModel = viewModel(factory = factory)

    val downloadState by viewModel.downloadState.collectAsState()

    // Auto-start the download when arriving at this step
    LaunchedEffect(model?.id) {
        if (model != null) viewModel.startDownload(model)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                viewModel.cancelDownload()
                onBack()
            }) {
                Text("← Back", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        Text(
            text = "Model Download",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (model == null) {
            Text(
                text = "No model selected. Go back and select a model.",
                style = MaterialTheme.typography.bodyMedium,
                color = DangerRed,
                textAlign = TextAlign.Center
            )
            return@Column
        }

        Text(
            text = "Downloading ${model.name}...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        when (val state = downloadState) {
            is DownloadState.Downloading -> {
                val progress = if (state.totalBytes > 0) state.bytesDownloaded.toFloat() / state.totalBytes else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = AccentCyan,
                    trackColor = SurfaceDark
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("${(progress * 100).toInt()}%", color = AccentCyan)
            }
            is DownloadState.Completed -> {
                LaunchedEffect(Unit) { onComplete() }
            }
            is DownloadState.Error -> {
                Text("Error: ${state.message}", color = DangerRed)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.startDownload(model) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BackgroundDark)
                ) {
                    Text("Retry Download")
                }
            }
            else -> {
                Button(
                    onClick = { viewModel.startDownload(model) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BackgroundDark),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Start Download", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
