package exh.recs.loved

// KMK -->
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.export.SafArtifactOutcome
import eu.kanade.tachiyomi.util.export.SafExportCoordinator
import eu.kanade.tachiyomi.util.system.toast
import exh.recs.BulkTasteOutcome
import exh.recs.KmkRecsReleaseNotes
import exh.recs.matching.ConfirmedGroupLocalTrackingPropagator
import exh.recs.matching.ConfirmedMangaGroupTargets
import exh.recs.matching.ConfirmedTrackedMangaTasteTargets
import exh.recs.matching.CrossSourceIdentityAuthorizationResolver
import exh.recs.share.RecommendationBundleExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.SetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
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
    private val getCrossSourceIdentityDecisions: GetCrossSourceIdentityDecisions = Injekt.get(),
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
    private val setMangaTasteBatch: SetMangaTasteBatch = Injekt.get(),
    private val clearMangaTaste: ClearMangaTaste = Injekt.get(),
    private val upsertCrossSourceMangaLinks: UpsertCrossSourceMangaLinks = Injekt.get(),
    private val deleteCrossSourceMangaLink: DeleteCrossSourceMangaLink = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
    // KMK v0.8.20: atomic ungroup + group-action Undo Journal
    private val deleteCrossSourceGroupCompletely: tachiyomi.domain.taste.interactor.DeleteCrossSourceGroupCompletely = Injekt.get(),
    private val groupUndoService: exh.util.GroupUndoService = exh.util.GroupUndoService(),
    private val identityController: exh.recs.matching.CrossSourceIdentityDecisionController =
        exh.recs.matching.CrossSourceIdentityDecisionController(),
    private val confirmedTrackedMangaTasteTargets: ConfirmedTrackedMangaTasteTargets = Injekt.get(),
    private val confirmedMangaGroupTargets: ConfirmedMangaGroupTargets = Injekt.get(),
    private val confirmedGroupLocalTrackingPropagator: ConfirmedGroupLocalTrackingPropagator =
        Injekt.get(),
    // KMK <--
) : StateScreenModel<LovedMangaScreenModel.State>(State.Loading) {

    // screenModelScope-owned, not Composable-`remember`-owned -- survives recomposition of
    // RatedMangaScreen/RatedMangaCollectionContent for as long as this screen model stays alive.
    val exportCoordinator = SafExportCoordinator()

    init {
        // KMK --> v0.7.29: subscribe to live updates so the screen reacts to taste changes without manual refresh
        screenModelScope.launch {
            getMangaTaste.subscribeAll().collectLatest { allTastes -> load(allTastes) }
        }
        // KMK <--
    }

    // [snapshot] is
    // captured by the caller (RatedMangaCollectionContent) at the moment the export is requested,
    // before the picker opens -- never re-read from live state here.
    fun exportRatedManga(context: Context, operationId: String, uri: Uri, snapshot: State.Success?): Boolean {
        if (!exportCoordinator.registerUri(operationId, uri)) return false
        screenModelScope.launch {
            val outcome = exportCoordinator.performWrite(operationId) {
                if (snapshot == null || snapshot.displayItems.isEmpty()) {
                    SafArtifactOutcome.PARTIAL_OR_EMPTY
                } else {
                    val exporter = RecommendationBundleExporter()
                    val bundle = exporter.buildLovedMangaBundle(
                        displayItems = snapshot.displayItems,
                        linkGroupByKey = snapshot.linkGroupByKey,
                        kmkVersion = KmkRecsReleaseNotes.VERSION_NAME,
                    )
                    exporter.writeToUri(context, uri, bundle).fold(
                        onSuccess = { SafArtifactOutcome.SUCCESS },
                        onFailure = { SafArtifactOutcome.FAILED },
                    )
                }
            }
            withUIContext {
                when (outcome) {
                    SafArtifactOutcome.SUCCESS -> context.toast(KMR.strings.rec_bundle_export_success)
                    SafArtifactOutcome.FAILED -> context.toast(KMR.strings.rec_bundle_export_failure)
                    SafArtifactOutcome.PARTIAL_OR_EMPTY -> context.toast(KMR.strings.rec_bundle_export_empty)
                    SafArtifactOutcome.CANCELLED -> Unit
                    SafArtifactOutcome.IN_PROGRESS -> Unit
                    SafArtifactOutcome.UNRESOLVED -> Unit
                }
            }
        }
        return true
    }

    private suspend fun load(allTastes: List<MangaTaste>) {
        try {
            // KMK --> v0.7.3: fail-safe source id lookup; empty set → hides all rather than showing uninstalled entries
            // sourceManager.getVisibleSources() is a synchronous, in-memory lookup, not a suspend call.
            val installedSourceIds: Set<Long> = try {
                sourceManager.getVisibleSources().map { it.id }.toSet()
            } catch (e: Exception) {
                emptySet()
            }
            // KMK <--

            // KMK --> v0.7.37: load link groups before rating filter; needed for exclusivity resolution and display grouping
            val links = try {
                getCrossSourceMangaLinks.awaitAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val linkGroupByKey = links.associate { "${it.source}|${it.url}" to it.groupId }
            val confirmedLinkGroupByKey = try {
                CrossSourceIdentityAuthorizationResolver.confirmedLinkGroupByKey(
                    links,
                    getCrossSourceIdentityDecisions.awaitAll(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
            // KMK <--

            // KMK --> v0.8.0: stored primary versions, fail-safe (empty map falls back to grouper choice)
            val primaryByGroupId: Map<String, RatedMangaKey> = try {
                getCrossSourceGroupPrimary.awaitAll()
                    .associate { it.groupId to RatedMangaKey(it.source, it.url) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
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
                    confirmedLinkGroupByKey = confirmedLinkGroupByKey,
                    primaryByGroupId = primaryByGroupId,
                    selectionMode = selectionMode,
                    selectedKeys = selectedKeys,
                )
            }
            // KMK <--
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
    /**
     * AUG-06: [changeSelectedRating] self-launches (it is not `suspend`) and only clears the
     * selection at the very end, so re-invoking it before the previous batch finishes -- e.g. the
     * user reopens `RatedMangaChangeRatingDialog` and picks another rating while the first batch
     * is still writing -- would launch a second concurrent batch over the same selected keys and
     * could double-commit ratings/journal entries. This mirrors [MangaScreenModel.runTasteAction]
     * and [ReaderViewModel]'s `ChapterCompletionRatingActionGate`: a third independently-motivated
     * confirmation of the "single shared State-level in-flight boolean" pattern for a
     * single-target-at-a-time surface (here, one batch operation at a time, not per-item).
     */
    fun changeSelectedRating(rating: MangaRating) {
        val current = mutableState.value as? State.Success ?: return
        if (current.isBulkRatingActionInProgress) return
        val targets = current.entries.filter { RatedMangaKey.of(it.taste) in current.selectedKeys }
        if (targets.isEmpty()) return
        val ratingPropagationEnabled = sourcePreferences.confirmedTrackedVersionRatingPropagationEnabled().get()
        // KMK v0.8.19: build pre-write entries for the local Action History journal; each entry is
        // committed only after its corresponding write succeeds.
        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChangeFromTaste(
            targets.map { it.taste },
            rating.value,
            when (rating) {
                MangaRating.LOVE -> exh.util.EvaluationJournalActionType.RATE_LOVE
                MangaRating.LIKE -> exh.util.EvaluationJournalActionType.RATE_LIKE
                MangaRating.DISLIKE -> exh.util.EvaluationJournalActionType.RATE_DISLIKE
                MangaRating.NOT_INTERESTED -> exh.util.EvaluationJournalActionType.NOT_INTERESTED
            },
        )
        // KMK v0.8.21-fix4: R1 correction -- this used to also remove/restore keys in the legacy
        // seenRecommendationMangaKeys preference per entry, coordinating a rollback across two
        // stores for a preference nothing reads live anymore (Not Interested exclusion is
        // MangaTaste-derived everywhere now). MangaTaste is the sole rating-family authority; the
        // legacy preference is migration/old-backup-restore-only.
        (mutableState.value as? State.Success)?.let { mutableState.value = it.copy(isBulkRatingActionInProgress = true) }
        screenModelScope.launch {
            try {
                val ratingMangas = if (ratingPropagationEnabled) {
                    targets.flatMap { entry ->
                        entry.manga?.let { confirmedTrackedMangaTasteTargets.await(it) } ?: emptyList()
                    }.distinctBy { it.source to it.url }
                } else {
                    emptyList()
                }
                if (ratingPropagationEnabled && ratingMangas.isNotEmpty()) {
                    val expandedJournal = exh.util.EvaluationModeJournalRecorder.buildRatingChange(
                        getMangaTaste,
                        ratingMangas,
                        rating.value,
                        when (rating) {
                            MangaRating.LOVE -> exh.util.EvaluationJournalActionType.RATE_LOVE
                            MangaRating.LIKE -> exh.util.EvaluationJournalActionType.RATE_LIKE
                            MangaRating.DISLIKE -> exh.util.EvaluationJournalActionType.RATE_DISLIKE
                            MangaRating.NOT_INTERESTED -> exh.util.EvaluationJournalActionType.NOT_INTERESTED
                        },
                    )
                    setMangaTasteBatch.await(ratingMangas, rating)
                    exh.util.EvaluationModeJournalRecorder.commit(expandedJournal)
                    targets.forEachIndexed { index, entry ->
                        if (entry.manga == null) {
                            setMangaTaste.await(
                                mangaId = entry.taste.mangaId,
                                source = entry.taste.source,
                                url = entry.taste.url,
                                title = entry.taste.title,
                                rating = rating,
                            )
                            journalEntries.getOrNull(index)?.let {
                                exh.util.EvaluationModeJournalRecorder.commit(listOf(it))
                            }
                        }
                    }
                } else {
                    targets.forEachIndexed { index, entry ->
                        try {
                            setMangaTaste.await(
                                mangaId = entry.taste.mangaId,
                                source = entry.taste.source,
                                url = entry.taste.url,
                                title = entry.manga?.title ?: entry.taste.title,
                                rating = rating,
                            )
                            journalEntries.getOrNull(index)?.let { exh.util.EvaluationModeJournalRecorder.commit(listOf(it)) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Leave this entry's journal entry uncommitted; the write itself did not
                            // succeed, so there is nothing to allow undoing for this entry.
                        }
                    }
                }
                propagateConfirmedLocalTracking(targets.mapNotNull { it.manga })
                clearSelection()
            } finally {
                (mutableState.value as? State.Success)?.let { mutableState.value = it.copy(isBulkRatingActionInProgress = false) }
            }
        }
    }

    private suspend fun propagateConfirmedLocalTracking(manga: List<Manga>) {
        if (!sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().get()) return
        manga.distinctBy { it.source to it.url }.forEach { origin ->
            confirmedGroupLocalTrackingPropagator.propagateIfAnyTracked(
                confirmedMangaGroupTargets.await(origin),
            )
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
        // KMK v0.8.19: build pre-write entries for the local Action History journal; each entry is
        // committed only after its corresponding clear succeeds.
        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChangeFromTaste(
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
     * Marks the selection Not Interested -- writes `MangaTaste.rating = NOT_INTERESTED` through
     * the same [setMangaTaste] every other rating change on this screen model uses. Returns the
     * pre-action [MangaTaste] snapshot for each successfully-changed entry so the caller can Undo
     * via [restoreRatings] -- the same pattern [clearSelectedRatings] already uses, rather than a
     * separate bespoke undo path.
     *
     * KMK v0.8.21-fix3: R1 correction -- this previously wrote ONLY the legacy
     * seenRecommendationMangaKeys preference and never touched MangaTaste at all, a dangling
     * writer the corrected architecture cannot allow. Because it now genuinely overwrites the
     * manga's prior rating (LOVE/LIKE/DISLIKE) with NOT_INTERESTED instead of leaving it
     * untouched, Undo must restore that exact prior rating, not merely clear to unrated -- hence
     * returning the pre-action snapshot instead of the old key-only "just remove the seen-key"
     * inverse, which would have silently left the manga unrated rather than restored.
     */
    suspend fun markSelectedNotInterested(): Pair<BulkTasteOutcome, List<MangaTaste>> {
        val current = mutableState.value as? State.Success ?: return BulkTasteOutcome(0, 0, 0) to emptyList()
        val targets = current.selectedKeys
        if (targets.isEmpty()) return BulkTasteOutcome(0, 0, 0) to emptyList()
        val targetEntries = current.entries.filter { RatedMangaKey.of(it.taste) in targets }
        val journalEntries = exh.util.EvaluationModeJournalRecorder.buildRatingChangeFromTaste(
            targetEntries.map { it.taste },
            MangaRating.NOT_INTERESTED.value,
            exh.util.EvaluationJournalActionType.NOT_INTERESTED,
        )
        val restoredEntries = mutableListOf<MangaTaste>()
        var failureCount = 0
        targetEntries.forEachIndexed { index, entry ->
            try {
                setMangaTaste.await(
                    mangaId = entry.taste.mangaId,
                    source = entry.taste.source,
                    url = entry.taste.url,
                    title = entry.manga?.title ?: entry.taste.title,
                    rating = MangaRating.NOT_INTERESTED,
                )
                restoredEntries += entry.taste
                journalEntries.getOrNull(index)?.let { exh.util.EvaluationModeJournalRecorder.commit(listOf(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureCount++
            }
        }
        clearSelection()
        val outcome = BulkTasteOutcome(requestedCount = targets.size, successCount = restoredEntries.size, failureCount = failureCount)
        return outcome to restoredEntries
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

    // KMK v0.8.21-fix3: R1 correction -- the bespoke undoMarkNotInterested (which only ever removed
    // a legacy preference key) is removed; markSelectedNotInterested's Undo now goes through
    // restoreRatings(), the same real-snapshot-restore every other rating-change Undo on this
    // screen model already uses.

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
                val identityAnchor = selected.first().key
                val identityResult = identityController.mutateAll(
                    selected.drop(1).map { candidate ->
                        tachiyomi.domain.taste.model.CrossSourceIdentityDecisionPolicy.canonicalPair(
                            tachiyomi.domain.taste.model.CrossSourceRecordKey(identityAnchor.source, identityAnchor.url),
                            tachiyomi.domain.taste.model.CrossSourceRecordKey(candidate.key.source, candidate.key.url),
                        )
                    },
                    exh.recs.matching.CrossSourceIdentityMutation.CONFIRM,
                )
                check(
                    identityResult != exh.recs.matching.CrossSourceIdentityMutationResult.CONFLICT &&
                        identityResult != exh.recs.matching.CrossSourceIdentityMutationResult.FAILED,
                )
                upsertCrossSourceMangaLinks.await(plan.writes)
                undoEntry?.let { exh.util.GroupUndoJournal.record(it) }
                plan to undoEntry?.id
            }
            result.onSuccess { (plan, undoEntryId) ->
                applyLinkWrites(plan.writes, selectedEntries.mapTo(mutableSetOf()) { RatedMangaKey.of(it.taste) })
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
    private fun applyLinkWrites(
        writes: List<tachiyomi.domain.taste.model.CrossSourceMangaLink>,
        explicitlyConfirmed: Set<RatedMangaKey>,
    ) {
        if (writes.isEmpty()) return
        val current = mutableState.value as? State.Success ?: return
        val confirmedWrites = writes.filter { RatedMangaKey(it.source, it.url) in explicitlyConfirmed }
        mutableState.value = current.copy(
            linkGroupByKey = mergeLinkWritesIntoMap(current.linkGroupByKey, writes),
            confirmedLinkGroupByKey = mergeLinkWritesIntoMap(current.confirmedLinkGroupByKey, confirmedWrites),
        )
    }

    /**
     * Removes the current selection from their current confirmed group(s). Does not clear ratings.
     * @param onResult receives the committed [exh.util.GroupJournalEntry] (for an Undo action) or
     * `null` if nothing was removed. The journal is local and available to ordinary users; its
     * developer-only details remain separately gated.
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
                val previous = try {
                    getCrossSourceMangaLinks.awaitBySourceUrl(key.source, key.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val deleted = try {
                    deleteCrossSourceMangaLink.awaitBySourceUrl(key.source, key.url)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
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
     * `null` if the group was already empty or the delete failed. The local journal is not gated by
     * Evaluation Mode.
     */
    fun ungroup(groupId: String, onResult: (exh.util.GroupJournalEntry?) -> Unit = {}) {
        screenModelScope.launch {
            // KMK v0.8.20: snapshot the complete pre-ungroup state before the atomic delete, and only
            // commit the group-undo entry after that delete actually succeeds.
            val previousLinks = try {
                getCrossSourceMangaLinks.awaitByGroupId(groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val previousPrimary = try {
                getCrossSourceGroupPrimary.awaitByGroupId(groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
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
            val confirmedLinkGroupByKey: Map<String, String> = emptyMap(),
            // KMK <--
            // KMK --> v0.7.14: active sort mode; RECENT matches the load-time sort order
            val sortMode: LoveSortMode = LoveSortMode.RECENT,
            // KMK <--
            // KMK --> v0.8.0
            val primaryByGroupId: Map<String, RatedMangaKey> = emptyMap(),
            val selectionMode: Boolean = false,
            val selectedKeys: Set<RatedMangaKey> = emptySet(),
            /** AUG-06: fences [changeSelectedRating] against re-invocation while a batch is in flight. */
            val isBulkRatingActionInProgress: Boolean = false,
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
                        buildGroupedItems(sorted, confirmedLinkGroupByKey, primaryByGroupId)
                    } else {
                        buildFlatItems(sorted, confirmedLinkGroupByKey)
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
    LoveSortMode.TITLE_AZ -> entries.sortedBy { (it.manga?.title ?: it.taste.title).lowercase(Locale.ROOT) }
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
