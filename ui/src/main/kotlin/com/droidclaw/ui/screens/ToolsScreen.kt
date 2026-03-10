package com.droidclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.ui.components.DroidClawToggleSwitch
import com.droidclaw.ui.components.ScreenEmptyState
import com.droidclaw.ui.components.ScreenLoadingState
import com.droidclaw.ui.components.ScreenMessageState
import com.droidclaw.ui.state.UiScreenState
import com.droidclaw.ui.theme.*
import com.droidclaw.ui.viewmodels.ToolUiState
import com.droidclaw.ui.viewmodels.ToolsViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import com.droidclaw.ui.viewmodels.RiskLevel
import com.droidclaw.ui.viewmodels.permissionRiskLevel

@Composable
fun ToolsScreen() {
    val viewModel: ToolsViewModel = viewModel()
    val filteredTools by viewModel.filteredTools.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val screenState by viewModel.screenState.collectAsState()
    val globalHighRiskLocked by viewModel.globalHighRiskLocked.collectAsState()
    val pendingEnableTool by viewModel.pendingEnableTool.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategory) {
        if (listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0)
        }
    }

    pendingEnableTool?.let { tool ->
        ToolEnableConfirmDialog(
            tool = tool,
            onConfirm = { viewModel.confirmEnableTool() },
            onDismiss = { viewModel.dismissEnableConfirmation() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "TOOL REGISTRY",
            style = MaterialTheme.typography.titleLarge,
            color = AccentCyan,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${filteredTools.size} tools registered",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Category Filter
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = listOf(null) + ToolCategory.entries
            items(categories) { category ->
                val label = category?.displayName ?: "All"
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setCategory(category) },
                    modifier = Modifier.semantics {
                        contentDescription = "Filter tools by $label"
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = AccentCyan,
                        containerColor = SurfaceDark,
                        labelColor = TextMuted
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) AccentCyan else BorderDark
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        GlobalLockCard(
            isLocked = globalHighRiskLocked,
            onToggle = { viewModel.toggleGlobalLock() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tool List
        when (val state = screenState) {
            UiScreenState.Loading -> {
                ScreenLoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                )
            }

            is UiScreenState.Error -> {
                ScreenMessageState(
                    title = "Tool Registry Error",
                    message = state.message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    titleColor = DangerRed,
                    messageColor = TextMuted,
                    actionLabel = "Retry",
                    onAction = { viewModel.refreshTools() }
                )
            }

            is UiScreenState.Empty -> {
                ScreenEmptyState(
                    title = "No Tools Found",
                    message = state.hint ?: "No tools in this category",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    glyph = "⚙"
                )
            }

            UiScreenState.Content -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filteredTools, key = { it.name }) { tool ->
                        ToolCard(
                            tool = tool,
                            globalHighRiskLocked = globalHighRiskLocked,
                            onToggle = { enabled -> viewModel.requestToggle(tool.name, enabled) }
                        )
                    }
                }
            }

            UiScreenState.Offline -> {
                ScreenMessageState(
                    title = "Tool Service Unavailable",
                    message = "Try reloading the tool registry.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    titleColor = WarningAmber,
                    messageColor = TextMuted,
                    actionLabel = "Retry",
                    onAction = { viewModel.refreshTools() }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolCard(tool: ToolUiState, globalHighRiskLocked: Boolean, onToggle: (Boolean) -> Unit) {
    val isHighRiskLocked = globalHighRiskLocked && tool.riskLevel == RiskLevel.HIGH
    val toggleState = when {
        isHighRiskLocked -> "Locked"
        tool.isEnabled -> "Enabled"
        else -> "Disabled"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isHighRiskLocked) DangerRed.copy(alpha = 0.3f) else BorderDark,
                RoundedCornerShape(8.dp)
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${tool.name}. ${tool.category.displayName}. ${tool.riskLevel.label}."
                stateDescription = toggleState
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tool.isEnabled && !isHighRiskLocked) TextPrimary else TextMuted
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = AccentCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = tool.category.displayName,
                        color = AccentCyan,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (isHighRiskLocked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked by global lock",
                        tint = DangerRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            if (tool.permissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tool.permissions.forEach { permission ->
                        PermissionChip(
                            permission = permission,
                            risk = permissionRiskLevel(permission)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        DroidClawToggleSwitch(
            checked = tool.isEnabled,
            onCheckedChange = { if (!isHighRiskLocked) onToggle(it) },
            enabled = !isHighRiskLocked,
            modifier = Modifier.semantics {
                role = Role.Switch
                contentDescription = "Toggle ${tool.name}"
                stateDescription = toggleState
            }
        )
    }
}

@Composable
private fun GlobalLockCard(isLocked: Boolean, onToggle: () -> Unit) {
    val borderColor = if (isLocked) DangerRed.copy(alpha = 0.5f) else BorderDark
    val iconVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen
    val iconTint = if (isLocked) DangerRed else TextMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "Global high-risk tools lock"
                stateDescription = if (isLocked) "Locked" else "Unlocked"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isLocked) "High-Risk Tools Locked" else "High-Risk Tools Unlocked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLocked) DangerRed else TextPrimary
                )
                Text(
                    text = if (isLocked) "All high-risk tools are currently disabled"
                           else "High-risk tools can be enabled individually",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
        DroidClawToggleSwitch(
            checked = isLocked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics {
                role = Role.Switch
                contentDescription = "Toggle global high-risk tools lock"
                stateDescription = if (isLocked) "Locked" else "Unlocked"
            }
        )
    }
}

@Composable
private fun PermissionChip(permission: String, risk: RiskLevel) {
    val bgColor = when (risk) {
        RiskLevel.HIGH -> DangerRed.copy(alpha = 0.15f)
        RiskLevel.MEDIUM -> WarningAmber.copy(alpha = 0.15f)
        RiskLevel.LOW -> TextMuted.copy(alpha = 0.10f)
    }
    val labelColor = when (risk) {
        RiskLevel.HIGH -> DangerRed
        RiskLevel.MEDIUM -> WarningAmber
        RiskLevel.LOW -> TextMuted
    }
    Surface(color = bgColor, shape = RoundedCornerShape(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .semantics {
                    contentDescription = "$permission permission, ${risk.label}"
                }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (risk == RiskLevel.HIGH) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = permission,
                color = labelColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ToolEnableConfirmDialog(
    tool: ToolUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "High-Risk Tool",
                    color = DangerRed,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enable \"${tool.name}\"?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This tool requires the following permissions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                tool.permissions.forEach { permission ->
                    val risk = permissionRiskLevel(permission)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "\u2022 ",
                            color = if (risk == RiskLevel.HIGH) DangerRed else WarningAmber,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = permission,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (risk == RiskLevel.HIGH) DangerRed else WarningAmber
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Enable Anyway", color = DangerRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
