package com.droidclaw.bridge

import java.util.Collections

class PermissionGuard {

    private val enabledTools: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun enableTool(toolName: String) {
        enabledTools.add(toolName)
    }

    fun disableTool(toolName: String) {
        enabledTools.remove(toolName)
    }

    fun isAllowed(tool: AndroidTool): Boolean {
        // In PRD, every tool needs explicit toggle to be enabled
        return enabledTools.contains(tool.name)
    }
}
