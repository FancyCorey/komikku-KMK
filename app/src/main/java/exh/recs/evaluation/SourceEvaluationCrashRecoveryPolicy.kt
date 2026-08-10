package exh.recs.evaluation

import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker

// KMK -->
object SourceEvaluationCrashRecoveryPolicy {

    private const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours

    sealed interface Decision {
        /** Marker exists and is recent — mark the extension unsafe. */
        data class MarkUnsafe(val marker: SourceEvaluationProbeMarker) : Decision

        /** Marker exists but is stale (> 24h old) — clear without marking unsafe. */
        data class ClearStale(val marker: SourceEvaluationProbeMarker) : Decision

        /** No marker found — nothing to do. */
        object DoNothing : Decision
    }

    fun decide(marker: SourceEvaluationProbeMarker?, now: Long): Decision {
        marker ?: return Decision.DoNothing
        return if (now - marker.updatedAt < STALE_THRESHOLD_MS) {
            Decision.MarkUnsafe(marker)
        } else {
            Decision.ClearStale(marker)
        }
    }
}
// KMK <--
