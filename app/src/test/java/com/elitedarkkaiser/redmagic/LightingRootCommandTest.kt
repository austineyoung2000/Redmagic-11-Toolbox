package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class LightingRootCommandTest {
    private fun selfPid(): Long = java.io.File("/proc/self/stat").readText().substringBefore(" ").toLong()
    @Test fun identityRejectsUntrustedOrInvalidPidRecords() {
        assertNull(LightingRootCommand.identity("1 100"))
        assertNull(LightingRootCommand.identity("500 0"))
        assertNull(LightingRootCommand.identity("500 100; kill"))
        val boot = "00000000-0000-0000-0000-000000000000"
        assertNull(LightingRootCommand.identity("500 100 invalid-boot"))
        assertEquals(LightingRootCommand.Identity(500, 100, boot), LightingRootCommand.identity("500 100 $boot"))
    }
    @Test fun revokedLateWriterCannotTouchLedNode() {
        val dir = Files.createTempDirectory("led-revoked-test").toFile()
        try {
            val registry = dir.resolve("writer.pid").apply { writeText("") }
            val revoked = dir.resolve("writer.revoked").apply { writeText("revoked") }
            val node = dir.resolve("effect")
            val command = LightingRootCommand.instrument("echo 0x1002001 > '${node.path}'", registry.path, revoked.path)
            val process = ProcessBuilder("sh", "-c", command).start()
            assertTrue(process.waitFor(3, TimeUnit.SECONDS))
            assertNotEquals(0, process.exitValue())
            assertFalse(node.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun boundedCancellationStopsRegisteredParentAndNestedWriter() {
        val dir = Files.createTempDirectory("led-cancel-test").toFile()
        var writer: Process? = null
        try {
            val registry = dir.resolve("writer.pid").apply { writeText("") }
            val revoked = dir.resolve("writer.revoked")
            val command = LightingRootCommand.instrument("(\nwhile :; do sleep 1; done\n)", registry.path, revoked.path)
            writer = ProcessBuilder("sh", "-c", command).start()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            var identities = emptyList<LightingRootCommand.Identity>()
            while (identities.size < 2 && System.nanoTime() < end) {
                identities = registry.readLines().mapNotNull(LightingRootCommand::identity)
                Thread.sleep(10)
            }
            assertTrue("Actual nested writer must be registered", identities.size >= 2)
            assertTrue(identities.none { it.pid == selfPid() })
            revoked.writeText("revoked")
            val cancel = ProcessBuilder("sh", "-c", LightingRootCommand.cancel(identities)).start()
            assertTrue(cancel.waitFor(3, TimeUnit.SECONDS))
            assertEquals(0, cancel.exitValue())
            assertTrue(writer.waitFor(3, TimeUnit.SECONDS))
        } finally {
            writer?.destroyForcibly()
            dir.deleteRecursively()
        }
    }
    @Test fun formerBootCannotTargetNewBootProcess() {
        val pidFile = Files.createTempFile("led-child", ".pid").toFile()
        val child = ProcessBuilder("sh", "-c", "echo $$ > '${pidFile.path}'; exec sleep 10").start()
        try {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (pidFile.length() == 0L && System.nanoTime() < end) Thread.sleep(10)
            val childPid = pidFile.readText().trim().toLong()
            val stat = java.io.File("/proc/${childPid}/stat").readText()
            val start = stat.substringAfterLast(") ").trim().split(Regex("\\s+"))[19].toLong()
            val cancel = ProcessBuilder("sh", "-c", LightingRootCommand.cancel(listOf(
                LightingRootCommand.Identity(childPid, start, "00000000-0000-0000-0000-000000000000")))).start()
            assertTrue(cancel.waitFor(3, TimeUnit.SECONDS))
            assertEquals(0, cancel.exitValue())
            assertTrue(child.isAlive)
        } finally { child.destroyForcibly(); pidFile.delete() }
    }
    @Test fun pidReuseCannotKillUnrelatedProcess() {
        val stat = java.io.File("/proc/self/stat").readText()
        val pid = selfPid()
        val start = stat.substringAfterLast(") ").trim().split(Regex("\\s+"))[19].toLong()
        val cancel = ProcessBuilder("sh", "-c", LightingRootCommand.cancel(
            listOf(LightingRootCommand.Identity(pid, start + 1,
                java.io.File("/proc/sys/kernel/random/boot_id").readText().trim())))).start()
        assertTrue(cancel.waitFor(3, TimeUnit.SECONDS))
        assertEquals(0, cancel.exitValue())
        assertTrue(java.io.File("/proc/${selfPid()}").exists())
    }
}
