package com.elitedarkkaiser.redmagic

/**
 * Coalesces concurrent requests for the same expensive operation.
 * The first caller starts the work; later callers only register completion
 * callbacks and receive the same result.
 */
internal class SingleFlightQueue<T> {
    private val lock = Any()
    private var running = false
    private val callbacks = mutableListOf<(T) -> Unit>()

    fun joinOrStart(callback: ((T) -> Unit)? = null): Boolean {
        synchronized(lock) {
            callback?.let(callbacks::add)
            if (running) return false

            running = true
            return true
        }
    }

    fun complete(value: T): List<(T) -> Unit> {
        return synchronized(lock) {
            check(running) { "No single-flight operation is running" }
            running = false
            callbacks.toList().also { callbacks.clear() }
        }
    }

    fun isRunning(): Boolean = synchronized(lock) { running }
}
