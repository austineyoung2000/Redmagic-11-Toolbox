package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class BoundedCommandQueueTest {
    @Test fun timedOutWriterIsNeverReplayedOrOverlapped() {
        val executor = Executors.newSingleThreadExecutor()
        val queue = BoundedCommandQueue(executor, 10)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val calls = AtomicInteger()
        try {
            val start = System.nanoTime()
            assertNull(queue.run(100) {
                calls.incrementAndGet(); entered.countDown()
                try { release.await(); 7 } finally { finished.countDown() }
            })
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertNull(queue.run(100) { calls.incrementAndGet() })
            assertEquals(1, calls.get())
            assertTrue(queue.status().contains("quarantined"))
            val generation = queue.revision.get()
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            executor.submit {}.get(1, TimeUnit.SECONDS)
            assertEquals(9, queue.run(100) { 9 })
            assertTrue(queue.revision.get() > generation)
            assertEquals(1, calls.get())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun waitingCallerCannotRunBehindUnfinishedCommand() {
        val executor = Executors.newSingleThreadExecutor()
        val clients = Executors.newSingleThreadExecutor()
        val queue = BoundedCommandQueue(executor, 10)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val first = clients.submit<Int?> { queue.run(1_000) { entered.countDown(); release.await(); 1 } }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertNull(queue.run(100) { fail("Must not queue uncertain work"); 2 })
            release.countDown()
            assertEquals(1, first.get(1, TimeUnit.SECONDS))
        } finally { release.countDown(); clients.shutdownNow(); executor.shutdownNow() }
    }

    @Test fun failureDoesNotReplayAndDoesNotPoisonFollowingCommand() {
        val executor = Executors.newSingleThreadExecutor()
        val queue = BoundedCommandQueue(executor)
        try {
            assertNull(queue.run<Int>(100) { throw IllegalStateException("closed pipe") })
            assertEquals(1L, queue.revision.get())
            assertEquals(3, queue.run(100) { 3 })
        } finally { executor.shutdownNow() }
    }
    @Test fun interruptedCallerKeepsItsInterruptAndFencesTheUnfinishedWriter() {
        val executor = Executors.newSingleThreadExecutor()
        val queue = BoundedCommandQueue(executor)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = java.util.concurrent.atomic.AtomicBoolean()
        val caller = Thread {
            assertNull(queue.run(5_000) { entered.countDown(); release.await(); 1 })
            interrupted.set(Thread.currentThread().isInterrupted)
        }
        try {
            caller.start()
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            caller.interrupt(); caller.join(1_000)
            assertFalse(caller.isAlive)
            assertTrue(interrupted.get())
            assertNull(queue.run(100) { fail("Writer was still active"); 2 })
            release.countDown()
            executor.submit {}.get(1, TimeUnit.SECONDS)
            assertEquals(3, queue.run(100) { 3 })
        } finally { release.countDown(); caller.interrupt(); executor.shutdownNow() }
    }

}
