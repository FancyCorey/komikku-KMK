# KMK-Recs v0.7.38 Implementation: Recommendation Discovery Memory and Group Seed Enrichment

Date: 2026-07-09
Status: Complete. Build: `Komikku-v1.13.6-kmk.7.38-debug.apk`

---

## Overview

Two parallel tracks delivered in v0.7.38:

**Track A â€” For You Candidate Discovery Memory**: Discovered candidate manga are stored locally after each For You refresh. On subsequent refreshes, the remembered candidates are merged with newly discovered ones and re-ranked against the current taste profile. Strong past candidates are not discarded just because they did not appear in the latest source search.

**Track B â€” Group-Seeded Recommendation Enrichment**: The recommendation seed for group-seeded searches is now built from ALL confirmed linked manga versions. Sparse group members (fewer than 2 local genres) are enriched with live metadata in a bounded, time-limited window. Tags contributed by multiple group members receive higher weight in scoring via a new `GroupSeedRecommendationScorer`.

---

## Track A: For You Candidate Discovery Memory

### Database (Migration 56)

**File**: `data/src/main/sqldelight/tachiyomi/migrations/56.sqm`

Creates `recommendation_candidate_memory` table (18 columns, PRIMARY KEY = `(source_id, url)`, 4 indexes). This table is:
- Local-only (NOT included in backup/sync proto fields)
- Clearable by the user via Settings â†’ Management â†’ Reset For You discovery history
- Not OCR-related; separate from the v0.7.36 OCR tables

**File**: `data/src/main/sqldelight/tachiyomi/data/recommendation_candidate_memory.sq`

Queries: `getAll`, `getBySource`, `getBySources`, `getBySourceUrl`, `getBySourceQuery`, `getDistinctPagesBySourceQuery`, `countBySource`, `upsert` (with `ON CONFLICT DO UPDATE SET ... page = MAX(page, :page)`), `deleteBySourceUrl`, `deleteBySource`, `deleteAll`, `pruneOldestBySource`.

### Domain Layer

**`domain/src/main/java/tachiyomi/domain/taste/model/RecommendationCandidateMemory.kt`** â€” 18-field domain model.

**`domain/src/main/java/tachiyomi/domain/taste/repository/RecommendationCandidateMemoryRepository.kt`** â€” interface.

**Interactors** in `domain/src/main/java/tachiyomi/domain/taste/interactor/`:
- `GetRecommendationCandidateMemory` â€” 7 read variants
- `UpsertRecommendationCandidateMemory` â€” `await(entry)`
- `DeleteRecommendationCandidateMemory` â€” by source URL or by source
- `PruneRecommendationCandidateMemory` â€” `awaitIfNeeded(sourceId)`, cap = 500 per source
- `ClearRecommendationCandidateMemory` â€” `await()` clears ALL entries

### Data Layer

**`data/src/main/java/tachiyomi/data/taste/RecommendationCandidateMemoryRepositoryImpl.kt`** â€” wraps SQLDelight queries. `getDistinctPagesBySourceQuery` maps `Long` â†’ `Int`.

### App Layer

**`app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryEntry.kt`** â€” app-layer struct with pre-parsed JSON fields (`List<String>` instead of raw JSON blobs).

**`app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryStore.kt`** â€” wraps the three interactors with JSON-safe helpers, `upsertBatch()`, `pruneIfNeeded()`, and title normalization.

**`app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt`** â€” `internal fun merge(...)`: deduplicates by local manga ID, re-scores all candidates with `PersonalRecommendationScorer`, applies favorite / seen / known / shouldHideForYou filters, returns top `limit` by score descending.

**`app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt`** â€” pure helper:
- `nextPageToProbe(knownPages)` â†’ null if no pages known yet (first run) or cap (3) reached; otherwise `maxKnown + 1`
- `isExhausted(newPageUrls, previouslyKnownUrls)` â†’ true if new page returned only known URLs

### BrowsePersonalRecommendationsScreenModel Changes

**Constructor additions**: `getMemory`, `upsertMemory`, `pruneMemory` (all injected via Injekt).

**Memory store field**: `private val memoryStore = RecommendationCandidateMemoryStore(...)`.

**Cache-hit path**: loads `remembered` and `knownPages` before the cache check; on cache hit, merges remembered + cached via `RecommendationCandidateMemoryRanker.merge()`.

**Plans-loop success path**:
1. Upserts page-1 results to memory
2. Calls `discoverAdditionalPage()` â†’ probes page 2 (or 3) if the planner says to
3. Upserts additional results if non-empty
4. Prunes memory for the source
5. Resolves memory entries to local `Manga` via `resolveMemoryEntries()`
6. Merges remembered + all new via `RecommendationCandidateMemoryRanker.merge()`
7. Returns merged results

**New private helpers**:
- `resolveMemoryEntries(entries)` â€” resolves each entry by `mangaId` or `(url, sourceId)` via `getMangaInteractor`
- `discoverAdditionalPage(source, knownPages, searchParams, ...)` â€” fetches page N, applies filters, scores up to `MAX_NEW_CANDIDATES_PER_ADDITIONAL_PAGE = 20`

### DI Registration

**`app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`** â€” added singleton for repository + factories for all 5 interactors.

### Settings: Reset Discovery History

**Strings** in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`:
- `rec_reset_discovery_history`
- `rec_reset_discovery_history_summary`
- `rec_reset_discovery_history_confirm`
- `rec_reset_discovery_history_done`

**`RecommendationsSettingsScreenModel.kt`**: injects `ClearRecommendationCandidateMemory`; adds `requestClearDiscoveryHistory()`, `dismissClearDiscoveryHistoryDialog()`, `confirmClearDiscoveryHistory()`; adds `showClearDiscoveryHistoryDialog: Boolean` to `State`.

**`RecommendationsSettingsScreen.kt`**: `TextButton` in Management section; `AlertDialog` with OK/Cancel in dialogs block.

---

## Track B: Group-Seeded Recommendation Enrichment

### New Types

**`app/src/main/java/exh/recs/group/GroupSeedTag.kt`**:
```kotlin
data class GroupSeedTag(
    val name: String,
    val weight: Double,   // count / totalMembers
    val memberCount: Int,
)
```

### GroupRecommendationSeed Changes

Added `seedTags: List<GroupSeedTag>`, `metadataMemberCount: Int = 0`, `enrichedMemberCount: Int = 0`. Kept `tags: List<String>` (flat list for query building, derived from `seedTags.map { it.name }`).

### GroupRecommendationSeedBuilder Rewrite

**Constructor additions**: `SourceManager`, `NetworkToLocalManga`.

**Constants**: `MIN_LOCAL_GENRES_THRESHOLD=2`, `MAX_ENRICH_MEMBERS=8`, `ENRICH_MEMBER_TIMEOUT_MS=5000L`, `TOTAL_ENRICH_TIMEOUT_MS=20000L`, `EARLY_STOP_TAG_COUNT=8`, `EARLY_STOP_MEMBER_COUNT=2`, `TOP_TAGS_LIMIT=10`.

**Build flow**:
1. Resolves all group members from `manga_cross_source_link`
2. Loads local manga for all members into `mangaByKey`
3. Identifies sparse members (< `MIN_LOCAL_GENRES_THRESHOLD` genres)
4. Bounded enrichment: 20s total, 5s per member, max 8 enrichments â€” calls `source.getMangaDetails(smanga)` on `Dispatchers.IO`, updates `mangaByKey` if genres improved, early-stops if â‰¥ 8 distinct tags from â‰¥ 2 members
5. Builds weighted `seedTags`: `weight = count / totalMembers`

Sparse members with no local DB row use `SManga.create().also { it.url = membUrl }` â€” sufficient for `getMangaDetails()`.

### GroupSeedRecommendationScorer

**File**: `app/src/main/java/exh/recs/group/GroupSeedRecommendationScorer.kt`

- `STRONG_MATCH_THRESHOLD = 2` (member count for 1.0 score)
- `STRONG_MATCH_SCORE = 1.0`, `WEAK_MATCH_SCORE = 0.3`
- `score(candidate, seed, aliasMap)` â†’ normalizes candidate genres via aliasMap â†’ for each genre: looks up in `seedTags` map by name â†’ adds `(1.0 if memberCount â‰¥ 2 else 0.3) * seedTag.weight`
- Returns 0.0 if no seed tags or no candidate genres

### GroupSeededRecommendationsScreenModel Changes

- `queryTags`: uses `seed.seedTags.take(5).map { it.name }` when available, falls back to original logic
- `buildBoostedWeights`: takes `List<GroupSeedTag>`, applies `boost = SEED_BOOST * seedTag.weight`
- `buildRecommendations` success path: adds `groupSeedScore = GroupSeedRecommendationScorer.score(local, seed, aliasMap)` and returns `scored.score + groupSeedScore` as final score

---

## Tests

**`app/src/test/java/exh/recs/group/GroupSeedEnrichmentTest.kt`** (10 tests) â€” verifies tag frequency counting, seed weights, scorer behavior (multi-member vs single-member), alias normalization, empty seed.

**`app/src/test/java/exh/recs/memory/RecommendationCandidateMemoryRankerTest.kt`** (10 tests) â€” verifies empty input, dedup by ID, favorited/seen/known filters, id=0 exclusion, limit cap, sort order.

**`app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt`** â€” range updated to 46..56 (11 files), `56 to listOf("recommendation_candidate_memory")` added to NEW_TABLES_BY_MIGRATION, new test `migration 56 adds recommendation_candidate_memory with all required columns`, full-sequence table set updated.

---

## Limitations (Accepted for v0.7.38)

- `knownIds` scope in the plans-loop success path only covers page-1 new candidates (not memory entries). Memory-only candidates that happen to be "known" won't be excluded by `getKnownMangaIds`. The cache-hit path computes `knownIds` correctly for all candidates.
- Page discovery cap is 3 pages per source per refresh session. Candidates from pages beyond 3 are never probed until memory is cleared.
- Memory is keyed on `(source_id, url)`. If a source changes manga URLs, old entries become stale but are pruned by the 500-entry cap over time.

---

## Files Changed

### New
- `app/src/main/java/exh/recs/group/GroupSeedTag.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryEntry.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryStore.kt`
- `app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt`
- `app/src/main/java/exh/recs/group/GroupSeedRecommendationScorer.kt`
- `data/src/main/sqldelight/tachiyomi/migrations/56.sqm`
- `data/src/main/sqldelight/tachiyomi/data/recommendation_candidate_memory.sq`
- `domain/src/main/java/tachiyomi/domain/taste/model/RecommendationCandidateMemory.kt`
- `domain/src/main/java/tachiyomi/domain/taste/repository/RecommendationCandidateMemoryRepository.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetRecommendationCandidateMemory.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertRecommendationCandidateMemory.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/DeleteRecommendationCandidateMemory.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/PruneRecommendationCandidateMemory.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/ClearRecommendationCandidateMemory.kt`
- `data/src/main/java/tachiyomi/data/taste/RecommendationCandidateMemoryRepositoryImpl.kt`
- `app/src/test/java/exh/recs/group/GroupSeedEnrichmentTest.kt`
- `app/src/test/java/exh/recs/memory/RecommendationCandidateMemoryRankerTest.kt`

### Modified
- `app/src/main/java/exh/recs/group/GroupRecommendationSeed.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationSeedBuilder.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

