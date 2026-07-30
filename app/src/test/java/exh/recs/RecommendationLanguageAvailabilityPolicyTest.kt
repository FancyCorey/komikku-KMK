package exh.recs

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK v0.8.12 -->
class RecommendationLanguageAvailabilityPolicyTest {

    private class FakeSource(override val id: Long, override val lang: String) : Source {
        override val name: String = "Fake $id"
        override val supportsLatest: Boolean = false
        override suspend fun getPopularManga(page: Int) = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList) =
            throw UnsupportedOperationException()
        override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()
        override fun getFilterList() = eu.kanade.tachiyomi.source.model.FilterList()
        override suspend fun getMangaUpdate(
            manga: eu.kanade.tachiyomi.source.model.SManga,
            chapters: List<eu.kanade.tachiyomi.source.model.SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ) = throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: eu.kanade.tachiyomi.source.model.SChapter) = throw UnsupportedOperationException()
    }

    private fun availableExtension(lang: String, sourceLangs: List<String> = emptyList()) = Extension.Available(
        name = "ext-$lang",
        pkgName = "pkg.$lang",
        versionName = "1.0",
        versionCode = 1,
        libVersion = 1.0,
        lang = lang,
        isNsfw = false,
        signatureHash = "sig",
        storeName = "store",
        sources = sourceLangs.mapIndexed { i, l -> Extension.Available.Source(id = i.toLong(), lang = l, name = "s-$l", baseUrl = "") },
        apkUrl = "",
        iconUrl = "",
        store = mihon.domain.extension.model.ExtensionStore(
            indexUrl = "https://example.com/index.json",
            name = "store",
            badgeLabel = "store",
            signingKey = "sig",
            contact = mihon.domain.extension.model.ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    @Test
    fun `a selected non-English language remains visible with no installed source`() {
        val result = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = setOf("fr"),
            installedVisibleSources = emptyList(),
            availableExtensions = emptyList(),
        )
        assertEquals(listOf("fr"), result)
    }

    @Test
    fun `available extension source languages are included`() {
        val result = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = emptySet(),
            installedVisibleSources = emptyList(),
            availableExtensions = listOf(availableExtension("es", listOf("es", "pt"))),
        )
        assertEquals(listOf("es", "pt"), result)
    }

    @Test
    fun `installed source languages are included`() {
        val result = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = emptySet(),
            installedVisibleSources = listOf(FakeSource(1, "de")),
            availableExtensions = emptyList(),
        )
        assertEquals(listOf("de"), result)
    }

    @Test
    fun `blank languages are excluded`() {
        val result = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = setOf("  ", ""),
            installedVisibleSources = listOf(FakeSource(1, "")),
            availableExtensions = listOf(availableExtension("  ")),
        )
        assertEquals(RecommendationSourceFilter.DefaultLanguages.toList(), result)
    }

    @Test
    fun `empty input falls back to English`() {
        val result = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = emptySet(),
            installedVisibleSources = emptyList(),
            availableExtensions = emptyList(),
        )
        assertEquals(listOf("en"), result)
    }

    @Test
    fun `local source is excluded from installed source languages`() {
        val result = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = emptySet(),
            installedVisibleSources = listOf(FakeSource(0, "other")),
            availableExtensions = emptyList(),
        )
        assertEquals(listOf("en"), result, "Local Source (id 0) must never contribute a language chip")
    }

    @Test
    fun `result is deterministic alphabetical order combining all three sources`() {
        val result = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = setOf("fr"),
            installedVisibleSources = listOf(FakeSource(1, "de")),
            availableExtensions = listOf(availableExtension("es")),
        )
        assertEquals(listOf("de", "es", "fr"), result)
    }
}
// KMK <--
