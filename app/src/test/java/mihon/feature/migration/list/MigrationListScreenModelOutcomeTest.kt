package mihon.feature.migration.list

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.migration.usecases.MigrationOutcome
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.feature.migration.list.models.MigratingManga
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

// KMK -->
/**
 * Direct fixture-based tests for [MigrationListScreenModel]'s actual `migrateNow()`/`migrateMangas()`
 * caller-level decisions. Confirmed regression this fixes: `migrateNow()` previously logged a
 * non-Success outcome and then called `removeManga(mangaId)` unconditionally, silently discarding
 * the failed item from the pending list.
 *
 * Determinism fix (V2): the earlier test implementation used `runBlocking` against the real
 * `Dispatchers.IO` thread pool with wall-clock polling loops, which passed reliably in isolation but
 * intermittently timed out when run as part of the full ~1834-test suite (six failures observed
 * across multiple full-suite runs, root cause not fully isolated). `MigrationListScreenModel` now
 * accepts an injectable `ioDispatcher` (defaulting to the real `Dispatchers.IO` in production). This
 * test constructs the model with a `UnconfinedTestDispatcher(testScheduler)` for both `Dispatchers.Main`
 * (via `Dispatchers.setMain`) and `ioDispatcher`, so every coroutine the model launches runs on the
 * single virtual-time `TestCoroutineScheduler` `runTest` already owns. Assertions after
 * `advanceUntilIdle()` are therefore synchronous and deterministic -- no real background thread, no
 * wall-clock `delay`, and no dependency on unrelated tests' load on a shared thread pool.
 *
 * The model is constructed with `runManually = true` so its `init` block populates `items` (each
 * starting at `SearchResult.NotFound`) without launching the real auto-search-and-migrate pipeline
 * (`SmartSourceSearchEngine`/`SourceMatchScorer`/per-source async search), which has no existing test
 * seam in this codebase and is not what this class's actual defect is about. [MigrateMangaUseCase] is
 * mocked directly to return canned [MigrationOutcome] values -- it is a concrete class with a heavy
 * platform-dependency constructor, the same disproportionate-fixture-cost judgment recorded in the
 * Phase 4 and Follow-up 1 reports. `Source.getNameForMangaInfo()` (called while building each item)
 * reads `SourcePreferences` via `Injekt.get()` internally rather than as a parameter -- a prior
 * version of this test registered a `SourcePreferences` singleton into the process-global Injekt
 * graph to satisfy that call, mirroring the existing `TestInjektSupport` pattern. That made this
 * test's outcome depend on process-global mutable state shared with every other test class in the
 * suite: full-suite runs intermittently failed with `items` empty at the very first assertion after
 * `advanceUntilIdle()` even though the same test passed reliably in isolation and in every smaller
 * combination tried. Mocking `getNameForMangaInfo()` statically (V2 corrective completion pass)
 * removes that call's dependency on Injekt entirely for this test class, so it no longer touches or
 * depends on any state another test class in the suite might register there.
 */
class MigrationListScreenModelOutcomeTest {

    private val preferenceStore = FakePreferenceStore()
    private val sourcePreferences = SourcePreferences(preferenceStore)

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("eu.kanade.tachiyomi.source.SourceExtensionsKt")
    }

    private fun manga(id: Long) = Manga.create().copy(id = id, source = 1L, ogTitle = "Manga $id")

    private fun buildModel(
        ids: List<Long>,
        migrateManga: MigrateMangaUseCase,
        testDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): MigrationListScreenModel {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("eu.kanade.tachiyomi.source.SourceExtensionsKt")
        every { any<Source>().getNameForMangaInfo(any()) } returns "Test Source"

        val getManga = mockk<GetManga>()
        ids.forEach { id -> coEvery { getManga.await(id) } returns manga(id) }

        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        coEvery { getChaptersByMangaId.await(any(), any()) } returns emptyList<Chapter>()

        val source = mockk<Source>(relaxed = true)
        every { source.id } returns 1L
        val sourceManager = mockk<SourceManager>(relaxed = true)
        every { sourceManager.getOrStub(any()) } returns source

        return MigrationListScreenModel(
            mangaIds = ids,
            extraSearchQuery = null,
            runManually = true,
            preferences = sourcePreferences,
            sourceManager = sourceManager,
            getManga = getManga,
            networkToLocalManga = mockk<NetworkToLocalManga>(relaxed = true),
            updateMangaFromRemote = mockk<UpdateMangaFromRemote>(relaxed = true),
            getChaptersByMangaId = getChaptersByMangaId,
            migrateManga = migrateManga,
            ioDispatcher = testDispatcher,
        )
    }

    private fun MigrationListScreenModel.markResolved(id: Long): Manga {
        val target = manga(id * 1000)
        val item = items.first { it.manga.id == id }
        item.searchResult.value = MigratingManga.SearchResult.Success(
            manga = target,
            chapterCount = 0,
            latestChapter = null,
            source = "target",
        )
        return target
    }

    @Test
    fun `migrateNow removes the item only after a verified Success outcome`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val migrateManga = mockk<MigrateMangaUseCase>()
        coEvery { migrateManga(any(), any(), any(), any(), any()) } returns MigrationOutcome.Success(emptySet(), emptySet(), emptySet())
        val model = buildModel(listOf(1L), migrateManga, dispatcher)
        advanceUntilIdle()
        model.markResolved(1L)

        model.migrateNow(mangaId = 1L, replace = true)
        advanceUntilIdle()

        assertTrue(model.items.isEmpty(), "a successful migration must remove the item")
    }

    @Test
    fun `migrateNow keeps a PartialFailure item in the list as a retryable failure`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val migrateManga = mockk<MigrateMangaUseCase>()
        coEvery { migrateManga(any(), any(), any(), any(), any()) } returns MigrationOutcome.PartialFailure(
            requestedFlags = emptySet(),
            completedFlags = emptySet(),
            skippedFlags = emptySet(),
            failedAt = null,
            finalUpdateFailed = true,
            cause = RuntimeException("final update failed"),
        )
        val model = buildModel(listOf(1L), migrateManga, dispatcher)
        advanceUntilIdle()
        model.markResolved(1L)

        model.migrateNow(mangaId = 1L, replace = true)
        advanceUntilIdle()

        assertEquals(1, model.items.size, "a PartialFailure must not remove the item -- logging is not user-facing failure handling")
        val failure = model.items.first().migrationResult.value
        assertTrue(failure is MigratingManga.MigrationResultState.Failed && failure.retryable)
    }

    @Test
    fun `migrateNow keeps a NotStarted item visible and retryable`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val migrateManga = mockk<MigrateMangaUseCase>()
        coEvery { migrateManga(any(), any(), any(), any(), any()) } returns MigrationOutcome.NotStarted(RuntimeException("no target source"))
        val model = buildModel(listOf(1L), migrateManga, dispatcher)
        advanceUntilIdle()
        model.markResolved(1L)

        model.migrateNow(mangaId = 1L, replace = false)
        advanceUntilIdle()

        assertEquals(1, model.items.size)
        val failure = model.items.first().migrationResult.value
        assertTrue(failure is MigratingManga.MigrationResultState.Failed && failure.retryable)
    }

    @Test
    fun `retry after a transient failure succeeds and removes the item`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val migrateManga = mockk<MigrateMangaUseCase>()
        coEvery {
            migrateManga(any(), any(), any(), any(), any())
        } returns MigrationOutcome.NotStarted(RuntimeException("transient")) andThen MigrationOutcome.Success(emptySet(), emptySet(), emptySet())
        val model = buildModel(listOf(1L), migrateManga, dispatcher)
        advanceUntilIdle()
        model.markResolved(1L)

        model.migrateNow(mangaId = 1L, replace = true)
        advanceUntilIdle()
        assertEquals(1, model.items.size)

        model.migrateNow(mangaId = 1L, replace = true)
        advanceUntilIdle()
        assertTrue(model.items.isEmpty(), "a retry that succeeds must remove the item")
    }

    @Test
    fun `cancelling a bulk migration mid-flight retains all items without a receipt`() = runTest {
        // cancelMigrate() only cancels the Job tracked by migrateMangas() (the bulk path) --
        // migrateNow()'s own launch is not separately trackable/cancellable from outside, so this
        // exercises the cancellable path.
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val migrateManga = mockk<MigrateMangaUseCase>()
        coEvery { migrateManga(any(), any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }
        val model = buildModel(listOf(1L), migrateManga, dispatcher)
        advanceUntilIdle()
        model.markResolved(1L)

        model.migrateMangas()
        advanceUntilIdle()
        assertTrue(model.items.first().migrationResult.value is MigratingManga.MigrationResultState.InProgress)

        model.cancelMigrate()
        advanceUntilIdle()

        assertEquals(1, model.items.size, "a cancelled attempt must not remove the item")
        assertNull(model.items.first().migrationResult.value, "cancellation must clear in-progress state, not leave it stuck")
    }

    @Test
    fun `bulk migration preserves failed items, removes only successful ones, and reports counts`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val migrateManga = mockk<MigrateMangaUseCase>()
        coEvery { migrateManga(manga(1L), any(), any(), any(), any()) } returns MigrationOutcome.Success(emptySet(), emptySet(), emptySet())
        coEvery { migrateManga(manga(2L), any(), any(), any(), any()) } returns MigrationOutcome.PartialFailure(
            emptySet(),
            emptySet(),
            emptySet(),
            null,
            true,
            RuntimeException("failed"),
        )
        val model = buildModel(listOf(1L, 2L), migrateManga, dispatcher)
        advanceUntilIdle()
        model.markResolved(1L)
        model.markResolved(2L)

        model.migrateMangas()
        advanceUntilIdle()

        val dialog = model.state.value.dialog
        assertTrue(dialog is MigrationListScreenModel.Dialog.Result, "expected a Result dialog after a partial-failure bulk run")
        dialog as MigrationListScreenModel.Dialog.Result
        assertEquals(1, dialog.failedCount)
        assertEquals(0, dialog.skippedCount)
        assertEquals(2, dialog.totalCount)

        assertEquals(1, model.items.size, "the successful item must be removed; the failed one must remain")
        assertEquals(2L, model.items.first().manga.id)
    }

    @Test
    fun `dismissing the Result dialog does not navigate away, leaving failed items visible`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val migrateManga = mockk<MigrateMangaUseCase>()
        coEvery { migrateManga(any(), any(), any(), any(), any()) } returns MigrationOutcome.NotStarted(null)
        val model = buildModel(listOf(1L), migrateManga, dispatcher)
        advanceUntilIdle()
        model.markResolved(1L)

        model.migrateMangas()
        advanceUntilIdle()
        assertTrue(model.state.value.dialog is MigrationListScreenModel.Dialog.Result)

        model.dismissResultDialog()

        assertNull(model.state.value.dialog)
        assertEquals(1, model.items.size, "the unresolved item must still be present after dismissing the summary")
    }

    @Test
    fun `a missing target is reported as skipped, never as a successful migration`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val migrateManga = mockk<MigrateMangaUseCase>(relaxed = true)
        val model = buildModel(listOf(1L), migrateManga, dispatcher)
        advanceUntilIdle()
        // Intentionally do NOT call markResolved(1L) -- searchResult stays NotFound.

        model.migrateNow(mangaId = 1L, replace = true)
        advanceUntilIdle()

        assertEquals(1, model.items.size, "an item with no successful search result must never be silently migrated or removed")
        val failure = model.items.first().migrationResult.value
        assertTrue(failure is MigratingManga.MigrationResultState.Failed && !failure.retryable)
        assertFalse(model.items.first().migrationResult.value is MigratingManga.MigrationResultState.InProgress)
    }
}
// KMK <--
