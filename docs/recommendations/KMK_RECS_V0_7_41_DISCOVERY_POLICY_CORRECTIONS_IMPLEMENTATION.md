# KMK-Recs v0.7.41 Discovery Policy Corrections Implementation

Date: 2026-07-11

Status: COMPLETE â€” all four corrections implemented, verified, and documented.

Version family: KMK-Recs `v0.7.41` (`KmkRecsReleaseNotes.VERSION_CODE = 741`).

Plan: `docs/recommendations/KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_AND_SOURCE_EVIDENCE_ROADMAP_PLAN.md` (sections 2.Aâ€“2.D only; the v0.7.42 source-evidence redesign in section 7 was planning scope only at the time this v0.7.41 report was written. It was later implemented separately â€” see `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md` and its v0.7.42-fix1 follow-up, `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md`).

## Scope

This is a corrective release for verified gaps left after v0.7.40. v0.7.40 introduced the shared
`RecommendationSourceSelector` and `RecommendationCandidateVisibilityPolicy` foundation; v0.7.41 makes
that foundation the single, consistent contract across every For You and rated-group path and fixes the
retry/planner semantics that v0.7.40 left partially implemented.

No database migration was added. `STATUS_EXHAUSTED` already exists in
`RecommendationDiscoveryProgress`, so no migration 59 was needed (per plan Â§3).

## Corrections implemented

### A. One candidate-visibility contract in every For You path

- `RecommendationCandidateMemoryRanker.merge()` now takes `minChapterCount` + `chapterCounts` and
  decides visibility through `RecommendationCandidateVisibilityPolicy.evaluate()` instead of calling
  `shouldHideForYou` and inline favorite/seen/known checks. This closes the gap where a candidate
  hidden by the live min-chapter filter could reappear through discovery memory.
- `loadFromCache()` now resolves all cached manga first, batches one `GetKnownRecommendationMangaIds`
  and (only when the minimum is active) one `GetChapterCountsByMangaIds` lookup, and applies the shared
  policy to every resolved cached manga. It accepts a new `minChapterCount` parameter.
- Both merge call sites in `searchSource()` (cached path and live page-one + extra-page path) now batch
  chapter counts across cache/new + memory candidates and pass them into `merge()`, so remembered memory
  candidates are min-chapter filtered by the same policy.
- Fail-open preserved: failed known-id or chapter-count lookups log a warning and use empty data;
  unknown chapter counts stay visible.

Files: `RecommendationCandidateMemoryRanker.kt`, `BrowsePersonalRecommendationsScreenModel.kt`.

### B. Group recommendations no longer exhaust the budget on hidden entries

`GroupSeededRecommendationsScreenModel.buildRecommendations()` was rebuilt from a
"collect `TARGET_RESULTS * 2` scored, then filter" approach into a **chunked collect-then-filter loop**:

- For each source/plan chunk: localize + score candidates (no DB work in the inner loop), run **one**
  batched chapter-count and known-id lookup for that chunk, then apply
  `RecommendationCandidateVisibilityPolicy`.
- Only VISIBLE candidates are added to a new pure `GroupRecommendationLoopPolicy.VisibleResultAccumulator`,
  which deduplicates by localized manga id and keeps the highest score. Hidden candidates (seed member,
  favorite, rated, seen, known, min-chapter) never increment the budget.
- The scan continues until 20 visible candidates are collected (`accumulator.isFull`) or the existing
  source/plan bounds are exhausted. All hard limits are unchanged: 5 sources, 8 raw per source/plan,
  12 s search timeout, 5 s localize timeout, 45 s total timeout, final cap of 20.
- Batch-lookup failures fail open (candidate retained) and are logged once per pass.

Files: `GroupSeededRecommendationsScreenModel.kt`, `GroupRecommendationLoopPolicy.kt` (new
`VisibleResultAccumulator`).

### C. Retry state is truthful and bounded

- `discoverAdditionalPage()` now computes the terminal retry state after the probe resolves. When a
  retryable failure uses its final allowed attempt (attempt count reaches `MAX_ATTEMPTS = 3`), it persists
  `status = STATUS_EXHAUSTED`, `attemptCount = MAX_ATTEMPTS`, the retained/truncated diagnostic,
  `failureKind = FAILURE_KIND_RETRYABLE`, and `nextRetryAt = null`. Earlier retryable failures schedule an
  exponential backoff; permanent failures never schedule a retry.
- `RecommendationDiscoveryPlanner.nextPageToProbe()` now selects the lowest still-pending retryable-error
  page **before** applying the page cap, so a due retry of page 20 is allowed while a NEW page 21 is never
  created. A retryable error still waiting for its retry time blocks advancement; exhausted / permanent /
  unsupported / success / empty / filtered / duplicate records are advanceable. A `null` `nextRetryAt` on a
  pending retryable record is treated as "due now".
- Cancellation is rethrown before any progress write, so it is never recorded as a failed retry.

Files: `BrowsePersonalRecommendationsScreenModel.kt`, `RecommendationDiscoveryPlanner.kt`.

### D. Conservative unknown-error classification

`RecommendationRetryClassifier.classify()` now returns retryable only for `IOException`,
`UnknownHostException`, and `SocketTimeoutException` (connectivity/I/O/timeout). `UnsupportedOperationException`,
HTTP 4xx (matched before `IOException`, since extensions often wrap client errors in `IOException`), and all
unknown/runtime exceptions are permanent. The explicit local probe timeout path in `discoverAdditionalPage()`
is classified retryable at the call site. Cancellation is never classified here.

Files: `RecommendationRetryClassifier.kt`.

## Files changed

Production:
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt`
- `app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt`
- `app/src/main/java/exh/recs/memory/RecommendationRetryClassifier.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationLoopPolicy.kt`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

`RecommendationCandidateVisibilityPolicy.kt` was reused unchanged (it was already the shared contract).
No domain/data/migration files were changed.

Tests:
- `app/src/test/java/exh/recs/memory/RecommendationRetryClassifierTest.kt` (NEW â€” 12 tests)
- `app/src/test/java/exh/recs/memory/RecommendationDiscoveryPlannerTest.kt` (18 tests; +5 retry/cap tests,
  exhausted-record helper, max-attempts test now uses `STATUS_EXHAUSTED`)
- `app/src/test/java/exh/recs/memory/RecommendationCandidateMemoryRankerTest.kt` (13 tests; +3 min-chapter)
- `app/src/test/java/exh/recs/group/GroupRecommendationLoopPolicyTest.kt` (19 tests; +5 accumulator)
- `app/src/test/java/exh/recs/RecommendationCandidateVisibilityPolicyTest.kt` (16 tests; +2 shared-contract)

## Regression tests â†’ plan Â§4 mapping

| Plan requirement | Test |
|---|---|
| 1. Cached candidate below min chapter is hidden | policy `HIDDEN_MIN_CHAPTERS`; cache path uses the policy (covered by policy + ranker min-chapter tests) |
| 2. Memory candidate below min chapter is hidden | `RecommendationCandidateMemoryRankerTest.memory candidate below min chapter count is hidden` |
| 3. Unknown/missing chapter count stays visible | ranker `unknown chapter count stays visible - fail open`; policy `unknown chapter count` |
| 4. Cache/memory/live same visibility result | `RecommendationCandidateVisibilityPolicyTest.same candidate and context yield the same decisionâ€¦` (single shared function) |
| 5. Group continues after hidden candidates within bounds | `GroupRecommendationLoopPolicyTest.accumulator continues collecting across chunksâ€¦` / `only counts added visible candidatesâ€¦` |
| 6. Due retryable page 20 selected; new page 21 never | `due retryable failure on cap page 20 is selected`; `new page past cap 21 is never created` |
| 7. Third retryable failure records STATUS_EXHAUSTED | terminal-state logic in `discoverAdditionalPage`; planner `exhausted retry record advances to next page` proves exhausted is advanceable |
| 8. Exhausted/permanent advance; not-due retryable blocks | `permanent failure â€¦ advances`, `exhausted retry record advances`, `retryable failure before its due time blocks advancement` |
| 9. Unknown exception permanent; I/O + timeout retryable | `RecommendationRetryClassifierTest` (IOException/UnknownHost/SocketTimeout retryable; Unsupported/HTTP4xx/unknown permanent) |
| 10. Cancellation creates no progress record | enforced by `if (e is CancellationException) throw e` before any `recordProgress`; classifier never sees cancellation |

## Verification

JDK confirmed before running Gradle: **Temurin 17.0.19+10** (`JAVA_HOME` =
`C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10`). Java 8 was not used; the
verification below is trustworthy.

| Command | Result |
|---|---|
| `.\gradlew.bat spotlessApply` | BUILD SUCCESSFUL |
| `.\gradlew.bat spotlessCheck` | BUILD SUCCESSFUL |
| `.\gradlew.bat :app:testDebugUnitTest` | BUILD SUCCESSFUL (267 actionable tasks; all recs tests pass) |
| `.\gradlew.bat assembleDebug` | BUILD SUCCESSFUL |

New/updated test-class counts (from `app/build/test-results/testDebugUnitTest/`):
`RecommendationRetryClassifierTest` 12, `RecommendationDiscoveryPlannerTest` 18,
`RecommendationCandidateMemoryRankerTest` 13, `GroupRecommendationLoopPolicyTest` 19,
`RecommendationCandidateVisibilityPolicyTest` 16 â€” all with 0 failures / 0 errors.

APK: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.41-debug.apk`
(copied from `app/build/outputs/apk/debug/app-universal-debug.apk`).

## Known limitations / follow-ups (resolved 2026-07-11)

- At the time of v0.7.41, the v0.7.42 Source Evidence Redesign (plan Â§7) had not been started â€”
  Source Evaluation still pooled catalogue samples with tag-search probes. It was later implemented
  separately in `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`, with a follow-up
  correction pass in `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md`.
- Group per-chunk batch lookups add a small number of extra DB round-trips versus the previous single
  post-loop batch, but each remains a bounded batched call (no per-candidate N+1).
- ~~`discoverAdditionalPage()` called `RecommendationCandidateVisibilityPolicy.evaluate()` with
  `knownIds = emptySet()`, so extra-page discovery did not have the full hide-known context.~~
  **Fixed by the follow-up below.**

---

## Follow-up (2026-07-11): extra-page discovery known-context gap

Confirmed by a Codex review of the v0.7.41 implementation. Unit tests passed on the repo-local JDK 17,
but one policy-context gap remained: `discoverAdditionalPage()` called
`RecommendationCandidateVisibilityPolicy.evaluate(...)` with `knownIds = emptySet()`, regardless of the
user's hide-known-manga setting. The final merge (`RecommendationCandidateMemoryRanker.merge()`) still
filtered known candidates before they could appear as visible rows, so this was not a visible-leak bug.
But it meant extra-page **progress records** and **candidate memory** could treat a known-only page as
`STATUS_SUCCESS` with a non-zero `visibleCount`, and store known candidates into
`recommendation_candidate_memory` â€” both untruthful relative to what a live page-one probe with the same
setting would have recorded.

### Fix

`discoverAdditionalPage()` now takes a `hideKnownManga: Boolean` parameter. After extra-page candidates
are localized, when `hideKnownManga` is enabled it batch-loads known IDs with one
`GetKnownRecommendationMangaIds.await(localizedIds)` call (fail-open: a lookup failure logs a WARN and
falls back to `emptySet()`, keeping candidates visible rather than crashing or blanking the row â€” the
same fail-open shape already used by the page-one path). Those known IDs are passed into
`RecommendationCandidateVisibilityPolicy.evaluate(...)` exactly as page-one, cache, memory-merge, and
group recommendations already do.

Because `localizedCount`, `filteredCount`, `scoredCount`, `visibleCount`, `progressStatus`, the returned
recommendations, and the candidates later passed to `memoryStore.upsertBatch(...)` are all derived from
the post-filter `localized` list, wiring the real known IDs into that one filter step automatically makes
all of them reflect the fully filtered result â€” no separate changes were needed at each of those call
sites. A known-only additional page now filters down to an empty list and is recorded as
`STATUS_EMPTY`/`STATUS_FILTERED` (matching the existing empty/filtered status logic), never
`STATUS_SUCCESS`, and no known candidates are written to candidate memory.

**Refactor for testability:** the identical `candidates.filter { RecommendationCandidateVisibilityPolicy
.evaluate(...) == CandidateVisibility.VISIBLE }` block that existed separately in the page-one path and
the extra-page path was extracted into one new top-level pure function,
`filterVisibleCandidates(candidates, tasteByKey, visibility, seenKeys, knownIds, minChapterCount,
chapterCounts): List<Manga>`, and both call sites now use it. This removes the duplication and gives a
directly unit-testable seam for this specific regression, since `discoverAdditionalPage()` itself is a
private suspend function with heavy DI (`CatalogueSource`, `NetworkToLocalManga`,
`GetKnownRecommendationMangaIds`, coroutine dispatcher) that is not practical to unit test directly.

**Call-site cleanup:** the post-`discoverAdditionalPage()` block in `searchSource()` previously re-fetched
known IDs over `additionalResults.first` to build `allKnownIds` for the merge
(`knownIds + getKnownMangaIds.await(additionalResults.first.map { it.manga.id })`). Since
`additionalResults.first` is now already known-filtered before being returned, that second lookup would
always return an empty set â€” it was replaced with `allKnownIds = knownIds` (page-one's already-computed
known IDs), removing one redundant DB round-trip per source per refresh.

### Files changed

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
  - New top-level `filterVisibleCandidates(...)` pure function (used by both page-one and
    extra-page filtering).
  - `discoverAdditionalPage(...)` gained a `hideKnownManga: Boolean` parameter and now batch-loads
    known IDs for extra-page candidates (fail-open) before calling the shared policy.
  - Call site in `searchSource()` passes `hideKnownManga = hideKnownManga` into
    `discoverAdditionalPage(...)` and simplifies the now-redundant `allKnownIds` computation to
    `knownIds`.
  - Docstring on `discoverAdditionalPage` updated to describe the known-context parity and stale
    `evaluatedPages` reference corrected.

### Tests added

- `app/src/test/java/exh/recs/BrowsePersonalRecommendationsFilterTest.kt` (NEW â€” 7 tests) covering
  `filterVisibleCandidates`:
  - known candidate excluded when hide-known context is provided;
  - an all-known page filters down to empty (proves it cannot be recorded as successful/visible);
  - empty `knownIds` (hide-known disabled, or a failed lookup that fell back to `emptySet()`) keeps
    candidates visible â€” fail-open;
  - min-chapter, seen, rated (disliked), and favorite rules each still apply correctly when combined
    with a non-empty known-id set, proving the existing rules are unchanged by this fix.

Per plan requirement 8, the "known lookup failure fails open" behavior in `discoverAdditionalPage()`
itself is verified by code inspection (identical `runCatching { ... }.getOrElse { logWarn; emptySet() }`
shape already used and tested at the page-one call site) rather than a second mocked suspend-function
test, since exercising the private suspend function directly would require mocking `CatalogueSource`,
`NetworkToLocalManga`, and the coroutine dispatcher with no additional behavioral coverage over the pure
`filterVisibleCandidates` tests above.

### Verification (follow-up)

JDK confirmed before running Gradle: **Temurin 17.0.19+10**
(`$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'`;
`$env:PATH="$env:JAVA_HOME\bin;$env:PATH"`). `java -version` resolved to 17.0.19; Java 8 was not used.

| Command | Result |
|---|---|
| `.\gradlew.bat spotlessApply` | BUILD SUCCESSFUL |
| `.\gradlew.bat spotlessCheck` | BUILD SUCCESSFUL |
| `.\gradlew.bat :app:testDebugUnitTest` | BUILD SUCCESSFUL (267 actionable tasks; all recs tests pass) |
| `.\gradlew.bat assembleDebug` | BUILD SUCCESSFUL |

`BrowsePersonalRecommendationsFilterTest`: 7/7 passed, 0 failures, 0 errors (from
`app/build/test-results/testDebugUnitTest/TEST-exh.recs.BrowsePersonalRecommendationsFilterTest.xml`).

APK rebuilt and re-copied: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.41-debug.apk`
(copied from `app/build/outputs/apk/debug/app-universal-debug.apk`; same v0.7.41 version â€” this is a
within-release correction, not a new feature version).

### Remaining limitations

None outstanding for this follow-up. At the time of writing, the v0.7.42 Source Evidence Redesign was
still planning-only and unaffected by this fix; it was implemented later (see
`KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`).

