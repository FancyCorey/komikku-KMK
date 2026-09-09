package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.tachiyomi.data.track.Tracker
import exh.util.TrackWriteField
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.i18n.MR
import kotlin.math.abs

/** Pure, capability-aware metadata projection used by the explicit reconciliation UI. */
internal object LocalTrackingReconciliationPolicy {
    private const val LOCAL_SCORE_MIN = 1.0
    private const val LOCAL_SCORE_MAX = 100.0

    /** Keeps an explicit retry scoped to the fields that failed in the previous remote attempt. */
    fun fieldsToAttempt(
        applicableFields: Set<TrackWriteField>,
        pendingFields: Set<TrackWriteField>?,
    ): Set<TrackWriteField> = pendingFields ?: applicableFields

    fun completedFieldCount(attemptedFieldCount: Int, failedFields: Set<TrackWriteField>): Int =
        (attemptedFieldCount - failedFields.size).coerceAtLeast(0)

    fun externalSnapshot(track: Track, tracker: Tracker): LocalTrackedWork.Metadata =
        LocalTrackedWork.Metadata(
            chapterNumber = track.lastChapterRead.takeIf { it >= 0.0 },
            score = tracker.getScoreList()
                .takeIf { it.isNotEmpty() }
                ?.let { tracker.get10PointScore(track) }
                ?.takeIf { it.isFinite() && it in 0.1..10.0 }
                ?.times(10.0),
            startDate = track.startDate.takeIf { tracker.supportsReadingDates && it > 0L },
            finishDate = track.finishDate.takeIf { tracker.supportsReadingDates && it > 0L },
            status = when {
                track.status == tracker.getReadingStatus() -> LocalTrackedWorkStatus.READING
                track.status == tracker.getCompletionStatus() -> LocalTrackedWorkStatus.COMPLETED
                tracker.hasNotStartedReading(track.status) -> LocalTrackedWorkStatus.PLANNED
                tracker.statusesWith(MR.strings.on_hold).contains(track.status) -> LocalTrackedWorkStatus.ON_HOLD
                tracker.statusesWith(MR.strings.dropped).contains(track.status) -> LocalTrackedWorkStatus.DROPPED
                else -> null
            },
            presentFields = buildSet {
                add(LocalTrackedWork.Metadata.Field.CHAPTER)
                if (tracker.getScoreList().isNotEmpty()) add(LocalTrackedWork.Metadata.Field.SCORE)
                if (tracker.supportsReadingDates) {
                    add(LocalTrackedWork.Metadata.Field.START_DATE)
                    add(LocalTrackedWork.Metadata.Field.FINISH_DATE)
                }
                if (track.status == tracker.getReadingStatus() ||
                    track.status == tracker.getCompletionStatus() ||
                    tracker.hasNotStartedReading(track.status) ||
                    tracker.statusesWith(MR.strings.on_hold).contains(track.status) ||
                    tracker.statusesWith(MR.strings.dropped).contains(track.status)
                ) {
                    add(LocalTrackedWork.Metadata.Field.STATUS)
                }
            },
        )

    fun mergeIntoLocal(work: LocalTrackedWork, metadata: LocalTrackedWork.Metadata): LocalTrackedWork =
        work.copy(
            status = if (metadata.presentFields.isEmpty()) {
                metadata.status ?: work.status
            } else if (LocalTrackedWork.Metadata.Field.STATUS in metadata.presentFields) {
                metadata.status ?: work.status
            } else {
                work.status
            },
            lastChapterNumber = metadata.valueOrExisting(LocalTrackedWork.Metadata.Field.CHAPTER, metadata.chapterNumber, work.lastChapterNumber),
            score = metadata.valueOrExisting(LocalTrackedWork.Metadata.Field.SCORE, metadata.score, work.score),
            startDate = metadata.valueOrExisting(LocalTrackedWork.Metadata.Field.START_DATE, metadata.startDate, work.startDate),
            finishDate = metadata.valueOrExisting(LocalTrackedWork.Metadata.Field.FINISH_DATE, metadata.finishDate, work.finishDate),
            updatedAt = System.currentTimeMillis(),
        )

    private fun <T> LocalTrackedWork.Metadata.valueOrExisting(
        field: LocalTrackedWork.Metadata.Field,
        value: T?,
        existing: T?,
    ): T? = if (presentFields.isEmpty()) {
        value ?: existing
    } else if (field in presentFields) {
        value
    } else {
        existing
    }

    fun externalStatus(tracker: Tracker, status: LocalTrackedWorkStatus): Long? = when (status) {
        LocalTrackedWorkStatus.READING -> tracker.getReadingStatus()
        LocalTrackedWorkStatus.COMPLETED -> tracker.getCompletionStatus()
        LocalTrackedWorkStatus.PLANNED -> tracker.getStatusList().firstOrNull(tracker::hasNotStartedReading)
        LocalTrackedWorkStatus.ON_HOLD -> tracker.statusesWith(MR.strings.on_hold).firstOrNull()
        LocalTrackedWorkStatus.DROPPED -> tracker.statusesWith(MR.strings.dropped).firstOrNull()
    }

    private fun Tracker.statusesWith(resource: dev.icerock.moko.resources.StringResource): List<Long> =
        getStatusList().filter { getStatus(it) == resource }

    fun scoreString(tracker: Tracker, score: Double): String? {
        if (score !in LOCAL_SCORE_MIN..LOCAL_SCORE_MAX || !score.isFinite()) return null
        val scores = tracker.getScoreList().mapIndexedNotNull { index, value ->
            val rawScore = tracker.indexToScore(index)
            rawScore.takeIf { it.isFinite() && it > 0.0 }?.let { value to it }
        }
        val maximum = scores.maxOfOrNull { it.second } ?: return null
        val target = score / LOCAL_SCORE_MAX * 10.0
        return scores.minByOrNull { (_, rawScore) -> abs(rawScore / maximum * 10.0 - target) }?.first
    }

    /** Trackers represent an explicitly unrated score with the first score-list value. */
    fun clearScoreString(tracker: Tracker): String? = tracker.getScoreList().firstOrNull()
}
