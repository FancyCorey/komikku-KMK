# KMK-Recs v0.7.29 to v0.7.34 Polish Implementation Record

Date: 2026-06-29
Status: Complete. All 10 sub-phases (A1-A4, B, D1-D2, I1-I3, J, C1-C3) shipped.

This document records the implementation history for polish phases A-J from `KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md`.

---

## v0.7.29 -- Phase A: Quick UX Polish

Planned as v0.7.21 in the polish plan; actual shipping version was v0.7.29.

### A1 -- Source status "Last checked" timestamp

**What shipped:** Recommendation Settings source priority list now shows a "Last checked: X ago" relative timestamp below each source status label when `fitStats.runCount >= 1`. When no run history exists, nothing is shown.

**Implementation:** Updated `SourcePriorityItem` in `RecommendationsSettingsScreen.kt` to read `SourceFitStats.updatedAt` (epoch ms) and compute a relative-time string using the Android relative-time formatter. Two new KMR strings: `rec_source_last_checked_today`, `rec_source_last_checked_days_ago`.

**Files changed:**
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

---

### A2 -- Pull-to-refresh on For You

**What shipped:** The For You LazyColumn supports pull-to-refresh gesture. Pulling down calls the existing `screenModel.refresh()`. The `isLoading` state drives the refresh indicator. The action bar refresh button continues to work unchanged.

**Files changed:**
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`

---

### A3 -- Loved Manga live updates

**What shipped:** `LovedMangaScreenModel` replaced one-shot `init` load with a reactive `collectLatest` flow subscription on the taste data source. Ratings applied on other screens (e.g., manga detail) are reflected immediately in the Loved Manga grid without requiring navigate-away-and-back.

**Files changed:**
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt`

---

### A4 -- Quarantine section collapse/expand toggle

**What shipped:** The quarantine/blocked-packages row in Source Evaluation `SafetyDiagnosticsRow` now has a collapse/expand toggle. `var quarantineExpanded by rememberSaveable { mutableStateOf(true) }` drives the toggle. Count chip remains visible when collapsed; expanded by default.

**Implementation note:** A build error occurred during implementation: `modifier = Modifier.size(20.dp)` added to the chevron `IconButton` caused an unresolved reference (`size`, `dp`) because `SourceEvaluationScreen.kt` does not import `androidx.compose.foundation.layout.size` or `androidx.compose.ui.unit.dp`. Fixed by removing the `.size(20.dp)` modifier entirely -- the IconButton renders correctly at the default 48dp touch target size.

**Files changed:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

---

## v0.7.30 -- Phase B: Cross-Source Link Group Management UI

Planned as v0.7.22; actual shipping version was v0.7.30.

**What shipped:** "Manage Cross-Source Links" screen accessible from the Loved Manga action bar. Shows all cross-source link groups with expand/collapse per group. Users can delete a single link from a group or delete the entire group. Deleting a link does NOT cascade to ratings, library entries, or taste rows. The Loved Manga grouper re-runs reactively (via A3 flow subscription).

**New items:**
- `DeleteCrossSourceMangaLink` interactor in `domain/src/main/java/tachiyomi/domain/taste/interactor/`
- New `LinkGroupManagementScreenModel`
- New `LinkGroupManagementScreen`
- Entry point: link icon button in `LovedMangaScreen` action bar

---

## v0.7.31 -- Phase J: Enrichment Cap Configuration

Planned as v0.7.30; actual shipping version was v0.7.31.

**What shipped:** Recommendation Settings (advanced section) shows a configurable enrichment cap for the rec-quality probe. Options: 1, 2, 3, 5, 10, 15, 20 (default 5). Boosted sources get 2x the cap. `BrowsePersonalRecommendationsScreenModel` reads `SourcePreferences.recommendationEnrichmentCap()` at enrichment invocation time instead of using the hardcoded constant 5.

Also in v0.7.31: `sourceEvaluationLastRunRatingCount` preference added to `SourcePreferences` (foundation for C3 profile-changed prompt, completed in v0.7.34).

**Files changed:**
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

---

## v0.7.32 -- Phase D: Fit Stat Improvements

Planned as v0.7.24; actual shipping version was v0.7.32.

### D1 -- Fit stat time decay (30-day rolling window)

**What shipped:** `SourceFitStats` gained four new fields: `recentRunCount`, `recentShownCount`, `recentErrorCount`, `windowStartAt`. In `merge()`, if `now - windowStartAt > RECENT_WINDOW_MS` (30 days), the recent-window fields are reset before incrementing. The `fitLabel` algorithm uses recent rates when `recentRunCount >= MIN_RUNS_FOR_LABEL`; falls back to all-time otherwise.

**Backward compatibility:** New fields default to 0 on parse from old data (the `parts.size < N` guards in the serialization path are safe).

### D2 -- Top Picks contribution count per source

**What shipped:** After `combinedAccumulator.rank()` is computed in `BrowsePersonalRecommendationsScreenModel`, the set of source IDs contributing to the final deduplicated Top Picks result is passed as `topPicksContributors: Set<Long>` to `SourceFitStatsStore.mergeRun()`. `SourceFitStats.topPicksContributionCount` accumulates these contributions. The field is persisted but not yet displayed in the UI (tracked as open item in `NEXT_WORK.md`).

**Files changed:**
- `app/src/main/java/exh/recs/SourceFitStats.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`

---

## v0.7.33 -- Phase I: Fullscreen Preview Improvements

Planned as v0.7.29; actual shipping version was v0.7.33.

### I1 -- State restoration (rememberSaveable)

**What shipped:** `fullscreenPageUrl`, `fullscreenPageIndex`, and `fullscreenPageTitle` in `BestVersionCompareScreen.kt` changed from `remember { mutableStateOf(...) }` to `rememberSaveable { mutableStateOf(...) }`. All three are primitives (String?, Int, String) that survive the Bundle. Fullscreen dialog state now survives screen rotation and back-stack navigation.

### I2 -- Tap-to-close when not zoomed

**What shipped:** A `detectTapGestures` modifier on the fullscreen image composable fires `fullscreenPageUrl = null` (and resets index/title) only when the current zoom scale is <= 1.0. At zoom > 1x, taps are ignored by the close handler and flow through to pan/zoom handling.

### I3 -- Per-thumbnail Fit/Crop toggle

**What shipped:** Each thumbnail in the comparison grid has a small icon button at the top-right. Tapping toggles `var fitMode by remember { mutableStateOf(false) }` per thumbnail. `ContentScale.Fit` when `fitMode = true`; `ContentScale.Crop` (default) when false. State is local and not persisted.

**Files changed:**
- `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt`

---

## v0.7.34 -- Phase C: Source Evaluation UX Polish

Planned as v0.7.23; actual shipping version was v0.7.34.

### C1 -- Per-source error category labels

**What shipped:** Rec-quality ERROR rows in Source Evaluation now show a compact category badge derived from `SourceRecommendationFitFailureClassifier.classify(errorMessage)`. Categories: "Ext not found", "Install failed", "Search timed out", "Network error", "Source not found", and others (14 total from the classifier enum). The raw error message remains accessible for advanced diagnostics.

**Implementation note:** Labels are hardcoded English strings in the `when` block due to the category-label-to-string mapping pattern. These were noted as a known C1 exception in the documentation audit (similar to the existing hardcoded English labels in evidence strength before v0.7.15; a future i18n pass would address them).

### C2 -- One-tap Retry after connectivity loss

**What shipped:** After a run ends with `ConnectivityLost` status and pending candidates remain (pendingCandidates > 0), a "Retry (N remaining)" button appears in the summary card. Tapping it calls the existing `continueEvaluation()` path without resetting completed results. When no candidates remain, only "Start new run" is shown.

### C3 -- Profile-changed re-check prompt

**What shipped:** When `currentRatedCount - sourceEvaluationLastRunRatingCount >= 5`, a non-blocking banner "Your taste profile has changed -- re-run evaluation for fresh results" is shown in Source Evaluation screen. The banner is dismissible. The existing "Re-check all" button works independently. The `sourceEvaluationLastRunRatingCount` preference (added in v0.7.31) is updated each time evaluation is started.

**Files changed (C1/C2/C3):**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

---

## Build Verification Summary

Each version was built with:

```
.\gradlew.bat assembleDebug
```

Final APK from v0.7.34 build:
```
C:\Users\USER\Downloads\Komikku\Komikku-v1.13.6-kmk.7.34-debug.apk
```

The v0.7.29 build had one build failure (see A4 implementation note above) that was fixed before the APK was produced.

