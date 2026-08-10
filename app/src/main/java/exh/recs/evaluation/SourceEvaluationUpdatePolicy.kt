package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluation

// KMK -->
/**
 * Pure stateless helper for detecting whether a non-installed extension has been updated
 * since it was last evaluated.
 *
 * Uses `extensionVersionCode` stored in the evaluation record (added in v0.7.4).
 * Records written before v0.7.4 have null version fields → UPDATE_UNKNOWN.
 */
object SourceEvaluationUpdatePolicy {

    enum class UpdateStatus {
        /** Extension versionCode is higher than the stored evaluation versionCode. */
        UPDATED,
        /** Extension versionCode matches the stored evaluation versionCode. */
        NOT_UPDATED,
        /** Stored evaluation has no versionCode metadata (evaluated before v0.7.4). */
        UPDATE_UNKNOWN,
        /** No evaluation record exists for this extension. */
        NEVER_EVALUATED,
    }

    data class EvaluationVersionSnapshot(
        val extensionVersionCode: Long?,
        val signatureHash: String,
        val pkgName: String,
    )

    data class AvailableExtensionSnapshot(
        val versionCode: Long,
        val signatureHash: String,
        val pkgName: String,
    )

    fun fromEvaluation(eval: SourceEvaluation): EvaluationVersionSnapshot =
        EvaluationVersionSnapshot(
            extensionVersionCode = eval.extensionVersionCode,
            signatureHash = eval.signatureHash,
            pkgName = eval.extensionPkgName,
        )

    fun detectUpdateStatus(
        evaluation: EvaluationVersionSnapshot,
        available: AvailableExtensionSnapshot,
    ): UpdateStatus {
        val storedVersionCode = evaluation.extensionVersionCode ?: return UpdateStatus.UPDATE_UNKNOWN
        return if (available.versionCode > storedVersionCode) UpdateStatus.UPDATED else UpdateStatus.NOT_UPDATED
    }

    /**
     * Given all evaluation snapshots for an extension key, determine whether the available
     * version is newer than what was evaluated. Uses the newest (highest versionCode) snapshot.
     */
    fun detectForPool(
        evaluations: List<EvaluationVersionSnapshot>,
        available: AvailableExtensionSnapshot,
    ): UpdateStatus {
        if (evaluations.isEmpty()) return UpdateStatus.NEVER_EVALUATED
        val best = evaluations.maxByOrNull { it.extensionVersionCode ?: Long.MIN_VALUE }
            ?: return UpdateStatus.UPDATE_UNKNOWN
        return detectUpdateStatus(best, available)
    }
}
// KMK <--
