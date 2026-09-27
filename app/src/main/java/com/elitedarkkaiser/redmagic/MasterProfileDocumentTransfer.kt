package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MasterProfileDocumentTransfer {
    private const val EXPORT_REQUEST = 8201
    private const val IMPORT_REQUEST = 8202

    fun requestExport(activity: Activity) {
        activity.startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(
                    Intent.EXTRA_TITLE,
                    "redmagic-11-toolbox-backup.json"
                )
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
            return true
        }

        val uri = data?.data ?: return true
        val submitted = runBackground {
            if (requestCode == EXPORT_REQUEST) {
                exportBackup(activity, uri)
            } else {
                importBackup(activity, uri)
            }
        }

        if (!submitted) {
            showToast(
                activity,
                "Unable to start master-backup transfer"
            )
        }

        return true
    }

    private fun exportBackup(
        activity: Activity,
        uri: Uri
    ) {
        val error = runCatching {
            val json = MasterProfileStorage
                .createBackupJson(activity)
            activity.contentResolver.openOutputStream(
                uri,
                "wt"
            )?.bufferedWriter()?.use {
                it.write(json)
            } ?: error("Unable to open backup destination")
        }.exceptionOrNull()

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                return@runOnUiThread
            }

            showToast(
                activity,
                error?.message ?: "Master backup exported"
            )
        }
    }

    private fun importBackup(
        activity: Activity,
        uri: Uri
    ) {
        val result = runCatching {
            val raw = activity.contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Unable to read backup")
            MasterProfileStorage.importBackup(
                activity,
                raw
            )
        }

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                return@runOnUiThread
            }

            result.onSuccess { imported ->
                showToast(
                    activity,
                    "Imported ${imported.savedProfileCount} " +
                        "saved profiles and restored settings"
                )
                activity.recreate()
            }.onFailure { error ->
                showToast(
                    activity,
                    error.message ?: "Backup import failed"
                )
            }
        }
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
}
