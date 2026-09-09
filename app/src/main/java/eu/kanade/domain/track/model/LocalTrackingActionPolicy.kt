package eu.kanade.domain.track.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.i18n.MR

object LocalTrackingActionPolicy {
    enum class Action { TRACK, REMOVE, RESUME }

    fun resolve(work: LocalTrackedWork?): Action = when {
        work == null -> Action.TRACK
        work.status == LocalTrackedWorkStatus.READING -> Action.REMOVE
        else -> Action.RESUME
    }

    /** The list of local-tracking statuses offered by the status/list workflow, in display order. */
    val statusOrder: List<LocalTrackedWorkStatus> = listOf(
        LocalTrackedWorkStatus.READING,
        LocalTrackedWorkStatus.PLANNED,
        LocalTrackedWorkStatus.ON_HOLD,
        LocalTrackedWorkStatus.COMPLETED,
        LocalTrackedWorkStatus.DROPPED,
    )

    fun statusLabel(status: LocalTrackedWorkStatus): StringResource = when (status) {
        LocalTrackedWorkStatus.READING -> MR.strings.reading
        LocalTrackedWorkStatus.PLANNED -> MR.strings.plan_to_read
        LocalTrackedWorkStatus.ON_HOLD -> MR.strings.on_hold
        LocalTrackedWorkStatus.COMPLETED -> MR.strings.completed
        LocalTrackedWorkStatus.DROPPED -> MR.strings.dropped
    }

    /** A finish date describes the current Completed state, not historical completion. */
    fun finishDateForStatus(status: LocalTrackedWorkStatus, now: Long): Long? =
        now.takeIf { status == LocalTrackedWorkStatus.COMPLETED }
}
