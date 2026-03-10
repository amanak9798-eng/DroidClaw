package com.droidclaw.bridge.tools

import com.droidclaw.bridge.ToolRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidToolsTest {

    @Test
    fun testToolsHavePermissions() {
        val registry = ToolRegistry()
        
        // At least some tools require permissions, check that the method works without crashing
        val tapTool = registry.getTool("tap_element")
        assertTrue(tapTool?.requiresPermission()?.contains("BIND_ACCESSIBILITY_SERVICE") == true)
        
        val callTool = registry.getTool("make_call")
        assertTrue(callTool?.requiresPermission()?.contains("CALL_PHONE") == true)
    }
}
