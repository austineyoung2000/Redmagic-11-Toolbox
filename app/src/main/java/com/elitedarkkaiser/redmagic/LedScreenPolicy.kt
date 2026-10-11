package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.PowerManager
import android.hardware.display.DisplayManager
import android.view.Display

object LedScreenPolicy {
    /** Hold CPU before enqueueing screen-off cleanup; a worker-time acquire is too late. */
    fun postScreenEvent(context: Context, handler: Handler, action: String?, reason: String, block: () -> Unit) {
        val off = action == Intent.ACTION_SCREEN_OFF
        val wake = if (off) (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Redmagic:ScreenOffHandoff").apply {
                setReferenceCounted(false)
                acquire(30_000L)
            } else null
        val work = Runnable {
            try {
                if (off) LightingRootExecutor.retryQuarantinedWriters(context)
                android.util.Log.i("RedmagicLedPolicy", "Screen handoff executing reason=$reason screenOff=$off")
                block()
            } finally {
                wake?.let { if (it.isHeld) it.release() }
            }
        }
        // Re-check live ownership inside the existing coordinator. A wake or
        // higher owner arriving while queued still selects the correct output.
        val accepted = if (off) handler.postAtFrontOfQueue(work) else handler.post(work)
        if (!accepted) {
            wake?.let { if (it.isHeld) it.release() }
            android.util.Log.e("RedmagicLedPolicy", "Screen handoff queue rejected reason=$reason")
        }
    }

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
