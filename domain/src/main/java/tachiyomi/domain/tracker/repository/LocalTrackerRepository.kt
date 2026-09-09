package tachiyomi.domain.tracker.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkList
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress

interface LocalTrackerRepository {
    fun getAllWorksAsFlow(): Flow<List<LocalTrackedWork>>

    suspend fun getWork(id: String): LocalTrackedWork?

    suspend fun upsertWork(work: LocalTrackedWork)

    suspend fun deleteWork(id: String)

    /** Consolidates one local work into another as one atomic database operation. */
    suspend fun consolidateWork(canonicalId: String, duplicateId: String) {
        error("Local tracker work consolidation is not supported by this repository")
    }

    suspend fun getSources(workId: String): List<LocalTrackedWorkSource>

    suspend fun getWorkIdBySourceUrl(source: Long, url: String): String?

    fun observeWorkIdBySourceUrl(source: Long, url: String): Flow<String?>

    suspend fun recordProgress(
        workId: String,
        source: Long,
        chapterNumber: Double?,
        chapterUrl: String,
        chapterLabel: String,
        progressAt: Long,
    )

    suspend fun upsertSource(source: LocalTrackedWorkSource)

    /** Applies a migrated source relationship and dependent progress/work update as one unit. */
    suspend fun migrateSourceRelationship(
        source: LocalTrackedWorkSource,
        progress: LocalTrackedWorkSourceProgress?,
        work: LocalTrackedWork?,
    ) {
        work?.let { upsertWork(it) }
        upsertSource(source)
        progress?.let { upsertSourceProgress(it) }
    }

    suspend fun deleteSource(workId: String, source: Long, url: String)

    suspend fun getSourceProgress(workId: String, source: Long, url: String): LocalTrackedWorkSourceProgress?

    /** Returns progress rows for every source currently attached to a local work. */
    suspend fun getSourceProgressForWork(workId: String): List<LocalTrackedWorkSourceProgress> =
        getSources(workId).mapNotNull { source ->
            getSourceProgress(workId, source.source, source.url)
        }

    suspend fun upsertSourceProgress(progress: LocalTrackedWorkSourceProgress)

    suspend fun recordSourceProgress(progress: LocalTrackedWorkSourceProgress)

    suspend fun deleteSourceProgress(workId: String, source: Long, url: String)

    suspend fun setInheritanceOptedOut(workId: String, source: Long, url: String, optedOut: Boolean, updatedAt: Long)

    suspend fun getLists(workId: String): List<String>

    suspend fun replaceLists(workId: String, lists: List<String>)

    /** Returns list membership with its original creation time for lossless backup/restore. */
    suspend fun getListEntries(workId: String): List<LocalTrackedWorkList> =
        getLists(workId).map { LocalTrackedWorkList(name = it, createdAt = System.currentTimeMillis()) }

    /** Replaces list membership while preserving each entry's creation time. */
    suspend fun replaceListEntries(workId: String, lists: List<LocalTrackedWorkList>) {
        replaceLists(workId, lists.map { it.name })
    }
}
