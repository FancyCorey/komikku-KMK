package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.source.service.SourcePreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

// KMK v0.8.17 (Komikku v1.14.1 reconciliation): this migration existed upstream at versionCode 80 but
// was never carried over during the original Komikku-1.14.0 reconciliation -- by that point KMK's own
// local versionCode sequence had already diverged past 80, so a straight copy of `version = 80f` would
// never fire for any KMK user (Migrator only runs a migration whose `version` falls within the
// (oldVersionCode+1)..newVersionCode range being upgraded through). The underlying `disabledRepos()`
// preference key is unchanged between upstream and KMK (still `"disabled_repos"`), so the fix itself is
// still relevant here -- it corrects a stored URL that could already have a duplicated "/repo.json"
// suffix from an earlier buggy write. Added now, using KMK's own next versionCode (90, this release) so
// it actually runs once for existing KMK users, with the duplicate-suffix guard already applied (the
// exact bug upstream v1.14.1 itself had to fix in this same file is written correctly from the start
// here, since there was never a KMK release with the buggy version of this migration to begin with).
class DisabledRepoMigration : Migration {
    override val version: Float = 90f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit {
            val disabledRepos = prefs.getStringSet(sourcePreferences.disabledRepos().key(), emptySet()) ?: return@edit
            disabledRepos
                .map {
                    it.removeSuffix("/index.min.json").removeSuffix("/index.json")
                        .removeSuffix("/repo.json") + "/repo.json"
                }.toSet()
                .let {
                    putStringSet(sourcePreferences.disabledRepos().key(), it)
                }
        }
        return@withIOContext true
    }
}
