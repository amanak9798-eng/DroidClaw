package com.droidclaw.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun testAllToolsRegistered() {
        val registry = ToolRegistry()
        val tools = registry.getAllTools()
        
        // 20 tools should be registered
        assertEquals(20, tools.size)
        
        // Example checks
        assertNotNull(registry.getTool("tap_element"))
        assertNotNull(registry.getTool("run_intent"))
        assertNotNull(registry.getTool("file_read"))
    }
}
