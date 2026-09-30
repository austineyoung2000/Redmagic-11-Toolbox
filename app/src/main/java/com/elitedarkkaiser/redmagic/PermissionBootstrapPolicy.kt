package com.elitedarkkaiser.redmagic

internal object PermissionBootstrapPolicy {
    fun mergeAccessibilityServices(
        currentValue: String?,
        requiredService: String
    ): String {
        val seen = mutableSetOf<String>()

        return buildList {
            currentValue
                .orEmpty()
                .split(':')
                .map(String::trim)
                .filter { it.isNotEmpty() && it != "null" }
                .forEach { service ->
                    if (seen.add(canonicalServiceName(service))) {
                        add(service)
                    }
                }

            val required = requiredService.trim()
            if (
                required.isNotEmpty() &&
                seen.add(canonicalServiceName(required))
            ) {
                add(required)
            }
        }
            .joinToString(":")
    }

    fun containsAccessibilityService(
        currentValue: String?,
        requiredService: String
    ): Boolean {
        val required = canonicalServiceName(requiredService)
        return currentValue
            .orEmpty()
            .split(':')
            .map(String::trim)
            .filter { it.isNotEmpty() && it != "null" }
            .any { canonicalServiceName(it) == required }
    }

    private fun canonicalServiceName(value: String): String {
        val trimmed = value.trim()
        val separator = trimmed.indexOf('/')
        if (separator <= 0 || separator == trimmed.lastIndex) {
            return trimmed
        }

        val packageName = trimmed.substring(0, separator)
        val className = trimmed.substring(separator + 1)
        val expandedClass = if (className.startsWith('.')) {
            packageName + className
        } else {
            className
        }
        return "$packageName/$expandedClass"
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
