package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.util.concurrent.Executor
import com.elitedarkkaiser.redmagic.state.LedState

@Suppress("DEPRECATION")
class CallLightingService : Service() {

    private var telephonyManager: TelephonyManager? = null
    private var modernPhoneCallback:
        TelephonyCallback? = null

    @Volatile
    private var lastState: Int = TelephonyManager.CALL_STATE_IDLE

    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler

    private val callStateLock = Any()
    private var pendingCallState =
        TelephonyManager.CALL_STATE_IDLE
    private var forcePendingCallState = false

    private val callStateRunnable = Runnable {
        val pending = synchronized(callStateLock) {
            val result =
                pendingCallState to
                    forcePendingCallState
            forcePendingCallState = false
            result
        }

        handleCallState(
            pending.first,
            pending.second
        )
    }

    private val fanPauseRunnable = Runnable {
        if (
            CallLightingState.isActive(this) &&
            CallLightingState.shouldPauseFanDuringCalls(this)
        ) {
            HardwareServiceActions.stopAutoFan(this)
            HardwareController.setFanLevel(0)
            HardwareController.enableFan(false)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            handler.post {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    ModeTransitionCoordinator.restoreEffectiveOwner(
                        this@CallLightingService, "call-screen-off")
                } else {
                    scheduleCallState(currentCallState(), force = true)
                }
            }
        }
    }

    private val phoneListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            scheduleCallState(state)
        }
    }

    override fun onCreate() {
        super.onCreate()

        workerThread = HandlerThread(
            "RedMagicCallLighting",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()
        }
        handler = Handler(workerThread.looper)

        telephonyManager =
            getSystemService(Context.TELEPHONY_SERVICE)
                as? TelephonyManager
        registerCallStateListener()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        scheduleCallState(
            currentCallState(),
            force = true
        )
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterCallStateListener()
        runCatching { unregisterReceiver(screenReceiver) }

        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }
        if (::workerThread.isInitialized) {
            workerThread.quitSafely()
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    private fun registerCallStateListener() {
        val manager =
            telephonyManager ?: return

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            val callback =
                object :
                    TelephonyCallback(),
                    TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(
                        state: Int
                    ) {
                        scheduleCallState(state)
                    }
                }

            modernPhoneCallback = callback

            manager.registerTelephonyCallback(
                Executor { command ->
                    if (::handler.isInitialized) {
                        handler.post(command)
                    }
                },
                callback
            )
        } else {
            manager.listen(
                phoneListener,
                PhoneStateListener.LISTEN_CALL_STATE
            )
        }
    }

    private fun unregisterCallStateListener() {
        val manager =
            telephonyManager ?: return

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            modernPhoneCallback?.let {
                callback ->
                runCatching {
                    manager
                        .unregisterTelephonyCallback(
                            callback
                        )
                }
            }
            modernPhoneCallback = null
        } else {
            manager.listen(
                phoneListener,
                PhoneStateListener.LISTEN_NONE
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun currentCallState(): Int {
        return runCatching {
            telephonyManager?.callState
        }.getOrNull()
            ?: lastState
    }

    private fun scheduleCallState(
        state: Int,
        force: Boolean = false
    ) {
        if (!::handler.isInitialized) return

        synchronized(callStateLock) {
            pendingCallState = state
            forcePendingCallState =
                forcePendingCallState || force
        }

        handler.removeCallbacks(
            callStateRunnable
        )
        handler.post(callStateRunnable)
    }

    private fun handleCallState(
        state: Int,
        force: Boolean
    ) {
        if (!force && state == lastState) {
            return
        }

        lastState = state

        if (!CallLightingState.isEnabled(this)) {
            if (CallLightingState.isActive(this)) {
                CallLightingState.setActive(
                    this,
                    false
                )
                restorePausedFanIfNeeded()
                restorePreviousLedOwner()
            }
            return
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                beginCallOwnership()

                if (LedOwnership.canCallApply(this)) {
                    applyIncomingProfile()
                }

                enforceFanPauseIfNeeded()
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                beginCallOwnership()

                if (LedOwnership.canCallApply(this)) {
                    applyConnectedProfile()
                }

                enforceFanPauseIfNeeded()
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                handler.removeCallbacks(
                    fanPauseRunnable
                )

                if (CallLightingState.isActive(this)) {
                    CallLightingState.setActive(
                        this,
                        false
                    )
                    restorePausedFanIfNeeded()
                    restorePreviousLedOwner()
                }
            }
        }
    }


    private fun beginCallOwnership() {
        val wasAlreadyActive =
            CallLightingState.isActive(this)

        if (
            !wasAlreadyActive &&
            CallLightingState
                .shouldPauseFanDuringCalls(this)
        ) {
            CallLightingState.savePreCallFanState(
                context = this,
                enabled =
                    HardwareController
                        .isFanEnabled(),
                level =
                    HardwareController
                        .readFanLevel() ?: 0
            )

            CallLightingState.setFanPausedForCall(
                this,
                true
            )

            HardwareServiceActions
                .stopAutoFan(this)
            HardwareController.setFanLevel(0)
            HardwareController.enableFan(false)
        }

        CallLightingState.setActive(
            this,
            true
        )
    }

    private fun restorePausedFanIfNeeded() {
        if (
            !CallLightingState
                .wasFanPausedForCall(this)
        ) {
            return
        }

        CallLightingState
            .restorePreCallFanState(this)
        CallLightingState.setFanPausedForCall(
            this,
            false
        )

        if (isAutoFanEnabledStorage(this)) {
            HardwareServiceActions
                .startAutoFan(this)
        }
    }

    private fun enforceFanPauseIfNeeded() {
        if (CallLightingState.shouldPauseFanDuringCalls(this)) {
            handler.removeCallbacks(fanPauseRunnable)

            HardwareServiceActions.stopAutoFan(this)
            HardwareController.setFanLevel(0)
            HardwareController.enableFan(false)

            handler.postDelayed(fanPauseRunnable, 750L)
        }
    }

    private fun applyIncomingProfile() {
        applyProfile(
            fan = CallLightingState.readLed(
                this,
                CallLightingState.INCOMING_FAN_ENABLED_KEY,
                CallLightingState.INCOMING_FAN_EFFECT_KEY,
                CallLightingState.INCOMING_FAN_COLOR_KEY,
                true,
                "flashing",
                5
            ),
            logo = CallLightingState.readLed(
                this,
                CallLightingState.INCOMING_LOGO_ENABLED_KEY,
                CallLightingState.INCOMING_LOGO_EFFECT_KEY,
                CallLightingState.INCOMING_LOGO_COLOR_KEY,
                true,
                "flashing",
                1
            ),
            shoulder = CallLightingState.readLed(
                this,
                CallLightingState.INCOMING_SHOULDER_ENABLED_KEY,
                CallLightingState.INCOMING_SHOULDER_EFFECT_KEY,
                CallLightingState.INCOMING_SHOULDER_COLOR_KEY,
                true,
                "flashing",
                8
            )
        )
    }

    private fun applyConnectedProfile() {
        applyProfile(
            fan = CallLightingState.readLed(
                this,
                CallLightingState.CONNECTED_FAN_ENABLED_KEY,
                CallLightingState.CONNECTED_FAN_EFFECT_KEY,
                CallLightingState.CONNECTED_FAN_COLOR_KEY,
                true,
                "steady",
                5
            ),
            logo = CallLightingState.readLed(
                this,
                CallLightingState.CONNECTED_LOGO_ENABLED_KEY,
                CallLightingState.CONNECTED_LOGO_EFFECT_KEY,
                CallLightingState.CONNECTED_LOGO_COLOR_KEY,
                true,
                "steady",
                1
            ),
            shoulder = CallLightingState.readLed(
                this,
                CallLightingState.CONNECTED_SHOULDER_ENABLED_KEY,
                CallLightingState.CONNECTED_SHOULDER_EFFECT_KEY,
                CallLightingState.CONNECTED_SHOULDER_COLOR_KEY,
                true,
                "steady",
                8
            )
        )
    }

    private fun applyProfile(
        fan: LedState,
        logo: LedState,
        shoulder: LedState
    ) {
        val signature = listOf(
            lastState,
            fan.enabled,
            fan.effect,
            fan.color,
            logo.enabled,
            logo.effect,
            logo.color,
            shoulder.enabled,
            shoulder.effect,
            shoulder.color
        ).joinToString("|")

        ModeTransitionCoordinator.applyLedProfile(
            context = this,
            owner = LedOwner.CALL,
            signature = signature
        ) {
            if (fan.enabled) {
                if (
                    fan.effect.startsWith(
                        "preset:"
                    )
                ) {
                    HardwareController
                        .setFanLedStockPreset(
                            fan.effect.removePrefix(
                                "preset:"
                            )
                        )
                } else {
                    HardwareController
                        .setFanLedEffect(
                            fan.effect,
                            fan.color
                        )
                }
            } else {
                HardwareController
                    .setFanLedEnabled(false)
            }

            if (logo.enabled) {
                HardwareController
                    .setLogoLedEffect(
                        logo.effect,
                        logo.color
                    )
            } else {
                HardwareController
                    .setLogoLedEnabled(false)
            }

            if (shoulder.enabled) {
                HardwareController
                    .setShoulderLedEffect(
                        shoulder.effect,
                        shoulder.color
                    )
            } else {
                HardwareController
                    .setShoulderLedEnabled(false)
            }
        }
    }

    private fun restorePreviousLedOwner() {
        ModeTransitionCoordinator
            .restoreEffectiveOwner(
                this,
                "call-ended"
            )
    }
}
