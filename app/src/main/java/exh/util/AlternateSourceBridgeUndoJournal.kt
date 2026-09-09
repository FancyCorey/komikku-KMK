package exh.util

import exh.recs.bridge.AlternateSourceBridgeMutation
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.taste.interactor.ReplaceAlternateSourceBridge
import tachiyomi.domain.taste.model.AlternateSourceBridgeMappingReplacement
import tachiyomi.domain.taste.model.AlternateSourceBridgeStateReplacement
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

data class AlternateSourceBridgeUndoEntry(
    val id: String,
    val timestamp: Long,
    val mutation: AlternateSourceBridgeMutation,
    val replacement: AlternateSourceBridgeStateReplacement,
)

object AlternateSourceBridgeUndoRecorder {
    fun build(
        replacement: AlternateSourceBridgeStateReplacement,
        mutation: AlternateSourceBridgeMutation,
    ): AlternateSourceBridgeUndoEntry {
        val rows = buildList {
            replacement.replacementBridge?.updatedAt?.let(::add)
            replacement.expectedBridge?.updatedAt?.let(::add)
            replacement.mappingReplacements.forEach { change ->
                change.replacement?.updatedAt?.let(::add)
                change.expected?.updatedAt?.let(::add)
            }
        }
        require(rows.isNotEmpty())
        return AlternateSourceBridgeUndoEntry(
            id = UUID.randomUUID().toString(),
            timestamp = rows.max(),
            mutation = mutation,
            replacement = replacement,
        )
    }
}

object AlternateSourceBridgeUndoJournal {
    const val MAX_ENTRIES = 20
    private val lock = Any()
    private val entries = ArrayDeque<AlternateSourceBridgeUndoEntry>()

    fun record(entry: AlternateSourceBridgeUndoEntry) {
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        ActionHistoryDiagnosticTrace.recordCommitted(
            rowKey = entry.id,
            family = "alternate_source_bridge",
            operation = entry.mutation.name,
            readCount = 1 + entry.replacement.mappingReplacements.size,
            writeCount = 1 + entry.replacement.mappingReplacements.size,
            affectedCount = 1 + entry.replacement.mappingReplacements.size,
            timestamp = entry.timestamp,
        )
    }

    fun snapshot(): List<AlternateSourceBridgeUndoEntry> = synchronized(lock) { entries.toList().asReversed() }
    fun find(id: String): AlternateSourceBridgeUndoEntry? = synchronized(lock) { entries.firstOrNull { it.id == id } }
    fun removeById(id: String) = synchronized(lock) { entries.removeAll { it.id == id } }
    fun clear() = synchronized(lock) { entries.clear() }
}

class AlternateSourceBridgeUndoService(
    private val replaceBridge: ReplaceAlternateSourceBridge = Injekt.get(),
) {
    suspend fun undo(id: String): GroupUndoResult {
        val entry = AlternateSourceBridgeUndoJournal.find(id) ?: return GroupUndoResult.FAILED
        val forward = entry.replacement
        val inverse = AlternateSourceBridgeStateReplacement(
            expectedBridge = forward.replacementBridge,
            replacementBridge = forward.expectedBridge,
            mappingReplacements = forward.mappingReplacements.map {
                AlternateSourceBridgeMappingReplacement(it.replacement, it.expected)
            },
        )
        return try {
            if (!replaceBridge.await(inverse)) return GroupUndoResult.CONFLICT
            AlternateSourceBridgeUndoJournal.removeById(id)
            GroupUndoResult.RESTORED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GroupUndoResult.FAILED
        }
    }
}
