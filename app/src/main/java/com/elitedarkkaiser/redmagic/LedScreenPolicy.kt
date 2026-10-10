package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.PowerManager
import android.hardware.display.DisplayManager
import android.view.Display

object LedScreenPolicy {
    fun isScreenInteractive(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) return false
            // AOD/doze and transitional display states must not resume lighting.
            val displays = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            displays.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON
        } catch (_: Throwable) {
            false
        }
    }

    fun blockNonChargingLedWriteIfScreenOff(context: Context, reason: String): Boolean {
        if (isScreenInteractive(context)) return false

        android.util.Log.i(
            "RedmagicLedPolicy",
            "Blocked non-charging LED write while screen is off: $reason"
        )

        ModeTransitionCoordinator.restoreEffectiveOwner(context, reason)

        return true
    }
}
