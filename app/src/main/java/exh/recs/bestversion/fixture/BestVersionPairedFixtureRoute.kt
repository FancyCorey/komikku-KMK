package exh.recs.bestversion.fixture

import eu.kanade.tachiyomi.source.Source
import exh.recs.bestversion.BestVersionCandidateSearchGateway
import exh.recs.bestversion.BestVersionCompareScreen
import exh.recs.matching.SameMangaCandidateResult
import exh.recs.matching.SameMangaMatchSettings
import exh.recs.matching.SameMangaSourceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.domain.manga.model.Manga

data class BestVersionPairedFixtureRouteDescriptor(
    val originMangaId: Long,
    val operationId: String,
)

fun BestVersionPairedFixtureRouteDescriptor.toCanonicalScreen(): BestVersionCompareScreen =
    BestVersionCompareScreen(originMangaId = originMangaId, fixtureOperationId = operationId)

class BestVersionPairedFixtureCommandController(
    private val routeController: BestVersionPairedFixtureRouteController,
) {
    private val commandMutex = Mutex()
    private val mutableState = MutableStateFlow<BestVersionPairedFixtureCommandState>(
        BestVersionPairedFixtureCommandState.Idle,
    )
    val state: StateFlow<BestVersionPairedFixtureCommandState> = mutableState.asStateFlow()

    suspend fun prepareAndOpen(
        openScreen: (BestVersionCompareScreen) -> Unit,
    ): BestVersionPairedFixtureRoutePreparation = commandMutex.withLock {
        mutableState.value = BestVersionPairedFixtureCommandState.Running
        try {
            val result = routeController.prepare()
            if (result is BestVersionPairedFixtureRoutePreparation.Ready) {
                openScreen(result.descriptor.toCanonicalScreen())
            }
            mutableState.value = result.toCommandState()
            result
        } catch (error: CancellationException) {
            mutableState.value = BestVersionPairedFixtureCommandState.Idle
            throw error
        } catch (_: Exception) {
            mutableState.value = BestVersionPairedFixtureCommandState.Failed
            BestVersionPairedFixtureRoutePreparation.Failed
        }
    }

    suspend fun cleanup(): BestVersionPairedFixtureCleanupResult = commandMutex.withLock {
        val result = routeController.cleanup()
        mutableState.value = BestVersionPairedFixtureCommandState.Idle
        result
    }
}

sealed interface BestVersionPairedFixtureCommandState {
    data object Idle : BestVersionPairedFixtureCommandState
    data object Running : BestVersionPairedFixtureCommandState
    data object Opened : BestVersionPairedFixtureCommandState
    data object NotAuthorized : BestVersionPairedFixtureCommandState
    data object Collision : BestVersionPairedFixtureCommandState
    data object RecoveryRequired : BestVersionPairedFixtureCommandState
    data object Failed : BestVersionPairedFixtureCommandState
}

private fun BestVersionPairedFixtureRoutePreparation.toCommandState(): BestVersionPairedFixtureCommandState = when (this) {
    is BestVersionPairedFixtureRoutePreparation.Ready -> BestVersionPairedFixtureCommandState.Opened
    BestVersionPairedFixtureRoutePreparation.NotAuthorized -> BestVersionPairedFixtureCommandState.NotAuthorized
    BestVersionPairedFixtureRoutePreparation.Collision -> BestVersionPairedFixtureCommandState.Collision
    BestVersionPairedFixtureRoutePreparation.RecoveryRequired -> BestVersionPairedFixtureCommandState.RecoveryRequired
    BestVersionPairedFixtureRoutePreparation.Failed -> BestVersionPairedFixtureCommandState.Failed
}

sealed interface BestVersionPairedFixtureRoutePreparation {
    data class Ready(val descriptor: BestVersionPairedFixtureRouteDescriptor) : BestVersionPairedFixtureRoutePreparation
    data object NotAuthorized : BestVersionPairedFixtureRoutePreparation
    data object Collision : BestVersionPairedFixtureRoutePreparation
    data object RecoveryRequired : BestVersionPairedFixtureRoutePreparation
    data object Failed : BestVersionPairedFixtureRoutePreparation
}

class BestVersionPairedFixtureRouteController(
    private val coordinator: BestVersionPairedFixtureCoordinator,
    private val activationProvider: () -> BestVersionPairedFixtureActivation,
) {
    suspend fun prepare(): BestVersionPairedFixtureRoutePreparation =
        when (val result = coordinator.seed(activationProvider())) {
            is BestVersionPairedFixtureSeedResult.Seeded -> {
                val originId = result.manifest.originMangaId
                if (originId == null) {
                    BestVersionPairedFixtureRoutePreparation.Failed
                } else {
                    BestVersionPairedFixtureRoutePreparation.Ready(
                        BestVersionPairedFixtureRouteDescriptor(originId, result.manifest.operationId),
                    )
                }
            }
            BestVersionPairedFixtureSeedResult.NotAuthorized -> BestVersionPairedFixtureRoutePreparation.NotAuthorized
            BestVersionPairedFixtureSeedResult.Collision -> BestVersionPairedFixtureRoutePreparation.Collision
            BestVersionPairedFixtureSeedResult.RecoveryRecordCorrupt,
            BestVersionPairedFixtureSeedResult.RecoveryRequired,
            -> BestVersionPairedFixtureRoutePreparation.RecoveryRequired
            is BestVersionPairedFixtureSeedResult.Failed -> BestVersionPairedFixtureRoutePreparation.Failed
        }

    suspend fun cleanup(): BestVersionPairedFixtureCleanupResult = coordinator.cleanup()
}

data class BestVersionPairedFixtureRouteBinding(
    val candidateSearchGateway: BestVersionCandidateSearchGateway,
    val migrationPresetFlags: Set<MigrationFlag>,
    val isCurrentTarget: suspend (Manga) -> Boolean,
)

fun interface BestVersionPairedFixtureRouteResolverContract {
    suspend fun resolve(operationId: String, origin: Manga): BestVersionPairedFixtureRouteBinding?
}

internal class ExactBestVersionPairedFixtureSearchGateway(
    private val targetSource: Source,
    private val target: Manga,
) : BestVersionCandidateSearchGateway {
    override fun getMatchingSources(): List<Source> = listOf(targetSource)

    override suspend fun search(
        queries: List<String>,
        settings: SameMangaMatchSettings,
        originManga: Manga,
        sources: List<Source>,
        onResult: suspend (SameMangaSourceResult) -> Unit,
    ) {
        if (sources.singleOrNull()?.id != targetSource.id) return
        onResult(
            SameMangaSourceResult(
                source = targetSource,
                result = SameMangaCandidateResult.Success(listOf(target)),
            ),
        )
    }
}
