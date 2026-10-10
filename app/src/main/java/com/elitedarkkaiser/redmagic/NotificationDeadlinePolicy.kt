package com.elitedarkkaiser.redmagic

internal data class NotificationDeadline(val token: String, val deadline: Long, val boot: Int)
internal object NotificationDeadlinePolicy {
    fun matches(saved: NotificationDeadline?, callback: NotificationDeadline): Boolean = saved == callback
    fun expired(saved: NotificationDeadline?, callback: NotificationDeadline, boot: Int, now: Long) =
        matches(saved, callback) && callback.boot == boot && now >= callback.deadline
}
