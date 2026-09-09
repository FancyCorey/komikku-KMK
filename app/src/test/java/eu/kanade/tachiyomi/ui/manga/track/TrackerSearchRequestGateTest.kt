package eu.kanade.tachiyomi.ui.manga.track

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackerSearchRequestGateTest {

    @Test
    fun `first request becomes current`() {
        val gate = TrackerSearchRequestGate()
        val request = gate.begin("query")

        assertNotNull(request)
        assertTrue(gate.isCurrent(request!!))
    }

    @Test
    fun `identical active query is suppressed`() {
        val gate = TrackerSearchRequestGate()
        gate.begin("query")

        assertNull(gate.begin("query"))
    }

    @Test
    fun `different query supersedes the previous request`() {
        val gate = TrackerSearchRequestGate()
        val first = gate.begin("first")!!
        val second = gate.begin("second")!!

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        assertEquals("second", second.query)
    }

    @Test
    fun `finishing a stale request does not clear the current request`() {
        val gate = TrackerSearchRequestGate()
        val first = gate.begin("first")!!
        val second = gate.begin("second")!!

        gate.finish(first)

        assertTrue(gate.isCurrent(second))
        assertNull(gate.begin("second"))
    }

    @Test
    fun `finishing the current request permits retry`() {
        val gate = TrackerSearchRequestGate()
        val first = gate.begin("query")!!

        gate.finish(first)
        val retry = gate.begin("query")!!

        assertTrue(gate.isCurrent(retry))
        assertNotEquals(first.generation, retry.generation)
    }
}
