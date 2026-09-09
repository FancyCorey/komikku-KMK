package exh.recs.settings

// KMK -->
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.Source
import exh.recs.ForYouResultBudgetPolicy
import exh.recs.GroupPreviewBudgetPolicy
import exh.recs.RecommendationEnrichmentCapPolicy
import exh.recs.RecommendationLanguageAvailabilityPolicy
import exh.recs.RecommendationSourceFilter
import exh.recs.RecommendationSourceOrdering
import exh.recs.RecommendationSourceRunStatus
import exh.recs.RecommendationSourceRunStatusStore
import exh.recs.SavedFocusMode
import exh.recs.SavedFocusModeStore
import exh.recs.SourceFitStats
import exh.recs.SourceFitStatsStore
import exh.recs.discovery.GetNonInstalledSourceSuggestions
import exh.recs.discovery.NonInstalledSourceSuggestion
import exh.recs.discovery.NonInstalledSourceSuggestionStore
import exh.recs.sourceprefs.RecommendationSourcePreference
import exh.recs.sourceprefs.RecommendationSourcePreferenceStore
import exh.util.PackageOperationKind
import exh.util.recordPackageOperationReceipt
import exh.util.recordUserInitiatedInstall
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.ClearRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.ClearTagTaste
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.interactor.GetTasteDiagnostics
import tachiyomi.domain.taste.interactor.GetTasteSuggestions
import tachiyomi.domain.taste.interactor.SetRecommendationSourceEnabled
import tachiyomi.domain.taste.interactor.SetTagTaste
import tachiyomi.domain.taste.interactor.TasteDiagnosticsResult
import tachiyomi.domain.taste.interactor.TasteSuggestionCandidate
import tachiyomi.domain.taste.interactor.TasteSuggestionResult
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
import tachiyomi.domain.taste.model.normalizeTag
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Immutable
sealed interface SourcesToTryInstallFeedback {
    val operationId: Long

    data class Installed(override val operationId: Long) : SourcesToTryInstallFeedback
    data class Failed(override val operationId: Long) : SourcesToTryInstallFeedback
    data class BulkInstalled(override val operationId: Long, val installedCount: Int) : SourcesToTryInstallFeedback
    data class BulkPartial(
        override val operationId: Long,
        val installedCount: Int,
        val failedCount: Int,
    ) : SourcesToTryInstallFeedback
    data class BulkFailed(override val operationId: Long, val failedCount: Int) : SourcesToTryInstallFeedback
    data class Cancelled(override val operationId: Long) : SourcesToTryInstallFeedback
}

internal fun shouldClearSuggestionInstallState(activeOperationId: Long?, operationId: Long): Boolean =
    activeOperationId == operationId

private data class RatingPreferenceState(
    val prompt: Boolean,
    val otherVersions: Boolean,
    val ratingPropagation: Boolean,
    val trackingPropagation: Boolean,
    val actionsUseSelection: Boolean,
)

// KMK EC-04 2026-09-04: shared across all four Recommendation Settings destination screens
// (RecommendationSourcePrioritySettingsScreen, RecommendationTasteTagsSettingsScreen,
// RecommendationNonInstalledDiscoverySettingsScreen, RecommendationDiagnosticsSettingsScreen) via
// Navigator.rememberNavigatorScreenModel instead of each screen's own rememberScreenModel. Those four
// screens previously each held an independent instance (Voyager's default rememberScreenModel key is
// per-Screen), so init's eager source scan/filter/order, language-availability computation, and six
// preference-store parses reran from scratch on every navigation between them. Navigator-scoping
// creates this ScreenModel once per enclosing Navigator and disposes it via
// NavigatorScreenModelDisposer when that Navigator itself is disposed (not per-screen pop), so state
// stays live and fresh across lateral navigation within Recommendation Settings while still being
// bounded to a single instance -- see RecommendationsSettingsScreenModelSharedInstanceSourceTest.
class RecommendationsSettingsScreenModel(
    private val getTagTaste: GetTagTaste = Injekt.get(),
    private val setTagTaste: SetTagTaste = Injekt.get(),
    private val clearTagTaste: ClearTagTaste = Injekt.get(),
    private val getDisabledSources: GetDisabledRecommendationSources = Injekt.get(),
    private val setSourceEnabled: SetRecommendationSourceEnabled = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK -->
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val getNonInstalledSourceSuggestions: GetNonInstalledSourceSuggestions = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.38: For You discovery memory reset
    private val clearMemory: ClearRecommendationCandidateMemory = Injekt.get(),
    // KMK <--
    // KMK --> v0.7.39: also clear discovery progress when user resets discovery history
    private val clearDiscoveryProgress: ClearRecommendationDiscoveryProgress = Injekt.get(),
    // KMK <--
    // KMK v0.8.10: Taste suggestions + diagnostics -- both read-only, on-demand (same pattern as
    // GetTasteProfile.await() elsewhere), deliberately not reactive Flow subscriptions since they
    // aggregate over all rated manga + genres, which would be expensive to recompute on every tag
    // preference keystroke. Reloaded explicitly: once at init, and once after any tag preference
    // change (add/edit/remove), which is exactly when a suggestion could newly qualify or newly
    // need excluding.
    private val getTasteSuggestions: GetTasteSuggestions = Injekt.get(),
    private val getTasteDiagnostics: GetTasteDiagnostics = Injekt.get(),
    private val getSourceEvaluations: GetSourceEvaluations = Injekt.get(),
    // KMK <--
    // local-only exposure history clear
    private val clearRecommendationExposure: tachiyomi.domain.taste.interactor.ClearRecommendationExposure = Injekt.get(),
) : StateScreenModel<RecommendationsSettingsScreenModel.State>(State()) {

    private val suggestionInstallJobs = mutableMapOf<String, Job>()
    private val suggestionInstallOperationIds = mutableMapOf<String, Long>()
    private var bulkSuggestionInstallJob: Job? = null
    private var bulkSuggestionInstallOperationId: Long? = null
    private var activeBulkSuggestion: NonInstalledSourceSuggestion? = null
    private var retryableSuggestions: List<NonInstalledSourceSuggestion> = emptyList()
    // The package installer can briefly report a cancelled extension as installed before its
    // rollback reaches installedExtensionsFlow. Keep the cancelled candidate visible and
    // retryable during that transient window instead of letting the list collapse to zero.
    private val installRecoverySuggestions = mutableMapOf<String, NonInstalledSourceSuggestion>()
    private var nextSuggestionInstallOperationId = 0L

    private val ratedVisibilityPref = sourcePreferences.recommendationRatedMangaVisibility()
    private val sourceOrderPref = sourcePreferences.recommendationSourceOrder()
    private val languagesPref = sourcePreferences.recommendationSourceLanguages()
    private val hideKnownMangaPref = sourcePreferences.recommendationHideKnownManga()
    // KMK --> v0.7.26
    private val minChapterCountPref = sourcePreferences.recommendationMinChapterCount()
    // KMK <--
    // KMK --> v0.7.34: enrichment cap preference
    private val enrichmentCapPref = sourcePreferences.recommendationEnrichmentCap()
    // KMK <--
    // KMK --> v0.8.2: visible-card budget per ordinary For You source row
    private val resultBudgetPref = sourcePreferences.recommendationResultBudget()
    // KMK <--
    // KMK --> v0.8.6: initial-preview budget per extension for group recommendation rows only
    private val groupPreviewBudgetPref = sourcePreferences.groupPreviewResultBudget()
    // KMK <--
    // bounded Latest exploration share
    private val latestExplorationPercentPref = sourcePreferences.recommendationLatestExplorationPercent()
    private val latestExplorationEnabledPref = sourcePreferences.recommendationLatestExplorationEnabled()
    // Exposure window
    private val exposureWindowDaysPref = sourcePreferences.recommendationExposureWindowDays()
    private val exposureWindowEnabledPref = sourcePreferences.recommendationExposureWindowEnabled()
    private val lastSourceStatusesPref = sourcePreferences.recommendationLastSourceRunStatuses()
    // KMK --> v0.7.19
    private val sourceFitStatsPref = sourcePreferences.recommendationSourceFitStats()
    // KMK <--
    // KMK v0.8.14-fix1: read-only For You preview snapshot -- see RecommendationForYouPreviewSnapshotStore.
    private val forYouPreviewSnapshotPref = sourcePreferences.recommendationForYouPreviewSnapshot()
    // KMK --> EC-04 2026-09-01: configurable discovery-effort policy
    private val discoveryEffortLevelPref = sourcePreferences.recommendationDiscoveryEffortLevel()
    private val discoveryCandidateBudgetPref = sourcePreferences.recommendationDiscoveryCandidateBudget()
    // KMK <--

    init {
        val languages = RecommendationSourceFilter.normalizeLanguages(languagesPref.get())
        // KMK --> v0.7.7 follow-up: read fresh at init time; refreshed reactively on extension changes
        val initSources = sourceManager.getVisibleSources()
        // KMK <--
        val filteredSources = RecommendationSourceFilter.filterForRecommendations(initSources, languages)
        val storedOrder = RecommendationSourceOrdering.parse(sourceOrderPref.get())
        val allInOrder = RecommendationSourceOrdering.applyAll(filteredSources, storedOrder)
        // KMK v0.8.12: merges selected + installed + available-extension languages instead of only
        // installed sources, so the chip list never collapses to just English -- see
        // RecommendationLanguageAvailabilityPolicy.
        val availableLangs = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = languages,
            installedVisibleSources = initSources,
            availableExtensions = extensionManager.availableExtensionsFlow.value,
        )
        val parsedStatuses = RecommendationSourceRunStatusStore.parse(lastSourceStatusesPref.get())
        // KMK --> v0.7.19
        val parsedFitStats = SourceFitStatsStore.parse(sourceFitStatsPref.get())
        // KMK <--
        // KMK -->
        val likedKeys = RecommendationSourcePreferenceStore.parse(sourcePreferences.likedRecommendationSourceKeys().get())
        val dislikedKeys = RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedRecommendationSourceKeys().get())
        // KMK <--
        // KMK v0.8.1-fix4: source/library-quality axis, separate from the recommendation axis above
        val qualityDislikedKeys = RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedSourceQualityKeys().get())
        val qualityExplicitKeys = RecommendationSourcePreferenceStore.parse(sourcePreferences.explicitSourceQualityKeys().get())
        mutableState.update {
            it.copy(
                orderedSources = allInOrder.toImmutableList(),
                ratedMangaVisibility = ratedVisibilityPref.get(),
                hideKnownManga = hideKnownMangaPref.get(),
                // KMK --> v0.7.26
                // resolved on read too, so a
                // value persisted by an older build (or corrupted storage) renders as a real labelled
                // option instead of an unlabelled raw number -- same treatment resultBudget and
                // groupPreviewBudget already get below.
                minChapterCount = exh.recs.RecommendationMinChapterCountPolicy.resolve(minChapterCountPref.get()),
                // KMK <--
                // KMK --> v0.7.34
                enrichmentCap = RecommendationEnrichmentCapPolicy.resolve(enrichmentCapPref.get()),
                // KMK <--
                // KMK --> EC-04 2026-09-01: resolved on read, same treatment as every value above --
                // an unknown/corrupt/blank stored value renders as the real DEFAULT (STANDARD)
                // instead of an unlabelled raw string.
                discoveryEffortLevel = exh.recs.memory.DiscoveryEffortLevel.resolve(discoveryEffortLevelPref.get()),
                discoveryCandidateBudget = exh.recs.memory.RecommendationDiscoveryCandidateBudgetPolicy.resolve(
                    discoveryCandidateBudgetPref.get(),
                ),
                // KMK <--
                // KMK --> v0.8.2
                resultBudget = ForYouResultBudgetPolicy.validate(resultBudgetPref.get()),
                groupPreviewBudget = GroupPreviewBudgetPolicy.validate(groupPreviewBudgetPref.get()),
                latestExplorationPercent = exh.recs.RecommendationLatestBudgetPolicy.validate(latestExplorationPercentPref.get()),
                latestExplorationEnabled = latestExplorationEnabledPref.get(),
                exposureWindowDays = exh.recs.RecommendationExposurePolicy.validateWindowDays(exposureWindowDaysPref.get()),
                exposureWindowEnabled = exposureWindowEnabledPref.get(),
                // KMK <--
                recommendationLanguages = languages.toImmutableSet(),
                availableLanguages = availableLangs.toImmutableList(),
                sourceStatuses = parsedStatuses.toPersistentMap(),
                // KMK --> v0.7.19
                sourceFitStats = parsedFitStats.toPersistentMap(),
                // KMK <--
                // KMK -->
                likedSourceKeys = likedKeys.toImmutableSet(),
                dislikedSourceKeys = dislikedKeys.toImmutableSet(),
                // KMK <--
                // KMK v0.8.1-fix4
                qualityDislikedSourceKeys = qualityDislikedKeys.toImmutableSet(),
                qualityExplicitSourceKeys = qualityExplicitKeys.toImmutableSet(),
                // KMK --> v0.7.8
                sameMangaResultsPerSource = exh.recs.matching.SameMangaMatchSettings.clampResultCap(
                    sourcePreferences.sameMangaMatchResultsPerSource().get(),
                ),
                sameMangaPreselectionMode = exh.recs.matching.SameMangaPreselectionMode.resolve(
                    sourcePreferences.sameMangaMatchPreselectionMode().get(),
                    sourcePreferences.sameMangaMatchPreselectResults().get(),
                ),
                chapterCompletionRatingPromptEnabled = sourcePreferences.chapterCompletionRatingPromptEnabled().get(),
                chapterCompletionRatingOtherVersionsPromptEnabled = sourcePreferences.chapterCompletionRatingOtherVersionsPromptEnabled().get(),
                confirmedTrackedVersionRatingPropagationEnabled = sourcePreferences.confirmedTrackedVersionRatingPropagationEnabled().get(),
                confirmedTrackedVersionLocalTrackingPropagationEnabled = sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().get(),
                ratedMangaActionsUseSelection = sourcePreferences.ratedMangaActionsUseSelection().get(),
                bestVersionPreviewSampleSize = exh.recs.matching.SameMangaMatchSettings.clampSampleSize(
                    sourcePreferences.bestVersionPreviewSampleSize().get(),
                ),
                bestVersionAvoidFirstPages = sourcePreferences.bestVersionAvoidFirstPages().get(),
                // KMK <--
            )
        }

        // Keep the navigator-scoped model aligned with preference changes made by another
        // settings route or restored profile. The initial emission also repairs a stale model
        // without requiring the user to leave and reopen Recommendation Settings.
        screenModelScope.launch {
            combine(
                sourcePreferences.chapterCompletionRatingPromptEnabled().changes().onStart {
                    emit(sourcePreferences.chapterCompletionRatingPromptEnabled().get())
                },
                sourcePreferences.chapterCompletionRatingOtherVersionsPromptEnabled().changes().onStart {
                    emit(sourcePreferences.chapterCompletionRatingOtherVersionsPromptEnabled().get())
                },
                sourcePreferences.confirmedTrackedVersionRatingPropagationEnabled().changes().onStart {
                    emit(sourcePreferences.confirmedTrackedVersionRatingPropagationEnabled().get())
                },
                sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().changes().onStart {
                    emit(sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled().get())
                },
                sourcePreferences.ratedMangaActionsUseSelection().changes().onStart {
                    emit(sourcePreferences.ratedMangaActionsUseSelection().get())
                },
            ) { prompt, otherVersions, ratingPropagation, trackingPropagation, actionsUseSelection ->
                RatingPreferenceState(prompt, otherVersions, ratingPropagation, trackingPropagation, actionsUseSelection)
            }.collectLatest { preferences ->
                mutableState.update {
                    it.copy(
                        chapterCompletionRatingPromptEnabled = preferences.prompt,
                        chapterCompletionRatingOtherVersionsPromptEnabled = preferences.otherVersions,
                        confirmedTrackedVersionRatingPropagationEnabled = preferences.ratingPropagation,
                        confirmedTrackedVersionLocalTrackingPropagationEnabled = preferences.trackingPropagation,
                        ratedMangaActionsUseSelection = preferences.actionsUseSelection,
                    )
                }
            }
        }

        // Live-update source statuses whenever For You finishes a run and persists new values.
        screenModelScope.launch {
            lastSourceStatusesPref.changes().collectLatest { raw ->
                val parsed = RecommendationSourceRunStatusStore.parse(raw)
                mutableState.update { it.copy(sourceStatuses = parsed.toPersistentMap()) }
            }
        }
        // KMK --> v0.7.19: live-update rolling fit stats whenever For You completes a run
        screenModelScope.launch {
            sourceFitStatsPref.changes().collectLatest { raw ->
                val parsed = SourceFitStatsStore.parse(raw)
                mutableState.update { it.copy(sourceFitStats = parsed.toPersistentMap()) }
            }
        }
        // KMK <--

        // KMK v0.8.14-fix1: read-only For You preview snapshot, live-updated whenever a For You run
        // finishes and persists a new snapshot -- see RecommendationForYouPreviewSnapshotStore.
        mutableState.update { it.copy(forYouPreviewSnapshot = RecommendationForYouPreviewSnapshotStore.parse(forYouPreviewSnapshotPref.get())) }
        screenModelScope.launch {
            forYouPreviewSnapshotPref.changes().collectLatest { raw ->
                mutableState.update { it.copy(forYouPreviewSnapshot = RecommendationForYouPreviewSnapshotStore.parse(raw)) }
            }
        }

        // KMK --> v0.7.0: Phase 1 – track dismissed suggestion count
        val dismissedPref = sourcePreferences.dismissedNonInstalledRecommendationSources()
        mutableState.update { it.copy(dismissedSuggestionCount = NonInstalledSourceSuggestionStore.parse(dismissedPref.get()).size) }
        screenModelScope.launch {
            dismissedPref.changes().collectLatest { raw ->
                mutableState.update { it.copy(dismissedSuggestionCount = NonInstalledSourceSuggestionStore.parse(raw).size) }
            }
        }
        // KMK <--

        // KMK -->
        screenModelScope.launch {
            getNonInstalledSourceSuggestions.subscribe().collectLatest { suggestions ->
                val mergedSuggestions = (suggestions + installRecoverySuggestions.values)
                    .distinctBy { it.dismissalKey }
                val currentKeys = mergedSuggestions.mapTo(mutableSetOf()) { it.dismissalKey }
                retryableSuggestions = retryableSuggestions.filter { it.dismissalKey in currentKeys }
                mutableState.update {
                    it.copy(
                        nonInstalledSuggestions = mergedSuggestions.toImmutableList(),
                        retryableSuggestionInstallFailureCount = retryableSuggestions.size,
                    )
                }
            }
        }

        // KMK v0.8.20-fix1: expose the existing per-source evaluation counters as read-only
        // diagnostics. The settings screen never performs a source request here; it only observes
        // the persisted evaluation rows and refreshes when a new evaluation is written.
        screenModelScope.launch {
            getSourceEvaluations.subscribeAll().collectLatest { evaluations ->
                mutableState.update { it.copy(sourceMetadataTagDiagnostics = evaluations.toImmutableList()) }
            }
        }

        screenModelScope.launch {
            combine(
                sourcePreferences.likedRecommendationSourceKeys().changes(),
                sourcePreferences.dislikedRecommendationSourceKeys().changes(),
            ) { likedRaw, dislikedRaw ->
                RecommendationSourcePreferenceStore.parse(likedRaw).toImmutableSet() to
                    RecommendationSourcePreferenceStore.parse(dislikedRaw).toImmutableSet()
            }.collectLatest { (liked, disliked) ->
                mutableState.update { it.copy(likedSourceKeys = liked, dislikedSourceKeys = disliked) }
            }
        }
        // KMK <--

        // KMK v0.8.1-fix4: live-update the source/library-quality axis
        screenModelScope.launch {
            combine(
                sourcePreferences.dislikedSourceQualityKeys().changes(),
                sourcePreferences.explicitSourceQualityKeys().changes(),
            ) { dislikedRaw, explicitRaw ->
                RecommendationSourcePreferenceStore.parse(dislikedRaw).toImmutableSet() to
                    RecommendationSourcePreferenceStore.parse(explicitRaw).toImmutableSet()
            }.collectLatest { (disliked, explicit) ->
                mutableState.update { it.copy(qualityDislikedSourceKeys = disliked, qualityExplicitSourceKeys = explicit) }
            }
        }

        screenModelScope.launch {
            combine(
                getTagTaste.subscribeAll(),
                getDisabledSources.subscribe(),
            ) { tags, disabledIds ->
                tags to disabledIds.toImmutableSet()
            }.collectLatest { (tags, disabledIds) ->
                val enabledOrdered = state.value.orderedSources.filter { it.id !in disabledIds }
                val boosted = RecommendationSourceOrdering.boostedSourceIds(enabledOrdered).toImmutableSet()
                mutableState.update {
                    it.copy(
                        tagPreferences = tags.sortedBy { t -> t.displayName }.toImmutableList(),
                        disabledSourceIds = disabledIds,
                        boostedSourceIds = boosted,
                    )
                }
                // KMK v0.8.10: a changed tag preference set can change which suggestions qualify
                // (newly excluded, or newly re-eligible after a removal) -- reload to stay accurate.
                loadTasteInsights()
            }
        }

        // KMK --> v0.7.7 follow-up: refresh visible sources when extensions are installed or uninstalled
        screenModelScope.launch {
            extensionManager.installedExtensionsFlow.collectLatest { refreshVisibleSources() }
        }
        // KMK <--
        // KMK v0.8.12: also refresh the language chip list when the available-extension repo data
        // changes (a repo refresh can surface new non-English extensions) or when the user's
        // selected languages change (e.g. from another screen/device via sync) -- previously only
        // installed-extension changes triggered a refresh, so the chip list could go stale.
        screenModelScope.launch {
            combine(
                extensionManager.availableExtensionsFlow,
                languagesPref.changes(),
            ) { _, _ -> Unit }.collectLatest { refreshVisibleSources() }
        }
        // KMK <--
    }

    // KMK v0.8.21-fix4: R4/AUG-14 correction -- Management and Diagnostics is the persistent
    // management surface for saved focus modes (rename/delete/reorder), per the corrected AUG-14
    // contract: "the For You control and Management and Diagnostics settings must use the same
    // focus-mode owner, persistence model, and ordering policy." This reads/writes the exact same
    // sourcePreferences.savedFocusModes() preference through the exact same SavedFocusModeStore
    // pure functions that exh.recs.BrowsePersonalRecommendationsScreenModel's own
    // savedFocusModes/renameFocusMode/deleteFocusMode/reorderFocusModes already use -- there is
    // only one focus-mode store; this is a second observer/writer of it, not a second system.
    // "Edit" (changing a mode's stored groups) is intentionally not duplicated here: the For You
    // page's Focus dialog is the only place with a live, current set of available recommendation
    // groups to build an "Update with current selection" editor from, and Management and
    // Diagnostics has no equivalent live context -- editing a saved mode's criteria remains a
    // For You-page action, exactly as create/apply already are.
    val savedFocusModes: StateFlow<List<SavedFocusMode>> =
        sourcePreferences.savedFocusModes().changes()
            .map { SavedFocusModeStore.parse(it) }
            .stateIn(
                screenModelScope,
                SharingStarted.Eagerly,
                SavedFocusModeStore.parse(sourcePreferences.savedFocusModes().get()),
            )

    fun renameFocusMode(id: String, newName: String) {
        val updated = SavedFocusModeStore.rename(savedFocusModes.value, id, newName, now = System.currentTimeMillis())
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(updated))
    }

    fun deleteFocusMode(id: String) {
        val updated = SavedFocusModeStore.delete(savedFocusModes.value, id)
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(updated))
    }

    fun reorderFocusModes(orderedIds: List<String>) {
        val updated = SavedFocusModeStore.reorder(savedFocusModes.value, orderedIds)
        sourcePreferences.savedFocusModes().set(SavedFocusModeStore.serialize(updated))
    }
    // KMK <--

    // KMK v0.8.10: Taste suggestions + diagnostics loading -->
    /**
     * Loads (or reloads) both the taste-suggestion candidates and the diagnostics summary. Safe to
     * call repeatedly -- guarded against overlapping loads so a rapid sequence of tag preference
     * changes doesn't queue up redundant DB scans.
     */
    private fun loadTasteInsights() {
        if (state.value.tasteInsightsLoading) return
        mutableState.update { it.copy(tasteInsightsLoading = true) }
        screenModelScope.launch {
            try {
                val suggestions = getTasteSuggestions.await()
                val diagnostics = getTasteDiagnostics.await()
                mutableState.update {
                    it.copy(tasteSuggestions = suggestions, tasteDiagnostics = diagnostics, tasteInsightsLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Local-DB-only aggregation; a failure here must never crash the settings screen --
                // simply leave the previous (possibly empty) insights in place.
                mutableState.update { it.copy(tasteInsightsLoading = false) }
            }
        }
    }

    /** Explicit user-triggered refresh (e.g. a refresh action on the suggestions/diagnostics screens). */
    fun refreshTasteInsights() = loadTasteInsights()

    /**
     * Adds a suggested tag as an explicit preference, using the exact same mutation path
     * ([setTagPreference]) a manually-added tag preference already uses -- fully reversible via the
     * existing remove/edit tag preference actions, and the suggestions list itself is reloaded
     * automatically once the tag preference change propagates through [getTagTaste]'s subscription.
     */
    fun addTasteSuggestion(candidate: TasteSuggestionCandidate, preference: TagPreference) {
        setTagPreference(candidate.displayName, preference)
    }
    // KMK <--

    fun setRatedMangaVisibility(visibility: RatedMangaVisibility) {
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.RATED_MANGA_VISIBILITY,
            "ratedMangaVisibility",
            ratedVisibilityPref,
            visibility,
        ) {
            ratedVisibilityPref.set(visibility)
        }
        mutableState.update { it.copy(ratedMangaVisibility = visibility) }
    }

    // KMK Undo Expansion Phase 1 -->
    /**
     * Shared build-before-write/commit-after-success wrapper for the simple [Preference]-backed
     * recommendation settings below. Reads the previous value, performs [write], and only commits a
     * journal entry to [exh.util.PreferenceUndoJournal] if the write actually changed the value.
     */
    private fun <T> journalPreferenceChange(
        actionType: exh.util.PreferenceJournalActionType,
        identityKey: String,
        preference: tachiyomi.core.common.preference.Preference<T>,
        newValue: T,
        write: () -> Unit,
    ) {
        val previousValue = preference.get()
        val undoEntry = exh.util.PreferenceUndoRecorder.buildPreferenceEntry(
            sourcePreferences,
            actionType,
            identityKey,
            preference,
            previousValue,
            newValue,
        )
        write()
        undoEntry?.let { exh.util.PreferenceUndoJournal.record(it) }
    }
    // KMK <--

    fun setHideKnownManga(enabled: Boolean) {
        journalPreferenceChange(exh.util.PreferenceJournalActionType.HIDE_KNOWN_MANGA, "hideKnownManga", hideKnownMangaPref, enabled) {
            hideKnownMangaPref.set(enabled)
        }
        mutableState.update { it.copy(hideKnownManga = enabled) }
    }

    // KMK --> v0.7.26
    // validate the write. Previously any
    // Int was persisted and mirrored into state verbatim, so an unsupported value could reach the
    // shared visibility policy and the For You cache fingerprint. The journal now records the same
    // resolved value that is actually stored and displayed, so Undo restores a legitimate value too.
    fun setMinChapterCount(value: Int) {
        val resolved = exh.recs.RecommendationMinChapterCountPolicy.resolve(value)
        journalPreferenceChange(exh.util.PreferenceJournalActionType.MIN_CHAPTER_COUNT, "minChapterCount", minChapterCountPref, resolved) {
            minChapterCountPref.set(resolved)
        }
        mutableState.update { it.copy(minChapterCount = resolved) }
    }
    // KMK <--

    /**
     * Persists the bounded Latest-catalogue exploration share. Validated on the way in (same
     * contract as [setMinChapterCount] and the budget setters), so an unsupported value can never
     * reach [exh.recs.RecommendationLatestBudgetPolicy.resolveAttempts] at refresh time. Journalled
     * through the existing preference Action History family so the change is undoable like every
     * other recommendation preference.
     */
    fun setLatestExplorationPercent(value: Int) {
        val resolved = exh.recs.RecommendationLatestBudgetPolicy.validate(value)
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.RESULT_BUDGET,
            "latestExplorationPercent",
            latestExplorationPercentPref,
            resolved,
        ) {
            latestExplorationPercentPref.set(resolved)
        }
        mutableState.update { it.copy(latestExplorationPercent = resolved) }
    }

    fun setLatestExplorationEnabled(enabled: Boolean) {
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.RESULT_BUDGET,
            "latestExplorationEnabled",
            latestExplorationEnabledPref,
            enabled,
        ) {
            latestExplorationEnabledPref.set(enabled)
        }
        mutableState.update { it.copy(latestExplorationEnabled = enabled) }
    }
    // KMK <--

    /** Persists the local exposure-history window. Local-only preference; never journalled to Action History (exposure itself never is). */
    fun setExposureWindowDays(value: Int) {
        val resolved = exh.recs.RecommendationExposurePolicy.validateWindowDays(value)
        exposureWindowDaysPref.set(resolved)
        mutableState.update { it.copy(exposureWindowDays = resolved) }
    }

    fun setExposureWindowEnabled(enabled: Boolean) {
        exposureWindowEnabledPref.set(enabled)
        mutableState.update { it.copy(exposureWindowEnabled = enabled) }
    }

    /**
     * Clears local exposure/ordering history only.
     *
     * this deliberately calls **only**
     * [ClearRecommendationExposure][tachiyomi.domain.taste.interactor.ClearRecommendationExposure],
     * whose repository method is a single `DELETE FROM recommendation_exposure`. It therefore cannot
     * touch ratings, library membership, tracking, taste, Not Interested, or any manga row -- those
     * live in entirely different tables reached through entirely different interactors, none of which
     * this ScreenModel invokes from here. The user-visible effect is only that repeat-title
     * de-emphasis restarts from zero.
     *
     * Exposed as a `suspend` function so success/failure/cancellation are directly testable without
     * driving the Compose lifecycle; [clearExposureHistory] is the fire-and-forget UI entry point.
     *
     * @return true when the delete completed, false when it failed. A failure is surfaced as state
     * rather than thrown, so the settings row can report it instead of crashing the screen.
     */
    suspend fun clearExposureHistoryNow(): Boolean {
        mutableState.update { it.copy(isClearingExposureHistory = true, exposureHistoryClearFailed = false) }
        return try {
            clearRecommendationExposure.await()
            mutableState.update { it.copy(isClearingExposureHistory = false, exposureHistoryClearFailed = false) }
            true
        } catch (e: CancellationException) {
            // Lifecycle cancellation must never be reported as a user-facing failure, and must not
            // leave the row stuck in a spinning state.
            mutableState.update { it.copy(isClearingExposureHistory = false) }
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Clearing recommendation exposure history failed" }
            mutableState.update { it.copy(isClearingExposureHistory = false, exposureHistoryClearFailed = true) }
            false
        }
    }

    /** UI entry point for the confirmed "Clear repeat history" action. */
    fun clearExposureHistory() {
        screenModelScope.launch { clearExposureHistoryNow() }
    }

    /** Clears the one-shot failure flag after the UI has shown it. */
    fun consumeExposureHistoryClearFailure() {
        mutableState.update { it.copy(exposureHistoryClearFailed = false) }
    }
    // KMK <--

    // KMK --> v0.7.34: enrichment cap setter
    fun setEnrichmentCap(value: Int) {
        val resolved = RecommendationEnrichmentCapPolicy.resolve(value)
        journalPreferenceChange(exh.util.PreferenceJournalActionType.ENRICHMENT_CAP, "enrichmentCap", enrichmentCapPref, resolved) {
            enrichmentCapPref.set(resolved)
        }
        mutableState.update { it.copy(enrichmentCap = resolved) }
    }
    // KMK <--

    // KMK --> EC-04 2026-09-01: discovery-effort level setter. Cache invalidation is automatic --
    // the next For You refresh reads the resolved level fresh from sourcePreferences at its own
    // discoverAdditionalPage call site; no cache fingerprint dependency is needed since this only
    // changes how many additional pages a refresh probes, not what page-1 or cached content means.
    fun setDiscoveryEffortLevel(value: exh.recs.memory.DiscoveryEffortLevel) {
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.DISCOVERY_EFFORT_LEVEL,
            "discoveryEffortLevel",
            discoveryEffortLevelPref,
            value.storedValue,
        ) {
            discoveryEffortLevelPref.set(value.storedValue)
        }
        mutableState.update { it.copy(discoveryEffortLevel = value) }
    }

    fun setDiscoveryCandidateBudget(value: Int) {
        val resolved = exh.recs.memory.RecommendationDiscoveryCandidateBudgetPolicy.resolve(value)
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.DISCOVERY_CANDIDATE_BUDGET,
            "discoveryCandidateBudget",
            discoveryCandidateBudgetPref,
            resolved,
        ) {
            discoveryCandidateBudgetPref.set(resolved)
        }
        mutableState.update { it.copy(discoveryCandidateBudget = resolved) }
    }
    // KMK <--

    // KMK --> v0.8.2: visible-card budget setter. Cache invalidation is automatic — the resolved
    // value is part of BrowsePersonalRecommendationsScreenModel's cache fingerprint, so the next
    // normal For You run detects the fingerprint mismatch and refetches instead of reusing a cache
    // sized for the old budget.
    fun setResultBudget(value: Int) {
        val validated = ForYouResultBudgetPolicy.validate(value)
        journalPreferenceChange(exh.util.PreferenceJournalActionType.RESULT_BUDGET, "resultBudget", resultBudgetPref, validated) {
            resultBudgetPref.set(validated)
        }
        mutableState.update { it.copy(resultBudget = validated) }
    }
    // KMK <--

    // KMK --> v0.8.6: group-preview budget setter. Scoped exclusively to GROUP_PREVIEW rows — never
    // touches resultBudgetPref (For You) or any global-search setting. Applies on the next load;
    // the in-memory GROUP_PREVIEW cache keys on this value, so a changed budget naturally misses
    // the cache instead of reusing a differently-sized preview.
    fun setGroupPreviewBudget(value: Int) {
        val validated = GroupPreviewBudgetPolicy.validate(value)
        journalPreferenceChange(exh.util.PreferenceJournalActionType.GROUP_PREVIEW_BUDGET, "groupPreviewBudget", groupPreviewBudgetPref, validated) {
            groupPreviewBudgetPref.set(validated)
        }
        mutableState.update { it.copy(groupPreviewBudget = validated) }
    }
    // KMK <--

    // --- Language actions ---

    fun toggleRecommendationLanguage(lang: String) {
        val current = state.value.recommendationLanguages
        val updated = if (lang in current) {
            // Prevent deselecting all languages — keep at least one
            val next = current - lang
            next.ifEmpty { current }
        } else {
            current + lang
        }
        val normalized = RecommendationSourceFilter.normalizeLanguages(updated.toSet())
        journalPreferenceChange(exh.util.PreferenceJournalActionType.RECOMMENDATION_LANGUAGES, "recommendationLanguages", languagesPref, normalized) {
            languagesPref.set(normalized)
        }
        recomputeSourcesForLanguages(normalized)
        mutableState.update { it.copy(recommendationLanguages = normalized.toImmutableSet()) }
    }

    private fun recomputeSourcesForLanguages(languages: Set<String>) {
        val filteredSources = RecommendationSourceFilter.filterForRecommendations(sourceManager.getVisibleSources(), languages)
        val storedOrder = RecommendationSourceOrdering.parse(sourceOrderPref.get())
        val allInOrder = RecommendationSourceOrdering.applyAll(filteredSources, storedOrder)
        val disabledIds = state.value.disabledSourceIds
        val enabledOrdered = allInOrder.filter { it.id !in disabledIds }
        val boosted = RecommendationSourceOrdering.boostedSourceIds(enabledOrdered).toImmutableSet()
        mutableState.update {
            it.copy(orderedSources = allInOrder.toImmutableList(), boostedSourceIds = boosted)
        }
    }

    // --- Source ordering actions ---

    fun setSourceOrder(sourceIds: List<Long>) {
        val currentById = state.value.orderedSources.associateBy { it.id }
        val ordered = sourceIds.mapNotNull { currentById[it] }
        if (ordered.size != state.value.orderedSources.size) return

        val newOrder = ordered.toImmutableList()

        // Merge visible (language-filtered) order back into full stored order so hidden-language
        // source positions are preserved.
        val existingStoredOrder = RecommendationSourceOrdering.parse(sourceOrderPref.get())
        val allVisibleIds = state.value.orderedSources.map { it.id }.toSet()
        val mergedOrder = RecommendationSourceOrdering.mergeVisibleOrder(
            existingStoredOrder = existingStoredOrder,
            visibleOrderedIds = newOrder.map { it.id },
            allVisibleSourceIds = allVisibleIds,
        )
        val serializedOrder = RecommendationSourceOrdering.serialize(mergedOrder)
        journalPreferenceChange(exh.util.PreferenceJournalActionType.SOURCE_ORDER, "sourceOrder", sourceOrderPref, serializedOrder) {
            sourceOrderPref.set(serializedOrder)
        }

        val disabledIds = state.value.disabledSourceIds
        val enabledOrdered = newOrder.filter { it.id !in disabledIds }
        val boosted = RecommendationSourceOrdering.boostedSourceIds(enabledOrdered).toImmutableSet()
        mutableState.update { it.copy(orderedSources = newOrder, boostedSourceIds = boosted) }
    }

    // KMK --> v0.6.14: confirmation dialog before destructive source order reset
    fun requestResetSourceOrder() {
        mutableState.update { it.copy(showResetSourceOrderDialog = true) }
    }

    fun dismissResetSourceOrderDialog() {
        mutableState.update { it.copy(showResetSourceOrderDialog = false) }
    }

    fun confirmResetSourceOrder() {
        mutableState.update { it.copy(showResetSourceOrderDialog = false) }
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.SOURCE_ORDER,
            "sourceOrder",
            sourceOrderPref,
            "",
        ) {
            sourceOrderPref.set("")
        }
        val languages = state.value.recommendationLanguages
        val filteredSources = RecommendationSourceFilter.filterForRecommendations(sourceManager.getVisibleSources(), languages.toSet())
        val fresh = filteredSources.toImmutableList()
        val disabledIds = state.value.disabledSourceIds
        val enabledOrdered = fresh.filter { it.id !in disabledIds }
        val boosted = RecommendationSourceOrdering.boostedSourceIds(enabledOrdered).toImmutableSet()
        mutableState.update { it.copy(orderedSources = fresh, boostedSourceIds = boosted) }
    }
    // KMK <--

    // --- Tag preference actions ---

    fun openAddTagDialog() {
        mutableState.update { it.copy(dialog = Dialog.AddTag) }
    }

    fun openEditTagDialog(tag: TagTaste) {
        mutableState.update { it.copy(dialog = Dialog.EditTag(tag)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun setTagPreference(displayName: String, preference: TagPreference) {
        if (displayName.isBlank()) return
        val trimmed = displayName.trim()
        screenModelScope.launchNonCancellable {
            // KMK Undo Expansion Phase 1: build before write, commit only after success.
            val normalizedTag = trimmed.normalizeTag()
            val previous = getTagTaste.await(normalizedTag)?.preference?.let { TagPreference.fromValue(it) }
            val undoEntry = exh.util.PreferenceUndoRecorder.buildTagPreferenceEntry(
                sourcePreferences,
                getTagTaste,
                setTagTaste,
                clearTagTaste,
                trimmed,
                normalizedTag,
                previous,
                preference,
            )
            setTagTaste.await(trimmed, preference)
            undoEntry?.let { exh.util.PreferenceUndoJournal.record(it) }
        }
    }

    fun removeTagPreference(normalizedTag: String) {
        screenModelScope.launchNonCancellable {
            // KMK Undo Expansion Phase 1: build before write, commit only after success.
            val previousTaste = getTagTaste.await(normalizedTag)
            val previous = previousTaste?.preference?.let { TagPreference.fromValue(it) }
            val undoEntry = if (previousTaste != null) {
                exh.util.PreferenceUndoRecorder.buildTagPreferenceEntry(
                    sourcePreferences,
                    getTagTaste,
                    setTagTaste,
                    clearTagTaste,
                    previousTaste.displayName,
                    normalizedTag,
                    previous,
                    null,
                )
            } else {
                null
            }
            clearTagTaste.await(normalizedTag)
            undoEntry?.let { exh.util.PreferenceUndoJournal.record(it) }
        }
    }

    // --- Source exclusion actions ---

    fun toggleSource(sourceId: Long) {
        val currentlyDisabled = sourceId in state.value.disabledSourceIds
        screenModelScope.launchNonCancellable {
            val entry = exh.util.PreferenceUndoEntry(
                id = exh.util.PreferenceUndoEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = exh.util.PreferenceJournalActionType.SOURCE_EXCLUSION,
                identityKey = sourceId.toString(),
                previousValue = !currentlyDisabled,
                expectedPostValue = currentlyDisabled,
                readCurrent = { sourceId !in getDisabledSources.await() },
                restore = { enabled -> setSourceEnabled.await(sourceId, enabled) },
            )
            setSourceEnabled.await(sourceId, enabled = currentlyDisabled)
            exh.util.PreferenceUndoJournal.record(entry)
        }
    }

    // KMK -->
    // --- Non-installed suggestion actions ---

    fun installSuggestion(suggestion: NonInstalledSourceSuggestion) {
        val key = suggestion.dismissalKey
        if (state.value.isBulkInstallingSuggestions || key in state.value.installingSuggestionKeys) return

        installRecoverySuggestions.remove(key)
        val operationId = newSuggestionInstallOperationId()
        suggestionInstallOperationIds[key] = operationId
        retryableSuggestions = emptyList()
        mutableState.update {
            it.copy(
                installingSuggestionKeys = (it.installingSuggestionKeys + key).toImmutableSet(),
                suggestionInstallFeedback = null,
                retryableSuggestionInstallFailureCount = 0,
            )
        }
        val job = screenModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val installed = installSuggestionOnce(suggestion)
                currentCoroutineContext().ensureActive()
                if (suggestionInstallOperationIds[key] == operationId) {
                    if (installed) installRecoverySuggestions.remove(key)
                    retryableSuggestions = if (installed) emptyList() else listOf(suggestion)
                    mutableState.update {
                        it.copy(
                            suggestionInstallFeedback = if (installed) {
                                SourcesToTryInstallFeedback.Installed(operationId)
                            } else {
                                SourcesToTryInstallFeedback.Failed(operationId)
                            },
                            retryableSuggestionInstallFailureCount = retryableSuggestions.size,
                        )
                    }
                }
            } catch (e: CancellationException) {
                installRecoverySuggestions[key] = suggestion
                if (suggestionInstallOperationIds[key] == operationId) {
                    retryableSuggestions = listOf(suggestion)
                    mutableState.update {
                        it.copy(
                            suggestionInstallFeedback = SourcesToTryInstallFeedback.Cancelled(operationId),
                            retryableSuggestionInstallFailureCount = retryableSuggestions.size,
                        )
                    }
                }
                throw e
            } catch (_: Exception) {
                logcat(LogPriority.WARN) { "Sources To Try install failed." }
                if (suggestionInstallOperationIds[key] == operationId) {
                    installRecoverySuggestions[key] = suggestion
                    retryableSuggestions = listOf(suggestion)
                    mutableState.update {
                        it.copy(
                            suggestionInstallFeedback = SourcesToTryInstallFeedback.Failed(operationId),
                            retryableSuggestionInstallFailureCount = retryableSuggestions.size,
                        )
                    }
                }
            } finally {
                if (shouldClearSuggestionInstallState(suggestionInstallOperationIds[key], operationId)) {
                    suggestionInstallOperationIds.remove(key, operationId)
                    suggestionInstallJobs.remove(key)
                    mutableState.update {
                        it.copy(installingSuggestionKeys = (it.installingSuggestionKeys - key).toImmutableSet())
                    }
                }
            }
        }
        suggestionInstallJobs[key] = job
        job.start()
    }

    fun installSuggestions(suggestions: List<NonInstalledSourceSuggestion>) {
        // KMK -->
        // Guard against overlapping bulk batches
        if (state.value.isBulkInstallingSuggestions || state.value.installingSuggestionKeys.isNotEmpty()) return
        // KMK <--
        val deduped = suggestions.distinctBy { "${it.extension.signatureHash}|${it.extension.pkgName}" }
        if (deduped.isEmpty()) return
        val operationId = newSuggestionInstallOperationId()
        bulkSuggestionInstallOperationId = operationId
        retryableSuggestions = emptyList()
        mutableState.update {
            it.copy(
                isBulkInstallingSuggestions = true,
                suggestionInstallFeedback = null,
                retryableSuggestionInstallFailureCount = 0,
            )
        }
        val job = screenModelScope.launch(start = CoroutineStart.LAZY) {
            var installedCount = 0
            val failed = mutableListOf<NonInstalledSourceSuggestion>()
            try {
                for (suggestion in deduped) {
                    currentCoroutineContext().ensureActive()
                    val key = suggestion.dismissalKey
                    activeBulkSuggestion = suggestion
                    mutableState.update { it.copy(installingSuggestionKeys = (it.installingSuggestionKeys + key).toImmutableSet()) }
                    try {
                        if (installSuggestionOnce(suggestion)) installedCount++ else failed += suggestion
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        logcat(LogPriority.WARN) { "A Sources To Try bulk install item failed." }
                        failed += suggestion
                        // Per-extension failure is isolated — continue with remaining suggestions
                    } finally {
                        activeBulkSuggestion = null
                        mutableState.update { it.copy(installingSuggestionKeys = (it.installingSuggestionKeys - key).toImmutableSet()) }
                    }
                }
                currentCoroutineContext().ensureActive()
                val feedback = when {
                    failed.isEmpty() -> SourcesToTryInstallFeedback.BulkInstalled(operationId, installedCount)
                    installedCount == 0 -> SourcesToTryInstallFeedback.BulkFailed(operationId, failed.size)
                    else -> SourcesToTryInstallFeedback.BulkPartial(operationId, installedCount, failed.size)
                }
                if (bulkSuggestionInstallOperationId == operationId) {
                    retryableSuggestions = failed.toList()
                    mutableState.update {
                        it.copy(
                            suggestionInstallFeedback = feedback,
                            retryableSuggestionInstallFailureCount = retryableSuggestions.size,
                        )
                    }
                }
            } finally {
                activeBulkSuggestion = null
                if (bulkSuggestionInstallOperationId == operationId) {
                    bulkSuggestionInstallOperationId = null
                    bulkSuggestionInstallJob = null
                }
                mutableState.update {
                    it.copy(
                        isBulkInstallingSuggestions = false,
                        installingSuggestionKeys = persistentSetOf(),
                    )
                }
            }
        }
        bulkSuggestionInstallJob = job
        job.start()
    }

    fun cancelSuggestionInstall(suggestion: NonInstalledSourceSuggestion) {
        val job = suggestionInstallJobs.remove(suggestion.dismissalKey) ?: return
        job.cancel()
        installRecoverySuggestions[suggestion.dismissalKey] = suggestion
        retryableSuggestions = listOf(suggestion)
        mutableState.update {
            it.copy(
                suggestionInstallFeedback = SourcesToTryInstallFeedback.Cancelled(newSuggestionInstallOperationId()),
                retryableSuggestionInstallFailureCount = retryableSuggestions.size,
            )
        }
    }

    fun cancelBulkSuggestionInstall() {
        val job = bulkSuggestionInstallJob ?: return
        bulkSuggestionInstallJob = null
        bulkSuggestionInstallOperationId = null
        job.cancel()
        retryableSuggestions = emptyList()
        mutableState.update {
            it.copy(
                suggestionInstallFeedback = SourcesToTryInstallFeedback.Cancelled(newSuggestionInstallOperationId()),
                retryableSuggestionInstallFailureCount = 0,
            )
        }
    }

    fun retrySuggestionInstallFailures() {
        val currentKeys = state.value.nonInstalledSuggestions.mapTo(mutableSetOf()) { it.dismissalKey }
        val retrySnapshot = retryableSuggestions.filter { it.dismissalKey in currentKeys }
        retryableSuggestions = retrySnapshot
        mutableState.update { it.copy(retryableSuggestionInstallFailureCount = retrySnapshot.size) }
        when (retrySnapshot.size) {
            0 -> return
            1 -> installSuggestion(retrySnapshot.single())
            else -> installSuggestions(retrySnapshot)
        }
    }

    fun dismissSuggestionInstallFeedback() {
        retryableSuggestions = emptyList()
        mutableState.update {
            it.copy(
                suggestionInstallFeedback = null,
                retryableSuggestionInstallFailureCount = 0,
            )
        }
    }

    private suspend fun installSuggestionOnce(suggestion: NonInstalledSourceSuggestion): Boolean {
        val receiptId = exh.util.NonUndoableEvent.newId()
        val terminal = try {
            withTimeoutOrNull(SOURCES_TO_TRY_INSTALL_TIMEOUT_MS) {
                extensionManager.installExtension(suggestion.extension)
                    .recordUserInitiatedInstall(id = receiptId) { sourcePreferences.evaluationMode().get() }
                    .recordPackageOperationReceipt(
                        kind = PackageOperationKind.INSTALL,
                        packageName = suggestion.extension.pkgName,
                        signatureHash = suggestion.extension.signatureHash,
                        versionCode = suggestion.extension.versionCode,
                        artifactUri = suggestion.extension.apkUrl,
                        id = receiptId,
                    ) { sourcePreferences.evaluationMode().get() }
                    .first { it.isCompleted() }
            }
        } catch (e: CancellationException) {
            // Covers screen-scope cancellation as well as explicit row/bulk cancellation. The
            // caller owns the job state; this owner owns releasing the active package operation.
            extensionManager.cancelInstallUpdateExtension(suggestion.extension)
            throw e
        }
        if (terminal == null) {
            // A stalled installer must release its package operation before the row becomes
            // retryable; user cancellation uses this same manager-level cleanup path.
            extensionManager.cancelInstallUpdateExtension(suggestion.extension)
            return false
        }
        return terminal == InstallStep.Installed
    }

    private fun newSuggestionInstallOperationId(): Long {
        nextSuggestionInstallOperationId += 1
        return nextSuggestionInstallOperationId
    }

    fun dismissSuggestion(suggestion: NonInstalledSourceSuggestion) {
        installRecoverySuggestions.remove(suggestion.dismissalKey)
        val pref = sourcePreferences.dismissedNonInstalledRecommendationSources()
        val current = NonInstalledSourceSuggestionStore.parse(pref.get())
        val newValue = NonInstalledSourceSuggestionStore.serialize(NonInstalledSourceSuggestionStore.dismiss(current, suggestion.dismissalKey))
        journalPreferenceChange(exh.util.PreferenceJournalActionType.SUGGESTION_DISMISSAL, suggestion.dismissalKey, pref, newValue) {
            pref.set(newValue)
        }
    }

    // KMK --> v0.7.0: Phase 1 – clear all dismissed source suggestions
    fun clearDismissedSuggestions() {
        val pref = sourcePreferences.dismissedNonInstalledRecommendationSources()
        journalPreferenceChange(exh.util.PreferenceJournalActionType.DISMISSED_SUGGESTIONS_CLEAR, "dismissedSuggestions", pref, "") {
            pref.set("")
        }
    }
    // KMK <--

    // KMK --> v0.7.38: For You discovery memory reset
    fun requestClearDiscoveryHistory() {
        mutableState.update { it.copy(showClearDiscoveryHistoryDialog = true) }
    }

    fun dismissClearDiscoveryHistoryDialog() {
        mutableState.update { it.copy(showClearDiscoveryHistoryDialog = false) }
    }

    fun confirmClearDiscoveryHistory() {
        mutableState.update { it.copy(showClearDiscoveryHistoryDialog = false) }
        screenModelScope.launchNonCancellable {
            clearMemory.await()
            // KMK --> v0.7.39: also clear progress so discovery restarts from page 1
            clearDiscoveryProgress.await()
            // KMK <--
        }
    }
    // KMK <--

    fun toggleExpandSuggestions() {
        mutableState.update { it.copy(suggestionsExpanded = !it.suggestionsExpanded) }
    }

    // --- Suggestion selection mode actions ---

    fun enterSuggestionSelectionMode() {
        mutableState.update { it.copy(isSuggestionSelectionMode = true) }
    }

    fun exitSuggestionSelectionMode() {
        mutableState.update { it.copy(isSuggestionSelectionMode = false, selectedSuggestionKeys = persistentSetOf()) }
    }

    fun toggleSuggestionSelected(suggestion: NonInstalledSourceSuggestion) {
        val key = suggestion.dismissalKey
        mutableState.update { s ->
            val keys = s.selectedSuggestionKeys
            s.copy(selectedSuggestionKeys = (if (key in keys) keys - key else keys + key).toImmutableSet())
        }
    }

    fun installSelectedSuggestions(visibleSuggestions: List<NonInstalledSourceSuggestion>) {
        val selectedKeys = state.value.selectedSuggestionKeys
        val installingKeys = state.value.installingSuggestionKeys
        // Only install visible, selected suggestions that aren't already installing
        val toInstall = visibleSuggestions.filter {
            it.dismissalKey in selectedKeys && it.dismissalKey !in installingKeys
        }
        if (toInstall.isEmpty()) return
        exitSuggestionSelectionMode()
        installSuggestions(toInstall)
    }

    // KMK --> v0.7.8: same-manga matching settings actions
    fun setSameMangaResultsPerSource(value: Int) {
        val preference = sourcePreferences.sameMangaMatchResultsPerSource()
        val resolved = exh.recs.matching.SameMangaMatchSettings.clampResultCap(value)
        journalPreferenceChange(exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING, "sameMangaResultsPerSource", preference, resolved) {
            preference.set(resolved)
        }
        mutableState.update { it.copy(sameMangaResultsPerSource = resolved) }
    }

    fun setSameMangaPreselectResults(enabled: Boolean) {
        val preference = sourcePreferences.sameMangaMatchPreselectResults()
        journalPreferenceChange(exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING, "sameMangaPreselectResults", preference, enabled) {
            preference.set(enabled)
        }
        mutableState.update {
            it.copy(
                sameMangaPreselectionMode = if (enabled) {
                    exh.recs.matching.SameMangaPreselectionMode.ALL
                } else {
                    exh.recs.matching.SameMangaPreselectionMode.NONE
                },
            )
        }
    }

    fun setSameMangaPreselectionMode(mode: exh.recs.matching.SameMangaPreselectionMode) {
        val preference = sourcePreferences.sameMangaMatchPreselectionMode()
        journalPreferenceChange(exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING, "sameMangaPreselectionMode", preference, mode.storedValue) {
            preference.set(mode.storedValue)
        }
        mutableState.update { it.copy(sameMangaPreselectionMode = mode) }
    }

    fun setChapterCompletionRatingPromptEnabled(enabled: Boolean) {
        val preference = sourcePreferences.chapterCompletionRatingPromptEnabled()
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING,
            "chapterCompletionRatingPromptEnabled",
            preference,
            enabled,
        ) {
            preference.set(enabled)
        }
        mutableState.update { it.copy(chapterCompletionRatingPromptEnabled = enabled) }
    }

    fun setChapterCompletionRatingOtherVersionsPromptEnabled(enabled: Boolean) {
        val preference = sourcePreferences.chapterCompletionRatingOtherVersionsPromptEnabled()
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING,
            "chapterCompletionRatingOtherVersionsPromptEnabled",
            preference,
            enabled,
        ) {
            preference.set(enabled)
        }
        mutableState.update { it.copy(chapterCompletionRatingOtherVersionsPromptEnabled = enabled) }
    }

    fun setConfirmedTrackedVersionRatingPropagationEnabled(enabled: Boolean) {
        val preference = sourcePreferences.confirmedTrackedVersionRatingPropagationEnabled()
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING,
            "confirmedTrackedVersionRatingPropagationEnabled",
            preference,
            enabled,
        ) {
            preference.set(enabled)
        }
        mutableState.update { it.copy(confirmedTrackedVersionRatingPropagationEnabled = enabled) }
    }

    fun setConfirmedTrackedVersionLocalTrackingPropagationEnabled(enabled: Boolean) {
        val preference = sourcePreferences.confirmedTrackedVersionLocalTrackingPropagationEnabled()
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING,
            "confirmedTrackedVersionLocalTrackingPropagationEnabled",
            preference,
            enabled,
        ) {
            preference.set(enabled)
        }
        mutableState.update { it.copy(confirmedTrackedVersionLocalTrackingPropagationEnabled = enabled) }
    }

    fun setRatedMangaActionsUseSelection(enabled: Boolean) {
        val preference = sourcePreferences.ratedMangaActionsUseSelection()
        journalPreferenceChange(
            exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING,
            "ratedMangaActionsUseSelection",
            preference,
            enabled,
        ) {
            preference.set(enabled)
        }
        mutableState.update { it.copy(ratedMangaActionsUseSelection = enabled) }
    }

    fun setBestVersionPreviewSampleSize(value: Int) {
        val preference = sourcePreferences.bestVersionPreviewSampleSize()
        val resolved = exh.recs.matching.SameMangaMatchSettings.clampSampleSize(value)
        journalPreferenceChange(exh.util.PreferenceJournalActionType.BEST_VERSION_PREVIEW, "bestVersionPreviewSampleSize", preference, resolved) {
            preference.set(resolved)
        }
        mutableState.update { it.copy(bestVersionPreviewSampleSize = resolved) }
    }

    fun setBestVersionAvoidFirstPages(enabled: Boolean) {
        val preference = sourcePreferences.bestVersionAvoidFirstPages()
        journalPreferenceChange(exh.util.PreferenceJournalActionType.BEST_VERSION_PREVIEW, "bestVersionAvoidFirstPages", preference, enabled) {
            preference.set(enabled)
        }
        mutableState.update { it.copy(bestVersionAvoidFirstPages = enabled) }
    }
    // KMK <--

    // --- Source preference (like/dislike) actions ---

    private fun currentRecommendationSourcePreferenceState(): exh.util.RecommendationSourcePreferenceUndoState =
        exh.util.RecommendationSourcePreferenceUndoState(
            liked = RecommendationSourcePreferenceStore.parse(sourcePreferences.likedRecommendationSourceKeys().get()),
            disliked = RecommendationSourcePreferenceStore.parse(sourcePreferences.dislikedRecommendationSourceKeys().get()),
        )

    private fun writeRecommendationSourcePreferenceState(state: exh.util.RecommendationSourcePreferenceUndoState) {
        val liked = sourcePreferences.likedRecommendationSourceKeys()
        val disliked = sourcePreferences.dislikedRecommendationSourceKeys()
        val previousLiked = liked.get()
        val previousDisliked = disliked.get()
        try {
            liked.set(RecommendationSourcePreferenceStore.serialize(state.liked))
            disliked.set(RecommendationSourcePreferenceStore.serialize(state.disliked))
        } catch (e: Throwable) {
            runCatching {
                liked.set(previousLiked)
                disliked.set(previousDisliked)
            }
            throw e
        }
    }

    fun setInstalledSourcePreference(sourceId: Long, preference: RecommendationSourcePreference) {
        val key = RecommendationSourcePreferenceStore.installedKey(sourceId)
        applySourcePreference(key, preference)
    }

    fun setAvailableSourcePreference(suggestion: NonInstalledSourceSuggestion, preference: RecommendationSourcePreference) {
        val key = RecommendationSourcePreferenceStore.availableKey(
            suggestion.extension.signatureHash,
            suggestion.extension.pkgName,
            suggestion.source?.id,
        )
        applySourcePreference(key, preference)
    }

    private fun applySourcePreference(key: String, preference: RecommendationSourcePreference) {
        val likedPref = sourcePreferences.likedRecommendationSourceKeys()
        val dislikedPref = sourcePreferences.dislikedRecommendationSourceKeys()
        val currentLiked = RecommendationSourcePreferenceStore.parse(likedPref.get())
        val currentDisliked = RecommendationSourcePreferenceStore.parse(dislikedPref.get())
        val (newLiked, newDisliked) = when (preference) {
            RecommendationSourcePreference.LIKE -> RecommendationSourcePreferenceStore.like(currentLiked, currentDisliked, key)
            RecommendationSourcePreference.DISLIKE -> RecommendationSourcePreferenceStore.dislike(currentLiked, currentDisliked, key)
            RecommendationSourcePreference.NEUTRAL -> RecommendationSourcePreferenceStore.reset(currentLiked, currentDisliked, key)
        }
        val previous = currentRecommendationSourcePreferenceState()
        val next = exh.util.RecommendationSourcePreferenceUndoState(newLiked, newDisliked)
        likedPref.set(RecommendationSourcePreferenceStore.serialize(newLiked))
        dislikedPref.set(RecommendationSourcePreferenceStore.serialize(newDisliked))
        if (previous != next) {
            exh.util.PreferenceUndoJournal.record(
                exh.util.PreferenceUndoEntry(
                    id = exh.util.PreferenceUndoEntry.newId(),
                    timestamp = System.currentTimeMillis(),
                    actionType = exh.util.PreferenceJournalActionType.SOURCE_PREFERENCE,
                    identityKey = key,
                    previousValue = previous,
                    expectedPostValue = next,
                    readCurrent = ::currentRecommendationSourcePreferenceState,
                    restore = ::writeRecommendationSourcePreferenceState,
                ),
            )
        }
    }
    // KMK <--

    // KMK v0.8.1-fix4: source/library-quality actions -- separate axis from like/dislike above.
    // "Do I consider this source itself worth showing/suggesting/evaluating?" not "do I want its
    // For You rows?"

    fun markInstalledSourceQualityPoor(sourceId: Long) =
        applySourceQualityMark(RecommendationSourcePreferenceStore.installedKey(sourceId), poor = true)

    fun markInstalledSourceQualityExplicit(sourceId: Long) =
        applySourceQualityMark(RecommendationSourcePreferenceStore.installedKey(sourceId), poor = false)

    fun clearInstalledSourceQualityMark(sourceId: Long) =
        clearSourceQualityMark(RecommendationSourcePreferenceStore.installedKey(sourceId))

    fun markAvailableSourceQualityPoor(suggestion: NonInstalledSourceSuggestion) = applySourceQualityMark(
        RecommendationSourcePreferenceStore.availableKey(suggestion.extension.signatureHash, suggestion.extension.pkgName, suggestion.source?.id),
        poor = true,
    )

    fun markAvailableSourceQualityExplicit(suggestion: NonInstalledSourceSuggestion) = applySourceQualityMark(
        RecommendationSourcePreferenceStore.availableKey(suggestion.extension.signatureHash, suggestion.extension.pkgName, suggestion.source?.id),
        poor = false,
    )

    fun clearAvailableSourceQualityMark(suggestion: NonInstalledSourceSuggestion) = clearSourceQualityMark(
        RecommendationSourcePreferenceStore.availableKey(suggestion.extension.signatureHash, suggestion.extension.pkgName, suggestion.source?.id),
    )

    // KMK Undo Expansion Phase 3: source-quality marks are 3 preferences (liked/disliked/explicit
    // key sets) written together as one state transform. Journaled as a single composite
    // Triple<Set,Set,Set> entry so Undo restores the whole prior mark state atomically, not one of
    // the three preferences in isolation (which could reconstruct an impossible intermediate state).
    private fun currentSourceQualityState(): exh.recs.sourceprefs.SourceQualityMarkPolicy.State {
        val likedPref = sourcePreferences.likedSourceQualityKeys()
        val dislikedPref = sourcePreferences.dislikedSourceQualityKeys()
        val explicitPref = sourcePreferences.explicitSourceQualityKeys()
        return exh.recs.sourceprefs.SourceQualityMarkPolicy.State(
            liked = RecommendationSourcePreferenceStore.parse(likedPref.get()),
            disliked = RecommendationSourcePreferenceStore.parse(dislikedPref.get()),
            explicit = RecommendationSourcePreferenceStore.parse(explicitPref.get()),
        )
    }

    private fun writeSourceQualityState(state: exh.recs.sourceprefs.SourceQualityMarkPolicy.State) {
        val liked = sourcePreferences.likedSourceQualityKeys()
        val disliked = sourcePreferences.dislikedSourceQualityKeys()
        val explicit = sourcePreferences.explicitSourceQualityKeys()
        val previousLiked = liked.get()
        val previousDisliked = disliked.get()
        val previousExplicit = explicit.get()
        try {
            liked.set(RecommendationSourcePreferenceStore.serialize(state.liked))
            disliked.set(RecommendationSourcePreferenceStore.serialize(state.disliked))
            explicit.set(RecommendationSourcePreferenceStore.serialize(state.explicit))
        } catch (e: Throwable) {
            runCatching {
                liked.set(previousLiked)
                disliked.set(previousDisliked)
                explicit.set(previousExplicit)
            }
            throw e
        }
    }

    private fun journalSourceQualityChange(
        identityKey: String,
        actionType: exh.util.PreferenceJournalActionType,
        previous: exh.recs.sourceprefs.SourceQualityMarkPolicy.State,
        next: exh.recs.sourceprefs.SourceQualityMarkPolicy.State,
    ) {
        if (previous == next) return
        exh.util.PreferenceUndoJournal.record(
            exh.util.PreferenceUndoEntry(
                id = exh.util.PreferenceUndoEntry.newId(),
                timestamp = System.currentTimeMillis(),
                actionType = actionType,
                identityKey = identityKey,
                previousValue = previous,
                expectedPostValue = next,
                readCurrent = { currentSourceQualityState() },
                restore = { writeSourceQualityState(it) },
            ),
        )
    }

    private fun applySourceQualityMark(key: String, poor: Boolean) {
        val current = currentSourceQualityState()
        val next = if (poor) {
            exh.recs.sourceprefs.SourceQualityMarkPolicy.markPoor(current, key)
        } else {
            exh.recs.sourceprefs.SourceQualityMarkPolicy.markExplicit(current, key)
        }
        writeSourceQualityState(next)
        journalSourceQualityChange(key, exh.util.PreferenceJournalActionType.SOURCE_QUALITY_MARK, current, next)
    }

    private fun clearSourceQualityMark(key: String) {
        val current = currentSourceQualityState()
        val next = exh.recs.sourceprefs.SourceQualityMarkPolicy.clear(current, key)
        writeSourceQualityState(next)
        journalSourceQualityChange(key, exh.util.PreferenceJournalActionType.SOURCE_QUALITY_MARK, current, next)
    }

    /** Management/recovery action: clears every source-quality mark across all sources. */
    fun clearAllSourceQualityMarks() {
        val current = currentSourceQualityState()
        val next = exh.recs.sourceprefs.SourceQualityMarkPolicy.State(emptySet(), emptySet(), emptySet())
        writeSourceQualityState(next)
        journalSourceQualityChange("all", exh.util.PreferenceJournalActionType.SOURCE_QUALITY_CLEAR_ALL, current, next)
    }
    // KMK <--

    // KMK --> v0.7.19: apply source order suggested by rolling fit stats
    fun applyFitSuggestedOrder() {
        val fitStats = state.value.sourceFitStats
        val sources = state.value.orderedSources
        val (withData, withoutData) = sources.partition {
            (fitStats[it.id]?.runCount ?: 0) >= SourceFitStats.MIN_RUNS_FOR_LABEL
        }
        val sortedWithData = withData.sortedWith(
            compareByDescending<Source> {
                fitStats[it.id]?.fitLabel?.fitScore ?: -1
            }.thenBy { sources.indexOf(it) },
        )
        setSourceOrder((sortedWithData + withoutData).map { it.id })
    }
    // KMK <--

    // KMK --> v0.7.7 follow-up: called whenever installed extensions change so orderedSources and
    // availableLanguages reflect the current source list without requiring a screen restart.
    private fun refreshVisibleSources() {
        val freshSources = sourceManager.getVisibleSources()
        val languages = state.value.recommendationLanguages.toSet()
        val filteredSources = RecommendationSourceFilter.filterForRecommendations(freshSources, languages)
        val storedOrder = RecommendationSourceOrdering.parse(sourceOrderPref.get())
        val allInOrder = RecommendationSourceOrdering.applyAll(filteredSources, storedOrder)
        // KMK v0.8.12: see the init block comment above -- same merged-language policy.
        val availableLangs = RecommendationLanguageAvailabilityPolicy.availableLanguages(
            selectedLanguages = languages,
            installedVisibleSources = freshSources,
            availableExtensions = extensionManager.availableExtensionsFlow.value,
        )
        val disabledIds = state.value.disabledSourceIds
        val enabledOrdered = allInOrder.filter { it.id !in disabledIds }
        val boosted = RecommendationSourceOrdering.boostedSourceIds(enabledOrdered).toImmutableSet()
        mutableState.update {
            it.copy(
                orderedSources = allInOrder.toImmutableList(),
                availableLanguages = availableLangs.toImmutableList(),
                boostedSourceIds = boosted,
            )
        }
    }
    // KMK <--

    // --- State ---

    @Immutable
    data class State(
        val tagPreferences: ImmutableList<TagTaste> = persistentListOf(),
        val orderedSources: ImmutableList<Source> = persistentListOf(),
        val disabledSourceIds: ImmutableSet<Long> = persistentSetOf(),
        val boostedSourceIds: ImmutableSet<Long> = persistentSetOf(),
        val ratedMangaVisibility: RatedMangaVisibility = RatedMangaVisibility.HIDE_DISLIKED_ONLY,
        val hideKnownManga: Boolean = true,
        // KMK --> v0.7.26
        val minChapterCount: Int = 0,
        // KMK <--
        // KMK --> v0.7.34: enrichment cap — number of candidates to enrich per source
        val enrichmentCap: Int = 5,
        // KMK <--
        // KMK --> EC-04 2026-09-01: configurable discovery-effort policy
        val discoveryEffortLevel: exh.recs.memory.DiscoveryEffortLevel = exh.recs.memory.DiscoveryEffortLevel.DEFAULT,
        val discoveryCandidateBudget: Int = exh.recs.memory.RecommendationDiscoveryCandidateBudgetPolicy.DEFAULT,
        // KMK <--
        // KMK --> v0.8.2: visible manga cards per ordinary For You source row
        val resultBudget: Int = ForYouResultBudgetPolicy.DEFAULT,
        // KMK v0.8.6: group-recommendation initial preview budget, independent of resultBudget above
        val groupPreviewBudget: Int = GroupPreviewBudgetPolicy.DEFAULT,
        // KMK <--
        // bounded Latest exploration share
        val latestExplorationPercent: Int = exh.recs.RecommendationLatestBudgetPolicy.DEFAULT,
        val latestExplorationEnabled: Boolean = true,
        val exposureWindowDays: Int = exh.recs.RecommendationExposurePolicy.DEFAULT_WINDOW_DAYS,
        val exposureWindowEnabled: Boolean = true,
        // Clear-exposure-history action state.
        val isClearingExposureHistory: Boolean = false,
        val exposureHistoryClearFailed: Boolean = false,
        val recommendationLanguages: ImmutableSet<String> = persistentSetOf("en"),
        val availableLanguages: ImmutableList<String> = persistentListOf(),
        val dialog: Dialog? = null,
        // KMK -->
        /** Last-run status for each source from the most recent For You run. */
        val sourceStatuses: ImmutableMap<Long, RecommendationSourceRunStatus> = persistentMapOf(),
        // KMK --> v0.7.19: rolling source fit stats accumulated across For You runs
        val sourceFitStats: ImmutableMap<Long, SourceFitStats> = persistentMapOf(),
        // KMK <--
        /** Non-installed sources suggested based on recommendation language and metadata. */
        val nonInstalledSuggestions: ImmutableList<NonInstalledSourceSuggestion> = persistentListOf(),
        /** Whether the Sources To Try section is showing all suggestions beyond the default 5. */
        val suggestionsExpanded: Boolean = false,
        /** Serialized keys of user-liked recommendation sources (installed or available). */
        val likedSourceKeys: ImmutableSet<String> = persistentSetOf(),
        /** Serialized keys of user-disliked recommendation sources (installed or available). */
        val dislikedSourceKeys: ImmutableSet<String> = persistentSetOf(),
        // KMK v0.8.1-fix4: source/library-quality axis -- separate from the recommendation axis above
        /** Keys of sources marked poor or too-explicit as a source/library, regardless of For You fit. */
        val qualityDislikedSourceKeys: ImmutableSet<String> = persistentSetOf(),
        /** Subset of qualityDislikedSourceKeys marked specifically "too explicit" rather than generically "poor". */
        val qualityExplicitSourceKeys: ImmutableSet<String> = persistentSetOf(),
        /** Dismissal keys of suggestions currently being installed individually or in bulk. */
        val installingSuggestionKeys: ImmutableSet<String> = persistentSetOf(),
        /** True while a bulk install of visible suggestions is in progress. */
        val isBulkInstallingSuggestions: Boolean = false,
        /** Identity-free terminal outcome for the latest Sources To Try install operation. */
        val suggestionInstallFeedback: SourcesToTryInstallFeedback? = null,
        /** Number of failed suggestions that still exist in the current live list and can be retried. */
        val retryableSuggestionInstallFailureCount: Int = 0,
        /** True while the user is manually selecting suggestions for selective install. */
        val isSuggestionSelectionMode: Boolean = false,
        /** Dismissal keys of suggestions currently selected for selective install. */
        val selectedSuggestionKeys: ImmutableSet<String> = persistentSetOf(),
        /** True while the source order reset confirmation dialog is visible. */
        // KMK --> v0.6.14
        val showResetSourceOrderDialog: Boolean = false,
        // KMK <--
        // KMK --> v0.7.38: For You discovery memory reset dialog
        val showClearDiscoveryHistoryDialog: Boolean = false,
        // KMK <--
        // KMK --> v0.7.0: Phase 1 – number of dismissed source suggestions
        val dismissedSuggestionCount: Int = 0,
        // KMK <--
        // KMK --> v0.7.8: same-manga matching settings
        val sameMangaResultsPerSource: Int = 2,
        val sameMangaPreselectionMode: exh.recs.matching.SameMangaPreselectionMode = exh.recs.matching.SameMangaPreselectionMode.ALL,
        val chapterCompletionRatingPromptEnabled: Boolean = true,
        val chapterCompletionRatingOtherVersionsPromptEnabled: Boolean = true,
        val confirmedTrackedVersionRatingPropagationEnabled: Boolean = false,
        val confirmedTrackedVersionLocalTrackingPropagationEnabled: Boolean = false,
        val ratedMangaActionsUseSelection: Boolean = true,
        val bestVersionPreviewSampleSize: Int = 5,
        val bestVersionAvoidFirstPages: Boolean = true,
        // KMK <--
        // KMK v0.8.10: taste suggestions + diagnostics
        val tasteSuggestions: TasteSuggestionResult = TasteSuggestionResult(persistentListOf(), persistentListOf(), 0),
        val tasteDiagnostics: TasteDiagnosticsResult? = null,
        val tasteInsightsLoading: Boolean = false,
        /** Latest persisted source-evaluation rows used for metadata/tag coverage diagnostics. */
        val sourceMetadataTagDiagnostics: ImmutableList<tachiyomi.domain.taste.model.SourceEvaluation> = persistentListOf(),
        // KMK <--
        // KMK v0.8.14-fix1: read-only For You preview snapshot -- null when no For You refresh has
        // ever produced a visible result yet. See RecommendationForYouPreviewSnapshotStore.
        val forYouPreviewSnapshot: RecommendationForYouPreviewSnapshot? = null,
        // KMK <--
    ) {
        // KMK --> v0.7.19: show the "Suggest priority order" button when enough sources have run history
        val suggestFitOrderAvailable: Boolean
            get() = sourceFitStats.values.count { it.runCount >= SourceFitStats.MIN_RUNS_FOR_LABEL } >= MIN_SUGGEST_SOURCES
        // KMK <--
    }

    sealed interface Dialog {
        data object AddTag : Dialog
        data class EditTag(val tag: TagTaste) : Dialog
    }

    // KMK --> v0.7.19
    companion object {
        /** Minimum number of sources with enough run history before the "Suggest priority order" button appears. */
        private const val MIN_SUGGEST_SOURCES = 3
    }
    // KMK <--
}

internal const val SOURCES_TO_TRY_INSTALL_TIMEOUT_MS = 90_000L
// KMK <--
