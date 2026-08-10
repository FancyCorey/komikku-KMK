package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository

// KMK -->
private class FakeChapterRepository : ChapterRepository {
    val byId = mutableMapOf<Long, Chapter>()
    var throwOnUpdate: RuntimeException? = null

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> = throw NotImplementedError()
    override suspend fun update(chapterUpdate: ChapterUpdate) {
        throwOnUpdate?.let { throw it }
        val existing = byId[chapterUpdate.id] ?: return
        byId[chapterUpdate.id] = existing.copy(
            read = chapterUpdate.read ?: existing.read,
            bookmark = chapterUpdate.bookmark ?: existing.bookmark,
            lastPageRead = chapterUpdate.lastPageRead ?: existing.lastPageRead,
        )
    }
    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        chapterUpdates.forEach { update(it) }
    }
    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = throw NotImplementedError()
    override suspend fun getChapterByMangaId(mangaId: Long, applyFilter: Boolean) = throw NotImplementedError()
    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> = throw NotImplementedError()
    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long) = emptyFlow<List<String>>()
    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long) = throw NotImplementedError()
    override suspend fun getChapterById(id: Long): Chapter? = byId[id]
    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyFilter: Boolean) = emptyFlow<List<Chapter>>()
    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long) = null
    override suspend fun getChapterByUrl(url: String): List<Chapter> = throw NotImplementedError()
    override suspend fun getMergedChapterByMangaId(mangaId: Long, applyFilter: Boolean) = throw NotImplementedError()
    override suspend fun getMergedChapterByMangaIdAsFlow(mangaId: Long, applyFilter: Boolean) = emptyFlow<List<Chapter>>()
    override suspend fun getScanlatorsByMergeId(mangaId: Long): List<String> = throw NotImplementedError()
    override fun getScanlatorsByMergeIdAsFlow(mangaId: Long) = emptyFlow<List<String>>()
}

class ChapterUndoServiceRestoreTest {

    private val repo = FakeChapterRepository()
    private val getChapter = GetChapter(repo)
    private val updateChapter = UpdateChapter(repo)
    private val service = ChapterUndoService(getChapter, updateChapter)
    private val preferenceStore = FakePreferenceStore()
    private val sourcePreferences = SourcePreferences(preferenceStore)

    @AfterEach
    fun tearDown() {
        ChapterUndoJournal.clear()
    }

    private fun chapter(id: Long, read: Boolean, bookmark: Boolean) = Chapter(
        id = id, mangaId = 1L, read = read, bookmark = bookmark, lastPageRead = 0L, dateFetch = 0L,
        sourceOrder = 0L, url = "/c/$id", name = "Chapter $id", dateUpload = 0L, chapterNumber = id.toDouble(),
        scanlator = null, lastModifiedAt = 0L, version = 0L, memo = JsonObject(emptyMap()),
    )

    @Test
    fun `undo restores the previous bookmark value`() = runTest {
        repo.byId[1L] = chapter(1L, read = false, bookmark = true)
        val entry = ChapterJournalEntry(
            id = ChapterJournalEntry.newId(),
            timestamp = 0L,
            actionType = ChapterJournalActionType.BOOKMARK,
            chapterId = 1L,
            previousRead = null,
            expectedPostRead = null,
            previousBookmark = false,
            expectedPostBookmark = true,
        )
        ChapterUndoJournal.record(entry)

        assertEquals(GroupUndoResult.RESTORED, service.undo(entry.id))
        assertEquals(false, repo.byId[1L]?.bookmark)
        assertTrue(ChapterUndoJournal.isEmpty())
    }

    @Test
    fun `undo restores page progress reset when marking unread`() = runTest {
        repo.byId[1L] = chapter(1L, read = false, bookmark = false)
            .copy(lastPageRead = 0L)
        val entry = ChapterJournalEntry(
            id = ChapterJournalEntry.newId(),
            timestamp = 0L,
            actionType = ChapterJournalActionType.UNREAD,
            chapterId = 1L,
            previousRead = true,
            expectedPostRead = false,
            previousLastPageRead = 123L,
            expectedPostLastPageRead = 0L,
            previousBookmark = null,
            expectedPostBookmark = null,
        )
        ChapterUndoJournal.record(entry)

        assertEquals(GroupUndoResult.RESTORED, service.undo(entry.id))
        assertEquals(true, repo.byId[1L]?.read)
        assertEquals(123L, repo.byId[1L]?.lastPageRead)
    }

    @Test
    fun `undo refuses when the read state changed after journaling`() = runTest {
        repo.byId[1L] = chapter(1L, read = false, bookmark = false) // diverged from expectedPostRead=true
        val entry = ChapterJournalEntry(
            id = ChapterJournalEntry.newId(),
            timestamp = 0L,
            actionType = ChapterJournalActionType.READ,
            chapterId = 1L,
            previousRead = false,
            expectedPostRead = true,
            previousBookmark = null,
            expectedPostBookmark = null,
        )
        ChapterUndoJournal.record(entry)

        assertEquals(GroupUndoResult.CONFLICT, service.undo(entry.id))
        assertEquals(1, ChapterUndoJournal.snapshot().size)
    }

    @Test
    fun `a persistence failure during restore is reported and remains undoable`() = runTest {
        repo.byId[1L] = chapter(1L, read = false, bookmark = true)
        val entry = ChapterJournalEntry(
            id = ChapterJournalEntry.newId(),
            timestamp = 0L,
            actionType = ChapterJournalActionType.BOOKMARK,
            chapterId = 1L,
            previousRead = null,
            expectedPostRead = null,
            previousBookmark = false,
            expectedPostBookmark = true,
        )
        ChapterUndoJournal.record(entry)
        repo.throwOnUpdate = RuntimeException("boom")

        assertEquals(GroupUndoResult.FAILED, service.undo(entry.id))
        assertEquals(true, repo.byId[1L]?.bookmark)
        assertEquals(1, ChapterUndoJournal.snapshot().size)
    }

    @Test
    fun `recorder skips journaling but the caller's write is unaffected when the batch exceeds the 500-chapter bound`() {
        sourcePreferences.evaluationMode().set(true)
        val oversized = (1..501).map { chapter(it.toLong(), read = false, bookmark = false) }
        val entries = ChapterUndoRecorder.buildBookmarkEntries(sourcePreferences, oversized, true)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `recorder builds nothing when Evaluation Mode is disabled`() {
        sourcePreferences.evaluationMode().set(false)
        val entries = ChapterUndoRecorder.buildBookmarkEntries(sourcePreferences, listOf(chapter(1L, false, false)), true)
        assertTrue(entries.isEmpty())
    }
}
// KMK <--
