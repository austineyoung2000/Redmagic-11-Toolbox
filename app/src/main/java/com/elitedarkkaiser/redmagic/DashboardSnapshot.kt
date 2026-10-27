package com.elitedarkkaiser.redmagic

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

object DashboardSnapshot {

    fun invalidateHardwareCache() {
        HardwareTelemetry.invalidateHardwareCache()
    }

    fun readFanEnabled(): String {
        return HardwareTelemetry.read().fanEnabled?.let {
            if (it) "1" else "0"
        } ?: "?"
    }

    fun readFanRpm(): String {
        return HardwareTelemetry.read()
            .fanRpm
            ?.toString()
            ?: "?"
    }

    fun readFanLevel(): String {
        return HardwareTelemetry.read()
            .fanLevel
            ?.toString()
            ?: "?"
    }

    fun readPumpEnabled(): String {
        return HardwareTelemetry.read().pumpEnabled ?: "?"
    }

    fun readPumpFreq(): String {
        return HardwareTelemetry.read().pumpFreq ?: "?"
    }

    fun readPumpSpeed(): String {
        return HardwareTelemetry.read().pumpSpeed ?: "?"
    }

    fun readCpuTempC(): String {
        val temperature =
            HardwareTelemetry.readTemperatureC()
                ?: return "?"

        return String.format("%.1f", temperature)
    }

    fun readCpuTempF(): String {
        val temperature =
            HardwareTelemetry.readTemperatureC()
                ?: return "?"

        return String.format(
            "%.1f",
            (temperature * 9f / 5f) + 32f
        )
    }

    fun currentForegroundPackage(
        context: Context
    ): String? {
        val manager =
            context.getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val now = System.currentTimeMillis()
        val stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000L,
            now
        )

        return stats.maxByOrNull {
            it.lastTimeUsed
        }?.packageName
    }

    fun buildSummary(
        context: Context
    ): String {
        val hardware = HardwareTelemetry.read()
        val rooted = RootShell.hasRoot()

        return buildSummary(
            context = context,
            hardware = hardware,
            rooted = rooted
        )
    }

    fun buildSummary(
        context: Context,
        hardware: HardwareTelemetrySnapshot,
        rooted: Boolean
    ): String {
        val fanEnabled = hardware.fanEnabled?.let {
            if (it) "1" else "0"
        } ?: "?"

        val fanLevel =
            hardware.fanLevel?.toString() ?: "?"

        val fanRpm =
            hardware.fanRpm?.toString() ?: "?"

        val pumpEnabled =
            hardware.pumpEnabled ?: "?"

        val pumpFreq =
            hardware.pumpFreq ?: "?"

        val pumpSpeed =
            hardware.pumpSpeed ?: "?"

        val temperatureF =
            hardware.temperatureF?.let {
                String.format("%.1f", it)
            } ?: "?"

        val foregroundPackage =
            currentForegroundPackage(context)
                ?: "Unavailable"

        return buildString {
            append(
                "Model: ${Build.MODEL ?: "Unknown"}\n"
            )
            append(
                "Root: ${
                    if (rooted) "Granted" else "Missing"
                }\n"
            )
            append("CPU Temp: $temperatureF°F\n")
            append(
                "Fan: $fanEnabled • Level $fanLevel • " +
                    "$fanRpm RPM\n"
            )
            append(
                "Pump: $pumpEnabled • Freq $pumpFreq • " +
                    "Speed $pumpSpeed\n"
            )
            append(
                "Foreground app: $foregroundPackage"
            )
        }
    }
}
