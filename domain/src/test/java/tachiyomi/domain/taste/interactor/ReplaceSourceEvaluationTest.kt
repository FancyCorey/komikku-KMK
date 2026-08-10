package tachiyomi.domain.taste.interactor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict
import tachiyomi.domain.taste.repository.SourceEvaluationRepository

// KMK v0.8.19 -->
// Current Komikku-standard alignment pass, 2026-07-23: persistence-contract coverage for
// SourceEvaluationRepository.replaceByPackage(), superseding the sequence-level-only
// SourceEvaluationExtensionErrorReconciliationSequenceTest.kt (deleted). That test exercised a
// suspend-lambda helper and proved ordering/failure-propagation logic only; it did not prove
// anything about the actual repository/interactor contract or row-level state.
//
// This suite uses an in-memory fake SourceEvaluationRepository (below), not a real SQLDelight
// database. A real SQLDelight-backed test harness (in-memory driver + generated queries) does not
// exist anywhere in this test suite. This is a known test-infrastructure limitation (see
// SourceEvaluationRunCompletionPolicyTest and
// SourceEvaluationExtensionErrorReconciliationPolicyTest doc comments for the same precedent).
// Building a real SQLDelight in-memory harness was not attempted in the implementation -- it would require
// wiring an in-memory SqlDriver, the generated Database class, and DatabaseHandler test double, which
// is a materially larger undertaking than the implementation's scope. The fake below models the exact
// atomicity contract required (delete-then-upsert commits together or not at all) at the interface
// level, which is the boundary ReplaceSourceEvaluation/SourceEvaluationRunner actually depend on --
// it proves the *contract*, not the SQL transaction mechanics themselves. The real transaction
// mechanics (handler.await(inTransaction = true) rolling back on any exception) are standard SQLDelight/
// SQLite behavior already relied on elsewhere in this codebase (e.g. every other multi-statement
// handler.await(inTransaction = true) block in the data module) and were not independently
// re-verified against a live database in the implementation.
class ReplaceSourceEvaluationTest {

    private fun evaluation(
        evaluationKey: String,
        pkgName: String,
        signatureHash: String,
        errorMessage: String? = "boom",
    ) = SourceEvaluation(
        evaluationKey = evaluationKey,
        sourceId = null,
        extensionPkgName = pkgName,
        signatureHash = signatureHash,
        extensionName = "Test Extension",
        sourceName = "Test Extension",
        lang = "en",
        baseUrl = null,
        repoName = "Test Repo",
        sourceCount = 1,
        isNsfw = false,
        evaluationVersion = 3,
        evaluatedAt = 1000L,
        expiresAt = null,
        sampleCount = 0,
        popularCount = 0,
        latestCount = 0,
        searchCount = 0,
        searchSuccessCount = 0,
        likedTitleMatchCount = 0,
        preferredTagMatchCount = 0,
        blockedTagMatchCount = 0,
        explicitSignalCount = 0,
        ecchiSignalCount = 0,
        errorCount = 1,
        qualityScore = 0.0,
        recommendationFitScore = 0.0,
        searchReliabilityScore = 0.0,
        explicitScore = 0.0,
        ecchiScore = 0.0,
        verdict = SourceEvaluationVerdict.ERROR,
        sampledTitlesJson = null,
        sampledTagsJson = null,
        errorMessage = errorMessage,
        extensionVersionName = "1.2.3",
        extensionVersionCode = 7L,
        extensionApkName = "test.apk",
        catalogueMetadataConfidence = SourceEvaluationMetadataConfidence.UNKNOWN,
        detailEnrichmentAttemptCount = 4,
        detailEnrichmentSuccessCount = 2,
        metadataCandidateCount = 3,
        positiveCandidateCount = 1,
        negativeCandidateCount = 1,
        explicitPreferredGroupHitCount = 0,
        learnedPositiveGroupHitCount = 0,
        blockedCandidateCount = 0,
        adultSignalCandidateCount = 0,
    )

    /**
     * In-memory fake modeling the exact atomicity contract required of
     * SourceEvaluationRepositoryImpl.replaceByPackage(): delete-then-upsert must commit together or
     * not at all. [failDeleteWith] and [failUpsertAfterDeleteWith] let a test simulate a failure at
     * either step; when either is set, no row-level change is applied (both the deleted rows and the
     * replacement row are absent afterward), matching real transaction rollback.
     */
    private class FakeSourceEvaluationRepository : SourceEvaluationRepository {
        val rows = mutableMapOf<String, SourceEvaluation>()
        var failDeleteWith: Throwable? = null
        var failUpsertAfterDeleteWith: Throwable? = null

        override suspend fun getAll(): List<SourceEvaluation> = rows.values.toList()
        override fun getAllAsFlow(): Flow<List<SourceEvaluation>> = MutableStateFlow(rows.values.toList())
        override suspend fun getByKey(key: String): SourceEvaluation? = rows[key]
        override suspend fun getBySourceId(sourceId: Long): SourceEvaluation? =
            rows.values.find { it.sourceId == sourceId }
        override suspend fun getByPackage(pkgName: String, signatureHash: String): List<SourceEvaluation> =
            rows.values.filter { it.extensionPkgName == pkgName && it.signatureHash == signatureHash }

        override suspend fun upsert(evaluation: SourceEvaluation) {
            rows[evaluation.evaluationKey] = evaluation
        }

        override suspend fun deleteByKey(key: String) {
            rows.remove(key)
        }

        override suspend fun deleteByPackage(pkgName: String, signatureHash: String) {
            rows.keys.filter { rows[it]?.extensionPkgName == pkgName && rows[it]?.signatureHash == signatureHash }
                .forEach { rows.remove(it) }
        }

        override suspend fun replaceByPackage(
            pkgName: String,
            signatureHash: String,
            evaluation: SourceEvaluation,
        ) {
            // Snapshot for rollback -- models a real SQL transaction: nothing is committed unless
            // every statement in the block succeeds.
            val snapshot = rows.toMap()
            try {
                failDeleteWith?.let { throw it }
                deleteByPackage(pkgName, signatureHash)
                failUpsertAfterDeleteWith?.let {
                    rows.clear()
                    rows.putAll(snapshot)
                    throw it
                }
                upsert(evaluation)
            } catch (e: Throwable) {
                rows.clear()
                rows.putAll(snapshot)
                throw e
            }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }

    @Test
    fun `existing package rows are deleted and exactly one extension-level error row is present after success`() = runTest {
        val repo = FakeSourceEvaluationRepository()
        repo.rows["sig|pkg|1"] = evaluation("sig|pkg|1", "pkg", "sig")
        repo.rows["sig|pkg|2"] = evaluation("sig|pkg|2", "pkg", "sig")
        val replace = ReplaceSourceEvaluation(repo)

        replace.await("pkg", "sig", evaluation("sig|pkg", "pkg", "sig", errorMessage = "extension error"))

        val remaining = repo.getByPackage("pkg", "sig")
        assertEquals(1, remaining.size)
        assertEquals("extension error", remaining.single().errorMessage)
    }

    @Test
    fun `a delete failure prevents upsert and leaves state unchanged`() = runTest {
        val repo = FakeSourceEvaluationRepository()
        repo.rows["sig|pkg|1"] = evaluation("sig|pkg|1", "pkg", "sig")
        repo.failDeleteWith = RuntimeException("delete failed")
        val replace = ReplaceSourceEvaluation(repo)

        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking {
                replace.await("pkg", "sig", evaluation("sig|pkg", "pkg", "sig"))
            }
        }
        // Original row untouched; no replacement row written.
        assertEquals(listOf("sig|pkg|1"), repo.rows.keys.toList())
    }

    @Test
    fun `an upsert failure rolls back the delete`() = runTest {
        val repo = FakeSourceEvaluationRepository()
        repo.rows["sig|pkg|1"] = evaluation("sig|pkg|1", "pkg", "sig")
        repo.failUpsertAfterDeleteWith = RuntimeException("upsert failed")
        val replace = ReplaceSourceEvaluation(repo)

        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking {
                replace.await("pkg", "sig", evaluation("sig|pkg", "pkg", "sig"))
            }
        }
        // Rolled back: the original row is still present, no replacement row exists.
        assertEquals(listOf("sig|pkg|1"), repo.rows.keys.toList())
    }

    @Test
    fun `cancellation propagates unchanged`() = runTest {
        val repo = FakeSourceEvaluationRepository()
        repo.failDeleteWith = CancellationException("cancelled")
        val replace = ReplaceSourceEvaluation(repo)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                replace.await("pkg", "sig", evaluation("sig|pkg", "pkg", "sig"))
            }
        }
    }

    @Test
    fun `error metadata, counters, timestamps, confidence, and signature hash survive the replacement`() = runTest {
        val repo = FakeSourceEvaluationRepository()
        val replace = ReplaceSourceEvaluation(repo)
        val record = evaluation("sig|pkg", "pkg", "sig", errorMessage = "install timed out").copy(
            evaluatedAt = 99999L,
            errorCount = 5,
            catalogueMetadataConfidence = SourceEvaluationMetadataConfidence.LOW,
        )

        replace.await("pkg", "sig", record)

        val stored = repo.getByKey("sig|pkg")
        assertEquals("install timed out", stored?.errorMessage)
        assertEquals(99999L, stored?.evaluatedAt)
        assertEquals(5, stored?.errorCount)
        assertEquals(SourceEvaluationMetadataConfidence.LOW, stored?.catalogueMetadataConfidence)
        assertEquals("sig", stored?.signatureHash)
    }

    @Test
    fun `re-running the same package does not accumulate duplicate stale rows`() = runTest {
        val repo = FakeSourceEvaluationRepository()
        val replace = ReplaceSourceEvaluation(repo)

        replace.await("pkg", "sig", evaluation("sig|pkg", "pkg", "sig", errorMessage = "first failure"))
        replace.await("pkg", "sig", evaluation("sig|pkg", "pkg", "sig", errorMessage = "second failure"))

        val remaining = repo.getByPackage("pkg", "sig")
        assertEquals(1, remaining.size)
        assertEquals("second failure", remaining.single().errorMessage)
    }

    @Test
    fun `a failed replacement leaves no durable row for the runner to treat as reconciled`() = runTest {
        val repo = FakeSourceEvaluationRepository()
        repo.rows["sig|pkg|1"] = evaluation("sig|pkg|1", "pkg", "sig")
        repo.failUpsertAfterDeleteWith = RuntimeException("upsert failed")
        val replace = ReplaceSourceEvaluation(repo)

        // Mirrors SourceEvaluationRunner.recordExtensionError()'s own try/catch: a failure here must
        // never advance the durable-handled cursor -- asserted structurally by confirming the
        // replacement's own row never lands durably (single stale row from before the call remains,
        // unchanged and unreplaced).
        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking {
                replace.await("pkg", "sig", evaluation("sig|pkg", "pkg", "sig"))
            }
        }
        assertTrue(repo.getByKey("sig|pkg") == null)
    }
}
// KMK <--
