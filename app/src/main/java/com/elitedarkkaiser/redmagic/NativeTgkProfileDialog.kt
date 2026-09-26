package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

object NativeTgkProfileDialog {

    private data class LaunchableApp(
        val packageName: String,
        val label: String
    )

    fun show(activity: Activity) {
        AppTheme.configure(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(activity, 18),
                dp(activity, 16),
                dp(activity, 18),
                dp(activity, 18)
            )
        }

        root.addView(
            TextView(activity).apply {
                text = "Game Trigger Mapping"
                textSize = 20f
                setTextColor(AppTheme.textPrimary)
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        root.addView(
            TextView(activity).apply {
                text =
                    "Use REDMAGIC’s native touch-game-key engine " +
                    "for independent per-app L and R targets. " +
                    "Mappings work without opening Game Space."
                textSize = 13f
                setTextColor(AppTheme.textSecondary)
                setPadding(
                    0,
                    dp(activity, 5),
                    0,
                    dp(activity, 14)
                )
            }
        )

        val addButton = actionButton(
            activity,
            "ADD APP MAPPING",
            primary = true
        )

        root.addView(addButton)

        val transferRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val importButton = actionButton(
            activity,
            "IMPORT",
            primary = false
        )
        val exportAllButton = actionButton(
            activity,
            "EXPORT ALL",
            primary = false
        )

        transferRow.addView(
            importButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                topMargin = dp(activity, 8)
            }
        )
        transferRow.addView(
            View(activity),
            LinearLayout.LayoutParams(
                dp(activity, 8),
                1
            )
        )
        transferRow.addView(
            exportAllButton,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                topMargin = dp(activity, 8)
            }
        )

        root.addView(transferRow)

        root.addView(
            TextView(activity).apply {
                text = "SAVED APP MAPPINGS"
                textSize = 12f
                setTextColor(AppTheme.textSecondary)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(
                    0,
                    dp(activity, 18),
                    0,
                    dp(activity, 8)
                )
            }
        )

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

        lateinit var managerDialog: AlertDialog

        fun launchEditor(
            profile: NativeTgkProfile,
            orientation: NativeTgkOrientation
        ) {
            if (!Settings.canDrawOverlays(activity)) {
                Toast.makeText(
                    activity,
                    "Allow display over other apps, then open Game Trigger Mapping again",
                    Toast.LENGTH_LONG
                ).show()

                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse(
                            "package:${activity.packageName}"
                        )
                    )
                )
                return
            }

            val started = runCatching {
                activity.startService(
                    Intent(
                        activity,
                        NativeTgkEditorService::class.java
                    ).apply {
                        putExtra(
                            NativeTgkEditorService.EXTRA_PACKAGE_NAME,
                            profile.packageName
                        )
                        putExtra(
                            NativeTgkEditorService.EXTRA_APP_LABEL,
                            profile.appLabel
                        )
                        putExtra(
                            NativeTgkEditorService.EXTRA_ORIENTATION,
                            orientation.name
                        )
                    }
                )
            }.isSuccess

            if (!started) {
                Toast.makeText(
                    activity,
                    "Could not start the trigger target editor",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            managerDialog.dismiss()
        }

        fun showOrientationPicker(
            profile: NativeTgkProfile
        ) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Choose mapping orientation")
                .setItems(
                    arrayOf(
                        "Landscape",
                        "Portrait"
                    )
                ) { _, which ->
                    val orientation = if (which == 0) {
                        NativeTgkOrientation.LANDSCAPE
                    } else {
                        NativeTgkOrientation.PORTRAIT
                    }

                    launchEditor(
                        profile,
                        orientation
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun showAppPicker() {
            val manager = activity.packageManager

            val apps = manager
                .getInstalledApplications(0)
                .asSequence()
                .filter {
                    it.enabled &&
                        it.packageName != activity.packageName
                }
                .filter {
                    manager.getLaunchIntentForPackage(
                        it.packageName
                    ) != null
                }
                .map {
                    LaunchableApp(
                        packageName = it.packageName,
                        label = it.loadLabel(manager)
                            .toString()
                            .trim()
                    )
                }
                .filter {
                    it.label.isNotBlank()
                }
                .distinctBy {
                    it.packageName
                }
                .sortedBy {
                    it.label.lowercase()
                }
                .toList()

            if (apps.isEmpty()) {
                Toast.makeText(
                    activity,
                    "No launchable applications were found",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val labels = apps.map {
                "${it.label}\n${it.packageName}"
            }.toTypedArray()

            MaterialAlertDialogBuilder(activity)
                .setTitle("Select an app or game")
                .setItems(labels) { _, position ->
                    val app = apps[position]
                    val existing =
                        NativeTgkStorage.getProfile(
                            activity,
                            app.packageName
                        )

                    val profile = existing
                        ?: NativeTgkProfile(
                            packageName = app.packageName,
                            appLabel = app.label
                        )

                    showOrientationPicker(profile)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun disableActiveProfileIfNeeded(
            packageName: String
        ) {
            if (
                NativeTgkRuntimeState.activePackage() !=
                packageName
            ) {
                return
            }

            NativeTgkRuntimeState.clear()

            Thread(
                {
                    NativeTgkCoordinator.disable(
                        activity.applicationContext,
                        "profile disabled or removed"
                    )
                },
                "RedMagicTgkProfileCleanup"
            ).start()
        }

        fun renderProfiles() {
            profilesContainer.removeAllViews()

            val profiles =
                NativeTgkStorage.readProfiles(activity)

            if (profiles.isEmpty()) {
                profilesContainer.addView(
                    TextView(activity).apply {
                        text =
                            "No app mappings saved yet. Add an " +
                            "app, choose an orientation, and place " +
                            "the L and R targets."
                        textSize = 13f
                        setTextColor(AppTheme.textSecondary)
                        setPadding(
                            dp(activity, 4),
                            dp(activity, 8),
                            dp(activity, 4),
                            dp(activity, 8)
                        )
                    }
                )
                return
            }

            profiles.forEachIndexed { index, profile ->
                if (index > 0) {
                    profilesContainer.addView(
                        View(activity).apply {
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    1,
                                    dp(activity, 10)
                                )
                        }
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

                card.addView(
                    TextView(activity).apply {
                        text = profile.appLabel
                        textSize = 16f
                        setTextColor(AppTheme.textPrimary)
                        setTypeface(
                            typeface,
                            Typeface.BOLD
                        )
                        maxLines = 1
                    }
                )

                card.addView(
                    TextView(activity).apply {
                        text = profile.packageName
                        textSize = 11f
                        setTextColor(
                            AppTheme.textSecondary
                        )
                        maxLines = 1
                        setPadding(
                            0,
                            dp(activity, 2),
                            0,
                            dp(activity, 6)
                        )
                    }
                )

                val activeLayout = profile.activeLayout()
                val layoutRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        0,
                        0,
                        0,
                        dp(activity, 8)
                    )
                }

                val selectLayoutButton = actionButton(
                    activity,
                    "LAYOUT: ${activeLayout?.name ?: "Default"}",
                    primary = false
                ).apply {
                    setOnClickListener {
                        val layouts = profile.layouts
                        val labels = layouts.map { layout ->
                            buildString {
                                append(layout.name)
                                if (layout.id == profile.activeLayoutId) {
                                    append(" • Active")
                                }
                            }
                        }.toTypedArray()

                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Select trigger layout")
                            .setSingleChoiceItems(
                                labels,
                                layouts.indexOfFirst {
                                    it.id == profile.activeLayoutId
                                }.coerceAtLeast(0)
                            ) { dialog, which ->
                                val selected = layouts[which]
                                NativeTgkStorage.saveProfile(
                                    activity,
                                    profile.copy(
                                        activeLayoutId = selected.id
                                    )
                                )
                                dialog.dismiss()
                                renderProfiles()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }

                val newLayoutButton = actionButton(
                    activity,
                    "NEW",
                    primary = false
                ).apply {
                    isEnabled = profile.layouts.size <
                        NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE
                    setOnClickListener {
                        val input = EditText(activity).apply {
                            hint = "Layout name"
                            setSingleLine(true)
                            setPadding(
                                dp(activity, 18),
                                dp(activity, 8),
                                dp(activity, 18),
                                dp(activity, 8)
                            )
                        }

                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Duplicate current layout")
                            .setMessage(
                                "The new layout starts with the " +
                                    "current targets and trigger behavior."
                            )
                            .setView(input)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Create") { _, _ ->
                                val name = input.text
                                    ?.toString()
                                    ?.trim()
                                    .orEmpty()

                                if (
                                    name.isBlank() ||
                                    name.length > 40 ||
                                    profile.layouts.any {
                                        it.name.equals(
                                            name,
                                            ignoreCase = true
                                        )
                                    }
                                ) {
                                    Toast.makeText(
                                        activity,
                                        "Enter a unique layout name up to 40 characters",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@setPositiveButton
                                }

                                val source = profile.activeLayout()
                                    ?: return@setPositiveButton
                                val id = "layout_" +
                                    System.currentTimeMillis()
                                        .toString(36)
                                val created = source.copy(
                                    id = id,
                                    name = name
                                )

                                NativeTgkStorage.saveProfile(
                                    activity,
                                    profile.copy(
                                        activeLayoutId = id,
                                        layouts =
                                            profile.layouts + created
                                    )
                                )
                                renderProfiles()
                            }
                            .show()
                    }
                }

                val deleteLayoutButton = actionButton(
                    activity,
                    "DELETE",
                    primary = false
                ).apply {
                    isEnabled = profile.layouts.size > 1
                    setTextColor(Color.rgb(255, 110, 110))
                    setOnClickListener {
                        val selected = profile.activeLayout()
                            ?: return@setOnClickListener

                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Delete ${selected.name}?")
                            .setMessage(
                                "Its portrait and landscape targets " +
                                    "and trigger behavior will be removed."
                            )
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                val remaining = profile.layouts
                                    .filterNot {
                                        it.id == selected.id
                                    }
                                val next = remaining.first()

                                NativeTgkStorage.saveProfile(
                                    activity,
                                    profile.copy(
                                        activeLayoutId = next.id,
                                        layouts = remaining
                                    )
                                )
                                renderProfiles()
                            }
                            .show()
                    }
                }

                listOf(
                    selectLayoutButton to 2f,
                    newLayoutButton to 0.8f,
                    deleteLayoutButton to 1f
                ).forEachIndexed { layoutIndex, item ->
                    if (layoutIndex > 0) {
                        layoutRow.addView(
                            View(activity),
                            LinearLayout.LayoutParams(
                                dp(activity, 6),
                                1
                            )
                        )
                    }

                    layoutRow.addView(
                        item.first,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            item.second
                        )
                    )
                }

                card.addView(layoutRow)

                val portraitStatus =
                    if (
                        profile.mappingFor(
                            NativeTgkOrientation.PORTRAIT
                        )
                            ?.isComplete() == true
                    ) {
                        "Portrait configured"
                    } else {
                        "Portrait not configured"
                    }

                val landscapeStatus =
                    if (
                        profile.mappingFor(
                            NativeTgkOrientation.LANDSCAPE
                        )
                            ?.isComplete() == true
                    ) {
                        "Landscape configured"
                    } else {
                        "Landscape not configured"
                    }

                card.addView(
                    TextView(activity).apply {
                        text =
                            "$landscapeStatus • $portraitStatus"
                        textSize = 12f
                        setTextColor(
                            AppTheme.textSecondary
                        )
                        setPadding(
                            0,
                            0,
                            0,
                            dp(activity, 8)
                        )
                    }
                )

                val enabledSwitch =
                    MaterialSwitch(activity).apply {
                        text = "Enable for this app"
                        setTextColor(
                            AppTheme.textPrimary
                        )
                        isChecked = profile.enabled
                    }

                val hapticsSwitch =
                    MaterialSwitch(activity).apply {
                        text = "Native trigger haptics"
                        setTextColor(
                            AppTheme.textPrimary
                        )
                        isChecked =
                            profile.hapticsEnabled
                    }

                val savedTargetsSwitch =
                    MaterialSwitch(activity).apply {
                        text = "Show saved targets in game"
                        setTextColor(
                            AppTheme.textPrimary
                        )
                        isChecked =
                            profile.showSavedTargets
                    }

                enabledSwitch.setOnCheckedChangeListener {
                        _,
                        checked ->
                    val current =
                        NativeTgkStorage.getProfile(
                            activity,
                            profile.packageName
                        ) ?: return@setOnCheckedChangeListener

                    NativeTgkStorage.saveProfile(
                        activity,
                        current.copy(enabled = checked)
                    )

                    if (!checked) {
                        disableActiveProfileIfNeeded(
                            profile.packageName
                        )
                    }
                }

                hapticsSwitch.setOnCheckedChangeListener {
                        _,
                        checked ->
                    val current =
                        NativeTgkStorage.getProfile(
                            activity,
                            profile.packageName
                        ) ?: return@setOnCheckedChangeListener

                    NativeTgkStorage.saveProfile(
                        activity,
                        current.copy(
                            hapticsEnabled = checked
                        )
                    )
                }

                savedTargetsSwitch
                    .setOnCheckedChangeListener {
                            _,
                            checked ->
                        val current =
                            NativeTgkStorage.getProfile(
                                activity,
                                profile.packageName
                            ) ?: return@setOnCheckedChangeListener

                        NativeTgkStorage.saveProfile(
                            activity,
                            current.copy(
                                showSavedTargets = checked
                            )
                        )

                        if (!checked) {
                            NativeTgkGameplayOverlay.hide()
                        }
                    }

                card.addView(enabledSwitch)
                card.addView(hapticsSwitch)
                card.addView(savedTargetsSwitch)

                val optionsRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        0,
                        dp(activity, 8),
                        0,
                        0
                    )
                }

                val opacityButton = actionButton(
                    activity,
                    "TARGETS ${profile.savedTargetOpacityPercent}%",
                    primary = false
                ).apply {
                    setOnClickListener {
                        val values = intArrayOf(
                            8,
                            12,
                            18,
                            25
                        )
                        val labels = values.map {
                            "$it%"
                        }.toTypedArray()
                        var selected = values.indexOf(
                            profile.savedTargetOpacityPercent
                        ).coerceAtLeast(0)

                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Saved target opacity")
                            .setSingleChoiceItems(
                                labels,
                                selected
                            ) { _, which ->
                                selected = which
                            }
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save") { _, _ ->
                                val current =
                                    NativeTgkStorage.getProfile(
                                        activity,
                                        profile.packageName
                                    )
                                        ?: return@setPositiveButton

                                NativeTgkStorage.saveProfile(
                                    activity,
                                    current.copy(
                                        savedTargetOpacityPercent =
                                            values[selected]
                                    )
                                )
                                renderProfiles()
                            }
                            .show()
                    }
                }

                fun behaviorLabel(
                    side: String,
                    behavior: NativeTgkTriggerBehavior,
                    count: Int
                ): String {
                    return when (behavior) {
                        NativeTgkTriggerBehavior.SINGLE_TOUCH ->
                            "$side SINGLE"
                        NativeTgkTriggerBehavior.LONG_PRESS ->
                            "$side LONG PRESS"
                        NativeTgkTriggerBehavior.RAPID_FIRE ->
                            "$side RAPID ×$count"
                    }
                }

                fun behaviorButton(
                    side: String,
                    behavior: NativeTgkTriggerBehavior,
                    count: Int,
                    left: Boolean
                ): MaterialButton {
                    return actionButton(
                        activity,
                        behaviorLabel(side, behavior, count),
                        primary = false
                    ).apply {
                        setOnClickListener {
                            val behaviors = arrayOf(
                                NativeTgkTriggerBehavior.SINGLE_TOUCH,
                                NativeTgkTriggerBehavior.LONG_PRESS,
                                NativeTgkTriggerBehavior.RAPID_FIRE,
                                NativeTgkTriggerBehavior.RAPID_FIRE,
                                NativeTgkTriggerBehavior.RAPID_FIRE
                            )
                            val counts = intArrayOf(
                                0,
                                0,
                                2,
                                5,
                                10
                            )
                            val labels = arrayOf(
                                "Single touch",
                                "Long press",
                                "Rapid fire ×2",
                                "Rapid fire ×5",
                                "Rapid fire ×10"
                            )
                            var selected = behaviors.indices
                                .firstOrNull {
                                    behaviors[it] == behavior &&
                                        counts[it] == count
                                } ?: 0

                            MaterialAlertDialogBuilder(activity)
                                .setTitle("$side trigger behavior")
                                .setSingleChoiceItems(
                                    labels,
                                    selected
                                ) { _, which ->
                                    selected = which
                                }
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Save") { _, _ ->
                                    val current =
                                        NativeTgkStorage.getProfile(
                                            activity,
                                            profile.packageName
                                        )
                                            ?: return@setPositiveButton

                                    val updated = current
                                        .withTriggerBehavior(
                                            left = left,
                                            behavior =
                                                behaviors[selected],
                                            count = counts[selected]
                                        )

                                    NativeTgkStorage.saveProfile(
                                        activity,
                                        updated
                                    )
                                    renderProfiles()
                                }
                                .show()
                        }
                    }
                }

                val leftRapidButton = behaviorButton(
                    "L",
                    profile.effectiveLeftBehavior(),
                    profile.effectiveLeftRapidFireCount(),
                    true
                )
                val rightRapidButton = behaviorButton(
                    "R",
                    profile.effectiveRightBehavior(),
                    profile.effectiveRightRapidFireCount(),
                    false
                )

                listOf(
                    opacityButton,
                    leftRapidButton,
                    rightRapidButton
                ).forEachIndexed { optionIndex, button ->
                    if (optionIndex > 0) {
                        optionsRow.addView(
                            View(activity),
                            LinearLayout.LayoutParams(
                                dp(activity, 6),
                                1
                            )
                        )
                    }

                    optionsRow.addView(
                        button,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                }

                card.addView(optionsRow)

                val editRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        0,
                        dp(activity, 8),
                        0,
                        0
                    )
                }

                val landscapeButton = actionButton(
                    activity,
                    if (
                        profile.mappingFor(
                            NativeTgkOrientation.LANDSCAPE
                        )
                            ?.isComplete() == true
                    ) {
                        "EDIT LANDSCAPE"
                    } else {
                        "SET LANDSCAPE"
                    },
                    primary = false
                ).apply {
                    setOnClickListener {
                        showOrientationPicker(
                            profile.copy(
                                appLabel = profile.appLabel
                            )
                        )
                    }
                }

                val portraitButton = actionButton(
                    activity,
                    if (
                        profile.mappingFor(
                            NativeTgkOrientation.PORTRAIT
                        )
                            ?.isComplete() == true
                    ) {
                        "EDIT PORTRAIT"
                    } else {
                        "SET PORTRAIT"
                    },
                    primary = false
                ).apply {
                    setOnClickListener {
                        launchEditor(
                            profile,
                            NativeTgkOrientation.PORTRAIT
                        )
                    }
                }

                /*
                 * Landscape has its own direct handler because it
                 * is the common gaming orientation.
                 */
                landscapeButton.setOnClickListener {
                    launchEditor(
                        profile,
                        NativeTgkOrientation.LANDSCAPE
                    )
                }

                editRow.addView(
                    landscapeButton,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                editRow.addView(
                    View(activity),
                    LinearLayout.LayoutParams(
                        dp(activity, 8),
                        1
                    )
                )

                editRow.addView(
                    portraitButton,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                card.addView(editRow)

                val exportButton = actionButton(
                    activity,
                    "EXPORT APP",
                    primary = false
                ).apply {
                    setOnClickListener {
                        managerDialog.dismiss()
                        NativeTgkDocumentTransfer.requestExport(
                            activity,
                            profile.packageName,
                            profile.appLabel
                        )
                    }
                }

                card.addView(
                    exportButton,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(activity, 8)
                    }
                )

                val deleteButton = actionButton(
                    activity,
                    "REMOVE MAPPING",
                    primary = false
                ).apply {
                    setTextColor(
                        Color.rgb(255, 110, 110)
                    )
                    setOnClickListener {
                        MaterialAlertDialogBuilder(
                            activity
                        )
                            .setTitle(
                                "Remove ${profile.appLabel}?"
                            )
                            .setMessage(
                                "Both portrait and landscape " +
                                    "trigger targets will be deleted."
                            )
                            .setNegativeButton(
                                "Cancel",
                                null
                            )
                            .setPositiveButton(
                                "Remove"
                            ) { _, _ ->
                                disableActiveProfileIfNeeded(
                                    profile.packageName
                                )
                                NativeTgkStorage.removeProfile(
                                    activity,
                                    profile.packageName
                                )
                                renderProfiles()
                            }
                            .show()
                    }
                }

                card.addView(
                    deleteButton,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(activity, 8)
                    }
                )

                profilesContainer.addView(card)
            }
        }

        addButton.setOnClickListener {
            showAppPicker()
        }
        importButton.setOnClickListener {
            managerDialog.dismiss()
            NativeTgkDocumentTransfer.requestImport(activity)
        }
        exportAllButton.setOnClickListener {
            managerDialog.dismiss()
            NativeTgkDocumentTransfer.requestExport(activity)
        }

        renderProfiles()

        managerDialog = MaterialAlertDialogBuilder(activity)
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()

        managerDialog.show()
    }

    private fun actionButton(
        activity: Activity,
        label: String,
        primary: Boolean
    ): MaterialButton {
        return MaterialButton(
            activity,
            null,
            if (primary) {
                com.google.android.material.R.attr
                    .materialButtonStyle
            } else {
                com.google.android.material.R.attr
                    .materialButtonOutlinedStyle
            }
        ).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            minHeight = dp(activity, 46)
            cornerRadius = dp(activity, 14)
            insetTop = 0
            insetBottom = 0
            setTextColor(
                if (primary) {
                    Color.WHITE
                } else {
                    AppTheme.textPrimary
                }
            )
        }
    }

    private fun panelBackground(
        activity: Activity
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(
                activity,
                18
            ).toFloat()
            setColor(AppTheme.panelColor)
            setStroke(
                dp(activity, 1),
                AppTheme.borderColor
            )
        }
    }

    private fun dp(
        context: android.content.Context,
        value: Int
    ): Int {
        return (
            value *
                context.resources.displayMetrics.density
            ).toInt()
    }
}
