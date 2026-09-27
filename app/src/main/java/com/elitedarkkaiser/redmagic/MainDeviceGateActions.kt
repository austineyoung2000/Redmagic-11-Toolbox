package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/** Builds the two startup gate dialogs without leaking theme wiring. */
internal class MainDeviceGateActions(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit
) {
    fun showRootRequired() {
        DeviceGateDialogs.showRootRequiredDialog(
            activity = activity,
            onClose = { activity.finish() },
            deps = dependencies()
        )
    }

    fun showUnsupportedDevice() {
        DeviceGateDialogs.showUnsupportedDeviceDialog(
            activity = activity,
            model = DeviceCompatibility.identity().detectedModel,
            onClose = { activity.finish() },
            deps = dependencies()
        )
    }

    private fun dependencies(): DeviceGateDialogs.Deps {
        return DeviceGateDialogs.Deps(
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
            }
        )
    }
}
