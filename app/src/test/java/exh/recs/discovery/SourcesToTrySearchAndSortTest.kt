package exh.recs.discovery

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK v0.8.10 -->
class SourcesToTrySearchAndSortTest {

    private fun availableExtension(
        pkgName: String,
        name: String,
        lang: String = "en",
        storeName: String = "Test Store",
        signatureHash: String = "hash1",
    ) = Extension.Available(
        name = name,
        pkgName = pkgName,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.5,
        lang = lang,
        isNsfw = false,
        signatureHash = signatureHash,
        storeName = storeName,
        sources = emptyList(),
        apkUrl = "https://example.com/apk/$pkgName.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "https://example.com/index.json",
            name = storeName,
            badgeLabel = storeName,
            signingKey = signatureHash,
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    private fun suggestion(
        pkgName: String = "eu.kanade.tachiyomi.extension.en.example",
        name: String = "Example Source",
        lang: String = "en",
        storeName: String = "Test Store",
        signatureHash: String = "hash1",
        score: Double = 0.5,
        confidence: SuggestionConfidence = SuggestionConfidence.LOW,
        reasons: List<NonInstalledSuggestionReason> = listOf(NonInstalledSuggestionReason.NeedsTesting),
    ) = NonInstalledSourceSuggestion(
        extension = availableExtension(pkgName, name, lang, storeName, signatureHash),
        source = null,
        score = score,
        confidence = confidence,
        reasons = reasons,
    )

    @Test
    fun `blank query returns every suggestion unfiltered`() {
        val suggestions = listOf(suggestion(name = "One"), suggestion(pkgName = "b", name = "Two"))
        assertEquals(suggestions, SourcesToTrySearchAndSort.search(suggestions, ""))
        assertEquals(suggestions, SourcesToTrySearchAndSort.search(suggestions, "   "))
    }

    @Test
    fun `matches by display name, case-insensitively`() {
        val target = suggestion(name = "MangaDex")
        val other = suggestion(pkgName = "b", name = "Something Else")
        val result = SourcesToTrySearchAndSort.search(listOf(target, other), "mangadex")
        assertEquals(listOf(target), result)
    }

    @Test
    fun `matches by language`() {
        val target = suggestion(name = "A", lang = "fr")
        val other = suggestion(pkgName = "b", name = "B", lang = "en")
        val result = SourcesToTrySearchAndSort.search(listOf(target, other), "fr")
        assertEquals(listOf(target), result)
    }

    @Test
    fun `matches by store repository name`() {
        val target = suggestion(name = "A", storeName = "Community Store")
        val other = suggestion(pkgName = "b", name = "B", storeName = "Other Store")
        val result = SourcesToTrySearchAndSort.search(listOf(target, other), "community")
        assertEquals(listOf(target), result)
    }

    @Test
    fun `matches by package identity`() {
        val target = suggestion(pkgName = "eu.kanade.tachiyomi.extension.en.stableid", name = "A")
        val other = suggestion(pkgName = "eu.kanade.tachiyomi.extension.en.other", name = "B")
        val result = SourcesToTrySearchAndSort.search(listOf(target, other), "stableid")
        assertEquals(listOf(target), result)
    }

    @Test
    fun `a query matching nothing returns an empty list`() {
        val suggestions = listOf(suggestion(name = "One Piece"))
        assertTrue(SourcesToTrySearchAndSort.search(suggestions, "nonexistent").isEmpty())
    }

    @Test
    fun `bestFitRank ranks strong fit above worth trying above liked above plain`() {
        val strongFit = suggestion(reasons = listOf(NonInstalledSuggestionReason.EvaluatedStrongFit))
        val worthTrying = suggestion(reasons = listOf(NonInstalledSuggestionReason.EvaluatedWorthTrying))
        val liked = suggestion(reasons = listOf(NonInstalledSuggestionReason.UserLikedSource))
        val plain = suggestion(reasons = listOf(NonInstalledSuggestionReason.NeedsTesting))

        assertTrue(SourcesToTrySearchAndSort.bestFitRank(strongFit) > SourcesToTrySearchAndSort.bestFitRank(worthTrying))
        assertTrue(SourcesToTrySearchAndSort.bestFitRank(worthTrying) > SourcesToTrySearchAndSort.bestFitRank(liked))
        assertTrue(SourcesToTrySearchAndSort.bestFitRank(liked) > SourcesToTrySearchAndSort.bestFitRank(plain))
    }

    @Test
    fun `sort by BEST_FIT orders strong fit first regardless of input order`() {
        val strongFit = suggestion(pkgName = "a", name = "Z Source", reasons = listOf(NonInstalledSuggestionReason.EvaluatedStrongFit))
        val plain = suggestion(pkgName = "b", name = "A Source", reasons = listOf(NonInstalledSuggestionReason.NeedsTesting))
        val result = SourcesToTrySearchAndSort.sort(listOf(plain, strongFit), SourcesToTrySortMode.BEST_FIT)
        assertEquals(listOf(strongFit, plain), result)
    }

    @Test
    fun `sort by NAME_AZ is case-insensitive alphabetical`() {
        val b = suggestion(pkgName = "b", name = "banana")
        val a = suggestion(pkgName = "a", name = "Apple")
        val result = SourcesToTrySearchAndSort.sort(listOf(b, a), SourcesToTrySortMode.NAME_AZ)
        assertEquals(listOf(a, b), result)
    }

    @Test
    fun `sort by LANGUAGE groups by language then name`() {
        val enB = suggestion(pkgName = "a", name = "B Source", lang = "en")
        val enA = suggestion(pkgName = "b", name = "A Source", lang = "en")
        val fr = suggestion(pkgName = "c", name = "C Source", lang = "fr")
        val result = SourcesToTrySearchAndSort.sort(listOf(fr, enB, enA), SourcesToTrySortMode.LANGUAGE)
        assertEquals(listOf(enA, enB, fr), result)
    }

    @Test
    fun `search never mutates the input list`() {
        val suggestions = listOf(suggestion(name = "One"), suggestion(pkgName = "b", name = "Two"))
        val snapshot = suggestions.toList()
        SourcesToTrySearchAndSort.search(suggestions, "one")
        assertEquals(snapshot, suggestions)
    }
}
// KMK <--
