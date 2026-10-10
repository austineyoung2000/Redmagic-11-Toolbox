package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class NotificationDeadlinePolicyTest {
    @Test fun restartMakesOldAlarmHarmlessEvenWithSameToken() {
        val setup = NotificationDeadline("window-a", 115, 4)
        val visible = NotificationDeadline("window-a", 120, 4)
        assertFalse(NotificationDeadlinePolicy.expired(visible, setup, 4, 116))
        assertFalse(NotificationDeadlinePolicy.expired(visible, visible, 4, 119))
        assertTrue(NotificationDeadlinePolicy.expired(visible, visible, 4, 120))
    }
    @Test fun overlappingAlertCannotBeExpiredByFormerWindow() {
        val old = NotificationDeadline("old", 110, 4)
        val restarted = NotificationDeadline("new", 115, 4)
        assertFalse(NotificationDeadlinePolicy.expired(restarted, old, 4, 150))
        assertTrue(NotificationDeadlinePolicy.expired(restarted, restarted, 4, 115))
    }
    @Test fun canceledWindowAndOldBootCannotClearAnotherOwner() {
        val callback = NotificationDeadline("window", 110, 4)
        assertFalse(NotificationDeadlinePolicy.expired(null, callback, 4, 150))
        assertFalse(NotificationDeadlinePolicy.expired(callback, callback, 5, 150))
    }
}
