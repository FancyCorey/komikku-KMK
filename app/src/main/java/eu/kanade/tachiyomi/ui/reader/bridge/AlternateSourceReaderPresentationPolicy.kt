package eu.kanade.tachiyomi.ui.reader.bridge

@JvmInline
value class AlternateSourceReaderOpaqueToken(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

data class AlternateSourceReaderCandidateRow(
    val token: AlternateSourceReaderOpaqueToken,
    val primaryLabel: String? = null,
    val secondaryLabel: String? = null,
    val genericLabel: AlternateSourceReaderGenericLabel? = null,
) {
    init {
        require((primaryLabel != null) xor (genericLabel != null))
        require(genericLabel == null || secondaryLabel == null)
    }
}

sealed interface AlternateSourceReaderLoadState<out T> {
    data object Loading : AlternateSourceReaderLoadState<Nothing>
    data object Empty : AlternateSourceReaderLoadState<Nothing>
    data class Content<T>(
        val items: List<T>,
        val hasPartialFailure: Boolean,
    ) : AlternateSourceReaderLoadState<T> {
        init {
            require(items.isNotEmpty())
        }
    }
    data class Failed(val reason: AlternateSourceReaderRecoveryReason) : AlternateSourceReaderLoadState<Nothing>
}

enum class AlternateSourceReaderResolvingPurpose {
    ENTRY,
    CORRECTION,
    RETURN,
}

enum class AlternateSourceReaderRecoveryReason {
    ROUTE_UNAVAILABLE,
    ROUTE_CHANGED,
    CONFLICT,
    RETRIES_EXHAUSTED,
    LOOP_STOPPED,
    INVALID_SESSION,
    FAILED,
}

enum class AlternateSourceReaderNotice {
    UNCERTAIN_ALIGNMENT,
    CORRECTED,
    SKIPPED,
    RETURNED,
    SESSION_ENDED,
}

enum class AlternateSourceReaderGenericLabelKind {
    SOURCE,
    CHAPTER,
}

data class AlternateSourceReaderGenericLabel(
    val kind: AlternateSourceReaderGenericLabelKind,
    val ordinal: Int,
) {
    init {
        require(ordinal > 0)
    }
}

sealed interface AlternateSourceReaderPresentation {
    data object Hidden : AlternateSourceReaderPresentation
    data class ChoosingSource(
        val content: AlternateSourceReaderLoadState<AlternateSourceReaderCandidateRow>,
        val selectedToken: AlternateSourceReaderOpaqueToken? = null,
    ) : AlternateSourceReaderPresentation
    data class ChoosingChapter(
        val sourceToken: AlternateSourceReaderOpaqueToken,
        val content: AlternateSourceReaderLoadState<AlternateSourceReaderCandidateRow>,
        val selectedToken: AlternateSourceReaderOpaqueToken? = null,
    ) : AlternateSourceReaderPresentation
    data class ConfirmPair(val requestToken: AlternateSourceReaderOpaqueToken) : AlternateSourceReaderPresentation
    data class ConfirmProvisional(val requestToken: AlternateSourceReaderOpaqueToken) : AlternateSourceReaderPresentation
    data object ConfirmSkip : AlternateSourceReaderPresentation
    data class Resolving(val purpose: AlternateSourceReaderResolvingPurpose) : AlternateSourceReaderPresentation
    data class RecoverableFailure(
        val reason: AlternateSourceReaderRecoveryReason,
        val canRetry: Boolean,
        val invalidateTokens: Boolean,
    ) : AlternateSourceReaderPresentation
}

data class AlternateSourceReaderContextActions(
    val returnToPrimary: Boolean = false,
    val correctMapping: Boolean = false,
    val skipChapter: Boolean = false,
    val resolving: Boolean = false,
    val degradedReason: AlternateSourceReaderRecoveryReason? = null,
)

sealed interface AlternateSourceReaderResultPresentation {
    data object Silent : AlternateSourceReaderResultPresentation
    data class ConfirmPair(val invalidateTokens: Boolean = false) : AlternateSourceReaderResultPresentation
    data class ConfirmProvisional(val invalidateTokens: Boolean = false) : AlternateSourceReaderResultPresentation
    data class Completed(val notice: AlternateSourceReaderNotice?) : AlternateSourceReaderResultPresentation
    data class Recoverable(
        val reason: AlternateSourceReaderRecoveryReason,
        val canRetry: Boolean,
        val invalidateTokens: Boolean,
    ) : AlternateSourceReaderResultPresentation
}

object AlternateSourceReaderPresentationPolicy {

    fun mangaCandidateRow(
        token: AlternateSourceReaderOpaqueToken,
        title: String,
        sourceLabel: String?,
        evaluationSourceLabel: String,
        evaluationModeEnabled: Boolean,
    ): AlternateSourceReaderCandidateRow = AlternateSourceReaderCandidateRow(
        token = token,
        primaryLabel = title,
        secondaryLabel = if (evaluationModeEnabled) evaluationSourceLabel else sourceLabel,
    )

    fun chapterCandidateRow(
        token: AlternateSourceReaderOpaqueToken,
        name: String,
        chapterNumber: Float,
    ): AlternateSourceReaderCandidateRow = AlternateSourceReaderCandidateRow(
        token = token,
        primaryLabel = name,
        secondaryLabel = chapterNumber.takeIf { it >= 0f }?.toString(),
    )

    fun contextActions(state: AlternateSourceReaderMachineState): AlternateSourceReaderContextActions =
        when (state.phase) {
            AlternateSourceReaderPhase.PRIMARY,
            AlternateSourceReaderPhase.ENDED,
            -> AlternateSourceReaderContextActions()
            AlternateSourceReaderPhase.RESOLVING_ENTRY,
            AlternateSourceReaderPhase.RESOLVING_CORRECTION,
            AlternateSourceReaderPhase.RETURNING,
            -> AlternateSourceReaderContextActions(resolving = true)
            AlternateSourceReaderPhase.ALTERNATE -> AlternateSourceReaderContextActions(
                returnToPrimary = state.session != null,
                correctMapping = state.session != null,
                skipChapter = state.session != null,
            )
            AlternateSourceReaderPhase.DEGRADED -> if (state.session == null) {
                AlternateSourceReaderContextActions()
            } else {
                AlternateSourceReaderContextActions(
                    returnToPrimary = true,
                    degradedReason = state.failureReason?.toRecoveryReason(),
                )
            }
        }

    fun result(
        result: AlternateSourceReaderCommandResult,
        explicitUserCommand: Boolean,
    ): AlternateSourceReaderResultPresentation = when (result) {
        is AlternateSourceReaderCommandResult.Entered -> AlternateSourceReaderResultPresentation.Completed(null)
        is AlternateSourceReaderCommandResult.RequiresProvisionalConfirmation ->
            AlternateSourceReaderResultPresentation.ConfirmProvisional()
        AlternateSourceReaderCommandResult.RequiresPairConfirmation ->
            AlternateSourceReaderResultPresentation.ConfirmPair()
        AlternateSourceReaderCommandResult.PairConfirmed -> AlternateSourceReaderResultPresentation.Completed(null)
        AlternateSourceReaderCommandResult.Corrected ->
            AlternateSourceReaderResultPresentation.Completed(AlternateSourceReaderNotice.CORRECTED)
        AlternateSourceReaderCommandResult.Skipped ->
            AlternateSourceReaderResultPresentation.Completed(AlternateSourceReaderNotice.SKIPPED)
        AlternateSourceReaderCommandResult.Returned ->
            AlternateSourceReaderResultPresentation.Completed(AlternateSourceReaderNotice.RETURNED)
        AlternateSourceReaderCommandResult.Continued,
        AlternateSourceReaderCommandResult.Restored,
        AlternateSourceReaderCommandResult.NotAtBoundary,
        -> AlternateSourceReaderResultPresentation.Silent
        AlternateSourceReaderCommandResult.NoSession -> if (explicitUserCommand) {
            AlternateSourceReaderResultPresentation.Completed(AlternateSourceReaderNotice.SESSION_ENDED)
        } else {
            AlternateSourceReaderResultPresentation.Silent
        }
        AlternateSourceReaderCommandResult.Unavailable -> AlternateSourceReaderResultPresentation.Recoverable(
            reason = AlternateSourceReaderRecoveryReason.ROUTE_UNAVAILABLE,
            canRetry = true,
            invalidateTokens = false,
        )
        AlternateSourceReaderCommandResult.Invalid -> AlternateSourceReaderResultPresentation.Recoverable(
            reason = AlternateSourceReaderRecoveryReason.INVALID_SESSION,
            canRetry = false,
            invalidateTokens = true,
        )
        AlternateSourceReaderCommandResult.Conflict -> AlternateSourceReaderResultPresentation.Recoverable(
            reason = AlternateSourceReaderRecoveryReason.CONFLICT,
            canRetry = true,
            invalidateTokens = true,
        )
        AlternateSourceReaderCommandResult.Stale -> AlternateSourceReaderResultPresentation.Recoverable(
            reason = AlternateSourceReaderRecoveryReason.ROUTE_CHANGED,
            canRetry = true,
            invalidateTokens = true,
        )
        AlternateSourceReaderCommandResult.LoopRejected -> AlternateSourceReaderResultPresentation.Recoverable(
            reason = AlternateSourceReaderRecoveryReason.LOOP_STOPPED,
            canRetry = false,
            invalidateTokens = false,
        )
        AlternateSourceReaderCommandResult.Failed -> AlternateSourceReaderResultPresentation.Recoverable(
            reason = AlternateSourceReaderRecoveryReason.FAILED,
            canRetry = true,
            invalidateTokens = false,
        )
    }

    private fun AlternateSourceReaderFailureReason.toRecoveryReason(): AlternateSourceReaderRecoveryReason =
        when (this) {
            AlternateSourceReaderFailureReason.ROUTE_UNAVAILABLE -> AlternateSourceReaderRecoveryReason.ROUTE_UNAVAILABLE
            AlternateSourceReaderFailureReason.STALE_BRIDGE -> AlternateSourceReaderRecoveryReason.ROUTE_CHANGED
            AlternateSourceReaderFailureReason.CONFLICT -> AlternateSourceReaderRecoveryReason.CONFLICT
            AlternateSourceReaderFailureReason.RETRY_EXHAUSTED -> AlternateSourceReaderRecoveryReason.RETRIES_EXHAUSTED
            AlternateSourceReaderFailureReason.LOOP_REJECTED -> AlternateSourceReaderRecoveryReason.LOOP_STOPPED
            AlternateSourceReaderFailureReason.INVALID_RESTORED_STATE -> AlternateSourceReaderRecoveryReason.INVALID_SESSION
        }
}
