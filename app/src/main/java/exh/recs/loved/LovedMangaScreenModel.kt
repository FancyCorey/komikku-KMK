package exh.recs.loved

// KMK -->
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.BulkTasteOutcome
import exh.recs.SeenMangaKey
import exh.recs.SeenRecommendationMangaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
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
    private val setMangaTaste: SetMangaTaste = Injekt.get(),
    private val clearMangaTaste: ClearMangaTaste = Injekt.get(),
    private val upsertCrossSourceMangaLinks: UpsertCrossSourceMangaLinks = Injekt.get(),
    private val deleteCrossSourceMangaLink: DeleteCrossSourceMangaLink = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
    // KMK v0.8.20: atomic ungroup + group-action Undo Journal
    private val deleteCrossSourceGroupCompletely: tachiyomi.domain.taste.interactor.DeleteCrossSourceGroupCompletely = Injekt.get(),
    private val groupUndoService: exh.util.GroupUndoService = exh.util.GroupUndoService(),
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
        // KMK v0.8.19: build pre-write journal entries for Evaluation Mode's Undo Journal (no-op if
        // disabled); each entry is committed only after its corresponding write succeeds.
        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChangeFromTaste(
            sourcePreferences,
            targets.map { it.taste },
            rating.value,
            when (rating) {
                MangaRating.LOVE -> exh.util.EvaluationJournalActionType.RATE_LOVE
                MangaRating.LIKE -> exh.util.EvaluationJournalActionType.RATE_LIKE
                MangaRating.DISLIKE -> exh.util.EvaluationJournalActionType.RATE_DISLIKE
            },
        )
        screenModelScope.launch {
            targets.forEachIndexed { index, entry ->
                runCatching {
                    setMangaTaste.await(
                        mangaId = entry.taste.mangaId,
                        source = entry.taste.source,
                        url = entry.taste.url,
                        title = entry.manga?.title ?: entry.taste.title,
                        rating = rating,
                    )
                }.onSuccess {
                    journalEntries.getOrNull(index)?.let { exh.util.EvaluationModeJournalRecorder.commit(listOf(it)) }
                }
            }
            clearSelection()
        }
    }

    /**
     * Clears ratings for the current selection. Does not touch cross-source link rows.
     *
     * KMK v0.8.19: now `suspend` and returns a real [BulkTasteOutcome] instead of firing-and-
     * forgetting inside its own `screenModelScope.launch` -- each item's [clearMangaTaste] call was
     * previously wrapped in a per-item `runCatching` whose result was discarded, so the caller (the
     * confirm-dialog's `onConfirm` in `RatedMangaScreen.kt`) always showed the "cleared" Snackbar
     * regardless of whether any write actually succeeded. The caller now awaits this and only shows
     * success/Undo for items that were durably cleared.
     */
    suspend fun clearSelectedRatings(): Pair<BulkTasteOutcome, List<MangaTaste>> {
        val current = mutableState.value as? State.Success ?: return BulkTasteOutcome(0, 0, 0) to emptyList()
        val targets = current.entries.filter { RatedMangaKey.of(it.taste) in current.selectedKeys }
        if (targets.isEmpty()) return BulkTasteOutcome(0, 0, 0) to emptyList()
        // KMK v0.8.19: build pre-write journal entries for Evaluation Mode's Undo Journal (no-op if
        // disabled); each entry is committed only after its corresponding clear succeeds.
        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChangeFromTaste(
            sourcePreferences,
            targets.map { it.taste },
            null,
            exh.util.EvaluationJournalActionType.CLEAR_RATING,
        )
        // KMK v0.8.19: track exactly which entries were durably cleared (not just a count) so the
        // caller's Undo restores precisely the items that actually changed -- a count-only result
        // cannot distinguish "the first 3 succeeded" from "the last 3 succeeded" when a partial
        // failure happens.
        val clearedEntries = mutableListOf<MangaTaste>()
        var failureCount = 0
        targets.forEachIndexed { index, entry ->
            try {
                clearMangaTaste.await(entry.taste.mangaId)
                clearedEntries += entry.taste
                journalEntries.getOrNull(index)?.let { exh.util.EvaluationModeJournalRecorder.commit(listOf(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureCount++
            }
        }
        clearSelection()
        val outcome = BulkTasteOutcome(requestedCount = targets.size, successCount = clearedEntries.size, failureCount = failureCount)
        return outcome to clearedEntries
    }

    /**
     * Mild negative signal — same "not interested" store For You/manga-detail already use.
     *
     * KMK v0.8.19: now `suspend` and returns a real [BulkTasteOutcome]. The preference write is a
     * single atomic `set()` call (not per-item), so a failure is all-or-nothing for the whole
     * selection -- reported honestly as such (all-failed, never partial) rather than guessed.
     */
    suspend fun markSelectedNotInterested(): BulkTasteOutcome {
        val current = mutableState.value as? State.Success ?: return BulkTasteOutcome(0, 0, 0)
        val targets = current.selectedKeys
        if (targets.isEmpty()) return BulkTasteOutcome(0, 0, 0)
        // KMK v0.8.19: build pre-write journal entries for Evaluation Mode's Undo Journal (no-op if
        // disabled), commit only after the write below succeeds.
        val targetEntries = current.entries.filter { RatedMangaKey.of(it.taste) in targets }
        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildNotInterestedFromTaste(
            sourcePreferences,
            targetEntries.map { it.taste },
            targetEntries.map { it.taste.source to it.taste.url },
        )
        val outcome = try {
            val raw = sourcePreferences.seenRecommendationMangaKeys().get()
            var seen = SeenRecommendationMangaStore.parse(raw)
            targets.forEach { key ->
                seen = SeenRecommendationMangaStore.add(seen, SeenMangaKey(key.source, key.url))
            }
            sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(seen))
            exh.util.EvaluationModeJournalRecorder.commit(journalEntries)
            BulkTasteOutcome.success(targets.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BulkTasteOutcome.failed(targets.size)
        }
        clearSelection()
        return outcome
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
     *
     * KMK v0.8.19: now `suspend` and returns [BulkTasteOutcome] so the caller can report an honest
     * "Undo partially failed" message instead of silently discarding per-item restore failures.
     */
    suspend fun restoreRatings(snapshot: List<MangaTaste>): BulkTasteOutcome {
        if (snapshot.isEmpty()) return BulkTasteOutcome(0, 0, 0)
        var successCount = 0
        var failureCount = 0
        snapshot.forEach { taste ->
            try {
                setMangaTaste.await(
                    mangaId = taste.mangaId,
                    source = taste.source,
                    url = taste.url,
                    title = taste.title,
                    rating = MangaRating.fromValue(taste.rating) ?: return@forEach,
                )
                successCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureCount++
            }
        }
        return BulkTasteOutcome(requestedCount = snapshot.size, successCount = successCount, failureCount = failureCount)
    }

    /**
     * Undo for [markSelectedNotInterested]: removes exactly the keys that were just added, leaving
     * any other "not interested" entries the user had before untouched.
     *
     * KMK v0.8.19: now `suspend` and returns [BulkTasteOutcome] (single atomic preference write, so
     * all-or-nothing like [markSelectedNotInterested] itself).
     */
    suspend fun undoMarkNotInterested(keys: Set<RatedMangaKey>): BulkTasteOutcome {
        if (keys.isEmpty()) return BulkTasteOutcome(0, 0, 0)
        return try {
            val raw = sourcePreferences.seenRecommendationMangaKeys().get()
            var seen = SeenRecommendationMangaStore.parse(raw)
            keys.forEach { key ->
                seen = SeenRecommendationMangaStore.remove(seen, SeenMangaKey(key.source, key.url))
            }
            sourcePreferences.seenRecommendationMangaKeys().set(SeenRecommendationMangaStore.serialize(seen))
            BulkTasteOutcome.success(keys.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BulkTasteOutcome.failed(keys.size)
        }
    }
    // KMK <--

    // KMK --> v0.8.0: group actions
    // KMK v0.8.11: root cause of the reported "Group doesn't actually group the selection" bug --
    // `load()` only reloads reactively from `getMangaTaste.subscribeAll()`, which fires on
    // manga_taste changes. Group actions below only ever write to the cross-source-link table
    // (`upsertCrossSourceMangaLinks`/`deleteCrossSourceMangaLink`), which is a plain suspend
    // interactor with no Flow -- so nothing told the screen to reload after a merge/remove/ungroup,
    // and the display kept showing the pre-action grouping until an unrelated taste change (e.g.
    // rating another version, exactly the reported workaround) happened to re-trigger `load()`.
    // Every group action below now updates `linkGroupByKey`/`primaryByGroupId` directly in state
    // immediately after a successful write, instead of waiting for that unrelated reload.
    /**
     * Merges the current selection into one group. Requires >= 2 selected entries — returns a
     * [MergeResult.TooFewSelected] otherwise (the UI already gates the confirm dialog on this, but
     * the result type still covers it so a caller can never observe a silent no-op). Never merges by
     * title — see [RatedGroupMergePlanner]. If the selection spans multiple existing groups, every
     * member of every non-target group is folded into the target group (a genuine merge).
     */
    fun mergeSelectedIntoGroup(onResult: (MergeResult) -> Unit = {}) {
        val current = mutableState.value as? State.Success ?: return onResult(MergeResult.TooFewSelected)
        val selectedEntries = current.entries.filter { RatedMangaKey.of(it.taste) in current.selectedKeys }
        if (selectedEntries.size < 2) {
            onResult(MergeResult.TooFewSelected)
            return
        }
        screenModelScope.launch {
            val result = runCatching {
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
                    ?: error("RatedGroupMergePlanner.plan() returned null for ${selected.size} selected entries")
                // KMK v0.8.20: build the not-yet-committed group-undo entry BEFORE the write (so it
                // captures the correct pre-merge state), but only record it into the journal after the
                // write below actually succeeds -- see GroupUndoRecorder's class doc.
                val undoEntry = exh.util.GroupUndoRecorder.buildMergeEntry(sourcePreferences, plan, existingGroupMembers)
                upsertCrossSourceMangaLinks.await(plan.writes)
                undoEntry?.let { exh.util.GroupUndoJournal.record(it) }
                plan to undoEntry?.id
            }
            result.onSuccess { (plan, undoEntryId) ->
                applyLinkWrites(plan.writes)
                onResult(MergeResult.Success(undoEntryId))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                onResult(MergeResult.Failed)
            }
            clearSelection()
        }
    }

    sealed interface MergeResult {
        data class Success(val undoEntryId: String?) : MergeResult
        data object TooFewSelected : MergeResult
        data object Failed : MergeResult
    }

    /** Undoes the most recent reversible group action (merge / remove-from-group / ungroup), if any. */
    fun undoLastGroupAction(entryId: String, onResult: (exh.util.GroupUndoOutcome) -> Unit = {}) {
        screenModelScope.launch {
            val outcome = groupUndoService.undo(entryId)
            onResult(outcome)
        }
    }

    /**
     * Merges the freshly-written [writes] into the in-memory `linkGroupByKey` immediately, so the
     * grouped display reflects a merge/split without waiting for an unrelated reactive reload — see
     * the class-level note above `mergeSelectedIntoGroup()`.
     */
    private fun applyLinkWrites(writes: List<tachiyomi.domain.taste.model.CrossSourceMangaLink>) {
        if (writes.isEmpty()) return
        val current = mutableState.value as? State.Success ?: return
        mutableState.value = current.copy(linkGroupByKey = mergeLinkWritesIntoMap(current.linkGroupByKey, writes))
    }

    /**
     * Removes the current selection from their current confirmed group(s). Does not clear ratings.
     * @param onResult receives the committed [exh.util.GroupJournalEntry] (for an Undo action) or
     * `null` if nothing was removed or Evaluation Mode is off (no journal entry to undo).
     */
    fun removeSelectedFromGroup(onResult: (exh.util.GroupJournalEntry?) -> Unit = {}) {
        val current = mutableState.value as? State.Success ?: return
        val targets = current.selectedKeys.filter { current.linkGroupByKey.containsKey("${it.source}|${it.url}") }
        if (targets.isEmpty()) return
        screenModelScope.launch {
            // KMK v0.8.20: read each link's full pre-removal row before deleting it, and only include
            // a key in the group-undo snapshot if its delete actually succeeded -- see
            // GroupUndoRecorder's build-before/commit-after contract.
            val removed = mutableListOf<Pair<exh.util.RatedLinkKey, tachiyomi.domain.taste.model.CrossSourceMangaLink>>()
            targets.forEach { key ->
                val previous = runCatching { getCrossSourceMangaLinks.awaitBySourceUrl(key.source, key.url) }.getOrNull()
                val deleted = runCatching { deleteCrossSourceMangaLink.awaitBySourceUrl(key.source, key.url) }.isSuccess
                if (deleted && previous != null) {
                    removed += exh.util.RatedLinkKey(key.source, key.url) to previous
                }
            }
            val undoEntry = exh.util.GroupUndoRecorder.buildRemoveFromGroupEntry(sourcePreferences, removed)
            undoEntry?.let { exh.util.GroupUndoJournal.record(it) }
            // KMK v0.8.11: reflect the removal immediately -- see the class-level note above
            // mergeSelectedIntoGroup().
            val after = mutableState.value as? State.Success
            if (after != null) {
                val updated = after.linkGroupByKey.toMutableMap()
                targets.forEach { key -> updated.remove("${key.source}|${key.url}") }
                mutableState.value = after.copy(linkGroupByKey = updated)
            }
            clearSelection()
            onResult(undoEntry)
        }
    }

    /**
     * Deletes every link in [groupId] and its stored primary version, atomically. Does not clear
     * ratings.
     * @param onResult receives the committed [exh.util.GroupJournalEntry] (for an Undo action) or
     * `null` if the group was already empty, the delete failed, or Evaluation Mode is off.
     */
    fun ungroup(groupId: String, onResult: (exh.util.GroupJournalEntry?) -> Unit = {}) {
        screenModelScope.launch {
            // KMK v0.8.20: snapshot the complete pre-ungroup state before the atomic delete, and only
            // commit the group-undo entry after that delete actually succeeds.
            val previousLinks = runCatching { getCrossSourceMangaLinks.awaitByGroupId(groupId) }.getOrDefault(emptyList())
            val previousPrimary = runCatching { getCrossSourceGroupPrimary.awaitByGroupId(groupId) }.getOrNull()
            val undoEntry = try {
                deleteCrossSourceGroupCompletely.await(groupId)
                exh.util.GroupUndoRecorder.buildUngroupEntry(sourcePreferences, groupId, previousLinks, previousPrimary)
                    ?.also { exh.util.GroupUndoJournal.record(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // KMK v0.8.11: reflect the ungroup immediately -- see the class-level note above
            // mergeSelectedIntoGroup().
            val after = mutableState.value as? State.Success
            if (after != null) {
                val updated = after.linkGroupByKey.filterValues { it != groupId }
                mutableState.value = after.copy(linkGroupByKey = updated, primaryByGroupId = after.primaryByGroupId - groupId)
            }
            clearSelection()
            onResult(undoEntry)
        }
    }

    /** Sets which linked version controls the rated-list cover/title for [groupId]. */
    fun setPrimaryVersion(groupId: String, key: RatedMangaKey) {
        screenModelScope.launch {
            val previousPrimary = getCrossSourceGroupPrimary.awaitByGroupId(groupId)
            val undoEntry = exh.util.GroupUndoRecorder.buildSetPrimaryEntry(
                sourcePreferences,
                groupId,
                previousPrimary,
                exh.util.RatedLinkKey(key.source, key.url),
            )
            try {
                setCrossSourceGroupPrimary.await(groupId, key.source, key.url)
                undoEntry?.let { exh.util.GroupUndoJournal.record(it) }
            } catch (e: CancellationException) {
                throw e
            }
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

// KMK v0.8.11 -->
/**
 * Pure helper: applies freshly-written [writes] on top of [current]'s `"source|url" -> groupId`
 * map, without needing another DB round trip -- see the note above
 * [LovedMangaScreenModel.mergeSelectedIntoGroup]. Extracted top-level so it is directly unit
 * testable without an Injekt-bootstrapped `LovedMangaScreenModel`.
 */
internal fun mergeLinkWritesIntoMap(
    current: Map<String, String>,
    writes: List<tachiyomi.domain.taste.model.CrossSourceMangaLink>,
): Map<String, String> {
    if (writes.isEmpty()) return current
    val updated = current.toMutableMap()
    writes.forEach { link -> updated["${link.source}|${link.url}"] = link.groupId }
    return updated
}
// KMK <--

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
