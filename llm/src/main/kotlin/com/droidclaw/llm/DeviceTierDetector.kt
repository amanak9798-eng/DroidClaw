package com.droidclaw.llm

import android.app.ActivityManager
import android.content.Context
import org.json.JSONArray
import java.io.InputStreamReader

enum class DeviceTier {
    NANO, STANDARD, FULL
}

data class ModelRecommendation(
    val id: String,
    val name: String,
    val quantization: String,
    val minRamGb: Double,
    val parameterCount: String,
    val downloadUrl: String? = null,
    val fileSizeMb: Int? = null,
    val curated: Boolean = false
)

open class DeviceTierDetector(private val context: Context?) {

    fun detectTier(): DeviceTier {
        val totalRamGb = getTotalRamGb()
        return when {
            totalRamGb >= 8.0 -> DeviceTier.FULL
            totalRamGb >= 4.0 -> DeviceTier.STANDARD
            else -> DeviceTier.NANO
        }
    }

    open fun getTotalRamGb(): Double {
        val activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0.0
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    }

    open fun getHfModelsJson(): String {
        return try {
            val stream = context?.assets?.open("hf_models.json") ?: return "[]"
            InputStreamReader(stream).use { it.readText() }
        } catch (e: Exception) {
            "[]"
        }
    }

    fun getTopRecommendations(limit: Int): List<ModelRecommendation> {
        val totalRamGb = getTotalRamGb()
        val jsonStr = getHfModelsJson()
        
        val jsonArray = JSONArray(jsonStr)
        val recommendations = mutableListOf<ModelRecommendation>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val minRam = obj.optDouble("min_ram_gb", Double.MAX_VALUE)
            
            // Only include model if RAM is sufficient. If RAM is undetectable (0.0),
            // fall back to showing NANO-tier models only (conservative approach).
            val ramKnown = totalRamGb > 0.0
            if (minRam <= totalRamGb || (!ramKnown && minRam <= 2.0)) {
                recommendations.add(parseModel(obj))
            }
        }

        return recommendations.sortedByDescending { it.minRamGb }.take(limit)
    }

    fun getAllModels(): List<ModelRecommendation> {
        val jsonStr = getHfModelsJson()
        val jsonArray = JSONArray(jsonStr)
        val allModels = mutableListOf<ModelRecommendation>()

        for (i in 0 until jsonArray.length()) {
            allModels.add(parseModel(jsonArray.getJSONObject(i)))
        }

        return allModels.sortedBy { it.name }
    }

    private fun parseModel(obj: org.json.JSONObject): ModelRecommendation {
        val fullName = obj.getString("name")
        val shortName = fullName.substringAfter("/")
        val downloadUrl = obj.optString("gguf_download_url", "").ifEmpty { null }
        val fileSizeMb = if (obj.has("file_size_mb")) obj.getInt("file_size_mb") else null
        val curated = obj.optBoolean("curated", false)

        return ModelRecommendation(
            id = fullName,
            name = shortName,
            quantization = obj.optString("quantization", "Unknown"),
            minRamGb = obj.optDouble("min_ram_gb", Double.MAX_VALUE),
            parameterCount = obj.optString("parameter_count", "Unknown"),
            downloadUrl = downloadUrl,
            fileSizeMb = fileSizeMb,
            curated = curated
        )
    }
}

