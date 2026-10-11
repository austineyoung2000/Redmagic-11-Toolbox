package com.elitedarkkaiser.redmagic

/** Worker-confined duplicate state belongs to one screen-off session, not the process lifetime. */
internal class NotificationSessionDeduplicator {
    private val seen = LinkedHashMap<String, Long>()
    fun reset() = seen.clear()
    fun seed(posts: List<Pair<String, Long>>) {
        reset()
        posts.takeLast(256).forEach { (key, postTime) -> seen[key] = postTime }
    }
    fun remove(key: String) { seen.remove(key) }
    fun accept(key: String, postTime: Long, eligible: Boolean): Boolean {
        if (!eligible) return false
        val previous = seen[key]
        // Messaging apps reuse a conversation key for new notification posts.
        // Only the same or an older post is a duplicate, not the key itself.
        if (previous != null && postTime <= previous) return false
        seen.remove(key)
        seen[key] = postTime
        if (seen.size > 256) seen.remove(seen.keys.first())
        return true
    }
}
