package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Persistent non-LED shell with bounded callers and no replay of dispatched work. */
object RootShell {
    private const val TAG = "RedmagicRootShell"
    private const val READ_LIMIT_MS = 8_000L
    private const val WRITE_LIMIT_MS = 20_000L
    private const val OUTPUT_LIMIT = 262_144
    private val queue = BoundedCommandQueue()
    @Volatile private var journal: RootCommandJournal? = null
    @Volatile private var sharedSession: Session? = null
    @Volatile private var rootVerified = false
    @Volatile private var blockedReason: String? = null
    private data class CommandResult(val exitCode: Int, val output: String)

    internal fun initialize(context: Context) {
        if (journal != null) return
        runCatching {
            val boot = File("/proc/sys/kernel/random/boot_id").readText().trim()
            require(boot.matches(Regex("[0-9a-f-]{36}")))
            journal = RootCommandJournal(File(context.noBackupFilesDir, "root-command-receipts"), boot)
        }.onFailure { blockedReason = "private command journal unavailable"; Log.e(TAG, blockedReason, it) }
    }

    internal fun stateGeneration(): Long = queue.revision.get()
    internal fun status(): String = "verifiedRoot=$rootVerified; ${blockedReason ?: queue.status()}; read=${READ_LIMIT_MS}ms write=${WRITE_LIMIT_MS}ms"

    class Session internal constructor(
        internal val process: Process,
        internal val writer: BufferedWriter,
        internal val reader: BufferedReader,
        internal val token: String
    ) : Closeable {
        @Volatile internal var closed = false
        internal var commandId = 0L
        val isAlive: Boolean get() = !closed && process.isAlive
        fun exec(command: String): Boolean = execute(command, WRITE_LIMIT_MS, this)?.exitCode == 0
        fun execForOutput(command: String): String? = output(execute(command, READ_LIMIT_MS, this))
        override fun close() { queue.run(2_000) { closeSession(this) } }
    }

    fun hasRoot(): Boolean = execForOutput("id -u")?.trim() == "0"
    fun exec(command: String): Boolean = execute(command, WRITE_LIMIT_MS)?.exitCode == 0
    fun execForOutput(command: String): String? = output(execute(command, READ_LIMIT_MS))
    private fun output(result: CommandResult?): String? = result?.takeIf { it.exitCode == 0 }?.output?.ifEmpty { null }
    fun openSession(): Session? = queue.run(READ_LIMIT_MS) { openVerifiedSession() }
    fun closeSharedSession() { queue.run(2_000) { sharedSession?.let(::closeSession); sharedSession = null } }

    private fun execute(command: String, limitMs: Long, requested: Session? = null): CommandResult? {
        val receipts = journal ?: return null
        val revoked = AtomicBoolean(false)
        val activeLease = AtomicReference<RootCommandJournal.Lease?>()
        val result = queue.run(limitMs, onTimeout = {
            revoked.set(true)
            runCatching { activeLease.get()?.revoke() }
            blockedReason = "command timed out; awaiting completion receipt"
            Log.w(TAG, blockedReason!!)
        }) {
            if (!receipts.prepare()) {
                blockedReason = "quarantined: unfinished command from earlier process"
                return@run null
            }
            blockedReason = null
            val lease = receipts.begin()
            activeLease.set(lease)
            if (revoked.get()) lease.revoke()
            try {
                val current = requested ?: sharedSession?.takeIf { it.isAlive } ?: openVerifiedSession().also { sharedSession = it }
                if (revoked.get()) return@run null
                val program = receipts.program(command, lease)
                lease.submitted = true
                val completed = if (current != null) readCommand(current, program) else runOneShot(program)
                if (!lease.completed()) {
                    blockedReason = "quarantined: command lacks completion receipt"
                    error("Root completion receipt missing")
                }
                if (completed.exitCode != 125) rootVerified = true // Durable guarded completion proves root, including fallback.
                completed
            } catch (error: Exception) {
                Log.e(TAG, "Root command failed without replay", error)
                sharedSession?.let(::closeSession)
                sharedSession = null
                throw error
            } finally {
                // Unknown work stays fenced. Never clear a record merely because a caller timed out.
                if (!lease.submitted || lease.completed()) lease.clear()
                if (lease.completed() || !lease.file.exists()) blockedReason = null
            }
        }
        if (result != null && result.exitCode != 0) queue.revision.incrementAndGet()
        return result
    }

    private fun openVerifiedSession(): Session? {
        var opened: Session? = null
        return runCatching {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val session = Session(process, process.outputStream.bufferedWriter(), process.inputStream.bufferedReader(),
                java.util.UUID.randomUUID().toString().replace("-", ""))
            opened = session
            val check = readCommand(session, "id -u")
            if (check.exitCode != 0 || check.output.trim() != "0") {
                closeSession(session); null
            } else { rootVerified = true; session }
        }.onFailure {
            opened?.let(::closeSession)
            rootVerified = false
            Log.w(TAG, "Interactive root probe failed; no hardware command was dispatched", it)
        }.getOrNull()
    }

    private fun readCommand(session: Session, command: String): CommandResult {
        check(session.isAlive) { "Root session is closed" }
        session.commandId++
        val marker = "__REDMAGIC_ROOT_${session.token}_${session.commandId}__"
        session.writer.write(command)
        session.writer.newLine()
        session.writer.write("redmagic_status=${'$'}?; printf '\\n$marker:%s\\n' \"${'$'}redmagic_status\"")
        session.writer.newLine(); session.writer.flush()
        val output = StringBuilder()
        var overflow = false
        while (true) {
            val line = session.reader.readLine() ?: error("Root shell closed before receipt")
            if (line.startsWith("$marker:")) {
                val code = line.substringAfter(':').trim().toIntOrNull() ?: -1
                return CommandResult(if (overflow) -1 else code, output.toString().trim())
            }
            if (output.length + line.length > OUTPUT_LIMIT) overflow = true
            if (!overflow) output.append(line).append('\n')
        }
    }

    private fun runOneShot(program: String): CommandResult {
        // Compatibility fallback is permitted only before any hardware payload is sent.
        val process = ProcessBuilder("su", "-c", program).redirectErrorStream(true).start()
        val session = Session(process, process.outputStream.bufferedWriter(), process.inputStream.bufferedReader(), "one-shot")
        val output = StringBuilder()
        var overflow = false
        try {
            while (true) {
                val line = session.reader.readLine() ?: break
                if (output.length + line.length > OUTPUT_LIMIT) overflow = true
                if (!overflow) output.append(line).append('\n')
            }
            return CommandResult(if (overflow) -1 else process.waitFor(), output.toString().trim())
        } finally { closeSession(session) }
    }

    private fun closeSession(session: Session) {
        session.closed = true
        if (sharedSession === session) sharedSession = null
        // Only our Java child handle is retired; no PID file, app process, watchdog or daemon is signalled.
        runCatching { session.process.destroy() }
        // This only runs on the serialized worker after a read returns, never from a waiting caller.
        runCatching { session.writer.close() }
        runCatching { session.reader.close() }
    }
}
