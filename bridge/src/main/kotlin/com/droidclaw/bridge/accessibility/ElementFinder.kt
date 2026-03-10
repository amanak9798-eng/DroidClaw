package com.droidclaw.bridge.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object ElementFinder {

    fun findNodeById(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (root == null) return null
        
        // Exact ID match — recycle all results except the one we return
        val nodesById = root.findAccessibilityNodeInfosByViewId(id)
        if (!nodesById.isNullOrEmpty()) {
            val result = nodesById[0]
            for (i in 1 until nodesById.size) nodesById[i].recycle()
            return result
        }
        
        // Fallback to text/description search
        return findNodeByText(root, id)
    }
    
    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) {
                // Recycle the child reference if it is not the node being returned
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    fun findNodeByCoordinates(root: AccessibilityNodeInfo?, x: Int, y: Int): AccessibilityNodeInfo? {
        if (root == null) return null
        return findDeepestNodeAtPoint(root, x, y)
    }
    
    private fun findDeepestNodeAtPoint(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) return null
        
        // Search children from last to first
        for (i in node.childCount - 1 downTo 0) {
            val child = node.getChild(i) ?: continue
            val found = findDeepestNodeAtPoint(child, x, y)
            if (found != null) return found
        }
        
        return node
    }
}
