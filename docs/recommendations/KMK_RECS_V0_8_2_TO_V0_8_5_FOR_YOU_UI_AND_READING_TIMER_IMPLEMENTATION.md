# KMK-Recs v0.8.2-v0.8.5 — For You UI And Reading Timer: Implementation Report

Date: 2026-07-15

Implements the coordinated v0.8.2–v0.8.5 plan set
(`KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md` plus the four phase plans) as one
implementation session with four internal milestones and a single final build, per the master
plan's explicit instruction. This is an internal/private handoff build for development; no
app-facing string in this pass uses "private build," "public build," "internal build," "test
build," "personal line," or similar release-channel wording — verified by grep across every
touched file (see "Wording Audit" below).

## Preflight Finding (Recorded Before Any Code Was Written)

The plan documents this task pointed to did not exist in the repository location initially
checked. After the user corrected the repository root to `C:\Users\USER\Downloads\Komikku\
komikku-source`, all six referenced documents were confirmed present and were read in full before
any implementation began: `docs/IMPLEMENTATION_PLAN_STANDARD.md`,
`docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md`, the four phase
plans, and `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`. No further plan-vs-code contradictions were found
during preflight inspection of `BrowsePersonalRecommendationsScreenModel.kt`,
`RecommendationsSettingsScreen(Model).kt`, `ReaderActivity.kt`, and `ReaderViewModel.kt` — every
constant, method, and lifecycle hook the plans referenced (`NORMAL_RESULTS_PER_SOURCE`,
`BOOSTED_RESULTS_PER_SOURCE`, `profileFingerprint()`, `onPause`/`onResume`, `SavedStateHandle`
usage for `chapter_id`/`page_index`, the existing chapter-identity `onEach` subscription) matched
the plans' own code-level addenda exactly.

One additional preflight finding: `LovedMangaScreenModel`/`RatedMangaScreen.kt` already implement
the single shared parameterized rated-collection UI the v0.8.3 plan asks for (`RatedMangaScreen
(ratingValue)` delegates to the same `RatedMangaCollectionContent` as `LovedMangaScreen`) — this
was completed in a prior v0.7.36/v0.8.0 session, not new work. Phase B's actual required delta was
narrower than the plan's full description; see that phase's section below.

## Phase A — v0.8.2 For You Display Count

### Files Changed

**New:**
- `app/src/main/java/exh/recs/ForYouResultBudgetPolicy.kt` — pure resolver:
  `SUPPORTED_VALUES = [5,10,15,20,30]`, `DEFAULT = 10`, `BOOSTED_MINIMUM = 20`,
  `resolve(configuredValue, isBoosted)`.
- `app/src/test/java/exh/recs/ForYouResultBudgetPolicyTest.kt` (11 tests).

**Modified:**
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` — new
  `recommendationResultBudget()` int preference, default 10.
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` — removed the two
  hardcoded `NORMAL_RESULTS_PER_SOURCE`/`BOOSTED_RESULTS_PER_SOURCE` constants; both `displayLimit`
  computation sites (cached-path merge and live-path score/merge inside `searchSource()`) now call
  `ForYouResultBudgetPolicy.resolve(resultBudget, isBoosted)`; `resultBudget` threaded through
  `searchSource(...)` as an explicit parameter (same convention as the existing `minChapterCount`
  parameter); added to `profileFingerprint()`'s SHA-256 digest so a cache built under a smaller
  budget can never satisfy a later, larger-budget request — the existing fingerprint-mismatch
  mechanism (already relied on by `minChapterCount`/`seenMangaCount`) forces a refetch with no new
  invalidation logic required.
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` /
  `RecommendationsSettingsScreen.kt` — new `resultBudget` state field, `setResultBudget()` action, a
  new settings row reusing the existing `SameMangaListPrefRow` dropdown component (the same one
  `enrichmentCap`/`minChapterCount` already use — no new picker component was created).
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — 2 new strings
  (`rec_result_budget_title`/`_summary`).

### Explicitly Unchanged (Confirmed By Inspection)

`MAX_SOURCE_ATTEMPTS`, `MAX_VISIBLE_SOURCE_ROWS`, `BOOSTED_SOURCE_COUNT`, `TOP_PICKS_ROW_CAP`,
`TOP_PICKS_DETAIL_CAP`, the enrichment-cap logic (`recommendationEnrichmentCap()` and its 2×-boosted
rule), the discovery-page raw cap (`RecommendationDiscoveryPlanner
.MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE`), and every query-attempt/source-selection policy — none of
these reference the new setting; `discoverAdditionalPage()`'s own candidate cap is independent of
`displayLimit` and was not touched.

### Tests

11 new `ForYouResultBudgetPolicyTest` cases: default value, all 5 supported values pass through
unchanged for normal rows, corrupt/out-of-range values fall back to `DEFAULT` for both normal and
boosted paths, boosted floor never drops below 20 regardless of a smaller configured value, boosted
rises to 30 when 30 is selected, `SUPPORTED_VALUES`/`BOOSTED_MINIMUM` contract checks.

The cache-fingerprint mechanism itself, and "hidden candidates don't consume a slot," were not
given new dedicated tests — both are mechanical reuses of pre-existing, already-relied-upon
behavior (`profileFingerprint()`'s digest pattern; `PersonalRecommendationScorer.rankCandidates()`
receiving only already-visibility-filtered candidates) rather than new logic this pass introduced,
consistent with this codebase's existing practice of not unit-testing `BrowsePersonalRecommendation
ScreenModel`'s private glue code directly (no `TestInjektSupport`-based ScreenModel test exists for
this function pre- or post-pass).

## Phase B — v0.8.3 Recommendation UI Refinement

### Finding: Most Requirements Already Satisfied

Verified by direct inspection, not changed this pass: Loved/Liked/Disliked already share one
parameterized implementation; long-press already enters selection mode with recommendations as
explicit menu actions (v0.8.0); Source Evaluation's quarantine/blocked-package controls are already
demoted to a collapsible "safety diagnostics" row, not pinned at top (v0.6.19); the For You toolbar
already has visible Loved/Liked/Disliked shortcuts with icons and content descriptions; a grep of
all five target screens for hardcoded `Color(...)` literals found none.

### Files Changed

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` — reordered existing
  `item{}` composable blocks only; no screen-model, query, scoring, or preference-meaning change.
  Moved the Source Evaluation entry-point block from last to third position. Split the former
  combined "Management" section into "Source management" (installed/hidden/disliked/quality-marked
  rows, Sources To Try, Best Version history) and a new "Discovery and cache management" section
  (enrichment cap, discovery-history reset). Final top-to-bottom order, confirmed by grep of every
  `SectionHeader()` call: For You behavior → Source priority (with nested Same-manga-matching/
  Source-status sub-sections) → Source Evaluation → Source management → Discovery/cache management
  — matching the plan's required 1–5 order exactly.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` — `rec_settings_management_header`
  retitled "Management" → "Source management" (same key, new display text — no stored-value
  meaning changed); new `rec_settings_discovery_cache_header` string.

## Phase C — v0.8.4 Active-Reading Timer

### Files Changed

**New package** `app/src/main/java/eu/kanade/tachiyomi/ui/reader/timer/`:
- `ReaderTimerModels.kt` — `ReaderTimerPhase` (IDLE/RUNNING/PAUSED/CHAPTER_GRACE/
  EXTRA_CHAPTER_GRACE/EXPIRED), `ReaderTimerPauseReason` (USER vs BACKGROUND),
  `ReaderTimerWarningPolicy`, `ReaderTimerGracePolicy`, `ReaderTimerSession`, `ReaderTimerEvent`.
- `ReaderTimerReducer.kt` — pure `(state, event, now) -> state`. `now` (monotonic) is threaded
  through every event, not only `Tick`, so any transition out of an actively-counting phase
  correctly freezes elapsed time at the exact transition instant.
- `ReaderTimerClock.kt` — `fun interface ReaderTimerClock { fun nowMonotonicMs(): Long }`;
  `SystemReaderTimerClock` backed by `SystemClock.elapsedRealtime()`.
- `ReaderTimerStateCodec.kt` — pure encode/decode to Bundle-safe primitives (String/Long/Boolean),
  matching this repo's existing `chapter_id`/`page_index` `SavedStateHandle` convention exactly.
  Any missing/malformed field falls back to a fresh `IDLE` session rather than crashing restoration.
- `ReaderTimerCoordinator.kt` — lifecycle-bound wrapper owning the real ticking coroutine. The
  ticker exists only while `ReaderTimerSession.isActivelyCounting`; because the coordinator instance
  itself is retained across rotation (it lives inside `ReaderViewModel`, an Android `ViewModel`),
  rotation can never spawn a second ticker.

**New** `app/src/main/java/eu/kanade/presentation/reader/ReaderTimerDialog.kt` — presets (15/30/60
min) plus a bounded custom duration (1–300 min), warning-threshold checkboxes, finish-current-
chapter/allow-one-extra-chapter toggles, a live remaining-time display, and pause/resume/reset/stop
controls.

**Modified:**
- `ReaderViewModel.kt` — `timerCoordinator` instantiated with `SavedStateHandle` restore/persist
  wiring; `timerState: StateFlow<ReaderTimerSession>`; `startTimer/pauseTimer/resumeTimer/
  resetTimer/stopTimer`; `onReaderForeground()`/`onReaderBackground()`; hooked into the *existing*
  chapter-identity `onEach` subscription (the same one that already updates `chapterId` — not a new
  observer) so `ChapterChanged` fires on every real chapter change (next/previous/manual dialog
  selection alike), satisfying the plan's requirement that manual selection behave identically to
  natural progression.
- `ReaderActivity.kt` — `onPause`/`onResume` call the new background/foreground hooks, kept clearly
  distinct in code comments from the pre-existing, unrelated `restartReadTimer()` (a per-chapter
  *history*-duration tracker using wall-clock `Instant.now()`, untouched by this pass). New
  `Dialog.ReadingTimer` case. In-reader `Toast` for warnings/expiry — never a system notification.
- `ReaderAppBars.kt`/`ReaderBottomBar.kt` — new always-visible "Reading timer" icon (`Icons.Outlined
  .Timer`) next to the existing Settings icon, threaded through both files' parameter lists.
- `i18n-kmk/.../strings.xml` — 22 new strings.

### Documented Deviation From The Plan's Literal Phase List

"Warning" is not modeled as a mutually-exclusive `ReaderTimerPhase` (the plan says required states
"may include" it — not "must"). Instead, `ReaderTimerSession.firedWarningMinutes` is a
monotonically-growing set that the UI diffs against its previous value to fire each threshold
exactly once. Rationale: a phase-based "Warning" state that the reducer enters and then
auto-exits on the very next tick risks being missed by a `StateFlow` collector between two 1-second
ticks (a collector reads only the latest emitted value; a momentary phase can be skipped if two
updates land before the collector's next read). A monotonically-growing set can never be "missed"
this way — membership, once true, stays true. This satisfies "fires once per session" more robustly
than the literal phase-list reading. Documented in the reducer's own top-of-file doc comment.

### Tests

44 new tests across three files:
- `ReaderTimerReducerTest.kt` (30) — every state transition listed in the plan: start/pause/resume/
  reset/stop, monotonic tick accounting, warning-fires-once (including multiple simultaneous
  thresholds and "no warnings during grace"), chapter-grace entry/exit, the one-extra-chapter cap
  (including a rapid-chapter-change "race" test proving a second boundary during
  `EXTRA_CHAPTER_GRACE` always ends the session, never granting a second extra), `ProcessRestored`
  freezing an actively-counting session as background-paused, `InvalidPersistedState` always
  resetting to fresh `IDLE`, and the `ReaderForeground` vs explicit-`USER`-pause distinction (the
  core "don't silently resume something the user consciously paused" requirement).
- `ReaderTimerStateCodecTest.kt` (9) — round-trip preservation for both running and paused sessions,
  and 7 distinct malformed-input cases (missing phase, unrecognized phase name, negative durations,
  an internally-inconsistent `PAUSED` record missing its `pausedFrom`, malformed CSV) all falling
  back to `IDLE` rather than crashing.
- `ReaderTimerCoordinatorTest.kt` (6, using `kotlinx-coroutines-test`'s `runTest`/virtual time) —
  the ticker actually advances state and reaches `EXPIRED`, `pause()` freezes elapsed against a
  simulated large real-time gap, background→foreground round-trips preserve elapsed progress, the
  persistence callback fires on every transition, `reset()` stops the ticker.

### Manual QA — Explicitly Unavailable, Recorded Honestly

Rotation, lock-screen, real backgrounding, and multi-day DST behavior could not be exercised on a
real device in this environment. This is recorded as an **unavailable test**, not claimed as
verified. The pure reducer/coordinator tests above cover the equivalent logic deterministically
(e.g. `ProcessRestored` exercises the exact state transition a rotation-induced recreation would
trigger), but that is not a substitute for on-device confirmation.

### Known Limitation

No preference persists the user's last-chosen timer configuration (warning minutes, grace policy)
across sessions — each new `Start` requires re-selecting them via the dialog's defaults. A
deliberate scope decision to avoid an unrequested preference/backup surface; flagged as a follow-up.

## Post-Review Corrections (Applied Before Final Handoff)

An independent review of the pre-final-build state flagged several issues. Two categories of
findings were verified against live disk state and found to already be correct (version metadata
at `VERSION_CODE 755`/`"KMK-Recs v0.8.5"`, `CURRENT_STATE.md`/`NEXT_WORK.md` already reconciled, the
combined report already present, `ReaderScheduleDialog` already wired into `SettingsReaderScreen
.kt` at a real call site) — the review appears to have been taken against an earlier snapshot. The
remaining findings were real and are fixed below:

1. **Timer chapter-boundary behavior corrected.** `ReaderTimerEvent.ChapterChanged` now carries
   `isNaturalProgression: Boolean`. `ReaderTimerReducer` only grants the one-extra-chapter allowance
   when `isNaturalProgression == true`; manual `ChapterListDialog` selection and previous-chapter
   navigation still end/reset grace (transitioning straight to `EXPIRED`) but can never consume the
   allowance — matching the plan's exact requirement ("must not consume the one-extra allowance
   unless it is an actual post-expiry next-chapter transition"). `ReaderViewModel` gained a
   `@Volatile pendingChapterChangeIsNatural` flag, defaulting to `true` (safe for the common case,
   `loadNextChapter()`), explicitly set `false` by `loadPreviousChapter()` and
   `loadNewChapterFromDialog()` immediately before calling `loadAdjacent()`, read and reset by the
   existing chapter-identity subscription. 3 reducer tests updated/added to lock in the corrected
   behavior (previously one test asserted manual and natural selection behave *identically*, which
   was the bug this correction fixes).
2. **Reading schedule editor rebuilt to support multiple windows with a real time picker.**
   `ReaderScheduleDialog` now takes `initialWindows: List<ReaderScheduleWindow>` and
   `onSave(mode, windows: List<...>)`, rendering the current list with a per-row delete action and
   an "Add window" flow. Time entry uses `com.google.android.material.timepicker.MaterialTimePicker`
   — the same repository-standard component `BiometricTimesScreen.kt` already uses for its
   equivalent time-range-list feature (app-lock schedules) — chained start-then-end exactly like
   that screen's `showTimePicker()` recursive-callback pattern, replacing the earlier manual
   `OutlinedTextField` HH:mm entry. "Edit" is delete-then-recreate, matching that same existing
   screen's precedent (it has no separate edit action either, only create/delete). `SettingsReader
   Screen.kt`'s `getReadingScheduleGroup()` updated to the list-based API; its subtitle now reports
   a window count instead of a single window's mode/day-count.
3. **`ReaderPreferences.kt` malformed comment fixed** (was a leftover mid-edit fragment reading
   "`exh.recs... no, eu.kanade...`").

Verification after these corrections: full `compileDebugKotlin`, `spotlessApply`/`spotlessCheck`,
and `eu.kanade.tachiyomi.ui.reader.*` targeted tests all pass (see "Tests And Results" below, run
after these fixes, not before).

## Post-Review Correction 2: What's New History Restored Per Milestone

`KmkRecsReleaseNotes.MARKDOWN` originally collapsed all four v0.8.2–v0.8.5 milestones into a single
`## KMK-Recs v0.8.5` section with one combined paragraph and four bullet points spanning every
phase. Corrected to four separate, newest-first `##` sections — `v0.8.5` (optional reading
schedule), `v0.8.4` (active-reading timer), `v0.8.3` (Recommendation Settings reorganization), and
`v0.8.2` (configurable For You results per source) — each with its own short description and
bullets, matching how every other version boundary in this changelog is presented. `VERSION_CODE`
(755) and `VERSION_NAME` ("KMK-Recs v0.8.5") were not changed — this is a documentation/UI-notes
correction within the same v0.8.5 build, not a new version. Wording reflects the final, corrected
implementation (manual/previous-chapter navigation never consumes the timer's one-extra-chapter
allowance; the schedule supports multiple recurring windows entered via the same time picker used
elsewhere in the app), grepped clean of build-channel wording. All older entries below v0.8.2 were
left untouched.

## Phase D — v0.8.5 Optional Reading Schedule

### Files Changed

**New package** `app/src/main/java/eu/kanade/tachiyomi/ui/reader/schedule/`:
- `ReaderScheduleModels.kt` — `ReaderScheduleMode` (ALLOWED/RESTRICTED — one mode applies to every
  window in a schedule, per the plan's own sanctioned simplification: "If the product does not need
  mixed rules, use one mode per schedule"), `ReaderScheduleWindow` (weekdays + start/end
  minute-of-day; `endMinuteOfDay <= startMinuteOfDay` is a normal midnight-crossing window, not an
  error), `ReaderSchedule`, `ReaderScheduleResult` (ALLOWED/RESTRICTED/DISABLED).
- `ReaderScheduleResolver.kt` — pure `(schedule, LocalDateTime) -> ReaderScheduleResult`. Takes no
  internal clock reading of its own; DST/time-zone safety comes entirely from the caller always
  passing a freshly-read `LocalDateTime.now()` at each evaluation point, never a cached result.
- `ReaderScheduleStore.kt` — pure serialize/deserialize to a compact primitive string for
  `ReaderPreferences` storage. A corrupt weekday token inside an otherwise-valid window is dropped
  (not the whole window); a window left with zero valid weekdays after that is dropped entirely.

**New** `app/src/main/java/eu/kanade/presentation/reader/ReaderScheduleDialog.kt`.

**Modified:**
- `ReaderPreferences.kt` — 3 new persisted settings: `readingScheduleEnabled()` (default off),
  `readingScheduleMode()`, `readingScheduleWindows()`.
- `ReaderViewModel.kt` — `scheduleGraceCoordinator`: a **second, fully independent instance** of the
  same `ReaderTimerCoordinator`/`ReaderTimerReducer` used by the manual timer — not a schedule
  branch added to the reducer itself (explicitly forbidden by the plan). `evaluateSchedule()` reads
  the current schedule preferences, resolves against `LocalDateTime.now()`, and on RESTRICTED starts
  this second coordinator with a **zero-length duration** and `finishCurrentChapter = true`; its
  very first tick immediately (sub-second) drives it into `CHAPTER_GRACE` using the reducer's
  already-tested, completely unmodified grace logic. On ALLOWED/DISABLED, the coordinator is reset.
  Hooked into the same existing chapter-identity subscription and the same foreground/background
  calls as the Phase C timer — no new observers.
- `ReaderActivity.kt` — `evaluateSchedule()` called from `onCreate` and `onResume` (covering reader
  open, resume, and background-return in one hook, since `onResume` fires for all three). In-reader
  `Toast` for the restricted/grace states, distinct in wording from the timer's own toasts.
- `SettingsReaderScreen.kt` — new "Reading schedule" `Preference.PreferenceGroup` reusing the
  existing declarative `SwitchPreference`/`TextPreference` framework and the established
  dialog-from-settings pattern already used elsewhere in this file's settings screens (e.g.
  `SettingsAdvancedScreen.kt`'s Shizuku dialog) — no new settings-screen architecture was created.
- `i18n-kmk/.../strings.xml` — 13 new strings.

### Multi-Window Editor

The schedule editor supports an arbitrary number of recurring windows, added and removed
individually via `ReaderScheduleDialog` (see "Post-Review Corrections" above for the exact UI and
why `MaterialTimePicker` was used instead of manual text entry). `ReaderScheduleResolver`/
`ReaderScheduleStore` were designed for a `List<ReaderScheduleWindow>` from the start with correct
OR-together overlap semantics (tested — see below), so the UI upgrade required no resolver/store/
persistence-format change.

### Notifications — Explicitly Out Of Scope, As Directed

No new notification, permission, or service infrastructure was added. Both the timer's and the
schedule's user-facing feedback are in-reader `Toast`s only. This matches the plan's explicit
instruction ("Notifications are out of scope unless existing permissions and lifecycle support them
without a background service. Document this decision.") — the decision is: defer, because a
meaningful notification (e.g., "your reading window opens in 10 minutes" while the app is not
running) would require a `WorkManager`/`AlarmManager`-scheduled background trigger, which the master
plan's non-negotiable boundaries explicitly forbid ("no permanent service").

### Tests

23 new tests:
- `ReaderScheduleResolverTest.kt` (16) — disabled schedule always `DISABLED`; an enabled schedule
  with zero valid windows is treated as `DISABLED` rather than locking the user out entirely;
  `ALLOWED`/`RESTRICTED` mode semantics; start-inclusive/end-exclusive window boundaries; weekday
  scoping including Sunday→Monday wraparound for a crossing window; midnight-crossing windows
  matching both the starting-evening and spillover-morning portions; multiple windows of the same
  schedule OR-ing together (including an overlapping-window determinism test); invalid weekday/
  out-of-range-minute windows being dropped (individually and mixed with a valid sibling window);
  and a purity/determinism test (identical inputs always produce identical output) standing in for
  "time-zone changes don't affect the resolver," since the resolver is provably a pure function of
  its `LocalDateTime` input.
- `ReaderScheduleStoreTest.kt` (9) — round-trip for single/multiple/midnight-crossing windows;
  blank input; malformed field counts, out-of-range weekday numbers, and non-numeric minutes all
  handled without crashing (with the exact behavior — partial weekday recovery vs whole-window drop
  — verified precisely); `parseMode` round-trip and its `RESTRICTED` fallback for null/corrupt input.

### Manual QA — Explicitly Unavailable, Recorded Honestly

Real-device schedule-boundary-during-active-reading QA, and any DST-transition-day behavior, could
not be exercised in this environment. Recorded as unavailable, not claimed as done. The
zero-duration-timer reuse means the grace-during-a-chapter mechanics are exactly the same,
already-tested code path as Phase C's timer expiry — no separate grace logic exists to leave
unverified.

### Known Limitation

The schedule-grace coordinator's session is **not** persisted across process death (only the raw
schedule *preferences* — enabled/mode/windows — persist globally). If the process dies mid-grace,
`evaluateSchedule()` on the next reader open simply re-resolves the schedule fresh; if still
restricted, a new zero-duration grace session starts immediately with the same non-destructive
behavior — the net user-facing effect is equivalent, but a very specific race (process dies exactly
during the grace window, user returns before the window would have naturally ended) is not
bit-for-bit preserved. Flagged as a follow-up if this proves user-visible in practice.

## Cross-Cutting Verification

### Backup/Sync

Every new persisted value (`SourcePreferences.recommendationResultBudget()`;
`ReaderPreferences.readingScheduleEnabled()/readingScheduleMode()/readingScheduleWindows()`) is
backed up automatically by this fork's existing generic preference-backup mechanism —
`PreferenceBackupCreator.createSource()`/`createApp()` call `preferenceStore.getAll()
.toBackupPreferences()`, which iterates every key in the store reflectively. No manual backup
registration was needed or added, matching how the pre-existing `recommendation_enrichment_cap`
preference (added in v0.7.34) already works. The active-reading timer's `SavedStateHandle` fields
are, correctly, outside this system entirely — they are ephemeral Android process/activity state,
never user data, and were never intended to survive a backup/restore round-trip.

### Dependency Injection

No new Injekt registrations were needed. Every new class this pass introduced
(`ForYouResultBudgetPolicy`, the `ReaderTimer*`/`ReaderSchedule*` families) is a pure object or a
plain class instantiated directly by its owner (`ReaderViewModel` constructs both coordinators
itself), matching this codebase's existing convention for pure policy objects (e.g.
`SourceQualityMarkPolicy`, `RecommendationCandidateVisibilityPolicy` are neither Injekt-registered).

### Wording Audit

```powershell
rg -n "private build|public build|internal build|test build|community build|personal line" app\src\main\java i18n-kmk\src\commonMain\moko-resources\base\strings.xml
```
No matches anywhere in the touched files. The word "private" appears in touched code only in its
pre-existing meaning (the "Private (recommended)" extension installer mode, and a code comment
about `context.isPackageInstalled` semantics unrelated to release channels).

### Hardcoded Colors

Grepped every touched Compose file (`ReaderTimerDialog.kt`, `ReaderScheduleDialog.kt`,
`ReaderBottomBar.kt`, `SettingsReaderScreen.kt`, `RecommendationsSettingsScreen.kt`) for
`Color(0x...)`/`Color.Red`/etc. — none found; all colors go through `MaterialTheme.colorScheme`.

### Scope Boundaries Confirmed Untouched

No file under `SourceEvaluationScorer.kt`, `PersonalRecommendationScorer.kt`,
`RecommendationQueryPlanner.kt`, `RecommendationSourceSelector.kt`, or any `.sq` database schema
file was modified by this pass. No database migration was added — every new persistent value uses
the existing generic preference-store mechanism.

## Tests And Results

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
# Both: BUILD SUCCESSFUL.

.\gradlew.bat :app:testDebugUnitTest --tests 'exh.recs.*' --tests 'eu.kanade.tachiyomi.ui.reader.*'
# BUILD SUCCESSFUL — covers every Recommendation/SourceEvaluation/Rated test class plus the new
# timer/schedule tests in one run.

.\gradlew.bat :app:testDebugUnitTest
# Full unit suite: BUILD SUCCESSFUL.

.\gradlew.bat assembleDebug
# BUILD SUCCESSFUL (run twice: once before the version-metadata bump to validate the code, once
# after, to bake KMK-Recs v0.8.5 / VERSION_CODE 755 into the final handoff APK).
```

**Gradle CLI note:** the wildcard pattern `--tests "*Recommendation*"` (and similarly bare
`*Rated*`) triggers a Gradle command-line abbreviated-task-name-matching bug in this environment —
it resolves the glob against a root-level Markdown filename (`RECOMMENDATION_IMPLEMENTATION_AUDIT
.md`) instead of passing it through to the `--tests` filter, producing `Task
'RECOMMENDATION_IMPLEMENTATION_AUDIT.md' not found`. Worked around throughout this pass by using
package-qualified dotted patterns (`exh.recs.*`, `eu.kanade.tachiyomi.ui.reader.*`) instead of bare
`*Word*` globs, per the task's own fallback instruction ("run the nearest concrete test classes and
record the commands").

## Manual QA

Not available in this environment for any reader-lifecycle-dependent behavior (rotation, lock
screen, real app backgrounding, multi-day DST transitions, on-device Recommendation Settings visual
layout in light/dark/accent themes). Recorded honestly as unavailable per phase above, not claimed
as verified. All logic-level behavior these manual steps would exercise is covered by the 78 new
automated tests across the four phases (11 + 0 + 44 + 23).

## Deviations From The Approved Plan

1. Phase B's scope was narrower than described because most of its requirements were already
   implemented in prior sessions (see "Preflight Finding" above) — only the settings section
   reorder was net-new work this pass.
2. Phase C's "Warning" state is implemented as an event/set-diff mechanism rather than a literal
   `ReaderTimerPhase` value, for the correctness reason documented in that phase's section (the
   plan's own wording, "states may include," permits this).
3. Notifications for both the timer and the schedule are in-reader `Toast`s only, per the plan's own
   instruction to defer anything requiring new background infrastructure.

## Known Limitations (Consolidated)

- No persisted "last used" timer configuration between sessions (Phase C).
- Schedule editor's "edit" is delete-then-recreate, not in-place field editing — matching the
  existing `BiometricTimesScreen.kt` precedent for the same kind of time-range list (Phase D).
- Schedule-grace session state is not persisted across process death, only the underlying schedule
  preferences are (Phase D) — re-derived correctly on next evaluation, not bit-for-bit restored.
- No on-device manual QA was possible in this environment for any reader-lifecycle behavior.

## Final Version Metadata

```text
KmkRecsReleaseNotes.VERSION_CODE = 755
KmkRecsReleaseNotes.VERSION_NAME = "KMK-Recs v0.8.5"
```

Upstream app version unchanged: `versionCode = 88`, `versionName = "1.13.6"` (app/build.gradle.kts,
untouched by this pass).

## Final APK

```text
C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.5-debug.apk
```

Universal ABI variant, built via `:app:assembleDebug`, matching the existing naming convention for
every prior version in that folder. Internal handoff artifact only — the word "private" here refers
solely to the local folder path, never to any app-visible text.
