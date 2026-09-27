package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

object TouchTuningProfileDialog {
    private data class LaunchableApp(
        val packageName: String,
        val label: String
    )

    fun show(activity: Activity) {
        AppTheme.configure(activity)
        val probe = TouchTuningController.probe(activity)
        if (!probe.compatible) {
            Toast.makeText(activity, probe.message, Toast.LENGTH_LONG).show()
            return
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 16),
                dp(activity, 18), dp(activity, 18))
        }
        root.addView(TextView(activity).apply {
            text = "Per-App Touch Response"
            textSize = 20f
            setTextColor(AppTheme.textPrimary)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            text = "Configure the stock REDMAGIC touch sampling, " +
                "sensitivity, follow response, stability, and edge " +
                "protection values stored for each app."
            textSize = 13f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, dp(activity, 5), 0, dp(activity, 14))
        })
        val addButton = actionButton(activity, "ADD APP", true)
        root.addView(addButton)
        val profilesContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(activity, 12), 0, 0)
        }
        root.addView(profilesContainer)

        fun runChange(task: () -> TouchTuningResult, onDone: () -> Unit) {
            Thread(
                {
                    val result = runCatching(task).getOrElse {
                        TouchTuningResult(
                            false,
                            null,
                            it.message ?: "Touch-tuning update failed"
                        )
                    }
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) {
                            return@runOnUiThread
                        }
                        Toast.makeText(
                            activity,
                            buildString {
                                append(result.message)
                                result.backend?.let {
                                    append(" • ").append(it)
                                }
                            },
                            Toast.LENGTH_LONG
                        ).show()
                        onDone()
                    }
                },
                "RedMagicTouchTuning"
            ).start()
        }

        lateinit var renderProfiles: () -> Unit

        fun showEditor(
            app: LaunchableApp,
            existing: TouchTuningProfile?
        ) {
            var sampleRate = existing?.sampleRateHz
                ?.takeIf { it in probe.sampleRates }
                ?: probe.sampleRates.first()
            var sensitivity = existing?.sensitivity ?: 0
            var follow = existing?.touchFollow ?: 0
            var stability = existing?.stability ?: 0
            var edgeEnabled = existing?.edgeProtectionEnabled ?: true
            var edgeLevel = existing?.edgeProtectionLevel ?: 0

            val editor = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 18), 0,
                    dp(activity, 18), 0)
            }

            fun addChoice(
                title: String,
                labels: List<String>,
                selectedIndex: () -> Int,
                onSelected: (Int) -> Unit
            ) {
                val button = actionButton(activity, "", false)
                fun updateLabel() {
                    button.text = "$title: ${labels[selectedIndex()]}"
                }
                updateLabel()
                button.setOnClickListener {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(title)
                        .setSingleChoiceItems(
                            labels.toTypedArray(),
                            selectedIndex()
                        ) { dialog, which ->
                            onSelected(which)
                            updateLabel()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                editor.addView(button)
            }

            addChoice(
                "Touch sampling",
                probe.sampleRates.map { "$it Hz" },
                { probe.sampleRates.indexOf(sampleRate).coerceAtLeast(0) }
            ) { sampleRate = probe.sampleRates[it] }

            val responseValues = listOf(-2, -1, 0, 1, 2)
            val responseLabels = listOf(
                "Lowest", "Low", "Default", "High", "Highest"
            )
            addChoice(
                "Touch sensitivity",
                responseLabels,
                { responseValues.indexOf(sensitivity) }
            ) { sensitivity = responseValues[it] }
            addChoice(
                "Touch follow",
                responseLabels,
                { responseValues.indexOf(follow) }
            ) { follow = responseValues[it] }
            addChoice(
                "Touch stability",
                responseLabels,
                { responseValues.indexOf(stability) }
            ) { stability = responseValues[it] }

            val edgeSwitch = MaterialSwitch(activity).apply {
                text = "Edge mistouch protection"
                textSize = 14f
                setTextColor(AppTheme.textPrimary)
                isChecked = edgeEnabled
                setOnCheckedChangeListener { _, checked ->
                    edgeEnabled = checked
                }
            }
            editor.addView(edgeSwitch)
            val edgeValues = listOf(-1, 0, 1)
            addChoice(
                "Protected edge width",
                listOf("Narrow", "Medium", "Wide"),
                { edgeValues.indexOf(edgeLevel) }
            ) { edgeLevel = edgeValues[it] }

            MaterialAlertDialogBuilder(activity)
                .setTitle(app.label)
                .setView(editor)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    val profile = TouchTuningProfile(
                        packageName = app.packageName,
                        appLabel = app.label,
                        enabled = existing?.enabled ?: true,
                        sampleRateHz = sampleRate,
                        sensitivity = sensitivity,
                        touchFollow = follow,
                        stability = stability,
                        edgeProtectionEnabled = edgeEnabled,
                        edgeProtectionLevel = edgeLevel
                    )
                    runChange(
                        { TouchTuningController.saveAndApply(activity, profile) },
                        renderProfiles
                    )
                }
                .show()
        }

        renderProfiles = {
            profilesContainer.removeAllViews()
            val profiles = TouchTuningStorage.readProfiles(activity)
            if (profiles.isEmpty()) {
                profilesContainer.addView(TextView(activity).apply {
                    text = "No per-app touch profiles saved yet."
                    textSize = 13f
                    setTextColor(AppTheme.textSecondary)
                    setPadding(dp(activity, 4), dp(activity, 8),
                        dp(activity, 4), dp(activity, 8))
                })
            }
            profiles.forEachIndexed { index, profile ->
                if (index > 0) {
                    profilesContainer.addView(
                        View(activity),
                        LinearLayout.LayoutParams(1, dp(activity, 10))
                    )
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
                })
                card.addView(TextView(activity).apply {
                    text = profile.packageName
                    textSize = 11f
                    setTextColor(AppTheme.textSecondary)
                })
                card.addView(TextView(activity).apply {
                    text = "${profile.sampleRateHz} Hz • sensitivity " +
                        "${signed(profile.sensitivity)} • follow " +
                        "${signed(profile.touchFollow)} • stability " +
                        signed(profile.stability)
                    textSize = 12f
                    setTextColor(AppTheme.textSecondary)
                    setPadding(0, dp(activity, 5), 0, dp(activity, 5))
                })
                val enabledSwitch = MaterialSwitch(activity).apply {
                    text = "Apply stock touch tuning"
                    textSize = 14f
                    setTextColor(AppTheme.textPrimary)
                    isChecked = profile.enabled
                    setOnCheckedChangeListener { button, checked ->
                        button.isEnabled = false
                        runChange(
                            {
                                TouchTuningController.saveAndApply(
                                    activity,
                                    profile.copy(enabled = checked)
                                )
                            },
                            renderProfiles
                        )
                    }
                }
                card.addView(enabledSwitch)

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                val edit = actionButton(activity, "EDIT", false).apply {
                    setOnClickListener {
                        showEditor(
                            LaunchableApp(
                                profile.packageName,
                                profile.appLabel
                            ),
                            TouchTuningStorage.getProfile(
                                activity,
                                profile.packageName
                            )
                        )
                    }
                }
                val delete = actionButton(activity, "DELETE", false).apply {
                    setTextColor(Color.rgb(255, 110, 110))
                    setOnClickListener {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Delete ${profile.appLabel}?")
                            .setMessage(
                                "The vendor touch entries for this app will be removed."
                            )
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                runChange(
                                    {
                                        TouchTuningController.remove(
                                            activity,
                                            profile
                                        )
                                    },
                                    renderProfiles
                                )
                            }
                            .show()
                    }
                }
                row.addView(edit, LinearLayout.LayoutParams(
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
                        manager.getLaunchIntentForPackage(it.packageName) != null
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
            if (apps.isEmpty()) {
                Toast.makeText(
                    activity,
                    "No launchable applications were found",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle("Select an app or game")
                .setItems(
                    apps.map { "${it.label}\n${it.packageName}" }
                        .toTypedArray()
                ) { _, which ->
                    val app = apps[which]
                    showEditor(
                        app,
                        TouchTuningStorage.getProfile(
                            activity,
                            app.packageName
                        )
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        renderProfiles()
        MaterialAlertDialogBuilder(activity)
            .setView(ScrollView(activity).apply {
                isFillViewport = true
                addView(root)
            })
            .setNegativeButton("Close", null)
            .show()
    }

    private fun signed(value: Int): String {
        return if (value > 0) "+$value" else value.toString()
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
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
