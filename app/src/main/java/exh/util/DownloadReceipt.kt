package exh.util

import java.util.UUID

// KMK Universal Action History Recovery Plan 2026-08-01 -->
/**
 * A private, Evaluation-Mode-only record of one verified-successful [eu.kanade.tachiyomi.data.download.DownloadManager.deleteChapters]
 * call -- the counterpart to [MigrationReceipt] for [NonUndoableEventType.DOWNLOAD_DELETED] events.
 * Never rendered directly as public-facing text (mirrors [MigrationReceipt]'s own privacy rule); it
 * exists only to let [DownloadFollowUpPolicy] decide whether a safe "Re-download" follow-up can be
 * offered.
 *
 * [chapterIds] is the exact set of chapter ids `deleteChapters` actually deleted (its own
 * `filteredChapters`, captured before the delete ran) -- never a broader "all chapters of this manga"
 * guess, since that would risk queuing chapters that were never downloaded in the first place. A
 * "Re-download" follow-up re-queues exactly these chapter ids through the ordinary
 * [eu.kanade.tachiyomi.data.download.DownloadManager.downloadChapters] enqueue path -- a new forward
 * operation with no completion guarantee of its own (downloads are queue-based and asynchronous
 * throughout this app), not a restore, and never labeled "Undo" (mirrors [ActionHistoryFollowUp]'s own
 * contract).
 */
data class DownloadReceipt(
    val id: String,
    val timestamp: Long,
    val mangaId: Long,
    val sourceId: Long,
    val chapterIds: List<Long>,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Bounded, in-memory, Evaluation-Mode-only store of [DownloadReceipt]s. Same bound and
 * most-recent-first contract as [MigrationReceiptJournal]/[PackageOperationJournal].
 */
object DownloadReceiptJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<DownloadReceipt>()

    fun record(receipt: DownloadReceipt) {
        synchronized(lock) {
            entries.addLast(receipt)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    /** Most recent first. */
    fun snapshot(): List<DownloadReceipt> = synchronized(lock) { entries.toList().asReversed() }

    /** The receipt sharing [id] with a rendered [NonUndoableEvent], if still retained. */
    fun forId(id: String): DownloadReceipt? = synchronized(lock) { entries.find { it.id == id } }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}
// KMK <--
