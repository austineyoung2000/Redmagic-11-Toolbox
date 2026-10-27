package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.HardwareController
import com.elitedarkkaiser.redmagic.RootShell

object ControlsTabUi {
    data class Refs(
        val magicKeyStatusLabel: TextView
    )

    data class Result(
        val view: LinearLayout,
        val refs: Refs
    )

    fun create(activity: Activity, deps: ControlsTabDeps): Result {
        val container = deps.scrollTabContainer()

        lateinit var rootCheckBtn: Button

        rootCheckBtn = deps.actionButton(
            "CHECK ROOT",
            false
        ) {
            rootCheckBtn.isEnabled = false
            rootCheckBtn.text = "CHECKING…"

            val submitted = deps.runBackground {
                val rooted = RootShell.hasRoot()

                rootCheckBtn.post {
                    if (
                        activity.isFinishing ||
                        activity.isDestroyed
                    ) {
                        return@post
                    }

                    rootCheckBtn.text = "CHECK ROOT"
                    rootCheckBtn.isEnabled = true

                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Root Status")
                        .setMessage(
                            if (rooted) {
                                "Root access granted\n\n" +
                                    "App is running as root"
                            } else {
                                "Root access NOT granted\n\n" +
                                    "Check your root manager"
                            }
                        )
                        .setPositiveButton("OK", null)
                        .show()

                    deps.refreshStatus()
                }
            }

            if (!submitted) {
                rootCheckBtn.text = "CHECK ROOT"
                rootCheckBtn.isEnabled = true

                MaterialAlertDialogBuilder(activity)
                    .setTitle("Root Status")
                    .setMessage(
                        "Unable to start the root check"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        var refreshOtaStatus: () -> Unit = {}
        var refreshRepairStatus: () -> Unit = {}
        val refreshBtn = deps.actionButton("REFRESH STATUS", false) {
            deps.refreshStatus()
            refreshOtaStatus()
            refreshRepairStatus()
        }

        val systemCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⚙", "SYSTEM"))
            addView(deps.row(rootCheckBtn, refreshBtn))
        }

        refreshOtaStatus = OtaSystemControlUi.attach(activity, systemCard, deps)
        refreshRepairStatus = DeviceIdRepairUi.attach(activity, systemCard, deps)

        val magicKeyStatusLabel =
            deps.subtleLabel("Current: loading…")

        val sliderAvailable =
            !deps.capabilities.scanComplete ||
                deps.capabilities.sliderAvailable

        val modeReadSubmitted = if (sliderAvailable) {
            deps.runBackground {
                val modeLabel =
                    deps.readMagicKeyModeLabel()

                magicKeyStatusLabel.post {
                    if (
                        activity.isFinishing ||
                        activity.isDestroyed
                    ) {
                        return@post
                    }

                    magicKeyStatusLabel.text =
                        "Current: $modeLabel"
                }
            }
        } else {
            magicKeyStatusLabel.text =
                "Current: Hardware unavailable"
            true
        }

        if (!modeReadSubmitted) {
            magicKeyStatusLabel.text =
                "Current: Unable to read"
        }

        var sliderAppBtnRef: Button? = null
        var sliderShortcutBtnRef: Button? = null

        val cameraBtn = deps.smallActionButton("CAMERA", false) {
            deps.applyStockMagicKeyMode("Camera", { HardwareController.setSliderOpenCamera() }, magicKeyStatusLabel, sliderAppBtnRef, sliderShortcutBtnRef)
        }

        val gameSpaceBtn = deps.smallActionButton("GAMESPACE", false) {
            deps.applyStockMagicKeyMode("GameSpace", { HardwareController.setSliderOpenGameSpace() }, magicKeyStatusLabel, sliderAppBtnRef, sliderShortcutBtnRef)
        }

        val soundModeBtn = deps.smallActionButton("SOUND MODE", false) {
            deps.applyStockMagicKeyMode("Sound Mode", { HardwareController.setSliderSoundMode() }, magicKeyStatusLabel, sliderAppBtnRef, sliderShortcutBtnRef)
        }

        val flashlightBtn = deps.smallActionButton("FLASHLIGHT", false) {
            deps.applyStockMagicKeyMode("Flashlight", { HardwareController.setSliderFlashlight() }, magicKeyStatusLabel, sliderAppBtnRef, sliderShortcutBtnRef)
        }

        val recorderBtn = deps.smallActionButton("VOICE RECORDER", false) {
            deps.applyStockMagicKeyMode("Voice Recorder", { HardwareController.setSliderVoiceRecorder() }, magicKeyStatusLabel, sliderAppBtnRef, sliderShortcutBtnRef)
        }

        val actionIcons = listOf(
            cameraBtn to android.R.drawable.ic_menu_camera,
            gameSpaceBtn to com.elitedarkkaiser.redmagic.R.drawable.ic_toolbox_game,
            soundModeBtn to android.R.drawable.ic_lock_silent_mode_off,
            flashlightBtn to com.elitedarkkaiser.redmagic.R.drawable.ic_toolbox_light,
            recorderBtn to android.R.drawable.ic_btn_speak_now
        )
        actionIcons.forEach { (button, resource) ->
            val icon = androidx.core.content.ContextCompat.getDrawable(activity, resource)?.mutate()
            icon?.setTint(AppTheme.accentColor)
            icon?.setBounds(0, 0, deps.dp(22), deps.dp(22))
            button.setCompoundDrawables(icon, null, null, null)
            button.compoundDrawablePadding = deps.dp(8)
            button.isAllCaps = false
        }

        val stockFunctionsCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⌘", "MAGIC KEY FUNCTIONS"))
            addView(deps.bodyText("Choose one stock Magic Key action. Selecting a stock action disables app launch mode."))
            addView(magicKeyStatusLabel)
            addView(deps.space(deps.dp(8)))
            addView(deps.flowRow(arrayOf(cameraBtn, gameSpaceBtn, soundModeBtn)))
            addView(deps.spacer(deps.dp(8)))
            addView(deps.flowRow(arrayOf(flashlightBtn, recorderBtn)))
            addView(deps.spacer(deps.dp(8)))
            addView(deps.singleRow(deps.actionButton("DISABLE MAGIC KEY ACTION", true) {
                deps.disableMagicKeyMode(
                    magicKeyStatusLabel,
                    sliderAppBtnRef,
                    sliderShortcutBtnRef
                )
            }))
        }

        val sliderAppBtn = deps.actionButton(
            "MAGIC KEY APP: ${deps.resolveMagicKeyAppLabel(deps.savedMagicKeyAppPackage())}",
            false
        ) {}
        sliderAppBtnRef = sliderAppBtn
        sliderAppBtn.setOnClickListener {
            deps.showMagicKeyAppPicker(
                sliderAppBtn,
                sliderShortcutBtnRef
            )
        }

        val sliderShortcutBtn = deps.actionButton(
            "MAGIC KEY SHORTCUT: " +
                (deps.savedMagicKeyShortcut()
                    ?: "Choose Shortcut"),
            false
        ) {}
        sliderShortcutBtnRef = sliderShortcutBtn
        sliderShortcutBtn.setOnClickListener {
            deps.showMagicKeyShortcutPicker(
                sliderShortcutBtn,
                sliderAppBtn
            )
        }

        val clearSliderAppBtn = deps.actionButton("CLEAR APP SELECTION", true) {
            deps.disableMagicKeyMode(
                magicKeyStatusLabel,
                sliderAppBtn,
                sliderShortcutBtn
            )
        }

        val dualAppButton = deps.actionButton(
            deps.sliderDualAppSummary(),
            false
        ) {}
        dualAppButton.setOnClickListener {
            deps.showSliderDualAppDialog(dualAppButton)
        }

        val sliderCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("↕", "SLIDER APP LAUNCH"))
            addView(deps.bodyText(
                "Choose one app or one Android app shortcut. " +
                    "App, shortcut, stock, and dual-slider modes " +
                    "are mutually exclusive."
            ))
            addView(deps.space(deps.dp(12)))
            addView(deps.singleRow(sliderAppBtn))
            addView(deps.space(deps.dp(12)))
            addView(deps.singleRow(sliderShortcutBtn))
            addView(deps.space(deps.dp(12)))
            addView(deps.singleRow(clearSliderAppBtn))
            addView(deps.space(deps.dp(12)))
            addView(deps.bodyText(
                "Optional: assign separate Up and Down apps, with " +
                    "a second pair active only during a scheduled time window."
            ))
            addView(deps.space(deps.dp(10)))
            addView(deps.singleRow(dualAppButton))
            addView(deps.space(deps.dp(4)))
            setPadding(deps.dp(18), deps.dp(18), deps.dp(18), deps.dp(26))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.sliderAvailable
        ) {
            stockFunctionsCard.addView(
                deps.bodyText(
                    "Magic Key controls unavailable: the slider " +
                        "vendor interface was not detected."
                ),
                1
            )
            sliderCard.addView(
                deps.bodyText(
                    "App launch is unavailable on this ROM."
                ),
                1
            )
            CapabilityUi.disableInteractions(
                stockFunctionsCard
            )
            CapabilityUi.disableInteractions(sliderCard)
        }

        fun magicKeyHero(): android.view.View = android.widget.FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, deps.dp(130))
            addView(android.widget.ImageView(activity).apply {
                setImageResource(com.elitedarkkaiser.redmagic.R.drawable.magic_key_hero)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = "Silver phone side with Magic Key highlighted"
            }, android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(android.view.View(activity).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, AppTheme.bgColor))
            }, android.widget.FrameLayout.LayoutParams(-1, -1))
        }

        InsidePages.install(container, "Controls & system", listOf(
            InsidePages.Page("System", "Root and device controls", listOf(systemCard), icon = "⚙"),
            InsidePages.Page("Magic Key", "Button and slider functions", emptyList(), icon = "⌘", factory = { listOf(magicKeyHero(), stockFunctionsCard, sliderCard) }),
            InsidePages.Page("Slider", "App launch actions", listOf(sliderCard), icon = "↕")
        ))

        return Result(
            view = container,
            refs = Refs(magicKeyStatusLabel = magicKeyStatusLabel)
        )
    }
}
