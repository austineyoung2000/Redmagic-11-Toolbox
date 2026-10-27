package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.LightingTabDeps
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

/** Inline normal lighting controls backed by the existing saved zone selections. */
internal object InlineLightingUi {
    private val ids = listOf(1, 3, 4, 5, 6, 7, 8, 9)
    private val paletteColors = listOf("#FF0000", "#FF8C00", "#FFD600", "#00E676", "#00E5FF", "#1565FF", "#A020F0", "#FF69B4")

    fun create(activity: Activity, deps: LightingTabDeps): List<View> {
        return createProfiles(activity, deps, savedFanLedStateStorage(activity), savedLogoLedStateStorage(activity), savedShoulderLedStateStorage(activity), deps.saveInlineFan, deps.saveInlineLogo, deps.saveInlineShoulder)
    }

    fun createProfiles(activity: Activity, deps: LightingTabDeps, initialFan: LedState, initialLogo: LedState, initialTrigger: LedState,
        saveFan: (LedState) -> Unit, saveLogo: (LedState) -> Unit, saveTrigger: (LedState) -> Unit): List<View> {
        var fan = initialFan
        var logo = initialLogo
        var trigger = initialTrigger
        val cards = listOf(deps.sectionPanel(), deps.sectionPanel(), deps.sectionPanel(), deps.sectionPanel())
        val available = !deps.capabilities.scanComplete || deps.capabilities.ledAvailable
        fun text(value: String, size: Float = 13f, bold: Boolean = false) = TextView(activity).apply {
            this.text = value
            textSize = size
            setTextColor(if (bold) AppTheme.textPrimary else AppTheme.textSecondary)
            if (bold) setTypeface(AppTheme.appTypeface, Typeface.BOLD)
        }
        fun heading(card: LinearLayout, title: String, enabled: Boolean, change: (Boolean) -> Unit) {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(title, 18f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(MaterialSwitch(activity).apply { isChecked = enabled; setOnCheckedChangeListener { _, checked -> change(checked) } })
            card.addView(row)
        }
        fun label(card: LinearLayout, title: String) {
            card.addView(text(title).apply { setPadding(0, deps.dp(12), 0, deps.dp(6)) })
        }
        fun palette(card: LinearLayout, title: String, selected: Int?, change: (Int) -> Unit) {
            label(card, title)
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            ids.forEachIndexed { index, id ->
                val item = View(activity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(paletteColors[index]))
                        if (selected == id) setStroke(deps.dp(2), Color.WHITE)
                    }
                    isClickable = true; isFocusable = true
                    contentDescription = "${listOf("Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink")[index]}${if (selected == id) ", selected" else ""}"
                    setOnClickListener { change(id) }
                }
                if (index > 0) row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
                row.addView(item, LinearLayout.LayoutParams(deps.dp(29), deps.dp(29)))
            }
            card.addView(row)
        }
        fun effects(card: LinearLayout, effect: String, isFan: Boolean, change: (String) -> Unit) {
            label(card, "Effect")
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            val values = if (isFan) listOf("steady", "breathe", "flashing", "rapid", "blink") else listOf("steady", "breathe", "flashing", "rapid")
            values.forEachIndexed { index, value ->
                val b = deps.filterChip(value.replaceFirstChar { it.uppercase() }, value == effect) { change(value) }
                row.addView(b, LinearLayout.LayoutParams(0, deps.dp(44), 1f).apply {
                    if (index > 0) marginStart = deps.dp(4)
                })
            }
            card.addView(row)
        }
        fun brightness(card: LinearLayout, level: Int, change: (Int) -> Unit) {
            label(card, "Brightness • $level / 255")
            card.addView(com.elitedarkkaiser.redmagic.ui.components.SelectionControlStyle.slider(activity).apply {
                valueFrom = 32f; valueTo = 255f; stepSize = 0f; value = level.toFloat()
                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) = Unit
                    override fun onStopTrackingTouch(slider: Slider) { change(slider.value.toInt()) }
                })
            })
        }
        fun rgbId(rgb: Int): Int? = ShoulderLedSplit.colors.firstOrNull { ShoulderLedSplit.parseRgb(it.hex) == rgb }?.id
        lateinit var redraw: () -> Unit
        fun render() {
            cards.forEach { it.removeAllViews() }
            val fanCard = cards[0]
            heading(fanCard, "Fan lighting", fan.enabled) { fan = fan.copy(enabled = it); saveFan(fan); redraw() }
            palette(fanCard, "Color", fan.color) { fan = fan.copy(color = it); saveFan(fan); redraw() }
            label(fanCard, "Multicolor palettes")
            val paletteSets = listOf(
                listOf("#FF69B4", "#FF0000", "#FF8C00", "#FF8C00"),
                listOf("#1565FF", "#00E676", "#22D3EE", "#FF69B4"),
                listOf("#22D3EE", "#FF0000", "#FFD600", "#FF69B4"),
                listOf("#00E676", "#FF69B4", "#FF8C00", "#22D3EE"),
                listOf("#00E676", "#A020F0", "#FF8C00", "#FF69B4"),
                listOf("#FF0000", "#FF0000", "#FF0000", "#FF0000"),
                listOf("#22D3EE", "#FF8C00", "#22D3EE", "#A020F0"),
                listOf("#22D3EE", "#FF0000", "#FF8C00", "#00E676")
            )
            val bubbleFactory = com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory(activity)
            val presets = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            paletteSets.forEachIndexed { index, colors ->
                val id = 0x101 + index
                if (index > 0) presets.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
                val bubble = bubbleFactory.fanPresetBubble(colors, { fan.color == id }) {
                    fan = fan.copy(color = id); saveFan(fan); redraw()
                }.apply { contentDescription = "Multicolor palette ${index + 1}${if (fan.color == id) ", selected" else ""}" }
                presets.addView(bubble, LinearLayout.LayoutParams(deps.dp(29), deps.dp(29)))
            }
            fanCard.addView(presets)
            effects(fanCard, LedBrightness.effect(fan.effect), true) { fan = fan.copy(effect = LedBrightness.withEffect(fan.effect, it)); saveFan(fan); redraw() }
            brightness(fanCard, LedBrightness.level(fan.effect)) { fan = fan.copy(effect = LedBrightness.encode(it, fan.effect)); saveFan(fan); redraw() }
            val pair = LogoBarSelection.from(logo)
            fun updatePair(next: LogoBarSelection, color: Int = logo.color) { logo = next.state(color); saveLogo(logo); redraw() }
            val logoCard = cards[1]
            heading(logoCard, "Logo lighting", pair.logoEnabled) { updatePair(pair.copy(logoEnabled = it)) }
            palette(logoCard, "Color", logo.color) { updatePair(pair, it) }
            effects(logoCard, pair.effect, false) { updatePair(pair.copy(effect = it)) }
            brightness(logoCard, pair.logoBrightness) { updatePair(pair.copy(logoBrightness = it)) }
            logoCard.addView(text("Logo and GAME MODE bar share the same effect."))
            val barCard = cards[2]
            heading(barCard, "GAME MODE bar", pair.barEnabled) { updatePair(pair.copy(barEnabled = it)) }
            palette(barCard, "Color", pair.barColor) { updatePair(pair.copy(barColor = it)) }
            effects(barCard, pair.effect, false) { updatePair(pair.copy(effect = it)) }
            brightness(barCard, pair.barBrightness) { updatePair(pair.copy(barBrightness = it)) }
            barCard.addView(text("Effect is shared with the logo."))
            val tr = cards[3]
            val split = ShoulderLedSplit.decode(trigger.effect)
            val effect = ShoulderLedSplit.baseEffect(trigger.effect)
            val level = LedBrightness.level(trigger.effect)
            fun updateTrigger(enabled: Boolean = trigger.enabled, color: Int = trigger.color, mode: String = effect, top: Int? = split?.topRgb, bottom: Int? = split?.bottomRgb, brightness: Int = level) {
                val base = if (top != null && bottom != null) ShoulderLedSplit.encode(mode, top, bottom) else mode
                trigger = LedState(enabled, LedBrightness.encode(brightness, base), color)
                saveTrigger(trigger); redraw()
            }
            heading(tr, "Trigger lighting", trigger.enabled) { updateTrigger(enabled = it) }
            palette(tr, if (split == null) "Color" else "Top trigger", if (split == null) trigger.color else rgbId(split.topRgb)) {
                if (split == null) updateTrigger(color = it) else updateTrigger(color = it, top = ShoulderLedSplit.presetRgb(it))
            }
            tr.addView(MaterialSwitch(activity).apply {
                text = "Split trigger colors"; setTextColor(AppTheme.textPrimary); isChecked = split != null
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        val rgb = ShoulderLedSplit.presetRgb(trigger.color)
                        updateTrigger(top = rgb, bottom = rgb)
                    } else updateTrigger(color = split?.topRgb?.let(::rgbId) ?: trigger.color, top = null, bottom = null)
                }
            })
            if (split != null) palette(tr, "Bottom trigger", rgbId(split.bottomRgb)) { updateTrigger(bottom = ShoulderLedSplit.presetRgb(it)) }
            effects(tr, effect, false) { updateTrigger(mode = it) }
            brightness(tr, level) { updateTrigger(brightness = it) }
            tr.addView(text("Both triggers share one effect and brightness."))
            if (!available) cards.forEach { com.elitedarkkaiser.redmagic.ui.CapabilityUi.disableInteractions(it) }
        }
        redraw = ::render
        redraw()
        return cards
    }
}
