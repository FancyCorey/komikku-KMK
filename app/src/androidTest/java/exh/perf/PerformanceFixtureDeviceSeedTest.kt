package exh.perf

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.repository.TasteRepository
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK HR-2026-08-26-CONSTRAINED-DEVICE-PERFORMANCE-PROGRAM (C4) -->
/**
 * On-device seeding entry point for the reusable C4 performance fixture. Runs as a normal
 * instrumented test so it gets a fully bootstrapped app process (real `App.onCreate`, real Injekt
 * graph, real database) for free -- no bespoke debug menu, broadcast receiver, or manifest wiring
 * needed, and it stays rerunnable with a single command against any connected/target device:
 *
 * ```
 * adb -s <serial> shell pm clear app.komikku.dev   # start from a clean library (optional but recommended)
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=exh.perf.PerformanceFixtureDeviceSeedTest
 * ```
 *
 * [PerformanceFixtureGenerator]/[PerformanceFixtureSeeder] themselves are already fully host-tested
 * (see `PerformanceFixtureGeneratorTest`/`PerformanceFixtureSeederTest` in `app/src/test`) against
 * an in-memory copy of the exact same production schema -- this test's only job is wiring them to
 * the REAL on-device repositories via Injekt, which cannot itself be exercised on the host.
 *
 * The profile defaults to `realistic-1k`; pass `-e profile <stable-profile-id>` to select one of
 * [PerformanceFixtureSpec.PROFILES], or pass `-e totalManga <n>` for a quick custom-scale smoke
 * check. Profile selection takes precedence over `totalManga`.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceFixtureDeviceSeedTest {

    @Test
    fun seedPerformanceFixture() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val requestedProfile = arguments.getString("profile")
        val requestedTotal = arguments.getString("totalManga")?.toIntOrNull()
        val spec = when {
            requestedProfile != null -> PerformanceFixtureSpec.profile(requestedProfile)
            requestedTotal != null -> PerformanceFixtureSpec.REALISTIC.copy(totalManga = requestedTotal)
            else -> PerformanceFixtureSpec.REALISTIC
        }

        Log.i(TAG, "Generating fixture dataset: profile=${requestedProfile ?: "realistic-1k"}, totalManga=${spec.totalManga}, seed=${spec.seed}")
        val dataset = PerformanceFixtureGenerator.generate(spec)

        val startedAt = System.currentTimeMillis()
        val result = PerformanceFixtureSeeder.seed(
            dataset = dataset,
            mangaRepository = Injekt.get<MangaRepository>(),
            chapterRepository = Injekt.get<ChapterRepository>(),
            categoryRepository = Injekt.get<CategoryRepository>(),
            historyRepository = Injekt.get<HistoryRepository>(),
            tasteRepository = Injekt.get<TasteRepository>(),
            localTrackerRepository = Injekt.get<LocalTrackerRepository>(),
            progress = { message -> Log.i(TAG, message) },
        )
        val elapsedMs = System.currentTimeMillis() - startedAt

        Log.i(
            TAG,
            "Seed complete in ${elapsedMs}ms: manga=${result.mangaIds.size} categories=${result.categoryIds.size} " +
                "chapters=${result.chapterCount} history=${result.historyCount} rated=${result.ratedCount} " +
                "crossSourceLinks=${result.crossSourceLinkCount} localTracking=${result.localTrackingCount}",
        )

        // A basic sanity assertion -- the real coverage (distribution/edge-case correctness) is
        // already proven on the host by PerformanceFixtureGeneratorTest/PerformanceFixtureSeederTest;
        // this only confirms the on-device Injekt wiring itself actually reached the real database.
        assertEquals(spec.totalManga, result.mangaIds.size)
    }

    companion object {
        private const val TAG = "PerformanceFixture"
    }
}
// KMK <--
