# KMK-Recs v0.7.4 — Source Evaluation Update Reassessment and Recommendation Fit — Implementation Report

Date: 2026-06-21
Version: KMK-Recs v0.7.4
APK: Komikku-v1.13.6-kmk.7.4-debug.apk
Plan: `KMK_RECS_V0_6_21_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_PLAN.md`

---

## Summary

v0.7.4 adds three layers of work:

1. **Documentation audit** — corrected stale deferred labels for Favorite other versions and alternate-title cross-extension matching (both were fully implemented in v0.7.0; docs incorrectly said deferred).
2. **Extension version metadata** — records which extension version was installed at evaluation time so the app can detect when an updated version is available for reassessment.
3. **Pure helpers** — `SourceRecommendationFitEligibility` and `SourceRecommendationFitScorer` for future bounded rec-fit probing; actual probe execution deferred.

---

## Part 1 — Documentation Audit

### Files corrected

**`docs/recommendations/NEXT_WORK.md`**
- `### Future: Favorite mode for cross-extension matching` — corrected to state implemented in v0.7.0 (Phase 3). `CrossExtensionMatchMode.Favorite` is fully wired in `CrossExtensionMatchScreenModel` with add-to-library + link group write.
- `### Staged settings and matching improvements` — corrected to state that alternate-title matching is fully implemented via `CrossExtensionMatchQueryPlanner.buildQueries()`. No remaining deferred work in this section.
- Added `### Source Evaluation update reassessment and recommendation fit` section documenting v0.7.4 scope and deferred items.

---

## Part 2 — Extension Version Metadata

### Migration 51.sqm (NEW)

`data/src/main/sqldelight/tachiyomi/migrations/51.sqm`

Adds three nullable columns to `source_evaluation`:
- `extension_version_name TEXT`
- `extension_version_code INTEGER`
- `extension_apk_name TEXT`

These are nullable so existing rows from pre-v0.7.4 evaluations are preserved unchanged.

### Domain model — `SourceEvaluation.kt` (UPDATED)

`domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt`

Added three nullable fields with `= null` defaults at the end of the data class:
```kotlin
val extensionVersionName: String? = null,
val extensionVersionCode: Long? = null,
val extensionApkName: String? = null,
```

Using named-parameter Kotlin construction, no existing callsites break.

### SQL schema — `source_evaluation.sq` (UPDATED)

`data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`

Added the three columns to:
1. The `CREATE TABLE` definition.
2. The `upsert` INSERT column list and VALUES.
3. The `upsert` ON CONFLICT UPDATE SET clause.

### Repository — `SourceEvaluationRepositoryImpl.kt` (UPDATED)

`data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt`

- Mapper lambda: added three new column parameters and passes them to `SourceEvaluation(...)`.
- `upsert()`: added three new keyword arguments from `evaluation.extensionVersion*` fields.

### Scorer — `SourceEvaluationScorer.kt` (UPDATED)

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt`

Both `score()` and `errorRecord()` gained three optional parameters with `= null` defaults:
```kotlin
extensionVersionName: String? = null,
extensionVersionCode: Long? = null,
extensionApkName: String? = null,
```
Passed through to the returned `SourceEvaluation`.

### Runner — `SourceEvaluationRunner.kt` (UPDATED)

`app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`

Three callsites updated to pass `ext.versionName`, `ext.versionCode`, `ext.apkName` from the `Extension.Available` in scope:
1. `SourceEvaluationScorer.score(...)` in `probeAndScore()`
2. `SourceEvaluationScorer.errorRecord(...)` for per-source probe errors
3. `SourceEvaluationScorer.errorRecord(...)` for per-extension install/load errors in `recordExtensionError()`

### SourceEvaluationUpdatePolicy.kt (NEW)

`app/src/main/java/exh/recs/evaluation/SourceEvaluationUpdatePolicy.kt`

Pure stateless helper. No Android dependencies.

- `UpdateStatus` enum: `UPDATED`, `NOT_UPDATED`, `UPDATE_UNKNOWN`, `NEVER_EVALUATED`
- `EvaluationVersionSnapshot` data class: holds `extensionVersionCode`, `signatureHash`, `pkgName`
- `AvailableExtensionSnapshot` data class: holds `versionCode`, `signatureHash`, `pkgName`
- `fromEvaluation(eval: SourceEvaluation)` — convenience constructor
- `detectUpdateStatus(evaluation, available)` — compares single snapshot pair
- `detectForPool(evaluations, available)` — uses highest stored versionCode among all evals for the same extension key; returns `NEVER_EVALUATED` for empty list, `UPDATE_UNKNOWN` if all stored versionCodes are null

### SourceEvaluationUpdatePolicyTest.kt (NEW)

`app/src/test/java/exh/recs/evaluation/SourceEvaluationUpdatePolicyTest.kt`

9 tests (all passed):
- Same versionCode → NOT_UPDATED
- Higher available versionCode → UPDATED
- Lower available than stored → NOT_UPDATED
- Null stored versionCode → UPDATE_UNKNOWN
- Empty evaluation list → NEVER_EVALUATED
- Single UPDATED evaluation in pool → UPDATED
- Single NOT_UPDATED evaluation in pool → NOT_UPDATED
- Pool uses best (highest) stored versionCode
- Pool with all-null versionCodes → UPDATE_UNKNOWN

### SourceEvaluationOptions (UPDATED)

`app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt`

Added:
```kotlin
val onlyUpdatedEvaluated: Boolean = false,
```

### SourceEvaluationCandidateFilter.kt (UPDATED)

`app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt`

`applyOptions()` gained `onlyUpdatedEvaluated: Boolean = false` parameter. When true:
- Extensions with no evaluation at all are excluded.
- Extensions where `detectForPool()` returns anything other than `UPDATED` are excluded.
- Restricts the run to only extensions where the available version is confirmed newer than the last evaluated version.

### SourceEvaluationScreenModel.kt (UPDATED)

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

- Added `updatedEvaluatedExtensionCount: Int = 0` to `State`.
- `applyOptionsAndUpdateState()`: computes `updatedCount` across all eligible extensions using `SourceEvaluationUpdatePolicy`; stores in state.
- `startReassessUpdated()`: sets `onlyUpdatedEvaluated = true`, `skipAlreadyEvaluated = false`, re-applies options, launches evaluation if candidates exist, then resets options.

### SourceEvaluationScreen.kt (UPDATED)

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

When `state.updatedEvaluatedExtensionCount > 0`, shows:
1. `InfoCard` with `source_evaluation_updated_extensions_notice` ("N evaluated extension(s) have updates...").
2. `OutlinedButton` with `source_evaluation_reassess_updated_button` ("Reassess updated extensions").

Both items are inserted before the existing `start_button` item.

### i18n strings (UPDATED)

`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Added:
- `source_evaluation_updated_extensions_notice` — "%1$d evaluated extension(s) have updates. You can reassess them to refresh their source quality scores."
- `source_evaluation_reassess_updated_button` — "Reassess updated extensions"

---

## Part 3 — Recommendation Fit Pure Helpers

### SourceRecommendationFitEligibility.kt (NEW)

`app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt`

Pure stateless gate for whether a `SourceEvaluation` record is eligible for a bounded rec-fit probe. No Android dependencies.

- `EligibilityResult` enum: `ELIGIBLE`, `INELIGIBLE_VERDICT`, `INSUFFICIENT_EVIDENCE`
- `ELIGIBLE_VERDICTS`: `STRONG_FIT`, `WORTH_TRYING` only
- `MIN_SAMPLE_COUNT = 3`
- `check(evaluation: SourceEvaluation): EligibilityResult`

### SourceRecommendationFitEligibilityTest.kt (NEW)

`app/src/test/java/exh/recs/evaluation/SourceRecommendationFitEligibilityTest.kt`

10 tests (all passed):
- STRONG_FIT with sufficient samples → ELIGIBLE
- WORTH_TRYING with sufficient samples → ELIGIBLE
- REJECTED, ERROR, WEAK, NEUTRAL, EXPLICIT_HEAVY → INELIGIBLE_VERDICT
- STRONG_FIT below MIN_SAMPLE_COUNT → INSUFFICIENT_EVIDENCE
- WORTH_TRYING with zero samples → INSUFFICIENT_EVIDENCE
- STRONG_FIT at exactly MIN_SAMPLE_COUNT → ELIGIBLE

### SourceRecommendationFitScorer.kt (NEW)

`app/src/main/java/exh/recs/evaluation/SourceRecommendationFitScorer.kt`

Pure stateless scorer. Takes an `Outcome` data class and returns a score in `[0.0, 1.0]`.

`Outcome` fields:
- `visibleCandidateCount`, `filteredOutCount`, `blockedTagCandidateCount`
- `matchedGroupCount`, `topPicksContribution`
- `noMatchesCount`, `errorCount`
- `avgCandidateScore: Double`

Scoring logic:
- Positive: visibility score (tiered by visible count), avg score boost, group boost, top-picks boost
- Negative: no-matches penalty, filtered-out penalty, blocked-tag ratio penalty, error penalty
- Result clamped to `[0.0, 1.0]`

Error-only outcome (visibleCount = 0 and errorCount > 0) → returns 0.0 immediately.

### SourceRecommendationFitScorerTest.kt (NEW)

`app/src/test/java/exh/recs/evaluation/SourceRecommendationFitScorerTest.kt`

10 tests (all passed):
- Error-only → 0.0
- Zero visible with no errors → 0.0
- Strong outcome (12 visible, groups, top picks, high avg) → score > 0.7
- Single visible candidate → score in (0, 0.5)
- Many no-matches reduce score
- Blocked tag candidates reduce score
- Matched groups boost score
- Top Picks contribution boosts score
- Score clamped to minimum 0.0
- Score clamped to maximum 1.0

---

## Bug Fix — Pre-existing Test Compilation Error

### GetTasteProfileTest.kt (FIXED)

`app/src/test/java/exh/taste/GetTasteProfileTest.kt`

The anonymous `TasteRepository` stub was missing 7 abstract methods introduced in v0.7.0 (`CrossSourceMangaLink` CRUD methods). Test compilation was failing before v0.7.4. Fixed by adding no-op stub implementations.

---

## Release Notes

KmkRecsReleaseNotes: VERSION_CODE `730 → 740`, VERSION_NAME `KMK-Recs v0.7.3 → KMK-Recs v0.7.4`

---

## What Was NOT Implemented (Deferred)

- **Actual bounded rec-fit probe execution in SourceEvaluationRunner** — the pure helpers exist but wiring the probe into the evaluation loop requires careful integration with the connectivity handling and is not part of this pass.
- **`rec_fit_probe_score` column in source_evaluation table** — depends on probe execution being implemented first.
- **Evidence strings i18n** — `source_evaluation_evidence_*` keys still hardcoded English in SourceEvaluationScreen.
- **`onlyUpdatedEvaluated` does not update the 100-rating reassessment baseline** — intentional; only a full global re-evaluation should update the baseline.

---

## Test Summary

| Test Class | Tests | Status |
|---|---|---|
| SourceEvaluationUpdatePolicyTest | 9 | All PASSED |
| SourceRecommendationFitEligibilityTest | 10 | All PASSED |
| SourceRecommendationFitScorerTest | 10 | All PASSED |
| GetTasteProfileTest (pre-existing fix) | 8 | All PASSED |
| Full suite (`testDebugUnitTest`) | All | BUILD SUCCESSFUL |

---

## Files Changed

| File | Change |
|---|---|
| `data/src/main/sqldelight/tachiyomi/migrations/51.sqm` | NEW |
| `domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt` | +3 nullable fields |
| `data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq` | +3 columns in schema and upsert |
| `data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt` | Updated mapper + upsert |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt` | +3 params to score() and errorRecord() |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt` | Pass ext version fields at 3 callsites |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationUpdatePolicy.kt` | NEW |
| `app/src/test/java/exh/recs/evaluation/SourceEvaluationUpdatePolicyTest.kt` | NEW |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt` | +onlyUpdatedEvaluated option |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt` | +onlyUpdatedEvaluated to applyOptions() |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` | +updatedEvaluatedExtensionCount state, +startReassessUpdated() |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` | Updated extension notice + button |
| `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` | +2 strings |
| `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt` | NEW |
| `app/src/test/java/exh/recs/evaluation/SourceRecommendationFitEligibilityTest.kt` | NEW |
| `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitScorer.kt` | NEW |
| `app/src/test/java/exh/recs/evaluation/SourceRecommendationFitScorerTest.kt` | NEW |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE 740, VERSION_NAME v0.7.4 |
| `app/src/test/java/exh/taste/GetTasteProfileTest.kt` | Fixed missing CrossSourceMangaLink stubs |
| `docs/recommendations/NEXT_WORK.md` | Doc audit: corrected Favorite + alternate-title, added v0.7.4 deferred items |
| `docs/recommendations/KMK_RECS_V0_7_4_..._IMPLEMENTATION.md` | NEW (this file) |
