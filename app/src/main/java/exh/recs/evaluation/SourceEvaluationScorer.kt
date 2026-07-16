package exh.recs.evaluation

import exh.recs.PersonalRecommendationScorer
import exh.source.ExplicitSourceClassifier
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TasteProfile
import tachiyomi.domain.taste.model.normalizeTag

// KMK -->
/**
 * Pure stateless scorer for source evaluation probe results.
 * No Android dependencies — fully unit-testable.
 *
 * As of v0.7.42, this scorer evaluates **catalogue fit only** — Popular/Latest samples, scored
 * against taste. It no longer pools in a tag-search probe; the honest, separately-labeled
 * search-compatibility signal lives in [SourceRecommendationFitProbe] / `SourceRecommendationFit`.
 *
 * As of v0.7.47, catalogue samples are bounded-enriched via `getMangaDetails()`
 * ([SourceEvaluationCatalogueEnricher]) before reaching this scorer, and evidence is split into
 * distinct positive/negative/blocked/adult/metadata counters instead of one ambiguous
 * `preferredTagMatchCount` (which previously counted *any* positive-scoring candidate, not
 * candidates with an actual preferred-tag match — see
 * `docs/recommendations/KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md`). Metadata-sparse
 * evidence now routes to [SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW] instead of a confident
 * [SourceEvaluationVerdict.WEAK], and noisy/adult/blocked-tag-heavy sources cannot reach
 * [SourceEvaluationVerdict.STRONG_FIT] purely because a few broad positive tags appeared.
 *
 * Taste matching reuses the real production contract, [PersonalRecommendationScorer], applied per
 * sampled catalogue item — the same function For You and [SourceRecommendationFitProbe] already
 * use — instead of an ad-hoc set-membership approximation. Blocked-tag handling is
 * [PersonalRecommendationScorer]'s real hard block per item, not a soft score penalty.
 *
 * Scoring rules:
 * - explicit_score is based on explicit keywords in sampled titles/tags. Does NOT depend on
 *   isNsfw alone. Does NOT trigger on ecchi-only signals.
 * - ecchi_score is based on ecchi/mature/lewd signals that are NOT explicit porn/hentai.
 * - explicit_heavy and ecchi_heavy remain separate verdicts.
 * - adult_signal_candidate_count is a broader, candidate-level risk counter (hentai/porn/smut/
 *   BL/GL/etc.) distinct from explicit_signal_count/ecchi_signal_count, which remain signal-level
 *   counts over titles/tags for the EXPLICIT_HEAVY/ECCHI_HEAVY gates.
 * - recommendation_fit_score (catalogue fit) is derived from positive/negative/blocked/adult
 *   candidate ratios — see the tag-enrichment-and-scoring-fix plan for the exact formula.
 * - quality_score reflects how many catalogue samples were collected.
 * - catalogue_metadata_confidence reflects how many *final* (post-enrichment) catalogue samples
 *   had usable genre metadata, independent of the fit score — low/unknown confidence means the
 *   catalogue evidence is inconclusive, not that the source is a poor fit; it now routes to
 *   NEEDS_MANUAL_REVIEW instead of a confident WEAK.
 * - search_count/search_success_count/search_reliability_score/liked_title_match_count are no
 *   longer populated (always 0/0/0.0/0) — retained as columns for backward-compatible reads of
 *   historical rows rather than a breaking schema removal. See [SourceEvaluation] KDoc.
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

    // KMK --> v0.7.47: candidate-level adult/risk signal terms — broader than EXPLICIT_TAG_TERMS,
    // used only for adultSignalCandidateCount (fit-score penalties / STRONG_FIT gating), never for
    // the EXPLICIT_HEAVY verdict gate (which stays on explicitScore, driven by EXPLICIT_TAG_TERMS).
    private val ADULT_RISK_TERMS = setOf(
        "hentai", "porn", "pornographic", "explicit", "adult", "adult content", "erotic", "erotica",
        "smut", "uncensored", "nsfw", "18+", "xxx", "yaoi", "yuri", "boys love", "girls love",
        "shounen ai", "shoujo ai",
    )
    private val ADULT_RISK_GROUPS = setOf("boys_love", "girls_love")
    // KMK <--

    // KMK --> v0.7.47: per-candidate taste/metadata/adult evidence breakdown. Uses the same alias
    // normalization contract as PersonalRecommendationScorer (normalizeTag() -> aliasMap lookup) so
    // group keys agree exactly with the hard-block/preference resolution PersonalRecommendationScorer
    // performs — this helper never re-derives blocking logic, it only reads matchedGroups semantics
    // in parallel to classify positive/negative/adult evidence for the split counters.
    internal data class SourceEvaluationCandidateEvidence(
        val blocked: Boolean,
        val score: Double,
        val hasMetadata: Boolean,
        val hasExplicitPreferredGroup: Boolean,
        val hasLearnedPositiveGroup: Boolean,
        val hasNegativeGroup: Boolean,
        val hasAdultSignal: Boolean,
        val matchedGroups: Set<String>,
    )

    private fun evidenceFor(
        candidate: Manga,
        tasteProfile: TasteProfile,
        aliasMap: Map<String, String>,
    ): SourceEvaluationCandidateEvidence {
        val genres = candidate.genre.orEmpty()
        val groups = genres.map { genre ->
            val normalized = genre.normalizeTag()
            aliasMap[normalized] ?: normalized
        }.toSet()

        val scored = PersonalRecommendationScorer.score(candidate, tasteProfile, aliasMap)

        val hasExplicitPreferredGroup = groups.any {
            tasteProfile.explicitTagPreferences[it] == TagPreference.PREFER.value
        }
        val hasLearnedPositiveGroup = groups.any { (tasteProfile.learnedTagWeights[it] ?: 0.0) > 0.0 }
        val hasNegativeGroup = groups.any {
            tasteProfile.explicitTagPreferences[it] == TagPreference.DISLIKE.value ||
                tasteProfile.explicitTagPreferences[it] == TagPreference.BLOCK.value ||
                (tasteProfile.learnedTagWeights[it] ?: 0.0) < 0.0
        }

        val titleLower = candidate.title.lowercase()
        val tagsLower = genres.map { it.lowercase() }
        val hasAdultSignal = groups.any { it in ADULT_RISK_GROUPS } ||
            ADULT_RISK_TERMS.any { term -> titleLower.contains(term) || tagsLower.any { it.contains(term) } }

        return SourceEvaluationCandidateEvidence(
            blocked = scored.blocked,
            score = scored.score,
            hasMetadata = genres.isNotEmpty(),
            hasExplicitPreferredGroup = hasExplicitPreferredGroup,
            hasLearnedPositiveGroup = hasLearnedPositiveGroup,
            hasNegativeGroup = hasNegativeGroup,
            hasAdultSignal = hasAdultSignal,
            matchedGroups = groups,
        )
    }
    // KMK <--

    /**
     * @param catalogueSamples Bounded-enriched [Manga] built from Popular + Latest results (as of
     * v0.7.47, list entries missing genre metadata are enriched via `getMangaDetails()` before
     * reaching this scorer — see [SourceEvaluationCatalogueEnricher]). Each item's own `genre` list
     * drives per-item taste matching via [PersonalRecommendationScorer].
     * @param aliasMap Tag alias map, threaded through to [PersonalRecommendationScorer] so catalogue
     * tags resolve to the same taste groups a For You candidate's tags would.
     * @param detailEnrichmentAttemptCount How many candidates were sent to `getMangaDetails()`.
     * @param detailEnrichmentSuccessCount How many detail calls returned successfully.
     */
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
        catalogueSamples: List<Manga>,
        popularCount: Int,
        latestCount: Int,
        errorCount: Int,
        tasteProfile: TasteProfile,
        aliasMap: Map<String, String> = emptyMap(),
        evaluatedAt: Long = System.currentTimeMillis(),
        // KMK --> v0.7.47
        detailEnrichmentAttemptCount: Int = 0,
        detailEnrichmentSuccessCount: Int = 0,
        // KMK <--
        // KMK --> v0.7.4: extension version at evaluation time
        extensionVersionName: String? = null,
        extensionVersionCode: Long? = null,
        extensionApkName: String? = null,
        // KMK <--
    ): SourceEvaluation {
        val sampledTitles = catalogueSamples.map { it.title }
        val sampledTags = catalogueSamples.flatMap { it.genre.orEmpty() }
        val allTitlesLower = sampledTitles.map { it.lowercase() }
        val allTagsLower = sampledTags.map { it.lowercase() }

        // Explicit signal counting (signal-level, drives EXPLICIT_HEAVY only)
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

        // KMK --> v0.7.47: split candidate-level evidence — see SourceEvaluationCandidateEvidence.
        val evidence = catalogueSamples.map { evidenceFor(it, tasteProfile, aliasMap) }

        val metadataCandidateCount = evidence.count { it.hasMetadata }
        val positiveCandidateCount = evidence.count {
            !it.blocked && it.score > 0.0 && (it.hasExplicitPreferredGroup || it.hasLearnedPositiveGroup)
        }
        val negativeCandidateCount = evidence.count { it.hasNegativeGroup }
        val blockedCandidateCount = evidence.count { it.blocked }
        val explicitPreferredGroupHitCount = evidence.count { it.hasExplicitPreferredGroup }
        val learnedPositiveGroupHitCount = evidence.count { it.hasLearnedPositiveGroup }
        val adultSignalCandidateCount = evidence.count { it.hasAdultSignal }

        // Backward-compatible mirrors (see SourceEvaluation KDoc / migration plan).
        val preferredTagMatchCount = positiveCandidateCount
        val blockedTagMatchCount = blockedCandidateCount
        // KMK <--

        // Title match — loved/liked manga title overlap
        // Not implemented: full taste title list is not passed to this scorer. Retained as an
        // always-0 field for backward-compatible reads of historical rows (see SourceEvaluation KDoc).
        val likedTitleMatchCount = 0

        val sampleCount = catalogueSamples.size

        // catalogue_metadata_confidence: how much of the *final* (post-enrichment) sampled catalogue
        // evidence had usable tags, tracked independently of the fit score.
        val metadataConfidence = when {
            sampleCount == 0 -> SourceEvaluationMetadataConfidence.UNKNOWN
            metadataCandidateCount.toDouble() / sampleCount >= 0.7 -> SourceEvaluationMetadataConfidence.HIGH
            metadataCandidateCount.toDouble() / sampleCount >= 0.3 -> SourceEvaluationMetadataConfidence.MODERATE
            metadataCandidateCount > 0 -> SourceEvaluationMetadataConfidence.LOW
            else -> SourceEvaluationMetadataConfidence.UNKNOWN
        }

        // quality_score: ratio of catalogue samples collected vs. expected minimum
        val qualityScore = when {
            sampleCount >= 15 -> 0.9
            sampleCount >= 8 -> 0.7
            sampleCount >= 3 -> 0.5
            sampleCount >= 1 -> 0.3
            else -> 0.0
        }

        // KMK --> v0.7.42: no longer populated — search is measured separately by
        // SourceRecommendationFitProbe. Retained as always-0/neutral for schema compatibility.
        val searchCount = 0
        val searchSuccessCount = 0
        val searchReliabilityScore = 0.0
        // KMK <--

        // KMK --> v0.7.47: revised fit-score formula — ratio-based, guarded against zero
        // denominators, penalized by blocked/negative/adult ratios and low metadata confidence.
        val metadataRatio = if (sampleCount > 0) metadataCandidateCount.toDouble() / sampleCount else 0.0
        val positiveRatio = if (metadataCandidateCount > 0) positiveCandidateCount.toDouble() / metadataCandidateCount else 0.0
        val negativeRatio = if (metadataCandidateCount > 0) negativeCandidateCount.toDouble() / metadataCandidateCount else 0.0
        val blockedRatio = if (metadataCandidateCount > 0) blockedCandidateCount.toDouble() / metadataCandidateCount else 0.0
        val adultRatio = if (metadataCandidateCount > 0) adultSignalCandidateCount.toDouble() / metadataCandidateCount else 0.0

        val fitBase = when {
            positiveCandidateCount >= 8 || positiveRatio >= 0.35 -> 0.85
            positiveCandidateCount >= 4 || positiveRatio >= 0.20 -> 0.70
            positiveCandidateCount >= 2 || positiveRatio >= 0.10 -> 0.55
            else -> 0.35
        }
        var fitScore = fitBase
        fitScore -= when {
            blockedRatio >= 0.25 -> 0.35
            blockedRatio >= 0.10 -> 0.20
            else -> 0.0
        }
        fitScore -= when {
            negativeRatio >= 0.40 -> 0.25
            negativeRatio >= 0.25 -> 0.15
            else -> 0.0
        }
        fitScore -= when {
            adultRatio >= 0.35 -> 0.25
            adultRatio >= 0.20 -> 0.15
            else -> 0.0
        }
        if (metadataConfidence == SourceEvaluationMetadataConfidence.LOW) fitScore -= 0.10
        val recommendationFitScore = fitScore.coerceIn(0.0, 1.0)
        // KMK <--

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

        // KMK --> v0.7.47: revised verdict order — metadata-sparse evidence routes to
        // NEEDS_MANUAL_REVIEW instead of a confident WEAK; STRONG_FIT/WORTH_TRYING now require
        // low blocked/adult risk in addition to a high enough fit score, so noisy/adult sources
        // cannot reach a strong verdict purely from broad positive tags. See the tag-enrichment
        // and scoring fix plan for the exact rule order.
        val verdict = when {
            errorCount > 0 && sampleCount == 0 -> SourceEvaluationVerdict.ERROR
            explicitScore >= 0.5 || (adultRatio >= 0.5 && adultSignalCandidateCount >= 3) ->
                SourceEvaluationVerdict.EXPLICIT_HEAVY
            ecchiScore >= 0.5 && explicitScore < 0.3 -> SourceEvaluationVerdict.ECCHI_HEAVY
            sampleCount > 0 && metadataConfidence == SourceEvaluationMetadataConfidence.UNKNOWN ->
                SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW
            metadataConfidence == SourceEvaluationMetadataConfidence.LOW && positiveCandidateCount > 0 ->
                SourceEvaluationVerdict.NEEDS_MANUAL_REVIEW
            (
                metadataConfidence == SourceEvaluationMetadataConfidence.HIGH ||
                    metadataConfidence == SourceEvaluationMetadataConfidence.MODERATE
                ) &&
                recommendationFitScore >= 0.70 && blockedRatio < 0.10 && adultRatio < 0.20 ->
                SourceEvaluationVerdict.STRONG_FIT
            metadataConfidence != SourceEvaluationMetadataConfidence.UNKNOWN &&
                recommendationFitScore >= 0.50 && blockedRatio < 0.25 && adultRatio < 0.35 ->
                SourceEvaluationVerdict.WORTH_TRYING
            else -> SourceEvaluationVerdict.WEAK
        }
        // KMK <--

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
            // KMK --> v0.7.42
            catalogueMetadataConfidence = metadataConfidence,
            // KMK <--
            sampledTitlesJson = if (sampledTitles.isEmpty()) null else sampledTitles.take(20).joinToString("|"),
            sampledTagsJson = if (sampledTags.isEmpty()) null else sampledTags.distinct().take(30).joinToString("|"),
            errorMessage = null,
            // KMK --> v0.7.4: extension version at evaluation time
            extensionVersionName = extensionVersionName,
            extensionVersionCode = extensionVersionCode,
            extensionApkName = extensionApkName,
            // KMK <--
            // KMK --> v0.7.47: enrichment + split evidence counters
            detailEnrichmentAttemptCount = detailEnrichmentAttemptCount,
            detailEnrichmentSuccessCount = detailEnrichmentSuccessCount,
            metadataCandidateCount = metadataCandidateCount,
            positiveCandidateCount = positiveCandidateCount,
            negativeCandidateCount = negativeCandidateCount,
            explicitPreferredGroupHitCount = explicitPreferredGroupHitCount,
            learnedPositiveGroupHitCount = learnedPositiveGroupHitCount,
            blockedCandidateCount = blockedCandidateCount,
            adultSignalCandidateCount = adultSignalCandidateCount,
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
