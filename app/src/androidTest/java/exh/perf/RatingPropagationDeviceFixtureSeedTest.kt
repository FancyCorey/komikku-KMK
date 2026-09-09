package exh.perf

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.testutil.DisposableTestEnvironmentGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.model.CrossSourceIdentityDecision
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionValue
import tachiyomi.domain.taste.model.CrossSourceIdentityReviewState
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import tachiyomi.domain.taste.repository.TasteRepository
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Seeds the realistic profile and adds the three persisted identity states required by V1B:
 * confirmed, rejected, and a same-pair conflict reduced to NEEDS_REVIEW. The fixture uses the
 * existing generator and real repositories; it does not mutate production behavior or schema.
 */
@RunWith(AndroidJUnit4::class)
class RatingPropagationDeviceFixtureSeedTest {

    @Test
    fun seedRatingPropagationStates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)
        val arguments = InstrumentationRegistry.getArguments()
        val totalManga = arguments.getString("totalManga")?.toIntOrNull() ?: 40
        val dataset = PerformanceFixtureGenerator.generate(
            PerformanceFixtureSpec.REALISTIC.copy(totalManga = totalManga),
        )

        PerformanceFixtureSeeder.seed(
            dataset = dataset,
            mangaRepository = Injekt.get<MangaRepository>(),
            chapterRepository = Injekt.get<ChapterRepository>(),
            categoryRepository = Injekt.get<CategoryRepository>(),
            historyRepository = Injekt.get<HistoryRepository>(),
            tasteRepository = Injekt.get<TasteRepository>(),
            localTrackerRepository = Injekt.get<LocalTrackerRepository>(),
        )

        val groups = dataset.manga
            .mapNotNull { manga -> manga.crossSourceGroupId?.let { it to manga } }
            .groupBy({ it.first }, { it.second })
            .values
            .filter { it.size >= 2 }
        assertTrue("realistic fixture must contain three cross-source pairs", groups.size >= 3)

        val tasteRepository = Injekt.get<TasteRepository>()
        val now = System.currentTimeMillis()
        val confirmed = decision(groups[0][0], groups[0][1], CrossSourceIdentityDecisionValue.USER_CONFIRMED, now)
        val rejected = decision(groups[1][0], groups[1][1], CrossSourceIdentityDecisionValue.USER_REJECTED, now + 1)
        val conflictPair = CrossSourceIdentityDecisionPolicy.canonicalPair(
            key(groups[2][0]),
            key(groups[2][1]),
        )
        val conflict = CrossSourceIdentityDecisionPolicy.merge(
            decision(conflictPair, CrossSourceIdentityDecisionValue.USER_CONFIRMED, now + 2),
            decision(conflictPair, CrossSourceIdentityDecisionValue.USER_REJECTED, now + 2),
        )
        tasteRepository.upsertCrossSourceIdentityDecisions(listOf(confirmed, rejected, conflict))

        val stored = tasteRepository.getAllCrossSourceIdentityDecisions()
            .filter { it.pair in setOf(confirmed.pair, rejected.pair, conflict.pair) }
        assertEquals(3, stored.size)
        assertEquals(CrossSourceIdentityDecisionValue.USER_CONFIRMED, stored.single { it.pair == confirmed.pair }.decision)
        assertEquals(CrossSourceIdentityDecisionValue.USER_REJECTED, stored.single { it.pair == rejected.pair }.decision)
        assertEquals(CrossSourceIdentityReviewState.NEEDS_REVIEW, stored.single { it.pair == conflict.pair }.reviewState)
        Log.i(TAG, "V1B fixture ready: confirmed=1 rejected=1 needsReview=1 totalManga=$totalManga")
        Unit
    }

    private fun key(manga: GeneratedManga) = CrossSourceRecordKey(manga.source, manga.url)

    private fun decision(
        first: GeneratedManga,
        second: GeneratedManga,
        value: CrossSourceIdentityDecisionValue,
        timestamp: Long,
    ) = decision(CrossSourceIdentityDecisionPolicy.canonicalPair(key(first), key(second)), value, timestamp)

    private fun decision(
        pair: tachiyomi.domain.taste.model.CrossSourceIdentityPair,
        value: CrossSourceIdentityDecisionValue,
        timestamp: Long,
    ) = CrossSourceIdentityDecisionPolicy.userDecision(pair, value, previous = null, timestamp = timestamp)

    companion object {
        private const val TAG = "RatingPropagationFixture"
    }
}
