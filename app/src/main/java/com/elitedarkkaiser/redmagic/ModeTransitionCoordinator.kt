package com.elitedarkkaiser.redmagic

import android.content.Context

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

    private var reconcilingAfterWrite = false

    private var lastOwner: LedOwner? = null
    private var lastSignature: String? = null

    internal fun <T> withLightingLock(block: () -> T): T = synchronized(transitionLock, block)

    fun applyLedProfile(
        context: Context,
        owner: LedOwner,
        signature: String,
        force: Boolean = false,
        block: () -> Unit
    ): Boolean {
        return synchronized(transitionLock) {
            LightingRootExecutor.initialize(context)
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
                if (effectiveOwner == LedOwner.NONE) shutdownLeds("rejected-stale-profile")
                return@synchronized false
            }

            // Screen state is checked inside the profile lock so a queued
            // normal/game/call write cannot relight hardware after shutdown.
            if (owner in setOf(LedOwner.NORMAL, LedOwner.GAME_MODE, LedOwner.RGB_CYCLE) &&
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

            if (owner != LedOwner.NOTIFICATION) NotificationWindowDeadline.supersede(context)
            val succeeded = runCatching { LedWriteReceipt.capture(
                stillEligible = { ownerCanApply(owner, LedOwnership.current(context)) }, block = block) }
                .onFailure { android.util.Log.e(TAG, "Profile write threw owner=$owner", it) }
                .getOrDefault(false)
            val ownerAfterWrite = LedOwnership.current(context)
            if (!succeeded || !ownerCanApply(owner, ownerAfterWrite)) {
                lastOwner = null
                lastSignature = null
                android.util.Log.w(TAG, "Handoff incomplete requested=$owner current=$ownerAfterWrite rootWritesSucceeded=$succeeded")
                // Never commit a stale or failed profile. Reconcile a changed owner
                // immediately; a failed unchanged owner gets a safe LED clear.
                if (!ownerCanApply(owner, ownerAfterWrite) && !reconcilingAfterWrite) {
                    shutdownLeds("owner-changed-during-write")
                    reconcilingAfterWrite = true
                    try { restoreEffectiveOwner(context, "owner-changed-during-write") }
                    finally { reconcilingAfterWrite = false }
                } else {
                    shutdownLeds("profile-write-failed")
                }
                return@synchronized false
            }

            lastOwner = owner
            lastSignature = signature
            if (owner != LedOwner.NOTIFICATION) NotificationWindowDeadline.acknowledgeHandoff(context)

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
            LightingRootExecutor.initialize(context)
            lastOwner = null
            lastSignature = null

            if (CallLightingState.isEnabled(context) && CallLightingState.isRingingNow(context)) {
                NotificationWindowDeadline.supersede(context)
                HardwareServiceActions.startCallLighting(context)
                return
            }

            if (
                ChargingLedState.isEnabled(context) &&
                ChargingLedState.isChargingNow(context)
            ) {
                NotificationWindowDeadline.supersede(context)
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

            ChargingLedState.setActive(context, false)
            CallLightingState.setActive(context, false)
            if (NotificationLightingState.isEligible(context)) return
            NotificationWindowDeadline.supersede(context)
            NotificationLightingState.expiresAt = 0L

            if (!LedScreenPolicy.isScreenInteractive(context)) {
                // Shutdown bypasses stale ownership, including RGB Studio.
                if (shutdownLeds(reason)) NotificationWindowDeadline.acknowledgeHandoff(context)
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

    private fun shutdownLeds(reason: String): Boolean {
        // Retry once, still under the transition lock, with the same proven
        // shutdown commands. Cooling nodes are never part of this operation.
        var succeeded = HardwareController.turnOffAllLeds()
        if (!succeeded) succeeded = HardwareController.turnOffAllLeds()
        if (succeeded) android.util.Log.i(TAG, "LED shutdown acknowledged reason=$reason")
        else android.util.Log.e(TAG, "LED shutdown failed reason=$reason")
        return succeeded
    }

    private fun ownerCanApply(requested: LedOwner, effective: LedOwner): Boolean =
        LightingPriorityPolicy.canApply(requested, effective)
}
