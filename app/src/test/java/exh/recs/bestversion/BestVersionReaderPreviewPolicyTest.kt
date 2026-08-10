package exh.recs.bestversion

// KMK v0.8.18 -->
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BestVersionReaderPreviewPolicyTest {

    @Test
    fun `webtoon default reading mode applies side padding`() {
        val display = BestVersionReaderPreviewPolicy.resolve(defaultReadingModeValue = 4, webtoonSidePaddingPercent = 10)
        assertTrue(display.webtoonStyle)
        assertEquals(10, display.sidePaddingDp)
    }

    @Test
    fun `continuous vertical default reading mode is treated as webtoon-style`() {
        val display = BestVersionReaderPreviewPolicy.resolve(defaultReadingModeValue = 5, webtoonSidePaddingPercent = 15)
        assertTrue(display.webtoonStyle)
        assertEquals(15, display.sidePaddingDp)
    }

    @Test
    fun `paged default reading mode never applies side padding even if the preference is nonzero`() {
        val display = BestVersionReaderPreviewPolicy.resolve(defaultReadingModeValue = 1, webtoonSidePaddingPercent = 20)
        assertFalse(display.webtoonStyle)
        assertEquals(0, display.sidePaddingDp)
    }

    @Test
    fun `side padding is clamped to the 0-25 range`() {
        val display = BestVersionReaderPreviewPolicy.resolve(defaultReadingModeValue = 4, webtoonSidePaddingPercent = 999)
        assertEquals(25, display.sidePaddingDp)
    }
}
// KMK <--
