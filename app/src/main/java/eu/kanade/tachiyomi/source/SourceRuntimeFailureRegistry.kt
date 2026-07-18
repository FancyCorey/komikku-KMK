package eu.kanade.tachiyomi.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// KMK v0.8.10-fix3 -->
/**
 * In-memory, process-lifetime record of [SourceRuntime] failures, keyed by source id.
 *
 * This is deliberately **not** the Source Evaluation crash-quarantine mechanism
 * ([exh.recs.evaluation.SourceEvaluationSafetyRepository] / `KnownUnsafeExtensionPackages`). That
 * mechanism is purpose-built and persisted for a different, narrower problem: a *native* crash
 * (SIGSEGV) detected during Source Evaluation's own deliberate probing, and it gates whether Source
 * Evaluation will attempt to probe that specific extension again. Reusing it here for general
 * Browse/reader/library-update runtime failures would conflate two structurally different failure
 * classes and have an unrequested side effect (quarantining the source from Source Evaluation too).
 * See the v0.8.10-fix3 implementation report for the full reasoning.
 *
 * This registry instead exists purely to avoid hammering a source that just failed within the same
 * process lifetime — it never disables or uninstalls an extension, and it is cleared automatically
 * (no persistence across process restarts) and manually via [clear]/[clearAll].
 */
object SourceRuntimeFailureRegistry {

    data class Entry(
        val sourceId: Long,
        val sourceName: String,
        val sourceLang: String,
        val operation: SourceRuntimeOperation,
        val kind: SourceRuntimeFailureKind,
        val firstFailureAt: Long,
        val lastFailureAt: Long,
        val count: Int,
    )

    /** Minimum time between two failures of the *same* source before it is no longer suppressed. */
    const val SUPPRESSION_WINDOW_MS = 60_000L

    private val entries = MutableStateFlow<Map<Long, Entry>>(emptyMap())

    /** Read-only, reactive view of every currently-recorded failure, for diagnostics/UI. */
    val failures = entries.map { it.values.toList() }

    /** Non-reactive snapshot, for call sites that just want a one-off read. */
    fun snapshot(): List<Entry> = entries.value.values.toList()

    fun record(failure: SourceRuntimeFailure) {
        val now = System.currentTimeMillis()
        entries.update { current ->
            val existing = current[failure.sourceId]
            val updated = Entry(
                sourceId = failure.sourceId,
                sourceName = failure.sourceName,
                sourceLang = failure.sourceLang,
                operation = failure.operation,
                kind = failure.kind,
                firstFailureAt = existing?.firstFailureAt ?: now,
                lastFailureAt = now,
                count = (existing?.count ?: 0) + 1,
            )
            current + (failure.sourceId to updated)
        }
    }

    fun get(sourceId: Long): Entry? = entries.value[sourceId]

    /**
     * True when [sourceId] failed recently enough (within [SUPPRESSION_WINDOW_MS]) that a caller
     * may choose to skip attempting it again this cycle, rather than re-invoking a source that is
     * very likely to fail again the same way. This is advisory only — [SourceRuntime.run] always
     * still attempts the call; callers that want to skip proactively (e.g. a batch loop deciding
     * whether to bother trying a source at all) can check this first.
     */
    fun isTemporarilyUnavailable(sourceId: Long): Boolean {
        val entry = entries.value[sourceId] ?: return false
        return System.currentTimeMillis() - entry.lastFailureAt < SUPPRESSION_WINDOW_MS
    }

    fun clear(sourceId: Long) {
        entries.update { it - sourceId }
    }

    fun clearAll() {
        entries.update { emptyMap() }
    }
}
// KMK <--
