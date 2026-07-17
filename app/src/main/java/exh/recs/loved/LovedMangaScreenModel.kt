package exh.recs.loved

// KMK -->
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

// KMK --> v0.7.14: sort options for Loved Manga
enum class LoveSortMode { RECENT, OLDEST, TITLE_AZ, SOURCE }
// KMK <--

data class LovedMangaEntry(
    val taste: MangaTaste,
    val manga: Manga?,
)

// KMK --> v0.8.0: stable key type for a rated manga entry, reused across selection/group actions
data class RatedMangaKey(val source: Long, val url: String) {
    companion object {
        fun of(taste: MangaTaste) = RatedMangaKey(taste.source, taste.url)
    }
}
// KMK <--

@Immutable
data class LovedDisplayItem(
    val taste: MangaTaste,
    val manga: Manga?,
    val versionCount: Int,
    // KMK --> v0.8.0: group transparency for the item action menu / selection actions. Kept on the
    // existing LovedDisplayItem name (not renamed to RatedMangaDisplayItem) to avoid unnecessary
    // churn across LovedMangaScreen/RatedMangaScreen/RecommendationBundleExporter call sites — the
    // added fields below are exactly what the v0.8.0 plan's suggested RatedMangaDisplayItem shape
    // needed.
    val confirmedGroupId: String? = null,
    val memberKeys: List<RatedMangaKey> = emptyList(),
    val hasConfirmedGroup: Boolean = false,
    // KMK <--
) {
    val key: RatedMangaKey get() = RatedMangaKey(taste.source, taste.url)
}

class LovedMangaScreenModel(
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    // KMK --> v0.7.2: use confirmed cross-source link groups for grouping
    private val getCrossSourceMangaLinks: GetCrossSourceMangaLinks = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.3: filter entries from uninstalled sources
    private val sourceManager: SourceManager = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.35: controls which rating tier this screen shows (LOVE/LIKE/DISLIKE)
    private val filterRating: MangaRating = MangaRating.LOVE,
    // KMK <--
    // KMK --> v0.8.0: bulk selection + group actions
    private val getCrossSourceGroupPrimary: GetCrossSourceGroupPrimary = Injekt.get(),
    private val setCrossSourceGroupPrimary: SetCrossSourceGroupPrimary = Injekt.get(),
    private val clearCrossSourceGroupPrimary: ClearCrossSourceGroupPrimary = Injekt.get(),
    private val setMangaTaste: SetMangaTaste = Injekt.get(),
    private val clearMangaTaste: ClearMangaTaste = Injekt.get(),
    private val upsertCrossSourceMangaLinks: UpsertCrossSourceMangaLinks = Injekt.get(),
    private val deleteCrossSourceMangaLink: DeleteCrossSourceMangaLink = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
) : StateScreenModel<LovedMangaScreenModel.State>(State.Loading) {

    init {
        // KMK --> v0.7.29: subscribe to live updates so the screen reacts to taste changes without manual refresh
        screenModelScope.launch {
            getMangaTaste.subscribeAll().collectLatest { allTastes -> load(allTastes) }
        }
        // KMK <--
    }

    private suspend fun load(allTastes: List<MangaTaste>) {
        runCatching {
            // KMK --> v0.7.3: fail-safe source id lookup; empty set → hides all rather than showing uninstalled entries
            val installedSourceIds: Set<Long> = runCatching {
                sourceManager.getVisibleSources().map { it.id }.toSet()
            }.getOrDefault(emptySet())
            // KMK <--

            // KMK --> v0.7.37: load link groups before rating filter; needed for exclusivity resolution and display grouping
            val linkGroupByKey: Map<String, String> = runCatching {
                getCrossSourceMangaLinks.awaitAll()
                    .associate { "${it.source}|${it.url}" to it.groupId }
            }.getOrDefault(emptyMap())
            // KMK <--

            // KMK --> v0.8.0: stored primary versions, fail-safe (empty map falls back to grouper choice)
            val primaryByGroupId: Map<String, RatedMangaKey> = runCatching {
                getCrossSourceGroupPrimary.awaitAll()
                    .associate { it.groupId to RatedMangaKey(it.source, it.url) }
            }.getOrDefault(emptyMap())
            // KMK <--

            // KMK --> v0.7.35: use generalized filter; legacy path kept for LOVE default
            // KMK --> v0.7.37: apply cross-source group rating exclusivity before rating filter
            val installedTastes = allTastes.filter { it.source in installedSourceIds }
            val resolvedTastes = resolveLinkedGroupRatingConflicts(installedTastes, linkGroupByKey)
            val lovedTastes = resolvedTastes
                .filter { it.rating == filterRating.value }
                .sortedByDescending { it.updatedAt }
            // KMK <--

            val entries = lovedTastes.map { taste ->
                val manga = getManga.await(taste.mangaId)
                    ?: getManga.await(taste.url, taste.source)
                LovedMangaEntry(taste = taste, manga = manga)
            }

            // KMK --> v0.7.45: default grouped display on, per the v0.7 closure amendment.
            // `load()` re-runs reactively on every taste change anywhere in the app (see the
            // `subscribeAll()` collector in init), so the previous hardcoded `false` here was
            // silently resetting the user's manual "show flat" toggle back to grouped every time
            // *any* manga was rated. Preserve the current toggle across reloads; only fall back to
            // the default (grouped) on the very first load, when there is no prior state yet.
            val previous = mutableState.value as? State.Success
            val groupDuplicates = previous?.groupDuplicates ?: true
            // KMK --> v0.8.0: preserve selection mode/keys across reactive reloads. Pruning stale
            // selection keys is deliberately NOT done here (an entry that drops out of the loaded
            // list is simply not resolvable by any action; actions no-op for keys with no entry).
            val selectionMode = previous?.selectionMode ?: false
            val selectedKeys = previous?.selectedKeys ?: emptySet()
            // KMK <--
            mutableState.value = if (entries.isEmpty()) {
                State.Empty
            } else {
                State.Success(
                    entries = entries,
                    groupDuplicates = groupDuplicates,
                    linkGroupByKey = linkGroupByKey,
                    primaryByGroupId = primaryByGroupId,
                    selectionMode = selectionMode,
                    selectedKeys = selectedKeys,
                )
            }
            // KMK <--
        }.onFailure { e ->
            if (e is CancellationException) throw e
            mutableState.value = State.Error(e)
        }
    }

    fun toggleGroupDuplicates() {
        val current = mutableState.value as? State.Success ?: return
        mutableState.value = current.copy(groupDuplicates = !current.groupDuplicates)
    }

    // KMK --> v0.7.14: sort mode toggle
    fun setSortMode(mode: LoveSortMode) {
        val current = mutableState.value as? State.Success ?: return
        mutableState.value = current.copy(sortMode = mode)
    }
    // KMK <--

    // KMK --> v0.8.0: selection mode — delegates to the pure RatedSelectionReducer (unit-tested
    // independently) so the ScreenModel only wires state in/out.
    /** Long-press: enters selection mode (if not already) and selects [key]. */
    fun enterSelection(key: RatedMangaKey) {
        val current = mutableState.value as? State.Success ?: return
        val next = RatedSelectionReducer.enter(current.toSelection(), key)
        mutableState.value = current.withSelection(next)
    }

    /** App-bar "Select" action: enters selection mode without selecting any item. */
    fun enterSelectionMode() {
        val current = mutableState.value as? State.Success ?: return
        val next = RatedSelectionReducer.enterEmpty(current.toSelection())
        mutableState.value = current.withSelection(next)
    }

    /** Tap while in selection mode: toggles [key]'s selection. No-op outside selection mode. */
    fun toggleSelection(key: RatedMangaKey) {
        val current = mutableState.value as? State.Success ?: return
        val next = RatedSelectionReducer.toggle(current.toSelection(), key)
        mutableState.value = current.withSelection(next)
    }

    fun clearSelection() {
        val current = mutableState.value as? State.Success ?: return
        mutableState.value = current.withSelection(RatedSelectionReducer.clear())
    }

    /** Selects every currently loaded (installed/visible, this rating tier) member of [groupId]. */
    fun selectAllInGroup(groupId: String) {
        val current = mutableState.value as? State.Success ?: return
        val memberKeys = current.displayItems
            .filter { it.confirmedGroupId == groupId }
            .flatMap { it.memberKeys }
        val next = RatedSelectionReducer.selectAll(current.toSelection(), memberKeys)
        mutableState.value = current.withSelection(next)
    }
    // KMK <--

    // KMK --> v0.8.0: rating bulk actions — reuse SetMangaTaste/ClearMangaTaste per entry, which
    // preserves rating exclusivity intrinsically (one manga_taste row per manga_id).
    fun changeSelectedRating(rating: MangaRating) {
        val current = mutableState.value as? State.Success ?: return
        val targets = current.entries.filter { RatedMangaKey.of(it.taste) in current.selectedKeys }
        if (targets.isEmpty()) return
        screenModelScope.launch {
            targets.forEach { entry ->
                runCatching {
                    setMangaTaste.await(
                        mangaId = entry.taste.mangaId,
                        source = entry.taste.source,
                        url = entry.taste.url,
                        title = entry.manga?.title ?: entry.taste.title,
                        rating = rating,
                    )
                }
            }
            clearSelection()
        }
    }

    /** Clears ratings for the current selection. Does not touch cross-source link rows. */
    fun clearSelectedRatings() {
        val current = mutableState.value as? State.Success ?: return
        val targets = current.entries.filter { RatedMangaKey.of(it.taste) in current.selectedKeys }
        if (targets.isEmpty()) return
        screenModelScope.launch {
            targets.forEach { entry ->
                runCatching { clearMangaTaste.await(entry.taste.mangaId) }
            }
            clearSelection()
        }
    }

    /** Mild negative signal — same "not interested" store For You/manga-detail already use. */
    fun markSelectedNotInterested() {
        val current = mutableState.value as? State.Success ?: return
        val targets = current.selectedKeys
        if (targets.isEmpty()) return
        screenModelScope.launch {
            runCatching {
                val raw = sourcePreferences.seenRecommendationMangaKeys().get()
                var seen = SeenRecommendationMangaStore.parse(raw)
                targets.forEach { key ->
                    seen = SeenRecommendationMangaStore.add(seen, SeenMangaKey(key.source, key.url))
                }
                sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(seen))
            }
            clearSelection()
        }
    }
    // KMK <--

    // KMK v0.8.7 -->
    /**
     * Undo for [clearSelectedRatings]: re-applies each snapshotted [MangaTaste] exactly as it was
     * before the clear, using the same [setMangaTaste] interactor a normal rating change uses.
     * [snapshot] must be captured by the caller *before* invoking [clearSelectedRatings] — this
     * screen model does not keep its own undo history, matching the existing project convention
     * (see `LibraryTab.kt`'s merge-undo Snackbar) of the caller owning the pre-action snapshot and
     * the Snackbar's `SnackbarResult.ActionPerformed` branch driving the restore call.
     */
    fun restoreRatings(snapshot: List<MangaTaste>) {
        if (snapshot.isEmpty()) return
        screenModelScope.launch {
            snapshot.forEach { taste ->
                runCatching {
                    setMangaTaste.await(
                        mangaId = taste.mangaId,
                        source = taste.source,
                        url = taste.url,
                        title = taste.title,
                        rating = MangaRating.fromValue(taste.rating) ?: return@runCatching,
                    )
                }
            }
        }
    }

    /** Undo for [markSelectedNotInterested]: removes exactly the keys that were just added, leaving any other "not interested" entries the user had before untouched. */
    fun undoMarkNotInterested(keys: Set<RatedMangaKey>) {
        if (keys.isEmpty()) return
        screenModelScope.launch {
            runCatching {
                val raw = sourcePreferences.seenRecommendationMangaKeys().get()
                var seen = SeenRecommendationMangaStore.parse(raw)
                keys.forEach { key ->
                    seen = SeenRecommendationMangaStore.remove(seen, SeenMangaKey(key.source, key.url))
                }
                sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(seen))
            }
        }
    }
    // KMK <--

    // KMK --> v0.8.0: group actions
    /**
     * Merges the current selection into one group. Requires >= 2 selected entries. Never merges by
     * title — see [RatedGroupMergePlanner]. If the selection spans multiple existing groups, every
     * member of every non-target group is folded into the target group (a genuine merge).
     */
    fun mergeSelectedIntoGroup() {
        val current = mutableState.value as? State.Success ?: return
        val selectedEntries = current.entries.filter { RatedMangaKey.of(it.taste) in current.selectedKeys }
        if (selectedEntries.size < 2) return
        screenModelScope.launch {
            runCatching {
                val selected = selectedEntries.map { entry ->
                    val key = RatedMangaKey.of(entry.taste)
                    RatedGroupMergePlanner.SelectedEntry(
                        key = key,
                        title = entry.manga?.title ?: entry.taste.title,
                        existingGroupId = current.linkGroupByKey["${key.source}|${key.url}"],
                    )
                }
                val distinctGroupIds = selected.mapNotNull { it.existingGroupId }.distinct()
                val existingGroupMembers = distinctGroupIds.associateWith { groupId ->
                    getCrossSourceMangaLinks.awaitByGroupId(groupId)
                }
                val plan = RatedGroupMergePlanner.plan(
                    selected = selected,
                    existingGroupMembers = existingGroupMembers,
                    now = System.currentTimeMillis(),
                    newGroupIdProvider = { UUID.randomUUID().toString() },
                )
                if (plan != null) upsertCrossSourceMangaLinks.await(plan.writes)
            }
            clearSelection()
        }
    }

    /** Removes the current selection from their current confirmed group(s). Does not clear ratings. */
    fun removeSelectedFromGroup() {
        val current = mutableState.value as? State.Success ?: return
        val targets = current.selectedKeys.filter { current.linkGroupByKey.containsKey("${it.source}|${it.url}") }
        if (targets.isEmpty()) return
        screenModelScope.launch {
            targets.forEach { key ->
                runCatching { deleteCrossSourceMangaLink.awaitBySourceUrl(key.source, key.url) }
            }
            clearSelection()
        }
    }

    /** Deletes every link in [groupId] and its stored primary version. Does not clear ratings. */
    fun ungroup(groupId: String) {
        screenModelScope.launch {
            runCatching { deleteCrossSourceMangaLink.awaitByGroupId(groupId) }
            runCatching { clearCrossSourceGroupPrimary.await(groupId) }
            clearSelection()
        }
    }

    /** Sets which linked version controls the rated-list cover/title for [groupId]. */
    fun setPrimaryVersion(groupId: String, key: RatedMangaKey) {
        screenModelScope.launch {
            runCatching { setCrossSourceGroupPrimary.await(groupId, key.source, key.url) }
        }
    }
    // KMK <--

    sealed interface State {
        data object Loading : State
        data object Empty : State
        data class Error(val error: Throwable) : State

        @Immutable
        data class Success(
            val entries: List<LovedMangaEntry>,
            val groupDuplicates: Boolean,
            // KMK --> v0.7.2: "source|url" → groupId, populated from manga_cross_source_link
            val linkGroupByKey: Map<String, String> = emptyMap(),
            // KMK <--
            // KMK --> v0.7.14: active sort mode; RECENT matches the load-time sort order
            val sortMode: LoveSortMode = LoveSortMode.RECENT,
            // KMK <--
            // KMK --> v0.8.0
            val primaryByGroupId: Map<String, RatedMangaKey> = emptyMap(),
            val selectionMode: Boolean = false,
            val selectedKeys: Set<RatedMangaKey> = emptySet(),
            // KMK <--
        ) : State {
            // KMK --> v0.8.0
            fun toSelection(): RatedSelectionReducer.Selection =
                RatedSelectionReducer.Selection(selectionMode, selectedKeys)

            fun withSelection(selection: RatedSelectionReducer.Selection): Success =
                copy(selectionMode = selection.selectionMode, selectedKeys = selection.selectedKeys)
            // KMK <--

            val displayItems: List<LovedDisplayItem>
                // KMK --> v0.7.14: sort before grouping
                get() {
                    val sorted = sortEntries(entries, sortMode)
                    return if (groupDuplicates) {
                        buildGroupedItems(sorted, linkGroupByKey, primaryByGroupId)
                    } else {
                        buildFlatItems(sorted, linkGroupByKey)
                    }
                }
            // KMK <--
        }
    }
}

// KMK --> v0.7.14: sort entries according to the chosen sort mode
private fun sortEntries(entries: List<LovedMangaEntry>, mode: LoveSortMode): List<LovedMangaEntry> = when (mode) {
    LoveSortMode.RECENT -> entries // already sorted desc by updatedAt at load time
    LoveSortMode.OLDEST -> entries.reversed()
    LoveSortMode.TITLE_AZ -> entries.sortedBy { (it.manga?.title ?: it.taste.title).lowercase() }
    LoveSortMode.SOURCE -> entries.sortedBy { it.taste.source }
}
// KMK <--

// KMK --> v0.8.0: flat display still exposes confirmed-group transparency (for the item menu / select
// actions) even though it never shows the version-count badge — that badge remains grouped-display-only,
// unchanged behavior from before this pass.
internal fun buildFlatItems(
    entries: List<LovedMangaEntry>,
    linkGroupByKey: Map<String, String>,
): List<LovedDisplayItem> {
    val groupSizeById = entries
        .mapNotNull { linkGroupByKey["${it.taste.source}|${it.taste.url}"] }
        .groupingBy { it }
        .eachCount()
    val membersByGroupId = entries.groupBy { linkGroupByKey["${it.taste.source}|${it.taste.url}"] }
    return entries.map { entry ->
        val groupId = linkGroupByKey["${entry.taste.source}|${entry.taste.url}"]
        val memberKeys = groupId?.let { gid -> membersByGroupId[gid]?.map { RatedMangaKey.of(it.taste) } }.orEmpty()
        LovedDisplayItem(
            taste = entry.taste,
            manga = entry.manga,
            versionCount = 1,
            confirmedGroupId = groupId,
            memberKeys = memberKeys,
            hasConfirmedGroup = groupId != null && (groupSizeById[groupId] ?: 0) >= 2,
        )
    }
}
// KMK <--

// KMK --> v0.7.2: pass author, artist, and linkGroupId into the grouper
internal fun buildGroupedItems(
    entries: List<LovedMangaEntry>,
    linkGroupByKey: Map<String, String>,
    // KMK --> v0.8.0
    primaryByGroupId: Map<String, RatedMangaKey>,
    // KMK <--
): List<LovedDisplayItem> {
    val inputs = entries.map { entry ->
        val key = "${entry.taste.source}|${entry.taste.url}"
        LovedMangaDuplicateGrouper.GroupInput(
            key = key,
            source = entry.taste.source,
            url = entry.taste.url,
            title = entry.manga?.title ?: entry.taste.title,
            description = entry.manga?.description.orEmpty(),
            author = entry.manga?.author,
            artist = entry.manga?.artist,
            linkGroupId = linkGroupByKey[key],
        )
    }
    val keyToEntry = entries.associateBy { "${it.taste.source}|${it.taste.url}" }
    return LovedMangaDuplicateGrouper.computeGroups(inputs).mapNotNull { group ->
        // KMK --> v0.8.0: a "confirmed group" for menu/actions purposes is specifically a
        // LINK_GROUP-reason group (user-verified identity), not a metadata-similarity grouping.
        val isConfirmedLinkGroup = group.reason == LovedMangaDuplicateGrouper.LovedMangaGroupReason.LINK_GROUP
        val confirmedGroupId = if (isConfirmedLinkGroup) linkGroupByKey[group.primaryKey] else null

        // Stored primary wins when it's installed/visible (i.e. present among this group's loaded
        // members) — otherwise fall back to the grouper's own primary-key choice (RatedGroupPrimaryResolver).
        val storedPrimary = confirmedGroupId?.let { primaryByGroupId[it] }
        val effectiveKey = RatedGroupPrimaryResolver.resolve(group.primaryKey, group.memberKeys, storedPrimary)
        val primary = keyToEntry[effectiveKey] ?: keyToEntry[group.primaryKey] ?: return@mapNotNull null
        // KMK <--
        LovedDisplayItem(
            taste = primary.taste,
            manga = primary.manga,
            versionCount = group.versionCount,
            confirmedGroupId = confirmedGroupId,
            memberKeys = group.memberKeys.mapNotNull { keyToEntry[it]?.let { e -> RatedMangaKey.of(e.taste) } },
            hasConfirmedGroup = isConfirmedLinkGroup && group.versionCount >= 2,
        )
    }
}
// KMK <--
