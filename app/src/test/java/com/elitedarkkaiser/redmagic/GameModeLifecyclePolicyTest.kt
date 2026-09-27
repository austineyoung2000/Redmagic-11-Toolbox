package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModeLifecyclePolicyTest {
    @Test
    fun stoppingServiceRejectsQueuedWork() {
        assertFalse(
            GameModeLifecyclePolicy.acceptsWork(
                stopping = true
            )
        )
    }

    @Test
    fun screenOffPauseRejectsForegroundWork() {
        assertFalse(
            GameModeLifecyclePolicy.acceptsWork(
                stopping = false,
                pausedForScreenOff = true
            )
        )
    }

    @Test
    fun staleLedOwnershipStillRequiresRestore() {
        assertTrue(
            GameModeLifecyclePolicy.needsRestore(
                activePackage = null,
                ledOverrideActive = true
            )
        )
    }
}
