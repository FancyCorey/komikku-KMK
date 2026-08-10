package exh.recs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.MangaTaste

// KMK v0.8.6 -->
class GroupPreviewVisibilityFingerprintTest {

    private fun taste(source: Long, url: String, rating: Int) =
        MangaTaste(mangaId = 1L, source = source, url = url, title = "t", rating = rating, createdAt = 0L, updatedAt = 0L)

    private fun baseline(
        effectiveDisabledSourceIds: Set<Long> = setOf(1L),
        storedSourceOrder: List<Long> = listOf(1L, 2L),
        dislikedSourceRaw: String = "",
        qualityDislikedSourceRaw: String = "",
        seenKeys: Set<SeenMangaKey> = setOf(SeenMangaKey(1L, "/a")),
        tasteByKey: Map<MangaTasteKey, MangaTaste> = mapOf(MangaTasteKey(1L, "/a") to taste(1L, "/a", 1)),
        visibility: Any = "SHOW",
        hideKnownManga: Boolean = false,
        minChapterCount: Int = 0,
    ) = GroupPreviewVisibilityFingerprint.build(
        effectiveDisabledSourceIds, storedSourceOrder, dislikedSourceRaw, qualityDislikedSourceRaw,
        seenKeys, tasteByKey, visibility, hideKnownManga, minChapterCount,
    )

    @Test
    fun `identical inputs produce an identical fingerprint`() {
        assertEquals(baseline(), baseline())
    }

    @Test
    fun `a newly disabled source changes the fingerprint`() {
        assertNotEquals(baseline(), baseline(effectiveDisabledSourceIds = setOf(1L, 5L)))
    }

    @Test
    fun `a changed source order changes the fingerprint`() {
        assertNotEquals(baseline(), baseline(storedSourceOrder = listOf(2L, 1L)))
    }

    @Test
    fun `a newly disliked source (raw preference) changes the fingerprint`() {
        assertNotEquals(baseline(), baseline(dislikedSourceRaw = "1|pkg|1"))
    }

    @Test
    fun `a newly quality-disliked source changes the fingerprint`() {
        assertNotEquals(baseline(), baseline(qualityDislikedSourceRaw = "1|pkg|1"))
    }

    @Test
    fun `a newly-seen entry changes the fingerprint`() {
        assertNotEquals(baseline(), baseline(seenKeys = setOf(SeenMangaKey(1L, "/a"), SeenMangaKey(2L, "/b"))))
    }

    @Test
    fun `a changed taste rating changes the fingerprint even with the same entry count`() {
        val loved = mapOf(MangaTasteKey(1L, "/a") to taste(1L, "/a", 2))
        assertNotEquals(baseline(), baseline(tasteByKey = loved))
    }

    @Test
    fun `a newly-added taste entry changes the fingerprint`() {
        val twoEntries = mapOf(
            MangaTasteKey(1L, "/a") to taste(1L, "/a", 1),
            MangaTasteKey(2L, "/b") to taste(2L, "/b", -1),
        )
        assertNotEquals(baseline(), baseline(tasteByKey = twoEntries))
    }

    @Test
    fun `a changed rated-manga visibility setting changes the fingerprint`() {
        assertNotEquals(baseline(), baseline(visibility = "HIDE"))
    }

    @Test
    fun `toggling hide-known-manga changes the fingerprint`() {
        assertNotEquals(baseline(), baseline(hideKnownManga = true))
    }

    @Test
    fun `a changed minimum chapter count changes the fingerprint`() {
        assertNotEquals(baseline(), baseline(minChapterCount = 10))
    }
}
// KMK <--
