package com.elitedarkkaiser.redmagic

import android.app.KeyguardManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TriggerRootService : Service() {

    private var rightCrossUnlockedUntil: Long = 0L

    @Volatile
    private var running = true

    private var leftUnlockArmedAt = 0L
    private var leftUnlockedUntil = 0L
    private var leftUnlockTapCount = 0
    private val held = ConcurrentHashMap<String, AtomicBoolean>()
    private val repeatThreads = ConcurrentHashMap<String, Thread>()
    private val readerThreads = ConcurrentHashMap<String, Thread>()
    private val readerProcesses = ConcurrentHashMap<String, Process>()
    private val lastDownAt = ConcurrentHashMap<String, Long>()
    private val lastActionAt = ConcurrentHashMap<String, Long>()

    private var initializationThread: Thread? = null
    private var rightUnlockArmedAt = 0L
    private var rightUnlockTapCount = 0
    private var rightUnlockedUntil = 0L

    private val INTENT_UNLOCK_TAP_WINDOW_MS = 450L
    private val INPUT_DEBOUNCE_MS = 80L
    private val ACTION_COOLDOWN_MS = 140L
    private val HOLD_REPEAT_START_MS = 350L
    private val HOLD_REPEAT_INTERVAL_MS = 110L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (!DeviceCompatibility.isSupportedDevice()) {
            stopSelf()
            return
        }

        if (triggersDisabledUntilRestartStorage(this)) {
            stopSelf()
            return
        }

        android.util.Log.d("TRIGGER", "TriggerRootService onCreate")

        initializationThread = Thread({
            HardwareController.enableTriggers()

            val leftDevice =
                findTriggerEvent("nubia_tgk_aw_sar0_ch0")
            if (running && leftDevice != null) {
                startReader(leftDevice, "left_trigger")
            }

            val rightDevice =
                findTriggerEvent("nubia_tgk_aw_sar1_ch0")
            if (running && rightDevice != null) {
                startReader(rightDevice, "right_trigger")
            }
        }, "RedMagicTriggerInit").apply {
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }


    private fun findTriggerEvent(triggerName: String): String? {
        val command =
            "for ev in /sys/class/input/event*; do " +
                "name=\$(cat \"\$ev/device/name\" 2>/dev/null); " +
                "if [ \"\$name\" = \"$triggerName\" ]; then " +
                "basename \"\$ev\"; exit 0; fi; done"

        val eventName = RootShell.execForOutput(command)
            ?.trim()
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()

        return if (
            eventName != null &&
            eventName.matches(Regex("event\\d+"))
        ) {
            "/dev/input/$eventName"
        } else {
            android.util.Log.e(
                "TRIGGER",
                "failed to resolve trigger event for $triggerName"
            )
            null
        }
    }

    private fun prefs() = getSharedPreferences("triggers", MODE_PRIVATE)

    private fun isScreenInteractive(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        return powerManager.isInteractive
    }

    private fun isDeviceLocked(): Boolean {
        val manager = getSystemService(
            KeyguardManager::class.java
        ) ?: return false
        return manager.isKeyguardLocked
    }

    private fun safetyConfig(): TriggerSafetyConfig {
        return readTriggerSafetyConfig(this)
    }

    private fun inputAllowed(
        prefKey: String,
        config: TriggerSafetyConfig
    ): Boolean {
        if (
            NativeTgkRuntimeState.isActive() ||
            NativeTgkEditorRuntime.isEditing()
        ) {
            android.util.Log.d(
                "TRIGGER",
                "$prefKey ignored while native TGK or its editor is active"
            )
            return false
        }

        if (!isScreenInteractive()) {
            android.util.Log.d(
                "TRIGGER",
                "$prefKey ignored because screen is off"
            )
            return false
        }

        if (config.blockOnLockScreen && isDeviceLocked()) {
            android.util.Log.d(
                "TRIGGER",
                "$prefKey ignored because device is locked"
            )
            return false
        }

        if (
            config.gameModeOnly &&
            !isGameModeLedOverrideActiveStorage(this)
        ) {
            android.util.Log.d(
                "TRIGGER",
                "$prefKey ignored because Game Mode is inactive"
            )
            return false
        }

        val current = now()
        val previous = lastDownAt[prefKey] ?: 0L
        if (current - previous < INPUT_DEBOUNCE_MS) {
            android.util.Log.d(
                "TRIGGER",
                "$prefKey ignored by input debounce"
            )
            return false
        }

        lastDownAt[prefKey] = current
        return true
    }


    private fun getAction(key: String): String {
        val value = prefs().getString(key, "NONE") ?: "NONE"
        android.util.Log.d("TRIGGER", "getAction(" + key + ")=" + value)
        return value
    }

    private fun runRoot(command: String) {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return
        }

        android.util.Log.d("TRIGGER", "runRoot=$command")

        val session: RootShell.Session? = null
        val succeeded =
            session?.exec(command) ?: RootShell.exec(command)

        if (!succeeded) {
            android.util.Log.e(
                "TRIGGER",
                "root action failed"
            )
        }
    }

    private fun performAction(action: String) {
        android.util.Log.d("TRIGGER", "performAction=" + action)

        when (action) {
            "VOL_UP" -> runRoot("input keyevent 24")
            "VOL_DOWN" -> runRoot("input keyevent 25")
            "MEDIA_PLAY_PAUSE" -> runRoot("input keyevent 85")
            "MEDIA_NEXT" -> runRoot("input keyevent 87")
            "MEDIA_PREVIOUS" -> runRoot("input keyevent 88")
            "NONE" -> Unit
            else -> Unit
        }
    }

    private fun performInitialAction(action: String) {
        if (action != "NONE") {
            HapticFeedback.pulse(
                this,
                HapticFeedback.Event.TRIGGER
            )
        }
        performAction(action)
    }

    private fun performInitialAction(
        prefKey: String,
        action: String
    ): Boolean {
        val current = now()
        val previous = lastActionAt[prefKey] ?: 0L
        if (current - previous < ACTION_COOLDOWN_MS) {
            android.util.Log.d(
                "TRIGGER",
                "$prefKey action ignored by cooldown"
            )
            return false
        }

        lastActionAt[prefKey] = current
        performInitialAction(action)
        return true
    }

    private fun isRepeatable(action: String): Boolean {
        return when (action) {
            "VOL_UP", "VOL_DOWN" -> true
            else -> false
        }
    }

    private fun startRepeater(
        prefKey: String,
        config: TriggerSafetyConfig
    ) {
        stopRepeater(prefKey)

        val action = getAction(prefKey)
        if (!isRepeatable(action)) return

        val flag = AtomicBoolean(true)
        held[prefKey] = flag

        val thread = Thread {
            try {
                Thread.sleep(HOLD_REPEAT_START_MS)

                while (running && flag.get()) {
                    if (
                        prefKey == "right_trigger" &&
                        config.usesIntentUnlock() &&
                        !isRightUnlocked(config)
                    ) {
                        break
                    }
                    performAction(getAction(prefKey))
                    if (config.leftUnlocksRight && config.usesIntentUnlock()) {
                        if (prefKey == "right_trigger") unlockLeftFromRight(config)
                        else if (prefKey == "left_trigger") unlockRightFromLeft(config)
                    }
                    if (
                        prefKey == "right_trigger" &&
                        config.usesIntentUnlock()
                    ) {
                        extendRightUnlock(config)
                    }
                    Thread.sleep(HOLD_REPEAT_INTERVAL_MS)
                }
            } catch (_: InterruptedException) {
            } catch (t: Throwable) {
                android.util.Log.e("TRIGGER", "startRepeater failed for " + prefKey + ": " + t)
            }
        }

        repeatThreads[prefKey] = thread
        thread.start()
    }

    private fun startHeldAction(
        prefKey: String,
        config: TriggerSafetyConfig
    ) {
        stopRepeater(prefKey)

        val flag = AtomicBoolean(true)
        held[prefKey] = flag

        val thread = Thread {
            try {
                Thread.sleep(
                    config.holdDurationMs.toLong()
                )

                if (!running || !flag.get()) {
                    return@Thread
                }

                if (
                    prefKey == "right_trigger" &&
                    config.usesIntentUnlock() &&
                    !isRightUnlocked(config)
                ) {
                    return@Thread
                }

                val action = getAction(prefKey)
                if (!performInitialAction(prefKey, action)) {
                    return@Thread
                }

                if (
                    prefKey == "left_trigger" &&
                    config.leftUnlocksRight
                ) {
                    unlockRightFromLeft(config)
                }

                if (prefKey == "right_trigger" && config.leftUnlocksRight && config.usesIntentUnlock()) {
                    unlockLeftFromRight(config)
                }

                if (!isRepeatable(action)) {
                    return@Thread
                }

                val repeatDelay =
                    (HOLD_REPEAT_START_MS -
                        config.holdDurationMs)
                        .coerceAtLeast(0)
                        .toLong()

                if (repeatDelay > 0L) {
                    Thread.sleep(repeatDelay)
                }

                while (running && flag.get()) {
                    if (
                        prefKey == "right_trigger" &&
                        config.usesIntentUnlock() &&
                        !isRightUnlocked(config)
                    ) {
                        break
                    }

                    performAction(getAction(prefKey))
                    if (config.leftUnlocksRight && config.usesIntentUnlock()) {
                        if (prefKey == "right_trigger") unlockLeftFromRight(config)
                        else if (prefKey == "left_trigger") unlockRightFromLeft(config)
                    }
                    if (
                        prefKey == "right_trigger" &&
                        config.usesIntentUnlock()
                    ) {
                        extendRightUnlock(config)
                    }
                    Thread.sleep(HOLD_REPEAT_INTERVAL_MS)
                }
            } catch (_: InterruptedException) {
            } catch (error: Throwable) {
                android.util.Log.e(
                    "TRIGGER",
                    "held activation failed for $prefKey: $error"
                )
            }
        }

        repeatThreads[prefKey] = thread
        thread.start()
    }

    private fun stopRepeater(prefKey: String) {
        held[prefKey]?.set(false)
        held.remove(prefKey)

        repeatThreads[prefKey]?.interrupt()
        repeatThreads.remove(prefKey)
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun isRightUnlocked(
        config: TriggerSafetyConfig = safetyConfig()
    ): Boolean {
        val unlocked = now() <= rightUnlockedUntil
        if (!unlocked && rightUnlockedUntil != 0L) {
            android.util.Log.d("TRIGGER", "right trigger locked by timeout")
        }
        return unlocked
    }

    private fun extendRightUnlock(
        config: TriggerSafetyConfig = safetyConfig()
    ) {
        rightUnlockedUntil =
            now() + config.unlockTimeoutMs
        android.util.Log.d("TRIGGER", "right unlock extended until=" + rightUnlockedUntil)
    }

    private fun unlockRightFromLeft(
        config: TriggerSafetyConfig
    ) {
        val expiresAt = now() + config.unlockTimeoutMs
        rightCrossUnlockedUntil = expiresAt
        rightUnlockedUntil = expiresAt
        rightUnlockArmedAt = 0L
        rightUnlockTapCount = 0
        android.util.Log.d(
            "TRIGGER",
            "left trigger unlocked right until=$expiresAt"
        )
    }

    private fun unlockLeftFromRight(config: TriggerSafetyConfig) {
        val expiresAt = now() + config.unlockTimeoutMs
        leftUnlockedUntil = expiresAt
        leftUnlockArmedAt = 0L
        leftUnlockTapCount = 0
        android.util.Log.d("TRIGGER", "right trigger unlocked left until=$expiresAt")
    }

    private fun handleRightIntentUnlock(
        config: TriggerSafetyConfig
    ): Boolean {
        if (!config.usesIntentUnlock()) {
            return true
        }

        val current = now()
        val requiredTaps = config.rightUnlockTapCount

        if (current <= rightUnlockedUntil) {
            extendRightUnlock(config)
            return true
        }

        if (
            rightUnlockArmedAt == 0L ||
            current - rightUnlockArmedAt >
                INTENT_UNLOCK_TAP_WINDOW_MS
        ) {
            rightUnlockArmedAt = current
            rightUnlockTapCount = 1
        } else {
            rightUnlockTapCount += 1
        }

        if (rightUnlockTapCount >= requiredTaps) {
            rightUnlockArmedAt = 0L
            rightUnlockTapCount = 0
            rightUnlockedUntil =
                current + config.unlockTimeoutMs
            android.util.Log.d("TRIGGER", "right trigger UNLOCKED taps=$requiredTaps")
            return true
        }

        android.util.Log.d(
            "TRIGGER",
            "right trigger unlock tap $rightUnlockTapCount/$requiredTaps ignored"
        )
        return false
    }


    private fun handleLeftIntentUnlock(
        config: TriggerSafetyConfig
    ): Boolean {
        if (!config.usesIntentUnlock()) {
            return true
        }

        val requiredTaps = config.leftUnlockTapCount
        if (requiredTaps <= 1) {
            return true
        }

        val current = now()

        if (current <= leftUnlockedUntil) {
            leftUnlockedUntil =
                current + config.unlockTimeoutMs
            return true
        }

        if (
            leftUnlockArmedAt == 0L ||
            current - leftUnlockArmedAt >
                INTENT_UNLOCK_TAP_WINDOW_MS
        ) {
            leftUnlockArmedAt = current
            leftUnlockTapCount = 1
        } else {
            leftUnlockTapCount += 1
        }

        if (leftUnlockTapCount >= requiredTaps) {
            leftUnlockArmedAt = 0L
            leftUnlockTapCount = 0
            leftUnlockedUntil =
                current + config.unlockTimeoutMs
            android.util.Log.d("TRIGGER", "left trigger UNLOCKED taps=$requiredTaps")
            return true
        }

        android.util.Log.d(
            "TRIGGER",
            "left trigger unlock tap $leftUnlockTapCount/$requiredTaps ignored"
        )
        return false
    }

    private fun handleLeftDown(device: String, line: String) {
        android.util.Log.d("TRIGGER", "LEFT DOWN device=" + device + " line=" + line)

        val config = safetyConfig()
        if (!inputAllowed("left_trigger", config)) {
            return
        }

        val alreadyUnlocked = config.usesIntentUnlock() && now() <= leftUnlockedUntil
        if (!handleLeftIntentUnlock(config)) {
            stopRepeater("left_trigger")
            return
        }

        if (config.usesHold() && !alreadyUnlocked) {
            startHeldAction("left_trigger", config)
        } else {
            val accepted = performInitialAction(
                "left_trigger",
                getAction("left_trigger")
            )
            if (accepted) {
                if (config.leftUnlocksRight) {
                    unlockRightFromLeft(config)
                }
                startRepeater("left_trigger", config)
            }
        }
    }

    private fun handleRightDown(device: String, line: String) {
        android.util.Log.d("TRIGGER", "RIGHT DOWN device=" + device + " line=" + line)

        val config = safetyConfig()
        if (!inputAllowed("right_trigger", config)) {
            return
        }

        val crossUnlocked =
            config.leftUnlocksRight &&
                now() <= rightCrossUnlockedUntil
        val alreadyUnlocked = config.usesIntentUnlock() &&
            (crossUnlocked || now() <= rightUnlockedUntil)

        if (
            !crossUnlocked &&
            !handleRightIntentUnlock(config)
        ) {
            stopRepeater("right_trigger")
            return
        }

        if (crossUnlocked && config.usesIntentUnlock()) {
            extendRightUnlock(config)
        }

        if (config.usesHold() && !alreadyUnlocked) {
            startHeldAction("right_trigger", config)
        } else {
            val accepted = performInitialAction(
                "right_trigger",
                getAction("right_trigger")
            )
            if (accepted) {
                if (config.leftUnlocksRight && config.usesIntentUnlock()) {
                    unlockLeftFromRight(config)
                }
                startRepeater("right_trigger", config)
            }
        }
    }

    private fun handleUp(prefKey: String, device: String, line: String) {
        android.util.Log.d("TRIGGER", "UP device=" + device + " key=" + prefKey + " line=" + line)
        stopRepeater(prefKey)
    }


    private fun isDownLine(line: String): Boolean {
        return line.contains(" DOWN") ||
            line.endsWith(" 00000001") ||
            line.contains(" value 1")
    }

    private fun isUpLine(line: String): Boolean {
        return line.contains(" UP") ||
            line.endsWith(" 00000000") ||
            line.contains(" value 0")
    }


    @Synchronized
    private fun startReader(device: String, prefKey: String) {
        val thread = Thread({
            var process: Process? = null

            try {
                if (!running) return@Thread

                android.util.Log.d(
                    "TRIGGER",
                    "startReader device=$device key=$prefKey"
                )

                val startedProcess = ProcessBuilder(
                    "su",
                    "-c",
                    "exec getevent -l '$device'"
                )
                    .redirectErrorStream(true)
                    .start()

                process = startedProcess
                readerProcesses[prefKey] = startedProcess

                startedProcess.inputStream.bufferedReader().use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break

                        android.util.Log.d(
                            "TRIGGER",
                            "raw device=$device key=$prefKey line=$line"
                        )

                        if (isDownLine(line)) {
                            if (prefKey == "left_trigger") {
                                handleLeftDown(device, line)
                            } else {
                                handleRightDown(device, line)
                            }
                        } else if (isUpLine(line)) {
                            handleUp(prefKey, device, line)
                        }
                    }
                }
            } catch (error: Throwable) {
                if (running) {
                    android.util.Log.e(
                        "TRIGGER",
                        "startReader failed for $device: $error"
                    )
                }
            } finally {
                process?.let { activeProcess ->
                    runCatching {
                        activeProcess.destroy()
                    }
                    if (activeProcess.isAlive) {
                        runCatching {
                            activeProcess.destroyForcibly()
                        }
                    }
                }

                readerProcesses.remove(prefKey)
                readerThreads.remove(prefKey)
            }
        }, "RedMagicTriggerReader-$prefKey").apply {
            priority = Thread.NORM_PRIORITY - 1
        }

        readerThreads[prefKey] = thread
        thread.start()
    }

    override fun onDestroy() {
        running = false

        initializationThread?.interrupt()
        initializationThread = null

        stopRepeater("left_trigger")
        stopRepeater("right_trigger")

        readerProcesses.values.forEach { process ->
            runCatching {
                process.destroy()
            }
            if (process.isAlive) {
                runCatching {
                    process.destroyForcibly()
                }
            }
        }

        readerThreads.values.forEach { thread ->
            thread.interrupt()
        }

        readerProcesses.clear()
        readerThreads.clear()
        lastDownAt.clear()
        lastActionAt.clear()

        android.util.Log.d(
            "TRIGGER",
            "TriggerRootService onDestroy"
        )
        super.onDestroy()
    }
}
