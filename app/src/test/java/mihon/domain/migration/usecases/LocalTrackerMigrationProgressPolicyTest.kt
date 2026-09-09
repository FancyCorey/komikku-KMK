package mihon.domain.migration.usecases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.tracker.model.LocalTrackedWorkSourceProgress

class LocalTrackerMigrationProgressPolicyTest {

    @Test
    fun `recognized exact chapter number is selected`() {
        val progress = progress(12.0)
        val target = listOf(chapter("/target/12", "Chapter 12", 12.0))

        assertEquals(target.single(), LocalTrackerMigrationProgressPolicy.exactTargetChapter(progress, target))
    }

    @Test
    fun `different chapter number is not guessed`() {
        val progress = progress(12.0)
        val target = listOf(chapter("/target/13", "Chapter 13", 13.0))

        assertNull(LocalTrackerMigrationProgressPolicy.exactTargetChapter(progress, target))
    }

    @Test
    fun `unrecognized progress is not mapped`() {
        val progress = progress(null)
        val target = listOf(chapter("/target/12", "Chapter 12", 12.0))

        assertNull(LocalTrackerMigrationProgressPolicy.exactTargetChapter(progress, target))
    }

    @Test
    fun `duplicate recognized chapter number is not mapped`() {
        val progress = progress(12.0)
        val target = listOf(
            chapter("/target/12", "Chapter 12", 12.0),
            chapter("/target/12-alt", "Chapter 12", 12.0),
        )

        assertNull(LocalTrackerMigrationProgressPolicy.exactTargetChapter(progress, target))
    }

    private fun progress(chapterNumber: Double?) = LocalTrackedWorkSourceProgress(
        workId = "work",
        source = 1L,
        url = "/origin",
        chapterNumber = chapterNumber,
        chapterUrl = "/origin/chapter",
        chapterLabel = "Chapter",
        progressAt = 100L,
        updatedAt = 100L,
    )

    private fun chapter(url: String, name: String, number: Double) = Chapter.create().copy(
        id = number.toLong(),
        mangaId = 2L,
        url = url,
        name = name,
        chapterNumber = number,
    )
}
