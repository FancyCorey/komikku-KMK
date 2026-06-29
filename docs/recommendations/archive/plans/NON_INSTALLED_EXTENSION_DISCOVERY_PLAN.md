# Non-Installed Extension Discovery Implementation Plan

Date: 2026-06-16

Status: planning only. Do not implement until the user approves this plan and requests a Claude Code prompt.

## Purpose

Add a recommendation-adjacent feature that suggests **non-installed extensions/sources worth trying** based on the user's recommendation preferences and currently available extension repository metadata.

This feature should not claim that an uninstalled extension is definitely good. The app cannot search a non-installed extension's live catalog. It can only inspect metadata from the extension repository until the user installs the extension.

The intended user-facing meaning is:

```text
These uninstalled sources look worth trying for recommendations.
```

Not:

```text
These uninstalled sources definitely contain the best manga for you.
```

## Core Product Goal

Help the user discover extensions that may improve Browse > For You recommendations without manually scrolling through hundreds of available extensions.

The feature should:

- inspect available but not installed extension/source metadata,
- respect recommendation language preferences,
- avoid NSFW sources unless the app is configured to show them,
- exclude already installed/untrusted/disabled/unavailable candidates,
- rank candidates conservatively,
- explain why each source is suggested,
- let the user install a suggestion through the existing extension installer,
- optionally mark an installed suggestion for later real evaluation through For You.

## Non-Goals

Do not implement:

- automatic installation,
- background crawling of extension websites,
- searching non-installed extensions,
- source-specific website scrapers,
- a universal "perfect extension finder,"
- automatic source priority reordering,
- a new extension repository system,
- changes to normal global search,
- changes to source migration behavior,
- favorite/rating sync changes.

## Current Code Facts

### Available Extensions Already Exist

Relevant files:

```text
app/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt
app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt
app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt
app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt
```

`Extension.Available` includes:

- `name`
- `pkgName`
- `versionName`
- `versionCode`
- `libVersion`
- `lang`
- `isNsfw`
- `signatureHash`
- `repoName`
- `sources`
- `apkName`
- `iconUrl`
- `repoUrl`

Each `Extension.Available.Source` includes:

- `id`
- `lang`
- `name`
- `baseUrl`

This is enough for metadata-based suggestions.

### Available Extensions Cannot Be Searched Before Install

The available extension model stores stub source data, not executable source implementations. Searching requires an installed extension APK loaded as real `CatalogueSource`.

Therefore, the feature cannot evaluate actual search quality before install.

### Existing Install Path Should Be Reused

`ExtensionManager.installExtension(extension: Extension.Available)` already handles download/install flow.

`ExtensionsScreenModel.installExtension()` already wraps this and tracks `InstallStep`.

The discovery feature should reuse this path rather than creating a new installer.

### Existing Recommendation Source Fit Research

Relevant research:

```text
docs/recommendations/INSTALLED_SOURCE_FIT_FEASIBILITY_RESEARCH.md
```

Installed-source fit learning is feasible because installed sources can be searched and observed. Non-installed source discovery should eventually feed into that system after install.

## Required UX Distinction

The app should separate:

### Installed Source Fit

Reliable local learning from real For You runs.

Example:

```text
Asura Scans is a strong fit because it often contributes high-scoring Top Picks.
```

### Non-Installed Extension Discovery

Lower-confidence metadata-based suggestion.

Example:

```text
Reaper Scans may be worth trying because it is an English source and resembles sources that work well for you.
```

The UI should use wording such as:

- "Suggested to try"
- "Potential fit"
- "Install and test"
- "Not enough data yet"

Avoid wording such as:

- "Best source"
- "Perfect match"
- "Guaranteed recommendation source"

## Proposed Feature Name

Recommended user-facing name:

```text
Sources To Try
```

Alternative:

```text
Suggested Extensions
```

Avoid "Best Sources" for non-installed candidates because quality is unconfirmed until install.

## Data Model

### Domain Model

Add a lightweight model near recommendation code, for example:

```text
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestion.kt
```

Suggested fields:

```kotlin
data class NonInstalledSourceSuggestion(
    val extension: Extension.Available,
    val source: Extension.Available.Source?,
    val score: Double,
    val confidence: SuggestionConfidence,
    val reasons: List<NonInstalledSuggestionReason>,
)
```

`source` may be null only if an available extension has no source entries. Prefer source-level suggestions when `sources` exists.

```kotlin
enum class SuggestionConfidence {
    LOW,
    MEDIUM,
}
```

Do not use `HIGH` before installation. High confidence should be reserved for installed-source fit learning.

Suggested reasons:

```kotlin
sealed interface NonInstalledSuggestionReason {
    data class LanguageMatch(val lang: String) : NonInstalledSuggestionReason
    data class SimilarToGoodInstalledSource(val sourceName: String) : NonInstalledSuggestionReason
    data class MatchesPreferredSourceKeyword(val keyword: String) : NonInstalledSuggestionReason
    data class SameRepoAsTrustedSources(val repoName: String) : NonInstalledSuggestionReason
    data object NeedsTesting : NonInstalledSuggestionReason
}
```

Keep reasons explainable and avoid hidden magic.

## Inputs

### Extension Metadata

Use:

```kotlin
ExtensionManager.availableExtensionsFlow
ExtensionManager.installedExtensionsFlow
ExtensionManager.untrustedExtensionsFlow
```

or:

```kotlin
GetExtensionsByType.subscribe()
```

`GetExtensionsByType` already:

- filters installed/untrusted from available,
- respects enabled source languages,
- respects NSFW display preference,
- expands available multi-source extensions into per-source entries.

However, recommendation discovery should likely use **recommendation language preferences**, not general source enabled languages, because For You uses recommendation-specific language filtering.

Relevant preference:

```kotlin
SourcePreferences.recommendationSourceLanguages()
```

### User Taste Profile

Use:

```kotlin
GetTasteProfile
GetTagAliases
```

Pre-install metadata rarely contains genres, so profile tags cannot directly prove fit. They can still help indirectly when source names/base URLs contain recognizable category terms.

### Installed Source Fit

Best future input:

```text
Installed Source Fit stats
```

This does not exist yet as a persistent feature. If non-installed discovery is implemented before source-fit stats, it should use only weaker heuristics and label confidence as low.

If source-fit stats are implemented first, use them to find names/hosts/repos/categories of sources that already work well.

## Candidate Filtering

Start from available extensions/sources.

Exclude:

- already installed package/signature pairs,
- untrusted package/signature pairs,
- disabled repos,
- languages outside `recommendationSourceLanguages`,
- NSFW if NSFW sources are hidden,
- Local Source equivalent, if any,
- blacklisted extensions already filtered by `ExtensionManager`,
- extensions with no useful source metadata unless extension-level suggestion is still meaningful.

For multi-source extensions, score each source separately where possible.

## Scoring Strategy

### Important Rule

Never score non-installed sources as strongly as installed sources.

Recommended score range:

```text
0.0 - 0.69 for non-installed suggestions
0.70+ reserved for installed-source fit after real runs
```

This prevents untested suggestions from outranking proven installed sources.

### Base Signals

Language match:

```text
+0.20 if source.lang is in recommendation languages
```

Same repo as currently useful installed sources:

```text
+0.05 to +0.10
```

Source name/base URL resembles a high-fit installed source:

```text
+0.10 to +0.20
```

Source name contains manually curated broad content keywords:

```text
+0.05 to +0.15
```

Examples of broad keywords could include:

- `scans`
- `manhwa`
- `manga`
- `webtoon`
- `comics`

But be careful: these are weak signals and should not dominate.

Available source has clear base URL:

```text
+0.05
```

Source is same language and same broad source family as an installed source the user likes:

```text
+0.10
```

### Negative Signals

NSFW hidden:

```text
exclude
```

Language mismatch:

```text
exclude by default
```

Source/extension was dismissed by user:

```text
exclude or strongly penalize
```

Source was installed before, tested, and produced poor source-fit stats:

```text
-0.20 to -0.40
```

Source name/base URL resembles sources with poor fit:

```text
-0.05 to -0.15
```

### Confidence

Before install:

- `LOW` by default.
- `MEDIUM` only if multiple weak signals agree.

After install and at least a few For You runs:

- hand off to Installed Source Fit scoring.

## Source Family Matching

Add small pure helpers:

```text
normalizeSourceName(name: String): String
sourceHostKey(baseUrl: String): String?
sourceKeywordTokens(name: String, baseUrl: String): Set<String>
similarSourceNameScore(a: String, b: String): Double
```

Use conservative matching:

- exact or near-exact source-name token overlap,
- same domain family,
- same repo family,
- avoid fuzzy matching that treats unrelated sources as equivalent.

Do not create source-specific rules for hundreds of sources.

## Persistence

### Minimum Viable Persistence

Use preferences for user-dismissed suggestions:

```kotlin
SourcePreferences.dismissedNonInstalledRecommendationSources()
```

Compact serialized format:

```text
signatureHash|pkgName|sourceId;...
```

This prevents dismissed suggestions from returning immediately.

### Optional Test State

If implementing install-and-test state:

```kotlin
SourcePreferences.pendingRecommendationSourceEvaluations()
```

Format:

```text
sourceId|installedAt|pkgName|sourceName
```

After the source appears in For You statuses/source-fit stats, mark it as evaluated.

### Avoid Full DB Initially

Do not add SQLDelight tables for v1 unless source-fit stats already require them.

If source-fit learning later uses DB tables, non-installed suggestion dismissal/test state can be migrated into the same area.

## UI Placement

### Recommended Placement

Add to Recommendation Settings, not Browse > For You initially.

Suggested section:

```text
Sources To Try
```

Each row:

```text
Source Name
EN · Repo Name · Potential fit
Reason: Similar to sources that work well for you
[Install]
```

Actions:

- Install
- Dismiss
- Search in Extensions

### Optional Browse > For You Surface

Later, Browse > For You could show a small empty/low-results prompt:

```text
Want more recommendations? Try 3 suggested sources.
```

Do not add this first unless the settings implementation is stable.

## Install Flow

Use existing:

```kotlin
ExtensionManager.installExtension(extension)
```

or route through a screen model that mirrors `ExtensionsScreenModel.installExtension()`.

Important issue:

`GetExtensionsByType` may copy available extensions per source and change `pkgName` to `"${ext.pkgName}-${source.id}"` for UI grouping. Installing likely needs the original package identity, not the synthetic per-source pkgName.

Therefore, the suggestion model should retain:

- original `Extension.Available`,
- selected `Extension.Available.Source`,
- original `pkgName`,
- original `signatureHash`.

Do not pass a synthetic copied extension with modified `pkgName` into `installExtension()` unless existing Extensions screen already safely does so. Verify before implementation.

## Post-Install Evaluation

After install:

1. Mark source as pending evaluation.
2. Do not automatically put it at the top of recommendation priority.
3. Optionally offer:

```text
Add to recommendation priority?
```

4. On the next For You refresh, let normal source status/source-fit systems evaluate it.
5. If it produces useful results, suggest keeping or boosting it.
6. If it produces no matches/errors repeatedly, suggest deprioritizing or uninstalling only as an informational prompt.

## Interaction With Installed Source Fit

Recommended dependency order:

1. Implement Installed Source Fit stats first.
2. Then implement Non-Installed Extension Discovery using those stats.

If this feature is implemented before Installed Source Fit, keep it as low-confidence metadata discovery only.

Best long-term flow:

```text
For You runs -> installed source fit stats -> profile of good source families
available extensions metadata -> Sources To Try suggestions
install source -> For You evaluates it -> source fit confirmed or downgraded
```

## Architecture Proposal

### New Package

```text
app/src/main/java/exh/recs/discovery/
```

Suggested files:

```text
NonInstalledSourceSuggestion.kt
NonInstalledSourceSuggestionScorer.kt
NonInstalledSourceSuggestionStore.kt
GetNonInstalledSourceSuggestions.kt
```

### Screen Model

Either extend existing recommendation settings model:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
```

or add:

```text
app/src/main/java/exh/recs/discovery/SourceDiscoveryScreenModel.kt
```

Recommendation: start inside Recommendation Settings to reduce navigation/UI scope.

### UI

Update:

```text
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
```

Add a collapsible or compact section under source priority/status.

## Testing Plan

Add pure unit tests for scoring/filtering.

Suggested test file:

```text
app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt
```

Test cases:

1. Installed extensions are excluded.
2. Untrusted extensions are excluded.
3. Language mismatch is excluded.
4. NSFW sources are excluded when NSFW display is disabled.
5. Dismissed suggestions are excluded.
6. Language match creates a low-confidence suggestion.
7. Similarity to a high-fit installed source increases score.
8. Poor prior evaluation lowers score.
9. Suggestions are sorted by score then name.
10. No suggestion receives high confidence before install.

If UI state model is added, test:

- install action calls installer path with original extension identity,
- dismiss action persists suggestion key,
- refresh recomputes suggestions.

## Documentation Requirements

Create implementation report when implemented:

```text
docs/recommendations/NON_INSTALLED_EXTENSION_DISCOVERY_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Document clearly:

- suggestions are metadata-based before install,
- installed-source fit is stronger than non-installed suggestions,
- no non-installed source is searched,
- install uses existing extension installation flow,
- user can dismiss suggestions.

## Suggested Versioning

If implemented after v0.5.1, use the next available KMK-Recs feature version, likely:

```text
KMK-Recs v0.6.0
```

Reason: this is a new user-visible feature category, not a small bugfix.

Expected APK naming:

```text
Komikku-v1.13.6-kmk.6.0-debug.apk
Komikku-v1.13.6-kmk.6.0-release.apk
```

Only claim an APK after building/copying it.

## Risks

### Weak Pre-Install Evidence

Available metadata does not prove catalog quality.

Mitigation:

- call suggestions "Potential fit,"
- use low/medium confidence only,
- install-and-test after user action.

### Synthetic Available Extension Identity

`GetExtensionsByType` may create per-source `Extension.Available` copies with modified `pkgName`.

Mitigation:

- retain original extension identity separately,
- verify install path before coding,
- add tests or manual verification for install.

### UI Noise

Too many suggestions could overwhelm the user.

Mitigation:

- show top 5 by default,
- allow "show more,"
- support dismiss.

### User Trust

Wrong suggestions can reduce trust in recommendations.

Mitigation:

- show reasons,
- state "needs testing",
- never auto-install or auto-prioritize.

### Maintenance Burden

Source-specific rules do not scale.

Mitigation:

- avoid per-source hardcoding,
- use generic metadata and learned source-fit stats,
- optional curated source profiles only later.

## Manual Verification Checklist

After implementation:

1. Open Recommendation Settings.
2. Confirm "Sources To Try" appears only when available suggestions exist.
3. Confirm installed sources do not appear as suggestions.
4. Confirm language filter works with recommendation language set to EN.
5. Confirm NSFW hidden preference is respected.
6. Dismiss a suggestion and confirm it stays hidden.
7. Tap Install and confirm existing extension install flow starts.
8. After install, confirm the source disappears from non-installed suggestions.
9. Refresh extensions and confirm suggestions update.
10. Confirm normal Extensions screen still works.
11. Confirm Browse > For You behavior is unchanged unless the user installs/enables a suggested source.

## Recommended Implementation Order

### Phase 1: Helper Models And Pure Scorer

Create models and scorer with tests.

No UI yet.

### Phase 2: Suggestion Store

Add dismissed suggestion persistence and tests.

### Phase 3: Settings Screen Integration

Show top suggestions in Recommendation Settings.

Add install/dismiss actions.

### Phase 4: Install-And-Test State

Optional. Mark installed suggestions as pending evaluation.

### Phase 5: Source Fit Integration

If Installed Source Fit exists, use it to improve suggestions and post-install confirmation.

## Final Recommendation

This feature is feasible, but it should be implemented as:

```text
low-confidence non-installed source discovery + user install + real post-install evaluation
```

It should not be implemented as:

```text
automatic best extension finder
```

The most responsible path is to implement Installed Source Fit first, then use it to make non-installed suggestions smarter. If the user wants non-installed suggestions immediately, build a conservative metadata-only version and clearly label it as "Sources To Try."

