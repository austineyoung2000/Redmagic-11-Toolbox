package com.elitedarkkaiser.redmagic

import org.json.JSONObject

/** Missing legacy fields inherit shared defaults; corrupt profiles do not erase them. */
internal object GameModeProfileCodec {
    fun decode(raw: String?, fallback: GameModeProfile): GameModeProfile {
        if (raw == null) return fallback
        return runCatching {
            val obj = JSONObject(raw)
            fun bool(key: String, default: Boolean) = obj.opt(key) as? Boolean ?: default
            fun int(key: String, default: Int) = (obj.opt(key) as? Number)?.toInt() ?: default
            fun text(key: String, default: String) = obj.opt(key) as? String ?: default
            val savedEffect = if (text("fanLedModeType", "basic") == "preset")
                "preset:${text("fanLedPresetValue", "")}" else text("fanLedEffect", fallback.fanLedEffect)
            val fanEffect = FanLedPalette.normalizeEffect(savedEffect)
            val fanColor = FanLedPalette.normalizeColor(savedEffect, int("fanLedColor", fallback.fanLedColor))
            val validFan = LedProfileValidation.isValid("fan", fanEffect, fanColor)
            val logoEffect = text("logoLedEffect", fallback.logoLedEffect)
            val logoColor = int("logoLedColor", fallback.logoLedColor)
            val validLogo = LedProfileValidation.isValid("logo", logoEffect, logoColor)
            val triggerEffect = text("shoulderLedEffect", fallback.shoulderLedEffect)
            val triggerColor = int("shoulderLedColor", fallback.shoulderLedColor)
            val validTrigger = LedProfileValidation.isValid("triggers", triggerEffect, triggerColor)
            fallback.copy(
                fanEnabled = bool("fanEnabled", fallback.fanEnabled),
                fanLevel = int("fanLevel", fallback.fanLevel).takeIf { it in 0..5 } ?: fallback.fanLevel,
                pumpEnabled = bool("pumpEnabled", fallback.pumpEnabled),
                pumpProfile = text("pumpProfile", fallback.pumpProfile).takeIf { it in setOf("slow", "medium", "quick") } ?: fallback.pumpProfile,
                fanLedEnabled = bool("fanLedEnabled", fallback.fanLedEnabled),
                fanLedEffect = if (validFan) fanEffect else fallback.fanLedEffect,
                fanLedColor = if (validFan) fanColor else fallback.fanLedColor,
                logoLedEnabled = bool("logoLedEnabled", fallback.logoLedEnabled),
                logoLedEffect = if (validLogo) logoEffect else fallback.logoLedEffect,
                logoLedColor = if (validLogo) logoColor else fallback.logoLedColor,
                shoulderLedEnabled = bool("shoulderLedEnabled", fallback.shoulderLedEnabled),
                shoulderLedEffect = if (validTrigger) triggerEffect else fallback.shoulderLedEffect,
                shoulderLedColor = if (validTrigger) triggerColor else fallback.shoulderLedColor
            )
        }.getOrDefault(fallback)
    }

    fun encode(profile: GameModeProfile, previous: String? = null): String {
        val obj = runCatching { JSONObject(previous ?: "{}") }.getOrElse { JSONObject() }
        obj.put("version", 2)
        obj.put("fanEnabled", profile.fanEnabled); obj.put("fanLevel", profile.fanLevel)
        obj.put("pumpEnabled", profile.pumpEnabled); obj.put("pumpProfile", profile.pumpProfile)
        obj.put("fanLedEnabled", profile.fanLedEnabled); obj.put("fanLedEffect", profile.fanLedEffect); obj.put("fanLedColor", profile.fanLedColor)
        obj.put("fanLedModeType", "basic"); obj.put("fanLedPresetValue", "")
        obj.put("logoLedEnabled", profile.logoLedEnabled); obj.put("logoLedEffect", profile.logoLedEffect); obj.put("logoLedColor", profile.logoLedColor)
        obj.put("shoulderLedEnabled", profile.shoulderLedEnabled); obj.put("shoulderLedEffect", profile.shoulderLedEffect); obj.put("shoulderLedColor", profile.shoulderLedColor)
        return obj.toString()
    }
}
