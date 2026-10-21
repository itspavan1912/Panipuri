package com.myjarvis

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibility : AccessibilityService() {
    companion object { @Volatile var instance: JarvisAccessibility? = null }

    private val h = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun lockScreen() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun powerOff() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        var tries = 0
        val r = object : Runnable {
            override fun run() {
                if (!clickText("Power off", "Shut down", "बंद करें") && ++tries < 8) h.postDelayed(this, 700)
            }
        }
        h.postDelayed(r, 1000)
    }

    // After an app opens a chat, type the message and press Send.
    fun sendWhenReady(msg: String) {
        var tries = 0
        val r = object : Runnable {
            override fun run() {
                if (typeText(msg)) {
                    h.postDelayed({ if (!clickDesc("Send", "भेजें")) clickText("Send", "भेजें") }, 700)
                } else if (++tries < 20) h.postDelayed(this, 700)
            }
        }
        h.postDelayed(r, 2000)
    }

    private fun walk(n: AccessibilityNodeInfo?, p: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (p(n)) return n
        for (i in 0 until n.childCount) walk(n.getChild(i), p)?.let { return it }
        return null
    }

    private fun click(n: AccessibilityNodeInfo?): Boolean {
        var c = n
        while (c != null && !c.isClickable) c = c.parent
        return c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun clickText(vararg labels: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (s in labels) for (n in root.findAccessibilityNodeInfosByText(s)) if (click(n)) return true
        return false
    }

    private fun clickDesc(vararg labels: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return labels.any { s -> click(walk(root) { it.contentDescription?.toString().equals(s, true) }) }
    }

    private fun typeText(msg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val field = walk(root) { it.isEditable } ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, msg)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
