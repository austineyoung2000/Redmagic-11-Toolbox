package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.os.Build

data class DeviceCapabilityReport(
    val model: String,
    val marketName: String,
    val fingerprint: String,
    val isKnownRedmagic11Pro: Boolean,
    val fanAvailable: Boolean,
    val fanRpmAvailable: Boolean,
    val pumpAvailable: Boolean,
    val ledAvailable: Boolean,
    val triggersAvailable: Boolean,
    val sliderAvailable: Boolean,
    val stockFirmware: Boolean,
    val stockGameSuiteAvailable: Boolean,
    val nativeTgkAvailable: Boolean,
    val chargeSeparationAvailable: Boolean,
    val refreshRateAvailable: Boolean,
    val touchTuningAvailable: Boolean,
    val performanceModesAvailable: Boolean,
    val gyroSensitivityAvailable: Boolean,
    val superResolutionAvailable: Boolean,
    val dtsEqualizerAvailable: Boolean,
    val stockScreenRecorderAvailable: Boolean,
    val fpsMonitorAvailable: Boolean,
    val summary: String
)

object DeviceCapabilityScanner {
    private fun output(command: String): String {
        return RootShell.execForOutput(command)?.trim().orEmpty()
    }

    private fun exists(path: String): Boolean {
        val safe = path.replace("'", "'\\''")
        return output("[ -e '$safe' ] && echo yes || echo no") == "yes"
    }

    private fun prop(name: String): String {
        return output("getprop $name")
    }

    private fun packageInstalled(packageName: String): Boolean {
        val safe = packageName.replace("'", "'\\''")
        return output("pm path '$safe'").startsWith("package:")
    }

    private fun handlesIntent(
        context: Context,
        action: String
    ): Boolean {
        val intent = Intent(action)
        val packageManager = context.packageManager

        return packageManager.queryIntentActivities(
            intent,
            0
        ).isNotEmpty() ||
            packageManager.queryIntentServices(
                intent,
                0
            ).isNotEmpty() ||
            packageManager.queryBroadcastReceivers(
                intent,
                0
            ).isNotEmpty()
    }

    fun scan(context: Context): DeviceCapabilityReport {
        val identity = DeviceCompatibility.identity()
        val model = identity.detectedModel
        val marketName = identity.marketName
        val fingerprint = Build.FINGERPRINT.orEmpty().ifBlank { prop("ro.build.fingerprint") }

        val fanAvailable =
            exists(DeviceCompatibility.Paths.FAN_ENABLE) &&
            exists(DeviceCompatibility.Paths.FAN_LEVEL)

        val fanRpmAvailable =
            exists(DeviceCompatibility.Paths.FAN_RPM)

        val pumpAvailable =
            exists(DeviceCompatibility.Paths.PUMP_ENABLE) &&
            exists(DeviceCompatibility.Paths.PUMP_FREQ) &&
            exists(DeviceCompatibility.Paths.PUMP_SPEED)

        val ledAvailable =
            exists(DeviceCompatibility.Paths.LED_EFFECT) &&
            exists(DeviceCompatibility.Paths.LED_CFG)

        val triggersAvailable =
            exists(DeviceCompatibility.Paths.SAR0_MODE) &&
            exists(DeviceCompatibility.Paths.SAR1_MODE)

        /*
         * NX809J exposes Magic Key behavior primarily through
         * system settings, not a consistently readable node.
         * The physical slider is guaranteed by the model gate;
         * optional vendor signals only strengthen that result.
         */
        val sliderAvailable =
            identity.supported ||
                exists("/proc/driver/slider") ||
                prop("persist.sys.nubia.slider")
                    .isNotBlank()

        val stockFirmware =
            DeviceCompatibility.isStockRedmagicFirmware()
        val gameSpaceInstalled = packageInstalled(
            "cn.nubia.gamelauncher"
        )
        val gameAssistInstalled = packageInstalled(
            "cn.nubia.gameassist"
        )
        val stockGameSuiteAvailable =
            stockFirmware &&
                gameSpaceInstalled &&
                gameAssistInstalled
        val nativeTgkAvailable =
            identity.supported &&
                NativeTgkBridge.readState(context).success
        val chargeSeparationAvailable =
            ChargeSeparationController
                .probe(context)
                .compatible
        val gyroSensitivityAvailable =
            exists(DeviceCompatibility.Paths.GYRO_ENABLE) &&
                exists(DeviceCompatibility.Paths.GYRO_X) &&
                exists(DeviceCompatibility.Paths.GYRO_Y)
        val refreshRateAvailable = stockGameSuiteAvailable
        val touchTuningAvailable = stockGameSuiteAvailable
        val performanceModesAvailable = stockGameSuiteAvailable
        val superResolutionAvailable = stockGameSuiteAvailable
        val fpsMonitorAvailable = stockGameSuiteAvailable
        val dtsEqualizerAvailable =
            stockFirmware && handlesIntent(
                context,
                "cn.zte.intent.action.EQUALIZERSERVICE"
            )
        val stockScreenRecorderAvailable =
            stockFirmware && handlesIntent(
                context,
                "cn.nubia.action.supersnap.screenrecord"
            )

        val summary = buildString {
            append("Model: ").append(model.ifBlank { "unknown" })
            if (marketName.isNotBlank()) append(" / ").append(marketName)
            append("\nCompatibility: ").append(
                if (identity.supported) {
                    "NX809J confirmed"
                } else {
                    "unsupported device"
                }
            )
            append("\nFan: ").append(if (fanAvailable) "available" else "missing")
            append("\nFan RPM: ").append(
                if (fanRpmAvailable) "available" else "missing"
            )
            append("\nPump: ").append(if (pumpAvailable) "available" else "missing")
            append("\nLED: ").append(if (ledAvailable) "available" else "missing")
            append("\nTriggers: ").append(if (triggersAvailable) "available" else "missing")
            append("\nSlider: ").append(if (sliderAvailable) "available" else "unknown/missing")
            append("\nStock REDMAGIC firmware: ").append(
                if (stockFirmware) "confirmed" else "not confirmed"
            )
            append("\nNative TGK: ").append(
                if (nativeTgkAvailable) "available" else "unavailable"
            )
            append("\nCharge separation: ").append(
                if (chargeSeparationAvailable) "available" else "unavailable"
            )
            append("\nRefresh rate: ").append(
                if (refreshRateAvailable) "available" else "unavailable"
            )
            append("\nTouch tuning: ").append(
                if (touchTuningAvailable) "available" else "unavailable"
            )
            append("\nPerformance modes: ").append(
                if (performanceModesAvailable) "available" else "unavailable"
            )
            append("\nGyroscope sensitivity: ").append(
                if (gyroSensitivityAvailable) "available" else "unavailable"
            )
            append("\nSuper resolution: ").append(
                if (superResolutionAvailable) "available" else "unavailable"
            )
            append("\nDTS equalizer: ").append(
                if (dtsEqualizerAvailable) "available" else "unavailable"
            )
            append("\nStock screen recorder: ").append(
                if (stockScreenRecorderAvailable) "available" else "unavailable"
            )
            append("\nPerformance monitor: ").append(
                if (fpsMonitorAvailable) "available" else "unavailable"
            )
        }

        return DeviceCapabilityReport(
            model = model,
            marketName = marketName,
            fingerprint = fingerprint,
            isKnownRedmagic11Pro = identity.supported,
            fanAvailable = fanAvailable,
            fanRpmAvailable = fanRpmAvailable,
            pumpAvailable = pumpAvailable,
            ledAvailable = ledAvailable,
            triggersAvailable = triggersAvailable,
            sliderAvailable = sliderAvailable,
            stockFirmware = stockFirmware,
            stockGameSuiteAvailable =
                stockGameSuiteAvailable,
            nativeTgkAvailable = nativeTgkAvailable,
            chargeSeparationAvailable =
                chargeSeparationAvailable,
            refreshRateAvailable = refreshRateAvailable,
            touchTuningAvailable = touchTuningAvailable,
            performanceModesAvailable =
                performanceModesAvailable,
            gyroSensitivityAvailable =
                gyroSensitivityAvailable,
            superResolutionAvailable =
                superResolutionAvailable,
            dtsEqualizerAvailable = dtsEqualizerAvailable,
            stockScreenRecorderAvailable =
                stockScreenRecorderAvailable,
            fpsMonitorAvailable = fpsMonitorAvailable,
            summary = summary
        )
    }
}
