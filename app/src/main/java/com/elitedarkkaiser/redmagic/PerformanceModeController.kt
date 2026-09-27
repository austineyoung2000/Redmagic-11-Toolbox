package com.elitedarkkaiser.redmagic

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log

data class PerformanceModeProbe(
    val compatible: Boolean,
    val message: String
)

data class PerformanceModeResult(
    val success: Boolean,
    val backend: String?,
    val activeMode: RedmagicPerformanceMode?,
    val message: String
)

object PerformanceModeController {
    private const val TAG = "RedmagicPerformance"
    private const val MODE_KEY = "performance_mode_value"
    private const val PACKAGE_KEY = "performance_mode_package"
    private const val CHICKEN_MODE_KEY = "game_chicken_mode_switch"

    fun probe(context: Context): PerformanceModeProbe {
        if (!DeviceCompatibility.isStockRedmagicFirmware()) {
            return PerformanceModeProbe(
                false,
                "Stock REDMAGIC Android 16 firmware is required"
            )
        }

        val readable = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                MODE_KEY,
                0
            )
            Settings.Global.getString(
                context.contentResolver,
                PACKAGE_KEY
            )
        }.isSuccess

        return PerformanceModeProbe(
            compatible = readable,
            message = if (readable) {
                "Stock REDMAGIC performance controller is available"
            } else {
                "Performance-controller settings are unreadable"
            }
        )
    }

    fun readActiveMode(context: Context): RedmagicPerformanceMode? {
        return RedmagicPerformanceMode.fromValue(
            Settings.Global.getInt(
                context.contentResolver,
                MODE_KEY,
                0
            )
        )
    }

    fun readActivePackage(context: Context): String? {
        return Settings.Global.getString(
            context.contentResolver,
            PACKAGE_KEY
        )?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun apply(
        context: Context,
        packageName: String,
        mode: RedmagicPerformanceMode
    ): PerformanceModeResult {
        if (!PACKAGE_PATTERN.matches(packageName)) {
            return PerformanceModeResult(
                false,
                null,
                null,
                "Invalid app package"
            )
        }
        val probe = probe(context)
        if (!probe.compatible) {
            return PerformanceModeResult(
                false,
                null,
                null,
                probe.message
            )
        }

        return writeLiveState(
            context = context,
            packageName = packageName,
            modeValue = mode.value,
            successMessage = "${mode.label} mode active for $packageName"
        )
    }

    @Synchronized
    fun reset(context: Context): PerformanceModeResult {
        if (!DeviceCompatibility.isStockRedmagicFirmware()) {
            return PerformanceModeResult(
                false,
                null,
                null,
                "Stock REDMAGIC Android 16 firmware is required"
            )
        }

        return writeLiveState(
            context = context,
            packageName = "",
            modeValue = 0,
            successMessage = "Performance mode returned to stock default"
        )
    }

    private fun writeLiveState(
        context: Context,
        packageName: String,
        modeValue: Int,
        successMessage: String
    ): PerformanceModeResult {
        val desired = linkedMapOf(
            PACKAGE_KEY to packageName,
            CHICKEN_MODE_KEY to "0",
            MODE_KEY to modeValue.toString()
        )
        val changed = desired.filter { (key, value) ->
            Settings.Global.getString(
                context.contentResolver,
                key
            ).orEmpty() != value
        }

        var backend = "No write required"
        if (changed.isNotEmpty()) {
            val directSucceeded = changed.all { (key, value) ->
                runCatching {
                    Settings.Global.putString(
                        context.contentResolver,
                        key,
                        value
                    )
                }.getOrDefault(false)
            }

            if (directSucceeded) {
                backend = "Android Settings API"
            } else {
                val command = desired.entries.joinToString(" && ") {
                        (key, value) ->
                    "/system/bin/settings put global " +
                        shellQuote(key) + " " + shellQuote(value)
                }
                if (!RootShell.exec(command)) {
                    return PerformanceModeResult(
                        false,
                        null,
                        null,
                        "Unable to update protected performance settings"
                    )
                }
                backend = "Root settings fallback"
            }
        }

        val verified = desired.all { (key, value) ->
            Settings.Global.getString(
                context.contentResolver,
                key
            ).orEmpty() == value
        }
        if (!verified) {
            return PerformanceModeResult(
                false,
                backend,
                null,
                "Performance settings could not be verified"
            )
        }

        val audioSynced = setAudioPerformanceMode(
            context,
            modeValue
        )
        if (!audioSynced) {
            Log.w(
                TAG,
                "Stock performance state applied without AudioManager sync"
            )
        }

        return PerformanceModeResult(
            true,
            if (audioSynced) {
                "$backend + AudioManager"
            } else {
                backend
            },
            RedmagicPerformanceMode.fromValue(modeValue),
            if (audioSynced) {
                successMessage
            } else {
                "$successMessage; AudioManager sync was unavailable"
            }
        )
    }

    private fun setAudioPerformanceMode(
        context: Context,
        modeValue: Int
    ): Boolean {
        val manager = context.getSystemService(
            Context.AUDIO_SERVICE
        ) as? AudioManager ?: return false

        return runCatching {
            manager.javaClass.getMethod(
                "setParameters",
                String::class.java
            ).invoke(
                manager,
                "$MODE_KEY=$modeValue"
            )
        }.onFailure {
            Log.w(TAG, "AudioManager performance sync failed", it)
        }.isSuccess
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private val PACKAGE_PATTERN = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )
}
