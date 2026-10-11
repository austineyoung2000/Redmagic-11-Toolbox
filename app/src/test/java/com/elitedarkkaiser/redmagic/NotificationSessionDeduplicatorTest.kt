package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class NotificationSessionDeduplicatorTest {
    @Test fun reusedMessengerKeyAcceptsNewPostsWithoutWakeOrRemoval() {
        val session = NotificationSessionDeduplicator()
        assertTrue(session.accept("conversation", 100L, true))
        assertFalse(session.accept("conversation", 100L, true))
        assertTrue(session.accept("conversation", 200L, true))
        assertFalse(session.accept("conversation", 200L, true))
        assertFalse(session.accept("conversation", 100L, true))
        assertTrue(session.accept("conversation", 300L, true))
    }
    @Test fun seededConversationDoesNotBlockFreshLockedMessage() {
        val session = NotificationSessionDeduplicator()
        session.seed(listOf("conversation" to 100L))
        assertFalse(session.accept("conversation", 100L, true))
        assertTrue(session.accept("conversation", 200L, true))
    }
    @Test fun reusedMessengerIdWorksAcrossRepeatedLockCycles() {
        val session = NotificationSessionDeduplicator()
        repeat(5) {
            assertTrue(session.accept("messenger-conversation", 100L, true))
            assertFalse(session.accept("messenger-conversation", 100L, true))
            session.reset()
        }
    }
    @Test fun awakeOrPreemptedCallbackDoesNotConsumeNextEligibleAlert() {
        val session = NotificationSessionDeduplicator()
        assertFalse(session.accept("messenger", 100L, false))
        assertTrue(session.accept("messenger", 100L, true))
        assertFalse(session.accept("messenger", 200L, false))
        assertTrue(session.accept("messenger", 200L, true))
    }
    @Test fun reconnectReplayRemainsSeenUntilWakeOrRemoval() {
        val session = NotificationSessionDeduplicator()
        session.seed(listOf("old" to 100L))
        assertFalse(session.accept("old", 100L, true))
        session.remove("old")
        assertTrue(session.accept("old", 100L, true))
        session.reset()
        assertTrue(session.accept("old", 100L, true))
    }
    @Test fun differentConversationsRemainIndependent() {
        val session = NotificationSessionDeduplicator()
        assertTrue(session.accept("first", 100L, true))
        assertTrue(session.accept("second", 100L, true))
        assertFalse(session.accept("first", 100L, true))
        assertTrue(session.accept("first", 200L, true))
    }
}
