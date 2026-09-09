# KMK-Recs v0.8.10-fix1 - Full Komikku Conformance And Stability Plan

**Status:** Planning only. No implementation is authorized by this document.

**Target feature line:** `KMK-Recs v0.8.10-fix1`

**Upstream base:** Komikku `1.14.0` (`versionCode` 89, `versionName` 1.14.0).

**Purpose:** Bring every KMK-added surface into alignment with the existing Komikku UI,
architecture, lifecycle, localization, accessibility, and testing conventions while fixing the
two verified v0.8.10 follow-up gaps: historical What's New formatting and the unverified
application-wide navigation/startup crash.

This is a large corrective pass, not a new feature release. Claude must implement it in the gated
phases below, but must not hand off the final APK until every required phase, test, documentation
update, and device verification gate is complete.

## 1. Non-Negotiable Rules

1. Read `docs/IMPLEMENTATION_PLAN_STANDARD.md`, `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`,
   `docs/recommendations/README.md`, `CURRENT_STATE.md`, `NEXT_WORK.md`,
   `DOCUMENTATION_RULES.md`, `RECOMMENDATION_VERSIONING.md`, `AGENTS.md`, and `CONTRIBUTING.md`
   before editing.
2. The live source code is authoritative. If a document says a feature exists, verify its actual
   caller, state, persistence, and tests before changing it.
3. Do not create a second settings framework, Markdown renderer, navigation convention, resource
   ownership system, or error-reporting architecture.
4. Reuse official Komikku components and patterns wherever they already exist:
   `SearchableSettings`, `PreferenceScaffold`, `Preference.PreferenceGroup`,
   `Preference.PreferenceItem`, `MarkdownRender`, `InfoScreen`, existing top bars, existing
   search toolbars, existing dialogs, existing `MaterialTheme` tokens, and existing responsive
   layout utilities.
5. Do not make user-visible strings mention development-channel labels such as `private`, `public`,
   `internal`, or `test`. Those words may appear only in internal documentation or handoff paths.
6. Do not solve crashes by adding broad catch-all handlers that hide the root cause. Every caught
   error must have a typed policy, a safe UI state, a diagnostic path, and a test.
7. Do not delete historical release-note entries, implementation records, migrations, backup fields,
   or existing user data. Archive stale documentation only after checking the encyclopedia index.
8. Do not add a database migration, preference key, backup field, or new abstraction unless the
   current code inspection proves it is required.
9. Do not produce the final APK until all phases are complete. Intermediate builds are validation
   artifacts only.

## 2. Required Claude Execution Assignment

This plan is intentionally split by risk. Do not run the whole plan in one unbounded session.

### Phase 0 and Phase 1

```text
Recommended model: Opus
Recommended effort: high
Why: repository-wide audit, upstream convention matching, crash triage, and shared UI contract design.
Token/throughput tradeoff: slower, but prevents implementing the wrong architecture across every KMK screen.
When to escalate: any unresolved startup crash, lifecycle issue, or disagreement between source and docs.
When to reduce effort: only after the exact file/symbol inventory and target contracts are written.
Required verification: no production edits until the inventory and crash reproduction plan are complete.
```

### Phases 2 through 7

```text
Recommended model: Sonnet
Recommended effort: high
Why: bounded implementation against the contracts established in Phases 0-1.
Token/throughput tradeoff: better throughput while retaining enough reasoning for Compose, lifecycle, and tests.
When to escalate: database/backup changes, unresolved crash paths, or repeated test failures.
When to reduce effort: documentation-only or mechanical resource moves after tests already exist.
Required verification: focused tests, spotlessCheck, and a phase report before continuing.
```

### Final Phase 8

```text
Recommended model: Opus
Recommended effort: high or xhigh
Why: final release-readiness review, regression analysis, device matrix, and handoff integrity.
Token/throughput tradeoff: quality is more important than completing another feature in the session.
When to escalate: any unexplained crash, migration uncertainty, accessibility failure, or APK/version mismatch.
When to reduce effort: only for copying and hashing an already verified APK.
Required verification: full tests, real-device checks, documentation reconciliation, and APK identity check.
```

Claude must record the requested and actual model/effort in the implementation report. Use separate
phase sessions if context or token limits become a risk. Do not repeatedly switch models inside one
session.

## 3. Verified Baseline Before This Fix

The current tree is on Komikku 1.14.0 and contains committed KMK-Recs v0.8.10 phases A-I.
The current release metadata is `KmkRecsReleaseNotes.VERSION_CODE = 760` and
`VERSION_NAME = KMK-Recs v0.8.10`. The current handoff artifact is
`private/Komikku-v1.14.0-kmk.8.10-debug.apk`.

The previous pass added or changed, among other things:

- `eu.kanade.tachiyomi.ui.reader.ChapterCompletionPromptState`, `ReaderActivity`, and
  `ReaderViewModel`;
- `exh.recs.settings.RecommendationSettingsAnchorScroll` and all recommendation settings screens;
- `exh.recs.loved.RatedMangaScreen` and `RatedMangaSearchFilter`;
- `exh.recs.discovery.SourcesToTrySearchAndSort` and
  `RecommendationNonInstalledDiscoverySettingsScreen`;
- taste suggestion and diagnostics interactors/aggregators;
- `SourceEvaluationCompletionLifecyclePolicy` and Source Evaluation screen/model wiring;
- `BackupDecoderErrorPolicy` and `BackupDecoder`.

The previous pass explicitly left the following unresolved or unverified:

- the 83 historical release entries before v0.8.9 remain in the old flat format;
- the broader “Settings works but other screens crash” report has no proven root-cause fix;
- real-device navigation, rotation, phone/tablet layout, accessibility, and process recreation QA;
- a single combined v1.14 upgrade test covering all KMK persisted areas.

Do not accept the previous “keep historical What's New entries unchanged” decision. It conflicts
with the approved v0.8.10-fix1 requirement and must be replaced with a lossless conversion plus
validation.

## 4. Phase 0 - Repository Inventory And Crash Reproduction

**Prerequisite:** none.

### 4.1 Inventory exact KMK surface

Run a source inventory over:

- every file under `app/src/main/java/exh/recs/**`;
- every `// KMK -->` / `// KMK <--` region under `app/src/main/java/**`;
- every KMK resource under `app/src/main/res/**`, `app/src/commonMain/moko-resources/**`, and
  `i18n-kmk` resources;
- every KMK database migration under `app/src/main/sqldelight/tachiyomi/migrations/**`;
- backup/proto models under `app/src/main/java/eu/kanade/tachiyomi/data/backup/**`;
- tests under `app/src/test/java/exh/**`, `app/src/test/java/eu/kanade/**`, and relevant domain tests.

Create a table in the implementation report with: surface, entry point, screen/screen model,
domain interactor, repository/database/prefs, localization owner, navigation route, lifecycle
owner, tests, and current conformance status.

### 4.2 Reproduce the application-wide crash

The reported failure was: Settings could open, but navigating to Library, For You, manga detail,
Browse, reader, Source Evaluation, rated collections, or What's New closed the app. No reliable
stack trace was provided, so Claude must not claim this is fixed without reproducing or narrowing it.

Inspect and instrument the first process lifecycle and navigation boundary:

- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`;
- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivityScreen.kt` if present;
- Voyager navigator setup and screen registration;
- `app/src/main/java/eu/kanade/tachiyomi/ui/setting/SettingsScreen.kt`;
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt`;
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt`;
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt`;
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt` and `MangaScreenModel.kt`;
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt` and
  `BrowsePersonalRecommendationsScreenModel.kt`.

Capture the first exception, process/thread, route, and state transition. Check database opening,
SQLDelight migration, KMR resource loading, Injekt module initialization, saved navigation state,
Compose state restoration, and release-note parsing before changing any screen.

### 4.3 Phase gate

Do not proceed until the report states one of:

- reproduced root cause with exact file/symbol and failing test/reproduction;
- not reproducible, but narrowed to a bounded set of tested startup/navigation boundaries with a
  new diagnostic test or device log proving where execution stops.

## 5. Phase 1 - Official Komikku Conformance Contract

**Depends on:** Phase 0 inventory.

Before changing each KMK screen, compare it to the official components in the current tree:

- `eu.kanade.presentation.more.settings.screen.SearchableSettings`;
- `eu.kanade.presentation.more.settings.Preference`;
- `eu.kanade.presentation.more.settings.PreferenceScaffold`;
- `eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen`;
- the relevant official settings category screens under
  `eu.kanade.presentation.more.settings.screen.*`;
- `eu.kanade.presentation.more.WhatsNewScreen`;
- `eu.kanade.presentation.manga.components.MarkdownRender`;
- `tachiyomi.presentation.core.screens.InfoScreen`;
- official top bars, `SearchToolbar`, menus, dialogs, `FlowRow`, `LazyColumn`, and window-size
  responsive utilities already used by Komikku.

Write the concrete target contract before implementation:

- use `MaterialTheme` colors and `MaterialTheme.padding` tokens, not KMK-local magic colors/dp;
- use the same title/subtitle hierarchy and section spacing as `PreferenceScaffold`;
- use official `PreferenceItem` rows for settings controls where the row represents a preference;
- use the official search-toolbar pattern for searchable collections;
- use official top-bar navigation/actions and `DropdownMenu`/`ModalBottomSheet` patterns;
- use KMR for every KMK string and KMR plurals where counts are displayed;
- use stable keys for every lazy item and preserve state with `rememberSaveable` only for primitive,
  serializable UI state;
- use width-aware layouts for phone/tablet/landscape, not hardcoded tablet widths;
- support dark/light theme through theme tokens, not hardcoded black/white values;
- provide content descriptions, traversal order, touch targets, semantics, and TalkBack labels;
- represent loading, empty, offline, error, partial, retry, and cancellation states explicitly.

Do not introduce a universal KMK wrapper merely to rename existing components. Add a shared helper
only when the same concrete layout/state behavior is repeated in at least two existing KMK surfaces
and the helper preserves official Komikku semantics.

## 6. Phase 2 - Recommendation Settings And Source Evaluation UI

**Depends on:** Phases 0-1.

### 6.1 Exact files and symbols

Audit and update, using the official settings contract:

- `exh/recs/settings/RecommendationSettingsIndexScreen.kt`;
- `exh/recs/settings/RecommendationSettingsSearchScreen.kt`;
- `exh/recs/settings/RecommendationSettingsSearchIndex.kt`;
- `exh/recs/settings/RecommendationSettingsAnchorScroll.kt`;
- `exh/recs/settings/RecommendationForYouSettingsScreen.kt`;
- `exh/recs/settings/RecommendationSourcePrioritySettingsScreen.kt`;
- `exh/recs/settings/RecommendationTasteTagsSettingsScreen.kt`;
- `exh/recs/settings/SourceEvaluationScreen` and `SourceEvaluationScreenModel`;
- `exh/recs/settings/RecommendationNonInstalledDiscoverySettingsScreen.kt`;
- `exh/recs/settings/RecommendationDiagnosticsSettingsScreen.kt`;
- `exh/recs/settings/RecommendationSettingsSharedComponents.kt`;
- `exh/recs/settings/RecommendationsSettingsScreenModel.kt`.

The exact current symbols and route names must be verified before editing because the v0.8.8/v0.8.9
split changed several former section functions into separate screens.

### 6.2 Required behavior

- Keep the seven existing recommendation destinations and their behavior.
- Keep per-control search anchors from v0.8.10; do not regress category navigation.
- Make search results use the same visual row hierarchy and query normalization as official Settings
  search, while retaining KMK-specific anchors and category labels.
- Keep section summaries concise; move long explanations into the destination screen or an info
  row, matching official settings density.
- Replace any ad hoc bordered button groups with the closest existing Komikku preference/control
  pattern. Do not remove controls merely to reduce text.
- Ensure source-priority reorder uses stable item keys and saves only after a valid completed move;
  never mutate the list while a reorder callback is still resolving.
- Preserve the existing priority order across leaving the screen, process recreation, and profile
  reload. Add a regression test for order persistence and invalid/duplicate source IDs.
- In Source Evaluation, keep the evaluation list focused on the configured visibility rules,
  preserve installed-source exclusion, and show quarantine/blocked sections through the existing
  expandable/secondary UI rather than permanently dominating the first viewport.
- Keep the evaluation continuation cursor and independent compatibility cursor intact.
- Show explicit loading, no candidates, completed, stale, error, offline, cancelled, and retry
  states. A zero count must explain whether there are no eligible sources or whether a cursor has
  reached the end.

### 6.3 Tests

Add or update pure tests for anchor mapping, search ranking, reorder persistence, eligibility/count
agreement, and terminal-state rendering. On-device verify all seven settings destinations in both
orientations and at large font scale.

## 7. Phase 3 - For You, Browse, And Recommendation Rows

**Depends on:** Phases 0-2.

### Exact files/symbols to inspect

- `exh/recs/BrowsePersonalRecommendationsTab.kt`;
- `exh/recs/BrowsePersonalRecommendationsScreenModel.kt`;
- `exh/recs/BrowseRecommendsScreen.kt` and `BrowseRecommendsScreenModel.kt`;
- `exh/recs/RecommendationSourceRow.kt` and any current row/card components found by source search;
- `exh/recs/RecommendsScreen.kt` and `RecommendsScreenModel.kt`;
- `exh/recs/batch/RecommendationSearchHelper.kt`;
- `exh/recs/RecommendationPagingSource.kt`;
- `exh/recs/RecommendationQueryPlanner.kt` and `RecommendationQueryAttemptPolicy.kt`;
- `exh/recs/RecommendationCandidateVisibilityPolicy.kt`;
- `exh/recs/GroupPreviewLoadCoordinator.kt`, `GenerationGuard.kt`, and related cache policies.

### Required conformance and stability work

- Preserve source row ordering, deduplication, hidden-known behavior, disliked-source behavior,
  source-specific budgets, and rolling discovery state.
- Use official Komikku lazy-list/card spacing, stable keys, content padding, and loading/error row
  patterns. Do not create a second card system for KMK recommendations.
- Keep initial preview limits bounded by the existing preference; expansion must use the existing
  full-source search flow and must not duplicate the initial requests.
- Verify that every `LaunchedEffect`, `remember`, paging load, cache lookup, and source request has
  a stable key and cancellation path. A screen leaving must cancel only its foreground work while
  preserving explicitly background jobs.
- Verify that source failures are isolated to their row, partial success renders, and one slow
  source cannot block all rows.
- Verify empty rows are hidden only under the existing policy, not because an exception was swallowed.
- Verify `For You` and group recommendations share the intended candidate visibility, query fallback,
  source selection, and dedup policies without creating a new scoring system.

### Tests

Extend pure policy tests for cancellation, generation changes, duplicate rows, empty/error rows,
known-manga filtering, and bounded concurrency. Add a Compose or instrumented test where the existing
test infrastructure permits; otherwise document the exact manual matrix.

## 8. Phase 4 - Rated Collections, Groups, Matching, And Best Version

**Depends on:** Phases 0-3.

### Exact files/symbols to inspect

- `exh/recs/loved/RatedMangaScreen.kt`;
- `exh/recs/loved/LovedMangaScreen.kt` and `LovedMangaScreenModel.kt`;
- `exh/recs/loved/RatedMangaCollectionContent`;
- `exh/recs/loved/RatedSelectionReducer.kt`;
- `exh/recs/loved/LovedMangaDuplicateGrouper.kt`;
- `exh/recs/matching/CrossExtensionMatchScreen.kt` and `CrossExtensionMatchScreenModel.kt`;
- `exh/recs/bestversion/BestVersionCompareScreen.kt` and `BestVersionCompareScreenModel.kt`;
- `exh/recs/bestversion/BestVersionPageSampler.kt` and `BestVersionChapterMatcher.kt`;
- `eu.kanade.tachiyomi.ui.manga.MangaScreen.kt` and `MangaScreenModel.kt`.

### Required behavior

- Keep Love, Like, Dislike, and Not Interested mutually exclusive through the existing rating
  persistence path. Do not add duplicate rating storage.
- Keep long-press selection and explicit item menus distinct: long-press selects; the menu exposes
  See Recommendations, See Group Recommendations when a confirmed group has at least two versions,
  Find Other Versions, rating actions, and group actions.
- Keep manual group selection user-confirmed. Never auto-merge based on title alone.
- Make all three rated collections reuse the same collection scaffold, toolbar, search, sorting,
  filtering, group actions, linked-version list, undo, confirmation, and error UI.
- Ensure source/name/title metadata is localized or resolved before opening MangaScreen, avoiding the
  previously fixed `ResultSet returned null`/missing manga crash path.
- Ensure group recommendations seed from every confirmed linked version through the existing
  row-based recommendation pipeline, not from one representative only.
- Ensure cancel from Best Version returns to the preview screen, fullscreen preview state is safe on
  rotation/back, and selection changes cannot leave a stale migration dialog.

### Tests

Test rating exclusivity, selection-mode transitions, group conflicts, empty groups, missing sources,
uninstalled sources, localized title resolution, recommendation navigation, Best Version cancel,
rotation state, and malformed/partial candidate data.

## 9. Phase 5 - Reader, Timer, Schedule, OCR, And Completion Prompt

**Depends on:** Phases 0-4.

### Exact files/symbols to inspect

- `eu.kanade.tachiyomi.ui.reader.ReaderActivity.kt`;
- `eu.kanade.tachiyomi.ui.reader.ReaderViewModel.kt`;
- `eu.kanade.presentation.reader.ReaderTimerDialog.kt`;
- `eu.kanade.presentation.reader.ReaderScheduleDialog.kt`;
- `eu.kanade.tachiyomi.ui.reader.timer.*`;
- `eu.kanade.tachiyomi.ui.reader.schedule.*`;
- `eu.kanade.presentation.reader.ChapterCompletionRatingDialog` or the current prompt component;
- OCR screens/interactors under `exh/ocr/**` and `docs/ocr/README.md`.

### Required behavior

- Preserve official ReaderActivity lifecycle and viewer controls; KMK controls must use existing
  reader toolbar/menu/dialog patterns and not add a second settings style.
- Completion rating prompt appears only after genuine latest-chapter completion and after leaving the
  reader, never mid-read. Dismissal returns to the same destination as normal reader back navigation.
- Rating and rate-other-versions flows must be cancellable at each step and must not serialize enum
  or screen objects through Android Bundle state.
- Reading schedule must permit finishing the already-open chapter but must block opening another
  chapter/manga once restricted. Test back navigation, deep links, manual chapter selection,
  rotation, backgrounding, and process recreation.
- Timer and schedule dialogs must use existing time-picker, typography, spacing, button order, and
  validation conventions. Multiple windows, whole-day windows, AM/PM/24-hour display, edit, delete,
  Save, Cancel, and outside-dismiss behavior must remain correct.
- OCR must retain its branch-specific storage/privacy behavior, use existing progress/error UI, and
  avoid indexing on the main thread. Do not mix OCR-specific screens into unrelated recommendation
  settings without matching the established settings navigation pattern.

### Tests

Run timer/schedule reducers and codecs, completion-prompt reducer tests, OCR indexing/search/storage
tests, and device tests for reader exit, blocked chapter transitions, rotation, backgrounding, and
large-font dialog layout.

## 10. Phase 6 - Historical What's New Conversion

**Depends on:** no code phase, but must be completed before the final release.

### Exact files/symbols

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`, especially `MARKDOWN`,
  `VERSION_CODE`, and `VERSION_NAME`;
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt`;
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt`;
- `app/src/main/java/eu/kanade/presentation/more/WhatsNewScreen.kt`;
- `app/src/test/java/exh/recs/KmkRecsReleaseNotesTest.kt`.

### Required work

- Keep the official Komikku `MarkdownRender`/`GFMFlavourDescriptor` renderer.
- Convert every historical KMK entry, including v0.4.2 through v0.8.9 and every fix version, to
  the same readable New/Improve/Fix presentation used by v0.8.10.
- Preserve every version heading, release order, feature meaning, and fix distinction.
- Remove internal planning/audit/AI implementation prose from user-facing changelog content.
- Use concise bullets, bold area labels, clear spacing, and official heading depth.
- Add a migration/conversion audit test that compares the old entry set and new entry set by version,
  verifies no version disappeared, and checks that each converted entry contains only supported Markdown.
- Do not silently accept a “keep-as-is” decision; any intentionally unconverted entry requires explicit
  user approval and a documented reason, which is not the current approved requirement.

## 11. Phase 7 - Crash Hardening, Persistence, And Release Safety

**Depends on:** Phases 0-6.

### 11.1 Crash root cause

After reproducing the reported navigation crash, fix the first failing boundary. Candidate areas to
verify, not assumptions:

- database initialization and migration 63;
- KMR resource generation or malformed release-note Markdown;
- Injekt registrations in `KMKDomainModule.kt`;
- Voyager route arguments and saved state;
- Compose `rememberSaveable` values that are not primitive/Bundle-safe;
- source evaluation singleton/job state restored after process death;
- invalid source IDs or missing installed-source rows;
- screen-model initialization that performs network/database work before composition;
- upstream 1.14 API/type changes.

The final report must include the original stack trace or a bounded non-reproduction result. It must
not claim “all navigation is fixed” from a compile-only result.

### 11.2 Persistence and backup

- Verify migrations 1-63 remain append-only and do not change existing user data.
- Verify all KMK preferences, ratings, groups, seen keys, source preferences, OCR exclusions,
  timer/schedule state, and extension repositories have defined backup/sync behavior or an explicit
  documented non-sync decision.
- Keep `BackupDecoderErrorPolicy` narrow and ensure malformed input produces the official invalid
  backup state, never a process crash.
- Add one combined upgrade test where feasible, in addition to existing per-area tests.

### 11.3 Security/privacy

- Do not log titles, descriptions, OCR text, credentials, or full URLs unnecessarily.
- Keep diagnostics local and aggregate; do not add network analytics.
- Verify installer/Shizuku state is never enabled implicitly by a UI render.
- Review exported intents, notification deep links, backup JSON, OCR storage, and source-evaluation
  temporary files for path traversal, leaked data, and stale temporary artifacts.

## 12. Phase 8 - Verification And Handoff

### Automated checks

Run with the repository JDK 17 configuration:

1. `spotlessApply` if required;
2. `spotlessCheck`;
3. focused tests after each phase;
4. `:app:testDebugUnitTest`;
5. migration/backup/proto tests;
6. `assembleDebug`;
7. APK existence, size, hash, and filename checks.

### Device matrix

On at least one phone and one tablet, verify:

- fresh install and upgrade from the current v0.8.10 APK;
- app launch and navigation through every KMK and official destination;
- For You refresh, empty/error/partial rows, group recommendations, and Sources To Try;
- rated collections, grouping, search, menus, bulk actions, and recommendations;
- Source Evaluation first batch, continuation batch, stale reassessment, cancellation, retry,
  offline recovery, and background notification return;
- reader completion prompt, timer, schedule blocking, rotation, backgrounding, and process recreation;
- What's New every historical version and v0.8.10-fix1;
- dark/light themes, landscape, large font, TalkBack/content descriptions, and touch targets;
- malformed backup restore and recovery;
- no application-wide crash after leaving Settings.

### Final identity

- Keep upstream `versionName = 1.14.0` and `versionCode = 89` unless a separate approved upstream
  update is requested.
- Use the local follow-up identity `KMK-Recs v0.8.10-fix1` and the next monotonic local release-note
  code according to `RECOMMENDATION_VERSIONING.md`.
- Use a matching APK name such as `Komikku-v1.14.0-kmk.8.10-fix1-debug.apk`.
- Place the final APK under `C:\Users\USER\Downloads\Komikku\private\`.
- Keep all development-channel wording out of the application UI and What's New.

## 13. Required Documentation Outputs

Claude must create/update these only after the corresponding work is actually complete:

- a v0.8.10-fix1 implementation report with requested/actual model and effort;
- `docs/recommendations/CURRENT_STATE.md` with verified behavior and limitations;
- `docs/recommendations/NEXT_WORK.md` with only genuinely open work;
- `docs/recommendations/README.md` with this plan/report indexed;
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md` with the new plan, exact source map, and any new constant
  procedure;
- `RECOMMENDATION_VERSIONING.md` with the final local identity and APK name;
- `KmkRecsReleaseNotes.kt` with the complete historical conversion and fix entry.

The implementation report must list every changed file, every removed file, tests run, device matrix
results, crash evidence, deviations, and residual limitations. “Build passed” alone is insufficient.

## 14. Completion Gate

The work is complete only when:

- every KMK surface has been checked against an existing Komikku pattern;
- the historical What's New entries are converted and validated;
- the application-wide crash is fixed or conclusively narrowed with a documented external-device
  blocker and no unsupported claim of completion;
- all automated tests and required device checks pass;
- documentation, version metadata, and APK naming agree;
- no stale plan claims that a completed feature is still planning-only;
- the final APK is built only after all preceding gates pass.
