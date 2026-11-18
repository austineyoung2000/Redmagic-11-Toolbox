package com.elitedarkkaiser.redmagic

import android.app.ActivityManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Bounded post-boot reconciliation; always re-reads current user choices. */
class BootRecoveryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        if (!DeviceCompatibility.isSupportedDevice() || !BootSessionStorage.hasClaimedCurrentBoot(ctx)) return Result.success()
        @Suppress("DEPRECATION")
        fun running(type: Class<*>): Boolean {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return am.getRunningServices(100).any { it.service.className == type.name && it.started }
        }
        if (runAttemptCount == 0) RootAvailability.check(ctx)
        NotificationLightingService.ensureConnected(ctx)
        var missing = !BootCoolingRestore.restore(ctx)
        fun ensure(name: String, type: Class<*>, enabled: Boolean, start: () -> Unit) {
            if (!enabled || running(type)) return
            missing = true
            BootDiagnostics.request(ctx, "Boot recovery $name", start)
        }
        ensure("LED guard", FanLedService::class.java, true) { HardwareServiceActions.startFanLed(ctx) }
        ensure("charging", ChargingModeService::class.java, true) { HardwareServiceActions.startChargingMode(ctx) }
        ensure("auto fan", AutoFanService::class.java, isAutoFanEnabledStorage(ctx)) { HardwareServiceActions.startAutoFan(ctx) }
        ensure("auto pump", AutoPumpService::class.java, savedPumpStateStorage(ctx).autoEnabled) { HardwareServiceActions.startAutoPump(ctx) }
        ensure("slider", SliderDualAppService::class.java, SliderDualAppStorage.read(ctx).enabled) { HardwareServiceActions.startSliderDualApp(ctx) }
        ensure("RGB Studio", RgbCycleService::class.java, RgbStudioStorage.isEnabled(ctx)) { HardwareServiceActions.startRgbCycle(ctx) }
        ensure("call lighting", CallLightingService::class.java, CallLightingState.isEnabled(ctx)) { HardwareServiceActions.startCallLighting(ctx) }
        if (GameplayRuntimeService.isAccessibilityConfigured(ctx)) {
            ensure("gameplay", GameplayRuntimeService::class.java, true) { GameplayRuntimeService.ensureRunning(ctx) }
        }
        val triggers = readTriggerPrefsSnapshot(ctx).triggersAutoStart && !triggersDisabledUntilRestartStorage(ctx)
        ensure("triggers", TriggerRootService::class.java, triggers) {
            val started = HardwareServiceActions.startTriggersIfAutoStartEnabled(ctx)
            BootDiagnostics.record(ctx, "Boot recovery trigger enable result: $started")
        }
        if (missing && runAttemptCount < 4) return Result.retry()
        if (missing) BootDiagnostics.record(ctx, "Boot recovery exhausted attempts; inspect services and permissions")
        else BootDiagnostics.record(ctx, "Boot recovery: configured services observed")
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val work = OneTimeWorkRequestBuilder<BootRecoveryWorker>()
                .setInitialDelay(3, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "redmagic_boot_recovery", ExistingWorkPolicy.KEEP, work
            )
        }
    }
}
