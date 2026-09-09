package exh.util

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DispatcherHandleTest {

    @Test
    fun `close action runs at most once`() {
        var closeCount = 0
        val handle = DispatcherHandle(UnconfinedTestDispatcher()) { closeCount++ }

        handle.close()
        handle.close()

        assertEquals(1, closeCount)
    }

    @Test
    fun `owned handle rejects a non-positive thread count`() {
        assertThrows(IllegalArgumentException::class.java) {
            ownedFixedThreadPoolDispatcherHandle(0)
        }
    }
}
