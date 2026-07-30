package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.download.service.DownloadPreferences

/**
 * Old installs relied on chapter URL hashing being implicitly enabled prior to
 * this preference existing. Since version-gated migrations never run for a
 * fresh install (only for upgrades), this only defaults the preference to
 * true for existing users who haven't explicitly set it, preserving their
 * previous download folder naming behavior.
 */
class ChapterUrlHashMigration : Migration {
    // KMK v0.8.17 (Komikku v1.14.1 reconciliation): upstream introduced this at its own versionCode
    // 81 (upstream PR #1800). KMK's local versionCode sequence diverged from upstream's numbering long
    // before this migration existed (KMK is already past 81), and Migrator only runs a migration whose
    // `version` falls within the (oldVersionCode+1)..newVersionCode range being upgraded through -- so
    // this must use KMK's own next versionCode (90, this release) to actually fire for existing KMK
    // users, not upstream's original 81.
    override val version: Float = 90f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val downloadPreferences = migrationContext.get<DownloadPreferences>() ?: return@withIOContext false

        val includeChapterUrlHash = downloadPreferences.includeChapterUrlHash()
        if (!includeChapterUrlHash.isSet()) {
            includeChapterUrlHash.set(true)
        }

        return@withIOContext true
    }
}
