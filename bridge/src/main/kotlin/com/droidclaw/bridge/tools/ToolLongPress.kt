package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.accessibility.ElementFinder
import com.droidclaw.bridge.accessibility.GestureDispatcher
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolLongPress : AndroidTool {
    override val name = "long_press"
    override val description = "Long press on a UI element or coordinates"
    override val category = ToolCategory.SCREEN

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "SERVICE_NOT_RUNNING"))
            
        val target = params["target"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_TARGET"))
        
        val coords = target.split(",")
        if (coords.size == 2) {
            val x = coords[0].toFloatOrNull()
            val y = coords[1].toFloatOrNull()
            if (x != null && y != null) {
                val success = GestureDispatcher.dispatchLongPress(service, x, y)
                return encodeResult(ToolResult(success, if (success) "Long pressed at $x, $y" else "Failed to long press"))
            }
        }
        
        val node = ElementFinder.findNodeById(service.getRootNode(), target)
            ?: return encodeResult(ToolResult(false, errorCode = "NODE_NOT_FOUND"))
            
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val success = GestureDispatcher.dispatchLongPress(service, rect.exactCenterX(), rect.exactCenterY())
        
        return encodeResult(ToolResult(success, if (success) "Long pressed node $target" else "Failed to long press"))
    }

    override fun requiresPermission(): List<String> = listOf("BIND_ACCESSIBILITY_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
