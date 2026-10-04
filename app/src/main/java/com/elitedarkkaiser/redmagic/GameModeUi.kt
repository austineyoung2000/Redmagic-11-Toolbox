package com.elitedarkkaiser.redmagic

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
        onSaveProfile: (GameModeProfile) -> Unit
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
            setPadding(deps.dp(22), deps.dp(18), deps.dp(22), deps.dp(12))
            background = deps.roundedBg(deps.panelColor, deps.borderColor, 22)
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
            text = "Edit Game Profile"
            textSize = 20f
            setTextColor(deps.textPrimary)
            setTypeface(deps.typeface, Typeface.BOLD)
        }

        val subtitleView = TextView(activity).apply {
            text = "These settings will apply automatically when a selected game launches."
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

        val fanLevelSeek = Slider(activity).apply {
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

        val ledEnableCheck = MaterialCheckBox(activity).apply {
            text = "Enable fan LED override"
            isChecked = gmFanLedEnabled
            textSize = 14f
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setPadding(0, deps.dp(10), 0, 0)
            setOnCheckedChangeListener { _, checked -> gmFanLedEnabled = checked }
        }

        val ledEffectLabel = TextView(activity).apply {
            text = "Fan LED effect"
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(10), 0, deps.dp(6))
        }

        lateinit var ledSteadyBtn: Button
        lateinit var ledBreatheBtn: Button
        lateinit var ledFlashingBtn: Button
        lateinit var ledRapidBtn: Button

        var refreshBlink: (() -> Unit)? = null
        fun refreshLedEffectButtons() {
            refreshBlink?.invoke()
            GameModeActions.refreshLedEffectButtons(
                selectedEffect = LedBrightness.effect(gmFanLedEffect),
                steadyBtn = ledSteadyBtn,
                breatheBtn = ledBreatheBtn,
                flashingBtn = ledFlashingBtn,
                rapidBtn = ledRapidBtn,
                roundedFill = deps.roundedFill,
                selectedColor = deps.panelPressed,
                unselectedColor = deps.panelColor
            )
        }

        fun gmLedEffectBtn(label: String, value: String): Button {
            return deps.filterChip(label, LedBrightness.effect(gmFanLedEffect) == value) {
                GameModeActions.updateLedEffect(
                    value = value,
                    onEffectChanged = { newValue -> gmFanLedEffect = LedBrightness.withEffect(gmFanLedEffect, newValue) },
                    refreshButtons = { refreshLedEffectButtons() }
                )
            }
        }

        val ledEffectRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        ledSteadyBtn = gmLedEffectBtn("Steady", "steady")
        ledBreatheBtn = gmLedEffectBtn("Breathe", "breathe")
        ledFlashingBtn = gmLedEffectBtn("Flashing", "flashing")
        ledRapidBtn = gmLedEffectBtn("Rapid", "rapid")

        ledEffectRow.addView(ledSteadyBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        ledEffectRow.addView(deps.space(deps.dp(8)))
        ledEffectRow.addView(ledBreatheBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        ledEffectRow.addView(deps.space(deps.dp(8)))
        ledEffectRow.addView(ledFlashingBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        ledEffectRow.addView(deps.space(deps.dp(8)))
        ledEffectRow.addView(ledRapidBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))

        val ledColorLabel = TextView(activity).apply {
            text = "Fan LED color"
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(10), 0, deps.dp(6))
        }

        lateinit var colorRow: LinearLayout
        lateinit var colorRow2: LinearLayout

        fun refreshLedColorDots() {
            colorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", gmFanLedColor == 1)
            colorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", gmFanLedColor == 3)
            colorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", gmFanLedColor == 4)
            colorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", gmFanLedColor == 5)
            colorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", gmFanLedColor == 6)
            colorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", gmFanLedColor == 7)
            colorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", gmFanLedColor == 8)
            colorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", gmFanLedColor == 9)
        }

        fun gmColorDot(id: Int, hex: String): View {
            return deps.colorDotGeneric(hex, gmFanLedColor == id && !gmFanLedEffect.startsWith("preset:")) {
                GameModeActions.updateLedColor(
                    id = id,
                    currentEffect = gmFanLedEffect,
                    onColorChanged = { newColor -> gmFanLedColor = newColor },
                    onEffectChanged = { newEffect -> gmFanLedEffect = newEffect },
                    refreshColorDots = { refreshLedColorDots() },
                    refreshEffectButtons = { refreshLedEffectButtons() }
                )
            }
        }

        colorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(gmColorDot(1, "#FF0000"))
            addView(deps.space(deps.dp(10)))
            addView(gmColorDot(3, "#FF8C00"))
            addView(deps.space(deps.dp(10)))
            addView(gmColorDot(4, "#FFD600"))
            addView(deps.space(deps.dp(10)))
            addView(gmColorDot(5, "#00E676"))
        }

        colorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(gmColorDot(6, "#00E5FF"))
            addView(deps.space(deps.dp(10)))
            addView(gmColorDot(7, "#1565FF"))
            addView(deps.space(deps.dp(10)))
            addView(gmColorDot(8, "#A020F0"))
            addView(deps.space(deps.dp(10)))
            addView(gmColorDot(9, "#FF69B4"))
        }

        lateinit var preset1: View
        lateinit var preset2: View
        lateinit var preset3: View
        lateinit var preset4: View
        lateinit var preset5: View
        lateinit var preset6: View
        lateinit var preset7: View
        lateinit var preset8: View

        fun refreshPresetBubbles() {
            fun presetRing(selected: Boolean): GradientDrawable {
                return GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(deps.dp(3), if (selected) Color.WHITE else Color.TRANSPARENT)
                }
            }

            preset1.alpha = 1f
            preset2.alpha = 1f
            preset3.alpha = 1f
            preset4.alpha = 1f
            preset5.alpha = 1f
            preset6.alpha = 1f
            preset7.alpha = 1f
            preset8.alpha = 1f

            preset1.background = presetRing(gmFanLedColor == 0x101)
            preset2.background = presetRing(gmFanLedColor == 0x102)
            preset3.background = presetRing(gmFanLedColor == 0x103)
            preset4.background = presetRing(gmFanLedColor == 0x104)
            preset5.background = presetRing(gmFanLedColor == 0x105)
            preset6.background = presetRing(gmFanLedColor == 0x106)
            preset7.background = presetRing(gmFanLedColor == 0x107)
            preset8.background = presetRing(gmFanLedColor == 0x108)
        }

            fun gmPresetBubble(hex1: String, hex2: String, hex3: String, hex4: String, value: String): View {
        return object : View(activity) {
            private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = deps.dp(3).toFloat()
            }

            init {
                val size = deps.dp(42)
                layoutParams = LinearLayout.LayoutParams(size, size)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    GameModeActions.applyLedPreset(
                        value = value,
                        currentEffect = gmFanLedEffect,
                        onEffectChanged = { gmFanLedEffect = it },
                        onColorChanged = { gmFanLedColor = it },
                        refreshEffectButtons = { refreshLedEffectButtons() },
                        refreshColorDots = { refreshLedColorDots() },
                        refreshPresetBubbles = { refreshPresetBubbles() }
                    )
                }
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)

                val pad = deps.dp(3).toFloat()
                val rect = android.graphics.RectF(
                    pad, pad,
                    width.toFloat() - pad,
                    height.toFloat() - pad
                )

                val save = canvas.save()
                val path = android.graphics.Path().apply {
                    addOval(rect, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(path)

                val midX = rect.centerX()
                val midY = rect.centerY()

                val colors = arrayOf(hex1, hex2, hex3, hex4)

                fillPaint.color = Color.parseColor(colors[0])
                canvas.drawRect(rect.left, rect.top, midX, midY, fillPaint)

                fillPaint.color = Color.parseColor(colors[1])
                canvas.drawRect(midX, rect.top, rect.right, midY, fillPaint)

                fillPaint.color = Color.parseColor(colors[2])
                canvas.drawRect(rect.left, midY, midX, rect.bottom, fillPaint)

                fillPaint.color = Color.parseColor(colors[3])
                canvas.drawRect(midX, midY, rect.right, rect.bottom, fillPaint)

                canvas.restoreToCount(save)

                ringPaint.color = if (
                    FanLedPalette.isSelected(
                        gmFanLedEffect,
                        gmFanLedColor,
                        value
                    )
                ) {
                    Color.WHITE
                } else {
                    Color.TRANSPARENT
                }

                canvas.drawOval(rect, ringPaint)
            }
        }
    }

        preset1 = gmPresetBubble("#FF69B4", "#FF0000", "#FF8C00", "#FF8C00", "0x3002101")
        preset2 = gmPresetBubble("#1565FF", "#00E676", "#22D3EE", "#FF69B4", "0x3002102")
        preset3 = gmPresetBubble("#22D3EE", "#FF0000", "#FFD600", "#FF69B4", "0x3002103")
        preset4 = gmPresetBubble("#00E676", "#FF69B4", "#FF8C00", "#22D3EE", "0x3002104")
        preset5 = gmPresetBubble("#00E676", "#A020F0", "#FF8C00", "#FF69B4", "0x3002105")
        preset6 = gmPresetBubble("#FF0000", "#FF0000", "#FF0000", "#FF0000", "0x3002106")
        preset7 = gmPresetBubble("#22D3EE", "#FF8C00", "#22D3EE", "#A020F0", "0x3002107")
        preset8 = gmPresetBubble("#22D3EE", "#FF0000", "#FF8C00", "#00E676", "0x3002108")

        val presetRow1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(preset1)
            addView(deps.space(deps.dp(10)))
            addView(preset2)
            addView(deps.space(deps.dp(10)))
            addView(preset3)
            addView(deps.space(deps.dp(10)))
            addView(preset4)
        }

        val presetRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(preset5)
            addView(deps.space(deps.dp(10)))
            addView(preset6)
            addView(deps.space(deps.dp(10)))
            addView(preset7)
            addView(deps.space(deps.dp(10)))
            addView(preset8)
        }

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

        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(fanEnableCheck)
        container.addView(fanLevelLabel)
        container.addView(fanLevelSeek)
        container.addView(pumpEnableCheck)
        container.addView(pumpLabel)
        container.addView(pumpRow)
        container.addView(ledEnableCheck)
        container.addView(ledEffectLabel)
        container.addView(ledEffectRow)
        container.addView(ledColorLabel)
        container.addView(colorRow)
        container.addView(colorRow2)
        container.addView(presetRow1)
        container.addView(presetRow2)
        val fanBlink = deps.filterChip("Blink", LedBrightness.effect(gmFanLedEffect) == "blink") {
            gmFanLedEffect = LedBrightness.withEffect(gmFanLedEffect, "blink")
            refreshLedEffectButtons()
        }
        refreshBlink = {
            val selected = LedBrightness.effect(gmFanLedEffect) == "blink"
            fanBlink.isSelected = selected
            if (fanBlink is com.google.android.material.button.MaterialButton) {
                fanBlink.backgroundTintList = ColorStateList.valueOf(if (selected) deps.panelPressed else deps.panelColor)
            } else fanBlink.background = deps.roundedFill(if (selected) deps.panelPressed else deps.panelColor, 999)
        }
        refreshBlink?.invoke()
        container.addView(fanBlink)
        LedBrightnessUi(activity, container, "fan", { gmFanLedEffect }, { gmFanLedColor },
            { gmFanLedEffect = it }, {}, deps.textSecondary, deps.accent, deps.dp)

        var gmLogoLedEnabled = current.logoLedEnabled
        var gmLogoLedEffect = current.logoLedEffect
        var gmLogoLedColor = current.logoLedColor
        var gmShoulderLedEnabled = current.shoulderLedEnabled
        var gmShoulderLedEffect = current.shoulderLedEffect
        var gmShoulderLedColor = current.shoulderLedColor

        GameModeLedZoneUi.create(
            activity = activity,
            deps = deps,
            title = "Logo LED",
            enableLabel = "Enable logo LED",
            initialEnabled = gmLogoLedEnabled,
            initialEffect = gmLogoLedEffect,
            initialColor = gmLogoLedColor,
            shoulderZone = false,
            onEnabledChanged = { gmLogoLedEnabled = it },
            onEffectChanged = { gmLogoLedEffect = it },
            onColorChanged = { gmLogoLedColor = it }
        ).forEach(container::addView)

        GameModeLedZoneUi.create(
            activity = activity,
            deps = deps,
            title = "Shoulder LEDs",
            enableLabel = "Enable shoulder LEDs",
            initialEnabled = gmShoulderLedEnabled,
            initialEffect = gmShoulderLedEffect,
            initialColor = gmShoulderLedColor,
            shoulderZone = true,
            onEnabledChanged = { gmShoulderLedEnabled = it },
            onEffectChanged = { gmShoulderLedEffect = it },
            onColorChanged = { gmShoulderLedColor = it }
        ).forEach(container::addView)

        container.addView(buttonRow)

        refreshPumpButtons()
        refreshLedEffectButtons()
        refreshLedColorDots()
        refreshPresetBubbles()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(scroll)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

        cancelBtn.setOnClickListener { dialog.dismiss() }

        saveBtn.setOnClickListener {
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
                onSaved = { dialog.dismiss() }
            )
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            setDimAmount(0.65f)
        }
    }
}
