package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class DownloadChapterDeletionTest {

    @Test
    fun `failed directory deletion is excluded from returned chapters`() {
        val first = mockk<UniFile>()
        val second = mockk<UniFile>()
        every { first.delete() } returns true
        every { second.delete() } returns false
        val firstChapter = Chapter.create().copy(id = 1L)
        val secondChapter = Chapter.create().copy(id = 2L)

        val deleted = deleteChapterDirectories(
            listOf(firstChapter to first, secondChapter to second),
        )

        assertEquals(listOf(firstChapter), deleted)
    }
}
