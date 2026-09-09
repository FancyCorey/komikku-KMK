package exh.recs.bridge.fixture

import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderRouteRole
import eu.kanade.tachiyomi.ui.reader.bridge.AlternateSourceReaderStateCodec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeEvidenceState
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import java.util.UUID

class AlternateSourceReaderFixtureRouteResolverTest {
    @Test
    fun `every non-recreated scenario launches the canonical primary routes`() = runTest {
        val scenarios = AlternateSourceReaderFixtureScenario.entries.filter {
            it != AlternateSourceReaderFixtureScenario.OFF &&
                it != AlternateSourceReaderFixtureScenario.PROCESS_RECREATED
        }

        scenarios.forEach { scenario ->
            val fixture = fixture(scenario)
            val result = fixture.resolver.resolve(scenario) as AlternateSourceReaderFixtureRouteResolution.Ready

            assertEquals(
                AlternateSourceReaderFixtureLaunch(
                    destination = AlternateSourceReaderFixtureDestination.MANGA,
                    mangaId = PRIMARY_MANGA_ID,
                ),
                result.manga,
            )
            assertEquals(
                AlternateSourceReaderFixtureLaunch(
                    destination = AlternateSourceReaderFixtureDestination.READER,
                    mangaId = PRIMARY_MANGA_ID,
                    chapterId = PRIMARY_CHAPTER_ID,
                ),
                result.reader,
            )
        }
    }

    @Test
    fun `process recreated scenario launches alternate route with a complete primitive session`() = runTest {
        val fixture = fixture(AlternateSourceReaderFixtureScenario.PROCESS_RECREATED, includeBridge = true)

        val result = fixture.resolver.resolve(AlternateSourceReaderFixtureScenario.PROCESS_RECREATED)
            as AlternateSourceReaderFixtureRouteResolution.Ready
        val decoded = AlternateSourceReaderStateCodec.read(result.reader.readerState::get)
            as AlternateSourceReaderStateCodec.DecodeResult.Valid

        assertEquals(ALTERNATE_MANGA_ID, result.reader.mangaId)
        assertEquals(ALTERNATE_CHAPTER_ID, result.reader.chapterId)
        assertEquals(AlternateSourceReaderRouteRole.ALTERNATE, decoded.session.currentRoute.role)
        assertEquals(ALTERNATE_CHAPTER_ID, decoded.session.currentRoute.chapterId)
        assertEquals(AlternateSourceReaderStateCodec.ALL_KEYS.toSet(), result.reader.readerState.keys)
        assertTrue(result.reader.readerState.values.all { it == null || it is String || it is Int || it is Long || it is Boolean })
    }

    @Test
    fun `resolver fails closed for scenario mismatch incomplete state hash drift and missing chapter`() = runTest {
        val valid = fixture(AlternateSourceReaderFixtureScenario.EXACT_GAP)
        assertEquals(
            AlternateSourceReaderFixtureRouteResolution.NotReady,
            valid.resolver.resolve(AlternateSourceReaderFixtureScenario.STALE),
        )

        val incomplete = fixture(
            AlternateSourceReaderFixtureScenario.EXACT_GAP,
            manifestTransform = { it.copy(completedSteps = it.completedSteps - AlternateSourceReaderFixtureStep.ROUTE) },
        )
        assertEquals(
            AlternateSourceReaderFixtureRouteResolution.NotReady,
            incomplete.resolver.resolve(AlternateSourceReaderFixtureScenario.EXACT_GAP),
        )

        val drift = fixture(
            AlternateSourceReaderFixtureScenario.EXACT_GAP,
            observedTransform = { it.copy(overlayRows = it.overlayRows + "foreign") },
        )
        assertEquals(
            AlternateSourceReaderFixtureRouteResolution.NotReady,
            drift.resolver.resolve(AlternateSourceReaderFixtureScenario.EXACT_GAP),
        )

        val missingChapter = fixture(AlternateSourceReaderFixtureScenario.EXACT_GAP, includePrimaryChapter = false)
        assertEquals(
            AlternateSourceReaderFixtureRouteResolution.NotReady,
            missingChapter.resolver.resolve(AlternateSourceReaderFixtureScenario.EXACT_GAP),
        )
    }

    @Test
    fun `resolver propagates inspection cancellation`() {
        val fixture = fixture(
            AlternateSourceReaderFixtureScenario.EXACT_GAP,
            inspectionFailure = CancellationException("inspection-cancelled"),
        )

        assertThrows(CancellationException::class.java) {
            runTest { fixture.resolver.resolve(AlternateSourceReaderFixtureScenario.EXACT_GAP) }
        }
    }

    private fun fixture(
        scenario: AlternateSourceReaderFixtureScenario,
        includeBridge: Boolean = false,
        includePrimaryChapter: Boolean = true,
        manifestTransform: (AlternateSourceReaderFixtureManifest) -> AlternateSourceReaderFixtureManifest = { it },
        observedTransform: (AlternateSourceReaderFixtureObservedState) -> AlternateSourceReaderFixtureObservedState = { it },
        inspectionFailure: Throwable? = null,
    ): ResolverFixture {
        val observed = AlternateSourceReaderFixtureObservedState(
            overlayRows = listOf("owned"),
            bridgeHash = HASH_A,
            identityHash = HASH_B,
            actionHistoryIds = emptySet(),
            routeReady = true,
        )
        val manifest = manifestTransform(
            AlternateSourceReaderFixtureManifest(
                operationId = UUID.randomUUID().toString(),
                pairedFixtureOperationId = UUID.randomUUID().toString(),
                scenario = scenario,
                createdAt = System.currentTimeMillis(),
                completedSteps = AlternateSourceReaderFixtureStep.entries.toSet(),
                primaryMangaId = PRIMARY_MANGA_ID,
                alternateMangaId = ALTERNATE_MANGA_ID,
                primaryGapChapter = gapSnapshot(),
                // The prepared overlay intentionally differs from its pre-seed baseline.
                bridgeBaselineHash = BASELINE_HASH_A,
                identityBaselineHash = BASELINE_HASH_B,
                seededOverlayHash = observed.sha256(),
            ),
        )
        val recovery = mockk<AlternateSourceReaderFixtureRecoveryStore>()
        every { recovery.load(any()) } returns AlternateSourceReaderFixtureManifestLoad.Present(manifest)
        val dataStore = mockk<AlternateSourceReaderFixtureDataStore>()
        if (inspectionFailure != null) {
            coEvery { dataStore.inspect(manifest) } throws inspectionFailure
        } else {
            coEvery { dataStore.inspect(manifest) } returns observedTransform(observed)
        }
        val mangaRepository = mockk<MangaRepository>()
        coEvery {
            mangaRepository.getMangaByUrlAndSourceId(AlternateSourceReaderFixturePaths.PRIMARY_MANGA, manifest.primarySourceId)
        } returns manga(PRIMARY_MANGA_ID, manifest.primarySourceId, AlternateSourceReaderFixturePaths.PRIMARY_MANGA)
        coEvery {
            mangaRepository.getMangaByUrlAndSourceId(AlternateSourceReaderFixturePaths.ALTERNATE_MANGA, manifest.alternateSourceId)
        } returns manga(ALTERNATE_MANGA_ID, manifest.alternateSourceId, AlternateSourceReaderFixturePaths.ALTERNATE_MANGA)
        val chapterRepository = mockk<ChapterRepository>()
        coEvery {
            chapterRepository.getChapterByUrlAndMangaId(AlternateSourceReaderFixturePaths.PRIMARY_CHAPTER_1, PRIMARY_MANGA_ID)
        } returns if (includePrimaryChapter) chapter(PRIMARY_CHAPTER_ID, PRIMARY_MANGA_ID, AlternateSourceReaderFixturePaths.PRIMARY_CHAPTER_1) else null
        coEvery {
            chapterRepository.getChapterByUrlAndMangaId(AlternateSourceReaderFixturePaths.ALTERNATE_CHAPTER_2, ALTERNATE_MANGA_ID)
        } returns chapter(ALTERNATE_CHAPTER_ID, ALTERNATE_MANGA_ID, AlternateSourceReaderFixturePaths.ALTERNATE_CHAPTER_2)
        val getBridge = mockk<GetAlternateSourceBridge>()
        coEvery { getBridge.await(any()) } returns if (includeBridge) bridgeState(manifest) else null
        return ResolverFixture(
            AlternateSourceReaderFixtureRouteResolver(
                recoveryStore = recovery,
                dataStore = dataStore,
                mangaRepository = mangaRepository,
                chapterRepository = chapterRepository,
                getBridge = getBridge,
            ),
        )
    }

    private fun bridgeState(manifest: AlternateSourceReaderFixtureManifest): AlternateSourceBridgeState {
        val key = AlternateSourceBridgeKey(
            primary = CrossSourceRecordKey(manifest.primarySourceId, manifest.primaryMangaUrl),
            alternate = CrossSourceRecordKey(manifest.alternateSourceId, manifest.alternateMangaUrl),
        )
        val now = 2_000L
        return AlternateSourceBridgeState(
            bridge = AlternateSourceBridge(
                key = key,
                version = 1,
                offsetMilli = 0L,
                offsetState = AlternateSourceBridgeEvidenceState.CONFIRMED,
                createdAt = now,
                updatedAt = now,
            ),
            mappings = listOf(
                AlternateSourceBridgeMapping(
                    key = AlternateSourceBridgeMappingKey(key, UUID.randomUUID().toString()),
                    primaryChapterUrl = AlternateSourceReaderFixturePaths.PRIMARY_CHAPTER_2,
                    alternateChapterUrl = AlternateSourceReaderFixturePaths.ALTERNATE_CHAPTER_2,
                    relation = AlternateSourceBridgeMappingRelation.EXACT,
                    state = AlternateSourceBridgeMappingState.CONFIRMED,
                    version = 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    private fun manga(id: Long, source: Long, url: String) = Manga.create().copy(id = id, source = source, url = url)

    private fun chapter(id: Long, mangaId: Long, url: String) = Chapter.create().copy(
        id = id,
        mangaId = mangaId,
        url = url,
        name = "Fixture",
        chapterNumber = 1.0,
    )

    private fun gapSnapshot() = AlternateSourceReaderFixtureChapterSnapshot(
        id = 202L,
        mangaId = PRIMARY_MANGA_ID,
        url = AlternateSourceReaderFixturePaths.PRIMARY_CHAPTER_2,
        name = "Chapter 2",
        chapterNumber = 2f,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 1L,
        dateUpload = 1L,
        sourceOrder = 1L,
    )

    private data class ResolverFixture(val resolver: AlternateSourceReaderFixtureRouteResolver)

    private companion object {
        const val PRIMARY_MANGA_ID = 101L
        const val ALTERNATE_MANGA_ID = 102L
        const val PRIMARY_CHAPTER_ID = 201L
        const val ALTERNATE_CHAPTER_ID = 302L
        val HASH_A = "A".repeat(64)
        val HASH_B = "B".repeat(64)
        val BASELINE_HASH_A = "C".repeat(64)
        val BASELINE_HASH_B = "D".repeat(64)
    }
}
