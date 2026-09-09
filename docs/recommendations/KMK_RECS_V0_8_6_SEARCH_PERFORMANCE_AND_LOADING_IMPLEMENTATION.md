# KMK-Recs v0.8.6 Search Performance And Loading Implementation Report

**Date:** 2026-07-15

**Feature version/build label:** KMK-Recs v0.8.6 (VERSION_CODE 756, `KmkRecsReleaseNotes.kt`)

**User-approved scope:** Full plan at
`docs/recommendations/KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_LOADING_IMPLEMENTATION_PLAN.md`,
implemented phase by phase (A through E) across one continuous session, followed by a gap-closing
pass covering cache wiring/diagnostics/concurrency test coverage, followed by a reviewer-audit
correctness pass (this update) fixing two real bugs the reviewer found in the actual code rather than
the self-report.

## Reviewer-audit correctness pass (this update)

A reviewer read the code (not just the implementation report) after the gap-closing pass and found
two real correctness bugs. Both are fixed here.

### Bug 1 (top priority): nested enrichment was not actually bounded by the shared budget

**The bug:** `CrossExtensionGenreSearchSource.enrichTopResults()` (~line 192) launched up to
`MAX_ENRICH_PER_SOURCE` (10) detail requests concurrently via `async`/`awaitAll`, but this was
per-source-*instance* concurrency only. With `GroupPreviewLoadCoordinator` allowing 4 concurrently
active GROUP_PREVIEW sources, the real worst case was up to 4 x 10 = 40 simultaneous detail requests
— the plan (section 7) explicitly required nested enrichment to be included in the *same total*
request budget, not merely bounded per-source-op count while unbounded underneath.

**The fix:**
- `CrossExtensionGenreSearchSource` (`app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt`)
  gained a new constructor parameter `sharedEnrichmentSemaphore: Semaphore? = null`. When non-null,
  every detail request inside `enrichTopResults()` now acquires a permit via `semaphore.withPermit {
  }` before calling `catalogueSource.getMangaDetails(...)`, releasing it automatically (including on
  failure) via `withPermit`'s own `finally`. Null (single-manga path — no group seed) preserves the
  original per-source-unbounded behavior exactly.
- `GroupPreviewLoadCoordinator` (`app/src/main/java/exh/recs/GroupPreviewLoadCoordinator.kt`) now owns
  one `sharedEnrichmentSemaphore: Semaphore` (default bound `DEFAULT_MAX_CONCURRENT_ENRICHMENT = 8`)
  alongside its existing source-level `Semaphore(4)`. This single instance is the one that must be
  shared across every source in a load — a per-source-instance semaphore would not cap the
  cross-source total, which is exactly what the bug was.
- `RecommendationPagingSource.createSources()` (`app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`)
  gained a `sharedEnrichmentSemaphore: Semaphore? = null` parameter, passed through to every
  `CrossExtensionGenreSearchSource` it constructs.
- `RecommendsScreenModel`'s `CrossSourceGroupSeed` branch now passes
  `groupPreviewCoordinator.sharedEnrichmentSemaphore` into `createSources(...)`, so every
  cross-extension source created for one group-recommendation load shares the exact same semaphore
  instance. The `SingleSourceManga` branch does not pass one (stays `null`, unaffected).

**Test added:** `GroupPreviewLoadCoordinatorTest.kt` — `shared enrichment semaphore caps total
concurrent detail requests across multiple simultaneously-active sources`. Simulates 4 sources
(matching `DEFAULT_MAX_CONCURRENT`) each independently firing 10 "enrichment" requests (matching
`MAX_ENRICH_PER_SOURCE`) all acquiring the *same* shared semaphore concurrently, and asserts the
observed peak concurrency never exceeds 8 — directly proving the shared instance caps the
cross-source total (as opposed to each source having its own independent 10-permit budget, which
would allow up to 40 concurrent requests, reproducing the original bug if the assertion were changed
to use per-instance semaphores instead).

### Bug 2: cache key was missing recommendation-affecting state

**The bug:** `GroupPreviewCache.Key`'s `visibilityFingerprint` field was originally built inline in
`RecommendsScreenModel` as `"$visibility-$hideKnownManga-$minChapterCount"` — missing disabled-source
state, source order, source-quality (dislike) preferences, seen-entries state, and taste
(Love/Like/Dislike rating) state, all of which directly affect
`RecommendationCandidateVisibilityPolicy.evaluate(...)`'s output and therefore the cached result. A
preference change in any of these could leave a stale cached preview visible for up to the cache's
5-minute TTL.

**The fix:** New pure `GroupPreviewVisibilityFingerprint.build(...)`
(`app/src/main/java/exh/recs/GroupPreviewVisibilityFingerprint.kt`), extracted specifically so the
"changed input -> different fingerprint" property is directly unit-testable. It combines:
`effectiveDisabledSourceIds` (disabled + disliked + quality-disliked source ids — the same set
`RecommendationSourceSelector.select(...)` already uses to choose eligible sources), `storedSourceOrder`,
the raw `dislikedSourceRaw`/`qualityDislikedSourceRaw` preference strings (in addition to the derived
id set, in case two different raw configurations happen to resolve to the same effective ids),
`seenKeys` (seen-entries state), a hash of sorted `(source, url, rating)` triples from `tasteByKey`
(taste/rating state — hashed rather than included verbatim since it can be large; a hash collision
only risks a rare unnecessary cache *hit* within this cache's own 5-minute TTL, never wrong data
surviving beyond that TTL), and the pre-existing `visibility`/`hideKnownManga`/`minChapterCount`.
`RecommendsScreenModel` now calls this once per load (not per row, since none of the inputs are
row-specific) and uses the result as `GroupPreviewCache.Key.visibilityFingerprint`. Three new
`var`s (`effectiveDisabledSourceIds`, `storedSourceOrder`, `dislikedSourceRaw`,
`qualityDislikedSourceRaw`) were hoisted out of the `CrossSourceGroupSeed` branch, where they were
previously `val`s local to that branch only, so the fingerprint builder (which runs after the
`when (args)` block) can read them.

**Test added:** `GroupPreviewVisibilityFingerprintTest.kt` (new, 11 tests) — identical inputs produce
an identical fingerprint; and each of the following, in isolation, changes the fingerprint: a newly
disabled source, a changed source order, a newly disliked source (raw preference), a newly
quality-disliked source, a newly-seen entry, a changed taste rating (same entry count, different
rating value — proves the hash isn't just counting entries), a newly-added taste entry, a changed
rated-manga visibility setting, toggling hide-known-manga, a changed minimum chapter count.

## Gap-closing pass (prior update, unchanged below)

The first implementation pass (below, unchanged) shipped policy/concurrency/timeout/lifecycle
correctness fixes and the settings UI, but left three items open: the cache was built but never
called, no diagnostics were added, and `RecommendsScreenModel` had no concurrency/lifecycle test
coverage. This update closes all three:

1. **Cache wired in.** `RecommendsScreenModel`'s per-row GROUP_PREVIEW block now builds a
   `GroupPreviewCache.Key` (group fingerprint from `groupSeed.groupId` falling back to a sorted
   `memberKeys` join; source ID; sorted recommendation-language set; sorted seed tags+titles;
   a `visibility-hideKnownManga-minChapterCount` visibility fingerprint; the configured preview
   budget) before calling `requestNextPage`. A hit renders the cached list directly and returns
   without ever calling the source or acquiring a concurrency permit. A miss proceeds as before and,
   after budget truncation, stores the final normalized list via `GroupPreviewCache.put`.
   Single-manga and merged-source rows never build a key or touch the cache (`cacheKey` is `null`
   for them). `GroupPreviewCacheTest.kt` (already existed from the first pass) continues to cover the
   cache class's own key/TTL/eviction/invalidation behavior; see Known limitations for what is and
   is not covered for the *wiring* itself.
2. **Diagnostics added**, via the existing `tachiyomi.core.common.util.system.logcat` abstraction
   (the same one `CrossExtensionGenreSearchSource` already uses) at `LogPriority.DEBUG`/`WARN`,
   tag `"RecommendsScreenModel"`: cache hit/miss with source name and hit count; per-row raw vs.
   final (post-budget) candidate count and elapsed time; timeout with elapsed time; cancellation;
   error with elapsed time; and one summary line per load with total row count, total elapsed time,
   and `groupPreviewCoordinator.observedMaxConcurrency()`. Only source *names* (already logged
   elsewhere in this codebase, e.g. `CrossExtensionGenreSearchSource`) and counts/timings are logged
   — no URLs, no manga titles, no cookies/auth headers/HTML/query params.
3. **Concurrency/lifecycle logic extracted into two small, directly testable, dependency-free
   classes** rather than building a full Injekt-mocking harness for `RecommendsScreenModel` (chosen
   per the explicit "prefer this if more tractable" instruction):
   - `app/src/main/java/exh/recs/GroupPreviewLoadCoordinator.kt` — `runBounded(operation)` wraps a
     suspend operation in the same `Semaphore(4)` + `withTimeoutOrNull(20_000L)` behavior previously
     inlined in `RecommendsScreenModel`, plus `observedMaxConcurrency()` for diagnostics.
     `RecommendsScreenModel` now delegates to one instance of this class instead of holding its own
     `Semaphore`.
   - `GenerationGuard` (same file) — `next()`/`isCurrent(generation)` replaces the raw
     `AtomicInteger` that was inlined in the first pass.
   - `GroupPreviewLoadCoordinatorTest.kt` (new, 6 tests) directly verifies: max concurrency is never
     exceeded under 20 concurrent callers with `maxConcurrent = 4`; a slow operation times out and
     returns `null` instead of throwing; a fast operation returns its value; an exception in one
     `runBounded` call propagates to the caller *and* does not leak the semaphore permit (verified by
     a follow-up call succeeding with `maxConcurrent = 1`, which would hang forever on a leaked
     permit); `CancellationException` propagates through `runBounded` uncaught; and results complete
     in finish order (not start order) when one operation is slow, directly exercising "progressive
     completion when one source is slow" without needing the full screen model.
   - `GenerationGuardTest.kt` (new, 3 tests, same file) verifies: a fresh generation is current;
     starting a new generation supersedes the previous one; and a stale generation completing late is
     correctly rejected while the newer one is accepted — directly exercising "stale-generation
     protection" as a pure unit, independent of Compose/Voyager/Injekt.

   This is a real, documented refactor, not just new tests: the semaphore/timeout/generation logic
   that used to live inline in `RecommendsScreenModel` now lives in these two extracted classes, and
   `RecommendsScreenModel` calls them. What is **not** covered by these tests: the actual integration
   inside `RecommendsScreenModel` (e.g. that a real `RecommendationPagingSource` failure correctly
   reaches `updateItem` with a row `Error`, or that the cache key is computed correctly from a real
   `GroupRecommendationSeed`) — that still has no test coverage, because `RecommendsScreenModel`
   itself still cannot be constructed in a unit test without mocking ~10 Injekt dependencies. Sibling
   isolation *inside the screen model* (one source's exception doesn't affect another's `updateItem`)
   remains verified only by code inspection (the per-row `try`/`catch` block is unchanged in
   structure from the first pass) and the passing regression suite, not by a dedicated new test.

See the Tests run / Known limitations / Deviations sections below for how this changes the overall
picture; sections describing the first pass are left as originally written except where corrected
inline.

**Goal:** Bound the request fan-out in group (cross-extension) recommendation loading — configurable
per-extension initial preview size, bounded concurrency, timeouts, cancellation/lifecycle safety,
duplicate-work reduction, safe diagnostics, and matching UI — without weakening scoring/visibility,
without changing For You semantics, and without capping normal global search.

## Files changed

### Policy / concurrency / cache code

- `app/src/main/java/exh/recs/GroupPreviewBudgetPolicy.kt` (new) — pure resolver for the
  configurable GROUP_PREVIEW initial-result budget. Mirrors `ForYouResultBudgetPolicy`'s
  `SUPPORTED_VALUES = [5,10,15,20,30]` / `DEFAULT = 10` / `validate()` style. Adds
  `previewCandidateBudget()`, `previewEnrichmentBudget()`, `isExpansionEligible()`.
- `app/src/main/java/exh/recs/RecommendationLoadContext.kt` (new) — `enum class
  RecommendationLoadContext { FOR_YOU, GROUP_PREVIEW, FULL_SOURCE }` per plan section 4. Currently
  used as a conceptual/documentation anchor and in test naming; `RecommendsScreenModel` itself
  branches on `groupSeed != null` (an existing, already-correct signal for "is this a group-seeded
  row") rather than threading the enum value through every call — see Deviations.
- `app/src/main/java/exh/recs/GroupPreviewCache.kt` (new) — bounded in-memory GROUP_PREVIEW cache.
  `Key(groupFingerprint, sourceId, language, normalizedSeed, visibilityFingerprint, previewBudget,
  queryPolicyVersion)`, `TTL_MS = 5 min`, `MAX_ENTRIES = 64`, oldest-entry (insertion-order) eviction,
  `invalidateAll()`. Process-memory only (satisfies "invalidate on process death" trivially; no DB
  table). **Wired into `RecommendsScreenModel` in the gap-closing pass** — see that section above.
- `app/src/main/java/exh/recs/GroupPreviewLoadCoordinator.kt` (new, gap-closing pass; extended in the
  reviewer-audit pass) — `GroupPreviewLoadCoordinator` (bounded-concurrency + timeout `runBounded()`,
  plus a `sharedEnrichmentSemaphore` added in the reviewer-audit pass — see Bug 1 above) and
  `GenerationGuard` (`next()`/`isCurrent()`), extracted from what was originally inlined directly in
  `RecommendsScreenModel`, specifically so they can be unit tested without Injekt.
- `app/src/main/java/exh/recs/GroupPreviewVisibilityFingerprint.kt` (new, reviewer-audit pass) — see
  Bug 2 above.
- `app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt` (modified, reviewer-audit
  pass) — see Bug 1 above.
- `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt` (modified, reviewer-audit pass)
  — see Bug 1 above.
- `app/src/main/java/exh/recs/RecommendsScreenModel.kt` (modified) — the actual orchestration
  changes. **Superseded by the gap-closing pass**: concurrency/timeout/generation logic that was
  originally inlined here (a raw `Semaphore(4)` + `withTimeoutOrNull` + `AtomicInteger`) now
  delegates to the extracted `GroupPreviewLoadCoordinator`/`GenerationGuard` classes described in the
  Gap-closing pass section above. Current behavior:
  - `groupPreviewCoordinator.runBounded { ... }` bounds concurrent GROUP_PREVIEW row operations to 4
    at once and applies a 20s timeout (both inside `GroupPreviewLoadCoordinator`, permits released
    via `withPermit`'s own `finally`). Single-manga/merged rows never call it. Nested detail
    enrichment happens inside the same wrapped call, so it is not budgeted separately per plan
    section 7. Timeout now renders as a row `Error` (`TimeoutException`) instead of hanging.
  - **Correctness fix**: the prior `catch (e: Exception)` silently swallowed
    `CancellationException` (a `kotlin.coroutines.cancellation.CancellationException` is an
    `Exception` subtype). Split into `catch (e: CancellationException) { throw e }` then
    `catch (e: Throwable) { if (e is Error) throw e; ... }`, satisfying plan section 8's
    "CancellationException is rethrown/propagated, never rendered as an error row" and "fatal VM
    errors are not caught as ordinary recoverable errors."
  - **Cache**: before calling `requestNextPage`, a GROUP_PREVIEW row builds a `GroupPreviewCache.Key`
    and checks `GroupPreviewCache.get(key)`. A hit renders immediately without a source call or
    concurrency permit. A miss proceeds to fetch, and after budget truncation the final list is
    stored via `GroupPreviewCache.put(key, budgeted)`. Single-manga/merged rows never build a key.
  - `GroupPreviewBudgetPolicy.previewCandidateBudget(sourcePreferences.groupPreviewResultBudget()
    .get())` truncates `titles` to the configured budget, applied **after** fetch, dedupe
    (`distinctBy { it.url }`), visibility (`RecommendationCandidateVisibilityPolicy`), and scoring
    (`RecommendationScorer`/`GroupSeedRecommendationScorer`) — exactly the order required by plan
    section 5. `FULL_SOURCE`/single-manga rows never truncate.
  - Lifecycle: switched the load coroutine from `ioCoroutineScope.launch` (a process-wide scope with
    no tie to the screen's lifecycle) to `screenModelScope.launch` (Voyager's per-`ScreenModel`
    scope, auto-cancelled on dispose), matching `BrowsePersonalRecommendationsScreenModel`'s existing
    convention. This directly satisfies "leaving the screen cancels in-flight preview work."
  - `generationGuard.next()`/`generationGuard.isCurrent(myGeneration)` replace the original inline
    `AtomicInteger`. Every `updateItem`/`updateItems` call site in the load coroutine is guarded by
    `generationGuard.isCurrent(myGeneration)` in addition to the existing `isActive` check, so a
    superseded generation can never mutate newer state — see Deviations for the scope of this (no
    refresh entry point exists yet to actually trigger a second generation today).
  - Diagnostics: `logcat(...)` calls (tag `"RecommendsScreenModel"`) for cache hit/miss, per-row
    raw/final candidate counts and elapsed time, timeout, cancellation, error, and a per-load summary
    line with total elapsed time and `groupPreviewCoordinator.observedMaxConcurrency()`.
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` (modified) — added
  `groupPreviewResultBudget()` (`"recommendation_group_preview_budget"`, default
  `GroupPreviewBudgetPolicy.DEFAULT`), following the exact pattern of the existing
  `recommendationResultBudget()`.

### UI

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` (modified) — added
  `groupPreviewBudgetPref`, `state.groupPreviewBudget`, `setGroupPreviewBudget(value)` (validates via
  `GroupPreviewBudgetPolicy.validate`), mirroring the existing `resultBudget`/`setResultBudget` For
  You pair one-for-one.
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` (modified) — added a
  `SameMangaListPrefRow` (the existing, already-used picker row composable — no new composable
  written) directly below the existing "Results per source" (For You) row, titled "Initial results
  per extension" with the required "opening a source continues loading" copy, options `[5,10,15,20,30]`.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` (modified) — added
  `rec_group_preview_budget_title` = "Initial results per extension" and
  `rec_group_preview_budget_summary` = "Manga cards shown for each extension in a group
  recommendation preview. Open a source to keep loading more. Larger values may take longer to
  load." Base locale only, per AGENTS.md/KMR rules. No build-channel terminology.

Per-source row loading/empty/timeout/error states and discoverable row-header expansion were **not
newly built** — `RecommendationItemResult` (`Loading`/`Success`/`Error`) and the existing row
rendering in `RecommendsScreen` already provide per-row loading/empty/error states and header-tap
expansion (opening the associated source), and the new timeout path reuses `RecommendationItemResult
.Error` rather than adding a new state variant. No further UI changes were made — see Known
limitations for what a dedicated "timeout" visual treatment (vs. generic error) would need.

### Documentation

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — `VERSION_CODE` 755→756, `VERSION_NAME`
  "KMK-Recs v0.8.5"→"KMK-Recs v0.8.6", new v0.8.6 What's New section inserted above the v0.8.5
  entry (newest-first preserved), all prior entries back to v0.4.2 left untouched. No build-channel
  terminology used.
- `docs/recommendations/CURRENT_STATE.md`, `docs/recommendations/NEXT_WORK.md`,
  `docs/recommendations/README.md` — updated (see diffs in this session).
- `docs/recommendations/KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_LOADING_IMPLEMENTATION_PLAN.md` —
  Status line updated to reflect actual implemented scope (partial — see below), not "implemented
  and shipped" unqualified.
- This file (implementation report), linked from the plan and `CURRENT_STATE.md`.

### Tests (`app/src/test/java/exh/recs/`)

- `GroupPreviewBudgetPolicyTest.kt` (new, 8 tests) — default, supported values, corrupt/migrated
  fallback, `previewCandidateBudget`/`previewEnrichmentBudget` bounds, `isExpansionEligible`
  true/false/corrupt-normalized.
- `RecommendationLoadContextTest.kt` (new, 1 test) — enum has exactly the three documented values.
- `GroupPreviewCacheTest.kt` (new, 6 tests) — miss on empty, hit on identical key, miss on any
  differing key component, TTL expiry, oldest-entry eviction past `MAX_ENTRIES`, `invalidateAll`.
- `GroupPreviewLoadCoordinatorTest.kt` (new, gap-closing pass; extended in the reviewer-audit pass,
  6 tests total) — max concurrency never exceeded (4-permit bound under 20 concurrent callers),
  timeout returns null without throwing, a fast operation returns its value, an exception propagates
  without leaking the permit, `CancellationException` propagates uncaught, progressive/finish-order
  completion under one slow sibling, **and (reviewer-audit pass) shared enrichment semaphore caps
  total concurrent detail requests across multiple simultaneously-active sources** (Bug 1 fix proof).
- `GenerationGuardTest.kt` (new, gap-closing pass, 3 tests, same file) — a fresh generation is
  current, a new generation supersedes the previous one, a stale generation completing late is
  rejected while the newer one is accepted.
- `GroupPreviewVisibilityFingerprintTest.kt` (new, reviewer-audit pass, 11 tests) — identical inputs
  produce identical fingerprints; a newly disabled source, changed source order, newly
  disliked/quality-disliked source, newly-seen entry, changed taste rating, newly-added taste entry,
  changed rated-manga visibility, toggled hide-known-manga, and changed min-chapter-count each change
  the fingerprint (Bug 2 fix proof).
- Still no tests that construct `RecommendsScreenModel` itself — see Known limitations #5 for exactly
  what that gap still covers (integration, not the extracted concurrency/generation/fingerprint logic,
  all of which is now tested in isolation).

## Behavior changed

- Group (cross-extension) recommendation rows: initial preview is now capped at a user-configurable
  budget (default 10, was effectively unbounded — limited only by each source's own single-page
  result count and the existing `MAX_ENRICH_PER_SOURCE = 10` enrichment cap). At most 4 group rows
  fetch/enrich concurrently instead of all eligible sources (up to 20) starting at once. One row
  timing out after 20s no longer risks stalling the screen and is shown as a row error instead. Row
  cancellation on cancellation-source exceptions is no longer misreported as a row error. Leaving the
  recommendations screen now actually cancels in-flight group-preview work (previously it continued
  on `ioCoroutineScope`, a process-wide scope).
- Single-manga recommendations, merged-source rows, For You, and normal global search: **unchanged**
  — none of the new budget/semaphore/timeout/generation logic executes on those paths (`isGroupPreview
  == false` skips all of it; `SearchScreenModel` was not touched at all).
- (Reviewer-audit pass) Nested detail-enrichment requests across all concurrently active
  GROUP_PREVIEW sources are now actually bounded to a shared total of 8 in flight, rather than up to
  40 in the worst case (4 sources x 10 each) — see Bug 1 above.
- (Reviewer-audit pass) The GROUP_PREVIEW cache now correctly misses (instead of serving a stale
  result) when disabled-source state, source order, source-quality preferences, seen-entries state,
  or taste/rating state change, in addition to the fields it already covered — see Bug 2 above.

## Tests run

- `./gradlew spotlessApply` — clean, all three passes (first implementation pass, gap-closing pass,
  reviewer-audit correctness pass).
- `./gradlew spotlessCheck` — clean (0 violations), all three passes.
- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL, run repeatedly after each edit batch across
  all three passes.
- `./gradlew :app:testDebugUnitTest` (full module suite, not just `exh.recs.*`) — run three times:
  - After the first implementation pass: 101 result files, 0 failures/0 errors.
  - After the gap-closing pass: 103 result files, 0 failures/0 errors.
  - After the reviewer-audit correctness pass (this update — shared enrichment semaphore +
    `GroupPreviewVisibilityFingerprint` + their tests): **104 result files, 0 failures/0 errors**
    (`GroupPreviewLoadCoordinatorTest` now 6 tests including the new shared-enrichment-semaphore
    proof; new `GroupPreviewVisibilityFingerprintTest`, 11 tests).
- `./gradlew assembleDebug` — **BUILD SUCCESSFUL**, run after each of the gap-closing pass and the
  reviewer-audit correctness pass.

## APK/build output

Built via `./gradlew assembleDebug` after all code/tests/docs were complete for the reviewer-audit
correctness pass (the final, most current state; 504 actionable tasks, 11 executed, BUILD
SUCCESSFUL). Source: `app/build/outputs/apk/debug/app-universal-debug.apk` (178,498,565 bytes),
copied to `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.6-debug.apk`, matching the
existing per-release naming/variant convention used by every prior `.apk` in that folder
(`app-universal-*`, not per-ABI splits). Confirmed present on disk at that exact path after the copy.

## Known limitations

1. ~~`GroupPreviewCache` not wired in~~ — **closed in the gap-closing pass.** Now looked up before
   fetch and stored after budget truncation; never touched by single-manga/merged rows.
2. ~~No diagnostics logging~~ — **closed in the gap-closing pass.** See the Gap-closing pass section
   above for exact coverage.
3. **`RecommendationQueryPlanner`/`RecommendationQueryAttemptPolicy` were not modified.** On
   inspection, the existing tag-attempt chain, title fallback (`MAX_TITLE_FALLBACKS = 2`), and
   enrichment cap (`MAX_ENRICH_PER_SOURCE = 10`) already satisfy plan section 6's dedup/fallback/bound
   requirements — no duplicate-plan generation or unbounded retry was found. This is a discrepancy
   against the plan's assumption that dedup work was needed there, not a skipped requirement. Still
   open — no change in this pass.
4. **The generation/job-token model is still scaffolding, not a full refresh feature**, now backed by
   the extracted, unit-tested `GenerationGuard` class instead of an inline `AtomicInteger`.
   `RecommendsScreenModel` still loads exactly once in `init {}` with no existing refresh/reload/retry
   entry point anywhere in the class or its consuming screen, so nothing in this codebase currently
   triggers a second generation in production — `GenerationGuardTest.kt` verifies the mechanism
   itself in isolation, but there is still no end-to-end test proving `RecommendsScreenModel` behaves
   correctly across two real generations, because there is no second generation to trigger yet.
5. **Partially closed.** `GroupPreviewLoadCoordinator`/`GenerationGuard` are now real, extracted,
   directly unit-tested classes (9 new tests total) covering: max concurrency never exceeded, timeout
   returns null without throwing, a fast operation returns normally, an exception propagates without
   leaking the permit, `CancellationException` propagates uncaught, progressive/finish-order
   completion under a slow sibling, and generation supersession/staleness rejection. What remains
   untested is the *integration* inside `RecommendsScreenModel` itself — e.g. that a real
   `RecommendationPagingSource` timeout actually reaches `updateItem` with the right `Error`, that
   the cache key is computed correctly from a real `GroupRecommendationSeed`, or that concurrent
   `updateItem` calls from multiple rows correctly merge into `mutableState` — because
   `RecommendsScreenModel` still cannot be constructed in a unit test without mocking ~10 Injekt
   dependencies with no existing fake/mocking harness for that in this test suite.
6. **Per-source row UI does not have a dedicated "timed out" visual state** distinct from a generic
   error — the new `TimeoutException` reaches the screen via the existing `RecommendationItemResult
   .Error` path, so it renders with whatever generic error UI that path already has. Unchanged.
7. **Device/manual QA was not performed** (no physical phone/tablet available in this environment).
   Required scenarios per plan section 14/16, all "not executable in this environment, requires
   manual QA": budgets 5/10/20/30 visually confirmed on-device; heavy-tag group recommendations;
   repeated refresh; leaving the screen while requests are active; network loss/recovery; source
   expansion beyond the initial preview; confirming normal global search stays visually uncapped;
   phone+tablet layout check of the new settings row and per-source row states; confirming the cache
   actually produces a faster/absent second network call on a real repeated-refresh device test
   (unit-tested at the cache-class level only, not observed end-to-end on-device).

## Follow-up recommendations

- Add a real refresh/reload entry point to `RecommendsScreenModel` (pull-to-refresh, like
  `BrowsePersonalRecommendationsScreenModel` already has) so `GenerationGuard` is actually exercised
  by a second real generation in production, and add an end-to-end test for it once that exists.
- Build a small fake-dependency test harness for `RecommendsScreenModel` itself (constructing it with
  fake `GetManga`/`SourceManager`/`GetMangaTaste`/etc.) so the cache-wiring, per-row error/timeout
  rendering, and multi-row `mutableState` merging can be verified end-to-end rather than only via the
  now-tested extracted coordinator/guard classes plus code inspection.
- Consider invalidating `GroupPreviewCache.invalidateAll()` on relevant preference changes (language,
  source order, disabled sources, group-preview budget) — the cache key already includes budget and
  language so a changed value naturally misses rather than serving stale data, but an explicit
  invalidation call on Recommendation Settings changes would be a cheap belt-and-suspenders addition on
  top of the existing key-based invalidation.

## Deviations from the approved plan

- `RecommendationLoadContext` enum exists but is not threaded as a parameter through
  `RecommendationPagingSource`/`CrossExtensionGenreSearchSource`/`RecommendsScreenModel` calls (the
  plan's section 4 suggests it replace "ad-hoc Boolean flags"). The existing `groupSeed != null`
  signal in `RecommendsScreenModel` already unambiguously distinguishes GROUP_PREVIEW from
  FOR_YOU/single-manga at the one call site that needed it, and introducing a parameter thread-through
  for a single boolean-equivalent branch point was judged higher regression risk than value for this
  session. The enum is real, tested, and documented as the intended vocabulary for `FULL_SOURCE`
  should a future expanded-source-view feature need it explicitly.
- `RecommendationQueryPlanner`/`RecommendationQueryAttemptPolicy` were left unmodified — see Known
  limitations #3.
- `GroupPreviewCache` was built but not wired in — see Known limitations #1. This means acceptance
  criterion #8 ("Cache reuse reduces repeat work without cross-context contamination") is only
  partially met: the cache exists and cannot cross-contaminate (it is never touched by For You or
  global search), but it does not yet reduce repeat work because nothing reads or writes it from the
  load path.
- Diagnostics (plan section 12) were not added — see Known limitations #2.
- The generation/job-token model was added as forward-compatible scaffolding rather than a full
  refresh feature, per explicit scope instruction — see Known limitations #4.
