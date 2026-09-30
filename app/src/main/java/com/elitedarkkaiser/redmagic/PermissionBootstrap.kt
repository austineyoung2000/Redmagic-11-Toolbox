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
        get() = missingPermissions.isEmpty()

    fun statusMessage(): String {
        fun status(granted: Boolean): String =
            if (granted) "Granted" else "Missing"

        return buildString {
            append("Usage Access: ${status(usageAccess)}\n")
            append("Display over other apps: ${status(overlay)}\n")
            append("Accessibility service: ${status(accessibility)}\n")
            append("Notifications: ${status(notifications)}\n")
            append("Phone state: ${status(phoneState)}")

            if (!rootCommandSucceeded) {
                append("\n\nRoot automation reported an error. " +
                    "The verified results above show what still needs attention.")
            }
        }
    }
}

internal object PermissionBootstrap {
    fun inspect(context: Context): PermissionBootstrapResult {
        val appContext = context.applicationContext
        val serviceName = ComponentName(
            appContext,
            TriggerAccessibilityService::class.java
        ).flattenToShortString()

        return verify(
            context = appContext,
            serviceName = serviceName,
            rootCommandSucceeded = true
        )
    }

    fun apply(context: Context): PermissionBootstrapResult {
        val appContext = context.applicationContext
        val packageName = appContext.packageName
        val serviceName = ComponentName(
            appContext,
            TriggerAccessibilityService::class.java
        ).flattenToShortString()

        val rootCommandSucceeded = RootShell.exec(
            buildCommand(
                packageName = packageName,
                serviceName = serviceName,
                grantNotifications =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            )
        )

        // Settings and AppOps updates can take a moment to become visible.
        Thread.sleep(250)

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
        val configuredServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val accessibilityMasterEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        val accessibilityConfigured = accessibilityMasterEnabled &&
            PermissionBootstrapPolicy.containsAccessibilityService(
                configuredServices,
                serviceName
            )
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
        serviceName: String,
        grantNotifications: Boolean
    ): String {
        val pkg = shellQuote(packageName)
        val service = shellQuote(serviceName)
        val fullService = shellQuote(
            serviceName.substringBefore('/') + "/" +
                serviceName.substringBefore('/') + "." +
                serviceName.substringAfter('/').removePrefix(".")
        )

        return buildString {
            /*
             * RootShell normally reuses an interactive shell. Keep failure
             * exits inside a subshell so a rejected permission does not kill
             * that shared session before RootShell can read its status.
             */
            append(
                "(redmagic_failed=0; " +
                    "redmagic_required=$service; " +
                    "redmagic_required_full=$fullService; "
            )
            append(
                "appops set $pkg GET_USAGE_STATS allow " +
                    ">/dev/null 2>&1 || redmagic_failed=1; "
            )
            append(
                "appops set $pkg SYSTEM_ALERT_WINDOW allow " +
                    ">/dev/null 2>&1 || redmagic_failed=1; "
            )
            if (grantNotifications) {
                append(
                    "pm grant $pkg android.permission.POST_NOTIFICATIONS " +
                        ">/dev/null 2>&1 || redmagic_failed=1; "
                )
            }
            append(
                "pm grant $pkg android.permission.READ_PHONE_STATE " +
                    ">/dev/null 2>&1 || redmagic_failed=1; "
            )
            append(
                "if redmagic_services=\$(settings get secure " +
                    "enabled_accessibility_services 2>/dev/null); then " +
                    "[ \"\$redmagic_services\" = null ] && " +
                    "redmagic_services=; " +
                    "case \":\$redmagic_services:\" in " +
                    "*:\"\$redmagic_required\":*|" +
                    "*:\"\$redmagic_required_full\":*) ;; " +
                    "*) redmagic_services=\"\${redmagic_services:+" +
                    "\$redmagic_services:}\$redmagic_required\" ;; esac; " +
                    "settings put secure enabled_accessibility_services " +
                    "\"\$redmagic_services\" >/dev/null 2>&1 || " +
                    "redmagic_failed=1; " +
                    "else redmagic_failed=1; fi; "
            )
            append(
                "settings put secure accessibility_enabled 1 " +
                    ">/dev/null 2>&1 || redmagic_failed=1; " +
                    "exit \$redmagic_failed)"
            )
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
