package eu.kanade.tachiyomi.ui.reader.bridge

import tachiyomi.domain.taste.model.AlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMapping
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingReplacement
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement

object AlternateSourceReaderMutationPolicy {

    sealed interface Result {
        data class Replacement(
            val value: AlternateSourceBridgeStateReplacement,
            val targetId: String,
        ) : Result

        data object Unchanged : Result
        data object Unavailable : Result
        data object Conflict : Result
        data object Invalid : Result
    }

    fun select(
        current: AlternateSourceBridgeState?,
        key: AlternateSourceBridgeKey,
        precedingPrimaryChapterUrl: String,
        followingPrimaryChapterUrl: String?,
        alternateChapterUrl: String,
        targetId: String,
        now: Long,
    ): Result {
        if (!AlternateSourceBridgePolicy.isValidKey(key)) return Result.Invalid
        val bridge = current?.bridge
        val anchored: List<AlternateSourceBridgeMapping> = current?.let {
            AlternateSourceBridgePolicy.projectConflicts(it.mappings).filter {
                it.deletedAt == null &&
                    it.relation == AlternateSourceBridgeMappingRelation.PRIMARY_MISSING &&
                    it.precedingPrimaryChapterUrl == precedingPrimaryChapterUrl &&
                    it.followingPrimaryChapterUrl == followingPrimaryChapterUrl
            }
        }.orEmpty()
        if (bridge != null) {
            if (
                bridge.key != key ||
                bridge.deletedAt != null ||
                bridge.version != AlternateSourceBridgePolicy.CURRENT_VERSION ||
                bridge.reviewState == AlternateSourceBridgeReviewState.CONFLICT ||
                !AlternateSourceBridgePolicy.isValid(bridge, now)
            ) {
                return Result.Unavailable
            }
            if (anchored.size > 1) return Result.Conflict
        }
        val latest = listOfNotNull(
            bridge?.updatedAt,
            current?.mappings?.maxOfOrNull { it.updatedAt },
        ).maxOrNull()
        val timestamp = AlternateSourceBridgePolicy.nextTimestamp(latest, now)
        val replacementBridge = bridge?.copy(
            continuationPrimaryChapterUrl = followingPrimaryChapterUrl,
            returnAfterAlternateChapterUrl = alternateChapterUrl,
            updatedAt = timestamp,
        ) ?: AlternateSourceBridge(
            key = key,
            version = AlternateSourceBridgePolicy.CURRENT_VERSION,
            continuationPrimaryChapterUrl = followingPrimaryChapterUrl,
            returnAfterAlternateChapterUrl = alternateChapterUrl,
            reviewState = AlternateSourceBridgeReviewState.CURRENT,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val mapping = AlternateSourceBridgeMapping(
            key = AlternateSourceBridgeMappingKey(key, targetId),
            alternateChapterUrl = alternateChapterUrl,
            precedingPrimaryChapterUrl = precedingPrimaryChapterUrl,
            followingPrimaryChapterUrl = followingPrimaryChapterUrl,
            relation = AlternateSourceBridgeMappingRelation.PRIMARY_MISSING,
            state = AlternateSourceBridgeMappingState.PROVISIONAL,
            version = AlternateSourceBridgePolicy.CURRENT_VERSION,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        if (
            !AlternateSourceBridgePolicy.isValid(replacementBridge, now) ||
            !AlternateSourceBridgePolicy.isValid(mapping, now)
        ) {
            return Result.Invalid
        }
        val mappingReplacements = buildList {
            anchored.singleOrNull()?.let {
                add(AlternateSourceBridgeMappingReplacement(it, null))
            }
            add(AlternateSourceBridgeMappingReplacement(null, mapping))
        }
        return Result.Replacement(
            AlternateSourceBridgeStateReplacement(
                expectedBridge = bridge,
                replacementBridge = replacementBridge,
                mappingReplacements = mappingReplacements,
            ),
            targetId,
        )
    }

    fun correct(
        current: AlternateSourceBridgeState?,
        targetId: String,
        alternateChapterUrl: String,
        now: Long,
    ): Result {
        val state = current ?: return Result.Unavailable
        if (projectedTargetIsConflicting(state, targetId)) return Result.Conflict
        val expected = liveTarget(state, targetId, now) ?: return Result.Unavailable
        if (expected.state == AlternateSourceBridgeMappingState.CONFLICT) return Result.Conflict
        if (expected.relation != AlternateSourceBridgeMappingRelation.PRIMARY_MISSING) return Result.Unavailable
        val following = expected.followingPrimaryChapterUrl ?: return Result.Invalid
        if (
            expected.alternateChapterUrl == alternateChapterUrl &&
            expected.state == AlternateSourceBridgeMappingState.CONFIRMED &&
            state.bridge.continuationPrimaryChapterUrl == following &&
            state.bridge.returnAfterAlternateChapterUrl == alternateChapterUrl
        ) {
            return Result.Unchanged
        }
        val timestamp = nextTimestamp(state, now)
        val bridge = state.bridge.copy(
            continuationPrimaryChapterUrl = following,
            returnAfterAlternateChapterUrl = alternateChapterUrl,
            updatedAt = timestamp,
        )
        val mapping = expected.copy(
            alternateChapterUrl = alternateChapterUrl,
            state = AlternateSourceBridgeMappingState.CONFIRMED,
            version = AlternateSourceBridgePolicy.CURRENT_VERSION,
            updatedAt = timestamp,
        )
        if (!AlternateSourceBridgePolicy.isValid(bridge, now) || !AlternateSourceBridgePolicy.isValid(mapping, now)) {
            return Result.Invalid
        }
        return Result.Replacement(
            AlternateSourceBridgeStateReplacement(
                expectedBridge = state.bridge,
                replacementBridge = bridge,
                mappingReplacements = listOf(AlternateSourceBridgeMappingReplacement(expected, mapping)),
            ),
            targetId,
        )
    }

    fun skip(
        current: AlternateSourceBridgeState?,
        targetId: String,
        alternateChapterUrl: String,
        now: Long,
    ): Result {
        val state = current ?: return Result.Unavailable
        if (projectedTargetIsConflicting(state, targetId)) return Result.Conflict
        val expected = liveTarget(state, targetId, now) ?: return Result.Unavailable
        if (expected.state == AlternateSourceBridgeMappingState.CONFLICT) return Result.Conflict
        if (
            expected.relation != AlternateSourceBridgeMappingRelation.PRIMARY_MISSING ||
            expected.alternateChapterUrl != alternateChapterUrl
        ) {
            return Result.Unavailable
        }
        val timestamp = nextTimestamp(state, now)
        val bridge = state.bridge.copy(
            returnAfterAlternateChapterUrl = null,
            automaticReturn = false,
            updatedAt = timestamp,
        )
        val mapping = expected.copy(
            primaryChapterUrl = null,
            precedingPrimaryChapterUrl = null,
            followingPrimaryChapterUrl = null,
            relation = AlternateSourceBridgeMappingRelation.ALTERNATE_SKIPPED,
            state = AlternateSourceBridgeMappingState.CONFIRMED,
            offsetMilli = null,
            version = AlternateSourceBridgePolicy.CURRENT_VERSION,
            updatedAt = timestamp,
        )
        if (!AlternateSourceBridgePolicy.isValid(bridge, now) || !AlternateSourceBridgePolicy.isValid(mapping, now)) {
            return Result.Invalid
        }
        return Result.Replacement(
            AlternateSourceBridgeStateReplacement(
                expectedBridge = state.bridge,
                replacementBridge = bridge,
                mappingReplacements = listOf(AlternateSourceBridgeMappingReplacement(expected, mapping)),
            ),
            targetId,
        )
    }

    private fun liveTarget(
        state: AlternateSourceBridgeState,
        targetId: String,
        now: Long,
    ): AlternateSourceBridgeMapping? {
        if (
            state.bridge.deletedAt != null ||
            state.bridge.version != AlternateSourceBridgePolicy.CURRENT_VERSION ||
            state.bridge.reviewState == AlternateSourceBridgeReviewState.CONFLICT ||
            !AlternateSourceBridgePolicy.isValid(state.bridge, now)
        ) {
            return null
        }
        val candidates = state.mappings.filter {
            it.key.bridge == state.bridge.key &&
                it.key.targetId == targetId &&
                it.deletedAt == null &&
                AlternateSourceBridgePolicy.isValid(it, now)
        }
        return candidates.singleOrNull()
    }

    private fun projectedTargetIsConflicting(
        state: AlternateSourceBridgeState,
        targetId: String,
    ): Boolean = AlternateSourceBridgePolicy.projectConflicts(state.mappings)
        .singleOrNull { it.key.bridge == state.bridge.key && it.key.targetId == targetId && it.deletedAt == null }
        ?.state == AlternateSourceBridgeMappingState.CONFLICT

    private fun nextTimestamp(state: AlternateSourceBridgeState, now: Long): Long =
        AlternateSourceBridgePolicy.nextTimestamp(
            maxOf(state.bridge.updatedAt, state.mappings.maxOfOrNull { it.updatedAt } ?: Long.MIN_VALUE),
            now,
        )
}
