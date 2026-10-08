package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertEquals
import org.junit.Test

class LightingPriorityPolicyTest {
    @Test fun ringingInterruptsChargingAndChargingResumesAfterAnswer() {
        assertEquals(LedOwner.CALL, LightingPriorityPolicy.select(true,true,true,true,true))
        assertEquals(LedOwner.CHARGING, LightingPriorityPolicy.select(false,true,true,true,true))
    }
    @Test fun notificationExpiryRevealsGameThenStudioThenNormal() {
        assertEquals(LedOwner.NOTIFICATION, LightingPriorityPolicy.select(false,false,true,true,true))
        assertEquals(LedOwner.GAME_MODE, LightingPriorityPolicy.select(false,false,false,true,true))
        assertEquals(LedOwner.RGB_CYCLE, LightingPriorityPolicy.select(false,false,false,false,true))
        assertEquals(LedOwner.NORMAL, LightingPriorityPolicy.select(false,false,false,false,false))
    }
}
