package exh.util

import exh.recs.TestInjektSupport
import exh.taste.StubMangaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate

// KMK Undo Expansion Phase 1 -->
private class FakeMangaRepository(private val categoryRepository: FakeCategoryRepository) : StubMangaRepository() {
    val byId = mutableMapOf<Long, Manga>()

    override suspend fun getMangaById(id: Long): Manga = byId[id] ?: throw NoSuchElementException()
    override suspend fun update(update: MangaUpdate): Boolean {
        val existing = byId[update.id] ?: return false
        byId[update.id] = existing.copy(
            favorite = update.favorite ?: existing.favorite,
            dateAdded = update.dateAdded ?: existing.dateAdded,
        )
        return true
    }
    override suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean {
        mangaUpdates.forEach { update(it) }
        return true
    }
    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        categoryRepository.setCategories(mangaId, categoryIds)
    }
}

private class FakeCategoryRepository : CategoryRepository {
    val byMangaId = mutableMapOf<Long, MutableList<Long>>()
    private fun categoryFor(id: Long) = Category(id = id, name = "cat$id", order = id, flags = 0L, hidden = false)

    override suspend fun get(id: Long): Category? = categoryFor(id)
    override suspend fun getAll(): List<Category> = emptyList()
    override fun getAllAsFlow(): Flow<List<Category>> = emptyFlow()
    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> = byMangaId[mangaId].orEmpty().map { categoryFor(it) }
    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> = emptyFlow()
    override suspend fun insert(category: Category): Long = 0L
    override suspend fun updatePartial(update: CategoryUpdate) {}
    override suspend fun updatePartial(updates: List<CategoryUpdate>) {}
    override suspend fun updateAllFlags(flags: Long?) {}
    override suspend fun delete(categoryId: Long) {}

    // Test-only helper mirroring SetMangaCategories -> MangaRepository.setMangaCategories, wired via the fake manga repo below.
    fun setCategories(mangaId: Long, categoryIds: List<Long>) {
        byMangaId[mangaId] = categoryIds.toMutableList()
    }
}

private class ThrowingChapterRepository : ChapterRepository {
    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> = throw NotImplementedError()
    override suspend fun update(chapterUpdate: tachiyomi.domain.chapter.model.ChapterUpdate) = throw NotImplementedError()
    override suspend fun updateAll(chapterUpdates: List<tachiyomi.domain.chapter.model.ChapterUpdate>) = throw NotImplementedError()
    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = throw NotImplementedError()
    override suspend fun getChapterByMangaId(mangaId: Long, applyFilter: Boolean) = throw NotImplementedError()
    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> = throw NotImplementedError()
    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long) = emptyFlow<List<String>>()
    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long) = throw NotImplementedError()
    override suspend fun getChapterById(id: Long) = null
    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyFilter: Boolean) = emptyFlow<List<Chapter>>()
    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long) = null
    override suspend fun getChapterByUrl(url: String): List<Chapter> = throw NotImplementedError()
    override suspend fun getMergedChapterByMangaId(mangaId: Long, applyFilter: Boolean) = throw NotImplementedError()
    override suspend fun getMergedChapterByMangaIdAsFlow(mangaId: Long, applyFilter: Boolean) = emptyFlow<List<Chapter>>()
    override suspend fun getScanlatorsByMergeId(mangaId: Long): List<String> = throw NotImplementedError()
    override fun getScanlatorsByMergeIdAsFlow(mangaId: Long) = emptyFlow<List<String>>()
}

/**
 * Interactor-level coverage for [LibraryUndoService.undo]: real [GetManga]/`UpdateManga`/`GetCategories`/
 * `SetMangaCategories` interactors over in-memory fakes, mirroring [EvaluationModeUndoServiceRestoreTest]'s
 * pattern (fakes instead of a database).
 */
class LibraryUndoServiceRestoreTest {

    private val categoryRepository = FakeCategoryRepository()
    private val mangaRepository = FakeMangaRepository(categoryRepository)
    private val getManga = GetManga(mangaRepository)
    private val updateManga = eu.kanade.domain.manga.interactor.UpdateManga(mangaRepository, FetchInterval(GetChaptersByMangaId(ThrowingChapterRepository())))
    private val getCategories = tachiyomi.domain.category.interactor.GetCategories(categoryRepository)
    private val setMangaCategoriesInteractor = tachiyomi.domain.category.interactor.SetMangaCategories(mangaRepository)
    private val service = LibraryUndoService(getManga, updateManga, getCategories, setMangaCategoriesInteractor)

    init {
        TestInjektSupport.ensureCustomMangaInfoBound()
    }

    @AfterEach
    fun tearDown() {
        LibraryUndoJournal.clear()
    }

    private fun seedManga(id: Long, favorite: Boolean, dateAdded: Long = 0L) {
        mangaRepository.byId[id] = Manga.create().copy(id = id, favorite = favorite, dateAdded = dateAdded)
    }

    @Test
    fun `undo restores favorite and dateAdded to their previous values`() = runTest {
        seedManga(1L, favorite = true, dateAdded = 500L)
        val entry = LibraryJournalEntry(
            id = LibraryJournalEntry.newId(),
            timestamp = 0L,
            actionType = LibraryJournalActionType.UNFAVORITE,
            mangaId = 1L,
            previousFavorite = true,
            previousDateAdded = 500L,
            expectedPostFavorite = false,
            previousCategoryIds = null,
            expectedPostCategoryIds = null,
        )
        mangaRepository.byId[1L] = mangaRepository.byId[1L]!!.copy(favorite = false, dateAdded = 0L)
        LibraryUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertEquals(GroupUndoResult.RESTORED, outcome)
        assertEquals(true, mangaRepository.byId[1L]?.favorite)
        assertEquals(500L, mangaRepository.byId[1L]?.dateAdded)
        assertTrue(LibraryUndoJournal.isEmpty())
    }

    @Test
    fun `undo refuses and leaves state untouched when favorite changed after journaling`() = runTest {
        seedManga(1L, favorite = true) // diverged from expectedPostFavorite=false
        val entry = LibraryJournalEntry(
            id = LibraryJournalEntry.newId(),
            timestamp = 0L,
            actionType = LibraryJournalActionType.UNFAVORITE,
            mangaId = 1L,
            previousFavorite = true,
            previousDateAdded = 0L,
            expectedPostFavorite = false,
            previousCategoryIds = null,
            expectedPostCategoryIds = null,
        )
        LibraryUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertEquals(GroupUndoResult.CONFLICT, outcome)
        assertEquals(true, mangaRepository.byId[1L]?.favorite)
        assertEquals(1, LibraryUndoJournal.snapshot().size)
    }

    @Test
    fun `undo restores the complete previous category set`() = runTest {
        seedManga(1L, favorite = true)
        categoryRepository.setCategories(1L, listOf(3L))
        val entry = LibraryJournalEntry(
            id = LibraryJournalEntry.newId(),
            timestamp = 0L,
            actionType = LibraryJournalActionType.SET_CATEGORIES,
            mangaId = 1L,
            previousFavorite = null,
            previousDateAdded = null,
            expectedPostFavorite = null,
            previousCategoryIds = listOf(1L, 2L),
            expectedPostCategoryIds = listOf(3L),
        )
        LibraryUndoJournal.record(entry)

        val outcome = service.undo(entry.id)

        assertEquals(GroupUndoResult.RESTORED, outcome)
        assertEquals(setOf(1L, 2L), categoryRepository.byMangaId[1L]?.toSet())
    }

    @Test
    fun `missing manga is a conflict, not a crash`() = runTest {
        val entry = LibraryJournalEntry(
            id = LibraryJournalEntry.newId(),
            timestamp = 0L,
            actionType = LibraryJournalActionType.FAVORITE,
            mangaId = 999L,
            previousFavorite = false,
            previousDateAdded = 0L,
            expectedPostFavorite = true,
            previousCategoryIds = null,
            expectedPostCategoryIds = null,
        )
        LibraryUndoJournal.record(entry)

        assertEquals(GroupUndoResult.CONFLICT, service.undo(entry.id))
    }

    @Test
    fun `undo of an unknown entry id is reported as failed, not a crash`() = runTest {
        assertEquals(GroupUndoResult.FAILED, service.undo("does-not-exist"))
    }
}
// KMK <--
