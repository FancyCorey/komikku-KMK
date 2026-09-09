package mihon.feature.migration.list.models

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import mihon.feature.migration.list.MigrationListScreenModel.ChapterInfo
import tachiyomi.domain.manga.model.Manga
import kotlin.coroutines.CoroutineContext

class MigratingManga(
    val manga: Manga,
    val chapterCount: Int,
    val latestChapter: Double?,
    val source: String,
    parentContext: CoroutineContext,
) {
    val migrationScope = CoroutineScope(parentContext + SupervisorJob() + Dispatchers.Default)

    // KMK -->
    var searchingJob: Deferred<Pair<Manga, ChapterInfo>?>? = null
    // KMK <--

    val searchResult = MutableStateFlow<SearchResult>(SearchResult.Searching)

    // KMK: truthful per-item migration
    // status, set by MigrationListScreenModel.migrateNow()/migrateMangas() via
    // MigrationOutcomeReducer. Null means "no migration attempted yet or last attempt not
    // resolved" -- the item stays visible and retryable in either case; it is never removed from
    // the pending list except after a confirmed RESULT_SUCCESS.
    val migrationResult = MutableStateFlow<MigrationResultState?>(null)

    sealed interface MigrationResultState {
        data object InProgress : MigrationResultState
        data class Failed(val retryable: Boolean) : MigrationResultState
    }

    sealed interface SearchResult {
        data object Searching : SearchResult
        data object NotFound : SearchResult
        data class Success(
            val manga: Manga,
            val chapterCount: Int,
            val latestChapter: Double?,
            val source: String,
        ) : SearchResult
    }
}
