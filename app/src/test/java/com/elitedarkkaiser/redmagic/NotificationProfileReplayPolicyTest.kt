package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class NotificationProfileReplayPolicyTest {
    private val orange = listOf(NotificationLedBatch.Zone("logo", "breathe", 3),
        NotificationLedBatch.Zone("triggers", "breathe", 3), NotificationLedBatch.Zone("fan", "breathe", 3))
    @Test fun overlappingFriendsRestartDurationWithoutAnotherHardwareBatch() {
        var active: List<NotificationLedBatch.Zone>? = null
        var writes = 0
        fun message(now: Long): Long {
            if (NotificationProfileReplayPolicy.shouldWrite(active, orange)) { writes++; active = orange }
            val timing = NotificationWindowTiming(now, 10)
            assertTrue(timing.markApplied(now))
            return timing.deadline
        }
        assertEquals(11000L, message(1000L))
        assertEquals(15000L, message(5000L))
        assertEquals(19000L, message(9000L))
        assertEquals(1, writes)
        active = null // Expiry/unlock must permit the next screen-off start.
        assertEquals(40000L, message(30000L))
        assertEquals(2, writes)
    }
    @Test fun changedColorEffectOrSelectedZonesStillRequiresApplication() {
        assertFalse(NotificationProfileReplayPolicy.shouldWrite(orange, orange.toList()))
        assertTrue(NotificationProfileReplayPolicy.shouldWrite(orange, orange.map { it.copy(color = 7) }))
        assertTrue(NotificationProfileReplayPolicy.shouldWrite(orange, orange.map { it.copy(effect = "steady") }))
        assertTrue(NotificationProfileReplayPolicy.shouldWrite(orange, orange.take(1)))
        assertTrue(NotificationProfileReplayPolicy.shouldWrite(null, orange))
    }
}
