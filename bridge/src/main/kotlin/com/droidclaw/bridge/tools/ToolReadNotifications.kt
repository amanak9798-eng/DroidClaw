package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawNotificationListener
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolReadNotifications : AndroidTool {
    override val name = "read_notifications"
    override val description = "Return all active notification content"
    override val category = ToolCategory.COMMS

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawNotificationListener.instance
            ?: return encodeResult(ToolResult(false, errorCode = "NOTIFICATION_SERVICE_NOT_RUNNING"))
            
        val activeNotifs = service.activeNotifications
        val notifsList = mutableListOf<JsonObject>()
        
        for (notif in activeNotifs) {
            val extras = notif.notification.extras
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            
            notifsList.add(buildJsonObject {
                put("id", notif.id)
                put("key", notif.key)
                put("package", notif.packageName)
                put("title", title)
                put("text", text)
            })
        }
        
        return encodeResult(ToolResult(true, data = JsonArray(notifsList).toString()))
    }

    override fun requiresPermission(): List<String> = listOf("BIND_NOTIFICATION_LISTENER_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
