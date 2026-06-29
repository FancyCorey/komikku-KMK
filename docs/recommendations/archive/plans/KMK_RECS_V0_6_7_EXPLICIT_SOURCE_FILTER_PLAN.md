# KMK-Recs v0.6.7 Explicit Porn/Hentai Source Filter Plan

Status: planning. Do not implement until the user explicitly approves or provides this plan to Claude for implementation.

Target version: `KMK-Recs v0.6.7`

## Purpose

Add a user-facing setting that can block clearly explicit porn/hentai sources separately from softer NSFW/ecchi sources.

The existing Komikku/KMK source model only exposes a broad `isNsfw` flag. That is too coarse for the requested behavior because a source can be marked NSFW for very different reasons:

- clearly explicit porn/hentai,
- doujin/hentai gallery sources,
- adult comics/porn comic sites,
- ecchi or suggestive content,
- mixed mature content.

The user wants the app to hide/block explicit porn/hentai sources without automatically hiding ecchi-only sources.

This feature should add a conservative local classifier and preference layer. It should not rely on `isNsfw == true` alone.

## Versioning

Use:

`KMK-Recs v0.6.7`

Reason:

- This continues the v0.6 extension/source management track.
- It affects extension/source visibility and recommendation source selection.
- Do not jump to a new major topic number.

Expected debug APK naming pattern:

`Komikku-v1.13.6-kmk.6.7-debug.apk`

Only use this exact name if source metadata confirms it is the next correct version.

## Current Code Findings

### Broad NSFW Metadata

`Extension` has only one adult-content metadata field:

`app/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt`

```kotlin
abstract val isNsfw: Boolean
```

There is no built-in extension metadata for:

- explicit porn,
- hentai,
- ecchi,
- mature,
- adult-but-not-explicit,
- content severity.

Therefore, the implementation must not equate `isNsfw` with explicit porn/hentai.

### Current NSFW Setting

`SourcePreferences` currently has:

`app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`

```kotlin
fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)
```

This is used by:

- `GetExtensionsByType`
- `ExtensionLoader`
- non-installed Sources To Try suggestions
- extension/source display surfaces

This setting is broad and should remain broad. Do not repurpose it for explicit-only filtering.

### Existing Hentai/Blacklist Logic

`app/src/main/java/exh/source/BlacklistedSources.kt`

Currently only blacklists EHentai extension/source families narrowly:

```kotlin
val BLACKLISTED_EXT_SOURCES = EHENTAI_EXT_SOURCES.keys
val BLACKLISTED_EXTENSIONS = arrayOf("eu.kanade.tachiyomi.extension.all.ehentai")
```

`app/src/main/java/exh/util/LewdMangaChecker.kt`

Has a heuristic for manga/source names and tags:

- `hentai`
- `adult`
- `smut`
- `lewd`
- `nsfw`
- `erotica`
- `pornographic`
- `mature`
- `18+`
- known explicit source names like `hentaifox`, `nhentai`, `pururin`, `tsumino`, etc.

This helper is manga-level and currently too broad for source-level explicit filtering because it includes words like `mature`, `lewd`, and `nsfw`, which could catch ecchi/mature sources. Reuse the idea, not the exact broad logic.

### Settings Location

`app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsBrowseScreen.kt`

Already has an `NSFW content` group with:

- `showNsfwSource()`
- parental controls info

The new user-facing setting should live in this same group because it controls adult source visibility.

`SettingsAdvancedScreen.kt` has `enableSourceBlacklist()` under developer tools. Do not place this new user-facing option there.

## Required User-Facing Behavior

Add a setting:

`Block explicit porn/hentai sources`

Behavior:

- When enabled, hide/block sources and extensions classified as clearly explicit porn/hentai.
- Do not automatically hide ecchi-only sources.
- Do not automatically hide every `isNsfw` source.
- Keep the existing broad `Show NSFW sources` setting unchanged.

Recommended default:

- `false`

Reason:

- Avoid surprising existing users by hiding sources after update.
- User explicitly opts in to this stricter explicit filter.

Interaction with `showNsfwSource()`:

- If `showNsfwSource()` is false, broad NSFW hiding remains stronger and may hide ecchi and explicit NSFW sources.
- If `showNsfwSource()` is true and `blockExplicitPornHentaiSources()` is true, explicit porn/hentai sources are hidden while ecchi/mixed NSFW sources can remain visible.
- If both are true/false combinations, behavior should be predictable and documented.

Suggested settings text:

- Title: `Block explicit porn/hentai sources`
- Subtitle: `Hides clearly explicit adult and hentai sources without blocking ecchi-only sources.`

## New Preference

In:

`app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`

Add:

```kotlin
fun blockExplicitAdultSources() = preferenceStore.getBoolean("block_explicit_adult_sources", false)
```

Alternative key names are acceptable if more consistent:

- `block_explicit_hentai_sources`
- `hide_explicit_adult_sources`
- `hide_porn_hentai_sources`

Pick one clear key and document it.

Recommended name:

```kotlin
fun blockExplicitPornHentaiSources() = preferenceStore.getBoolean("block_explicit_porn_hentai_sources", false)
```

Use the same name consistently in code and docs.

## New Classifier

Create a dedicated source/extension classifier instead of hardcoding checks across multiple files.

Suggested new file:

`app/src/main/java/exh/source/ExplicitSourceClassifier.kt`

Purpose:

- classify sources/extensions as explicit porn/hentai using conservative source-level evidence,
- deliberately avoid classifying ecchi-only sources as explicit,
- provide shared logic for source manager, extensions page, recommendations, and non-installed suggestions.

Suggested object:

```kotlin
package exh.source

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import tachiyomi.domain.source.model.StubSource

object ExplicitSourceClassifier {
    fun isExplicitExtension(extension: Extension): Boolean
    fun isExplicitAvailableSource(source: Extension.Available.Source): Boolean
    fun isExplicitSource(source: CatalogueSource): Boolean
    fun isExplicitSourceName(name: String): Boolean
    fun isExplicitPackageName(pkgName: String): Boolean
    fun shouldHideExtension(extension: Extension, blockExplicit: Boolean): Boolean =
        blockExplicit && isExplicitExtension(extension)
}
```

Claude should adjust imports/types to what is actually needed.

## Classification Rules

The classifier must be conservative.

### Strong Explicit Keywords

Treat as explicit if source/extension/package/base URL contains strong indicators:

- `hentai`
- `porn`
- `porno`
- `nhentai`
- `e-hentai`
- `exhentai`
- `hentaifox`
- `hentai2read`
- `hentainexus`
- `hentai cafe`
- `manhwahentai`
- `myhentaicomics`
- `myhentaigallery`
- `ninehentai`
- `simply hentai`
- `pururin`
- `tsumino`
- `8muses`
- `hbrowse`
- `luscious`
- `allporncomic`
- `multporn`
- `doujins`

### Adult Keywords With Care

Treat as explicit only if the keyword is strong enough or combined with explicit context:

- `xxx`
- `18+`
- `adult comic`
- `adult manga`
- `adult manhwa`
- `adult manhua`
- `erotic`
- `erotica`
- `smut`

Avoid using broad terms by themselves if they may be mixed/non-explicit:

- `nsfw`
- `mature`
- `lewd`
- `adult` alone, unless source name/base URL clearly indicates explicit content

### Ecchi Must Not Be Explicit

Do not classify as explicit based only on:

- `ecchi`
- `suggestive`
- `fanservice`

If a source name contains both `ecchi` and explicit keywords like `hentai` or `porn`, the explicit keywords win.

### Known Source IDs

Use known explicit source ID sets where available:

- `EHENTAI_EXT_SOURCES.keys`
- `EXHENTAI_EXT_SOURCES.keys`
- `nHentaiSourceIds`
- `EIGHTMUSES_SOURCE_ID`
- `PURURIN_SOURCE_ID`

Do not include ordinary ecchi/mature source IDs unless they are clearly explicit.

### Package Names

Classify package names conservatively:

- `eu.kanade.tachiyomi.extension.all.ehentai`
- package names containing `.nhentai`
- package names containing `hentai`
- package names containing `porn`
- package names containing known explicit site names

Do not classify a package as explicit simply because `extension.isNsfw == true`.

## Surfaces To Apply The Filter

### 1. Extension List / Extensions Page

File:

`app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt`

Currently filters by broad NSFW:

```kotlin
val showNsfwSources = preferences.showNsfwSource().get()
.filter { (showNsfwSources || !it.isNsfw) }
```

Add:

```kotlin
val blockExplicit = preferences.blockExplicitPornHentaiSources().get()
```

and filter installed/available/untrusted extensions with classifier:

```kotlin
.filter { !ExplicitSourceClassifier.shouldHideExtension(it, blockExplicit) }
```

Important: `GetExtensionsByType.subscribe()` currently reads `showNsfwSource()` once outside the flow combine. Claude should check whether this is existing behavior and whether the new explicit preference should be included in `combine(...)` so changes update live. Preferred behavior is live update without restart where practical.

### 2. Installed Extension Loading

File:

`app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`

Current behavior may skip loading NSFW extensions when `showNsfwSource()` is false.

Do not necessarily block loading explicit extensions here unless needed.

Preferred:

- keep loading installed extensions so they can be managed/uninstalled/trusted,
- hide them from user-facing source/extension lists through `GetExtensionsByType` and visible source filtering.

Reason:

- If explicit extensions are not loaded at all, users may have trouble seeing/removing them.
- The new setting is a visibility/blocking filter, not a destructive uninstall.

Claude should inspect current behavior and document the chosen approach.

### 3. Source Manager / Visible Sources

File:

`app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`

Current visible source methods:

```kotlin
override fun getVisibleOnlineSources()
override fun getVisibleCatalogueSources()
```

Add explicit filtering to visible source results:

```kotlin
.filter { !ExplicitSourceClassifier.isExplicitSource(it) || !sourcePreferences.blockExplicitPornHentaiSources().get() }
```

Preferred helper:

```kotlin
private fun Source.isHiddenByExplicitFilter(): Boolean
```

Do not remove explicit sources from internal `sourcesMapFlow` unless there is a strong reason. Filtering visible sources is safer because existing library entries and database references can still resolve.

### 4. Browse Sources Page

Source page likely uses `sourceManager.getVisibleCatalogueSources()` through screen model/interactors.

If `AndroidSourceManager.getVisibleCatalogueSources()` is filtered, this should naturally apply.

Claude should verify:

- `SourcesScreenModel`
- source interactor classes such as `GetEnabledSources` / `GetLanguagesWithSources`

If some source list bypasses `getVisibleCatalogueSources()`, apply classifier there too.

### 5. Recommendations / For You

Files:

- `app/src/main/java/exh/recs/RecommendationSourceFilter.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt`
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt`

Expected impact:

- For You source list should not include explicit sources when the new setting is enabled.
- Recommendation Settings source priority should not show explicit sources when enabled.
- Sources To Try should not suggest explicit porn/hentai sources when enabled.
- Top Picks/For You searches should not query blocked explicit sources.

Preferred implementation:

- ensure recommendations use `sourceManager.getVisibleCatalogueSources()` after the visible filter is updated,
- add explicit filtering in `GetNonInstalledSourceSuggestions` / scorer because available extensions are not in the source manager yet.

Non-installed suggestion logic already checks broad NSFW:

`GetNonInstalledSourceSuggestions.kt`

```kotlin
val nsfwEnabled = sourcePreferences.showNsfwSource().get()
```

Add explicit preference and classifier:

```kotlin
val blockExplicit = sourcePreferences.blockExplicitPornHentaiSources().get()
```

Then exclude `Extension.Available` and/or per-source suggestions classified as explicit when enabled.

### 6. Global Search / Migration

Global search and migration should ideally respect visible source filtering if they source from enabled/visible catalogue sources.

Claude should inspect:

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt`
- migration source selection flows

Do not force a large refactor. If these paths already rely on visible source lists, no direct changes are needed. If they bypass visible source lists, add a narrow explicit filter only where source lists are assembled.

### 7. Existing Library Items

Do not hide existing library manga or break opening existing manga entries just because their source is explicit.

Reason:

- Users may have existing items and need to remove/export/manage them.
- Source resolution should still work internally.

The setting should primarily affect browse/search/recommendation/source discovery surfaces.

## Settings UI

File:

`app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsBrowseScreen.kt`

Add the new switch in the existing `NSFW content` group below `showNsfwSource()`.

Suggested:

```kotlin
Preference.PreferenceItem.SwitchPreference(
    preference = sourcePreferences.blockExplicitPornHentaiSources(),
    title = stringResource(KMR.strings.pref_block_explicit_porn_hentai_sources),
    subtitle = stringResource(KMR.strings.pref_block_explicit_porn_hentai_sources_summary),
)
```

Do not require authentication unless the codebase already requires it for all NSFW controls. The existing broad `showNsfwSource()` authenticates on change. For consistency, Claude may reuse the same authentication pattern if appropriate, but the setting is for blocking content rather than revealing it.

Recommended:

- Authentication is not necessary when enabling the block.
- If disabling the block reveals explicit sources, consider using the same authentication prompt as `showNsfwSource()`.
- If implementing conditional auth is too complex, document and keep it simple.

## String Resources

Add to KMK strings:

File likely:

`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Suggested keys:

```xml
<string name="pref_block_explicit_porn_hentai_sources">Block explicit porn/hentai sources</string>
<string name="pref_block_explicit_porn_hentai_sources_summary">Hides clearly explicit adult and hentai sources without blocking ecchi-only sources.</string>
```

Optional labels if needed:

```xml
<string name="explicit_source_hidden">Hidden by explicit source filter</string>
```

Do not add user-facing text that says internal development details.

## Classifier Tests

Add unit tests for the classifier.

Suggested test file:

`app/src/test/java/exh/source/ExplicitSourceClassifierTest.kt`

Test cases:

### Explicit Should Match

- `NHentai`
- `E-Hentai`
- `ExHentai`
- `HentaiFox`
- `Hentai2Read`
- `MyHentaiComics`
- `AllPornComic`
- `Tsumino`
- `Pururin`
- `8Muses`
- `Adult Comic` if rule includes this phrase
- package `eu.kanade.tachiyomi.extension.all.ehentai`
- package containing `.nhentai`

### Ecchi Should Not Match

- `EcchiManga`
- `Ecchi Scans`
- `EcchiToons`
- `Mature Manga` if no explicit keyword
- `NSFW Scans` if no explicit keyword and classifier avoids broad NSFW
- `Adult` alone if no explicit context and classifier avoids broad adult

### Mixed Should Match

- `Ecchi Hentai`
- `Adult Porn Comics`
- `EcchiPorn`

### Source ID Tests

If the classifier exposes source ID checks:

- EHentai source IDs explicit
- NHentai source IDs explicit
- local source not explicit
- merged source not explicit by this classifier

## Integration Tests / Regression Checks

Add tests where practical:

- `GetExtensionsByType` filters explicit available extensions when preference enabled.
- `GetExtensionsByType` does not filter ecchi-only `isNsfw` extension when broad NSFW is shown and explicit block is enabled.
- `NonInstalledSourceSuggestionScorer` excludes explicit available extensions when explicit block is enabled.

If DI/flow tests are too heavy, document why and rely on classifier unit tests plus manual QA.

## Manual QA

After building APK:

1. Open Settings > Browse > NSFW content.
2. Confirm `Block explicit porn/hentai sources` appears separately from `Show NSFW sources`.
3. Enable broad `Show NSFW sources`.
4. Enable `Block explicit porn/hentai sources`.
5. Open Browse > Extensions.
6. Confirm obvious explicit sources like NHentai/EHentai/HentaiFox/etc. are hidden if available/installed.
7. Confirm ecchi-only sources are not hidden solely because they contain `ecchi`.
8. Open Browse > Sources.
9. Confirm explicit sources are hidden from visible source list.
10. Open For You / Recommendation Settings.
11. Confirm explicit sources do not appear in source priority or Sources To Try.
12. Disable the explicit block.
13. Confirm explicit sources can appear again if broad NSFW is enabled.
14. Disable broad `Show NSFW sources`.
15. Confirm broad NSFW behavior remains stronger and unchanged.
16. Confirm existing library entries from explicit sources do not crash and remain manageable.

## Implementation Order

1. Add preference in `SourcePreferences`.
2. Add strings.
3. Add `ExplicitSourceClassifier`.
4. Add classifier unit tests.
5. Add setting in `SettingsBrowseScreen`.
6. Apply filter to `GetExtensionsByType`.
7. Apply filter to `AndroidSourceManager.getVisibleOnlineSources()` and `getVisibleCatalogueSources()`.
8. Apply filter to non-installed Sources To Try suggestion path.
9. Verify recommendations source lists naturally respect visible source filtering; add direct filter if needed.
10. Inspect global search/migration source list assembly; add narrow filters only if they bypass visible sources.
11. Update docs/release notes/versioning.
12. Run tests/build APK.

## Risks And Mitigations

### Risk: False Positives

Some source names may contain adult-ish terms but not be explicit porn/hentai.

Mitigation:

- Keep classifier conservative.
- Do not match `ecchi`.
- Avoid broad matching on `nsfw`, `mature`, `lewd`, or `adult` alone.

### Risk: False Negatives

Some explicit sources may not be caught if names are obscure.

Mitigation:

- Start with obvious known explicit names.
- Future versions can add user-managed source block/allow lists if needed.

### Risk: Installed Explicit Extensions Become Hard To Manage

If hidden from Extensions page, users may not be able to uninstall them.

Mitigation:

- Prefer hiding available sources and browse/recommendation sources while still letting installed explicit extensions appear in Extensions page with a hidden/blocked badge, OR add a clear "Show blocked explicit sources" path.

Important decision:

Claude should evaluate this carefully before implementation.

Recommended final behavior:

- Browse/Sources/Recommendations: hide explicit sources when blocked.
- Extensions page:
  - available explicit extensions hidden,
  - installed explicit extensions should remain visible enough to manage/uninstall, but can be marked/disabled/filtered if the UI supports it.

If keeping installed explicit extensions visible conflicts with `GetExtensionsByType` filtering, document the choice. The safest user-management behavior is not to trap installed extensions out of sight.

### Risk: Restart Requirement

Some existing source visibility changes may require restart due to `ExtensionLoader` behavior.

Mitigation:

- Prefer filtering visible lists live instead of changing load behavior.
- If restart is still needed for some surfaces, add subtitle/docs noting that source list changes may require refresh/restart.

## Documentation Updates

Claude must update documentation after implementation.

Required:

- Create implementation report:
  - `docs/recommendations/KMK_RECS_V0_6_7_EXPLICIT_SOURCE_FILTER_IMPLEMENTATION.md`
- Update:
  - `docs/recommendations/CURRENT_STATE.md`
  - `docs/recommendations/NEXT_WORK.md`
  - `docs/recommendations/README.md`
  - `RECOMMENDATION_VERSIONING.md`
- Update release notes:
  - `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

Documentation should explicitly mention:

- `v0.6.7` adds explicit porn/hentai source blocking.
- The setting is separate from broad NSFW visibility.
- Ecchi-only sources are not blocked by this setting.
- The classifier is conservative and can be expanded later.
- Which surfaces are filtered.
- Tests run.
- APK produced.

## Acceptance Criteria

This implementation is complete only when:

- A new setting exists in Browse > NSFW content.
- Explicit porn/hentai source blocking is separate from broad `Show NSFW sources`.
- Ecchi-only sources are not blocked by explicit filtering.
- Obvious explicit sources/extensions are blocked/hidden in browse/recommendation discovery surfaces when enabled.
- Installed explicit extensions remain manageable or the implementation clearly provides a safe way to manage them.
- Sources To Try does not recommend explicit porn/hentai sources when enabled.
- For You does not query explicit porn/hentai sources when enabled.
- Broad NSFW behavior still works as before.
- Classifier tests pass.
- Existing tests pass or failures are documented.
- Documentation and release notes are updated to `KMK-Recs v0.6.7`.
- A debug APK is produced using current versioning rules.

