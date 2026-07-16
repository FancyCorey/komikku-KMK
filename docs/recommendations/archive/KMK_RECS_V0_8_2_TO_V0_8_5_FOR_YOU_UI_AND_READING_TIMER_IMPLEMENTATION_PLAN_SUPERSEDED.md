# KMK-Recs v0.8.2-v0.8.5 For You UI And Reading Timer Implementation Plan

Date: 2026-07-14
Status: APPROVED PLAN - implementation not started
Target: one coordinated implementation session and one final build after all phases pass

## 1. Purpose

This plan combines the configurable For You display count, recommendation UI refinement, active-reading timer, and optional clock-based reading schedule into one coordinated change set. The phases are checkpoints, not separate releases. Claude may compile and test between phases, but must not produce the final APK until every phase, test, documentation update, and verification step is complete.

The implementation must follow current Komikku architecture, KMR localization, Compose/Material conventions, Injekt registration style, existing preference patterns, reader lifecycle patterns, notification conventions, and repository test patterns. Do not create a parallel recommendation system, rated-manga screen system, or reader architecture.

Final APK handoff path:
C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.5-debug.apk

Development handoff wording must not appear in app-visible UI, localized strings, dialogs, or What's New content. Terms such as private, public, internal, community, and test build are implementation workflow terms only.

## 2. Required preflight

Before editing code, read and record findings from:

1. docs/recommendations/CURRENT_STATE.md
2. docs/recommendations/NEXT_WORK.md
3. docs/recommendations/DOCUMENTATION_RULES.md
4. docs/recommendations/KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md
5. docs/KMK_MARKDOWN_ENCYCLOPEDIA.md
6. RECOMMENDATION_VERSIONING.md
7. app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
8. app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
9. app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
10. app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
11. app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
12. the existing shared Loved/Liked/Disliked collection implementation
13. the official Komikku reader screen/activity/view-model lifecycle path
14. existing preference, KMR, WorkManager, notification, backup/sync, and test helpers

Search for existing equivalents before adding classes. Identify all callers before changing behavior. Record exact files inspected and any deviations in the implementation report.

## 3. Version and documentation contract

Use these coordinated milestones:

- KMK-Recs v0.8.2: configurable For You display count and recommendation UI refinement.
- KMK-Recs v0.8.4: active-reading timer.
- KMK-Recs v0.8.5: optional clock-based schedule, only if it can be completed without weakening the timer.

These are checkpoints within one coordinated implementation. Update versioning and state documents only after work is actually implemented. The final report must be:
docs/recommendations/KMK_RECS_V0_8_2_TO_V0_9_1_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md

The report must contain exact files changed, preferences/schema changes, migration decisions, tests, manual QA, deviations, known limitations, and final APK path.

## 4. Phase A - configurable For You results per source

### Existing limits to preserve

The current code has separate limits, including:

- NORMAL_RESULTS_PER_SOURCE = 10
- TOP_PICKS_ROW_CAP = 20
- TOP_PICKS_DETAIL_CAP = 50
- MAX_VISIBLE_SOURCE_ROWS = 20
- MAX_SOURCE_ATTEMPTS = 40
- RecommendationPagingSource.MAX_CROSS_EXTENSION_SOURCES = 20
- independent raw-result, enrichment, discovery-page, and query-attempt caps

The new setting controls only the visible manga count in ordinary For You source rows. It must not silently increase source count, priority coverage, query attempts, enrichment calls, discovery depth, or Top Picks limits.

### Required setting

Add a validated preference named For You items per source with options:

- 5
- 10 (default, preserving current behavior)
- 15
- 20
- 30

Invalid, missing, or corrupted values fall back to 10. Do not use unrestricted text input.

The preference must:

1. Apply to ordinary per-source For You rows.
2. Apply consistently to cached and newly fetched results.
3. Apply after shared visibility filtering and deduplication, so hidden/rated/known/not-interested items do not consume visible slots.
4. Preserve source priority order and truthful no-match/error ordering.
5. Invalidate or fingerprint the For You cache when changed.
6. Leave Top Picks row/detail limits independent.
7. Leave Source Evaluation sample limits unchanged.
8. Leave the 20-source and top-three boosted-source rules unchanged.
9. Use existing preference storage and existing backup/sync behavior where applicable. If not automatically covered, document the choice or add additive coverage.

Inspect and update SourcePreferences, the For You screen-model result construction, the cache fingerprint, and the For You settings UI. Replace only the ordinary per-source result cap currently represented by NORMAL_RESULTS_PER_SOURCE; do not replace unrelated caps.

If the source API supports requested page size, request the selected value with a hard safety cap. If it ignores page size, use bounded post-fetch trimming and do not create an unbounded pagination loop. More visible results must never create unlimited network work.

Add KMR strings for title, options, and concise performance guidance.

Tests required:

- default and corrupted-value fallback;
- all supported values;
- cache fingerprint change;
- hidden candidates not consuming the visible count;
- row stops at configured count;
- Top Picks remains independently capped;
- source-attempt and boosted-source counts unchanged;
- empty/offline/error rows remain truthful.

## 5. Phase B - recommendation UI refinement

This is a targeted integration pass, not a scoring change or complete visual rewrite.

### Design contract

Reuse mature Komikku Library components, spacing, typography, Material theme values, menu patterns, selection patterns, and accessibility conventions. Use MaterialTheme.colorScheme and existing design tokens. No hardcoded theme colors. Verify light theme, dark theme, and at least two accent-color configurations.

Use stable dimensions and responsive layouts. Horizontal control groups that can overflow a phone must use the existing wrapping or overflow pattern. Do not design for tablet width only.

### For You

Inspect toolbar, rating shortcuts, Top Picks, source rows, status indicators, loading/error/empty states, refresh, and navigation actions.

Required behavior:

- keep Loved, Liked, and Disliked shortcuts visible and understandable;
- use familiar icons with content descriptions and text where icons are ambiguous;
- keep secondary actions in existing overflow/context patterns;
- make no-match, error, outdated, disliked, and installed/hidden states compact but distinct;
- retain discoverable source-row navigation;
- preserve retry and pull-to-refresh;
- prevent layout shifts, overlap, clipped text, and bottom-navigation obstruction.

### Loved, Liked, and Disliked

Reuse the existing shared rated-manga collection screen/model. Do not build three implementations.

All three views must share:

- grid/list behavior;
- sorting;
- grouping;
- source filtering;
- linked-version actions;
- sharing/export;
- duplicate handling;
- selection mode;
- action menus.

Only rating-specific title, icon, filter, and empty state should vary through arguments/state.

Long-press and visible Select must preserve the current bulk-selection behavior. Recommendations must remain explicit menu actions, not hidden long-press behavior. Destructive actions retain confirmation and existing undo rules.

### Recommendation Settings

Reorganize the existing screen into these ordered sections using established Komikku settings patterns:

1. For You behavior: language, items per source, known/rated/not-interested visibility, minimum chapter count, refresh.
2. Source priority: reorderable list, top-three behavior, recommendation-source preference.
3. Source evaluation: batch size, installer mode, continuation/reassessment, compatibility, diagnostics.
4. Source management: installed/hidden/disliked/quality-marked visibility and recovery.
5. Discovery and cache: discovery memory/cache reset and maintenance.

Keep frequent settings near the top. Preserve state when navigating away/back. Destructive actions require confirmation. Use collapsible sections only where the existing screen/state model supports them reliably.

### Source Evaluation

Do not remove diagnostics, continuation, evidence, or recovery. Present:

- compact summary counts;
- clear primary actions;
- expandable per-source details;
- secondary diagnostics behind expansion;
- grouped sorting/filter controls;
- no permanent quarantine/blocked warning wall;
- phone-safe wrapping for installer and action controls;
- truthful idle, offline, running, canceled, completed, outdated, and error states.

### UI verification

Perform phone and tablet checks in light/dark themes where device/screenshot QA is available. Check rotation/recomposition, scrolling, selection, dialogs, menus, back handling, accessibility semantics, and narrow-width wrapping. Document unavailable manual QA honestly.

## 6. Phase C - active-reading timer (v0.8.4)

First identify the official reader lifecycle and existing reader state persistence. Do not infer reading activity from page changes alone.

### User behavior

Add a reader-toolbar/menu entry using the existing reader UI pattern. Provide:

- 15-minute, 30-minute, and 1-hour presets;
- custom bounded duration;
- start, pause, resume, reset, and stop;
- remaining-time display;
- configurable 15/10/5/1-minute warnings;
- finish-current-chapter option;
- optional one-extra-chapter allowance, limited to one;
- clear paused state outside the reader.

The timer counts only while the reader is the active visible screen, the app is foregrounded, the timer is running, and the session was not explicitly stopped/reset.

It pauses when leaving the reader, backgrounding, locking the device, opening a non-reader screen, or destroying the reader. Returning resumes only if the user did not explicitly stop/reset.

### State and time model

Implement a small pure state machine, reusing existing state patterns where possible:

- Idle
- Running
- Paused
- Warning
- ChapterGrace
- ExtraChapterGrace
- Expired

Use monotonic elapsed time for active duration. Never calculate elapsed countdown time from wall-clock subtraction. Persist only the minimum local state needed for safe restoration. Do not persist page images or sensitive reading content.

Define transitions for start/pause/resume/reset/stop, reader lifecycle, chapter change, zero during a chapter, chapter finish, extra-chapter use, process recreation, malformed state, duration changes, and device clock/time-zone changes.

### Expiry

Never forcibly close the reader, kill the app, or discard progress. At zero, show a localized non-blocking warning. If enabled, enter ChapterGrace and stop normal countdown. At chapter completion, end unless the one-extra-chapter allowance is enabled and unused. The allowance may be consumed once only.

If chapter completion cannot be detected reliably from the current reader lifecycle, use the safest existing boundary signal and document the limitation. Do not guess.

### Persistence, privacy, and notifications

Use existing preference/data-store patterns. Avoid a database migration unless structured persistence is truly required. Store no remote telemetry. Do not log titles, chapter names, page content, OCR text, or exact reading history.

Do not create a permanent foreground service for a timer that pauses while backgrounded. Prefer in-app warnings. Any notification must not claim the timer is active while paused/backgrounded.

Tests required:

- countdown and pause/resume;
- foreground/background and reader/non-reader transitions;
- warning thresholds firing once;
- current-chapter grace;
- one-extra-chapter maximum;
- reset/stop;
- process recreation and malformed state;
- monotonic time;
- chapter transition races;
- no timing during Library/settings/source browsing.

## 7. Phase D - optional clock-based schedule (v0.8.5)

Implement only after Phase C is stable.

Use a separate schedule model, not additional branches in the countdown state machine. Support:

- selected weekdays;
- one or more local time windows;
- allowed or restricted mode;
- enable/disable;
- deterministic overlapping-window handling.

Evaluate on reader open, reader resume, foreground return, and timer state changes. Use local wall clock only for determining the current schedule window. Do not use it for elapsed countdown timing.

The first version applies restrictions only inside Komikku. It must not claim to restrict other apps or provide OS parental controls. If a window ends during a chapter, use the same non-destructive grace behavior as the timer.

Handle midnight crossing, day boundaries, time-zone/DST changes, disabled schedules, invalid values, overlapping rules, and schedule edits while reading. If notifications require new permissions or a background service, defer that notification portion and document it.

Test normal windows, midnight windows, day boundaries, overlaps, disabled schedules, clock changes, schedule edits, expiry during a chapter, and corrupted preferences using a fixed clock.

## 8. Cross-cutting requirements

1. All new UI strings use KMR.
2. All preferences have validated defaults and safe corruption fallback.
3. The timer makes no network calls.
4. Opening timer UI never triggers recommendation/evaluation work.
5. The UI phase must not alter recommendation scoring.
6. No new app-wide permission without a documented necessity.
7. Coroutines are cancellation-safe and lifecycle-bound.
8. Dialogs and menus dismiss correctly by back/outside tap where appropriate.
9. Destructive actions require confirmation and are not triggered by recomposition.
10. Avoid duplicate code between rated collections and timer/schedule state.
11. Do not expose raw exception text or sensitive manga/chapter/page data.
12. Preserve backup/sync compatibility and document local-only decisions.
13. Preserve migration numbering; add migrations only when required.
14. Keep source-quality dislike, For You dislike, and explicit-content blocking separate.
15. Do not add app-visible build-channel terminology.

## 9. Verification

Use the repository's local JDK 17 setup, then run:

    .\gradlew.bat spotlessApply
    .\gradlew.bat spotlessCheck
    .\gradlew.bat :app:testDebugUnitTest --tests "*Recommendation*"
    .\gradlew.bat :app:testDebugUnitTest --tests "*SourceEvaluation*"
    .\gradlew.bat :app:testDebugUnitTest --tests "*Rated*"
    .\gradlew.bat :app:testDebugUnitTest --tests "*Reader*"
    .\gradlew.bat :app:testDebugUnitTest
    .\gradlew.bat assembleDebug

If a selector is unsupported, run concrete test classes and record them. Distinguish new failures, pre-existing failures, skipped tests, and unavailable device/manual tests.

Before handoff inspect for hardcoded strings/colors, build-channel wording, accidental recommendation-query changes, unbounded network loops, lifecycle leaks, duplicate implementations, missing Injekt registrations, missing backup/sync decisions, unsafe destructive actions, and incorrect version metadata.

Copy the final APK to:
C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.5-debug.apk

## 10. Completion rule

Do not mark complete or produce the final APK until Phase A, Phase B, and Phase C are implemented and tested; Phase D is either implemented/tested or explicitly deferred; documentation/indexes are reconciled; formatting, tests, and build are verified; the implementation report exists; and the APK is copied to the required handoff folder.



