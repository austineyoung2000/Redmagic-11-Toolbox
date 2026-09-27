package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.widget.Toast

/**
 * Coordinates non-trigger actions exposed by the Hardware tab.
 *
 * Native TGK enable/disable behavior intentionally remains owned by
 * MainActivity and HardwareServiceActions.
 */
class MainHardwareTabActions(
    private val activity: Activity,
    private val runBackground: (() -> Unit) -> Boolean,
    private val onMasterProfileApplied: (MasterProfile) -> Unit
) {
    fun readChargeSeparation(
        onComplete: (ChargeSeparationResult) -> Unit
    ) {
        val submitted = runBackground {
            val result = ChargeSeparationController.read(activity)
            postIfAlive {
                onComplete(result)
            }
        }

        if (!submitted) {
            onComplete(
                ChargeSeparationResult(
                    success = false,
                    enabled = null,
                    backend = null,
                    message = "Unable to read charge separation"
                )
            )
        }
    }

    fun setChargeSeparation(
        enabled: Boolean,
        onComplete: (ChargeSeparationResult) -> Unit
    ) {
        val submitted = runBackground {
            val result = ChargeSeparationController.setEnabled(
                activity,
                enabled
            )
            postIfAlive {
                onComplete(result)
            }
        }

        if (!submitted) {
            onComplete(
                ChargeSeparationResult(
                    success = false,
                    enabled = null,
                    backend = null,
                    message = "Unable to apply charge separation"
                )
            )
        }
    }

    fun loadMasterProfiles(
        onComplete: (List<MasterProfile>) -> Unit
    ) {
        val submitted = runBackground {
            val profiles = MasterProfileStorage.loadProfiles(activity)
            postIfAlive { onComplete(profiles) }
        }

        if (!submitted) {
            onComplete(emptyList())
        }
    }

    fun saveMasterProfile(
        name: String,
        onComplete: (Boolean) -> Unit
    ) {
        val submitted = runBackground {
            val saved = runCatching {
                MasterProfileActions.captureAndSave(
                    activity,
                    name
                )
                true
            }.getOrDefault(false)

            postIfAlive {
                showShortToast(
                    if (saved) {
                        "Saved $name"
                    } else {
                        "Failed to save $name"
                    }
                )
                onComplete(saved)
            }
        }

        if (!submitted) {
            showShortToast(
                "Unable to start master-profile capture"
            )
            onComplete(false)
        }
    }

    fun applyMasterProfile(profile: MasterProfile) {
        val submitted = runBackground {
            val applied = runCatching {
                MasterProfileActions.applyProfile(
                    activity,
                    profile
                )
                MasterProfileStorage.markProfileApplied(
                    activity,
                    profile.name
                )
                true
            }.getOrDefault(false)

            postIfAlive {
                if (applied) {
                    onMasterProfileApplied(profile)
                }

                showShortToast(
                    if (applied) {
                        "Applied ${profile.name}"
                    } else {
                        "Failed to apply ${profile.name}"
                    }
                )
            }
        }

        if (!submitted) {
            showShortToast(
                "Unable to start master-profile application"
            )
        }
    }

    fun deleteMasterProfile(
        name: String,
        onComplete: () -> Unit
    ) {
        val submitted = runBackground {
            val deleted = runCatching {
                MasterProfileStorage.deleteProfile(activity, name)
                true
            }.getOrDefault(false)

            postIfAlive {
                showShortToast(
                    if (deleted) "Deleted $name"
                    else "Failed to delete $name"
                )
                if (deleted) onComplete()
            }
        }

        if (!submitted) {
            showShortToast("Unable to start profile deletion")
        }
    }

    fun requestMasterBackupExport() {
        MasterProfileDocumentTransfer.requestExport(activity)
    }

    fun requestMasterBackupImport() {
        MasterProfileDocumentTransfer.requestImport(activity)
    }

    private fun postIfAlive(action: () -> Unit) {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                action()
            }
        }
    }

    private fun showShortToast(message: String) {
        Toast.makeText(
            activity,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }
}
