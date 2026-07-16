# KMK-Recs v0.7.5 Recommendation JSON Export / Import Plan

Date: 2026-06-22

Status: implementation plan. Do not implement until the user explicitly approves and requests a Claude Code prompt.

## Purpose

Add a way to export recommendation manga as a portable JSON file and import that JSON on another Komikku/KMK install.

This is **not** a backup feature and should not try to export extension APKs. It is a recommendation-sharing feature:

- export a visible set of recommended manga,
- send/share/save the JSON,
- import it on another device,
- preview what can be added,
- install missing extensions or resolve/migrate to equivalent sources when needed,
- add selected manga to the library using Komikku's existing favorite/library behavior.

## Current Baseline

Current documented baseline:

```text
KMK-Recs v0.7.4
```

Relevant implemented systems:

- Browse > For You personal recommendation rows.
- Top Picks row/detail from already-fetched For You results.
- Loved Manga view, installed-source-only filtering, and conservative duplicate grouping.
- Cross-source link groups in `manga_cross_source_link`, including backup/restore/sync.
- Cross-extension matching with alternate-title query planning.
- Bulk Favorite behavior for adding multiple manga to the library.
- Library CSV export in Settings > Data storage.
- Sources To Try and source evaluation for extension/source discovery.

This plan should become:

```text
KMK-Recs v0.7.5
```

Reasoning: this is a user-facing manga/recommendation sharing surface, so it belongs in the current `0.7.x` feature line.

## Required Reading Before Coding

Claude must read these before implementation:

```text
docs/recommendations/README.md
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/DOCUMENTATION_RULES.md
docs/recommendations/KMK_RECS_V0_7_5_RECOMMENDATION_JSON_EXPORT_IMPORT_PLAN.md
RECOMMENDATION_VERSIONING.md
```

Claude must inspect these code paths before editing:

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt
app/src/main/java/exh/recs/TopPicksScreen.kt
app/src/main/java/exh/recs/loved/LovedMangaScreen.kt
app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt
app/src/main/java/exh/recs/loved/LovedMangaDuplicateGrouper.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchQueryPlanner.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/BulkFavoriteScreenModel.kt
app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt
app/src/main/java/eu/kanade/tachiyomi/data/export/LibraryExporter.kt
app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt
app/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt
app/src/main/java/mihon/feature/migration/list/search/SmartSourceSearchEngine.kt
app/src/main/java/mihon/feature/migration/list/search/SourceMatchScorer.kt
```

## Product Requirements

### Export

The user should be able to export recommendation sets as JSON from recommendation-adjacent places:

1. **Top Picks**
   - Export the current Top Picks set.
   - Inline Top Picks has up to 20 items.
   - Top Picks detail has up to 50 items.
   - The export should use the already-fetched results, not trigger a new crawl.

2. **For You source row**
   - Export recommendations from a specific source row.
   - Preserve source context and recommendation reasons where available.

3. **Loved Manga**
   - Export visible Loved Manga entries.
   - Respect the current rule that Loved Manga only shows currently installed sources.
   - If duplicate grouping is enabled, export the displayed representatives by default.
   - If future UX allows selection, export selected entries only.

4. **Manual selection**
   - Nice to have, but not required in the first pass.
   - If implemented, use existing selection patterns and keep the JSON schema the same.

### Import

Import must be preview-first. The app must not silently add everything.

The import flow should:

1. Let the user pick a `.json` file.
2. Validate the schema and size.
3. Show a preview grouped by resolution status:
   - ready to add,
   - already in library,
   - source installed but manga not locally resolved yet,
   - missing extension/source,
   - needs manual match/migration,
   - unsupported/skipped.
4. Let the user select/deselect entries.
5. Add selected resolvable manga to the library.
6. For missing sources, prompt the user to install the extension or find an equivalent source.

### Missing Extension / Source Handling

The export must not include extension APK files.

If the importer does not have the required source/extension:

- show a clear "Missing extension/source" state,
- attempt to match the source against available extension repo metadata using safe identity fields,
- offer "Install extension" when the source/extension is available in the configured repos,
- after install, refresh the import preview and try resolving those entries again,
- if unavailable, offer "Find equivalent" using existing matching/global-search/migration behavior,
- never auto-install without confirmation.

If the same manga is available from a different installed source, the import flow should let the user confirm that equivalent entry instead of requiring the exact original extension.

## Non-Goals

Do not implement these in v0.7.5:

- exporting extension APKs,
- bundling repo credentials,
- exporting tracker/auth cookies/tokens,
- exporting chapters, read progress, downloads, or history,
- changing normal global search behavior,
- auto-installing extensions without user confirmation,
- auto-migrating manga without user confirmation,
- replacing Komikku backup/restore,
- syncing these bundles through cloud sync,
- fetching chapter lists or manga details in bulk just to enrich an export.

## JSON Schema

Create a stable versioned JSON schema. Suggested package:

```text
app/src/main/java/exh/recs/share/
```

Suggested files:

```text
RecommendationBundle.kt
RecommendationBundleExporter.kt
RecommendationBundleImporter.kt
RecommendationBundleValidator.kt
RecommendationBundleSourceResolver.kt
RecommendationBundleLibraryAdder.kt
```

Use `kotlinx.serialization`.

Suggested root object:

```kotlin
@Serializable
data class RecommendationBundle(
    val schema: String = "kmk.recommendation.bundle",
    val schemaVersion: Int = 1,
    val kmkRecsVersion: String,
    val appVersionName: String? = null,
    val createdAt: Long,
    val title: String,
    val description: String? = null,
    val bundleType: RecommendationBundleType,
    val exportSource: RecommendationBundleExportSource,
    val requiredSources: List<RecommendationBundleSource>,
    val items: List<RecommendationBundleItem>,
)
```

Suggested item object:

```kotlin
@Serializable
data class RecommendationBundleItem(
    val title: String,
    val url: String,
    val sourceId: Long,
    val sourceName: String? = null,
    val sourceLang: String? = null,
    val extensionPkgName: String? = null,
    val extensionName: String? = null,
    val extensionSignatureHash: String? = null,
    val repoName: String? = null,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: Int? = null,
    val score: Double? = null,
    val matchedGroups: List<String> = emptyList(),
    val recommendationReason: String? = null,
    val crossSourceGroupId: String? = null,
)
```

Important schema rules:

- `sourceId + url` is the strongest same-source identity.
- `extensionPkgName + sourceName + sourceLang` is useful for finding a missing source.
- `extensionSignatureHash + extensionPkgName` helps distinguish repo/build identity.
- `title + author/artist/description/genres` are fallback matching evidence only.
- `crossSourceGroupId` is optional. It should only be exported if known locally and should never be treated as globally authoritative unless all entries in that group are from the same imported bundle or local link data confirms it.

## Export Architecture

### For You / Top Picks Data

`BrowsePersonalRecommendationsScreenModel` already exposes:

- `PersonalRecommendation(manga, score, matchedGroups)`,
- per-source `items`,
- `combinedResult`,
- `combinedDetailResult`.

Export should build bundle items from these existing in-memory results. It must not start another recommendation search.

Potential issue:

- `TopPicksScreen` currently receives only `ArrayList<Long>` manga IDs.
- It loses score, matched tags, and source-row context.

Recommended approach:

1. In For You, export Top Picks directly from `combinedDetailResult` before navigating, where score and matched groups still exist.
2. For `TopPicksScreen`, either:
   - pass a lightweight serializable export context containing item metadata, or
   - export from resolved manga IDs with reduced metadata and no score/reason.
3. Prefer the first option if it can be done safely without `Parcelable`/`Serializable` state crashes. If not, keep the Top Picks screen export minimal and document the limitation.

### Loved Manga Export

`LovedMangaScreenModel` exposes:

- `LovedMangaEntry(taste, manga)`,
- `LovedDisplayItem(taste, manga, versionCount)`,
- duplicate grouping via `LovedMangaDuplicateGrouper`,
- installed-source filtering via `LovedMangaSourceFilter`.

Export should:

- use the visible display list,
- preserve `sourceId + url`,
- include title from resolved manga or taste fallback,
- include `crossSourceGroupId` when available through the existing link-group map,
- not export hidden uninstalled-source taste rows.

### Source / Extension Metadata

To help importers install or resolve missing sources, the exporter should attach source metadata where available.

Claude should inspect `SourceManager`, `ExtensionManager`, and `Extension.Installed` / `Extension.Available` models to determine exact available fields.

Do not guess fields. If a field is unavailable, omit it from JSON rather than fabricating it.

## Import Architecture

### New Import Surface

Add an import entry point in a location that makes sense:

1. Settings > Data storage is appropriate for file import/export.
2. Browse > For You overflow is appropriate for recommendation-specific import/export.

Recommended first pass:

- Add **Import recommendation bundle** under Settings > Data storage or Recommendation Settings.
- Add export actions to For You/Top Picks/Loved Manga.

This avoids crowding the For You tab while still keeping import discoverable.

### Import Preview Screen

Create a dedicated preview screen, for example:

```text
app/src/main/java/exh/recs/share/RecommendationBundleImportScreen.kt
app/src/main/java/exh/recs/share/RecommendationBundleImportScreenModel.kt
```

Preview states:

```kotlin
sealed interface RecommendationImportItemState {
    data object ReadyToAdd
    data object AlreadyInLibrary
    data object MissingSource
    data object SourceInstalledNeedsResolve
    data object NeedsManualMatch
    data object Unsupported
    data class Error(val message: String)
}
```

Screen requirements:

- show bundle title, created date, item count, and source count,
- show warnings for missing extensions/sources,
- selection checkboxes for importable items,
- "Install missing extensions" action when available,
- "Find equivalent" action for individual unresolved items,
- "Add selected to library" action,
- clear error states instead of app crashes.

### Resolving Imported Items

Resolution order:

1. **Exact local manga by `sourceId + url`**
   - If source is installed and manga exists locally, use it.

2. **Network-to-local manga creation**
   - If source is installed but manga is not local, create/resolve a local manga using the existing `NetworkToLocalManga` pattern where safe.

3. **Installed source identity fallback**
   - Match source by package/source/lang/name metadata if `sourceId` differs.
   - This matters because source IDs can differ between forks, repos, or extension changes.

4. **Cross-source link group**
   - If local `manga_cross_source_link` already maps the imported `(source,url)` or an equivalent known key, offer the linked local version first.
   - Do not silently apply if ambiguous.

5. **Smart search / migration fallback**
   - Use existing global-search or migration-style matching to find an equivalent installed-source entry.
   - User must confirm the selected equivalent.

6. **Missing extension**
   - If the extension/source is not installed, offer install if available in configured repos.
   - After installation, refresh resolution.

### Add To Library

Do not copy large blocks from `BulkFavoriteScreenModel`.

Recommended approach:

1. Extract the reusable add-to-library logic from `BulkFavoriteScreenModel` into a helper/interactor if practical.
2. Preserve:
   - duplicate detection,
   - default category behavior,
   - category picker when needed,
   - metadata/chapter fetch preferences,
   - cover cache behavior,
   - tracker binding behavior.
3. Use that helper from both Bulk Favorite and recommendation bundle import, or from import only if extraction would be too risky.

If extraction becomes too invasive, Claude should stop and document the blocker instead of duplicating fragile behavior.

## Duplicate And Identity Rules

Import must avoid aggressive duplicate assumptions.

Safe duplicate/equivalence signals:

1. exact `sourceId + url`,
2. exact local cross-source link group,
3. exact normalized title + same non-blank author,
4. exact normalized title + same non-blank artist,
5. exact normalized title + very similar long description,
6. user-confirmed manual match.

Unsafe signals:

- title alone,
- generic title similarity alone,
- same genre list alone,
- same source name alone.

If uncertain, show both and let the user choose.

## Privacy And Safety

Before export, show a short confirmation or warning:

- The JSON may reveal manga titles, tags, source names, and recommendation reasons.
- It does not include account tokens, cookies, tracking auth, read history, or downloads.

Validation limits:

- reject wrong `schema`,
- reject unsupported `schemaVersion` with a clear message,
- cap maximum file size,
- cap maximum item count,
- skip malformed items individually where possible,
- never crash on bad JSON.

Suggested first-pass limits:

```text
max file size: 2 MB
max items: 500
max sources: 200
```

Claude may tune these after inspecting existing app conventions.

## Relationship To Deferred Items

This plan should **not** attempt to implement the entire deferred roadmap. However, it should intentionally reuse or prepare for these already-discussed systems:

- **Cross-source link groups**: use them as the strongest equivalence signal during export/import.
- **Loved Manga grouping**: reuse the same conservative duplicate reasoning for display/import preview.
- **Source fit learning**: not part of this feature.
- **Bounded rec-fit probe**: not part of this feature.
- **Query-time blocked tags**: not part of this feature.
- **Local Source decision**: Local Source should be unsupported or skipped in v0.7.5 unless existing code proves it can be safely represented. Document the behavior.
- **Backup/restore for seen manga**: unrelated.
- **Manage hidden source suggestions**: unrelated.

## User Experience Details

### Export Actions

Use overflow/menu actions rather than cluttering every row with large buttons.

Suggested labels:

- `Export Top Picks`
- `Export this source row`
- `Export Loved Manga`

Export destinations:

- `Save JSON` through Android document picker.
- Optional `Share JSON` through Android share sheet if easy and consistent with existing utilities.

### Import Preview Labels

Use plain language:

- `Ready`
- `Already in library`
- `Missing source`
- `Install source`
- `Find equivalent`
- `Needs review`
- `Unsupported`

Do not use internal wording like `sourceId` in primary UI text. Technical details can appear in expandable diagnostics.

### Missing Source Prompt

If extension metadata maps to an available extension:

```text
This bundle uses MangaFire. Install this extension to import 12 manga from it?
```

If not available:

```text
This source is not installed and was not found in your extension repos. You can find an equivalent source manually.
```

## Tests

Add focused unit tests for pure helpers:

```text
app/src/test/java/exh/recs/share/RecommendationBundleSerializationTest.kt
app/src/test/java/exh/recs/share/RecommendationBundleValidatorTest.kt
app/src/test/java/exh/recs/share/RecommendationBundleSourceResolverTest.kt
app/src/test/java/exh/recs/share/RecommendationBundleDuplicatePolicyTest.kt
```

Required tests:

- JSON round-trip preserves schema, source metadata, manga metadata, score, and matched groups.
- Wrong schema is rejected safely.
- Unsupported schema version is rejected safely.
- Oversized item count is rejected.
- Malformed item is skipped or reported without aborting the whole preview where possible.
- Exact `sourceId + url` resolves as ready.
- Missing installed source is classified as missing source.
- Source metadata fallback can identify an installed source when source ID changed.
- Title-only match is not treated as confirmed duplicate.
- Title + author can be treated as strong duplicate evidence.
- Cross-source link group outranks fuzzy metadata.
- Loved Manga export excludes uninstalled-source entries.
- Top Picks export does not start a second crawl.

Manual verification:

1. Export Top Picks from For You.
2. Import the JSON on the same install; all items should be ready or already in library.
3. Import on an install missing one extension; affected items should show missing-source state.
4. Install the missing extension from the prompt, refresh, and confirm affected items become resolvable if the source exists.
5. Find equivalent for one unresolved item and add it to library.
6. Confirm normal global search behavior is unchanged.
7. Confirm Komikku backup/restore is unchanged.

## Documentation Updates Required After Implementation

Claude must create:

```text
docs/recommendations/KMK_RECS_V0_7_5_RECOMMENDATION_JSON_EXPORT_IMPORT_IMPLEMENTATION.md
```

Claude must update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
```

If user-facing changes ship, update:

```text
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

The What's New text must mention only user-facing behavior, not documentation or internal tests.

## Implementation Phases

This can be implemented in one coherent v0.7.5 pass if Claude can keep the scope tight. If it becomes too large, split as follows:

### Phase 1: Export Only

- JSON model.
- Validator.
- Export Top Picks.
- Export For You source row.
- Export Loved Manga.
- Save JSON.
- Serialization tests.

### Phase 2: Import Preview

- Pick JSON.
- Parse/validate.
- Preview screen.
- Resolve exact installed-source items.
- Missing-source classification.
- Resolver tests.

### Phase 3: Install / Resolve / Add To Library

- Install missing extension prompt.
- Refresh preview after install.
- Find equivalent using existing matching/search.
- Add selected to library via reusable favorite logic.
- Duplicate/category handling.
- Integration-ish focused tests where practical.

Recommended implementation if done in one pass:

1. Build pure JSON/validator/resolver helpers first.
2. Add export actions.
3. Add import preview.
4. Add add-to-library behavior.
5. Add missing-source install prompt.
6. Add docs/tests/release notes.

## Risk Assessment

### Low Risk

- JSON serialization.
- Exporting already-fetched recommendation data.
- Import preview classification.
- Save/share through Android document APIs.

### Medium Risk

- Passing rich Top Picks export context through Voyager without state-save crashes.
- Source identity fallback when source IDs differ.
- Missing-extension install from import flow.
- Reusing Bulk Favorite behavior without duplicating too much code.

### High Risk / Avoid In First Pass

- Exporting/importing extension APKs.
- Auto-migration without confirmation.
- Aggressive duplicate merging by title similarity.
- Bulk fetching manga details/chapters during import preview.
- Treating cross-device `crossSourceGroupId` as universally authoritative.

## Recommendation

This feature is feasible and useful, but it must be implemented as a recommendation bundle, not as a backup/export-everything system.

The most important requirements are:

1. export metadata and source identity, not extension APKs;
2. preview before importing;
3. prompt to install missing extensions or find equivalents;
4. reuse existing library-add behavior;
5. use conservative duplicate rules and user confirmation;
6. keep normal global search unchanged.

If these rules are followed, this should fit cleanly into Komikku/KMK without undermining existing backup, migration, or recommendation behavior.

