package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Dedicated bounded LED channel. Gameplay/cooling RootShell is untouched. */
internal object LightingRootExecutor {
    private const val TAG = "RedmagicLightingRoot"
    private const val COMMAND_LIMIT_MS = 5_000L
    private const val CANCEL_LIMIT_MS = 2_000L
    private val lock = Any()
    private var directory: File? = null
    private var session: Session? = null
    private var useOneShot = false
    @Volatile private var quarantined = false
    private var recovered = false
    private class Session(val process: Process, val oneShot: Boolean = false) {
        val input = process.inputStream.bufferedReader()
        val output = process.outputStream.bufferedWriter()
        val reader = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedmagicLedRootReader").apply { isDaemon = true }
        }
    }
    data class Result(val succeeded: Boolean, val output: String = "")

    fun initialize(context: Context) = synchronized(lock) {
        if (directory == null) directory = File(context.noBackupFilesDir, "lighting-writers").apply { mkdirs() }
        if (!recovered) {
            recovered = true
            // Revoke before reading PIDs. A delayed root authorization cannot
            // start a former process's LED script after recovery has begun.
            directory!!.listFiles()?.filter { it.extension == "pid" }?.forEach {
                if (!cancelWriter(it)) quarantined = true
            }
        }
    }

    fun status(): String = if (quarantined) "quarantined: writer cancellation not acknowledged"
        else "bounded LED channel; command limit=${COMMAND_LIMIT_MS}ms"

    fun exec(command: String): Boolean = execute(command).succeeded
    fun output(command: String): String? = execute(command).takeIf { it.succeeded }?.output?.trim()

    private fun execute(command: String): Result = synchronized(lock) {
        val dir = directory ?: return@synchronized Result(false)
        if (quarantined) {
            Log.e(TAG, "LED root channel quarantined after unacknowledged writer cancellation")
            return@synchronized Result(false)
        }
        val registry = File.createTempFile("writer-", ".pid", dir)
        val revoked = File(registry.path + ".revoked")
        val marker = "__REDMAGIC_LED_${UUID.randomUUID().toString().replace("-", "")}__"
        val payload = LightingRootCommand.instrument(command, registry.path, revoked.path) +
            "\nredmagic_led_status=${'$'}?; printf '\\n$marker:%s\\n' \"${'$'}redmagic_led_status\"\n"
        val active = session?.takeIf { it.process.isAlive } ?: runCatching {
            val process = if (useOneShot) ProcessBuilder("su", "-c", payload) else ProcessBuilder("su")
            Session(process.redirectErrorStream(true).start(), useOneShot).also { session = it }
        }.getOrElse {
            registry.delete()
            Log.e(TAG, "Could not open dedicated LED root shell", it)
            return@synchronized Result(false)
        }
        val task = active.reader.submit<Result> {
            if (!active.oneShot) {
                active.output.write(payload)
                active.output.flush()
            }
            val output = StringBuilder()
            while (true) {
                val line = active.input.readLine() ?: error("LED shell closed before receipt")
                if (line.startsWith("$marker:")) {
                    return@submit Result(line.substringAfter(':').trim().toIntOrNull() == 0, output.toString())
                }
                // A root command cannot consume unlimited app memory.
                if (output.length < 32_768) output.append(line.take(4096)).append('\n')
            }
            @Suppress("UNREACHABLE_CODE") Result(false)
        }
        try {
            val result = task.get(COMMAND_LIMIT_MS, TimeUnit.MILLISECONDS)
            registry.delete()
            revoked.delete()
            if (active.oneShot) {
                active.reader.shutdown()
                session = null
            }
            result
        } catch (error: Exception) {
            Log.e(TAG, "LED command did not complete within bounded channel", error)
            val registered = runCatching { registry.readText().isNotBlank() }.getOrDefault(true)
            if (!cancelWriter(registry)) quarantined = true
            // An implementation refusing interactive su has not run the PID
            // preamble, and therefore has not written any LED node. The next
            // operation may use bounded su -c; never replay a partial command.
            if (!active.oneShot && !registered) useOneShot = true
            task.cancel(true)
            // Killing the registered root shells releases a blocked reader.
            // Do not close BufferedReader on this thread: close can wait for a
            // read lock held by readLine, defeating the timeout itself.
            active.process.destroyForcibly()
            active.reader.shutdownNow()
            session = null
            Result(false)
        }
    }

    private fun cancelWriter(registry: File): Boolean {
        val revoked = File(registry.path + ".revoked")
        if (!runCatching { revoked.writeText("revoked\n") }.isSuccess) return false
        val lines = runCatching { registry.readLines() }.getOrDefault(emptyList()).filter { it.isNotBlank() }
        val identities = lines.mapNotNull(LightingRootCommand::identity)
        if (identities.size != lines.size) return false
        val succeeded = if (identities.isEmpty()) true else runCatching {
            val process = ProcessBuilder("su", "-c", LightingRootCommand.cancel(identities))
                .redirectOutput(java.io.File("/dev/null")).redirectError(java.io.File("/dev/null")).start()
            if (!process.waitFor(CANCEL_LIMIT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                false
            } else process.exitValue() == 0
        }.getOrDefault(false)
        if (succeeded) registry.delete()
        // Retain revocation markers for timed-out/orphaned authorization. They
        // are tiny and rare; deleting them early could allow a late writer.
        return succeeded
    }
}
