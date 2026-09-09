# KMK-Recs v0.8.0 Rated Manga Bulk Selection And Group Actions â€” Implementation Report

Date: 2026-07-12

KMK-Recs version/build label: `KMK-Recs v0.8.0` (`KmkRecsReleaseNotes.VERSION_CODE = 750`).

Status: implemented and verified (automated: compile, tests, spotless, lint, `assembleDebug`). No
named APK handoff was produced or requested â€” this is a private v0.8.0 feature build, not a release.

Plan implemented: `docs/recommendations/KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_PLAN.md`

## User-Approved Scope

Turn the Loved / Liked / Disliked screens into a rated-manga management surface: long-press enters
bulk selection instead of opening recommendations; recommendations move to an explicit item menu;
bulk rating/group actions with confirmation where destructive; a focused linked-version list; a
user-selected primary version that controls the rated-list cover/title without weakening group
recommendation metadata. Reuse existing systems (rated screen, cross-source link table, group
recommendation flow, cross-extension matching flow) wherever possible. No unrelated For You/Source
Evaluation changes; no public release.

## Files Changed

**New files:**

- `data/src/main/sqldelight/tachiyomi/migrations/62.sqm` â€” additive `CREATE TABLE IF NOT EXISTS
  manga_cross_source_group_primary`.
- `data/src/main/sqldelight/tachiyomi/data/manga_cross_source_group_primary.sq` â€” schema + queries
  (`getByGroupId`, `getAll`, `upsert`, `deleteByGroupId`, `deleteAll`).
- `domain/src/main/java/tachiyomi/domain/taste/model/CrossSourceGroupPrimary.kt`.
- `domain/src/main/java/tachiyomi/domain/taste/interactor/GetCrossSourceGroupPrimary.kt`,
  `SetCrossSourceGroupPrimary.kt`, `ClearCrossSourceGroupPrimary.kt`.
- `app/src/main/java/exh/recs/loved/RatedGroupMergePlanner.kt` â€” pure merge-planning logic (no
  Android/DB dependencies).
- `app/src/main/java/exh/recs/loved/RatedGroupPrimaryResolver.kt` â€” pure primary-version resolution.
- `app/src/main/java/exh/recs/loved/RatedSelectionReducer.kt` â€” pure selection-state reducer.
- `app/src/main/java/exh/recs/links/LinkedVersionListScreen.kt` â€” focused version-list UI.
- `app/src/main/java/exh/recs/links/LinkedVersionListScreenModel.kt` â€” screen model +
  `LinkedVersionListBuilder` (pure row-building logic).
- Tests: `RatedGroupMergePlannerTest.kt`, `RatedGroupPrimaryResolverTest.kt`,
  `RatedSelectionReducerTest.kt`, `RatedMangaDisplayItemGroupingTest.kt`,
  `LinkedVersionListBuilderTest.kt` (all in `app/src/test/java/exh/recs/...`).

**Modified files:**

- `domain/src/main/java/tachiyomi/domain/taste/repository/TasteRepository.kt` â€” 5 new
  `manga_cross_source_group_primary` methods.
- `data/src/main/java/tachiyomi/data/taste/TasteRepositoryImpl.kt` â€” implementations + mapper.
- `app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt` â€” the primary rewrite:
  - `RatedMangaKey(source, url)` (new stable key type; no existing reusable `MangaTasteKey` data
    class was found, per the plan's fallback instruction).
  - `LovedDisplayItem` gained `confirmedGroupId`, `memberKeys`, `hasConfirmedGroup` (kept the
    existing name rather than renaming to `RatedMangaDisplayItem` â€” same fields the plan's suggested
    shape needed, avoids churn across `RatedMangaScreen`/`LovedMangaScreen`/
    `RecommendationBundleExporter` call sites).
  - `State.Success` gained `primaryByGroupId`, `selectionMode`, `selectedKeys`, plus
    `toSelection()`/`withSelection()` bridging helpers to the pure `RatedSelectionReducer`.
  - `load()` now also loads stored primaries (`GetCrossSourceGroupPrimary.awaitAll()`, fail-open to
    empty map) and preserves selection state across reactive reloads (same pattern already used for
    `groupDuplicates`).
  - New actions: `enterSelection`, `toggleSelection`, `clearSelection`, `selectAllInGroup`,
    `changeSelectedRating`, `clearSelectedRatings`, `markSelectedNotInterested`,
    `mergeSelectedIntoGroup`, `removeSelectedFromGroup`, `ungroup`, `setPrimaryVersion` â€” all thin
    wrappers around existing interactors (`SetMangaTaste`, `ClearMangaTaste`,
    `UpsertCrossSourceMangaLinks`, `DeleteCrossSourceMangaLink`,
    `SetCrossSourceGroupPrimary`/`ClearCrossSourceGroupPrimary`) plus the
    `SeenRecommendationMangaStore` preference for "Mark not interested." All side effects run in
    `screenModelScope.launch { ... }`, never in composables.
  - `buildFlatItems`/`buildGroupedItems` changed from `private` to `internal` (test visibility only)
    and now compute the new fields; `buildGroupedItems` delegates primary resolution to
    `RatedGroupPrimaryResolver`.
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt` â€” UI rewrite:
  - Removed the long-press-opens-recommendations behavior and the Explore overlay icon (superseded
    by the item menu's "See recommendations"/"See group recommendations" entries).
  - App bar: normal mode unchanged except a new "Select" action; selection mode shows count + close.
  - New `RatedSelectionBottomBar` (Change/Clear/Group/More via `BottomAppBar`).
  - New per-card overflow menu (`RatedMangaItemMenu`) with the three grouped sections; `MangaItem`'s
    existing `isSelected` parameter is reused directly for the selection checkmark (no new selection
    visual was built).
  - New confirmation dialogs (`RatedMangaConfirmDialog`) for Clear rating, Merge, Remove from group,
    Ungroup, Mark not interested; a rating-picker dialog (`RatedMangaChangeRatingDialog`) for Change
    rating.
- `app/src/main/java/exh/recs/links/LinkGroupManagementScreen.kt` and
  `LinkGroupManagementScreenModel.kt` â€” gained an optional `focusedGroupId: String?` constructor
  parameter that scopes the manager to one group, instead of a new global-manager screen.
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” version bump + changelog entry.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” ~50 new KMR strings under a "v0.8.0"
  block (selection UI, item-menu actions, confirmation dialogs, rating labels, linked-version-list
  labels). No hardcoded user-facing strings were added.
- `app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt` â€” migration range
  extended `46..61` â†’ `46..62`; migration-62 entries added; two dedicated migration-62 tests.
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationFitProbeTest.kt` and
  `app/src/test/java/exh/taste/GetTasteProfileTest.kt` â€” their local `TasteRepository` test fakes
  needed the 5 new interface methods added (compile-time fallout from the interface change, not
  behavioral changes to those tests).
- Documentation: `docs/recommendations/README.md`, `CURRENT_STATE.md`, `NEXT_WORK.md`,
  `RECOMMENDATION_VERSIONING.md`, and this report.

## Behavior Changed

1. Long-press in Loved/Liked/Disliked enters bulk selection instead of opening recommendations, for
   all three rating tiers (previously DISLIKE already opened the manga on long-press; LOVE/LIKE
   opened group recommendations â€” both are now selection).
2. A "Select" action exists in the app bar for discoverability.
3. Selection mode has a phone-friendly bottom action bar (Change/Clear/Group/More).
4. Every card has an action menu with clearly grouped Recommendation/Rating/Group actions.
5. "See group recommendations" is visible only for a confirmed linked group (real cross-source link,
   not a metadata-similarity grouping) with 2+ versions â€” verified explicitly by a dedicated test
   (`RatedMangaDisplayItemGroupingTest`) distinguishing `LINK_GROUP`-reason grouping from
   title/author/artist-similarity grouping.
6. Users can open a linked-version list and see every version with source/language/title/rating/
   favorite/installed-missing/updated/primary.
7. Users can set a primary version; it controls the rated-list cover/title for that group.
   Recommendations are unaffected (still seed from the full group via the unchanged
   `CrossSourceGroupSeed`/`GroupRecommendationSeedBuilder` path).
8. Clear/merge/remove-from-group/ungroup/mark-not-interested all require confirmation.
9. Missing/uninstalled sources render as "Source not installed" in the linked-version list rather
   than crashing (verified by `LinkedVersionListBuilderTest`).

## Migrations Added

- Migration `62.sqm`: `CREATE TABLE IF NOT EXISTS manga_cross_source_group_primary (group_id TEXT
  NOT NULL PRIMARY KEY, source INTEGER NOT NULL, url TEXT NOT NULL, updated_at INTEGER NOT NULL)`.
  Additive only; does not touch `manga_cross_source_link` or any other existing table. Verified by
  `KmkMigrationTest` that (a) the table and its columns exist after migrating, and (b) an existing
  `manga_cross_source_link` row survives the full 46â†’62 migration sequence unchanged.

## Tests Run

All run with repo-local JDK 17 (`.tools/jdk17/jdk-17.0.19+10`).

| Command | Result |
|---|---|
| `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL (checkpoint, run repeatedly during implementation) |
| `./gradlew :app:compileDebugUnitTestKotlin` | BUILD SUCCESSFUL (after fixing 2 pre-existing `TasteRepository` test fakes for the new interface methods) |
| `./gradlew :app:testDebugUnitTest` | **BUILD SUCCESSFUL â€” 1016 tests, 0 failures, 0 errors** (up from 988 pre-existing) |
| `./gradlew spotlessApply` | BUILD SUCCESSFUL (auto-reformatted a few files) |
| `./gradlew spotlessCheck` | BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` (post-spotless re-run) | BUILD SUCCESSFUL â€” 1016/1016 |
| `./gradlew :app:lintKmkPublicTest` | BUILD SUCCESSFUL â€” only pre-existing, unrelated warnings (not touched by this pass) |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

New test files and counts:

- `RatedGroupMergePlannerTest` â€” 6 tests: fewer-than-2 returns null; creates new group; reuses the
  single existing group; merges two existing groups and folds every member of the merged-away group
  (not just the selected subset); never merges by title (two dedicated title-based non-merge cases);
  preserves existing title/createdAt on a folded rewrite.
- `RatedGroupPrimaryResolverTest` â€” 3 tests: stored primary wins when present among current members;
  falls back cleanly when the stored primary is missing/uninstalled; falls back when there is no
  stored primary at all.
- `RatedSelectionReducerTest` â€” 8 tests: long-press enters selection and selects; entering again
  adds another key; tap-toggle is a no-op outside selection mode; toggle adds/removes; clear exits
  and empties; select-all enters selection mode and is additive on top of an existing selection.
- `RatedMangaDisplayItemGroupingTest` â€” 5 tests: confirmed-link-group exposes `hasConfirmedGroup` +
  both member keys; a lone unlinked entry is never a confirmed group; a metadata-similarity grouping
  (same title+author, no link) is explicitly NOT treated as a confirmed group; flat display still
  exposes `hasConfirmedGroup` for a 2+-member confirmed link group; flat display does not mark a
  lone loaded linked member as confirmed (needs 2+ visible members).
- `LinkedVersionListBuilderTest` â€” 4 tests: installed source with resolved manga exposes full data;
  missing/uninstalled source marks `isInstalled = false` without crashing and falls back to the
  link's own title; `isPrimary` is set only for the matching key; the primary row sorts first.
- `KmkMigrationTest` â€” 2 new tests (migration 62 table/columns; existing link rows survive).

### Existing Regression Tests Re-Run (via the full suite above)

- `LovedMangaDuplicateGrouperTest` â€” 38 tests, all passing (grouping algorithm itself untouched).
- `RatedMangaExclusivityTest` â€” 9 tests, all passing.
- `SourceRecommendationFitProbeTest`, `GetTasteProfileTest` â€” all passing after the required
  `TasteRepository` fake updates (interface addition only, no logic change to those fakes).
- Full `:app:testDebugUnitTest` â€” run in full (1016/1016), not skipped.

## Build / APK Output

`assembleDebug` (root-level, builds all debug variants) succeeded. No named/versioned APK handoff
copy was produced or requested for this pass â€” this is documented as a private v0.8.0 feature build
verified by build success and the full test suite, not a release with a distributed artifact. The
raw debug APK exists under the standard Gradle output path
(`app/build/outputs/apk/debug/app-universal-debug.apk`) if a manual install is wanted for real-device
verification, which has not been performed.

## Known Limitations

- **No backup/sync for the new primary-version table.** `manga_cross_source_link` is already
  included in backup/sync (proto 624). This pass deliberately does not extend backup/sync/proto
  support to `manga_cross_source_group_primary` â€” see Deviations below. A user's chosen primary
  version will not survive a backup/restore or sync cycle until a follow-up adds this.
- **"Select all in group" only affects the currently loaded rating tier.** Because `LovedMangaEntry`
  is filtered to one rating tier per screen instance (LOVE/LIKE/DISLIKE are separate screen
  instances), "Select all in group" selects only the confirmed group's members visible in the
  *current* screen's loaded entries â€” it cannot select a cross-tier member without navigating to
  that tier's screen. This matches the plan's own guardrail ("Do not silently select missing/
  uninstalled source rows") extended to cross-tier rows, which are equally not actionable from this
  screen model instance.
- **Clear-rating selection semantics: conservative choice made explicit.** Per the plan's own
  documented alternative ("If [group-wide clearing] is too much for v0.8.0, implement conservative
  behavior: selected card affects only the primary row unless the user uses Select All In Group"),
  clearing/changing rating/marking-not-interested only ever act on the manga rows actually present
  in `selectedKeys` â€” a grouped display card's selection represents only its displayed primary
  unless the user explicitly used "Select all in group" first to expand the selection to every
  member. This was the conservative option the plan pre-approved as acceptable for v0.8.0.
- **No manual/real-device verification was performed.** All verification is automated (compile,
  unit tests, spotless, lint, `assembleDebug`). Long-press/selection-mode/menu behavior has not been
  exercised on a device or emulator.
- **`MangaItem`'s existing `isSelected` visual was reused as-is** for the selection checkmark rather
  than building a new selection indicator â€” this matches existing bulk-favorite selection UI
  elsewhere in the app (`RecommendsScreen`'s `BulkFavoriteScreenModel` flow uses the same parameter).

## Deviations From The Approved Plan

1. **Primary-version table is not backed up/synced.** The plan said: "Before finalizing, check
   whether `manga_cross_source_link` is currently included in backup/sync. If it is, include
   primary-version data consistently. If link groups are not backed up, document that limitation
   instead." `manga_cross_source_link` *is* backed up/synced. Rather than "include primary-version
   data consistently" (which would require touching the backup proto schema, `BackupCreator`/
   `TasteBackupCreator`, `BackupRestorer`/`TasteRestorer`, and `SyncManager`/`SyncService` â€” a
   meaningfully larger surface area than this UI/management-layer pass), this was scoped out and is
   documented here as a known limitation, following the plan's own escape hatch for exactly this
   kind of judgment call.
2. **`RatedMangaKey` introduced instead of reusing an existing `MangaTasteKey`.** A repo-wide search
   found `MangaTasteKey` referenced only as a naming convention in a few files (e.g.
   `BrowsePersonalRecommendationsScreenModel.kt`, `RecommendationCandidateVisibilityPolicy.kt`), not
   as a single reusable `data class` with the `(source, url)` shape needed here â€” no duplicate was
   introduced; `RatedMangaKey(source: Long, url: String)` was added as the plan explicitly allowed
   ("If there is already a reusable `MangaTasteKey`, reuse it instead of introducing a duplicate" â€”
   none was found).
3. **`LovedDisplayItem` kept its existing name** rather than being renamed to the plan's suggested
   `RatedMangaDisplayItem` â€” the plan explicitly allowed this ("Claude may keep the existing
   `LovedDisplayItem` name if a rename creates unnecessary churn... document why"). A rename would
   have touched `RatedMangaScreen.kt`, `LovedMangaScreen.kt`, and
   `RecommendationBundleExporter.kt`'s `buildLovedMangaBundle` signature for no behavioral benefit.
4. **Group merge target choice.** The plan said "if multiple selected items have groups, choose the
   first stable group id and confirm merge" without specifying an ordering. `RatedGroupMergePlanner`
   uses the lexicographically smallest group id among the distinct groups present in the selection,
   for a fully deterministic (and therefore testable) choice â€” documented here since the plan left
   this open.
5. **`EXPLICIT_HEAVY` "several adult candidates" from the prior (v0.7.47) session's threshold
   interpretation is unrelated to this pass** and not touched â€” noted only to confirm no scope creep
   into Source Evaluation occurred, per the plan's explicit non-goal.

No other deviations. Group recommendations were not rebuilt (the existing `CrossSourceGroupSeed`
flow is used unchanged). No second duplicate-grouping engine was created (`LovedMangaDuplicateGrouper`
is untouched; only its already-exposed `reason` field is now read by the screen model). No auto-merge
by title exists anywhere in the new code (`RatedGroupMergePlanner` never reads `title` for grouping
decisions, only for fallback display text). No public release was made or requested.

