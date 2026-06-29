package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getLongArray
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.taste.model.RatedMangaVisibility

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun sourceDisplayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun enabledLanguages() = preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    fun disabledSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun incognitoExtensions() = preferenceStore.getStringSet("incognito_extensions", emptySet())

    fun pinnedSources() = preferenceStore.getStringSet(
        // KMK -->
        PINNED_SOURCES_PREF_KEY,
        // KMK <--
        emptySet(),
    )

    fun lastUsedSource() = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)

    fun migrationSortingMode() = preferenceStore.getEnum("pref_migration_sorting", SetMigrateSorting.Mode.ALPHABETICAL)

    fun migrationSortingDirection() = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    fun hideInLibraryItems() = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    // KMK -->
    fun hideInLibraryFeedItems() = preferenceStore.getBoolean("feed_hide_in_library_items", false)
    // KMK <--

    @Deprecated("Use ExtensionRepoRepository instead", replaceWith = ReplaceWith("ExtensionRepoRepository.getAll()"))
    fun extensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    fun extensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun trustedExtensions() = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    fun globalSearchFilterState() = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    fun migrationSources() = preferenceStore.getLongArray("migration_sources", emptyList())

    fun migrationFlags() = preferenceStore.getObjectFromInt(
        key = "migration_flags",
        defaultValue = MigrationFlag.entries.toSet(),
        serializer = { MigrationFlag.toBit(it) },
        deserializer = { value: Int -> MigrationFlag.fromBit(value) },
    )

    fun migrationDeepSearchMode() = preferenceStore.getBoolean("migration_deep_search", false)

    fun migrationPrioritizeByChapters() = preferenceStore.getBoolean("migration_prioritize_by_chapters", false)

    fun migrationHideUnmatched() = preferenceStore.getBoolean("migration_hide_unmatched", false)

    fun migrationHideWithoutUpdates() = preferenceStore.getBoolean("migration_hide_without_updates", false)

    // KMK -->
    fun migrationSmartSearchSingleEntry() = preferenceStore.getBoolean("migration_smart_search_single_entry", false)

    fun globalSearchPinnedState() = preferenceStore.getEnum(
        Preference.appStateKey("global_search_pinned_toggle_state"),
        SourceFilter.PinnedOnly,
    )

    fun disabledRepos() = preferenceStore.getStringSet("disabled_repos", emptySet())
    // KMK <--

    // SY -->
    fun enableSourceBlacklist() = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    fun sourcesTabCategories() = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    fun sourcesTabCategoriesFilter() = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    fun sourcesTabSourcesInCategories() = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun dataSaver() = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    fun dataSaverIgnoreJpeg() = preferenceStore.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = preferenceStore.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = preferenceStore.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = preferenceStore.getBoolean("data_saver_image_format_jpeg", false)

    fun dataSaverServer() = preferenceStore.getString("data_saver_server", "")

    fun dataSaverColorBW() = preferenceStore.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
    }

    fun allowLocalSourceHiddenFolders() = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)

    fun preferredMangaDexId() = preferenceStore.getString("preferred_mangaDex_id", "0")

    fun mangadexSyncToLibraryIndexes() = preferenceStore.getStringSet(
        "pref_mangadex_sync_to_library_indexes",
        emptySet(),
    )

    fun recommendationSearchFlags() = preferenceStore.getInt("rec_search_flags", Int.MAX_VALUE)
    // SY <--

    // KMK -->
    fun relatedMangas() = preferenceStore.getBoolean("related_mangas", true)

    /**
     * When enabled, the recommendations screen searches all installed extensions by the source
     * manga's genres and adds one result row per extension (capped at 20 extensions).
     * Defaults to false (opt-in) so users with many installed extensions are not surprised by
     * a large number of network calls on first open.
     */
    fun recommendationCrossExtensionSearch() = preferenceStore.getBoolean("rec_cross_extension_search", false)

    fun recommendationRatedMangaVisibility() = preferenceStore.getEnum(
        "recommendation_rated_manga_visibility",
        RatedMangaVisibility.HIDE_DISLIKED_ONLY,
    )

    // KMK -->
    /** Languages to search for For You recommendations. Defaults to EN-only. */
    fun recommendationSourceLanguages() = preferenceStore.getStringSet("recommendation_source_languages", setOf("en"))

    /** Comma-separated source ids in user-defined priority order for For You recommendations. */
    fun recommendationSourceOrder() = preferenceStore.getString("recommendation_source_order", "")

    /** Semicolon-separated "sourceId=strategyName" pairs persisting the last-successful query strategy per source. */
    fun recommendationSourceStrategies() = preferenceStore.getString("recommendation_source_strategies", "")

    /** When true, hides manga that are already rated, in library, started, or read from For You results. */
    fun recommendationHideKnownManga() = preferenceStore.getBoolean("recommendation_hide_known_manga", true)

    /** Compact serialized last For You source run statuses for display in Recommendation Settings. */
    fun recommendationLastSourceRunStatuses() = preferenceStore.getString("recommendation_last_source_run_statuses", "")

    // KMK --> v0.7.19: rolling source fit stats accumulated across For You runs
    /** Compact serialized rolling source fit stats for the Source Priority section in Recommendation Settings. */
    fun recommendationSourceFitStats() = preferenceStore.getString("recommendation_source_fit_stats", "")
    // KMK <--

    // KMK --> v0.7.26: minimum chapter count filter for For You
    /** Minimum locally-known chapter count for a manga to appear in For You. 0 = no filter. */
    fun recommendationMinChapterCount() = preferenceStore.getInt("recommendation_min_chapter_count", 0)
    // KMK <--

    // KMK --> v0.7.34: configurable enrichment cap for For You sources
    /** Number of candidate manga per source to enrich with full metadata. Default 5; boosted sources get 2×. Max 20. */
    fun recommendationEnrichmentCap() = preferenceStore.getInt("recommendation_enrichment_cap", 5)
    // KMK <--

    /** Semicolon-separated dismissal keys for non-installed source suggestions. Format: signatureHash|pkgName|sourceId */
    fun dismissedNonInstalledRecommendationSources() = preferenceStore.getString("dismissed_non_installed_rec_sources", "")

    /** Semicolon-separated liked recommendation source keys. Format: i|sourceId or a|signatureHash|pkgName[|sourceId] */
    fun likedRecommendationSourceKeys() = preferenceStore.getString("liked_recommendation_source_keys", "")

    /** Semicolon-separated disliked recommendation source keys. Format: i|sourceId or a|signatureHash|pkgName[|sourceId] */
    fun dislikedRecommendationSourceKeys() = preferenceStore.getString("disliked_recommendation_source_keys", "")

    /** When true, hides clearly explicit porn/hentai sources from Browse and Sources To Try. Does not affect ecchi-only sources. */
    fun blockExplicitPornHentaiSources() = preferenceStore.getBoolean("block_explicit_porn_hentai_sources", false)

    // KMK --> v0.6.20: seen manga + reassessment prefs
    /** Semicolon-separated "sourceId|url" pairs identifying recommendation manga the user has marked as seen/already read. */
    fun seenRecommendationMangaKeys() = preferenceStore.getString("seen_recommendation_manga_keys", "")

    /** Total rated manga count at the time of the last source-evaluation reassessment baseline. 0 = never set. */
    fun sourceEvaluationLastReassessmentRatingCount() = preferenceStore.getInt("source_evaluation_last_reassessment_rating_count", 0)

    /** Epoch-ms timestamp of the last source-evaluation reassessment baseline. 0 = never set. */
    fun sourceEvaluationLastReassessmentAt() = preferenceStore.getLong("source_evaluation_last_reassessment_at", 0L)

    // KMK --> v0.7.6: continuation cursor for source evaluation batches
    /** Serialized [SourceEvaluationCursor] for continuing a paused evaluation queue. Blank = no cursor. */
    fun sourceEvaluationContinuationCursor() = preferenceStore.getString("source_evaluation_continuation_cursor", "")

    // KMK --> v0.7.11: source evaluation consent
    /** When true, the user has acknowledged the Source Evaluation pre-run warning. */
    fun sourceEvaluationConsentGiven() = preferenceStore.getBoolean("source_evaluation_consent_given", false)
    // KMK <--

    // KMK --> v0.7.31: C3 — rated count at the time evaluation was last launched (for profile-changed prompt)
    /** Total rated manga count when source evaluation was last started. -1 = never run. */
    fun sourceEvaluationLastRunRatingCount() = preferenceStore.getInt("source_evaluation_last_run_rating_count", -1)
    // KMK <--

    // KMK --> SEC-01 v0.7.16: leftover extension detection after process death
    /** Package name of a Shizuku-installed extension that was left behind by a process-death interruption. Blank = none. */
    fun sourceEvaluationLeftoverPkg() = preferenceStore.getString("source_evaluation_leftover_pkg", "")
    // KMK <--
    // KMK <--

    // KMK --> v0.7.8: same-manga matching and best-version comparison settings
    /** Max results per source for bounded same-manga workflows (Love/Like/Dislike/Seen/Favorite/Best-version). Valid: 1, 2, 5, 10. Default 2. */
    fun sameMangaMatchResultsPerSource() = preferenceStore.getInt("same_manga_match_results_per_source", 2)

    /** When true, same-manga results start selected. When false, start unselected. Origin is never selected. */
    fun sameMangaMatchPreselectResults() = preferenceStore.getBoolean("same_manga_match_preselect_results", true)

    /** Number of pages sampled per candidate chapter in Best Version preview. Valid: 2, 5, 10. Default 5. */
    fun bestVersionPreviewSampleSize() = preferenceStore.getInt("best_version_preview_sample_size", 5)

    /** When true, automatic sample skips first 1–2 pages (avoids credits/covers/ads). */
    fun bestVersionAvoidFirstPages() = preferenceStore.getBoolean("best_version_avoid_first_pages", true)
    // KMK <--
    // KMK <--

    companion object {
        const val PINNED_SOURCES_PREF_KEY = "pinned_catalogues"
    }
    // KMK <--
}
