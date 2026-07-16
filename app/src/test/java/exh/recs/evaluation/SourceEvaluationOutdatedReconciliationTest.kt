package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK v0.8.8 -->
/**
 * Tests for [SourceEvaluationOutdatedReconciliation] — the root-cause fix for "visible Outdated rows
 * lead to zero reassessment candidates." See that class's doc for the full traced pipeline.
 */
class SourceEvaluationOutdatedReconciliationTest {

    private val now = 1_000_000L

    private fun makeExt(pkgName: String, signatureHash: String, lang: String = "en") = Extension.Available(
        name = "TestExt",
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = lang,
        isNsfw = false,
        signatureHash = signatureHash,
        repoName = "test-repo",
        sources = emptyList(),
        apkName = "$pkgName.apk",
        iconUrl = "",
        repoUrl = "",
    )

    private fun makeEval(
        signatureHash: String,
        pkgName: String,
        expiresAt: Long?,
        evaluationVersion: Int = SourceEvaluationKeys.CURRENT_VERSION,
    ) = SourceEvaluation(
        evaluationKey = "$signatureHash|$pkgName",
        sourceId = null,
        extensionPkgName = pkgName,
        signatureHash = signatureHash,
        extensionName = "TestExt",
        sourceName = "TestExt",
        lang = "en",
        baseUrl = null,
        repoName = null,
        sourceCount = 1,
        isNsfw = false,
        evaluationVersion = evaluationVersion,
        evaluatedAt = now - 1000L,
        expiresAt = expiresAt,
        sampleCount = 10,
        popularCount = 5,
        latestCount = 5,
        searchCount = 3,
        searchSuccessCount = 2,
        likedTitleMatchCount = 0,
        preferredTagMatchCount = 0,
        blockedTagMatchCount = 0,
        explicitSignalCount = 0,
        ecchiSignalCount = 0,
        errorCount = 0,
        qualityScore = 0.5,
        recommendationFitScore = 0.5,
        searchReliabilityScore = 0.5,
        explicitScore = 0.0,
        ecchiScore = 0.0,
        verdict = SourceEvaluationVerdict.NEUTRAL,
        sampledTitlesJson = null,
        sampledTagsJson = null,
        errorMessage = null,
    )

    private fun buildPool(
        available: List<Extension.Available>,
        evaluations: List<SourceEvaluation>,
        installedPkgNames: Set<String> = emptySet(),
        recLanguages: Set<String> = setOf("en"),
        dislikedKeys: Set<String> = emptySet(),
    ) = SourceEvaluationCandidateFilter.buildPool(
        available = available,
        installedPkgNames = installedPkgNames,
        untrustedPkgNames = emptySet(),
        recLanguages = recLanguages,
        nsfwEnabled = true,
        blockExplicit = false,
        dislikedKeys = dislikedKeys,
        evaluations = evaluations,
    )

    @Test
    fun `no evaluations means nothing outdated`() {
        val result = SourceEvaluationOutdatedReconciliation.reconcile(emptyList(), buildPool(emptyList(), emptyList()), now)
        assertEquals(0, result.totalOutdatedCount)
        assertEquals(0, result.workableOutdatedCount)
        assertFalse(result.allOutdatedAreUnreachable)
    }

    @Test
    fun `a current (non-stale) evaluation is not counted as outdated`() {
        val ext = makeExt("com.test.current", "sig1")
        val eval = makeEval("sig1", "com.test.current", expiresAt = now + 100_000L)
        val pool = buildPool(listOf(ext), listOf(eval))
        val result = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval), pool, now)
        assertEquals(0, result.totalOutdatedCount)
    }

    @Test
    fun `an outdated evaluation for a still-eligible extension is workable`() {
        val ext = makeExt("com.test.outdated", "sig1")
        val eval = makeEval("sig1", "com.test.outdated", expiresAt = now - 1L)
        val pool = buildPool(listOf(ext), listOf(eval))
        val result = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval), pool, now)
        assertEquals(1, result.totalOutdatedCount)
        assertEquals(1, result.workableOutdatedCount)
        assertEquals(0, result.unreachableOutdatedCount)
        assertFalse(result.allOutdatedAreUnreachable)
    }

    // --- The exact bug scenario: outdated row visible, but extension no longer in the pool ---

    @Test
    fun `ROOT CAUSE - an outdated evaluation for a now-installed extension is unreachable, not silently dropped`() {
        val ext = makeExt("com.test.nowinstalled", "sig1")
        val eval = makeEval("sig1", "com.test.nowinstalled", expiresAt = now - 1L)
        // The extension is now installed, so buildPool excludes it from allEligible even though its
        // stale evaluation row is still present and still displays "Outdated -- reassess needed."
        val pool = buildPool(listOf(ext), listOf(eval), installedPkgNames = setOf("com.test.nowinstalled"))
        val result = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval), pool, now)
        assertEquals(1, result.totalOutdatedCount)
        assertEquals(0, result.workableOutdatedCount)
        assertEquals(1, result.unreachableOutdatedCount)
        assertTrue(result.allOutdatedAreUnreachable, "this is exactly the reported bug scenario -- must be flagged, not silently zero")
    }

    @Test
    fun `an outdated evaluation for a now-language-filtered extension is unreachable`() {
        val ext = makeExt("com.test.jp", "sig1", lang = "ja")
        val eval = makeEval("sig1", "com.test.jp", expiresAt = now - 1L)
        // recLanguages no longer includes "ja" -- the user changed their language filter since evaluating.
        val pool = buildPool(listOf(ext), listOf(eval), recLanguages = setOf("en"))
        val result = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval), pool, now)
        assertTrue(result.allOutdatedAreUnreachable)
    }

    @Test
    fun `an outdated evaluation for a now-disliked extension is unreachable`() {
        val ext = makeExt("com.test.disliked", "sig1")
        val eval = makeEval("sig1", "com.test.disliked", expiresAt = now - 1L)
        val pool = buildPool(listOf(ext), listOf(eval), dislikedKeys = setOf("a|sig1|com.test.disliked"))
        val result = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval), pool, now)
        assertTrue(result.allOutdatedAreUnreachable)
    }

    @Test
    fun `an extension no longer in the available extensions list at all is unreachable`() {
        val eval = makeEval("sigremoved", "com.test.removed", expiresAt = now - 1L)
        // Not present in `available` at all (repo no longer offers it) -- pool.allEligible is empty.
        val pool = buildPool(emptyList(), listOf(eval))
        val result = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval), pool, now)
        assertEquals(1, result.totalOutdatedCount)
        assertTrue(result.allOutdatedAreUnreachable)
    }

    // --- Partial reachability (mixed workable + unreachable) ---

    @Test
    fun `a mix of workable and unreachable outdated rows reports both counts correctly`() {
        val workableExt = makeExt("com.test.workable", "sig1")
        val workableEval = makeEval("sig1", "com.test.workable", expiresAt = now - 1L)
        val unreachableExt = makeExt("com.test.unreachable", "sig2")
        val unreachableEval = makeEval("sig2", "com.test.unreachable", expiresAt = now - 1L)

        val pool = buildPool(
            listOf(workableExt, unreachableExt),
            listOf(workableEval, unreachableEval),
            installedPkgNames = setOf("com.test.unreachable"),
        )
        val result = SourceEvaluationOutdatedReconciliation.reconcile(listOf(workableEval, unreachableEval), pool, now)
        assertEquals(2, result.totalOutdatedCount)
        assertEquals(1, result.workableOutdatedCount)
        assertEquals(1, result.unreachableOutdatedCount)
        assertFalse(result.allOutdatedAreUnreachable, "not ALL are unreachable -- one is workable")
    }

    // --- Outdated by version (no expiry) also participates in reconciliation ---

    @Test
    fun `an outdated-by-version evaluation (no expiry) is correctly reconciled too`() {
        val ext = makeExt("com.test.oldversion", "sig1")
        val eval = makeEval("sig1", "com.test.oldversion", expiresAt = now + 100_000L, evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION - 1)
        val pool = buildPool(listOf(ext), listOf(eval), installedPkgNames = setOf("com.test.oldversion"))
        val result = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval), pool, now)
        assertTrue(result.allOutdatedAreUnreachable)
    }

    // --- 250+ synthetic candidates across multiple batches (Phase 4 test requirement) ---

    @Test
    fun `250 synthetic sources - mixed current, workable-outdated, and unreachable-outdated, correctly reconciled`() {
        val extensions = mutableListOf<Extension.Available>()
        val evaluations = mutableListOf<SourceEvaluation>()
        val installed = mutableSetOf<String>()

        // 100 current (not outdated) -- must not be counted.
        repeat(100) { i ->
            val pkg = "com.test.current$i"
            val sig = "sigcur$i"
            extensions.add(makeExt(pkg, sig))
            evaluations.add(makeEval(sig, pkg, expiresAt = now + 100_000L))
        }
        // 100 outdated and still eligible (workable).
        repeat(100) { i ->
            val pkg = "com.test.workable$i"
            val sig = "sigwork$i"
            extensions.add(makeExt(pkg, sig))
            evaluations.add(makeEval(sig, pkg, expiresAt = now - 1L))
        }
        // 50 outdated but now installed (unreachable) -- the exact bug scenario, at scale.
        repeat(50) { i ->
            val pkg = "com.test.installed$i"
            val sig = "siginst$i"
            extensions.add(makeExt(pkg, sig))
            evaluations.add(makeEval(sig, pkg, expiresAt = now - 1L))
            installed.add(pkg)
        }

        assertEquals(250, extensions.size)

        val pool = buildPool(extensions, evaluations, installedPkgNames = installed)
        val result = SourceEvaluationOutdatedReconciliation.reconcile(evaluations, pool, now)

        assertEquals(150, result.totalOutdatedCount, "100 workable + 50 unreachable = 150 outdated; 100 current excluded")
        assertEquals(100, result.workableOutdatedCount)
        assertEquals(50, result.unreachableOutdatedCount)
        assertFalse(result.allOutdatedAreUnreachable, "100 are workable, so not ALL are unreachable")

        // The actual reassessment queue (what "Continue reassessing outdated" will process) must
        // agree exactly with workableOutdatedCount -- this is the count/work-query agreement the
        // plan requires.
        val staleCandidates = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        assertEquals(result.workableOutdatedCount, staleCandidates.size, "displayed workable count and the actual launched-work candidate list must agree")

        // Continuation across multiple batches (e.g. batch size 25) must eventually cover every
        // workable candidate exactly once, with stable ordering and no duplicates.
        var cursor: SourceEvaluationCursor? = null
        val fingerprint = "test-fingerprint"
        val processedInOrder = mutableListOf<String>()
        var iterations = 0
        while (true) {
            val slice = SourceEvaluationContinuationPolicy.sliceForRun(staleCandidates, batchSize = 25, cursor = cursor, currentFingerprint = fingerprint, now = now)
            if (slice.isEmpty()) break
            val keys = slice.map { SourceEvaluationContinuationPolicy.candidateKey(it) }.toSet()
            processedInOrder += keys
            cursor = SourceEvaluationContinuationPolicy.advanceCursor(cursor, keys, staleCandidates, fingerprint, now)
            iterations++
            assertTrue(iterations <= 10, "continuation looping past a sane batch count -- possible restart-from-beginning regression")
        }
        assertEquals(100, processedInOrder.size, "every workable candidate processed exactly once across batches")
        assertEquals(processedInOrder.size, processedInOrder.toSet().size, "no duplicate processing across batches")
        assertEquals(4, iterations, "100 candidates at batch size 25 should take exactly 4 batches, not restart or skip")
    }
}
// KMK <--
