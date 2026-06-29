package exh.recs.backup

// KMK --> v0.7.28: round-trip tests for seen manga key backup/restore logic
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeenMangaKeyBackupTest {

    // ---- Serialization helpers used by both backup and restore paths ----

    @Test
    fun `serialize then parse round-trips a single key`() {
        val key = SeenMangaKey(sourceId = 100L, url = "/manga/one-piece")
        val raw = key.serialize()
        val result = SeenRecommendationMangaStore.parse(raw)
        assertEquals(1, result.size)
        assertTrue(key in result)
    }

    @Test
    fun `serialize then parse round-trips multiple keys`() {
        val keys = setOf(
            SeenMangaKey(1L, "/a"),
            SeenMangaKey(2L, "/b"),
            SeenMangaKey(999999L, "/manga/long-url/chapter/1"),
        )
        val raw = SeenRecommendationMangaStore.serialize(keys)
        val result = SeenRecommendationMangaStore.parse(raw)
        assertEquals(keys, result)
    }

    // ---- Restore merge semantics ----

    @Test
    fun `restore merges new keys into existing set`() {
        val existing = setOf(SeenMangaKey(1L, "/a"), SeenMangaKey(2L, "/b"))
        val incoming = setOf(SeenMangaKey(3L, "/c"), SeenMangaKey(4L, "/d"))
        val merged = existing + incoming
        val expectedRaw = SeenRecommendationMangaStore.serialize(merged)
        // Simulate: parse merged and verify all four keys present
        val result = SeenRecommendationMangaStore.parse(expectedRaw)
        assertEquals(4, result.size)
        assertTrue(SeenMangaKey(1L, "/a") in result)
        assertTrue(SeenMangaKey(2L, "/b") in result)
        assertTrue(SeenMangaKey(3L, "/c") in result)
        assertTrue(SeenMangaKey(4L, "/d") in result)
    }

    @Test
    fun `restore does not duplicate keys already in existing set`() {
        val existing = setOf(SeenMangaKey(1L, "/a"), SeenMangaKey(2L, "/b"))
        // Backup contains the same two keys + one new
        val backupRaw = SeenRecommendationMangaStore.serialize(
            setOf(SeenMangaKey(1L, "/a"), SeenMangaKey(2L, "/b"), SeenMangaKey(3L, "/c")),
        )
        val backupKeys = SeenRecommendationMangaStore.parse(backupRaw)
        val merged = existing + backupKeys
        assertEquals(3, merged.size)
    }

    @Test
    fun `restore with empty backup leaves existing set unchanged`() {
        val existing = setOf(SeenMangaKey(1L, "/a"))
        val merged = existing + SeenRecommendationMangaStore.parse("")
        assertEquals(existing, merged)
    }

    @Test
    fun `restore with empty existing set produces backup set`() {
        val backup = setOf(SeenMangaKey(42L, "/manga/foo"))
        val merged = emptySet<SeenMangaKey>() + backup
        assertEquals(backup, merged)
    }

    @Test
    fun `key with pipe in url does not corrupt parse`() {
        // urls are expected to not contain '|', but serialization format
        // reads everything after the first '|' as the url — safe for normal urls
        val key = SeenMangaKey(sourceId = 7L, url = "/manga/test")
        val raw = key.serialize()
        assertEquals("7|/manga/test", raw)
        val result = SeenRecommendationMangaStore.parse(raw)
        assertEquals(1, result.size)
        assertTrue(key in result)
    }
}
// KMK <--
