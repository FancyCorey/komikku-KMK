package exh.recs.evaluation

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import exh.recs.RecommendationSourceFilter
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.taste.interactor.GetSourceEvaluationUnsafeSources
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.model.SourceEvaluationUnsafeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
class GetSourceEvaluationCandidates(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getSourceEvaluations: GetSourceEvaluations = Injekt.get(),
    // KMK --> v0.6.16: crash quarantine
    private val getSourceEvaluationUnsafeSources: GetSourceEvaluationUnsafeSources = Injekt.get(),
    // KMK <--
) {
    fun subscribe(): Flow<SourceEvaluationCandidateFilter.CandidatePoolResult> {
        val safeEvaluationsFlow = getSourceEvaluations.subscribeAll()
            .catch { e ->
                logcat(LogPriority.ERROR, e) { "source_evaluation table unavailable in GetSourceEvaluationCandidates" }
                emit(emptyList())
            }

        // KMK --> v0.6.16: crash quarantine — track unsafe sources reactively
        val unsafeSourcesFlow = getSourceEvaluationUnsafeSources.subscribeAll()
            .catch { e ->
                logcat(LogPriority.ERROR, e) { "source_evaluation_unsafe_source table unavailable in GetSourceEvaluationCandidates" }
                emit(emptyList())
            }
        // KMK <--

        val extensionTripleFlow = combine(
            extensionManager.availableExtensionsFlow,
            extensionManager.installedExtensionsFlow,
            extensionManager.untrustedExtensionsFlow,
        ) { a, b, c -> Triple(a, b, c) }

        // Combine all relevant preference changes into a single trigger; values are read synchronously below.
        val prefTriggerFlow = combine(
            sourcePreferences.recommendationSourceLanguages().changes(),
            sourcePreferences.showNsfwSource().changes(),
            sourcePreferences.blockExplicitPornHentaiSources().changes(),
            sourcePreferences.dislikedRecommendationSourceKeys().changes(),
        ) { _, _, _, _ -> Unit }

        return combine(
            extensionTripleFlow,
            prefTriggerFlow,
            safeEvaluationsFlow,
            // KMK --> v0.6.16: crash quarantine
            unsafeSourcesFlow,
            // KMK <--
        ) { (available, installed, untrusted), _, evaluations, unsafeSources ->
            val recLanguages = RecommendationSourceFilter.normalizeLanguages(
                sourcePreferences.recommendationSourceLanguages().get(),
            )
            val nsfwEnabled = sourcePreferences.showNsfwSource().get()
            val blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()
            val dislikedKeys = RecommendationSourcePreferenceStore.parse(
                sourcePreferences.dislikedRecommendationSourceKeys().get(),
            )
            val installedPkgNames = installed.map { it.pkgName }.toSet()
            val untrustedPkgNames = untrusted.map { it.pkgName }.toSet()
            // KMK --> v0.6.16: crash quarantine — build extension-level key set for pool filtering
            val unsafeExtensionKeys = unsafeSources.map { it.extensionKey }.toSet()
            // KMK <--

            SourceEvaluationCandidateFilter.buildPool(
                available = available,
                installedPkgNames = installedPkgNames,
                untrustedPkgNames = untrustedPkgNames,
                recLanguages = recLanguages,
                nsfwEnabled = nsfwEnabled,
                blockExplicit = blockExplicit,
                dislikedKeys = dislikedKeys,
                evaluations = evaluations,
                // KMK --> v0.6.16: crash quarantine
                unsafeExtensionKeys = unsafeExtensionKeys,
                // KMK <--
            )
        }
    }
}
// KMK <--
