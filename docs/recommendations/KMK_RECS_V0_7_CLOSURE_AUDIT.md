# KMK-Recs v0.7 Closure Audit

Date: 2026-07-12

Status: audit only. This file is not an implementation plan and does not approve code changes.

## Purpose

This audit reconciles the current code and active markdown after `KMK-Recs v0.7.44` so the v0.7 line can be closed deliberately instead of continuing because of stale deferred notes. It checks:

- items still documented as open,
- items that were once planned but are now implemented,
- items that are genuinely unfinished,
- items that should be explicitly deferred to v0.8 or later,
- documentation inconsistencies that could confuse Claude, Codex, or a human reviewer.

## Current Confirmed Baseline

Current release marker:

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
  - `VERSION_CODE = 746`
  - `VERSION_NAME = "KMK-Recs v0.7.44"`
- `RECOMMENDATION_VERSIONING.md`
  - latest recorded APK: `Komikku-v1.13.6-kmk.7.44-debug.apk`
- `docs/recommendations/NEXT_WORK.md`
  - v0.7.44 marked shipped and verified.
- `app/build.gradle.kts`
  - personal/debug line still uses upstream base `applicationId = "app.komikku"` plus debug suffix.
  - community test line exists as `kmkPublicTest` with `applicationIdSuffix = ".kmk"`.

## High-Level Verdict

`v0.7.44` appears functionally close enough to close the v0.7 feature line.

The remaining work is not a large set of missing core v0.7 behavior. It is mostly:

1. stale documentation that still lists already-fixed issues,
2. optional quality-of-life items that can safely move to v0.8,
3. one small user-facing polish item that is genuinely not surfaced yet,
4. one recently requested default-behavior tweak for Rated Manga grouping,
5. broader future recommendation controls that should be planned as a new feature phase, not squeezed into v0.7 closure.

## Confirmed Implemented, Despite Older Deferred Notes

These should not be reimplemented.

### Source Evaluation Screen Error Localization

`NEXT_WORK.md` still lists "ScreenErrorMessage Still Hardcoded (R-019)" as open, but code inspection shows this is stale.

Current code:

- `SourceEvaluationScreenModel.kt` defines typed `ScreenErrorKey`.
- `SourceEvaluationScreen.kt` maps `ScreenErrorKey` to KMR strings.
- `i18n-kmk/.../strings.xml` includes:
  - `source_evaluation_error_candidate_load_failed`
  - `source_evaluation_error_candidate_load_failed_unknown`
  - crash recovery and job-conflict strings.

Recommendation: remove this from "Remaining Open Items" or mark it resolved by v0.7.18/v0.7.44.

### Recommendation-Quality PromptRequired Cleanup Path

`NEXT_WORK.md` still lists "Cleanup for PromptRequired Extensions in Rec-Quality Probe" as open. The current code now routes recommendation-quality probing through:

- `SourceRecommendationQualityJob`
- `SourceRecommendationQualityRunner`
- `SourceEvaluationCleanupPolicy`

The runner explicitly handles:

- `RemovePrivateSilently`
- `PromptRequired`
- cleanup failures

Recommendation: mark the old inline-probe cleanup item resolved or convert it to a QA note only if device testing shows leftovers.

### Same-Manga Match Candidate Preselection

The setting for same-manga matching candidates starts enabled by default:

- `SourcePreferences.sameMangaMatchPreselectResults() = preferenceStore.getBoolean("same_manga_match_preselect_results", true)`
- Used by:
  - `CrossExtensionMatchScreenModel`
  - `BestVersionCompareScreenModel`
  - Recommendation settings state.

This means Love/Like/Dislike/Not Interested/Favorite/Best Version same-manga match candidates still default to selected.

## Confirmed Not Implemented Or Still Unfinished

### Rated Manga Duplicate Grouping Defaults Off

The user requested that Loved/Liked/Disliked grouped selection/display should be on by default in a future implementation.

Current code:

- `LovedMangaScreenModel.load(...)` creates `State.Success(..., groupDuplicates = false, ...)`.
- The screen has a toggle, but the base state is flat.

Impact:

- Loved/Liked/Disliked views can still show separate versions until the user toggles grouping.
- This is not the same as same-manga match preselection. Match preselection is already default-on; Rated Manga duplicate grouping is not.

Recommendation:

- If doing one final v0.7 cleanup build, change this default to grouped.
- Otherwise record as first v0.8 polish item.

### Top Picks Contribution Display

Current state:

- `SourceFitStats.topPicksContributionCount` exists.
- `BrowsePersonalRecommendationsScreenModel` increments the source stats when sources contribute to Top Picks.
- `RecommendationsSettingsScreen.kt` displays fit labels and timestamps, but does not display `topPicksContributionCount`.
- `CURRENT_STATE.md` and `NEXT_WORK.md` both correctly note that the field exists but is not surfaced.

Impact:

- The app learns which sources contribute to Top Picks, but the user cannot see that signal in Recommendation Settings.

Recommendation:

- This is a small, low-risk final v0.7 cleanup candidate if the user wants one last build.
- Display only when count is greater than zero, using a compact KMR string such as `Top Picks: %1$d`.

### Manage Hidden Source Suggestions

Current documented open item:

- If the user dislikes a non-installed source suggestion, it is hidden.
- There is no dedicated management screen to undo individual hidden non-installed source dislikes.

Impact:

- Recoverability is weak if the user accidentally dislikes a source suggestion.

Recommendation:

- Good feature, but not necessary to close v0.7.
- Move to v0.8 unless the user strongly wants a final source-management polish pass.

### Temporary Hide For Source Suggestions

Current documented open item:

- No temporary hide/snooze action distinct from dislike.

Recommendation:

- Defer to v0.8 or later. It needs preference schema/design decisions and is not a closure blocker.

### Automatic Best Version Re-Search Trigger

Current documented open item:

- After new source-quality information appears, the app does not automatically suggest re-running Best Version comparison.

Recommendation:

- Defer. This is a larger behavior feature and should not be mixed into v0.7 closure.

## Broader Feature Ideas That Should Move To v0.8+

These are real ideas, but they should not keep v0.7 open.

### Broader For You Filters

Examples already discussed:

- latest chapter/update date,
- publication age,
- status,
- local minimum chapter count,
- metadata-confidence controls.

Recommendation: v0.8 feature phase. These need careful source metadata handling because many extensions have incomplete or inconsistent fields.

### Refresh Effort Modes

Examples:

- light refresh,
- normal refresh,
- deeper refresh,
- page budget controls.

Recommendation: v0.8 feature phase. This builds on the v0.7.38-v0.7.44 discovery memory/query-policy work.

### Source-Scope Controls

Examples:

- all sources,
- priority sources only,
- selected sources,
- group sources.

Recommendation: v0.8 feature phase. Useful, but needs UI design and clear interaction with source priority and disabled sources.

### Chapter/Update Checks Beyond Local Minimum Chapter Filter

Recommendation: v0.8 or later. Avoid network-heavy chapter fetching unless explicitly requested in a bounded workflow.

### Local Outcome Learning

Examples:

- learn from opens,
- dismissals,
- later ratings,
- source contribution outcomes.

Recommendation: v0.8 or later. This is promising but needs its own privacy/data-lifecycle plan.

## Permanently Deferred Or No-Action Items

These should remain out of scope unless the user reopens them explicitly.

- AniList/tracker known-list cache.
- Local Source synthetic "Already Known" row.
- Auto-suggest already-linked versions when re-searching.
- Per-chapter enrichment cache.
- Network image loading fallback in Best Version preview.
- Website-native recommendation scraping across arbitrary sources.
- Larger 500/1000 Source Evaluation batches.
- Additional extension repositories bundled by default without strong trust review.

## Documentation Problems To Fix Before Closing v0.7

### `NEXT_WORK.md` Has Stale Open Items

The following should be changed:

- Mark ScreenErrorMessage hardcoded item resolved.
- Mark PromptRequired rec-quality cleanup item resolved or QA-only.
- Keep Top Picks contribution display as the only small confirmed implementation gap if not implemented.

### `KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md` Is Historical Now

The file says all seven phases are complete, but it still presents old v0.6.20 baseline phase instructions as if they are an active master plan.

Recommendation:

- Mark it "historical/completed roadmap" at the top, or archive it.
- Do not leave it as an "active master plan" in `README.md` unless it is rewritten to only contain current work.

### `README.md` Still Has Encoding/Mojibake Artifacts

Visible examples:

- `D1[mojibake]D4`
- `[mojibake]2[mojibake][mojibake]4 implemented`

Recommendation:

- Clean these before final v0.7 closure.

### Active Planning File Table Still Lists Old Partial Plans

`README.md` still lists older staged plans as active/partial:

- `KMK_RECS_STAGED_SETTINGS_AND_MATCHING_IMPROVEMENTS_PLAN.md`
- `KMK_RECS_V0_5_0_CROSS_EXTENSION_MATCHING_PLAN.md`

Many items from these plans are now implemented through v0.7.0-v0.7.44.

Recommendation:

- Reconcile or archive them so future AI sessions do not revive old deferred work that is already shipped.

## Public/Community Readiness Notes

This audit is about closing v0.7, not declaring a public release.

Current state:

- Personal build remains suitable for the user's update-style workflow.
- `kmkPublicTest` build line exists for side-by-side community testing.
- A full public release still needs separate release-process decisions:
  - signing,
  - artifact naming,
  - install instructions,
  - warnings/limitations,
  - issue reporting,
  - upstream contribution scope decisions.

These are not blockers to closing the v0.7 feature line.

## Recommended Closeout Path

### Option A: Close v0.7 With Docs Only

Use this if the user wants no more code in v0.7.

Do:

1. Update `NEXT_WORK.md` to remove or resolve stale open items.
2. Mark the deferred master plan historical or archive it.
3. Fix README mojibake and active-plan statuses.
4. Add a final "v0.7 line closed" note.

Result: v0.7.44 remains the final v0.7 APK.

### Option B: One Final Tiny v0.7.45 Cleanup Build

Use this if the user wants the last visible loose ends cleaned before closing.

Do:

1. Rated Manga duplicate grouping defaults on.
2. Top Picks contribution count displayed in Recommendation Settings.
3. Docs reconciliation from Option A.
4. Run focused tests plus normal verification.
5. Build `Komikku-v1.13.6-kmk.7.45-debug.apk`.

Do not include:

- new For You filters,
- refresh effort modes,
- source-scope controls,
- best-version re-search automation,
- hidden-source temporary snooze,
- large source-evaluation changes.

### Option C: Move Straight To v0.8

Use this if the user accepts v0.7.44 as final and wants larger changes next.

First v0.8 candidates:

1. broader For You filters,
2. refresh-effort modes,
3. source-scope controls,
4. local outcome learning,
5. hidden-source management/snooze.

## Final Audit Recommendation

Recommended path: **Option B only if the user wants one final cleanup APK; otherwise Option A.**

The two code-level items small enough for a final v0.7 cleanup are:

- default Rated Manga grouping on,
- show Top Picks contribution count.

Everything else should either be documentation cleanup or v0.8+.


