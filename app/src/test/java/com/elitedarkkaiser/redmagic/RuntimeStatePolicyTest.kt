package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class RuntimeStatePolicyTest {
    @Test fun failedReadsBackOffAndCapThenSuccessRestoresInitialDelay() {
        val retry = ReadRetryBackoff(5_000, 60_000)
        var now = 100L
        for (delay in listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L)) {
            assertTrue(retry.canAttempt(now))
            retry.failed(now)
            assertEquals(delay, retry.remainingMs(now))
            assertFalse(retry.canAttempt(now + delay - 1))
            now += delay
        }
        retry.succeeded()
        assertTrue(retry.canAttempt(now))
        retry.failed(now)
        assertEquals(5_000L, retry.remainingMs(now))
    }
    @Test fun changedWriterLateRootCompletionAndTimeInvalidateCoolingState() {
        var now = 0L
        val receipt = CoolingStateReceipt(clock = { now })
        val stamp = CoolingWriteStamp(1, 2)
        receipt.record(stamp)
        assertTrue(receipt.isCurrent(stamp))
        assertFalse(receipt.isCurrent(stamp.copy(resource = 4)))
        assertFalse(receipt.isCurrent(stamp.copy(root = 2)))
        now = 30_000
        assertFalse(receipt.isCurrent(stamp))
        receipt.recordWrite(stamp, stamp.copy(resource = 4), true)
        assertTrue(receipt.isCurrent(stamp.copy(resource = 4)))
        receipt.recordWrite(stamp, stamp.copy(resource = 6), true)
        assertFalse(receipt.isCurrent(stamp.copy(resource = 6)))
        receipt.recordWrite(stamp, stamp.copy(resource = 4), false)
        assertFalse(receipt.isCurrent(stamp.copy(resource = 4)))
    }
    @Test fun disabledCoolingIgnoresRetainedLevelsButEnabledCoolingRequiresExactReadback() {
        assertTrue(CoolingStateMatch.fan(false, 5, 0))
        assertFalse(CoolingStateMatch.fan(null, 0, 0))
        assertFalse(CoolingStateMatch.fan(true, 4, 3))
        assertTrue(CoolingStateMatch.fan(true, 3, 3))
        assertTrue(CoolingStateMatch.pump("0", "4", "80", "off"))
        assertFalse(CoolingStateMatch.pump(null, null, null, "off"))
        for ((profile, speed) in listOf("slow" to "40", "medium" to "60", "quick" to "80")) {
            assertTrue(CoolingStateMatch.pump("1", "4", speed, profile))
            assertFalse(CoolingStateMatch.pump("0", "4", speed, profile))
            assertFalse(CoolingStateMatch.pump("1", "3", speed, profile))
        }
    }
    @Test fun coldShutdownAcknowledgementExpiresForIndependentExternalChanges() {
        var now = 0L
        val receipt = CoolingShutdownReceipt(clock = { now })
        assertTrue(receipt.record(receipt.token(), true, true))
        now = 29_999
        assertTrue(receipt.isApplied())
        now = 30_000
        assertFalse(receipt.isApplied())
        assertTrue(receipt.record(receipt.token(), true, true))
        assertTrue(receipt.isApplied())
    }
}
