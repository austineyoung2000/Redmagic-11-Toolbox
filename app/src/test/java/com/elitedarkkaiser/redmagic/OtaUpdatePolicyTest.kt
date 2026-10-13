package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class OtaUpdatePolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun output(enabled: List<String>, disabled: List<String>): String = buildString {
        appendLine("__REDMAGIC_OTA_ENABLED__")
        enabled.forEach { appendLine("package:$it") }
        appendLine("__REDMAGIC_OTA_DISABLED__")
        disabled.forEach { appendLine("package:$it") }
        appendLine("__REDMAGIC_OTA_END__")
    }

    @Test fun exactPackageReadbackDistinguishesCompleteMixedAndMissingStates() {
        val targets = OtaUpdatePolicy.packages
        val enabled = OtaUpdatePolicy.parse(output(targets, emptyList()))!!
        assertTrue(enabled.matches(true))
        assertFalse(enabled.matches(false))
        val disabled = OtaUpdatePolicy.parse(output(emptyList(), targets))!!
        assertTrue(disabled.matches(false))
        val mixed = OtaUpdatePolicy.parse(output(targets.take(1), targets.drop(1)))!!
        assertFalse(mixed.matches(true)); assertFalse(mixed.matches(false))
        assertEquals("OTA: Mixed package states", mixed.summary())
        val prefix = OtaUpdatePolicy.parse(output(listOf("com.zte.zdm.other"), emptyList()))!!
        assertTrue(prefix.states.values.all { it == OtaUpdatePolicy.State.MISSING })
        assertFalse(prefix.matches(true)); assertFalse(prefix.matches(false))
    }

    @Test fun binderErrorsIncompleteAndContradictoryReadbackStayUnknown() {
        assertNull(OtaUpdatePolicy.parse(null))
        assertNull(OtaUpdatePolicy.parse("cmd: Failure calling service package: Failed transaction"))
        assertNull(OtaUpdatePolicy.parse("__REDMAGIC_OTA_ENABLED__\n__REDMAGIC_OTA_DISABLED__"))
        assertNull(OtaUpdatePolicy.parse(output(listOf("Failure calling service package"), emptyList())))
        assertNull(OtaUpdatePolicy.parse(output(listOf("com.zte.zdm"), listOf("com.zte.zdm"))))
    }

    @Test fun middleFailureStillAttemptsThirdPackageAndReportsFailureForBothActions() {
        for (enabled in listOf(false, true)) {
            val log = temporary.newFile()
            val command = OtaUpdatePolicy.changeCommand(enabled)
            // Host shell simulation only: no Android package manager or device commands are invoked.
            val shell = ProcessBuilder("sh", "-c", """
                pm() {
                    printf '%s\n' "${'$'}*" >> '${log.path}'
                    [ "${'$'}4" != com.zte.zdmdaemon ]
                }
                $command
            """.trimIndent()).start()
            assertTrue(shell.waitFor(2, TimeUnit.SECONDS))
            assertEquals(1, shell.exitValue())
            assertEquals(OtaUpdatePolicy.packages.map {
                "${if (enabled) "enable" else "disable-user"} --user 0 $it"
            }, log.readLines())
        }
    }

    @Test fun successfulBatchOnlyTargetsTheThreeNamedUserZeroPackages() {
        for (enabled in listOf(false, true)) {
            val log = temporary.newFile()
            val command = OtaUpdatePolicy.changeCommand(enabled)
            val shell = ProcessBuilder("sh", "-c", """
                pm() { printf '%s\n' "${'$'}*" >> '${log.path}'; return 0; }
                $command
            """.trimIndent()).start()
            assertTrue(shell.waitFor(2, TimeUnit.SECONDS))
            assertEquals(0, shell.exitValue())
            assertEquals(3, log.readLines().size)
            assertTrue(log.readLines().all { "--user 0 com.zte.zdm" in it })
        }
    }
}
