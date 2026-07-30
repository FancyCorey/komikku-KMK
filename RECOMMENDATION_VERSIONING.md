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
## Canonical Versioning Rule (Effective v0.7.42 Follow-Up)

The early history used a mixture of semantic-style labels, topic-line labels, and sequential
`v0.7.N` feature labels. Those historical entries remain unchanged because they describe APKs
that were actually handed off. They are not a template for future releases.

For every release after `KMK-Recs v0.7.42`, use this single convention:

| Situation | Feature label | `KmkRecsReleaseNotes.VERSION_CODE` | APK handoff name |
|---|---|---:|---|
| New approved recommendation feature or coherent phase | `KMK-Recs v0.7.<next>` | Next unused integer greater than the current one | `Komikku-v1.13.6-kmk.7.<next>-debug.apk` |
| Corrective follow-up to the current feature release | `KMK-Recs v0.7.<current>-fix<N>` | Next unused integer greater than the current one | `Komikku-v1.13.6-kmk.7.<current>.<N>-debug.apk` |
| Rebuild with no source, resource, schema, or release-note change | Keep the existing feature label | Keep the existing code | Use the same APK identity only when it replaces an unshared artifact; otherwise do not hand it off as a new release |

Rules:

- `VERSION_CODE` is a monotonic local KMK What's New marker. It must never decrease and must
  increase whenever a corrected APK needs to display a new KMK What's New entry.
- The KMK feature label, release-note heading, implementation report, `CURRENT_STATE.md`,
  `NEXT_WORK.md`, `RECOMMENDATION_VERSIONING.md`, and APK filename must all name the same
  release identity.
- A `-fix<N>` label is still part of its parent feature family. It is not a new feature phase.
  Example: the corrective follow-up to v0.7.42 is `KMK-Recs v0.7.42-fix1`, code `743`, APK
  `Komikku-v1.13.6-kmk.7.42.1-debug.apk`.
- Do not infer Android package install compatibility from this local marker. The upstream
  Android `versionCode` and `versionName` remain authoritative for Android install/update
  behavior and live in `app/build.gradle.kts`.
- If a release must install over a previously distributed APK, verify the Android package
  version separately before handoff. Do not attempt to solve that by changing only
  `KmkRecsReleaseNotes.VERSION_CODE`.

### Historical Versioning Note

Entries before this rule may contain labels such as `v0.6.22`, `v0.7.4`, or a numerical code
that no longer follows the current monotonic convention. Treat those as an archival record,
not as an active rule. New documentation must cite the canonical convention above rather than
copying an older entry.

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

### KMK-Recs v0.7.11 through v0.7.34

Implemented in individual passes. See `docs/recommendations/` implementation reports for details.
Notable milestones: v0.7.14 (sort chips in Loved Manga), v0.7.26 (min chapter filter), v0.7.28 (Seen key backup), v0.7.30 (link group management), v0.7.31 (enrichment cap setting), v0.7.32–34 (source stats 30-day rolling, Top Picks contribution count, evaluation error labels, retry button, profile-changed banner).

### KMK-Recs v0.7.35

Rated Manga entry points (Liked/Disliked) and group-seeded recommendations.

Files changed: see `docs/recommendations/KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_IMPLEMENTATION.md`

APK: `Komikku-v1.13.6-kmk.7.35-debug.apk` (VERSION_CODE 735)

### KMK-Recs v0.7.36

Rated Manga UI parity, Library toolbar shortcuts, group-seeded recommendation crash fix, and discoverability.

Files changed:
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt` — full rewrite; shared `RatedMangaCollectionContent` composable with full feature set (export, sort, grouping, link management, version badge, explore overlay for LOVE/LIKE)
- `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt` — simplified to delegation via `RatedMangaCollectionContent`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt` — inject `NetworkToLocalManga`, localize all results before emitting
- `app/src/main/java/eu/kanade/presentation/library/components/LibraryToolbar.kt` — 3 optional callbacks for Loved/Liked/Disliked shortcuts
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt` — wire callbacks
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=736
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — `rec_bundle_export_liked_manga`, `rec_bundle_export_disliked_manga`

Tests run:
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tasks, full suite)
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.36-debug.apk` (VERSION_CODE 736)

### KMK-Recs v0.7.37

Group-seeded recommendations bounded runtime, source-aware dedup, cross-source rating exclusivity, and CancellationException propagation fix.

Files changed:
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt` — new constants (MAX_SOURCES=5, RAW_CAP_PER_SOURCE=8, TARGET_RESULTS=20, SEARCH_TIMEOUT_MS=12s, LOCALIZE_TIMEOUT_MS=5s, TOTAL_LOAD_TIMEOUT_MS=45s); extracted `buildRecommendations()` loop; `withTimeoutOrNull` around full load and per-candidate localization; early exit at TARGET_RESULTS via `break@outer`; source-aware `(sourceId, url)` dedup via `GroupRecommendationLoopPolicy`; CancellationException rethrow in per-source catch
- `app/src/main/java/exh/recs/group/GroupRecommendationLoopPolicy.kt` — new pure helper object with `CandidateState`, `tryAcceptNetwork`, `tryAcceptLocal`, `reachedTarget`
- `app/src/main/java/exh/recs/loved/LovedMangaSourceFilter.kt` — new `resolveLinkedGroupRatingConflicts` function; for confirmed cross-source link groups with conflicting member ratings, keeps only members matching the most-recently-updated rating
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt` — load link groups before rating filter; apply `resolveLinkedGroupRatingConflicts` before `filterRating` so each confirmed group appears in exactly one rating tab
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=737
- `app/src/test/java/exh/recs/group/GroupRecommendationLoopPolicyTest.kt` — 13 new tests covering tryAcceptNetwork (new, duplicate, same-url different-source, seed member, non-seed on different source), tryAcceptLocal (new, duplicate, independent ids), reachedTarget (below/equal/above/zero), and combined flow
- `app/src/test/java/exh/recs/loved/RatedMangaExclusivityTest.kt` — 9 new tests covering standalone kept, same-rating group kept, love-newer-than-like, like-newer-than-love, dislike-newest, standalone unaffected, rating overwrite display, empty list, key-not-in-map standalone

Tests run:
- `:app:spotlessApply` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tasks, full suite, all PASSED)
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.37-debug.apk` (VERSION_CODE 737)

### KMK-Recs v0.7.38

For You candidate discovery memory and group-seeded recommendation enrichment.

Files changed: see `docs/recommendations/KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_AND_GROUP_SEED_ENRICHMENT_IMPLEMENTATION.md`

Key additions:
- `recommendation_candidate_memory` SQLDelight table (migration 56, local-only, NOT in backup/sync)
- `RecommendationCandidateMemoryStore`, `RecommendationCandidateMemoryRanker`, `RecommendationDiscoveryPlanner`
- `GroupSeedTag`, `GroupSeedRecommendationScorer`, rewritten `GroupRecommendationSeedBuilder`
- Reset For You discovery history button in Settings → Management

Tests run:
- `:app:spotlessApply` — BUILD SUCCESSFUL
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tasks, full suite, all PASSED)
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.38-debug.apk` (VERSION_CODE 738)

### KMK-Recs v0.7.42

Source Evidence Redesign: `SourceEvaluationScorer` scores catalogue fit (Popular/Latest samples)
only and reuses `PersonalRecommendationScorer` for per-item taste matching; the scorer's own
internal search probe was removed; `catalogueMetadataConfidence` added; `SourceRecommendationFitEligibility`
fails open toward probing when catalogue metadata confidence is low/unknown; staleness parity added
to `source_recommendation_fit` (migrations 59/60); UI relabeled "Recommendations" → "For You search"
where the value represents search compatibility.

Files changed: see `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`

Tests run:
- `:app:spotlessApply` — BUILD SUCCESSFUL
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tasks, full suite, all PASSED)
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.42-debug.apk` (VERSION_CODE 742)

### KMK-Recs v0.7.42-fix1 (corrective follow-up, not a new feature phase)

Per the Canonical Versioning Rule above: this is a corrective follow-up to `v0.7.42`, using the next
monotonic `VERSION_CODE` and a `.1` APK suffix — not a new `v0.7.43` feature line.

Codex review after the v0.7.42 implementation found that several downstream Source Evaluation UI/queue
consumers still used pre-v0.7.42 assumptions even though the core scorer/schema/tests were
build-healthy:

- `SourceRecommendationQualityQueue.compute()` used a hardcoded `{STRONG_FIT, WORTH_TRYING}`
  "promising" gate instead of `SourceRecommendationFitEligibility.check(...)`, so a WEAK/NEUTRAL
  catalogue verdict with LOW/UNKNOWN metadata confidence — eligible under the v0.7.42 policy — was
  invisible to the manual "Check search compatibility"/"Re-check all" actions.
- The same queue only checked whether a `SourceRecommendationFit` row existed, not whether it was
  current (`evaluationVersion`/`expiresAt`, added in v0.7.42), so pre-redesign compatibility results
  could be silently treated as still valid.
- `SourceRecommendationQualityDiagnostics.compute()` duplicated the same hardcoded gate, so its
  counts could disagree with the queue and the automatic evaluation path.
- The Source Evaluation row subtitle still displayed `SourceEvaluation.searchReliabilityScore` as
  `search N%`, even though that field is intentionally always `0.0` as of v0.7.42 — a misleading,
  fabricated figure.

Fix: `SourceRecommendationFitEligibility` gained two shared functions —
`isProbeEligible(evaluation)` and `isFitCurrent(fit, now)` — that both the queue and diagnostics now
call, so there is one source of truth for probe eligibility and fit staleness. The row subtitle now
shows catalogue metadata confidence (new KMR strings) instead of the fabricated search percentage.
v0.7.41 documentation that said v0.7.42 was still planning-only was corrected to note it was
implemented later, without rewriting the historical record.

No database migration was added.

Files changed: see `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md`

Tests run:
- `:app:testDebugUnitTest --tests "*.SourceRecommendationQualityQueueTest"` — BUILD SUCCESSFUL (17 tests, all PASSED)
- `:app:testDebugUnitTest --tests "*.SourceRecommendationQualityDiagnosticsTest"` — BUILD SUCCESSFUL (16 tests, all PASSED)
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tasks, full suite, all PASSED)
- `:app:spotlessApply` / `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.42.1-debug.apk` (VERSION_CODE 743)

### KMK-Recs v0.7.42-fix2 (corrective follow-up, not a new feature phase)

Per the Canonical Versioning Rule above: this is a second corrective follow-up to `v0.7.42`, using the
next monotonic `VERSION_CODE` and a `.2` APK suffix — not a new `v0.7.43` feature line.

A code review after v0.7.42-fix1 found that the remaining presentation/action paths still carried old
assumptions, even though fix1 correctly unified automatic evaluation, the manual queue, diagnostics,
fit staleness, and the catalogue row subtitle:

- `SourceEvaluationResultList.SortMode.SEARCH_RELIABILITY` sorted a retired field
  (`SourceEvaluation.searchReliabilityScore`), which v0.7.42 intentionally always writes as `0.0`.
- `BEST_FIT` still used that same retired field as a tie-breaker.
- Individual row labels used a separate hardcoded Strong Fit/Worth Trying check rather than
  `SourceRecommendationFitEligibility.isProbeEligible`, and could display an expired or
  older-version `SourceRecommendationFit` as if it were a current result.
- The action area could check missing fits or re-check every fit, but had no way to target only
  stale (outdated) fits, and "Re-check all" silently excluded them.
- `CURRENT_STATE.md` still described the pre-v0.7.42 (`STRONG_FIT`/`WORTH_TRYING`-only) eligibility
  rule without a historical qualifier, and still listed "Search reliability" as if it were an active
  sort mode.

Fix: one new pure, Android-free `SourceRecommendationFitDisplayPolicy` resolves every source to exactly
one truthful `CompatibilityDisplayState` (`INELIGIBLE`, `NOT_CHECKED`, `OUTDATED`, `GREAT`, `GOOD`,
`MIXED`, `WEAK`, `NO_MATCHES`, `ERROR`) by reusing `SourceRecommendationFitEligibility.isProbeEligible`/
`isFitCurrent` — no new score, no duplicated verdict/confidence/version/expiry logic. The queue
(`SourceRecommendationQualityQueue`), diagnostics (`SourceRecommendationQualityDiagnostics`), row labels
(`SourceEvaluationScreen`), and sorting (`SourceEvaluationResultList`) all resolve through this one
policy. The queue gained a fourth bucket (`outdatedPromising`, distinct from `missingPromising`); a new
`recheckOutdatedRecommendationQuality()` action targets only stale fits; "Re-check all" now correctly
includes outdated rows. `SortMode.SEARCH_RELIABILITY` was replaced with `SortMode.FOR_YOU_COMPATIBILITY`,
which orders by `SourceRecommendationFitDisplayPolicy`'s seven-bucket rank (current positive/weak/
no-matches/error, then outdated, then not-checked, then ineligible); `BEST_FIT` remains catalogue-first,
using current compatibility as a true tie-breaker only after catalogue verdict/fit/quality are equal.

No database migration was added.

Files changed: see `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_IMPLEMENTATION.md`

Tests run:
- `:app:testDebugUnitTest --tests "*.SourceRecommendationFitDisplayPolicyTest"` — BUILD SUCCESSFUL (27 tests, all PASSED)
- `:app:testDebugUnitTest --tests "*.SourceRecommendationQualityQueueTest"` — BUILD SUCCESSFUL (20 tests, all PASSED)
- `:app:testDebugUnitTest --tests "*.SourceRecommendationQualityDiagnosticsTest"` — BUILD SUCCESSFUL (18 tests, all PASSED)
- `:app:testDebugUnitTest --tests "*.SourceEvaluationResultListTest"` — BUILD SUCCESSFUL (21 tests, all PASSED)
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (267 tasks, full suite, all PASSED)
- `:app:spotlessApply` / `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.42.2-debug.apk` (VERSION_CODE 744)

### KMK-Recs v0.7.43 (new feature release)

Per the Canonical Versioning Rule above: this is a new feature line (not a `-fixN` corrective
follow-up), using the next monotonic `VERSION_CODE` and no APK sub-suffix.

Three related coherence problems, addressed together:

1. **For You search compatibility ran in `screenModelScope`, not as a background job.** Leaving the
   Source Evaluation screen could stop a check in progress, unlike full Source Evaluation (which
   already ran as a `SourceEvaluationJob` WorkManager job). Fix: a new, separate
   `SourceRecommendationQualityJob` (`SourceRecommendationQualityJob:active` unique work name, never
   reusing `SourceEvaluationJob:active`) with its own process-scoped state bridge
   (`SourceRecommendationQualityJobState`), notifier/channel (`CHANNEL_SOURCE_RECOMMENDATION_QUALITY`,
   IDs `-803`/`-804`), and runner (`SourceRecommendationQualityRunner`, extracted verbatim from the old
   screen-model probe loop). A `ScreenErrorKey.JobConflict` guard prevents Source Evaluation and the
   compatibility job from running at the same time (both may temporarily install extensions).
2. **Loved/Liked group recommendations used a separate one-grid search flow**
   (`GroupSeededRecommendationsScreenModel`) instead of the manga-detail Recommendations page's
   provider/extension row pattern. Fix: `RecommendsScreen`/`RecommendsScreenModel` gained a
   `CrossSourceGroupSeed` route that reuses `RecommendationPagingSource.createSources(...)` and
   `CrossExtensionGenreSearchSource`, seeded by every confirmed linked version via the existing
   `GroupRecommendationSeedBuilder`. Seed members are excluded from results; cross-extension rows
   search by the group's combined/weighted tags; `GroupSeedRecommendationScorer` contributes a
   group-tag-aware score bonus on top of the existing single-manga `RecommendationScorer`. The old
   `GroupSeededRecommendationsScreen`/`GroupSeededRecommendationsScreenModel`/
   `GroupRecommendationLoopPolicy` were removed as dead code once nothing referenced them.
3. **"Seen" sounded neutral but the desired behavior is a mild negative signal.** Fix: user-facing
   copy renamed to "Not interested" (`rec_mark_seen`, `rec_clear_seen`, `rec_match_title_seen`, and the
   cross-extension-match apply/applying strings). Internal storage is unchanged —
   `SeenRecommendationMangaStore`, `seenRecommendationMangaKeys()`, and backup proto field 626 all keep
   their existing names/format for compatibility; only the UI label changed. For You scoring
   (`BrowsePersonalRecommendationsScreenModel`) now builds a scoring-only adjusted `TasteProfile` that
   applies `NOT_INTERESTED_WEIGHT = -0.3` per genre occurrence for genres found on already-locally-known
   Not Interested manga (bounded lookup, never triggers a network call) — roughly 6-7x weaker than a
   Dislike rating's `-2.0` per occurrence. Query-tag selection still uses the raw profile; only ranking
   is affected. This never writes to the ratings table, so it does not count toward the reassessment
   threshold. Exact Not Interested/Seen manga remain hard-excluded from both For You (pre-existing) and
   group recommendations (added — this exclusion had been dropped when the group screen was replaced
   in the same release; see the implementation report for detail).

No database migration was added; no backup schema changed.

Files changed: see
`docs/recommendations/KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_IMPLEMENTATION.md`

Tests run:
- `:app:testDebugUnitTest --tests "*SourceRecommendationQuality*"` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest --tests "*SourceEvaluation*"` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest --tests "*Group*Recommendation*"` — BUILD SUCCESSFUL (7 tests, all PASSED — no Phase B/C-specific new tests were added; existing `GroupRecommendationSourcePolicyTest`/`GroupSeedEnrichmentTest` still pass against the reused seed builder/scorer)
- `:app:testDebugUnitTest --tests "*Seen*"` — BUILD SUCCESSFUL (backup round-trip and cross-extension-match route tests confirm storage compatibility survived the copy-only rename)
- `:app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicy*"` — 3 of 16 tests FAILED; pre-existing and unrelated (see Deviations below)
- `:app:testDebugUnitTest` — 928 tests, 3 failed (same pre-existing 3), 1 skipped, rest PASSED
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.43-debug.apk` (VERSION_CODE 745)

Deviations from the plan:
- No new focused unit tests were added for Phase A/B/C behavior (target job state transitions, group
  seed exclusion, Not Interested weighting). Existing tests for the reused pure helpers
  (`SourceRecommendationQualityQueue`, `GroupRecommendationSeedBuilder`/`GroupSeedRecommendationScorer`,
  `SeenRecommendationMangaStore`) continued to pass unchanged, and compile + manual reasoning verified
  the new wiring, but net-new test coverage for the new job/route/scoring code itself was not written.
- `RecommendationCandidateVisibilityPolicy` (favorite/rated/known/min-chapter) is not applied to
  group-seeded recommendations — only exact seed-member and exact-Seen exclusion are. The single-manga
  Recommendations page these rows now share code with never applied that policy either, so this keeps
  the two paths symmetric rather than adding new filtering asymmetrically.
- The mild Not Interested scoring penalty is wired into For You only, not group recommendations
  (group recs still get exact-Seen hiding, matching the plan's "must remain hidden" requirement).
- Pre-existing test failures unrelated to this work: 3 cases in `RecommendationCandidateVisibilityPolicyTest`
  throw `InjektionException` for `GetCustomMangaInfo` whenever the test helper builds a
  `Manga.copy(favorite = true)` — `Manga.kt:42-46` eagerly resolves that interactor via Injekt when
  `favorite` is true, and unit tests never register the binding. Reproduces in complete isolation with
  or without any of this release's changes; not touched by this work.

### KMK-Recs v0.7.44 (corrective/consolidation release, not a UI redesign)

Follow-up to v0.7.43's structural moves (background compatibility job, row-based group recs), fixing
the behavioral and test gaps its own implementation report flagged as deviations:

1. **Fixed the 3 pre-existing `RecommendationCandidateVisibilityPolicyTest` failures for real**, plus
   2 more test classes with the same latent bug (`BrowsePersonalRecommendationsFilterTest`,
   `RecommendationCandidateMemoryRankerTest`). Root cause: `Manga.kt`'s constructor eagerly resolves
   `GetCustomMangaInfo` via Injekt whenever `favorite = true`, and unit tests never bootstrap the real
   Injekt graph. Fixed by registering a no-op `GetCustomMangaInfo` binding via a shared
   `TestInjektSupport.ensureCustomMangaInfoBound()` helper called from each affected test class's
   `@BeforeAll` — no production code touched. Full suite is clean: 949 tests, 0 failures.
2. **New shared query-attempt policy** (`RecommendationQueryAttemptPolicy`, pure/Android-free):
   builds the strict-to-lenient tag chain (`TOP_TAGS_FILTER` → `TAG_PAIR`/`SINGLE_STRONGEST_TAG` →
   `TEXT_ONLY_TOP_TAGS`, capped at 3 attempts) and classifies outcomes into typed
   `RecommendationQueryFailureKind`s (`NO_RAW_RESULTS`, `FILTER_UNSUPPORTED`, `FILTERED_UNRELATED`,
   `WEAK_METADATA`, `SOURCE_EXCEPTION`, `CANCELLED`). Used directly by `CrossExtensionGenreSearchSource`
   (both single-manga and group rows) for the tag chain plus a new bounded title fallback. For You's
   existing `RecommendationQueryPlanner` was deepened (`MAX_STRATEGIES_PER_SOURCE` 2 → 3) to walk the
   same fallback chain rather than stopping after one fallback, so both paths now follow the same
   strict-to-lenient principle even though they're two coordinated pure objects, not one shared class
   (`RecommendationQueryPlanner` keeps its `lastSuccessful`-first persisted-strategy behavior, which
   the new one-shot `RecommendationQueryAttemptPolicy` doesn't need).
3. **Group recommendations now actually use the whole linked group**: `CrossExtensionGenreSearchSource`
   gained a `titlesOverride` (fed `GroupRecommendationSeed.titles` — every linked version's title) used
   as a bounded (max 2) fallback search once every tag attempt fails. `RecommendationPagingSource`/
   `RecommendsScreenModel` thread this through unchanged for the single-manga path (`titlesOverride =
   null` falls back to `manga.ogTitle` only, as before).
4. **Group recommendations now honor source selection and visibility policy**: cross-extension rows use
   `RecommendationSourceSelector.select(...)` (language/priority/disabled/disliked) instead of raw
   visible-source order, and candidates go through the full `RecommendationCandidateVisibilityPolicy`
   (favorite/rated/seen/known/min-chapter), batched once per row rather than per candidate. Both are
   scoped to the `CrossSourceGroupSeed` path only — single-manga recommendations are unchanged.
5. **Group rows now filter out near-zero-relevance candidates** (`GROUP_RELEVANCE_MIN_SCORE = 0.1`)
   instead of only sorting them last, when the seed has real tag evidence.
6. **For You**: a source's "successful strategy" is no longer persisted when the final attempt in the
   chain produced zero results — previously a source could get "locked" onto a strategy that never
   actually worked, just because it was the last one tried.
7. **New pure test coverage** for the background For You search compatibility job's conflict-guard
   decision (`SourceRecommendationQualityJobConflictPolicy`, extracted from two inline `isRunning()`
   checks in `SourceEvaluationScreenModel`) and for `SourceRecommendationQualityRunner.loadAvailableExtensions()`,
   which now does a bounded (5s) wait for `availableExtensionsFlow` to warm up instead of returning
   empty immediately if the job starts right after app startup.
8. **Phone UI density pass** (Source Evaluation, Recommendations Settings): `EvaluationResultRow`'s
   error-kind badge and reason-hint text are now collapsed behind a per-row "Show details" toggle
   (`rememberSaveable(evaluation.evaluationKey)`) instead of always shown; the 13 previously-hardcoded
   English failure-kind labels are now KMR strings. The Shizuku setup card's action row, the
   missing/outdated/recheck-all action row, and the source-suggestion install/dismiss/like/dislike row
   now use `FlowRow` instead of `Row` so they wrap instead of crowding on a 360dp-wide phone.
9. **Not Interested unchanged** — no MarkSeen/Not Interested behavior changes in this release per
   explicit instruction; storage/proto field 626 untouched.

No database migration was added; no backup schema changed; no new background job or network behavior
was introduced (the bounded `availableExtensionsFlow` wait reuses the existing job's existing flow).

Files changed: see
`docs/recommendations/KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_IMPLEMENTATION.md`

Tests run:
- `:app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicyTest*"` — BUILD SUCCESSFUL (16/16 PASSED)
- `:app:testDebugUnitTest --tests "*RecommendationQueryPlanner*"` — BUILD SUCCESSFUL (14/14 PASSED)
- `:app:testDebugUnitTest --tests "*GenreFilterMapper*"` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest --tests "*Group*Recommendation*"` — BUILD SUCCESSFUL (7/7 PASSED)
- `:app:testDebugUnitTest --tests "*SourceRecommendationQuality*"` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest --tests "*Seen*"` — BUILD SUCCESSFUL
- `:app:testDebugUnitTest` — 949 tests, 0 failed
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.7.44-debug.apk` (VERSION_CODE 746)

Deviations from the plan:
- Phase D (For You uses the shared attempt policy) was implemented as "`RecommendationQueryPlanner`'s
  existing fallback chain deepened to 3 steps" rather than literally swapping in
  `RecommendationQueryAttemptPolicy` at the For You call site — the plan explicitly allowed either
  shape ("Keep `RecommendationQueryPlanner` as the deterministic planner, **or** create a small
  companion"). Reusing the existing, already-integrated planner (persisted per-source strategy,
  discovery-progress recording, candidate memory) was far lower-risk than rewiring `searchSource()`'s
  ~300-line loop around a new abstraction.
- Phase D items 2 ("filtered as unrelated" status) and 4 (don't poison rolling discovery on one strict
  failure) were verified as **already correct** in the existing code (per-plan `STATUS_SUCCESS`/
  `STATUS_FILTERED`/`STATUS_EMPTY` progress classification; progress is only recorded once a plan
  succeeds or the chain is exhausted) rather than requiring new code — confirmed by reading
  `searchSource()`, not assumed.
- Phase G (phone UI pass) was scoped to the explicitly-named highest-risk spots (Shizuku card, rec-
  quality actions, source-suggestion row, `EvaluationResultRow` diagnostics) rather than an exhaustive
  review of all ~59 button/row sites in `SourceEvaluationScreen.kt`. `RatedSortRow`'s `FilterChip` row
  and the settings page's tag-preference `FlowRow`s were reviewed and found already compliant (existing
  `horizontalScroll`/`FlowRow` usage). No screenshot/device testing was available — verification was by
  Compose layout code inspection only, as the plan permits when device testing isn't available.
- No new SQLDelight table, no new backup field — none were needed.

### KMK-Recs v0.7.45 (final v0.7 closure + public release readiness pass)

Retroactive entry — this release shipped with its own implementation report but was never added here;
added in v0.7.46 per the public-polish closeout plan's explicit instruction to fill the gap.

Combined the last approved small v0.7 feature window with public-release hardening: Rated Manga
(Loved/Liked/Disliked) grouped display now defaults on (and the toggle survives reactive reloads,
fixing a real bug where it silently reset); Top Picks contribution count surfaced compactly on the
source fit badge; non-installed recommendation-quality probes now require the Private installer or are
refused (closing the `PromptRequired`-log-only public-safety gap); the one genuine raw-exception-to-UI
path (`SourceEvaluationRunner`'s per-source probe catch → `EvaluationResultRow` subtitle) was classified
via a new `SourceEvaluationProbeErrorClassifier`; confirmed OCR ships in the same build as KMK-Recs
(including `kmkPublicTest`) and corrected docs that claimed otherwise.

Full detail: `docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_IMPLEMENTATION.md`.

Tests: `:app:testDebugUnitTest` — 954 tests, 0 failures. `spotlessCheck`, `:app:assembleKmkPublicTest`,
`assembleDebug` all passed. `:app:lintKmkPublicTest` was **not** run (documented gap, closed in v0.7.46).

APK: `Komikku-v1.13.6-kmk.7.45-debug.apk` (private) / `Komikku-KMK-PublicTest-v1.13.6-kmk.7.45-debug.apk`
(public, `app.komikku.kmk`) (VERSION_CODE 747)

### KMK-Recs v0.7.46 (public polish closeout)

Follow-up cleanup pass closing the gaps v0.7.45's own implementation report flagged as deviations/limitations:

1. **OCR error/privacy polish.** New `OcrErrorClassifier` (pure, 6-way:
   `Storage`/`ImageDecode`/`NoDownloadedPages`/`Cancelled`/`PermissionOrFileAccess`/`Internal`) replaces
   every `e.message`/`"Unknown error"` UI-facing OCR path (`OcrSearchScreenModel`'s search/clear/clear-old-rows
   catches, `OcrIndexService`'s enumerate-pages and per-page failure catches). New failed OCR rows store a
   stable key in `ocr_indexed_page.error_message` instead of raw exception text; old rows with raw text are
   unaffected (that field was never actually displayed in normal UI — see the implementation report).
   OCR page-error logcat no longer includes manga title or chapter name, only numeric IDs.
2. **OCR deletion/storage controls.** `OcrIndexRepository.deleteByManga`/`deleteByChapter` (already
   existed) are now reachable from a per-result overflow menu in `OcrSearchScreen`, each with its own
   confirm dialog stating downloaded images are unaffected. Added a compact "Clear empty/failed rows"
   button (`deleteEmptyAndFailed`, already existed, was unused) shown only when relevant.
3. **Non-OCR error hygiene.** Fixed a second raw-exception-to-UI instance in `SourceEvaluationRunner`
   (`recordExtensionError`, same `EvaluationResultRow` path as v0.7.45's fix, missed previously). New
   shared `RecommendationErrorClassifier` (`Network`/`Timeout`/`Cancelled`/`FileAccess`/`Internal`) fixes
   3 genuinely-displayed raw-exception sites in `BestVersionCompareScreenModel`/`BestVersionCompareScreen`
   and the bundle-import file-read path in `RecommendationBundleImporter`. Several other `e.message`
   hits (`RecommendationBundleImportScreenModel`, `RecommendationBundleLibraryAdder`, the various job/queue
   `errorMessage` fields) were confirmed diagnostic-only/never-rendered by tracing every consumer — see
   the implementation report for the full per-hit breakdown.
4. **Docs.** Filled in this file's missing v0.7.45 entry (above); marked
   `KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md` historical; cleaned mojibake from
   `docs/recommendations/README.md`; fixed the wrong `KmkRecsReleaseNotes.kt` path in the encyclopedia
   (if it was wrong); public README now says `v0.7.46` and "debug-signed public test build."

No database migration, no backup/proto field changes, no OCR build-line split (OCR remains included in
both build lines, per explicit instruction for this pass).

Full detail: `docs/community/KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_IMPLEMENTATION.md`.

### KMK-Recs v0.7.47 (Source Evaluation tag enrichment and scoring fix)

Root-cause fix for the evidence-pipeline defect found in
`docs/recommendations/KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md`: catalogue-fit scoring
never enriched Popular/Latest samples missing genre tags, so many sources were effectively being
scored on "does the list page expose tags?" instead of real taste fit. Implemented per
`docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md`:

1. **Bounded catalogue detail enrichment.** New `SourceEvaluationCatalogueEnricher.enrich()` (pure,
   unit-tested with a fake `CatalogueSource`) deduplicates Popular+Latest samples by URL, then calls
   `source.getMangaDetails()` sequentially for up to 12 samples lacking genre metadata, each wrapped
   in a 10s `withTimeoutOrNull` + `runCatching`, rethrowing `CancellationException`. A failed/timed-out
   call keeps the original list-entry candidate; enriched results are never written to the app manga
   table (evidence-only) and no chapter lists or page images are fetched. Wired into
   `SourceEvaluationRunner.probeAndScore()` under a new `EnrichingDetails` queue phase.
2. **Split evidence counters (migration 61, 9 additive columns, all default 0).**
   `detail_enrichment_attempt_count`, `detail_enrichment_success_count`, `metadata_candidate_count`,
   `positive_candidate_count`, `negative_candidate_count`, `explicit_preferred_group_hit_count`,
   `learned_positive_group_hit_count`, `blocked_candidate_count`, `adult_signal_candidate_count`.
   `preferred_tag_match_count`/`blocked_tag_match_count` are kept and now mirror
   `positive_candidate_count`/`blocked_candidate_count` for backward compatibility.
3. **Revised fit-score formula and verdict order.** Ratio-based fit score (positive/negative/
   blocked/adult candidate ratios, metadata-confidence penalty) replaces the old raw
   preferred-minus-blocked count. `NEEDS_MANUAL_REVIEW` now covers metadata-sparse evidence (`UNKNOWN`
   confidence, or `LOW` confidence with any positive signal) instead of a confident `WEAK`.
   `STRONG_FIT`/`WORTH_TRYING` now additionally require low blocked/adult-risk ratios, so a source
   cannot reach Strong Fit purely from broad positive tags alongside heavy BL/GL/adult/blocked content.
4. **Staleness bump and enforcement.** `SourceEvaluationKeys.CURRENT_VERSION`: `2 -> 3` — every
   existing row (v1 or v2) is now stale. New `SourceEvaluationDisplayPolicy`
   (CURRENT/OUTDATED_VERSION/EXPIRED/METADATA_SPARSE/ERROR) drives an "Outdated — reassess needed"
   row subtitle and a `BEST_FIT` sort fix so a stale row (any verdict) can never outrank a current
   row. `SourceRecommendationFitEligibility.check()` gained a `STALE_EVALUATION` result, checked
   before the verdict/confidence gates, so a stale catalogue row can no longer feed the search-
   compatibility probe queue as if it were current evidence.
5. **No cross-cutting changes.** Catalogue fit and For You search compatibility
   (`SourceRecommendationFitProbe`/`SourceRecommendationFit`) remain fully separate signals — neither
   file was touched. Source Evaluation remains strictly one-extension-at-a-time. No source-specific
   (Elf Toon/KaliScan/etc.) hacks were added.

No backup/proto field changes. No new user-facing screens; `SourceEvaluationScreen`'s existing row
subtitle and verdict badge machinery render the new states.

Full detail: `docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_IMPLEMENTATION.md`.

### KMK-Recs v0.8.0 (Rated Manga bulk selection + group actions, private feature)

Implemented per
`docs/recommendations/KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_PLAN.md`:

1. **Selection mode replaces the hidden long-press recommendation gesture.** Long-press in
   Loved/Liked/Disliked (`RatedMangaScreen.kt`/`RatedMangaCollectionContent`) now enters bulk
   selection and selects the pressed item; a top-right "Select" app-bar action does the same. Tap
   toggles selection while in that mode; outside it, tap still opens the manga as before.
2. **Phone-friendly selection UI.** App bar shows selected count + close; a bottom action bar
   exposes Change (rating), Clear (rating), Group (merge selected, or select-all-in-group for a
   single confirmed-group selection), and More (Mark not interested, Remove from group).
3. **Per-item action menu**, grouped into Recommendation Actions (See recommendations — the
   pre-existing single-entry `RecommendsScreen.Args.SingleSourceManga` route, confirmed distinct
   from group recommendations; See group recommendations — confirmed-group-with-2+-versions only,
   reuses `RecommendsScreen.Args.CrossSourceGroupSeed` unchanged; Find other versions — reuses
   `CrossExtensionMatchScreen.fromMode(_, CrossExtensionMatchMode.Rating(_))`; Favorite other
   versions — reuses the existing `CrossExtensionMatchMode.Favorite` mode, not a new deferred
   action), Rating Actions (Change/Clear via `SetMangaTaste`/`ClearMangaTaste`; Mark not interested
   via the existing `SeenRecommendationMangaStore` preference store), and Group Actions (Manage
   group, View linked versions, Set primary version, Select all in group, Merge selected into
   group, Remove from group, Ungroup).
4. **New focused version-list screen** — `exh/recs/links/LinkedVersionListScreen.kt` +
   `LinkedVersionListScreenModel.kt` — loads directly from persisted group data by `groupId`
   (independent of the rated screen's in-memory display state). Shows source name/language, title,
   rating, favorite status, installed/missing status, last-updated, and a primary marker per row.
   Missing/uninstalled sources render as "Source not installed," never crash.
5. **User-selected primary version** — new additive table `manga_cross_source_group_primary`
   (migration 62: `group_id TEXT PRIMARY KEY, source INTEGER, url TEXT, updated_at INTEGER`), new
   domain model `CrossSourceGroupPrimary` + `GetCrossSourceGroupPrimary`/`SetCrossSourceGroupPrimary`/
   `ClearCrossSourceGroupPrimary` interactors, new `TasteRepository`/`TasteRepositoryImpl` methods.
   `RatedGroupPrimaryResolver` (pure, unit-tested) resolves which member controls the grouped
   display's cover/title: the stored primary wins only if it's present among the group's currently
   loaded members; otherwise falls back to the grouper's own primary-key choice — a missing/
   uninstalled stored primary never crashes. Recommendations are unaffected — `CrossSourceGroupSeed`
   still seeds from full group metadata via the unchanged `GroupRecommendationSeedBuilder` path.
6. **`LinkGroupManagementScreen` gained an optional `focusedGroupId`** (instead of a new global
   manager) for the "Manage Group" action.
7. **Group merge is manual-selection-only** — `RatedGroupMergePlanner` (pure, unit-tested): with 0
   existing groups among the selection, creates a new group; with exactly 1, reuses it; with 2+,
   merges into the lexicographically-first target and folds every member of every merged-away group
   (not just the selected subset) into the target. Titles are used only as fallback display text for
   brand-new link rows, never to decide grouping.
8. **Confirmations required**: Clear rating, Merge selected into group, Remove from group, Ungroup,
   and Mark not interested. Clearing a rating never touches `manga_cross_source_link` or
   `manga_cross_source_group_primary` rows; removing from a group/ungrouping never touches
   `manga_taste` rows.
9. **All new user-facing strings are KMR strings** (`rated_manga_*`, `linked_version_list_*`).

**Deviation from the plan (documented, not silent):** the plan asked to check whether
`manga_cross_source_link` is included in backup/sync and, if so, "include primary-version data
consistently." It is included (proto 624, confirmed via
`docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`). This pass deliberately does **not** add
backup/sync/proto support for the new `manga_cross_source_group_primary` table — doing so would
require touching the backup proto schema, `BackupCreator`/`TasteBackupCreator`,
`BackupRestorer`/`TasteRestorer`, and `SyncManager`/`SyncService`, which was judged out of scope for
this UI/management-layer pass. The primary-version selection is local-only until a follow-up adds
it. See the implementation report for the full list of files changed and tests run.

No database migration to `manga_cross_source_link` itself (only additive migration 62 for the new
table). No change to `SourceEvaluationScorer`, For You, or any other recommendation-scoring path.

Full detail: `docs/recommendations/KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_IMPLEMENTATION.md`.

### KMK-Recs v0.8.1-fix1 (Rated Manga + Source Evaluation polish, private corrective follow-up)

**Private build only. No public release was prepared or requested.** Fixes five gaps found after
v0.8.0/v0.7.47, per
`docs/recommendations/KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_PLAN.md`:

1. **Linked-version remove confirmation.** `LinkedVersionListScreen`'s delete action previously
   called `screenModel.removeFromGroup(row.key)` directly with no confirmation. It now shows an
   `AlertDialog` naming the version's title where resolvable (falls back to a generic message
   otherwise); confirm removes, dismiss cancels. Removal still only deletes the
   `manga_cross_source_link` row — never rating, favorite, history, or manga data.
2. **Group primary version backup/restore/sync (proto 627).** New
   `BackupCrossSourceGroupPrimary` model (`groupId`/`source`/`url`/`updatedAt`, proto numbers 1-4).
   Added to `Backup.kt` at `@ProtoNumber(627)` (626 was the next-available KMK taste field; 627 was
   confirmed unoccupied by inspection). `TasteBackupCreator.backupCrossSourceGroupPrimaries()` reads
   through `GetCrossSourceGroupPrimary`; `BackupCreator.backupCrossSourceGroupPrimaries(options)`
   gates on `options.tasteProfile`, matching every other taste-profile field. Restore:
   `TasteRestorer.restoreCrossSourceGroupPrimaries()`, called in `BackupRestorer` immediately after
   `restoreCrossSourceMangaLinks()` so the group already exists; merges by `groupId` (a backup
   containing more than one row per group keeps only the newest), skips invalid rows (blank
   `groupId`, `source == 0`, blank `url`), writes directly through `tasteRepository
   .upsertCrossSourceGroupPrimary()` (not `SetCrossSourceGroupPrimary`, which always stamps "now")
   so the backup's own `updatedAt` survives restore, and collects errors per group without aborting
   the rest of taste restore. Precedence logic extracted into pure, unit-tested
   `CrossSourceGroupPrimaryRestorePolicy` (`isValid`/`newestOf`/`shouldRestore`). Sync:
   `SyncManager` includes `backupCrossSourceGroupPrimaries` in the payload alongside cross-source
   links; `SyncService.mergeCrossSourceGroupPrimariesPure()` (a testable companion-object function,
   mirroring `mergeCrossSourceMangaLinks`' shape) merges local/remote by `groupId`, keeping the
   newer `updatedAt`, filtering blank-`groupId` rows.
3. **Select action no longer auto-selects.** `RatedSelectionReducer.enterEmpty()` enters selection
   mode without adding any key (preserves an existing selection if one is somehow already present,
   consistent with `enter()`'s additive behavior); `LovedMangaScreenModel.enterSelectionMode()`
   wires it to the app-bar "Select" action in `RatedMangaScreen.kt`, replacing the previous
   `displayItems.firstOrNull()` auto-select. Long-press (`enterSelection(key)`) is unchanged.
4. **Set Primary Version access clarified, not relocated.** Confirmed no direct rated-item-menu
   action exists for this (searched `RatedMangaScreen.kt`'s item menu — only `View linked versions`
   is present). Per the plan's preferred minimal path, this was kept as-is; `LinkedVersionListScreen`
   now shows a `linked_version_list_primary_hint` caption ("Tap the star to choose which version
   controls the cover and title shown in the rated list.") above the row list. No second primary
   picker was built.
5. **Source Evaluation evidence explainability.** New pure `SourceEvaluationEvidenceSummaryPolicy`
   (`evidenceFor(evaluation, displayState)`) decides whether a row should offer a "Details" toggle at
   all — returns `null` for outdated/expired rows (stale counters), `ERROR` rows (no samples
   collected), and zero-sample rows. `EvaluationResultRow` gained a second, independent
   expand/collapse block (`source_evaluation_details_toggle`/`_hide`, new strings) below the
   existing rec-quality-error details block, showing `source_evaluation_detail_enriched_count`
   (existing string), `source_evaluation_detail_enrichment_failed_count` (existing, shown only when
   > 0), the new `source_evaluation_metadata_sample_count`, `source_evaluation_positive_negative_summary`
   (existing), and `source_evaluation_verdict_review_explanation` (existing, shown only for
   `NEEDS_MANUAL_REVIEW`). No raw exception traces were added.

Tests added: `CrossSourceGroupPrimaryRestorePolicyTest` (8), extended `TasteBackupRoundTripTest`
(+3, proto 627 round-trip/coexistence/forward-compat), `SyncServiceCrossSourceGroupPrimaryMergeTest`
(8, local-only/remote-only/conflict-newer-wins/tie/blank-filtering), extended
`RatedSelectionReducerTest` (+3, `enterEmpty`), `SourceEvaluationEvidenceSummaryPolicyTest` (7).
Full suite: 1046 tests, 0 failures (up from 1016 pre-existing).

No database migration in this pass (proto-only backup field, per plan §Non-Goals). No change to
`SourceEvaluationScorer`'s scoring formulas, group recommendation scoring, or any other
recommendation-scoring path.

Full detail: `docs/recommendations/KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_IMPLEMENTATION.md`.

### KMK-Recs v0.8.1-fix2 (Version visibility + sync validation, private corrective follow-up)

**Private build only. No public release was prepared or requested.** `KmkRecsReleaseNotes
.VERSION_CODE = 752`, `VERSION_NAME = "KMK-Recs v0.8.1-fix2"`. Fixes a crash and two smaller gaps
found in v0.8.1-fix1, per
`docs/recommendations/KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_PLAN.md`:

1. **Loved Manga crash fixed.** The v0.8.0 group-primary interactors
   (`GetCrossSourceGroupPrimary`, `SetCrossSourceGroupPrimary`, `ClearCrossSourceGroupPrimary`)
   were never registered in `KMKDomainModule` — every screen model or backup/restore class that
   requested one via `Injekt.get()` (`LovedMangaScreenModel`, `LinkedVersionListScreenModel`,
   `TasteBackupCreator`, `TasteRestorer`) crashed with `InjektionException`. Fixed by adding the
   three `addFactory { ... }` registrations immediately after the existing cross-source-link-group
   registrations in `KMKDomainModule.kt`. Regression-guarded by a new
   `CrossSourceGroupPrimaryDomainModuleRegistrationTest`, which mirrors the exact registration
   pattern against a minimal fake `TasteRepository` and asserts all three interactors resolve via
   `Injekt.get()` — it does not invoke the real `KMKDomainModule.registerInjectables()` (that also
   registers many Android-only dependencies, e.g. a real `DatabaseHandler`, impractical in a pure
   JVM unit test), but directly guards against "forgot to add the addFactory line" for these three
   types specifically.
2. **KMK-Recs What's New sequencing verified and made explicit.** New pure `KmkRecsWhatsNewPolicy`
   (`hasUnseenChangelog`, `shouldShowKmkDialog`, `seenVersionCodeOnAcknowledge`) in
   `eu.kanade.presentation.more.settings.screen.about`, wired into `MainActivity.kt` and
   `KmkRecsWhatsNewScreen.kt`. Investigation confirmed `MainActivity`'s existing `if (showChangelog)
   {...} else if (showKmkChangelog) {...}` structure already prevents the normal Komikku changelog
   and the KMK changelog from rendering simultaneously — Compose recomposes the tree the instant
   `showChangelog` flips to `false`, so the KMK dialog reliably shows on the next frame rather than
   being suppressed. This pass named that behavior in a dedicated, unit-tested policy object instead
   of restructuring it (no functional sequencing bug was found — see the plan's own framing: "verify
   and repair the trigger/visibility path, not invent a separate release-note system"). Also fixed a
   minor Compose anti-pattern: `KmkRecsWhatsNewScreen`'s mark-seen write now runs inside a
   `LaunchedEffect(Unit)` instead of directly in the composable body. The compact update dialog
   (`KmkRecsWhatsNewDialog`) gained a one-line body via new KMR string `kmk_recs_updated_body`, so it
   is not just a bare title + two buttons. The manual About entry (`kmk_recs_whats_new` row, subtitle
   `KmkRecsReleaseNotes.VERSION_NAME`) was already correct and required no change.
3. **Group-primary sync validation hardened to match restore.**
   `SyncService.mergeCrossSourceGroupPrimariesPure()` previously only filtered blank `groupId` rows
   before merging — looser than restore's `CrossSourceGroupPrimaryRestorePolicy.isValid()` (blank
   `groupId`, `source == 0L`, or blank `url`). Now both paths call the same `isValid()` function
   (imported directly from the restore package — not architecturally awkward, both are subpackages
   of `eu.kanade.tachiyomi.data` in the same Gradle module, `internal` visibility is module-scoped).
   Merge behavior otherwise unchanged: merge by `groupId`, newer `updatedAt` wins, equal `updatedAt`
   keeps local.

Tests added: `CrossSourceGroupPrimaryDomainModuleRegistrationTest` (1), `KmkRecsWhatsNewPolicyTest`
(8), extended `SyncServiceCrossSourceGroupPrimaryMergeTest` (+3: `source == 0L` filtered, blank
`url` filtered, valid rows still merge after invalid rows are filtered). Confirmed still passing:
`CrossSourceGroupPrimaryRestorePolicyTest`, `TasteBackupRoundTripTest`. Full suite: **1058 tests, 0
failures** (up from 1046 pre-existing).

No database migration in this pass. No change to `SourceEvaluationScorer`'s scoring formulas, For
You ranking, group recommendation scoring, OCR, or Rated Manga bulk actions.

Private APK handoff: `private/Komikku-v1.13.6-kmk.8.1-fix2-debug.apk` (private line,
`app.komikku.dev`, built via `:app:assembleDebug`). No public-test (`app.komikku.kmk`) build was
produced.

Full detail: `docs/recommendations/KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_IMPLEMENTATION.md`.

### KMK-Recs v0.8.1-fix3 (Source Evaluation continuation fix, corrective follow-up)

Internal/private handoff build for development. No public release was prepared or requested.
`KmkRecsReleaseNotes.VERSION_CODE = 753`, `VERSION_NAME = "KMK-Recs v0.8.1-fix3"`. Fixes a queue-
semantics bug found after real-device use of v0.8.1-fix2's Source Evaluation reassessment flow, per
`docs/recommendations/KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.md`:

1. **Root cause**: `SourceEvaluationCandidateFilter.shouldSkip()` treats any extension with
   existing evaluation rows — including stale/outdated ones — as "already evaluated, hidden" unless
   the caller passes `reEvaluateStale = true`. The screen's default options never set that, so
   `state.candidates`/`canContinue`/`remainingCandidateCount` (all derived from `state.candidates` +
   cursor) went to zero once the unassessed pool was exhausted, even though stale rows remained
   visible in the (separately populated) results list.
2. **Fix**: new pure `SourceEvaluationCandidateQueuePolicy.staleCandidates()` classifies the
   stale/outdated reassessment queue independent of the unassessed queue's option toggles.
   `SourceEvaluationScreenModel.State` gained a second, fully independent set of queue fields
   (`staleCandidates`, `continuationCursorStale`, `canContinueStale`,
   `remainingStaleCandidateCount`) with their own preference-backed cursor slot
   (`sourceEvaluationContinuationCursorStale`) and a `|queue=stale`-suffixed fingerprint, so the two
   queues' cursors can never collide or clobber each other — including across batch-size changes,
   which (as with the unassessed queue) never invalidate either cursor.
3. **New actions**: `startOrContinueStaleReassessment()` (consent/online/prompt-heavy gated, same as
   the existing actions) and `restartStaleReassessment()` (explicit reset, separate from continue).
   `SourceEvaluationJobState.pendingIsStaleRun` routes the background job's completion handling to
   the correct cursor slot.
4. **UI**: a compact "Reassess outdated (N)" / "Continue reassessing outdated (N remaining)" button,
   shown only when the stale queue is non-empty, plus a "Restart outdated reassessment" text action
   once a cursor exists.

Failed/attempted candidates were verified (not changed) to already advance the cursor correctly —
`SourceEvaluationRunner` records a candidate as completed as soon as it is handed off, before
success/failure is known.

Tests added: `SourceEvaluationCandidateQueuePolicyTest` (6), 3 new tests in
`SourceEvaluationContinuationPolicyTest` covering the `|queue=stale` fingerprint-separation
mechanism. Verification: `:app:testDebugUnitTest --tests "*SourceEvaluation*"` (all passed,
including new tests), `spotlessCheck` (passed), `assembleDebug` (`BUILD SUCCESSFUL`).

No database migration in this pass. No change to `SourceEvaluationScorer`'s scoring formulas, For
You ranking, group recommendation scoring, or Rated Manga.

Internal handoff APK: `private/Komikku-v1.13.6-kmk.8.1-fix3-debug.apk` (universal ABI variant,
built via `:app:assembleDebug`). No app-facing string added or touched in this pass uses "private
build," "public build," "internal build," "test build," or "personal line" wording — verified by
grep audit of every new/changed string (see the implementation report's "Wording Audit" section).

Full detail: `docs/recommendations/KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_IMPLEMENTATION.md`.

### KMK-Recs v0.8.1-fix4 (Final cleanup + source/library-quality dislike, corrective follow-up)

Internal/private handoff build for development. No public release was prepared or requested.
`KmkRecsReleaseNotes.VERSION_CODE = 754`, `VERSION_NAME = "KMK-Recs v0.8.1-fix4"`. Implemented per
`docs/recommendations/KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_PLAN.md`:

1. **New source/library-quality preference axis**, independent of the existing recommendation-
   behavior like/dislike axis: `SourcePreferences.likedSourceQualityKeys()` /
   `dislikedSourceQualityKeys()` / `explicitSourceQualityKeys()`, backed by a new pure
   `SourceQualityMarkPolicy` and reusing `RecommendationSourcePreferenceStore`'s existing key format
   and serializer (no duplicate serializer, no database migration).
2. **Filtering**: `NonInstalledSourceSuggestionScorer` (Sources To Try) and
   `SourceEvaluationCandidateFilter.buildPool()` (Source Evaluation candidates) both exclude
   source-quality-disliked sources, with a separate `sourceQualityHiddenCount` diagnostic distinct
   from the recommendation-dislike count. `BrowsePersonalRecommendationsScreenModel`/
   `RecommendsScreenModel` also exclude installed quality-disliked sources from For You/grouped
   recommendation source selection.
3. **Past evaluations preserved**: `SourceEvaluationDisplayFilter` hides quality-disliked rows by
   default (never deletes), recoverable via a new "Show disliked sources" toggle.
4. **Recovery**: per-row "Clear source mark" and a bulk "Clear source quality marks" action.
5. **Stale-queue completion feedback**: a state-derived "Outdated reassessment complete" card once
   the v0.8.1-fix3 stale/outdated queue is fully drained.
6. **v0.8.1-fix3 wording/encoding cleanup**: removed remaining app-facing private/public/internal/
   test-build wording from `KmkRecsReleaseNotes` and `kmk_recs_updated_body`; normalized malformed
   `<!-- KMK --> vX.Y: ... -->` XML comments in `strings.xml` to valid `<!-- KMK vX.Y: ... -->` form.

Tests added: 9 in `RecommendationSourcePreferenceStoreTest` (axis independence, mark/clear
behavior), 3 in `NonInstalledSourceSuggestionScorerTest`, 3 in `SourceEvaluationCandidateFilterTest`,
1 in `SourceEvaluationCandidateQueuePolicyTest` (unassessed-exhausted-but-stale-actionable
regression), 2 in `SourceEvaluationContinuationPolicyTest` (stale completion state, failed-candidate
cursor advancement). Verification: `:app:testDebugUnitTest --tests "*SourceEvaluation*"` / `*
RecommendationSourcePreferenceStoreTest` / `*NonInstalledSourceSuggestionScorerTest"` all passed;
`spotlessCheck` passed (after one `spotlessApply` for lambda formatting); `assembleDebug`
`BUILD SUCCESSFUL`; wording grep returned no app-facing matches.

No database migration in this pass — purely preference-backed. No change to `SourceEvaluationScorer`'s
scoring formulas, For You ranking, or group recommendation scoring.

Internal handoff APK: `private/Komikku-v1.13.6-kmk.8.1-fix4-debug.apk` (universal ABI variant,
built via `:app:assembleDebug`).

Full detail: `docs/recommendations/KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_IMPLEMENTATION.md`.

### KMK-Recs v0.8.2-v0.8.5 (For You results budget, Recommendation Settings reorganization, active-reading timer, optional reading schedule)

Internal/private handoff build for development. No public release was prepared or requested.
`KmkRecsReleaseNotes.VERSION_CODE = 755`, `VERSION_NAME = "KMK-Recs v0.8.5"`. Implemented as one
coordinated session with four internal milestones and a single final build, per
`docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md`:

1. **v0.8.2 — For You results budget.** New `ForYouResultBudgetPolicy` pure resolver
   (`SUPPORTED_VALUES = [5,10,15,20,30]`, `DEFAULT = 10`, `BOOSTED_MINIMUM = 20`) and
   `SourcePreferences.recommendationResultBudget()`. Replaces the hardcoded
   `NORMAL_RESULTS_PER_SOURCE`/`BOOSTED_RESULTS_PER_SOURCE` constants at both `displayLimit`
   computation sites in `BrowsePersonalRecommendationsScreenModel.searchSource()`. Included in
   `profileFingerprint()`'s digest so a cache built under a smaller budget cannot satisfy a larger
   request — reuses the existing fingerprint-mismatch mechanism, no new invalidation logic. Source
   count, priority, enrichment, Source Evaluation, Top Picks, and query-attempt limits untouched.
2. **v0.8.3 — Recommendation Settings reorganized** into For You behavior / Source priority /
   Source evaluation / Source management / Discovery-cache-management (composable reorder only).
   Most other v0.8.3 requirements (shared rated-collection UI, non-pinned quarantine controls, For
   You shortcuts) were already satisfied by prior sessions — confirmed by inspection, not re-done.
3. **v0.8.4 — Active-reading timer.** New `eu.kanade.tachiyomi.ui.reader.timer` package: pure
   `ReaderTimerReducer` state machine (IDLE/RUNNING/PAUSED/CHAPTER_GRACE/EXTRA_CHAPTER_GRACE/
   EXPIRED), monotonic-clock based (`SystemClock.elapsedRealtime()`, never wall-clock),
   `SavedStateHandle`-persisted as individual primitives (matching the existing `chapter_id`/
   `page_index` convention). Lifecycle-bound to `ReaderViewModel`/`ReaderActivity` — pauses on
   `onPause`, auto-resumes on `onResume` only if not explicitly user-paused. Reader bottom-bar
   "Reading timer" icon and dialog. "Warning" is an event/set-diff (`firedWarningMinutes`), not a
   literal phase, for StateFlow-collector-safety reasons documented in the reducer's own doc comment.
4. **v0.8.5 — Optional reading schedule.** New `eu.kanade.tachiyomi.ui.reader.schedule` package:
   pure `ReaderScheduleResolver` (day/time windows, midnight-crossing support, ALLOWED/RESTRICTED
   modes, one mode per schedule per the plan's own sanctioned simplification). Reuses the timer's
   grace mechanism via a **second, independent** `ReaderTimerCoordinator` instance (a zero-duration
   session whose first tick immediately enters the reducer's already-tested `CHAPTER_GRACE` logic)
   rather than adding schedule branches to the reducer itself. New "Reading schedule" section in
   Settings > Reader, reusing the existing declarative `Preference.PreferenceGroup` framework.

Tests added: 78 total — `ForYouResultBudgetPolicyTest` (11), `ReaderTimerReducerTest` (30),
`ReaderTimerStateCodecTest` (9), `ReaderTimerCoordinatorTest` (6), `ReaderScheduleResolverTest`
(16), `ReaderScheduleStoreTest` (9)`. Verification: `exh.recs.*` + `eu.kanade.tachiyomi.ui.reader.*`
targeted packages passed; full `:app:testDebugUnitTest` passed; `spotlessCheck` passed;
`assembleDebug` `BUILD SUCCESSFUL` (built twice — once pre-version-bump to validate, once
post-bump to bake the final version metadata into the handoff APK).

No database migration in this pass — every new persistent value
(`recommendationResultBudget`/`readingScheduleEnabled`/`readingScheduleMode`/
`readingScheduleWindows`) uses the existing generic preference-store mechanism, which this fork
already backs up/restores/syncs automatically via `preferenceStore.getAll()` — no manual backup
registration needed. No Injekt registrations needed — every new class is a pure object or is
constructed directly by its owner. No change to `SourceEvaluationScorer`'s scoring formulas, For
You ranking, group recommendation scoring, or any `.sq` database schema file.

Known limitations: the reading-schedule editor's "edit" is delete-then-recreate rather than
in-place field editing, matching the existing `BiometricTimesScreen.kt` time-range-list precedent;
no preference persists the timer's last-used warning/grace configuration between sessions; the
schedule-grace coordinator's
session isn't persisted across process death (only the underlying schedule preferences are — the
next evaluation correctly re-derives the restriction). No on-device manual QA (rotation, lock
screen, real backgrounding, DST transitions) was possible in this environment — recorded as
unavailable, not claimed as verified.

Internal handoff APK: `private/Komikku-v1.13.6-kmk.8.5-debug.apk` (universal ABI variant, built via
`:app:assembleDebug`).

Full detail: `docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md`.

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

### KMK-Recs v0.8.6-v0.8.9 (catch-up summary)

This file was not updated per-version through v0.8.6-v0.8.8; those releases' full detail lives in
their own implementation reports (`docs/recommendations/KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_
LOADING_IMPLEMENTATION.md`, `KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_
IMPLEMENTATION.md`, `KMK_RECS_V0_8_7_FIX1_AND_V0_8_8_IMPLEMENTATION.md`) and `CURRENT_STATE.md`'s
per-version sections, which are kept current. Brief pointer only, to keep this file's version trail
from silently going stale:

- **v0.8.6** — group-recommendation search performance/loading (bounded concurrency, timeouts,
  cache, budget policy). `VERSION_CODE = 756`.
- **v0.8.7** — Reading Schedule dialog root-cause fix; partial Rated UI/Recommendation Settings
  refinement. `VERSION_CODE = 757`.
- **v0.8.7-fix1 + v0.8.8** (shipped together) — real reading-schedule enforcement (previously
  toast-only), chapter-completion rating prompt, Recommendation Settings split into per-category
  screens, outdated-evaluation reconciliation fix. `VERSION_CODE = 758`.
- **v0.8.9** — What's New now uses the official Komikku New/Improve/Fix changelog structure going
  forward (renderer unchanged — it already supported this; only content changed), all 76 prior
  historical entries preserved individually; new Recommendation Settings search (category-level, 7
  destinations, pure ranked index, read-only). `VERSION_CODE = 759`. See
  `docs/recommendations/KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_IMPLEMENTATION.md`.

APK for this version: `Komikku-v1.13.6-kmk.8.9-debug.apk`.

### Upstream Komikku 1.14.0 Reconciliation (2026-07-17) — not a KMK-Recs feature version

The whole fork was reconciled against the official Komikku v1.14.0 tag across 9 phases (source
API, backup/restore/sync/proto, library/manga/migration/reader, trackers/notifications). This is
an **upstream app-version sync**, not a new recommendation feature or a corrective follow-up to
one — per this file's own stated boundary ("[a]pstream app versioning still lives in
`app/build.gradle.kts`... [the KMK-Recs feature version] does not need to change Android
install/update behavior by itself"), it does not get its own `KMK-Recs vX.Y.Z` entry or a
`KmkRecsReleaseNotes.VERSION_CODE` bump/What's New entry. The recommendation system's own state is
unchanged and was directly verified intact post-reconciliation (see
`docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md`).

What did change, per this file's own convention (upstream Komikku app version, tracked separately
from the feature label):

- `app/build.gradle.kts`: `versionName` `"1.13.6"` → `"1.14.0"`, `versionCode` `88` → `89`.
- `KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME`: unchanged (`759` / `"KMK-Recs v0.8.9"`).
- APK for this milestone: `Komikku-v1.14.0-kmk.8.9-debug.apk` — same recs feature suffix as the
  entry above, new upstream-version prefix.

### KMK-Recs v0.8.10-fix3 (structural source-runtime isolation, complete)

Corrective handoff under the v0.8.10 line — not a new feature phase; the structural follow-up to
fix2's narrow Asura/Zstd `LinkageError` crash isolation patch. Per the Canonical Versioning Rule
above and the fix1/fix2 precedent: `KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` are **not**
bumped for this pass (narrowly-scoped crash-isolation/stability work, not a new KMK-Recs feature) —
remain at `760`/`"KMK-Recs v0.8.10"`.

Authoritative plan and report:

- `docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_PLAN.md` (plan)
- `docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md`
  (implementation report — full call-site inventory, module-boundary blocker and its resolution,
  tests, verification)

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.10-fix3-debug.apk
```

Files changed: `SourceRuntime.kt`/`SourceRuntimeFailureRegistry.kt` (new, `app`),
`SourceRuntimeClassifier.kt` (new, `core:common` — pure classification only, added mid-pass to
resolve a confirmed `app`→`data` module-dependency-direction blocker), `RecommendationErrorClassifier.kt`
(delegates instead of duplicating), `SourceEvaluationProbeErrorClassifier.kt` (new
`EXTENSION_INCOMPATIBLE` kind), and 16 call-site files across official Browse/global search/feeds,
library update/bulk favorite, KMK matching/Best Version/Source Evaluation/For You/group
recommendations. Tests: 23 new (1408 → 1431). Full verification (`spotlessCheck`,
`:app:testDebugUnitTest`, `assembleDebug`) passed before the APK was built.

### KMK-Recs v0.8.10-fix4 (complete source-runtime isolation, complete)

Corrective handoff under the same v0.8.10 line after live-device QA disproved full fix3 coverage.
`Komikku-v1.14.0-kmk.8.10-fix3-debug.apk` still crashed with the installed AsuraScans extension
present: `NoClassDefFoundError: okhttp3.zstd.Zstd` reached `GlobalExceptionHandler`/`CrashActivity`
when opening For You, manga recommendations, Browse/source screens, and For You settings. Direct
re-inspection of the actual code (not fix3's report characterization of it) found the real,
confirmed root cause: `BrowseSourceScreenModel.kt`'s `init` block called `source.getFilterList()`
with zero try/catch -- not "lower risk", fully unguarded.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_PLAN.md`
- `docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md`

Migrated `BrowseSourceScreenModel.kt`, `SearchScreenModel.kt`, `FeedScreenModel.kt`,
`SourceFeedScreenModel.kt`, `BrowsePersonalRecommendationsScreenModel.kt`,
`CrossExtensionGenreSearchSource.kt`, `RecommendationCandidateEnricher.kt`,
`GroupRecommendationSeedBuilder.kt`, `RecommendsScreenModel.kt` (partial -- see the implementation
report's documented discrepancy: it wraps a polymorphic `PagingSource.requestNextPage()` call, not a
single raw `Source` method, so it uses the shared `core:common` classifier directly instead of
`SourceRuntime.run()`), `SourceEvaluationRunner.kt`, `SourceEvaluationCatalogueEnricher.kt`,
`SourceRecommendationFitProbe.kt`, `SameMangaCandidateSearcher.kt`,
`CrossExtensionMatchScreenModel.kt`, `BestVersionCompareScreenModel.kt` to the shared `SourceRuntime`
boundary. Inspected and left unchanged (already safe via an equivalent mechanism):
`SourceRecommendationQualityRunner.kt`, `RecommendationSearchHelper.kt`, `HttpPageLoader.kt`,
`ChapterLoader.kt`, `Downloader.kt`, `ExtensionManager.kt`, `ExtensionsScreenModel.kt`,
`ExtensionDetailsScreenModel.kt`. Tests: 3 new files, 24 tests, all proving sibling isolation against
the real `SourceRuntime.run()` boundary (not only classifier-level assertions).

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.10-fix4-debug.apk
```

Release-note/version decision: confirmed via direct read of
`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` -- `VERSION_CODE`/`VERSION_NAME` remain at
`760`/`"KMK-Recs v0.8.10"`, unchanged from fix1/fix2/fix3, since this is a crash-isolation/stability
pass with no user-visible feature change. No new What's New entry was added. The pre-supplied draft
of this section (written before implementation) reached the same conclusion; confirmed, not
overridden.

### KMK-Recs v0.8.10-fix5 (source client/property health and recovery, complete)

Corrective handoff under the same v0.8.10 line after live-device evidence disproved fix4's "complete
source-runtime isolation" framing. `Komikku-v1.14.0-kmk.8.10-fix4-debug.apk` still crashed with the
installed AsuraScans extension present: `NoClassDefFoundError: okhttp3.zstd.Zstd`, now reached from a
different, unprotected category fix3/fix4 did not cover -- direct `HttpSource.client`/
`HttpSource.headers` lazy-property reads and page-preview image fetches in
`MangaCoverFetcher.kt`/`PagePreviewFetcher.kt` (Coil cover/preview loading), not any
`SourceRuntime`-guarded source *method*.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_10_FIX5_SOURCE_CLIENT_HEALTH_AND_RECOVERY_PLAN.md`
- `docs/community/KMK_RECS_V0_8_10_FIX5_SOURCE_CLIENT_HEALTH_AND_RECOVERY_IMPLEMENTATION.md`

Added `SourceRuntimeOperation.Client`/`Headers`/`CoverImage`/`PreviewImage`; new
`SourceRuntimeAccessors.kt` (`HttpSource.safeClientOrNull()`/`safeHeadersOrNull()`, routing through
`SourceRuntime.runBlockingSourceCall`); migrated `MangaCoverFetcher.kt`/`PagePreviewFetcher.kt`; new
`SourceRuntimeHealthReporter.kt` mapping `SourceRuntimeFailureRegistry` entries to installed-extension
identity; a non-blocking Source Evaluation source-health warning + recovery-actions dialog
(`SourceEvaluationScreenModel.kt`/`SourceEvaluationScreen.kt` -- Retry/Update/Reinstall/Uninstall/
Disable, each offered only when actually valid); and the optional Phase 7 proactive skip applied to
`CrossExtensionGenreSearchSource.kt` (gated to `sharedEnrichmentSemaphore != null`, i.e. batch/
GROUP_PREVIEW context only, never single-manga user-initiated browsing). Three call-site families
reviewed and deferred with documented reasons: `SourceEvaluationRunner.kt`/
`SourceRecommendationFitProbe.kt` iterate by extension, not source id, so the proactive-skip
integration point doesn't exist cleanly at the loop level; `BrowsePersonalRecommendationsScreenModel.
searchSource()`'s cache-first path was judged too risky to touch within this pass's scope. Tests: 6
new (1434 -> 1440), extending the existing `SourceRuntimeTest.kt` `FakeSource` harness rather than a
separate `HttpSource`-backed test file, since a real `HttpSource` subclass cannot be constructed in
this test suite (its `network` lazy property calls `Injekt.get<NetworkHelper>()`, and there is no
Injekt-bootstrapping harness here).

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.10-fix5-debug.apk
```

Release-note/version decision: `VERSION_CODE`/`VERSION_NAME` remain at `760`/`"KMK-Recs v0.8.10"`,
unchanged from fix1-fix4, matching the same crash-isolation/stability-pass precedent -- no user-visible
feature change, no new What's New entry.

### KMK-Recs v0.8.10-fix6 (remaining source runtime isolation, complete)

Corrective handoff under the same v0.8.10 line after a newer real-device crash log
(`komikku_crash_logs_7.txt`) confirmed the same `NoClassDefFoundError: okhttp3.zstd.Zstd` structural
hazard reaches additional, not-yet-migrated call sites: `getFilterList()` in
`SourceFeedScreenModel.kt`/`FeedScreenModel.kt`, migration/smart-search
(`SmartSourceSearchEngine.kt`), the `RecommendationSource` delegate wrapper
(`RecommendationPagingSource.kt`), and WebView source-header reads
(`WebViewScreenModel.kt`/`WebViewActivity.kt`, two separate occurrences of the same pattern). Now
implicated by at least two extensions: AsuraScans and KaynScans.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_10_FIX6_REMAINING_SOURCE_RUNTIME_ISOLATION_PLAN.md`
- `docs/community/KMK_RECS_V0_8_10_FIX6_REMAINING_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md`

Migrated all 5 plan-named files (Tasks 1-5) to the existing shared `SourceRuntime`/
`SourceRuntimeAccessors`/`SourceRuntimeFailureRegistry`/core:common classifier boundary -- no second
classifier created. Task 6's re-audit found and fixed 2 additional genuine holes not named in the
plan: `HttpPageLoader.kt`'s `getPages()` cache-miss fallback (the surrounding `catch(Throwable)`
handled the cache lookup's exception, not the fallback call's own exception), and
`WebViewActivity.kt`'s independent duplicate of the exact `source.headers`/`catch(Exception)` pattern
fixed in `WebViewScreenModel.kt`. One item deferred with documented reasoning:
`SuwayomiApi.kt`'s `source.client` read (the plan itself flagged the `Call.Factory` vs `OkHttpClient`
type mismatch as uncertain; narrow tracker-specific path, not the reported crash surface). Tests: 3
new (1440 -> 1443), extending the existing `SourceRuntimeTest.kt` harness per the plan's exact
required cases.

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.10-fix6-debug.apk
```

Release-note/version decision: `VERSION_CODE`/`VERSION_NAME` remain at `760`/`"KMK-Recs v0.8.10"`,
unchanged from fix1-fix5, same crash-isolation/stability-pass precedent.

### KMK-Recs v0.8.10-fix7 (source runtime rethrow containment, complete)

Corrective handoff under the same v0.8.10 line after a newer real-device log
(`2026-07-18-debug.txt`) confirmed the AsuraScans `okhttp3.zstd.Zstd` crash could still reach Browse/
For You/recommendation-related paths after fix6. Root cause: `SourceRuntime.run(...)`/
`runBlockingSourceCall(...)` correctly classify a recoverable `LinkageError` and record it in
`SourceRuntimeFailureRegistry`, but several call sites then used `Result.getOrThrow()`, which rethrows
the exact stored raw `LinkageError` -- an `Error`, not an `Exception` -- past any outer path that only
catches `Exception`.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_10_FIX7_SOURCE_RUNTIME_RETHROW_CONTAINMENT_PLAN.md`
- `docs/community/KMK_RECS_V0_8_10_FIX7_SOURCE_RUNTIME_RETHROW_CONTAINMENT_IMPLEMENTATION.md`

Added `RecoverableSourceRuntimeException`/`getOrThrowSourceRuntimeException()` to `SourceRuntime.kt` --
found and corrected a real defect in the plan's own provided code (no `CancellationException` guard,
which would have broken cancellation semantics). Migrated the plan's 4 named locations
(`RecommendationPagingSource.kt`, `SmartSourceSearchEngine.kt`,
`BrowsePersonalRecommendationsScreenModel.kt` x2, `HttpPageLoader.kt`). The required Step 3 audit of
every remaining `Result.getOrThrow()` found and fixed **6 more genuine holes** beyond the plan's named
locations, all via `UpdateMangaFromRemote`'s stored raw `LinkageError`: `MergedSource.kt` (2 sites),
`BrowseSourceScreenModel.kt`, `MangaScreenModel.kt`, `MigrationListScreenModel.kt` (3 sites), and
`GalleryAdder.kt` (a double hole via its own `retry()` helper). 2 pre-existing, out-of-scope
error-handling gaps were found and disclosed but not fixed (`MigrateMangaUseCase.kt`,
`LibraryUpdateJob.kt`/`MetadataUpdateJob.kt` swallow fatal errors too broadly -- a different problem
than this pass's scope). Tests: 4 new (1443 -> 1447).

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.10-fix7-debug.apk
```

Release-note/version decision: `VERSION_CODE`/`VERSION_NAME` remain at `760`/`"KMK-Recs v0.8.10"`,
unchanged from fix1-fix6, same crash-isolation/stability-pass precedent.

### KMK-Recs v0.8.10-fix8 (zstd dependency and enforced runtime suppression, complete)

Corrective handoff under the same v0.8.10 line. Root cause had two parts: (1) the app's OkHttp 5.3.2
setup never included `com.squareup.okhttp3:okhttp-zstd` -- a separate, optional artifact -- so any
extension (confirmed: AsuraScans) whose lazy client touches `okhttp3.zstd.Zstd` throws
`NoClassDefFoundError` the first time that code path runs; (2) `SourceRuntime.run()`/
`runBlockingSourceCall()` correctly classified and recorded that failure in
`SourceRuntimeFailureRegistry`, but the registry was advisory only -- nothing stopped the same
already-known-broken source from being touched again on the very next call, so a source could crash
repeatedly instead of failing fast after the first confirmed failure.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_10_FIX8_ZSTD_AND_RUNTIME_SUPPRESSION_IMPLEMENTATION.md`

Added `okhttp-zstd` to the existing `okhttp` bundle in `gradle/libs.versions.toml` (same
`okhttp_version` ref, no version change). Added enforced suppression to `SourceRuntime.run()`/
`runBlockingSourceCall()`: a new `suppressionFailureOrNull(source)` check runs before either function
ever touches `source.block()` again; if `SourceRuntimeFailureRegistry.isTemporarilyUnavailable(id)` is
true, the call returns `Result.failure(SourceTemporarilyUnavailableException(...))` immediately,
without re-touching the source's (possibly still-broken) lazy `client`/`headers`.
`CancellationException` and fatal non-`LinkageError` `Error`s are unaffected by this check (they were
never suppressible failures to begin with). `safeClientOrNull()`/`safeHeadersOrNull()`
(`SourceRuntimeAccessors.kt`) needed no code change -- they already delegate 100% through
`runBlockingSourceCall()`, so the new enforcement covers them automatically.
`MangaCoverFetcher.kt`/`PagePreviewFetcher.kt` likewise needed no change -- both already route every
source-owned touch point through `SourceRuntime`/`safeClientOrNull()`/`safeHeadersOrNull()` since fix5.
The required re-audit of the ~19 named source/client/recommendation touch-point files found all of them
already covered by the fix1-fix7 migrations (`Downloader.kt` remains the sole file needing no
`SourceRuntime` migration, per the fix7 analysis: its own outer `catch(Throwable)` already isolates a
raw `Error`). No global `SafeHttpSource` proxy was introduced, per the plan's explicit instruction.

Enforcing suppression changed real behavior that 3 pre-existing tests had encoded the old (advisory-only)
assumption into, and this pass corrected all three along with the 8 new tests it added:
- `SourceRuntimeTest`'s `...increments count on repeated failures...` test now clears the registry
  between each of 3 repeated calls to prove genuinely separate failures still increment `count`
  (previously it fired 3 rapid calls on the same source and expected `count == 3`; the 2nd and 3rd are
  now correctly suppressed before ever reaching `record()`, so `count` stays `1` unless cleared).
- `SourceRuntimeTest`'s lazy-client classification test now clears the registry between its two
  `SourceRuntime.run()` calls on the same source, so the second is a genuine re-touch rather than an
  now-suppressed one.
- `RecommendationSourceFailureIsolationTest`'s sequential-enrichment test and
  `SourceEvaluationCatalogueEnricherTest`'s "detail failure keeps original candidate" test both drove a
  loop where one item's failure on a source is immediately followed by another item on the *same*
  source within the same batch -- once fix8 confirms a source broken, that source is now correctly
  suppressed for the rest of the batch too (this is the intended strengthening: stop hammering a source
  already known to be broken), so both tests were updated to assert the new, correct outcome instead
  of the item after the failure spuriously succeeding.
- `SourceEvaluationCatalogueEnricherTest` and `SourceRecommendationFitProbeTest` also gained a
  `@BeforeEach` registry clear: both reuse fixed fake-source ids across many test methods, and the new
  enforcement means a failure recorded by one test method could otherwise leak into and suppress an
  unrelated later test method sharing the same id.

Sibling-*source* isolation (a different, unaffected property: one broken source must never suppress or
block a different, healthy source) is covered by a new dedicated test, "a batch of sibling sources is
unaffected when one source is suppressed mid-batch". Tests: 8 new (1447 -> 1455; all pass), plus the 3
corrected pre-existing tests above.

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.10-fix8-debug.apk
```

Release-note/version decision: `VERSION_CODE`/`VERSION_NAME` remain at `760`/`"KMK-Recs v0.8.10"`,
unchanged from fix1-fix7, same crash-isolation/stability-pass precedent. No new What's New entry was
added: every fix1-fix7 pass in this line was a pure crash-isolation/robustness pass with no
user-visible feature change, and this pass follows the same category -- the `okhttp-zstd` dependency
add and enforced suppression are both invisible to the user except as "the crash stops happening."

### KMK-Recs v0.8.10-fix9 (deferred polish and conformance, partial)

Corrective/polish handoff after fix8 confirmed stable on device. Unlike fix1-fix8 (crash-isolation
passes), this pass bumps `VERSION_CODE`/`VERSION_NAME` to `761`/`"KMK-Recs v0.8.10-fix9"` and adds a
real What's New entry, since it includes a user-visible change (Taste and Tags grouping).

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_PLAN.md`
- `docs/community/KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_IMPLEMENTATION.md`

Delivered:

- Converted all 85 historical KMK-Recs changelog entries to the What's Changed/New/Improve/Fix
  structure (script-assisted, verified lossless by structural tests plus a bullet-count check: 345
  bullets before and after).
- Taste and Tags: new `TagPreferenceGroupingPolicy` splits Preferred/Disliked/Blocked/Other into
  separate capped-at-10, expandable sections in `RecommendationSettingsSharedComponents.kt`'s
  `TagPreferencesContent`, so Blocked is reachable without scrolling past a long Preferred list.
- Source-runtime hygiene (bounded, per fix8's guardrails): `SuwayomiApi.kt`'s `client` field now
  routes through `safeClientOrNull()` instead of a raw `source.client` read; a new shared
  `rethrowIfFatal()` helper in `SourceRuntime.kt` (reusing the existing `core:common` classifier, not
  a second one) is now called from `MigrateMangaUseCase.kt`, `LibraryUpdateJob.kt`, and
  `MetadataUpdateJob.kt`'s broad `catch (e: Throwable)` blocks so cancellation and fatal VM/system
  errors are rethrown instead of silently swallowed; added a test proving a second failure right after
  a manual registry clear correctly re-enters suppression (not a one-shot bypass).

Deferred, with documented reasoning (see implementation report):

- The broader Phase 3/4 Komikku-widget conformance pass (`PreferenceGroupHeader`/
  `SwitchPreferenceWidget`/`ListPreferenceWidget` migration across the Recommendation Settings/Source
  Evaluation/Sources To Try/Rated Collections screens) was not attempted this pass — real scope, not a
  quick edit.
- The Recommendation Settings search "flicker" investigation found the KMK screen's
  `Crossfade`/`produceState` pattern is *structurally identical* to official Komikku's own
  `SettingsSearchScreen.kt`, disproving the plan's assumption that KMK deviates here. Per instruction,
  stopped and asked the user rather than guessing; user chose to document and defer rather than
  diverge from the official pattern.

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.10-fix9-debug.apk
```

### KMK-Recs v0.8.11 (UI navigation standardization, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `762`/`"KMK-Recs v0.8.11"` — real user-visible UI/navigation
changes, so (unlike fix1-fix9) this pass adds a genuine What's New entry.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_AUDIT_PLAN.md`
- `docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_IMPLEMENTATION_PLAN.md`
- `docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_IMPLEMENTATION.md`
- `docs/community/KMK_RECS_INTERACTION_FUNCTIONALITY_AUDIT.md`

Delivered (Phases A-G): Recommendation Settings duplicate-destination removal and search-entry
cleanup (with a new `dedupeKey` safety net in `RecommendationSettingsSearchIndex`), official
`SwitchPreferenceWidget`/`ListPreferenceWidget`/`TextPreferenceWidget`/`PreferenceGroupHeader`
conformance for simple rows, Taste Suggestions grouping/capping
(`TasteSuggestionVisibilityPolicy`), Sources To Try action density (Install stays primary, rest moves
to overflow), Source Priority density (like/dislike into overflow) plus a new "Best Version preview"
section, `SourceEvaluationScreen` resectioned into named groups with real scroll-to-anchor support,
and For You top-bar action grouping (Rated manga menu, Export to overflow).

Delivered (Phase H): created `docs/community/KMK_RECS_INTERACTION_FUNCTIONALITY_AUDIT.md` defining a
long-press/selection/bulk-action/search/grouping standard and auditing KMK-added screens against it;
fixed the reported Loved/Liked/Disliked multi-select "Group" bug -- root cause was that
`LovedMangaScreenModel.load()` only reloads reactively from a `manga_taste` Flow, while group actions
only write to the non-reactive cross-source-link table, so the display never refreshed after a
merge/ungroup until an unrelated taste change coincidentally reloaded it. Fixed by applying each
action's writes into in-memory state immediately (`mergeLinkWritesIntoMap()`), with an explicit
success/failure Snackbar added for Merge. One interaction gap documented, not fixed (real layout
work): search is hidden, not disabled, while Loved/Liked/Disliked is in selection mode.

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.11-debug.apk
```

### KMK-Recs v0.8.12 (Recommendation Settings structural follow-up, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `763`/`"KMK-Recs v0.8.12"` — real user-visible changes, so
this pass adds a genuine What's New entry.

Plan:

- `docs/community/KMK_RECS_V0_8_12_RECOMMENDATION_SETTINGS_STRUCTURAL_FOLLOWUP_PLAN.md`
- `docs/community/KMK_RECS_V0_8_12_RECOMMENDATION_SETTINGS_STRUCTURAL_FOLLOWUP_IMPLEMENTATION.md`

Delivered (Workstream A): `RecommendationLanguageAvailabilityPolicy` merges selected languages +
installed-visible-source languages + available-extension languages (deterministic alphabetical,
falls back to English only when all three are empty) so a selected non-English language can no
longer disappear from the chip list just because installed sources happen to be mostly English.
Wired into `RecommendationsSettingsScreenModel` with a new reactive collector on
`availableExtensionsFlow`/language-preference changes. Audited the 9 other readers of
`recommendationSourceLanguages()` — no other mismatches found.

Delivered (Workstream B): new `RecommendationMatchingVersionsSettingsScreen` ("Matching and
versions") holds Same Manga Matching and Best Version Preview, moved out of Source Priority (whose
"physical adjacency" placement comment no longer applied). Index screen, search index (with a
`matching_versions` category entry), and both anchor/search-index test files updated; no duplicated
controls between the two destinations.

Delivered (Workstream C): investigation found the plan's own evidence for outdated-reassessment
counts did not match current code — `state.staleCandidates.size` and the stale-reassess button
label already used only the actionable/workable count, with a separate non-inflating
`outdated_unreachable_note`. No functional UI change was required; extracted the completion-display
condition into a new tested `SourceEvaluationStaleCompletionDisplayPolicy` for C2's optional
testability allowance, and confirmed C3's installed-extension evaluation boundary is unchanged.

Delivered (Workstream D): `TasteSuggestionVisibilityPolicy` rewritten from a boolean `expanded` flag
to an integer visible-count (`DEFAULT_VISIBLE = 10`, `REVEAL_STEP = 10`) with `visible()`,
`nextVisibleCount()`, `canShowMore()`, `canShowAll()`, `canShowFewer()`. `TasteSuggestionGroup` and
`TagPreferenceGroup` both updated to genuinely reveal 10 at a time ("Show N more" / "Show all (N)" /
"Show fewer") instead of the old "Show more" action that revealed every remaining item at once
despite its "(%d)" label implying a bounded reveal. Tag colors and mutation behavior unchanged.

Delivered (Workstream E): confirmed search entries already route correctly to the new destinations
(no duplicate rows). Fixed a real raw-exception leak in `ExceptionFormatter.formattedMessage`:
`RecoverableSourceRuntimeException` (thrown by `getOrThrowSourceRuntimeException()` for any
recoverable per-source failure) was not unwrapped before classification, so every recoverable
failure surfaced through For You / Matching-and-versions rows as
`"RecoverableSourceRuntimeException: ..."` instead of a clean message — including a `LinkageError`
cause that the existing `is LinkageError` branch was meant to sanitize but never reached. Now
recurses on `sourceRuntimeFailure` before falling through to the raw-class-name branch; added an
`UninitializedPropertyAccessException` case. Same fix applied to `CrossExtensionMatchScreen`'s
row error text (previously `localizedMessage ?: javaClass.simpleName`). SourceRuntime boundary
itself untouched; all existing recoverable-failure tests still pass unchanged.

Delivered (Workstream F): targeted For You false no-match audit using Vortex Scans as the concrete
example. Root cause confirmed in code: the query-attempt chain's final, most-lenient attempt
(`TEXT_ONLY_TOP_TAGS`) searches for the literal tag words themselves (e.g. "Isekai Fantasy Action")
as free text — most source search backends treat a multi-word query as an AND-of-words *title*
match, so a source whose text search does not also match genres/tags can legitimately return zero
raw results for every attempt even though its Popular catalogue has plenty of matching manga. This
is a genuine fetched-nothing-raw case, not a misclassification of filtered/weak-metadata results —
`hadRawResults` correctly stayed false. Fix: new pure `RecommendationCatalogueFallbackPolicy`
(`shouldAttempt(hadRawResults, hadError)`) gates one bounded, single-page `getPopularManga(1)` probe
after the existing attempt chain finds nothing and no source error occurred, reusing the exact same
visibility/enrichment/scoring/merge pipeline as an ordinary successful attempt — every existing
filter (blocked/adult/known/rated/seen/min-chapter/dedup) still applies unchanged, and the fallback
is never persisted as a "successful strategy" so it can't get locked in over the real tag-search
strategies. `RecommendationDiscoveryPlanner`'s existing page/attempt caps are untouched — this adds
exactly one bounded probe, not unbounded crawling.

Files changed: `app/src/main/java/exh/recs/RecommendationLanguageAvailabilityPolicy.kt` (new),
`app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`,
`app/src/main/java/exh/recs/settings/RecommendationMatchingVersionsSettingsScreen.kt` (new),
`app/src/main/java/exh/recs/settings/RecommendationSourcePrioritySettingsScreen.kt`,
`app/src/main/java/exh/recs/settings/RecommendationSettingsIndexScreen.kt`,
`app/src/main/java/exh/recs/settings/RecommendationSettingsSearchScreen.kt`,
`app/src/main/java/exh/recs/evaluation/SourceEvaluationStaleCompletionDisplayPolicy.kt` (new),
`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`,
`app/src/main/java/exh/recs/settings/TasteSuggestionVisibilityPolicy.kt`,
`app/src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt`,
`app/src/main/java/eu/kanade/presentation/util/ExceptionFormatter.kt`,
`app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt`,
`app/src/main/java/exh/recs/RecommendationCatalogueFallbackPolicy.kt` (new),
`app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`,
`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=763),
`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`.

Tests added/updated: `RecommendationLanguageAvailabilityPolicyTest` (7, new),
`RecommendationSettingsScreenAnchorKeysTest` (updated for the new screen's keys),
`SourceEvaluationStaleCompletionDisplayPolicyTest` (4, new), `TasteSuggestionVisibilityPolicyTest`
(rewritten for the integer-count API), `RecommendationCatalogueFallbackPolicyTest` (4, new).

Tests run:

- `exh.recs.settings.*`, `exh.recs.evaluation.*`, `exh.recs.RecommendationLanguageAvailabilityPolicyTest`, `exh.recs.RecommendationCatalogueFallbackPolicyTest`, `eu.kanade.tachiyomi.source.SourceRuntimeTest`, `exh.recs.RecommendationErrorClassifierExtensionLinkageTest`, `exh.recs.RecommendationSourceFailureIsolationTest` — all PASSED
- Full `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.12-debug.apk
```

### KMK-Recs v0.8.12-fix1 (fatal-error containment correction, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `764`/`"KMK-Recs v0.8.12-fix1"` — a real behavior/safety
correction (not cosmetic-only), so this pass adds a genuine What's New entry per the canonical rule.

Follow-up report:

- `docs/community/KMK_RECS_V0_8_12_FIX1_FATAL_ERROR_CONTAINMENT_IMPLEMENTATION.md`

**Problem found in review:** both `catch (e: Error)` blocks in
`BrowsePersonalRecommendationsScreenModel.searchSource()` — the main tag-search attempt loop and the
v0.8.12 Popular-catalogue fallback — caught every `Error` subtype unconditionally and recorded it as
a per-source failure (or silently swallowed it, in the fallback's case). The code comments justifying
this ("SourceRuntime.run() already rethrows fatal errors") were only true for `Error`s thrown
*inside* a `SourceRuntime.run()`/`runBlockingSourceCall()` block. Code between those calls in the
same `try` — enrichment, scoring, chapter-count/known-manga lookups, candidate-memory merge — runs
outside SourceRuntime's boundary but was still covered by the same broad `catch (e: Error)`, so a
genuinely fatal VM condition (`OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`, or any other
non-`LinkageError` `Error`) thrown from that code would have been silently recorded as an ordinary
per-source search failure instead of propagating.

**Fix:** both catch blocks now call the existing shared `rethrowIfFatal(e)` helper (already used
elsewhere in the codebase for exactly this purpose, e.g. `MigrateMangaUseCase`/`LibraryUpdateJob`/
`MetadataUpdateJob` since v0.8.10-fix9) before treating the caught `Error` as recoverable.
`rethrowIfFatal` rethrows anything that is not a recoverable per-source failure (i.e. everything
except `LinkageError` and its subtypes, after unwrapping `ExecutionException`/`CompletionException`/
`InvocationTargetException` wrappers) and returns normally only for a genuinely recoverable failure.
Recoverable extension/source failures (missing class, linkage failure, failed client init,
`RecoverableSourceRuntimeException`) are still isolated exactly as before — only the fatal-error
classification changed.

**Also audited (no other defect found):** every other `catch (e: Error)` block in the recommendation
codebase — `batch/RecommendationSearchHelper.kt`, `matching/CrossExtensionMatchScreenModel.kt`,
`matching/SameMangaCandidateSearcher.kt`, `evaluation/SourceEvaluationRunner.kt` (2 sites) — already
correctly checked `unwrapSourceRuntimeCause().isRecoverableSourceRuntimeFailure()` (or equivalent)
before swallowing, and already rethrow anything not recoverable. Only
`BrowsePersonalRecommendationsScreenModel.kt`'s two sites had the defect.

**Catalogue fallback behavior reconfirmed unchanged:** `RecommendationCatalogueFallbackPolicy
.shouldAttempt(hadRawResults, hadError)` still gates the fallback to run only when the tag-search
attempts found zero raw results and no error occurred; the probe is still a single bounded
`getPopularManga(1)` call with no retry loop; results still go through the same
`filterVisibleCandidates`/enrichment/scoring/`RecommendationCandidateMemoryRanker.merge`/cache
pipeline as an ordinary attempt; the fallback's `successfulStrategy` is still always `null`, so a
failed or fatal-erroring probe can never be recorded as a successful tag-search strategy.

Files changed: `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`,
`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=764),
`app/src/test/java/exh/recs/BrowsePersonalRecommendationsFatalErrorHandlingTest.kt` (new),
`app/src/test/java/exh/recs/KmkRecsReleaseNotesTest.kt`, plus documentation listed above.

Tests added: `BrowsePersonalRecommendationsFatalErrorHandlingTest` — 11 tests covering recoverable
`LinkageError`/`RecoverableSourceRuntimeException`/ordinary `Exception` isolation, fatal
`OutOfMemoryError`/`StackOverflowError`/`ThreadDeath`/`AssertionError` propagation (both the main loop
and fallback catch-block shapes), and the fallback's single-bounded-attempt gate.

Tests run:

- `exh.recs.BrowsePersonalRecommendationsFatalErrorHandlingTest`, `eu.kanade.tachiyomi.source.SourceRuntimeTest`, `exh.recs.RecommendationSourceFailureIsolationTest`, `exh.recs.RecommendationCatalogueFallbackPolicyTest`, `exh.recs.KmkRecsReleaseNotesTest` — all PASSED
- Full `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.12-fix1-debug.apk
```

### KMK-Recs v0.8.13 (For You strategy recovery and relevance, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `765`/`"KMK-Recs v0.8.13"` — real recommendation-quality
behavior change, so this pass adds a genuine What's New entry.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_13_FOR_YOU_STRATEGY_RECOVERY_AND_RELEVANCE_PLAN.md`
- `docs/community/KMK_RECS_V0_8_13_FOR_YOU_STRATEGY_RECOVERY_AND_RELEVANCE_IMPLEMENTATION.md`

Live-device audit (Vortex Scans) confirmed two structural bugs beyond the v0.8.12 catalogue
fallback: (1) a persisted `TEXT_ONLY_TOP_TAGS` strategy was terminal in `RecommendationQueryPlanner`
and never actively cleared on failure, permanently trapping a source in the least reliable search
strategy; (2) `PersonalRecommendationScorer.rankCandidates`/`RecommendationCandidateMemoryRanker
.merge` let a candidate become eligible from source affinity alone, with no actual tag match --
confirmed live in `recommendation_candidate_memory` rows with a positive score and null
`matched_groups_json`.

Delivered (Phases A-H): new `RecommendationStrategyRecoveryPolicy` decides whether a persisted
strategy hint is still trustworthy before `RecommendationQueryPlanner.buildPlans` uses it, and
`BrowsePersonalRecommendationsScreenModel.load()` now actively removes a source's persisted strategy
on `NoMatches`/`FilteredOut`/`Error`/`HiddenByDuplicateHandling`. `RecommendationQueryPlanner
.buildPlans` rewritten to delegate to `RecommendationQueryAttemptPolicy.buildTagAttemptChain` and
rotate it, so no valid last strategy (including `TEXT_ONLY_TOP_TAGS`) can ever eliminate the other
attempts. New `RecommendationAdditionalPagePolicy.shouldDiscoverAdditionalPage` stops extra-page
discovery for a zero-raw page-1 query. `PersonalRecommendationScorer.rankCandidates` gained a
`requirePositiveTasteEvidence` parameter (default `true`, backward compatible) requiring
`matchedGroups.isNotEmpty()`; `RecommendationCandidateMemoryRanker.merge` applies the same gate
directly. The v0.8.12 catalogue fallback now also records itself in candidate memory/discovery
progress under a named `RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY` constant (never a
persisted strategy) for diagnosability. `searchSource()` refactored into `processRawCandidates`,
`mergeFreshAndRememberedCandidates`, `tryCatalogueFallback`, `resolveEffectiveStrategy`, and
`finalEmptyOutcome` helpers, deduplicating logic that previously existed twice (plan loop + catalogue
fallback) -- no behavior change beyond the other phases' fixes. Source status strings for
`NoMatches`/`FilteredOut` clarified. Full per-source fallback/no-evidence provenance display in
Recommendation Settings was scoped down to string-clarity only (see implementation report for the
documented reasoning) -- it would require new screen-model data plumbing not otherwise needed by this
plan's core fix.

Tests added: `RecommendationStrategyRecoveryPolicyTest` (14), `RecommendationAdditionalPagePolicyTest`
(5), plus updates to `RecommendationQueryPlannerTest`, `RecommendationCatalogueFallbackPolicyTest`,
`PersonalRecommendationScorerTest`, `RecommendationCandidateMemoryRankerTest`,
`KmkRecsReleaseNotesTest`.

Tests run:

- `exh.recs.*` (full package) — all PASSED
- Full `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.13-debug.apk
```

### KMK-Recs v0.8.13-fix1 (Recommendation Settings and Source Evaluation UX, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `766`/`"KMK-Recs v0.8.13-fix1"` — real UX changes, so this
pass adds a genuine What's New entry.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_13_FIX1_RECOMMENDATION_SETTINGS_AND_EVALUATION_UX_PLAN.md`
- `docs/community/KMK_RECS_V0_8_13_FIX1_RECOMMENDATION_SETTINGS_AND_EVALUATION_UX_IMPLEMENTATION.md`

Live-device evidence (Samsung tablet, ADB) confirmed a Recommendation Settings search flicker:
`produceState(initialValue = null, ...)` combined with `Crossfade(targetState = result)` intentionally
passed every keystroke through a null/blank state and cross-faded to the result list; `uiautomator
dump` failed with "could not get idle state" while the crossfade animated. Fixed by replacing both
with a synchronous `remember(entries, searchKey)` computation (the search itself is pure, local, and
cheap) — rows now render fully opaque immediately, no intermediate state.

Delivered (Phases A-G): search-result category/control row distinction (`Category · summary`
subtitle for anchored control rows, vs. category alone for category rows) so a query like "fil" no
longer reads as several identical Source Evaluation rows; language selector moved from For You
Display to Source Priority (source scope, not display density) and group-recommendation preview
budget moved from For You Display to Matching and versions (controls group-recommendation previews,
not the main For You page) — both pure moves, same preference keys, every language still available;
new read-only "Preview For You layout" dialog on Source Priority
(`RecommendationForYouPreviewPolicy`) showing source order/enabled/liked/disliked/boosted/last-status/
result-budget/selected-languages from already-loaded state only — no network, source, or fetch calls;
`SourceEvaluationStaleCompletionDisplayPolicy` extended to a three-state `DisplayState`
(`Hidden`/`CompletedAllActionable`/`CompletedWithUnreachableRemaining`) so the completion card can no
longer read as if excluded outdated rows had also been processed; `SourceEvaluationOutdatedReconciliation
.Result` gained `hasActionableOutdated`/`hasUnreachableOutdated`/`completionStillHasUnreachableRows`
helper properties; Source Evaluation's quarantine/blocked diagnostics now collapse by default; v0.8.13
residual completion (`searchSource()` verified at ~350 lines, well under the compiler instruction-limit
risk threshold — no further extraction needed; duplicate v0.8.13 encyclopedia rows merged into one).

**Deferred, documented (not implemented this pass):** fallback/provenance UI in Source Priority/For
You source status details (showing when a source's last useful candidates came from
`RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY`) requires new screen-model data plumbing
(`RecommendationsSettingsScreenModel` has no dependency on candidate-memory/discovery-progress stores
today) — the same scope boundary documented in the v0.8.13 implementation report's Phase H. Recorded
as a `NEXT_WORK.md` item; the underlying data already exists (v0.8.13 Phase D), only the read-side UI
wiring remains.

Files changed: `app/src/main/java/exh/recs/settings/RecommendationSettingsSearchScreen.kt`,
`RecommendationSettingsSearchIndex.kt` (no functional change, re-verified), `RecommendationForYouSettingsScreen.kt`,
`RecommendationSourcePrioritySettingsScreen.kt`, `RecommendationMatchingVersionsSettingsScreen.kt`,
`RecommendationSettingsIndexScreen.kt`, `RecommendationForYouPreviewPolicy.kt` (new),
`app/src/main/java/exh/recs/evaluation/SourceEvaluationOutdatedReconciliation.kt`,
`SourceEvaluationStaleCompletionDisplayPolicy.kt`, `SourceEvaluationScreen.kt`,
`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=766),
`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`, `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
(duplicate row merge).

Tests added/updated: `RecommendationSettingsSearchIndexTest` (+5), `RecommendationSettingsScreenAnchorKeysTest`
(updated for moved/new anchors), `RecommendationForYouPreviewPolicyTest` (6, new),
`SourceEvaluationOutdatedReconciliationTest` (+2), `SourceEvaluationStaleCompletionDisplayPolicyTest`
(+5), `KmkRecsReleaseNotesTest` (updated).

Tests run:

- `exh.recs.settings.*`, `exh.recs.evaluation.*`, `exh.recs.RecommendationStrategyRecoveryPolicyTest`,
  `exh.recs.RecommendationAdditionalPagePolicyTest`, `exh.recs.RecommendationQueryPlannerTest`,
  `exh.recs.PersonalRecommendationScorerTest`, `exh.recs.memory.RecommendationCandidateMemoryRankerTest` — all PASSED
- Full `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.13-fix1-debug.apk
```

### KMK-Recs v0.8.14 (Recommendation Settings Structural UX correction, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `767`/`"KMK-Recs v0.8.14"`.

Plan and audit:

- `docs/community/KMK_RECS_STRUCTURAL_UX_CORRECTION_AUDIT.md`
- `docs/community/KMK_RECS_V0_8_14_RECOMMENDATION_SETTINGS_STRUCTURAL_UX_PLAN.md`
- `docs/community/KMK_RECS_V0_8_14_RECOMMENDATION_SETTINGS_STRUCTURAL_UX_IMPLEMENTATION.md`

Live-device evidence (ADB, Samsung SM-X520) after v0.8.13-fix1 still showed 7 top-level Recommendation
Settings rows and a `hide` search still surfacing "For You" as a result — the prior pass renamed/moved
individual controls but never removed "For You" and "Matching and versions" as standalone top-level
destinations, which the user judged structurally wrong (their controls are general taste/filter and
advanced version/quality controls, not meaningful sections of their own).

Delivered (Phases A-G): Recommendation Settings index rebuilt to exactly five sections — Sources and
languages, Taste and filters, Source Evaluation, Sources to try, Management and diagnostics.
`RecommendationForYouSettingsScreen.kt` and `RecommendationMatchingVersionsSettingsScreen.kt` deleted
(judged safe: no serialized-navigation compatibility concern for a private/dev build); every control
they owned moved verbatim (same `RecommendationsSettingsScreenModel` methods/preference keys, zero
preference-behavior change) into `RecommendationTasteTagsSettingsScreen` (rated visibility, hide known
manga, minimum chapter count) or `RecommendationDiagnosticsSettingsScreen` (result budget, find other
versions/same-manga matching, Best Version preview, group-preview budget). Search routing and anchor
tests updated to match. Source Evaluation's stale-reassessment contract was already correct at the
count/button level from v0.8.13-fix1 (`state.staleCandidates` was already actionable-only); this pass
closed the two remaining gaps: the completion message now states the exact excluded count ("Actionable
outdated sources reassessed. N outdated source(s) are outside this run because they are installed,
filtered, or excluded."), and a past-evaluation row that is outdated but excluded from the current
reassessment pool now reads "Outdated — not included in this run" instead of "Outdated — reassess
needed" (`EvaluationResultRow` gained an `isActionableOutdated` param wired from
`state.outdatedReconciliation.workableOutdatedExtensionKeys`). Source Evaluation's first screen also
had its remaining visual fog reduced: the skip/include-explicit toggles, candidate diagnostics, and the
installer-mode selector are now collapsed by default behind "More setup options"/"Installer details"
disclosures — the installer selector still force-shows whenever the installer isn't ready, so no
critical setup detail is ever hidden. A few remaining technical terms ("probe", "eligible") in primary
Source Evaluation copy were replaced with plain wording.

Files changed: `app/src/main/java/exh/recs/settings/RecommendationSettingsIndexScreen.kt`,
`RecommendationSettingsSearchScreen.kt`, `RecommendationTasteTagsSettingsScreen.kt` (rewritten),
`RecommendationDiagnosticsSettingsScreen.kt` (rewritten), `RecommendationSourcePrioritySettingsScreen.kt`
(renamed to Sources and languages), `RecommendationForYouSettingsScreen.kt` (deleted),
`RecommendationMatchingVersionsSettingsScreen.kt` (deleted),
`app/src/main/java/exh/recs/evaluation/SourceEvaluationOutdatedReconciliation.kt` (doc only, contract
unchanged), `SourceEvaluationStaleCompletionDisplayPolicy.kt` (doc only, contract unchanged),
`SourceEvaluationScreen.kt`, `SourceEvaluationScreenModel.kt` (`showSetupOptions`/`showInstallerDetails`
state + toggles), `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=767),
`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`.

Tests added/updated: `RecommendationSettingsSearchIndexTest` (5-section fixtures + new tests),
`RecommendationSettingsScreenAnchorKeysTest` (rewritten key lists; new `setup_options_toggle`/
`installer_details_toggle` static keys), `SourceEvaluationOutdatedReconciliationTest` (+1,
`workableOutdatedExtensionKeys` membership).

Tests run:

- `:app:testDebugUnitTest --tests "*RecommendationSettingsSearchIndexTest" --tests
  "*RecommendationSettingsScreenAnchorKeysTest" --tests "*SourceEvaluationOutdatedReconciliationTest"
  --tests "*SourceEvaluationStaleCompletionDisplayPolicyTest"` — all PASSED
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL
- Full `:app:testDebugUnitTest`, `:app:spotlessCheck`, `:app:assembleDebug` — pending final verification
  pass (see implementation report for final status).

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.14-debug.apk
```

### KMK-Recs v0.8.14-fix1 (Recommendation Settings structural completion, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `768`/`"KMK-Recs v0.8.14-fix1"`.

Plan, live-device follow-up, and implementation report:

- `docs/community/KMK_RECS_V0_8_14_LIVE_DEVICE_STRUCTURAL_UX_FOLLOWUP.md`
- `docs/community/KMK_RECS_V0_8_14_FIX1_RECOMMENDATION_SETTINGS_STRUCTURAL_COMPLETION_PLAN.md`
- `docs/community/KMK_RECS_V0_8_14_FIX1_RECOMMENDATION_SETTINGS_STRUCTURAL_COMPLETION_IMPLEMENTATION.md`

Live-device review (ADB, Samsung SM-X520) after v0.8.14 confirmed that pass renamed/moved individual
controls but did not fully implement the intended structure: the top-level row still said "Sources and
languages" with language selection bundled into it, the "Preview For You" dialog still showed only
source order/status text (never actual manga), and the Source Evaluation excluded-sources note still
sat ahead of the "Reassess outdated" action, reading as if it blocked that action.

Delivered (Phases A-F): "Sources and languages" renamed "For You sources" (index label + screen `AppBar`
title); `LanguageSelectorContent` moved from that screen to `RecommendationDiagnosticsSettingsScreen`'s
new "Recommendation languages" section (same `state.recommendationLanguages`/`state.availableLanguages`/
`screenModel::toggleRecommendationLanguage`, zero preference-behavior change); the Preview For You action
moved from the bottom of the source screen to the top. The status-only preview was replaced entirely: a
new `RecommendationForYouPreviewSnapshot`/`RecommendationForYouPreviewSnapshotStore` (compact
control-character-delimited string format, same style as `RecommendationSourceRunStatusStore`, capped at
21 rows / 30 manga per row, storing only manga id/source id/url/title/thumbnail url — no descriptions,
genres, or scores) is built by `BrowsePersonalRecommendationsScreenModel.persistForYouPreviewSnapshot()`
after a For You run finishes with at least one visible row (Top Picks first, then deduped visible source
rows in priority order) and persisted to the new `recommendationForYouPreviewSnapshot()` preference;
`RecommendationsSettingsScreenModel` reads and live-updates it via `.changes()`, the same pattern already
used for source-run statuses. The new `ForYouSnapshotPreviewDialog` renders actual manga covers
(`MangaCover.Book`, no `onClick`) and titles in horizontally-scrolling rows — no source calls, no network
calls beyond the already-cached cover images, no manga/source navigation. Source Evaluation: the
`Reassess outdated (N)` action (already actionable-count-only since v0.8.13-fix1/v0.8.14) now renders
ahead of the excluded/unreachable-sources note, which is now a collapsed `N outdated source(s) outside
this run` `DisclosureToggleRow` instead of an always-visible `InfoCard`. Recommendation Settings search:
category-level result rows no longer fall back to showing their own category name as a subtitle when it
equals the title (previously every category row read like "Taste and filters" / "Taste and filters");
"language" search now routes to Management and diagnostics, not For You sources. Stale
`RecommendationForYouSettingsScreen`/`RecommendationMatchingVersionsSettingsScreen` references audited
across active code — every remaining mention is inside a comment explicitly marked "retired"/"former",
no live imports or instantiations exist. Two icon-only tap targets in `EvaluationResultRow` (the
rec-quality/catalogue-evidence `Show details`/`Hide details` toggles, previously a 20dp `IconButton`
next to a non-clickable label) were changed to whole-row clickable targets, and the row overflow menu
(previously a 28dp `IconButton`) now uses the default 48dp `IconButton` size.

Files changed: `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` (new
`recommendationForYouPreviewSnapshot()` preference),
`app/src/main/java/exh/recs/settings/RecommendationForYouPreviewSnapshotStore.kt` (new, replaces the
deleted `RecommendationForYouPreviewPolicy.kt`), `RecommendationSourcePrioritySettingsScreen.kt`,
`RecommendationDiagnosticsSettingsScreen.kt`, `RecommendationSettingsIndexScreen.kt`,
`RecommendationSettingsSearchScreen.kt`, `RecommendationSettingsSearchIndex.kt` (doc only),
`RecommendationSettingsSharedComponents.kt` (doc only),
`RecommendationNonInstalledDiscoverySettingsScreen.kt` (doc only), `RecommendationsSettingsScreenModel.kt`,
`app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`,
`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`,
`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`,
`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=768),
`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`.

Tests added/updated: `RecommendationForYouPreviewSnapshotStoreTest` (9, new, round-trip/cap/malformed
coverage), `RecommendationSettingsScreenAnchorKeysTest` (rewritten `forYouSourcesKeys`, moved language
keys into `diagnosticsKeys`, new `outdated_excluded_toggle` static key), `RecommendationSettingsSearchIndexTest`
(fixture renamed, new "Sources and languages" retirement test, `searching language` test repointed to
Management and diagnostics), `KmkRecsReleaseNotesTest` (updated heading order). Deleted:
`RecommendationForYouPreviewPolicyTest.kt` (its subject, `RecommendationForYouPreviewPolicy`, was deleted
— replaced by the real snapshot).

Tests run:

- `:app:testDebugUnitTest --tests "*RecommendationSettingsSearchIndexTest" --tests
  "*RecommendationSettingsScreenAnchorKeysTest" --tests "*RecommendationForYouPreview*" --tests
  "*SourceEvaluationOutdated*" --tests "*SourceEvaluationStaleCompletionDisplayPolicyTest"` — all PASSED
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL
- Full `:app:testDebugUnitTest`, `:app:spotlessCheck`, `:app:assembleDebug` — see implementation report
  for final status.

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.14-fix1-debug.apk
```

### KMK-Recs v0.8.15 (Source Evaluation reassessment fix and universal UI readability, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `769`/`"KMK-Recs v0.8.15"`.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_PLAN.md`
- `docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_IMPLEMENTATION.md`

Live ADB evidence (device `R5GL201CAQX`, package `app.komikku.dev`) confirmed a false-completion bug:
tapping `Reassess outdated (25)` finished in under a second, showed `Evaluation completed`, but a
before/after database comparison showed zero source_evaluation changes, and 44 of the 48 stale rows
had a non-null `source_id`. Root cause, confirmed by code inspection: `SourceEvaluationRunner
.recordExtensionError()` wrote its error record via a fire-and-forget `scope.launch { ... }` (never
awaited, so the batch/worker could reach a terminal state before the write landed), and built its key
with `sourceId = null` (`SourceEvaluationScorer.errorRecord()`), which never matches — and so never
replaces — existing per-source stale rows. Fixed: `recordExtensionError()` is now `suspend` and
awaited directly by every caller in `evaluateExtension()`; it now also calls the already-existing
`SourceEvaluationRepository.deleteByPackage`/`DeleteSourceEvaluation.awaitByPackage` (no schema
change) to clear old per-source rows for that package/signature before upserting the current
extension-level row. `evaluateExtension()` now returns whether it durably wrote anything, and the
runner only advances `_completedCandidateKeys` (used for both stale-cursor advancement and the
end-of-run status decision) for durably-handled candidates instead of every candidate handed to it.
The end-of-run decision itself was extracted into a new pure `SourceEvaluationRunCompletionPolicy
.resolveStatus(candidatesCount, durablyHandledCount)`: a non-empty batch with zero durable writes now
resolves to a new `SourceEvaluationQueueState.Status.NoActionableWork` terminal status (own icon/message,
same terminal/idle/lifecycle handling as Completed/Failed/etc.) instead of the misleading generic
`Completed`.

Also started the universal KMK UI readability audit (Phase B): Source Evaluation past-evaluation rows'
main subtitle compacted from a single dense line (extension, language, catalogue fit %, metadata
confidence, evidence strength, last-evaluated all at once) to `"<verdict> • Last evaluated ..."`, with
the dropped raw facts moved into the row's existing expandable evidence-details disclosure; three
Recommendation Settings summaries (`same_manga_match_preselect_summary`, `rec_enrichment_cap_summary`,
`rec_group_preview_budget_summary`) shortened to plainer first-view wording; two mojibake'd strings
(an en dash rendering as `1â€“2`, a multiplication sign rendering as `2Ã—`) corrected to plain ASCII;
new living `docs/community/KMK_RECS_UNIVERSAL_UI_READABILITY_AUDIT.md` covering every KMK-added
Recommendation Settings/For You surface (several verified compliant this pass without changes,
several explicitly deferred with reasons — no surface claimed compliant without being opened and read
this pass).

Files changed: `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`,
`SourceEvaluationQueueState.kt`, `SourceEvaluationRunCompletionPolicy.kt` (new),
`SourceEvaluationCompletionLifecyclePolicy.kt`, `SourceEvaluationScreen.kt`,
`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=769),
`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`,
`docs/community/KMK_RECS_UNIVERSAL_UI_READABILITY_AUDIT.md` (new).

Tests added: `SourceEvaluationRunCompletionPolicyTest` (4, new), `SourceEvaluationQueueStateTest` (4,
new), `SourceEvaluationCompletionLifecyclePolicyTest` (+1, NoActionableWork lifecycle coverage).

Tests run:

- `:app:testDebugUnitTest --tests "exh.recs.evaluation.*" --tests "exh.recs.settings.*" --tests
  "exh.recs.KmkRecsReleaseNotesTest"` — all PASSED
- `:app:spotlessCheck` — BUILD SUCCESSFUL
- Full `:app:testDebugUnitTest`, `:app:assembleDebug` — see implementation report for final status.

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.15-debug.apk
```

### KMK-Recs v0.8.15-fix1 (Source Evaluation stale queue and row readability, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `770`/`"KMK-Recs v0.8.15-fix1"`.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_15_FIX1_SOURCE_EVALUATION_STALE_QUEUE_AND_ROW_READABILITY_PLAN.md`
- `docs/community/KMK_RECS_V0_8_15_FIX1_SOURCE_EVALUATION_STALE_QUEUE_AND_ROW_READABILITY_IMPLEMENTATION.md`

Live ADB/database evidence (device `SM_X520`, package `app.komikku.dev`) showed v0.8.15 did not fully
close the "Reassess outdated" false-progress bug: `Reassess outdated (25)` advanced to `Continue
reassessing outdated (15 remaining)` and reported `Evaluation completed`, but `source_evaluation` had
the same 48 stale rows before and after (425 total rows, 48 stale, 44 stale source-level + 4 stale
extension-level, unchanged). Root cause: `SourceEvaluationCandidateQueuePolicy.staleCandidates(...)`
built the stale queue from `pool.allEligible`, which deliberately does not apply explicit/adult-content
blocking (that filtering is option-based, applied downstream by `SourceEvaluationCandidateFilter
.applyOptions` for the *unassessed* queue only). An explicit-blocked extension could therefore still
enter `state.staleCandidates`; `SourceEvaluationRunner.start()` correctly skipped it before any database
write (blocking is honored) but still added its key to the cursor-tracking set, so the stale cursor
advanced past sources that were never actually processed.

Delivered (Fixes A-G): `staleCandidates(...)` gained an `includeExplicit` parameter and now filters
blocked-explicit extensions out of the actionable list itself, mirroring `applyOptions`'s existing
behavior -- non-actionable candidates never reach the runner in the first place.
`SourceEvaluationScreenModel` passes `opts.includeExplicitCandidates` through. As a second, independent
guarantee, the runner's cursor-tracking set was kept as `_completedCandidateKeys` but contract-narrowed to durable writes only and the
deliberate explicit-skip branch no longer adds to it at all (only `skippedCount` increments) -- so even
a future no-write skip path could never advance the cursor or satisfy
`SourceEvaluationRunCompletionPolicy`'s durable-write count. `recordExtensionError()`'s v0.8.15
delete-before-upsert step is now failure-aware: it returns whether the delete succeeded, every call
site propagates that as the candidate's durable-handled result (`return recordExtensionError(...)`
instead of an unconditional `return true`), and a new `SourceEvaluationQueueState
.reconciliationFailedCount` plus in-app note gives an honest explanation instead of a silent
retry-forever loop. Also delivered the plan's clarified Source Evaluation row-action model: a new pure
`SourceEvaluationRowActionPolicy` (`InstallEligibility`, `hasErrorInfo`); the existing catalogue-evidence
disclosure keeps its "Details" label; the existing rec-quality/search-compatibility disclosure was
renamed "Errors" (`source_evaluation_row_show_details`/`hide_details` string values changed) and now
also covers catalogue-level evaluation errors; a new "Install" `TextButton` reuses
`extensionManager.installExtension(...)` via a new `SourceEvaluationScreenModel
.installEvaluatedSource()`, shown only when `canOfferInstall` confirms the source is not installed,
blocked/quarantined, or unavailable; and a page-level "Sources to try" app-bar action navigates to the
existing `RecommendationNonInstalledDiscoverySettingsScreen` (no duplicate screen). No source-quality
rating controls were added to Source Evaluation. No schema/migration changes.

Files changed: `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateQueuePolicy.kt`,
`SourceEvaluationRunner.kt`, `SourceEvaluationScreenModel.kt`, `SourceEvaluationScreen.kt`,
`SourceEvaluationQueueState.kt`, `SourceEvaluationRowActionPolicy.kt` (new),
`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=770),
`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`.

Tests added/updated: `SourceEvaluationCandidateQueuePolicyTest` (+4, explicit-blocking cases),
`SourceEvaluationRowActionPolicyTest` (9, new), `KmkRecsReleaseNotesTest` (heading order updated).

Tests run:

- `:app:testDebugUnitTest --tests "exh.recs.evaluation.*" --tests "exh.recs.KmkRecsReleaseNotesTest"`
  — all PASSED
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL
- `:app:spotlessCheck`, full `:app:testDebugUnitTest`, `:app:assembleDebug` — see implementation report
  for final status.

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.15-fix1-debug.apk
```

### KMK-Recs v0.8.16 (Interaction, changelog, and Best Version polish, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `771`/`"KMK-Recs v0.8.16"`.

Plan and implementation report:

- `docs/community/KMK_RECS_V0_8_16_INTERACTION_CHANGELOG_AND_BEST_VERSION_POLISH_PLAN.md`
- `docs/community/KMK_RECS_V0_8_16_INTERACTION_CHANGELOG_AND_BEST_VERSION_POLISH_IMPLEMENTATION.md`

Delivered:

- **Phase A** — `MangaInfoHeader.MangaActionRow`: "Find best version" moved out of the taste/seen
  dropdown into its own `MangaActionButton` (`Icons.AutoMirrored.Outlined.CompareArrows`), gated only
  on `onFindBestVersionClicked != null` (no longer coupled to `onSeenClicked`).
- **Phase B** — `BestVersionCompareScreenModel`/`BestVersionCompareScreen`: candidate rows in
  confirmation/chapter-selection/preview now show source name (`sourceName(sourceId)`, resolved via
  the already-injected `SourceManager`); a new `FullscreenCandidatePreviewDialog` shows every sampled
  page for one candidate in a scrollable, read-only view with source/title context; `confirmMigration`
  now stores `completedTargetMangaId = target.id` (candidates are already localized by
  `SameMangaCandidateSearcher`, so no second `networkToLocalManga` call was needed) and Done
  navigates via a new pure `BestVersionMigrationCompletionPolicy` (`navigator.replace(MangaScreen(...))`
  on success, `navigator.pop()` fallback if the target id is somehow null).
- **Phase C** — `BrowsePersonalRecommendationsTab`/`BrowsePersonalRecommendationsScreenModel`: long-press
  on a For You card enters selection mode (new pure `ForYouSelectionPolicy`, keyed by
  `MangaIdentityKey`); a bottom action bar offers Love/Like/Dislike (`SetMangaTasteBatch`, already
  registered in `KMKDomainModule`), Not interested (`SeenRecommendationMangaStore`, same store manga
  detail already uses), and (single-selection only) Find best version / Open. Bulk add-to-library was
  evaluated and deliberately deferred (documented, not a fake button) -- it needs category-selection UX
  beyond this pass's scope.
- **Phase D** — new pure `KmkRecsReleaseNotesGroupingPolicy` splits `KmkRecsReleaseNotes.MARKDOWN` into
  per-version sections and groups by major.minor family; `KmkRecsWhatsNewScreen` now renders each family
  as an expandable section (current v0.8.x expanded, older families collapsed with a short summary)
  using `InfoScreen` + `MarkdownRender` directly -- the official upstream `WhatsNewScreen` is untouched.
  No historical entry was deleted or reformatted.
- **Phase E** — `SourceEvaluationContinuationPolicyTest`'s stale doc comment (claiming completed keys are
  populated "unconditionally on handoff") corrected to describe the v0.8.15-fix1 durable-write-only
  contract; new pure `SourceEvaluationExtensionErrorReconciliationPolicy` extracted from
  `SourceEvaluationRunner.recordExtensionError()`'s delete-then-upsert decision, with direct test
  coverage (`SourceEvaluationExtensionErrorReconciliationPolicyTest`) -- full runner-level testing
  remains too heavy (no fake/mock harness for the delete/upsert interactors in this suite, consistent
  with the v0.8.15-fix1 precedent). `NEXT_WORK.md`'s v0.8.15/v0.8.15-fix1 rolling sections were verified
  to already read as historical/resolved, not pending -- no change needed there.

Files changed: `app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt`,
`app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt`,
`BestVersionCompareScreen.kt`, `BestVersionMigrationCompletionPolicy.kt` (new),
`app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`,
`BrowsePersonalRecommendationsScreenModel.kt`, `ForYouSelectionPolicy.kt` (new),
`app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt`,
`app/src/main/java/exh/recs/KmkRecsReleaseNotesGroupingPolicy.kt` (new),
`app/src/main/java/exh/recs/evaluation/SourceEvaluationExtensionErrorReconciliationPolicy.kt` (new),
`SourceEvaluationRunner.kt`, `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=771),
`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`.

Tests added/updated: `BestVersionMigrationCompletionPolicyTest` (new), `ForYouSelectionPolicyTest` (new),
`KmkRecsReleaseNotesGroupingPolicyTest` (new),
`SourceEvaluationExtensionErrorReconciliationPolicyTest` (new),
`SourceEvaluationContinuationPolicyTest` (comment fix, no behavior change), `KmkRecsReleaseNotesTest`
(heading order + rating-menu wording scoped to the current entry).

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.16-debug.apk
```

### KMK-Recs v0.8.16-fix1 (UI readability and responsive polish, complete)

`VERSION_CODE`/`VERSION_NAME` bumped to `772`/`"KMK-Recs v0.8.16-fix1"`.

Plan and evidence:

- `docs/community/KMK_RECS_V0_8_16_FIX1_UI_READABILITY_AND_RESPONSIVE_POLISH_PLAN.md`
- `docs/community/KMK_RECS_V0_8_16_FIX1_UI_READABILITY_AND_RESPONSIVE_POLISH_IMPLEMENTATION.md`
- `C:\Users\USER\Downloads\Komikku\diagnostics\ui-audit-v0.8.16\UI_AUDIT_NOTES.md` (live ADB tablet audit)

Delivered (Phases A-G):

- **Phase A** — Source Evaluation reassessment copy: `source_evaluation_stale_reassess_complete_partial`
  reworded from one success-reading sentence into two clearer sentences ("Finished reassessing... N
  source(s) were skipped this run."); `source_evaluation_outdated_excluded_toggle` label shortened to
  "N skipped source(s)". `SourceEvaluationStaleCompletionDisplayPolicy`'s existing three-state
  `DisplayState` (Hidden/CompletedAllActionable/CompletedWithUnreachableRemaining) and its existing
  test coverage already satisfied the plan's required scenarios -- no policy/test change needed, copy
  only. The stale-reassess CTA already only renders when `state.staleCandidates.isNotEmpty()` (pre-
  existing), so a zero-actionable state already shows no runnable-looking button.
- **Phase B** — `EvaluationResultRow`: subtitle bumped `bodySmall` → `bodyMedium`; `Details`/`Errors`/
  `Install` toggle labels bumped `labelSmall` → `bodySmall` and merged into one shared `FlowRow` so they
  sit side by side on wide screens and wrap on narrow width instead of each always claiming a full-width
  line. Raw metrics inside expanded Details/Errors remain `labelSmall` (unchanged -- true secondary
  diagnostics).
- **Phase C** — new pure `ForYouSelectionActionLayoutPolicy` (`layoutFor(maxWidthDp)`,
  `overflowActions(layout, isSingleSelection)`); `ForYouSelectionBottomBar` wrapped in
  `BoxWithConstraints`, compact width (<720dp) keeps Love/Like/Dislike visible and moves Not
  interested/Find best version/Open into a "More" `DropdownMenu`; wide width renders every action
  exactly as v0.8.16 did. No action was removed at any width.
- **Phase D** — `ForYouSnapshotPreviewDialog` rebuilt from a compact `AlertDialog`
  (`LazyColumn(heightIn(max = 420.dp))`) to a full-screen `Dialog` + `Scaffold`/`AppBar`, matching
  `BestVersionCompareScreen`'s existing fullscreen-dialog pattern; height cap removed; close action is
  top-left (`AppBar`'s standard navigation-icon position, matching every other Komikku/KMK top app bar
  in this app, not a top-right-only icon). Still consumes only `RecommendationForYouPreviewSnapshot`;
  no `onClick` was added to any card/row.
- **Phase E** — Recommendation Settings index: new dedicated `rec_settings_index_evaluation_summary`
  ("Find sources that match your taste.") replaces the reused `source_evaluation_settings_desc`
  ("Temporarily install non-installed extensions...") on both the index row and the search-result
  summary; `rec_settings_index_taste_filters_summary`/`_discovery_summary`/`_management_summary`
  shortened to plainer sentences. Search synonyms (installer, background, network, shizuku, ...)
  unchanged.
- **Phase F** — `SampledPage` now carries `eu.kanade.domain.manga.model.PagePreview(index, imageUrl,
  source)` instead of a bare `imageUrl: String`; all three Best Version preview surfaces (thumbnail row,
  single-page fullscreen, candidate fullscreen) use `SubcomposeAsyncImage(model = page.preview, ...)`
  with explicit loading/error content instead of `AsyncImage(model = page.imageUrl)`, routing through
  the existing `PagePreviewFetcher`/`SourceRuntime` boundary instead of bypassing it. New pure
  `BestVersionPreviewOutcomePolicy.hasUsablePreview(count)` -- a candidate with zero usable sampled
  pages now reports `CandidatePreviewState.PreviewError` instead of a false-success `Loaded` row.
  `best_version_pages_loaded` copy annotated (in-code comment) as counting prepared preview pages, not
  confirmed image-decode success; new `best_version_preview_page_failed` string replaces the previous
  silent broken-image placeholder.
- **Phase G** — this section, `CURRENT_STATE.md`, `NEXT_WORK.md`, `README.md`,
  `KMK_MARKDOWN_ENCYCLOPEDIA.md`, `KMK_RECS_UNIVERSAL_UI_READABILITY_AUDIT.md`, and the implementation
  report.

Files changed: `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`,
`app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`,
`app/src/main/java/exh/recs/ForYouSelectionActionLayoutPolicy.kt` (new),
`app/src/main/java/exh/recs/settings/RecommendationSourcePrioritySettingsScreen.kt`,
`app/src/main/java/exh/recs/settings/RecommendationSettingsIndexScreen.kt`,
`app/src/main/java/exh/recs/settings/RecommendationSettingsSearchScreen.kt`,
`app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt`,
`app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt`,
`app/src/main/java/exh/recs/bestversion/BestVersionPreviewOutcomePolicy.kt` (new),
`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE=772),
`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`.

Tests added/updated: `ForYouSelectionActionLayoutPolicyTest` (new),
`BestVersionPreviewOutcomePolicyTest` (new), `SampledPageTest` (new), `KmkRecsReleaseNotesTest`
(heading order). No pre-existing test file needed behavior changes -- Phase A's copy-only fix required
no policy/test edits, confirmed by re-running `SourceEvaluationStaleCompletionDisplayPolicyTest`.

Tests run: see the implementation report for exact commands/results.

APK handoff name:

```text
Komikku-v1.14.0-kmk.8.16-fix1-debug.apk
```

### KMK-Recs v0.8.17 (complete: Phase A/B/C/D)

`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped `772`/`"KMK-Recs v0.8.16-fix1"` →
`773`/`"KMK-Recs v0.8.17"` -- unlike the Phase-D-only intermediate pass (which intentionally did not
bump, matching the v0.8.10-fix1 through fix8 precedent for behavior-neutral passes), this final pass
includes real user-facing recommendation changes from Phase C. App `versionCode`/`versionName` bumped
`89`/`"1.14.0"` → `90`/`"1.14.1"` (`app/build.gradle.kts`), from Phase D.

**Phase B (universal UI/action standardization audit)**: re-inspected Sources to Try, Rated manga
collections, Group recommendations, and Best Version against the readability/interaction standard --
all confirmed already compliant (concise rows, one visible action plus overflow menu, official
selection-mode components reused where they fit). No code changes needed; see
`docs/community/KMK_RECS_UNIVERSAL_UI_READABILITY_AUDIT.md` and
`docs/community/KMK_RECS_INTERACTION_FUNCTIONALITY_AUDIT.md` for the row-by-row evidence.
`RecommendationDiagnosticsSettingsScreen`'s density (the one surface the plan named as needing action)
was investigated: collapsing its 6 sections by default would silently break `ScrollToAnchorEffect`
(used by every Recommendation Settings screen's search-anchor scrolling), since it assumes every
anchorable row stays present in the `LazyColumn`'s item list. A correct fix requires the anchor system
itself to auto-expand a collapsed section before scrolling -- a real cross-cutting change, not a local
edit -- so this was deliberately deferred to a dedicated follow-up rather than risking a search-anchor
regression for a cosmetic tweak.

**Phase C (For You diagnostic-first quality tuning)**: `RecommendationSourceRunStatus`/`RecommendationSourceStatus`
already distinguished `NoMatches` (search returned zero raw results) from `FilteredOut` (results existed
but were all excluded by ratings/blocked tags/no positive taste match) and was already computed
correctly in `BrowsePersonalRecommendationsScreenModel.finalEmptyOutcome()` -- but that distinction was
only ever shown as a one-word badge on the dense `SourcePriorityItem` row, with no way to see more.
Rather than build new pipeline instrumentation (high-risk, would touch `searchSource()`'s many helper
functions), the compact-explanation layer was built entirely on this existing, already-correct signal:
new `app/src/main/java/exh/recs/RecommendationSourceStatusExplanationPolicy.kt` maps each of the 7
`RecommendationSourceStatus` values to a new `rec_source_status_explain_*` string; `SourcePriorityItem`'s
status line is now tappable, opening an `AlertDialog` with the full plain-language explanation. The
always-visible compact strings (`rec_source_status_no_matches`/`rec_source_status_filtered`) were
shortened to genuinely short labels ("No matches"/"Filtered out"); their previous, longer text moved
into the new `_explain_*` strings shown only on tap. 2 new tests
(`RecommendationSourceStatusExplanationPolicyTest`).

Plan, evidence, and reports:

- `docs/community/KMK_RECS_V0_8_17_UNIVERSAL_UI_FOR_YOU_QUALITY_AND_1_14_1_PLAN.md`
- `docs/community/KMK_RECS_V0_8_17_UNIVERSAL_UI_FOR_YOU_QUALITY_AND_1_14_1_IMPLEMENTATION.md`
- `docs/community/KMK_WORKING_TREE_HYGIENE_AND_BASELINE.md`

Official Komikku `v1.14.0..v1.14.1` is a small patch release: 4 commits, 14 changed files, no
schema/proto/backup-format changes. Reconciled file-by-file:

- **`app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`** (the real risk this pass) --
  `DELEGATED_SOURCES` restructured from `Map<String, DelegatedSource>` (keyed by qualified class name,
  filtered by a separate `factory`-flag prefix-match pass) to a plain `List<DelegatedSource>` matched
  inline (`firstOrNull { exact-match || (factory && startsWith(prefix)) }`) -- fixes upstream PR #1797
  "Fix loading delegated sources with latest extension DSL changes". Pururin's entry moved from
  `PURURIN_SOURCE_ID`/`eu.kanade.tachiyomi.extension.en.pururin.Pururin` to
  `fillInSourceId`/`eu.kanade.tachiyomi.extension.all.pururin.Pururin` (matching the pattern already
  used by MangaDex/NHentai/LANraragi); MangaDex's entry corrected from a package-prefix `factory=true`
  match to an exact-class-name match; NHentai's and LANraragi's `factory=true` flags removed (their
  qualified names already included the exact class, so the flag was redundant, not load-bearing). No
  KMK customization existed inside the touched `DELEGATED_SOURCES`/`toInternalSource` region (verified
  by direct inspection before editing) -- the rest of the file's `// KMK -->`/`// SY -->` additions
  (merged sources, hentai/explicit filtering, `getMergedSources`) are untouched.
- **`app/src/main/java/eu/kanade/tachiyomi/source/online/english/Pururin.kt`** → moved to
  **`.../online/all/Pururin.kt`** (package rename, matching upstream's rename) and its redundant
  `override val lang = "en"` removed (the source is no longer English-only, matching the "all" package).
- **`app/src/main/java/eu/kanade/tachiyomi/source/online/all/NHentai.kt`**,
  **`Lanraragi.kt`** -- removed a redundant `override val lang = delegate.lang` (confirmed
  `DelegatedHttpSource` already defaults `lang` to `delegate.lang`).
- **`app/src/main/java/exh/source/SourceHelper.kt`**,
  **`app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt`** -- import path updated for
  Pururin's package move. `PURURIN_SOURCE_ID`'s own definition and its other legitimate uses
  (`ExplicitSourceClassifier.kt`, `SourceTagsUtil.kt`, `SourceHelper.kt`'s
  `LIBRARY_UPDATE_EXCLUDED_SOURCES`) are untouched -- upstream's diff never touched those files.
- **`app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt`** --
  `DELEGATED_SOURCES.values.map{}` → `DELEGATED_SOURCES.map{}` (dependent on the Map→List change above).
- **`app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/ExtensionStoreRestorer.kt`**,
  **`app/src/main/java/mihon/core/migration/migrations/TrustExtensionRepositoryMigration.kt`** -- both
  fixed to guard against a duplicated `/repo.json` suffix (`.removeSuffix("/repo.json")` added before
  re-appending it) -- upstream PR #1801.
- **`app/src/main/java/mihon/core/migration/migrations/ChapterUrlHashMigration.kt`** (new) -- defaults
  `DownloadPreferences.includeChapterUrlHash()` to `true` for existing users if unset, preserving prior
  download-folder-naming behavior (upstream PR #1800). Confirmed the underlying preference key is
  unchanged in the KMK tree before adding. Assigned `version = 90f` (KMK's own next versionCode), not
  upstream's original `81f` -- see below.
- **`app/src/main/java/mihon/core/migration/migrations/DisabledRepoMigration.kt`** (new) -- fixes a
  possible duplicated `/repo.json` suffix in the `disabled_repos` preference. **Investigated why this
  file was absent from the KMK tree entirely**: `TrustExtensionRepositoryMigration` (upstream version
  67) is present, but this migration (upstream version 80) and everything else in upstream's version
  15-79 range is either missing or deliberately commented out in `Migrations.kt` -- consistent with a
  systematic decision during the original 1.14.0 reconciliation, not an isolated oversight. However, the
  underlying `sourcePreferences.disabledRepos()` preference key (`"disabled_repos"`) is unchanged and
  still live in the KMK tree, so the fix itself remains relevant. **Root cause of the absence**: KMK's
  local `versionCode` sequence diverged from upstream's numbering before this migration existed
  upstream, and `Migrator`/`VersionRangeMigrationStrategy` only runs a migration whose `.version` falls
  in the exact `(oldVersionCode+1)..newVersionCode` range being upgraded through (confirmed by reading
  `Migrator.kt`, `MigrationStrategyFactory.kt`, `MigrationStrategy.kt`, and `App.kt`'s
  `initializeMigrator()`, which passes `old = eh_last_version_code` app-state pref and
  `new = BuildConfig.VERSION_CODE`) -- a straight copy of `version = 80f` would never have fired for any
  KMK user, since KMK's versionCode was already past 80 by the time of the 1.14.0 reconciliation. Added
  now with `version = 90f` (this release) so it finally runs once for existing KMK users, with the
  duplicate-suffix guard already correctly written from the start (there was never a KMK release with
  the buggy pre-fix version of this migration, so there was nothing to "fix" -- it's written correctly
  on arrival).

Files changed: `app/build.gradle.kts` (versionCode/versionName),
`app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`,
`app/src/main/java/eu/kanade/tachiyomi/source/online/all/Pururin.kt` (new, moved from `online/english`),
`app/src/main/java/eu/kanade/tachiyomi/source/online/all/NHentai.kt`,
`app/src/main/java/eu/kanade/tachiyomi/source/online/all/Lanraragi.kt`,
`app/src/main/java/exh/source/SourceHelper.kt`,
`app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt`,
`app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt`,
`app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/ExtensionStoreRestorer.kt`,
`app/src/main/java/mihon/core/migration/migrations/TrustExtensionRepositoryMigration.kt`,
`app/src/main/java/mihon/core/migration/migrations/ChapterUrlHashMigration.kt` (new),
`app/src/main/java/mihon/core/migration/migrations/DisabledRepoMigration.kt` (new),
`app/src/main/java/mihon/core/migration/migrations/Migrations.kt`.

Tests added: `app/src/test/java/eu/kanade/tachiyomi/source/DelegatedSourceResolutionTest.kt` (9 tests,
covering exact-match resolution for Pururin/MangaDex/NHentai/LANraragi/8Muses, confirming Pururin no
longer resolves by its old package name, confirming MangaDex no longer resolves by bare package-prefix
now that `factory=false`, and confirming no `DELEGATED_SOURCES` entry retains the pre-fix `factory=true`
flag).

Tests run: `:app:compileDebugKotlin`, `:app:testDebugUnitTest --tests
"eu.kanade.tachiyomi.source.DelegatedSourceResolutionTest"`, `spotlessCheck`, full
`:app:testDebugUnitTest` -- all passed. See the implementation report for exact output.

APK handoff name:

```text
Komikku-v1.14.1-kmk.8.17-debug.apk
```

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

### KMK-Recs v0.8.17-fix1 (complete)

`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped `773`/`"v0.8.17"` → `774`/`"v0.8.17-fix1"`. A
live-device follow-up: For You selection gained Clear Rating and real bulk-action feedback, a
rate-other-versions continuation, and consistent action-button alignment; Source Evaluation's
`Details`/`Errors`/`Install` row actions were unified into one aligned composable; a
`RecommendationSettingsQuickAccessRow` was added to all five Recommendation Settings detail screens;
Best Version preview candidates gained a bounded 25s timeout, a per-candidate Retry action, and
corrected "Prepared N of N preview samples" wording. See
`docs/community/KMK_RECS_V0_8_17_FIX1_LIVE_DEVICE_UI_ACTION_AND_BEST_VERSION_IMPLEMENTATION.md`.
App version unchanged (`1.14.1`/`90`, from v0.8.17). APK: `Komikku-v1.14.1-kmk.8.17-fix1-debug.apk`.

### KMK-Recs v0.8.18 (complete)

`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped `774`/`"v0.8.17-fix1"` → `775`/`"v0.8.18"`. This
consolidated pass supersedes the separate v0.8.17-fix2 plan (its Best Version origin/unavailable-chapter/
reader-preview scope became this pass's Phases B and C) -- fix2 was never implemented standalone. All
six phases (A-F) implemented:

- **Phase A**: removed `SourceEvaluationScreen`'s redundant top-right "Sources to try" app-bar action
  (superseded by the v0.8.17-fix1 quick-access row already on that screen). No Home/For You replacement
  action exists -- no safe route was found without either popping an unknown back-stack depth or faking
  tab navigation; documented as a deliberate non-fix rather than invented.
- **Phase B**: `BestVersionCompareScreenModel.State` gained `compareCandidates` (origin manga always
  first, followed by every selected real candidate) and `originKey`. Origin's own chapter state is
  derived directly from the already-fetched local `originChapters`/`BestVersionChapterMatcher
  .selectDefaultChapter(...)` -- never searched through extensions. New `keepCurrentVersion()` finalizes
  immediately (`step = Done`, `keptCurrentVersion = true`, `completedTargetMangaId = origin.id`) when
  origin is selected as best, with no `migrateMangaUseCase` call and no migrate/copy confirmation dialog
  -- the dialog's lookup in `BestVersionCompareScreen.kt` stays scoped to the unchanged
  `selectedCandidates` list, which never includes origin, so it structurally cannot fire for origin.
  `CandidatePreviewState` gained a `Skipped` case: a candidate whose chapter is already
  `CandidateChapterState.Unavailable` before `startPreview()` runs is marked `Skipped` immediately and
  never sent to page-list fetching -- it can no longer render as an indefinite spinner or a false
  "failure". Zero previewable candidates now short-circuits straight to `ComparePreview` (all-Skipped)
  instead of starting a pointless network round-trip. 9 new tests
  (`BestVersionOriginAndUnavailablePreviewTest`).
- **Phase C**: new pure `BestVersionReaderPreviewPolicy.resolve(defaultReadingModeValue,
  webtoonSidePaddingPercent)` maps two `ReaderPreferences` values to a side-padding decision applied
  only in `FullscreenCandidatePreviewDialog` when the user's own default reading mode is webtoon-style.
  Reads only preference *values* via `Injekt.get<ReaderPreferences>()` -- no `ReaderActivity`/
  `ReaderViewModel` embedding, and explicitly does not reuse reading history, mark-read, page timers,
  scheduled jobs, chapter transitions, preloading, Discord RPC, or the reader's own menus/page actions.
  4 new tests (`BestVersionReaderPreviewPolicyTest`).
- **Phase D**: `MangaActionRow`'s WebView, Merge, and Find best version actions moved from three
  always-visible equal-weight primary buttons into one "More" overflow `DropdownMenu` -- every action
  remains one tap away; Find best version stays out of the Rate dropdown (unchanged since v0.8.16).
- **Phase E**: new `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionApkExporter.kt` copies
  raw installed-extension APK/archive bytes unchanged (never repackaged/re-signed) via
  `ActivityResultContracts.CreateDocument`. Single export from Extension Details' overflow menu
  (`application/vnd.android.package-archive`); multi export from the Extensions page's existing
  selection mode as one `application/zip` containing every resolvable extension's file plus a
  non-sensitive `manifest.json` (pkgName/name/lang/versionName/versionCode/signatureHash/isNsfw/
  isShared/sourceCount/storeName/exportedAt/app+KMK version -- no cookies, credentials, preferences,
  ratings, recommendation data, history, or backups). A confirmation dialog warns extensions are
  executable code before every export; unresolvable source files are skipped and reported, never
  failing the whole export. 3 new tests (`ExtensionApkExporterTest`).
- **Phase F**: new `RecommendationSettingsEdgeQuickAccessPanel` in
  `RecommendationSettingsSharedComponents.kt` -- a Samsung-edge-panel-style right-edge tap-to-open handle
  (28dp wide, narrow enough to stay clear of Android's own edge-swipe-back gesture) that opens a compact
  tile panel over the current screen, sharing the exact same `RecommendationSettingsQuickAccessDestination`
  registry the existing `RecommendationSettingsQuickAccessRow` already uses -- one source of truth for
  both mechanisms. Added to all five Recommendation Settings detail screens as an overlay above each
  screen's own `Scaffold`, alongside the existing row (kept, not replaced, per the plan's "do not remove
  it blindly"). Closes via tap-outside (scrim), the handle itself, or the system back gesture
  (`BackHandler`). Swipe-to-open was judged unreliable/risky to implement safely alongside Android's own
  edge-swipe-back gesture in this pass -- tap-to-open shipped, swipe-to-open documented as a real,
  separate follow-up.

App `versionCode`/`versionName` intentionally **unchanged** (`90`/`"1.14.1"`) -- no phase proved an
Android app-version bump was required. See
`docs/community/KMK_RECS_V0_8_18_CONSOLIDATED_IMPLEMENTATION.md` for the full implementation report.
APK: `Komikku-v1.14.1-kmk.8.18-debug.apk`.

### KMK-Recs v0.8.18-fix1 (private next-work queue, complete)

`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped `775`/`"v0.8.18"` → `776`/`"v0.8.18-fix1"`. A
private corrective pass (not a public PR extraction) closing 5 items found after v0.8.18:

- **Item 1**: audited all 6 bounded-integer Recommendation Settings controls against Komikku's official
  `SliderPreference`. Only `result_budget` and `group_preview_budget` were even candidate-eligible for a
  real slider (evenly spaced `5,10,15,20,30`), and both were kept as list/dialog controls rather than
  silently widening their selectable range to include `25` -- documented with a code comment at each of
  the 6 call sites (`RecommendationDiagnosticsSettingsScreen.kt` x5, `RecommendationTasteTagsSettingsScreen.kt`
  x1). No preference keys, defaults, bounds, or behavior changed.
- **Item 2**: `ExtensionApkExporter.exportSingle(...)`/`exportMultiple(...)` now wrap their file-copy work
  in `withContext(Dispatchers.IO) { ... }` -- large exports no longer block the calling coroutine's
  original dispatcher. SAF flow, confirmation dialogs, and manifest content unchanged.
- **Item 3**: `MangaInfoHeader.kt`'s `onWebViewLongClicked` parameter (dead wiring since v0.8.18 moved
  WebView into the "More" menu, which has no long-press affordance) renamed to `onCopyLinkClicked` and
  restored as an explicit "Copy link" `DropdownMenuItem` in that same menu, reusing the existing
  `MR.strings.action_copy_link` string. Renamed at every pass-through call site
  (`presentation/manga/MangaScreen.kt`, `ui/manga/MangaScreen.kt`); the underlying `copyMangaUrl(...)`/
  merged-manga behavior is unchanged.
- **Item 4**: `RecommendationSettingsEdgeQuickAccessPanel` generalized into a reusable
  `EdgeQuickAccessPanel<T>` shell in `RecommendationSettingsSharedComponents.kt`. Removed (with its
  `Box(Modifier.fillMaxSize())` wrapper) from all five Recommendation Settings detail screens -- the
  existing `RecommendationSettingsQuickAccessRow` was left untouched on all five. Added, via a new
  `RecommendationCollectionQuickAccessDestination` enum (`ForYou`/`Loved`/`Liked`/`Disliked`) and
  `RecommendationCollectionQuickAccessPanel` wrapper, to `RatedMangaCollectionContent` (covers Loved/
  Liked/Disliked in one edit) and to `personalRecommendationsTab()`'s content (For You, overlaid inside
  `BrowseTab`'s shared tab content since this tab has no screen-owned `Scaffold`). Tap-to-open/right-edge
  handle only, unchanged from v0.8.18 -- no swipe-to-open attempted.
- **Item 5**: confirmed `SourceEvaluationScreen` shares the same root `Navigator` as `BrowseTab` (both
  reachable through `personalRecommendationsTab()`'s own push chain). Added `BrowseTab.showForYou()`/
  `switchToForYouTabChannel`, mirroring the existing `showExtension()` pattern, and a "Go to For You"
  app-bar action on `SourceEvaluationScreen` (gated on the For You tab not being hidden) that calls
  `navigator.popUntilRoot()`, selects `BrowseTab` on the tab navigator, then `BrowseTab.showForYou()`. The
  same route is reused by the new collection panel's `ForYou` destination.

Items 6-8 (universal UI audit reaffirmation, For You scoring/Vortex deferral, upstream PR base note)
required no code -- items 6's two "must fix now" sub-items are closed by items 3 and 4 above.

App `versionCode`/`versionName` unchanged (`90`/`"1.14.1"`). Full `:app:testDebugUnitTest` suite (1646
tests) passed; `KmkRecsReleaseNotesTest`'s heading-order assertion was updated for the new changelog
entry (a legitimate test update, not a regression). See
`docs/community/KMK_RECS_V0_8_18_FIX1_PRIVATE_NEXT_WORK_IMPLEMENTATION.md` for the full file list, known
limitations, and remaining manual QA. APK: `Komikku-v1.14.1-kmk.8.18-fix1-debug.apk`.
