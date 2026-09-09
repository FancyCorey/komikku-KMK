package eu.kanade.tachiyomi.ui.manga.track

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class TrackerSearchExecutionTest {

    @Test
    fun `successful search returns its value`() = runTest {
        assertEquals(Result.success("result"), runTrackerSearch { "result" })
    }

    @Test
    fun `ordinary search failure remains available to bounded presentation`() = runTest {
        val failure = IOException("private server response")

        assertSame(failure, runTrackerSearch<String> { throw failure }.exceptionOrNull())
    }

    @Test
    fun `cancellation is rethrown`() {
        assertThrows(CancellationException::class.java) {
            runTest { runTrackerSearch<String> { throw CancellationException("cancelled") } }
        }
    }

    @Test
    fun `fatal errors are rethrown`() {
        val failure = OutOfMemoryError("fatal")

        val thrown = assertThrows(OutOfMemoryError::class.java) {
            runTest { runTrackerSearch<String> { throw failure } }
        }
        assertSame(failure, thrown)
    }
}
