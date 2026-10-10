package com.elitedarkkaiser.redmagic

/** Records every root LED write in a profile, including failures ignored by callers. */
internal object LedWriteReceipt {
    private class Receipt(val stillEligible: () -> Boolean, var succeeded: Boolean = true)
    private val current = ThreadLocal<Receipt>()

    fun record(succeeded: Boolean): Boolean {
        if (!succeeded) current.get()?.succeeded = false
        return succeeded
    }

    fun canWrite(): Boolean {
        val receipt = current.get() ?: return true
        if (!receipt.succeeded) return false
        if (!receipt.stillEligible()) receipt.succeeded = false
        return receipt.succeeded
    }

    fun capture(stillEligible: () -> Boolean = { true }, block: () -> Unit): Boolean {
        val parent = current.get()
        val receipt = Receipt(stillEligible)
        current.set(receipt)
        try {
            block()
            return receipt.succeeded
        } finally {
            current.set(parent)
            if (!receipt.succeeded) parent?.succeeded = false
        }
    }
}
