package exh.recs.evaluation

import eu.kanade.domain.base.BasePreferences

// KMK -->
/**
 * Pure helper that determines whether a requested evaluation installer mode is usable
 * and what batch sizes are allowed. No Android dependencies that block unit testing.
 *
 * Clarification: "stop using Shizuku after evaluation" means Komikku stops using Shizuku as its
 * installer mode for the run. It does NOT mean stopping the Shizuku app/service or revoking
 * Shizuku permission — those belong to Android, not Komikku.
 *
 * The evaluation always uses a temporary [installerOverride] passed to
 * [ExtensionManager.installExtension]. The user's global extension installer preference is never
 * mutated by the evaluation runner (Option A from the plan).
 */
object SourceEvaluationInstallerPolicy {

    enum class InstallerMode { CURRENT, PRIVATE, SHIZUKU }

    enum class InstallerReadiness {
        /** Ready to use for evaluation. */
        READY,
        /** Available in principle but permission needs to be granted first. */
        NEEDS_PERMISSION,
        /** Not installed or not running. */
        UNAVAILABLE,
        /** Installer mode is system-prompt-heavy; prefer smaller batches. */
        PROMPT_HEAVY,
    }

    /** Typed key for user-visible policy messages. Resolved to localized strings in the UI layer. */
    enum class InstallerPolicyMessage {
        PRIVATE_READY,
        PRIVATE_UNAVAILABLE,
        SHIZUKU_READY,
        SHIZUKU_NOT_INSTALLED,
        SHIZUKU_NOT_RUNNING,
        SHIZUKU_NEEDS_PERMISSION,
        CURRENT_SHIZUKU_UNAVAILABLE,
        CURRENT_PROMPT_HEAVY,
    }

    data class PolicyResult(
        val readiness: InstallerReadiness,
        val maxRecommendedBatchSize: Int,
        val requiresPromptWarning: Boolean,
        val cleanupIsSilent: Boolean,
        val messageKey: InstallerPolicyMessage?,
    )

    /**
     * Validate a requested evaluation installer mode.
     *
     * @param mode requested mode
     * @param currentGlobalInstaller the user's current global preference
     * @param privateAvailable whether PRIVATE is in the available entries
     * @param shizukuInstalled whether Shizuku app is installed
     * @param shizukuBinderAlive whether Shizuku binder responds to ping
     * @param shizukuPermissionGranted whether Komikku has Shizuku permission
     */
    fun validate(
        mode: InstallerMode,
        currentGlobalInstaller: BasePreferences.ExtensionInstaller,
        privateAvailable: Boolean,
        shizukuInstalled: Boolean,
        shizukuBinderAlive: Boolean,
        shizukuPermissionGranted: Boolean,
    ): PolicyResult {
        return when (mode) {
            InstallerMode.PRIVATE -> {
                if (!privateAvailable) {
                    PolicyResult(
                        readiness = InstallerReadiness.UNAVAILABLE,
                        maxRecommendedBatchSize = 0,
                        requiresPromptWarning = false,
                        cleanupIsSilent = false,
                        messageKey = InstallerPolicyMessage.PRIVATE_UNAVAILABLE,
                    )
                } else {
                    PolicyResult(
                        readiness = InstallerReadiness.READY,
                        maxRecommendedBatchSize = 100,
                        requiresPromptWarning = false,
                        cleanupIsSilent = true,
                        // KMK --> v0.6.13: describe Private as the recommended mode for evaluation
                        messageKey = InstallerPolicyMessage.PRIVATE_READY,
                        // KMK <--
                    )
                }
            }
            InstallerMode.SHIZUKU -> {
                when {
                    !shizukuInstalled -> PolicyResult(
                        readiness = InstallerReadiness.UNAVAILABLE,
                        maxRecommendedBatchSize = 0,
                        requiresPromptWarning = false,
                        cleanupIsSilent = false,
                        messageKey = InstallerPolicyMessage.SHIZUKU_NOT_INSTALLED,
                    )
                    !shizukuBinderAlive -> PolicyResult(
                        readiness = InstallerReadiness.UNAVAILABLE,
                        maxRecommendedBatchSize = 0,
                        requiresPromptWarning = false,
                        cleanupIsSilent = false,
                        messageKey = InstallerPolicyMessage.SHIZUKU_NOT_RUNNING,
                    )
                    !shizukuPermissionGranted -> PolicyResult(
                        readiness = InstallerReadiness.NEEDS_PERMISSION,
                        maxRecommendedBatchSize = 0,
                        requiresPromptWarning = false,
                        cleanupIsSilent = false,
                        messageKey = InstallerPolicyMessage.SHIZUKU_NEEDS_PERMISSION,
                    )
                    // KMK --> v0.6.13: Shizuku installs as system package; cleanup requires Android prompt
                    else -> PolicyResult(
                        readiness = InstallerReadiness.READY,
                        maxRecommendedBatchSize = 50,
                        requiresPromptWarning = true,
                        cleanupIsSilent = false,
                        messageKey = InstallerPolicyMessage.SHIZUKU_READY,
                    )
                    // KMK <--
                }
            }
            InstallerMode.CURRENT -> {
                when (currentGlobalInstaller) {
                    BasePreferences.ExtensionInstaller.PRIVATE -> PolicyResult(
                        readiness = InstallerReadiness.READY,
                        maxRecommendedBatchSize = 100,
                        requiresPromptWarning = false,
                        cleanupIsSilent = true,
                        messageKey = null,
                    )
                    // KMK --> v0.6.13: Shizuku via CURRENT also installs as system package
                    BasePreferences.ExtensionInstaller.SHIZUKU -> PolicyResult(
                        readiness = if (shizukuInstalled && shizukuBinderAlive && shizukuPermissionGranted) {
                            InstallerReadiness.READY
                        } else {
                            InstallerReadiness.UNAVAILABLE
                        },
                        maxRecommendedBatchSize = if (shizukuInstalled && shizukuBinderAlive && shizukuPermissionGranted) 50 else 0,
                        requiresPromptWarning = shizukuInstalled && shizukuBinderAlive && shizukuPermissionGranted,
                        cleanupIsSilent = false,
                        messageKey = if (shizukuInstalled && shizukuBinderAlive && shizukuPermissionGranted) {
                            InstallerPolicyMessage.SHIZUKU_READY
                        } else {
                            InstallerPolicyMessage.CURRENT_SHIZUKU_UNAVAILABLE
                        },
                    )
                    // KMK <--
                    else -> PolicyResult(
                        readiness = InstallerReadiness.PROMPT_HEAVY,
                        maxRecommendedBatchSize = 10,
                        requiresPromptWarning = true,
                        cleanupIsSilent = false,
                        messageKey = InstallerPolicyMessage.CURRENT_PROMPT_HEAVY,
                    )
                }
            }
        }
    }

    /** Convert requested mode + policy to the override to pass to ExtensionManager. */
    fun effectiveInstallerOverride(
        mode: InstallerMode,
        currentGlobalInstaller: BasePreferences.ExtensionInstaller,
        privateAvailable: Boolean,
    ): BasePreferences.ExtensionInstaller? {
        return when (mode) {
            InstallerMode.PRIVATE -> if (privateAvailable) BasePreferences.ExtensionInstaller.PRIVATE else null
            InstallerMode.SHIZUKU -> BasePreferences.ExtensionInstaller.SHIZUKU
            InstallerMode.CURRENT -> null // no override; use global preference
        }
    }

    /**
     * Recommend a default mode given what is available.
     * Prefers PRIVATE > SHIZUKU > CURRENT.
     */
    fun recommendDefaultMode(
        privateAvailable: Boolean,
        shizukuInstalled: Boolean,
        shizukuBinderAlive: Boolean,
        shizukuPermissionGranted: Boolean,
    ): InstallerMode {
        if (privateAvailable) return InstallerMode.PRIVATE
        if (shizukuInstalled && shizukuBinderAlive && shizukuPermissionGranted) return InstallerMode.SHIZUKU
        return InstallerMode.CURRENT
    }
}
// KMK <--
