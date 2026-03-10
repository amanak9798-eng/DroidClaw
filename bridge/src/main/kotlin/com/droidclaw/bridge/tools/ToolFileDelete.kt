package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*
import java.io.File

class ToolFileDelete : AndroidTool {
    override val name = "file_delete"
    override val description = "Delete a file or empty directory"
    override val category = ToolCategory.STORAGE

    override suspend fun execute(params: JsonObject): JsonObject {
        val context = DroidClawAccessibilityService.instance 
            ?: return encodeResult(ToolResult(false, errorCode = "NO_ACCESSIBILITY_SERVICE"))
            
        val path = params["path"]?.jsonPrimitive?.content ?: return encodeResult(ToolResult(false, errorCode = "MISSING_PATH"))

        val file = File(path).canonicalFile
        val allowedRoots = listOf(
            context.filesDir.canonicalFile,
            context.cacheDir.canonicalFile,
            context.getExternalFilesDir(null)?.canonicalFile
        ).filterNotNull()

        val isAllowed = allowedRoots.any { root -> file.startsWith(root) }
        if (!isAllowed) {
            return encodeResult(ToolResult(false, errorCode = "PATH_NOT_PERMITTED"))
        }

        if (!file.exists()) {
            return encodeResult(ToolResult(false, errorCode = "FILE_NOT_FOUND"))
        }

        return try {
            val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
            encodeResult(ToolResult(success, if (success) "Deleted $path" else "Delete failed"))
        } catch (e: Exception) {
            encodeResult(ToolResult(false, errorCode = "DELETE_FAILED_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = listOf("WRITE_EXTERNAL_STORAGE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
