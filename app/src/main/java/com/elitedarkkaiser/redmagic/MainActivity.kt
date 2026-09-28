package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

class MainActivity : Activity() {
    private val backgroundReleaseHandler =
        Handler(Looper.getMainLooper())

    private val releaseUiForGameplay = Runnable {
        if (
            !isFinishing &&
            !isDestroyed &&
            (
                NativeTgkRuntimeState.isActive() ||
                    RefreshRateCoordinator.isActive() ||
                    PerformanceModeCoordinator.isActive()
                )
        ) {
            /*
             * The accessibility/gameplay runtime shares this process.
             * Do not retain the complete five-tab activity hierarchy
             * behind a memory-intensive game after its profile becomes
             * active. Reopening the launcher activity reconstructs it.
             */
            finishAndRemoveTask()
        }
    }

    private var useFahrenheit = true
    private var uiLaunched = false
    private var deviceCapabilities = DeviceCapabilities.unknown()

    private lateinit var activityRuntime: MainActivityRuntime
    private lateinit var tabHost: MainTabHost

    private val uiKit: MainActivityUiKit by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainActivityUiKit(this)
    }

    private val ledViews: LedControlViewFactory by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        LedControlViewFactory(this)
    }

    private val deviceGateActions: MainDeviceGateActions by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainDeviceGateActions(this, uiKit)
    }

    private val gameModeActions: MainGameModeActions by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainGameModeActions(this, uiKit, ledViews)
    }

    private val homeController: MainHomeController by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainHomeController(
            activity = this,
            uiKit = uiKit,
            requestStatusRefresh = ::refreshStatus,
            gameModeActions = gameModeActions
        )
    }

    private val coolingController: MainCoolingTabActions by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainCoolingTabActions(
            activity = this,
            uiKit = uiKit,
            runBackground = ::submitBackgroundTask,
            refreshStatus = ::refreshStatus,
            capabilities = { deviceCapabilities },
            useFahrenheit = { useFahrenheit }
        )
    }

    private val controlsController: MainControlsController by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainControlsController(
            activity = this,
            uiKit = uiKit,
            runBackground = ::submitBackgroundTask,
            refreshStatus = ::refreshStatus,
            capabilities = { deviceCapabilities }
        )
    }

    private val hardwareActions: MainHardwareTabActions by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainHardwareTabActions(
            activity = this,
            runBackground = ::submitBackgroundTask,
            onMasterProfileApplied = ::applyMasterProfileToUiState
        )
    }

    private val hardwareController: MainHardwareController by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainHardwareController(
            activity = this,
            uiKit = uiKit,
            actions = hardwareActions,
            runBackground = ::submitBackgroundTask,
            refreshStatus = ::refreshStatus,
            capabilities = { deviceCapabilities }
        )
    }

    private val lightingActions: MainLightingTabActions by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainLightingTabActions(
            activity = this,
            runBackground = ::submitBackgroundTask,
            dp = { value -> uiKit.dp(value) },
            filterChip = { label, selected, onClick ->
                ledViews.filterChip(label, selected, onClick)
            },
            updateSelectableButton = { button, selected ->
                uiKit.updateSelectableButton(button, selected)
            }
        )
    }

    private val lightingController: MainLightingController by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainLightingController(
            activity = this,
            uiKit = uiKit,
            ledViews = ledViews,
            actions = lightingActions,
            runBackground = ::submitBackgroundTask,
            capabilities = { deviceCapabilities },
            showGameModeAppPicker = {
                gameModeActions.showAppPicker()
            },
            showGameModeProfileDialog = {
                gameModeActions.showProfileDialog()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.configure(this)

        if (!DeviceCompatibility.isSupportedDevice()) {
            deviceGateActions.showUnsupportedDevice()
            return
        }

        activityRuntime = MainActivityRuntime(
            activity = this,
            onStatusSnapshot = ::applyStatusSnapshot
        )

        initDefaultTriggerMappingsStorage(this)
        deviceCapabilities = deviceCapabilitiesStorage(this)

        val needsFirstInstallSetup =
            !isFirstInstallPermissionsPromptedStorage(this) ||
                !PermissionActions.hasUsageStatsPermission(this)

        if (needsFirstInstallSetup) {
            FirstInstallPermissionsDialog.show(this) {
                setCachedRootAccessStorage(this, true)
                launchMainUi()
            }
        } else {
            verifyRootAndLaunch()
        }
    }

    @Deprecated("Legacy result API retained for Android 9 compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            NativeTgkDocumentTransfer.handleActivityResult(
                activity = this,
                requestCode = requestCode,
                resultCode = resultCode,
                data = data,
                runBackground = ::submitBackgroundTask
            )
        ) {
            return
        }

        MasterProfileDocumentTransfer.handleActivityResult(
            activity = this,
            requestCode = requestCode,
            resultCode = resultCode,
            data = data,
            runBackground = ::submitBackgroundTask
        )
    }

    override fun onStart() {
        super.onStart()
        backgroundReleaseHandler.removeCallbacks(
            releaseUiForGameplay
        )
        if (::activityRuntime.isInitialized) {
            activityRuntime.onStart()
        }
    }

    override fun onResume() {
        super.onResume()

        val savedUnit = isUseFahrenheitStorage(this)
        if (savedUnit != useFahrenheit) {
            useFahrenheit = savedUnit
            if (uiLaunched) refreshStatus()
        }
    }

    override fun onStop() {
        if (::activityRuntime.isInitialized) {
            activityRuntime.onStop()
        }
        if (!isChangingConfigurations) {
            backgroundReleaseHandler.removeCallbacks(
                releaseUiForGameplay
            )
            backgroundReleaseHandler.postDelayed(
                releaseUiForGameplay,
                GAMEPLAY_UI_RELEASE_DELAY_MS
            )
        }
        super.onStop()
    }

    override fun onDestroy() {
        backgroundReleaseHandler.removeCallbacksAndMessages(null)
        if (::activityRuntime.isInitialized) {
            activityRuntime.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val GAMEPLAY_UI_RELEASE_DELAY_MS = 5_000L
    }

    private fun verifyRootAndLaunch() {
        if (hasCachedRootAccessStorage(this)) {
            launchMainUi()
            return
        }

        val submitted = activityRuntime.verifyRoot { rooted ->
            if (rooted) {
                setCachedRootAccessStorage(this, true)
                launchMainUi()
            } else {
                deviceGateActions.showRootRequired()
            }
        }

        if (!submitted && !isFinishing && !isDestroyed) {
            deviceGateActions.showRootRequired()
        }
    }

    private fun launchMainUi() {
        if (uiLaunched || isFinishing || isDestroyed) return

        lightingController.loadSavedState()
        coolingController.loadSavedPumpState()
        useFahrenheit = isUseFahrenheitStorage(this)

        tabHost = MainTabHost(
            activity = this,
            topInset = uiKit.getStatusBarHeight(),
            backgroundColor = AppTheme.bgColor,
            dp = { value -> uiKit.dp(value) },
            createHome = { homeController.createView() },
            createCooling = { coolingController.createView() },
            createControls = { controlsController.createView() },
            createHardware = { hardwareController.createView() },
            createLighting = { lightingController.createView() }
        )
        tabHost.launch()
        uiLaunched = true

        coolingController.startAutoPumpIfEnabled()
        lightingController.startRgbStudioIfEnabled()
        startTriggerAutoStartIfEnabled()
        activityRuntime.onUiReady()
        startCapabilityScan()
    }

    private fun startCapabilityScan() {
        DeviceScanActions.runBackgroundScan(this) { capabilities ->
            deviceCapabilities = capabilities

            runOnUiThread {
                if (
                    uiLaunched &&
                    ::tabHost.isInitialized &&
                    !isFinishing &&
                    !isDestroyed
                ) {
                    tabHost.refreshBuiltTabs()
                }
            }
        }
    }

    private fun startTriggerAutoStartIfEnabled() {
        if (!readTriggerPrefsSnapshot(this).triggersAutoStart) return

        submitBackgroundTask {
            HardwareServiceActions.startTriggersIfAutoStartEnabled(this)
            refreshStatus()
        }
    }

    private fun applyMasterProfileToUiState(profile: MasterProfile) {
        coolingController.applyMasterProfile(profile.hardware)
        lightingController.applyMasterProfile(profile)
        useFahrenheit = profile.useFahrenheit
        refreshStatus()
    }

    private fun applyStatusSnapshot(snapshot: MainStatusSnapshot) {
        coolingController.applyTemperature(
            snapshot.telemetry.temperatureF
        )
        homeController.applyStatusSnapshot(
            snapshot,
            useFahrenheit
        )
    }

    private fun refreshStatus() {
        if (::activityRuntime.isInitialized) {
            activityRuntime.requestStatusRefresh()
        }
    }

    private fun submitBackgroundTask(task: () -> Unit): Boolean {
        return if (::activityRuntime.isInitialized) {
            activityRuntime.submit(task)
        } else {
            false
        }
    }
}
