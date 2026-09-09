package eu.kanade.tachiyomi.ui.reader.bridge

object AlternateSourceReaderRoutePolicy {

    sealed interface PrepareResult {
        data class Allowed(val session: AlternateSourceReaderSession) : PrepareResult
        data object SameRoute : PrepareResult
        data object ImmediateAutomaticReversal : PrepareResult
        data object TransitionLimitReached : PrepareResult
        data object InvalidSession : PrepareResult
    }

    sealed interface CommitResult {
        data class Applied(val session: AlternateSourceReaderSession) : CommitResult
        data object PendingRouteMismatch : CommitResult
        data object InvalidSession : CommitResult
    }

    sealed interface FailureResult {
        data class RetryAvailable(val session: AlternateSourceReaderSession) : FailureResult
        data class AutomationEnded(val session: AlternateSourceReaderSession) : FailureResult
        data object InvalidSession : FailureResult
    }

    sealed interface ContinueResult {
        data class Applied(val session: AlternateSourceReaderSession) : ContinueResult
        data object WrongRole : ContinueResult
        data object IdentityMismatch : ContinueResult
        data object InvalidSession : ContinueResult
    }

    fun prepare(
        session: AlternateSourceReaderSession,
        destination: AlternateSourceReaderRoute,
        explicitManualReturn: Boolean,
    ): PrepareResult {
        if (!AlternateSourceReaderSession.isValid(session)) return PrepareResult.InvalidSession
        if (!AlternateSourceReaderSession.isValidRoute(destination, session.bridgeKey)) return PrepareResult.InvalidSession
        val destinationFingerprint = AlternateSourceReaderRouteFingerprint.of(destination)
        if (destinationFingerprint == session.lastSafeRouteFingerprint) return PrepareResult.SameRoute
        if (session.transitionCount >= AlternateSourceReaderSession.MAX_TRANSITIONS) {
            return PrepareResult.TransitionLimitReached
        }
        if (!explicitManualReturn && destinationFingerprint == session.previousRouteFingerprint) {
            return PrepareResult.ImmediateAutomaticReversal
        }
        return PrepareResult.Allowed(session.copy(pendingRouteFingerprint = destinationFingerprint))
    }

    fun commit(
        session: AlternateSourceReaderSession,
        destination: AlternateSourceReaderRoute,
        reason: AlternateSourceReaderTransitionReason,
    ): CommitResult {
        if (!AlternateSourceReaderSession.isValid(session)) return CommitResult.InvalidSession
        if (!AlternateSourceReaderSession.isValidRoute(destination, session.bridgeKey)) return CommitResult.InvalidSession
        val destinationFingerprint = AlternateSourceReaderRouteFingerprint.of(destination)
        if (session.pendingRouteFingerprint != destinationFingerprint) return CommitResult.PendingRouteMismatch
        val committed = session.copy(
            currentRoute = destination,
            previousRouteFingerprint = session.lastSafeRouteFingerprint,
            lastSafeRouteFingerprint = destinationFingerprint,
            pendingRouteFingerprint = null,
            transitionCount = session.transitionCount + 1,
            failedResolutionCount = 0,
            lastTransitionReason = reason,
        )
        return if (AlternateSourceReaderSession.isValid(committed)) {
            CommitResult.Applied(committed)
        } else {
            CommitResult.InvalidSession
        }
    }

    fun fail(session: AlternateSourceReaderSession): FailureResult {
        if (!AlternateSourceReaderSession.isValid(session)) return FailureResult.InvalidSession
        val failed = session.copy(
            pendingRouteFingerprint = null,
            failedResolutionCount = minOf(
                session.failedResolutionCount + 1,
                AlternateSourceReaderSession.MAX_FAILED_RESOLUTIONS,
            ),
        )
        return if (failed.failedResolutionCount >= AlternateSourceReaderSession.MAX_FAILED_RESOLUTIONS) {
            FailureResult.AutomationEnded(failed)
        } else {
            FailureResult.RetryAvailable(failed)
        }
    }

    fun continueAlternate(
        session: AlternateSourceReaderSession,
        destination: AlternateSourceReaderRoute,
    ): ContinueResult {
        if (!AlternateSourceReaderSession.isValid(session)) return ContinueResult.InvalidSession
        if (!AlternateSourceReaderSession.isValidRoute(destination, session.bridgeKey)) {
            return ContinueResult.InvalidSession
        }
        if (
            session.currentRoute.role != AlternateSourceReaderRouteRole.ALTERNATE ||
            destination.role != AlternateSourceReaderRouteRole.ALTERNATE
        ) {
            return ContinueResult.WrongRole
        }
        if (
            destination.record != session.currentRoute.record ||
            destination.mangaId != session.currentRoute.mangaId
        ) {
            return ContinueResult.IdentityMismatch
        }
        val updated = session.copy(
            currentRoute = destination,
            lastSafeRouteFingerprint = AlternateSourceReaderRouteFingerprint.of(destination),
            pendingRouteFingerprint = null,
            lastTransitionReason = AlternateSourceReaderTransitionReason.CONTINUED_READING,
        )
        return if (AlternateSourceReaderSession.isValid(updated)) {
            ContinueResult.Applied(updated)
        } else {
            ContinueResult.InvalidSession
        }
    }
}

enum class AlternateSourceReaderPhase {
    PRIMARY,
    RESOLVING_ENTRY,
    ALTERNATE,
    RESOLVING_CORRECTION,
    RETURNING,
    DEGRADED,
    ENDED,
}

enum class AlternateSourceReaderFailureReason {
    ROUTE_UNAVAILABLE,
    STALE_BRIDGE,
    CONFLICT,
    RETRY_EXHAUSTED,
    LOOP_REJECTED,
    INVALID_RESTORED_STATE,
}

data class AlternateSourceReaderMachineState(
    val phase: AlternateSourceReaderPhase = AlternateSourceReaderPhase.PRIMARY,
    val generation: Long = 0L,
    val session: AlternateSourceReaderSession? = null,
    val failureReason: AlternateSourceReaderFailureReason? = null,
)

sealed interface AlternateSourceReaderEvent {
    data object BeginEntry : AlternateSourceReaderEvent
    data class EntryResolved(val generation: Long, val session: AlternateSourceReaderSession) : AlternateSourceReaderEvent
    data object BeginCorrection : AlternateSourceReaderEvent
    data class CorrectionResolved(val generation: Long, val session: AlternateSourceReaderSession) : AlternateSourceReaderEvent
    data object BeginReturn : AlternateSourceReaderEvent
    data class ReturnResolved(val generation: Long) : AlternateSourceReaderEvent
    data class Failed(val generation: Long, val reason: AlternateSourceReaderFailureReason) : AlternateSourceReaderEvent
    data class Cancelled(val generation: Long) : AlternateSourceReaderEvent
    data object Dismiss : AlternateSourceReaderEvent
}

data class AlternateSourceReaderReduction(
    val state: AlternateSourceReaderMachineState,
    val applied: Boolean,
)

object AlternateSourceReaderStateMachine {

    fun reduce(
        state: AlternateSourceReaderMachineState,
        event: AlternateSourceReaderEvent,
    ): AlternateSourceReaderReduction = when (event) {
        AlternateSourceReaderEvent.BeginEntry -> {
            if (
                state.phase == AlternateSourceReaderPhase.PRIMARY ||
                (state.phase == AlternateSourceReaderPhase.DEGRADED && state.session == null)
            ) {
                begin(state, AlternateSourceReaderPhase.RESOLVING_ENTRY)
            } else {
                AlternateSourceReaderReduction(state, false)
            }
        }
        AlternateSourceReaderEvent.BeginCorrection -> {
            if (state.phase == AlternateSourceReaderPhase.ALTERNATE && state.session != null) {
                begin(state, AlternateSourceReaderPhase.RESOLVING_CORRECTION)
            } else {
                AlternateSourceReaderReduction(state, false)
            }
        }
        AlternateSourceReaderEvent.BeginReturn -> {
            if (
                state.session != null &&
                state.phase in setOf(AlternateSourceReaderPhase.ALTERNATE, AlternateSourceReaderPhase.DEGRADED)
            ) {
                begin(state, AlternateSourceReaderPhase.RETURNING)
            } else {
                AlternateSourceReaderReduction(state, false)
            }
        }
        is AlternateSourceReaderEvent.EntryResolved -> resolve(
            state = state,
            generation = event.generation,
            expectedPhase = AlternateSourceReaderPhase.RESOLVING_ENTRY,
            session = event.session,
        )
        is AlternateSourceReaderEvent.CorrectionResolved -> resolve(
            state = state,
            generation = event.generation,
            expectedPhase = AlternateSourceReaderPhase.RESOLVING_CORRECTION,
            session = event.session,
        )
        is AlternateSourceReaderEvent.ReturnResolved -> {
            if (state.phase == AlternateSourceReaderPhase.RETURNING && event.generation == state.generation) {
                AlternateSourceReaderReduction(
                    AlternateSourceReaderMachineState(
                        phase = AlternateSourceReaderPhase.ENDED,
                        generation = state.generation,
                    ),
                    true,
                )
            } else {
                AlternateSourceReaderReduction(state, false)
            }
        }
        is AlternateSourceReaderEvent.Failed -> {
            if (event.generation != state.generation || state.phase !in resolvingPhases) {
                AlternateSourceReaderReduction(state, false)
            } else {
                AlternateSourceReaderReduction(
                    state.copy(phase = AlternateSourceReaderPhase.DEGRADED, failureReason = event.reason),
                    true,
                )
            }
        }
        is AlternateSourceReaderEvent.Cancelled -> {
            if (event.generation != state.generation || state.phase !in resolvingPhases) {
                AlternateSourceReaderReduction(state, false)
            } else if (state.session == null) {
                AlternateSourceReaderReduction(
                    AlternateSourceReaderMachineState(
                        phase = AlternateSourceReaderPhase.PRIMARY,
                        generation = state.generation,
                    ),
                    true,
                )
            } else {
                AlternateSourceReaderReduction(
                    state.copy(
                        phase = AlternateSourceReaderPhase.ALTERNATE,
                        failureReason = null,
                    ),
                    true,
                )
            }
        }
        AlternateSourceReaderEvent.Dismiss -> AlternateSourceReaderReduction(
            AlternateSourceReaderMachineState(
                phase = AlternateSourceReaderPhase.ENDED,
                generation = state.generation,
            ),
            true,
        )
    }

    private fun begin(
        state: AlternateSourceReaderMachineState,
        phase: AlternateSourceReaderPhase,
    ): AlternateSourceReaderReduction {
        if (state.generation == Long.MAX_VALUE) return AlternateSourceReaderReduction(state, false)
        return AlternateSourceReaderReduction(
            state.copy(
                phase = phase,
                generation = state.generation + 1L,
                failureReason = null,
            ),
            true,
        )
    }

    private fun resolve(
        state: AlternateSourceReaderMachineState,
        generation: Long,
        expectedPhase: AlternateSourceReaderPhase,
        session: AlternateSourceReaderSession,
    ): AlternateSourceReaderReduction {
        if (
            generation != state.generation ||
            state.phase != expectedPhase ||
            !AlternateSourceReaderSession.isValid(session)
        ) {
            return AlternateSourceReaderReduction(state, false)
        }
        return AlternateSourceReaderReduction(
            state.copy(
                phase = AlternateSourceReaderPhase.ALTERNATE,
                session = session,
                failureReason = null,
            ),
            true,
        )
    }

    private val resolvingPhases = setOf(
        AlternateSourceReaderPhase.RESOLVING_ENTRY,
        AlternateSourceReaderPhase.RESOLVING_CORRECTION,
        AlternateSourceReaderPhase.RETURNING,
    )
}
