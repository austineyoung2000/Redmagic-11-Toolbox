package com.elitedarkkaiser.redmagic

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GameModeProfileCodecTest {
    private val defaults = GameModeProfile(true, 3, true, "quick", true, "breathe", 7,
        false, "rapid", 8, true, "steady", 5)

    @Test fun legacyPartialProfileInheritsMissingCoolingAndLightingFields() {
        val decoded = GameModeProfileCodec.decode("""{"fanLevel":5,"fanLedColor":1}""", defaults)
        assertEquals(defaults.copy(fanLevel = 5, fanLedColor = 1), decoded)
    }

    @Test fun appProfilesRemainIndependentAndRoundTripFullSettings() {
        val first = defaults.copy(fanLevel = 1, pumpEnabled = false, pumpProfile = "slow",
            logoLedEffect = LogoBarSelection("breathe", true, 128, true, 1, 32).encode(),
            shoulderLedEffect = "dim:128:" + ShoulderLedSplit.encode("breathe", 0xff0000, 0x00ffff))
        val second = defaults.copy(fanLevel = 5, pumpProfile = "medium", fanLedEffect = "dim:32:steady")
        assertEquals(first, GameModeProfileCodec.decode(GameModeProfileCodec.encode(first), defaults))
        assertEquals(second, GameModeProfileCodec.decode(GameModeProfileCodec.encode(second), defaults))
        assertEquals(defaults, GameModeProfileCodec.decode(null, defaults))
    }

    @Test fun explicitFalseAndZeroDoNotBecomeDefaults() {
        val decoded = GameModeProfileCodec.decode("""{"fanEnabled":false,"fanLevel":0,"pumpEnabled":false,"fanLedEnabled":false,"shoulderLedEnabled":false}""", defaults)
        assertFalse(decoded.fanEnabled); assertEquals(0, decoded.fanLevel)
        assertFalse(decoded.pumpEnabled); assertFalse(decoded.fanLedEnabled); assertFalse(decoded.shoulderLedEnabled)
    }

    @Test fun malformedOrInvalidFieldsKeepKnownSettings() {
        assertEquals(defaults, GameModeProfileCodec.decode("not JSON", defaults))
        assertEquals(defaults, GameModeProfileCodec.decode("""{"fanLevel":99,"pumpProfile":"invalid","logoLedEffect":"unknown","fanLedColor":-1}""", defaults))
    }

    @Test fun writingKeepsUnrelatedLegacyMetadataAndResolvesStockPreset() {
        val previous = """{"extra":{"saved":true},"fanLedModeType":"preset","fanLedPresetValue":"0x3002101"}"""
        val decoded = GameModeProfileCodec.decode(previous, defaults)
        assertEquals(0x101, decoded.fanLedColor)
        val encoded = JSONObject(GameModeProfileCodec.encode(decoded, previous))
        assertTrue(encoded.getJSONObject("extra").getBoolean("saved"))
        assertEquals("basic", encoded.getString("fanLedModeType"))
        assertEquals(decoded, GameModeProfileCodec.decode(encoded.toString(), defaults))
    }
}
