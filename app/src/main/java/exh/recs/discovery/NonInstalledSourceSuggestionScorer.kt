package exh.recs.discovery

import eu.kanade.tachiyomi.extension.model.Extension
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import exh.source.ExplicitSourceClassifier
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK -->
/**
 * Pure stateless scorer for non-installed extension suggestions.
 * No Android dependencies — fully unit-testable.
 *
 * Language match, repo match, base URL, and generic content keywords are eligibility
 * filters only — they are not evidence and do not contribute to score. A source is
 * included only when it has meaningful positive evidence: conservative similarity to
 * an already-installed source name, OR an explicit user like.
 *
 * Scores are capped at 0.69. Scores of 0.70+ are reserved for installed-source fit
 * learning that has real post-install search data.
 */
object NonInstalledSourceSuggestionScorer {

    private const val SCORE_EXACT_SOURCE_NAME = 0.60
    private const val SCORE_SIMILAR_SOURCE_NAME = 0.50
    private const val SCORE_USER_LIKED = 0.68
    private const val SCORE_CAP = 0.69
    private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55

    private val GENERIC_NORMALIZED = setOf(
        "manga",
        "scans",
        "scan",
        "manhwa",
        "webtoon",
        "comics",
        "comic",
        "source",
    )
    private val GENERIC_TOKENS = setOf(
        "manga",
        "scans",
        "scan",
        "manhwa",
        "webtoon",
        "comics",
        "comic",
        "source",
    )

    internal fun scoreAndFilter(
        available: List<Extension.Available>,
        installedHints: List<InstalledExtensionHints>,
        untrusted: List<Extension.Untrusted>,
        recLanguages: Set<String>,
        nsfwEnabled: Boolean,
        // KMK -->
        blockExplicit: Boolean = false,
        // KMK <--
        dismissed: Set<String>,
        likedKeys: Set<String> = emptySet(),
        dislikedKeys: Set<String> = emptySet(),
        // KMK -->
        /** Map of evaluation_key → verdict for sources already evaluated via source evaluation. */
        evaluations: Map<String, SourceEvaluationVerdict> = emptyMap(),
        // KMK <--
        // KMK v0.8.1-fix4: source/library-quality dislike axis -- separate from dislikedKeys
        // (recommendation-behavior dislike). Hides poor/too-explicit-marked sources regardless of
        // the global explicit filter, since this is an explicit per-source user judgement.
        qualityDislikedKeys: Set<String> = emptySet(),
    ): List<NonInstalledSourceSuggestion> {
        val normalizedLangs = recLanguages.map { it.lowercase() }.toSet()
        val installedKeys = installedHints.map { it.signatureHash + "|" + it.pkgName }.toSet()
        val untrustedKeys = untrusted.map { it.signatureHash + "|" + it.pkgName }.toSet()
        val installedSourceNames = installedHints.flatMap { it.sourceNames }.filter { it.isNotBlank() }

        val suggestions = mutableListOf<NonInstalledSourceSuggestion>()

        for (ext in available) {
            val extKey = ext.signatureHash + "|" + ext.pkgName
            if (extKey in installedKeys) continue
            if (extKey in untrustedKeys) continue
            if (!nsfwEnabled && ext.isNsfw) continue
            // KMK -->
            if (blockExplicit && ExplicitSourceClassifier.isExplicitExtension(ext)) continue
            // KMK <--

            if (ext.sources.isEmpty()) {
                if (ext.lang.lowercase() !in normalizedLangs) continue
                val dismissalKey = buildDismissalKey(ext.signatureHash, ext.pkgName, null)
                if (dismissalKey in dismissed) continue
                val candKey = RecommendationSourcePreferenceStore.availableKey(ext.signatureHash, ext.pkgName, null)
                if (candKey in dislikedKeys) continue
                if (candKey in qualityDislikedKeys) continue // KMK v0.8.1-fix4
                // KMK -->
                val evalVerdict = evaluations[dismissalKey]
                if (evalVerdict == SourceEvaluationVerdict.REJECTED) continue
                if (blockExplicit && evalVerdict == SourceEvaluationVerdict.EXPLICIT_HEAVY) continue
                // KMK <--
                score(ext, null, ext.name, installedSourceNames, candKey, likedKeys, evalVerdict)?.let { suggestions += it }
            } else {
                for (src in ext.sources) {
                    if (src.lang.lowercase() !in normalizedLangs) continue
                    val dismissalKey = buildDismissalKey(ext.signatureHash, ext.pkgName, src.id)
                    if (dismissalKey in dismissed) continue
                    val candKey = RecommendationSourcePreferenceStore.availableKey(ext.signatureHash, ext.pkgName, src.id)
                    if (candKey in dislikedKeys) continue
                    if (candKey in qualityDislikedKeys) continue // KMK v0.8.1-fix4
                    // KMK -->
                    val evalVerdict = evaluations[dismissalKey]
                    if (evalVerdict == SourceEvaluationVerdict.REJECTED) continue
                    if (blockExplicit && evalVerdict == SourceEvaluationVerdict.EXPLICIT_HEAVY) continue
                    // KMK <--
                    score(ext, src, src.name, installedSourceNames, candKey, likedKeys, evalVerdict)?.let { suggestions += it }
                }
            }
        }

        return suggestions.sortedWith(
            compareByDescending<NonInstalledSourceSuggestion> { it.score }
                .thenBy { it.displayName.lowercase() },
        )
    }

    /**
     * Returns null when there is no meaningful evidence to recommend this source.
     * User-liked sources bypass the evidence gate and receive SCORE_USER_LIKED.
     * Evaluated sources bypass the evidence gate and get scores above SCORE_CAP (0.70+).
     */
    private fun score(
        ext: Extension.Available,
        src: Extension.Available.Source?,
        name: String,
        installedSourceNames: List<String>,
        candidateKey: String,
        likedKeys: Set<String>,
        // KMK -->
        evalVerdict: SourceEvaluationVerdict? = null,
        // KMK <--
    ): NonInstalledSourceSuggestion? {
        // KMK -->
        if (evalVerdict != null) {
            return when (evalVerdict) {
                SourceEvaluationVerdict.STRONG_FIT -> {
                    val reasons = listOf(NonInstalledSuggestionReason.EvaluatedStrongFit)
                    NonInstalledSourceSuggestion(extension = ext, source = src, score = 0.90, confidence = SuggestionConfidence.MEDIUM, reasons = reasons)
                }
                SourceEvaluationVerdict.WORTH_TRYING -> {
                    val reasons = listOf(NonInstalledSuggestionReason.EvaluatedWorthTrying)
                    NonInstalledSourceSuggestion(extension = ext, source = src, score = 0.75, confidence = SuggestionConfidence.MEDIUM, reasons = reasons)
                }
                SourceEvaluationVerdict.EXPLICIT_HEAVY -> {
                    val reasons = listOf(NonInstalledSuggestionReason.EvaluatedExplicitHeavy)
                    NonInstalledSourceSuggestion(extension = ext, source = src, score = 0.10, confidence = SuggestionConfidence.LOW, reasons = reasons)
                }
                SourceEvaluationVerdict.ECCHI_HEAVY -> {
                    val reasons = listOf(NonInstalledSuggestionReason.EvaluatedEcchiHeavy)
                    NonInstalledSourceSuggestion(extension = ext, source = src, score = 0.30, confidence = SuggestionConfidence.LOW, reasons = reasons)
                }
                SourceEvaluationVerdict.WEAK, SourceEvaluationVerdict.POOR_SEARCH, SourceEvaluationVerdict.NEUTRAL -> {
                    // Evaluated but not recommended — still show if we have metadata evidence
                    scoreFromMetadata(ext, src, name, installedSourceNames, candidateKey, likedKeys)
                }
                else -> scoreFromMetadata(ext, src, name, installedSourceNames, candidateKey, likedKeys)
            }
        }
        // KMK <--
        return scoreFromMetadata(ext, src, name, installedSourceNames, candidateKey, likedKeys)
    }

    private fun scoreFromMetadata(
        ext: Extension.Available,
        src: Extension.Available.Source?,
        name: String,
        installedSourceNames: List<String>,
        candidateKey: String,
        likedKeys: Set<String>,
    ): NonInstalledSourceSuggestion? {
        if (candidateKey in likedKeys) {
            val reasons = listOf(NonInstalledSuggestionReason.UserLikedSource, NonInstalledSuggestionReason.NeedsTesting)
            val s = minOf(SCORE_USER_LIKED, SCORE_CAP)
            return NonInstalledSourceSuggestion(extension = ext, source = src, score = s, confidence = SuggestionConfidence.MEDIUM, reasons = reasons)
        }

        val reasons = mutableListOf<NonInstalledSuggestionReason>()
        val (matchedName, isExact) = findSimilarInstalledSource(name, installedSourceNames)
        if (matchedName != null) {
            reasons += NonInstalledSuggestionReason.SimilarToInstalledSource(matchedName)
        }

        if (!hasMeaningfulEvidence(reasons)) return null

        reasons += NonInstalledSuggestionReason.NeedsTesting

        val rawScore = if (isExact) SCORE_EXACT_SOURCE_NAME else SCORE_SIMILAR_SOURCE_NAME
        val s = minOf(rawScore, SCORE_CAP)
        val confidence = if (s >= MEDIUM_CONFIDENCE_THRESHOLD) SuggestionConfidence.MEDIUM else SuggestionConfidence.LOW
        return NonInstalledSourceSuggestion(extension = ext, source = src, score = s, confidence = confidence, reasons = reasons)
    }

    private fun hasMeaningfulEvidence(reasons: List<NonInstalledSuggestionReason>): Boolean =
        reasons.any { it is NonInstalledSuggestionReason.SimilarToInstalledSource }

    /**
     * Returns (matchedInstalledName, isExact). First element is null when no match is found.
     *
     * Similarity rules:
     * 1. Exact normalized name match.
     * 2. Containment where both normalized names are at least 8 characters.
     * 3. Token overlap: at least one shared distinctive token of length >= 5 that is not a generic word.
     *
     * Generic single-word names such as "Manga", "Scans", "Comics" are excluded from matching
     * on both sides so they cannot act as false anchors.
     */
    internal fun findSimilarInstalledSource(candidateName: String, installedNames: List<String>): Pair<String?, Boolean> {
        val normalizedCandidate = normalizeSourceName(candidateName)
        if (normalizedCandidate.length < 4 || normalizedCandidate in GENERIC_NORMALIZED) return null to false
        val candidateTokens = distinctiveTokens(candidateName)

        for (installedName in installedNames) {
            val normalizedInstalled = normalizeSourceName(installedName)
            if (normalizedInstalled.length < 4 || normalizedInstalled in GENERIC_NORMALIZED) continue

            if (normalizedCandidate == normalizedInstalled) return installedName to true

            if (normalizedCandidate.length >= 8 && normalizedInstalled.length >= 8 &&
                (normalizedCandidate.contains(normalizedInstalled) || normalizedInstalled.contains(normalizedCandidate))
            ) {
                return installedName to false
            }

            if (candidateTokens.isNotEmpty()) {
                val installedTokens = distinctiveTokens(installedName)
                if (installedTokens.isNotEmpty() && (candidateTokens intersect installedTokens).isNotEmpty()) {
                    return installedName to false
                }
            }
        }

        return null to false
    }

    internal fun normalizeSourceName(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]"), "")

    /**
     * Tokens from [name] that are distinctive: length >= 5 and not a generic content word.
     * Uses the original (non-normalized) name so word boundaries are preserved.
     */
    internal fun distinctiveTokens(name: String): Set<String> =
        name.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 5 && it !in GENERIC_TOKENS }
            .toSet()

    /** Kept for compatibility with existing tests. */
    internal fun sourceKeywordTokens(name: String, baseUrl: String): Set<String> =
        "$name $baseUrl".lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()
}
// KMK <--
