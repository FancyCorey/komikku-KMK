package exh.util

import kotlinx.coroutines.CancellationException

// KMK -->
/**
 * Restore logic for [PreferenceUndoJournal] entries. Generic over the entry's own value type `T`, so
 * one service covers every preference/small-row family (Phase 1's recommendation/tag/schedule
 * preferences, Phase 3's source-quality marks) without a per-key restore implementation.
 *
 * Restore contract, identical in shape to [EvaluationModeUndoService]/[GroupUndoService]:
 * 1. Re-read the current value via the entry's own [PreferenceUndoEntry.readCurrent].
 * 2. If it no longer equals [PreferenceUndoEntry.expectedPostValue], refuse and report a conflict.
 * 3. Otherwise call [PreferenceUndoEntry.restore] with the entry's [PreferenceUndoEntry.previousValue].
 * 4. Remove the entry from the journal only after a successful restore.
 */
class PreferenceUndoService {
    suspend fun undo(entryId: String): GroupUndoResult {
        val entry = PreferenceUndoJournal.snapshot().find { it.id == entryId } ?: return GroupUndoResult.FAILED
        return undoEntry(entry)
    }

    private suspend fun <T> undoEntry(entry: PreferenceUndoEntry<T>): GroupUndoResult {
        if (!entry.reversible) return GroupUndoResult.CONFLICT
        return try {
            val current = entry.readCurrent()
            if (current != entry.expectedPostValue) {
                return GroupUndoResult.CONFLICT
            }
            entry.restore(entry.previousValue)
            PreferenceUndoJournal.removeById(entry.id)
            GroupUndoResult.RESTORED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GroupUndoResult.FAILED
        }
    }
}
// KMK <--
