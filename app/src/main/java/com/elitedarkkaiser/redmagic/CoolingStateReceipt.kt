package com.elitedarkkaiser.redmagic

internal data class CoolingWriteStamp(val root: Long, val resource: Long)

/** Acknowledged cooling state must still belong to this writer and expires for external readback. */
internal class CoolingStateReceipt(private val clock: () -> Long, private val lifetimeMs: Long = 30_000) {
    private var stamp: CoolingWriteStamp? = null
    private var atMs = 0L
    fun isCurrent(current: CoolingWriteStamp): Boolean = stamp == current && clock() - atMs < lifetimeMs
    fun record(current: CoolingWriteStamp) { stamp = current; atMs = clock() }
    fun invalidate() { stamp = null }
    fun recordWrite(before: CoolingWriteStamp, after: CoolingWriteStamp, succeeded: Boolean) {
        if (succeeded && before.root == after.root && after.resource == before.resource + 2) record(after)
        else invalidate()
    }
}

internal object CoolingStateMatch {
    fun fan(enabled: Boolean?, level: Int?, expected: Int): Boolean =
        if (expected == 0) enabled == false else enabled == true && level == expected
    fun pump(enabled: String?, frequency: String?, speed: String?, expected: String): Boolean =
        if (expected == "off") enabled == "0" else enabled == "1" && frequency == "4" && speed == when (expected) {
            "slow" -> "40"; "medium" -> "60"; "quick" -> "80"; else -> null
        }
}
