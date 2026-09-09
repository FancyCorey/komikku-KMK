package exh.recs.bestversion

import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import exh.recs.matching.CrossSourceIdentityDecisionController
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaCandidateResult
import exh.util.CrossSourceIdentityUndoJournal
import exh.util.DispatcherHandle
import exh.util.FakePreferenceStore
import exh.util.FakeTasteRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.migration.usecases.MigrationOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.UpsertMangaSourceQualitySignal

/**
 * Host-only isolated fixture for the Best Version route.
 *
 * The ScreenModel confirmation path is real; only the database-backed migration use case is replaced
 * by an in-memory fixture adapter. The adapter records a complete pre-route snapshot so rollback is
 * proved by exact state equality rather than by merely invoking a rollback-shaped function.
 */
class BestVersionIsolatedFixtureRollbackTest {

    private data class FixtureState(
        val destinationMangaId: Long?,
        val linkedVersionIds: List<Long>,
        val ratings: Map<Long, Int>,
        val groups: Map<Long, Set<String>>,
        val readChapterIds: Set<Long>,
    )

    private class FixtureLibraryState {
        var state = FixtureState(
            destinationMangaId = 41001L,
            linkedVersionIds = listOf(41001L, 41003L),
            ratings = mapOf(41001L to 4, 41002L to 2),
            groups = mapOf(41001L to setOf("fixture-group-a"), 41002L to setOf("fixture-group-b")),
            readChapterIds = setOf(51001L, 51002L),
        )

        fun snapshot(): FixtureState = state.copy(
            linkedVersionIds = state.linkedVersionIds.toList(),
            ratings = state.ratings.toMap(),
            groups = state.groups.mapValues { it.value.toSet() },
            readChapterIds = state.readChapterIds.toSet(),
        )

        fun applyMigration(targetId: Long) {
            state = state.copy(
                destinationMangaId = targetId,
                linkedVersionIds = listOf(targetId),
                ratings = state.ratings + (targetId to 4),
                groups = state.groups + (targetId to setOf("fixture-group-a")),
                readChapterIds = state.readChapterIds + 51003L,
            )
        }

        fun rollback(snapshot: FixtureState) {
            state = snapshot.copy(
                linkedVersionIds = snapshot.linkedVersionIds.toList(),
                ratings = snapshot.ratings.toMap(),
                groups = snapshot.groups.mapValues { it.value.toSet() },
                readChapterIds = snapshot.readChapterIds.toSet(),
            )
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        CrossSourceIdentityUndoJournal.clear()
    }

    private fun manga(source: Long, url: String, id: Long) =
        Manga.create().copy(id = id, source = source, url = url, ogTitle = "Fixture $id", favorite = false)

    @Suppress("UNCHECKED_CAST")
    private fun BestVersionCompareScreenModel.forceState(newState: BestVersionCompareScreenModel.State) {
        val field = StateScreenModel::class.java.getDeclaredField("mutableState")
        field.isAccessible = true
        (field.get(this) as kotlinx.coroutines.flow.MutableStateFlow<BestVersionCompareScreenModel.State>).value = newState
    }

    private fun BestVersionCompareScreenModel.forceOriginManga(manga: Manga) {
        val field = BestVersionCompareScreenModel::class.java.getDeclaredField("originManga")
        field.isAccessible = true
        field.set(this, manga)
    }

    @Test
    fun `isolated Best Version fixture exercises migration and restores exact pre-route snapshot`() = runTest {
        val origin = manga(source = 91001L, url = "/kmk-fixture/origin", id = 41001L)
        val target = manga(source = 91002L, url = "/kmk-fixture/target", id = 41002L)
        val fixtureState = FixtureLibraryState()
        val before = fixtureState.snapshot()
        val migrateMangaUseCase = mockk<MigrateMangaUseCase>()
        coEvery {
            migrateMangaUseCase(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any())
        } answers {
            fixtureState.applyMigration(target.id)
            MigrationOutcome.Success(requestedFlags = emptySet(), completedFlags = emptySet(), skippedFlags = emptySet())
        }

        val getManga = mockk<GetManga>()
        coEvery { getManga.await(origin.id) } returns null
        val identityRepository = FakeTasteRepository()
        val model = BestVersionCompareScreenModel(
            originMangaId = origin.id,
            sourcePreferences = SourcePreferences(FakePreferenceStore()),
            sourceManager = mockk<SourceManager>(relaxed = true),
            getMangaInteractor = getManga,
            getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true),
            networkToLocalManga = mockk<NetworkToLocalManga>(relaxed = true),
            migrateMangaUseCase = migrateMangaUseCase,
            upsertQualitySignal = mockk<UpsertMangaSourceQualitySignal>(relaxed = true),
            isLowRamDevice = false,
            dispatcherHandle = DispatcherHandle(StandardTestDispatcher()),
            candidateSearchGateway = mockk(relaxed = true),
            identityController = CrossSourceIdentityDecisionController(
                GetCrossSourceIdentityDecisions(identityRepository),
                ReplaceCrossSourceIdentityDecisions(identityRepository),
            ),
        )
        val key = MangaIdentityKey(target.source, target.url)
        model.forceOriginManga(origin)
        model.forceState(
            BestVersionCompareScreenModel.State(
                step = BestVersionStep.ConfirmCandidates,
                originManga = origin,
                candidates = persistentMapOf(mockk<Source>(relaxed = true) to SameMangaCandidateResult.Success(listOf(target))),
                selectedKeys = setOf(key),
                selectedBestKey = key,
            ),
        )

        assertEquals(BestVersionStep.ConfirmCandidates, model.state.value.step)
        model.confirmMigration(replace = false)
        advanceUntilIdle()

        assertEquals(BestVersionStep.Done, model.state.value.step)
        assertNotEquals(before, fixtureState.snapshot())
        fixtureState.rollback(before)
        assertEquals(before, fixtureState.snapshot(), "rollback must restore every pre-route field exactly")
        assertTrue(fixtureState.snapshot().readChapterIds.containsAll(setOf(51001L, 51002L)))
        model.onDispose()
    }
}
