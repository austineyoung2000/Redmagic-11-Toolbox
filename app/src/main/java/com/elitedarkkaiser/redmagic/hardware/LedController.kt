package com.elitedarkkaiser.redmagic.hardware

import com.elitedarkkaiser.redmagic.HardwareController
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.state.LightingState

object LedController {
    const val EFFECT_STEADY = "steady"
    const val EFFECT_BREATHE = "breathe"
    const val EFFECT_FLASHING = "flashing"
    const val EFFECT_RAPID = "rapid"

    fun setFanEnabled(enabled: Boolean): Boolean {
        return HardwareController.setFanLedEnabled(enabled)
    }

    fun setLogoEnabled(enabled: Boolean): Boolean {
        return HardwareController.setLogoLedEnabled(enabled)
    }

    fun setShoulderEnabled(enabled: Boolean): Boolean {
        return HardwareController.setShoulderLedEnabled(enabled)
    }

    fun setFanEffect(effect: String, color: Int): Boolean {
        return HardwareController.setFanLedEffect(normalizeEffect(effect), color)
    }

    fun setLogoEffect(effect: String, color: Int): Boolean {
        return HardwareController.setLogoLedEffect(normalizeEffect(effect), color)
    }

    fun setShoulderEffect(effect: String, color: Int): Boolean {
        return HardwareController.setShoulderLedEffect(effect, color)
    }

    fun setFanStockPreset(effectValue: String): Boolean {
        return HardwareController.setFanLedStockPreset(effectValue)
    }

    fun turnOffAll(): Boolean {
        return HardwareController.turnOffAllLeds()
    }

    fun applyFan(state: LedState): Boolean {
        return if (state.enabled) {
            setFanEffect(state.effect, state.color)
        } else {
            setFanEnabled(false)
        }
    }

    fun applyLogo(state: LedState): Boolean {
        return if (state.enabled) {
            setLogoEffect(state.effect, state.color)
        } else {
            setLogoEnabled(false)
        }
    }

    fun applyShoulder(state: LedState): Boolean {
        return if (state.enabled) {
            setShoulderEffect(state.effect, state.color)
        } else {
            setShoulderEnabled(false)
        }
    }

    fun applyLighting(state: LightingState): Boolean {
        val fanOk = applyFan(state.fan)
        val logoOk = applyLogo(state.logo)
        val shoulderOk = applyShoulder(state.shoulder)
        return fanOk && logoOk && shoulderOk
    }

    fun normalizeEffect(effect: String): String {
        if (com.elitedarkkaiser.redmagic.LedBrightness.decode(effect) != null) return effect
        return when (effect.lowercase()) {
            EFFECT_BREATHE -> EFFECT_BREATHE
            EFFECT_FLASHING -> EFFECT_FLASHING
            "blink" -> "blink"
            EFFECT_RAPID -> EFFECT_RAPID
            else -> EFFECT_STEADY
        }
    }

    fun normalizeColor(color: Int): Int {
        return color.coerceIn(1, 9)
    }
}
