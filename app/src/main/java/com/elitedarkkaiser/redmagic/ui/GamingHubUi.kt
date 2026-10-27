package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.*
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/** Gaming overview backed by the existing game, performance, touch and trigger stores. */
internal object GamingHubUi {
    fun create(activity: Activity, deps: HardwareTabDeps): LinearLayout {
        val d = deps.dp
        val gameActions = MainGameModeActions(activity, MainActivityUiKit(activity), LedControlViewFactory(activity))
        lateinit var render: (LinearLayout) -> Unit
        val root = object : LinearLayout(activity) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) post { if (isAttachedToWindow) render(this) }
            }
        }
        root.orientation = LinearLayout.VERTICAL
        fun label(text: String, bold: Boolean = false, small: Boolean = false) = TextView(activity).apply {
            this.text = text
            textSize = if (small) 12f else 15f
            setTextColor(if (small) AppTheme.textSecondary else AppTheme.textPrimary)
            if (bold) setTypeface(AppTheme.appTypeface, Typeface.BOLD)
        }
        fun row(parent: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(d(16), d(14), d(16), d(14))
                background = AppTheme.roundedBg(AppTheme.panelColor, AppTheme.borderColor, d(16).toFloat())
                isClickable = true; isFocusable = true
                setOnClickListener { action() }
            }
            card.addView(ToolboxIcons.create(activity, title), LinearLayout.LayoutParams(d(28), d(28)).apply { marginEnd = d(16) })
            val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            text.addView(label(title, true)); text.addView(label(subtitle, small = true))
            card.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(label("›"))
            parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(8) })
        }
        fun setting(parent: LinearLayout, title: String, value: String, action: () -> Unit) {
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(d(8), d(8), d(8), d(8)); isClickable = true; isFocusable = true
                setOnClickListener { action() }
            }
            item.addView(label(title), LinearLayout.LayoutParams(0, -2, 1f))
            item.addView(label(value, small = true).apply { setTextColor(AppTheme.accentColor) })
            item.addView(label("  ›"))
            parent.addView(item)
        }
        fun refresh() { root.post { render(root) } }
        fun build(parent: LinearLayout) {
            parent.removeAllViews()
            row(parent, "Game profiles", "Manage per-game cooling and lighting") { gameActions.showProfileDialog() }
            row(parent, "Shoulder triggers", "Mapping, controls and safety") { deps.showNativeTgkProfileDialog() }
            row(parent, "Game overlay", "Manage overlay permission") {
                runCatching { activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${activity.packageName}"))) }
            }
            row(parent, "Magic Key", "Set button functions") { (activity as? MainActivity)?.openMagicKeyFromGaming() }
            val heading = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,d(12),0,d(8)) }
            heading.addView(label("Per-app game profiles", true), LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(label("See all").apply { setTextColor(AppTheme.accentColor); setOnClickListener { gameActions.showProfileDialog() } })
            parent.addView(heading)
            val packages = getSavedGamePackagesStorage(activity).sorted()
            if (packages.isEmpty()) {
                row(parent, "Add games", "Select games to configure their profiles") { gameActions.showAppPicker { refresh() } }
            }
            packages.forEach { pkg ->
                val pm = activity.packageManager
                val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                val profile = getSavedGameModeProfileForPackageStorage(activity, pkg)
                val perf = PerformanceModeStorage.getProfile(activity, pkg)
                val touch = TouchTuningStorage.getProfile(activity, pkg)
                val trigger = NativeTgkStorage.getProfile(activity, pkg)
                val card = deps.sectionPanel()
                val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val image = ImageView(activity).apply { setImageDrawable(runCatching { pm.getApplicationIcon(pkg) }.getOrNull()); contentDescription = null }
                head.addView(image, LinearLayout.LayoutParams(d(42), d(42)).apply { marginEnd = d(12) })
                val names = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                names.addView(label(appLabel, true)); names.addView(label("Game profile", small = true))
                head.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
                head.addView(label("⋮").apply { setOnClickListener { gameActions.showProfileEditor(pkg, appLabel) } })
                card.addView(head)
                setting(card, "Performance mode", if (perf?.enabled == true) perf.mode.label else "System") { PerformanceModeProfileDialog.showForApp(activity, pkg, appLabel) { refresh() } }
                setting(card, "Fan control", if (profile.fanEnabled) "Level ${profile.fanLevel}" else "Off") { gameActions.showProfileEditor(pkg, appLabel) }
                setting(card, "Touch sampling rate", if (touch?.enabled == true) "${touch.sampleRateHz} Hz" else "System") { TouchTuningProfileDialog.show(activity, pkg, appLabel) }
                setting(card, "Shoulder triggers", if (trigger?.enabled == true) "Enabled" else "Not configured") { NativeTgkProfileDialog.show(activity, pkg) }
                parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(10) })
            }
        }
        render = { view -> build(view) }
        build(root)
        return root
    }
}
