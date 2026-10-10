package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class NotificationSessionDeduplicatorTest {
    @Test fun reusedMessengerIdWorksAcrossRepeatedLockCycles() {
        val session = NotificationSessionDeduplicator()
        repeat(5) {
            assertTrue(session.accept("messenger-conversation", true))
            assertFalse(session.accept("messenger-conversation", true))
            session.reset() // Screen-on closes the preceding screen-off session.
        }
    }
    @Test fun awakeOrPreemptedCallbackDoesNotConsumeNextEligibleAlert() {
        val session = NotificationSessionDeduplicator()
        assertFalse(session.accept("messenger", false))
        assertTrue(session.accept("messenger", true))
    }
    @Test fun reconnectReplayRemainsSeenUntilWakeOrRemoval() {
        val session = NotificationSessionDeduplicator()
        session.seed(listOf("old"))
        assertFalse(session.accept("old", true))
        session.remove("old")
        assertTrue(session.accept("old", true))
        session.reset()
        assertTrue(session.accept("old", true))
    }
}
