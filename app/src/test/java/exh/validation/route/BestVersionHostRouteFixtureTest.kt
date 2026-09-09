package exh.validation.route

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.recs.bestversion.BestVersionCandidateSearchGateway
import exh.recs.bestversion.BestVersionCompareScreenModel
import exh.recs.bestversion.BestVersionStep
import exh.recs.bestversion.CandidateChapterState
import exh.recs.bestversion.CandidatePreviewState
import exh.recs.bestversion.fixture.BestVersionPairedFixtureRouteBinding
import exh.recs.bestversion.fixture.BestVersionPairedFixtureRouteResolverContract
import exh.recs.matching.CrossSourceIdentityDecisionController
import exh.recs.matching.MangaIdentityKey
import exh.recs.matching.SameMangaCandidateResult
import exh.recs.matching.SameMangaMatchSettings
import exh.recs.matching.SameMangaSourceResult
import exh.util.CrossSourceIdentityUndoJournal
import exh.util.DispatcherHandle
import exh.util.FakePreferenceStore
import exh.util.FakeTasteRepository
import exh.util.MigrationReceiptJournal
import exh.util.NonUndoableEventJournal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.JsonObject
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.domain.migration.usecases.MigrationOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.UpsertMangaSourceQualitySignal

class BestVersionHostRouteFixtureTest {
    @Test
    fun `missing origin reaches the typed terminal error without searching`() {
        HostRouteTestEnvironment().use { environment ->
            val gateway = ScriptedBestVersionSearchGateway(emptyList(), emptyMap())
            BestVersionHostRouteFixture(environment, origin = null, gateway = gateway).use { route ->
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.Error(exh.recs.bestversion.BestVersionErrorReason.OriginMissing), route.state.step)
                assertEquals(0, gateway.searchCount)
            }
        }
    }

    @Test
    fun `empty candidate result settles on candidate confirmation`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(901L, "Empty")
            val gateway = ScriptedBestVersionSearchGateway(
                sources = listOf(source),
                results = mapOf(source to SameMangaCandidateResult.Success(emptyList())),
            )
            BestVersionHostRouteFixture(environment, origin(), gateway).use { route ->
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.ConfirmCandidates, route.state.step)
                assertTrue((route.state.candidates.getValue(source) as SameMangaCandidateResult.Success).results.isEmpty())
                assertTrue(route.state.selectedKeys.isEmpty())
            }
        }
    }

    @Test
    fun `successful candidate is retained beside a failed source and toggles publicly`() {
        HostRouteTestEnvironment().use { environment ->
            val working = source(902L, "Working")
            val failed = source(903L, "Failed")
            val target = manga(902L, "/target", 42L)
            val key = MangaIdentityKey(target.source, target.url)
            val gateway = ScriptedBestVersionSearchGateway(
                sources = listOf(working, failed),
                results = mapOf(
                    working to SameMangaCandidateResult.Success(listOf(target)),
                    failed to SameMangaCandidateResult.Error(IllegalStateException("synthetic-source-failure")),
                ),
            )
            BestVersionHostRouteFixture(environment, origin(), gateway).use { route ->
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.ConfirmCandidates, route.state.step)
                assertTrue(route.state.candidates.getValue(working) is SameMangaCandidateResult.Success)
                assertTrue(route.state.candidates.getValue(failed) is SameMangaCandidateResult.Error)
                assertTrue(key in route.state.selectedKeys)

                route.model.toggleSelection(key)
                assertFalse(key in route.state.selectedKeys)
                route.model.toggleSelection(key)
                assertTrue(key in route.state.selectedKeys)
            }
        }
    }

    @Test
    fun `chapter route distinguishes available unavailable and source error`() {
        HostRouteTestEnvironment().use { environment ->
            val availableSource = source(904L, "Available")
            val unavailableSource = source(905L, "Unavailable")
            val errorSource = source(906L, "Error")
            val available = manga(904L, "/available", 44L)
            val unavailable = manga(905L, "/unavailable", 45L)
            val error = manga(906L, "/error", 46L)
            coEvery { availableSource.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(firstArg(), listOf(sourceChapter(5f)))
            }
            coEvery { unavailableSource.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(firstArg(), emptyList())
            }
            coEvery { errorSource.getMangaUpdate(any(), any(), false, true) } throws
                IllegalStateException("synthetic-chapter-failure")
            val sourceManager = mockk<SourceManager>(relaxed = true).also { manager ->
                every { manager.get(904L) } returns availableSource
                every { manager.get(905L) } returns unavailableSource
                every { manager.get(906L) } returns errorSource
            }
            val chapters = mockk<GetChaptersByMangaId>().also { interactor ->
                coEvery { interactor.await(9_000L) } returns listOf(domainChapter(5.0, 9_000L))
            }
            val gateway = ScriptedBestVersionSearchGateway(
                sources = listOf(availableSource, unavailableSource, errorSource),
                results = mapOf(
                    availableSource to SameMangaCandidateResult.Success(listOf(available)),
                    unavailableSource to SameMangaCandidateResult.Success(listOf(unavailable)),
                    errorSource to SameMangaCandidateResult.Success(listOf(error)),
                ),
            )
            BestVersionHostRouteFixture(
                environment = environment,
                origin = origin(),
                gateway = gateway,
                sourceManager = sourceManager,
                getChapters = chapters,
            ).use { route ->
                route.advanceUntilIdle()
                route.model.confirmCandidates()
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.SelectChapter, route.state.step)
                assertTrue(route.state.candidateChapters.getValue(MangaIdentityKey(900L, "/origin")) is CandidateChapterState.Available)
                assertTrue(route.state.candidateChapters.getValue(MangaIdentityKey(904L, "/available")) is CandidateChapterState.Available)
                assertEquals(CandidateChapterState.Unavailable, route.state.candidateChapters.getValue(MangaIdentityKey(905L, "/unavailable")))
                assertTrue(route.state.candidateChapters.getValue(MangaIdentityKey(906L, "/error")) is CandidateChapterState.ChapterError)
            }
        }
    }

    @Test
    fun `chapter route normalizes a source sentinel from its chapter label`() {
        HostRouteTestEnvironment().use { environment ->
            val candidateSource = httpSource(915L, "Sentinel source")
            val origin = origin().copy(ogTitle = "Resurrection Boy")
            val candidate = manga(915L, "/sentinel", 55L).copy(ogTitle = "Resurrection Boy")
            coEvery { candidateSource.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(
                    firstArg(),
                    listOf(
                        SChapter.create().apply {
                            url = "/sentinel/chapter-12"
                            name = "Ch.012"
                            chapter_number = -1f
                        },
                    ),
                )
            }
            val sourceManager = mockk<SourceManager>(relaxed = true).also { manager ->
                every { manager.get(900L) } returns httpSource(900L, "Origin")
                every { manager.get(915L) } returns candidateSource
            }
            val chapters = mockk<GetChaptersByMangaId>().also { interactor ->
                coEvery { interactor.await(9_000L) } returns listOf(domainChapter(12.0, 9_000L))
            }
            val gateway = ScriptedBestVersionSearchGateway(
                sources = listOf(candidateSource),
                results = mapOf(candidateSource to SameMangaCandidateResult.Success(listOf(candidate))),
            )
            BestVersionHostRouteFixture(
                environment = environment,
                origin = origin,
                gateway = gateway,
                sourceManager = sourceManager,
                getChapters = chapters,
            ).use { route ->
                route.advanceUntilIdle()
                route.model.confirmCandidates()
                route.advanceUntilIdle()

                val key = MangaIdentityKey(915L, "/sentinel")
                val state = route.state.candidateChapters.getValue(key)
                assertTrue(state is CandidateChapterState.Available)
                assertEquals(12f, (state as CandidateChapterState.Available).chapter.chapter_number)
            }
        }
    }

    @Test
    fun `chapter retry fans out current errors and excludes successful and unavailable`() {
        HostRouteTestEnvironment().use { environment ->
            val originSource = httpSource(900L, "Origin")
            val loadedSource = httpSource(911L, "Loaded")
            val unavailableSource = httpSource(912L, "Unavailable")
            val retrySource = httpSource(913L, "Retry")
            val loaded = manga(911L, "/loaded", 51L)
            val unavailable = manga(912L, "/unavailable", 52L)
            val retry = manga(913L, "/retry", 53L)
            coEvery { loadedSource.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(firstArg(), listOf(sourceChapter(5f)))
            }
            coEvery { unavailableSource.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(firstArg(), emptyList())
            }
            var chapterFetches = 0
            coEvery { retrySource.getMangaUpdate(any(), any(), false, true) } answers {
                chapterFetches++
                if (chapterFetches == 1) throw RuntimeException("offline")
                SMangaUpdate(firstArg(), listOf(sourceChapter(5f)))
            }
            val sourceManager = mockk<SourceManager>(relaxed = true).also { manager ->
                every { manager.get(900L) } returns originSource
                every { manager.get(911L) } returns loadedSource
                every { manager.get(912L) } returns unavailableSource
                every { manager.get(913L) } returns retrySource
            }
            val chapters = mockk<GetChaptersByMangaId>().also { interactor ->
                coEvery { interactor.await(9_000L) } returns listOf(domainChapter(5.0, 9_000L))
            }
            val gateway = ScriptedBestVersionSearchGateway(
                sources = listOf(loadedSource, unavailableSource, retrySource),
                results = mapOf(
                    loadedSource to SameMangaCandidateResult.Success(listOf(loaded)),
                    unavailableSource to SameMangaCandidateResult.Success(listOf(unavailable)),
                    retrySource to SameMangaCandidateResult.Success(listOf(retry)),
                ),
            )
            BestVersionHostRouteFixture(
                environment = environment,
                origin = origin(),
                gateway = gateway,
                sourceManager = sourceManager,
                getChapters = chapters,
            ).use { route ->
                route.advanceUntilIdle()
                route.model.confirmCandidates()
                route.advanceUntilIdle()

                val loadedKey = MangaIdentityKey(911L, "/loaded")
                val unavailableKey = MangaIdentityKey(912L, "/unavailable")
                val retryKey = MangaIdentityKey(913L, "/retry")
                assertTrue(route.state.candidateChapters.getValue(loadedKey) is CandidateChapterState.Available)
                assertEquals(CandidateChapterState.Unavailable, route.state.candidateChapters.getValue(unavailableKey))
                assertTrue(route.state.candidateChapters.getValue(retryKey) is CandidateChapterState.ChapterError)

                route.model.retryFailedChapterLoads()
                route.advanceUntilIdle()

                assertTrue(route.state.candidateChapters.getValue(loadedKey) is CandidateChapterState.Available)
                assertEquals(CandidateChapterState.Unavailable, route.state.candidateChapters.getValue(unavailableKey))
                assertTrue(route.state.candidateChapters.getValue(retryKey) is CandidateChapterState.Available)
                assertEquals(2, chapterFetches)
            }
        }
    }

    @Test
    fun `preview route retries every failed candidate and excludes loaded and skipped`() {
        HostRouteTestEnvironment().use { environment ->
            val originSource = httpSource(900L, "Origin")
            val loadedSource = httpSource(907L, "Loaded")
            val skippedSource = httpSource(908L, "Skipped")
            val retrySource = httpSource(909L, "Retry")
            val retrySourceTwo = httpSource(910L, "Retry Two")
            val loaded = manga(907L, "/loaded", 47L)
            val skipped = manga(908L, "/skipped", 48L)
            val retry = manga(909L, "/retry", 49L)
            val retryTwo = manga(910L, "/retry-two", 50L)
            coEvery { loadedSource.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(firstArg(), listOf(sourceChapter(5f)))
            }
            coEvery { skippedSource.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(firstArg(), emptyList())
            }
            coEvery { retrySource.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(firstArg(), listOf(sourceChapter(5f)))
            }
            coEvery { retrySourceTwo.getMangaUpdate(any(), any(), false, true) } answers {
                SMangaUpdate(firstArg(), listOf(sourceChapter(5f)))
            }
            coEvery { originSource.getPageList(any()) } returns previewPages("origin")
            coEvery { loadedSource.getPageList(any()) } returns previewPages("loaded")
            var retryFetchCount = 0
            coEvery { retrySource.getPageList(any()) } answers {
                retryFetchCount++
                if (retryFetchCount == 1) emptyList() else previewPages("retry")
            }
            var retryTwoFetchCount = 0
            coEvery { retrySourceTwo.getPageList(any()) } answers {
                retryTwoFetchCount++
                if (retryTwoFetchCount == 1) emptyList() else previewPages("retry-two")
            }
            val sourceManager = mockk<SourceManager>(relaxed = true).also { manager ->
                every { manager.get(900L) } returns originSource
                every { manager.get(907L) } returns loadedSource
                every { manager.get(908L) } returns skippedSource
                every { manager.get(909L) } returns retrySource
                every { manager.get(910L) } returns retrySourceTwo
            }
            val chapters = mockk<GetChaptersByMangaId>().also { interactor ->
                coEvery { interactor.await(9_000L) } returns listOf(domainChapter(5.0, 9_000L))
            }
            val gateway = ScriptedBestVersionSearchGateway(
                sources = listOf(loadedSource, skippedSource, retrySource, retrySourceTwo),
                results = mapOf(
                    loadedSource to SameMangaCandidateResult.Success(listOf(loaded)),
                    skippedSource to SameMangaCandidateResult.Success(listOf(skipped)),
                    retrySource to SameMangaCandidateResult.Success(listOf(retry)),
                    retrySourceTwo to SameMangaCandidateResult.Success(listOf(retryTwo)),
                ),
            )
            BestVersionHostRouteFixture(
                environment = environment,
                origin = origin(),
                gateway = gateway,
                sourceManager = sourceManager,
                getChapters = chapters,
            ).use { route ->
                route.advanceUntilIdle()
                route.model.confirmCandidates()
                route.advanceUntilIdle()
                route.model.startPreview()
                route.advanceUntilIdle()

                val loadedKey = MangaIdentityKey(907L, "/loaded")
                val skippedKey = MangaIdentityKey(908L, "/skipped")
                val retryKey = MangaIdentityKey(909L, "/retry")
                val retryTwoKey = MangaIdentityKey(910L, "/retry-two")
                assertEquals(BestVersionStep.ComparePreview, route.state.step)
                assertTrue(route.state.candidatePreviews.getValue(loadedKey) is CandidatePreviewState.Loaded)
                assertEquals(CandidatePreviewState.Skipped, route.state.candidatePreviews.getValue(skippedKey))
                assertTrue(route.state.candidatePreviews.getValue(retryKey) is CandidatePreviewState.PreviewError)
                assertTrue(route.state.candidatePreviews.getValue(retryTwoKey) is CandidatePreviewState.PreviewError)

                route.model.retryCandidate(retryKey)
                route.advanceUntilIdle()

                assertTrue(route.state.candidatePreviews.getValue(retryKey) is CandidatePreviewState.Loaded)
                assertTrue(route.state.candidatePreviews.getValue(retryTwoKey) is CandidatePreviewState.Loaded)
                assertEquals(2, retryFetchCount)
                assertEquals(2, retryTwoFetchCount)
            }
        }
    }

    @Test
    fun `route disposal rejects a late candidate callback after cancellation`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(910L, "Late")
            val target = manga(910L, "/late", 50L)
            val started = CompletableDeferred<Unit>()
            val lateCallback = CompletableDeferred<Unit>()
            val gateway = object : BestVersionCandidateSearchGateway {
                override fun getMatchingSources() = listOf(source)

                override suspend fun search(
                    queries: List<String>,
                    settings: SameMangaMatchSettings,
                    originManga: Manga,
                    sources: List<Source>,
                    onResult: suspend (SameMangaSourceResult) -> Unit,
                ) {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (_: CancellationException) {
                        onResult(SameMangaSourceResult(source, SameMangaCandidateResult.Success(listOf(target))))
                        lateCallback.complete(Unit)
                    }
                }
            }
            val route = BestVersionHostRouteFixture(environment, origin(), gateway)
            environment.runCurrent()
            assertTrue(started.isCompleted)
            val stateAtLeave = route.state

            route.close()
            environment.advanceUntilIdle()

            assertTrue(lateCallback.isCompleted)
            assertEquals(stateAtLeave, route.state)
            assertTrue(route.state.candidates.getValue(source) is SameMangaCandidateResult.Loading)
        }
    }

    @Test
    fun `keeping the current version completes without migration or receipt`() {
        HostRouteTestEnvironment().use { environment ->
            val migrate = mockk<MigrateMangaUseCase>(relaxed = true)
            BestVersionHostRouteFixture(
                environment = environment,
                origin = origin(),
                gateway = ScriptedBestVersionSearchGateway(emptyList(), emptyMap()),
                migrateMangaUseCase = migrate,
            ).use { route ->
                route.advanceUntilIdle()

                route.model.selectBestVersion(route.state.originKey!!)

                assertEquals(BestVersionStep.Done, route.state.step)
                assertTrue(route.state.keptCurrentVersion)
                assertTrue(NonUndoableEventJournal.isEmpty())
                assertTrue(MigrationReceiptJournal.isEmpty())
            }
        }
    }

    @Test
    fun `successful migration completes and records one correlated receipt`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(911L, "Migration")
            val target = manga(911L, "/migration", 51L)
            val origin = origin()
            val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                coEvery {
                    useCase(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any())
                } returns MigrationOutcome.Success(emptySet(), emptySet(), emptySet())
            }
            val preferences = SourcePreferences(FakePreferenceStore()).apply {
                evaluationMode().set(true)
                sameMangaMatchPreselectionMode().set("all")
            }
            val gateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            BestVersionHostRouteFixture(environment, origin, gateway, migrateMangaUseCase = migrate, sourcePreferences = preferences).use { route ->
                route.advanceUntilIdle()
                val key = MangaIdentityKey(target.source, target.url)
                route.model.selectBestVersion(key)
                route.model.confirmMigration(replace = false)
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.Done, route.state.step)
                assertEquals(target.id, route.state.completedTargetMangaId)
                val event = NonUndoableEventJournal.snapshot().single()
                val receipt = MigrationReceiptJournal.snapshot().single()
                assertEquals(event.id, receipt.id)
            }
        }
    }

    @Test
    fun `partial migration failure stays terminal error and records nothing`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(912L, "Partial")
            val target = manga(912L, "/partial", 52L)
            val origin = origin()
            val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                coEvery {
                    useCase(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any())
                } returns MigrationOutcome.PartialFailure(
                    requestedFlags = emptySet(),
                    completedFlags = emptySet(),
                    skippedFlags = emptySet(),
                    failedAt = null,
                    finalUpdateFailed = true,
                    cause = IllegalStateException("synthetic-partial-failure"),
                )
            }
            val gateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            BestVersionHostRouteFixture(environment, origin, gateway, migrateMangaUseCase = migrate).use { route ->
                route.advanceUntilIdle()
                route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
                route.model.confirmMigration(replace = false)
                route.advanceUntilIdle()

                assertTrue(route.state.step is BestVersionStep.Error)
                assertFalse(route.state.migrationComplete)
                assertTrue(NonUndoableEventJournal.isEmpty())
                assertTrue(MigrationReceiptJournal.isEmpty())
            }
        }
    }

    @Test
    fun `migration cancellation remains cancellation and records nothing`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(913L, "Cancelled")
            val target = manga(913L, "/cancelled", 53L)
            val origin = origin()
            val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                coEvery {
                    useCase(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any())
                } throws CancellationException("synthetic-migration-cancelled")
            }
            val gateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            BestVersionHostRouteFixture(environment, origin, gateway, migrateMangaUseCase = migrate).use { route ->
                route.advanceUntilIdle()
                route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
                route.model.confirmMigration(replace = false)
                route.advanceUntilIdle()

                assertTrue(route.model.migrationJob?.isCancelled == true)
                assertFalse(route.state.step is BestVersionStep.Error)
                assertFalse(route.state.migrationComplete)
                assertTrue(NonUndoableEventJournal.isEmpty())
                assertTrue(MigrationReceiptJournal.isEmpty())
            }
        }
    }

    @Test
    fun `an invalid explicit fixture token fails closed without ordinary search`() {
        HostRouteTestEnvironment().use { environment ->
            val gateway = ScriptedBestVersionSearchGateway(emptyList(), emptyMap())
            BestVersionHostRouteFixture(
                environment = environment,
                origin = origin(),
                gateway = gateway,
                fixtureOperationId = "invalid-token",
                fixtureRouteResolver = BestVersionPairedFixtureRouteResolverContract { _, _ -> null },
            ).use { route ->
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.Error(exh.recs.bestversion.BestVersionErrorReason.SourceUnavailable), route.state.step)
                assertEquals(0, gateway.searchCount)
            }
        }
    }

    @Test
    fun `explicit fixture route uses the canonical model and exact bounded migration flags`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(914L, "Fixture")
            val target = manga(914L, "/fixture-target", 54L)
            val origin = origin()
            val fixtureGateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            val flags = setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY)
            val resolver = BestVersionPairedFixtureRouteResolverContract { operationId, resolvedOrigin ->
                assertEquals("operation-token", operationId)
                assertEquals(origin, resolvedOrigin)
                BestVersionPairedFixtureRouteBinding(fixtureGateway, flags) { true }
            }
            val ordinaryGateway = ScriptedBestVersionSearchGateway(emptyList(), emptyMap())
            val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                coEvery {
                    useCase(
                        current = origin,
                        target = target,
                        replace = false,
                        presetFlags = flags,
                        throttleFunc = any(),
                    )
                } returns MigrationOutcome.Success(flags, flags, emptySet())
            }
            BestVersionHostRouteFixture(
                environment = environment,
                origin = origin,
                gateway = ordinaryGateway,
                migrateMangaUseCase = migrate,
                fixtureOperationId = "operation-token",
                fixtureRouteResolver = resolver,
            ).use { route ->
                route.advanceUntilIdle()
                assertEquals(BestVersionStep.ConfirmCandidates, route.state.step)
                assertEquals(0, ordinaryGateway.searchCount)
                assertEquals(1, fixtureGateway.searchCount)

                route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
                route.model.confirmMigration(replace = false)
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.Done, route.state.step)
                coVerify(exactly = 1) {
                    migrate(
                        current = origin,
                        target = target,
                        replace = false,
                        presetFlags = flags,
                        throttleFunc = any(),
                    )
                }
                assertEquals(1, NonUndoableEventJournal.snapshot().size)
                assertEquals(1, MigrationReceiptJournal.snapshot().size)
            }
        }
    }

    @Test
    fun `explicit fixture route preserves replace migration semantics with exact bounded flags`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(921L, "Fixture")
            val target = manga(921L, "/fixture-replace", 61L)
            val origin = origin()
            val flags = setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY)
            val fixtureGateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            val resolver = BestVersionPairedFixtureRouteResolverContract { _, _ ->
                BestVersionPairedFixtureRouteBinding(fixtureGateway, flags) { true }
            }
            val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                coEvery {
                    useCase(current = origin, target = target, replace = true, presetFlags = flags, throttleFunc = any())
                } returns MigrationOutcome.Success(flags, flags, emptySet())
            }
            BestVersionHostRouteFixture(
                environment,
                origin,
                ScriptedBestVersionSearchGateway(emptyList(), emptyMap()),
                migrateMangaUseCase = migrate,
                fixtureOperationId = "operation-token",
                fixtureRouteResolver = resolver,
            ).use { route ->
                route.advanceUntilIdle()
                route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
                route.model.confirmMigration(replace = true)
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.Done, route.state.step)
                val receipt = MigrationReceiptJournal.snapshot().single()
                assertTrue(receipt.replace)
                coVerify(exactly = 1) {
                    migrate(current = origin, target = target, replace = true, presetFlags = flags, throttleFunc = any())
                }
            }
        }
    }

    @Test
    fun `fixture route refuses a stale target immediately before migration`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(915L, "Fixture")
            val target = manga(915L, "/stale", 55L)
            val origin = origin()
            val fixtureGateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            val resolver = BestVersionPairedFixtureRouteResolverContract { _, _ ->
                BestVersionPairedFixtureRouteBinding(fixtureGateway, setOf(MigrationFlag.CHAPTER)) { false }
            }
            val migrate = mockk<MigrateMangaUseCase>(relaxed = true)
            BestVersionHostRouteFixture(
                environment,
                origin,
                ScriptedBestVersionSearchGateway(emptyList(), emptyMap()),
                migrateMangaUseCase = migrate,
                fixtureOperationId = "operation-token",
                fixtureRouteResolver = resolver,
            ).use { route ->
                route.advanceUntilIdle()
                route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
                route.model.confirmMigration(replace = false)
                route.advanceUntilIdle()

                assertEquals(BestVersionStep.Error(exh.recs.bestversion.BestVersionErrorReason.SourceUnavailable), route.state.step)
                coVerify(exactly = 0) { migrate(any(), any(), any(), any(), any()) }
                assertTrue(NonUndoableEventJournal.isEmpty())
                assertTrue(MigrationReceiptJournal.isEmpty())
            }
        }
    }

    @Test
    fun `duplicate migration confirmation launches only one operation`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(916L, "Duplicate")
            val target = manga(916L, "/duplicate", 56L)
            val origin = origin()
            val completion = CompletableDeferred<MigrationOutcome>()
            val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                coEvery { useCase(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any()) } coAnswers {
                    completion.await()
                }
            }
            val gateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            BestVersionHostRouteFixture(environment, origin, gateway, migrateMangaUseCase = migrate).use { route ->
                route.advanceUntilIdle()
                route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
                route.model.confirmMigration(replace = false)
                environment.runCurrent()
                route.model.confirmMigration(replace = false)
                environment.runCurrent()

                coVerify(exactly = 1) {
                    migrate(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any())
                }
                completion.complete(MigrationOutcome.Success(emptySet(), emptySet(), emptySet()))
                route.advanceUntilIdle()
                assertEquals(BestVersionStep.Done, route.state.step)
            }
        }
    }

    @Test
    fun `route disposal cancels an active migration and records nothing`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(917L, "Dispose")
            val target = manga(917L, "/dispose", 57L)
            val origin = origin()
            val started = CompletableDeferred<Unit>()
            val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                coEvery { useCase(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any()) } coAnswers {
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
            val gateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            val route = BestVersionHostRouteFixture(environment, origin, gateway, migrateMangaUseCase = migrate)
            route.advanceUntilIdle()
            route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
            route.model.confirmMigration(replace = false)
            environment.runCurrent()
            assertTrue(started.isCompleted)

            route.close()
            route.advanceUntilIdle()

            assertTrue(route.model.migrationJob?.isCancelled == true)
            assertTrue(NonUndoableEventJournal.isEmpty())
            assertTrue(MigrationReceiptJournal.isEmpty())
        }
    }

    @Test
    fun `not-started and thrown migration failures remain typed and record nothing`() {
        listOf<suspend () -> MigrationOutcome>(
            { MigrationOutcome.NotStarted(IllegalStateException("not-started-private")) },
            { throw IllegalStateException("thrown-private") },
        ).forEachIndexed { index, outcome ->
            HostRouteTestEnvironment().use { environment ->
                val source = source(918L + index, "Failure")
                val target = manga(918L + index, "/failure-$index", 58L + index)
                val origin = origin()
                val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                    coEvery { useCase(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any()) } coAnswers {
                        outcome()
                    }
                }
                val gateway = ScriptedBestVersionSearchGateway(
                    listOf(source),
                    mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
                )
                BestVersionHostRouteFixture(environment, origin, gateway, migrateMangaUseCase = migrate).use { route ->
                    route.advanceUntilIdle()
                    route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
                    route.model.confirmMigration(replace = false)
                    route.advanceUntilIdle()

                    assertTrue(route.state.step is BestVersionStep.Error)
                    assertTrue(NonUndoableEventJournal.isEmpty())
                    assertTrue(MigrationReceiptJournal.isEmpty())
                }
            }
        }
    }

    @Test
    fun `cancellation in auxiliary quality recording cannot erase a completed migration`() {
        HostRouteTestEnvironment().use { environment ->
            val source = source(920L, "Quality")
            val target = manga(920L, "/quality", 60L)
            val origin = origin()
            val migrate = mockk<MigrateMangaUseCase>().also { useCase ->
                coEvery { useCase(current = origin, target = target, replace = false, presetFlags = any(), throttleFunc = any()) } returns
                    MigrationOutcome.Success(emptySet(), emptySet(), emptySet())
            }
            val quality = mockk<UpsertMangaSourceQualitySignal>().also { signal ->
                coEvery { signal.insert(any()) } throws CancellationException("quality-write-cancelled")
            }
            val gateway = ScriptedBestVersionSearchGateway(
                listOf(source),
                mapOf(source to SameMangaCandidateResult.Success(listOf(target))),
            )
            BestVersionHostRouteFixture(
                environment,
                origin,
                gateway,
                migrateMangaUseCase = migrate,
                upsertQualitySignal = quality,
            ).use { route ->
                route.advanceUntilIdle()
                route.model.selectBestVersion(MangaIdentityKey(target.source, target.url))
                route.model.confirmMigration(replace = false)
                route.advanceUntilIdle()

                assertTrue(route.model.migrationJob?.isCancelled == true)
                assertEquals(BestVersionStep.Done, route.state.step)
                assertTrue(route.state.migrationComplete)
                assertEquals(1, NonUndoableEventJournal.snapshot().size)
                assertEquals(1, MigrationReceiptJournal.snapshot().size)
            }
        }
    }
}

private class BestVersionHostRouteFixture(
    private val environment: HostRouteTestEnvironment,
    origin: Manga?,
    gateway: BestVersionCandidateSearchGateway,
    sourceManager: SourceManager = mockk(relaxed = true),
    getChapters: GetChaptersByMangaId = mockk(relaxed = true),
    migrateMangaUseCase: MigrateMangaUseCase = mockk(relaxed = true),
    // Route tests exercise migration/chapter behavior, not the product's exact-title default.
    // Make that intent explicit so a fixture title mismatch cannot silently turn the candidate
    // set empty when the production default-selection policy changes.
    sourcePreferences: SourcePreferences = SourcePreferences(FakePreferenceStore()).apply {
        sameMangaMatchPreselectionMode().set("all")
    },
    fixtureOperationId: String? = null,
    fixtureRouteResolver: BestVersionPairedFixtureRouteResolverContract? = null,
    upsertQualitySignal: UpsertMangaSourceQualitySignal = mockk(relaxed = true),
) : AutoCloseable {
    private var closed = false
    private val originId = origin?.id ?: 9_001L
    private val getManga = mockk<GetManga>().also { interactor ->
        coEvery { interactor.await(originId) } returns origin
    }
    private val identityRepository = FakeTasteRepository()
    private val identityController = CrossSourceIdentityDecisionController(
        GetCrossSourceIdentityDecisions(identityRepository),
        ReplaceCrossSourceIdentityDecisions(identityRepository),
    )
    private val cleanJournals = Unit.also {
        NonUndoableEventJournal.clear()
        MigrationReceiptJournal.clear()
        CrossSourceIdentityUndoJournal.clear()
    }

    val model = cleanJournals.let {
        environment.createScreenModel {
            BestVersionCompareScreenModel(
                originMangaId = originId,
                sourcePreferences = sourcePreferences,
                sourceManager = sourceManager,
                getMangaInteractor = getManga,
                getChaptersByMangaId = getChapters,
                networkToLocalManga = mockk<NetworkToLocalManga>(relaxed = true),
                migrateMangaUseCase = migrateMangaUseCase,
                upsertQualitySignal = upsertQualitySignal,
                dispatcherHandle = DispatcherHandle(environment.dispatcher),
                isLowRamDevice = false,
                candidateSearchGateway = gateway,
                fixtureOperationId = fixtureOperationId,
                fixtureRouteResolver = fixtureRouteResolver,
                identityController = identityController,
            )
        }
    }

    val state: BestVersionCompareScreenModel.State
        get() = model.state.value

    init {
        environment.registerCleanup {
            try {
                close()
            } finally {
                NonUndoableEventJournal.clear()
                MigrationReceiptJournal.clear()
            }
        }
    }

    fun advanceUntilIdle() {
        environment.advanceUntilIdle()
    }

    override fun close() {
        if (closed) return
        closed = true
        environment.disposeScreenModels()
    }
}

private class ScriptedBestVersionSearchGateway(
    private val sources: List<Source>,
    private val results: Map<Source, SameMangaCandidateResult>,
) : BestVersionCandidateSearchGateway {
    var searchCount = 0
        private set

    override fun getMatchingSources(): List<Source> = sources

    override suspend fun search(
        queries: List<String>,
        settings: SameMangaMatchSettings,
        originManga: Manga,
        sources: List<Source>,
        onResult: suspend (SameMangaSourceResult) -> Unit,
    ) {
        searchCount++
        sources.forEach { source ->
            onResult(SameMangaSourceResult(source, results.getValue(source)))
        }
    }
}

private fun source(id: Long, name: String): Source = mockk<Source>().also { source ->
    every { source.id } returns id
    every { source.name } returns name
    every { source.lang } returns "en"
}

private fun httpSource(id: Long, name: String): HttpSource = mockk<HttpSource>(relaxed = true).also { source ->
    every { source.id } returns id
    every { source.name } returns name
    every { source.lang } returns "en"
}

private fun origin(): Manga = manga(900L, "/origin", 9_000L)

private fun manga(source: Long, url: String, id: Long): Manga = Manga.create().copy(
    id = id,
    source = source,
    url = url,
    ogTitle = "Fixture $id",
)

private fun sourceChapter(number: Float): SChapter = SChapter.create().apply {
    url = "/chapter/$number"
    name = "Chapter $number"
    chapter_number = number
}

private fun domainChapter(number: Double, mangaId: Long): Chapter = Chapter(
    id = number.toLong(),
    mangaId = mangaId,
    read = false,
    bookmark = false,
    lastPageRead = 0,
    dateFetch = 0L,
    sourceOrder = number.toLong(),
    url = "/chapter/$number",
    name = "Chapter $number",
    dateUpload = 0L,
    chapterNumber = number,
    scanlator = null,
    lastModifiedAt = 0L,
    version = 1L,
    memo = JsonObject(emptyMap()),
)

private fun previewPages(prefix: String): List<Page> = (0 until 10).map { index ->
    Page(index = index, imageUrl = "https://fixture.invalid/$prefix/$index.jpg")
}
