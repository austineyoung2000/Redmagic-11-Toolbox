package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionBootstrapPolicyTest {
    private val required =
        "com.elitedarkkaiser.redmagic/.TriggerAccessibilityService"

    @Test
    fun mergePreservesExistingServicesAndAppendsRequiredService() {
        val merged = PermissionBootstrapPolicy.mergeAccessibilityServices(
            "com.password/.Service:com.automation/.Service",
            required
        )

        assertEquals(
            "com.password/.Service:com.automation/.Service:$required",
            merged
        )
    }

    @Test
    fun mergeDoesNotDuplicateRequiredService() {
        val merged = PermissionBootstrapPolicy.mergeAccessibilityServices(
            "com.password/.Service:$required:$required",
            required
        )

        assertEquals("com.password/.Service:$required", merged)
    }

    @Test
    fun mergeTreatsNullSettingAsEmpty() {
        assertEquals(
            required,
            PermissionBootstrapPolicy.mergeAccessibilityServices(
                "null",
                required
            )
        )
    }

    @Test
    fun missingLabelsReportsOnlyFailedPermissions() {
        assertEquals(
            listOf("Display over other apps", "Phone state"),
            PermissionBootstrapPolicy.missingPermissionLabels(
                usageAccess = true,
                overlay = false,
                accessibility = true,
                notifications = true,
                phoneState = false
            )
        )
    }
}
