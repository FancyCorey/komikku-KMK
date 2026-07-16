# KMK-Recs v0.7.5 â€” Recommendation JSON Export / Import Implementation

Date: 2026-06-22

Status: implemented. All tests pass. APK built.

APK: `Komikku-v1.13.6-kmk.7.5-debug.apk` (VERSION_CODE 750)

---

## What Was Built

A recommendation-sharing system that exports recommended manga as a JSON bundle and imports that JSON with a safe preview and add flow.

---

## New Files

### `exh/recs/share/RecommendationBundle.kt`

Pure data models for the JSON schema. No Android dependencies.

- `RecommendationBundleType`: `TOP_PICKS`, `SOURCE_ROW`, `LOVED_MANGA`
- `RecommendationBundleSource`: sourceId, sourceName, sourceLang, extensionPkgName, extensionName, extensionSignatureHash, repoName
- `RecommendationBundleItem`: title, url, sourceId + source metadata, thumbnailUrl, author, artist, description, genres, status, score, matchedGroups, recommendationReason, crossSourceGroupId
- `RecommendationBundle`: schema (`kmk.recommendation.bundle`), schemaVersion (`1`), kmkRecsVersion, appVersionName, createdAt, title, description, bundleType, requiredSources, items

### `exh/recs/share/RecommendationBundleValidator.kt`

Pure validator. No Android dependencies. Safe for unit tests.

- `MAX_FILE_SIZE_BYTES = 2 MB`, `MAX_ITEMS = 500`, `MAX_SOURCES = 200`
- `ValidationResult` sealed interface: `Valid(bundle)`, `WrongSchema(found)`, `UnsupportedVersion(found)`, `TooManyItems`, `TooManySources`, `FileTooLarge`, `MalformedJson(message)`
- `validate(jsonString, fileSizeBytes)`: checks size, parses JSON (lenient), checks schema ID, version, item count, source count

### `exh/recs/share/RecommendationBundleSourceResolver.kt`

Pure source resolution and duplicate detection. No Android dependencies.

3-step resolution:
1. Exact `sourceId` match â†’ `FoundExact`
2. `extensionPkgName + sourceName + sourceLang` â†’ `FoundByMetadata`
3. `extensionSignatureHash + sourceName + sourceLang` â†’ `FoundByMetadata`
If nothing matches â†’ `Missing`

`isDuplicateByMetadata()`: title (normalized) + author OR title (normalized) + artist required. Title alone is never enough.

`normalizeTitle()`: lowercase, strip `(...)` and `[...]`, replace non-alphanumeric with spaces, trim, collapse spaces.

### `exh/recs/share/RecommendationBundleExporter.kt`

Builds bundles from in-memory data; writes JSON to a URI.

- `buildTopPicksBundle(recs: List<PersonalRecommendation>, kmkVersion)` â€” from `combinedDetailResult`, with scores and matched groups
- `buildSourceRowBundle(sourceName, sourceLang, recs, kmkVersion)` â€” per source row, with scores
- `buildLovedMangaBundle(displayItems, linkGroupByKey, kmkVersion)` â€” skips items where `manga == null`
- `buildTopPicksFromMangaBundle(mangas: List<Manga>, kmkVersion)` â€” for `TopPicksScreen` (no scores, no groups; IDs only)
- `writeToUri(context, uri, bundle): Result<Unit>` â€” suspending; writes UTF-8 JSON

Uses Injekt `extensionManager` to look up installed extension per sourceId.

Compile fix applied: `manga.status` (Long) cast via `.takeIf { it != 0L }?.toInt()` before assigning to `RecommendationBundleItem.status: Int?`.

### `exh/recs/share/RecommendationBundleImporter.kt`

Reads bytes from a URI, decodes UTF-8, calls validator.

- `ImportReadResult(validation: ValidationResult, fileSizeBytes: Int)`
- `readFromUri(context, uri): ImportReadResult`

### `exh/recs/share/RecommendationBundleLibraryAdder.kt`

Injekt DI singleton for adding manga to the library.

- `Outcome` sealed: `Added`, `AlreadyFavorite`, `Duplicate(duplicates: List<Manga>)`, `Error(message)`
- `addToLibrary(manga, skipDuplicates=false, categoryIds=emptyList()): AddResult`
  - Checks favorite â†’ checks duplicates (via `GetDuplicateLibraryManga`) â†’ `setMangaDefaultChapterFlags.await()` â†’ `updateManga.awaitUpdateFavorite(id, true)` â†’ resolves categories â†’ `setMangaCategories.await()`
- `addMultiple(mangas, skipDuplicates, categoryIds): List<AddResult>`
- `resolveDefaultCategoryIds()` â€” reads `libraryPreferences.defaultCategory()`

### `exh/recs/share/RecommendationBundleImportScreenModel.kt`

Screen model for the import preview screen.

- Takes `uriString: String` + `context: Context` in constructor (Voyager safety â€” primitive only)
- `RecommendationImportItemState` sealed: `ReadyToAdd(localManga?)`, `AlreadyInLibrary(localManga)`, `MissingSource(availableExt?)`, `SourceInstalledNeedsResolve(resolvedSourceId)`, `NeedsManualMatch`, `Unsupported`, `Error(message)`
- `State` sealed: `Loading`, `LoadError(message)`, `Preview(bundle, items, selectedIndices, isAdding, addSummary, installingPkgName)`
- Default selection: all `ReadyToAdd` + `SourceInstalledNeedsResolve` items
- `addSelected()`: builds `SManga` from bundle item for `SourceInstalledNeedsResolve` items; calls `libraryAdder.addToLibrary(skipDuplicates=true)`
- `installMissingExtension(ext)`: `extensionManager.installExtension(ext).collectLatest { step -> if (step == InstallStep.Installed) re-resolve }`
- Local Source (id=0L) â†’ always `Unsupported`

### `exh/recs/share/RecommendationBundleImportScreen.kt`

Voyager screen. Constructor: `RecommendationBundleImportScreen(uriString: String) : Screen()` â€” primitive only, Voyager-safe.

States:
- `Loading` â†’ spinner + loading text
- `LoadError` â†’ error message
- `Preview` â†’ `BundleInfoCard` + per-missing-extension `InstallExtensionCard` + `itemsIndexed` with `ImportItemRow`

Bottom bar: Select All / Deselect All + "Add selected (N)" button.

`AddSummary` AlertDialog using `tachiyomi.i18n.MR.strings.action_ok` for the dismiss button.

`SuggestionChip` for state badges.

Compile fix applied: added `import eu.kanade.tachiyomi.extension.model.Extension`.

---

## Modified Files

### `exh/recs/BrowsePersonalRecommendationsTab.kt`

- Added `ActivityResultContracts.CreateDocument("application/json")` export launcher
- `pendingExportSource: CatalogueSource?` and `pendingExportIsTopPicks: Boolean` state tracks which export is in flight
- "Export Top Picks" action button added to tab actions list
- `onLongClickSource: ((CatalogueSource) -> Unit)?` parameter added to `PersonalRecommendationsContent`
- Per-source `GlobalSearchResultItem` passes long-click â†’ `onLongClickSource`
- Launcher callback uses `screenModel.state.value` snapshot (not captured `state`) to avoid stale data

### `exh/recs/TopPicksScreen.kt`

- Export launcher for Top Picks JSON
- `AppBarActions(persistentListOf(AppBar.Action(...)))` share button in app bar
- Uses `RecommendationBundleExporter().buildTopPicksFromMangaBundle(state.mangas, ...)`

### `exh/recs/loved/LovedMangaScreen.kt`

- Export launcher for Loved Manga JSON
- `AppBarActions(persistentListOf(AppBar.Action(...)))` share button in app bar
- Uses `screenModel.state.value as? LovedMangaScreenModel.State.Success` snapshot in launcher callback

### `eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt`

In `getExportGroup()`:
- Added `importBundleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` for JSON files
- Added `TextPreference` for `KMR.strings.rec_bundle_import_settings_title`, onClick launches file picker
- Navigator push: `exh.recs.share.RecommendationBundleImportScreen(uri.toString())`

### `exh/recs/KmkRecsReleaseNotes.kt`

- `VERSION_CODE = 750`
- `VERSION_NAME = "KMK-Recs v0.7.5"`
- v0.7.5 What's New block added at top of MARKDOWN

### `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

22 new strings: `rec_bundle_import_title`, `rec_bundle_import_loading`, `rec_bundle_import_bundle_info`, `rec_bundle_import_state_ready`, `rec_bundle_import_state_in_library`, `rec_bundle_import_state_missing_source`, `rec_bundle_import_state_resolving`, `rec_bundle_import_state_needs_match`, `rec_bundle_import_state_unsupported`, `rec_bundle_import_state_error`, `rec_bundle_import_add_selected`, `rec_bundle_import_complete_title`, `rec_bundle_import_complete_body`, `rec_bundle_missing_source_prompt`, `rec_bundle_install_extension`, `rec_bundle_export_top_picks`, `rec_bundle_export_source_row`, `rec_bundle_export_loved_manga`, `rec_bundle_export_success`, `rec_bundle_export_failure`, `rec_bundle_export_empty`, `rec_bundle_import_settings_title`, `action_select_all`, `action_deselect_all`.

---

## Tests

| Test file | Tests | Result |
|-----------|-------|--------|
| `RecommendationBundleSerializationTest.kt` | 8 | PASSED |
| `RecommendationBundleValidatorTest.kt` | 11 | PASSED |
| `RecommendationBundleSourceResolverTest.kt` | 11 | PASSED |
| `RecommendationBundleDuplicatePolicyTest.kt` | 9 | PASSED |
| `:app:testDebugUnitTest --tests "*RecommendationBundle*"` | â€” | BUILD SUCCESSFUL |
| `:app:assembleDebug` | â€” | BUILD SUCCESSFUL |

---

## Decisions Made

- **No score extraction from `TopPicksScreen`**: TopPicksScreen only has `ArrayList<Long>` manga IDs. `buildTopPicksFromMangaBundle()` was added to the exporter for this reduced-metadata path. For You tab exports from `combinedDetailResult` which has full `PersonalRecommendation` data including scores.

- **`BulkFavoriteScreenModel` not extracted**: Adding to library via `RecommendationBundleLibraryAdder` uses the same Injekt-injected interactors directly. Too invasive to share the screen model.

- **Voyager primitive-only constructor**: `RecommendationBundleImportScreen(uriString: String)` â€” only the URI string is passed. Bundle is loaded in `init` of the screen model. Never pass large objects through Voyager constructors.

- **`MissingSource` per-extension InstallExtensionCard**: One install card per unique missing extension (grouped from items). The card shows above the item list and triggers the install flow for that extension.

- **`skipDuplicates = true` in `addSelected()`**: Import always skips duplicates to avoid surprises. The add summary shows how many were skipped as "Already in library".

