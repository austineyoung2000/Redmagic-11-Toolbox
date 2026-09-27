package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.CoolingTabDeps
import com.elitedarkkaiser.redmagic.ui.CoolingTabUi
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/**
 * Owns Cooling-tab state, view references, and hardware actions.
 *
 * The tab is built lazily and may be rebuilt after a capability scan, so all
 * rendering methods tolerate the view not existing yet and always target the
 * newest set of references.
 */
internal class MainCoolingTabActions(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit,
    private val runBackground: (() -> Unit) -> Boolean,
    private val refreshStatus: () -> Unit,
    private val capabilities: () -> DeviceCapabilities,
    private val useFahrenheit: () -> Boolean
) {
    var selectedCurve = "balanced"
        private set

    var autoFanCurveEnabled = false
        private set

    var pumpEnabled = false
        private set

    var pumpProfile = "quick"
        private set

    var autoPumpEnabled = false
        private set

    private var refs: CoolingTabUi.Refs? = null
    private var lastDisplayedTempF: Float? = null

    fun loadSavedPumpState() {
        val state = savedPumpStateStorage(activity)
        pumpEnabled = state.enabled
        pumpProfile = state.profile
        autoPumpEnabled = state.autoEnabled
    }

    fun setAutoPumpEnabled(enabled: Boolean) {
        autoPumpEnabled = enabled
    }

    fun applyMasterProfile(hardware: HardwareSettingsSnapshot) {
        autoFanCurveEnabled = hardware.autoFanEnabled
        selectedCurve = hardware.fanCurveMode
        pumpEnabled = hardware.pumpEnabled
        pumpProfile = hardware.pumpProfile
        autoPumpEnabled = hardware.autoPumpEnabled

        refs?.fanSeek?.value = hardware.fanLevel.toFloat()
        renderFanCurveState()
        refreshSmartPumpStatusViews()
    }

    fun startAutoPumpIfEnabled() {
        if (autoPumpEnabled) {
            HardwareServiceActions.startAutoPump(activity)
        }
    }

    fun createView(): LinearLayout {
        selectedCurve = selectedCurveStorage(activity)
        autoFanCurveEnabled = isAutoFanEnabledStorage(activity)

        val result = CoolingTabUi.create(createDependencies())
        refs = result.refs

        renderFanCurveState()
        refreshSmartPumpStatusViews()
        renderTemperature(lastDisplayedTempF, "")

        return result.view
    }

    fun applyTemperature(temperatureF: Float?) {
        val previous = lastDisplayedTempF
        val trend = when {
            temperatureF == null || previous == null -> ""
            temperatureF > previous + 1f -> " ↑"
            temperatureF < previous - 1f -> " ↓"
            else -> " →"
        }

        if (temperatureF != null) {
            lastDisplayedTempF = temperatureF
        }

        refreshSmartPumpStatusViews()
        renderTemperature(temperatureF, trend)
    }

    private fun createDependencies(): CoolingTabDeps {
        return CoolingTabDeps(
            scrollTabContainer = { uiKit.scrollTabContainer() },
            sectionPanel = { uiKit.sectionPanel() },
            sectionHeader = { icon, text ->
                uiKit.sectionHeader(icon, text)
            },
            subtleLabel = { text -> uiKit.subtleLabel(text) },
            bodyText = { text -> uiKit.bodyText(text) },
            segmentedChip = { label, selected, onClick ->
                uiKit.segmentedChip(label, selected, onClick)
            },
            updateSelectableButton = { button, selected ->
                uiKit.updateSelectableButton(button, selected)
            },
            actionButton = { text, danger, onClick ->
                uiKit.actionButton(text, danger, onClick)
            },
            row = { left, right -> uiKit.row(left, right) },
            singleRow = { button -> uiKit.singleRow(button) },
            space = { width -> uiKit.space(width) },
            spacer = { height -> uiKit.spacer(height) },
            dp = { value -> uiKit.dp(value) },
            roundedBg = { fill, stroke, radius ->
                uiKit.roundedBg(fill, stroke, radius)
            },
            runBackground = runBackground,
            capabilities = capabilities(),
            getSelectedCurve = { selectedCurve },
            setSelectedCurve = { selectedCurve = it },
            setSelectedCurveSaved = {
                saveSelectedCurveStorage(activity, it)
            },
            getAutoFanCurveEnabled = { autoFanCurveEnabled },
            setAutoFanCurveEnabled = {
                autoFanCurveEnabled = it
            },
            setAutoFanEnabledSaved = {
                saveAutoFanEnabledStorage(activity, it)
            },
            getPumpEnabled = { pumpEnabled },
            setPumpEnabled = { pumpEnabled = it },
            getPumpProfile = { pumpProfile },
            setPumpProfileValue = { pumpProfile = it },
            getAutoPumpEnabled = { autoPumpEnabled },
            setAutoPumpEnabled = { autoPumpEnabled = it },
            startAutoFanService = {
                HardwareServiceActions.startAutoFan(activity)
            },
            stopAutoFanService = {
                HardwareServiceActions.stopAutoFan(activity)
            },
            startAutoPumpService = {
                HardwareServiceActions.startAutoPump(activity)
            },
            stopAutoPumpService = {
                HardwareServiceActions.stopAutoPump(activity)
            },
            savePumpState = {
                savePumpStateStorage(
                    activity,
                    pumpEnabled,
                    pumpProfile
                )
            },
            saveAutoPumpState = {
                saveAutoPumpStateStorage(
                    activity,
                    autoPumpEnabled
                )
            },
            refreshStatus = refreshStatus,
            refreshSmartPumpStatusViews = {
                refreshSmartPumpStatusViews()
            },
            buildAutoPumpStatusText = {
                buildAutoPumpStatusText()
            },
            applyPumpProfile = { profile ->
                applyPumpProfile(profile)
            },
            confirmExperimentalPumpThenApply = { onApplied ->
                confirmExperimentalPumpThenApply(onApplied)
            },
            updateManualCurveUiState = {
                updateManualCurveUiState()
            }
        )
    }

    private fun renderFanCurveState() {
        val currentRefs = refs ?: return
        val currentCapabilities = capabilities()
        val fanAvailable =
            !currentCapabilities.scanComplete ||
                currentCapabilities.fanAvailable

        if (fanAvailable) {
            currentRefs.autoCurveCheck.isChecked =
                autoFanCurveEnabled
        }

        currentRefs.curveStatusText.text = when {
            !fanAvailable ->
                "Fan controls unavailable on this ROM"
            autoFanCurveEnabled ->
                "Auto fan curve active • Running in background service"
            else ->
                "Selected curve: $selectedCurve • Manual control"
        }

        uiKit.updateSelectableButton(
            currentRefs.quietCurveButton,
            selectedCurve == "quiet"
        )
        uiKit.updateSelectableButton(
            currentRefs.balancedCurveButton,
            selectedCurve == "balanced"
        )
        uiKit.updateSelectableButton(
            currentRefs.turboCurveButton,
            selectedCurve == "turbo"
        )

        updateManualCurveUiState()
    }

    private fun updateManualCurveUiState() {
        val currentRefs = refs ?: return
        val currentCapabilities = capabilities()
        val fanAvailable =
            !currentCapabilities.scanComplete ||
                currentCapabilities.fanAvailable
        val manualEnabled =
            fanAvailable && !autoFanCurveEnabled
        val alpha = if (manualEnabled) 1f else 0.40f

        listOf(
            currentRefs.quietCurveButton,
            currentRefs.balancedCurveButton,
            currentRefs.turboCurveButton
        ).forEach { button ->
            button.alpha = alpha
            button.isEnabled = manualEnabled
            button.isClickable = manualEnabled
        }
    }

    private fun buildAutoPumpStatusText(): Pair<String, String> {
        val temperatureF = lastDisplayedTempF
            ?: return "Pump Mode: AUTO • Unknown temp" to
                "Speed: ? • Freq: ?"

        val profile = when {
            temperatureF >= 105f -> "Quick"
            temperatureF >= 90f -> "Medium"
            else -> "Slow"
        }
        val speed = when (profile) {
            "Quick" -> 80
            "Medium" -> 60
            else -> 40
        }
        val temperature = TempFormat.formatDisplayTempFromF(
            temperatureF,
            useFahrenheit()
        )

        return "Pump Mode: AUTO • $profile ($temperature)" to
            "Speed: $speed • Freq: 4"
    }

    private fun refreshSmartPumpStatusViews() {
        val currentRefs = refs ?: return

        if (autoPumpEnabled) {
            val status = buildAutoPumpStatusText()
            currentRefs.smartPumpStatusView.text = status.first
            currentRefs.smartPumpSpeedView.text = status.second
            return
        }

        if (!pumpEnabled) {
            currentRefs.smartPumpStatusView.text = "Pump Mode: OFF"
            currentRefs.smartPumpSpeedView.text = "Speed: 0 • Freq: 0"
            return
        }

        val manualLabel = pumpProfile.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        val manualSpeed = when (pumpProfile.lowercase()) {
            "slow" -> 40
            "medium" -> 60
            "quick" -> 80
            "experimental" -> 90
            else -> 60
        }

        currentRefs.smartPumpStatusView.text =
            "Pump Mode: MANUAL • $manualLabel"
        currentRefs.smartPumpSpeedView.text =
            "Speed: $manualSpeed • Freq: 4"
    }

    private fun renderTemperature(
        temperatureF: Float?,
        trend: String
    ) {
        refs?.tempText?.text = if (temperatureF != null) {
            "Current temp: ${
                TempFormat.formatDisplayTempFromF(
                    temperatureF,
                    useFahrenheit()
                )
            }$trend"
        } else {
            "Current temp: --"
        }
    }

    private fun applyPumpProfile(profile: String) {
        pumpProfile = profile
        pumpEnabled = true
        autoPumpEnabled = false
        savePumpStateStorage(activity, pumpEnabled, pumpProfile)
        saveAutoPumpStateStorage(activity, autoPumpEnabled)
        refreshSmartPumpStatusViews()

        runBackground {
            HardwareServiceActions.stopAutoPump(activity)
            HardwareController.setPumpProfile(profile)
            refreshStatus()
        }
    }

    private fun confirmExperimentalPumpThenApply(
        onApplied: () -> Unit = {}
    ) {
        if (savedPumpStateStorage(activity).experimentalAccepted) {
            applyPumpProfile("experimental")
            onApplied()
            return
        }

        ExperimentalPumpDialog.show(
            activity = activity,
            onCancel = {},
            onConfirm = {
                setPumpExperimentalAcceptedStorage(activity, true)
                applyPumpProfile("experimental")
                onApplied()
            },
            deps = ExperimentalPumpDialog.Deps(
                textPrimary = AppTheme.textPrimary,
                textSecondary = AppTheme.textSecondary,
                panelColor = AppTheme.panelColor,
                borderColor = AppTheme.borderColor,
                panelPressed = AppTheme.panelPressed,
                typeface = Typeface.SANS_SERIF,
                dp = { value -> uiKit.dp(value) },
                roundedBg = { fill, stroke, radius ->
                    uiKit.roundedBg(fill, stroke, radius)
                },
                roundedFill = { color, radius ->
                    uiKit.roundedFill(color, radius)
                },
                space = { value -> uiKit.space(value) }
            )
        )
    }
}
