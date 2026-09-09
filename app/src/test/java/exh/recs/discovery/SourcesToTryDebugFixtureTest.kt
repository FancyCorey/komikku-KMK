package exh.recs.discovery

import eu.kanade.tachiyomi.extension.model.Extension
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

class SourcesToTryDebugFixtureTest {
    private val signer = "a".repeat(64)

    private fun extension(
        packageName: String,
        signerHash: String = signer,
        indexUrl: String = "http://127.0.0.1:38291/index.json",
        storeName: String = SourcesToTryDebugFixture.STORE_NAME,
        sourceId: Long = if (packageName.endsWith(".alpha")) 910000000000000001L else 910000000000000002L,
        sourceName: String = if (packageName.endsWith(".alpha")) "Fixture Source Alpha" else "Fixture Source Beta",
        versionName: String = "1.6.0",
        versionCode: Long = 1L,
        lang: String = "en",
        isNsfw: Boolean = false,
        sources: List<Extension.Available.Source>? = null,
    ): Extension.Available {
        val baseUrl = indexUrl.removeSuffix("/index.json")
        val actualSources = sources ?: listOf(
            Extension.Available.Source(sourceId, lang, sourceName, ""),
        )
        return Extension.Available(
            name = sourceName,
            pkgName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = 1.6,
            lang = lang,
            isNsfw = isNsfw,
            signatureHash = signerHash,
            storeName = storeName,
            sources = actualSources,
            apkUrl = "$baseUrl/apks/$packageName.apk",
            iconUrl = "$baseUrl/icons/$packageName.png",
            store = ExtensionStore(
                indexUrl = indexUrl,
                name = storeName,
                badgeLabel = SourcesToTryDebugFixture.STORE_BADGE,
                signingKey = signerHash,
                contact = ExtensionStore.Contact(baseUrl, null),
                isLegacy = false,
                extensionListUrl = null,
            ),
        )
    }

    private fun append(
        available: List<Extension.Available>,
        isDebugBuild: Boolean = true,
        mode: SourcesToTryDebugFixtureMode = SourcesToTryDebugFixtureMode.SUGGESTION_VISIBLE,
        signerHash: String = signer,
        installedPackages: Set<String> = emptySet(),
        untrustedPackages: Set<String> = emptySet(),
        languages: Set<String> = setOf("en"),
        showNsfw: Boolean = true,
        blockExplicit: Boolean = false,
        dismissed: Set<String> = emptySet(),
        disliked: Set<String> = emptySet(),
        qualityDisliked: Set<String> = emptySet(),
        evaluations: Map<String, SourceEvaluationVerdict> = emptyMap(),
        existing: List<NonInstalledSourceSuggestion> = emptyList(),
    ) = SourcesToTryDebugFixture.appendSuggestions(
        mode = mode,
        isDebugBuild = isDebugBuild,
        expectedSignerSha256 = signerHash,
        available = available,
        installedPackages = installedPackages,
        untrustedPackages = untrustedPackages,
        enabledLanguages = languages,
        showNsfw = showNsfw,
        blockExplicit = blockExplicit,
        dismissed = dismissed,
        dislikedKeys = disliked,
        qualityDislikedKeys = qualityDisliked,
        evaluations = evaluations,
        existing = existing,
    )

    @Test
    fun `only exact synthetic identities from the exact loopback store are appended in stable order`() {
        val unrelated = extension("ordinary.pkg")
        val beta = extension("app.komikku.fixture.sources.beta")
        val alpha = extension("app.komikku.fixture.sources.alpha")

        val result = append(listOf(unrelated, beta, alpha))

        assertEquals(
            listOf("app.komikku.fixture.sources.alpha", "app.komikku.fixture.sources.beta"),
            result.map { it.extension.pkgName },
        )
        assertTrue(result.all { it.reasons == listOf(NonInstalledSuggestionReason.NeedsTesting) })
    }

    @Test
    fun `off release and unprovisioned signer paths preserve the ordinary list exactly`() {
        val exact = extension("app.komikku.fixture.sources.alpha")
        val existing = listOf(
            NonInstalledSourceSuggestion(exact, exact.sources.single(), 0.68, SuggestionConfidence.MEDIUM, emptyList()),
        )

        assertEquals(existing, append(listOf(exact), isDebugBuild = false, existing = existing))
        assertEquals(existing, append(listOf(exact), mode = SourcesToTryDebugFixtureMode.OFF, existing = existing))
        assertEquals(existing, append(listOf(exact), signerHash = "", existing = existing))
    }

    @Test
    fun `empty fixture suppresses suggestions only in debug mode`() {
        val exact = extension("app.komikku.fixture.sources.alpha")
        val existing = listOf(
            NonInstalledSourceSuggestion(exact, exact.sources.single(), 0.68, SuggestionConfidence.MEDIUM, emptyList()),
        )

        assertTrue(
            append(
                listOf(exact),
                mode = SourcesToTryDebugFixtureMode.EMPTY,
                existing = existing,
            ).isEmpty(),
        )
        assertEquals(
            existing,
            append(
                listOf(exact),
                isDebugBuild = false,
                mode = SourcesToTryDebugFixtureMode.EMPTY,
                existing = existing,
            ),
        )
    }

    @Test
    fun `wrong signer store transport or source metadata fails closed`() {
        val packageName = "app.komikku.fixture.sources.alpha"
        val candidates = listOf(
            extension(packageName, signerHash = "b".repeat(64)),
            extension(packageName, storeName = "Other Store"),
            extension(packageName, indexUrl = "https://127.0.0.1:38291/index.json"),
            extension(packageName, indexUrl = "http://example.invalid/index.json"),
            extension(packageName, indexUrl = "http://127.0.0.1:38291/other.json"),
            extension(packageName, versionName = "1.6.1"),
            extension(packageName, versionCode = 2L),
            extension(packageName, sourceId = 99),
            extension(packageName, sources = emptyList()),
            extension(
                packageName,
                sources = listOf(
                    Extension.Available.Source(910000000000000001L, "en", "Fixture Source Alpha", ""),
                    Extension.Available.Source(910000000000000003L, "en", "Extra", ""),
                ),
            ),
        )

        candidates.forEach { candidate ->
            assertTrue(append(listOf(candidate)).isEmpty(), candidate.toString())
        }
    }

    @Test
    fun `installed untrusted language nsfw and user policy filters cannot be bypassed`() {
        val alpha = extension("app.komikku.fixture.sources.alpha")
        val key = buildDismissalKey(alpha.signatureHash, alpha.pkgName, alpha.sources.single().id)
        val preferenceKey = RecommendationSourcePreferenceStore.availableKey(
            alpha.signatureHash,
            alpha.pkgName,
            alpha.sources.single().id,
        )

        assertTrue(append(listOf(alpha), installedPackages = setOf(alpha.pkgName)).isEmpty())
        assertTrue(append(listOf(alpha), untrustedPackages = setOf(alpha.pkgName)).isEmpty())
        assertTrue(append(listOf(alpha), languages = setOf("ja")).isEmpty())
        assertTrue(append(listOf(extension(alpha.pkgName, isNsfw = true)), showNsfw = false).isEmpty())
        assertTrue(append(listOf(alpha), dismissed = setOf(key)).isEmpty())
        assertTrue(append(listOf(alpha), disliked = setOf(preferenceKey)).isEmpty())
        assertTrue(append(listOf(alpha), qualityDisliked = setOf(preferenceKey)).isEmpty())
        assertTrue(append(listOf(alpha), evaluations = mapOf(key to SourceEvaluationVerdict.REJECTED)).isEmpty())
        assertTrue(
            append(
                listOf(alpha),
                blockExplicit = true,
                evaluations = mapOf(key to SourceEvaluationVerdict.EXPLICIT_HEAVY),
            ).isEmpty(),
        )
    }

    @Test
    fun `existing dismissal identity is never duplicated`() {
        val alpha = extension("app.komikku.fixture.sources.alpha")
        val existing = append(listOf(alpha))

        assertEquals(existing, append(listOf(alpha), existing = existing))
    }
}
