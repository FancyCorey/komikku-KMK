# KMK-Recs v0.4.4 Status Polish And Beta Hardening Plan

Date: 2026-06-14

Status: implementation plan only. Do not implement until the user explicitly approves and requests coding.

Target feature version: `KMK-Recs v0.4.4`

Expected APK naming after implementation:

```text
Komikku-v1.13.6-kmk.4.4-debug.apk
Komikku-v1.13.6-kmk.4.4-release.apk
```

## Purpose

KMK-Recs v0.4.3 added the major beta features that were planned:

- adaptive For You source fill,
- last-run source status diagnostics,
- Top Picks drill-down to 50,
- local KMK-Recs What's New cleanup.

The next pass should not add a large new recommendation system. It should harden the new beta behavior and remove confusing edge cases found during review.

Primary goals:

- make source statuses more accurate after display dedupe,
- make settings status feel fresher and more understandable,
- make Top Picks drill-down behavior clearer during loading/empty states,
- keep performance bounded,
- update documentation and versioning after implementation.

## Current Baseline

Current implemented feature version:

```text
KMK-Recs v0.4.3
```

Current handoff APK:

```text
Komikku-v1.13.6-kmk.4.3-debug.apk
```

Implemented v0.4.3 behavior:

- For You searches sources in batches of 5.
- For You can attempt up to 40 sources.
- For You stops once 20 useful source rows are found.
- Top three priority sources stay boosted and get larger result caps.
- Source statuses are serialized to `recommendation_last_source_run_statuses`.
- Recommendation Settings displays last-run status per source.
- Top Picks inline row shows up to 20.
- Top Picks header opens `TopPicksScreen` with up to 50 already-fetched candidates.

Known v0.4.3 limitations from implementation report:

- `HiddenByDuplicateHandling` status is not recorded.
- Settings derives disabled status in UI instead of persisting it.
- Top Picks detail may show partial results if opened while For You is still loading.
- `progress/total` remains item-count based while adaptive fill grows dynamically.

## Approved Scope For v0.4.4

### Phase 1: Add duplicate-hidden source status

Goal: if a source produced recommendations but all of its cards are hidden by cross-source display dedupe, settings should not simply say `Shown`.

Current behavior:

- `searchSource()` returns `Shown` if it produced scored recommendations.
- `State.dedupedItems()` later hides duplicate cards for display.
- There is no post-dedupe status correction.

Desired behavior:

- Sources with pre-dedupe results and zero post-dedupe visible cards should show `Hidden by duplicate handling` or equivalent.
- Sources with at least one visible card should remain `Shown`.
- Sources with no scored candidates before dedupe should remain `NoMatches` or `FilteredOut`.

Implementation guidance:

1. Add enum value to `RecommendationSourceStatus`:

```kotlin
HiddenByDuplicateHandling
```

2. Update `RecommendationSourceRunStatusStore` parser/serializer tests.

3. Add string resource:

```xml
<string name="rec_source_status_duplicate_hidden">Hidden by duplicate handling</string>
```

4. Decide where to calculate this status.

Recommended low-risk approach:

- Keep `searchSource()` status as pre-display status.
- After `dedupedItems()` or equivalent display filtering, compute a derived display status map.
- Persist the corrected status after the run completes.

Potential implementation shape:

```kotlin
private fun State.displayAdjustedStatuses(): PersistentMap<Long, RecommendationSourceRunStatus>
```

or a screen-model helper:

```kotlin
private fun adjustStatusesForDisplayDedupe(
    items: PersistentMap<CatalogueSource, PersonalRecommendationResult>,
    statuses: Map<Long, RecommendationSourceRunStatus>,
): Map<Long, RecommendationSourceRunStatus>
```

Rule:

- For each source with `status == Shown`:
  - Look at the deduped display result for that source.
  - If deduped result is `Success(emptyList())`, replace status with `HiddenByDuplicateHandling`.
  - Preserve `visibleCount` or set it to `0`.

5. Make sure Top Picks accumulation is not affected. This is status-only display polish.

Acceptance criteria:

- A source that produced results but lost every display card to dedupe shows duplicate-hidden status in settings.
- A source that still has at least one display card shows shown status.
- No extra network calls.
- No change to scoring or Top Picks ranking.

Tests:

- Add parser/serializer round-trip test for `HiddenByDuplicateHandling`.
- Add pure helper test if display-status adjustment is extracted.

### Phase 2: Improve settings status freshness

Goal: reduce confusion when Recommendation Settings is open while For You finishes or refreshes.

Current behavior:

- Settings reads `recommendationLastSourceRunStatuses()` on init.
- It may not live-update while already open.

Preferred implementation:

- Subscribe to `recommendationLastSourceRunStatuses().changes()` if the preference API supports it in this context.
- Parse the new value and update `state.sourceStatuses`.

Implementation sketch:

```kotlin
screenModelScope.launch {
    lastSourceStatusesPref.changes()
        .collectLatest { raw ->
            mutableState.update {
                it.copy(sourceStatuses = RecommendationSourceRunStatusStore.parse(raw).toPersistentMap())
            }
        }
}
```

If `.changes()` is unavailable or awkward:

- Add a manual "Reload source statuses" action in Recommendation Settings.
- Or refresh statuses when the screen resumes if there is an existing lifecycle pattern.

Do not make settings trigger For You searches.

Acceptance criteria:

- Opening settings after For You completes shows current statuses.
- If feasible, settings updates automatically after For You persists new statuses.
- If automatic updates are not feasible, a visible refresh/reload option exists.

Tests:

- No heavy test needed unless a parser/helper changes.
- Document manual verification.

### Phase 3: Add last-checked context

Goal: make it clear that source statuses are from the last For You run, not live source health.

Current model already has `updatedAt`.

Implementation guidance:

1. Add compact text in `SourcePriorityItem` subtitle when status exists:

```text
EN · #4 · Shown: 10 matches · checked 2m ago
```

or:

```text
EN · #4 · Shown: 10 matches
```

with a section summary:

```text
Statuses are from the last For You refresh.
```

Recommendation:

- Keep individual rows compact.
- Add or update the source priority summary to say statuses are from the last For You refresh.
- Only add per-row relative time if it is easy and does not clutter the UI.

Acceptance criteria:

- User can tell these statuses are from the last For You run.
- UI remains readable in the source priority list.

### Phase 4: Top Picks drill-down loading and empty-state polish

Goal: avoid confusing partial or empty Top Picks drill-down behavior.

Current behavior:

- Top Picks detail opens with whatever `combinedDetailResult` has at click time.
- If For You is still loading, the list may be partial.

Preferred behavior:

Option A: disable/no-op Top Picks header until at least one detail candidate exists.

Option B: open the screen but show a clear message:

```text
Top Picks is still loading. Refresh For You or wait for more sources to finish.
```

Option C: open with partial results and a compact note:

```text
Showing current Top Picks while sources continue loading.
```

Recommendation:

- Use Option C if it can be done cleanly because it preserves the useful current behavior.
- If adding the note is awkward, use Option A.

Implementation details:

- Add a boolean or count to `TopPicksScreen`, if needed:

```kotlin
isPartial: Boolean
```

- Determine partial state from `state.progress < state.total` or new adaptive progress fields if available.
- Add an empty-state message if `mangaIds` is empty.

Acceptance criteria:

- Tapping Top Picks during loading is understandable.
- Empty Top Picks detail does not look broken.
- No second crawl.

### Phase 5: Progress wording sanity check

Goal: make adaptive fill progress not misleading.

Current known limitation:

- `State.progress` / `State.total` remains item-count based.
- With adaptive fill, total grows as batches are added.

Implementation guidance:

- Inspect where progress is shown in `BrowsePersonalRecommendationsTab.kt`.
- If progress is not user-visible or not confusing, document no change.
- If it is visible and misleading, add explicit fields:

```kotlin
attemptedCount: Int
maxAttemptCount: Int
usefulSourceCount: Int
targetVisibleSourceCount: Int
isSearchingMoreSources: Boolean
```

Keep UI minimal. Do not overbuild this unless the current UI is clearly confusing.

Acceptance criteria:

- No obviously incorrect progress display.
- Adaptive fill remains bounded and understandable.

### Phase 6: Documentation and versioning

Create after implementation:

```text
docs/recommendations/KMK_RECS_V0_4_4_STATUS_POLISH_AND_BETA_HARDENING_IMPLEMENTATION.md
```

Update:

- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `RECOMMENDATION_VERSIONING.md`
- `KmkRecsReleaseNotes.kt`

Version bump:

```kotlin
VERSION_CODE = 404
VERSION_NAME = "KMK-Recs v0.4.4"
```

What's New should mention only user-facing changes, for example:

```markdown
## KMK-Recs v0.4.4

- Recommendation Settings now better explains when a source's results were hidden by duplicate handling.
- Source status wording now makes it clearer that results come from the last For You refresh.
- Top Picks drill-down now handles loading or empty states more clearly.
```

Do not mention:

- documentation cleanup,
- implementation reports,
- tests,
- internal refactors.

## Explicitly Deferred

Do not implement these in v0.4.4 unless separately approved:

- source quality learning,
- automatic source demotion,
- AniList/tracker known-list cache,
- minimum chapter count filter,
- query-time blocked-tag exclusion,
- Local Source recommendation support,
- pull-to-refresh,
- full recommendation database,
- background source scans.

## Risk Notes

### Status accuracy risk

Status labels should be honest. If exact reason cannot be known, use broader wording like `Filtered out` rather than inventing precision.

### Settings performance risk

Settings should only read a compact serialized status preference. It must not search extensions.

### Top Picks risk

Top Picks detail must continue to use already-fetched candidates only. No second crawl.

### Scope risk

This is a beta hardening pass. Keep changes small and focused.

## Required Validation

Run at minimum:

```text
./gradlew :app:testDebugUnitTest
```

If practical:

```text
./gradlew :app:assembleDebug
```

Recommended focused tests:

- `RecommendationSourceRunStatusStoreTest`
- any pure helper test for post-dedupe status adjustment

Manual verification:

1. Refresh For You with overlapping sources.
2. Confirm a source hidden entirely by display dedupe shows duplicate-hidden status in Recommendation Settings.
3. Confirm source statuses clearly say they come from the last For You refresh.
4. Open settings before/while For You finishes and confirm statuses update or can be refreshed.
5. Tap Top Picks while For You is loading and confirm the UI is understandable.
6. Open KMK-Recs What's New and confirm only user-facing v0.4.4 changes are listed.

## Summary For Claude

Implement KMK-Recs v0.4.4 as a small beta hardening pass:

- add duplicate-hidden source status after display dedupe,
- make settings source statuses fresher or reloadable,
- clarify that statuses are from the last For You refresh,
- polish Top Picks drill-down loading/empty behavior,
- sanity-check adaptive progress wording,
- update release notes, docs, tests, and versioning.

Do not add heavier recommendation features in this pass.
