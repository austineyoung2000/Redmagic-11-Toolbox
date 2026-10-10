package com.elitedarkkaiser.redmagic

/** Records every root LED write in a profile, including failures ignored by callers. */
internal object LedWriteReceipt {
    private class Receipt(var succeeded: Boolean = true)
    private val current = ThreadLocal<Receipt>()

    fun record(succeeded: Boolean): Boolean {
        if (!succeeded) current.get()?.succeeded = false
        return succeeded
    }

    fun capture(block: () -> Unit): Boolean {
        val parent = current.get()
        val receipt = Receipt()
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
