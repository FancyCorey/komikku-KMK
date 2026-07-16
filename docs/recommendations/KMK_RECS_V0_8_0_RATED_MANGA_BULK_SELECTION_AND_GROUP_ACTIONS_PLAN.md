# KMK-Recs v0.8.0 Rated Manga Bulk Selection And Group Actions Plan

Date: 2026-07-12

Status: implementation plan only. Do not code until explicitly approved.

Scope type: private KMK-Recs v0.8.0 feature work.

## Goal

Turn the Loved / Liked / Disliked manga screens into a proper rated-manga management surface:

- long-press enters selection mode instead of opening recommendations;
- expose recommendations through explicit item-menu actions;
- add bulk rating/group actions with confirmation where destructive;
- make linked cross-source groups transparent through a focused version-list view;
- allow a user-selected primary version to control the rated-list cover/title without weakening group recommendation metadata.

This is mostly a UX and management-layer improvement. It should reuse the existing rated manga screen, existing cross-source link table, existing group recommendation flow, and existing cross-extension matching flow wherever possible.

## Current Code Facts

### Rated Manga Screens

- `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt`
  - LOVE compatibility route.
  - Delegates to `RatedMangaCollectionContent(rating = MangaRating.LOVE, screenModel = LovedMangaScreenModel())`.

- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`
  - Shared UI for LOVE / LIKE / DISLIKE.
  - Current grid card behavior:
    - tap opens `MangaScreen(item.taste.mangaId, true)`;
    - long-press opens `RecommendsScreen.Args.CrossSourceGroupSeed(...)` for LOVE/LIKE;
    - DISLIKE long-press opens `MangaScreen`;
    - top-left Explore overlay opens group recommendations for LOVE/LIKE;
    - top-right badge shows version count when `item.versionCount > 1`.
  - There is no item overflow/action menu.
  - There is no selection mode.
  - There is no bottom action bar.

- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt`
  - Shared screen model for LOVE / LIKE / DISLIKE via `filterRating`.
  - Loads all tastes through `GetMangaTaste.subscribeAll()`.
  - Filters to installed visible sources using `SourceManager.getVisibleCatalogueSources()`.
  - Loads confirmed cross-source links via `GetCrossSourceMangaLinks.awaitAll()`.
  - Applies `resolveLinkedGroupRatingConflicts(...)` before filtering by rating.
  - `groupDuplicates` defaults to true and is preserved across reactive reloads.
  - `LovedDisplayItem` currently contains only:
    - `taste`,
    - `manga`,
    - `versionCount`.
  - It does not expose group id, member keys, or whether the group is confirmed.

### Existing Rating APIs

- `domain/src/main/java/tachiyomi/domain/taste/interactor/SetMangaTaste.kt`
  - Can change a single manga rating.

- `domain/src/main/java/tachiyomi/domain/taste/interactor/SetMangaTasteBatch.kt`
  - Can rate multiple `Manga` rows together.

- `domain/src/main/java/tachiyomi/domain/taste/interactor/ClearMangaTaste.kt`
  - Already exists.
  - Supports clearing by `mangaId` or by `source + url`.

- `data/src/main/sqldelight/tachiyomi/data/manga_taste.sq`
  - `manga_taste` is keyed by `manga_id`.
  - `source + url` is unique.
  - Existing delete queries support clear-rating behavior.

### Existing Group Link APIs

- `domain/src/main/java/tachiyomi/domain/taste/model/CrossSourceMangaLink.kt`
  - Fields: `source`, `url`, `groupId`, `title`, `createdAt`, `updatedAt`.
  - No primary-version field exists.

- `data/src/main/sqldelight/tachiyomi/data/manga_cross_source_link.sq`
  - Existing table links source/url pairs into a `group_id`.
  - Supports lookup by group, lookup by source/url, upsert, delete one link, delete group.

- `app/src/main/java/exh/recs/links/LinkGroupManagementScreen.kt`
  - Global link manager.
  - Shows groups and lets users delete group or remove individual links.
  - It does not show source names, rating, favorite status, installed/missing status, or a user-selected primary version.

### Existing Matching And Recommendations

- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt`
  - Existing cross-extension matching flow.
  - Uses serializable primitive route args to avoid previous `BadParcelableException`.

- `app/src/main/java/exh/recs/matching/CrossExtensionMatchRouteMode.kt`
  - Existing modes:
    - `Rating`,
    - `MarkSeen`,
    - `Favorite`.
  - There is no separate link-only mode.

- `app/src/main/java/exh/recs/RecommendsScreen.kt`
  - Existing group recommendation entry point uses `RecommendsScreen.Args.CrossSourceGroupSeed(sourceId, url, primaryTitle)`.
  - v0.7.43+ group recommendations already use linked versions and the normal extension-row recommendation UI.

## UX Contract

### Long Press

For v0.8.0, long-press in Loved / Liked / Disliked must no longer open recommendations.

Long-press becomes the standard selection gesture:

1. enter selection mode;
2. select that item/group;
3. allow selecting multiple more entries.

Add a visible top-right select icon/action for discoverability. Users should not have to know the long-press gesture exists.

### Normal Tap

Outside selection mode:

- tap a card opens `MangaScreen(item.taste.mangaId, true)` as it does today.

Inside selection mode:

- tap toggles selection for that display item;
- opening the manga should move to the item menu or a secondary action, not conflict with selection.

### Recommendations

The rated manga item menu must contain:

- `See Recommendations`;
- `See Group Recommendations`.

`See Group Recommendations` appears only when the rated entry has a confirmed linked group with at least 2 versions.

It must reuse the already-implemented group recommendation flow:

- same extension-row UI;
- same source selection behavior;
- same group metadata/tags/titles;
- same filtering and dedupe policy.

This work is about discoverability and gesture cleanup. Do not rebuild the group recommendation engine.

### Item Action Menu Structure

Separate actions into three visible groups.

#### Recommendation Actions

- `See Recommendations`
  - Opens the standard single-entry recommendation flow for the primary/current entry.
  - If current app behavior already treats `CrossSourceGroupSeed` as grouped, Claude must inspect whether there is a single-entry route. Do not silently make both menu items do the same thing without documenting it.

- `See Group Recommendations`
  - Visible only for confirmed groups with 2+ versions.
  - Opens `RecommendsScreen.Args.CrossSourceGroupSeed(...)` using the selected/displayed primary item.

- `Find Other Versions`
  - Launches the cross-extension matching flow again.
  - Prefer reusing existing `CrossExtensionMatchScreen.fromMode(originMangaId, CrossExtensionMatchMode.Rating(currentRating))`.
  - If a true link-only mode is required, document that as a deviation before adding it. Do not create a parallel matching system.

#### Rating Actions

- `Change Rating`
  - Allows changing selected entry/entries to LOVE, LIKE, or DISLIKE.
  - Must use existing `SetMangaTaste` / `SetMangaTasteBatch` style behavior and preserve rating exclusivity.

- `Clear Rating`
  - Uses existing `ClearMangaTaste`.
  - Requires confirmation.
  - Clearing a rating must not delete manga, favorites, history, or cross-source links unless explicitly selected through a group action.

- `Mark Not Interested`
  - Applies existing Not Interested / seen behavior for selected item(s).
  - It should not be treated as strongly negative like DISLIKE.
  - Confirm if multiple selected.

- `Favorite Other Versions`
  - Useful when the group exists but not all installed versions are favorited.
  - Must only affect installed/local manga rows that the app can safely favorite.
  - If the current favorite interactor/API does not support this cleanly, implement as a documented deferred action rather than a brittle direct database update.

#### Group Actions

- `Manage Group`
  - Opens link management for the relevant group.
  - Prefer extending `LinkGroupManagementScreen` with an optional focused `groupId`, instead of creating another global manager.

- `Open Version List` / `View Linked Versions`
  - Strongly recommended and in scope.
  - Shows every linked version clearly:
    - source name;
    - source language;
    - title;
    - rating;
    - favorite status;
    - installed/missing source status;
    - last updated if available, otherwise link `updatedAt`;
    - primary-version marker.

- `Set Primary Version`
  - Lets the user choose which linked version controls the rated-list cover/title.
  - Recommendations must still use full group metadata, not only the primary version.

- `Select All In Group`
  - In selection mode, selects all currently visible installed members represented by the group.
  - Do not silently select missing/uninstalled source rows that cannot be acted on safely.

- `Merge Selected Into Group`
  - Requires at least two selected entries.
  - Requires confirmation.
  - Do not auto-merge by title. Manual selection is the user-confirmed intent.
  - If selected entries already belong to different confirmed groups, show a conflict confirmation explaining that groups will be merged into one group id.

- `Remove From Group`
  - Removes selected entry/entries from their current confirmed group.
  - Requires confirmation.
  - Must not clear ratings.

- `Ungroup`
  - Deletes the confirmed group links for the target group.
  - Requires confirmation.
  - Must not clear ratings.

## Data Model Plan

### Display Item Expansion

Update the rated manga display model so the UI can make correct decisions without recomputing groups in composables.

Recommended shape:

```kotlin
@Immutable
data class RatedMangaDisplayItem(
    val primary: LovedMangaEntry,
    val versionCount: Int,
    val confirmedGroupId: String?,
    val memberKeys: List<MangaTasteKey>,
    val confirmedGroupMemberKeys: List<MangaTasteKey>,
    val hasConfirmedGroup: Boolean,
)
```

Claude may keep the existing `LovedDisplayItem` name if a rename creates unnecessary churn, but the fields above are needed. If keeping the old name, document why.

Use a stable key type instead of repeated string parsing where practical:

```kotlin
data class RatedMangaKey(
    val source: Long,
    val url: String,
)
```

If there is already a reusable `MangaTasteKey`, reuse it instead of introducing a duplicate.

### Primary Version Persistence

Add a small additive table rather than modifying the existing link table:

```sql
CREATE TABLE manga_cross_source_group_primary (
    group_id TEXT NOT NULL PRIMARY KEY,
    source INTEGER NOT NULL,
    url TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Reasons:

- avoids changing the existing `manga_cross_source_link` primary key/upsert behavior;
- one primary per group is easy to enforce;
- deleting or changing a primary can be done independently;
- fallback remains simple if the selected primary is missing/uninstalled.

Add SQLDelight queries:

- `getByGroupId`;
- `getAll`;
- `upsert`;
- `deleteByGroupId`;
- `deleteAll`.

Add domain model and repository methods:

- `CrossSourceGroupPrimary`;
- `GetCrossSourceGroupPrimary`;
- `SetCrossSourceGroupPrimary`;
- `ClearCrossSourceGroupPrimary`.

Update `TasteRepository` and `TasteRepositoryImpl` with the smallest consistent methods.

Add a migration file with `CREATE TABLE IF NOT EXISTS`.

Before finalizing, check whether `manga_cross_source_link` is currently included in backup/sync. If it is, include primary-version data consistently. If link groups are not backed up, document that limitation instead of adding a partial backup field.

### Primary Selection Rules

When grouped display is enabled:

1. if the group has a stored primary and that primary is installed/visible and present in the current rating tier, use it;
2. otherwise fallback to the grouper's current primary key;
3. if the stored primary is missing, do not crash; show fallback and allow the version list to indicate that the stored primary is unavailable.

## UI Implementation Plan

### Rated Grid

File: `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`

Replace hidden recommendation gesture with explicit actions:

- remove current long-press recommendation behavior;
- keep tap-to-open manga outside selection mode;
- long-press enters selection mode and selects item;
- add visible top-right select action in the app bar;
- add item overflow/menu action on each card;
- keep version-count badge;
- remove or demote the Explore overlay if it duplicates the new menu. If kept, it must not be the only discoverable recommendation action.

### Selection Mode

Use a phone-friendly bottom action bar:

- `Change`;
- `Clear`;
- `Group`;
- `More`.

The app bar in selection mode should show selected count and a close action.

Bottom bar actions:

- `Change`: rating picker LOVE / LIKE / DISLIKE;
- `Clear`: confirm and clear ratings;
- `Group`: merge selected into group or select all in group depending on context;
- `More`: Mark Not Interested, Remove From Group, Ungroup, Favorite Other Versions where available.

Selection must survive recomposition. Prefer screen-model state over local-only composable state if practical.

Do not allow unsafe no-op actions:

- merge requires at least two selected entries;
- group recommendations require confirmed group 2+;
- clear requires at least one selected entry;
- remove from group requires selected entries that are actually linked.

### Confirmation Dialogs

Destructive or broad actions must ask for confirmation:

- clear rating;
- clear multiple ratings;
- ungroup;
- remove from group;
- merge groups;
- mark multiple as not interested;
- favorite multiple other versions if implemented.

Dialogs should use KMR strings and concise wording.

### Focused Version List

Create a focused screen or bottom sheet:

Suggested files:

- `app/src/main/java/exh/recs/links/LinkedVersionListScreen.kt`
- `app/src/main/java/exh/recs/links/LinkedVersionListScreenModel.kt`

Inputs:

- `groupId`.

Display:

- source name and language;
- title;
- rating label;
- favorite status;
- installed/missing source status;
- updated date where available;
- primary marker;
- actions per row:
  - open manga if installed/local manga exists;
  - set as primary;
  - remove from group.

This screen can reuse link data from `TasteRepository`, manga data from `GetManga`, source data from `SourceManager`, and taste data from `GetMangaTaste`.

Do not make this screen depend on the rated screen's in-memory display items. It should load from persisted group data by `groupId`.

## ScreenModel / Domain Implementation Plan

### Rated Screen State

Extend `LovedMangaScreenModel.State.Success` with:

- `selectionMode: Boolean`;
- `selectedKeys: Set<RatedMangaKey>`;
- primary-version map if needed;
- richer display items exposing group id and member keys.

Add actions:

- `enterSelection(key)`;
- `toggleSelection(key)`;
- `clearSelection()`;
- `selectAllInGroup(groupId)`;
- `changeSelectedRating(rating)`;
- `clearSelectedRatings()`;
- `markSelectedNotInterested()`;
- `mergeSelectedIntoGroup()`;
- `removeSelectedFromGroup()`;
- `ungroup(groupId)`;
- `setPrimaryVersion(groupId, key)`.

Keep side effects in screen model or interactors, not composables.

### Group Merge Behavior

Merge algorithm:

1. collect selected keys;
2. load existing links for those keys;
3. choose target group id:
   - if exactly one selected item already has a group, use it;
   - if multiple selected items have groups, choose the first stable group id and confirm merge;
   - if none have groups, create a new stable group id;
4. upsert links for all selected entries into the target group;
5. if merging multiple groups, rewrite all links from the other selected groups into the target group so the group is genuinely merged;
6. preserve existing titles and timestamps where possible;
7. refresh screen state.

Never merge merely because titles match.

### Clear Rating Behavior

For a selected display group:

- if the user selected the grouped item, decide whether the action applies only to the displayed primary or to all selected group members based on selection semantics shown in the UI.
- Recommended: grouped display item selection represents the displayed group, and bulk actions should clearly say how many versions will be affected before confirmation.
- If that is too much for v0.8.0, implement conservative behavior: selected card affects only the primary row unless the user uses `Select All In Group`.

Document whichever choice is implemented.

## Localization / Strings

All KMK UI strings must be KMR strings, not hardcoded.

Expected new strings include:

- `rated_manga_select`;
- `rated_manga_selected_count`;
- `rated_manga_action_see_recommendations`;
- `rated_manga_action_see_group_recommendations`;
- `rated_manga_action_find_other_versions`;
- `rated_manga_action_change_rating`;
- `rated_manga_action_clear_rating`;
- `rated_manga_action_mark_not_interested`;
- `rated_manga_action_favorite_other_versions`;
- `rated_manga_action_manage_group`;
- `rated_manga_action_view_linked_versions`;
- `rated_manga_action_set_primary_version`;
- `rated_manga_action_select_all_in_group`;
- `rated_manga_action_merge_selected_into_group`;
- `rated_manga_action_remove_from_group`;
- `rated_manga_action_ungroup`;
- confirmation title/body strings for destructive actions;
- version-list labels for source, language, rating, favorite, installed/missing, primary.

Follow existing Komikku/KMK string style.

## Safety And Error Handling

- All database actions must run off the main thread through repository/interactor paths.
- Do not directly mutate SQLDelight queries from composables.
- If a source is missing/uninstalled, the version list must show it as missing instead of crashing.
- If a manga row cannot be found for a linked version, show the link title and source/url fallback.
- If a selected action partly fails, surface a toast/snackbar and keep the screen usable.
- Do not allow `CancellationException` to be swallowed as a generic failure.
- Do not introduce Serializable screen arguments containing non-serializable sealed objects. Use primitive route arguments like `CrossExtensionMatchRouteMode`.

## Tests Required

Add or update focused tests. Do not rely only on manual APK testing.

### Unit Tests

- Rated display item grouping exposes confirmed group id and version/member counts.
- Stored primary version wins when installed/visible and falls back when missing.
- Long-press selection reducer enters selection mode and selects the item.
- Selection toggle adds/removes keys and exits cleanly when cleared.
- Group recommendation action is visible only for confirmed groups with 2+ versions.
- Merge selected into group:
  - creates new group when none selected had one;
  - merges two existing groups with confirmation path;
  - never auto-merges by title.
- Clear rating uses source/url or manga id and does not delete group links.
- Rating change keeps one effective rating per manga/group.
- Version list model marks installed vs missing sources correctly.

### Migration Tests

- New `manga_cross_source_group_primary` table exists after migration.
- Existing databases without the table migrate without losing `manga_cross_source_link` rows.

### Existing Regression Tests To Run

- `LovedMangaDuplicateGrouperTest`
- `RatedMangaExclusivityTest`
- cross-extension match route/state tests
- recommendation group seed tests
- any source evaluation tests touched indirectly
- full `:app:testDebugUnitTest` if time allows

### Build / Formatting

Run:

```text
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If a full build is skipped, document why and run the narrowest meaningful tests.

## Documentation Updates Required

Claude must update or create an implementation report after coding:

- `docs/recommendations/KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_IMPLEMENTATION.md`

Also update:

- `docs/recommendations/README.md`;
- `docs/recommendations/CURRENT_STATE.md`;
- `docs/recommendations/NEXT_WORK.md`;
- `RECOMMENDATION_VERSIONING.md` if an APK is produced;
- `CHANGELOG` / KMK release notes / What's New if applicable.

Implementation report must include:

- files changed;
- behavior changed;
- migrations added;
- tests run;
- build/APK output;
- known limitations;
- deviations from this plan.

## Non-Goals

- Do not rebuild group recommendations.
- Do not create a second duplicate grouping engine.
- Do not auto-merge by title.
- Do not change source evaluation scoring.
- Do not implement unrelated For You recommendation changes in this pass.
- Do not make a public release unless separately requested.

## Acceptance Criteria

- Long-press in Loved / Liked / Disliked enters selection mode, not recommendations.
- Top-right select action exists for discoverability.
- Selection mode has a bottom action bar with bulk actions.
- Item menu has clearly grouped Recommendation / Rating / Group actions.
- `See Group Recommendations` is visible only for confirmed linked groups with 2+ versions.
- Group recommendations use the existing row-based group recommendation screen.
- Users can open a linked version list and see every version clearly.
- Users can set a primary version, and that version controls cover/title in the rated list.
- Recommendations still use all linked group metadata.
- Clear/ungroup/merge/remove destructive actions require confirmation.
- Missing/uninstalled sources do not crash version list or rated list.
- Tests and docs are updated.

