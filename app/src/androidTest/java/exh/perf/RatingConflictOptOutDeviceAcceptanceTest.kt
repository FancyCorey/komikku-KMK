package exh.perf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.testutil.DisposableTestEnvironmentGuard
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.recs.matching.CrossSourceIdentityAuthorizationResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Device proof that an explicit rejected same-manga decision is excluded from group actions. */
@RunWith(AndroidJUnit4::class)
class RatingConflictOptOutDeviceAcceptanceTest {

    @Test
    fun rejectedIdentityIsExcludedFromConfirmedGroupTargets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)

        val decisions = Injekt.get<GetCrossSourceIdentityDecisions>().awaitAll()
        val rejected = decisions.firstOrNull { !CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(it) }
            ?: error("seeded profile has no rejected identity decision")
        val links = Injekt.get<GetCrossSourceMangaLinks>().awaitAll()
        val left = links.firstOrNull { it.source == rejected.pair.left.source && it.url == rejected.pair.left.url }
            ?: error("rejected left link is missing")
        val origin = Injekt.get<MangaRepository>().getMangaByUrlAndSourceId(left.url, left.source)
            ?: error("rejected left manga is missing")

        val targets = ConfirmedMangaGroupTargets(
            getCrossSourceMangaLinks = Injekt.get(),
            getManga = Injekt.get<GetManga>(),
            identityResolver = CrossSourceIdentityAuthorizationResolver(),
        ).await(origin)

        assertEquals(1, targets.size)
        assertTrue("a rejected candidate must not be a confirmed group target", targets.single().id == origin.id)
        Injekt.get<TasteRepository>().getCrossSourceIdentityDecision(rejected.pair)?.let {
            assertTrue("the explicit rejection must remain persisted", !CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(it))
        }
        Unit
    }
}
