package com.droidclaw.bridge.tools

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class ToolScreenshot : AndroidTool {
    override val name = "screenshot"
    override val description = "Capture screen as base64 image via MediaProjection"
    override val category = ToolCategory.SCREEN

    override suspend fun execute(params: JsonObject): JsonObject {
        val service = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "SERVICE_NOT_RUNNING"))
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bitmap = takeScreenshot(service)
            if (bitmap != null) {
                val base64 = ByteArrayOutputStream().use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    bitmap.recycle()
                    Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                }
                return encodeResult(ToolResult(true, screenshotB64 = base64))
            }
        }
        
        return encodeResult(ToolResult(false, errorCode = "SCREENSHOT_FAILED_OR_UNSUPPORTED"))
    }

    private suspend fun takeScreenshot(service: AccessibilityService): Bitmap? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.hardwareBuffer, screenshotResult.colorSpace)
                    val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    screenshotResult.hardwareBuffer.close()
                    if (cont.isActive) cont.resume(softwareBitmap)
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        } else {
            if (cont.isActive) cont.resume(null)
        }
    }

    override fun requiresPermission(): List<String> = listOf("BIND_ACCESSIBILITY_SERVICE")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
