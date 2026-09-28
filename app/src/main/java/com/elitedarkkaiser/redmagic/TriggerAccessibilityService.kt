package com.elitedarkkaiser.redmagic

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Supplies immediate foreground hints to the independently restartable
 * gameplay runtime. The authoritative root ActivityManager monitor and all
 * overlay, mapping, refresh-rate, and performance ownership intentionally
 * live in [GameplayRuntimeService].
 */
class TriggerAccessibilityService : AccessibilityService() {
    private var lastForwardedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        if (!DeviceCompatibility.isSupportedDevice()) {
            disableSelf()
            return
        }

        GameplayRuntimeService.ensureRunning(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return
        }

        val reportedPackage = event?.packageName
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return

        if (reportedPackage == lastForwardedPackage) {
            return
        }
        lastForwardedPackage = reportedPackage

        GameplayRuntimeService.forwardForegroundHint(
            this,
            reportedPackage
        )
    }

    override fun onInterrupt() {
        GameplayRuntimeService.checkAccessibilityState(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        lastForwardedPackage = null
        GameplayRuntimeService.checkAccessibilityState(this)
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        /* TriggerRootService remains the only physical-trigger owner. */
        return false
    }
}
