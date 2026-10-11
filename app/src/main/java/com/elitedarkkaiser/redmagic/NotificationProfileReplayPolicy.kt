package com.elitedarkkaiser.redmagic

/** Match stock's unchanged-output suppression, scoped to a live notification window. */
internal object NotificationProfileReplayPolicy {
    fun shouldWrite(active: List<NotificationLedBatch.Zone>?, requested: List<NotificationLedBatch.Zone>): Boolean =
        active != requested
}
