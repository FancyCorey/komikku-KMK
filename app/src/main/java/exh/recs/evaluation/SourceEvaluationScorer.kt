package exh.recs.evaluation

import exh.source.ExplicitSourceClassifier
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.taste.model.normalizeTag

// KMK -->
/**
 * Pure stateless scorer for source evaluation probe results.
 * No Android dependencies — fully unit-testable.
 *
 * Scoring rules:
 * - explicit_score is based on explicit keywords in sampled titles/tags. Does NOT depend on
 *   isNsfw alone. Does NOT trigger on ecchi-only signals.
 * - ecchi_score is based on ecchi/mature/lewd signals that are NOT explicit porn/hentai.
 * - explicit_heavy and ecchi_heavy remain separate verdicts.
 * - recommendation_fit_score uses taste-profile tag/title overlap.
 * - quality_score reflects how many usable samples were collected.
 * - search_reliability_score reflects how often search probes returned results.
 * - Evaluated positive scores can exceed 0.70 (reserved range for post-install evidence).
 */
object SourceEvaluationScorer {

    private val EXPLICIT_TAG_TERMS = setOf(
        "hentai", "porn", "pornographic", "explicit", "yaoi", "yuri",
        "adult content", "erotic", "erotica", "smut", "hentai manga",
        "uncensored", "nsfw", "18+", "xxx",
    )
    private val ECCHI_TAG_TERMS = setOf(
        "ecchi", "mature", "lewd", "fan service", "fanservice", "risque",
        "suggestive", "semi-explicit", "sensual", "sexy", "sexual",
    )
    private val EXPLICIT_TITLE_TERMS = listOf(
        "hentai",
        "porn",
        "xxx",
        "erotic",
        "smut",
    )
    private val ECCHI_TITLE_TERMS = listOf("ecchi")

    fun score(
        extensionName: String,
        pkgName: String,
        signatureHash: String,
        sourceId: Long?,
        sourceName: String,
        lang: String,
        baseUrl: String?,
        repoName: String?,
        sourceCount: Int,
        isNsfw: Boolean,
        sampledTitles: List<String>,
        sampledTags: List<String>,
        popularCount: Int,
        latestCount: Int,
        searchCount: Int,
        searchSuccessCount: Int,
        errorCount: Int,
        tasteProfile: TasteProfile,
        evaluatedAt: Long = System.currentTimeMillis(),
        // KMK --> v0.7.4: extension version at evaluation time
        extensionVersionName: String? = null,
        extensionVersionCode: Long? = null,
        extensionApkName: String? = null,
        // KMK <--
    ): SourceEvaluation {
        val allTitlesLower = sampledTitles.map { it.lowercase() }
        val allTagsLower = sampledTags.map { it.lowercase() }

        // Explicit signal counting
        val explicitFromTitles = allTitlesLower.count { t -> EXPLICIT_TITLE_TERMS.any { t.contains(it) } }
        val explicitFromTags = allTagsLower.count { t -> EXPLICIT_TAG_TERMS.any { t.contains(it) } }
        val explicitSignalCount = explicitFromTitles + explicitFromTags

        // Ecchi signal counting (only when not already explicit)
        val ecchiFromTitles = allTitlesLower.count { t ->
            ECCHI_TITLE_TERMS.any { t.contains(it) } && EXPLICIT_TITLE_TERMS.none { t.contains(it) }
        }
        val ecchiFromTags = allTagsLower.count { t ->
            ECCHI_TAG_TERMS.any { t.contains(it) } && EXPLICIT_TAG_TERMS.none { t.contains(it) }
        }
        val ecchiSignalCount = ecchiFromTitles + ecchiFromTags

        // Taste profile matching
        val normalizedPreferred = tasteProfile.learnedTagWeights
            .filter { it.value > 0 }
            .keys
            .map { it.lowercase() }
            .toSet()
        val normalizedBlocked = tasteProfile.blockedGroups
            .map { it.lowercase() }
            .toSet()

        val preferredTagMatchCount = allTagsLower.count { t ->
            val normalized = t.normalizeTag()
            normalized in normalizedPreferred
        }
        val blockedTagMatchCount = allTagsLower.count { t ->
            val normalized = t.normalizeTag()
            normalized in normalizedBlocked
        }

        // Title match — loved/liked manga title overlap
        // We derive a set of known liked title words from learnedTagWeights labels (coarse proxy)
        val likedTitleMatchCount = 0 // Full implementation requires full taste title list; use 0 as safe default

        val sampleCount = sampledTitles.size
        val totalProbes = popularCount + latestCount + searchCount

        // quality_score: ratio of samples collected vs. expected minimum
        val qualityScore = when {
            sampleCount >= 15 -> 0.9
            sampleCount >= 8 -> 0.7
            sampleCount >= 3 -> 0.5
            sampleCount >= 1 -> 0.3
            else -> 0.0
        }

        // search_reliability_score: search success ratio
        val searchReliabilityScore = if (searchCount == 0) {
            0.5 // no search attempted; neutral
        } else {
            (searchSuccessCount.toDouble() / searchCount.toDouble()).coerceIn(0.0, 1.0)
        }

        // recommendation_fit_score: preferred tag matches minus blocked penalties
        val fitBase = when {
            preferredTagMatchCount >= 5 -> 0.85
            preferredTagMatchCount >= 3 -> 0.70
            preferredTagMatchCount >= 1 -> 0.55
            else -> 0.35
        }
        val fitPenalty = when {
            blockedTagMatchCount >= 3 -> 0.30
            blockedTagMatchCount >= 1 -> 0.15
            else -> 0.0
        }
        val recommendationFitScore = (fitBase - fitPenalty).coerceIn(0.0, 1.0)

        // explicit_score: ratio of explicit signals to samples, biased upward for extensions
        // already classified explicit by name/package
        val nameExplicitBias = if (ExplicitSourceClassifier.isExplicitName(extensionName) ||
            ExplicitSourceClassifier.isExplicitPackageName(pkgName)
        ) {
            0.4
        } else {
            0.0
        }
        val explicitScore = if (sampleCount == 0) {
            nameExplicitBias
        } else {
            (explicitSignalCount.toDouble() / sampleCount.toDouble() * 1.5 + nameExplicitBias).coerceIn(0.0, 1.0)
        }

        // ecchi_score: similar to explicit but for ecchi signals, no name bias
        val ecchiScore = if (sampleCount == 0) {
            0.0
        } else {
            (ecchiSignalCount.toDouble() / sampleCount.toDouble() * 1.2).coerceIn(0.0, 1.0)
        }

        // Verdict
        val verdict = when {
            errorCount > 0 && sampleCount == 0 -> SourceEvaluationVerdict.ERROR
            explicitScore >= 0.5 -> SourceEvaluationVerdict.EXPLICIT_HEAVY
            ecchiScore >= 0.5 && explicitScore < 0.3 -> SourceEvaluationVerdict.ECCHI_HEAVY
            recommendationFitScore >= 0.70 && qualityScore >= 0.5 && searchReliabilityScore >= 0.4 ->
                SourceEvaluationVerdict.STRONG_FIT
            recommendationFitScore >= 0.50 && qualityScore >= 0.3 ->
                SourceEvaluationVerdict.WORTH_TRYING
            searchReliabilityScore < 0.2 && searchCount >= 2 -> SourceEvaluationVerdict.POOR_SEARCH
            qualityScore < 0.3 && errorCount > 2 -> SourceEvaluationVerdict.ERROR
            else -> SourceEvaluationVerdict.WEAK
        }

        val evaluationKey = SourceEvaluationKeys.buildKey(signatureHash, pkgName, sourceId)

        return SourceEvaluation(
            evaluationKey = evaluationKey,
            sourceId = sourceId,
            extensionPkgName = pkgName,
            signatureHash = signatureHash,
            extensionName = extensionName,
            sourceName = sourceName,
            lang = lang,
            baseUrl = baseUrl,
            repoName = repoName,
            sourceCount = sourceCount,
            isNsfw = isNsfw,
            evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
            evaluatedAt = evaluatedAt,
            expiresAt = null,
            sampleCount = sampleCount,
            popularCount = popularCount,
            latestCount = latestCount,
            searchCount = searchCount,
            searchSuccessCount = searchSuccessCount,
            likedTitleMatchCount = likedTitleMatchCount,
            preferredTagMatchCount = preferredTagMatchCount,
            blockedTagMatchCount = blockedTagMatchCount,
            explicitSignalCount = explicitSignalCount,
            ecchiSignalCount = ecchiSignalCount,
            errorCount = errorCount,
            qualityScore = qualityScore,
            recommendationFitScore = recommendationFitScore,
            searchReliabilityScore = searchReliabilityScore,
            explicitScore = explicitScore,
            ecchiScore = ecchiScore,
            verdict = verdict,
            sampledTitlesJson = if (sampledTitles.isEmpty()) null else sampledTitles.take(20).joinToString("|"),
            sampledTagsJson = if (sampledTags.isEmpty()) null else sampledTags.distinct().take(30).joinToString("|"),
            errorMessage = null,
            // KMK --> v0.7.4: extension version at evaluation time
            extensionVersionName = extensionVersionName,
            extensionVersionCode = extensionVersionCode,
            extensionApkName = extensionApkName,
            // KMK <--
        )
    }

    /** Create an error record when install/load fails. */
    fun errorRecord(
        extensionName: String,
        pkgName: String,
        signatureHash: String,
        sourceId: Long?,
        sourceName: String,
        lang: String,
        repoName: String?,
        isNsfw: Boolean,
        errorMessage: String,
        evaluatedAt: Long = System.currentTimeMillis(),
        // KMK --> v0.7.4: extension version at evaluation time
        extensionVersionName: String? = null,
        extensionVersionCode: Long? = null,
        extensionApkName: String? = null,
        // KMK <--
    ): SourceEvaluation {
        val key = SourceEvaluationKeys.buildKey(signatureHash, pkgName, sourceId)
        return SourceEvaluation(
            evaluationKey = key,
            sourceId = sourceId,
            extensionPkgName = pkgName,
            signatureHash = signatureHash,
            extensionName = extensionName,
            sourceName = sourceName,
            lang = lang,
            baseUrl = null,
            repoName = repoName,
            sourceCount = 0,
            isNsfw = isNsfw,
            evaluationVersion = SourceEvaluationKeys.CURRENT_VERSION,
            evaluatedAt = evaluatedAt,
            expiresAt = null,
            sampleCount = 0,
            popularCount = 0,
            latestCount = 0,
            searchCount = 0,
            searchSuccessCount = 0,
            likedTitleMatchCount = 0,
            preferredTagMatchCount = 0,
            blockedTagMatchCount = 0,
            explicitSignalCount = 0,
            ecchiSignalCount = 0,
            errorCount = 1,
            qualityScore = 0.0,
            recommendationFitScore = 0.0,
            searchReliabilityScore = 0.0,
            explicitScore = 0.0,
            ecchiScore = 0.0,
            verdict = SourceEvaluationVerdict.ERROR,
            sampledTitlesJson = null,
            sampledTagsJson = null,
            errorMessage = errorMessage,
            // KMK --> v0.7.4: extension version at evaluation time
            extensionVersionName = extensionVersionName,
            extensionVersionCode = extensionVersionCode,
            extensionApkName = extensionApkName,
            // KMK <--
        )
    }
}
// KMK <--
