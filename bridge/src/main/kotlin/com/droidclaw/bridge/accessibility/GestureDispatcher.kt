package com.droidclaw.bridge.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object GestureDispatcher {

    suspend fun dispatchTap(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        
        return dispatchGesture(service, gesture)
    }

    suspend fun dispatchSwipe(service: AccessibilityService, startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        
        return dispatchGesture(service, gesture)
    }
    
    suspend fun dispatchLongPress(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        // Duration > 500ms usually triggers long press
        val stroke = GestureDescription.StrokeDescription(path, 0, 600)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        
        return dispatchGesture(service, gesture)
    }

    private suspend fun dispatchGesture(service: AccessibilityService, gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            if (!service.dispatchGesture(gesture, callback, null)) {
                if (continuation.isActive) continuation.resume(false)
            }
            // The Android gesture API has no cancellation mechanism once dispatched;
            // the callback (onCancelled) will still fire and resume the continuation.
            continuation.invokeOnCancellation { /* nothing to clean up — gesture runs to completion */ }
        }
}
