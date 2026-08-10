package exh.util

import java.util.UUID

// KMK Universal Action History Recovery Plan 2026-08-01 -->
/** Private, Evaluation-Mode-only state needed to compensate for one remote tracker write. */
enum class TrackWriteField {
    STATUS,
    SCORE,
    CHAPTER_PROGRESS,
    START_DATE,
    FINISH_DATE,
    PRIVATE,
}

/**
 * A typed snapshot of the value that existed immediately before a verified tracker write.
 *
 * Only the field matching [field] is populated. The receipt is never rendered directly; the public
 * event contains only its type and timestamp, while this private twin is used for a fresh, guarded
 * compensating sync. A compensating sync is a new forward write, not a database rollback.
 */
data class TrackWriteReceipt(
    val id: String,
    val timestamp: Long,
    val mangaId: Long,
    val trackerId: Long,
    val field: TrackWriteField,
    val previousStatus: Long? = null,
    val previousScore: String? = null,
    val previousChapterProgress: Int? = null,
    val previousStartDate: Long? = null,
    val previousFinishDate: Long? = null,
    val previousPrivate: Boolean? = null,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** Bounded, in-memory, Evaluation-Mode-only store of [TrackWriteReceipt]s. */
object TrackWriteReceiptJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<TrackWriteReceipt>()

    fun record(receipt: TrackWriteReceipt) {
        synchronized(lock) {
            entries.addLast(receipt)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    fun snapshot(): List<TrackWriteReceipt> = synchronized(lock) { entries.toList().asReversed() }

    fun forId(id: String): TrackWriteReceipt? = synchronized(lock) { entries.find { it.id == id } }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}

/** Records the private twin and public event only after the caller's remote write returned. */
fun recordSuccessfulTrackWrite(
    evaluationModeEnabled: Boolean,
    mangaId: Long,
    trackerId: Long,
    field: TrackWriteField,
    previousStatus: Long? = null,
    previousScore: String? = null,
    previousChapterProgress: Int? = null,
    previousStartDate: Long? = null,
    previousFinishDate: Long? = null,
    previousPrivate: Boolean? = null,
) {
    if (!evaluationModeEnabled) return

    val id = TrackWriteReceipt.newId()
    val timestamp = System.currentTimeMillis()
    NonUndoableEventJournal.record(
        NonUndoableEvent(
            id = id,
            timestamp = timestamp,
            eventType = NonUndoableEventType.TRACKER_WRITE_COMPLETED,
        ),
    )
    TrackWriteReceiptJournal.record(
        TrackWriteReceipt(
            id = id,
            timestamp = timestamp,
            mangaId = mangaId,
            trackerId = trackerId,
            field = field,
            previousStatus = previousStatus,
            previousScore = previousScore,
            previousChapterProgress = previousChapterProgress,
            previousStartDate = previousStartDate,
            previousFinishDate = previousFinishDate,
            previousPrivate = previousPrivate,
        ),
    )
}
// KMK <--
