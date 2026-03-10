package com.droidclaw.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidclaw.core.db.entity.ChatSessionEntity
import com.droidclaw.orchestrator.AgentState
import com.droidclaw.orchestrator.ChatMessage
import com.droidclaw.ui.components.InlineErrorBanner
import com.droidclaw.ui.components.MarkdownText
import com.droidclaw.ui.components.PulsingDot
import com.droidclaw.ui.components.TypingIndicator
import com.droidclaw.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droidclaw.ui.state.UiScreenState
import com.droidclaw.ui.viewmodels.ChatViewModel

private val THINK_TAG_REGEX = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)

private fun stripThinkTags(text: String): String {
    return THINK_TAG_REGEX.replace(text, "").trim()
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    var text by remember { mutableStateOf("") }
    val messages by viewModel.chatMessages.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val screenState by viewModel.screenState.collectAsState()
    val selectedModelName by viewModel.selectedModelName.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val contextLimit by viewModel.contextLimit.collectAsState()
    val contextUsed by viewModel.contextUsed.collectAsState()
    val listState = rememberLazyListState()

    var isSessionDrawerOpen by remember { mutableStateOf(false) }

    val isModelSelected = selectedModelName != null

    // Scroll only when message count changes — not on every isProcessing flip
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
            // --- Top Bar ---
            ChatTopBar(
                modelName = selectedModelName,
                availableModels = availableModels,
                agentState = agentState,
                isProcessing = isProcessing,
                onStop = { viewModel.stopAgent() },
                onNewSession = { viewModel.newSession() },
                onModelSelected = { id, name -> viewModel.selectModel(id, name) },
                onOpenSessionDrawer = { isSessionDrawerOpen = true }
            )

            // --- Context Usage Bar ---
            if (contextUsed > 0) {
                ContextUsageBar(
                    used = contextUsed,
                    limit = contextLimit
                )
            }

            // --- Messages Area ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (screenState is UiScreenState.Empty && !isProcessing) {
                    EmptyStateContent(
                        isModelSelected = isModelSelected,
                        onSuggestionTap = { suggestion ->
                            text = suggestion
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            when (msg.role) {
                                "user"      -> UserBubble(msg.content)
                                "assistant" -> AssistantBubble(stripThinkTags(msg.content), msg.contextConsumedTokens)
                                "tool"      -> ToolCallCard(msg.content, msg.toolName)
                                else        -> AssistantBubble(stripThinkTags(msg.content), msg.contextConsumedTokens)
                            }
                        }

                        if (isProcessing) {
                            item(key = "__typing__") {
                                ThinkingBubble()
                            }
                        }
                    }
                }
            }

            // --- Error Banner ---
            val errorMessage = (screenState as? UiScreenState.Error)?.message
            errorMessage?.let { msg ->
                InlineErrorBanner(
                    message = msg,
                    modifier = Modifier.fillMaxWidth(),
                    onRetry = { viewModel.retryLastPrompt() },
                    onDismiss = { viewModel.clearError() }
                )
            }

            if (agentState == AgentState.ERROR && errorMessage == null) {
                InlineErrorBanner(
                    message = "Agent error — try sending your message again.",
                    modifier = Modifier.fillMaxWidth(),
                    onRetry = { viewModel.retryLastPrompt() }
                )
            }

            // --- Model Not Loaded Alert ---
            if (!isModelSelected) {
                ModelNotLoadedBanner()
            }

            // --- Input Bar ---
            ChatInputBar(
                text = text,
                onTextChange = { text = it },
                isProcessing = isProcessing,
                isEnabled = isModelSelected,
                onSend = {
                    if (text.isNotBlank()) {
                        val accepted = viewModel.submitPrompt(text.trim())
                        if (accepted) text = ""
                    }
                },
                onStop = { viewModel.stopAgent() }
            )
        }

        // --- Session Drawer Overlay ---
        AnimatedVisibility(
            visible = isSessionDrawerOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it }
        ) {
            SessionDrawer(
                sessions = sessions,
                currentSessionId = currentSessionId,
                onSessionSelected = { sessionId ->
                    viewModel.switchSession(sessionId)
                    isSessionDrawerOpen = false
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
                onNewSession = {
                    viewModel.newSession()
                    isSessionDrawerOpen = false
                },
                onDismiss = { isSessionDrawerOpen = false }
            )
        }
    }
}

// ─── Context Usage Bar ──────────────────────────────────────────────

@Composable
private fun ContextUsageBar(used: Int, limit: Int) {
    val fraction = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "context_progress")
    val barColor = when {
        fraction > 0.9f -> DangerRed
        fraction > 0.7f -> WarningAmber
        else            -> AccentCyan
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Context", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                text = "$used / $limit tokens",
                style = MaterialTheme.typography.labelSmall,
                color = barColor,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = barColor,
            trackColor = BorderDark,
        )
    }
}

// ─── Model Not Loaded Banner ────────────────────────────────────────

@Composable
private fun ModelNotLoadedBanner() {
    Surface(
        color = WarningAmber.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "No model loaded. Please load a model from storage first.",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Session Drawer ─────────────────────────────────────────────────

@Composable
private fun SessionDrawer(
    sessions: List<ChatSessionEntity>,
    currentSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = SurfaceDark,
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 300.dp)
                .fillMaxWidth(0.8f),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chat History",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    FilledTonalButton(
                        onClick = onNewSession,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AccentCyan.copy(alpha = 0.15f),
                            contentColor = AccentCyan
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(color = BorderDark, thickness = 1.dp)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionItem(
                            session = session,
                            isSelected = session.id == currentSessionId,
                            onClick = { onSessionSelected(session.id) },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }

                    if (sessions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No conversations yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss)
        )
    }
}

@Composable
private fun SessionItem(
    session: ChatSessionEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor = if (isSelected) AccentCyan.copy(alpha = 0.1f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) AccentCyan else TextPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatTimeAgo(session.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        if (!isSelected) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete session",
                    tint = TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1  -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24   -> "${hours}h ago"
        days < 7     -> "${days}d ago"
        else         -> "${days / 7}w ago"
    }
}

// ─── Top Bar ────────────────────────────────────────────────────────

@Composable
private fun ChatTopBar(
    modelName: String?,
    availableModels: List<com.droidclaw.llm.DownloadedModel>,
    agentState: AgentState,
    isProcessing: Boolean,
    onStop: () -> Unit,
    onNewSession: () -> Unit,
    onModelSelected: (String, String) -> Unit,
    onOpenSessionDrawer: () -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Surface(color = SurfaceDark, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenSessionDrawer, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Chat History",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Agent avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("◉", color = AccentCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier
                .weight(1f)
                .clickable { isDropdownExpanded = !isProcessing }
            ) {
                Text(
                    text = "DroidClaw",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = when {
                        isProcessing                        -> "Thinking…"
                        agentState == AgentState.RUNNING    -> modelName ?: "Ready"
                        agentState == AgentState.STARTING_LLM -> "Loading model…"
                        agentState == AgentState.ERROR      -> "Error"
                        else                                -> modelName?.let { "Model: $it" } ?: "Select a model"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isProcessing              -> AccentCyan
                        agentState == AgentState.ERROR -> DangerRed
                        else                      -> TextMuted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    if (availableModels.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No models found", color = TextMuted) },
                            onClick = { isDropdownExpanded = false }
                        )
                    } else {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.modelId, color = TextPrimary) },
                                onClick = {
                                    onModelSelected(model.modelId, model.modelId)
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onNewSession, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "New Session",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Status indicator — pulsing when processing, static otherwise
            if (isProcessing) {
                PulsingDot(
                    modifier = Modifier.size(10.dp),
                    color = AccentCyan
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                agentState == AgentState.RUNNING -> StatusGreen
                                agentState == AgentState.ERROR   -> DangerRed
                                else                             -> TextMuted.copy(alpha = 0.4f)
                            }
                        )
                        .clearAndSetSemantics {
                            contentDescription = when {
                                agentState == AgentState.RUNNING -> "Agent ready"
                                agentState == AgentState.ERROR   -> "Agent error"
                                else                             -> "Agent idle"
                            }
                        }
                )
            }
        }
    }
}

// ─── Empty State with Suggestion Chips ──────────────────────────────

private val SUGGESTION_PROMPTS = listOf(
    "📱 Open Settings",
    "📸 Take a screenshot",
    "📋 List files in Downloads",
    "🔔 What's on my screen?"
)

@Composable
private fun EmptyStateContent(
    isModelSelected: Boolean = true,
    onSuggestionTap: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Text("◉", color = AccentCyan.copy(alpha = 0.5f), fontSize = 36.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (isModelSelected) "Ready for commands" else "No Model Loaded",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (isModelSelected)
                "Send a message to start the agent.\nIt will use native tools to complete tasks."
            else
                "Please load a model from storage\nto start chatting.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 24.dp),
            lineHeight = 22.sp
        )

        // Suggestion chips — only show when a model is selected
        if (isModelSelected) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Try asking…",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SUGGESTION_PROMPTS.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onSuggestionTap(suggestion) },
                        label = {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentCyan
                            )
                        },
                        modifier = Modifier.widthIn(min = 200.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = AccentCyan.copy(alpha = 0.08f),
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = AccentCyan.copy(alpha = 0.3f),
                            borderWidth = 1.dp
                        )
                    )
                }
            }
        }
    }
}

// ─── User Bubble ────────────────────────────────────────────────────

@Composable
private fun UserBubble(content: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = UserBubble,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

// ─── Assistant Bubble (with Markdown rendering) ──────────────────────

@Composable
private fun AssistantBubble(content: String, contextConsumed: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text("◉", color = AccentCyan, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Surface(
                color = AssistantBubble,
                shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                MarkdownText(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            if (contextConsumed > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$contextConsumed context tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

// ─── Thinking Bubble ────────────────────────────────────────────────

@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text("◉", color = AccentCyan, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = AssistantBubble,
            shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
        ) {
            TypingIndicator(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

// ─── Tool Call Card (expandable) ────────────────────────────────────

@Composable
private fun ToolCallCard(content: String, toolName: String?) {
    val isSuccess = try {
        org.json.JSONObject(content).optBoolean("success", false)
    } catch (e: Exception) {
        content.startsWith("✓") || !content.contains("error", ignoreCase = true)
    }
    
    val displayContent = try {
        val json = org.json.JSONObject(content)
        if (json.optBoolean("success", false)) {
            val data = json.opt("data")
            if (data != null) data.toString() else "Completed successfully"
        } else {
            json.optString("error", "Unknown error")
        }
    } catch (e: Exception) {
        content
    }

    val accentColor = if (isSuccess) StatusGreen else DangerRed
    var expanded by remember { mutableStateOf(false) }

    val semanticsText = buildString {
        append("Tool call ")
        append(toolName ?: "Unknown tool")
        append(if (isSuccess) " succeeded" else " failed")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 36.dp)
    ) {
        // Header row — always visible, tappable to expand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .background(accentColor.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = semanticsText },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = toolName?.let { "Tool: $it" } ?: "Tool call",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isSuccess) "✓" else "✗",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = accentColor.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }

        // Expanded detail block — shows the full content
        AnimatedVisibility(visible = expanded) {
            Surface(
                color = Color(0xFF0D1117),
                shape = RoundedCornerShape(0.dp, 0.dp, 6.dp, 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = Color(0xFFCDD9E5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ─── Input Bar (with character counter + focus glow) ────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isProcessing: Boolean,
    isEnabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused && isEnabled -> AccentCyan.copy(alpha = 0.6f)
            else                   -> BorderDark
        },
        label = "input_border"
    )
    val showCharCounter = text.length > 50

    Surface(color = SurfaceDark, shadowElevation = 8.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    interactionSource = interactionSource,
                    placeholder = {
                        Text(
                            when {
                                !isEnabled -> "Load a model to start…"
                                isProcessing -> "Agent is thinking…"
                                else -> "Message DroidClaw…"
                            },
                            color = TextMuted.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.SansSerif
                            )
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = borderColor,
                        unfocusedBorderColor = BorderDark,
                        focusedContainerColor = InputSurface,
                        unfocusedContainerColor = InputSurface,
                        disabledBorderColor = BorderDark.copy(alpha = 0.5f),
                        disabledContainerColor = InputSurface.copy(alpha = 0.5f),
                        disabledTextColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp
                    ),
                    maxLines = 4,
                    enabled = isEnabled && !isProcessing
                )
                Spacer(modifier = Modifier.width(8.dp))

                if (isProcessing) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = DangerRed,
                            contentColor = TextPrimary
                        ),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(22.dp))
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        modifier = Modifier.size(48.dp),
                        enabled = text.isNotBlank() && isEnabled,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AccentCyan,
                            contentColor = BackgroundDark,
                            disabledContainerColor = AccentCyan.copy(alpha = 0.2f),
                            disabledContentColor = TextMuted
                        ),
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Character counter — only visible when text is long enough
            AnimatedVisibility(visible = showCharCounter) {
                Text(
                    text = "${text.length} chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 16.dp, bottom = 6.dp)
                )
            }
        }
    }
}
