# KMK-Recs Polish And Remaining Work Plan

Date: 2026-06-29 (updated: 2026-06-29 -- reconciliation pass; all phases A-J complete)

Status: ALL PHASES COMPLETE as of KMK-Recs v0.7.34. This document is now a historical record of the polish plan. No further implementation expected from this document. Open deferred items tracked in NEXT_WORK.md.

---

## Overview

| Phase | Planned | Actual Shipped | Topic | Status |
|---|---|---|---|---|
| A | v0.7.21 | **v0.7.29** | Quick UX polish (timestamps, pull-to-refresh, live updates, quarantine toggle) | **Complete** |
| B | v0.7.22 | **v0.7.30** | Cross-source link group management UI | **Complete** |
| C | v0.7.23 | **v0.7.34** | Source Evaluation UX polish (error categories, retry button, rec-quality triggers) | **Complete** |
| D | v0.7.24 | **v0.7.32** | Fit stat improvements (decay, Top Picks contribution) | **Complete** |
| E | v0.7.25 | v0.7.25 | For You offline / connection loss handling | **Complete** |
| F | v0.7.26 | v0.7.26 | Minimum chapter count filter | **Complete** |
| G | v0.7.27 | v0.7.27 | Quality signal display UI (Best Version history browser) | **Complete** |
| H | v0.7.28 | v0.7.28 | Backup / restore for seen manga | **Complete** |
| I | v0.7.29 | **v0.7.33** | Fullscreen preview improvements (rotation, tap-to-close, fit toggle) | **Complete** |
| J | v0.7.30 | **v0.7.31** | Enrichment cap: make configurable | **Complete** |

Note: Phases A, B, C, D, I, J shipped at different versions than originally planned. E, F, G, H shipped exactly at planned versions. All phases are complete.

Items permanently deferred (no implementation planned): AniList/tracker known-list cache (not needed), Local Source synthetic row (unclear value), auto-suggest already-linked versions on re-search (future), per-chapter enrichment cache (future).

---

## Phase A: Quick UX Polish

Suggested version: `KMK-Recs v0.7.21`

Four independent polish items that each touch a small number of files and can be done in a single session.

### A1 â€” Source status "Last checked" timestamp

**What:** Recommendation Settings shows per-source fit badges and last-run statuses, but no indication of when the data is from. Add a compact relative timestamp ("Checked 2 days ago") below the status line in `SourcePriorityItem`.

**How:** `SourceFitStats.updatedAt` (epoch ms) is already available. Compute a human-readable relative string at render time (`today / N days ago / never`). No new preferences needed.

**Files:**
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` â€” update `SourcePriorityItem` status line to include relative age when `fitStats?.updatedAt > 0`
- `i18n-kmk/.../base/strings.xml` â€” 2â€“3 strings (`rec_source_last_checked_today`, `rec_source_last_checked_days_ago`, `rec_source_never_checked`)

**Constraints:** Only show timestamp when `fitStats != null && fitStats.runCount > 0`. Do not show when source was never searched.

---

### A2 â€” Pull-to-refresh on For You

**What:** The manual refresh button exists in the action bar. Pull-to-refresh on the For You `LazyColumn` is the ergonomic complement.

**How:** Wrap the `LazyColumn` in a `SwipeRefresh` or use `PullToRefreshBox` (M3). The refresh action calls the existing `screenModel.refresh()`.

**Files:**
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt` â€” add pull-to-refresh wrapper around the source list; bind to `state.isLoading` and `screenModel::refresh`

**Constraints:** The refresh button in the action bar must still work. The `isLoading` state already exists so the spinner state is handled.

---

### A3 â€” Loved Manga live updates

**What:** `LovedMangaScreen` loads the manga list once at open time. If the user rates a manga on another screen while Loved Manga is open, the list doesn't update until they navigate away and back.

**How:** Replace the one-shot `init` load in `LovedMangaScreenModel` with a `Flow` subscription â€” subscribe to `getMangaTaste.subscribeAll()` or an equivalent reactive source and re-apply the grouper on each emission.

**Files:**
- `app/src/main/java/exh/recs/LovedMangaScreenModel.kt` â€” replace one-shot await with a `collectLatest` flow subscription

**Constraints:** The grouper (`LovedMangaGrouper`) is pure and idempotent â€” safe to re-run on each emission. Sorting preference changes already re-filter in state; the live-update only needs to react to underlying data changes, not preference changes.

---

### A4 â€” Quarantine section collapse/expand toggle

**What:** The quarantine row in Source Evaluation's `SafetyDiagnosticsRow` is always expanded when there is content. A toggle would let users collapse it to reduce visual noise after reviewing.

**How:** Add a local `var quarantineExpanded by rememberSaveable { mutableStateOf(true) }` toggle in the composable. Show a chevron icon to expand/collapse the list of quarantined packages. The count chip remains visible when collapsed.

**Files:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” modify `SafetyDiagnosticsRow` or its quarantine sub-section to support collapse

**Constraints:** State is local UI state only (no preference needed). Expanded by default so existing behavior is unchanged on first open.

---

## Phase B: Cross-Source Link Group Management UI

Suggested version: `KMK-Recs v0.7.22`

**What:** Users currently accumulate cross-source link groups every time they confirm a match action (rate/seen/favorite across extensions), with no way to view or delete them. A management surface is needed.

**Goal:** Let users see which manga are linked, and delete incorrect links without affecting ratings.

### Current code to inspect

```text
app/src/main/java/exh/recs/CrossExtensionMatchScreenModel.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/GetCrossSourceMangaLinks.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertCrossSourceMangaLinks.kt
domain/src/main/java/tachiyomi/domain/taste/model/CrossSourceMangaLink.kt
app/src/main/java/exh/recs/LovedMangaScreen.kt
app/src/main/java/exh/recs/LovedMangaScreenModel.kt
```

### Required behavior

- Entry point: a "Linked versions" section in the Loved Manga screen (or a dedicated manage screen reachable from manga detail).
- Each link group shows: the representative manga name, and the N linked versions (source + name).
- User can delete a single link from a group, or the entire group.
- Deleting a link does NOT change any ratings â€” links and ratings are independent.
- No auto-re-link after deletion.

### Implementation sketch

1. Add `DeleteCrossSourceMangaLink` interactor (deletes one row by id or by manga pair).
2. New `LinkGroupManagementScreenModel`: loads all link groups, groups by canonical manga, exposes delete action.
3. New `LinkGroupManagementScreen` or section in Loved Manga settings.
4. Entry point: button in Loved Manga screen action bar.

### Constraints

- Deleting a link must not cascade-delete manga ratings.
- The link grouper in `LovedMangaScreenModel` must re-run after a deletion (subscribe reactively as per Phase A3).
- Do not auto-suggest re-linking after deletion (permanently deferred).

---

## Phase C: Source Evaluation UX Polish

Suggested version: `KMK-Recs v0.7.23`

Three independent but thematically related Source Evaluation improvements.

### C1 â€” Per-source error category labels in past results

**What:** Source Evaluation past result rows show raw error messages. `SourceRecommendationFitFailureClassifier` (v0.7.13) already categorizes errors into a 14-value enum, but past result rows don't display the category â€” only the raw message.

**How:** In the past result row composable, check if a stored `errorMessage` maps to a known `FailureCategory` via `SourceRecommendationFitFailureClassifier`. If it does, show a compact category badge (e.g., "Install timeout", "Network error") instead of the full raw message.

**Files:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” update past-result error display to use `SourceRecommendationFitFailureClassifier.classify()`
- `i18n-kmk/.../base/strings.xml` â€” localized category label strings for each `FailureCategory` value (14 values, some can share strings)

**Constraints:** The raw error message should still be accessible (e.g., expandable or in a tooltip) so advanced users can diagnose.

---

### C2 â€” One-tap Retry after connectivity loss

**What:** After `ConnectivityLost` status (v0.7.18), the options section re-appears and the user can manually press Start again. But "Start again" re-runs from scratch. A "Retry from where it stopped" button would resume the batch using the existing continuation logic.

**How:** `SourceEvaluationScreenModel` already has batch continuation (added in v0.7.6: "Continue next batch" button). After `ConnectivityLost`, if `pendingCandidates` are still available, show a "Retry (N remaining)" button that invokes the same continue-batch action rather than a full restart.

**Files:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” add conditional "Retry (N remaining)" button when status is `ConnectivityLost` and remaining candidates exist
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` â€” expose a `retryAfterConnectivityLoss()` action that delegates to the existing continue-batch path

**Constraints:** Only show Retry if candidates remain. If the run was already complete when connectivity dropped (zero remaining), only "Start new run" makes sense.

---

### C3 â€” Auto-prompt rec-quality re-check after taste profile change

**What:** After the user changes tag preferences or ratings, the rec-quality verdicts for Source Evaluation past results may be stale. Currently the "Re-check all" button requires manual action. Add a non-blocking prompt: "Your taste profile has changed â€” re-check recommendation quality?"

**How:** Compare a fingerprint of the current taste profile against the profile fingerprint stored when rec-quality was last evaluated. If different, show a dismissible info card in the Source Evaluation screen with a "Re-check now" button that calls the existing `evaluateRecommendationQualityForPromising(reCheckAll = true)` action.

**Files:**
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` â€” compute and cache a profile fingerprint at rec-quality evaluation time; compare on screen open
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” show conditional info card when fingerprint mismatch detected
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` â€” add `sourceEvaluationLastRecQualityProfileFingerprint()` preference

**Constraints:** The prompt is dismissible and non-blocking. It must never auto-run the re-check. The existing "Re-check all" button must still work independently.

---

## Phase D: Fit Stat Improvements

Suggested version: `KMK-Recs v0.7.24`

Two improvements to the rolling fit stat system from v0.7.19.

### D1 â€” Fit stat time decay

**What:** `SourceFitStats` is currently pure accumulation â€” an error run from 3 months ago carries the same weight as one from yesterday. This makes labels slow to recover after a source improves (e.g., after an extension update fixes errors).

**Approach:** Add a 30-day recent window to `SourceFitStats`. Four new fields: `recentRunCount`, `recentShownCount`, `recentErrorCount`, `windowStartAt`. On each `merge()`, if `now - windowStartAt > 30 days`, reset the recent window then increment. The `fitLabel` algorithm uses recent rates when `recentRunCount >= MIN_RUNS_FOR_LABEL`, falling back to all-time rates otherwise.

**Files:**
- `app/src/main/java/exh/recs/SourceFitStats.kt` â€” add recent-window fields, update `merge()`, update `fitLabel` to prefer recent rates

**Constraints:** Serialization must remain backward-compatible â€” new fields default to 0 on parse from old data (safe with the existing `parts.size < N` guards).

---

### D2 â€” Top Picks contribution count per source

**What:** The fit stats don't currently track whether a source's results contributed to the Top Picks row. A source could show 10 results that are all deduped away from Top Picks while another source's single result lands in the top slot. The contribution signal is a stronger fit indicator than raw visible count.

**How:** After `combinedAccumulator.rank()` is computed in `BrowsePersonalRecommendationsScreenModel`, collect which `sourceIds` appear in the Top Picks result set. Pass as `topPicksContributors: Set<Long>` into `SourceFitStatsStore.mergeRun()` to increment a `topPicksContributionCount` field.

**Files:**
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` â€” collect `sourceIds` from ranked Top Picks result and pass to `mergeRun()`
- `app/src/main/java/exh/recs/SourceFitStats.kt` â€” add `topPicksContributionCount` field; update `merge()` and `fitLabel` to weight it positively

**Constraints:** Only count contribution from the final deduplicated Top Picks. A source that contributes 0 Top Picks results but 10 visible source-row results should not get a contribution bonus.

---

## Phase E: For You Offline / Connection Loss Handling

Suggested version: `KMK-Recs v0.7.25`

**What:** When the device has no internet connection, the For You screen currently either silently fails or shows individual per-source error states. It should detect the offline condition early and show a clear "Connection Lost" screen-level message, blocking the run rather than attempting N failing source queries.

**Behavior:**
- On screen open (or on manual refresh), if the device is offline, immediately show a screen-level offline state instead of starting source queries.
- If connectivity is lost mid-run (all remaining sources error with connectivity-style failures), surface the same offline state.
- The offline state shows a message ("No connection â€” For You needs internet to load recommendations") and a Retry button that re-checks connectivity and restarts the run if online.
- If connectivity returns while the screen is open, optionally show a snackbar: "Back online â€” tap to refresh."

**How:**
- Use `ConnectivityManager.activeNetworkInfo?.isConnected` or `NetworkCapabilities` (API 23+) to check before starting a run.
- Add a `ForYouScreenError.Offline` sealed case (or equivalent) to the screen's `State`.
- The screen renders an `EmptyScreen`-style composable with a wifi-off icon and the retry button when this state is active.
- Optionally, register a `ConnectivityManager.NetworkCallback` in `BrowsePersonalRecommendationsScreenModel` to observe connectivity and clear the error state when connection returns.

**Files:**
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` â€” add offline pre-check before starting a run; add `isOffline: Boolean` (or typed error) to State
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt` â€” render offline state composable
- `i18n-kmk/.../base/strings.xml` â€” `rec_offline_title`, `rec_offline_body`, `rec_offline_retry`

**Constraints:**
- Do NOT suppress per-source error detail when sources fail for non-connectivity reasons. The offline state is for device-level connectivity loss only.
- Do not change normal source error display for non-offline runs.
- The action bar refresh button must still work from the offline state.

---

## Phase F: Minimum Chapter Count Filter

Suggested version: `KMK-Recs v0.7.26`

**What:** For You currently shows manga with any chapter count, including one-shots or series with 0 locally-known chapters. Add a user-configurable minimum chapter count filter that uses only already-locally-known chapter counts â€” no network fetch.

**Behavior:**
- New preference: minimum chapters threshold (default 0 = no filter). Configurable in Recommendation Settings.
- Options: 0 (off), 5, 10, 20, or a slider up to 50.
- At result filtering time in `PersonalRecommendationScorer` (or post-fetch dedup step), if a candidate manga's locally-known chapter count is below the threshold, it is filtered out.
- "Locally known" means the chapter count stored in the local DB. If a manga has never been opened (count = 0 or null), treat 0 as unknown â€” do not filter out unless threshold = 0 (i.e., explicit "any count including 0" selection).

**Files:**
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` â€” add `recommendationMinChapterCount()` preference (Int, default 0)
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` â€” read preference and pass to scorer/filter step
- `app/src/main/java/exh/recs/PersonalRecommendationScorer.kt` (or equivalent post-fetch filter) â€” add chapter count exclusion check
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` â€” add minimum chapter count setting row
- `i18n-kmk/.../base/strings.xml` â€” `rec_min_chapter_count`, `rec_min_chapter_count_off`, etc.

**Constraints:**
- Never fetch chapter data. Use only `manga.totalChapters` or equivalent local DB field.
- A manga with null/unknown chapter count is NOT filtered out â€” only those with a known count below the threshold are excluded.
- This filter applies post-fetch, same as the existing tag/seen/rating exclusions.

---

## Phase G: Quality Signal Display UI

Suggested version: `KMK-Recs v0.7.27`

**What:** `manga_source_quality_signal` records are written each time a Best Version pick is confirmed (v0.7.x), but there is no UI surface for browsing or editing past quality decisions. Users cannot see what "Best Version" choices are stored, nor undo individual ones.

**Design decision:** Implement as a dedicated "Best Version History" section accessible from the Manga Detail screen (action button or overflow menu item) for the currently-viewed manga, showing all quality signal records for that manga across sources. A secondary entry point is a "Best Version History" list in Recommendation Settings for bulk review.

**Required behavior:**
- Per-manga view: shows each recorded quality signal row â€” source name, verdict (Best / Not Best / Unknown), recorded date.
- User can delete a single quality signal record, resetting that source back to "unknown" for future Best Version comparisons.
- Global list view (in settings): shows all manga that have quality signal records, grouped by manga. Same delete capability.
- Deleting a record does NOT change library entries, ratings, or cross-source links.

**Files:**
- New interactor: `DeleteMangaSourceQualitySignal` in `domain/src/main/java/tachiyomi/domain/taste/interactor/`
- New `QualitySignalHistoryScreenModel` (or extend existing manga detail screen model)
- New `QualitySignalHistoryScreen` or sheet
- Entry point in manga detail: new action / overflow item (KMK-only, behind `if (isKmkBuild)` guard or equivalent)
- Entry point in Recommendation Settings: list item "Best version history"
- `i18n-kmk/.../base/strings.xml` â€” strings for the screen title, verdict labels, delete confirmation

**Constraints:**
- Quality signal records and manga library entries are independent. Deleting a record must not affect library status.
- Do not auto-re-evaluate quality after deletion.
- Existing `GetMangaSourceQualitySignals` interactor is already present â€” use it.

---

## Phase H: Backup / Restore for Seen Manga

Suggested version: `KMK-Recs v0.7.28`

**What:** "Seen" manga keys (the set of manga the user has explicitly dismissed from For You) are stored in a `SharedPreferences` string set. This data is not included in the Komikku backup, so it is lost on device change or backup restore.

**How:**
1. Add a `BackupSeenMangaKey` proto message (proto field 626 or next available after 625). Fields: `mangaKey: String` (the same key format already used in the preference store).
2. In `TasteBackupCreator`, read all seen keys and serialize into the new proto repeated field.
3. In `TasteBackupRestorer`, merge restored seen keys into the existing preference set (union, not replace, so new dismissals after the backup point are preserved).
4. Add a round-trip test in `TasteBackupRoundTripTest` for the new field.

**Files:**
- `app/src/main/proto/` â€” add `BackupSeenMangaKey` message; add repeated field to the root backup message
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/TasteBackupCreator.kt` â€” serialize seen keys
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/TasteBackupRestorer.kt` â€” merge restored keys
- `app/src/test/java/exh/recs/backup/TasteBackupRoundTripTest.kt` â€” new round-trip test for seen manga keys

**Constraints:**
- Restore is additive (union). Never clear the existing seen set on restore.
- The seen key format must not change (existing preference store format is the source of truth).
- Forward-compat: an old backup without the `BackupSeenMangaKey` field must restore cleanly with no seen keys (proto default = empty repeated field).

---

## Phase I: Fullscreen Preview Improvements

Suggested version: `KMK-Recs v0.7.29`

Three small UX fixes for the fullscreen manga cover/thumbnail preview in the cross-extension match screen.

### I1 â€” Rotation / state restoration

**What:** `fullscreenPage` (the index of the page shown in fullscreen) is local UI state and resets to null on screen rotation, collapsing the fullscreen view.

**How:** Change `var fullscreenPage by remember { mutableStateOf<Int?>(null) }` to `rememberSaveable`. No other changes needed â€” the page index is a primitive (Int) that survives the bundle.

**Files:**
- `app/src/main/java/exh/recs/CrossExtensionMatchScreen.kt` (or wherever the fullscreen composable lives) â€” change `remember` to `rememberSaveable`

---

### I2 â€” Tap-to-close when not zoomed

**What:** The fullscreen view currently requires the user to tap a back button or swipe back. A tap anywhere on the image should close fullscreen when the image is at 1Ã— zoom (not panned/zoomed).

**How:** Add a `detectTapGestures` modifier on the fullscreen image composable that fires `fullscreenPage = null` only when the current zoom scale is â‰¤ 1.0. When zoomed in, taps should not close (they may pan instead).

**Files:**
- The fullscreen composable in `CrossExtensionMatchScreen.kt` â€” add tap gesture modifier with zoom state check

**Constraints:** Must not conflict with the existing pinch-to-zoom gesture. Use `pointerInput(Unit) { detectTapGestures { ... } }` at lower priority than the zoom transformer.

---

### I3 â€” Thumbnail ContentScale.Fit toggle

**What:** Thumbnail previews in the comparison grid use `ContentScale.Crop`, which fills the box by cropping. Some covers are tall (portrait) and get clipped significantly. Add a per-image toggle between Crop (default) and Fit (letter-boxed).

**How:** Add a small icon button (e.g., "expand" icon) overlaid on each thumbnail. Tapping it toggles a local `var fitMode by remember { mutableStateOf(false) }` per thumbnail. When true, use `ContentScale.Fit`; when false, use `ContentScale.Crop`.

**Files:**
- The thumbnail composable in `CrossExtensionMatchScreen.kt` â€” add `fitMode` toggle state and icon button overlay

**Constraints:** Toggle state is per-thumbnail, local only. No preference storage needed.

---

## Phase J: Enrichment Cap â€” Make Configurable

Suggested version: `KMK-Recs v0.7.30`

**What:** The enrichment probe currently caps at 5 candidates per source run. This value is hardcoded. Make it user-configurable so users who want richer metadata comparisons can raise it.

**How:**
- Add `recommendationEnrichmentCap()` preference (Int, default 5). Reasonable range: 1â€“20.
- Add a setting row in Recommendation Settings (advanced/expand section).
- Pass the preference value into `BrowsePersonalRecommendationsScreenModel` at enrichment invocation time.

**Files:**
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` â€” add `recommendationEnrichmentCap()` preference
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` â€” replace hardcoded cap with preference read
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` â€” add setting row
- `i18n-kmk/.../base/strings.xml` â€” `rec_enrichment_cap`, `rec_enrichment_cap_summary`

**Constraints:**
- The hardcoded value is the default â€” no migration of existing data needed.
- The setting should be in an "advanced" section or behind an expand with a note that higher values mean more network calls per run.
- Cap at 20 in the preference to prevent accidental abuse.

---

## Permanently Deferred (Will Not Implement)

- **AniList / tracker known-list cache** â€” not needed; local library data is sufficient.
- **Local Source "Already Known" synthetic row** â€” unclear user value; Local Source stays excluded from For You.
- **Auto-suggest already-linked versions when re-searching** â€” future work; cross-extension match always runs fresh.
- **Per-chapter enrichment cache** â€” future work; enriched data not persisted to local DB.

---

## Required Reading Before Any Implementation Session

Before starting any phase above, Claude must read:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md  â† this file
```

For Phase B (link groups), also read:
```text
docs/recommendations/CROSS_EXTENSION_IDENTITY_FEASIBILITY_RESEARCH.md
```

For Phase G (quality signals), also read:
```text
app/src/main/proto/  â€” current backup proto field assignments
app/src/test/java/exh/recs/backup/TasteBackupRoundTripTest.kt
```

