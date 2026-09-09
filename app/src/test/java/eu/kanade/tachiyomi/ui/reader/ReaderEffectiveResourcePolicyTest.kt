package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderEffectiveResourcePolicyTest {

    @Test
    fun `defaults retain the established reader settings`() {
        val config = ReaderEffectiveResourcePolicy.resolve(
            rawWorkerCount = ReaderEffectiveResourcePolicy.DEFAULT_WORKER_COUNT,
            rawPreloadSize = ReaderEffectiveResourcePolicy.DEFAULT_PRELOAD_SIZE,
            rawCacheSizeMb = "75",
            rawArchiveReaderMode = 0,
        )

        assertEquals(2, config.workerCount)
        assertEquals(10, config.preloadSize)
        assertEquals(75L, config.cacheSizeMb)
        assertEquals(75L * 1024L * 1024L, config.cacheSizeBytes)
        assertEquals(12, config.queueCapacity)
    }

    @Test
    fun `worker count never starves the loader or exceeds the existing UI maximum`() {
        assertEquals(
            1,
            ReaderEffectiveResourcePolicy.resolve(0, 10, "75", 0).workerCount,
        )
        assertEquals(
            1,
            ReaderEffectiveResourcePolicy.resolve(-100, 10, "75", 0).workerCount,
        )
        assertEquals(
            4,
            ReaderEffectiveResourcePolicy.resolve(500, 10, "75", 0).workerCount,
        )
    }

    @Test
    fun `preload values stay within the established reader UI range`() {
        assertEquals(
            4,
            ReaderEffectiveResourcePolicy.resolve(2, 0, "75", 0).preloadSize,
        )
        assertEquals(
            4,
            ReaderEffectiveResourcePolicy.resolve(2, -100, "75", 0).preloadSize,
        )
        assertEquals(
            20,
            ReaderEffectiveResourcePolicy.resolve(2, 999, "75", 0).preloadSize,
        )
    }

    @Test
    fun `cache parser falls back or clamps without throwing`() {
        assertEquals(75L, ReaderEffectiveResourcePolicy.resolve(2, 10, null, 0).cacheSizeMb)
        assertEquals(75L, ReaderEffectiveResourcePolicy.resolve(2, 10, "not-a-number", 0).cacheSizeMb)
        assertEquals(50L, ReaderEffectiveResourcePolicy.resolve(2, 10, "0", 0).cacheSizeMb)
        assertEquals(50L, ReaderEffectiveResourcePolicy.resolve(2, 10, "-1", 0).cacheSizeMb)
        assertEquals(5000L, ReaderEffectiveResourcePolicy.resolve(2, 10, Long.MAX_VALUE.toString(), 0).cacheSizeMb)
        assertEquals(
            75L,
            ReaderEffectiveResourcePolicy.resolve(2, 10, "999999999999999999999999", 0).cacheSizeMb,
        )
        assertEquals(75L * 1024L * 1024L, ReaderEffectiveResourcePolicy.effectiveCacheSizeBytes("bad"))
        assertEquals(5000L * 1024L * 1024L, ReaderEffectiveResourcePolicy.effectiveCacheSizeBytes("9000"))
    }

    @Test
    fun `queue capacity is derived from the effective worker and preload limits`() {
        assertEquals(5, ReaderEffectiveResourcePolicy.resolve(0, 0, "75", 0).queueCapacity)
        assertEquals(24, ReaderEffectiveResourcePolicy.resolve(100, 100, "75", 0).queueCapacity)
    }

    @Test
    fun `invalid archive mode falls back to file while valid modes remain intact`() {
        assertEquals(0, ReaderEffectiveResourcePolicy.resolve(2, 10, "75", -1).archiveReaderMode)
        assertEquals(0, ReaderEffectiveResourcePolicy.resolve(2, 10, "75", 0).archiveReaderMode)
        assertEquals(1, ReaderEffectiveResourcePolicy.resolve(2, 10, "75", 1).archiveReaderMode)
        assertEquals(2, ReaderEffectiveResourcePolicy.resolve(2, 10, "75", 2).archiveReaderMode)
    }
}
