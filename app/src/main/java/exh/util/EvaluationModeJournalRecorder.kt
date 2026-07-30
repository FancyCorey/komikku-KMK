package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
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
 * No-ops entirely (reads nothing, records nothing) when Evaluation Mode is disabled, per the feature's
 * explicit requirement that normal users receive no journal behavior and no extra database work.
 */
object EvaluationModeJournalRecorder {

    /** Commits previously-built entries into the journal. Call only after the corresponding write succeeded. */
    fun commit(entries: List<EvaluationJournalEntry>) {
        entries.forEach { EvaluationModeUndoJournal.record(it) }
    }

    /** Builds a rating-set/clear journal entry for [manga] ahead of the caller writing [newRating] (null = clear). */
    suspend fun buildRatingChange(
        sourcePreferences: SourcePreferences,
        getMangaTaste: GetMangaTaste,
        manga: List<Manga>,
        newRating: Int?,
        actionType: EvaluationJournalActionType,
    ): List<EvaluationJournalEntry> {
        if (!sourcePreferences.evaluationMode().get() || manga.isEmpty()) return emptyList()
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
                previousNotInterested = currentSeenState(sourcePreferences, m.source, m.url),
                newNotInterested = currentSeenState(sourcePreferences, m.source, m.url),
                isBulk = manga.size > 1,
                bulkOperationId = bulkId,
                changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
            )
        }
    }

    /** Builds a Not-Interested journal entry for [manga] ahead of the caller setting the "seen" store. */
    suspend fun buildNotInterested(
        sourcePreferences: SourcePreferences,
        getMangaTaste: GetMangaTaste,
        manga: List<Manga>,
    ): List<EvaluationJournalEntry> {
        if (!sourcePreferences.evaluationMode().get() || manga.isEmpty()) return emptyList()
        val bulkId = if (manga.size > 1) EvaluationJournalEntry.newBulkId() else null
        return manga.map { m ->
            val previousTaste = getMangaTaste.await(m.source, m.url)
            EvaluationJournalEntry(
                id = EvaluationJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = EvaluationJournalActionType.NOT_INTERESTED,
                mangaId = m.id,
                source = m.source,
                url = m.url,
                previousRating = previousTaste?.rating,
                newRating = previousTaste?.rating,
                previousNotInterested = currentSeenState(sourcePreferences, m.source, m.url),
                newNotInterested = true,
                isBulk = manga.size > 1,
                bulkOperationId = bulkId,
                changedFields = setOf(EvaluationJournalEntry.FIELD_NOT_INTERESTED),
            )
        }
    }

    /**
     * Same as [buildRatingChange] but for callers (Loved/Liked/Disliked) that already hold each
     * item's current [MangaTaste] from screen state, avoiding a redundant [GetMangaTaste] lookup.
     */
    fun buildRatingChangeFromTaste(
        sourcePreferences: SourcePreferences,
        previousTastes: List<MangaTaste>,
        newRating: Int?,
        actionType: EvaluationJournalActionType,
    ): List<EvaluationJournalEntry> {
        if (!sourcePreferences.evaluationMode().get() || previousTastes.isEmpty()) return emptyList()
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
                previousNotInterested = currentSeenState(sourcePreferences, taste.source, taste.url),
                newNotInterested = currentSeenState(sourcePreferences, taste.source, taste.url),
                isBulk = previousTastes.size > 1,
                bulkOperationId = bulkId,
                changedFields = setOf(EvaluationJournalEntry.FIELD_RATING),
            )
        }
    }

    /** Not-Interested variant of [buildRatingChangeFromTaste]. */
    fun buildNotInterestedFromTaste(
        sourcePreferences: SourcePreferences,
        previousTastes: List<MangaTaste?>,
        keys: List<Pair<Long, String>>,
    ): List<EvaluationJournalEntry> {
        if (!sourcePreferences.evaluationMode().get() || keys.isEmpty()) return emptyList()
        val bulkId = if (keys.size > 1) EvaluationJournalEntry.newBulkId() else null
        return keys.mapIndexed { index, (source, url) ->
            val taste = previousTastes.getOrNull(index)
            EvaluationJournalEntry(
                id = EvaluationJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = EvaluationJournalActionType.NOT_INTERESTED,
                mangaId = taste?.mangaId,
                source = source,
                url = url,
                previousRating = taste?.rating,
                newRating = taste?.rating,
                previousNotInterested = currentSeenState(sourcePreferences, source, url),
                newNotInterested = true,
                isBulk = keys.size > 1,
                bulkOperationId = bulkId,
                changedFields = setOf(EvaluationJournalEntry.FIELD_NOT_INTERESTED),
            )
        }
    }

    private fun currentSeenState(sourcePreferences: SourcePreferences, source: Long, url: String): Boolean {
        val raw = sourcePreferences.seenRecommendationMangaKeys().get()
        return SeenMangaKey(source, url) in SeenRecommendationMangaStore.parse(raw)
    }
}
// KMK <--
