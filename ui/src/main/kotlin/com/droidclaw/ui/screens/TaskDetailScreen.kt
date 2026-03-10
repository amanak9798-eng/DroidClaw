package com.droidclaw.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droidclaw.core.db.entity.ToolCallEntity
import com.droidclaw.ui.components.StatusBadge
import com.droidclaw.ui.theme.*
import com.droidclaw.ui.viewmodels.ServiceLocator
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

class TaskDetailViewModel(application: Application, taskId: String) : AndroidViewModel(application) {
    val toolCalls: Flow<List<ToolCallEntity>> = ServiceLocator.getDatabase(application).taskDao().getToolCallsForTask(taskId)
}

class TaskDetailViewModelFactory(
    private val application: Application,
    private val taskId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TaskDetailViewModel(application, taskId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String, 
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = viewModel(factory = TaskDetailViewModelFactory(LocalContext.current.applicationContext as Application, taskId))
) {
    val calls by viewModel.toolCalls.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        TopAppBar(
            title = { Text("Task Execution Details", color = TextPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(calls, key = { it.id }) { call ->
                ToolCallCard(call)
            }
            if (calls.isEmpty()) {
                item {
                    Text("No local tool calls executed for this task yet.", color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun ToolCallCard(call: ToolCallEntity) {
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
                text = call.toolName,
                style = MaterialTheme.typography.titleMedium,
                color = AccentCyan,
                fontWeight = FontWeight.Bold
            )
            val color = if (call.status.equals("success", ignoreCase=true)) StatusGreen else if (call.status.equals("failed", ignoreCase=true)) DangerRed else TextMuted
            StatusBadge(text = call.status, color = color)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Input parameters:", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(
            text = call.inputJson,
            style = MaterialTheme.typography.bodySmall,
            color = WarningAmber,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        call.resultJson?.let { resultJsonStr ->
            Text("Result:", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            Text(
                text = resultJsonStr,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
