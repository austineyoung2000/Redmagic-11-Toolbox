package com.elitedarkkaiser.redmagic

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.util.AtomicFile
import java.io.File

/** Event-only handoff to the independent root module. Contains no notification data. */
internal object NotificationListenerRecoveryState {
    private var lastWritten: String? = null
    @Synchronized fun publish(context: Context, connected: Boolean) {
        runCatching {
            val access = context.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(context, NotificationLightingService::class.java))
            val desired = DeviceCompatibility.isSupportedDevice() && NotificationLightingState.enabled(context) && access
            val boot = File("/proc/sys/kernel/random/boot_id").readText().trim()
            val stat = File("/proc/self/stat").readText().substringAfterLast(") ").split(' ')
            val content = "version=1\nboot=$boot\npid=${Process.myPid()}\nuid=${Process.myUid()}\nstart=${stat[19]}\ndesired=$desired\nconnected=$connected\n"
            if (content == lastWritten) return
            val file = AtomicFile(File(context.noBackupFilesDir, "notification-listener-recovery"))
            val output = file.startWrite()
            try {
                output.write(content.toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
                lastWritten = content
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        }.onFailure { android.util.Log.w("NotificationLighting", "Cannot publish module recovery state", it) }
    }
}
