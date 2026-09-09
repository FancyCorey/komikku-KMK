package exh.uconfig

import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class EHConfigurationCoordinatorTest {

    @Test
    fun `request without warning runs once and reaches success`() = runTest {
        var calls = 0
        val coordinator = coordinator { calls += 1 }

        assertTrue(coordinator.request(needsConfirmation = false))
        assertFalse(coordinator.request(needsConfirmation = false))
        assertEquals(EHConfigurationState.Running, coordinator.state.value)
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(EHConfigurationState.Succeeded, coordinator.state.value)
        assertTrue(coordinator.consumeSuccess())
        assertEquals(EHConfigurationState.Idle, coordinator.state.value)
    }

    @Test
    fun `warning requires confirmation and can be dismissed`() = runTest {
        var calls = 0
        val coordinator = coordinator { calls += 1 }

        assertTrue(coordinator.request(needsConfirmation = true))
        assertEquals(EHConfigurationState.AwaitingConfirmation, coordinator.state.value)
        assertEquals(0, calls)
        assertTrue(coordinator.dismissConfirmation())
        assertEquals(EHConfigurationState.Idle, coordinator.state.value)

        assertTrue(coordinator.request(needsConfirmation = true))
        assertTrue(coordinator.confirm())
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(EHConfigurationState.Succeeded, coordinator.state.value)
    }

    @Test
    fun `ordinary failure stores no exception text and retry can succeed`() = runTest {
        var calls = 0
        val failures = mutableListOf<Exception>()
        val coordinator = coordinator(onFailure = failures::add) {
            calls += 1
            if (calls == 1) throw IOException("https://user:secret@example.invalid/private?token=secret")
        }

        coordinator.request(needsConfirmation = false)
        advanceUntilIdle()

        assertEquals(EHConfigurationState.Failed, coordinator.state.value)
        assertEquals(1, failures.size)
        assertFalse(coordinator.state.value.toString().contains("secret"))
        assertTrue(coordinator.retry())
        assertFalse(coordinator.retry())
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(EHConfigurationState.Succeeded, coordinator.state.value)
    }

    @Test
    fun `dismiss only applies to the matching terminal state`() = runTest {
        val coordinator = coordinator { throw IOException("failed") }

        assertFalse(coordinator.dismissFailure())
        coordinator.request(needsConfirmation = false)
        advanceUntilIdle()
        assertTrue(coordinator.dismissFailure())
        assertEquals(EHConfigurationState.Idle, coordinator.state.value)
        assertFalse(coordinator.consumeSuccess())
    }

    @Test
    fun `runner rethrows cancellation and fatal errors`() {
        assertThrows(CancellationException::class.java) {
            runTest {
                runEHConfiguration { throw CancellationException("cancel") }
            }
        }
        assertThrows(OutOfMemoryError::class.java) {
            runTest {
                runEHConfiguration { throw OutOfMemoryError("fatal") }
            }
        }
    }

    private fun TestScope.coordinator(
        onFailure: (Exception) -> Unit = {},
        configure: suspend () -> Unit,
    ): EHConfigurationCoordinator = EHConfigurationCoordinator(
        context = mockk(relaxed = true),
        scope = TestScope(StandardTestDispatcher(testScheduler)),
        configure = configure,
        onFailure = onFailure,
    )
}
