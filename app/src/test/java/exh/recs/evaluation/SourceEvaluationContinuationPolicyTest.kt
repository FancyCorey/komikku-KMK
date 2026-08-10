package exh.recs.evaluation

import eu.kanade.tachiyomi.extension.model.Extension
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->
class SourceEvaluationContinuationPolicyTest {

    private val now = System.currentTimeMillis()
    private val fp = "lang=en|incEx=false|skip=true|stale=false|upd=false|blkEx=false"

    private fun candidate(sig: String, pkg: String): EvaluationCandidate = EvaluationCandidate(
        extension = Extension.Available(
            name = pkg,
            pkgName = pkg,
            versionName = "1.0",
            versionCode = 1L,
            libVersion = 1.4,
            lang = "en",
            isNsfw = false,
            signatureHash = sig,
            storeName = "test-repo",
            sources = emptyList(),
            apkUrl = "https://test-repo.example.com/apk/$pkg.apk",
            iconUrl = "",
            store = ExtensionStore(
                indexUrl = "",
                name = "test-repo",
                badgeLabel = "test-repo",
                signingKey = sig,
                contact = ExtensionStore.Contact(website = "", discord = null),
                isLegacy = false,
                extensionListUrl = null,
            ),
        ),
        priorityRank = 0,
    )

    private fun key(sig: String, pkg: String) = "$sig|$pkg"

    @Test
    fun `buildFilterFingerprint includes all filter fields`() {
        val result = SourceEvaluationContinuationPolicy.buildFilterFingerprint(
            languages = setOf("en", "ja"),
            includeExplicit = true,
            skipAlreadyEvaluated = false,
            reEvaluateStale = true,
            onlyUpdatedEvaluated = false,
            blockExplicit = true,
        )
        assertTrue(result.contains("lang=en,ja"), "Expected languages sorted: $result")
        assertTrue(result.contains("|incEx=true"))
        assertTrue(result.contains("|skip=false"))
        assertTrue(result.contains("|stale=true"))
        assertTrue(result.contains("|upd=false"))
        assertTrue(result.contains("|blkEx=true"))
    }

    @Test
    fun `buildFilterFingerprint languages sorted`() {
        val a = SourceEvaluationContinuationPolicy.buildFilterFingerprint(
            languages = setOf("en", "ja"),
            includeExplicit = false,
            skipAlreadyEvaluated = false,
            reEvaluateStale = false,
            onlyUpdatedEvaluated = false,
            blockExplicit = false,
        )
        val b = SourceEvaluationContinuationPolicy.buildFilterFingerprint(
            languages = setOf("ja", "en"),
            includeExplicit = false,
            skipAlreadyEvaluated = false,
            reEvaluateStale = false,
            onlyUpdatedEvaluated = false,
            blockExplicit = false,
        )
        assertEquals(a, b, "Fingerprints should be order-independent")
    }

    @Test
    fun `sliceForRun with null cursor returns leading batch`() {
        val candidates = (1..20).map { candidate("sig$it", "pkg$it") }
        val slice = SourceEvaluationContinuationPolicy.sliceForRun(candidates, 5, null, fp, now)
        assertEquals(5, slice.size)
        assertEquals(candidates.take(5), slice)
    }

    @Test
    fun `sliceForRun with mismatched fingerprint returns leading batch`() {
        val candidates = (1..20).map { candidate("sig$it", "pkg$it") }
        val cursor = SourceEvaluationCursor(
            filterFingerprint = "different-fp",
            lastCandidateKey = key("sig5", "pkg5"),
            completedCandidateKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet(),
            updatedAt = now,
        )
        val slice = SourceEvaluationContinuationPolicy.sliceForRun(candidates, 5, cursor, fp, now)
        assertEquals(candidates.take(5), slice)
    }

    @Test
    fun `sliceForRun with expired cursor returns leading batch`() {
        val candidates = (1..20).map { candidate("sig$it", "pkg$it") }
        val eightDaysAgo = now - 8L * 24 * 60 * 60 * 1000
        val cursor = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = key("sig5", "pkg5"),
            completedCandidateKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet(),
            updatedAt = eightDaysAgo,
        )
        val slice = SourceEvaluationContinuationPolicy.sliceForRun(candidates, 5, cursor, fp, now)
        assertEquals(candidates.take(5), slice)
    }

    @Test
    fun `sliceForRun continues after last completed key`() {
        val candidates = (1..20).map { candidate("sig$it", "pkg$it") }
        val cursor = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = key("sig5", "pkg5"),
            completedCandidateKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet(),
            updatedAt = now,
        )
        val slice = SourceEvaluationContinuationPolicy.sliceForRun(candidates, 5, cursor, fp, now)
        assertEquals(5, slice.size)
        assertEquals(candidates[5], slice[0], "Should start at index 6 (sig6/pkg6)")
        assertEquals(candidates[9], slice[4])
    }

    @Test
    fun `sliceForRun skips already-completed interleaved candidates`() {
        val candidates = (1..10).map { candidate("sig$it", "pkg$it") }
        // Completed 1-3; last key is sig3
        // But also 5 was somehow completed in a prior interleaved run
        val cursor = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = key("sig3", "pkg3"),
            completedCandidateKeys = setOf(key("sig1", "pkg1"), key("sig2", "pkg2"), key("sig3", "pkg3"), key("sig5", "pkg5")),
            updatedAt = now,
        )
        val slice = SourceEvaluationContinuationPolicy.sliceForRun(candidates, 5, cursor, fp, now)
        // Starting after sig3 (index 3), skipping sig5 → sig4, sig6, sig7, sig8, sig9
        assertFalse(slice.any { it.extension.pkgName == "pkg5" }, "Already-completed key should be skipped")
        assertTrue(slice.any { it.extension.pkgName == "pkg4" })
    }

    @Test
    fun `remainingCount without cursor returns full count`() {
        val candidates = (1..10).map { candidate("sig$it", "pkg$it") }
        val count = SourceEvaluationContinuationPolicy.remainingCount(candidates, null, fp, now)
        assertEquals(10, count)
    }

    @Test
    fun `remainingCount after first 5 returns 15`() {
        val candidates = (1..20).map { candidate("sig$it", "pkg$it") }
        val cursor = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = key("sig5", "pkg5"),
            completedCandidateKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet(),
            updatedAt = now,
        )
        val count = SourceEvaluationContinuationPolicy.remainingCount(candidates, cursor, fp, now)
        assertEquals(15, count)
    }

    @Test
    fun `canContinue is false when cursor is null`() {
        val candidates = (1..10).map { candidate("sig$it", "pkg$it") }
        assertFalse(SourceEvaluationContinuationPolicy.canContinue(candidates, null, fp, now))
    }

    @Test
    fun `canContinue is false when fingerprint mismatches`() {
        val candidates = (1..10).map { candidate("sig$it", "pkg$it") }
        val cursor = SourceEvaluationCursor(
            filterFingerprint = "other",
            lastCandidateKey = key("sig5", "pkg5"),
            completedCandidateKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet(),
            updatedAt = now,
        )
        assertFalse(SourceEvaluationContinuationPolicy.canContinue(candidates, cursor, fp, now))
    }

    @Test
    fun `canContinue is true when there are remaining candidates`() {
        val candidates = (1..10).map { candidate("sig$it", "pkg$it") }
        val cursor = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = key("sig5", "pkg5"),
            completedCandidateKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet(),
            updatedAt = now,
        )
        assertTrue(SourceEvaluationContinuationPolicy.canContinue(candidates, cursor, fp, now))
    }

    @Test
    fun `canContinue is false when all candidates completed`() {
        val candidates = (1..5).map { candidate("sig$it", "pkg$it") }
        val cursor = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = key("sig5", "pkg5"),
            completedCandidateKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet(),
            updatedAt = now,
        )
        assertFalse(SourceEvaluationContinuationPolicy.canContinue(candidates, cursor, fp, now))
    }

    @Test
    fun `advanceCursor builds correct cursor`() {
        val candidates = (1..10).map { candidate("sig$it", "pkg$it") }
        val completedKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet()
        val cursor = SourceEvaluationContinuationPolicy.advanceCursor(
            current = null,
            completedKeys = completedKeys,
            allCandidates = candidates,
            currentFingerprint = fp,
            now = now,
        )
        assertEquals(fp, cursor.filterFingerprint)
        assertEquals(key("sig5", "pkg5"), cursor.lastCandidateKey)
        assertEquals(completedKeys, cursor.completedCandidateKeys)
        assertEquals(now, cursor.updatedAt)
    }

    @Test
    fun `advanceCursor merges prior completed keys`() {
        val candidates = (1..10).map { candidate("sig$it", "pkg$it") }
        val prior = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = key("sig5", "pkg5"),
            completedCandidateKeys = (1..5).map { key("sig$it", "pkg$it") }.toSet(),
            updatedAt = now,
        )
        val newCompleted = (6..8).map { key("sig$it", "pkg$it") }.toSet()
        val cursor = SourceEvaluationContinuationPolicy.advanceCursor(
            current = prior,
            completedKeys = newCompleted,
            allCandidates = candidates,
            currentFingerprint = fp,
            now = now,
        )
        val expected = (1..8).map { key("sig$it", "pkg$it") }.toSet()
        assertEquals(expected, cursor.completedCandidateKeys)
    }

    @Test
    fun `serialize and deserialize roundtrip`() {
        val cursor = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = "aabbcc|eu.kanade.pkg",
            completedCandidateKeys = setOf("aabbcc|eu.kanade.pkg", "ddeeff|com.other.pkg"),
            updatedAt = 1234567890L,
        )
        val serialized = SourceEvaluationContinuationPolicy.serialize(cursor)
        val deserialized = SourceEvaluationContinuationPolicy.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals(cursor.filterFingerprint, deserialized!!.filterFingerprint)
        assertEquals(cursor.lastCandidateKey, deserialized.lastCandidateKey)
        assertEquals(cursor.completedCandidateKeys, deserialized.completedCandidateKeys)
        assertEquals(cursor.updatedAt, deserialized.updatedAt)
    }

    @Test
    fun `deserialize null lastCandidateKey roundtrip`() {
        val cursor = SourceEvaluationCursor(
            filterFingerprint = fp,
            lastCandidateKey = null,
            completedCandidateKeys = emptySet(),
            updatedAt = 9999L,
        )
        val serialized = SourceEvaluationContinuationPolicy.serialize(cursor)
        val deserialized = SourceEvaluationContinuationPolicy.deserialize(serialized)
        assertNotNull(deserialized)
        assertNull(deserialized!!.lastCandidateKey)
        assertTrue(deserialized.completedCandidateKeys.isEmpty())
    }

    @Test
    fun `deserialize blank string returns null`() {
        assertNull(SourceEvaluationContinuationPolicy.deserialize(""))
        assertNull(SourceEvaluationContinuationPolicy.deserialize("   "))
    }

    @Test
    fun `deserialize malformed string returns null`() {
        assertNull(SourceEvaluationContinuationPolicy.deserialize("only-one-part"))
        assertNull(SourceEvaluationContinuationPolicy.deserialize("a\nb\nc\nnot-a-long"))
    }

    @Test
    fun `candidateKey format is signatureHash pipe pkgName`() {
        val c = candidate("deadbeef", "eu.kanade.tachiyomi.extension.manga.source")
        val k = SourceEvaluationContinuationPolicy.candidateKey(c)
        assertEquals("deadbeef|eu.kanade.tachiyomi.extension.manga.source", k)
    }

    // KMK --> v0.8.1-fix3: stale-reassessment queue uses a "|queue=stale" fingerprint suffix
    // (built in SourceEvaluationScreenModel) so it never shares a cursor slot with the unassessed
    // queue's fingerprint, even when the underlying option booleans happen to coincide.

    @Test
    fun `stale queue fingerprint suffix separates it from unassessed queue fingerprint`() {
        val unassessedFp = SourceEvaluationContinuationPolicy.buildFilterFingerprint(
            languages = setOf("en"),
            includeExplicit = false,
            skipAlreadyEvaluated = false,
            reEvaluateStale = true,
            onlyUpdatedEvaluated = false,
            blockExplicit = false,
        )
        val staleFp = unassessedFp + "|queue=stale"
        assertTrue(unassessedFp != staleFp)
    }

    @Test
    fun `cursor advanced under the unassessed fingerprint does not canContinue under the stale fingerprint`() {
        val candidates = listOf(candidate("s1", "eu.kanade.tachiyomi.extension.en.a"))
        val unassessedFp = fp
        val staleFp = fp + "|queue=stale"

        val cursor = SourceEvaluationContinuationPolicy.advanceCursor(
            current = null,
            completedKeys = setOf(key("s1", "eu.kanade.tachiyomi.extension.en.a")),
            allCandidates = candidates,
            currentFingerprint = unassessedFp,
            now = now,
        )

        assertFalse(SourceEvaluationContinuationPolicy.canContinue(candidates, cursor, staleFp, now))
    }

    @Test
    fun `batch size change does not invalidate the stale queue cursor, mirroring the unassessed queue`() {
        val staleFp = fp + "|queue=stale"
        val candidates = (1..5).map { candidate("s$it", "eu.kanade.tachiyomi.extension.en.c$it") }

        val firstSlice = SourceEvaluationContinuationPolicy.sliceForRun(candidates, batchSize = 2, cursor = null, currentFingerprint = staleFp, now = now)
        assertEquals(2, firstSlice.size)

        val cursor = SourceEvaluationContinuationPolicy.advanceCursor(
            current = null,
            completedKeys = firstSlice.map { SourceEvaluationContinuationPolicy.candidateKey(it) }.toSet(),
            allCandidates = candidates,
            currentFingerprint = staleFp,
            now = now,
        )

        // Continuing with a larger batch size must pick up where it left off, not restart.
        val nextSlice = SourceEvaluationContinuationPolicy.sliceForRun(candidates, batchSize = 4, cursor = cursor, currentFingerprint = staleFp, now = now)
        assertEquals(3, nextSlice.size)
        assertTrue(nextSlice.none { SourceEvaluationContinuationPolicy.candidateKey(it) in cursor.completedCandidateKeys })
    }

    // KMK v0.8.1-fix4: stale queue completion state -- once every stale candidate is completed,
    // remainingCount reaches zero and canContinue turns false, which is what the UI uses to show
    // "Outdated reassessment complete" instead of the continue button.
    @Test
    fun `stale queue reports zero remaining and canContinue false once every candidate is completed`() {
        val staleFp = fp + "|queue=stale"
        val candidates = listOf(candidate("s1", "eu.kanade.tachiyomi.extension.en.a"), candidate("s2", "eu.kanade.tachiyomi.extension.en.b"))

        val cursor = SourceEvaluationContinuationPolicy.advanceCursor(
            current = null,
            completedKeys = candidates.map { SourceEvaluationContinuationPolicy.candidateKey(it) }.toSet(),
            allCandidates = candidates,
            currentFingerprint = staleFp,
            now = now,
        )

        assertEquals(0, SourceEvaluationContinuationPolicy.remainingCount(candidates, cursor, staleFp, now))
        assertFalse(SourceEvaluationContinuationPolicy.canContinue(candidates, cursor, staleFp, now))
    }

    // KMK v0.8.1-fix4: failed/attempted candidates still advance the stale cursor -- advanceCursor()
    // only needs the completed-key set as input; it has no opinion on how the runner decides what
    // belongs in that set.
    // KMK v0.8.15-fix1: the runner's contract for what belongs in that set changed -- it is now
    // populated *only* when a candidate durably writes a source_evaluation row (see
    // SourceEvaluationRunner's `_completedCandidateKeys`), not unconditionally on handoff. A
    // candidate that *errors* still normally lands here, because recordExtensionError() itself
    // performs a durable write (an error-kind row) unless the delete-before-upsert reconciliation
    // step also fails -- so this test's premise (an errored candidate's key still reaches this
    // policy) remains realistic; only the reason it reaches this policy has changed.
    @Test
    fun `stale cursor advances past a failed candidate the same as a successful one`() {
        val staleFp = fp + "|queue=stale"
        val candidates = listOf(candidate("s1", "eu.kanade.tachiyomi.extension.en.failed"), candidate("s2", "eu.kanade.tachiyomi.extension.en.ok"))

        // Simulates the runner handing off both candidates -- one errors (but still durably writes
        // an error row via recordExtensionError()), one succeeds -- both keys land in
        // completedCandidateKeys because both produced a durable write.
        val cursor = SourceEvaluationContinuationPolicy.advanceCursor(
            current = null,
            completedKeys = setOf(key("s1", "eu.kanade.tachiyomi.extension.en.failed"), key("s2", "eu.kanade.tachiyomi.extension.en.ok")),
            allCandidates = candidates,
            currentFingerprint = staleFp,
            now = now,
        )

        assertEquals(0, SourceEvaluationContinuationPolicy.remainingCount(candidates, cursor, staleFp, now))
    }
    // KMK <--
}
// KMK <--
