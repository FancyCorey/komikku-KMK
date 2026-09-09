package exh.util

import java.util.UUID

// KMK Universal Action History Recovery Plan 2026-08-01 -->
/** Private, bounded receipt for a verified tracker binding. */
data class TrackerBindingReceipt(
    val id: String,
    val timestamp: Long,
    val mangaId: Long,
    val trackerId: Long,
    val remoteId: Long,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/** Bounded in-memory receipts; the Action History row exposes only a generic summary. */
object TrackerBindingReceiptJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<TrackerBindingReceipt>()

    fun record(receipt: TrackerBindingReceipt) {
        synchronized(lock) {
            entries.addLast(receipt)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    fun forId(id: String): TrackerBindingReceipt? = synchronized(lock) {
        entries.find { it.id == id }
    }

    fun snapshot(): List<TrackerBindingReceipt> = synchronized(lock) {
        entries.toList().asReversed()
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}

/** Records only after Tracker.register returns successfully. */
fun recordSuccessfulTrackerBinding(
    evaluationModeEnabled: Boolean,
    mangaId: Long,
    trackerId: Long,
    remoteId: Long,
) {
    val id = TrackerBindingReceipt.newId()
    val timestamp = System.currentTimeMillis()
    NonUndoableEventJournal.record(
        NonUndoableEvent(
            id = id,
            timestamp = timestamp,
            eventType = NonUndoableEventType.TRACKER_BOUND,
        ),
    )
    TrackerBindingReceiptJournal.record(
        TrackerBindingReceipt(
            id = id,
            timestamp = timestamp,
            mangaId = mangaId,
            trackerId = trackerId,
            remoteId = remoteId,
        ),
    )
}
// KMK <--
