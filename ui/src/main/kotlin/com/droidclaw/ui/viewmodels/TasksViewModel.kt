package com.droidclaw.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droidclaw.core.db.entity.TaskEntity
import com.droidclaw.ui.state.UiScreenState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TasksViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ServiceLocator.getDatabase(application)
    private val taskDao = db.taskDao()

    private val rawTasks: StateFlow<List<TaskEntity>?> = taskDao.getAllTasks()
        .map<List<TaskEntity>, List<TaskEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allTasks: StateFlow<List<TaskEntity>> = rawTasks
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val screenState: StateFlow<UiScreenState> = rawTasks
        .map { tasks ->
            when {
                tasks == null -> UiScreenState.Loading
                tasks.isEmpty() -> UiScreenState.Empty("No tasks yet")
                else -> UiScreenState.Content
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiScreenState.Loading)

    val activeTask: StateFlow<TaskEntity?> = taskDao.getTasksByStatus("RUNNING")
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}
