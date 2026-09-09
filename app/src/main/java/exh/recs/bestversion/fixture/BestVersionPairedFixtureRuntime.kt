package exh.recs.bestversion.fixture

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Application-scoped entry point used by the later mounted F2 route. */
class BestVersionPairedFixtureRuntime(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    preferenceStore: PreferenceStore = Injekt.get(),
    private val dataStore: BestVersionPairedFixtureDataStore = RepositoryBestVersionPairedFixtureDataStore(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val isDebugBuild: Boolean = BuildConfig.DEBUG,
    private val fixtureProfile: String = BuildConfig.BEST_VERSION_FIXTURE_PROFILE,
    private val expectedSignerSha256: String = BuildConfig.SOURCES_TO_TRY_FIXTURE_SIGNER_SHA256,
    private val modeOverride: BestVersionPairedFixtureMode? = null,
) {
    private val recoveryStore = PreferenceBestVersionPairedFixtureRecoveryStore(preferenceStore)
    val coordinator = BestVersionPairedFixtureCoordinator(
        recoveryStore = recoveryStore,
        dataStore = dataStore,
    )
    val routeController = BestVersionPairedFixtureRouteController(coordinator, ::activation)
    val commandController = BestVersionPairedFixtureCommandController(routeController)
    val routeResolver = BestVersionPairedFixtureRouteResolverContract(::resolveRoute)

    fun controlsAvailable(
        installedExtensions: List<Extension.Installed> = extensionManager.installedExtensionsFlow.value,
    ): Boolean = BestVersionPairedFixtureGate.isAllowed(
        activation(installedExtensions).copy(mode = BestVersionPairedFixtureMode.PAIRED_RECORDS),
    )

    fun activation(
        installedExtensions: List<Extension.Installed> = extensionManager.installedExtensionsFlow.value,
    ): BestVersionPairedFixtureActivation = activationFor(
        isDebugBuild = isDebugBuild,
        mode = modeOverride ?: BestVersionPairedFixtureMode.fromPrefValue(
            sourcePreferences.bestVersionPairedFixtureMode().get(),
        ),
        evaluationModeEnabled = sourcePreferences.evaluationMode().get(),
        fixtureProfile = fixtureProfile,
        expectedSignerSha256 = expectedSignerSha256,
        installedExtensions = installedExtensions,
    )

    private suspend fun resolveRoute(
        operationId: String,
        origin: Manga,
    ): BestVersionPairedFixtureRouteBinding? {
        if (!BestVersionPairedFixtureGate.isAllowed(activation())) return null
        val manifest = when (val loaded = recoveryStore.load()) {
            is BestVersionPairedFixtureManifestLoad.Present -> loaded.manifest
            BestVersionPairedFixtureManifestLoad.Corrupt,
            BestVersionPairedFixtureManifestLoad.Missing,
            -> return null
        }
        val spec = BestVersionPairedFixtureSpec()
        if (!manifest.isStructurallyValid() || manifest.operationId != operationId) return null
        if (manifest.pendingStep != null || manifest.completedSteps != BestVersionPairedFixtureStep.entries.toSet()) return null
        if (manifest.originMangaId != origin.id || origin.source != spec.originSourceId || origin.url != spec.originUrl) return null
        if (origin.notes != manifest.ownershipMarker) return null

        val observed = try {
            dataStore.inspect(spec, manifest)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        if (observed.isAbsent || observed.sha256() != manifest.seededStateHash) return null

        val target = currentTarget(spec, manifest) ?: return null
        val targetSource = sourceManager.get(spec.targetSourceId) ?: return null
        if (targetSource.id != spec.targetSourceId || targetSource.name != "Fixture Source") return null
        val originSource = sourceManager.get(spec.originSourceId) ?: return null
        if (originSource.id != spec.originSourceId || originSource.name != "Fixture Source") return null

        return BestVersionPairedFixtureRouteBinding(
            candidateSearchGateway = ExactBestVersionPairedFixtureSearchGateway(targetSource, target),
            migrationPresetFlags = spec.allowedMigrationFlags,
            isCurrentTarget = { selected ->
                val current = currentTarget(spec, manifest)
                current != null && current.id == selected.id && current.source == selected.source && current.url == selected.url
            },
        )
    }

    private suspend fun currentTarget(
        spec: BestVersionPairedFixtureSpec,
        manifest: BestVersionPairedFixtureManifest,
    ): Manga? {
        val target = try {
            mangaRepository.getMangaByUrlAndSourceId(spec.targetUrl, spec.targetSourceId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        return target?.takeIf {
            it.id == manifest.targetMangaId &&
                it.source == spec.targetSourceId &&
                it.url == spec.targetUrl &&
                it.notes == manifest.ownershipMarker
        }
    }

    companion object {
        internal fun activationFor(
            isDebugBuild: Boolean,
            mode: BestVersionPairedFixtureMode,
            evaluationModeEnabled: Boolean,
            fixtureProfile: String,
            expectedSignerSha256: String,
            installedExtensions: List<Extension.Installed>,
        ): BestVersionPairedFixtureActivation {
            val fixturePackages = setOf(
                BestVersionPairedFixtureGate.ALPHA_PACKAGE,
                BestVersionPairedFixtureGate.BETA_PACKAGE,
            )
            val identities = installedExtensions
                .asSequence()
                .filter { it.pkgName in fixturePackages }
                .mapNotNull { extension ->
                    val source = extension.sources.singleOrNull() ?: return@mapNotNull null
                    BestVersionPairedFixtureSourceIdentity(
                        packageName = extension.pkgName,
                        sourceId = source.id,
                        signerSha256 = extension.signatureHash,
                        extensionName = extension.name,
                        sourceName = source.name,
                        versionName = extension.versionName,
                        versionCode = extension.versionCode,
                    )
                }
                .toSet()
            return BestVersionPairedFixtureActivation(
                isDebugBuild = isDebugBuild,
                mode = mode,
                evaluationModeEnabled = evaluationModeEnabled,
                fixtureProfile = fixtureProfile,
                expectedSignerSha256 = expectedSignerSha256,
                installedSources = identities,
            )
        }
    }
}
