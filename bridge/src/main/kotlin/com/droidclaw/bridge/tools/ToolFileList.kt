package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*
import java.io.File

class ToolFileList : AndroidTool {
    override val name = "file_list"
    override val description = "List files in a directory with optional filter"
    override val category = ToolCategory.STORAGE

    override suspend fun execute(params: JsonObject): JsonObject {
        val path = params["path"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_PATH"))
        
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return encodeResult(ToolResult(false, errorCode = "NOT_A_DIRECTORY"))
        }
        
        return try {
            val filesList = mutableListOf<JsonObject>()
            val files = dir.listFiles()
            if (files == null) {
                return encodeResult(ToolResult(false, errorCode = "PERMISSION_DENIED_OR_IO_ERROR"))
            }
            files.forEach { file ->
                filesList.add(buildJsonObject {
                    put("name", file.name)
                    put("isDirectory", file.isDirectory)
                    put("size", file.length())
                })
            }
            encodeResult(ToolResult(true, data = JsonArray(filesList).toString()))
        } catch (e: Exception) {
            encodeResult(ToolResult(false, errorCode = "LIST_FAILED_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = listOf("READ_EXTERNAL_STORAGE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
