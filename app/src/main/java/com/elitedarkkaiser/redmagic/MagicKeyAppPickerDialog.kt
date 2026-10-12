package com.elitedarkkaiser.redmagic

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.elitedarkkaiser.redmagic.ui.AppTheme

internal object MagicKeyAppPickerDialog {
    private const val TAG = "MagicKeyAppPicker"

    data class Deps(
        val textPrimary: Int,
        val textSecondary: Int,
        val panelColor: Int,
        val borderColor: Int,
        val typeface: Typeface?,
        val dp: (Int) -> Int,
        val roundedBg: (Int, Int, Int) -> Drawable,
        val roundedFill: (Int, Int) -> Drawable,
        val space: (Int) -> View,
        val runBackground: (() -> Unit) -> Boolean
    )

    data class MagicKeyAppItem(
        val pkg: String,
        val label: String
    )

    fun show(
        activity: MainActivity,
        targetButton: Button,
        statusLabel: TextView?,
        applyLaunchAppMagicKeyMode:
            (String, String, TextView, Button) -> Unit,
        deps: Deps
    ) {
        val status = statusLabel

        if (status == null) {
            Toast.makeText(
                activity,
                "Magic Key controls are not ready",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        targetButton.isEnabled = false
        targetButton.text = "MAGIC KEY APP: Loading…"

        val submitted = deps.runBackground {
            val result = runCatching {
                loadLaunchableApps(activity)
            }

            activity.runOnUiThread {
                if (
                    activity.isFinishing ||
                    activity.isDestroyed
                ) {
                    return@runOnUiThread
                }

                restoreTargetButton(activity, targetButton)

                result.onSuccess { apps ->
                    if (apps.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "No launchable apps found",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@onSuccess
                    }

                    showSelectionDialog(
                        activity = activity,
                        apps = apps,
                        selectedPackage =
                            savedMagicKeyAppPackageStorage(
                                activity
                            ),
                        titleText = "Choose Magic Key App",
                        subtitleText =
                            "Pick one launchable app for the Magic Key",
                        onSelected = { selected ->
                            applyLaunchAppMagicKeyMode(
                                selected.pkg,
                                selected.label,
                                status,
                                targetButton
                            )
                        },
                        deps = deps
                    )
                }.onFailure { error ->
                    android.util.Log.e(
                        TAG,
                        "Unable to load launchable apps",
                        error
                    )

                    Toast.makeText(
                        activity,
                        "Unable to load installed apps",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        if (!submitted) {
            restoreTargetButton(activity, targetButton)
            Toast.makeText(
                activity,
                "Unable to start installed-app scan",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun chooseApp(
        activity: MainActivity,
        title: String,
        subtitle: String,
        selectedPackage: String?,
        deps: Deps,
        onSelected: (MagicKeyAppItem) -> Unit
    ) {
        val submitted = deps.runBackground {
            val result = runCatching {
                loadLaunchableApps(activity)
            }

            activity.runOnUiThread {
                if (
                    activity.isFinishing ||
                    activity.isDestroyed
                ) {
                    return@runOnUiThread
                }

                result.onSuccess { apps ->
                    if (apps.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "No launchable apps found",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showSelectionDialog(
                            activity = activity,
                            apps = apps,
                            selectedPackage = selectedPackage,
                            titleText = title,
                            subtitleText = subtitle,
                            onSelected = onSelected,
                            deps = deps
                        )
                    }
                }.onFailure { error ->
                    android.util.Log.e(
                        TAG,
                        "Unable to load launchable apps",
                        error
                    )
                    Toast.makeText(
                        activity,
                        "Unable to load installed apps",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        if (!submitted) {
            Toast.makeText(
                activity,
                "Unable to start installed-app scan",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun restoreTargetButton(
        activity: MainActivity,
        targetButton: Button
    ) {
        targetButton.isEnabled = true
        targetButton.text =
            "MAGIC KEY APP: " +
                MagicKeyActions.resolveAppLabel(
                    activity,
                    savedMagicKeyAppPackageStorage(
                        activity
                    )
                )
    }

    private fun loadLaunchableApps(
        activity: MainActivity
    ): List<MagicKeyAppItem> {
        val packageManager = activity.packageManager
        val launcherIntent = Intent(
            Intent.ACTION_MAIN
        ).addCategory(
            Intent.CATEGORY_LAUNCHER
        )

        @Suppress("DEPRECATION")
        return packageManager.queryIntentActivities(
            launcherIntent,
            0
        ).asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo
                    ?.packageName
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                if (
                    pkg == activity.packageName ||
                    info.activityInfo?.enabled == false
                ) {
                    return@mapNotNull null
                }

                val label = runCatching {
                    info.loadLabel(packageManager)
                        .toString()
                        .trim()
                }.getOrDefault(pkg)

                MagicKeyAppItem(
                    pkg = pkg,
                    label = label.ifBlank { pkg }
                )
            }
            .distinctBy { it.pkg }
            .sortedWith(
                compareBy<MagicKeyAppItem> {
                    it.label.lowercase()
                }.thenBy {
                    it.pkg.lowercase()
                }
            )
            .toList()
    }

    private fun showSelectionDialog(
        activity: MainActivity,
        apps: List<MagicKeyAppItem>,
        selectedPackage: String?,
        titleText: String,
        subtitleText: String,
        onSelected: (MagicKeyAppItem) -> Unit,
        deps: Deps
    ) {
        MaterialAppPicker.show(activity, titleText,
            apps.map { MaterialAppPicker.App(it.pkg, it.label) }, selectedPackage,
            subtitleText, confirmSelection = true) { picked ->
            apps.firstOrNull { it.pkg == picked.packageName }?.let(onSelected)
        }
    }
}
