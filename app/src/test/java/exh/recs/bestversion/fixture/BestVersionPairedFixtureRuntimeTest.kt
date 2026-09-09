package exh.recs.bestversion.fixture

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import exh.util.FakePreferenceStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mihon.domain.migration.models.MigrationFlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import java.util.UUID

class BestVersionPairedFixtureRuntimeTest {
    private val signer = "b".repeat(64)

    @Test
    fun `runtime derives an allowed activation only from both exact installed fixtures`() {
        val activation = activationFor(
            listOf(
                extension(
                    BestVersionPairedFixtureGate.ALPHA_PACKAGE,
                    BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
                    "Fixture Source Alpha",
                ),
                extension(
                    BestVersionPairedFixtureGate.BETA_PACKAGE,
                    BestVersionPairedFixtureGate.BETA_SOURCE_ID,
                    "Fixture Source Beta",
                ),
                extension("unrelated.package", 5L, "Unrelated"),
            ),
        )

        assertTrue(BestVersionPairedFixtureGate.isAllowed(activation))
        assertTrue(activation.installedSources.size == 2)
    }

    @Test
    fun `runtime fails closed for wrong version name signer id or source count`() {
        val alpha = extension(
            BestVersionPairedFixtureGate.ALPHA_PACKAGE,
            BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
            "Fixture Source Alpha",
        )
        val beta = extension(
            BestVersionPairedFixtureGate.BETA_PACKAGE,
            BestVersionPairedFixtureGate.BETA_SOURCE_ID,
            "Fixture Source Beta",
        )

        assertFalse(BestVersionPairedFixtureGate.isAllowed(activationFor(listOf(alpha.copy(versionCode = 2L), beta))))
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activationFor(listOf(alpha.copy(name = "Wrong"), beta))))
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activationFor(listOf(alpha.copy(signatureHash = "c".repeat(64)), beta))))
        val wrongSource = source(99L)
        assertFalse(BestVersionPairedFixtureGate.isAllowed(activationFor(listOf(alpha.copy(sources = listOf(wrongSource)), beta))))
        assertFalse(
            BestVersionPairedFixtureGate.isAllowed(
                activationFor(listOf(alpha.copy(sources = listOf(source(alpha.sources.single().id), source(7L))), beta)),
            ),
        )
    }

    @Test
    fun `runtime resolves only a complete hash-identical owned route and revalidates its target`() = runTest {
        val operationId = UUID.randomUUID().toString()
        val manifest = completeManifest(operationId)
        val preferences = FakePreferenceStore()
        assertTrue(PreferenceBestVersionPairedFixtureRecoveryStore(preferences).save(manifest))
        val sourcePreferences = SourcePreferences(preferences).apply {
            evaluationMode().set(true)
            bestVersionPairedFixtureMode().set(BestVersionPairedFixtureMode.PAIRED_RECORDS.prefValue)
        }
        val observed = BestVersionPairedFixtureObservedState(mangaRows = listOf("owned"))
        val dataStore = mockk<BestVersionPairedFixtureDataStore>()
        coEvery { dataStore.inspect(any(), manifest) } returns observed
        val marker = manifest.ownershipMarker
        val origin = fixtureManga(manifest.originMangaId!!, BestVersionPairedFixtureGate.ALPHA_SOURCE_ID, "/kmk-fixture/f2/origin", marker)
        val target = fixtureManga(manifest.targetMangaId!!, BestVersionPairedFixtureGate.BETA_SOURCE_ID, "/kmk-fixture/f2/target", marker)
        val mangaRepository = mockk<MangaRepository>()
        coEvery {
            mangaRepository.getMangaByUrlAndSourceId("/kmk-fixture/f2/target", BestVersionPairedFixtureGate.BETA_SOURCE_ID)
        } returns target
        val alpha = source(BestVersionPairedFixtureGate.ALPHA_SOURCE_ID)
        val beta = source(BestVersionPairedFixtureGate.BETA_SOURCE_ID)
        val sourceManager = mockk<SourceManager>()
        every { sourceManager.get(BestVersionPairedFixtureGate.ALPHA_SOURCE_ID) } returns alpha
        every { sourceManager.get(BestVersionPairedFixtureGate.BETA_SOURCE_ID) } returns beta
        val runtime = runtime(preferences, sourcePreferences, dataStore, mangaRepository, sourceManager)

        val binding = runtime.routeResolver.resolve(operationId, origin)

        assertTrue(binding != null)
        assertEquals(setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY), binding!!.migrationPresetFlags)
        assertEquals(listOf(beta), binding.candidateSearchGateway.getMatchingSources())
        assertTrue(binding.isCurrentTarget(target))

        coEvery {
            mangaRepository.getMangaByUrlAndSourceId("/kmk-fixture/f2/target", BestVersionPairedFixtureGate.BETA_SOURCE_ID)
        } returns target.copy(notes = "foreign")
        assertFalse(binding.isCurrentTarget(target))
    }

    @Test
    fun `runtime refuses wrong token incomplete manifest and state hash drift`() = runTest {
        val operationId = UUID.randomUUID().toString()
        val manifest = completeManifest(operationId)
        val preferences = FakePreferenceStore()
        assertTrue(PreferenceBestVersionPairedFixtureRecoveryStore(preferences).save(manifest))
        val sourcePreferences = SourcePreferences(preferences).apply {
            evaluationMode().set(true)
            bestVersionPairedFixtureMode().set(BestVersionPairedFixtureMode.PAIRED_RECORDS.prefValue)
        }
        val dataStore = mockk<BestVersionPairedFixtureDataStore>()
        coEvery { dataStore.inspect(any(), any()) } returns BestVersionPairedFixtureObservedState(mangaRows = listOf("drift"))
        val mangaRepository = mockk<MangaRepository>(relaxed = true)
        val sourceManager = mockk<SourceManager>(relaxed = true)
        val runtime = runtime(preferences, sourcePreferences, dataStore, mangaRepository, sourceManager)
        val origin = fixtureManga(
            manifest.originMangaId!!,
            BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
            "/kmk-fixture/f2/origin",
            manifest.ownershipMarker,
        )

        assertNull(runtime.routeResolver.resolve(UUID.randomUUID().toString(), origin))
        assertNull(runtime.routeResolver.resolve(operationId, origin))

        assertTrue(
            PreferenceBestVersionPairedFixtureRecoveryStore(preferences).save(
                manifest.copy(pendingStep = BestVersionPairedFixtureStep.CATEGORY),
            ),
        )
        assertNull(runtime.routeResolver.resolve(operationId, origin))
    }

    @Test
    fun `runtime refuses explicit route when activation becomes unauthorized`() = runTest {
        val operationId = UUID.randomUUID().toString()
        val manifest = completeManifest(operationId)
        val preferences = FakePreferenceStore()
        assertTrue(PreferenceBestVersionPairedFixtureRecoveryStore(preferences).save(manifest))
        val sourcePreferences = SourcePreferences(preferences).apply {
            evaluationMode().set(false)
            bestVersionPairedFixtureMode().set(BestVersionPairedFixtureMode.PAIRED_RECORDS.prefValue)
        }
        val runtime = runtime(
            preferences,
            sourcePreferences,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
        val origin = fixtureManga(
            manifest.originMangaId!!,
            BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
            "/kmk-fixture/f2/origin",
            manifest.ownershipMarker,
        )

        assertNull(runtime.routeResolver.resolve(operationId, origin))
    }

    @Test
    fun `runtime refuses source-manager identity drift after activation`() = runTest {
        val operationId = UUID.randomUUID().toString()
        val manifest = completeManifest(operationId)
        val preferences = FakePreferenceStore()
        assertTrue(PreferenceBestVersionPairedFixtureRecoveryStore(preferences).save(manifest))
        val sourcePreferences = SourcePreferences(preferences).apply {
            evaluationMode().set(true)
            bestVersionPairedFixtureMode().set(BestVersionPairedFixtureMode.PAIRED_RECORDS.prefValue)
        }
        val observed = BestVersionPairedFixtureObservedState(mangaRows = listOf("owned"))
        val dataStore = mockk<BestVersionPairedFixtureDataStore>()
        coEvery { dataStore.inspect(any(), manifest) } returns observed
        val origin = fixtureManga(
            manifest.originMangaId!!,
            BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
            "/kmk-fixture/f2/origin",
            manifest.ownershipMarker,
        )
        val target = fixtureManga(
            manifest.targetMangaId!!,
            BestVersionPairedFixtureGate.BETA_SOURCE_ID,
            "/kmk-fixture/f2/target",
            manifest.ownershipMarker,
        )
        val mangaRepository = mockk<MangaRepository>()
        coEvery { mangaRepository.getMangaByUrlAndSourceId(any(), any()) } returns target
        val wrongName = mockk<Source>()
        every { wrongName.id } returns BestVersionPairedFixtureGate.BETA_SOURCE_ID
        every { wrongName.name } returns "Wrong"
        val sourceManager = mockk<SourceManager>()
        every { sourceManager.get(BestVersionPairedFixtureGate.BETA_SOURCE_ID) } returns wrongName
        every { sourceManager.get(BestVersionPairedFixtureGate.ALPHA_SOURCE_ID) } returns source(BestVersionPairedFixtureGate.ALPHA_SOURCE_ID)
        val runtime = runtime(preferences, sourcePreferences, dataStore, mangaRepository, sourceManager)

        assertNull(runtime.routeResolver.resolve(operationId, origin))
    }

    @Test
    fun `runtime route inspection propagates cancellation`() {
        val operationId = UUID.randomUUID().toString()
        val manifest = completeManifest(operationId)
        val preferences = FakePreferenceStore()
        assertTrue(PreferenceBestVersionPairedFixtureRecoveryStore(preferences).save(manifest))
        val sourcePreferences = SourcePreferences(preferences).apply {
            evaluationMode().set(true)
            bestVersionPairedFixtureMode().set(BestVersionPairedFixtureMode.PAIRED_RECORDS.prefValue)
        }
        val dataStore = mockk<BestVersionPairedFixtureDataStore>()
        coEvery { dataStore.inspect(any(), manifest) } throws CancellationException("route-inspection-cancelled")
        val runtime = runtime(
            preferences,
            sourcePreferences,
            dataStore,
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
        val origin = fixtureManga(
            manifest.originMangaId!!,
            BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
            "/kmk-fixture/f2/origin",
            manifest.ownershipMarker,
        )

        assertThrows(CancellationException::class.java) {
            runTest { runtime.routeResolver.resolve(operationId, origin) }
        }
    }

    private fun activationFor(installed: List<Extension.Installed>) =
        BestVersionPairedFixtureRuntime.activationFor(
            isDebugBuild = true,
            mode = BestVersionPairedFixtureMode.PAIRED_RECORDS,
            evaluationModeEnabled = true,
            fixtureProfile = BestVersionPairedFixtureGate.ISOLATED_PROFILE,
            expectedSignerSha256 = signer,
            installedExtensions = installed,
        )

    private fun runtime(
        preferenceStore: FakePreferenceStore,
        sourcePreferences: SourcePreferences,
        dataStore: BestVersionPairedFixtureDataStore,
        mangaRepository: MangaRepository,
        sourceManager: SourceManager,
    ): BestVersionPairedFixtureRuntime {
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.installedExtensionsFlow } returns MutableStateFlow(
            listOf(
                extension(
                    BestVersionPairedFixtureGate.ALPHA_PACKAGE,
                    BestVersionPairedFixtureGate.ALPHA_SOURCE_ID,
                    "Fixture Source Alpha",
                ),
                extension(
                    BestVersionPairedFixtureGate.BETA_PACKAGE,
                    BestVersionPairedFixtureGate.BETA_SOURCE_ID,
                    "Fixture Source Beta",
                ),
            ),
        )
        return BestVersionPairedFixtureRuntime(
            extensionManager = extensionManager,
            sourcePreferences = sourcePreferences,
            preferenceStore = preferenceStore,
            dataStore = dataStore,
            mangaRepository = mangaRepository,
            sourceManager = sourceManager,
            isDebugBuild = true,
            fixtureProfile = BestVersionPairedFixtureGate.ISOLATED_PROFILE,
            expectedSignerSha256 = signer,
        )
    }

    private fun completeManifest(operationId: String): BestVersionPairedFixtureManifest {
        val observed = BestVersionPairedFixtureObservedState(mangaRows = listOf("owned"))
        return BestVersionPairedFixtureManifest(
            operationId = operationId,
            completedSteps = BestVersionPairedFixtureStep.entries.toSet(),
            originMangaId = 101L,
            targetMangaId = 102L,
            seededStateHash = observed.sha256(),
        )
    }

    private fun fixtureManga(id: Long, source: Long, url: String, marker: String): Manga =
        Manga.create().copy(id = id, source = source, url = url, notes = marker, ogTitle = "Fixture")

    private fun extension(packageName: String, sourceId: Long, extensionName: String) = Extension.Installed(
        name = extensionName,
        pkgName = packageName,
        versionName = "1.6.0",
        versionCode = 1L,
        libVersion = 1.6,
        lang = "en",
        isNsfw = false,
        signatureHash = signer,
        pkgFactory = null,
        sources = listOf(source(sourceId)),
        icon = null,
        isShared = false,
    )

    private fun source(id: Long): Source {
        val source = mockk<Source>()
        every { source.id } returns id
        every { source.name } returns "Fixture Source"
        every { source.lang } returns "en"
        return source
    }
}
