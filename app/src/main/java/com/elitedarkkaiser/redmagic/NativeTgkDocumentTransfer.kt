package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object NativeTgkDocumentTransfer {
    const val EXPORT_REQUEST = 8211
    const val IMPORT_REQUEST = 8212
    private const val PENDING_PREFS = "mapping_document_transfer"
    private const val PENDING_EXPORT_PACKAGE = "pending_export_package"
    private const val EXPORT_ALL = "__all_profiles__"

    fun requestExport(
        activity: Activity,
        packageName: String? = null,
        appLabel: String? = null
    ) {
        activity.getSharedPreferences(
            PENDING_PREFS,
            Activity.MODE_PRIVATE
        ).edit()
            .putString(
                PENDING_EXPORT_PACKAGE,
                packageName ?: EXPORT_ALL
            )
            .apply()

        val safeLabel = appLabel
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9._-]+"), "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
        val filename = if (packageName == null) {
            "redmagic-tgk-profiles.json"
        } else {
            "redmagic-tgk-${safeLabel ?: packageName}.json"
        }

        activity.startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, filename)
            },
            EXPORT_REQUEST
        )
    }

    fun requestImport(activity: Activity) {
        activity.startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            },
            IMPORT_REQUEST
        )
    }

    fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        runBackground: (() -> Unit) -> Boolean
    ): Boolean {
        if (
            requestCode != EXPORT_REQUEST &&
            requestCode != IMPORT_REQUEST
        ) {
            return false
        }

        if (resultCode != Activity.RESULT_OK) {
            if (requestCode == EXPORT_REQUEST) {
                clearExportRequest(activity)
            }
            return true
        }

        val uri = data?.data
        if (uri == null) {
            if (requestCode == EXPORT_REQUEST) {
                clearExportRequest(activity)
            }
            showToast(activity, "No document was selected")
            return true
        }

        if (requestCode == EXPORT_REQUEST) {
            handleExport(activity, uri, runBackground)
        } else {
            handleImport(activity, uri, runBackground)
        }

        return true
    }

    private fun handleExport(
        activity: Activity,
        uri: Uri,
        runBackground: (() -> Unit) -> Boolean
    ) {
        val pending = consumeExportRequest(activity)
        val packageName = pending?.takeUnless { it == EXPORT_ALL }

        if (pending == null) {
            showToast(activity, "TGK export request expired")
            return
        }

        val submitted = runBackground {
            val error = runCatching {
                val json = NativeTgkStorage.createExportJson(
                    activity.applicationContext,
                    packageName
                )
                activity.contentResolver.openOutputStream(
                    uri,
                    "wt"
                )?.bufferedWriter()?.use {
                    it.write(json)
                } ?: error("Unable to open export destination")
            }.exceptionOrNull()

            activity.runOnUiThread {
                if (activityAlive(activity)) {
                    showToast(
                        activity,
                        error?.message ?: "TGK profiles exported"
                    )
                }
            }
        }

        if (!submitted) {
            showToast(activity, "Unable to start profile export")
        }
    }

    private fun handleImport(
        activity: Activity,
        uri: Uri,
        runBackground: (() -> Unit) -> Boolean
    ) {
        val submitted = runBackground {
            val loaded = runCatching {
                val raw = activity.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Unable to read TGK profile file")
                val packages =
                    NativeTgkStorage.importedPackageNames(raw)
                val existing = NativeTgkStorage
                    .readProfiles(activity.applicationContext)
                    .map { it.packageName }
                    .toSet()

                ImportDocument(
                    raw = raw,
                    conflictCount = packages.intersect(existing).size
                )
            }

            activity.runOnUiThread {
                if (!activityAlive(activity)) return@runOnUiThread

                loaded.onSuccess { document ->
                    if (document.conflictCount == 0) {
                        importDocument(
                            activity,
                            document.raw,
                            replaceExisting = true,
                            runBackground = runBackground
                        )
                    } else {
                        showConflictDialog(
                            activity,
                            document,
                            runBackground
                        )
                    }
                }.onFailure {
                    showToast(
                        activity,
                        it.message ?: "TGK import failed"
                    )
                }
            }
        }

        if (!submitted) {
            showToast(activity, "Unable to start profile import")
        }
    }

    private fun showConflictDialog(
        activity: Activity,
        document: ImportDocument,
        runBackground: (() -> Unit) -> Boolean
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Existing TGK mappings found")
            .setMessage(
                "${document.conflictCount} imported app mapping(s) " +
                    "already exist. Replace them or keep the " +
                    "current versions?"
            )
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Keep existing") { _, _ ->
                importDocument(
                    activity,
                    document.raw,
                    replaceExisting = false,
                    runBackground = runBackground
                )
            }
            .setPositiveButton("Replace") { _, _ ->
                importDocument(
                    activity,
                    document.raw,
                    replaceExisting = true,
                    runBackground = runBackground
                )
            }
            .show()
    }

    private fun importDocument(
        activity: Activity,
        raw: String,
        replaceExisting: Boolean,
        runBackground: (() -> Unit) -> Boolean
    ) {
        val submitted = runBackground {
            val result = runCatching {
                NativeTgkRuntimeState.clear()
                NativeTgkCoordinator.disable(
                    activity.applicationContext,
                    "TGK profiles imported"
                )
                NativeTgkStorage.importProfilesJson(
                    context = activity.applicationContext,
                    raw = raw,
                    replaceExisting = replaceExisting
                )
            }

            activity.runOnUiThread {
                if (!activityAlive(activity)) return@runOnUiThread

                result.onSuccess {
                    showToast(
                        activity,
                        "Imported ${it.importedCount}, " +
                            "replaced ${it.replacedCount}, " +
                            "skipped ${it.skippedCount} TGK profile(s)"
                    )
                    NativeTgkProfileDialog.show(activity)
                }.onFailure {
                    showToast(
                        activity,
                        it.message ?: "TGK import failed"
                    )
                }
            }
        }

        if (!submitted) {
            showToast(activity, "Unable to apply imported profiles")
        }
    }

    @Synchronized
    private fun consumeExportRequest(activity: Activity): String? {
        val prefs = activity.getSharedPreferences(
            PENDING_PREFS,
            Activity.MODE_PRIVATE
        )
        val pending = prefs.getString(PENDING_EXPORT_PACKAGE, null)
        prefs.edit().remove(PENDING_EXPORT_PACKAGE).apply()
        return pending
    }

    @Synchronized
    private fun clearExportRequest(activity: Activity) {
        activity.getSharedPreferences(
            PENDING_PREFS,
            Activity.MODE_PRIVATE
        ).edit()
            .remove(PENDING_EXPORT_PACKAGE)
            .apply()
    }

    private fun activityAlive(activity: Activity): Boolean {
        return !activity.isFinishing && !activity.isDestroyed
    }

    private fun showToast(
        activity: Activity,
        message: String
    ) {
        Toast.makeText(
            activity,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private data class ImportDocument(
        val raw: String,
        val conflictCount: Int
    )
}
