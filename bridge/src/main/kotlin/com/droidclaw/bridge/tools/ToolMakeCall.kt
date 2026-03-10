package com.droidclaw.bridge.tools

import android.content.Intent
import android.net.Uri
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolMakeCall : AndroidTool {
    override val name = "make_call"
    override val description = "Initiate a phone call (requires permission)"
    override val category = ToolCategory.COMMS

    override suspend fun execute(params: JsonObject): JsonObject {
        val number = params["number"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_NUMBER"))

        // Sanitize: allow digits, +, -, (, ), spaces only
        val sanitized = number.replace(Regex("[^\\d+\\-() ]"), "")
        if (sanitized.isBlank()) return encodeResult(ToolResult(false, errorCode = "INVALID_NUMBER"))

        val context = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "NO_CONTEXT"))
            
        return try {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:${Uri.encode(sanitized)}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            encodeResult(ToolResult(true, data = "Calling $sanitized"))
        } catch (e: Exception) {
            encodeResult(ToolResult(false, errorCode = "CALL_FAILED_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = listOf("CALL_PHONE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
