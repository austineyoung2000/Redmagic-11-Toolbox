package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Base64
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs a minimal KernelSU/Magisk/APatch service.d supervisor. REDMAGIC
 * firmware kills every process owned by the application UID at once, so an
 * in-APK watchdog cannot be the final recovery boundary. The root-owned
 * supervisor performs no hardware writes and only starts the existing
 * watchdog foreground service after that process disappears.
 */
internal object GameplayRuntimeSupervisor {
    private const val TAG = "RedmagicGameplaySupervisor"
    private const val ASSET_NAME =
        "redmagic_gameplay_supervisor.sh"
    private const val SERVICE_DIRECTORY = "/data/adb/service.d"
    private const val SERVICE_PATH =
        "$SERVICE_DIRECTORY/redmagic_gameplay_supervisor.sh"
    private const val STATE_DIRECTORY =
        "/data/adb/redmagic_toolbox"
    private const val PID_FILE =
        "$STATE_DIRECTORY/gameplay_supervisor.lock/pid"
    private const val REQUIRED_VERSION = "2"

    private val installQueued = AtomicBoolean(false)
    private val installer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RedmagicSupervisorInstaller").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    internal fun ensureInstalled(context: Context) {
        if (
            !DeviceCompatibility.isSupportedDevice() ||
            !GameplayRuntimeService.isAccessibilityConfigured(context) ||
            !installQueued.compareAndSet(false, true)
        ) {
            return
        }

        val appContext = context.applicationContext
        runCatching {
            installer.execute {
                try {
                    installOrStart(appContext)
                } finally {
                    installQueued.set(false)
                }
            }
        }.onFailure {
            installQueued.set(false)
            Log.e(TAG, "Unable to queue supervisor installation", it)
        }
    }

    private fun installOrStart(context: Context) {
        val installedVersion = RootShell.execForOutput(
            "sed -n 's/^SUPERVISOR_VERSION=//p' " +
                "$SERVICE_PATH 2>/dev/null | head -n 1"
        )?.trim()

        if (installedVersion != REQUIRED_VERSION) {
            val encodedScript = runCatching {
                context.assets.open(ASSET_NAME).use { input ->
                    Base64.encodeToString(
                        input.readBytes(),
                        Base64.NO_WRAP
                    )
                }
            }.getOrElse {
                Log.e(TAG, "Unable to read supervisor asset", it)
                return
            }

            val installed = RootShell.exec(
                buildInstallCommand(encodedScript)
            )
            if (!installed) {
                Log.e(TAG, "Unable to install root supervisor")
                return
            }
            Log.i(TAG, "Installed gameplay recovery supervisor")
        }

        if (!RootShell.exec(buildStartCommand())) {
            Log.e(TAG, "Unable to start root supervisor")
        }
    }

    private fun buildInstallCommand(encodedScript: String): String {
        return """
            redmagic_install_supervisor() {
                service_dir='$SERVICE_DIRECTORY'
                service_path='$SERVICE_PATH'
                state_dir='$STATE_DIRECTORY'
                temporary_path='${SERVICE_PATH}.new'
                mkdir -p "${'$'}service_dir" "${'$'}state_dir" || return 1
                chmod 0700 "${'$'}state_dir" || return 1
                printf '%s' '$encodedScript' |
                    base64 -d > "${'$'}temporary_path" || return 1
                chmod 0755 "${'$'}temporary_path" || return 1
                old_pid="${'$'}(cat '$PID_FILE' 2>/dev/null)"
                if [ -n "${'$'}old_pid" ]; then
                    kill "${'$'}old_pid" 2>/dev/null || true
                    sleep 1
                fi
                mv -f "${'$'}temporary_path" "${'$'}service_path"
            }
            redmagic_install_supervisor
            redmagic_status="${'$'}?"
            unset -f redmagic_install_supervisor
            (exit "${'$'}redmagic_status")
        """.trimIndent()
    }

    private fun buildStartCommand(): String {
        return """
            redmagic_start_supervisor() {
                service_path='$SERVICE_PATH'
                supervisor_pid="${'$'}(cat '$PID_FILE' 2>/dev/null)"
                if [ -n "${'$'}supervisor_pid" ] &&
                    kill -0 "${'$'}supervisor_pid" 2>/dev/null; then
                    return 0
                fi
                nohup "${'$'}service_path" \
                    </dev/null >/dev/null 2>&1 &
            }
            redmagic_start_supervisor
            redmagic_status="${'$'}?"
            unset -f redmagic_start_supervisor
            (exit "${'$'}redmagic_status")
        """.trimIndent()
    }
}
