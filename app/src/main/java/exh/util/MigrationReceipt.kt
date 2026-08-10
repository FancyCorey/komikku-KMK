package exh.util

import java.util.UUID

// KMK Universal Action History Recovery Plan 2026-07-31 -->
/**
 * A private, Evaluation-Mode-only record of one verified-successful migration -- the counterpart to
 * [PackageOperationReceipt] for [NonUndoableEventType.MIGRATION_COMPLETED] events. Never rendered
 * directly as public-facing text (mirrors [PackageOperationReceipt]'s own privacy rule); it exists
 * only to let [MigrationFollowUpPolicy] decide whether a safe "Migrate back" follow-up can be offered.
 *
 * [originMangaId]/[originSourceId] identify the manga the user migrated *from* -- the origin row is
 * never deleted by a migration (only unfavorited when `replace` was true), so it can still be
 * re-resolved by id later. [targetMangaId]/[targetSourceId] identify the manga the user migrated *to*
 * (the live library entry). A "Migrate back" follow-up runs an ordinary,
 * fresh migration in the reverse direction (`current = target row, target = origin row`) through the
 * exact same [mihon.domain.migration.usecases.MigrateMangaUseCase] the original migration used --
 * it is a new forward operation with its own success/failure outcome, not a database rollback, and
 * is never labeled "Undo" (mirrors [ActionHistoryFollowUp]'s own contract for package follow-ups).
 */
data class MigrationReceipt(
    val id: String,
    val timestamp: Long,
    val originMangaId: Long,
    val originSourceId: Long,
    val targetMangaId: Long,
    val targetSourceId: Long,
    val replace: Boolean,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Bounded, in-memory, Evaluation-Mode-only store of [MigrationReceipt]s. Same bound and
 * most-recent-first contract as [PackageOperationJournal]/[NonUndoableEventJournal]; kept separate so
 * the richer private origin/target identity here never needs to flow through the general Action
 * History rendering path.
 */
object MigrationReceiptJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<MigrationReceipt>()

    fun record(receipt: MigrationReceipt) {
        synchronized(lock) {
            entries.addLast(receipt)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    /** Most recent first. */
    fun snapshot(): List<MigrationReceipt> = synchronized(lock) { entries.toList().asReversed() }

    /** The receipt sharing [id] with a rendered [NonUndoableEvent], if still retained. */
    fun forId(id: String): MigrationReceipt? = synchronized(lock) { entries.find { it.id == id } }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}
// KMK <--
