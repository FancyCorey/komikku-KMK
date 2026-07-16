# KMK-Recs v0.7.7 Follow-Up: Recommendation Quality On-Demand Fix Plan

Date: 2026-06-22

Status: active implementation plan. Do not implement until the user explicitly approves and asks Claude Code to proceed.

## Problem

The v0.7.7 `Evaluate recommendations` action for promising evaluated sources is not working correctly.

Observed behavior:

- User opens Source Evaluation.
- User uses the visible `Recommendation Quality` section.
- The app starts evaluating promising sources marked `STRONG_FIT` or `WORTH_TRYING`.
- It immediately loads through all target evaluations.
- Every target becomes `Recommendations: Error`.

Likely current cause:

```text
SourceEvaluationScreenModel.evaluateRecommendationQualityForPromising()
```

appears to look only in:

```text
extensionManager.installedExtensionsFlow.value
```

for the evaluated extension/source. Most promising sources in this workflow are **not currently installed** because Source Evaluation temporarily installed them earlier, evaluated them, then cleaned them up.

When the extension is not currently installed, the current implementation writes an error fit such as:

```text
Extension not installed or source not found
```

That makes the on-demand recommendation-quality feature fail for the main use case: checking non-installed `Strong Fit` / `Worth Trying` sources later.

## Version

This is a follow-up fix to:

```text
KMK-Recs v0.7.7
```

Do not move this to v0.7.8. v0.7.8 is reserved for the separate Best Version / Chapter Quality workflow.

Suggested implementation report:

```text
docs/recommendations/KMK_RECS_V0_7_7_RECOMMENDATION_QUALITY_ON_DEMAND_FIX_IMPLEMENTATION.md
```

If an APK is built, use a patch-style filename under the same feature version family, for example:

```text
Komikku-v1.13.6-kmk.7.7.1-debug.apk
```

or, if the project only supports one patch digit:

```text
Komikku-v1.13.6-kmk.7.7-debug.apk
```

and document that it is a rebuilt v0.7.7 follow-up APK.

## Required Reading Before Coding

Claude must read:

```text
docs/recommendations/README.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/KMK_RECS_V0_7_7_SOURCE_EVALUATION_FOLLOWUP_AND_BEST_VERSION_ADDENDUM.md
docs/recommendations/KMK_RECS_V0_7_7_SOURCE_EVALUATION_FOLLOWUP_AND_BEST_VERSION_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_7_7_RECOMMENDATION_QUALITY_ON_DEMAND_FIX_PLAN.md
RECOMMENDATION_VERSIONING.md
```

Claude must inspect:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationInstallerPolicy.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationCleanupPolicy.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitScorer.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationDisplayFilter.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt
domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt
domain/src/main/java/tachiyomi/domain/taste/model/SourceRecommendationFit.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/GetSourceRecommendationFit.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertSourceRecommendationFit.kt
data/src/main/java/tachiyomi/data/taste/SourceRecommendationFitRepositoryImpl.kt
```

## Desired Behavior

The `Evaluate recommendations` action should work for both:

1. promising sources whose extensions are currently installed;
2. promising sources whose extensions are **not currently installed**, but exist in available extension metadata and can be temporarily installed.

Correct flow:

```text
Promising evaluation selected
-> If extension/source currently installed, run bounded probe directly
-> If extension/source not installed, temporarily install it using Source Evaluation's safe/private install flow
-> Load the matching source from the installed extension
-> Run SourceRecommendationFitProbe
-> Persist SourceRecommendationFit
-> Clean up temporary extension using SourceEvaluationCleanupPolicy
-> Move to next target
```

Only mark a source as `Recommendations: Error` if a real step fails:

- extension not found in available metadata;
- install fails;
- installed extension does not expose expected source;
- probe fails unexpectedly;
- cleanup failure if severe enough to report;
- network/source error during probe.

Do not mark every non-installed promising source as error merely because it is not installed at the moment the button is pressed.

## Non-Goals

Do not implement:

- Best Version / Chapter Quality workflow;
- v0.7.8 work;
- image/page preview;
- new recommendation scoring model;
- source priority changes;
- global search changes;
- broad UI redesign;
- new extension repository logic;
- automatic checking of all non-promising sources.

## Part 1: Extract Reusable Recommendation-Quality Probe Runner

The current on-demand implementation likely duplicates too much logic in `SourceEvaluationScreenModel`.

Create a focused helper if practical:

```text
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityEvaluator.kt
```

Responsibilities:

- given a `SourceEvaluation`, run recommendation-quality probing;
- use already-installed source if available;
- otherwise temporarily install extension/source if possible;
- run `SourceRecommendationFitProbe`;
- map outcome to `SourceRecommendationFit`;
- persist via `UpsertSourceRecommendationFit`;
- clean up temporary install safely;
- return a typed result for UI/progress.

Suggested result:

```kotlin
sealed interface RecommendationQualityEvaluationResult {
    data class Success(val fit: SourceRecommendationFit) : RecommendationQualityEvaluationResult
    data class Error(val evaluationKey: String, val message: String) : RecommendationQualityEvaluationResult
    data class Skipped(val evaluationKey: String, val reason: String) : RecommendationQualityEvaluationResult
}
```

If a separate helper is too much, keep logic in `SourceEvaluationScreenModel`, but still structure it into small private functions:

```kotlin
findInstalledSourceForEvaluation(...)
findAvailableExtensionForEvaluation(...)
temporarilyInstallForRecommendationQuality(...)
runRecommendationQualityProbe(...)
cleanupTemporaryExtension(...)
```

## Part 2: Finding The Extension To Reinstall

The evaluation row stores metadata such as:

- `evaluationKey`
- `sourceId`
- `extensionPkgName`
- `signatureHash`
- `extensionName`
- `sourceName`
- `sourceLang`
- version metadata if present

Use that metadata to locate the available extension.

Recommended matching order:

1. exact `signatureHash + extensionPkgName`;
2. exact `extensionPkgName`;
3. `signatureHash + extensionName`;
4. fallback by `extensionName + sourceName + sourceLang` only if unambiguous.

Do not guess if multiple available extensions match ambiguously. Return a safe error such as:

```text
Extension match ambiguous
```

## Part 3: Temporary Install Strategy

Use the same install strategy principles as Source Evaluation.

Preferred:

- use Private installer path when available;
- do not force Shizuku;
- do not show Shizuku-not-installed prompts unless user selected Shizuku;
- do not change global installer preferences unexpectedly.

The on-demand recommendation-quality action should behave like Source Evaluation:

- temporary install;
- probe;
- cleanup.

Avoid Android uninstall prompts when possible by using private install cleanup.

If the extension was already installed before the action:

- do not uninstall it afterward.

If the extension was temporarily installed by the action:

- clean it up afterward using `SourceEvaluationCleanupPolicy`.

## Part 4: Source Loading

After install:

1. wait until `ExtensionManager.installedExtensionsFlow` exposes the installed extension;
2. find a `CatalogueSource` matching the evaluation.

Preferred matching:

```text
source.id == evaluation.sourceId
```

Fallback:

```text
source.name == evaluation.sourceName && source.lang == evaluation.sourceLang
```

Only fallback if unambiguous.

If no matching source is found:

```text
Source not found in installed extension
```

and clean up if temporarily installed.

## Part 5: Running The Probe

Use the existing bounded components:

```text
SourceRecommendationFitProbe
SourceRecommendationFitScorer
SourceRecommendationFitProbeOutcome
RecommendationQualityVerdict
UpsertSourceRecommendationFit
```

Do not broaden probe scope.

Probe must remain:

- page 1 only;
- bounded query count;
- no chapter list fetch;
- no page/image fetch;
- timeout-safe;
- error-isolating per source.

## Part 6: UI / Progress Behavior

Current UI has:

- `Recommendation Quality` section;
- missing promising count;
- `Evaluate recommendations`;
- `Re-check all`.

Keep that UI, but make progress/error clearer.

During on-demand run:

- show progress `N / total`;
- optionally show current source/extension name;
- disable duplicate start buttons while running;
- keep Cancel if feasible, or allow screen back to cancel if current architecture supports it.

After run:

- refresh `recommendationFitsByEvalKey`;
- missing count should decrease for successes;
- errors should show as real error results, but not mass-error due to missing install.

## Part 7: Error Handling

Handle safely:

- available extension metadata missing;
- ambiguous extension match;
- install failure;
- installed extension never appears;
- source ID not found;
- source name/lang ambiguous;
- probe throws;
- network/source errors;
- cleanup failure;
- app cancellation;
- user navigates away.

One failing source must not stop the entire batch unless cancellation occurs.

Each failure should write a meaningful `SourceRecommendationFit` error only for that evaluation.

Suggested error messages:

```text
Extension not found in available sources
Extension match ambiguous
Install failed
Installed extension did not load
Source not found in installed extension
Probe failed
Cleanup failed
```

## Part 8: Adjacent Implemented-Code Audit

While investigating the on-demand recommendation-quality bug, two adjacent risks were found in implemented code. Claude must verify whether these risks still exist in the current working tree before coding, then fix them if they are still present.

These are not old planning-only concerns. They were observed in implemented files.

### 8.1 Source Evaluation installed-key snapshot

Implemented code currently appears to load `installedExtensionKeys` once in:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

around the v0.7.6 initialization block:

```text
extensionManager.installedExtensionsFlow.value.map { "${it.signatureHash}|${it.pkgName}" }
```

Risk:

- `SourceEvaluationDisplayFilter` uses `installedExtensionKeys` to hide installed sources.
- If the user installs or uninstalls extensions while the Source Evaluation screen/model is alive, the hidden-installed state can become stale.
- This can make installed sources remain visible in past evaluations, or make newly uninstalled sources remain hidden until the screen is recreated.
- The on-demand recommendation-quality fix will temporarily install and clean up extensions, so stale installed-key state can also make the UI misleading during or after this workflow.

Required behavior:

- `installedExtensionKeys` should be kept in sync with `extensionManager.installedExtensionsFlow`.
- When installed extensions change, update `State.installedExtensionKeys` and re-run `applyDisplayFilter()`.
- Avoid infinite update loops or excessive recomposition.
- If a temporary install happens during recommendation-quality probing, the UI should settle back to the correct hidden-installed state after cleanup.

Suggested implementation:

```kotlin
extensionManager.installedExtensionsFlow
    .onEach { installed ->
        val keys = installed.map { "${it.signatureHash}|${it.pkgName}" }.toSet()
        mutableState.update { it.copy(installedExtensionKeys = keys) }
        applyDisplayFilter()
    }
    .launchIn(screenModelScope)
```

Adjust this pattern to match project style. If `applyDisplayFilter()` reads state immediately after `mutableState.update`, avoid race/stale reads by either passing the computed keys into the filter helper or updating state in one block.

Tests:

- Add or extend pure tests for `SourceEvaluationDisplayFilter` if useful.
- Add a screen-model/helper-level test if practical to confirm installed-key changes immediately affect filtered evaluations.
- Manual QA: open Source Evaluation, show/hide installed rows, install one suggested source, return without recreating the screen, confirm the installed row can be hidden immediately.

### 8.2 Recommendation Settings visible-source snapshot

Implemented code currently appears to capture visible catalogue sources once in:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
```

with:

```text
private val visibleSources = sourceManager.getVisibleCatalogueSources()
```

Risk:

- Source Priority / For You settings can become stale after installing or uninstalling extensions while the settings screen/model is alive.
- The user may install a suggested extension, return to Recommendation Settings, and not see source priority/language/source-order state reflect the current installed sources until the screen is recreated.
- This is related to the same class of issue: implemented code using one-time source/extension snapshots where the UI expects current extension state.

Required behavior:

- Claude must verify whether current settings state already refreshes after extension install/uninstall.
- If it does not, make the source list refresh when installed extensions/source manager data changes or when the screen resumes, using the least invasive pattern that fits Komikku's architecture.
- Recompute:
  - `orderedSources`
  - `availableLanguages`
  - `boostedSourceIds`
  - source-order preservation
  - disabled-source handling
- Do not break manual source priority order.
- Do not reset user ordering just because a new source appears.

Suggested approach:

- Prefer a small `refreshVisibleSources()` / `recomputeSourcesForLanguages(...)` path that reads current `sourceManager.getVisibleCatalogueSources()` when needed instead of relying on a constructor-time `val`.
- If there is an existing source/extension flow suitable for observing installed extension changes, use it to trigger refresh.
- Preserve existing stored order with `RecommendationSourceOrdering.applyAll(...)`.
- Newly installed sources should be inserted according to existing ordering behavior, not at random.

Tests/manual QA:

- Install a suggested extension from Recommendation Settings.
- Confirm it disappears from Sources To Try.
- Confirm its installed sources appear in source priority without requiring app restart.
- Uninstall an extension and confirm it no longer appears in source priority after refresh.
- Confirm manual source ordering is preserved.

### 8.3 Do not over-broaden this audit

Do not redesign Settings or Source Evaluation broadly.

Only fix stale installed/source snapshots that are directly connected to this v0.7.7 workflow:

- Source Evaluation installed-row display/filtering.
- Recommendation Settings installed-source/source-priority freshness.
- On-demand recommendation-quality installed/non-installed probing.

Do not implement unrelated v0.7.8 Best Version work here.
## Part 9: Tests

Add focused tests.

### Pure Matching Helper Tests

If helper exists:

```text
SourceRecommendationQualityExtensionResolverTest
```

Cases:

- exact signature + pkg match;
- pkg match fallback;
- source name/lang fallback;
- ambiguous match returns error;
- no match returns error.

### Queue / Target Tests

Extend:

```text
SourceRecommendationQualityQueueTest
```

Cases:

- missing promising rows are targets;
- already checked rows excluded unless re-check all;
- non-promising rows excluded.

### Evaluator Tests

If practical:

```text
SourceRecommendationQualityEvaluatorTest
```

Cases:

- installed source path runs probe without install;
- non-installed available extension path installs then probes;
- pre-existing installed extension is not cleaned up;
- temporary installed extension is cleaned up;
- no available extension writes meaningful error;
- installed extension without source writes meaningful error;
- one failure does not stop next target.

If full evaluator tests are too hard due Android extension dependencies, add pure tests for resolvers/policies and document manual testing for install/probe integration.

### Existing Tests To Run

```text
./gradlew :app:testDebugUnitTest --tests "*SourceRecommendationQuality*"
./gradlew :app:testDebugUnitTest --tests "*SourceEvaluation*"
./gradlew :app:testDebugUnitTest --tests "*SourceRecommendationFit*"
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Document any skipped tests and why.

## Part 10: Manual QA

Manual tablet checks:

1. Have non-installed evaluations with `STRONG_FIT` / `WORTH_TRYING`.
2. Open Source Evaluation.
3. Confirm `Recommendation Quality` section shows missing promising count.
4. Press `Evaluate recommendations`.
5. Confirm it does not immediately mark all rows as error.
6. Confirm extension installs temporarily when needed.
7. Confirm the source is probed.
8. Confirm results become `Great`, `Good`, `Mixed`, `Weak`, `No matches`, or real `Error`.
9. Confirm temporary extension is cleaned up afterward.
10. Confirm installed extensions are not uninstalled.
11. Confirm one failing source does not stop the rest.
12. Confirm `Re-check all` works for already-checked promising sources.
13. Confirm Source Evaluation hide/show installed rows updates after an extension is installed or uninstalled without restarting the app.
14. Confirm Recommendation Settings source priority reflects newly installed/uninstalled sources without losing manual ordering.

## Documentation After Implementation

Claude must create:

```text
docs/recommendations/KMK_RECS_V0_7_7_RECOMMENDATION_QUALITY_ON_DEMAND_FIX_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Implementation report must include:

- root cause;
- adjacent stale-state audit results;
- files changed;
- whether a helper/evaluator was added;
- temporary install behavior;
- cleanup behavior;
- installed/source state refresh behavior;
- error handling;
- tests added;
- tests run;
- APK name/path if built;
- known risks.

## What's New

If release notes are updated, keep them user-facing:

- Recommendation Quality checks now work for promising sources that are not currently installed.
- The app temporarily loads a promising source when needed, checks recommendation quality, then cleans it up.
- Errors now reflect real install/source/probe failures instead of marking every non-installed source as failed.

Do not mention documentation cleanup or internal helper names in What's New.

## Summary

The v0.7.7 on-demand recommendation-quality UI is correct in concept, but the implementation currently assumes promising sources are already installed. Since Source Evaluation is specifically about non-installed sources, that assumption breaks the main workflow.

Fix the action so it can temporarily install/load promising sources, run the bounded recommendation probe, persist the real result, and clean up safely.

