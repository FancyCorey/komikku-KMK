# KMK-Recs v0.7.42-fix2 Source Evidence Display, Sorting, and State Follow-Up Implementation

Date: 2026-07-12

Status: COMPLETE and verified.

Version family: KMK-Recs `v0.7.42-fix2` â€” a **corrective follow-up to v0.7.42** (second follow-up,
after fix1), not a new feature phase. Per `RECOMMENDATION_VERSIONING.md`'s Canonical Versioning Rule:
`KmkRecsReleaseNotes.VERSION_CODE = 744`, `VERSION_NAME = "KMK-Recs v0.7.42-fix2"`, APK
`Komikku-v1.13.6-kmk.7.42.2-debug.apk`.

Plan: `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_PLAN.md`.

## Background

v0.7.42-fix1 unified automatic evaluation, the manual queue, diagnostics, and fit staleness. A code
review after fix1 found the remaining presentation/action/sort paths still carried old assumptions.
This release fixes those without inventing a third score â€” it only makes every displayed state and
user action faithfully use the two evidence layers that already exist (catalogue fit and For You
search compatibility).

## Findings confirmed

All findings named in the plan were re-verified against the actual code at the start of this session
before editing â€” none were already fixed.

1. **`SortMode.SEARCH_RELIABILITY` sorted a retired field.** Confirmed: `SourceEvaluationResultList
   .SortMode` had a `SEARCH_RELIABILITY` case whose comparator read `SourceEvaluation
   .searchReliabilityScore`, which `SourceEvaluationScorer` (as of the base v0.7.42 release) always
   writes as `0.0`.
2. **`BEST_FIT` used the same retired field as a tie-breaker.** Confirmed: `bestFitComparator` had
   `val search = b.searchReliabilityScore.compareTo(a.searchReliabilityScore)` between the catalogue
   quality-score and newest-evaluation tie-breakers.
3. **Row labels used a separate hardcoded Strong Fit/Worth Trying test.** Confirmed:
   `EvaluationResultRow` computed `val isPromising = evaluation.verdict == SourceEvaluationVerdict
   .STRONG_FIT || evaluation.verdict == SourceEvaluationVerdict.WORTH_TRYING`, independent of
   `SourceRecommendationFitEligibility` (fix1 wired the queue/diagnostics to that eligibility contract,
   but not this row-level check).
4. **Rows could display a stale fit as a current result.** Confirmed: `recQualityLabel` was
   `if (recFit != null) { ...verdict label... } else if (isPromising) { ...not-checked... }` â€” this
   never called `isFitCurrent`, so an older-version or expired `recFit` rendered its old verdict
   (e.g. "For You search: Great") as if it were current.
5. **The action area could not target only stale fits.** Confirmed: `evaluateRecommendationQualityForPromising
   (reCheckAll: Boolean)` only had two target sets â€” `queue.missingPromising` (which, before this
   release, already conflated "no fit" and "stale fit" into one bucket per fix1) and
   `queue.missingPromising + queue.checkedPromising`. There was no way to check only outdated rows,
   and â€” because fix1's two-bucket queue put outdated rows in `missingPromising` â€” "Re-check all"
   (`missing + checked`) *did* already include outdated rows in fix1's data model, but the plan's
   requirement (verified against the current three-concept model: missing / outdated / checked) was to
   make this an explicit, named target rather than an accidental consequence of a conflated bucket.
6. **`CURRENT_STATE.md` retained pre-v0.7.42 eligibility wording without a historical qualifier.**
   Confirmed: the "Recommendation Fit Helpers" section (originally written for v0.7.4) stated
   `SourceRecommendationFitEligibility` "Checks verdict (STRONG_FIT or WORTH_TRYING only)" with no
   note that this was superseded by the v0.7.42 confidence-based fail-open rule. The v0.6.15 "Results
   stability" bullet also listed "Search reliability" as one of six sort modes with no note that it
   was later retired.

## What changed

### New shared policy â€” `SourceRecommendationFitDisplayPolicy`

New file `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitDisplayPolicy.kt`. Pure,
Android-free, stateless. Resolves `(SourceEvaluation, SourceRecommendationFit?, now)` to exactly one
`CompatibilityDisplayState`:

```kotlin
enum class CompatibilityDisplayState {
    INELIGIBLE, NOT_CHECKED, OUTDATED, GREAT, GOOD, MIXED, WEAK, NO_MATCHES, ERROR,
    val isCurrentOutcome: Boolean // true for GREAT/GOOD/MIXED/WEAK/NO_MATCHES/ERROR
}

object SourceRecommendationFitDisplayPolicy {
    fun resolve(evaluation, fit, now): CompatibilityDisplayState
    fun compatibilityRank(state): Int // 0 = GREAT/GOOD/MIXED â€¦ 6 = INELIGIBLE
}
```

`resolve()` calls only `SourceRecommendationFitEligibility.isProbeEligible()` and `.isFitCurrent()` â€”
it does not re-derive verdict, confidence, version, or expiry logic. `compatibilityRank()` encodes the
plan's exact seven-bucket order (Â§9): current GREAT/GOOD/MIXED (rank 0) â†’ WEAK (1) â†’ NO_MATCHES (2) â†’
ERROR (3) â†’ OUTDATED (4) â†’ NOT_CHECKED (5) â†’ INELIGIBLE (6).

### Queue â€” fourth bucket, targeted actions

`SourceRecommendationQualityQueue.QueueResult` gained `outdatedPromising: List<SourceEvaluation>` and
`outdatedCount`/`needsCheckPromising` derived properties. `compute()` now classifies each eligible
evaluation via `SourceRecommendationFitDisplayPolicy.resolve()` into exactly one of
missing/outdated/checked â€” the four buckets (missing, outdated, checked, ineligible) are mutually
exclusive and collectively exhaustive (verified by test â€” see below).

`SourceEvaluationScreenModel`:
- `evaluateRecommendationQualityForPromising(reCheckAll: Boolean)`: `false` targets
  `queue.missingPromising` only (unchanged intent, now correctly excludes outdated rows since they're
  no longer folded into "missing"); `true` now targets
  `missingPromising + outdatedPromising + checkedPromising` (previously `missing + checked` only â€”
  under the old two-bucket model this happened to include what are now "outdated" rows, but the
  three-way split makes the "all eligible rows" semantics explicit and correct going forward).
- New `recheckOutdatedRecommendationQuality()`: targets `queue.outdatedPromising` only.
- Both delegate to a new private `runRecQualityCheck(targets: List<SourceEvaluation>)` that guards
  concurrency (`recQualityRunning`) and empty targets (no coroutine started, no state mutation, no
  false "success") in one place instead of duplicating the guard per action.

### UI â€” separate counts, targeted button, truthful row labels

`SourceEvaluationScreen.kt`:
- One `now = remember(state.evaluations, state.recommendationFitsByEvalKey) { System
  .currentTimeMillis() }` is captured once per composition and threaded into the queue computation,
  the sort call, and every row â€” so they cannot disagree about which fits are current within one
  render pass.
- The "For You Search Compatibility" section now shows missing and outdated counts separately (two
  `Text` elements, each conditional on `> 0`), and three conditional buttons: **Check search
  compatibility** (missing only), **Recheck outdated** (outdated only, new), **Re-check all** (only
  when `checkedPromising.isNotEmpty()`, unchanged condition).
- `EvaluationResultRow` resolves `compatState = SourceRecommendationFitDisplayPolicy.resolve(evaluation,
  recFit, now)` once and switches on it: `NOT_CHECKED` â†’ "For You search: Not checked"; `OUTDATED` â†’
  "For You search: Outdated - recheck" (new); `GREAT`/`GOOD`/`MIXED`/`WEAK`/`NO_MATCHES`/`ERROR` â†’ the
  existing localized outcome label; `INELIGIBLE` â†’ no compatibility label at all.
- The error-kind badge (v0.7.31) is now gated on `compatState == CompatibilityDisplayState.ERROR`
  instead of a bare `recFit != null && recFit.verdict == ERROR` check â€” a stale ERROR fit displays as
  Outdated and no longer also shows error-kind detail.
- The error/reason detail text (v0.7.12/v0.7.13) is now gated on `compatState.isCurrentOutcome` â€” a
  stale fit's old reason text is no longer shown as if it described a current result.

### Sorting â€” retired Search Reliability, added For You Compatibility, fixed Best Fit

`SourceEvaluationResultList.kt`:
- `SortMode.SEARCH_RELIABILITY` removed; `SortMode.FOR_YOU_COMPATIBILITY` added.
- `sort()` signature changed to `sort(evaluations, fitsByEvalKey, mode, now = System
  .currentTimeMillis())` (matches the plan's suggested signature exactly).
- `bestFitComparator` (now a function taking `fitsByEvalKey`/`now`) no longer reads
  `searchReliabilityScore`. Its former "search" tie-break step is replaced with a compatibility
  tie-break that only compares two rows when **both** resolve to `isCurrentOutcome == true`; if either
  is outdated/not-checked/ineligible, the step is a neutral tie (`0`) and falls through to the
  newest-evaluation/name tie-breakers â€” exactly the plan's "current better outcomes outrank current
  worse outcomes, while outdated/not-checked/ineligible are neutral ties."
- New `forYouCompatibilityComparator` orders by `compatibilityRank`, then (only within the
  current-outcome bucket) `recommendationQualityScore` descending, then newest evaluation, then
  case-insensitive source/extension name.
- `searchReliabilityComparator` was deleted (no longer reachable from any `SortMode`).

### UI strings

`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`:
- `source_evaluation_sort_search_reliability` ("Search reliability") removed â€” confirmed no remaining
  code reference before removal (`grep -rn` returned only the string declaration itself).
- Added: `source_evaluation_sort_for_you_compatibility` ("For You compatibility"),
  `source_evaluation_rec_quality_outdated` ("Outdated - recheck"),
  `source_evaluation_rec_quality_outdated_count` ("%1$d source(s) have outdated results"),
  `source_evaluation_rec_quality_recheck_outdated` ("Recheck outdated").
- No hardcoded English strings were introduced in `SourceEvaluationScreen.kt` â€” every new visible
  label/count uses `stringResource(KMR.strings...)`.

### Documentation correction (plan Â§11 / finding 6)

`CURRENT_STATE.md`:
- "Recommendation Fit Helpers" (v0.7.4 section) now opens with **"(historical -- superseded by v0.7.42;
  see 'Source Evidence Redesign (v0.7.42)' and its fix1/fix2 sections above for the current rule)"**
  and the `SourceRecommendationFitEligibility` bullet explicitly says the STRONG_FIT/WORTH_TRYING-only
  description "is no longer accurate."
- The v0.6.15 "Results stability" bullet's sort-mode list now has an inline historical note that
  "Search reliability" sorted a field v0.7.42 made permanently `0.0` and was retired by fix2 in favor
  of "For You compatibility" â€” "it is no longer an active or meaningful sort."
- A new "v0.7.42-fix2" subsection under "Source Evidence Redesign (v0.7.42)" documents the current
  authoritative behavior (see below).

## Files changed

Production:
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitDisplayPolicy.kt` (new)
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityDiagnostics.kt` (routed through the
  shared policy for architectural consistency; `Summary` shape and semantics unchanged)
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` (VERSION_CODE 744, VERSION_NAME
  "KMK-Recs v0.7.42-fix2", changelog entry)

Not changed: `SourceRecommendationFitEligibility.kt` (reused as-is, per the plan's explicit
requirement not to duplicate its logic), `domain/.../SourceRecommendationFit.kt` (no schema/model
change needed â€” `evaluationVersion`/`expiresAt` already existed from v0.7.42).

Documentation:
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `RECOMMENDATION_VERSIONING.md`

Tests:
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationFitDisplayPolicyTest.kt` (new â€” 27 tests)
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityQueueTest.kt` (rewritten â€” 20
  tests: original behaviors preserved, but tests that previously asserted a stale fit counts as
  "missing" were updated to assert "outdated" instead, since that is the new, correct classification;
  added bucket-exhaustiveness/mutual-exclusivity and outdated-target-exclusion tests)
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityDiagnosticsTest.kt` (extended â€” 18
  tests: all original assertions still hold since `Summary`'s semantics didn't change; added two tests
  cross-checking diagnostics against the queue's new missing/outdated split, and one for a stale ERROR
  fit not being counted as an outcome)
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationResultListTest.kt` (rewritten â€” 21 tests: all
  `sort()` calls updated to the new 4-argument signature; added tests for the full For You
  compatibility ordering, current-vs-stale ranking, Best Fit's compatibility tie-break only applying
  after equal catalogue evidence, and a searchReliabilityScore-only difference now producing a
  deterministic name-based tie instead of a score-based ordering)

All tests are pure JVM (no network, Compose, Android, database, or installer dependencies) â€” verified
by inspection (only `org.junit.jupiter.api.*` and domain-model imports).

## Deferred / out of scope (per plan Â§4 and Â§14 guardrails)

- No new score was introduced; catalogue fit and search compatibility remain two separate measurements.
- No automatic re-probing on startup, refresh, or score-version change.
- No change to source-evaluation installer behavior, extension evaluation batches, database schema,
  backup, sync, or the For You recommendation pipeline.
- No historical results were removed merely because they are stale â€” `OUTDATED` rows keep their old
  fit record and diagnostic; only their *display* changed.
- The orphaned, already-unused `source_evaluation_sort_recommendation_quality` KMR string (confirmed
  via `grep -rn` to have zero references anywhere in `app/src/main` both before and after this change)
  was left untouched â€” removing it is unrelated to this plan's scope.

## Verification

JDK confirmed before running Gradle: repo-local Temurin **17.0.19+10** at
`C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10`. Java 8 was not used.

One real compile error was caught by `:app:compileDebugKotlin` (not by manual review) during this
session's earlier fix1 work and was already fixed before this session began; no new compile errors were
found this pass beyond two self-introduced lint warnings (`redundant else`, `condition always true`)
that were cleaned up before the final build.

| Command | Result |
|---|---|
| `:app:testDebugUnitTest --tests "*.SourceRecommendationFitDisplayPolicyTest"` | BUILD SUCCESSFUL (27/27 pass) |
| `:app:testDebugUnitTest --tests "*.SourceRecommendationQualityQueueTest"` | BUILD SUCCESSFUL (20/20 pass) |
| `:app:testDebugUnitTest --tests "*.SourceRecommendationQualityDiagnosticsTest"` | BUILD SUCCESSFUL (18/18 pass) |
| `:app:testDebugUnitTest --tests "*.SourceEvaluationResultListTest"` | BUILD SUCCESSFUL (21/21 pass) |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL (267 actionable tasks; full suite passes) |
| `:app:spotlessApply` | BUILD SUCCESSFUL |
| `:app:spotlessCheck` | BUILD SUCCESSFUL |
| `:app:assembleDebug` | BUILD SUCCESSFUL |

`KmkRecsReleaseNotes.kt` was edited after the first successful verification pass (VERSION_CODE 744,
VERSION_NAME "KMK-Recs v0.7.42-fix2", changelog entry) â€” `spotlessApply`/`spotlessCheck` were re-run
afterward since that edit touches production Kotlin; `:app:testDebugUnitTest` was not re-run because
no test references `KmkRecsReleaseNotes` (confirmed via `grep -rln`), so the earlier passing result
remains valid for that file; `:app:assembleDebug` was re-run after the release-notes edit to produce
the handed-off APK.

APK built and copied: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.42.2-debug.apk`
(from `app/build/outputs/apk/debug/app-universal-debug.apk`).

## Release identity (must agree everywhere â€” verified)

| Source | Value |
|---|---|
| `KmkRecsReleaseNotes.VERSION_CODE` | `744` |
| `KmkRecsReleaseNotes.VERSION_NAME` | `"KMK-Recs v0.7.42-fix2"` |
| APK filename | `Komikku-v1.13.6-kmk.7.42.2-debug.apk` |
| This implementation report | `v0.7.42-fix2` |
| `CURRENT_STATE.md` Feature Version | `KMK-Recs v0.7.42-fix2` |
| `NEXT_WORK.md` shipped-version summary | `v0.7.42-fix2` |
| `RECOMMENDATION_VERSIONING.md` | `### KMK-Recs v0.7.42-fix2` entry, APK `Komikku-v1.13.6-kmk.7.42.2-debug.apk`, code `744` |

Android `versionCode`/`versionName` in `app/build.gradle.kts` were **not** modified â€” this is a KMK
local release marker only, per the Canonical Versioning Rule.

