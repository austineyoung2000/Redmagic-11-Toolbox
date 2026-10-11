package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.SystemClock
import android.os.UserManager
import java.io.File

internal object BootDiagnostics {
    private const val PREFS = "boot_diagnostics"
    private fun bootId() = runCatching {
        File("/proc/sys/kernel/random/boot_id").readText().trim()
    }.getOrDefault("unknown")

    @Synchronized
    fun record(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = bootId()
        val previous = if (prefs.getString("boot_id", null) == id)
            prefs.getString("events", "").orEmpty() else ""
        val line = "${SystemClock.elapsedRealtime() / 1000}s: $message"
        prefs.edit().putString("boot_id", id)
            .putString("events", (previous.lines().filter { it.isNotBlank() } + line).takeLast(80).joinToString("\n"))
            .apply()
    }

    fun request(context: Context, name: String, action: () -> Unit) {
        try {
            action()
            record(context, "$name: startup requested (not proof of running)")
        } catch (e: Exception) {
            record(context, "$name: startup failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    fun buildReport(context: Context): String = buildString {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        appendLine("REDMAGIC BOOT DIAGNOSTICS")
        val version = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("Installed app: ${version.versionName} (${version.longVersionCode})")
        appendLine("Current boot: ${bootId()}")
        appendLine("Uptime: ${SystemClock.elapsedRealtime() / 1000}s")
        appendLine("User unlocked: ${context.getSystemService(UserManager::class.java)?.isUserUnlocked}")
        appendLine("Screen awake: ${LedScreenPolicy.isScreenInteractive(context)}")
        appendLine("LED owner: ${LedOwnership.current(context)}")
        appendLine("LED root safety: ${LightingRootExecutor.status()}")
        appendLine("Notification deadline: ${NotificationWindowDeadline.status(context)}")
        appendLine("Notification lighting enabled: ${NotificationLightingState.enabled(context)}")
        val notificationFile = File(context.applicationInfo.dataDir, "shared_prefs/notification_lighting.xml")
        appendLine("Notification settings file: ${notificationFile.absolutePath}; exists=${notificationFile.exists()}")
        val listener = android.content.ComponentName(context, NotificationLightingService::class.java)
        appendLine("Notification listener access: ${context.getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(listener)}")
        val notificationApps = NotificationLightingState.packages(context)
        appendLine("Notification profiles: ${notificationApps.size}")
        notificationApps.forEach { pkg ->
            val profile = NotificationLightingState.read(context, pkg)
            appendLine("  $pkg: ${profile?.let { "seconds=${it.seconds}, effect=${it.effect}, color=${it.color}, logo=${it.logo}, triggers=${it.triggers}, fan=${it.fan}" } ?: "INVALID saved profile"}")
        }
        appendLine("Auto fan configured: ${isAutoFanEnabledStorage(context)}")
        appendLine("Auto pump configured: ${savedPumpStateStorage(context).autoEnabled}")
        appendLine("RGB Studio configured: ${RgbStudioStorage.isEnabled(context)}")
        appendLine("Call lighting configured: ${CallLightingState.isEnabled(context)}")
        appendLine("Charging lighting configured: ${ChargingLedState.isEnabled(context)}")
        appendLine("Charging now: ${ChargingLedState.isChargingNow(context)}")
        appendLine("Triggers auto-start: ${readTriggerPrefsSnapshot(context).triggersAutoStart}")
        appendLine("\nRECORDED STARTUP EVENTS")
        appendLine("Recorded boot: ${prefs.getString("boot_id", "none")}")
        appendLine(prefs.getString("events", "No startup events recorded yet. Reboot with this build installed."))
        appendLine("\nCURRENT APP SERVICES (snapshot; started does not mean hardware is on)")
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val services = manager.getRunningServices(100).filter { it.service.packageName == context.packageName }
        appendLine("LED screen/power guard present: ${services.any { it.service.className == FanLedService::class.java.name && it.started }}")
        if (services.isEmpty()) appendLine("No services visible to ActivityManager.")
        services.forEach { appendLine("${it.service.shortClassName}: pid=${it.pid}, started=${it.started}, foreground=${it.foreground}") }
        appendLine("\nROOT BOOT HELPER — latest attempt; may be from a previous boot")
        LightingRootExecutor.initialize(context)
        appendLine(LightingRootExecutor.output("if [ -f /data/adb/redmagic_trigger_bridge/toolbox-boot.log ]; then tail -n 120 /data/adb/redmagic_trigger_bridge/toolbox-boot.log; else echo 'Boot helper log not present'; fi") ?: "Root read unavailable or failed.")
    }
}
