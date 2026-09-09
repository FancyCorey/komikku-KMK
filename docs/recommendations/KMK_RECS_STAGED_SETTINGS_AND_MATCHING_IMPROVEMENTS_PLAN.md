# KMK-Recs Staged Settings And Matching Improvements Plan

Date: 2026-06-18

Status: planning only. Do not implement until the user approves this plan and requests a Claude Code prompt.

## Purpose

This plan covers the next group of improvements discussed after `KMK-Recs v0.6.2`.

The requested ideas touch several different parts of the recommendation system:

- bulk installing non-installed Sources To Try suggestions,
- clarifying source Like/Dislike meaning across For You and Sources To Try,
- verifying source priority ordering persistence,
- adding a Loved Manga view,
- improving Love/Like/Dislike other versions matching so alternate titles are not missed.

Because these are not all the same subsystem, the work should be staged rather than forced into one oversized patch.

## Versioning Recommendation

Use the topic continuity rule from `RECOMMENDATION_VERSIONING.md`.

Recommended staged versions:

| Version | Topic | Scope |
| --- | --- | --- |
| `KMK-Recs v0.6.3` | Extension/source settings polish | Bulk install Sources To Try, source preference semantics cleanup, source ordering persistence verification |
| `KMK-Recs v0.5.2` | Cross-extension matching patch | Improve Love/Like/Dislike other versions search query strategy while preserving the bounded matching workflow |
| `KMK-Recs v0.7.0` | Loved Manga view | New user-facing rated-manga/taste browsing surface |

Do not label all of this as one new version unless the implementation intentionally ships it as one combined beta APK. Even then, documentation should clearly separate the subfeatures by subsystem.

## Current Implementation Findings

### Sources To Try

Current files:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt
app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStore.kt
```

Current behavior as of v0.6.2:

- each suggestion has individual Install and Dismiss actions,
- each suggestion has Like and Dislike icon buttons,
- suggestions are shown top 5 by default, expandable to all suggestions,
- install uses `extensionManager.installExtension(suggestion.extension)`,
- liked/disliked available source keys use `a|signatureHash|pkgName[|sourceId]`.

There is currently no bulk-install action and no selection mode.

### Source Like/Dislike Semantics

Current behavior as of v0.6.2:

- installed source Like is stored visually but does not affect For You order,
- installed source Dislike excludes that source from For You by adding it to the effective disabled source IDs,
- non-installed source Like keeps the suggestion visible and scores it high,
- non-installed source Dislike hides it from Sources To Try,
- Dismiss remains separate from Dislike.

User concern:

Disliking a source's For You/recommendation behavior is not always the same as disliking the source as a manga source. A source can have weak recommendation/search behavior but still contain good manga.

### Source Ordering

Current files:

```text
app/src/main/java/exh/recs/RecommendationSourceOrdering.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/test/java/exh/recs/RecommendationSourceOrderingTest.kt
```

Current implementation:

- source order is serialized in `SourcePreferences.recommendationSourceOrder()`,
- `RecommendationSourceOrdering.parse()` deduplicates IDs,
- `applyAll()` displays visible sources in stored order,
- `mergeVisibleOrder()` merges a language-filtered visible reorder back into the full stored order,
- settings UI keeps a local `sourcesState` `SnapshotStateList` while dragging,
- `LaunchedEffect(state.orderedSources)` refreshes the local list only when not dragging,
- drag uses item keys, then looks up source IDs before `removeAt()`.

This is already much safer than the earlier crash-prone indexed version, but the next pass should verify persistence and edge cases rather than assuming they are fixed.

### Loved Manga

Current rating/taste behavior:

- `MangaRating.LOVE` exists with value `2`,
- `GetMangaTaste.awaitAll()` can return all taste rows,
- taste rows store `(mangaId, source, url, title, rating, updatedAt)`,
- For You already uses `GetMangaTaste.awaitAll()`.

Potential issue:

Taste rows may contain enough identity to show a Loved Manga list, but duplicate suppression needs access to richer manga metadata when available. Title-only dedupe is too risky. A conservative title + description/intro dedupe is safer, but it depends on local DB metadata being initialized for those manga.

### Cross-Extension Matching

Current files:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt
```

Finding:

`CrossExtensionMatchScreenModel` does not directly reuse `SearchScreenModel`. It implements a separate bounded search flow using:

```kotlin
source.getSearchManga(1, query.sanitize(), source.getFilterList())
```

It starts with:

```kotlin
mutableState.update { it.copy(searchQuery = manga.title) }
search(manga.title)
```

This means it searches only the current manga title. It does not currently generate alternate queries from:

- original title vs custom title,
- romanized title,
- Korean/Japanese title,
- tracker alternate titles,
- metadata title aliases,
- source website aliases,
- description/intro matching.

Normal global search also searches one user-entered query, but its source selection and display behavior are more complete. The matching workflow copied the basic per-source search idea, then added a per-source cap and auto-selection. It is not a full global-search screen reuse.

## Stage 1: KMK-Recs v0.6.3

### Goal

Polish recommendation settings and source preference behavior without changing manga matching or adding a new Loved Manga surface.

### 1. Bulk Install Sources To Try

Recommended first implementation: **Install Visible Suggestions**, not long-press selection mode.

Reason:

- simpler,
- less UI state,
- less risk inside an already-dense settings screen,
- matches the user's simpler proposed alternative,
- avoids adding multi-select interaction to a settings page before it is clearly needed.

Behavior:

- Add a button below Sources To Try:

```text
Install visible suggestions
```

- It should install only the suggestions currently displayed in the section:
  - top 5 when collapsed,
  - all current suggestions when expanded.
- It should skip suggestions already being installed during that action.
- It should use the same existing install path as individual install:

```kotlin
extensionManager.installExtension(suggestion.extension)
```

- It should not install dismissed or disliked hidden suggestions because those are not visible.
- It should not auto-like installed suggestions.
- It should not change source priority order.

Implementation notes:

- Add a screen-model action:

```kotlin
fun installSuggestions(suggestions: List<NonInstalledSourceSuggestion>)
```

- Prefer sequential installation unless Komikku's extension installer is known to safely support parallel installs.
- Track `installingSuggestionKeys: Set<String>` in settings state if practical.
- Disable the bulk button while a bulk install is already running.
- Individual install buttons can also be disabled for installing keys if that state is added.

Possible future enhancement:

- Add long-press selection mode later if users need finer control.
- Selection mode should be separate future polish, not v0.6.3 unless explicitly requested.

Tests:

- Unit-test a pure helper if created, e.g. visible suggestion selection and duplicate extension collapse.
- Manual test: collapsed installs 5; expanded installs all currently visible; disliked suggestions are not installed because they are hidden.

### 2. Clarify Source Preference Semantics

Recommended change:

Split source preference meaning into two explicit concepts:

1. **Recommendation Quality Preference**
   - "This source is good/bad for For You recommendations."
   - Affects For You source selection and recommendation behavior.

2. **Source Quality Preference**
   - "I like/dislike this source overall as a manga source."
   - Affects Sources To Try and future source discovery.

Do not force a dislike in one context to automatically imply dislike in the other.

Practical v0.6.3 approach:

- Rename the existing installed source row controls conceptually to recommendation behavior:
  - Like recommendation behavior
  - Dislike recommendation behavior
- Keep their current effect:
  - disliked installed source excluded from For You,
  - liked installed source visually marked/stored.
- Rename/clarify Sources To Try controls as source-interest controls:
  - Interested / Not interested, or Like source / Dislike source.
- Keep their current effect:
  - liked non-installed suggestion stays visible and ranks high,
  - disliked non-installed suggestion is hidden from Sources To Try.

Important:

- Do not make installed-source recommendation dislike automatically hide same-name non-installed suggestions.
- Do not make non-installed source dislike disable an installed source after installation unless the user explicitly dislikes it in the installed source row.
- Keep the existing preference keys if possible to avoid migration churn, but update labels/help text to clarify meaning.

Better long-term model:

If the code needs to store both concepts explicitly, add a scoped key format later:

```text
rec|i|sourceId
source|i|sourceId
source|a|signatureHash|pkgName|sourceId
```

However, avoid this migration in v0.6.3 unless the current behavior is confusing enough to require it immediately.

Recommended v0.6.3 UI polish:

- Add compact section/help text:

```text
Installed source dislikes affect For You only. Sources To Try dislikes hide suggestions.
```

- Update content descriptions and strings so the two controls are not presented as one universal source dislike.

Tests:

- Existing v0.6.2 preference tests should still pass.
- Add tests only if behavior changes.

### 3. Source Ordering Persistence Verification

Goal:

Ensure manual source priority order is stable after leaving settings, reopening settings, closing the app, and source/language filtering changes.

Recommended implementation:

- First inspect whether there is an actual bug in current behavior.
- Do not rewrite reorder logic unless a reproducible issue is found.

Areas to verify:

- `setSourceOrder()` writes `sourceOrderPref` immediately after drag movement.
- `RecommendationSourceOrdering.mergeVisibleOrder()` preserves hidden-language IDs.
- `LaunchedEffect(state.orderedSources)` does not clear/reload `sourcesState` while dragging.
- Disabled/disliked sources do not get dropped from stored order.
- New sources are appended after stored sources in a stable order.

Potential issue to check:

The default order for appended new sources comes from `sourceManager.getVisibleCatalogueSources()`. If that order is unstable between app launches, "new/unordered" sources can appear to randomly move. Stored sources should remain stable; only newly appended sources may shift.

Recommended tests:

- Add screen-model or helper tests if dependency seams allow.
- At minimum, expand `RecommendationSourceOrderingTest`:
  - stored order survives disabled source presence,
  - stored order survives language-filtered merge,
  - new sources append without disturbing stored IDs,
  - duplicate IDs in stored order do not create reorder drift,
  - hidden-language IDs remain after visible EN reorder.

Manual verification:

1. Reorder sources.
2. Back out of settings.
3. Reopen settings.
4. Confirm order persists.
5. Close/reopen app.
6. Confirm order persists.
7. Change recommendation language filters.
8. Restore previous language.
9. Confirm order persists.

Profile note:

Current storage appears to be app-level `SourcePreferences`, not a separate multi-profile system. If "profile" means the user's Komikku app preference profile, the current storage path is correct. If a future true multi-profile feature exists, source order would need profile-scoped keys.

## Stage 2: KMK-Recs v0.5.2

### Goal

Improve Love/Like/Dislike other versions matching so it finds more true matches across extensions when titles differ.

### Current Limitation

The workflow searches only the current manga title. This misses cases where another source uses an alternate title, romanized title, Korean/Japanese title, or localized title.

### Recommended Approach

Do not replace the bounded matching workflow with normal global search. The matching workflow needs different behavior:

- recommendation language filtering,
- source priority ordering,
- result cap per source,
- origin filtering before cap,
- default selected candidates,
- manual deselection preservation,
- apply rating to selected candidates.

Instead, extract or align with global-search mechanics while adding a query strategy.

### Query Strategy

Create a small query planner for cross-extension matching:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchQueryPlanner.kt
```

Inputs:

- current manga title,
- original title if different from custom title,
- optional metadata alternate titles when available,
- optional tracker title if already locally available,
- optional user-entered query override in the match screen later.

Output:

- ordered distinct queries,
- capped to a small number, e.g. 3 queries initially.

Initial query sources:

1. `manga.title`
2. `manga.ogTitle` if accessible and different
3. titles from existing metadata alias systems if there is a cheap local API

Do not perform network calls to trackers just to get alternate titles in v0.5.2.

### Result Handling

For each source:

- try queries in order,
- merge results by `(source, url)`,
- filter origin before cap,
- apply per-source cap of 2 after merging/filtering,
- preserve manual deselection keys across query refreshes.

Efficiency guard:

- Keep max query count low.
- Keep source thread pool unchanged or bounded.
- Avoid multiplying source count by too many queries.
- Stop early for a source if high-confidence candidates already found, if a lightweight confidence scorer is added.

Optional confidence scoring:

- Prefer exact normalized title match.
- Then title token overlap.
- Then same author/artist if locally returned.
- Then description similarity only if details are already present locally.

Do not fetch manga details for every candidate in v0.5.2; that would bog down the system.

Tests:

- query planner dedupes repeated titles,
- original title is included when different,
- query count cap is respected,
- origin filter still happens before cap,
- results from multiple queries merge by `(source, url)`,
- manual deselection remains preserved.

Documentation:

- Update `CURRENT_STATE.md`.
- Create `KMK_RECS_V0_5_2_CROSS_EXTENSION_MATCH_QUERY_PLAN.md` or implementation report after coding.
- Keep normal global search documented as uncapped and unchanged.

## Stage 3: KMK-Recs v0.7.0

### Goal

Add an optional Loved Manga view that lists manga rated `LOVE`.

### Product Behavior

The view should show all locally loved manga taste entries in a clean browsing surface.

Possible location:

- Browse tab as a sub-tab or menu item,
- Recommendation Settings link,
- More/settings recommendation tools area.

Recommended first location:

- Browse area, near For You, because this is a user-facing manga list rather than a setting.

### Data Source

Use existing local taste data:

```kotlin
GetMangaTaste.awaitAll()
```

Filter:

```text
rating == MangaRating.LOVE.value
```

Then resolve manga by:

- `mangaId` where available,
- fallback `(source, url)` if needed.

### Duplicate Handling

Conservative duplicate handling only.

Recommended first-pass dedupe:

- group only if normalized title matches exactly AND normalized description/intro matches exactly or near-exactly,
- ignore blank descriptions,
- never title-only dedupe,
- allow a setting/toggle:

```text
Group clear duplicates
```

If grouping is enabled:

- show one representative entry,
- optionally show a small "N versions" badge,
- tapping can open the representative.

If grouping is disabled:

- show every loved entry.

Important:

Do not silently delete or alter taste rows. This is display-only grouping.

### Metadata Limitations

Description may be missing for some locally known manga if details were never initialized. The view should fail open:

- no description means no dedupe,
- show separate entries.

Do not fetch details for all loved manga automatically in v0.7.0 unless explicitly approved.

Tests:

- LOVE only included,
- LIKE/DISLIKE excluded,
- title + same description groups,
- same title + different description does not group,
- blank description does not group,
- grouping disabled shows all entries.

## Implementation Order Recommendation

Recommended order for Claude:

1. Implement v0.6.3 first:
   - bulk install visible suggestions,
   - clarify preference semantics text/labels,
   - add source ordering persistence tests or verification.
2. Then separately implement v0.5.2 matching query improvements.
3. Then separately implement v0.7.0 Loved Manga view.

Reason:

The settings/source work is the most directly connected to the current v0.6.2 implementation. Matching and Loved Manga touch different surfaces and should not be rushed into the same patch unless the user explicitly approves a larger beta build.

## Detailed Implementation Blueprint For Claude

This section is intentionally prescriptive. Claude should use it as the implementation checklist and avoid guessing.

## Blueprint A: KMK-Recs v0.6.3

### A1. Bulk Install Visible Sources To Try

#### Files To Change

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
```

#### Files To Read But Avoid Rewriting

```text
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestion.kt
app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStore.kt
```

These already provide the suggestion list and stable suggestion identity. Do not duplicate this logic.

#### Current Code To Reuse

Current individual install path in `RecommendationsSettingsScreenModel.kt`:

```kotlin
fun installSuggestion(suggestion: NonInstalledSourceSuggestion) {
    screenModelScope.launch {
        extensionManager.installExtension(suggestion.extension).collect { /* system feedback handles progress */ }
    }
}
```

The bulk install action should reuse this same `extensionManager.installExtension(suggestion.extension)` path.

#### Add State

In `RecommendationsSettingsScreenModel.State`, add:

```kotlin
val installingSuggestionKeys: ImmutableSet<String> = persistentSetOf()
val isBulkInstallingSuggestions: Boolean = false
```

Use `suggestion.dismissalKey` as the UI operation key. This is stable enough for current visible suggestions.

Do not store this state in preferences. It is transient UI state only.

#### Add Screen Model Action

Add:

```kotlin
fun installSuggestions(suggestions: List<NonInstalledSourceSuggestion>)
```

Expected behavior:

1. If `suggestions.isEmpty()`, return.
2. If `state.value.isBulkInstallingSuggestions`, return.
3. Build `keys = suggestions.map { it.dismissalKey }.toSet()`.
4. Set:

```kotlin
isBulkInstallingSuggestions = true
installingSuggestionKeys += keys
```

5. Launch a coroutine.
6. Install sequentially, not in parallel:

```kotlin
for (suggestion in suggestions.distinctBy { it.extension.pkgName to it.source?.id }) {
    extensionManager.installExtension(suggestion.extension).collect { }
}
```

7. Wrap each install in `runCatching` or `try/catch` so one failing extension does not abort the entire batch.
8. On completion/finally, remove keys from `installingSuggestionKeys` and set `isBulkInstallingSuggestions = false`.

Important:

- Sequential install is preferred unless Komikku's installer is verified to support parallel extension install safely.
- Do not dismiss suggestions after install. Existing `installedExtensionsFlow` should remove installed suggestions naturally.
- Do not auto-like installed suggestions.
- Do not change source priority order after install.
- Do not install suggestions hidden by collapse/visibility state.

#### Compute Visible Suggestions Once In UI

In `RecommendationsSettingsScreen.kt`, the UI already computes:

```kotlin
val visibleSuggestions = if (state.suggestionsExpanded) state.nonInstalledSuggestions
else state.nonInstalledSuggestions.take(5)
```

Keep this as the source of truth for bulk install.

After the suggestion list and expand/collapse button, add an item:

```kotlin
item(key = "suggestions_install_visible") {
    Button(
        onClick = { screenModel.installSuggestions(visibleSuggestions) },
        enabled = visibleSuggestions.isNotEmpty() && !state.isBulkInstallingSuggestions,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        Text(
            if (state.isBulkInstallingSuggestions) stringResource(KMR.strings.rec_suggestion_installing_visible)
            else stringResource(KMR.strings.rec_suggestion_install_visible, visibleSuggestions.size)
        )
    }
}
```

Exact UI can vary slightly to match code style, but the behavior should not.

#### Disable Individual Install While Installing

In `SourceSuggestionItem`, add:

```kotlin
isInstalling: Boolean
```

Then set:

```kotlin
Button(
    onClick = onInstall,
    enabled = !isInstalling,
)
```

When rendering each suggestion:

```kotlin
val isInstalling = suggestion.dismissalKey in state.installingSuggestionKeys
```

Do not disable Like/Dislike/Dismiss globally unless the implementation finds a real race. It is acceptable to leave them available, but install buttons should not fire repeatedly for the same suggestion.

#### Strings To Add

Add to `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`:

```xml
<string name="rec_suggestion_install_visible">Install visible suggestions (%1$d)</string>
<string name="rec_suggestion_installing_visible">Installing suggestions...</string>
```

If using per-item install state:

```xml
<string name="rec_suggestion_installing">Installing...</string>
```

Use ASCII apostrophes/periods. Avoid smart punctuation.

#### Tests

No Android UI test is required for this pass unless the project already has a convenient Compose test setup.

Recommended unit-testable helper if Claude wants a seam:

```text
app/src/main/java/exh/recs/settings/SuggestionInstallSelection.kt
app/src/test/java/exh/recs/settings/SuggestionInstallSelectionTest.kt
```

Helper:

```kotlin
internal fun uniqueInstallSuggestions(
    suggestions: List<NonInstalledSourceSuggestion>,
): List<NonInstalledSourceSuggestion>
```

Deduplicate by:

```text
extension.signatureHash + "|" + extension.pkgName + "|" + (source?.id ?: "")
```

Tests:

- collapsed visible list passes only visible suggestions from UI call,
- duplicate available source entries are deduped,
- input order is preserved.

Do not overbuild this helper if it creates awkward fake `Extension.Available` fixtures. Manual verification is acceptable for this small UI action.

#### Manual Verification

1. Open Recommendation Settings.
2. Scroll to Sources To Try.
3. Confirm button says `Install visible suggestions (N)`.
4. When collapsed, confirm N is at most 5.
5. Expand suggestions.
6. Confirm N equals all currently visible suggestions.
7. Tap button.
8. Confirm individual install buttons do not repeatedly fire for the same visible suggestions.
9. Confirm installed suggestions disappear naturally after installation.
10. Confirm disliked hidden suggestions are not installed.

### A2. Source Preference Semantics Cleanup

#### Product Decision

Do not create a full new data model in v0.6.3.

Reason:

- v0.6.2 already stores source preference keys in `likedRecommendationSourceKeys()` and `dislikedRecommendationSourceKeys()`.
- Current behavior is useful but needs clearer labels.
- A storage migration for separate `recommendation-quality` and `overall-source-quality` keys is too much churn unless the user explicitly requests it.

#### Files To Change

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
docs/recommendations/CURRENT_STATE.md
docs/recommendations/SOURCE_PREFERENCE_LIKE_DISLIKE_IMPLEMENTATION.md
docs/recommendations/NEXT_WORK.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

#### Behavior To Preserve

Installed source row:

- Like: visual/stored only.
- Dislike: excludes from For You by contributing to `effectiveDisabledIds` in `BrowsePersonalRecommendationsScreenModel.load()`.

Sources To Try row:

- Like: suggestion remains visible and ranks high.
- Dislike: suggestion hidden from Sources To Try.

Dismiss remains separate.

#### Text/Label Changes

Add an explanatory note under the source status note or near source preference controls:

```text
Installed source dislikes affect For You only. Sources To Try dislikes hide future suggestions.
```

Suggested string:

```xml
<string name="rec_source_preference_scope_note">Installed source dislikes affect For You only. Sources To Try dislikes hide future suggestions.</string>
```

Add the note in `RecommendationsSettingsScreen.kt` after `source_status_note` or before Sources To Try.

Current content descriptions use:

```kotlin
KMR.strings.rec_source_preference_like
KMR.strings.rec_source_preference_dislike
```

Replace or supplement with clearer strings:

```xml
<string name="rec_source_preference_like_for_you">Like for For You</string>
<string name="rec_source_preference_dislike_for_you">Dislike for For You</string>
<string name="rec_source_preference_like_source">Like source suggestion</string>
<string name="rec_source_preference_dislike_source">Dislike source suggestion</string>
```

Use the For You versions for installed source rows, and source suggestion versions for Sources To Try rows.

Do not change the stored key format in v0.6.3.

#### What Not To Do

- Do not make installed-source dislike automatically hide non-installed suggestions with similar names.
- Do not make non-installed suggestion dislike disable an installed source later.
- Do not add source-quality-vs-recommendation-quality DB tables.
- Do not rename existing preference methods unless doing so is purely additive and backwards compatible.

#### Tests

Existing behavior tests should still pass.

If adding no behavior, no new unit tests are required, but update documentation to clearly state the distinction.

### A3. Source Ordering Persistence Verification

#### Files To Inspect

```text
app/src/main/java/exh/recs/RecommendationSourceOrdering.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/test/java/exh/recs/RecommendationSourceOrderingTest.kt
```

#### Current Risk Points

1. `sourcesState` is local UI state created with:

```kotlin
val sourcesState = remember { state.orderedSources.toMutableStateList() }
```

2. `LaunchedEffect(state.orderedSources)` refreshes the list only when:

```kotlin
if (!reorderableState.isAnyItemDragging)
```

3. During drag, the code removes/adds by source ID lookup, not raw index:

```kotlin
val fromSourceIndex = sourcesState.indexOfFirst { it.id == fromSourceId }
val toSourceIndex = sourcesState.indexOfFirst { it.id == toSourceId }
if (fromSourceIndex == -1 || toSourceIndex == -1) return
```

This is good. Do not rewrite it unless a bug is found.

#### Required Verification Before Changing Code

Claude should check:

- whether `sourceOrderPref.set(...)` is called on every valid reorder,
- whether `setSourceOrder()` rejects partial/invalid reordered lists,
- whether disabled sources remain in `orderedSources` display and in the stored preference,
- whether disliked installed sources remain in display order but are excluded only in For You,
- whether language-filtered hidden source IDs are preserved by `mergeVisibleOrder()`.

#### Add/Update Tests

Expand `RecommendationSourceOrderingTest.kt` if gaps remain.

Add tests:

```kotlin
@Test
fun `applyAll keeps stored disabled source positions for settings display`() { ... }

@Test
fun `apply appends new visible sources without disturbing stored order`() { ... }

@Test
fun `mergeVisibleOrder preserves hidden ids when visible order changes multiple times`() { ... }

@Test
fun `parse drops malformed ids and preserves first occurrence order`() { ... }
```

If the existing tests already cover part of these, do not duplicate exact tests.

#### Manual Verification Required

Claude should document manual verification steps even if it cannot perform them:

1. Reorder source priority.
2. Leave Recommendation Settings.
3. Reopen Recommendation Settings.
4. Confirm order persisted.
5. Close and reopen app.
6. Confirm order persisted.
7. Disable a source.
8. Confirm it remains in its position in the settings list.
9. Re-enable it.
10. Confirm it returns in the same position.
11. Change recommendation language, then change back.
12. Confirm original language-visible order is preserved.

If a real bug is found:

- prefer fixing `RecommendationSourceOrdering` helper or `setSourceOrder()`,
- avoid adding ad-hoc list mutation logic inside the composable.

## Blueprint B: KMK-Recs v0.5.2

### B1. Alternate-Query Matching For Love/Like/Dislike Other Versions

#### Files To Change

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/test/java/exh/recs/matching/CrossExtensionMatchSelectionTest.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
docs/recommendations/CURRENT_STATE.md
docs/recommendations/KMK_RECS_V0_5_2_CROSS_EXTENSION_MATCH_QUERY_IMPLEMENTATION.md
RECOMMENDATION_VERSIONING.md
```

#### New File To Create

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchQueryPlanner.kt
```

Optional test file:

```text
app/src/test/java/exh/recs/matching/CrossExtensionMatchQueryPlannerTest.kt
```

#### Current Limitation To Fix

Current code:

```kotlin
mutableState.update { it.copy(searchQuery = manga.title) }
search(manga.title)
```

And:

```kotlin
source.getSearchManga(1, query.sanitize(), source.getFilterList())
```

This searches one title only.

#### Query Planner Responsibilities

Create pure helper:

```kotlin
internal object CrossExtensionMatchQueryPlanner {
    const val MAX_QUERIES = 3

    fun buildQueries(manga: Manga): List<String>
}
```

Initial sources:

1. `manga.title`
2. `manga.ogTitle` if different and non-blank
3. Optional extracted title variants from notes/metadata only if already local and cheap

Do not fetch AniList/MAL/tracker data.

Do not call website APIs just to find aliases.

Deduping:

- trim,
- drop blank,
- normalize for comparison with lowercase and non-alphanumeric collapse,
- preserve display/original query string.

Test examples:

- custom title and `ogTitle` same -> one query,
- custom title and `ogTitle` different -> two queries,
- more than 3 candidates -> capped,
- blank titles ignored.

#### CrossExtensionMatchScreenModel Changes

State should include:

```kotlin
val searchQueries: List<String> = emptyList()
```

On init:

```kotlin
val queries = CrossExtensionMatchQueryPlanner.buildQueries(manga)
mutableState.update { it.copy(searchQuery = queries.firstOrNull().orEmpty(), searchQueries = queries) }
search(queries)
```

Change:

```kotlin
private fun search(query: String)
```

to:

```kotlin
private fun search(queries: List<String>)
```

For each source:

1. Iterate through queries.
2. Call:

```kotlin
source.getSearchManga(1, query.sanitize(), source.getFilterList())
```

3. Convert results to domain manga.
4. Merge by URL or `(source, url)`.
5. Filter origin before cap.
6. Take `PER_SOURCE_RESULT_LIMIT`.

Pseudo:

```kotlin
val merged = linkedMapOf<String, Manga>()
for (query in queries) {
    val page = source.getSearchManga(1, query.sanitize(), source.getFilterList())
    val localized = page.mangas
        .map { it.toDomainManga(source.id) }
        .distinctBy { it.url }
        .let { networkToLocalManga(it) }
        .filterNot(::isOrigin)

    localized.forEach { manga -> merged.putIfAbsent(manga.url, manga) }
    if (merged.size >= PER_SOURCE_RESULT_LIMIT) break
}
val titles = merged.values.take(PER_SOURCE_RESULT_LIMIT)
```

Important:

- Preserve current per-source cap of 2.
- Preserve origin-before-cap behavior.
- Preserve selected-by-default behavior.
- Preserve manual deselection.
- Preserve recommendation language filtering and priority ordering.
- Do not change normal global search.

#### Optional UI Copy

In `CrossExtensionMatchScreen`, subtitle currently shows selected count. Keep this.

Optional small body note near progress:

```text
Searching alternate titles
```

Only add this if it does not clutter the screen.

If shown, add string:

```xml
<string name="rec_match_searching_alternate_titles">Searching alternate titles</string>
```

#### Performance Guard

The max number of source calls becomes:

```text
matching source count * max 3 queries
```

To prevent bogging down:

- cap queries at 3,
- keep dispatcher thread pool at 5,
- break early once a source has 2 candidates,
- do not fetch details for every candidate,
- do not call tracker APIs.

#### Tests

Add tests for the pure query planner.

Extend `CrossExtensionMatchSelectionTest` if it has suitable fake-source seams:

- multiple queries merge results,
- origin filtered before cap across merged results,
- duplicate URL from two queries appears once,
- result cap still 2,
- manually deselected candidate stays deselected after another result update.

If fake-source testing is hard, keep planner tests and document manual verification.

#### Manual Verification

1. Open manga with known alternate title.
2. Use Love other versions.
3. Confirm results include matches from current title.
4. Confirm results include matches from original/custom alternate title when available.
5. Confirm at most 2 results per source.
6. Confirm normal global search remains uncapped.

## Blueprint C: KMK-Recs v0.7.0

### C1. Loved Manga View

#### Files Likely To Change

```text
app/src/main/java/exh/recs/LovedMangaScreen.kt
app/src/main/java/exh/recs/LovedMangaScreenModel.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt
app/src/main/java/exh/recs/RecommendsScreen.kt or related Browse navigation file
domain/src/main/java/tachiyomi/domain/taste/interactor/GetMangaTaste.kt
i18n-kmk/src/commonMain/moko-resources/base/strings.xml
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
docs/recommendations/CURRENT_STATE.md
docs/recommendations/KMK_RECS_V0_7_0_LOVED_MANGA_IMPLEMENTATION.md
RECOMMENDATION_VERSIONING.md
```

Claude must inspect actual Browse tab navigation before choosing final file names. Do not guess if `BrowseTab.kt` has changed.

#### Existing APIs To Reuse

`GetMangaTaste.awaitAll()` already exists:

```kotlin
suspend fun awaitAll(): List<MangaTaste> = repository.getAllMangaTastes()
```

`MangaTaste` fields:

```kotlin
val mangaId: Long
val source: Long
val url: String
val title: String
val rating: Int
val createdAt: Long
val updatedAt: Long
```

`MangaRating.LOVE.value == 2`.

`GetManga` can resolve manga by ID:

```kotlin
getMangaInteractor.await(id)
```

#### Screen Model

Create:

```kotlin
class LovedMangaScreenModel(
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<LovedMangaScreenModel.State>(State.Loading)
```

State:

```kotlin
sealed interface State {
    data object Loading : State
    data class Success(
        val entries: List<LovedMangaEntry>,
        val grouped: Boolean,
    ) : State
    data class Error(val throwable: Throwable) : State
}
```

Entry:

```kotlin
data class LovedMangaEntry(
    val representative: Manga,
    val versions: List<Manga>,
    val updatedAt: Long,
)
```

Load:

1. `getMangaTaste.awaitAll()`
2. filter `rating == MangaRating.LOVE.value`
3. sort by `updatedAt DESC`
4. resolve each `mangaId` using `GetManga.await(mangaId)`
5. drop unresolved entries or show fallback title-only item only if existing card UI supports it

Recommended: drop unresolved entries for v0.7.0 and document it.

#### Duplicate Grouping Helper

Create pure helper:

```text
app/src/main/java/exh/recs/LovedMangaDuplicateGrouper.kt
app/src/test/java/exh/recs/LovedMangaDuplicateGrouperTest.kt
```

Grouping rule:

- normalize title exactly,
- normalize description/intro exactly or near-exactly,
- description must be non-blank,
- never group title-only.

Recommended first pass:

```kotlin
fun duplicateKey(manga: Manga): String? {
    val title = normalizeTitle(manga.title)
    val description = normalizeDescription(manga.description.orEmpty())
    if (title.isBlank() || description.length < 40) return null
    return "$title|$description"
}
```

Use `description.length < 40` guard to avoid grouping on tiny generic descriptions.

Near-exact matching can be deferred. Exact normalized title + exact normalized description is safer.

Grouping:

- if key is null, entry stands alone,
- if key repeats, group versions,
- representative should be most recently updated/loved, or first in sorted order.

Do not delete or merge database rows.

#### UI

Use existing manga card/list components if available.

Likely reusable:

```text
eu.kanade.presentation.browse.components.GlobalSearchCardRow
```

But if that component expects horizontal rows by source, a simpler vertical list may be cleaner.

UI requirements:

- title: `Loved Manga`
- empty state: no loved manga yet,
- optional toggle/chip:

```text
Group clear duplicates
```

- if grouped entry has multiple versions, show badge/subtitle:

```text
3 versions
```

Tapping representative should open the manga detail screen using existing navigation patterns. Claude must inspect existing navigation from Browse/For You card clicks and reuse it.

#### Strings

Add:

```xml
<string name="rec_loved_manga_title">Loved Manga</string>
<string name="rec_loved_manga_empty">No loved manga yet.</string>
<string name="rec_loved_manga_group_duplicates">Group clear duplicates</string>
<string name="rec_loved_manga_versions">%1$d versions</string>
```

#### Tests

`LovedMangaDuplicateGrouperTest`:

- same title + same nonblank description groups,
- same title + different description does not group,
- same title + blank description does not group,
- different title + same description does not group,
- grouping preserves most-recent representative.

Screen model tests optional unless existing test infrastructure makes it straightforward.

#### Manual Verification

1. Love several manga.
2. Open Loved Manga.
3. Confirm only Love entries appear.
4. Confirm Like/Dislike entries do not appear.
5. Confirm duplicate grouping can be toggled.
6. Confirm title-only similar entries are not grouped.
7. Confirm tapping opens manga.

## Documentation Requirements For Any Implementation Pass

Claude must create or update implementation reports based on what it actually implements.

If implementing only v0.6.3:

```text
docs/recommendations/KMK_RECS_V0_6_3_SETTINGS_POLISH_IMPLEMENTATION.md
```

If implementing only v0.5.2:

```text
docs/recommendations/KMK_RECS_V0_5_2_CROSS_EXTENSION_MATCH_QUERY_IMPLEMENTATION.md
```

If implementing only v0.7.0:

```text
docs/recommendations/KMK_RECS_V0_7_0_LOVED_MANGA_IMPLEMENTATION.md
```

Each implementation report must include:

- version,
- what changed,
- files changed,
- tests run,
- build run,
- APK name if created,
- known limitations,
- anything intentionally deferred.

Also update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Do not claim tests or APKs exist unless they were actually run/built.

## Summary For Approval

This plan recommends:

- **Yes** to bulk installing Sources To Try, starting with an "Install visible suggestions" button rather than long-press multi-select.
- **Yes** to clarifying source dislike semantics, but avoid making For You dislike automatically mean "dislike this source everywhere."
- **Yes** to ordering verification, with tests/manual checks before rewriting the drag implementation.
- **Yes** to a Loved Manga view, but as a separate v0.7.0 feature with conservative display-only duplicate grouping.
- **Yes** to improving other-version matching, but as a v0.5.2 patch that adds a bounded alternate-query planner instead of blindly expanding every source search.

