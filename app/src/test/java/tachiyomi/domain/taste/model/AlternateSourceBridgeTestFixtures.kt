package tachiyomi.domain.taste.model

internal const val BRIDGE_TARGET_ID = "123e4567-e89b-42d3-a456-426614174000"

internal fun bridgeKey() = AlternateSourceBridgeKey(
    primary = CrossSourceRecordKey(1, "/primary"),
    alternate = CrossSourceRecordKey(2, "/alternate"),
)

internal fun bridge(
    updatedAt: Long = 2_000,
    offsetMilli: Long? = null,
    offsetState: AlternateSourceBridgeEvidenceState? = null,
    continuationUrl: String? = null,
    returnAfterUrl: String? = null,
    automaticReturn: Boolean = false,
    reviewState: AlternateSourceBridgeReviewState = AlternateSourceBridgeReviewState.CURRENT,
    deletedAt: Long? = null,
) = AlternateSourceBridge(
    key = bridgeKey(),
    version = AlternateSourceBridgePolicy.CURRENT_VERSION,
    offsetMilli = offsetMilli,
    offsetState = offsetState,
    continuationPrimaryChapterUrl = continuationUrl,
    returnAfterAlternateChapterUrl = returnAfterUrl,
    automaticReturn = automaticReturn,
    reviewState = reviewState,
    createdAt = 1_000,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun bridgeMapping(
    targetId: String = BRIDGE_TARGET_ID,
    updatedAt: Long = 2_000,
    primaryUrl: String? = null,
    alternateUrl: String? = "/chapter/alternate",
    relation: AlternateSourceBridgeMappingRelation = AlternateSourceBridgeMappingRelation.PRIMARY_MISSING,
    precedingPrimaryUrl: String? = if (relation == AlternateSourceBridgeMappingRelation.PRIMARY_MISSING) "/chapter/before" else null,
    followingPrimaryUrl: String? = if (relation == AlternateSourceBridgeMappingRelation.PRIMARY_MISSING) "/chapter/after" else null,
    state: AlternateSourceBridgeMappingState = AlternateSourceBridgeMappingState.CONFIRMED,
    offsetMilli: Long? = null,
    deletedAt: Long? = null,
) = AlternateSourceBridgeMapping(
    key = AlternateSourceBridgeMappingKey(bridgeKey(), targetId),
    primaryChapterUrl = primaryUrl,
    alternateChapterUrl = alternateUrl,
    precedingPrimaryChapterUrl = precedingPrimaryUrl,
    followingPrimaryChapterUrl = followingPrimaryUrl,
    relation = relation,
    state = state,
    offsetMilli = offsetMilli,
    version = AlternateSourceBridgePolicy.CURRENT_VERSION,
    createdAt = 1_000,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
