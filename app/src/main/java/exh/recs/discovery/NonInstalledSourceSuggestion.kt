package exh.recs.discovery

import eu.kanade.tachiyomi.extension.model.Extension

// KMK -->
enum class SuggestionConfidence { LOW, MEDIUM }

sealed interface NonInstalledSuggestionReason {
    data class LanguageMatch(val lang: String) : NonInstalledSuggestionReason
    data class SimilarToInstalledSource(val sourceName: String) : NonInstalledSuggestionReason
    data class SameRepoAsInstalledSources(val repoName: String) : NonInstalledSuggestionReason
    data object NeedsTesting : NonInstalledSuggestionReason
    data object UserLikedSource : NonInstalledSuggestionReason
    // KMK -->
    /** Source was evaluated and scored as a strong fit for the user's taste profile. */
    data object EvaluatedStrongFit : NonInstalledSuggestionReason
    /** Source was evaluated and scored as worth trying. */
    data object EvaluatedWorthTrying : NonInstalledSuggestionReason
    /** Source was evaluated and found to contain heavy explicit content. */
    data object EvaluatedExplicitHeavy : NonInstalledSuggestionReason
    /** Source was evaluated and found to contain heavy ecchi/mature content. */
    data object EvaluatedEcchiHeavy : NonInstalledSuggestionReason
    // KMK <--
}

/**
 * A non-installed extension/source suggested for the user to try for recommendations.
 * Stores the original [Extension.Available] identity (not a synthetic GetExtensionsByType copy)
 * so that [extension] can be passed directly to ExtensionManager.installExtension().
 */
data class NonInstalledSourceSuggestion(
    val extension: Extension.Available,
    val source: Extension.Available.Source?,
    val score: Double,
    val confidence: SuggestionConfidence,
    val reasons: List<NonInstalledSuggestionReason>,
) {
    val dismissalKey: String = buildDismissalKey(extension.signatureHash, extension.pkgName, source?.id)
    val displayName: String = source?.name ?: extension.name
    val displayLang: String = source?.lang ?: extension.lang
    val displayBaseUrl: String = source?.baseUrl ?: ""
    val displayRepoName: String = extension.repoName
}

/** Stable per-suggestion key for dismissal persistence. */
internal fun buildDismissalKey(signatureHash: String, pkgName: String, sourceId: Long?): String =
    if (sourceId != null) "$signatureHash|$pkgName|$sourceId" else "$signatureHash|$pkgName"

/**
 * Slim representation of an installed extension used by [NonInstalledSourceSuggestionScorer]
 * to avoid depending on Extension.Installed's Drawable field in unit tests.
 */
internal data class InstalledExtensionHints(
    val signatureHash: String,
    val pkgName: String,
    val repoName: String?,
    val sourceNames: List<String>,
)
// KMK <--
