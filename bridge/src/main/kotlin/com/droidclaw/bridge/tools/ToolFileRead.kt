package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*
import java.io.File
import android.util.Base64

class ToolFileRead : AndroidTool {
    override val name = "file_read"
    override val description = "Read text or binary file from storage path"
    override val category = ToolCategory.STORAGE
    override val parameters = mapOf(
        "path" to com.droidclaw.bridge.ToolParam("string", "Absolute file path to read (e.g. '/sdcard/Documents/notes.txt')")
    )

    override suspend fun execute(params: JsonObject): JsonObject {
        val path = params["path"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_PATH"))
        
        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            return encodeResult(ToolResult(false, errorCode = "FILE_NOT_FOUND_OR_UNREADABLE"))
        }
        
        return try {
            val content = file.readText()
            encodeResult(ToolResult(true, data = content))
        } catch (e: Exception) {
            // If binary, try base64 fallback or error
            try {
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                encodeResult(ToolResult(true, data = "base64:$base64"))
            } catch (ex: Exception) {
                encodeResult(ToolResult(false, errorCode = "READ_FAILED_${ex.message}"))
            }
        }
    }

    override fun requiresPermission(): List<String> = listOf("READ_EXTERNAL_STORAGE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
