package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.BatteryManager

internal object ChargingLedState {
    const val PREFS = "redmagic_hw_controls_prefs"

    const val ENABLED_KEY = "charging_led_enabled"
    const val ACTIVE_KEY = "charging_led_active"

    const val FAN_ENABLED_KEY = "charging_fan_led_enabled"
    const val FAN_EFFECT_KEY = "charging_fan_led_effect"
    const val FAN_COLOR_KEY = "charging_fan_led_color"

    const val LOGO_ENABLED_KEY = "charging_logo_led_enabled"
    const val LOGO_EFFECT_KEY = "charging_logo_led_effect"
    const val LOGO_COLOR_KEY = "charging_logo_led_color"

    const val SHOULDER_ENABLED_KEY = "charging_shoulder_led_enabled"
    const val SHOULDER_EFFECT_KEY = "charging_shoulder_led_effect"
    const val SHOULDER_COLOR_KEY = "charging_shoulder_led_color"


    data class Profile(
        val enabled: Boolean,
        val effect: String,
        val color: Int
    )

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, enabled)
            .apply()
    }

    fun readProfile(
        context: Context,
        enabledKey: String,
        effectKey: String,
        colorKey: String,
        defaultEnabled: Boolean,
        defaultEffect: String,
        defaultColor: Int
    ): Profile {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedEffect =
            prefs.getString(effectKey, defaultEffect) ?: defaultEffect
        val savedColor = prefs.getInt(colorKey, defaultColor)
        val isFanProfile = effectKey == FAN_EFFECT_KEY
        return Profile(
            enabled = prefs.getBoolean(enabledKey, defaultEnabled),
            effect = if (isFanProfile) {
                FanLedPalette.normalizeEffect(savedEffect)
            } else {
                savedEffect
            },
            color = if (isFanProfile) {
                FanLedPalette.normalizeColor(savedEffect, savedColor)
            } else {
                savedColor
            }
        )
    }

    fun saveProfile(
        context: Context,
        enabledKey: String,
        effectKey: String,
        colorKey: String,
        enabled: Boolean,
        effect: String,
        color: Int
    ) {
        val savedColor = if (
            effectKey == FAN_EFFECT_KEY &&
            effect.startsWith("preset:")
        ) {
            -1
        } else {
            color
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(enabledKey, enabled)
            .putString(effectKey, effect)
            .putInt(colorKey, savedColor)
            .apply()
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, false)
    }

    fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ACTIVE_KEY, active)
            .apply()
    }

    fun isActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ACTIVE_KEY, false)
    }

    fun isChargingNow(context: Context): Boolean {
        val bm = context.getSystemService(BatteryManager::class.java)
        val status = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun applyChargingProfile(
        context: Context,
        force: Boolean = false
    ) {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val fanEnabled =
            prefs.getBoolean(FAN_ENABLED_KEY, true)
        val fanEffect =
            prefs.getString(
                FAN_EFFECT_KEY,
                "steady"
            ) ?: "steady"
        val fanColor =
            prefs.getInt(FAN_COLOR_KEY, 5)

        val logoEnabled =
            prefs.getBoolean(LOGO_ENABLED_KEY, true)
        val logoEffect =
            prefs.getString(
                LOGO_EFFECT_KEY,
                "steady"
            ) ?: "steady"
        val logoColor =
            prefs.getInt(LOGO_COLOR_KEY, 1)

        val shoulderEnabled =
            prefs.getBoolean(
                SHOULDER_ENABLED_KEY,
                true
            )
        val shoulderEffect =
            prefs.getString(
                SHOULDER_EFFECT_KEY,
                "breathe"
            ) ?: "breathe"
        val shoulderColor =
            prefs.getInt(
                SHOULDER_COLOR_KEY,
                8
            )

        val signature = listOf(
            fanEnabled,
            fanEffect,
            fanColor,
            logoEnabled,
            logoEffect,
            logoColor,
            shoulderEnabled,
            shoulderEffect,
            shoulderColor
        ).joinToString("|")

        ModeTransitionCoordinator.applyLedProfile(
            context = context,
            owner = LedOwner.CHARGING,
            signature = signature,
            force = force
        ) {
            if (fanEnabled) {
                if (
                    fanEffect.startsWith(
                        "preset:"
                    )
                ) {
                    HardwareController
                        .setFanLedStockPreset(
                            fanEffect.removePrefix(
                                "preset:"
                            )
                        )
                } else {
                    HardwareController
                        .setFanLedEffect(
                            fanEffect,
                            fanColor
                        )
                }
            } else {
                HardwareController
                    .setFanLedEnabled(false)
            }

            if (logoEnabled) {
                HardwareController
                    .setLogoLedEffect(
                        logoEffect,
                        logoColor
                    )
            } else {
                HardwareController
                    .setLogoLedEnabled(false)
            }

            if (shoulderEnabled) {
                HardwareController
                    .setShoulderLedEffect(
                        shoulderEffect,
                        shoulderColor
                    )
            } else {
                HardwareController
                    .setShoulderLedEnabled(false)
            }
        }
    }
}
