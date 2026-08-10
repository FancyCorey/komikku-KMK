package exh.recs.links

// KMK --> v0.8.0
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import exh.recs.loved.RatedMangaKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetCrossSourceGroupPrimary
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One row in the focused linked-version list — source name/language, title, rating, favorite
 * status, installed/missing status, last-updated (from the link row), and whether this is the
 * user-selected primary version. [mangaId] is null when the local manga row could not be resolved
 * (link title/source/url fallback is shown instead).
 */
@Immutable
data class LinkedVersionRow(
    val key: RatedMangaKey,
    val sourceName: String?,
    val lang: String,
    val title: String,
    val rating: MangaRating?,
    val isFavorite: Boolean,
    val isInstalled: Boolean,
    val updatedAt: Long,
    val isPrimary: Boolean,
    val mangaId: Long?,
)

/** Pure builder — no Android/DB access — for [LinkedVersionRow] rows. Fully unit-testable. */
internal object LinkedVersionListBuilder {
    data class MemberInput(
        val link: CrossSourceMangaLink,
        val sourceName: String?,
        val lang: String,
        val manga: Manga?,
        val taste: MangaTaste?,
    )

    fun build(members: List<MemberInput>, primary: RatedMangaKey?): List<LinkedVersionRow> {
        return members.map { m ->
            val key = RatedMangaKey(m.link.source, m.link.url)
            LinkedVersionRow(
                key = key,
                sourceName = m.sourceName,
                lang = m.lang,
                title = m.manga?.title ?: m.link.title,
                rating = m.taste?.rating?.let { MangaRating.fromValue(it) },
                isFavorite = m.manga?.favorite ?: false,
                isInstalled = m.sourceName != null,
                updatedAt = m.link.updatedAt,
                isPrimary = primary != null && key == primary,
                mangaId = m.manga?.id,
            )
        }.sortedWith(compareByDescending<LinkedVersionRow> { it.isPrimary }.thenByDescending { it.updatedAt })
    }
}

/**
 * Loads a focused version list for one [groupId] directly from persisted group data — does not
 * depend on the rated screen's in-memory display items, so it stays correct even if opened from a
 * stale/flat list.
 */
class LinkedVersionListScreenModel(
    private val groupId: String,
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    private val getCrossSourceGroupPrimary: GetCrossSourceGroupPrimary = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val setCrossSourceGroupPrimary: SetCrossSourceGroupPrimary = Injekt.get(),
    private val deleteCrossSourceMangaLink: DeleteCrossSourceMangaLink = Injekt.get(),
    private val clearCrossSourceGroupPrimary: ClearCrossSourceGroupPrimary = Injekt.get(),
) : StateScreenModel<LinkedVersionListScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val links = getCrossSourceMangaLinks.awaitByGroupId(groupId)
            if (links.isEmpty()) {
                mutableState.value = State.Empty
                return
            }
            val primary = getCrossSourceGroupPrimary.awaitByGroupId(groupId)
                ?.let { RatedMangaKey(it.source, it.url) }

            val members = links.map { link ->
                // Missing/uninstalled sources must not crash — sourceManager.get() returns null
                // for a source that isn't currently installed, which is the "missing" signal here.
                // sourceManager.get() is a synchronous lookup, not a suspend call.
                val source = try {
                    sourceManager.get(link.source)
                } catch (e: Exception) {
                    null
                }
                val manga = try {
                    getManga.await(link.url, link.source)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val taste = manga?.let { m ->
                    try {
                        getMangaTaste.await(m.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                LinkedVersionListBuilder.MemberInput(
                    link = link,
                    sourceName = source?.name,
                    lang = source?.lang ?: "",
                    manga = manga,
                    taste = taste,
                )
            }
            val rows = LinkedVersionListBuilder.build(members, primary)
            mutableState.value = State.Success(groupId = groupId, rows = rows)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableState.value = State.Error(e)
        }
    }

    fun reload() {
        screenModelScope.launch { load() }
    }

    fun setPrimary(key: RatedMangaKey) {
        screenModelScope.launch {
            try {
                setCrossSourceGroupPrimary.await(groupId, key.source, key.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // load() below still runs and reflects whatever the actual persisted state is.
            }
            load()
        }
    }

    fun removeFromGroup(key: RatedMangaKey) {
        screenModelScope.launch {
            try {
                deleteCrossSourceMangaLink.awaitBySourceUrl(key.source, key.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // load() below still runs and reflects whatever the actual persisted state is.
            }
            load()
        }
    }

    fun ungroup() {
        screenModelScope.launch {
            try {
                deleteCrossSourceMangaLink.awaitByGroupId(groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // load() below still runs and reflects whatever the actual persisted state is.
            }
            try {
                clearCrossSourceGroupPrimary.await(groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // load() below still runs and reflects whatever the actual persisted state is.
            }
            load()
        }
    }

    sealed interface State {
        data object Loading : State
        data object Empty : State
        data class Error(val error: Throwable) : State

        @Immutable
        data class Success(val groupId: String, val rows: List<LinkedVersionRow>) : State
    }
}
// KMK <--
