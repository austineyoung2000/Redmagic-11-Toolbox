package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerSafetyConfigTest {
    @Test
    fun offModeDoesNotEnableIntentOrHoldFiltering() {
        val config = TriggerSafetyConfig(
            mode = TriggerSafetyConfig.MODE_OFF
        )

        assertFalse(config.usesIntentUnlock())
        assertFalse(config.usesHold())
    }

    @Test
    fun holdModeDoesNotEnableIntentUnlock() {
        val config = TriggerSafetyConfig(
            mode = TriggerSafetyConfig.MODE_HOLD
        )

        assertFalse(config.usesIntentUnlock())
        assertTrue(config.usesHold())
    }

    @Test
    fun combinedModeEnablesBothFilters() {
        val config = TriggerSafetyConfig(
            mode = TriggerSafetyConfig.MODE_INTENT_HOLD
        )

        assertTrue(config.usesIntentUnlock())
        assertTrue(config.usesHold())
    }
}
