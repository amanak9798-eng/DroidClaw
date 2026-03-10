package com.droidclaw.ui.viewmodels

import android.app.ActivityManager
import android.app.Application
import android.os.Debug
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NodeStats(
    val totalRamMb: Long = 0,
    val availableRamMb: Long = 0,
    val usedRamMb: Long = 0,
    val appRamMb: Long = 0,
    val ramUsagePercent: Float = 0f
)

class SystemStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val _stats = MutableStateFlow(NodeStats())
    val stats: StateFlow<NodeStats> = _stats.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                val activityManager = getApplication<Application>().getSystemService(ActivityManager::class.java)
                if (activityManager != null) {
                    val memInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memInfo)

                    val totalMb = memInfo.totalMem / (1024 * 1024)
                    val availMb = memInfo.availMem / (1024 * 1024)
                    val usedMb = totalMb - availMb
                    val appMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
                    val usagePercent = if (totalMb > 0) (usedMb.toFloat() / totalMb) * 100f else 0f

                    _stats.value = NodeStats(
                        totalRamMb = totalMb,
                        availableRamMb = availMb,
                        usedRamMb = usedMb,
                        appRamMb = appMb,
                        ramUsagePercent = usagePercent
                    )
                }
                delay(2000)
            }
        }
    }
}
