package com.elitedarkkaiser.redmagic

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Explicit opt-in provisioning using the exact device-tested binary; no startup enforcement. */
internal object DeviceIdRepairController {
    private const val BASE = "/data/adb/redmagic_toolbox/device_id_repair"
    private const val BOOT = "/data/adb/service.d/redmagic_device_id_repair.sh"
    private const val HASH = "a5eedbc361c631d50398c6458b0b8cf1b6130fa97454e5ea203ada1c71287768"
    data class State(val module: Boolean, val boot: Boolean, val outcome: String)

    @Synchronized fun read(): State? {
        if (!DeviceCompatibility.isSupportedDevice()) return null
        val output = RootShell.execForOutput("""
            (m=/data/adb/modules/autoDeviceID
            if [ -d "${'$'}m" ] && [ ! -e "${'$'}m/disable" ] && [ ! -e "${'$'}m/remove" ]; then echo module; else echo standalone; fi
            if [ -f '$BASE/boot_enabled' ]; then echo boot; else echo manual; fi
            cat '$BASE/status' 2>/dev/null || echo 'Not run by Toolbox')
        """.trimIndent()) ?: return null
        val lines = output.trim().lines()
        if (lines.size != 3 || lines[0] !in listOf("module", "standalone") || lines[1] !in listOf("boot", "manual")) return null
        return State(lines[0] == "module", lines[1] == "boot", lines[2])
    }

    @Synchronized fun run(context: Context): String {
        if (!DeviceCompatibility.isSupportedDevice()) return "Unsupported device"
        val state = read() ?: return "Unable to verify repair status"
        if (state.module) return "Handled by installed module"
        if (!install(context)) return "Unable to prepare repair; refresh status before retrying"
        return RootShell.execForOutput("/system/bin/sh '$BASE/run.sh' manual")?.trim()
            ?: "Completion unknown; refresh status. Do not immediately retry."
    }

    @Synchronized fun setBoot(context: Context, enabled: Boolean): Boolean {
        if (!DeviceCompatibility.isSupportedDevice()) return false
        if (!enabled) return RootShell.exec("rm -f '$BASE/boot_enabled' '$BOOT'")
        val state = read() ?: return false
        if (state.module || !install(context)) return false
        return RootShell.exec("""
            (umask 077
            mkdir -p /data/adb/service.d &&
            cp '$BASE/boot.sh' '$BOOT.new' && chmod 700 '$BOOT.new' &&
            mv -f '$BOOT.new' '$BOOT' && touch '$BASE/boot_enabled')
        """.trimIndent()) && read()?.boot == true
    }

    private fun install(context: Context): Boolean {
        val stage = File(context.noBackupFilesDir, "device-id-repair-assets")
        val names = listOf("KmInstallKeybox", "run.sh", "boot.sh")
        val hashes = runCatching {
            stage.mkdirs()
            names.associateWith { name ->
                val bytes = context.assets.open("device_id_repair/$name").use { it.readBytes() }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                if (name == "KmInstallKeybox") require(hash == HASH)
                File(stage, name).writeBytes(bytes)
                hash
            }
        }.getOrNull() ?: return false
        val commands = names.joinToString("\n") { name ->
            val source = File(stage, name).absolutePath.replace("'", "'\"'\"'")
            "cp '$source' '$BASE/$name.new' && [ \"${'$'}(sha256sum '$BASE/$name.new' | cut -d ' ' -f 1)\" = '${hashes[name]}' ] && chmod 700 '$BASE/$name.new' && mv -f '$BASE/$name.new' '$BASE/$name' || exit 1"
        }
        return RootShell.exec("""
            (umask 077
            [ "${'$'}(id -u)" = 0 ] || exit 1
            [ ! -L '$BASE' ] || exit 1
            mkdir -p '$BASE' && chmod 700 '$BASE' || exit 1
            boot=${'$'}(cat /proc/sys/kernel/random/boot_id) || exit 1
            case "${'$'}boot" in ''|*[!0-9a-f-]*) exit 1 ;; esac
            lock='$BASE/operation-'"${'$'}boot"
            mkdir "${'$'}lock" || exit 1
            trap 'rmdir "${'$'}lock"' EXIT
            $commands)
        """.trimIndent())
    }
}
