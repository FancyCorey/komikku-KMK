package exh.recs.evaluation

// KMK -->
/**
 * Pure, Android-free helper for deciding how to clean up a temporarily-installed extension
 * after Source Evaluation. All decision logic is here so it can be tested without Android.
 */
object SourceEvaluationCleanupPolicy {

    /**
     * Outcome of [cleanupDecision].
     *
     * - [RemovePrivateSilently]: extension was installed privately (isShared=false); delete the
     *   private file. [ExtensionInstaller.uninstallApk] takes the silent path when
     *   `context.isPackageInstalled(pkgName)` is false (private-only extensions are not
     *   registered with Android's package manager).
     * - [SkipPreExisting]: extension was already installed before evaluation; do not touch it.
     * - [PromptRequired]: extension is system-installed (isShared=true); cleanup would trigger
     *   an Android uninstall dialog. [SourceEvaluationRunner] decides whether to proceed based
     *   on [SourceEvaluationOptions.promptHeavyCleanupAllowed].
     * - [NotNeeded]: extension was not found in installedExtensionsFlow after evaluation —
     *   install failed, or it was already cleaned up.
     */
    enum class CleanupDecision {
        RemovePrivateSilently,
        SkipPreExisting,
        PromptRequired,
        NotNeeded,
    }

    /**
     * Decide how to handle cleanup for a temporarily-installed evaluation extension.
     *
     * @param preExistingInstalled true if the extension was already installed BEFORE evaluation started.
     * @param installedAfterEvaluation true if the extension appears in installedExtensionsFlow after evaluation.
     * @param isShared true if the installed extension is a system/shared package (Extension.Installed.isShared).
     */
    fun cleanupDecision(
        preExistingInstalled: Boolean,
        installedAfterEvaluation: Boolean,
        isShared: Boolean,
    ): CleanupDecision {
        if (!installedAfterEvaluation) return CleanupDecision.NotNeeded
        if (preExistingInstalled) return CleanupDecision.SkipPreExisting
        return if (isShared) CleanupDecision.PromptRequired else CleanupDecision.RemovePrivateSilently
    }
}
// KMK <--
