package com.elitedarkkaiser.redmagic

/** Setup is bounded separately; only first successful application starts the visible window. */
internal class NotificationWindowTiming(now: Long, seconds: Int) {
    private val durationMs = seconds.coerceIn(3, 30) * 1000L
    var deadline: Long = now + SETUP_LIMIT_MS
        private set
    var applied: Boolean = false
        private set
    fun markApplied(now: Long): Boolean {
        if (now >= deadline) return false
        if (!applied) {
            applied = true
            deadline = now + durationMs
        }
        return true
    }
    companion object { const val SETUP_LIMIT_MS = 15_000L }
}
