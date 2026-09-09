package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TasteProfile

/**
 * Tests for [SourceEvaluationEvidenceSummaryPolicy] (v0.8.1-fix1).
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.SourceEvaluationEvidenceSummaryPolicyTest"
 */
class SourceEvaluationEvidenceSummaryPolicyTest {

    private fun manga(genres: List<String>?, id: Long = 1L): Manga =
        Manga.create().copy(id = id, ogTitle = "Manga $id", ogGenre = genres, source = 1L, url = "/m/$id")

    private fun evaluation(
        catalogueSamples: List<Manga>,
        tasteProfile: TasteProfile = TasteProfile(emptyMap(), emptyMap(), emptyMap(), emptySet()),
        errorCount: Int = 0,
        evaluationVersion: Int? = null,
    ): SourceEvaluation {
        val scored = SourceEvaluationScorer.score(
            extensionName = "Test Extension",
            pkgName = "eu.kanade.test",
            signatureHash = "sig",
            sourceId = 1L,
            sourceName = "Test Source",
            lang = "en",
            baseUrl = null,
            repoName = null,
            sourceCount = 1,
            isNsfw = false,
            catalogueSamples = catalogueSamples,
            popularCount = catalogueSamples.size,
            latestCount = 0,
            errorCount = errorCount,
            tasteProfile = tasteProfile,
        )
        return if (evaluationVersion != null) scored.copy(evaluationVersion = evaluationVersion) else scored
    }

    @Test
    fun `current row with samples returns evidence counts`() {
        val eval = evaluation(listOf(manga(listOf("action"), id = 1L)))
        val displayState = SourceEvaluationDisplayPolicy.state(eval)
        val evidence = SourceEvaluationEvidenceSummaryPolicy.evidenceFor(eval, displayState)
        assertNotNull(evidence)
        assertEquals(1, evidence!!.sampleCount)
        assertEquals(eval.popularCount, evidence.popularCount)
        assertEquals(eval.latestCount, evidence.latestCount)
    }

    @Test
    fun `evidence preserves popular and latest sample split`() {
        val eval = evaluation(listOf(manga(listOf("action"), id = 1L))).copy(
            popularCount = 4,
            latestCount = 3,
        )
        val evidence = SourceEvaluationEvidenceSummaryPolicy.evidenceFor(
            eval,
            SourceEvaluationDisplayPolicy.state(eval),
        )
        assertNotNull(evidence)
        assertEquals(4, evidence!!.popularCount)
        assertEquals(3, evidence.latestCount)
    }

    @Test
    fun `negative catalogue counts are clamped for defensive presentation`() {
        val eval = evaluation(listOf(manga(listOf("action"), id = 1L))).copy(
            popularCount = -2,
            latestCount = -1,
        )
        val evidence = SourceEvaluationEvidenceSummaryPolicy.evidenceFor(
            eval,
            SourceEvaluationDisplayPolicy.state(eval),
        )
        assertNotNull(evidence)
        assertEquals(0, evidence!!.popularCount)
        assertEquals(0, evidence.latestCount)
    }

    @Test
    fun `outdated row returns null — stale counters must not be shown as current evidence`() {
        val eval = evaluation(listOf(manga(listOf("action"), id = 1L)), evaluationVersion = 1)
        val displayState = SourceEvaluationDisplayPolicy.state(eval)
        assertEquals(SourceEvaluationDisplayPolicy.SourceEvaluationDisplayState.OUTDATED_VERSION, displayState)
        assertNull(SourceEvaluationEvidenceSummaryPolicy.evidenceFor(eval, displayState))
    }

    @Test
    fun `error row returns null — no catalogue samples were collected`() {
        val eval = evaluation(emptyList(), errorCount = 1)
        assertEquals(SourceEvaluationVerdict.ERROR, eval.verdict)
        val displayState = SourceEvaluationDisplayPolicy.state(eval)
        assertNull(SourceEvaluationEvidenceSummaryPolicy.evidenceFor(eval, displayState))
    }

    @Test
    fun `zero-sample row returns null`() {
        val eval = evaluation(emptyList(), errorCount = 0)
        val displayState = SourceEvaluationDisplayPolicy.state(eval)
        assertNull(SourceEvaluationEvidenceSummaryPolicy.evidenceFor(eval, displayState))
    }

    @Test
    fun `positive negative blocked adult counts map correctly onto the evidence summary`() {
        val positive = manga(listOf("action"), id = 1L)
        val blocked = manga(listOf("yaoi"), id = 2L)
        val profile = TasteProfile(
            learnedTagWeights = emptyMap(),
            explicitTagPreferences = mapOf("action" to TagPreference.PREFER.value),
            sourceAffinity = emptyMap(),
            // No aliasMap is passed to SourceEvaluationScorer.score() below, so "yaoi" resolves to
            // its own raw normalized group key ("yaoi"), not the aliased "boys_love" group.
            blockedGroups = setOf("yaoi"),
        )
        val eval = evaluation(
            listOf(positive, blocked),
            tasteProfile = profile,
        )
        val displayState = SourceEvaluationDisplayPolicy.state(eval)
        val evidence = SourceEvaluationEvidenceSummaryPolicy.evidenceFor(eval, displayState)
        assertNotNull(evidence)
        assertEquals(eval.positiveCandidateCount, evidence!!.positiveCandidateCount)
        assertEquals(eval.negativeCandidateCount, evidence.negativeCandidateCount)
        assertEquals(eval.blockedCandidateCount, evidence.blockedCandidateCount)
        assertEquals(eval.adultSignalCandidateCount, evidence.adultSignalCandidateCount)
        assertTrue(evidence.blockedCandidateCount >= 1)
    }

    @Test
    fun `NEEDS_MANUAL_REVIEW verdict is flagged in the evidence summary`() {
        // Many metadata-sparse samples -> NEEDS_MANUAL_REVIEW per SourceEvaluationScorer's verdict order.
        val eval = evaluation((1L..10L).map { manga(null, id = it) })
        assertEquals(SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW, eval.verdict)
        val displayState = SourceEvaluationDisplayPolicy.state(eval)
        val evidence = SourceEvaluationEvidenceSummaryPolicy.evidenceFor(eval, displayState)
        assertNotNull(evidence)
        assertTrue(evidence!!.isManualReview)
    }

    @Test
    fun `detailEnrichmentFailedCount is attempts minus successes`() {
        val eval = evaluation(listOf(manga(listOf("action"), id = 1L)))
            .copy(detailEnrichmentAttemptCount = 5, detailEnrichmentSuccessCount = 3)
        val displayState = SourceEvaluationDisplayPolicy.state(eval)
        val evidence = SourceEvaluationEvidenceSummaryPolicy.evidenceFor(eval, displayState)
        assertEquals(2, evidence!!.detailEnrichmentFailedCount)
    }
}
