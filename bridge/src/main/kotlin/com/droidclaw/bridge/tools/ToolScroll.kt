package com.droidclaw.bridge.tools

import android.view.accessibility.AccessibilityNodeInfo
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.accessibility.ElementFinder
import com.droidclaw.bridge.accessibility.GestureDispatcher
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolScroll : AndroidTool {
    override val name = "scroll"
    override val description = "Scroll in a direction within a scrollable container"
    override val category = ToolCategory.SCREEN

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "SERVICE_NOT_RUNNING"))
            
        val direction = params["direction"]?.jsonPrimitive?.content ?: "down"
        val target = params["target"]?.jsonPrimitive?.content
        
        var scrollableNode = if (target != null) {
            ElementFinder.findNodeById(service.getRootNode(), target)
        } else {
            findFirstScrollable(service.getRootNode())
        }
        
        if (scrollableNode == null) {
            // Fallback to gesture swipe
            return performGestureScroll(service, direction)
        }

        val action = when (direction.lowercase()) {
            "up" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
            "down" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
            "left" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
            "right" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
            else -> return encodeResult(ToolResult(false, errorCode = "INVALID_DIRECTION"))
        }
        
        val success = scrollableNode.performAction(action.id)
        return encodeResult(ToolResult(success, if (success) "Scrolled $direction" else "Failed to scroll"))
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstScrollable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private suspend fun performGestureScroll(service: DroidClawAccessibilityService, direction: String): JsonObject {
        val metrics = service.resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        val offset = Math.min(centerX, centerY) * 0.5f
        
        val success = when (direction.lowercase()) {
            "up" -> GestureDispatcher.dispatchSwipe(service, centerX, centerY - offset, centerX, centerY + offset)
            "down" -> GestureDispatcher.dispatchSwipe(service, centerX, centerY + offset, centerX, centerY - offset)
            "left" -> GestureDispatcher.dispatchSwipe(service, centerX - offset, centerY, centerX + offset, centerY)
            "right" -> GestureDispatcher.dispatchSwipe(service, centerX + offset, centerY, centerX - offset, centerY)
            else -> false
        }
        
        return Json.encodeToJsonElement(ToolResult(success, if (success) "Scrolled via gesture" else "Failed scroll gesture")).jsonObject
    }

    override fun requiresPermission(): List<String> = listOf("BIND_ACCESSIBILITY_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
