package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootEventPolicyTest {
    @Test
    fun moduleFallbackStartsOnceWithoutInventingUnlockAutomation() {
        val first = BootEventPolicy.decide(BootEvent.ROOT_STARTUP, false, true, true)
        assertTrue(first.startCoreServices)
        assertTrue(first.startTriggers)
        assertFalse(first.runUnlockAutomation)
        val duplicate = BootEventPolicy.decide(BootEvent.ROOT_STARTUP, true, true, true)
        assertFalse(duplicate.startCoreServices)
        assertFalse(duplicate.resetManualTriggerPause)
        assertFalse(duplicate.needsAsyncWork)
    }

    @Test
    fun firstBootEventStartsCoreServicesAndConfiguredTriggers() {
        val decision = BootEventPolicy.decide(
            event = BootEvent.BOOT_COMPLETED,
            coreServicesAlreadyStarted = false,
            triggersAutoStart = true,
            hasUnlockAutomation = false
        )

        assertTrue(decision.startCoreServices)
        assertTrue(decision.resetManualTriggerPause)
        assertTrue(decision.startTriggers)
        assertFalse(decision.runUnlockAutomation)
    }

    @Test
    fun userUnlockedDoesNotRestartCoreServicesForSameBoot() {
        val decision = BootEventPolicy.decide(
            event = BootEvent.USER_UNLOCKED,
            coreServicesAlreadyStarted = true,
            triggersAutoStart = true,
            hasUnlockAutomation = true
        )

        assertFalse(decision.startCoreServices)
        assertFalse(decision.startTriggers)
        assertTrue(decision.runUnlockAutomation)
    }

    @Test
    fun bootCompletedAlwaysClearsManualRestartPause() {
        val decision = BootEventPolicy.decide(
            event = BootEvent.BOOT_COMPLETED,
            coreServicesAlreadyStarted = true,
            triggersAutoStart = false,
            hasUnlockAutomation = false
        )

        assertTrue(decision.resetManualTriggerPause)
        assertFalse(decision.needsAsyncWork)
    }

    @Test
    fun userUnlockedCanRecoverWhenBootBroadcastWasMissed() {
        val decision = BootEventPolicy.decide(
            event = BootEvent.USER_UNLOCKED,
            coreServicesAlreadyStarted = false,
            triggersAutoStart = true,
            hasUnlockAutomation = true
        )

        assertTrue(decision.startCoreServices)
        assertTrue(decision.resetManualTriggerPause)
        assertTrue(decision.startTriggers)
        assertTrue(decision.runUnlockAutomation)
    }
}
