package com.elitedarkkaiser.redmagic

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean

class GameModeService : Service() {

    companion object {
        const val EXTRA_APPLY_SAVED_PROFILE =
            "apply_saved_game_mode_profile"
        const val EXTRA_CONTINUE_AFTER_APPLY =
            "continue_game_mode_after_profile_apply"
    }

    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler

    private var gameModeActiveFor: String? = null
    private var gameModeApplyPendingFor: String? = null
    private var pollingPausedForScreenOff = false

    private val activeGamePollMs = 120_000L
    private val foregroundDebounceMs = 1_500L

    private var pendingForegroundPackage: String? = null
    private val stopping = AtomicBoolean(false)

    private val foregroundPackageRunnable = Runnable {
        val pkg = pendingForegroundPackage
        pendingForegroundPackage = null

        if (!pkg.isNullOrBlank()) {
            handleForegroundPackageNow(pkg)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            handler.post {
                if (!GameModeLifecyclePolicy.acceptsWork(stopping.get())) {
                    return@post
                }

                when (action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        pollingPausedForScreenOff = true
                        handler.removeCallbacks(pollRunnable)
                        handler.removeCallbacks(
                            foregroundPackageRunnable
                        )
                        pendingForegroundPackage = null

                        if (GameModeLifecyclePolicy.needsRestore(
                                activePackage = gameModeActiveFor,
                                ledOverrideActive =
                                    isGameModeLedOverrideActiveStorage(
                                        this@GameModeService
                                    )
                            )
                        ) {
                            restoreAndClear("screen off")
                        }

                        android.util.Log.i(
                            "RedmagicGameMode",
                            "screen off: paused game mode polling"
                        )
                        stopSelf()
                    }

                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_USER_PRESENT -> {
                        pollingPausedForScreenOff = false
                        android.util.Log.i(
                            "RedmagicGameMode",
                            "screen on/unlock: waiting for foreground app event"
                        )
                    }
                }
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!GameModeLifecyclePolicy.acceptsWork(stopping.get())) return

            try {
                val currentPkg = getForegroundPackageName()
                val tracked = getSavedGamePackagesStorage(this@GameModeService)

                if (!currentPkg.isNullOrBlank() && tracked.contains(currentPkg)) {
                    if (gameModeActiveFor != currentPkg) {
                        gameModeActiveFor = currentPkg
                        setGameModeLedOverrideActiveStorage(this@GameModeService, true)
                        applyGameModeProfile()
                    } else if (
                        gameModeApplyPendingFor == currentPkg &&
                        LedScreenPolicy.isScreenInteractive(this@GameModeService)
                    ) {
                        applyGameModeProfile()
                    }
                } else if (!currentPkg.isNullOrBlank()) {
                    if (gameModeActiveFor != null) {
                        restoreAndClear("foreground app changed")
                    }
                    stopSelf()
                } else if (gameModeActiveFor == null) {
                    stopSelf()
                }
            } catch (_: Throwable) {
            } finally {
                if (
                    GameModeLifecyclePolicy.acceptsWork(
                        stopping = stopping.get(),
                        pausedForScreenOff = pollingPausedForScreenOff
                    ) &&
                    gameModeActiveFor != null
                ) {
                    handler.postDelayed(this, activeGamePollMs)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        workerThread = HandlerThread(
            "RedMagicGameMode",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()
        }
        handler = Handler(workerThread.looper)

        handler.post {
            if (!GameModeLifecyclePolicy.acceptsWork(stopping.get())) {
                return@post
            }
            ChargingLedRecovery.repairStaleChargingOwnership(
                this@GameModeService
            )
        }

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        )

        // No idle polling here. GameMode starts from an explicit foreground app event.
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val pkg = intent?.getStringExtra("foreground_pkg")
        val applySavedProfile =
            intent?.getBooleanExtra(
                EXTRA_APPLY_SAVED_PROFILE,
                false
            ) == true
        val continueAfterApply =
            intent?.getBooleanExtra(
                EXTRA_CONTINUE_AFTER_APPLY,
                false
            ) == true

        handler.post {
            if (!GameModeLifecyclePolicy.acceptsWork(stopping.get())) {
                return@post
            }

            when {
                applySavedProfile -> {
                    applySavedProfileNow()

                    if (gameModeActiveFor != null) {
                        handler.removeCallbacks(pollRunnable)
                        handler.post(pollRunnable)
                    } else if (!continueAfterApply) {
                        stopSelf(startId)
                    }
                }

                !pkg.isNullOrBlank() -> {
                    scheduleForegroundPackage(pkg)
                }

                else -> {
                    /*
                     * Service restoration requests may not carry
                     * a package. Probe UsageStats immediately so
                     * process recreation restores the right mode.
                     */
                    handler.removeCallbacks(pollRunnable)
                    handler.post(pollRunnable)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun applySavedProfileNow() {
        val profile =
            getSavedGameModeProfileStorage(this)

        GameModeActions.applyProfileNow(
            profile = profile,
            applyFanLed = { effect, color ->
                if (effect.startsWith("preset:")) {
                    HardwareController.setFanLedStockPreset(
                        effect.removePrefix("preset:")
                    )
                } else {
                    HardwareController.setFanLedEffect(
                        effect,
                        color
                    )
                }
            }
        )
    }

    private fun scheduleForegroundPackage(
        currentPkg: String
    ) {
        if (!GameModeLifecyclePolicy.acceptsWork(stopping.get())) return

        pendingForegroundPackage = currentPkg

        handler.removeCallbacks(
            foregroundPackageRunnable
        )
        handler.postDelayed(
            foregroundPackageRunnable,
            foregroundDebounceMs
        )
    }

    private fun handleForegroundPackageNow(
        currentPkg: String
    ) {
        if (
            !GameModeLifecyclePolicy.acceptsWork(
                stopping = stopping.get(),
                pausedForScreenOff = pollingPausedForScreenOff
            )
        ) return

        val tracked = getSavedGamePackagesStorage(this)
        handler.removeCallbacks(pollRunnable)

        if (!tracked.contains(currentPkg)) {
            if (GameModeLifecyclePolicy.needsRestore(
                    activePackage = gameModeActiveFor,
                    ledOverrideActive =
                        isGameModeLedOverrideActiveStorage(this)
                )
            ) {
                restoreAndClear("left tracked game")

                android.util.Log.i(
                    "RedmagicGameMode",
                    "left tracked game: restored normal hardware profile"
                )
            }

            stopSelf()
            return
        }

        if (gameModeActiveFor != currentPkg) {
            gameModeActiveFor = currentPkg
            setGameModeLedOverrideActiveStorage(this, true)
            applyGameModeProfile()
        } else if (
            gameModeApplyPendingFor == currentPkg &&
            LedScreenPolicy.isScreenInteractive(this)
        ) {
            applyGameModeProfile()
        }

        handler.postDelayed(pollRunnable, activeGamePollMs)
    }

    override fun onDestroy() {
        if (!stopping.compareAndSet(false, true)) {
            super.onDestroy()
            return
        }

        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(
            foregroundPackageRunnable
        )
        pendingForegroundPackage = null

        runCatching {
            unregisterReceiver(screenReceiver)
        }

        val cleanupPosted = handler.postAtFrontOfQueue {
            handler.removeCallbacks(pollRunnable)
            handler.removeCallbacks(foregroundPackageRunnable)

            if (GameModeLifecyclePolicy.needsRestore(
                    activePackage = gameModeActiveFor,
                    ledOverrideActive =
                        isGameModeLedOverrideActiveStorage(
                            this@GameModeService
                        )
                )
            ) {
                restoreAndClear("service destroyed")
            }

            workerThread.quitSafely()
        }

        if (!cleanupPosted) {
            workerThread.quitSafely()
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun restoreAndClear(reason: String) {
        val activePackage = gameModeActiveFor
        val overrideActive = isGameModeLedOverrideActiveStorage(this)

        if (!GameModeLifecyclePolicy.needsRestore(
                activePackage = activePackage,
                ledOverrideActive = overrideActive
            )
        ) return

        /* Clear ownership before restoring so re-entrant cleanup cannot run
         * the same hardware restoration twice. */
        gameModeActiveFor = null
        gameModeApplyPendingFor = null

        runCatching {
            restoreNormalProfile()
        }.onFailure { error ->
            android.util.Log.e(
                "RedmagicGameMode",
                "Failed to restore after $reason for " +
                    (activePackage ?: "stale LED override"),
                error
            )
        }
    }

    private fun getForegroundPackageName(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 15_000L

        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        var lastForeground: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                val pkg = event.packageName
                if (!pkg.isNullOrBlank() && !shouldIgnorePackage(pkg)) {
                    lastForeground = pkg
                }
            }
        }

        return lastForeground
    }

    private fun shouldIgnorePackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg == "com.android.systemui") return true
        return false
    }

    

    private fun getProfileForPackage(pkg: String): Map<String, Any> {
        val prefs = getSharedPreferences("redmagic_hw_controls_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("game_profile_$pkg", null) ?: return emptyMap()

        return try {
            val obj = org.json.JSONObject(json)
            mapOf(
                "fanEnabled" to obj.optBoolean("fanEnabled", true),
                "fanLevel" to obj.optInt("fanLevel", 3),
                "fanLedEnabled" to obj.optBoolean("fanLedEnabled", true),
                "fanLedEffect" to obj.optString("fanLedEffect", "steady"),
                "fanLedColor" to obj.optInt("fanLedColor", 5),
                "fanLedModeType" to obj.optString("fanLedModeType", "basic"),
                "fanLedPresetValue" to obj.optString("fanLedPresetValue", ""),
                "logoLedEnabled" to obj.optBoolean("logoLedEnabled", true),
                "logoLedEffect" to obj.optString("logoLedEffect", "steady"),
                "logoLedColor" to obj.optInt("logoLedColor", 1),
                "shoulderLedEnabled" to obj.optBoolean("shoulderLedEnabled", true),
                "shoulderLedEffect" to obj.optString("shoulderLedEffect", "breathe"),
                "shoulderLedColor" to obj.optInt("shoulderLedColor", 8)
            )
        } catch (_: Throwable) {
            emptyMap()
        }
    }
    private fun applyGameModeProfile() {
        if (HardwareScreenPolicy.blockNormalLedsWhileScreenOff(this, "game-mode-apply-screen-off")) return
        val prefs = getSharedPreferences("redmagic_hw_controls_prefs", Context.MODE_PRIVATE)

        val pkg = gameModeActiveFor ?: return

        if (!LedOwnership.canGameModeApply(this)) {
            gameModeApplyPendingFor = pkg

            android.util.Log.i(
                "RedmagicLedOwnership",
                "GameModeService deferred game LED apply because owner=${LedOwnership.current(this)}"
            )
            return
        }
        val profile = getProfileForPackage(pkg)

        val fanEnabled = profile["fanEnabled"] as? Boolean ?: prefs.getBoolean("game_mode_fan_enabled", true)
        val fanLevel = profile["fanLevel"] as? Int ?: prefs.getInt("game_mode_fan_level", 3)
        val pumpEnabled = prefs.getBoolean("game_mode_pump_enabled", false)
        val pumpProfile = prefs.getString("game_mode_pump_profile", "quick") ?: "quick"

        val fanLedEnabled = profile["fanLedEnabled"] as? Boolean ?: prefs.getBoolean("game_mode_fan_led_enabled", true)
        val fanLedEffect = profile["fanLedEffect"] as? String ?: prefs.getString("game_mode_fan_led_effect", "steady") ?: "steady"
        val fanLedColor = profile["fanLedColor"] as? Int ?: prefs.getInt("game_mode_fan_led_color", 5)
        val fanLedModeType = profile["fanLedModeType"] as? String ?: "basic"
        val fanLedPresetValue = profile["fanLedPresetValue"] as? String ?: ""

        val logoLedEnabled = profile["logoLedEnabled"] as? Boolean ?: prefs.getBoolean("game_mode_logo_led_enabled", true)
        val logoLedEffect = profile["logoLedEffect"] as? String ?: prefs.getString("game_mode_logo_led_effect", "steady") ?: "steady"
        val logoLedColor = profile["logoLedColor"] as? Int ?: prefs.getInt("game_mode_logo_led_color", 1)

        val shoulderLedEnabled = profile["shoulderLedEnabled"] as? Boolean ?: prefs.getBoolean("game_mode_shoulder_led_enabled", true)
        val shoulderLedEffect = profile["shoulderLedEffect"] as? String ?: prefs.getString("game_mode_shoulder_led_effect", "breathe") ?: "breathe"
        val shoulderLedColor = profile["shoulderLedColor"] as? Int ?: prefs.getInt("game_mode_shoulder_led_color", 8)

        fun applyOnce(reason: String) {
            if (gameModeActiveFor != pkg) return

            if (fanEnabled) {
                HardwareController.setFanLevel(
                    fanLevel
                )
            } else {
                HardwareController.enableFan(false)
            }

            if (pumpEnabled) {
                HardwareController.setPumpProfile(
                    pumpProfile
                )
            } else {
                HardwareController.enablePump(false)
            }

            val ledSignature = listOf(
                pkg,
                fanLedEnabled,
                fanLedEffect,
                fanLedColor,
                fanLedModeType,
                fanLedPresetValue,
                logoLedEnabled,
                logoLedEffect,
                logoLedColor,
                shoulderLedEnabled,
                shoulderLedEffect,
                shoulderLedColor
            ).joinToString("|")

            ModeTransitionCoordinator.applyLedProfile(
                context = this@GameModeService,
                owner = LedOwner.GAME_MODE,
                signature = ledSignature
            ) {
                if (fanLedEnabled) {
                    if (
                        fanLedModeType == "preset" &&
                        fanLedPresetValue.isNotBlank()
                    ) {
                        HardwareController
                            .setFanLedStockPreset(
                                fanLedPresetValue
                            )
                    } else if (
                        fanLedEffect.startsWith(
                            "preset:"
                        )
                    ) {
                        HardwareController
                            .setFanLedStockPreset(
                                fanLedEffect.removePrefix(
                                    "preset:"
                                )
                            )
                    } else {
                        HardwareController
                            .setFanLedEffect(
                                fanLedEffect,
                                fanLedColor
                            )
                    }
                } else {
                    HardwareController
                        .setFanLedEnabled(false)
                }

                if (logoLedEnabled) {
                    HardwareController
                        .setLogoLedEffect(
                            logoLedEffect,
                            logoLedColor
                        )
                } else {
                    HardwareController
                        .setLogoLedEnabled(false)
                }

                if (shoulderLedEnabled) {
                    HardwareController
                        .setShoulderLedEffect(
                            shoulderLedEffect,
                            shoulderLedColor
                        )
                } else {
                    HardwareController
                        .setShoulderLedEnabled(false)
                }

                android.util.Log.i(
                    "RedmagicGameMode",
                    "apply[$reason] pkg=$pkg " +
                        "fan=$fanLedEnabled/" +
                        "$fanLedEffect/$fanLedColor " +
                        "logo=$logoLedEnabled/" +
                        "$logoLedEffect/$logoLedColor " +
                        "shoulder=$shoulderLedEnabled/" +
                        "$shoulderLedEffect/" +
                        "$shoulderLedColor"
                )
            }

            gameModeApplyPendingFor = null
        }

        applyOnce("now")
    }
    private fun restoreNormalProfile() {
        val prefs = getSharedPreferences(
            "redmagic_hw_controls_prefs",
            Context.MODE_PRIVATE
        )

        val fanEnabled =
            prefs.getBoolean("fan_enabled", false)
        val fanLevel =
            prefs.getInt("fan_level", 0)
        val pumpEnabled =
            prefs.getBoolean("pump_enabled", false)
        val pumpProfile =
            prefs.getString(
                "pump_profile",
                "quick"
            ) ?: "quick"

        /*
         * If a call currently owns the fan pause, update the
         * state that Call Lighting will restore. Do not briefly
         * restart the fan underneath the active call.
         */
        if (
            CallLightingState.isActive(this) &&
            CallLightingState
                .wasFanPausedForCall(this)
        ) {
            CallLightingState.savePreCallFanState(
                context = this,
                enabled = fanEnabled,
                level = fanLevel
            )
        } else if (fanEnabled) {
            HardwareController.setFanLevel(fanLevel)
        } else {
            HardwareController.enableFan(false)
        }

        if (pumpEnabled) {
            HardwareController.setPumpProfile(
                pumpProfile
            )
        } else {
            HardwareController.enablePump(false)
        }

        /*
         * Release Game Mode before selecting the next owner.
         * This prevents its own saved flag from winning the
         * restoration decision.
         */
        setGameModeLedOverrideActiveStorage(
            this,
            false
        )
        gameModeApplyPendingFor = null

        ModeTransitionCoordinator
            .restoreEffectiveOwner(
                this,
                "game-mode-ended"
            )

        android.util.Log.i(
            "RedmagicGameMode",
            "restored normal cooling and reconciled LEDs"
        )
    }
}
