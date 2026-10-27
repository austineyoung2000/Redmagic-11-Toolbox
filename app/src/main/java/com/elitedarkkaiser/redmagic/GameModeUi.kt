package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.ui.components.SelectionControlStyle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.slider.Slider
import android.widget.TextView

internal object GameModeUi {

    data class Deps(
        val textPrimary: Int,
        val textSecondary: Int,
        val panelColor: Int,
        val borderColor: Int,
        val panelPressed: Int,
        val accent: Int,
        val typeface: Typeface?,
        val dp: (Int) -> Int,
        val roundedBg: (Int, Int, Int) -> Drawable,
        val roundedFill: (Int, Int) -> Drawable,
        val filterChip: (String, Boolean, () -> Unit) -> Button,
        val space: (Int) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable,
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View
    )

    fun showGameModeProfileDialog(
        activity: MainActivity,
        current: GameModeProfile,
        deps: Deps,
        onSaveProfile: (GameModeProfile) -> Unit,
        appLabel: String? = null,
        appPackage: String? = null,
        presentPage: ((View) -> (() -> Unit))? = null
    ) {
        var gmFanEnabled = current.fanEnabled
        var gmFanLevel = current.fanLevel
        var gmPumpEnabled = current.pumpEnabled
        var gmPumpProfile = current.pumpProfile
        var gmFanLedEnabled = current.fanLedEnabled
        var gmFanLedEffect = FanLedPalette.normalizeEffect(
            current.fanLedEffect
        )
        var gmFanLedColor = FanLedPalette.normalizeColor(
            current.fanLedEffect,
            current.fanLedColor
        )

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(deps.dp(16), deps.dp(12), deps.dp(16), deps.dp(24))
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(
                container,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val titleView = TextView(activity).apply {
            text = appLabel?.let { "Edit $it" } ?: "Edit shared Game Mode defaults"
            textSize = 20f
            setTextColor(deps.textPrimary)
            setTypeface(deps.typeface, Typeface.BOLD)
        }

        val subtitleView = TextView(activity).apply {
            text = appPackage?.let { "Applies only while this app is foreground.\n$it" }
                ?: "Starting settings for selected apps without their own saved profile."
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(8), 0, deps.dp(10))
        }

        val fanEnableCheck = MaterialCheckBox(activity).apply {
            text = "Enable fan override"
            isChecked = gmFanEnabled
            textSize = 14f
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setOnCheckedChangeListener { _, checked -> gmFanEnabled = checked }
        }

        val fanLevelLabel = TextView(activity).apply {
            text = "Fan level: $gmFanLevel"
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(10), 0, deps.dp(4))
        }

        val fanLevelSeek = SelectionControlStyle.slider(activity).apply {
            valueFrom = 0f
            valueTo = 5f
            stepSize = 1f
            value = gmFanLevel.coerceIn(0, 5).toFloat()

            addOnChangeListener { _, newValue, _ ->
                gmFanLevel = newValue.toInt()
                fanLevelLabel.text = "Fan level: $gmFanLevel"
            }
        }

        val pumpEnableCheck = MaterialCheckBox(activity).apply {
            text = "Enable pump override"
            isChecked = gmPumpEnabled
            textSize = 14f
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setPadding(0, deps.dp(10), 0, 0)
            setOnCheckedChangeListener { _, checked -> gmPumpEnabled = checked }
        }

        val pumpLabel = TextView(activity).apply {
            text = "Pump profile"
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(10), 0, deps.dp(6))
        }

        lateinit var slowBtn: Button
        lateinit var mediumBtn: Button
        lateinit var quickBtn: Button

        fun refreshPumpButtons() {
            GameModeActions.refreshPumpButtons(
                selectedProfile = gmPumpProfile,
                slowBtn = slowBtn,
                mediumBtn = mediumBtn,
                quickBtn = quickBtn,
                roundedFill = deps.roundedFill,
                selectedColor = deps.panelPressed,
                unselectedColor = deps.panelColor
            )
        }

        fun gmPumpBtn(label: String, value: String): Button {
            return deps.filterChip(label, gmPumpProfile == value) {
                GameModeActions.updatePumpProfile(
                    value = value,
                    onProfileChanged = { newValue -> gmPumpProfile = newValue },
                    refreshButtons = { refreshPumpButtons() }
                )
            }
        }

        val pumpRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        slowBtn = gmPumpBtn("Slow", "slow")
        mediumBtn = gmPumpBtn("Medium", "medium")
        quickBtn = gmPumpBtn("Quick", "quick")

        pumpRow.addView(slowBtn)
        pumpRow.addView(deps.space(deps.dp(8)))
        pumpRow.addView(mediumBtn)
        pumpRow.addView(deps.space(deps.dp(8)))
        pumpRow.addView(quickBtn)

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, deps.dp(18), 0, 0)
        }

        val cancelBtn = MaterialButton(
            activity,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Cancel"
            textSize = 13f
            isAllCaps = false
            setTextColor(deps.textPrimary)

            backgroundTintList =
                ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = deps.dp(1)
            strokeColor =
                ColorStateList.valueOf(deps.borderColor)
            rippleColor =
                ColorStateList.valueOf(com.elitedarkkaiser.redmagic.ui.AppTheme.rippleColor)
            cornerRadius = deps.dp(14)

            insetTop = 0
            insetBottom = 0
            minHeight = deps.dp(48)
            setPadding(
                deps.dp(18),
                deps.dp(10),
                deps.dp(18),
                deps.dp(10)
            )
        }

        val saveBtn = MaterialButton(activity).apply {
            text = "Save"
            textSize = 13f
            isAllCaps = false
            setTextColor(deps.textPrimary)

            backgroundTintList =
                ColorStateList.valueOf(deps.panelPressed)
            rippleColor =
                ColorStateList.valueOf(com.elitedarkkaiser.redmagic.ui.AppTheme.rippleColor)
            cornerRadius = deps.dp(14)

            insetTop = 0
            insetBottom = 0
            minHeight = deps.dp(48)
            setPadding(
                deps.dp(20),
                deps.dp(10),
                deps.dp(20),
                deps.dp(10)
            )
        }

        buttonRow.addView(cancelBtn)
        buttonRow.addView(deps.space(deps.dp(10)))
        buttonRow.addView(saveBtn)

        fun card(): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(deps.dp(16), deps.dp(14), deps.dp(16), deps.dp(16))
            background = deps.roundedBg(deps.panelColor, deps.borderColor, 22)
        }
        fun addCard(view: View) = container.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = deps.dp(12)
        })
        if (presentPage != null) {
            container.addView(android.widget.ImageView(activity).apply {
                setImageResource(R.drawable.rgb_silver_hero)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = "REDMAGIC 11 Pro with illuminated shoulder triggers"
            }, LinearLayout.LayoutParams(-1, deps.dp(130)))
        }
        container.addView(titleView)
        container.addView(subtitleView)
        val coolingCard = card()
        coolingCard.addView(fanEnableCheck)
        coolingCard.addView(fanLevelLabel)
        coolingCard.addView(fanLevelSeek)
        coolingCard.addView(pumpEnableCheck)
        coolingCard.addView(pumpLabel)
        coolingCard.addView(pumpRow)
        addCard(coolingCard)

        var gmLogoLedEnabled = current.logoLedEnabled
        var gmLogoLedEffect = current.logoLedEffect
        var gmLogoLedColor = current.logoLedColor
        var gmShoulderLedEnabled = current.shoulderLedEnabled
        var gmShoulderLedEffect = current.shoulderLedEffect
        var gmShoulderLedColor = current.shoulderLedColor
        GameProfileLightingUi.create(activity, deps,
            com.elitedarkkaiser.redmagic.state.LedState(gmFanLedEnabled, gmFanLedEffect, gmFanLedColor),
            com.elitedarkkaiser.redmagic.state.LedState(gmLogoLedEnabled, gmLogoLedEffect, gmLogoLedColor),
            com.elitedarkkaiser.redmagic.state.LedState(gmShoulderLedEnabled, gmShoulderLedEffect, gmShoulderLedColor),
            onFan = { gmFanLedEnabled = it.enabled; gmFanLedEffect = it.effect; gmFanLedColor = it.color },
            onLogo = { gmLogoLedEnabled = it.enabled; gmLogoLedEffect = it.effect; gmLogoLedColor = it.color },
            onTrigger = { gmShoulderLedEnabled = it.enabled; gmShoulderLedEffect = it.effect; gmShoulderLedColor = it.color }
        ).forEach(::addCard)

        container.addView(buttonRow)

        refreshPumpButtons()

        var dialog: androidx.appcompat.app.AlertDialog? = null
        var closePage: (() -> Unit)? = null
        if (presentPage == null) {
            dialog = MaterialAlertDialogBuilder(activity).setView(scroll).setCancelable(true).create()
        }
        val closeEditor = { if (presentPage == null) dialog?.dismiss() else closePage?.invoke(); Unit }
        cancelBtn.setOnClickListener { closeEditor() }

        saveBtn.setOnClickListener {
            runCatching {
            GameModeActions.saveProfile(
                context = activity,
                profile = GameModeActions.buildProfile(
                    fanEnabled = gmFanEnabled,
                    fanLevel = gmFanLevel,
                    pumpEnabled = gmPumpEnabled,
                    pumpProfile = gmPumpProfile,
                    fanLedEnabled = gmFanLedEnabled,
                    fanLedEffect = gmFanLedEffect,
                    fanLedColor = gmFanLedColor,
                    logoLedEnabled = gmLogoLedEnabled,
                    logoLedEffect = gmLogoLedEffect,
                    logoLedColor = gmLogoLedColor,
                    shoulderLedEnabled = gmShoulderLedEnabled,
                    shoulderLedEffect = gmShoulderLedEffect,
                    shoulderLedColor = gmShoulderLedColor
                ),
                persistProfile = onSaveProfile,
                onSaved = { closeEditor() }
            )
            }.onFailure {
                android.widget.Toast.makeText(activity, "Profile could not be saved. Try again.", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        if (presentPage != null) {
            closePage = presentPage(scroll)
        } else {
            dialog?.show()
            dialog?.window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                setDimAmount(0.65f)
            }
        }
    }
}
