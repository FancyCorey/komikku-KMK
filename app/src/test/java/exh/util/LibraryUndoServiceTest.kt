package exh.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK Undo Expansion Phase 1 -->
/** Pure-logic coverage for the favorite/category conflict checks. Interactor-level restore is covered in [LibraryUndoServiceRestoreTest]. */
class LibraryUndoServiceTest {

    @Test
    fun `no conflict when current favorite still matches the expected post-action state`() {
        assertFalse(libraryUndoFavoriteConflicts(expectedPostFavorite = true, currentFavorite = true))
    }

    @Test
    fun `conflict when favorite changed after the journaled action`() {
        assertTrue(libraryUndoFavoriteConflicts(expectedPostFavorite = true, currentFavorite = false))
    }

    @Test
    fun `no conflict when category sets are equal regardless of order`() {
        assertFalse(libraryUndoCategoriesConflict(expectedPostCategoryIds = listOf(1L, 2L), currentCategoryIds = listOf(2L, 1L)))
    }

    @Test
    fun `conflict when category set changed after the journaled action`() {
        assertTrue(libraryUndoCategoriesConflict(expectedPostCategoryIds = listOf(1L, 2L), currentCategoryIds = listOf(1L, 3L)))
    }
}
// KMK <--
