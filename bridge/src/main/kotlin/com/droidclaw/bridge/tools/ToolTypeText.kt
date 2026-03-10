package com.droidclaw.bridge.tools

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.accessibility.ElementFinder
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolTypeText : AndroidTool {
    override val name = "type_text"
    override val description = "Type text into the currently focused input field"
    override val category = ToolCategory.SCREEN
    override val parameters = mapOf(
        "text" to com.droidclaw.bridge.ToolParam("string", "The text to type into the field"),
        "target" to com.droidclaw.bridge.ToolParam("string", "Accessibility node ID of the input field. If omitted, types into the currently focused field.", required = false)
    )

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "SERVICE_NOT_RUNNING"))
            
        val text = params["text"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_TEXT"))
        val target = params["target"]?.jsonPrimitive?.content
        
        var nodeToType: AccessibilityNodeInfo? = null
        if (target != null) {
            nodeToType = ElementFinder.findNodeById(service.getRootNode(), target)
        } else {
            // Find focused element
            nodeToType = service.getRootNode()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }
        
        if (nodeToType == null || !nodeToType.isEditable) {
            return encodeResult(ToolResult(false, errorCode = "NO_EDITABLE_NODE_FOUND"))
        }

        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val success = nodeToType.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        
        return encodeResult(ToolResult(success, if (success) "Typed text: $text" else "Failed to type"))
    }

    override fun requiresPermission(): List<String> = listOf("BIND_ACCESSIBILITY_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
