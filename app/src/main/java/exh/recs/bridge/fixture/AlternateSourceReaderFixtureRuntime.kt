package exh.recs.bridge.fixture

import android.content.Context
import android.util.Log
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import exh.recs.bestversion.fixture.BestVersionPairedFixtureMode
import exh.recs.bestversion.fixture.BestVersionPairedFixtureRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class AlternateSourceReaderFixtureCommand { OPEN_MANGA, OPEN_READER, CLEANUP }

sealed interface AlternateSourceReaderFixtureCommandState {
    data object Idle : AlternateSourceReaderFixtureCommandState
    data class Running(val command: AlternateSourceReaderFixtureCommand) : AlternateSourceReaderFixtureCommandState
    data class Succeeded(val command: AlternateSourceReaderFixtureCommand) : AlternateSourceReaderFixtureCommandState
    data class Partial(val command: AlternateSourceReaderFixtureCommand) : AlternateSourceReaderFixtureCommandState
    data class Failed(val command: AlternateSourceReaderFixtureCommand) : AlternateSourceReaderFixtureCommandState
}

sealed interface AlternateSourceReaderFixturePrepareOutcome {
    data class Ready(val route: AlternateSourceReaderFixtureLaunch) : AlternateSourceReaderFixturePrepareOutcome
    data object NotAuthorized : AlternateSourceReaderFixturePrepareOutcome
    data object RecoveryRequired : AlternateSourceReaderFixturePrepareOutcome
    data object Partial : AlternateSourceReaderFixturePrepareOutcome
    data object Failed : AlternateSourceReaderFixturePrepareOutcome
}

sealed interface AlternateSourceReaderFixtureCleanupOutcome {
    data object Cleaned : AlternateSourceReaderFixtureCleanupOutcome
    data object Partial : AlternateSourceReaderFixtureCleanupOutcome
    data object Failed : AlternateSourceReaderFixtureCleanupOutcome
}

interface AlternateSourceReaderFixtureCommandGateway {
    suspend fun prepare(
        scenario: AlternateSourceReaderFixtureScenario,
        destination: AlternateSourceReaderFixtureDestination,
    ): AlternateSourceReaderFixturePrepareOutcome

    suspend fun cleanup(): AlternateSourceReaderFixtureCleanupOutcome
}

class AlternateSourceReaderFixtureCommandController(
    private val scenarioProvider: () -> AlternateSourceReaderFixtureScenario,
    private val gateway: AlternateSourceReaderFixtureCommandGateway,
    private val launcher: AlternateSourceReaderFixtureRouteLauncher,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private companion object {
        const val TAG = "KMKFixture"
    }

    private val mutableState = MutableStateFlow<AlternateSourceReaderFixtureCommandState>(
        AlternateSourceReaderFixtureCommandState.Idle,
    )
    val state: StateFlow<AlternateSourceReaderFixtureCommandState> = mutableState.asStateFlow()

    @Synchronized
    fun prepareAndOpenManga(): Boolean = startPrepare(
        AlternateSourceReaderFixtureCommand.OPEN_MANGA,
        AlternateSourceReaderFixtureDestination.MANGA,
    )

    @Synchronized
    fun prepareAndOpenReader(): Boolean = startPrepare(
        AlternateSourceReaderFixtureCommand.OPEN_READER,
        AlternateSourceReaderFixtureDestination.READER,
    )

    @Synchronized
    fun cleanup(): Boolean {
        if (mutableState.value is AlternateSourceReaderFixtureCommandState.Running) return false
        val command = AlternateSourceReaderFixtureCommand.CLEANUP
        mutableState.value = AlternateSourceReaderFixtureCommandState.Running(command)
        logcat(LogPriority.WARN) { "[$TAG] command dispatch command=$command" }
        scope.launch {
            try {
                val outcome = gateway.cleanup()
                logcat(LogPriority.WARN) { "[$TAG] cleanup outcome=$outcome" }
                val terminal = when (outcome) {
                    AlternateSourceReaderFixtureCleanupOutcome.Cleaned ->
                        AlternateSourceReaderFixtureCommandState.Succeeded(command)
                    AlternateSourceReaderFixtureCleanupOutcome.Partial ->
                        AlternateSourceReaderFixtureCommandState.Partial(command)
                    AlternateSourceReaderFixtureCleanupOutcome.Failed ->
                        AlternateSourceReaderFixtureCommandState.Failed(command)
                }
                complete(command, terminal)
            } catch (e: CancellationException) {
                resetIfRunning(command)
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "[$TAG] cleanup failed with exception" }
                complete(command, AlternateSourceReaderFixtureCommandState.Failed(command))
            }
        }
        return true
    }

    @Synchronized
    fun dismissResult(): Boolean {
        if (mutableState.value is AlternateSourceReaderFixtureCommandState.Running) return false
        if (mutableState.value == AlternateSourceReaderFixtureCommandState.Idle) return false
        mutableState.value = AlternateSourceReaderFixtureCommandState.Idle
        return true
    }

    private fun startPrepare(
        command: AlternateSourceReaderFixtureCommand,
        destination: AlternateSourceReaderFixtureDestination,
    ): Boolean {
        if (mutableState.value is AlternateSourceReaderFixtureCommandState.Running) return false
        val scenario = scenarioProvider()
        if (scenario == AlternateSourceReaderFixtureScenario.OFF) return false
        mutableState.value = AlternateSourceReaderFixtureCommandState.Running(command)
        logcat(LogPriority.WARN) { "[$TAG] command dispatch command=$command scenario=$scenario" }
        scope.launch {
            try {
                val terminal = when (val outcome = gateway.prepare(scenario, destination)) {
                    is AlternateSourceReaderFixturePrepareOutcome.Ready -> {
                        logcat(LogPriority.WARN) { "[$TAG] prepare outcome ready command=$command destination=$destination" }
                        try {
                            launcher.launch(outcome.route)
                            logcat(LogPriority.WARN) { "[$TAG] route launch succeeded command=$command destination=$destination" }
                            AlternateSourceReaderFixtureCommandState.Succeeded(command)
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) {
                                "[$TAG] route launch failed command=$command destination=$destination"
                            }
                            AlternateSourceReaderFixtureCommandState.Partial(command)
                        }
                    }
                    AlternateSourceReaderFixturePrepareOutcome.Partial,
                    AlternateSourceReaderFixturePrepareOutcome.RecoveryRequired,
                    -> {
                        logcat(LogPriority.WARN) {
                            "[$TAG] prepare outcome partial command=$command destination=$destination outcome=$outcome"
                        }
                        AlternateSourceReaderFixtureCommandState.Partial(command)
                    }
                    AlternateSourceReaderFixturePrepareOutcome.Failed,
                    AlternateSourceReaderFixturePrepareOutcome.NotAuthorized,
                    -> {
                        logcat(LogPriority.WARN) {
                            "[$TAG] prepare outcome failed command=$command destination=$destination outcome=$outcome"
                        }
                        AlternateSourceReaderFixtureCommandState.Failed(command)
                    }
                }
                complete(command, terminal)
            } catch (e: CancellationException) {
                resetIfRunning(command)
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) {
                    "[$TAG] prepare command crashed command=$command destination=$destination"
                }
                complete(command, AlternateSourceReaderFixtureCommandState.Failed(command))
            }
        }
        return true
    }

    @Synchronized
    private fun complete(
        command: AlternateSourceReaderFixtureCommand,
        state: AlternateSourceReaderFixtureCommandState,
    ) {
        if (mutableState.value == AlternateSourceReaderFixtureCommandState.Running(command)) {
            mutableState.value = state
        }
    }

    @Synchronized
    private fun resetIfRunning(command: AlternateSourceReaderFixtureCommand) {
        if (mutableState.value == AlternateSourceReaderFixtureCommandState.Running(command)) {
            mutableState.value = AlternateSourceReaderFixtureCommandState.Idle
        }
    }
}

class AlternateSourceReaderFixtureRuntime(
    context: Context,
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    preferenceStore: PreferenceStore = Injekt.get(),
    private val dataStore: AlternateSourceReaderFixtureDataStore = RepositoryAlternateSourceReaderFixtureDataStore(),
    private val isDebugBuild: Boolean = BuildConfig.DEBUG,
    private val fixtureProfile: String = BuildConfig.ALTERNATE_SOURCE_READER_FIXTURE_PROFILE,
    private val pairedFixtureProfile: String = BuildConfig.BEST_VERSION_FIXTURE_PROFILE,
    private val expectedSignerSha256: String = BuildConfig.SOURCES_TO_TRY_FIXTURE_SIGNER_SHA256,
    launcher: AlternateSourceReaderFixtureRouteLauncher = AndroidAlternateSourceReaderFixtureRouteLauncher(context),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AlternateSourceReaderFixtureCommandGateway {
    private val recoveryStore = PreferenceAlternateSourceReaderFixtureRecoveryStore(preferenceStore)
    private val coordinator = AlternateSourceReaderFixtureCoordinator(recoveryStore, dataStore)
    private val routeResolver = AlternateSourceReaderFixtureRouteResolver(
        recoveryStore = recoveryStore,
        dataStore = dataStore,
        diagnostic = { message -> Log.w(TAG, message) },
    )
    val commandController = AlternateSourceReaderFixtureCommandController(
        scenarioProvider = ::scenario,
        gateway = this,
        launcher = launcher,
        scope = scope,
    )

    fun scenario(): AlternateSourceReaderFixtureScenario = AlternateSourceReaderFixtureScenario.fromPrefValue(
        sourcePreferences.alternateSourceReaderFixtureScenario().get(),
    )

    fun controlsAvailable(installedExtensions: List<Extension.Installed> = extensionManager.installedExtensionsFlow.value): Boolean {
        val activation = activation(installedExtensions).copy(scenario = AlternateSourceReaderFixtureScenario.EXACT_GAP)
        val allowed = AlternateSourceReaderFixtureGate.isAllowed(activation)
        Log.w(
            TAG,
            "controls available=$allowed debug=${activation.isDebugBuild} evaluation=${activation.evaluationModeEnabled} " +
                "scenario=${activation.scenario} profile=${activation.fixtureProfile} " +
                "pairedProfile=${activation.pairedFixtureProfile} installedSources=${activation.installedSources.size}",
        )
        return allowed
    }

    override suspend fun prepare(
        scenario: AlternateSourceReaderFixtureScenario,
        destination: AlternateSourceReaderFixtureDestination,
    ): AlternateSourceReaderFixturePrepareOutcome {
        val activation = activation().copy(scenario = scenario)
        Log.w(
            TAG,
            "prepare start scenario=$scenario destination=$destination allowed=${AlternateSourceReaderFixtureGate.isAllowed(activation)} " +
                "profile=${activation.fixtureProfile} pairedProfile=${activation.pairedFixtureProfile} " +
                "installedSources=${activation.installedSources.size}",
        )
        val manifest = when (val seeded = coordinator.seed(activation)) {
            is AlternateSourceReaderFixtureSeedResult.Seeded -> seeded.manifest
            AlternateSourceReaderFixtureSeedResult.NotAuthorized -> {
                Log.w(TAG, "prepare seed outcome=NotAuthorized")
                return AlternateSourceReaderFixturePrepareOutcome.NotAuthorized
            }
            AlternateSourceReaderFixtureSeedResult.Collision -> {
                Log.w(TAG, "prepare seed outcome=Collision")
                return AlternateSourceReaderFixturePrepareOutcome.Partial
            }
            AlternateSourceReaderFixtureSeedResult.RecoveryRecordCorrupt,
            AlternateSourceReaderFixtureSeedResult.RecoveryRequired,
            -> {
                Log.w(TAG, "prepare seed outcome=RecoveryRequired")
                return AlternateSourceReaderFixturePrepareOutcome.RecoveryRequired
            }
            is AlternateSourceReaderFixtureSeedResult.Failed -> {
                Log.w(TAG, "prepare seed outcome=Failed cleanup=${seeded.cleanupCompleted}")
                return if (seeded.cleanupCompleted) {
                    AlternateSourceReaderFixturePrepareOutcome.Failed
                } else {
                    AlternateSourceReaderFixturePrepareOutcome.Partial
                }
            }
        }
        if (manifest.scenario != scenario) return AlternateSourceReaderFixturePrepareOutcome.Failed
        return when (val resolved = routeResolver.resolve(scenario)) {
            is AlternateSourceReaderFixtureRouteResolution.Ready -> {
                Log.w(TAG, "prepare route outcome=Ready")
                val route = when (destination) {
                    AlternateSourceReaderFixtureDestination.MANGA -> resolved.manga
                    AlternateSourceReaderFixtureDestination.READER -> resolved.reader
                }
                AlternateSourceReaderFixturePrepareOutcome.Ready(route)
            }
            AlternateSourceReaderFixtureRouteResolution.NotReady -> {
                Log.w(TAG, "prepare route outcome=NotReady")
                AlternateSourceReaderFixturePrepareOutcome.Partial
            }
        }
    }

    override suspend fun cleanup(): AlternateSourceReaderFixtureCleanupOutcome = when (coordinator.cleanup()) {
        AlternateSourceReaderFixtureCleanupResult.NothingToClean,
        AlternateSourceReaderFixtureCleanupResult.Cleaned,
        -> AlternateSourceReaderFixtureCleanupOutcome.Cleaned
        AlternateSourceReaderFixtureCleanupResult.RecoveryRecordCorrupt ->
            AlternateSourceReaderFixtureCleanupOutcome.Failed
        AlternateSourceReaderFixtureCleanupResult.ConflictOrFailure ->
            AlternateSourceReaderFixtureCleanupOutcome.Partial
    }

    private fun activation(
        installedExtensions: List<Extension.Installed> = extensionManager.installedExtensionsFlow.value,
    ): AlternateSourceReaderFixtureActivation {
        val paired = BestVersionPairedFixtureRuntime.activationFor(
            isDebugBuild = isDebugBuild,
            mode = BestVersionPairedFixtureMode.PAIRED_RECORDS,
            evaluationModeEnabled = sourcePreferences.evaluationMode().get(),
            fixtureProfile = pairedFixtureProfile,
            expectedSignerSha256 = expectedSignerSha256,
            installedExtensions = installedExtensions,
        )
        return AlternateSourceReaderFixtureActivation(
            isDebugBuild = isDebugBuild,
            scenario = scenario(),
            evaluationModeEnabled = paired.evaluationModeEnabled,
            fixtureProfile = fixtureProfile,
            pairedFixtureProfile = paired.fixtureProfile,
            expectedSignerSha256 = paired.expectedSignerSha256,
            installedSources = paired.installedSources,
        )
    }

    private companion object {
        const val TAG = "KMKFixture"
    }
}
