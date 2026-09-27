package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.HardwareTabDeps
import com.elitedarkkaiser.redmagic.ui.HardwareTabUi
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/** Owns Hardware-tab composition and delegates hardware work off the UI. */
internal class MainHardwareController(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit,
    private val actions: MainHardwareTabActions,
    private val runBackground: (() -> Unit) -> Boolean,
    private val refreshStatus: () -> Unit,
    private val capabilities: () -> DeviceCapabilities
) {
    fun createView(): LinearLayout {
        return HardwareTabUi.create(
            activity,
            createDependencies()
        )
    }

    private fun createDependencies(): HardwareTabDeps {
        return HardwareTabDeps(
            scrollTabContainer = { uiKit.scrollTabContainer() },
            sectionPanel = { uiKit.sectionPanel() },
            sectionHeader = { icon, text ->
                uiKit.sectionHeader(icon, text)
            },
            bodyText = { text -> uiKit.bodyText(text) },
            subtleLabel = { text -> uiKit.subtleLabel(text) },
            actionButton = { text, danger, onClick ->
                uiKit.actionButton(text, danger, onClick)
            },
            singleRow = { button -> uiKit.singleRow(button) },
            row = { left, right -> uiKit.row(left, right) },
            space = { width -> uiKit.space(width) },
            dp = { value -> uiKit.dp(value) },
            capabilities = capabilities(),
            showTriggerSetupDialog = { showTriggerSetupDialog() },
            showNativeTgkProfileDialog = {
                NativeTgkProfileDialog.show(activity)
            },
            showNativeTgkDiagnosticsDialog = {
                NativeTgkDiagnosticsDialog.show(activity)
            },
            triggerSafetySummary = {
                triggerSafetySummaryStorage(activity)
            },
            showTriggerSafetyDialog = { onSaved ->
                showTriggerSafetyDialog(onSaved)
            },
            enableTriggersAndService = { onComplete ->
                enableTriggers(onComplete)
            },
            disableTriggersAndService = { onComplete ->
                disableTriggers(onComplete)
            },
            triggersAutoStartEnabled = {
                readTriggerPrefsSnapshot(activity).triggersAutoStart
            },
            setTriggersAutoStartEnabled = { enabled ->
                setTriggersAutoStartStorage(activity, enabled)
            },
            readChargeSeparation = { onComplete ->
                actions.readChargeSeparation(onComplete)
            },
            setChargeSeparation = { enabled, onComplete ->
                actions.setChargeSeparation(enabled, onComplete)
            },
            showRefreshRateProfiles = {
                RefreshRateProfileDialog.show(activity)
            },
            showTouchTuningProfiles = {
                TouchTuningProfileDialog.show(activity)
            },
            showPerformanceModeProfiles = {
                PerformanceModeProfileDialog.show(activity)
            },
            testHapticStrength = { strength ->
                runBackground {
                    HapticFeedback.testPulse(strength)
                }
            },
            loadMasterProfiles = { actions.loadMasterProfiles() },
            saveMasterProfile = { name, onComplete ->
                actions.saveMasterProfile(name, onComplete)
            },
            applyMasterProfile = { profile ->
                actions.applyMasterProfile(profile)
            },
            deleteMasterProfile = { name ->
                actions.deleteMasterProfile(name)
            },
            exportMasterBackup = {
                actions.requestMasterBackupExport()
            },
            importMasterBackup = {
                actions.requestMasterBackupImport()
            },
            automationRulesSummary = {
                AutomationRulesStorage.summary(activity)
            },
            showAutomationRulesDialog = { onSaved ->
                AutomationRulesDialog.show(activity, onSaved)
            }
        )
    }

    private fun enableTriggers(onComplete: (Boolean) -> Unit) {
        val submitted = runBackground {
            val enabled =
                HardwareServiceActions.enableTriggersManually(activity)

            if (enabled) refreshStatus()
            postResult(enabled, onComplete)
        }

        if (!submitted) onComplete(false)
    }

    private fun disableTriggers(onComplete: (Boolean) -> Unit) {
        val submitted = runBackground {
            val disabled =
                HardwareServiceActions.disableTriggersUntilRestart(
                    activity
                )

            refreshStatus()
            postResult(disabled, onComplete)
        }

        if (!submitted) onComplete(false)
    }

    private fun postResult(
        result: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                onComplete(result)
            }
        }
    }

    private fun showTriggerSetupDialog() {
        TriggerSetupDialog.show(
            activity = activity,
            deps = TriggerSetupDialog.Deps(
                textPrimary = AppTheme.textPrimary,
                textSecondary = AppTheme.textSecondary,
                panelColor = AppTheme.panelColor,
                borderColor = AppTheme.borderColor,
                panelPressed = AppTheme.panelPressed,
                accent = AppTheme.accentColor,
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

    private fun showTriggerSafetyDialog(onSaved: () -> Unit) {
        TriggerSafetyDialog.show(
            activity = activity,
            deps = TriggerSafetyDialog.Deps(
                textPrimary = AppTheme.textPrimary,
                textSecondary = AppTheme.textSecondary,
                panelColor = AppTheme.panelColor,
                borderColor = AppTheme.borderColor,
                panelPressed = AppTheme.panelPressed,
                accent = AppTheme.accentColor,
                typeface = Typeface.SANS_SERIF,
                dp = { value -> uiKit.dp(value) },
                roundedBg = { fill, stroke, radius ->
                    uiKit.roundedBg(fill, stroke, radius)
                },
                space = { value -> uiKit.space(value) }
            ),
            onSaved = onSaved
        )
    }
}
