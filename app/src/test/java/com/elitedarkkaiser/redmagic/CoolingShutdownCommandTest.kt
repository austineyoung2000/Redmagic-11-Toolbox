package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CoolingShutdownCommandTest {
    @Test fun failedFirstWriteStillAttemptsPowerOffAndReportsFailure() {
        val dir = Files.createTempDirectory("cooling-off-").toFile()
        try {
            val enable = dir.resolve("power off")
            enable.writeText("1")
            fun run(valuePath: String, enablePath: String): Int = ProcessBuilder("sh", "-c",
                CoolingShutdownCommand.fan(valuePath, enablePath)).redirectErrorStream(true).start().let {
                it.inputStream.readBytes(); it.waitFor()
            }
            assertNotEquals(0, run(dir.resolve("missing/value").absolutePath, enable.absolutePath))
            assertEquals("0", enable.readText().trim())
            val value = dir.resolve("level")
            value.writeText("5")
            assertNotEquals(0, run(value.absolutePath, dir.resolve("missing/enable").absolutePath))
            assertEquals("0", value.readText().trim())
            assertEquals(0, run(value.absolutePath, enable.absolutePath))
        } finally { dir.deleteRecursively() }
    }
}
