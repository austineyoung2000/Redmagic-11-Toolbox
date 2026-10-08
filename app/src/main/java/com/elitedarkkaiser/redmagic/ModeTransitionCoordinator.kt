package com.elitedarkkaiser.redmagic

import android.content.Context
import android.telephony.TelephonyManager

/*
 * Serializes complete LED-profile transitions across every
 * feature that can own the same physical LEDs.
 *
 * RootShell already serializes individual commands. This
 * coordinator operates one level higher so complete profiles
 * cannot interleave with another mode's profile.
 */
object ModeTransitionCoordinator {
    private const val TAG = "RedmagicModeCoordinator"

    private val transitionLock = Any()

    private var lastOwner: LedOwner? = null
    private var lastSignature: String? = null

    fun applyLedProfile(
        context: Context,
        owner: LedOwner,
        signature: String,
        force: Boolean = false,
        block: () -> Unit
    ): Boolean {
        return synchronized(transitionLock) {
            val effectiveOwner =
                LedOwnership.current(context)

            if (
                !ownerCanApply(
                    owner,
                    effectiveOwner
                )
            ) {
                android.util.Log.i(
                    TAG,
                    "Skipped $owner profile because " +
                        "effective owner is $effectiveOwner"
                )
                return@synchronized false
            }

            // Screen state is checked inside the profile lock so a queued
            // normal/game/call write cannot relight hardware after shutdown.
            if (owner in setOf(LedOwner.NORMAL, LedOwner.GAME_MODE, LedOwner.CALL) &&
                !LedScreenPolicy.isScreenInteractive(context)) {
                HardwareController.turnOffAllLeds()
                lastOwner = null
                lastSignature = null
                return@synchronized false
            }

            if (
                !force &&
                lastOwner == owner &&
                lastSignature == signature
            ) {
                android.util.Log.d(
                    TAG,
                    "Skipped unchanged $owner profile"
                )
                return@synchronized false
            }

            block()

            lastOwner = owner
            lastSignature = signature

            android.util.Log.i(
                TAG,
                "Applied $owner profile: $signature"
            )
            true
        }
    }

    /*
     * Selects the highest-priority state that is valid at the
     * exact moment a higher-priority owner exits.
     */
    fun restoreEffectiveOwner(
        context: Context,
        reason: String
    ) {
        synchronized(transitionLock) {
            lastOwner = null
            lastSignature = null

            if (
                ChargingLedState.isEnabled(context) &&
                ChargingLedState.isChargingNow(context)
            ) {
                ChargingLedState.setActive(
                    context,
                    true
                )
                ChargingLedState.applyChargingProfile(
                    context,
                    force = true
                )
                return
            }

            ChargingLedState.setActive(
                context,
                false
            )

            if (!LedScreenPolicy.isScreenInteractive(context)) {
                if (RgbStudioStorage.isEnabled(context) &&
                    LedOwnership.current(context) == LedOwner.RGB_CYCLE) {
                    // RGB Studio owns its configured screen-off timeout.
                    HardwareServiceActions.startRgbCycle(context)
                } else {
                    // Do not gate shutdown on stale call/game ownership.
                    HardwareController.turnOffAllLeds()
                }
                return
            }

            if (
                CallLightingState.isEnabled(context) &&
                (
                    CallLightingState.isActive(context) ||
                    isCallInProgress(context)
                )
            ) {
                HardwareServiceActions
                    .startCallLighting(context)
                return
            }

            if (
                isGameModeLedOverrideActiveStorage(
                    context
                )
            ) {
                GameModeActions
                    .startServiceSilentlyIfPermitted(
                        context
                    )
                return
            }

            if (RgbStudioStorage.isEnabled(context)) {
                HardwareServiceActions
                    .startRgbCycle(context)

                /*
                 * Probe Game Mode too. If a selected game is
                 * actually foreground, its higher-priority
                 * ownership will pause RGB Studio.
                 */
                GameModeActions
                    .startServiceSilentlyIfPermitted(
                        context
                    )
                return
            }

            /*
             * Game Mode performs an immediate foreground probe.
             * FanLedService applies normal state if no tracked
             * game claims ownership.
             */
            GameModeActions
                .startServiceSilentlyIfPermitted(
                    context
                )
            HardwareServiceActions.startFanLed(context)
        }
    }

    fun invalidate() {
        synchronized(transitionLock) {
            lastOwner = null
            lastSignature = null
        }
    }

    @Suppress("DEPRECATION")
    private fun isCallInProgress(
        context: Context
    ): Boolean {
        val manager =
            context.getSystemService(
                TelephonyManager::class.java
            ) ?: return false

        val state = runCatching {
            manager.callState
        }.getOrDefault(
            TelephonyManager.CALL_STATE_IDLE
        )

        return state !=
            TelephonyManager.CALL_STATE_IDLE
    }

    private fun ownerCanApply(
        requested: LedOwner,
        effective: LedOwner
    ): Boolean {
        return when (requested) {
            LedOwner.CHARGING ->
                effective == LedOwner.CHARGING

            LedOwner.CALL ->
                effective == LedOwner.CALL

            LedOwner.GAME_MODE ->
                effective == LedOwner.GAME_MODE

            LedOwner.RGB_CYCLE ->
                effective == LedOwner.RGB_CYCLE

            LedOwner.NORMAL ->
                effective == LedOwner.NORMAL

            LedOwner.NONE ->
                effective == LedOwner.NORMAL ||
                    effective == LedOwner.RGB_CYCLE

            else -> false
        }
    }
}
