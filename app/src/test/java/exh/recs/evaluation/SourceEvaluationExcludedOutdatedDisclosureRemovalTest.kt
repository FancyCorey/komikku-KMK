package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK v0.8.20-fix1 -->
/**
 * Regression tests for the post-remediation fix: SourceEvaluationScreen no longer renders a
 * pre-run "N skipped source(s)" disclosure warning next to the "Reassess outdated" action. The
 * underlying eligibility computation ([SourceEvaluationOutdatedReconciliation],
 * [SourceEvaluationCandidateQueuePolicy]) is unchanged and already has thorough coverage in
 * [SourceEvaluationOutdatedReconciliationTest] -- these tests pin the specific contract the removed
 * UI depended on being true: excluded sources never inflate the primary action's count, and zero
 * actionable candidates means the button itself has nothing to show (`state.staleCandidates`, which
 * [exh.recs.evaluation.SourceEvaluationScreen] gates the "stale_reassess_button" item on, is empty).
 *
 * The removed pre-run disclosure is enforced by the screen's current static contract; this test
 * keeps the user-facing eligibility behavior covered without referring to deleted state symbols.
 */
class SourceEvaluationExcludedOutdatedDisclosureRemovalTest {

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

    private fun makeEval(signatureHash: String, pkgName: String, expiresAt: Long?) = SourceEvaluation(
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
        evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
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
    ) = SourceEvaluationCandidateFilter.buildPool(
        available = available,
        installedPkgNames = installedPkgNames,
        untrustedPkgNames = emptySet(),
        recLanguages = setOf("en"),
        nsfwEnabled = true,
        blockExplicit = false,
        dislikedKeys = emptySet(),
        evaluations = evaluations,
    )

    @Test
    fun `all outdated sources ineligible yields an empty actionable queue -- the reassess button has nothing to show`() {
        val ext = makeExt("com.test.installed", "sig1")
        val eval = makeEval("sig1", "com.test.installed", expiresAt = now - 1L)
        val pool = buildPool(listOf(ext), listOf(eval), installedPkgNames = setOf("com.test.installed"))

        val staleCandidates = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        val reconciliation = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval), pool, now)

        assertTrue(staleCandidates.isEmpty(), "SourceEvaluationScreen gates \"stale_reassess_button\" on staleCandidates.isNotEmpty() -- this must be empty so the button is hidden")
        assertEquals(1, reconciliation.totalOutdatedCount, "the source is still genuinely outdated -- just not actionable")
        assertEquals(0, reconciliation.workableOutdatedCount)
    }

    @Test
    fun `some outdated actionable and some ineligible -- the queue contains only the actionable one`() {
        val actionableExt = makeExt("com.test.actionable", "sigA")
        val actionableEval = makeEval("sigA", "com.test.actionable", expiresAt = now - 1L)
        val excludedExt = makeExt("com.test.excluded", "sigB")
        val excludedEval = makeEval("sigB", "com.test.excluded", expiresAt = now - 1L)
        val pool = buildPool(
            listOf(actionableExt, excludedExt),
            listOf(actionableEval, excludedEval),
            installedPkgNames = setOf("com.test.excluded"),
        )

        val staleCandidates = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)

        assertEquals(1, staleCandidates.size, "excluded sources must never inflate the reassessment queue/count")
        assertEquals("com.test.actionable", staleCandidates.single().extension.pkgName)
    }

    @Test
    fun `all outdated sources actionable -- the queue count matches exactly`() {
        val ext1 = makeExt("com.test.a", "sig1")
        val eval1 = makeEval("sig1", "com.test.a", expiresAt = now - 1L)
        val ext2 = makeExt("com.test.b", "sig2")
        val eval2 = makeEval("sig2", "com.test.b", expiresAt = now - 1L)
        val pool = buildPool(listOf(ext1, ext2), listOf(eval1, eval2))

        val staleCandidates = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        val reconciliation = SourceEvaluationOutdatedReconciliation.reconcile(listOf(eval1, eval2), pool, now)

        assertEquals(2, staleCandidates.size)
        assertEquals(reconciliation.totalOutdatedCount, reconciliation.workableOutdatedCount, "nothing excluded -- workable must equal total")
    }

    @Test
    fun `zero outdated sources at all also yields an empty actionable queue`() {
        val pool = buildPool(emptyList(), emptyList())
        val staleCandidates = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        assertTrue(staleCandidates.isEmpty())
    }

    @Test
    fun `excluded sources never inflate the primary reassess action's count across a larger mixed set`() {
        val extensions = mutableListOf<Extension.Available>()
        val evaluations = mutableListOf<SourceEvaluation>()
        val installed = mutableSetOf<String>()

        repeat(12) { i ->
            val pkg = "com.test.actionable$i"
            val sig = "siga$i"
            extensions.add(makeExt(pkg, sig))
            evaluations.add(makeEval(sig, pkg, expiresAt = now - 1L))
        }
        repeat(8) { i ->
            val pkg = "com.test.excluded$i"
            val sig = "sige$i"
            extensions.add(makeExt(pkg, sig))
            evaluations.add(makeEval(sig, pkg, expiresAt = now - 1L))
            installed.add(pkg)
        }

        val pool = buildPool(extensions, evaluations, installedPkgNames = installed)
        val staleCandidates = SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)
        val reconciliation = SourceEvaluationOutdatedReconciliation.reconcile(evaluations, pool, now)

        assertEquals(12, staleCandidates.size, "the 8 excluded (installed) sources must not appear in the actionable queue/count")
        assertEquals(12, reconciliation.workableOutdatedCount)
        assertEquals(20, reconciliation.totalOutdatedCount)
    }

    @Test
    fun `a successful reassessment run's completion state reports the actual actionable count, not a false all-clear`() {
        // Mirrors SourceEvaluationStaleCompletionDisplayPolicyTest's own coverage of this exact
        // contract (kept in sync here since this fix's requirement explicitly calls it out):
        // after a run exhausts the actionable queue while unreachable rows remain, the completion
        // state must be the honest "completed, N still excluded" variant, never a plain "complete".
        val result = SourceEvaluationStaleCompletionDisplayPolicy.evaluate(
            staleCandidatesEmpty = true,
            continuationCursorStaleIsSet = true,
            isRunning = false,
            hasUnreachableOutdated = true,
        )
        assertEquals(SourceEvaluationStaleCompletionDisplayPolicy.DisplayState.CompletedWithUnreachableRemaining, result)
    }
}
// KMK <--
