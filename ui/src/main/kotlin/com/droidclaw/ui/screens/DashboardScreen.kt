package com.droidclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.content.SharedPreferences
import com.droidclaw.core.AppConfig
import com.droidclaw.core.db.entity.TaskEntity
import com.droidclaw.orchestrator.AgentState
import com.droidclaw.ui.components.StatusBadge
import com.droidclaw.ui.components.DroidClawToggleSwitch
import com.droidclaw.ui.theme.*
import com.droidclaw.ui.viewmodels.ChatViewModel
import com.droidclaw.ui.viewmodels.LiveLogViewModel
import com.droidclaw.ui.viewmodels.SystemStatsViewModel
import com.droidclaw.ui.viewmodels.TasksViewModel

@Composable
fun DashboardScreen(tasksViewModel: TasksViewModel = viewModel()) {
    val statsViewModel: SystemStatsViewModel = viewModel()
    val logVm: LiveLogViewModel = viewModel()
    val chatVm: ChatViewModel = viewModel()

    val stats by statsViewModel.stats.collectAsState()
    val activeTask by tasksViewModel.activeTask.collectAsState()
    val logs by logVm.logs.collectAsState()
    val agentState by chatVm.agentState.collectAsState()
    val isProcessing by chatVm.isProcessing.collectAsState()

    val logListState = rememberLazyListState()

    // Auto-scroll live log to bottom when new entries arrive
    LaunchedEffect(logs.lastOrNull()) {
        if (logs.isNotEmpty()) logListState.animateScrollToItem(logs.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "SYSTEM STATUS",
            style = MaterialTheme.typography.titleLarge,
            color = AccentCyan
        )
        Spacer(modifier = Modifier.height(16.dp))

        // LLM & Agent Status Card
        AgentStatusCard(agentState, isProcessing)

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Grid — Real data from ActivityManager
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "DEVICE RAM",
                value = "${stats.usedRamMb} / ${stats.totalRamMb} MB",
                subtitle = "%.0f%% used".format(stats.ramUsagePercent),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "APP HEAP",
                value = "${stats.appRamMb} MB",
                subtitle = "Native allocation",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "AVAILABLE",
                value = "${stats.availableRamMb} MB",
                subtitle = "Free for models",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Task — populated from Room TaskDao via TasksViewModel
        ActiveTaskCard(activeTask)

        Spacer(modifier = Modifier.height(24.dp))

        // Performance Tuning - Vulkan/NPU toggles
        Text(
            text = "PERFORMANCE TUNING",
            style = MaterialTheme.typography.titleLarge,
            color = AccentCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        PerformanceTuningCard()

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "LIVE LOGS",
            style = MaterialTheme.typography.titleLarge,
            color = AccentCyan
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Log Feed — LiveLogViewModel streams from NativeToolExecutor SharedFlow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "Waiting for agent activity…\nLogs will appear here when a task is running.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(state = logListState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(logs, key = { it.id }) { entry ->
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                entry.message.startsWith("[Error]") || entry.message.startsWith("[Denied]") -> DangerRed
                                entry.message.startsWith("[Success]") -> StatusGreen
                                entry.message.startsWith("[Execute]") -> WarningAmber
                                entry.message.startsWith("[System]") -> AccentCyan
                                else -> TextPrimary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgentStatusCard(agentState: AgentState, isProcessing: Boolean) {
    val (statusText, statusColor, dotColor) = when {
        isProcessing -> Triple("Processing Task", AccentCyan, AccentCyan)
        agentState == AgentState.RUNNING -> Triple("LLM Ready — Idle", StatusGreen, StatusGreen)
        agentState == AgentState.STARTING_LLM -> Triple("Loading LLM Model…", WarningAmber, WarningAmber)
        agentState == AgentState.ERROR -> Triple("Error — Check Logs", DangerRed, DangerRed)
        else -> Triple("Offline", TextMuted, TextMuted)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("AGENT STATUS", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(statusText, style = MaterialTheme.typography.bodyLarge, color = statusColor, fontWeight = FontWeight.Bold)
        }
        StatusBadge(text = agentState.name, color = statusColor)
    }
}

@Composable
fun StatCard(title: String, value: String, subtitle: String = "", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = AccentCyan.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun ActiveTaskCard(activeTask: TaskEntity?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeTask?.description ?: "No Active Task",
                style = MaterialTheme.typography.bodyLarge,
                color = if (activeTask != null) TextPrimary else TextMuted,
                fontWeight = FontWeight.Bold
            )
            StatusBadge(text = activeTask?.status ?: "IDLE", color = if (activeTask != null) AccentCyan else TextMuted)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (activeTask != null) "Task ID: ${activeTask.id}" else "Start a task from the Chat tab to see progress here.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
fun PerformanceTuningCard() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(AppConfig.PREFS_MAIN, Context.MODE_PRIVATE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        DashboardToggleRow(prefs, AppConfig.PREFS_KEY_USE_VULKAN, "Enable Vulkan GPU Acceleration", false)
        Spacer(modifier = Modifier.height(8.dp))
        DashboardToggleRow(prefs, AppConfig.PREFS_KEY_USE_NPU, "Enable NPU/NNAPI Acceleration", false)
    }
}

@Composable
fun DashboardToggleRow(
    prefs: SharedPreferences,
    key: String,
    title: String,
    defaultValue: Boolean
) {
    var isEnabled by remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                isEnabled = !isEnabled
                prefs.edit().putBoolean(key, isEnabled).apply()
            }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        DroidClawToggleSwitch(checked = isEnabled, onCheckedChange = { newState ->
            isEnabled = newState
            prefs.edit().putBoolean(key, newState).apply()
        })
    }
}
