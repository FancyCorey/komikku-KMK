package tachiyomi.domain.taste.model

data class CrossSourceRecordKey(
    val source: Long,
    val url: String,
)

data class CrossSourceIdentityPair(
    val left: CrossSourceRecordKey,
    val right: CrossSourceRecordKey,
)

enum class CrossSourceIdentityDecisionValue {
    USER_CONFIRMED,
    USER_REJECTED,
}

enum class CrossSourceIdentityReviewState {
    CURRENT,
    NEEDS_REVIEW,
}

enum class CrossSourceIdentityReasonCode {
    USER_CONFIRMATION,
    USER_REJECTION,
    BRIDGE_ALTERNATE_SELECTION,
    SYNC_CONFLICT,
    ENDPOINT_STALE,
    URL_REUSED,
}

data class CrossSourceIdentityDecision(
    val pair: CrossSourceIdentityPair,
    val decision: CrossSourceIdentityDecisionValue,
    val decisionVersion: Int,
    val evidenceVersion: Int,
    val reasonCodes: Set<CrossSourceIdentityReasonCode>,
    val reviewState: CrossSourceIdentityReviewState,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class CrossSourceIdentityReplacement(
    val expected: CrossSourceIdentityDecision?,
    val replacement: CrossSourceIdentityDecision?,
)
