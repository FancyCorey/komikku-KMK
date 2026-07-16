# KMK-Recs v0.8.7-fix1 and v0.8.8 Implementation Report

**Date:** 2026-07-16

**Feature version/build label:** KMK-Recs v0.8.8 (VERSION_CODE 758, `KmkRecsReleaseNotes.kt`) — ships
both the v0.8.7-fix1 schedule-enforcement fix and the v0.8.8 plan's three phases as one release, per
the addendum's own precedent (v0.8.2-v0.8.5 shipped as one coordinated session under one final
version).

**User-approved scope:** Three authoritative plan documents, treated as executable specifications:
- `docs/recommendations/KMK_RECS_V0_8_7_FIX1_EXECUTABLE_IMPLEMENTATION_PLAN.md`
- `docs/recommendations/KMK_RECS_V0_8_8_EXECUTABLE_IMPLEMENTATION_PLAN.md`
- `docs/recommendations/KMK_RECS_V0_8_7_AND_V0_8_8_DETAILED_EXECUTION_ADDENDUM.md`

## Gap-closing pass: Phase 3 full per-category split (this update)

The first pass (below, unchanged) shipped only a settings index screen with Evaluation routed
directly and every other category still routing to the single, unchanged
`RecommendationsSettingsScreen`. Asked to reconsider rather than decline again, I re-read that
screen fully and split it for real.

### What was split

Six new screens, each extracted **verbatim** from the former single `RecommendationsSettingsScreen.kt`
(same `item(key = ...)` blocks, same `screenModel` method calls, same state field reads — the exact
per-item code, just moved into its own file/screen):

- `RecommendationForYouSettingsScreen.kt` — daily-recommendations language selector, ratings/known-
  manga visibility, min-chapter filter, For You/group-preview result budgets.
- `RecommendationSourcePrioritySettingsScreen.kt` — the drag-and-drop reorderable source list,
  restore-default/suggest-order actions, and the source-status display-order breakdown. Also carries
  Same-Manga Matching and Best Version preview settings (see "Genuine structural note" below for why).
- `RecommendationTasteTagsSettingsScreen.kt` — tag preference chips + add/edit dialog.
- `RecommendationNonInstalledDiscoverySettingsScreen.kt` — the Sources To Try suggestion list, bulk
  install/selection, and quality-mark recovery actions.
- `RecommendationDiagnosticsSettingsScreen.kt` — Best Version history entry point, discovery/cache
  management (enrichment cap, reset discovery history).
- `RecommendationSettingsSharedComponents.kt` (new, not a screen) — the composables every one of the
  above needs (`SectionHeader`, `SourcePriorityItem`, `SourceSuggestionItem`, `TagPreferenceDialog`,
  `SameMangaListPrefRow`/`SameMangaSwitchRow`, etc.), changed from `private` to `internal` visibility
  so they can be shared across files in the same package without duplicating a single line of them.

The former `RecommendationsSettingsScreen.kt` (1,450+ lines) was deleted entirely — every line of its
content now lives in exactly one of the files above, with zero duplication and zero behavior change.
`RecommendationSettingsIndexScreen.kt` now routes all seven rows to real, distinct destinations.

### Genuine structural note (per instruction #3 — one exception, not a blanket re-decline)

**Background/network/installer behavior** has no distinct content in the former
`RecommendationsSettingsScreen` at all — installer mode, batch size, and network-retry behavior are
controls that live entirely inside `SourceEvaluationScreen` (already a separate screen, unrelated to
this pass). There was nothing to extract. Creating an empty new screen whose only content is a button
to `SourceEvaluationScreen` would add a pointless extra tap, so this index row routes directly to
`SourceEvaluationScreen`, exactly like Evaluation does. This is a real, live-code-verified structural
fact (confirmed by re-reading `RecommendationsSettingsScreen.kt` in full before writing any code),
not a re-declination of the whole task.

**Same-Manga Matching and Best Version** settings also don't map to any of the plan's seven named
categories (For You / source priority / taste-tags / evaluation / non-installed discovery /
background-installer / diagnostics). They were physically adjacent to the source-priority list in the
original screen (immediately following it), so they were kept in
`RecommendationSourcePrioritySettingsScreen` rather than invented a new "Matching" category the plan
never named. This is a judgment call, documented rather than silent.

### Scroll-to-section

No longer relevant, exactly as instruction #4 anticipated: each category is now its own screen with
its own bounded `LazyColumn`, so there is no "scroll past unrelated sections to reach this one"
problem left to solve. The prior pass's decline was specifically about the *unsafe-to-scroll-to*
risk within one shared, variable-length screen — that constraint doesn't exist anymore now that the
split is real.

### Tests

No new tests were added for the split itself. `RecommendationsSettingsScreenModel` was not changed in
any way (same class, same methods, same state shape) — the split only moves which `item{}` blocks each
screen's `Content()` registers, so there is no new pure logic to extract and test. Voyager navigation
between the index and the six new screens has no state-holder logic to test either (each `Item.screen`
is a static `Screen` instance in a list) — this repo has no Compose-UI-test/Robolectric
infrastructure to verify Voyager push/pop behavior directly, the same gap already documented for
every other Compose-layer change this session. `RecommendationSettingsSectionSummariesTest.kt`
(existing, unchanged) continues to cover the pure summary-derivation logic these screens' headers use.

### Verification (gap-closing pass)

- `./gradlew spotlessApply` / `spotlessCheck` — clean, run after adding the shared-components file and
  again after each of the six screens.
- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL (fixed two real compile errors along the way:
  a dangling top-level KDoc ktlint violation in the shared-components file, and a missing
  `androidx.compose.foundation.layout.padding` import in two of the new screens — `Modifier.padding()`
  was resolving to the wrong `padding` symbol without it).
- `./gradlew :app:testDebugUnitTest` (full suite) — result recorded in the final message.
- `./gradlew assembleDebug` — result recorded in the final message.
- Confirmed via `grep` that no file anywhere in `app/src/main/java` or `app/src/test/java` still
  references the deleted `RecommendationsSettingsScreen` class.

## Findings summary (mandatory preflight)

### Reader schedule flow (`ReaderViewModel.kt`, `ReaderActivity.kt`, `ReaderTimerCoordinator.kt`)

Confirmed the plan's stated defect exactly: `evaluateSchedule()` granted chapter-grace on
`restricted && scheduleGraceCoordinator.state.value.phase == IDLE` — indistinguishable between "this
reader just opened while restricted" and "this reader was already active and just became
restricted," since a brand-new `ReaderTimerCoordinator` is always `IDLE`.

**Beyond what the plan stated**: I found the schedule had **no actual enforcement at all** —
`RESTRICTED`/`EXPIRED` phases only ever drove a `toast(...)` call in `ReaderActivity.kt`; nothing
blocked chapter/page loading anywhere. This was documented and reported at the first checkpoint
before any fix code was written, per the "stop and document" instruction.

### Chapter-completion flow (`ReaderViewModel.updateChapterProgress`)

`updateChapterProgressOnComplete(readerChapter)` is called exactly when `pageIndex ==
pages.lastIndex` (or the extra-page equivalent) inside `updateChapterProgress` — a genuine,
restore/rotation-safe completion signal (not re-triggered by page-status recomposition once past that
check, and not fired on state restoration since it only runs inside the live page-selection path).
"Latest chapter" is derivable from `state.value.viewerChapters?.nextChapter == null` — the same field
`loadNextChapter()`/`loadPreviousChapter()` already use for navigation, never a chapter name/number
comparison.

### Rating/group flow (`MangaScreenModel.kt`, `LovedMangaScreenModel.kt`, `CrossExtensionMatchScreen.kt`)

`SetMangaTaste` interactor is the single existing exclusive-rating mutation, used identically by
`MangaScreenModel.setMangaTaste(rating)` and `LovedMangaScreenModel`'s bulk-rating methods.
`GetCrossSourceMangaLinks.awaitBySourceUrl(source, url)` returning non-null is the existing
"has a confirmed group" check (same one `LovedDisplayItem.hasConfirmedGroup` is built from).
`CrossExtensionMatchScreen.fromMode(mangaId, CrossExtensionMatchMode.Rating(rating))` is the existing
selector entry point `RatedMangaScreen`'s "Find Other Versions" already uses — confirmed as the
correct reuse target.

### Recommendation-settings structure (`RecommendationsSettingsScreen.kt`, `SourceEvaluationScreen.kt`)

`RecommendationsSettingsScreen.kt` is one 1,450+-line screen containing every section (For You,
Ratings/Known Manga, Tags, Source Priority — including a live drag-and-drop reorderable list built
from `items(count = sourcesState.size, ...)` — Same-Manga Matching, Source Evaluation entry button,
Management, Sources To Try, Discovery/Cache). `SourceEvaluationScreen.kt` is already a genuinely
separate screen, reached via a button inside the settings screen. `SettingsMainScreen.kt` (the app's
main More>Settings entry point) is the established index pattern: a `LazyColumn` of
`TextPreferenceWidget` rows, each pushing a distinct destination screen.

**Discrepancy vs. the plan's implicit assumption**: the plan describes converting "the recommendation
settings root into an index with sections," implying each category has (or should get) its own
detail screen. In the live tree, only Source Evaluation is a separate screen; every other category is
one section of one shared screen. Splitting the other six categories into six new screens, each
correctly re-wired to `RecommendationsSettingsScreenModel`'s existing state without breaking any
preference, is a large, high-risk restructuring I could not visually verify in this environment. See
"Phase 3 scope decision" below for exactly what was built instead, and why.

### Outdated-evaluation continuation path (`SourceEvaluationScreenModel.kt`, evaluation policies)

Found substantially more existing infrastructure here than the plan's framing suggested was missing:
`SourceEvaluationCandidateQueuePolicy.staleCandidates()` (a first-class stale-queue candidate list,
independent of the unassessed queue's `skipAlreadyEvaluated` option — added in v0.8.1-fix3),
`SourceEvaluationContinuationPolicy` (a real, tested, cursor-based `sliceForRun`/`advanceCursor`
pair keyed by extension identity, fingerprinted by filter options so option changes invalidate the
cursor but batch-size changes don't — genuinely continues past 10/25/50/100 without restarting at
offset zero), and explicit reassessment already forces `skipAlreadyEvaluated = false, reEvaluateStale
= true` regardless of the user's persisted toggles (`launchEvaluation`'s `staleRun` branch). These
already satisfy several of the plan's Phase 4 requirements as stated.

**The actual root cause I traced and confirmed** (see `SourceEvaluationOutdatedReconciliation.kt`'s
class doc for the full pipeline trace) is narrower and more specific than "a count query and a work
query disagree" in one function — it's a **display-vs-work eligibility mismatch across two
independent, correctly-designed computations**: the per-row "Outdated — reassess needed" label
(`SourceEvaluationDisplayPolicy.state`) is computed purely from the stored `SourceEvaluation` row
(version/expiry/verdict) with zero awareness of current candidate-pool eligibility, while the
reassessment queue (`SourceEvaluationCandidateQueuePolicy.staleCandidates`) is filtered from
`SourceEvaluationCandidateFilter.buildPool`'s `allEligible`, which unconditionally excludes
extensions that are now installed, untrusted, language-mismatched against the *current* filter,
nsfw-disabled, disliked, or quarantined. **A source can correctly show "Outdated" (its stored verdict
really is stale) while being permanently unreachable by "Continue reassessing outdated" this run** —
with no error and no candidates, because from the queue's point of view there was nothing to do. This
exactly matches the reported symptom. See "Phase 4 fix" below for why I did not collapse these into
one shared policy (that would be architecturally wrong) and what I built instead.

## Phase 1: v0.8.7-fix1 schedule enforcement (gap-closing, from the prior checkpoint)

Already reported and merged in the immediately preceding session turn:
- `ReaderScheduleEntitlement.kt` (new, pure): the plan's exact
  `NotStarted -> OpenedWhileAllowed -> CurrentChapterGrace -> GraceConsumed -> Closed` /
  `NotStarted -> OpenedWhileRestricted -> Closed` state machine.
- `ReaderViewModel.kt`: `scheduleEntitlement` field owned per-session; `evaluateSchedule()` rewritten
  to drive it; `isChapterNavigationBlockedBySchedule()` gate checked directly by `init()` (initial +
  deep-link load), `loadAdjacent()` (manual dialog selection + toolbar next/prev), and
  `loadNewChapter()` (natural forward paging — found as a separate bypass not covered by
  `loadAdjacent`'s gate).
- `ReaderActivity.kt`: full-screen blocking overlay, drawn last so it covers and consumes touch over
  page content and app bars.
- 18 tests in `ReaderScheduleEntitlementTest.kt`.

## Phase 2: latest-chapter rating prompt

- `LatestChapterCompletionPolicy.kt` (new, pure) — `isGenuineLatestChapterCompletion(pageIndex,
  lastPageIndex, hasExtraPage, hasNextChapter, isErrorPage)`, mirroring
  `updateChapterProgress`'s own last-page check exactly, never a string/number comparison. 8 tests.
- `ReaderViewModel.kt`:
  - `ratingPromptEvaluatedForChapterId` dedup guard (keyed per chapter id) — the prompt is evaluated
    at most once per chapter, so it can never fire twice for the same completion.
  - `Dialog.ChapterCompletionRating(mangaId: Long)` / `Dialog.ChapterCompletionRatingGroupOffer(mangaId: Long, ratingValue: Int)`
    — IDs and a primitive rating value only; no screen or `CrossExtensionMatchMode` object is ever
    held in `ReaderViewModel.State` or passed through an `Intent`.
  - `maybeShowChapterCompletionRatingPrompt` checks `isChapterNavigationBlockedBySchedule()` (the
    exact Phase 1 gate, not a new/separate check) before ever showing the prompt — the prompt cannot
    become a schedule-bypass vector.
  - `rateFromChapterCompletionPrompt(mangaId, rating)` calls `setMangaTasteInteractor.await(...)`
    (the same exclusive-rating mutation `MangaScreenModel`/`LovedMangaScreenModel` use), then checks
    `getCrossSourceMangaLinks.awaitBySourceUrl(...)` to decide whether to offer step 2.
  - `markNotInterestedFromChapterCompletionPrompt` reuses `SeenRecommendationMangaStore`.
  - `dismissChapterCompletionPrompt` only clears the dialog — it never undoes a rating already
    committed by a successful `setMangaTasteInteractor.await(...)` call in step 1.
- `Constants.kt` (core:common): new `OPEN_CROSS_EXTENSION_MATCH_FOR_RATING` action +
  `CROSS_EXTENSION_MATCH_MANGA_ID_EXTRA`/`CROSS_EXTENSION_MATCH_RATING_EXTRA` — primitives only
  through the `Intent`, mirroring the existing `OPEN_SOURCE_EVALUATION` precedent in
  `MainActivity.kt` exactly. `MainActivity.kt`'s intent-routing `when` gained one new branch that
  constructs `CrossExtensionMatchScreen.fromMode(mangaId, CrossExtensionMatchMode.Rating(rating))`
  fresh on the receiving end.
- `ReaderActivity.kt`: two new `AlertDialog` branches; new KMR strings
  (`chapter_completion_rating_title`, `chapter_completion_rating_group_offer_title`,
  `chapter_completion_rating_group_offer_message`).

## Phase 3: Recommendation Settings navigation

- `RecommendationSettingsIndexScreen.kt` (new) — mirrors `SettingsMainScreen.kt`'s exact pattern
  (`TextPreferenceWidget` rows in a `LazyColumn`, each pushing a destination screen). Seven rows: For
  You, Source Priority, Taste and Tags, Source Evaluation, Sources To Try (non-installed discovery),
  Background/Network/Installer, Management/Diagnostics.
- `BrowsePersonalRecommendationsTab.kt`'s "Open settings" action now pushes
  `RecommendationSettingsIndexScreen` instead of `RecommendationsSettingsScreen()` directly — this is
  the only changed call site; `RecommendationsSettingsScreen` itself, its screen model, and every
  preference it reads/writes are completely untouched.

### Phase 3 scope decision (documented, not silently reinterpreted)

- **Evaluation** routes directly to `SourceEvaluationScreen()` — a genuine navigation improvement
  (previously reachable only via an extra tap through the settings screen's "Open Source Evaluation"
  button).
- The other six rows all navigate to the same existing `RecommendationsSettingsScreen()`. I
  considered and rejected automatic scroll-to-section: several of that screen's sections (Same-Manga
  Matching, Source Evaluation entry, Management, Sources To Try, Discovery/Cache) sit *after* the
  variable-length reorderable source-priority list and conditional rows (e.g. "Suggest priority order"
  only appears when `state.suggestFitOrderAvailable`), so their `LazyColumn` item index shifts with
  the user's actual source count and enabled options — a hardcoded index table would scroll to the
  wrong section for some users, which is a worse and harder-to-notice bug than not auto-scrolling at
  all. I judged this a correctness/regression-risk trade-off not worth taking, especially since I
  cannot visually verify scroll behavior in this environment. Splitting the six categories into six
  real, independently-scoped detail screens (the more thorough interpretation of the plan) was also
  not attempted, for the same reason: a restructuring of that size and risk, done without any way to
  visually confirm the result, was judged out of proportion to what could be safely delivered and
  verified this session.
- No preference, screen model, or existing setting's behavior changed anywhere in this phase.

## Phase 4: outdated-evaluation continuation fix

See the "Findings summary" above for the full traced pipeline and confirmed root cause.

### The fix

`SourceEvaluationOutdatedReconciliation.kt` (new, pure) — `reconcile(allEvaluations, pool, now)`
computes, from the full evaluation history and the current eligible candidate pool:
- `totalOutdatedExtensionKeys` — every extension whose stored evaluation(s) are stale per
  `SourceEvaluationDisplayPolicy.isStaleForRanking` (i.e. exactly what the UI already labels
  "Outdated"), independent of pool eligibility.
- `workableOutdatedExtensionKeys` — the subset that is actually present in
  `pool.allEligible` right now — i.e. exactly what `staleCandidates()` will process.
- Derived: `unreachableOutdatedCount`, `allOutdatedAreUnreachable`.

**Why this is not "one shared candidate-selection policy used for both the displayed count and the
launched work"** (the plan's literal Phase 4 requirement): the display label and the work queue
correctly answer two different questions — "is this stored verdict stale?" (a property of the
evaluation row alone) versus "can I run a fresh evaluation for this extension right now?" (a property
of current installed/language/dislike/quarantine state). Merging them would either make installed
extensions' evaluation history disappear from view (wrong — Source Evaluation history should remain
visible for installed sources) or make the reassessment queue try to evaluate installed extensions
(wrong — Source Evaluation is explicitly a non-installed discovery tool; `buildPool`'s
`installedPkgNames` exclusion is intentional and must not be removed). Instead, the fix reconciles the
two so the mismatch is surfaced honestly to the user instead of silently producing a zero-candidate,
apparently-broken queue.

- `SourceEvaluationScreenModel.kt`: `State.outdatedReconciliation` field, computed alongside
  `staleCandidates`/`canContinueStale`/`remainingStaleCandidateCount` in the same refresh path (not a
  separately-timed computation — this is exactly what prevents a *new* count/work disagreement).
- `SourceEvaluationScreen.kt`: a new `InfoCard`, shown only when `unreachableOutdatedCount > 0`,
  explaining either "N outdated, but none can be reassessed right now" (`allOutdatedAreUnreachable`)
  or "M of N outdated can't be reassessed right now" (partial) — replacing the previous silent no-op
  with a truthful explanation. New KMR strings `source_evaluation_outdated_all_unreachable` /
  `source_evaluation_outdated_some_unreachable`.

### What Phase 4 does NOT include (disclosed, not hidden)

- A full rewrite of `SourceEvaluationCandidateFilter`/`buildPool` into "one shared policy" — judged
  architecturally wrong per the reasoning above, not merely out of scope.
- New failure-category classification beyond what already exists
  (`SourceRecommendationFitFailureClassifier`, `SourceEvaluationProbeErrorClassifier` already
  distinguish offline/timeout/install/probe/persistence/cancellation categories from prior sessions)
  — I did not find evidence these mis-classify an empty/filtered candidate set as generic failure; the
  actual reported symptom traced to the display/work mismatch above, not to error classification.
- A per-extension "why exactly is this one excluded" breakdown (installed vs. language vs. disliked
  vs. quarantined) — the fix reports aggregate unreachable counts, not a reason-per-source table. This
  is a reasonable follow-up, not built here.

## Tests added (this session, both phases combined with the Phase 1/2/3 checkpoints above)

- `ReaderScheduleEntitlementTest.kt`: 18 tests (Phase 1).
- `LatestChapterCompletionPolicyTest.kt`: 8 tests (Phase 2).
- `SourceEvaluationOutdatedReconciliationTest.kt`: 10 tests (Phase 4), including a 250-synthetic-
  source test (100 current, 100 workable-outdated, 50 unreachable-outdated) that additionally drives
  the *existing* `SourceEvaluationContinuationPolicy` through 4 real batches of 25 to prove: the
  displayed workable count exactly matches the actual `staleCandidates()` size (the count/work
  agreement the plan requires), every workable candidate is processed exactly once across batches
  (no duplicates), and continuation does not restart from the beginning (exactly 4 batches for 100
  candidates at batch size 25, not more).
- No new tests for Phase 3 (`RecommendationSettingsIndexScreen`) — it is Compose-UI navigation with
  no pure logic to extract (every row's target is a static list); this repo has no
  Compose-UI-test/Robolectric infrastructure to test Voyager navigation directly, consistent with
  every other Compose-layer gap already documented earlier this session.

## Tests run (final, both passes)

- `./gradlew spotlessApply` / `spotlessCheck` — clean, run after every phase/screen and again at the
  very end of both passes.
- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL, run repeatedly.
- `./gradlew :app:testDebugUnitTest` (full suite) — **109 test result files, 0 failures/0 errors**,
  confirmed after the Phase 3 gap-closing split (no regressions from deleting
  `RecommendationsSettingsScreen.kt` and adding the seven new files).
- `./gradlew assembleDebug` — **BUILD SUCCESSFUL** (both after the first pass and again after the
  gap-closing split).

## Known limitations

1. Phase 3 is now fully split (gap-closing pass) — see "Gap-closing pass: Phase 3 full per-category
   split" above. Background/Installer intentionally routes to `SourceEvaluationScreen` rather than a
   new empty screen — a confirmed structural fact, not a decline.
2. Phase 4's fix is a reconciliation/transparency fix, not a full rebuild into one shared
   candidate-selection policy — see "Phase 4: outdated-evaluation continuation fix" above for why.
3. No per-extension exclusion-reason breakdown (installed vs. language vs. disliked vs. quarantined)
   in the new unreachable-count UI — only an aggregate count.
4. Same Compose/Robolectric test-infrastructure gap already documented in every prior report this
   session: `ReaderViewModel`, `SourceEvaluationScreenModel`, and the new Compose dialogs/screens are
   not directly unit-testable; all new pure logic is tested at the extracted-function boundary, and
   call-site wiring is verified by code inspection only.
5. **Device/manual QA was not performed** — no physical phone/tablet available in this environment.
   Required scenarios, all "not executable in this environment, requires manual QA":
   - Schedule: reader opened while restricted is blocked immediately; an already-open reader crossing
     into restriction finishes only its current chapter; leaving and reopening (same or different
     manga) grants no new allowance; manual chapter selection/deep link/rotation/background-resume
     cannot bypass the block; existing schedule editor (whole-day/overnight/multi-window) unaffected.
   - Rating prompt: fires only on genuine latest-chapter completion; never twice; every rating action;
     group-offer Yes/No/cancel; behavior during a schedule restriction.
   - Settings navigation: index → each destination; back-stack behavior; narrow-phone layout;
     accessibility traversal.
   - Source Evaluation: the new unreachable-outdated explanation card renders correctly and
     "Continue reassessing outdated" behavior matches it; multi-batch continuation on a real device.

## Follow-up recommendations

- Split Recommendation Settings into real per-category screens (Phase 3's fuller interpretation), with
  device verification of each section's layout before merging.
- Add a per-extension exclusion-reason breakdown to the outdated-reconciliation UI.
- Build a Robolectric-based (or equivalent) test harness so `ReaderViewModel` and
  `SourceEvaluationScreenModel` can be integration-tested directly.
- Consider whether `SourceEvaluationDisplayPolicy`'s row label itself should optionally indicate
  "not currently reassessable" per-row (a stronger UX than the current aggregate banner), once a
  reason-per-source computation exists.

## Deviations from the approved plans

- Beyond the plan's stated Phase 1 defect, actual reading enforcement did not exist at all in the
  live tree (toast-only) — built the missing blocking mechanism as part of the fix; reported at the
  first checkpoint before writing fix code, per the "stop and document" instruction.
- Phase 3 did not produce per-category detail screens or scroll-to-section — see "Phase 3 scope
  decision."
- Phase 4's fix reconciles rather than merges the display and work-eligibility computations — see
  "Phase 4: outdated-evaluation continuation fix" for the architectural reasoning, confirmed against
  the live `buildPool`/`SourceEvaluationDisplayPolicy` code before writing any fix code.
- Versioning: both plans shipped as one release, `KMK-Recs v0.8.8` (VERSION_CODE 758), rather than as
  two separate version bumps — matching the addendum's own "v0.8.7-fix1 and v0.8.8" combined framing
  and the established v0.8.2-v0.8.5 precedent for a single coordinated session. The Phase 3
  gap-closing pass did not bump the version further — it is a navigation-only follow-up within the
  same v0.8.8 release (no preference, behavior, or scoring change), consistent with how other
  gap-closing passes were handled earlier in this session.

## Final APK

`app/build/outputs/apk/debug/app-universal-debug.apk`, copied to
`C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.8-debug.apk` (178,519,005 bytes),
confirmed present on disk after the Phase 3 gap-closing pass's `assembleDebug` run. VERSION_CODE 758,
VERSION_NAME "KMK-Recs v0.8.8" (unchanged from the first pass).
