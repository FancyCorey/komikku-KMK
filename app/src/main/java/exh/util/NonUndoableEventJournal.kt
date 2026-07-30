package exh.util

import java.util.UUID

// KMK v0.8.20-fix1 -->
/**
 * Bounded, in-memory, Evaluation-Mode-only record of operations that genuinely cannot be undone --
 * currently a completed Best Version migration, a source install confirmed via `InstallStep`, and
 * (since KMK Confirmed Blocker Remediation Phase 5) a source uninstall confirmed by observing
 * `ExtensionManager.installedExtensionsFlow`.
 * This is deliberately NOT the same architecture as [EvaluationModeUndoJournal] and its typed
 * siblings ([GroupUndoJournal], [LibraryUndoJournal], [PreferenceUndoJournal], [ChapterUndoJournal]):
 * those journals record a previous state that can be restored through a verified inverse write. A
 * migration replaces the library entry, may delete downloaded chapters, and can update an external
 * tracker service the device cannot roll back (see `best_version_migrate_not_undoable`); a source
 * install runs the OS package installer. Neither has a safe, verified inverse, so this journal never
 * exposes an Undo action for any entry it records -- it exists purely so these operations are not
 * invisible to Evaluation Mode's Action History, not to make them reversible.
 *
 * Per the same privacy rule the other journals already follow (see [EvaluationJournalEntry]'s doc),
 * an entry stores only its [NonUndoableEventType] and [timestamp] -- no manga id, no source/
 * repository/extension name, no package name, no network payload. This is deliberately even
 * stricter than the typed journals (which at least record `source`/`url` identity for restore
 * purposes) since there is nothing here that ever needs to be looked up again.
 */
enum class NonUndoableEventType {
    MIGRATION_COMPLETED,
    EXTENSION_INSTALLED,
    // KMK Confirmed Blocker Remediation Corrective Completion Plan V2 2026-07-29: a fresh install and
    // an update of an already-installed extension are not the same operation -- updateExtension()
    // previously reused recordUserInitiatedInstall() unmodified, so every update was misrecorded as
    // EXTENSION_INSTALLED. See ExtensionsScreenModel.updateExtension().
    EXTENSION_UPDATED,
    // KMK Confirmed Blocker Remediation Phase 5 2026-07-29: only recorded after
    // ExtensionManager.installedExtensionsFlow is verified to no longer contain the uninstalled
    // package (bounded wait, see SourceEvaluationScreenModel.uninstallRuntimeHealthExtension) --
    // extensionManager.uninstallExtension() itself is fire-and-forget with no completion signal, so
    // this event is never recorded on the mere fact that uninstall was requested.
    EXTENSION_UNINSTALLED,
}

data class NonUndoableEvent(
    val id: String,
    val timestamp: Long,
    val eventType: NonUndoableEventType,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

object NonUndoableEventJournal {
    /** Same bound as [EvaluationModeUndoJournal.MAX_ENTRIES] -- a manual test/evidence-capture session. */
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<NonUndoableEvent>()

    fun record(event: NonUndoableEvent) {
        synchronized(lock) {
            entries.addLast(event)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    /** Most recent first. */
    fun snapshot(): List<NonUndoableEvent> = synchronized(lock) { entries.toList().asReversed() }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}
// KMK <--
