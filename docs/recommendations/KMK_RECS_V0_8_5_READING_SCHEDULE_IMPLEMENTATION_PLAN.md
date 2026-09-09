# KMK-Recs v0.8.5 - Optional Reading Schedule Plan

Status: **implemented and shipped in KMK-Recs v0.8.5**, after v0.8.4 timer tests passed as required. See `KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md` for the implementation report, including the post-review correction adding multi-window add/delete support with `MaterialTimePicker` (the editor initially shipped with a single manual-text-entry window).

## Scope

Add optional local day/time reading windows without turning Komikku into an operating-system parental-control service. This remains inside the 0.8 line.

## Architecture

Create a pure schedule resolver separate from the active countdown state machine. The resolver receives a local date/time, weekday, enabled rules, and mode, then returns Allowed, Restricted, or Disabled. The timer coordinator consumes that result at reader open/resume and when the schedule changes.

Do not use wall-clock time for countdown elapsed accounting. Do not create a permanent service or request broad permissions. Do not claim to restrict other apps.

## Settings

Use existing Komikku settings patterns. Provide:

- enabled toggle;
- allowed-reading or restricted-reading mode;
- weekday selection;
- one or more start/end local time windows;
- add/edit/delete rule;
- deterministic behavior for overlapping windows;
- explanation that restrictions apply inside Komikku only.

Validate all values. Invalid schedules fall back to disabled. Avoid a custom clock picker if the repository already has a standard time picker; reuse existing components.

## Reader behavior

Evaluate schedule at ReaderActivity open, onResume, after background return, and after schedule edits. If restricted, show a clear localized reader warning/action rather than crashing or silently failing. If a window ends during a chapter, use the timer's non-destructive chapter-grace policy. Do not close the reader or discard progress.

Handle midnight-crossing windows, day boundaries, daylight-saving/time-zone changes, disabled rules, overlapping rules, process recreation, and schedule edits while reading.

Notifications are out of scope unless existing permissions and lifecycle support them without a background service. Document this decision.

## Tests

Use a fixed clock to test normal windows, midnight crossing, weekday boundaries, overlap precedence, disabled schedules, invalid values, time-zone/DST changes, schedule edits, reader resume, and expiry during a chapter. Add UI/state tests for add/edit/delete and back handling.

## Acceptance criteria

Schedule logic is deterministic and local, applies only inside the reader, does not affect recommendation code, does not require broad permissions, does not crash on malformed data, and does not interrupt a chapter destructively.



## Code-level implementation addendum from source review

The schedule must remain independent from ReaderActivity's elapsed timer. Its resolver can be pure and called from ReaderActivity/ViewModel on open/resume and from the timer coordinator when the active chapter changes. It must not schedule work merely to enforce a foreground-only rule.

Use a repository-standard local time representation and existing preference storage. Normalize a window where end is earlier than start as a midnight-crossing window, not as an invalid empty window. Define overlap precedence explicitly: in allowed mode, any matching allowed window permits reading unless a higher-priority restricted rule exists; in restricted mode, any matching restricted window restricts reading. If the product does not need mixed rules, use one mode per schedule and reject conflicting entries at save time.

A schedule ending during a chapter must enter the same non-destructive grace path as timer expiry. It must never call finish(), navigate away, stop a download, or alter reading history. Schedule edits while reading are applied at the next safe state boundary and must not interrupt the current composable or create a second timer ticker.


