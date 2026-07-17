package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
class SourceEvaluationCandidateFilterTest {

    private val now = 1_000_000L

    private fun makeExt(
        name: String = "TestExt",
        pkgName: String = "eu.kanade.tachiyomi.extension.en.test",
        lang: String = "en",
        isNsfw: Boolean = false,
        signatureHash: String = "abc123",
        repoName: String = "test-repo",
    ) = Extension.Available(
        name = name,
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = lang,
        isNsfw = isNsfw,
        signatureHash = signatureHash,
        storeName = repoName,
        sources = emptyList(),
        apkUrl = "https://test-repo.example.com/apk/$pkgName.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "",
            name = repoName,
            badgeLabel = repoName,
            signingKey = signatureHash,
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    private fun makeEval(
        signatureHash: String = "abc123",
        pkgName: String = "eu.kanade.tachiyomi.extension.en.test",
        expiresAt: Long? = now + 100_000L,
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
        installedPkgNames: Set<String> = emptySet(),
        untrustedPkgNames: Set<String> = emptySet(),
        recLanguages: Set<String> = setOf("en"),
        nsfwEnabled: Boolean = true,
        blockExplicit: Boolean = false,
        dislikedKeys: Set<String> = emptySet(),
        evaluations: List<SourceEvaluation> = emptyList(),
        // KMK --> v0.6.16: crash quarantine
        unsafeExtensionKeys: Set<String> = emptySet(),
        // KMK <--
    ) = SourceEvaluationCandidateFilter.buildPool(
        available = available,
        installedPkgNames = installedPkgNames,
        untrustedPkgNames = untrustedPkgNames,
        recLanguages = recLanguages,
        nsfwEnabled = nsfwEnabled,
        blockExplicit = blockExplicit,
        dislikedKeys = dislikedKeys,
        evaluations = evaluations,
        // KMK --> v0.6.16: crash quarantine
        unsafeExtensionKeys = unsafeExtensionKeys,
        // KMK <--
    )

    private fun applyOptions(
        pool: SourceEvaluationCandidateFilter.CandidatePoolResult,
        includeExplicit: Boolean = false,
        skipAlreadyEvaluated: Boolean = false,
        reEvaluateStale: Boolean = false,
    ) = SourceEvaluationCandidateFilter.applyOptions(
        pool = pool,
        includeExplicit = includeExplicit,
        skipAlreadyEvaluated = skipAlreadyEvaluated,
        reEvaluateStale = reEvaluateStale,
        now = now,
    )

    // --- buildPool tests ---

    @Test
    fun `eligible extension is included in pool`() {
        val ext = makeExt()
        val pool = buildPool(available = listOf(ext))
        assertEquals(1, pool.allEligible.size)
        assertEquals("eu.kanade.tachiyomi.extension.en.test", pool.allEligible.first().extension.pkgName)
    }

    @Test
    fun `installed extension is excluded`() {
        val ext = makeExt(pkgName = "eu.kanade.tachiyomi.extension.en.installed")
        val pool = buildPool(
            available = listOf(ext),
            installedPkgNames = setOf("eu.kanade.tachiyomi.extension.en.installed"),
        )
        assertTrue(pool.allEligible.isEmpty())
    }

    @Test
    fun `untrusted extension is excluded`() {
        val ext = makeExt(pkgName = "eu.kanade.tachiyomi.extension.en.untrusted")
        val pool = buildPool(
            available = listOf(ext),
            untrustedPkgNames = setOf("eu.kanade.tachiyomi.extension.en.untrusted"),
        )
        assertTrue(pool.allEligible.isEmpty())
    }

    @Test
    fun `disliked extension is excluded and counted`() {
        val ext = makeExt(signatureHash = "sig1", pkgName = "eu.kanade.tachiyomi.extension.en.disliked")
        val dislikeKey = "a|sig1|eu.kanade.tachiyomi.extension.en.disliked"
        val pool = buildPool(
            available = listOf(ext),
            dislikedKeys = setOf(dislikeKey),
        )
        assertTrue(pool.allEligible.isEmpty())
        assertEquals(1, pool.dislikedHiddenCount)
    }

    @Test
    fun `language mismatch excludes extension`() {
        val ext = makeExt(lang = "ja")
        val pool = buildPool(available = listOf(ext), recLanguages = setOf("en"))
        assertTrue(pool.allEligible.isEmpty())
    }

    @Test
    fun `nsfw extension excluded when nsfwEnabled is false`() {
        val ext = makeExt(isNsfw = true)
        val pool = buildPool(available = listOf(ext), nsfwEnabled = false)
        assertTrue(pool.allEligible.isEmpty())
    }

    @Test
    fun `nsfw extension included when nsfwEnabled is true`() {
        val ext = makeExt(isNsfw = true)
        val pool = buildPool(available = listOf(ext), nsfwEnabled = true)
        assertEquals(1, pool.allEligible.size)
    }

    @Test
    fun `duplicate extensions by signatureHash+pkgName are deduplicated`() {
        val ext1 = makeExt(signatureHash = "dupsig", pkgName = "eu.kanade.tachiyomi.extension.en.dup")
        val ext2 = makeExt(signatureHash = "dupsig", pkgName = "eu.kanade.tachiyomi.extension.en.dup", name = "Dup2")
        val pool = buildPool(available = listOf(ext1, ext2))
        assertEquals(1, pool.allEligible.size)
    }

    @Test
    fun `explicit extension tracked in explicitExtensionKeys`() {
        val ext = makeExt(name = "Hentai Zone", pkgName = "eu.kanade.tachiyomi.extension.en.hentaizone")
        val pool = buildPool(available = listOf(ext))
        val extKey = "${ext.signatureHash}|${ext.pkgName}"
        assertTrue(extKey in pool.explicitExtensionKeys)
        assertEquals(1, pool.allEligible.size)
    }

    // --- applyOptions tests ---

    @Test
    fun `explicit hidden when blockExplicit=true and includeExplicit=false`() {
        val ext = makeExt(name = "Hentai Zone", pkgName = "eu.kanade.tachiyomi.extension.en.hentaizone")
        val pool = buildPool(available = listOf(ext), blockExplicit = true)
        val result = applyOptions(pool, includeExplicit = false)
        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.explicitHiddenCount)
    }

    @Test
    fun `explicit included when blockExplicit=true and includeExplicit=true`() {
        val ext = makeExt(name = "Hentai Zone", pkgName = "eu.kanade.tachiyomi.extension.en.hentaizone")
        val pool = buildPool(available = listOf(ext), blockExplicit = true)
        val result = applyOptions(pool, includeExplicit = true)
        assertEquals(1, result.candidates.size)
        assertEquals(0, result.explicitHiddenCount)
    }

    @Test
    fun `extension-key skip works with extensionKey not evaluationKey`() {
        val ext = makeExt(signatureHash = "evalsig", pkgName = "eu.kanade.tachiyomi.extension.en.evaled")
        val eval = makeEval(signatureHash = "evalsig", pkgName = "eu.kanade.tachiyomi.extension.en.evaled")
        val pool = buildPool(available = listOf(ext), evaluations = listOf(eval))
        val result = applyOptions(pool, skipAlreadyEvaluated = true)
        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.evaluatedHiddenCount)
    }

    @Test
    fun `stale evaluation re-included when reEvaluateStale is true`() {
        val ext = makeExt(signatureHash = "stalesig", pkgName = "eu.kanade.tachiyomi.extension.en.stale")
        val staleEval = makeEval(
            signatureHash = "stalesig",
            pkgName = "eu.kanade.tachiyomi.extension.en.stale",
            expiresAt = now - 1L, // expired before now
        )
        val pool = buildPool(available = listOf(ext), evaluations = listOf(staleEval))
        val result = applyOptions(pool, skipAlreadyEvaluated = true, reEvaluateStale = true)
        assertEquals(1, result.candidates.size)
        assertEquals(0, result.evaluatedHiddenCount)
    }

    @Test
    fun `non-stale evaluation still skipped when reEvaluateStale is true`() {
        val ext = makeExt(signatureHash = "freshsig", pkgName = "eu.kanade.tachiyomi.extension.en.fresh")
        val freshEval = makeEval(
            signatureHash = "freshsig",
            pkgName = "eu.kanade.tachiyomi.extension.en.fresh",
            expiresAt = now + 100_000L, // not expired
        )
        val pool = buildPool(available = listOf(ext), evaluations = listOf(freshEval))
        val result = applyOptions(pool, skipAlreadyEvaluated = true, reEvaluateStale = true)
        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.evaluatedHiddenCount)
    }

    // --- isStale tests ---

    @Test
    fun `isStale returns true when expiresAt is in the past`() {
        val eval = makeEval(expiresAt = now - 1L)
        assertTrue(SourceEvaluationCandidateFilter.isStale(listOf(eval), now))
    }

    @Test
    fun `isStale returns false when expiresAt is in the future`() {
        val eval = makeEval(expiresAt = now + 1_000L)
        assertFalse(SourceEvaluationCandidateFilter.isStale(listOf(eval), now))
    }

    @Test
    fun `isStale returns true when evaluationVersion is outdated`() {
        val eval = makeEval(evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION - 1)
        assertTrue(SourceEvaluationCandidateFilter.isStale(listOf(eval), now))
    }

    @Test
    fun `isStale returns false for empty list`() {
        assertFalse(SourceEvaluationCandidateFilter.isStale(emptyList(), now))
    }

    // KMK --> v0.6.16: unsafe extension key tests

    @Test
    fun `unsafe extension is excluded from pool and counted`() {
        val ext = makeExt(signatureHash = "sig1", pkgName = "eu.kanade.tachiyomi.extension.en.unsafe")
        val unsafeKey = "sig1|eu.kanade.tachiyomi.extension.en.unsafe"
        val pool = buildPool(available = listOf(ext), unsafeExtensionKeys = setOf(unsafeKey))
        assertTrue(pool.allEligible.isEmpty())
        assertEquals(1, pool.unsafeHiddenCount)
        assertTrue(pool.unsafeExtensionKeys.contains(unsafeKey))
    }

    @Test
    fun `safe extension is not excluded when unsafe keys does not match`() {
        val ext = makeExt(signatureHash = "sig2", pkgName = "eu.kanade.tachiyomi.extension.en.safe")
        val pool = buildPool(available = listOf(ext), unsafeExtensionKeys = setOf("othersig|other.pkg"))
        assertEquals(1, pool.allEligible.size)
        assertEquals(0, pool.unsafeHiddenCount)
    }

    @Test
    fun `unsafe hidden count is zero when no unsafe extensions`() {
        val ext = makeExt()
        val pool = buildPool(available = listOf(ext))
        assertEquals(0, pool.unsafeHiddenCount)
        assertTrue(pool.unsafeExtensionKeys.isEmpty())
    }

    @Test
    fun `multiple unsafe extensions all excluded`() {
        val ext1 = makeExt(signatureHash = "s1", pkgName = "eu.kanade.tachiyomi.extension.en.one")
        val ext2 = makeExt(signatureHash = "s2", pkgName = "eu.kanade.tachiyomi.extension.en.two")
        val safeExt = makeExt(signatureHash = "s3", pkgName = "eu.kanade.tachiyomi.extension.en.three")
        val pool = buildPool(
            available = listOf(ext1, ext2, safeExt),
            unsafeExtensionKeys = setOf("s1|eu.kanade.tachiyomi.extension.en.one", "s2|eu.kanade.tachiyomi.extension.en.two"),
        )
        assertEquals(1, pool.allEligible.size)
        assertEquals(2, pool.unsafeHiddenCount)
    }

    // KMK --> v0.6.19 follow-up: installed exclusion + unassessed remaining count tests

    @Test
    fun `installed extension not counted in eligible pool even when in available list`() {
        val installed = makeExt(pkgName = "eu.kanade.tachiyomi.extension.en.installed")
        val available = makeExt(pkgName = "eu.kanade.tachiyomi.extension.en.available")
        val pool = buildPool(
            available = listOf(installed, available),
            installedPkgNames = setOf("eu.kanade.tachiyomi.extension.en.installed"),
        )
        assertEquals(1, pool.allEligible.size)
        assertEquals("eu.kanade.tachiyomi.extension.en.available", pool.allEligible.first().extension.pkgName)
    }

    @Test
    fun `installed extension not counted in disliked hidden when also disliked`() {
        val installedAndDisliked = makeExt(signatureHash = "sig1", pkgName = "eu.kanade.tachiyomi.extension.en.both")
        val dislikeKey = "a|sig1|eu.kanade.tachiyomi.extension.en.both"
        val pool = buildPool(
            available = listOf(installedAndDisliked),
            installedPkgNames = setOf("eu.kanade.tachiyomi.extension.en.both"),
            dislikedKeys = setOf(dislikeKey),
        )
        // Installed extensions are skipped before the dislike check — they must not inflate dislikedHiddenCount
        assertEquals(0, pool.allEligible.size)
        assertEquals(0, pool.dislikedHiddenCount)
    }

    @Test
    fun `already-evaluated extension excluded from candidates when skipAlreadyEvaluated is true`() {
        val evaluated = makeExt(signatureHash = "sig1", pkgName = "eu.kanade.tachiyomi.extension.en.evaluated")
        val unevaluated = makeExt(signatureHash = "sig2", pkgName = "eu.kanade.tachiyomi.extension.en.unevaluated")
        val eval = makeEval(signatureHash = "sig1", pkgName = "eu.kanade.tachiyomi.extension.en.evaluated")
        val pool = buildPool(available = listOf(evaluated, unevaluated), evaluations = listOf(eval))
        val result = applyOptions(pool, skipAlreadyEvaluated = true)
        assertEquals(1, result.candidates.size)
        assertEquals("eu.kanade.tachiyomi.extension.en.unevaluated", result.candidates.first().extension.pkgName)
        assertEquals(1, result.evaluatedHiddenCount)
    }

    // KMK <--

    // KMK v0.8.1-fix4: source/library-quality dislike axis is separate from dislikedKeys
    // (recommendation-behavior dislike) in buildPool()

    @Test
    fun `source-quality disliked extension is excluded from pool and counted separately`() {
        val ext = makeExt(signatureHash = "sig1", pkgName = "eu.kanade.tachiyomi.extension.en.poor")
        val qualityKey = "a|sig1|eu.kanade.tachiyomi.extension.en.poor"
        val pool = SourceEvaluationCandidateFilter.buildPool(
            available = listOf(ext),
            installedPkgNames = emptySet(),
            untrustedPkgNames = emptySet(),
            recLanguages = setOf("en"),
            nsfwEnabled = true,
            blockExplicit = false,
            dislikedKeys = emptySet(),
            evaluations = emptyList(),
            qualityDislikedKeys = setOf(qualityKey),
        )
        assertTrue(pool.allEligible.isEmpty())
        assertEquals(1, pool.sourceQualityHiddenCount)
        assertEquals(0, pool.dislikedHiddenCount)
    }

    @Test
    fun `recommendation-disliked and source-quality-disliked counts are tracked independently`() {
        val extA = makeExt(signatureHash = "siga", pkgName = "eu.kanade.tachiyomi.extension.en.a")
        val extB = makeExt(signatureHash = "sigb", pkgName = "eu.kanade.tachiyomi.extension.en.b")
        val pool = SourceEvaluationCandidateFilter.buildPool(
            available = listOf(extA, extB),
            installedPkgNames = emptySet(),
            untrustedPkgNames = emptySet(),
            recLanguages = setOf("en"),
            nsfwEnabled = true,
            blockExplicit = false,
            dislikedKeys = setOf("a|siga|eu.kanade.tachiyomi.extension.en.a"),
            evaluations = emptyList(),
            qualityDislikedKeys = setOf("a|sigb|eu.kanade.tachiyomi.extension.en.b"),
        )
        assertTrue(pool.allEligible.isEmpty())
        assertEquals(1, pool.dislikedHiddenCount)
        assertEquals(1, pool.sourceQualityHiddenCount)
    }

    @Test
    fun `sourceQualityHiddenCount is zero when no source-quality dislikes present`() {
        val ext = makeExt()
        val pool = buildPool(available = listOf(ext))
        assertEquals(0, pool.sourceQualityHiddenCount)
    }

    // KMK <--
}
// KMK <--
