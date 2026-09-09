package exh.recs.discovery

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.interactor.GetSourceRecommendationFit

class GetNonInstalledSourceSuggestionsTest {
    private val signer = "a".repeat(64)

    @Test
    fun `mounted discovery emits every exact fixture and reacts to catalog removal`() = runTest {
        val available = MutableStateFlow(
            listOf(
                extension("ordinary.pkg", 1, "Ordinary"),
                extension("app.komikku.fixture.sources.beta", 910000000000000002L, "Fixture Source Beta"),
                extension("app.komikku.fixture.sources.alpha", 910000000000000001L, "Fixture Source Alpha"),
            ),
        )
        val subject = subject(available, isDebugBuild = true, fixtureSigner = signer)

        assertEquals(
            listOf("app.komikku.fixture.sources.alpha", "app.komikku.fixture.sources.beta"),
            subject.subscribe().first().map { it.extension.pkgName },
        )

        val afterRemoval = async {
            subject.subscribe().first { rows -> rows.size == 1 }
        }
        runCurrent()
        available.value = listOf(extension("app.komikku.fixture.sources.beta", 910000000000000002L, "Fixture Source Beta"))
        assertEquals("app.komikku.fixture.sources.beta", afterRemoval.await().single().extension.pkgName)

        val afterFixtureRemoval = async { subject.subscribe().first { it.isEmpty() } }
        runCurrent()
        available.value = listOf(extension("ordinary.pkg", 1, "Ordinary"))
        assertTrueEmpty(afterFixtureRemoval.await())
    }

    @Test
    fun `mounted release and mode-off paths never activate exact fixture entries`() = runTest {
        val available = MutableStateFlow(
            listOf(extension("app.komikku.fixture.sources.alpha", 910000000000000001L, "Fixture Source Alpha")),
        )

        assertTrueEmpty(subject(available, isDebugBuild = false, fixtureSigner = signer).subscribe().first())
        assertTrueEmpty(subject(available, isDebugBuild = true, fixtureSigner = "").subscribe().first())
    }

    private fun subject(
        available: MutableStateFlow<List<Extension.Available>>,
        isDebugBuild: Boolean,
        fixtureSigner: String,
    ): GetNonInstalledSourceSuggestions {
        val preferences = SourcePreferences(FakePreferenceStore()).apply {
            sourcesToTryFixtureMode().set(SourcesToTryDebugFixtureMode.SUGGESTION_VISIBLE.prefValue)
            recommendationSourceLanguages().set(setOf("en"))
            showNsfwSource().set(true)
            blockExplicitPornHentaiSources().set(false)
        }
        val extensionManager = mockk<ExtensionManager>().also { manager ->
            every { manager.availableExtensionsFlow } returns available
            every { manager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
            every { manager.untrustedExtensionsFlow } returns MutableStateFlow(emptyList())
        }
        val evaluations = mockk<GetSourceEvaluations>().also { interactor ->
            every { interactor.subscribeAll() } returns MutableStateFlow(emptyList())
        }
        val fits = mockk<GetSourceRecommendationFit>().also { interactor ->
            coEvery { interactor.awaitAll() } returns emptyList()
        }
        return GetNonInstalledSourceSuggestions(
            extensionManager = extensionManager,
            sourcePreferences = preferences,
            getSourceEvaluations = evaluations,
            getSourceRecommendationFit = fits,
            isDebugBuild = isDebugBuild,
            fixtureSignerSha256 = fixtureSigner,
        )
    }

    private fun extension(packageName: String, sourceId: Long, sourceName: String): Extension.Available {
        val baseUrl = "http://127.0.0.1:38291"
        return Extension.Available(
            name = sourceName,
            pkgName = packageName,
            versionName = "1.6.0",
            versionCode = 1,
            libVersion = 1.6,
            lang = "en",
            isNsfw = false,
            signatureHash = signer,
            storeName = SourcesToTryDebugFixture.STORE_NAME,
            sources = listOf(Extension.Available.Source(sourceId, "en", sourceName, "")),
            apkUrl = "$baseUrl/apks/$packageName.apk",
            iconUrl = "$baseUrl/icons/$packageName.png",
            store = ExtensionStore(
                indexUrl = "$baseUrl/index.json",
                name = SourcesToTryDebugFixture.STORE_NAME,
                badgeLabel = SourcesToTryDebugFixture.STORE_BADGE,
                signingKey = signer,
                contact = ExtensionStore.Contact(baseUrl, null),
                isLegacy = false,
                extensionListUrl = null,
            ),
        )
    }

    private fun assertTrueEmpty(rows: List<NonInstalledSourceSuggestion>) {
        assertEquals(emptyList<NonInstalledSourceSuggestion>(), rows)
    }
}
