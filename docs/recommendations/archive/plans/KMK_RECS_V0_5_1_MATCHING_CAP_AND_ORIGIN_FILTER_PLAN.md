# KMK-Recs v0.5.1 Matching Cap And Origin Filter Plan

Date: 2026-06-16

Status: implementation plan only. Do not implement until the user explicitly approves and requests coding.

Target feature version: `KMK-Recs v0.5.1`

Expected APK naming after implementation:

```text
Komikku-v1.13.6-kmk.5.1-debug.apk
Komikku-v1.13.6-kmk.5.1-release.apk
```

## Purpose

Clean up the v0.5.0 cross-extension rating matching workflow after the requested result-limit change from 5 results per source to 2 results per source.

The code already changed the matching cap to 2 in `CrossExtensionMatchScreenModel.PER_SOURCE_RESULT_LIMIT`, but the implementation is incomplete in two ways:

- the origin manga can still appear in the matching list and be manually selected;
- project bookkeeping is inconsistent because release notes say `KMK-Recs v0.5.1` while current docs still say `v0.5.0`.

This patch should be small, focused, and correction-oriented. It must not add favorite mode, cross-source link groups, backup fields, or unrelated recommendation features.

## Current Baseline

Current implemented behavior from assessment:

- `CrossExtensionMatchScreenModel.PER_SOURCE_RESULT_LIMIT = 2`.
- `CrossExtensionMatchSelectionTest` includes a test for the cap constant.
- `KmkRecsReleaseNotes.VERSION_CODE = 501`.
- `KmkRecsReleaseNotes.VERSION_NAME = "KMK-Recs v0.5.1"`.
- Root APK handoff folder currently has `Komikku-v1.13.6-kmk.5.0-debug.apk`, not a versioned v0.5.1 APK.
- `CURRENT_STATE.md` still documents current version as `KMK-Recs v0.5.0` and `VERSION_CODE = 500`.
- No v0.5.1 implementation report exists yet.

Relevant files:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt
app/src/test/java/exh/recs/matching/CrossExtensionMatchSelectionTest.kt
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
```

## Approved Scope

### Phase 1: Make origin manga truly excluded

Current problem:

- `updateItem()` excludes the origin manga from auto-selection.
- It does not exclude the origin manga from `MatchItemResult.Success`.
- Therefore the origin can still appear in the UI and be manually selected with `toggleSelection()`.

Desired behavior:

- The origin manga should not appear as a selectable/displayed candidate.
- The origin manga should not count toward `totalCandidates`.
- The origin manga should not consume one of the 2 source result slots.
- `toggleSelection()` should defensively ignore the origin key even if a future UI path tries to select it.

Recommended implementation:

1. Add a helper in `CrossExtensionMatchScreenModel`:

```kotlin
private fun isOrigin(manga: Manga): Boolean {
    val origin = originManga ?: return false
    return manga.source == origin.source && manga.url == origin.url
}
```

2. Filter origin before storing the success result:

```kotlin
val titles = page.mangas
    .map { it.toDomainManga(source.id) }
    .distinctBy { it.url }
    .filterNot { smanga -> source.id == origin.source && smanga.url == origin.url }
    .take(PER_SOURCE_RESULT_LIMIT)
    .let { networkToLocalManga(it) }
```

The exact pre-local conversion syntax may need adjustment because `originManga` is a domain `Manga` while the list before `networkToLocalManga()` is domain manga created from source results. The key requirement is:

```text
origin exclusion must happen before the 2-result cap whenever feasible.
```

If source/url comparison is only reliable after `networkToLocalManga()`, then implement:

```kotlin
.let { networkToLocalManga(it) }
.filterNot(::isOrigin)
.take(PER_SOURCE_RESULT_LIMIT)
```

This is less efficient, but still behaviorally correct. Prefer pre-cap/pre-local filtering if safe.

3. Store only filtered candidates in `MatchItemResult.Success`.

4. Update `toggleSelection()`:

```kotlin
fun toggleSelection(key: MangaIdentityKey) {
    val origin = originManga
    if (origin != null && key.source == origin.source && key.url == origin.url) return
    ...
}
```

Acceptance criteria:

- Origin manga never appears in the matching screen candidate list.
- Origin manga never counts toward "Selected N of M".
- Origin manga cannot be selected manually.
- If a source returns `[origin, matchA, matchB]`, the screen can still show `matchA` and `matchB` for that source.

### Phase 2: Keep 2-result cap scoped to matching workflow only

Current behavior:

- `CrossExtensionMatchScreenModel.PER_SOURCE_RESULT_LIMIT = 2`.
- `SearchScreenModel.perSourceResultLimit` defaults to `null`.
- Normal global search should remain uncapped.

Desired behavior:

- Keep cap at 2 for cross-extension matching.
- Keep normal global search uncapped.
- Do not introduce a user setting for the cap in this patch.
- Do not modify normal global search UI or source behavior.

Implementation guidance:

- Leave `SearchScreenModel.perSourceResultLimit` default as `null`.
- Leave `GlobalSearchScreenModel` without an override.
- Keep the matching workflow separate from normal global search.
- If the cap is implemented only in `CrossExtensionMatchScreenModel`, that is acceptable.

Acceptance criteria:

- Cross-extension matching returns at most 2 visible candidates per source.
- Normal global search still returns however many results the existing implementation allows.

### Phase 3: Strengthen tests

Update `CrossExtensionMatchSelectionTest` or add a new focused helper test file.

Required tests:

1. `PER_SOURCE_RESULT_LIMIT` is 2.
2. Origin key is excluded from auto-selection.
3. Origin key is excluded before cap:

```text
input: [origin, a, b, c], cap=2
output: [a, b]
```

4. Manual deselection remains preserved after result updates.
5. Toggling origin key is ignored defensively.
6. Normal global search cap default remains null if testable without heavy dependencies.

Recommended helper extraction:

Move pure selection/filtering logic into internal helpers that can be unit-tested without constructing a full screen model.

Possible helper:

```kotlin
internal fun filterMatchingCandidates(
    candidates: List<Manga>,
    originKey: MangaIdentityKey,
    limit: Int,
): List<Manga>
```

or, for easier tests:

```kotlin
internal fun filterCandidateKeys(
    candidates: List<MangaIdentityKey>,
    originKey: MangaIdentityKey,
    limit: Int,
): List<MangaIdentityKey>
```

Keep helpers small and close to the matching package.

### Phase 4: Fix documentation and versioning

Since `KmkRecsReleaseNotes` already says v0.5.1 and the cap change is user-visible, treat this as a real `KMK-Recs v0.5.1` patch.

Create:

```text
docs/recommendations/KMK_RECS_V0_5_1_MATCHING_CAP_AND_ORIGIN_FILTER_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Required documentation corrections:

- `CURRENT_STATE.md`
  - current version becomes `KMK-Recs v0.5.1`;
  - current APK handoff should become `Komikku-v1.13.6-kmk.5.1-debug.apk` only after that APK is actually built/copied;
  - `KmkRecsReleaseNotes.VERSION_CODE` becomes/continues `501`;
  - cross-extension matching states the cap is 2 and origin results are excluded before display/selection.
- `NEXT_WORK.md`
  - remove any implication that v0.5.1 is still only future work if this patch is completed;
  - future favorite/link-group work should move to `v0.5.2` or "future" unless intentionally still named v0.5.1 before implementation.
- `README.md`
  - move v0.5.1 plan/report to implemented plans after implementation;
  - do not leave the active plan marked as pending.
- `RECOMMENDATION_VERSIONING.md`
  - add a `KMK-Recs v0.5.1` section;
  - record files changed, tests run, and APK name if built.
- `KmkRecsReleaseNotes.kt`
  - keep `VERSION_CODE = 501`;
  - keep `VERSION_NAME = "KMK-Recs v0.5.1"`;
  - keep the What's New entry user-facing only.

Important:

Do not claim a v0.5.1 APK exists until it is actually built and copied to the root handoff path.

### Phase 5: Build and APK handoff naming

After code/docs fixes, run:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If successful, copy or rename the universal debug APK to:

```text
C:\Users\USER\Downloads\Komikku\Komikku-v1.13.6-kmk.5.1-debug.apk
```

Then update docs that mention current APK handoff.

## Explicitly Deferred

Do not implement in this patch:

- Favorite other versions.
- Cross-source link groups.
- Backup/restore/sync for cross-source link groups.
- Link other versions.
- Any new recommendation scoring.
- Source quality learning.
- Pull-to-refresh.
- Normal global search changes.

These should remain future work after v0.5.1 unless separately approved.

## Risk Notes

### Origin filtering order

The most important behavioral fix is filtering origin before the cap. If the origin is removed after the cap, it can still consume one of the two slots and reduce useful matches.

### Normal global search regression

Do not touch normal global search behavior except for preserving the existing null-default cap hook if needed. Manual verification should include normal global search.

### Documentation truthfulness

Do not let docs say v0.5.1 is current unless release notes, implementation report, tests, and APK handoff are aligned.

### Scope creep

Favorite mode and link groups are valuable but larger. This patch is about making v0.5.0/v0.5.1 coherent and safer.

## Required Validation

Run:

```text
./gradlew :app:testDebugUnitTest
```

If practical:

```text
./gradlew :app:assembleDebug
```

Focused tests:

```text
./gradlew :app:testDebugUnitTest --tests "*CrossExtensionMatch*"
```

Manual verification:

1. Open a manga detail page.
2. Tap `Rate`.
3. Tap `Love other versions`.
4. Confirm each source row shows no more than 2 candidates.
5. Confirm the current/origin manga does not appear in the candidate list.
6. Confirm all non-origin candidates are selected by default.
7. Deselect a candidate and confirm it stays deselected while other rows load.
8. Confirm normal global search still behaves normally and is not capped to 2.
9. Confirm KMK What's New shows v0.5.1 user-facing notes only.

## Summary For Claude

Implement KMK-Recs v0.5.1 as a small correction patch for the cross-extension matching workflow:

- keep matching cap at 2 results per source;
- ensure origin manga is filtered before display, before selection, and preferably before the 2-result cap;
- defensively prevent origin selection in `toggleSelection()`;
- strengthen tests around origin filtering, cap order, and selection behavior;
- keep normal global search uncapped and unchanged;
- create a v0.5.1 implementation report;
- align `CURRENT_STATE.md`, `NEXT_WORK.md`, `README.md`, `RECOMMENDATION_VERSIONING.md`, release notes, tests, and APK naming.

Do not implement favorite mode, link groups, backup changes, or unrelated recommendation features in this patch.
