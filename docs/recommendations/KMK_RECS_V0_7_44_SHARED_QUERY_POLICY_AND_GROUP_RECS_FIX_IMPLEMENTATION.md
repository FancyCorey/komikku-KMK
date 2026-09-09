# KMK-Recs v0.7.44 Shared Query Policy, Group Recommendation Parity, and For You Relevance Implementation

Date: 2026-07-12

Status: implemented, verified, shipped.

Plan: `KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_PLAN.md`

## 1. Release Identity

- `KmkRecsReleaseNotes.VERSION_CODE`: `746` (previous checked-in value was `745` from v0.7.43,
  confirmed before editing).
- `KmkRecsReleaseNotes.VERSION_NAME`: `"KMK-Recs v0.7.44"`.
- APK handoff: `Komikku-v1.13.6-kmk.7.44-debug.apk`, copied to `private/`.
- Android `versionCode`/`versionName` were not touched.
- Implemented in checkpointed phases (A-H as laid out in the plan), each compiled/tested before the
  next began. No APK was built until every phase plus this report were complete.
- Per explicit user instruction: **no MarkSeen/Not Interested behavior changes** are part of this
  release. That work was confirmed already implemented in v0.7.43 and is untouched here.

## 2. Phase A: Repaired Active Test Failures

### Root cause

`Manga.kt`'s constructor eagerly resolves `GetCustomMangaInfo` via Injekt whenever `favorite = true`
(`customMangaInfo = if (favorite) getCustomMangaInfo.get(id) else null`). Unit tests never bootstrap
the app's real Injekt graph (only `App.onCreate` does), so any test constructing a favorite-manga
threw `InjektionException`. This affected three test classes, not just the one the plan named:

- `RecommendationCandidateVisibilityPolicyTest` (3 of 16 tests) â€” the one explicitly named in the plan.
- `BrowsePersonalRecommendationsFilterTest` (1 test) â€” found while re-running the full `exh.recs.*`
  suite after the first fix; same root cause, previously undetected because it only reproduced when
  this specific test happened to run without another test having already (accidentally) warmed up the
  binding.
- `RecommendationCandidateMemoryRankerTest` (1 test) â€” same root cause, same discovery path.

### Fix

New `app/src/test/java/exh/recs/TestInjektSupport.kt` â€” a shared, idempotent
`TestInjektSupport.ensureCustomMangaInfoBound()` that registers a no-op `GetCustomMangaInfo` binding
via `Injekt.importModule(...)`. Called from a `@JvmStatic @BeforeAll` in each of the three affected
test classes (`RecommendationCandidateVisibilityPolicyTest`, `BrowsePersonalRecommendationsFilterTest`,
`RecommendationCandidateMemoryRankerTest`). No production code was touched â€” this is purely a test
bootstrap fix, chosen over the plan's alternative options (constructing test manga to avoid the
favorite path, or refactoring the policy test helper) because the affected tests are specifically
*testing* favorite-manga behavior and cannot avoid constructing `favorite = true` manga.

### Comment reconciliation

`RecommendationCandidateVisibilityPolicy.kt`'s class doc, which the v0.7.43 report already corrected
to no longer claim "live/cache/memory/group all go through this policy" (it was updated to say group
recommendations only excluded seed-member/exact-Seen), needed a further update once Phase E (below)
made group recommendations actually use the full policy â€” done, see Phase E.

### Verification

`.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicyTest*"` â€” 16/16
PASSED. Re-running the full `exh.recs.*` package after the fix confirmed 0 failures across all three
previously-flaky classes.

## 3. Phase B: Shared Query Attempt Policy

New `app/src/main/java/exh/recs/RecommendationQueryAttemptPolicy.kt` â€” pure, Android-free:

- `RecommendationQueryFailureKind` enum: `NONE`, `SOURCE_EXCEPTION`, `FILTER_UNSUPPORTED`,
  `NO_RAW_RESULTS`, `FILTERED_UNRELATED`, `WEAK_METADATA`, `CANCELLED`.
- `RecommendationQueryAttemptOutcome` data class (`strategy`, `rawCount`, `enrichedCount`,
  `relevantCount`, `failureKind`, computed `isUseful`).
- `buildTagAttemptChain(topTags)`: deterministic strict-to-lenient chain â€” `TOP_TAGS_FILTER` (top 5
  tags) â†’ `TAG_PAIR` (top 2) or `SINGLE_STRONGEST_TAG` (top 1, when fewer than 2 tags available) â†’
  `TEXT_ONLY_TOP_TAGS` (top 3, `forceTextOnly = true`) â€” capped at `MAX_TAG_ATTEMPTS = 3`.
- `classify(...)`: maps attempt counts + exception/filter-unsupported flags to a `RecommendationQueryFailureKind`.
- `shouldTryNext(outcome)`: true unless the outcome is useful or cancelled.

New `app/src/test/java/exh/recs/RecommendationQueryAttemptPolicyTest.kt` â€” 16 tests covering chain
construction (empty input, tag-count-dependent second strategy, deterministic ordering, cap), and every
`classify`/`shouldTryNext` branch including the cancellation case.

**Design decision:** rather than replacing `RecommendationQueryPlanner` (For You's existing planner,
which also persists per-source "last successful strategy" and integrates with discovery
progress/candidate memory), `RecommendationQueryAttemptPolicy` is a new, smaller, one-shot policy used
directly by the new call site (`CrossExtensionGenreSearchSource`, Phase C) that has no persisted-strategy
state to manage. `RecommendationQueryPlanner` itself was deepened (Phase D) to walk the same
strict-to-lenient family. The plan explicitly permitted either shape ("Keep `RecommendationQueryPlanner`
as the deterministic planner, **or** create a small companion") â€” this was the lower-risk choice given
`searchSource()`'s existing integration depth.

## 4. Phase C: Applied the Shared Query Policy to Group Recommendations

### `CrossExtensionGenreSearchSource` rewrite

`requestNextPage()` now:

1. Builds the tag attempt chain via `RecommendationQueryAttemptPolicy.buildTagAttemptChain(desiredGenres)`
   (`desiredGenres = genreOverride ?: manga.genre.orEmpty()`, unchanged input source).
2. Tries each attempt via a new `tryAttempt(plan)` â€” same `getFilterList()` â†’ `GenreFilterMapper.buildSearch(...)`
   â†’ `getSearchManga(...)` â†’ enrich-top-results flow as before, but now per-attempt with a **classified
   outcome** (`lastFailureKind`, a new public read-only property for row-level diagnostics/logging)
   instead of throwing/returning after one try. `CancellationException` always rethrows before
   classification; ordinary exceptions are logged and treated as a failed attempt so one attempt's
   transient error doesn't block trying a more lenient one.
3. If every tag attempt returns zero raw results, falls through to a new `runTitleFallback()` â€” tries
   up to `MAX_TITLE_FALLBACKS = 2` titles from `titlesOverride` (new constructor parameter; `null` for
   single-manga falls back to `manga.ogTitle` only, matching the old behavior exactly).
4. `enrichTopResults(...)` extracted unchanged (same `MAX_ENRICH_PER_SOURCE = 10` cap, same mutate-in-place
   behavior) â€” reused by both the tag-attempt path and the title-fallback path.

This class only fetches raw candidates â€” true relevance scoring happens downstream in
`RecommendsScreenModel` (`RecommendationScorer`/`GroupSeedRecommendationScorer`), so "useful" at this
layer means "non-empty raw result," not "scored as relevant." The downstream relevance filter is new in
this release too (Phase E, item 5).

### `RecommendationPagingSource.createSources(...)` and `RecommendsScreenModel`

Two new optional parameters, both `null`/no-op for the single-manga path:

- `groupTitlesOverride: List<String>?` â€” fed `GroupRecommendationSeed.titles` (every linked version's
  title, already built by the unchanged `GroupRecommendationSeedBuilder`).
- `eligibleCrossExtensionSources: List<CatalogueSource>?` â€” see Phase E.

`RecommendsScreenModel`'s `CrossSourceGroupSeed` branch now passes
`groupTitlesOverride = seed.titles.takeIf { it.isNotEmpty() }` alongside the existing
`groupGenreOverride = seed.tags.takeIf { it.isNotEmpty() }`.

### Row scorer

`RecommendationScorer.score(primary, candidate)` remains in use (unchanged) as the base similarity
score, but is now combined with a relevance floor (Phase E, item 5) so a candidate isn't kept purely
because it sorted above zero â€” see below. Seed-member and exact-Seen exclusion (v0.7.43) are unchanged.

## 5. Phase D: Applied the Widened Chain to For You

`RecommendationQueryPlanner.buildPlans(...)` was changed from "primary + at most one fallback" to
walking the existing `fallbackFor()` chain repeatedly until `MAX_STRATEGIES_PER_SOURCE` (2 â†’ **3**) is
reached or a strategy has no fallback (`TEXT_ONLY_TOP_TAGS` remains terminal). `lastSuccessful` still
seeds the starting strategy, preserving the existing fast-path behavior. This is a minimal, surgical
change to the existing, already-integrated planner rather than a rewrite of `searchSource()`.

Also fixed: a source's "successful strategy" is no longer persisted (`successfulStrategy = plan.type.takeIf
{ mergedRecommendations.isNotEmpty() }`) unless the attempt that reached the end of the loop actually
produced a visible result. Previously, once the loop reached the last plan in the chain (regardless of
whether it produced results), `successfulStrategy = plan.type` was set unconditionally â€” meaning a
source could get "locked" onto a strategy that produced zero results simply because it happened to be
tried last.

### Verified already-correct (no code change needed)

Read through `BrowsePersonalRecommendationsScreenModel.searchSource()` in full before assuming Phase D
items 2 and 4 needed new code:

- **Item 2** ("filtered as unrelated" vs. generic failure): `recordProgress(...)` already classifies
  `STATUS_SUCCESS` / `STATUS_FILTERED` (raw results existed but none survived scoring/visibility) /
  `STATUS_EMPTY` (no raw results at all) â€” this distinction already existed pre-v0.7.44.
- **Item 4** (don't poison rolling discovery on one strict failure): the `if (recommendations.size <
  minUseful && plan != plans.last()) { continue }` guard means `recordProgress(...)` (and cache/memory
  writes) only run once a plan either meets `minUseful` or is the last attempt in the chain â€” never on
  an early, still-retryable attempt. This was already true before v0.7.44 and is now exercised across
  3 attempts instead of 2.

Both were confirmed correct by reading the code, not assumed â€” documenting this per the plan's own
"if an apparently required code path is already implemented, document that verification instead of
duplicating it" guardrail (carried over from the v0.7.43 plan and applied here).

Test-suite tests updated for the widened chain (`RecommendationQueryPlannerTest`): the `TOP_TAGS_FILTER
fallback is TAG_PAIR when tags available` test now expects 3 plans (`TOP_TAGS_FILTER â†’ TAG_PAIR â†’
TEXT_ONLY_TOP_TAGS`) instead of 2; every other existing test (including `TEXT_ONLY_TOP_TAGS has no
fallback`, `MAX_STRATEGIES_PER_SOURCE` cap, determinism) continued to pass unchanged.

## 6. Phase E: Group Source Selection and Visibility Parity

### Source selection

`RecommendsScreenModel`'s `CrossSourceGroupSeed` branch now computes eligible cross-extension sources
before calling `createSources(...)`:

```kotlin
val eligibleSources = RecommendationSourceSelector.select(
    sources = sourceManager.getVisibleCatalogueSources(),
    languages = recommendationLanguages,       // sourcePreferences.recommendationSourceLanguages()
    storedOrder = storedOrder,                  // RecommendationSourceOrdering.parse(...)
    effectiveDisabledIds = effectiveDisabledIds, // disabled + disliked installed sources
    maxSources = MAX_CROSS_EXTENSION_SOURCES,
)
```

identical inputs/logic to what `BrowsePersonalRecommendationsScreenModel` already uses for For You.
Passed through `createSources(..., eligibleCrossExtensionSources = eligibleSources)`; when non-null,
`RecommendationPagingSource.createSources` uses it instead of raw
`sourceManager.getVisibleCatalogueSources().take(MAX_CROSS_EXTENSION_SOURCES)`. The single-manga path
passes `null` and is unaffected.

### Candidate visibility

Group candidates now go through the full `RecommendationCandidateVisibilityPolicy.evaluate(...)`
(seed member, favorite, rated, seen/Not Interested, known, min-chapter) instead of only the manual
seed-member/exact-Seen filter v0.7.43 shipped with. Context is batch-loaded once per screen load
(`tasteByKey` via `getMangaTaste.awaitAll()`, `visibility`/`hideKnownManga`/`minChapterCount` from
preferences); known-id and chapter-count lookups are batched **once per row** (after localization and
dedup, before scoring) rather than per candidate, matching the plan's "avoid per-candidate database
calls inside tight loops" requirement.

`RecommendationCandidateVisibilityPolicy.kt`'s doc comment was updated again (the v0.7.43 report had
already corrected the stale "group goes through this policy" claim to "group does not") â€” it now
correctly states group recommendations **do** go through the shared policy as of v0.7.44, and the
matching test-file comment in `RecommendationCandidateVisibilityPolicyTest.kt`
(`---- v0.7.41: single shared contract â€” live / cache / memory / group all go through this policy
----`) was left as-is since it is now, again, accurate.

### Relevance filtering (Phase C item 9 / plan acceptance criterion)

Group rows now filter out near-zero-relevance candidates rather than only sorting them last:

```kotlin
if (seed != null && seed.seedTags.isNotEmpty()) {
    scored.filter { (_, score) -> score > GROUP_RELEVANCE_MIN_SCORE } // 0.1
} else {
    scored
}
```

applied to `RecommendationScorer.score(primary, candidate) + GroupSeedRecommendationScorer.score(candidate,
seed, aliasMap)`. Deliberately conservative â€” only removes true zero-signal noise; the threshold only
activates when the seed actually has tag evidence to score against, so a sparse-metadata group doesn't
have its (already limited) results emptied further. Single-manga rows are unaffected (`seed == null`).

### Scoped to group-seed path only

All of the above (source selector, visibility policy, relevance filter) apply only when
`args is RecommendsScreen.Args.CrossSourceGroupSeed`. The single-manga Recommendations page's behavior
is byte-for-byte unchanged â€” confirmed by reading every branch touched and gating each new block on
`groupSeed != null`/`seed != null`.

## 7. Phase F: Background Job Test Coverage

### `SourceRecommendationQualityJobConflictPolicy` (new, pure)

Extracted the job-conflict guard decision â€” previously two inline
`if (SourceRecommendationQualityJob.isRunning(context))` / `if (SourceEvaluationJob.isRunning(context))`
checks in `SourceEvaluationScreenModel` with zero dedicated test coverage (the Android/WorkManager
`isRunning()` calls themselves aren't unit-testable, but which job should block which is a pure
decision). `conflictFor(starting, sourceEvaluationRunning, recommendationQualityRunning)` returns the
blocking job kind or `null`. Both guard sites in `SourceEvaluationScreenModel` now call through this
policy; behavior is identical, just testable. New
`SourceRecommendationQualityJobConflictPolicyTest.kt` â€” 5 tests covering both directions, both the
"nothing running" and "blocked" cases, and confirming a job's own running-flag doesn't block itself.

### `SourceRecommendationQualityRunner.loadAvailableExtensions()`

Reviewed per the plan's explicit ask. Previously returned `emptyList()` immediately if
`extensionManager.availableExtensionsFlow.value` was empty â€” plausible right after app/extension-manager
startup, before the repo list has loaded, if the user taps "check compatibility" very early. Now does a
bounded wait (existing `extensionManager.availableExtensionsFlow`, `first { it.isNotEmpty() }`,
`withTimeoutOrNull(5_000L)`) before falling back to empty. No new API surface; the function became
`suspend` (its only caller was already inside a coroutine).

### Other pieces named in Phase F

Target queue computation (`SourceRecommendationQualityQueueTest`), per-source failure isolation and
cancellation propagation (exercised structurally in `SourceRecommendationQualityRunner` â€” unchanged from
v0.7.43, still Android-dependent and not independently unit-testable), and installed-vs-temporary
cleanup decision boundaries (`SourceRecommendationQualityInstalledResolverTest`/
`SourceRecommendationQualityExtensionResolverTest`/`SourceRecommendationQualitySourceResolverTest`, all
pre-existing from v0.7.43) were reviewed and confirmed already covered or confirmed not independently
testable without Android test infrastructure â€” no duplicate tests were added for already-covered pure
helpers.

## 8. Phase G: Phone UI Density Pass

**No screenshot/device testing was available this session.** Verification was by Compose layout code
inspection only (the plan explicitly permits this and requires documenting it). Screens reviewed:
`SourceEvaluationScreen.kt` (full file), `RecommendationsSettingsScreen.kt` (full file),
`RatedMangaScreen.kt` (`RatedSortRow` and the collection grid).

### G.1 â€” `EvaluationResultRow` diagnostics collapsed by default

- Title and compact catalogue subtitle remain always visible (unchanged).
- The compatibility label (`recQualityLabel`, e.g. "For You search: Good") remains always visible â€”
  it was already short and KMR-driven.
- The error-kind badge and reason-hint text (previously always rendered when present, up to 3 extra
  lines) are now behind a new per-row `IconButton` (`ExpandLess`/`ExpandMore`, 16dp icon inside a 20dp
  touch target) with a KMR content description (`source_evaluation_row_show_details`/
  `source_evaluation_row_hide_details`). State is `rememberSaveable(evaluation.evaluationKey)`, defaulting
  to collapsed, so re-composition/scroll doesn't lose per-row expansion state and each row is independent.
- **13 previously-hardcoded English failure-kind labels** ("No taste evidence", "Ext list unavailable",
  "Search error", etc.) extracted into a new `failureKindLabel(...)` composable backed by 13 new KMR
  strings (`source_evaluation_rec_error_kind_*`). This was the one clear hardcoded-string violation the
  plan called out explicitly.
- Reason-hint `maxLines` increased from 2 to 4 now that it's opt-in (was previously always-visible and
  kept short for that reason; expanded state can now afford more room).

### G.2 â€” Action rows converted to `FlowRow`

- `ShizukuSetupCard`'s action row (up to 5 `TextButton`s: Open/Stop-using-or-Use-for-run/Use-Private/
  Uninstall/Refresh) â€” was a fixed `Row`, now `FlowRow` (wraps instead of crowding/overflowing).
- The missing/outdated/recheck-all compatibility-check action row (up to 3 buttons) â€” same fix.
- Both changes are presentation-only; `onClick` handlers, `enabled` conditions, and button content are
  unchanged.

### G.3 â€” Recommendations Settings

- Preferred/blocked tag chip rows already used `FlowRow` (pre-existing, confirmed by inspection â€”
  nothing to fix).
- Found and fixed one additional crowded row not explicitly named in the plan but matching its "long
  source suggestion rows" concern: the non-installed source suggestion row's Install/Dismiss/Like/Dislike
  controls (`Button` + `OutlinedButton` + 2 `IconButton`s in one `Row`) â€” converted to `FlowRow`.

### G.4 â€” Rated Manga and grouped recommendation entry points

- `RatedSortRow`'s `FilterChip` row already wraps in `Modifier.horizontalScroll(scrollState)` â€” reviewed
  and confirmed this already prevents overflow/clipping on narrow screens (chips scroll rather than
  shrink or get cut off). No change needed.
- Group-recs entry points (long-press + Explore icon overlay) were already made discoverable without
  requiring long-press in v0.7.36/v0.7.43 â€” confirmed unchanged.
- No new large diagnostic blocks were added to `RecommendsScreen`'s rows in this release (group-row
  diagnostics stay at the existing `RecommendationItemResult.Error`/empty-success granularity).

### G.5 â€” Not reviewed exhaustively

`SourceEvaluationScreen.kt` alone has ~59 button/row call sites. This pass covered the four areas the
plan explicitly named as highest-risk (G.1-G.4 above) rather than auditing every row in the file â€” an
intentional scope decision given the size of the rest of this release, documented here per the plan's
own instruction to document any such limitation rather than silently narrowing scope.

## 9. Data / Migration / Backup Impact

None. No SQLDelight schema changes, no new migrations, no new backup fields. No new background job, no
new network behavior (the bounded `availableExtensionsFlow` wait reuses the existing flow from the
existing v0.7.43 job; it does not add a network call).

## 10. Compatibility With Old Backups/Preferences

Unaffected â€” no preference keys, backup fields, or storage formats changed in this release.

## 11. Tests Run

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

- `.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicyTest*"` â€”
  BUILD SUCCESSFUL, 16/16 PASSED (was 3 FAILED before Phase A)
- `.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationQueryPlanner*"` â€” BUILD SUCCESSFUL,
  14/14 PASSED
- `.\gradlew.bat :app:testDebugUnitTest --tests "*GenreFilterMapper*"` â€” BUILD SUCCESSFUL
- `.\gradlew.bat :app:testDebugUnitTest --tests "*Group*Recommendation*"` â€” BUILD SUCCESSFUL, 7/7
  PASSED (`GroupRecommendationSourcePolicyTest` x6, `GroupSeedEnrichmentTest` x1 â€” real tests matched,
  not a no-op filter)
- `.\gradlew.bat :app:testDebugUnitTest --tests "*SourceRecommendationQuality*"` â€” BUILD SUCCESSFUL
- `.\gradlew.bat :app:testDebugUnitTest --tests "*Seen*"` â€” BUILD SUCCESSFUL (unaffected by this
  release, per the "no MarkSeen changes" instruction)
- `.\gradlew.bat :app:testDebugUnitTest` â€” **949 tests, 0 failures** (was 928 tests, 3 failures at the
  start of this session)
- `.\gradlew.bat spotlessCheck` â€” BUILD SUCCESSFUL
- `.\gradlew.bat assembleDebug` â€” BUILD SUCCESSFUL

New test files: `RecommendationQueryAttemptPolicyTest.kt` (16 tests),
`SourceRecommendationQualityJobConflictPolicyTest.kt` (5 tests), `TestInjektSupport.kt` (shared test
helper, not itself a test class).

## 12. Build Output

APK: `Komikku-v1.13.6-kmk.7.44-debug.apk` â€” copied to `private/`.

## 13. Deviations From The Plan

1. For You's adoption of the shared query-attempt policy was implemented as "deepen the existing
   `RecommendationQueryPlanner`'s fallback chain from 2 to 3 steps" rather than replacing it with
   `RecommendationQueryAttemptPolicy` directly at the `searchSource()` call site. The plan explicitly
   allowed either shape; reusing the existing, already-integrated planner (persisted per-source
   strategy, discovery-progress recording, candidate-memory interplay) was far lower-risk than rewiring
   a ~300-line function around a new abstraction it wasn't designed for.
2. Phase D items 2 and 4 required no new code â€” verified as already correct in the existing
   `searchSource()` implementation (see Section 5) rather than assumed and re-implemented.
3. Phase G was scoped to the plan's explicitly-named highest-risk spots rather than an exhaustive review
   of every button/row in `SourceEvaluationScreen.kt` (~59 sites). One additional crowded row not
   explicitly named (the source-suggestion row in Recommendations Settings) was found and fixed because
   it matched the plan's stated concern closely enough to be in scope.
4. No screenshot/device testing was available â€” Phase G verification was by Compose layout code
   inspection only, as the plan permits when device testing isn't available.
5. No new SQLDelight table, no new backup field, no MarkSeen/Not Interested changes â€” none were needed
   or in scope.

## 14. Remaining Limitations

- The mild Not Interested scoring penalty (v0.7.43) still applies to For You only, not group
  recommendations â€” unchanged from v0.7.43's documented limitation; out of scope for this release per
  explicit instruction.
- `RecommendationCandidateVisibilityPolicy` is now applied to group recommendations (Phase E), but the
  single-manga Recommendations page still does not apply it â€” this remains an intentional asymmetry
  (the single-manga page was never designed around that policy) rather than a bug.
- Phone UI density verification was code-inspection-only; an actual narrow-device pass (or Compose
  preview screenshots) would be a reasonable follow-up if the team wants to confirm the `FlowRow`
  wrapping and collapsed-diagnostics changes render as intended on real hardware.
- `SourceEvaluationScreen.kt`'s remaining ~55 button/row sites outside the four explicitly-reviewed
  areas were not individually audited for phone density; if further crowding is found there, it should
  be a targeted follow-up rather than assumed fixed by this pass.

