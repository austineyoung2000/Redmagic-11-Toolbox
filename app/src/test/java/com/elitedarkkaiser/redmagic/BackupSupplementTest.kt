package com.elitedarkkaiser.redmagic

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupSupplementTest {
    private fun backup(): JSONObject {
        val app=JSONObject().put("color",7).put("effect","breathe").put("seconds",10)
            .put("brightness",128).put("logo",true).put("triggers",true).put("fan",false)
            .put("logoState",JSONObject().put("enabled",true).put("color",7).put("effect","areas:breathe:1:96:1:1:160"))
            .put("triggerState",JSONObject().put("enabled",true).put("color",7).put("effect","dim:128:split:breathe:00FF00:0000FF"))
        return JSONObject().put("version",1)
            .put("notifications",JSONObject().put("enabled",true).put("apps",JSONObject().put("com.facebook.katana",app)))
            .put("editPositions",JSONObject().put("com.facebook.katana_landscape_x",0.25).put("com.facebook.katana_landscape_y",0.75))
    }
    @Test fun preservesSplitEffectsAndPositionsThroughJsonRoundTrip() {
        val parsed=BackupSupplement.parse(JSONObject(backup().toString()))
        assertEquals(true,parsed.enabled)
        val app=JSONObject(parsed.apps.getValue("com.facebook.katana"))
        assertEquals("areas:breathe:1:96:1:1:160",app.getJSONObject("logoState").getString("effect"))
        assertEquals("dim:128:split:breathe:00FF00:0000FF",app.getJSONObject("triggerState").getString("effect"))
        assertEquals(0.25f,parsed.positions.getValue("com.facebook.katana_landscape_x"),0f)
    }
    @Test fun acceptsCommonSettingsWithoutOverrides() {
        val json=backup()
        val app=json.getJSONObject("notifications").getJSONObject("apps").getJSONObject("com.facebook.katana")
        app.remove("logoState");app.remove("triggerState")
        assertFalse(JSONObject(BackupSupplement.parse(json).apps.getValue("com.facebook.katana")).has("logoState"))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsFutureVersion() { BackupSupplement.parse(backup().put("version",2)) }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidWindow() {
        val json=backup();json.getJSONObject("notifications").getJSONObject("apps").getJSONObject("com.facebook.katana").put("seconds",31)
        BackupSupplement.parse(json)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsIncompletePosition() {
        val json=backup();json.getJSONObject("editPositions").remove("com.facebook.katana_landscape_y")
        BackupSupplement.parse(json)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsOutOfBoundsPosition() {
        val json=backup();json.getJSONObject("editPositions").put("com.facebook.katana_landscape_x",1.5)
        BackupSupplement.parse(json)
    }
}
