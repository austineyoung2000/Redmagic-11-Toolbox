package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LedWriteReceiptTest {
    @Test fun laterSuccessCannotHideFailedZone() {
        assertFalse(LedWriteReceipt.capture {
            LedWriteReceipt.record(false)
            LedWriteReceipt.record(true)
        })
        assertTrue(LedWriteReceipt.capture { LedWriteReceipt.record(true) })
    }

    @Test fun nestedFailurePropagatesAndExceptionDoesNotLeakReceipt() {
        assertFalse(LedWriteReceipt.capture {
            LedWriteReceipt.capture { LedWriteReceipt.record(false) }
            LedWriteReceipt.record(true)
        })
        runCatching { LedWriteReceipt.capture { error("simulated root exception") } }
        assertTrue(LedWriteReceipt.capture { LedWriteReceipt.record(true) })
    }

    @Test fun shutdownAttemptsEveryRegionAndReportsAnyFailedWrite() {
        val directory = Files.createTempDirectory("led-shutdown-test").toFile()
        try {
            val effect = directory.resolve("effect")
            val cfg = directory.resolve("cfg")
            fun run(path: String): Int = ProcessBuilder("sh", "-c",
                LedShutdownCommand.build(effect.absolutePath, path)).redirectErrorStream(true).start().let {
                it.inputStream.readBytes()
                it.waitFor()
            }
            assertEquals(0, run(cfg.absolutePath))
            assertEquals("0x3000000", effect.readText().trim())
            assertEquals("1", cfg.readText().trim())
            assertNotEquals(0, run(directory.resolve("missing/cfg").absolutePath))
            assertEquals("0x3000000", effect.readText().trim())
        } finally { directory.deleteRecursively() }
    }
}
