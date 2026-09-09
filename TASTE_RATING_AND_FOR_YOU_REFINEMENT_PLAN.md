# Taste Rating Persistence and For You Refinement Plan

Date: 2026-06-12

Status: mostly implemented historical plan. Use this file for rationale, but use `docs/recommendations/CURRENT_STATE.md` and `RECOMMENDATION_IMPLEMENTATION_AUDIT.md` for current behavior.

This document is a planning handoff for Claude Code. It describes the current problem, the likely root cause, the desired behavior, and the safest implementation approach. Do not implement from this document until the user approves the plan and provides a separate implementation prompt.

## Situation Summary

The personal recommendation system is now mostly working:

- Manga detail pages expose a `Rate` action with `Love`, `Like`, `Dislike`, and clear behavior.
- The Browse `For You` tab uses those ratings to build a taste profile.
- The `For You` tab searches across extension sources and returns useful recommendations.
- Already-rated manga are currently filtered out of `For You`.
- Backup, restore, sync, caching, dedupe, and source-row hardening have been added or partially added.

However, the user is seeing a serious state problem:

1. Open a manga from the library.
2. Tap `Rate`.
3. Select `Love`, `Like`, or `Dislike`.
4. The manga detail UI immediately changes from `Rate` to the selected value.
5. Leave the manga detail screen.
6. Reopen the same manga.
7. The rating UI returns to the base `Rate` state.
8. Despite the UI losing the selected state, `For You` appears to react to the rating after refresh because the rated manga stops appearing there.

This strongly suggests that the rating write is happening, but the manga detail screen is not reliably reading the same saved taste row when it is reopened.

## Core Diagnosis

The taste system currently treats `manga_id` as the primary lookup key for the manga detail UI.

Current files:

- `domain/src/main/java/tachiyomi/domain/taste/repository/TasteRepository.kt`
  - `getMangaTaste(mangaId: Long)`
  - `getMangaTasteAsFlow(mangaId: Long)`
  - `upsertMangaTaste(taste: MangaTaste)`
  - `deleteMangaTaste(mangaId: Long)`

- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetMangaTaste.kt`
  - `await(mangaId)`
  - `subscribe(mangaId)`
  - `awaitAll()`

- `domain/src/main/java/tachiyomi/domain/taste/interactor/SetMangaTaste.kt`
  - writes `mangaId`, `source`, `url`, `title`, and `rating`

- `data/src/main/sqldelight/tachiyomi/data/manga_taste.sq`
  - `manga_id INTEGER NOT NULL PRIMARY KEY`
  - `source INTEGER NOT NULL`
  - `url TEXT NOT NULL`
  - `CREATE UNIQUE INDEX manga_taste_source_url_index ON manga_taste(source, url)`
  - `getByMangaId`
  - no `getBySourceUrl`
  - no `deleteBySourceUrl`

- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt`
  - subscribes with `getMangaTaste.subscribe(mangaId)`
  - initial state currently loads with `getMangaTaste.await(manga.id)`
  - writes with `setMangaTasteInteractor.await(mangaId = manga.id, source = manga.source, url = manga.url, ...)`
  - clears with `clearMangaTasteInteractor.await(mangaId)`

The problem is that Komikku/Mihon can represent the same manga across navigation paths with a local database row ID that is not always the best stable identity. The stable identity for a source manga is `source + url`.

The code already partly acknowledges this:

- `manga_taste.sq` has a unique index on `(source, url)`.
- `BackupMangaTaste` stores `source` and `url`.
- `TasteRestorer` resolves restored taste rows by `(url, source)` first.
- `SyncService` merges taste ratings by `(source, url)`.

So the system is already conceptually source-url based, but the manga detail screen still reads and clears primarily by local `mangaId`.

## Desired Behavior

The rating UI should be stable:

- If a user rates a manga `Love`, leaving and reopening the manga should still show `Love`.
- If a user rates a manga `Dislike`, leaving and reopening the manga should still show `Dislike`.
- If a user clears a rating, leaving and reopening the manga should show `Rate`.
- The same source manga should resolve the same taste row even if reached from library, source browse, search, recommendations, or another navigation path.
- `For You` should use the same identity logic as the manga detail UI, so filtering and taste profile behavior agree.

## Important Product Considerations

The user also raised two product-design points.

### Rated Manga In For You

Current behavior:

- `BrowsePersonalRecommendationsScreenModel` filters out rated manga from `For You`:
  - `ratedMangaIds = getMangaTaste.awaitAll().map { it.mangaId }.toHashSet()`
  - search results filter `it.id in ratedMangaIds`
  - cache read also filters `manga.id in ratedMangaIds`

This behavior is useful for discovery, but it is too rigid.

The user sees the value in hiding rated manga, but also sees a valid rediscovery case:

- A loved manga might not have been followed for a long time.
- It may be useful for it to reappear in `For You`.
- Liked/loved manga can be reminders, not just already-known entries.

Therefore, rated-manga visibility should become a recommendation setting instead of a hardcoded behavior.

Recommended setting:

`For You rated manga visibility`

Options:

1. `Hide all rated manga`
   - Current discovery-first behavior.
   - Hides Love, Like, and Dislike.

2. `Hide disliked manga only`
   - Recommended default if changing behavior.
   - Keeps things the user disliked out of discovery.
   - Allows liked/loved manga to reappear as rediscovery.

3. `Show all rated manga`
   - Useful for users who want `For You` to behave as a broader personalized feed.
   - Rated manga should show a small state badge later, but badge work can be deferred.

If this is too much UI for the immediate fix, then keep the current behavior temporarily, but design the implementation so it can be changed cleanly.

### Editable Browse Tabs

The user also noted that Browse tabs such as Feed and Migrate take space even though they are often unused.

This is a separate feature and should not be mixed into the rating persistence fix.

Potential future feature:

- Allow Browse tabs to be hidden/reordered:
  - Sources
  - Feed
  - Extensions
  - Migrate
  - For You

Recommended status:

- Document as future work only.
- Do not implement in the same pass as taste identity and `For You` filtering.

## Implementation Plan

### Phase 1: Make Manga Taste Identity Source-URL Aware

Goal:

The manga detail UI should read, subscribe, write, and clear taste rows using the stable source manga identity whenever possible.

#### 1. Add SQLDelight Queries

File:

- `data/src/main/sqldelight/tachiyomi/data/manga_taste.sq`

Add:

```sql
getBySourceUrl:
SELECT *
FROM manga_taste
WHERE source = :source
AND url = :url;

deleteBySourceUrl:
DELETE FROM manga_taste
WHERE source = :source
AND url = :url;
```

Consider changing `upsert` conflict behavior.

Current:

```sql
ON CONFLICT(manga_id)
DO UPDATE
```

Problem:

- There is also a unique index on `(source, url)`.
- If the same `source + url` is rated through a different local `manga_id`, the insert can conflict on `(source, url)` instead of `manga_id`.
- Because the upsert only handles `manga_id`, that conflict may fail or be ignored by caller error handling.

Preferred behavior:

- There should only be one taste row per source manga.
- If a source-url taste already exists, update its `manga_id` to the current local row and update the rating.

SQLite/SQLDelight may not support every `ON CONFLICT` target form equally depending on version. Use the safest supported approach.

Preferred SQL if accepted:

```sql
upsert:
INSERT INTO manga_taste(manga_id, source, url, title, rating, created_at, updated_at)
VALUES (:mangaId, :source, :url, :title, :rating, :createdAt, :updatedAt)
ON CONFLICT(source, url)
DO UPDATE
SET
    manga_id = :mangaId,
    title = :title,
    rating = :rating,
    updated_at = :updatedAt;
```

If SQLDelight does not accept the conflict target because `(source, url)` is only an index, then either:

- change the table migration/schema to declare `UNIQUE(source, url)` directly, or
- implement repository-level transaction logic:
  1. delete or update existing row by source-url,
  2. then insert/update by manga id.

Be careful with existing installs:

- The table already exists from migration `46.sqm`.
- If the schema changes beyond adding queries, a new migration may be needed.
- Do not casually rewrite migration `46.sqm` if users already installed APKs built from it.
- For an already-shipped install, create a new migration number if table/index structure changes.

#### 2. Extend TasteRepository

File:

- `domain/src/main/java/tachiyomi/domain/taste/repository/TasteRepository.kt`

Add:

```kotlin
suspend fun getMangaTaste(source: Long, url: String): MangaTaste?

fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?>

suspend fun deleteMangaTaste(source: Long, url: String)
```

Keep the existing manga-id methods for compatibility and tests.

#### 3. Implement Repository Methods

File:

- `data/src/main/java/tachiyomi/data/taste/TasteRepositoryImpl.kt`

Implement the new source-url methods using the new SQLDelight queries:

```kotlin
override suspend fun getMangaTaste(source: Long, url: String): MangaTaste? =
    handler.awaitOneOrNull {
        manga_tasteQueries.getBySourceUrl(source, url, mangaTasteMapper)
    }

override fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?> =
    handler.subscribeToOneOrNull {
        manga_tasteQueries.getBySourceUrl(source, url, mangaTasteMapper)
    }

override suspend fun deleteMangaTaste(source: Long, url: String) {
    handler.await { manga_tasteQueries.deleteBySourceUrl(source, url) }
}
```

#### 4. Extend Interactors

Files:

- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetMangaTaste.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/ClearMangaTaste.kt`

Add overloads:

```kotlin
suspend fun await(source: Long, url: String): MangaTaste? = repository.getMangaTaste(source, url)

fun subscribe(source: Long, url: String): Flow<MangaTaste?> =
    repository.getMangaTasteAsFlow(source, url)
```

For clear:

```kotlin
suspend fun await(source: Long, url: String) = repository.deleteMangaTaste(source, url)
```

Keep old `mangaId` overloads.

#### 5. Update MangaScreenModel Rating Read Path

File:

- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt`

Current issue:

- It subscribes early with `getMangaTaste.subscribe(mangaId)`.
- It seeds success state with `getMangaTaste.await(manga.id)`.
- Both are manga-id based.

Desired approach:

Once the manga is loaded and the actual `source + url` is known:

- seed `State.Success.mangaTaste` with `getMangaTaste.await(manga.source, manga.url)`.
- subscribe to `getMangaTaste.subscribe(manga.source, manga.url)`.

Recommended implementation shape:

Do not start a manga-id taste subscription before the manga row has loaded. Instead, after loading `manga` in the existing init flow:

```kotlin
val initialMangaTaste = getMangaTaste.await(manga.source, manga.url)
```

Then after `State.Success` is created, start or switch the subscription:

```kotlin
screenModelScope.launchIO {
    getMangaTaste.subscribe(manga.source, manga.url).collect { taste ->
        updateSuccessState { it.copy(mangaTaste = taste) }
    }
}
```

Watch for duplicate collectors:

- The screen model is per screen, so a single collector after initial manga load is acceptable.
- If there is already an early collector, remove or replace it.

#### 6. Update MangaScreenModel Clear Path

Current:

```kotlin
clearMangaTasteInteractor.await(mangaId)
```

Preferred:

```kotlin
val manga = successState?.manga ?: return
clearMangaTasteInteractor.await(manga.source, manga.url)
```

Fallback:

- If `successState?.manga` is unavailable, use the old `mangaId` method.

#### 7. Keep Write Path Source-URL Complete

Current write path already passes:

- `manga.id`
- `manga.source`
- `manga.url`
- `manga.title`
- `rating`

Keep this, but ensure `upsert` handles source-url conflicts robustly.

## Phase 2: Make For You Filtering Use Stable Taste Identity

> **Status: IMPLEMENTED.** `BrowsePersonalRecommendationsScreenModel` now uses `MangaTasteKey(source, url)` instead of `manga.id`. `shouldHideForYou()` helper added. Build verified, tests passed.

Goal:

`For You` should agree with the manga detail rating state.

Current:

File:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`

It builds:

```kotlin
val ratedMangaIds = getMangaTaste.awaitAll().map { it.mangaId }.toHashSet()
```

Then filters:

```kotlin
filterNot { it.favorite || it.id in ratedMangaIds }
```

This is fragile for the same reason: local row IDs are not the best identity.

Change to source-url identity.

Recommended helper:

```kotlin
private data class MangaTasteKey(
    val source: Long,
    val url: String,
)
```

Build:

```kotlin
val ratedMangaKeys = getMangaTaste.awaitAll()
    .map { MangaTasteKey(it.source, it.url) }
    .toHashSet()
```

Filter with:

```kotlin
val key = MangaTasteKey(manga.source, manga.url)
```

This applies to:

- fresh search result filtering,
- cache read filtering,
- any known/rated filtering added by dedupe or display logic.

## Phase 3: Add Rated Manga Visibility Setting For For You

> **Status: IMPLEMENTED.** `RatedMangaVisibility` enum added. `SourcePreferences.recommendationRatedMangaVisibility()` pref wired through settings screen, screen model, and For You filtering. Default is `HIDE_DISLIKED_ONLY`. UI in Recommendation Settings. Build verified, 12 tests passed.

Goal:

Do not hardcode whether rated manga appear in `For You`.

Recommended setting:

`For You rated manga visibility`

Options:

```kotlin
enum class RatedMangaVisibility {
    HIDE_ALL_RATED,
    HIDE_DISLIKED_ONLY,
    SHOW_ALL_RATED,
}
```

Recommended default:

- If preserving current behavior matters most: `HIDE_ALL_RATED`.
- If improving rediscovery now matters most: `HIDE_DISLIKED_ONLY`.

Given the user's latest feedback, `HIDE_DISLIKED_ONLY` is probably the better future default, but changing defaults can surprise users. Consider adding the setting with `HIDE_ALL_RATED` first, then let the user switch it.

Suggested storage location:

- Prefer an existing preference service if one is already used for source/browse recommendations.
- Current likely candidate:
  - `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`

Possible method:

```kotlin
fun recommendationRatedMangaVisibility() = preferenceStore.getEnum(
    "recommendation_rated_manga_visibility",
    RatedMangaVisibility.HIDE_ALL_RATED,
)
```

If putting this enum in `SourcePreferences` feels semantically awkward, create a small recommendation preferences holder. Do not over-engineer it.

Update:

- `BrowsePersonalRecommendationsScreenModel`
- `RecommendationsSettingsScreen`
- `RecommendationsSettingsScreenModel`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

UI placement:

- Add it to Recommendation Settings near source exclusions and tag preferences.
- Use a compact list preference / segmented option / dropdown following existing settings conventions.
- Do not redesign the settings screen.

Filtering rules:

```kotlin
private fun shouldHideRatedManga(
    manga: Manga,
    tasteByKey: Map<MangaTasteKey, MangaTaste>,
    visibility: RatedMangaVisibility,
): Boolean {
    val taste = tasteByKey[MangaTasteKey(manga.source, manga.url)] ?: return false
    return when (visibility) {
        HIDE_ALL_RATED -> true
        HIDE_DISLIKED_ONLY -> taste.rating == MangaRating.DISLIKE.value
        SHOW_ALL_RATED -> false
    }
}
```

Keep favorite filtering separate:

- The user may also want loved manga from library to reappear later.
- But current implementation hides favorites too: `it.favorite`.
- Do not change favorite filtering in this pass unless explicitly approved.
- Document this as a separate option:
  - `Hide library manga in For You`

## Phase 4: Hide Migrate and For You Browse Tabs

> **Status: IMPLEMENTED.**
> Added `hideMigrateTab()` and `hideForYouTab()` boolean prefs to `UiPreferences`.
> `BrowseTab.kt` now builds its tab list dynamically with `buildList { }.toImmutableList()`.
> Toggles appear in Settings → Browse under the Feed group.
> Sources and Extensions tabs are always visible and cannot be hidden.

## Phase 4 (original): Document Editable Browse Tabs As Future Work

Do not implement this now.

Add a short section to the existing recommendation research or hardening document explaining:

- User rarely uses Feed and Migrate.
- Browse tab space is limited.
- Future work could support showing/hiding/reordering Browse tabs.
- This should be a separate UI preferences feature, not part of taste persistence.

Potential future implementation:

- Add preference storing enabled tab IDs and ordering.
- Update `BrowseTab.kt` to build tab list from preferences.
- Provide settings UI under Browse settings.
- Ensure at least one Browse tab remains enabled.

## Testing Plan

### Unit/Repository Tests

Add tests if practical for:

- `TasteRepositoryImpl` source-url lookup.
- Upsert behavior when same `source + url` is saved with a different `mangaId`.
- Delete by source-url.

If repository tests are heavy due SQLDelight setup, add focused tests around interactors/helpers where possible.

### Manual Test: Rating Persistence

1. Open a manga from Library.
2. Select `Love`.
3. Confirm UI changes to `Love`.
4. Back out to Library.
5. Reopen the same manga.
6. Confirm UI still shows `Love`.
7. Change to `Dislike`.
8. Back out and reopen.
9. Confirm UI still shows `Dislike`.
10. Clear rating.
11. Back out and reopen.
12. Confirm UI shows `Rate`.

Repeat from:

- library entry,
- source browse/search result,
- `For You` result,
- recommendation drill-down result if available.

### Manual Test: For You Identity

1. Rate a manga from Library.
2. Refresh `For You`.
3. Confirm filtering follows the configured rated visibility setting.
4. Open the same title from a search/source path.
5. Confirm the detail screen shows the same rating state.

### Manual Test: Rated Visibility Setting

For each setting:

1. `Hide all rated manga`
   - Love/Like/Dislike should not appear in `For You`.

2. `Hide disliked manga only`
   - Disliked manga should not appear.
   - Liked/loved manga may appear.

3. `Show all rated manga`
   - Rated manga may appear.

## Build Verification

Use the existing local Gradle/JDK setup.

Known working compile command pattern:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk21\jdk-21.0.11+10'
$env:ANDROID_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\android-sdk'
$env:ANDROID_SDK_ROOT='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\android-sdk'
$env:GRADLE_USER_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\gradle-home'
$env:GRADLE_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Dkotlin.compiler.execution.strategy=in-process'
& 'C:\Users\USER\Downloads\Komikku\gradle-dist\gradle-9.3.1\bin\gradle.bat' --no-daemon :app:compileDebugKotlin
```

The Kotlin daemon may throw an `AccessDeniedException` under `AppData` in the sandbox. The in-process strategy above has compiled successfully before.

## Recommended Implementation Order

1. Add source-url SQLDelight queries.
2. Extend repository interface and implementation.
3. Extend `GetMangaTaste` and `ClearMangaTaste` interactors.
4. Update `MangaScreenModel` to seed and subscribe by `source + url`.
5. Update clear rating to delete by `source + url`.
6. Make `upsert` robust for source-url conflicts.
7. Update `For You` filtering to use source-url taste keys.
8. Add rated manga visibility setting.
9. Wire setting into `For You` filtering.
10. Add strings.
11. Add focused tests where practical.
12. Run compile and focused tests.
13. Document follow-up: editable Browse tabs are future work.

## Risks And Mitigations

### Risk: SQL Migration Compatibility

If table constraints need changing, existing installed databases need a new migration.

Mitigation:

- Prefer adding queries without schema change.
- If upsert conflict handling requires schema adjustment, add a new migration instead of editing already-shipped migration behavior.

### Risk: Duplicate Taste Rows

If old installs somehow have inconsistent taste rows, source-url lookup may find unexpected data.

Mitigation:

- The unique index on `(source, url)` should prevent duplicates.
- Upsert should converge rows by source-url.

### Risk: For You Behavior Surprise

Changing the default from hiding all rated manga to showing liked/loved manga may surprise the user.

Mitigation:

- Add a setting.
- Keep current default unless the user approves a new default.

### Risk: Too Much Scope

Editable Browse tabs are tempting but separate.

Mitigation:

- Document only.
- Do not implement tab customization in this pass.

## Completion Criteria

The plan is complete when:

- A manga rating survives leaving and reopening the manga screen.
- The same rating appears when the manga is opened from different navigation paths.
- Clearing a rating survives leaving and reopening.
- `For You` filtering uses source-url identity, not only local manga ID.
- A user setting controls whether rated manga are hidden or allowed in `For You`.
- Compile succeeds.
- The implementation notes document that editable Browse tabs are future work.
