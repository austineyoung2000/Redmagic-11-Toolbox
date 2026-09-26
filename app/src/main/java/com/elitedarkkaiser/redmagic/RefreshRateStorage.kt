package com.elitedarkkaiser.redmagic

import android.content.Context
import android.hardware.display.DisplayManager
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

data class RefreshRateProfile(
    val packageName: String,
    val appLabel: String,
    val refreshRateHz: Int,
    val enabled: Boolean = true,
    val pendingReset: Boolean = false
)

object RefreshRateStorage {
    private const val PREFS = "refresh_rate_profiles"
    private const val KEY = "profiles_json"
    private const val FORMAT = "redmagic-refresh-rate-profiles"
    private const val VERSION = 1
    private const val MAX_IMPORT_SIZE = 1_000_000

    private val packagePattern = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )

    @Synchronized
    fun readProfiles(context: Context): List<RefreshRateProfile> {
        val raw = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getString(KEY, null) ?: return emptyList()

        return runCatching {
            val array = JSONObject(raw).optJSONArray("profiles")
                ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)
                        ?.toProfile()
                        ?.let(::add)
                }
            }.distinctBy { it.packageName }
                .sortedBy { it.appLabel.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun getProfile(
        context: Context,
        packageName: String
    ): RefreshRateProfile? {
        return readProfiles(context).firstOrNull {
            it.packageName == packageName
        }
    }

    @Synchronized
    fun saveProfile(
        context: Context,
        profile: RefreshRateProfile
    ): Boolean {
        if (!profile.isValid()) return false

        val updated = readProfiles(context)
            .filterNot { it.packageName == profile.packageName }
            .plus(profile)
            .sortedBy { it.appLabel.lowercase() }
        return writeProfiles(context, updated)
    }

    @Synchronized
    fun removeProfile(
        context: Context,
        packageName: String
    ): Boolean {
        val profiles = readProfiles(context)
        val target = profiles.firstOrNull {
            it.packageName == packageName
        } ?: return true
        val updated = profiles.filterNot {
            it.packageName == packageName
        }.toMutableList()

        /*
         * The vendor service persists per-package choices. Keep a
         * hidden tombstone until the app next becomes foreground so
         * the coordinator can safely write Follow system (0).
         */
        if (target.refreshRateHz != 0) {
            updated += target.copy(
                enabled = false,
                pendingReset = true
            )
        }
        return writeProfiles(context, updated)
    }

    @Synchronized
    fun finishPendingReset(
        context: Context,
        packageName: String
    ): Boolean {
        return writeProfiles(
            context,
            readProfiles(context).filterNot {
                it.packageName == packageName &&
                    it.pendingReset
            }
        )
    }

    fun supportedRates(context: Context): List<Int> {
        val manager = context.getSystemService(
            Context.DISPLAY_SERVICE
        ) as? DisplayManager ?: return emptyList()

        return manager.displays
            .asSequence()
            .flatMap { display ->
                display.supportedModes.asSequence()
            }
            .map { it.refreshRate.roundToInt() }
            .filter { it in 30..360 }
            .distinct()
            .sorted()
            .toList()
    }

    fun createExportJson(
        context: Context,
        allowEmpty: Boolean = false
    ): String {
        val profiles = readProfiles(context)
        require(allowEmpty || profiles.isNotEmpty()) {
            "No refresh-rate profiles are available to export"
        }

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put(
                "profiles",
                JSONArray().apply {
                    profiles.forEach { put(it.toJson()) }
                }
            )
            .toString()
    }

    @Synchronized
    fun importProfilesJson(
        context: Context,
        raw: String,
        replaceAll: Boolean
    ) {
        require(raw.length <= MAX_IMPORT_SIZE) {
            "Refresh-rate profile data is too large"
        }
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) {
            "Invalid refresh-rate profile data"
        }
        require(root.optInt("version", 0) in 1..VERSION) {
            "Unsupported refresh-rate profile version"
        }

        val imported = buildList {
            val array = root.optJSONArray("profiles") ?: JSONArray()
            require(array.length() <= 500) {
                "Invalid refresh-rate profile count"
            }
            for (index in 0 until array.length()) {
                array.optJSONObject(index)
                    ?.toProfile()
                    ?.let(::add)
            }
        }

        val merged = if (replaceAll) {
            mutableMapOf<String, RefreshRateProfile>()
        } else {
            readProfiles(context).associateBy {
                it.packageName
            }.toMutableMap()
        }
        imported.forEach { merged[it.packageName] = it }
        check(
            writeProfiles(
                context,
                merged.values.sortedBy {
                    it.appLabel.lowercase()
                }
            )
        ) { "Unable to save refresh-rate profiles" }
    }

    private fun writeProfiles(
        context: Context,
        profiles: List<RefreshRateProfile>
    ): Boolean {
        val root = JSONObject()
            .put("version", VERSION)
            .put(
                "profiles",
                JSONArray().apply {
                    profiles.forEach { put(it.toJson()) }
                }
            )
        return context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit().putString(KEY, root.toString()).commit()
    }

    private fun RefreshRateProfile.isValid(): Boolean {
        return packagePattern.matches(packageName) &&
            appLabel.isNotBlank() &&
            refreshRateHz in 0..360
    }

    private fun RefreshRateProfile.toJson(): JSONObject {
        return JSONObject()
            .put("packageName", packageName)
            .put("appLabel", appLabel)
            .put("refreshRateHz", refreshRateHz)
            .put("enabled", enabled)
            .put("pendingReset", pendingReset)
    }

    private fun JSONObject.toProfile(): RefreshRateProfile? {
        val profile = RefreshRateProfile(
            packageName = optString("packageName"),
            appLabel = optString("appLabel"),
            refreshRateHz = optInt("refreshRateHz", -1),
            enabled = optBoolean("enabled", true),
            pendingReset = optBoolean("pendingReset", false)
        )
        return profile.takeIf { it.isValid() }
    }
}
