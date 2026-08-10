package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCrossSourceGroupPrimary

// KMK --> v0.8.1-fix1
/**
 * Pure restore-precedence policy for [BackupCrossSourceGroupPrimary] rows — no Android/DB
 * dependencies, so it's directly unit-testable without instantiating [TasteRestorer]'s full
 * dependency graph. Extracted from [TasteRestorer.restoreCrossSourceGroupPrimaries].
 */
internal object CrossSourceGroupPrimaryRestorePolicy {

    /** A row is valid only with a non-blank groupId, a real source id, and a non-blank url. */
    fun isValid(primary: BackupCrossSourceGroupPrimary): Boolean =
        primary.groupId.isNotBlank() && primary.source != 0L && primary.url.isNotBlank()

    /** Among rows for the same groupId within one backup, the newest updatedAt wins. */
    fun newestOf(candidates: List<BackupCrossSourceGroupPrimary>): BackupCrossSourceGroupPrimary =
        candidates.maxBy { it.updatedAt }

    /**
     * True when the backup's row should overwrite the existing stored primary: the existing
     * primary is missing, or it is strictly older than the backup row.
     */
    fun shouldRestore(existingUpdatedAt: Long?, backupUpdatedAt: Long): Boolean =
        existingUpdatedAt == null || existingUpdatedAt < backupUpdatedAt
}
// KMK <--
