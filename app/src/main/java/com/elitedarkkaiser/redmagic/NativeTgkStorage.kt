package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

enum class NativeTgkOrientation {
    PORTRAIT,
    LANDSCAPE
}

enum class NativeTgkTriggerBehavior(
    val vendorMode: Int
) {
    SINGLE_TOUCH(0),
    LONG_PRESS(5),
    RAPID_FIRE(6)
}

data class NativeTgkRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val captureWidth: Int,
    val captureHeight: Int
) {
    fun isValid(): Boolean {
        return captureWidth > 1 &&
            captureHeight > 1 &&
            left >= 0 &&
            top >= 0 &&
            right > left &&
            bottom > top &&
            right <= captureWidth &&
            bottom <= captureHeight
    }

    fun scaledTo(
        displayWidth: Int,
        displayHeight: Int
    ): IntArray {
        require(isValid()) {
            "Invalid stored TGK rectangle"
        }
        require(displayWidth > 1 && displayHeight > 1) {
            "Invalid target display size"
        }

        fun scale(
            value: Int,
            sourceSize: Int,
            targetSize: Int
        ): Int {
            return (
                value.toLong() * targetSize.toLong() /
                    sourceSize.toLong()
                ).toInt()
        }

        val scaledLeft = scale(
            left,
            captureWidth,
            displayWidth
        ).coerceIn(0, displayWidth - 2)

        val scaledTop = scale(
            top,
            captureHeight,
            displayHeight
        ).coerceIn(0, displayHeight - 2)

        val scaledRight = scale(
            right,
            captureWidth,
            displayWidth
        ).coerceIn(scaledLeft + 1, displayWidth - 1)

        val scaledBottom = scale(
            bottom,
            captureHeight,
            displayHeight
        ).coerceIn(scaledTop + 1, displayHeight - 1)

        return intArrayOf(
            scaledLeft,
            scaledTop,
            scaledRight,
            scaledBottom
        )
    }
}

data class NativeTgkOrientationMapping(
    val left: NativeTgkRect?,
    val right: NativeTgkRect?
) {
    fun isComplete(): Boolean {
        return left?.isValid() == true &&
            right?.isValid() == true
    }
}

data class NativeTgkLayout(
    val id: String,
    val name: String,
    val leftBehavior: NativeTgkTriggerBehavior =
        NativeTgkTriggerBehavior.SINGLE_TOUCH,
    val rightBehavior: NativeTgkTriggerBehavior =
        NativeTgkTriggerBehavior.SINGLE_TOUCH,
    val leftRapidFireCount: Int = 0,
    val rightRapidFireCount: Int = 0,
    val portrait: NativeTgkOrientationMapping? = null,
    val landscape: NativeTgkOrientationMapping? = null
) {
    fun mappingFor(
        orientation: NativeTgkOrientation
    ): NativeTgkOrientationMapping? {
        return when (orientation) {
            NativeTgkOrientation.PORTRAIT -> portrait
            NativeTgkOrientation.LANDSCAPE -> landscape
        }
    }

    fun withMapping(
        orientation: NativeTgkOrientation,
        mapping: NativeTgkOrientationMapping
    ): NativeTgkLayout {
        return when (orientation) {
            NativeTgkOrientation.PORTRAIT ->
                copy(portrait = mapping)
            NativeTgkOrientation.LANDSCAPE ->
                copy(landscape = mapping)
        }
    }
}

data class NativeTgkProfile(
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val bridgeVisualFeedback: Boolean = true,
    val showSavedTargets: Boolean = true,
    val savedTargetOpacityPercent: Int = 12,
    val leftBehavior: NativeTgkTriggerBehavior =
        NativeTgkTriggerBehavior.SINGLE_TOUCH,
    val rightBehavior: NativeTgkTriggerBehavior =
        NativeTgkTriggerBehavior.SINGLE_TOUCH,
    val leftRapidFireCount: Int = 0,
    val rightRapidFireCount: Int = 0,
    val portrait: NativeTgkOrientationMapping? = null,
    val landscape: NativeTgkOrientationMapping? = null,
    val activeLayoutId: String = "default",
    val layouts: List<NativeTgkLayout> = emptyList()
) {
    fun activeLayout(): NativeTgkLayout? {
        return layouts.firstOrNull {
            it.id == activeLayoutId
        } ?: layouts.firstOrNull()
    }

    fun mappingFor(
        orientation: NativeTgkOrientation
    ): NativeTgkOrientationMapping? {
        activeLayout()?.let {
            return it.mappingFor(orientation)
        }

        return when (orientation) {
            NativeTgkOrientation.PORTRAIT -> portrait
            NativeTgkOrientation.LANDSCAPE -> landscape
        }
    }

    fun hasCompleteMapping(
        orientation: NativeTgkOrientation
    ): Boolean {
        return mappingFor(orientation)?.isComplete() == true
    }

    fun hasAnyCompleteMapping(): Boolean {
        return hasCompleteMapping(
            NativeTgkOrientation.PORTRAIT
        ) || hasCompleteMapping(
            NativeTgkOrientation.LANDSCAPE
        )
    }

    fun effectiveLeftRapidFireCount(): Int {
        return activeLayout()?.leftRapidFireCount
            ?: leftRapidFireCount
    }

    fun effectiveLeftBehavior(): NativeTgkTriggerBehavior {
        return activeLayout()?.leftBehavior
            ?: leftBehavior
    }

    fun effectiveRightBehavior(): NativeTgkTriggerBehavior {
        return activeLayout()?.rightBehavior
            ?: rightBehavior
    }

    fun effectiveRightRapidFireCount(): Int {
        return activeLayout()?.rightRapidFireCount
            ?: rightRapidFireCount
    }

    fun withMapping(
        orientation: NativeTgkOrientation,
        mapping: NativeTgkOrientationMapping
    ): NativeTgkProfile {
        val active = activeLayout()
        if (active == null) {
            return when (orientation) {
                NativeTgkOrientation.PORTRAIT ->
                    copy(portrait = mapping)
                NativeTgkOrientation.LANDSCAPE ->
                    copy(landscape = mapping)
            }
        }

        return copy(
            layouts = layouts.map {
                if (it.id == active.id) {
                    it.withMapping(orientation, mapping)
                } else {
                    it
                }
            }
        )
    }

    fun withTriggerBehavior(
        left: Boolean,
        behavior: NativeTgkTriggerBehavior,
        count: Int
    ): NativeTgkProfile {
        val active = activeLayout()
        if (active == null) {
            return if (left) {
                copy(
                    leftBehavior = behavior,
                    leftRapidFireCount = count
                )
            } else {
                copy(
                    rightBehavior = behavior,
                    rightRapidFireCount = count
                )
            }
        }

        return copy(
            layouts = layouts.map {
                if (it.id != active.id) {
                    it
                } else if (left) {
                    it.copy(
                        leftBehavior = behavior,
                        leftRapidFireCount = count
                    )
                } else {
                    it.copy(
                        rightBehavior = behavior,
                        rightRapidFireCount = count
                    )
                }
            }
        )
    }
}

object NativeTgkStorage {
    private const val TAG = "RedmagicNativeTgk"
    private const val PREFS_NAME = "native_tgk_profiles"
    private const val PROFILES_KEY = "profiles_json"
    private const val VERSION = 4
    private const val EXPORT_FORMAT =
        "redmagic-native-tgk-profiles"
    private const val EXPORT_VERSION = 1
    private const val MAX_IMPORT_SIZE = 5_000_000

    const val MAX_LAYOUTS_PER_PROFILE = 5

    val supportedRapidFireCounts = setOf(0, 2, 5, 10)

    data class ImportResult(
        val importedCount: Int,
        val replacedCount: Int,
        val skippedCount: Int
    )

    private val packagePattern = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )

    @Synchronized
    fun readProfiles(
        context: Context
    ): List<NativeTgkProfile> {
        val raw = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(PROFILES_KEY, null)
            ?: return emptyList()

        return try {
            val root = JSONObject(raw)
            val profiles = root.optJSONArray("profiles")
                ?: JSONArray()

            buildList {
                for (index in 0 until profiles.length()) {
                    val profile = profiles
                        .optJSONObject(index)
                        ?.toProfile()
                        ?: continue

                    add(profile)
                }
            }.distinctBy {
                it.packageName
            }.sortedBy {
                it.appLabel.lowercase()
            }
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "Failed to read native TGK profiles",
                error
            )
            emptyList()
        }
    }

    @Synchronized
    fun getProfile(
        context: Context,
        packageName: String
    ): NativeTgkProfile? {
        return readProfiles(context).firstOrNull {
            it.packageName == packageName
        }
    }

    @Synchronized
    fun saveProfile(
        context: Context,
        profile: NativeTgkProfile
    ): Boolean {
        val normalized = profile.normalized()

        if (
            !packagePattern.matches(normalized.packageName) ||
            normalized.appLabel.isBlank() ||
            normalized.savedTargetOpacityPercent !in 5..30 ||
            normalized.layouts.isEmpty() ||
            normalized.layouts.size > MAX_LAYOUTS_PER_PROFILE ||
            normalized.layouts.any {
                it.name.isBlank() ||
                    it.name.length > 40 ||
                    it.leftRapidFireCount !in
                    supportedRapidFireCounts ||
                    it.rightRapidFireCount !in
                    supportedRapidFireCounts
            } ||
            normalized.leftRapidFireCount !in
                supportedRapidFireCounts ||
            normalized.rightRapidFireCount !in
                supportedRapidFireCounts
        ) {
            return false
        }

        val profiles = readProfiles(context)
            .filterNot {
                it.packageName == normalized.packageName
            }
            .plus(normalized)
            .sortedBy {
                it.appLabel.lowercase()
            }

        return writeProfiles(context, profiles)
    }

    @Synchronized
    fun removeProfile(
        context: Context,
        packageName: String
    ): Boolean {
        val profiles = readProfiles(context)
        val updated = profiles.filterNot {
            it.packageName == packageName
        }

        if (updated.size == profiles.size) {
            return true
        }

        return writeProfiles(context, updated)
    }

    fun enabledPackages(
        context: Context
    ): Set<String> {
        return readProfiles(context)
            .asSequence()
            .filter { it.enabled }
            .filter { it.hasAnyCompleteMapping() }
            .map { it.packageName }
            .toSet()
    }

    fun hasEnabledProfile(
        context: Context,
        packageName: String
    ): Boolean {
        return getProfile(context, packageName)
            ?.let { profile ->
                profile.enabled &&
                    profile.hasAnyCompleteMapping()
            } == true
    }

    fun createExportJson(
        context: Context,
        packageName: String? = null,
        allowEmpty: Boolean = false
    ): String {
        val profiles = readProfiles(context).filter {
            packageName == null || it.packageName == packageName
        }

        require(allowEmpty || profiles.isNotEmpty()) {
            if (packageName == null) {
                "No TGK profiles are available to export"
            } else {
                "No TGK profile exists for $packageName"
            }
        }

        return JSONObject()
            .put("format", EXPORT_FORMAT)
            .put("exportVersion", EXPORT_VERSION)
            .put("profileSchemaVersion", VERSION)
            .put("exportedAtEpochMs", System.currentTimeMillis())
            .put(
                "profiles",
                JSONArray().apply {
                    profiles.forEach {
                        put(it.toJson())
                    }
                }
            )
            .toString(2)
    }

    fun importedPackageNames(raw: String): Set<String> {
        return parseExport(raw)
            .map { it.packageName }
            .toSet()
    }

    @Synchronized
    fun importProfilesJson(
        context: Context,
        raw: String,
        replaceExisting: Boolean,
        replaceAll: Boolean = false
    ): ImportResult {
        val imported = parseExport(raw)
        val existing = if (replaceAll) {
            emptyList()
        } else {
            readProfiles(context)
        }
        val merged = existing.associateBy {
            it.packageName
        }.toMutableMap()
        var replacedCount = 0
        var skippedCount = 0
        var importedCount = 0

        imported.forEach { profile ->
            val conflict = merged.containsKey(profile.packageName)
            if (conflict && !replaceExisting) {
                skippedCount += 1
                return@forEach
            }

            if (conflict) {
                replacedCount += 1
            }
            merged[profile.packageName] = profile.normalized()
            importedCount += 1
        }

        check(
            writeProfiles(
                context,
                merged.values.sortedBy {
                    it.appLabel.lowercase()
                }
            )
        ) {
            "Unable to save imported TGK profiles"
        }

        return ImportResult(
            importedCount = importedCount,
            replacedCount = replacedCount,
            skippedCount = skippedCount
        )
    }

    private fun parseExport(raw: String): List<NativeTgkProfile> {
        require(raw.length <= MAX_IMPORT_SIZE) {
            "TGK profile file is larger than 5 MB"
        }

        val root = JSONObject(raw)
        require(root.optString("format") == EXPORT_FORMAT) {
            "This is not a Redmagic TGK profile file"
        }
        require(
            root.optInt("exportVersion", 0) in
                1..EXPORT_VERSION
        ) {
            "Unsupported TGK export version"
        }

        val array = root.optJSONArray("profiles")
            ?: error("TGK profile file contains no profiles")
        require(array.length() in 0..500) {
            "TGK profile file has an invalid profile count"
        }

        val profiles = buildList {
            for (index in 0 until array.length()) {
                val profile = array.optJSONObject(index)
                    ?.toProfile()
                    ?: error(
                        "TGK profile ${index + 1} is invalid"
                    )
                add(profile)
            }
        }

        require(
            profiles.map { it.packageName }.toSet().size ==
                profiles.size
        ) {
            "TGK profile file contains duplicate packages"
        }

        return profiles
    }

    private fun writeProfiles(
        context: Context,
        profiles: List<NativeTgkProfile>
    ): Boolean {
        return try {
            val root = JSONObject()
                .put("version", VERSION)
                .put(
                    "profiles",
                    JSONArray().apply {
                        profiles.forEach {
                            put(it.toJson())
                        }
                    }
                )

            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            ).edit()
                .putString(PROFILES_KEY, root.toString())
                .commit()
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "Failed to save native TGK profiles",
                error
            )
            false
        }
    }

    private fun NativeTgkProfile.toJson(): JSONObject {
        return JSONObject()
            .put("packageName", packageName)
            .put("appLabel", appLabel)
            .put("enabled", enabled)
            .put("hapticsEnabled", hapticsEnabled)
            .put("bridgeVisualFeedback", bridgeVisualFeedback)
            .put("showSavedTargets", showSavedTargets)
            .put(
                "savedTargetOpacityPercent",
                savedTargetOpacityPercent
            )
            .put("leftBehavior", leftBehavior.name)
            .put("rightBehavior", rightBehavior.name)
            .put("leftRapidFireCount", leftRapidFireCount)
            .put("rightRapidFireCount", rightRapidFireCount)
            .put("activeLayoutId", activeLayoutId)
            .put(
                "layouts",
                JSONArray().apply {
                    layouts.forEach {
                        put(it.toJson())
                    }
                }
            )
            .apply {
                portrait?.let {
                    put("portrait", it.toJson())
                }
                landscape?.let {
                    put("landscape", it.toJson())
                }
            }
    }

    private fun NativeTgkLayout.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("leftBehavior", leftBehavior.name)
            .put("rightBehavior", rightBehavior.name)
            .put("leftRapidFireCount", leftRapidFireCount)
            .put("rightRapidFireCount", rightRapidFireCount)
            .apply {
                portrait?.let {
                    put("portrait", it.toJson())
                }
                landscape?.let {
                    put("landscape", it.toJson())
                }
            }
    }

    private fun NativeTgkOrientationMapping.toJson(): JSONObject {
        return JSONObject().apply {
            left?.let {
                put("left", it.toJson())
            }
            right?.let {
                put("right", it.toJson())
            }
        }
    }

    private fun NativeTgkRect.toJson(): JSONObject {
        return JSONObject()
            .put("left", left)
            .put("top", top)
            .put("right", right)
            .put("bottom", bottom)
            .put("captureWidth", captureWidth)
            .put("captureHeight", captureHeight)
    }

    private fun JSONObject.toProfile(): NativeTgkProfile? {
        val packageName = optString("packageName")
        val appLabel = optString("appLabel")

        if (
            !packagePattern.matches(packageName) ||
            appLabel.isBlank()
        ) {
            return null
        }

        val legacyLeftRapid = optInt(
            "leftRapidFireCount",
            0
        ).takeIf {
            it in supportedRapidFireCounts
        } ?: 0
        val legacyRightRapid = optInt(
            "rightRapidFireCount",
            0
        ).takeIf {
            it in supportedRapidFireCounts
        } ?: 0
        val legacyPortrait = optJSONObject("portrait")
            ?.toOrientationMapping()
        val legacyLandscape = optJSONObject("landscape")
            ?.toOrientationMapping()
        val parsedLayouts = optJSONArray("layouts")
            ?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)
                            ?.toLayout()
                            ?.let(::add)
                    }
                }
            }
            .orEmpty()

        return NativeTgkProfile(
            packageName = packageName,
            appLabel = appLabel,
            enabled = optBoolean("enabled", true),
            bridgeVisualFeedback = optBoolean("bridgeVisualFeedback", true),
            hapticsEnabled = optBoolean(
                "hapticsEnabled",
                true
            ),
            showSavedTargets = optBoolean(
                "showSavedTargets",
                true
            ),
            savedTargetOpacityPercent = optInt(
                "savedTargetOpacityPercent",
                12
            ).coerceIn(5, 30),
            leftBehavior = behaviorFromJson(
                "leftBehavior",
                legacyLeftRapid
            ),
            rightBehavior = behaviorFromJson(
                "rightBehavior",
                legacyRightRapid
            ),
            leftRapidFireCount = legacyLeftRapid,
            rightRapidFireCount = legacyRightRapid,
            portrait = legacyPortrait,
            landscape = legacyLandscape,
            activeLayoutId = optString(
                "activeLayoutId",
                "default"
            ),
            layouts = parsedLayouts
        ).normalized()
    }

    private fun JSONObject.toLayout(): NativeTgkLayout? {
        val id = optString("id").trim()
        val name = optString("name").trim()
        if (id.isBlank() || name.isBlank()) {
            return null
        }

        val leftRapid = optInt(
            "leftRapidFireCount",
            0
        ).takeIf {
            it in supportedRapidFireCounts
        } ?: 0
        val rightRapid = optInt(
            "rightRapidFireCount",
            0
        ).takeIf {
            it in supportedRapidFireCounts
        } ?: 0

        return NativeTgkLayout(
            id = id,
            name = name.take(40),
            leftBehavior = behaviorFromJson(
                "leftBehavior",
                leftRapid
            ),
            rightBehavior = behaviorFromJson(
                "rightBehavior",
                rightRapid
            ),
            leftRapidFireCount = leftRapid,
            rightRapidFireCount = rightRapid,
            portrait = optJSONObject("portrait")
                ?.toOrientationMapping(),
            landscape = optJSONObject("landscape")
                ?.toOrientationMapping()
        )
    }

    private fun NativeTgkProfile.normalized(): NativeTgkProfile {
        val validLayouts = layouts
            .distinctBy { it.id }
            .take(MAX_LAYOUTS_PER_PROFILE)
            .map { it.normalizedBehavior() }

        val normalizedLayouts = if (validLayouts.isEmpty()) {
            listOf(
                NativeTgkLayout(
                    id = "default",
                    name = "Default",
                    leftBehavior = if (
                        leftRapidFireCount > 0
                    ) {
                        NativeTgkTriggerBehavior.RAPID_FIRE
                    } else if (
                        leftBehavior ==
                        NativeTgkTriggerBehavior.RAPID_FIRE
                    ) {
                        NativeTgkTriggerBehavior.SINGLE_TOUCH
                    } else {
                        leftBehavior
                    },
                    rightBehavior = if (
                        rightRapidFireCount > 0
                    ) {
                        NativeTgkTriggerBehavior.RAPID_FIRE
                    } else if (
                        rightBehavior ==
                        NativeTgkTriggerBehavior.RAPID_FIRE
                    ) {
                        NativeTgkTriggerBehavior.SINGLE_TOUCH
                    } else {
                        rightBehavior
                    },
                    leftRapidFireCount =
                        leftRapidFireCount,
                    rightRapidFireCount =
                        rightRapidFireCount,
                    portrait = portrait,
                    landscape = landscape
                )
            )
        } else {
            validLayouts
        }

        val normalizedActiveId = activeLayoutId.takeIf {
            candidate ->
            normalizedLayouts.any { it.id == candidate }
        } ?: normalizedLayouts.first().id

        return copy(
            activeLayoutId = normalizedActiveId,
            layouts = normalizedLayouts
        )
    }

    private fun NativeTgkLayout.normalizedBehavior():
        NativeTgkLayout {
        val normalizedLeftBehavior = when {
            leftBehavior == NativeTgkTriggerBehavior.RAPID_FIRE &&
                leftRapidFireCount <= 0 ->
                NativeTgkTriggerBehavior.SINGLE_TOUCH
            else -> leftBehavior
        }
        val normalizedRightBehavior = when {
            rightBehavior == NativeTgkTriggerBehavior.RAPID_FIRE &&
                rightRapidFireCount <= 0 ->
                NativeTgkTriggerBehavior.SINGLE_TOUCH
            else -> rightBehavior
        }

        return copy(
            leftBehavior = normalizedLeftBehavior,
            rightBehavior = normalizedRightBehavior,
            leftRapidFireCount = if (
                normalizedLeftBehavior ==
                NativeTgkTriggerBehavior.RAPID_FIRE
            ) {
                leftRapidFireCount
            } else {
                0
            },
            rightRapidFireCount = if (
                normalizedRightBehavior ==
                NativeTgkTriggerBehavior.RAPID_FIRE
            ) {
                rightRapidFireCount
            } else {
                0
            }
        )
    }

    private fun JSONObject.behaviorFromJson(
        key: String,
        rapidFireCount: Int
    ): NativeTgkTriggerBehavior {
        val stored = optString(key).takeIf {
            it.isNotBlank()
        }?.let {
            runCatching {
                NativeTgkTriggerBehavior.valueOf(it)
            }.getOrNull()
        }

        return stored ?: if (rapidFireCount > 0) {
            NativeTgkTriggerBehavior.RAPID_FIRE
        } else {
            NativeTgkTriggerBehavior.SINGLE_TOUCH
        }
    }

    private fun JSONObject.toOrientationMapping():
        NativeTgkOrientationMapping {
        return NativeTgkOrientationMapping(
            left = optJSONObject("left")?.toRect(),
            right = optJSONObject("right")?.toRect()
        )
    }

    private fun JSONObject.toRect(): NativeTgkRect? {
        val rect = NativeTgkRect(
            left = optInt("left", -1),
            top = optInt("top", -1),
            right = optInt("right", -1),
            bottom = optInt("bottom", -1),
            captureWidth = optInt("captureWidth", -1),
            captureHeight = optInt("captureHeight", -1)
        )

        return rect.takeIf {
            it.isValid()
        }
    }
}
