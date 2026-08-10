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
import exh.recs.RecommendationLanguageAvailabilityPolicy
import exh.recs.RecommendationSourceFilter
import exh.recs.RecommendationSourceOrdering
import exh.recs.RecommendationSourceRunStatus
import exh.recs.RecommendationSourceRunStatusStore
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    // KMK: local-only exposure history clear
    private val clearRecommendationExposure: tachiyomi.domain.taste.interactor.ClearRecommendationExposure = Injekt.get(),
) : StateScreenModel<RecommendationsSettingsScreenModel.State>(State()) {

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
    // KMK: bounded Latest exploration share
    private val latestExplorationPercentPref = sourcePreferences.recommendationLatestExplorationPercent()
    // KMK: exposure window
    private val exposureWindowDaysPref = sourcePreferences.recommendationExposureWindowDays()
    private val lastSourceStatusesPref = sourcePreferences.recommendationLastSourceRunStatuses()
    // KMK --> v0.7.19
    private val sourceFitStatsPref = sourcePreferences.recommendationSourceFitStats()
    // KMK <--
    // KMK v0.8.14-fix1: read-only For You preview snapshot -- see RecommendationForYouPreviewSnapshotStore.
    private val forYouPreviewSnapshotPref = sourcePreferences.recommendationForYouPreviewSnapshot()

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
                // KMK: resolved on read too, so a
                // value persisted by an older build (or corrupted storage) renders as a real labelled
                // option instead of an unlabelled raw number -- same treatment resultBudget and
                // groupPreviewBudget already get below.
                minChapterCount = exh.recs.RecommendationMinChapterCountPolicy.resolve(minChapterCountPref.get()),
                // KMK <--
                // KMK --> v0.7.34
                enrichmentCap = enrichmentCapPref.get(),
                // KMK <--
                // KMK --> v0.8.2
                resultBudget = ForYouResultBudgetPolicy.validate(resultBudgetPref.get()),
                groupPreviewBudget = GroupPreviewBudgetPolicy.validate(groupPreviewBudgetPref.get()),
                // KMK
                latestExplorationPercent = exh.recs.RecommendationLatestBudgetPolicy.validate(latestExplorationPercentPref.get()),
                exposureWindowDays = exh.recs.RecommendationExposurePolicy.validateWindowDays(exposureWindowDaysPref.get()),
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
                sameMangaResultsPerSource = sourcePreferences.sameMangaMatchResultsPerSource().get(),
                sameMangaPreselectResults = sourcePreferences.sameMangaMatchPreselectResults().get(),
                bestVersionPreviewSampleSize = sourcePreferences.bestVersionPreviewSampleSize().get(),
                bestVersionAvoidFirstPages = sourcePreferences.bestVersionAvoidFirstPages().get(),
                // KMK <--
            )
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
                mutableState.update { it.copy(nonInstalledSuggestions = suggestions.toImmutableList()) }
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

    // KMK -->
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
    // KMK: validate the write. Previously any
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

    // KMK
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
    // KMK <--

    // KMK -->
    /** Persists the local exposure-history window. Local-only preference; never journalled to Action History (exposure itself never is). */
    fun setExposureWindowDays(value: Int) {
        val resolved = exh.recs.RecommendationExposurePolicy.validateWindowDays(value)
        exposureWindowDaysPref.set(resolved)
        mutableState.update { it.copy(exposureWindowDays = resolved) }
    }

    /**
     * Clears local exposure/ordering history only.
     *
     * KMK: this deliberately calls **only**
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
        journalPreferenceChange(exh.util.PreferenceJournalActionType.ENRICHMENT_CAP, "enrichmentCap", enrichmentCapPref, value) {
            enrichmentCapPref.set(value)
        }
        mutableState.update { it.copy(enrichmentCap = value) }
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
            // KMK: build before write, commit only after success.
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
            // KMK: build before write, commit only after success.
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
        if (!sourcePreferences.evaluationMode().get()) {
            screenModelScope.launchNonCancellable {
                setSourceEnabled.await(sourceId, enabled = currentlyDisabled)
            }
            return
        }
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
        mutableState.update { it.copy(installingSuggestionKeys = (it.installingSuggestionKeys + key).toImmutableSet()) }
        screenModelScope.launch {
            try {
                // KMK -->
                // takeWhile stops collection at any terminal InstallStep (Installed, Error, Idle)
                // without this, installExtension() flow never terminates and the coroutine hangs.
                val receiptId = exh.util.NonUndoableEvent.newId()
                extensionManager.installExtension(suggestion.extension)
                    .recordUserInitiatedInstall(id = receiptId) { sourcePreferences.evaluationMode().get() }
                    // KMK: typed
                    // PackageOperationReceipt alongside the visibility-only event above.
                    .recordPackageOperationReceipt(
                        kind = PackageOperationKind.INSTALL,
                        packageName = suggestion.extension.pkgName,
                        signatureHash = suggestion.extension.signatureHash,
                        versionCode = suggestion.extension.versionCode,
                        artifactUri = suggestion.extension.apkUrl,
                        id = receiptId,
                    ) { sourcePreferences.evaluationMode().get() }
                    .takeWhile { !it.isCompleted() }
                    .collect()
                // KMK <--
            } finally {
                mutableState.update { it.copy(installingSuggestionKeys = (it.installingSuggestionKeys - key).toImmutableSet()) }
            }
        }
    }

    fun installSuggestions(suggestions: List<NonInstalledSourceSuggestion>) {
        // KMK -->
        // Guard against overlapping bulk batches
        if (state.value.isBulkInstallingSuggestions) return
        // KMK <--
        val deduped = suggestions.distinctBy { "${it.extension.signatureHash}|${it.extension.pkgName}" }
        if (deduped.isEmpty()) return
        mutableState.update { it.copy(isBulkInstallingSuggestions = true) }
        screenModelScope.launch {
            try {
                for (suggestion in deduped) {
                    val key = suggestion.dismissalKey
                    mutableState.update { it.copy(installingSuggestionKeys = (it.installingSuggestionKeys + key).toImmutableSet()) }
                    try {
                        // KMK -->
                        // takeWhile terminates collection at any terminal step so the loop
                        // can advance to the next suggestion. Raw .collect {} never returns
                        // if the flow doesn't complete on its own.
                        val receiptId = exh.util.NonUndoableEvent.newId()
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
                            .takeWhile { !it.isCompleted() }
                            .collect()
                        // KMK <--
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Per-extension failure is isolated — continue with remaining suggestions
                    } finally {
                        mutableState.update { it.copy(installingSuggestionKeys = (it.installingSuggestionKeys - key).toImmutableSet()) }
                    }
                }
            } finally {
                mutableState.update { it.copy(isBulkInstallingSuggestions = false) }
            }
        }
    }

    fun dismissSuggestion(suggestion: NonInstalledSourceSuggestion) {
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
        journalPreferenceChange(exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING, "sameMangaResultsPerSource", preference, value) {
            preference.set(value)
        }
        mutableState.update { it.copy(sameMangaResultsPerSource = value) }
    }

    fun setSameMangaPreselectResults(enabled: Boolean) {
        val preference = sourcePreferences.sameMangaMatchPreselectResults()
        journalPreferenceChange(exh.util.PreferenceJournalActionType.SAME_MANGA_MATCHING, "sameMangaPreselectResults", preference, enabled) {
            preference.set(enabled)
        }
        mutableState.update { it.copy(sameMangaPreselectResults = enabled) }
    }

    fun setBestVersionPreviewSampleSize(value: Int) {
        val preference = sourcePreferences.bestVersionPreviewSampleSize()
        journalPreferenceChange(exh.util.PreferenceJournalActionType.BEST_VERSION_PREVIEW, "bestVersionPreviewSampleSize", preference, value) {
            preference.set(value)
        }
        mutableState.update { it.copy(bestVersionPreviewSampleSize = value) }
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
        sourcePreferences.likedRecommendationSourceKeys().set(RecommendationSourcePreferenceStore.serialize(state.liked))
        sourcePreferences.dislikedRecommendationSourceKeys().set(RecommendationSourcePreferenceStore.serialize(state.disliked))
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
        if (sourcePreferences.evaluationMode().get() && previous != next) {
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

    // KMK: source-quality marks are 3 preferences (liked/disliked/explicit
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
        sourcePreferences.likedSourceQualityKeys().set(RecommendationSourcePreferenceStore.serialize(state.liked))
        sourcePreferences.dislikedSourceQualityKeys().set(RecommendationSourcePreferenceStore.serialize(state.disliked))
        sourcePreferences.explicitSourceQualityKeys().set(RecommendationSourcePreferenceStore.serialize(state.explicit))
    }

    private fun journalSourceQualityChange(
        identityKey: String,
        actionType: exh.util.PreferenceJournalActionType,
        previous: exh.recs.sourceprefs.SourceQualityMarkPolicy.State,
        next: exh.recs.sourceprefs.SourceQualityMarkPolicy.State,
    ) {
        if (!sourcePreferences.evaluationMode().get() || previous == next) return
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
        // KMK --> v0.8.2: visible manga cards per ordinary For You source row
        val resultBudget: Int = ForYouResultBudgetPolicy.DEFAULT,
        // KMK v0.8.6: group-recommendation initial preview budget, independent of resultBudget above
        val groupPreviewBudget: Int = GroupPreviewBudgetPolicy.DEFAULT,
        // KMK <--
        // KMK: bounded Latest exploration share
        val latestExplorationPercent: Int = exh.recs.RecommendationLatestBudgetPolicy.DEFAULT,
        // KMK
        val exposureWindowDays: Int = exh.recs.RecommendationExposurePolicy.DEFAULT_WINDOW_DAYS,
        // KMK: clear-exposure-history action state.
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
        val sameMangaPreselectResults: Boolean = true,
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
// KMK <--
