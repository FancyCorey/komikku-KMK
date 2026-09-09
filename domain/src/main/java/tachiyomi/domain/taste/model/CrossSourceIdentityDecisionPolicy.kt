package tachiyomi.domain.taste.model

object CrossSourceIdentityDecisionPolicy {

    const val CURRENT_DECISION_VERSION = 1
    const val CURRENT_EVIDENCE_VERSION = 1
    const val MAX_URL_LENGTH = 4096
    const val MAX_REASON_CODES = 8
    const val MAX_TRANSFER_ROWS = 10_000
    const val MAX_SYNC_ROWS = MAX_TRANSFER_ROWS * 2
    const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1000L

    fun canonicalPair(first: CrossSourceRecordKey, second: CrossSourceRecordKey): CrossSourceIdentityPair {
        return if (compareKeys(first, second) <= 0) {
            CrossSourceIdentityPair(first, second)
        } else {
            CrossSourceIdentityPair(second, first)
        }
    }

    fun canonicalize(decision: CrossSourceIdentityDecision): CrossSourceIdentityDecision =
        decision.copy(pair = canonicalPair(decision.pair.left, decision.pair.right))

    fun isValid(decision: CrossSourceIdentityDecision, now: Long): Boolean {
        val canonical = canonicalize(decision)
        return canonical == decision &&
            isValidKey(decision.pair.left) &&
            isValidKey(decision.pair.right) &&
            decision.pair.left != decision.pair.right &&
            decision.decisionVersion == CURRENT_DECISION_VERSION &&
            decision.evidenceVersion == CURRENT_EVIDENCE_VERSION &&
            decision.reasonCodes.isNotEmpty() &&
            decision.reasonCodes.size <= MAX_REASON_CODES &&
            decision.createdAt > 0L &&
            decision.updatedAt >= decision.createdAt &&
            decision.updatedAt <= now + MAX_FUTURE_SKEW_MS &&
            (decision.deletedAt == null || decision.deletedAt in decision.createdAt..decision.updatedAt)
    }

    fun isAuthoritativeConfirmation(decision: CrossSourceIdentityDecision?): Boolean =
        decision != null &&
            decision.deletedAt == null &&
            decision.reviewState == CrossSourceIdentityReviewState.CURRENT &&
            decision.decision == CrossSourceIdentityDecisionValue.USER_CONFIRMED

    fun isCurrentRejection(decision: CrossSourceIdentityDecision?): Boolean =
        decision != null &&
            decision.deletedAt == null &&
            decision.reviewState == CrossSourceIdentityReviewState.CURRENT &&
            decision.decision == CrossSourceIdentityDecisionValue.USER_REJECTED

    fun userDecision(
        pair: CrossSourceIdentityPair,
        value: CrossSourceIdentityDecisionValue,
        previous: CrossSourceIdentityDecision?,
        timestamp: Long,
    ): CrossSourceIdentityDecision {
        val updatedAt = nextTimestamp(previous, timestamp)
        return CrossSourceIdentityDecision(
            pair = canonicalPair(pair.left, pair.right),
            decision = value,
            decisionVersion = CURRENT_DECISION_VERSION,
            evidenceVersion = CURRENT_EVIDENCE_VERSION,
            reasonCodes = setOf(
                if (value == CrossSourceIdentityDecisionValue.USER_CONFIRMED) {
                    CrossSourceIdentityReasonCode.USER_CONFIRMATION
                } else {
                    CrossSourceIdentityReasonCode.USER_REJECTION
                },
            ),
            reviewState = CrossSourceIdentityReviewState.CURRENT,
            createdAt = previous?.createdAt ?: updatedAt,
            updatedAt = updatedAt,
            deletedAt = null,
        )
    }

    fun clearedDecision(
        previous: CrossSourceIdentityDecision,
        timestamp: Long,
    ): CrossSourceIdentityDecision = tombstone(previous, nextTimestamp(previous, timestamp))

    fun tombstone(decision: CrossSourceIdentityDecision, timestamp: Long): CrossSourceIdentityDecision {
        require(timestamp >= decision.createdAt)
        return canonicalize(
            decision.copy(
                updatedAt = timestamp,
                deletedAt = timestamp,
            ),
        )
    }

    fun merge(
        first: CrossSourceIdentityDecision,
        second: CrossSourceIdentityDecision,
    ): CrossSourceIdentityDecision {
        val left = canonicalize(first)
        val right = canonicalize(second)
        require(left.pair == right.pair)
        if (left.updatedAt != right.updatedAt) return if (left.updatedAt > right.updatedAt) left else right
        if (samePayload(left, right)) return left.copy(createdAt = minOf(left.createdAt, right.createdAt))

        val stable = listOf(left, right).minBy(::stablePayload)
        return stable.copy(
            reasonCodes = (left.reasonCodes + right.reasonCodes + CrossSourceIdentityReasonCode.SYNC_CONFLICT)
                .sortedBy { it.ordinal }
                .take(MAX_REASON_CODES)
                .toSet(),
            reviewState = CrossSourceIdentityReviewState.NEEDS_REVIEW,
            createdAt = minOf(left.createdAt, right.createdAt),
            deletedAt = null,
        )
    }

    fun encodeReasonCodes(reasonCodes: Set<CrossSourceIdentityReasonCode>): String =
        reasonCodes.sortedBy { it.ordinal }.joinToString(",") { it.name }

    fun decodeReasonCodes(value: String): Set<CrossSourceIdentityReasonCode>? {
        if (value.isBlank()) return null
        return runCatching {
            value.split(',').map(CrossSourceIdentityReasonCode::valueOf).toSet()
        }.getOrNull()?.takeIf { it.isNotEmpty() && it.size <= MAX_REASON_CODES }
    }

    private fun isValidKey(key: CrossSourceRecordKey): Boolean =
        key.source > 0L && key.url.isNotBlank() && key.url.length <= MAX_URL_LENGTH

    private fun nextTimestamp(previous: CrossSourceIdentityDecision?, timestamp: Long): Long {
        require(timestamp > 0L)
        val previousUpdatedAt = previous?.updatedAt ?: Long.MIN_VALUE
        return if (timestamp > previousUpdatedAt) timestamp else Math.addExact(previousUpdatedAt, 1L)
    }

    private fun compareKeys(first: CrossSourceRecordKey, second: CrossSourceRecordKey): Int {
        val sourceOrder = first.source.compareTo(second.source)
        return if (sourceOrder != 0) sourceOrder else first.url.compareTo(second.url)
    }

    private fun stablePayload(decision: CrossSourceIdentityDecision): String = buildString {
        append(decision.decision.name)
        append('|')
        append(decision.reviewState.name)
        append('|')
        append(decision.deletedAt ?: Long.MIN_VALUE)
        append('|')
        append(encodeReasonCodes(decision.reasonCodes))
    }

    private fun samePayload(first: CrossSourceIdentityDecision, second: CrossSourceIdentityDecision): Boolean =
        first.pair == second.pair &&
            first.decision == second.decision &&
            first.decisionVersion == second.decisionVersion &&
            first.evidenceVersion == second.evidenceVersion &&
            first.reasonCodes == second.reasonCodes &&
            first.reviewState == second.reviewState &&
            first.updatedAt == second.updatedAt &&
            first.deletedAt == second.deletedAt
}
