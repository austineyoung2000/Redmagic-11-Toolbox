package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Portable saved data only: no runtime ownership, permission or root caches. */
internal object BackupSupplement {
    private const val NOTIFICATIONS = "notification_lighting"
    private const val POSITIONS = "gameplay_edit_control_positions"
    data class Parsed(val enabled: Boolean?, val apps: Map<String, String>, val positions: Map<String, Float>)

    fun export(context: Context): JSONObject {
        val notificationPrefs = context.getSharedPreferences(NOTIFICATIONS, Context.MODE_PRIVATE)
        val apps = JSONObject()
        notificationPrefs.all.forEach { (key, value) ->
            if (key.startsWith("app:") && value is String) apps.put(key.removePrefix("app:"), JSONObject(value))
        }
        val positions = JSONObject()
        context.getSharedPreferences(POSITIONS, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (value is Float) positions.put(key, value.toDouble())
        }
        val exported = JSONObject().put("version", 1)
            .put("notifications", JSONObject().put("enabled", notificationPrefs.getBoolean("enabled", false)).put("apps", apps))
            .put("editPositions", positions)
        parse(exported) // Refuse to export supplemental data that cannot be restored.
        return exported
    }

    fun parse(json: JSONObject): Parsed {
        require(json.getInt("version") == 1) { "Unsupported supplemental backup version" }
        val notifications = json.getJSONObject("notifications")
        require(notifications.get("enabled") is Boolean) { "Invalid notification enable setting" }
        val appsJson = notifications.getJSONObject("apps")
        require(appsJson.length() <= 500) { "Too many notification profiles" }
        val apps = appsJson.keys().asSequence().associateWith { pkg ->
            require(pkg.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) { "Invalid notification package" }
            val p = appsJson.getJSONObject(pkg)
            require(p.getInt("color") in listOf(1,3,4,5,6,7,8,9)) { "Invalid notification color" }
            require(p.getString("effect") in listOf("steady","breathe","flashing","rapid")) { "Invalid notification effect" }
            require(p.getInt("seconds") in 3..30 && p.optInt("brightness",128) in 32..255) { "Invalid notification duration or brightness" }
            listOf("logo","triggers","fan").forEach { if (p.has(it)) require(p.get(it) is Boolean) { "Invalid notification zone" } }
            listOf("logoState","triggerState").forEach { key ->
                if (p.has(key)) {
                    val state = p.getJSONObject(key)
                    require(state.get("enabled") is Boolean && state.getInt("color") in listOf(1,3,4,5,6,7,8,9)) { "Invalid split-zone state" }
                    val effect = state.getString("effect")
                    val valid = if (key == "logoState" && effect.startsWith("areas:")) LogoBarSelection.decode(effect) != null
                    else {
                        val base = LedBrightness.effect(effect)
                        (!effect.startsWith("dim:") || LedBrightness.decode(effect) != null) &&
                            (base in listOf("steady","breathe","flashing","rapid") ||
                                (key == "triggerState" && ShoulderLedSplit.decode(effect) != null))
                    }
                    require(valid) { "Invalid split-zone effect" }
                }
            }
            p.toString()
        }
        val positionsJson = json.getJSONObject("editPositions")
        require(positionsJson.length() <= 2000) { "Too many edit positions" }
        val positions = positionsJson.keys().asSequence().associateWith { key ->
            require(key.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+_(portrait|landscape)_[xy]"))) { "Invalid edit-position key" }
            val value = positionsJson.getDouble(key).toFloat()
            require(value.isFinite() && value in 0f..1f) { "Invalid edit position" }
            value
        }
        positions.keys.forEach { key ->
            val paired = key.dropLast(1) + if (key.endsWith("x")) "y" else "x"
            require(positions.containsKey(paired)) { "Incomplete edit position" }
        }
        return Parsed(notifications.getBoolean("enabled"),apps,positions)
    }

    fun restore(context: Context, data: Parsed, replaceAll: Boolean = false) {
        val notifications = context.getSharedPreferences(NOTIFICATIONS, Context.MODE_PRIVATE).edit()
        if (replaceAll) notifications.clear()
        data.enabled?.let { notifications.putBoolean("enabled", it) }
        data.apps.forEach { (pkg, raw) -> notifications.putString("app:$pkg", raw) }
        check(notifications.commit()) { "Unable to restore notification settings" }
        val positions = context.getSharedPreferences(POSITIONS, Context.MODE_PRIVATE).edit()
        if (replaceAll) positions.clear()
        data.positions.forEach { (key, value) -> positions.putFloat(key,value) }
        check(positions.commit()) { "Unable to restore edit positions" }
        context.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(context.packageName))
    }
}
