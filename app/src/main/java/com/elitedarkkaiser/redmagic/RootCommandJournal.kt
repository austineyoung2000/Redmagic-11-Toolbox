package com.elitedarkkaiser.redmagic

import java.io.File
import java.util.UUID

/** Completion receipts fence writers across process death without signalling any PID. */
internal class RootCommandJournal(private val directory: File, private val boot: String) {
    class Lease(val file: File, val token: String) {
        val done = File(file.path + ".done")
        val revoked = File(file.path + ".revoked")
        @Volatile var submitted = false
        fun revoke() { revoked.writeText("revoked\n") }
        fun completed(): Boolean = runCatching {
            done.readText().trim().let { it.startsWith("$token:") && it.substringAfter(':').toIntOrNull() != null }
        }.getOrDefault(false)
        fun clear() { file.delete(); done.delete(); File(done.path + ".new").delete(); revoked.delete() }
    }
    fun prepare(): Boolean {
        if (!directory.exists() && !directory.mkdirs()) return false
        val files = directory.listFiles() ?: return false
        for (file in files.filter { it.extension == "lease" }) {
            val fields = runCatching { file.readLines() }.getOrNull() ?: return false
            if (fields.size != 2 || fields[0].isBlank() || fields[1].isBlank()) return false
            val lease = Lease(file, fields[1])
            // Revoke first: a delayed authorization cannot begin a former app's command.
            runCatching { lease.revoke() }.getOrElse { return false }
            if (fields[0] != boot || lease.completed()) lease.clear() else return false
        }
        return true
    }
    fun begin(): Lease {
        val token = UUID.randomUUID().toString().replace("-", "")
        val file = File(directory, "$token.lease")
        val staging = File(directory, "$token.pending")
        staging.writeText("$boot\n$token\n")
        // Root writes into an app-created inode, preserving its owner and SELinux label.
        File(file.path + ".done.new").writeText("")
        check(staging.renameTo(file)) { "Unable to publish root command lease" }
        return Lease(file, token)
    }
    fun program(command: String, lease: Lease): String {
        val dollar = '$'
        return """
            (
                redmagic_command_status=125
                if [ ! -e ${quote(lease.revoked.path)} ] && [ "${dollar}(id -u)" = 0 ]; then
                    (
                        $command
                    )
                    redmagic_command_status=${dollar}?
                fi
                printf '%s:%s\n' '${lease.token}' "${dollar}redmagic_command_status" > ${quote(lease.done.path + ".new")} &&
                    mv -f ${quote(lease.done.path + ".new")} ${quote(lease.done.path)} || exit 126
                exit "${dollar}redmagic_command_status"
            )
        """.trimIndent()
    }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
