package com.droidclaw.bridge.tools

import android.telephony.SmsManager
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolSendSms : AndroidTool {
    override val name = "send_sms"
    override val description = "Send SMS to a phone number"
    override val category = ToolCategory.COMMS
    override val parameters = mapOf(
        "number" to com.droidclaw.bridge.ToolParam("string", "Recipient phone number (e.g. '+1234567890')"),
        "message" to com.droidclaw.bridge.ToolParam("string", "SMS text message body")
    )

    override suspend fun execute(params: JsonObject): JsonObject {
        val number = params["number"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_NUMBER"))
        val message = params["message"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_MESSAGE"))
        
        return try {
            val context = DroidClawAccessibilityService.instance
                ?: return encodeResult(ToolResult(false, errorCode = "NO_CONTEXT"))
                
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(number, null, message, null, null)
            encodeResult(ToolResult(true, data = "SMS sent to $number"))
        } catch (e: Exception) {
            encodeResult(ToolResult(false, errorCode = "SMS_FAILED_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = listOf("SEND_SMS")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
