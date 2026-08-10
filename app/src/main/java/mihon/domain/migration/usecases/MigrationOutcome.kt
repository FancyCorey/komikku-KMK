package mihon.domain.migration.usecases

import mihon.domain.migration.models.MigrationFlag

// KMK -->
/**
 * Truthful result of [MigrateMangaUseCase.invoke]. Confirmed defect this fixes: `invoke()`
 * previously returned `Unit` and wrapped its entire body (remote refresh, chapter/history copy,
 * category replacement, tracker migration, download deletion, cover copy, and the final
 * `updateManga.awaitAll(...)` call) in one `catch (e: Throwable) { rethrowIfFatal(e) }` block --
 * meaning any *non-fatal* exception partway through was silently swallowed and the caller
 * ([exh.recs.bestversion.BestVersionCompareScreenModel.confirmMigration]) had no way to distinguish
 * a genuine success from a silent partial failure. It unconditionally recorded a non-undoable
 * `MIGRATION_COMPLETED` history event and navigated to `Done` regardless, which is exactly the
 * defect where success was recorded before the underlying operation actually returned success.
 *
 * The follow-up pass extended [completedFlags] to also cover [MigrationFlag.NOTES] and
 * [MigrationFlag.EXTRA] (previously invisible -- they're applied as part of the final manga-row
 * update rather than their own flag-gated block, so they were never added to `completedFlags` at
 * all even on a full success), added [skippedFlags] to distinguish "requested but not applicable"
 * (e.g. [MigrationFlag.CUSTOM_COVER] requested when the source manga has no custom cover) from
 * "requested and actually ran", and added [PartialFailure.finalUpdateFailed] so a failure in the
 * last `updateManga.awaitAll(...)` call -- which is the step that actually commits favorite/
 * category-visible state -- is distinguishable from a failure in one of the earlier flag-gated
 * steps.
 *
 * This is deliberately NOT a reversal/undo contract -- see [MigrateMangaUseCase]'s class doc for why
 * local-state staged reversal and `reverseMigration` are explicitly out of scope the implementation. This
 * type only makes the existing (already-happened) side effects *truthfully reported*, so a caller
 * can show an honest error instead of a false success.
 */
sealed interface MigrationOutcome {
    /**
     * The final `updateManga.awaitAll(...)` call returned normally -- every requested flag's step
     * ran (or was truthfully skipped as not applicable). [completedFlags] and [skippedFlags]
     * together always equal [requestedFlags].
     */
    data class Success(
        val requestedFlags: Set<MigrationFlag>,
        val completedFlags: Set<MigrationFlag>,
        val skippedFlags: Set<MigrationFlag>,
    ) : MigrationOutcome

    /**
     * A non-fatal exception interrupted the sequence. [completedFlags] lists exactly which
     * flag-gated steps had already run (and therefore already produced real, uncommitted-nowhere-
     * else side effects -- e.g. downloaded files already deleted, a tracker already called) before
     * the failure; [skippedFlags] lists requested flags already determined not applicable before
     * the failure. [failedAt] is the pre-final-update step that was in progress when the exception
     * was thrown, or null if the failure happened before any flag-gated step began, or after all of
     * them finished (during the final manga-row update -- see [finalUpdateFailed]). None of these
     * side effects are undone by this type -- see [MigrateMangaUseCase]'s class doc.
     */
    data class PartialFailure(
        val requestedFlags: Set<MigrationFlag>,
        val completedFlags: Set<MigrationFlag>,
        val skippedFlags: Set<MigrationFlag>,
        val failedAt: MigrationFlag?,
        val finalUpdateFailed: Boolean,
        val cause: Throwable,
    ) : MigrationOutcome

    /** The migration could not start at all -- `target`/`current` source could not be resolved, or the initial remote refresh failed before any flag-gated step ran. */
    data class NotStarted(val cause: Throwable?) : MigrationOutcome

    companion object {
        /**
         * Pure decision extracted from [MigrateMangaUseCase.invoke]'s catch block, directly
         * unit-testable without constructing the use case's full platform-dependency graph
         * (`SourceManager`/`TrackerManager`/`DownloadManager`/`CoverCache`/`UpdateMangaFromRemote`).
         * A failure is [NotStarted] only when nothing had run yet and the final update never began;
         * any flag-gated step having started or completed, or the final update having begun, makes
         * it a [PartialFailure], even if the failure happened before that step's own work finished.
         */
        fun fromFailure(
            requestedFlags: Set<MigrationFlag>,
            completedFlags: Set<MigrationFlag>,
            skippedFlags: Set<MigrationFlag>,
            currentStep: MigrationFlag?,
            finalUpdateStarted: Boolean,
            cause: Throwable,
        ): MigrationOutcome =
            if (completedFlags.isEmpty() && skippedFlags.isEmpty() && currentStep == null && !finalUpdateStarted) {
                NotStarted(cause)
            } else {
                PartialFailure(
                    requestedFlags = requestedFlags,
                    completedFlags = completedFlags,
                    skippedFlags = skippedFlags,
                    failedAt = currentStep,
                    finalUpdateFailed = finalUpdateStarted,
                    cause = cause,
                )
            }
    }
}
// KMK <--
