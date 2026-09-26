package com.elitedarkkaiser.redmagic

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class TriggerAccessibilityService : AccessibilityService() {

    private val rootExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedMagicTriggerActions").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    private val nativeTgkExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedMagicNativeTgk").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    private val refreshRateExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedMagicRefreshRate").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    private var nativeTgkTask: Future<*>? = null
    private var screenReceiverRegistered = false

    @Volatile
    private var lastForegroundPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                deactivateNativeTgk("screen off")
                RefreshRateCoordinator.clearRuntimeState()
            }
        }
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return
        }

        val reportedPackage = event?.packageName
            ?.toString()
            ?: return

        if (reportedPackage.isBlank()) {
            return
        }

        /*
         * Our non-touchable gameplay overlay and SystemUI can emit
         * window events even though the mapped game remains the
         * resumed activity. Resolve those ambiguous events through
         * UsageEvents; when the Control Center, Recents, launcher, or
         * another app is genuinely resumed, the resolved package
         * immediately drives TGK cleanup.
         */
        val pkg = if (
            reportedPackage == packageName ||
            reportedPackage == SYSTEM_UI_PACKAGE
        ) {
            latestResumedPackage() ?: reportedPackage
        } else {
            reportedPackage
        }

        lastForegroundPackage = pkg
        NativeTgkDiagnostics.recordForeground(this, pkg)
        dispatchNativeTgkForForeground(pkg)
        dispatchRefreshRateForForeground(pkg)

        if (
            pkg == packageName ||
            pkg == SYSTEM_UI_PACKAGE
        ) {
            return
        }

        val isTrackedGame =
            getSavedGamePackagesStorage(this).contains(pkg)
        val gameModeActive =
            isGameModeLedOverrideActiveStorage(this)

        /*
         * Game Mode and native TGK mappings are independent.
         * Forward the event to Game Mode only when its existing
         * profile lifecycle requires it.
         */
        if (isTrackedGame || gameModeActive) {
            startService(
                Intent(
                    this,
                    GameModeService::class.java
                ).apply {
                    putExtra("foreground_pkg", pkg)
                }
            )
        }
    }

    override fun onConfigurationChanged(
        newConfig: Configuration
    ) {
        super.onConfigurationChanged(newConfig)

        lastForegroundPackage?.let {
            dispatchNativeTgkForForeground(it)
        }
    }

    override fun onInterrupt() {
        RefreshRateCoordinator.clearRuntimeState()
        deactivateNativeTgk(
            "accessibility service interrupted"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        if (!DeviceCompatibility.isSupportedDevice()) {
            disableSelf()
            return
        }

        registerScreenReceiver()

        /*
         * Repair any native TGK state left behind by an earlier
         * process termination before accepting new foreground
         * application events.
         */
        NativeTgkRuntimeState.clear()
        RefreshRateCoordinator.clearRuntimeState()
        nativeTgkTask = nativeTgkExecutor.submit {
            NativeTgkCoordinator.disable(
                applicationContext,
                "accessibility service connected"
            )
        }

        submitRootAction {
            HardwareServiceActions
                .startTriggersIfAutoStartEnabled(this)
        }
    }

    override fun onDestroy() {
        lastForegroundPackage = null
        NativeTgkRuntimeState.clear()
        RefreshRateCoordinator.clearRuntimeState()

        nativeTgkTask?.cancel(true)
        nativeTgkTask = null

        if (screenReceiverRegistered) {
            runCatching {
                unregisterReceiver(screenReceiver)
            }
            screenReceiverRegistered = false
        }

        nativeTgkExecutor.shutdownNow()
        refreshRateExecutor.shutdownNow()

        Thread(
            {
                NativeTgkCoordinator.disable(
                    applicationContext,
                    "accessibility service destroyed"
                )
            },
            "RedMagicNativeTgkCleanup"
        ).start()

        rootExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun dispatchNativeTgkForForeground(
        packageName: String
    ) {
        if (NativeTgkEditorRuntime.isEditing()) {
            if (NativeTgkRuntimeState.isActive()) {
                deactivateNativeTgk(
                    "target editor active"
                )
            }
            return
        }

        val orientation =
            NativeTgkCoordinator.currentOrientation(this)

        val mappingReady =
            NativeTgkCoordinator.hasReadyMapping(
                context = this,
                packageName = packageName,
                orientation = orientation
            )

        if (!mappingReady) {
            if (NativeTgkRuntimeState.isActive()) {
                deactivateNativeTgk(
                    "left mapped app or orientation"
                )
            }
            return
        }

        if (
            NativeTgkRuntimeState.matches(
                packageName,
                orientation
            )
        ) {
            return
        }

        /*
         * Mark the native path active before the two-second vendor
         * setup delay so the legacy F7/F8 readers cannot perform
         * quick actions during the transition.
         */
        NativeTgkRuntimeState.markActive(
            packageName,
            orientation
        )

        nativeTgkTask?.cancel(true)
        nativeTgkTask = nativeTgkExecutor.submit {
            val result =
                NativeTgkCoordinator.applyForegroundMapping(
                    context = applicationContext,
                    packageName = packageName,
                    orientation = orientation
                )

            if (!result.success) {
                NativeTgkRuntimeState.clearIfMatches(
                    packageName,
                    orientation
                )
            }
        }
    }

    private fun deactivateNativeTgk(reason: String) {
        if (!NativeTgkRuntimeState.isActive()) {
            NativeTgkGameplayOverlay.hide()
            return
        }

        NativeTgkRuntimeState.clear()
        nativeTgkTask?.cancel(true)

        nativeTgkTask = runCatching {
            nativeTgkExecutor.submit {
                NativeTgkCoordinator.disable(
                    applicationContext,
                    reason
                )
            }
        }.getOrNull()
    }

    private fun dispatchRefreshRateForForeground(
        packageName: String
    ) {
        runCatching {
            refreshRateExecutor.execute {
                RefreshRateCoordinator.onForegroundPackage(
                    applicationContext,
                    packageName
                )
            }
        }
    }

    private fun latestResumedPackage(): String? {
        return runCatching {
            val manager = getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = manager.queryEvents(
                end - FOREGROUND_EVENT_LOOKBACK_MS,
                end
            )
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTimestamp = Long.MIN_VALUE

            while (events.hasNextEvent()) {
                events.getNextEvent(event)

                if (
                    event.eventType !=
                    UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.eventType !=
                    UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    continue
                }

                val candidate = event.packageName
                if (
                    !candidate.isNullOrBlank() &&
                    event.timeStamp >= latestTimestamp
                ) {
                    latestPackage = candidate
                    latestTimestamp = event.timeStamp
                }
            }

            latestPackage
        }.getOrNull()
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) {
            return
        }

        val filter = IntentFilter(
            Intent.ACTION_SCREEN_OFF
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                screenReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                screenReceiver,
                filter
            )
        }

        screenReceiverRegistered = true
    }

    companion object {
        private const val SYSTEM_UI_PACKAGE =
            "com.android.systemui"
        private const val FOREGROUND_EVENT_LOOKBACK_MS =
            15_000L
    }

    private fun prefs() = getSharedPreferences(
        "triggers",
        Context.MODE_PRIVATE
    )

    private fun getAction(key: String): String {
        return prefs().getString(
            key,
            "NONE"
        ) ?: "NONE"
    }

    private fun submitRootAction(action: () -> Unit) {
        runCatching {
            rootExecutor.execute(action)
        }
    }

    private fun performAction(action: String) {
        val command = when (action) {
            "VOL_UP" -> "input keyevent 24"
            "VOL_DOWN" -> "input keyevent 25"
            "MEDIA_PLAY_PAUSE" -> "input keyevent 85"
            "MEDIA_NEXT" -> "input keyevent 87"
            "MEDIA_PREVIOUS" -> "input keyevent 88"
            else -> return
        }

        submitRootAction {
            HapticFeedback.pulse(
                this,
                HapticFeedback.Event.TRIGGER
            )
            RootShell.exec(command)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        /*
         * Native TGK consumes F7/F8 inside InputManager and creates
         * the mapped touch contacts. Never perform or consume the
         * legacy quick action while that native path is active.
         */
        if (
            NativeTgkRuntimeState.isActive() ||
            NativeTgkEditorRuntime.isEditing()
        ) {
            return false
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_F7 -> {
                performAction(
                    getAction("left_trigger")
                )
                true
            }

            KeyEvent.KEYCODE_F8 -> {
                performAction(
                    getAction("right_trigger")
                )
                true
            }

            else -> false
        }
    }
}
