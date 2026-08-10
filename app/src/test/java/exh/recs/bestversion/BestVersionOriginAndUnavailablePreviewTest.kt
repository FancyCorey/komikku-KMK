package exh.recs.bestversion

// KMK v0.8.18 -->
import exh.recs.matching.MangaIdentityKey
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

/**
 * Tests for the v0.8.18 Best Version fixes: origin/current manga included as the comparison
 * baseline, and unavailable-chapter candidates never entering preview loading. Works against plain
 * data-class [BestVersionCompareScreenModel.State] -- no Injekt or domain dependencies required,
 * same pattern as [BestVersionSelectionPolicyTest].
 */
class BestVersionOriginAndUnavailablePreviewTest {

    private fun manga(source: Long, url: String) =
        Manga.create().copy(source = source, url = url)

    private fun baseState(origin: Manga? = null) = BestVersionCompareScreenModel.State(
        step = BestVersionStep.ComparePreview,
        originManga = origin,
        candidatePreviews = persistentMapOf(),
    )

    // --- origin inclusion ---

    @Test
    fun `compareCandidates includes origin first when no real candidates are selected`() {
        val origin = manga(1L, "/origin")
        val state = baseState(origin)
        assertEquals(listOf(origin), state.compareCandidates)
    }

    @Test
    fun `compareCandidates is empty when origin has not loaded yet`() {
        val state = baseState(origin = null)
        assertTrue(state.compareCandidates.isEmpty())
    }

    @Test
    fun `originKey resolves from originManga`() {
        val origin = manga(1L, "/origin")
        val state = baseState(origin)
        assertEquals(MangaIdentityKey(1L, "/origin"), state.originKey)
    }

    @Test
    fun `originKey is null before origin loads`() {
        val state = baseState(origin = null)
        assertNull(state.originKey)
    }

    // --- keep-current behavior ---

    @Test
    fun `keeping current version never sets selectedBestKey (no migrate dialog trigger)`() {
        val origin = manga(1L, "/origin")
        val kept = baseState(origin).copy(
            step = BestVersionStep.Done,
            selectedBestKey = null,
            migrationComplete = true,
            keptCurrentVersion = true,
            completedTargetMangaId = origin.id,
        )
        assertNull(kept.selectedBestKey)
        assertTrue(kept.keptCurrentVersion)
        assertEquals(BestVersionStep.Done, kept.step)
    }

    @Test
    fun `keeping current version resolves completedTargetMangaId to the origin itself`() {
        val origin = manga(1L, "/origin").copy(id = 42L)
        val kept = baseState(origin).copy(completedTargetMangaId = origin.id, keptCurrentVersion = true)
        assertEquals(42L, kept.completedTargetMangaId)
    }

    @Test
    fun `default state is not a kept-current completion`() {
        assertTrue(!baseState().keptCurrentVersion)
    }

    // --- unavailable-chapter preview skipping ---

    @Test
    fun `an unavailable candidate chapter maps to Skipped, never Loading or null`() {
        val origin = manga(1L, "/origin")
        val unavailableKey = MangaIdentityKey(2L, "/candidate")
        val state = baseState(origin).copy(
            candidateChapters = persistentMapOf(unavailableKey to CandidateChapterState.Unavailable),
            candidatePreviews = persistentMapOf(unavailableKey to CandidatePreviewState.Skipped),
        )
        assertEquals(CandidatePreviewState.Skipped, state.candidatePreviews[unavailableKey])
    }

    @Test
    fun `Skipped is a distinct terminal state from PreviewError`() {
        assertTrue(CandidatePreviewState.Skipped != CandidatePreviewState.PreviewError("x"))
    }
}
// KMK <--
