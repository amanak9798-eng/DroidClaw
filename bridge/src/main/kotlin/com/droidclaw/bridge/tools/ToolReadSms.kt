package com.droidclaw.bridge.tools

import android.net.Uri
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolReadSms : AndroidTool {
    override val name = "read_sms"
    override val description = "Read recent SMS messages with optional filter"
    override val category = ToolCategory.COMMS

    override suspend fun execute(params: JsonObject): JsonObject {
        val count = params["count"]?.jsonPrimitive?.intOrNull ?: 10
        
        val context = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "NO_CONTEXT"))
            
        val messages = mutableListOf<JsonObject>()
        
        return try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                null, null, "date DESC"
            )
            
            cursor?.use {
                val indexAddress = it.getColumnIndex("address")
                val indexBody = it.getColumnIndex("body")
                val indexDate = it.getColumnIndex("date")
                var rows = 0
                
                while (it.moveToNext() && rows < count) {
                    messages.add(buildJsonObject {
                        put("address", if (indexAddress >= 0) it.getString(indexAddress) else "")
                        put("body", if (indexBody >= 0) it.getString(indexBody) else "")
                        put("date", if (indexDate >= 0) it.getLong(indexDate) else 0L)
                    })
                    rows++
                }
            }
            encodeResult(ToolResult(true, data = JsonArray(messages).toString()))
        } catch (e: Exception) {
            encodeResult(ToolResult(false, errorCode = "READ_SMS_FAILED_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = listOf("READ_SMS")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
