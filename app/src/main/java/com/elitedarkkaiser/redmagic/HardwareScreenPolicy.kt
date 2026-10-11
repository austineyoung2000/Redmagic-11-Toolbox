package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.PowerManager

object HardwareScreenPolicy {
    private const val HOT_KEEP_COOLING_F = 100f
    private const val SAFE_SHUTDOWN_F = 92f

    @Volatile
    private var screenOffShutdownApplied = false

    fun invalidateCoolingShutdownReceipt() {
        // LED programs may change fan power after an earlier cold shutdown.
        // Invalidate the receipt without changing cooling hardware or policy.
        screenOffShutdownApplied = false
    }

    fun isScreenInteractive(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isInteractive
        } catch (_: Throwable) {
            true
        }
    }

    fun currentTempF(): Float? {
        return HardwareController.readTemperatureF()
            ?: DashboardSnapshot.readCpuTempF().toFloatOrNull()
    }

    fun coolingAllowedWhileScreenOff(tempF: Float?): Boolean {
        return tempF != null && tempF >= HOT_KEEP_COOLING_F
    }

    fun coolingShouldStopWhileScreenOff(tempF: Float?): Boolean {
        return tempF == null || tempF <= SAFE_SHUTDOWN_F
    }

    fun blockNormalLedsWhileScreenOff(context: Context, reason: String): Boolean {
        if (isScreenInteractive(context)) return false

        android.util.Log.i(
            "RedmagicScreenPolicy",
            "Blocked normal/game LED write while screen is off: reason=$reason"
        )

        return true
    }

    @Synchronized
    fun blockCoolingWhileScreenOffUnlessHot(
        context: Context,
        reason: String
    ): Boolean {
        if (isScreenInteractive(context)) {
            screenOffShutdownApplied = false
            return false
        }

        val tempF = currentTempF()

        if (coolingAllowedWhileScreenOff(tempF)) {
            screenOffShutdownApplied = false
            android.util.Log.i(
                "RedmagicScreenPolicy",
                "Allowed cooling while screen is off because temp is hot: reason=$reason tempF=$tempF"
            )
            return false
        }

        android.util.Log.i(
            "RedmagicScreenPolicy",
            "Blocked fan/pump while screen is off: reason=$reason tempF=$tempF"
        )

        if (!screenOffShutdownApplied) {
            HardwareController.enableFan(false)
            HardwareController.enablePump(false)
            screenOffShutdownApplied = true
        }

        return true
    }

    fun blockFanPumpAndNormalLedsWhileScreenOff(context: Context, reason: String): Boolean {
        return blockCoolingWhileScreenOffUnlessHot(context, reason)
    }
}
