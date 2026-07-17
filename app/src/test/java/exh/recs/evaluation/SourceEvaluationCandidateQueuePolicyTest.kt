package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK --> v0.8.1-fix3
class SourceEvaluationCandidateQueuePolicyTest {

    private val now = 1_000_000L

    private fun makeExt(
        pkgName: String,
        signatureHash: String,
        lang: String = "en",
    ) = Extension.Available(
        name = "TestExt",
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = lang,
        isNsfw = false,
        signatureHash = signatureHash,
        storeName = "test-repo",
        sources = emptyList(),
        apkUrl = "https://test-repo.example.com/apk/$pkgName.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "",
            name = "test-repo",
            badgeLabel = "test-repo",
            signingKey = signatureHash,
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
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

    private fun buildPool(available: List<Extension.Available>, evaluations: List<SourceEvaluation>) =
        SourceEvaluationCandidateFilter.buildPool(
            available = available,
            installedPkgNames = emptySet(),
            untrustedPkgNames = emptySet(),
            recLanguages = setOf("en"),
            nsfwEnabled = true,
            blockExplicit = false,
            dislikedKeys = emptySet(),
            evaluations = evaluations,
        )

    @Test
    fun `unassessed extension is not in the stale queue`() {
        val ext = makeExt("eu.kanade.tachiyomi.extension.en.new", "sig1")
        val pool = buildPool(listOf(ext), emptyList())
        assertTrue(SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now).isEmpty())
    }

    @Test
    fun `expired evaluation puts extension in the stale queue`() {
        val ext = makeExt("eu.kanade.tachiyomi.extension.en.expired", "sig1")
        val eval = makeEval("sig1", "eu.kanade.tachiyomi.extension.en.expired", expiresAt = now - 1L)
        val pool = buildPool(listOf(ext), listOf(eval))
        val stale = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        assertEquals(1, stale.size)
        assertEquals("eu.kanade.tachiyomi.extension.en.expired", stale.first().extension.pkgName)
    }

    @Test
    fun `outdated evaluationVersion puts extension in the stale queue even without expiry`() {
        val ext = makeExt("eu.kanade.tachiyomi.extension.en.outdated", "sig1")
        val eval = makeEval(
            "sig1",
            "eu.kanade.tachiyomi.extension.en.outdated",
            expiresAt = now + 100_000L,
            evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION - 1,
        )
        val pool = buildPool(listOf(ext), listOf(eval))
        val stale = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        assertEquals(1, stale.size)
    }

    @Test
    fun `current fresh evaluation is not in the stale queue`() {
        val ext = makeExt("eu.kanade.tachiyomi.extension.en.current", "sig1")
        val eval = makeEval("sig1", "eu.kanade.tachiyomi.extension.en.current", expiresAt = now + 100_000L)
        val pool = buildPool(listOf(ext), listOf(eval))
        assertTrue(SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now).isEmpty())
    }

    @Test
    fun `only one of two evaluations must be stale is not enough - all must be stale`() {
        // isStale() requires ALL evaluations for the extension to be stale; mirror that contract here.
        val ext = makeExt("eu.kanade.tachiyomi.extension.en.mixed", "sig1")
        val staleEval = makeEval("sig1", "eu.kanade.tachiyomi.extension.en.mixed", expiresAt = now - 1L)
        val freshEval = makeEval("sig1", "eu.kanade.tachiyomi.extension.en.mixed", expiresAt = now + 100_000L)
        val pool = buildPool(listOf(ext), listOf(staleEval, freshEval))
        assertTrue(SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now).isEmpty())
    }

    @Test
    fun `stale queue includes candidates regardless of skipAlreadyEvaluated option`() {
        // The whole point of the fix: a row does not disappear from BOTH queues.
        val ext = makeExt("eu.kanade.tachiyomi.extension.en.stale2", "sig2")
        val eval = makeEval("sig2", "eu.kanade.tachiyomi.extension.en.stale2", expiresAt = now - 1L)
        val pool = buildPool(listOf(ext), listOf(eval))

        // Default unassessed-queue options (skipAlreadyEvaluated=true, reEvaluateStale=false) hide it:
        val unassessed = SourceEvaluationCandidateFilter.applyOptions(
            pool = pool,
            includeExplicit = false,
            skipAlreadyEvaluated = true,
            reEvaluateStale = false,
            now = now,
        )
        assertTrue(unassessed.candidates.isEmpty())
        assertEquals(1, unassessed.evaluatedHiddenCount)

        // But the stale queue still surfaces it as actionable:
        val stale = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        assertEquals(1, stale.size)
    }

    // KMK v0.8.1-fix4: exact regression scenario from the fix4 plan -- unassessed queue empty,
    // stale queue non-empty, must still be actionable (the ScreenModel gates "Reassess outdated" on
    // staleCandidates().isNotEmpty(), independent of the unassessed queue being exhausted).

    @Test
    fun `unassessed queue exhausted does not prevent stale queue from being actionable`() {
        val unassessedExt = makeExt("eu.kanade.tachiyomi.extension.en.done", "sigdone")
        val currentEval = makeEval("sigdone", "eu.kanade.tachiyomi.extension.en.done", expiresAt = now + 100_000L)
        val staleExt = makeExt("eu.kanade.tachiyomi.extension.en.needsredo", "sigstale")
        val staleEval = makeEval("sigstale", "eu.kanade.tachiyomi.extension.en.needsredo", expiresAt = now - 1L)
        val pool = buildPool(listOf(unassessedExt, staleExt), listOf(currentEval, staleEval))

        val unassessed = SourceEvaluationCandidateFilter.applyOptions(
            pool = pool,
            includeExplicit = false,
            skipAlreadyEvaluated = true,
            reEvaluateStale = false,
            now = now,
        )
        assertTrue(unassessed.candidates.isEmpty(), "unassessed queue should be exhausted -- both extensions have evaluations")

        val stale = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        assertEquals(1, stale.size)
        assertEquals("eu.kanade.tachiyomi.extension.en.needsredo", stale.first().extension.pkgName)
    }
}
// KMK <--
