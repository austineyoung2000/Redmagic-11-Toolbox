package com.elitedarkkaiser.redmagic

internal object LightingPriorityPolicy {
    fun notificationEligible(enabled: Boolean, deadline: Long, now: Long,
        screenInteractive: Boolean, plugged: Boolean, ringing: Boolean): Boolean =
        enabled && deadline > now && !screenInteractive && !plugged && !ringing

    fun canApply(requested: LedOwner, effective: LedOwner): Boolean =
        if (requested == LedOwner.NONE) effective in setOf(LedOwner.NONE, LedOwner.NORMAL, LedOwner.RGB_CYCLE)
        else requested == effective

    fun select(incomingCall: Boolean, charging: Boolean, notification: Boolean,
        game: Boolean, rgbStudio: Boolean, screenInteractive: Boolean = true): LedOwner = when {
        incomingCall -> LedOwner.CALL
        charging -> LedOwner.CHARGING
        notification -> LedOwner.NOTIFICATION
        !screenInteractive -> LedOwner.NONE
        game -> LedOwner.GAME_MODE
        rgbStudio -> LedOwner.RGB_CYCLE
        else -> LedOwner.NORMAL
    }
}
