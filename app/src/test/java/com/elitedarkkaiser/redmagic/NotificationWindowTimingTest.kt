package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class NotificationWindowTimingTest {
    @Test fun slowSetupDoesNotConsumeSelectedVisibleDuration() {
        val window = NotificationWindowTiming(1000L, 10)
        assertEquals(16000L, window.deadline)
        assertTrue(window.markApplied(9000L))
        assertEquals(19000L, window.deadline)
        assertTrue(window.applied)
    }
    @Test fun burstsCannotExtendFirstSuccessfulApplicationDeadline() {
        val window = NotificationWindowTiming(1000L, 10)
        assertTrue(window.markApplied(2000L))
        assertTrue(window.markApplied(11000L))
        assertEquals(12000L, window.deadline)
        assertFalse(window.markApplied(12000L))
        assertFalse(window.markApplied(13000L))
        assertEquals(12000L, window.deadline)
    }
    @Test fun expiredSetupCannotStartOrResurrectVisibleLighting() {
        val window = NotificationWindowTiming(1000L, 10)
        assertFalse(window.markApplied(16000L))
        assertFalse(window.markApplied(20000L))
        assertFalse(window.applied)
        assertEquals(16000L, window.deadline)
    }
    @Test fun supportedDurationBoundsApplyAfterPreparation() {
        for (seconds in listOf(3, 10, 30)) {
            val window = NotificationWindowTiming(1000L, seconds)
            assertTrue(window.markApplied(5000L))
            assertEquals(5000L + seconds * 1000L, window.deadline)
        }
    }
}
