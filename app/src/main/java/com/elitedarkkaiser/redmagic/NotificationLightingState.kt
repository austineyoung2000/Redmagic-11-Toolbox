package com.elitedarkkaiser.redmagic

import android.content.Context
import org.json.JSONObject
import android.os.SystemClock

internal object NotificationLightingState {
    data class Profile(val color: Int = 7, val effect: String = "breathe", val seconds: Int = 10,
        val brightness: Int = 128, val logo: Boolean = true, val triggers: Boolean = false, val fan: Boolean = false)
    private fun prefs(c: Context) = c.getSharedPreferences("notification_lighting", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, value: Boolean) { prefs(c).edit().putBoolean("enabled", value).apply() }
    @Volatile var expiresAt = 0L
    fun isActive() = expiresAt > SystemClock.elapsedRealtime()
    fun read(c: Context, pkg: String): Profile? = runCatching {
        val raw = prefs(c).getString("app:$pkg", null) ?: return null
        val j = JSONObject(raw)
        Profile(j.getInt("color"), j.getString("effect"), j.getInt("seconds").coerceIn(3,30),
            j.optInt("brightness",128).coerceIn(32,255),j.optBoolean("logo",true),j.optBoolean("triggers"),j.optBoolean("fan"))
            .takeIf { it.color in listOf(1,3,4,5,6,7,8,9) && it.effect in listOf("steady","breathe","flashing","rapid") }
    }.getOrNull()
    fun save(c: Context, pkg: String, p: Profile) {
        val j = JSONObject().put("color",p.color).put("effect",p.effect).put("seconds",p.seconds)
            .put("brightness",p.brightness).put("logo",p.logo).put("triggers",p.triggers).put("fan",p.fan)
        prefs(c).edit().putString("app:$pkg",j.toString()).apply()
    }
    fun remove(c: Context, pkg: String) { prefs(c).edit().remove("app:$pkg").apply() }
}
