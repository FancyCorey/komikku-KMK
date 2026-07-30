package mihon.domain.migration.usecases

import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK Confirmed Blocker Remediation Phase 4 2026-07-29, extended by the follow-up pass 2026-07-29 -->
/**
 * Tests for [MigrationOutcome] and its [MigrationOutcome.fromFailure] factory -- the pure decision
 * extracted from [MigrateMangaUseCase.invoke]'s catch block. Proves the confirmed defect fix
 * (`invoke()` used to swallow a non-fatal exception with no signal to the caller) at the type level,
 * without constructing the use case's full platform-dependency graph (`SourceManager`/
 * `TrackerManager`/`DownloadManager`/`CoverCache`/`UpdateMangaFromRemote`), which no existing test in
 * this codebase does. The exact placement of `currentStep`/`completedFlags`/`skippedFlags`/
 * `finalUpdateStarted` assignments inside [MigrateMangaUseCase.invoke]'s flag-gated blocks and final
 * `updateManga.awaitAll(...)` call was verified by direct code reading rather than by a fake-based
 * integration test -- see the Phase 4 report for the exact disposition and why a full integration
 * harness was judged disproportionate to this use case's platform-dependency surface.
 */
class MigrationOutcomeTest {

    @Test
    fun `no completed or skipped flags, no current step, and no final update means the migration never started`() {
        val cause = RuntimeException("remote refresh failed")
        val outcome = MigrationOutcome.fromFailure(emptySet(), emptySet(), emptySet(), null, false, cause)
        assertTrue(outcome is MigrationOutcome.NotStarted)
        assertEquals(cause, (outcome as MigrationOutcome.NotStarted).cause)
    }

    @Test
    fun `a failure with zero completed flags but a current step in progress is a partial failure, not NotStarted`() {
        // The failure happened mid-way through the very first flag-gated step (e.g. the CHAPTER
        // block's own work threw before finishing) -- currentStep is set even though completedFlags
        // is still empty, and this must not be misreported as "nothing was attempted."
        val cause = RuntimeException("chapter update failed")
        val outcome = MigrationOutcome.fromFailure(
            setOf(MigrationFlag.CHAPTER),
            emptySet(),
            emptySet(),
            MigrationFlag.CHAPTER,
            false,
            cause,
        )
        assertTrue(outcome is MigrationOutcome.PartialFailure)
        val failure = outcome as MigrationOutcome.PartialFailure
        assertTrue(failure.completedFlags.isEmpty())
        assertEquals(MigrationFlag.CHAPTER, failure.failedAt)
        assertFalse(failure.finalUpdateFailed)
        assertEquals(cause, failure.cause)
    }

    @Test
    fun `a requested-but-skipped flag with no other progress still counts as started, not NotStarted`() {
        // CUSTOM_COVER requested but the source manga has no custom cover -- determined not
        // applicable before any other step ran, then the initial remote refresh-adjacent work fails.
        val cause = RuntimeException("category update failed")
        val outcome = MigrationOutcome.fromFailure(
            setOf(MigrationFlag.CUSTOM_COVER, MigrationFlag.CATEGORY),
            emptySet(),
            setOf(MigrationFlag.CUSTOM_COVER),
            MigrationFlag.CATEGORY,
            false,
            cause,
        )
        assertTrue(outcome is MigrationOutcome.PartialFailure)
        val failure = outcome as MigrationOutcome.PartialFailure
        assertEquals(setOf(MigrationFlag.CUSTOM_COVER), failure.skippedFlags)
    }

    @Test
    fun `a failure after some flags completed reports exactly those flags, not the whole requested set`() {
        val cause = RuntimeException("tracker migration failed")
        val requested = setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY, MigrationFlag.TRACK)
        val outcome = MigrationOutcome.fromFailure(
            requested,
            setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY),
            emptySet(),
            MigrationFlag.TRACK,
            false,
            cause,
        )
        assertTrue(outcome is MigrationOutcome.PartialFailure)
        val failure = outcome as MigrationOutcome.PartialFailure
        assertEquals(setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY), failure.completedFlags)
        assertEquals(MigrationFlag.TRACK, failure.failedAt)
    }

    @Test
    fun `a failure during the final manga update is reported as finalUpdateFailed, not a flag-gated step`() {
        // Mirrors the real invoke()'s currentStep = null reset right before the final,
        // non-flag-gated updateManga.awaitAll(...) call -- every flag-gated step already completed,
        // but the final commit itself threw.
        val cause = RuntimeException("final manga update failed")
        val outcome = MigrationOutcome.fromFailure(
            MigrationFlag.entries.toSet(),
            MigrationFlag.entries.toSet(),
            emptySet(),
            null,
            true,
            cause,
        )
        assertTrue(outcome is MigrationOutcome.PartialFailure)
        val failure = outcome as MigrationOutcome.PartialFailure
        assertEquals(MigrationFlag.entries.toSet(), failure.completedFlags)
        assertNull(failure.failedAt)
        assertTrue(failure.finalUpdateFailed, "a failure in updateManga.awaitAll(...) must be distinguishable from a mid-flag failure")
    }

    @Test
    fun `Success carries requested, completed, and skipped flags that together equal the request`() {
        val requested = setOf(MigrationFlag.CHAPTER, MigrationFlag.REMOVE_DOWNLOAD, MigrationFlag.NOTES)
        val outcome = MigrationOutcome.Success(
            requestedFlags = requested,
            completedFlags = setOf(MigrationFlag.CHAPTER, MigrationFlag.REMOVE_DOWNLOAD),
            skippedFlags = setOf(MigrationFlag.NOTES),
        )
        assertEquals(requested, outcome.completedFlags + outcome.skippedFlags)
    }

    @Test
    fun `NotStarted can carry a null cause for the target-source-not-resolvable case`() {
        val outcome = MigrationOutcome.NotStarted(null)
        assertNull(outcome.cause)
    }

    @Test
    fun `the three outcome variants are mutually exclusive sealed cases`() {
        val outcomes: List<MigrationOutcome> = listOf(
            MigrationOutcome.Success(emptySet(), emptySet(), emptySet()),
            MigrationOutcome.PartialFailure(emptySet(), emptySet(), emptySet(), null, false, RuntimeException()),
            MigrationOutcome.NotStarted(null),
        )
        val whenResults = outcomes.map {
            when (it) {
                is MigrationOutcome.Success -> "success"
                is MigrationOutcome.PartialFailure -> "partial"
                is MigrationOutcome.NotStarted -> "not_started"
            }
        }
        assertEquals(listOf("success", "partial", "not_started"), whenResults)
    }
}
// KMK <--
