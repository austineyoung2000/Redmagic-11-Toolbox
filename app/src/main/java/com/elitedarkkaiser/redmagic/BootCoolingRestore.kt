package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

/** Only restores explicit manual choices; never records telemetry as a preference. */
internal object BootCoolingRestore {
    private const val PREFS = "boot_cooling_restore"
    private fun done(context: Context, name: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(name, null) == BootSessionStorage.currentBootId()

    fun markUserChange(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(name, BootSessionStorage.currentBootId()).apply()
    }

    fun pending(context: Context): Boolean = BootSessionStorage.hasClaimedCurrentBoot(context) &&
        (!done(context, "fan") || !done(context, "pump"))

    /** False means restoration was deferred or failed, so a bounded retry is appropriate. */
    fun restore(context: Context): Boolean {
        val fan = ManualFanStorage.read(context)
        val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val pump = savedPumpStateStorage(context)
        if (fan == null || isAutoFanEnabledStorage(context)) markUserChange(context, "fan")
        if (!prefs.contains(AppPrefs.PUMP_ENABLED) || pump.autoEnabled) markUserChange(context, "pump")
        if (!pending(context)) return true
        if (!HardwareScreenPolicy.isScreenInteractive(context) || GameModeService.hasSelectedForegroundGame() || CallLightingState.isActive(context)) {
            BootDiagnostics.record(context, "Manual cooling boot restore deferred: screen or active owner")
            return false
        }
        if (!done(context, "fan")) {
            val latest = ManualFanStorage.read(context)
            if (latest == null || isAutoFanEnabledStorage(context)) markUserChange(context, "fan")
            else if (HardwareController.setFanLevel(latest, force = true)) {
                val live = HardwareTelemetry.readForControl()
                if (live != null && CoolingStateMatch.fan(live.fanEnabled, live.fanLevel, latest)) {
                    markUserChange(context, "fan")
                    BootDiagnostics.record(context, "Manual fan boot restore verified level=$latest")
                } else BootDiagnostics.record(context, "Manual fan boot restore unverified")
            } else BootDiagnostics.record(context, "Manual fan boot restore write failed")
        }
        if (!done(context, "pump")) {
            val latest = savedPumpStateStorage(context)
            if (latest.autoEnabled) markUserChange(context, "pump")
            else {
                val profile = if (latest.enabled) latest.profile else "off"
                if (HardwareController.setPumpProfile(profile, force = true)) {
                    val live = HardwareTelemetry.readForControl()
                    if (live != null && CoolingStateMatch.pump(live.pumpEnabled, live.pumpFreq, live.pumpSpeed, profile)) {
                        markUserChange(context, "pump")
                        BootDiagnostics.record(context, "Manual pump boot restore verified profile=$profile")
                    } else BootDiagnostics.record(context, "Manual pump boot restore unverified")
                } else BootDiagnostics.record(context, "Manual pump boot restore write failed")
            }
        }
        return !pending(context)
    }
}
