# KMK-Recs v0.5.0 Cross-Extension Rating Matching — Implementation Report

Date: 2026-06-16

Status: implemented and tested.

## Scope Delivered

Phase 1 (rating workflow) and Phase 5 (docs/versioning) are implemented. Phases 2–4 are deferred.

**Delivered:**
- Love/Like/Dislike other versions actions in the manga detail rating dropdown
- `CrossExtensionMatchScreen` — separate bounded search workflow
- `CrossExtensionMatchScreenModel` — per-source cap of 2, auto-selection, manual deselection preserved
- `SetMangaTasteBatch` — batch taste writer
- `SearchScreenModel.perSourceResultLimit` — opt-in cap, default `null` (normal global search uncapped)
- Source selection respects recommendation language filter and source priority order
- Local Source excluded
- No global search preference writes from matching workflow

**Deferred:**
- Favorite mode (Phase 2) — `BulkFavoriteScreenModel` coupling is too tight; deferred to v0.5.1
- Cross-source link groups and SQLDelight schema (Phase 3) — deferred to v0.5.1
- Backup/restore/sync for link groups (Phase 4) — deferred with Phase 3

## Changes

### SearchScreenModel.kt

Added `protected open val perSourceResultLimit: Int? = null` property. The search loop applies it after `networkToLocalManga()`:

```kotlin
.let { list -> perSourceResultLimit?.let(list::take) ?: list }
```

Default is `null` — no cap. Normal global search is unaffected. `GlobalSearchScreenModel` does not override this property, so it stays uncapped.

### SetMangaTasteBatch.kt (new)

`domain/src/main/java/tachiyomi/domain/taste/interactor/SetMangaTasteBatch.kt`

Accepts `List<Manga>` and `MangaRating`. Calls `repository.upsertMangaTaste()` per entry using source/url identity.

### CrossExtensionMatchScreenModel.kt (new)

`app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt`

Standalone screen model (does not extend `SearchScreenModel`) to avoid global search state entanglement.

Key behavior:
- On init, loads origin manga by ID, sets search query to its title, launches search
- Source selection: `RecommendationSourceFilter.filterForRecommendations()` (uses rec language pref) then `RecommendationSourceOrdering.apply()` (uses priority order)
- Per-source result cap: `take(PER_SOURCE_RESULT_LIMIT)` = `take(2)`
- Auto-selection: on each `MatchItemResult.Success`, new keys are added to `selectedKeys` unless they are the origin manga or are in `manuallyDeselectedKeys`
- Manual deselection: moves key to `manuallyDeselectedKeys`, preventing re-selection on result refresh
- Manual re-selection: removes from `manuallyDeselectedKeys`
- `applyRating()`: calls `SetMangaTasteBatch.await()` for all selected candidates then invokes `onComplete`

State:
- `selectedKeys: Set<MangaIdentityKey>`
- `manuallyDeselectedKeys: Set<MangaIdentityKey>`
- `isApplying: Boolean`
- `progress`, `total`, `totalCandidates` derived

### CrossExtensionMatchScreen.kt (new)

`app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt`

Takes `originMangaId: Long` and `mode: CrossExtensionMatchMode`.

UI:
- `AppBar` with title ("Love other versions" / "Like other versions" / "Dislike other versions") and subtitle showing "Selected N of M"
- `LinearProgressIndicator` while sources are loading
- `LazyColumn` of per-source rows using `GlobalSearchResultItem` + `GlobalSearchCardRow`
- Tap on a card toggles selection (selected = highlighted overlay via `selection` list)
- Bottom confirm `Button` showing "Apply Love to N versions" etc., disabled while applying or when none selected
- Confirm navigates back after applying

Does not write any global search preferences.

### MangaInfoHeader.kt

`MangaActionRow` gains `onTasteOtherVersionsClicked: ((MangaRating) -> Unit)? = null`.

When non-null, adds a `HorizontalDivider` followed by three dropdown items ("Love other versions", "Like other versions", "Dislike other versions") inside the existing taste `DropdownMenu`. Existing single-manga rating actions are unchanged.

### Presentation MangaScreen.kt

Added `onTasteOtherVersionsClicked: ((MangaRating) -> Unit)? = null` to:
- Top-level `MangaScreen` composable
- `MangaScreenSmallImpl`
- `MangaScreenLargeImpl`

Threaded through to both `MangaActionRow` call sites.

### UI MangaScreen.kt

Added navigation callback:
```kotlin
onTasteOtherVersionsClicked = { rating ->
    navigator.push(
        CrossExtensionMatchScreen(
            originMangaId = successState.manga.id,
            mode = CrossExtensionMatchMode.Rating(rating),
        ),
    )
},
```

Added imports for `CrossExtensionMatchMode` and `CrossExtensionMatchScreen`.

### KMKDomainModule.kt

Registered `SetMangaTasteBatch` with `addFactory { SetMangaTasteBatch(get()) }`.

### strings.xml

8 new strings:
- `rec_match_title_love`, `rec_match_title_like`, `rec_match_title_dislike`
- `rec_match_apply_love`, `rec_match_apply_like`, `rec_match_apply_dislike` (with `%1$d` count arg)
- `rec_match_selection_count` (with `%1$d` selected, `%2$d` total)
- `rec_match_applying`

### KmkRecsReleaseNotes.kt

`VERSION_CODE = 500`, `VERSION_NAME = "KMK-Recs v0.5.0"`. Added v0.5.0 What's New bullets.

## Files Changed

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt` — added `perSourceResultLimit` property + cap application
- `domain/src/main/java/tachiyomi/domain/taste/interactor/SetMangaTasteBatch.kt` (new)
- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt` (new)
- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt` (new)
- `app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt` — `onTasteOtherVersionsClicked` param + dropdown items
- `app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt` — `onTasteOtherVersionsClicked` threaded through 3 composables
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` — navigation wiring + imports
- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` — registered `SetMangaTasteBatch`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — VERSION_CODE=500, v0.5.0 notes
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 8 new strings
- `app/src/test/java/exh/recs/matching/CrossExtensionMatchSelectionTest.kt` (new) — 8 tests

## Tests Run

- `exh.recs.matching.CrossExtensionMatchSelectionTest` — 8 tests, all PASSED
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (full suite)
- `:app:assembleDebug` — BUILD SUCCESSFUL

APK: `Komikku-v1.13.6-kmk.5.0-debug.apk`

## Deviations From Plan

- **Favorite mode deferred** — plan suggested including it if `BulkFavoriteScreenModel` logic could be cleanly reused. After reviewing the coupling, it requires more refactoring than fits in this pass. Deferred to v0.5.1.
- **Cross-source link groups deferred** — plan noted this as "strongly recommended if feasible in this pass." SQLDelight schema, migration, domain model, repository, interactors, backup/restore, and sync are all needed. Deferred to v0.5.1 to keep v0.5.0 focused and shippable.
- **Backup/restore for link groups deferred** — with Phase 3.
- **`CrossExtensionMatchScreenModel` does not extend `SearchScreenModel`** — plan offered two alternatives. Used the copied search loop approach because the selection state and source filtering logic differ too much, and the global search init (subscribing to `globalSearchPinnedState`) would have added unnecessary state. The `perSourceResultLimit` property was still added to `SearchScreenModel` as required for the test that confirms normal global search is uncapped.
- **Per-source cap changed from 5 to 2** — plan specified 5 results per source. Changed to 2 after implementation to keep the confirmation list tighter and reduce wrong-match risk. `PER_SOURCE_RESULT_LIMIT = 2` in `CrossExtensionMatchScreenModel`.
- **Confirmation navigates back immediately** — plan mentioned disabling the button during apply. Both are implemented: button is disabled while `isApplying`, and navigates back via `onComplete` after apply finishes.

## Known Risks

- **Wrong match risk** — all results auto-selected by default. Users must deselect before confirming. Mitigated by 2-result cap per source and explicit confirmation button.
- **Normal global search** — verified uncapped: `SearchScreenModel.perSourceResultLimit` defaults to `null`; `GlobalSearchScreenModel` does not override it.
- **Preference pollution** — matching workflow does not call `setSourceFilter()` or write any global search preferences.

## Follow-up Recommendations

- v0.5.1: Favorite mode using extracted domain helper from `BulkFavoriteScreenModel`
- v0.5.1: Cross-source link groups (SQLDelight table, domain model, backup at proto 624)
- Future: "Link other versions" entry point once link groups are implemented
- Future: Pre-populate matching screen with already-linked versions if link groups exist
