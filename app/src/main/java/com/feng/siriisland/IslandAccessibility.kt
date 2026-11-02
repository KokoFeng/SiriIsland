package com.feng.siriisland

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class IslandAccessibility : AccessibilityService() {
    private var c: IslandController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        c?.stop()
        c = IslandController(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).also { it.start(true) }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        c?.stop(); c = null
        return super.onUnbind(intent)
    }
}
