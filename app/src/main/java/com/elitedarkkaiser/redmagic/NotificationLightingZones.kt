package com.elitedarkkaiser.redmagic

/** Translate persisted notification choices to the stock notification LED regions. */
internal object NotificationLightingZones {
    fun fromProfile(profile: NotificationLightingState.Profile): List<NotificationLedBatch.Zone> {
        val colors = setOf(1, 3, 4, 5, 6, 7, 8, 9)
        val effects = setOf("steady", "breathe", "flashing", "rapid")
        fun zone(name: String, enabled: Boolean, state: com.elitedarkkaiser.redmagic.state.LedState?): NotificationLedBatch.Zone? {
            if (!enabled) return null
            val color = state?.color?.takeIf { it in colors } ?: profile.color
            val effect = state?.effect?.takeIf { it in effects } ?: profile.effect
            return NotificationLedBatch.Zone(name, effect, color)
        }
        return listOfNotNull(zone("logo", profile.logo, profile.logoState), zone("triggers", profile.triggers, profile.triggerState), zone("fan", profile.fan, profile.fanState))
    }
}
