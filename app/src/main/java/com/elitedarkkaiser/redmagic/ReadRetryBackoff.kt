package com.elitedarkkaiser.redmagic

/** Failed reads back off independently of UI cache invalidation. Monotonic milliseconds only. */
internal class ReadRetryBackoff(private val initialMs: Long, private val maximumMs: Long) {
    private var delayMs = 0L
    private var retryAtMs = 0L
    @Synchronized fun remainingMs(now: Long): Long = (retryAtMs - now).coerceAtLeast(0)
    @Synchronized fun canAttempt(now: Long): Boolean = now >= retryAtMs
    @Synchronized fun failed(now: Long) {
        delayMs = if (delayMs == 0L) initialMs else (delayMs * 2).coerceAtMost(maximumMs)
        retryAtMs = now + delayMs
    }
    @Synchronized fun succeeded() { delayMs = 0; retryAtMs = 0 }
}
