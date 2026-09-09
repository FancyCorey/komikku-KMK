package exh.recs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Domain B capture-gating tests: loaded-visible-result records exactly once, recomposition/loading/
 * empty/partial states never record, and a stale generation is rejected.
 */
class RecommendationExposureCapturePolicyTest {

    @Test
    fun `a loaded, non-empty, fully-settled, not-yet-recorded generation should record`() {
        assertTrue(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = false,
                hasItems = true,
                total = 5,
                progress = 5,
                generation = 1L,
                lastRecordedGeneration = -1L,
            ),
        )
    }

    @Test
    fun `loading state never records`() {
        assertFalse(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = true,
                hasItems = true,
                total = 5,
                progress = 5,
                generation = 1L,
                lastRecordedGeneration = -1L,
            ),
        )
    }

    @Test
    fun `empty items never records`() {
        assertFalse(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = false,
                hasItems = false,
                total = 0,
                progress = 0,
                generation = 1L,
                lastRecordedGeneration = -1L,
            ),
        )
    }

    @Test
    fun `zero total sources never records`() {
        assertFalse(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = false,
                hasItems = true,
                total = 0,
                progress = 0,
                generation = 1L,
                lastRecordedGeneration = -1L,
            ),
        )
    }

    @Test
    fun `a partial (still-in-progress) result never records -- only a fully settled one does`() {
        assertFalse(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = false,
                hasItems = true,
                total = 5,
                progress = 3,
                generation = 1L,
                lastRecordedGeneration = -1L,
            ),
        )
    }

    @Test
    fun `recording exactly once per generation -- a repeated call for the same generation is rejected`() {
        // Simulates: the Tab's LaunchedEffect body ran once, updated lastRecordedGeneration, then
        // Compose recomposed with the identical generation (e.g. an unrelated state field changed).
        assertFalse(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = false,
                hasItems = true,
                total = 5,
                progress = 5,
                generation = 1L,
                lastRecordedGeneration = 1L,
            ),
        )
    }

    @Test
    fun `a new generation after a previously recorded one is allowed to record again`() {
        // Simulates: refresh() bumped resultGeneration to 2, superseding a stale generation-1 record.
        assertTrue(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = false,
                hasItems = true,
                total = 5,
                progress = 5,
                generation = 2L,
                lastRecordedGeneration = 1L,
            ),
        )
    }

    @Test
    fun `an older-numbered generation than what was already recorded is still rejected if equal`() {
        // Defensive: generation is monotonically increasing in practice, but equality is the only
        // comparison the gate performs, and it must reject an exact repeat regardless of ordering.
        assertFalse(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = false,
                hasItems = true,
                total = 5,
                progress = 5,
                generation = 5L,
                lastRecordedGeneration = 5L,
            ),
        )
    }

    @Test
    fun `negative progress or total values never record (defensive)`() {
        assertFalse(
            RecommendationExposureCapturePolicy.shouldRecord(
                isLoading = false,
                hasItems = true,
                total = -1,
                progress = -1,
                generation = 1L,
                lastRecordedGeneration = -1L,
            ),
        )
    }
}
// KMK <--
