package tachiyomi.domain.tracker.model

/** A chapter update whose target identity has already been resolved for its source. */
data class MappedLocalTrackedProgress(
    val targetSource: Long,
    val targetUrl: String,
    val targetChapterUrl: String,
    val targetLabel: String,
    val chapterNumber: Double?,
    val progressAt: Long,
)

enum class LocalTrackedProgressInheritanceDecision {
    APPLY,
    DISABLED,
    UNAUTHORIZED_GROUP,
    OPTED_OUT,
    UNMAPPED,
    OLDER_THAN_TARGET,
    ALREADY_RECORDED,
}

/**
 * Pure conflict policy for optional progress inheritance between confirmed source versions.
 * Mapping and persistence remain the responsibility of their existing owners.
 */
object LocalTrackedProgressInheritancePolicy {

    /**
     * Progress means the user is actively reading again. Preserve an explicit Completed state,
     * but bring paused, dropped, and planned work back to Reading like the existing tracker
     * implementations do when they write chapter progress.
     */
    fun statusAfterProgress(status: LocalTrackedWorkStatus): LocalTrackedWorkStatus = when (status) {
        LocalTrackedWorkStatus.COMPLETED,
        LocalTrackedWorkStatus.READING,
        -> status
        LocalTrackedWorkStatus.ON_HOLD,
        LocalTrackedWorkStatus.DROPPED,
        LocalTrackedWorkStatus.PLANNED,
        -> LocalTrackedWorkStatus.READING
    }

    /** Whether a progress event may advance the aggregate work-level progress. */
    fun acceptsAggregateProgress(
        target: LocalTrackedWork,
        chapterNumber: Double?,
        progressAt: Long,
    ): Boolean = target.lastProgressAt == null ||
        (
            chapterNumber != null && (
                target.lastChapterNumber == null ||
                    chapterNumber > target.lastChapterNumber ||
                    (
                        chapterNumber == target.lastChapterNumber &&
                            progressAt >= target.lastProgressAt
                        )
                )
            ) ||
        (chapterNumber == null && target.lastChapterNumber == null && progressAt >= target.lastProgressAt)

    fun decide(
        enabled: Boolean,
        confirmedGroupMember: Boolean,
        optedOut: Boolean,
        target: LocalTrackedWork,
        update: MappedLocalTrackedProgress?,
    ): LocalTrackedProgressInheritanceDecision = when {
        !enabled -> LocalTrackedProgressInheritanceDecision.DISABLED
        !confirmedGroupMember -> LocalTrackedProgressInheritanceDecision.UNAUTHORIZED_GROUP
        optedOut -> LocalTrackedProgressInheritanceDecision.OPTED_OUT
        update == null || update.targetSource <= 0L || update.targetUrl.isBlank() || update.targetLabel.isBlank() ->
            LocalTrackedProgressInheritanceDecision.UNMAPPED
        target.lastProgressAt != null && update.progressAt < target.lastProgressAt ->
            LocalTrackedProgressInheritanceDecision.OLDER_THAN_TARGET
        target.lastChapterNumber != null &&
            update.chapterNumber != null &&
            update.chapterNumber < target.lastChapterNumber ->
            LocalTrackedProgressInheritanceDecision.OLDER_THAN_TARGET
        target.lastChapterNumber != null &&
            update.chapterNumber != null &&
            update.chapterNumber == target.lastChapterNumber ->
            LocalTrackedProgressInheritanceDecision.ALREADY_RECORDED
        else -> LocalTrackedProgressInheritanceDecision.APPLY
    }

    /** Whether a source-specific progress row may advance independently of the shared work row. */
    fun acceptsSourceProgress(
        existing: LocalTrackedWorkSourceProgress?,
        update: MappedLocalTrackedProgress,
    ): Boolean = when {
        existing == null -> true
        existing.chapterNumber == null && update.chapterNumber == null ->
            update.progressAt >= existing.progressAt
        existing.chapterNumber == null -> true
        update.chapterNumber == null -> false
        update.chapterNumber > existing.chapterNumber -> true
        update.chapterNumber < existing.chapterNumber -> false
        else -> update.progressAt >= existing.progressAt
    }

    fun apply(
        target: LocalTrackedWork,
        update: MappedLocalTrackedProgress,
    ): LocalTrackedWork? = when (
        decide(
            enabled = true,
            confirmedGroupMember = true,
            optedOut = false,
            target = target,
            update = update,
        )
    ) {
        LocalTrackedProgressInheritanceDecision.APPLY -> target.copy(
            status = statusAfterProgress(target.status),
            lastChapterSource = update.targetSource,
            lastChapterNumber = update.chapterNumber,
            lastChapterUrl = update.targetChapterUrl,
            lastChapterLabel = update.targetLabel,
            lastProgressAt = update.progressAt,
            finishDate = target.finishDate.takeIf { statusAfterProgress(target.status) == LocalTrackedWorkStatus.COMPLETED },
            startDate = target.startDate ?: update.progressAt,
            updatedAt = maxOf(target.updatedAt, update.progressAt),
        )
        else -> null
    }
}
