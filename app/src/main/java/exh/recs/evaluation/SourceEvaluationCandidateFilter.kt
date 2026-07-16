package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import exh.recs.evaluation.SourceEvaluationUpdatePolicy
import exh.source.ExplicitSourceClassifier
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationKeys

// KMK -->
object SourceEvaluationCandidateFilter {

    /**
     * Pre-filtered pool of candidates before option-based filters are applied.
     * Excludes installed, untrusted, language-mismatched, nsfw (when disabled), disliked, and unsafe extensions.
     * Does NOT apply skipAlreadyEvaluated or includeExplicit — those are option-based and applied in [applyOptions].
     */
    data class CandidatePoolResult(
        val allEligible: List<EvaluationCandidate>,
        val evaluationsByExtensionKey: Map<String, List<SourceEvaluation>>,
        val explicitExtensionKeys: Set<String>,
        val dislikedHiddenCount: Int,
        val blockExplicit: Boolean,
        // KMK --> v0.6.16: crash quarantine
        val unsafeExtensionKeys: Set<String> = emptySet(),
        val unsafeHiddenCount: Int = 0,
        // KMK <--
        // KMK v0.8.1-fix4: separate from dislikedHiddenCount (recommendation-behavior dislike) so
        // the UI can truthfully explain why a candidate is missing.
        val sourceQualityHiddenCount: Int = 0,
    )

    /**
     * Final candidate list after applying option-based filters, with diagnostic counts.
     */
    data class FilterResult(
        val candidates: List<EvaluationCandidate>,
        val evaluatedHiddenCount: Int,
        val explicitHiddenCount: Int,
    )

    /**
     * Build base candidate pool from available extensions.
     * Applies broad eligibility rules but not option-based filters.
     */
    fun buildPool(
        available: List<Extension.Available>,
        installedPkgNames: Set<String>,
        untrustedPkgNames: Set<String>,
        recLanguages: Set<String>,
        nsfwEnabled: Boolean,
        blockExplicit: Boolean,
        dislikedKeys: Set<String>,
        evaluations: List<SourceEvaluation>,
        // KMK --> v0.6.16: crash quarantine — extension-level keys to skip
        unsafeExtensionKeys: Set<String> = emptySet(),
        // KMK <--
        // KMK v0.8.1-fix4: source/library-quality dislike keys — separate axis from dislikedKeys
        qualityDislikedKeys: Set<String> = emptySet(),
    ): CandidatePoolResult {
        var dislikedCount = 0
        // KMK --> v0.6.16: crash quarantine
        var unsafeCount = 0
        val unsafeKeys = mutableSetOf<String>()
        // KMK <--
        var qualityDislikedCount = 0 // KMK v0.8.1-fix4
        val explicitExtKeys = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<EvaluationCandidate>()

        for (ext in available) {
            val extKey = "${ext.signatureHash}|${ext.pkgName}"
            if (!seen.add(extKey)) continue

            if (ext.pkgName in installedPkgNames) continue
            if (ext.pkgName in untrustedPkgNames) continue
            if (ext.lang.lowercase() !in recLanguages) continue
            if (!nsfwEnabled && ext.isNsfw) continue

            val dislikeKey = "a|${ext.signatureHash}|${ext.pkgName}"
            if (dislikeKey in dislikedKeys) {
                dislikedCount++
                continue
            }
            // KMK v0.8.1-fix4: source/library-quality dislike hides regardless of recommendation dislike
            if (dislikeKey in qualityDislikedKeys) {
                qualityDislikedCount++
                continue
            }

            // KMK --> v0.6.16: skip extensions suspected of causing fatal crashes
            if (extKey in unsafeExtensionKeys) {
                unsafeCount++
                unsafeKeys.add(extKey)
                continue
            }
            // KMK <--

            if (ExplicitSourceClassifier.isExplicitExtension(ext)) {
                explicitExtKeys.add(extKey)
            }

            candidates.add(EvaluationCandidate(extension = ext, priorityRank = candidates.size))
        }

        val evalsByExtKey = evaluations.groupBy { it.extensionKey }

        return CandidatePoolResult(
            allEligible = candidates,
            evaluationsByExtensionKey = evalsByExtKey,
            explicitExtensionKeys = explicitExtKeys,
            dislikedHiddenCount = dislikedCount,
            blockExplicit = blockExplicit,
            // KMK --> v0.6.16: crash quarantine
            unsafeExtensionKeys = unsafeKeys,
            unsafeHiddenCount = unsafeCount,
            // KMK <--
            sourceQualityHiddenCount = qualityDislikedCount, // KMK v0.8.1-fix4
        )
    }

    /**
     * Apply option-based filters (skipAlreadyEvaluated, includeExplicit, reEvaluateStale,
     * onlyUpdatedEvaluated) to a pre-built pool.
     * Returns the final candidate list and diagnostic hidden counts.
     */
    fun applyOptions(
        pool: CandidatePoolResult,
        includeExplicit: Boolean,
        skipAlreadyEvaluated: Boolean,
        reEvaluateStale: Boolean,
        now: Long,
        // KMK --> v0.7.4: restrict run to extensions that have been updated since last evaluation
        onlyUpdatedEvaluated: Boolean = false,
        // KMK <--
    ): FilterResult {
        val shouldHideExplicit = pool.blockExplicit && !includeExplicit
        var explicitHiddenCount = 0
        var evaluatedHiddenCount = 0

        val visible = pool.allEligible.filter { c ->
            val extKey = "${c.extension.signatureHash}|${c.extension.pkgName}"
            val isExplicit = extKey in pool.explicitExtensionKeys

            if (shouldHideExplicit && isExplicit) {
                explicitHiddenCount++
                return@filter false
            }

            if (shouldSkip(extKey, pool.evaluationsByExtensionKey, skipAlreadyEvaluated, reEvaluateStale, now)) {
                evaluatedHiddenCount++
                return@filter false
            }

            // KMK --> v0.7.4: only include extensions that were evaluated AND have since been updated
            if (onlyUpdatedEvaluated) {
                val evals = pool.evaluationsByExtensionKey[extKey]
                if (evals.isNullOrEmpty()) return@filter false
                val snapshots = evals.map { SourceEvaluationUpdatePolicy.fromEvaluation(it) }
                val available = SourceEvaluationUpdatePolicy.AvailableExtensionSnapshot(
                    versionCode = c.extension.versionCode,
                    signatureHash = c.extension.signatureHash,
                    pkgName = c.extension.pkgName,
                )
                if (SourceEvaluationUpdatePolicy.detectForPool(snapshots, available) !=
                    SourceEvaluationUpdatePolicy.UpdateStatus.UPDATED
                ) {
                    return@filter false
                }
            }
            // KMK <--

            true
        }

        return FilterResult(
            candidates = visible,
            evaluatedHiddenCount = evaluatedHiddenCount,
            explicitHiddenCount = explicitHiddenCount,
        )
    }

    /** Returns true if all evaluations for an extension are stale (expired or version-outdated). */
    fun isStale(evaluations: List<SourceEvaluation>, now: Long): Boolean {
        if (evaluations.isEmpty()) return false
        return evaluations.all { eval ->
            val expiresAt = eval.expiresAt
            (expiresAt != null && expiresAt <= now) ||
                eval.evaluationVersion < SourceEvaluationKeys.CURRENT_VERSION
        }
    }

    /** Returns true if this extension-key should be skipped based on evaluation history and options. */
    fun shouldSkip(
        extKey: String,
        evaluationsByExtKey: Map<String, List<SourceEvaluation>>,
        skipAlreadyEvaluated: Boolean,
        reEvaluateStale: Boolean,
        now: Long,
    ): Boolean {
        if (!skipAlreadyEvaluated) return false
        val evals = evaluationsByExtKey[extKey] ?: return false
        if (evals.isEmpty()) return false
        if (reEvaluateStale && isStale(evals, now)) return false
        return true
    }
}
// KMK <--
