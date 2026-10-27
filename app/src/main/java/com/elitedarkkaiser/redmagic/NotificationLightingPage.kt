package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.LightingTabDeps
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.google.android.material.materialswitch.MaterialSwitch

internal object NotificationLightingPage {
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun create(activity: Activity, deps: LightingTabDeps): View {
        lateinit var render: () -> Unit
        val root = object : LinearLayout(activity) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) post { if (isAttachedToWindow) render() }
            }
        }.apply { orientation = LinearLayout.VERTICAL }
        fun changed() { activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName)); NotificationLightingService.ensureConnected(activity) }
        fun label(value: String, size: Float = 13f) = TextView(activity).apply { text = value; textSize = size; setTextColor(AppTheme.textSecondary); setPadding(0, deps.dp(8), 0, deps.dp(6)) }
        fun heading(card: LinearLayout, title: String, enabled: Boolean, update: (Boolean) -> Unit) {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(activity).apply { text = title; textSize = 18f; setTextColor(AppTheme.textPrimary); setTypeface(null, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(MaterialSwitch(activity).apply { isChecked = enabled; setOnCheckedChangeListener { _, value -> update(value) } })
            card.addView(row)
        }
        fun editor(pkg: String, name: String) {
            val main = activity as? MainActivity ?: return
            lateinit var close: () -> Unit
            var profile = NotificationLightingState.read(activity, pkg) ?: NotificationLightingState.Profile()
            val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(deps.dp(16), deps.dp(8), deps.dp(16), deps.dp(20)) }
            lateinit var repaint: () -> Unit
            fun update(next: NotificationLightingState.Profile) { profile = next; repaint() }
            repaint = {
                content.removeAllViews()
                val card = deps.sectionPanel()
                card.addView(TextView(activity).apply { text = name; textSize = 18f; setTextColor(AppTheme.textPrimary); setTypeface(null, android.graphics.Typeface.BOLD) })
                card.addView(label("Color"))
                val colors = listOf(1 to "#FF0000", 3 to "#FF8C00", 4 to "#FFD600", 5 to "#00E676", 6 to "#00E5FF", 7 to "#1565FF", 8 to "#A020F0", 9 to "#FF69B4")
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val factory = LedControlViewFactory(activity)
                colors.forEachIndexed { index, (id, hex) ->
                    if (index > 0) row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
                    row.addView(factory.colorDot(hex, profile.color == id) { update(profile.copy(color = id)) }.apply { contentDescription = "Notification color $hex" }, LinearLayout.LayoutParams(deps.dp(29), deps.dp(29)))
                }
                card.addView(row)
                card.addView(label("Effect"))
                val effects = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                listOf("steady", "breathe", "flashing", "rapid").forEachIndexed { i, value ->
                    effects.addView(deps.filterChip(value.replaceFirstChar { it.uppercase() }, profile.effect == value) { update(profile.copy(effect = value)) }, LinearLayout.LayoutParams(0, deps.dp(44), 1f).apply { if (i > 0) marginStart = deps.dp(4) })
                }
                card.addView(effects)
                card.addView(label("Lighting window"))
                listOf(listOf(3, 5, 10), listOf(15, 30)).forEach { values ->
                    val r = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    values.forEachIndexed { i, seconds -> r.addView(deps.filterChip("$seconds s", profile.seconds == seconds) { update(profile.copy(seconds = seconds)) }, LinearLayout.LayoutParams(0, deps.dp(44), 1f).apply { if (i > 0) marginStart = deps.dp(4) }) }
                    card.addView(r)
                }
                card.addView(label("Notifications use the device's stock brightness."))
                listOf(Triple("Logo / GAME MODE bar", profile.logo, 0), Triple("Trigger lighting", profile.triggers, 1), Triple("Fan lighting", profile.fan, 2)).forEach { (title, enabled, zone) ->
                    heading(card, title, enabled) { value -> update(when (zone) { 0 -> profile.copy(logo = value); 1 -> profile.copy(triggers = value); else -> profile.copy(fan = value) }) }
                }
                content.addView(card)
                content.addView(deps.actionButton("Save profile", true) {
                    val toSave = profile
                    worker.execute {
                        val saved = runCatching { NotificationLightingState.save(activity, pkg, toSave) }.getOrDefault(false)
                        activity.runOnUiThread {
                            if (saved) { changed(); close(); render() }
                            else Toast.makeText(activity, "Profile could not be saved. Try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                })
                if (NotificationLightingState.hasProfile(activity, pkg)) content.addView(deps.actionButton("Remove app", false) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setMessage("Remove notification lighting for $name?").setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ -> NotificationLightingState.remove(activity, pkg); changed(); close(); render() }.show()
                })
            }
            repaint()
            close = main.showGamingPage(name, ScrollView(activity).apply { addView(content) })
        }
        render = {
            root.removeAllViews()
            val settings = deps.sectionPanel()
            heading(settings, "Screen-off notifications", NotificationLightingState.enabled(activity)) { value ->
                worker.execute {
                    val saved = runCatching { NotificationLightingState.setEnabled(activity, value) }.getOrDefault(false)
                    activity.runOnUiThread { if (saved) changed() else { Toast.makeText(activity, "Could not save notification setting", Toast.LENGTH_LONG).show(); render() } }
                }
            }
            settings.addView(label("Lighting appears for selected apps while the screen is off."))
            val access = activity.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(activity, NotificationLightingService::class.java))
            settings.addView(deps.actionButton(if (access) "Notification access granted" else "Grant notification access", false) { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
            root.addView(settings)
            root.addView(deps.actionButton("+ Add app", false) {
                val manager = activity.packageManager
                val apps = manager.getInstalledApplications(0).filter { it.enabled && it.packageName != activity.packageName && manager.getLaunchIntentForPackage(it.packageName) != null }.map { MaterialAppPicker.App(it.packageName, it.loadLabel(manager).toString()) }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
                MaterialAppPicker.show(activity, "Choose notification app", apps) { editor(it.packageName, it.label) }
            })
            root.addView(label("Configured apps"))
            NotificationLightingState.packages(activity).forEach { pkg ->
                val name = runCatching { activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                val card = deps.sectionPanel().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true; isFocusable = true; setOnClickListener { editor(pkg, name) } }
                card.addView(android.widget.ImageView(activity).apply { setImageDrawable(runCatching { activity.packageManager.getApplicationIcon(pkg) }.getOrNull()) }, LinearLayout.LayoutParams(deps.dp(42), deps.dp(42)).apply { marginEnd = deps.dp(12) })
                card.addView(TextView(activity).apply { text = name; setTextColor(AppTheme.textPrimary); textSize = 16f }, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(TextView(activity).apply { text = "›"; setTextColor(AppTheme.textPrimary); textSize = 24f })
                root.addView(card)
            }
            if (NotificationLightingState.packages(activity).isEmpty()) root.addView(label("No apps configured yet."))
        }
        render()
        return root
    }
}
