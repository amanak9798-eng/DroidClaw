package com.droidclaw.bridge.tools

import android.content.Intent
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolRunIntent : AndroidTool {
    override val name = "run_intent"
    override val description = "Launch app or broadcast Android Intent with extras"
    override val category = ToolCategory.SYSTEM

    override suspend fun execute(params: JsonObject): JsonObject {
        val action = params["action"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_ACTION"))
        val uriStr = params["uri"]?.jsonPrimitive?.content
        val packageName = params["package_name"]?.jsonPrimitive?.content
        
        if (action !in ALLOWED_ACTIONS) {
            return encodeResult(ToolResult(false, errorCode = "ACTION_NOT_ALLOWED"))
        }

        val context = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "NO_CONTEXT"))

        return try {
            val intent = Intent(action)
            if (uriStr != null) {
                intent.data = android.net.Uri.parse(uriStr)
            }
            if (packageName != null) {
                intent.setPackage(packageName)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(intent)
            encodeResult(ToolResult(true, data = "Intent started: $action"))
        } catch (e: Exception) {
            encodeResult(ToolResult(false, errorCode = "INTENT_FAILED_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = emptyList()

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject

    companion object {
        val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_VIEW,
            Intent.ACTION_MAIN,
            Intent.ACTION_SEND,
            Intent.ACTION_DIAL,
            Intent.ACTION_SEARCH,
            Intent.ACTION_WEB_SEARCH,
            Intent.ACTION_OPEN_DOCUMENT,
            "android.intent.action.MEDIA_PLAY",
            "android.intent.action.MEDIA_PAUSE",
            "android.intent.action.NEXT",
            "android.intent.action.PREVIOUS",
            "android.settings.ACCESSIBILITY_SETTINGS",
            "android.settings.APPLICATION_DETAILS_SETTINGS"
        )
    }
}
