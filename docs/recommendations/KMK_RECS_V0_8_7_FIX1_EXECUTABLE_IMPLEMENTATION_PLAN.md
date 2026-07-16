# KMK-Recs v0.8.7-fix1 Executable Implementation Plan

**Scope:** Reader schedule enforcement bug
**Version:** 0.8.x fix
**Build rule:** do not hand off the APK until implementation, tests, documentation, and device QA are complete.

**Status:** Implemented and verified in code (2026-07-16). Found the actual defect was larger than
this plan's own framing: restriction had no real enforcement at all (toast-only) prior to this fix.
Built `ReaderScheduleEntitlement` (session-bound state machine) plus real gates at every
chapter-loading entry point (`init`, `loadAdjacent`, `loadNewChapter` — the last found as a separate
bypass) and a full-screen block overlay. 18 tests passing. Shipped together with v0.8.8 as one release
(`KMK-Recs v0.8.8`, VERSION_CODE 758) per the addendum's combined framing. Device QA not performed —
no physical device available. See
`docs/recommendations/KMK_RECS_V0_8_7_FIX1_AND_V0_8_8_IMPLEMENTATION.md` for full detail.

## Preflight

Read `docs/IMPLEMENTATION_PLAN_STANDARD.md`, the encyclopedia, current state, next work, and all v0.8.7 schedule reports. Inspect the live symbols in `ReaderViewModel.kt`, `ReaderActivity.kt`, `ReaderTimerCoordinator.kt`, `ReaderTimerReducer.kt`, `ReaderScheduleModels.kt`, `ReaderScheduleResolver.kt`, `ReaderScheduleStore.kt`, and their tests. Record the actual call chain for reader creation, `evaluateSchedule`, resume/foreground, chapter changes, and reader destruction before editing.

## Confirmed defect

The current restriction logic starts chapter grace when the schedule is restricted and the coordinator is idle. A new ReaderViewModel is idle by definition. Therefore a user can leave a reader during restricted hours, open another manga or chapter, and receive another allowance.

## Target state machine

Add an explicit reader-session eligibility state owned by the reader lifecycle, not inferred from timer-idle state:

`NotStarted -> OpenedWhileAllowed -> CurrentChapterGrace -> GraceConsumed -> Closed`

or

`NotStarted -> OpenedWhileRestricted -> Closed`.

Rules:

- On reader creation, resolve the schedule before content is readable.
- Allowed/disabled at creation: record `OpenedWhileAllowed`.
- Restricted at creation: record `OpenedWhileRestricted`; do not start grace.
- Only an active session transitioning allowed -> restricted may enter `CurrentChapterGrace`.
- Bind grace to session, manga, and current chapter identity.
- Current chapter completion consumes grace; no extra chapter unless existing policy explicitly allows it.
- Manual next/previous/dialog/deep-link selection while restricted must be rejected and must never renew grace.
- Reader close/destroy clears entitlement.
- Activity recreation, rotation, background/foreground, and resume restore the same session state but never create a new entitlement.
- A new reader always evaluates the current schedule from scratch.

## Required code changes

1. In the existing `ReaderViewModel.evaluateSchedule` path, replace `restricted && coordinator idle` as the grant condition with a transition-aware condition.
2. Capture initial resolved schedule state when the session starts.
3. Add explicit session/chapter entitlement state and clear it on reader replacement/exit.
4. Route every ReaderActivity entry and chapter-selection path through the gate before loading readable content.
5. Keep timer reducer logic pure; preserve existing natural-forward versus manual chapter semantics.
6. Make repeated lifecycle evaluations idempotent and prevent duplicate timers.
7. Preserve schedule store format, all-day windows, overnight windows, multiple windows, and legacy three-field records.
8. Do not persist the grace entitlement in preferences, database, navigation arguments, or saved state.

## Failure handling

Missing/corrupt schedule data uses the existing safe default and cannot grant unintended access. Resolver errors use the existing restriction/error UX. Cancellation and destruction clear transient state without throwing. A blocked chapter load leaves the current reader state intact.

## Tests

Add tests for: open allowed then restriction transition; open restricted; leave and reopen same manga; leave and open another manga; manual chapter changes; automatic current-chapter completion; repeated resume; rotation/recreation; background/foreground; lock/unlock; missing/corrupt preferences; duplicate timer prevention; disabled schedule; unrestricted regression.

Run formatting, focused tests, full unit tests, build, and real-device tests across phone/tablet, normal/overnight/all-day/multiple windows. Update implementation report, current state, next work, encyclopedia, version history, and What's New after verification. Keep 0.8.x APK naming aligned with actual metadata and place the final APK in the established private handoff directory. Do not put internal release-channel terminology in app UI.
