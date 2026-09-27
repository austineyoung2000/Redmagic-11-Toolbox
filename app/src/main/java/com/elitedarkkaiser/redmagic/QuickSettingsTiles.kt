package com.elitedarkkaiser.redmagic

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.concurrent.Executors

private object QuickSettingsTileWorker {
    private val executor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "RedMagicQuickTiles").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    fun execute(block: () -> Unit) {
        executor.execute(block)
    }
}

private object QuickSettingsTileStorage {
    private const val PREFS = "quick_settings_tiles"
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

abstract class RedMagicTileService : TileService() {
    private val mainHandler = Handler(Looper.getMainLooper())

    final override fun onStartListening() {
        super.onStartListening()

        if (!DeviceCompatibility.isSupportedDevice()) {
            showUnsupportedTile()
            return
        }

        refreshTile()
    }

    final override fun onClick() {
        super.onClick()

        if (!DeviceCompatibility.isSupportedDevice()) {
            showUnsupportedTile()
            return
        }

        qsTile?.state = Tile.STATE_UNAVAILABLE
        qsTile?.updateTile()

        QuickSettingsTileWorker.execute {
            runCatching { performTileAction() }
            mainHandler.post { refreshTile() }
        }
    }

    protected fun updateTile(
        active: Boolean,
        label: String,
        subtitle: String
    ) {
        val tile = qsTile ?: return
        tile.label = label
        tile.state = if (active) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }

        tile.contentDescription = "$label, $subtitle"
        tile.updateTile()
    }

    protected fun runStateRead(
        reader: () -> Unit
    ) {
        QuickSettingsTileWorker.execute {
            runCatching(reader)
        }
    }

    private fun showUnsupportedTile() {
        val tile = qsTile ?: return
        tile.label = "Redmagic 11 Toolbox"
        tile.state = Tile.STATE_UNAVAILABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "NX809J only"
        }
        tile.contentDescription =
            "Redmagic 11 Toolbox, NX809J only"
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    protected fun openControlCenter() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                8203,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    protected abstract fun refreshTile()
    protected abstract fun performTileAction()
}

class FanTileService : RedMagicTileService() {
    override fun refreshTile() {
        runStateRead {
            val telemetry = HardwareTelemetry.read()
            val enabled = telemetry.fanEnabled == true
            val level = telemetry.fanLevel ?: 0

            if (enabled && level > 0) {
                QuickSettingsTileStorage.saveFanLevel(this, level)
            }

            Handler(Looper.getMainLooper()).post {
                updateTile(
                    active = enabled,
                    label = "Cooling Fan",
                    subtitle = if (enabled) {
                        "Level $level"
                    } else {
                        "Off"
                    }
                )
            }
        }
    }

    override fun performTileAction() {
        val telemetry = HardwareTelemetry.read()
        val enabled = telemetry.fanEnabled == true

        saveAutoFanEnabledStorage(this, false)
        HardwareServiceActions.stopAutoFan(this)

        if (enabled) {
            telemetry.fanLevel?.let {
                QuickSettingsTileStorage.saveFanLevel(this, it)
            }
            HardwareController.setFanLevel(0)
        } else {
            HardwareController.setFanLevel(
                QuickSettingsTileStorage.lastFanLevel(this)
            )
        }
    }
}

class PumpTileService : RedMagicTileService() {
    override fun refreshTile() {
        runStateRead {
            val enabled =
                HardwareController.readPumpEnabled()
                    ?.trim()
                    ?.toIntOrNull()
                    ?.let { it != 0 } == true
            val profile = savedPumpStateStorage(this).profile

            Handler(Looper.getMainLooper()).post {
                updateTile(
                    active = enabled,
                    label = "Cooling Pump",
                    subtitle = if (enabled) {
                        profile.replaceFirstChar {
                            it.titlecase()
                        }
                    } else {
                        "Off"
                    }
                )
            }
        }
    }

    override fun performTileAction() {
        val state = savedPumpStateStorage(this)
        val enabled =
            HardwareController.readPumpEnabled()
                ?.trim()
                ?.toIntOrNull()
                ?.let { it != 0 } == true

        saveAutoPumpStateStorage(this, false)
        HardwareServiceActions.stopAutoPump(this)

        if (enabled) {
            HardwareController.enablePump(false)
            savePumpStateStorage(this, false, state.profile)
        } else {
            HardwareController.setPumpProfile(state.profile)
            savePumpStateStorage(this, true, state.profile)
        }
    }
}

class AutoCoolingTileService : RedMagicTileService() {
    override fun refreshTile() {
        val fan = isAutoFanEnabledStorage(this)
        val pump = savedPumpStateStorage(this).autoEnabled
        updateTile(
            active = fan || pump,
            label = "Auto Cooling",
            subtitle = when {
                fan && pump -> "Fan + pump"
                fan -> "Fan only"
                pump -> "Pump only"
                else -> "Off"
            }
        )
    }

    override fun performTileAction() {
        val enable =
            !isAutoFanEnabledStorage(this) ||
                !savedPumpStateStorage(this).autoEnabled

        saveAutoFanEnabledStorage(this, enable)
        saveAutoPumpStateStorage(this, enable)

        if (enable) {
            HardwareServiceActions.startAutoFan(this)
            HardwareServiceActions.startAutoPump(this)
        } else {
            HardwareServiceActions.stopAutoFan(this)
            HardwareServiceActions.stopAutoPump(this)
        }
    }
}

class TriggersTileService : RedMagicTileService() {
    override fun refreshTile() {
        runStateRead {
            val enabled = HardwareController.areTriggersEnabled()
            Handler(Looper.getMainLooper()).post {
                updateTile(
                    active = enabled,
                    label = "Shoulder Triggers",
                    subtitle = if (enabled) "Enabled" else "Disabled"
                )
            }
        }
    }

    override fun performTileAction() {
        if (HardwareController.areTriggersEnabled()) {
            HardwareServiceActions.disableTriggersUntilRestart(this)
        } else {
            HardwareServiceActions.enableTriggersManually(this)
        }
    }
}

class RgbStudioTileService : RedMagicTileService() {
    override fun refreshTile() {
        val enabled = RgbStudioStorage.isEnabled(this)
        updateTile(
            active = enabled,
            label = "RGB Studio",
            subtitle = if (enabled) "Running" else "Off"
        )
    }

    override fun performTileAction() {
        val enabled = RgbStudioStorage.isEnabled(this)
        RgbStudioStorage.setEnabled(this, !enabled)

        if (enabled) {
            HardwareServiceActions.stopRgbCycle(this)
        } else {
            HardwareServiceActions.startRgbCycle(this)
        }
    }
}

class MasterProfileTileService : RedMagicTileService() {
    override fun refreshTile() {
        val profile = MasterProfileStorage.lastAppliedProfile(this)
        updateTile(
            active = profile != null,
            label = "Master Profile",
            subtitle = profile?.name ?: "Choose in app"
        )
    }

    override fun performTileAction() {
        val profile = MasterProfileStorage.lastAppliedProfile(this)
        if (profile == null) {
            Handler(Looper.getMainLooper()).post {
                openControlCenter()
            }
            return
        }

        MasterProfileActions.applyProfile(this, profile)
    }
}
