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
    // KMK Code-Only Completion Plan 2026-07-31: only recorded for a manual (non-sync), fully
    // successful restore -- BackupRestoreJob.doWork() only records this after
    // BackupRestorer.restore() returns BackupRestoreOutcome.Success (zero item-level errors) and
    // isSync is false. A cancelled, partially-successful, or failed restore records nothing -- see
    // BackupRestoreOutcome's own doc for why partial success is deliberately not represented here.
    BACKUP_RESTORED,
    // KMK Universal Action History Recovery Plan 2026-08-01: only recorded for
    // DownloadManager.deleteChapters() -- the chapter-list delete, which always knows exactly which
    // chapter ids it deleted (filteredChapters). DownloadManager.deleteManga() (whole-manga/source
    // directory cleanup, including its own internal empty-directory-cleanup call from
    // deleteChapters()) does not record this: it has no discrete per-chapter inventory to make a
    // truthful "Re-download these chapters" follow-up possible, and recording a manga-level event
    // with no verifiable chapter list would misrepresent what can safely be re-queued.
    DOWNLOAD_DELETED,
    // KMK Universal Action History Recovery Plan 2026-08-01: a tracker field write completed through
    // the shared Tracker API. The private TrackWriteReceipt twin contains only the prior typed value
    // needed for a guarded compensating sync; this public event never contains tracker or manga names.
    TRACKER_WRITE_COMPLETED,
    // KMK Universal Action History Recovery Plan 2026-08-01: a tracker binding completed through
    // Tracker.register(). Its private receipt contains only opaque ids needed to offer a guarded
    // unlink follow-up when the tracker implements DeletableTracker.
    TRACKER_BOUND,
    // KMK Universal Action History Recovery Plan 2026-08-01: a guarded tracker unlink follow-up
    // completed. This event has no receipt and is intentionally not itself reversible.
    TRACKER_UNBOUND,
    // KMK Codex continuous completion 2026-08-05: manual Source Evaluation management actions
    // delete diagnostic/quarantine records with no safe generic inverse; retain visibility only.
    SOURCE_EVALUATION_DATA_CLEARED,
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
