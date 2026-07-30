package exh.util

import java.util.UUID

// KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29 -->
/**
 * Distinguishes install, update, and uninstall as three separate package operations. Prior to this
 * pass, [NonUndoableEventJournal] recorded these as bare visibility-only events with no package
 * identity at all -- enough to render "Extension installed" in Action History, but not enough to
 * decide whether a safe follow-up action (uninstall this exact extension, reinstall this exact
 * extension) can be offered. This model exists specifically to carry the private metadata that
 * decision requires; it is deliberately separate from [NonUndoableEventJournal], which continues to
 * drive the existing, already-privacy-reviewed Action History summary text unchanged.
 */
enum class PackageOperationKind {
    INSTALL,
    UPDATE,
    UNINSTALL,
}

/**
 * A private, Evaluation-Mode-only record of one verified-successful package operation.
 *
 * Per the same privacy rule [NonUndoableEventJournal] and the typed undo journals already follow,
 * this data is used **only** internally, to compute [PackageOperationFollowUpPolicy] eligibility --
 * it is never rendered directly as public-facing text. [packageName] is an internal Android package
 * identifier, not a display name; any user-visible follow-up label goes through the existing
 * privacy-safe formatters, never this receipt's raw fields.
 */
data class PackageOperationReceipt(
    val id: String,
    val timestamp: Long,
    val kind: PackageOperationKind,
    val packageName: String,
    val signatureHash: String?,
    val versionCode: Long?,
    val artifactUri: String?,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Bounded, in-memory, Evaluation-Mode-only store of [PackageOperationReceipt]s. Same bound and
 * most-recent-first contract as [NonUndoableEventJournal]; kept as a separate journal rather than
 * folded into it so the richer private metadata here never needs to flow through the general
 * Action History rendering path, which only ever needs [NonUndoableEventJournal]'s bare event type.
 */
object PackageOperationJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<PackageOperationReceipt>()

    fun record(receipt: PackageOperationReceipt) {
        synchronized(lock) {
            entries.addLast(receipt)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    /** Most recent first. */
    fun snapshot(): List<PackageOperationReceipt> = synchronized(lock) { entries.toList().asReversed() }

    /** The most recent receipt recorded for [packageName], if any -- used for follow-up eligibility. */
    fun latestFor(packageName: String): PackageOperationReceipt? =
        synchronized(lock) { entries.lastOrNull { it.packageName == packageName } }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}
// KMK <--
