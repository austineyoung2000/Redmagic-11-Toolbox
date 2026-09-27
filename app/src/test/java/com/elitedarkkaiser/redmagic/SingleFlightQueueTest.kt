package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightQueueTest {
    @Test
    fun concurrentRequestsShareOneOperationAndResult() {
        val queue = SingleFlightQueue<Int>()
        val received = mutableListOf<Int>()

        assertTrue(queue.joinOrStart(received::add))
        assertFalse(queue.joinOrStart(received::add))
        assertTrue(queue.isRunning())

        queue.complete(42).forEach { it(42) }

        assertEquals(listOf(42, 42), received)
        assertFalse(queue.isRunning())
    }

    @Test
    fun queueCanStartAgainAfterCompletion() {
        val queue = SingleFlightQueue<String>()

        assertTrue(queue.joinOrStart())
        queue.complete("first")
        assertTrue(queue.joinOrStart())
    }
}
