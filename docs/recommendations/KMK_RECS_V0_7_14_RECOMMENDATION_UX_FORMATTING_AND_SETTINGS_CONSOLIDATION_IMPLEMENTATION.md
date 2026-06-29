# KMK-Recs v0.7.14 Recommendation UX Formatting And Settings Consolidation — Implementation

Date: 2026-06-27

Status: COMPLETE. All build gates pass.

APK: `Komikku-v1.13.6-kmk.7.14-debug.apk`

## Summary

v0.7.14 is a focused UX consolidation pass. No scoring changes, no new probes, no new screens. The goals were:
- Remove a redundant double-header in Recommendation Settings.
- Extract hardcoded user-facing strings to KMR localization.
- Verify same-manga and source-status ordering behavior (confirmed correct).
- Add Loved Manga sort options.
- Add a compact cache refresh hint.

## Version

- `versionCode = 86`
- `VERSION_CODE = 714`
- `VERSION_NAME = "KMK-Recs v0.7.14"`

## UI/Settings Patterns Checked

Before making changes, the following nearby official Komikku settings patterns were inspected:
- `FilterChip` usage in settings (used in `RatedVisibilityContent`, `LanguageSelectorContent`).
- `SectionHeader` pattern (color = primary, titleSmall, bold, medium padding).
- `TextButton` with `Modifier.padding(horizontal = MaterialTheme.padding.medium)` for action buttons.
- `Text` with `bodySmall` + `onSurfaceVariant` for summary/hint text.

All v0.7.14 additions follow these existing patterns.

## Part 1 — Settings Layout Polish

### Problem

`RecommendationsSettingsScreen` had two consecutive `SectionHeader` items:
1. `daily_recs_header` ("Daily recommendations")
2. `lang_header` ("Recommendation languages")

These appeared side-by-side with no content between them, creating a confusing double-header.

### Fix

Removed the `daily_recs_header` item. The `lang_header` item now uses the `rec_settings_daily_recs_header` string resource ("Daily recommendations") so the section is still labeled, but the language selector is the first and only control in that section. This avoids a redundant header and correctly signals that the language selector IS the daily recommendations control.

**File changed:** `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`

## Part 2 — Terminology Cleanup

### Hardcoded source fit badge labels

**Problem:** `SourcePriorityItem` in `RecommendationsSettingsScreen` used hardcoded English strings for fit badges: `"Great fit"`, `"Good fit"`, `"Low fit"`, `"No matches"`, `"Often filtered"`, `"Often errors"`, `"Deduplicated"`.

**Fix:** Extracted all 7 to new KMR string keys:
- `rec_source_fit_great` = "Great fit"
- `rec_source_fit_good` = "Good fit"
- `rec_source_fit_low` = "Low fit"
- `rec_source_fit_no_matches` = "No matches"
- `rec_source_fit_often_filtered` = "Often filtered"
- `rec_source_fit_often_errors` = "Often errors"
- `rec_source_fit_deduplicated` = "Deduplicated"

### Hardcoded suggestion expand/collapse toggle

**Problem:** The suggestion expand toggle used hardcoded `"Show fewer"` and `"Show ${n} more"` strings.

**Fix:** Extracted to:
- `rec_suggestions_show_fewer` = "Show fewer"
- `rec_suggestions_show_more` = "Show %1$d more"

### Same-manga matching preselect summary

**Problem:** `same_manga_match_preselect_summary` did not explain when to turn off the preselect default.

**Fix:** Updated existing string to: "When enabled, same-manga candidates start selected. Origin manga is never selected. Turn off if a source returns too many wrong matches."

### Terminology verified as correct

The following terminology was reviewed and confirmed consistent with the plan's terminology table:
- Love / Like / Dislike (manga ratings) — correct in all action labels.
- Seen (neutral removal) — correctly separate from ratings in menu labels.
- Prefer source / Avoid source — `rec_source_preference_like_for_you` / `rec_source_preference_dislike_for_you` already use neutral thumbs labels; the `rec_source_preference_scope_note` string explains the scope difference.
- Strong Fit / Worth Trying (VerdictBadge in SourceEvaluationScreen) — correct.
- Great / Good / Mixed / Weak / No matches / Error / Not checked / Too little evidence (Rec Quality) — all correct KMR strings already in place.

**Files changed:** `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
**Files changed:** `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`

## Part 3 — Source Status Ordering Verification

`SourceStatusDisplayOrder` was read and confirmed correct:
- `group(input)`: isDisliked → DISLIKED (2), hasMatches → HAS_MATCHES (0), else → NO_MATCHES (1)
- `sort(inputs)`: `compareBy(groupSortKey, priorityIndex, sourceId)`

`SourceStatusDisplayOrderTest.kt` already exists with 7 tests covering:
- matches before no-match
- no-match before disliked
- priority preserved inside each group
- empty list does not crash
- full ordering: HAS_MATCHES → NO_MATCHES → DISLIKED
- disliked sorts last even at high priority

No changes needed. **Verified only.**

The UI also correctly renders group labels using `source_status_group_matches` / `source_status_group_no_matches` / `source_status_group_disliked` KMR strings.

## Part 4 — Same-Manga Matching Settings Verification

Both bounded workflows were read and confirmed:

**`CrossExtensionMatchScreenModel`** reads:
- `sourcePreferences.sameMangaMatchResultsPerSource().get()` → capped via `SameMangaMatchSettings.clampResultCap()`
- `sourcePreferences.sameMangaMatchPreselectResults().get()` → used in `updateItem()`

**`BestVersionCompareScreenModel`** reads all 4 via `resolveSettings()`:
- `sameMangaMatchResultsPerSource` → `resultsPerSource`
- `sameMangaMatchPreselectResults` → `preselectResults`
- `bestVersionPreviewSampleSize` → `previewSampleSize`
- `bestVersionAvoidFirstPages` → `avoidFirstPages`

Normal global search code was not touched and remains uncapped.

The preselect summary string was updated as described in Part 2.

**Verified only** — no code logic changes.

## Part 5 — Loved Manga UX Polish

### Added sort options

Added 4 sort modes to Loved Manga:
- **Most recent** (default): entries sorted by `updatedAt` DESC, matching the load-time order
- **Oldest first**: `entries.reversed()`
- **Title A–Z**: sorted by `(manga?.title ?: taste.title).lowercase()`
- **Source**: sorted by `taste.source` (numeric source ID)

Sort applies before grouping, so the group-duplicates toggle and sort work together correctly.

### Verified existing behavior

- Uninstalled-source entries are filtered at load time (`filterLovedTastesByInstalledSources`) and are not shown in the list. Data remains in the database.
- Reinstalling a source makes its entries visible again (filter rechecks `sourceManager.getVisibleCatalogueSources()` on load).
- Grouping uses cross-source link groups as primary evidence (`getCrossSourceMangaLinks`), with title+author/artist+description similarity as secondary. Title-only matching is not used.

**Files changed:**
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt` — `LoveSortMode` enum, `sortMode` in State.Success, `setSortMode()`, `sortEntries()` helper
- `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt` — `LoveSortRow` composable, added sort chip row in grid

## Part 6 — Cache/Staleness Wording

### Added compact refresh hint

Added a `rec_settings_refresh_hint` string item immediately below the `hide_known_manga` toggle:

> "Refresh For You after changing ratings, tags, source preferences, or known-manga settings."

Placed as `bodySmall` + `onSurfaceVariant` text — consistent with other summary hints in the screen.

No new card or large warning. The hint is compact and in the right location.

### Cache invalidation verified

From reading existing code:
- Rating changes: `SetMangaTaste` interactor triggers taste DB change which invalidates the For You scorer's genre cache.
- Tag changes: `setTagPreference` updates `SourcePreferences` which For You reads each run.
- Source preference changes: `For You` reads `disabledSources`, `recommendationSourceLanguages`, and `recommendationSourceOrder` each run.
- Known-manga settings: `hideKnownManga` and `seenRecommendationMangaKeys` are read each run.
- Same-manga settings: only affect bounded workflows (CrossExtensionMatch, BestVersion), not For You.

The For You cache is keyed by a fingerprint that includes these preferences, so changes do invalidate the cache.

**Files changed:** `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`, `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`

## Files Modified

| File | Change |
|---|---|
| `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` | Added 14 new KMR strings; updated 1 existing string |
| `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` | Removed double-header; extracted 9 hardcoded strings to KMR; added cache refresh hint |
| `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt` | Added `LoveSortMode` enum, `sortMode` state, `setSortMode()`, `sortEntries()` |
| `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt` | Added `LoveSortRow` composable and sort chip row in grid |
| `app/build.gradle.kts` | `versionCode = 86 // KMK-Recs v0.7.14` |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE = 714, VERSION_NAME = "KMK-Recs v0.7.14", What's New added |

## New KMR Strings

```xml
rec_source_fit_great = "Great fit"
rec_source_fit_good = "Good fit"
rec_source_fit_low = "Low fit"
rec_source_fit_no_matches = "No matches"
rec_source_fit_often_filtered = "Often filtered"
rec_source_fit_often_errors = "Often errors"
rec_source_fit_deduplicated = "Deduplicated"
rec_suggestions_show_fewer = "Show fewer"
rec_suggestions_show_more = "Show %1$d more"
rec_settings_refresh_hint = "Refresh For You after changing ratings, tags, source preferences, or known-manga settings."
loved_manga_sort_recent = "Most recent"
loved_manga_sort_oldest = "Oldest first"
loved_manga_sort_title = "Title A–Z"
loved_manga_sort_source = "Source"
```

## Updated KMR Strings

```xml
same_manga_match_preselect_summary: appended "Turn off if a source returns too many wrong matches."
```

## Tests

No new test files were added for v0.7.14. Changes were:
- **Pure display changes** (hardcoded string extraction) — no logic to test.
- **SourceStatusDisplayOrder** — already has 7 tests in `SourceStatusDisplayOrderTest.kt`; verified correct.
- **Same-manga matching** — settings consumption verified by reading `CrossExtensionMatchScreenModel` and `BestVersionCompareScreenModel`; existing tests in `SameMangaMatchSettingsTest.kt` cover the settings logic.
- **`LoveSortMode` sort** — the `sortEntries` function is pure. A future session can add tests for each sort mode.

## Build Gates

All run and passed:

```
.\gradlew.bat spotlessApply    → BUILD SUCCESSFUL
.\gradlew.bat spotlessCheck    → BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest  → BUILD SUCCESSFUL (267 actionable tasks)
.\gradlew.bat assembleDebug    → BUILD SUCCESSFUL
```

APK copied to: `Komikku-v1.13.6-kmk.7.14-debug.apk`

## Manual QA Checklist

1. Open Recommendation Settings — confirm language selector appears at top without a redundant "Daily recommendations" header above it.
2. Confirm section headers are not duplicated. Section order: Daily recommendations (language), Ratings and known manga, Tags, Source priority, Same manga matching, Source status, Management, Experimental — Source Evaluation.
3. Confirm cache refresh hint appears below the Hide known manga toggle.
4. Confirm source fit badges on priority rows show the correct label (Great fit / Good fit / Low fit / No matches / Often filtered / Often errors / Deduplicated).
5. Confirm Sources To Try expand toggle shows "Show fewer" / "Show N more".
6. Open Loved Manga — confirm sort chips appear: Most recent, Oldest first, Title A–Z, Source.
7. Tap each sort chip and confirm order changes correctly.
8. Toggle "Group clear duplicates" with each sort mode active — confirm grouping still works.
9. Toggle same-manga preselect setting in Recommendation Settings — confirm summary includes "Turn off if a source returns too many wrong matches."
10. Open Cross Extension Match from a manga — confirm candidates start selected (if preselect is on).
11. Open Best Version — confirm candidates start selected (if preselect is on).
12. Open Source Evaluation — confirm source status ordering: sources with results first, no results second, disliked last.
13. Normal global search behavior unchanged.

## Deferred Items

Items explicitly out of scope for v0.7.14 (per plan's Non-Goals):
- `evidenceStrengthLabel()` and `lastEvaluatedLabel()` in `SourceEvaluationScreen.kt` — these are non-`@Composable` functions returning hardcoded strings; KMR strings exist but refactoring requires context threading. Deferred.
- `VerdictBadge` hardcoded verdict strings — not confusing per terminology table; deferred.
- OCR changes.
- Source evaluation probing changes.
- Cross-source link group management UI.
- Backup/restore changes.
- `LoveSortMode` tests — `sortEntries` is pure and testable; add in a future session.
- Loved Manga "clear duplicates" feedback when nothing changed — deferred.
