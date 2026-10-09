package com.elitedarkkaiser.redmagic

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log

/**
 * Non-root client for REDMAGIC's stock FpsDetectService.
 *
 * The service and callback wire format are mirrored from the
 * firmware AIDL used by Game Assist. No Game Space component is
 * started or required. On custom ROMs the service is simply absent.
 */
object VendorFpsMonitor {
    private const val TAG = "RedmagicFpsMonitor"
    private const val SERVICE_NAME = "FpsDetectService"
    private const val SERVICE_DESCRIPTOR =
        "com.zte.performance.fpsTicker.IFpsTickInterface"
    private const val CALLBACK_DESCRIPTOR =
        "com.zte.performance.fpsTicker.IFpsTickCallback"
    private const val REGISTER_TRANSACTION = 1
    private const val UNREGISTER_TRANSACTION = 2
    private const val CALLBACK_FPS_TRANSACTION = 1
    private const val CALLBACK_DESTROY_TRANSACTION = 2
    private const val RETRY_INTERVAL_MS = 30_000L
    private const val STALE_AFTER_MS = 3_500L

    data class Reading(
        val fps: Int,
        val packageName: String,
        val receivedAtMs: Long
    )

    private val lock = Any()

    @Volatile
    private var targetPackage: String? = null

    @Volatile
    private var latestReading: Reading? = null

    private var serviceBinder: IBinder? = null
    private var registered = false
    private var lastAttemptAtMs = 0L

    private val callbackBinder = object : Binder(), IInterface {
        init {
            attachInterface(this, CALLBACK_DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int
        ): Boolean {
            return when (code) {
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(CALLBACK_DESCRIPTOR)
                    true
                }

                CALLBACK_FPS_TRANSACTION -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val fps = data.readInt()
                    data.readFloat()
                    val packageName = data.readString().orEmpty()
                    data.readString()
                    acceptReading(fps, packageName)
                    true
                }

                CALLBACK_DESTROY_TRANSACTION -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    synchronized(lock) {
                        registered = false
                        serviceBinder = null
                    }
                    latestReading = null
                    reply?.writeNoException()
                    true
                }

                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        synchronized(lock) {
            registered = false
            serviceBinder = null
        }
        latestReading = null
    }

    fun start(packageName: String) {
        if (
            targetPackage == packageName &&
            synchronized(lock) { registered }
        ) {
            return
        }
        targetPackage = packageName
        latestReading = null
        ensureRegistered(force = true)
    }

    fun stop() {
        targetPackage = null
        latestReading = null

        synchronized(lock) {
            val binder = serviceBinder
            if (registered && binder != null && binder.isBinderAlive) {
                runCatching {
                    transactCallback(
                        binder,
                        UNREGISTER_TRANSACTION
                    )
                }
            }
            runCatching {
                binder?.unlinkToDeath(deathRecipient, 0)
            }
            registered = false
            serviceBinder = null
            lastAttemptAtMs = 0L
        }
    }

    fun currentFps(packageName: String): Int? {
        ensureRegistered(force = false)
        val reading = latestReading ?: return null
        val age = SystemClock.elapsedRealtime() - reading.receivedAtMs
        return reading.fps.takeIf {
            reading.packageName == packageName &&
                age in 0..STALE_AFTER_MS &&
                it in 0..1_000
        }
    }

    private fun acceptReading(fps: Int, packageName: String) {
        val target = targetPackage ?: return
        if (packageName != target || fps !in 0..1_000) {
            return
        }
        latestReading = Reading(
            fps = fps,
            packageName = packageName,
            receivedAtMs = SystemClock.elapsedRealtime()
        )
    }

    private fun ensureRegistered(force: Boolean) {
        if (targetPackage == null) return

        synchronized(lock) {
            if (
                registered &&
                serviceBinder?.isBinderAlive == true
            ) {
                return
            }

            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastAttemptAtMs < RETRY_INTERVAL_MS) {
                return
            }
            lastAttemptAtMs = now

            val binder = findService() ?: return
            try {
                transactCallback(binder, REGISTER_TRANSACTION)
                binder.linkToDeath(deathRecipient, 0)
                serviceBinder = binder
                registered = true
                Log.i(TAG, "Registered stock FPS callback")
            } catch (error: Throwable) {
                registered = false
                serviceBinder = null
                Log.w(
                    TAG,
                    "Stock FPS callback is unavailable",
                    error
                )
            }
        }
    }

    private fun findService(): IBinder? {
        /*
         * Probe the capability itself instead of the ROM fingerprint.
         * A custom ROM that preserves the vendor service can therefore
         * use live FPS without pretending to be stock firmware.
         */
        return runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, SERVICE_NAME) as? IBinder
        }.onFailure {
            Log.d(TAG, "FpsDetectService lookup failed", it)
        }.getOrNull()
    }

    private fun transactCallback(
        binder: IBinder,
        transaction: Int
    ) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            data.writeStrongBinder(callbackBinder)
            check(binder.transact(transaction, data, reply, 0)) {
                "FpsDetectService rejected transaction $transaction"
            }
            reply.readException()
        } catch (error: RemoteException) {
            throw IllegalStateException(
                "FpsDetectService transaction failed",
                error
            )
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
