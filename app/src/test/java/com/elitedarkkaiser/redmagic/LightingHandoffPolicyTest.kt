package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class LightingHandoffPolicyTest {
    @Test fun everyPriorityCombinationHonorsScreenOffAndCallException() {
        for (mask in 0 until 32) for (screenOn in listOf(false, true)) {
            val call = mask and 1 != 0
            val charge = mask and 2 != 0
            val notification = mask and 4 != 0 && !screenOn && !charge && !call
            val game = mask and 8 != 0
            val studio = mask and 16 != 0
            val expected = when {
                call -> LedOwner.CALL
                charge -> LedOwner.CHARGING
                notification -> LedOwner.NOTIFICATION
                !screenOn -> LedOwner.NONE
                game -> LedOwner.GAME_MODE
                studio -> LedOwner.RGB_CYCLE
                else -> LedOwner.NORMAL
            }
            assertEquals("mask=$mask screenOn=$screenOn", expected,
                LightingPriorityPolicy.select(call, charge, notification, game, studio, screenOn))
            for (requested in LedOwner.values()) {
                val allowed = requested == expected || requested == LedOwner.NONE &&
                    expected in listOf(LedOwner.NONE, LedOwner.NORMAL, LedOwner.RGB_CYCLE)
                assertEquals("requested=$requested effective=$expected", allowed,
                    LightingPriorityPolicy.canApply(requested, expected))
            }
        }
    }

    @Test fun staleDeadlineOrScreenWakeOrPlugOrCallCannotRetainNotification() {
        assertTrue(LightingPriorityPolicy.notificationEligible(true, 110, 100, false, false, false))
        assertFalse(LightingPriorityPolicy.notificationEligible(true, 100, 100, false, false, false))
        assertFalse(LightingPriorityPolicy.notificationEligible(true, 110, 100, true, false, false))
        assertFalse(LightingPriorityPolicy.notificationEligible(true, 110, 100, false, true, false))
        assertFalse(LightingPriorityPolicy.notificationEligible(true, 110, 100, false, false, true))
        assertFalse(LightingPriorityPolicy.notificationEligible(false, 110, 100, false, false, false))
    }

    @Test fun changedOwnerInvalidatesInFlightOldOwner() {
        assertTrue(LightingPriorityPolicy.canApply(LedOwner.NOTIFICATION, LedOwner.NOTIFICATION))
        assertFalse(LightingPriorityPolicy.canApply(LedOwner.NOTIFICATION, LedOwner.NONE))
        assertFalse(LightingPriorityPolicy.canApply(LedOwner.NOTIFICATION, LedOwner.CHARGING))
        assertFalse(LightingPriorityPolicy.canApply(LedOwner.CHARGING, LedOwner.CALL))
        assertTrue(LightingPriorityPolicy.canApply(LedOwner.CHARGING, LedOwner.CHARGING))
    }
}
