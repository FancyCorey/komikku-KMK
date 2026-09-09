package tachiyomi.data.taste

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceIdentityReasonCode
import tachiyomi.domain.taste.model.CrossSourceIdentityReplacement
import tachiyomi.domain.taste.model.CrossSourceIdentityReviewState
import tachiyomi.domain.taste.model.CrossSourceRecordKey

class CrossSourceIdentityDecisionRepositoryTest {

    private fun repository(): TasteRepositoryImpl {
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
        return TasteRepositoryImpl(AndroidDatabaseHandler(database, driver))
    }

    private fun row(
        updatedAt: Long = 2_000,
        decision: CrossSourceIdentityDecisionValue = CrossSourceIdentityDecisionValue.USER_CONFIRMED,
    ) = CrossSourceIdentityDecision(
        pair = CrossSourceIdentityDecisionPolicy.canonicalPair(
            CrossSourceRecordKey(2, "/b"),
            CrossSourceRecordKey(1, "/a"),
        ),
        decision = decision,
        decisionVersion = 1,
        evidenceVersion = 1,
        reasonCodes = setOf(
            if (decision == CrossSourceIdentityDecisionValue.USER_CONFIRMED) {
                CrossSourceIdentityReasonCode.USER_CONFIRMATION
            } else {
                CrossSourceIdentityReasonCode.USER_REJECTION
            },
        ),
        reviewState = CrossSourceIdentityReviewState.CURRENT,
        createdAt = 1_000,
        updatedAt = updatedAt,
    )

    @Test
    fun `upsert and reverse-order lookup use one canonical row`() = runTest {
        val repository = repository()
        val original = row()
        repository.upsertCrossSourceIdentityDecisions(listOf(original))
        val reverse = CrossSourceIdentityDecisionPolicy.canonicalPair(original.pair.right, original.pair.left)
        assertEquals(original, repository.getCrossSourceIdentityDecision(reverse))
        assertEquals(1, repository.getAllCrossSourceIdentityDecisions().size)
    }

    @Test
    fun `batch duplicate pair resolves by timestamp rather than input order`() = runTest {
        val repository = repository()
        val newer = row(updatedAt = 3_000, decision = CrossSourceIdentityDecisionValue.USER_REJECTED)
        repository.upsertCrossSourceIdentityDecisions(listOf(newer, row(updatedAt = 2_000)))
        assertEquals(newer, repository.getCrossSourceIdentityDecision(newer.pair))
    }

    @Test
    fun `conflict checked replacement succeeds only against exact expected state`() = runTest {
        val repository = repository()
        val original = row()
        val replacement = row(updatedAt = 3_000, decision = CrossSourceIdentityDecisionValue.USER_REJECTED)
        repository.upsertCrossSourceIdentityDecisions(listOf(original))

        assertFalse(repository.replaceCrossSourceIdentityDecision(row(updatedAt = 1_999), replacement))
        assertEquals(original, repository.getCrossSourceIdentityDecision(original.pair))
        assertTrue(repository.replaceCrossSourceIdentityDecision(original, replacement))
        assertEquals(replacement, repository.getCrossSourceIdentityDecision(original.pair))
    }

    @Test
    fun `conflict checked removal never deletes a newer row`() = runTest {
        val repository = repository()
        val current = row(updatedAt = 3_000)
        repository.upsertCrossSourceIdentityDecisions(listOf(current))
        assertFalse(repository.replaceCrossSourceIdentityDecision(row(updatedAt = 2_000), null))
        assertEquals(current, repository.getCrossSourceIdentityDecision(current.pair))
        assertTrue(repository.replaceCrossSourceIdentityDecision(current, null))
        assertNull(repository.getCrossSourceIdentityDecision(current.pair))
    }

    @Test
    fun `batch replacement is all or nothing when any expected row is stale`() = runTest {
        val repository = repository()
        val first = row()
        val second = row().copy(
            pair = CrossSourceIdentityDecisionPolicy.canonicalPair(CrossSourceRecordKey(3, "/c"), CrossSourceRecordKey(4, "/d")),
        )
        repository.upsertCrossSourceIdentityDecisions(listOf(first, second))
        val firstReplacement = row(updatedAt = 3_000, decision = CrossSourceIdentityDecisionValue.USER_REJECTED)
        val secondReplacement = second.copy(updatedAt = 3_000, decision = CrossSourceIdentityDecisionValue.USER_REJECTED)
        val result = repository.replaceCrossSourceIdentityDecisions(
            listOf(
                CrossSourceIdentityReplacement(first, firstReplacement),
                CrossSourceIdentityReplacement(second.copy(updatedAt = 1_999), secondReplacement),
            ),
        )
        assertFalse(result)
        assertEquals(first, repository.getCrossSourceIdentityDecision(first.pair))
        assertEquals(second, repository.getCrossSourceIdentityDecision(second.pair))
    }

    @Test
    fun `delete all creates tombstones and preserves already-newer state`() = runTest {
        val repository = repository()
        val old = row(updatedAt = 2_000)
        val newer = row(updatedAt = 5_000, decision = CrossSourceIdentityDecisionValue.USER_REJECTED).copy(
            pair = CrossSourceIdentityDecisionPolicy.canonicalPair(
                CrossSourceRecordKey(3, "/c"),
                CrossSourceRecordKey(4, "/d"),
            ),
        )
        repository.upsertCrossSourceIdentityDecisions(listOf(old, newer))
        repository.tombstoneAllCrossSourceIdentityDecisions(updatedAt = 4_000)
        val rows = repository.getAllCrossSourceIdentityDecisions().associateBy { it.pair }
        assertEquals(4_000L, rows.getValue(old.pair).deletedAt)
        assertNull(rows.getValue(newer.pair).deletedAt)
        assertEquals(5_000L, rows.getValue(newer.pair).updatedAt)
    }
}
