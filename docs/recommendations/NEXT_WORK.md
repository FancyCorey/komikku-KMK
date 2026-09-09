## Jump to last-read chapter — 2026-08-09 (batch J1) — IN_PROGRESS

Distinct feature batch, not part of the recommendation line. Full record:
`private/docs/audits-and-reports/KMK_JUMP_TO_LAST_READ_CHAPTER_IMPLEMENTATION_LEDGER_2026-08-09.md`.

- **Landed (DONE):** `LastReadChapterTargetPolicy` (new, 26 tests, 0 failures) defining the target rule
  purely from `read` + `lastPageRead` + `manga.sortDescending()` — **no new column, no migration, no new
  timestamp**. `lastModifiedAt` was deliberately rejected as a read timestamp (any write moves it).
  `MangaScreenModel.resolveLastReadChapterTarget()` / `currentIndexOfChapter()`.
  `MangaToolbar(onClickJumpToLastRead)` + `Icons.Outlined.History` + KMR strings.
- **NOT landed (IN_PROGRESS):** the screen-level scroll wiring. The toolbar parameter defaults to `null`
  and **no call site supplies it**, so the action is not visible in the app and the feature is **not
  user-reachable**. Original Komikku behavior is byte-identical meanwhile.
- **Next step, precisely scoped:** thread the lambda into `MangaScreenSmallImpl` (toolbar ~line 558) and
  the tablet impl (~line 1064); own a single `jumpJob` (cancel-and-replace on repeated taps); re-resolve
  by identity then `LastReadChapterTargetPolicy.toLazyListIndex(layoutInfo.totalItemsCount,
  listItem.size, index)` and `animateScrollToItem`; snackbar `jump_to_last_read_unavailable` when the
  target is filtered away.
- **Verified by source inspection:** `sharedChapterItems` is the last LazyColumn emitter in both
  layouts, so the header offset can be derived from live `layoutInfo` rather than hardcoded. The chapter
  list is fully materialised (no chapter pager), so "target outside the first loaded page" does not
  arise.
- **BLOCKED_EXTERNAL:** rendered scroll/theme/layout/TalkBack validation — no Compose UI or screenshot
  infrastructure in this repository.

## Latest catalogue + exposure packet — 2026-08-09 (batch L6: real 63 → 64 path + evidence classes)

Supersedes the batch L5 entry below only for the migration row (L5 claimed migration-64 coverage
`DONE`; the test skipped schema 63, so that claim was overstated). Full record:
`private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`
("Batch L6").

- **Real 63 → 64 migration path — DONE.** New
  `app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigration63To64UpgradeTest.kt` (12 tests)
  drives the generated `Database.Schema.migrate(...)` shipped in the app. SQLDelight's convention is
  that migration file `N` runs when `oldVersion <= N && newVersion > N`, so real file 63 is
  `migrate(driver, 63, 64)` and real file 64 is `migrate(driver, 64, 65)`; `Database.Schema.version`
  is 65 and a test guards that constant. The pre-63 fixture reproduces `extension_repos` verbatim from
  `32.sqm`. The existing `46..62` range test and `KmkMigration64ExposureTest` were left **unchanged** —
  no migration-contract reason to widen them.
- **Tracker tri-state — DONE, re-verified not rewritten.** All eight required properties confirmed
  against current source (failed → `Unknown`; `Unknown` leaves candidates unchanged; successful empty →
  known-empty; cancellation rethrown; composite `(sourceId, url)`; fixed-string log with no
  identifiers; legacy `await()` and both its callers unchanged; library/rated exemptions intact).
- **End-to-end recommendation behavior — DONE, re-verified.** Lane provenance survives merge; majority
  uses realised counts not configured slots; enforcement is the cap; exposure identity is source-aware
  at capture; clear-history has a real UI caller; min-chapter is applied at the candidate-visibility
  stage.
- **Direct ScreenModel validation — PARTIAL_VALIDATION.** New blocker detail beyond earlier passes:
  `context.isOnline()` is a **top-level extension function** (`NetworkExtensions.kt:16`), so it compiles
  to a static and cannot be stubbed by `mockk<Context>(relaxed = true)` — it would need `mockkStatic`.
  Combined with no Robolectric, the `Application` requirement, and `init` starting a full load on
  construction, a bounded direct harness is not constructible. **Smallest remediation (next batch):**
  add an `autoLoad: Boolean = true` constructor parameter guarding the `init` launch, **or** replace the
  `Context.isOnline()` call with a one-method injectable `NetworkStatus` interface. Either makes a
  direct harness constructible with mockk alone.
- **Domain D — BLOCKED_EXTERNAL, unchanged.** No Compose UI or screenshot infrastructure exists here.
- **`private/control/MIGRATION_STATUS.md` — inspected, not affected.** It tracks publication-readiness
  device batches, not SQLDelight schema versions.
- **Overall feature status: DONE_WITH_VALIDATION_GAP.**

## Latest catalogue + exposure packet — 2026-08-09 (batch L5: tracker fail-safety + migration 64)

Supersedes the batch L3 entry below only for the tracker row (L3 marked Domain B `DONE`; the tracker
sub-contract was in fact unsafe, so that specific claim was premature). Full record:
`private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`
("Batch L5").

- **Tracker fail-safety — DONE.** `GetTracks.awaitOrNull()` (new, additive; `await()` unchanged and both
  pre-existing callers verified untouched) lets `resolveTrackedExposureKeys()` return a real tri-state:
  `TrackedState.Known(keys)` or `TrackedState.Unknown`. `RecommendationDisplayReranker.rerank()` returns
  the candidate list unchanged when tracker state is `Unknown`, so a failed lookup can never penalise a
  possibly-tracked title. `InteractionSignals.tracked` defaults to `Unknown` so a forgetful caller gets
  the safe path. Proven through the production-facing contract — a real `GetTracks` over a failing
  `TrackRepository`, and the real `RecommendationCandidateMemoryRanker.merge()` — not only the pure
  policy. 11 new tests + `RecommendationDisplayRerankerTest` 20 → 24.
- **Migration 64 coverage — DONE.** New `KmkMigration64ExposureTest` (14 tests). Adding coverage
  surfaced a real defect: `64.sqm` used bare `CREATE TABLE`/`CREATE INDEX`, violating the `IF NOT EXISTS`
  idempotency contract `KmkMigrationTest` enforces; fixed in place. 64 was deliberately kept out of
  `KMK_MIGRATION_RANGE` because widening it would have to include 63 (the upstream 1.14.0 reconciliation,
  which legitimately ALTERs upstream tables) and break that test's own invariants.
- **Direct ScreenModel validation — PARTIAL_VALIDATION, not DONE.** A minimal harness is not
  constructible with existing infrastructure. Exact blockers: no Robolectric anywhere in the project
  (verified by grep over `libs.versions.toml` and `app/build.gradle.kts`); the constructor requires a
  real Android `Application` for `context.isOnline()`/`context.toast(...)`; and
  `init { screenModelScope.launch { load(...) } }` starts a full recommendation load on construction, so
  there is no inert construction path. Closing this needs either Robolectric (a new framework, out of
  scope) or an init/context refactor — its own batch.
- **Domain D — BLOCKED_EXTERNAL, unchanged.** No Compose UI or screenshot infrastructure exists here.
- **Validation:** 2574 tests / 0 failures / 0 errors / 1 skipped (baseline 2545, +29); compile, focused
  suites, `spotlessCheck`, and `git diff --check` all exit 0. Security scans not re-run (unchanged).
  **Working tree dirty: 378 paths** (baseline 376; delta is exactly the two new test files). Nothing
  committed, pushed, or cleaned.

## Latest catalogue + exposure packet — 2026-08-09 (batch L3: structural repair pass)

Supersedes the batch L2 entry below where they disagree; L2 is left unchanged as historical record.
Full record: `private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`
("Batch L3"), including a per-requirement matrix mapping each requirement to its production caller,
persistence, UI path, tests, evidence, and rollback.

- **Domain A — DONE.** `RecommendationLatestBudgetPolicy.enforcePersonalizedMajority()` now decides the
  final row composition from realised per-lane counts, called from
  `RecommendationCandidateMemoryRanker.merge()`'s cap. `PersonalRecommendation.lane` carries provenance
  through the merge. Proven by `RecommendationLatestLaneCompositionTest` (14 tests) including an
  exhaustive sweep over 5 display limits × 5 percentages × every personalized count from 0 to the limit.
- **Domain B — DONE.** Exposure identity is `(sourceId, url)` everywhere
  (`RecommendationDisplayReranker.ExposureKey`); the three positive-interaction exemptions are separate,
  independently tested sets (`InteractionSignals`); the tracker exemption is a real batched
  `GetTracks.await(ids)` call that fails open; "Clear repeat history" is a settings row behind a
  confirmation dialog with success/failure/cancellation/no-Action-History tests.
- **Domain C — DONE.** All supported thresholds (0/5/10/20/50) proven through the real merge pipeline,
  plus unknown-count fail-open at every threshold and Latest-lane parity. Fingerprint participation
  (`profileFingerprint(..., minChapterCount, ...)`) is source-verified only, since that function is private.
- **Domain D — BLOCKED_EXTERNAL, unchanged.** No Compose UI/screenshot test infrastructure exists in this
  repository. Rendered validation of the quick-access panel has not been performed and is not claimed.
  Closing it requires either a live-device pass or introducing that infrastructure as its own batch.
- **Remaining known gaps (not claimed done):** `BrowsePersonalRecommendationsScreenModel` still has no
  test harness anywhere in the repo, so `tryAdditiveLatestAugmentation`, `recordVisibleExposure`'s
  coroutine body, `buildExposureRerankContext`, and `resolveTrackedExposureKeys` are verified by source
  inspection plus the pure policies/merge-level tests they feed — not by direct tests. Latest-lane
  cancellation and unsupported/failed-source outcomes are likewise source-verified via the shared
  `SourceRuntime` boundary. Building that harness is the natural next batch.
- **Validation:** 2545 tests / 0 failures / 0 errors / 1 skipped; `spotlessCheck` clean;
  `:app:assembleDebug` successful; `git diff --check` clean. **Working tree dirty: 376 changed paths**
  (baseline 375; the single delta is the new untracked
  `app/src/test/java/exh/recs/RecommendationLatestLaneCompositionTest.kt`, confirmed by diffing the
  baseline manifest against the final status). Nothing was committed, pushed, or cleaned.

## Latest catalogue + exposure packet — 2026-08-08 (batch L2: structural completion pass)

Full record: `private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`
("Batch L2" sections). This supersedes the batch-L1 status entry immediately below for the items it
closes; the L1 entry is left unchanged as historical record.

- **Domain A -- CLOSED.** The Latest lane's `RecommendationCatalogueFallbackPolicy.shouldAttempt` gate
  previously made Latest fallback-only (never ran once personalized results already existed). Fixed with
  a new `tryAdditiveLatestAugmentation()` in `BrowsePersonalRecommendationsScreenModel.kt`, sharing the
  existing rescue-lane budget pool, bounded per source by
  `RecommendationLatestBudgetPolicy.resolveAdditiveSlotsPerSource()` (new `MAX_ADDITIVE_SLOTS_PER_SOURCE
  = 2`, worst-case share 2/5 = 40% of a 5-row source, so personalized results remain the majority in
  every supported (displayLimit, percent) combination -- exhaustively tested). Reuses the full filter
  pipeline (`processRawCandidates`), excludes ids already present in the personalized set, tags itself
  under the existing `LATEST_CATALOGUE` provenance key (distinct from Popular), and fails open (returns
  empty) on any per-source exception while rethrowing `CancellationException`. 6 new tests in
  `RecommendationLatestBudgetPolicyTest.kt`. **Known gap:** no ScreenModel-level integration test exercises
  `tryAdditiveLatestAugmentation` end-to-end (only the pure budget policy is directly tested); the merge
  path it feeds into is covered indirectly via `RecommendationCandidateMemoryRankerTest`.
- **Domain B -- CLOSED.** `RecommendationExposurePolicy`/`RecommendationDisplayReranker` previously had
  zero production callers. Now wired end-to-end: new `recommendation_exposure` table (SQLDelight
  `.sq` + migration), `RecommendationExposureRepositoryImpl`, 4 interactors
  (`GetRecommendationExposure`/`RecordRecommendationExposure`/`PruneRecommendationExposure`/
  `ClearRecommendationExposure`), registered in `KMKDomainModule.kt`. Exposure is recorded exactly once
  per stable result generation via `BrowsePersonalRecommendationsScreenModel.recordVisibleExposure()`,
  gated by `RecommendationExposureCapturePolicy.shouldRecord()` (loaded, non-empty, fully-settled,
  not-yet-recorded generation only) and fired from a `LaunchedEffect(state.resultGeneration, allDone)` in
  `BrowsePersonalRecommendationsTab.kt` -- never on unrelated recomposition. Reranking runs inside
  `RecommendationCandidateMemoryRanker.merge()` **before** the final `.take(limit)` cap (the structural
  requirement -- reranking after a cap can never promote a less-exposed candidate into view), using a
  bounded soft penalty that never hides/excludes a candidate, never touches library/rated/tracked/
  interacted titles, and never overrides Not Interested/Dislike/blocked-tag/blocked-source hard
  exclusions. A new "Repeat title cooldown" settings row (7/14/30 day options, default 14) controls the
  window via `RecommendationExposurePolicy.SUPPORTED_WINDOW_DAYS`; pruning runs once per `load()`. Local
  storage only -- confirmed absent from the backup allowlist (`grep -rn "recommendation_exposure"
  app/src/main/java/eu/kanade/tachiyomi/data/backup/` returns nothing). 20 new tests (11
  `RecommendationExposureRepositoryTest` real-database tests, 9 `RecommendationExposureCapturePolicyTest`
  gating tests) plus 5 reranking-through-merge tests in `RecommendationCandidateMemoryRankerTest.kt`.
  **Known gap:** `recordVisibleExposure()`'s coroutine-launch body and the Tab-level `LaunchedEffect`
  itself have no automated test (no Compose UI test infra in this repo -- see Domain D); the exposure
  capture policy's pure gating logic is fully tested, but the wiring around it is not. A
  `clearExposureHistory()` interactor call exists in `RecommendationsSettingsScreenModel` but has no
  settings UI button yet -- deferred, not part of this pass's required scope.
- **Domain C -- CLOSED.** No defect was found in minimum-chapter-count filtering itself (preference read,
  validation, persistence, and candidate filtering were already correct), but the prior coverage was
  policy-unit-tests-only with no proof the persisted value changes real pipeline output. Added 4 new
  tests to `RecommendationCandidateMemoryRankerTest.kt` that construct a real preference store, persist a
  20-chapter threshold, and prove it hides/keeps candidates through the actual `merge()` production path
  (including a malformed-value-resolves-to-Off case and a threshold-change-alters-output case).
- **Domain D -- OPEN, explicit validation gap (not closed).** Confirmed via
  `grep -rn "createComposeRule|ComposeTestRule|paparazzi|Paparazzi|roborazzi|Roborazzi"` across
  `app/src/test`, `app/src/androidTest`, `build.gradle.kts`, and `libs.versions.toml` that this repository
  has no Compose UI or screenshot testing infrastructure at all. The quick-access panel's rendered
  layout (centering, dead space, wrapping, touch targets, scrim/back/navigation behavior) was **not**
  independently re-verified this pass -- it was previously addressed in Step 6 (see the batch-L1 entry
  below) but that was also not backed by rendered validation. This remains an open gap requiring either a
  live-device pass or new Compose UI test infra to close, not something a policy-level test can close.
- **Validation (batch L2):** `:data:compileDebugKotlin`/`:app:compileDebugKotlin`/
  `:app:compileDebugUnitTestKotlin` confirmed to actually re-execute (not merely up-to-date) after fixing
  a real `awaitList`/`Query<T>` compile bug in `RecommendationExposureRepositoryImpl.kt`. Full
  `:app:testDebugUnitTest`: **2517 tests, 0 failures, 0 errors, 1 skipped.** `spotlessCheck`: clean.
  `git diff --check`: clean (one CRLF-normalization warning only, not an error). **Working tree is
  dirty** (~375 changed paths at last check) -- this pass did not commit, push, or clean anything, per
  its explicit constraints.

## Latest catalogue + exposure packet — 2026-08-08 (batch L1 partially done)

Owning packet: `KMK_RECS_FUTURE_LATEST_CATALOGUE_QUALITY_IMPLEMENTATION_PACKET_2026-08-08.md`.
Full record: `private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`.

- **DONE (batch L1):** Step 5A minimum-chapter conformance repair; Step 1 pure policies; Step 3
  bounded Latest discovery lane; the Latest half of Step 5 (the "Explore new releases" setting);
  Step 6 quick-access panel layout repair. See `CURRENT_STATE.md`'s "Latest Catalogue Discovery Lane"
  section. Validated: spotlessCheck clean, 2482 unit tests / 0 failures, `:app:assembleDebug`
  successful, `git diff --check` clean.
- **OPEN — Step 2: local exposure persistence.** Not started. Needs
  `data/src/main/sqldelight/tachiyomi/data/recommendation_exposure.sq` + migration `64.sqm` (63 is
  the highest existing; 64 was still free as of 2026-08-08 and must be re-verified at start), the
  domain model/repository, `RecommendationExposureRepositoryImpl`, get/record/prune/clear
  interactors, and `KMKDomainModule` registration. Local-only, excluded from backup/sync/export, no
  Action History entry.
- **OPEN — Step 4: exposure capture + soft reranking integration.** Not started, blocked by Step 2.
  `RecommendationExposurePolicy` and `RecommendationDisplayReranker` are implemented and fully
  unit-tested but are **called by no production code**. Needs: exposure recording from
  `BrowsePersonalRecommendationsTab.kt` keyed on a stable result generation (never on recomposition),
  a single per-refresh exposure-summary load, and the reranker call placed after hard visibility /
  scoring / dedup but before the per-source row cap and Top Picks accumulation.
- **OPEN — exposure-window setting.** `recommendationExposureWindowDays()` exists in
  `SourcePreferences` (so the 14-day default has one owner) but has **no settings UI row on purpose**
  — a visible control that cannot affect anything would be dishonest. It belongs to the batch that
  lands Steps 2 and 4.
- **Not claimed:** no device/emulator verification, no APK installation, no screenshot evidence, and
  no control-plane state promotion were performed in batch L1 (the control plane remains terminal
  `BLOCKED` for device/publication gates and the source worktree is dirty).

## KMK Post-Screenshot UI and Not Interested plan — 2026-08-07 (Batch E/E2 done, B/C/F open)

## Future recommendation-quality proposal - 2026-08-08

- **Latest catalogue discovery and novelty quality -- OPEN, NOT IMPLEMENTED.** Record and assess
  each source's Latest catalogue as a bounded discovery lane alongside personalized/search and
  Popular inputs. The future pass must preserve minimum chapter count, blocked-tag, language,
  known/seen/rated, source-fit, metadata-confidence, deduplication, cancellation, and privacy
  safeguards. It must use deterministic local fixtures and must not assume every source exposes a
  reliable Latest route. Full structural plan:
  `docs/recommendations/KMK_RECS_FUTURE_LATEST_CATALOGUE_QUALITY_AND_NOVELTY_PLAN_2026-08-08.md`.
  No implementation, ranking change, APK work, or device validation was performed for this item.
  Clarified decisions: ignored cards are exposure only, not negative feedback; Latest must obey all
  existing eligibility filters; personalized results remain dominant; exploration is bounded and
  deterministic first; the default exposure window is 14 days; and repeated untouched cards are
  softly shifted downward rather than removed. A visible loaded card counts as exposed, while a
  fetched-but-never-visible page does not. The For You quick-access panel also has a separate OPEN
  visual audit item for centering, balance, spacing, and hit-target quality.
  Repository-specific implementation packet:
  `docs/recommendations/KMK_RECS_FUTURE_LATEST_CATALOGUE_QUALITY_IMPLEMENTATION_PACKET_2026-08-08.md`.
  The packet is `decision-gated`; no implementation is authorized yet. It now explicitly includes
  settings-copy/format conformance and hardening the existing minimum-chapter setting: current
  filter behavior is tested for normal values, but setter/read sanitization and malformed-value
  coverage still need to be proven.

Governing plan: `private/docs/plans/KMK_POST_SCREENSHOT_UI_AND_NOT_INTERESTED_IMPLEMENTATION_PLAN_2026-08-07.md`.
Full detail: `private/docs/audits-and-reports/KMK_POST_SCREENSHOT_UI_AND_NOT_INTERESTED_LEDGER_2026-08-07.md`.

- **Batch E (Not Interested first-class state) -- DONE.** See the "Not Interested (formerly 'Seen')
  Manga Marker" section of `CURRENT_STATE.md` above for the behavior change. Detail-page journaling
  gap closed, primary-button presentation now reflects Not Interested. 14 new tests, all passing.
- **Batch E2 (Not Interested as a structural peer of Love/Like/Dislike) -- DONE, second pass.** New
  shared `exh.recs.loved.MangaPreferenceAction` dispatch model; `NotInterestedPresentationPolicy`
  renamed to `MangaPreferencePresentationPolicy` and given `dropdownActions()`/`labelFor()`/
  `notInterestedToggleLabel()`; the detail-page dropdown now renders all four actions from one shared
  data-driven list instead of a bolted-on standalone item; selecting a rating while Not Interested is
  active now atomically clears it and writes the rating through one combined Action History entry with
  full undo support. See the "Not Interested (formerly 'Seen') Manga Marker" section of
  `CURRENT_STATE.md` for the full behavior change and the ledger's "Batch E2 result" entry for the
  test/validation record (23 new/updated tests, full 2392-test suite clean).
- **Batch D (reader schedule discoverability) -- `CLOSED_AS_ALREADY_IMPLEMENTED`.** Audited, already
  fully correct, no source change.
- **Batch A (architecture reconciliation audit) -- done** for the areas Batch D/E cover; the reader
  completion prompt (Batch C) and settings sectioning (Batch B) were only partially audited.
- **Batch C (reader completion prompt hierarchy) -- open.** Audit found the dialog is already mostly
  compliant (48dp targets, explicit Cancel, correct OK/Cancel write behavior already tested). One
  confirmed remaining gap: in `ReaderActivity.kt`'s `ChapterCompletionRatingGroupOffer` dialog, the OK
  and Cancel buttons are visually identical (`TextButton`, same size/color) with no dialog-state
  -transition test coverage yet.
- **Batch B (recommendation settings sectioning) -- not yet audited.** `RecommendationSettingsIndexScreen.kt`
  is 5 flat destination rows with no internal section headers; whether the 6 named areas' *detail*
  screens need additional internal sectioning was not checked this pass.
- **Batch F (cross-route consistency + release notes) -- not started.**

## Correction-pass status — 2026-07-25

The previously listed Phase 1/2 gaps for chapter history, manga-detail read actions, source exclusion, source-order reset, same-manga/best-version controls, composite source preferences, and direct Source Evaluation quality marks were closed in the correction pass. Their older entries below are historical audit notes and should not be treated as active blockers.

Active follow-up remains limited to the explicitly bounded items: wiring additional read/bookmark surfaces where the product wants them, designing inline For You Undo feedback, and evaluating recovery UX for external effects such as downloads, extension packages, backup restore, tracker writes, and migrations. No automatic rollback is claimed for those external effects.

The live observation on 2026-07-25 also confirmed a coverage distinction: manga-detail rating/clear-rating writes in MangaScreenModel.setMangaTaste() and clearMangaTaste() are not currently journaled, so they do not appear in Action History. Migration remains intentionally excluded because it can include remote tracker writes. See private/docs/audits-and-reports/KMK_EVALUATION_MODE_UNDO_LIVE_OBSERVATION_2026-07-25.md for the exact code paths and future requirements.

**Update, KMK-Recs v0.8.20-fix1:** the manga-detail journaling gap above is closed -- `setMangaTaste()`/`clearMangaTaste()` now use the same build-before-write/commit-after-success journal contract as every other rating surface. Migration is still not undoable (that remains correct -- it can include remote tracker writes with no safe automatic reversal), but a completed migration and a confirmed extension install are now at least visible in Action History as clearly-labeled, non-undoable events via the new `NonUndoableEventJournal`. Extension uninstall is not yet represented there (no verified-completion signal exists for it in the current code) -- see private `private/docs/audits-and-reports/KMK_FIX_PASS_V0_8_20_FIX1_REPORT.md` for the exact disposition.
# KMK-Recs v0.8.20-fix3 completion

Completed: reader schedule deletion confirmation, Evaluation Mode redaction of the For You sources
summary, and read-only per-source metadata/tag coverage diagnostics backed by existing Source
Evaluation rows. The diagnostics intentionally report observed counts and confidence only; they do
not invent source-specific filter semantics or treat missing metadata as negative evidence.

# KMK-Recs v0.8.20-fix2 correction status

Implemented and validated. Source Evaluation completion now derives its workable outdated count from
the same actionable queue and explicit-source option used by the reassess action. The shared
Evaluation Mode install recorder now covers Browse > Extensions, Sources To Try, recommendation
bundle import, and direct Source Evaluation installation. Temporary evaluation/probe installs are
not recorded, and extension uninstall remains intentionally unsupported because the current API
does not expose a verified completion signal. See the private
`private/docs/audits-and-reports/KMK_FIX_PASS_V0_8_20_FIX2_REPORT.md` for exact symbols, tests, and limitations.

# KMK Personal Recommendations Next Work

## Evaluation Mode Undo Expansion, 2026-07-25 (Phases 1/3/4/5 complete, Phase 2 partial)

See `CURRENT_STATE.md`'s latest entry and the private
`private/docs/audits-and-reports/KMK_EVALUATION_MODE_UNDO_COVERAGE_AUDIT_2026-07-25.md` / `private/docs/plans/KMK_EVALUATION_MODE_UNDO_MASTER_IMPLEMENTATION_PLAN_2026-07-25.md`
for full detail. Remaining work, in priority order:

- **Finish Phase 2 chapter-state wiring.** `ChapterUndoJournal`/`ChapterUndoService`/`ChapterUndoRecorder`
  are complete and tested; only `MangaScreenModel.bookmarkChapters()` is wired. Still needed:
  `MangaScreenModel.markChaptersRead()` (keep the tracker-prompt branch entirely outside the Undo
  restore contract), `LibraryScreenModel.markReadSelection()` (apply the 500-chapter bound per manga),
  `UpdatesScreenModel.markUpdatesRead()`/`bookmarkUpdates()`, and the reader-completion read-state batch
  in `ReaderViewModel.kt` (explicitly excluding `updateChapterProgress()`/`lastPageRead`, per the plan).
- **Wire the For You source-exclusion toggle** (`RecommendationsSettingsScreenModel.toggleSource()`) —
  it's backed by `recommendation_disabled_source` via `SetSourceEnabled`, not a `Preference<T>`, so it
  needs a small dedicated `PreferenceUndoEntry`-shaped wrapper (read/restore lambdas around the
  interactor) rather than `PreferenceUndoRecorder.buildPreferenceEntry`'s generic path.
  `moveMangaToCategoriesAndAddToLibrary`'s bulk-add-with-categories path (`MangaScreenModel.kt` line
  ~1021) also still needs its own explicit test.
- **`UpdateChapter`/`SetMangaCategories` silently swallow write exceptions** (pre-existing, shared by
  every write path in the app, confirmed during this pass via `ChapterUndoServiceRestoreTest`'s
  documented-limitation test). A persistence failure during Undo restore is currently indistinguishable
  from success. Fixing this needs an app-wide interactor contract change (return `Boolean`/rethrow),
  out of scope for a bounded Undo pass — track separately if it becomes a real correctness concern.
- **Chapter-journal entries are not yet shown in `EvaluationModeActionHistoryScreen`.** The history
  screen was unified across taste/group/library/preference journals this pass; the chapter journal
  (Phase 2) needs the same `HistoryRow` treatment once its remaining call sites are wired.
- **For You still has no inline Undo** for any of the newly-added journals either (same structural
  `BrowseTab` Scaffold limitation already tracked from the prior pass) — all new Phase 1/2/3 actions
  taken from For You specifically (there are few today) remain history-screen-only.

## Group-action Undo Journal (merge/remove/ungroup), 2026-07-24 (implemented and verified)

See `CURRENT_STATE.md`'s latest entry and `docs/community/KMK_EVALUATION_MODE_UNDO_JOURNAL.md`'s
"Group-action Undo Journal" section for full detail. Remaining open items:

- **Live-device validation not performed (required before evidence routes can be unblocked).** No ADB
  actions, screenshot capture, or device installation-for-testing occurred as part of implementing this
  feature, by explicit scope. Before `private/docs/evidence-and-qa/KMK_FEATURE_EVIDENCE_PROGRESS.md`'s blocked grouping
  routes (merge, remove-from-group, ungroup) can be marked complete, a separate pass must: install the
  build, perform a real merge/remove/ungroup on-device with Evaluation Mode on, confirm the Snackbar
  Undo action actually restores the grouping, and confirm the conflict path (re-group something, then
  try an unrelated Undo) is refused correctly on a live database, not just the in-memory fakes.
- **"Not the same manga"/disassociation does not exist as a feature.** Confirmed via a grep across
  `LovedMangaScreenModel`/`RatedMangaScreen`/the whole `exh` package — there is no such action anywhere
  in the codebase. If this is ever added as a real feature, it needs its own typed snapshot (likely
  similar in shape to `REMOVE_FROM_GROUP`) before it can be journaled; nothing was invented to satisfy
  the evidence checklist item, and the checklist should instead treat "conflict handling for entries
  already in different groups" as already covered by `RatedGroupMergePlanner`'s existing multi-group-fold
  merge behavior.
- Grouping/ungrouping restore for "For You" itself is out of scope — group actions only exist on the
  rated collection screens (`RatedMangaScreen`/`LovedMangaScreenModel`), not in `BrowsePersonalRecommendationsTab`.
- If a future pass adds more group-shaped mutations (e.g. a real "not the same manga" split), extend
  `GroupJournalActionType`/`GroupUndoRecorder` rather than inventing a third parallel journal.

## Evaluation Mode Action Undo Journal code-review follow-up, 2026-07-23 (implemented and verified)

See `CURRENT_STATE.md`'s latest entry and `docs/community/KMK_EVALUATION_MODE_UNDO_JOURNAL.md` for full
detail on the 5 code-review issues found and the 4 fixed same day. Remaining open items:

- **For You still has no inline Undo (not deferred — a required next step, see below).** Actions are
  still journaled and undo-able via `EvaluationModeActionHistoryScreen`, so this is a UX-surface gap, not
  a missing safety net, but it is real and should be closed:
  - Add a `SnackbarHostState` to `BrowseTab`'s shared `Scaffold` (affects every Browse tab — needs its
    own verification pass across all tabs, not just For You), **or**
  - Give `BrowsePersonalRecommendationsTab` its own local Snackbar surface layered above its content
    (mirrors the existing `showFabMenu`-style overlay pattern already in that file).
  - Either way, wire `showBulkActionFeedback()` to show a Snackbar with an Undo action calling
    `EvaluationModeUndoService`/the same restore path Loved/Liked/Disliked already use, when Evaluation
    Mode is enabled (outside Evaluation Mode, For You bulk actions currently have no Undo path at all
    since the journal itself is Evaluation-Mode-gated — decide whether For You's normal-mode Undo should
    reuse a separate always-on mechanism or stay Evaluation-Mode-only before implementing).
- Grouping/ungrouping/merging cross-source manga links has no safe typed inverse yet — needs a real
  design pass (snapshotting a link-group graph correctly) before it could be added to the journal.
- Device QA not performed this pass (either the initial implementation or this follow-up): history screen
  layout (phone/tablet/dark/light/large-font), the Settings nav row's visibility toggle, a live on-device
  undo of an actual rating change, and confirming the screen actually pops when Evaluation Mode is
  disabled while it's open.
- If Merge/Remove-from-group/Ungroup ever gain their own safe undo mechanism in a future pass, consider
  whether it should reuse `EvaluationModeUndoJournal`'s entry/eviction model rather than inventing a
  second one.

## Bulk-action result feedback, 2026-07-23 (implemented and verified)

See `CURRENT_STATE.md`'s latest entry and
`docs/community/KMK_RECS_INTERACTION_FUNCTIONALITY_AUDIT.md`'s two new table rows for full detail.
Remaining open items, **deferred, not part of this pass**:

- For You bulk actions have no Undo — needs a `SnackbarHostState` wired into
  `BrowsePersonalRecommendationsTab`'s Scaffold (a real structural change, not attempted this pass).
- Loved/Liked/Disliked's bulk rating-set actions (if distinct from Clear/Not-interested on that screen)
  were not audited for the same false-success bug class this pass fixed for Clear Rating/Not
  Interested — worth a follow-up sweep.
- Merge/Remove-from-group/Ungroup message polish remains deferred (already tracked before this pass).
- Source-selection bulk actions (Sources To Try install) already have real feedback from prior passes;
  not re-touched.
- No device QA performed this pass (message wording, Snackbar placement/accessibility, dark/light/
  tablet/large-font layout) — the underlying Snackbar/toast components themselves are unchanged from
  the existing, previously-verified `SnackbarHostState`/`toast()` usage, but the new *text* itself has
  not been visually confirmed on a device.

## Current Komikku-standard alignment pass, 2026-07-23 (implemented and verified)

Plan: `docs/community/KMK_CURRENT_KOMIKKU_STANDARD_ALIGNMENT_IMPLEMENTATION_PLAN_2026-07-23.md`.
Resolved both P1 items from `KMK_PRIVATE_CONVENTION_CORRECTION_PASS_REVIEW_2026-07-23.md`: atomic
extension-error replacement (`ReplaceSourceEvaluation`/`replaceByPackage()`) and persistence-contract
test coverage (`ReplaceSourceEvaluationTest.kt`, 7 tests against an in-memory fake repository). See
`CURRENT_STATE.md`'s latest entry for full detail. The following were limitations of that initial pass;
the follow-up correction section below records their resolution:

- An exhaustive line-by-line classification of every one of the ~85 files the repo-wide grep sweep
  matched — a representative sample (named plan files + migration/update/debug/tracking callers) was
  checked, not every file individually narrated.
- A real SQLDelight in-memory test harness for `SourceEvaluationRepository` — the new persistence test
  uses an in-memory fake at the interface boundary, not a real database, per this pass's disclosed
  scope limit.
- Everything already deferred by the prior correction pass (Evaluation Mode matrix's 4 open rows,
  oversized-file splits, clipboard-copy workflow-risk doc edit) — unchanged, not touched this pass.

## Follow-up correction to the 2026-07-23 alignment pass (implemented and verified)

The previously disclosed gaps are now closed. The active source-runtime inventory is classified in
`docs/community/KMK_SOURCE_RUNTIME_EXHAUSTIVE_INVENTORY_2026-07-23.md`, and
`app/src/test/java/tachiyomi/data/taste/SourceEvaluationRepositoryTransactionTest.kt` exercises the
generated SQLDelight database with an in-memory SQLite driver. Three additional bypasses were fixed in
Library MangaDex sync, MangaDex settings logout, and the MangaDex OAuth activity; downloader data-saver
image fetching now uses `SourceRuntimeOperation.Image`. Device-only validation remains separate QA.

## Private convention correction pass, 2026-07-22 (implemented and verified)

Plan: `docs/community/KMK_PRIVATE_CONVENTION_CORRECTION_PASS_2026-07-22.md`. Findings/status:
`docs/community/KMK_KOMIKKU_CONVENTION_COMPLIANCE_AUDIT_2026-07-22.md`. Closed one confirmed
`SourceRuntime` bypass (`MdList.kt`, MangaDex tracker methods), extracted and regression-tested
`SourceEvaluationRunner`'s delete-before-upsert sequencing, and confirmed resource/settings-search
compliance with no defect found. Remaining open items, **deferred, not part of this pass**:

- A full repo-wide `SourceRuntime` bypass sweep beyond the plan's named file list (migration/update/
  debug callers not exhaustively covered).
- Reader-related source labels, merged-manga-detail downstream string, recommendation-bundle schema,
  and crash-log content for Evaluation Mode (`KMK_EVALUATION_MODE_VERIFICATION_MATRIX.md`'s 4 open rows).
- The repo-URL clipboard-copy workflow-risk documentation edit to
  `KMK_CLAUDE_ADB_AND_SCREENSHOT_WORKFLOW_POLICY.md` (identified, not yet written).
- Splitting the four oversized files identified across both audit passes (`SourceEvaluationScreen.kt`,
  `SourceEvaluationScreenModel.kt`, `BrowsePersonalRecommendationsScreenModel.kt`,
  `RecommendationSettingsSharedComponents.kt`) — explicitly out of scope for this correction pass per
  its own "do not make broad changes merely to reduce file size" constraint.

## v0.8.19 Evaluation Mode (implemented; pending device verification)

See `docs/recommendations/CURRENT_STATE.md`'s latest entry and
`docs/community/KMK_EVALUATION_MODE_VERIFICATION_MATRIX.md` for full detail. No device screenshots have
been captured for this release; treat as pending device verification until a human confirms on-device.

## v0.8.18 consolidated implementation (complete)

Plan: `docs/community/KMK_RECS_V0_8_18_CONSOLIDATED_IMPLEMENTATION_PLAN.md` (source material:
`docs/community/KMK_RECS_V0_8_18_ROLLING_FOLLOWUP_PLAN.md`,
`docs/community/KMK_RECS_V0_8_17_FIX2_BEST_VERSION_ORIGIN_UNAVAILABLE_AND_READER_PREVIEW_PLAN.md`).
Implementation report: `docs/community/KMK_RECS_V0_8_18_CONSOLIDATED_IMPLEMENTATION.md`.

**All six phases (A-F) complete**, closing the fix2 plan's scope as part of this consolidated pass
(fix2 was never implemented standalone -- v0.8.18 explicitly supersedes it, per the plan's own "this
file is the authoritative handoff plan" note).

- **Phase A**: removed `SourceEvaluationScreen`'s redundant top-right "Sources to try" app-bar
  shortcut (now covered by the Recommendation Settings quick-access row already on that screen since
  v0.8.17-fix1). No Home/For You replacement action was added -- no safe route exists without either
  popping an unknown depth of the back stack or faking tab navigation; documented as a deliberate
  non-fix rather than invented.
- **Phase B**: `BestVersionCompareScreenModel.State` gained `compareCandidates` (origin + selected
  real candidates, origin always first) and `originKey`; origin's chapter is derived directly from
  already-fetched local `originChapters`, never searched through extensions. New
  `keepCurrentVersion()` finalizes immediately when origin is selected as best -- no
  `migrateMangaUseCase` call, no migrate/copy dialog (that dialog's lookup stays scoped to
  `selectedCandidates`, which never includes origin, so it can never accidentally trigger).
  `CandidatePreviewState` gained `Skipped` for chapters already known `Unavailable` before preview
  starts -- they're marked immediately, never sent to page-list fetching, never rendered as an
  indefinite spinner. 9 new tests (`BestVersionOriginAndUnavailablePreviewTest`).
- **Phase C**: new pure `BestVersionReaderPreviewPolicy` maps `ReaderPreferences.defaultReadingMode()`/
  `webtoonSidePadding()` to a side-padding decision applied only in the full-screen candidate compare
  dialog when the user's own default reading mode is webtoon-style. Reads only these preference
  *values* -- no `ReaderActivity`/`ReaderViewModel` embedding, no reading history, mark-read, timers,
  chapter transitions, preloading, Discord, or reader menus. 4 new tests
  (`BestVersionReaderPreviewPolicyTest`).
- **Phase D**: `MangaActionRow`'s WebView/Merge/Find best version buttons moved into one "More"
  overflow menu, decluttering the compact action row down to Library/Interval/Tracking/Rate/More.
  Find best version stays out of the Rate dropdown (unchanged from v0.8.16), just moved into More
  instead of staying a fifth equal-weight primary button.
- **Phase E**: new `ExtensionApkExporter` copies raw installed-extension APK/archive bytes unchanged
  (never repackaged/re-signed) via SAF. Single export from Extension Details' overflow menu; multi
  export from the Extensions page's existing selection mode as one zip (APKs + non-sensitive
  `manifest.json`, no cookies/credentials/preferences/ratings/history/backups). Confirmation dialog
  warns extensions are executable code before every export. 3 new tests (`ExtensionApkExporterTest`).
- **Phase F**: new `RecommendationSettingsEdgeQuickAccessPanel` -- a right-edge tap-to-open panel
  (28dp handle, narrow enough to stay clear of Android's own edge-swipe-back gesture) sharing the same
  `RecommendationSettingsQuickAccessDestination` registry `RecommendationSettingsQuickAccessRow`
  already uses. Added to all five Recommendation Settings detail screens alongside the existing row
  (not a replacement -- kept per the plan's "do not remove it blindly"). Swipe-to-open was judged
  unreliable/risky alongside the system back gesture in this pass; documented as a real follow-up,
  tap-to-open shipped instead.

`KmkRecsReleaseNotes` bumped `774`/`"v0.8.17-fix1"` → `775`/`"v0.8.18"`. App version intentionally
unchanged (`1.14.1`/`90`) -- no phase required an Android app-version bump. Final APK:
`Komikku-v1.14.1-kmk.8.18-debug.apk`.

## Post-v0.8.18 evaluation / next-fix queue (complete -- v0.8.18-fix1)

**Implemented**: `docs/community/KMK_RECS_V0_8_18_FIX1_PRIVATE_NEXT_WORK_IMPLEMENTATION_PLAN.md` (plan) /
`docs/community/KMK_RECS_V0_8_18_FIX1_PRIVATE_NEXT_WORK_IMPLEMENTATION.md` (implementation report,
2026-07-20) closed all 5 in-scope items below: numeric-settings audit (no behavior change), extension
export IO dispatcher, WebView copy-link restoration, quick-access panel relocation to For You/Loved/
Liked/Disliked, and Source Evaluation's "Go to For You" app-bar action. It is private (not for public/
community posting) -- the separate, sanitized `docs/community/KMK_SOURCE_EVALUATION_PUBLIC_FEATURE_BRIEF.md`
is the Track A public/upstream companion and stays free of this private plan's implementation detail.

The items below are retained as historical record of what was found/clarified after v0.8.18 and what
this fix closed. Do not treat this section as active future work.

## v0.8.18-fix1 validation and evidence workflow (complete -- 2026-07-20)

Closed items 0-3 from the prior "Active next step" queue (retained below, historical):

- **Item 0 (APK naming)**: corrected. APK renamed `Komikku-v1.14.1-kmk.8.18.1-debug.apk` →
  `Komikku-v1.14.1-kmk.8.18-fix1-debug.apk`, matching this project's established fix-release convention;
  all doc references updated.
- **Item 1 (live-device QA)**: performed. See
  `docs/community/KMK_RECS_V0_8_18_FIX1_DEVICE_QA.md` for the full per-item results. **Found and fixed a
  real regression during this pass**: Loved/Liked/Disliked crashed on every open
  (`IllegalStateException: TabNavigator not initialized`), root-caused to two screens incorrectly reading
  `LocalTabNavigator.current` from outside the `TabNavigator`'s composition scope. Fixed via a new
  `HomeScreen.Tab.Browse(toForYou = true)` + `HomeScreen.openTab(...)` path (mirroring the existing
  `showExtension()` pattern). Also fixed a mislabeled quick-access tile ("For You sources" instead of
  "For You") that was very likely the actual source of the "quick access part should lead to the
  settings" report. Full root-cause/fix detail in the "Post-Implementation Correction" section of
  `docs/community/KMK_RECS_V0_8_18_FIX1_PRIVATE_NEXT_WORK_IMPLEMENTATION.md`. Re-verified: compile,
  spotless, full test suite, and live-device retest all pass. A short list of narrower items (merged-manga
  copy-link, multi-extension export SAF flow, Disliked-specific screenshot, hidden-For-You-tab state) were
  not independently exercised and remain listed as manual QA in the device QA report.
- **Item 2 (screenshot/evidence workflow)**: built.
  `docs/community/KMK_SOURCE_EVALUATION_SCREENSHOT_EVIDENCE_WORKFLOW.md` (guide) +
  `scripts/kmk_capture_source_fit_evidence.ps1` (capture script) implement the raw-local/sanitized-public
  split with a fixed `docs/community/evidence/{raw,sanitized}/` folder layout. No automatic redaction is
  implemented (deliberately -- a script can't reliably tell a private name from ordinary UI chrome).
- **Item 3 (public brief finalization)**: reviewed.
  `docs/community/KMK_SOURCE_EVALUATION_PUBLIC_FEATURE_BRIEF.md`'s text content is judged ready for
  public/community use as written (no private KMK details, no private source/repo names). It is not yet
  actually posted anywhere -- no evidence screenshots have been captured/attached yet, which remains the
  one blocking step.

Item 4 (deeper For You scoring, broader UI standardization, actual PR extraction) remains future work, not
part of this validation pass -- unchanged from before.

1. **Numeric Recommendation Settings should reuse Komikku's slider preference pattern.**
   - User clarification: this is part of the broader "standardize the structure" expectation, not a one-off
     visual polish item.
   - Official reference: `SettingsDownloadScreen.kt` uses
     `Preference.PreferenceItem.SliderPreference` for `pref_download_concurrent_sources` and
     `pref_download_concurrent_pages`, rendered through `PreferenceItem.kt` / `BaseSliderItem`.
   - KMK target area: `RecommendationDiagnosticsSettingsScreen.kt` currently renders numeric tuning rows
     such as `enrichment_cap` through custom option-row UI (`SameMangaListPrefRow`). Audit all KMK numeric
     Recommendation Settings controls, especially Management and diagnostics, and convert suitable numeric
     controls to the official slider preference style or a thin shared KMK wrapper that visually and
     behaviorally matches it.
   - Candidate controls to audit include, at minimum: enrichment cap, group preview budget, For You result
     budget / results per source, minimum chapter count, Best Version preview pages, and any other bounded
     integer setting exposed in Recommendation Settings.
   - Preserve existing preference keys, defaults, bounds, summaries, search entries, and behavior. This is a
     UI/interaction standardization change, not a schema migration.
   - If a numeric setting has sparse/non-linear options where a slider would be misleading, document why and
     keep a list/dialog-style control; do not force every number into a slider blindly.

2. **Extension export should run file-copy work on an IO dispatcher.**
   - `ExtensionApkExporter.exportSingle(...)` and `exportMultiple(...)` are `suspend`, but the actual
     `copyTo(...)` calls are not wrapped in `withContext(Dispatchers.IO)`. They are launched from Compose
     coroutine scopes, so large single/multi-extension export can freeze the UI.
   - Keep the existing confirmation dialogs and SAF flow; move only the blocking file work to IO and add a
     focused regression/proof test where practical.

3. **Manga detail WebView long-press/copy-link path became dead wiring.**
   - `MangaActionRow` still accepts `onWebViewLongClicked`, and the manga screen still supplies copy-link
     behavior, but the v0.8.18 More menu never invokes it.
   - Either restore the behavior as an explicit `Copy link` overflow item, or remove the unused callback chain
     cleanly if the feature is intentionally retired. Do not leave a callback that is passed through several
     layers but unreachable.

4. **Recommendation quick-access side panel is on the wrong surfaces.**
   - v0.8.18 added the right-edge quick-access panel only to Recommendation Settings detail screens. User
     correction: this is not the intended placement.
   - Remove the side panel from Recommendation Settings detail screens. The settings screens already have
     normal Recommendation Settings navigation and should not carry this panel.
   - Implement the side panel only on user-facing browsing/collection surfaces: the For You page and the
     Loved/Liked/Disliked rated manga pages.
   - The panel should provide quick access between the For You and rated manga surfaces, not between settings
     sections. Do not confuse this with settings navigation.
   - Next fix should either implement a safe drag-open gesture with normal touch targets and accessibility, or
     explicitly confirm a tap handle is the accepted final behavior if a true swipe/pull would conflict with
     Android back gestures.

5. **Source Evaluation app-bar Home/For You replacement remains unresolved.**
   - v0.8.18 removed the redundant Sources to try shortcut but did not add a Home/For You button because no
     safe existing navigation route was found.
   - Next fix should re-evaluate whether a safe route now exists. If not, keep it documented as a non-fix.

Source: user live-device review after v0.8.17-fix1 and later v0.8.17-fix2 deferral. Treat the old
v0.8.17-fix2 Best Version plan as source material only; do not implement it as a separate build unless
the user explicitly reactivates it. The next implementation should be consolidated under v0.8.18 after
the user finishes adding items.

Required items:

1. **Do not preview unavailable chapters.**
   - In Find Best Version, sources that already show the selected chapter as unavailable in the chapter
     selection step must not be sent into page-preview loading.
   - They must not become endless spinners in the preview step.
   - The UI should either skip them with a clear explanation or show a terminal "chapter unavailable" row,
     not a loading state.

2. **Include the origin/current manga as the baseline comparison candidate.**
   - Current code explicitly filters the origin manga out of selected candidates and blocks toggling it.
   - The user expects the manga being used to find the best version to appear in the comparison so its
     current source/quality can be judged against other versions.
   - Selecting the origin as best must be a safe "keep current version" path, not a migration-to-self.

3. **Assess and improve reader-consistent full-screen preview.**
   - Best Version preview currently uses a Compose `SubcomposeAsyncImage`/`PagePreviewFetcher` path, while
     Komikku's real reader uses `ReaderPageImageView`, subsampling views, crop-border behavior, and separate
     pager/webtoon preferences.
   - Claude must assess whether full reader display reuse is feasible before coding. If it is too risky,
     implement the safe reader-informed subset and document the limitation.
   - The goal is a full-screen, read-only comparison view that respects existing reader presentation rules
     where safe, especially crop borders, webtoon/strip display, and fair fit/scroll behavior.

4. **Settings visual/operational assessment.**
   - Record what KMK can learn from official reader/settings structures for preview controls and future
     settings cleanup.
   - Do not redesign all settings in this fix; document the relevant standard and apply only what is needed
     for Best Version preview.

5. **Source Evaluation app-bar action cleanup.**
   - Current code in `SourceEvaluationScreen.kt` still has a top-right app-bar shortcut to
     `RecommendationNonInstalledDiscoverySettingsScreen` ("Sources to try").
   - That shortcut is now redundant because `RecommendationSettingsQuickAccessRow` already provides
     lateral access to Sources to try and the other Recommendation Settings destinations from the Source
     Evaluation page.
   - Remove the Sources to try-specific app-bar action and its now-misleading comments/string usage.
   - Prefer replacing it with a Home/For You action that returns the user to the actual For You page.
   - Important code constraint: For You is currently a tab inside `BrowseTab` (`personalRecommendationsTab()`),
     not a standalone settings/detail screen. Claude must inspect and use the existing tab/navigation
     structure (`BrowseTab`, `HomeScreen`, `LocalTabNavigator`, or an existing/open-tab helper) instead of
     guessing a fake screen route.
   - If there is no safe existing route to open Browse directly on the For You tab, add the smallest
     documented helper needed for that purpose, or leave the action removed and document why the Home
     replacement was deferred. Do not keep the redundant Sources to try shortcut.

6. **Manga detail action-row decluttering.**
   - The manga detail header currently places too many first-level actions in one equal-width row:
     Add/In library, interval/update review, Tracking, WebView, Merge, Rate/Seen/version-rating actions,
     and Find best version.
   - Code evidence: `MangaActionRow` in `MangaInfoHeader.kt` uses a single `Row`; each
     `MangaActionButton` uses `Modifier.weight(1f)`, so every new action compresses all actions.
   - Proposed structure: keep only the most common actions visible on compact layouts (Library,
     Tracking, Rate, plus a More action), and move secondary/contextual actions such as Find best
     version, WebView, Merge, favorite/rate other versions, and other management actions into a
     Komikku-style overflow menu or sheet.
   - Find best version must stay conceptually separate from Rate; it should move into More/secondary
     actions if needed, not back into the rating menu.
   - The fix should be implemented in the shared `MangaActionRow` component so both manga header layouts
     benefit consistently.

7. **Manual extension APK export.**
   - Add an explicit user-initiated option to export one installed extension or a selected set of installed
     extensions themselves.
   - This means exporting the local installed extension APK/archive bytes, not only source IDs, extension
     catalogue metadata, or recommendation bundles.
   - Single-extension export should be reachable from Extension Details.
   - Multi-extension export should reuse the existing Extensions page selection mode and export selected
     installed extensions as a single zip with a non-sensitive manifest.
   - Do not add automatic import/install, backup/sync inclusion, trust bypass, or hidden background export.
   - Export must warn that extensions are executable code; it must not include cookies, credentials, source
     preferences, ratings, recommendation data, reading history, or backups.
   - Missing/unreadable APK paths and unexportable selected entries must be reported as non-fatal failures,
     not crashes or silent skips.

8. **Right-edge Recommendation Settings quick access.**
   - Add a Samsung-style right-edge pull/slide quick-access panel for Recommendation Settings.
   - The panel should show the five Recommendation Settings destinations as compact app-like shortcut tiles:
     For You sources, Taste and filters, Source Evaluation, Sources to try, and Management and diagnostics.
   - It must reuse `RecommendationSettingsQuickAccessDestination` and the existing title/icon/navigation
     mapping instead of creating another hardcoded destination list.
   - It should be available at least on Recommendation Settings detail screens, including Source Evaluation,
     and preferably on For You/recommendation result screens if the existing navigation stack supports that
     safely.
   - It should feel like a quick launcher, not another row of text buttons. Use a subtle right-edge handle,
     right-side sheet/panel, normal touch targets, Komikku/Material colors, and accessible labels.
   - Do not break Android gesture navigation. If full swipe-to-open is too risky, implement a reliable
     tap-to-open right-edge handle first and document swipe-to-open as follow-up.
   - Decide from code/UI inspection whether the existing `RecommendationSettingsQuickAccessRow` should stay,
     be hidden on compact widths, or be replaced by the edge panel; do not remove it blindly.

## v0.8.17-fix1 Live-Device UI/Action and Best Version Preview (complete)

Plan: `docs/community/KMK_RECS_V0_8_17_FIX1_LIVE_DEVICE_UI_ACTION_AND_BEST_VERSION_PLAN.md`.
Implementation report: `docs/community/KMK_RECS_V0_8_17_FIX1_LIVE_DEVICE_UI_ACTION_AND_BEST_VERSION_IMPLEMENTATION.md`.

**All seven phases (A-G) complete.** Phase A: For You selection gained a working Clear Rating action,
real success/failure toast feedback for every bulk action (previously fire-and-forget with swallowed
errors), and a shared `ForYouActionButton` fixing the bottom bar's icon/label alignment. Phase B: rating
exactly one selected For You manga now offers a continuation into the existing
`CrossExtensionMatchScreen` rate-other-versions flow; the reader's chapter-completion rating prompt no
longer requires a pre-confirmed cross-source group to offer this continuation (it now always offers it,
with wording that only claims "confirmed other versions" when a group actually exists). Phase C: Source
Evaluation's `Details`/`Errors`/`Install` row actions now share one aligned composable
(`SourceEvaluationRowAction`), fixing a live-device-observed visual misalignment (layout-only, no
eligibility/behavior change). Phase D: a new `RecommendationSettingsQuickAccessRow` lets users jump
laterally between the five Recommendation Settings detail screens without backing out to the index.
Phase E: Best Version preview candidates now have a bounded 25s timeout (a hung source can no longer
freeze the whole comparison screen), a per-candidate Retry action, and the "N/N pages loaded" wording was
actually corrected to "Prepared N of N preview samples" (a prior pass's code comment had claimed this was
already fixed, but the string itself hadn't changed). Phase F: guardrail cross-check of For You/Source
Evaluation/Best Version/Settings plus Top Picks and Rated collections -- only Top Picks and Rated
collections needed no changes (already compliant); every other defect found was fixed in Phases A-E.
`KmkRecsReleaseNotes` bumped to `774`/`"v0.8.17-fix1"`. No recommendation-scoring changes, no upstream
Komikku reconciliation, no schema/proto/backup changes -- exactly as scoped. Final APK:
`Komikku-v1.14.1-kmk.8.17-fix1-debug.apk`.

## v0.8.17 Universal UI, For You quality, and Komikku 1.14.1 (complete)

Plan: `docs/community/KMK_RECS_V0_8_17_UNIVERSAL_UI_FOR_YOU_QUALITY_AND_1_14_1_PLAN.md`.
Implementation report: `docs/community/KMK_RECS_V0_8_17_UNIVERSAL_UI_FOR_YOU_QUALITY_AND_1_14_1_IMPLEMENTATION.md`.
Working-tree baseline: `docs/community/KMK_WORKING_TREE_HYGIENE_AND_BASELINE.md`.

**All four phases (A, B, C, D) are complete.** Phase B re-audited Sources to Try, Rated manga
collections, Group recommendations, and Best Version against the universal readability/interaction
standard -- all confirmed already compliant, no code changes needed. `RecommendationDiagnosticsSettingsScreen`
density was investigated and deliberately deferred: collapsing its sections by default would break
`ScrollToAnchorEffect`'s search-anchor scrolling (used by every Recommendation Settings screen) for any
control inside a collapsed section, since the anchor system assumes every anchorable row is always
present in the `LazyColumn`. A correct fix requires the anchor system itself to auto-expand containing
sections first -- a real, separate cross-cutting change, tracked as a follow-up rather than attempted as
a shallow per-screen tweak. Phase C added a tap-to-explain detail dialog to each source's status line in
For You sources (`SourcePriorityItem` in `RecommendationSettingsSharedComponents.kt`), driven by the new
`RecommendationSourceStatusExplanationPolicy` -- built entirely on the diagnostic distinction
(`RecommendationSourceStatus.NoMatches` vs `FilteredOut` vs the other five states) that already existed
and was already computed correctly in `BrowsePersonalRecommendationsScreenModel.finalEmptyOutcome()`; no
new pipeline instrumentation or scoring change. The always-visible compact status strings
(`rec_source_status_no_matches`/`rec_source_status_filtered`) were shortened; the fuller explanation moved
to new `rec_source_status_explain_*` strings shown only in the tap dialog. `KmkRecsReleaseNotes` bumped
to v0.8.17 (`VERSION_CODE = 773`) with a real What's New entry, since this pass now includes genuine
user-facing recommendation-feature changes (unlike the Phase-D-only pass, which intentionally did not
bump it). Phase D reconciled Komikku v1.14.1:
reconciled all 14 upstream `v1.14.0..v1.14.1` file changes: `AndroidSourceManager.kt`'s
`DELEGATED_SOURCES` restructured from a `Map` (keyed by qualified class name, with a separate
`factory`-flag prefix-match pass) to a `List` with inline exact/prefix matching (upstream PR #1797,
the real delegated-source-loading bug fix); Pururin moved package
(`online.english` → `online.all`) and source id (`PURURIN_SOURCE_ID` → `fillInSourceId`, matching
NHentai/MangaDex/LANraragi's pattern); `MangaDex`'s `factory` flag removed and its qualified-class-name
entry corrected to the exact class instead of a package prefix; `NHentai`/`Lanraragi` dropped a
redundant `lang` override already provided by `DelegatedHttpSource`; two `/repo.json`-suffix-duplication
bugs fixed (`ExtensionStoreRestorer.kt`, `TrustExtensionRepositoryMigration.kt`); a new
`ChapterUrlHashMigration` and a newly-added `DisabledRepoMigration` (present upstream since its own
versionCode 80, but never carried over during the original 1.14.0 reconciliation -- added now, at KMK's
own next versionCode `90` rather than upstream's original number, since `Migrator` only runs a migration
whose `version` falls in the exact `(oldVersionCode+1)..newVersionCode` range being upgraded through).
App `versionName`/`versionCode` bumped `1.14.0`/`89` → `1.14.1`/`90`. 9 tests added for delegated-source
resolution (`DelegatedSourceResolutionTest`), 2 more for the Phase C explanation policy
(`RecommendationSourceStatusExplanationPolicyTest`). No schema/proto/backup-format changes in this
upstream release. Final APK: `Komikku-v1.14.1-kmk.8.17-debug.apk`, copied to
`C:\Users\USER\Downloads\Komikku\private\`.

## v0.8.17-fix1 live-device UI/action follow-up (planned)

Source: user live-device review after the v0.8.17 APK. Treat these as concrete next-fix requirements,
not as vague UI polish. The fix should verify the current code first, then implement only the missing or
misaligned behavior.

Executable plan: `docs/community/KMK_RECS_V0_8_17_FIX1_LIVE_DEVICE_UI_ACTION_AND_BEST_VERSION_PLAN.md`.

Required items:

1. **For You selection action alignment and completeness.**
   - In For You selection mode, the action icons/text/emojis must align cleanly in the bottom bar and
     any overflow/menu surfaces. Current user report: some actions are visually misaligned with the
     icon/emoji that represents them.
   - Add a clear-rating action where it is meaningful. The user expects to be able to clear an existing
     Love/Like/Dislike/Not Interested rating from selected For You manga, not only apply new ratings.
   - Do not add a visible action unless it has working behavior and a clear result message.

2. **For You rating feedback and related-version continuation.**
   - After Love/Like/Dislike/Not Interested is applied from For You selection mode, show a short
     confirmation/snackbar/toast-style result so the user knows the action succeeded.
   - When a single manga is rated from For You, offer the same continuation the user requested elsewhere:
     a prompt or follow-up action to rate other versions / linked versions when relevant. This must reuse
     existing same-manga/cross-extension grouping/rating flows rather than creating a new grouping system.
   - The same continuation gap exists in the end-of-latest-chapter rating prompt: after the user rates
     the manga, it currently closes automatically instead of asking whether to rate other versions.

3. **Source Evaluation action-row alignment.**
   - `Details` and `Errors` now appear visually aligned, but `Install` does not align with the rest of
     the action row. Fix the shared row action layout so all three actions have consistent baseline,
     height, icon/text alignment, and tap-target sizing.
   - Keep the existing Details/Errors/Install action model; this is a layout/alignment fix, not a
     request to add source-rating controls to Source Evaluation.

4. **Recommendation Settings quick-access island.**
   - The user still does not see the requested quick-access/island-style navigation for Recommendation
     Settings.
   - Implement or explicitly defer with a concrete reason. If implemented, provide both a visible
     affordance and any optional swipe/drag gesture so the feature is discoverable.
   - Scope: quick access between Recommendation Settings destinations such as For You sources, Taste and
     filters, Source Evaluation, Sources to try, and Management/diagnostics. Do not duplicate settings;
     navigate to the existing destinations.

5. **Best Version preview loading, image failure, and strip-format follow-up.**
   - Live ADB check on 2026-07-20 confirmed the device is on `Find Best Version` and the current UI
     shows mixed broken states for the same workflow:
     - `The Tutorial is Too Hard` / `Source: Asura Scans` remains stuck on a row-level loading spinner.
     - `The Tutorial Is Too Hard` / `Source: Greed Scans` remains stuck on a row-level loading spinner.
     - `The Tutorial Is Too Tough!` / `Source: Mangahere` remains stuck on a row-level loading spinner.
     - `The Tutorial Is Too Tough!` / `Source: Comix` reports `5/5 pages loaded`, but every sampled
       thumbnail says `Preview failed`.
   - The relevant code paths are:
     - `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt`
       (`startPreview()`, `SourceRuntimeOperation.PageList`, `SourceRuntimeOperation.ImageUrl`,
       `CandidatePreviewState.Loaded`, `CandidatePreviewState.PreviewError`, and the final
       `awaitAll()`).
     - `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt`
       (`ComparePreviewContent`, per-thumbnail `SubcomposeAsyncImage(model = page.preview)`,
       `FullscreenPagePreviewDialog`, `FullscreenCandidatePreviewDialog`, `ContentScale.Fit/Crop`
       handling, and `best_version_preview_page_failed` display).
     - `app/src/main/java/eu/kanade/tachiyomi/data/coil/PagePreviewFetcher.kt`
       (`executeNetworkRequest()`, `PagePreviewSource.fetchPreviewImage(...)`, direct request fallback,
       source headers, cache handling).
     - `app/src/main/java/exh/recs/bestversion/BestVersionPageSampler.kt` and
       `BestVersionPreviewOutcomePolicy.kt`.
   - Root problems to solve structurally:
     - Candidate preview loading still waits for every selected candidate's async task to finish before
       switching to `ComparePreview`; one slow/hanging source can leave the whole workflow in a loading
       state even after other candidates have already produced useful preview data.
     - `Loaded(5 pages)` only means page URLs were prepared. It does not prove the images actually
       fetched/decoded. The current per-image `Preview failed` text is accurate but too generic to tell
       whether the cause is page-list failure, missing image URL, source-specific preview fetch failure,
       direct fallback request failure, cache failure, Cloudflare/HTTP failure, or decode failure.
     - Strip/webtoon-style pages are still displayed directly inside the comparison thumbnail/fullscreen
       surfaces. The Best Version preview does not currently reuse the reader's presentation logic or a
       dedicated read-only normalization layer, so some sources appear as long strips while others appear
       as individual pages/panels, making quality comparison unreliable.
   - Required fix behavior:
     - Add candidate-level timeout/isolation for preview loading. A slow source must become a per-source
       `PreviewError` or retryable state; it must not keep all candidates stuck in row-level or
       screen-level loading.
     - Allow the screen to reach `ComparePreview` once every candidate has either loaded or failed, and
       ensure failures are displayed per candidate with a retry affordance where safe.
     - Improve diagnostics enough that a future live log/user report can distinguish page-list errors,
       image-url resolution errors, image-fetch errors, and image-decode/display errors without exposing
       raw exception text to normal UI.
     - Fix the misleading `5/5 pages loaded` wording so it does not imply visible image success when all
       five thumbnails fail. Use wording like prepared/found samples, or show a secondary image-load
       status/count if that can be tracked safely.
     - Design a lightweight read-only preview normalization for long-strip/webtoon pages. Prefer reusing
       existing reader presentation helpers where practical; if full reader reuse is too heavy, provide a
       consistent fit/crop/default mode that makes page quality comparison fairer and document the
       limitation. Do not turn Best Version preview into a full reader.
     - Keep source/extension identity visible in all error, retry, and fullscreen preview states.
   - Verification must include live-device ADB/UI checks on the current failing example or an equivalent
     multi-source Best Version comparison. The specific current evidence is saved at
     `C:\Users\USER\Downloads\Komikku\diagnostics\adb-best-version-preview-2026-07-20\window.xml`.

6. **Universal structural standard, not one-off patches.**
   - While the concrete reports above are from For You selection, Source Evaluation, and Recommendation
     Settings, plus Best Version preview, the fix must audit similar KMK-added action rows/selection
     surfaces for the same patterns: icon/text alignment, missing result feedback, missing clear/reverse
     actions, missing continuation prompts after rating, loading states that can hang indefinitely, and
     generic error states that do not help the user or future debugging.
   - If a similar screen intentionally differs, document why.

Expected verification:

- For You long-press selection: Love/Like/Dislike/Not Interested/Clear Rating actions visible where
  appropriate, aligned, usable, and confirmed with user feedback.
- For You single-item rating: rate-other-versions continuation appears when linked/other versions are
  available or the existing search flow can be launched.
- End-of-latest-chapter rating prompt: after rating, the user is asked whether to rate other versions
  instead of the flow closing silently.
- Source Evaluation row actions: Details/Errors/Install align consistently on phone and tablet widths.
- Recommendation Settings: quick-access island/menu exists or is documented as intentionally deferred
  with the blocking reason and future design.
- Best Version preview: row-level loading does not hang indefinitely; each source resolves to loaded,
  failed, or retryable; `5/5 pages loaded` no longer appears beside five failed thumbnails without a
  clearer explanation; long-strip previews are presented in a more consistent read-only comparison mode.

## v0.8.16-fix1 UI readability and responsive polish (complete)

Plan: `docs/community/KMK_RECS_V0_8_16_FIX1_UI_READABILITY_AND_RESPONSIVE_POLISH_PLAN.md`.
Implementation report: `docs/community/KMK_RECS_V0_8_16_FIX1_UI_READABILITY_AND_RESPONSIVE_POLISH_IMPLEMENTATION.md`.
Evidence: `C:\Users\USER\Downloads\Komikku\diagnostics\ui-audit-v0.8.16\UI_AUDIT_NOTES.md` (live ADB
tablet audit of v0.8.16).

Delivered: Source Evaluation reassessment completion copy split into two clearer sentences (reassessed
vs. skipped) and the skipped-sources disclosure relabeled "N skipped source(s)"; Source Evaluation row
subtitle/action text sizes bumped and `Details`/`Errors`/`Install` merged into one wrapping `FlowRow`;
new `ForYouSelectionActionLayoutPolicy` makes the For You selection bottom bar width-aware (compact
width moves secondary actions into a "More" overflow, nothing is ever removed); the "Preview For You"
dialog is now full-screen (`Dialog`+`Scaffold`/`AppBar`) instead of a 420dp-capped `AlertDialog`, still
strictly read-only; Recommendation Settings index/search subtitles simplified (Source Evaluation's row
no longer reuses the screen's own installer-implementation-detail text); and Best Version preview
images now load through the existing source-aware `PagePreview`/`PagePreviewFetcher` path instead of
raw URL strings, fixing the audited bug where a candidate could report "N/N pages loaded" while showing
broken image placeholders. No recommendation scoring/ranking/queue/schema changes. See the
implementation report for the exact file list and test results.

## Next implementation planning gate (superseded by v0.8.17 and v0.8.17-fix1)

Status: this gate was partially closed by v0.8.17. Komikku 1.14.1 integration is complete. The active
items from this gate are now the v0.8.17-fix1 live-device UI/action follow-up above plus any later
approved For You quality or universal structural work.

The next implementation plan must include the items below as mandatory planning deliverables. It should
either implement them in the next version/fix, or split them into explicitly approved follow-up phases
with clear version boundaries. Do not leave them as scattered future notes, and do not let Claude treat
them as optional unless the user explicitly defers one of them during planning.

1. **Complete the remaining universal UI/action standardization.**
   This is broader than Source Evaluation or Recommendation Settings. Audit every KMK-added
   recommendation/rating/source/Best Version/For You settings surface against the shared interaction
   standards: long-press behavior, selection mode, multi-select actions, overflow/menu actions, search,
   row actions, button labels, tap targets, typography, responsive phone/tablet behavior, loading/error
   states, and whether visible actions actually work. Carry forward the v0.8.16-fix1 live-device
   confirmation gap for Best Version source-aware previews.

2. **Komikku 1.14.1 integration.**
   Completed in v0.8.17 Phase D. Keep only live-device verification items from the v0.8.17
   implementation report; do not re-plan 1.14.1 as future work unless a new upstream regression is found.

3. **Plan deeper For You recommendation-quality tuning.**
   This is future functional recommendation-quality work unless separately prioritized. Use the
   source no-match reports as concrete evidence: a source can visibly contain relevant manga while For
   You records no matches. The plan must inspect the source discovery/query planner, strategy recovery,
   tag aliasing, source-specific tag semantics, known-manga filtering, memory merge, catalogue fallback,
   and relevance gates before proposing scoring or crawling changes.

These may be split into separate implementation plans/phases. If split, the plan must define the order,
dependencies, version names, verification gates, and what should not be built until all selected phases
are complete.

## v0.8.16 Interaction, changelog, and Best Version polish (complete)

Plan: `docs/community/KMK_RECS_V0_8_16_INTERACTION_CHANGELOG_AND_BEST_VERSION_POLISH_PLAN.md`.
Implementation report: `docs/community/KMK_RECS_V0_8_16_INTERACTION_CHANGELOG_AND_BEST_VERSION_POLISH_IMPLEMENTATION.md`.

Delivered: `Find best version` separated from the taste/seen dropdown on manga detail
(`MangaInfoHeader.MangaActionRow`); Best Version candidate rows show source names and gained a
full-screen candidate comparison view (`FullscreenCandidatePreviewDialog`); Best Version Done now
navigates to the migrated/copied target manga (`BestVersionMigrationCompletionPolicy`); For You gained
long-press multi-select with a real bottom action bar (`ForYouSelectionPolicy`); KMK What's New now
groups entries by version family, current family expanded and older families collapsed by default,
with every historical entry preserved (`KmkRecsReleaseNotesGroupingPolicy`); Source Evaluation
continuation-policy test comments and `recordExtensionError()`'s delete+upsert decision now have
direct, accurate documentation and test coverage
(`SourceEvaluationExtensionErrorReconciliationPolicy`). See the implementation report for the exact
file list and test results.

## v0.8.15-fix1 Source Evaluation stale queue and row readability (complete)

Delivered: `docs/community/KMK_RECS_V0_8_15_FIX1_SOURCE_EVALUATION_STALE_QUEUE_AND_ROW_READABILITY_PLAN.md`
/ `docs/community/KMK_RECS_V0_8_15_FIX1_SOURCE_EVALUATION_STALE_QUEUE_AND_ROW_READABILITY_IMPLEMENTATION.md`.
Closed the second cause of the "Reassess outdated" false-progress bug that v0.8.15 left open: the stale
queue now excludes blocked/explicit sources from the actionable list before they ever reach the runner
(`SourceEvaluationCandidateQueuePolicy.staleCandidates(includeExplicit = ...)`), and the runner's
cursor-tracking set only ever gains a key when a candidate durably writes a database row (renamed
`_completedCandidateKeys`, no exceptions). `recordExtensionError()`'s delete-before-upsert step is
now failure-aware: a failed delete no longer counts as durable work. Also delivered the clarified
Source Evaluation row-action model (`Details`/`Errors`/`Install`, `SourceEvaluationRowActionPolicy`)
and a page-level "Sources to try" shortcut. See the implementation report for exactly what changed,
files touched, and test results.

## v0.8.15 Source Evaluation reassessment fix and universal UI readability (complete)

Delivered: `docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_PLAN.md` /
`docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_IMPLEMENTATION.md`.
Root-cause fix for the live-device "Reassess outdated (25)" / "Evaluation completed" / no DB change bug
(fire-and-forget error-record write + an extension-level error key that couldn't replace stale
per-source rows, both in `SourceEvaluationRunner`); a batch that durably handles zero candidates now
reports a new `NoActionableWork` status instead of the generic `Completed`. Also started the universal
KMK UI readability audit: Source Evaluation row subtitles compacted, three Recommendation Settings
summaries shortened, two mojibake'd strings fixed, and `KMK_RECS_UNIVERSAL_UI_READABILITY_AUDIT.md`
added as a living cross-surface tracking document. See the implementation report for exactly what
changed, files touched, and test results.

## v0.8.14-fix1 Recommendation Settings structural completion (complete)

Delivered: `docs/community/KMK_RECS_V0_8_14_FIX1_RECOMMENDATION_SETTINGS_STRUCTURAL_COMPLETION_PLAN.md` /
`docs/community/KMK_RECS_V0_8_14_FIX1_RECOMMENDATION_SETTINGS_STRUCTURAL_COMPLETION_IMPLEMENTATION.md`.
"Sources and languages" renamed "For You sources", language selection moved to Management and
diagnostics, the For You preview replaced with a real manga-covers-and-titles snapshot
(`RecommendationForYouPreviewSnapshotStore`), Source Evaluation's outdated-reassessment action reordered
ahead of a newly-collapsed excluded-sources disclosure, Recommendation Settings search duplicate-subtitle
cleanup, stale screen-name references audited, and two Source Evaluation tap targets widened to normal
size. See the implementation report for exactly what changed, files touched, and test results.

## v0.8.14 live-device structural UX follow-up (superseded by v0.8.14-fix1 above)

Status: closed -- every item below was implemented in v0.8.14-fix1.

Live ADB review after v0.8.14 confirmed that Browse, For You, and Recommendation Settings open without the earlier source-runtime crash, but the structural UX pass is still incomplete. See `docs/community/KMK_RECS_V0_8_14_LIVE_DEVICE_STRUCTURAL_UX_FOLLOWUP.md`.

## Rolling next-version notes

Status: capture queue for future implementation plans. These items are not part of v0.8.14-fix1 unless explicitly moved there.

### v0.8.15-fix1 Source Evaluation stale queue and row readability

Date: 2026-07-19
Status: implemented; retained here as historical detail for why the v0.8.15-fix1 corrective pass was needed. Do not treat this section as active future work.

Plan:

- `docs/community/KMK_RECS_V0_8_15_FIX1_SOURCE_EVALUATION_STALE_QUEUE_AND_ROW_READABILITY_PLAN.md`

Live-device follow-up after v0.8.15 showed the Source Evaluation stale/outdated fix is still incomplete.

Confirmed ADB/database evidence:

- Before tapping `Reassess outdated`: `source_evaluation` contained `48` stale rows (`evaluation_version < 3`), including `44` source-level stale rows.
- After tapping `Reassess outdated`: the UI changed to `Evaluation completed` plus `Continue reassessing outdated (15 remaining)`, but the database still contained the same `48` stale rows.
- Logcat showed `SourceEvaluationJob` started and returned `SUCCESS`; no user-visible failure explained the no-write result.

Root cause now confirmed in code:

- `SourceEvaluationCandidateQueuePolicy.staleCandidates(pool, now)` builds the stale queue from `pool.allEligible`, which intentionally does not apply option filters such as explicit/adult blocking.
- `SourceEvaluationRunner.start()` then skips explicit candidates when explicit blocking is active, adds their keys to `_completedCandidateKeys`, and writes nothing.
- `SourceEvaluationRunCompletionPolicy` receives `_completedCandidateKeys.size` as durable work even though skipped candidates did not write to `source_evaluation`.
- The stale cursor can therefore advance while stale database rows remain unchanged.

Implemented fix:

- Make stale candidates actionable-only under the current options.
- Separate cursor advancement from durable database writes, or otherwise guarantee no-write skips cannot report `Completed`.
- Harden `recordExtensionError()` delete failure handling so failed package-row deletion cannot leave stale source rows while reporting success.
- Add tests for actionable stale queue filtering, no-write completion behavior, cursor non-advancement, and package delete+upsert reconciliation.
- Continue Source Evaluation row readability work: normal-size text/tap targets, compact collapsed summaries, raw evidence only in expanded details.
- Implement the user-confirmed three Source Evaluation row actions:
  - `Details` for full evidence/metadata diagnostics;
  - `Errors` for error-specific failure information;
  - `Install` for direct install from an evaluated non-installed source when safely available.
- Add a Source Evaluation page-level shortcut to the existing Sources to try screen so users can jump directly to the curated install/rating surface without backing out through Recommendation Settings.

### v0.8.15 follow-up: reassessment durability edges and documentation reconciliation

Date: 2026-07-19
Status: superseded/expanded by the v0.8.15-fix1 plan above.

Context:

- v0.8.15 implemented the main Source Evaluation reassessment root-cause fix, but the post-implementation review found three follow-up items that should be handled before treating the reassessment work as fully closed.
- These are corrective/follow-up items, not a new source-evaluation redesign.

Resolution note (2026-08-05): this historical follow-up is resolved in the current source and is
retained here for traceability. `SourceEvaluationRunner.recordExtensionError()` now calls the
injected `ReplaceSourceEvaluation` interactor; `ReplaceSourceEvaluation.await()` delegates to the
repository `replaceByPackage()` contract, so the stale-row replacement is one atomic repository
operation rather than a separate delete-then-upsert sequence. The current
`domain/src/test/java/tachiyomi/domain/taste/interactor/ReplaceSourceEvaluationTest.kt` covers
successful replacement, delete failure, upsert rollback, cancellation propagation, metadata
preservation, duplicate prevention, and non-durable failure classification. The normal
Source-Evaluation UI/job/runner flow remains the existing product path; only live-device execution
and independent device evidence remain outside host validation.

Historical required fixes (resolved in the current source; retained for traceability):

1. Documentation wording is resolved by this dated reconciliation note. The historical
   Source-Evaluation reassessment section is not an active implementation queue; only live-device
   execution and independent device evidence remain outside host validation.
2. `SourceEvaluationRunner.recordExtensionError()` now uses the injected
   `ReplaceSourceEvaluation.await()` interactor, which delegates to the repository
   `replaceByPackage()` transaction. The operation is awaited, cancellation propagates, and an
   ordinary persistence failure is classified as non-durable rather than silently counted as
   handled. The old fire-and-forget and separate delete-then-upsert descriptions above are
   historical context, not pending work.
3. The direct persistence-facing regression coverage is present in
   `domain/src/test/java/tachiyomi/domain/taste/interactor/ReplaceSourceEvaluationTest.kt` and
   `domain/src/test/java/tachiyomi/data/taste/SourceEvaluationRepositoryTransactionTest.kt`,
   covering success, delete failure, rollback, cancellation, metadata preservation, duplicate
   prevention, and non-durable classification. No additional runner extraction is required by this
   historical follow-up.

The normal Source Evaluation UI/job/runner path remains the existing product flow. Do not reopen
this implementation work unless current source inspection proves a regression; device execution,
rollback evidence, and the separate manual publication gate remain external.

### KMK What's New collapsible historical sections

Date: 2026-07-19
Status: implemented in v0.8.16 (`KmkRecsReleaseNotesGroupingPolicy`). Retained as historical detail.

User clarification:

- The KMK What's New section now contains many versions and is becoming too long to read comfortably.
- The latest active/current version line should stay expanded by default. For the current app state, that means the current `0.8.x` entry or current version group should be visible immediately.
- Older version families should be collapsed by default, especially:
  - `0.7.x`;
  - `0.6.x`;
  - `0.5.x`;
  - `0.4.x`.
- The older collapsed groups should not simply hide everything without context. Each collapsed group should provide a concise, properly formatted summary of what that version family covered.
- Expanding a group should reveal the full historical notes in the existing official-Komikku-style format.
- Preserve the current useful information and historical individual entries. Do not delete old changelog content.
- The goal is to make What's New readable and professional, not to reduce accuracy.

Expected behavior:

- Opening KMK What's New should show the latest/current release details first.
- Older release families should appear as collapsed sections with clear titles, for example `KMK-Recs v0.7.x`, `KMK-Recs v0.6.x`, etc.
- Each collapsed section should show a short summary line or paragraph that tells the user the major theme of that era before expansion.
- Tapping a section expands/collapses it using Komikku's existing disclosure/list patterns where possible.
- The implementation should reuse existing Komikku/Compose expandable row patterns instead of creating a custom one-off changelog system if an existing pattern fits.
- The Markdown content should remain structured and testable. If the current `KmkRecsReleaseNotes.MARKDOWN` string is not enough to support default-collapsed version families cleanly, introduce a small structured model beside it, but preserve Markdown rendering for the actual notes where possible.

Likely code areas:

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt`
- official Komikku What's New / changelog renderer code
- existing expandable/disclosure UI components used in settings or source evaluation
- `app/src/test/java/exh/recs/KmkRecsReleaseNotesTest.kt`

Required tests:

- Latest/current version remains first and expanded by default.
- Historical version headings are still present and not deleted.
- Historical version families can be grouped without duplicate or missing version headings.
- Every expanded historical entry still contains the official-style `What's Changed` structure.
- Collapsed summaries are stable and do not contain app-internal/private/public wording.

### Recommendation Settings quick-access island / swipe drawer

Date: 2026-07-19
Status: confirmed for a future UI/navigation implementation plan, exact version not assigned.

User idea:

- Recommendation Settings / For You settings would benefit from a quick-access navigation surface similar in spirit to an iPhone-style app island or pull-out quick view.
- The user imagines swiping/dragging from one side to reveal compact app-like shortcuts: icon/emoji plus section name, tapping a shortcut jumps directly to that Recommendation Settings section.
- This should make it easier to move between For You sources, Taste and filters, Source Evaluation, Sources to try, and Management/diagnostics without repeatedly backing out and re-entering screens.

Implementation requirements to evaluate before coding:

- Check Komikku's existing navigation, drawer, bottom sheet, swipe, and settings patterns first. Do not create an iOS-looking custom gesture if it clashes with Komikku/Material behavior.
- Determine whether this should be:
  - a side sheet/drawer;
  - a modal bottom sheet;
  - a floating quick-access button opening a compact menu;
  - a horizontal shortcut row;
  - or a gesture plus visible affordance.
- Avoid gesture conflicts with system back gestures, nested scrolling, pull-to-refresh, and tablet/landscape layouts.
- The interaction must be discoverable. If swipe is supported, there should also be a visible button/icon to open the same quick-access surface.
- Shortcuts should use existing section icons where possible, not arbitrary new imagery.
- Search should remain available and not be replaced by this feature.
- This should apply to Recommendation Settings navigation only unless a broader pattern is deliberately approved later.

Likely code areas:

- `app/src/main/java/exh/recs/settings/RecommendationSettingsIndexScreen.kt`
- `app/src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt`
- destination screens under `app/src/main/java/exh/recs/settings/`
- existing Komikku settings/navigation drawer/bottom-sheet components
- KMR strings for section shortcut labels and accessibility descriptions

Required tests / QA:

- Verify the visible quick-access action opens the same surface as the gesture.
- Verify every shortcut navigates to the correct section and preserves current settings state.
- Verify search still works.
- Manual QA: phone/tablet, portrait/landscape, system gesture navigation, large font, dark mode.

### Manga action and selection standardization

Date: 2026-07-19
Status: implemented in v0.8.16 (Find best version separated from rating on manga detail;
`ForYouSelectionPolicy` long-press selection with bulk rate/not-interested/single-only actions). Bulk
add-to-library was evaluated and deliberately deferred (documented) as it needs category-selection UX
beyond that pass's scope. Retained as historical detail.

User clarification:

- Finding Best Version should be separate from rating. Rating means Love/Like/Dislike/Not Interested or clearing/changing that preference. Best Version is a source-quality/comparison/migration workflow and should have a separate icon/action, not be visually grouped as another rating.
- Long-press behavior should be standardized across manga views where selection makes sense.
- In For You, long-pressing a manga card should enter selection mode and allow useful actions such as add to library, rate, mark not interested/seen, or find Best Version.
- Loved/Liked/Disliked and Library can expose different actions, but the underlying interaction language should be consistent.
- Avoid using long-press as a hidden recommendation launcher. Recommendation actions should be explicit menu/button actions.
- Current gap confirmed by user after v0.8.15: the For You page still does not allow long-press selection or multi-select of manga cards. This should be treated as a structural interaction gap, not a one-off button request.
- For You selection mode should allow selecting multiple recommendation cards and exposing useful bulk actions where safe. Candidate actions to evaluate:
  - add selected manga to library;
  - rate selected manga;
  - mark selected manga as not interested/seen;
  - find Best Version for a selected manga when exactly one item is selected;
  - block/dislike source where selected items share a source or show a safe per-source choice.
- Do not add a visual selection state unless it leads to working actions.

Likely code areas:

- `BrowsePersonalRecommendationsTab.kt`
- For You manga card/list row composables used by source rows and Top Picks.
- `GlobalSearchCardRow.kt`
- rated manga screens under `exh/recs/loved`
- Best Version and cross-source matching screens
- shared selection/action components if a reusable pattern already exists.

### Best Version preview source labels and page normalization

Date: 2026-07-19
Status: source labels and full-screen comparison implemented in v0.8.16
(`best_version_candidate_source` rows, `FullscreenCandidatePreviewDialog`). Page-shape normalization
(reusing reader display normalization, or a lighter equivalent, so panel-style vs. strip-style pages
compare more fairly) was NOT attempted in v0.8.16 -- out of that pass's scope -- and remains open for a
future pass.

User clarification:

- In the Best Version workflow, previewed chapter/page samples must clearly show which extension/source each preview belongs to.
- The current preview can feel non-standard because different extensions return differently shaped pages:
  - some appear as individual panels/pages;
  - some appear as long strip-style images;
  - this makes quality comparison harder because layout differences distract from image quality.
- Investigate whether the Best Version preview can reuse the normal reader's display normalization or reader-view behavior so preview pages are shown more consistently.
- If full reader reuse is too risky, design a lighter read-only preview normalization that still makes comparisons fairer.

Important constraints:

- Do not turn Best Version preview into a full reader.
- Do not lose the side-by-side/source comparison purpose.
- Keep source/extension identity visible while previewing.
- Do not make network/page loading heavier than necessary.

Likely code areas:

- Best Version workflow screens/models under `exh/recs`
- reader page display components under `eu/kanade/presentation/reader` or related reader UI packages
- chapter/page preview sampling logic
- strings for source labels and preview hints.

### Best Version migration completion navigation

Date: 2026-07-19
Status: implemented in v0.8.16 (`BestVersionMigrationCompletionPolicy`, `navigator.replace(MangaScreen
(targetId, true))` on Done, `pop()` fallback if the target id could not be resolved). Retained as
historical detail.

User clarification:

- After a Best Version migration completes, pressing `Done` should open the manga that the user migrated to.
- It should not return the user to the old/original manga entry.
- This is intended to make the workflow feel complete: once a better version is chosen and migration succeeds, the destination manga becomes the user's current manga.

Likely code areas:

- Best Version migration confirmation/completion screen
- migration result handling
- navigator stack handling after migration
- manga detail navigation (`MangaScreen`) for the destination manga id.

### For You preview full-screen presentation

Date: 2026-07-19
Status: confirmed for the next implementation/fix plan, exact version not assigned.

Implementation handoff: captured in `docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_PLAN.md`.

User clarification:

- The new read-only For You preview content is good: it correctly shows manga covers and titles from the last For You refresh.
- The current preview container is too small.
- The preview should open as a full-screen read-only screen or full-screen dialog, not a compact `AlertDialog`.
- The user expects to scroll the preview comfortably in a phone/tablet-sized layout, closer to the actual For You page.
- The preview must remain read-only: manga cards should not open manga, trigger searches, run source calls, or mutate settings.
- A close action should be easy to find. Preferred direction: a close button in the top-right, unless code/design review shows Komikku's existing full-screen modal pattern uses a more standard placement.

Likely code areas:

- `app/src/main/java/exh/recs/settings/RecommendationSourcePrioritySettingsScreen.kt`
- `ForYouSnapshotPreviewDialog`
- `RecommendationForYouPreviewSnapshotStore`
- Komikku's existing full-screen dialog/screen patterns, especially settings sub-screens and modal screens with top app bars.
- `i18n-kmk` strings for close/title/hint text if the layout changes wording.

Implementation notes:

- Prefer replacing the compact `AlertDialog` with a full-screen `Screen` or full-screen modal pattern already used by Komikku.
- Use a top app bar or equivalent official Komikku pattern with a close action.
- Preserve the existing snapshot persistence model; this is a presentation change, not a new data pipeline.
- Ensure large-font, phone, tablet, landscape, and dark-mode layouts remain usable.
- Add or update tests only for pure state/string/anchor behavior; Compose visual verification remains manual/device QA unless an existing test harness supports it.

### Source Evaluation outdated reassessment correctness

Date: 2026-07-19
Status: implemented in v0.8.15; retained as pre-fix evidence. The host follow-up is resolved in
the current source (2026-08-05); live-device verification remains pending.

Implementation handoff: captured in `docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_PLAN.md`.

User-reported symptom:

- The `Reassess outdated (25)` action still gives a false completion signal.
- Pressing it can show `Evaluation completed`, but the button/count remains `Reassess outdated (25)` and the actual outdated set is not reassessed.
- This must not be treated as wording-only polish. The issue is that the stale/outdated reassessment action appears to enter a completed state without actually draining or updating the actionable outdated queue.

Expected behavior:

- If `Reassess outdated` is visible with an actionable count, pressing it must either:
  - process an actual batch of actionable stale/outdated candidates; or
  - show a clear non-fatal reason why none can currently be processed.
- The UI must not show `Evaluation completed` while actionable stale candidates still remain.
- Installed, language-filtered, explicit-filtered, quarantined, or otherwise excluded outdated sources may be shown in a separate collapsed/explanatory note, but they must not block reassessment of reachable stale sources and must not be counted as actionable.
- Cursor/continuation state must advance only after a real stale batch is attempted and must be recomputed/reset when the stale candidate list or its fingerprint changes.
- After a stale reassessment run, the button count, completion card, hidden/excluded note, and row statuses must reflect the post-run state.

Likely code areas to inspect and change:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
  - `startOrContinueStaleReassessment()`
  - `restartStaleReassessment()`
  - stale run selection/cursor update logic
  - state fields such as `staleCandidates`, `continuationCursorStale`, and `remainingStaleCandidateCount`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateQueuePolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationOutdatedReconciliation.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationStaleCompletionDisplayPolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationContinuationPolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
  - stale completion card
  - stale reassess button
  - unreachable/excluded outdated note

Required tests:

- A mixed set with actionable stale candidates plus unreachable/excluded stale candidates must reassess the actionable subset and leave only the unreachable/excluded note afterward.
- A stale reassessment run must not enter a completed display state when the same actionable count remains.
- Cursor/fingerprint tests must cover:
  - first stale batch;
  - continuing to the next stale batch;
  - stale candidate list changes after source refresh/reconciliation;
  - all stale candidates excluded/unreachable.
- Failure/no-op runs must show an explicit non-fatal state instead of silently claiming completion.
- If live ADB is available, verify the current tablet state before/after pressing `Reassess outdated` and capture whether the underlying database rows actually changed.

Live ADB evidence captured 2026-07-19:

- Device/package: `<test-tablet>` / `app.komikku.dev`.
- Pre-run UI showed:
  - `Reassess outdated (25)`;
  - `17 outdated source(s) outside this run`;
  - `109 evaluated extension(s) have updates`.
- Pre-run DB snapshot: `C:\Users\USER\Downloads\Komikku\diagnostics\tachiyomi-dev-live-20260719.db`.
- Post-run DB snapshot: `C:\Users\USER\Downloads\Komikku\diagnostics\tachiyomi-dev-after-reassess-20260719.db`.
- Logcat capture: `C:\Users\USER\Downloads\Komikku\diagnostics\reassess-outdated-logcat-20260719.txt`.
- After tapping `Reassess outdated (25)`, logcat showed `SourceEvaluationJob` starting and returning `Worker result SUCCESS` in under one second.
- The UI then showed `Evaluation completed`, while still showing `Reassess outdated (25)` and the same `17 outdated source(s) outside this run`.
- DB before/after comparison showed no changes:
  - `source_evaluation` still had 48 rows with `evaluation_version < SourceEvaluationKeys.CURRENT_VERSION`;
  - the version/verdict distribution was identical before and after;
  - max `evaluated_at` for stale rows was unchanged.
- Current live DB distribution before and after:
  - version `1`: 48 rows (`15 error`, `3 explicit_heavy`, `1 poor_search`, `3 strong_fit`, `24 weak`, `2 worth_trying`);
  - version `3`: 377 rows.
- Most stale rows are source-specific: 44 stale rows have non-null `source_id`; only 4 stale rows have `source_id = null`.

Historical root-cause findings (pre-fix; resolved in the current source on 2026-08-05):

- The pre-fix `SourceEvaluationRunner.start()` path could add a candidate key before durable
  source-evaluation work was known to have succeeded. The current path advances the stale cursor
  only after the candidate reports durable handling.
- The pre-fix `recordExtensionError()` path used a fire-and-forget
  `scope.launch { upsertSourceEvaluation.await(errRecord) }` write. The current path awaits the
  injected `ReplaceSourceEvaluation` interactor and preserves cancellation/error classification.
- The pre-fix extension-level error path used a `sourceId = null` row without replacing older
  source-specific stale rows. The current `replaceByPackage()` repository transaction replaces the
  package rows atomically before the new extension-level record is considered durable.
- The pre-fix worker could therefore report success while stale rows remained. The current
  continuation and reconciliation policies depend on durable per-candidate/per-source outcomes.

Host follow-up evidence is recorded in the central ledger and in the focused
`ReplaceSourceEvaluationTest` and `SourceEvaluationRepositoryTransactionTest` suites. No live-device
execution or independent device evidence is implied by this documentation correction.

### Universal KMK structural UI readability/density cleanup

Date: 2026-07-19
Status: confirmed for the next implementation/fix plan, exact version not assigned.

User clarification:

- Source Evaluation still has too much small, dense, technical text in the first view.
- The user should be able to understand a source row at a glance without reading `catalogue fit`, `metadata`, `evidence`, sample counts, and error diagnostics all inline.
- Technical details must remain available, but should move behind an expandable details area or help/details affordance.
- This is a universal structural readability standard for every KMK-added screen and workflow, not only Source Evaluation or Recommendation Settings.
- Source Evaluation and Management/diagnostics are current visible examples, but the same audit must cover For You, For You source rows, Source Priority, Sources to Try, Taste and tags, rated manga collections, Best Version, group recommendations, recommendation search/settings, source actions, and any other KMK-added UI.

Expected Source Evaluation row structure:

- Main row should show a compact, user-facing summary:
  - fit summary/verdict derived from existing catalogue fit, metadata confidence, evidence, and current label;
  - last evaluated / reassessment-needed state;
  - clear status if the source is broken, explicit, blocked, quarantined, or unavailable.
- Expandable details should contain the raw/debug facts:
  - catalogue fit percentage;
  - metadata confidence;
  - evidence strength;
  - For You search compatibility result;
  - error kind and recoverable runtime failure details;
  - enriched sample count;
  - samples with metadata;
  - liked/disliked/blocked/adult-risk counts;
  - manual review or warning notes.
- Tap targets for details/actions must remain usable on phones and with larger font sizes.
- Avoid tiny `Show details` / `Hide details` text that is hard to target or visually distinguish.

Universal readability standard:

- Management/diagnostics and related Recommendation Settings screens still have long/high-level summaries that assume the user already understands the feature internals.
- Summaries should be shorter, lower-level, and more intuitive.
- Longer explanations may be kept in details/help text, but should not dominate the first view.
- Example target: `same_manga_match_preselect_summary` currently explains implementation behavior in a way that is hard for new users. Prefer plain-language copy such as "Start likely matches selected so you only remove wrong ones", with details explaining that the origin manga is never selected.
- Similar cleanup should be applied across all KMK-added surfaces, not only the examples explicitly named by the user.
- Any screen that exposes dense counters, technical diagnostics, small text actions, repeated inline buttons, long summaries, raw exception names, or unclear internal terminology must be reviewed against this standard.
- The implementation plan must identify which KMK screens were audited, what pattern each screen should follow, which screens are changed in that pass, and which issues are intentionally deferred.

Likely code/string areas:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
  - `EvaluationResultRow`
  - Details toggle/tap target presentation
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationDisplayPolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationEvidenceSummaryPolicy.kt`
- Possible new pure policy for compact row summaries if the logic would otherwise crowd the Composable.
- `app/src/main/java/exh/recs/settings/RecommendationDiagnosticsSettingsScreen.kt`
- `app/src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt`
- `app/src/main/java/exh/recs/settings/RecommendationSettingsSearchScreen.kt`
- For You and recommendation UI under `app/src/main/java/exh/recs/`
- rated manga collection screens under `app/src/main/java/exh/recs/loved/`
- Best Version screens under `app/src/main/java/exh/recs/bestversion/`
- group recommendation and source result rows under `app/src/main/java/exh/recs/`
- `i18n-kmk/src/commonMain/resources/MR/base/strings.xml`
  - `source_evaluation_catalogue_row_subtitle`
  - stale/error/detail Source Evaluation strings
  - `same_manga_match_preselect_summary`
  - `rec_enrichment_cap_summary`
  - `rec_group_preview_budget_summary`
  - any other KMK recommendation-setting summaries that are long, technical, or duplicated in search results.

Implementation constraints:

- Do not remove diagnostic information; relocate it into expandable details/help where appropriate.
- Follow official Komikku spacing, typography, settings row, disclosure, and accessibility patterns.
- Use KMR strings for every new or changed user-facing string.
- Keep Recommendation Settings search useful even if visible summaries become shorter: the search index can retain synonyms/technical terms so users can still find settings by words like metadata, catalogue, source, evaluation, blocked, tags, and compatibility.
- Avoid creating another parallel UI framework for cards/rows unless an existing KMK component is already the shared standard.
- This must be handled as a structural UI audit plus targeted refactor, not as isolated copy edits for only the strings named in this note.
- Verify phone, tablet, landscape, dark mode, and large-font behavior where device access is available; otherwise document those as manual QA gaps.

## v0.8.14 (complete)

Delivered: `docs/community/KMK_RECS_V0_8_14_RECOMMENDATION_SETTINGS_STRUCTURAL_UX_PLAN.md` /
`docs/community/KMK_RECS_V0_8_14_RECOMMENDATION_SETTINGS_STRUCTURAL_UX_IMPLEMENTATION.md`.
Recommendation Settings index rebuilt to exactly five sections (Sources and languages, Taste and
filters, Source Evaluation, Sources to try, Management and diagnostics); `For You`/`Matching and
versions` retired as top-level destinations, files deleted, controls moved verbatim into the new
five-section model; Source Evaluation stale-reassessment row labels and completion copy now correctly
distinguish actionable from excluded/unreachable outdated sources; Source Evaluation first-view visual
fog reduced via "More setup options"/"Installer details" disclosures; primary Source Evaluation copy
audited for technical wording. See the implementation report for exactly what changed and what tests
were added.

**Live-device residuals opened after this pass.** The implementation report remains the record of what
v0.8.14 shipped, but the follow-up document above is now the authoritative record for what still needs
correction before this structural UX line should be considered polished.

## v0.8.13-fix1 (complete)

Delivered: `docs/community/KMK_RECS_V0_8_13_FIX1_RECOMMENDATION_SETTINGS_AND_EVALUATION_UX_PLAN.md` —
Recommendation Settings search flicker fix (synchronous `remember` replacing `produceState`/
`Crossfade`), search-result category/control row distinction, language selector moved to Source
Priority, group-preview budget moved to Matching and versions, read-only "Preview For You layout"
dialog, truthful Source Evaluation stale-reassessment completion states
(`SourceEvaluationStaleCompletionDisplayPolicy.DisplayState`), Source Evaluation quarantine/blocked
diagnostics collapsed by default, and v0.8.13 residual completion. See
`docs/community/KMK_RECS_V0_8_13_FIX1_RECOMMENDATION_SETTINGS_AND_EVALUATION_UX_IMPLEMENTATION.md` for
exactly what changed and what tests were added.

**Deferred, documented residual:** fallback/provenance UI in Source Priority/For You source status
details (surfacing when a source's last useful candidates came from the v0.8.13 catalogue fallback,
`RecommendationCatalogueFallbackPolicy.QUERY_STRATEGY`) needs new `RecommendationsSettingsScreenModel`
data plumbing (it currently has no dependency on candidate-memory/discovery-progress stores) — the
underlying data already exists (recorded since v0.8.13 Phase D), only the read-side UI wiring is
missing. A future pass can add this without any further write-side or schema change.

## v0.8.13 (complete)

Delivered: `docs/community/KMK_RECS_V0_8_13_FOR_YOU_STRATEGY_RECOVERY_AND_RELEVANCE_PLAN.md` — strategy
recovery (`RecommendationStrategyRecoveryPolicy`, rewritten `RecommendationQueryPlanner.buildPlans`),
bad-extra-page-progression stop (`RecommendationAdditionalPagePolicy`), catalogue fallback diagnostics
(`CATALOGUE_FALLBACK` query-strategy constant recorded in memory/progress), positive-taste-evidence
relevance gate (`PersonalRecommendationScorer.requirePositiveTasteEvidence`, applied to memory merge
too), and a `searchSource()` helper-extraction refactor. See
`docs/community/KMK_RECS_V0_8_13_FOR_YOU_STRATEGY_RECOVERY_AND_RELEVANCE_IMPLEMENTATION.md` for exactly
what changed, the documented scope decision on Phase H's UI diagnostics, and what real-device QA
remains (confirming the live the affected source false no-match / stale-strategy symptoms are actually
resolved on-device, and confirming `searchSource()` no longer exceeds Android's compiler instruction
limit).

## v0.8.12-fix1 (complete)

Delivered: corrective fix found during v0.8.12 review — `BrowsePersonalRecommendationsScreenModel
.searchSource()`'s two `catch (e: Error)` blocks (main attempt loop + catalogue fallback) previously
swallowed every `Error` subtype unconditionally instead of only recoverable per-source failures,
risking silent loss of fatal VM errors thrown by non-SourceRuntime code between the SourceRuntime
calls (enrichment/scoring/memory lookups). Both now route through the shared `rethrowIfFatal()`
helper. See
`docs/community/KMK_RECS_V0_8_12_FIX1_FATAL_ERROR_CONTAINMENT_IMPLEMENTATION.md` for the full
before/after and verification.

## v0.8.12 (complete)

Delivered: all workstreams (A-G) of
`docs/community/KMK_RECS_V0_8_12_RECOMMENDATION_SETTINGS_STRUCTURAL_FOLLOWUP_PLAN.md` — centralized
recommendation language availability policy, Same manga matching / Best Version preview moved to a
new "Matching and versions" destination, Source Evaluation outdated-count investigation (confirmed
already correct, extracted a tested completion-display policy), 10-at-a-time progressive reveal for
Taste Suggestions and stored tag preferences, a fixed raw-exception-class-name leak in For You /
Matching-and-versions row errors, and a targeted For You false no-match fix (bounded Popular
catalogue fallback probe) for the the affected source report. See
`docs/community/KMK_RECS_V0_8_12_RECOMMENDATION_SETTINGS_STRUCTURAL_FOLLOWUP_IMPLEMENTATION.md` for
exactly what changed and what tests were added.

## v0.8.11 (complete)

Delivered: all phases (A-H) of
`docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_IMPLEMENTATION_PLAN.md` — Recommendation
Settings destination dedupe and search cleanup, official widget conformance for simple rows, Taste
Suggestions grouping/capping, Sources To Try action density, Source Priority density/sections, Source
Evaluation sectioning with anchors, For You top-bar grouping, the interaction/functionality audit
(`docs/community/KMK_RECS_INTERACTION_FUNCTIONALITY_AUDIT.md`), and the Loved/Liked/Disliked
multi-select grouping bug fix. See
`docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_IMPLEMENTATION.md` for exactly what
changed and what tests were added.

**Not done, deferred to a future pass** (both recorded in the audit doc):

1. Search does not visually coexist with selection mode on Loved/Liked/Disliked — the search field is
   hidden (the underlying filter is still applied to whatever query was last entered) while
   `selectionMode` is true, since the top bar branches entirely between the two. Not broken, but real
   layout work to fix properly (showing both the selection-count/close affordance and a compact search
   field at once), not attempted this pass.
2. `removeSelectedFromGroup()`/`ungroup()` got the same immediate-state-refresh fix as merge (see
   below), but no explicit success/failure Snackbar was added for those two specifically — lower risk
   since the affected items simply leave the confirmed-group view, but could be added for consistency
   with Merge's new feedback in a future pass.
4. Recommendation Settings search "flicker" — investigated three times now (fix9, v0.8.11 Phase A,
   noted again in the interaction audit), not fixed. The KMK search screen
   (`RecommendationSettingsSearchScreen.kt`'s `RecommendationSettingsSearchResult`) uses
   `produceState`+`Crossfade(targetState = result)`, which is structurally identical to official
   Komikku's `SettingsSearchScreen.kt`'s `SearchResult` — same pattern, same per-keystroke animation.
   The plan assumed KMK deviates from official here; it does not. Fixing it would mean diverging from
   official Komikku's own established pattern without further evidence the flicker is actually
   reproducible/worse in the KMK screen specifically. To verify later: reproduce on a real device with
   both screens side by side; if KMK's does look worse, the likely cause is result-list content
   (longer subtitles/wrapping) rather than the animation itself.
5. Real-device QA (see the v0.8.11 implementation report's manual QA checklist) is still required —
   no `adb`/device access exists in this environment.

## v0.8.10-fix9 (partial)

Delivered: historical changelog conversion (item 1 below), Taste and Tags grouping (part of item 2),
and all of item 3 (source-runtime hygiene). See
`docs/community/KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_IMPLEMENTATION.md` for exactly
what changed and what tests were added.

**Not done, deferred to a future pass:**

1. Full Komikku-widget conformance across Recommendation Settings (`SectionHeader` ->
   `PreferenceGroupHeader`, boolean rows -> `SwitchPreferenceWidget`, numeric/list rows ->
   `ListPreferenceWidget`), Source Evaluation, Sources To Try, and Rated Collections. Scope: real,
   ~10 files, not attempted this pass. Risk of not doing it: KMK-added screens keep visually diverging
   from official Komikku settings density/spacing; no functional risk.
2. Recommendation Settings search "flicker" � investigated, not fixed. The KMK search screen
   (`RecommendationSettingsSearchScreen.kt`'s `RecommendationSettingsSearchResult`) uses
   `produceState`+`Crossfade(targetState = result)`, which is structurally identical to official
   Komikku's `SettingsSearchScreen.kt`'s `SearchResult` � same pattern, same per-keystroke animation.
   The plan assumed KMK deviates from official here; it does not. Fixing it would mean diverging from
   official Komikku's own established pattern, which the user chose not to do without further evidence
   the flicker is actually reproducible/worse in the KMK screen specifically. To verify later: reproduce
   on a real device with both screens side by side; if KMK's does look worse, the likely cause is
   result-list content (longer subtitles/wrapping) rather than the animation itself.
3. Taste suggestion grouping follow-up � the fix9 grouping work addressed stored tag preferences, but
   the same readability issue also applies to the rating-derived tag suggestions section. Preferred
   suggestions and Block suggestions should be separately visible without forcing the user to scroll
   through a long Preferred suggestion list before seeing Block suggestions. A future pass should
   inspect `TasteSuggestionsContent` in `RecommendationSettingsSharedComponents.kt` and apply the same
   capped/expandable grouped presentation there: show a small initial set for Preferred suggestions
   and a small initial set for Block suggestions, with Show more / Show fewer / Show all behavior
   where needed.

## v0.8.11 planned audit line

The next major KMK-Recs line should be `v0.8.11`, not another `v0.8.10-fixN`, because the remaining
work is a broad UI/navigation/information-architecture pass rather than a narrow bug fix. Start with:

- `docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_AUDIT_PLAN.md`
- `docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_IMPLEMENTATION_PLAN.md`

The audit evidence has now been converted into an executable implementation plan. It defines exact
screen-level, component-level, file-level, and test-level changes for Recommendation Settings
naming/reordering, Komikku settings-widget conformance, Taste Suggestions grouping, For You top-bar
clutter, Source Evaluation, Sources To Try, Source Priority, structural interaction/functionality auditing, rated-manga multi-select grouping, and search-index consequences. It keeps
the For You false-no-matches/recommendation-quality issue as a separate functional track unless the
user explicitly approves combining it.

## Post-fix8 resumption priorities

The v0.8.10-fix8 APK has been confirmed by the user on device to resolve the repeated Browse / For You
/ source / recommendation crash family. Before starting another broad implementation, use
`docs/community/KMK_POST_FIX8_SOURCE_RUNTIME_AND_CLAUDE_WORKFLOW_RETROSPECTIVE.md` to keep future
plans focused and main-session oriented.

Recommended next evaluation order:

1. **Historical KMK What's New formatting** � current code preserves pre-v0.8.9 entries in their
   original flat format by design, but the user later requested all historical entries to follow the
   cleaner Komikku-style New/Improve/Fix structure. This requires a lossless conversion plan and
   tests proving no version entry or meaning was dropped.
2. **UI/conformance cleanup across KMK-added surfaces** � resume only after Codex converts broad
   wording such as "match official Komikku UI" into exact screen/component/file-level tasks.
3. **Remaining source-runtime hygiene** � `SuwayomiApi.kt` safe-client type mismatch,
   fatal-`Throwable` over-suppression in `MigrateMangaUseCase.kt` / `LibraryUpdateJob.kt` /
   `MetadataUpdateJob.kt`, and optional manual retry/bypass semantics for runtime suppression.
4. **Manual QA / release readiness** � record real-device checks for the fixed crash family and
   high-risk reader/OCR/recommendation/source-evaluation flows before any wider sharing.

Current handoff plan for items 1-3:

- `docs/community/KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_PLAN.md`

Future follow-up, not part of fix9:

- **For You recommendation-quality audit** � user reports that For You still feels too strict or
  mismatched even after Source Evaluation improved. Sharpened example: the affected source can be opened
  directly and visibly contains many manga matching the user's taste, but Recommendation Settings /
  For You source status reports the affected source as **no matches at all**, not merely weak or low-ranked
  matches. Other sources may also surface generic or weak-feeling results. Treat this as a false
  no-matches candidate-discovery/query-policy/filtering issue first, and only secondarily as a
  ranking issue. A future plan should inspect
  `BrowsePersonalRecommendationsScreenModel`, `RecommendationQueryAttemptPolicy`,
  `RecommendationCandidateVisibilityPolicy`, `PersonalRecommendationScorer`,
  `recommendation_candidate_memory`, and source-specific Popular/Latest/search paths to determine
  whether For You is over-relying on strict tag/title searches, failing to use useful catalogue
  samples, declaring no matches after only one unsupported/empty query strategy, filtering good
  candidates too early, failing to continue into Popular/Latest/page discovery for sources whose
  search compatibility is poor, or ranking generic matches above stronger visible catalogue matches.
  Additional screenshot evidence from the Browse > For You tab shows row-level failures for multiple
  sources: Manhwatop and ManhuaTop display raw
  `UninitializedPropertyAccessException: lateinit property url has not been initialized`; Drake Scans
  displays raw `RecoverableSourceRuntimeException: Source "Drake Scans" (en) is temporarily
  unavailable after a recent recoverable failure (Search)`. A future fix should classify these into
  localized, non-raw row states, determine whether the `lateinit url` rows come from malformed source
  manga results before URL validation/localization, and decide whether temporarily suppressed source
  rows should show a short "temporarily unavailable, retry later" message or be hidden behind
  diagnostics. Do not change this during the v0.8.10-fix9 UI/conformance pass.
- **Historical note: Komikku v1.14.1 reconciliation was later completed.** At the time of
  v0.8.10-fix9 this was future work, but it was completed in the v0.8.17 upstream-reconciliation pass.
  Do not re-plan v1.14.1 unless a new regression is found; use the v0.8.17 implementation report for
  the actual file-by-file reconciliation.

---

## v0.8.10-fix8 (complete)

Two related fixes: added the missing `okhttp-zstd` OkHttp artifact to `gradle/libs.versions.toml`'s
`okhttp` bundle (AsuraScans' lazy client references `okhttp3.zstd.Zstd`, which was never on the
classpath), and made `SourceRuntimeFailureRegistry` suppression *enforced* rather than advisory --
`SourceRuntime.run()`/`runBlockingSourceCall()` now short-circuit to
`Result.failure(SourceTemporarilyUnavailableException(...))` before ever touching an already-confirmed-
broken source again, instead of only recording the failure for display purposes. See
`docs/community/KMK_RECS_V0_8_10_FIX8_ZSTD_AND_RUNTIME_SUPPRESSION_IMPLEMENTATION.md` for the full
report, the corrected-test rationale, and the final APK hash.

**Remaining follow-up, not yet done:**
1. `SuwayomiApi.kt`'s `source.client` read remains unguarded (from fix6) -- `safeClientOrNull()`
   returns `Call.Factory`, not the `OkHttpClient` this call site needs; a future pass should add an
   `OkHttpClient`-returning variant or confirm `Call.Factory` suffices.
2. `MigrateMangaUseCase.kt` and `LibraryUpdateJob.kt`/`MetadataUpdateJob.kt` still use a bare
   `catch (e: Throwable)` that silently swallows genuinely fatal VM errors, not just recoverable source
   failures (carried over from fix7, unaddressed by fix8 -- out of this pass's scope).
3. **New from fix8**: `SourceRuntimeFailureRegistry`'s 60-second `SUPPRESSION_WINDOW_MS` is a single
   global constant; a future pass could consider whether some operations (e.g. a one-off manual retry
   from the Source Evaluation recovery UI) should be able to bypass suppression explicitly rather than
   only via `clear(sourceId)`.
4. Real-device QA (the 6-step checklist in the fix8 report) is still required before this line can be
   considered release-verified -- no `adb`/device access exists in this environment.

---

## v0.8.10-fix7 (complete)

A newer real-device log showed the AsuraScans `okhttp3.zstd.Zstd` `NoClassDefFoundError` could still
crash Browse/For You/recommendation-related paths even after fix6 -- not because a call site skipped
`SourceRuntime`, but because the boundary correctly recorded the recoverable `LinkageError` and then
the caller immediately rethrew that exact raw `Error` via `Result.getOrThrow()`, past any outer
`catch(Exception)`-only path. Added `RecoverableSourceRuntimeException`/
`getOrThrowSourceRuntimeException()` and migrated the plan's 4 named locations plus 6 more genuine
holes found by the required Step 3 audit of every remaining `getOrThrow()` call site (`MergedSource.kt`
x2, `BrowseSourceScreenModel.kt`, `MangaScreenModel.kt`, `MigrationListScreenModel.kt` x3,
`GalleryAdder.kt`). See
`docs/community/KMK_RECS_V0_8_10_FIX7_SOURCE_RUNTIME_RETHROW_CONTAINMENT_IMPLEMENTATION.md` for the
full report, audit table, and final APK hash.

**Remaining follow-up, not yet done:**
1. `SuwayomiApi.kt`'s `source.client` read remains unguarded (from fix6) -- `safeClientOrNull()`
   returns `Call.Factory`, not the `OkHttpClient` this call site needs; a future pass should add an
   `OkHttpClient`-returning variant or confirm `Call.Factory` suffices.
2. **New from fix7**: `MigrateMangaUseCase.kt` and `LibraryUpdateJob.kt`/`MetadataUpdateJob.kt` all use
   a bare `catch (e: Throwable)` that silently swallows genuinely fatal VM errors (`OutOfMemoryError`,
   etc.), not just recoverable source failures -- the opposite problem from a crash (over-suppression),
   but still worth a dedicated pass to rethrow fatal errors while keeping per-item isolation for
   recoverable ones.
3. Real-device QA (the 10-step checklist in the fix7 report) is still required before this line can be
   considered release-verified -- no `adb`/device access exists in this environment.

---

## v0.8.10-fix6 (complete)

A newer real-device crash log confirmed the AsuraScans/KaynScans `okhttp3.zstd.Zstd`
`NoClassDefFoundError` still reached the app through call sites fix3/fix4/fix5 had not yet migrated:
`getFilterList()` in `SourceFeedScreenModel.kt`/`FeedScreenModel.kt`, migration/smart-search
(`SmartSourceSearchEngine.kt`), the `RecommendationSource` delegate wrapper
(`RecommendationPagingSource.kt`), and WebView source-header reads in **two** separate files
(`WebViewScreenModel.kt`, `WebViewActivity.kt`). The required Task 6 re-audit also found and fixed a
genuine hole not named in the plan: `HttpPageLoader.kt`'s `getPages()` cache-miss fallback wasn't
actually covered by its surrounding `catch(Throwable)`. All fixes reuse the existing `SourceRuntime`
boundary -- no second classifier. See
`docs/community/KMK_RECS_V0_8_10_FIX6_REMAINING_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` for the
full report, audit table, and final APK hash.

**Remaining follow-up, not yet done:** `SuwayomiApi.kt`'s `source.client` read remains unguarded --
deferred because `safeClientOrNull()` returns `Call.Factory`, not the `OkHttpClient` this call site
needs, and the plan itself flagged this type mismatch as uncertain rather than prescribing a fix. A
future pass should either add an `OkHttpClient`-returning safe accessor variant or confirm
`Call.Factory` is sufficient for Suwayomi's actual usage before touching it. Real-device QA (the
10-step checklist in the fix6 report) is still required before this line can be considered
release-verified -- no `adb`/device access exists in this environment.

---
## v0.8.10-fix5 (complete)

Live-device evidence after `v0.8.10-fix4` shipped showed the AsuraScans `okhttp3.zstd.Zstd`
`NoClassDefFoundError` still reached the app when opening For You, Browse, or manga recommendations --
now from a category fix3/fix4 did not cover: `HttpSource.client`/`HttpSource.headers` lazy-property
reads and page-preview image fetches in `MangaCoverFetcher.kt`/`PagePreviewFetcher.kt` (Coil cover/
preview loading), not any `SourceRuntime`-guarded source *method*. Fix5 added
`SourceRuntimeOperation.Client`/`Headers`/`CoverImage`/`PreviewImage`, new safe accessors routing
through `SourceRuntime.runBlockingSourceCall`, migrated both Coil fetchers, added a
`SourceRuntimeHealthReporter` + a non-blocking Source Evaluation diagnostics warning with Retry/
Update/Reinstall/Uninstall/Disable recovery actions (each gated to only appear when actually valid),
and applied the optional Phase 7 proactive-skip to `CrossExtensionGenreSearchSource` (batch-context
only). See
`docs/community/KMK_RECS_V0_8_10_FIX5_SOURCE_CLIENT_HEALTH_AND_RECOVERY_IMPLEMENTATION.md` for the full
report, including 3 call-site families explicitly reviewed and deferred with documented reasons, the
`HttpSource`/`ExtensionManager` unit-test-construction limitation, and the final APK hash.

**Remaining follow-up, not yet done:** the deferred Phase 7 proactive-skip candidates
(`SourceEvaluationRunner.kt`/`SourceRecommendationFitProbe.kt` iterate by extension not source id --
needs a source-id-aware restructuring to integrate cleanly; `BrowsePersonalRecommendationsScreenModel.
searchSource()`'s cache-first path needs a more careful insertion point than this pass's budget
allowed). Real-device QA (the 10-step checklist in the fix5 report) is still required before this line
can be considered release-verified -- no `adb`/device access exists in this environment.

---
## v0.8.10-fix4 (complete)

Live-device verification after `v0.8.10-fix3` showed the AsuraScans `okhttp3.zstd.Zstd` `NoClassDefFoundError` still reached `GlobalExceptionHandler`/`CrashActivity` when opening For You, manga recommendations, Browse/source screens, and For You settings. The confirmed root cause was `BrowseSourceScreenModel.kt`'s `init` block calling `source.getFilterList()` with no try/catch at all -- not the "lower risk" deferral fix3's report characterized it as. See
`docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` for the
full call-site inventory, the `RecommendsScreenModel.kt`/`RecommendationSearchHelper.kt`
polymorphic-PagingSource discrepancy from the plan's implied shape, the sections 7-8 re-check
findings (HttpPageLoader/Downloader/ExtensionManager already safely isolated, no changes needed),
new sibling-isolation tests, and the final APK hash.

---
## v0.8.10-fix3 (complete)

Structural source-runtime isolation is complete. See
`docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` for the
full call-site inventory, the confirmed `app`?`data` module-dependency-direction blocker and its
resolution (pure classifier functions relocated to `core:common`), tests, and verification.

**Genuinely open follow-up from this pass** (documented, not silently dropped):

- `BrowseSourceScreenModel.kt`'s remaining direct `getFilterList()` calls (UI-state-building, not the
  paging load itself) were intentionally deferred as lower-risk and not a confirmed crash site. If a
  future crash report implicates one of these specific calls, migrate them the same way as the rest
  of this pass.
- Real-device QA with the previously-broken Asura Scans extension still installed was **not**
  performed (no `adb`/device access in this environment, confirmed empty via `adb devices`) � this
  is the standing verification gap for fix1/fix2/fix3 alike and must be done before any public
  release.

---

## v0.8.10-fix1 (in progress)

Full Komikku UI/architecture conformance pass plus mandatory application-wide crash investigation and
lossless historical What's New conversion. See
`docs/community/KMK_RECS_V0_8_10_FIX1_FULL_KOMIKKU_CONFORMANCE_AND_STABILITY_IMPLEMENTATION_PLAN.md`
(plus its two addenda) for the full gated Phase 0-8 requirements. Do not treat any phase as complete
until its own report section says so � this plan explicitly overrides the v0.8.10 Phase G "keep
historical entries as-is" decision.

Date: 2026-07-09 (updated: 2026-07-17 -- v0.8.10 corrective/completion release shipped: Phases A-I of
`docs/community/KMK_RECS_V0_8_10_0_8_9_COMPLETION_AND_1_14_VALIDATION_IMPLEMENTATION_PLAN.md` complete
and verified; see `docs/recommendations/CURRENT_STATE.md`'s "v0.8.10 phase map" for the full A-J
breakdown. Phase J (device/accessibility/release verification) remains an open, disclosed blocker --
not executable in this environment, must be done manually before any public release. Previously
updated: 2026-07-12 -- v0.8.1-fix2 shipped, version visibility + sync validation)

Status: v0.7 feature line is closed as of v0.7.45; v0.7.46/v0.7.47 were follow-up correctness/hygiene
passes; v0.8.0 was the first private v0.8 feature build (Rated Manga management); v0.8.1-fix1 was a
private corrective/polish follow-up fixing gaps found in v0.8.0/v0.7.47; v0.8.1-fix2 is a further
private corrective follow-up fixing a Loved Manga crash introduced by v0.8.1-fix1's group-primary
interactors (missing Injekt registration), a version-visibility gap, and a sync-validation gap. The
entire v0.8.x line is private-only — no public release. Open items only. All polish phases A through J are shipped. v0.7.35 (Rated Manga entry points + group-seeded recommendations) shipped. v0.7.36 (Rated Manga UI parity, Library shortcuts, crash fix, discoverability) shipped. v0.7.37 (group-seeded recommendation bounded runtime, source-aware dedup, cross-source rating exclusivity) shipped. v0.7.38 (For You candidate discovery memory, additional page discovery, group seed enrichment from all linked versions, GroupSeedRecommendationScorer, Reset discovery history) shipped. v0.7.39 (For You rolling discovery progress table, empty/filtered/error pages no longer retried, cumulative 20-page cap, progress cleared on history reset) shipped. v0.7.40 (same-refresh discovery merge fix, typed discovery retry policy with exponential backoff, shared RecommendationSourceSelector adopted in group flow, shared RecommendationCandidateVisibilityPolicy adopted in both flows) shipped. v0.7.41 (discovery policy corrections: single visibility contract across live/cache/memory/group with min-chapter parity, group budget counts only visible results, truthful STATUS_EXHAUSTED retry state + due-page-20/no-page-21 planner fix, conservative unknown-error classification, cancellation never recorded) shipped. v0.7.41 known-context follow-up (extra-page discovery now uses the real hide-known context, matching live/cache/memory/group) shipped. v0.7.42 (Source Evidence Redesign: catalogue-only Source Evaluation scoring, shared PersonalRecommendationScorer taste matching, catalogue metadata confidence, fail-open rec-fit eligibility, staleness parity, honest "For You search" UI labeling) shipped. v0.7.42-fix1 (corrective follow-up: manual queue/diagnostics now share the same eligibility/staleness contract as automatic evaluation; misleading `search 0%` row subtitle replaced with catalogue metadata confidence) shipped. v0.7.42-fix2 (second corrective follow-up: one shared `SourceRecommendationFitDisplayPolicy` now drives queue buckets, diagnostics, row labels, sorting, and targeted rechecks; retired the unusable Search Reliability sort in favor of For You Compatibility; added a targeted Recheck Outdated action; stale fits can no longer display as current) shipped. v0.7.43 (background For You search compatibility job separate from Source Evaluation; Loved/Liked group recommendations replaced with the row-based manga-detail Recommendations pipeline seeded by every confirmed linked version; "Seen" renamed "Not interested" with a mild negative scoring signal in For You, storage unchanged) shipped. v0.7.44 (fixed the 3 pre-existing recommendation test failures for good, plus 2 latent ones; new shared strict-to-lenient query-attempt policy used by both group rows and For You; group recommendations now use the whole linked group's tags/titles, honor source-selection and candidate-visibility policy, and filter out near-zero-relevance candidates; Source Evaluation/Settings phone UI density pass) shipped. Do not implement any item without explicit user approval and a focused implementation plan.

---

## KMK Upstream 1.14.0 Reconciliation (2026-07-17)

**Status:** Complete, verified, not a KMK-Recs feature release. App `versionName`/`versionCode`
bumped to 1.14.0/89 in `app/build.gradle.kts`; KMK-Recs feature label unchanged at v0.8.9. See
`docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md` for the full report.

Reconciled the whole fork against the official Komikku v1.14.0 tag across 9 phases. Directly
relevant to this recommendation system: confirmed (not assumed) that the What's New renderer, all
historical entries plus v0.8.9, the Recommendation Settings search index's 7 category
destinations, ranked-matching/synonym/punctuation-normalization behavior, and the entire
`exh/recs/**` pipeline all survived untouched except for a single mechanical
`CatalogueSource`?`Source` type-widening pass (source-API contract change, no behavior change).

**One real follow-up surfaced, not part of the recommendation system:** `BackupDecoder.decode()`
crashes (uncaught `IndexOutOfBoundsException`) on a truncated-but-well-formed backup file instead
of showing the "invalid backup file" message. Confirmed pre-existing (byte-identical between
v1.13.6 and v1.14.0), not caused by this reconciliation. Flagged as a separate background task,
not fixed here.

---

## v0.8.9 � OFFICIAL-STYLE WHAT'S NEW STRUCTURE, RECOMMENDATION SETTINGS SEARCH

**Status:** Both features implemented and tested (2026-07-16); no physical device QA performed. See
`KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_IMPLEMENTATION.md` for full detail.

**Done:**
- What's New: confirmed the existing renderer (official `MarkdownRender`/`GFMFlavourDescriptor`)
  already fully supports the official New/Improve/Fix structure � no renderer changes needed. Added
  the v0.8.9 entry in that structure. Every one of the 76 pre-existing historical entries remains
  individually intact.
- Recommendation Settings search: new parallel, pure, ranked search index (7 category-level entries
  covering For You, Source Priority, Taste/Tags, Source Evaluation, Sources To Try, Installer/
  Background, Diagnostics) plus a search screen mirroring the official Settings search UI shape,
  reachable from a new search action in the Recommendation Settings index top bar.

**Not done � follow-up work:**
- Retroactive New/Improve/Fix reformatting of the 76 historical What's New entries � left in their
  original format, a large content-only rewrite judged disproportionate to this pass.
- Per-control search entries with stable in-screen anchors (currently category-level only � every
  result still navigates to the correct screen, just not a specific row within it).
- Robolectric/Compose-UI test infrastructure � still absent; What's New rendering and search UI
  interaction are verified by code inspection and pure-logic tests only.
- Device QA (What's New phone/tablet layout and TalkBack, search phone/tablet/dark-light/TalkBack/
  large-font/rotation) � none of this was executed, no physical device was available.

---

## v0.8.8 � SCHEDULE ENFORCEMENT, RATING PROMPT, SETTINGS INDEX, EVALUATION RECONCILIATION

**Status:** All four items implemented and tested, including the Phase B full settings split
(2026-07-16, gap-closing pass); no physical device QA performed. See
`KMK_RECS_V0_8_7_FIX1_AND_V0_8_8_IMPLEMENTATION.md` for full detail. Ships v0.8.7-fix1 and v0.8.8
together as one release.

**Done:**
- Reading-schedule enforcement actually exists now (it previously did not � restriction was toast-only).
  Session-bound `ReaderScheduleEntitlement`, real gates at every chapter-load entry point, full-screen
  block overlay.
- Chapter-completion rating prompt (latest chapter only, deduplicated, reuses existing exclusive
  rating + cross-extension matching, schedule-aware).
- Recommendation Settings fully split into per-category screens (gap-closing pass): all seven index
  rows now route to distinct destinations � Evaluation and Background/Installer both to
  `SourceEvaluationScreen` (installer settings have no content elsewhere � a confirmed structural
  fact), the other five each to a new dedicated screen extracted verbatim (zero behavior change) from
  the former single `RecommendationsSettingsScreen`, which has been deleted. Scroll-to-section is no
  longer relevant now that each category is its own bounded screen.
- Outdated-evaluation reassessment: traced and fixed the real root cause (display-label vs.
  work-queue eligibility mismatch, not a literal count/work query pair) with a reconciliation policy
  and an explanatory UI banner; 250-synthetic-source test suite proves count/work agreement and
  correct multi-batch continuation.

**Not done � follow-up work:**
- Per-extension exclusion-reason breakdown in the outdated-reconciliation UI (currently aggregate
  counts only).
- Robolectric/Compose-UI test infrastructure � `ReaderViewModel`, `SourceEvaluationScreenModel`, and
  the new Compose screens/dialogs remain untestable end-to-end in this repo's current test setup.
- Device QA (schedule blocking on-device, rating prompt at real chapter boundaries, settings
  navigation on phone/tablet across all seven new/updated screens, multi-batch Source Evaluation
  continuation) � none of this was executed, no physical device was available.

---

## v0.8.7 � READING SCHEDULE FIX COMPLETE; RATED UI/SETTINGS REFINEMENT PARTIAL

**Status:** Reading Schedule dialog root-cause fix fully implemented and tested (2026-07-15). Rated
UI/Recommendation Settings refinement partially implemented � see
`KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION.md` for full detail.

**Done (Reading Schedule):** unsafe `MainActivity` cast replaced with a `ContextWrapper`-unwrapping
`findActivity()`; visible error instead of silent no-op on missing Activity; device 12h/24h time
format; explicit whole-day window flag with backward-compatible serialization; real in-place Edit;
Save/Cancel/outside-dismiss consistency (only Save persists).

**Done (Rated UI/Settings, across two passes):** "Select all in group" added to the Rated Manga
bulk-selection bottom bar; state-derived summaries added to 6 of 9 Recommendation Settings section
headers (Daily recommendations, Ratings and Known Manga, Tags, Source Priority, Same-Manga Matching,
Sources To Try); Undo (Snackbar, reusing the existing `LibraryTab.kt` pattern) added for Clear Rating
and Mark Not Interested; Source Priority's action layout, Source Evaluation, Sources To Try, and
group management (`LinkGroupManagementScreen`) audited and found already compliant.

**Not done � follow-up work:**
- Expand/collapse for Recommendation Settings sections � attempted and explicitly declined: the
  Source Priority section's drag-and-drop reorderable list made this unsafe to verify without a
  physical device. Controls in all 9 sections still always render.
- Summaries for the remaining 3 Recommendation Settings sections (Source Evaluation, Management,
  Experimental).
- Undo for Merge Selected Into Group, Remove From Group, and Ungroup (needs a link-group-graph
  snapshot/restore, not just a rating/flag value).
- Source Priority per-row quality-dislike/explicit-block/reset/details actions � this is new
  functionality (not currently wired per-row at all), not a reorganization of existing actions.
- A deeper line-by-line audit of cross-extension matching and the Best Version workflow screens
  specifically (not yet done in either pass).
- Device QA (phone/tablet layout for both the schedule dialog and the rated/settings screens, 12h/24h
  display, TalkBack) � none of this was executed, no physical device was available.

---

## v0.8.6 � CODE COMPLETE, MANUAL QA PENDING (group recommendation search performance and loading)

**Status:** Implemented and unit-tested across two passes in one session (2026-07-15); no physical
device QA performed. See `KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_LOADING_IMPLEMENTATION.md` for full
detail.

**Done:** `GroupPreviewBudgetPolicy` + `SourcePreferences.groupPreviewResultBudget()` (initial
per-extension group-recommendation preview budget, 5/10/15/20/30, default 10, exposed as "Initial
results per extension" in Recommendation Settings); `RecommendationLoadContext` enum;
`RecommendsScreenModel` now bounds group-preview concurrency to 4 and applies a 20s per-row timeout
via the extracted, unit-tested `GroupPreviewLoadCoordinator`; fixes a `CancellationException`-
swallowing bug; truncates to the configured budget in the correct post-scoring order; runs on
`screenModelScope` instead of a process-wide scope (so leaving the screen actually cancels in-flight
work); carries a `GenerationGuard`-backed staleness token; looks up `GroupPreviewCache` before every
GROUP_PREVIEW fetch and populates it after budget truncation (wired into the load path, not just
built in isolation); and logs cache hit/miss, per-row counts/timing, and a per-load
elapsed-time/max-concurrency summary via the existing `logcat` abstraction.

**Not done � follow-up work:**
- Add a real refresh/reload entry point to `RecommendsScreenModel` (there isn't one today) so
  `GenerationGuard` is exercised by a genuine second generation in production, plus an end-to-end
  test for it.
- Build a fake-dependency test harness for `RecommendsScreenModel` itself so cache-wiring, per-row
  error/timeout rendering, and multi-row state merging can be verified end-to-end (currently only the
  extracted coordinator/guard logic is directly tested; the screen model's own integration is verified
  by code inspection and the passing regression suite, not a dedicated test).
- Device QA (phone+tablet layout, budgets 5/10/20/30 visually, heavy-tag groups, repeated refresh,
  leaving mid-load, network loss/recovery, source expansion, confirming global search stays uncapped,
  confirming the cache actually avoids a redundant network call on-device) � none of this was
  executed, no physical device was available.

---

## v0.8.2-v0.8.5 � SHIPPED (For You results budget, Recommendation Settings reorganization, active-reading timer, optional reading schedule)

**Status:** COMPLETE (2026-07-15). Internal/private handoff build; no public release prepared.
Implemented as one coordinated session per `KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md`
and its four phase plans:

1. **v0.8.2**: `ForYouResultBudgetPolicy` + `SourcePreferences.recommendationResultBudget()` � a
   user-configurable 5/10/15/20/30 (default 10) visible-card budget per ordinary For You source row,
   included in the cache fingerprint, boosted-row floor preserved at 20. No change to source count,
   priority, enrichment, Source Evaluation, Top Picks, or query-attempt limits.
2. **v0.8.3**: Recommendation Settings reordered into For You behavior / Source priority / Source
   evaluation / Source management / Discovery-cache-management sections. Most of this phase's other
   requirements (shared rated-collection UI, non-pinned quarantine controls, For You shortcuts) were
   already satisfied by prior sessions.
3. **v0.8.4**: New `eu.kanade.tachiyomi.ui.reader.timer` package � pure `ReaderTimerReducer` state
   machine (IDLE/RUNNING/PAUSED/CHAPTER_GRACE/EXTRA_CHAPTER_GRACE/EXPIRED), monotonic-clock based,
   `SavedStateHandle`-persisted, lifecycle-bound to `ReaderViewModel`/`ReaderActivity`. Reader
   bottom-bar "Reading timer" icon and dialog (presets/custom duration, warnings, grace options).
4. **v0.8.5**: New `eu.kanade.tachiyomi.ui.reader.schedule` package � pure `ReaderScheduleResolver`
   (day/time windows, midnight-crossing support, ALLOWED/RESTRICTED modes). Reuses the timer's own
   grace mechanism via a second independent `ReaderTimerCoordinator` instance rather than adding
   schedule branches to the reducer. New "Reading schedule" section in Settings > Reader.

Post-review corrections applied within the same v0.8.5 build (no version bump): manual/previous-
chapter navigation no longer consumes the timer's one-extra-chapter allowance (only natural forward
progression does); the schedule editor now supports an add/delete list of multiple recurring
windows using the repository-standard `MaterialTimePicker`, not a single manual-text-entry window;
the in-app What's New history now shows v0.8.5/v0.8.4/v0.8.3/v0.8.2 as four separate entries instead
of one collapsed v0.8.5 paragraph.

Known limitations: no persisted default timer configuration across sessions; schedule-grace session
state isn't persisted across process death (re-derived correctly on next evaluation instead). No
manual on-device QA was possible in this environment for reader-lifecycle-dependent behavior �
recorded as unavailable, not claimed as verified.

See `docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md`
for the full file list, test results, and deviations.

---

## v0.8.1-fix4 — SHIPPED (Final cleanup + source/library-quality dislike)

**Status:** COMPLETE (2026-07-12). Internal/private handoff build; no public release prepared.
Implemented per `KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_PLAN.md`:

1. **New source/library-quality preference axis**, separate from the existing For You/recommendation
   like-dislike axis: `SourcePreferences.likedSourceQualityKeys()` / `dislikedSourceQualityKeys()` /
   `explicitSourceQualityKeys()`, driven by a new pure `SourceQualityMarkPolicy` (mark poor, mark
   explicit, clear) built on the existing `RecommendationSourcePreferenceStore` key format/serializer.
2. Source-quality-disliked sources are now excluded from `NonInstalledSourceSuggestionScorer`
   (Sources To Try) and `SourceEvaluationCandidateFilter.buildPool()` (Source Evaluation candidates),
   with a separate `sourceQualityHiddenCount` diagnostic distinct from recommendation-dislike hidden
   count. Installed sources marked poor/explicit are also excluded from For You/grouped
   recommendation source selection in `BrowsePersonalRecommendationsScreenModel`/`RecommendsScreenModel`.
3. Past Source Evaluation rows for a since-marked source are hidden by default (never deleted) behind
   a "Show disliked sources" toggle, mirroring the existing "Show installed" pattern.
4. Recovery: "Clear source mark" per-row, "Clear source quality marks" as a bulk management action.
5. v0.8.1-fix3 cleanup: removed all app-facing private/public/internal/test-build wording from
   `KmkRecsReleaseNotes` and `kmk_recs_updated_body`; normalized malformed `<!-- KMK --> vX.Y: ... -->`
   XML comments in `strings.xml`; added a state-derived "Outdated reassessment complete" message for
   the stale/outdated Source Evaluation queue.

See `docs/recommendations/KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_IMPLEMENTATION.md`
for the full file list, test results, and deviations.

---

## v0.8.1-fix2 — SHIPPED (Version visibility + sync validation, private corrective follow-up)

**Status:** COMPLETE (2026-07-12). Private-only build; no public release prepared. Implemented per
`KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_PLAN.md`, fixing a crash and two
smaller gaps found in v0.8.1-fix1:

1. **Loved Manga crash fixed.** `GetCrossSourceGroupPrimary`/`SetCrossSourceGroupPrimary`/
   `ClearCrossSourceGroupPrimary` (added v0.8.0) were never registered in `KMKDomainModule`,
   causing `InjektionException: No registered instance or factory for type class
   tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary` whenever `LovedMangaScreenModel`,
   `LinkedVersionListScreenModel`, `TasteBackupCreator`, or `TasteRestorer` was constructed. Fixed
   by adding the three missing factory registrations.
2. **KMK-Recs What's New visibility verified and made explicit.** New pure `KmkRecsWhatsNewPolicy`
   names the show/sequence/mark-seen decisions `MainActivity.kt` already made inline; confirmed the
   Komikku-changelog/KMK-changelog `if/else if` structure already prevents both dialogs from
   showing at once (Compose recomposes reliably once `showChangelog` flips false) — this was
   clarified and tested, not restructured. Fixed a minor side-effect anti-pattern in
   `KmkRecsWhatsNewScreen` (mark-seen now runs in `LaunchedEffect`, not the composable body
   directly). Added a one-line body to the compact update dialog.
3. **Group-primary sync validation hardened to match restore.**
   `SyncService.mergeCrossSourceGroupPrimariesPure()` now filters invalid rows via
   `CrossSourceGroupPrimaryRestorePolicy.isValid()` (blank `groupId`, `source == 0L`, blank `url`)
   instead of only filtering blank `groupId`.

See `docs/recommendations/KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_IMPLEMENTATION.md`
for the full file list, test results, and deviations.

---

## v0.8.1-fix1 — SHIPPED (Rated Manga + Source Evaluation polish, private corrective follow-up)

**Status:** COMPLETE (2026-07-12). Private-only build; no public release prepared. Implemented per
`KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_PLAN.md`, fixing five gaps found
after v0.8.0/v0.7.47:

1. `LinkedVersionListScreen`'s remove-from-group action now requires confirmation (previously
   called `removeFromGroup()` directly with no dialog).
2. `manga_cross_source_group_primary` is now backed up/restored/synced (proto 627,
   `BackupCrossSourceGroupPrimary`, `TasteRestorer.restoreCrossSourceGroupPrimaries()`,
   `SyncService.mergeCrossSourceGroupPrimariesPure()`) — previously durable user data with zero
   backup/sync coverage.
3. The app-bar "Select" action in Loved/Liked/Disliked now enters selection mode without
   auto-selecting the first visible item (`RatedSelectionReducer.enterEmpty()`) — previously it
   silently selected item #1, a bulk-action safety gap.
4. Documentation and version-list UI now clearly state that "Set Primary Version" lives inside
   `LinkedVersionListScreen` only, not as a direct rated-item-menu action (a hint string was added
   to the version list; no second primary picker was built).
5. Source Evaluation rows gained an expandable "Details" section
   (`SourceEvaluationEvidenceSummaryPolicy`) surfacing the v0.7.47 enrichment/evidence counters that
   were computed but never shown: enriched X/Y samples, metadata C/S samples, liked/disliked/
   blocked/adult-risk counts, and the manual-review explanation for `NEEDS_MANUAL_REVIEW`.

See `docs/recommendations/KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_IMPLEMENTATION.md`
for the full file list, test results, and deviations.

---

## v0.8.0 — SHIPPED (Rated Manga bulk selection + group actions, private feature)

**Status:** COMPLETE (2026-07-12). Implemented per
`KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_PLAN.md`: long-press in Loved/Liked/
Disliked now enters bulk selection instead of opening recommendations (a top-right "Select" action
provides the same entry point for discoverability); a phone-friendly bottom action bar (Change/
Clear/Group/More) and a per-item action menu (Recommendation/Rating/Group sections) were added;
"See group recommendations" reuses the existing `RecommendsScreen.Args.CrossSourceGroupSeed` flow
unchanged and is gated on a confirmed linked group with 2+ versions; "Find other versions" and
"Favorite other versions" reuse the existing `CrossExtensionMatchScreen` Rating/Favorite modes; a
new focused `LinkedVersionListScreen` shows every version in a group (source/language/title/rating/
favorite/installed-missing/updated/primary) loaded directly from persisted group data; a new
additive `manga_cross_source_group_primary` table (migration 62) lets the user pick which version
controls the rated-list cover/title, independent of `manga_cross_source_link`'s own lifecycle, with
fail-open fallback if the stored primary is missing; group merge is manual-selection-only
(`RatedGroupMergePlanner`, never merges by title); Clear/Merge/Remove-from-group/Ungroup/Mark-not-
interested all require confirmation; clearing a rating never touches cross-source link rows.
~~Known limitation: the new primary-version table is not yet included in backup/sync.~~ **Fixed in
v0.8.1-fix1** (see above). See
`docs/recommendations/KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_IMPLEMENTATION.md`
for the full file list, test results, and deviations.

---

## v0.7.47 — SHIPPED (Source Evaluation tag enrichment and scoring fix)

**Status:** COMPLETE (2026-07-12). Fixes the root-cause evidence-pipeline defect found in
`KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md`: catalogue-fit scoring never enriched
Popular/Latest samples missing genre tags, so sources were often being scored on "does the list
page expose tags?" instead of actual taste fit. Implemented exactly per
`KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md`: bounded `getMangaDetails()`
catalogue enrichment (`SourceEvaluationCatalogueEnricher`, cap 12, 10s timeout, cancellation-safe,
no chapter/page fetches, evidence-only — never written to the app manga table); split
positive/negative/blocked/adult/metadata evidence counters (migration 61, 9 new additive columns);
a ratio-based fit-score formula and revised verdict order that routes metadata-sparse evidence to
`NEEDS_MANUAL_REVIEW` instead of a confident `WEAK`, and blocks noisy/adult/blocked-tag-heavy
sources from reaching `STRONG_FIT`; `SourceEvaluationKeys.CURRENT_VERSION` bumped `2 -> 3` so every
existing row is stale; a new `SourceEvaluationDisplayPolicy` for stale-row labeling/ranking; and a
new `STALE_EVALUATION` result on `SourceRecommendationFitEligibility.check()` so a stale catalogue
row can no longer feed the search-compatibility queue as if current. Catalogue fit and For You
search compatibility remain fully separate signals — no change to `SourceRecommendationFitProbe` /
`SourceRecommendationFit`. No source-specific hacks were added. See
`docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_IMPLEMENTATION.md` for
the full file list, migration number, and test results.

---

## v0.7.46 — SHIPPED (public polish closeout)

**Status:** COMPLETE (2026-07-12). Follow-up to v0.7.45 closing the specific gaps its own
implementation report flagged: OCR errors and per-page failure rows now use a stable, KMR-localized
`OcrErrorClassifier` instead of raw exception text; OCR logcat no longer includes manga
title/chapter name; per-chapter/per-manga OCR clearing (already existed in the repository) is now
reachable from the OCR search UI; a second raw-exception-to-UI path in Source Evaluation and three in
Best Version comparison were classified via a new shared `RecommendationErrorClassifier`;
`RECOMMENDATION_VERSIONING.md`'s missing v0.7.45 entry was filled in; `docs/recommendations/README.md`
mojibake cleaned; the deferred feature master plan marked historical. No new recommendation behavior,
no schema/backup changes, OCR remains included in both build lines. See
`docs/community/KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_IMPLEMENTATION.md` for full detail including honest
test/lint/manual-QA status.

---

## v0.7.45 — SHIPPED (final v0.7 closure + public release readiness pass)

**Status:** COMPLETE (2026-07-12). This is the final v0.7 release. Combined the small approved
closure feature window (Rated Manga grouping default-on, Top Picks contribution display) with a
public-release-readiness hardening pass: recommendation-quality cleanup public-safety fix (non-installed
probes now require Private or are refused, instead of PromptRequired-log-only), classified probe-error
storage (no more raw exception text in `SourceEvaluation.errorMessage`), OCR build-line decision (OCR
ships in the main build -- documented, not split out), and public-facing doc/security-review
reconciliation (stale `v0.7.34` references, the "OCR is separate"/"privacy notice missing" security-doc
contradiction). See `docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_IMPLEMENTATION.md` for full
detail, including manual QA results, the private/public APK paths, and deferred v0.8+ items.

`v0.7` feature work is now frozen. `KMK_RECS_V0_7_CLOSURE_AUDIT.md`'s "Option B" closeout path was used.

---

## v0.7.44 — SHIPPED (shared query policy, group recs parity, test-suite cleanup, phone UI density)

**Status:** COMPLETE and verified (2026-07-12). Implemented in checkpointed phases (A: test fixes,
B: shared query-attempt policy, C: apply to group recs, D: apply widened chain to For You, E: group
source-selection + visibility policy, F: background-job test coverage, G: phone UI density, H: docs),
each compiled/tested before the next began. `:app:testDebugUnitTest` (949 tests, 0 failures) →
`spotlessCheck` → `assembleDebug` all ran on JDK 17.0.19 and passed. APK built and copied to
`Komikku-v1.13.6-kmk.7.44-debug.apk`. `KmkRecsReleaseNotes.VERSION_CODE` = `746`, `VERSION_NAME` =
`"KMK-Recs v0.7.44"`.

**What changed:** Fixed the pre-existing `RecommendationCandidateVisibilityPolicyTest` Injekt failures
(and 2 more latent instances of the same bug) via a shared `TestInjektSupport` test helper — no
production code touched. New pure `RecommendationQueryAttemptPolicy` builds the shared strict-to-lenient
tag chain and classifies attempt outcomes; used by `CrossExtensionGenreSearchSource` for both
single-manga and group rows (plus a new bounded title fallback for group rows using
`GroupRecommendationSeed.titles`). `RecommendationQueryPlanner` (For You) was deepened to walk the same
3-step fallback chain instead of stopping after one fallback, and no longer persists a "successful
strategy" for a source whose final attempt produced zero results. Group recommendations now compute
eligible cross-extension sources via `RecommendationSourceSelector` and filter candidates through the
full `RecommendationCandidateVisibilityPolicy` (previously only seed-member/exact-Seen exclusion),
scoped to the group-seed path only. A new `SourceRecommendationQualityJobConflictPolicy` extracts and
tests the background-job conflict-guard decision; `SourceRecommendationQualityRunner` now does a
bounded wait for `availableExtensionsFlow` to warm up. Source Evaluation rows collapse detailed
error/reason diagnostics behind a per-row expand toggle by default, with 13 previously-hardcoded English
failure-kind labels now KMR strings; the Shizuku setup card, compatibility-check actions, and
source-suggestion row now use `FlowRow` instead of `Row` so they wrap on narrow phones.

**Explicitly out of scope per instruction:** no MarkSeen/Not Interested behavior changes — that was
confirmed already implemented in v0.7.43 and intentionally untouched here.

**Deviations (see the implementation report for full detail):** For You's shared-policy adoption was
implemented by deepening the existing `RecommendationQueryPlanner` rather than swapping in
`RecommendationQueryAttemptPolicy` directly (plan explicitly allowed either shape); the phone UI pass
covered the explicitly-named highest-risk spots rather than an exhaustive review of every button row in
`SourceEvaluationScreen.kt`; no screenshot/device testing was available, verification was by Compose
layout code inspection.

Full detail: `KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_IMPLEMENTATION.md`.

---

## v0.7.43 — SHIPPED (background compatibility job, group recs row-based rewrite, Seen -> Not Interested)

**Status:** COMPLETE and verified (2026-07-12). Implemented in three checkpointed phases (A: background
job, B: group recommendations rewrite, C: Seen reframe), each compiled and spotless-checked before the
next began, per the plan's non-negotiable rule against building the final APK before every phase is
done. `spotlessCheck` → focused tests → `:app:testDebugUnitTest` (928 tests; 3 pre-existing/unrelated
failures, see below; 1 skipped; rest pass) → `assembleDebug` all ran on JDK 17.0.19 and passed. APK built
and copied to `Komikku-v1.13.6-kmk.7.43-debug.apk`. `KmkRecsReleaseNotes.VERSION_CODE` = `745`,
`VERSION_NAME` = `"KMK-Recs v0.7.43"`.

**What changed:**
- New `SourceRecommendationQualityJob` (WorkManager, unique work name `SourceRecommendationQualityJob:active`,
  never reusing `SourceEvaluationJob:active`) + `SourceRecommendationQualityJobState` + `SourceRecommendationQualityNotifier`
  (own channel/notification IDs) + `SourceRecommendationQualityRunner` (probe loop extracted from the old
  screen-model coroutine). A `ScreenErrorKey.JobConflict` guard prevents Source Evaluation and the
  compatibility job from installing extensions at the same time.
- `RecommendsScreen`/`RecommendsScreenModel` gained `Args.CrossSourceGroupSeed`, reusing
  `RecommendationPagingSource.createSources(...)` and `CrossExtensionGenreSearchSource` (new optional
  `genreOverride`) seeded by `GroupRecommendationSeedBuilder` (reused, unchanged). Seed members and exact
  Not Interested/Seen manga are excluded from results; `GroupSeedRecommendationScorer` adds a group-tag
  bonus on top of the existing `RecommendationScorer`. `GroupSeededRecommendationsScreen`/`ScreenModel`/
  `GroupRecommendationLoopPolicy` deleted.
- "Mark as seen"/"Clear seen"/"Seen other versions" copy renamed to "Not interested"/"Undo not
  interested"/"Not interested in other versions". Internal storage (`SeenRecommendationMangaStore`,
  `seenRecommendationMangaKeys()`, backup proto field 626) is unchanged. For You scoring
  (`BrowsePersonalRecommendationsScreenModel`) now applies a bounded, scoring-only `-0.3`-per-genre
  penalty (vs. Dislike's `-2.0`) for genres found on already-locally-known Not Interested manga; never
  triggers a network call and never writes to the ratings table.

**Deviations/limitations (see the implementation report for full detail):** no new focused unit tests
were added for the Phase A/B/C code itself (existing tests for the reused pure helpers continued to
pass); `RecommendationCandidateVisibilityPolicy` (favorite/rated/known/min-chapter) is not applied to
group recommendations, matching the single-manga Recommendations page it now shares code with; the
Not Interested scoring penalty is For You-only, not group recommendations. 3 pre-existing test failures
in `RecommendationCandidateVisibilityPolicyTest` (Injekt `GetCustomMangaInfo` gap for `favorite=true`
test manga, reproduces in isolation regardless of this work) are unrelated and untouched.

Full detail: `KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_IMPLEMENTATION.md`.

---

## v0.7.42-fix2 — SHIPPED (second corrective follow-up to v0.7.42, not a new feature phase)

**Status:** COMPLETE and verified (2026-07-12). A code review after fix1 found the remaining
presentation/action/sort paths (row labels, the Best Fit and Search Reliability sort comparators, and
the action buttons) still used ad-hoc, disagreeing logic even though fix1 correctly unified automatic
evaluation, the queue, diagnostics, and fit staleness. `spotlessApply` → `spotlessCheck` → targeted
policy/queue/diagnostics/sort tests → `:app:testDebugUnitTest` (267 tasks, all pass) → `assembleDebug`
all ran on JDK 17.0.19 and passed. APK built and copied to `Komikku-v1.13.6-kmk.7.42.2-debug.apk`.
`KmkRecsReleaseNotes.VERSION_CODE` = `744`, `VERSION_NAME` = `"KMK-Recs v0.7.42-fix2"`.

**What changed:** New pure `SourceRecommendationFitDisplayPolicy` resolves every source to exactly one
truthful `CompatibilityDisplayState` (INELIGIBLE/NOT_CHECKED/OUTDATED/GREAT/GOOD/MIXED/WEAK/NO_MATCHES/
ERROR) by reusing `SourceRecommendationFitEligibility.isProbeEligible`/`isFitCurrent` — no new score,
no duplicated logic. The queue gained a fourth bucket (`outdatedPromising`, distinct from
`missingPromising`); a new `recheckOutdatedRecommendationQuality()` action targets stale fits only;
"Re-check all" now correctly includes outdated rows. Row labels now show "Not checked" or
"Outdated - recheck" truthfully instead of a hardcoded Strong Fit/Worth Trying check that could display
a stale result as current. `SortMode.SEARCH_RELIABILITY` (which sorted a field always `0.0` since
v0.7.42) was replaced with `SortMode.FOR_YOU_COMPATIBILITY`; `BEST_FIT` no longer reads that retired
field and now uses current compatibility as a true tie-breaker only after catalogue evidence is equal.

**Files:** `SourceRecommendationFitDisplayPolicy.kt` (new), `SourceRecommendationQualityQueue.kt`,
`SourceRecommendationQualityDiagnostics.kt`, `SourceEvaluationResultList.kt`,
`SourceEvaluationScreen.kt`, `SourceEvaluationScreenModel.kt`, `i18n-kmk` strings,
`KmkRecsReleaseNotes.kt`, plus `SourceRecommendationFitDisplayPolicyTest.kt` (new, 27 tests),
`SourceRecommendationQualityQueueTest.kt` (extended, 20 tests), `SourceRecommendationQualityDiagnosticsTest.kt`
(extended, 18 tests), `SourceEvaluationResultListTest.kt` (rewritten, 21 tests).

**No open follow-up remains from v0.7.42/fix1** — every finding named in the fix2 plan was confirmed
and fixed; see the implementation report for the "already fixed" check performed before editing.

Full detail: `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_IMPLEMENTATION.md`.

---

## v0.7.42-fix1 — SHIPPED (corrective follow-up to v0.7.42, not a new feature phase)

**Status:** COMPLETE and verified (2026-07-12). Codex review after the v0.7.42 implementation found that
`SourceRecommendationQualityQueue`, `SourceRecommendationQualityDiagnostics`, and the Source Evaluation
row subtitle still used pre-v0.7.42 assumptions even though the core scorer/schema/tests were
build-healthy. This release fixes those downstream consumers. `spotlessApply` → `spotlessCheck` →
targeted queue/diagnostics tests → `:app:testDebugUnitTest` (267 tasks, all pass) → `assembleDebug` all
ran on JDK 17.0.19 and passed. APK built and copied to `Komikku-v1.13.6-kmk.7.42.1-debug.apk`.
`KmkRecsReleaseNotes.VERSION_CODE` = `743`, `VERSION_NAME` = `"KMK-Recs v0.7.42-fix1"`.

**What changed:** `SourceRecommendationFitEligibility` gained `isProbeEligible(evaluation)` and
`isFitCurrent(fit, now)` — the single shared contract for probe eligibility and fit staleness.
`SourceRecommendationQualityQueue.compute()` (the manual "Check search compatibility"/"Re-check all"
actions) now uses this contract instead of a hardcoded `{STRONG_FIT, WORTH_TRYING}` set and a
mere fit-presence check, so a WEAK/NEUTRAL catalogue verdict with LOW/UNKNOWN metadata confidence is
correctly queued, and a missing/older-version/expired fit is correctly treated as needing
re-evaluation. `SourceRecommendationQualityDiagnostics.compute()` uses the same contract, so its counts
can never disagree with the queue; a stale fit no longer contributes to the Good/Weak/Error/No-results
buckets. The Source Evaluation row subtitle no longer shows a fabricated `search N%` figure
(`SourceEvaluation.searchReliabilityScore` is intentionally always `0.0` as of v0.7.42) — it shows
catalogue metadata confidence instead, via new KMR strings. Stale v0.7.41 documentation wording that
said v0.7.42 was still "planning-only" was corrected to note it was implemented later, without
rewriting history.

**Files:** `SourceRecommendationFitEligibility.kt`, `SourceRecommendationQualityQueue.kt`,
`SourceRecommendationQualityDiagnostics.kt`, `SourceEvaluationScreen.kt`, `i18n-kmk` strings,
`KmkRecsReleaseNotes.kt`, `KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md`, plus
`SourceRecommendationQualityQueueTest.kt` (rewritten, 17 tests) and
`SourceRecommendationQualityDiagnosticsTest.kt` (rewritten, 16 tests). No database migration was added.

**No open follow-up remains from the v0.7.42 base release** — the "`SourceRecommendationQualityQueue
.compute()` not updated" limitation noted in the original v0.7.42 shipping note is resolved by this fix.

Full detail: `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md`.

---

## v0.7.42 Source Evidence Redesign — SHIPPED

**Status:** COMPLETE and verified (2026-07-12) per the approved plan's decisions D1–D4. Gradle was initially blocked by a transient harness safety-classifier outage (11+ retries rejected, including a bare `--version` check, while read-only shell commands worked throughout); a later retry in the same session succeeded. `spotlessApply` → `spotlessCheck` → `:app:testDebugUnitTest` (267 tasks, all pass) → `assembleDebug` all ran on JDK 17.0.19 and passed. APK built and copied to `Komikku-v1.13.6-kmk.7.42-debug.apk`. `KmkRecsReleaseNotes.VERSION_CODE` = `742`.

**What changed:** Source Evaluation no longer pools Popular/Latest catalogue samples with a tag-search probe into one score. `SourceEvaluationScorer` now scores catalogue fit only, reusing the real `PersonalRecommendationScorer` per sampled item (previously an ad-hoc, divergent approximation). The scorer's own search probe was removed from `SourceEvaluationRunner`; search compatibility is measured solely by the pre-existing `SourceRecommendationFitProbe`. A new `catalogueMetadataConfidence` signal on `SourceEvaluation` prevents sparse Popular/Latest metadata from being misread as "bad fit," and `SourceRecommendationFitEligibility` now fails open toward probing when that confidence is low/unknown. `SourceEvaluationKeys.CURRENT_VERSION` bumped 1→2; `SourceRecommendationFit` gained its own independent version/expiry columns (migration 60) for staleness parity with `source_evaluation` (migration 59 adds the confidence column). UI strings that said "Recommendations: Good/Great" were relabeled "For You search: Good/Great" (resource IDs unchanged, English text only).

**Files:** `SourceEvaluationScorer.kt`, `SourceEvaluationRunner.kt`, `SourceRecommendationFitEligibility.kt`, `SourceEvaluation.kt`, `SourceRecommendationFit.kt`, `SourceEvaluationRepositoryImpl.kt`, `SourceRecommendationFitRepositoryImpl.kt`, `source_evaluation.sq`, `source_recommendation_fit.sq`, migrations `59.sqm`/`60.sqm`, `i18n-kmk` strings, `KmkRecsReleaseNotes.kt`, plus `SourceEvaluationScorerTest.kt` (new, 15 tests), `SourceRecommendationFitEligibilityTest.kt` (extended, 18 tests), `KmkMigrationTest.kt` (range 46–60, 17 tests).

**Deliberately deferred at the time — resolved by v0.7.42-fix1 above:** `SourceRecommendationQualityQueue.compute()` was not updated to use the new eligibility logic or fit staleness — it still partitioned "promising" sources via a hardcoded `{STRONG_FIT, WORTH_TRYING}` set instead of `SourceRecommendationFitEligibility.check()`, and didn't yet treat a stale `SourceRecommendationFit` as "missing." This mirrored the same "infrastructure exists, not wired into a UI consumer" state `SourceEvaluation.isStale()` already had, kept scoped out at the time to keep the v0.7.42 diff reviewable.

Full detail: `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`.

---

## v0.7.41 Known-Context Gap — RESOLVED

**Status:** COMPLETE (2026-07-11). Originally confirmed by Codex review after Claude's v0.7.41 implementation; unit tests passed with the repo-local JDK 17, but one policy-context gap remained. That gap is now fixed and verified.

**Problem (as found):** `BrowsePersonalRecommendationsScreenModel.discoverAdditionalPage()` called `RecommendationCandidateVisibilityPolicy.evaluate(...)` with `knownIds = emptySet()`. The caller's final merge still filtered additional-page candidates, so visible leakage was unlikely, but extra-page progress and candidate memory could treat known-only pages as successful and store candidates that should have been filtered before scoring/progress/memory.

**Fix shipped:** `discoverAdditionalPage()` now takes a `hideKnownManga: Boolean` parameter and batch-loads known IDs for localized extra-page candidates (one `GetKnownRecommendationMangaIds.await(...)` call, fail-open on lookup failure) before calling the shared policy — the same known-id context used by live page-one, cache, memory-merge, and group recommendations. `localizedCount`, `filteredCount`, `scoredCount`, `visibleCount`, `progressStatus`, the returned recommendations, and the candidates passed to `memoryStore.upsertBatch(...)` are all derived from the post-filter list, so a known-only additional page is now recorded as empty/filtered, never `STATUS_SUCCESS`, and no known candidates are written to candidate memory. The identical page-one/extra-page filter block was extracted into a new pure `filterVisibleCandidates(...)` top-level function for testability and de-duplication; the redundant post-call known-id re-lookup at the `searchSource()` call site was removed (extra-page candidates are already known-filtered by the time they are returned).

**Files:**
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/test/java/exh/recs/BrowsePersonalRecommendationsFilterTest.kt` (NEW — 7 tests)

**Verification:** JDK confirmed Temurin 17.0.19+10. `spotlessApply` / `spotlessCheck` / `:app:testDebugUnitTest` (267 tasks, all pass) / `assembleDebug` all BUILD SUCCESSFUL. See `KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md` § Follow-up for full detail.

---

Archived stale version (through v0.7.26): `archive/NEXT_WORK_STALE_V0_7_26_ARCHIVED_2026_06_29.md`

---

## Confirmed Bugs Or Inconsistencies

None currently confirmed. (The additional-page known-filtering gap tracked here was resolved by the v0.7.41 known-context follow-up above.)

---

## Later Feature Phase: After Shared Candidate Policy And Truthful Source Evidence

The v0.7.41 known-context follow-up and the v0.7.42 Source Evidence Redesign are both complete and verified (see above), so the project now has one truthful, verified source-evidence model and one shared candidate policy. These broader features can now be planned, but each still needs its own explicit approval and focused plan before implementation:

- broader For You filters such as publication/latest-update age, status, and metadata-confidence controls where source metadata supports them;
- refresh-effort modes that control how much extra discovery is attempted per refresh;
- source-scope controls for all sources, priority sources, selected sources, or source groups;
- chapter/update checks beyond the existing local minimum chapter-count filter, using only reliable local or explicitly fetched data;
- local outcome learning from exposed results, user opens, dismissals, ratings, and source contribution outcomes.

These should not be mixed into the source-evidence redesign itself. They should be planned as a later phase once the evidence labels and candidate visibility contract are stable.

---
## Remaining Open Items

These items were explicitly deferred through the polish planning phases (A-J) and remain unimplemented.

### 1. Manage Hidden Source Suggestions (non-installed dislike undo)

**Status:** Deferred from v0.6.2 / v0.6.20.

**Problem:** When a user dislikes a non-installed source suggestion (Sources To Try), that suggestion is hidden permanently. There is no UI path to undo the dislike. The only recovery is clearing all app preferences.

**Planned behavior:** A "Manage hidden source suggestions" screen (or section in Recommendation Settings > Management) that lists all disliked non-installed source keys with per-item "Undo dislike" actions.

**Constraints:** The key format for non-installed sources is `a|signatureHash|pkgName[|sourceId]`. Reading them back for display requires re-resolving against the available extension list or showing raw package names for unresolvable entries.

---

### 2. Hidden-Source "Hide Temporarily" Concept

**Status:** Deferred from v0.6.20.

**Problem:** Disliking a source is permanent. There is no lighter "suppress this for now" action distinct from dislike. Users who want to stop seeing a source for a while (e.g., a source they will reinstall later) have no option other than dislike.

**Planned behavior:** A "Hide temporarily" action in the source suggestion or source status row that hides the source for a configurable period (e.g., 30 days) without writing a permanent dislike. Different key format from dislike.

**Constraints:** Requires a new preference key format (e.g., `h|sourceId|expiryEpochMs`) and a cleanup pass on each app launch to expire stale hide entries.

---

### 3. Automatic Best Version Re-Search Trigger

**Status:** Deferred from v0.7.8.

**Problem:** After a user performs a Source Evaluation reassessment and a new "best" source emerges, there is no automatic prompt to re-run Best Version comparison. Users must manually navigate to the manga detail and re-trigger the workflow.

**Planned behavior:** Optional: a prompt or indicator in the manga detail page when the source quality data has changed significantly since the last Best Version pick was recorded.

**Constraints:** Quality signal records are stored in `manga_source_quality_signal` but source quality signals (`source_recommendation_fit`) are per-source, not per-manga. Connecting them would require a join or an additional field.

---

### 4. Cleanup for PromptRequired Extensions in Rec-Quality Probe -- RESOLVED v0.7.45

**Status:** Resolved. Superseded by a different (safer) fix than originally planned.

Code inspection during the v0.7 closure audit and this pass confirmed the inline
`evaluateRecommendationQualityForPromising()` path no longer exists -- it was replaced in v0.7.43 by
the background `SourceRecommendationQualityJob`/`SourceRecommendationQualityRunner`. That runner's
`PromptRequired` cleanup was still log-only (matching this item's original concern, just in the new
code path). Rather than replicating Source Evaluation's full cleanup-status/leftover-warning UX, v0.7.45
fixed this by forcing non-installed probes to the Private installer (which cleans up silently) and
refusing them entirely when Private is unavailable, instead of ever risking a Shizuku/Current leftover.
See `docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_IMPLEMENTATION.md`.

---

### 5. ScreenErrorMessage Still Hardcoded (R-019) -- RESOLVED (already fixed by v0.7.18/v0.7.44)

**Status:** Resolved. This item was stale -- confirmed already fixed during the v0.7 closure audit.

Code inspection shows `SourceEvaluationScreenModel` already defines a typed `ScreenErrorKey` sealed
interface (`Offline`, `CandidateLoadFailed`, `CrashRecovery`, `JobConflict`), and
`SourceEvaluationScreen.kt` maps every case to a KMR string. No raw `"Failed to load candidates:
${e.message}"` string remains.

---

### 6. Top Picks Contribution Display in Recommendation Settings -- RESOLVED v0.7.45

**Status:** Resolved. `topPicksContributionCount` is now shown compactly, appended to the existing
source fit badge (`"Great fit · 5"`) rather than a new row, only when the count is greater than zero.
See `RecommendationsSettingsScreen.kt` and `rec_source_fit_with_top_picks_count`.

**Files (historical, kept for reference):**
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` -- add `topPicksContributionCount` display in `SourcePriorityItem`
- `i18n-kmk/.../base/strings.xml` -- string for the label

---

## Permanently Deferred (No Implementation Planned)

- **AniList / tracker known-list cache** -- not needed; local library data is sufficient.
- **Local Source "Already Known" synthetic row** -- unclear user value; Local Source stays excluded from For You.
- **Auto-suggest already-linked versions when re-searching** -- deferred indefinitely; cross-extension match always runs fresh.
- **Per-chapter enrichment cache** -- enriched data not persisted to local DB; deferred indefinitely.
- **Network image loading fallback in Best Version preview** -- no workaround for sources requiring special headers. Deferred indefinitely.
- **Diagnostics string localization (`source_evaluation_rec_quality_diagnostics`)** -- English-only in base locale. Low priority; deferred indefinitely.

---

## Required Reading Before Any Implementation Session

Before starting any open item above, read:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md                   <- this file
docs/recommendations/KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md
```

## Batch L7 result and next work (2026-08-09)

**Status: DONE_WITH_VALIDATION_GAP.** The real generated migration 63 -> 64 path was already DONE
and was not changed. The bounded ScreenModel testability seam is DONE: `autoLoad = true` remains the
production default, while direct tests use `autoLoad = false` to construct the real model safely.

New direct tests cover tracker Known/Unknown/cancellation behavior and settled visible-exposure
capture, including source-aware identity and loading/empty/partial suppression. Full host validation
passed: 2,592 JUnit tests, 0 failures, 0 errors, 1 skipped; Kotlin compile, Spotless, and diff check
passed under repository JDK 17.

**Open next batch: PARTIAL_VALIDATION.** Build only the smallest additional direct harness needed to
drive the full `BrowsePersonalRecommendationsScreenModel` source-search assembly, especially
minimum-chapter filtering, realised personalized-majority output, refresh/lifecycle cancellation,
and stale-generation behavior. Do not repeat migration, tracker, reranker, lane, or exposure-policy
work without a changed fact. If the complete 25-collaborator load graph remains infeasible without
Robolectric or unrelated infrastructure, retain the exact blocker and keep the feature status
`DONE_WITH_VALIDATION_GAP`; do not promote it to COMPLETE or publication-ready.

## Jump to last-read chapter follow-up (2026-08-09)

The screen-level wiring is complete for both phone and tablet layouts and is validated by JDK 17
compile, focused policy tests, full unit tests, Spotless, and diff check. The remaining evidence gap
is rendered-device validation of the toolbar action, animated positioning, stale-target snackbar,
dark/light theme, long-label layout, and accessibility traversal. Do not reopen source wiring unless
a device result or a changed source fact identifies a defect.

## 2026-08-11 settings automation next batch

R0 recommendation contract audit is recorded in the private automation ledger.
R1 is complete with a rendered-validation gap: the existing five-screen settings
structure now has consistent search actions, and Source Evaluation retains its
gated Home/For You action. The next batch is R2: add separate Latest and
repeat-exposure enable/value preferences with defaults 20 and 14,
preserving the last configured values when disabled. Accept 1-100 integer
values when enabled, recover malformed and legacy values safely, and keep all
other For You matching, filtering, source evaluation, and minimum-chapter
behavior unchanged. Host validation must cover the control contract before any
tablet evidence is attempted.

R1 is now closed as `DONE_WITH_VALIDATION_GAP`. The remaining gap is rendered
UI and accessibility evidence only; no additional R1 source work is planned
unless a changed fact appears. Start R2 with the Latest/exposure preference
contract: separate enable switches, preserved last values, 1-100 validation,
defaults 20% and 14 days, and consumer isolation from personalized matching.

## 2026-08-11 R2 result and next wake

R2 is now `DONE_WITH_VALIDATION_GAP`. The separate Latest and repeat-title
cooldown switches, retained 1-100 values, legacy handling, bounded slider/text
entry, and isolated consumers are implemented and host-validated. Focused R2
JUnit XML reports 15 + 21 + 14 tests with zero failures/errors; compilation,
Spotless, and diff checks passed.

The next batch is H1: make normal Action History visible in its agreed settings
location, preserve typed undo/conflict behavior, add related-manga context
navigation with Back returning to history, and audit a developer-options-gated
sanitized session trace. Keep Evaluation Mode separate and never expose raw SQL,
raw database values, URLs, credentials, or unbounded logs.

## 2026-08-11 H1 result and H2A next batch

H1 is `DONE_WITH_VALIDATION_GAP`. Action History is now visible from Advanced
settings for ordinary users, while Evaluation Mode remains a separate display
privacy control. The seven existing journal families still provide the same
typed undo and verified follow-up behavior. Taste and library rows open their
owned manga context; other rows do not infer one. Host validation passed with
compile exit 0, 21 focused tests with zero failures/errors/skips, Spotless exit
0, and diff-check exit 0. Rendered navigation and accessibility remain for V1.

H2A is next: audit real journal/database/preference operation producers and
implement the developer-options-gated, detailed-but-sanitized, bounded,
session-only diagnostic trace. Do not derive it from row summaries or expose
raw SQL, database values, URLs, accounts, clipboard data, or unbounded logs.

## H2A0 gate repair is now first

The source audit found that the Developer Options preference and warning
assumed by the trace plan do not exist. Add and host-validate that explicit
public-build opt-in first, while preserving the separate `BuildConfig.DEBUG`
fixture boundary and Evaluation Mode privacy behavior. Only then proceed to
H2A producer instrumentation.

## H2A0 result and H2A next batch (2026-08-11)

H2A0 is `DONE_WITH_VALIDATION_GAP`. The missing Developer Options gate is now
implemented with a private default-off preference, explicit confirmation
warning, public-build opt-in, Evaluation Mode separation, and preserved
debug-fixture gating. Focused policy validation reports 8 tests with zero
failures/errors/skips; Kotlin compile, Spotless, and diff checks passed.
Rendered UI, accessibility, theme, and device evidence remain V1 work.

H2A is next. Audit real journal/receipt/database/preference operation
boundaries, define redaction and outcome phases, and implement only a bounded,
session-only sanitized diagnostic trace. Do not derive it from Action History
row summaries or raw logs, and do not expose it in Evaluation Mode.

## H2A result and B1 next batch (2026-08-11)

H2A is `DONE_WITH_VALIDATION_GAP`. The bounded, session-only diagnostic trace
now receives committed events from the real journal boundaries and phase/outcome
events from Action History undo and follow-up paths. Developer Options gates the
detail action; Evaluation Mode suppresses it. Five trace tests passed with zero
failures/errors/skips, and compile, Spotless, and diff checks passed. Rendered
detail UI, restart/device/accessibility evidence, and complete direct caller
coverage remain V1 gaps.

B1 is next: inspect the real Best Version search and preview paths, then record
the cap, chapter identity, cancellation, unavailable-source, preview-failure,
retry, and global-search separation contracts before editing.

## Next: C1 chapter-list validation

B1 is complete with a host-validation gap. The next batch validates the existing
Jump to last read chapter target at the top, middle, bottom, missing-progress,
and latest-chapter boundaries. Keep the action named and scoped as Jump to last
read; do not add a separate latest-available action. Rendered and device proof
remains reserved for the later evidence batch.

## 2026-08-12 C1 result and E1 next batch

C1 is now `DONE_WITH_VALIDATION_GAP`. The host implementation keeps the single
`Jump to last read` action, safely resolves filtered/sorted targets, centers a
middle item only when it has surrounding content, and clamps first/last/latest
and short-list cases without assuming a later chapter. Missing and stale
targets use the unavailable state. The focused policy XML reports 29 tests
with zero skips, failures, or errors; compile, Spotless, and diff checks pass.

Rendered phone/tablet positioning, touch target, TalkBack traversal,
theme/localized copy, stale-target snackbar, and device confirmation remain
V1 evidence gaps. E1 is next: review stale state and integration boundaries
across recommendations, Action History, Best Version, and reader navigation
before the V1 evidence batch.

## 2026-08-12 E1 result

E1 is `DONE`. The cross-feature host review found no new contradiction across
Latest discovery, exposure ordering, Source Evaluation, Action History, Best
Version, reader schedule, or the chapter-list jump action. The selected run
covered 22 suites and 296 tests with zero failures, errors, or skips; Kotlin
compilation and Spotless returned exit 0.

V1 is next for rendered phone/tablet validation, accessibility and theme checks,
schedule back-stack and process-stop evidence, chapter-list positioning proof,
and privacy-safe restoration records. Runtime conditions from the old crash
log remain separately named and are not presented as E1 fixes.

## 2026-08-12 V2 Action History and Not Interested audit

The source audit reopened a user-visible gap: the taste journal is intentionally
disabled when Evaluation Mode is off, although the destination is now labeled
Action history. Normal-user Not Interested writes and removals therefore do not
appear there. The separate seen-key storage remains valid for compatibility,
but the user-facing history contract needs a deliberate repair.

The full finding and implementation batches are recorded in
`private/docs/audits-and-reports/kmk-action-history-not-interested-audit-2026-08-12.md`.
No source implementation was started in this audit pass. Next batch is HN1:
define the normal-user local-history lifetime and refactor the journal boundary
without merging Not Interested storage into numeric ratings.

## 2026-08-12 HN1 result

HN1 completed the normal-user journal boundary. The six taste and Not
Interested builders now produce bounded local-only Action History inverses
when Evaluation Mode is off, while preserving commit-after-success ordering,
conflict detection, separate Not Interested storage, and developer-only trace
detail. Host validation covered 25 focused tests with zero failures, errors, or
skips; compilation, unit-test compilation, Spotless, and `git diff --check`
passed. The first bounded test attempt timed out and remains recorded as a
tooling exception; a fresh retry passed.

Next batch is HN2: inspect every supported Not Interested writer and remover,
including group/list actions, and close success, ordinary failure,
cancellation, malformed-state, bulk, conflict, and reversible-cleanup tests.
Do not begin ADB validation until all coding batches are closed.

## 2026-08-12 HN2 result

HN2 is host-validated with a documented rendered/lifecycle gap. Cross-source
Mark Seen and Source Evaluation Clear Seen now journal through the shared
Not Interested boundary. Key-based removal ignores malformed, absent, and
duplicate entries, while preserving existing numeric ratings for safe undo.

The fresh focused suite reports 31 tests with zero failures, errors, or skips;
compile, unit-test compilation, Spotless, and diff hygiene passed. The
adversarial review found and repaired the initial null-rating snapshot.

Next batch is HN3: verify safe manga-context navigation, honest missing-context
rendering, and Back behavior. ADB remains deferred until the coding batches are
complete.

## 2026-08-12 HN3 result

HN3 is host-validated with a documented rendered gap. The Action History
summary/time target now opens a manga only from a positive journal-owned ID;
missing or invalid context stays non-clickable. Undo and follow-up buttons no
longer share the manga-navigation hit target, and the existing app-bar Back
route remains the owner. Fresh XML is 18 tests, zero failures/errors/skips;
compile, unit-test compilation, Spotless, and diff hygiene passed after one
Compose import repair. The wrapper shutdown timeout is retained as a tooling
exception. Next coding batch: HN4 single/bulk undo, exact counts, conflicts,
failure, cancellation, and retry. Do not begin ADB validation yet.

## 2026-08-12 HN5 final result

HN5 repaired the last source-level Evaluation Mode suppression found in the
Best Version migration writer. Successful migrations now create the generic
Action History event and the matching typed local receipt for ordinary users,
while failure, cancellation, missing-target, and not-started paths remain
silent. The event and receipt share one correlation ID, so the existing
compensating follow-up remains usable.

The expanded focused selection passed with 247 tests, zero failures, errors,
or skips across 23 suites. Compile, unit-test compile, Spotless, and diff
hygiene passed. The exact source block has a static guard against reintroducing
the Evaluation Mode gate. No ADB was used.

HN5 is `DONE_WITH_VALIDATION_GAP / HOST_VALIDATED / V1_NEXT`. V1 remains for
rendered layout, accessibility/theme, lifecycle/restart, and authorized-tablet
evidence before documentation reconciliation.

## Source gate correction - 2026-08-12

The workflow now keeps the tablet phase behind the complete source coding
graph. L1 closed the schedule saved-state repair at host validation: the
primitive saver and malformed-row handling pass 65 focused tests, and compile,
unit-test compile, Spotless, and diff checks pass. The next source batch is L2,
which owns the reader Configure schedule route, reader Back restoration, and
the dialog/settings presentation contract. R3 Source Evaluation and Latest
visibility, R4 cross-feature adversarial/integration review, and R5 source
documentation/coding-completion reconciliation remain before V1. No ADB or
tablet evidence is part of these source batches.

## 2026-08-12 HN4 result and HN5 next

HN4 is `DONE_WITH_VALIDATION_GAP`. The full Action History gate audit removed
the remaining ordinary-user suppression from source enablement, source
preferences, and source-quality marks in addition to the previously repaired
Not Interested, taste, chapter, schedule, library, group, cover, backup,
tracker, install, migration, and Source Evaluation paths. Disabled-mode
coverage and static gate guards now cover the expanded surface.

Fresh XML: 133 tests, zero failures/errors/skips across 15 suites. Compile,
unit-test compilation, Spotless, and diff hygiene passed. HN5 is next for the
remaining host lifecycle/restart and rendered contract review before V1
device/evidence closure. ADB remains explicitly deferred.

## L2 result - 2026-08-12

L2 is `DONE_WITH_VALIDATION_GAP / HOST_VALIDATED`. The reader timer route stays
inside the reader activity and opens the shared schedule dialog, while the
dialog presentation now handles long lists, clear sections, full-row mode
controls, and stable window actions. The focused schedule suite reports 65
green tests and host compile/format/diff gates pass. R3 now owns the remaining
Source Evaluation and Latest catalogue visibility work. R4 adversarial and
integration review and R5 source coding/documentation closure still precede
the tablet phase. No ADB or device work is authorized yet.

## R3 result - 2026-08-12

R3 is `DONE_WITH_VALIDATION_GAP / HOST_VALIDATED`. The Source Evaluation
details now show how many catalogue samples came from Popular and Latest,
using counts already produced and stored by the runner. The For You Latest
lane, filtering, ranking, and privacy boundaries are unchanged. Focused XML
reports 106 tests with zero failures, errors, or skips; compilation, Spotless,
and diff hygiene passed. R4 now owns the source-only cross-feature adversarial
and integration review. ADB remains prohibited until R5 closes.
## 2026-08-12 Source-First Repair Restart

The next work is now the post-L2 source-repair graph, not tablet validation.
Start with N0, which audits the newly reported Best Version preview and
per-source-cap behavior, recommendation-settings layout and controls, Source
Evaluation Latest/host/search behavior, preference and Action History parity,
and the long-background error. N1-N6 then implement and host-validate one
structural area per wake. V1 ADB/tablet evidence is deferred until N6 closes
the source coding-completion gate. The governing plan is
`private/docs/plans/kmk-post-l2-source-repair-batch-plan-2026-08-12.md`.

N0 is complete. The next source batch is N1: verify the existing Best Version
per-source cap is discoverable in the intended flow, then repair only confirmed
preview progression, stale-result, timeout, cancellation, retry, or cleanup
gaps. Do not add a duplicate cap or use external HTTP/parser failures as proof
of an app defect. N2-N6 remain queued, and V1 ADB/tablet validation is still
blocked until the source coding gate closes.

## 2026-08-12 N1 result and N2 next

N1 closed the confirmed Best Version discoverability gap. The Find Best
Version AppBar now opens the existing Results per source preference at its
anchored location. The cap remains source-aware and separate from normal
global search; preview timeout, retry, unavailable-chapter handling,
cancellation, and dispatcher cleanup were retained. Focused route coverage,
Kotlin compile, unit-test compile, Spotless, and diff checks passed. The first
test attempt timed out as a recorded tooling exception; the bounded retry
passed. Dirty/untracked work remains preserved.

N2 is next: audit and repair only confirmed recommendation-settings grouping,
first-view access, concise copy, and native control gaps. Keep the five
destinations separate, preserve stored values and matching behavior, and keep
ADB/tablet validation deferred until N0-N6 and the source coding gate close.

## 2026-08-12 N2 result and N3 next

N2 closed the confirmed shared-structure gap. Section headers now use the
established divider treatment, and the quick-access destination collection has
a localized accessibility description. The four shared detail settings screens
remain separate, while Source Evaluation retains its dedicated host/search
actions. Native controls, values, filters, matching, and Back behavior were
preserved. Focused N2 validation reported 130 passing tests; compile,
unit-test compile, Spotless, and diff checks passed.

One aggregate control audit remains stale because it still expects the old
Evaluation Mode-only Action History route; N4 owns that reconciliation. N3 is
next: Source Evaluation Latest plus host/search integration. ADB remains
deferred until N0-N6 and the source coding gate close.

## 2026-08-12 N3 result and N4 next

N3 closed the confirmed host-navigation gap. All four recommendation detail
screens now expose the shared For You app-bar action, and Source Evaluation
uses the same root-and-tab helper while preserving its hidden-tab condition.
Latest probing, filtering, scoring, persistence, Popular/Latest evidence, and
the separate search-compatibility probe were preserved. Focused route coverage,
the 484-test Source Evaluation package, control audits, compile, Spotless, and
diff checks passed. N4 is next for Love/Like/Dislike/Not Interested parity,
bulk clear, and normal Action History behavior. ADB remains deferred.

## 2026-08-12 N4 result and N5 next

N4 closed the Not Interested parity gap. Its collection now has the same
selection AppBar, row-selection, select-all, and bulk-clear structure as the
other preference collections, with exact-key journal-backed removal and
single-row cleanup retained. The stale Action History control expectation was
updated to the normal user route and developer-gated diagnostic contract.

Focused tests, compile, unit-test compile, Spotless, control audits, and diff
hygiene passed. No device work occurred. N5 is next for background recovery
and cross-feature lifecycle repair, including correlation of the supplied
Reader load failures and the external Google configuration blocker. ADB stays
deferred until N6 closes the source coding gate.

## N5 active: background recovery and lifecycle repair

The supplied crash report is now the N5 input. The old Reader schedule parcel failure is already
source-repaired. The missing Google client asset remains externally blocked for real sync, but the
background worker will be hardened to classify that unavailable configuration without repeated
exception noise. Reader startup will keep the requested chapter when present and use a deterministic
fallback when a restored chapter id is no longer in the current list, with privacy-safe failure
classification. Focused source tests and host gates must pass before N6; ADB remains deferred.

## N5 result and N6 next

N5 closed the confirmed source-owned Reader startup defect and the repeated background configuration
noise. Exact launch chapters still win; stale restored ids use the first existing ordered chapter,
and empty lists still fail truthfully. Reader diagnostics are category-only and privacy-safe.
Missing `client_secrets.json` now skips Google background sync without changing configured OAuth, but
real Google sync remains an external blocker. Focused tests, compile, Spotless, control checks, and
diff hygiene passed. N6 is next for final adversarial integration and source coding completion;
tablet work remains prohibited until that gate closes.

## N6 active: adversarial integration and source coding completion

Run the full relevant source suite and cross-feature control/privacy/lifecycle audits. Reopen an
earlier batch only for a confirmed regression; otherwise reconcile the source gate and move to V1.
Keep Google OAuth configuration as an external blocker and keep process-recreation, rendered UI,
accessibility/theme, and authorized-tablet evidence deferred until the source gate is complete.

N6 is complete at the source gate with host validation. The next batch is V1 authorized-tablet
evidence, using only the declared tablet workflow and only after the per-command device identity
check. Capture no route that lacks a valid fixture, privacy decision, or rollback result. Keep the
Google client configuration as BLOCKED_EXTERNAL and keep human publication approval separate.

The first complete source gate found six stale Evaluation Mode-off test expectations. Update only
those tests to match normal Action History, preserve no-record failure/cancellation boundaries, then
rerun the affected classes and the full source gate. Do not begin V1 device evidence until N6 is
green.

## 2026-08-12 V1 preflight blocker

N6 is green. The next action remains V1 authorized-tablet evidence, but its preflight is
`BLOCKED_EXTERNAL`: `adb devices -l` returned no attached rows, so R5GL201CAQX was not available.
Wait for that exact serial, verify it before every device command, and continue only with routes that
have a valid fixture, privacy decision, and rollback contract. Do not substitute the emulator or
claim rendered evidence from source tests.

V1 is paused after the two-pass no-progress cap. The next external action is to connect the
authorized tablet and verify its identity before any route selection. Do not loop on an empty ADB
list, use emulator evidence, or mark the rendered/device gaps complete.

## 2026-08-12 V1 next checkpoint

The authorized tablet is now available. The first V1 route, Browse > For You, has a private
`DONE_WITH_VALIDATION_GAP` bundle. Continue with one separate route bundle at a time only after fresh
identity, Evaluation Mode, loaded-state, privacy, hash, and restoration checks. Keep the raw bounded
log private; use only the filtered log for any later public-boundary review. Do not claim V1 or human
publication completion from this one route.

## 2026-08-12 OAuth O1 next

O0 source ownership is closed. Implement O1 only: create a safe, testable
configuration boundary for development, public-test, and production variants;
make missing, malformed, and variant-mismatched configuration explicit; and
preserve existing signed-in behavior. Do not import the supplied client JSON,
change the callback yet, use ADB, or contact a real Google account. Record the
pre-edit packet before source changes, then run focused host tests and the
standard compile, format, and control gates.

## 2026-08-12 OAuth O4 next

O3 is closed with a host-validation gap. Implement O4 only: add deterministic
host fakes and contract tests for refresh success, missing credentials,
revocation, transient I/O, malformed responses, cancellation, worker retry,
process/lifecycle transitions, and stable privacy-safe errors. Reuse the
existing Komikku Drive service, preferences, credential, worker, and sync
contracts. Do not add a second OAuth stack, import client JSON, use ADB, or
contact a real Google account. Record the O4 pre-edit packet before source
changes, then run focused tests, debug compile, Spotless, privacy, and diff
hygiene gates.

## 2026-08-12 Batch 2 next

Batch 1 host validation is closed with a named `TOOLING_LIMIT`: synthetic
state and loopback source/preview fixtures pass six focused tests, while the
Android/Compose rendering and saved-state harness remains unconfigured. Next
is B2, shared native settings controls. Reuse the local fixtures, keep the
rendered gap visible, and do not use ADB.

## 2026-08-12 OAuth O5 final next step

O4 is complete with an external validation gap. Reconcile foreground error
copy, provider-payload exposure, variant compatibility, original Komikku sync
behavior, and the account/network/process-death boundary. Reuse the current
Drive service and worker contracts; do not add another OAuth stack, import
client JSON, use ADB, or contact a real account. Record the O5 pre-edit packet
before source changes and close it with adversarial, compile, Spotless, privacy,
and diff gates.

## 2026-08-13 OAuth O5 source completion

O5 is complete with the source coding gate closed. Foreground authorization
failures use stable localized copy, and provider exception/callback payload text
is blocked at the UI boundary. Focused tests, debug/public-test/release compile,
public-test/release assembly, Spotless, privacy, and diff checks passed.

Next is V1 external/rendered evidence only after explicit user authorization.
Real account/network, process-death, WorkManager runtime, release signing
identity, and tablet/rendered behavior remain unverified. The current tablet
pause is intentional; do not use ADB, install an APK, capture screenshots/XML,
or inspect a database until the user explicitly resumes that phase.

## 2026-08-13 deferred structural correction

Before any future source repair, resume the documentation-first correction plan
at Batch 0. Reconcile the actual production owners against original Komikku,
then build the local route and lifecycle seams in Batch 1. The queued feature
work covers canonical Configure schedule navigation and disabled-state
handling, Best Version exact chapter preview and per-source caps,
recommendation-settings dividers and shared controls, Source Evaluation
integration, preference parity, normal Action History, and cross-feature
exception recovery. Do not treat earlier source batches or this note as proof
that those follow-ups are complete.

## 2026-08-13 Batch 0 result and Batch 1 next

Batch 0 completed the source-ownership and upstream comparison audit without
editing production code. Reader schedule has two route owners; Best Version
has a discoverable cap but a separate chapter/preview path; shared
recommendation controls are only partially family-audited; and Action History
requires a full writer-after-success and journal-family review. Batch 1 is next
for local route, Compose/semantics, source/preview, lifecycle, prerequisite,
and typed-journal validation seams. No ADB or device work is authorized.

## 2026-08-12 OAuth O5 next

O4 is closed with an external validation gap. Reconcile the release-facing
Google Drive contract: review foreground authorization failure text, provider
payload exposure, variant configuration, compatibility with original Komikku
sync behavior, and the remaining account/network/process-death gap. Reuse the
existing service, scopes, preferences, and worker. Do not add a second OAuth
stack, import client JSON, use ADB, or contact a real Google account. Record the
O5 pre-edit packet before any source change and close it with adversarial tests,
compile, Spotless, privacy, and diff gates.

## 2026-08-12 OAuth O4 next

O3 is closed with a host-validation gap. Implement O4 only: add deterministic
host fakes and contract tests for refresh success, missing credentials,
revocation, transient I/O, malformed responses, cancellation, worker retry,
process/lifecycle transitions, and stable privacy-safe errors. Reuse the
existing Komikku Drive service, preferences, credential, worker, and sync
contracts. Do not add a second OAuth stack, import client JSON, use ADB, or
contact a real Google account. Record the O4 pre-edit packet before source
changes, then run focused tests, debug compile, Spotless, privacy, and diff
hygiene gates.

## 2026-08-12 OAuth O3 next

O2 is host-complete with an external validation gap. Preserve the existing
Komikku authorization contract while implementing token expiry, refresh,
background-sync failure containment, sign-out, process recreation, and repeated
authorization handling. Keep client JSON and real-account/device validation
out of source work. Record the O3 pre-edit packet before changing code.

## 2026-08-12 OAuth O2 next

Complete the supported Android authorization return path. Verify the current
Google and Android contract, replace the legacy custom-scheme callback only
after the replacement is source-compatible, and cover cancellation, denial,
expired codes, repeated authorization, process recreation, and callback
mismatch with fakes. Do not import the external client JSON, use ADB, or
contact a real Google account during O2.

## 2026-08-12 OAuth O1 next

O0 source ownership is closed. Implement O1 only: create a safe, testable
configuration boundary for development, public-test, and production variants;
make missing, malformed, and variant-mismatched configuration explicit; and
preserve existing signed-in behavior. Do not import the supplied client JSON,
change the callback yet, use ADB, or contact a real Google account. Record the
pre-edit packet before source changes, then run focused host tests and the
standard compile, format, and control gates.

## 2026-08-12 OAuth O4 current next step

O3 is closed with a host-validation gap. Implement O4 only: add deterministic
host fakes and contract tests for refresh success, missing credentials,
revocation, transient I/O, malformed responses, cancellation, worker retry,
process/lifecycle transitions, and stable privacy-safe errors. Reuse the
existing Komikku Drive service, preferences, credential, worker, and sync
contracts. Do not add a second OAuth stack, import client JSON, use ADB, or
contact a real Google account. Record the O4 pre-edit packet before source
changes, then run focused tests, debug compile, Spotless, privacy, and diff
hygiene gates.

## 2026-08-12 OAuth O5 final next step

O4 is complete with an external validation gap. Reconcile foreground error
copy, provider-payload exposure, variant compatibility, original Komikku sync
behavior, and the account/network/process-death boundary. Reuse the current
Drive service and worker contracts; do not add another OAuth stack, import
client JSON, use ADB, or contact a real account. Record the O5 pre-edit packet
before source changes and close it with adversarial, compile, Spotless, privacy,
and diff gates.
## 2026-08-13 B3 next: recommendation source behavior

Audit and repair Best Version discovery through exact chapter matching and
preview, including the configurable per-source cap and truthful failure,
timeout, retry, cancellation, and stale-preview cleanup. Verify Latest is
additive for supporting sources, preserves all filters, and stays within the
configured exploration share. Keep global search separate from Best Version
and source-evaluation limits. Reuse the existing search, `SourceRuntime`,
chapter/preview, policy, and local loopback fixture owners. Record a new
pre-edit packet before source changes; ADB and real network/account evidence
remain disabled.

## 2026-08-13 B4 next: preference parity and normal Action History

B3 is closed for source and host gates with a documented tooling gap. Implement
B4 only: audit and repair Love, Like, Dislike, and Not Interested as one shared
preference family; preserve parallel collection/marker/clear/bulk behavior;
add clear-one versus clear-all scope confirmation; keep normal Action History
visible without Evaluation Mode; verify context navigation, Back restoration,
typed undo, conflict handling, view-only diagnostic rows, clear operations,
failed/cancelled persistence, and Developer Options trace gating. Reuse the
existing preference stores, journal registry, undo services, navigation, and
trace policies. Record the B4 pre-edit packet before source changes. ADB,
device evidence, credentials, database inspection, publication, commits, and
pushes remain disabled.

## 2026-08-13 B5 next: reader schedule, chapter navigation, and lifecycle repair

B4 is closed for source and host gates with the named local-rendering tooling
gap preserved. Implement B5 only: verify the Reader Configure Schedule route
opens the canonical schedule owner, handles the scheduler-disabled prerequisite,
and returns to Reader on Back; complete Jump to last read wiring and its
chapter-list boundaries; and audit long-background/recreation recovery across
reader, schedule, Best Version, and recommendation flows. Reuse the existing
schedule persistence, reader navigation, chapter target policy, lifecycle
guards, and host fixtures. Record a new pre-edit packet before source changes.
ADB, device evidence, credentials, database inspection, publication, commits,
and pushes remain disabled.

## 2026-08-13 B6 next: adversarial integration and source coding completion

B5 is closed for source and host gates with the named rendered/lifecycle
tooling gap preserved. Implement B6 only: rerun the cross-feature dependency
matrix across settings controls, Best Version/source behavior, Latest/source
evaluation, four-state preferences, ordinary Action History, reader schedule,
Jump-to-last-read, and long-background recovery. Reconcile stale or
contradictory source ownership, verify original-Komikku compatibility and KMK
integration, and repair regressions in the same batch. Reuse the current
policies, route owners, persistence/journal boundaries, host fixtures, and
existing tests. Do not add another OAuth stack, use ADB, inspect databases,
capture screenshots/XML/logs, contact accounts, publish, commit, or push.
Record the B6 pre-edit packet before any source change. B7 remains optional
tablet evidence and is currently disabled by the user.

## 2026-08-13 B6 closed; B7 paused by explicit user decision

B6 is closed for source and host work with the named validation gaps
preserved. The focused matrix passed 676 tests and the full unit suite passed
2,725 tests with zero failures/errors and one skipped test. Compile, Spotless,
privacy, dirty-path preservation, and diff hygiene passed. No B6 source repair
was necessary after the cross-feature owner and compatibility audit.

There is no next coding batch in this plan. B7 is optional tablet/external
evidence only and is disabled by the user. Do not use ADB, emulator,
installation, screenshots, XML/log capture, database inspection, credentials,
publication, commits, or pushes unless the user separately reauthorizes that
scope and a fresh plan packet is recorded. Rendered Compose and Android
recreation gaps remain `TOOLING_LIMIT`, not silently closed.
