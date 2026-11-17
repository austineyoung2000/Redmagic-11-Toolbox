package com.elitedarkkaiser.redmagic

import android.content.Context
import java.util.concurrent.Executors

/** Event-driven root recovery; never runs an exploit or polls in the background. */
internal object RootAvailability {
    private const val PREFS = "root_availability"
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "RedmagicRootRecovery").apply { isDaemon = true }
    }

    fun status(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("boot", null) != BootSessionStorage.currentBootId() || !prefs.contains("verified"))
            return "not observed this boot"
        return if (prefs.getBoolean("verified", false)) "last probe verified" else "last probe unavailable"
    }

    fun check(context: Context, force: Boolean = false): Boolean {
        val verified = RootShell.hasRoot()
        observe(context, verified, force)
        return verified
    }

    fun observe(context: Context, verified: Boolean, force: Boolean = false) {
        val app = context.applicationContext
        if (!DeviceCompatibility.isSupportedDevice()) return
        val shouldRecover = synchronized(this) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val boot = BootSessionStorage.currentBootId()
            val previous = if (prefs.getString("boot", null) == boot && prefs.contains("verified"))
                prefs.getBoolean("verified", false) else null
            prefs.edit().putString("boot", boot).putBoolean("verified", verified).apply()
            RootAvailabilityPolicy.shouldRecover(previous, verified, force)
        }
        if (!shouldRecover) return
        BootDiagnostics.record(app, "Root verified; root-ready recovery requested")
        executor.execute { recover(app) }
    }

    private fun recover(context: Context) {
        // Root may have disappeared while this work was queued.
        if (!RootShell.hasRoot()) {
            observe(context, false)
            BootDiagnostics.record(context, "Root recovery deferred: root no longer verified")
            return
        }
        // Recheck current preferences at execution time; keep existing ownership and safety policies.
        BootDiagnostics.request(context, "Root recovery telemetry") { HardwareTelemetry.invalidateHardwareCache() }
        BootDiagnostics.request(context, "Root recovery gameplay") { GameplayRuntimeService.ensureRunning(context) }
        if (isAutoFanEnabledStorage(context))
            BootDiagnostics.request(context, "Root recovery auto fan") { HardwareServiceActions.startAutoFan(context) }
        if (savedPumpStateStorage(context).autoEnabled)
            BootDiagnostics.request(context, "Root recovery auto pump") { HardwareServiceActions.startAutoPump(context) }
        if (SliderDualAppStorage.read(context).enabled)
            BootDiagnostics.request(context, "Root recovery slider") { HardwareServiceActions.startSliderDualApp(context) }
        BootDiagnostics.request(context, "Root recovery lighting services") { HardwareServiceActions.ensureLightingServices(context) }
        BootDiagnostics.request(context, "Root recovery lighting owner") { ModeTransitionCoordinator.restoreEffectiveOwner(context, "root-available") }
        BootDiagnostics.request(context, "Root recovery triggers") { HardwareServiceActions.recoverTriggersAfterRoot(context) }
        if (BootSessionStorage.hasClaimedCurrentBoot(context)) {
            BootDiagnostics.request(context, "Root recovery manual cooling") { BootCoolingRestore.restore(context) }
            BootDiagnostics.request(context, "Root recovery boot reconciliation") { BootRecoveryWorker.schedule(context) }
        }
    }
}
