package com.elitedarkkaiser.redmagic

/** Fences a cooling shutdown acknowledgement against intervening LED power changes. */
internal class CoolingShutdownReceipt(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val lifetimeMs: Long = 30_000
) {
    private var generation = 0L
    private var applied = false
    private var appliedAt = 0L
    @Synchronized fun token(): Long = generation
    @Synchronized fun invalidate() { generation++; applied = false }
    @Synchronized fun isApplied(): Boolean = applied && clock() - appliedAt < lifetimeMs
    @Synchronized fun record(token: Long, fanStopped: Boolean, pumpStopped: Boolean): Boolean {
        if (token != generation) return false
        applied = fanStopped && pumpStopped
        appliedAt = clock()
        return applied
    }
}
