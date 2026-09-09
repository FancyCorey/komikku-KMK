package exh.recs.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

class SourceMetadataTagDiagnosticsPolicyTest {

    private fun evaluation(key: String, sourceId: Long?, sourceName: String, evaluatedAt: Long) =
        SourceEvaluation(
            evaluationKey = key,
            sourceId = sourceId,
            extensionPkgName = "package.$key",
            signatureHash = "signature",
            extensionName = "Extension",
            sourceName = sourceName,
            lang = "en",
            baseUrl = null,
            repoName = null,
            sourceCount = 1,
            isNsfw = false,
            evaluationVersion = 1,
            evaluatedAt = evaluatedAt,
            expiresAt = null,
            sampleCount = 4,
            popularCount = 2,
            latestCount = 2,
            searchCount = 0,
            searchSuccessCount = 0,
            likedTitleMatchCount = 0,
            preferredTagMatchCount = 1,
            blockedTagMatchCount = 0,
            explicitSignalCount = 0,
            ecchiSignalCount = 0,
            errorCount = 0,
            qualityScore = 0.5,
            recommendationFitScore = 0.5,
            searchReliabilityScore = 0.0,
            explicitScore = 0.0,
            ecchiScore = 0.0,
            verdict = SourceEvaluationVerdict.NEUTRAL,
            sampledTitlesJson = null,
            sampledTagsJson = null,
            errorMessage = null,
        )

    @Test
    fun `keeps only the newest row for each source`() {
        val rows = SourceMetadataTagDiagnosticsPolicy.latestPerSource(
            listOf(
                evaluation("old", 1L, "First", 10L),
                evaluation("new", 1L, "First", 20L),
                evaluation("other", 2L, "Second", 15L),
            ),
        )

        assertEquals(listOf("new", "other"), rows.map { it.evaluationKey })
    }

    @Test
    fun `uses evaluation key when source id is missing`() {
        val rows = SourceMetadataTagDiagnosticsPolicy.latestPerSource(
            listOf(
                evaluation("extension-a", null, "A", 10L),
                evaluation("extension-b", null, "B", 20L),
            ),
        )

        assertEquals(2, rows.size)
    }

    // KMK Confirmed Blocker Remediation 2026-07-28 -->

    @Test
    fun `empty evaluations produce an empty row list`() {
        val rows = SourceMetadataTagDiagnosticsPolicy.latestPerSource(emptyList())
        assertEquals(emptyList<String>(), rows.map { it.evaluationKey })
    }

    @Test
    fun `two different sources sharing a display name remain distinct rows, not merged`() {
        // Regression for the exact identity risk the plan calls out: grouping must key off
        // sourceId (or the evaluationKey fallback), never off sourceName -- two unrelated sources
        // that happen to render the same display name must never collapse into one row.
        val rows = SourceMetadataTagDiagnosticsPolicy.latestPerSource(
            listOf(
                evaluation("ext-1", 1L, "Reader", 10L),
                evaluation("ext-2", 2L, "Reader", 10L),
            ),
        )
        assertEquals(2, rows.size)
        assertEquals(setOf(1L, 2L), rows.map { it.sourceId }.toSet())
    }

    @Test
    fun `rows are sorted by display name case-insensitively`() {
        val rows = SourceMetadataTagDiagnosticsPolicy.latestPerSource(
            listOf(
                evaluation("z", 1L, "zeta", 10L),
                evaluation("a", 2L, "Alpha", 10L),
                evaluation("m", 3L, "middle", 10L),
            ),
        )
        assertEquals(listOf("Alpha", "middle", "zeta"), rows.map { it.sourceName })
    }

    @Test
    fun `zero enrichment attempts is represented as a plain zero-of-zero count, not a computed ratio`() {
        // SourceMetadataTagDiagnosticsContent's rendering pulls detailEnrichmentSuccessCount and
        // detailEnrichmentAttemptCount directly into an "X of Y" string resource
        // (rec_source_metadata_tag_diagnostics_enrichment) -- there is no division anywhere in that
        // path, so 0/0 cannot produce NaN or a crash. This test pins that both counts are readable,
        // finite integers even when both are zero (the empty-evaluation-history case).
        val eval = evaluation("ext-1", 1L, "Source", 10L)
        assertEquals(0, eval.detailEnrichmentSuccessCount)
        assertEquals(0, eval.detailEnrichmentAttemptCount)
    }
    // KMK <--

    // KMK Final Evidence Closure 2026-07-30 -->
    // Regression coverage for SourceMetadataTagDiagnosticsPolicy.sourceLabelFor, extracted from
    // SourceMetadataTagDiagnosticsContent's row rendering so this privacy branch is directly
    // testable rather than only confirmed by reading the Composable's source.

    @Test
    fun `Evaluation Mode off returns the real source name`() {
        val eval = evaluation("ext-1", 1L, "MangaDex", 10L)
        val label = SourceMetadataTagDiagnosticsPolicy.sourceLabelFor(eval, evaluationModeEnabled = false)
        assertEquals("MangaDex", label)
    }

    @Test
    fun `Evaluation Mode on with a source id never returns the raw source name`() {
        val eval = evaluation("ext-1", 1L, "MangaDex", 10L)
        val label = SourceMetadataTagDiagnosticsPolicy.sourceLabelFor(eval, evaluationModeEnabled = true)
        assertEquals(exh.util.EvaluationModeFormatter.sourceLabel(1L), label)
        assertTrue(label != "MangaDex")
    }

    @Test
    fun `Evaluation Mode on with a missing source id falls back to the evaluation key, still not the raw name`() {
        val eval = evaluation("extension-only", null, "MangaDex", 10L)
        val label = SourceMetadataTagDiagnosticsPolicy.sourceLabelFor(eval, evaluationModeEnabled = true)
        assertEquals(exh.util.EvaluationModeFormatter.sourceLabel("extension-only"), label)
        assertTrue(label != "MangaDex")
    }

    @Test
    fun `sourceLabelFor never leaks the raw source name across every row in a latestPerSource batch`() {
        val rows = SourceMetadataTagDiagnosticsPolicy.latestPerSource(
            listOf(
                evaluation("ext-1", 1L, "MangaDex", 10L),
                evaluation("ext-2", 2L, "Comick", 10L),
                evaluation("ext-3", null, "Batoto", 10L),
            ),
        )
        val rawNames = rows.map { it.sourceName }.toSet()
        for (row in rows) {
            val label = SourceMetadataTagDiagnosticsPolicy.sourceLabelFor(row, evaluationModeEnabled = true)
            assertTrue(label !in rawNames, "leaked a raw source name for ${row.evaluationKey}: $label")
        }
    }
    // KMK <--

    @Test
    fun `query matches structured sampled titles and tags and sorts deterministically`() {
        val rows = SourceMetadataTagDiagnosticsPolicy.queryRows(
            evaluations = listOf(
                evaluation("alpha", 1L, "Alpha", 20L).copy(sampledTitlesJson = "[\"Moon\"]"),
                evaluation("beta", 2L, "Beta", 10L).copy(sampledTagsJson = "[\"Adventure\"]"),
            ),
            query = SourceMetadataTagDiagnosticsPolicy.Query(text = "adventure"),
            evaluationModeEnabled = false,
            now = 30L,
        )

        assertEquals(listOf("beta"), rows.map { it.evaluation.evaluationKey })
    }

    @Test
    fun `query status filter separates outdated and current rows`() {
        val rows = SourceMetadataTagDiagnosticsPolicy.queryRows(
            evaluations = listOf(
                evaluation("old", 1L, "Old", 10L).copy(evaluationVersion = 1),
                evaluation("current", 2L, "Current", 20L).copy(evaluationVersion = 3),
            ),
            query = SourceMetadataTagDiagnosticsPolicy.Query(
                status = SourceMetadataTagDiagnosticsPolicy.StatusFilter.OUTDATED,
            ),
            evaluationModeEnabled = false,
            now = 30L,
        )

        assertEquals(listOf("old"), rows.map { it.evaluation.evaluationKey })
    }

    @Test
    fun `malformed structured payload is ignored without losing the row`() {
        val row = SourceMetadataTagDiagnosticsPolicy.queryRows(
            evaluations = listOf(
                evaluation("broken", 1L, "Broken", 10L).copy(sampledTagsJson = "not-json"),
            ),
            query = SourceMetadataTagDiagnosticsPolicy.Query(text = "broken"),
            evaluationModeEnabled = false,
            now = 30L,
        )

        assertEquals(listOf("broken"), row.map { it.evaluation.evaluationKey })
    }

    @Test
    fun `evaluation mode excludes raw sampled payload from matching`() {
        val rows = SourceMetadataTagDiagnosticsPolicy.queryRows(
            evaluations = listOf(
                evaluation("ext-1", 1L, "Private Source", 10L).copy(
                    sampledTitlesJson = "[\"Sensitive title\"]",
                    catalogueMetadataConfidence = SourceEvaluationMetadataConfidence.HIGH,
                ),
            ),
            query = SourceMetadataTagDiagnosticsPolicy.Query(text = "sensitive title"),
            evaluationModeEnabled = true,
            now = 30L,
        )

        assertTrue(rows.isEmpty())
    }

    // KMK v0.8.21-fix2: the old "every diagnostics row exposes only the existing review route"
    // test asserted Action.REVIEW_SOURCE_EVALUATION/availableActions(), which is removed --
    // availableActions() always returned the same single action regardless of the row (a confirmed
    // usability defect: a repeated, identical, full-row "Open source evaluation" action on every
    // source, navigating to the same generic destination with no source-specific context). See
    // SourceMetadataTagDiagnostics.kt's removal note and Plan A's AUG-03/AUG-12 reopening section.
}
