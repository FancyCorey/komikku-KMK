package exh.recs.matching

import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks

/** Resolves only explicitly confirmed same-manga members, including the origin. */
class ConfirmedMangaGroupTargets(
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks,
    private val getManga: GetManga,
    private val identityResolver: CrossSourceIdentityAuthorizationResolver,
) {
    suspend fun await(origin: Manga): List<Manga> {
        val originLink = getCrossSourceMangaLinks.awaitBySourceUrl(origin.source, origin.url)
            ?: return listOf(origin)
        val members = identityResolver.confirmedGroupMembers(
            origin.source,
            origin.url,
            getCrossSourceMangaLinks.awaitByGroupId(originLink.groupId),
        )
        return buildList {
            add(origin)
            for (member in members) {
                getManga.await(member.url, member.source)?.let(::add)
            }
        }.distinctBy { it.source to it.url }
    }
}
