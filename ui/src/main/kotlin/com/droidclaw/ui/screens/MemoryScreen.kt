package com.droidclaw.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droidclaw.core.db.entity.MemoryCapsuleEntity
import com.droidclaw.ui.components.ScreenEmptyState
import com.droidclaw.ui.components.ScreenLoadingState
import com.droidclaw.ui.components.ScreenMessageState
import com.droidclaw.ui.state.UiScreenState
import com.droidclaw.ui.theme.AccentCyan
import com.droidclaw.ui.theme.BorderDark
import com.droidclaw.ui.theme.DangerRed
import com.droidclaw.ui.theme.SurfaceDark
import com.droidclaw.ui.theme.TextMuted
import com.droidclaw.ui.theme.TextPrimary
import com.droidclaw.ui.theme.WarningAmber
import com.droidclaw.ui.viewmodels.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ServiceLocator.getDatabase(application).memoryCapsuleDao()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val rawMemories: StateFlow<List<MemoryCapsuleEntity>?> = dao.getAllMemoryCapsules()
        .map<List<MemoryCapsuleEntity>, List<MemoryCapsuleEntity>?> { it }
        .catch { error ->
            _errorMessage.value = error.message ?: "Failed to load memories"
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val memories: StateFlow<List<MemoryCapsuleEntity>> = rawMemories
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _deletingIds = MutableStateFlow<Set<String>>(emptySet())
    val deletingIds: StateFlow<Set<String>> = _deletingIds.asStateFlow()

    val screenState: StateFlow<UiScreenState> = combine(rawMemories, _errorMessage) { list, error ->
        when {
            error != null -> UiScreenState.Error(error)
            list == null -> UiScreenState.Loading
            list.isEmpty() -> UiScreenState.Empty("The agent hasn't remembered any facts yet.")
            else -> UiScreenState.Content
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiScreenState.Loading)

    fun deleteMemory(id: String) {
        if (_deletingIds.value.contains(id)) return
        _deletingIds.value = _deletingIds.value + id
        viewModelScope.launch {
            runCatching { dao.deleteMemoryCapsule(id) }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Failed to forget memory"
                }
            _deletingIds.value = _deletingIds.value - id
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(viewModel: MemoryViewModel = viewModel()) {
    val memories by viewModel.memories.collectAsState()
    val screenState by viewModel.screenState.collectAsState()
    val deletingIds by viewModel.deletingIds.collectAsState()
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AGENT MEMORY",
            style = MaterialTheme.typography.titleLarge,
            color = AccentCyan,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (val state = screenState) {
            UiScreenState.Loading -> {
                ScreenLoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            is UiScreenState.Error -> {
                ScreenMessageState(
                    title = "Memory Load Error",
                    message = state.message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    titleColor = DangerRed,
                    messageColor = TextMuted,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.clearError() }
                )
            }

            is UiScreenState.Empty -> {
                ScreenEmptyState(
                    title = "No Memories Yet",
                    message = state.hint ?: "The agent hasn't remembered any facts about you yet.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    glyph = "◈"
                )
            }

            UiScreenState.Content -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(
                        items = memories,
                        key = { it.id }
                    ) { memory ->
                        MemoryCard(
                            memory = memory,
                            isDeleting = deletingIds.contains(memory.id),
                            onDelete = { viewModel.deleteMemory(memory.id) }
                        )
                    }
                }
            }

            UiScreenState.Offline -> {
                ScreenMessageState(
                    title = "Memory Service Unavailable",
                    message = "Stored memories are temporarily unavailable.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    titleColor = WarningAmber,
                    messageColor = TextMuted
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryCapsuleEntity,
    isDeleting: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = "Memory item. Learned from conversation. ${memory.content}"
                stateDescription = if (isDeleting) "Deleting" else "Ready"
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Learned from conversation",
                style = MaterialTheme.typography.labelSmall,
                color = AccentCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onDelete,
            enabled = !isDeleting,
            modifier = Modifier.semantics {
                contentDescription = if (isDeleting) "Forgetting memory" else "Forget memory"
                stateDescription = if (isDeleting) "Busy" else "Ready"
            }
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AccentCyan
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Forget memory",
                    tint = DangerRed
                )
            }
        }
    }
}
