# KMK-Recs v0.5.0 Cross-Extension Rating And Favorite Matching Plan

Date: 2026-06-16

Status: implemented in KMK-Recs v0.5.0. See `KMK_RECS_V0_5_0_CROSS_EXTENSION_MATCHING_IMPLEMENTATION.md` for what shipped and what was deferred.

Implementation note: the per-source result cap was changed from 5 (as written in this plan) to 2 after implementation. `PER_SOURCE_RESULT_LIMIT = 2` is the shipped value.

Target feature version: `KMK-Recs v0.5.0`

Expected APK naming after implementation:

```text
Komikku-v1.13.6-kmk.5.0-debug.apk
Komikku-v1.13.6-kmk.5.0-release.apk
```

## Purpose

Add a user-confirmed workflow for finding the same manga across multiple extensions, then applying a rating or favorite action to the selected matching versions.

The feature must preserve the distinction between:

- normal global search, which is for broad exploration and must keep its current behavior;
- bounded cross-extension matching, which is a focused rating/favorite workflow and may cap results per source.

This feature should not attempt fully automatic manga identity matching. Extension metadata is inconsistent, titles can differ, and some distinct works can share nearly identical names. The system should help find likely matches, but final identity confirmation belongs to the user.

## Current Baseline

Current documented feature version:

```text
KMK-Recs v0.4.4
```

Relevant implemented systems:

- `GlobalSearchScreen` / `GlobalSearchScreenModel` / `SearchScreenModel`
  - normal Browse global search;
  - searches visible enabled catalogue sources;
  - currently fetches page 1 per source with no per-source cap applied by the model;
  - supports pinned/all source filter;
  - includes KMK bulk favorite selection support in normal global search.
- `BulkFavoriteScreenModel`
  - existing add/remove favorite behavior;
  - existing duplicate library checks;
  - existing category/default-category handling;
  - existing metadata/chapter fetch-on-add behavior.
- `MangaScreen` / `MangaScreenModel`
  - manga detail action header has KMK `Rate` dropdown;
  - current `Love`, `Like`, `Dislike`, and `Clear rating` affect only the current manga;
  - `MangaScreenModel.setMangaTaste()` writes one row through `SetMangaTaste`.
- `manga_taste`
  - stores taste by local `manga_id`, `source`, `url`, title, rating, and timestamps;
  - repository already handles upsert by source/url safely.
- backup/sync
  - current taste proto numbers `620-623` are used;
  - `Backup.kt` says `620-629` are reserved for the KMK taste system.

## Non-Goals

Do not change normal global search behavior:

- no new per-source result cap in normal global search;
- no forced selection mode in normal global search;
- no change to existing global-search source filters, sorting, or result count;
- no automatic rating/favoriting from normal global search.

Do not create a full local manga catalogue.

Do not use AniList, MAL, MangaUpdates, or tracker identity as the main matching authority. These databases are incomplete for many manga/manhwa/manhua and should not decide cross-extension identity.

Do not auto-favorite or auto-rate unconfirmed matches in the background.

## Product Behavior

### Entry Points

Add one or more explicit actions from the manga detail page, preferably inside the existing KMK rating dropdown or a nearby overflow/action menu.

Recommended wording:

```text
Love other versions
Like other versions
Dislike other versions
Favorite other versions
Link other versions
```

Recommended v0.5.0 scope:

1. Add rating-based matching actions:
   - `Love other versions`
   - `Like other versions`
   - `Dislike other versions`
2. Add favorite-based matching action if it can reuse the same confirmation screen cleanly:
   - `Favorite other versions`
3. Add `Link other versions` only if link groups are implemented in the same pass.

Keep the existing single-manga rating actions exactly as they are:

- tapping `Love` still immediately loves only the current manga;
- tapping `Like` still immediately likes only the current manga;
- tapping `Dislike` still immediately dislikes only the current manga;
- tapping `Clear rating` still clears only the current manga.

### Search Behavior

When launched from the matching workflow:

- use the current manga title as the initial query;
- search visible enabled catalogue sources;
- respect normal source enabled/disabled state;
- preferably respect the KMK recommendation language filter, defaulting to EN, because this workflow is about finding usable matching versions rather than exploring every installed language;
- use source priority order where practical so preferred sources appear first;
- cap results at 5 per source;
- exclude Local Source (`id == 0L`) unless explicitly approved later;
- do not mutate normal global search preferences.

The 5-result cap applies only to this workflow. Normal global search remains uncapped.

### Selection Behavior

All manga results returned by this workflow should be selected by default.

The user should be able to manually deselect any result that is not truly the same manga.

Important details:

- auto-selection applies only to results in the matching workflow;
- if new source results arrive while the screen is loading, those newly returned results should also be selected by default;
- if the user manually deselects an item, the app must not reselect it when the same source result refreshes;
- the current manga itself should normally be excluded from the selectable result list if it appears as a result from its own source, because the requested action already begins from that manga.

Recommended state model:

```kotlin
selectedKeys: Set<MangaIdentityKey>
manuallyDeselectedKeys: Set<MangaIdentityKey>
```

Where:

```kotlin
data class MangaIdentityKey(
    val source: Long,
    val url: String,
)
```

On every successful result batch:

```text
for each candidate:
  if candidate is not the origin manga
  and candidate key is not manuallyDeselectedKeys
  then add candidate key to selectedKeys
```

### Confirmation Behavior

The confirmation toolbar/button should clearly state the action:

```text
Apply Love to 12 versions
Apply Like to 12 versions
Apply Dislike to 12 versions
Favorite 12 versions
Link 12 versions
```

Applying a rating:

- writes a normal `manga_taste` row for every selected manga using `SetMangaTaste`;
- writes or updates the origin manga rating as well if the action was launched before rating the origin;
- should use source/url identity, not title identity;
- should run in IO/background scope;
- should show progress or disable the confirm button while running.

Applying favorite:

- should reuse existing favorite/library behavior as much as possible;
- should preserve default category behavior;
- should preserve duplicate-library warning behavior where practical;
- should not silently remove favorites.

Recommended v0.5.0 favorite behavior:

- use a dedicated bulk-confirm path based on `BulkFavoriteScreenModel` patterns;
- if duplicates are detected, show the existing duplicate dialog or a workflow-specific duplicate summary;
- do not skip duplicate warnings unless the user explicitly chooses to continue.

## Cross-Extension Link Groups

### Why Link Groups Are Recommended

The first search is expensive and user-confirmed. After the user confirms that several source entries are the same work, the app should remember that relationship.

This avoids repeated extension searches and gives future features a reliable local identity layer.

### Data Model

Use a small local table for user-confirmed same-work groups.

Recommended SQLDelight schema:

```sql
CREATE TABLE manga_cross_source_link (
    group_id TEXT NOT NULL,
    manga_id INTEGER NOT NULL,
    source INTEGER NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY(group_id, source, url),
    FOREIGN KEY(manga_id) REFERENCES mangas(_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX manga_cross_source_link_source_url_index
ON manga_cross_source_link(source, url);

CREATE INDEX manga_cross_source_link_group_index
ON manga_cross_source_link(group_id);
```

Use a generated UUID/string for `group_id`.

Why this shape:

- `(source, url)` is the stable cross-device identity already used by taste restore;
- `manga_id` is convenient locally but should not be trusted across devices;
- one manga entry should belong to one cross-source link group at a time;
- the table stays tiny because it contains only user-confirmed links.

### Link Creation Rules

When the user confirms selected matches:

1. Create or reuse a group for the origin manga.
2. Insert origin manga into the group.
3. Insert selected manga into the same group.
4. If a selected manga already belongs to another group, merge groups only after explicit user confirmation or use the existing group if the origin is already in it.

Recommended v0.5.0 simplification:

- If a selected candidate is already linked to another group, show it as a warning and skip linking it unless the user explicitly confirms group merge.
- Defer group-merge UI if needed.

### Later Uses

Once link groups exist, later features can use them to:

- apply rating to already-linked versions without searching;
- apply favorite to already-linked versions without searching;
- improve Top Picks dedupe;
- show "Linked across N sources" on manga detail;
- show/manage linked versions.

## Implementation Architecture

### Preferred Screen Structure

Create a separate workflow rather than modifying normal global search UI in place.

Recommended files:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchModels.kt
```

Possible package name alternatives:

```text
exh.matching
exh.recs.matching
eu.kanade.tachiyomi.ui.browse.matching
```

Prefer `exh.recs.matching` because the feature belongs to the KMK recommendation/taste layer, not upstream browse behavior.

### Reusing Search Logic

`SearchScreenModel` currently contains useful source search behavior but no per-workflow cap hook.

Recommended minimal refactor:

1. Add a protected open property to `SearchScreenModel`:

```kotlin
protected open val perSourceResultLimit: Int? = null
```

2. Apply it only after `networkToLocalManga()`:

```kotlin
val titles = page.mangas
    .map { it.toDomainManga(source.id) }
    .distinctBy { it.url }
    .let { networkToLocalManga(it) }
    .let { list -> perSourceResultLimit?.let(list::take) ?: list }
```

3. Leave `GlobalSearchScreenModel` unchanged so normal global search keeps `null` and remains uncapped.

4. Create `CrossExtensionMatchScreenModel` that either:
   - extends `SearchScreenModel` with `perSourceResultLimit = 5`, or
   - uses a small copied search loop if the state/selection needs are too different.

Preferred: extend or lightly refactor `SearchScreenModel` only if the default behavior stays exactly unchanged and tests/manual verification confirm normal global search remains uncapped.

### Source Selection For Matching

The matching workflow should not use `GlobalSearchScreenModel` directly if it would mutate global search source-filter preferences.

Current concern:

- `SearchScreenModel.setSourceFilter()` writes `preferences.globalSearchPinnedState()`.
- The matching workflow should not unexpectedly change the user's normal global search filter.

Recommended solution:

- override matching source selection without calling `setSourceFilter()`;
- use an internal workflow-only source mode;
- do not write `globalSearchPinnedState()` or `globalSearchFilterState()` from matching.

Source order recommendation:

```text
visible catalogue sources
-> enabled languages / recommendation language filter
-> disabled source exclusion
-> Local Source exclusion
-> manual recommendation source priority order, where available
-> source name fallback sort
```

### UI Reuse

The existing `GlobalSearchContent` and `GlobalSearchCardRow` are browse-oriented horizontal rows. They can be reused if selection state can be displayed clearly.

However, this workflow needs:

- all results selected by default;
- easy deselection;
- clear action-specific confirmation;
- per-source cap;
- loading/progress state;
- no normal global-search toolbar.

Recommended UI:

- top app bar with title like `Rate other versions`;
- subtitle/summary:

```text
Selected 18 of 23 possible matches
```

- grouped rows by source;
- checkbox or selected overlay on each manga card;
- per-source loading/error/empty state;
- bottom confirmation button.

Use existing card components if possible, but do not force the normal global search toolbar into this workflow.

## Matching And Ranking

Because the user wants all results selected by default, ranking is mostly about display order and trust, not filtering.

Recommended display scoring:

1. exact normalized title match;
2. normalized title containment or high similarity;
3. same author/artist if known;
4. source priority;
5. result order from the extension.

Do not drop lower-confidence results solely because the score is weak. Show them selected by default per the requested behavior, but visually communicate enough context for manual deselection.

Useful row/card context:

- title;
- source;
- in-library badge from existing card if available;
- current rating badge if already rated;
- matched reason if cheap:

```text
Exact title
Similar title
Same author
Already linked
```

No network calls should be made only to fetch author/artist/details before showing candidates. Use metadata already present in the search result. If details are missing, leave reason simple.

## Rating Workflow Details

### Launching From Manga Detail

In `MangaInfoHeader`, extend the KMK taste dropdown with separate "other versions" actions.

Do not replace existing actions.

Potential callback shape:

```kotlin
onTasteOtherVersionsClicked: ((MangaRating) -> Unit)? = null
```

Wire through:

- `MangaInfoHeader`
- `MangaScreen` presentation composables
- `eu.kanade.tachiyomi.ui.manga.MangaScreen`

Then navigate:

```kotlin
navigator.push(
    CrossExtensionMatchScreen(
        originMangaId = successState.manga.id,
        mode = CrossExtensionMatchMode.Rating(rating),
    ),
)
```

### Applying Ratings

Add an interactor rather than writing loops in the screen model:

```text
domain/src/main/java/tachiyomi/domain/taste/interactor/SetMangaTasteBatch.kt
```

or:

```text
ApplyMangaTasteToMatches.kt
```

Behavior:

- accept `List<Manga>` and `MangaRating`;
- call `TasteRepository.upsertMangaTaste()` inside one transaction if repository support is extended;
- preserve createdAt when overwriting an existing taste if feasible;
- set updatedAt to now.

Current `SetMangaTaste` always sets `createdAt = now`, even on update. v0.5.0 can leave that alone for consistency, or improve the repository/interactor if tests cover it.

### Clearing Ratings

Do not include cross-extension clear rating in v0.5.0 unless explicitly approved.

Reason:

- clearing across many sources is more destructive/confusing than applying a new rating;
- it needs a stronger confirmation UX.

## Favorite Workflow Details

Favorite syncing should be optional in v0.5.0 depending on implementation complexity.

Recommended if included:

- mode: `CrossExtensionMatchMode.Favorite`;
- confirmation: `Favorite N versions`;
- only favorite selected manga that are not already favorite;
- use existing default category behavior;
- reuse duplicate warnings rather than silently bypassing them;
- fetch metadata/chapters on add according to existing library preferences.

If `BulkFavoriteScreenModel` is too tightly coupled to global search selection state:

- extract the favorite application logic into a domain/use-case helper;
- keep UI-specific dialogs in the matching screen model.

Do not add unfavorite cross-source behavior in v0.5.0.

## Backup, Restore, And Sync

If link groups are implemented, include them in backup/restore/sync in the same pass.

Recommended proto:

```kotlin
@ProtoNumber(624) var backupCrossSourceMangaLinks: List<BackupCrossSourceMangaLink> = emptyList()
```

Recommended backup model:

```kotlin
@Serializable
data class BackupCrossSourceMangaLink(
    @ProtoNumber(1) val groupId: String = "",
    @ProtoNumber(2) val source: Long = 0,
    @ProtoNumber(3) val url: String = "",
    @ProtoNumber(4) val title: String = "",
    @ProtoNumber(5) val updatedAt: Long = 0,
)
```

Restore behavior:

- resolve local `manga_id` by `(source, url)`;
- skip entries whose manga row does not exist locally;
- restore each row independently with try/catch and collect errors, matching `TasteRestorer` style;
- do not fail the whole restore because one linked manga is missing.

Sync behavior:

- verify `SyncService` merges the new backup list;
- merge by `(groupId, source, url)` or by `(source, url)` depending on final schema;
- prefer newer `updatedAt` when conflicts exist;
- document whether links sync automatically.

## Versioning And Docs

Because this adds a new user-visible capability and likely a new schema/backup field, use:

```text
KMK-Recs v0.5.0
```

Update after implementation:

- `docs/recommendations/KMK_RECS_V0_5_0_CROSS_EXTENSION_MATCHING_IMPLEMENTATION.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `RECOMMENDATION_VERSIONING.md`
- `KmkRecsReleaseNotes.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

What's New should mention only user-facing items, for example:

```markdown
## KMK-Recs v0.5.0

- Added a new workflow for finding matching manga across sources and applying Love, Like, or Dislike to selected versions.
- Matching searches are capped per source so the confirmation list stays manageable, while normal global search remains unchanged.
- Confirmed cross-source matches can now be remembered locally for future rating/favorite actions.
```

Do not mention:

- implementation docs;
- internal table names;
- tests;
- refactors.

## Testing Plan

### Unit Tests

Add pure tests for:

- per-source cap applies only in matching workflow helper, not normal global search defaults;
- auto-selection selects incoming results by default;
- manual deselection is preserved when results update;
- origin manga is excluded from selectable matches;
- link-group insert/merge helper behavior if link groups are implemented;
- backup round-trip for `BackupCrossSourceMangaLink` if backup is implemented.

Recommended test files:

```text
app/src/test/java/exh/recs/matching/CrossExtensionMatchSelectionTest.kt
app/src/test/java/exh/recs/matching/CrossSourceLinkBackupTest.kt
```

### Existing Tests To Run

At minimum:

```text
./gradlew :app:testDebugUnitTest
```

If building APK:

```text
./gradlew :app:assembleDebug
```

Focused tests:

```text
./gradlew :app:testDebugUnitTest --tests "*CrossExtensionMatch*"
./gradlew :app:testDebugUnitTest --tests "*Taste*"
./gradlew :app:testDebugUnitTest --tests "*Backup*"
```

### Manual Verification

1. Open normal Browse global search and verify it still shows uncapped source rows as before.
2. Open manga detail and use single-manga `Love`, `Like`, `Dislike`; verify the current fast behavior still works.
3. From manga detail, launch `Love other versions`.
4. Confirm the matching workflow searches sources and shows no more than 5 results per source.
5. Confirm all returned candidates are selected by default.
6. Deselect an incorrect candidate; wait for other sources to finish; confirm it stays deselected.
7. Confirm applying rating writes taste rows for selected candidates.
8. Confirm For You uses the newly rated entries in the taste profile.
9. If favorite mode is included, confirm selected entries are added to library with existing duplicate/category behavior.
10. If link groups are included, relaunch matching from one linked manga and confirm already-linked versions can be reused or identified.
11. Backup and restore taste/link data if schema/backup changed.

## Risks And Mitigations

### Accidental wrong matches

Risk: all results are selected by default, so bad extension results can be applied accidentally.

Mitigation:

- keep cap at 5 per source;
- show source grouping clearly;
- show title/source/in-library/rating badges where possible;
- require explicit final confirmation;
- do not auto-apply in the background.

### Normal global search regression

Risk: adding caps to shared search code could unintentionally limit normal global search.

Mitigation:

- make cap opt-in only;
- default cap must be `null`;
- include manual verification that normal global search is uncapped.

### Preference pollution

Risk: matching workflow could mutate `globalSearchPinnedState()` or `globalSearchFilterState()`.

Mitigation:

- do not call `setSourceFilter()` from matching;
- keep matching source filtering local to its screen model.

### Extension load/performance

Risk: searching many sources can be slow.

Mitigation:

- use existing 5-thread dispatcher pattern;
- cap result display to 5 per source;
- respect enabled languages and disabled sources;
- consider source priority ordering;
- do not fetch details/chapters just to rank candidates.

### Schema/backup fragility

Risk: new link table/proto fields can fail silently if proto numbers collide or restore mapping is incomplete.

Mitigation:

- use proto number `624` within the reserved KMK taste range;
- add backup round-trip tests;
- restore by `(source, url)`;
- document the range in `Backup.kt` and versioning notes.

## Suggested Implementation Phases

### Phase 1: Matching search screen, no persistence beyond ratings

- Add `CrossExtensionMatchMode`.
- Add `CrossExtensionMatchScreen` and screen model.
- Reuse/lift source search logic.
- Apply per-source cap of 5 only in this workflow.
- Auto-select all incoming results by default.
- Add rating action application.
- Verify normal global search remains unchanged.

This phase delivers the core user value.

### Phase 2: Favorite mode

- Add favorite mode to the same screen.
- Reuse/extract favorite-add behavior from `BulkFavoriteScreenModel`.
- Preserve duplicate/category behavior.
- Keep unfavorite out of scope.

### Phase 3: Cross-source link groups

- Add SQLDelight table and migration.
- Add domain model/repository/interactors.
- Save confirmed matches after rating/favorite confirmation.
- Use saved links to prepopulate or identify known matches.

### Phase 4: Backup/restore/sync for link groups

- Add `BackupCrossSourceMangaLink`.
- Add `Backup.backupCrossSourceMangaLinks` at proto `624`.
- Add creator/restorer support.
- Add sync merge support.
- Add round-trip tests.

### Phase 5: Polish and docs

- Add strings.
- Update What's New.
- Update docs and versioning.
- Build debug APK.

## Recommended v0.5.0 Scope Decision

Best balanced scope:

- Phase 1 required.
- Phase 2 included if favorite logic can be cleanly reused.
- Phase 3 included if schema work is acceptable.
- Phase 4 required if Phase 3 is included.
- Phase 5 required.

If implementation risk feels high, split it:

- v0.5.0: rating workflow only, no link-group persistence.
- v0.5.1: favorite workflow and link groups.

However, the long-term best version of this feature includes link groups, because user-confirmed identity is the reliable solution to cross-extension ambiguity.

## Summary For Claude

Implement a separate cross-extension matching workflow for KMK ratings/favorites without changing normal global search.

Key requirements:

- normal global search remains uncapped and unchanged;
- matching workflow caps results to 5 per source;
- matching workflow launches from manga detail actions;
- all returned matching candidates are selected by default;
- user can deselect wrong candidates before confirming;
- selected candidates can receive Love/Like/Dislike ratings;
- favorite selected candidates if included, using existing favorite/duplicate/category behavior;
- use `(source, url)` as stable identity;
- strongly consider persisting user-confirmed cross-source link groups;
- if link groups are persisted, include backup/restore/sync with proto `624`;
- update docs, tests, versioning, and user-facing What's New.

