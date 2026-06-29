package exh.recs

// KMK -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeenRecommendationMangaStoreTest {

    @Test
    fun `parse empty string returns empty set`() {
        val result = SeenRecommendationMangaStore.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse blank string returns empty set`() {
        val result = SeenRecommendationMangaStore.parse("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse single entry returns one key`() {
        val result = SeenRecommendationMangaStore.parse("123|/manga/foo")
        assertEquals(1, result.size)
        assertTrue(SeenMangaKey(123L, "/manga/foo") in result)
    }

    @Test
    fun `parse multiple entries returns correct keys`() {
        val result = SeenRecommendationMangaStore.parse("1|/a;2|/b;3|/c")
        assertEquals(3, result.size)
        assertTrue(SeenMangaKey(1L, "/a") in result)
        assertTrue(SeenMangaKey(2L, "/b") in result)
        assertTrue(SeenMangaKey(3L, "/c") in result)
    }

    @Test
    fun `parse skips malformed entries without crashing`() {
        val result = SeenRecommendationMangaStore.parse("bad;1|/ok;also_bad")
        assertEquals(1, result.size)
        assertTrue(SeenMangaKey(1L, "/ok") in result)
    }

    @Test
    fun `serialize and parse round-trip`() {
        val original = setOf(
            SeenMangaKey(10L, "/manga/test"),
            SeenMangaKey(20L, "/manga/other"),
        )
        val serialized = SeenRecommendationMangaStore.serialize(original)
        val parsed = SeenRecommendationMangaStore.parse(serialized)
        assertEquals(original, parsed)
    }

    @Test
    fun `add returns set with new key`() {
        val existing = setOf(SeenMangaKey(1L, "/a"))
        val result = SeenRecommendationMangaStore.add(existing, SeenMangaKey(2L, "/b"))
        assertEquals(2, result.size)
        assertTrue(SeenMangaKey(2L, "/b") in result)
    }

    @Test
    fun `add does not duplicate existing key`() {
        val key = SeenMangaKey(1L, "/a")
        val existing = setOf(key)
        val result = SeenRecommendationMangaStore.add(existing, key)
        assertEquals(1, result.size)
    }

    @Test
    fun `remove returns set without key`() {
        val key = SeenMangaKey(1L, "/a")
        val existing = setOf(key, SeenMangaKey(2L, "/b"))
        val result = SeenRecommendationMangaStore.remove(existing, key)
        assertEquals(1, result.size)
        assertFalse(key in result)
    }

    @Test
    fun `remove non-existent key leaves set unchanged`() {
        val existing = setOf(SeenMangaKey(1L, "/a"))
        val result = SeenRecommendationMangaStore.remove(existing, SeenMangaKey(99L, "/none"))
        assertEquals(existing, result)
    }

    @Test
    fun `url containing pipe character parsed correctly`() {
        // The format is "sourceId|url", so url can contain pipes — only FIRST pipe is the separator
        val key = SeenMangaKey(5L, "/manga/foo|bar")
        val serialized = key.serialize()
        val parsed = SeenRecommendationMangaStore.parse(serialized)
        assertEquals(setOf(key), parsed)
    }
}
// KMK <--
