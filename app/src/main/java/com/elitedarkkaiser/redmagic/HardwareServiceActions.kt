package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object HardwareServiceActions {
    fun startAutoFan(context: Context) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        startForegroundCapableService(context, Intent(context, AutoFanService::class.java))
    }

    fun stopAutoFan(context: Context) {
        context.stopService(Intent(context, AutoFanService::class.java))
    }

    fun startFanLed(context: Context) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        startForegroundCapableService(context, Intent(context, FanLedService::class.java))
    }

    fun ensureLightingServices(context: Context) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        // The screen/power guard must exist even when every saved zone is off.
        // Starting services does not grant their profiles lighting ownership.
        BootDiagnostics.request(context, "LED screen/power guard") { startFanLed(context) }
        BootDiagnostics.request(context, "Charging mode") { startChargingMode(context) }
        NotificationLightingService.ensureConnected(context)
        if (CallLightingState.isEnabled(context)) {
            BootDiagnostics.request(context, "Call lighting") { startCallLighting(context) }
        }
        if (RgbStudioStorage.isEnabled(context)) {
            BootDiagnostics.request(context, "RGB Studio") { startRgbCycle(context) }
        }
    }

    fun startRgbCycle(context: Context) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        // Normal writes are ownership-gated. Keep its screen/power receiver
        // alive while Studio owns lighting instead of removing the guard.
        startFanLed(context)
        startForegroundCapableService(
            context,
            Intent(context, RgbCycleService::class.java)
        )
    }

    fun stopRgbCycle(context: Context, restoreNormalLeds: Boolean = true) {
        context.stopService(Intent(context, RgbCycleService::class.java))
        if (restoreNormalLeds) {
            startFanLed(context)
        }
    }

    fun startAutoPump(context: Context) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        startForegroundCapableService(context, Intent(context, AutoPumpService::class.java))
    }

    fun stopAutoPump(context: Context) {
        context.stopService(Intent(context, AutoPumpService::class.java))
    }

    fun startTriggers(context: Context): Boolean {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return false
        }

        if (triggersDisabledUntilRestartStorage(context)) {
            return false
        }

        context.startService(
            Intent(context, TriggerRootService::class.java)
        )
        return true
    }

    fun enableTriggersManually(context: Context): Boolean {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return false
        }

        setTriggersDisabledUntilRestartStorage(
            context,
            false
        )

        val enabled = HardwareController.enableTriggers()
        if (enabled) {
            startTriggers(context)
        }
        return enabled
    }

    fun startTriggersIfAutoStartEnabled(
        context: Context
    ): Boolean {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return false
        }

        if (!readTriggerPrefsSnapshot(context).triggersAutoStart) {
            return false
        }

        if (triggersDisabledUntilRestartStorage(context)) {
            return false
        }

        val enabled = HardwareController.enableTriggers()
        if (enabled) {
            startTriggers(context)
        }
        return enabled
    }

    fun stopTriggers(context: Context) {
        context.stopService(
            Intent(context, TriggerRootService::class.java)
        )
        HardwareController.disableTriggers()
    }

    fun disableTriggersUntilRestart(
        context: Context
    ): Boolean {
        setTriggersDisabledUntilRestartStorage(
            context,
            true
        )

        context.stopService(
            Intent(context, TriggerRootService::class.java)
        )

        return HardwareController.disableTriggers()
    }

    fun startChargingMode(context: Context) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        context.startService(Intent(context, ChargingModeService::class.java))
    }

    fun startCallLighting(context: Context) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        context.startService(Intent(context, CallLightingService::class.java))
    }

    fun stopCallLighting(context: Context) {
        context.stopService(Intent(context, CallLightingService::class.java))
    }

    fun startSliderDualApp(context: Context) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        startForegroundCapableService(
            context,
            Intent(context, SliderDualAppService::class.java)
        )
    }

    fun stopSliderDualApp(context: Context) {
        context.stopService(
            Intent(context, SliderDualAppService::class.java)
        )
    }

    fun enqueueFanLedRestore(context: Context, delaySeconds: Long = 2) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        val request = OneTimeWorkRequestBuilder<FanLedRestoreWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .addTag("fan_led_manual_restore")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "fan_led_manual_restore",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun startForegroundCapableService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
