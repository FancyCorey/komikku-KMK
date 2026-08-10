package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker

// KMK -->
class SourceEvaluationCrashRecoveryPolicyTest {

    private val now = 1_000_000L
    private val recentMs = 1 * 60 * 60 * 1000L // 1 hour ago
    private val staleMs = 25 * 60 * 60 * 1000L // 25 hours ago

    private fun makeMarker(updatedAt: Long) = SourceEvaluationProbeMarker(
        evaluationKey = "batch1",
        extensionPkgName = "eu.kanade.tachiyomi.extension.en.test",
        signatureHash = "abc123",
        extensionName = "Test Extension",
        sourceId = 12345L,
        sourceName = "Test Source",
        lang = "en",
        phase = "ProbingPopular",
        startedAt = updatedAt - 1000L,
        updatedAt = updatedAt,
        batchId = "batch1",
    )

    @Test
    fun `DoNothing when no marker`() {
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(null, now)
        assertEquals(SourceEvaluationCrashRecoveryPolicy.Decision.DoNothing, decision)
    }

    @Test
    fun `MarkUnsafe when marker is recent`() {
        val marker = makeMarker(updatedAt = now - recentMs)
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(marker, now)
        assertInstanceOf(SourceEvaluationCrashRecoveryPolicy.Decision.MarkUnsafe::class.java, decision)
        val markUnsafe = decision as SourceEvaluationCrashRecoveryPolicy.Decision.MarkUnsafe
        assertEquals(marker, markUnsafe.marker)
    }

    @Test
    fun `ClearStale when marker is older than 24 hours`() {
        val marker = makeMarker(updatedAt = now - staleMs)
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(marker, now)
        assertInstanceOf(SourceEvaluationCrashRecoveryPolicy.Decision.ClearStale::class.java, decision)
        val clearStale = decision as SourceEvaluationCrashRecoveryPolicy.Decision.ClearStale
        assertEquals(marker, clearStale.marker)
    }

    @Test
    fun `MarkUnsafe when marker is exactly at threshold minus 1ms`() {
        val thresholdMs = 24 * 60 * 60 * 1000L
        val marker = makeMarker(updatedAt = now - thresholdMs + 1L)
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(marker, now)
        assertInstanceOf(SourceEvaluationCrashRecoveryPolicy.Decision.MarkUnsafe::class.java, decision)
    }

    @Test
    fun `ClearStale when marker is exactly at threshold`() {
        val thresholdMs = 24 * 60 * 60 * 1000L
        val marker = makeMarker(updatedAt = now - thresholdMs)
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(marker, now)
        assertInstanceOf(SourceEvaluationCrashRecoveryPolicy.Decision.ClearStale::class.java, decision)
    }

    @Test
    fun `MarkUnsafe for marker written just now`() {
        val marker = makeMarker(updatedAt = now)
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(marker, now)
        assertInstanceOf(SourceEvaluationCrashRecoveryPolicy.Decision.MarkUnsafe::class.java, decision)
    }
}
// KMK <--
