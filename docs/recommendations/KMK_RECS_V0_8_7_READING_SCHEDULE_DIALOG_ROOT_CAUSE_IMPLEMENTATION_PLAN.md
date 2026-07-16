# KMK-Recs v0.8.7 Reading Schedule Dialog Root-Cause Implementation Plan

**Status:** Implemented and verified (2026-07-15). All five findings (A-E) fixed; 11 new unit tests
added and passing (`ReaderScheduleStoreTest`, `ReaderScheduleResolverTest`); `spotlessCheck` and full
`:app:testDebugUnitTest`/`assembleDebug` passed. Device/manual QA not performed (no physical device
available). See `docs/recommendations/KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION.md`
for the full report (this plan and its sibling shipped together as required).

**Parent plan:** KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION_PLAN.md

The Reading Schedule issue must be assessed and repaired in the same v0.8.7 implementation before the final build.

## 1. Confirmed code findings

The reported “select days and nothing happens” behavior is not caused by the resolver or serializer first. The failure is in the Compose-to-MaterialTimePicker bridge.

### Finding A: silent Activity-cast abort

In:

    app/src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt

The AddWindowFlow function obtains LocalContext.current and performs a direct nullable cast to MainActivity. If the Compose context is a ContextThemeWrapper or another ContextWrapper rather than a direct MainActivity instance, the flow silently calls onDone(null). The weekday dialog closes, no time picker is shown, and no window is added. This exactly matches the reported symptom.

Claude must verify this on the real host activity and with a test/fake context. The fix must not rely on an unsafe direct cast.

Preferred implementation:

- unwrap ContextWrapper instances until an Activity is found;
- verify that the Activity is a MainActivity or otherwise has the required FragmentManager;
- if no usable Activity exists, fail visibly through the existing UI error/event pattern rather than silently discarding the draft;
- do not leak or retain the Activity beyond the picker flow;
- prevent duplicate picker launches when recomposition occurs.

If the repository's official pattern provides a safer Activity/FragmentManager access helper, reuse it rather than creating a new utility.

### Finding B: device time format is ignored

The current picker is forced to CLOCK_24H. This ignores the device's 12-hour/24-hour preference. The implementation must follow the device locale/time-format preference. Stored values remain integer minutes since midnight; only presentation changes.

### Finding C: whole-day windows are impossible

The current flow only creates a window when startMinute is not equal to endMinute. Therefore 00:00 to 00:00, or any equal start/end pair, is discarded. The requested whole-day option needs an explicit, unambiguous representation. Do not overload equal times unless the model/resolver contract is deliberately changed and thoroughly tested.

### Finding D: draft cancellation and dialog dismissal need explicit separation

The outer dialog calls persist() from onDismissRequest, while the explicit Cancel button does not. This means tapping outside/back can save a draft while Cancel discards it. This is inconsistent and can make the UI appear unpredictable.

Required behavior:

- Save commits the current mode and windows;
- Cancel discards all draft changes;
- outside dismissal/back follows one documented policy, preferably equivalent to Cancel unless the repository's settings convention requires confirmation;
- no window is persisted merely because the dialog was dismissed accidentally.

### Finding E: edit behavior is incomplete

The current dialog renders delete buttons and an Add flow, but does not provide a real Edit action despite comments describing delete-and-recreate behavior. The implementation must either provide an explicit Edit action that opens existing values, or remove the claim that Edit exists. The preferred behavior is real Edit, preserving all other windows.

## 2. Required source inspection

Claude must read the current versions of:

- app/src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt;
- app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsReaderScreen.kt;
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderSettingsScreenModel.kt;
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt;
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleModels.kt;
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleStore.kt;
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleResolver.kt;
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt;
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt;
- the repository's BiometricTimesScreen implementation and official Activity/FragmentManager helper;
- ReaderScheduleStoreTest.kt;
- ReaderScheduleResolverTest.kt.

Before editing, confirm whether the settings screen is hosted by MainActivity in every relevant navigation path. Do not assume that one path using MainActivity means every Compose context is a direct MainActivity instance.

## 3. Correct editor model

Replace the implicit multi-stage state with a testable draft model, following local Compose conventions:

- selected weekdays;
- start minute;
- end minute;
- whole-day flag;
- editing index or stable window identifier;
- current picker stage;
- validation/error state.

Required flow:

1. User taps Add Window or Edit.
2. User selects weekdays.
3. User confirms weekdays.
4. User selects start time.
5. User selects end time, unless Whole Day was selected.
6. Draft is validated.
7. Save/Add commits the draft to the local window list.
8. The outer Save commits the complete list to preferences.
9. Cancel at any stage discards only the active draft and returns to the prior dialog state.

Time picker behavior:

- use the device's 12-hour/24-hour preference;
- use MaterialTimePicker and the existing project pattern;
- ensure the FragmentManager is valid before showing;
- handle picker cancellation and dismissal without losing existing windows;
- prevent duplicate fragment tags or simultaneous picker instances;
- do not launch picker UI from a stale composition.

## 4. Whole-day representation

Determine whether the current model can safely represent an all-day window. If not, add a small explicit representation, such as an allDay flag on ReaderScheduleWindow, with backward-compatible serialization.

If changing the model:

- preserve old serialized windows;
- define how old equal-time windows are interpreted;
- update ReaderScheduleStore serialization/parsing;
- update ReaderScheduleResolver;
- add migration/round-trip tests;
- document the representation.

A whole-day window must mean every minute of every selected weekday. It must not be confused with an empty or invalid window.

## 5. Multiple windows and editing

The dialog must support:

- adding several windows;
- editing one window without changing others;
- deleting one window;
- duplicate-window prevention or deterministic duplicate handling;
- stable display order;
- selected weekday summary;
- localized time summary;
- preservation of overnight windows.

Overnight semantics remain:

- end earlier than start means the interval crosses midnight;
- spillover applies to the next calendar day according to the resolver contract;
- Sunday-to-Monday spillover is explicitly tested.

## 6. Persistence and enforcement

Verify the full path:

- SettingsReaderScreen reads preferences;
- ReaderScheduleStore parses windows;
- saving updates mode and windows;
- settings UI receives changed preference values;
- ReaderViewModel re-evaluates after settings changes;
- ReaderActivity re-evaluates on create/resume;
- restricted periods produce current in-reader warning/grace behavior;
- disabled or empty schedules never lock the user out.

The implementation must not require an app restart for a schedule change.

## 7. Required tests

Add or update tests for:

### Dialog/state tests

- weekday selection reaches the time-picker stage;
- non-Activity ContextWrapper is correctly unwrapped;
- missing Activity produces a visible safe failure rather than silent discard;
- start picker launches once;
- end picker launches once;
- picker cancellation leaves existing windows unchanged;
- Save commits;
- Cancel discards;
- outside/back behavior is deterministic;
- one window can be edited;
- other windows survive edits and deletes;
- duplicate windows are handled deterministically.

### Time-format tests

- 12-hour device display includes AM/PM;
- 24-hour device display uses 24-hour values;
- stored representation remains minutes since midnight;
- localized display never enters persistence.

### Model/store/resolver tests

- one window round-trip;
- multiple-window round-trip;
- whole-day round-trip;
- invalid/corrupt input safely ignored;
- equal-time behavior follows the explicit whole-day contract;
- same-day intervals;
- overnight intervals;
- Sunday-to-Monday spillover;
- overlapping windows;
- ALLOWED mode;
- RESTRICTED mode;
- disabled schedule;
- enabled schedule with no valid windows;
- DST/time-zone boundaries where supported.

### Reader integration tests

- settings change re-evaluates the active reader;
- foreground/background return re-evaluates local time;
- restriction does not alter the manual timer;
- chapter navigation cannot bypass intended schedule enforcement.

## 8. Manual verification

Test on a phone and tablet:

1. Open Reader Settings and enable the schedule.
2. Select one weekday and confirm that the time picker actually appears.
3. Add an AM/PM window on a 12-hour device.
4. Add a 24-hour window on a 24-hour device.
5. Add multiple windows on the same day.
6. Edit one window and verify the others remain.
7. Delete one window.
8. Add a whole-day window.
9. Add an overnight window.
10. Cancel during weekday selection, start picker, and end picker.
11. Save, leave settings, reopen, and verify all windows remain.
12. Force-stop/reopen and verify persistence.
13. Open a manga inside and outside the allowed/restricted period.
14. Change the schedule while the reader is open.
15. Background and resume the reader across a time boundary.
16. Verify no crash, silent disappearance, stale dialog, stuck picker, or unexpected persistence.

## 9. Documentation and versioning

Update the v0.8.7 implementation report with:

- confirmed root cause of the silent no-op;
- Activity/context resolution;
- time-format correction;
- whole-day representation;
- Cancel/dismissal policy;
- test results;
- device QA results;
- remaining limitations.

Update CURRENT_STATE.md, NEXT_WORK.md, README.md, and the documentation encyclopedia/index. Preserve all prior v0.8.x What's New entries and add the final v0.8.7 entry only after the fix is complete.

Do not build the final v0.8.7 APK until the rated UI refinement and this Reading Schedule repair are complete. Use:

C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.7-debug.apk

No internal build-channel wording may appear in user-facing app text.
