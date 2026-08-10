package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class TrustExtensionRepositoryMigration : Migration {
    override val version: Float = 67f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return@withIOContext false
        val repository = migrationContext.get<ExtensionStoreRepository>() ?: return@withIOContext false
        for ((index, source) in sourcePreferences.extensionRepos().get().withIndex()) {
            try {
                repository.insertFromPreference(
                    indexUrl = source.removeSuffix("/index.min.json").removeSuffix("/index.json")
                        // KMK -->
                        .removeSuffix("/repo.json") + "/repo.json",
                    // KMK <--
                    name = "Repo #${index + 1}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                logcat(LogPriority.ERROR) { "Extension repository migration failed" }
            }
        }
        sourcePreferences.extensionRepos().delete()
        return@withIOContext true
    }
}
