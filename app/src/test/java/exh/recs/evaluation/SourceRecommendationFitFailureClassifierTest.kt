package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class SourceRecommendationFitFailureClassifierTest {

    @Test
    fun `null errorMessage classifies as NONE`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.NONE,
            SourceRecommendationFitFailureClassifier.classify(null),
        )
    }

    @Test
    fun `blank errorMessage classifies as NONE`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.NONE,
            SourceRecommendationFitFailureClassifier.classify("   "),
        )
    }

    @Test
    fun `extension not found message classifies as EXTENSION_NOT_FOUND`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.EXTENSION_NOT_FOUND,
            SourceRecommendationFitFailureClassifier.classify("Extension not found in available sources"),
        )
    }

    @Test
    fun `install failed message classifies as INSTALL_FAILED_OR_TIMED_OUT`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.INSTALL_FAILED_OR_TIMED_OUT,
            SourceRecommendationFitFailureClassifier.classify("Install failed or timed out"),
        )
    }

    @Test
    fun `did not load message classifies as INSTALLED_EXTENSION_DID_NOT_LOAD`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.INSTALLED_EXTENSION_DID_NOT_LOAD,
            SourceRecommendationFitFailureClassifier.classify("Installed extension did not load"),
        )
    }

    @Test
    fun `source not found classifies as SOURCE_NOT_FOUND`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.SOURCE_NOT_FOUND,
            SourceRecommendationFitFailureClassifier.classify("Source not found in installed extension"),
        )
    }

    @Test
    fun `search error message classifies as SEARCH_ERROR`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.SEARCH_ERROR,
            SourceRecommendationFitFailureClassifier.classify("Plan TOP_TAGS_FILTER: error — UnknownHostException"),
        )
    }

    @Test
    fun `timed out message classifies as SEARCH_TIMED_OUT`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.SEARCH_TIMED_OUT,
            SourceRecommendationFitFailureClassifier.classify("Plan TOP_TAGS_FILTER: timed out"),
        )
    }

    @Test
    fun `no raw results message classifies as RAW_RESULTS_EMPTY`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.RAW_RESULTS_EMPTY,
            SourceRecommendationFitFailureClassifier.classify("Plan TOP_TAGS_FILTER: no raw results"),
        )
    }

    @Test
    fun `no genre metadata message classifies as RESULTS_NO_METADATA`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.RESULTS_NO_METADATA,
            SourceRecommendationFitFailureClassifier.classify("5 results, all had no genre metadata"),
        )
    }

    @Test
    fun `available extension list empty classifies correctly`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.AVAILABLE_EXTENSION_LIST_EMPTY,
            SourceRecommendationFitFailureClassifier.classify("Available extension list unavailable"),
        )
    }

    @Test
    fun `extension match ambiguous classifies correctly`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.EXTENSION_MATCH_AMBIGUOUS,
            SourceRecommendationFitFailureClassifier.classify("Extension match ambiguous: multiple candidates"),
        )
    }

    @Test
    fun `source match ambiguous classifies correctly`() {
        assertEquals(
            SourceRecommendationProbeFailureKind.SOURCE_MATCH_AMBIGUOUS,
            SourceRecommendationFitFailureClassifier.classify("Source match ambiguous in installed extension: 2 matches"),
        )
    }

    @Test
    fun `install and load failures are recognized as install-or-load issues`() {
        val installLoadKinds = listOf(
            SourceRecommendationProbeFailureKind.EXTENSION_NOT_FOUND,
            SourceRecommendationProbeFailureKind.EXTENSION_MATCH_AMBIGUOUS,
            SourceRecommendationProbeFailureKind.INSTALL_FAILED_OR_TIMED_OUT,
            SourceRecommendationProbeFailureKind.INSTALLED_EXTENSION_DID_NOT_LOAD,
            SourceRecommendationProbeFailureKind.SOURCE_NOT_FOUND,
            SourceRecommendationProbeFailureKind.SOURCE_MATCH_AMBIGUOUS,
            SourceRecommendationProbeFailureKind.AVAILABLE_EXTENSION_LIST_EMPTY,
        )
        for (kind in installLoadKinds) {
            assertTrue(
                SourceRecommendationFitFailureClassifier.isInstallOrLoadIssue(kind),
                "$kind should be classified as install/load issue",
            )
        }
    }

    @Test
    fun `search errors are not install-or-load issues`() {
        val searchKinds = listOf(
            SourceRecommendationProbeFailureKind.SEARCH_ERROR,
            SourceRecommendationProbeFailureKind.SEARCH_TIMED_OUT,
            SourceRecommendationProbeFailureKind.RAW_RESULTS_EMPTY,
            SourceRecommendationProbeFailureKind.RESULTS_NO_METADATA,
            SourceRecommendationProbeFailureKind.UNKNOWN,
        )
        for (kind in searchKinds) {
            assertTrue(
                !SourceRecommendationFitFailureClassifier.isInstallOrLoadIssue(kind),
                "$kind should NOT be classified as install/load issue",
            )
        }
    }
}
// KMK <--
