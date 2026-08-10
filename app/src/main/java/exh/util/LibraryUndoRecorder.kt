package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.domain.manga.model.Manga

// KMK -->
/**
 * Builds not-yet-committed [LibraryJournalEntry] snapshots for favorite/unfavorite and category-set
 * mutations. Mirrors [EvaluationModeJournalRecorder]/[GroupUndoRecorder]'s build-before-write /
 * commit-after-success contract exactly: callers must call [LibraryUndoJournal.record] themselves, and
 * only after their write has been confirmed to have taken effect.
 *
 * No-ops (returns `null`) when Evaluation Mode is disabled.
 */
object LibraryUndoRecorder {

    /** @param previousManga the manga row read BEFORE the favorite write. */
    fun buildFavoriteEntry(
        sourcePreferences: SourcePreferences,
        previousManga: Manga,
        newFavorite: Boolean,
    ): LibraryJournalEntry? {
        if (!sourcePreferences.evaluationMode().get()) return null
        return LibraryJournalEntry(
            id = LibraryJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = if (newFavorite) LibraryJournalActionType.FAVORITE else LibraryJournalActionType.UNFAVORITE,
            mangaId = previousManga.id,
            previousFavorite = previousManga.favorite,
            previousDateAdded = previousManga.dateAdded,
            expectedPostFavorite = newFavorite,
            previousCategoryIds = null,
            expectedPostCategoryIds = null,
        )
    }

    /** Bulk variant: builds one entry per manga that will actually be journaled after a successful write. */
    fun buildFavoriteEntries(
        sourcePreferences: SourcePreferences,
        previousMangas: List<Manga>,
        newFavorite: Boolean,
    ): List<LibraryJournalEntry> {
        if (!sourcePreferences.evaluationMode().get() || previousMangas.isEmpty()) return emptyList()
        val bulkId = if (previousMangas.size > 1) LibraryJournalEntry.newBulkId() else null
        return previousMangas.map { manga ->
            LibraryJournalEntry(
                id = LibraryJournalEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = if (newFavorite) LibraryJournalActionType.FAVORITE else LibraryJournalActionType.UNFAVORITE,
                mangaId = manga.id,
                previousFavorite = manga.favorite,
                previousDateAdded = manga.dateAdded,
                expectedPostFavorite = newFavorite,
                previousCategoryIds = null,
                expectedPostCategoryIds = null,
                isBulk = previousMangas.size > 1,
                bulkOperationId = bulkId,
            )
        }
    }

    /** @param previousCategoryIds the manga's complete category-id set read BEFORE the write. */
    fun buildCategoriesEntry(
        sourcePreferences: SourcePreferences,
        mangaId: Long,
        previousCategoryIds: List<Long>,
        newCategoryIds: List<Long>,
    ): LibraryJournalEntry? {
        if (!sourcePreferences.evaluationMode().get()) return null
        if (previousCategoryIds.toSet() == newCategoryIds.toSet()) return null
        return LibraryJournalEntry(
            id = LibraryJournalEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = LibraryJournalActionType.SET_CATEGORIES,
            mangaId = mangaId,
            previousFavorite = null,
            previousDateAdded = null,
            expectedPostFavorite = null,
            previousCategoryIds = previousCategoryIds,
            expectedPostCategoryIds = newCategoryIds,
        )
    }
}
// KMK <--
