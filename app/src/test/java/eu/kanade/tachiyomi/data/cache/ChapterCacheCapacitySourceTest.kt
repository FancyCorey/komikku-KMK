package eu.kanade.tachiyomi.data.cache

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ChapterCacheCapacitySourceTest {

    private val source = File(
        "src/main/java/eu/kanade/tachiyomi/data/cache/ChapterCache.kt",
    ).readText()

    @Test
    fun `cache size changes normalize then resize the live cache in place`() {
        assertTrue(source.contains("ReaderEffectiveResourcePolicy.effectiveCacheSizeBytes"))
        assertTrue(source.contains("ChapterCacheCapacityPolicy.apply(rawCacheSizeMb, diskCache::setMaxSize)"))
        assertFalse(source.contains("cacheSize().get().toLong()"))
        assertFalse(source.contains("it.toLong()"))
        assertFalse(source.contains("oldCache.close()"))
        assertFalse(source.contains("private var diskCache"))
        assertFalse(source.contains("logcat(LogPriority.WARN, e)"))
        assertTrue(source.contains("Failed to put page list to cache"))
        assertTrue(source.contains("Failed to remove file from cache"))
    }
}
