# KMK-Recs v0.8.7 Reading Schedule Assessment And Repair Addendum

**Status:** Proposed companion plan. No application code has been changed by Codex.

This addendum belongs with:
KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION_PLAN.md

The Reading Schedule issue must be assessed and repaired in the same v0.8.7 implementation before the final build.

## 1. Reported problem

The user selects weekdays and attempts to add a reading window, but the UI appears to do nothing. The implementation must identify the exact failing layer instead of assuming the resolver is wrong.

Trace the complete path:

1. weekday selection state;
2. start/end time picker callbacks;
3. draft-window validation;
4. add/edit/delete state mutation;
5. preference serialization;
6. settings state reloading;
7. reader schedule resolution;
8. foreground/background enforcement;
9. schedule changes while the reader is open.

## 2. Required source inspection

Read the live versions of:

- app/src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt
- app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsReaderScreen.kt
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderSettingsScreenModel.kt
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleModels.kt
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleStore.kt
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleResolver.kt
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt
- app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt
- app/src/test/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleStoreTest.kt
- app/src/test/java/eu/kanade/tachiyomi/ui/reader/schedule/ReaderScheduleResolverTest.kt

Document the actual root cause in the implementation report.

## 3. Required user behavior

The schedule editor must support:

- one or more weekdays per window;
- platform Material time picker for start and end;
- localized 12-hour display with AM/PM or 24-hour display according to device settings;
- stable minute-of-day storage, never localized strings;
- multiple independent windows per schedule;
- editing one window without losing other windows;
- deleting one window;
- explicit whole-day windows;
- immediate display of a newly saved window;
- persistence after leaving/reopening settings and app restart;
- validation when no weekday or invalid time data is supplied;
- non-destructive Cancel behavior.

## 4. Window semantics

Preserve and test these rules:

- Multiple windows combine with OR semantics.
- End earlier than start means an intentional overnight window.
- Overnight windows spill into the next calendar day according to the existing resolver contract.
- Whole-day windows use an unambiguous representation.
- Overlapping windows do not duplicate or corrupt state.
- Empty/invalid windows cannot accidentally lock the user out.
- ALLOWED and RESTRICTED modes keep their existing meanings.
- Device-local date/time and time zone are used.
- DST behavior is deterministic and documented.

Do not change the schedule model unless the current model cannot represent whole-day windows safely. If a model change is necessary, add migration/serialization compatibility tests.

## 5. Reader enforcement

Verify and repair, if needed:

- disabled schedule never restricts reading;
- enabled schedule with no valid windows remains safe;
- restricted periods use the existing in-reader feedback;
- schedule changes apply without process restart;
- foreground/background return re-evaluates local time;
- day and time rollover are handled;
- overnight restrictions are enforced;
- manual chapter navigation cannot unintentionally bypass the schedule;
- schedule grace remains separate from the manual reading timer;
- no unrelated reader preferences or persistent notifications are changed.

## 6. UI requirements

Use existing Komikku settings and Material patterns:

- saved windows appear as compact rows;
- each row shows weekday summary and localized start/end times;
- explicit Add, Edit, Delete, Whole Day, Save, and Cancel actions;
- Save commits the draft; Cancel discards it;
- empty state when no windows exist;
- no manual text parsing for times when the Material picker is available;
- accessibility labels for weekday toggles and time controls;
- phone-safe and tablet-safe dialog layout;
- no app-visible internal build-channel terminology.

## 7. Required tests

Add or update tests for:

- selecting weekdays and saving one window;
- saving multiple windows;
- editing without losing unrelated windows;
- deleting one window;
- whole-day windows;
- 12-hour/24-hour display conversion;
- stable minute-of-day serialization;
- no-day and invalid-input validation;
- store round-trip;
- overlapping windows;
- same-day windows;
- overnight spillover;
- Sunday-to-Monday spillover;
- ALLOWED and RESTRICTED modes;
- disabled and empty schedule safety;
- reopening settings and process recreation;
- reader resume and settings changes;
- day/time rollover;
- time-zone/DST boundaries where supported.

Run:

    ./gradlew spotlessCheck
    ./gradlew :app:testDebugUnitTest
    ./gradlew assembleDebug

Perform manual phone/tablet QA by adding windows with AM/PM, 24-hour time, multiple windows, whole-day windows, overnight windows, editing, deleting, cancelling, leaving/reopening settings, and opening the reader inside and outside restricted periods.

## 8. Documentation and handoff

Update the v0.8.7 implementation report, CURRENT_STATE.md, NEXT_WORK.md, README.md, encyclopedia/index, and What's New with the actual root cause and behavior change.

Do not produce the final APK until the rated-UI work and this Reading Schedule assessment/repair are both complete. Use the established v0.8.7 naming convention and copy the final APK to:

C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.7-debug.apk
