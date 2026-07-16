# KMK-Recs Deferred Feature Master Implementation Plan

**Historical note (added v0.7.46):** this was a deferred feature master plan from an earlier phase. All
7 phases it describes are complete (see line 9 below). Current open work lives in `NEXT_WORK.md` and
the focused plans created after v0.7.45 (`docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_PLAN.md`,
`docs/community/KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_PLAN.md`). Do not treat this file as an active
roadmap or revive its old v0.6.20-baseline phase instructions.

Date: 2026-06-20

Status: master planning document. Do not implement until the user approves a specific phase and requests a Claude Code prompt.

Current documented baseline: `KMK-Recs v0.6.20`

Last updated: 2026-06-28 â€” Phase 1 â†’ v0.7.18; Phase 6 â†’ v0.7.19; Phase 7 â†’ v0.7.20. All 7 phases complete.

## Purpose

This document consolidates the remaining deferred KMK recommendation work into one implementation roadmap. It intentionally excludes:

- features already implemented as of `KMK-Recs v0.6.20`,
- v0.6.20 source ordering/reassessment/seen work that already shipped,
- older implementation-plan details that are now historical archive material.

The goal is to give Claude Code one stable document to read before future implementation passes, while still keeping implementation bounded by phase. Claude should not attempt to implement every item in one patch unless the user explicitly approves that scope.

## Required Reading Before Any Implementation

Claude must read these files before coding any phase from this plan:

```text
docs/recommendations/README.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md
docs/recommendations/CROSS_EXTENSION_IDENTITY_FEASIBILITY_RESEARCH.md
docs/recommendations/INSTALLED_SOURCE_FIT_FEASIBILITY_RESEARCH.md
RECOMMENDATION_VERSIONING.md
```

If implementation touches backup, restore, sync, source evaluation, or matching, Claude must also inspect the relevant current code paths listed in each phase below.

## Global Rules

1. Do not make normal global search worse.
   - Normal global search must remain uncapped and exploratory.
   - Caps belong only to bounded workflows such as cross-extension matching.
2. Do not rely on extension/source name alone for quality decisions when live or local evidence exists.
3. Do not treat no-match as dislike.
4. Do not silently reorder the user's priority list.
5. Do not fetch chapter lists or manga details in bulk unless the phase explicitly approves that cost.
6. Prefer local, cached, bounded signals over repeated network calls.
7. Keep user-confirmed identity separate from automatic title similarity.
8. Update documentation in the same implementation session.
9. User-facing What's New must mention user-facing app changes only, not internal documentation or test work.

## Current Implemented Baseline

As of `KMK-Recs v0.6.20`, the app already has:

- Browse > For You personal recommendations.
- Top Picks row and Top Picks detail screen up to 50 already-fetched candidates.
- Love / Like / Dislike taste ratings.
- Preferred / disliked / blocked tag preferences.
- Cross-extension `Love other versions`, `Like other versions`, `Dislike other versions`.
- Cross-extension `Seen other versions`.
- Manga-level `Seen` marker stored in preferences.
- Known-manga filtering, including seen entries.
- Source Evaluation for non-installed extensions through temporary private install/probe/uninstall.
- Sources To Try suggestions.
- Source like/dislike controls.
- Explicit porn/hentai source filter.
- Source status ordering, reassessment prompt, evidence labels, source management controls.
- Background source evaluation WorkManager flow and notification deep link.

Do not reimplement these unless fixing a verified bug.

## Versioning Strategy

Follow the user's versioning rule:

- Work that extends source/extension/recommendation-source behavior remains under major `0.6.x`.
- Cross-extension matching and same-manga identity work remains under `0.5.x` until it becomes a larger new surface.
- New user-facing manga library/taste browsing surfaces use `0.7.x`.
- If this plan is implemented as multiple APKs, each APK must have a clear version and implementation report.

Suggested phase versions:

| Phase | Planned version | Actual version | Topic | Status |
| --- | --- | --- | --- | --- |
| Phase 1 | `KMK-Recs v0.6.21` | `KMK-Recs v0.7.18` | Source Evaluation robustness, repo/network handling, management polish | **COMPLETE** |
| Phase 2 | `KMK-Recs v0.5.2` | already shipped | Alternate-title cross-extension matching | **COMPLETE** |
| Phase 3 | `KMK-Recs v0.5.3` | already shipped | Favorite other versions and optional link prefill | **COMPLETE** |
| Phase 4 | `KMK-Recs v0.5.4` | already shipped | Persistent cross-source link groups with backup/restore/sync | **COMPLETE** |
| Phase 5 | `KMK-Recs v0.7.0` | `KMK-Recs v0.7.0` | Loved Manga view | **COMPLETE** |
| Phase 6 | `KMK-Recs v0.6.22` | `KMK-Recs v0.7.19` | Installed Source Fit / Best Sources For You | **COMPLETE** |
| Phase 7 | `KMK-Recs v0.6.23` | `KMK-Recs v0.7.20` | Query-time blocked tags and local-source decision | **COMPLETE** |

The version numbers can be adjusted if intervening fixes occur, but the topic grouping should remain stable.

## Phase 1: Source Evaluation Robustness And Management Polish

**STATUS: COMPLETE as KMK-Recs v0.7.18 (2026-06-28). See `KMK_RECS_V0_7_18_SOURCE_EVAL_ROBUSTNESS_IMPLEMENTATION.md`.**

Recommended version: `KMK-Recs v0.6.21`

### Goal

Improve existing Source Evaluation and Sources To Try behavior without adding a new recommendation engine.

### Included Deferred Features

- Broader extension repo failure surfacing.
- Mid-run connectivity loss handling and retry.
- Manage hidden/disliked non-installed source suggestions.
- Better reset controls for source/suggestion preferences.
- Optional source status timestamps if not already clear enough.
- Version-aware unblock/quarantine handling.
- Backup/restore coverage review for newer safety states where appropriate.

### Current Code To Inspect

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationNotifier.kt
app/src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionStore.kt
app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStore.kt
app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt
app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
domain/src/main/java/tachiyomi/domain/taste/repository/SourceEvaluationRepository.kt
domain/src/main/java/tachiyomi/domain/taste/repository/SourceEvaluationSafetyRepository.kt
data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq
data/src/main/sqldelight/tachiyomi/data/source_evaluation_unsafe_source.sq
```

### Required Behavior

#### Repo Failure Surfacing

Current deferred note says `ExtensionApi.getExtensions()` can return `emptyList()` per repo on failure with no UI explanation.

Claude should inspect whether repo failures are observable at the call site. If failures can be separated from a genuinely empty repo:

- record the repo URL/name and failure message in a lightweight UI state,
- show a non-blocking warning in Source Evaluation and/or Sources To Try,
- keep existing installed extension behavior unchanged,
- avoid treating repo failure as "no good sources exist."

If current APIs swallow errors too early, Claude should document the limitation and add the smallest safe seam to preserve failure metadata.

#### Connectivity Loss Recovery

Source Evaluation already has an offline start guard. It still needs better behavior when connectivity drops mid-run.

Required behavior:

- do not crash the app;
- mark the current extension/source probe as error or paused rather than hanging forever;
- surface a visible message such as "Connection lost. Evaluation can be retried.";
- allow retry/refresh without fully closing the app;
- keep per-extension timeout handling intact.

Do not add a global network monitor unless the app already has one. Prefer existing connectivity utilities if present.

#### Manage Hidden / Disliked Suggestions

Current state:

- non-installed source dislike hides a suggestion;
- there is no direct UI reset path except broad reset/clearing preferences.

Add a management surface, preferably under the existing Source management section:

- show count of hidden/disliked non-installed suggestions;
- allow clearing all hidden/disliked suggestions with confirmation;
- if practical, show individual hidden suggestions with restore buttons.

Do not merge this with installed source dislikes. Keep available-source keys separate from installed-source keys.

#### Version-Aware Unblock / Quarantine

Current risk:

- a package/source marked unsafe can hide a future fixed version.

Suggested behavior:

- store or compare `versionCode` / `versionName` / signature hash where available;
- if the package has a newer version than the quarantined marker, label it as "previous version quarantined";
- allow user-controlled re-test with confirmation;
- do not automatically unblock silently.

This should be implemented only if the needed version metadata is already available from `Extension.Available` / installed extension models. Otherwise document as still deferred.

#### Backup/Restore Coverage Review

Review whether source-evaluation unsafe markers, hidden source suggestions, and seen manga should be backed up.

Recommendation:

- backup user preference decisions that represent durable user intent;
- do not backup volatile crash probe markers;
- only backup quarantine/unsafe state if it prevents repeated dangerous crashes across restore.

If adding backup fields, reserve proto numbers carefully and add round-trip tests. If not adding backup, document why.

### Tests

Add focused tests where pure seams exist:

- hidden/disliked suggestion key restoration/clearing;
- version-aware quarantine policy;
- repo failure mapping helper;
- connectivity/error state mapping helper.

Manual verification:

1. Disconnect Wi-Fi mid-evaluation and confirm the app does not crash.
2. Reconnect and retry without force closing.
3. Dislike a Sources To Try suggestion, restore/reset it, and confirm it reappears when otherwise eligible.
4. Confirm installed source dislikes are not affected by non-installed suggestion reset unless explicitly intended.

### Do Not Include

- source quality learning,
- new Loved Manga view,
- cross-source link groups,
- website-native scraping.

## Phase 2: Alternate-Title Cross-Extension Matching

**STATUS: COMPLETE (shipped in a prior version before this plan was written). `CrossExtensionMatchQueryPlanner` produces up to 3 deduplicated queries â€” title, ogTitle, bracket-stripped variant.**

Recommended version: `KMK-Recs v0.5.2`

### Goal

Improve `Love/Like/Dislike/Seen other versions` so it can find matches when another source uses an alternate title, original title, romanized title, translated title, or custom title.

### Current Problem

Current matching searches primarily from the current manga title. This misses valid versions when source naming differs.

### Current Code To Inspect

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt
app/src/main/java/mihon/feature/migration/list/search/BaseSmartSearchEngine.kt
app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt
app/src/main/java/mihon/feature/migration/list/search/SourceMatchScorer.kt
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt
```

### Required Behavior

- Keep normal global search unchanged and uncapped.
- Keep matching workflow per-source cap at the shipped value, currently 2.
- Keep origin filtering before cap.
- Keep candidates selected by default.
- Preserve manual deselection.
- Reuse existing source language and priority filtering.
- Do not fetch tracker data or website aliases over the network just to build queries.

### Suggested Implementation

Create:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchQueryPlanner.kt
app/src/test/java/exh/recs/matching/CrossExtensionMatchQueryPlannerTest.kt
```

Planner inputs should use cheap local data only:

- current title,
- original title if available and different,
- custom title / non-custom title distinction if available,
- local metadata alternate titles if already present,
- migration-style cleaned title variants if cheap.

Planner output:

- distinct ordered queries,
- cap at 3 queries initially,
- normalized only for dedupe; preserve original display query for the source call.

For each source:

1. run query 1;
2. filter origin;
3. add unique `(source, url)` results;
4. if cap reached, stop for that source;
5. otherwise run next query up to max 3;
6. apply per-source cap after merge.

### Optional Scoring

If cheap, show match reasons:

- exact title,
- alternate title,
- similar title,
- same author/artist if present in search result.

Do not fetch manga details for every candidate in this phase.

### Tests

- query planner dedupes same title variants;
- query planner caps to 3;
- original/custom title included when different;
- results from multiple queries merge by `(source, url)`;
- origin filtering happens before cap;
- manual deselection remains preserved.

## Phase 3: Favorite Other Versions

**STATUS: COMPLETE (shipped in KMK-Recs v0.7.0). `CrossExtensionMatchMode.Favorite` fully implemented in `CrossExtensionMatchScreenModel`.**

Recommended version: `KMK-Recs v0.5.3`

### Goal

Add `Favorite other versions` using the existing bounded cross-extension matching workflow.

### Current Code To Inspect

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/BulkFavoriteScreenModel.kt
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt
```

### Required Behavior

- Add a separate mode, e.g. `CrossExtensionMatchMode.Favorite`.
- Use same candidate search/selection screen as rating and seen modes.
- All candidates selected by default.
- User can deselect wrong matches.
- On confirm, selected manga are added to library/favorites using existing library behavior.
- Preserve duplicate warnings and default category behavior.
- Do not unfavorite anything.
- Do not alter Love/Like/Dislike taste rows unless the user separately rates.

### Implementation Guidance

Do not copy/paste a large amount of `BulkFavoriteScreenModel` into the matching screen. Instead:

1. inspect whether the favorite application logic can be extracted into a domain/helper class;
2. keep UI-specific selection state in `CrossExtensionMatchScreenModel`;
3. use the helper for actual favorite/add-to-library behavior;
4. preserve existing duplicate handling as much as possible.

If duplicate/category behavior is too coupled for a safe first pass, stop and document what extraction is needed before coding further.

### Tests

- favorite mode does not write taste rows;
- selected candidates are passed to favorite helper;
- deselected candidates are ignored;
- duplicate candidates follow existing duplicate behavior;
- normal bulk favorite screen behavior remains unchanged.

## Phase 4: Persistent Cross-Source Link Groups

**STATUS: COMPLETE (shipped in KMK-Recs v0.7.0, audited v0.7.2, backup/sync wired v0.7.16). `manga_cross_source_link` table, migration 50, backed up at proto 624, sync-merged. Link groups drive Loved Manga grouping.**

Recommended version: `KMK-Recs v0.5.4`

### Goal

Persist user-confirmed "same manga across sources" relationships so future rating/favorite/seen workflows can use known links before searching.

### Why This Matters

Automatic same-manga identity across all extensions is not reliable. User-confirmed link groups are the maintainable identity layer.

### Current Code To Inspect

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
domain/src/main/java/tachiyomi/domain/taste/repository/TasteRepository.kt
data/src/main/sqldelight/tachiyomi/data/manga_taste.sq
app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/TasteBackupCreator.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/TasteRestorer.kt
app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt
app/src/main/java/eu/kanade/domain/KMKDomainModule.kt
```

### Data Model

Create a SQLDelight table similar to:

```sql
CREATE TABLE manga_cross_source_link (
    group_id TEXT NOT NULL,
    source INTEGER NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY(group_id, source, url)
);

CREATE UNIQUE INDEX manga_cross_source_link_source_url_index
ON manga_cross_source_link(source, url);

CREATE INDEX manga_cross_source_link_group_index
ON manga_cross_source_link(group_id);
```

Do not rely on local `manga_id` as the cross-device identity. Use `(source, url)`.

### Domain Layer

Add:

```text
domain/src/main/java/tachiyomi/domain/taste/model/CrossSourceMangaLink.kt
domain/src/main/java/tachiyomi/domain/taste/repository/CrossSourceMangaLinkRepository.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/GetCrossSourceMangaLinks.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertCrossSourceMangaLinks.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/DeleteCrossSourceMangaLink.kt
```

Implementation should live under the existing taste data layer and be registered in `KMKDomainModule`.

### Link Creation

When a user confirms selected matches in rating/favorite/seen workflows:

1. create or reuse a link group for the origin;
2. insert origin plus selected candidates;
3. if a selected candidate already belongs to another group, skip it or show warning;
4. defer group-merge UI unless explicitly approved.

### Using Links

When opening a cross-extension matching workflow:

- load existing links for the origin `(source, url)`;
- preselect linked versions;
- show them first;
- still allow the user to run the search for additional versions.

Do not silently apply actions to linked manga without a confirmation screen in the first implementation.

### Backup / Restore / Sync

Use proto number `624` if still unused and reserved for KMK taste/link data.

Add:

```text
BackupCrossSourceMangaLink
Backup.backupCrossSourceMangaLinks
TasteBackupCreator backup support
TasteRestorer restore support
SyncService merge support
```

Restore should:

- restore each row independently;
- skip invalid rows safely;
- collect errors instead of aborting the whole taste restore.

Sync merge should:

- merge by `(source, url)` or `(group_id, source, url)`;
- prefer newer `updatedAt` on conflict;
- document exact behavior.

### Tests

- SQL/repository upsert and query;
- group creation includes origin and selected candidates;
- existing linked candidate is handled safely;
- backup encode/decode round-trip;
- restore skips invalid rows without aborting;
- sync merge keeps newest rows.

## Phase 5: Loved Manga View

**STATUS: COMPLETE (shipped in KMK-Recs v0.7.0, sort modes added v0.7.14/v0.7.15). See `KMK_RECS_V0_7_0_LOVED_MANGA_VIEW_IMPLEMENTATION.md`.**

Recommended version: `KMK-Recs v0.7.0`

### Goal

Add a user-facing view that shows manga rated `Love`, with conservative duplicate grouping.

### Current Code To Inspect

```text
domain/src/main/java/tachiyomi/domain/taste/interactor/GetMangaTaste.kt
domain/src/main/java/tachiyomi/domain/taste/model/MangaTaste.kt
app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt
app/src/main/java/exh/recs/BrowseRecommendsScreen.kt
app/src/main/java/exh/recs/TopPicksScreen.kt
app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt
```

Claude must inspect actual browse navigation before choosing the final entry point.

### Required Behavior

- Show only `MangaRating.LOVE` entries.
- Exclude Like/Dislike/Seen entries.
- Sort by most recently loved/updated first.
- Resolve manga rows by `mangaId` or `(source, url)` when possible.
- Provide a conservative "Group clear duplicates" display option.
- Never delete or merge taste rows.

### Duplicate Grouping

Create a pure helper:

```text
app/src/main/java/exh/recs/loved/LovedMangaDuplicateGrouper.kt
app/src/test/java/exh/recs/loved/LovedMangaDuplicateGrouperTest.kt
```

Group only when:

- normalized title matches exactly, and
- normalized non-blank description/intro matches exactly or near-exactly.

Do not group by title alone.

If description is blank or too short, do not group.

### UI

Suggested location:

- Browse area near For You, or
- Recommendation Settings link if Browse navigation is too invasive.

Preferred first pass: Browse-adjacent user-facing view, because this is not a setting.

Required UI states:

- loading,
- empty,
- error,
- success list,
- grouping toggle.

If a grouped entry has multiple versions, show an `N versions` label. Tapping the representative opens manga detail.

### Tests

- LOVE included;
- LIKE/DISLIKE excluded;
- same title + same non-blank description groups;
- same title + different description does not group;
- blank description does not group;
- grouping disabled shows all entries.

## Phase 6: Installed Source Fit / Best Sources For You

**STATUS: COMPLETE as KMK-Recs v0.7.19 (2026-06-28). See `KMK_RECS_V0_7_19_INSTALLED_SOURCE_FIT_IMPLEMENTATION.md`.**

Implemented: `SourceFitStats` rolling accumulator, `SourceFitLabel` enum, `SourceFitStatsStore`, `recommendationSourceFitStats` preference, merge-after-run in `BrowsePersonalRecommendationsScreenModel`, live-subscribe and `applyFitSuggestedOrder()` in settings screen model, rolling-label badge in `SourcePriorityItem`, "Suggest priority order based on fit" button.

Recommended version: `KMK-Recs v0.6.22`

### Goal

Learn which installed sources are useful for the user's For You recommendations, based on actual observed outcomes.

### Current Code To Inspect

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
app/src/main/java/exh/recs/RecommendationSourceRunStatus.kt
app/src/main/java/exh/recs/RecommendationStatusAdjuster.kt
app/src/main/java/exh/recs/CombinedPicksAccumulator.kt
app/src/main/java/exh/recs/RecommendationScorer.kt
app/src/main/java/exh/recs/PersonalRecommendationScorer.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/GetMangaTaste.kt
```

### Required Behavior

Persist rolling source-fit stats from real For You runs. Do not run extra searches just to collect stats.

Suggested stats:

```text
source_id
run_count
shown_count
no_match_count
filtered_out_count
error_count
hidden_by_duplicate_count
visible_candidate_count
top_picks_contribution_count
liked_candidate_count
loved_candidate_count
disliked_candidate_count
blocked_tag_candidate_count
avg_candidate_score
avg_matched_group_count
last_success_at
last_error_at
updated_at
```

### Scoring

Use a confidence-adjusted score:

- reliability: successful runs vs errors;
- fit: high-scoring visible results, Top Picks contribution, later Love/Like;
- noise: filtered-out, blocked-tag, duplicate-hidden, user Dislike;
- confidence: enough runs/data before strong labels.

Never let one good or bad run dominate.

Suggested labels:

- Great fit,
- Good fit,
- Mixed,
- No matches recently,
- Often filtered,
- Often errors,
- Too little data.

### UI

In Recommendation Settings or Source Evaluation:

- show fit labels beside installed sources;
- show reasons compactly;
- add `Suggest priority order` button only after enough data exists;
- if user accepts suggested order, apply via existing source priority preference;
- do not auto-reorder.

### Tests

- stats accumulator updates correctly for each status;
- confidence prevents strong labels with too little data;
- Top Picks contribution increases fit;
- errors reduce reliability but decay over time if implemented;
- suggested order is deterministic and does not include disabled/disliked sources unless explicitly chosen.

## Phase 7: Query-Time Blocked Tags And Local Source Decision

**STATUS: COMPLETE as KMK-Recs v0.7.20 (2026-06-28). See `KMK_RECS_V0_7_20_PHASE7_BLOCKED_TAGS_TESTS_IMPLEMENTATION.md`.**

Query-time blocked tag exclusion: code was already in place from v0.7.0 (`GenreFilterMapper.buildSearch()` `blockedGenres` parameter + `STATE_EXCLUDE` for TriState). 7 unit tests added in v0.7.20. Local Source: keep-excluded decision documented â€” no coding required.

Recommended version: `KMK-Recs v0.6.23`

### Goal

Improve filtering efficiency where safe, and decide whether Local Source belongs in For You.

### Query-Time Blocked Tags

Current behavior:

- blocked tags filter after fetching.

Potential improvement:

- if a source exposes filter types that clearly support tag exclusion, push blocked tags into the source query.

Implementation constraints:

- never assume every source uses the same filters;
- if mapper cannot safely identify exclusion support, do nothing;
- keep post-fetch blocked-tag filtering as fallback;
- log/debug reason only if useful.

Current code to inspect:

```text
app/src/main/java/exh/recs/sources/GenreFilterMapper.kt
app/src/main/java/exh/recs/RecommendationQueryPlanner.kt
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
```

Tests:

- mapper only emits exclusion filters for known-safe filter shapes;
- fallback post-fetch filter still runs;
- no crash for unknown/custom filter types.

### Local Source Support

Current behavior:

- Local Source id `0` is excluded from For You.

Decision needed before coding:

1. Keep excluded.
2. Add local-library/history synthetic row.
3. Add downloaded/local-files row.
4. Add cached metadata row.

Recommendation:

- keep Local Source excluded from normal For You source searches;
- if desired, implement a separate synthetic "Local / Already Known" row later, based only on local DB metadata.

Do not try to search Local Source like a web catalogue unless the code proves it supports the same search contract.

## Conditional / Low-Priority Items

These should not be implemented before the phases above unless the user explicitly changes priority.

### AniList / Tracker Known-List Cache

Potential value:

- hide or badge manga already tracked externally.

Constraints:

- cache locally;
- never fetch per recommendation result;
- use as weak signal only;
- handle tracker removal/dropped states carefully.

Risk: moderate complexity, limited benefit for manhwa/manhua coverage.

### Minimum Chapter Count Filter

Potential value:

- filter out very short manga.

Constraint:

- use only locally known chapter counts;
- do not fetch chapter lists just for filtering.

Risk: low if local-only, but coverage is incomplete.

### Website-Native Recommendation Support

Potential value:

- some sites have good related/recommended sections.

Constraint:

- no standard extension API;
- would require source-specific extension support or brittle parsing.

Recommendation:

- do not implement globally;
- consider only for a small curated set of high-value sources after Source Fit exists.

### Larger Source Evaluation Batch Sizes

Current batch sizes are 10, 25, 50, 100.

500/1000 remain deferred because:

- evaluation is network-heavy;
- extension install/probe/uninstall is fragile on Android;
- crash quarantine exists but does not make huge batches free.

Recommendation:

- revisit only after Source Evaluation is stable across multiple real runs.

### Additional Extension Repositories

Current reliable default remains Keiyoushi:

```text
https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json
```

Do not add unknown repos by default. If future reliable repos are found:

- allow manual add first;
- document trust/security implications;
- avoid silently bundling unverified repos.

## Recommended Implementation Order

Best practical order:

1. Phase 1: Source Evaluation robustness and management polish.
2. Phase 2: Alternate-title matching.
3. Phase 3: Favorite other versions.
4. Phase 4: Persistent link groups with backup/restore/sync.
5. Phase 5: Loved Manga view.
6. Phase 6: Installed Source Fit.
7. Phase 7: Query-time blocked tags and Local Source decision.

Reasoning:

- Phase 1 reduces support/debug friction for the existing source-evaluation system.
- Phase 2 improves the current matching workflow without schema risk.
- Phase 3 depends on the matching workflow.
- Phase 4 should happen after matching/favorite workflows are stable.
- Phase 5 is a new surface and should not be mixed into matching internals.
- Phase 6 needs stable source-result behavior and enough observed data.
- Phase 7 is optimization/product-decision work, not core functionality.

## Claude Execution Contract

For any approved phase, Claude must:

1. Read this master plan and the required baseline docs.
2. Inspect the exact current code before editing.
3. Implement only the approved phase unless the user explicitly approves adjacent work.
4. Preserve existing implemented behavior unless fixing a verified bug.
5. Add or update focused tests.
6. Run focused tests and, if practical, `:app:testDebugUnitTest`.
7. Build debug APK only if requested or normal for the handoff.
8. Create an implementation report in `docs/recommendations`.
9. Update:
   - `CURRENT_STATE.md`,
   - `NEXT_WORK.md`,
   - `README.md` if planning status changes,
   - `RECOMMENDATION_VERSIONING.md`,
   - `KmkRecsReleaseNotes.kt` for user-facing changes.
10. Clearly list anything intentionally deferred.

## Documentation Output Per Phase

Use these implementation report names unless the final version changes:

```text
docs/recommendations/KMK_RECS_V0_6_21_SOURCE_EVALUATION_ROBUSTNESS_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_5_2_CROSS_EXTENSION_ALTERNATE_TITLE_MATCHING_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_5_3_FAVORITE_OTHER_VERSIONS_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_5_4_CROSS_SOURCE_LINK_GROUPS_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_7_0_LOVED_MANGA_VIEW_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_6_22_INSTALLED_SOURCE_FIT_IMPLEMENTATION.md
docs/recommendations/KMK_RECS_V0_6_23_QUERY_TIME_BLOCKED_TAGS_AND_LOCAL_SOURCE_DECISION_IMPLEMENTATION.md
```

Each report must include:

- files changed,
- behavior changed,
- data/schema/backup changes,
- tests run,
- APK built/path if any,
- known limitations,
- deferred items remaining.

## Summary Recommendation

This work can be planned as one roadmap, but implementation should happen in large coherent phases, not as one uncontrolled patch. The next best phase is Phase 1 because it improves reliability and management around Source Evaluation before adding more identity/linking features.


