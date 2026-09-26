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

object RefreshRateProfileDialog {
    private data class LaunchableApp(
        val packageName: String,
        val label: String
    )

    fun show(activity: Activity) {
        AppTheme.configure(activity)
        val supportedRates = RefreshRateStorage.supportedRates(activity)
        if (supportedRates.isEmpty()) {
            Toast.makeText(
                activity,
                "No supported display refresh rates were detected",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 16),
                dp(activity, 18), dp(activity, 18))
        }
        root.addView(TextView(activity).apply {
            text = "Per-App Refresh Rate"
            textSize = 20f
            setTextColor(AppTheme.textPrimary)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            text = "Choose a display rate for each app. The stock " +
                "REDMAGIC service applies it only when that app is " +
                "foreground; 0 means follow the system setting."
            textSize = 13f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, dp(activity, 5), 0, dp(activity, 14))
        })

        val addButton = actionButton(activity, "ADD APP", true)
        root.addView(addButton)
        root.addView(TextView(activity).apply {
            text = "SAVED APP RATES"
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
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        lateinit var dialog: AlertDialog

        fun chooseRate(
            app: LaunchableApp,
            existing: RefreshRateProfile? = null,
            onSaved: () -> Unit
        ) {
            val choices = listOf(0) + supportedRates
            val labels = choices.map {
                if (it == 0) "Follow system" else "$it Hz"
            }.toTypedArray()
            val selected = choices.indexOf(
                existing?.refreshRateHz ?: 0
            ).coerceAtLeast(0)

            MaterialAlertDialogBuilder(activity)
                .setTitle("${app.label} refresh rate")
                .setSingleChoiceItems(labels, selected) {
                        picker, which ->
                    val saved = RefreshRateStorage.saveProfile(
                        activity,
                        RefreshRateProfile(
                            packageName = app.packageName,
                            appLabel = app.label,
                            refreshRateHz = choices[which],
                            enabled = existing?.enabled ?: true
                        )
                    )
                    if (saved) {
                        RefreshRateCoordinator.clearRuntimeState()
                        picker.dismiss()
                        onSaved()
                    } else {
                        Toast.makeText(
                            activity,
                            "Could not save the refresh-rate profile",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun renderProfiles() {
            profilesContainer.removeAllViews()
            val profiles = RefreshRateStorage.readProfiles(activity)
                .filterNot { it.pendingReset }
            if (profiles.isEmpty()) {
                profilesContainer.addView(TextView(activity).apply {
                    text = "No per-app refresh rates saved yet."
                    textSize = 13f
                    setTextColor(AppTheme.textSecondary)
                    setPadding(dp(activity, 4), dp(activity, 8),
                        dp(activity, 4), dp(activity, 8))
                })
                return
            }

            profiles.forEachIndexed { index, profile ->
                if (index > 0) {
                    profilesContainer.addView(View(activity),
                        LinearLayout.LayoutParams(1, dp(activity, 10)))
                }
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(activity, 14), dp(activity, 13),
                        dp(activity, 14), dp(activity, 13))
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
                    setPadding(0, dp(activity, 2), 0, dp(activity, 8))
                })

                val enabledSwitch = MaterialSwitch(activity).apply {
                    text = if (profile.refreshRateHz == 0) {
                        "Follow system"
                    } else {
                        "Use ${profile.refreshRateHz} Hz"
                    }
                    textSize = 14f
                    setTextColor(AppTheme.textPrimary)
                    isChecked = profile.enabled
                    setOnCheckedChangeListener { _, checked ->
                        RefreshRateStorage.saveProfile(
                            activity,
                            profile.copy(enabled = checked)
                        )
                        RefreshRateCoordinator.clearRuntimeState()
                    }
                }
                card.addView(enabledSwitch)

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(activity, 8), 0, 0)
                }
                val change = actionButton(activity, "CHANGE RATE", false)
                change.setOnClickListener {
                    chooseRate(
                        LaunchableApp(
                            profile.packageName,
                            profile.appLabel
                        ),
                        profile
                    ) { renderProfiles() }
                }
                val delete = actionButton(activity, "DELETE", false).apply {
                    setTextColor(Color.rgb(255, 110, 110))
                    setOnClickListener {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Delete ${profile.appLabel}?")
                            .setMessage(
                                "Its saved refresh-rate setting will be removed."
                            )
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                RefreshRateStorage.removeProfile(
                                    activity,
                                    profile.packageName
                                )
                                RefreshRateCoordinator.clearRuntimeState()
                                renderProfiles()
                            }
                            .show()
                    }
                }
                row.addView(change, LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginEnd = dp(activity, 6) })
                row.addView(delete, LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ))
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
            val labels = apps.map {
                "${it.label}\n${it.packageName}"
            }.toTypedArray()
            MaterialAlertDialogBuilder(activity)
                .setTitle("Select an app or game")
                .setItems(labels) { _, which ->
                    val app = apps[which]
                    chooseRate(
                        app,
                        RefreshRateStorage.getProfile(
                            activity,
                            app.packageName
                        )
                    ) { renderProfiles() }
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
        primary: Boolean
    ): MaterialButton {
        return MaterialButton(activity).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            cornerRadius = dp(activity, 14)
            setTextColor(AppTheme.textPrimary)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (primary) AppTheme.accentColor else AppTheme.chipOnColor
            )
        }
    }

    private fun panelBackground(activity: Activity): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(activity, 18).toFloat()
            setColor(AppTheme.panelColor)
            setStroke(dp(activity, 1), AppTheme.borderColor)
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density)
            .toInt()
    }
}
