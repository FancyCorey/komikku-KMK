package tachiyomi.domain.tracker.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalTrackedSourceProgressPolicyTest {
    private fun progress(chapter: Double?, at: Long) = LocalTrackedWorkSourceProgress(
        workId = "work-1",
        source = 1,
        url = "/example",
        chapterNumber = chapter,
        chapterUrl = "/chapter",
        chapterLabel = "Chapter",
        progressAt = at,
        updatedAt = at,
    )

    @Test
    fun `higher chapter wins even when timestamp is older`() {
        assertTrue(LocalTrackedSourceProgressPolicy.accepts(progress(12.0, 3_000), progress(13.0, 2_000)))
    }

    @Test
    fun `older equal chapter does not win`() {
        assertFalse(LocalTrackedSourceProgressPolicy.accepts(progress(12.0, 3_000), progress(12.0, 2_000)))
        assertTrue(LocalTrackedSourceProgressPolicy.accepts(progress(12.0, 3_000), progress(12.0, 3_000)))
    }

    @Test
    fun `unknown progress is timestamp monotonic and recognized progress wins`() {
        assertFalse(LocalTrackedSourceProgressPolicy.accepts(progress(null, 3_000), progress(null, 2_000)))
        assertTrue(LocalTrackedSourceProgressPolicy.accepts(progress(null, 3_000), progress(1.0, 2_000)))
    }
}
