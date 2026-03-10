package com.droidclaw.bridge.tools

import android.accessibilityservice.AccessibilityService
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolPressKey : AndroidTool {
    override val name = "press_key"
    override val description = "Send hardware key event (back, home, volume, etc.)"
    override val category = ToolCategory.SCREEN

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "SERVICE_NOT_RUNNING"))
            
        val key = params["key"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_KEY"))
        
        val action = when (key.uppercase()) {
            "HOME" -> AccessibilityService.GLOBAL_ACTION_HOME
            "BACK" -> AccessibilityService.GLOBAL_ACTION_BACK
            "RECENTS" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "POWER_DIALOG" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            else -> return encodeResult(ToolResult(false, errorCode = "UNSUPPORTED_KEY"))
        }
        
        val success = service.performGlobalAction(action)
        return encodeResult(ToolResult(success, if (success) "Pressed $key" else "Failed to press $key"))
    }

    override fun requiresPermission(): List<String> = listOf("BIND_ACCESSIBILITY_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
