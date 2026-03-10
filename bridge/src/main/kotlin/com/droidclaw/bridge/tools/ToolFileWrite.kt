package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*
import java.io.File
import android.util.Base64

class ToolFileWrite : AndroidTool {
    override val name = "file_write"
    override val description = "Write or append content to a file"
    override val category = ToolCategory.STORAGE

    override suspend fun execute(params: JsonObject): JsonObject {
        val path = params["path"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_PATH"))
        val content = params["content"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_CONTENT"))
        
        val file = File(path)
        
        return try {
            if (content.startsWith("base64:")) {
                val bytes = Base64.decode(content.substring(7), Base64.NO_WRAP)
                file.writeBytes(bytes)
            } else {
                file.writeText(content)
            }
            encodeResult(ToolResult(true, data = "File written: $path"))
        } catch (e: Exception) {
            encodeResult(ToolResult(false, errorCode = "WRITE_FAILED_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = listOf("WRITE_EXTERNAL_STORAGE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
