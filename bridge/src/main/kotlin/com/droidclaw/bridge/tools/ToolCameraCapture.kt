package com.droidclaw.bridge.tools

import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolCameraCapture : AndroidTool {
    override val name = "camera_capture"
    override val description = "Take photo with front or rear camera"
    override val category = ToolCategory.HARDWARE

    override suspend fun execute(params: JsonObject): JsonObject {
        // Full camera capture without a surface requires CameraX/Camera2 API wrapper.
        // For MCP stubbing, returning an error until fully integrated with UI/Foreground service.
        return encodeResult(ToolResult(false, errorCode = "NOT_IMPLEMENTED_YET_REQUIRES_FOREGROUND_CAMERA"))
    }

    override fun requiresPermission(): List<String> = listOf("CAMERA")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
