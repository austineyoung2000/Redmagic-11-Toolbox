package com.elitedarkkaiser.redmagic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class RedmagicPerformanceMode(
    val value: Int,
    val label: String
) {
    ECO(1, "Eco"),
    BALANCE(2, "Balance"),
    RISE(3, "Rise");

    companion object {
        fun fromValue(value: Int): RedmagicPerformanceMode? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

data class PerformanceModeProfile(
    val packageName: String,
    val appLabel: String,
    val mode: RedmagicPerformanceMode,
    val enabled: Boolean = true
)

object PerformanceModeStorage {
    private const val PREFS = "performance_mode_profiles"
    private const val KEY = "profiles_json"
    private const val FORMAT = "redmagic-performance-mode-profiles"
    private const val VERSION = 1
    private const val MAX_IMPORT_SIZE = 1_000_000

    private val packagePattern = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )

    @Synchronized
    fun readProfiles(context: Context): List<PerformanceModeProfile> {
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
    ): PerformanceModeProfile? {
        return readProfiles(context).firstOrNull {
            it.packageName == packageName
        }
    }

    @Synchronized
    fun saveProfile(
        context: Context,
        profile: PerformanceModeProfile
    ): Boolean {
        if (!profile.isValid()) return false

        return writeProfiles(
            context,
            readProfiles(context)
                .filterNot { it.packageName == profile.packageName }
                .plus(profile)
                .sortedBy { it.appLabel.lowercase() }
        )
    }

    @Synchronized
    fun removeProfile(
        context: Context,
        packageName: String
    ): Boolean {
        return writeProfiles(
            context,
            readProfiles(context).filterNot {
                it.packageName == packageName
            }
        )
    }

    fun createExportJson(
        context: Context,
        allowEmpty: Boolean = false
    ): String {
        val profiles = readProfiles(context)
        require(allowEmpty || profiles.isNotEmpty()) {
            "No performance-mode profiles are available to export"
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
            "Performance-mode profile data is too large"
        }
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) {
            "Invalid performance-mode profile data"
        }
        require(root.optInt("version", 0) in 1..VERSION) {
            "Unsupported performance-mode profile version"
        }

        val imported = buildList {
            val array = root.optJSONArray("profiles") ?: JSONArray()
            require(array.length() <= 500) {
                "Invalid performance-mode profile count"
            }
            for (index in 0 until array.length()) {
                array.optJSONObject(index)
                    ?.toProfile()
                    ?.let(::add)
            }
        }

        val merged = if (replaceAll) {
            mutableMapOf<String, PerformanceModeProfile>()
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
        ) { "Unable to save performance-mode profiles" }
    }

    private fun writeProfiles(
        context: Context,
        profiles: List<PerformanceModeProfile>
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

    private fun PerformanceModeProfile.isValid(): Boolean {
        return packagePattern.matches(packageName) &&
            appLabel.isNotBlank()
    }

    private fun PerformanceModeProfile.toJson(): JSONObject {
        return JSONObject()
            .put("packageName", packageName)
            .put("appLabel", appLabel)
            .put("mode", mode.value)
            .put("enabled", enabled)
    }

    private fun JSONObject.toProfile(): PerformanceModeProfile? {
        val mode = RedmagicPerformanceMode.fromValue(
            optInt("mode", -1)
        ) ?: return null
        val profile = PerformanceModeProfile(
            packageName = optString("packageName"),
            appLabel = optString("appLabel"),
            mode = mode,
            enabled = optBoolean("enabled", true)
        )
        return profile.takeIf { it.isValid() }
    }
}
