package exh.recs.bridge.fixture

import exh.util.FakeTasteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceAlternateSourceBridge
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecision
import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository
import java.util.UUID

class RepositoryAlternateSourceReaderFixtureDataStoreTest {

    @Test
    fun `every active scenario composes through repositories and reaches its typed route outcome`() = runTest {
        AlternateSourceReaderFixtureScenario.entries.filterNot { it == AlternateSourceReaderFixtureScenario.OFF }.forEach { scenario ->
            val harness = Harness()
            val manifest = harness.manifest(scenario)

            harness.store.applyStep(manifest, AlternateSourceReaderFixtureStep.PRIMARY_GAP)
            harness.store.applyStep(manifest, AlternateSourceReaderFixtureStep.IDENTITY)
            harness.store.applyStep(manifest, AlternateSourceReaderFixtureStep.BRIDGE)
            harness.store.applyStep(manifest, AlternateSourceReaderFixtureStep.SESSION)
            harness.store.applyStep(manifest, AlternateSourceReaderFixtureStep.ROUTE)

            val observed = harness.store.inspect(manifest)
            assertTrue(observed.routeReady, scenario.name)
            assertFalse(observed.overlayAbsent, scenario.name)
            assertEquals(manifest.actionHistoryBaselineIds, observed.actionHistoryIds)
            assertTrue(harness.store.cleanupOverlay(manifest), scenario.name)
            val cleaned = harness.store.inspect(manifest)
            assertTrue(cleaned.overlayAbsent, scenario.name)
            assertTrue(cleaned.baselinesMatch(manifest), scenario.name)
        }
    }

    @Test
    fun `baseline captures the exact owned chapter and refuses preexisting identity state`() = runTest {
        val harness = Harness()
        val graph = harness.graph()
        val baseline = harness.store.captureBaseline(graph)
        assertEquals(ORIGIN_CHAPTER_2_ID, baseline.primaryGapChapter.id)
        assertEquals(ORIGIN_ID, baseline.primaryGapChapter.mangaId)

        val pair = CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(PRIMARY_SOURCE_ID, PRIMARY_MANGA_URL),
            CrossSourceRecordKey(ALTERNATE_SOURCE_ID, ALTERNATE_MANGA_URL),
        )
        harness.tasteRepository.upsertCrossSourceIdentityDecisions(
            listOf(
                CrossSourceIdentityDecisionPolicy.userDecision(
                    pair,
                    CrossSourceIdentityDecisionValue.USER_REJECTED,
                    previous = null,
                    timestamp = NOW,
                ),
            ),
        )
        assertIllegalState { harness.store.captureBaseline(graph) }
    }

    @Test
    fun `primary gap step verifies deletion instead of trusting the repository return boundary`() = runTest {
        val harness = Harness(removeChapter = false)
        val manifest = harness.manifest(AlternateSourceReaderFixtureScenario.EXACT_GAP)

        assertIllegalState {
            harness.store.applyStep(manifest, AlternateSourceReaderFixtureStep.PRIMARY_GAP)
        }
        coVerify(exactly = 1) { harness.chapterRepository.removeChaptersWithIds(listOf(ORIGIN_CHAPTER_2_ID)) }
    }

    @Test
    fun `cleanup refuses concurrent bridge mutation before deleting either repository row`() = runTest {
        val harness = Harness()
        val manifest = harness.seed(AlternateSourceReaderFixtureScenario.EXACT_GAP)
        val current = requireNotNull(harness.bridgeRepository.state)
        harness.bridgeRepository.state = current.copy(bridge = current.bridge.copy(updatedAt = current.bridge.updatedAt + 10L))

        assertFalse(harness.store.cleanupOverlay(manifest))
        assertTrue(harness.bridgeRepository.state != null)
        assertTrue(harness.tasteRepository.getCrossSourceIdentityDecision(identityPair()) != null)
    }

    @Test
    fun `cleanup rejects action history drift and retains the recovery-relevant baseline mismatch`() = runTest {
        val harness = Harness()
        val manifest = harness.seed(AlternateSourceReaderFixtureScenario.OFFSET_PROVISIONAL)
        harness.actionIds += UUID.randomUUID().toString()

        assertFalse(harness.store.cleanupOverlay(manifest))
        val observed = harness.store.inspect(manifest)
        assertTrue(observed.overlayAbsent)
        assertFalse(observed.baselinesMatch(manifest))
    }

    @Test
    fun `paired fixture preparation and cleanup are delegated without broad repository mutation`() = runTest {
        val harness = Harness()
        assertEquals(
            AlternateSourceReaderPairedGraphPreparation.Ready(harness.graph()),
            harness.store.preparePairedGraph(),
        )
        assertTrue(harness.store.cleanupPairedGraph(OPERATION_ID))
        assertTrue(harness.store.isPairedGraphAbsent(OPERATION_ID))
        assertEquals(listOf("prepare", "cleanup:$OPERATION_ID", "absent:$OPERATION_ID"), harness.paired.calls)
    }

    private class Harness(
        removeChapter: Boolean = true,
    ) {
        val mangaRepository = mockk<MangaRepository>()
        val chapterRepository = mockk<ChapterRepository>()
        val tasteRepository = FakeTasteRepository()
        val bridgeRepository = MutableBridgeRepository()
        val paired = FakePairedFixtureGateway()
        val actionIds = mutableSetOf(HISTORY_ID)
        private val mangas = listOf(
            manga(ORIGIN_ID, PRIMARY_SOURCE_ID, PRIMARY_MANGA_URL),
            manga(ALTERNATE_ID, ALTERNATE_SOURCE_ID, ALTERNATE_MANGA_URL),
        )
        private val chapters = mutableMapOf<Long, Chapter>().apply {
            listOf(
                chapter(101L, ORIGIN_ID, "/kmk-fixture/f2/origin/chapter-1", 1.0),
                chapter(ORIGIN_CHAPTER_2_ID, ORIGIN_ID, PRIMARY_GAP_CHAPTER_URL, 2.0, read = true),
                chapter(103L, ORIGIN_ID, "/kmk-fixture/f2/origin/chapter-3", 3.0),
                chapter(201L, ALTERNATE_ID, "/kmk-fixture/f2/target/chapter-1", 1.0),
                chapter(202L, ALTERNATE_ID, "/kmk-fixture/f2/target/chapter-2", 2.0),
                chapter(203L, ALTERNATE_ID, "/kmk-fixture/f2/target/chapter-3", 3.0),
                chapter(204L, ALTERNATE_ID, "/kmk-fixture/f2/target/chapter-4", 4.0),
            ).forEach { put(it.id, it) }
        }
        val store: RepositoryAlternateSourceReaderFixtureDataStore

        init {
            coEvery { mangaRepository.getMangaByUrlAndSourceId(any(), any()) } answers {
                val url = firstArg<String>()
                val source = secondArg<Long>()
                mangas.firstOrNull { it.url == url && it.source == source }
            }
            coEvery { chapterRepository.getChapterById(any()) } answers { chapters[firstArg()] }
            coEvery { chapterRepository.getChapterByUrlAndMangaId(any(), any()) } answers {
                val url = firstArg<String>()
                val mangaId = secondArg<Long>()
                chapters.values.firstOrNull { it.url == url && it.mangaId == mangaId }
            }
            coEvery { chapterRepository.removeChaptersWithIds(any()) } answers {
                if (removeChapter) firstArg<List<Long>>().forEach(chapters::remove)
            }
            store = RepositoryAlternateSourceReaderFixtureDataStore(
                pairedFixture = paired,
                mangaRepository = mangaRepository,
                chapterRepository = chapterRepository,
                getIdentity = GetCrossSourceIdentityDecisions(tasteRepository),
                replaceIdentity = ReplaceCrossSourceIdentityDecision(tasteRepository),
                getBridge = GetAlternateSourceBridge(bridgeRepository),
                replaceBridge = ReplaceAlternateSourceBridge(bridgeRepository),
                bridgeJournalIds = { actionIds.toSet() },
                identityJournalIds = { emptySet() },
                now = { NOW },
            )
        }

        fun graph() = AlternateSourceReaderPairedGraph(OPERATION_ID, ORIGIN_ID, ALTERNATE_ID)

        suspend fun manifest(scenario: AlternateSourceReaderFixtureScenario): AlternateSourceReaderFixtureManifest {
            val baseline = store.captureBaseline(graph())
            return AlternateSourceReaderFixtureManifest(
                operationId = MANIFEST_ID,
                pairedFixtureOperationId = OPERATION_ID,
                scenario = scenario,
                createdAt = NOW,
                completedSteps = setOf(
                    AlternateSourceReaderFixtureStep.PAIRED_GRAPH,
                    AlternateSourceReaderFixtureStep.BASELINES,
                ),
                primaryMangaId = ORIGIN_ID,
                alternateMangaId = ALTERNATE_ID,
                primaryGapChapter = baseline.primaryGapChapter,
                bridgeBaselineHash = baseline.bridgeHash,
                identityBaselineHash = baseline.identityHash,
                actionHistoryBaselineIds = baseline.actionHistoryIds,
            )
        }

        suspend fun seed(scenario: AlternateSourceReaderFixtureScenario): AlternateSourceReaderFixtureManifest {
            val manifest = manifest(scenario)
            store.applyStep(manifest, AlternateSourceReaderFixtureStep.PRIMARY_GAP)
            store.applyStep(manifest, AlternateSourceReaderFixtureStep.IDENTITY)
            store.applyStep(manifest, AlternateSourceReaderFixtureStep.BRIDGE)
            return manifest
        }
    }

    private class FakePairedFixtureGateway : AlternateSourceReaderPairedFixtureGateway {
        val calls = mutableListOf<String>()
        override suspend fun prepare(): AlternateSourceReaderPairedGraphPreparation {
            calls += "prepare"
            return AlternateSourceReaderPairedGraphPreparation.Ready(
                AlternateSourceReaderPairedGraph(OPERATION_ID, ORIGIN_ID, ALTERNATE_ID),
            )
        }

        override suspend fun cleanup(operationId: String): Boolean {
            calls += "cleanup:$operationId"
            return true
        }

        override suspend fun cleanupUnrecorded(): Boolean {
            calls += "cleanup-unrecorded"
            return true
        }

        override suspend fun isAbsent(operationId: String): Boolean {
            calls += "absent:$operationId"
            return true
        }
    }

    private class MutableBridgeRepository : AlternateSourceBridgeRepository {
        var state: AlternateSourceBridgeState? = null

        override suspend fun get(key: AlternateSourceBridgeKey): AlternateSourceBridgeState? =
            state?.takeIf { it.bridge.key == key }

        override suspend fun getAllBridges(): List<AlternateSourceBridge> = listOfNotNull(state?.bridge)

        override suspend fun getAllMappings(): List<AlternateSourceBridgeMapping> = state?.mappings.orEmpty()

        override suspend fun upsert(state: AlternateSourceBridgeState) {
            this.state = state
        }

        override suspend fun replace(replacement: AlternateSourceBridgeStateReplacement): Boolean {
            val current = state
            if (current?.bridge != replacement.expectedBridge) return false
            if (replacement.mappingReplacements.any { change ->
                    current?.mappings.orEmpty().firstOrNull { it.key == (change.expected ?: change.replacement)?.key } != change.expected
                }
            ) {
                return false
            }
            val replacementBridge = replacement.replacementBridge
            if (replacementBridge == null) {
                state = null
                return true
            }
            val mappings = current?.mappings.orEmpty().toMutableList()
            replacement.mappingReplacements.forEach { change ->
                val key = (change.expected ?: change.replacement)?.key ?: return false
                mappings.removeAll { it.key == key }
                change.replacement?.let(mappings::add)
            }
            state = AlternateSourceBridgeState(replacementBridge, mappings)
            return true
        }

        override suspend fun tombstoneAll(updatedAt: Long) = Unit
    }

    companion object {
        private const val NOW = 1_700_000_000_000L
        private const val PRIMARY_SOURCE_ID = 910000000000000001L
        private const val ALTERNATE_SOURCE_ID = 910000000000000002L
        private const val PRIMARY_MANGA_URL = "/kmk-fixture/f2/origin"
        private const val ALTERNATE_MANGA_URL = "/kmk-fixture/f2/target"
        private const val PRIMARY_GAP_CHAPTER_URL = "/kmk-fixture/f2/origin/chapter-2"
        private const val ORIGIN_ID = 11L
        private const val ALTERNATE_ID = 12L
        private const val ORIGIN_CHAPTER_2_ID = 102L
        private const val OPERATION_ID = "11111111-1111-4111-8111-111111111111"
        private const val MANIFEST_ID = "33333333-3333-4333-8333-333333333333"
        private const val HISTORY_ID = "44444444-4444-4444-8444-444444444444"

        private fun identityPair() = CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(PRIMARY_SOURCE_ID, PRIMARY_MANGA_URL),
            CrossSourceRecordKey(ALTERNATE_SOURCE_ID, ALTERNATE_MANGA_URL),
        )

        private fun manga(id: Long, source: Long, url: String) = Manga.create().copy(
            id = id,
            source = source,
            url = url,
            ogTitle = "Fixture",
            notes = "kmk-f2:$OPERATION_ID",
        )

        private fun chapter(id: Long, mangaId: Long, url: String, number: Double, read: Boolean = false) =
            Chapter.create().copy(
                id = id,
                mangaId = mangaId,
                url = url,
                name = "Chapter ${number.toInt()}",
                chapterNumber = number,
                read = read,
                bookmark = read,
                lastPageRead = if (read) 5L else 0L,
                dateFetch = NOW,
                dateUpload = NOW,
                sourceOrder = number.toLong(),
            )
    }

    private suspend fun assertIllegalState(block: suspend () -> Unit) {
        val failure = try {
            block()
            null
        } catch (e: IllegalStateException) {
            e
        }
        assertTrue(failure != null)
    }
}
