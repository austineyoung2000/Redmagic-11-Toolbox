package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch

object LightingTabUi {
    fun create(activity: Activity, deps: LightingTabDeps): LinearLayout {
        val container = deps.scrollTabContainer()

        val previewSwitch = MaterialSwitch(activity).apply {
            isChecked = deps.getRealTimePreviewEnabled()
            setOnCheckedChangeListener { _, checked ->
                deps.setRealTimePreviewEnabled(checked)
                deps.saveRealTimePreviewEnabled(checked)
            }
        }

        val previewRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, deps.dp(8), 0, deps.dp(8))

            addView(TextView(activity).apply {
                text = "Real-time preview"
                textSize = 14f
                setTextColor(AppTheme.textPrimary)
            }, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))

            addView(previewSwitch)
        }

        val zonesCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("✦", "LIGHTING SETTINGS"))
            addView(previewRow)
        }

        val chargingModeCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⚡", "CHARGING MODE"))
            addView(deps.bodyText("Applies only while the device is plugged in and charging. Charging Mode takes LED priority over Game Mode and normal LED profiles."))

            val chargingSwitch = MaterialSwitch(activity).apply {
                isChecked = deps.getChargingLedEnabled()
                setOnCheckedChangeListener { _, checked ->
                    deps.setChargingLedEnabled(checked)
                }
            }

            val chargingRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = "Enable charging LED profile"
                    textSize = 14f
                    setTextColor(AppTheme.textPrimary)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(chargingSwitch)
            }

            addView(chargingRow)
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.ledAvailable
        ) {
            listOf(
                zonesCard to
                    "LED controls unavailable: required vendor " +
                    "interfaces were not detected.",
                chargingModeCard to
                    "Charging lighting is unavailable on this ROM."
            ).forEach { (card, message) ->
                card.addView(
                    deps.bodyText(message),
                    1
                )
                CapabilityUi.disableInteractions(card)
            }
        }

        val notificationCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("✉", "SCREEN-OFF NOTIFICATIONS"))
            addView(deps.bodyText("Choose a color and a limited lighting window for each app. Incoming calls and charging take priority."))
            addView(deps.singleRow(deps.actionButton("NOTIFICATION LIGHTING", false) {
                com.elitedarkkaiser.redmagic.NotificationLightingDialog.show(activity)
            }))
        }
        val callLightingCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("☎", "CALL LIGHTING"))
            addView(deps.bodyText("Applies while an incoming call is ringing; answering or dismissing restores the previous lighting. Priority: Incoming Call > Charging > Notification > Game Mode > Normal LEDs."))

            val callSwitch = MaterialSwitch(activity).apply {
                isChecked = deps.getCallLightingEnabled()
                setOnCheckedChangeListener { _, checked ->
                    deps.setCallLightingEnabled(checked)
                }
            }

            val callRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = "Enable call lighting"
                    textSize = 14f
                    setTextColor(AppTheme.textPrimary)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(callSwitch)
            }

            val pauseFanSwitch = MaterialSwitch(activity).apply {
                isChecked = deps.getPauseFanDuringCalls()
                setOnCheckedChangeListener { _, checked ->
                    deps.setPauseFanDuringCalls(checked)
                }
            }

            val pauseFanRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, deps.dp(8), 0, 0)
                addView(TextView(activity).apply {
                    text = "Pause fan during calls"
                    textSize = 14f
                    setTextColor(AppTheme.textPrimary)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(pauseFanSwitch)
            }

            addView(callRow)
            addView(pauseFanRow)
            addView(deps.bodyText("Temporarily turns the fan off while an incoming call rings, then restores it when answered or dismissed."))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.ledAvailable
        ) {
            callLightingCard.addView(
                deps.bodyText(
                    "Call lighting is unavailable because LED " +
                        "interfaces were not detected."
                ),
                1
            )
            CapabilityUi.disableInteractions(
                callLightingCard
            )
        }

        InsidePages.install(container, "Lighting", listOf(
            InsidePages.Page("RGB Studio", "Color sequences and timing", emptyList(), factory = {
                val editor = deps.createRgbStudioPage { }
                if (deps.capabilities.scanComplete && !deps.capabilities.ledAvailable) {
                    editor.addView(deps.bodyText("RGB Studio is unavailable on this ROM."), 0)
                    CapabilityUi.disableInteractions(editor)
                }
                val hero = android.widget.FrameLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, deps.dp(130))
                    addView(android.widget.ImageView(context).apply {
                        setImageResource(com.elitedarkkaiser.redmagic.R.drawable.rgb_silver_hero)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        contentDescription = "Silver REDMAGIC phone with illuminated triggers and logo"
                    }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    addView(android.view.View(context).apply {
                        background = android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, AppTheme.bgColor))
                    }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                listOf(hero, editor)
            }),
            InsidePages.Page("Charging", "Charging lighting profile", emptyList(), factory = { listOf(chargingModeCard) + com.elitedarkkaiser.redmagic.ModeLightingUi.charging(activity, deps) }),
            InsidePages.Page("Calls", "Incoming call lighting", emptyList(), factory = { listOf(callLightingCard) + com.elitedarkkaiser.redmagic.ModeLightingUi.calls(activity, deps) }),
            InsidePages.Page("Notifications", "Screen-off notification lighting", emptyList(), factory = { listOf(com.elitedarkkaiser.redmagic.NotificationLightingPage.create(activity, deps)) }),
            InsidePages.Page("Lighting settings", "Preview preferences", listOf(zonesCard), icon = "⚙")
        ), prefix = {
            val hero = android.widget.FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, deps.dp(130))
                addView(android.widget.ImageView(context).apply {
                    setImageResource(com.elitedarkkaiser.redmagic.R.drawable.rgb_silver_hero)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Silver REDMAGIC phone with illuminated triggers and logo"
                }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(android.view.View(context).apply {
                    background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, AppTheme.bgColor))
                }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            listOf(hero) + com.elitedarkkaiser.redmagic.InlineLightingUi.create(activity, deps)
        })
        return container
    }
}
