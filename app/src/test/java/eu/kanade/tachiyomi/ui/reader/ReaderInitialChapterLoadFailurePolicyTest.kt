package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.NoSuchElementException

class ReaderInitialChapterLoadFailurePolicyTest {
    @Test
    fun `missing chapter is classified without reading its message`() {
        assertEquals(
            ReaderInitialChapterLoadFailurePolicy.Category.MISSING_CHAPTER,
            ReaderInitialChapterLoadFailurePolicy.category(NoSuchElementException("private chapter URL")),
        )
    }

    @Test
    fun `io failures are bounded to the io category`() {
        assertEquals(
            ReaderInitialChapterLoadFailurePolicy.Category.IO,
            ReaderInitialChapterLoadFailurePolicy.category(IOException("private source URL")),
        )
    }

    @Test
    fun `unknown failures do not expose a payload category`() {
        assertEquals(
            ReaderInitialChapterLoadFailurePolicy.Category.UNKNOWN,
            ReaderInitialChapterLoadFailurePolicy.category(IllegalStateException("private value")),
        )
    }
}
