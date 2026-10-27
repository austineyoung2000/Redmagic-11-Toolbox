package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationLightingZonesTest {
    @Test fun legacyProfilesUseSharedColorAndEffect() {
        val profile = NotificationLightingState.Profile(color = 3, effect = "breathe", logo = true, triggers = true, fan = false)
        assertEquals(listOf(NotificationLedBatch.Zone("logo", "breathe", 3), NotificationLedBatch.Zone("triggers", "breathe", 3)), NotificationLightingZones.fromProfile(profile))
    }
    @Test fun eachEnabledRegionUsesItsOwnSelection() {
        val profile = NotificationLightingState.Profile(color = 1, effect = "steady", logo = true, triggers = true, fan = true,
            logoState = LedState(true, "flashing", 4), triggerState = LedState(true, "rapid", 7), fanState = LedState(true, "breathe", 8))
        assertEquals(listOf(NotificationLedBatch.Zone("logo", "flashing", 4), NotificationLedBatch.Zone("triggers", "rapid", 7), NotificationLedBatch.Zone("fan", "breathe", 8)), NotificationLightingZones.fromProfile(profile))
    }
}
