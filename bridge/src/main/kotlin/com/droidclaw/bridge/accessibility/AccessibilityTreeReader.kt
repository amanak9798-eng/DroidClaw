package com.droidclaw.bridge.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AccessibilityTreeReader {
    
    fun readFullTree(root: AccessibilityNodeInfo?): JsonArray {
        if (root == null) return buildJsonArray { }
        
        val nodes = mutableListOf<JsonObject>()
        traverseNode(root, nodes)
        return buildJsonArray {
            nodes.forEach { add(it) }
        }
    }
    
    private fun traverseNode(node: AccessibilityNodeInfo, nodes: MutableList<JsonObject>) {
        if (node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            // Only add nodes that have some bounds and are either clickable or have text/desc
            val hasText = !node.text.isNullOrBlank()
            val hasDesc = !node.contentDescription.isNullOrBlank()
            val isInteractive = node.isClickable || node.isScrollable || node.isCheckable || node.isEditable
            
            if (hasText || hasDesc || isInteractive) {
                nodes.add(buildJsonObject {
                    put("id", node.viewIdResourceName ?: "")
                    put("text", node.text?.toString() ?: "")
                    put("description", node.contentDescription?.toString() ?: "")
                    put("class", node.className?.toString() ?: "")
                    put("clickable", node.isClickable)
                    put("scrollable", node.isScrollable)
                    put("editable", node.isEditable)
                    put("bounds", buildJsonObject {
                        put("left", bounds.left)
                        put("top", bounds.top)
                        put("right", bounds.right)
                        put("bottom", bounds.bottom)
                    })
                })
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverseNode(child, nodes)
            } finally {
                child.recycle()
            }
        }
    }
}
