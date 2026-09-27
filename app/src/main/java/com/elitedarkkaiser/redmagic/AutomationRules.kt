package com.elitedarkkaiser.redmagic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import org.json.JSONObject
import java.util.concurrent.Executors

enum class AutomationRuleEvent(
    val storageKey: String,
    val title: String,
    val broadcastAction: String? = null
) {
    POWER_CONNECTED(
        "power_connected",
        "Power connected",
        Intent.ACTION_POWER_CONNECTED
    ),
    POWER_DISCONNECTED(
        "power_disconnected",
        "Power disconnected",
        Intent.ACTION_POWER_DISCONNECTED
    ),
    BATTERY_LOW(
        "battery_low",
        "Battery low",
        Intent.ACTION_BATTERY_LOW
    ),
    BATTERY_OKAY(
        "battery_okay",
        "Battery recovered",
        Intent.ACTION_BATTERY_OKAY
    ),
    DEVICE_UNLOCKED(
        "device_unlocked",
        "First unlock after restart"
    );

    companion object {
        fun fromBroadcastAction(
            action: String?
        ): AutomationRuleEvent? {
            return entries.firstOrNull {
                it.broadcastAction == action
            }
        }
    }
}

object AutomationRulesStorage {
    private const val PREFS = "automation_rules"

    @Synchronized
    fun profileName(
        context: Context,
        event: AutomationRuleEvent
    ): String? {
        return context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getString(event.storageKey, null)
            ?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun saveProfileName(
        context: Context,
        event: AutomationRuleEvent,
        profileName: String?
    ) {
        val editor = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()

        if (profileName.isNullOrBlank()) {
            editor.remove(event.storageKey)
        } else {
            editor.putString(
                event.storageKey,
                profileName
            )
        }

        editor.apply()
    }

    @Synchronized
    fun clearProfileReferences(
        context: Context,
        profileName: String
    ) {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
        val editor = prefs.edit()
        var changed = false

        AutomationRuleEvent.entries.forEach { event ->
            if (
                prefs.getString(
                    event.storageKey,
                    null
                ) == profileName
            ) {
                editor.remove(event.storageKey)
                changed = true
            }
        }

        if (changed) {
            editor.apply()
        }
    }

    @Synchronized
    fun summary(context: Context): String {
        val enabled = AutomationRuleEvent.entries
            .count { profileName(context, it) != null }

        return when (enabled) {
            0 -> "No automatic profile rules configured"
            1 -> "1 automatic profile rule configured"
            else -> "$enabled automatic profile rules configured"
        }
    }

    @Synchronized
    fun toJson(context: Context): JSONObject {
        return JSONObject().apply {
            AutomationRuleEvent.entries.forEach { event ->
                profileName(context, event)?.let {
                    put(event.storageKey, it)
                }
            }
        }
    }

    @Synchronized
    fun restoreFromJson(
        context: Context,
        json: JSONObject
    ) {
        AutomationRuleEvent.entries.forEach { event ->
            saveProfileName(
                context,
                event,
                json.optString(
                    event.storageKey,
                    ""
                ).takeIf { it.isNotBlank() }
            )
        }
    }
}

object AutomationRuleExecutor {
    private const val TAG = "RedmagicAutomation"
    private const val DUPLICATE_EVENT_WINDOW_MS = 2_000L

    private val executor =
        Executors.newSingleThreadExecutor { task ->
            Thread(
                task,
                "RedMagicAutomation"
            ).apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    private val eventLock = Any()
    private var lastEvent: AutomationRuleEvent? = null
    private var lastEventAtMs = 0L

    fun dispatch(
        context: Context,
        event: AutomationRuleEvent,
        onFinished: () -> Unit = {}
    ) {
        val appContext = context.applicationContext

        executor.execute {
            try {
                applyNow(appContext, event)
            } finally {
                onFinished()
            }
        }
    }

    fun applyNow(
        context: Context,
        event: AutomationRuleEvent
    ): Boolean {
        val now = SystemClock.elapsedRealtime()

        synchronized(eventLock) {
            if (
                lastEvent == event &&
                now - lastEventAtMs <
                    DUPLICATE_EVENT_WINDOW_MS
            ) {
                return false
            }

            lastEvent = event
            lastEventAtMs = now
        }

        val profileName =
            AutomationRulesStorage.profileName(
                context,
                event
            ) ?: return false

        val profile = MasterProfileStorage
            .loadProfiles(context)
            .firstOrNull { it.name == profileName }

        if (profile == null) {
            AutomationRulesStorage.saveProfileName(
                context,
                event,
                null
            )
            android.util.Log.w(
                TAG,
                "Removed missing profile rule for $event"
            )
            return false
        }

        return runCatching {
            MasterProfileActions.applyProfile(
                context,
                profile
            )
            MasterProfileStorage.markProfileApplied(
                context,
                profile.name
            )
            android.util.Log.i(
                TAG,
                "Applied ${profile.name} for $event"
            )
            true
        }.getOrElse { error ->
            android.util.Log.e(
                TAG,
                "Failed ${profile.name} for $event",
                error
            )
            false
        }
    }
}

class AutomationRulesReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return
        }

        val event = AutomationRuleEvent
            .fromBroadcastAction(intent?.action)
            ?: return

        if (
            AutomationRulesStorage.profileName(
                context,
                event
            ) == null
        ) {
            return
        }

        val pendingResult = goAsync()
        AutomationRuleExecutor.dispatch(
            context,
            event
        ) {
            pendingResult.finish()
        }
    }
}
