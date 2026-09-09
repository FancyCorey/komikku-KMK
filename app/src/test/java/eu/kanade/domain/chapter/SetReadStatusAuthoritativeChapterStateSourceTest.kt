package eu.kanade.domain.chapter

import eu.kanade.domain.download.interactor.DeleteDownload
import eu.kanade.domain.track.interactor.RecordLocalTrackedChapterProgress
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.repository.MangaRepository
import java.io.File

/**
 * Regression guard for merged/source-group chapter rows whose in-memory read flag can be stale
 * while the selected chapter ID still identifies the correct database row.
 */
class SetReadStatusAuthoritativeChapterStateSourceTest {

    private val source by lazy {
        File("src/main/java/eu/kanade/domain/chapter/interactor/SetReadStatus.kt").readText()
    }

    @Test
    fun `read status filtering uses the authoritative chapter row by id`() {
        val reload = source.indexOf("chapterRepository.getChapterById(chapter.id)")
        val filter = source.indexOf("}.filter {", reload)
        val update = source.indexOf("chapterRepository.updateAll(", filter)

        assertTrue(reload >= 0, "selected chapters must be reloaded by their database IDs")
        assertTrue(filter > reload, "read/unread filtering must happen after the authoritative reload")
        assertTrue(update > filter, "the filtered authoritative rows must be sent to the repository")
    }

    @Test
    fun `stale selected chapter still updates the authoritative row`() = runTest {
        val chapterRepository = mockk<ChapterRepository>()
        val downloadPreferences = mockk<DownloadPreferences>(relaxed = true)
        val deleteDownload = mockk<DeleteDownload>(relaxed = true)
        val mangaRepository = mockk<MangaRepository>(relaxed = true)
        val mergedChapters = mockk<GetMergedChaptersByMangaId>(relaxed = true)
        val localProgress = mockk<RecordLocalTrackedChapterProgress>(relaxed = true)
        val authoritative = Chapter.create().copy(
            id = 128628L,
            mangaId = 30L,
            name = "Chapter 37",
            read = false,
        )
        val staleSelection = authoritative.copy(read = true)

        coEvery { chapterRepository.getChapterById(authoritative.id) } returns authoritative
        coEvery { chapterRepository.updateAll(any()) } just Runs
        every { downloadPreferences.removeAfterMarkedAsRead().get() } returns false

        val result = eu.kanade.domain.chapter.interactor.SetReadStatus(
            downloadPreferences = downloadPreferences,
            deleteDownload = deleteDownload,
            mangaRepository = mangaRepository,
            chapterRepository = chapterRepository,
            getMergedChaptersByMangaId = mergedChapters,
            recordLocalTrackedChapterProgress = localProgress,
        ).await(read = true, chapters = arrayOf(staleSelection), manually = false)

        assertTrue(result is eu.kanade.domain.chapter.interactor.SetReadStatus.Result.Success)
        coVerify(exactly = 1) {
            chapterRepository.updateAll(
                match { updates ->
                    updates.single().id == authoritative.id && updates.single().read == true
                },
            )
        }
    }
}
