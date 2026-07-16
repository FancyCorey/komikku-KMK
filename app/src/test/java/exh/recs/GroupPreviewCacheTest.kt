package exh.recs

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

// KMK v0.8.6 -->
class GroupPreviewCacheTest {

    @AfterEach
    fun tearDown() {
        GroupPreviewCache.invalidateAll()
    }

    private fun manga(title: String): Manga = Manga.create().copy(ogTitle = title)

    private fun key(
        groupFingerprint: String = "group-1",
        sourceId: Long = 1L,
        language: String = "en",
        normalizedSeed: String = "seed",
        visibilityFingerprint: String = "vis-1",
        previewBudget: Int = 10,
    ) = GroupPreviewCache.Key(groupFingerprint, sourceId, language, normalizedSeed, visibilityFingerprint, previewBudget)

    @Test
    fun `miss on empty cache`() {
        assertNull(GroupPreviewCache.get(key()))
    }

    @Test
    fun `put then get returns the same list for an identical key`() {
        val mangas = listOf(manga("A"), manga("B"))
        GroupPreviewCache.put(key(), mangas)
        assertEquals(mangas, GroupPreviewCache.get(key()))
    }

    @Test
    fun `differing any single key component is a miss`() {
        GroupPreviewCache.put(key(), listOf(manga("A")))
        assertNull(GroupPreviewCache.get(key(sourceId = 2L)))
        assertNull(GroupPreviewCache.get(key(language = "ja")))
        assertNull(GroupPreviewCache.get(key(normalizedSeed = "other-seed")))
        assertNull(GroupPreviewCache.get(key(visibilityFingerprint = "vis-2")))
        assertNull(GroupPreviewCache.get(key(previewBudget = 20)))
        assertNull(GroupPreviewCache.get(key(groupFingerprint = "group-2")))
    }

    @Test
    fun `entry expires after TTL`() {
        val mangas = listOf(manga("A"))
        val k = key()
        GroupPreviewCache.put(k, mangas, nowMs = 1_000L)
        assertNotNull(GroupPreviewCache.get(k, nowMs = 1_000L + GroupPreviewCache.TTL_MS - 1))
        assertNull(GroupPreviewCache.get(k, nowMs = 1_000L + GroupPreviewCache.TTL_MS + 1))
    }

    @Test
    fun `oldest entry is evicted once MAX_ENTRIES is exceeded`() {
        repeat(GroupPreviewCache.MAX_ENTRIES) { i ->
            GroupPreviewCache.put(key(sourceId = i.toLong()), listOf(manga("m$i")))
        }
        assertEquals(GroupPreviewCache.MAX_ENTRIES, GroupPreviewCache.size())
        assertNotNull(GroupPreviewCache.get(key(sourceId = 0L)))

        // One more insert should evict the oldest (sourceId = 0)
        GroupPreviewCache.put(key(sourceId = 9999L), listOf(manga("overflow")))
        assertEquals(GroupPreviewCache.MAX_ENTRIES, GroupPreviewCache.size())
        assertNull(GroupPreviewCache.get(key(sourceId = 0L)))
        assertNotNull(GroupPreviewCache.get(key(sourceId = 9999L)))
    }

    @Test
    fun `invalidateAll clears every entry`() {
        GroupPreviewCache.put(key(), listOf(manga("A")))
        GroupPreviewCache.invalidateAll()
        assertEquals(0, GroupPreviewCache.size())
        assertNull(GroupPreviewCache.get(key()))
    }
}
// KMK <--
