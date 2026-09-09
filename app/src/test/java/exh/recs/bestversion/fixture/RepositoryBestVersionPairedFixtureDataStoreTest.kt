package exh.recs.bestversion.fixture

import exh.util.ActionHistoryDiagnosticTrace
import exh.util.MigrationReceipt
import exh.util.MigrationReceiptJournal
import exh.util.NonUndoableEvent
import exh.util.NonUndoableEventJournal
import exh.util.NonUndoableEventType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaSourceQualitySignal
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository
import tachiyomi.domain.taste.repository.TasteRepository
import java.util.UUID

class RepositoryBestVersionPairedFixtureDataStoreTest {
    private val mangaRepository = mockk<MangaRepository>(relaxed = true)
    private val chapterRepository = mockk<ChapterRepository>(relaxed = true)
    private val historyRepository = mockk<HistoryRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val tasteRepository = mockk<TasteRepository>(relaxed = true)
    private val qualitySignalRepository = mockk<MangaSourceQualitySignalRepository>(relaxed = true)
    private val store = RepositoryBestVersionPairedFixtureDataStore(
        mangaRepository,
        chapterRepository,
        historyRepository,
        categoryRepository,
        tasteRepository,
        qualitySignalRepository,
        now = { 1_700_000_000_000L },
    )
    private val spec = BestVersionPairedFixtureSpec()
    private val manifest = BestVersionPairedFixtureManifest(operationId = UUID.randomUUID().toString())

    @AfterEach
    fun clearInMemoryHistory() {
        NonUndoableEventJournal.clear()
        MigrationReceiptJournal.clear()
        ActionHistoryDiagnosticTrace.clear()
    }

    @Test
    fun `origin step inserts through repository and records the real row id`() = runTest {
        val inserted = manga(spec.originSourceId, spec.originUrl, 41L, manifest.ownershipMarker)
        val persisted = inserted
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.originUrl, spec.originSourceId) } returnsMany
            listOf(null, persisted)
        coEvery { mangaRepository.insertNetworkManga(any(), true) } returns listOf(inserted)
        coEvery { mangaRepository.update(any()) } returns true

        val result = store.applyStep(spec, manifest, BestVersionPairedFixtureStep.ORIGIN_MANGA)

        assertEquals(41L, result.originMangaId)
        coVerify(exactly = 1) { mangaRepository.insertNetworkManga(any(), true) }
        coVerify(exactly = 1) {
            mangaRepository.update(
                match<MangaUpdate> { it.id == 41L && it.favorite == true },
            )
        }
    }

    @Test
    fun `cleanup refuses a row whose ownership marker does not match`() = runTest {
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.originUrl, spec.originSourceId) } returns
            manga(spec.originSourceId, spec.originUrl, 41L, "foreign")
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.targetUrl, spec.targetSourceId) } returns null

        assertFalse(store.cleanup(spec, manifest))
        coVerify(exactly = 0) { mangaRepository.deleteManga(any()) }
        coVerify(exactly = 0) { tasteRepository.deleteAllMangaTastes() }
        coVerify(exactly = 0) { tasteRepository.deleteAllCrossSourceMangaLinks() }
    }

    @Test
    fun `cleanup removes only exact fixture rows through typed repositories`() = runTest {
        val origin = manga(spec.originSourceId, spec.originUrl, 41L, manifest.ownershipMarker)
        val target = manga(spec.targetSourceId, spec.targetUrl, 42L, manifest.ownershipMarker)
        val originChapter = Chapter.create().copy(id = 51L, mangaId = origin.id, url = "/kmk-fixture/f2/origin/chapter-1")
        val targetChapter = Chapter.create().copy(id = 61L, mangaId = target.id, url = "/kmk-fixture/f2/target/chapter-1")
        val category = Category(71L, manifest.categoryName, 0L, 0L, false)
        val link = CrossSourceMangaLink(
            spec.originSourceId,
            spec.originUrl,
            manifest.groupId,
            "Fixture Pair A",
            1L,
            1L,
        )
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.originUrl, spec.originSourceId) } returns origin
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.targetUrl, spec.targetSourceId) } returns target
        coEvery { tasteRepository.getAllCrossSourceMangaLinks() } returns listOf(link)
        coEvery { categoryRepository.getAll() } returns listOf(category)
        coEvery { chapterRepository.getChapterByMangaId(origin.id) } returns listOf(originChapter)
        coEvery { chapterRepository.getChapterByMangaId(target.id) } returns listOf(targetChapter)

        assertTrue(store.cleanup(spec, manifest.copy(categoryId = category.id)))

        coVerify(exactly = 1) { tasteRepository.deleteCrossSourceGroupCompletely(manifest.groupId) }
        coVerify(exactly = 1) { tasteRepository.deleteMangaTaste(spec.originSourceId, spec.originUrl) }
        coVerify(exactly = 1) { tasteRepository.deleteMangaTaste(spec.targetSourceId, spec.targetUrl) }
        coVerify(exactly = 1) { historyRepository.resetHistoryByMangaIds(listOf(41L, 42L)) }
        coVerify(exactly = 1) { chapterRepository.removeChaptersWithIds(listOf(51L, 61L)) }
        coVerify(exactly = 1) { mangaRepository.deleteManga(41L) }
        coVerify(exactly = 1) { mangaRepository.deleteManga(42L) }
        coVerify(exactly = 1) { categoryRepository.delete(71L) }
    }

    @Test
    fun `preflight detects orphan fixture category even when manga rows are absent`() = runTest {
        coEvery { mangaRepository.getMangaByUrlAndSourceId(any(), any()) } returns null
        coEvery { categoryRepository.getAll() } returns listOf(Category(71L, "KMK F2 orphaned", 0L, 0L, false))
        coEvery { tasteRepository.getAllCrossSourceMangaLinks() } returns emptyList()
        coEvery { tasteRepository.getAllCrossSourceGroupPrimaries() } returns emptyList()

        val observed = store.inspect(spec, manifest = null)

        assertFalse(observed.isAbsent)
        assertTrue(observed.categoryRows.single().contains("KMK F2 orphaned"))
    }

    @Test
    fun `cleanup removes exact post baseline side effects and preserves foreign history`() = runTest {
        val origin = manga(spec.originSourceId, spec.originUrl, 41L, manifest.ownershipMarker)
        val target = manga(spec.targetSourceId, spec.targetUrl, 42L, manifest.ownershipMarker)
        val foreignId = "foreign-event"
        NonUndoableEventJournal.record(NonUndoableEvent(foreignId, 1L, NonUndoableEventType.MIGRATION_COMPLETED))
        MigrationReceiptJournal.record(MigrationReceipt(foreignId, 1L, 8L, 80L, 9L, 90L, false))
        val foreignSignal = qualitySignal(71L, 80L, "/foreign", 90L, "/foreign-target")
        coEvery { qualitySignalRepository.getAll() } returns listOf(foreignSignal)
        val baseline = store.captureSideEffectBaseline()

        val fixtureId = "fixture-event"
        NonUndoableEventJournal.record(NonUndoableEvent(fixtureId, 2L, NonUndoableEventType.MIGRATION_COMPLETED))
        MigrationReceiptJournal.record(
            MigrationReceipt(fixtureId, 2L, origin.id, spec.originSourceId, target.id, spec.targetSourceId, true),
        )
        val fixtureSignal = qualitySignal(72L, spec.originSourceId, spec.originUrl, spec.targetSourceId, spec.targetUrl)
        coEvery { qualitySignalRepository.getByOrigin(spec.originSourceId, spec.originUrl) } returns listOf(fixtureSignal)
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.originUrl, spec.originSourceId) } returns origin
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.targetUrl, spec.targetSourceId) } returns target
        coEvery { tasteRepository.getAllCrossSourceMangaLinks() } returns emptyList()
        coEvery { categoryRepository.getAll() } returns emptyList()
        coEvery { chapterRepository.getChapterByMangaId(any()) } returns emptyList()

        assertTrue(store.cleanup(spec, manifest.copy(originMangaId = 41L, targetMangaId = 42L, sideEffectBaseline = baseline)))

        coVerify(exactly = 1) { qualitySignalRepository.deleteById(72L) }
        coVerify(exactly = 0) { qualitySignalRepository.deleteById(71L) }
        assertTrue(NonUndoableEventJournal.snapshot().any { it.id == foreignId })
        assertTrue(NonUndoableEventJournal.snapshot().none { it.id == fixtureId })
        assertTrue(MigrationReceiptJournal.snapshot().any { it.id == foreignId })
        assertTrue(MigrationReceiptJournal.snapshot().none { it.id == fixtureId })
    }

    @Test
    fun `cleanup refuses an uncorrelated post baseline migration event before mutation`() = runTest {
        val origin = manga(spec.originSourceId, spec.originUrl, 41L, manifest.ownershipMarker)
        val target = manga(spec.targetSourceId, spec.targetUrl, 42L, manifest.ownershipMarker)
        NonUndoableEventJournal.record(
            NonUndoableEvent("ambiguous", 2L, NonUndoableEventType.MIGRATION_COMPLETED),
        )
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.originUrl, spec.originSourceId) } returns origin
        coEvery { mangaRepository.getMangaByUrlAndSourceId(spec.targetUrl, spec.targetSourceId) } returns target
        coEvery { tasteRepository.getAllCrossSourceMangaLinks() } returns emptyList()
        coEvery { categoryRepository.getAll() } returns emptyList()

        assertFalse(store.cleanup(spec, manifest.copy(originMangaId = 41L, targetMangaId = 42L)))

        coVerify(exactly = 0) { mangaRepository.deleteManga(any()) }
        assertEquals("ambiguous", NonUndoableEventJournal.snapshot().single().id)
    }

    private fun manga(
        source: Long,
        url: String,
        id: Long,
        marker: String,
    ) = Manga.create().copy(
        id = id,
        source = source,
        url = url,
        ogTitle = "Fixture",
        // Keep this direct repository test independent of Manga's global custom-metadata injector.
        // The production adapter separately verifies the favorite=true MangaUpdate write.
        favorite = false,
        notes = marker,
    )

    private fun qualitySignal(id: Long, originSource: Long, originUrl: String, targetSource: Long, targetUrl: String) =
        MangaSourceQualitySignal(
            id = id,
            originSourceId = originSource,
            originUrl = originUrl,
            originTitle = "Fixture",
            selectedSourceId = targetSource,
            selectedUrl = targetUrl,
            selectedTitle = "Fixture",
            selectedSourceName = "Fixture Source",
            comparedCandidatesJson = "[]",
            chapterNumber = null,
            chapterName = "",
            sampleSize = 1,
            sampledPagesJson = "[]",
            selectedAt = 1L,
            qualitySignalVersion = 1,
        )
}
