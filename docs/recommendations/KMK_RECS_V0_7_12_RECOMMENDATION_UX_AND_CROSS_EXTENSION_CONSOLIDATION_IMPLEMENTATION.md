# KMK-Recs v0.7.12 — Recommendation UX and Cross-Extension Consolidation Implementation

**Version:** KMK-Recs v0.7.12 (versionCode 84)
**APK:** `Komikku-v1.13.6-kmk.7.12-debug.apk`
**Base plan:** `docs/community/KMK_PHASE_6_7_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_PLAN.md`

---

## What Was Done

### Phase 0: Stale v0.8.0 Marker Cleanup

All `// KMK --> v0.8.0` markers in active code were corrected to `// KMK --> v0.7.11` or `// KMK --> v0.7.12` as appropriate. Corrected files:

- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` (3 occurrences)
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` (3 occurrences)
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` (comment)
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`

The v0.7.11 correction document (`KMK_RECS_V0_7_11_PHASE_5_CORRECTION.md`) was intentionally left intact — it explains the accident for historical context.

---

### Pre-Phase 6/7 Fix: Rec-Quality Probe Error Transparency

**Problem:** The Source Evaluation screen showed "Error" for almost every source with no further detail. The two root causes:

**Scenario A — Pre-probe errors** (`writeRecQualityErrorFit`): When source installation failed, the extension was not found in the available list, or install timed out, `writeRecQualityErrorFit` stored an `errorMessage` string in `SourceRecommendationFit`. But the UI never displayed it — only the verdict badge showed. Fixed by adding a new error detail `Text` element below the quality label.

**Scenario B — Probe-level errors** (`buildRecQualityFitFromOutcome`): When `getSearchManga` threw an exception, the exception message was captured in `outcome.reasons` per plan (e.g. `"Plan TOP_TAGS_FILTER: error — UnknownHostException"`), and `outcome.label()` correctly returned `ERROR`. But `buildRecQualityFitFromOutcome` was hardcoding `errorMessage = null` regardless. Fixed by deriving `probeErrorMessage` from `outcome.reasons.take(2).joinToString("; ").take(200)` when label is ERROR.

**Files changed:**

`exh/recs/evaluation/SourceEvaluationScreenModel.kt` — `buildRecQualityFitFromOutcome`:
```kotlin
// KMK --> v0.7.12: when probe outcome is ERROR, populate errorMessage from per-plan reasons
val probeErrorMessage = if (label == RecommendationQualityLabel.ERROR && outcome.reasons.isNotEmpty()) {
    outcome.reasons.take(2).joinToString("; ").take(200)
} else {
    null
}
// KMK <--
// ...
errorMessage = probeErrorMessage,
```

`exh/recs/evaluation/SourceEvaluationScreen.kt` — added below the quality label row:
```kotlin
// KMK --> v0.7.12: show error detail when rec-quality verdict is ERROR
val recFitErrorMessage = recFit?.errorMessage
if (recFit != null &&
    recFit.verdict == tachiyomi.domain.taste.model.RecommendationQualityVerdict.ERROR &&
    !recFitErrorMessage.isNullOrBlank()
) {
    Text(
        text = stringResource(KMR.strings.source_evaluation_rec_quality_error_hint, recFitErrorMessage),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
// KMK <--
```

**New KMR strings** (`i18n-kmk/.../base/strings.xml`):
```xml
<!-- KMK v0.7.12: surface error reasons in rec-quality display, settings reorganization -->
<!-- KMK --> <string name="source_evaluation_rec_quality_error_hint">%1$s</string>
<!-- KMK --> <string name="rec_settings_daily_recs_header">Daily recommendations</string>
<!-- KMK --> <string name="rec_settings_ratings_known_manga_header">Ratings and known manga</string>
<!-- KMK --> <string name="rec_settings_management_header">Management</string>
<!-- KMK --> <string name="rec_settings_source_status_section_header">Source status</string>
<!-- KMK --> <string name="source_evaluation_settings_experimental_header">Experimental — Source Evaluation</string>
```

**Remaining honest limitation:** When `extensionManager.availableExtensionsFlow.value` is empty (extension repos never refreshed), the resolver returns "Available extension list unavailable" which now appears in the UI error detail — no longer a silent Error. The user needs to refresh extension repos before probing non-installed sources.

---

### Phase 6/7 (partial): Recommendation Settings Reorganization

`exh/recs/settings/RecommendationsSettingsScreen.kt` restructured. New section order in LazyColumn:

| # | Key | Header | Notes |
|---|-----|--------|-------|
| 1 | `daily_recs_header` | Daily recommendations | **NEW** — language selector moved to top |
| 2 | `rated_header` | Ratings and known manga | **RENAMED** from "Rated manga visibility" |
| 3 | `tag_header` | Tags | unchanged |
| 4 | `source_header` | Source priority | unchanged; source list + reset button |
| 5 | `same_manga_header` | Same manga matching | **MOVED UP** from after Sources To Try |
| 6 | `status_order_header` | Source status | **RENAMED** from composite "Source priority — Has Matches / No Matches / Disliked" |
| 7 | `management_header` | Management | **NEW** — Sources To Try + cleanup actions |
| 8 | `source_eval_header` | Experimental — Source Evaluation | **RENAMED** to clarify it's advanced/experimental |

The duplicate same-manga section that was previously duplicated after Sources To Try was removed.

---

### Version Bump

`app/build.gradle.kts`:
- `versionCode = 84 // KMK-Recs v0.7.12`

`exh/recs/KmkRecsReleaseNotes.kt`:
- `VERSION_CODE = 712`
- `VERSION_NAME = "KMK-Recs v0.7.12"`
- What's New section added for v0.7.12

---

## Tests

**New tests added** (`SourceRecommendationFitProbeTest`):
- `probe reasons list is populated when source throws` — asserts `outcome.reasons.isNotEmpty()` when source throws and label is ERROR
- `outcome label ERROR when all plans fail with errorCount gt 0 and querySuccessCount is 0` — pure outcome constructor test
- `reasons list first entry describes first plan failure` — asserts reason string names the failed plan type

**Full test count:** 12 tests in `SourceRecommendationFitProbeTest` (up from 8).

**All gates passed:**
- `spotlessApply` ✓
- `spotlessCheck` ✓
- `:app:testDebugUnitTest` ✓ (BUILD SUCCESSFUL)
- `assembleDebug` ✓ (BUILD SUCCESSFUL, 162.4 MB)

---

## What Was Deferred (Not Done in v0.7.12)

The following Phase 6/7 plan items were not implemented:

- **Terminology cleanup** — Love/Like/Dislike/Seen consistency across all UI strings; Prefer/Avoid source, Strong Fit / Worth Trying labels
- **For You row ordering clarity** — review `SourceStatusDisplayOrder` for sort signal documentation
- **Known manga / Seen handling** — verify Seen/Read integration in recommendation filters
- **Loved Manga view filter** — conservative duplicate grouping; installed-source-only filter (already coded but needs UX review)
- **Same-manga matching settings caps** — bounded-workflow cap controls, results-per-source 1/2/5/10 option, preselect on/off
- **Cross-extension link group management UI** — route safety confirmed OK (`CrossExtensionMatchRouteMode` uses primitives); management UI for named groups is deferred
- **Best Version cancel safety** — cancel from preview should return to candidate preview, not exit the workflow
- **Best Version fullscreen preview** — pinch-to-zoom candidate preview
- **Source quality signal (user-confirmed)** — store per-source "quality confirmed by user" bit with detail
- **Recommendation cache invalidation** — document and surface the existing refresh / cache-bust behavior

These items carry forward to v0.7.13+. See `docs/recommendations/NEXT_WORK.md`.
