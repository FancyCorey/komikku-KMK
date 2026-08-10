package exh.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ActionHistoryFollowUpSafetyTest {

    @Test
    fun `ordinary exception is classified as failed`() {
        val result = runBlocking {
            runActionHistoryFollowUpSafely {
                error("external operation failed")
            }
        }

        assertEquals(ActionHistoryFollowUpResult.Failed, result)
    }

    @Test
    fun `returned result is preserved`() {
        val result = runBlocking {
            runActionHistoryFollowUpSafely { ActionHistoryFollowUpResult.Started }
        }

        assertEquals(ActionHistoryFollowUpResult.Started, result)
    }

    @Test
    fun `cancellation is propagated`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runActionHistoryFollowUpSafely {
                    throw CancellationException("cancelled")
                }
            }
        }
    }
}
