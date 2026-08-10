package exh.recs.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluationProbeMarker

// KMK -->
/**
 * Tests for the policy-level decisions made during startup recovery.
 * The class itself depends on Injekt/Android, so these tests exercise the pure policy
 * via [SourceEvaluationCrashRecoveryPolicy] and the seed data structure.
 */
class SourceEvaluationStartupRecoveryTest {

    private val recentMs = 60 * 60 * 1000L // 1 hour
    private val staleMs = 25 * 60 * 60 * 1000L // 25 hours

    private fun marker(updatedAt: Long) = SourceEvaluationProbeMarker(
        evaluationKey = "test",
        extensionPkgName = "com.example.test",
        signatureHash = "abc123",
        extensionName = "Test Extension",
        sourceId = null,
        sourceName = null,
        lang = "en",
        phase = "ProbingPopular",
        startedAt = updatedAt,
        updatedAt = updatedAt,
        batchId = null,
    )

    @Test
    fun `recent marker triggers MarkUnsafe decision`() {
        val now = System.currentTimeMillis()
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(marker(now - recentMs), now)
        assertTrue(decision is SourceEvaluationCrashRecoveryPolicy.Decision.MarkUnsafe)
    }

    @Test
    fun `stale marker triggers ClearStale decision`() {
        val now = System.currentTimeMillis()
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(marker(now - staleMs), now)
        assertTrue(decision is SourceEvaluationCrashRecoveryPolicy.Decision.ClearStale)
    }

    @Test
    fun `null marker triggers DoNothing decision`() {
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(null, System.currentTimeMillis())
        assertEquals(SourceEvaluationCrashRecoveryPolicy.Decision.DoNothing, decision)
    }

    @Test
    fun `MarkUnsafe decision carries the original marker`() {
        val now = System.currentTimeMillis()
        val m = marker(now - recentMs)
        val decision = SourceEvaluationCrashRecoveryPolicy.decide(m, now)
        val markUnsafe = decision as SourceEvaluationCrashRecoveryPolicy.Decision.MarkUnsafe
        assertEquals("Test Extension", markUnsafe.marker.extensionName)
        assertEquals("ProbingPopular", markUnsafe.marker.phase)
    }

    // Seed-level: verify the DCM seed produces a well-formed synthetic marker
    @Test
    fun `DCM seed pkgName matches extension class package`() {
        val seed = SourceEvaluationKnownUnsafeSeeds.DIGITAL_COMIC_MUSEUM
        assertTrue(seed.pkgName.startsWith("eu.kanade.tachiyomi.extension.en."))
        assertTrue(seed.pkgName.endsWith("digitalcomicmuseum"))
    }

    @Test
    fun `synthetic probe marker from DCM seed has extension-level key format`() {
        val seed = SourceEvaluationKnownUnsafeSeeds.DIGITAL_COMIC_MUSEUM
        val fakeHash = "deadbeef"
        val syntheticMarker = SourceEvaluationProbeMarker(
            evaluationKey = null,
            extensionPkgName = seed.pkgName,
            signatureHash = fakeHash,
            extensionName = seed.extensionName,
            sourceId = null,
            sourceName = null,
            lang = null,
            phase = "KnownUnsafeSeed",
            startedAt = 0L,
            updatedAt = 0L,
            batchId = null,
        )
        assertEquals("$fakeHash|${seed.pkgName}", syntheticMarker.extensionKey)
        assertNull(syntheticMarker.sourceId)
    }

    @Test
    fun `startup recovery result defaults to no recovery`() {
        val result = SourceEvaluationStartupRecovery.Result()
        assertFalse(result.markerRecovered)
        assertFalse(result.markerClearedStale)
        assertEquals(0, result.seededUnsafeCount)
        assertNull(result.errorMessage)
        assertNull(result.recoveredExtensionName)
        assertNull(result.recoveredPhase)
    }
}
// KMK <--
