# KMK-Recs v0.8.4 - Active Reading Timer Plan

Status: **implemented and shipped in KMK-Recs v0.8.5**. See `KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md` for the implementation report, including the post-review correction to chapter-boundary/one-extra-chapter handling (manual/previous-chapter navigation never consumes the allowance, only natural forward progression does).

## Scope

Add a local reading-duration timer that counts only while the official reader is active in the foreground. It must not be a general app-usage timer and must not require a permanent background service.

## Existing reader integration points

Inspect exact behavior in:

- app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt
  - onResume, onPause, reader composition, current chapter, next/previous chapter actions;
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt
  - currentChapter, saved-state fields, chapter loading, chapter transitions, history/update methods;
- app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderTopBar.kt;
- app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt;
- app/src/main/java/eu/kanade/presentation/reader/ChapterTransition.kt;
- existing reader settings/preferences, KMR resources, and test clock/lifecycle helpers.

Use the official ReaderActivity/ViewModel lifecycle. Do not put timer accounting in page image loaders, viewers, network code, or recommendation code.

## User flow

Add a reader toolbar/menu action named Reading timer. Opening it shows the current state and controls:

- Start with 15 minutes, 30 minutes, 1 hour, or custom duration.
- Pause, resume, reset, stop.
- Remaining time.
- Warning interval checkboxes for 15, 10, 5, and 1 minute.
- Finish current chapter after expiry.
- Allow one extra chapter after expiry, disabled by default and capped at one.
- Clear explanation that the timer pauses outside the reader.

Use existing reader dialog/sheet patterns. Do not force a blocking setup flow when the reader opens.

## Pure state machine

Create a pure, testable timer domain component using existing repository naming/style. States:

- Idle
- Running
- Paused
- Warning
- ChapterGrace
- ExtraChapterGrace
- Expired

Events:

- Start(duration, warning policy, grace policy)
- Pause
- Resume
- Reset
- Stop
- ReaderForeground
- ReaderBackground
- ChapterChanged
- ChapterFinished
- Tick(monotonic elapsed)
- ProcessRestored
- InvalidPersistedState

Use monotonic time for elapsed active reading. Wall-clock changes must not change remaining duration. Warning thresholds fire once per session. Explicit Stop/Reset clears the active session. Leaving the reader pauses rather than consumes time. Returning resumes only if the session was not explicitly stopped/reset.

## Chapter behavior

When the countdown reaches zero:

- never close the activity;
- never kill the app;
- never discard reading progress;
- show a localized warning;
- if finish-current-chapter is enabled, enter ChapterGrace and stop normal countdown;
- at an existing chapter boundary, end unless one-extra-chapter is enabled and unused;
- if enabled, enter ExtraChapterGrace once, then end at the next chapter boundary.

Use the existing currentChapter and chapter-transition signals. Do not assume that reaching the last image is a reliable chapter-complete event unless the existing reader explicitly exposes that signal. If only transition to the next chapter is reliable, use that and document the decision.

## Lifecycle and persistence

The timer coordinator must be lifecycle-bound to ReaderActivity/ViewModel and cancellation-safe. It must pause from onPause/onStop and resume from onResume only when the session state permits. Rotation must not reset or duplicate the timer. Process recreation must restore a valid session using existing SavedState/persistence conventions; malformed state falls back to Idle without crashing.

Persist only duration, remaining/active state, warning-fired state, grace state, and chapter identity as needed. Do not persist images, OCR text, titles, or remote data. Do not add a database migration unless the repository's existing persistence cannot safely restore the session.

No permanent foreground service. No network calls. In-app warnings are preferred. A notification is allowed only if it is lifecycle-truthful and does not claim active timing while the reader is backgrounded.

## Tests

Pure tests must cover start/pause/resume/reset/stop, monotonic ticks, warning thresholds once-only, background pause, reader resume, chapter grace, one-extra limit, chapter races, process restoration, invalid state, duration changes, and clock/time-zone changes.

Integration/manual tests must cover ReaderActivity open/close, back navigation, rotation, screen lock, app background, returning to the reader, next/previous chapter, downloaded and online chapters, reader settings dialogs, and no timing while Library/settings/source screens are open.

## Acceptance criteria

The timer counts only active foreground reader time, survives rotation safely, pauses outside the reader, never forcibly interrupts or loses progress, has bounded and understandable controls, is fully localized/theme-aware, has no sensitive logging, and does not affect recommendations or source evaluation.



## Code-level implementation addendum from source review

ReaderActivity already owns onPause/onResume, renders ReaderAppBars, exposes state.currentChapter/currentPage, and routes next/previous chapter through loadNextChapter/loadPreviousChapter. ReaderViewModel already owns SavedState fields such as chapter_id/page_index, currentChapter, chapter loading, updateHistory, and chapter transitions. Integrate the timer at this boundary rather than inside PageLoader, Viewer, ReaderPage, or network code.

ReaderActivity.onPause currently performs history/RPC cleanup and onResume restarts reader behavior. Add timer pause/resume calls adjacent to the lifecycle boundary with cancellation-safe, idempotent methods. Rotation must call pause/resume without creating a second ticker. ReaderActivity's ReaderAppBars call site is the insertion point for a timer action. ReaderViewModel's existing currentChapter and loadNextChapter/loadPreviousChapter paths are the source of chapter identity/boundary events.

Do not use page index as chapter completion. The reliable completion event is the existing transition to the next chapter; inspect the viewer transition callbacks before implementation. Manual chapter selection from ChapterListDialog must reset the timer's current-chapter grace state consistently and must not consume the one-extra allowance unless it is an actual post-expiry next-chapter transition.

The timer coordinator must expose a pure state reducer and a monotonic clock interface so all elapsed-time logic is unit-testable. Persist only a serializable primitive session record. Restore malformed or stale records to Idle. Do not put a serializable state object into Android navigation arguments or Bundles; this repository has prior BadParcelableException history.


