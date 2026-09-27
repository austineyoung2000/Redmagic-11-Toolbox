package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

object PerformanceModeProfileDialog {
    private data class LaunchableApp(
        val packageName: String,
        val label: String
    )

    fun show(activity: Activity) {
        AppTheme.configure(activity)
        val probe = PerformanceModeController.probe(activity)
        if (!probe.compatible) {
            Toast.makeText(
                activity,
                probe.message,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(activity, 18),
                dp(activity, 16),
                dp(activity, 18),
                dp(activity, 18)
            )
        }
        root.addView(TextView(activity).apply {
            text = "Per-App Performance Mode"
            textSize = 20f
            setTextColor(AppTheme.textPrimary)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            text = "Choose one of the verified stock REDMAGIC modes. " +
                "The mode is applied only while that app is foreground " +
                "and returns to the stock default when you leave it."
            textSize = 13f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, dp(activity, 5), 0, dp(activity, 14))
        })

        val addButton = actionButton(activity, "ADD APP", true)
        root.addView(addButton)
        root.addView(TextView(activity).apply {
            text = "SAVED APP MODES"
            textSize = 12f
            setTextColor(AppTheme.textSecondary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(activity, 18), 0, dp(activity, 8))
        })
        val profilesContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(profilesContainer)

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        lateinit var dialog: AlertDialog
        lateinit var renderProfiles: () -> Unit

        fun chooseMode(
            app: LaunchableApp,
            existing: PerformanceModeProfile?
        ) {
            val modes = RedmagicPerformanceMode.entries
            val labels = modes.map { mode ->
                when (mode) {
                    RedmagicPerformanceMode.ECO ->
                        "Eco — lower power and heat"
                    RedmagicPerformanceMode.BALANCE ->
                        "Balance — stock balanced tuning"
                    RedmagicPerformanceMode.RISE ->
                        "Rise — higher performance"
                }
            }.toTypedArray()
            val selected = modes.indexOf(
                existing?.mode ?: RedmagicPerformanceMode.BALANCE
            ).coerceAtLeast(0)

            MaterialAlertDialogBuilder(activity)
                .setTitle("${app.label} performance")
                .setSingleChoiceItems(labels, selected) {
                        picker, which ->
                    val saved = PerformanceModeStorage.saveProfile(
                        activity,
                        PerformanceModeProfile(
                            packageName = app.packageName,
                            appLabel = app.label,
                            mode = modes[which],
                            enabled = existing?.enabled ?: true
                        )
                    )
                    if (saved) {
                        PerformanceModeCoordinator.clearRuntimeState()
                        picker.dismiss()
                        renderProfiles()
                    } else {
                        Toast.makeText(
                            activity,
                            "Could not save the performance profile",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        renderProfiles = {
            profilesContainer.removeAllViews()
            val profiles = PerformanceModeStorage.readProfiles(activity)
            if (profiles.isEmpty()) {
                profilesContainer.addView(TextView(activity).apply {
                    text = "No per-app performance modes saved yet."
                    textSize = 13f
                    setTextColor(AppTheme.textSecondary)
                    setPadding(
                        dp(activity, 4),
                        dp(activity, 8),
                        dp(activity, 4),
                        dp(activity, 8)
                    )
                })
            }

            profiles.forEachIndexed { index, profile ->
                if (index > 0) {
                    profilesContainer.addView(
                        View(activity),
                        LinearLayout.LayoutParams(
                            1,
                            dp(activity, 10)
                        )
                    )
                }
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        dp(activity, 14),
                        dp(activity, 13),
                        dp(activity, 14),
                        dp(activity, 13)
                    )
                    background = panelBackground(activity)
                }
                card.addView(TextView(activity).apply {
                    text = profile.appLabel
                    textSize = 16f
                    setTextColor(AppTheme.textPrimary)
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                })
                card.addView(TextView(activity).apply {
                    text = profile.packageName
                    textSize = 11f
                    setTextColor(AppTheme.textSecondary)
                    maxLines = 1
                    setPadding(0, dp(activity, 2), 0, dp(activity, 7))
                })

                card.addView(MaterialSwitch(activity).apply {
                    text = "Use ${profile.mode.label} mode"
                    textSize = 14f
                    setTextColor(AppTheme.textPrimary)
                    isChecked = profile.enabled
                    setOnCheckedChangeListener { _, checked ->
                        PerformanceModeStorage.saveProfile(
                            activity,
                            profile.copy(enabled = checked)
                        )
                        PerformanceModeCoordinator.clearRuntimeState()
                    }
                })

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(activity, 8), 0, 0)
                }
                val change = actionButton(
                    activity,
                    "CHANGE MODE",
                    false
                ).apply {
                    setOnClickListener {
                        chooseMode(
                            LaunchableApp(
                                profile.packageName,
                                profile.appLabel
                            ),
                            profile
                        )
                    }
                }
                val delete = actionButton(
                    activity,
                    "DELETE",
                    false
                ).apply {
                    setTextColor(Color.rgb(255, 110, 110))
                    setOnClickListener {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Delete ${profile.appLabel}?")
                            .setMessage(
                                "Its saved performance mode will be removed."
                            )
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                PerformanceModeStorage.removeProfile(
                                    activity,
                                    profile.packageName
                                )
                                PerformanceModeCoordinator
                                    .clearRuntimeState()
                                renderProfiles()
                            }
                            .show()
                    }
                }
                row.addView(
                    change,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply { marginEnd = dp(activity, 6) }
                )
                row.addView(
                    delete,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
                card.addView(row)
                profilesContainer.addView(card)
            }
        }

        addButton.setOnClickListener {
            val manager = activity.packageManager
            val apps = manager.getInstalledApplications(0)
                .asSequence()
                .filter {
                    it.enabled &&
                        it.packageName != activity.packageName &&
                        manager.getLaunchIntentForPackage(
                            it.packageName
                        ) != null
                }
                .map {
                    LaunchableApp(
                        it.packageName,
                        it.loadLabel(manager).toString().trim()
                    )
                }
                .filter { it.label.isNotBlank() }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()

            MaterialAlertDialogBuilder(activity)
                .setTitle("Choose app")
                .setItems(
                    apps.map {
                        "${it.label}\n${it.packageName}"
                    }.toTypedArray()
                ) { _, which ->
                    val app = apps[which]
                    chooseMode(
                        app,
                        PerformanceModeStorage.getProfile(
                            activity,
                            app.packageName
                        )
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        renderProfiles()
        dialog = MaterialAlertDialogBuilder(activity)
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
    }

    private fun actionButton(
        activity: Activity,
        label: String,
        filled: Boolean
    ): MaterialButton {
        return MaterialButton(
            activity,
            null,
            if (filled) {
                com.google.android.material.R.attr.materialButtonStyle
            } else {
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            }
        ).apply {
            text = label
            isAllCaps = false
            setTextColor(AppTheme.textPrimary)
            cornerRadius = dp(activity, 14)
            minHeight = dp(activity, 46)
        }
    }

    private fun panelBackground(activity: Activity): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(activity, 16).toFloat()
            setColor(AppTheme.panelColor)
            setStroke(dp(activity, 1), AppTheme.borderColor)
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
