package com.droidclaw.app

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

import com.droidclaw.ui.AppScaffold
import com.droidclaw.ui.theme.DroidClawTheme
import com.droidclaw.ui.theme.BackgroundDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startupStartMs = SystemClock.elapsedRealtime()
        setContent {
            val firstFrameReported = remember { mutableStateOf(false) }
            val pendingNavClick = remember { mutableStateOf<Pair<String, Long>?>(null) }
            DroidClawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    AppScaffold(
                        onFirstContentComposed = {
                            if (!firstFrameReported.value) {
                                firstFrameReported.value = true
                                val elapsed = SystemClock.elapsedRealtime() - startupStartMs
                                Log.i("PerfBudget", "startup_to_first_compose_ms=$elapsed")
                                reportFullyDrawn()
                            }
                        },
                        onRouteChanged = { route ->
                            val now = SystemClock.elapsedRealtime()
                            val elapsed = now - startupStartMs
                            Log.d("PerfBudget", "route_visible route=$route elapsed_since_start_ms=$elapsed")

                            pendingNavClick.value?.let { (targetRoute, clickAt) ->
                                if (targetRoute == route) {
                                    val navLatency = now - clickAt
                                    Log.d("PerfBudget", "nav_click_to_visible_ms route=$route latency_ms=$navLatency")
                                    pendingNavClick.value = null
                                }
                            }
                        },
                        onNavItemClicked = { route ->
                            pendingNavClick.value = route to SystemClock.elapsedRealtime()
                        }
                    )
                }
            }
        }
    }
}
