# KMK-Recs v0.7.3: Loved Manga Installed Source Filter Plan

Date: 2026-06-21

Status: implementation plan, awaiting user approval before coding

Feature version: `KMK-Recs v0.7.3`

Expected APK name: `Komikku-v1.13.6-kmk.7.3-debug.apk`

## Summary

The Loved Manga view currently loads every `MangaRating.LOVE` taste row. That can include manga from sources/extensions that are no longer installed. The user requested that the Loved Manga list should not include sources that are not installed.

This should be a display filter only:

- keep the stored taste rows;
- keep cross-source link rows;
- hide loved entries whose source is not currently available as an installed/visible catalogue source;
- if the source is reinstalled later, the loved entry can appear again.

This is a focused Loved Manga stabilization pass. It should not change recommendation scoring, source evaluation, cross-extension matching, or taste storage semantics.

## Current Behavior

Relevant file:

```text
app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt
```

Current loading flow:

```text
GetMangaTaste.awaitAll()
-> filter rating == LOVE
-> sort by updatedAt descending
-> resolve each manga through GetManga
-> load cross-source link groups
-> show all loved entries
```

Problem:

- no installed-source filtering happens before display;
- a stale loved taste row can appear even when its source/extension is no longer installed;
- unresolved manga can still appear using fallback `MangaTaste.title`, even when the source is gone.

## Goals

1. Hide Loved Manga entries whose source is not currently installed/available.
2. Do not delete taste rows for uninstalled-source manga.
3. Do not delete cross-source link rows for uninstalled-source manga.
4. Keep grouped duplicate behavior from v0.7.2 intact.
5. Keep sorting by most recently loved.
6. Fail safely if source lookup fails.
7. Add tests or a pure helper seam for installed-source filtering.
8. Update docs and What's New.

## Non-Goals

- Do not remove loved taste rows for uninstalled sources.
- Do not remove link-group rows for uninstalled sources.
- Do not change For You filtering.
- Do not change cross-extension matching.
- Do not change source evaluation.
- Do not add a full source management UI.
- Do not fetch extension metadata over the network just to render Loved Manga.

## Required Behavior

### Installed Source Filter

Loved Manga should show only entries whose `taste.source` exists in the currently installed/visible catalogue sources.

Recommended source of truth:

```kotlin
SourceManager.getVisibleCatalogueSources()
```

or the closest existing installed catalogue source list used elsewhere in recommendations.

Build:

```kotlin
val installedSourceIds = sourceManager.getVisibleCatalogueSources()
    .map { it.id }
    .toSet()
```

Then filter:

```kotlin
val lovedTastes = allTastes
    .filter { it.rating == MangaRating.LOVE.value }
    .filter { it.source in installedSourceIds }
    .sortedByDescending { it.updatedAt }
```

Claude must inspect whether `getVisibleCatalogueSources()` excludes Local Source and disabled/hidden sources. The requirement is specifically **installed source**, not necessarily enabled-for-recommendations source. If a source is installed but disabled from recommendations, it can still appear in Loved Manga unless the user later requests a separate filter.

### Local Source

Decision for this pass:

- Do not special-case Local Source unless current source lookup includes it.
- If Local Source id `0` is not part of installed catalogue sources, Loved Manga should hide it under the same rule.
- If the user later wants local loved manga shown, that should be a separate product decision.

### Cross-Source Link Groups

Grouping should happen after installed-source filtering.

Reason:

- A group may have three versions, but only one source is still installed.
- The Loved Manga view should show only installed-source entries.
- `versionCount` should count only currently visible installed-source members, not hidden uninstalled members.

Example:

```text
Group A:
- MangaFire version, installed
- OldSource version, uninstalled

Display:
- one MangaFire card
- versionCount = 1
```

Do not remove the link row for `OldSource`. If the extension is reinstalled later, that member can reappear.

### Empty State

If all loved manga are from uninstalled sources:

- show the normal empty state or a slightly clearer empty message if easy;
- do not show an error.

Optional clearer message:

```text
No loved manga from installed sources.
```

If adding this string feels too much for the pass, use the existing empty state.

## Implementation Guidance

### 1. Add SourceManager Dependency

File:

```text
app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt
```

Inject:

```kotlin
private val sourceManager: SourceManager = Injekt.get()
```

Import:

```kotlin
tachiyomi.domain.source.service.SourceManager
```

Use it during `load()`.

### 2. Add Pure Helper If Practical

Preferred helper:

```text
app/src/main/java/exh/recs/loved/LovedMangaSourceFilter.kt
```

Suggested function:

```kotlin
internal fun filterLovedTastesByInstalledSources(
    tastes: List<MangaTaste>,
    installedSourceIds: Set<Long>,
): List<MangaTaste>
```

Behavior:

- keep only `rating == MangaRating.LOVE.value`;
- keep only `source in installedSourceIds`;
- preserve input order or sort outside the helper.

Alternative:

- if helper feels unnecessary, implement inline but add tests around a builder function if available.

### 3. Keep Link Group Map Independent

Do not filter the loaded link map itself. It is fine to load all links and pass `linkGroupId` only for visible entries.

Because entries are filtered before grouping:

- hidden uninstalled entries will not become group members;
- visible installed entries can still use `linkGroupId` to group with other installed loved entries.

### 4. Fail-Safe Behavior

If `SourceManager.getVisibleCatalogueSources()` throws or returns empty unexpectedly:

- prefer showing an empty state rather than crashing;
- document exact behavior in implementation report.

Do not fail open by showing all sources if the purpose of this pass is to hide uninstalled sources. If source lookup fails, safer behavior is to show no entries and log/document the condition.

## Tests

Add or update tests, preferably with a pure helper:

```text
app/src/test/java/exh/recs/loved/LovedMangaSourceFilterTest.kt
```

Required cases:

- keeps LOVE entries whose source is installed;
- removes LOVE entries whose source is not installed;
- removes LIKE/DISLIKE even if source is installed;
- preserves entries when multiple installed source IDs exist;
- returns empty list when installed source set is empty;
- does not mutate input list;
- filtering happens before grouping, so versionCount counts visible installed entries only.

If testing screen-model flow is practical:

- fake `SourceManager` with installed source IDs;
- fake `GetMangaTaste`;
- confirm state entries exclude uninstalled sources.

If screen-model testing is awkward, document manual verification and rely on pure helper tests.

## Manual Verification

1. Love manga from an installed source.
2. Confirm it appears in Loved Manga.
3. Uninstall that source/extension.
4. Reopen Loved Manga.
5. Confirm the manga no longer appears.
6. Reinstall the source/extension.
7. Reopen Loved Manga.
8. Confirm the loved manga can appear again if its taste row still exists.
9. Confirm grouped duplicates only count visible installed-source entries.

## Documentation Requirements

After implementation, create:

```text
docs/recommendations/KMK_RECS_V0_7_3_LOVED_MANGA_INSTALLED_SOURCE_FILTER_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Documentation must state:

- Loved Manga hides loved entries from uninstalled sources.
- This is display filtering only.
- Taste rows and link rows are preserved.
- Reinstalling a source can make hidden loved entries visible again.
- Version counts in grouped entries count visible installed-source members only.

## What's New

User-facing entry:

```text
- Loved Manga now hides entries from sources that are no longer installed.
```

Do not mention internal source IDs, repository internals, or documentation work in What's New.

## Recommendation

Proceed as a small v0.7.3 pass after approval. This is low-risk and should be implemented before adding more Loved Manga sorting or management features, because it keeps the list aligned with what the user can actually open/use on the device.


