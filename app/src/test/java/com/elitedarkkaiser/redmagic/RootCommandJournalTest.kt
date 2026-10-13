package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class RootCommandJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun predecessorBlocksUntilDurableReceiptAndIsRevoked() {
        val directory = temporary.newFolder()
        val original = RootCommandJournal(directory, "boot-a")
        assertTrue(original.prepare())
        val lease = original.begin()
        val replacement = RootCommandJournal(directory, "boot-a")
        assertFalse(replacement.prepare())
        assertTrue(lease.revoked.exists())
        lease.done.writeText("${lease.token}:0\n")
        assertTrue(replacement.prepare())
        assertFalse(lease.file.exists())
    }
    @Test fun oldBootDoesNotBlockNewBootButMalformedCurrentRecordFailsClosed() {
        val directory = temporary.newFolder()
        val old = RootCommandJournal(directory, "boot-a")
        assertTrue(old.prepare()); old.begin()
        assertTrue(RootCommandJournal(directory, "boot-b").prepare())
        File(directory, "bad.lease").writeText("invalid")
        assertFalse(RootCommandJournal(directory, "boot-b").prepare())
    }
    @Test fun shellExitStillCreatesReceiptAndRevokedPayloadCannotRun() {
        val journal = RootCommandJournal(temporary.newFolder("quoted'folder"), "boot-a")
        assertTrue(journal.prepare())
        val lease = journal.begin()
        val program = journal.program("exit 7", lease)
        // Host-only sandbox shell; the simulated UID supplies the NX command guard without invoking su.
        val shell = ProcessBuilder("sh", "-c", "id() { echo 0; };\n$program").start()
        assertTrue(shell.waitFor(2, TimeUnit.SECONDS))
        assertEquals(7, shell.exitValue())
        assertTrue(lease.completed())
        lease.clear()
        val revoked = journal.begin()
        revoked.revoke()
        val marker = temporary.root.resolve("must-not-run")
        val forbidden = journal.program("touch '${marker.path}'", revoked)
        val second = ProcessBuilder("sh", "-c", "id() { echo 0; };\n$forbidden").start()
        assertTrue(second.waitFor(2, TimeUnit.SECONDS))
        assertEquals(125, second.exitValue())
        assertFalse(marker.exists())
        assertTrue(revoked.completed())
    }
    @Test fun deniedRootCannotRunPayloadAndRecordsGuardFailure() {
        val journal = RootCommandJournal(temporary.newFolder(), "boot-a")
        assertTrue(journal.prepare())
        val lease = journal.begin()
        val marker = temporary.root.resolve("not-root")
        val program = journal.program("touch '${marker.path}'", lease)
        val shell = ProcessBuilder("sh", "-c", "id() { echo 10000; };\n$program").start()
        assertTrue(shell.waitFor(2, TimeUnit.SECONDS))
        assertEquals(125, shell.exitValue())
        assertTrue(lease.completed())
        assertFalse(marker.exists())
    }

}
