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
        var fan = savedFanLedStateStorage(activity)
        var logo = savedLogoLedStateStorage(activity)
        var trigger = savedShoulderLedStateStorage(activity)
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
                row.addView(item, LinearLayout.LayoutParams(0, deps.dp(32), 1f).apply { marginEnd = deps.dp(5) })
            }
            card.addView(row)
        }
        fun effects(card: LinearLayout, effect: String, isFan: Boolean, change: (String) -> Unit) {
            label(card, "Effect")
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            val values = if (isFan) listOf("steady", "breathe", "flashing", "rapid", "blink") else listOf("steady", "breathe", "flashing", "rapid")
            values.forEach { value ->
                val b = deps.filterChip(value.replaceFirstChar { it.uppercase() }, value == effect) { change(value) }
                row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            card.addView(row)
        }
        fun brightness(card: LinearLayout, level: Int, change: (Int) -> Unit) {
            label(card, "Brightness • $level / 255")
            card.addView(Slider(activity).apply {
                valueFrom = 32f; valueTo = 255f; stepSize = 1f; value = level.toFloat()
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
            heading(fanCard, "Fan lighting", fan.enabled) { fan = fan.copy(enabled = it); deps.saveInlineFan(fan); redraw() }
            palette(fanCard, "Color", fan.color) { fan = fan.copy(color = it); deps.saveInlineFan(fan); redraw() }
            label(fanCard, "Multicolor palettes")
            val presets = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            (0x101..0x108).forEachIndexed { index, id ->
                presets.addView(deps.filterChip("${index + 1}", fan.color == id) {
                    fan = fan.copy(color = id); deps.saveInlineFan(fan); redraw()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            fanCard.addView(presets)
            effects(fanCard, LedBrightness.effect(fan.effect), true) { fan = fan.copy(effect = LedBrightness.withEffect(fan.effect, it)); deps.saveInlineFan(fan); redraw() }
            brightness(fanCard, LedBrightness.level(fan.effect)) { fan = fan.copy(effect = LedBrightness.encode(it, fan.effect)); deps.saveInlineFan(fan); redraw() }
            val pair = LogoBarSelection.from(logo)
            fun updatePair(next: LogoBarSelection, color: Int = logo.color) { logo = next.state(color); deps.saveInlineLogo(logo); redraw() }
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
                deps.saveInlineShoulder(trigger); redraw()
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
