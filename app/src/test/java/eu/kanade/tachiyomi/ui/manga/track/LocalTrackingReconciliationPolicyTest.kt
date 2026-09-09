package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.test.DummyTracker
import exh.util.TrackWriteField
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus

class LocalTrackingReconciliationPolicyTest {

    @Test
    fun `partial retry attempts only fields that failed previously`() {
        val applicable = setOf(
            TrackWriteField.STATUS,
            TrackWriteField.CHAPTER_PROGRESS,
            TrackWriteField.SCORE,
        )
        val failed = setOf(TrackWriteField.STATUS, TrackWriteField.SCORE)

        assertEquals(
            failed,
            LocalTrackingReconciliationPolicy.fieldsToAttempt(applicable, failed),
            "a retry must not repeat a field already confirmed remotely",
        )
    }

    @Test
    fun `offline failure is reported as zero completed fields without negative counts`() {
        assertEquals(
            0,
            LocalTrackingReconciliationPolicy.completedFieldCount(
                attemptedFieldCount = 2,
                failedFields = setOf(TrackWriteField.STATUS, TrackWriteField.SCORE),
            ),
        )
        assertEquals(
            0,
            LocalTrackingReconciliationPolicy.completedFieldCount(
                attemptedFieldCount = 0,
                failedFields = setOf(TrackWriteField.STATUS),
            ),
        )
    }
    private val remote = Track(
        id = 1L,
        mangaId = 2L,
        trackerId = 3L,
        remoteId = 4L,
        libraryId = null,
        title = "Same title",
        lastChapterRead = 12.0,
        totalChapters = 30L,
        status = 1L,
        score = 7.0,
        remoteUrl = "https://example.test/4",
        startDate = 1000L,
        finishDate = 2000L,
        private = false,
    )

    private val local = LocalTrackedWork(
        id = "local",
        title = "Same title",
        normalizedTitle = "same title",
        status = LocalTrackedWorkStatus.READING,
        lastChapterSource = null,
        lastChapterNumber = 3.0,
        lastChapterUrl = null,
        lastChapterLabel = null,
        lastProgressAt = null,
        score = 40.0,
        startDate = null,
        finishDate = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun `external projection respects tracker capabilities`() {
        val metadata = LocalTrackingReconciliationPolicy.externalSnapshot(
            remote,
            DummyTracker(id = 3L, name = "Tracker", supportsReadingDates = false),
        )

        assertEquals(12.0, metadata.chapterNumber)
        assertEquals(54.0, metadata.score)
        assertEquals(null, metadata.startDate)
        assertEquals(null, metadata.finishDate)
    }

    @Test
    fun `external projection omits scores from trackers without scoring`() {
        val metadata = LocalTrackingReconciliationPolicy.externalSnapshot(
            remote,
            DummyTracker(id = 3L, name = "Tracker", valScoreList = emptyList<String>().toImmutableList()),
        )

        assertEquals(null, metadata.score)
    }

    @Test
    fun `merge preserves local values when external field is unavailable`() {
        val merged = LocalTrackingReconciliationPolicy.mergeIntoLocal(
            local,
            LocalTrackedWork.Metadata(chapterNumber = 12.0, score = null, startDate = null, finishDate = null),
        )

        assertEquals(12.0, merged.lastChapterNumber)
        assertEquals(40.0, merged.score)
        assertEquals(null, merged.startDate)
        assertEquals(null, merged.finishDate)
    }

    @Test
    fun `merge clears supported external fields when the tracker has cleared them`() {
        val merged = LocalTrackingReconciliationPolicy.mergeIntoLocal(
            local.copy(startDate = 1_000L, finishDate = 2_000L),
            LocalTrackedWork.Metadata(
                chapterNumber = 12.0,
                score = null,
                startDate = null,
                finishDate = null,
                presentFields = setOf(
                    LocalTrackedWork.Metadata.Field.SCORE,
                    LocalTrackedWork.Metadata.Field.START_DATE,
                    LocalTrackedWork.Metadata.Field.FINISH_DATE,
                ),
            ),
        )

        assertEquals(null, merged.score)
        assertEquals(null, merged.startDate)
        assertEquals(null, merged.finishDate)
    }

    @Test
    fun `score export chooses nearest supported tracker score`() {
        val tracker = DummyTracker(
            id = 3L,
            name = "Tracker",
            valScoreList = listOf("0", "5", "10").toImmutableList(),
        )

        assertEquals("5", LocalTrackingReconciliationPolicy.scoreString(tracker, 54.0))
    }

    @Test
    fun `local score conversion rejects unrated and exports the top of a 100 point scale`() {
        val tracker = DummyTracker(
            id = 3L,
            name = "Tracker",
            valScoreList = listOf("0", "5", "10").toImmutableList(),
        )

        assertEquals("10", LocalTrackingReconciliationPolicy.scoreString(tracker, 100.0))
        assertEquals(null, LocalTrackingReconciliationPolicy.scoreString(tracker, 0.0))
    }

    @Test
    fun `clear score uses the tracker's unrated value`() {
        val tracker = DummyTracker(
            id = 3L,
            name = "Tracker",
            valScoreList = listOf("-", "5", "10").toImmutableList(),
        )

        assertEquals("-", LocalTrackingReconciliationPolicy.clearScoreString(tracker))
    }

    @Test
    fun `external semantic statuses map to local statuses`() {
        val tracker = DummyTracker(
            id = 3L,
            name = "Tracker",
            valStatuses = listOf(0L, 1L, 2L),
            valReadingStatus = 1L,
            valCompletionStatus = 2L,
        )

        assertEquals(LocalTrackedWorkStatus.READING, LocalTrackingReconciliationPolicy.externalSnapshot(remote.copy(status = 1L), tracker).status)
        assertEquals(LocalTrackedWorkStatus.COMPLETED, LocalTrackingReconciliationPolicy.externalSnapshot(remote.copy(status = 2L), tracker).status)
    }

    @Test
    fun `hold and dropped statuses round trip through tracker status resources`() {
        val tracker = DummyTracker(
            id = 3L,
            name = "Tracker",
            valStatuses = listOf(1L, 2L, 4L, 5L),
            valReadingStatus = 1L,
            valCompletionStatus = 2L,
        )

        assertEquals(LocalTrackedWorkStatus.ON_HOLD, LocalTrackingReconciliationPolicy.externalSnapshot(remote.copy(status = 4L), tracker).status)
        assertEquals(LocalTrackedWorkStatus.DROPPED, LocalTrackingReconciliationPolicy.externalSnapshot(remote.copy(status = 5L), tracker).status)
        assertEquals(4L, LocalTrackingReconciliationPolicy.externalStatus(tracker, LocalTrackedWorkStatus.ON_HOLD))
        assertEquals(5L, LocalTrackingReconciliationPolicy.externalStatus(tracker, LocalTrackedWorkStatus.DROPPED))
    }

    @Test
    fun `unsupported local statuses do not guess remote status`() {
        val tracker = DummyTracker(id = 3L, name = "Tracker", valStatuses = listOf(1L, 2L))

        assertEquals(null, LocalTrackingReconciliationPolicy.externalStatus(tracker, LocalTrackedWorkStatus.DROPPED))
        assertEquals(tracker.getReadingStatus(), LocalTrackingReconciliationPolicy.externalStatus(tracker, LocalTrackedWorkStatus.READING))
    }
}
