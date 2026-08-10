package exh.recs.evaluation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import eu.kanade.tachiyomi.util.system.isShizukuInstalled
import logcat.LogPriority
import rikka.shizuku.Shizuku
import tachiyomi.core.common.util.system.logcat

// KMK -->
/**
 * Pure state query and shortcut helper for Shizuku setup in Source Evaluation.
 *
 * This helper detects Shizuku status and provides shortcuts to open/uninstall Shizuku.
 * It does NOT attempt to silently start, stop, install, or uninstall Shizuku — those
 * actions belong to Android and Shizuku themselves.
 */
object ShizukuSetupHelper {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download"

    data class State(
        val installed: Boolean,
        val binderAlive: Boolean,
        val permissionGranted: Boolean,
    )

    fun readState(context: Context): State {
        val installed = context.isShizukuInstalled

        val binderAlive = if (installed) {
            try {
                Shizuku.pingBinder()
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        val permissionGranted = if (installed && binderAlive) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        return State(
            installed = installed,
            binderAlive = binderAlive,
            permissionGranted = permissionGranted,
        )
    }

    /** Opens the official Shizuku download/setup page in the user's browser. */
    fun openDownload(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            logcat(LogPriority.WARN) { "Could not open Shizuku download page" }
        }
    }

    /**
     * Opens the Shizuku app if installed.
     * @return true if launched, false if not installed or no launch intent available.
     */
    fun openApp(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            logcat(LogPriority.WARN) { "Could not open Shizuku app" }
            false
        }
    }

    /**
     * Launches Android's standard uninstall confirmation for Shizuku.
     * This does NOT silently uninstall — Android shows a confirmation dialog.
     * @return true if the uninstall intent was dispatched.
     */
    fun openUninstall(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DELETE)
                .setData(Uri.parse("package:$SHIZUKU_PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            logcat(LogPriority.WARN) { "Could not launch Shizuku uninstall intent" }
            false
        }
    }

    /**
     * Returns the best InstallerMode fallback when Shizuku is no longer desired for this run.
     * Prefers PRIVATE when available, otherwise CURRENT.
     */
    fun stopUsingFallbackMode(privateAvailable: Boolean): SourceEvaluationInstallerPolicy.InstallerMode {
        return if (privateAvailable) {
            SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE
        } else {
            SourceEvaluationInstallerPolicy.InstallerMode.CURRENT
        }
    }
}
// KMK <--
