package exh.util

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.19 -->
/**
 * Shared recorder for the taste-action entry family. The existing screens (For You, Loved, Liked,
 * Disliked, and manga detail) funnel through the supported taste boundaries; broader Action History
 * families use sibling recorders and must follow the same contract.
 *
 * The `build*` functions read pre-action state and return not-yet-committed [EvaluationJournalEntry]
 * lists; they perform no journal writes. Callers must call [commit] with the built entries only AFTER
 * their write succeeds, so a failed write can never leave a stale/orphaned undo entry behind. Call the
 * `build*` function BEFORE the write (to capture pre-action state), then `commit` its result AFTER the
 * write succeeds -- never before.
 *
 * Records the same bounded, local-only inverse for ordinary users and Evaluation Mode. The journal's
 * historical `EvaluationMode` name remains for compatibility, but Action History is now a normal
 * user-facing destination. Developer-only diagnostic details are still gated separately by
 * [DeveloperOptionsGatePolicy], and nothing here is persisted, backed up, synced, or exported.
 *
 * KMK v0.8.21-fix3: R1 correction -- Not Interested is a real [tachiyomi.domain.taste.model.
 * MangaRating] value (`MangaTaste.rating`) now, not a second axis backed by a separate preference
 * key-set. Every entry this recorder builds therefore carries exactly one rating-family
 * before/after pair ([EvaluationJournalEntry.previousRating]/[newRating]); the previous
 * `previousNotInterested`/`newNotInterested` fields and the builders that existed only to keep two
 * stores in sync ([buildNotInterested], [buildNotInterestedRemoval],
 * [buildRatingChangeReplacingNotInterested], and friends) are gone, because there is no longer a
 * second store to keep in sync with.
 */
object EvaluationModeJournalRecorder {

    /** Commits previously-built entries into the journal. Call only after the corresponding write succeeded. */
    fun commit(entries: List<EvaluationJournalEntry>) {
        entries.forEach { EvaluationModeUndoJournal.record(it) }
    }

    /**
     * Builds a rating-set/clear journal entry for [manga] ahead of the caller writing [newRating]
     * (null = clear). [newRating] may be any [tachiyomi.domain.taste.model.MangaRating.value],
     * including [tachiyomi.domain.taste.model.MangaRating.NOT_INTERESTED.value] -- it is just
     * another rating from this recorder's point of view.
     */
    suspend fun buildRatingChange(
        getMangaTaste: GetMangaTaste,
        manga: List<Manga>,
        newRating: Int?,
        actionType: EvaluationJournalActionType,
    ): List<EvaluationJournalEntry> {
        if (manga.isEmpty()) return emptyList()
        val bulkId = if (manga.size > 1) EvaluationJournalEntry.newBulkId() else null
        return manga.map { m ->
            val previousTaste = getMangaTaste.await(m.source, m.url)
            EvaluationJournalEntry(
                id = EvaluationJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = actionType,
                mangaId = m.id,
                source = m.source,
                url = m.url,
                previousRating = previousTaste?.rating,
                newRating = newRating,
                isBulk = manga.size > 1,
                bulkOperationId = bulkId,
                changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
            )
        }
    }

    /**
     * Same as [buildRatingChange] but for callers (Loved/Liked/Disliked/Not Interested) that
     * already hold each item's current [MangaTaste] from screen state, avoiding a redundant
     * [GetMangaTaste] lookup.
     */
    fun buildRatingChangeFromTaste(
        previousTastes: List<MangaTaste>,
        newRating: Int?,
        actionType: EvaluationJournalActionType,
    ): List<EvaluationJournalEntry> {
        if (previousTastes.isEmpty()) return emptyList()
        val bulkId = if (previousTastes.size > 1) EvaluationJournalEntry.newBulkId() else null
        return previousTastes.map { taste ->
            EvaluationJournalEntry(
                id = EvaluationJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = actionType,
                mangaId = taste.mangaId,
                source = taste.source,
                url = taste.url,
                previousRating = taste.rating,
                newRating = newRating,
                isBulk = previousTastes.size > 1,
                bulkOperationId = bulkId,
                changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
            )
        }
    }
}
// KMK <--
