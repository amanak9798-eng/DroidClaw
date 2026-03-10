package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.AccessibilityTreeReader
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolReadScreen : AndroidTool {
    override val name = "read_screen"
    override val description = "Returns accessibility tree (fast) or OCR screenshot (fallback)"
    override val category = ToolCategory.SCREEN

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "SERVICE_NOT_RUNNING"))
            
        val rootNode = service.getRootNode()
            ?: return encodeResult(ToolResult(false, errorCode = "NO_ACTIVE_WINDOW"))
            
        val tree = AccessibilityTreeReader.readFullTree(rootNode)
        
        return encodeResult(ToolResult(true, data = tree.toString()))
    }

    override fun requiresPermission(): List<String> = listOf("BIND_ACCESSIBILITY_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
