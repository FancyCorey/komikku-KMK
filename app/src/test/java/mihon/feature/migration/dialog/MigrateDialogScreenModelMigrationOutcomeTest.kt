package mihon.feature.migration.dialog

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.migration.usecases.MigrationOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

// KMK Confirmed Blocker Remediation follow-up Phase 1 2026-07-29 -->
/**
 * Tests for [MigrateDialogScreenModel.migrateManga]'s [MigrationOutcome] handling -- the fix for the
 * confirmed gap that this call site discarded the outcome entirely and always set
 * `isMigrated = true`, closing the dialog as if a PartialFailure/NotStarted result had fully
 * succeeded. [MigrateMangaUseCase] is mocked directly (it is a concrete class with a heavy platform-
 * dependency constructor -- `SourceManager`/`TrackerManager`/`DownloadManager`/`CoverCache`/
 * `UpdateMangaFromRemote`/etc. -- mirroring the same disproportionate-fixture-cost judgment call
 * already made for this use case in the Phase 4 report) so only the screen model's own outcome
 * handling is under test.
 */
class MigrateDialogScreenModelMigrationOutcomeTest {

    private fun manga(id: Long) = Manga.create().copy(id = id, source = 1L)

    private fun buildModel(migrateManga: MigrateMangaUseCase): MigrateDialogScreenModel {
        val preferenceStore = FakePreferenceStore()
        val sourcePreferences = SourcePreferences(preferenceStore)
        val model = MigrateDialogScreenModel(
            sourcePreference = sourcePreferences,
            coverCache = mockk(relaxed = true),
            downloadManager = mockk(relaxed = true) {
                every { getDownloadCount(any<Manga>()) } returns 0
            },
            migrateManga = migrateManga,
        )
        model.init(manga(1L), manga(2L))
        return model
    }

    @Test
    fun `Success sets isMigrated and returns true`() = runTest {
        val useCase = mockk<MigrateMangaUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns MigrationOutcome.Success(emptySet(), emptySet(), emptySet())
        val model = buildModel(useCase)

        val migrated = model.migrateManga(replace = true)

        assertTrue(migrated)
        assertTrue(model.state.value.isMigrated)
        assertFalse(model.state.value.isMigrating)
        assertNull(model.state.value.errorMessage)
    }

    @Test
    fun `PartialFailure does not mark migrated and surfaces the partial-failure error key`() = runTest {
        val useCase = mockk<MigrateMangaUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns MigrationOutcome.PartialFailure(
            requestedFlags = emptySet(),
            completedFlags = emptySet(),
            skippedFlags = emptySet(),
            failedAt = null,
            finalUpdateFailed = true,
            cause = RuntimeException("final update failed"),
        )
        val model = buildModel(useCase)

        val migrated = model.migrateManga(replace = true)

        assertFalse(migrated)
        assertFalse(model.state.value.isMigrated)
        assertFalse(model.state.value.isMigrating)
        assertEquals(MigrationDialogErrorKey.PARTIAL_FAILURE, model.state.value.errorMessage)
    }

    @Test
    fun `NotStarted does not mark migrated and surfaces the not-started error key`() = runTest {
        val useCase = mockk<MigrateMangaUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns MigrationOutcome.NotStarted(RuntimeException("no target source"))
        val model = buildModel(useCase)

        val migrated = model.migrateManga(replace = false)

        assertFalse(migrated)
        assertFalse(model.state.value.isMigrated)
        assertEquals(MigrationDialogErrorKey.NOT_STARTED, model.state.value.errorMessage)
    }

    @Test
    fun `a retry after a failure clears the previous error message while migrating`() = runTest {
        val useCase = mockk<MigrateMangaUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns MigrationOutcome.NotStarted(null) andThen MigrationOutcome.Success(emptySet(), emptySet(), emptySet())
        val model = buildModel(useCase)

        model.migrateManga(replace = false)
        assertEquals(MigrationDialogErrorKey.NOT_STARTED, model.state.value.errorMessage)

        val migrated = model.migrateManga(replace = false)

        assertTrue(migrated)
        assertNull(model.state.value.errorMessage)
        assertTrue(model.state.value.isMigrated)
    }
}
// KMK <--
