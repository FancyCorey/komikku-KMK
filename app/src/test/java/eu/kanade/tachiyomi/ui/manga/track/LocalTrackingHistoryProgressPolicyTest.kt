package eu.kanade.tachiyomi.ui.manga.track

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.History
import java.util.Date

class LocalTrackingHistoryProgressPolicyTest {

    @Test
    fun `selects the latest read chapter and preserves the first read date`() {
        val result = LocalTrackingHistoryProgressPolicy.resolve(
            listOf(
                History(id = 1L, chapterId = 11L, readAt = Date(200L), readDuration = 1L),
                History(id = 2L, chapterId = 12L, readAt = Date(500L), readDuration = 1L),
                History(id = 3L, chapterId = 13L, readAt = null, readDuration = 1L),
            ),
        )

        assertEquals(12L, result?.chapterId)
        assertEquals(500L, result?.progressAt)
        assertEquals(200L, result?.firstReadAt)
    }

    @Test
    fun `returns no progress when history has no completed read timestamp`() {
        assertNull(
            LocalTrackingHistoryProgressPolicy.resolve(
                listOf(History(id = 1L, chapterId = 11L, readAt = null, readDuration = 0L)),
            ),
        )
    }
}
