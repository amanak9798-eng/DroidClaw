package com.droidclaw.bridge

import com.droidclaw.bridge.tools.*
import kotlinx.serialization.json.JsonObject

data class ToolParam(
    val type: String,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null
)

interface AndroidTool {
    val name: String
    val description: String
    val category: ToolCategory
    /** Rich parameter schema with descriptions. Override this for detailed schemas. */
    val parameters: Map<String, ToolParam> get() = emptyMap()
    /** Legacy simple schema. Kept for backward compatibility. */
    val parametersSchema: Map<String, String> get() = emptyMap()
    suspend fun execute(params: JsonObject): JsonObject
    fun requiresPermission(): List<String>
}

enum class ToolCategory(val displayName: String) {
    SCREEN("Screen"),
    COMMS("Comms"),
    STORAGE("Storage"),
    HARDWARE("Hardware"),
    SYSTEM("System")
}

class ToolRegistry {

    private val tools = mutableMapOf<String, AndroidTool>()

    init {
        // Screen & Input Tools
        register(ToolTapElement())
        register(ToolSwipe())
        register(ToolReadScreen())
        register(ToolScreenshot())
        register(ToolTypeText())
        register(ToolPressKey())
        register(ToolScroll())
        register(ToolLongPress())
        
        // Communication Tools
        register(ToolSendSms())
        register(ToolReadSms())
        register(ToolReadNotifications())
        register(ToolDismissNotification())
        register(ToolMakeCall())
        
        // Storage & IO Tools
        register(ToolFileRead())
        register(ToolFileWrite())
        register(ToolFileList())
        register(ToolFileDelete())
        
        // Hardware & System Tools
        register(ToolCameraCapture())
        register(ToolGetLocation())
        register(ToolRunIntent())
    }

    fun register(tool: AndroidTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AndroidTool? = tools[name]
    
    fun getAllTools(): List<AndroidTool> = tools.values.toList()

}
