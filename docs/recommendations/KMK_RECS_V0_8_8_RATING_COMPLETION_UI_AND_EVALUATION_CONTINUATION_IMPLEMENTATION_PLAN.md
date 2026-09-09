# KMK-Recs v0.8.8: Chapter Completion Rating, Recommendation Settings UX, and Evaluation Continuation

**Status:** Planning
**Version:** KMK-Recs v0.8.8
**Scope:** Follow-up implementation after v0.8.7-fix1
**Relationship to v0.8.7-fix1:** Separate feature/fix scope; may be built into the same APK only after both scopes are implemented and verified together

## 1. Purpose

This plan contains three related but separable workstreams:

1. Offer a rating prompt after the user finishes the latest available chapter.
2. Reorganize the recommendation settings and source-evaluation screens so they are scannable, sectioned, and usable on phones.
3. Fix the continuation flow for outdated source evaluations, which currently can report “evaluation failed” even when outdated evaluations remain.

The implementation must reuse existing rating, cross-extension matching, recommendation, navigation, scheduling, and source-evaluation infrastructure. It must not create parallel rating or matching systems.

Do not build a release after implementing only one workstream. Complete code, tests, documentation, cleanup, and verification for all approved workstreams before producing the APK.

## 2. Required preflight

Before editing code, read:

- `docs/recommendations/DOCUMENTATION_RULES.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/IMPLEMENTATION_PLAN_STANDARD.md`
- the v0.8.7 schedule implementation and v0.8.7-fix1 schedule-session plan
- the latest rated-manga, group-recommendation, source-evaluation, and UI-refinement reports

Then inspect the live source tree and record exact files/functions before implementation:

- reader chapter-completion and chapter-navigation state;
- manga detail and reader exit navigation;
- existing rating actions and cross-extension rating flow;
- rated manga collection screens and group handling;
- recommendation settings screen/model;
- source evaluation screen/model/repository/use cases;
- evaluation database queries, status fields, batch selection, and error persistence;
- existing More/settings section navigation patterns;
- current localization resources and icon conventions.

Do not rely on older plans if the code has already changed. The code and current-state documents are authoritative; update stale documentation after implementation.

## 3. Workstream A: latest-chapter completion rating prompt

### 3.1 Trigger semantics

The prompt must appear only when all of the following are true:

- the user has completed the latest available chapter for that manga at the moment completion is processed;
- the chapter completion is genuine, not merely opening the chapter, jumping pages, rotating, restoring state, or leaving the reader;
- the manga is not already blocked by the reading schedule restriction;
- the same completion event has not already displayed the prompt for the current manga/chapter/session.

Do not show the prompt after an older chapter, when newer chapters are available, or when the user exits before completing the chapter.

The latest-chapter check must use the reader’s existing chapter ordering and fetched chapter data. Do not compare chapter names as strings or assume numeric chapter names are always available.

### 3.2 Prompt flow

After completion, show a lightweight, dismissible rating prompt with these actions:

- Love
- Like
- Dislike
- Mark as seen / existing non-rating exclusion action, using the current product terminology and semantics
- Dismiss/close

Use the existing single-rating mutation path so a manga cannot simultaneously be loved, liked, and disliked. A new selection must overwrite the prior rating consistently across all linked versions according to the existing confirmed-group behavior.

After a rating is applied, show a second prompt asking whether to apply the same rating to other confirmed versions.

- Yes opens the existing cross-extension matching/group-version selection flow.
- No returns to the manga screen, matching the normal back-from-reader destination.
- Close/dismiss at either step returns to the appropriate previous screen without changing state.
- If no confirmed alternate versions exist, do not show an empty second step; return directly after the first action or show only the existing relevant flow.
- If the user cancels version selection, preserve the first rating and return to the manga screen.

Do not silently rate unconfirmed search candidates. Existing default-selection behavior may be reused only after the user enters the version-selection flow.

### 3.3 State and lifecycle safety

- Keep prompt state in the existing screen/state architecture rather than serializing a non-serializable mode object into navigation arguments.
- Handle Activity recreation, rotation, backgrounding, and process-visible restoration safely.
- Do not show duplicate prompts after recomposition or repeated completion callbacks.
- Do not show the prompt if the user is opening a chapter from a restricted schedule state and has not completed it.
- Ensure the prompt cannot bypass the v0.8.7-fix1 reading restriction.
- Ensure back, close, cancel, and outside-dismiss actions are all safe and idempotent.

### 3.4 UX and accessibility

- Use existing Material components, spacing, typography, iconography, and motion patterns.
- Keep the first prompt compact enough for a phone screen.
- Use icons with accessible content descriptions and text labels where the action is not self-evident.
- Use a short transition between rating and alternate-version steps only if it does not delay or obscure dismissal.
- Extract all user-facing text to the project’s localization system.
- Do not place development/release-channel terminology in the UI.

### 3.5 Tests

Add tests for:

- latest chapter completed triggers the prompt;
- non-latest chapter does not trigger it;
- leaving before completion does not trigger it;
- repeated completion callbacks do not duplicate it;
- each rating writes the expected exclusive rating;
- dismissing the first prompt makes no change;
- selecting a rating then cancelling version selection preserves the first rating;
- confirming alternate versions applies the same rating through the existing group flow;
- no alternate versions skips the second prompt;
- rotation/backgrounding does not duplicate or lose prompt state;
- restricted schedule state cannot be bypassed through the prompt.

## 4. Workstream B: recommendation settings and source evaluation UX

### 4.1 Design target

The current settings/evaluation screens contain too many controls and explanations in one continuous page. Reorganize them into navigable sections using the same interaction pattern already used by the app’s More/settings screens.

The first screen should be a concise section index, not a long form. Each section must show:

- a clear title;
- a familiar icon;
- a one-line purpose summary;
- current important state where useful;
- a click target that opens the detailed controls.

Do not hide essential status, errors, or an active evaluation behind multiple levels of navigation.

### 4.2 Recommendation settings sections

Use the existing controls and group them into stable sections such as:

- For You behavior and visibility;
- source priority and source ordering;
- source inclusion/exclusion and blocked categories;
- taste and tag preferences;
- evaluation and reassessment;
- recommendation filters and limits;
- background processing, notifications, and network behavior;
- diagnostics and reset actions.

The exact labels must be reconciled against current implemented settings rather than invented. Keep settings that affect immediate For You behavior easy to reach.

### 4.3 Source evaluation sections

Organize the source-evaluation screen into:

- evaluation status and remaining counts;
- start/continue/reassess actions;
- evaluation mode and installer configuration;
- filtering and candidate visibility;
- past evaluations;
- recommendation-compatibility results;
- quarantine/blocked-source management;
- diagnostics and error details.

The screen must preserve visible status for:

- unassessed sources;
- outdated/reassessment-needed sources;
- failed evaluations;
- quarantined sources;
- blocked sources;
- installed-source filtering.

Do not keep quarantine or blocked lists permanently at the top if the current design already moved them behind optional sections.

### 4.4 Interaction requirements

- Preserve scroll position and navigation state when entering/leaving a section.
- Make active evaluation progress and errors visible without requiring the user to guess which section contains them.
- Use clear loading, empty, error, and retry states.
- Use compact rows and supporting detail expansion for phone layouts.
- Avoid nesting cards inside cards or creating a wall of separate bordered panels.
- Ensure buttons do not overflow or become unreadable at narrow widths.
- Preserve drag/reorder behavior and saved source priority order.
- Do not rename user-facing concepts merely for internal versioning reasons.

### 4.5 Tests

Add UI/state tests for:

- section index navigation;
- back navigation from every detail section;
- state preservation after returning;
- evaluation progress visibility;
- errors and retry actions;
- phone-width rendering and text wrapping;
- source priority reorder persistence;
- disabled/blocked/installed filtering;
- accessibility labels for icons and controls.

## 5. Workstream C: continue outdated source evaluations

### 5.1 Reported failure

The source-evaluation UI can show outdated or “reassess needed” entries, yet the continuation action can immediately end in “evaluation failed” or report zero remaining candidates. This must be traced through the complete path rather than fixed only in the button label.

### 5.2 Required trace

Inspect and document the full call chain:

1. UI calculation of the remaining count.
2. UI selection of the continuation/reassessment action.
3. ViewModel intent and batch-size handling.
4. Repository/use-case candidate query.
5. Filters for installed, blocked, quarantined, explicit, hidden, and already-evaluated sources.
6. Evaluation job creation and installer mode selection.
7. Per-source result persistence.
8. Progress/error aggregation.
9. Refresh of the source-evaluation screen.

The displayed count and the actual query must use the same candidate policy. A source must not count as remaining in one query and be excluded by another without an explicit visible reason.

### 5.3 Continuation semantics

Implement continuation as a cursor/progress operation over the complete eligible set, not as “restart the first batch.” It must:

- identify the next eligible sources after completed batches;
- continue past the first 10/25/50/100 selection;
- preserve stable ordering across runs;
- skip only sources intentionally excluded by the current settings;
- allow a later batch to continue when earlier sources were successful, failed, or skipped;
- not re-run completed sources unless the user explicitly chooses reassessment;
- not silently convert a query-empty state into a generic evaluation failure.

If all remaining candidates are filtered out, show the exact reason categories and count rather than “evaluation failed.”

### 5.4 Error handling

Separate these states:

- no eligible sources remain;
- all candidates are hidden by filters;
- source installation failed;
- source loading failed;
- network failure;
- evaluation probe failed;
- database persistence failed;
- job cancellation;
- unexpected exception.

Each source failure must be isolated so one broken extension does not abort the batch. Persist a concise diagnostic and allow retry. Do not catch exceptions and replace them with a generic failed state without retaining the root cause.

### 5.5 Tests

Add tests for:

- 250 eligible sources processed in three or more continuation batches;
- continuation after a successful first batch;
- continuation after mixed success/failure;
- continuation after cancellation;
- stale entries remaining visible and eligible for reassessment;
- installed/blocked/quarantined filters producing matching counts;
- stable ordering and no duplicate batch processing;
- empty eligible set with explanatory reasons;
- repository exception without losing prior results;
- database persistence failure without falsely reporting success;
- refresh showing the newly updated status.

## 6. Shared architecture and cleanup requirements

- Reuse existing rating mutation, group matching, navigation, schedule, and evaluation abstractions.
- Do not create a second cross-extension rating system.
- Do not duplicate candidate filtering in UI and repository layers; define or reuse one shared policy.
- Keep pure eligibility/counting helpers separate from orchestration so they can be unit-tested.
- Remove dead code only after confirming no existing screen or migration depends on it.
- Preserve official Komikku formatting, naming, localization, coroutine, database, and testing conventions.
- Avoid broad refactors unrelated to these workstreams.

## 7. Verification and handoff

Before building:

- run formatting checks;
- run targeted reader, rating, recommendation-settings, and source-evaluation tests;
- run the complete unit-test suite;
- build the debug APK using the existing 0.8.x version convention;
- verify APK naming matches the actual version code;
- place the APK in the established private handoff directory;
- perform real-device checks for latest-chapter completion, prompt cancellation, rotation, backgrounding, narrow phone layout, source continuation, network loss, and retry.

Update the implementation report, `CURRENT_STATE.md`, `NEXT_WORK.md`, README/index references, and What's New only after the behavior is verified. Do not put internal release-channel terms into app-visible strings.

## 8. Completion criteria

This plan is complete only when:

- latest-chapter completion can lead to an exclusive rating prompt;
- users can dismiss every step safely;
- alternate-version rating reuses the existing confirmed-group flow;
- recommendation settings and source evaluation are sectioned and usable on phones;
- continuation processes all eligible outdated sources across multiple batches;
- counts, filters, statuses, errors, retries, and persistence agree;
- schedule restriction enforcement from v0.8.7-fix1 remains intact;
- all tests and device verification pass;
- documentation, versioning, and APK handoff are reconciled.
