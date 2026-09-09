# For You Source Order And Combined Row Implementation Plan

Date: 2026-06-14

Status: planning document for Claude Code. Do not implement until the user approves the summarized plan and provides a separate implementation prompt.

## Goal

Fix the confirmed For You source ordering bug and add back the spirit of the compiled/boosted recommendation behavior the user liked, without turning For You into a crawler or adding unnecessary overhead.

This plan should be implemented only after explicit user approval.

## User-Reported Problems

1. Browse > For You does not visually respect source priority.
   - Priority affects which sources are searched.
   - Priority affects which sources are boosted.
   - But visible rows are sorted alphabetically, so the UI does not reflect the user's chosen order.

2. The user remembers and liked a compiled/local-style recommendation behavior.
   - It appeared to gather many recommendations and present them cleanly.
   - It made boosted/local/common items feel useful.
   - The user is not seeing that behavior in Browse > For You now.

3. Local Source is missing from For You.
   - The code currently excludes real Komikku Local Source (`id = 0L`) from recommendation source filtering.
   - The behavior the user remembers may not be the real Local Source. It more closely matches the existing compiled/batch recommendation system.

## Important Code Findings

### For You Ordering Bug

Current file:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`

Current behavior:

```kotlin
items = newItems.toSortedMap(compareBy { s -> s.name }).toPersistentMap()
```

This makes For You rows display alphabetically by source name.

But earlier in the same screen model, source selection already computes the correct priority order:

```text
RecommendationSourceFilter.filterForRecommendations(...)
-> RecommendationSourceOrdering.apply(...)
-> take(MAX_SOURCES)
-> boostedSourceIds = first 3
```

The bug is not in source selection. The bug is in UI state ordering after results update.

### Real Local Source

Current file:

- `app/src/main/java/exh/recs/RecommendationSourceFilter.kt`

Current behavior:

```kotlin
private const val LOCAL_SOURCE_ID = 0L
...
if (isLocalSource(source) && !includeLocal) return@filter false
```

Real Local Source is excluded from both For You and Recommendation Settings by default.

This is likely intentional from the language-filter pass because Local Source does not behave like a normal extension source.

### Existing Compiled/Ranked Recommendation System

Current files:

- `app/src/main/java/exh/recs/batch/RecommendationSearchHelper.kt`
- `app/src/main/java/exh/recs/sources/StaticResultPagingSource.kt`
- `app/src/main/java/exh/recs/BrowseRecommendsScreen.kt`
- `app/src/main/java/exh/recs/RecommendsScreen.kt`

This system already does a compiled recommendation pass:

1. Takes a list of source manga.
2. For each manga, creates recommendation sources through `RecommendationPagingSource.createSources(...)`.
3. Fetches recommendations from trackers and/or extension sources.
4. Groups results by URL.
5. Counts repeated occurrences.
6. Sorts by occurrence count descending.
7. Displays those ranked results through `StaticResultPagingSource`.

This is probably the closest existing code to the user's remembered "compiled a bunch of stuff and showed it properly" behavior.

However, it is not currently part of Browse > For You.

### Manga-Detail Cross-Extension Recommendations

Current file:

- `app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt`

This creates one recommendation provider per visible source for a single manga detail page. It searches by the current manga's genres and enriches top results.

This also is not part of Browse > For You.

## Implementation Strategy

Implement this in two separate phases.

Phase 1 is a bug fix.

Phase 2 is a new For You feature that reuses existing compiled recommendation ideas safely.

## Required Pre-Implementation Verification

Before changing code, Claude must verify the current implementation one more time and document the result in its implementation note.

Claude must specifically check:

- whether Browse > For You already has any hidden, disabled, or partially wired combined/local row implementation,
- whether any existing preference toggles enable the remembered compiled/local behavior,
- whether `RecommendationSearchHelper`, `StaticResultPagingSource`, `BrowseRecommendsScreen`, or `RecommendsScreen` can be reused directly or should only be used as a reference,
- whether the real Komikku Local Source (`id = 0L`) is still intentionally excluded by `RecommendationSourceFilter`,
- whether the For You row order bug is still caused by `toSortedMap(compareBy { s -> s.name })`,
- whether any newer markdown file created after this plan changes the intended scope.

If Claude finds an existing disconnected implementation, it should prefer reconnecting or repairing it over creating a duplicate implementation.

If Claude finds that this plan is based on an incorrect assumption, it must stop and report the mismatch instead of coding through it.

## Phase 1: Fix For You Visual Row Ordering

### Desired Behavior

Browse > For You rows should display in the same priority order used for source selection:

1. User-prioritized sources first.
2. Disabled sources excluded.
3. Language-filtered sources only.
4. Top-three boosted sources remain first.
5. Loading, success, and error rows should keep stable positions.
6. Empty completed rows may still be hidden as they are today.

### Proposed Code Approach

In `BrowsePersonalRecommendationsScreenModel`:

1. Store selected source order in `State`.
   - Add a field such as:

```kotlin
val sourceOrderIds: PersistentList<Long> = persistentListOf()
```

2. When `load()` computes:

```kotlin
val sources = orderedEnabledSources.take(MAX_SOURCES)
```

also store:

```kotlin
sourceOrderIds = sources.map { it.id }.toPersistentList()
```

3. Replace name-based sorting in `updateItem()`.

Current:

```kotlin
items = newItems.toSortedMap(compareBy { s -> s.name }).toPersistentMap()
```

Replace with a priority-aware ordering helper.

Possible helper:

```kotlin
private fun sortItemsBySourceOrder(
    items: PersistentMap<CatalogueSource, PersonalRecommendationResult>,
    orderIds: List<Long>,
): PersistentMap<CatalogueSource, PersonalRecommendationResult> {
    val index = orderIds.withIndex().associate { it.value to it.index }
    return items.entries
        .sortedWith(
            compareBy<Map.Entry<CatalogueSource, PersonalRecommendationResult>>(
                { index[it.key.id] ?: Int.MAX_VALUE },
                { it.key.name },
                { it.key.lang },
                { it.key.id },
            ),
        )
        .associate { it.key to it.value }
        .toPersistentMap()
}
```

4. Use that helper whenever initial items or updated items are written.

5. Ensure dedupe does not disturb source row order.
   - `dedupedItems()` should preserve the existing map order.
   - If it does not, make it explicitly preserve ordered entries.

### Tests

Add or update unit tests around the screen model if practical.

At minimum, add a small pure helper test if the ordering logic is extracted:

- rows are returned in priority order,
- unknown rows fall back to name/lang/id order,
- updating one row does not reorder the full list alphabetically,
- empty-row filtering in the UI should not shift the remaining rows out of priority order.

If existing screen model tests are too heavy, keep the helper pure and test that.

## Phase 2: Add A Combined For You Row

### Product Decision

Do not use the real Komikku Local Source (`id = 0L`) as the first implementation.

Reason:

- Local Source is a real source for local files/downloads.
- It is not naturally a recommendation engine.
- Adding it directly may confuse "local manga files" with "compiled recommendation results."

Instead, add a synthetic For You row such as:

```text
Combined Picks
```

or:

```text
Common Recommendations
```

This row should reuse the existing compiled/ranked recommendation concept but be generated from the For You taste profile and source results.

### Desired Behavior

The combined row should:

- appear near the top of Browse > For You,
- preferably appear before per-source rows,
- combine candidates from the same source searches For You already performs,
- rank candidates by existing score plus cross-source repetition,
- avoid extra extension searches where possible,
- respect blocked tags and rated visibility,
- respect language filtering,
- respect disabled sources,
- respect favorites/rated filtering as configured,
- not fetch chapters,
- not create a full local database.

### Important Efficiency Rule

Do not run a second full recommendation crawl just to build the combined row.

The combined row should be built from data already produced by For You source searches.

That means:

1. Run the same per-source searches as today.
2. As each source returns `List<PersonalRecommendation>`, feed those results into a combined accumulator.
3. Update the synthetic combined row from the accumulator.

### Proposed Data Model

Add a display model that can represent both real source rows and synthetic rows.

Current For You state uses:

```kotlin
PersistentMap<CatalogueSource, PersonalRecommendationResult>
```

That makes synthetic rows awkward because the key must be a real `CatalogueSource`.

Recommended approach:

```kotlin
sealed interface PersonalRecommendationRowKey {
    val stableId: String
    val title: String
    val subtitle: String?

    data class Source(val source: CatalogueSource) : PersonalRecommendationRowKey
    data object Combined : PersonalRecommendationRowKey
}
```

Then state can become:

```kotlin
PersistentMap<PersonalRecommendationRowKey, PersonalRecommendationResult>
```

However, this is a wider UI change.

Lower-risk alternative:

- keep real source rows as-is,
- add a separate `combinedResult: PersonalRecommendationResult?` to `State`,
- render combined row before `visibleItems`.

Recommended for first pass:

```kotlin
val combinedResult: PersonalRecommendationResult? = null
```

This is much lower risk than replacing the row key type everywhere.

### Combined Row Accumulator

Add an internal accumulator in `BrowsePersonalRecommendationsScreenModel`.

Candidate key:

```text
source + url
```

Do not dedupe only by title at this stage, because separate manga can share similar names.

Data:

```kotlin
data class CombinedCandidateBucket(
    val manga: Manga,
    var bestScore: Double,
    var occurrenceCount: Int,
    val matchedGroups: MutableSet<String>,
    val sourceIds: MutableSet<Long>,
)
```

When a source returns recommendations:

1. For each recommendation:
   - key by `manga.source + manga.url`.
   - increment occurrence count.
   - keep max score.
   - merge matched groups.
   - add source id.

2. Rank combined candidates by:

```text
bestScore
+ occurrenceCount bonus
+ boosted source bonus if one of the contributing source IDs is boosted
```

3. Cap display count.
   - Use 20 results, matching boosted rows.

4. Update `combinedResult`.

### Combined Row Naming

Avoid calling this "Local Source" unless it actually uses Komikku Local Source.

Suggested title:

```text
Combined Picks
```

Subtitle examples:

```text
Ranked across selected sources
```

or:

```text
Matched: action, reincarnation, fantasy
```

### Combined Row Placement

Recommended first behavior:

1. Combined row appears first.
2. Then regular source rows appear in priority order.

Alternative:

- Combined row appears after the top-three boosted sources.

The first behavior is simpler and makes the feature visible.

### Combined Row Click Behavior

Because this is not a real source, row-header click needs special handling.

Recommended first pass:

- If user taps the combined row header, open a `BrowseRecommendsScreen` with `MergedSourceMangas`, using a `RankedSearchResults` built from combined row candidates.

This reuses existing `StaticResultPagingSource` display behavior.

Potential problem:

- `RankedSearchResults` currently maps `SManga` to occurrence count, not `Manga`.
- The combined row has local `Manga`.

Options:

1. Convert combined `Manga` back to `SManga` for `RankedSearchResults`.
2. Add a separate screen path for `List<PersonalRecommendation>`.
3. Keep the header click disabled in the first pass and only support tapping individual manga.

Recommended first pass:

- Keep individual manga tap behavior.
- Header click can be disabled or show no-op for combined row.
- Add full combined drill-down in a later pass if needed.

### Cache Interaction

Do not create a separate cache table in the first pass.

The combined row is derived from per-source cached/live results. If per-source rows load from cache, the combined row can still be rebuilt from those cached results.

This keeps storage simple.

### Dedupe Interaction

Current display-time dedupe removes duplicate titles across rows.

If combined row is added, do not let it steal all results from source rows by reference-equality dedupe.

Recommended:

- Apply cross-source dedupe only to real source rows.
- Combined row should maintain its own deduped result list.
- Do not run the existing `dedupedItems()` across the combined row and source rows together.

### Settings

Add a setting only if needed.

Recommended first pass:

- Enable combined row by default.
- Add a setting only if the user dislikes it or if performance is worse than expected.

Optional setting:

```text
Show combined For You row
```

Default:

```text
enabled
```

### Tests

Add pure tests for combined ranking.

Suggested test cases:

- same `source + url` increments occurrence count,
- same title but different source/url does not automatically merge,
- higher score beats lower score,
- occurrence bonus can raise repeated recommendations,
- blocked/rated filtering is still applied before candidates reach the accumulator,
- combined row cap is respected,
- combined row updates when cached rows load.

## Phase 3: Optional Real Local Source Support

This phase should not be included in the first implementation unless the user explicitly approves it.

Real Local Source support needs a separate product decision:

- Should For You recommend manga from local files?
- Should it use downloaded manga?
- Should it use library/history/cached manga?
- Should it show local manga already known to the user?

Until those questions are answered, keep real Local Source excluded.

## Versioning

Recommended feature version:

```text
KMK-Recs v0.3.3
```

Reason:

- This is a patch/behavior correction if only Phase 1 is implemented.
- If Phase 2 combined row is included, it is arguably a minor user-visible capability. If Claude implements both, use:

```text
KMK-Recs v0.4.0
```

Recommended APK naming if built from upstream `1.13.6`:

```text
Komikku-v1.13.6-kmk.0.3.3-debug.apk
```

or, if combined row is included:

```text
Komikku-v1.13.6-kmk.0.4.0-debug.apk
```

Use the exact naming style already documented in `RECOMMENDATION_VERSIONING.md`; if the project prefers `kmk.3.3` instead of `kmk.0.3.3`, keep that convention consistent.

## Documentation Requirements For Claude

Claude must update documentation in the same implementation session.

Before implementation, Claude must add a short "Pre-Implementation Verification" section to the implementation note recording what it checked and what it found.

Required files to update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/SOURCE_LIST_DIAGNOSIS.md`
- `RECOMMENDATION_VERSIONING.md`

If implementation is substantial, Claude should also create:

```text
docs/recommendations/FOR_YOU_SOURCE_ORDER_AND_COMBINED_ROW_IMPLEMENTATION.md
```

That implementation note must include:

- user-approved scope,
- files changed,
- exact behavior changed,
- tests run,
- APK filename if built,
- known limitations,
- deviations from this plan.

## Acceptance Criteria

### Phase 1

- For You source rows display in the same priority order shown in Recommendation Settings.
- Top-three boosted sources appear first unless their rows are hidden because they have no visible results.
- Loading rows remain stable instead of jumping alphabetically as results arrive.
- Empty completed rows remain hidden.
- Existing per-source recommendation behavior still works.

### Phase 2

- A combined For You row appears near the top.
- It is derived from already-fetched For You source results, not from an extra full crawl.
- It respects language filters, disabled sources, blocked tags, rated visibility, and favorites filtering.
- It uses occurrence/repetition as a positive signal without unsafe title-only dedupe.
- It is capped and does not fetch chapters.
- It has tests for ranking/accumulation behavior.

## Non-Goals

- Do not redesign Browse.
- Do not add a full manga catalogue database.
- Do not fetch chapters for recommendations.
- Do not include real Local Source unless separately approved.
- Do not merge different manga only because their titles are similar.
- Do not remove the existing manga-detail recommendation system.

