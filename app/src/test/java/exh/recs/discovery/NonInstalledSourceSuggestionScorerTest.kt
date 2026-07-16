package exh.recs.discovery

import eu.kanade.tachiyomi.extension.model.Extension
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class NonInstalledSourceSuggestionScorerTest {

    // --- Test fixture helpers ---

    private fun availableExt(
        name: String = "Test Source",
        pkgName: String = "eu.test",
        lang: String = "en",
        isNsfw: Boolean = false,
        signatureHash: String = "sig1",
        repoName: String = "TestRepo",
        sources: List<Extension.Available.Source> = emptyList(),
    ) = Extension.Available(
        name = name,
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = lang,
        isNsfw = isNsfw,
        signatureHash = signatureHash,
        repoName = repoName,
        sources = sources,
        apkName = "$pkgName.apk",
        iconUrl = "",
        repoUrl = "https://repo.example.com",
    )

    private fun availableSource(
        id: Long,
        name: String,
        lang: String = "en",
        baseUrl: String = "https://$name.example.com",
    ) = Extension.Available.Source(id = id, lang = lang, name = name, baseUrl = baseUrl)

    private fun installedHint(
        signatureHash: String = "sig_installed",
        pkgName: String = "eu.installed",
        repoName: String? = "TestRepo",
        sourceNames: List<String> = emptyList(),
    ) = InstalledExtensionHints(signatureHash, pkgName, repoName, sourceNames)

    private fun untrusted(
        signatureHash: String = "sig_untrusted",
        pkgName: String = "eu.untrusted",
    ) = Extension.Untrusted(
        name = "Untrusted",
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        signatureHash = signatureHash,
    )

    private val recLanguages = setOf("en")
    private val nsfwEnabled = true
    private val noDismissed = emptySet<String>()

    // --- Eligibility filter tests ---

    @Test
    fun `installed extension is excluded from suggestions`() {
        val ext = availableExt(pkgName = "eu.alreadyinstalled", signatureHash = "sig_a")
        val hint = installedHint(pkgName = "eu.alreadyinstalled", signatureHash = "sig_a")
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `untrusted extension is excluded from suggestions`() {
        val ext = availableExt(pkgName = "eu.untrustedpkg", signatureHash = "sig_u")
        val u = untrusted(pkgName = "eu.untrustedpkg", signatureHash = "sig_u")
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = listOf(u),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `language mismatch is excluded`() {
        val ext = availableExt(lang = "ja", sources = listOf(availableSource(1L, "JaSource", lang = "ja")))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = setOf("en"),
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nsfw source is excluded when nsfw display is disabled`() {
        val ext = availableExt(isNsfw = true, sources = listOf(availableSource(1L, "NsfwSource")))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = false,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `dismissed suggestion is excluded`() {
        val ext = availableExt(
            pkgName = "eu.dismissed",
            signatureHash = "sig_d",
            sources = listOf(availableSource(42L, "DismissedSource")),
        )
        val key = buildDismissalKey("sig_d", "eu.dismissed", 42L)
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = setOf(key),
        )
        assertTrue(result.isEmpty())
    }

    // --- Evidence gate tests: weak signals no longer qualify ---

    @Test
    fun `language-only source is excluded from suggestions`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "GoodSource")))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `same-repo-only source is excluded from suggestions`() {
        val ext = availableExt(repoName = "SharedRepo", sources = listOf(availableSource(1L, "RepoSource")))
        val hint = installedHint(repoName = "SharedRepo", pkgName = "eu.other", signatureHash = "sig_other")
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generic-keyword-only source is excluded from suggestions`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "Manga Comics", baseUrl = "https://mangacomics.example.com")))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `base-url-only source is excluded from suggestions`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "ASource", baseUrl = "https://asource.example.com")))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `regression - ordinary english sources with no installed hints produce empty list`() {
        val sources = listOf(
            availableExt(name = "Random Scans", pkgName = "eu.randomscans", signatureHash = "sig_r"),
            availableExt(name = "Generic Manga", pkgName = "eu.genericmanga", signatureHash = "sig_g"),
            availableExt(name = "Another Comics", pkgName = "eu.anothercomics", signatureHash = "sig_ac"),
        )
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = sources,
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    // --- Meaningful evidence tests ---

    @Test
    fun `similar installed source creates suggestion`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "Asura Scans EN")))
        val hint = installedHint(sourceNames = listOf("Asura Scans"))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertEquals(1, result.size)
        assertTrue(result[0].reasons.any { it is NonInstalledSuggestionReason.SimilarToInstalledSource })
    }

    @Test
    fun `needs testing alone does not qualify a suggestion`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "SomeSource")))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertTrue(result.isEmpty())
    }

    // --- Scoring and ordering tests ---

    @Test
    fun `suggestions sort by score descending then name ascending`() {
        // extA: exact name match -> score 0.60
        // extB: token match -> score 0.50; both have SimilarToInstalledSource
        val extA = availableExt(
            name = "Mangafire EN",
            pkgName = "eu.mangafireen",
            signatureHash = "sig_a",
            sources = listOf(availableSource(1L, "Mangafire EN")),
        )
        val extB = availableExt(
            name = "Asura Scans Plus",
            pkgName = "eu.asuraplus",
            signatureHash = "sig_b",
            sources = listOf(availableSource(2L, "Asura Scans Plus")),
        )
        val hint = installedHint(
            signatureHash = "sig_installed",
            sourceNames = listOf("Mangafire EN", "Asura Scans"),
        )
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(extB, extA),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertEquals(2, result.size)
        // extA has exact match (0.60) > extB token match (0.50)
        assertEquals("Mangafire EN", result[0].displayName)
        assertEquals("Asura Scans Plus", result[1].displayName)
    }

    @Test
    fun `no suggestion receives high confidence before install`() {
        val ext = availableExt(
            repoName = "SharedRepo",
            sources = listOf(availableSource(1L, "Scans Source", baseUrl = "https://scans.example.com")),
        )
        val hint = installedHint(repoName = "SharedRepo", sourceNames = listOf("Scans Source"))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertEquals(1, result.size)
        assertTrue(result[0].score <= 0.69) { "Score ${result[0].score} exceeded 0.69 cap" }
        assertTrue(result[0].confidence in listOf(SuggestionConfidence.LOW, SuggestionConfidence.MEDIUM))
    }

    @Test
    fun `original extension identity is preserved not a synthetic copy`() {
        val originalPkg = "eu.original"
        val src = availableSource(id = 99L, name = "My Source")
        val ext = availableExt(pkgName = originalPkg, sources = listOf(src))
        val hint = installedHint(sourceNames = listOf("My Source"))
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
        )
        assertEquals(1, result.size)
        // Original pkgName must be preserved — not "eu.original-99"
        assertEquals(originalPkg, result[0].extension.pkgName)
        assertEquals(src, result[0].source)
    }

    // --- Utility tests ---

    @Test
    fun `normalizeSourceName removes non-alphanumeric and lowercases`() {
        assertEquals("asurascans", NonInstalledSourceSuggestionScorer.normalizeSourceName("Asura Scans"))
        assertEquals("mangafire", NonInstalledSourceSuggestionScorer.normalizeSourceName("MangaFire"))
        assertEquals("qiscans", NonInstalledSourceSuggestionScorer.normalizeSourceName("QI Scans!"))
    }

    @Test
    fun `distinctiveTokens filters generic words and short tokens`() {
        val tokens = NonInstalledSourceSuggestionScorer.distinctiveTokens("Asura Scans")
        assertTrue("asura" in tokens)
        assertFalse("scans" in tokens) { "Generic word 'scans' should be filtered" }

        val tokens2 = NonInstalledSourceSuggestionScorer.distinctiveTokens("Manga Scans")
        assertTrue(tokens2.isEmpty()) { "All tokens are generic or short" }
    }

    @Test
    fun `sourceKeywordTokens splits name and baseUrl into tokens`() {
        val tokens = NonInstalledSourceSuggestionScorer.sourceKeywordTokens("Asura Scans", "https://asurascans.com")
        assertTrue("asura" in tokens)
        assertTrue("scans" in tokens)
    }

    // --- Like / Dislike integration tests ---

    @Test
    fun `disliked available source is excluded from suggestions`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "Asura Scans EN")))
        val hint = installedHint(sourceNames = listOf("Asura Scans"))
        val candKey = RecommendationSourcePreferenceStore.availableKey(ext.signatureHash, ext.pkgName, 1L)
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
            dislikedKeys = setOf(candKey),
        )
        assertTrue(result.isEmpty())
    }

    // KMK v0.8.1-fix4: source/library-quality dislike is a separate axis from recommendation dislike

    @Test
    fun `source-quality disliked available source is excluded from suggestions`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "Asura Scans EN")))
        val hint = installedHint(sourceNames = listOf("Asura Scans"))
        val candKey = RecommendationSourcePreferenceStore.availableKey(ext.signatureHash, ext.pkgName, 1L)
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
            qualityDislikedKeys = setOf(candKey),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `source-quality dislike is hidden even when global explicit filter is off`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "Asura Scans EN")))
        val hint = installedHint(sourceNames = listOf("Asura Scans"))
        val candKey = RecommendationSourcePreferenceStore.availableKey(ext.signatureHash, ext.pkgName, 1L)
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            blockExplicit = false,
            dismissed = noDismissed,
            qualityDislikedKeys = setOf(candKey),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `recommendation-disliked source and source-quality-disliked source are independently excluded`() {
        val extA = availableExt(pkgName = "eu.a", signatureHash = "siga", sources = listOf(availableSource(1L, "Asura Scans EN")))
        val extB = availableExt(pkgName = "eu.b", signatureHash = "sigb", sources = listOf(availableSource(2L, "Bato Scans EN")))
        val hint = installedHint(sourceNames = listOf("Asura Scans", "Bato Scans"))
        val recDislikeKey = RecommendationSourcePreferenceStore.availableKey("siga", "eu.a", 1L)
        val qualityDislikeKey = RecommendationSourcePreferenceStore.availableKey("sigb", "eu.b", 2L)
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(extA, extB),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
            dislikedKeys = setOf(recDislikeKey),
            qualityDislikedKeys = setOf(qualityDislikeKey),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `liked available source is included even without metadata similarity`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "SomeSource")))
        val candKey = RecommendationSourcePreferenceStore.availableKey(ext.signatureHash, ext.pkgName, 1L)
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(), // no similarity evidence
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
            likedKeys = setOf(candKey),
        )
        assertEquals(1, result.size)
        assertTrue(result[0].reasons.any { it is NonInstalledSuggestionReason.UserLikedSource })
    }

    @Test
    fun `liked source score is higher than neutral metadata-matched suggestion`() {
        val extLiked = availableExt(
            name = "Some Source",
            pkgName = "eu.some",
            signatureHash = "sig_s",
            sources = listOf(availableSource(1L, "Some Source")),
        )
        val extNeutral = availableExt(
            name = "Asura Scans EN",
            pkgName = "eu.asura",
            signatureHash = "sig_a",
            sources = listOf(availableSource(2L, "Asura Scans EN")),
        )
        val hint = installedHint(sourceNames = listOf("Asura Scans"))
        val likedKey = RecommendationSourcePreferenceStore.availableKey(extLiked.signatureHash, extLiked.pkgName, 1L)
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(extNeutral, extLiked),
            installedHints = listOf(hint),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
            likedKeys = setOf(likedKey),
        )
        assertEquals(2, result.size)
        // liked source (0.68) > neutral similarity match (0.50-0.60)
        assertEquals("Some Source", result[0].displayName)
    }

    @Test
    fun `liked source has UserLikedSource reason and MEDIUM confidence`() {
        val ext = availableExt(sources = listOf(availableSource(1L, "SomeSource")))
        val candKey = RecommendationSourcePreferenceStore.availableKey(ext.signatureHash, ext.pkgName, 1L)
        val result = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
            likedKeys = setOf(candKey),
        )
        assertEquals(1, result.size)
        val suggestion = result[0]
        assertTrue(suggestion.reasons.any { it is NonInstalledSuggestionReason.UserLikedSource })
        assertEquals(SuggestionConfidence.MEDIUM, suggestion.confidence)
        assertTrue(suggestion.score <= 0.69)
    }

    @Test
    fun `dismissed and disliked are separate - dismissed source is not disliked`() {
        val ext = availableExt(
            pkgName = "eu.dismissed",
            signatureHash = "sig_d",
            sources = listOf(availableSource(42L, "DismissedSource")),
        )
        val dismissalKey = buildDismissalKey("sig_d", "eu.dismissed", 42L)
        val candKey = RecommendationSourcePreferenceStore.availableKey("sig_d", "eu.dismissed", 42L)
        // dismissed via dismiss — disliked set is empty, liked set has the key
        val likedKey = candKey
        val resultLikedButDismissed = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = setOf(dismissalKey), // dismissed
            likedKeys = setOf(likedKey), // but also liked
            dislikedKeys = emptySet(),
        )
        // dismissed takes precedence — dismissed keys are checked before like
        assertTrue(resultLikedButDismissed.isEmpty())

        // disliked source separately also excluded
        val resultDislikedOnly = NonInstalledSourceSuggestionScorer.scoreAndFilter(
            available = listOf(ext),
            installedHints = emptyList(),
            untrusted = emptyList(),
            recLanguages = recLanguages,
            nsfwEnabled = nsfwEnabled,
            dismissed = noDismissed,
            likedKeys = emptySet(),
            dislikedKeys = setOf(candKey),
        )
        assertTrue(resultDislikedOnly.isEmpty())
    }
}
// KMK <--
