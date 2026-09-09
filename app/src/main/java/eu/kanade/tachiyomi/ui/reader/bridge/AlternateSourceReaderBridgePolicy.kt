package eu.kanade.tachiyomi.ui.reader.bridge

import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingRelation
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState
import tachiyomi.domain.taste.model.AlternateSourceBridgeState

object AlternateSourceReaderBridgePolicy {

    sealed interface EntryDecision {
        data class Confirmed(val targetId: String, val alternateChapterUrl: String) : EntryDecision
        data class RequiresConfirmation(val targetId: String, val alternateChapterUrl: String) : EntryDecision
        data object Unavailable : EntryDecision
        data object Conflict : EntryDecision
    }

    sealed interface AutomaticReturnDecision {
        data class Allowed(val primaryChapterUrl: String) : AutomaticReturnDecision
        data object Disabled : AutomaticReturnDecision
        data object Stale : AutomaticReturnDecision
        data object Conflict : AutomaticReturnDecision
        data object Provisional : AutomaticReturnDecision
        data object WrongBoundary : AutomaticReturnDecision
    }

    fun resolveEntry(
        state: AlternateSourceBridgeState?,
        precedingPrimaryChapterUrl: String?,
        followingPrimaryChapterUrl: String?,
        now: Long,
    ): EntryDecision {
        if (state == null || !AlternateSourceBridgePolicy.isValid(state.bridge, now) || state.bridge.deletedAt != null) {
            return EntryDecision.Unavailable
        }
        if (state.bridge.version != AlternateSourceBridgePolicy.CURRENT_VERSION) return EntryDecision.Unavailable
        if (state.bridge.reviewState == AlternateSourceBridgeReviewState.CONFLICT) return EntryDecision.Conflict
        if (precedingPrimaryChapterUrl == null && followingPrimaryChapterUrl == null) return EntryDecision.Unavailable
        val candidates = AlternateSourceBridgePolicy.projectConflicts(state.mappings).filter {
            it.key.bridge == state.bridge.key &&
                it.deletedAt == null &&
                it.version == AlternateSourceBridgePolicy.CURRENT_VERSION &&
                it.relation == AlternateSourceBridgeMappingRelation.PRIMARY_MISSING &&
                it.precedingPrimaryChapterUrl == precedingPrimaryChapterUrl &&
                it.followingPrimaryChapterUrl == followingPrimaryChapterUrl
        }
        val mapping = candidates.singleOrNull() ?: return if (candidates.isEmpty()) {
            EntryDecision.Unavailable
        } else {
            EntryDecision.Conflict
        }
        if (!AlternateSourceBridgePolicy.isValid(mapping, now)) return EntryDecision.Unavailable
        if (mapping.state == AlternateSourceBridgeMappingState.CONFLICT) return EntryDecision.Conflict
        val alternateUrl = mapping.alternateChapterUrl ?: return EntryDecision.Unavailable
        return when (mapping.state) {
            AlternateSourceBridgeMappingState.CONFIRMED -> EntryDecision.Confirmed(mapping.key.targetId, alternateUrl)
            AlternateSourceBridgeMappingState.PROVISIONAL -> {
                EntryDecision.RequiresConfirmation(mapping.key.targetId, alternateUrl)
            }
            AlternateSourceBridgeMappingState.CONFLICT -> EntryDecision.Conflict
        }
    }

    fun automaticReturn(
        state: AlternateSourceBridgeState?,
        session: AlternateSourceReaderSession,
        completedAlternateChapterUrl: String,
        now: Long,
    ): AutomaticReturnDecision {
        if (!AlternateSourceReaderSession.isValid(session)) return AutomaticReturnDecision.Disabled
        if (state == null || state.bridge.key != session.bridgeKey) return AutomaticReturnDecision.Stale
        if (!AlternateSourceBridgePolicy.isValid(state.bridge, now) || state.bridge.deletedAt != null) {
            return AutomaticReturnDecision.Stale
        }
        if (state.bridge.version != AlternateSourceBridgePolicy.CURRENT_VERSION) {
            return AutomaticReturnDecision.Stale
        }
        if (state.bridge.reviewState == AlternateSourceBridgeReviewState.CONFLICT) {
            return AutomaticReturnDecision.Conflict
        }
        if (state.bridge.updatedAt != session.bridgeUpdatedAt) return AutomaticReturnDecision.Stale
        val targetId = session.activeTargetId ?: return AutomaticReturnDecision.Disabled
        val candidates = AlternateSourceBridgePolicy.projectConflicts(state.mappings).filter {
            it.key.bridge == session.bridgeKey && it.key.targetId == targetId && it.deletedAt == null
        }
        val mapping = candidates.singleOrNull() ?: return if (candidates.isEmpty()) {
            AutomaticReturnDecision.Stale
        } else {
            AutomaticReturnDecision.Conflict
        }
        if (!AlternateSourceBridgePolicy.isValid(mapping, now)) return AutomaticReturnDecision.Stale
        if (
            mapping.version != AlternateSourceBridgePolicy.CURRENT_VERSION ||
            mapping.relation != AlternateSourceBridgeMappingRelation.PRIMARY_MISSING
        ) {
            return AutomaticReturnDecision.Stale
        }
        if (mapping.updatedAt != session.mappingUpdatedAt) return AutomaticReturnDecision.Stale
        if (mapping.state == AlternateSourceBridgeMappingState.CONFLICT) return AutomaticReturnDecision.Conflict
        if (mapping.state != AlternateSourceBridgeMappingState.CONFIRMED) return AutomaticReturnDecision.Provisional
        if (session.currentRoute.role != AlternateSourceReaderRouteRole.ALTERNATE) {
            return AutomaticReturnDecision.Disabled
        }
        if (session.currentRoute.chapterUrl != completedAlternateChapterUrl) {
            return AutomaticReturnDecision.WrongBoundary
        }
        val primaryUrl = state.bridge.continuationPrimaryChapterUrl ?: return AutomaticReturnDecision.Disabled
        return if (
            AlternateSourceBridgePolicy.canAutomaticallyReturn(
                state.bridge,
                completedAlternateChapterUrl,
                primaryUrl,
            )
        ) {
            AutomaticReturnDecision.Allowed(primaryUrl)
        } else {
            AutomaticReturnDecision.WrongBoundary
        }
    }
}
