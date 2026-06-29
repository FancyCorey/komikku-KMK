# Top Picks Filtering And Exception Handling Implementation Plan

Date: 2026-06-14

Status: planning document for Claude Code. Do not implement until the user approves the summarized plan and provides a separate implementation prompt.

## Goal

Improve Browse > For You and the synthetic Top Picks row by adding practical, local-first filtering and exception handling:

- change "Combined Picks" into a true "Top Picks" feature, not just a renamed row,
- ensure Top Picks ranks the best candidates according to the user's explicit and learned preferences,
- reduce repeated/duplicate entries in Top Picks using conservative metadata checks,
- optionally hide manga the user already knows locally,
- improve general exception handling for recommendation filtering/scoring edge cases,
- make future filters like minimum chapter count possible without expensive network calls,
- keep the recommendation system bounded, fast, and predictable.

This plan is about recommendation quality and user control. It is not a redesign.

## Current Baseline

As of KMK-Recs v0.4.0:

- For You rows display in recommendation source priority order.
- The synthetic "Combined Picks" row appears first.
- Combined Picks is built from already-fetched per-source For You results.
- Combined Picks does not run an extra crawl.
- Combined Picks keys candidates by `(manga.source, manga.url)`.
- Same-title manga from different sources are intentionally kept separate.
- Rated/favorite filtering already happens before scoring in the per-source pipeline.
- Local Source (`id = 0L`) remains excluded from For You.

## User-Reported Needs

The user wants more exception handling and filtering, especially:

1. Change Combined Picks into Top Picks as a real preference-ranked feature, not a cosmetic rename.
2. Avoid repeated manga across Top Picks when they are clearly the same work.
3. Hide or reduce things already read/known.
4. Possibly filter by minimum chapter count.
5. Handle failures and incomplete data gracefully in different recommendation use cases.
6. Keep results based on the user's real preferences and source priority.
7. Avoid making the system slow or repetitive.

## Feasibility Summary

### Local Read/Known Filtering Is Feasible

Komikku already stores local signals:

- `mangas.favorite`
- chapter read state in `chapters.read`
- partial read state in `chapters.last_page_read`
- reading history in `history.last_read`
- library/history aggregate views such as `libraryView` and `historyView`
- local tracker rows in `manga_sync`
- manga taste rows in `manga_taste`

Because For You already calls `networkToLocalManga(...)`, recommendation candidates become local `Manga` rows before filtering/scoring. That gives the app a reliable local id for exact source/url checks.

Best first implementation:

```text
source + url exact match
-> local manga row
-> check favorite / rating / read chapters / last_page_read / history
```

This avoids external APIs and does not require chapter fetching.

### AniList Is Possible But Should Not Be First

AniList can expose user list status/progress/score for tracked manga, but:

- not all manhwa/manhua exist there,
- names often differ,
- extension manga can be hard to match to AniList,
- live lookup per recommendation would be slow,
- only tracked manga would be covered.

Recommended approach:

- Do not use AniList in this phase.
- If added later, use a cached optional known-list feature, never per-result live lookups.

### Minimum Chapter Count Is Only Partly Feasible

Filtering by chapter count is safe only when the chapter count is already locally known.

Fetching chapter lists for every recommendation candidate would be expensive and should not be part of normal For You loading.

Recommended first implementation:

- Add infrastructure for "known chapter count" only if cheap.
- Do not fetch chapters.
- If chapter count is unknown, do not exclude the manga.

## Implementation Strategy

Implement this in phases:

1. Convert Combined Picks into a true preference-ranked Top Picks feature.
2. Local known/read filtering.
3. Conservative duplicate handling in Top Picks.
4. General exception handling and graceful degradation.
5. Optional local-known chapter count filter later.
6. Optional AniList/tracker known-list cache later.

## Required Pre-Implementation Verification

Before changing code, Claude must verify current implementation and document the result.

Claude must check:

- whether "Combined Picks" strings are still in `i18n-kmk/strings.xml`,
- whether the row title comes from `rec_combined_picks_title`,
- whether `CombinedPicksAccumulator` still keys by `(manga.source, manga.url)`,
- whether `CombinedPicksAccumulator` ranking still uses only `bestScore + occurrence bonus + boosted bonus`,
- whether `BrowsePersonalRecommendationsScreenModel` still filters favorites/rated entries before scoring,
- whether `PersonalRecommendationScorer` still scores candidates using explicit tag preferences, learned tag weights, blocked groups, and source affinity,
- whether any local read/history helper already exists for exact source/url candidates,
- whether any existing SQL query can cheaply identify read/started/history manga by manga id,
- whether current source-search/filter/enrichment/cache code already isolates per-source failures,
- whether any new filtering query failure could accidentally blank the whole recommendation page,
- whether the current tests still pass before changes if practical.

If an existing helper already solves part of this, prefer reusing it.

If chapter count requires network chapter fetching, do not implement that part in this phase.

## Phase 1: Convert Combined Picks Into True Top Picks

The synthetic row should display as:

```text
Top Picks
```

This is not just a string rename. The row should intentionally represent the best available recommendations according to the user's taste profile and source priorities.

### Existing Preference Signals To Reuse

`PersonalRecommendationScorer` already scores each candidate with:

- explicit preferred tags: positive weight,
- explicit disliked tags: negative weight,
- learned tag weights from Love/Like/Dislike manga ratings,
- hard-blocked tag groups,
- weak source affinity.

`BrowsePersonalRecommendationsScreenModel` already feeds the scored per-source results into `CombinedPicksAccumulator`.

Claude must preserve and strengthen this flow rather than bypassing it.

### Desired Top Picks Ranking

Top Picks should rank by:

```text
personal preference score
+ strong matched-preference evidence
+ repeated occurrence / same-work evidence
+ boosted or high-priority source contribution
- disliked/weak evidence already reflected by personal score
```

The ranking should remain bounded and deterministic.

### Recommended Code Approach

Keep `PersonalRecommendation.score` as the primary ranking signal.

Update `CombinedPicksAccumulator` only if needed so that its combined score clearly prioritizes:

1. higher personal score from `PersonalRecommendationScorer`,
2. more matched preferred/learned groups,
3. conservative duplicate/occurrence evidence,
4. boosted source contribution,
5. deterministic tie-breaks.

Do not make occurrence/source bonuses overpower a much better personal preference score.

The internal class name can remain `CombinedPicksAccumulator` for now if renaming it would cause unnecessary churn. The user-facing string should change to `Top Picks`.

Likely files:

- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
- `app/src/main/java/exh/recs/CombinedPicksAccumulator.kt`
- `app/src/test/java/exh/recs/CombinedPicksAccumulatorTest.kt`
- documentation files

### Tests

Add or update tests to prove:

- a higher personal score beats a weak repeated candidate unless scores are close,
- matched groups improve tie-breaking,
- boosted source bonus is helpful but does not dominate personal score,
- Top Picks remains deterministic.

## Phase 2: Add Local Known/Read Filtering

### Desired Behavior

The user should be able to avoid recommendations they already know.

Recommended default:

```text
Hide already known manga: enabled
```

Where "known" means exact local identity match with one or more of:

- already rated,
- in library/favorite,
- has at least one read chapter,
- has at least one partially read chapter,
- has history with `last_read > 0`.

### Proposed Code Approach

Create a focused helper/interactor such as:

```kotlin
class RecommendationKnownMangaFilter(...)
```

Batch form is preferred for efficiency:

```kotlin
suspend fun knownMangaIds(mangaIds: Collection<Long>): Set<Long>
```

Use local DB data:

- `mangas.favorite`
- `manga_taste`
- `chapters.read`
- `chapters.last_page_read`
- `history.last_read`

If no existing generated query exists, add a small SQLDelight query. Keep it local and indexed.

Candidate query shape:

```sql
SELECT DISTINCT M._id
FROM mangas M
LEFT JOIN chapters C ON C.manga_id = M._id
LEFT JOIN history H ON H.chapter_id = C._id
LEFT JOIN manga_taste T ON T.manga_id = M._id
WHERE M._id IN :mangaIds
AND (
    M.favorite = 1
    OR T.manga_id IS NOT NULL
    OR C.read = 1
    OR C.last_page_read != 0
    OR H.last_read > 0
);
```

If SQLDelight does not like this exact shape, implement equivalent local queries.

### Where To Apply

Apply before scoring if possible:

```text
networkToLocalManga(raw)
-> local known/read filter
-> enrichment
-> scoring
-> Top Picks accumulator
```

This prevents known manga from appearing in both source rows and Top Picks.

### Settings

Recommended first setting:

```text
Hide known manga
```

Default:

```text
enabled
```

Description:

```text
Hide manga already rated, in library, or started locally.
```

This should live in Recommendation Settings near rated visibility/source settings.

### Caveat

This only catches manga Komikku still knows about. If the history/database entry was deleted or the manga was read on another device without backup/sync, the app cannot know locally.

## Phase 3: Conservative Duplicate Handling In Top Picks

### Problem

Top Picks currently keeps candidates separate by `(source, url)`. That is safe but can show the same work multiple times from different sources.

### Conservative Duplicate Rule

Two Top Picks candidates may be treated as the same work only if:

```text
normalized title matches exactly
AND
(
    normalized author matches exactly and is not blank
    OR normalized artist matches exactly and is not blank
)
```

If author and artist are both missing, do not merge.

If only titles are similar but not exact after normalization, do not merge.

If sources disagree on author/artist, do not merge.

### Ranking Behavior When Merging

If candidates are merged as exact duplicates:

- keep the best local `Manga` entry as display representative,
- keep the highest personal score,
- increase occurrence count,
- merge matched tag groups,
- merge contributing source ids,
- apply occurrence/source bonus.

Representative tie-break:

1. higher score,
2. richer metadata, such as more genres and non-empty author/artist,
3. boosted source contribution,
4. lower manga id for determinism.

### Where To Implement

Implement inside `CombinedPicksAccumulator`, or create a small pure helper used by it.

The key idea:

- keep exact `(source, url)` buckets as base identity,
- add optional conservative work-key merging when strong metadata exists.

Suggested helper:

```kotlin
private fun conservativeWorkKey(manga: Manga): String?
```

Return `null` when metadata is too weak.

### Tests

Add tests:

- same source/url still merges,
- exact title + exact author merges across different sources,
- exact title + exact artist merges across different sources,
- exact title but blank author/artist does not merge,
- exact title but different author does not merge,
- similar title does not merge,
- merged duplicate keeps best score and merged matched groups.

## Phase 4: Minimum Known Chapter Count Filter

## Phase 4: General Exception Handling And Graceful Degradation

### Goal

Recommendation filters should improve results without making For You fragile.

If a new filter, metadata lookup, duplicate check, or local known/read lookup fails, the app should degrade safely:

- one bad source should not crash For You,
- one bad candidate should not abort the whole source row,
- one failed known/read lookup should not hide everything,
- missing metadata should reduce certainty, not create aggressive filtering,
- cache read/write problems should fall back to live results or empty cache behavior.

### Existing Behavior To Preserve

The current For You implementation already isolates many per-source failures:

- source searches are run per source,
- each source search catches exceptions and updates that row as an error,
- fallback query plans can run when one strategy fails,
- `getFilterList()` failures fall back to an empty filter list,
- metadata enrichment failures are capped and should not stop all recommendations.

Claude must preserve this behavior.

### New Failure Cases To Handle

The new filtering work introduces additional possible failure points:

- SQLDelight known/read query fails,
- history table has no row for a candidate,
- candidate has missing author/artist/title metadata,
- candidate has malformed or blank URL,
- cache contains stale manga ids,
- duplicate-merge helper receives incomplete metadata,
- settings preference has invalid/old value,
- local DB cleanup removed a non-library manga after cache was written.

### Required Handling Rules

Use fail-open behavior for quality filters unless the user explicitly requested a hard block.

Recommended rules:

- If known/read lookup fails, keep candidates visible and log the failure.
- If duplicate metadata is incomplete, do not merge.
- If author/artist is blank, do not use title-only duplicate merging.
- If minimum known chapter count is unknown, keep candidate visible.
- If cache row points to a missing manga id, skip only that manga.
- If a source fails, show the row error or hide empty completed row according to current behavior, but keep other rows and Top Picks.
- If Top Picks accumulator fails for one candidate, skip that candidate and continue.

### Where To Implement

Add small local `runCatching`/try-catch boundaries around new filter/helper code, not giant catch-all blocks around the whole screen model.

Preferred pattern:

```kotlin
val knownIds = runCatching {
    getKnownRecommendationManga.await(candidateIds)
}.getOrElse { error ->
    logcat(LogPriority.WARN, error) { "Recommendation known-manga filter failed" }
    emptySet()
}
```

For duplicate handling:

```kotlin
val key = runCatching { conservativeWorkKey(manga) }.getOrNull()
```

Do not swallow programming errors silently in tests. Production code can fail open, but tests should cover expected edge cases.

### Tests

Add tests where practical:

- known/read filter failure returns no hidden ids rather than throwing,
- blank title/author/artist does not merge duplicates,
- malformed metadata does not crash Top Picks ranking,
- missing cached manga ids are skipped without failing the row,
- per-candidate duplicate failure does not fail the whole accumulator.

## Phase 5: Minimum Known Chapter Count Filter

Recommended status: defer full implementation unless it can be done from existing local data only.

Safe later behavior:

- If local chapter count is known and less than threshold, hide candidate.
- If local chapter count is unknown, keep candidate.
- Never fetch chapter lists just to check this filter.

Why defer:

For fresh extension search results, chapter count often requires `getChapterList`, which is a network call per manga. That would make For You much slower and would hit extensions unnecessarily.

## Phase 6: Optional AniList/Tracker Known List Cache

Recommended status: good for later, not this pass.

Possible later behavior:

- periodically read locally stored tracker rows from `manga_sync`,
- optionally fetch remote user list on manual sync/refresh,
- cache normalized known titles/progress/status,
- use it as a weak "probably known" signal.

Constraints:

- Do not query AniList live for every recommendation.
- Do not block For You on tracker network calls.
- Do not hide based only on fuzzy title matching unless the user explicitly enables aggressive filtering.

## Additional Recommendations

### Add "Why Hidden" Diagnostics Later

For development and trust, consider a debug-only or settings-only diagnostic that can show:

- hidden because rated,
- hidden because favorite,
- hidden because read locally,
- hidden because duplicate,
- hidden because blocked tag.

### Preserve User Control

Recommended defaults:

- hide known manga: enabled,
- conservative duplicate merge in Top Picks: enabled,
- minimum chapter count: off,
- tracker/AniList known cache: off until implemented.

## Versioning

Recommended feature version:

```text
KMK-Recs v0.4.1
```

If Claude adds a new database table or a larger tracker-cache feature, reconsider as:

```text
KMK-Recs v0.5.0
```

This plan should not require a new table.

## Documentation Requirements For Claude

Claude must update documentation in the same implementation session.

Required files:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/SOURCE_LIST_DIAGNOSIS.md`
- `RECOMMENDATION_VERSIONING.md`

Claude should create:

```text
docs/recommendations/TOP_PICKS_FILTERING_AND_EXCEPTION_HANDLING_IMPLEMENTATION.md
```

Implementation note must include:

- date,
- feature version,
- user-approved scope,
- pre-implementation verification,
- files changed,
- behavior changed,
- tests run,
- APK/build output if built,
- known limitations,
- deviations from this plan,
- follow-up recommendations.

## Acceptance Criteria

### Top Picks Feature

- User-facing row title says `Top Picks`.
- Old "Combined Picks" wording is removed from user-facing strings.
- The row is intentionally ranked as the user's best available picks, not just a pooled result list.
- Personal score from explicit tags, learned ratings, disliked tags, blocked tags, and source affinity remains the primary signal.
- Repetition and boosted-source bonuses improve ranking without overpowering much stronger preference matches.

### Known Manga Filter

- Recommendation candidates with exact local source/url and local known signals can be hidden.
- Known signals include rating, favorite/library, read chapter, partial read chapter, or history.
- Filter is local-only and does not call AniList or fetch chapters.
- Behavior is configurable if implemented as a setting.

### Top Picks Duplicate Handling

- Top Picks merges only strong duplicates.
- Exact title + exact author/artist can merge across sources.
- Title-only duplicates with missing author/artist are not merged.
- Similar-title duplicates are not merged.
- Tests cover conservative merge behavior.

### Performance

- No chapter list fetching for filtering.
- No live tracker/AniList lookup per result.
- No full local manga catalogue crawl.
- Existing per-source caps remain.

### Exception Handling

- New filters fail open instead of blanking For You.
- Missing metadata does not crash Top Picks or cause aggressive duplicate merging.
- Per-source failures remain isolated.
- Cache/missing-manga issues skip only affected entries.
- Tests cover at least the main new failure cases.

## Non-Goals

- Do not add AniList live filtering in this phase.
- Do not fetch chapter lists to count chapters.
- Do not aggressively fuzzy-merge titles.
- Do not redesign For You.
- Do not change manga-detail recommendations unless required by shared strings/docs.
