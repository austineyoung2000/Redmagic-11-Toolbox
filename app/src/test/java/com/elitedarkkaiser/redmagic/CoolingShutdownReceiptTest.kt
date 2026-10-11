package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class CoolingShutdownReceiptTest {
    @Test fun partialShutdownMustRemainRetryable() {
        for (fan in listOf(false, true)) for (pump in listOf(false, true)) {
            val receipt = CoolingShutdownReceipt()
            assertEquals(fan && pump, receipt.record(receipt.token(), fan, pump))
            assertEquals(fan && pump, receipt.isApplied())
        }
    }
    @Test fun interveningLedPowerChangeRejectsOldCompletion() {
        val receipt = CoolingShutdownReceipt()
        val old = receipt.token()
        receipt.invalidate()
        assertFalse(receipt.record(old, true, true))
        assertFalse(receipt.isApplied())
        assertTrue(receipt.record(receipt.token(), true, true))
        assertFalse(receipt.record(old, true, true))
        assertTrue(receipt.isApplied())
        receipt.invalidate()
        assertFalse(receipt.isApplied())
    }
}
