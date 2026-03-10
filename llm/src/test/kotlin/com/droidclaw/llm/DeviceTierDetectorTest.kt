package com.droidclaw.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTierDetectorTest {

    private fun createDetector(ramGb: Double, jsonStr: String): DeviceTierDetector {
        return object : DeviceTierDetector(null) {
            override fun getTotalRamGb(): Double = ramGb
            override fun getHfModelsJson(): String = jsonStr
        }
    }

    @Test
    fun testDetectTier() {
        assertEquals(DeviceTier.FULL, createDetector(8.0, "[]").detectTier())
        assertEquals(DeviceTier.FULL, createDetector(12.0, "[]").detectTier())
        assertEquals(DeviceTier.STANDARD, createDetector(4.0, "[]").detectTier())
        assertEquals(DeviceTier.STANDARD, createDetector(7.9, "[]").detectTier())
        assertEquals(DeviceTier.NANO, createDetector(3.9, "[]").detectTier())
        assertEquals(DeviceTier.NANO, createDetector(2.0, "[]").detectTier())
    }

    @Test
    fun testGetTopRecommendations() {
        val mockJson = """
            [
                {
                    "name": "model-1gb",
                    "min_ram_gb": 1.0,
                    "quantization": "Q4_0",
                    "parameter_count": "1B"
                },
                {
                    "name": "model-4gb",
                    "min_ram_gb": 4.0,
                    "quantization": "Q4_K_M",
                    "parameter_count": "3B"
                },
                {
                    "name": "model-8gb",
                    "min_ram_gb": 8.0,
                    "quantization": "Q8_0",
                    "parameter_count": "7B"
                }
            ]
        """.trimIndent()

        // Test with 12GB RAM -> Should recommend all, but top is 8gb model
        val detector12 = createDetector(12.0, mockJson)
        val recs12 = detector12.getTopRecommendations(5)
        assertEquals(3, recs12.size)
        assertEquals("model-8gb", recs12[0].name)
        assertEquals(8.0, recs12[0].minRamGb, 0.001)

        // Test with 4.5GB RAM -> Should recommend 4gb and 1gb models
        val detector4 = createDetector(4.5, mockJson)
        val recs4 = detector4.getTopRecommendations(5)
        assertEquals(2, recs4.size)
        assertEquals("model-4gb", recs4[0].name)
        assertEquals(4.0, recs4[0].minRamGb, 0.001)

        // Test with 2GB RAM -> Should only recommend 1gb model
        val detector2 = createDetector(2.0, mockJson)
        val recs2 = detector2.getTopRecommendations(5)
        assertEquals(1, recs2.size)
        assertEquals("model-1gb", recs2[0].name)
        assertEquals(1.0, recs2[0].minRamGb, 0.001)
        
        // Test with extremely low RAM (e.g. 0.4GB) -> Should recommend everything as fallback
        val detectorLow = createDetector(0.4, mockJson)
        val recsLow = detectorLow.getTopRecommendations(5)
        assertEquals(3, recsLow.size) 
        // Our logic falls back to giving all models if device totalRamGb < 0.5 
    }
}
