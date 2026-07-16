# KMK-Recs v0.7.10 Recommendation Quality Strong-Fit Error Fix Plan

Date: 2026-06-23

Status: planning; pending user approval before implementation.

## Goal

Fix the Source Evaluation screen's **Recommendation Quality** action for sources that are already classified as `STRONG_FIT` or `WORTH_TRYING`.

The user is referring specifically to:

```text
Recommendation Settings
-> Source Evaluation
-> Recommendation Quality
-> Evaluate recommendations / Re-check all
```

This is not the normal source-evaluation batch. Normal source evaluation answers:

```text
Does this source contain manga that seems to fit my taste?
```

The Recommendation Quality check answers:

```text
For sources already marked Strong Fit / Worth Trying, does this source produce good recommendation-like results?
```

Current observed bug:

- User starts recommendation-quality evaluation for promising sources.
- It appears to load the evaluations.
- Then the relevant rows become `ERROR`.
- It does not appear to temporarily install, probe, and clean up the sources in the same reliable way as normal source evaluation.

## Scope

This plan fixes only the second-stage recommendation-quality action for promising source evaluations.

Do not change:

- normal Source Evaluation scoring;
- normal For You recommendations;
- Best Version / chapter quality flow;
- Loved Manga;
- JSON export/import;
- normal global search;
- source priority drag ordering;
- extension repository logic;
- source explicit/hentai classifier.

## Current Code Findings

### Main Files

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityExtensionResolver.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityExtensionResolverTest.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityQueueTest.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationFitProbeTest.kt
docs/recommendations/CURRENT_STATE.md
docs/recommendations/KMK_RECS_V0_7_7_RECOMMENDATION_QUALITY_ON_DEMAND_FIX_IMPLEMENTATION.md
```

### Current On-Demand Action

`SourceEvaluationScreenModel.evaluateRecommendationQualityForPromising(reCheckAll: Boolean)`:

1. Computes queue with `SourceRecommendationQualityQueue.compute(...)`.
2. Targets missing promising rows, or missing + checked promising rows when `reCheckAll = true`.
3. Loads the taste profile.
4. Builds a `SourceRecommendationFitProbe`.
5. Resolves non-installed extensions using only:

```kotlin
val availableExtensions = lastCandidatePool.value?.allEligible?.map { it.extension }
    ?: emptyList()
```

6. Calls:

```kotlin
evaluateOneForRecQuality(evaluation, probe, tasteProfile, availableExtensions, installerOverride)
```

### Most Likely Bug

The on-demand recommendation-quality action depends on `lastCandidatePool.value?.allEligible` as its available-extension source.

That pool can be:

- null if the current screen session has not loaded candidates yet;
- stale if repository/language/settings changed;
- filtered by current source-evaluation options;
- missing sources that exist in the available extension repository but are not in the current candidate pool;
- empty after filters such as installed-hidden, evaluated-hidden, explicit-hidden, unsafe-hidden, language changes, or state timing.

When `availableExtensions` is empty or incomplete, every non-installed promising source can fail at:

```kotlin
SourceRecommendationQualityExtensionResolver.resolve(evaluation, availableExtensions)
```

and write:

```text
Extension not found in available sources
```

as a `RecommendationQualityVerdict.ERROR`.

This matches the user's report that recommendation evaluation loads and then turns all sources into errors.

### Other Fragile Points

#### Installed Extension Lookup Is Too Strict

Current installed path requires exact `pkgName` and `signatureHash`:

```kotlin
val alreadyInstalled = extensionManager.installedExtensionsFlow.value.find { ext ->
    ext.pkgName == evaluation.extensionPkgName && ext.signatureHash == evaluation.signatureHash
}
```

This can fail if signature metadata differs after repo/signing changes or if source evaluation data came from an older version.

#### Source Lookup Inside Installed Extension Is Too Strict

Current source lookup:

```kotlin
val sources = installedExt.sources.filterIsInstance<CatalogueSource>()
sources.find { it.id == evaluation.sourceId }?.let { return it }
val byName = sources.filter { it.name == evaluation.sourceName }
return if (byName.size == 1) byName.first() else null
```

This can fail if source id changed, source name changed slightly, or multiple sources share a name in different languages.

## Required Behavior

When the user taps **Evaluate recommendations** or **Re-check all** in Source Evaluation:

1. Only `STRONG_FIT` and `WORTH_TRYING` evaluations should be targeted.
2. Already checked rows should only be included for `Re-check all`.
3. Installed promising sources should be probed directly.
4. Non-installed promising sources should be:
   - resolved from a reliable available-extension pool;
   - temporarily installed using the same installer mode policy as source evaluation;
   - waited on until installed sources actually refresh;
   - matched to the intended source;
   - probed with `SourceRecommendationFitProbe`;
   - persisted as `SourceRecommendationFit`;
   - cleaned up afterward if the source was temporarily installed.
5. Errors should remain isolated per source and not cancel the whole run.
6. Error messages should be diagnostic enough to tell which stage failed.
7. The UI should not mark every row as `ERROR` just because the screen-local candidate pool was empty or stale.

## Implementation Plan

## Part 1: Stop Using Only `lastCandidatePool` For Available Extensions

Add a dedicated resolver input builder in `SourceEvaluationScreenModel`.

Recommended function:

```kotlin
private suspend fun loadAvailableExtensionsForRecQuality(): List<Extension.Available>
```

The function should prefer a fresh/full available extension list rather than only `lastCandidatePool`.

Investigate available APIs in this codebase, likely one of:

- `extensionManager.availableExtensionsFlow.value`;
- an extension manager method for available extensions;
- the same extension list source used by `GetSourceEvaluationCandidates`;
- `lastCandidatePool.value?.allEligible` only as fallback if no fuller API exists.

The goal is:

```text
Use the broad available extension repository list,
then let the resolver match the specific evaluation.
```

Do not let current UI filters remove the extension before rec-quality resolution.

Recommended fallback order:

1. Full available extension list from `ExtensionManager` or candidate provider.
2. Freshly rebuilt candidate pool using the current available extensions and unsafe/evaluation data, before option-level filters.
3. `lastCandidatePool.value?.allEligible`.
4. Empty list only if all of the above fail.

If the fallback reaches empty list, write a clear error:

```text
Available extension list unavailable
```

not a misleading source-level probe error.

## Part 2: Rebuild/Refresh Candidate Pool Before On-Demand Checks

Before running `targets`, ensure the screen model has a fresh enough extension/candidate pool.

Inside `evaluateRecommendationQualityForPromising`, replace the current static line:

```kotlin
val availableExtensions = lastCandidatePool.value?.allEligible?.map { it.extension }
    ?: emptyList()
```

with:

```kotlin
val availableExtensions = loadAvailableExtensionsForRecQuality()
```

Do not require the user to leave and reopen Source Evaluation. Do not require running a normal source-evaluation batch first.

## Part 3: Make Installed Extension Matching More Robust

Add or reuse a helper for installed extension matching.

Recommended helper:

```kotlin
private fun findInstalledExtensionForEvaluation(
    evaluation: SourceEvaluation,
    installed: List<Extension.Installed>,
): InstalledExtensionLookupResult
```

Matching order should mirror the available resolver, but adapted for installed extensions:

1. exact `signatureHash + pkgName`;
2. unique `pkgName`;
3. `signatureHash + extensionName`;
4. unique `extensionName + lang`.

Do not guess if multiple candidates match at the same fallback level. Return an ambiguity result and write:

```text
Installed extension match ambiguous: ...
```

If practical, create a pure helper:

```text
SourceRecommendationQualityInstalledResolver
```

with tests.

## Part 4: Make Source Lookup Within Extension More Robust

Replace or strengthen `findSourceInInstalledExt`.

Recommended lookup order:

1. exact source id;
2. exact source name + evaluation lang;
3. exact source name if unambiguous;
4. normalized source name + lang if unambiguous;
5. normalized source name if unambiguous.

Return an explicit result rather than nullable if practical:

```kotlin
sealed interface SourceLookupResult {
    data class Found(val source: CatalogueSource) : SourceLookupResult
    data class Ambiguous(val reason: String) : SourceLookupResult
    data object NotFound : SourceLookupResult
}
```

This will make row errors more useful:

```text
Source not found after install
Source match ambiguous after install: 2 sources named MangaFire
```

If a new pure helper is created, test it.

## Part 5: Preserve Per-Source Error Isolation

Keep the existing source loop pattern:

```kotlin
for ((index, evaluation) in targets.withIndex()) {
    try {
        evaluateOneForRecQuality(...)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        writeRecQualityErrorFit(evaluation, e.message ?: "Probe error")
    }
    mutableState.update { it.copy(recQualityProgress = index + 1) }
}
```

But improve stage-specific errors:

- `Available extension list unavailable`
- `Extension not found in available sources`
- `Extension match ambiguous: ...`
- `Install failed or timed out`
- `Installed extension did not load`
- `Installed extension match ambiguous: ...`
- `Source not found in installed extension`
- `Source match ambiguous in installed extension: ...`
- `Probe returned no taste profile tags`
- `Probe timed out`

Do not collapse all failures into generic `ERROR`.

## Part 6: Ensure Temporary Install/Cleanup Matches Normal Evaluation

The user expects this path to work like normal source evaluation:

```text
install temporarily -> evaluate recommendation quality -> uninstall/cleanup
```

Audit differences between:

```text
SourceEvaluationRunner.evaluateExtension(...)
```

and:

```text
SourceEvaluationScreenModel.evaluateOneForRecQuality(...)
```

Important checks:

- installer override uses `SourceEvaluationInstallerPolicy.effectiveInstallerOverride`;
- Private installer remains preferred when available;
- non-installed extension is not permanently left installed when it was only installed for rec-quality probing;
- pre-existing installed extensions are not uninstalled;
- cleanup still uses `SourceEvaluationCleanupPolicy`.

If needed, track whether the extension was installed before this rec-quality action:

```kotlin
val wasPreExisting = extensionManager.installedExtensionsFlow.value.any { ... }
```

Then only cleanup when `wasPreExisting == false`.

## Part 7: UI Feedback

If practical, improve Recommendation Quality section messaging without adding a large new UI surface:

- show progress as now;
- after run, rows should show the actual recommendation verdict if probe succeeded;
- if failed, show the specific error message;
- if all targets fail at the same early stage, consider a top-level transient screen message:

```text
Recommendation quality check could not access available extensions. Refresh extensions and try again.
```

## Part 8: Tests

### Existing Tests To Run

At minimum:

```text
./gradlew :app:testDebugUnitTest --tests "*SourceRecommendationQuality*"
./gradlew :app:testDebugUnitTest --tests "*SourceRecommendationFit*"
```

Preferred:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

### New / Updated Tests

Add tests for whichever helpers are introduced.

#### Available Resolver / Pool Input

If a pure helper is created:

- uses full available list when candidate pool is empty;
- falls back to candidate pool only when full list unavailable;
- does not apply option-level UI filters that would hide the evaluated extension;
- returns empty only when no source is available.

#### Installed Resolver

Test:

- exact sig+pkg wins;
- unique pkg fallback works;
- pkg ambiguity returns ambiguous;
- sig+name fallback works;
- name+lang fallback works only when unambiguous;
- not found returns not found.

#### Source Lookup

Test:

- exact source id wins;
- source name + language works after id changes;
- exact name ambiguity fails safely;
- normalized name fallback works only when unambiguous;
- wrong language is not selected if another language-specific match exists.

#### Screen Model / Integration Style Tests

If feasible without brittle Injekt setup:

- on-demand rec-quality does not produce `Extension not found in available sources` when a matching available extension exists outside `lastCandidatePool`;
- installed sources are probed without temporary install;
- non-installed sources call temporary install path and cleanup.

If direct ScreenModel tests are too heavy, document why and cover the pure helpers thoroughly.

## Versioning

Recommended version:

```text
KMK-Recs v0.7.10
APK: Komikku-v1.13.6-kmk.7.10-debug.apk
```

Version code should follow the established app convention in `KmkRecsReleaseNotes.kt` and `RECOMMENDATION_VERSIONING.md`. If v0.7.9 used `VERSION_CODE = 790`, choose the next valid higher code that still installs over v0.7.9.

## Documentation Updates Required After Implementation

Claude must create:

```text
docs/recommendations/KMK_RECS_V0_7_10_RECOMMENDATION_QUALITY_STRONG_FIT_ERROR_FIX_IMPLEMENTATION.md
```

Claude must update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/README.md
docs/recommendations/NEXT_WORK.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Implementation report must include:

- date;
- feature version/build label;
- user-approved scope;
- files changed;
- behavior changed;
- tests run;
- APK/build output;
- known limitations;
- follow-up recommendations;
- deviations from this plan.

## Expected Files Changed

Likely:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityExtensionResolver.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityExtensionResolverTest.kt
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
docs/recommendations/CURRENT_STATE.md
docs/recommendations/README.md
docs/recommendations/NEXT_WORK.md
RECOMMENDATION_VERSIONING.md
```

Possible new helpers:

```text
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityInstalledResolver.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualitySourceResolver.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityInstalledResolverTest.kt
app/src/test/java/exh/recs/evaluation/SourceRecommendationQualitySourceResolverTest.kt
```

Avoid editing unrelated app areas.

## Success Criteria

The implementation is successful when:

- Source Evaluation Recommendation Quality no longer turns all Strong Fit/Worth Trying rows into immediate errors because `lastCandidatePool` is empty/stale.
- Non-installed promising sources can be resolved from the full available extension list.
- Temporarily installed sources are probed and cleaned up.
- Installed promising sources are probed directly.
- Source lookup after install tolerates source id/name drift safely without guessing ambiguous matches.
- Errors are specific and per-source.
- Normal Source Evaluation still works unchanged.
- For You still works unchanged.
- At least targeted `SourceRecommendationQuality` / `SourceRecommendationFit` tests pass.
- Debug APK builds and can update over v0.7.9.

