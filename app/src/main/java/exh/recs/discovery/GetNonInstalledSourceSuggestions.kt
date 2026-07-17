package exh.recs.discovery

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import exh.recs.RecommendationSourceFilter
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
class GetNonInstalledSourceSuggestions(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getSourceEvaluations: GetSourceEvaluations = Injekt.get(),
) {
    fun subscribe(): Flow<List<NonInstalledSourceSuggestion>> {
        val dismissedPref = sourcePreferences.dismissedNonInstalledRecommendationSources()
        val likedPref = sourcePreferences.likedRecommendationSourceKeys()
        val dislikedPref = sourcePreferences.dislikedRecommendationSourceKeys()
        // KMK v0.8.1-fix4: separate source/library-quality dislike axis
        val qualityDislikedPref = sourcePreferences.dislikedSourceQualityKeys()

        val extensionTripleFlow = combine(
            extensionManager.availableExtensionsFlow,
            extensionManager.installedExtensionsFlow,
            extensionManager.untrustedExtensionsFlow,
        ) { a, b, c -> Triple(a, b, c) }

        // kotlinx.coroutines combine supports max 5 type-safe direct params; pair the 6th flow
        // (evaluation records) with dislikedPref so we stay within the 5-source limit.
        // KMK --> v0.6.9: catch DB errors (e.g. missing source_evaluation table on older installs)
        // so Settings/Sources To Try render without crashing when the evaluation table is absent.
        val safeEvaluationsFlow = getSourceEvaluations.subscribeAll()
            .catch { e ->
                logcat(LogPriority.ERROR, e) { "source_evaluation table unavailable; falling back to empty evaluations" }
                emit(emptyList())
            }
        // KMK <--
        val dislikedAndEvaluationsFlow = combine(
            dislikedPref.changes(),
            safeEvaluationsFlow,
            qualityDislikedPref.changes(),
        ) { dislikedRaw, evaluationList, qualityDislikedRaw ->
            val verdictMap: Map<String, SourceEvaluationVerdict> =
                evaluationList.associate { it.evaluationKey to it.verdict }
            Triple(dislikedRaw, verdictMap, qualityDislikedRaw)
        }

        return combine(
            extensionTripleFlow,
            dismissedPref.changes(),
            sourcePreferences.recommendationSourceLanguages().changes(),
            likedPref.changes(),
            dislikedAndEvaluationsFlow,
        ) { (available, installed, untrusted), dismissedRaw, _, likedRaw, (dislikedRaw, evaluations, qualityDislikedRaw) ->
            val nsfwEnabled = sourcePreferences.showNsfwSource().get()
            // KMK -->
            val blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()
            // KMK <--
            val recLanguages = RecommendationSourceFilter.normalizeLanguages(
                sourcePreferences.recommendationSourceLanguages().get(),
            )
            val dismissed = NonInstalledSourceSuggestionStore.parse(dismissedRaw)
            val likedKeys = RecommendationSourcePreferenceStore.parse(likedRaw)
            val dislikedKeys = RecommendationSourcePreferenceStore.parse(dislikedRaw)
            val qualityDislikedKeys = RecommendationSourcePreferenceStore.parse(qualityDislikedRaw) // KMK v0.8.1-fix4
            val installedHints = installed.map { ext ->
                InstalledExtensionHints(
                    signatureHash = ext.signatureHash,
                    pkgName = ext.pkgName,
                    repoName = ext.storeName,
                    sourceNames = ext.sources.map { it.name },
                )
            }
            NonInstalledSourceSuggestionScorer.scoreAndFilter(
                available = available,
                installedHints = installedHints,
                untrusted = untrusted,
                recLanguages = recLanguages,
                nsfwEnabled = nsfwEnabled,
                // KMK -->
                blockExplicit = blockExplicit,
                // KMK <--
                dismissed = dismissed,
                likedKeys = likedKeys,
                dislikedKeys = dislikedKeys,
                // KMK -->
                evaluations = evaluations,
                // KMK <--
                qualityDislikedKeys = qualityDislikedKeys, // KMK v0.8.1-fix4
            )
        }
    }
}
// KMK <--
