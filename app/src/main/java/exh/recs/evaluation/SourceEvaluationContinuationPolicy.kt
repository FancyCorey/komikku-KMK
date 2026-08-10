package exh.recs.evaluation

// KMK -->
/**
 * Persisted cursor tracking which candidates have been processed in the current evaluation queue.
 *
 * The cursor is keyed by [filterFingerprint] so that changing evaluation options (language,
 * explicit inclusion, skip-evaluated, etc.) automatically invalidates the saved position.
 * Changing only batch size does NOT invalidate the cursor; it just changes how many candidates
 * the next slice picks up.
 *
 * Candidate keys are extensionKey values: "${signatureHash}|${pkgName}".
 */
data class SourceEvaluationCursor(
    val filterFingerprint: String,
    val lastCandidateKey: String?,
    val completedCandidateKeys: Set<String>,
    val updatedAt: Long,
)

object SourceEvaluationContinuationPolicy {

    /** Cursors older than 7 days are considered stale and are reset. */
    private const val CURSOR_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000

    // Tab separates completed-key list items; newline separates the four cursor fields.
    // Neither appears in signature hashes (hex), pkg names (java identifiers), or fingerprints.
    private const val KEY_SEP = "\t"
    private const val FIELD_SEP = "\n"

    /**
     * Build the filter fingerprint for the current option set.
     * Batch size is intentionally NOT included: going from 10 to 25 should
     * continue the same queue with a larger slice.
     */
    fun buildFilterFingerprint(
        languages: Set<String>,
        includeExplicit: Boolean,
        skipAlreadyEvaluated: Boolean,
        reEvaluateStale: Boolean,
        onlyUpdatedEvaluated: Boolean,
        blockExplicit: Boolean,
    ): String = buildString {
        append("lang=")
        append(languages.sorted().joinToString(","))
        append("|incEx=").append(includeExplicit)
        append("|skip=").append(skipAlreadyEvaluated)
        append("|stale=").append(reEvaluateStale)
        append("|upd=").append(onlyUpdatedEvaluated)
        append("|blkEx=").append(blockExplicit)
    }

    /**
     * Return the slice of [candidates] that should be run next.
     *
     * If [cursor] is null or its fingerprint does not match [currentFingerprint], returns
     * the leading [batchSize] slice (fresh start).
     *
     * If the cursor is valid, finds the first candidate after the last completed one
     * (skipping all [SourceEvaluationCursor.completedCandidateKeys]) and returns up to
     * [batchSize] from there.
     */
    fun sliceForRun(
        candidates: List<EvaluationCandidate>,
        batchSize: Int,
        cursor: SourceEvaluationCursor?,
        currentFingerprint: String,
        now: Long = System.currentTimeMillis(),
    ): List<EvaluationCandidate> {
        if (cursor == null ||
            cursor.filterFingerprint != currentFingerprint ||
            cursor.updatedAt + CURSOR_EXPIRY_MS < now
        ) {
            return candidates.take(batchSize)
        }

        val lastKey = cursor.lastCandidateKey
        val startIndex = if (lastKey != null) {
            val idx = candidates.indexOfFirst { candidateKey(it) == lastKey }
            if (idx < 0) 0 else idx + 1
        } else {
            0
        }

        // From startIndex, skip any already in completedCandidateKeys (handles interleaved reruns)
        val remaining = candidates.drop(startIndex).filter { candidateKey(it) !in cursor.completedCandidateKeys }
        return remaining.take(batchSize)
    }

    /**
     * Advance the cursor after a run completes.
     * [completedKeys] are the extension keys that were handed to the runner in this run.
     * [allCandidates] is the full filtered pool (used to find the last candidate key).
     */
    fun advanceCursor(
        current: SourceEvaluationCursor?,
        completedKeys: Set<String>,
        allCandidates: List<EvaluationCandidate>,
        currentFingerprint: String,
        now: Long = System.currentTimeMillis(),
    ): SourceEvaluationCursor {
        val priorCompleted = if (current?.filterFingerprint == currentFingerprint) {
            current.completedCandidateKeys
        } else {
            emptySet()
        }
        val allCompleted = priorCompleted + completedKeys

        val lastKey = allCandidates.lastOrNull { candidateKey(it) in completedKeys }?.let { candidateKey(it) }
            ?: current?.lastCandidateKey

        return SourceEvaluationCursor(
            filterFingerprint = currentFingerprint,
            lastCandidateKey = lastKey,
            completedCandidateKeys = allCompleted,
            updatedAt = now,
        )
    }

    /**
     * How many candidates remain after the current cursor position.
     * Returns the full candidate count when there is no valid cursor.
     */
    fun remainingCount(
        candidates: List<EvaluationCandidate>,
        cursor: SourceEvaluationCursor?,
        currentFingerprint: String,
        now: Long = System.currentTimeMillis(),
    ): Int {
        if (cursor == null ||
            cursor.filterFingerprint != currentFingerprint ||
            cursor.updatedAt + CURSOR_EXPIRY_MS < now
        ) {
            return candidates.size
        }
        val lastKey = cursor.lastCandidateKey ?: return candidates.size
        val idx = candidates.indexOfFirst { candidateKey(it) == lastKey }
        if (idx < 0) return candidates.size
        val remaining = candidates.drop(idx + 1).filter { candidateKey(it) !in cursor.completedCandidateKeys }
        return remaining.size
    }

    /** Whether a "Continue next batch" action is available. */
    fun canContinue(
        candidates: List<EvaluationCandidate>,
        cursor: SourceEvaluationCursor?,
        currentFingerprint: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (cursor == null || cursor.filterFingerprint != currentFingerprint) return false
        if (cursor.updatedAt + CURSOR_EXPIRY_MS < now) return false
        return remainingCount(candidates, cursor, currentFingerprint, now) > 0
    }

    /** Serialize a cursor to a compact string for storage in SharedPreferences. */
    fun serialize(cursor: SourceEvaluationCursor): String = buildString {
        append(cursor.filterFingerprint)
        append(FIELD_SEP)
        append(cursor.lastCandidateKey ?: "")
        append(FIELD_SEP)
        append(cursor.completedCandidateKeys.joinToString(KEY_SEP))
        append(FIELD_SEP)
        append(cursor.updatedAt)
    }

    /** Deserialize a cursor from a stored string. Returns null on any parse error. */
    fun deserialize(value: String): SourceEvaluationCursor? = try {
        if (value.isBlank()) return null
        val parts = value.split(FIELD_SEP, limit = 4)
        if (parts.size < 4) return null
        val fingerprint = parts[0]
        if (fingerprint.isBlank()) return null
        val lastKey = parts[1].takeIf { it.isNotEmpty() }
        val completedRaw = parts[2]
        val completed = if (completedRaw.isBlank()) {
            emptySet()
        } else {
            completedRaw.split(KEY_SEP).filter { it.isNotBlank() }.toSet()
        }
        val updatedAt = parts[3].toLongOrNull() ?: return null
        SourceEvaluationCursor(
            filterFingerprint = fingerprint,
            lastCandidateKey = lastKey,
            completedCandidateKeys = completed,
            updatedAt = updatedAt,
        )
    } catch (_: Exception) {
        null
    }

    internal fun candidateKey(c: EvaluationCandidate): String =
        "${c.extension.signatureHash}|${c.extension.pkgName}"
}
// KMK <--
