package com.elitedarkkaiser.redmagic

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MasterProfileBackupTest {
    @Test fun schema12RoundTripRetainsSupplementalSettings() {
        val supplement=JSONObject().put("version",1)
            .put("notifications",JSONObject().put("enabled",true).put("apps",JSONObject()))
            .put("editPositions",JSONObject().put("com.test.game_portrait_x",0.2).put("com.test.game_portrait_y",0.8))
        val raw=JSONObject().put("schemaVersion",12).put("name","Test").put("hardware",JSONObject())
            .put("supplementalSettings",supplement).toString()
        val decoded=MasterProfileStorage.decodeProfile(raw)
        val reopened=MasterProfileStorage.decodeProfile(MasterProfileStorage.encodeProfile(decoded))
        assertEquals(12,reopened.schemaVersion)
        val data=BackupSupplement.parse(JSONObject(reopened.supplementalSettingsJson!!))
        assertEquals(true,data.enabled)
        assertEquals(0.2f,data.positions.getValue("com.test.game_portrait_x"),0f)
    }
    @Test fun olderProfileOmitsSupplementInsteadOfClearingNewSettings() {
        val raw=JSONObject().put("schemaVersion",11).put("hardware",JSONObject()).toString()
        assertNull(MasterProfileStorage.decodeProfile(raw).supplementalSettingsJson)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsFutureProfileVersion() {
        MasterProfileStorage.decodeProfile(JSONObject().put("schemaVersion",13).put("hardware",JSONObject()).toString())
    }
}
