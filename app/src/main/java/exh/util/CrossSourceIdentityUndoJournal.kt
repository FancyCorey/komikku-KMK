package exh.util

import exh.recs.matching.CrossSourceIdentityMutation
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecisions
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityReplacement
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

data class CrossSourceIdentityUndoEntry(
    val id: String,
    val timestamp: Long,
    val mutation: CrossSourceIdentityMutation,
    val replacements: List<CrossSourceIdentityReplacement>,
)

object CrossSourceIdentityUndoRecorder {
    fun build(
        replacements: List<CrossSourceIdentityReplacement>,
        mutation: CrossSourceIdentityMutation,
    ): CrossSourceIdentityUndoEntry {
        require(replacements.isNotEmpty())
        return CrossSourceIdentityUndoEntry(
            id = UUID.randomUUID().toString(),
            timestamp = replacements.maxOf { requireNotNull(it.replacement).updatedAt },
            mutation = mutation,
            replacements = replacements,
        )
    }
}

object CrossSourceIdentityUndoJournal {
    const val MAX_ENTRIES = 20
    private val lock = Any()
    private val entries = ArrayDeque<CrossSourceIdentityUndoEntry>()

    fun record(entry: CrossSourceIdentityUndoEntry) {
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        ActionHistoryDiagnosticTrace.recordCommitted(
            rowKey = entry.id,
            family = "cross_source_identity",
            operation = entry.mutation.name,
            readCount = entry.replacements.size,
            writeCount = entry.replacements.size,
            affectedCount = entry.replacements.size,
            timestamp = entry.timestamp,
        )
    }

    fun snapshot(): List<CrossSourceIdentityUndoEntry> = synchronized(lock) { entries.toList().asReversed() }
    fun find(id: String): CrossSourceIdentityUndoEntry? = synchronized(lock) { entries.firstOrNull { it.id == id } }
    fun removeById(id: String) = synchronized(lock) { entries.removeAll { it.id == id } }
    fun clear() = synchronized(lock) { entries.clear() }
    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}

class CrossSourceIdentityUndoService(
    private val replaceDecisions: ReplaceCrossSourceIdentityDecisions = Injekt.get(),
) {
    suspend fun undo(id: String): GroupUndoResult {
        val entry = CrossSourceIdentityUndoJournal.find(id) ?: return GroupUndoResult.FAILED
        return try {
            val inverse = entry.replacements.map { CrossSourceIdentityReplacement(it.replacement, it.expected) }
            if (!replaceDecisions.await(inverse)) return GroupUndoResult.CONFLICT
            CrossSourceIdentityUndoJournal.removeById(id)
            GroupUndoResult.RESTORED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GroupUndoResult.FAILED
        }
    }
}
