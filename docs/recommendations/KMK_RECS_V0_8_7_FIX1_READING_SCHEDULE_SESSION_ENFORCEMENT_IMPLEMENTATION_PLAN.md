# KMK-Recs v0.8.7-fix1: Reading Schedule Session Enforcement

**Status:** Planning
**Version:** KMK-Recs v0.8.7-fix1
**Scope:** Private fix build
**Parent implementation:** v0.8.7 reading schedule work
**Primary area:** Reader access enforcement and schedule-grace lifecycle

## 1. Objective

Fix the reading-schedule bypass in which a user can open a manga during a restricted schedule window, receive permission to finish the current chapter, leave the reader, and then open another manga or chapter with a newly created reader session that receives another fresh allowance.

The intended behavior is precise:

- A reader already active when the schedule becomes restricted may finish the chapter that was already open.
- A reader opened after the restriction is already active must be blocked.
- Leaving the reader ends the chapter-finish allowance.
- Opening another manga, another chapter, or a recreated reader Activity must never create a new allowance merely because the previous reader had one.

This is a fix to enforcement semantics, not a change to the existing schedule-window editor or timer UI.

## 2. Required preflight

Before editing code, inspect and document the current behavior in the working tree:

1. Read `docs/recommendations/DOCUMENTATION_RULES.md`, `docs/recommendations/README.md`, `docs/recommendations/CURRENT_STATE.md`, and `docs/recommendations/NEXT_WORK.md`.
2. Read the existing v0.8.7 schedule plans and implementation reports.
3. Inspect:
   - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`
   - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`
   - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleModels.kt`
   - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleResolver.kt`
   - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleStore.kt`
   - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/timer/ReaderTimerCoordinator.kt`
   - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/timer/ReaderTimerReducer.kt`
   - all related unit tests.
4. Identify the exact Activity/ViewModel creation, foreground, background, chapter-change, and reader-exit paths.
5. Confirm whether the current implementation represents the schedule grace period in memory only or persists it anywhere. It must not become a persistent permission.
6. Do not begin implementation until the preflight findings are recorded in the implementation report.

## 3. Root-cause correction

The current logic starts schedule grace when the coordinator is idle and the resolved schedule is restricted. That condition is insufficient because a newly created reader session also starts idle.

Replace the implicit `idle means eligible` rule with an explicit eligibility transition:

- `READER_OPENED_WHILE_ALLOWED`: eligible to continue normally; if the schedule later changes to restricted, grant one current-chapter grace allowance.
- `READER_OPENED_WHILE_RESTRICTED`: immediately restricted; do not grant grace.
- `READER_BECAME_RESTRICTED_WHILE_ACTIVE`: grant only the current-chapter allowance.
- `READER_LEFT`: clear the allowance and all session-specific schedule state.
- `READER_REOPENED`: start a new session and evaluate the current schedule from scratch; never inherit the previous session's allowance.

The implementation may use an explicit session state, a schedule-transition marker, or an equivalent existing architecture pattern. Do not add a second independent scheduling system. Reuse the existing schedule resolver and timer coordinator where possible.

## 4. Reader session rules

The implementation must enforce these rules at the reader boundary:

### 4.1 Initial open

When a manga reader is created:

- Resolve the current local schedule state.
- If the result is `ALLOWED` or disabled, open normally.
- If the result is `RESTRICTED`, do not start chapter grace. Show the existing restriction behavior and prevent reading.
- Do not infer eligibility from whether the timer coordinator is idle.

### 4.2 Restriction transition during reading

When an already active reader changes from `ALLOWED` to `RESTRICTED`:

- Preserve access to the chapter that was already open.
- Grant exactly one current-chapter grace allowance.
- Do not grant an extra chapter under the schedule policy unless the existing product requirement explicitly permits it; the current intended policy is finish-current-chapter only.
- Record the manga/chapter/session identity associated with the allowance.

### 4.3 Chapter navigation

While the schedule is restricted:

- Automatic completion of the already-open chapter may terminate the grace allowance.
- Manual selection of another chapter must not silently grant another allowance.
- Backward navigation must not consume or renew the allowance.
- A chapter loaded through a dialog, deep link, restored state, or another reader navigation route must pass through the same restriction gate.
- If a navigation action is blocked, preserve the current reader state and show the normal restriction UX rather than leaving a half-loaded chapter.

### 4.4 Exit and recreation

When the reader Activity/ViewModel is destroyed, closed, or replaced:

- Clear the session-specific grace entitlement.
- Do not persist it in saved state, navigation arguments, database rows, or preferences.
- Do not let Activity recreation, rotation, process-visible recreation, or returning from another screen create a new entitlement.
- A new reader session must be evaluated against the current schedule before content is made readable.

## 5. Background and foreground behavior

Review the existing foreground/background callbacks and make them transition-aware:

- Backgrounding must pause or preserve only the existing active session state as appropriate.
- Returning to the app must re-resolve the schedule.
- If the reader was already active before restriction, preserve the one current-chapter allowance.
- If the reader was not actively eligible before restriction, do not create grace on resume.
- A temporary disconnect, Activity recreation, or UI recomposition must not reset the schedule state.
- Avoid starting duplicate timers or duplicate schedule evaluations on every resume.
- Ensure cancellation and cleanup are idempotent.

## 6. UI behavior

Use the existing reader restriction presentation and avoid introducing product-facing terminology about development channels. The UI must:

- Clearly explain why a newly opened manga cannot be read during a restricted window.
- Explain when the current chapter is allowed to finish, if that allowance applies.
- Avoid implying that leaving and reopening grants another chapter.
- Keep the restriction state stable while navigation is blocked.
- Avoid showing a completed/allowed state while a newly selected chapter is actually prohibited.

Do not change the schedule editor unless verification finds a regression directly caused by this fix. The existing v0.8.7 editor supports multiple windows, all-day windows, device time format, and cancellation; preserve that behavior.

## 7. Persistence and compatibility

- No new database migration should be added for an in-memory reader-session entitlement unless the existing architecture proves persistence is required; persistence is specifically undesirable for this permission.
- Preserve existing serialized schedule formats and old three-field schedule records.
- Do not alter existing schedule preferences or timer preference keys unnecessarily.
- If a new enum/state is required, keep it local to the reader scheduling domain and document its lifecycle.
- Ensure stale state cannot survive app restart or reader exit.

## 8. Tests required before build

Add or update focused tests using existing project testing patterns.

### 8.1 Pure schedule tests

Retain coverage for:

- all-day windows,
- multiple windows,
- overnight windows,
- weekday boundaries,
- 12-hour/24-hour display behavior where applicable,
- legacy schedule serialization.

### 8.2 Session eligibility tests

Add deterministic tests for:

1. Reader opened while allowed, then schedule becomes restricted: current chapter remains readable.
2. Reader opened while restricted: no grace allowance is created.
3. Reader opened while restricted, exited, then another manga opened: still blocked.
4. Reader opened while restricted, exited, then the same manga reopened: still blocked.
5. Active reader enters restriction, then Activity is recreated: no second allowance is created.
6. Active reader enters restriction, backgrounds, and resumes: the original allowance remains bounded to the same session/chapter.
7. Manual next-chapter selection during restriction is rejected.
8. Manual previous-chapter selection during restriction is rejected or handled according to the existing restriction policy, but never renews grace.
9. Automatic forward completion follows the configured finish-current-chapter policy exactly once.
10. Repeated resume/evaluate calls do not duplicate timers or extend grace.
11. Reader exit clears entitlement before a new reader opens.
12. A blocked navigation attempt does not corrupt the current chapter or reader state.

### 8.3 Regression tests

Verify that:

- unrestricted reading is unchanged,
- schedule disabled is unchanged,
- the existing timer feature remains unchanged,
- natural chapter progression still works when permitted,
- the reader does not crash when the schedule changes during loading,
- cancellation and Activity destruction are safe and idempotent.

## 9. Static and device verification

Before declaring completion:

- Run formatting checks.
- Run all relevant reader, schedule, and timer unit tests.
- Run the complete debug unit-test suite.
- Build the private debug APK.
- On a real device, verify:
  - open during allowed hours, then cross into restricted hours;
  - finish the current chapter;
  - leave and open another chapter;
  - reopen the app during restricted hours;
  - background and foreground the app;
  - rotate the device;
  - lock and unlock the device;
  - test all-day, normal, overnight, and multiple-window schedules.
- Capture diagnostics for any blocked transition or unexpected allowance.

## 10. Documentation and versioning

Update only after behavior and tests are complete:

- Add an implementation report linked from the plan.
- Update `CURRENT_STATE.md` and `NEXT_WORK.md` with the actual shipped state.
- Record the fix under the existing 0.8.x line as `v0.8.7-fix1` or the repository's established equivalent; do not create a new 0.9 major line.
- Add a What's New entry for the fix using user-facing behavior, not internal development terminology.
- Keep APK naming aligned with the actual version code and existing naming convention.
- Place the resulting private APK in the established private handoff location.
- Do not describe the build as “private” or “public” inside the app UI; those are release-process terms only.

## 11. Completion criteria

The fix is complete only when:

- a newly opened reader is blocked during restricted hours;
- an already-active reader can finish only its current chapter;
- leaving and reopening cannot renew the allowance;
- manual navigation cannot bypass the restriction;
- Activity lifecycle events cannot renew or duplicate the allowance;
- all required automated and real-device tests pass;
- documentation and versioning are reconciled;
- the private debug APK is built and handed off with the correct name.

Do not produce a final APK after only partial implementation. Complete the code, tests, verification, documentation, and cleanup as one fix before handing off the build.
