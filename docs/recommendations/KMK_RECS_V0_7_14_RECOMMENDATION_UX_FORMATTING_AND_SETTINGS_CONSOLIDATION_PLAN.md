# KMK-Recs v0.7.14 Recommendation UX Formatting And Settings Consolidation Plan

Date: 2026-06-27

Status: approved for Claude Code implementation after user handoff.

Target version:

```text
KMK-Recs v0.7.14
```

Expected Android versionCode:

```text
86
```

This pass follows KMK-Recs v0.7.13. It is a focused UI/UX consolidation pass for the recommendation system. It should not introduce another large recommendation engine, OCR feature, database-heavy workflow, or broad source-evaluation redesign.

## Purpose

KMK-Recs now has many useful recommendation features:

- For You recommendations.
- Top Picks.
- manga Love / Like / Dislike / Seen.
- source Prefer / Avoid.
- Source Evaluation.
- Recommendation Quality probes.
- Loved Manga.
- same-manga matching.
- Best Version comparison.
- source quality signals.
- recommendation bundle export/import.

The problem is that the features have grown in stages, so some wording, section ordering, status labels, and advanced controls can feel inconsistent. This pass should make the existing system easier to understand and more consistent with Komikku UI patterns.

This is mostly a UI-formatting, wording, settings-layout, and explanation pass. Functional changes should be small and directly tied to clarity.

## Hard Rules

1. Do not change normal global search behavior.
2. Do not change recommendation scoring unless needed only to preserve existing documented behavior.
3. Do not add another large feature surface.
4. Do not add OCR changes.
5. Do not alter source evaluation probing logic from v0.7.13 except for labels, summaries, or small display-state fixes.
6. Do not remove existing user preferences or reset user settings.
7. Do not add hardcoded user-facing strings. Use `KMR` strings in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`.
8. Check current Komikku settings/UI patterns before changing layout. Use nearby existing settings screens/components where practical.
9. If something is already implemented correctly, document that it was verified instead of rewriting it.
10. Keep all new helpers pure where practical and add tests for sort/label/visibility logic.

## Files Claude Must Inspect First

Claude must inspect these before coding:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/community/KMK_PHASE_6_7_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_PLAN.md
docs/recommendations/KMK_RECS_V0_7_12_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_7_13_SOURCE_EVALUATION_RECOMMENDATION_QUALITY_FUNCTIONAL_FIX_IMPLEMENTATION.md
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/loved/LovedMangaScreen.kt
app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
app/src/main/java/exh/recs/SourceStatusDisplayOrder.kt
app/src/main/java/exh/recs/RecommendationStatusAdjuster.kt
app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
```

Claude should also inspect nearby official Komikku settings/UI patterns in this repository before changing `RecommendationsSettingsScreen`. Good comparison targets include existing settings screens under:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/more/settings/
app/src/main/java/eu/kanade/presentation/more/settings/
```

## Scope Summary

This pass has six bounded parts:

1. Recommendation Settings layout polish.
2. Terminology cleanup.
3. For You/source status ordering and wording clarity.
4. Same-manga settings verification and wording.
5. Loved Manga UX polish.
6. Cache/staleness and refresh wording.

## Part 1: Recommendation Settings Layout Polish

### Current state

v0.7.12 reorganized `RecommendationsSettingsScreen` into sections:

- Daily recommendations.
- Ratings and known manga.
- Tags.
- Source priority.
- Same manga matching.
- Source status.
- Management.
- Experimental - Source Evaluation.

This structure is directionally correct. v0.7.14 should polish it, not replace it.

### Required behavior

1. Keep daily-use controls near the top.
2. Keep advanced/experimental controls lower.
3. Keep Source Evaluation clearly labeled as advanced/experimental.
4. Keep Sources To Try and source cleanup/management grouped under Management.
5. Avoid duplicate section headings.
6. Avoid explanatory walls of text.
7. Do not create nested cards inside cards.
8. Use compact summary text below controls only when it clarifies user impact.

### Specific implementation instructions

In `RecommendationsSettingsScreen.kt`:

- Review the current section order and only adjust if the existing order still feels inconsistent.
- If a section header is followed by another section header immediately, remove the redundant header or convert the second one to a small summary label.
- Ensure the language selector is not introduced by two separate headers that both say essentially the same thing.
- Ensure "Source status" is visually distinct from "Source priority"; priority is the reorderable control, status is last-run diagnostics.
- Ensure "Experimental - Source Evaluation" does not dominate the screen before ordinary controls.
- Keep the existing same-manga matching settings section if it is already present.
- Keep controls reachable; do not hide important controls behind a new route unless necessary.

### Acceptance criteria

- Recommendation Settings reads as one coherent settings screen.
- No control is removed.
- No preference value is reset.
- Advanced source-evaluation controls remain lower than daily-use settings.

## Part 2: Terminology Cleanup

### Current problem

The app uses overlapping words:

- Love, Like, Dislike.
- Favorite.
- Seen, Read, Known.
- Source Like/Dislike.
- Strong Fit, Worth Trying, Weak, Rejected.
- Great, Good, Mixed, Weak, No matches, Error.
- Top Picks.

The behavior is mostly correct, but the UI should use terms consistently.

### Required terminology rules

Use these user-facing meanings:

| Concept | User-facing wording |
|---|---|
| Manga rating | Love / Like / Dislike |
| Neutral removal from recommendations | Seen |
| Android/library favorite | Favorite / In library, only where this is truly Komikku's library favorite behavior |
| Installed source preference | Prefer source / Avoid source |
| Non-installed source preference | Prefer source / Avoid source |
| Source Evaluation catalog/library fit | Strong Fit / Worth Trying / Weak / Rejected |
| Recommendation Quality probe | Great / Good / Mixed / Weak / No matches / Error / Not checked / Too little evidence |
| Cross-source combined ranking | Top Picks |
| Known-manga filtering | Hide known manga |

### Required implementation

Search the active KMK recommendation UI strings and composables for confusing wording. Focus on:

```text
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
app/src/main/java/exh/recs/
```

Update only confusing user-facing strings. Do not rename database enums or internal classes unless needed for compile correctness.

Known strings likely needing review:

- `taste_pref_dislike` currently displays "Avoid" for disliked tags. This may be acceptable for tags, but source actions should clearly say "Avoid source."
- source like/dislike content descriptions and labels.
- Source Evaluation result labels.
- Recommendation Quality labels and status lines.
- same-manga matching summaries.
- Seen/Read related menu labels.

### Acceptance criteria

- Users can distinguish manga preference from source preference.
- Users can distinguish source catalog fit from recommendation quality.
- "Seen" is not presented as a negative rating.
- "Favorite" is not used where the action is only a KMK taste rating.

## Part 3: For You Source Status Ordering And Wording

### Current state

`SourceStatusDisplayOrder` already groups source statuses for settings display. User expectation:

1. Sources with matches should appear first.
2. Sources with no matches/no results should appear below sources with matches.
3. Disliked/disabled/avoided sources should appear below no-match sources.

### Required behavior

Verify and preserve this order:

```text
Has matches
No matches / no usable results / filtered out
Disliked or disabled
```

If the current helper does this, add or update tests and documentation rather than rewriting it.

### Required status wording

Where status rows are shown, the wording should distinguish:

- shown with result count,
- no matches,
- results filtered out,
- hidden by duplicate handling,
- source disabled,
- outside attempt limit,
- error,
- not checked.

Do not show empty source rows on the For You page itself. Empty or failed rows belong in settings/status/diagnostics, not the main feed.

### Candidate files

```text
app/src/main/java/exh/recs/SourceStatusDisplayOrder.kt
app/src/main/java/exh/recs/RecommendationSourceRunStatusStore.kt
app/src/main/java/exh/recs/RecommendationStatusAdjuster.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/test/java/exh/recs/SourceStatusDisplayOrderTest.kt
app/src/test/java/exh/recs/AdjustStatusesForDedupeTest.kt
```

### Tests

Add or update pure tests covering:

- has-match group before no-match group,
- no-match group before disliked group,
- priority order preserved inside each group,
- duplicate-hidden status is not treated as a shown row,
- disabled/disliked sources sort last.

## Part 4: Same-Manga Matching Settings Verification And Wording

### Current state

v0.7.8 added settings in `SourcePreferences`:

- `sameMangaMatchResultsPerSource()`
- `sameMangaMatchPreselectResults()`
- `bestVersionPreviewSampleSize()`
- `bestVersionAvoidFirstPages()`

`RecommendationsSettingsScreen` already shows a "Same manga matching" section.

### Required behavior

Verify these settings are actually used by all bounded workflows:

- Love other versions.
- Like other versions.
- Dislike other versions.
- Seen other versions.
- Favorite other versions, if present.
- Best Version candidate search.

Normal global search must remain uncapped.

### Required wording

The settings summary must clearly say:

- the per-source cap only affects bounded same-manga workflows,
- it does not affect normal global search,
- selected-by-default can be turned off if a source returns many wrong matches,
- Best Version sampling avoids first pages because sources may contain ads/covers/credits.

### Candidate files

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt
app/src/main/java/exh/recs/matching/SameMangaMatchSettings.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/test/java/exh/recs/matching/SameMangaMatchSettingsTest.kt
app/src/test/java/exh/recs/matching/CrossExtensionMatchSelectionTest.kt
```

### Acceptance criteria

- Same-manga matching caps and preselect preferences affect bounded workflows only.
- Normal global search remains unchanged.
- If already implemented, tests and docs confirm it.

## Part 5: Loved Manga UX Polish

### Current state

Loved Manga exists and only displays loved manga from currently installed sources as of v0.7.3. Conservative grouping and link group usage exist as of v0.7.2.

### Required behavior

Verify:

- uninstalled-source loved entries are hidden from the visible Loved Manga list,
- hidden entries remain stored,
- reinstalling a source can make those entries visible again,
- grouping uses cross-source link groups as the strongest evidence,
- grouping never uses title-only matching,
- "clear duplicates" or equivalent grouping action has visible feedback when it changes nothing.

### UX improvements in this pass

If low risk, add sorting options:

- Most recently loved.
- Title A-Z.
- Source.
- Oldest first.

If sorting options would create too much churn, document them as still deferred and only improve labels/empty states.

### Candidate files

```text
app/src/main/java/exh/recs/loved/LovedMangaScreen.kt
app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt
app/src/main/java/exh/recs/loved/LovedMangaDuplicateGrouper.kt
app/src/main/java/exh/recs/loved/LovedMangaSourceFilter.kt
app/src/test/java/exh/recs/loved/LovedMangaDuplicateGrouperTest.kt
app/src/test/java/exh/recs/loved/LovedMangaSourceFilterTest.kt
```

### Tests

Add or update tests for:

- installed-only filtering,
- uninstalled taste rows preserved but hidden,
- linked entries group together,
- title-only duplicates do not merge,
- title plus author/artist can group,
- title plus highly similar description can group if that behavior exists.

## Part 6: Cache/Staleness And Refresh Wording

### Current state

For You cache already has fingerprints and invalidation behavior, including known-manga settings. Users may still not understand when a For You result is stale.

### Required behavior

Do not rebuild the cache system. Instead:

- verify rating changes, tag changes, seen changes, source preference changes, language changes, hide-known changes, and same-manga setting changes invalidate only what they currently should,
- document current behavior,
- add a compact UI hint only if there is an existing place where it fits naturally.

Possible UI wording:

```text
Refresh For You after changing ratings, tags, source preferences, or known-manga settings.
```

Do not add a large warning card.

### Candidate files

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
app/src/main/java/exh/recs/RecommendationCache.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStore.kt
app/src/main/java/exh/recs/seen/SeenRecommendationMangaStore.kt
```

## Versioning

Update:

```text
app/build.gradle.kts
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Required values:

```text
KmkRecsReleaseNotes.VERSION_CODE = 714
KmkRecsReleaseNotes.VERSION_NAME = "KMK-Recs v0.7.14"
app versionCode = 86 // KMK-Recs v0.7.14
```

The What's New entry should mention only user-facing changes, for example:

- clearer recommendation settings organization,
- clearer source/recommendation status wording,
- same-manga matching settings wording,
- Loved Manga clarity/sorting if implemented.

Do not mention documentation-only changes in What's New.

## Documentation Updates

Create:

```text
docs/recommendations/KMK_RECS_V0_7_14_RECOMMENDATION_UX_FORMATTING_AND_SETTINGS_CONSOLIDATION_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/KMK_RECS_V0_7_12_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_IMPLEMENTATION.md
```

The implementation document must include:

- what Komikku UI/settings patterns were checked,
- exact files changed,
- what wording changed,
- what settings layout changed,
- what was verified as already implemented,
- tests added or updated,
- validation commands run,
- manual QA steps,
- remaining deferred items.

## Validation

Run:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

If SQLDelight is not changed, do not run SQLDelight generation unnecessarily.

If any command cannot be run, document exactly why.

## Manual QA Checklist

Claude should document this checklist in the implementation note:

1. Open Recommendation Settings.
2. Confirm daily recommendation controls appear before advanced Source Evaluation controls.
3. Confirm section headers are not duplicated or confusing.
4. Toggle same-manga results per source and preselect settings.
5. Confirm normal global search still shows uncapped behavior.
6. Open Cross Extension Match from a manga and verify cap/preselect behavior.
7. Open Best Version and verify same-manga cap and preselect behavior.
8. Open Source Evaluation and check source/recommendation quality labels.
9. Open Loved Manga and verify installed-only display.
10. If sorting was added, verify each sort option.

## Explicit Non-Goals

Do not implement in v0.7.14:

- OCR changes.
- chapter image quality algorithm changes.
- source evaluation probing changes beyond display wording.
- new source evaluation database schema.
- automatic migration/favorite/rating behavior.
- cross-source link group management UI unless it is already almost complete and trivial to expose.
- backup/restore changes.
- iOS work.


