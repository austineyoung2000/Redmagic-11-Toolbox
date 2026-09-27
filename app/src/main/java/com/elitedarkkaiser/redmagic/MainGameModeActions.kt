package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/** Shared Game Mode dialogs used by Home and Lighting without activity state. */
internal class MainGameModeActions(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit,
    private val ledViews: LedControlViewFactory
) {
    fun showAppPicker(onUpdated: () -> Unit = {}) {
        showGamePickerDialogUI(activity) {
            onUpdated()
        }
    }

    fun showProfileDialog() {
        GameModeUi.showGameModeProfileDialog(
            activity = activity,
            current = getSavedGameModeProfileStorage(activity),
            deps = GameModeUi.Deps(
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
                filterChip = { label, selected, onClick ->
                    ledViews.filterChip(label, selected, onClick)
                },
                space = { value -> uiKit.space(value) },
                colorDotDrawable = { hex, selected ->
                    ledViews.colorDotDrawable(hex, selected)
                },
                colorDotGeneric = { hex, selected, onClick ->
                    ledViews.colorDot(hex, selected, onClick)
                }
            ),
            onSaveProfile = { profile ->
                saveGameModeProfileStorage(activity, profile)
                GameModeActions.applySavedProfileThroughService(activity)
            }
        )
    }
}
