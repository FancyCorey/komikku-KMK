package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class SourceEvaluationKnownUnsafeSeedsTest {

    @Test
    fun `ALL_SEEDS contains DigitalComicMuseum`() {
        assertTrue(SourceEvaluationKnownUnsafeSeeds.ALL_SEEDS.contains(SourceEvaluationKnownUnsafeSeeds.DIGITAL_COMIC_MUSEUM))
    }

    @Test
    fun `DigitalComicMuseum has expected package name`() {
        val seed = SourceEvaluationKnownUnsafeSeeds.DIGITAL_COMIC_MUSEUM
        assertTrue(
            seed.pkgName == "eu.kanade.tachiyomi.extension.en.digitalcomicmuseum",
            "Expected DCM package, got: ${seed.pkgName}",
        )
    }

    @Test
    fun `DigitalComicMuseum extension name is non-empty`() {
        assertFalse(SourceEvaluationKnownUnsafeSeeds.DIGITAL_COMIC_MUSEUM.extensionName.isBlank())
    }

    @Test
    fun `DigitalComicMuseum reason is non-empty`() {
        assertFalse(SourceEvaluationKnownUnsafeSeeds.DIGITAL_COMIC_MUSEUM.reason.isBlank())
    }

    @Test
    fun `DigitalComicMuseum reason mentions crash`() {
        val reason = SourceEvaluationKnownUnsafeSeeds.DIGITAL_COMIC_MUSEUM.reason.lowercase()
        assertTrue(reason.contains("crash") || reason.contains("sigsegv") || reason.contains("overflow")) {
            "Reason should mention native crash: $reason"
        }
    }

    @Test
    fun `ALL_SEEDS is non-empty`() {
        assertNotNull(SourceEvaluationKnownUnsafeSeeds.ALL_SEEDS)
        assertTrue(SourceEvaluationKnownUnsafeSeeds.ALL_SEEDS.isNotEmpty())
    }
}
// KMK <--
