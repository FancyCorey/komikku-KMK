# v0.8.7-fix1 and v0.8.8 Detailed Execution Addendum

This addendum supersedes summary-level instructions. Claude must treat both referenced plans as executable engineering specifications and must inspect live code before editing.

**Status:** Both plans implemented and verified in code (2026-07-16), shipped together as
`KMK-Recs v0.8.8` (VERSION_CODE 758). See
`docs/recommendations/KMK_RECS_V0_8_7_FIX1_AND_V0_8_8_IMPLEMENTATION.md` for the mandatory preflight
findings (including a larger-than-stated Phase 1 defect and the confirmed Phase 4 root cause), the
per-phase implementation detail, documented scope decisions (Phase 3 index-only, Phase 4
reconciliation-not-merge), tests, and the manual-QA list. Not claimed complete beyond what that report
documents.

## Mandatory preflight

Read `docs/IMPLEMENTATION_PLAN_STANDARD.md`, the recommendation encyclopedia, `CURRENT_STATE.md`, `NEXT_WORK.md`, the v0.8.7 schedule plans, the v0.8.8 plan, and the live reader, schedule, rated-manga, recommendation-settings, and source-evaluation code. Record exact current file paths, symbols, callers, state owners, dispatchers, persistence, and tests. If code differs from a plan, stop and update the implementation report before coding.

## v0.8.7-fix1 execution contract

The schedule grace entitlement must be session-bound. A reader opened while restricted receives no grace. A reader already active when the state transitions from allowed to restricted may finish only its currently open chapter. Leaving the reader clears the entitlement. Reopening the same or another manga cannot create a new entitlement. Manual chapter selection, deep links, restored state, Activity recreation, rotation, background/foreground, and resume must pass through the same gate and must not renew grace. Do not use coordinator-idle as proof of eligibility. Add lifecycle tests for every transition and verify the existing schedule editor/serialization remains unchanged.

## v0.8.8 execution contract

### Completion rating

Find the authoritative chapter-completion callback and latest-chapter predicate. Add a pure, tested decision that triggers only after genuine completion of the latest available chapter. Deduplicate by manga/chapter/session. Reuse the existing exclusive rating mutation and confirmed-version flow. Provide dismissible Love, Like, Dislike, and existing Seen/Not Interested actions. After a rating, offer other confirmed versions; Yes opens the existing selector, No returns to manga detail, and cancel preserves the first rating. Never serialize screen/match objects in Android state.

### Settings UI

Reuse the existing More/settings section-row components. Convert the recommendation settings root into an index with sections for For You, source priority, taste/tags, evaluation, non-installed discovery, background/network/installer behavior, and diagnostics. Convert source evaluation into sections while keeping remaining counts, active progress, cancel, and blocking errors visible. Use localized strings, existing theme tokens, accessible icons, phone-safe wrapping, and stable back navigation. Do not hide active work or errors behind another level.

### Reassessment continuation

Trace count -> UI event -> ViewModel -> candidate query -> filters -> cursor/order -> worker -> per-source evaluation -> persistence -> aggregation -> refresh. Reproduce the visible outdated rows plus immediate failure. Extract one shared pure candidate policy used by both count and worker. Explicit reassessment must include outdated rows even when normal “skip evaluated” is enabled. Continuation must process the next stable batch after the completed batch, including beyond 10/25/50/100, without duplicate processing. Isolate source failures and distinguish no candidates, filtered candidates, offline, installation/loading, probe, persistence, cancellation, and unexpected errors. Never convert an empty candidate set into generic evaluation failure.

## Required tests and gate

Test latest/non-latest completion, dismissal, exclusive ratings, group rating/cancel, recreation, schedule interaction, section navigation, phone layout states, 250+ candidates over multiple batches, mixed failures, cancellation/resume, stable ordering, filter/count agreement, retry, persistence, and no duplicate work. Run formatting, focused tests, full unit tests, build, and real-device checks for rotation, lock screen, backgrounding, network loss, schedule boundaries, prompts, and multi-batch continuation before producing one final 0.8.x APK. Update implementation report, current state, next work, encyclopedia, version history, What's New, and APK handoff only after all phases pass. Do not expose internal release-channel terminology in the app.
