package eu.kanade.tachiyomi.crash

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashPayloadPolicyTest {
    @Test
    fun `transport details are bounded`() {
        val throwable = IllegalStateException("private=" + "x".repeat(CrashPayloadPolicy.MAX_DETAILS_LENGTH * 2))

        assertEquals(CrashPayloadPolicy.MAX_DETAILS_LENGTH, CrashPayloadPolicy.detailsForTransport(throwable).length)
    }

    @Test
    fun `missing blank and oversized encoded payloads are rejected`() {
        assertNull(CrashPayloadPolicy.acceptEncodedPayload(null))
        assertNull(CrashPayloadPolicy.acceptEncodedPayload("  "))
        assertNull(CrashPayloadPolicy.acceptEncodedPayload("x".repeat(CrashPayloadPolicy.MAX_ENCODED_LENGTH + 1)))
    }

    @Test
    fun `valid bounded payload remains available for explicit diagnostics`() {
        val payload = "diagnostic details"

        assertEquals(payload, CrashPayloadPolicy.acceptEncodedPayload(payload))
        assertEquals(payload, CrashPayloadPolicy.acceptDecodedDetails(payload))
        assertTrue(CrashPayloadPolicy.MAX_ENCODED_LENGTH > CrashPayloadPolicy.MAX_DETAILS_LENGTH)
    }

    @Test
    fun `oversized decoded details are rejected instead of silently accepted`() {
        assertNull(CrashPayloadPolicy.acceptDecodedDetails("x".repeat(CrashPayloadPolicy.MAX_DETAILS_LENGTH + 1)))
    }

    @Test
    fun `serializer round trip preserves only the bounded diagnostic payload`() {
        val encoded = Json.encodeToString(
            GlobalExceptionHandler.ThrowableSerializer,
            IllegalStateException("x".repeat(CrashPayloadPolicy.MAX_DETAILS_LENGTH * 2)),
        )
        val decoded = Json.decodeFromString(GlobalExceptionHandler.ThrowableSerializer, encoded)

        assertTrue(encoded.length <= CrashPayloadPolicy.MAX_ENCODED_LENGTH)
        assertEquals(CrashPayloadPolicy.MAX_DETAILS_LENGTH, decoded.message?.length)
    }
}
