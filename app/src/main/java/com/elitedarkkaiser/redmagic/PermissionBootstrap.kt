package com.elitedarkkaiser.redmagic

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

internal data class PermissionBootstrapResult(
    val rootCommandSucceeded: Boolean,
    val usageAccess: Boolean,
    val overlay: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
    val phoneState: Boolean
) {
    val missingPermissions: List<String>
        get() = PermissionBootstrapPolicy.missingPermissionLabels(
            usageAccess = usageAccess,
            overlay = overlay,
            accessibility = accessibility,
            notifications = notifications,
            phoneState = phoneState
        )

    val success: Boolean
        get() = rootCommandSucceeded && missingPermissions.isEmpty()
}

internal object PermissionBootstrap {
    fun apply(context: Context): PermissionBootstrapResult {
        val appContext = context.applicationContext
        val packageName = appContext.packageName
        val serviceName = ComponentName(
            appContext,
            TriggerAccessibilityService::class.java
        ).flattenToString()
        val currentServices = RootShell.execForOutput(
            "settings get secure enabled_accessibility_services"
        )
        if (currentServices == null) {
            return verify(
                context = appContext,
                serviceName = serviceName,
                rootCommandSucceeded = false
            )
        }
        val mergedServices =
            PermissionBootstrapPolicy.mergeAccessibilityServices(
                currentServices,
                serviceName
            )

        val rootCommandSucceeded = RootShell.exec(
            buildCommand(
                packageName = packageName,
                enabledServices = mergedServices
            )
        )

        return verify(
            context = appContext,
            serviceName = serviceName,
            rootCommandSucceeded = rootCommandSucceeded
        )
    }

    private fun verify(
        context: Context,
        serviceName: String,
        rootCommandSucceeded: Boolean
    ): PermissionBootstrapResult {
        val requiredComponent =
            ComponentName.unflattenFromString(serviceName)
        val accessibilityManager = context.getSystemService(
            AccessibilityManager::class.java
        )
        val accessibilityBound = accessibilityManager
            ?.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            ?.any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                ComponentName(
                    serviceInfo.packageName,
                    serviceInfo.name
                ) == requiredComponent
            } == true
        val accessibilityConfigured = RootShell.execForOutput(
            "settings get secure enabled_accessibility_services"
        )
            ?.split(':')
            ?.any { it == serviceName } == true
        val accessibilityEnabled =
            accessibilityBound || accessibilityConfigured

        val notificationsGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        val phoneStateGranted =
            context.checkSelfPermission(
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

        return PermissionBootstrapResult(
            rootCommandSucceeded = rootCommandSucceeded,
            usageAccess =
                PermissionActions.hasUsageStatsPermission(context),
            overlay = Settings.canDrawOverlays(context),
            accessibility = accessibilityEnabled,
            notifications = notificationsGranted,
            phoneState = phoneStateGranted
        )
    }

    private fun buildCommand(
        packageName: String,
        enabledServices: String
    ): String {
        val pkg = shellQuote(packageName)
        val services = shellQuote(enabledServices)

        return buildString {
            /*
             * RootShell normally reuses an interactive shell. Keep failure
             * exits inside a subshell so a rejected permission does not kill
             * that shared session before RootShell can read its status.
             */
            append("(")
            append("appops set $pkg GET_USAGE_STATS allow || exit 20; ")
            append("appops set $pkg SYSTEM_ALERT_WINDOW allow || exit 21; ")
            append(
                "pm grant $pkg android.permission.POST_NOTIFICATIONS " +
                    ">/dev/null 2>&1 || true; "
            )
            append(
                "pm grant $pkg android.permission.READ_PHONE_STATE " +
                    ">/dev/null 2>&1 || true; "
            )
            append(
                "settings put secure enabled_accessibility_services " +
                    "$services || exit 22; "
            )
            append(
                "settings put secure accessibility_enabled 1 || exit 23"
            )
            append(")")
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
