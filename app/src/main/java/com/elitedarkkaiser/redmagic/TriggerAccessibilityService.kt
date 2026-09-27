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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class TriggerAccessibilityService : AccessibilityService() {

    private val foregroundHandler = Handler(Looper.getMainLooper())

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

    /*
     * Foreground transitions are serialized on nativeTgkExecutor.
     * A generation invalidates work that has not started without
     * interrupting an operation already talking to system_server.
     * Interrupting the vendor setup delay can leave its enable state
     * only partially applied, so completed operations are followed by
     * the newest queued transition instead.
     */
    private val nativeTgkGeneration = AtomicLong(0L)

    @Volatile
    private var nativeHealthCheckInFlight = false

    @Volatile
    private var lastNativeHealthCheckAt = 0L

    private var screenReceiverRegistered = false

    @Volatile
    private var foregroundRootProcess: Process? = null

    @Volatile
    private var foregroundRootReader: Thread? = null

    @Volatile
    private var forceForegroundReconcile = false

    @Volatile
    private var nativeTgkStartupCleanupComplete = false

    private val foregroundPackagePattern = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )

    private val foregroundMonitor = object : Runnable {
        override fun run() {
            if (
                nativeTgkStartupCleanupComplete &&
                isScreenInteractive()
            ) {
                ensureForegroundRootMonitor()

                if (foregroundRootProcess?.isAlive != true) {
                    latestResumedPackage()?.let { packageName ->
                        reconcileDetectedPackage(packageName)
                    }
                }
            } else {
                stopForegroundRootMonitor()
            }

            foregroundHandler.postDelayed(
                this,
                FOREGROUND_MONITOR_INTERVAL_MS
            )
        }
    }

    @Volatile
    private var lastForegroundPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                lastForegroundPackage = null
                forceForegroundReconcile = false
                stopForegroundRootMonitor()
                deactivateNativeTgk("screen off")
                RefreshRateOverlay.hide()
                RefreshRateCoordinator.clearRuntimeState()
                dispatchPerformanceReset("screen off")
            } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                forceForegroundReconcile = true
                if (nativeTgkStartupCleanupComplete) {
                    ensureForegroundRootMonitor()
                }
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

        if (!nativeTgkStartupCleanupComplete) {
            return
        }

        ensureForegroundRootMonitor()

        /*
         * When available, topResumedActivity is the authoritative
         * source on REDMAGIC firmware. Do not race it with delayed or
         * missing Usage Events.
         */
        if (foregroundRootProcess?.isAlive == true) {
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
        val pkg = latestResumedPackage()
            ?: if (
                reportedPackage == packageName ||
                reportedPackage == SYSTEM_UI_PACKAGE
            ) {
                return
            } else {
                reportedPackage
            }

        reconcileDetectedPackage(pkg)
    }

    private fun reconcileDetectedPackage(
        packageName: String
    ) {
        val orientation =
            NativeTgkCoordinator.currentOrientation(this)
        val mappingNeedsApply =
            !NativeTgkEditorRuntime.isEditing() &&
                NativeTgkCoordinator.hasReadyMapping(
                    context = this,
                    packageName = packageName,
                    orientation = orientation
                ) &&
                (
                    !NativeTgkRuntimeState.matches(
                        packageName,
                        orientation
                    ) ||
                        overlayNeedsRecovery(
                            packageName,
                            orientation
                        )
                    )
        val nativeHealthCheckDue =
            !NativeTgkEditorRuntime.isEditing() &&
                NativeTgkRuntimeState.matches(
                    packageName,
                    orientation
                ) &&
                isNativeHealthCheckDue()
        val editorNeedsStop =
            NativeTgkEditorRuntime
                .shouldStopForForeground(packageName)
        val performanceNeedsApply =
            PerformanceModeCoordinator.needsReconcile(
                this,
                packageName
            )
        val force = forceForegroundReconcile

        if (
            force ||
            packageName != lastForegroundPackage ||
            mappingNeedsApply ||
            nativeHealthCheckDue ||
            editorNeedsStop ||
            performanceNeedsApply
        ) {
            forceForegroundReconcile = false
            handleForegroundPackage(packageName)
        }
    }

    private fun handleForegroundPackage(pkg: String) {
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

        /* Reconcile against the next authoritative root sample. */
        forceForegroundReconcile = true
        ensureForegroundRootMonitor()
    }

    override fun onInterrupt() {
        RefreshRateOverlay.hide()
        RefreshRateCoordinator.clearRuntimeState()
        dispatchPerformanceReset(
            "accessibility service interrupted"
        )
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
        foregroundHandler.removeCallbacks(foregroundMonitor)
        nativeTgkStartupCleanupComplete = false

        /*
         * Repair any native TGK state left behind by an earlier
         * process termination before accepting new foreground
         * application events.
         */
        NativeTgkRuntimeState.clear()
        RefreshRateOverlay.hide()
        RefreshRateCoordinator.clearRuntimeState()
        runCatching {
            refreshRateExecutor.execute {
                PerformanceModeCoordinator
                    .recoverAndResetIfManaged(
                        applicationContext,
                        "accessibility service connected"
                    )
            }
        }
        val startupGeneration =
            nativeTgkGeneration.incrementAndGet()

        nativeTgkExecutor.submit {
            NativeTgkCoordinator.disable(
                applicationContext,
                "accessibility service connected"
            )

            foregroundHandler.post {
                if (
                    nativeTgkExecutor.isShutdown ||
                    nativeTgkGeneration.get() !=
                    startupGeneration
                ) {
                    return@post
                }

                nativeTgkStartupCleanupComplete = true
                forceForegroundReconcile = true
                ensureForegroundRootMonitor()
                foregroundHandler.removeCallbacks(
                    foregroundMonitor
                )
                foregroundHandler.post(foregroundMonitor)
            }
        }

        submitRootAction {
            HardwareServiceActions
                .startTriggersIfAutoStartEnabled(this)
        }
    }

    override fun onDestroy() {
        lastForegroundPackage = null
        forceForegroundReconcile = false
        nativeTgkStartupCleanupComplete = false
        stopForegroundRootMonitor()
        foregroundHandler.removeCallbacksAndMessages(null)
        NativeTgkRuntimeState.clear()
        RefreshRateOverlay.hide()
        RefreshRateCoordinator.clearRuntimeState()
        val resetPerformanceMode =
            PerformanceModeCoordinator.isActive()

        val cleanupGeneration =
            nativeTgkGeneration.incrementAndGet()

        if (screenReceiverRegistered) {
            runCatching {
                unregisterReceiver(screenReceiver)
            }
            screenReceiverRegistered = false
        }

        runCatching {
            nativeTgkExecutor.submit {
                if (
                    nativeTgkGeneration.get() ==
                    cleanupGeneration
                ) {
                    NativeTgkCoordinator.disable(
                        applicationContext,
                        "accessibility service destroyed"
                    )
                }
            }
        }
        nativeTgkExecutor.shutdown()
        refreshRateExecutor.shutdownNow()

        if (resetPerformanceMode) {
            Thread(
                {
                    PerformanceModeCoordinator.resetIfOwned(
                        applicationContext,
                        "accessibility service destroyed"
                    )
                },
                "RedMagicPerformanceCleanup"
            ).start()
        }

        rootExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun dispatchNativeTgkForForeground(
        packageName: String
    ) {
        if (NativeTgkEditorRuntime.isEditing()) {
            if (
                NativeTgkEditorRuntime
                    .shouldStopForForeground(packageName)
            ) {
                stopService(
                    Intent(
                        this,
                        NativeTgkEditorService::class.java
                    )
                )
            }
            invalidateNativeTgkWorkForEditor()
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
            /* Always remove any stale target window, even if the
             * in-memory TGK state was already cleared. */
            deactivateNativeTgk(
                "left mapped app or orientation"
            )
            return
        }

        if (
            NativeTgkRuntimeState.matches(
                packageName,
                orientation
            )
        ) {
            if (
                overlayNeedsRecovery(
                    packageName,
                    orientation
                )
            ) {
                scheduleNativeTgkApply(
                    packageName,
                    orientation,
                    "gameplay overlay missing"
                )
            } else {
                scheduleNativeHealthCheck(
                    packageName,
                    orientation
                )
            }
            return
        }

        /*
         * Mark the native path active before the two-second vendor
         * setup delay so the legacy F7/F8 readers cannot perform
         * quick actions during the transition.
         */
        scheduleNativeTgkApply(
            packageName,
            orientation,
            "foreground mapping changed"
        )
    }

    private fun scheduleNativeTgkApply(
        packageName: String,
        orientation: NativeTgkOrientation,
        reason: String
    ) {
        NativeTgkRuntimeState.markActive(
            packageName,
            orientation
        )

        val generation =
            nativeTgkGeneration.incrementAndGet()
        lastNativeHealthCheckAt = SystemClock.elapsedRealtime()

        android.util.Log.i(
            "RedmagicForeground",
            "Queueing native mapping generation=$generation " +
                "package=$packageName orientation=$orientation " +
                "reason=$reason"
        )

        runCatching {
            nativeTgkExecutor.submit nativeApply@{
                if (
                    nativeTgkGeneration.get() != generation ||
                    !NativeTgkRuntimeState.matches(
                        packageName,
                        orientation
                    )
                ) {
                    return@nativeApply
                }

                val result =
                    NativeTgkCoordinator.applyForegroundMapping(
                        context = applicationContext,
                        packageName = packageName,
                        orientation = orientation
                    )

                if (
                    !result.success &&
                    nativeTgkGeneration.get() == generation
                ) {
                    NativeTgkRuntimeState.clearIfMatches(
                        packageName,
                        orientation
                    )
                }
            }
        }.onFailure {
            NativeTgkRuntimeState.clearIfMatches(
                packageName,
                orientation
            )
            NativeTgkGameplayOverlay.hide()
        }
    }

    private fun deactivateNativeTgk(reason: String) {
        NativeTgkRuntimeState.clear()
        NativeTgkGameplayOverlay.hide()

        val generation =
            nativeTgkGeneration.incrementAndGet()
        lastNativeHealthCheckAt = 0L

        runCatching {
            nativeTgkExecutor.submit {
                if (
                    nativeTgkGeneration.get() == generation
                ) {
                    NativeTgkCoordinator.disable(
                        applicationContext,
                        reason
                    )
                }
            }
        }
    }

    private fun invalidateNativeTgkWorkForEditor() {
        nativeTgkGeneration.incrementAndGet()
        lastNativeHealthCheckAt = 0L
        NativeTgkRuntimeState.clear()
        NativeTgkGameplayOverlay.hide()
    }

    private fun isNativeHealthCheckDue(): Boolean {
        return !nativeHealthCheckInFlight &&
            SystemClock.elapsedRealtime() -
            lastNativeHealthCheckAt >=
            NATIVE_HEALTH_CHECK_INTERVAL_MS
    }

    private fun scheduleNativeHealthCheck(
        packageName: String,
        orientation: NativeTgkOrientation
    ) {
        if (!isNativeHealthCheckDue()) {
            return
        }

        val generation = nativeTgkGeneration.get()
        lastNativeHealthCheckAt = SystemClock.elapsedRealtime()
        nativeHealthCheckInFlight = true

        runCatching {
            nativeTgkExecutor.submit nativeHealth@{
                try {
                    if (
                        nativeTgkGeneration.get() != generation ||
                        !NativeTgkRuntimeState.matches(
                            packageName,
                            orientation
                        )
                    ) {
                        return@nativeHealth
                    }

                    val liveState = NativeTgkBridge.readState(
                        applicationContext
                    )
                    val mappingEnabled =
                        liveState.success &&
                            liveState.state?.mappingEnabled() == true

                    if (!mappingEnabled && liveState.success) {
                        foregroundHandler.post {
                            if (
                                nativeTgkGeneration.get() ==
                                generation &&
                                NativeTgkRuntimeState.matches(
                                    packageName,
                                    orientation
                                )
                            ) {
                                scheduleNativeTgkApply(
                                    packageName,
                                    orientation,
                                    "live mapping state disabled"
                                )
                            }
                        }
                    }
                } finally {
                    nativeHealthCheckInFlight = false
                }
            }
        }.onFailure {
            nativeHealthCheckInFlight = false
        }
    }

    private fun overlayNeedsRecovery(
        packageName: String,
        orientation: NativeTgkOrientation
    ): Boolean {
        /*
         * Runtime ownership alone is insufficient: Android can remove
         * either overlay window while the system mapping remains active.
         * Treat detached windows as a broken foreground session so the
         * normal apply path restores the views and verifies the mapping.
         */
        return NativeTgkCoordinator.expectsGameplayOverlay(
            this,
            packageName,
            orientation
        ) && !NativeTgkGameplayOverlay.isVisibleFor(
            packageName,
            orientation
        )
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
                PerformanceModeCoordinator.onForegroundPackage(
                    applicationContext,
                    packageName
                )
                RefreshRateOverlay.refresh()
            }
        }
    }

    private fun dispatchPerformanceReset(reason: String) {
        runCatching {
            refreshRateExecutor.execute {
                PerformanceModeCoordinator.resetIfOwned(
                    applicationContext,
                    reason
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

    private fun isScreenInteractive(): Boolean {
        return getSystemService(PowerManager::class.java)
            ?.isInteractive == true
    }

    @Synchronized
    private fun ensureForegroundRootMonitor() {
        if (foregroundRootProcess?.isAlive == true) {
            return
        }

        if (!needsForegroundMonitoring()) {
            stopForegroundRootMonitor()
            return
        }

        val process = runCatching {
            ProcessBuilder(
                "su",
                "-c",
                ROOT_FOREGROUND_MONITOR_COMMAND
            ).redirectErrorStream(true).start()
        }.getOrElse {
            android.util.Log.e(
                "RedmagicForeground",
                "Unable to start authoritative foreground monitor",
                it
            )
            return
        }

        foregroundRootProcess = process

        val readerThread = Thread(
            {
                runCatching {
                    process.inputStream.bufferedReader().useLines {
                        lines ->
                        lines.forEach { rawLine ->
                            val detected = rawLine.trim()
                            if (
                                foregroundPackagePattern.matches(
                                    detected
                                )
                            ) {
                                foregroundHandler.post {
                                    if (
                                        foregroundRootProcess ===
                                        process &&
                                        isScreenInteractive()
                                    ) {
                                        reconcileDetectedPackage(
                                            detected
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                synchronized(this) {
                    if (foregroundRootProcess === process) {
                        foregroundRootProcess = null
                        foregroundRootReader = null
                    }
                }
            },
            "RedMagicForegroundReader"
        ).apply {
            priority = Thread.NORM_PRIORITY - 1
        }

        foregroundRootReader = readerThread
        readerThread.start()
    }

    @Synchronized
    private fun stopForegroundRootMonitor() {
        val process = foregroundRootProcess
        val reader = foregroundRootReader

        foregroundRootProcess = null
        foregroundRootReader = null

        reader?.interrupt()
        if (process != null) {
            runCatching { process.destroy() }
            if (process.isAlive) {
                runCatching { process.destroyForcibly() }
            }
        }
    }

    private fun needsForegroundMonitoring(): Boolean {
        return NativeTgkEditorRuntime.isEditing() ||
            NativeTgkStorage.enabledPackages(this).isNotEmpty() ||
            RefreshRateStorage.readProfiles(this).any {
                it.enabled || it.pendingReset
            } ||
            PerformanceModeStorage.readProfiles(this).any {
                it.enabled
            }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) {
            return
        }

        val filter = IntentFilter(
            Intent.ACTION_SCREEN_OFF
        ).apply {
            addAction(Intent.ACTION_SCREEN_ON)
        }

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
        private const val FOREGROUND_MONITOR_INTERVAL_MS =
            750L
        private const val NATIVE_HEALTH_CHECK_INTERVAL_MS =
            10_000L

        private val ROOT_FOREGROUND_MONITOR_COMMAND = """
            while true; do
                line="${'$'}(
                    dumpsys activity activities 2>/dev/null |
                    grep -m 1 'topResumedActivity='
                )"
                detected="${'$'}(
                    printf '%s\n' "${'$'}line" |
                    sed -n 's/.* u[0-9][0-9]* \([^/ ]*\)\/.*/\1/p'
                )"
                if [ -n "${'$'}detected" ]; then
                    printf '%s\n' "${'$'}detected"
                fi
                sleep 1
            done
        """.trimIndent()
    }

    private fun submitRootAction(action: () -> Unit) {
        runCatching {
            rootExecutor.execute(action)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        /*
         * TriggerRootService owns physical-trigger actions and applies the
         * complete saved safety policy. Handling F7/F8 here created a second,
         * unconditional action path that bypassed intent and hold settings.
         * Leave all keys unconsumed so only the authoritative runtime acts.
         */
        return false
    }
}
