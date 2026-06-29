# KMK-Recs v0.7.15 Phase 6/7 Cleanup And Komikku Alignment Plan

Date: 2026-06-27

Status: planning document for Claude Code implementation. Do not implement outside this scope.

Target version:

```text
KMK-Recs v0.7.15
```

Expected Android versionCode:

```text
87
```

## Purpose

KMK-Recs v0.7.14 completed the main UI/settings consolidation pass. v0.7.15 should be a narrow cleanup pass that removes remaining rough edges from Phase 6/7 and aligns the KMK recommendation code more closely with current Komikku UI, string, testing, and documentation conventions.

This is not a new feature pass. It should make the existing features feel more polished, better tested, and less obviously bolted on.

## Guiding Principle

When in doubt, follow the official Komikku/Mihon style already present in this repository:

- use existing Compose components and spacing patterns,
- use `KMR`/resource strings for user-facing text,
- keep screen route arguments primitive,
- keep logic in pure helpers where it can be unit-tested,
- avoid broad refactors,
- avoid adding new database schema unless the value is clear and the migration is carefully tested,
- document what was verified instead of rewriting stable code.

## Hard Rules

1. Do not change recommendation scoring.
2. Do not change Source Evaluation probing logic.
3. Do not change OCR.
4. Do not change normal global search behavior.
5. Do not add cross-source link group management UI in this pass unless it is already trivial and low-risk.
6. Do not add backup/restore changes in this pass.
7. Do not remove user preferences or reset user settings.
8. Do not add hardcoded user-facing strings.
9. Do not implement large schema changes unless the plan explicitly allows it as a low-risk optional item.
10. If a leftover item is too broad, document it cleanly in `NEXT_WORK.md` instead of half-implementing it.

## Claude Must Inspect First

Claude must read these files before coding:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/KMK_RECS_V0_7_14_RECOMMENDATION_UX_FORMATTING_AND_SETTINGS_CONSOLIDATION_IMPLEMENTATION.md
docs/community/KMK_PHASE_6_7_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_PLAN.md
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
app/src/main/java/exh/recs/loved/LovedMangaScreen.kt
app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt
app/src/test/java/exh/recs/loved/LovedMangaDuplicateGrouperTest.kt
app/src/test/java/exh/recs/loved/LovedMangaSourceFilterTest.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt
app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
```

Claude must also inspect nearby official Komikku UI patterns before changing UI:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/more/settings/
app/src/main/java/eu/kanade/presentation/more/settings/
app/src/main/java/eu/kanade/presentation/components/
```

## Scope Summary

v0.7.15 should cover:

1. Source Evaluation string cleanup.
2. Loved Manga sort tests.
3. Loved Manga duplicate-toggle feedback polish.
4. Seen/read behavior verification and documentation.
5. Best Version cancel/fullscreen state verification.
6. Source quality signal decision cleanup.
7. Documentation and versioning cleanup.

## Part 1: Source Evaluation String Cleanup

### Current State

`SourceEvaluationScreen.kt` still has hardcoded user-facing text in:

```text
evidenceStrengthLabel(evaluation)
lastEvaluatedLabel(evaluatedAt)
VerdictBadge(verdict)
```

v0.7.14 deferred this because `evidenceStrengthLabel()` and `lastEvaluatedLabel()` are plain helper functions, not composables. Some KMR keys already exist for evidence strength labels:

```text
source_evaluation_evidence_strong
source_evaluation_evidence_moderate
source_evaluation_evidence_weak
source_evaluation_evidence_low_confidence
```

### Required Implementation

Refactor only enough to remove hardcoded user-facing strings safely.

Preferred approach:

1. Replace `evidenceStrengthLabel(evaluation): String` with a pure classifier returning an enum or sealed value, for example:

```kotlin
private enum class EvidenceStrength {
    STRONG,
    MODERATE,
    WEAK,
    LOW_CONFIDENCE,
}
```

2. Add pure helper:

```kotlin
private fun evidenceStrength(evaluation: SourceEvaluation): EvidenceStrength
```

3. In `EvaluationResultRow`, map the enum to KMR strings using `stringResource(...)`.

4. Replace `lastEvaluatedLabel(evaluatedAt): String` with either:

- a small value object:

```kotlin
private data class LastEvaluatedDisplay(val daysAgo: Int)
```

or

- compute `days` in the composable and use KMR strings:

```text
source_evaluation_last_evaluated_today
source_evaluation_last_evaluated_days
```

5. Refactor `VerdictBadge` to use KMR strings instead of hardcoded label strings.

Add missing KMR strings if needed:

```xml
source_evaluation_last_evaluated_today
source_evaluation_last_evaluated_days
source_evaluation_verdict_strong_fit
source_evaluation_verdict_worth_trying
source_evaluation_verdict_neutral
source_evaluation_verdict_weak
source_evaluation_verdict_poor_search
source_evaluation_verdict_explicit
source_evaluation_verdict_ecchi
source_evaluation_verdict_rejected
source_evaluation_verdict_error
source_evaluation_verdict_review
```

### Komikku Alignment

- Keep display logic close to the composable that displays text, matching common Compose/i18n style.
- Keep classification logic pure and testable.
- Do not pass `Context` into random helpers just to resolve strings.
- Do not hardcode English strings in new UI code.

### Tests

If practical, add tests for the pure evidence classifier:

- strong signals + good search + good sample => strong,
- at least one strong signal plus search/sample => moderate,
- sample count >= 2 => weak,
- otherwise low confidence.

If the helper remains private and difficult to test directly, Claude may extract it to a small internal helper file only if that keeps code cleaner and consistent with the rest of `exh.recs.evaluation`.

## Part 2: Loved Manga Sort Tests

### Current State

v0.7.14 added:

```kotlin
enum class LoveSortMode { RECENT, OLDEST, TITLE_AZ, SOURCE }
private fun sortEntries(entries: List<LovedMangaEntry>, mode: LoveSortMode): List<LovedMangaEntry>
```

The implementation doc says this pure sort is not yet tested.

### Required Implementation

Add tests for each sort mode.

Because `sortEntries` is currently private, Claude has two acceptable options:

1. Extract sorting into an internal pure object/helper, for example:

```kotlin
internal object LovedMangaSorter
```

or

2. Test through `State.Success.displayItems` if that is cleaner and less invasive.

Prefer the option that best matches existing test style in `app/src/test/java/exh/recs/loved/`.

### Required Test Cases

Add or update tests covering:

- `RECENT` preserves load order.
- `OLDEST` reverses load order.
- `TITLE_AZ` sorts using `manga.title` when available.
- `TITLE_AZ` falls back to `taste.title` when manga is null.
- `SOURCE` sorts by `taste.source`.
- Sorting happens before grouping.

### Non-Goal

Do not change Loved Manga grouping logic unless a test reveals a real bug.

## Part 3: Loved Manga Duplicate-Toggle Feedback Polish

### Current State

Loved Manga has a `Group clear duplicates` toggle. If grouping changes nothing, the user may think the control is broken.

### Required Implementation

Add simple, low-risk feedback when grouping is enabled but no duplicates are grouped.

Acceptable approaches:

1. Show a compact text line under the toggle:

```text
No clear duplicates found.
```

only when:

- groupDuplicates is true,
- entries count > 0,
- displayItems count == entries count.

2. Or show a small result count:

```text
Showing N loved manga.
```

and, when grouping applies:

```text
Grouped N versions.
```

Prefer the least noisy version, consistent with Komikku's existing subdued body-small summary text style.

### Required Strings

Add KMR strings rather than hardcoding:

```xml
loved_manga_no_clear_duplicates
loved_manga_grouped_versions_summary
```

Only add the second if actually used.

### Non-Goals

- Do not delete duplicate entries.
- Do not merge taste rows.
- Do not change cross-source link groups.
- Do not use title-only duplicate matching.

## Part 4: Seen/Read Behavior Verification

### Current State

Seen/read behavior is documented as:

- seen manga are filtered from For You,
- `Seen other versions` exists,
- seen state is stored in preferences, not backup/restore,
- `Seen other versions` should remain available after marking current manga seen.

### Required Work

Verify current code still matches this:

- `BrowsePersonalRecommendationsScreenModel` filters `SeenMangaKey`.
- manga detail page still shows `Seen other versions` where relevant.
- `CrossExtensionMatchMode.MarkSeen` writes seen keys and cross-source link group.
- `SeenRecommendationMangaStoreTest` covers parse/serialize/add/remove behavior.

If the code already matches, only update docs. Do not invent new persistence in this pass.

### Optional Low-Risk Test

If there is a pure helper around menu visibility, add a test that marking the current manga seen does not hide `Seen other versions`.

If this requires UI instrumentation, do not add it in this pass. Document manual QA instead.

## Part 5: Best Version Cancel And Fullscreen State Verification

### Current State

Docs say v0.7.9 fixed:

- Cancel in migration/copy dialog dismisses the dialog and returns to preview.
- Fullscreen preview supports pinch-to-zoom and pan.

But `NEXT_WORK.md` still includes older deferred items around cancel safety/fullscreen preview. This may now be stale.

### Required Work

Claude must inspect:

```text
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
app/src/test/java/exh/recs/bestversion/BestVersionSelectionPolicyTest.kt
docs/recommendations/KMK_RECS_V0_7_9_BEST_VERSION_SELECTION_AND_FULLSCREEN_FIX_IMPLEMENTATION.md
```

If the fix is implemented:

- update `NEXT_WORK.md` to remove stale deferred text,
- update `CURRENT_STATE.md` if needed,
- add a short verified note to the v0.7.15 implementation doc.

If a gap remains:

- implement only the smallest fix if it is local and low-risk,
- otherwise document the exact remaining bug and leave it deferred.

### Non-Goal

Do not rewrite the Best Version workflow.

## Part 6: Source Quality Signal Decision Cleanup

### Current State

`manga_source_quality_signal` exists and is written after Best Version selection. `NEXT_WORK.md` says:

```text
Store a confirmed_quality = true bit when the user completes a Best Version migration.
```

But the current model is already described as "user-confirmed source quality signals from Best Version comparisons." The table has no `confirmed_quality` column.

### Required Work

Claude must decide, based on code and risk, whether this is:

1. stale documentation only, because every signal is already user-confirmed by workflow design; or
2. a useful explicit schema field worth adding.

Preferred v0.7.15 outcome:

- Treat this as documentation cleanup unless there is a clear code path where non-user-confirmed quality signals are written.
- If every insert comes from explicit user selection, update docs to say an explicit `confirmed_quality` field is unnecessary for now.
- Do not add a migration just to store a constant `true` for all rows.

Only add `confirmed_quality` if Claude finds multiple signal origins where future non-confirmed signals will be mixed with confirmed signals. If adding it:

- add SQLDelight migration,
- update domain model and mapper,
- update insert query,
- add tests or compile validation,
- document why the schema change was needed.

## Part 7: Documentation And Versioning

### Versioning

Update:

```text
app/build.gradle.kts
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Required values:

```text
KmkRecsReleaseNotes.VERSION_CODE = 715
KmkRecsReleaseNotes.VERSION_NAME = "KMK-Recs v0.7.15"
app versionCode = 87 // KMK-Recs v0.7.15
```

What's New must mention only user-facing changes. Good examples:

- cleaner Source Evaluation labels,
- Loved Manga duplicate feedback,
- Loved Manga sorting test/quality should not be in What's New unless user-facing behavior changed.

Do not mention tests or documentation-only cleanup in What's New.

### Documentation

Create:

```text
docs/recommendations/KMK_RECS_V0_7_15_PHASE_6_7_CLEANUP_AND_KOMIKKU_ALIGNMENT_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md
```

The implementation document must include:

- official Komikku UI/settings patterns checked,
- exact files changed,
- hardcoded strings removed,
- tests added,
- what was verified as already working,
- what was left deferred and why,
- validation commands run,
- manual QA checklist.

## Validation

Run:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

If SQLDelight is changed, also run the appropriate SQLDelight generation/check command used in this project.

If any command cannot be run, document exactly why.

## Manual QA Checklist

Claude should document this checklist:

1. Open Source Evaluation.
2. Confirm evidence labels and last-evaluated labels still display correctly.
3. Confirm verdict badges show correct labels.
4. Open Loved Manga.
5. Toggle Group clear duplicates when duplicates exist.
6. Toggle Group clear duplicates when no clear duplicates exist and confirm feedback is understandable.
7. Try each Loved Manga sort mode.
8. Open a manga marked Seen and confirm Seen other versions remains available where expected.
9. Open Best Version and confirm Cancel returns to preview if this was still relevant.
10. Confirm normal global search is unchanged.

## Expected Final Report From Claude

Claude's final report should include:

- what Komikku patterns it checked,
- what changed in v0.7.15,
- what was verified and not changed,
- whether any SQLDelight/database changes were made,
- test/build results,
- APK path,
- remaining deferred items,
- whether Phase 8 is ready to begin next.

