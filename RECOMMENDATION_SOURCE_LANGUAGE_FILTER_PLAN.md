# Recommendation Source Language Filter and Priority Cleanup Plan

Date: 2026-06-13

Status: superseded implementation plan. This plan became KMK-Recs v0.3.2. For current behavior, read `docs/recommendations/CURRENT_STATE.md` and `RECOMMENDATION_IMPLEMENTATION_AUDIT.md` first.

## Executive Summary

The current For You recommendation source priority list is showing sources from multiple languages and multiple source variants with the same display name. The user reported entries such as:

- Local Source
- MangaFire
- ThunderScans
- MangaHere
- MangaFire PT-BR
- ManhwaPlus
- QIScans
- MangaFire JA
- DriftScans
- MangaFire ES-419
- MangaDemon EN
- LavaScans
- ManhwaTop
- Asmodius
- MangaNato
- ManhwaFast
- KyanScans
- VortexScans
- MangaFire PT
- MangaFire ES
- AsuraScans EN

This creates three practical issues:

1. **Language noise**: non-English variants can occupy recommendation slots even when the user only wants English.
2. **Duplicate-name confusion**: sources such as MangaFire appear many times because each language variant has a separate source id.
3. **Top-20 starvation**: the For You source cap is applied after visible source selection, so irrelevant language variants can consume slots that should go to useful English sources.

This pass should add a bounded, local, configurable language filter for For You recommendation sources.

Core goal:

> For You should only search sources in the selected recommendation languages, defaulting to English for the user's current workflow, while still allowing other languages later if the user chooses.

## Current Code Map

### Source Preferences

- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
  - Existing global source language preference:
    - `enabledLanguages()`
  - Existing recommendation preferences:
    - `recommendationSourceOrder()`
    - `recommendationSourceStrategies()`
    - `recommendationRatedMangaVisibility()`

### For You Source Selection

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
  - Current source selection:

```kotlin
val storedOrder = RecommendationSourceOrdering.parse(sourcePreferences.recommendationSourceOrder().get())
val orderedEnabledSources = RecommendationSourceOrdering.apply(
    visibleSources = sourceManager.getVisibleCatalogueSources(),
    storedOrder = storedOrder,
    disabledSourceIds = disabledSourceIds,
)
val sources = orderedEnabledSources.take(MAX_SOURCES)
val boostedSourceIds = RecommendationSourceOrdering.boostedSourceIds(sources)
```

Current limitation:

- `sourceManager.getVisibleCatalogueSources()` can include multiple languages.
- Ordering/capping happens before any recommendation-specific language filtering.
- Therefore non-target languages and duplicate source-name variants can consume priority slots.

### Source Priority Settings

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
  - Displays `state.orderedSources`.
  - Allows drag reorder.
  - Shows language as `source.lang.uppercase()`.
  - Does not currently filter priority sources by recommendation language.

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`
  - Uses `sourceManager.getVisibleCatalogueSources()`.
  - Applies `RecommendationSourceOrdering.applyAll`.
  - Does not currently expose language filter state.

### Source Ordering Helper

- `app/src/main/java/exh/recs/RecommendationSourceOrdering.kt`
  - Parses source id order.
  - Applies stored order.
  - Excludes disabled sources for For You.
  - Includes disabled sources for settings display.
  - Removes duplicate ids while parsing.

## Non-Goals

Do not redesign the entire recommendation settings screen.

Do not remove non-English source support permanently.

Do not globally change Komikku's source language settings.

Do not delete or mutate installed extensions.

Do not use source display name alone as identity.

Reason:

- Separate source ids with the same name can be legitimate language variants.
- The stable identity must remain source id.

Do not make the Local Source recommendation behavior ambiguous.

Either:

- exclude it from For You recommendation search by default, or
- include it only if explicitly enabled and supported.

## Product Decision

### Default Language

For this user's current recommendation workflow, default recommendation source languages should be English-only:

```text
en
```

This should be configurable later or immediately if not too expensive.

### Recommended Setting

Add a recommendation-specific language preference:

```kotlin
fun recommendationSourceLanguages() =
    preferenceStore.getStringSet("recommendation_source_languages", setOf("en"))
```

Why recommendation-specific instead of reusing `enabledLanguages()` directly:

- The user may have multiple source languages enabled globally for browsing.
- For You recommendations should be narrower and higher quality.
- The user specifically wants EN-only for now, while still allowing other languages eventually.

Fallback behavior:

- If `recommendationSourceLanguages()` is empty or missing, use `setOf("en")`.
- If the user later wants "all enabled source languages", that can be added as a setting option.

### Local Source

Recommended default:

- Exclude Local Source from For You search and source priority by default.

Reason:

- Local Source is not a remote catalogue source in the same way extension sources are.
- It likely does not provide useful genre/filter search behavior for recommendations.
- Including it in priority can confuse the source cap and the UI.

If Claude finds that Local Source has a specific id constant or type marker, filter it explicitly. If not, use the safest project-native check available.

Do not hardcode a fragile display-name-only exclusion unless no better option exists.

## Desired Behavior

### In Recommendation Settings

The source priority list should:

- show only sources matching selected recommendation languages,
- default to English-only,
- not show MangaFire variants from PT-BR, JA, ES-419, PT, ES when only EN is selected,
- clearly display language next to source names,
- keep source ids as the internal identity,
- preserve manual ordering for hidden languages so if a language is re-enabled later, its source order can return.

### In For You

For You should:

- filter visible catalogue sources by recommendation language before ordering/capping,
- exclude disabled recommendation sources,
- apply manual order,
- take the first 20 after filtering,
- compute boosted top three after filtering,
- avoid non-English variants consuming top-20 slots when EN-only is selected.

### Duplicate Source Names

Duplicate source display names are allowed because source ids differ.

But the UI should make them understandable:

```text
MangaFire · EN
MangaFire · PT-BR
MangaFire · ES-419
```

If language filter is EN-only, only the EN entry should remain.

## Implementation Plan

### Phase 1: Add Recommendation Language Preference

Modify:

- `SourcePreferences.kt`

Add:

```kotlin
fun recommendationSourceLanguages() =
    preferenceStore.getStringSet("recommendation_source_languages", setOf("en"))
```

If the project prefers constants for defaults, add:

```kotlin
private val DEFAULT_RECOMMENDATION_SOURCE_LANGUAGES = setOf("en")
```

Do not change global `enabledLanguages()`.

### Phase 2: Add Source Filtering Helper

Create or extend a helper in `exh.recs`.

Recommended new file:

- `app/src/main/java/exh/recs/RecommendationSourceFilter.kt`

Suggested API:

```kotlin
internal object RecommendationSourceFilter {
    val DefaultLanguages: Set<String> = setOf("en")

    fun normalizeLanguages(languages: Set<String>): Set<String>

    fun filterForRecommendations(
        sources: List<CatalogueSource>,
        languages: Set<String>,
        includeLocal: Boolean = false,
    ): List<CatalogueSource>

    fun isAllowedLanguage(source: CatalogueSource, languages: Set<String>): Boolean

    fun isLocalSource(source: CatalogueSource): Boolean
}
```

Language normalization:

- lowercase values,
- trim whitespace,
- remove blanks.

Language matching:

- Compare normalized `source.lang`.
- Treat `en` as English only.
- Do not make `all` behavior unless explicitly implemented.

Local source:

- Prefer project-native local-source checks.
- If the source has a known local source id or type, use that.
- If no safe check exists, document the limitation before using display-name fallback.

### Phase 3: Apply Filter Before Ordering and Cap

Modify:

- `BrowsePersonalRecommendationsScreenModel.kt`

Desired source selection:

```kotlin
val recommendationLanguages = sourcePreferences.recommendationSourceLanguages().get()
val recommendationVisibleSources = RecommendationSourceFilter.filterForRecommendations(
    sources = sourceManager.getVisibleCatalogueSources(),
    languages = recommendationLanguages,
)

val storedOrder = RecommendationSourceOrdering.parse(sourcePreferences.recommendationSourceOrder().get())
val orderedEnabledSources = RecommendationSourceOrdering.apply(
    visibleSources = recommendationVisibleSources,
    storedOrder = storedOrder,
    disabledSourceIds = disabledSourceIds,
)
val sources = orderedEnabledSources.take(MAX_SOURCES)
val boostedSourceIds = RecommendationSourceOrdering.boostedSourceIds(sources)
```

Important:

- The language filter must happen before `.take(MAX_SOURCES)`.
- Disabled-source filtering should still happen inside `RecommendationSourceOrdering.apply`.
- Stored order can contain source ids from other languages; those ids should simply be ignored until the language is enabled again.

### Phase 4: Apply Filter In Settings UI State

Modify:

- `RecommendationsSettingsScreenModel.kt`

State should include:

```kotlin
val recommendationLanguages: ImmutableSet<String>
val availableLanguages: ImmutableList<String>
```

The ordered source list shown in settings should be computed from:

```kotlin
val visibleSources = sourceManager.getVisibleCatalogueSources()
val filteredSources = RecommendationSourceFilter.filterForRecommendations(
    sources = visibleSources,
    languages = recommendationLanguages,
)
val allInOrder = RecommendationSourceOrdering.applyAll(filteredSources, storedOrder)
```

Available languages:

- derive from visible catalogue sources:

```kotlin
visibleSources.map { it.lang.lowercase() }.distinct().sorted()
```

Consider excluding Local Source language marker from available languages if Local Source is excluded.

### Phase 5: Add Simple Language Selector UI

Modify:

- `RecommendationsSettingsScreen.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Add a section above source priority:

```text
Recommendation languages
```

UI:

- Use FilterChips for visible languages.
- Default selected: EN.
- Allow selecting/deselecting languages.
- Prevent all languages from being deselected, or if all are deselected, immediately restore EN.

Recommended behavior:

- show compact chips:
  - EN
  - PT-BR
  - ES
  - ES-419
  - JA
- tapping a chip toggles that language.

State/model method:

```kotlin
fun toggleRecommendationLanguage(lang: String)
```

When language changes:

- save preference,
- recompute ordered sources from filtered source list,
- recompute boosted ids,
- do not delete stored source order.

### Phase 6: Preserve Full Manual Order Across Languages

Potential issue:

- If settings only shows EN sources and user reorders them, serializing only visible EN sources could drop stored order for hidden languages.

Required behavior:

- Keep `recommendationSourceOrder` as the full known source id order.
- When reordering the currently visible filtered list, merge that visible order back into the full order while preserving hidden-language ids.

Add helper to `RecommendationSourceOrdering`:

```kotlin
fun mergeVisibleOrder(
    existingStoredOrder: List<Long>,
    visibleOrderedIds: List<Long>,
    allVisibleSourceIds: Set<Long>,
): List<Long>
```

Suggested semantics:

- Remove visible filtered ids from existing order.
- Insert the new visible filtered order at the position of the first removed visible id.
- Keep hidden-language ids in their relative positions.
- Append any source ids not represented yet.

Simpler acceptable first pass:

- Store visible filtered order followed by hidden ids from previous order.
- This preserves hidden-language ids, though their cross-language interleaving may change.

Do not discard hidden language ids.

### Phase 7: Recover From Existing Duplicate/Bad Stored Order

Existing users may already have:

- duplicate MangaFire variants,
- duplicate source ids from prior crash,
- order entries from languages they no longer want.

Existing `RecommendationSourceOrdering.parse` already removes duplicate ids.

Add tests to ensure:

- duplicate ids are removed,
- hidden-language ids do not show when language is disabled,
- hidden-language ids are not lost from stored order when EN list is reordered.

### Phase 8: Komikku-Style Versioning and Documentation

Update:

- `RECOMMENDATION_VERSIONING.md`
- this plan, or a companion implementation note if Claude prefers keeping implementation logs separate

Important:

- Komikku's public release/update style should be the model for user-facing APK naming and release notes.
- The internal `KMK-Recs` version can still exist as a feature-track label, but it should not be the only or primary release identity.
- Do not rely only on the filename if the APK is meant to update over an installed build. Android update behavior depends on package name, signing key, and `versionCode`.

Komikku release style observed:

- GitHub releases are titled like:

```text
Komikku v1.13.6
```

- Tags use:

```text
v1.13.6
```

- Release notes use:

```text
#### What's Changed
##### New
##### Improve
##### Fix
Full Changelog: ...
```

- APK names are user-facing and simple, for example:

```text
Komikku-v1.13.6.apk
```

Recommended local fork naming:

```text
Komikku-v1.13.6-kmk.3.2-debug.apk
Komikku-v1.13.6-kmk.3.2-release.apk
```

Rationale:

- Keeps the upstream Komikku base version visible.
- Adds a local fork/build suffix for recommendation work.
- Avoids confusing the user with a detached feature-only name such as `KMK-Recs-v0.3.2`.

Recommended release-note heading:

```text
## Komikku v1.13.6-kmk.3.2
```

Recommended changelog shape:

```text
#### What's Changed

##### New
- recommendations: Add recommendation source language filter.

##### Improve
- recommendations: Default For You source priority to English sources.
- recommendations: Prevent non-target language variants from consuming top source slots.
- recommendations: Preserve hidden-language source order when filtering priority sources.

##### Fix
- recommendations: Reduce duplicate source-name confusion from multi-language source variants.
```

Also add an internal feature version entry:

```text
KMK-Recs v0.3.2
```

Suggested entry:

- add recommendation language filter,
- default For You source priority to English,
- prevent non-target language variants from consuming top-20 source slots,
- clarify duplicate source-name variants by language,
- preserve hidden-language source order.

If an APK is built, use:

```text
Komikku-v1.13.6-kmk.3.2-debug.apk
```

If `versionCode` or `versionName` is changed in `app/build.gradle.kts`, Claude must document:

- old value,
- new value,
- reason for the change,
- whether the build is intended to update the currently installed APK.

Do not change `versionCode` blindly. If the user only needs a one-off debug build, APK filename naming may be enough. If the user expects tap-to-update behavior over the previous installed APK, evaluate whether `versionCode` must be incremented.

Also update this plan, `RECOMMENDATION_VERSIONING.md`, or a companion implementation note with:

- exact files changed,
- tests run,
- APK path,
- known caveats.

## Testing Plan

### Unit Tests

Add tests:

- `RecommendationSourceFilterTest`
  - EN-only keeps EN sources.
  - EN-only removes PT-BR, JA, ES, ES-419 sources.
  - multi-language selection keeps selected languages.
  - empty language set falls back to EN.
  - local source is excluded by default.

- `RecommendationSourceOrderingTest`
  - hidden-language ids are not shown in filtered list.
  - merging visible order preserves hidden ids.
  - duplicate ids are still removed.

### Manual Tablet Test

After building and installing:

1. Open recommendation settings.
2. Confirm recommendation languages defaults to EN.
3. Confirm priority list only shows EN sources.
4. Confirm MangaFire non-EN variants are not shown when EN-only.
5. Confirm AsuraScans EN, ThunderScans EN, MangaHere EN, LavaScans EN, etc. can appear if installed/visible.
6. Confirm Local Source is absent unless explicitly supported/allowed.
7. Toggle another language, such as PT-BR or ES.
8. Confirm matching language sources appear.
9. Toggle it off again.
10. Confirm those sources disappear.
11. Reorder EN sources.
12. Refresh For You.
13. Confirm top three boosted sources are the first three EN sources.
14. Confirm non-EN variants do not consume top-20 For You slots.

## Risk Assessment

### Low Risk

- Adding a recommendation-specific language preference.
- Filtering before top-20 cap.
- Displaying source language more clearly.

### Medium Risk

- Preserving hidden-language order while reordering visible filtered lists.
  - Mitigation: helper function plus unit tests.

- Local Source detection.
  - Mitigation: use project-native id/type check; if uncertain, document and avoid brittle behavior.

### Higher Risk

- Letting users select all languages again can reintroduce duplicate-name noise.
  - Mitigation: clear language chips and language labels beside source names.

## Final Acceptance Criteria

The pass is complete when:

- For You filters sources by recommendation languages before source ordering and top-20 cap.
- Default recommendation language is EN.
- Source priority settings show only selected languages.
- Non-EN MangaFire variants do not appear in EN-only mode.
- Top three boosted sources are selected from filtered EN sources.
- Stored order is not destroyed for hidden languages.
- Local Source is excluded by default or handled explicitly.
- Tests cover language filtering and order preservation.
- Version notes include `KMK-Recs v0.3.2` as an internal feature label.
- APK/release notes follow Komikku-style naming, for example `Komikku v1.13.6-kmk.3.2` and `Komikku-v1.13.6-kmk.3.2-debug.apk`.
