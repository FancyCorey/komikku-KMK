package exh.recs.share

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.TestInjektSupport
import exh.taste.StubMangaRepository
import exh.util.FakePreferenceStore
import exh.util.LibraryUndoJournal
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import mihon.domain.source.interactor.UpdateMangaFromRemote
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager

// KMK Confirmed Blocker Remediation Phase 5 2026-07-29 -->
/**
 * Tests for [RecommendationBundleLibraryAdder.addToLibrary]'s journal wiring -- the fix for the
 * confirmed gap that bundle-import favorite-flips had no typed local undo at all, unlike every other
 * favorite-flip call site in the app. Mirrors [exh.util.LibraryUndoServiceRestoreTest]'s fake-repository
 * pattern rather than mocking the whole class: real [UpdateManga]/[SetMangaCategories]/[GetCategories]
 * interactors over in-memory fakes, proving the entry is built from the pre-write manga, recorded only
 * after the favorite write actually succeeds, and skipped entirely when Evaluation Mode is disabled.
 */
private class FakeMangaRepository(private val categoryRepository: FakeCategoryRepository) : StubMangaRepository() {
    val byId = mutableMapOf<Long, Manga>()

    override suspend fun update(update: MangaUpdate): Boolean {
        val existing = byId[update.id] ?: return false
        byId[update.id] = existing.copy(
            favorite = update.favorite ?: existing.favorite,
            dateAdded = update.dateAdded ?: existing.dateAdded,
        )
        return true
    }
    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        categoryRepository.byMangaId[mangaId] = categoryIds.toMutableList()
    }
}

private class FakeCategoryRepository : CategoryRepository {
    val byMangaId = mutableMapOf<Long, MutableList<Long>>()
    override suspend fun get(id: Long): Category? = null
    override suspend fun getAll(): List<Category> = emptyList()
    override fun getAllAsFlow(): Flow<List<Category>> = emptyFlow()
    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> = emptyList()
    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> = emptyFlow()
    override suspend fun insert(category: Category): Long = 0L
    override suspend fun updatePartial(update: CategoryUpdate) {}
    override suspend fun updatePartial(updates: List<CategoryUpdate>) {}
    override suspend fun updateAllFlags(flags: Long?) {}
    override suspend fun delete(categoryId: Long) {}
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

class RecommendationBundleLibraryAdderJournalTest {

    private val categoryRepository = FakeCategoryRepository()
    private val mangaRepository = FakeMangaRepository(categoryRepository)
    private val preferenceStore = FakePreferenceStore()
    private val sourcePreferences = SourcePreferences(preferenceStore)
    private val libraryPreferences = LibraryPreferences(preferenceStore)

    private fun buildAdder(): RecommendationBundleLibraryAdder {
        libraryPreferences.fetchMetadataOnAdd().set(false)
        return RecommendationBundleLibraryAdder(
            sourceManager = mockk(relaxed = true),
            libraryPreferences = libraryPreferences,
            getDuplicateLibraryManga = GetDuplicateLibraryManga(mangaRepository),
            getCategories = GetCategories(categoryRepository),
            setMangaCategories = SetMangaCategories(mangaRepository),
            updateManga = UpdateManga(mangaRepository, FetchInterval(GetChaptersByMangaId(ThrowingChapterRepository()))),
            updateMangaFromRemote = mockk<UpdateMangaFromRemote>(relaxed = true),
            setMangaDefaultChapterFlags = SetMangaDefaultChapterFlags(
                libraryPreferences,
                SetMangaChapterFlags(mangaRepository),
                GetFavorites(mangaRepository),
            ),
            sourcePreferences = sourcePreferences,
        )
    }

    init {
        TestInjektSupport.ensureCustomMangaInfoBound()
    }

    @AfterEach
    fun tearDown() {
        LibraryUndoJournal.clear()
    }

    private fun newManga(id: Long) = Manga.create().copy(id = id, favorite = false, dateAdded = 0L)

    @Test
    fun `a successful add journals a FAVORITE entry built from the pre-write manga`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        mangaRepository.byId[1L] = newManga(1L)
        val adder = buildAdder()

        val result = adder.addToLibrary(newManga(1L), skipDuplicates = true)

        assertTrue(result.outcome is RecommendationBundleLibraryAdder.Outcome.Added)
        assertTrue(mangaRepository.byId[1L]?.favorite == true)
        val entries = LibraryUndoJournal.snapshot()
        assertEquals(1, entries.size)
        assertEquals(1L, entries.first().mangaId)
        assertFalse(entries.first().previousFavorite ?: true)
        assertEquals(true, entries.first().expectedPostFavorite)
    }

    @Test
    fun `Evaluation Mode disabled records nothing even though the add still succeeds`() = runTest {
        sourcePreferences.evaluationMode().set(false)
        mangaRepository.byId[2L] = newManga(2L)
        val adder = buildAdder()

        val result = adder.addToLibrary(newManga(2L), skipDuplicates = true)

        assertTrue(result.outcome is RecommendationBundleLibraryAdder.Outcome.Added)
        assertTrue(LibraryUndoJournal.isEmpty())
    }

    @Test
    fun `a manga missing from the repository fails the write and records nothing`() = runTest {
        sourcePreferences.evaluationMode().set(true)
        // manga 3L intentionally not seeded into mangaRepository.byId -- update() returns false.
        val adder = buildAdder()

        val result = adder.addToLibrary(newManga(3L), skipDuplicates = true)

        assertTrue(result.outcome is RecommendationBundleLibraryAdder.Outcome.Added)
        assertTrue(LibraryUndoJournal.isEmpty(), "a journal entry must never be recorded when the underlying favorite write did not actually apply")
    }
}
// KMK <--
