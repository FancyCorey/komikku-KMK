package exh.util

import eu.kanade.domain.manga.interactor.UpdateManga
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK Undo Expansion Phase 1 -->
/** Pure conflict check for a favorite/unfavorite entry: compares only `favorite`, never `dateAdded`/covers/etc. */
fun libraryUndoFavoriteConflicts(expectedPostFavorite: Boolean?, currentFavorite: Boolean?): Boolean =
    expectedPostFavorite != currentFavorite

/** Pure conflict check for a category-set entry: order-independent set comparison. */
fun libraryUndoCategoriesConflict(expectedPostCategoryIds: List<Long>?, currentCategoryIds: List<Long>): Boolean =
    expectedPostCategoryIds?.toSet() != currentCategoryIds.toSet()

/**
 * Restore logic for [LibraryUndoJournal] entries (favorite/unfavorite row flip, category-set replace).
 * Never restores cover files, downloaded chapters, remote metadata, or tracker state -- those are real
 * external effects outside this journal's scope by design (see the coverage audit).
 */
class LibraryUndoService(
    private val getManga: GetManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
) {
    suspend fun undo(entryId: String): GroupUndoResult {
        val entry = LibraryUndoJournal.snapshot().find { it.id == entryId } ?: return GroupUndoResult.FAILED
        if (!entry.reversible) return GroupUndoResult.CONFLICT
        return try {
            when (entry.actionType) {
                LibraryJournalActionType.FAVORITE, LibraryJournalActionType.UNFAVORITE -> restoreFavorite(entry)
                LibraryJournalActionType.SET_CATEGORIES -> restoreCategories(entry)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GroupUndoResult.FAILED
        }
    }

    private suspend fun restoreFavorite(entry: LibraryJournalEntry): GroupUndoResult {
        val current = getManga.await(entry.mangaId) ?: return GroupUndoResult.CONFLICT
        if (libraryUndoFavoriteConflicts(entry.expectedPostFavorite, current.favorite)) {
            return GroupUndoResult.CONFLICT
        }
        val success = updateManga.await(
            MangaUpdate(id = entry.mangaId, favorite = entry.previousFavorite, dateAdded = entry.previousDateAdded),
        )
        if (!success) return GroupUndoResult.FAILED
        LibraryUndoJournal.removeById(entry.id)
        return GroupUndoResult.RESTORED
    }

    private suspend fun restoreCategories(entry: LibraryJournalEntry): GroupUndoResult {
        val currentCategoryIds = getCategories.await(entry.mangaId).map { it.id }
        if (libraryUndoCategoriesConflict(entry.expectedPostCategoryIds, currentCategoryIds)) {
            return GroupUndoResult.CONFLICT
        }
        val restored = setMangaCategories.await(entry.mangaId, entry.previousCategoryIds.orEmpty())
        if (!restored) return GroupUndoResult.FAILED
        LibraryUndoJournal.removeById(entry.id)
        return GroupUndoResult.RESTORED
    }
}
// KMK <--
