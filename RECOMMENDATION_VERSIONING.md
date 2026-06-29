# Recommendation Feature Versioning

Date: 2026-06-13

Status: living project note for the Komikku personal recommendation fork work.

## Purpose

The recommendation work is becoming a long-running feature branch rather than a one-off patch. This file gives the work its own small version trail without confusing it with upstream Komikku's app version.

Upstream app versioning still lives in:

- `app/build.gradle.kts`
  - `versionCode`
  - `versionName`

The recommendation feature version is a local project label for planning, testing, and handoff notes. It does not need to change Android install/update behavior by itself.

## Naming

Use this feature name in markdown notes and APK handoff notes:

```text
KMK Personal Recommendations
```

Short label:

```text
KMK-Recs
```

## Version Format

Use semantic-style feature versions:

```text
KMK-Recs vMAJOR.MINOR.PATCH
```

Meaning:

- `MAJOR`: breaking data model or behavior change, backup incompatibility risk, or large UI direction change.
- `MINOR`: new user-visible recommendation capability.
- `PATCH`: bug fix, crash fix, spacing/polish, small scoring correction, or documentation update.

## Topic Continuity Rule

Keep related work on the same version track instead of jumping around.

Examples:

- Extension/source discovery work belongs to the v0.6 line.
- Follow-up fixes or improvements to that same system should use `v0.6.1`, `v0.6.2`, `v0.6.3`, etc.
- A genuinely new topic can move to the next minor/major line.
- If an older topic is revisited later, continue that topic's existing line rather than assigning a new unrelated version.

This keeps the version trail readable during long-running development and prevents feature plans from flip-flopping between unrelated version numbers.

## Current Version Trail

### KMK-Recs v0.1.0

Initial personal recommendation system:

- manga taste ratings,
- tag taste preferences,
- For You Browse tab,
- capped cross-extension recommendation search,
- recommendation cache,
- backup/restore/sync support.

### KMK-Recs v0.2.0

Rating and For You behavior refinement:

- source/url taste identity,
- rated manga visibility setting,
- hide disliked only default,
- hide empty For You rows,
- Browse tab visibility preferences.

### KMK-Recs v0.3.0

Recommendation quality and efficiency pass:

- manual recommendation source ordering,
- top-three boosted sources,
- capped metadata enrichment before scoring,
- query strategy planning,
- alias-aware genre filter matching,
- cache version `personal_v3`.

### KMK-Recs v0.3.1

Crash and polish patch:

- fix source priority drag crash caused by LazyColumn indexes including non-source rows,
- harden stored source order parsing against duplicate ids,
- improve vertical spacing for tag preference chips.

### KMK-Recs v0.3.2

Recommendation source language filter:

- add recommendation-specific language preference defaulting to EN (`recommendationSourceLanguages`).
- add `RecommendationSourceFilter` helper — EN-only filtering, Local Source exclusion, available language list.
- filter visible catalogue sources by recommendation language before manual ordering and top-20 cap in For You.
- filter source list in Recommendation Settings to show only selected-language sources.
- add language chip selector to Recommendation Settings screen (available languages auto-derived from installed sources).
- add `mergeVisibleOrder` to `RecommendationSourceOrdering` — preserves hidden-language source positions in stored order when visible EN subset is reordered.
- include selected languages in profile fingerprint so cache invalidates on language change.
- prevent non-English MangaFire variants (PT-BR, JA, ES-419, ES, PT) from consuming top-20 For You source slots when EN-only is selected.
- top-3 boosted sources selected from language-filtered set.

Files changed:

- `SourcePreferences.kt` — added `recommendationSourceLanguages()`
- `RecommendationSourceFilter.kt` — new file
- `RecommendationSourceOrdering.kt` — added `mergeVisibleOrder()`
- `BrowsePersonalRecommendationsScreenModel.kt` — language filter in `load()`, languages in `profileFingerprint()`
- `RecommendationsSettingsScreenModel.kt` — `recommendationLanguages`/`availableLanguages` state, `toggleRecommendationLanguage()`, language-aware `recomputeSourcesForLanguages()`, merge in `setSourceOrder()`
- `RecommendationsSettingsScreen.kt` — `LanguageSelectorContent` composable, language section in settings list
- `i18n-kmk/strings.xml` — added `rec_source_languages`, `rec_source_languages_summary`

Tests added:

- `RecommendationSourceFilterTest.kt` — 11 tests covering EN-only, multi-language, empty fallback, Local Source exclusion, availableLanguages
- `RecommendationSourceOrderingTest.kt` — 5 new `mergeVisibleOrder` tests (18 total)

APK: `Komikku-v1.13.6-kmk.3.2-debug.apk`

### KMK-Recs v0.4.0

For You source ordering fix and Combined Picks row:

- fix For You rows displaying in alphabetical order instead of source priority order.
- add `sourceOrder: PersistentList<CatalogueSource>` to screen model `State` so the UI renders rows in the same order sources were selected/prioritized.
- add `CombinedPicksAccumulator` — pure helper that collects recommendations from each source result and ranks by `bestScore + occurrenceBonus + boostedBonus`.
- add synthetic "Combined Picks" row to For You that appears first, derived from already-fetched source results (no extra crawl).
- Combined Picks keyed by `(manga.source, manga.url)` — same-title entries from different sources are kept separate.
- Combined Picks not included in cross-source title dedupe; each row dedupes independently.
- Combined Picks row header click is no-op in this pass; individual manga taps work normally.
- Combined Picks capped at 20 results matching boosted source limit.
- fix race condition in `updateItem()` — items mutation now inside `mutableState.update {}` lambda.

Files changed:

- `BrowsePersonalRecommendationsScreenModel.kt` — `State` gains `sourceOrder`/`combinedResult`, `updateItem()` feeds accumulator atomically, `load()` resets accumulator and sets `currentBoostedSourceIds`, `COMBINED_ROW_CAP` constant
- `BrowsePersonalRecommendationsTab.kt` — renders Combined Picks row first, iterates `state.sourceOrder` for priority-ordered source rows
- `CombinedPicksAccumulator.kt` — new pure class
- `i18n-kmk/strings.xml` — added `rec_combined_picks_title`, `rec_combined_picks_subtitle`, `rec_combined_picks_matched`

Tests added:

- `CombinedPicksAccumulatorTest.kt` — 12 tests covering occurrence count, separate-source deduplication, score merging, occurrence bonus, boosted bonus, cap, clear, matched group merging, constants

APK: `Komikku-v1.13.6-kmk.4.0-debug.apk`

### KMK-Recs v0.4.1

Top Picks naming, local known-manga filter, conservative cross-source duplicate merging, and exception handling:

- renamed "Combined Picks" row to "Top Picks" (user-facing strings only; internal class is `CombinedPicksAccumulator`).
- added conservative cross-source work-key deduplication: two candidates from different sources merge only if normalized title matches exactly AND normalized author OR artist also matches exactly and is non-blank. Title-only matches never merge.
- add `hideKnownManga: Boolean` preference (`recommendation_hide_known_manga`, default enabled).
- add `GetKnownRecommendationMangaIds` interactor + SQLDelight `getKnownRecommendationMangaIds` query — identifies locally known manga (rated, in library, read chapters, partial read, or history) by manga ID set.
- apply known-manga filter before scoring in `BrowsePersonalRecommendationsScreenModel.searchSource()`.
- all new quality filters fail-open: known-manga lookup failure logs and keeps all candidates.
- conservative work-key failures `runCatching`-protected; malformed metadata keeps entries separate.
- add "Hide known manga" toggle in Recommendation Settings screen.
- rankings: `matchedGroups.size` added as a tiebreaker after occurrence count.
- `CombinedPicksAccumulator.clear()` also clears `workKeyToPrimaryKey` map.
- `Bucket.manga` is now `var` to support updating the display representative on work-key merge.

Files changed:

- `i18n-kmk/strings.xml` — removed `rec_combined_picks_*`, added `rec_top_picks_*`, `rec_hide_known_manga`, `rec_hide_known_manga_summary`
- `CombinedPicksAccumulator.kt` — conservative work-key dedup, `var manga`, updated `clear()`, ranked `matchedGroups.size` as tiebreaker
- `BrowsePersonalRecommendationsTab.kt` — string refs updated to `rec_top_picks_*`
- `data/.../mangas.sq` — added `getKnownRecommendationMangaIds` query
- `domain/.../manga/repository/MangaRepository.kt` — added `getKnownRecommendationMangaIds`
- `data/.../manga/MangaRepositoryImpl.kt` — implemented
- `domain/.../taste/interactor/GetKnownRecommendationMangaIds.kt` — new interactor
- `KMKDomainModule.kt` — registered interactor
- `SourcePreferences.kt` — added `recommendationHideKnownManga()`
- `BrowsePersonalRecommendationsScreenModel.kt` — injected `GetKnownRecommendationMangaIds`, `hideKnownManga` param in `searchSource()`, fail-open known filter
- `RecommendationsSettingsScreenModel.kt` — `hideKnownManga` state + `setHideKnownManga()`
- `RecommendationsSettingsScreen.kt` — `HideKnownMangaRow` composable, new toggle in settings

Tests updated:

- `CombinedPicksAccumulatorTest.kt` — 24 tests (was 12): added 12 new tests covering work-key dedup, conservative merge behavior, score preservation, work-key helper edge cases

APK: `Komikku-v1.13.6-kmk.4.1-debug.apk`

### KMK-Recs v0.4.2

Cache correctness, refined Top Picks duplicate identity, and local What's New:

- `hideKnownManga` added to profile fingerprint so changing the setting invalidates cache.
- `loadFromCache()` now re-applies the known-manga filter (fail-open, same pattern as live search).
- `conservativeWorkKey(manga): String?` replaced with `conservativeWorkKeys(manga): Set<String>` — generates independent `title|author` and `title|artist` keys; merges on any shared key (exact title + author OR exact title + artist).
- Added `KmkRecsReleaseNotes` object (`VERSION_CODE = 402`, `VERSION_NAME`, `MARKDOWN`).
- Added `KmkRecsWhatsNewDialog` — shown at launch when `kmk_recs_last_seen_version_code < 402`; shown as `else if` after upstream changelog so dialogs never overlap.
- Added `KmkRecsWhatsNewScreen` — Voyager screen rendering KMK release notes locally; marks version as seen on open.
- Added "KMK-Recs What's new" entry to About screen.
- Added `kmk_recs_whats_new` and `kmk_recs_updated` strings to `i18n-kmk`.

Files changed:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/CombinedPicksAccumulator.kt`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (new)
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt` (new)
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt` (new)
- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt`
- `app/src/test/java/exh/recs/CombinedPicksAccumulatorTest.kt` — 32 tests (was 24)
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Tests run:

- `CombinedPicksAccumulatorTest` — 32 tests, BUILD SUCCESSFUL
- Full `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.4.2-debug.apk`

### KMK-Recs v0.4.3

Adaptive For You fill, per-source status diagnostics, Top Picks drill-down, and What's New cleanup:

- Replaced fixed `take(20)` source cap with adaptive batch fill: batches of 5, max 40 attempted, stops when 20 useful rows are found.
- Boosted sources remain fixed as the top-3 priority sources regardless of adaptive fill progress.
- After each For You run, per-source statuses are persisted to `recommendation_last_source_run_statuses` preference.
- Recommendation Settings shows last-run status for each source: Shown (with count), NoMatches, FilteredOut, Error, OutsideAttemptLimit, or Not checked yet.
- Top Picks header in For You is now tappable and opens a new `TopPicksScreen` showing up to 50 ranked candidates from already-fetched results — no second crawl.
- `WhatsNewScreen.onOpenInBrowser` made nullable; the browser button only renders when non-null.
- `KmkRecsWhatsNewScreen` now passes `null` for `onOpenInBrowser` — no more no-op browser button.
- `KmkRecsReleaseNotes.VERSION_CODE` bumped to 403; What's New notes now contain user-facing changes only.

Files changed:

- `app/src/main/java/exh/recs/RecommendationSourceRunStatus.kt` (new — Phase 1/2 from previous session)
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` — added `recommendationLastSourceRunStatuses()`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` — adaptive batch fill, `SourceSearchOutcome`, `State` gains `combinedDetailResult` and `sourceStatuses`, `updateItem()` ranks detail and row separately, `searchSource()` returns outcome with status
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt` — wired `onClickTopPicks` callback, navigates to `TopPicksScreen`
- `app/src/main/java/exh/recs/TopPicksScreen.kt` (new) — `TopPicksScreen` and `TopPicksScreenModel`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — `State` gains `sourceStatuses`, reads and parses from preferences on init
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` — `SourcePriorityItem` shows last-run status in subtitle
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=403, user-facing notes only
- `app/src/main/java/eu/kanade/presentation/more/WhatsNewScreen.kt` — `onOpenInBrowser` made nullable
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt` — passes null for onOpenInBrowser
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — added source status strings
- `app/src/test/java/exh/recs/RecommendationSourceRunStatusStoreTest.kt` (new) — 9 tests

Tests run:

- `exh.recs.*` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.4.3-debug.apk`

### KMK-Recs v0.4.4

Status polish and beta hardening:

- Added `HiddenByDuplicateHandling` to `RecommendationSourceStatus` enum.
- Extracted `adjustStatusesForDedupe()` pure helper (`RecommendationStatusAdjuster.kt`) — reclassifies `Shown` sources that have zero cards visible after cross-source display dedupe.
- Post-dedupe status adjustment runs after the For You batch loop in `BrowsePersonalRecommendationsScreenModel.load()`.
- `RecommendationsSettingsScreenModel` subscribes to `lastSourceStatusesPref.changes()` — the settings screen now live-updates when For You finishes running.
- Added "Statuses below are from the last For You refresh." note above the source list in Recommendation Settings.
- `HiddenByDuplicateHandling` case added to status display in `RecommendationsSettingsScreen`.
- `TopPicksScreen` gains `isPartial: Boolean` param, a loading spinner state, an empty-state message, and a full-width partial-results banner when `isPartial == true`.
- `BrowsePersonalRecommendationsTab` computes `isPartial` and passes it to `TopPicksScreen`.
- `KmkRecsReleaseNotes.VERSION_CODE` bumped to 404.

Files changed:

- `app/src/main/java/exh/recs/RecommendationSourceRunStatus.kt` — added `HiddenByDuplicateHandling`
- `app/src/main/java/exh/recs/RecommendationStatusAdjuster.kt` (new)
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
- `app/src/main/java/exh/recs/TopPicksScreen.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 4 new strings
- `app/src/test/java/exh/recs/RecommendationSourceRunStatusStoreTest.kt` — 10 tests (was 9)
- `app/src/test/java/exh/recs/AdjustStatusesForDedupeTest.kt` (new) — 9 tests

Tests run:

- `exh.recs.RecommendationSourceRunStatusStoreTest` — 10 tests, all PASSED
- `exh.recs.AdjustStatusesForDedupeTest` — 9 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.4.4-debug.apk`

### KMK-Recs v0.5.0

Cross-extension rating matching workflow (rating only; favorite + link groups deferred to v0.5.1):

- Added "Love other versions", "Like other versions", and "Dislike other versions" actions to the manga detail rating dropdown.
- New `CrossExtensionMatchScreen` and `CrossExtensionMatchScreenModel` in `exh.recs.matching`.
- Matching search respects recommendation language filter and source priority order. Local Source excluded.
- Per-source result cap of 2 in matching workflow only (plan specified 5; changed to 2 to keep confirmation lists tighter). Normal global search remains uncapped (`SearchScreenModel.perSourceResultLimit` defaults to `null`).
- All returned candidates auto-selected by default. Origin manga excluded. Manual deselection preserved on result refresh.
- New `SetMangaTasteBatch` interactor applies `MangaRating` to a list of manga by `(source, url)` identity.
- Matching workflow does not write any global search preferences.
- Favorite mode, cross-source link groups, and backup for link groups deferred to v0.5.1.

Files changed:

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt` — added `perSourceResultLimit` property
- `domain/src/main/java/tachiyomi/domain/taste/interactor/SetMangaTasteBatch.kt` (new)
- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt` (new)
- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt` (new)
- `app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt`
- `app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`
- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 8 new strings
- `app/src/test/java/exh/recs/matching/CrossExtensionMatchSelectionTest.kt` (new) — 8 tests

Tests run:

- `exh.recs.matching.CrossExtensionMatchSelectionTest` — 8 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.5.0-debug.apk`

### KMK-Recs v0.5.1

Matching cap correction and origin filter fix:

- Origin manga is now filtered before the 2-result per-source cap. If a source returns `[origin, A, B, C]`, the screen shows `[A, B]`, not `[A]`.
- `toggleSelection()` defensively rejects the origin key even from stale UI events.
- Added `isOrigin()` private helper in `CrossExtensionMatchScreenModel` for clean reuse.
- All other v0.5.0 behavior unchanged: cap stays at 2, normal global search remains uncapped.

Files changed:

- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt` — `isOrigin()` helper, `filterNot(::isOrigin)` before `.take()`, defensive guard in `toggleSelection()`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — added origin filter note to v0.5.1 What's New entry
- `app/src/test/java/exh/recs/matching/CrossExtensionMatchSelectionTest.kt` — 2 new tests: origin-before-cap behavior and defensive toggle guard (10 tests total)
- `docs/recommendations/CURRENT_STATE.md` — updated version, APK, cross-extension section, test count
- `docs/recommendations/NEXT_WORK.md` — removed v0.5.1 bug entry; updated future work labels
- `docs/recommendations/README.md` — moved v0.5.1 plan to implemented; added implementation report entry
- `docs/recommendations/KMK_RECS_V0_5_1_MATCHING_CAP_AND_ORIGIN_FILTER_IMPLEMENTATION.md` (new)

Tests run:

- `exh.recs.matching.CrossExtensionMatchSelectionTest` — 10 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.5.1-debug.apk`

### KMK-Recs v0.6.0

Non-installed extension discovery — Sources To Try:

- Added "Sources To Try" section to Recommendation Settings.
- Suggestions are generated from available extension repository metadata using: language match, same repo as installed sources, source name similarity.
- Scores are capped at 0.69. HIGH confidence (0.70+) reserved for installed-source fit learning.
- Original `Extension.Available` identity is preserved in each suggestion so `installExtension()` receives the correct package — not a `GetExtensionsByType` synthetic copy.
- Dismissed suggestion keys persisted as `SourcePreferences.dismissedNonInstalledRecommendationSources()`.
- No auto-install, no source-specific rules, no website crawling.
- `NonInstalledSourceSuggestionScorer` is pure (no Android dependencies) — fully unit-testable.
- Installed-source fit learning is not implemented yet; suggestions are labeled "Install to test with For You."

Files changed:

- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestion.kt` (new)
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt` (new)
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionStore.kt` (new)
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt` (new)
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` — added `dismissedNonInstalledRecommendationSources()`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — suggestions state, install/dismiss/expand actions
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` — Sources To Try section and `SourceSuggestionItem` composable
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=600
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 10 new strings
- `app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt` (new) — 12 tests

Tests run:

- `exh.recs.discovery.NonInstalledSourceSuggestionScorerTest` — 12 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.0-debug.apk`

### KMK-Recs v0.6.1

Sources To Try hardening — selective evidence gate:

- Sources To Try section is now selective: a source only appears when it has `SimilarToInstalledSource` evidence.
- Language match, same repo, base URL, and generic keywords reclassified as eligibility filters only — no longer score, no longer qualify.
- `hasMeaningfulEvidence()` gates the scorer: returns `null` if no `SimilarToInstalledSource` reason is found.
- Similarity rules: exact normalized name match, containment (both names ≥ 8 chars), or distinctive token overlap (token length ≥ 5, not generic word).
- Generic single-word names (`manga`, `scans`, `manhwa`, etc.) excluded from both sides of comparison.
- `GetNonInstalledSourceSuggestions` now adds `recommendationSourceLanguages().changes()` as a 5th combine source so language changes trigger immediate refresh.
- Empty state shown when no qualified suggestions exist.
- Score constants: SCORE_EXACT_SOURCE_NAME=0.60, SCORE_SIMILAR_SOURCE_NAME=0.50, SCORE_CAP=0.69.

Files changed:

- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt` — rewritten, evidence gate
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt` — 5th combine source for language pref
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=601
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — updated `rec_sources_to_try_empty`
- `app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt` — rewritten, 18 tests

Tests run:

- `exh.recs.discovery.NonInstalledSourceSuggestionScorerTest` — 18 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.1-debug.apk`

### KMK-Recs v0.6.2

Source Like/Dislike preferences for installed recommendation sources and non-installed Sources To Try suggestions:

- Added thumbs-up / thumbs-down icon buttons to installed source rows and Sources To Try suggestion rows in Recommendation Settings.
- Liked non-installed suggestions score 0.68 (`SCORE_USER_LIKED`), bypass the evidence gate, and rank above neutral metadata matches. Reason: `UserLikedSource`.
- Disliked non-installed suggestions are excluded from Sources To Try.
- Disliked installed sources are excluded from For You source candidate list (added to effective disabled set in `BrowsePersonalRecommendationsScreenModel.load()`). Fingerprint updated so dislike changes invalidate cache.
- Dismiss remains a separate lightweight "not now" action.
- Key format: `i|sourceId` for installed, `a|signatureHash|pkgName[|sourceId]` for available.
- Like and dislike are mutually exclusive. Tapping the active icon resets to neutral.
- Preferences: `SourcePreferences.likedRecommendationSourceKeys()`, `SourcePreferences.dislikedRecommendationSourceKeys()`.
- New package `exh/recs/sourceprefs/`: `RecommendationSourcePreference` (enum), `RecommendationSourcePreferenceStore` (parse/serialize/like/dislike/reset/key-building).

Files changed:

- `app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreference.kt` (new)
- `app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStore.kt` (new)
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestion.kt` — added `UserLikedSource` reason
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt` — liked/disliked key sets, SCORE_USER_LIKED, UserLikedSource bypass
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt` — liked/disliked pref flows in combine
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` — effectiveDisabledIds
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — like/dislike state and actions
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` — ThumbUp/ThumbDown icon buttons
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` — two new preference keys
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=602
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 4 new strings
- `app/src/test/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStoreTest.kt` (new) — 13 tests
- `app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt` — 5 new tests (23 total)

Tests run:

- `exh.recs.sourceprefs.RecommendationSourcePreferenceStoreTest` — 13 tests, all PASSED
- `exh.recs.discovery.NonInstalledSourceSuggestionScorerTest` — 23 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.2-debug.apk`

### KMK-Recs v0.6.3

Extension/source settings polish:

- "Install visible suggestions" bulk install button in Sources To Try (sequential, deduped by extension, per-suggestion install-in-progress tracking).
- Updated Like/Dislike accessibility labels: "Like for For You" / "Dislike for For You" on installed source rows; "Like source suggestion" / "Dislike source suggestion" on suggestion rows.
- Scope note below Sources To Try explaining the difference between installed source dislikes (For You only) and suggestion dislikes (hide future suggestions).
- Source priority ordering verified correct; 1 new gap test added: `parse drops malformed ids`.
- Fixed pre-existing test compilation failure: `StubMangaRepository` and `GetTasteProfileTest` were missing stubs for newly added `TasteRepository`/`MangaRepository` methods.

Files changed:

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — `installingSuggestionKeys`, `isBulkInstallingSuggestions` state; `installSuggestions()`, updated `installSuggestion()`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` — bulk install button, scope note, `isInstalling` param, updated content descriptions
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=603
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 7 new strings
- `app/src/test/java/exh/recs/RecommendationSourceOrderingTest.kt` — 1 new test (malformed id parse)
- `app/src/test/java/exh/taste/StubMangaRepository.kt` — stub for `getKnownRecommendationMangaIds`
- `app/src/test/java/exh/taste/GetTasteProfileTest.kt` — stubs for 3 new TasteRepository methods

Tests run:

- `exh.recs.RecommendationSourceOrderingTest` — 17 tests (was 16), all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.3-debug.apk`

### KMK-Recs v0.6.4

Sources To Try bulk install fix:

- Fixed `installSuggestion()` and `installSuggestions()`: replaced `.collect {}` with `.takeWhile { !it.isCompleted() }.collect()`. Raw `.collect {}` blocked indefinitely because `installExtension()` flow does not self-terminate after `Installed`; `takeWhile` ends collection at any terminal `InstallStep` (Installed, Error, or Idle).
- Added duplicate-bulk-batch guard: `installSuggestions()` returns early if `isBulkInstallingSuggestions` is already true.
- Moved per-suggestion `installingSuggestionKeys` cleanup to `finally` block.
- Moved `isBulkInstallingSuggestions = false` to outer `finally` block.
- Added explicit `CancellationException` rethrow so coroutine cancellation propagates correctly.

Pattern source: `ExtensionsScreenModel.collectToInstallUpdate()` (upstream Extensions tab).

Files changed:

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — `installSuggestion()`, `installSuggestions()`, new imports
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=604

Tests run:

- `:app:testDebugUnitTest --offline` — BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug --offline` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.4-debug.apk`

### KMK-Recs v0.6.5

Sources To Try selective install:

- Added "Select" button next to "Install visible suggestions" to enter selection mode.
- In selection mode: each suggestion row shows a leading Checkbox; tapping the card or checkbox toggles selection.
- "Install selected (N)" button installs only chosen suggestions using the safe `installSuggestions()` path (v0.6.4 fix).
- "Cancel" exits selection mode and clears the selection without installing.
- Install and Dismiss buttons hidden in selection mode to prevent competing install paths. Like/Dislike remain visible.
- `installSelectedSuggestions()` filters: visible AND selected AND not already installing. Stale selected keys for invisible/dismissed suggestions are ignored.
- New state: `isSuggestionSelectionMode`, `selectedSuggestionKeys`.
- New actions: `enterSuggestionSelectionMode`, `exitSuggestionSelectionMode`, `toggleSuggestionSelected`, `installSelectedSuggestions`.
- 3 new strings: `rec_suggestion_select`, `rec_suggestion_cancel_selection`, `rec_suggestion_install_selected`.

Files changed:

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — 2 new state fields, 4 new actions
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` — `SourceSuggestionItem` restructured with Checkbox + outer Row; controls row mode-aware; imports added
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=605
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 3 new strings

Tests run:

- `:app:compileDebugKotlin --offline` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest --offline` — BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug --offline` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.5-debug.apk`

### KMK-Recs v0.6.6

Extension selective uninstall in Browse > Extensions:

- Added "Select extensions" overflow action to enter selection mode.
- In selection mode: installed/untrusted rows show a Checkbox; available rows are not selectable; active install/update rows are not selectable.
- "Uninstall selected (N)" button disabled when N = 0 or bulk uninstall running.
- "Cancel" exits selection mode and clears selection.
- One confirmation dialog before uninstall; user informed that Android may ask per extension.
- Sequential uninstall with 300ms delay between intents to reduce Android prompt stacking.
- Back press exits selection mode before clearing search query.
- Selection cleared in `finally` block regardless of outcome.
- Normal install/update/open/trust/update-all behavior unchanged.

Files changed:

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt` — 3 new state fields, `selectionKey()` helper, `enterExtensionSelectionMode`, `exitExtensionSelectionMode`, `toggleExtensionSelected`, `uninstallSelectedExtensions`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsTab.kt` — overflow action, combined BackHandler, bulk confirm dialog, wiring
- `app/src/main/java/eu/kanade/presentation/browse/ExtensionsScreen.kt` — new params, selection controls item, Checkbox in ExtensionItem, hide actions in selection mode
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=606
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 5 new strings

Tests run:

- `:app:compileDebugKotlin --offline` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest --offline` — BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug --offline` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.6-debug.apk`

### KMK-Recs v0.6.7

Explicit porn/hentai source filter:

- New preference `blockExplicitPornHentaiSources()` (default `false`) in `SourcePreferences`.
- New toggle under Settings > Browse > NSFW content: "Block explicit porn/hentai sources".
- New pure classifier `ExplicitSourceClassifier` in `exh.source`. Conservative rules: explicit keywords in extension name or package name, or known explicit source IDs (NHentai, Pururin, Tsumino, 8Muses, HBrowse, all E-Hentai/ExHentai source IDs). Does NOT match ecchi, nsfw, mature, lewd, or adult alone.
- Filter applied to: `GetExtensionsByType` (available list only), `AndroidSourceManager.getVisibleOnlineSources()`, `AndroidSourceManager.getVisibleCatalogueSources()`, `NonInstalledSourceSuggestionScorer.scoreAndFilter()` via `GetNonInstalledSourceSuggestions`.
- Installed/untrusted extensions remain unfiltered — manageable at all times.
- Default `false` — no behavior change for existing users on update.

Files changed:

- `app/src/main/java/exh/source/ExplicitSourceClassifier.kt` — new pure classifier object
- `app/src/test/java/exh/source/ExplicitSourceClassifierTest.kt` — 20 JUnit 5 tests
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` — new `blockExplicitPornHentaiSources()` preference
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 2 new strings
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsBrowseScreen.kt` — new SwitchPreference in NSFW group
- `app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt` — filter available list
- `app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt` — filter getVisibleOnlineSources/getVisibleCatalogueSources
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt` — pass blockExplicit
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt` — blockExplicit param + filter
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=607

Tests run:

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, all tests PASSED (ExplicitSourceClassifierTest: 20 new)
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.7-debug.apk`

### KMK-Recs v0.6.8

Source evaluation system:

- Bounded one-at-a-time evaluation of non-installed extensions: installs temporarily, probes popular/latest/search pages, scores against taste profile, stores verdict, uninstalls.
- SQLDelight `source_evaluation` table (35 columns); 5 interactors (Get/Upsert/Delete/Clear).
- `SourceEvaluationInstallerPolicy` — validates PRIVATE/SHIZUKU/CURRENT mode; no global pref mutation (Option A: installerOverride param).
- `SourceEvaluationScorer` — pure scorer; explicit-heavy and ecchi-heavy always separate verdicts.
- `SourceEvaluationRunner` — sequential coroutine loop; one extension at a time.
- `SourceEvaluationScreen` + `SourceEvaluationScreenModel` — UI with options, progress, past results.
- `NonInstalledSourceSuggestionScorer` updated: evaluated STRONG_FIT → 0.90, WORTH_TRYING → 0.75; REJECTED/EXPLICIT_HEAVY filtered.
- `GetNonInstalledSourceSuggestions`: 6th reactive combine source (evaluation flow via nested combine).
- 4 new `NonInstalledSuggestionReason` variants.
- Entry point: Recommendation Settings > Source Evaluation.

**Note: this version was missing SQLite migration 47.sqm for existing installs. Fixed in v0.6.9.**

APK: `Komikku-v1.13.6-kmk.6.8-debug.apk`

### KMK-Recs v0.6.9

Source evaluation migration crash fix (hotfix):

- Added `data/src/main/sqldelight/tachiyomi/migrations/47.sqm` — creates `source_evaluation` table and indexes with `IF NOT EXISTS` for updates from pre-v0.6.8 installs.
- Added `.catch` fallback on `getSourceEvaluations.subscribeAll()` in `GetNonInstalledSourceSuggestions` — if evaluation table is absent, Sources To Try renders using metadata-only suggestions and logs the error. No crash.
- Added `.catch` fallback on `getSourceEvaluations.subscribeAll()` in `SourceEvaluationScreenModel.init` — Source Evaluation screen opens with empty past-evaluations list instead of crashing.
- No evaluation data deleted. No uninstall/reinstall required.

Files changed:

- `data/src/main/sqldelight/tachiyomi/migrations/47.sqm` (new)
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt` — safeEvaluationsFlow with catch
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` — catch on subscribeAll
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=609

Tests run:

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.9-debug.apk`

### KMK-Recs v0.6.10

Source Evaluation DI crash fix:

- Registered `GetNonInstalledSourceSuggestions` in `KMKDomainModule` (`addFactory { GetNonInstalledSourceSuggestions(get(), get(), get()) }`). Root cause: `SourceEvaluationScreenModel` requested it via `Injekt.get()` but it was never registered, causing `InjektionException` at screen construction time.
- Normalized `RecommendationsSettingsScreenModel` to use `Injekt.get()` instead of manual construction `GetNonInstalledSourceSuggestions()`. Both consumers now use a single consistent DI pattern.
- Removed redundant outer `screenModelScope.launch {}` wrapper from candidate loading in `SourceEvaluationScreenModel.init` — `launchIn(screenModelScope)` already launches collection.
- Added `.catch` fallback on `getNonInstalled.subscribe()` in `SourceEvaluationScreenModel.init` — candidate loading failures now produce a safe empty state instead of crashing the screen.
- Previous v0.6.9 `.catch` on `getSourceEvaluations.subscribeAll()` is unrelated and intact.

Files changed:

- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` — import + `addFactory` for `GetNonInstalledSourceSuggestions`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` — `Injekt.get()` instead of manual construction
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` — remove outer launch, add `.catch` to candidate loading
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=610

Tests run:

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.10-debug.apk`

### KMK-Recs v0.6.11

Shizuku setup and temporary use for Source Evaluation:

- Added `ShizukuSetupHelper` object (`exh/recs/evaluation/ShizukuSetupHelper.kt`) with `readState(context)`, `openDownload(context)`, `openApp(context)`, `openUninstall(context)`, and `stopUsingFallbackMode(privateAvailable)`. All Shizuku API calls (`pingBinder`, `checkSelfPermission`) are wrapped in `try/catch`. No silent start/stop/revoke behavior.
- `SourceEvaluationScreenModel` now reads real Shizuku state (`installed`, `binderAlive`, `permissionGranted`) instead of hardcoded `false` values. Shizuku state stored in screen model `State.shizukuState`.
- Added screen model actions: `useShizukuForEvaluation()`, `stopUsingShizukuForEvaluation()`, `openShizukuSetup()`, `openShizukuApp()`, `uninstallShizuku()`, `refreshShizukuState()`. `stopUsingShizukuForEvaluation()` switches to PRIVATE when available, otherwise CURRENT; does not mutate the global installer preference.
- `uninstallShizuku()` is a no-op when an evaluation run is active; otherwise fires `Intent.ACTION_DELETE` for `moe.shizuku.privileged.api` through Android's uninstall confirmation flow.
- Added `ShizukuSetupCard` composable to `SourceEvaluationScreen` (appears between InstallerModeSelector and options toggles). Shows one of five status states and context-sensitive buttons (Install, Open, Use for this run, Stop using, Uninstall). Uninstall button disabled while evaluation is running.
- Added 12 new strings to `i18n-kmk/base/strings.xml` (English only).
- Added `SourceEvaluationInstallerPolicyTest.kt` — 8 unit tests covering Shizuku readiness states and stop-using fallback logic.

Files changed:

- `app/src/main/java/exh/recs/evaluation/ShizukuSetupHelper.kt` (new)
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` — real Shizuku state, Shizuku actions
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` — `ShizukuSetupCard` composable
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=611
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 12 new strings
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationInstallerPolicyTest.kt` (new) — 8 tests

Tests run:

- `exh.recs.evaluation.SourceEvaluationInstallerPolicyTest` — 8 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.11-debug.apk`

### KMK-Recs v0.6.12

Broad Source Evaluation candidate pool and Shizuku UX polish:

- Replaced `GetNonInstalledSourceSuggestions` (Sources To Try, selective) with new `GetSourceEvaluationCandidates` in `SourceEvaluationScreenModel`. Source Evaluation now draws from the full pool of available non-installed extensions matching language/nsfw/disliked filters, instead of only the small Sources To Try candidate list.
- `SourceEvaluationCandidateFilter` — new pure object (`buildPool` + `applyOptions` + `shouldSkip` + `isStale`). Testable without Android.
- `GetSourceEvaluationCandidates` — new reactive class combining 3 flow groups: extension triple (available/installed/untrusted), preference triggers (recLanguages/nsfw/blockExplicit/disliked), and safe evaluations flow.
- Fixed skip-already-evaluated: was comparing `signatureHash|pkgName` against `evaluationKey` which is `signatureHash|pkgName|sourceId`. Now uses `extensionKey` = `signatureHash|extensionPkgName` so evaluated extensions are correctly excluded.
- `SourceEvaluationScreenModel` holds a `lastCandidatePool` private `MutableStateFlow`. Option setters (`setSkipAlreadyEvaluated`, `setIncludeExplicit`, `setReEvaluateStale`) now call `applyOptionsAndUpdateState()` immediately so candidate count and start button react live. `startEvaluation()` simplified — no longer re-applies skip filter since `state.candidates` is already filtered.
- `State` gains `candidateDiagnostics: CandidateDiagnostics` with `totalEligible`, `evaluatedHiddenCount`, `explicitHiddenCount`, `dislikedHiddenCount`.
- `CandidateDiagnosticsRow` composable added to `SourceEvaluationScreen` between options toggles and start button. Shows eligible count, evaluated-hidden count, explicit-hidden count.
- Shizuku UX: `ShizukuSetupCard` gains `onRefresh` param and "Refresh status" `TextButton`. Status texts updated: `shizuku_status_selected_ready` for "ready + selected", `shizuku_status_selected_not_ready` for "selected but not ready". Automatic state refresh on screen resume via `DisposableEffect` + `DefaultLifecycleObserver.onStart`.
- 7 new strings in `i18n-kmk/base/strings.xml` (shizuku_action_refresh_status, shizuku_status_selected_ready, shizuku_status_selected_not_ready, source_evaluation_candidates_loading, source_evaluation_candidates_available, source_evaluation_candidates_evaluated_hidden, source_evaluation_candidates_explicit_hidden).
- `GetNonInstalledSourceSuggestions` and its DI registration left unchanged — Sources To Try in Recommendation Settings still uses it.

Files changed:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt` (new)
- `app/src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt` (new)
- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` — import + `addFactory` for `GetSourceEvaluationCandidates`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` — swap to new provider, `CandidateDiagnostics`, `applyOptionsAndUpdateState()`, fix `startEvaluation()`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` — `DisposableEffect` lifecycle refresh, `CandidateDiagnosticsRow`, `onRefresh` in `ShizukuSetupCard`, updated status strings
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=612
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 7 new strings
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationCandidateFilterTest.kt` (new) — 18 tests

Tests run:

- `exh.recs.evaluation.SourceEvaluationCandidateFilterTest` — 18 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.12-debug.apk`

### KMK-Recs v0.6.13

Source Evaluation private install verification and cleanup:

- Created `SourceEvaluationCleanupPolicy` pure helper with `CleanupDecision` enum and `cleanupDecision()` function. Testable without Android.
- `SourceEvaluationRunner` now detects pre-existing installations before evaluating — if an extension is already installed, it is skipped rather than uninstalled during cleanup.
- Cleanup is now private-aware: checks `installedExt.isShared` before uninstalling. Private extensions (`isShared=false`) are removed silently via private file deletion. System-installed extensions (`isShared=true`) only trigger Android uninstall dialogs when `promptHeavyCleanupAllowed=true` (off by default).
- Added diagnostics logging with `"KMK SourceEvaluation install:"` prefix covering: ext name, pkg, sig, mode, override, privateAvailable, wasPreExisting, post-install isShared, cleanup decision.
- Fixed `SourceEvaluationInstallerPolicy`: SHIZUKU ready case now sets `requiresPromptWarning = true` (was wrongly `false`). CURRENT/SHIZUKU via CURRENT also updated.
- Private installer policy now includes a description: "Best for evaluation. Extensions are installed inside Komikku and cleaned up silently."
- `SourceEvaluationScreenModel` checks `policy.requiresPromptWarning && batchSize > 1` before starting; shows a confirmation dialog instead of starting immediately when true.
- Dialog offers: "Use Private" (if privateAvailable), "Continue anyway" (enables promptHeavyCleanupAllowed), "Cancel".
- InstallerMode chip for PRIVATE now shows "Private (recommended)".
- Shizuku setup card: when Shizuku selected + Private available, shows "Shizuku is ready and selected. Private is recommended for silent cleanup." and a "Use Private" button.
- `SourceEvaluationOptions.promptHeavyCleanupAllowed` added (default false).
- `SourceEvaluationQueueState.CleanupStatus` enum added; `EvaluationResult.cleanupStatus` field added.
- `State.privateAvailable` and `State.showPromptHeavyWarningDialog` added to screen model.

New file:
- `exh/recs/evaluation/SourceEvaluationCleanupPolicy.kt`

New test:
- `exh/recs/evaluation/SourceEvaluationCleanupPolicyTest.kt` — 6 tests, all PASSED

Tests run:
- `exh.recs.evaluation.SourceEvaluationCleanupPolicyTest` — 6 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, all tests PASSED
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.13-debug.apk`

### KMK-Recs v0.6.14

Timeout resilience and priority reset safety:

- Part A: Fixed Source Evaluation batch cancellation from slow source timeouts. Replaced all `withTimeout()` calls in `SourceEvaluationRunner` with `withTimeoutOrNull()` so probe/install/load timeouts are local per-extension/per-source failures, not batch-level cancellations. Added timeout diagnostics logging with prefix `"KMK SourceEvaluation timeout:"`. Moved `completedCount++` into `finally` so the progress bar advances correctly even for failed/timed-out extensions (pre-existing skipped extensions still do not count).
- Part B: Removed one-tap `Reset priority` action from top app bar. Added `requestResetSourceOrder()` / `confirmResetSourceOrder()` / `dismissResetSourceOrderDialog()` to ScreenModel and `showResetSourceOrderDialog: Boolean` state. Added "Restore default source order" `TextButton` in Source Priority section (disabled while dragging). Added `AlertDialog` confirmation before reset.

Tests:
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tests, 26 executed, 241 up-to-date)
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.14-debug.apk`

### KMK-Recs v0.6.15

Source Evaluation results stability and sort:

- Created `SourceEvaluationResultList` pure helper object with `SortMode` enum, `sanitize()` (drops blank/duplicate `evaluationKey` rows), `sort()` (6 modes), `stableUiKey()` (fallback composite key), and `verdictRank()` (internal for unit tests).
- SQL: added `ORDER BY evaluated_at DESC` to `getAll` and `getAllAsFlow` in `source_evaluation.sq` for deterministic base ordering.
- ScreenModel: added `resultSortMode` and `showClearEvaluationsDialog` to State. `onEach` now sanitizes evaluations via `SourceEvaluationResultList.sanitize()`. Replaced `clearAllEvaluations()` with three-step `requestClearAllEvaluations()` / `dismissClearAllEvaluations()` / `confirmClearAllEvaluations()`. Added `setResultSortMode()`.
- Screen: replaced `items(..., key = { it.evaluationKey })` + `Modifier.animateItem()` with `itemsIndexed(sortedEvaluations, key = { index, eval -> SourceEvaluationResultList.stableUiKey(eval, index) })`. Added sort dropdown in `eval_sort` list item (compact `TextButton` + `DropdownMenu`). Added clear confirmation `AlertDialog`. Updated `EvaluationResultRow` with defensive display values, compact score subtitle (`fit X% • search Y%`), and truncated error messages (60 char cap).
- Added 10 new strings under `<!-- KMK v0.6.15 -->` in `i18n-kmk/strings.xml`.
- Unit tests: 13 new tests in `SourceEvaluationResultListTest.kt` covering sanitize, sort modes, verdict ranking, and `stableUiKey`.

Tests:
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.15-debug.apk`

### KMK-Recs v0.6.16

Source Evaluation crash quarantine and diagnostics:

- Added probe marker written to SQLite before each risky extension network call. If the process dies (SIGSEGV / stack overflow), the marker persists and is detected on next Source Evaluation screen open, quarantining the extension and clearing the marker.
- New `source_evaluation_probe_marker` and `source_evaluation_unsafe_source` tables (migration 48.sqm).
- 7 new interactors for probe marker and unsafe source management.
- `SourceEvaluationCrashRecoveryPolicy` pure helper — decides MarkUnsafe / ClearStale / DoNothing.
- `SourceEvaluationDiagnosticsBuilder` — builds diagnostics text for "Copy Diagnostics" button.
- Unsafe extensions excluded from `SourceEvaluationCandidateFilter.buildPool()`.
- New "Quarantined Extensions" card in Source Evaluation screen.
- "Copy Diagnostics" button added.

APK: `Komikku-v1.13.6-kmk.6.16-debug.apk`

### KMK-Recs v0.6.17

Source Evaluation startup recovery and known-unsafe seed:

- Moved crash recovery to `App.onCreate` via `SourceEvaluationStartupRecovery.runAsync(scope)` — runs before user reaches Source Evaluation screen.
- New `SourceEvaluationStartupRecovery` shared helper used by both App.kt and SourceEvaluationScreenModel.
- DCM (`eu.kanade.tachiyomi.extension.en.digitalcomicmuseum`) pre-seeded into quarantine table on startup if not already present.
- `SourceEvaluationKnownUnsafeSeeds.kt` with `KnownUnsafeSeed` data class and `ALL_SEEDS` list.
- Quarantined Extensions dialog updated to explain that quarantine only affects Source Evaluation and that crashed installed extensions should be uninstalled from the Extensions screen.

APK: `Komikku-v1.13.6-kmk.6.17-debug.apk`

### KMK-Recs v0.6.18

Package-level extension load quarantine and startup safety:

- Added `KnownUnsafeExtensionPackages` static compile-time guard — checked in `ExtensionLoader` before any `ChildFirstPathClassLoader` or `Class.forName` is called. This is the actual crash fix.
- Added `ExtensionLoadSafetyPolicy.shouldBlock(pkgName, userBlockedPackages)` pure testable helper.
- `LoadResult.Blocked(pkgName, reason)` variant — silently ignored by ExtensionManager (doesn't match Success/Untrusted filters), no UI impact on extensions screen.
- New `unsafe_extension_package` table (migration 49.sqm) for DB-backed package blocks visible in UI.
- New "Blocked Extensions" card in Source Evaluation with per-package "Allow again" and "Allow all" actions.
- `SourceEvaluationStartupRecovery` writes package-level block on startup (idempotent, no sig hash required).
- `SourceEvaluationDiagnosticsBuilder` includes blocked package count and static guard status.

APK: `Komikku-v1.13.6-kmk.6.18-debug.apk`

### KMK-Recs v0.6.19

Source Evaluation UX and background execution:

- **Phase 0 — Taste profile confidence**: `TasteProfileConfidence` domain model derived cheaply from `TasteProfile`. Shows a low-confidence warning banner when total usable tags < 5 or positive tags < 2. Evaluation still runs; scores may be less accurate.
- **Phase 1 — Shizuku UX cleanup**: `showShizukuSetup` state field in ScreenModel. Shizuku card hidden by default for PRIVATE/CURRENT installers; auto-shown when SHIZUKU is selected or when CURRENT+global Shizuku is active. Compact "Shizuku setup" toggle link shown when card is hidden.
- **Phase 2 — Safety diagnostics demotion**: Quarantined Extensions and Blocked Extensions cards removed from top-of-page positions. Replaced with a compact `SafetyDiagnosticsRow` below candidate diagnostics that shows counts as `TextButton`s opening the existing dialogs.
- **Phase 3 — Background execution**: `SourceEvaluationJobState` process-scoped singleton holds pending candidates/options and `activeQueueState: MutableStateFlow`. `SourceEvaluationJob` WorkManager `CoroutineWorker` reads from singleton, creates runner, awaits terminal state. `SourceEvaluationNotifier` shows foreground progress notification (using moko-resources). ScreenModel init observes `JobState.activeQueueState` to reconnect after screen leave/return.
- Added `isTerminal` property on `SourceEvaluationQueueState` (`Completed || Cancelled || Failed`).
- New notification channel `source_evaluation_channel` (`IMPORTANCE_LOW`), IDs `-801`/`-802`.
- 11 unit tests in `TasteProfileConfidenceTest`.

Files added:
- `domain/.../taste/model/TasteProfileConfidence.kt`
- `app/.../exh/recs/evaluation/SourceEvaluationJobState.kt`
- `app/.../exh/recs/evaluation/SourceEvaluationJob.kt`
- `app/.../exh/recs/evaluation/SourceEvaluationNotifier.kt`
- `app/src/test/.../TasteProfileConfidenceTest.kt`

Files modified: `SourceEvaluationQueueState.kt`, `SourceEvaluationScreenModel.kt`, `SourceEvaluationScreen.kt`, `Notifications.kt`, `KmkRecsReleaseNotes.kt`, `i18n-kmk/strings.xml` (8 new strings).

Tests run:
- `TasteProfileConfidenceTest` — 11 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.19-debug.apk`

### KMK-Recs v0.6.19 (follow-up)

Source Evaluation notification deep link, wording, and offline guard:

- Tapping the foreground progress notification opens Source Evaluation directly (deep link via `Intent.ACTION_VIEW`, handled in `MainActivity`).
- Candidate count now shows "N unassessed extensions remaining" when skip-already-evaluated is on.
- Starting evaluation shows an error message when offline instead of silently doing nothing.
- Additional unit tests for `SourceEvaluationCandidateFilter` (25 total).

APK: `Komikku-v1.13.6-kmk.6.19-debug.apk` (VERSION_CODE 619)

### KMK-Recs v0.6.20

Source status display ordering, reassessment baseline, seen manga marker, and source explainability:

- **Source status display order**: Recommendation Settings shows a non-draggable "Source Status" section grouped as: With results → No results → Disliked. Within each group, user priority order is preserved. Uses new pure helper `SourceStatusDisplayOrder`.
- **Reassessment tracking**: Source Evaluation tracks how many ratings have been added since the last evaluation baseline. Shows a prompt at 100+ new ratings; "Reassess sources" button always available. Baseline updated automatically on successful evaluation completion.
- **Source explainability**: Past result rows show evidence strength (Strong / Moderate / Weak / Low confidence) and how long ago each source was last evaluated.
- **Seen / Already read manga**: "Mark as seen" action added to manga detail rating menu. Seen entries are stored in preferences and always filtered from For You (regardless of "Hide known manga" setting). Changing the seen set invalidates the recommendation cache. "Seen other versions" action opens the cross-extension matching workflow with `CrossExtensionMatchMode.MarkSeen`.
- **Management controls**: Collapsible "Source management" section in Source Evaluation with Reset disliked sources, Reset reassessment baseline, and Clear seen manga actions, each guarded by a confirmation dialog.
- **Hidden-source state**: Deferred. No overloading of disliked state.
- **Backup/restore for seen manga**: Deferred.

New files:
- `exh/recs/SourceStatusDisplayOrder.kt`
- `exh/recs/SeenRecommendationMangaStore.kt`
- `app/src/test/.../SourceStatusDisplayOrderTest.kt` (8 tests)
- `app/src/test/.../SeenRecommendationMangaStoreTest.kt` (11 tests)

New preferences: `seenRecommendationMangaKeys`, `sourceEvaluationLastReassessmentRatingCount`, `sourceEvaluationLastReassessmentAt`.

New strings: 22 keys in `i18n-kmk/strings.xml`.

Tests run:
- `SourceStatusDisplayOrderTest` — 8 tests, all PASSED
- `SeenRecommendationMangaStoreTest` — 11 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.6.20-debug.apk` (VERSION_CODE 620)

### KMK-Recs v0.7.0

Loved Manga view:

- Added "Loved Manga" screen accessible via heart icon button in the For You tab action bar.
- Shows all manga rated `LOVE`, sorted by most recently loved. `LIKE`, `DISLIKE`, and `SEEN` entries excluded.
- "Group clear duplicates" toggle collapses same-manga entries from multiple sources into one card. Grouping uses exact normalized title + description match, requiring description ≥ 50 characters. No taste data is modified.
- Grouped entries show a "%1$d versions" badge. Tapping navigates to manga detail.
- Unresolved manga (not in local DB) shows title from taste row and a placeholder cover.

New files:
- `exh/recs/loved/LovedMangaDuplicateGrouper.kt`
- `exh/recs/loved/LovedMangaScreenModel.kt`
- `exh/recs/loved/LovedMangaScreen.kt`
- `app/src/test/.../LovedMangaDuplicateGrouperTest.kt` (12 tests)

Modified files:
- `BrowsePersonalRecommendationsTab.kt` — added Loved Manga action button
- `KmkRecsReleaseNotes.kt` — VERSION_CODE=700
- `i18n-kmk/strings.xml` — 5 new strings

Tests run:
- `LovedMangaDuplicateGrouperTest` — 12 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.0-debug.apk` (VERSION_CODE 700)

### KMK-Recs v0.7.1

Cross-extension match state crash fix and Seen menu visibility fix:

- Fixed `BadParcelableException` / `NotSerializableException` crash when opening Seen other versions, Favorite other versions, or any cross-extension match mode. Root cause: `CrossExtensionMatchScreen` stored `CrossExtensionMatchMode` (a sealed interface with non-serializable `data object` members) directly in its constructor. Android/Voyager state save failed during app background/rotation.
- Fixed: `Seen other versions` disappeared from the action menu after marking the current manga as seen. Condition `&& !isSeen` in `MangaInfoHeader` hid the item incorrectly.

Fix approach: route-safe primitive arguments. `CrossExtensionMatchScreen` now stores `modeKey: String` + `ratingValue: Int?`. Mode is reconstructed in `Content()` via `CrossExtensionMatchRouteMode.toMode()`. Invalid mode shows a safe error string instead of crashing.

New files:
- `exh/recs/matching/CrossExtensionMatchRouteMode.kt` — bidirectional conversion between mode objects and primitive route args
- `app/src/test/.../CrossExtensionMatchRouteModeTest.kt` — 8 tests, all PASSED

Modified files:
- `exh/recs/matching/CrossExtensionMatchScreen.kt` — primitive constructor, `fromMode()` factory, safe error state
- `eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` — 3 call sites updated to `CrossExtensionMatchScreen.fromMode(...)`
- `eu/kanade/presentation/manga/components/MangaInfoHeader.kt` — removed `&& !isSeen` from Seen other versions guard
- `KmkRecsReleaseNotes.kt` — VERSION_CODE=710
- `i18n-kmk/strings.xml` — 1 new string (`rec_match_mode_invalid`)

Tests run:
- `CrossExtensionMatchRouteModeTest` — 8 tests, all PASSED
- `CrossExtensionMatchSelectionTest` — 10 tests, all PASSED
- `:app:testDebugUnitTest --tests "*CrossExtensionMatch*"` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.1-debug.apk` (VERSION_CODE 710)

### KMK-Recs v0.7.4

Source Evaluation update reassessment and recommendation fit helpers:

- Extension version metadata (`extensionVersionName`, `extensionVersionCode`, `extensionApkName`) now stored in `source_evaluation` table via migration 51.sqm. All three fields are nullable so pre-v0.7.4 rows are untouched.
- `SourceEvaluationScorer.score()` and `errorRecord()` accept version fields; `SourceEvaluationRunner` passes `ext.versionName/versionCode/apkName` at all three callsites.
- `SourceEvaluationUpdatePolicy` — pure stateless helper. Detects UPDATED / NOT_UPDATED / UPDATE_UNKNOWN / NEVER_EVALUATED per extension key.
- `onlyUpdatedEvaluated` option added to `SourceEvaluationCandidateFilter.applyOptions()` and `SourceEvaluationOptions`. When true, only extensions confirmed updated since last eval appear as candidates.
- `SourceEvaluationScreenModel` computes `updatedEvaluatedExtensionCount`; `startReassessUpdated()` action launches an update-only run.
- `SourceEvaluationScreen` shows "N evaluated extension(s) have updates" notice + "Reassess updated extensions" button.
- `SourceRecommendationFitEligibility` — pure gate (STRONG_FIT/WORTH_TRYING + MIN_SAMPLE_COUNT = 3). Returns ELIGIBLE/INELIGIBLE_VERDICT/INSUFFICIENT_EVIDENCE.
- `SourceRecommendationFitScorer` — pure scorer: takes `Outcome` data class, returns score in [0.0, 1.0]. Actual probe execution deferred.
- Documentation audit: corrected NEXT_WORK.md to mark Favorite other versions and alternate-title matching as implemented (both shipped in v0.7.0; were incorrectly listed as deferred).
- Pre-existing test fix: `GetTasteProfileTest.fakeTasteRepo` was missing 7 CrossSourceMangaLink abstract method stubs; added stub implementations.

Files changed:
- `data/src/main/sqldelight/tachiyomi/migrations/51.sqm` (new)
- `domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt` — +3 nullable version fields
- `data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq` — +3 columns in schema and upsert
- `data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt` — mapper + upsert
- `exh/recs/evaluation/SourceEvaluationScorer.kt` — +3 params to score() and errorRecord()
- `exh/recs/evaluation/SourceEvaluationRunner.kt` — pass version fields at 3 callsites
- `exh/recs/evaluation/SourceEvaluationUpdatePolicy.kt` (new)
- `exh/recs/evaluation/SourceEvaluationQueueState.kt` — +onlyUpdatedEvaluated option
- `exh/recs/evaluation/SourceEvaluationCandidateFilter.kt` — +onlyUpdatedEvaluated to applyOptions()
- `exh/recs/evaluation/SourceEvaluationScreenModel.kt` — +updatedEvaluatedExtensionCount state, +startReassessUpdated()
- `exh/recs/evaluation/SourceEvaluationScreen.kt` — updated notice + button
- `i18n-kmk/.../strings.xml` — +2 strings
- `exh/recs/evaluation/SourceRecommendationFitEligibility.kt` (new)
- `exh/recs/evaluation/SourceRecommendationFitScorer.kt` (new)
- `app/src/test/.../SourceEvaluationUpdatePolicyTest.kt` (new — 9 tests)
- `app/src/test/.../SourceRecommendationFitEligibilityTest.kt` (new — 10 tests)
- `app/src/test/.../SourceRecommendationFitScorerTest.kt` (new — 10 tests)
- `app/src/test/.../GetTasteProfileTest.kt` — CrossSourceMangaLink stub fix
- `KmkRecsReleaseNotes.kt` — VERSION_CODE=740

Tests run:
- `SourceEvaluationUpdatePolicyTest` — 9 tests, all PASSED
- `SourceRecommendationFitEligibilityTest` — 10 tests, all PASSED
- `SourceRecommendationFitScorerTest` — 10 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.4-debug.apk` (VERSION_CODE 740)

### KMK-Recs v0.7.5

Recommendation JSON export and import:

- Export recommended manga as a JSON bundle from three surfaces: For You tab (Top Picks with full scores and matched groups), For You source rows (long-press → export that source's results), Loved Manga screen (share button in app bar).
- `TopPicksScreen` also gets a share button; exports with reduced metadata (no scores, no groups — TopPicksScreen only has manga IDs).
- JSON schema: `kmk.recommendation.bundle` v1. `RecommendationBundle` carries `bundleType`, `requiredSources`, `items`, `kmkRecsVersion`, `createdAt`.
- Import entry point: Settings > Data storage > "Import recommendation bundle". File picker → reads JSON → validates → navigates to preview screen.
- Preview screen (`RecommendationBundleImportScreen`) groups items by resolution state: ReadyToAdd, AlreadyInLibrary, MissingSource (with "Install" button), SourceInstalledNeedsResolve, NeedsManualMatch, Unsupported (Local Source), Error.
- Source resolution: 3-step — exact sourceId → pkgName+name+lang → signatureHash+name+lang.
- "Install extension" flow: `extensionManager.installExtension(ext).collectLatest`; re-resolves items on `InstallStep.Installed`.
- "Add selected (N)" calls `RecommendationBundleLibraryAdder.addToLibrary(skipDuplicates=true)` for each selected item. SourceInstalledNeedsResolve items build via `networkToLocalManga(sManga.toDomainManga(resolvedSourceId))`.
- Duplicate detection requires title + (author OR artist) — title alone is never flagged as a duplicate.
- Add summary dialog shows Added / Already in library / Failed counts.

New files:
- `exh/recs/share/RecommendationBundle.kt` — pure JSON data models (`RecommendationBundle`, `RecommendationBundleItem`, `RecommendationBundleSource`, `RecommendationBundleType`)
- `exh/recs/share/RecommendationBundleValidator.kt` — pure validator; max 2 MB, 500 items, 200 sources
- `exh/recs/share/RecommendationBundleSourceResolver.kt` — pure source resolution and duplicate detection
- `exh/recs/share/RecommendationBundleExporter.kt` — builds bundles from in-memory data; writes to URI
- `exh/recs/share/RecommendationBundleImporter.kt` — reads bytes from URI, delegates to validator
- `exh/recs/share/RecommendationBundleLibraryAdder.kt` — Injekt DI; adds manga to library with duplicate + category handling
- `exh/recs/share/RecommendationBundleImportScreenModel.kt` — screen model; resolution, install, add flows
- `exh/recs/share/RecommendationBundleImportScreen.kt` — Voyager screen; Voyager-safe primitive constructor (`uriString: String`)
- `app/src/test/.../RecommendationBundleSerializationTest.kt` (8 tests)
- `app/src/test/.../RecommendationBundleValidatorTest.kt` (11 tests)
- `app/src/test/.../RecommendationBundleSourceResolverTest.kt` (11 tests)
- `app/src/test/.../RecommendationBundleDuplicatePolicyTest.kt` (9 tests)

Modified files:
- `exh/recs/BrowsePersonalRecommendationsTab.kt` — export launcher, Export Top Picks action, long-press source row to export source
- `exh/recs/TopPicksScreen.kt` — export launcher, share action
- `exh/recs/loved/LovedMangaScreen.kt` — export launcher, share action
- `eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt` — "Import recommendation bundle" TextPreference in export group
- `KmkRecsReleaseNotes.kt` — VERSION_CODE=750
- `i18n-kmk/.../strings.xml` — 22 new strings (`rec_bundle_*`, `action_select_all`, `action_deselect_all`)

Compile fix: `RecommendationBundleExporter.kt` line 109 `manga.status` cast from `Long` to `Int?` via `.takeIf { it != 0L }?.toInt()`. `RecommendationBundleImportScreen.kt` missing `Extension` import added.

Tests run:
- `RecommendationBundleSerializationTest` — 8 tests, all PASSED
- `RecommendationBundleValidatorTest` — 11 tests, all PASSED
- `RecommendationBundleSourceResolverTest` — 11 tests, all PASSED
- `RecommendationBundleDuplicatePolicyTest` — 9 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.5-debug.apk` (VERSION_CODE 750)

### KMK-Recs v0.7.3

Loved Manga installed-source filter:

- Loved Manga now hides entries whose source is not currently installed. This is display filtering only — taste rows and cross-source link rows are preserved. Reinstalling a source makes its loved entries visible again.
- Added `LovedMangaSourceFilter.kt` pure helper: `filterLovedTastesByInstalledSources(tastes, installedSourceIds)`.
- `LovedMangaScreenModel` injects `SourceManager`, builds `installedSourceIds` with a fail-safe `runCatching` (empty set on failure → empty state, not all entries shown).
- Filtering happens before sorting and before duplicate grouping, so `versionCount` counts only visible installed-source members.
- Local Source (`id == 0L`) is excluded by `getVisibleCatalogueSources()` under the same rule as For You.

Files changed:
- `exh/recs/loved/LovedMangaSourceFilter.kt` (new)
- `exh/recs/loved/LovedMangaScreenModel.kt` — inject SourceManager, apply filter
- `app/src/test/.../LovedMangaSourceFilterTest.kt` (new — 9 tests)
- `KmkRecsReleaseNotes.kt` — VERSION_CODE=730

Tests run:
- `LovedMangaSourceFilterTest` — 9 tests, all PASSED
- `LovedMangaDuplicateGrouperTest` — 38 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.3-debug.apk` (VERSION_CODE 730)

### KMK-Recs v0.7.2

Loved Manga smart grouping and cross-source link usage:

- Rewrote `LovedMangaDuplicateGrouper` with a tiered evidence strategy. Priority: (1) confirmed cross-source link group, (2) exact title + same author, (3) exact title + same artist, (4) exact title + exact long description, (5) exact title + highly similar long description (Jaccard ≥ 0.85, both ≥ 80 chars), (6) similar title + same author (Jaccard ≥ 0.80), (7) similar title + same artist. Never groups by title alone.
- Updated `LovedMangaScreenModel` to load all cross-source links from `GetCrossSourceMangaLinks`, build a `(source|url) → groupId` map, and pass `linkGroupId` into grouper inputs. Fails open with empty map if link loading fails.
- Added `LovedMangaGroupReason` enum (LINK_GROUP, TITLE_AND_AUTHOR, TITLE_AND_ARTIST, TITLE_AND_SIMILAR_DESCRIPTION, SIMILAR_TITLE_AND_AUTHOR, SIMILAR_TITLE_AND_ARTIST, STANDALONE) stored on `GroupResult`.
- Improved `normalizeTitle` to strip punctuation separators (-, –, —, :, ·, …).
- Added `tokenJaccardSimilarity` for title and description similarity.
- Audited cross-source link group implementation and corrected docs that incorrectly said link groups are deferred. Link groups are fully implemented (SQLDelight table, migration 50, domain model, interactors, backup at proto 624, restore, sync merge).

New files: none (modified existing files only).

Modified files:
- `exh/recs/loved/LovedMangaDuplicateGrouper.kt` — full rewrite with tiered grouping strategy
- `exh/recs/loved/LovedMangaScreenModel.kt` — inject `GetCrossSourceMangaLinks`, pass linkGroupId into grouper
- `app/src/test/.../LovedMangaDuplicateGrouperTest.kt` — 34 tests, all PASSED
- `KmkRecsReleaseNotes.kt` — VERSION_CODE=720
- `docs/recommendations/CURRENT_STATE.md` — updated version, grouping description, cross-source link doc fix
- `docs/recommendations/NEXT_WORK.md` — updated after v0.7.2
- `docs/recommendations/README.md` — implementation report added
- `RECOMMENDATION_VERSIONING.md` — v0.7.2 entry added

Tests run:
- `LovedMangaDuplicateGrouperTest` — 34 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.2-debug.apk` (VERSION_CODE 720)

### KMK-Recs v0.7.6

Source Evaluation batch continuation, installed-source display filter, and second-stage recommendation-quality probe:

- **Batch continuation**: After a Source Evaluation batch completes, a "Continue next batch (N remaining)" button appears. Tapping it evaluates the next slice using the same filter fingerprint without restarting from scratch. Progress is stored per fingerprint and expires after 7 days.
- **Hide installed by default**: Past evaluation results now hide extensions that are currently installed. A "Show installed" chip reveals them; a "Hidden installed: N" count shows how many are hidden. Logic lives in new pure helper `SourceEvaluationDisplayFilter` (returns `visible: List<SourceEvaluation>` and `hiddenInstalledCount: Int`).
- **Second-stage recommendation-quality probe**: After a source evaluation batch, `SourceRecommendationFitProbe` runs a bounded 2-plan probe for each STRONG_FIT and WORTH_TRYING source. The probe calls `getSearchManga(page=1, ...)` with a 30s timeout per plan. Results are scored by `SourceRecommendationFitScorer` (reused from v0.7.4) and stored in the `source_recommendation_fit` table via `UpsertSourceRecommendationFit`. Each past result row shows a third line: "Recommendations: Great", "Recommendations: Mixed", etc.

New domain model: `SourceRecommendationFit` (fitKey, evaluationKey, verdict, quality score, probe stats, error message).

New enum: `RecommendationQualityVerdict` (GREAT, GOOD, MIXED, WEAK, NO_MATCHES, ERROR, TOO_LITTLE_EVIDENCE).

New interactors: `GetSourceRecommendationFit`, `UpsertSourceRecommendationFit`.

Reused helpers (v0.7.4): `SourceRecommendationFitEligibility`, `SourceRecommendationFitScorer`.

New test file: `SourceEvaluationDisplayFilterTest` — 7 tests covering show/hide behavior, hidden count, partial overlap.

APK: `Komikku-v1.13.6-kmk.7.6-debug.apk` (VERSION_CODE 760)

### KMK-Recs v0.7.7

Source Evaluation follow-up: toggle fix, recommendation-quality workflow section, and "Not checked" label for promising rows:

- **Toggle fix (Fix 1)**: The "Show installed" chip now remains visible when installed rows are visible, so users can hide them again without leaving the screen. Old condition `hiddenInstalledCount > 0 || !state.showInstalled` incorrectly hid the chip when `showInstalled=true`. Fixed to `hiddenInstalledCount > 0 || state.showInstalled`. Chip label is now dynamic: "Hide installed" when rows are visible, "Show installed" when hidden.
- **Recommendation Quality section (Fix 2)**: A visible "Recommendation Quality" section now appears above the sort header when any promising sources (STRONG_FIT or WORTH_TRYING) exist. Shows missing count, "Evaluate recommendations" button for unchecked promising sources, and "Re-check all" button for already-checked ones. Running state shows progress: "Checking recommendation quality… (N/M)".
- **Source fit and recommendation quality stay separate (Fix 3)**: Already correct from v0.7.6. No changes.
- **"Not checked" label (Fix 4)**: Promising rows without a `SourceRecommendationFit` record now show "Recommendations: Not checked" instead of nothing, making it clear a quality check is available but has not run yet.
- **Reuse existing probe logic (Fix 5)**: `evaluateRecommendationQualityForPromising(reCheckAll: Boolean)` action in `SourceEvaluationScreenModel` runs the probe inline for installed promising sources. Reuses `SourceRecommendationFitProbe`, `SourceRecommendationFitScorer`, `GetSourceRecommendationFit`, `UpsertSourceRecommendationFit` — no new infrastructure.

New pure helper: `SourceRecommendationQualityQueue` — partitions evaluations into missingPromising, checkedPromising, ineligible.

New string keys: `source_evaluation_hide_installed`, `source_evaluation_rec_quality_section_title`, `source_evaluation_rec_quality_missing`, `source_evaluation_rec_quality_evaluate`, `source_evaluation_rec_quality_recheck_all`, `source_evaluation_rec_quality_not_checked`, `source_evaluation_rec_quality_running`.

Composable context fix: `remember` call for `recQualityQueue` moved outside `LazyColumn` (was incorrectly inside `LazyListScope` which is not `@Composable`).

New test file: `SourceRecommendationQualityQueueTest` — 8 tests.

Added to `SourceEvaluationDisplayFilterTest`: 3 new toggle behavior tests.

APK: `Komikku-v1.13.6-kmk.7.7-debug.apk` (VERSION_CODE 770)

### KMK-Recs v0.7.8

Best version / chapter quality compare workflow:

- "Find best version" item added to the manga detail rating dropdown (after "Seen other versions"). Opens `BestVersionCompareScreen(originMangaId: Long)`.
- Bounded same-manga candidate search across all configured sources. Cap and preselect behavior are configurable per new preferences.
- 10-step state machine: LoadingOrigin → SearchingCandidates → ConfirmCandidates → LoadingChapters → SelectChapter → LoadingPreview → ComparePreview → PreparingMigration → Done / Error.
- `BestVersionChapterMatcher` — finds the candidate chapter with the closest `chapter_number` within ±1.0. `selectDefaultChapter()` picks in-progress > latest-read > latest.
- `BestVersionPageSampler` — samples mid-chapter pages from a 30–75% window. Configurable sample size (2/5/10). Optional skip of first 2 pages and last page.
- Page previews loaded via `HttpSource.getPageList` + `getImageUrl` and displayed with Coil3 `AsyncImage`.
- Migrate / Copy / Cancel dialog calls `MigrateMangaUseCase`.
- New `manga_source_quality_signal` SQLDelight table (migration 53) stores the user's confirmed quality choice: origin/selected source+url, chapter number, sampled page URLs, timestamp.
- 4 new preferences: `sameMangaMatchResultsPerSource`, `sameMangaMatchPreselectResults`, `bestVersionPreviewSampleSize`, `bestVersionAvoidFirstPages`.
- Recommendation Settings gains a "Same manga matching" section with all 4 new preferences.
- `CrossExtensionMatchScreenModel` now reads cap and preselect from preferences instead of hardcoded values.

New files:
- `exh/recs/matching/SameMangaMatchSettings.kt`
- `exh/recs/matching/SameMangaCandidateResult.kt`
- `exh/recs/matching/SameMangaCandidateSearcher.kt`
- `exh/recs/bestversion/BestVersionPageSampler.kt`
- `exh/recs/bestversion/BestVersionChapterMatcher.kt`
- `exh/recs/bestversion/BestVersionCompareScreenModel.kt`
- `exh/recs/bestversion/BestVersionCompareScreen.kt`
- `data/.../manga_source_quality_signal.sq`
- `data/.../migrations/53.sqm`
- `domain/.../taste/model/MangaSourceQualitySignal.kt`
- `domain/.../taste/repository/MangaSourceQualitySignalRepository.kt`
- `domain/.../taste/interactor/GetMangaSourceQualitySignals.kt`
- `domain/.../taste/interactor/UpsertMangaSourceQualitySignal.kt`
- `data/.../taste/MangaSourceQualitySignalRepositoryImpl.kt`
- `app/src/test/.../SameMangaMatchSettingsTest.kt` (18 tests)
- `app/src/test/.../BestVersionPageSamplerTest.kt` (11 tests)
- `app/src/test/.../BestVersionChapterMatcherTest.kt` (11 tests)

Tests run:
- `SameMangaMatchSettingsTest` — 18 tests, all PASSED
- `BestVersionPageSamplerTest` — 11 tests, all PASSED
- `BestVersionChapterMatcherTest` — 11 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL in 2m 4s

v0.7.7 follow-up fix (shipped within this APK):

- `SourceRecommendationQualityExtensionResolver` — new pure resolver: `resolve(evaluation, available)` finds an `Extension.Available` match using 4-step resolution (exact sig+pkg → pkg-only → sig+name → name+lang). Returns `Found`, `Ambiguous`, or `NotFound`. 10 tests.
- `evaluateRecommendationQualityForPromising()` enhanced: non-installed promising sources are now resolved from `lastCandidatePool`, temporarily installed, probed, and cleaned up. Error messages reflect real failure reasons.
- `installedExtensionKeys` in `SourceEvaluationScreenModel` is now reactive via `extensionManager.installedExtensionsFlow .onEach { } .launchIn()`. The display filter and hidden-count update immediately on extension changes. Fixes Audit 8.1.
- `visibleSources` in `RecommendationsSettingsScreenModel` is no longer a static field. All call sites call `sourceManager.getVisibleCatalogueSources()` inline. A reactive observer on `installedExtensionsFlow` calls `refreshVisibleSources()` to update `orderedSources` and `availableLanguages` on extension changes. Fixes Audit 8.2.

Tests run (follow-up):
- `SourceRecommendationQualityExtensionResolverTest` — 10 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (full suite, no regressions)
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.8-debug.apk` (VERSION_CODE 780, rebuilt)

### KMK-Recs v0.7.9

Best Version dialog polish and fullscreen page preview:

- Fixed Cancel button in the Best Version migration confirmation dialog. `onDismiss` previously called `screenModel.selectBestVersion(MangaIdentityKey(-1, ""))`, a fake sentinel that kept `selectedBestKey != null` and trapped the dialog. Now calls `screenModel.dismissMigrationDialog()` which sets `selectedBestKey = null`.
- Added `dismissMigrationDialog()` to `BestVersionCompareScreenModel`. Clears `selectedBestKey` and `isMigrating`; preserves step, candidates, previews, selected chapter, and all loaded image URLs.
- Dialog guard hardened: only renders when `selectedBestKey` resolves to a real candidate in `selectedCandidates`. Stale keys are cleared via `LaunchedEffect(selectedBestKey) { screenModel.dismissMigrationDialog() }`.
- `confirmMigration(replace)` made defensive: missing origin transitions to `Error` and clears `selectedBestKey`; unresolvable target calls `dismissMigrationDialog()` rather than silently returning with key still set.
- Sampled page thumbnails in `ComparePreviewContent` are now tappable. Tap opens a fullscreen `Dialog` with the full image.
- Fullscreen page preview supports pinch-to-zoom (max 5×) and pan via `detectTransformGestures`. Resetting scale to 1× resets offset. Close icon (top-right) and Android back both dismiss.
- Closing fullscreen returns to the comparison screen with all candidates, previews, selected chapter, and loaded image URLs intact.
- Added `FullscreenPreviewPage` local data class and `FullscreenPagePreviewDialog` composable in `BestVersionCompareScreen.kt`.
- Added 2 new i18n strings: `best_version_preview_page_content_description`, `best_version_close_preview`.
- Same-manga match preselect default verified: preference `same_manga_match_preselect_results` defaults `true`; both `CrossExtensionMatchScreenModel` and `BestVersionCompareScreenModel` respect it. No default was changed.

Files changed:
- `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt` — `dismissMigrationDialog()`, hardened `confirmMigration()`
- `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt` — dialog fix, `FullscreenPreviewPage`, `FullscreenPagePreviewDialog`, thumbnail `.clickable`, new imports
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 2 new strings
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=790, VERSION_NAME="KMK-Recs v0.7.9"
- `app/src/test/java/exh/recs/bestversion/BestVersionSelectionPolicyTest.kt` (new) — 13 tests

Tests run:
- `BestVersionSelectionPolicyTest` — 13 tests, all PASSED
- `BestVersionPageSamplerTest` — 11 tests, all PASSED
- `BestVersionChapterMatcherTest` — 11 tests, all PASSED
- `SameMangaMatchSettingsTest` — 18 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (full suite, no regressions)
- `:app:assembleDebug` — BUILD SUCCESSFUL in 1m 3s

APK: `Komikku-v1.13.6-kmk.7.9-debug.apk` (VERSION_CODE 790)

### KMK-Recs v0.7.10

Recommendation Quality strong-fit error fix:

- Root cause fixed: `evaluateRecommendationQualityForPromising()` used `lastCandidatePool.value?.allEligible?.map { it.extension }` as its only available-extension source. When no evaluation batch had run in the current session, `lastCandidatePool` was null, making every non-installed promising source fail with "Extension not found in available sources" ERROR.
- Added `loadAvailableExtensionsForRecQuality()` which reads `extensionManager.availableExtensionsFlow.value` (the same full unfiltered repository list used by `GetSourceEvaluationCandidates`). Falls back to `lastCandidatePool` only if the manager returns an empty list.
- Replaced the `lastCandidatePool`-based single line with `loadAvailableExtensionsForRecQuality()` in `evaluateRecommendationQualityForPromising()`.
- Added `SourceRecommendationQualityInstalledResolver` (new pure helper): 4-step installed extension matching (exact sig+pkg → pkg only → sig+name → name+lang). Returns `Found`, `Ambiguous`, or `NotFound`. Avoids guessing on ambiguous matches.
- Added `SourceRecommendationQualitySourceResolver` (new pure helper): 5-step source-within-extension matching (exact source id → name+lang → name → normalized name+lang → normalized name). Returns `Found`, `Ambiguous`, or `NotFound`.
- Replaced the single `find { pkgName + signatureHash }` call in the installed path with `SourceRecommendationQualityInstalledResolver.resolve()`.
- Replaced `findSourceInInstalledExt()` (2-step, nullable return) in both installed and non-installed paths with `SourceRecommendationQualitySourceResolver.resolve()`.
- Error messages are now stage-specific: "Available extension list unavailable", "Extension not found in available sources", "Extension match ambiguous: ...", "Installed extension match ambiguous: ...", "Install failed or timed out", "Installed extension did not load", "Source not found in installed extension", "Source match ambiguous in installed extension: ...", "Source not found after install", "Source match ambiguous after install: ...".
- Per-source error isolation preserved (individual `try/catch` in the loop). A single source failure does not cancel the rest of the run.
- Temporary install/cleanup path unchanged: still uses `SourceEvaluationInstallerPolicy.effectiveInstallerOverride()`, `extensionManager.installExtension()`, and `SourceEvaluationCleanupPolicy`.

Files changed:
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` — `loadAvailableExtensionsForRecQuality()` added; `evaluateOneForRecQuality()` rewritten; `findSourceInInstalledExt()` removed; `availableExtensions` line replaced
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityInstalledResolver.kt` (new) — 4-step installed extension resolver
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualitySourceResolver.kt` (new) — 5-step source-within-extension resolver
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=800, VERSION_NAME="KMK-Recs v0.7.10"
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityInstalledResolverTest.kt` (new) — 9 tests
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualitySourceResolverTest.kt` (new) — 8 tests

Tests run:
- `SourceRecommendationQualityInstalledResolverTest` — 9 tests, all PASSED (new)
- `SourceRecommendationQualitySourceResolverTest` — 8 tests, all PASSED (new)
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (full suite, no regressions)
- `:app:assembleDebug` — BUILD SUCCESSFUL in 56s

APK: `Komikku-v1.13.6-kmk.7.10-debug.apk` (VERSION_CODE 800)

### Planned Follow-Ups

These are planning placeholders only. Do not mark them implemented until each has an implementation report, test results, and APK handoff details.

#### KMK-Recs v0.5.2

Cross-extension matching patch.

Planning file:

- `docs/recommendations/KMK_RECS_STAGED_SETTINGS_AND_MATCHING_IMPROVEMENTS_PLAN.md`

Expected scope:

- improve Love/Like/Dislike other versions matching with bounded alternate-query planning,
- preserve normal global search behavior,
- keep per-source matching cap and manual deselection behavior.

This remains in the v0.5 line because it revisits the cross-extension matching workflow.

#### KMK-Recs v0.7.0

Implemented. See `### KMK-Recs v0.7.0` entry above.

#### KMK-Recs v0.7.2

Implemented. See `### KMK-Recs v0.7.2` entry above.

## APK Naming Recommendation

For local handoff builds, use filenames like:

```text
Komikku-v1.13.6-kmk.3.2-debug.apk
Komikku-v1.13.6-kmk.3.2-release.apk
```

This keeps the upstream Komikku app version visible while also recording the local KMK recommendation feature version.

## When To Bump

Bump the feature version whenever:

- a new APK is handed to the user for tablet testing,
- a recommendation database schema changes,
- backup/restore fields change,
- For You behavior changes in a user-visible way,
- a crash fix or important behavior fix lands.

Also update the relevant implementation markdown file with:

- what changed,
- files touched,
- tests run,
- known risks,
- APK filename if built.
