package exh.perf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import exh.recs.matching.ConfirmedTrackedMangaTasteTargets
import exh.recs.matching.CrossSourceIdentityAuthorizationResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy
import tachiyomi.domain.taste.model.CrossSourceRecordKey
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository
import tachiyomi.domain.tracker.model.LocalTrackedWork
import tachiyomi.domain.tracker.model.LocalTrackedWorkSource
import tachiyomi.domain.tracker.model.LocalTrackedWorkStatus
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Device complement to the host propagation tests. It drives the real resolver and taste
 * interactor against the seeded profile, while creating and removing only one disposable local
 * work. This is acceptance evidence for persistence and identity authorization, not UI evidence.
 */
@RunWith(AndroidJUnit4::class)
class RatingPropagationDeviceAcceptanceTest {

    @Test
    fun confirmedTrackedGroupReceivesOneRating() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        eu.kanade.tachiyomi.testutil.DisposableTestEnvironmentGuard.assumeDisposableEnvironment(context)

        val tasteRepository = Injekt.get<TasteRepository>()
        val mangaRepository = Injekt.get<tachiyomi.domain.manga.repository.MangaRepository>()
        val links = Injekt.get<GetCrossSourceMangaLinks>().awaitAll()
        val decisions = Injekt.get<GetCrossSourceIdentityDecisions>().awaitAll()
        val decision = decisions.firstOrNull { CrossSourceIdentityDecisionPolicy.isAuthoritativeConfirmation(it) }
            ?: error("seeded profile has no authoritative confirmed identity decision")
        val left = links.first { it.source == decision.pair.left.source && it.url == decision.pair.left.url }
        val right = links.first { it.source == decision.pair.right.source && it.url == decision.pair.right.url }
        val origin = mangaRepository.getMangaByUrlAndSourceId(left.url, left.source)
            ?: error("confirmed left manga is missing")
        val sibling = mangaRepository.getMangaByUrlAndSourceId(right.url, right.source)
            ?: error("confirmed right manga is missing")

        val localTrackerRepository = Injekt.get<LocalTrackerRepository>()
        val workId = "device-rating-propagation-${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        val previousOriginTaste = tasteRepository.getMangaTaste(origin.source, origin.url)
        val previousSiblingTaste = tasteRepository.getMangaTaste(sibling.source, sibling.url)
        try {
            localTrackerRepository.upsertWork(
                LocalTrackedWork(
                    id = workId,
                    title = origin.title,
                    normalizedTitle = origin.title.lowercase(),
                    status = LocalTrackedWorkStatus.READING,
                    lastChapterSource = origin.source,
                    lastChapterNumber = null,
                    lastChapterUrl = null,
                    lastChapterLabel = null,
                    lastProgressAt = null,
                    score = null,
                    startDate = now,
                    finishDate = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            localTrackerRepository.upsertSource(
                LocalTrackedWorkSource(
                    workId = workId,
                    source = origin.source,
                    url = origin.url,
                    title = origin.title,
                    confidence = 100,
                    confirmation = tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                    inheritanceOptedOut = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            localTrackerRepository.upsertSource(
                LocalTrackedWorkSource(
                    workId = workId,
                    source = sibling.source,
                    url = sibling.url,
                    title = sibling.title,
                    confidence = 100,
                    confirmation = tachiyomi.domain.tracker.model.LocalTrackedWorkSourceConfirmation.USER_CONFIRMED,
                    inheritanceOptedOut = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val targets = ConfirmedTrackedMangaTasteTargets(
                getCrossSourceMangaLinks = Injekt.get(),
                getManga = Injekt.get<GetManga>(),
                identityResolver = CrossSourceIdentityAuthorizationResolver(),
                localTrackerRepository = localTrackerRepository,
            ).await(origin)
            assertEquals(setOf(origin.source to origin.url, sibling.source to sibling.url), targets.map { it.source to it.url }.toSet())

            Injekt.get<SetMangaTasteBatch>().await(targets, MangaRating.LOVE)
            assertEquals(MangaRating.LOVE.value, tasteRepository.getMangaTaste(origin.source, origin.url)?.rating)
            assertEquals(MangaRating.LOVE.value, tasteRepository.getMangaTaste(sibling.source, sibling.url)?.rating)
            assertTrue("propagation must include the confirmed sibling", targets.any { it.id == sibling.id })
        } finally {
            restoreTaste(tasteRepository, previousOriginTaste, origin)
            restoreTaste(tasteRepository, previousSiblingTaste, sibling)
            localTrackerRepository.getWork(workId)?.let { localTrackerRepository.deleteWork(workId) }
        }
    }

    private suspend fun restoreTaste(repository: TasteRepository, previous: MangaTaste?, manga: Manga) {
        if (previous == null) {
            repository.deleteMangaTaste(manga.source, manga.url)
        } else {
            repository.upsertMangaTaste(previous)
        }
    }
}
