package com.elitedarkkaiser.redmagic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TouchTuningProfile(
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val sampleRateHz: Int = 480,
    val sensitivity: Int = 0,
    val touchFollow: Int = 0,
    val stability: Int = 0,
    val edgeProtectionEnabled: Boolean = true,
    val edgeProtectionLevel: Int = 0
)

object TouchTuningStorage {
    private const val PREFS = "touch_tuning_profiles"
    private const val KEY = "profiles_json"
    private const val FORMAT = "redmagic-touch-tuning-profiles"
    private const val VERSION = 1

    private val packagePattern = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )

    @Synchronized
    fun readProfiles(context: Context): List<TouchTuningProfile> {
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
    ): TouchTuningProfile? {
        return readProfiles(context).firstOrNull {
            it.packageName == packageName
        }
    }

    @Synchronized
    fun saveProfile(
        context: Context,
        profile: TouchTuningProfile
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
            "No touch-tuning profiles are available to export"
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

    internal fun validateBackupJson(raw: String) {
        require(raw.length <= 1_000_000) {
            "Touch-tuning profile data is too large"
        }
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) {
            "Invalid touch-tuning profile data"
        }
        require(root.optInt("version", 0) in 1..VERSION) {
            "Unsupported touch-tuning profile version"
        }
        val array = root.getJSONArray("profiles")
        require(array.length() <= 500) { "Too many backed-up app profiles" }
        for (index in 0 until array.length()) {
            require(array.getJSONObject(index).toProfile() != null) { "Invalid backed-up app profile" }
        }
    }

    @Synchronized
    fun importProfilesJson(
        context: Context,
        raw: String,
        replaceAll: Boolean
    ) {
        require(raw.length <= 1_000_000) {
            "Touch-tuning profile data is too large"
        }
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) {
            "Invalid touch-tuning profile data"
        }
        require(root.optInt("version", 0) in 1..VERSION) {
            "Unsupported touch-tuning profile version"
        }
        val imported = buildList {
            val array = root.optJSONArray("profiles") ?: JSONArray()
            require(array.length() <= 500) {
                "Invalid touch-tuning profile count"
            }
            for (index in 0 until array.length()) {
                array.optJSONObject(index)
                    ?.toProfile()
                    ?.let(::add)
            }
        }
        val merged = if (replaceAll) {
            mutableMapOf<String, TouchTuningProfile>()
        } else {
            readProfiles(context).associateBy {
                it.packageName
            }.toMutableMap()
        }
        imported.forEach { merged[it.packageName] = it }
        check(writeProfiles(context, merged.values.toList())) {
            "Unable to save touch-tuning profiles"
        }
    }

    private fun writeProfiles(
        context: Context,
        profiles: List<TouchTuningProfile>
    ): Boolean {
        val root = JSONObject().put(
            "profiles",
            JSONArray().apply {
                profiles.sortedBy { it.appLabel.lowercase() }
                    .forEach { put(it.toJson()) }
            }
        )
        return context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit().putString(KEY, root.toString()).commit()
    }

    private fun TouchTuningProfile.isValid(): Boolean {
        return packagePattern.matches(packageName) &&
            appLabel.isNotBlank() &&
            sampleRateHz in ALLOWED_SAMPLE_RATES &&
            sensitivity in -2..2 &&
            touchFollow in -2..2 &&
            stability in -2..2 &&
            edgeProtectionLevel in -1..1
    }

    private fun TouchTuningProfile.toJson(): JSONObject {
        return JSONObject()
            .put("packageName", packageName)
            .put("appLabel", appLabel)
            .put("enabled", enabled)
            .put("sampleRateHz", sampleRateHz)
            .put("sensitivity", sensitivity)
            .put("touchFollow", touchFollow)
            .put("stability", stability)
            .put("edgeProtectionEnabled", edgeProtectionEnabled)
            .put("edgeProtectionLevel", edgeProtectionLevel)
    }

    private fun JSONObject.toProfile(): TouchTuningProfile? {
        val profile = TouchTuningProfile(
            packageName = optString("packageName"),
            appLabel = optString("appLabel"),
            enabled = optBoolean("enabled", true),
            sampleRateHz = optInt("sampleRateHz", 480),
            sensitivity = optInt("sensitivity", 0),
            touchFollow = optInt("touchFollow", 0),
            stability = optInt("stability", 0),
            edgeProtectionEnabled = optBoolean(
                "edgeProtectionEnabled",
                true
            ),
            edgeProtectionLevel = optInt("edgeProtectionLevel", 0)
        )
        return profile.takeIf { it.isValid() }
    }

    val ALLOWED_SAMPLE_RATES = setOf(120, 240, 360, 480, 960)
}
