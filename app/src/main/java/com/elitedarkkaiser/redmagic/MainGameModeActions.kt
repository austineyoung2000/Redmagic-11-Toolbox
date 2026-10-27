package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/** Shared Game Mode dialogs used by Home and Lighting without activity state. */
internal class MainGameModeActions(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit,
    private val ledViews: LedControlViewFactory
) {
    fun showAppPicker(onUpdated: () -> Unit = {}) {
        showGamePickerDialogUI(activity) {
            onUpdated()
        }
    }

    fun showProfileDialog() {
        val content = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(uiKit.dp(16), uiKit.dp(8), uiKit.dp(16), uiKit.dp(24))
        }
        val scroll = android.widget.ScrollView(activity).apply { addView(content) }
        val hero = android.widget.ImageView(activity).apply {
            setImageResource(com.elitedarkkaiser.redmagic.R.drawable.rgb_silver_hero)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = "REDMAGIC gaming phone"
        }
        content.addView(hero, android.widget.LinearLayout.LayoutParams(-1, uiKit.dp(130)))
        val toolbar = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = AppTheme.roundedBg(AppTheme.panelColor, AppTheme.borderColor, uiKit.dp(16).toFloat())
        }
        content.addView(toolbar, android.widget.LinearLayout.LayoutParams(-1, uiKit.dp(48)).apply { bottomMargin = uiKit.dp(12) })
        val list = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
        content.addView(list)
        fun render() {
            list.removeAllViews()
            val manager = activity.packageManager
            getSavedGamePackagesStorage(activity).sortedBy { pkg ->
                runCatching { manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg).lowercase()
            }.forEach { pkg ->
                val title = runCatching { manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                val row = android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(uiKit.dp(16), uiKit.dp(14), uiKit.dp(16), uiKit.dp(14))
                    background = AppTheme.roundedBg(AppTheme.panelColor, AppTheme.borderColor, uiKit.dp(18).toFloat())
                    isClickable = true; isFocusable = true
                    setOnClickListener { showProfileEditor(pkg, title) }
                }
                row.addView(android.widget.ImageView(activity).apply { setImageDrawable(runCatching { manager.getApplicationIcon(pkg) }.getOrNull()) },
                    android.widget.LinearLayout.LayoutParams(uiKit.dp(44), uiKit.dp(44)).apply { marginEnd = uiKit.dp(14) })
                val text = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
                text.addView(android.widget.TextView(activity).apply { this.text = title; textSize = 16f; setTextColor(AppTheme.textPrimary); setTypeface(null, Typeface.BOLD) })
                text.addView(android.widget.TextView(activity).apply { this.text = pkg; textSize = 12f; setTextColor(AppTheme.textSecondary) })
                row.addView(text, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(android.widget.TextView(activity).apply { this.text = "›"; textSize = 24f; setTextColor(AppTheme.textPrimary) })
                list.addView(row, android.widget.LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = uiKit.dp(10) })
            }

        }
        fun toolbarAction(text: String, action: () -> Unit) {
            toolbar.addView(android.widget.TextView(activity).apply {
                this.text = text; textSize = 13f; gravity = android.view.Gravity.CENTER
                setTextColor(AppTheme.textPrimary); isClickable = true; isFocusable = true
                setOnClickListener { action() }
            }, android.widget.LinearLayout.LayoutParams(0, -1, 1f))
        }
        toolbarAction("+ Add") { showAppPicker { render() } }
        toolbarAction("− Remove") {
            val manager = activity.packageManager
            val selected = getSavedGamePackagesStorage(activity)
            val apps = selected.map { pkg -> MaterialAppPicker.App(pkg, runCatching {
                manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)) }.sortedBy { it.label.lowercase() }
            MaterialAppPicker.showMultiple(activity, "Choose games to keep", apps, selected) { kept ->
                setSavedGamePackagesStorage(activity, kept)
                render()
            }
        }
        toolbarAction("⚙ Defaults") { showProfileEditor(null, "Shared defaults") }
        render()
        activity.showGamingPage("Game profiles", scroll)
    }

    fun showProfileEditor(pkg: String?, label: String) {
        GameModeUi.showGameModeProfileDialog(
            activity = activity,
            current = if (pkg == null) getSavedGameModeProfileStorage(activity)
                else getSavedGameModeProfileForPackageStorage(activity, pkg),
            deps = GameModeUi.Deps(
                textPrimary = AppTheme.textPrimary,
                textSecondary = AppTheme.textSecondary,
                panelColor = AppTheme.panelColor,
                borderColor = AppTheme.borderColor,
                panelPressed = AppTheme.panelPressed,
                accent = AppTheme.accentColor,
                typeface = Typeface.SANS_SERIF,
                dp = { value -> uiKit.dp(value) },
                roundedBg = { fill, stroke, radius ->
                    uiKit.roundedBg(fill, stroke, radius)
                },
                roundedFill = { color, radius ->
                    uiKit.roundedFill(color, radius)
                },
                filterChip = { label, selected, onClick ->
                    ledViews.filterChip(label, selected, onClick)
                },
                space = { value -> uiKit.space(value) },
                colorDotDrawable = { hex, selected ->
                    ledViews.colorDotDrawable(hex, selected)
                },
                colorDotGeneric = { hex, selected, onClick ->
                    ledViews.colorDot(hex, selected, onClick)
                }
            ),
            onSaveProfile = { profile ->
                if (pkg == null) saveGameModeProfileStorage(activity, profile)
                else if (!saveGameModeProfileForPackageStorage(activity, pkg, profile)) {
                    throw IllegalStateException("App profile could not be saved")
                }
                GameModeActions.applySavedProfileThroughService(activity)
            },
            appLabel = label.takeIf { pkg != null },
            appPackage = pkg,
            presentPage = { view -> activity.showGamingPage(label, view) }
        )
    }
}
