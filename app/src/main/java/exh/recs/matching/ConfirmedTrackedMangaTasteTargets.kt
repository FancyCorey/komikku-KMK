package exh.recs.matching

import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.tracker.repository.LocalTrackerRepository

/**
 * Resolves only confirmed, already locally tracked members of the origin's link group.
 * Unconfirmed or merely discovered candidates must remain explicit user selections.
 */
class ConfirmedTrackedMangaTasteTargets(
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks,
    private val getManga: GetManga,
    private val identityResolver: CrossSourceIdentityAuthorizationResolver,
    private val localTrackerRepository: LocalTrackerRepository,
) {
    suspend fun await(origin: Manga): List<Manga> {
        val originLink = getCrossSourceMangaLinks.awaitBySourceUrl(origin.source, origin.url)
            ?: return listOf(origin)
        val members = identityResolver.confirmedGroupMembers(
            origin.source,
            origin.url,
            getCrossSourceMangaLinks.awaitByGroupId(originLink.groupId),
        )
        val targets = buildList {
            for (member in members) {
                val manga = getManga.await(member.url, member.source) ?: continue
                val isOrigin = manga.source == origin.source && manga.url == origin.url
                val isTracked = isOrigin || localTrackerRepository.getWorkIdBySourceUrl(manga.source, manga.url) != null
                if (isTracked) add(manga)
            }
        }
        return targets.distinctBy { it.source to it.url }.ifEmpty { listOf(origin) }
    }
}
