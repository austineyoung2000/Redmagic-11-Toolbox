package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

data class RefreshRateProbe(
    val compatible: Boolean,
    val backend: String?,
    val supportedRates: List<Int>,
    val message: String
)

data class RefreshRateApplyResult(
    val success: Boolean,
    val backend: String?,
    val message: String
)

object RefreshRateBridge {
    private const val TAG = "RedmagicRefreshRate"
    private const val SERVICE_NAME = "ZteScreenRefreshRate"
    private const val CALLER = "GameAssist"
    private const val SET_TRANSACTION = 6
    private const val TIMEOUT_SECONDS = 5L

    fun probe(context: Context): RefreshRateProbe {
        if (!DeviceCompatibility.isStockRedmagicFirmware()) {
            return RefreshRateProbe(
                false,
                null,
                emptyList(),
                "Stock REDMAGIC firmware is required"
            )
        }

        val rates = RefreshRateStorage.supportedRates(context)
        if (rates.isEmpty()) {
            return RefreshRateProbe(
                false,
                null,
                rates,
                "No supported display refresh rates were reported"
            )
        }

        reflectionManager()?.let {
            return RefreshRateProbe(
                true,
                "vendor manager",
                rates,
                "Vendor refresh-rate manager is available"
            )
        }

        if (serviceAvailable()) {
            return RefreshRateProbe(
                true,
                "ordinary Binder service",
                rates,
                "Vendor refresh-rate Binder service is available"
            )
        }

        return RefreshRateProbe(
            false,
            null,
            rates,
            "The ZTE refresh-rate service is unavailable"
        )
    }

    @Synchronized
    fun apply(
        context: Context,
        packageName: String,
        refreshRateHz: Int
    ): RefreshRateApplyResult {
        val probe = probe(context)
        if (!probe.compatible) {
            return RefreshRateApplyResult(
                false,
                null,
                probe.message
            )
        }
        if (!PACKAGE_PATTERN.matches(packageName)) {
            return RefreshRateApplyResult(
                false,
                null,
                "Invalid target package"
            )
        }
        if (
            refreshRateHz != 0 &&
            refreshRateHz !in probe.supportedRates
        ) {
            return RefreshRateApplyResult(
                false,
                null,
                "$refreshRateHz Hz is not supported by this display"
            )
        }

        val failures = mutableListOf<String>()
        reflectionManager()?.let { manager ->
            try {
                manager.javaClass.getMethod(
                    "setRefreshRateByGameAssist",
                    String::class.java,
                    String::class.java,
                    Integer.TYPE
                ).invoke(
                    manager,
                    CALLER,
                    packageName,
                    refreshRateHz
                )
                return RefreshRateApplyResult(
                    true,
                    "vendor manager",
                    resultMessage(refreshRateHz)
                )
            } catch (error: Throwable) {
                failures += "vendor manager: ${errorSummary(error)}"
            }
        }

        try {
            val output = runService(
                "call",
                SERVICE_NAME,
                SET_TRANSACTION.toString(),
                "s16",
                CALLER,
                "s16",
                packageName,
                "i32",
                refreshRateHz.toString()
            )
            if (
                output.contains("Exception", ignoreCase = true) ||
                output.contains("Permission Denial", ignoreCase = true)
            ) {
                error(output)
            }
            return RefreshRateApplyResult(
                true,
                "ordinary Binder service",
                resultMessage(refreshRateHz)
            )
        } catch (error: Throwable) {
            failures += "Binder service: ${errorSummary(error)}"
        }

        val message = failures.joinToString(" | ")
        Log.w(TAG, "Unable to apply refresh rate: $message")
        return RefreshRateApplyResult(
            false,
            null,
            "Refresh-rate request failed: $message"
        )
    }

    private fun reflectionManager(): Any? {
        return runCatching {
            val type = Class.forName(
                "com.zte.performance.refreshrate." +
                    "ScreenRefreshRateManager"
            )
            type.getMethod("getInstance").invoke(null)
        }.getOrNull()
    }

    private fun serviceAvailable(): Boolean {
        return runCatching {
            runService("check", SERVICE_NAME)
                .contains("found", ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun runService(vararg arguments: String): String {
        val command = listOf("/system/bin/service") + arguments
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("service command timed out")
        }
        val output = process.inputStream.bufferedReader().use {
            it.readText()
        }.trim()
        if (process.exitValue() != 0) {
            error(output.ifBlank { "service command failed" })
        }
        return output
    }

    private fun resultMessage(rate: Int): String {
        return if (rate == 0) {
            "Following the system refresh rate"
        } else {
            "Requested $rate Hz for the foreground app"
        }
    }

    private fun errorSummary(error: Throwable): String {
        return error.cause?.message
            ?: error.message
            ?: error.javaClass.simpleName
    }

    private val PACKAGE_PATTERN = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )
}
