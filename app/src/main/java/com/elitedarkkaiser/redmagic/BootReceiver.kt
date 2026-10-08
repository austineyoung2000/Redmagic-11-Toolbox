package com.elitedarkkaiser.redmagic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

open class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (DeviceCompatibility.isSupportedDevice()) {
                GameplayRuntimeService.ensureRunning(context)
            }
            return
        }

        val event = when (intent?.action) {
            ROOT_BOOT_ACTION -> BootEvent.ROOT_STARTUP
            Intent.ACTION_BOOT_COMPLETED ->
                BootEvent.BOOT_COMPLETED
            Intent.ACTION_USER_UNLOCKED ->
                BootEvent.USER_UNLOCKED
            else -> return
        }

        if (!DeviceCompatibility.isSupportedDevice()) return

        val appContext = context.applicationContext
        BootDiagnostics.record(appContext, "Received $event")
        BootDiagnostics.request(appContext, "Gameplay runtime") {
            GameplayRuntimeService.ensureRunning(appContext)
        }
        val triggersAutoStart =
            readTriggerPrefsSnapshot(appContext).triggersAutoStart
        val hasUnlockAutomation =
            AutomationRulesStorage.profileName(
                appContext,
                AutomationRuleEvent.DEVICE_UNLOCKED
            ) != null
        val decision = BootEventPolicy.decide(
            event = event,
            coreServicesAlreadyStarted =
                !BootSessionStorage.claimCoreServiceStartup(appContext),
            triggersAutoStart = triggersAutoStart,
            hasUnlockAutomation = hasUnlockAutomation
        )

        BootDiagnostics.record(appContext, "Decision: core=${decision.startCoreServices}, triggers=${decision.startTriggers}, unlockAutomation=${decision.runUnlockAutomation}")
        if (decision.resetManualTriggerPause) {
            setTriggersDisabledUntilRestartStorage(
                appContext,
                false
            )
        }

        if (decision.startCoreServices) {
            startCoreServices(appContext)
        }

        if (!decision.needsAsyncWork) return

        val pendingResult = goAsync()
        Thread(
            {
                android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_BACKGROUND
                )

                try {
                    if (decision.runUnlockAutomation) {
                        AutomationRuleExecutor.applyNow(
                            appContext,
                            AutomationRuleEvent.DEVICE_UNLOCKED
                        )
                    }

                    if (decision.startTriggers) {
                        BootDiagnostics.request(appContext, "Trigger auto-start") {
                            val started = HardwareServiceActions.startTriggersIfAutoStartEnabled(appContext)
                            BootDiagnostics.record(appContext, "Trigger auto-start result: $started")
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            },
            "RedMagicBootStartup"
        ).start()
    }

    companion object {
        const val ROOT_BOOT_ACTION = "com.elitedarkkaiser.redmagic.ROOT_BOOT_STARTUP"
    }

    private fun startCoreServices(context: Context) {
        if (isAutoFanEnabledStorage(context)) {
            BootDiagnostics.request(context, "Auto fan") { HardwareServiceActions.startAutoFan(context) }
        }
        if (savedPumpStateStorage(context).autoEnabled) {
            BootDiagnostics.request(context, "Auto pump") { HardwareServiceActions.startAutoPump(context) }
        }
        BootDiagnostics.request(context, "Charging mode") { HardwareServiceActions.startChargingMode(context) }

        if (CallLightingState.isEnabled(context)) {
            BootDiagnostics.request(context, "Call lighting") { HardwareServiceActions.startCallLighting(context) }
        }
        if (RgbStudioStorage.isEnabled(context)) {
            BootDiagnostics.request(context, "RGB Studio") { HardwareServiceActions.startRgbCycle(context) }
        }
        if (SliderDualAppStorage.read(context).enabled) {
            BootDiagnostics.request(context, "Slider") { HardwareServiceActions.startSliderDualApp(context) }
        }
    }
}
