package exh.util

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
 * true when [currentRating] no longer matches what [entry]'s own action produced (i.e. something
 * else changed the manga after this journal entry was recorded) -- restoring in that case would
 * silently overwrite a newer change, which this feature must never do.
 *
 * KMK v0.8.21-fix3: R1 correction -- rating is the only axis now; the prior
 * `currentNotInterested`/`entry.newNotInterested` comparison is gone along with the second store
 * it compared against.
 */
fun evaluationUndoHasConflict(entry: EvaluationJournalEntry, currentRating: Int?): Boolean =
    currentRating != entry.newRating

/**
 * Restore logic for [EvaluationModeUndoJournal] entries. Every restore is a typed inverse of exactly
 * the fields a supported action changed -- this never performs a generic snapshot/rollback, and it
 * never touches a manga's title/cover/library/history/group state.
 *
 * Restore contract, per entry:
 * 1. Re-read the manga's *current* rating.
 * 2. If the current rating no longer equals [EvaluationJournalEntry.newRating] (something changed
 *    it after this journal entry was recorded), the entry is left unrestored and reported as a
 *    conflict -- never force-restored.
 * 3. Otherwise, write back [EvaluationJournalEntry.previousRating] through the exact same
 *    interactors ([SetMangaTaste]/[ClearMangaTaste]) a normal rating change uses.
 * 4. The entry is removed from the journal only after its restore succeeds.
 */
class EvaluationModeUndoService(
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

            if (evaluationUndoHasConflict(entry, currentRating)) {
                return EvaluationUndoItemResult.CONFLICT
            }

            val mangaId = if (EvaluationJournalEntry.FIELD_RATING in entry.changedFields && entry.previousRating != null) {
                entry.mangaId ?: currentTaste?.mangaId ?: return EvaluationUndoItemResult.MISSING
            } else {
                null
            }
            val previousRating = entry.previousRating?.let(MangaRating::fromValue)
                ?: if (entry.previousRating == null) null else return EvaluationUndoItemResult.FAILED

            try {
                if (EvaluationJournalEntry.FIELD_RATING in entry.changedFields) {
                    if (previousRating == null) {
                        clearMangaTaste.await(entry.source, entry.url)
                    } else {
                        setMangaTaste.await(
                            mangaId = mangaId!!,
                            source = entry.source,
                            url = entry.url,
                            title = currentTaste?.title.orEmpty(),
                            rating = previousRating,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return EvaluationUndoItemResult.FAILED
            }
            EvaluationUndoItemResult.RESTORED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EvaluationUndoItemResult.FAILED
        }
    }
}
// KMK <--
