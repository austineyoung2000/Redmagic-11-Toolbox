package com.elitedarkkaiser.redmagic

/** Fences a cooling shutdown acknowledgement against intervening LED power changes. */
internal class CoolingShutdownReceipt {
    private var generation = 0L
    private var applied = false
    @Synchronized fun token(): Long = generation
    @Synchronized fun invalidate() { generation++; applied = false }
    @Synchronized fun isApplied(): Boolean = applied
    @Synchronized fun record(token: Long, fanStopped: Boolean, pumpStopped: Boolean): Boolean {
        if (token != generation) return false
        applied = fanStopped && pumpStopped
        return applied
    }
}
