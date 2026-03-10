package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.accessibility.GestureDispatcher
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolSwipe : AndroidTool {
    override val name = "swipe"
    override val description = "Swipe gesture with direction, distance, and speed"
    override val category = ToolCategory.SCREEN
    override val parameters = mapOf(
        "startX" to com.droidclaw.bridge.ToolParam("number", "Start X coordinate in pixels"),
        "startY" to com.droidclaw.bridge.ToolParam("number", "Start Y coordinate in pixels"),
        "endX" to com.droidclaw.bridge.ToolParam("number", "End X coordinate in pixels"),
        "endY" to com.droidclaw.bridge.ToolParam("number", "End Y coordinate in pixels"),
        "durationMs" to com.droidclaw.bridge.ToolParam("number", "Swipe duration in milliseconds (default: 300)", required = false)
    )

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "SERVICE_NOT_RUNNING"))
            
        val startX = params["startX"]?.jsonPrimitive?.floatOrNull
        val startY = params["startY"]?.jsonPrimitive?.floatOrNull
        val endX = params["endX"]?.jsonPrimitive?.floatOrNull
        val endY = params["endY"]?.jsonPrimitive?.floatOrNull
        
        if (startX == null || startY == null || endX == null || endY == null) {
            return encodeResult(ToolResult(false, errorCode = "MISSING_COORDINATES"))
        }
        
        val durationMs = params["durationMs"]?.jsonPrimitive?.longOrNull ?: 300L
        
        val success = GestureDispatcher.dispatchSwipe(service, startX, startY, endX, endY, durationMs)
        return encodeResult(ToolResult(success, if (success) "Swiped from ($startX, $startY) to ($endX, $endY)" else "Failed to swipe"))
    }

    override fun requiresPermission(): List<String> = listOf("BIND_ACCESSIBILITY_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
