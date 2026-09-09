package eu.kanade.tachiyomi.ui.reader.bridge

import exh.recs.bridge.AlternateSourceBridgeController
import exh.recs.bridge.AlternateSourceBridgeMutation
import exh.recs.bridge.AlternateSourceBridgeMutationResult
import exh.recs.matching.CrossSourceIdentityAuthorizationResolver
import exh.recs.matching.CrossSourceIdentityDecisionController
import exh.recs.matching.CrossSourceIdentityMutation
import exh.recs.matching.CrossSourceIdentityMutationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeKey
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingState
import tachiyomi.domain.taste.model.AlternateSourceBridgePolicy
import tachiyomi.domain.taste.model.AlternateSourceBridgeState
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

fun interface AlternateSourceReaderRouteSwitcher {
    suspend fun switchTo(route: AlternateSourceReaderRoute): Boolean
}

class AlternateSourceReaderSessionStore(
    private val get: (String) -> Any?,
    private val set: (String, Any?) -> Unit,
    private val remove: (String) -> Unit,
) {
    fun read(): AlternateSourceReaderStateCodec.DecodeResult = AlternateSourceReaderStateCodec.read(get)

    fun hasStoredState(): Boolean = AlternateSourceReaderStateCodec.ALL_KEYS.any { get(it) != null }

    fun write(session: AlternateSourceReaderSession) = AlternateSourceReaderStateCodec.write(session, set)

    fun clear() = AlternateSourceReaderStateCodec.clear(remove)
}

data class AlternateSourceReaderEntryRequest(
    val bridgeKey: AlternateSourceBridgeKey,
    val primaryRoute: AlternateSourceReaderRoute,
    val precedingPrimaryChapterUrl: String,
    val followingPrimaryChapterUrl: String?,
)

sealed interface AlternateSourceReaderCommandResult {
    data class Entered(val targetId: String) : AlternateSourceReaderCommandResult
    data class RequiresProvisionalConfirmation(val targetId: String) : AlternateSourceReaderCommandResult
    data object RequiresPairConfirmation : AlternateSourceReaderCommandResult
    data object PairConfirmed : AlternateSourceReaderCommandResult
    data object Corrected : AlternateSourceReaderCommandResult
    data object Skipped : AlternateSourceReaderCommandResult
    data object Continued : AlternateSourceReaderCommandResult
    data object Returned : AlternateSourceReaderCommandResult
    data object Restored : AlternateSourceReaderCommandResult
    data object NoSession : AlternateSourceReaderCommandResult
    data object Unavailable : AlternateSourceReaderCommandResult
    data object Conflict : AlternateSourceReaderCommandResult
    data object Stale : AlternateSourceReaderCommandResult
    data object NotAtBoundary : AlternateSourceReaderCommandResult
    data object LoopRejected : AlternateSourceReaderCommandResult
    data object Invalid : AlternateSourceReaderCommandResult
    data object Failed : AlternateSourceReaderCommandResult
}

class AlternateSourceReaderCoordinator(
    private val getBridge: GetAlternateSourceBridge,
    private val routeResolver: AlternateSourceReaderRouteResolver,
    private val bridgeController: AlternateSourceBridgeController,
    private val identityResolver: CrossSourceIdentityAuthorizationResolver,
    private val identityController: CrossSourceIdentityDecisionController,
    private val sessionStore: AlternateSourceReaderSessionStore,
    private val routeSwitcher: AlternateSourceReaderRouteSwitcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val targetId: () -> String = { UUID.randomUUID().toString() },
) {
    private val commandMutex = Mutex()
    private val dismissed = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(AlternateSourceReaderMachineState())
    val state: StateFlow<AlternateSourceReaderMachineState> = mutableState.asStateFlow()

    suspend fun restore(activeRoute: AlternateSourceReaderRoute): AlternateSourceReaderCommandResult = guarded {
        val decoded = sessionStore.read()
        val session = when (decoded) {
            is AlternateSourceReaderStateCodec.DecodeResult.Valid -> decoded.session
            AlternateSourceReaderStateCodec.DecodeResult.Invalid -> {
                if (!sessionStore.hasStoredState()) return@guarded AlternateSourceReaderCommandResult.NoSession
                clearInvalidRestoredState()
                return@guarded AlternateSourceReaderCommandResult.Invalid
            }
        }
        if (
            session.pendingRouteFingerprint != null ||
            activeRoute.role != AlternateSourceReaderRouteRole.ALTERNATE ||
            AlternateSourceReaderRouteFingerprint.of(activeRoute) != session.lastSafeRouteFingerprint ||
            session.currentRoute != activeRoute ||
            !identityResolver.isConfirmed(
                session.bridgeKey.primary.source,
                session.bridgeKey.primary.url,
                session.bridgeKey.alternate.source,
                session.bridgeKey.alternate.url,
            )
        ) {
            clearInvalidRestoredState()
            return@guarded AlternateSourceReaderCommandResult.Invalid
        }
        val bridge = getBridge.await(session.bridgeKey)
        if (!isCurrentSessionState(bridge, session)) {
            clearInvalidRestoredState()
            return@guarded AlternateSourceReaderCommandResult.Stale
        }
        mutableState.value = AlternateSourceReaderMachineState(
            phase = AlternateSourceReaderPhase.ALTERNATE,
            session = session,
        )
        AlternateSourceReaderCommandResult.Restored
    }

    suspend fun confirmPair(key: AlternateSourceBridgeKey): AlternateSourceReaderCommandResult = guarded {
        if (!AlternateSourceBridgePolicy.isValidKey(key)) return@guarded AlternateSourceReaderCommandResult.Invalid
        if (isPairConfirmed(key)) return@guarded AlternateSourceReaderCommandResult.PairConfirmed
        when (
            identityController.mutate(
                CrossSourceIdentityDecisionPolicy.canonicalPair(key.primary, key.alternate),
                CrossSourceIdentityMutation.CONFIRM,
            )
        ) {
            CrossSourceIdentityMutationResult.APPLIED,
            CrossSourceIdentityMutationResult.UNCHANGED,
            -> AlternateSourceReaderCommandResult.PairConfirmed
            CrossSourceIdentityMutationResult.CONFLICT -> AlternateSourceReaderCommandResult.Conflict
            CrossSourceIdentityMutationResult.FAILED -> AlternateSourceReaderCommandResult.Failed
        }
    }

    suspend fun enter(
        request: AlternateSourceReaderEntryRequest,
        confirmProvisional: Boolean = false,
    ): AlternateSourceReaderCommandResult = guarded {
        enterLocked(request, confirmProvisional)
    }

    suspend fun select(
        request: AlternateSourceReaderEntryRequest,
        alternateChapterUrl: String,
    ): AlternateSourceReaderCommandResult = guarded {
        if (!validRequest(request)) return@guarded AlternateSourceReaderCommandResult.Invalid
        if (!isPairConfirmed(request.bridgeKey)) {
            return@guarded AlternateSourceReaderCommandResult.RequiresPairConfirmation
        }
        val current = getBridge.await(request.bridgeKey)
        val existing = AlternateSourceReaderBridgePolicy.resolveEntry(
            current,
            request.precedingPrimaryChapterUrl,
            request.followingPrimaryChapterUrl,
            clock(),
        )
        when (existing) {
            is AlternateSourceReaderBridgePolicy.EntryDecision.Confirmed -> {
                if (existing.alternateChapterUrl == alternateChapterUrl) {
                    return@guarded enterLocked(request, confirmProvisional = false)
                }
            }
            else -> Unit
        }
        val build = AlternateSourceReaderMutationPolicy.select(
            current = current,
            key = request.bridgeKey,
            precedingPrimaryChapterUrl = request.precedingPrimaryChapterUrl,
            followingPrimaryChapterUrl = request.followingPrimaryChapterUrl,
            alternateChapterUrl = alternateChapterUrl,
            targetId = targetId(),
            now = clock(),
        )
        val replacement = when (build) {
            is AlternateSourceReaderMutationPolicy.Result.Replacement -> build
            AlternateSourceReaderMutationPolicy.Result.Conflict -> return@guarded AlternateSourceReaderCommandResult.Conflict
            AlternateSourceReaderMutationPolicy.Result.Unavailable -> return@guarded AlternateSourceReaderCommandResult.Unavailable
            AlternateSourceReaderMutationPolicy.Result.Invalid -> return@guarded AlternateSourceReaderCommandResult.Invalid
            AlternateSourceReaderMutationPolicy.Result.Unchanged -> return@guarded AlternateSourceReaderCommandResult.Stale
        }
        val mutation = if (current == null) {
            AlternateSourceBridgeMutation.CREATE
        } else {
            AlternateSourceBridgeMutation.CORRECT_MAPPING
        }
        when (bridgeController.apply(replacement.value, mutation)) {
            AlternateSourceBridgeMutationResult.APPLIED -> enterLocked(request, confirmProvisional = false)
            AlternateSourceBridgeMutationResult.UNCHANGED -> AlternateSourceReaderCommandResult.Stale
            AlternateSourceBridgeMutationResult.CONFLICT -> AlternateSourceReaderCommandResult.Conflict
            AlternateSourceBridgeMutationResult.FAILED -> AlternateSourceReaderCommandResult.Failed
        }
    }

    suspend fun correct(currentRoute: AlternateSourceReaderRoute): AlternateSourceReaderCommandResult = guarded {
        val session = activeSession() ?: return@guarded AlternateSourceReaderCommandResult.NoSession
        val continued = continuedSession(session, currentRoute)
            ?: return@guarded AlternateSourceReaderCommandResult.Invalid
        mutableState.value = mutableState.value.copy(session = continued)
        val generation = begin(AlternateSourceReaderEvent.BeginCorrection)
            ?: return@guarded AlternateSourceReaderCommandResult.Stale
        val current = getBridge.await(continued.bridgeKey)
        if (!isCurrentSessionState(current, continued)) {
            fail(generation, AlternateSourceReaderFailureReason.STALE_BRIDGE, continued)
            return@guarded AlternateSourceReaderCommandResult.Stale
        }
        val target = continued.activeTargetId ?: return@guarded cancel(
            generation,
            AlternateSourceReaderCommandResult.Unavailable,
        )
        val build = AlternateSourceReaderMutationPolicy.correct(
            current,
            target,
            currentRoute.chapterUrl,
            clock(),
        )
        applyMutation(
            generation = generation,
            session = continued,
            build = build,
            mutation = AlternateSourceBridgeMutation.CORRECT_MAPPING,
            clearTarget = false,
            success = AlternateSourceReaderCommandResult.Corrected,
        )
    }

    suspend fun skip(currentRoute: AlternateSourceReaderRoute): AlternateSourceReaderCommandResult = guarded {
        val session = activeSession() ?: return@guarded AlternateSourceReaderCommandResult.NoSession
        val continued = continuedSession(session, currentRoute)
            ?: return@guarded AlternateSourceReaderCommandResult.Invalid
        mutableState.value = mutableState.value.copy(session = continued)
        val generation = begin(AlternateSourceReaderEvent.BeginCorrection)
            ?: return@guarded AlternateSourceReaderCommandResult.Stale
        val current = getBridge.await(continued.bridgeKey)
        if (!isCurrentSessionState(current, continued)) {
            fail(generation, AlternateSourceReaderFailureReason.STALE_BRIDGE, continued)
            return@guarded AlternateSourceReaderCommandResult.Stale
        }
        val target = continued.activeTargetId ?: return@guarded cancel(
            generation,
            AlternateSourceReaderCommandResult.Unavailable,
        )
        val build = AlternateSourceReaderMutationPolicy.skip(current, target, currentRoute.chapterUrl, clock())
        applyMutation(
            generation = generation,
            session = continued,
            build = build,
            mutation = AlternateSourceBridgeMutation.SKIP_ALTERNATE,
            clearTarget = true,
            success = AlternateSourceReaderCommandResult.Skipped,
        )
    }

    suspend fun continueAlternate(route: AlternateSourceReaderRoute): AlternateSourceReaderCommandResult = guarded {
        val session = activeSession() ?: return@guarded AlternateSourceReaderCommandResult.NoSession
        val updated = continuedSession(session, route)
            ?: return@guarded AlternateSourceReaderCommandResult.Invalid
        sessionStore.write(updated)
        mutableState.value = mutableState.value.copy(
            phase = AlternateSourceReaderPhase.ALTERNATE,
            session = updated,
            failureReason = null,
        )
        AlternateSourceReaderCommandResult.Continued
    }

    suspend fun manualReturn(preferContinuation: Boolean): AlternateSourceReaderCommandResult = guarded {
        val session = activeSession() ?: return@guarded AlternateSourceReaderCommandResult.NoSession
        val generation = begin(AlternateSourceReaderEvent.BeginReturn)
            ?: return@guarded AlternateSourceReaderCommandResult.Stale
        val state = getBridge.await(session.bridgeKey)
        val preferredUrl = if (
            preferContinuation &&
            state?.bridge?.key == session.bridgeKey &&
            state.bridge.deletedAt == null &&
            state.bridge.reviewState != tachiyomi.domain.taste.model.AlternateSourceBridgeReviewState.CONFLICT
        ) {
            AlternateSourceBridgePolicy.approvedContinuationUrl(state.bridge)
        } else {
            null
        }
        val preferred = preferredUrl?.let {
            resolve(session.bridgeKey, AlternateSourceReaderRouteRole.PRIMARY, it, 0)
        }
        val destination = preferred ?: resolve(
            session.bridgeKey,
            AlternateSourceReaderRouteRole.PRIMARY,
            session.primaryResumeRoute.chapterUrl,
            session.primaryResumeRoute.pageIndex,
        ) ?: run {
            fail(generation, AlternateSourceReaderFailureReason.ROUTE_UNAVAILABLE, session)
            return@guarded AlternateSourceReaderCommandResult.Unavailable
        }
        returnLocked(session, destination, generation, explicitManualReturn = true)
    }

    suspend fun automaticReturn(completedAlternateChapterUrl: String): AlternateSourceReaderCommandResult = guarded {
        val session = activeSession() ?: return@guarded AlternateSourceReaderCommandResult.NoSession
        val generation = begin(AlternateSourceReaderEvent.BeginReturn)
            ?: return@guarded AlternateSourceReaderCommandResult.Stale
        val bridge = getBridge.await(session.bridgeKey)
        val decision = AlternateSourceReaderBridgePolicy.automaticReturn(
            bridge,
            session,
            completedAlternateChapterUrl,
            clock(),
        )
        val primaryUrl = when (decision) {
            is AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Allowed -> decision.primaryChapterUrl
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.WrongBoundary ->
                return@guarded cancel(generation, AlternateSourceReaderCommandResult.NotAtBoundary)
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Conflict ->
                return@guarded cancel(generation, AlternateSourceReaderCommandResult.Conflict)
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Stale ->
                return@guarded cancel(generation, AlternateSourceReaderCommandResult.Stale)
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Disabled,
            AlternateSourceReaderBridgePolicy.AutomaticReturnDecision.Provisional,
            -> return@guarded cancel(generation, AlternateSourceReaderCommandResult.NotAtBoundary)
        }
        val destination = resolve(
            session.bridgeKey,
            AlternateSourceReaderRouteRole.PRIMARY,
            primaryUrl,
            0,
        ) ?: run {
            fail(generation, AlternateSourceReaderFailureReason.ROUTE_UNAVAILABLE, session)
            return@guarded AlternateSourceReaderCommandResult.Unavailable
        }
        returnLocked(session, destination, generation, explicitManualReturn = false)
    }

    suspend fun hasUnacknowledgedUncertainAlignment(): Boolean = commandMutex.withLock {
        val session = activeSession() ?: return@withLock false
        if (session.uncertainAlignmentNoticeEmitted) return@withLock false
        val targetId = session.activeTargetId ?: return@withLock false
        val state = getBridge.await(session.bridgeKey) ?: return@withLock false
        AlternateSourceBridgePolicy.projectConflicts(state.mappings).singleOrNull {
            it.key.bridge == session.bridgeKey &&
                it.key.targetId == targetId &&
                it.deletedAt == null
        }?.state == AlternateSourceBridgeMappingState.PROVISIONAL
    }

    suspend fun acknowledgeUncertainAlignmentNotice(): Boolean = commandMutex.withLock {
        val current = mutableState.value
        val session = current.session ?: return@withLock false
        if (session.uncertainAlignmentNoticeEmitted) return@withLock false
        val targetId = session.activeTargetId ?: return@withLock false
        val state = getBridge.await(session.bridgeKey) ?: return@withLock false
        val provisional = AlternateSourceBridgePolicy.projectConflicts(state.mappings).singleOrNull {
            it.key.bridge == session.bridgeKey &&
                it.key.targetId == targetId &&
                it.deletedAt == null
        }?.state == AlternateSourceBridgeMappingState.PROVISIONAL
        if (!provisional) return@withLock false
        val acknowledged = session.copy(uncertainAlignmentNoticeEmitted = true)
        if (!AlternateSourceReaderSession.isValid(acknowledged)) return@withLock false
        sessionStore.write(acknowledged)
        mutableState.value = current.copy(session = acknowledged)
        true
    }

    fun dismiss() {
        dismissed.set(true)
        finalizeDismissal()
    }

    private suspend fun enterLocked(
        request: AlternateSourceReaderEntryRequest,
        confirmProvisional: Boolean,
    ): AlternateSourceReaderCommandResult {
        if (!validRequest(request)) return AlternateSourceReaderCommandResult.Invalid
        if (!isPairConfirmed(request.bridgeKey)) {
            return AlternateSourceReaderCommandResult.RequiresPairConfirmation
        }
        val bridge = getBridge.await(request.bridgeKey)
        val decision = AlternateSourceReaderBridgePolicy.resolveEntry(
            bridge,
            request.precedingPrimaryChapterUrl,
            request.followingPrimaryChapterUrl,
            clock(),
        )
        val target = when (decision) {
            is AlternateSourceReaderBridgePolicy.EntryDecision.Confirmed -> decision
            is AlternateSourceReaderBridgePolicy.EntryDecision.RequiresConfirmation -> {
                if (!confirmProvisional) {
                    return AlternateSourceReaderCommandResult.RequiresProvisionalConfirmation(decision.targetId)
                }
                AlternateSourceReaderBridgePolicy.EntryDecision.Confirmed(
                    decision.targetId,
                    decision.alternateChapterUrl,
                )
            }
            AlternateSourceReaderBridgePolicy.EntryDecision.Conflict ->
                return AlternateSourceReaderCommandResult.Conflict
            AlternateSourceReaderBridgePolicy.EntryDecision.Unavailable ->
                return AlternateSourceReaderCommandResult.Unavailable
        }
        val generation = begin(AlternateSourceReaderEvent.BeginEntry)
            ?: return AlternateSourceReaderCommandResult.Stale
        val alternate = resolve(
            request.bridgeKey,
            AlternateSourceReaderRouteRole.ALTERNATE,
            target.alternateChapterUrl,
            0,
        ) ?: run {
            fail(generation, AlternateSourceReaderFailureReason.ROUTE_UNAVAILABLE, null)
            return AlternateSourceReaderCommandResult.Unavailable
        }
        val current = requireNotNull(bridge)
        val mapping = AlternateSourceBridgePolicy.projectConflicts(current.mappings).singleOrNull {
            it.key.bridge == request.bridgeKey &&
                it.key.targetId == target.targetId &&
                it.deletedAt == null &&
                it.state != AlternateSourceBridgeMappingState.CONFLICT
        } ?: return cancel(generation, AlternateSourceReaderCommandResult.Conflict)
        val pending = AlternateSourceReaderSession.createPendingEntry(
            bridgeKey = request.bridgeKey,
            primaryResumeRoute = request.primaryRoute,
            alternateRoute = alternate,
            activeTargetId = target.targetId,
            bridgeUpdatedAt = current.bridge.updatedAt,
            mappingUpdatedAt = mapping.updatedAt,
        )
        sessionStore.write(pending)
        mutableState.value = mutableState.value.copy(session = pending)
        if (!routeSwitcher.switchTo(alternate)) {
            fail(generation, AlternateSourceReaderFailureReason.ROUTE_UNAVAILABLE, pending)
            return AlternateSourceReaderCommandResult.Failed
        }
        val committed = AlternateSourceReaderRoutePolicy.commit(
            pending,
            alternate,
            AlternateSourceReaderTransitionReason.GAP_ENTRY,
        ) as? AlternateSourceReaderRoutePolicy.CommitResult.Applied
            ?: return cancel(generation, AlternateSourceReaderCommandResult.Stale)
        sessionStore.write(committed.session)
        reduce(AlternateSourceReaderEvent.EntryResolved(generation, committed.session))
        return AlternateSourceReaderCommandResult.Entered(target.targetId)
    }

    private suspend fun applyMutation(
        generation: Long,
        session: AlternateSourceReaderSession,
        build: AlternateSourceReaderMutationPolicy.Result,
        mutation: AlternateSourceBridgeMutation,
        clearTarget: Boolean,
        success: AlternateSourceReaderCommandResult,
    ): AlternateSourceReaderCommandResult {
        val replacement = when (build) {
            is AlternateSourceReaderMutationPolicy.Result.Replacement -> build
            AlternateSourceReaderMutationPolicy.Result.Conflict ->
                return cancel(generation, AlternateSourceReaderCommandResult.Conflict)
            AlternateSourceReaderMutationPolicy.Result.Unavailable ->
                return cancel(generation, AlternateSourceReaderCommandResult.Unavailable)
            AlternateSourceReaderMutationPolicy.Result.Invalid ->
                return cancel(generation, AlternateSourceReaderCommandResult.Invalid)
            AlternateSourceReaderMutationPolicy.Result.Unchanged ->
                return cancel(generation, AlternateSourceReaderCommandResult.Stale)
        }
        when (bridgeController.apply(replacement.value, mutation)) {
            AlternateSourceBridgeMutationResult.CONFLICT ->
                return cancel(generation, AlternateSourceReaderCommandResult.Conflict)
            AlternateSourceBridgeMutationResult.FAILED ->
                return cancel(generation, AlternateSourceReaderCommandResult.Failed)
            AlternateSourceBridgeMutationResult.UNCHANGED ->
                return cancel(generation, AlternateSourceReaderCommandResult.Stale)
            AlternateSourceBridgeMutationResult.APPLIED -> Unit
        }
        val refreshed = getBridge.await(session.bridgeKey)
            ?: return cancel(generation, AlternateSourceReaderCommandResult.Stale)
        val expectedMapping = refreshed.mappings.singleOrNull {
            it.key.targetId == replacement.targetId && it.deletedAt == null
        } ?: return cancel(generation, AlternateSourceReaderCommandResult.Stale)
        val updated = session.copy(
            activeTargetId = if (clearTarget) null else replacement.targetId,
            bridgeUpdatedAt = refreshed.bridge.updatedAt,
            mappingUpdatedAt = expectedMapping.updatedAt,
            pendingRouteFingerprint = null,
        )
        if (!AlternateSourceReaderSession.isValid(updated)) {
            return cancel(generation, AlternateSourceReaderCommandResult.Invalid)
        }
        sessionStore.write(updated)
        reduce(AlternateSourceReaderEvent.CorrectionResolved(generation, updated))
        return success
    }

    private suspend fun returnLocked(
        session: AlternateSourceReaderSession,
        destination: AlternateSourceReaderRoute,
        generation: Long,
        explicitManualReturn: Boolean,
    ): AlternateSourceReaderCommandResult {
        val prepared = when (
            val result = AlternateSourceReaderRoutePolicy.prepare(session, destination, explicitManualReturn)
        ) {
            is AlternateSourceReaderRoutePolicy.PrepareResult.Allowed -> result.session
            AlternateSourceReaderRoutePolicy.PrepareResult.ImmediateAutomaticReversal -> {
                cancel(generation, AlternateSourceReaderCommandResult.LoopRejected)
                return AlternateSourceReaderCommandResult.LoopRejected
            }
            AlternateSourceReaderRoutePolicy.PrepareResult.SameRoute,
            AlternateSourceReaderRoutePolicy.PrepareResult.TransitionLimitReached,
            -> {
                cancel(generation, AlternateSourceReaderCommandResult.LoopRejected)
                return AlternateSourceReaderCommandResult.LoopRejected
            }
            AlternateSourceReaderRoutePolicy.PrepareResult.InvalidSession -> {
                cancel(generation, AlternateSourceReaderCommandResult.Invalid)
                return AlternateSourceReaderCommandResult.Invalid
            }
        }
        sessionStore.write(prepared)
        mutableState.value = mutableState.value.copy(session = prepared)
        if (!routeSwitcher.switchTo(destination)) {
            fail(generation, AlternateSourceReaderFailureReason.ROUTE_UNAVAILABLE, prepared)
            return AlternateSourceReaderCommandResult.Failed
        }
        val reason = if (explicitManualReturn) {
            AlternateSourceReaderTransitionReason.MANUAL_RETURN
        } else {
            AlternateSourceReaderTransitionReason.AUTOMATIC_RETURN
        }
        val committed = AlternateSourceReaderRoutePolicy.commit(prepared, destination, reason)
        if (committed !is AlternateSourceReaderRoutePolicy.CommitResult.Applied) {
            return cancel(generation, AlternateSourceReaderCommandResult.Stale)
        }
        sessionStore.clear()
        reduce(AlternateSourceReaderEvent.ReturnResolved(generation))
        return AlternateSourceReaderCommandResult.Returned
    }

    private suspend fun resolve(
        key: AlternateSourceBridgeKey,
        role: AlternateSourceReaderRouteRole,
        chapterUrl: String,
        pageIndex: Int,
    ): AlternateSourceReaderRoute? {
        val first = routeResolver.resolve(key, role, chapterUrl, pageIndex)
        val result = if (first == AlternateSourceReaderRouteResolver.Result.Failed) {
            routeResolver.resolve(key, role, chapterUrl, pageIndex)
        } else {
            first
        }
        return (result as? AlternateSourceReaderRouteResolver.Result.Resolved)?.route
    }

    private suspend fun isPairConfirmed(key: AlternateSourceBridgeKey): Boolean =
        identityResolver.isConfirmed(
            key.primary.source,
            key.primary.url,
            key.alternate.source,
            key.alternate.url,
        )

    private fun continuedSession(
        session: AlternateSourceReaderSession,
        route: AlternateSourceReaderRoute,
    ): AlternateSourceReaderSession? =
        (
            AlternateSourceReaderRoutePolicy.continueAlternate(session, route)
                as? AlternateSourceReaderRoutePolicy.ContinueResult.Applied
            )?.session

    private fun activeSession(): AlternateSourceReaderSession? =
        mutableState.value.session?.takeIf {
            mutableState.value.phase in setOf(AlternateSourceReaderPhase.ALTERNATE, AlternateSourceReaderPhase.DEGRADED)
        }

    private fun isCurrentSessionState(
        state: AlternateSourceBridgeState?,
        session: AlternateSourceReaderSession,
    ): Boolean {
        if (
            state == null ||
            state.bridge.key != session.bridgeKey ||
            state.bridge.updatedAt != session.bridgeUpdatedAt ||
            state.bridge.deletedAt != null ||
            state.bridge.version != AlternateSourceBridgePolicy.CURRENT_VERSION ||
            !AlternateSourceBridgePolicy.isValid(state.bridge, clock())
        ) {
            return false
        }
        val target = session.activeTargetId ?: return true
        val mappings = AlternateSourceBridgePolicy.projectConflicts(state.mappings).filter {
            it.key.bridge == session.bridgeKey &&
                it.key.targetId == target &&
                it.deletedAt == null &&
                it.updatedAt == session.mappingUpdatedAt &&
                AlternateSourceBridgePolicy.isValid(it, clock())
        }
        return mappings.singleOrNull()?.state != AlternateSourceBridgeMappingState.CONFLICT
    }

    private fun validRequest(request: AlternateSourceReaderEntryRequest): Boolean =
        AlternateSourceBridgePolicy.isValidKey(request.bridgeKey) &&
            request.primaryRoute.role == AlternateSourceReaderRouteRole.PRIMARY &&
            request.primaryRoute.record == request.bridgeKey.primary &&
            request.primaryRoute.chapterUrl == request.precedingPrimaryChapterUrl &&
            request.followingPrimaryChapterUrl?.isNotBlank() != false &&
            request.followingPrimaryChapterUrl?.length?.let { it > AlternateSourceBridgePolicy.MAX_URL_LENGTH } != true &&
            AlternateSourceReaderSession.isValidRoute(request.primaryRoute, request.bridgeKey)

    private fun begin(event: AlternateSourceReaderEvent): Long? {
        val reduction = reduce(event)
        return reduction.takeIf { it.applied }?.state?.generation
    }

    private fun fail(
        generation: Long,
        reason: AlternateSourceReaderFailureReason,
        session: AlternateSourceReaderSession?,
    ) {
        val failed = session?.let {
            when (val result = AlternateSourceReaderRoutePolicy.fail(it)) {
                is AlternateSourceReaderRoutePolicy.FailureResult.RetryAvailable -> result.session
                is AlternateSourceReaderRoutePolicy.FailureResult.AutomationEnded -> result.session
                AlternateSourceReaderRoutePolicy.FailureResult.InvalidSession -> null
            }
        }
        failed?.let(sessionStore::write)
        if (failed != null) mutableState.value = mutableState.value.copy(session = failed)
        reduce(
            AlternateSourceReaderEvent.Failed(
                generation,
                if (failed?.failedResolutionCount == AlternateSourceReaderSession.MAX_FAILED_RESOLUTIONS) {
                    AlternateSourceReaderFailureReason.RETRY_EXHAUSTED
                } else {
                    reason
                },
            ),
        )
    }

    private fun cancel(
        generation: Long,
        result: AlternateSourceReaderCommandResult,
    ): AlternateSourceReaderCommandResult {
        reduce(AlternateSourceReaderEvent.Cancelled(generation))
        return result
    }

    private fun clearInvalidRestoredState() {
        sessionStore.clear()
        mutableState.value = AlternateSourceReaderMachineState(
            phase = AlternateSourceReaderPhase.DEGRADED,
            failureReason = AlternateSourceReaderFailureReason.INVALID_RESTORED_STATE,
        )
    }

    private fun reduce(event: AlternateSourceReaderEvent): AlternateSourceReaderReduction {
        val reduction = AlternateSourceReaderStateMachine.reduce(mutableState.value, event)
        if (reduction.applied) mutableState.value = reduction.state
        return reduction
    }

    private suspend fun guarded(
        block: suspend () -> AlternateSourceReaderCommandResult,
    ): AlternateSourceReaderCommandResult = commandMutex.withLock {
        if (dismissed.get()) return@withLock AlternateSourceReaderCommandResult.NoSession
        try {
            val result = block()
            if (dismissed.get()) {
                finalizeDismissal()
                AlternateSourceReaderCommandResult.NoSession
            } else {
                result
            }
        } catch (e: CancellationException) {
            if (dismissed.get()) finalizeDismissal() else cancelResolvingCommand()
            throw e
        } catch (e: Error) {
            if (dismissed.get()) finalizeDismissal() else cancelResolvingCommand()
            throw e
        } catch (_: Exception) {
            if (dismissed.get()) {
                finalizeDismissal()
                return@withLock AlternateSourceReaderCommandResult.NoSession
            }
            val state = mutableState.value
            if (
                state.phase in setOf(
                    AlternateSourceReaderPhase.RESOLVING_ENTRY,
                    AlternateSourceReaderPhase.RESOLVING_CORRECTION,
                    AlternateSourceReaderPhase.RETURNING,
                )
            ) {
                fail(state.generation, AlternateSourceReaderFailureReason.ROUTE_UNAVAILABLE, state.session)
            }
            AlternateSourceReaderCommandResult.Failed
        }
    }

    private fun cancelResolvingCommand() {
        val current = mutableState.value
        if (current.phase !in setOf(
                AlternateSourceReaderPhase.RESOLVING_ENTRY,
                AlternateSourceReaderPhase.RESOLVING_CORRECTION,
                AlternateSourceReaderPhase.RETURNING,
            )
        ) {
            return
        }
        val session = current.session
        if (session?.pendingRouteFingerprint != null) {
            if (session.currentRoute.role == AlternateSourceReaderRouteRole.PRIMARY) {
                sessionStore.clear()
                mutableState.value = current.copy(session = null)
            } else {
                val restored = session.copy(pendingRouteFingerprint = null)
                sessionStore.write(restored)
                mutableState.value = current.copy(session = restored)
            }
        }
        reduce(AlternateSourceReaderEvent.Cancelled(current.generation))
    }

    private fun finalizeDismissal() {
        sessionStore.clear()
        val current = mutableState.value
        mutableState.value = AlternateSourceReaderMachineState(
            phase = AlternateSourceReaderPhase.ENDED,
            generation = current.generation,
        )
    }
}
