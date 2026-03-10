package com.droidclaw.bridge.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DroidClawAccessibilityService : AccessibilityService() {
    
    companion object {
        @Volatile
        var instance: DroidClawAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We can capture events here if needed, but for MCP we mostly query the active window
    }

    override fun onInterrupt() {
        // Handle interrupt
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
    
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }
}
