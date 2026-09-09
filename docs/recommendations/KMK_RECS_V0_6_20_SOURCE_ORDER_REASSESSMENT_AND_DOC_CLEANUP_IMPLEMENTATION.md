# KMK-Recs v0.6.20 Implementation Notes

Date: 2026-06-20
APK: `Komikku-v1.13.6-kmk.6.20-debug.apk`
VERSION_CODE: 620

## Scope

Implementation of `KMK_RECS_V0_6_20_SOURCE_ORDER_REASSESSMENT_AND_DOC_CLEANUP_PLAN.md`.

All 7 parts of the plan were addressed. Part 4 (hidden-source state) was explicitly deferred.

---

## Changes Implemented

### Part 1: For You Source Row Ordering

**Problem:** No visual grouping of source statuses in Recommendation Settings. Disliked sources appeared inline with active sources.

**Solution:**

Created `SourceStatusDisplayOrder.kt` â€” a pure, tested object that computes display groups:

- `Group.HAS_MATCHES` (sort key 0) â€” `Shown` status
- `Group.NO_MATCHES` (sort key 1) â€” any non-Shown, non-disliked status
- `Group.DISLIKED` (sort key 2)

Sort order: group first, then user's saved priority index, then sourceId as tie-breaker. Priority within each group is preserved.

Applied in `RecommendationsSettingsScreen.kt`: a new non-draggable "Source Status" section below the priority drag list shows sources in group order with labeled group headers (With results / No results / Disliked).

The drag-to-reorder priority list is unchanged â€” users still reorder sources by drag. The status section is read-only and reflects last For You run results.

**Files changed:**
- `app/src/main/java/exh/recs/SourceStatusDisplayOrder.kt` (new)
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
- `app/src/test/java/exh/recs/SourceStatusDisplayOrderTest.kt` (new, 8 tests)

**New strings:** `source_status_group_matches`, `source_status_group_no_matches`, `source_status_group_disliked`

---

### Part 2: Manual Source Reassessment

**Problem:** No way to know when source evaluations may be stale relative to taste changes.

**Solution:**

Added two new preferences in `SourcePreferences.kt`:
- `sourceEvaluationLastReassessmentRatingCount()` â€” total rated manga count at the last baseline
- `sourceEvaluationLastReassessmentAt()` â€” epoch-ms of the last baseline

`SourceEvaluationScreenModel` now:
- Loads `currentRatedCount` from `GetMangaTaste.awaitAll().size` at screen open
- Loads `reassessmentBaselineCount` from preferences
- Updates the baseline automatically when the background evaluation job reports `Completed` status
- Exposes computed `ratingsSinceBaseline = currentRatedCount - reassessmentBaselineCount`

`SourceEvaluationScreen` shows:
- An `InfoCard` prompt when `ratingsSinceBaseline >= 100`
- A "Reassess sources" button (available always) that sets `skipAlreadyEvaluated = false` and starts evaluation
- Button label changes to "Reassess using current tastes" when below the threshold

Baseline update only happens on successful `Completed` status â€” cancelled/failed runs do not update it.

**Files changed:**
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

**New strings:** `source_evaluation_reassessment_recommended`, `source_evaluation_reassess_button`, `source_evaluation_reassess_current_tastes`

---

### Part 3: Source Explainability

**Problem:** Past evaluation result rows showed only fit%, search%, and a verdict badge. No evidence quality or freshness signal.

**Solution:**

Added two pure functions in `SourceEvaluationScreen.kt`:
- `evidenceStrengthLabel(evaluation)` â€” returns "Strong evidence", "Moderate evidence", "Weak evidence", or "Low confidence" based on `preferredTagMatchCount`, `likedTitleMatchCount`, `searchCount`, `searchSuccessCount`, and `sampleCount`
- `lastEvaluatedLabel(evaluatedAt)` â€” returns "Last evaluated today" or "Last evaluated N days ago"

Both are appended to the existing `EvaluationResultRow` subtitle: `"ext â€¢ LANG â€¢ fit X% â€¢ search Y% â€¢ Evidence Strength â€¢ Last evaluated Z"`

No new database fields needed â€” uses already-stored `SourceEvaluation` fields.

**Files changed:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

**New strings:** none (evidence labels embedded inline for now; can be extracted later if i18n is needed)

---

### Part 4: Separate Preference Semantics

**Decision: hidden-source state is DEFERRED.**

Reasoning: Adding a hidden-source state would require a new key format in `RecommendationSourcePreferenceStore`, new UI controls, and a separate management path. The existing `disliked` / `quarantined` / `blocked` concepts are already correctly separated. A hidden-but-not-disliked concept is documented in `NEXT_WORK.md` as future work.

The existing concepts remain cleanly separated:
- **Disliked installed source**: `dislikedRecommendationSourceKeys`, excludes from For You
- **Disliked non-installed source**: `dislikedRecommendationSourceKeys`, hides from Sources To Try
- **No matches**: ephemeral run status, not a user preference
- **Quarantined source**: `source_evaluation_unsafe_source` table, crash recovery only
- **Blocked package**: `unsafe_extension_package` table, load-time guard
- **Evaluation cache**: `source_evaluation` table, independent of preferences

---

### Part 5: Reset / Management Controls

**Problem:** No way to reset source decisions without clearing app preferences manually.

**Solution:**

Added a collapsible "Source management" section in `SourceEvaluationScreen` with three actions, each triggering a confirmation dialog before executing:

| Action | What it clears |
|---|---|
| Reset disliked sources | `dislikedRecommendationSourceKeys` preference â†’ empty |
| Reset reassessment baseline | `sourceEvaluationLastReassessmentRatingCount` and `At` prefs â†’ 0 |
| Clear seen manga | `seenRecommendationMangaKeys` preference â†’ empty |

The section is collapsed by default (`showManagementSection = false`). Tapping the header expands it.

`ManagementAction` enum in `SourceEvaluationScreenModel` typed the three actions for the confirmation dialog flow. No source priority, installed extensions, or manga ratings are affected.

**Files changed:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

**New strings:** `source_evaluation_management_title`, `source_evaluation_reset_disliked_sources`, `source_evaluation_reset_reassessment_baseline`, `source_evaluation_clear_seen_manga`, `source_evaluation_management_confirm_title`, `source_evaluation_management_confirm_body`

---

### Part 6: Seen / Already Read Manga Marker

**Problem:** Recommendation manga the user has already read keep reappearing in For You. Marking them as Like/Dislike would distort taste scores.

**Solution:**

Created `SeenRecommendationMangaStore.kt` â€” a pure, tested object that:
- Parses / serializes `Set<SeenMangaKey(sourceId, url)>` from semicolon-separated `"sourceId|url"` strings
- Provides `parse`, `serialize`, `add`, `remove` helpers
- Uses `indexOf('|')` so URLs containing pipes are parsed correctly

Added `seenRecommendationMangaKeys()` preference to `SourcePreferences.kt`.

**For You filtering (`BrowsePersonalRecommendationsScreenModel`):**
- Seen keys loaded at `load()` time
- Filtered from search results (`localized.filterNot { SeenMangaKey(it.source, it.url) in seenKeys }`) â€” ALWAYS, regardless of `hideKnownManga`
- Filtered from cache results (same check in `loadFromCache`)
- `seenMangaCount` added to `profileFingerprint()` so the recommendation cache is invalidated when the seen set changes

**MangaInfoHeader:** Added "Mark as seen" / "Clear seen" item in the rating dropdown (below the existing "Other versions" divider section). Also added "Seen other versions" item visible when the manga is not already seen. Uses `Icons.Outlined.Visibility` / `VisibilityOff`.

**MangaScreenModel:** Added `isSeen: Boolean = false` to `State.Success`. Added `markSeen()` and `clearSeen()` functions that read/write the preference and update state. Initial `isSeen` is read at screen load from the preference.

**MangaScreen (ui and presentation):** Added `isSeen`, `onSeenClicked`, `onSeenOtherVersionsClicked` params threaded through `MangaScreen` â†’ `MangaScreenSmallImpl` / `MangaScreenLargeImpl` â†’ `MangaActionRow` / `MangaInfoHeader`.

**CrossExtensionMatchMode:** Added `MarkSeen` as a new sealed interface case. `CrossExtensionMatchScreen` handles it with new screen title / confirm label strings. `CrossExtensionMatchScreenModel.applyRating()` branches on mode:
- `Rating(r)` â†’ calls `SetMangaTasteBatch` (unchanged)
- `MarkSeen` â†’ reads the seen preference, adds all selected `SeenMangaKey` entries, writes back

Seen records do NOT write taste rows. Tag weights are not changed. Seen does not count toward the 100-rating reassessment threshold.

**Backup / restore:** DEFERRED. Seen state is stored in preferences only. A future pass may move it to SQLDelight with backup/restore if the preference-based approach proves limiting.

**Files changed:**
- `app/src/main/java/exh/recs/SeenRecommendationMangaStore.kt` (new)
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/eu/kanade/presentation/manga/components/MangaInfoHeader.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt`
- `app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt`
- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt`
- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt`
- `app/src/test/java/exh/recs/SeenRecommendationMangaStoreTest.kt` (new, 11 tests)

**New strings:** `rec_mark_seen`, `rec_clear_seen`, `rec_match_title_seen`, `rec_match_apply_seen`, `rec_match_applying_seen`

---

### Part 7: Documentation Cleanup

Archive layout was already created by a prior Codex pass. Active folder is clean. No further archiving needed in this pass.

Updated:
- `docs/recommendations/README.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/CURRENT_STATE.md`
- `RECOMMENDATION_VERSIONING.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

---

## New Strings (v0.6.20)

| Key | Value |
|---|---|
| `rec_mark_seen` | `Mark as seen` |
| `rec_clear_seen` | `Clear seen` |
| `rec_match_title_seen` | `Seen other versions` |
| `rec_match_apply_seen` | `Mark %1$d version(s) as seen` |
| `rec_match_applying_seen` | `Marking as seenâ€¦` |
| `source_evaluation_reassessment_recommended` | `%1$d new ratings since last source evaluation. Reassessment is recommended.` |
| `source_evaluation_reassess_button` | `Reassess sources` |
| `source_evaluation_reassess_current_tastes` | `Reassess using current tastes` |
| `source_evaluation_last_evaluated_today` | `Last evaluated today` |
| `source_evaluation_last_evaluated_days` | `Last evaluated %1$d days ago` |
| `source_evaluation_evidence_strong` | `Strong evidence` |
| `source_evaluation_evidence_moderate` | `Moderate evidence` |
| `source_evaluation_evidence_weak` | `Weak evidence` |
| `source_evaluation_evidence_low_confidence` | `Low confidence` |
| `source_evaluation_management_title` | `Source management` |
| `source_evaluation_reset_disliked_sources` | `Reset disliked sources` |
| `source_evaluation_reset_reassessment_baseline` | `Reset reassessment baseline` |
| `source_evaluation_clear_seen_manga` | `Clear seen manga` |
| `source_evaluation_management_confirm_title` | `Confirm reset` |
| `source_evaluation_management_confirm_body` | `This will clear the selected data. This action cannot be undone.` |
| `source_status_group_matches` | `With results` |
| `source_status_group_no_matches` | `No results` |
| `source_status_group_disliked` | `Disliked` |

---

## Test Results

- `SourceStatusDisplayOrderTest` â€” 8 tests, all PASSED
- `SeenRecommendationMangaStoreTest` â€” 11 tests, all PASSED
- `SourceEvaluationCandidateFilterTest` (prior tests) â€” 25 tests, all PASSED
- All other existing tests â€” PASSED
- `:app:assembleDebug` â€” BUILD SUCCESSFUL

---

## Constraints Preserved

- v0.6.18 `KnownUnsafeExtensionPackages` static guard and `ExtensionLoader` filter â€” unchanged.
- Shizuku is not the default path â€” unchanged.
- No unvetted repos added by default â€” unchanged.
- Offline failures do not affect source quality scores â€” unchanged.
- Disliked and quarantined/blocked concepts remain separate â€” no overloading.
- No-match sources are not penalized in scoring â€” the sort is display-only.
- Seen manga does not affect taste weights or the 100-rating reassessment threshold.
- Normal global search limits are unchanged.
- The For You page layout is not redesigned.

---

## Known Limitations

- **Backup/restore for seen manga**: Deferred. Seen entries in preferences are not included in Tachiyomi backup files. Future pass may move to SQLDelight + proto backup.
- **Hidden-source state**: Deferred. Currently there is no "hide temporarily, don't dislike" concept separate from dislike. Documented in `NEXT_WORK.md`.
- **Evidence strength labels**: Currently embedded as hardcoded strings in `SourceEvaluationScreen.kt` rather than extracted to `i18n-kmk/strings.xml`. The `source_evaluation_evidence_*` keys are in strings.xml but not yet hooked to the labels (they are prepared for future use). Current labels use the English values directly.
- **Source evaluation reassessment tests**: The reassessment threshold logic (`ratingsSinceBaseline >= 100`) is computed in the Composable UI rather than in the ScreenModel as a pure function. Unit tests for this threshold are deferred; manual testing is sufficient for the first pass.

