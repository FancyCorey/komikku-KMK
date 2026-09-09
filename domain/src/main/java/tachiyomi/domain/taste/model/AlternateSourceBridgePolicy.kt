package tachiyomi.domain.taste.model

object AlternateSourceBridgePolicy {

    const val LEGACY_VERSION = 1
    const val CURRENT_VERSION = 2
    const val MAX_URL_LENGTH = CrossSourceIdentityDecisionPolicy.MAX_URL_LENGTH
    const val MAX_TARGET_ID_LENGTH = 36
    const val MAX_BRIDGE_ROWS = 2_000
    const val MAX_MAPPING_ROWS = 10_000
    const val MAX_MAPPINGS_PER_BRIDGE = 1_000
    const val MAX_FUTURE_SKEW_MS = CrossSourceIdentityDecisionPolicy.MAX_FUTURE_SKEW_MS
    const val MAX_ABSOLUTE_OFFSET_MILLI = 1_000_000L

    private val targetIdPattern = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    )

    fun isValidKey(key: AlternateSourceBridgeKey): Boolean =
        isValidRecordKey(key.primary) &&
            isValidRecordKey(key.alternate) &&
            key.primary != key.alternate

    fun isValid(bridge: AlternateSourceBridge, now: Long): Boolean =
        isValidKey(bridge.key) &&
            bridge.version in LEGACY_VERSION..CURRENT_VERSION &&
            bridge.createdAt > 0L &&
            bridge.updatedAt >= bridge.createdAt &&
            bridge.updatedAt <= now + MAX_FUTURE_SKEW_MS &&
            validDeletedAt(bridge.createdAt, bridge.updatedAt, bridge.deletedAt) &&
            validOptionalChapterUrl(bridge.continuationPrimaryChapterUrl) &&
            validOptionalChapterUrl(bridge.returnAfterAlternateChapterUrl) &&
            ((bridge.offsetMilli == null) == (bridge.offsetState == null)) &&
            (bridge.offsetMilli == null || validOffset(bridge.offsetMilli)) &&
            (
                bridge.version != LEGACY_VERSION ||
                    (bridge.returnAfterAlternateChapterUrl == null && !bridge.automaticReturn)
                ) &&
            (
                !bridge.automaticReturn ||
                    (bridge.continuationPrimaryChapterUrl != null && bridge.returnAfterAlternateChapterUrl != null)
                ) &&
            (bridge.reviewState != AlternateSourceBridgeReviewState.CONFLICT || !bridge.automaticReturn)

    fun isValid(mapping: AlternateSourceBridgeMapping, now: Long): Boolean {
        if (!isValidKey(mapping.key.bridge) || !isValidTargetId(mapping.key.targetId)) return false
        if (mapping.version !in LEGACY_VERSION..CURRENT_VERSION || mapping.createdAt <= 0L) return false
        if (mapping.updatedAt < mapping.createdAt || mapping.updatedAt > now + MAX_FUTURE_SKEW_MS) return false
        if (!validDeletedAt(mapping.createdAt, mapping.updatedAt, mapping.deletedAt)) return false
        if (!validOptionalChapterUrl(mapping.primaryChapterUrl) || !validOptionalChapterUrl(mapping.alternateChapterUrl)) return false
        if (!validOptionalChapterUrl(mapping.precedingPrimaryChapterUrl) || !validOptionalChapterUrl(mapping.followingPrimaryChapterUrl)) return false
        if (mapping.offsetMilli != null && !validOffset(mapping.offsetMilli)) return false

        val hasPrimary = mapping.primaryChapterUrl != null
        val hasAlternate = mapping.alternateChapterUrl != null
        val hasPreceding = mapping.precedingPrimaryChapterUrl != null
        val hasFollowing = mapping.followingPrimaryChapterUrl != null
        if (mapping.version == LEGACY_VERSION && (hasPreceding || hasFollowing)) return false
        return when (mapping.relation) {
            AlternateSourceBridgeMappingRelation.EXACT,
            AlternateSourceBridgeMappingRelation.DUPLICATE,
            -> hasPrimary && hasAlternate && !hasPreceding && !hasFollowing && mapping.offsetMilli == null

            AlternateSourceBridgeMappingRelation.OFFSET ->
                hasPrimary && hasAlternate && !hasPreceding && !hasFollowing && mapping.offsetMilli != null

            AlternateSourceBridgeMappingRelation.PRIMARY_MISSING ->
                !hasPrimary && hasAlternate &&
                    (mapping.version == LEGACY_VERSION || hasPreceding || hasFollowing) &&
                    mapping.offsetMilli == null

            AlternateSourceBridgeMappingRelation.ALTERNATE_SKIPPED ->
                hasAlternate && !hasPreceding && !hasFollowing && mapping.offsetMilli == null
        }
    }

    /** Identity confirmation can authorize offering this pair, but never supplies chapter mapping evidence. */
    fun isIdentityAuthorizedPair(
        key: AlternateSourceBridgeKey,
        decision: CrossSourceIdentityDecision?,
    ): Boolean =
        isValidKey(key) &&
            CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(decision) &&
            decision?.pair == CrossSourceIdentityDecisionPolicy.canonicalPair(key.primary, key.alternate)

    fun confirmedOffsetHint(bridge: AlternateSourceBridge?): Long? = bridge
        ?.takeIf {
            it.deletedAt == null &&
                it.reviewState == AlternateSourceBridgeReviewState.CURRENT &&
                it.offsetState == AlternateSourceBridgeEvidenceState.CONFIRMED
        }
        ?.offsetMilli

    fun approvedContinuationUrl(bridge: AlternateSourceBridge?): String? = bridge
        ?.takeIf { it.deletedAt == null && it.reviewState == AlternateSourceBridgeReviewState.CURRENT }
        ?.continuationPrimaryChapterUrl

    fun canAutomaticallyReturn(
        bridge: AlternateSourceBridge?,
        completedAlternateChapterUrl: String,
        primaryChapterUrl: String,
    ): Boolean =
        bridge?.automaticReturn == true &&
            bridge.returnAfterAlternateChapterUrl == completedAlternateChapterUrl &&
            approvedContinuationUrl(bridge) == primaryChapterUrl

    fun resolveAnchoredGapTarget(
        bridge: AlternateSourceBridge?,
        mappings: List<AlternateSourceBridgeMapping>,
        precedingPrimaryChapterUrl: String?,
        followingPrimaryChapterUrl: String?,
    ): AlternateSourceBridgeTargetResolution {
        if (bridge == null) return AlternateSourceBridgeTargetResolution.Unavailable
        if (precedingPrimaryChapterUrl == null && followingPrimaryChapterUrl == null) {
            return AlternateSourceBridgeTargetResolution.Unavailable
        }
        val candidates = projectConflicts(mappings.filter { it.key.bridge == bridge.key }).filter {
            it.deletedAt == null &&
                it.version == CURRENT_VERSION &&
                it.relation == AlternateSourceBridgeMappingRelation.PRIMARY_MISSING &&
                it.precedingPrimaryChapterUrl == precedingPrimaryChapterUrl &&
                it.followingPrimaryChapterUrl == followingPrimaryChapterUrl
        }
        val target = candidates.singleOrNull() ?: return if (candidates.isEmpty()) {
            AlternateSourceBridgeTargetResolution.Unavailable
        } else {
            AlternateSourceBridgeTargetResolution.Conflict
        }
        return resolveTarget(bridge, candidates, target.key.targetId)
    }

    fun resolveTarget(
        bridge: AlternateSourceBridge?,
        mappings: List<AlternateSourceBridgeMapping>,
        targetId: String,
    ): AlternateSourceBridgeTargetResolution {
        if (bridge == null || bridge.deletedAt != null) return AlternateSourceBridgeTargetResolution.Unavailable
        if (bridge.reviewState == AlternateSourceBridgeReviewState.CONFLICT) return AlternateSourceBridgeTargetResolution.Conflict
        val projected = projectConflicts(mappings.filter { it.key.bridge == bridge.key && it.deletedAt == null })
        val mapping = projected.singleOrNull { it.key.targetId == targetId }
            ?: return AlternateSourceBridgeTargetResolution.Unavailable
        if (mapping.state == AlternateSourceBridgeMappingState.CONFLICT) {
            return AlternateSourceBridgeTargetResolution.Conflict
        }
        if (mapping.relation == AlternateSourceBridgeMappingRelation.ALTERNATE_SKIPPED) {
            return AlternateSourceBridgeTargetResolution.Skipped
        }
        val alternate = mapping.alternateChapterUrl ?: return AlternateSourceBridgeTargetResolution.Unavailable
        return AlternateSourceBridgeTargetResolution.Target(alternate, mapping.state, mapping.relation)
    }

    fun projectConflicts(mappings: List<AlternateSourceBridgeMapping>): List<AlternateSourceBridgeMapping> {
        val merged = mappings
            .groupBy { it.key }
            .mapValues { (_, rows) -> rows.reduce(::merge) }
            .values
            .sortedWith(mappingComparator)
        val conflictingKeys = buildSet {
            merged.filter {
                it.deletedAt == null &&
                    it.relation == AlternateSourceBridgeMappingRelation.PRIMARY_MISSING &&
                    (it.precedingPrimaryChapterUrl != null || it.followingPrimaryChapterUrl != null)
            }
                .groupBy {
                    Triple(
                        it.key.bridge,
                        it.precedingPrimaryChapterUrl,
                        it.followingPrimaryChapterUrl,
                    )
                }
                .values
                .filter { rows -> rows.mapNotNull { it.alternateChapterUrl }.distinct().size > 1 }
                .flatten()
                .forEach { add(it.key) }
            merged.filter { it.deletedAt == null }.groupBy { it.primaryChapterUrl }
                .filterKeys { it != null }
                .values
                .filter { rows -> rows.mapNotNull { it.alternateChapterUrl }.distinct().size > 1 }
                .flatten()
                .forEach { add(it.key) }
            merged.filter { it.deletedAt == null && it.relation != AlternateSourceBridgeMappingRelation.DUPLICATE }
                .groupBy { it.alternateChapterUrl }
                .filterKeys { it != null }
                .values
                .filter { rows -> rows.map { it.key.targetId }.distinct().size > 1 }
                .flatten()
                .forEach { add(it.key) }
        }
        return merged.map { row ->
            if (row.key in conflictingKeys) row.copy(state = AlternateSourceBridgeMappingState.CONFLICT) else row
        }
    }

    fun merge(first: AlternateSourceBridge, second: AlternateSourceBridge): AlternateSourceBridge {
        require(first.key == second.key)
        if (first.updatedAt != second.updatedAt) return if (first.updatedAt > second.updatedAt) first else second
        if (samePayload(first, second)) return first.copy(createdAt = minOf(first.createdAt, second.createdAt))
        if ((first.deletedAt != null) != (second.deletedAt != null)) {
            return if (first.deletedAt != null) first else second
        }
        val stable = listOf(first, second).minBy(::stablePayload)
        return stable.copy(
            reviewState = AlternateSourceBridgeReviewState.CONFLICT,
            offsetState = stable.offsetMilli?.let { AlternateSourceBridgeEvidenceState.CONFLICT },
            automaticReturn = false,
            createdAt = minOf(first.createdAt, second.createdAt),
        )
    }

    fun merge(first: AlternateSourceBridgeMapping, second: AlternateSourceBridgeMapping): AlternateSourceBridgeMapping {
        require(first.key == second.key)
        if (first.updatedAt != second.updatedAt) return if (first.updatedAt > second.updatedAt) first else second
        if (samePayload(first, second)) return first.copy(createdAt = minOf(first.createdAt, second.createdAt))
        if ((first.deletedAt != null) != (second.deletedAt != null)) {
            return if (first.deletedAt != null) first else second
        }
        val stable = listOf(first, second).minBy(::stablePayload)
        return stable.copy(
            state = AlternateSourceBridgeMappingState.CONFLICT,
            createdAt = minOf(first.createdAt, second.createdAt),
        )
    }

    fun tombstone(bridge: AlternateSourceBridge, timestamp: Long): AlternateSourceBridge {
        require(timestamp >= bridge.createdAt)
        return bridge.copy(updatedAt = timestamp, deletedAt = timestamp, automaticReturn = false)
    }

    fun tombstone(mapping: AlternateSourceBridgeMapping, timestamp: Long): AlternateSourceBridgeMapping {
        require(timestamp >= mapping.createdAt)
        return mapping.copy(updatedAt = timestamp, deletedAt = timestamp)
    }

    fun nextTimestamp(previousUpdatedAt: Long?, timestamp: Long): Long {
        require(timestamp > 0L)
        return if (previousUpdatedAt == null || timestamp > previousUpdatedAt) {
            timestamp
        } else {
            Math.addExact(previousUpdatedAt, 1L)
        }
    }

    val bridgeComparator = compareBy<AlternateSourceBridge>(
        { it.key.primary.source },
        { it.key.primary.url },
        { it.key.alternate.source },
        { it.key.alternate.url },
    )

    val mappingComparator = compareBy<AlternateSourceBridgeMapping>(
        { it.key.bridge.primary.source },
        { it.key.bridge.primary.url },
        { it.key.bridge.alternate.source },
        { it.key.bridge.alternate.url },
        { it.key.targetId },
    )

    private fun isValidRecordKey(key: CrossSourceRecordKey): Boolean =
        key.source > 0L && key.url.isNotBlank() && key.url.length <= MAX_URL_LENGTH

    private fun isValidTargetId(value: String): Boolean =
        value.length == MAX_TARGET_ID_LENGTH && targetIdPattern.matches(value)

    private fun validOptionalChapterUrl(value: String?): Boolean =
        value == null || (value.isNotBlank() && value.length <= MAX_URL_LENGTH)

    private fun validOffset(value: Long): Boolean = value in -MAX_ABSOLUTE_OFFSET_MILLI..MAX_ABSOLUTE_OFFSET_MILLI

    private fun validDeletedAt(createdAt: Long, updatedAt: Long, deletedAt: Long?): Boolean =
        deletedAt == null || deletedAt in createdAt..updatedAt

    private fun samePayload(first: AlternateSourceBridge, second: AlternateSourceBridge): Boolean =
        first.copy(createdAt = 0L) == second.copy(createdAt = 0L)

    private fun samePayload(first: AlternateSourceBridgeMapping, second: AlternateSourceBridgeMapping): Boolean =
        first.copy(createdAt = 0L) == second.copy(createdAt = 0L)

    private fun stablePayload(value: AlternateSourceBridge): String = listOf(
        value.offsetMilli,
        value.offsetState?.name,
        value.continuationPrimaryChapterUrl,
        value.returnAfterAlternateChapterUrl,
        value.automaticReturn,
        value.reviewState.name,
        value.deletedAt,
    ).joinToString("|")

    private fun stablePayload(value: AlternateSourceBridgeMapping): String = listOf(
        value.primaryChapterUrl,
        value.alternateChapterUrl,
        value.precedingPrimaryChapterUrl,
        value.followingPrimaryChapterUrl,
        value.relation.name,
        value.state.name,
        value.offsetMilli,
        value.deletedAt,
    ).joinToString("|")
}
