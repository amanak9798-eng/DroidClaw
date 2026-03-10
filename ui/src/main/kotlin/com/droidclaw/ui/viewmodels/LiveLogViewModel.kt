package com.droidclaw.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val LOG_MAX_LINES = 200

data class LogEntry(
    val id: Long = System.nanoTime(),
    val message: String
)

class LiveLogViewModel(application: Application) : AndroidViewModel(application) {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val nativeToolExecutor = ServiceLocator.getNativeToolExecutor(application)

    init {
        viewModelScope.launch {
            nativeToolExecutor.logs.collect { message ->
                addLog(message)
            }
        }
    }

    private fun addLog(message: String) {
        val entry = LogEntry(message = message)
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > LOG_MAX_LINES) {
            current.removeAt(0)
        }
        _logs.value = current
    }
}
