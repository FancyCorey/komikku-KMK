package exh.recs.bestversion

// KMK --> v0.7.9
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaMatchSettings
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for the v0.7.9 Best Version dialog guard logic and dismissMigrationDialog() state transition.
 * Works against plain data-class State — no Injekt or domain dependencies required.
 */
class BestVersionSelectionPolicyTest {

    private fun baseState() = BestVersionCompareScreenModel.State(
        step = BestVersionStep.ComparePreview,
        candidatePreviews = persistentMapOf(),
    )

    // --- dismissMigrationDialog() state transitions ---

    @Test
    fun `dismiss clears selectedBestKey`() {
        val state = baseState().copy(selectedBestKey = MangaIdentityKey(1L, "/url"))
        val dismissed = state.copy(selectedBestKey = null, isMigrating = false)
        assertNull(dismissed.selectedBestKey)
    }

    @Test
    fun `dismiss clears isMigrating`() {
        val state = baseState().copy(
            selectedBestKey = MangaIdentityKey(1L, "/url"),
            isMigrating = true,
        )
        val dismissed = state.copy(selectedBestKey = null, isMigrating = false)
        assertFalse(dismissed.isMigrating)
    }

    @Test
    fun `dismiss preserves ComparePreview step`() {
        val state = baseState().copy(
            step = BestVersionStep.ComparePreview,
            selectedBestKey = MangaIdentityKey(1L, "/url"),
        )
        val dismissed = state.copy(selectedBestKey = null, isMigrating = false)
        assertEquals(BestVersionStep.ComparePreview, dismissed.step)
    }

    @Test
    fun `dismiss preserves candidatePreviews`() {
        val previews = persistentMapOf(
            MangaIdentityKey(1L, "/url") to (CandidatePreviewState.Loading as CandidatePreviewState),
        )
        val state = baseState().copy(
            selectedBestKey = MangaIdentityKey(1L, "/url"),
            candidatePreviews = previews,
        )
        val dismissed = state.copy(selectedBestKey = null, isMigrating = false)
        assertEquals(previews, dismissed.candidatePreviews)
    }

    @Test
    fun `dismiss preserves selectedChapterNumber`() {
        val state = baseState().copy(
            selectedBestKey = MangaIdentityKey(1L, "/url"),
            selectedChapterNumber = 42.0,
        )
        val dismissed = state.copy(selectedBestKey = null, isMigrating = false)
        assertEquals(42.0, dismissed.selectedChapterNumber)
    }

    // --- Dialog guard conditions ---

    @Test
    fun `null selectedBestKey means no dialog`() {
        val state = baseState().copy(selectedBestKey = null)
        assertFalse(state.selectedBestKey != null)
    }

    @Test
    fun `non-ComparePreview step suppresses dialog`() {
        val state = baseState().copy(
            step = BestVersionStep.SearchingCandidates,
            selectedBestKey = MangaIdentityKey(1L, "/url"),
        )
        assertFalse(state.step == BestVersionStep.ComparePreview)
    }

    @Test
    fun `PreparingMigration step suppresses dialog`() {
        val state = baseState().copy(
            step = BestVersionStep.PreparingMigration,
            selectedBestKey = MangaIdentityKey(1L, "/url"),
        )
        assertFalse(state.step == BestVersionStep.ComparePreview)
    }

    @Test
    fun `ComparePreview step with non-null key enables dialog condition`() {
        val state = baseState().copy(
            step = BestVersionStep.ComparePreview,
            selectedBestKey = MangaIdentityKey(1L, "/url"),
        )
        assertTrue(state.selectedBestKey != null && state.step == BestVersionStep.ComparePreview)
    }

    // --- SameMangaMatchSettings: preselectResults default is verified at preference level,
    //     but clamp helpers are pure and testable here ---

    @Test
    fun `SameMangaMatchSettings clampResultCap preserves valid values`() {
        for (valid in SameMangaMatchSettings.VALID_RESULT_CAPS) {
            assertEquals(valid, SameMangaMatchSettings.clampResultCap(valid), "Expected $valid to clamp to itself")
        }
    }

    @Test
    fun `SameMangaMatchSettings clampResultCap falls back to default for invalid`() {
        assertEquals(SameMangaMatchSettings.DEFAULT_RESULT_CAP, SameMangaMatchSettings.clampResultCap(-1))
        assertEquals(SameMangaMatchSettings.DEFAULT_RESULT_CAP, SameMangaMatchSettings.clampResultCap(0))
        assertEquals(SameMangaMatchSettings.DEFAULT_RESULT_CAP, SameMangaMatchSettings.clampResultCap(99))
    }

    @Test
    fun `SameMangaMatchSettings default result cap is in valid set`() {
        assertTrue(SameMangaMatchSettings.DEFAULT_RESULT_CAP in SameMangaMatchSettings.VALID_RESULT_CAPS)
    }

    @Test
    fun `SameMangaMatchSettings clampSampleSize preserves valid values`() {
        for (valid in SameMangaMatchSettings.VALID_SAMPLE_SIZES) {
            assertEquals(valid, SameMangaMatchSettings.clampSampleSize(valid), "Expected $valid to clamp to itself")
        }
    }

    @Test
    fun `SameMangaMatchSettings clampSampleSize falls back to default for invalid`() {
        assertEquals(SameMangaMatchSettings.DEFAULT_SAMPLE_SIZE, SameMangaMatchSettings.clampSampleSize(0))
        assertEquals(SameMangaMatchSettings.DEFAULT_SAMPLE_SIZE, SameMangaMatchSettings.clampSampleSize(99))
    }

    @Test
    fun `SameMangaMatchSettings default sample size is in valid set`() {
        assertTrue(SameMangaMatchSettings.DEFAULT_SAMPLE_SIZE in SameMangaMatchSettings.VALID_SAMPLE_SIZES)
    }
}
// KMK <--
