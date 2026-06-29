package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
class SourceEvaluationDisplayFilterTest {

    private fun makeEval(sig: String, pkg: String): SourceEvaluation = SourceEvaluation(
        evaluationKey = "$sig|$pkg|1",
        sourceId = 1L,
        extensionPkgName = pkg,
        signatureHash = sig,
        extensionName = "Test Extension",
        sourceName = "Test Source",
        lang = "en",
        baseUrl = "https://example.com",
        repoName = "test-repo",
        sourceCount = 1,
        isNsfw = false,
        evaluationVersion = 1,
        evaluatedAt = 1_000_000L,
        expiresAt = null,
        sampleCount = 5,
        popularCount = 2,
        latestCount = 2,
        searchCount = 1,
        searchSuccessCount = 1,
        likedTitleMatchCount = 0,
        preferredTagMatchCount = 2,
        blockedTagMatchCount = 0,
        explicitSignalCount = 0,
        ecchiSignalCount = 0,
        errorCount = 0,
        qualityScore = 0.7,
        recommendationFitScore = 0.75,
        searchReliabilityScore = 1.0,
        explicitScore = 0.0,
        ecchiScore = 0.0,
        verdict = SourceEvaluationVerdict.STRONG_FIT,
        sampledTitlesJson = null,
        sampledTagsJson = null,
        errorMessage = null,
    )

    @Test
    fun `showInstalled false hides installed evaluations by default`() {
        val eval = makeEval("abc", "eu.pkg.one")
        val installedKeys = setOf("abc|eu.pkg.one")
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = listOf(eval),
            installedExtensionKeys = installedKeys,
            showInstalled = false,
        )
        assertTrue(result.visible.isEmpty())
        assertEquals(1, result.hiddenInstalledCount)
    }

    @Test
    fun `showInstalled true reveals installed evaluations`() {
        val eval = makeEval("abc", "eu.pkg.one")
        val installedKeys = setOf("abc|eu.pkg.one")
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = listOf(eval),
            installedExtensionKeys = installedKeys,
            showInstalled = true,
        )
        assertEquals(1, result.visible.size)
        assertEquals(0, result.hiddenInstalledCount)
    }

    @Test
    fun `non-installed evaluations are always visible`() {
        val eval = makeEval("abc", "eu.pkg.one")
        val installedKeys = setOf("xyz|eu.pkg.other")
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = listOf(eval),
            installedExtensionKeys = installedKeys,
            showInstalled = false,
        )
        assertEquals(1, result.visible.size)
        assertEquals(0, result.hiddenInstalledCount)
    }

    @Test
    fun `empty installedExtensionKeys shows all evaluations`() {
        val evals = listOf(makeEval("a", "pkg.a"), makeEval("b", "pkg.b"))
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = evals,
            installedExtensionKeys = emptySet(),
            showInstalled = false,
        )
        assertEquals(2, result.visible.size)
        assertEquals(0, result.hiddenInstalledCount)
    }

    @Test
    fun `hiddenInstalledCount matches number of hidden evaluations`() {
        val evals = listOf(
            makeEval("a", "pkg.a"),
            makeEval("b", "pkg.b"),
            makeEval("c", "pkg.c"),
        )
        val installedKeys = setOf("a|pkg.a", "c|pkg.c")
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = evals,
            installedExtensionKeys = installedKeys,
            showInstalled = false,
        )
        assertEquals(1, result.visible.size)
        assertEquals(2, result.hiddenInstalledCount)
        assertEquals("b", result.visible.first().signatureHash)
    }

    @Test
    fun `evaluation with blank extensionKey is never treated as installed`() {
        // extensionKey = "$signatureHash|$extensionPkgName" — blank sig produces weird key
        val evalBlankSig = makeEval("", "eu.pkg.blank")
        val installedKeys = setOf("|eu.pkg.blank", "eu.pkg.blank")
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = listOf(evalBlankSig),
            installedExtensionKeys = installedKeys,
            showInstalled = false,
        )
        // extensionKey with blank sig is "|eu.pkg.blank" — filter checks key.isNotBlank()
        // Since "|eu.pkg.blank" is not blank, and it IS in the set, it should be hidden
        assertEquals(1, result.hiddenInstalledCount)
    }

    @Test
    fun `filtering multiple evaluations with partial overlap`() {
        val evals = (1..6).map { makeEval("sig$it", "pkg$it") }
        val installedKeys = setOf("sig2|pkg2", "sig4|pkg4", "sig6|pkg6")
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = evals,
            installedExtensionKeys = installedKeys,
            showInstalled = false,
        )
        assertEquals(3, result.visible.size)
        assertEquals(3, result.hiddenInstalledCount)
        val visibleSigs = result.visible.map { it.signatureHash }.toSet()
        assertEquals(setOf("sig1", "sig3", "sig5"), visibleSigs)
    }

    // KMK --> v0.7.7: toggle behavior tests

    @Test
    fun `toggling showInstalled true then false hides installed rows immediately`() {
        val eval = makeEval("abc", "eu.pkg.one")
        val installedKeys = setOf("abc|eu.pkg.one")
        // Start with showInstalled=true — row is visible
        val resultVisible = SourceEvaluationDisplayFilter.filter(
            evaluations = listOf(eval),
            installedExtensionKeys = installedKeys,
            showInstalled = true,
        )
        assertEquals(1, resultVisible.visible.size)
        assertEquals(0, resultVisible.hiddenInstalledCount)

        // Toggle to showInstalled=false — row should be hidden immediately (same call, new value)
        val resultHidden = SourceEvaluationDisplayFilter.filter(
            evaluations = listOf(eval),
            installedExtensionKeys = installedKeys,
            showInstalled = false,
        )
        assertTrue(resultHidden.visible.isEmpty())
        assertEquals(1, resultHidden.hiddenInstalledCount)
    }

    @Test
    fun `hiddenInstalledCount is zero when showInstalled is true`() {
        val evals = listOf(makeEval("a", "pkg.a"), makeEval("b", "pkg.b"))
        val installedKeys = setOf("a|pkg.a")
        val result = SourceEvaluationDisplayFilter.filter(
            evaluations = evals,
            installedExtensionKeys = installedKeys,
            showInstalled = true,
        )
        assertEquals(2, result.visible.size)
        assertEquals(0, result.hiddenInstalledCount)
    }

    @Test
    fun `hiddenInstalledCount updates correctly when showInstalled changes`() {
        val evals = (1..4).map { makeEval("sig$it", "pkg$it") }
        val installedKeys = setOf("sig1|pkg1", "sig3|pkg3")
        val hiddenResult = SourceEvaluationDisplayFilter.filter(
            evaluations = evals,
            installedExtensionKeys = installedKeys,
            showInstalled = false,
        )
        assertEquals(2, hiddenResult.hiddenInstalledCount)
        val shownResult = SourceEvaluationDisplayFilter.filter(
            evaluations = evals,
            installedExtensionKeys = installedKeys,
            showInstalled = true,
        )
        assertEquals(0, shownResult.hiddenInstalledCount)
    }
    // KMK <-- v0.7.7
}
// KMK <--
