package exh.recs.links

// KMK --> v0.7.30
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.model.CrossSourceMangaLink
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A link group is a set of [CrossSourceMangaLink] rows sharing the same [CrossSourceMangaLink.groupId].
 */
@Immutable
data class LinkGroup(
    val groupId: String,
    val links: List<CrossSourceMangaLink>,
) {
    val primaryTitle: String
        get() = links.firstOrNull()?.title ?: groupId
}

// KMK --> v0.8.0: optional focusedGroupId scopes results to a single group ("Manage Group" action
// from Rated Manga), instead of duplicating this screen as a separate global manager.
class LinkGroupManagementScreenModel(
    private val focusedGroupId: String? = null,
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    private val deleteCrossSourceMangaLink: DeleteCrossSourceMangaLink = Injekt.get(),
) : StateScreenModel<LinkGroupManagementScreenModel.State>(State.Loading) {
    // KMK <--

    init {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val links = if (focusedGroupId != null) {
                getCrossSourceMangaLinks.awaitByGroupId(focusedGroupId)
            } else {
                getCrossSourceMangaLinks.awaitAll()
            }
            val groups = links
                .groupBy { it.groupId }
                .map { (groupId, members) -> LinkGroup(groupId, members) }
                .sortedBy { it.primaryTitle.lowercase() }
            mutableState.value = if (groups.isEmpty()) State.Empty else State.Success(groups)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableState.value = State.Error(e)
        }
    }

    fun reload() {
        mutableState.value = State.Loading
        screenModelScope.launch { load() }
    }

    fun deleteGroup(groupId: String) {
        val current = mutableState.value as? State.Success ?: return
        // Optimistic remove
        val updatedGroups = current.groups.filter { it.groupId != groupId }
        mutableState.value = if (updatedGroups.isEmpty()) State.Empty else current.copy(groups = updatedGroups)
        screenModelScope.launch {
            try {
                deleteCrossSourceMangaLink.awaitByGroupId(groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reload() // roll back on error
            }
        }
    }

    fun deleteLink(source: Long, url: String) {
        val current = mutableState.value as? State.Success ?: return
        val updatedGroups = current.groups.mapNotNull { group ->
            val remaining = group.links.filter { it.source != source || it.url != url }
            if (remaining.isEmpty()) null else group.copy(links = remaining)
        }
        mutableState.value = if (updatedGroups.isEmpty()) State.Empty else current.copy(groups = updatedGroups)
        screenModelScope.launch {
            try {
                deleteCrossSourceMangaLink.awaitBySourceUrl(source, url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reload()
            }
        }
    }

    sealed interface State {
        data object Loading : State
        data object Empty : State
        data class Error(val error: Throwable) : State

        @Immutable
        data class Success(val groups: List<LinkGroup>) : State
    }
}
// KMK <--
