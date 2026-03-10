package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawNotificationListener
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolDismissNotification : AndroidTool {
    override val name = "dismiss_notification"
    override val description = "Dismiss notification by key or package"
    override val category = ToolCategory.COMMS

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawNotificationListener.instance
            ?: return encodeResult(ToolResult(false, errorCode = "NOTIFICATION_SERVICE_NOT_RUNNING"))
            
        val key = params["key"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_KEY"))
        
        try {
            service.cancelNotification(key)
            return encodeResult(ToolResult(true, data = "Dismissed notification $key"))
        } catch (e: Exception) {
            return encodeResult(ToolResult(false, errorCode = "DISMISS_FAILED_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = listOf("BIND_NOTIFICATION_LISTENER_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
