package exh.recs.discovery

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourcesToTryDebugFixtureTest {
    private fun extension(pkgName: String) = Extension.Available(
        name = "Fixture Source",
        pkgName = pkgName,
        versionName = "1.0.0",
        versionCode = 1L,
        libVersion = 1.5,
        lang = "en",
        isNsfw = false,
        signatureHash = "fixture-signature",
        storeName = "Fixture Store",
        sources = emptyList(),
        apkUrl = "https://example.invalid/fixture.apk",
        iconUrl = "",
        store = ExtensionStore(
            indexUrl = "https://example.invalid/index.json",
            name = "Fixture Store",
            badgeLabel = "Fixture",
            signingKey = "fixture-signature",
            contact = ExtensionStore.Contact(website = "", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        ),
    )

    @Test
    fun `release builds never activate the fixture`() {
        val result = SourcesToTryDebugFixture.appendSuggestion(
            mode = SourcesToTryDebugFixtureMode.SUGGESTION_VISIBLE,
            isDebugBuild = false,
            available = listOf(extension("fixture.pkg")),
            installed = emptySet(),
            untrusted = emptySet(),
            enabledLanguages = setOf("en"),
            showNsfw = true,
            blockExplicit = false,
            existing = emptyList(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `debug fixture selects a deterministic not-installed catalogue extension`() {
        val result = SourcesToTryDebugFixture.appendSuggestion(
            mode = SourcesToTryDebugFixtureMode.SUGGESTION_VISIBLE,
            isDebugBuild = true,
            available = listOf(extension("z.pkg"), extension("a.pkg")),
            installed = emptySet(),
            untrusted = emptySet(),
            enabledLanguages = setOf("en"),
            showNsfw = true,
            blockExplicit = false,
            existing = emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals("a.pkg", result.single().extension.pkgName)
        assertEquals(listOf(NonInstalledSuggestionReason.NeedsTesting), result.single().reasons)
    }

    @Test
    fun `debug fixture does not duplicate an existing suggestion`() {
        val existing = SourcesToTryDebugFixture.appendSuggestion(
            mode = SourcesToTryDebugFixtureMode.SUGGESTION_VISIBLE,
            isDebugBuild = true,
            available = listOf(extension("a.pkg")),
            installed = emptySet(),
            untrusted = emptySet(),
            enabledLanguages = setOf("en"),
            showNsfw = true,
            blockExplicit = false,
            existing = emptyList(),
        )
        val repeated = SourcesToTryDebugFixture.appendSuggestion(
            mode = SourcesToTryDebugFixtureMode.SUGGESTION_VISIBLE,
            isDebugBuild = true,
            available = listOf(extension("a.pkg")),
            installed = emptySet(),
            untrusted = emptySet(),
            enabledLanguages = setOf("en"),
            showNsfw = true,
            blockExplicit = false,
            existing = existing,
        )

        assertEquals(existing, repeated)
    }
}
