package mihon.feature.migration.list

import mihon.domain.migration.usecases.MigrationOutcome

// KMK -->
/**
 * Maps a [MigrationOutcome] to the migration-list-specific decision of whether the attempted item
 * may be removed from the pending list. Extracted as a small pure function so
 * [MigrationListScreenModel]'s bulk loop
 * (`migrateMangas`) and single-item flow (`migrateNow`) share one decision and cannot drift --
 * `migrateNow()` must retain a non-Success item rather than merely logging the outcome and removing
 * it, because logging is not user-facing failure handling.
 *
 * [MigrationListItemState.SKIPPED] is not derived from a [MigrationOutcome] at all -- it represents
 * an item with no successful search result yet, so no migration was ever attempted for it. It exists
 * here (not only as a UI-layer concept) so the same enum can represent "why is this item still in
 * the list" everywhere a caller might ask.
 */
enum class MigrationListItemState {
    /** [MigrationOutcome.Success] -- the item may be removed from the pending list. */
    SUCCESS,

    /**
     * [MigrationOutcome.PartialFailure] -- one or more side effects may already have happened
     * (see [MigrationOutcome.PartialFailure.completedFlags]), but the migration did not finish.
     * The item must remain in the list, retryable via the same "Migrate now"/"Copy now" actions or
     * explicitly dismissible via the existing Skip action.
     */
    RETRYABLE_FAILURE,

    /** [MigrationOutcome.NotStarted] -- nothing happened; retryable the same way as above. */
    NOT_STARTED,

    /** No successful search result exists yet for this item -- migration was never attempted. */
    SKIPPED,
    ;

    val isFailure: Boolean get() = this == RETRYABLE_FAILURE || this == NOT_STARTED
}

object MigrationOutcomeReducer {
    fun reduce(outcome: MigrationOutcome): MigrationListItemState = when (outcome) {
        is MigrationOutcome.Success -> MigrationListItemState.SUCCESS
        is MigrationOutcome.PartialFailure -> MigrationListItemState.RETRYABLE_FAILURE
        is MigrationOutcome.NotStarted -> MigrationListItemState.NOT_STARTED
    }
}
// KMK <--
