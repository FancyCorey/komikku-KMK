package exh.util

import java.util.UUID

// KMK Action History: private custom-cover receipts.
data class CustomCoverUndoEntry(
    val id: String,
    val timestamp: Long,
    val mangaId: Long,
    val previousDigest: String?,
    val expectedPostDigest: String?,
    val reversible: Boolean = true,
)

object CustomCoverUndoJournal {
    const val MAX_ENTRIES = 10

    private val lock = Any()
    private val entries = ArrayDeque<CustomCoverUndoEntry>()

    fun record(entry: CustomCoverUndoEntry) {
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        ActionHistoryDiagnosticTrace.recordCommitted(
            rowKey = entry.id,
            family = "custom cover",
            operation = "CUSTOM_COVER",
            readCount = 1,
            writeCount = 1,
            affectedCount = 1,
            timestamp = entry.timestamp,
        )
    }

    fun snapshot(): List<CustomCoverUndoEntry> = synchronized(lock) { entries.toList().asReversed() }

    fun removeById(id: String) {
        synchronized(lock) { entries.removeAll { it.id == id } }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }

    fun newId(): String = UUID.randomUUID().toString()
}
