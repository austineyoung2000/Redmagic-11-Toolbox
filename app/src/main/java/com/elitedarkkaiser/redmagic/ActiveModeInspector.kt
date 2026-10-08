package com.elitedarkkaiser.redmagic

import android.content.Context

object ActiveModeInspector {
    fun summary(context: Context): String {
        val owner = LedOwnership.current(context)
        val profileName = MasterProfileStorage
            .lastAppliedProfile(context)
            ?.name

        val ownerLabel = when (owner) {
            LedOwner.NOTIFICATION -> "Notification Lighting"
            LedOwner.CHARGING -> "Charging Mode"
            LedOwner.CALL -> "Call Lighting"
            LedOwner.GAME_MODE -> "Game Mode"
            LedOwner.RGB_CYCLE -> "RGB Studio"
            LedOwner.NORMAL -> "Normal"
            LedOwner.NONE -> "LEDs Off"
        }

        val ownerDetail = when (owner) {
            LedOwner.NOTIFICATION -> "A timed screen-off notification window owns the LEDs."
            LedOwner.CHARGING ->
                "Charging Mode controls LEDs unless an incoming call is ringing."
            LedOwner.CALL ->
                "An incoming ringing call currently controls the LEDs."
            LedOwner.GAME_MODE ->
                "A selected foreground app is using its Game Mode profile."
            LedOwner.RGB_CYCLE ->
                "RGB Studio currently controls the LED zones."
            LedOwner.NORMAL ->
                "Normal saved lighting is currently active."
            LedOwner.NONE ->
                "No feature currently owns an active LED profile."
        }

        val cooling = when {
            CallLightingState.isActive(context) &&
                CallLightingState.wasFanPausedForCall(context) ->
                "Fan paused for active call"
            owner == LedOwner.GAME_MODE ->
                "Game Mode profile"
            isAutoFanEnabledStorage(context) &&
                savedPumpStateStorage(context).autoEnabled ->
                "Auto Fan + Auto Pump"
            isAutoFanEnabledStorage(context) ->
                "Auto Fan"
            savedPumpStateStorage(context).autoEnabled ->
                "Auto Pump"
            else ->
                "Manual saved controls"
        }

        val baseProfile = profileName?.let {
            "Master Profile: $it"
        } ?: "Master Profile: None applied"

        return buildString {
            append("ACTIVE OWNER: ")
            append(ownerLabel)
            append('\n')
            append(ownerDetail)
            append('\n')
            append("COOLING: ")
            append(cooling)
            append('\n')
            append(baseProfile)
            append('\n')
            append("Priority: Incoming Call > Charging > Notification > Game Mode > RGB Studio > Normal")
        }
    }
}
