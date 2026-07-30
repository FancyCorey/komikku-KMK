package tachiyomi.data.taste

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.taste.model.SourceEvaluation
import tachiyomi.domain.taste.model.SourceEvaluationMetadataConfidence
import tachiyomi.domain.taste.model.SourceEvaluationVerdict

// KMK v0.8.19: real SQLDelight coverage for the atomic extension-error replacement path.
// The domain tests validate the interactor contract with a fake repository; these tests exercise
// SourceEvaluationRepositoryImpl, generated queries, and AndroidDatabaseHandler's transaction
// rollback behavior against an in-memory SQLite database.
class SourceEvaluationRepositoryTransactionTest {

    private fun openHandler(): DatabaseHandler {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        return AndroidDatabaseHandler(database, driver)
    }

    private fun evaluation(
        key: String,
        packageName: String = "test.extension",
        signatureHash: String = "signature",
        errorMessage: String? = "extension failed",
        evaluatedAt: Long = 1000L,
    ) = SourceEvaluation(
        evaluationKey = key,
        sourceId = null,
        extensionPkgName = packageName,
        signatureHash = signatureHash,
        extensionName = "Test Extension",
        sourceName = "Test Extension",
        lang = "en",
        baseUrl = null,
        repoName = "Test Repository",
        sourceCount = 1,
        isNsfw = false,
        evaluationVersion = 3,
        evaluatedAt = evaluatedAt,
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
        extensionVersionName = "1.0.0",
        extensionVersionCode = 1L,
        extensionApkName = "test.apk",
        catalogueMetadataConfidence = SourceEvaluationMetadataConfidence.UNKNOWN,
        detailEnrichmentAttemptCount = 1,
        detailEnrichmentSuccessCount = 0,
        metadataCandidateCount = 0,
        positiveCandidateCount = 0,
        negativeCandidateCount = 0,
        explicitPreferredGroupHitCount = 0,
        learnedPositiveGroupHitCount = 0,
        blockedCandidateCount = 0,
        adultSignalCandidateCount = 0,
    )

    @Test
    fun `replacement removes every stale package row and preserves replacement fields`() = runTest {
        val handler = openHandler()
        val repository = SourceEvaluationRepositoryImpl(handler)
        repository.upsert(evaluation("old-1"))
        repository.upsert(evaluation("old-2"))

        repository.replaceByPackage(
            pkgName = "test.extension",
            signatureHash = "signature",
            evaluation = evaluation("extension-error", evaluatedAt = 9999L, errorMessage = "missing class"),
        )

        val rows = repository.getByPackage("test.extension", "signature")
        assertEquals(1, rows.size)
        assertEquals("extension-error", rows.single().evaluationKey)
        assertEquals(9999L, rows.single().evaluatedAt)
        assertEquals("missing class", rows.single().errorMessage)
    }

    @Test
    fun `failure after delete rolls back stale-row deletion`() = runTest {
        val handler = openHandler()
        val repository = SourceEvaluationRepositoryImpl(handler)
        repository.upsert(evaluation("old-row"))
        val failingRepository = SourceEvaluationRepositoryImpl(
            FailingTransactionHandler(handler, IllegalStateException("forced post-delete failure")),
        )

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                failingRepository.replaceByPackage(
                    "test.extension",
                    "signature",
                    evaluation("replacement"),
                )
            }
        }

        val rows = repository.getByPackage("test.extension", "signature")
        assertEquals(listOf("old-row"), rows.map { it.evaluationKey })
    }

    private class FailingTransactionHandler(
        private val delegate: DatabaseHandler,
        private val failure: Throwable,
    ) : DatabaseHandler by delegate {
        override suspend fun <T> await(
            inTransaction: Boolean,
            block: suspend Database.() -> T,
        ): T {
            if (!inTransaction) return delegate.await(false, block)
            return delegate.await(true) {
                block()
                throw failure
            }
        }
    }
}
