package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log

data class ChargeSeparationProbe(
    val compatible: Boolean,
    val enabled: Boolean?,
    val message: String
)

data class ChargeSeparationResult(
    val success: Boolean,
    val enabled: Boolean?,
    val backend: String?,
    val message: String
)

/**
 * Controls the stock REDMAGIC charge-separation implementation.
 *
 * GameAssist uses Settings.Global charge_separation_switch for the actual
 * state. The vendor content provider is retained as a compatibility signal
 * because it owns the stock warning/dialog side of this feature.
 */
object ChargeSeparationController {
    private const val TAG = "RedmagicChargeSeparation"
    private const val SETTING_KEY = "charge_separation_switch"
    private const val PROVIDER_AUTHORITY =
        "cn.zte.chargeseparation.chargeseparationcontentprovider"
    private const val MINIMUM_ENABLE_PERCENT = 20

    @Synchronized
    fun probe(context: Context): ChargeSeparationProbe {
        if (!DeviceCompatibility.isStockRedmagicFirmware()) {
            return ChargeSeparationProbe(
                compatible = false,
                enabled = null,
                message = "Stock REDMAGIC Android 16 firmware is required"
            )
        }

        if (!providerAvailable(context)) {
            return ChargeSeparationProbe(
                compatible = false,
                enabled = null,
                message = "The stock charge-separation provider is unavailable"
            )
        }

        val enabled = readSetting(context)
            ?: return ChargeSeparationProbe(
                compatible = false,
                enabled = null,
                message = "The charge-separation system setting is unreadable"
            )

        return ChargeSeparationProbe(
            compatible = true,
            enabled = enabled,
            message = "Stock charge-separation interface available"
        )
    }

    @Synchronized
    fun read(context: Context): ChargeSeparationResult {
        val probe = probe(context)
        return ChargeSeparationResult(
            success = probe.compatible && probe.enabled != null,
            enabled = probe.enabled,
            backend = if (probe.compatible) {
                "Android Settings API"
            } else {
                null
            },
            message = probe.message
        )
    }

    @Synchronized
    fun setEnabled(
        context: Context,
        enabled: Boolean
    ): ChargeSeparationResult {
        val probe = probe(context)
        if (!probe.compatible || probe.enabled == null) {
            return ChargeSeparationResult(
                success = false,
                enabled = probe.enabled,
                backend = null,
                message = probe.message
            )
        }

        if (enabled) {
            enableBlockReason(context)?.let { reason ->
                return ChargeSeparationResult(
                    success = false,
                    enabled = probe.enabled,
                    backend = null,
                    message = reason
                )
            }
        }

        if (probe.enabled == enabled) {
            return ChargeSeparationResult(
                success = true,
                enabled = enabled,
                backend = "No write required",
                message = if (enabled) {
                    "Charge separation is already enabled"
                } else {
                    "Charge separation is already disabled"
                }
            )
        }

        val appWriteSucceeded = runCatching {
            Settings.Global.putInt(
                context.contentResolver,
                SETTING_KEY,
                if (enabled) 1 else 0
            )
        }.getOrDefault(false)

        if (appWriteSucceeded && readSetting(context) == enabled) {
            return success(enabled, "Android Settings API")
        }

        /*
         * Ordinary apps normally cannot write Settings.Global. Use one
         * constant root command only after the direct vendor setting write
         * has failed. RootShell reuses its serialized persistent session.
         */
        val settingValue = if (enabled) 1 else 0
        val rootSucceeded = RootShell.exec(
            "/system/bin/settings put global " +
                "$SETTING_KEY $settingValue"
        )

        val verified = readSetting(context)
        if (rootSucceeded && verified == enabled) {
            return success(enabled, "Root settings fallback")
        }

        Log.e(
            TAG,
            "Charge-separation write failed; requested=$enabled " +
                "verified=$verified"
        )
        return ChargeSeparationResult(
            success = false,
            enabled = verified,
            backend = if (rootSucceeded) {
                "Root settings fallback"
            } else {
                null
            },
            message = "Charge-separation state could not be changed and verified"
        )
    }

    private fun success(
        enabled: Boolean,
        backend: String
    ): ChargeSeparationResult {
        Log.i(
            TAG,
            "Charge separation ${if (enabled) "enabled" else "disabled"} " +
                "through $backend"
        )
        return ChargeSeparationResult(
            success = true,
            enabled = enabled,
            backend = backend,
            message = if (enabled) {
                "Charge separation enabled"
            } else {
                "Charge separation disabled"
            }
        )
    }

    private fun providerAvailable(context: Context): Boolean {
        return runCatching {
            context.packageManager.resolveContentProvider(
                PROVIDER_AUTHORITY,
                0
            ) != null
        }.getOrDefault(false)
    }

    private fun readSetting(context: Context): Boolean? {
        return runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                SETTING_KEY,
                0
            ) == 1
        }.getOrNull()
    }

    private fun enableBlockReason(context: Context): String? {
        val battery = runCatching {
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
        }.getOrNull()
            ?: return "Battery state is unavailable"

        val plugged = battery.getIntExtra(
            BatteryManager.EXTRA_PLUGGED,
            0
        ) != 0
        if (!plugged) {
            return "Connect a charger before enabling charge separation"
        }

        val level = battery.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1
        )
        val scale = battery.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            -1
        )
        if (level < 0 || scale <= 0) {
            return "Battery level is unavailable"
        }

        val percent = level * 100 / scale
        return if (percent < MINIMUM_ENABLE_PERCENT) {
            "Battery must be at least $MINIMUM_ENABLE_PERCENT% to enable charge separation"
        } else {
            null
        }
    }
}
