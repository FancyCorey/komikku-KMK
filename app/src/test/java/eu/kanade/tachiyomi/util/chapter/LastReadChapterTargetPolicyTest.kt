package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.manga.ChapterList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

// KMK -->
/**
 * Target-resolution contract for "Jump to last read".
 *
 * These are **pure policy tests**: they prove the resolution rule and the index arithmetic, not the
 * Compose scroll itself. Rendered scroll behavior is not covered because this repository does not
 * currently provide Compose UI test infrastructure.
 *
 * Run with:
 * `./gradlew :app:testDebugUnitTest --tests "*.LastReadChapterTargetPolicyTest"`
 */
class LastReadChapterTargetPolicyTest {

    private fun chapter(
        id: Long,
        read: Boolean = false,
        lastPageRead: Long = 0L,
        url: String = "/c/$id",
    ) = Chapter.create().copy(
        id = id,
        mangaId = 1L,
        url = url,
        name = "Chapter $id",
        read = read,
        lastPageRead = lastPageRead,
    )

    private fun item(
        id: Long,
        read: Boolean = false,
        lastPageRead: Long = 0L,
        url: String = "/c/$id",
        sourceName: String? = null,
    ) = ChapterList.Item(
        chapter = chapter(id, read, lastPageRead, url),
        downloadState = Download.State.NOT_DOWNLOADED,
        downloadProgress = 0,
        sourceName = sourceName,
        showScanlator = false,
    )

    private fun missing(count: Int) = ChapterList.MissingCount(id = "m-$count", count = count)

    // ---- No target cases ----

    @Test
    fun `an empty chapter list has no target`() {
        assertNull(LastReadChapterTargetPolicy.resolve(emptyList(), sortDescending = false))
        assertNull(LastReadChapterTargetPolicy.resolve(emptyList(), sortDescending = true))
    }

    @Test
    fun `a list with no read and no in-progress chapters has no target`() {
        val chapters = listOf(item(1L), item(2L), item(3L))
        assertNull(LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false))
        assertNull(LastReadChapterTargetPolicy.resolve(chapters, sortDescending = true))
    }

    @Test
    fun `a list containing only separators has no target`() {
        val chapters = listOf(missing(3), missing(2))
        assertNull(LastReadChapterTargetPolicy.resolve(chapters, sortDescending = true))
    }

    @Test
    fun `no target is produced rather than falling back to the last item in the list`() {
        // Guards the documented decision: an unread manga has no last-read chapter.
        val chapters = listOf(item(1L), item(2L), item(3L))
        assertNull(LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false))
    }

    // ---- Single and multiple read chapters ----

    @Test
    fun `one read chapter is the target in ascending order`() {
        val chapters = listOf(item(1L, read = true), item(2L), item(3L))
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        assertEquals(LastReadChapterTargetPolicy.Target(1L, 0), target)
    }

    @Test
    fun `one read chapter is the target in descending order`() {
        // Descending display: chapter 3 newest first. Only chapter 1 is read, so it sits last.
        val chapters = listOf(item(3L), item(2L), item(1L, read = true))
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = true)
        assertEquals(LastReadChapterTargetPolicy.Target(1L, 2), target)
    }

    @Test
    fun `with multiple read chapters ascending order targets the furthest along`() {
        // Ascending: 1,2,3 -- read through 2, so the last completed is chapter 2.
        val chapters = listOf(item(1L, read = true), item(2L, read = true), item(3L))
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        assertEquals(LastReadChapterTargetPolicy.Target(2L, 1), target)
    }

    @Test
    fun `with multiple read chapters descending order targets the furthest along`() {
        // Descending: 3,2,1 -- read through 2, which is nearer the top in reading terms.
        val chapters = listOf(item(3L), item(2L, read = true), item(1L, read = true))
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = true)
        assertEquals(LastReadChapterTargetPolicy.Target(2L, 1), target)
    }

    // ---- In-progress precedence ----

    @Test
    fun `a partially read chapter outranks a fully read one, ascending`() {
        val chapters = listOf(
            item(1L, read = true),
            item(2L, read = true),
            item(3L, lastPageRead = 7L),
        )
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        assertEquals(LastReadChapterTargetPolicy.Target(3L, 2), target)
    }

    @Test
    fun `a partially read chapter outranks a fully read one, descending`() {
        val chapters = listOf(
            item(3L, lastPageRead = 7L),
            item(2L, read = true),
            item(1L, read = true),
        )
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = true)
        assertEquals(LastReadChapterTargetPolicy.Target(3L, 0), target)
    }

    @Test
    fun `with several in-progress chapters the furthest along wins`() {
        val chapters = listOf(
            item(1L, lastPageRead = 3L),
            item(2L, lastPageRead = 5L),
            item(3L),
        )
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        assertEquals(LastReadChapterTargetPolicy.Target(2L, 1), target)
    }

    @Test
    fun `a read chapter that also has lastPageRead is treated as completed, not in progress`() {
        // read == true wins; lastPageRead is leftover position data on a finished chapter.
        val chapters = listOf(item(1L, read = true, lastPageRead = 9L), item(2L))
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        assertEquals(LastReadChapterTargetPolicy.Target(1L, 0), target)
    }

    // ---- Separators, merged manga, duplicate urls ----

    @Test
    fun `missing-count separators are skipped as candidates but still occupy an index`() {
        val chapters = listOf(
            item(1L, read = true),
            missing(2),
            item(4L, read = true),
            item(5L),
        )
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        // Index 2, not 1 -- the separator still shifts the rendered position.
        assertEquals(LastReadChapterTargetPolicy.Target(4L, 2), target)
    }

    @Test
    fun `duplicate chapter urls across sources resolve by chapter id, not url`() {
        val chapters = listOf(
            item(10L, read = true, url = "/same", sourceName = "Source A"),
            item(20L, read = true, url = "/same", sourceName = "Source B"),
            item(30L, url = "/same", sourceName = "Source A"),
        )
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        assertEquals(20L, target?.chapterId)
        assertEquals(1, target?.indexInList)
    }

    @Test
    fun `a merged manga list resolves across interleaved sources`() {
        val chapters = listOf(
            item(1L, read = true, sourceName = "Source A"),
            item(2L, read = true, sourceName = "Source B"),
            item(3L, lastPageRead = 2L, sourceName = "Source A"),
            item(4L, sourceName = "Source B"),
        )
        val target = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        assertEquals(LastReadChapterTargetPolicy.Target(3L, 2), target)
    }

    // ---- Stale / deleted target re-resolution ----

    @Test
    fun `currentIndexOf finds a chapter that is still present`() {
        val chapters = listOf(item(1L), item(2L, read = true), item(3L))
        assertEquals(1, LastReadChapterTargetPolicy.currentIndexOf(chapters, 2L))
    }

    @Test
    fun `currentIndexOf returns null for a deleted or filtered-out chapter`() {
        val chapters = listOf(item(1L), item(3L))
        assertNull(LastReadChapterTargetPolicy.currentIndexOf(chapters, 2L))
    }

    @Test
    fun `currentIndexOf tracks a chapter whose position moved after a refresh`() {
        val before = listOf(item(1L), item(2L, read = true))
        val after = listOf(item(9L), item(1L), item(2L, read = true))
        assertEquals(1, LastReadChapterTargetPolicy.currentIndexOf(before, 2L))
        assertEquals(2, LastReadChapterTargetPolicy.currentIndexOf(after, 2L))
    }

    @Test
    fun `currentIndexOf never matches a separator`() {
        val chapters = listOf(missing(2), item(1L, read = true))
        assertEquals(1, LastReadChapterTargetPolicy.currentIndexOf(chapters, 1L))
        assertNull(LastReadChapterTargetPolicy.currentIndexOf(chapters, 999L))
    }

    // ---- Absolute LazyColumn index arithmetic ----

    @Test
    fun `toLazyListIndex adds the live header offset`() {
        // 5 headers rendered + 10 chapters = 15 total; chapter index 3 -> absolute 8.
        assertEquals(8, LastReadChapterTargetPolicy.toLazyListIndex(15, 10, 3))
    }

    @Test
    fun `toLazyListIndex works with no headers at all`() {
        assertEquals(3, LastReadChapterTargetPolicy.toLazyListIndex(10, 10, 3))
    }

    @Test
    fun `toLazyListIndex handles a different header count without any hardcoded offset`() {
        // The portrait layout's header count is conditional; both of these must be correct.
        assertEquals(2 + 4, LastReadChapterTargetPolicy.toLazyListIndex(2 + 10, 10, 4))
        assertEquals(7 + 4, LastReadChapterTargetPolicy.toLazyListIndex(7 + 10, 10, 4))
    }

    @Test
    fun `toLazyListIndex refuses inconsistent layout numbers instead of scrolling wrongly`() {
        // Mid-recomposition the layout can report fewer items than the list holds.
        assertNull(LastReadChapterTargetPolicy.toLazyListIndex(3, 10, 3))
        assertNull(LastReadChapterTargetPolicy.toLazyListIndex(0, 10, 3))
        assertNull(LastReadChapterTargetPolicy.toLazyListIndex(15, 0, 0))
    }

    @Test
    fun `toLazyListIndex refuses an out-of-range chapter index`() {
        assertNull(LastReadChapterTargetPolicy.toLazyListIndex(15, 10, 10))
        assertNull(LastReadChapterTargetPolicy.toLazyListIndex(15, 10, -1))
    }

    // ---- Determinism / repeated resolution ----

    @Test
    fun `resolution is deterministic across repeated calls`() {
        val chapters = listOf(item(1L, read = true), item(2L, lastPageRead = 4L), item(3L))
        val first = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        val second = LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        assertEquals(first, second)
    }

    @Test
    fun `resolution never mutates read state or the input list`() {
        val chapters = listOf(item(1L, read = true), item(2L))
        val snapshot = chapters.map { (it as ChapterList.Item).chapter.copy() }
        LastReadChapterTargetPolicy.resolve(chapters, sortDescending = false)
        chapters.forEachIndexed { index, entry ->
            val chapter = (entry as ChapterList.Item).chapter
            assertEquals(snapshot[index].read, chapter.read)
            assertEquals(snapshot[index].lastPageRead, chapter.lastPageRead)
        }
    }
}
// KMK <--
