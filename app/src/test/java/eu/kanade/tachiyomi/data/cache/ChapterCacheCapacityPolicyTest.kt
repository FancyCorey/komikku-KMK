package eu.kanade.tachiyomi.data.cache

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ChapterCacheCapacityPolicyTest {

    @Test
    fun `valid size is applied in bytes without rewriting the raw value`() {
        var appliedBytes: Long? = null

        val result = ChapterCacheCapacityPolicy.apply("250") { appliedBytes = it }

        assertInstanceOf(ChapterCacheCapacityUpdate.Applied::class.java, result)
        assertEquals(250L * 1024L * 1024L, appliedBytes)
    }

    @Test
    fun `malformed restored size applies the established default`() {
        var appliedBytes: Long? = null

        val result = ChapterCacheCapacityPolicy.apply("not-a-number") { appliedBytes = it }

        assertEquals(75L * 1024L * 1024L, result.effectiveBytes)
        assertEquals(result.effectiveBytes, appliedBytes)
    }

    @Test
    fun `ordinary setter failure is isolated without retaining its exception`() {
        val result = ChapterCacheCapacityPolicy.apply("500") {
            throw IllegalStateException("closed cache")
        }

        assertEquals(
            ChapterCacheCapacityUpdate.Failed(500L * 1024L * 1024L),
            result,
        )
    }

    @Test
    fun `cancellation is never converted to a failed capacity update`() {
        assertThrows(CancellationException::class.java) {
            ChapterCacheCapacityPolicy.apply("500") {
                throw CancellationException("cancelled")
            }
        }
    }
}
