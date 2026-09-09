package tachiyomi.domain.taste.model

data class AlternateSourceBridgeKey(
    val primary: CrossSourceRecordKey,
    val alternate: CrossSourceRecordKey,
)

enum class AlternateSourceBridgeEvidenceState {
    PROVISIONAL,
    CONFIRMED,
    CONFLICT,
}

enum class AlternateSourceBridgeReviewState {
    CURRENT,
    CONFLICT,
}

data class AlternateSourceBridge(
    val key: AlternateSourceBridgeKey,
    val version: Int,
    val offsetMilli: Long? = null,
    val offsetState: AlternateSourceBridgeEvidenceState? = null,
    val continuationPrimaryChapterUrl: String? = null,
    val returnAfterAlternateChapterUrl: String? = null,
    val automaticReturn: Boolean = false,
    val reviewState: AlternateSourceBridgeReviewState = AlternateSourceBridgeReviewState.CURRENT,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

enum class AlternateSourceBridgeMappingRelation {
    EXACT,
    OFFSET,
    DUPLICATE,
    PRIMARY_MISSING,
    ALTERNATE_SKIPPED,
}

enum class AlternateSourceBridgeMappingState {
    PROVISIONAL,
    CONFIRMED,
    CONFLICT,
}

data class AlternateSourceBridgeMappingKey(
    val bridge: AlternateSourceBridgeKey,
    val targetId: String,
)

data class AlternateSourceBridgeMapping(
    val key: AlternateSourceBridgeMappingKey,
    val primaryChapterUrl: String? = null,
    val alternateChapterUrl: String? = null,
    val precedingPrimaryChapterUrl: String? = null,
    val followingPrimaryChapterUrl: String? = null,
    val relation: AlternateSourceBridgeMappingRelation,
    val state: AlternateSourceBridgeMappingState,
    val offsetMilli: Long? = null,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

data class AlternateSourceBridgeMappingReplacement(
    val expected: AlternateSourceBridgeMapping?,
    val replacement: AlternateSourceBridgeMapping?,
)

data class AlternateSourceBridgeStateReplacement(
    val expectedBridge: AlternateSourceBridge?,
    val replacementBridge: AlternateSourceBridge?,
    val mappingReplacements: List<AlternateSourceBridgeMappingReplacement> = emptyList(),
)

data class AlternateSourceBridgeState(
    val bridge: AlternateSourceBridge,
    val mappings: List<AlternateSourceBridgeMapping>,
)

sealed interface AlternateSourceBridgeTargetResolution {
    data object Unavailable : AlternateSourceBridgeTargetResolution
    data object Conflict : AlternateSourceBridgeTargetResolution
    data object Skipped : AlternateSourceBridgeTargetResolution

    data class Target(
        val alternateChapterUrl: String,
        val state: AlternateSourceBridgeMappingState,
        val relation: AlternateSourceBridgeMappingRelation,
    ) : AlternateSourceBridgeTargetResolution
}
