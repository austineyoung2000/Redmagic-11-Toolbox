package com.elitedarkkaiser.redmagic

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

object PermissionActions {
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow("android:get_usage_stats", Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow("android:get_usage_stats", Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    internal fun openSettingsForFirstMissing(
        context: Context,
        result: PermissionBootstrapResult
    ) {
        val intent = when {
            !result.usageAccess ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            !result.overlay ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            !result.accessibility ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            !result.notifications &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        context.packageName
                    )
                }
            else ->
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
        }

        context.startActivity(
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
