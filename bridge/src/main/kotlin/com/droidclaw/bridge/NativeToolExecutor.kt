package com.droidclaw.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

@Serializable
data class ToolExecuteRequest(val name: String, val params: JsonObject)

@Serializable
data class ToolResultResponse(val success: Boolean, val error: String? = null, val data: JsonObject? = null) {
    override fun toString(): String {
        return buildJsonObject {
            put("success", success)
            if (error != null) put("error", error)
            if (data != null) put("data", data)
        }.toString()
    }
}

class NativeToolExecutor(
    private val registry: ToolRegistry, 
    private val permissionGuard: PermissionGuard
) {
    private val _logs = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 100)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    suspend fun broadcastLog(message: String) {
        _logs.emit(message)
    }

    /**
     * Builds the OpenAI function-calling `tools` JSON array from the ToolRegistry.
     * Only includes tools that are currently enabled via PermissionGuard.
     * Uses the rich [ToolParam] schema when available, falling back to legacy parametersSchema.
     */
    fun getToolsSchemaJson(): JSONArray {
        val toolsArray = JSONArray()
        for (tool in registry.getAllTools()) {
            if (!permissionGuard.isAllowed(tool)) continue

            val parametersObj = JSONObject().apply {
                put("type", "object")
                val properties = JSONObject()
                val required = JSONArray()

                // Prefer rich parameters over legacy parametersSchema
                if (tool.parameters.isNotEmpty()) {
                    for ((paramName, param) in tool.parameters) {
                        val paramObj = JSONObject().apply {
                            put("type", param.type)
                            put("description", param.description)
                            if (param.enumValues != null) {
                                put("enum", JSONArray(param.enumValues))
                            }
                        }
                        properties.put(paramName, paramObj)
                        if (param.required) {
                            required.put(paramName)
                        }
                    }
                } else {
                    // Fallback to legacy simple schema
                    for ((paramName, paramType) in tool.parametersSchema) {
                        properties.put(paramName, JSONObject().apply {
                            put("type", paramType)
                        })
                        required.put(paramName)
                    }
                }

                put("properties", properties)
                if (required.length() > 0) put("required", required)
            }

            val functionObj = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", parametersObj)
                })
            }
            toolsArray.put(functionObj)
        }
        return toolsArray
    }

    /**
     * Returns a list of tool names that are currently enabled, for display purposes.
     */
    fun getEnabledToolNames(): List<String> {
        return registry.getAllTools().filter { permissionGuard.isAllowed(it) }.map { it.name }
    }

    /**
     * Returns a plain-text summary of all enabled tools, grouped by category.
     * This is injected into the system prompt so the LLM knows what it can do.
     */
    fun getEnabledToolsSummary(): String {
        val enabledTools = registry.getAllTools().filter { permissionGuard.isAllowed(it) }
        if (enabledTools.isEmpty()) return "No tools are currently enabled."

        val grouped = enabledTools.groupBy { it.category }
        val sb = StringBuilder()
        for ((category, tools) in grouped) {
            sb.appendLine("## ${category.displayName} Tools")
            for (tool in tools) {
                sb.appendLine("- **${tool.name}**: ${tool.description}")
            }
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    suspend fun executeTool(request: ToolExecuteRequest): ToolResultResponse {
        return withContext(Dispatchers.IO) {
            val tool = registry.getTool(request.name)
            if (tool == null) {
                broadcastLog("[Error] Tool not found: ${request.name}")
                return@withContext ToolResultResponse(success = false, error = "Tool not found: ${request.name}")
            }

            if (!permissionGuard.isAllowed(tool)) {
                broadcastLog("[Denied] Permission denied for tool: ${request.name}")
                return@withContext ToolResultResponse(success = false, error = "Permission denied for tool: ${request.name}")
            }

            try {
                broadcastLog("[Execute] Running tool: ${request.name} with params: ${request.params}")
                val resultData = tool.execute(request.params)
                broadcastLog("[Success] Tool ${request.name} completed successfully.")
                ToolResultResponse(success = true, data = resultData)
            } catch (e: Exception) {
                broadcastLog("[Error] Tool ${request.name} failed: ${e.message}")
                ToolResultResponse(success = false, error = e.localizedMessage ?: "Unknown tool execution error")
            }
        }
    }
}
