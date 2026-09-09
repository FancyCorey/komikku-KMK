package exh.util

import java.util.ArrayDeque
// KMK H2A -->
/**
 * A bounded, in-memory diagnostic trail for Action History operations.
 *
 * The trail stores operation metadata only. It deliberately has no value, URL, title, account,
 * SQL, exception, clipboard, or log payload fields. The internal key is used only to associate an
 * event with its owning history row and is never rendered.
 */
enum class DiagnosticTracePhase {
    ATTEMPTED,
    COMMITTED,
    CANCELLED,
    FAILED,
    SKIPPED,
    FOLLOW_UP,
}

enum class DiagnosticTraceOutcome {
    IN_PROGRESS,
    SUCCESS,
    PARTIAL,
    CONFLICT,
    CANCELLED,
    FAILED,
    SKIPPED,
}

data class DiagnosticTraceEvent(
    internal val rowKey: String,
    val family: String,
    val operation: String,
    val phase: DiagnosticTracePhase,
    val outcome: DiagnosticTraceOutcome,
    val timestamp: Long,
    val durationMillis: Long?,
    val readCount: Int,
    val writeCount: Int,
    val writeCommitted: Boolean,
    val affectedCount: Int,
)

data class DiagnosticTraceToken internal constructor(
    internal val rowKey: String,
    internal val operation: String,
    internal val startedAt: Long,
)

object ActionHistoryDiagnosticTrace {
    const val MAX_EVENTS = 100

    private val lock = Any()
    private val events = ArrayDeque<DiagnosticTraceEvent>()

    fun begin(
        rowKey: String,
        family: String,
        operation: String,
        timestamp: Long = System.currentTimeMillis(),
    ): DiagnosticTraceToken {
        append(
            DiagnosticTraceEvent(
                rowKey = rowKey,
                family = safeLabel(family),
                operation = safeLabel(operation),
                phase = DiagnosticTracePhase.ATTEMPTED,
                outcome = DiagnosticTraceOutcome.IN_PROGRESS,
                timestamp = timestamp,
                durationMillis = null,
                readCount = 0,
                writeCount = 0,
                writeCommitted = false,
                affectedCount = 0,
            ),
        )
        return DiagnosticTraceToken(rowKey, safeLabel(operation), timestamp)
    }

    fun finish(
        token: DiagnosticTraceToken,
        phase: DiagnosticTracePhase,
        outcome: DiagnosticTraceOutcome,
        readCount: Int = 0,
        writeCount: Int = 0,
        writeCommitted: Boolean = false,
        affectedCount: Int = 0,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        append(
            DiagnosticTraceEvent(
                rowKey = token.rowKey,
                family = "action history",
                operation = token.operation,
                phase = phase,
                outcome = outcome,
                timestamp = timestamp,
                durationMillis = (timestamp - token.startedAt).coerceAtLeast(0L),
                readCount = readCount.coerceAtLeast(0),
                writeCount = writeCount.coerceAtLeast(0),
                writeCommitted = writeCommitted,
                affectedCount = affectedCount.coerceAtLeast(0),
            ),
        )
    }

    /** Records the post-success boundary of a journal/receipt writer. */
    fun recordCommitted(
        rowKey: String,
        family: String,
        operation: String,
        readCount: Int,
        writeCount: Int,
        affectedCount: Int,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        append(
            DiagnosticTraceEvent(
                rowKey = rowKey,
                family = safeLabel(family),
                operation = safeLabel(operation),
                phase = DiagnosticTracePhase.COMMITTED,
                outcome = DiagnosticTraceOutcome.SUCCESS,
                timestamp = timestamp,
                durationMillis = null,
                readCount = readCount.coerceAtLeast(0),
                writeCount = writeCount.coerceAtLeast(0),
                writeCommitted = true,
                affectedCount = affectedCount.coerceAtLeast(0),
            ),
        )
    }

    fun snapshotFor(rowKey: String): List<DiagnosticTraceEvent> = synchronized(lock) {
        events.filter { it.rowKey == rowKey }
    }

    fun snapshot(): List<DiagnosticTraceEvent> = synchronized(lock) { events.toList() }

    fun removeByRowKey(rowKey: String) {
        synchronized(lock) { events.removeAll { it.rowKey == rowKey } }
    }

    fun clear() {
        synchronized(lock) { events.clear() }
    }

    private fun append(event: DiagnosticTraceEvent) {
        synchronized(lock) {
            events.addLast(event)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    private fun safeLabel(value: String): String {
        val normalized = value.trim()
        if (
            normalized.length > 64 ||
            normalized.contains("://") ||
            normalized.contains('/') ||
            normalized.contains('?') ||
            normalized.contains('@')
        ) {
            return "redacted"
        }
        return normalized
            .replace(Regex("[^A-Za-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(64)
            .ifEmpty { "redacted" }
    }
}
// KMK H2A <--
