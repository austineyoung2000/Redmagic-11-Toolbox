package com.elitedarkkaiser.redmagic

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Locale
import java.util.concurrent.Executors

class CoolingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (!DeviceCompatibility.isSupportedDevice()) {
            appWidgetManager.updateAppWidget(
                appWidgetIds,
                unsupportedViews(context)
            )
            return
        }

        appWidgetManager.updateAppWidget(
            appWidgetIds,
            buildViews(context, null)
        )
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (!DeviceCompatibility.isSupportedDevice()) {
            return
        }

        val action = intent.action ?: return
        if (action !in widgetActions) return

        val pendingResult = goAsync()
        worker.execute {
            try {
                when (action) {
                    ACTION_TOGGLE_FAN -> toggleFan(context)
                    ACTION_TOGGLE_PUMP -> togglePump(context)
                }
                updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun refreshAsync(context: Context) {
        val appContext = context.applicationContext
        worker.execute { updateAllWidgets(appContext) }
    }

    private fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, CoolingWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val telemetry = HardwareTelemetry.read()
        if (telemetry.fanEnabled == true && (telemetry.fanLevel ?: 0) > 0) {
            WidgetState.saveFanLevel(context, telemetry.fanLevel ?: 3)
        }
        manager.updateAppWidget(ids, buildViews(context, telemetry))
    }

    private fun unsupportedViews(context: Context): RemoteViews {
        return RemoteViews(
            context.packageName,
            R.layout.widget_cooling
        ).apply {
            setTextViewText(
                R.id.widget_temperature,
                "Unsupported"
            )
            setTextViewText(
                R.id.widget_fan_state,
                "NX809J only"
            )
            setTextViewText(
                R.id.widget_pump_state,
                "Controls disabled"
            )
            setTextViewText(R.id.widget_fan_button, "FAN —")
            setTextViewText(R.id.widget_pump_button, "PUMP —")
            setTextViewText(
                R.id.widget_refresh_button,
                "UNAVAILABLE"
            )
            setOnClickPendingIntent(
                R.id.widget_root,
                openAppIntent(context)
            )
        }
    }

    private fun buildViews(
        context: Context,
        telemetry: HardwareTelemetrySnapshot?
    ): RemoteViews {
        val fanEnabled = telemetry?.fanEnabled == true
        val fanLevel = telemetry?.fanLevel ?: 0
        val pumpEnabled = telemetry?.pumpEnabled
            ?.trim()
            ?.toIntOrNull()
            ?.let { it != 0 } == true

        return RemoteViews(
            context.packageName,
            R.layout.widget_cooling
        ).apply {
            setTextViewText(
                R.id.widget_temperature,
                telemetry?.let {
                    formatTemperature(
                        it.temperatureC,
                        isUseFahrenheitStorage(context)
                    )
                } ?: "Refreshing…"
            )
            setTextViewText(
                R.id.widget_fan_state,
                if (telemetry == null) "Fan —" else if (fanEnabled) {
                    "Fan level $fanLevel"
                } else {
                    "Fan off"
                }
            )
            setTextViewText(
                R.id.widget_pump_state,
                if (telemetry == null) "Pump —" else if (pumpEnabled) {
                    "Pump " + savedPumpStateStorage(context)
                        .profile
                        .replaceFirstChar {
                            it.titlecase(Locale.getDefault())
                        }
                } else {
                    "Pump off"
                }
            )
            setTextViewText(
                R.id.widget_fan_button,
                if (fanEnabled) "FAN ON" else "FAN OFF"
            )
            setTextViewText(
                R.id.widget_pump_button,
                if (pumpEnabled) "PUMP ON" else "PUMP OFF"
            )
            setOnClickPendingIntent(
                R.id.widget_root,
                openAppIntent(context)
            )
            setOnClickPendingIntent(
                R.id.widget_fan_button,
                actionIntent(context, ACTION_TOGGLE_FAN, 9101)
            )
            setOnClickPendingIntent(
                R.id.widget_pump_button,
                actionIntent(context, ACTION_TOGGLE_PUMP, 9102)
            )
            setOnClickPendingIntent(
                R.id.widget_refresh_button,
                actionIntent(context, ACTION_REFRESH, 9103)
            )
        }
    }

    private fun toggleFan(context: Context) {
        val telemetry = HardwareTelemetry.read()
        saveAutoFanEnabledStorage(context, false)
        HardwareServiceActions.stopAutoFan(context)

        if (telemetry.fanEnabled == true) {
            telemetry.fanLevel?.let {
                WidgetState.saveFanLevel(context, it)
            }
            if (HardwareController.setFanLevel(0)) ManualFanStorage.save(context, 0)
        } else {
            val level = WidgetState.lastFanLevel(context)
            if (HardwareController.setFanLevel(level)) ManualFanStorage.save(context, level)
        }
    }

    private fun togglePump(context: Context) {
        val saved = savedPumpStateStorage(context)
        val enabled = HardwareController.readPumpEnabled()
            ?.trim()
            ?.toIntOrNull()
            ?.let { it != 0 } == true

        saveAutoPumpStateStorage(context, false)
        HardwareServiceActions.stopAutoPump(context)

        if (enabled) {
            HardwareController.enablePump(false)
            savePumpStateStorage(context, false, saved.profile)
        } else {
            HardwareController.setPumpProfile(saved.profile)
            savePumpStateStorage(context, true, saved.profile)
        }
    }

    private fun formatTemperature(
        celsius: Float?,
        useFahrenheit: Boolean
    ): String {
        if (celsius == null) return "Temperature unavailable"
        val value = if (useFahrenheit) {
            (celsius * 9f / 5f) + 32f
        } else {
            celsius
        }
        val unit = if (useFahrenheit) "°F" else "°C"
        return String.format(
            Locale.getDefault(),
            "%.1f%s",
            value,
            unit
        )
    }

    private fun actionIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(
            context,
            CoolingWidgetProvider::class.java
        ).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        return PendingIntent.getActivity(
            context,
            9104,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private object WidgetState {
        private const val PREFS = "cooling_widget"
        private const val LAST_FAN_LEVEL = "last_fan_level"

        fun lastFanLevel(context: Context): Int {
            return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            ).getInt(LAST_FAN_LEVEL, 3).coerceIn(1, 5)
        }

        fun saveFanLevel(context: Context, level: Int) {
            if (level !in 1..5) return
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            ).edit().putInt(LAST_FAN_LEVEL, level).apply()
        }
    }

    companion object {
        private const val ACTION_TOGGLE_FAN =
            "com.elitedarkkaiser.redmagic.widget.TOGGLE_FAN"
        private const val ACTION_TOGGLE_PUMP =
            "com.elitedarkkaiser.redmagic.widget.TOGGLE_PUMP"
        private const val ACTION_REFRESH =
            "com.elitedarkkaiser.redmagic.widget.REFRESH"

        private val widgetActions = setOf(
            ACTION_TOGGLE_FAN,
            ACTION_TOGGLE_PUMP,
            ACTION_REFRESH
        )

        private val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "RedMagicCoolingWidget").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }
}
