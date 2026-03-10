package com.droidclaw.bridge.tools

import android.location.Location
import android.location.LocationManager
import com.droidclaw.bridge.AndroidTool
import com.droidclaw.bridge.ToolCategory
import com.droidclaw.bridge.accessibility.DroidClawAccessibilityService
import com.droidclaw.bridge.models.ToolResult
import kotlinx.serialization.json.*

class ToolGetLocation : AndroidTool {
    override val name = "get_location"
    override val description = "Return current GPS coordinates"
    override val category = ToolCategory.HARDWARE

    override suspend fun execute(params: JsonObject): JsonObject {
        val context = DroidClawAccessibilityService.instance
            ?: return encodeResult(ToolResult(false, errorCode = "NO_CONTEXT"))

        try {
            val locationManager = context.getSystemService(LocationManager::class.java)
            // Need permission check in real app, assume granted for MCP stub if passing PermissionGuard
            val provider = LocationManager.GPS_PROVIDER
            val location: Location? = locationManager.getLastKnownLocation(provider)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val data = buildJsonObject {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("accuracy", location.accuracy)
                }.toString()
                return encodeResult(ToolResult(true, data = data))
            } else {
                return encodeResult(ToolResult(false, errorCode = "LOCATION_UNAVAILABLE"))
            }
        } catch (e: SecurityException) {
            return encodeResult(ToolResult(false, errorCode = "PERMISSION_DENIED"))
        } catch (e: Exception) {
            return encodeResult(ToolResult(false, errorCode = "LOCATION_ERROR_${e.message}"))
        }
    }

    override fun requiresPermission(): List<String> = listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION")

    private fun encodeResult(result: ToolResult): JsonObject =
        Json.encodeToJsonElement(result).jsonObject
}
