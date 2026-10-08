package com.elitedarkkaiser.redmagic

import android.content.Context

enum class LedOwner {
    NONE,
    NORMAL,
    GAME_MODE,
    CALL,
    CHARGING,
    NOTIFICATION,
    RGB_CYCLE
}

object LedOwnership {
    fun current(context: Context): LedOwner {
        return LightingPriorityPolicy.select(
            incomingCall = CallLightingState.isEnabled(context) && CallLightingState.isActive(context),
            charging = ChargingLedState.isEnabled(context) && ChargingLedState.isChargingNow(context),
            notification = NotificationLightingState.isActive(),
            game = isGameModeLedOverrideActiveStorage(context),
            rgbStudio = RgbStudioStorage.isEnabled(context)
        )
    }

    fun canNormalApply(context: Context): Boolean {
        return current(context) == LedOwner.NORMAL
    }

    fun canGameModeApply(context: Context): Boolean {
        val owner = current(context)
        return owner == LedOwner.NORMAL || owner == LedOwner.GAME_MODE
    }

    fun canCallApply(context: Context): Boolean {
        return current(context) == LedOwner.CALL
    }
}
