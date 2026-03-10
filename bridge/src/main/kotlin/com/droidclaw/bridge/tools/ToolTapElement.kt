package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.accessibility.ElementFinder
import com.droidclaw.bridge.accessibility.GestureDispatcher
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolTapElement : AndroidTool {
    override val name = "tap_element"
    override val description = "Tap a UI element by accessibility ID or {x,y} coordinates"
    override val category = ToolCategory.SCREEN
    override val parameters = mapOf(
        "target" to com.droidclaw.bridge.ToolParam("string", "Accessibility node ID (e.g. 'com.app:id/button') or comma-separated x,y pixel coordinates (e.g. '540,960')")
    )

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "SERVICE_NOT_RUNNING"))
            
        val target = params["target"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_TARGET"))
        
        // Check if target is coordinates
        val coords = target.split(",")
        if (coords.size == 2) {
            val x = coords[0].toFloatOrNull()
            val y = coords[1].toFloatOrNull()
            if (x != null && y != null) {
                val success = GestureDispatcher.dispatchTap(service, x, y)
                return encodeResult(ToolResult(success, if (success) "Tapped at $x, $y" else "Failed to tap"))
            }
        }
        
        // Otherwise search node
        val node = ElementFinder.findNodeById(service.getRootNode(), target)
            ?: return encodeResult(ToolResult(false, errorCode = "NODE_NOT_FOUND"))
            
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        if (rect.isEmpty) {
            return encodeResult(ToolResult(false, errorCode = "NODE_NOT_VISIBLE"))
        }

        val success = GestureDispatcher.dispatchTap(service, rect.exactCenterX(), rect.exactCenterY())
        
        return encodeResult(ToolResult(success, if (success) "Tapped node $target" else "Failed to tap"))
    }

    override fun requiresPermission(): List<String> = listOf("BIND_ACCESSIBILITY_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
