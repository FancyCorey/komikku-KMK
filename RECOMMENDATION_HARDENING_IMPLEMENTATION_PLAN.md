# Komikku Personal Recommendations Hardening Plan

Date: 2026-06-11

Status: superseded implementation plan. Most items from this plan were implemented in later KMK-Recs phases. For current behavior, read `docs/recommendations/CURRENT_STATE.md` and `RECOMMENDATION_IMPLEMENTATION_AUDIT.md` first.

This document is a handoff plan for the next implementation pass on the Komikku personal recommendation system. It assumes the current Claude-built implementation is already present in the working tree and should be treated as the baseline, not rewritten from scratch.

## Executive Summary

The current recommendation feature is feasible and already implemented as an MVP-plus system:

- A personal taste profile exists.
- Manga can be rated with dislike/like/love.
- Tags can be preferred, disliked, blocked, and aliased.
- The Browse tab has a personal recommendation tab.
- Recommendations search across visible catalogue sources with hard caps.
- Results are cached.
- Backup/restore and sync support have been added.
- Unit tests exist for scoring, tag normalization, genre mapping, taste profile behavior, and backup round-trip encoding.

The next pass should focus on hardening and UX completion, not a redesign. The strongest plan is:

1. Fix low-risk correctness and restore/sync issues.
2. Make the cache invalidation safer.
3. Restore source-row drill-down behavior for the personal recommendation tab.
4. Hide empty rows.
5. Add cross-source deduplication once result score metadata is preserved in UI state.
6. Defer reading-history learning until the user has lived with explicit ratings for a while.

## Current Implementation Map

These are the main files involved in the recommendation system.

### Browse Recommendations

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
  - Loads taste profile.
  - Selects top search tags.
  - Searches up to 20 visible catalogue sources.
  - Limits per-source output to 10 manga.
  - Uses `Dispatchers.IO.limitedParallelism(5)`.
  - Caches per-source results for 24 hours.
  - Filters out favorites and already-rated manga.
  - Produces result reasons from matched tags.

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt`
  - Adds the Browse tab UI.
  - Shows loading/empty/result/error rows using global search components.
  - Shows matched-tag reasons as a subtitle.
  - Has refresh and settings actions.
  - Current issue: source row click is a no-op.
  - Current issue: empty success rows still render.

- `app/src/main/java/exh/recs/PersonalRecommendationScorer.kt`
  - Scores candidates against explicit preferences, learned tag weights, source affinity, and blocked groups.
  - Blocks candidates with blocked tag groups.
  - Returns `ScoredCandidate` with score, matched groups, and reasons.

- `app/src/main/java/exh/recs/sources/GenreFilterMapper.kt`
  - Builds source-specific search parameters from selected tags.
  - Should eventually support blocked-tag exclusion where source filters allow it.

### Taste Domain and Data

- `domain/src/main/java/tachiyomi/domain/taste/`
  - Taste models, repository interfaces, and interactors.

- `data/src/main/java/tachiyomi/data/taste/`
  - Repository implementation and SQLDelight data plumbing.

- `data/src/main/sqldelight/tachiyomi/data/manga_taste.sq`
- `data/src/main/sqldelight/tachiyomi/data/tag_taste.sq`
- `data/src/main/sqldelight/tachiyomi/data/tag_alias.sq`
- `data/src/main/sqldelight/tachiyomi/data/recommendation_cache.sq`
- `data/src/main/sqldelight/tachiyomi/data/recommendation_disabled_source.sq`
- `data/src/main/sqldelight/tachiyomi/migrations/46.sqm`

### Backup, Restore, Sync

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/TasteBackupCreator.kt`
  - Creates backup sections for manga tastes, tag tastes, aliases, and disabled recommendation sources.

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt`
  - Has taste fields at proto numbers 620-623.
  - The comment should reserve 620-629 for this fork's taste system.

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/TasteRestorer.kt`
  - Restores taste rows.
  - Current issue: restore loops are not isolated per row.

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`
  - Wires taste restore after manga restore when library entries are restored.
  - Passes manga tastes, tag tastes, aliases, and disabled sources.

- `app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt`
  - Merges taste sections into sync.
  - Current issue: pre-existing `backupFeeds` merge gap remains; final merged backup includes saved searches but not feeds.

### Tests

- `app/src/test/java/eu/kanade/tachiyomi/data/backup/TasteBackupRoundTripTest.kt`
- `app/src/test/java/exh/recs/PersonalRecommendationScorerTest.kt`
- `app/src/test/java/exh/recs/RecommendationScorerTest.kt`
- `app/src/test/java/exh/recs/sources/GenreFilterMapperTest.kt`
- `app/src/test/java/exh/taste/GetTasteProfileTest.kt`
- `app/src/test/java/exh/taste/TagNormalizationTest.kt`

## Non-Goals For This Pass

Do not add reading-history learning in this pass.

Reason: reading history is a strong implicit signal, but it is ambiguous. A manga stopped at chapter 2 may mean dislike, distraction, bad timing, source quality, or temporary abandonment. Since the user explicitly prefers to remove manga from the library to reduce mental load, implicit history learning should be introduced only after real tablet usage shows the explicit taste model is insufficient.

Do not redesign the recommendation UI.

Reason: the existing UI shape matches Komikku's global search/feed patterns. This pass should polish behavior, not change the product direction.

Do not replace extension searches with a local global manga database.

Reason: there is no reliable, compact, legally clean, comprehensive local catalogue covering all extensions. Extension search remains the correct architecture.

Do not make AniList/MAL a core dependency.

Reason: external tracker data is incomplete for manhwa/manhua and many source-specific titles. Tracker data may be useful later as optional enrichment, but the core recommendation system should stay local and extension-driven.

## Implementation Phase 1: Low-Risk Hardening

Phase 1 should be done first. These changes are small, practical, and unlikely to disturb the recommendation algorithm.

### 1. Hide Empty Recommendation Rows

Current behavior:

- `BrowsePersonalRecommendationsTab.kt` renders every source row.
- `RecommendationItemResult.Success(emptyList())` still shows a row header with no useful content.

Desired behavior:

- Keep loading rows visible while a source is searching.
- Keep error rows visible if the source fails.
- Hide `Success(emptyList())` rows after they complete.
- If all completed rows are empty, show a calm empty state rather than a blank page.

Suggested implementation:

In `PersonalRecommendationsContent`, derive visible rows before `LazyColumn`.

Rules:

- Include `Loading`.
- Include `Error`.
- Include `Success` only when `result.result.isNotEmpty()`.

Add a state helper if useful:

```kotlin
private fun RecommendationItemResult.hasVisibleContent(): Boolean =
    when (this) {
        RecommendationItemResult.Loading -> true
        is RecommendationItemResult.Error -> true
        is RecommendationItemResult.Success -> result.isNotEmpty()
    }
```

Then iterate over visible entries instead of `state.items`.

Add an empty-state condition:

- If `!state.isLoading`
- `!state.profileIsEmpty`
- `state.items.isNotEmpty()`
- no visible rows remain
- show `taste_recommendations_empty` or add a more specific string such as `taste_recommendations_no_results`.

Acceptance criteria:

- A source returning no results does not leave an empty header row.
- Loading and error rows remain visible.
- A profile with no recommendation results does not render a blank screen.

### 2. Add Error Isolation In TasteRestorer

Current behavior:

- `TasteRestorer.restoreMangaTastes`, `restoreTagTastes`, `restoreTagAliases`, and `restoreDisabledRecommendationSources` loop over backup rows without per-row error isolation.
- A single bad row can abort the rest of that taste section.

Desired behavior:

- One bad taste row should be logged and skipped.
- Remaining rows should continue restoring.
- This should match the spirit of `MangaRestorer`, where individual failures do not abort the whole restore.

Important design note:

`TasteRestorer` currently does not receive the `errors` list from `BackupRestorer`. There are two reasonable options:

Option A: Give `TasteRestorer` an optional error callback.

```kotlin
class TasteRestorer(
    ...
    private val onError: (String) -> Unit = {},
)
```

But because `TasteRestorer` is constructed as a default dependency inside `BackupRestorer`, this is awkward unless the constructor is adjusted.

Option B: Let each restore method return a list of error messages.

Example:

```kotlin
suspend fun restoreMangaTastes(...): List<String>
```

Then `BackupRestorer.restoreTasteProfile` can collect those messages and append them to its existing `errors` list with `Date()`.

Preferred approach:

Use Option B. It avoids passing mutable state into the restorer and is straightforward to test.

Suggested shape:

```kotlin
suspend fun restoreMangaTastes(backupMangaTastes: List<BackupMangaTaste>): List<String> {
    if (backupMangaTastes.isEmpty()) return emptyList()
    val errors = mutableListOf<String>()
    backupMangaTastes.forEach { backup ->
        try {
            restoreOneMangaTaste(backup, now)
        } catch (e: Exception) {
            errors += "Taste manga ${backup.title} [${backup.source}]: ${e.message}"
        }
    }
    return errors
}
```

Use private helper functions to keep each loop readable:

- `restoreOneMangaTaste(...)`
- `restoreOneTagTaste(...)`
- `restoreOneTagAlias(...)`
- `restoreOneDisabledSource(...)`

Then in `BackupRestorer.restoreTasteProfile`:

```kotlin
val tasteErrors = buildList {
    addAll(tasteRestorer.restoreMangaTastes(backupMangaTastes))
    addAll(tasteRestorer.restoreTagTastes(backupTagTastes))
    addAll(tasteRestorer.restoreTagAliases(backupTagAliases))
    addAll(tasteRestorer.restoreDisabledRecommendationSources(backupDisabledSources))
}
tasteErrors.forEach { errors.add(Date() to it) }
```

Acceptance criteria:

- A thrown exception from one restored taste row does not prevent later rows from restoring.
- Restore completion can still report taste restore errors.
- Existing backup round-trip tests still pass.

Recommended test:

- Add a focused `TasteRestorerTest` only if stubbing the dependencies is reasonable.
- If dependency setup becomes heavy, keep this as implementation hardening and rely on restore integration/manual testing.

### 3. Fix The Sync Feeds Gap

Current behavior:

- `SyncService.mergeSyncData()` merges saved searches.
- It merges taste data.
- It does not preserve `backupFeeds` in the final merged `Backup`.

This was a pre-existing bug, but it is worth fixing now because the file is already touched and the saved-search/feed section is adjacent.

Desired behavior:

- Local and remote feed definitions should be merged and written into `mergedBackup.backupFeeds`.

Suggested implementation:

Find the backup feed model fields first:

- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupFeed.kt`

Implement:

```kotlin
private fun mergeFeedsLists(
    localFeeds: List<BackupFeed>?,
    remoteFeeds: List<BackupFeed>?,
): List<BackupFeed>
```

Use a composite key appropriate to `BackupFeed`. Likely candidates:

- `source`
- feed name or saved-search identifier
- query/filter payload if present

If `BackupFeed` has no stable updated timestamp, use a distinct union and prefer local on exact key conflicts.

Then in `mergeSyncData()`:

```kotlin
val mergedFeedsList = mergeFeedsLists(
    localSyncData.backup?.backupFeeds,
    remoteSyncData.backup?.backupFeeds,
)
...
backupFeeds = mergedFeedsList,
```

Acceptance criteria:

- Sync merge no longer drops feeds.
- Existing saved search merge remains unchanged.
- No taste code behavior changes because of this fix.

## Implementation Phase 2: Cache Correctness

The current cache is useful but too easy to keep stale after meaningful taste changes.

### 4. Strengthen Recommendation Cache Fingerprint

Current behavior:

In `BrowsePersonalRecommendationsScreenModel.kt`:

```kotlin
private fun profileFingerprint(topTags: List<String>): String =
    topTags.sorted().joinToString(",")
```

This only reflects the final selected search tags. It does not necessarily change when:

- A blocked tag changes.
- A disliked tag changes.
- A learned weight changes but does not alter the top tag list.
- A source affinity changes.
- A tag alias changes.
- Disabled recommendation sources change.
- The recommendation algorithm changes.

Desired behavior:

The fingerprint should change whenever the recommendation output should be considered stale.

Suggested implementation:

Replace `profileFingerprint(topTags)` with a richer deterministic fingerprint.

Inputs should include:

- algorithm version string, for example `"personal_v2"`
- `topTags`
- `profile.explicitTagPreferences`
- `profile.learnedTagWeights`
- `profile.blockedGroups`
- `profile.sourceAffinity`
- alias map
- disabled source IDs

Do not use raw object `toString()` unless ordering is guaranteed. Build stable sorted strings.

Example:

```kotlin
private fun profileFingerprint(
    topTags: List<String>,
    profile: TasteProfile,
    aliasMap: Map<String, String>,
    disabledSourceIds: Set<Long>,
): String {
    return buildString {
        append("personal_v2")
        append("|top=").append(topTags.sorted().joinToString(","))
        append("|explicit=").append(profile.explicitTagPreferences.toStableString())
        append("|learned=").append(profile.learnedTagWeights.toStableString { "%.4f".format(Locale.US, it) })
        append("|blocked=").append(profile.blockedGroups.sorted().joinToString(","))
        append("|source=").append(profile.sourceAffinity.toStableString { "%.4f".format(Locale.US, it) })
        append("|alias=").append(aliasMap.toStableString())
        append("|disabled=").append(disabledSourceIds.sorted().joinToString(","))
    }.sha256()
}
```

Prefer a small local helper. If the project already has a hash helper, use it. If not, use Java `MessageDigest`.

Important:

- Keep `cacheKey` version aligned with the fingerprint change.
- Consider changing `personal_v1` to `personal_v2` in `cacheKey(...)` so old cache entries are naturally ignored.

Acceptance criteria:

- Changing a blocked tag invalidates cached recommendation rows.
- Changing aliases invalidates cached recommendation rows.
- Disabling or enabling a recommendation source changes the fingerprint or source selection enough that stale rows do not appear.
- Existing cache table does not require a schema change.

Recommended tests:

- Add unit tests around a new pure helper if extracted.
- Test that two profiles with the same top tags but different blocked groups have different fingerprints.
- Test that alias-map changes alter the fingerprint.

## Implementation Phase 3: UX Completion

### 5. Restore Source Row Drill-Down

Current behavior:

In `BrowsePersonalRecommendationsTab.kt`:

```kotlin
onClickSource = { /* no drill-down for taste tab */ },
```

This is a mismatch with the user's expected workflow. The earlier cross-extension recommendation prototype allowed tapping an extension row and continuing into that source with the recommendation filters applied. The user explicitly liked this behavior.

Desired behavior:

When the user taps a source row in the "For You" tab:

- Open that source's browse/search screen.
- Apply the same recommendation query/filter strategy used to produce that row, where possible.
- If exact filter restoration is not practical for a source, fall back to opening that source with the text query used by the row.

Implementation constraints:

- Komikku source filters are extension-specific and may not serialize cleanly.
- `GenreFilterMapper.buildSearch(...)` returns search text and filters, but the UI navigation needs a way to pass those into the browse source screen.
- Existing global search/source search screens should be reused.

Suggested approach:

1. Inspect source browse navigation APIs:
   - `eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen`
   - existing calls to `BrowseSourceScreen(...)`
   - saved search/feed navigation paths

2. Add per-source drill-down metadata to recommendation state.

Example:

```kotlin
data class RecommendationSearchContext(
    val sourceId: Long,
    val textQuery: String?,
    val tagGroups: List<String>,
)
```

Store this in `State`, keyed by source ID:

```kotlin
val searchContexts: PersistentMap<Long, RecommendationSearchContext> = persistentMapOf()
```

3. Populate it in `searchSource(...)` immediately after `GenreFilterMapper.buildSearch(...)`.

4. In `BrowsePersonalRecommendationsTab.kt`, replace the no-op click with navigation.

Candidate behavior:

```kotlin
onClickSource = { source ->
    val context = state.searchContexts[source.id]
    navigator.push(BrowseSourceScreen(source.id, context?.textQuery))
}
```

Exact constructor arguments must be verified against the local codebase.

5. If browse source does not support passing a `FilterList`, do not attempt a large architecture change. Pass the text query and let the source browse screen open with that. Document exact filter preloading as future work.

Acceptance criteria:

- Tapping a source row from the "For You" tab no longer does nothing.
- The user lands in the selected source's browse/search screen.
- The drill-down uses the recommendation query text when possible.
- No crash when a source has no saved search context because it came from cache.

Important cache note:

If a source row came from cache, there may not be a live `FilterList`. Therefore, the state should store enough context to drill down even for cached rows:

- `queryKey`
- `topTags`
- maybe `textQuery`

If exact filter recreation requires `source.getFilterList()`, rebuild it on click or store only tag groups and text query.

## Implementation Phase 4: Cross-Source Deduplication

This is the best single recommendation-quality improvement, but it should be done carefully.

### 6. Deduplicate Recommendations Across Source Rows

Current behavior:

- Each source row is independent.
- The same title can appear across many sources.
- Popular titles can consume many visible recommendation slots.

Desired behavior:

- The same normalized title should not appear repeatedly across rows.
- Keep the best-scored occurrence.
- If scores are tied, use a deterministic fallback:
  - prefer exact title match quality if available,
  - then prefer row/source order,
  - then prefer manga with richer metadata such as genre list,
  - then stable source ID/title ordering.

Important implementation issue:

The current UI state stores:

```kotlin
RecommendationItemResult.Success(List<Manga>)
```

This loses the per-result score from `PersonalRecommendationScorer.ScoredCandidate`.

For proper dedupe, change state to preserve score/reason metadata per item.

Recommended model:

```kotlin
@Immutable
data class PersonalRecommendation(
    val manga: Manga,
    val score: Double,
    val matchedGroups: List<String>,
    val reasons: List<String>,
)
```

Then:

```kotlin
sealed interface RecommendationItemResult {
    data object Loading : RecommendationItemResult
    data class Success(val result: List<PersonalRecommendation>) : RecommendationItemResult
    data class Error(val throwable: Throwable) : RecommendationItemResult
}
```

Update UI rendering:

- `GlobalSearchCardRow` still needs `List<Manga>`, so pass `result.result.map { it.manga }`.
- Row subtitle can use row-level reasons derived from the result list.

Update cache loading:

- Cache has `resultMangaIds`, `resultScores`, and `resultReasons`.
- Convert cache data back into `PersonalRecommendation` where possible.
- If `resultScores` is missing or malformed, default to `0.0`.
- For matched groups, either parse `resultReasons` as a row-level fallback or leave empty. Do not block cache loading because metadata is incomplete.

Deduplication placement:

Preferred: derive deduped display rows from state, not from the search execution itself.

Reason:

- Search jobs finish asynchronously.
- Mutating global "seen titles" during concurrent source searches introduces race conditions.
- Display-level dedupe is deterministic and easier to reason about.

Suggested flow:

1. Keep raw per-source results in `state.items`.
2. Add a computed method to build display rows:

```kotlin
fun dedupedItems(): PersistentMap<CatalogueSource, RecommendationItemResult>
```

3. Inside that method:
   - collect all successful recommendations across sources,
   - normalize title keys,
   - choose the best candidate per key by score,
   - rebuild per-source rows containing only winners assigned to their kept source,
   - keep loading/error rows as-is,
   - hide now-empty success rows in the UI.

Title normalization:

Use a conservative helper:

```kotlin
private fun normalizedTitleKey(title: String): String =
    title.lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\[[^]]*]"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
```

Be careful:

- Do not over-normalize into false duplicates.
- Do not strip meaningful subtitle text aggressively.
- Keep this helper covered by tests.

Acceptance criteria:

- If the same normalized title appears in multiple source rows, only one remains visible.
- The visible one is the highest-scored candidate.
- Loading/error state remains stable while searches continue.
- Empty rows caused by dedupe are hidden.

Recommended tests:

- Add `BrowsePersonalRecommendationsDedupTest` or a pure helper test.
- Same title with different case/punctuation dedupes.
- Different titles do not dedupe.
- Higher score wins.
- Ties are deterministic.

## Implementation Phase 5: Optional Query-Time Blocked Tags

This is useful but source-dependent. Do after the main hardening work unless it is trivial.

### 7. Respect Blocked Tags At Query Time Where Possible

Current behavior:

- Blocked tags are applied after candidates are fetched.
- This wastes result slots when a source supports exclusion filters.

Desired behavior:

- If an extension exposes filters that support excluding genres/tags, push blocked tag groups into the query itself.
- If not supported, keep the current post-fetch filtering.

Suggested approach:

1. Extend `GenreFilterMapper`.
2. Accept both positive search tags and blocked groups:

```kotlin
GenreFilterMapper.buildSearch(
    filterList = filterList,
    includeTags = searchTags,
    excludeTags = profile.blockedGroups.toList(),
)
```

3. Detect common filter patterns carefully:
   - checkbox/tri-state genre filters,
   - include/exclude genre groups,
   - source-specific text fields.

Risk:

Filter APIs vary widely by extension. A too-clever mapper can break searches. Keep this conservative.

Acceptance criteria:

- Existing positive genre search still works.
- Sources without exclude filters behave exactly as before.
- Blocked-tag candidates remain filtered post-fetch regardless of query-time behavior.

Recommended tests:

- Extend `GenreFilterMapperTest`.
- Include source filter examples for:
  - no exclude support,
  - tri-state include/exclude support,
  - text-only fallback.

## Implementation Phase 6: Polish, Later

These are good ideas, but they should come after stability and dedupe.

### 8. Pull-To-Refresh

Current behavior:

- App bar refresh works.

Desired behavior:

- Pull-to-refresh works in the "For You" feed.

Implementation:

- Reuse the project's existing pull-refresh component/pattern.
- Search for existing usages of pull-to-refresh in browse/feed/history screens.
- Wire refresh gesture to `screenModel.refresh`.

Acceptance criteria:

- Pulling down refreshes recommendations.
- Existing refresh button still works.
- Loading state does not flicker badly.

### 9. Show Existing Taste Rating Badges On Covers

Current behavior:

- Taste system is mostly visible on manga detail and settings/recommendation screens.

Desired behavior:

- Manga cards/search results can show a small heart/thumb/rating badge if already rated.

Risk:

- This touches shared card components and can create visual clutter.
- This is polish, not correctness.

Recommendation:

- Do this after the user confirms the recommendation tab feels right.

### 10. Reading-History Learning

Potential behavior:

- Finished many chapters/completed series: positive implicit signal.
- Dropped early: mild negative signal.

Recommendation:

- Defer at least one week of tablet usage.
- If implemented later, make it opt-in.
- Keep explicit ratings stronger than implicit history.
- Use weak weights and decay over time.

## Build And Test Plan

Use the existing local toolchain.

Known environment:

```powershell
$env:JAVA_HOME="C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk21\jdk-21.0.11+10"
$env:ANDROID_HOME="C:\Users\USER\Downloads\Komikku\komikku-source\.tools\android-sdk"
$env:ANDROID_SDK_ROOT="C:\Users\USER\Downloads\Komikku\komikku-source\.tools\android-sdk"
$env:GRADLE_USER_HOME="C:\Users\USER\Downloads\Komikku\komikku-source\.tools\gradle-home"
$env:GRADLE_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Gradle path:

```powershell
C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat
```

Suggested verification commands:

```powershell
& "C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat" :app:testDebugUnitTest --tests "*TasteBackupRoundTripTest*" --tests "*PersonalRecommendationScorerTest*" --tests "*GenreFilterMapperTest*" --tests "*GetTasteProfileTest*" --tests "*TagNormalizationTest*"
```

Then:

```powershell
& "C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat" :app:assembleDebug
```

If SQLDelight models or schema change:

```powershell
& "C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat" :data:generateCommonMainTachiyomiDatabaseInterface
```

If release testing is desired:

- Set up or reuse a local signing key.
- Build a release APK.
- Debug APKs are acceptable for correctness testing, but release builds will better represent speed on the tablet.

## Manual Tablet Test Plan

After building and installing:

1. Open a manga detail page.
2. Rate several manga:
   - at least one Love,
   - one Like,
   - one Dislike.
3. Open taste/tag settings.
4. Prefer a few tags.
5. Block at least one tag, such as a tag family the user strongly dislikes.
6. Open Browse > Recommendations / For You.
7. Confirm:
   - sources load progressively,
   - empty rows disappear,
   - matched-tag reasons show,
   - favorites and already-rated entries do not dominate,
   - blocked-tag manga do not appear,
   - tapping a manga opens the manga screen,
   - tapping a source row opens source browse/search.
8. Disable a source in recommendation settings.
9. Refresh.
10. Confirm that disabled source no longer appears.
11. Re-enable the source and refresh.
12. Confirm it can appear again.
13. Change a blocked/preferred tag.
14. Refresh.
15. Confirm the cache does not serve obviously stale results.

Backup/restore:

1. Create a backup with taste profile enabled.
2. Restore it over a clean or test install.
3. Confirm manga ratings, tag preferences, aliases, and disabled recommendation sources return.
4. Confirm a malformed or missing taste row does not abort the whole restore.

Sync:

1. If sync is used, sync from device A.
2. Sync on device B.
3. Confirm taste data appears.
4. Confirm feeds are not lost during sync merge.

## Recommended Implementation Order For Claude

Use this order exactly unless a compile error forces adjustment:

1. Hide empty recommendation rows in `BrowsePersonalRecommendationsTab.kt`.
2. Fix `SyncService.mergeSyncData()` so `backupFeeds` survive merge.
3. Add per-row error isolation to `TasteRestorer` and wire returned errors into `BackupRestorer`.
4. Strengthen recommendation cache fingerprint and bump cache key version.
5. Restore source-row drill-down from the "For You" tab.
6. Preserve per-result score metadata in recommendation state.
7. Add cross-source dedupe using the preserved score metadata.
8. Run focused unit tests.
9. Build debug APK.
10. Document any source drill-down limitations.

## Risk Assessment

### Low Risk

- Hiding empty rows.
- Sync feed merge fix.
- Better restore error isolation.
- Cache key version bump.

### Medium Risk

- Stronger cache fingerprint.
  - Risk: accidental fingerprint instability could prevent cache reuse.
  - Mitigation: stable sorted string helpers and pure unit tests.

- Source-row drill-down.
  - Risk: source browse screen may not support exact filter transfer.
  - Mitigation: start with source + text query fallback.

### Higher Risk

- Cross-source dedupe.
  - Risk: losing per-source diversity or hiding useful alternate versions.
  - Mitigation: preserve score metadata, keep highest-score occurrence, deterministic tie-breaking, hide only exact normalized title duplicates.

- Query-time blocked tag exclusion.
  - Risk: extension filter APIs differ and can be easy to misuse.
  - Mitigation: conservative mapper changes and keep post-fetch filtering as the authoritative safety net.

### Defer

- Reading-history learning.
- Cover rating badges.
- Broad visual redesign.
- Tracker/AniList-based ranking.

## Final Acceptance Criteria

The pass is complete when:

- "For You" still loads recommendations across capped visible sources.
- Empty result rows do not clutter the page.
- Source row taps do something useful.
- Cache invalidates when meaningful taste settings change.
- Restore does not abort taste restoration because of one bad row.
- Sync no longer drops feeds.
- Duplicate titles across sources are reduced or eliminated after the dedupe phase.
- Existing taste/scorer/backup tests pass.
- A debug APK builds successfully.
