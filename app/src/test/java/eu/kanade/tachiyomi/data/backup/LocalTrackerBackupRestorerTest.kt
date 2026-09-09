package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.creators.LocalTrackerBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWork
import eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkSource
import eu.kanade.tachiyomi.data.backup.restore.restorers.LocalTrackerBackupRestorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkList
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

private fun graphSource(url: String) = LocalTrackedWorkSource(
    workId = "graph",
    source = 1L,
    url = url,
    title = "Graph",
    confidence = 100,
    confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
    createdAt = 1L,
    updatedAt = 1L,
)

private fun graphProgress(url: String) = LocalTrackedWorkSourceProgress(
    workId = "graph",
    source = 1L,
    url = url,
    chapterNumber = 1.0,
    chapterUrl = "$url-1",
    chapterLabel = "Chapter 1",
    progressAt = 1L,
    updatedAt = 1L,
)

class LocalTrackerBackupRestorerTest {
    @Test
    fun `creator and protobuf wire round trip preserve the complete local tracker graph`() = kotlinx.coroutines.test.runTest {
        val sourceRepository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "wire",
                title = "Wire Graph",
                normalizedTitle = "wire graph",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = 1L,
                lastChapterNumber = 12.0,
                lastChapterUrl = "/wire/chapter-12",
                lastChapterLabel = "Chapter 12",
                lastProgressAt = 4L,
                score = 85.0,
                startDate = 2L,
                finishDate = null,
                createdAt = 1L,
                updatedAt = 5L,
            ),
        )
        sourceRepository.sources[1L to "/wire"] = graphSource("/wire").copy(
            workId = "wire",
            title = "Wire Graph",
            inheritanceOptedOut = true,
            updatedAt = 5L,
        )
        sourceRepository.sourceProgresses += graphProgress("/wire").copy(
            workId = "wire",
            chapterNumber = 12.0,
            chapterUrl = "/wire/chapter-12",
            chapterLabel = "Chapter 12",
            progressAt = 4L,
            inheritedFromSource = 2L,
            inheritedFromUrl = "/origin",
            updatedAt = 5L,
        )
        sourceRepository.listEntries["wire"] = mutableListOf(LocalTrackedWorkList("Reading", 3L))
        sourceRepository.lists["wire"] = mutableListOf("Reading")

        val created = LocalTrackerBackupCreator(sourceRepository)().single()
        val encoded = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(backupManga = emptyList(), backupLocalTrackedWorks = listOf(created)),
        )
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), encoded)

        val targetRepository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "placeholder",
                title = "Placeholder",
                normalizedTitle = "placeholder",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.PLANNED,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val errors = LocalTrackerBackupRestorer(targetRepository).restore(decoded.backupLocalTrackedWorks)

        assertTrue(errors.isEmpty(), "expected a clean graph restore, got: $errors")
        assertEquals(85.0, targetRepository.works["wire"]?.score)
        assertEquals("Wire Graph", targetRepository.works["wire"]?.title)
        assertTrue(targetRepository.sources[1L to "/wire"]!!.inheritanceOptedOut)
        assertEquals("/origin", targetRepository.sourceProgresses.single().inheritedFromUrl)
        assertEquals(listOf("Reading"), targetRepository.lists["wire"])
        assertEquals(listOf(LocalTrackedWorkList("Reading", 3L)), targetRepository.listEntries["wire"])
    }

    @Test
    fun `backup creator includes source opt out and progress provenance`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "exported",
                title = "Exported",
                normalizedTitle = "exported",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 5L,
            ),
        )
        repository.sources[1L to "/target"] = LocalTrackedWorkSource(
            workId = "exported",
            source = 1L,
            url = "/target",
            title = "Exported",
            confidence = 100,
            confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
            inheritanceOptedOut = true,
            createdAt = 1L,
            updatedAt = 5L,
        )
        repository.sourceProgresses += LocalTrackedWorkSourceProgress(
            workId = "exported",
            source = 1L,
            url = "/target",
            chapterNumber = 12.0,
            chapterUrl = "/target-chapter-12",
            chapterLabel = "Chapter 12",
            progressAt = 4L,
            inheritedFromSource = 2L,
            inheritedFromUrl = "/origin",
            updatedAt = 5L,
        )

        val row = LocalTrackerBackupCreator(repository)().single()

        assertTrue(row.sources.single().inheritanceOptedOut)
        assertEquals("/origin", row.sourceProgress.single().inheritedFromUrl)
    }

    @Test
    fun `source opt out and progress provenance are restored`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "seed",
                title = "Seed",
                normalizedTitle = "seed",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val restorer = LocalTrackerBackupRestorer(repository)
        val errors = restorer.restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "imported",
                    title = "Imported",
                    normalizedTitle = "imported",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 5L,
                    sources = listOf(
                        BackupLocalTrackedWorkSource(
                            source = 1L,
                            url = "/target",
                            title = "Imported",
                            confidence = 100,
                            confirmation = "USER_CONFIRMED",
                            inheritanceOptedOut = true,
                            createdAt = 1L,
                            updatedAt = 5L,
                        ),
                    ),
                    sourceProgress = listOf(
                        eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkSourceProgress(
                            source = 1L,
                            url = "/target",
                            chapterNumber = 12.0,
                            hasChapterNumber = true,
                            chapterUrl = "/target-chapter-12",
                            chapterLabel = "Chapter 12",
                            progressAt = 4L,
                            inheritedFromSource = 2L,
                            inheritedFromUrl = "/origin",
                            hasInheritedFrom = true,
                            updatedAt = 5L,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.isEmpty())
        assertTrue(repository.sources[1L to "/target"]!!.inheritanceOptedOut)
        assertEquals("/origin", repository.sourceProgresses.single().inheritedFromUrl)
    }

    @Test
    fun `child timestamps win over an older backup and duplicate children use the newest row`() = kotlinx.coroutines.test.runTest {
        val existingWork = LocalTrackedWork(
            id = "merged",
            title = "Merged",
            normalizedTitle = "merged",
            status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
            lastChapterSource = null,
            lastChapterNumber = null,
            lastChapterUrl = null,
            lastChapterLabel = null,
            lastProgressAt = null,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val repository = FakeLocalTrackerRepository(existingWork)
        repository.sources[1L to "/merged"] = graphSource("/merged").copy(
            workId = "merged",
            title = "Newer local source",
            updatedAt = 20L,
        )

        val errors = LocalTrackerBackupRestorer(repository).restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "merged",
                    title = "Merged",
                    normalizedTitle = "merged",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 30L,
                    sources = listOf(
                        BackupLocalTrackedWorkSource(
                            source = 1L,
                            url = "/merged",
                            title = "Older backup source",
                            confidence = 100,
                            confirmation = "USER_CONFIRMED",
                            createdAt = 1L,
                            updatedAt = 10L,
                        ),
                        BackupLocalTrackedWorkSource(
                            source = 2L,
                            url = "/duplicate",
                            title = "Older duplicate",
                            confidence = 50,
                            confirmation = "USER_CONFIRMED",
                            createdAt = 1L,
                            updatedAt = 11L,
                        ),
                        BackupLocalTrackedWorkSource(
                            source = 2L,
                            url = "/duplicate",
                            title = "Newest duplicate",
                            confidence = 90,
                            confirmation = "USER_CONFIRMED",
                            createdAt = 1L,
                            updatedAt = 12L,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.isEmpty())
        assertEquals("Newer local source", repository.sources[1L to "/merged"]?.title)
        assertEquals("Newest duplicate", repository.sources[2L to "/duplicate"]?.title)
        assertEquals(2, repository.sources.size)
    }

    @Test
    fun `newer local work is preserved and duplicate source ownership is reported`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "local",
                title = "Local",
                normalizedTitle = "local",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 20L,
            ),
        )
        repository.sources[1L to "/same"] = LocalTrackedWorkSource(
            workId = "other",
            source = 1L,
            url = "/same",
            title = "Other",
            confidence = 100,
            confirmation = LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
            createdAt = 1L,
            updatedAt = 20L,
        )
        val restorer = LocalTrackerBackupRestorer(repository)

        val errors = restorer.restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "local",
                    title = "Older",
                    normalizedTitle = "older",
                    status = "COMPLETED",
                    createdAt = 1L,
                    updatedAt = 10L,
                ),
                BackupLocalTrackedWork(
                    id = "imported",
                    title = "Imported",
                    normalizedTitle = "imported",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 30L,
                    sources = listOf(
                        BackupLocalTrackedWorkSource(
                            source = 1L,
                            url = "/same",
                            title = "Imported",
                            confidence = 100,
                            confirmation = "USER_CONFIRMED",
                            createdAt = 1L,
                            updatedAt = 30L,
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Local", repository.works["local"]?.title)
        assertEquals(null, repository.works["imported"])
        assertEquals(1, repository.sources.size)
        assertTrue(errors.any { it.contains("source") })
    }

    @Test
    fun `metadata is restored and legacy rows retain nullable defaults`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "seed",
                title = "Seed",
                normalizedTitle = "seed",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val restorer = LocalTrackerBackupRestorer(repository)

        val errors = restorer.restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "metadata",
                    title = "Metadata",
                    normalizedTitle = "metadata",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 2L,
                    score = 8.5,
                    hasScore = true,
                    scoreScaleVersion = 1,
                    startDate = 10L,
                    finishDate = 20L,
                    hasStartDate = true,
                    hasFinishDate = true,
                ),
                BackupLocalTrackedWork(
                    id = "legacy",
                    title = "Legacy",
                    normalizedTitle = "legacy",
                    status = "PLANNED",
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
        )

        assertTrue(errors.isEmpty())
        assertEquals(8.5, repository.works["metadata"]?.score)
        assertEquals(10L, repository.works["metadata"]?.startDate)
        assertEquals(20L, repository.works["metadata"]?.finishDate)
        assertEquals(null, repository.works["legacy"]?.score)
        assertEquals(null, repository.works["legacy"]?.startDate)
        assertEquals(null, repository.works["legacy"]?.finishDate)
    }

    @Test
    fun `legacy local score is migrated during backup restore`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "seed",
                title = "Seed",
                normalizedTitle = "seed",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val errors = LocalTrackerBackupRestorer(repository).restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "legacy-score",
                    title = "Legacy Score",
                    normalizedTitle = "legacy-score",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 2L,
                    score = 8.0,
                    hasScore = true,
                ),
            ),
        )

        assertTrue(errors.isEmpty())
        assertEquals(80.0, repository.works["legacy-score"]?.score)
    }

    @Test
    fun `invalid metadata dates are rejected without writing a work`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "seed",
                title = "Seed",
                normalizedTitle = "seed",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val errors = LocalTrackerBackupRestorer(repository).restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "invalid",
                    title = "Invalid",
                    normalizedTitle = "invalid",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 2L,
                    startDate = 20L,
                    finishDate = 10L,
                    hasStartDate = true,
                    hasFinishDate = true,
                ),
            ),
        )

        assertTrue(errors.single().contains("invalid payload"))
        assertEquals(null, repository.works["invalid"])
    }

    @Test
    fun `invalid source confirmation is rejected before writing a work`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "seed",
                title = "Seed",
                normalizedTitle = "seed",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val errors = LocalTrackerBackupRestorer(repository).restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "invalid-source",
                    title = "Invalid source",
                    normalizedTitle = "invalid-source",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 2L,
                    sources = listOf(
                        BackupLocalTrackedWorkSource(
                            source = 1L,
                            url = "/source",
                            title = "Invalid source",
                            confidence = 100,
                            confirmation = "NOT_A_CONFIRMATION",
                            createdAt = 1L,
                            updatedAt = 2L,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.single().contains("invalid source confirmation"))
        assertEquals(null, repository.works["invalid-source"])
        assertTrue(repository.sources.isEmpty())
    }

    @Test
    fun `populated source and progress payloads remove stale children`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "graph",
                title = "Graph",
                normalizedTitle = "graph",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        repository.sources[1L to "/keep"] = graphSource("/keep")
        repository.sources[1L to "/stale"] = graphSource("/stale")
        repository.sourceProgresses += graphProgress("/keep")
        repository.sourceProgresses += graphProgress("/stale")

        val errors = LocalTrackerBackupRestorer(repository).restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "graph",
                    title = "Graph",
                    normalizedTitle = "graph",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 2L,
                    sources = listOf(
                        BackupLocalTrackedWorkSource(
                            source = 1L,
                            url = "/keep",
                            title = "Graph",
                            confidence = 100,
                            confirmation = "USER_CONFIRMED",
                            createdAt = 1L,
                            updatedAt = 2L,
                        ),
                    ),
                    sourceProgress = listOf(
                        eu.kanade.tachiyomi.data.backup.models.BackupLocalTrackedWorkSourceProgress(
                            source = 1L,
                            url = "/keep",
                            chapterNumber = 2.0,
                            hasChapterNumber = true,
                            chapterUrl = "/keep-2",
                            chapterLabel = "Chapter 2",
                            progressAt = 2L,
                            updatedAt = 2L,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.isEmpty())
        assertEquals(setOf("/keep"), repository.sources.values.map { it.url }.toSet())
        assertEquals(setOf("/keep"), repository.sourceProgresses.map { it.url }.toSet())
    }

    @Test
    fun `marked empty child collections clear the current graph`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "empty-graph",
                title = "Empty graph",
                normalizedTitle = "empty-graph",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        repository.sources[1L to "/stale"] = graphSource("/stale").copy(workId = "empty-graph")
        repository.sourceProgresses += graphProgress("/stale").copy(workId = "empty-graph")

        val errors = LocalTrackerBackupRestorer(repository).restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "empty-graph",
                    title = "Empty graph",
                    normalizedTitle = "empty-graph",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 2L,
                    hasSources = true,
                    hasLists = true,
                    hasSourceProgress = true,
                ),
            ),
        )

        assertTrue(errors.isEmpty())
        assertTrue(repository.sources.isEmpty())
        assertTrue(repository.sourceProgresses.isEmpty())
    }

    @Test
    fun `partial row failure rolls back the local tracker graph`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "seed",
                title = "Seed",
                normalizedTitle = "seed",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            failOnSource = true,
        )

        val errors = LocalTrackerBackupRestorer(repository).restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "partial",
                    title = "Partial",
                    normalizedTitle = "partial",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 2L,
                    sources = listOf(
                        BackupLocalTrackedWorkSource(
                            source = 1L,
                            url = "/partial",
                            title = "Partial",
                            confidence = 100,
                            confirmation = "USER_CONFIRMED",
                            createdAt = 1L,
                            updatedAt = 2L,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.single().contains("partial"))
        assertEquals(null, repository.works["partial"])
        assertTrue(repository.sources.isEmpty())
    }

    @Test
    fun `rollback failure is included in the restore error`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "seed",
                title = "Seed",
                normalizedTitle = "seed",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            failOnSource = true,
            failOnDeleteWork = true,
        )

        val errors = LocalTrackerBackupRestorer(repository).restore(
            listOf(
                BackupLocalTrackedWork(
                    id = "partial",
                    title = "Partial",
                    normalizedTitle = "partial",
                    status = "READING",
                    createdAt = 1L,
                    updatedAt = 2L,
                    sources = listOf(
                        BackupLocalTrackedWorkSource(
                            source = 1L,
                            url = "/partial",
                            title = "Partial",
                            confidence = 100,
                            confirmation = "USER_CONFIRMED",
                            createdAt = 1L,
                            updatedAt = 2L,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(errors.single().contains("rollback failed"))
    }

    @Test
    fun `cancellation during local restore propagates`() = kotlinx.coroutines.test.runTest {
        val repository = FakeLocalTrackerRepository(
            LocalTrackedWork(
                id = "seed",
                title = "Seed",
                normalizedTitle = "seed",
                status = tachiyomi.domain.tracker.model.LocalTrackedWorkStatus.READING,
                lastChapterSource = null,
                lastChapterNumber = null,
                lastChapterUrl = null,
                lastChapterLabel = null,
                lastProgressAt = null,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            failure = CancellationException("cancelled"),
        )

        var thrown: CancellationException? = null
        try {
            LocalTrackerBackupRestorer(repository).restore(
                listOf(
                    BackupLocalTrackedWork(
                        id = "cancelled",
                        title = "Cancelled",
                        normalizedTitle = "cancelled",
                        status = "READING",
                        createdAt = 1L,
                        updatedAt = 2L,
                    ),
                ),
            )
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown != null)
    }

    private class FakeLocalTrackerRepository(
        initial: LocalTrackedWork,
        private val failure: Throwable? = null,
        private val failOnSource: Boolean = false,
        private val failOnDeleteWork: Boolean = false,
    ) : LocalTrackerRepository {
        val works = linkedMapOf(initial.id to initial)
        val sources = linkedMapOf<Pair<Long, String>, LocalTrackedWorkSource>()
        val sourceProgresses = mutableListOf<LocalTrackedWorkSourceProgress>()
        val lists = linkedMapOf<String, MutableList<String>>()
        val listEntries = linkedMapOf<String, MutableList<LocalTrackedWorkList>>()
        private val flow = MutableStateFlow(works.values.toList())
        private val sourceLookupFlow = MutableStateFlow<String?>(null)

        override fun getAllWorksAsFlow(): Flow<List<LocalTrackedWork>> = flow
        override suspend fun getWork(id: String) = works[id]
        override suspend fun upsertWork(work: LocalTrackedWork) {
            failure?.let { throw it }
            works[work.id] = work
            flow.value = works.values.toList()
        }
        override suspend fun deleteWork(id: String) {
            if (failOnDeleteWork) error("delete failed")
            works.remove(id)
        }
        override suspend fun getSources(workId: String) = sources.values.filter { it.workId == workId }
        override suspend fun getWorkIdBySourceUrl(source: Long, url: String) = sources[source to url]?.workId
        override fun observeWorkIdBySourceUrl(source: Long, url: String): Flow<String?> =
            sourceLookupFlow
        override suspend fun recordProgress(workId: String, source: Long, chapterNumber: Double?, chapterUrl: String, chapterLabel: String, progressAt: Long) = Unit
        override suspend fun upsertSource(source: LocalTrackedWorkSource) {
            if (failOnSource) error("source write failed")
            sources[source.source to source.url] = source
            sourceLookupFlow.value = source.workId
        }
        override suspend fun deleteSource(workId: String, source: Long, url: String) {
            sources.remove(source to url)
            deleteSourceProgress(workId, source, url)
            sourceLookupFlow.value = null
        }
        override suspend fun getSourceProgress(workId: String, source: Long, url: String): LocalTrackedWorkSourceProgress? =
            sourceProgresses.singleOrNull { it.workId == workId && it.source == source && it.url == url }
        override suspend fun upsertSourceProgress(progress: LocalTrackedWorkSourceProgress) {
            sourceProgresses.removeIf { it.workId == progress.workId && it.source == progress.source && it.url == progress.url }
            sourceProgresses += progress
        }
        override suspend fun recordSourceProgress(progress: LocalTrackedWorkSourceProgress) {
            upsertSourceProgress(progress)
        }
        override suspend fun deleteSourceProgress(workId: String, source: Long, url: String) {
            sourceProgresses.removeIf { it.workId == workId && it.source == source && it.url == url }
        }
        override suspend fun setInheritanceOptedOut(workId: String, source: Long, url: String, optedOut: Boolean, updatedAt: Long) = Unit
        override suspend fun getLists(workId: String) = this.lists[workId]?.toList().orEmpty()
        override suspend fun replaceLists(workId: String, lists: List<String>) {
            this.lists[workId] = lists.toMutableList()
            this.listEntries[workId] = lists.map { LocalTrackedWorkList(it, 1L) }.toMutableList()
        }
        override suspend fun getListEntries(workId: String) = this.listEntries[workId]?.toList()
            ?: this.lists[workId]?.map { LocalTrackedWorkList(it, 1L) }.orEmpty()
        override suspend fun replaceListEntries(workId: String, lists: List<LocalTrackedWorkList>) {
            this.listEntries[workId] = lists.toMutableList()
            this.lists[workId] = lists.map { it.name }.toMutableList()
        }
    }
}
