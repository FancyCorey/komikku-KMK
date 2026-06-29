package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
class SourceRecommendationQualityInstalledResolverTest {

    private fun makeInstalled(
        name: String,
        pkg: String,
        sig: String,
        lang: String = "en",
    ) = Extension.Installed(
        name = name,
        pkgName = pkg,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.0,
        lang = lang,
        isNsfw = false,
        signatureHash = sig,
        repoName = "test-repo",
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        isShared = false,
    )

    private fun makeEval(
        pkg: String,
        sig: String,
        name: String = "Ext $pkg",
        lang: String = "en",
        sourceId: Long = 1L,
        sourceName: String = "Source $pkg",
    ) = SourceEvaluation(
        evaluationKey = "$sig|$pkg|$sourceId",
        sourceId = sourceId,
        extensionPkgName = pkg,
        signatureHash = sig,
        extensionName = name,
        sourceName = sourceName,
        lang = lang,
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
        preferredTagMatchCount = 1,
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
    fun `exact sig and pkg match returns Found`() {
        val ext = makeInstalled("MangaDex", "eu.kanade.tachiyomi.extension.en.mangadex", "abc123")
        val eval = makeEval("eu.kanade.tachiyomi.extension.en.mangadex", "abc123", "MangaDex")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, listOf(ext))

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.Found::class.java, result)
        assertEquals(ext, (result as SourceRecommendationQualityInstalledResolver.ResolveResult.Found).extension)
    }

    @Test
    fun `pkgName-only fallback when sig differs returns Found`() {
        val ext = makeInstalled("MangaDex", "eu.kanade.tachiyomi.extension.en.mangadex", "newsig")
        val eval = makeEval("eu.kanade.tachiyomi.extension.en.mangadex", "oldsig", "MangaDex")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, listOf(ext))

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.Found::class.java, result)
        assertEquals(ext, (result as SourceRecommendationQualityInstalledResolver.ResolveResult.Found).extension)
    }

    @Test
    fun `multiple installed extensions with same pkgName returns Ambiguous`() {
        val ext1 = makeInstalled("Ext A", "com.example.ext", "sig1")
        val ext2 = makeInstalled("Ext B", "com.example.ext", "sig2")
        val eval = makeEval("com.example.ext", "oldsig", "Ext A")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, listOf(ext1, ext2))

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.Ambiguous::class.java, result)
        assertEquals(2, (result as SourceRecommendationQualityInstalledResolver.ResolveResult.Ambiguous).matchCount)
    }

    @Test
    fun `sig plus name fallback returns Found`() {
        val ext = makeInstalled("ComicK", "com.comick", "sig123")
        val eval = makeEval("com.different.pkg", "sig123", "ComicK")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, listOf(ext))

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.Found::class.java, result)
        assertEquals(ext, (result as SourceRecommendationQualityInstalledResolver.ResolveResult.Found).extension)
    }

    @Test
    fun `name plus lang fallback unambiguous returns Found`() {
        val ext = makeInstalled("Webtoon", "com.webtoon.en", "sig456", "en")
        val eval = makeEval("com.different", "diffsig", "Webtoon", "en")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, listOf(ext))

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.Found::class.java, result)
        assertEquals(ext, (result as SourceRecommendationQualityInstalledResolver.ResolveResult.Found).extension)
    }

    @Test
    fun `name plus lang fallback ambiguous returns Ambiguous`() {
        val ext1 = makeInstalled("Webtoon", "com.webtoon.v1", "sig1", "en")
        val ext2 = makeInstalled("Webtoon", "com.webtoon.v2", "sig2", "en")
        val eval = makeEval("com.different", "diffsig", "Webtoon", "en")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, listOf(ext1, ext2))

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.Ambiguous::class.java, result)
        assertEquals(2, (result as SourceRecommendationQualityInstalledResolver.ResolveResult.Ambiguous).matchCount)
    }

    @Test
    fun `no match at any step returns NotFound`() {
        val ext = makeInstalled("SomeOtherExt", "com.other.ext", "othersig", "fr")
        val eval = makeEval("com.unknown.pkg", "unknownsig", "UnknownExt", "en")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, listOf(ext))

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.NotFound::class.java, result)
    }

    @Test
    fun `empty installed list returns NotFound`() {
        val eval = makeEval("com.some.pkg", "somesig", "SomeExt")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, emptyList())

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.NotFound::class.java, result)
    }

    @Test
    fun `exact match takes priority over pkg-only match`() {
        val exactExt = makeInstalled("Ext", "com.example.ext", "correctsig")
        val pkgExt = makeInstalled("Ext Other", "com.example.ext", "othersig")
        val eval = makeEval("com.example.ext", "correctsig", "Ext")

        val result = SourceRecommendationQualityInstalledResolver.resolve(eval, listOf(exactExt, pkgExt))

        assertInstanceOf(SourceRecommendationQualityInstalledResolver.ResolveResult.Found::class.java, result)
        assertEquals(exactExt, (result as SourceRecommendationQualityInstalledResolver.ResolveResult.Found).extension)
    }
}
// KMK <--
