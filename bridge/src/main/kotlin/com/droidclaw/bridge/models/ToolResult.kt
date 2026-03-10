package com.droidclaw.bridge.models

import kotlinx.serialization.Serializable

@Serializable
data class ToolResult(
    val success: Boolean,
    val data: String? = null,
    val errorCode: String? = null,
    val screenshotB64: String? = null
)
