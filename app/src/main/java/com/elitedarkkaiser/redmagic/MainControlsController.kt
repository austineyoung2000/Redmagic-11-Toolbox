package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.ControlsTabDeps
import com.elitedarkkaiser.redmagic.ui.ControlsTabUi
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/** Owns Controls-tab composition, Magic Key dialogs, and current view refs. */
internal class MainControlsController(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit,
    private val runBackground: (() -> Unit) -> Boolean,
    private val refreshStatus: () -> Unit,
    private val capabilities: () -> DeviceCapabilities
) {
    private var magicKeyStatusLabel: TextView? = null

    fun createView(): LinearLayout {
        val result = ControlsTabUi.create(
            activity,
            createDependencies()
        )
        magicKeyStatusLabel = result.refs.magicKeyStatusLabel
        return result.view
    }

    private fun createDependencies(): ControlsTabDeps {
        return ControlsTabDeps(
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
            smallActionButton = { text, danger, onClick ->
                uiKit.smallActionButton(text, danger, onClick)
            },
            singleRow = { button -> uiKit.singleRow(button) },
            row = { left, right -> uiKit.row(left, right) },
            flowRow = { views -> uiKit.flowRow(*views) },
            space = { width -> uiKit.space(width) },
            spacer = { height -> uiKit.spacer(height) },
            dp = { value -> uiKit.dp(value) },
            runBackground = runBackground,
            capabilities = capabilities(),
            refreshStatus = refreshStatus,
            readMagicKeyModeLabel = {
                if (SliderDualAppStorage.read(activity).enabled) {
                    "Dual App Slider"
                } else {
                    MagicKeyActions.readModeLabel()
                }
            },
            applyStockMagicKeyMode = {
                    label, action, status, sliderButton, shortcutButton ->
                MagicKeyActions.applyStockMode(
                    activity = activity,
                    label = label,
                    applyMode = action,
                    statusLabel = status,
                    sliderButton = sliderButton,
                    shortcutButton = shortcutButton,
                    runBackground = runBackground,
                    refreshStatus = refreshStatus
                )
            },
            disableMagicKeyMode = {
                    status, sliderButton, shortcutButton ->
                MagicKeyActions.disableMode(
                    activity = activity,
                    statusLabel = status,
                    sliderButton = sliderButton,
                    shortcutButton = shortcutButton,
                    runBackground = runBackground,
                    refreshStatus = refreshStatus
                )
            },
            resolveMagicKeyAppLabel = { pkg ->
                MagicKeyActions.resolveAppLabel(activity, pkg)
            },
            savedMagicKeyAppPackage = {
                savedMagicKeyAppPackageStorage(activity)
            },
            showMagicKeyAppPicker = { button, shortcutButton ->
                showMagicKeyAppPicker(button, shortcutButton)
            },
            savedMagicKeyShortcut = {
                savedMagicKeyShortcutStorage(activity)?.label
            },
            showMagicKeyShortcutPicker = { button, appButton ->
                showMagicKeyShortcutPicker(button, appButton)
            },
            sliderDualAppSummary = {
                SliderDualAppStorage.summary(activity)
            },
            showSliderDualAppDialog = { button ->
                showSliderDualAppDialog(button)
            }
        )
    }

    private fun showMagicKeyAppPicker(
        targetButton: Button,
        shortcutButton: Button?
    ) {
        MagicKeyAppPickerDialog.show(
            activity = activity,
            targetButton = targetButton,
            statusLabel = magicKeyStatusLabel,
            applyLaunchAppMagicKeyMode = {
                    pkg, label, status, sliderButton ->
                MagicKeyActions.applyLaunchAppMode(
                    activity = activity,
                    pkg = pkg,
                    label = label,
                    statusLabel = status,
                    sliderButton = sliderButton,
                    shortcutButton = shortcutButton,
                    runBackground = runBackground,
                    refreshStatus = refreshStatus
                )
            },
            deps = pickerDependencies()
        )
    }

    private fun showSliderDualAppDialog(targetButton: Button) {
        val status = magicKeyStatusLabel ?: return

        SliderDualAppDialog.show(
            activity = activity,
            statusLabel = status,
            pickerDeps = pickerDependencies(),
            runBackground = runBackground,
            onSaved = {
                targetButton.text =
                    SliderDualAppStorage.summary(activity)
                refreshStatus()
            }
        )
    }

    private fun showMagicKeyShortcutPicker(
        targetButton: Button,
        appButton: Button?
    ) {
        MagicKeyShortcutDialog.show(
            activity = activity,
            targetButton = targetButton,
            appButton = appButton,
            statusLabel = magicKeyStatusLabel,
            pickerDeps = pickerDependencies(),
            runBackground = runBackground,
            refreshStatus = refreshStatus
        )
    }

    private fun pickerDependencies(): MagicKeyAppPickerDialog.Deps {
        return MagicKeyAppPickerDialog.Deps(
            textPrimary = AppTheme.textPrimary,
            textSecondary = AppTheme.textSecondary,
            panelColor = AppTheme.panelColor,
            borderColor = AppTheme.borderColor,
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
    }
}
