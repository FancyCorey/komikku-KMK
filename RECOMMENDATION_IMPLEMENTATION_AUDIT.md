# Recommendation System Implementation Audit

Date: 2026-06-14

Author: Claude Code (audit for Codex handoff)

Status: authoritative implementation state as of KMK-Recs v0.3.2.

This document describes what is **actually implemented** in the codebase, verified by reading source files. It does not repeat planning document content unless that content has been implemented. Where plans were not implemented, this document says so explicitly.

---

## Permanent Documentation Rule

**Every implementation session must update or create a markdown file before the task is considered complete.**

For small changes: update the relevant existing plan or versioning file.
For larger phases: create a new implementation note.

Each implementation note must include:
- Date
- Version/build label if applicable
- Goal
- User-approved scope
- Files changed
- Behavior changed
- Tests run
- APK/build output if applicable
- Known limitations
- Follow-up recommendations
- Any deviations from the original plan

Do not claim something is implemented just because it was planned. Verify against actual code.

---

## System Overview

The Komikku fork implements a personal manga recommendation system with two distinct sub-systems:

1. **Single-manga recommendations** — accessed from the manga detail page. Searches other installed extension sources for manga with similar genres to the currently viewed manga. This is the original Komikku/SY cross-extension recommendation feature, extended by this fork.

2. **For You (Browse tab)** — a taste-profile-driven recommendation feed in Browse. Searches across multiple catalogue sources using the user's explicit ratings and tag preferences. This is the KMK-added feature.

These two sub-systems are separate. They share some utilities (`GenreFilterMapper`, `normalizeTag`) but have independent screen models, data flows, and UI.

---

## Version Trail

### KMK-Recs v0.1.0

**Status: COMPLETE**

Core taste system:
- SQLDelight tables: `manga_taste`, `tag_taste`, `tag_alias`, `recommendation_cache`, `recommendation_disabled_source`
- DB migration: `data/src/main/sqldelight/tachiyomi/migrations/46.sqm`
- Domain models: `MangaTaste`, `MangaRating` (DISLIKE=-1, LIKE=1, LOVE=2), `TagTaste`, `TagPreference` (BLOCK=-2, DISLIKE=-1, PREFER=1), `TagAlias`, `RecommendationCacheEntry`, `TasteProfile`, `TagNormalization`
- Repositories and interactors for all taste entities
- DI registration in `KMKDomainModule`
- `PersonalRecommendationScorer` — hard-blocks blocked groups, scores by explicit prefs, learned weights, source affinity
- For You tab — `BrowsePersonalRecommendationsScreenModel`, `BrowsePersonalRecommendationsTab`
- Cache with 24h TTL
- Manga detail rating UI (`MangaInfoHeader`, `MangaActionRow`, dropdown with Love/Like/Dislike/Clear)
- Settings screen — `RecommendationsSettingsScreen`, `RecommendationsSettingsScreenModel`

### KMK-Recs v0.2.0

**Status: COMPLETE**

Rating and For You refinements:
- Stable manga taste identity by `(source, url)` rather than `manga_id` alone
- `RatedMangaVisibility` enum — HIDE_ALL_RATED, HIDE_DISLIKED_ONLY (default), SHOW_ALL_RATED
- Settings: rated manga visibility preference
- Hide empty source rows in For You
- Browse tab visibility preferences (hide Feed, For You, Migrate tabs)
- Backup/restore for taste data
- `SyncService` feeds merge fix

### KMK-Recs v0.3.0

**Status: COMPLETE**

Quality and efficiency pass:
- `RecommendationSourceOrdering` — manual source priority stored as comma-separated source ids in `SourcePreferences`
- Top-3 boosted sources (BOOSTED_SOURCE_COUNT = 3): 20 results, 10 enrichment, 60 raw candidates
- Normal sources: 10 results, 5 enrichment, 30 raw candidates
- `RecommendationCandidateEnricher` — capped sequential `getMangaDetails()` calls per source
- `RecommendationQueryPlanner` — at most 2 strategy attempts per source per refresh; strategy types: TOP_TAGS_FILTER, SINGLE_STRONGEST_TAG, TAG_PAIR, TEXT_ONLY_TOP_TAGS; strategy memory persisted in `SourcePreferences`
- Alias-aware genre filter matching in `GenreFilterMapper` with `BUILT_IN_SYNONYMS`
- Cache bumped to `personal_v3`
- Deterministic duplicate suppression (reference equality winner, tie-break: score > genre count > manga id)
- `RecommendationsSettingsScreen` — drag-to-reorder source list, "Boosted" badge on top 3, enable/disable switches, "Reset priority" action
- Source drill-down from For You: `BrowseSourceScreen(source.id, ctx?.textQuery)`

### KMK-Recs v0.3.1

**Status: COMPLETE**

Crash and polish patch:
- Fixed drag-to-reorder crash: reorder callback now uses `from.key`/`to.key` (source IDs) instead of item indices, which was incorrect when the LazyColumn contained non-source items
- `RecommendationSourceOrdering.parse()` uses `.distinct()` to remove duplicate ids from stored order
- `setSourceOrder` in screen model accepts full source id list (not from/to index pair) for correctness with key-based reorder

### KMK-Recs v0.3.2

**Status: COMPLETE — built as Komikku-v1.13.6-kmk.3.2-debug.apk**

Source language filter:
- `SourcePreferences.recommendationSourceLanguages()` — StringSet preference, default `setOf("en")`
- `RecommendationSourceFilter` — `filterForRecommendations()`, `isLocalSource()`, `normalizeLanguages()`, `availableLanguages()`, Local Source excluded by id=0 (not display name)
- `RecommendationSourceOrdering.mergeVisibleOrder()` — preserves hidden-language source ids in stored order when visible EN subset is reordered
- For You: language filter applied before ordering and top-20 cap
- Settings: language selector UI (FilterChips, auto-derived from installed sources), source list shows only selected-language sources
- Profile fingerprint includes selected languages — cache invalidates on language change
- Strings: `rec_source_languages`, `rec_source_languages_summary`

---

## How For You Actually Works (Current State)

### Source Selection Pipeline

```
getVisibleCatalogueSources()
  → RecommendationSourceFilter.filterForRecommendations(languages)   [KMK-Recs v0.3.2]
    → RecommendationSourceOrdering.apply(storedOrder, disabledIds)   [KMK-Recs v0.3.0]
      → .take(MAX_SOURCES=20)
        → boostedSourceIds = first 3 of these 20
```

### Per-Source Search Pipeline

```
For each source (parallel, capped at 5 concurrent):
  Check cache (24h TTL + profile fingerprint match) → serve if hit
  Build query plans (RecommendationQueryPlanner, at most 2 attempts)
  source.getFilterList()
  GenreFilterMapper.buildSearch(filterList, tags, aliasCandidates, forceTextOnly)
  source.getSearchManga(page=1, textQuery, filters)
  Take up to rawCap (30 or 60) raw candidates, distinctBy url
  networkToLocalManga → filter favorites and hidden-rated
  RecommendationCandidateEnricher.enrich(source, candidates, smangaByUrl, limit=5 or 10)
  PersonalRecommendationScorer.rankCandidates(enriched, profile, aliasMap, displayLimit=10 or 20)
  Save to cache
  Update UI state
```

### Display Order

**Source rows are displayed in alphabetical order by source name**, NOT priority order. This is because `updateItem()` in the screen model inserts items into a `SortedMap(compareBy { s -> s.name })`.

This is a known inconsistency: priority order affects which sources are selected (top 20) and which are boosted (top 3), but the displayed row order on the For You page is always alphabetical.

**Planned fix (not yet implemented):** display rows in source priority order instead of alphabetical.

### Deduplication

Runs at display time via `State.dedupedItems()`. Uses normalized title key (lowercased, strips parens/brackets, collapses non-alphanumeric). Winner is the highest-scored occurrence across all sources (reference equality), with tie-break: score > genre count > manga.id. Empty rows after dedupe are hidden.

### Empty Row Handling

Empty `Success(emptyList())` rows are filtered out before rendering. Only Loading and Error rows remain visible while in-progress. If all sources complete with no results, a single empty-state message is shown.

### What Affects For You Results

| Signal | Effect |
|--------|--------|
| LOVE or LIKE rating | Manga with that source/url pair is removed from For You results. Tags from that manga increase `learnedTagWeights` in `TasteProfile`. |
| DISLIKE rating | Manga is removed from For You. Tags reduce learned weights. |
| Tag PREFER | +3 to tag score in `topSearchTags()`. Used as search query. |
| Tag DISLIKE | Reduces tag search score but does not hard-block. |
| Tag BLOCK | Post-fetch hard block — any candidate with that tag group is dropped entirely regardless of score. |
| Source disabled | Source excluded from For You entirely. |
| Language filter | Sources not matching selected languages are excluded before top-20 cap. |
| Source priority order | Determines which 20 sources are selected and which 3 are boosted. Does not affect displayed row order (rows are alphabetical). |
| Rated manga visibility | Controls whether rated manga appear in For You. Default: hide disliked only. |

---

## How Source Priority Works

### Storage

Stored as a comma-separated string of source ids in `SourcePreferences.recommendationSourceOrder()`. Example: `"12345,67890,11111"`. Empty string = no manual order (use default visible order).

Additionally, `SourcePreferences.recommendationSourceLanguages()` (StringSet, default `{"en"}`) filters the source pool before ordering is applied.

### Priority Resolution in For You

1. Get visible catalogue sources.
2. Filter by recommendation languages (`RecommendationSourceFilter.filterForRecommendations`).
3. Apply stored order (`RecommendationSourceOrdering.apply`): sources in stored order first, then append any not in stored order in their default order; disabled sources always excluded.
4. Take first 20.
5. First 3 of those 20 are boosted.

### Priority in Settings

Settings shows only sources matching the currently selected recommendation languages. All (including disabled) are shown for configuration. Drag-to-reorder calls `setSourceOrder()` which uses `mergeVisibleOrder()` to preserve hidden-language source ids in the full stored order.

### Hidden-Language Order Preservation

When EN-only is selected and the user reorders EN sources, `mergeVisibleOrder()` reconstructs the full stored order as: `[visible EN ordered ids] + [hidden-language ids from previous stored order]`. Hidden-language source positions are preserved at the end.

---

## How Language Filtering Works

### Preference

`SourcePreferences.recommendationSourceLanguages()` — StringSet, default `{"en"}`. Separate from global `enabledLanguages()`. Allows EN-only For You even if other languages are enabled globally for browsing.

### Filter Logic (RecommendationSourceFilter)

- `normalizeLanguages(langs)` — lowercase, trim, remove blanks; falls back to `{"en"}` if empty.
- `isLocalSource(source)` — `source.id == 0L` (constant from `LocalSource.ID`).
- `filterForRecommendations(sources, languages, includeLocal=false)`:
  - Excludes Local Source by default (id=0).
  - Keeps only sources whose `lang.lowercase()` is in normalized languages.
- `availableLanguages(sources)` — derives distinct sorted lang codes from installed sources, excluding Local Source.

### Why Local Source Is Excluded

Local Source (id=0) does not have a remote catalogue search API that supports genre filters in the same way extension sources do. Including it in source priority would confuse the source cap and enrichment budget. It is excluded by id constant, not by display name. The `includeLocal=true` override exists for testing.

---

## How Caching Works

### Cache Key

`"personal_v3:{sourceId}:{queryKey}"` where `queryKey = topTags.sorted().joinToString(",")`.

### Fingerprint

SHA-256 of: version tag `"personal_v3"`, normalized selected languages, sorted top tags, explicit tag preferences, learned tag weights, blocked groups, source affinity, alias map, disabled source ids, stored order ids.

Any change to taste preferences, ratings, aliases, disabled sources, source order, or language selection invalidates all cached entries (via fingerprint mismatch).

### TTL

24 hours. `ClearRecommendationCache.awaitExpired()` is called at the start of each `load()`.

### Cache Read Re-filtering

On cache read, favorites and hidden-rated manga are re-filtered because manga may have been favorited or rated after the cache was written.

---

## How Ratings Affect Recommendations

### Manga Detail Rating UI

`MangaInfoHeader.kt` → `MangaActionRow` → dropdown with Love/Like/Dislike/Clear.

`MangaScreenModel.setMangaTaste(rating)` / `clearMangaTaste()` writes to `manga_taste` table via `SetMangaTaste` / `ClearMangaTaste` interactors.

Taste identity key: `(source: Long, url: String)` — stable across devices and install paths. The `manga_id` is device-local and is not the primary key used for lookup.

### TasteProfile Computation

`GetTasteProfile.await()` computes `TasteProfile` from all `manga_taste` and `tag_taste` rows:
- `learnedTagWeights`: derived from genres of rated manga (positive for LIKE/LOVE, negative for DISLIKE)
- `explicitTagPreferences`: from `tag_taste` rows
- `sourceAffinity`: derived from source ids of rated manga
- `blockedGroups`: tag groups with BLOCK preference

### Scoring

`PersonalRecommendationScorer.rankCandidates()`:
1. Hard-block: candidates with any blocked tag group are dropped entirely.
2. Score = explicit pref score + learned weight score + source affinity (weak).
3. Returns sorted list capped at display limit.

---

## How Tag Preferences Affect Recommendations

### Search Query Construction

`topSearchTags()` in screen model:
- Combines explicit PREFER preferences (+3 weight) and positive learned weights.
- Takes top 5 by combined score.
- These are used as the search query sent to each source.

### GenreFilterMapper

Attempts to match desired tags against source filter lists (TriState, CheckBox, Select, AutoComplete). Falls back to text query for unmatched tags. Alias-aware since v0.3.0: tries user aliases + BUILT_IN_SYNONYMS before falling back.

`BUILT_IN_SYNONYMS` keys are in `normalizeTag()` form (spaces, not underscores): `"girls love"`, `"boys love"`, `"sci fi"`, `"martial arts"`, `"isekai"`, `"reincarnation"`, `"regression"`.

### Tag BLOCK

Blocked groups come from `tag_taste` rows with BLOCK preference. In `PersonalRecommendationScorer`, any candidate whose genre list intersects a blocked group is hard-removed. Blocked tags are **not** currently pushed into source filter queries (post-fetch filtering only). Query-time exclusion was planned in the hardening doc but not implemented.

---

## Does a Combined/Local/Global Recommendation Row Exist?

**No combined/local For You source exists.**

The For You page only searches remote installed extension sources. There is no "Local Source" row in For You, and no combined row that aggregates results across all sources.

The "combined recommendation source" concept that appears in some planning notes refers to `CrossExtensionGenreSearchSource` in the **single-manga recommendation screen** (manga detail → Recommendations tab), not the For You page. The two are separate.

---

## Single-Manga Recommendations vs. For You

| Aspect | Single-manga recs (manga detail) | For You (Browse tab) |
|--------|----------------------------------|----------------------|
| Entry point | Manga detail → Recommendations tab | Browse → For You tab |
| Screen model | `RecommendsScreenModel` / `BrowseRecommendsScreenModel` | `BrowsePersonalRecommendationsScreenModel` |
| What it searches | All installed catalogue sources for manga similar to the current manga | Installed catalogue sources matching the user's taste profile |
| Search signal | Genres of the current manga | Top tags from TasteProfile (ratings + explicit preferences) |
| Scoring | `RecommendationScorer` (title 60%, tag Jaccard 40%) | `PersonalRecommendationScorer` (blocked groups, explicit prefs, learned weights, source affinity) |
| Source for results | `CrossExtensionGenreSearchSource` (one per visible source) | `BrowsePersonalRecommendationsScreenModel.searchSource()` |
| Cache | Not aware of — check `RecommendsScreenModel` | Yes, `recommendation_cache` table, 24h TTL |
| Source ordering/filtering | Not subject to recommendation source priority | Subject to language filter, priority order, disabled sources |
| Blocked tags | Not subject to tag BLOCK | Hard-blocked post-fetch |
| Local Source | May be shown (existing Komikku behavior) | Excluded (id=0 filter) |

---

## Backup and Restore

### What Is Backed Up

`Backup.kt` proto numbers 620–623 (reserved 620–629 for this fork):
- 620: `backupMangaTastes` — per-manga ratings
- 621: `backupTagTastes` — explicit tag preferences
- 622: `backupTagAliases` — alias groups
- 623: `backupDisabledRecommendationSources` — disabled source ids

Controlled by `BackupOptions.tasteProfile` (default true).

`recommendation_source_order` and `recommendation_source_languages` are `SourcePreferences` values stored in the regular Android SharedPreferences, which Komikku backs up via standard preferences backup if that path is used. They are not in the taste-specific proto backup.

### Restore

`TasteRestorer` restores all 4 taste entities. Per-row error isolation is implemented: each row gets its own try/catch, errors are returned as `List<String>` and added to `BackupRestorer.errors`. A bad row does not abort the rest.

Taste restore runs after manga library restore (joins the manga restore job) so `(source, url)` lookups can resolve local manga ids.

### Sync

`SyncService.mergeSyncData()` merges:
- Manga tastes: keyed `source|url`, last-write-wins on `updatedAt`
- Tag tastes: keyed normalized displayName, last-write-wins
- Aliases: unioned by `distinctBy(alias+groupKey)`
- Disabled sources: unioned by `distinctBy(sourceId)`
- Feeds: merged via `mergeFeedsLists()` (composite key: `source|global|savedSearchName|query|filterList`, local-preferred)

---

## All Current Tests

| Test file | Tests | What it covers |
|-----------|-------|----------------|
| `TagNormalizationTest.kt` | 7 | `normalizeTag()` edge cases |
| `GetTasteProfileTest.kt` | 8 | TasteProfile computation from ratings/tags |
| `PersonalRecommendationScorerTest.kt` | 12 | Scorer hard-block, scoring, source affinity |
| `RecommendationScorerTest.kt` | ? | Original cross-extension scorer (title+tag Jaccard) |
| `GenreFilterMapperTest.kt` | 15 | Filter mapping, alias matching, text fallback |
| `ForYouVisibilityFilterTest.kt` | 12 | `shouldHideForYou()` for all visibility/rating combos |
| `RecommendationQueryPlannerTest.kt` | 13 | Strategy selection, fallback, serialization |
| `RecommendationSourceOrderingTest.kt` | 19 | parse/serialize, apply, applyAll, boostedSourceIds, mergeVisibleOrder |
| `RecommendationSourceFilterTest.kt` | 12 | Language filtering, Local Source exclusion, normalizeLanguages, availableLanguages |
| `TasteBackupRoundTripTest.kt` | 4 | Proto encode/decode of taste fields |

Total: ~102 focused unit tests.

---

## Known Bugs and Gaps

### 1. For You rows displayed alphabetically, not in priority order (BUG)

**File:** `BrowsePersonalRecommendationsScreenModel.kt:440`
**Code:** `newItems.toSortedMap(compareBy { s -> s.name })`

Source rows in the For You tab are sorted alphabetically by source name when results arrive. Priority order controls which 20 sources are searched and which 3 are boosted, but the displayed rows are alphabetical. Users who set MangaFire as their top priority source will not see it first in the For You feed.

**Fix needed:** Sort displayed rows by source priority order (the `orderedSources` list from `RecommendationSourceOrdering.apply`) rather than source name.

### 2. Phase 5 source quality stats — not implemented

`RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md` Phase 5 described per-source quality counters in a new `recommendation_source_quality` SQLDelight table (migration `47.sqm`). This was explicitly deferred and no migration or table exists.

### 3. Query-time blocked tag exclusion — not implemented

Planning described pushing blocked tags into source filter queries at search time (not just post-fetch). Not implemented. Post-fetch blocking is the only current mechanism.

### 4. TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md — fully implemented

This planning doc described fixing rating persistence (taste identity by `source+url` instead of `manga_id`), rated manga visibility, and various hardening items. All items in that document are implemented. The plan document's status line says "planning" but the work is done.

### 5. APK naming note in RECOMMENDATION_VERSIONING.md is stale

`RECOMMENDATION_VERSIONING.md` describes APK naming as `Komikku-KMK-Recs-v0.3.1-debug.apk`. The actual naming scheme used since then is `Komikku-v1.13.6-kmk.3.2-debug.apk` (upstream version + fork suffix). The versioning file should be updated to reflect this.

### 6. RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md and RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md status lines say "planning"

Both documents say `Status: planning document`. The content of these documents is now mostly or fully implemented. The status lines should be updated to "implemented" or "partially implemented (Phase 5 deferred)" to avoid confusion.

### 7. Pull-to-refresh not implemented

The hardening plan mentioned pull-to-refresh gesture support. Not implemented. Refresh is available only via the app bar Refresh button.

### 8. RECOMMENDATION_SEARCH_RESEARCH.md — read for context only, not a task list

This doc contains architecture notes and a feasibility matrix but does not define implementation tasks. Its content has been largely followed in the implementation.

### 9. `updateItem` alphabetical sort removes ability to reorder displayed rows via preference

Even if the priority preference is changed and settings updated, For You rows will always appear A→Z by source name. To respect priority order in display, `updateItem` would need to use a list instead of a sorted map, or the display layer would need to re-sort by the stored priority order on render.

---

## Files Changed by This Fork (Recommendation System)

### New files

| File | Purpose |
|------|---------|
| `exh/recs/BrowsePersonalRecommendationsScreenModel.kt` | For You screen model |
| `exh/recs/BrowsePersonalRecommendationsTab.kt` | For You tab content |
| `exh/recs/PersonalRecommendationScorer.kt` | Taste-aware scorer |
| `exh/recs/RecommendationCandidateEnricher.kt` | Capped getMangaDetails enrichment |
| `exh/recs/RecommendationQueryPlanner.kt` | Query strategy planner |
| `exh/recs/RecommendationSourceFilter.kt` | Language and Local Source filtering |
| `exh/recs/RecommendationSourceOrdering.kt` | Source priority ordering |
| `exh/recs/settings/RecommendationsSettingsScreen.kt` | Recommendation settings UI |
| `exh/recs/settings/RecommendationsSettingsScreenModel.kt` | Recommendation settings state |
| `domain/.../taste/` | All taste domain models and interactors |
| `data/.../taste/` | All taste repository implementations |
| `data/.../sqldelight/.../manga_taste.sq` | DB table |
| `data/.../sqldelight/.../tag_taste.sq` | DB table |
| `data/.../sqldelight/.../tag_alias.sq` | DB table |
| `data/.../sqldelight/.../recommendation_cache.sq` | DB table |
| `data/.../sqldelight/.../recommendation_disabled_source.sq` | DB table |
| `data/.../sqldelight/.../migrations/46.sqm` | DB migration |
| `data/backup/models/BackupMangaTaste.kt` | Backup model |
| `data/backup/models/BackupTagTaste.kt` | Backup model |
| `data/backup/models/BackupTagAlias.kt` | Backup model |
| `data/backup/models/BackupDisabledRecommendationSource.kt` | Backup model |
| `data/backup/create/creators/TasteBackupCreator.kt` | Backup creator |
| `data/backup/restore/restorers/TasteRestorer.kt` | Backup restorer |
| `domain/.../taste/interactor/GetTagAliases.kt` (extended) | Group-to-aliases map |
| `KMKDomainModule.kt` (new Injekt bindings) | DI registrations |

### Modified upstream files

| File | What changed |
|------|-------------|
| `SourcePreferences.kt` | Added `recommendationRatedMangaVisibility`, `recommendationSourceOrder`, `recommendationSourceStrategies`, `recommendationSourceLanguages` |
| `UiPreferences.kt` | Added `hideMigrateTab`, `hideForYouTab` |
| `BrowseTab.kt` | Added For You tab, Migrate/ForYou hide toggles |
| `MangaInfoHeader.kt` | Added taste rating dropdown to `MangaActionRow` |
| `MangaScreenModel.kt` | Added taste rating state and actions |
| `MangaScreen.kt` (both) | Wired rating through to screen model |
| `Backup.kt` | Added taste fields at proto 620–623 |
| `BackupOptions.kt` | Added `tasteProfile` option |
| `RestoreOptions.kt` | Added `tasteProfile` option |
| `BackupCreator.kt` | Wired `TasteBackupCreator` |
| `BackupRestorer.kt` | Wired `TasteRestorer` |
| `SyncService.kt` | Added taste merge, feeds merge fix |
| `SyncManager.kt` | Added taste fields to sync backup |
| `GenreFilterMapper.kt` | Added alias-aware matching, `BUILT_IN_SYNONYMS`, `aliasCandidates` param |
| `i18n-kmk/strings.xml` | All KMK strings added |
| `BrowseSettingsScreen.kt` (or equivalent) | Hide Migrate/For You tab toggles |

---

## What the Planning Docs Say vs. What Was Implemented

| Plan | Status |
|------|--------|
| `RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md` Phases 1–4 | DONE |
| `RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md` Phase 5 (blocked tag query-time exclusion) | NOT DONE |
| `RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md` Phase 6 (pull-to-refresh) | NOT DONE |
| `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md` Phases 1–4 | DONE |
| `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md` Phase 5 (source quality stats) | NOT DONE — explicitly deferred |
| `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md` Phase 6 (duplicate suppression) | DONE (conservative reference-equality approach) |
| `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md` Phase 7 (result explanations) | PARTIALLY DONE — row-level matched tags shown; item-level not displayed |
| `RECOMMENDATION_SOURCE_LANGUAGE_FILTER_PLAN.md` | DONE (all 8 phases) |
| `TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md` | DONE |

---

## Follow-Up Recommendations for Next Sessions

1. **Fix For You row display order** — change `updateItem()` to use source priority order, not alphabetical. This requires passing the ordered source list into the update function or pre-computing a display order index. This is a medium-priority UX bug.

2. **Mark planning documents as "implemented"** — update the `Status:` field in `RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md` and `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md`.

3. **Update RECOMMENDATION_VERSIONING.md APK naming** — current file says `Komikku-KMK-Recs-v0.3.1-debug.apk`; should say `Komikku-v1.13.6-kmk.3.2-debug.apk` scheme.

4. **Source quality stats (deferred Phase 5)** — needs new SQLDelight table `recommendation_source_quality`, migration `47.sqm`, domain layer, and recording hooks in `searchSource()`. Medium complexity, low risk.

5. **Query-time blocked tag exclusion** — would improve search precision for sources that support filter exclusion. Medium risk due to filter API variation across extensions.

6. **Pull-to-refresh gesture** — low complexity, improves UX.
