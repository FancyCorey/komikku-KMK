package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK v0.8.19 -->
/**
 * Pure conflict check, extracted for direct unit testing without a database:
 * true when [currentRating]/[currentNotInterested] no longer match what [entry]'s own action produced
 * (i.e. something else changed the manga after this journal entry was recorded) -- restoring in that
 * case would silently overwrite a newer change, which this feature must never do.
 */
fun evaluationUndoHasConflict(entry: EvaluationJournalEntry, currentRating: Int?, currentNotInterested: Boolean): Boolean =
    currentRating != entry.newRating || currentNotInterested != entry.newNotInterested

/**
 * Restore logic for [EvaluationModeUndoJournal] entries. Every restore is a typed inverse of exactly
 * the fields a supported action changed -- this never performs a generic snapshot/rollback, and it
 * never touches a manga's title/cover/library/history/group state.
 *
 * Restore contract, per entry:
 * 1. Re-read the manga's *current* rating and Not Interested state.
 * 2. If the current state no longer equals [EvaluationJournalEntry.newRating]/[newNotInterested]
 *    (something changed it after this journal entry was recorded), the entry is left unrestored and
 *    reported as a conflict -- never force-restored.
 * 3. Otherwise, write back [EvaluationJournalEntry.previousRating]/[previousNotInterested] through the
 *    exact same interactors ([SetMangaTaste]/[ClearMangaTaste]/`SeenRecommendationMangaStore`) a normal
 *    rating change uses.
 * 4. The entry is removed from the journal only after its restore succeeds.
 */
class EvaluationModeUndoService(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val setMangaTaste: SetMangaTaste = Injekt.get(),
    private val clearMangaTaste: ClearMangaTaste = Injekt.get(),
) {
    suspend fun undo(entryId: String): EvaluationUndoOutcome {
        val entry = EvaluationModeUndoJournal.snapshot().find { it.id == entryId }
            ?: return EvaluationUndoOutcome(0, 0, 0, 0, 0)
        return undoEntries(listOf(entry))
    }

    suspend fun undoBulk(bulkOperationId: String): EvaluationUndoOutcome {
        val entries = EvaluationModeUndoJournal.entriesForBulk(bulkOperationId)
        if (entries.isEmpty()) return EvaluationUndoOutcome(0, 0, 0, 0, 0)
        return undoEntries(entries)
    }

    private suspend fun undoEntries(targets: List<EvaluationJournalEntry>): EvaluationUndoOutcome {
        var restored = 0
        var conflict = 0
        var missing = 0
        var failed = 0
        for (entry in targets) {
            when (restoreOne(entry)) {
                EvaluationUndoItemResult.RESTORED -> {
                    restored++
                    EvaluationModeUndoJournal.removeById(entry.id)
                }
                EvaluationUndoItemResult.CONFLICT -> conflict++
                EvaluationUndoItemResult.MISSING -> missing++
                EvaluationUndoItemResult.FAILED -> failed++
            }
        }
        return EvaluationUndoOutcome(
            requestedCount = targets.size,
            restoredCount = restored,
            conflictCount = conflict,
            missingCount = missing,
            failedCount = failed,
        )
    }

    private suspend fun restoreOne(entry: EvaluationJournalEntry): EvaluationUndoItemResult {
        if (!entry.reversible) return EvaluationUndoItemResult.CONFLICT
        return try {
            val currentTaste = getMangaTaste.await(entry.source, entry.url)
            val currentRating = currentTaste?.rating
            val currentNotInterested = currentSeenState(entry.source, entry.url)

            if (evaluationUndoHasConflict(entry, currentRating, currentNotInterested)) {
                return EvaluationUndoItemResult.CONFLICT
            }

            if (EvaluationJournalEntry.FIELD_RATING in entry.changedFields) {
                if (entry.previousRating == null) {
                    clearMangaTaste.await(entry.source, entry.url)
                } else {
                    val mangaId = entry.mangaId ?: currentTaste?.mangaId
                    if (mangaId == null) return EvaluationUndoItemResult.MISSING
                    setMangaTaste.await(
                        mangaId = mangaId,
                        source = entry.source,
                        url = entry.url,
                        title = currentTaste?.title.orEmpty(),
                        rating = MangaRating.fromValue(entry.previousRating) ?: return EvaluationUndoItemResult.FAILED,
                    )
                }
            }
            if (EvaluationJournalEntry.FIELD_NOT_INTERESTED in entry.changedFields) {
                setSeenState(entry.source, entry.url, entry.previousNotInterested)
            }
            EvaluationUndoItemResult.RESTORED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EvaluationUndoItemResult.FAILED
        }
    }

    private fun currentSeenState(source: Long, url: String): Boolean {
        val raw = sourcePreferences.seenRecommendationMangaKeys().get()
        val seen = SeenRecommendationMangaStore.parse(raw)
        return SeenMangaKey(source, url) in seen
    }

    private fun setSeenState(source: Long, url: String, notInterested: Boolean) {
        val raw = sourcePreferences.seenRecommendationMangaKeys().get()
        var seen = SeenRecommendationMangaStore.parse(raw)
        val key = SeenMangaKey(source, url)
        seen = if (notInterested) SeenRecommendationMangaStore.add(seen, key) else SeenRecommendationMangaStore.remove(seen, key)
        sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(seen))
    }
}
// KMK <--
