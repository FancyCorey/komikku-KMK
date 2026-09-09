# KMK-Recs v0.7.40 For You And Rated-Group Policy Foundation Plan

Date: 2026-07-11

Status: COMPLETE. APK: Komikku-v1.13.6-kmk.7.40-debug.apk. All four deliverables implemented, spotlessApply/spotlessCheck passed, 267 unit tests pass, assembleDebug succeeded.

Version family: KMK-Recs `v0.7.40`.

Parent audits:

- `KMK_RECS_COMPLETE_SYSTEM_EVALUATION_AND_IMPROVEMENT_AUDIT.md`
- `KMK_RECS_CENTRALIZATION_AND_DATA_LIFECYCLE_AUDIT.md`
- `KMK_RECS_CENTRALIZATION_PHASE_1_TARGET_CODE_AUDIT.md`

## 1. Approved Scope

This release fixes known personalized-recommendation correctness problems and introduces the smallest shared policies needed by both Browse > For You and rated-group recommendations.

It has four deliverables:

1. Newly discovered extra-page candidates compete for display during the same For You refresh.
2. Retryable extra-page source failures do not permanently skip a discovery page; retries remain bounded and cancellable.
3. Rated-group recommendations use the same recommendation-language, priority, disabled-source, disliked-source, known/seen/rated visibility, and minimum-chapter semantics as For You, while retaining their own small source/result/timeout caps.
4. Live For You, cache/memory ranking, and rated-group flows use one candidate eligibility/visibility contract with typed reasons.

## 2. Explicit Non-Goals

Do not include any of the following in v0.7.40:

- Source catalogue fit versus retrieval compatibility redesign.
- New source-evaluation schema beyond discovery-progress retry metadata.
- Publication year, latest chapter date, remote universal chapter count, image quality, or source-native recommendation adapters.
- New user-facing filters or refresh-effort presets.
- Changes to normal global search behavior.
- Changes to cross-extension matching result caps/default selection.
- New backup/sync fields for derived cache/progress data.
- A cleanup sweep of all old local network manga rows.
- Broad architecture/refactor work outside files named in this plan.

## 3. Required Reading Before Editing

Claude must read these files completely before changing code:

```text
AGENTS.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/KMK_RECS_CENTRALIZATION_AND_DATA_LIFECYCLE_AUDIT.md
docs/recommendations/KMK_RECS_CENTRALIZATION_PHASE_1_TARGET_CODE_AUDIT.md
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt
app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt
app/src/main/java/exh/recs/memory/RecommendationDiscoveryProgressStore.kt
app/src/main/java/exh/recs/RecommendationSourceFilter.kt
app/src/main/java/exh/recs/RecommendationSourceOrdering.kt
app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt
domain/src/main/java/tachiyomi/domain/taste/model/RecommendationDiscoveryProgress.kt
data/src/main/sqldelight/tachiyomi/data/recommendation_discovery_progress.sq
```

Also inspect the nearest existing unit tests before adding tests:

```text
app/src/test/java/exh/recs/memory/RecommendationDiscoveryPlannerTest.kt
app/src/test/java/exh/recs/memory/RecommendationCandidateMemoryRankerTest.kt
app/src/test/java/exh/recs/ForYouVisibilityFilterTest.kt
app/src/test/java/exh/recs/RecommendationSourceOrderingTest.kt
app/src/test/java/exh/recs/group/GroupRecommendationLoopPolicyTest.kt
app/src/test/java/exh/recs/group/GroupSeedEnrichmentTest.kt
app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt
```

Follow `AGENTS.md`: KMR strings only for new KMK UI text, Injekt DI, SQLDelight migrations, KMK marker blocks, Spotless verification, and no changes to non-base locale files.

## 4. Current Behavior To Preserve

### For You source selection

`BrowsePersonalRecommendationsScreenModel.load()` currently:

1. builds `TasteProfile` and aliases;
2. filters visible catalogue sources by recommendation language and excludes Local Source;
3. applies stored source ordering and disabled/disliked source exclusions;
4. boosts the first three eligible sources;
5. attempts sources in priority order, batches of five, until 20 useful rows or 40 attempts;
6. uses source-specific search and Top Picks derived from fetched rows.

This behavior must remain unchanged for the For You screen in v0.7.40, except where a shared selector replaces duplicated selection logic without changing its outcome.

### For You visibility semantics

Current For You semantics to preserve:

- Seen entries are always hidden.
- Favorite/library/rated visibility uses `RatedMangaVisibility` and `shouldHideForYou`.
- `hideKnownManga` additionally hides local known/library/history entries through `GetKnownRecommendationMangaIds`.
- Blocked tags are hard-rejected by `PersonalRecommendationScorer`.
- Minimum chapter count filters only locally known nonzero chapter counts below the configured threshold; unknown counts pass.
- Database failures in known/chapter lookup fail open with a warning rather than crashing the feed.

### Group-recommendation semantics to preserve

- Seed is built from the current manga and confirmed cross-source link group.
- Seed members are excluded from results.
- Group seed is contextual only; it must not persistently mutate user taste.
- Max five sources, eight raw candidates per source, target 20 results, per-search/localization timeout, and 45-second total timeout remain group-flow caps.
- Normal global search remains unchanged.

## 5. Deliverable A: Same-Refresh Discovery Merge

### Current defect

In `BrowsePersonalRecommendationsScreenModel.searchSource()`:

```text
remembered is loaded before the live search
page one produces recommendations
discoverAdditionalPage produces additionalResults
additionalResults is persisted
if resolved remembered list is empty -> return only page-one recommendations
```

The additional-page results are not eligible to display until a later refresh.

### Required change

Create one final merge/rank operation that receives all of:

- resolved remembered candidates;
- page-one candidates from this refresh;
- additional-page candidates from this refresh;
- current profile, alias map, taste map, visibility settings, seen keys, known IDs, and chapter filter context.

The final operation must run even when remembered candidates are empty. It must deduplicate by local manga identity before ranking and return the normal source display limit.

### Exact code targets

Modify:

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
  searchSource(...)
  code currently around mergedRecommendations / additionalResults

app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt
  merge(...)
```

Do not duplicate ranking logic in `searchSource`. Extend or replace `RecommendationCandidateMemoryRanker.merge()` so it is the one final merge-and-visibility implementation for live+remembered candidates.

### Required invariant

For equivalent inputs, the final eligibility/ranking result must not depend on whether a candidate came from page one, page two, cache, or memory.

## 6. Deliverable B: Typed Discovery Retry Policy

### Current defect

`discoverAdditionalPage()` records all non-cancellation failures as `STATUS_ERROR`; `RecommendationDiscoveryPlanner.nextPageToProbe(evaluatedPages)` only sees a page number set and advances past it forever.

### Required data-model change

Add migration `58.sqm` only after confirming `57.sqm` is the current latest migration. Do not edit an existing migration.

Extend `recommendation_discovery_progress` with, at minimum:

```text
attempt_count INTEGER NOT NULL DEFAULT 0
next_retry_at INTEGER
failure_kind TEXT
```

Add any index needed for due retry lookup only if the final query actually uses it. Do not add speculative indexes.

Update all required layers:

```text
data/src/main/sqldelight/tachiyomi/data/recommendation_discovery_progress.sq
data/src/main/sqldelight/tachiyomi/migrations/58.sqm
domain/src/main/java/tachiyomi/domain/taste/model/RecommendationDiscoveryProgress.kt
domain/src/main/java/tachiyomi/domain/taste/repository/RecommendationDiscoveryProgressRepository.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/GetRecommendationDiscoveryProgress.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertRecommendationDiscoveryProgress.kt
data/src/main/java/tachiyomi/data/taste/RecommendationDiscoveryProgressRepositoryImpl.kt
app/src/main/java/eu/kanade/domain/KMKDomainModule.kt
app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt
```

Use project SQLDelight conventions and regenerate interfaces through the normal Gradle build/codegen path. Do not hand-edit generated files.

### Typed outcomes

Replace the current effectively free-form status handling with stable constants/enums in the domain model. At minimum distinguish:

```text
SUCCESS
EMPTY
FILTERED
DUPLICATE_ONLY
UNSUPPORTED
RETRYABLE_FAILURE
PERMANENT_FAILURE
EXHAUSTED
```

`CancellationException` is never an outcome. It must be rethrown and no progress record written for that abandoned request.

### Retry classification

Implement a small pure classifier in `app/src/main/java/exh/recs/memory/` or the closest existing domain-appropriate package. It must:

- classify `IOException`, socket timeout, DNS/connectivity failures, and explicit coroutine timeout as retryable;
- classify known unsupported-operation behavior as permanent/unsupported;
- treat unknown extension exceptions conservatively as permanent after recording a diagnostic, unless evidence supports retryability;
- never catch/reclassify `CancellationException`.

Use a bounded retry policy:

```text
maximum retryable attempts: 3
initial retry delay: 5 minutes
subsequent delay: bounded exponential backoff, maximum 24 hours
```

These values must be constants with documentation and unit tests. The user can still reset discovery history manually; no retry loop may run automatically in the background.

### Planner redesign

Change `RecommendationDiscoveryPlanner.nextPageToProbe(...)` so it receives the full progress records plus current time, not just `Set<Int>`.

Required selection order:

1. the lowest due retryable-failure page that has attempts below the cap;
2. otherwise the next never-evaluated page after the highest terminal/evaluated page;
3. otherwise `null` when the page cap is reached or no page is due.

Do not advance past a retryable failure until it succeeds, becomes terminal, or reaches retry exhaustion. On retry exhaustion, persist `EXHAUSTED` with the diagnostic preserved.

### Timeout behavior

Wrap the additional-page `source.getSearchManga(...)` call in a bounded timeout using the existing coroutine style. Choose and document one constant. Keep it local to this additional-page path; do not silently change page-one behavior in this release.

### UI behavior

No new user-facing UI is required in v0.7.40. Existing source status can remain unchanged. Retry metadata stays diagnostic/local. Do not expose raw exception messages in For You rows.

## 7. Deliverable C: Shared Source Selector

### New helper

Create a small, testable `RecommendationSourceSelector` in `app/src/main/java/exh/recs/` using the existing `RecommendationSourceFilter` and `RecommendationSourceOrdering` rather than replacing them.

It must be a policy composer, not a new source registry. It receives:

- visible catalogue sources;
- recommendation language set;
- stored order;
- disabled source IDs;
- disliked installed source IDs;
- policy values for Local Source and maximum source count.

It returns the selected ordered sources. An optional diagnostics result is allowed only if it directly supports tests/logging; do not add user-facing data solely for future ideas.

### For You adoption

In `BrowsePersonalRecommendationsScreenModel.load()`, replace its local composition of language filtering + ordering + disabled/disliked merge with the selector. Preserve resulting source order exactly for existing inputs.

### Rated-group adoption

Modify `GroupSeededRecommendationsScreenModel`:

- inject/read `SourcePreferences` and `GetDisabledRecommendationSources` using existing Injekt conventions;
- parse disliked installed source IDs with the existing `RecommendationSourcePreferenceStore` helper;
- read `recommendationSourceLanguages()` instead of passing `emptySet()`;
- get visible sources at load time;
- call the shared selector;
- take the first `MAX_SOURCES` only after priority/exclusions are applied.

Do not apply For You boosted result-size behavior to the group flow. The group flow keeps its own `MAX_SOURCES`, raw cap, target, and timeouts.

## 8. Deliverable D: Shared Candidate Visibility Policy

### New helper boundary

Create a pure `RecommendationCandidateVisibilityPolicy` in `app/src/main/java/exh/recs/` or a closer domain package only if the existing module layering requires it. It must not perform network or database work.

Inputs must include:

- manga;
- `tasteByKey` or a resolved manga taste;
- `RatedMangaVisibility`;
- seen keys;
- known manga IDs already loaded by the caller;
- hide-known flag;
- optional chapter count and configured minimum chapter count;
- explicit suppressed keys, used for group seed members;
- whether favorite/library is treated as immediately hidden (preserve For You behavior).

Output must be a typed result:

```text
VISIBLE
HIDDEN_FAVORITE
HIDDEN_RATED
HIDDEN_SEEN
HIDDEN_KNOWN
HIDDEN_MIN_CHAPTERS
HIDDEN_SEED_MEMBER
```

Blocked-tag rejection stays in `PersonalRecommendationScorer` in this release. Do not duplicate it in the visibility helper.

### For You adoption

Refactor the live search, cache load, additional-page discovery, and candidate-memory merge paths to use the same policy result. Preserve current fail-open behavior when known/chapter database lookups fail. The screen model remains responsible for batching DB calls before passing sets/maps into the pure policy.

### Rated-group adoption

`GroupSeededRecommendationsScreenModel.buildRecommendations()` must:

1. retain existing network seed-member exclusion before localization;
2. localize only its bounded raw candidates;
3. batch known-manga IDs and chapter counts per source/plan candidate batch before visibility evaluation; do not issue one known lookup per candidate;
4. load taste map, seen keys, rated visibility, hide-known setting, and chapter minimum once per screen load;
5. apply the shared policy before scoring/appending;
6. preserve current fail-open database behavior with warning logs;
7. keep the group seed itself and all confirmed seed members suppressed.

Do not make group recommendations silently hide all candidates because metadata is absent. Unknown chapter count passes exactly as For You does.

## 9. Score Eligibility Alignment

The final merged For You candidate path and group path must use the same explicit positive-score eligibility rule:

```text
blocked -> hidden by scorer
score <= 0 -> not a normal personalized recommendation
score > 0 -> eligible, subject to visibility and result cap
```

Implement this in one obvious location used by live and memory merge. Do not leave live page-one behavior capable of displaying zero/negative-score entries while memory removes them later.

This release does not add a user-facing "show low-confidence candidates" option.

## 10. Exact Test Plan

### New/extended pure tests

```text
app/src/test/java/exh/recs/memory/RecommendationDiscoveryPlannerTest.kt
app/src/test/java/exh/recs/memory/RecommendationCandidateMemoryRankerTest.kt
app/src/test/java/exh/recs/RecommendationSourceSelectorTest.kt             (new)
app/src/test/java/exh/recs/RecommendationCandidateVisibilityPolicyTest.kt  (new)
app/src/test/java/exh/recs/group/GroupRecommendationSourcePolicyTest.kt    (new or extend existing group test)
app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt
```

### Required assertions

Discovery:

- empty memory + page one + page two returns candidates from both pages in one final ranking;
- duplicate ID across memory/current pages appears once;
- score `0.0` and negative score candidates are excluded consistently;
- retryable error is selected again only after `nextRetryAt` and before retry cap;
- permanent/unsupported failure advances without repeated retry;
- retry exhaustion becomes terminal;
- cancellation creates no progress record.

Source selection:

- priority order retained;
- disabled and disliked IDs excluded;
- selected language used;
- Local Source excluded;
- group cap occurs after ordering/exclusion.

Visibility:

- Love/Like/Dislike under every `RatedMangaVisibility` mode;
- seen always hidden;
- known only hidden with hide-known enabled;
- favorite hidden;
- chapter count behavior: known below threshold hidden, zero/unknown passes;
- seed-member key hidden in group context;
- policy returns the correct typed reason.

Migration:

- migration 58 upgrades from the prior latest schema;
- required retry columns exist with expected defaults;
- current migration range/count constants are updated without weakening previous migration checks.

### Device QA after unit verification

On a phone/tablet with several installed sources:

1. Run For You twice and verify a newly discovered page-two candidate can appear on the second live discovery pass without requiring a third refresh.
2. Disconnect/reconnect during extra-page discovery; verify no crash, no endless retry, and later retry behavior follows delay/reset expectations.
3. Disable or dislike a high-priority source; verify For You and group recommendations both exclude it.
4. Mark a group recommendation Seen/Love/Like/Dislike; verify the next group search follows the same visibility settings as For You.
5. Verify no normal global-search behavior changed.

## 11. Documentation and Versioning

At completion, Claude must create:

```text
docs/recommendations/KMK_RECS_V0_7_40_FOR_YOU_AND_RATED_GROUP_POLICY_FOUNDATION_IMPLEMENTATION.md
```

It must record every actual file changed, any deviation from this plan, migration number, test commands/results, APK path, and remaining limits.

Update only after implementation is verified:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
docs/KMK_MARKDOWN_ENCYCLOPEDIA.md
docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md   (only if relevant data lifecycle/schema scope changes)
docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md  (only if retention/diagnostic handling changes)
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
RECOMMENDATION_VERSIONING.md
```

Use `KMK-Recs v0.7.40` consistently. Preserve the established separate app build version versus KMK feature version convention.

## 12. Implementation Order for Claude

Claude may work in internal phases but must not create/copy the final APK until all are complete.

1. Inspect actual code against this plan; document any material mismatch before changing behavior.
2. Add pure source-selection and visibility policies plus tests.
3. Adopt policies in For You with behavior-preservation tests.
4. Adopt policies in rated-group flow and add group tests.
5. Add migration 58 plus typed retry planner/progress changes and tests.
6. Fix same-refresh candidate merge and score eligibility alignment.
7. Run formatting, full relevant unit tests, migration tests, then final build.
8. Write implementation report and update state/version documentation.

## 13. Mandatory Verification Commands

Run from the repository root after all code changes:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

If database schema changes require it, run the normal SQLDelight generation/build task rather than editing generated output.

Do not call the release complete, update final release notes, or hand off an APK if any command fails.

## 14. Claude Stop Conditions

Claude must stop and report instead of guessing if:

- migration 57 is not the latest actual migration;
- an existing helper already provides the intended policy under another name;
- a shared policy would change normal global-search semantics;
- a database query cannot be batched without causing a material behavior regression;
- device-only behavior or extension API semantics contradict this plan;
- tests expose an existing incompatibility with backup/sync or source evaluation;
- the proposed retry classifier cannot safely distinguish cancellation from failure.

