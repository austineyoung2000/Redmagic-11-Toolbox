package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

private const val DEVICE_SCAN_MODEL = "device_scan_model"
private const val DEVICE_SCAN_SUMMARY = "device_scan_summary"
private const val DEVICE_SCAN_SUPPORTED_MODEL = "device_scan_supported_model"
private const val DEVICE_SCAN_FAN_AVAILABLE = "device_scan_fan_available"
private const val DEVICE_SCAN_FAN_RPM_AVAILABLE =
    "device_scan_fan_rpm_available"
private const val DEVICE_SCAN_PUMP_AVAILABLE = "device_scan_pump_available"
private const val DEVICE_SCAN_LED_AVAILABLE = "device_scan_led_available"
private const val DEVICE_SCAN_TRIGGERS_AVAILABLE = "device_scan_triggers_available"
private const val DEVICE_SCAN_SLIDER_AVAILABLE =
    "device_scan_slider_available"
private const val DEVICE_SCAN_LAST_RUN = "device_scan_last_run"
private const val DEVICE_SCAN_FINGERPRINT =
    "device_scan_fingerprint"
private const val DEVICE_SCAN_SCHEMA = "device_scan_schema"
private const val CURRENT_DEVICE_SCAN_SCHEMA = 2
private const val DEVICE_SCAN_STOCK_FIRMWARE =
    "device_scan_stock_firmware"
private const val DEVICE_SCAN_STOCK_GAME_SUITE =
    "device_scan_stock_game_suite"
private const val DEVICE_SCAN_NATIVE_TGK =
    "device_scan_native_tgk"
private const val DEVICE_SCAN_CHARGE_SEPARATION =
    "device_scan_charge_separation"
private const val DEVICE_SCAN_REFRESH_RATE =
    "device_scan_refresh_rate"
private const val DEVICE_SCAN_TOUCH_TUNING =
    "device_scan_touch_tuning"
private const val DEVICE_SCAN_PERFORMANCE_MODES =
    "device_scan_performance_modes"
private const val DEVICE_SCAN_GYRO_SENSITIVITY =
    "device_scan_gyro_sensitivity"
private const val DEVICE_SCAN_SUPER_RESOLUTION =
    "device_scan_super_resolution"
private const val DEVICE_SCAN_DTS_EQUALIZER =
    "device_scan_dts_equalizer"
private const val DEVICE_SCAN_SCREEN_RECORDER =
    "device_scan_screen_recorder"
private const val DEVICE_SCAN_FPS_MONITOR =
    "device_scan_fps_monitor"

fun saveDeviceCapabilityReportStorage(context: Context, report: DeviceCapabilityReport) {
    context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(DEVICE_SCAN_MODEL, report.model)
        .putString(DEVICE_SCAN_SUMMARY, report.summary)
        .putBoolean(DEVICE_SCAN_SUPPORTED_MODEL, report.isKnownRedmagic11Pro)
        .putBoolean(DEVICE_SCAN_FAN_AVAILABLE, report.fanAvailable)
        .putBoolean(
            DEVICE_SCAN_FAN_RPM_AVAILABLE,
            report.fanRpmAvailable
        )
        .putBoolean(DEVICE_SCAN_PUMP_AVAILABLE, report.pumpAvailable)
        .putBoolean(DEVICE_SCAN_LED_AVAILABLE, report.ledAvailable)
        .putBoolean(DEVICE_SCAN_TRIGGERS_AVAILABLE, report.triggersAvailable)
        .putBoolean(
            DEVICE_SCAN_SLIDER_AVAILABLE,
            report.sliderAvailable
        )
        .putLong(DEVICE_SCAN_LAST_RUN, System.currentTimeMillis())
        .putString(
            DEVICE_SCAN_FINGERPRINT,
            report.fingerprint
        )
        .putInt(
            DEVICE_SCAN_SCHEMA,
            CURRENT_DEVICE_SCAN_SCHEMA
        )
        .putBoolean(
            DEVICE_SCAN_STOCK_FIRMWARE,
            report.stockFirmware
        )
        .putBoolean(
            DEVICE_SCAN_STOCK_GAME_SUITE,
            report.stockGameSuiteAvailable
        )
        .putBoolean(
            DEVICE_SCAN_NATIVE_TGK,
            report.nativeTgkAvailable
        )
        .putBoolean(
            DEVICE_SCAN_CHARGE_SEPARATION,
            report.chargeSeparationAvailable
        )
        .putBoolean(
            DEVICE_SCAN_REFRESH_RATE,
            report.refreshRateAvailable
        )
        .putBoolean(
            DEVICE_SCAN_TOUCH_TUNING,
            report.touchTuningAvailable
        )
        .putBoolean(
            DEVICE_SCAN_PERFORMANCE_MODES,
            report.performanceModesAvailable
        )
        .putBoolean(
            DEVICE_SCAN_GYRO_SENSITIVITY,
            report.gyroSensitivityAvailable
        )
        .putBoolean(
            DEVICE_SCAN_SUPER_RESOLUTION,
            report.superResolutionAvailable
        )
        .putBoolean(
            DEVICE_SCAN_DTS_EQUALIZER,
            report.dtsEqualizerAvailable
        )
        .putBoolean(
            DEVICE_SCAN_SCREEN_RECORDER,
            report.stockScreenRecorderAvailable
        )
        .putBoolean(
            DEVICE_SCAN_FPS_MONITOR,
            report.fpsMonitorAvailable
        )
        .apply()
}

data class DeviceCapabilities(
    val scanComplete: Boolean,
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
    val fpsMonitorAvailable: Boolean
) {
    companion object {
        fun unknown(): DeviceCapabilities {
            return DeviceCapabilities(
                scanComplete = false,
                fanAvailable = true,
                fanRpmAvailable = true,
                pumpAvailable = true,
                ledAvailable = true,
                triggersAvailable = true,
                sliderAvailable = true,
                stockFirmware = false,
                stockGameSuiteAvailable = false,
                nativeTgkAvailable = false,
                chargeSeparationAvailable = false,
                refreshRateAvailable = false,
                touchTuningAvailable = false,
                performanceModesAvailable = false,
                gyroSensitivityAvailable = false,
                superResolutionAvailable = false,
                dtsEqualizerAvailable = false,
                stockScreenRecorderAvailable = false,
                fpsMonitorAvailable = false
            )
        }
    }
}

fun DeviceCapabilityReport.toDeviceCapabilities():
    DeviceCapabilities {
    return DeviceCapabilities(
        scanComplete = true,
        fanAvailable = fanAvailable,
        fanRpmAvailable = fanRpmAvailable,
        pumpAvailable = pumpAvailable,
        ledAvailable = ledAvailable,
        triggersAvailable = triggersAvailable,
        sliderAvailable = sliderAvailable,
        stockFirmware = stockFirmware,
        stockGameSuiteAvailable = stockGameSuiteAvailable,
        nativeTgkAvailable = nativeTgkAvailable,
        chargeSeparationAvailable = chargeSeparationAvailable,
        refreshRateAvailable = refreshRateAvailable,
        touchTuningAvailable = touchTuningAvailable,
        performanceModesAvailable = performanceModesAvailable,
        gyroSensitivityAvailable = gyroSensitivityAvailable,
        superResolutionAvailable = superResolutionAvailable,
        dtsEqualizerAvailable = dtsEqualizerAvailable,
        stockScreenRecorderAvailable =
            stockScreenRecorderAvailable,
        fpsMonitorAvailable = fpsMonitorAvailable
    )
}

fun deviceCapabilitiesStorage(
    context: Context
): DeviceCapabilities {
    if (!hasDeviceCapabilityReportStorage(context)) {
        return DeviceCapabilities.unknown()
    }

    val prefs = context.getSharedPreferences(
        AppPrefs.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    return DeviceCapabilities(
        scanComplete = true,
        fanAvailable = prefs.getBoolean(
            DEVICE_SCAN_FAN_AVAILABLE,
            false
        ),
        fanRpmAvailable = prefs.getBoolean(
            DEVICE_SCAN_FAN_RPM_AVAILABLE,
            false
        ),
        pumpAvailable = prefs.getBoolean(
            DEVICE_SCAN_PUMP_AVAILABLE,
            false
        ),
        ledAvailable = prefs.getBoolean(
            DEVICE_SCAN_LED_AVAILABLE,
            false
        ),
        triggersAvailable = prefs.getBoolean(
            DEVICE_SCAN_TRIGGERS_AVAILABLE,
            false
        ),
        sliderAvailable = prefs.getBoolean(
            DEVICE_SCAN_SLIDER_AVAILABLE,
            false
        ),
        stockFirmware = prefs.getBoolean(
            DEVICE_SCAN_STOCK_FIRMWARE,
            false
        ),
        stockGameSuiteAvailable = prefs.getBoolean(
            DEVICE_SCAN_STOCK_GAME_SUITE,
            false
        ),
        nativeTgkAvailable = prefs.getBoolean(
            DEVICE_SCAN_NATIVE_TGK,
            false
        ),
        chargeSeparationAvailable = prefs.getBoolean(
            DEVICE_SCAN_CHARGE_SEPARATION,
            false
        ),
        refreshRateAvailable = prefs.getBoolean(
            DEVICE_SCAN_REFRESH_RATE,
            false
        ),
        touchTuningAvailable = prefs.getBoolean(
            DEVICE_SCAN_TOUCH_TUNING,
            false
        ),
        performanceModesAvailable = prefs.getBoolean(
            DEVICE_SCAN_PERFORMANCE_MODES,
            false
        ),
        gyroSensitivityAvailable = prefs.getBoolean(
            DEVICE_SCAN_GYRO_SENSITIVITY,
            false
        ),
        superResolutionAvailable = prefs.getBoolean(
            DEVICE_SCAN_SUPER_RESOLUTION,
            false
        ),
        dtsEqualizerAvailable = prefs.getBoolean(
            DEVICE_SCAN_DTS_EQUALIZER,
            false
        ),
        stockScreenRecorderAvailable = prefs.getBoolean(
            DEVICE_SCAN_SCREEN_RECORDER,
            false
        ),
        fpsMonitorAvailable = prefs.getBoolean(
            DEVICE_SCAN_FPS_MONITOR,
            false
        )
    )
}

fun deviceScanSummaryStorage(context: Context): String {
    return context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(DEVICE_SCAN_SUMMARY, "Device scan pending…") ?: "Device scan pending…"
}


fun hasDeviceCapabilityReportStorage(context: Context): Boolean {
    val prefs = context.getSharedPreferences(
        AppPrefs.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    return prefs.getInt(DEVICE_SCAN_SCHEMA, 0) ==
        CURRENT_DEVICE_SCAN_SCHEMA &&
        prefs.contains(DEVICE_SCAN_SUMMARY) &&
        prefs.contains(DEVICE_SCAN_FAN_RPM_AVAILABLE) &&
        prefs.contains(DEVICE_SCAN_SLIDER_AVAILABLE) &&
        prefs.getString(
            DEVICE_SCAN_FINGERPRINT,
            null
        ) == android.os.Build.FINGERPRINT
}
