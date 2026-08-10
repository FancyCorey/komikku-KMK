package exh.recs.share

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

// KMK -->

class RecommendationBundleSourceResolverTest {

    private val installedSources = listOf(
        RecommendationBundleSourceResolver.InstalledSourceSnapshot(
            sourceId = 1_000L,
            pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
            name = "MangaDex",
            lang = "en",
            signatureHash = "sig_mangadex",
        ),
        RecommendationBundleSourceResolver.InstalledSourceSnapshot(
            sourceId = 2_000L,
            pkgName = "eu.kanade.tachiyomi.extension.ja.pixiv",
            name = "Pixiv",
            lang = "ja",
            signatureHash = "sig_pixiv",
        ),
        RecommendationBundleSourceResolver.InstalledSourceSnapshot(
            sourceId = 3_000L,
            pkgName = "eu.kanade.tachiyomi.extension.en.mangaplus",
            name = "Manga Plus",
            lang = "en",
            signatureHash = "sig_mangaplus",
        ),
    )

    private fun item(
        sourceId: Long,
        pkgName: String? = null,
        name: String? = null,
        lang: String? = null,
        sigHash: String? = null,
    ) = RecommendationBundleItem(
        title = "Test Manga",
        url = "/manga/test",
        sourceId = sourceId,
        extensionPkgName = pkgName,
        sourceName = name,
        sourceLang = lang,
        extensionSignatureHash = sigHash,
    )

    // ----- Exact sourceId resolution -----

    @Test
    fun `exact sourceId match resolves immediately`() {
        val result = RecommendationBundleSourceResolver.resolveSource(
            item(sourceId = 1_000L),
            installedSources,
        )
        assertInstanceOf(RecommendationBundleSourceResolver.ResolvedSource.FoundExact::class.java, result)
        assertEquals(1_000L, (result as RecommendationBundleSourceResolver.ResolvedSource.FoundExact).sourceId)
    }

    @Test
    fun `exact sourceId takes priority over metadata match`() {
        // sourceId=1000 is MangaDex but item metadata says Pixiv pkg — exact wins
        val result = RecommendationBundleSourceResolver.resolveSource(
            item(
                sourceId = 1_000L,
                pkgName = "eu.kanade.tachiyomi.extension.ja.pixiv",
                name = "Pixiv",
                lang = "ja",
            ),
            installedSources,
        )
        assertInstanceOf(RecommendationBundleSourceResolver.ResolvedSource.FoundExact::class.java, result)
        assertEquals(1_000L, (result as RecommendationBundleSourceResolver.ResolvedSource.FoundExact).sourceId)
    }

    // ----- Metadata fallback (pkgName + name + lang) -----

    @Test
    fun `pkgName fallback resolves when sourceId not found`() {
        val result = RecommendationBundleSourceResolver.resolveSource(
            item(
                sourceId = 9_999L, // not installed under this id
                pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
                name = "MangaDex",
                lang = "en",
            ),
            installedSources,
        )
        assertInstanceOf(RecommendationBundleSourceResolver.ResolvedSource.FoundByMetadata::class.java, result)
        assertEquals(1_000L, (result as RecommendationBundleSourceResolver.ResolvedSource.FoundByMetadata).sourceId)
    }

    @Test
    fun `signature hash fallback resolves when pkgName missing`() {
        val result = RecommendationBundleSourceResolver.resolveSource(
            item(
                sourceId = 9_999L,
                name = "MangaDex",
                lang = "en",
                sigHash = "sig_mangadex",
            ),
            installedSources,
        )
        assertInstanceOf(RecommendationBundleSourceResolver.ResolvedSource.FoundByMetadata::class.java, result)
        assertEquals(1_000L, (result as RecommendationBundleSourceResolver.ResolvedSource.FoundByMetadata).sourceId)
    }

    // ----- Missing source -----

    @Test
    fun `unresolvable item returns Missing`() {
        val result = RecommendationBundleSourceResolver.resolveSource(
            item(sourceId = 9_999L, pkgName = "com.unknown.extension", name = "Unknown", lang = "xx"),
            installedSources,
        )
        assertEquals(RecommendationBundleSourceResolver.ResolvedSource.Missing, result)
    }

    @Test
    fun `empty installed list always returns Missing`() {
        val result = RecommendationBundleSourceResolver.resolveSource(
            item(sourceId = 1_000L, pkgName = "eu.kanade.tachiyomi.extension.en.mangadex", name = "MangaDex", lang = "en"),
            emptyList(),
        )
        assertEquals(RecommendationBundleSourceResolver.ResolvedSource.Missing, result)
    }

    // ----- normalizeTitle -----

    @Test
    fun `normalizeTitle strips parenthetical suffixes`() {
        val normalized = RecommendationBundleSourceResolver.normalizeTitle("Berserk (2016)")
        assertEquals("berserk", normalized)
    }

    @Test
    fun `normalizeTitle strips bracket suffixes`() {
        val normalized = RecommendationBundleSourceResolver.normalizeTitle("One Piece [Colored]")
        assertEquals("one piece", normalized)
    }

    @Test
    fun `normalizeTitle collapses whitespace`() {
        val normalized = RecommendationBundleSourceResolver.normalizeTitle("  My  Manga  ")
        assertEquals("my manga", normalized)
    }

    @Test
    fun `normalizeTitle handles special chars`() {
        val normalized = RecommendationBundleSourceResolver.normalizeTitle("Re:Zero − Starting Life")
        assertEquals("re zero starting life", normalized)
    }

    // ----- resolveAvailableExtension -----

    private fun availExt(pkgName: String, sigHash: String = "sig_$pkgName") = Extension.Available(
        name = pkgName,
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.4,
        lang = "en",
        isNsfw = false,
        signatureHash = sigHash,
        storeName = "test-repo",
        sources = emptyList(),
        apkUrl = "https://test-repo.example.com/apk/$pkgName.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "",
            name = "test-repo",
            badgeLabel = "test-repo",
            signingKey = sigHash,
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    @Test
    fun `resolveAvailableExtension returns NotFound when item has no pkgName`() {
        val result = RecommendationBundleSourceResolver.resolveAvailableExtension(
            item(sourceId = 9_999L),
            listOf(availExt("com.example.ext")),
        )
        assertEquals(RecommendationBundleSourceResolver.AvailableExtensionResolution.NotFound, result)
    }

    @Test
    fun `resolveAvailableExtension returns NotFound when no available extension matches pkgName`() {
        val result = RecommendationBundleSourceResolver.resolveAvailableExtension(
            item(sourceId = 9_999L, pkgName = "com.unknown.ext"),
            listOf(availExt("com.example.ext")),
        )
        assertEquals(RecommendationBundleSourceResolver.AvailableExtensionResolution.NotFound, result)
    }

    @Test
    fun `resolveAvailableExtension returns Unambiguous for exact pkgName + sigHash match`() {
        val ext = availExt("com.example.ext", "sig_exact")
        val result = RecommendationBundleSourceResolver.resolveAvailableExtension(
            item(sourceId = 9_999L, pkgName = "com.example.ext", sigHash = "sig_exact"),
            listOf(ext, availExt("com.other.ext")),
        )
        val resolution = result as RecommendationBundleSourceResolver.AvailableExtensionResolution.Unambiguous
        assertEquals(ext, resolution.ext)
    }

    @Test
    fun `resolveAvailableExtension returns Ambiguous when multiple extensions share pkgName and sigHash`() {
        val ext1 = availExt("com.example.ext", "sig_shared")
        val ext2 = availExt("com.example.ext", "sig_shared")
        val result = RecommendationBundleSourceResolver.resolveAvailableExtension(
            item(sourceId = 9_999L, pkgName = "com.example.ext", sigHash = "sig_shared"),
            listOf(ext1, ext2),
        )
        val resolution = result as RecommendationBundleSourceResolver.AvailableExtensionResolution.Ambiguous
        assertEquals(2, resolution.candidates.size)
    }

    @Test
    fun `resolveAvailableExtension falls back to pkgName-only when sigHash provided but no sigHash match`() {
        val ext = availExt("com.example.ext", "sig_other")
        val result = RecommendationBundleSourceResolver.resolveAvailableExtension(
            item(sourceId = 9_999L, pkgName = "com.example.ext", sigHash = "sig_nomatch"),
            listOf(ext),
        )
        val resolution = result as RecommendationBundleSourceResolver.AvailableExtensionResolution.Unambiguous
        assertEquals(ext, resolution.ext)
    }

    @Test
    fun `resolveAvailableExtension returns Ambiguous on pkgName-only match when multiple extensions share same pkgName`() {
        val ext1 = availExt("com.example.ext", "sig_a")
        val ext2 = availExt("com.example.ext", "sig_b")
        val result = RecommendationBundleSourceResolver.resolveAvailableExtension(
            item(sourceId = 9_999L, pkgName = "com.example.ext"),
            listOf(ext1, ext2),
        )
        val resolution = result as RecommendationBundleSourceResolver.AvailableExtensionResolution.Ambiguous
        assertEquals(2, resolution.candidates.size)
    }
}

// KMK <--
