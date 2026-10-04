package com.elitedarkkaiser.redmagic

import android.view.View

/** Builds the repeated logo/shoulder LED controls in the Game Mode dialog. */
internal object GameModeLedZoneUi {
    fun create(
        activity: MainActivity,
        deps: GameModeUi.Deps,
        title: String,
        enableLabel: String,
        initialEnabled: Boolean,
        initialEffect: String,
        initialColor: Int,
        shoulderZone: Boolean,
        onEnabledChanged: (Boolean) -> Unit,
        onEffectChanged: (String) -> Unit,
        onColorChanged: (Int) -> Unit
    ): List<View> {
        val initial = com.elitedarkkaiser.redmagic.state.LedState(initialEnabled, initialEffect, initialColor)
        val changed: (com.elitedarkkaiser.redmagic.state.LedState) -> Unit = { state ->
            onEnabledChanged(state.enabled)
            onEffectChanged(state.effect)
            onColorChanged(state.color)
        }
        return if (shoulderZone) listOf(TriggerLedProfileUi.create(activity, title, enableLabel, initial,
            TriggerLedProfileUi.Deps(deps.textPrimary, deps.textSecondary, deps.accent,
                deps.panelPressed, deps.borderColor, deps.dp, deps.colorDotGeneric, deps.colorDotDrawable), changed))
        else listOf(LogoBarProfileUi.create(activity, initial, onChanged = changed))
    }
}
