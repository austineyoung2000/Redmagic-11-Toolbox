package com.elitedarkkaiser.redmagic

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/** A timeout releases the caller, never the unknown writer or its queue position. */
internal class BoundedCommandQueue(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "RedmagicRootCommands").apply { isDaemon = true }
    },
    private val queueWaitMs: Long = Long.MAX_VALUE
) {
    private val lock = ReentrantLock()
    @Volatile private var pending: Future<*>? = null
    @Volatile private var timedOut = false
    val revision = AtomicLong()

    fun status(): String = when {
        pending?.isDone == false && timedOut -> "quarantined: waiting for timed-out command completion"
        pending?.isDone == false -> "command in progress"
        else -> "ready"
    }

    fun <T> run(limitMs: Long, onTimeout: () -> Unit = {}, operation: () -> T): T? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limitMs)
        val acquired = try { lock.tryLock(minOf(queueWaitMs, limitMs), TimeUnit.MILLISECONDS) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt(); return null }
        if (!acquired) return null
        try {
            if (pending?.isDone == false) return null
            if (timedOut) { revision.incrementAndGet(); timedOut = false }
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return null
            val task = executor.submit<T> { operation() }
            pending = task
            return try {
                task.get(remaining, TimeUnit.NANOSECONDS)
            } catch (_: TimeoutException) {
                timedOut = true; revision.incrementAndGet(); runCatching(onTimeout); null
            } catch (_: InterruptedException) {
                timedOut = true; revision.incrementAndGet(); runCatching(onTimeout)
                Thread.currentThread().interrupt(); null
            } catch (_: ExecutionException) {
                revision.incrementAndGet(); null
            } finally {
                if (task.isDone) pending = null
            }
        } finally { lock.unlock() }
    }
}
