package com.elitedarkkaiser.redmagic

/** Worker-confined duplicate state belongs to one screen-off session, not the process lifetime. */
internal class NotificationSessionDeduplicator {
    private val seen = LinkedHashSet<String>()
    fun reset() = seen.clear()
    fun seed(keys: List<String>) { reset(); seen.addAll(keys.takeLast(256)) }
    fun remove(key: String) { seen.remove(key) }
    fun accept(key: String, eligible: Boolean): Boolean {
        if (!eligible || !seen.add(key)) return false
        if (seen.size > 256) seen.remove(seen.first())
        return true
    }
}
