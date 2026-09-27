package com.elitedarkkaiser.redmagic

internal object PermissionBootstrapPolicy {
    fun mergeAccessibilityServices(
        currentValue: String?,
        requiredService: String
    ): String {
        return buildList {
            currentValue
                .orEmpty()
                .split(':')
                .map(String::trim)
                .filter { it.isNotEmpty() && it != "null" }
                .forEach(::add)

            add(requiredService.trim())
        }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(":")
    }

    fun missingPermissionLabels(
        usageAccess: Boolean,
        overlay: Boolean,
        accessibility: Boolean,
        notifications: Boolean,
        phoneState: Boolean
    ): List<String> {
        return buildList {
            if (!usageAccess) add("Usage Access")
            if (!overlay) add("Display over other apps")
            if (!accessibility) add("Accessibility service")
            if (!notifications) add("Notifications")
            if (!phoneState) add("Phone state")
        }
    }
}
