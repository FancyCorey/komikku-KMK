# KMK-Recs v0.6.7 Implementation Report: Explicit Porn/Hentai Source Filter

Date: 2026-06-19

Status: implemented and shipped as `Komikku-v1.13.6-kmk.6.7-debug.apk`.

## Goal

Add a user-facing toggle that hides clearly explicit porn/hentai sources from Browse > Sources, Browse > Extensions (available only), and Sources To Try — separately from the existing broad "Show NSFW sources" setting. Ecchi-only sources must not be blocked by the new setting.

## Motivation

The existing `isNsfw` flag is broad and covers all adult content including ecchi. Users who want to hide only the most explicit content (full hentai/porn) but keep ecchi-tagged sources visible had no setting for this. The new toggle fills this gap conservatively.

## Architecture Decisions

### Conservative classifier, not `isNsfw`

`Extension.isNsfw == true` is NOT sufficient to classify a source as explicit. Many ecchi-only sources carry `isNsfw = true`. A dedicated `ExplicitSourceClassifier` object applies conservative rules:

- Match explicit keywords in extension name (`hentai`, `porn`, `pururin`, `tsumino`, `8muses`, `hbrowse`, `luscious`, `doujins`, `multporn`, `xxx`, `erotic`, `smut`, `adult comic`, `adult manga`, `adult manhwa`, `adult manhua`)
- Match `hentai` or `porn` in package name
- Match known explicit source IDs (`NHENTAI_SOURCE_ID`, `PURURIN_SOURCE_ID`, `TSUMINO_SOURCE_ID`, `EIGHTMUSES_SOURCE_ID`, `HBROWSE_SOURCE_ID`, all E-Hentai and ExHentai source IDs)

Does NOT trigger on: `ecchi`, `nsfw`, `mature`, `lewd`, `adult` (alone).

### Installed extensions stay visible

`GetExtensionsByType` filters only the `available` list, not `installed` or `untrusted`. Installed explicit extensions remain manageable and uninstallable. A user who already installed an explicit extension and then enables this setting can still update or uninstall it.

### Preference reads `.get()` once per call

`blockExplicitPornHentaiSources()` is read with `.get()` before the flow lambda in `GetExtensionsByType`, and inside the flow lambda in `GetNonInstalledSourceSuggestions` (same pattern as `nsfwEnabled`). No reactive preference changes are added — existing flows have no 6th combine source for this preference.

### Default `false`

The preference defaults to `false` (opt-in). Existing users are not surprised by hidden sources on update.

### No authentication

The setting blocks content (more conservative direction), not reveals it. Authentication is not needed.

## Files Changed

### New files

- `app/src/main/java/exh/source/ExplicitSourceClassifier.kt` — pure classifier object, no Android deps
- `app/src/test/java/exh/source/ExplicitSourceClassifierTest.kt` — JUnit 5 unit tests (20 tests)

### Modified files

**`app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`**
- Added `blockExplicitPornHentaiSources()` at end of inner KMK block, default `false`

**`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`**
- Added `pref_block_explicit_porn_hentai_sources` (title string)
- Added `pref_block_explicit_porn_hentai_sources_summary` (subtitle string)

**`app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsBrowseScreen.kt`**
- Added `SwitchPreference` for `blockExplicitPornHentaiSources()` inside NSFW content group, after `showNsfwSource()` and before `InfoPreference`

**`app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt`**
- Added `import exh.source.ExplicitSourceClassifier`
- Read `blockExplicit = preferences.blockExplicitPornHentaiSources().get()` once before combine
- Added `!(blockExplicit && ExplicitSourceClassifier.isExplicitExtension(extension))` to `available` filter only

**`app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`**
- Added `import exh.source.ExplicitSourceClassifier`
- Converted `getVisibleOnlineSources()` and `getVisibleCatalogueSources()` from expression-body to block-body functions
- Both now read `blockExplicit` once per call and filter using `ExplicitSourceClassifier.isExplicitCatalogueSource(it)`

**`app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt`**
- Added `blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()` inside flow lambda
- Passed `blockExplicit = blockExplicit` to `NonInstalledSourceSuggestionScorer.scoreAndFilter()`

**`app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt`**
- Added `import exh.source.ExplicitSourceClassifier`
- Added `blockExplicit: Boolean = false` param to `scoreAndFilter()`
- Added `if (blockExplicit && ExplicitSourceClassifier.isExplicitExtension(ext)) continue` right after the `nsfwEnabled` check

**`app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`**
- `VERSION_CODE = 607`, `VERSION_NAME = "KMK-Recs v0.6.7"`
- New MARKDOWN entry for v0.6.7

## Test Results

- `ExplicitSourceClassifierTest`: 20 tests, all PASSED
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL (all prior tests unchanged)
- `:app:assembleDebug`: BUILD SUCCESSFUL

## APK

`Komikku-v1.13.6-kmk.6.7-debug.apk` — copied from `app-universal-debug.apk`

## What Is Not Filtered

- Installed/untrusted extensions (intentionally kept manageable)
- Sources with `isNsfw = true` but no explicit keyword or known ID (e.g., ecchi-only sources)
- Any source with only `adult` in the name without a qualifier (e.g., `adult comic`)
