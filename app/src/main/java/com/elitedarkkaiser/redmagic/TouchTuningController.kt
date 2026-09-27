package com.elitedarkkaiser.redmagic

import android.content.Context
import android.provider.Settings

data class TouchTuningProbe(
    val compatible: Boolean,
    val sampleRates: List<Int>,
    val message: String
)

data class TouchTuningResult(
    val success: Boolean,
    val backend: String?,
    val message: String
)

object TouchTuningController {
    private const val SAMPLE_RATE_KEY =
        "NubiaperformanceTouchSampleRate"
    private const val SENSITIVITY_KEY =
        "NubiaperformanceTouchSen"
    private const val FOLLOW_KEY =
        "NubiaperformanceTouchFollow"
    private const val STABILITY_KEY =
        "NubiaperformanceTouchMicroSensitive"
    private const val EDGE_PROTECTION_KEY =
        "PerformanceTouchProtectLev"

    private val settingKeys = listOf(
        SAMPLE_RATE_KEY,
        SENSITIVITY_KEY,
        FOLLOW_KEY,
        STABILITY_KEY,
        EDGE_PROTECTION_KEY
    )

    fun probe(context: Context): TouchTuningProbe {
        if (!DeviceCompatibility.isStockRedmagicFirmware()) {
            return TouchTuningProbe(
                false,
                emptyList(),
                "Stock REDMAGIC Android 16 firmware is required"
            )
        }

        val rates = vendorSampleRates().ifEmpty {
            listOf(480, 960)
        }
        val readable = runCatching {
            settingKeys.forEach {
                Settings.Global.getString(context.contentResolver, it)
            }
        }.isSuccess
        return TouchTuningProbe(
            compatible = readable && rates.isNotEmpty(),
            sampleRates = rates,
            message = if (readable) {
                "Stock per-app touch controller is available"
            } else {
                "Touch-controller settings are unreadable"
            }
        )
    }

    @Synchronized
    fun saveAndApply(
        context: Context,
        profile: TouchTuningProfile
    ): TouchTuningResult {
        val probe = probe(context)
        if (!probe.compatible) {
            return TouchTuningResult(false, null, probe.message)
        }
        if (profile.sampleRateHz !in probe.sampleRates) {
            return TouchTuningResult(
                false,
                null,
                "${profile.sampleRateHz} Hz is not supported by this firmware"
            )
        }

        val values = if (profile.enabled) {
            mapOf(
                SAMPLE_RATE_KEY to profile.sampleRateHz.toString(),
                SENSITIVITY_KEY to profile.sensitivity.toString(),
                FOLLOW_KEY to profile.touchFollow.toString(),
                STABILITY_KEY to profile.stability.toString(),
                EDGE_PROTECTION_KEY to
                    "${if (profile.edgeProtectionEnabled) 1 else 0}" +
                    "&${profile.edgeProtectionLevel}"
            )
        } else {
            emptyMap()
        }
        val writeResult = updateVendorLists(
            context,
            profile.packageName,
            values
        )
        if (!writeResult.success) return writeResult

        if (!TouchTuningStorage.saveProfile(context, profile)) {
            return TouchTuningResult(
                false,
                writeResult.backend,
                "Touch values were applied but the app profile was not saved"
            )
        }
        return TouchTuningResult(
            true,
            writeResult.backend,
            if (profile.enabled) {
                "Touch tuning saved for ${profile.appLabel}"
            } else {
                "Touch tuning disabled for ${profile.appLabel}"
            }
        )
    }

    @Synchronized
    fun remove(
        context: Context,
        profile: TouchTuningProfile
    ): TouchTuningResult {
        val probe = probe(context)
        if (!probe.compatible) {
            return TouchTuningResult(false, null, probe.message)
        }
        val result = updateVendorLists(
            context,
            profile.packageName,
            emptyMap()
        )
        if (!result.success) return result
        if (!TouchTuningStorage.removeProfile(
                context,
                profile.packageName
            )
        ) {
            return TouchTuningResult(
                false,
                result.backend,
                "Vendor values were cleared but the profile was not removed"
            )
        }
        return TouchTuningResult(
            true,
            result.backend,
            "Touch-tuning profile removed"
        )
    }

    @Synchronized
    fun applyAllStored(context: Context): TouchTuningResult {
        return reconcileStored(context, emptySet())
    }

    @Synchronized
    fun reconcileStored(
        context: Context,
        additionallyRemovedPackages: Set<String>
    ): TouchTuningResult {
        val probe = probe(context)
        if (!probe.compatible) {
            return TouchTuningResult(false, null, probe.message)
        }
        val profiles = TouchTuningStorage.readProfiles(context)
        val managedPackages = additionallyRemovedPackages +
            profiles.map { it.packageName }
        val desired = settingKeys.associateWith { key ->
            val untouched = Settings.Global.getString(
                context.contentResolver,
                key
            ).orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { entry ->
                    entry.isNotEmpty() &&
                        managedPackages.none { pkg ->
                            entry.startsWith("$pkg+")
                        }
                }
                .toMutableList()

            profiles.filter { it.enabled }.forEach { profile ->
                untouched += "${profile.packageName}+" +
                    valueForKey(profile, key)
            }
            if (untouched.isEmpty()) "" else untouched.joinToString(
                separator = ",",
                postfix = ","
            )
        }
        return writeDesiredSettings(
            context,
            desired,
            "Touch-tuning profiles restored"
        )
    }

    private fun updateVendorLists(
        context: Context,
        packageName: String,
        profileValues: Map<String, String>
    ): TouchTuningResult {
        if (!PACKAGE_PATTERN.matches(packageName)) {
            return TouchTuningResult(false, null, "Invalid app package")
        }

        val desired = settingKeys.associateWith { key ->
            updatePackageEntry(
                Settings.Global.getString(context.contentResolver, key),
                packageName,
                profileValues[key]
            )
        }
        return writeDesiredSettings(
            context,
            desired,
            "Touch-controller settings updated"
        )
    }

    private fun writeDesiredSettings(
        context: Context,
        desired: Map<String, String>,
        successMessage: String
    ): TouchTuningResult {
        val changed = desired.filter { (key, value) ->
            Settings.Global.getString(context.contentResolver, key)
                .orEmpty() != value
        }
        if (changed.isEmpty()) {
            return TouchTuningResult(
                true,
                "No write required",
                "Touch-controller values are already current"
            )
        }

        val directFailures = changed.filter { (key, value) ->
            !runCatching {
                Settings.Global.putString(
                    context.contentResolver,
                    key,
                    value
                )
            }.getOrDefault(false)
        }

        if (directFailures.isNotEmpty()) {
            val command = directFailures.entries.joinToString(" && ") {
                (key, value) ->
                "/system/bin/settings put global " +
                    shellQuote(key) + " " + shellQuote(value)
            }
            if (!RootShell.exec(command)) {
                return TouchTuningResult(
                    false,
                    null,
                    "Unable to update the protected touch-controller settings"
                )
            }
        }

        val verified = desired.all { (key, value) ->
            Settings.Global.getString(context.contentResolver, key)
                .orEmpty() == value
        }
        if (!verified) {
            return TouchTuningResult(
                false,
                if (directFailures.isEmpty()) {
                    "Android Settings API"
                } else {
                    "Root settings fallback"
                },
                "Touch-controller settings could not be verified"
            )
        }

        return TouchTuningResult(
            true,
            if (directFailures.isEmpty()) {
                "Android Settings API"
            } else {
                "Root settings fallback"
            },
            successMessage
        )
    }

    private fun valueForKey(
        profile: TouchTuningProfile,
        key: String
    ): String {
        return when (key) {
            SAMPLE_RATE_KEY -> profile.sampleRateHz.toString()
            SENSITIVITY_KEY -> profile.sensitivity.toString()
            FOLLOW_KEY -> profile.touchFollow.toString()
            STABILITY_KEY -> profile.stability.toString()
            EDGE_PROTECTION_KEY ->
                "${if (profile.edgeProtectionEnabled) 1 else 0}" +
                    "&${profile.edgeProtectionLevel}"
            else -> error("Unknown touch setting")
        }
    }

    private fun updatePackageEntry(
        raw: String?,
        packageName: String,
        value: String?
    ): String {
        val prefix = "$packageName+"
        val entries = raw.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(prefix) }
            .toMutableList()
        value?.let { entries += prefix + it }
        return if (entries.isEmpty()) "" else entries.joinToString(
            separator = ",",
            postfix = ","
        )
    }

    private fun vendorSampleRates(): List<Int> {
        val configured = runCatching {
            val feature = Class.forName("com.zte.feature.Feature")
            feature.getMethod(
                "get",
                String::class.java,
                String::class.java
            ).invoke(
                null,
                "ZTE_TOUCH_RATE_GEAR_CONFIGURATION",
                ""
            ) as? String
        }.getOrNull().orEmpty()

        return configured.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in TouchTuningStorage.ALLOWED_SAMPLE_RATES }
            .distinct()
            .sorted()
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private val PACKAGE_PATTERN = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )
}
