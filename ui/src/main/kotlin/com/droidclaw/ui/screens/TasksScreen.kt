package com.droidclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.droidclaw.ui.components.ScreenEmptyState
import com.droidclaw.ui.components.ScreenLoadingState
import com.droidclaw.ui.components.ScreenMessageState
import com.droidclaw.ui.components.StatusBadge
import com.droidclaw.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import com.droidclaw.ui.viewmodels.TasksViewModel
import com.droidclaw.core.AppConfig
import com.droidclaw.ui.Screen
import com.droidclaw.ui.state.UiScreenState

@Composable
fun TasksScreen(navController: NavController = rememberNavController(), tasksViewModel: TasksViewModel = viewModel()) {
    val tasks by tasksViewModel.allTasks.collectAsState()
    val screenState by tasksViewModel.screenState.collectAsState()
    val listState = rememberLazyListState()
    val navigatingTaskId = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tasks) {
        // Clear stale navigation lock if the task list updates after detail screen actions.
        if (navigatingTaskId.value != null && tasks.none { it.id == navigatingTaskId.value }) {
            navigatingTaskId.value = null
        }
    }
    
    // Tasks are populated from Room TaskDao via TasksViewModel
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "TASK MANAGER",
            style = MaterialTheme.typography.titleLarge,
            color = AccentCyan,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Tasks Content
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
                    title = "Task Sync Error",
                    message = state.message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    titleColor = DangerRed,
                    messageColor = TextMuted
                )
            }

            is UiScreenState.Empty -> {
                ScreenEmptyState(
                    title = "No Tasks Yet",
                    message = state.hint ?: "Tasks will appear here when you start\nautomation from the Chat tab.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            UiScreenState.Content -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            title = task.description,
                            status = task.status,
                            color = when (task.status.uppercase()) {
                                AppConfig.TaskStatus.COMPLETED -> StatusGreen
                                AppConfig.TaskStatus.FAILED    -> DangerRed
                                AppConfig.TaskStatus.RUNNING   -> AccentCyan
                                else                           -> TextMuted
                            },
                            onClick = {
                                if (navigatingTaskId.value == task.id) return@TaskCard
                                navigatingTaskId.value = task.id
                                navController.navigate(Screen.TaskDetail(task.id).route) {
                                    launchSingleTop = true
                                }
                                navigatingTaskId.value = null
                            }
                        )
                    }
                }
            }

            UiScreenState.Offline -> {
                ScreenMessageState(
                    title = "Task Service Unavailable",
                    message = "Task data is temporarily unavailable. Try again shortly.",
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
fun TaskCard(title: String, status: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "Open task details"
            ) { onClick() }
            .semantics {
                contentDescription = "Task $title"
                stateDescription = "Status $status"
            }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            StatusBadge(text = status, color = color)
        }
    }
}
