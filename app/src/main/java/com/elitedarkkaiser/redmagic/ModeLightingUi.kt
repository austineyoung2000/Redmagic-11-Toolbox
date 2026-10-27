package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.view.View
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.LightingTabDeps

/** Profile-specific editors reuse the normal lighting presentation and ownership paths. */
internal object ModeLightingUi {
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private data class Keys(val enabled: String, val effect: String, val color: String, val initialEffect: String, val initialColor: Int)

    fun charging(activity: Activity, deps: LightingTabDeps): List<View> {
        val fan = Keys(ChargingLedState.FAN_ENABLED_KEY, ChargingLedState.FAN_EFFECT_KEY, ChargingLedState.FAN_COLOR_KEY, "steady", 5)
        val logo = Keys(ChargingLedState.LOGO_ENABLED_KEY, ChargingLedState.LOGO_EFFECT_KEY, ChargingLedState.LOGO_COLOR_KEY, "steady", 1)
        val trigger = Keys(ChargingLedState.SHOULDER_ENABLED_KEY, ChargingLedState.SHOULDER_EFFECT_KEY, ChargingLedState.SHOULDER_COLOR_KEY, "breathe", 8)
        fun read(k: Keys): LedState = ChargingLedState.readProfile(activity, k.enabled, k.effect, k.color, true, k.initialEffect, k.initialColor).let { LedState(it.enabled, it.effect, it.color) }
        fun save(k: Keys, state: LedState) { worker.execute { ChargingLedActions.saveProfileAndApplyIfCharging(activity.applicationContext, k.enabled, k.effect, k.color, state.enabled, state.effect, state.color) } }
        return InlineLightingUi.createProfiles(activity, deps, read(fan), read(logo), read(trigger), { save(fan, it) }, { save(logo, it) }, { save(trigger, it) })
    }

    fun calls(activity: Activity, deps: LightingTabDeps): List<View> {
        val fan = Keys(CallLightingState.INCOMING_FAN_ENABLED_KEY, CallLightingState.INCOMING_FAN_EFFECT_KEY, CallLightingState.INCOMING_FAN_COLOR_KEY, "flashing", 5)
        val logo = Keys(CallLightingState.INCOMING_LOGO_ENABLED_KEY, CallLightingState.INCOMING_LOGO_EFFECT_KEY, CallLightingState.INCOMING_LOGO_COLOR_KEY, "flashing", 1)
        val trigger = Keys(CallLightingState.INCOMING_SHOULDER_ENABLED_KEY, CallLightingState.INCOMING_SHOULDER_EFFECT_KEY, CallLightingState.INCOMING_SHOULDER_COLOR_KEY, "flashing", 8)
        fun read(k: Keys) = CallLightingState.readLed(activity, k.enabled, k.effect, k.color, true, k.initialEffect, k.initialColor)
        fun save(k: Keys, state: LedState) { CallLightingState.saveLed(activity, k.enabled, k.effect, k.color, state); if (CallLightingState.isEnabled(activity)) HardwareServiceActions.startCallLighting(activity) }
        return InlineLightingUi.createProfiles(activity, deps, read(fan), read(logo), read(trigger), { save(fan, it) }, { save(logo, it) }, { save(trigger, it) })
    }
}
