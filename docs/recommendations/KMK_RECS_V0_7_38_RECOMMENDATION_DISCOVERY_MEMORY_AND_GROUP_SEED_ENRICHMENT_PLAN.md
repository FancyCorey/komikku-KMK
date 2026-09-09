# KMK-Recs v0.7.38 Recommendation Discovery Memory And Group Seed Enrichment Plan

Date: 2026-07-10

Status: Planning. Do not implement until explicitly approved.

Target implementation style: phased implementation is allowed, but Claude must not build or package an APK until every approved v0.7.38 phase in the current pass is complete, documented, formatted, tested, and verified.

## Purpose

This plan covers two related recommendation problems that are now visible after v0.7.35-v0.7.37:

1. Browse > For You keeps searching the same early candidate window from each source. Refreshing can repeat the same top candidates instead of gradually exploring deeper candidates and ranking the best discoveries over time.
2. Rated Manga "Recommendations from this" uses the existing cross-source group entry point, but the actual recommendation seed can still behave like it only represents one manga entry because `GroupRecommendationSeedBuilder` mostly reads locally stored genres from the linked manga rows. If linked versions exist but their local metadata is sparse, their source tags are not meaningfully contributing.

The user expectation is:

- For You should keep the best candidates already found and discover additional candidates over time.
- A refresh should not discard a good manga from an earlier batch just because a newer batch was evaluated.
- Group-seeded recommendations from Loved/Liked manga should use the confirmed cross-source group and its combined metadata, not only the clicked entry's tags.
- The implementation must be careful, bounded, local-first, documented, and testable.

## Required Reading Before Coding

Claude must read these before changing code:

- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/DOCUMENTATION_RULES.md`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_36_RATED_MANGA_UI_PARITY_AND_GROUP_RECS_CRASH_FIX_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_37_GROUP_SEEDED_RECOMMENDATIONS_LOADING_FIX_IMPLEMENTATION.md`

The encyclopedia is mandatory because it points to the current security, database, backup/sync, UI, testing, and versioning documents. Do not rely only on this plan.

## Current Code Facts To Verify First

Claude must inspect these files before implementing:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/RecommendationQueryPlanner.kt`
- `app/src/main/java/exh/recs/RecommendationCandidateEnricher.kt`
- `app/src/main/java/exh/recs/PersonalRecommendationScorer.kt`
- `app/src/main/java/exh/recs/CombinedPicksAccumulator.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationSeed.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationSeedBuilder.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationLoopPolicy.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt`
- `app/src/main/java/exh/recs/loved/LovedMangaSourceFilter.kt`
- `data/src/main/sqldelight/tachiyomi/data/recommendation_cache.sq`
- `data/src/main/sqldelight/tachiyomi/data/manga_taste.sq`
- `data/src/main/sqldelight/tachiyomi/data/manga_cross_source_link.sq`
- latest migration under `data/src/main/sqldelight/migrations/`
- `app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt`

Known current behavior as of v0.7.37:

- For You gets `topSearchTags(profile)` from taste profile data.
- It searches up to `MAX_SOURCE_ATTEMPTS = 40` sources in `SOURCE_BATCH_SIZE = 5`.
- It stops after `MAX_VISIBLE_SOURCE_ROWS = 20` useful rows.
- Normal rows display 10 results; boosted rows display 20 results.
- It calls `source.getSearchManga(1, ...)` only on page 1 for each query plan.
- It enriches missing candidate metadata through `RecommendationCandidateEnricher`.
- It scores with `PersonalRecommendationScorer.rankCandidates`.
- It builds Top Picks with `CombinedPicksAccumulator`.
- The existing `recommendation_cache` is a TTL/fingerprint result cache, not a durable candidate discovery pool.
- `GroupRecommendationSeedBuilder` uses `manga_cross_source_link` to find group members, then reads local `Manga.genre` rows and counts tags.
- `GroupSeededRecommendationsScreenModel` now localizes candidates before showing them and has v0.7.37 timeouts, but the seed itself can still be weak if group members have poor local genre data.

## Non-Goals

Do not implement these in v0.7.38:

- Do not crawl an entire source catalogue.
- Do not fetch every manga from every source.
- Do not replace confirmed cross-source identity with fuzzy title matching.
- Do not install/uninstall extensions as part of this feature.
- Do not add candidate memory to backup/sync unless the user explicitly approves it later.
- Do not store full manga descriptions in the new memory tables unless needed for a clearly tested reason.
- Do not change the meaning of Love, Like, Dislike, or Seen.
- Do not regress the v0.7.37 invariant that one confirmed cross-source group appears under only one rating category, based on the latest rating in that group.

## Core Design

There are two layers:

1. **Discovery memory:** remember candidate manga already found from source searches, then rank all known candidates together on later refreshes.
2. **Seed enrichment:** when a recommendation starts from a Loved/Liked group, build the seed from all confirmed linked versions and enrich missing metadata in a bounded way.

These are related but should not be tangled. Use small helpers and interactors rather than adding more orchestration directly into screen models.

## Track A: For You Candidate Discovery Memory

### Problem

For You currently searches page 1 of a few query plans. A refresh can keep returning the same candidates. If the same first 60 candidates are repeatedly processed, the user gets stuck seeing the same recommendation surface even when a source has many more manga that may be better.

The desired model:

- First refresh discovers candidates 1-60.
- Second refresh should try to discover additional candidates, such as 61-120 where the source supports pagination or alternative query windows.
- Display should rank the best candidates from all known candidates, not only the newest batch.
- Old strong candidates stay visible if they still score higher than newly discovered candidates.

### Database Plan

Add a new SQLDelight table for derived, local-only recommendation candidate memory. Claude must check the latest migration number first. If the latest migration is `54.sqm`, use `55.sqm`; otherwise use the next available number.

Recommended table name:

`recommendation_candidate_memory`

Recommended fields:

- `source_id INTEGER NOT NULL`
- `url TEXT NOT NULL`
- `manga_id INTEGER`
- `title TEXT NOT NULL`
- `thumbnail_url TEXT`
- `normalized_title TEXT NOT NULL`
- `last_score REAL NOT NULL DEFAULT 0.0`
- `matched_groups_json TEXT`
- `result_reasons_json TEXT`
- `query_signature TEXT NOT NULL`
- `query_tags_json TEXT NOT NULL`
- `query_strategy TEXT`
- `page INTEGER NOT NULL DEFAULT 1`
- `discovered_at INTEGER NOT NULL`
- `last_scored_at INTEGER NOT NULL`
- `last_seen_at INTEGER NOT NULL`
- `profile_fingerprint TEXT`
- `filtered_reason TEXT`

Recommended primary key:

- `PRIMARY KEY(source_id, url)`

Recommended indexes:

- `CREATE INDEX IF NOT EXISTS recommendation_candidate_memory_source_index ON recommendation_candidate_memory(source_id);`
- `CREATE INDEX IF NOT EXISTS recommendation_candidate_memory_score_index ON recommendation_candidate_memory(last_score);`
- `CREATE INDEX IF NOT EXISTS recommendation_candidate_memory_seen_index ON recommendation_candidate_memory(last_seen_at);`
- `CREATE INDEX IF NOT EXISTS recommendation_candidate_memory_query_index ON recommendation_candidate_memory(query_signature);`

Create `data/src/main/sqldelight/tachiyomi/data/recommendation_candidate_memory.sq` with typed queries:

- `getAll`
- `getBySource`
- `getBySources`
- `getBySourceUrl`
- `upsert`
- `deleteBySourceUrl`
- `deleteBySource`
- `deleteAll`
- `pruneOldestBySource`
- `countBySource`

Do not add this table to backup/sync in this pass. It is derived cache-like data and should be clearable.

### Domain / Helper Plan

Create small domain or rec-layer helpers rather than overloading `BrowsePersonalRecommendationsScreenModel`.

Suggested files:

- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryEntry.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryStore.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt`
- `app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt`

Responsibilities:

- Store maps SQLDelight rows to domain data and hides JSON parsing failures.
- Ranker accepts remembered local manga plus newly fetched manga, re-scores with the current `TasteProfile`, and returns the best results.
- Planner decides what pages/plans still need probing, avoiding infinite loops when a source repeats identical results.

Keep these helpers mostly pure where possible so tests are easy.

### For You Integration

In `BrowsePersonalRecommendationsScreenModel`:

1. Load remembered candidates for the currently eligible source list.
2. Re-score remembered candidates against the current taste profile.
3. Run live source searches to discover additional candidates.
4. Upsert newly discovered candidates into memory.
5. Merge remembered + new candidates.
6. Rank the merged set.
7. Show the best candidates per source row and Top Picks from the merged ranked set.

Important behavior:

- Remembered candidates must remain eligible even if the newest search batch did not return them.
- Remembered candidates must still be filtered by current settings:
  - favorites / already rated visibility
  - Seen manga
  - Hide known manga
  - min chapter count
  - blocked tags
  - installed/enabled source status
- If a remembered candidate cannot be resolved to a valid local manga, skip it and optionally delete or refresh the memory row.
- If profile inputs change, do not wipe the memory automatically. Re-score memory with the new profile.

### Discovery Progression

Current For You uses page 1 only. Add bounded progression carefully:

- Track which `(source_id, query_signature, query_strategy, page)` has been discovered.
- On refresh, if page 1 results are already known, try page 2, then page 3, within a small cap.
- Recommended cap: at most 3 pages per source per refresh.
- Recommended new-candidate cap: at most 20 newly processed candidates per source per refresh.
- Recommended stored cap: at most 500 candidates per source initially. Prune oldest or lowest-use candidates only after verifying no active UI row depends on them.

Avoid a source loop:

- If page N returns the same URLs as a previous page, mark that plan/source as exhausted for that refresh.
- If a source errors, record the status but do not delete its existing remembered good candidates.
- If all pages are exhausted, show known best candidates and record that there are no new candidates for that query.

### UI / Settings

Keep UI minimal:

- Add a clear action in Recommendation Settings or For You settings: `Reset For You discovery history`.
- Add an optional action: `Reassess For You candidates`.

Behavior:

- `Reset For You discovery history` clears only `recommendation_candidate_memory`.
- It must not clear ratings, seen manga, cross-source links, source preferences, or source evaluation data.
- `Reassess For You candidates` re-scores remembered candidates against the current taste profile and current filters.

Do not add a large new dashboard unless the user asks later.

### Failure Handling

For You must not crash if memory fails.

Handle:

- Missing table after bad migration: show current live-search behavior and log/record an error. Migration tests should prevent this.
- Bad JSON in `matched_groups_json` or `result_reasons_json`: ignore the field for that row.
- Local manga missing for stored `manga_id`: resolve by `(source_id, url)` if possible; otherwise skip.
- Source search timeout: keep remembered candidates and continue.
- Candidate enrichment timeout: keep current behavior; candidate can still be scored if metadata is enough.
- Cancellation: rethrow `CancellationException`; do not swallow navigation/lifecycle cancellation.

## Track B: Group-Seeded Recommendation Enrichment

### Problem

Loved/Liked manga grouping already exists and is confirmed through `manga_cross_source_link`. However, group-seeded recommendations can still feel like they are based on one manga entry because the seed builder currently relies on local genre rows from linked members. If most linked members have not had details fetched, only one source's tags may meaningfully contribute.

### Seed Model Changes

Extend `GroupRecommendationSeed` carefully.

Current likely fields:

- `primaryTitle`
- `titles`
- `tags`
- `sourceIds`
- `memberKeys`
- `groupId`

Recommended additions:

- `tagWeights: Map<String, Double>` or equivalent serializable/pure structure.
- `tagSources: Map<String, Int>` or `tagCounts: Map<String, Int>` for explainability/testing.
- `metadataMemberCount: Int`
- `enrichedMemberCount: Int`

If adding maps complicates Compose/state serialization, use a simple list data class:

```kotlin
data class GroupSeedTag(
    val name: String,
    val weight: Double,
    val memberCount: Int,
)
```

Then `GroupRecommendationSeed` can hold `seedTags: List<GroupSeedTag>` plus a compatibility `tags: List<String>` if needed.

### Seed Builder Changes

In `GroupRecommendationSeedBuilder`:

1. Resolve the confirmed cross-source group through `manga_cross_source_link`.
2. Load all linked local manga rows by `(source, url)`.
3. Collect titles from all linked rows.
4. Collect local genres from all linked rows.
5. For linked rows with missing or weak genre metadata, do bounded detail enrichment:
   - Resolve `CatalogueSource` by source ID.
   - Build/use an `SManga` from the local manga row using the existing project conversion pattern.
   - Call `source.getMangaDetails(smanga)` on the IO dispatcher only.
   - Localize updated details through `NetworkToLocalManga` if needed.
6. Merge all genre/tag data into a weighted seed.

Recommended bounds:

- Maximum group members to detail-enrich: 8.
- Per-member detail timeout: 5 seconds.
- Total seed enrichment timeout: 20 seconds.
- Continue with partial seed on timeout or failure.

Do not enrich every linked member blindly if local metadata is already sufficient.

Suggested threshold:

- If a member has at least 2 nonblank genres locally, use local metadata and skip detail fetch for that member.
- If total seed has at least 8 useful tags across at least 2 sources, do not enrich additional members unless they are the clicked primary member.

### Seed Scoring Changes

The current implementation boosts base `TasteProfile.learnedTagWeights` by `+0.3` for seed tags capped at `0.9`. That is too weak for a specific "recommend from this manga" workflow.

Add a group-seed scoring layer:

- Keep `PersonalRecommendationScorer` as the personal taste baseline.
- Add a helper such as `GroupSeedRecommendationScorer`.
- Candidate final score should be:

```text
personalScore + groupSeedScore
```

Recommended groupSeedScore:

- Match against candidate genres normalized through existing alias/tag logic.
- Strong match for tags appearing in multiple linked members.
- Smaller match for tags appearing in only one linked member.
- Hard reject still comes from blocked tags in `PersonalRecommendationScorer`.
- Candidate that matches no group seed tags can still appear if personal score is good, but it should not outrank strong group matches by default.

Keep source affinity from personal profile, but do not let source affinity overpower seed similarity.

### Query Strategy For Group Seeds

In `GroupSeededRecommendationsScreenModel`:

- Build query tags from the weighted seed first.
- Use personal profile tags only as fallback when the group seed is empty or too weak.
- Keep v0.7.37 bounds:
  - total timeout
  - per-search timeout
  - per-candidate localization timeout
  - target result cap
  - cancellation rethrow
- Do not increase source count dramatically in this pass.

Recommended behavior:

- Use top 5 seed tags by weight/frequency as query tags.
- If top seed tags are too broad or no results are found, fallback to current `RecommendationQueryPlanner` alternatives.
- Exclude all seed `memberKeys`, not just the clicked member.

### Optional Group Candidate Memory

This can be Phase 2 or Phase 3 if Phase 1 is large.

Add a second context to candidate memory rather than a completely separate duplicate system:

- `context_type TEXT NOT NULL` with values such as `FOR_YOU` and `GROUP_SEED`.
- `context_key TEXT NOT NULL`
  - For For You: a profile/language/source context key.
  - For group seed: `group:<group_id>` when group exists; otherwise `source:<source_id>|url:<url>`.

If adding context to the same table is too risky after the first migration design, use a separate table:

`group_recommendation_candidate_memory`

Recommended fields:

- `context_key`
- `source_id`
- `url`
- `manga_id`
- `title`
- `last_score`
- `matched_seed_tags_json`
- `discovered_at`
- `last_scored_at`
- `query_signature`
- `page`

Behavior:

- Group refresh can discover more candidates over time.
- Old strong group candidates remain available.
- Candidates are re-scored when group seed tags or personal taste profile changes.

If time is limited, implement enriched group seed first and defer group candidate memory with a documented note. Do not pretend group candidate memory shipped unless it is implemented and tested.

## Implementation Phases

Claude may implement this in phases, but must not build the APK until the approved v0.7.38 phases in the current pass are complete.

### Phase 0: Audit And Baseline Documentation

Before coding:

1. Confirm exact latest migration number.
2. Confirm current group seed builder behavior.
3. Confirm current For You cache/memory behavior.
4. Confirm current tests for:
   - `PersonalRecommendationScorerTest`
   - `CombinedPicksAccumulatorTest`
   - `GroupRecommendationLoopPolicyTest`
   - `RatedMangaExclusivityTest`
   - `KmkMigrationTest`
5. Add notes to the implementation report draft.

No APK build in Phase 0.

### Phase 1: Group Seed Enrichment First

Implement:

- Expanded `GroupRecommendationSeed`.
- Bounded enrichment in `GroupRecommendationSeedBuilder`.
- Weighted group seed helper/scorer.
- `GroupSeededRecommendationsScreenModel` scoring/query changes.
- Tests for seed metadata from all confirmed linked versions.

Reason for doing this first:

- It directly fixes the user's current complaint about Loved/Liked group recommendations.
- It is more contained than For You candidate memory.
- It reduces risk before adding a new persistent table.

### Phase 2: For You Candidate Memory Schema And Helpers

Implement:

- SQLDelight table and migration.
- Store/ranker/planner helpers.
- Migration tests.
- Pure unit tests for merge/rank/prune behavior.

Do not integrate into the UI until the helper tests pass.

### Phase 3: For You Integration

Implement:

- Load memory before live search.
- Re-score remembered candidates.
- Search for new candidates using bounded page progression.
- Upsert new candidates.
- Merge old + new.
- Rank all candidates together.
- Preserve Top Picks behavior with merged results.
- Preserve existing source status behavior.

Add tests around extracted helpers if direct screen model tests are impractical.

### Phase 4: Reset/Reassess Controls And Documentation

Implement:

- Clear discovery history action.
- Reassess candidates action if practical.
- KMR strings for all user-facing text.
- Documentation updates.
- Release note update.

Only after Phase 4 should Claude run final verification and build.

## Specific Test Requirements

### Group Seed Tests

Add or update tests under `app/src/test/java/exh/recs/group/`.

Required cases:

1. A linked group with three members uses tags from all three members.
2. The clicked manga is not the only tag source.
3. A member with missing local genres can be enriched from source details.
4. A failing member detail call is skipped and does not fail the whole seed.
5. Timeout returns a partial seed.
6. Seed member keys exclude all linked versions from results.
7. Candidate with group seed tag overlap ranks above a candidate with only generic personal affinity.
8. Blocked tags still reject candidates even if they match group seed tags.

### For You Memory Tests

Add tests for new helpers.

Required cases:

1. Upsert same `(source_id, url)` updates instead of duplicating.
2. Refresh can merge old candidates with new candidates.
3. Old high-scoring candidate remains above lower-scoring new candidate.
4. New high-scoring candidate can outrank old candidate.
5. Known/rated/seen/blocked filtering still applies to remembered candidates.
6. Bad JSON in stored reasons/matched groups does not crash.
7. Missing local manga row is skipped.
8. Planner advances from page 1 to page 2 when page 1 is already known.
9. Planner stops when a source repeats the same URLs on the next page.
10. Prune keeps storage bounded.

### Migration Tests

Update `KmkMigrationTest`:

- New table exists after migration.
- Indexes exist if the existing migration test style supports checking them.
- Migration from the previous version succeeds.

### Regression Tests

Run or update tests covering:

- `PersonalRecommendationScorerTest`
- `CombinedPicksAccumulatorTest`
- `GroupRecommendationLoopPolicyTest`
- `RatedMangaExclusivityTest`
- `LovedMangaSourceFilterTest`
- `KmkMigrationTest`

Rating exclusivity must remain true:

- A confirmed cross-source group with LOVE then LIKE should display only in LIKE if LIKE is the latest group rating.
- A confirmed group must not show simultaneously in Love, Like, and Dislike.

## Manual QA Checklist

Claude should document manual QA steps even if it cannot run the APK on device.

Required flows:

1. Open For You.
2. Refresh once.
3. Note a strong result.
4. Refresh again.
5. Confirm additional candidates can be discovered.
6. Confirm strong old result is not removed simply because it came from the first batch.
7. Open Loved Manga.
8. Open a grouped loved manga recommendation through Explore or long-press.
9. Confirm it loads within timeout.
10. Tap a result and confirm it opens a valid manga page.
11. Confirm no indefinite spinner.
12. Confirm no crash from missing local manga ID.
13. Confirm Seen/Hide known/Blocked tags still filter remembered candidates.
14. Use Reset discovery history and confirm only discovery memory is cleared.

## UI And String Rules

- Follow existing Komikku/KMK formatting patterns.
- All user-facing strings must use KMR string resources.
- Do not hardcode English UI strings.
- Keep the UI minimal and consistent with existing Recommendation Settings.
- Do not add large explanatory paragraphs into the UI.
- Do not put internal implementation details into What's New.

## Security And Privacy

Candidate memory is local preference-derived data.

Rules:

- Do not upload it.
- Do not sync it.
- Do not include it in backup unless the user separately approves.
- Store minimal metadata only.
- Provide a clear/reset path.
- Avoid storing descriptions unless a later feature proves they are necessary.
- Treat source URLs and titles as potentially sensitive reading preference data.

Update `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` if the new table or behavior changes the privacy model.

## Documentation Requirements

When implemented, update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/recommendations/KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_AND_GROUP_SEED_ENRICHMENT_IMPLEMENTATION.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md` if the new implementation report becomes a primary reference
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` if a database table/migration is added
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` if local preference-derived storage is added
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

The implementation report must state:

- What was implemented.
- What was deferred.
- Migration number used.
- Tests added/updated.
- Verification commands and results.
- Whether group candidate memory was implemented or deferred.
- Whether candidate memory is backup/sync excluded.

## Verification Commands

Run these before any APK build:

```powershell
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If a command fails, fix the cause and rerun it. Do not produce an APK for the user until all required approved phases and verification pass.

## Acceptance Criteria

v0.7.38 is acceptable only if:

- Group-seeded recommendations use metadata from the confirmed cross-source group, not just the clicked manga entry.
- Sparse linked members can be enriched in a bounded way.
- Group recommendations do not spin forever.
- Group recommendation results open safely without null local manga crashes.
- For You can preserve old strong candidates while discovering new candidates.
- For You ranks all known eligible candidates together.
- For You does not repeatedly process only the same first-page candidates when additional pages are available.
- Candidate memory remains bounded and clearable.
- Existing rating exclusivity remains intact.
- Existing filters still apply to remembered candidates.
- All new user-facing text is localized through KMR strings.
- Documentation is updated in the same implementation session.
- Tests and debug build pass.

## Claude Implementation Instruction Summary

Implement carefully in phases, but do not build/package until the full approved v0.7.38 scope for this pass is complete.

Start with Phase 0 audit, then Phase 1 group seed enrichment. Continue to Phase 2-4 only if the user approves implementing the full plan in one pass. Do not silently skip documentation or tests. Do not create another parallel recommendation system where existing group, scorer, cache, and rating infrastructure can be reused.

