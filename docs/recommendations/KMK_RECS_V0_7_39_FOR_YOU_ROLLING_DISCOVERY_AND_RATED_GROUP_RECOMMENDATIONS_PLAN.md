# KMK-Recs v0.7.39 For You Rolling Discovery And Rated Group Recommendations Plan

Date: 2026-07-10

Status: planning. Do not implement until the user explicitly approves this plan and provides the Claude Code prompt.

## User-Approved Direction

This plan combines two related but separate recommendation improvements:

1. Improve the For You page so each refresh can discover a new bounded set of candidates, store what was evaluated, and compare new candidates against the best remembered candidates instead of behaving like each refresh is mostly isolated.
2. Improve Loved/Liked/Disliked group-based recommendation behavior so rated manga groups can reuse the existing grouped cross-source identity information and produce recommendations from the whole group, not only one representative manga.

Claude may implement this plan in internal phases, but should not build or hand off the final APK until every phase in this approved scope is complete, documented, and verified. If the work becomes too large for one session, Claude should pause after documenting exactly what is complete, what remains, and what prompt should continue the same plan.

## Important Clarification

The main current problem is not that the For You scorer only accepts exact matches. The scorer is already additive and closeness-based. The weakness is that candidate discovery is still too narrow:

- The source query is built from a small set of top taste tags.
- Many source websites have incomplete tags, differently named tags, weak filters, or poor search behavior.
- If the source search does not return a manga, the scorer never sees it.
- Candidate memory exists in v0.7.38, but discovery is too shallow and depends too much on successful scored recommendations being stored.

The goal is therefore not to build a full local manga database for every source. That would be too heavy, slow, and fragile. The goal is an app-layer rolling candidate memory that gradually evaluates more source pages/batches over time, remembers enough progress to avoid repeating bad pages, and always ranks the best known candidates by the current taste profile.

## Existing Code To Review First

Claude must read these files before editing:

- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/DOCUMENTATION_RULES.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `docs/recommendations/KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_AND_GROUP_SEED_ENRICHMENT_PLAN.md`
- `docs/recommendations/KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_AND_GROUP_SEED_ENRICHMENT_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_PLAN.md`
- `docs/recommendations/KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_36_RATED_MANGA_UI_PARITY_AND_GROUP_RECS_CRASH_FIX_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_37_GROUP_SEEDED_RECOMMENDATIONS_LOADING_FIX_IMPLEMENTATION.md`

Claude must inspect these implementation files and tests:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/PersonalRecommendationScorer.kt`
- `app/src/main/java/exh/recs/RecommendationQueryPlanner.kt`
- `app/src/main/java/exh/recs/sources/GenreFilterMapper.kt`
- `app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryStore.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryEntry.kt`
- `data/src/main/sqldelight/tachiyomi/data/recommendation_candidate_memory.sq`
- `data/src/main/sqldelight/tachiyomi/migrations/56.sqm`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationSeed.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationSeedBuilder.kt`
- `app/src/main/java/exh/recs/group/GroupSeedRecommendationScorer.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaGrouper.kt`
- `app/src/main/java/exh/recs/rated/RatedMangaScreen.kt`
- `app/src/main/java/exh/recs/rated/RatedMangaScreenModel.kt`
- `data/src/main/sqldelight/tachiyomi/data/manga_cross_source_link.sq`
- relevant tests under `app/src/test/java/exh/recs/`

## Non-Goals

- Do not build a full local database of all manga from all sources.
- Do not crawl entire sources aggressively.
- Do not increase network load without tight bounds.
- Do not remove existing For You source rows, Top Picks, source priority, hide-known, seen, liked, disliked, or loved behavior.
- Do not create a second independent cross-source grouping system.
- Do not replace manga-detail recommendations unless reuse is clearly possible and safe.
- Do not build the APK until all plan phases are complete and verified.

## Part 1 - For You Rolling Candidate Discovery

### Current Behavior To Fix

As of v0.7.38:

- `BrowsePersonalRecommendationsScreenModel.searchSource()` loads remembered candidates with `memoryStore.loadForSourceQuery(source.id, queryKey)`.
- It loads known pages with `memoryStore.knownPages(source.id, queryKey)`.
- Page 1 is fetched through `source.getSearchManga(1, searchParams.textQuery, searchParams.filters)`.
- Page 1 scored recommendations are saved with `memoryStore.upsertBatch(... page = 1 ...)`.
- `discoverAdditionalPage()` calls `RecommendationDiscoveryPlanner.nextPageToProbe(knownPages)`.
- `RecommendationDiscoveryPlanner.MAX_PAGES_PER_REFRESH` is currently `3`.
- `RecommendationDiscoveryPlanner.nextPageToProbe()` returns null when `knownPages` is empty.
- A page is considered known only when at least one candidate from that page exists in `recommendation_candidate_memory`.
- Empty pages, pages with all filtered candidates, or pages whose candidates score <= 0 may not be marked as evaluated.
- `RecommendationCandidateMemoryRanker.merge()` re-scores remembered and new candidates, which is good, but it can only rank candidates that were actually stored or newly found.

This means the app has the beginning of the right architecture, but it can still retry weak pages, fail to advance through sources, and display a narrow pool.

### Desired Behavior

Each For You refresh should:

1. Use the existing query strategy for the source and current taste signature.
2. Fetch page 1 only when necessary for cache/live refresh behavior.
3. Select the next unevaluated discovery page/batch for that source/query signature.
4. Fetch a bounded number of candidates from that page/batch.
5. Score candidates using existing taste scoring.
6. Store scored candidates.
7. Mark the page/batch as evaluated even if it produced no usable visible recommendations.
8. Merge remembered candidates and new candidates.
9. Re-score everything against the current taste profile.
10. Show the best overall candidates, not merely the newest candidates.

Older high-scoring candidates must remain eligible. New candidates should only replace old candidates when they score better or when filters remove the old ones.

### Schema And Storage Changes

Claude should evaluate whether to extend the existing `recommendation_candidate_memory` table or add a small companion progress table. Preferred design:

1. Keep `recommendation_candidate_memory` for candidate rows.
2. Add a companion table such as `recommendation_discovery_progress` or equivalent to track page/batch progress independently of successful candidates.

The progress table should track at minimum:

- `source_id`
- `query_signature`
- `query_tags_json`
- `query_strategy`
- `page`
- `evaluated_at`
- `raw_count`
- `localized_count`
- `scored_count`
- `visible_count`
- `filtered_count`
- `status`
- optional `error_message`
- `profile_fingerprint`

Recommended status values:

- `success`
- `empty`
- `filtered`
- `duplicate`
- `error`
- `unsupported`
- `exhausted`

Primary key should prevent duplicate progress rows for the same source/query/page. Use SQLDelight naming and migration conventions already present in the app.

If Claude chooses to add columns to `recommendation_candidate_memory` instead, it must justify why that is better and ensure empty/filtered pages can still be tracked without fake manga rows.

### Discovery Planner Changes

Update `RecommendationDiscoveryPlanner` so it no longer treats "page exists in candidate memory" as the only progress signal.

Required behavior:

- Determine the next page from evaluated progress, not only remembered successful candidates.
- If page 1 was already evaluated, page 2 can be probed.
- If page 2 was evaluated but empty/filtered, page 3 can still be probed on a later refresh.
- Continue across refreshes beyond page 3 if a bounded configuration allows it.
- Keep per-refresh work small.

Recommended constants:

- `MAX_NEW_PAGES_PER_SOURCE_REFRESH = 1` by default.
- `MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE = 20` or keep the current `20`.
- A practical upper bound such as `MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY = 20` initially, unless existing source pagination makes this unsafe.

Do not make a single refresh scan dozens of pages. The purpose is slow, steady improvement over time.

### Refresh Merge Logic Changes

In `BrowsePersonalRecommendationsScreenModel.searchSource()`:

1. Load remembered candidates.
2. Load discovery progress/evaluated pages.
3. Fetch the next unevaluated discovery page.
4. Score the fetched candidates.
5. Upsert scored candidates.
6. Record progress even when no scored candidates exist.
7. Resolve remembered candidates after upserting new ones, or include both old remembered candidates and current-run candidates in the merge.
8. Use `RecommendationCandidateMemoryRanker.merge()` or an improved equivalent to re-score all candidates.
9. Return the merged best candidates.

Important fix:

- If additional page candidates are found on the first run, they must be eligible for the current display result, not only stored for a later refresh.

### Filtering Rules

All existing For You filters must continue to apply:

- favorites excluded where current behavior excludes them;
- liked/loved/disliked/known visibility respected;
- seen manga excluded where required;
- hide-known setting respected;
- minimum chapter count respected;
- blocked tags respected;
- duplicates handled according to existing Top Picks/source row behavior.

Do not allow remembered candidates to bypass filters. Remembered candidates should be re-scored and re-filtered every time they are displayed.

### Taste Changes And Reset

The current reset discovery memory option should remain. Claude should also ensure:

- Taste/profile fingerprint changes do not blindly reuse stale ordering.
- Remembered candidates may be re-scored under the new profile.
- Discovery progress can remain if the query signature is unchanged, but if query tags/strategy change, the new query signature should naturally create a new progress path.
- The reset control clearly resets candidate discovery/progress memory, not the user ratings.

## Part 2 - Group-Based Recommendations From Rated Manga

### Current Behavior To Fix

The Loved/Liked/Disliked screens now exist, but group-seeded recommendations may still behave as though only one representative manga matters. The user expects:

- if a manga is grouped across several sources;
- and the user asks for recommendations from that group;
- the seed should represent the group as a whole;
- tags/genres/names/source metadata from all grouped versions should contribute;
- the recommendation UI should feel similar to existing manga-detail recommendations/source rows where practical.

### Desired Behavior

For a rated manga group:

1. Resolve all confirmed cross-source linked members for the group.
2. Include the representative manga plus linked installed-source versions.
3. Fetch or reuse available metadata from each member, bounded and safely.
4. Build a weighted group seed from all member metadata.
5. Use that group seed to search recommendations.
6. Exclude all seed group members from results.
7. Score results by both:
   - the user taste profile;
   - the specific group seed similarity.
8. Show results without endless loading.
9. Opening a result must not crash if a local manga row is missing.

### Reuse Existing Functionality

Claude must avoid making a parallel grouping implementation.

Reuse or extend:

- `manga_cross_source_link` for confirmed same-manga identity.
- `LovedMangaGrouper` or related rated manga grouping helpers.
- `GroupRecommendationSeedBuilder`.
- `GroupSeedRecommendationScorer`.
- existing manga-detail recommendation conventions where they fit.

If `MangaScreenModel` or the manga-detail recommendations use a source-native related/recommendations call, Claude should inspect whether that can be reused for each group member. If it is too coupled to the manga detail screen, do not force reuse. Instead, document why and keep the group recommendation pipeline source-row based but visually consistent.

### Metadata Enrichment

The group seed should use:

- all available genres/tags from the local manga rows;
- metadata fetched from linked installed-source members where safe;
- title variants from group members;
- source information if already available;
- existing tag aliases/synonyms.

Enrichment must be bounded:

- Do not fetch metadata for unlimited group members.
- Use existing source calls safely on background dispatchers.
- Catch per-source/per-member errors.
- Continue with partial metadata if one member fails.

### Rating State Exclusivity

The implementation must preserve the rule:

- a manga should not simultaneously be Love, Like, and Dislike.

If a user changes rating, the latest rating wins. This should use the existing rating/taste upsert system where possible. For grouped versions:

- when a user rates other versions through the matching flow, the selected versions should receive the new rating;
- previous conflicting rating states for those exact manga keys should be overwritten;
- Loved/Liked/Disliked collection screens should not show the same exact manga in multiple rating categories.

Do not invent a new rating table unless the existing taste table cannot support this. If the current data model already enforces one rating per manga key, add tests proving it.

### Rated Manga UI Parity

Verify that Loved, Liked, and Disliked screens share the same capabilities:

- sort chips;
- group clear duplicates;
- source/version badges;
- manage cross-source links;
- export/share where applicable;
- recommendations entry/action;
- installed-source filtering;
- safe empty states;
- no crash when opening results.

If a capability is intentionally only for Loved Manga, document why. Otherwise, parity should be restored by using a reusable rated collection screen.

## Part 3 - Error Handling And UX

### For You Errors

For You should not surface raw exception text for expected source limitations. It should:

- log source failures;
- continue other sources;
- show a source row as no matches/error only where appropriate;
- avoid breaking Top Picks;
- avoid infinite loading.

### Group Recommendation Errors

Fix or guard:

- endless loading when opening recommendations from Loved/Liked/Disliked groups;
- crash when selecting/opening a recommended manga;
- null local manga from SQLDelight queries;
- deleted/uninstalled source entries;
- missing source metadata;
- source search exceptions.

### Source Evaluation Error Separation

The screenshot showing `UnsupportedOperationException` in Source Evaluation is not the same as the For You issue. Do not solve Source Evaluation broadly in this plan unless the same helper code is directly touched. However:

- do not let Source Evaluation errors be confused with For You candidate discovery;
- if shared query helpers are changed, ensure Source Evaluation remains compiling and safe;
- if easy, classify unsupported search/filter behavior as unsupported rather than repeatedly displaying noisy raw errors.

## Part 4 - Documentation Updates

Claude must update or create:

- `docs/recommendations/KMK_RECS_V0_7_39_FOR_YOU_ROLLING_DISCOVERY_AND_RATED_GROUP_RECOMMENDATIONS_IMPLEMENTATION.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` if schema/storage changes affect local derived data or privacy
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` if schema/migration changes are added
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

The implementation report must include:

- date;
- version label;
- user-approved scope;
- files changed;
- behavior changed;
- tests run;
- known limitations;
- deviations;
- APK output only after all phases are complete.

## Part 5 - Tests

Add or update focused tests. Do not rely only on manual testing.

### Required Pure/Unit Tests

For discovery planner:

- empty progress chooses the first live/discovery path correctly;
- evaluated page 1 causes page 2 to be selected next;
- empty/filtered page 2 still counts as evaluated;
- repeated refresh does not retry page 2 after it was marked empty/filtered;
- max discovery page is respected;
- one refresh does not exceed the per-source page cap.

For memory/ranking:

- old high-score candidate remains above weaker new candidates;
- stronger new candidate replaces weaker old candidate in display order;
- remembered candidates are re-scored when the taste profile changes;
- remembered candidates still obey seen/rated/hide-known filters;
- additional candidates found on the current refresh are eligible for immediate display.

For rated/group recommendations:

- group seed includes tags from multiple linked manga entries;
- linked group members are excluded from recommendation results;
- partial metadata failure does not fail the whole group;
- missing local manga row is handled without crash;
- one manga key cannot appear in multiple rating categories after rating updates;
- Liked/Disliked screens retain Loved Manga feature parity.

For schema/migration:

- any new SQLDelight table/columns compile;
- migration test range is updated if a migration is added;
- migration creates the new progress table/columns;
- existing data remains readable.

### Required Verification Commands

Run at minimum:

```text
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If a command cannot run, document why and do not claim the build is ready.

## Acceptance Criteria

This plan is complete only when:

- For You refresh can evaluate a new bounded candidate batch/page over time.
- Evaluated empty/filtered/error pages are tracked so they are not retried forever.
- New candidates are merged with remembered candidates and the best overall candidates are displayed.
- Older good candidates remain visible when newer candidates are worse.
- Taste changes re-score remembered candidates.
- Group-based recommendations use the full rated manga group, not only one representative.
- Loved/Liked/Disliked screens have consistent feature parity unless a documented exception exists.
- Opening group recommendation results does not crash.
- Endless loading in group recommendations is fixed.
- Documentation reflects actual implemented behavior.
- Tests cover the changed pure logic and any schema migration.
- The final APK is built only after all phases are implemented and verified.

## Implementation Order

Claude should work in this order:

1. Read and summarize the current v0.7.38 code path.
2. Implement discovery progress storage and migration.
3. Update discovery planner and tests.
4. Update For You search/merge flow and tests.
5. Verify hide-known/seen/rated filtering still applies to remembered candidates.
6. Review current rated/group recommendation implementation.
7. Fix group seed construction to use full linked groups.
8. Fix group recommendation loading/result-opening safety.
9. Verify Loved/Liked/Disliked UI parity.
10. Update docs/release notes.
11. Run verification.
12. Build final APK only if all previous steps pass.

## Claude Stop Conditions

Claude must stop and ask for clarification if:

- implementing progress storage requires deleting or resetting existing user data;
- the source pagination behavior does not support reliable page advancement;
- reusing manga-detail recommendation code would require risky navigation or screen-model coupling;
- a migration number conflict appears;
- tests reveal that current rating storage can actually hold conflicting ratings for the same manga key;
- any required verification command fails in a way Claude cannot fix safely.


