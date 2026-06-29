# KMK-Recs v0.6.12: Source Evaluation Candidate Pool And Shizuku UX Plan

Date: 2026-06-19

Feature version target: KMK-Recs v0.6.12

Status: implementation plan only. Do not modify application code until the user explicitly approves implementation.

## Goal

Fix two connected Source Evaluation usability problems:

1. Source Evaluation only shows/evaluates the same small set of already recommended Sources To Try candidates instead of broadening discovery to more available non-installed extensions.
2. Shizuku can be installed, running, and authorized, but the Source Evaluation UI does not clearly refresh or explain what the user should do next.

The intended behavior is:

- Sources To Try should remain selective and recommendation-focused.
- Source Evaluation should evaluate a broader eligible pool of non-installed extensions so it can discover new good sources.
- Already-evaluated sources should not keep reappearing unless the user intentionally disables "skip already evaluated" or enables stale re-evaluation.
- Shizuku setup should clearly say whether it is ready and whether the next step is "start evaluation."

## User-Observed Problems

### Candidate pool problem

User reports that even after installing/running/authorizing Shizuku and refreshing/opening the screen, Source Evaluation only shows the same three sources that were already in recommendations and already tried.

This matches the current implementation:

```text
SourceEvaluationScreenModel
-> getNonInstalled.subscribe()
-> GetNonInstalledSourceSuggestions
-> NonInstalledSourceSuggestionScorer.scoreAndFilter()
```

`GetNonInstalledSourceSuggestions` is designed for the "Sources To Try" UI, not for broad evaluation. It intentionally filters out most available extensions unless they already have recommendation evidence, such as:

- similar name to an installed source,
- explicit user like,
- existing positive source evaluation verdict.

That is correct for Sources To Try, but wrong for Source Evaluation. Source Evaluation is supposed to create evidence by testing broader candidates.

### Shizuku UX problem

After Shizuku is installed/running/authorized, the UI may still leave the user unsure what to do because:

- it does not automatically refresh state after returning from the Shizuku app,
- there is no manual "Refresh status" button,
- the "Stop using Shizuku" button can appear because Shizuku is selected, but that does not clearly tell the user "you can start now,"
- there is no clear "Shizuku ready" confirmation message tied to the Start Evaluation button.

## Current Relevant Code

### Source Evaluation candidate loading

File:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

Current candidate loading:

```kotlin
getNonInstalled.subscribe()
    .catch { ... }
    .onEach { suggestions ->
        val candidates = suggestions.mapIndexed { index, s ->
            EvaluationCandidate(extension = s.extension, priorityRank = index)
        }.distinctBy { it.extension.pkgName + "|" + it.extension.signatureHash }
        mutableState.update { it.copy(candidates = candidates, isLoadingCandidates = false) }
    }
    .launchIn(screenModelScope)
```

Problem: `getNonInstalled` returns recommendation suggestions, not evaluation candidates.

### Sources To Try suggestion scorer

File:

```text
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt
```

This scorer intentionally requires meaningful positive evidence for metadata-only suggestions:

```kotlin
private fun hasMeaningfulEvidence(reasons: List<NonInstalledSuggestionReason>): Boolean =
    reasons.any { it is NonInstalledSuggestionReason.SimilarToInstalledSource }
```

This should not be weakened for Sources To Try. Instead, Source Evaluation needs a separate provider.

### Evaluation options

File:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt
```

Current options:

```kotlin
data class SourceEvaluationOptions(
    val installerMode: SourceEvaluationInstallerPolicy.InstallerMode = SourceEvaluationInstallerPolicy.InstallerMode.PRIVATE,
    val batchSize: Int = 10,
    val skipAlreadyEvaluated: Boolean = true,
    val includeExplicitCandidates: Boolean = false,
    val reEvaluateStale: Boolean = false,
)
```

Problems:

- `skipAlreadyEvaluated` currently filters candidates at start time using extension-level keys against source-level evaluation keys:

```kotlin
val evaluatedKeys = s.evaluations.map { it.evaluationKey }.toSet()
val key = c.extension.signatureHash + "|" + c.extension.pkgName
key !in evaluatedKeys
```

This does not correctly skip multi-source extension evaluations because stored source evaluation keys can be `signatureHash|pkgName|sourceId`.

- `reEvaluateStale` exists but is not meaningfully applied.

### Shizuku state

Files:

```text
app/src/main/java/exh/recs/evaluation/ShizukuSetupHelper.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
```

Current `ShizukuSetupHelper` correctly reads:

- installed,
- binderAlive,
- permissionGranted.

Current UI has setup/open/use/stop/uninstall actions, but no explicit refresh action or lifecycle refresh.

## Design Decision

Do not change `GetNonInstalledSourceSuggestions` to become broad.

That class should stay selective because it powers Sources To Try. If it becomes broad, the user-facing recommendations become noisy again.

Instead, create a dedicated evaluation candidate provider:

```text
GetSourceEvaluationCandidates
```

This provider should use broad eligibility rules, not recommendation-evidence rules.

## Implementation Plan

### Phase 1: Create a dedicated evaluation candidate provider

Create:

```text
app/src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt
```

This class should subscribe to:

- `extensionManager.availableExtensionsFlow`
- `extensionManager.installedExtensionsFlow`
- `extensionManager.untrustedExtensionsFlow`
- `sourcePreferences.recommendationSourceLanguages().changes()`
- `sourcePreferences.dismissedNonInstalledRecommendationSources().changes()`
- `sourcePreferences.likedRecommendationSourceKeys().changes()`
- `sourcePreferences.dislikedRecommendationSourceKeys().changes()`
- `getSourceEvaluations.subscribeAll()`
- `sourcePreferences.showNsfwSource().changes()` if available, otherwise read inside combine
- `sourcePreferences.blockExplicitPornHentaiSources().changes()` if available, otherwise read inside combine

If combine source count becomes awkward, use nested `combine` or small intermediate data classes. Avoid complex untyped tuple nesting that makes future maintenance painful.

Recommended output:

```kotlin
data class SourceEvaluationCandidateInfo(
    val candidate: EvaluationCandidate,
    val status: Status,
    val existingEvaluationCount: Int,
    val lastEvaluatedAt: Long?,
)
```

or keep output simple:

```kotlin
Flow<List<EvaluationCandidate>>
```

Recommendation: start with `Flow<List<EvaluationCandidate>>` for this fix, but structure helper functions so status counts can be added later.

### Phase 2: Broad eligibility rules

The provider should build candidates from available non-installed extensions directly.

Include an available extension when:

- it is not already installed,
- it is not untrusted,
- its extension/source language matches recommendation languages,
- NSFW is allowed or the extension/source is not NSFW,
- explicit filter is respected unless `includeExplicitCandidates` is true at start time,
- it is not explicitly disliked in recommendation source preferences,
- it is not dismissed if dismissal is meant to remove it from evaluation too.

Important nuance:

- Dismissed from Sources To Try might mean "not now" rather than "never evaluate."
- Disliked means stronger user rejection.

Recommendation:

- Respect `dislikedRecommendationSourceKeys()` always.
- Respect explicit source filter.
- Do not automatically exclude dismissed suggestions from Source Evaluation unless current code/product already treats dismissal as global hiding. If uncertain, include dismissed in evaluation but document it. Evaluation is a discovery tool, not the same as display recommendations.

### Phase 3: Language handling

For extensions with listed sources:

- include the extension if at least one source language matches recommendation languages.

For extensions with empty source metadata:

- include the extension if `ext.lang` matches recommendation languages.

Then dedupe by extension identity:

```kotlin
signatureHash + "|" + pkgName
```

Source Evaluation installs/evaluates an extension as a package, then probes all catalogue sources inside it. Therefore the candidate list should be extension-level, not source-level.

### Phase 4: Already evaluated filtering

Fix evaluated-key handling.

Stored source evaluation records have:

- `evaluationKey`
- `extensionKey`
- `sourceId`
- `extensionPkgName`
- `signatureHash`

Use extension-level grouping:

```kotlin
val evaluatedExtensionKeys = evaluations.map { it.extensionKey }.toSet()
val candidateExtensionKey = "${ext.signatureHash}|${ext.pkgName}"
```

When `skipAlreadyEvaluated == true`, exclude candidates whose extension key has any existing evaluation record.

When `reEvaluateStale == true`, allow candidates with stale records. Stale should be based on:

- `expiresAt != null && expiresAt <= now`, OR
- `evaluationVersion < SourceEvaluationKeys.CURRENT_VERSION`.

If implementing stale correctly requires more changes than expected, do this minimum:

- use `extensionKey` for skip filtering now,
- leave `reEvaluateStale` disabled/hidden or document as future work.

Preferred fix:

```kotlin
fun shouldSkipCandidate(
    ext: Extension.Available,
    evaluationsByExtensionKey: Map<String, List<SourceEvaluation>>,
    skipAlreadyEvaluated: Boolean,
    reEvaluateStale: Boolean,
    now: Long,
): Boolean
```

Rules:

- If no evaluations exist for extension key -> do not skip.
- If `skipAlreadyEvaluated == false` -> do not skip.
- If `reEvaluateStale == true` and any record is stale -> do not skip.
- Otherwise skip.

### Phase 5: Move candidate filtering earlier

Currently candidates are loaded once from suggestions, and `skipAlreadyEvaluated` is applied only in `startEvaluation()`.

This makes the UI misleading: it may say there are candidates, but Start evaluates fewer or none.

Change Source Evaluation state to hold both:

```kotlin
allCandidates
visibleCandidates
```

or simpler:

- compute visible candidates in the candidate provider using current options,
- reload/recompute when `skipAlreadyEvaluated`, `includeExplicitCandidates`, or `reEvaluateStale` changes.

Recommendation:

Use a provider method:

```kotlin
subscribe(optionsFlow: Flow<SourceEvaluationOptions>): Flow<List<EvaluationCandidate>>
```

If that is too invasive, keep provider broad and in `SourceEvaluationScreenModel` recompute:

```kotlin
private val allEvaluationCandidates = MutableStateFlow<List<EvaluationCandidate>>(emptyList())
```

Then on options/evaluations changes update `state.candidates`.

Acceptance requirement:

- The count shown in the Start button must match what will actually be evaluated.

### Phase 6: Replace candidate loading in SourceEvaluationScreenModel

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

Replace dependency:

```kotlin
private val getNonInstalled: GetNonInstalledSourceSuggestions = Injekt.get()
```

With:

```kotlin
private val getSourceEvaluationCandidates: GetSourceEvaluationCandidates = Injekt.get()
```

or manual construction if DI pattern requires it, but DI is preferred.

Candidate loading should use the new broad provider:

```kotlin
getSourceEvaluationCandidates.subscribe()
    .catch { ... emptyList() ... }
    .onEach { candidates -> mutableState.update { it.copy(candidates = candidates, isLoadingCandidates = false) } }
    .launchIn(screenModelScope)
```

Do not use `GetNonInstalledSourceSuggestions` for Source Evaluation anymore.

Keep `GetNonInstalledSourceSuggestions` unchanged for Recommendation Settings / Sources To Try.

### Phase 7: Register provider in DI

Update:

```text
app/src/main/java/eu/kanade/domain/KMKDomainModule.kt
```

Register:

```kotlin
addFactory { GetSourceEvaluationCandidates(get(), get(), get()) }
```

Use named arguments if the project style allows it.

This prevents another runtime `Injekt.get()` crash.

### Phase 8: Add source evaluation candidate UI diagnostics

In `SourceEvaluationScreen`, add a small candidate summary near the Start button:

Examples:

```text
12 extensions available for evaluation
3 already evaluated are hidden
Explicit-heavy candidates hidden
```

Keep it compact. This should help the user understand why the count is low.

If only three candidates are available because filters are tight, the UI should say why.

Do not overbuild a full candidate management screen in this pass.

### Phase 9: Shizuku status UX polish

Update:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
```

Add a manual `Refresh status` action on the Shizuku setup card.

Wire it to:

```kotlin
screenModel.refreshShizukuState()
```

Add a lifecycle refresh when returning to Source Evaluation.

Recommended Compose pattern:

```kotlin
LifecycleStartEffect(Unit) {
    screenModel.refreshShizukuState()
    onStopOrDispose { }
}
```

or use the existing lifecycle utility pattern if the project already uses one.

Goal:

- after the user opens Shizuku, starts it, grants permission, and returns, the card refreshes automatically.

### Phase 10: Make Shizuku next step explicit

Update status text in `ShizukuSetupCard`:

Current selected state is too vague:

```text
Komikku will use Shizuku for this evaluation run
```

Preferred state when selected and ready:

```text
Shizuku is ready and selected. You can start evaluation now.
```

When selected but not ready:

```text
Shizuku is selected, but it is not ready yet.
```

When installed/running/authorized but not selected:

```text
Shizuku is ready. Tap "Use for this run" if you want evaluation to use it.
```

This directly addresses the user's confusion after opening Shizuku.

### Phase 11: Start button clarity

If the Start button is disabled, show why:

- no candidates,
- installer not ready,
- Shizuku selected but not running,
- Shizuku selected but permission missing.

Keep this as a short line below the Start button or reuse existing policy message.

Do not block start if `installerMode == PRIVATE` and Private is ready.

### Phase 12: Tests

Add/update unit tests.

Recommended new test file:

```text
app/src/test/java/exh/recs/evaluation/GetSourceEvaluationCandidatesTest.kt
```

Because Android `ExtensionManager` flows may be awkward to instantiate, split pure filtering logic into an object:

```text
SourceEvaluationCandidateFilter.kt
```

Test pure filtering rules:

- includes available extension with matching language even if it has no suggestion evidence.
- excludes installed extension.
- excludes untrusted extension.
- excludes disliked available source/extension key.
- excludes non-matching language.
- excludes explicit extension when block explicit is enabled and include explicit is false.
- includes explicit extension when include explicit is true.
- dedupes multiple matching sources from same extension to one extension candidate.
- skips already-evaluated extension using `extensionKey`, not full `evaluationKey`.
- allows stale re-evaluation when `reEvaluateStale == true`.

Update existing tests if the new helper changes package visibility.

Also add/update Shizuku UI logic tests if pure helpers are changed:

- selected and ready -> ready selected message state.
- selected but not ready -> selected but not ready state.

Run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

### Phase 13: Documentation and versioning

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Create implementation report:

```text
docs/recommendations/KMK_RECS_V0_6_12_SOURCE_EVALUATION_CANDIDATE_POOL_AND_SHIZUKU_UX_IMPLEMENTATION.md
```

Implementation report must include:

- root cause of the three-candidate issue,
- why `GetNonInstalledSourceSuggestions` was the wrong provider for Source Evaluation,
- new candidate provider/filter behavior,
- how already-evaluated filtering now works,
- Shizuku refresh/status UX changes,
- files changed,
- tests run,
- generated APK path/name,
- manual verification performed or not performed.

User-facing What's New should mention only actual user-facing changes:

```text
Source Evaluation now checks a broader pool of available extensions instead of only already suggested sources.
Shizuku setup now refreshes status more clearly and tells you when evaluation can start.
```

Do not mention developer documentation changes in What's New.

## Acceptance Criteria

This work is complete only if:

- Source Evaluation no longer depends on `GetNonInstalledSourceSuggestions` for its candidate pool.
- Sources To Try remains selective and is not flooded with every available extension.
- Source Evaluation can evaluate broader available non-installed extensions that match language/safety filters.
- Already-evaluated extensions are skipped correctly using extension-level keys.
- `reEvaluateStale` either works correctly or is explicitly deferred/hidden; it must not remain misleading.
- The Start button count matches the actual number of candidates that will be evaluated.
- Shizuku status refreshes when returning from Shizuku and through a manual refresh action.
- Shizuku selected/ready state clearly tells the user they can start evaluation.
- No app code tries to silently start/stop/install/uninstall Shizuku.
- Unit tests cover the candidate filtering logic.
- Debug APK builds successfully.
- Documentation and versioning are updated to v0.6.12.

## Non-Goals

Do not implement:

- a full candidate browser,
- source scoring changes,
- source probing changes,
- batch size expansion beyond existing values,
- Shizuku silent service control,
- universal extension installation automation,
- changes to normal Sources To Try recommendation strictness,
- changes to For You recommendation source ranking.

## Recommended Implementation Order

1. Add pure `SourceEvaluationCandidateFilter`.
2. Add tests for broad candidate filtering and skip logic.
3. Add `GetSourceEvaluationCandidates`.
4. Register it in DI.
5. Swap Source Evaluation screen model to use it.
6. Fix start-count/skip behavior.
7. Add Shizuku refresh/status UI polish.
8. Update docs/version/release notes.
9. Run full tests and build APK.

## Recommendation

Proceed with this fix before adding more source-evaluation features.

The current behavior prevents Source Evaluation from doing its main job: discovering sources beyond the already-suggested list. Separating the broad evaluation candidate pool from the selective Sources To Try recommendation pool is the cleanest and most maintainable correction.
