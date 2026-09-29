package com.elitedarkkaiser.redmagic

/**
 * Determines whether the root foreground monitor is still authoritative.
 * A live process is not sufficient because its dumpsys pipeline can remain
 * alive after it has stopped producing parseable top-resumed packages.
 */
internal object ForegroundAuthorityPolicy {
    fun hasFreshRootSample(
        processAlive: Boolean,
        lastSampleAtMs: Long,
        nowMs: Long,
        maximumAgeMs: Long
    ): Boolean {
        if (
            !processAlive ||
            lastSampleAtMs <= 0L ||
            maximumAgeMs < 0L
        ) {
            return false
        }

        val ageMs = nowMs - lastSampleAtMs
        return ageMs in 0L..maximumAgeMs
    }
}
