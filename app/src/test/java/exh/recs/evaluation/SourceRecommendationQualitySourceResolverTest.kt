package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->

/** Minimal CatalogueSource stub for SourceRecommendationQualitySourceResolver tests. */
private class FakeResolverSource(
    override val id: Long,
    override val name: String,
    override val lang: String,
) : eu.kanade.tachiyomi.source.Source {
    override val supportsLatest = false
    override suspend fun getPopularManga(page: Int): eu.kanade.tachiyomi.source.model.MangasPage =
        eu.kanade.tachiyomi.source.model.MangasPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int): eu.kanade.tachiyomi.source.model.MangasPage =
        eu.kanade.tachiyomi.source.model.MangasPage(emptyList(), false)
    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: eu.kanade.tachiyomi.source.model.FilterList,
    ): eu.kanade.tachiyomi.source.model.MangasPage =
        eu.kanade.tachiyomi.source.model.MangasPage(emptyList(), false)
    override fun getFilterList(): eu.kanade.tachiyomi.source.model.FilterList =
        eu.kanade.tachiyomi.source.model.FilterList()
    override suspend fun getMangaUpdate(
        manga: eu.kanade.tachiyomi.source.model.SManga,
        chapters: List<eu.kanade.tachiyomi.source.model.SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = eu.kanade.tachiyomi.source.model.SMangaUpdate(manga, emptyList())
    override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter) =
        emptyList<eu.kanade.tachiyomi.source.model.Page>()
}

class SourceRecommendationQualitySourceResolverTest {

    private fun makeInstalled(
        vararg sources: FakeResolverSource,
    ) = Extension.Installed(
        name = "TestExt",
        pkgName = "com.test.ext",
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.0,
        lang = "en",
        isNsfw = false,
        signatureHash = "abc",
        storeName = "test-repo",
        pkgFactory = null,
        sources = sources.toList(),
        icon = null,
        isShared = false,
    )

    private fun makeEval(
        sourceId: Long = 1L,
        sourceName: String = "TestSource",
        lang: String = "en",
    ) = SourceEvaluation(
        evaluationKey = "abc|com.test.ext|$sourceId",
        sourceId = sourceId,
        extensionPkgName = "com.test.ext",
        signatureHash = "abc",
        extensionName = "TestExt",
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
    fun `exact source id returns Found`() {
        val source = FakeResolverSource(id = 42L, name = "MangaDex", lang = "en")
        val ext = makeInstalled(source)
        val eval = makeEval(sourceId = 42L, sourceName = "MangaDex", lang = "en")

        val result = SourceRecommendationQualitySourceResolver.resolve(ext, eval)

        assertInstanceOf(SourceRecommendationQualitySourceResolver.ResolveResult.Found::class.java, result)
        assertEquals(source, (result as SourceRecommendationQualitySourceResolver.ResolveResult.Found).source)
    }

    @Test
    fun `name plus lang match when id differs returns Found`() {
        val source = FakeResolverSource(id = 99L, name = "MangaDex", lang = "en")
        val ext = makeInstalled(source)
        val eval = makeEval(sourceId = 42L, sourceName = "MangaDex", lang = "en")

        val result = SourceRecommendationQualitySourceResolver.resolve(ext, eval)

        assertInstanceOf(SourceRecommendationQualitySourceResolver.ResolveResult.Found::class.java, result)
        assertEquals(source, (result as SourceRecommendationQualitySourceResolver.ResolveResult.Found).source)
    }

    @Test
    fun `name only match ambiguous across languages returns Ambiguous`() {
        val en = FakeResolverSource(id = 1L, name = "MangaDex", lang = "en")
        val ja = FakeResolverSource(id = 2L, name = "MangaDex", lang = "ja")
        val ext = makeInstalled(en, ja)
        // lang="fr" → name+lang step finds 0, name-only step finds 2 → Ambiguous
        val eval = makeEval(sourceId = 99L, sourceName = "MangaDex", lang = "fr")

        val result = SourceRecommendationQualitySourceResolver.resolve(ext, eval)

        assertInstanceOf(SourceRecommendationQualitySourceResolver.ResolveResult.Ambiguous::class.java, result)
    }

    @Test
    fun `normalized name plus lang match returns Found`() {
        val source = FakeResolverSource(id = 99L, name = "  Manga Dex  ", lang = "en")
        val ext = makeInstalled(source)
        val eval = makeEval(sourceId = 42L, sourceName = "manga dex", lang = "en")

        val result = SourceRecommendationQualitySourceResolver.resolve(ext, eval)

        assertInstanceOf(SourceRecommendationQualitySourceResolver.ResolveResult.Found::class.java, result)
        assertEquals(source, (result as SourceRecommendationQualitySourceResolver.ResolveResult.Found).source)
    }

    @Test
    fun `no match returns NotFound`() {
        val source = FakeResolverSource(id = 1L, name = "ComicK", lang = "en")
        val ext = makeInstalled(source)
        val eval = makeEval(sourceId = 99L, sourceName = "MangaDex", lang = "en")

        val result = SourceRecommendationQualitySourceResolver.resolve(ext, eval)

        assertInstanceOf(SourceRecommendationQualitySourceResolver.ResolveResult.NotFound::class.java, result)
    }

    @Test
    fun `empty sources returns NotFound`() {
        val ext = makeInstalled()
        val eval = makeEval(sourceId = 1L, sourceName = "MangaDex", lang = "en")

        val result = SourceRecommendationQualitySourceResolver.resolve(ext, eval)

        assertInstanceOf(SourceRecommendationQualitySourceResolver.ResolveResult.NotFound::class.java, result)
    }

    @Test
    fun `lang-specific name match isolates correct language`() {
        val enSource = FakeResolverSource(id = 1L, name = "MangaDex", lang = "en")
        val jaSource = FakeResolverSource(id = 2L, name = "MangaDex", lang = "ja")
        val ext = makeInstalled(enSource, jaSource)
        val eval = makeEval(sourceId = 99L, sourceName = "MangaDex", lang = "en")

        // name+lang step finds exactly the en source — should return Found, not Ambiguous
        val result = SourceRecommendationQualitySourceResolver.resolve(ext, eval)

        assertInstanceOf(SourceRecommendationQualitySourceResolver.ResolveResult.Found::class.java, result)
        assertEquals(enSource, (result as SourceRecommendationQualitySourceResolver.ResolveResult.Found).source)
    }

    @Test
    fun `exact id takes priority over name match`() {
        val idSource = FakeResolverSource(id = 42L, name = "DifferentName", lang = "en")
        val nameSource = FakeResolverSource(id = 99L, name = "MangaDex", lang = "en")
        val ext = makeInstalled(idSource, nameSource)
        val eval = makeEval(sourceId = 42L, sourceName = "MangaDex", lang = "en")

        // id match is step 1 — wins over name match at step 2
        val result = SourceRecommendationQualitySourceResolver.resolve(ext, eval)

        assertInstanceOf(SourceRecommendationQualitySourceResolver.ResolveResult.Found::class.java, result)
        assertEquals(idSource, (result as SourceRecommendationQualitySourceResolver.ResolveResult.Found).source)
    }
}
// KMK <--
