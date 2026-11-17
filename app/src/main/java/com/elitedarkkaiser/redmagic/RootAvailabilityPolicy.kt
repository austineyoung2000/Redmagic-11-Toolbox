package com.elitedarkkaiser.redmagic

/** Only an observed unavailable -> verified transition triggers automatic recovery. */
internal object RootAvailabilityPolicy {
    fun shouldRecover(previous: Boolean?, verified: Boolean, force: Boolean): Boolean =
        verified && (force || previous == false)
}
