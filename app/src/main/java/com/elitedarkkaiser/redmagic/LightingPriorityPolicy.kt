package com.elitedarkkaiser.redmagic

internal object LightingPriorityPolicy {
    fun select(incomingCall: Boolean, charging: Boolean, notification: Boolean,
        game: Boolean, rgbStudio: Boolean): LedOwner = when {
        incomingCall -> LedOwner.CALL
        charging -> LedOwner.CHARGING
        notification -> LedOwner.NOTIFICATION
        game -> LedOwner.GAME_MODE
        rgbStudio -> LedOwner.RGB_CYCLE
        else -> LedOwner.NORMAL
    }
}
