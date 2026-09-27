package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs
import java.util.Calendar

data class SliderDualAppConfig(
    val enabled: Boolean = false,
    val defaultUpPackage: String? = null,
    val defaultDownPackage: String? = null,
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinutes: Int = 22 * 60,
    val scheduleEndMinutes: Int = 7 * 60,
    val scheduledUpPackage: String? = null,
    val scheduledDownPackage: String? = null
) {
    fun packageForState(
        sliderUp: Boolean,
        minuteOfDay: Int = currentMinuteOfDay()
    ): String? {
        val scheduled = scheduleEnabled &&
            isInsideSchedule(
                minuteOfDay,
                scheduleStartMinutes,
                scheduleEndMinutes
            )

        return when {
            scheduled && sliderUp -> scheduledUpPackage
            scheduled -> scheduledDownPackage
            sliderUp -> defaultUpPackage
            else -> defaultDownPackage
        }
    }

    fun isComplete(): Boolean {
        if (
            defaultUpPackage.isNullOrBlank() ||
            defaultDownPackage.isNullOrBlank()
        ) {
            return false
        }

        return !scheduleEnabled ||
            (
                !scheduledUpPackage.isNullOrBlank() &&
                    !scheduledDownPackage.isNullOrBlank()
                )
    }

    companion object {
        fun isInsideSchedule(
            minuteOfDay: Int,
            startMinutes: Int,
            endMinutes: Int
        ): Boolean {
            val minute = minuteOfDay.coerceIn(0, 1439)
            val start = startMinutes.coerceIn(0, 1439)
            val end = endMinutes.coerceIn(0, 1439)

            return when {
                start == end -> true
                start < end -> minute in start until end
                else -> minute >= start || minute < end
            }
        }

        private fun currentMinuteOfDay(): Int {
            val calendar = Calendar.getInstance()
            return calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(Calendar.MINUTE)
        }
    }
}

object SliderDualAppStorage {
    private const val ENABLED = "slider_dual_app_enabled"
    private const val DEFAULT_UP = "slider_dual_app_default_up"
    private const val DEFAULT_DOWN = "slider_dual_app_default_down"
    private const val SCHEDULE_ENABLED =
        "slider_dual_app_schedule_enabled"
    private const val SCHEDULE_START =
        "slider_dual_app_schedule_start"
    private const val SCHEDULE_END =
        "slider_dual_app_schedule_end"
    private const val SCHEDULED_UP =
        "slider_dual_app_scheduled_up"
    private const val SCHEDULED_DOWN =
        "slider_dual_app_scheduled_down"
    private const val PREVIOUS_MODE =
        "slider_dual_app_previous_mode"
    private const val PREVIOUS_APP =
        "slider_dual_app_previous_app"
    private const val PREVIOUS_SHORTCUT =
        "slider_dual_app_previous_shortcut"
    private const val PREVIOUS_SHORTCUT_LABEL =
        "slider_dual_app_previous_shortcut_label"
    private const val PREVIOUS_VALID =
        "slider_dual_app_previous_valid"

    @Synchronized
    fun read(context: Context): SliderDualAppConfig {
        val prefs = context.getSharedPreferences(
            AppPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        return SliderDualAppConfig(
            enabled = prefs.getBoolean(ENABLED, false),
            defaultUpPackage = prefs.getString(DEFAULT_UP, null),
            defaultDownPackage = prefs.getString(DEFAULT_DOWN, null),
            scheduleEnabled = prefs.getBoolean(
                SCHEDULE_ENABLED,
                false
            ),
            scheduleStartMinutes = prefs.getInt(
                SCHEDULE_START,
                22 * 60
            ).coerceIn(0, 1439),
            scheduleEndMinutes = prefs.getInt(
                SCHEDULE_END,
                7 * 60
            ).coerceIn(0, 1439),
            scheduledUpPackage = prefs.getString(
                SCHEDULED_UP,
                null
            ),
            scheduledDownPackage = prefs.getString(
                SCHEDULED_DOWN,
                null
            )
        )
    }

    @Synchronized
    fun save(context: Context, config: SliderDualAppConfig) {
        context.getSharedPreferences(
            AppPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(ENABLED, config.enabled)
            .putString(DEFAULT_UP, config.defaultUpPackage)
            .putString(DEFAULT_DOWN, config.defaultDownPackage)
            .putBoolean(
                SCHEDULE_ENABLED,
                config.scheduleEnabled
            )
            .putInt(
                SCHEDULE_START,
                config.scheduleStartMinutes.coerceIn(0, 1439)
            )
            .putInt(
                SCHEDULE_END,
                config.scheduleEndMinutes.coerceIn(0, 1439)
            )
            .putString(SCHEDULED_UP, config.scheduledUpPackage)
            .putString(
                SCHEDULED_DOWN,
                config.scheduledDownPackage
            )
            .apply()
    }

    @Synchronized
    fun capturePreviousMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(
            AppPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        if (prefs.getBoolean(PREVIOUS_VALID, false)) {
            return true
        }

        val mode = MagicKeyActions.readModeValue()
        if (mode !in setOf(0, 1, 2, 3, 4, 5, 16, 17)) {
            return false
        }

        val appPackage = if (mode == 16) {
            RootShell.execForOutput(
                "settings get system physical_key_function_app_value"
            )?.trim()?.takeIf {
                it.isNotBlank() && it != "null"
            } ?: savedMagicKeyAppPackageStorage(context)
        } else {
            null
        }

        if (mode == 16 && appPackage.isNullOrBlank()) {
            return false
        }

        val shortcutTarget = if (mode == 17) {
            RootShell.execForOutput(
                "settings get system " +
                    "physical_key_function_shortcut_value"
            )?.trim()?.takeIf {
                it.isNotBlank() &&
                    it != "null" &&
                    it.contains(';')
            }
        } else {
            null
        }

        if (mode == 17 && shortcutTarget == null) {
            return false
        }

        val shortcutLabel = if (mode == 17) {
            savedMagicKeyShortcutStorage(context)?.label
        } else {
            null
        }

        val editor = prefs.edit()
            .putInt(PREVIOUS_MODE, mode)
            .putBoolean(PREVIOUS_VALID, true)

        if (appPackage == null) {
            editor.remove(PREVIOUS_APP)
        } else {
            editor.putString(PREVIOUS_APP, appPackage)
        }

        if (shortcutTarget == null) {
            editor
                .remove(PREVIOUS_SHORTCUT)
                .remove(PREVIOUS_SHORTCUT_LABEL)
        } else {
            editor.putString(
                PREVIOUS_SHORTCUT,
                shortcutTarget
            )
            editor.putString(
                PREVIOUS_SHORTCUT_LABEL,
                shortcutLabel
            )
        }

        return editor.commit()
    }

    @Synchronized
    fun disable(
        context: Context,
        restorePrevious: Boolean = false
    ): Boolean {
        val current = read(context)
        if (current.enabled) {
            save(context, current.copy(enabled = false))
        }
        HardwareServiceActions.stopSliderDualApp(context)

        return if (restorePrevious) {
            restorePreviousMode(context)
        } else {
            clearPreviousMode(context)
            true
        }
    }

    @Synchronized
    private fun restorePreviousMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(
            AppPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(PREVIOUS_VALID, false)) {
            return false
        }

        val mode = prefs.getInt(PREVIOUS_MODE, -1)
        val appPackage = prefs.getString(PREVIOUS_APP, null)
        val shortcutTarget = prefs.getString(
            PREVIOUS_SHORTCUT,
            null
        )
        val shortcutParts = shortcutTarget
            ?.split(';', limit = 2)
            ?.takeIf {
                it.size == 2 &&
                    it[0].isNotBlank() &&
                    it[1].isNotBlank()
            }

        val restored = when (mode) {
            0 -> HardwareController.disableSliderSystemHandling()
            1 -> HardwareController.setSliderOpenCamera()
            2 -> HardwareController.setSliderOpenGameSpace()
            3 -> HardwareController.setSliderSoundMode()
            4 -> HardwareController.setSliderFlashlight()
            5 -> HardwareController.setSliderVoiceRecorder()
            16 -> !appPackage.isNullOrBlank() &&
                HardwareController.setSliderLaunchApp(appPackage)
            17 -> shortcutParts != null &&
                HardwareController.setSliderLaunchShortcut(
                    shortcutParts[0],
                    shortcutParts[1]
                )
            else -> false
        }

        if (restored) {
            saveMagicKeyAppPackageStorage(
                context,
                appPackage.takeIf { mode == 16 }
            )
            saveMagicKeyShortcutStorage(
                context,
                if (mode == 17 && shortcutParts != null) {
                    MagicKeyShortcutTarget(
                        packageName = shortcutParts[0],
                        shortcutId = shortcutParts[1],
                        label = prefs.getString(
                            PREVIOUS_SHORTCUT_LABEL,
                            null
                        )?.takeIf { it.isNotBlank() }
                            ?: shortcutParts[1]
                    )
                } else {
                    null
                }
            )
            clearPreviousMode(context)
        }

        return restored
    }

    @Synchronized
    private fun clearPreviousMode(context: Context) {
        context.getSharedPreferences(
            AppPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .remove(PREVIOUS_MODE)
            .remove(PREVIOUS_APP)
            .remove(PREVIOUS_SHORTCUT)
            .remove(PREVIOUS_SHORTCUT_LABEL)
            .remove(PREVIOUS_VALID)
            .apply()
    }

    @Synchronized
    fun summary(context: Context): String {
        val config = read(context)
        return when {
            !config.enabled -> "DUAL-APP SLIDER: Off"
            config.scheduleEnabled ->
                "DUAL-APP SLIDER: On • Schedule On"
            else -> "DUAL-APP SLIDER: On"
        }
    }
}
