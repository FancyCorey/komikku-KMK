package exh.recs.settings

// KMK -->
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.CatalogueSource
import exh.recs.ForYouResultBudgetPolicy
import exh.recs.GroupPreviewBudgetPolicy
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
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.taste.interactor.ClearRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.ClearRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.ClearTagTaste
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.interactor.SetRecommendationSourceEnabled
import tachiyomi.domain.taste.interactor.SetTagTaste
import tachiyomi.domain.taste.model.RatedMangaVisibility
import tachiyomi.domain.taste.model.TagPreference
import tachiyomi.domain.taste.model.TagTaste
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
    private val lastSourceStatusesPref = sourcePreferences.recommendationLastSourceRunStatuses()
    // KMK --> v0.7.19
    private val sourceFitStatsPref = sourcePreferences.recommendationSourceFitStats()
    // KMK <--

    init {
        val languages = RecommendationSourceFilter.normalizeLanguages(languagesPref.get())
        // KMK --> v0.7.7 follow-up: read fresh at init time; refreshed reactively on extension changes
        val initSources = sourceManager.getVisibleCatalogueSources()
        // KMK <--
        val filteredSources = RecommendationSourceFilter.filterForRecommendations(initSources, languages)
        val storedOrder = RecommendationSourceOrdering.parse(sourceOrderPref.get())
        val allInOrder = RecommendationSourceOrdering.applyAll(filteredSources, storedOrder)
        val availableLangs = RecommendationSourceFilter.availableLanguages(initSources)
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
                minChapterCount = minChapterCountPref.get(),
                // KMK <--
                // KMK --> v0.7.34
                enrichmentCap = enrichmentCapPref.get(),
                // KMK <--
                // KMK --> v0.8.2
                resultBudget = ForYouResultBudgetPolicy.validate(resultBudgetPref.get()),
                groupPreviewBudget = GroupPreviewBudgetPolicy.validate(groupPreviewBudgetPref.get()),
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
            }
        }

        // KMK --> v0.7.7 follow-up: refresh visible sources when extensions are installed or uninstalled
        screenModelScope.launch {
            extensionManager.installedExtensionsFlow.collectLatest { refreshVisibleSources() }
        }
        // KMK <--
    }

    fun setRatedMangaVisibility(visibility: RatedMangaVisibility) {
        ratedVisibilityPref.set(visibility)
        mutableState.update { it.copy(ratedMangaVisibility = visibility) }
    }

    fun setHideKnownManga(enabled: Boolean) {
        hideKnownMangaPref.set(enabled)
        mutableState.update { it.copy(hideKnownManga = enabled) }
    }

    // KMK --> v0.7.26
    fun setMinChapterCount(value: Int) {
        minChapterCountPref.set(value)
        mutableState.update { it.copy(minChapterCount = value) }
    }
    // KMK <--

    // KMK --> v0.7.34: enrichment cap setter
    fun setEnrichmentCap(value: Int) {
        enrichmentCapPref.set(value)
        mutableState.update { it.copy(enrichmentCap = value) }
    }
    // KMK <--

    // KMK --> v0.8.2: visible-card budget setter. Cache invalidation is automatic — the resolved
    // value is part of BrowsePersonalRecommendationsScreenModel's cache fingerprint, so the next
    // normal For You run detects the fingerprint mismatch and refetches instead of reusing a cache
    // sized for the old budget.
    fun setResultBudget(value: Int) {
        val validated = ForYouResultBudgetPolicy.validate(value)
        resultBudgetPref.set(validated)
        mutableState.update { it.copy(resultBudget = validated) }
    }
    // KMK <--

    // KMK --> v0.8.6: group-preview budget setter. Scoped exclusively to GROUP_PREVIEW rows — never
    // touches resultBudgetPref (For You) or any global-search setting. Applies on the next load;
    // the in-memory GROUP_PREVIEW cache keys on this value, so a changed budget naturally misses
    // the cache instead of reusing a differently-sized preview.
    fun setGroupPreviewBudget(value: Int) {
        val validated = GroupPreviewBudgetPolicy.validate(value)
        groupPreviewBudgetPref.set(validated)
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
        languagesPref.set(normalized)
        recomputeSourcesForLanguages(normalized)
        mutableState.update { it.copy(recommendationLanguages = normalized.toImmutableSet()) }
    }

    private fun recomputeSourcesForLanguages(languages: Set<String>) {
        val filteredSources = RecommendationSourceFilter.filterForRecommendations(sourceManager.getVisibleCatalogueSources(), languages)
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
        sourceOrderPref.set(RecommendationSourceOrdering.serialize(mergedOrder))

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
        sourceOrderPref.set("")
        val languages = state.value.recommendationLanguages
        val filteredSources = RecommendationSourceFilter.filterForRecommendations(sourceManager.getVisibleCatalogueSources(), languages.toSet())
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
        screenModelScope.launchNonCancellable {
            setTagTaste.await(displayName.trim(), preference)
        }
    }

    fun removeTagPreference(normalizedTag: String) {
        screenModelScope.launchNonCancellable {
            clearTagTaste.await(normalizedTag)
        }
    }

    // --- Source exclusion actions ---

    fun toggleSource(sourceId: Long) {
        val currentlyDisabled = sourceId in state.value.disabledSourceIds
        screenModelScope.launchNonCancellable {
            setSourceEnabled.await(sourceId, enabled = currentlyDisabled)
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
                extensionManager.installExtension(suggestion.extension)
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
                        extensionManager.installExtension(suggestion.extension)
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
        pref.set(NonInstalledSourceSuggestionStore.serialize(NonInstalledSourceSuggestionStore.dismiss(current, suggestion.dismissalKey)))
    }

    // KMK --> v0.7.0: Phase 1 – clear all dismissed source suggestions
    fun clearDismissedSuggestions() {
        sourcePreferences.dismissedNonInstalledRecommendationSources().set("")
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
        sourcePreferences.sameMangaMatchResultsPerSource().set(value)
        mutableState.update { it.copy(sameMangaResultsPerSource = value) }
    }

    fun setSameMangaPreselectResults(enabled: Boolean) {
        sourcePreferences.sameMangaMatchPreselectResults().set(enabled)
        mutableState.update { it.copy(sameMangaPreselectResults = enabled) }
    }

    fun setBestVersionPreviewSampleSize(value: Int) {
        sourcePreferences.bestVersionPreviewSampleSize().set(value)
        mutableState.update { it.copy(bestVersionPreviewSampleSize = value) }
    }

    fun setBestVersionAvoidFirstPages(enabled: Boolean) {
        sourcePreferences.bestVersionAvoidFirstPages().set(enabled)
        mutableState.update { it.copy(bestVersionAvoidFirstPages = enabled) }
    }
    // KMK <--

    // --- Source preference (like/dislike) actions ---

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
        likedPref.set(RecommendationSourcePreferenceStore.serialize(newLiked))
        dislikedPref.set(RecommendationSourcePreferenceStore.serialize(newDisliked))
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

    private fun applySourceQualityMark(key: String, poor: Boolean) {
        val likedPref = sourcePreferences.likedSourceQualityKeys()
        val dislikedPref = sourcePreferences.dislikedSourceQualityKeys()
        val explicitPref = sourcePreferences.explicitSourceQualityKeys()
        val current = exh.recs.sourceprefs.SourceQualityMarkPolicy.State(
            liked = RecommendationSourcePreferenceStore.parse(likedPref.get()),
            disliked = RecommendationSourcePreferenceStore.parse(dislikedPref.get()),
            explicit = RecommendationSourcePreferenceStore.parse(explicitPref.get()),
        )
        val next = if (poor) {
            exh.recs.sourceprefs.SourceQualityMarkPolicy.markPoor(current, key)
        } else {
            exh.recs.sourceprefs.SourceQualityMarkPolicy.markExplicit(current, key)
        }
        likedPref.set(RecommendationSourcePreferenceStore.serialize(next.liked))
        dislikedPref.set(RecommendationSourcePreferenceStore.serialize(next.disliked))
        explicitPref.set(RecommendationSourcePreferenceStore.serialize(next.explicit))
    }

    private fun clearSourceQualityMark(key: String) {
        val likedPref = sourcePreferences.likedSourceQualityKeys()
        val dislikedPref = sourcePreferences.dislikedSourceQualityKeys()
        val explicitPref = sourcePreferences.explicitSourceQualityKeys()
        val current = exh.recs.sourceprefs.SourceQualityMarkPolicy.State(
            liked = RecommendationSourcePreferenceStore.parse(likedPref.get()),
            disliked = RecommendationSourcePreferenceStore.parse(dislikedPref.get()),
            explicit = RecommendationSourcePreferenceStore.parse(explicitPref.get()),
        )
        val next = exh.recs.sourceprefs.SourceQualityMarkPolicy.clear(current, key)
        likedPref.set(RecommendationSourcePreferenceStore.serialize(next.liked))
        dislikedPref.set(RecommendationSourcePreferenceStore.serialize(next.disliked))
        explicitPref.set(RecommendationSourcePreferenceStore.serialize(next.explicit))
    }

    /** Management/recovery action: clears every source-quality mark across all sources. */
    fun clearAllSourceQualityMarks() {
        sourcePreferences.likedSourceQualityKeys().set("")
        sourcePreferences.dislikedSourceQualityKeys().set("")
        sourcePreferences.explicitSourceQualityKeys().set("")
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
            compareByDescending<CatalogueSource> {
                fitStats[it.id]?.fitLabel?.fitScore ?: -1
            }.thenBy { sources.indexOf(it) },
        )
        setSourceOrder((sortedWithData + withoutData).map { it.id })
    }
    // KMK <--

    // KMK --> v0.7.7 follow-up: called whenever installed extensions change so orderedSources and
    // availableLanguages reflect the current source list without requiring a screen restart.
    private fun refreshVisibleSources() {
        val freshSources = sourceManager.getVisibleCatalogueSources()
        val languages = state.value.recommendationLanguages.toSet()
        val filteredSources = RecommendationSourceFilter.filterForRecommendations(freshSources, languages)
        val storedOrder = RecommendationSourceOrdering.parse(sourceOrderPref.get())
        val allInOrder = RecommendationSourceOrdering.applyAll(filteredSources, storedOrder)
        val availableLangs = RecommendationSourceFilter.availableLanguages(freshSources)
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
        val orderedSources: ImmutableList<CatalogueSource> = persistentListOf(),
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
