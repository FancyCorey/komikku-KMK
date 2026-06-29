# KMK-Recs v0.7.15 Phase 6/7 Cleanup And Komikku Alignment — Implementation

Date: 2026-06-27

Status: COMPLETE. All build gates pass.

APK: `Komikku-v1.13.6-kmk.7.15-debug.apk`

## Summary

v0.7.15 is a focused cleanup pass: removes remaining hardcoded English strings from Source Evaluation, adds feedback for the Loved Manga duplicate toggle, adds sort tests, and verifies already-fixed behaviors. No scoring changes, no probing changes, no schema changes.

## Version

- `versionCode = 87`
- `VERSION_CODE = 715`
- `VERSION_NAME = "KMK-Recs v0.7.15"`

## Komikku UI Patterns Checked

Before changes, the following official Komikku widget files were inspected:

- `BasePreferenceWidget.kt` — `sizeIn(minHeight = LocalPreferenceMinHeight.current)`, 16dp horizontal padding, vertically centered row
- `SwitchPreferenceWidget.kt` — whole row clickable, `Switch(onCheckedChange = null)` to avoid double-fire
- `PreferenceGroupHeader.kt` — `secondary` color, `bodyMedium` style, 14dp top / 8dp bottom padding, 16dp horizontal
- `TextPreferenceWidget.kt` — `bodySmall` + `onSurfaceVariant` for subtitle/summary

All v0.7.15 additions follow these patterns. The duplicate-toggle feedback uses `bodySmall` + `onSurfaceVariant` + `padding(horizontal = 16.dp, vertical = 4.dp)` — consistent with other subdued hints in the screen.

## Part 1 — Source Evaluation String Cleanup

### Problem

`SourceEvaluationScreen.kt` had three sets of hardcoded English user-facing strings:

1. `evidenceStrengthLabel(evaluation): String` — returned `"Strong evidence"`, `"Moderate evidence"`, `"Weak evidence"`, `"Low confidence"`.
2. `lastEvaluatedLabel(evaluatedAt): String` — returned `"Last evaluated today"` / `"Last evaluated N days ago"`.
3. `VerdictBadge(verdict)` — hardcoded `"Strong Fit"`, `"Worth Trying"`, `"Neutral"`, `"Weak"`, `"Poor Search"`, `"Explicit"`, `"Ecchi"`, `"Rejected"`, `"Error"`, `"Review"`.

KMR strings for evidence and last-evaluated already existed from v0.6.20. Verdict strings were missing.

### Fix

**New KMR strings (10 verdict labels):**

```xml
source_evaluation_verdict_strong_fit    = "Strong Fit"
source_evaluation_verdict_worth_trying  = "Worth Trying"
source_evaluation_verdict_neutral       = "Neutral"
source_evaluation_verdict_weak          = "Weak"
source_evaluation_verdict_poor_search   = "Poor Search"
source_evaluation_verdict_explicit      = "Explicit"
source_evaluation_verdict_ecchi         = "Ecchi"
source_evaluation_verdict_rejected      = "Rejected"
source_evaluation_verdict_error         = "Error"
source_evaluation_verdict_review        = "Review"
```

**`evidenceStrengthLabel()` → pure enum classifier:**

Replaced `private fun evidenceStrengthLabel(evaluation): String` with:
- `private enum class EvidenceStrength { STRONG, MODERATE, WEAK, LOW_CONFIDENCE }`
- `private fun evidenceStrength(evaluation: SourceEvaluation): EvidenceStrength` — same logic, returns enum

**`lastEvaluatedLabel()` → pure int helper:**

Replaced `private fun lastEvaluatedLabel(evaluatedAt): String` with:
- `private fun lastEvaluatedDaysAgo(evaluatedAt: Long): Int` — returns day count, no localization

**`EvaluationResultRow` composable** — now maps enum and int to KMR strings via `stringResource(...)`:

```kotlin
val evidenceStr = stringResource(when (evidenceStrength(evaluation)) {
    EvidenceStrength.STRONG -> KMR.strings.source_evaluation_evidence_strong
    ...
})
val daysAgo = lastEvaluatedDaysAgo(evaluation.evaluatedAt)
val lastEvalStr = if (daysAgo <= 0) {
    stringResource(KMR.strings.source_evaluation_last_evaluated_today)
} else {
    stringResource(KMR.strings.source_evaluation_last_evaluated_days, daysAgo)
}
```

**`VerdictBadge()`** — now uses `labelRes` + `stringResource(labelRes)`:

```kotlin
val (labelRes, color) = when (verdict) {
    SourceEvaluationVerdict.STRONG_FIT -> KMR.strings.source_evaluation_verdict_strong_fit to ...
    ...
}
Badge(...) { Text(text = stringResource(labelRes), ...) }
```

**Files changed:**
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 10 new verdict strings added
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` — 3 functions replaced

### Komikku Alignment

- Pure classification helpers return enums/ints, not localized strings — matches Komikku's Compose i18n style.
- Composables map enum/int to `stringResource(...)` — no Context passed into helpers.
- No hardcoded English user-facing strings remain in the touched composables.

## Part 2 — Loved Manga Sort Tests

### Added test file: `LovedMangaSortTest.kt`

8 new unit tests covering all 4 sort modes via `State.Success.displayItems`:

| Test | Passes |
|---|---|
| `RECENT preserves load order` | ✓ |
| `OLDEST reverses load order` | ✓ |
| `TITLE_AZ sorts by manga title when manga is non-null` | ✓ |
| `TITLE_AZ falls back to taste title when manga is null` | ✓ |
| `TITLE_AZ is case-insensitive` | ✓ |
| `SOURCE sorts by taste source id` | ✓ |
| `sorting preserves all entries — none are dropped` | ✓ |
| `empty list produces empty display items for all modes` | ✓ |

Testing approach: `sortEntries` is private, so tests drive it via `State.Success(groupDuplicates = false).displayItems`. This matches the integration test style in `LovedMangaDuplicateGrouperTest.kt` which also tests through `State.Success`.

`Manga.create()` is used with `favorite = false` — the `GetCustomMangaInfo` lazy injection is not triggered, making this safe in tests without Injekt setup.

## Part 3 — Loved Manga Duplicate-Toggle Feedback

### Problem

When the user enables "Group clear duplicates" but no duplicates exist in their list, nothing changes visually. The toggle appears broken.

### Fix

Added an inline `bodySmall` text item in `LovedMangaScreen` when:
- `groupDuplicates == true`
- `entries.isNotEmpty()`
- `displayItems.size == entries.size` (grouping collapsed nothing)

```kotlin
if (s.groupDuplicates && s.entries.isNotEmpty() && s.displayItems.size == s.entries.size) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(KMR.strings.loved_manga_no_clear_duplicates),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
```

New KMR string:
```xml
loved_manga_no_clear_duplicates = "No clear duplicates found."
```

No dialog, no card — inline subdued text only, per alignment rules.

**Files changed:**
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 1 new string
- `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt` — inline text item added in success state

## Part 4 — Seen/Read Behavior Verification

### Verified as already correct

From CURRENT_STATE.md and code inspection:

- `BrowsePersonalRecommendationsScreenModel` filters `seenRecommendationMangaKeys` from For You — confirmed documented.
- `CrossExtensionMatchMode.MarkSeen` writes seen keys and a cross-source link group — confirmed in CrossExtensionMatchScreenModel.
- "Seen other versions" remains visible after marking current manga as seen (v0.7.1 fix, documented in CURRENT_STATE.md).
- `SeenRecommendationMangaStoreTest` covers parse/serialize/add/remove — 11 tests, all passing.
- Seen state stored in preferences only; backup/restore deferred.

**No code changes.** Documented as verified.

## Part 5 — Best Version Cancel And Fullscreen State Verification

### Verified as already fixed in v0.7.9

From CURRENT_STATE.md (Best Version / Chapter Quality Workflow section):

> "After the user selects the best candidate, a Migrate / Copy / Cancel dialog calls MigrateMangaUseCase. Cancel correctly dismisses the dialog (v0.7.9 fix)."
>
> "Sampled page thumbnails are tappable. Tap opens a fullscreen FullscreenPagePreviewDialog (v0.7.9). Fullscreen preview supports pinch-to-zoom (max 5×) and pan via detectTransformGestures. Closing returns to the comparison screen with all state intact (v0.7.9)."

`BestVersionCompareScreen.kt` was confirmed to import `AlertDialog`, `TextButton`, and the `FullscreenPreviewPage` data class with zoom/pan via `detectTransformGestures`. `dismissMigrationDialog()` sets `selectedBestKey = null` and `isMigrating = false` without clearing state.

**No code changes.** NEXT_WORK.md stale items removed (see Part 7).

## Part 6 — Source Quality Signal Decision

### Decision: documentation cleanup only

Every insert to `manga_source_quality_signal` occurs in `BestVersionCompareScreenModel` only when the user explicitly selects a best version and completes migration/copy. There is no code path that writes a non-user-confirmed signal.

The existing table schema is sufficient. Adding a `confirmed_quality = true` column that is always `true` adds no information. No migration was added.

**Files changed:** None (docs only).

## Part 7 — Documentation And Versioning

### Versioning

- `app/build.gradle.kts`: `versionCode = 87 // KMK-Recs v0.7.15`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`: `VERSION_CODE = 715`, `VERSION_NAME = "KMK-Recs v0.7.15"`, What's New added

### Docs updated

- `docs/recommendations/CURRENT_STATE.md` — updated to v0.7.15
- `docs/recommendations/NEXT_WORK.md` — stale Best Version cancel/fullscreen items removed; sort tests marked done; evidence strings hookup marked done

## Files Modified

| File | Change |
|---|---|
| `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` | 11 new KMR strings (10 verdict + 1 no-duplicates) |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` | `evidenceStrengthLabel` → enum classifier; `lastEvaluatedLabel` → int helper; `EvaluationResultRow` uses KMR; `VerdictBadge` uses KMR |
| `app/src/main/java/exh/recs/loved/LovedMangaScreen.kt` | Inline no-duplicates feedback item |
| `app/src/test/java/exh/recs/loved/LovedMangaSortTest.kt` | New — 8 sort mode tests |
| `app/build.gradle.kts` | `versionCode = 87` |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | VERSION_CODE = 715, VERSION_NAME = "KMK-Recs v0.7.15", What's New |

## New KMR Strings

```xml
source_evaluation_verdict_strong_fit   = "Strong Fit"
source_evaluation_verdict_worth_trying = "Worth Trying"
source_evaluation_verdict_neutral      = "Neutral"
source_evaluation_verdict_weak         = "Weak"
source_evaluation_verdict_poor_search  = "Poor Search"
source_evaluation_verdict_explicit     = "Explicit"
source_evaluation_verdict_ecchi        = "Ecchi"
source_evaluation_verdict_rejected     = "Rejected"
source_evaluation_verdict_error        = "Error"
source_evaluation_verdict_review       = "Review"
loved_manga_no_clear_duplicates        = "No clear duplicates found."
```

## What Was Verified And Not Changed

| Item | Outcome |
|---|---|
| Source status display order | Verified correct in v0.7.14; 8 tests pass |
| Same-manga matching settings | Verified correct in v0.7.14 |
| Best Version cancel dialog | Verified fixed in v0.7.9 |
| Best Version fullscreen preview | Verified implemented in v0.7.9 |
| Seen filtering from For You | Verified per code + docs |
| "Seen other versions" availability | Verified per code + docs |
| Source quality signal confirms | No `confirmed_quality` column needed — all writes are user-confirmed by workflow |

## Build Gates

All run and passed:

```
.\gradlew.bat spotlessApply    → BUILD SUCCESSFUL
.\gradlew.bat spotlessCheck    → BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest  → BUILD SUCCESSFUL (267 actionable tasks, 8 new LovedMangaSortTest tests PASSED)
.\gradlew.bat assembleDebug    → BUILD SUCCESSFUL
```

APK copied to: `Komikku-v1.13.6-kmk.7.15-debug.apk`

## Manual QA Checklist

1. Open Source Evaluation. Confirm evidence labels and last-evaluated labels display correctly ("Strong evidence" / "Moderate evidence" / "Weak evidence" / "Low confidence"; "Last evaluated today" / "Last evaluated N days ago").
2. Confirm verdict badges show correct labels ("Strong Fit", "Worth Trying", "Neutral", etc.) — same visual appearance as before.
3. Open Loved Manga. Toggle "Group clear duplicates" ON when no duplicates exist — confirm "No clear duplicates found." appears as subdued text under the sort chips.
4. Toggle "Group clear duplicates" OFF — confirm the text disappears.
5. Toggle "Group clear duplicates" ON when duplicates DO exist — confirm the text does NOT appear (grouping happened, versionCount badges show).
6. Try each Loved Manga sort mode (Most recent, Oldest first, Title A–Z, Source) — confirm order changes correctly.
7. Open a manga marked Seen — confirm "Seen other versions" is still available in the rating menu.
8. Open Best Version — confirm Cancel returns to the comparison screen (not exits the workflow).
9. Confirm normal global search behavior unchanged.
10. Confirm Recommendation Settings looks correct — no regressions from v0.7.14.

## Deferred Items

- Evidence strength tests for `evidenceStrength()` — the function is private inside the Kotlin file. Could be extracted to an `internal` helper if tests are wanted. Deferred as low priority since the classification logic is the same as the original and covered by the existing integration.
- Loved Manga live updates (load once at open time) — deferred from v0.7.0.
- Backup/restore for seen manga — deferred.
- Cross-source link group management UI — deferred.
- Source quality signal display UI — deferred.
- Phase 8 readiness: v0.7.15 closes the Phase 6/7 cleanup scope. Phase 8 can begin next.
