## Jump to last-read chapter (batch J1) — 2026-08-09 — IN_PROGRESS, not user-reachable

A new manga-detail feature batch, separate from the recommendation line. Full record:
`private/docs/audits-and-reports/KMK_JUMP_TO_LAST_READ_CHAPTER_IMPLEMENTATION_LEDGER_2026-08-09.md`.

The target-resolution contract is complete and tested; the screen-level scroll wiring is not. **The
action does not appear in the app yet** — `MangaToolbar`'s new parameter defaults to `null` and no call
site supplies it, so original Komikku chapter-list behavior is unchanged.

- **Target rule (no new database field, no migration):** the last-read chapter is the in-progress
  chapter (`lastPageRead > 0 && !read`) furthest along in reading order; failing that, the completed
  chapter furthest along; failing that, **no target** and no action. It deliberately does not fall back
  to "the last item in the list". `Chapter.lastModifiedAt` was rejected as a read timestamp because any
  write moves it. Direction follows the existing `manga.sortDescending()` contract, mirroring the Resume
  FAB's `getNextUnread` split.
- Resolution runs on the already-filtered displayed list, so merged manga, duplicate chapter urls across
  sources, and missing-chapter separators are handled by construction, and filters/sorting are never
  silently changed.
- Jumping never mutates read state and never opens the reader — it scrolls within the chapter list.
- 26 policy tests, 0 failures. Rendered scroll/theme/layout/TalkBack validation is **BLOCKED_EXTERNAL**
  (no Compose UI or screenshot infrastructure exists here).

## Real 63 → 64 migration path + evidence re-classification (batch L6) — 2026-08-09

Full record: `private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`
("Batch L6").

- **The migration-64 claim was overstated and is now genuinely true.** The existing test applied
  migrations 46..62 and then 64, **skipping 63 entirely** — so the path a real device takes was never
  executed. The skip had a hard cause: `63.sqm` cannot run as raw SQL at all (it carries SQLDelight
  `import` directives and adapter syntax like `INTEGER AS Boolean` / `BLOB AS JsonObject`, and depends
  on upstream tables the KMK range never creates). The new test drives the **generated
  `Database.Schema.migrate(...)` that actually ships in the app**, building a faithful pre-63 database
  (with `extension_repos` copied verbatim from migration 32) and then running the real production 63
  and 64 migrations in sequence. It asserts the exposure table, all six columns, the composite
  `(source_id, url)` primary key, both indexes, replay-safety, that migration 63's own effects survive,
  that unrelated tables and rows survive, that a single combined upgrade reaches the same state, and
  that the fresh-install schema still matches the upgraded one.
- **Evidence is now classified rather than blurred.** Direct production-path validation (real migrator,
  real repository, real `GetTracks`) is reported separately from integration/merge tests, pure policy
  tests, and source inspection.
- **The tracker tri-state and the earlier recommendation repairs were re-audited against current
  source and confirmed unchanged** — no rewrite was needed.

Validation this pass: the new migration suite is **12 tests, 0 failures**. The overall feature status
is **DONE_WITH_VALIDATION_GAP**: direct `BrowsePersonalRecommendationsScreenModel` harness coverage
remains **PARTIAL_VALIDATION** (no Robolectric; the constructor needs a real Android `Application`;
`context.isOnline()` is a top-level extension function that a plain `mockk<Context>` cannot stub; and
`init` starts a full load on construction). Domain D rendered Compose validation remains
**BLOCKED_EXTERNAL**. The working tree is dirty; nothing was committed or pushed.

## Tracker fail-safety + migration 64 coverage (batch L5) — 2026-08-09

Two focused repairs. Full record:
`private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`
("Batch L5").

- **A failed tracker lookup can no longer cause a tracked title to be de-emphasized.** Tracker state
  is now a genuine three-way distinction — known tracked, known untracked, and unavailable — instead of
  a set where "lookup failed" and "nothing is tracked" looked identical. When tracker state cannot be
  determined, repeat-title reordering is skipped entirely for that refresh: nothing is hidden, removed,
  reordered, or down-rated. The root enabler was one layer down: the shared `GetTracks.await()` already
  swallowed its own errors and returned an empty map, so the distinction was unrecoverable at the call
  site. A new `awaitOrNull()` returns null on failure; `await()` is unchanged, so every other caller in
  the app behaves exactly as before.
- **Migration 64 now has real coverage** — and adding it immediately caught a second defect: the
  migration used bare `CREATE TABLE`/`CREATE INDEX`, breaking the `IF NOT EXISTS` idempotency contract
  every other KMK migration follows. Fixed. The new test verifies the table, all six columns, the
  composite `(source_id, url)` primary key, both indexes, idempotency, a real upgrade from the prior
  schema that preserves unrelated tables and rows, the fresh-install schema path, and the repository's
  insert/increment/prune/clear query shapes — all in-memory, no device database touched.

Validation: **2574 unit tests, 0 failures, 0 errors, 1 skipped** (up from 2545); `spotlessCheck` clean;
`git diff --check` clean. Direct `BrowsePersonalRecommendationsScreenModel` harness coverage remains
**PARTIAL_VALIDATION** — no Robolectric exists in this project, the constructor needs a real Android
`Application`, and the `init` block starts a full load on construction. Domain D rendered validation
remains **BLOCKED_EXTERNAL**. Working tree dirty: 378 paths (baseline 376, delta is the two new test
files); nothing committed or pushed.

## Latest catalogue + exposure structural repair pass (batch L3) — 2026-08-09

A re-audit of the **current source** (not the batch L2 summary) found four real defects that L2 had
reported as complete. All four are fixed. Full record, including the requirement matrix and the exact
per-defect evidence: `private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`
("Batch L3").

- **Personalized results are now genuinely the majority of every row.** The previous claim rested on
  budget arithmetic (2 Latest slots out of a 5-card row = 40%), which only holds when the personalized
  lane actually fills the row. With one personalized result and two Latest results the row was
  two-thirds Latest. `PersonalRecommendation` now carries its discovery lane all the way through the
  merge, and the display cap enforces an explicit invariant on the realised list: Latest may tie but can
  never outnumber personalized/Popular results, and never exceeds two per source. When no personalized
  result exists at all, Latest keeps its original rescue role unchanged.
- **Repeat-title cooldown no longer confuses two sources that share a URL.** Exposure history is now
  keyed by source *and* URL everywhere. Previously two sources exposing the same relative path (a
  routine occurrence) could inherit each other's de-emphasis.
- **Tracked titles are now genuinely exempt from de-emphasis.** The tracker check was previously
  hardcoded off. It is now a real batched lookup that fails open — if tracker state can't be read, the
  title simply doesn't get the exemption; nothing is ever hidden or down-rated as a result.
- **"Clear repeat history" is now a real settings action** with a confirmation dialog that states
  plainly it only forgets what has been shown — ratings, library, and tracking are untouched.

Validation: **2545 unit tests, 0 failures, 0 errors, 1 skipped** (up from 2517); `spotlessCheck` clean;
`:app:assembleDebug` successful; `git diff --check` clean. The quick-access panel still has **no
rendered/visual validation** (no Compose UI or screenshot test infrastructure exists in this repo) —
that remains `BLOCKED_EXTERNAL` and is not claimed as done. Working tree remains dirty (376 changed
paths, up from a 375 baseline by exactly the one new test file); nothing was committed or pushed.

## Latest catalogue + exposure structural completion pass (batch L2) — 2026-08-08

Closes the two production-integration gaps batch L1 left open (see `NEXT_WORK.md`'s "batch L2:
structural completion pass" entry for the full file/symbol/test list; private full record:
`private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`).

- **Latest catalogue lane is now a bounded additive exploration lane, not fallback-only.** It can
  contribute a small, capped share of a source's row even when personalized results already exist for
  that source, while personalized results remain the majority in every supported configuration.
- **Exposure persistence and soft reranking are now wired into production.** A new local-only
  `recommendation_exposure` table records exactly one event per loaded, visible, stable result
  generation; a bounded reranker (previously implemented but called by nothing) now reorders the
  accepted candidate list before the display cap, softly deprioritizing repeatedly-exposed, untouched
  titles without ever hiding a candidate, penalizing library/rated/tracked/interacted titles, or
  overriding Not Interested/Dislike/blocked-tag/blocked-source exclusions. A "Repeat title cooldown"
  settings row controls the exposure window (7/14/30 days, default 14).
- **Minimum-chapter filtering now has an end-to-end pipeline proof**, not only policy-unit tests --
  4 new tests persist a real threshold and confirm it changes actual `merge()` output.
- **Explicit, unclosed gap:** the quick-access panel has not received rendered/visual validation this
  pass (no Compose UI or screenshot test infrastructure exists anywhere in this repository, confirmed by
  grep). This is not fabricated as complete.
- **Validation:** full `:app:testDebugUnitTest` -- 2517 tests, 0 failures, 0 errors, 1 skipped;
  `spotlessCheck` clean; `git diff --check` clean; compile steps confirmed to actually re-execute (a
  real `awaitList`/`Query<T>` compile bug was found and fixed mid-pass, after an earlier compile run had
  misleadingly reported success without recompiling the affected module). Working tree remains dirty
  (~375 changed paths) -- nothing was committed, pushed, or cleaned this pass.

## KMK-Recs v0.8.20-fix3: schedule confirmation and source diagnostics

## Future recommendation-quality proposal - 2026-08-08

An OPEN, unimplemented plan records a possible Latest-catalogue discovery lane for For You. The
proposal treats Latest as an additive, bounded signal alongside personalized/search and Popular,
with explicit provenance, novelty/underexposure scoring, existing chapter/tag/language/known-title
filters, source-fit and metadata-confidence safeguards, deterministic offline fixtures, and graceful
fallback for sources without a reliable Latest route. No source behavior, ranking, database, APK, or
device state changed for this proposal. See
`docs/recommendations/KMK_RECS_FUTURE_LATEST_CATALOGUE_QUALITY_AND_NOVELTY_PLAN_2026-08-08.md`.

The follow-up decision is that ignored cards are not negative feedback: only actual exposure may
affect temporary repetition control, while explicit actions affect eligibility or taste. Latest
must obey every existing filter and receive only a bounded exploration share behind personalized
matches. The default exposure window is 14 days. Repeatedly visible, untouched candidates should
be softly shifted down within their source/topic listing rather than removed; loaded visible cards
count as exposure, fetched-but-never-visible pages do not. The For You quick-access panel's
reported spacing, centering, balance, and hit-target problems remain a separate OPEN visual audit
item.

The reader schedule editor now asks for confirmation before removing a time window. Evaluation
Mode also relabels the top source in the For You sources summary, closing the remaining summary-level
source-name leak. Management and diagnostics now exposes the latest persisted evaluation row for
each source with count-based metadata coverage, detail-enrichment, confidence, and tag-evidence
details. The new section is read-only and does not make network requests or expose sampled tag text.

## KMK-Recs v0.8.20-fix1: manga-detail rating journaling and extension/migration visibility closed

The 2026-07-25 correction pass below (still accurate for the items it lists) explicitly flagged one
remaining gap: "manga-detail rating/clear-rating writes in `MangaScreenModel.setMangaTaste()` and
`clearMangaTaste()` are not currently journaled, so they do not appear in Action History." That gap
is now closed -- both methods build a journal entry before the write and commit it only after the
write succeeds, the same contract every other rating surface (For You/Loved/Liked/Disliked) already
uses. See `private/docs/audits-and-reports/KMK_FIX_PASS_V0_8_20_FIX1_REPORT.md` (private) for the exact symbols and tests.

The same fix pass also added `NonUndoableEventJournal`: a completed Best Version migration or a
confirmed source install (both still genuinely non-reversible, per the "still intentionally outside
the automatic undo contract" list below) is now at least visible in Action History as a truthful,
clearly-labeled "cannot be undone" entry, rather than being invisible. Extension uninstall remains
unrepresented -- see the private report for why that specific gap was left open.

## 2026-07-25 correction pass: audit gaps closed

The prior status summary below describes the state before the correction pass. The following changes supersede its remaining-gap statements for the items listed here:

- Chapter undo is now visible in EvaluationModeActionHistoryScreen, and Clear history clears the chapter journal as well as the existing taste, group, library, and preference journals.
- Manga detail read and bookmark actions now build chapter snapshots before the write and commit them only after the write succeeds.
- Recommendation source exclusion, source-order reset, same-manga matching controls, and best-version preview controls now use typed preference undo entries.
- Recommendation source like/dislike state is journaled as one typed composite state so restoring it cannot restore only one of the two serialized preference sets.
- Source Evaluation quality marks now use the same typed journal path as the corresponding settings actions.
- UpdateChapter and SetMangaCategories now return explicit persistence success, and restore services retain their entries when the repository reports failure instead of claiming success.

Still intentionally outside the automatic undo contract: downloads and downloaded-file deletion, extension install/uninstall, backup restore, tracker/network writes, source-evaluation runs, and manga migration. These have external or multi-stage effects that require separate explicit recovery UX rather than a speculative inverse. The build checks completed after this pass; live-device verification remains a separate activity.
# KMK-Recs v0.8.20-fix2: reassessment eligibility and install-history coverage corrected

The actionable Source Evaluation stale queue and completion reconciliation now share the same
explicit-source eligibility. Successful user-initiated extension installs are recorded consistently
across Browse Extensions, Sources To Try, recommendation bundle import, and Source Evaluation when
Evaluation Mode is enabled. Temporary evaluation installs, failures, cancellations, and extension
uninstall remain outside the visibility-only journal. See the private
`private/docs/audits-and-reports/KMK_FIX_PASS_V0_8_20_FIX2_REPORT.md` for the exact implementation and validation record.

# KMK Personal Recommendations Current State

Latest (2026-07-25): **Evaluation Mode Undo Expansion — Phases 1, 3, 4, 5 fully implemented and
verified; Phase 2 partially implemented (one representative call site, rest deferred, honestly
documented, not silently skipped).** Executed from
`private/docs/plans/KMK_EVALUATION_MODE_UNDO_MASTER_IMPLEMENTATION_PLAN_2026-07-25.md` against the Phase 0 audit's
contracts, in one continuous session.

- **New typed journals** (same in-memory/Evaluation-Mode-only/build-before-write-commit-after-success
  contract as the existing taste/group journals): `LibraryUndoJournal`/`LibraryUndoService`/
  `LibraryUndoRecorder` (favorite/category), `PreferenceUndoJournal`/`PreferenceUndoService`/
  `PreferenceUndoRecorder` (generic, typed per-preference `PreferenceUndoEntry<T>` — no
  `Map<String,Any>`/JSON blob), `ChapterUndoJournal`/`ChapterUndoService`/`ChapterUndoRecorder`
  (read/bookmark, with a shared 500-chapter bound policy for oversized "all chapters" operations).
- **Phase 1 wired:** library favorite (manga detail, bulk add/remove, `BulkFavoriteScreenModel`),
  category assignment, tag preference add/remove, 5 numeric/boolean recommendation settings, language
  selection, source order, Sources To Try dismissal/clear, reading schedule. **Deferred:** the
  `toggleSource` (For You source exclusion) preference — it's `recommendation_disabled_source`-table-
  backed via a dedicated interactor rather than a `Preference<T>`, needing its own small typed entry
  not built this pass.
- **Phase 2 wired:** `MangaScreenModel.bookmarkChapters()` only, as the one representative,
  tracker-decoupled chapter-state call site. **Deferred, explicitly:** `markChaptersRead` (manga
  detail), `markReadSelection` (Library bulk), `markUpdatesRead`/`bookmarkUpdates` (Updates screen),
  and the reader-completion read-state batch — the infra (`ChapterUndoJournal` family) is ready for all
  of them, but the remaining call sites were not wired this session.
- **Phase 3 wired:** source-quality marks (installed + Sources To Try) reuse `PreferenceUndoJournal`
  directly via a composite `Triple`-shaped `SourceQualityMarkPolicy.State` entry, restored atomically.
  Evaluation run/reset/reassess/install/uninstall remain outside the journal, as required.
- **Phase 4:** Best Version migration confirm dialog now shows an explicit, honest "this cannot be
  automatically undone" warning (new `best_version_migrate_not_undoable` string) and fixes a raw
  unlocalized `"Cancel"` string found during this pass. No migration Undo was added;
  `MigrateMangaUseCase` semantics are unchanged.
- **Phase 5:** `EvaluationModeActionHistoryScreen` now unifies all four safe journals (taste, group,
  library, preference — chapter journal deliberately not yet wired into the screen, see below) into one
  newest-first list via a shared `HistoryRow` shape, with per-journal-family localized summaries and a
  single "Clear history" action that clears all four journals.
- **Documented, real limitation found this pass:** `UpdateChapter`/`SetMangaCategories` (shared,
  pre-existing interactors used by every write path in the app, not touched this pass) swallow
  exceptions internally and never signal failure, so a persistence-layer failure during a chapter/
  category Undo restore is not currently distinguishable from success. Covered by a documented-
  limitation test (`ChapterUndoServiceRestoreTest`) rather than silently asserted as correct.
- **Tests:** ~70 new focused tests across `LibraryUndoJournalTest`, `LibraryUndoServiceTest`,
  `LibraryUndoServiceRestoreTest`, `PreferenceUndoJournalTest`, `PreferenceUndoServiceTest`,
  `PreferenceUndoRecorderTest`, `SourceQualityMarkUndoTest`, `ChapterUndoJournalTest`,
  `ChapterUndoServiceRestoreTest`. Full `:app:testDebugUnitTest` passes (including one pre-existing
  Injekt-registration test fixed to account for `BulkFavoriteScreenModel`'s new constructor param).
  `spotlessApply`/`spotlessCheck`/`:app:compileDebugKotlin`/`:app:assembleDebug` all pass. Not
  committed to git, not installed on the tablet, no Device QA performed this pass.

Previous (2026-07-24): **Group-action Undo Journal (merge / remove-from-group / ungroup) — implemented and
verified.** Driven by `private/docs/evidence-and-qa/KMK_FEATURE_EVIDENCE_PROGRESS.md`, whose remaining grouping evidence
routes (merge, remove, ungroup, conflict handling) were blocked because those mutations had no safe
undo. Adds a sibling journal to the rating Undo Journal below, covering the actual cross-source-link/
primary-version data:

- **New files:** `app/src/main/java/exh/util/GroupUndoJournal.kt` (typed snapshot models +
  bounded 10-entry in-memory store, `GroupJournalEntry`/`GroupLinkSnapshot`/`GroupPrimarySnapshot`),
  `GroupUndoRecorder.kt` (build-before-write/commit-after-success snapshot builders, Evaluation-Mode-gated),
  `GroupUndoService.kt` (conflict detection + atomic restore).
- **New repository/interactor plumbing (no schema migration needed):**
  `TasteRepository.deleteCrossSourceGroupCompletely()` (atomic link+primary delete — closes a real
  pre-existing non-atomicity gap in `ungroup()`, independent of undo) and
  `TasteRepository.restoreCrossSourceGroupState()` (the single transactional write path every group
  undo goes through), plus their interactors, registered in `KMKDomainModule`.
- **Wired into `LovedMangaScreenModel.mergeSelectedIntoGroup/removeSelectedFromGroup/ungroup`**, each
  now returning a committed journal entry id (or `null` outside Evaluation Mode) for the UI to offer
  Undo. `RatedMangaScreen` now shows real result feedback for all three actions (merge and ungroup
  previously showed no feedback at all -- a separate pre-existing gap this closed as a side effect) with
  an Undo action wired to `GroupUndoService`.
- **Conflict safety:** identical contract to the rating journal — undo refuses to restore (reports
  conflict, never force-restores) if any touched link/primary row no longer matches the entry's own
  recorded post-action state.
- **Atomicity:** restore is one SQLDelight transaction; a simulated mid-transaction failure leaves state
  completely unchanged and keeps the entry in the journal for retry (`GroupUndoServiceRestoreTest`).
- **Scope:** "not the same manga"/disassociation and "conflict handling for entries in different
  groups" do not exist as separate features in the codebase — the latter is already exactly what
  `RatedGroupMergePlanner`'s multi-group-fold does; no new feature was invented to satisfy the evidence
  checklist. See `docs/community/KMK_EVALUATION_MODE_UNDO_JOURNAL.md`'s "Group-action Undo Journal"
  section for the full record, including why this is a separate journal from the rating one.
- **Tests:** `GroupUndoJournalTest` (8 tests), `GroupUndoServiceTest` (7 pure conflict-logic tests),
  `GroupUndoServiceRestoreTest` (9 interactor-level tests: merge/remove/ungroup restore, conflict
  detection, transaction rollback, cancellation propagation, partial-success snapshot accuracy,
  Evaluation-Mode-off). Uses an extended `FakeTasteRepository` (now with real cross-source-link/primary
  semantics and a transaction-failure test hook) alongside the existing `FakePreferenceStore`.
- **Not performed this pass, by explicit scope:** no ADB actions, screenshot capture, or device
  installation-for-testing. The blocked evidence routes in `private/docs/evidence-and-qa/KMK_FEATURE_EVIDENCE_PROGRESS.md` remain
  blocked pending a separate live-device validation pass.

Ran `spotlessApply`/`spotlessCheck`/`:app:compileDebugKotlin`/`:app:testDebugUnitTest` — all passed,
including the full existing suite (no regressions) plus the 24 new group-undo tests. Not committed to
git per instruction.

Previous (2026-07-23): **Evaluation Mode Action Undo Journal code-review follow-up — implemented and
verified.** A same-day code review found 5 real gaps in the initial implementation below; 4 are fixed,
1 remains open with a scoped tracked follow-up:

1. **Fixed — record-after-success ordering.** `EvaluationModeJournalRecorder`'s `record*` functions were
   renamed to `build*` (build the not-yet-committed entry, no journal write) plus a new `commit(entries)`
   that callers now invoke only after their write succeeds — previously a failed write could leave a
   stale journal entry describing an action that never happened. All four call sites
   (`BrowsePersonalRecommendationsScreenModel`, `LovedMangaScreenModel`) updated, including per-item
   commit in the two per-item-try/catch bulk-clear paths so a partial-batch failure only journals what
   actually changed.
2. **Fixed — history screen now reacts live to Evaluation Mode being disabled.**
   `EvaluationModeActionHistoryScreen` now calls `rememberEvaluationModeEnabled()` and pops itself the
   moment the setting flips off while the screen is open, instead of only gating at its Settings-row
   entry point.
3. **Fixed — real interactor-level restore test coverage added.** New `EvaluationModeUndoServiceRestoreTest`
   exercises `EvaluationModeUndoService.restoreOne()`'s actual `GetMangaTaste`/`SetMangaTaste`/
   `ClearMangaTaste` calls against real interactors, using two new lightweight test doubles
   (`FakePreferenceStore`, `FakeTasteRepository`) instead of a database — both `PreferenceStore` and
   `TasteRepository` were confirmed to be plain interfaces with no framework coupling before writing them.
4. **Fixed — eviction is now bulk-operation-aware.** `EvaluationModeUndoJournal.record()`'s eviction loop
   now evicts a whole bulk group together when the oldest entry belongs to one, instead of evicting single
   entries regardless of grouping — a bulk Undo group can no longer be left partially evicted.
5. **Open, tracked, not implemented this pass — For You still has no inline Undo.**
   `BrowsePersonalRecommendationsTab` still shows only a Toast (no Snackbar/Undo) because it renders
   inside `BrowseTab`'s shared `Scaffold`, which has no `SnackbarHostState` and affects every Browse tab,
   not just For You — a real, scoped follow-up (tracked in `docs/recommendations/NEXT_WORK.md`), not a
   drive-by fix. **Mitigation:** For You's writes already go through the same journal-recording path as
   every other screen, so those actions remain undo-able via `EvaluationModeActionHistoryScreen` even
   without inline Undo — the safety net still covers For You, just not through its own inline feedback.

See `docs/community/KMK_EVALUATION_MODE_UNDO_JOURNAL.md` for the full updated decision record. Re-ran
`spotlessApply`/`:app:compileDebugKotlin`/`:app:testDebugUnitTest` after these fixes — all passed,
including the 5 new restore tests. Not committed to git per instruction; not reinstalled on the tablet
this pass (requires fresh explicit permission).

Previous (2026-07-23): **Evaluation Mode Action Undo Journal — initial implementation.** New
bounded, **in-memory-only** (never persisted, never backed up/synced) undo journal for
rating/Clear-Rating/Not-Interested test actions performed while Evaluation Mode is enabled — see
`docs/community/KMK_EVALUATION_MODE_UNDO_JOURNAL.md` for the full decision record.

- **New files:** `app/src/main/java/exh/util/EvaluationModeUndoJournal.kt` (bounded 20-entry in-memory
  store, `EvaluationJournalEntry`/`EvaluationUndoOutcome` models),
  `EvaluationModeJournalRecorder.kt` (shared recording helper, no-op when Evaluation Mode is off),
  `EvaluationModeUndoService.kt` (typed-inverse restore logic + conflict detection),
  `EvaluationModeActionHistoryScreen.kt` (Evaluation Mode-only history/Undo/clear-all UI).
- **Wired into the shared action boundary**, not duplicated in the UI: `BrowsePersonalRecommendationsScreenModel.rateSelected/
  clearSelectedRatings/markSelectedNotInterested` (For You) and `LovedMangaScreenModel.changeSelectedRating/
  clearSelectedRatings/markSelectedNotInterested` (Loved/Liked/Disliked) each call the recorder before
  their existing write, so single-item and bulk actions cannot bypass journaling.
- **Persistence decision:** in-memory only, documented reasoning in the dedicated doc — matches
  `EvaluationModeFormatter`'s existing process-lifetime-only precedent, eliminates the entire
  termination-corruption risk class by construction, and needs no migration/backup-sync opt-out design.
- **Conflict safety:** undo re-reads current state and refuses to restore (reports a conflict, never
  force-restores) if the manga changed after the journaled action — pure logic in
  `evaluationUndoHasConflict()`.
- **Gating:** `EvaluationModeJournalRecorder`'s functions all short-circuit on
  `!sourcePreferences.evaluationMode().get()`; the history screen's Settings nav row is only rendered
  when Evaluation Mode is on (`listOfNotNull` guard in `SettingsAdvancedScreen.getDeveloperToolsGroup()`).
- **Scope:** Set Love/Like/Dislike, Clear Rating, Not Interested, and their bulk forms only. Grouping/
  ungrouping/merging, migration, library deletion, source install/removal are explicitly NOT supported
  in this pass — no safe existing typed inverse was found; documented as deferred, not attempted.
- **Tests:** `EvaluationModeUndoJournalTest` (8 tests: record, eviction bound, bulk grouping, removal,
  ordering) and `EvaluationModeUndoServiceTest` (7 tests: pure conflict detection, outcome
  classification). The DB/preference-touching restore orchestration itself is not integration-tested —
  documented reason: `SourcePreferences` has no lightweight fake anywhere in this test suite.
- **Not touched this pass:** Merge/Remove-from-group/Ungroup remain without any undo mechanism (already
  documented pre-existing limitation, unchanged).

`spotlessApply`/`spotlessCheck`/`:app:compileDebugKotlin`/`:app:testDebugUnitTest` all run and passed;
results recorded in the pass's final chat report. Not committed to git per instruction.

Previous (2026-07-23): **Bulk-action result feedback — implemented and verified.** Fixed a real
false-success bug and unified bulk-action wording across For You, Loved, Liked, and Disliked. New
shared `app/src/main/java/exh/recs/BulkTasteActionFeedback.kt` (`BulkTasteOutcome`,
`BulkTasteActionType`, `bulkTasteActionMessage()`), reused by `BrowsePersonalRecommendationsScreenModel`/
`Tab` (For You) and `LovedMangaScreenModel`/`RatedMangaScreen` (Loved/Liked/Disliked — all three ratings
share this ScreenModel via `filterRating`).

- **Bug fixed:** `LovedMangaScreenModel.clearSelectedRatings()`/`markSelectedNotInterested()` were
  fire-and-forget `fun`s with discarded per-item `runCatching` results; `RatedMangaScreen.kt`'s confirm
  dialogs showed the "cleared"/"marked not interested" Snackbar unconditionally whenever the pre-action
  selection was non-empty, regardless of whether the write actually succeeded — a partial or total
  failure looked identical to full success. Both are now `suspend fun` returning a real
  `BulkTasteOutcome`; the Snackbar now reflects the true result and only offers Undo for items
  durably changed. `clearSelectedRatings()` also now returns exactly which entries were cleared (not
  just a count) so Undo restores the correct items under partial failure.
- **`runCatching`-swallows-cancellation bug fixed:** `BrowsePersonalRecommendationsScreenModel.rateSelected/
  markSelectedNotInterested/clearSelectedRatings` and `LovedMangaScreenModel`'s corresponding functions
  used `runCatching`, which also catches `CancellationException` — replaced with explicit try/catch that
  rethrows cancellation, matching the project's established `SourceRuntime`-era convention.
- **Wording unified:** messages now name the specific action ("5 manga rated Love", "3 manga rated
  Like; 1 failed", "1 manga rating cleared", "5 manga marked as Not Interested") with correct
  singular/plural, replacing For You's previous generic "Done: N manga updated" toast text. New KMR
  string resources in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
  (`rec_bulk_action_*`), reusing the existing `rated_manga_rating_love/like/dislike` labels for the
  action name rather than introducing new hardcoded English text.
- **Undo:** Loved/Liked/Disliked's Clear Rating/Not Interested keep their existing Snackbar+Undo
  (`MR.strings.action_undo`), now gated on the real outcome; Undo itself now reports if it partially
  fails (`rec_bulk_action_undo_partial_failure`). For You's bulk actions remain toast-only, no Undo —
  documented reason: no `SnackbarHostState` is wired into that Tab's Scaffold; adding one is a
  Scaffold-level structural change out of this pass's bounded scope, not silently omitted.
- **Tests:** new `app/src/test/java/exh/recs/BulkTasteActionFeedbackTest.kt` (9 tests) covering
  `BulkTasteOutcome`'s classification logic (single/multi success, partial failure, complete failure,
  no-op/skip, the rating-to-action-type mapping). Message-text building itself requires an Android
  `Context` and is not unit-tested, consistent with this codebase's existing pattern for
  Context-dependent string resolution.
- **Not touched this pass:** Merge/Remove-from-group/Ungroup message polish (already tracked as
  deferred in the interaction audit); source-selection bulk actions (Sources To Try install already has
  real feedback per prior passes); For You Undo (documented above).

See `docs/community/KMK_RECS_INTERACTION_FUNCTIONALITY_AUDIT.md` for the updated table rows and
`docs/recommendations/NEXT_WORK.md` for the remaining open items.

Previous (2026-07-23): **Current Komikku-standard alignment pass — implemented and verified.** The
follow-up closes the two persistence/source-isolation gaps found in the prior review and records an
exhaustive active-call classification in
`docs/community/KMK_SOURCE_RUNTIME_EXHAUSTIVE_INVENTORY_2026-07-23.md`:

- **Atomic extension-error replacement.** Added `SourceEvaluationRepository.replaceByPackage()`
  (`data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt`), backed by a new
  `ReplaceSourceEvaluation` interactor (`domain/src/main/java/tachiyomi/domain/taste/interactor/`)
  registered in `KMKDomainModule.kt`. Delete-then-upsert now runs inside one
  `handler.await(inTransaction = true)` block instead of two separate interactor calls — a failure at
  either step rolls back the whole transaction, so stale rows can never be deleted without their
  replacement being written (or vice versa). `SourceEvaluationRunner.recordExtensionError()` now calls
  this one atomic operation; the sequence-level-only `reconcileExtensionErrorRow()` helper (private
  convention correction pass, 2026-07-22) was deleted as obsolete.
- **Persistence-contract and real-database coverage.** Kept the 7 domain-level fake contract tests and
  added `app/src/test/java/tachiyomi/data/taste/SourceEvaluationRepositoryTransactionTest.kt`. The new
  tests exercise the generated SQLDelight database and `SourceEvaluationRepositoryImpl` against an
  in-memory SQLite driver, proving both successful replacement and rollback after a failure following
  the delete statement.
- **SourceRuntime inventory and corrections.** The active source-method search is now classified in the
  dedicated inventory rather than described as a spot-check. Three additional bypasses were corrected:
  `LibraryScreenModel.syncMangaToDex()`, `SettingsMangadexScreen.loginPreference()`, and
  `MangaDexLoginActivity` login/logout. Downloader data-saver image fetching now uses the new
  `SourceRuntimeOperation.Image`; reader image URL/image loading is explicitly source-typed and guarded.
  Lower-layer paging calls, source implementation overrides, tracker APIs, and application-owned network
  calls are documented with reasons rather than incorrectly wrapped.

`:app:compileDebugKotlin`, full `:app:testDebugUnitTest`, focused real-database/domain tests, and
`spotlessCheck` all passed in this follow-up. The broader release-test and preview-assemble gates from
the prior pass remain as previously recorded; this follow-up was not committed to git per instruction.

Previous (2026-07-22): `v0.8.19` — **implemented; pending device verification**. Adds a developer-only
"Evaluation Mode" setting (Settings > Advanced > Developer tools) that relabels source, extension, and
repository names (plus preferred/blocked tag labels) with generic placeholders everywhere they're shown,
so screenshots/recordings can be captured without exposing private source/repository identity. Manga
titles, including disliked-manga titles, are never touched. `KmkRecsReleaseNotes` bumped `776`/
`"v0.8.18-fix1"` -> `777`/`"v0.8.19"` per explicit user approval. Coverage matrix:
`docs/community/KMK_EVALUATION_MODE_VERIFICATION_MATRIX.md` (26 surfaces; 21 resolved by code
inspection, 4 open/unread, 1 documented workflow risk). Full audit trail:
`docs/community/KMK_FULL_PLATFORM_PROFESSIONAL_READINESS_AUDIT.md`. No device screenshots were captured
for this entry -- **pending device verification** until a human confirms on-device.

Also (2026-07-22): **private convention correction pass -- implemented and verified.** Closed one
confirmed `SourceRuntime` bypass: `app/src/main/java/eu/kanade/tachiyomi/data/track/mdlist/MdList.kt`
called MangaDex tracker methods (`fetchTrackingInfo`/`updateFollowStatus`/`updateRating`/
`getSearchManga`/`getFilterList`/`getMangaDetails`/`getMangaMetadata`) directly and unguarded across
`update()`/`refresh()`/`search()`/`getMangaMetadata()`; now routed through `SourceRuntime.run`/
`runBlockingSourceCall`. Added `reconcileExtensionErrorRow()` (extracted from
`SourceEvaluationRunner.recordExtensionError()`, no behavior change) plus a new fake-based regression
test suite proving the delete-before-upsert ordering, failure, and cancellation contract. Resource/
settings-search compliance checked -- no defect found. See
`docs/community/KMK_KOMIKKU_CONVENTION_COMPLIANCE_AUDIT_2026-07-22.md` for the full reconciled findings
table and `docs/community/KMK_PRIVATE_CONVENTION_CORRECTION_PASS_2026-07-22.md` for the executed plan.
`spotlessApply`/`spotlessCheck`/`compileDebugKotlin`/focused tests all passed; `testReleaseUnitTest` and
`assemblePreview` results recorded in the pass's final chat report. Not committed to git per instruction.

Previous (2026-07-20): v0.8.18-fix1 **live-device QA and public evidence workflow complete**. Device QA
(`docs/community/KMK_RECS_V0_8_18_FIX1_DEVICE_QA.md`) found and fixed a real regression the original
v0.8.18-fix1 implementation had not yet been exercised against: Loved/Liked/Disliked crashed on every
open (`IllegalStateException: TabNavigator not initialized`), root-caused to `RatedMangaScreen.kt` and
`SourceEvaluationScreen.kt` reading `LocalTabNavigator.current` from outside the `TabNavigator`'s
composition scope; fixed via a new `HomeScreen.Tab.Browse(toForYou = true)` + `HomeScreen.openTab(...)`
path. Also fixed a mislabeled quick-access tile ("For You sources" instead of "For You"). Re-verified:
compile, spotless, full test suite (1646 tests), and live-device retest all pass; the APK was rebuilt and
its filename corrected to `Komikku-v1.14.1-kmk.8.18-fix1-debug.apk` (was
`Komikku-v1.14.1-kmk.8.18.1-debug.apk`, which didn't match this project's fix-release naming convention).
`KmkRecsReleaseNotes` was **not** re-bumped for this correction -- it lands within the same unreleased
`776`/`"v0.8.18-fix1"` build. A repeatable public screenshot-evidence workflow was also built this
session: `docs/community/KMK_SOURCE_EVALUATION_SCREENSHOT_EVIDENCE_WORKFLOW.md` (guide) +
`scripts/kmk_capture_source_fit_evidence.ps1` (capture script), with a `docs/community/evidence/{raw,
sanitized}/` folder split (raw is git-ignored, local-only). The public source-fit feature brief
(`docs/community/KMK_SOURCE_EVALUATION_PUBLIC_FEATURE_BRIEF.md`) was reviewed and its text judged ready
for public/community use -- no screenshots have been captured/attached yet, which is the one remaining
blocking step before it's actually posted anywhere. Not committed to git per instruction.

Previous (2026-07-20): `v0.8.18-fix1` implementation was **complete** — implemented the private next-work queue found after
v0.8.18: numeric-settings slider/list audit (documentation only, no behavior change), extension export
file-copy moved to `Dispatchers.IO`, manga detail "Copy link" restored to the "More" menu, the right-edge
quick-access panel relocated off Recommendation Settings and onto For You/Loved/Liked/Disliked, and a
safe "Go to For You" app-bar action added to Source Evaluation. See
`docs/community/KMK_RECS_V0_8_18_FIX1_PRIVATE_NEXT_WORK_IMPLEMENTATION.md` for the full file list, test
results, and known limitations. `KmkRecsReleaseNotes` bumped `775`/`"v0.8.18"` → `776`/`"v0.8.18-fix1"`.
App version intentionally unchanged (`1.14.1`/`90`). Final APK:
`Komikku-v1.14.1-kmk.8.18-fix1-debug.apk`, copied to `C:\Users\USER\Downloads\Komikku\private\`. Not
committed to git per instruction. This is a private app fix, not a public PR extraction -- the separate
`docs/community/KMK_SOURCE_EVALUATION_PUBLIC_FEATURE_BRIEF.md` remains the sanitized public/upstream
companion and is untouched by this pass.

Previous (2026-07-20): `v0.8.18` was **complete** — all six phases (A: Source Evaluation app-bar cleanup,
B: Best Version origin baseline/unavailable-chapter correctness, C: reader-informed full-screen Best
Version preview, D: manga detail action-row decluttering, E: manual extension APK export, F: right-edge
Recommendation Settings quick-access panel). See
`docs/community/KMK_RECS_V0_8_18_CONSOLIDATED_IMPLEMENTATION.md` for full detail. This pass explicitly
supersedes/consolidates the separate v0.8.17-fix2 plan (Best Version origin/unavailable/reader-preview
work) -- fix2 was never implemented standalone. `KmkRecsReleaseNotes` bumped `774`/`"v0.8.17-fix1"` →
`775`/`"v0.8.18"`. App version intentionally unchanged (`1.14.1`/`90`) -- no phase required a bump.
Final APK: `Komikku-v1.14.1-kmk.8.18-debug.apk`, copied to `C:\Users\USER\Downloads\Komikku\private\`.
Not committed to git per instruction.

Previous (2026-07-20): `v0.8.17-fix1` is **complete** — all seven phases (A: For You selection actions/
feedback/Clear Rating, B: rate-other-versions continuation, C: Source Evaluation row-action alignment,
D: Recommendation Settings quick-access row, E: Best Version preview reliability, F: universal
action/readability cross-check, G: docs/versioning/build). See
`docs/community/KMK_RECS_V0_8_17_FIX1_LIVE_DEVICE_UI_ACTION_AND_BEST_VERSION_IMPLEMENTATION.md` for the
full phase-by-phase status. This is a live-device follow-up fix, not a recommendation-scoring release,
not an upstream Komikku reconciliation, and not a schema/backup change -- `app/build.gradle.kts`'s own
version was intentionally left unchanged (`1.14.1`/`90`, unmodified from v0.8.17), only
`KmkRecsReleaseNotes` was bumped (`773`/`"v0.8.17"` → `774`/`"v0.8.17-fix1"`). Final APK:
`Komikku-v1.14.1-kmk.8.17-fix1-debug.apk`, copied to `C:\Users\USER\Downloads\Komikku\private\`. Not
committed to git per instruction.

Previous (2026-07-20): `v0.8.17` is **complete** — all four phases (A: working-tree hygiene, B: universal
UI/action standardization audit, C: For You diagnostic-first quality tuning, D: Komikku `v1.14.1`
upstream reconciliation). See
`docs/community/KMK_RECS_V0_8_17_UNIVERSAL_UI_FOR_YOU_QUALITY_AND_1_14_1_IMPLEMENTATION.md` for the
full phase-by-phase status. Phase B re-audited Sources to Try, Rated manga collections, Group
recommendations, and Best Version -- all confirmed compliant, no changes needed; `RecommendationDiagnosticsSettingsScreen`
density was investigated and deliberately deferred (collapsing sections would break `ScrollToAnchorEffect`'s
search-anchor scrolling used across every Recommendation Settings screen, a real structural blocker, not a
shallow tweak candidate). Phase C added a tap-to-explain detail dialog to each For You source's status
line, built entirely on the pre-existing `RecommendationSourceStatus` diagnostic distinction (no new
pipeline instrumentation or scoring change) via a new `RecommendationSourceStatusExplanationPolicy`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `773`/`"KMK-Recs v0.8.17"` -- this pass
includes real user-facing recommendation changes (Phase C), unlike the Phase-D-only intermediate pass.
The app's own `versionName`/`versionCode` were also bumped: `1.14.0`/`89` → `1.14.1`/`90`, reconciling
four upstream commits (delegated-source-loading fix, a chapter-URL-hash migration, and two
`/repo.json`-suffix-duplication fixes) — see the implementation report for the exact file-by-file
reconciliation table and why `DisabledRepoMigration` (present upstream since versionCode 80, absent from
the KMK tree until now) was added fresh at KMK's own versionCode `90` rather than copied with its
original upstream version number. Final APK: `Komikku-v1.14.1-kmk.8.17-debug.apk`, copied to
`C:\Users\USER\Downloads\Komikku\private\`. Not committed to git per instruction.

Previous (2026-07-19): `v0.8.16-fix1` is **complete** — see
`docs/community/KMK_RECS_V0_8_16_FIX1_UI_READABILITY_AND_RESPONSIVE_POLISH_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `772`/`"KMK-Recs v0.8.16-fix1"`. A UI
readability/responsive-polish pass driven by a live ADB tablet audit (`UI_AUDIT_NOTES.md`) of the
v0.8.16 build, not a scoring, ranking, or Source Evaluation queue-logic change: (1) Source Evaluation
past-evaluation row subtitle bumped `bodySmall`→`bodyMedium` and the `Details`/`Errors`/`Install`
actions merged into one `FlowRow` that wraps on narrow width instead of always stacking; the stale-
reassessment completion card copy reworded from one success-reading sentence into two clearer sentences
("Finished reassessing... N source(s) were skipped this run") and the skipped-sources disclosure label
shortened to "N skipped source(s)" -- the underlying `SourceEvaluationStaleCompletionDisplayPolicy`
three-state model and its tests were already correct, so this was copy-only; (2) For You's selection
bottom bar is now width-aware (new `ForYouSelectionActionLayoutPolicy`): Love/Like/Dislike stay visible
at every width, Not interested/Find best version/Open move into a "More" overflow menu below 720dp --
every action remains reachable at every width, none were removed; (3) the "Preview For You" dialog is
now a full-screen `Dialog`+`Scaffold`/`AppBar` (matching Best Version's fullscreen dialogs) instead of a
420dp-capped `AlertDialog`, still strictly read-only (no `onClick` on any card/row, no source/network
calls); (4) Recommendation Settings index subtitles simplified -- Source Evaluation's index-row subtitle
no longer reuses the screen's own "Temporarily install non-installed extensions..." implementation-detail
text (new dedicated `rec_settings_index_evaluation_summary`, "Find sources that match your taste."),
search synonyms unchanged; (5) **Best Version preview image loading fixed structurally**: `SampledPage`
now carries a source-aware `PagePreview(index, imageUrl, source)` instead of a bare URL string, and all
three preview surfaces (thumbnail row, single-page fullscreen, candidate fullscreen) load through
`SubcomposeAsyncImage(model = page.preview, ...)` → the existing `PagePreviewFetcher`/`SourceRuntime`
boundary, with explicit loading/error content -- this fixes the audited bug where Comix reported "5/5
pages loaded" while every thumbnail rendered as a broken placeholder (raw-URL Coil loading bypassed
source-specific header/cache/runtime handling entirely); a candidate with zero usable sampled pages now
shows `PreviewError` instead of a false-success `Loaded` row with an empty strip. No schema/migration
changes, no recommendation-scoring/ranking/quarantine changes, no retired settings sections reintroduced.
7 new tests (`ForYouSelectionActionLayoutPolicyTest`, `BestVersionPreviewOutcomePolicyTest`,
`SampledPageTest`, plus updated `KmkRecsReleaseNotesTest` heading-order cases).

Previous (2026-07-19): `v0.8.16` is **complete** — see
`docs/community/KMK_RECS_V0_8_16_INTERACTION_CHANGELOG_AND_BEST_VERSION_POLISH_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `771`/`"KMK-Recs v0.8.16"`. An interaction/
changelog/Best Version polish pass, not a scoring or Source Evaluation behavior change: (1) "Find best
version" on manga detail is now a separate `MangaActionRow` button instead of a hidden entry inside the
Love/Like/Dislike/Seen dropdown, and its visibility depends only on `onFindBestVersionClicked != null`
(previously coupled to `onSeenClicked`); (2) Best Version candidate rows (confirmation, chapter
selection, preview) now show the source name, and a new `FullscreenCandidatePreviewDialog` shows every
sampled page for one candidate at once (read-only, no chapter navigation/mark-read/page saving); after a
migration/copy succeeds, Done now navigates to the target manga (`BestVersionMigrationCompletionPolicy`,
`navigator.replace(MangaScreen(targetId, true))`) instead of popping back to the origin, falling back to
`pop()` only if the target id could not be resolved -- candidates were already localized by
`SameMangaCandidateSearcher` before this pass, so `target.id` was already reliable; (3) For You gained
long-press multi-select (`ForYouSelectionPolicy`, keyed by `MangaIdentityKey`) with a bottom action bar:
bulk Love/Like/Dislike (`SetMangaTasteBatch`), Not interested (`SeenRecommendationMangaStore`), and
single-selection-only Find best version / Open; bulk add-to-library was evaluated and deliberately
deferred (documented, not a fake button) since it needs category-selection UX beyond this pass; (4) KMK
What's New (`KmkRecsWhatsNewScreen` only -- official upstream `WhatsNewScreen` untouched) now groups
entries by major.minor family via a new pure `KmkRecsReleaseNotesGroupingPolicy`, keeping the current
v0.8.x family expanded and collapsing older families (v0.7.x, v0.6.x, ...) behind a short summary; every
historical entry is preserved verbatim, only revealed on expand; (5) Source Evaluation doc/test hygiene:
`SourceEvaluationContinuationPolicyTest`'s stale comment (claiming the runner populates completed keys
"unconditionally on handoff") was corrected to describe the actual v0.8.15-fix1 durable-write-only
contract, and a new pure `SourceEvaluationExtensionErrorReconciliationPolicy` was extracted from
`SourceEvaluationRunner.recordExtensionError()`'s delete-then-upsert decision with direct test coverage
(full runner-level testing remains too heavy -- no fake/mock harness for the delete/upsert interactors in
this suite). No schema/migration changes. 21 new tests
(`BestVersionMigrationCompletionPolicyTest`, `ForYouSelectionPolicyTest`,
`KmkRecsReleaseNotesGroupingPolicyTest`, `SourceEvaluationExtensionErrorReconciliationPolicyTest`, plus
updated `KmkRecsReleaseNotesTest` cases).

Previous (2026-07-19): `v0.8.15-fix1` is **complete** — see
`docs/community/KMK_RECS_V0_8_15_FIX1_SOURCE_EVALUATION_STALE_QUEUE_AND_ROW_READABILITY_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `770`/`"KMK-Recs v0.8.15-fix1"`. Closes the
second cause of the live-device "Reassess outdated" false-progress bug that v0.8.15 left open: live
ADB/database testing found the action still advanced from 25 to 15 remaining and reported `Evaluation
completed`, while `source_evaluation` kept the same 48 stale rows. Root cause: the stale-reassessment
queue (`SourceEvaluationCandidateQueuePolicy.staleCandidates`) was built from `pool.allEligible`, which
deliberately does not apply explicit/adult-content blocking -- so an explicit-blocked extension could
still enter the stale queue. `SourceEvaluationRunner` correctly skipped it before any write (blocking
is honored), but still advanced its cursor-tracking set past it. Fixed with two independent guarantees:
(1) `staleCandidates(...)` now accepts `includeExplicit` and filters blocked-explicit extensions out of
the actionable list before it ever reaches the runner, mirroring how the unassessed queue's
`applyOptions` already behaves; (2) the runner's cursor-tracking set (`_completedCandidateKeys`,
renamed from `_completedCandidateKeys`) is now populated *only* when a candidate durably writes a
database row -- the deliberate explicit-skip branch no longer adds to it at all, so even a future
no-write skip path could never advance the cursor or count as completed work. Also hardened
`recordExtensionError()`'s delete-before-upsert step (v0.8.15): if the stale-row delete itself fails,
the candidate is no longer implicitly treated as durably handled -- `recordExtensionError()` now
returns whether the delete succeeded, and every caller propagates that as the candidate's durable-write
result, with a new `reconciliationFailedCount`/in-app note surfacing the honest outcome instead of a
silent retry-forever loop. Also delivered the clarified Source Evaluation row-action model: each row
can show `Details` (full catalogue evidence, unchanged), `Errors` (renamed from the generic "Show
details"/"Hide details" toggle; now also covers catalogue-level evaluation errors, and is only shown
when there is real error information), and `Install` (new -- reuses the existing
`extensionManager.installExtension(...)` path, shown only when
`SourceEvaluationRowActionPolicy.canOfferInstall` confirms the source is not already installed,
blocked/quarantined, or unavailable); plus a page-level "Sources to try" app-bar shortcut that
navigates to the existing `RecommendationNonInstalledDiscoverySettingsScreen` (no duplicate screen).
No schema/migration changes. 20 new tests
(`SourceEvaluationCandidateQueuePolicyTest` explicit-blocking cases, new
`SourceEvaluationRowActionPolicyTest`).

Previous (2026-07-19): `v0.8.15` implementation is complete — see
`docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `769`/`"KMK-Recs v0.8.15"`. Root-cause fix
for a live-device "Reassess outdated (25)" / "Evaluation completed" / no database change bug: 44 of 48
stale rows had a real `source_id`, but `SourceEvaluationRunner.recordExtensionError()` wrote its
extension-level error record via a fire-and-forget `scope.launch { ... }` (never awaited, so the batch
could report terminal before the write landed) and built its key from `sourceId = null`, which never
matched — and so never replaced — those existing per-source stale rows. Both fixed directly:
`recordExtensionError()` is now `suspend` and awaited from every caller, and it now deletes existing
`source_evaluation` rows for the failing package/signature (`SourceEvaluationRepository.deleteByPackage`,
already-existing API, no schema change) before upserting the one current extension-level row. The
runner's `_completedCandidateKeys` tracking (used for both stale-cursor advancement and the
end-of-run completion decision) was also moved from "candidate handed to the runner" to "candidate
durably wrote something" (extracted into a new pure `SourceEvaluationRunCompletionPolicy`); a batch
with candidates but zero durable writes now resolves to a new `NoActionableWork` terminal status with a
clear message instead of the generic `Evaluation completed`. Also: Source Evaluation past-evaluation
rows' main subtitle was compacted from a single dense line (extension, language, catalogue fit %,
metadata confidence, evidence strength, last-evaluated, all at once) to `"<verdict> • Last evaluated
..."`, with the dropped facts moved into the existing expandable evidence-details disclosure; three
Recommendation Settings summaries (same-manga preselect, enrichment cap, group preview budget) were
shortened to plainer first-view wording; two mojibake'd strings (an en dash and a multiplication sign)
were corrected to plain ASCII; a running `KMK_RECS_UNIVERSAL_UI_READABILITY_AUDIT.md` was added,
covering every KMK-added Recommendation Settings/For You surface (several verified compliant this pass
without changes, several explicitly deferred with reasons). No schema/migration changes. 8 new tests
(completion-policy decision table, new-status idle/terminal/lifecycle coverage).

Previously: `v0.8.14-fix1` is **complete** — see
`docs/community/KMK_RECS_V0_8_14_FIX1_RECOMMENDATION_SETTINGS_STRUCTURAL_COMPLETION_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `768`/`"KMK-Recs v0.8.14-fix1"`. Corrective
follow-up after live-device review confirmed v0.8.14 renamed/moved controls but never fully implemented
the intended structure: the "Sources and languages" screen is renamed "For You sources" and no longer
owns language selection -- `LanguageSelectorContent` moved to Management and diagnostics (new
"Recommendation languages" section, same preference key, zero behavior change), and the Preview For You
action moved from the bottom of the screen to the top. The read-only For You preview was replaced
entirely: it now shows a real snapshot (Top Picks + visible source rows, manga covers and titles) from
the last successful For You refresh, via a new `RecommendationForYouPreviewSnapshotStore` (compact
delimited-string preference, capped rows/manga-per-row, no descriptions/genres/scores) persisted by
`BrowsePersonalRecommendationsScreenModel` after a run finishes with at least one visible row and read
by `RecommendationsSettingsScreenModel` -- the preview dialog never triggers a source search, network
call beyond loading already-cached cover thumbnails, or manga/source navigation (every card omits
`onClick`). Source Evaluation's outdated-reassessment action was already actionable-count-only from
v0.8.13-fix1/v0.8.14; this pass reordered the primary "Reassess outdated" action ahead of the
excluded-sources note and demoted that note to a collapsed "N outdated source(s) outside this run"
disclosure so it no longer reads as blocking the action. Recommendation Settings search cleaned up:
category-level rows no longer show a subtitle that's identical to their own title (e.g. "Taste and
filters" / "Taste and filters"); "language" now routes to Management and diagnostics. Two icon-only
20-28dp tap targets in Source Evaluation's past-evaluation rows (`Show details`/`Hide details`, and the
row overflow menu) were widened to normal size. No schema/migration changes; one new preference
(`recommendation_for_you_preview_snapshot`). See the implementation report for the full file list and
test results.

Previously: `v0.8.14` is **complete** — see
`docs/community/KMK_RECS_V0_8_14_RECOMMENDATION_SETTINGS_STRUCTURAL_UX_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `767`/`"KMK-Recs v0.8.14"`. Structural
correction after live-device evidence (ADB) showed v0.8.13-fix1 still exposed 7 top-level
Recommendation Settings rows: the index is now exactly five sections — Sources and languages, Taste
and filters, Source Evaluation, Sources to try, Management and diagnostics. "For You" and "Matching
and versions" are retired as top-level destinations (files deleted); every control they owned moved
verbatim (same `RecommendationsSettingsScreenModel` methods/keys, zero preference-behavior change)
into Taste and filters or Management and diagnostics. Search routing and anchor-key tests updated to
match. Source Evaluation: the completion message after a stale-reassessment run now states the exact
excluded count instead of pointing at a separate note; a past-evaluation row that's outdated but
outside the current reassessment pool now reads "Outdated — not included in this run" instead of
"Outdated — reassess needed" (`EvaluationResultRow` gained `isActionableOutdated`, wired from
`SourceEvaluationOutdatedReconciliation.Result.workableOutdatedExtensionKeys`). Source Evaluation's
first screen also had remaining setup clutter (skip/explicit toggles, candidate diagnostics,
installer-mode selector) collapsed behind disclosures by default — installer mode still force-shows
whenever the installer isn't ready. A few remaining technical terms ("probe", "eligible") in primary
copy were replaced with plain wording. Reconciliation/completion-state contracts themselves were
already correct from v0.8.13-fix1 — no schema, preference, or migration changes. 1 new test
(`workableOutdatedExtensionKeys` membership) plus updated search-index/anchor-key fixtures.

Previously: `v0.8.13-fix1` is **complete** — see
`docs/community/KMK_RECS_V0_8_13_FIX1_RECOMMENDATION_SETTINGS_AND_EVALUATION_UX_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `766`/`"KMK-Recs v0.8.13-fix1"`. Fixed a
live-device-reproduced Recommendation Settings search flicker (`produceState`/`Crossfade` replaced
with synchronous `remember`); moved language selection from For You Display to Source Priority and
group-recommendation preview budget from For You Display to Matching and versions (source scope vs.
display density vs. cross-version matching — pure moves, no preference/behavior change); added a
read-only "Preview For You layout" dialog on Source Priority using only already-loaded settings state
(no network/source calls); made Source Evaluation's stale-reassessment completion state truthful
(`SourceEvaluationStaleCompletionDisplayPolicy` now distinguishes "all actionable reassessed" from
"all actionable reassessed, N remain excluded"); collapsed Source Evaluation's quarantine/blocked
diagnostics by default; verified `searchSource()` is no longer a compiler-instruction-limit risk
(~350 lines after the v0.8.13 helper extraction) and merged duplicate v0.8.13 encyclopedia rows.
Fallback/provenance UI in source status details remains deferred (documented — needs new
`RecommendationsSettingsScreenModel` data plumbing). 18 new/updated tests. No migration.

Previously: `v0.8.13` is **complete** — see
`docs/community/KMK_RECS_V0_8_13_FOR_YOU_STRATEGY_RECOVERY_AND_RELEVANCE_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `765`/`"KMK-Recs v0.8.13"`. Live-device
audit (the affected source) confirmed two structural For You bugs beyond the v0.8.12 catalogue fallback: (1)
a persisted `TEXT_ONLY_TOP_TAGS` strategy was terminal and never actively cleared on failure,
permanently trapping a source in the least reliable search strategy — fixed with a new
`RecommendationStrategyRecoveryPolicy` that decides whether a persisted hint is still trustworthy,
plus active strategy-forgetting on `NoMatches`/`FilteredOut`/`Error`/`HiddenByDuplicateHandling`, plus
a rewritten `RecommendationQueryPlanner.buildPlans` (now rotates the shared
`RecommendationQueryAttemptPolicy` chain instead of a separate terminal fallback chain) so no valid
last strategy can eliminate the other attempts; (2) source affinity alone could make an unrelated
candidate eligible for display — confirmed live in `recommendation_candidate_memory` rows with a
positive score and null `matched_groups_json` — fixed by a `requirePositiveTasteEvidence` gate
(default true) on `PersonalRecommendationScorer.rankCandidates` and the same gate applied directly in
`RecommendationCandidateMemoryRanker.merge`. Also: a new `RecommendationAdditionalPagePolicy` stops
extra-page discovery for a zero-raw page-1 query (confirmed live: many the affected source progress rows
advancing pages 2-8+ for `TEXT_ONLY_TOP_TAGS` with `raw_count=0`); the catalogue fallback now records
itself in candidate memory/progress under a named `CATALOGUE_FALLBACK` query-strategy constant for
diagnosability; `searchSource()` refactored into five smaller helpers (`processRawCandidates`,
`mergeFreshAndRememberedCandidates`, `tryCatalogueFallback`, `resolveEffectiveStrategy`,
`finalEmptyOutcome`), deduplicating logic that previously existed twice. Source status strings
clarified for `NoMatches`/`FilteredOut`. Full per-source fallback/no-evidence provenance UI in
Recommendation Settings deliberately scoped down to string-clarity only — see the implementation
report for the reasoning. 19 new tests, several existing test files updated for the new default
relevance gate. No migration.

Previously: `v0.8.12-fix1` is **complete** — see
`docs/community/KMK_RECS_V0_8_12_FIX1_FATAL_ERROR_CONTAINMENT_IMPLEMENTATION.md`.
`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `764`/`"KMK-Recs v0.8.12-fix1"`.
Corrective follow-up found during v0.8.12 review: two `catch (e: Error)` blocks in
`BrowsePersonalRecommendationsScreenModel.searchSource()` (the main tag-search attempt loop and the
v0.8.12 Popular-catalogue fallback) caught every `Error` subtype unconditionally instead of only
recoverable per-source failures, so a fatal VM error (`OutOfMemoryError`, `StackOverflowError`,
`ThreadDeath`) thrown by code running *between* `SourceRuntime.run()` calls (enrichment, scoring,
memory lookups) — outside SourceRuntime's own fatal-rethrow boundary — could have been silently
swallowed instead of propagating. Fixed by routing both catch blocks through the existing shared
`rethrowIfFatal()` helper before treating the caught `Error` as recoverable. Every other
`catch (e: Error)` site in the recommendation codebase was audited and already classified correctly
— only this file had the defect. Catalogue fallback behavior (bounded single-page probe, full
filter/enrich/score/merge pipeline, never marked as a successful strategy) reconfirmed unchanged. 11
new tests. No migration.

Previously: `v0.8.12` is **complete** (all workstreams A-G) — see
`docs/community/KMK_RECS_V0_8_12_RECOMMENDATION_SETTINGS_STRUCTURAL_FOLLOWUP_IMPLEMENTATION.md` for
the exact delivered split. `KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `763`/
`"KMK-Recs v0.8.12"`. Delivered: (A) `RecommendationLanguageAvailabilityPolicy` merges selected +
installed-source + available-extension languages so a selected non-English language can no longer
disappear from the chip list; (B) Same Manga Matching and Best Version Preview moved out of Source
Priority into a new "Matching and versions" destination; (C) investigated and confirmed the outdated-
reassessment button/count already used only the actionable count (a plan-vs-code discrepancy,
documented), extracted a tested `SourceEvaluationStaleCompletionDisplayPolicy`; (D)
`TasteSuggestionVisibilityPolicy` rewritten from boolean `expanded` to an integer visible-count so
Taste Suggestions and stored tag preferences both genuinely reveal 10 at a time ("Show N more"/
"Show all (N)"/"Show fewer") instead of revealing everything on one tap; (E) fixed a real raw-
exception-class-name leak in `ExceptionFormatter.formattedMessage` (`RecoverableSourceRuntimeException`
now unwraps to its real cause) and in `CrossExtensionMatchScreen`'s row error text; SourceRuntime
boundary itself unchanged, all recoverable-failure tests still pass; (F) targeted For You false
no-match fix for the the affected source report — root cause confirmed in code (the final, most-lenient
query attempt searches literal tag words as free text, which most source search backends AND-match
against titles only, so a source with no title/genre text-search overlap legitimately returns zero
raw results even with a populated catalogue); fixed via a new bounded, single-page Popular-catalogue
fallback probe (`RecommendationCatalogueFallbackPolicy`) gated on true-zero-raw-results-and-no-error,
reusing the exact same filter/enrich/score/merge pipeline as a normal successful attempt. 16 new/
updated tests. See the implementation report for the full file list and verification.

Previously: `v0.8.11` is **complete** (all phases A-H) — see
`docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_IMPLEMENTATION.md` for the exact
delivered/deferred split. `KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to `762`/
`"KMK-Recs v0.8.11"`. Delivered (Phases A-G of the plan): (1) the duplicate Recommendation Settings
index row that opened the exact same `SourceEvaluationScreen` as "Source Evaluation" was removed, and
the four previously-identical-looking Source Evaluation search entries now have distinct
titles/summaries and real anchors into the screen; a `dedupeKey`-based dedupe was added to
`RecommendationSettingsSearchIndex.search()` as a safety net; (2) ordinary toggle/list/action rows in
Recommendation Settings now use official `SwitchPreferenceWidget`/`ListPreferenceWidget`/
`TextPreferenceWidget`, and `SectionHeader` delegates to the official `PreferenceGroupHeader`; (3)
rating-derived Taste Suggestions are now grouped into independently-capped Preferred/Blocked sections
(`TasteSuggestionVisibilityPolicy`), matching the fix9 treatment already given to stored tag
preferences; (4) Sources To Try suggestion cards keep only Install visible, with Dismiss/like/dislike/
quality marks moved into one overflow menu; (5) Source Priority rows moved like/dislike-for-For-You
into an overflow menu, keeping drag/title/badges/switch visible, and the screen gained a distinct
"Best Version preview" section header; (6) `SourceEvaluationScreen` was reorganized into named
sections (Run evaluation, Installer and cleanup, Reassessment, For You search compatibility,
Diagnostics and recovery) with real scroll-to-anchor support, and For You search compatibility now
has an explainer distinguishing it from catalogue evaluation; (7) the For You top bar groups Loved/
Liked/Disliked under one "Rated manga" menu and moves Export Top Picks to overflow, leaving Refresh
and Settings as the two always-visible actions; (8) Phase H — created
`docs/community/KMK_RECS_INTERACTION_FUNCTIONALITY_AUDIT.md` defining a long-press/selection/bulk-
action/search/grouping standard and auditing KMK-added screens against it, and fixed the reported bug
where multi-select "Group" in Loved/Liked/Disliked did not reliably reflect a merge/ungroup: the root
cause was that `LovedMangaScreenModel.load()` only reloads reactively from
`getMangaTaste.subscribeAll()` (a Flow over `manga_taste`), while group actions only write to the
cross-source-link table via plain non-reactive suspend interactors — nothing told the screen to
reload, so the display kept showing the pre-merge grouping until an unrelated taste change
coincidentally re-triggered `load()` (exactly the reported workaround). Fixed by applying each
action's writes into the in-memory `linkGroupByKey`/`primaryByGroupId` immediately (new pure helper
`mergeLinkWritesIntoMap()`), and merge now reports success/failure via an explicit Snackbar instead
of no feedback. One interaction gap was found and documented, not fixed, as real layout work: search
is hidden (not disabled — the underlying filter still applies) while Loved/Liked/Disliked is in
selection mode. 15 new tests total across v0.8.11 (1468 -> 1483: 10 in Phases A-G, 5 in the grouping
fix). See the audit doc and `NEXT_WORK.md` for the one remaining documented gap.

Previously: `v0.8.10-fix9` was reported **partially complete** — see
`docs/community/KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_IMPLEMENTATION.md` for the exact
delivered/deferred split. `KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` bumped to
`761`/`"KMK-Recs v0.8.10-fix9"` (unlike fix1-fix8, this pass has a real user-visible change). Delivered:
(1) every historical KMK-Recs changelog entry (85 total, back to v0.4.2) now uses the same What's
Changed/New/Improve/Fix structure as recent entries, converted losslessly — the file comment
previously saying historical entries are "preserved exactly as it was written" in their original
"flat-bullet format" is now stale and was replaced; (2) Taste and Tags preferences are now grouped by
Preferred/Disliked/Blocked/Other via a new pure `TagPreferenceGroupingPolicy`, each group capped at 10
with Show more/fewer, so Blocked tags are reachable without scrolling past a long Preferred list; (3)
bounded source-runtime hygiene: `SuwayomiApi.kt`'s `client` field now goes through `safeClientOrNull()`
instead of a raw `source.client` read, and a new shared `rethrowIfFatal()` helper (reusing the existing
`core:common` `isRecoverableSourceRuntimeFailure()`/`unwrapSourceRuntimeCause()` classifier, not a
second one) is now called from `MigrateMangaUseCase.kt`/`LibraryUpdateJob.kt`/`MetadataUpdateJob.kt`'s
broad `catch (e: Throwable)` blocks so cancellation and fatal VM/system errors are rethrown instead of
silently swallowed. Deferred with documented reasoning: the broader Phase 3/4 Komikku-widget
conformance pass (migrating the remaining Recommendation Settings/Source Evaluation/Sources To
Try/Rated Collections screens onto `PreferenceGroupHeader`/`SwitchPreferenceWidget`/
`ListPreferenceWidget`), and the Recommendation Settings search "flicker" fix — investigation found
the KMK search screen's `Crossfade`/`produceState` pattern is structurally identical to official
Komikku's own `SettingsSearchScreen.kt`, disproving the plan's assumption that KMK deviates from
official behavior here; the user chose to document and defer rather than diverge from the official
pattern. 13 new tests (1455 -> 1468: 5 in `KmkRecsReleaseNotesTest`, 5 in the new
`TagPreferenceGroupingPolicyTest`, 3 in `SourceRuntimeTest`). `fix8`'s
`SourceRuntimeFailureRegistry` enforcement, the `okhttp-zstd` dependency, and all other fix8 protections
are unchanged and were re-verified intact (Phase 0 preflight) before this pass began. Final APK:
`Komikku-v1.14.0-kmk.8.10-fix9-debug.apk`.

Previously: `v0.8.10-fix8` was reported **complete**. Two related fixes: (1) the app's OkHttp
5.3.2 setup was missing `com.squareup.okhttp3:okhttp-zstd` -- a separate, optional artifact -- so any
extension (confirmed: AsuraScans) whose lazy client touches `okhttp3.zstd.Zstd` threw
`NoClassDefFoundError` on first use; added to the existing `okhttp` bundle in
`gradle/libs.versions.toml`, same `okhttp_version` ref, no OkHttp version change. (2) fix1-fix7 built a
`SourceRuntime` boundary that correctly classified and recorded a recoverable failure in
`SourceRuntimeFailureRegistry`, but the registry was advisory only -- nothing stopped the same
already-known-broken source from being touched, and potentially crashing again, on the very next call.
Fix8 made suppression enforced: `SourceRuntime.run()`/`runBlockingSourceCall()` now check
`SourceRuntimeFailureRegistry.isTemporarilyUnavailable(id)` before ever calling `source.block()` again,
returning `Result.failure(SourceTemporarilyUnavailableException(...))` without re-touching the source if
so; `CancellationException` and fatal non-`LinkageError` `Error`s are unaffected.
`safeClientOrNull()`/`safeHeadersOrNull()` and `MangaCoverFetcher.kt`/`PagePreviewFetcher.kt` needed no
code change -- they already fully delegate through `SourceRuntime`, so the new enforcement covers them
automatically. The required re-audit of ~19 named source/client/recommendation touch-point files found
all of them already covered by the fix1-fix7 migrations. No global `SafeHttpSource` proxy was
introduced. Enforcing suppression changed real behavior 3 pre-existing tests had encoded the old
advisory-only assumption into (a same-source repeated-failure count test, a lazy-client
second-touch-still-classifies test, and two same-source-mid-batch tests where a later item in the same
batch as a confirmed failure is now itself correctly suppressed rather than spuriously succeeding) --
all three were corrected to assert the new, intended behavior. 8 new tests (1447 -> 1455). See
`docs/community/KMK_RECS_V0_8_10_FIX8_ZSTD_AND_RUNTIME_SUPPRESSION_IMPLEMENTATION.md` for the full
report. `KmkRecsReleaseNotes` not bumped (matches fix1-fix7 precedent). Final APK:
`Komikku-v1.14.0-kmk.8.10-fix8-debug.apk`.

Previously: `v0.8.10-fix7` was reported **complete**. A newer real-device log confirmed the
AsuraScans `okhttp3.zstd.Zstd` crash could still reach Browse/For You/recommendation-related paths
after fix6 -- not because a call site skipped the `SourceRuntime` boundary, but because several call
sites correctly recorded the recoverable `LinkageError` in `SourceRuntimeFailureRegistry` and then
immediately rethrew that exact raw `Error` via `Result.getOrThrow()`, past any outer `catch (e:
Exception)`-only path. Fix7 added `RecoverableSourceRuntimeException`/
`getOrThrowSourceRuntimeException()` to `SourceRuntime.kt` (converting a recoverable failure into an
`Exception` for call sites that need exception-style flow, while still rethrowing
`CancellationException` and genuinely fatal errors unchanged -- a real defect in the plan's own
provided code, missing exactly that `CancellationException` guard, was found and fixed before
shipping). Migrated the plan's 4 named locations, then the required audit of every remaining
`Result.getOrThrow()` found and fixed **6 more genuine holes** via `UpdateMangaFromRemote`'s stored raw
`LinkageError`: `MergedSource.kt` (2 sites), `BrowseSourceScreenModel.kt`'s `changeMangaFavorite`,
`MangaScreenModel.kt`'s manga-detail refresh, `MigrationListScreenModel.kt` (3 sites), and
`GalleryAdder.kt` (a double hole through its own `retry()` helper). 2 pre-existing, broader-scope
error-handling gaps (`MigrateMangaUseCase.kt`, `LibraryUpdateJob.kt`/`MetadataUpdateJob.kt` swallow
fatal errors too broadly) were found and disclosed, not fixed -- out of this pass's specific
rethrow-containment scope. 4 new tests (1443 -> 1447). See
`docs/community/KMK_RECS_V0_8_10_FIX7_SOURCE_RUNTIME_RETHROW_CONTAINMENT_IMPLEMENTATION.md` for the
full report and audit table. `KmkRecsReleaseNotes` not bumped (matches fix1-fix6 precedent). Final
APK: `Komikku-v1.14.0-kmk.8.10-fix7-debug.apk`.

Previously: `v0.8.10-fix6` was reported **complete**. A newer real-device crash log
(`komikku_crash_logs_7.txt`, app version `1.14.0-47`, commit `65bc71b1c` == fix4) confirmed the same
`NoClassDefFoundError: okhttp3.zstd.Zstd` structural hazard still reaches call sites fix3/fix4/fix5 had
not yet migrated to the shared `SourceRuntime` boundary: `getFilterList()` in
`SourceFeedScreenModel.kt`/`FeedScreenModel.kt` (feed and saved-search paths), migration/smart-search
(`SmartSourceSearchEngine.kt`), the `RecommendationSource` delegate wrapper
(`RecommendationPagingSource.kt`), and WebView source-header reads -- found in **two separate files**
(`WebViewScreenModel.kt`, the plan's named target, and `WebViewActivity.kt`, an independent duplicate
of the identical unsafe pattern found during the required re-audit). Also fixed one more genuine hole
the re-audit surfaced: `HttpPageLoader.kt`'s `getPages()` cache-miss fallback call to
`source.getPageList(...)` was not actually covered by its surrounding `catch(Throwable)` (that catch
handles the *cache lookup's* exception, not the fallback expression's own exception). All 7 fixes
route through the existing `SourceRuntime`/`SourceRuntimeAccessors`/`SourceRuntimeFailureRegistry`/
core:common classifier -- no second classifier was created. 3 new tests (1440 -> 1443). One item
deferred with documented reasoning: `SuwayomiApi.kt`'s `source.client` read (a narrow tracker-specific
path; the plan itself flagged a `Call.Factory`/`OkHttpClient` type-mismatch uncertainty). See
`docs/community/KMK_RECS_V0_8_10_FIX6_REMAINING_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` for the
full report and audit table. `KmkRecsReleaseNotes` not bumped (matches fix1-fix5 precedent). Final
APK: `Komikku-v1.14.0-kmk.8.10-fix6-debug.apk`.

Previously: `v0.8.10-fix5` was reported **complete**. Live-device evidence after fix4
shipped proved fix4's source-runtime isolation was incomplete: AsuraScans still threw
`NoClassDefFoundError: okhttp3.zstd.Zstd`, now reached from `HttpSource.client`/`HttpSource.headers`
lazy-property reads and page-preview image fetches in `MangaCoverFetcher.kt`/`PagePreviewFetcher.kt`
(Coil cover/preview loading), not from any `SourceRuntime`-guarded *method* call (which is what fix3/
fix4 covered). Fix5 added `SourceRuntimeOperation.Client`/`Headers`/`CoverImage`/`PreviewImage`, new
`HttpSource.safeClientOrNull()`/`safeHeadersOrNull()` accessors routing through
`SourceRuntime.runBlockingSourceCall`, migrated both Coil fetchers to them, added a
`SourceRuntimeHealthReporter` mapping `SourceRuntimeFailureRegistry` entries to installed-extension
identity, and added a non-blocking source-health warning + recovery-actions dialog (Retry/Update/
Reinstall/Uninstall/Disable — each offered only when actually valid, never silently) to Source
Evaluation. Also applied the Phase 7 (optional) proactive-skip to `CrossExtensionGenreSearchSource`,
gated strictly to batch/GROUP_PREVIEW context. 6 new tests (1434 -> 1440). See
`docs/community/KMK_RECS_V0_8_10_FIX5_SOURCE_CLIENT_HEALTH_AND_RECOVERY_IMPLEMENTATION.md` for the
full report, including 3 source-runtime call-site families reviewed and deferred with documented
reasons (`SourceEvaluationRunner`/`SourceRecommendationFitProbe` iterate by extension not source id;
`BrowsePersonalRecommendationsScreenModel.searchSource()`'s cache-first path was judged too risky to
touch within this pass's scope). `KmkRecsReleaseNotes` not bumped (matches fix1-fix4 precedent). Final
APK: `Komikku-v1.14.0-kmk.8.10-fix5-debug.apk`.

Previously: `v0.8.10-fix4` was reported **complete**, and is now understood to have been complete for
its own stated scope (source-*method*-call isolation) but not for the broader "source-runtime
isolation" framing — see the correction note atop
`docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md`. Live-device evidence
(`C:\Users\USER\Downloads\Komikku\kmk_fix3_live_crash_logcat.txt`) showed
`Komikku-v1.14.0-kmk.8.10-fix3-debug.apk` still crashed with the installed AsuraScans extension
present -- `NoClassDefFoundError: okhttp3.zstd.Zstd` still reached `GlobalExceptionHandler`/
`CrashActivity` when opening For You/Browse/source-related flows. The confirmed root cause was
`BrowseSourceScreenModel.kt`'s `init` block calling `source.getFilterList()` with no try/catch
at all. Fix4 migrated that plus the remaining direct source-method call families listed in
`docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_PLAN.md` to the shared
`SourceRuntime` boundary; see
`docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` for the
full report.
Date: 2026-07-09 (updated: 2026-07-18 -- v0.8.10-fix3 structural source-runtime isolation
**complete**. New shared boundary: `eu.kanade.tachiyomi.source.SourceRuntime` (app-layer execution
helper + failure registry) backed by pure classification functions
(`isRecoverableSourceRuntimeFailure`/`unwrapSourceRuntimeCause`) relocated to `core:common` mid-pass
to resolve a confirmed `app`→`data` module-dependency-direction blocker (full reasoning in the
implementation report). 16 call sites across official Browse/global search/feeds, library
update/bulk favorite, and KMK matching/Best Version/Source Evaluation/For You/group recommendations
migrated to the shared classifier; 7 more call-site families (`MangaScreenModel` related-manga,
reader `HttpPageLoader`, `Downloader`, `RecommendationPagingSource`,
`RecommendationCandidateEnricher`, `GroupRecommendationSeedBuilder`) were confirmed **already**
safely isolated via `runCatching`/`catch(Throwable)` by direct inspection, not assumed. One family
(`BrowseSourceScreenModel`'s remaining local `getFilterList()` calls) intentionally deferred as
lower-risk. 23 new tests (1408 -> 1431). See
`docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` for the
complete call-site inventory, module-boundary blocker, and verification. `KmkRecsReleaseNotes` not
bumped (matches the fix1/fix2 precedent for crash-isolation-only passes). Final APK:
`Komikku-v1.14.0-kmk.8.10-fix3-debug.apk`. Fix2 (below) remains an accurate historical record of the
narrow patch this fix3 pass superseded structurally, not a claim that fix2 itself was wrong or
incomplete for its own stated scope.

Previously updated: 2026-07-18 -- v0.8.10-fix2 (Asura extension-linkage crash isolation)
complete: fixed a confirmed application-wide crash where a broken/incompletely-packaged extension
(the installed Asura Scans extension) threw `NoClassDefFoundError: okhttp3.zstd.Zstd` while
constructing its HTTP client during a For You/group-recommendation request. Two call sites were
narrowed from "rethrow every Error" / "catch only Exception" to "recoverable LinkageError becomes a
per-source error row; genuinely fatal VM errors (OutOfMemoryError, StackOverflowError, ...) still
propagate": `RecommendsScreenModel`'s shared GROUP_PREVIEW boundary and
`BrowsePersonalRecommendationsScreenModel`'s per-source search loop (the actual "For You" tab, which
previously did not catch `Error` at all). New `RecommendationErrorKind.ExtensionIncompatible` +
`RecommendationErrorClassifier.isRecoverableSourceFailure()`; the shared official
`Throwable.formattedMessage` renderer now shows a sanitized "Extension incompatible or missing
dependency" message for any `LinkageError` instead of leaking the raw class-resolution detail. 19 new
tests. `KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` intentionally NOT bumped for this pass,
matching the v0.8.10-fix1 precedent (a narrowly-scoped DI/crash-isolation fix, not a KMK-Recs feature
changelog entry). Final APK: `Komikku-v1.14.0-kmk.8.10-fix2-debug.apk`. See
`docs/community/KMK_RECS_V0_8_10_FIX2_IMPLEMENTATION.md` for the full report. Previously updated:
2026-07-18 -- v0.8.10-fix1 (missing `UpdateMangaFromRemote` DI registration crash fix, see
`docs/community/KMK_RECS_V0_8_10_FIX1_CRASH_FIX_IMPLEMENTATION.md`).
Before that: 2026-07-17 -- v0.8.10 corrective/completion release complete: Phases
A-I of `docs/community/KMK_RECS_V0_8_10_0_8_9_COMPLETION_AND_1_14_VALIDATION_IMPLEMENTATION_PLAN.md`
all landed, verified, and committed; Phase J (device/accessibility/release verification) is
explicitly not executable in this environment -- disclosed as a blocker, not claimed passed. App
`versionName`/`versionCode` remain 1.14.0/89 (unchanged by v0.8.10 -- this release only touches the
KMK-Recs feature layer, not the Komikku app version). `KmkRecsReleaseNotes.VERSION_CODE`/
`VERSION_NAME` bumped 759/"KMK-Recs v0.8.9" -> 760/"KMK-Recs v0.8.10". Final APK:
`Komikku-v1.14.0-kmk.8.10-debug.apk`. See "v0.8.10 phase map" below for the full A-J breakdown, and
`docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md` for the earlier 9-phase 1.14.0
reconciliation this release's Phase I validated against.
Previously updated: KMK upstream 1.14.0 reconciliation complete (2026-07-17, app versionName/
versionCode bumped to 1.14.0/89); v0.8.9 implementation complete in code, manual-QA pending
(2026-07-16))

Status: Updated through KMK-Recs v0.8.10, with v0.8.10-fix3 planned as the next structural crash
repair (see "v0.8.10 phase map" below for the completed base release). Previously: v0.8.9
(official-style What's New entry structure going forward, Recommendation Settings search — see
below). Before that: v0.8.8 (schedule enforcement fix,
chapter-completion rating prompt, Recommendation Settings index, outdated-evaluation reconciliation
fix). Before that: v0.8.7
(Reading Schedule dialog root-cause fix, plus a partial Rated UI / Recommendation Settings refinement
pass). Before that: v0.8.6 (configurable group-recommendation preview budget, bounded
concurrency, timeout/cancellation/lifecycle fixes, cache reuse, and diagnostics for group
recommendations). v0.7.45 closed the original v0.7 feature line; v0.7.46/v0.7.47 were follow-up
correctness/hygiene passes; v0.8.0 was the first v0.8 feature build; v0.8.1-fix1 through v0.8.1-fix4
were corrective/polish passes; v0.8.2-v0.8.5 was one coordinated implementation session with four
internal milestones and a single final build; v0.8.6 implemented search-performance/loading
hardening for group recommendations across three passes in one session — the first pass shipped
budget/concurrency/timeout/cancellation/lifecycle/UI; a gap-closing pass wired the GROUP_PREVIEW
cache into the load path, added diagnostics logging, and extracted the concurrency/generation logic
into two small classes (`GroupPreviewLoadCoordinator`, `GenerationGuard`) with direct unit test
coverage; and a **reviewer-audit correctness pass** fixed two real bugs a code-level review found
after the first two passes: (1) nested detail-enrichment requests were bounded per source instance
only, allowing up to 4 sources x 10 requests = 40 concurrent detail calls instead of one shared
budget — fixed with a `sharedEnrichmentSemaphore` (default 8) passed from
`GroupPreviewLoadCoordinator` through `RecommendationPagingSource.createSources()` into every
`CrossExtensionGenreSearchSource`; (2) the GROUP_PREVIEW cache key's visibility fingerprint was
missing disabled-source state, source order, source-quality preferences, seen-entries state, and
taste/rating state — fixed with a new pure `GroupPreviewVisibilityFingerprint.build(...)` that covers
all of them, directly unit tested for the "changed input -> different fingerprint" property. Two
items remain open by explicit finding/scope decision rather than oversight:
`RecommendationQueryPlanner`/`RecommendationQueryAttemptPolicy` were audited and already satisfied
the plan's dedup/fallback requirements, so were left unmodified; and `RecommendsScreenModel` itself
still has no direct unit test coverage (no existing Injekt-mocking harness for it), though the logic
it delegates to is now tested in isolation. Device/manual QA (phone/tablet layout, real network
conditions) was not performed — no physical device was available in this environment. See
`docs/recommendations/KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_LOADING_IMPLEMENTATION.md` for full
detail. **This entire v0.8.x line is an internal/private handoff build for development — no public
release has been prepared or requested.**

### v0.8.10 Phase G decision: What's New completion (2026-07-17)

The v0.8.10 corrective plan required an explicit, documented decision between (1) keeping historical
`KmkRecsReleaseNotes.kt` entries unchanged with official New/Improve/Fix formatting applying from
v0.8.9 onward, or (2) mechanically converting historical entries to that structure with an audit
proving no meaning was lost. **Decision: Option 1 (keep-as-is)** — this was already the exact
decision v0.8.9 made and documented in-code (see the comment above `KmkRecsReleaseNotes.MARKDOWN`),
and `KmkRecsReleaseNotesTest.kt` already mechanically enforces it (no duplicate headings, no
truncation, correct ordering, official-structure checks scoped to the newest entry only). Reconciled
against the actual file rather than re-deciding from scratch: the real historical entry count is
**84** (`## KMK-Recs vX.Y.Z` headings, v0.4.2 through v0.8.9) — not the plan's stated "76." Retroactively
converting 83 historical entries remains, as v0.8.9 already found, a large, error-prone content
rewrite disproportionate to a pure formatting change, with no user-facing benefit (the existing
`GFMFlavourDescriptor`-based `MarkdownRender` already renders both the old flat-bullet form and the
new structured form correctly side by side in the same changelog). No code change was required for
this phase; the v0.8.10 entry itself will be added only once the v0.8.10 implementation is complete,
per the plan's explicit instruction.

### v0.8.10 Phase I: Komikku 1.14 compatibility validation (2026-07-17)

Validated the current tree against the completed 9-phase 1.14.0 reconciliation report
(`docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md`) and the real migration/backup
test suites — this was a validation pass, not a re-merge.

- **Migration history 45-63 append-only:** confirmed mechanically — every `.sqm` file number from 1
  through 63 exists exactly once with no gaps or duplicates. `KmkMigrationTest` (24 tests, real
  `JdbcSqliteDriver`) and `Kmk114ReconciliationMigrationTest` (3 tests, real SQLite execution of the
  hand-seeded pre-migration-63 baseline through migration 63) both re-run clean.
- **Stale `63.sqm` comment fixed:** the migration's header comment described the extension_store
  conversion and mangas/chapters.memo columns as "the remaining, not-yet-applied part of upstream
  1.14.0" — accurate when drafted, but misleading now that this migration is part of the completed,
  shipped reconciliation (reads as if the migration itself were still pending). Reworded to describe
  what the migration ports without implying it's outstanding work.
- **Proto fields 620-629:** confirmed additive-only and intact — 620 through 627 are in active use
  (manga tastes, tag tastes, tag aliases, disabled recommendation sources, cross-source manga links,
  manga source quality signals, seen manga keys, cross-source group primaries), 628-629 remain
  correctly reserved and unused. No renumbering, no collisions with upstream's own field ranges.
- **Broader compatibility claims** (source API compatibility for browse/search/migration/For You/
  group recommendations/Source Evaluation/Best Version; installer behavior; OCR/timer/schedule/
  chapter-completion rating/group recommendations after upstream API changes; 1.14 vs. KMK-Recs
  What's New separation; debug package/version metadata) were not re-derived from scratch in this
  phase — they were already validated in the original 9-phase reconciliation, and this phase's full
  `:app:testDebugUnitTest` run (1381 tests, 0 failures, 0 errors, spanning backup/sync/OCR/timer/
  schedule/migration suites together) re-confirms none of those areas regressed since. No code
  changes were required or made to any of those areas.
- **Known scope limitation, disclosed rather than silently accepted:** the plan calls for "a real
  upgrade test from a pre-1.14 KMK database containing library, ratings, cross-source groups, source
  preferences, sync preferences, OCR exclusion state, timer/schedule state, and extension repository
  settings" as a single combined scenario. What exists today is equivalent in substance but not in
  form: each of those areas has its own dedicated, real-SQLite migration/round-trip test (the 46-62
  per-migration tests, the migration-63-specific test, `TasteBackupRoundTripTest`,
  `Kmk114MemoBackupRoundTripTest`, `KmkOcrExclusionTest`, the reader timer/schedule tests, etc.) rather
  than one single test that seeds all of those areas simultaneously and migrates them together in one
  pass. This is a test-environment/effort-scope limitation, not a known functional gap — no evidence
  found of an actual interaction bug between these areas during migration — but it is recorded here
  explicitly rather than claimed as fully satisfying the plan's literal wording.

### v0.8.10 phase map (2026-07-17)

Explicit mapping of every phase in the v0.8.10 plan to its outcome, commit, and verification —
required by the plan's final-documentation-reconciliation step.

| Phase | Scope | Outcome | Commit |
| --- | --- | --- | --- |
| A | Defer chapter-completion rating prompt to reader exit | Done — new `ChapterCompletionPromptReducer` (pure, `None`/`PendingOnExit`), `ReaderViewModel`/`ReaderActivity` wired so the prompt fires on exit, never mid-read. 8 new tests. | `2f63c305a` |
| B | Recommendation Settings search gains stable per-control anchors | Done — `anchor` param threaded through every category screen, `ScrollToAnchorEffect`, search index expanded 7→29 entries. 12 new tests. | `9c25c7360` |
| C | Searchable Loved/Liked/Disliked rated manga collections | Done — `RatedMangaSearchFilter` (pure), `SearchToolbar` swap in `RatedMangaScreen`. 10 new tests. | `7004623e9` |
| D | Sources To Try search, sort, and truthful explanation | Done — `SourcesToTrySearchAndSort` (pure), sort chips, fixed a pre-existing silent-null explanation-text gap (`EvaluatedExplicitHeavy`/`EvaluatedEcchiHeavy`). 11 new tests. | `3c6c0f1b3` |
| E | Taste suggestions and diagnostics (largest net-new build) | Done — `TasteSuggestionAggregator`/`TasteDiagnosticsAggregator` (pure, separate from the live-scoring `GetTasteProfile`), new UI sections in Taste and Tags / Diagnostics settings screens, 19 new KMR strings. 18 new tests. | `f19841878` |
| F | Source Evaluation UI/state corrections | Audited in full against the plan checklist; most items already correctly fixed in prior versions (independent cursors, outdated-queue reconciliation, classified error text, job-conflict guarding, gated installer messaging) and left untouched. One confirmed gap found and fixed: the "Evaluation completed" summary persisted indefinitely across screen visits — new `SourceEvaluationCompletionLifecyclePolicy` clears it on screen leave. 8 new tests. | `17e8b04c9` |
| G | What's New completion decision | Decision: keep historical entries as-is (already v0.8.9's approach), official structure from v0.8.9 onward. Pinned the real entry count at 84 (plan stated "76"). No code change; documented here. | `10ea72efd` |
| H | Backup decoder hardening | Done — confirmed the real crash empirically (truncated/corrupt gzip and near-empty files threw uncaught `EOFException`, not the already-caught `SerializationException`). New pure `BackupDecoderErrorPolicy` classifies the real malformed-backup exception family; `BackupDecoder.decode()` now wraps its full detection+decode block. 8 new tests driving the real pipeline. | `8c6917cc6` |
| I | Komikku 1.14 compatibility validation | Done — confirmed migration history 1-63 is append-only with no gaps/duplicates (re-ran real-SQLite migration test suites), fixed stale "not-yet-applied" wording in `63.sqm`, confirmed backup proto fields 620-629 remain additive-only and intact, re-confirmed OCR/timer/schedule/backup/sync compatibility via the full suite. One scope limitation disclosed (see Phase I section above): no single combined-database migration test exists, though equivalent per-area coverage does. | `820153f99` |
| J | Device/accessibility/release verification | **Not executable in this environment** — no physical/emulated Android device, no accessibility scanner, no release-signing pipeline available here. Disclosed as an explicit, standing blocker, not silently skipped or claimed passed. Must be performed manually before any public release. | n/a |

v0.8.10 release-notes/version bump: `26966bb34`. Final APK build/copy/hash: see the implementation
report's final verification section (this same commit range).

## Feature Version

Current documented feature version:

```text
KMK-Recs v0.8.10
```

The About screen's `KMK-Recs What's new` entry (More/Settings > About) always shows this exact
version string as its subtitle, so the installed private KMK version is visible even if the
automatic update dialog was dismissed or never appeared. Tapping it opens the full changelog,
newest version first.

## Official-Style What's New And Recommendation Settings Search (v0.8.9)

Per `KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_PLAN.md` — see
`KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_IMPLEMENTATION.md` for full detail.

- **What's New**: audited the actual renderer (`WhatsNewScreen`'s `MarkdownRender`/
  `GFMFlavourDescriptor`) and found it's the same official renderer Komikku itself uses for its real
  upstream changelog, already fully supporting the target New/Improve/Fix hierarchy — no renderer
  changes were needed. Kept `KmkRecsReleaseNotes.MARKDOWN` as a plain Markdown string rather than a
  new structured data model (documented decision). Added the v0.8.9 entry in the official structure;
  all 76 pre-existing historical entries (v0.4.2 through v0.8.8) remain individually intact in their
  original format — not retroactively rewritten (disclosed scope decision, not a gap).
- **Recommendation Settings search**: audited the main Settings search
  (`SettingsSearchScreen.kt`) and found it's built entirely on the official `Preference`/
  `SearchableSettings` DSL, which none of the Recommendation Settings screens use (they're hand-built
  custom composables). Built a new, parallel `RecommendationSettingsSearchIndex` (pure, ranked,
  case-insensitive, punctuation-normalized, read-only) + `RecommendationSettingsSearchScreen`
  (UI shape copied structurally from `SettingsSearchScreen`) covering all 7 category-level
  destinations with the plan's required example search terms as synonyms. Category-level, not
  per-control with in-screen anchors (disclosed scope decision). 25 new tests total (8 What's New +
  17 search index), all passing; 111 total test result files, 0 failures.

## Schedule Enforcement Fix, Rating Prompt, Settings Index, Evaluation Reconciliation (v0.8.8)

Per `KMK_RECS_V0_8_7_FIX1_EXECUTABLE_IMPLEMENTATION_PLAN.md`,
`KMK_RECS_V0_8_8_EXECUTABLE_IMPLEMENTATION_PLAN.md`, and
`KMK_RECS_V0_8_7_AND_V0_8_8_DETAILED_EXECUTION_ADDENDUM.md` — see
`KMK_RECS_V0_8_7_FIX1_AND_V0_8_8_IMPLEMENTATION.md` for the full traced-pipeline findings, root
causes, and per-phase detail. Shipped as one release, `KMK-Recs v0.8.8`.

- **v0.8.7-fix1 — schedule enforcement (complete).** Found that reading-schedule restriction had
  *no actual enforcement* in the live tree — it only ever drove a toast; a reader opened while
  restricted could always read, and leaving+reopening (even a different manga) granted a fresh
  chapter-grace allowance every time. New `ReaderScheduleEntitlement` pure state machine
  (session-bound, never persisted) replaces the old `restricted && coordinator-idle` check;
  `ReaderViewModel.isChapterNavigationBlockedBySchedule()` is now checked directly by `init()`
  (initial/deep-link load), `loadAdjacent()` (manual selection + toolbar next/prev), and
  `loadNewChapter()` (natural forward paging — a separate bypass found and closed); a new full-screen
  blocking overlay in `ReaderActivity` makes the block visible and un-swipeable. 18 tests.
- **v0.8.8 Phase A — chapter-completion rating prompt.** New pure `LatestChapterCompletionPolicy`
  (8 tests) triggers a dismissible Love/Like/Dislike/Not-Interested prompt only on genuine completion
  of the *latest* available chapter (never a name/number comparison), deduplicated per chapter id,
  and never shown while the Phase 1 schedule gate is blocking. Reuses `SetMangaTaste` (the existing
  exclusive-rating mutation) and `GetCrossSourceMangaLinks` (existing confirmed-group check); step 2
  (offer to rate other versions) reuses `CrossExtensionMatchScreen.fromMode(...)` via a new,
  primitives-only `MainActivity` intent route (`OPEN_CROSS_EXTENSION_MATCH_FOR_RATING`) — no screen
  or match-mode object is ever serialized.
- **v0.8.8 Phase B — Recommendation Settings index (fully split, gap-closing pass).** New
  `RecommendationSettingsIndexScreen` mirroring `SettingsMainScreen`'s existing index pattern. All
  seven categories now route to real, distinct screens: Evaluation and Background/Installer both
  route to `SourceEvaluationScreen` (the latter has no distinct content outside that screen — a
  confirmed structural fact, not a decline); the other five (For You, Source Priority [+ Same-Manga
  Matching/Best Version, kept adjacent since neither is a named category], Taste/Tags, Non-installed
  Discovery, Diagnostics) each got their own new screen, extracted verbatim from the former single
  1,450+-line `RecommendationsSettingsScreen` (now deleted entirely). Shared composables moved to
  `RecommendationSettingsSharedComponents.kt` (`internal` visibility, zero duplication). Zero
  preference/behavior changes — `RecommendationsSettingsScreenModel` itself was not touched.
  Scroll-to-section is no longer applicable now that each category has its own bounded screen.
- **v0.8.8 Phase C — outdated-evaluation continuation fix.** Traced the full pipeline and found the
  reported "visible Outdated rows -> zero reassessment candidates" bug is a **display-vs-work
  eligibility mismatch**: the per-row "Outdated" label is computed purely from the stored evaluation
  row (version/expiry), while the reassessment queue is filtered from the current eligible candidate
  pool (excludes now-installed/language-mismatched/disliked/quarantined extensions) — a source can
  correctly show "Outdated" while being permanently unreachable this run, with the queue simply having
  nothing to do. New pure `SourceEvaluationOutdatedReconciliation` reconciles the two (rather than
  merging them into one policy, which would be architecturally wrong — see the implementation report)
  and a new UI banner explains the mismatch instead of a silent no-op. 10 tests, including a
  250-synthetic-source test that also drives the *existing* `SourceEvaluationContinuationPolicy`
  through real multi-batch continuation, proving count/work agreement, no duplicate processing, and
  no restart-from-beginning.

## Reading Schedule Fix And Rated UI/Settings Refinement (v0.8.7)

Per `KMK_RECS_V0_8_7_READING_SCHEDULE_DIALOG_ROOT_CAUSE_IMPLEMENTATION_PLAN.md` (fully implemented)
and `KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION_PLAN.md`
(partially implemented) — see
`KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION.md` for full detail.

**Reading Schedule fix (complete):** Confirmed root cause — `ReaderScheduleDialog.kt`'s
`AddWindowFlow` did a direct `context as? MainActivity` cast that silently failed whenever
`LocalContext.current` was a `ContextWrapper` rather than a literal `MainActivity`, closing the
weekday dialog with no time picker ever shown. `BiometricTimesScreen.kt` (the file cited as the
repo's "official pattern") has the identical bug — confirmed no safer existing helper actually
existed to reuse. Fixed: a new `Context.findActivity()` `ContextWrapper` unwrapper; a visible error
dialog instead of a silent no-op when no Activity exists; device 12h/24h time-format support instead
of hardcoded 24h; an explicit `allDay` flag on `ReaderScheduleWindow` (backward-compatible
serialization) for whole-day windows; real in-place Edit (was previously advertised but not
implemented); and Save-commits/Cancel-and-outside-dismiss-both-discard consistency. 11 new tests
across `ReaderScheduleStoreTest`/`ReaderScheduleResolverTest`.

**Rated UI / Recommendation Settings refinement (partial, across two passes):** Added "Select all in
group" to the Rated Manga bulk-selection bottom bar's More menu (backed by a new, tested
`RatedSelectionGroupResolver` pure conflict-resolution rule). Added real state-derived summary lines
to **6 of 9** Recommendation Settings section headers (Daily recommendations, Ratings and Known
Manga, Tags, Source Priority, Same-Manga Matching, Sources To Try — backed by tested
`RecommendationSettingsSectionSummaries.tagCounts`/`sourcePriorityCounts`). Added Undo (Snackbar with
an action label, reusing `LibraryTab.kt`'s existing merge-undo pattern) for 2 of the 5 bulk actions
the plan lists: Clear Rating and Mark Not Interested, via two new `LovedMangaScreenModel` methods
(`restoreRatings`/`undoMarkNotInterested`). Audited Source Priority's per-row action layout, Source
Evaluation, Sources To Try, Library quick access, and group management (`LinkGroupManagementScreen`)
against the plan and found them already substantially compliant (several — e.g. the conditional
Shizuku setup card, the full rated selection-mode/overflow-menu action set, Source Priority's
existing icon-button layout — were already shipped in prior sessions or judged already compliant) —
no changes made there. **Explicitly not implemented, after being attempted and judged too risky**:
expand/collapse for any Recommendation Settings section — the Source Priority section's
drag-and-drop reorderable list made this unsafe to verify without a physical device this late in the
session; static sections were judged separable future work. **Still not done**: summaries for Source
Evaluation/Management/Experimental (3 of 9), Undo for Merge/Remove From Group/Ungroup (would need a
link-group-graph snapshot), Source Priority per-row quality-dislike/explicit-block/reset/details (new
functionality, not a reorganization), and a full line-by-line audit of cross-extension matching and
Best Version workflow screens. 15 new tests total across
`RatedSelectionGroupResolverTest`/`RecommendationSettingsSectionSummariesTest`.

## Group Recommendation Search Performance And Loading (v0.8.6)

Per `KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_LOADING_IMPLEMENTATION_PLAN.md`, implemented across
Phases A-E in one continuous session — see
`KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_LOADING_IMPLEMENTATION.md` for the complete file list,
deviations, and known limitations. Summary:

- **New `GroupPreviewBudgetPolicy`** (5/10/15/20/30, default 10) and
  `SourcePreferences.groupPreviewResultBudget()` control the initial per-extension preview size for
  group (Loved/Liked/Rated-group) recommendation rows only — completely independent of
  `ForYouResultBudgetPolicy`/`recommendationResultBudget()` and never applied to normal global
  search. Exposed as "Initial results per extension" in Recommendation Settings, directly below the
  existing For You "Results per source" row.
- **New `RecommendationLoadContext` enum** (`FOR_YOU`/`GROUP_PREVIEW`/`FULL_SOURCE`) — documentation/
  vocabulary anchor; `RecommendsScreenModel` uses its existing `groupSeed != null` signal at the one
  call site that needed the distinction rather than threading the enum as a parameter.
- **Bounded concurrency**: `RecommendsScreenModel` now bounds concurrent GROUP_PREVIEW row
  fetch/enrichment to 4 via `Semaphore(4)`, with a 20s per-row timeout (`withTimeoutOrNull`,
  matching the existing `ADDITIONAL_PAGE_TIMEOUT_MS` convention). Single-manga/merged rows are
  unaffected.
- **Correctness fix**: `CancellationException` was previously swallowed by a catch-all `catch (e:
  Exception)` in the per-row loop, so cancelling a load could render as a row error. Now rethrown;
  fatal `Error`s are also never caught as recoverable.
- **Lifecycle**: the load coroutine now runs on `screenModelScope` (cancelled automatically on
  screen dispose) instead of the process-wide `ioCoroutineScope`, and a `loadGeneration`
  `AtomicInteger` guards every state mutation — functional scaffolding for a future refresh entry
  point, which does not exist yet (`RecommendsScreenModel` still only loads once in `init{}`).
- **`GroupPreviewCache`** (bounded in-memory, 5-minute TTL, 64-entry oldest-eviction, full key per
  plan section 10) is wired into `RecommendsScreenModel`'s load path: looked up before
  `requestNextPage`, stored after budget truncation. A hit skips the source call and the concurrency
  permit entirely. Never touched by single-manga/merged rows, For You, or global search.
- **Diagnostics logging** added via the existing `logcat` abstraction (tag `"RecommendsScreenModel"`):
  cache hit/miss, per-row raw/final candidate counts and elapsed time, timeout/cancellation/error
  events, and a per-load summary with total elapsed time and max observed concurrency. No
  cookies/auth/HTML/private params logged.
- **Concurrency/timeout/generation logic extracted** into `GroupPreviewLoadCoordinator` (bounded
  `runBounded()` + `observedMaxConcurrency()`) and `GenerationGuard` (`next()`/`isCurrent()`), both
  directly unit tested (9 tests total) without needing an Injekt-mocking harness for the full screen
  model.
- `RecommendationQueryPlanner`/`RecommendationQueryAttemptPolicy` were audited and found to already
  satisfy the plan's dedup/fallback/bound requirements — left unmodified.

## Configurable For You Results, UI Refinement, Reading Timer, Reading Schedule (v0.8.2-v0.8.5)

Per the master plan `KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md` and its four phase
plans, implemented as one coordinated session:

- **v0.8.2 — For You results budget.** New `ForYouResultBudgetPolicy` pure resolver and
  `SourcePreferences.recommendationResultBudget()` (5/10/15/20/30, default 10) control visible
  manga cards per ordinary For You source row. The top 3 boosted sources keep a 20-result floor
  (`max(configured, 20)`); selecting 30 raises boosted rows to 30 too. Included in the cache
  fingerprint so a smaller-budget cache can never satisfy a larger request. Source count, source
  priority, enrichment calls, Source Evaluation limits, Top Picks caps, and query-attempt limits are
  all untouched.
- **v0.8.3 — Recommendation Settings reorganized** into For You behavior → Source priority → Source
  evaluation → Source management → Discovery/cache management (composable reorder only — no
  screen-model, query, or scoring change). Loved/Liked/Disliked already shared one implementation
  and Source Evaluation's quarantine controls were already non-pinned from prior sessions.
- **v0.8.4 — Active-reading timer** in the reader (bottom-bar icon): presets or custom duration,
  optional warnings, finish-current-chapter/one-extra-chapter grace. Pure `ReaderTimerReducer` state
  machine (`eu.kanade.tachiyomi.ui.reader.timer`), monotonic-clock based, counts only while the
  reader is foregrounded and running. Persisted as primitive `SavedStateHandle` fields, same
  convention as `chapter_id`/`page_index`.
- **v0.8.5 — Optional reading schedule** (Settings > Reader): day/time windows that restrict or
  allow reading, reader-only, no other-app enforcement claimed. Pure `ReaderScheduleResolver`
  (`eu.kanade.tachiyomi.ui.reader.schedule`), reuses the timer's own grace mechanism via a second,
  independent `ReaderTimerCoordinator` instance rather than adding schedule branches to the reducer.

The in-app What's New history (`KmkRecsReleaseNotes.MARKDOWN`) presents v0.8.5, v0.8.4, v0.8.3, and
v0.8.2 as four separate, newest-first entries — not collapsed into one v0.8.5 paragraph — so each
milestone's user-facing changes remain individually visible from the About screen.

See `KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md` for the full file
list, deviations, and known limitations (no persisted default timer settings).

## Final Cleanup And Source/Library-Quality Dislike (v0.8.1-fix4)

Per `KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_PLAN.md`, a corrective/polish
pass adding a second source-feedback axis and cleaning up remaining v0.8.1-fix3 defects:

- **Two-axis source feedback model.** The existing `likedRecommendationSourceKeys()` /
  `dislikedRecommendationSourceKeys()` pair answers "do I want this source's For You rows?" A new,
  fully independent pair — `likedSourceQualityKeys()` / `dislikedSourceQualityKeys()` (plus
  `explicitSourceQualityKeys()` as a labeling subset) — answers "is this source itself worth
  showing/suggesting/evaluating?" Both reuse the exact same `RecommendationSourcePreferenceStore` key
  format and mutation helpers; a new pure `SourceQualityMarkPolicy` wraps mark-poor/mark-explicit/
  clear/clear-all as pure functions. No database migration — preference-backed only.
- **Filtering.** Source-quality-disliked sources are excluded from `NonInstalledSourceSuggestionScorer`
  (Sources To Try, even with the global explicit filter off) and `SourceEvaluationCandidateFilter
  .buildPool()` (Source Evaluation candidates, with a separate `sourceQualityHiddenCount`
  diagnostic). Installed sources marked poor/explicit are excluded from For You/grouped
  recommendation source selection in `BrowsePersonalRecommendationsScreenModel`/
  `RecommendsScreenModel`, alongside (not instead of) the existing recommendation-dislike exclusion.
- **Past evaluations preserved, not deleted.** `SourceEvaluationDisplayFilter` hides
  quality-disliked rows by default, recoverable via a "Show disliked sources" toggle mirroring the
  existing "Show installed" pattern exactly.
- **Recovery**: per-row "Clear source mark" plus a bulk "Clear source quality marks" management
  action.
- **Stale-queue completion feedback**: once the stale/outdated Source Evaluation queue is fully
  drained after having run, the screen shows a state-derived "Outdated reassessment complete" card
  instead of just quietly removing the action.
- **Wording/encoding cleanup**: removed all remaining app-facing private/public/internal/test-build
  wording from `KmkRecsReleaseNotes` and `kmk_recs_updated_body`; normalized malformed
  `<!-- KMK --> vX.Y: ... -->` XML comments in `strings.xml`.

See `KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_IMPLEMENTATION.md` for the full
file list and verification results.

## Source Evaluation Continuation Fix (v0.8.1-fix3)

Per `KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.md`, a corrective follow-up
fixing a queue-semantics bug: after a batch reassessed some sources, rows still marked "Outdated —
reassess needed" had nowhere to go — `SourceEvaluationCandidateFilter.shouldSkip()` silently
absorbed them into the "already evaluated, hidden" count whenever `reEvaluateStale == false` (the
default), so `state.candidates`/`canContinue`/`remainingCandidateCount` hit zero even though stale
rows were still visible in the results list.

- **New pure `SourceEvaluationCandidateQueuePolicy.staleCandidates()`** classifies which candidates
  have evaluation rows that are ALL stale (expired or `evaluationVersion < CURRENT_VERSION`),
  independent of the unassessed queue's `skipAlreadyEvaluated`/`reEvaluateStale` option toggles.
- **The stale/outdated reassessment queue is now first-class**: `SourceEvaluationScreenModel.State`
  gained `staleCandidates`, `continuationCursorStale`, `canContinueStale`,
  `remainingStaleCandidateCount` — computed alongside, but independently of, the unassessed queue's
  equivalents in `applyOptionsAndUpdateState()`.
  `startOrContinueStaleReassessment()`/`restartStaleReassessment()` are new, explicit actions,
  separate from `startEvaluation()`/`continueEvaluation()` (which only ever operate on the
  unassessed queue).
- **Cursors never collide.** The stale queue's continuation cursor is persisted under its own
  preference key (`sourceEvaluationContinuationCursorStale`) and uses a fingerprint suffixed
  `|queue=stale`, so switching between the unassessed and stale-reassessment queues — or changing
  batch size, which still does not invalidate either cursor — can never discard the other queue's
  progress. `SourceEvaluationJobState.pendingIsStaleRun` tells the completion-handling coroutine
  which cursor slot to advance.
- **Failed/attempted candidates already advanced the cursor correctly** — `SourceEvaluationRunner`
  adds a candidate to `completedCandidateKeys` as soon as it is handed to the runner, before
  success/failure is known — this was verified, not changed, by this pass.
- **UI**: a new "Reassess outdated (N)" / "Continue reassessing outdated (N remaining)" button
  appears (only when applicable) below the existing start/continue controls, with a compact
  "Restart outdated reassessment" text action shown once a stale-queue cursor exists.

See `KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_IMPLEMENTATION.md` for the full file
list and verification results.

## Version Visibility And Sync Validation (v0.8.1-fix2)

Per `KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_PLAN.md`, a private corrective
follow-up fixing a crash and two smaller gaps found after v0.8.1-fix1:

- **Fixed the v0.8.1-fix1 Loved Manga crash.** `GetCrossSourceGroupPrimary`, `SetCrossSourceGroupPrimary`,
  and `ClearCrossSourceGroupPrimary` (added in v0.8.0) were never registered in `KMKDomainModule`,
  so `LovedMangaScreenModel`, `LinkedVersionListScreenModel`, `TasteBackupCreator`, and
  `TasteRestorer` all threw `InjektionException` the moment they were constructed — most visibly
  when opening Browse > For You > Loved Manga. Fixed by adding the three missing `addFactory { ... }`
  registrations next to the existing cross-source-link registrations.
- **KMK-Recs What's New sequencing made explicit and testable.** New pure `KmkRecsWhatsNewPolicy`
  (`hasUnseenChangelog`, `shouldShowKmkDialog`, `seenVersionCodeOnAcknowledge`) backs
  `MainActivity.kt`'s dialog logic. The normal Komikku changelog dialog and the KMK dialog were
  already structurally unable to show at the same time (`if (showChangelog) {...} else if
  (showKmkChangelog) {...}` in one composable — Compose recomposes this tree the moment
  `showChangelog` flips to `false`, so the KMK dialog reliably renders on the very next frame, never
  stacked); this pass named that behavior in a dedicated policy object instead of leaving it as
  inline booleans, and fixed a minor anti-pattern in `KmkRecsWhatsNewScreen` (the mark-seen write
  now runs in a `LaunchedEffect`, not directly in the composable body). The compact update dialog
  also gained a one-line body (`kmk_recs_updated_body`) so it isn't just a bare title + two buttons.
- **Group-primary sync validation matches restore.** `SyncService.mergeCrossSourceGroupPrimariesPure()`
  now filters invalid rows via `CrossSourceGroupPrimaryRestorePolicy.isValid()` (blank `groupId`,
  `source == 0L`, or blank `url`) — the same rule restore already used; previously sync only
  filtered blank `groupId`.

See `docs/recommendations/KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_IMPLEMENTATION.md`
for the full file list, deviations, and test results.

## Rated Manga And Source Evaluation Polish (v0.8.1-fix1)

Per `KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_PLAN.md`, a private corrective
follow-up fixing five gaps found after the v0.8.0/v0.7.47 passes:

- **Linked-version removal now requires confirmation.** `LinkedVersionListScreen`'s delete action
  previously called `removeFromGroup()` directly; it now shows a confirmation dialog naming the
  version's title where resolvable. Removal still only deletes the `manga_cross_source_link` row —
  it never touches rating, favorite, history, or manga data.
- **Group primary versions are now backed up, restored, and synced.** New `BackupCrossSourceGroupPrimary`
  model at **proto 627**; `TasteBackupCreator`/`BackupCreator` include it when `tasteProfile` is
  enabled; `TasteRestorer.restoreCrossSourceGroupPrimaries()` restores after cross-source links
  (newer-wins per `groupId`, invalid rows skipped, errors collected per group without aborting the
  rest of taste restore); `SyncService.mergeCrossSourceGroupPrimariesPure()` merges local/remote by
  `groupId`, newer `updatedAt` wins. See `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`.
- **The app-bar "Select" action no longer auto-selects the first item.** It enters selection mode
  with an empty selection (`RatedSelectionReducer.enterEmpty()` / `LovedMangaScreenModel
  .enterSelectionMode()`). Long-press still selects the pressed item; tap-toggle inside selection
  mode is unchanged.
- **"Set Primary Version" access clarified.** It remains available only inside
  `LinkedVersionListScreen` (not a direct rated-item-menu action) — the version list now shows an
  explicit hint that the star sets the primary version. Documentation no longer implies a direct
  menu action exists.
- **Source Evaluation rows can show an expandable "Details" section** (`SourceEvaluationEvidenceSummaryPolicy`,
  pure/unit-tested) with the v0.7.47 enrichment/evidence counters: enriched X of Y samples,
  metadata C of S samples, N liked/M disliked/B blocked/A adult-risk, and the manual-review
  explanation for `NEEDS_MANUAL_REVIEW` rows. Hidden entirely for outdated/expired/error/zero-sample
  rows — their counters are stale or nonexistent, never shown as current evidence.

See `docs/recommendations/KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_IMPLEMENTATION.md`
for the full file list, deviations, and test results.

## Rated Manga Bulk Selection And Group Actions (v0.8.0)

Per `KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_PLAN.md`, the Loved / Liked /
Disliked screens (`RatedMangaScreen.kt` / `LovedMangaScreen.kt`, shared via
`RatedMangaCollectionContent`) became a proper rated-manga management surface:

- **Long-press no longer opens recommendations.** It enters bulk selection mode and selects the
  long-pressed item. A "Select" action in the app bar does the same, for discoverability. Tapping a
  card while in selection mode toggles its selection instead of opening it.
- **Selection mode UI**: the app bar shows the selected count and a close action; a bottom action
  bar exposes Change (rating), Clear (rating), Group (merge or select-all-in-group depending on
  context), and More (Mark not interested, Remove from group).
- **Per-item action menu**, grouped into Recommendation Actions (See recommendations — the existing
  single-entry `RecommendsScreen.Args.SingleSourceManga` route; See group recommendations — visible
  only for a confirmed linked group with 2+ versions, reuses the existing
  `RecommendsScreen.Args.CrossSourceGroupSeed` flow unchanged; Find other versions — reuses
  `CrossExtensionMatchScreen.fromMode(..., CrossExtensionMatchMode.Rating(...))`; Favorite other
  versions — reuses the existing `CrossExtensionMatchMode.Favorite` mode, shown only for confirmed
  groups), Rating Actions (Change/Clear rating via `SetMangaTaste`/`ClearMangaTaste`, Mark not
  interested via the existing `SeenRecommendationMangaStore` preference), and Group Actions (Manage
  group — `LinkGroupManagementScreen` gained an optional `focusedGroupId` instead of a new global
  manager; View linked versions — new focused screen; Set primary version; Select all in group;
  Merge selected into group; Remove from group; Ungroup).
- **New focused version-list screen** (`exh/recs/links/LinkedVersionListScreen.kt` +
  `LinkedVersionListScreenModel.kt`) loads directly from persisted group data by `groupId` (not from
  the rated screen's in-memory state). Shows source name/language, title, rating, favorite status,
  installed/missing status, last-updated, and the primary marker per row. Missing/uninstalled
  sources show as "Source not installed" rather than crashing.
- **User-selected primary version**: new additive table `manga_cross_source_group_primary`
  (migration 62) stores one primary `(source, url)` per `group_id`, independent of
  `manga_cross_source_link`'s own lifecycle. The primary controls the cover/title shown for a
  grouped rated-list entry (`RatedGroupPrimaryResolver`); recommendations continue to use the full
  group's metadata (`RecommendsScreen.Args.CrossSourceGroupSeed` is unchanged). A missing/uninstalled
  stored primary fails open to the grouper's own primary-key choice — it does not crash.
- **Group merge is manual-selection-only** (`RatedGroupMergePlanner`, pure/unit-tested): merging two
  existing groups folds every member of the non-target group into the target, never groups by title.
- **Confirmations**: Clear rating, Merge selected into group, Remove from group, Ungroup, and Mark
  not interested all require confirmation before running.
- **Safety**: clearing a rating (`ClearMangaTaste`) never touches `manga_cross_source_link` or
  `manga_cross_source_group_primary` rows; removing from a group or ungrouping never touches
  `manga_taste` rows.
- **Backup/sync gap fixed in v0.8.1-fix1** (see above): `manga_cross_source_group_primary` is now
  included in backup, restore, and sync at proto 627.

See `docs/recommendations/KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_IMPLEMENTATION.md`
for the full v0.8.0 file list and test results.

No new named APK handoff was produced for v0.7.47 (code + docs + tests only, verified via
`assembleDebug`). Named private handoff copies exist for v0.8.0, v0.8.1-fix1, and v0.8.1-fix2:

```text
private/Komikku-v1.13.6-kmk.8.0-debug.apk
private/Komikku-v1.13.6-kmk.8.1-fix1-debug.apk
private/Komikku-v1.13.6-kmk.8.1-fix2-debug.apk
```

All three are the private/personal line (`app.komikku` / `app.komikku.dev`). No public-test
(`app.komikku.kmk`) build was produced for any v0.8.x pass — this entire line is private-only.

## Source Evaluation Tag Enrichment And Scoring Fix (v0.7.47)

Per `KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md` and
`KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md`, Source Evaluation's catalogue-fit
scoring pipeline was corrected:

- **Bounded detail enrichment.** `SourceEvaluationCatalogueEnricher.enrich()` (new, pure/testable)
  calls `source.getMangaDetails()` for up to 12 Popular/Latest list-entry samples per source that
  lack genre metadata, sequentially, each wrapped in a 10s `withTimeoutOrNull`, `runCatching`, and
  cancellation-rethrowing. A failed or timed-out call keeps the original list-entry candidate; it
  never fetches chapter lists or page images, and never writes to the app manga table (evidence-only).
  `SourceEvaluationRunner.probeAndScore()` runs this after Popular/Latest, before scoring, under a
  new `EnrichingDetails` queue phase.
- **Split evidence counters.** `SourceEvaluationScorer` now records
  `detailEnrichmentAttemptCount`/`detailEnrichmentSuccessCount`/`metadataCandidateCount`/
  `positiveCandidateCount`/`negativeCandidateCount`/`explicitPreferredGroupHitCount`/
  `learnedPositiveGroupHitCount`/`blockedCandidateCount`/`adultSignalCandidateCount` (migration 61,
  additive, all default 0). `preferredTagMatchCount`/`blockedTagMatchCount` are kept as backward-
  compatible mirrors of `positiveCandidateCount`/`blockedCandidateCount`.
- **Revised fit score and verdict order.** The fit score is now a ratio-based formula (positive/
  negative/blocked/adult candidate ratios, metadata-confidence penalty) instead of a raw preferred-
  minus-blocked count. Metadata-sparse evidence (`UNKNOWN` confidence, or `LOW` confidence with any
  positive signal) now resolves to `NEEDS_MANUAL_REVIEW` instead of a confident `WEAK`. `STRONG_FIT`
  and `WORTH_TRYING` now additionally require low blocked/adult-risk ratios, so a source cannot reach
  Strong Fit purely because a few broad positive tags appeared alongside heavy BL/GL/adult content.
- **Staleness bumped and enforced.** `SourceEvaluationKeys.CURRENT_VERSION`: `2 -> 3`. Every existing
  row (v1 or v2) is now stale. `SourceEvaluationDisplayPolicy` (new) classifies a row as
  CURRENT/OUTDATED_VERSION/EXPIRED/METADATA_SPARSE/ERROR; stale rows show "Outdated — reassess
  needed" in the row subtitle and are ranked strictly below every current row in the `BEST_FIT` sort
  (an old `STRONG_FIT` can never outrank a current `WORTH_TRYING`).
  `SourceRecommendationFitEligibility.check()` gained a `STALE_EVALUATION` result so a stale
  catalogue row's verdict is never treated as eligible current evidence for the search-compatibility
  probe queue.
- No chapters or page images are fetched at any point in this pipeline; catalogue fit and For You
  search compatibility (`SourceRecommendationFit`) remain fully separate signals, unchanged by this
  pass.

See `docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_IMPLEMENTATION.md`
for the full file list and test results.

As of v0.7.46:
- OCR errors (search, index-clear, per-page failures) are classified into stable, KMR-localized
  messages (`OcrErrorClassifier`) instead of raw exception text; new failed OCR rows store a stable
  key instead of raw text; OCR page-error logcat no longer includes manga title/chapter name.
- OCR search results now have a per-row overflow menu to clear OCR text for just that chapter or manga,
  plus a compact "clear empty/failed rows" button -- both existing repository methods, newly reachable.
- A second raw-exception-to-UI path in Source Evaluation (`recordExtensionError`) and three in Best
  Version comparison were classified the same way (`RecommendationErrorClassifier`).

As of v0.7.45:
- Rated Manga (Loved/Liked/Disliked) grouped display defaults on (`LovedMangaScreenModel.load()`); the
  user can still toggle to flat, and the toggle now survives reactive reloads (previously the hardcoded
  default was re-applied every time `load()` re-ran, silently resetting a manual "show flat" choice).
- `SourceFitStats.topPicksContributionCount` is now shown, appended to the existing source fit badge in
  Recommendation Settings (`"Great fit · 5"`), only when greater than zero.
- OCR (`libs.mlkit.text.recognition`) is confirmed to ship in every build, including `kmkPublicTest` --
  `docs/ocr/README.md` previously described it as a separate build line; that was never actually true
  and has been corrected.
- Non-installed (temporarily-installed) recommendation-quality probes now require the Private installer
  or are refused with a clear message, closing a public-safety gap where `PromptRequired` cleanup was
  log-only.

## Recommendation Systems

The app currently has two separate recommendation systems.

### Manga Detail Recommendations

Location: manga detail page Recommendations tab.

This uses recommendation providers such as tracker/provider recommendations and cross-extension genre search. The cross-extension logic is implemented through `CrossExtensionGenreSearchSource` and related manga-detail recommendation paging code.

This system is separate from Browse > For You.

### Browse > For You

Location: Browse tab For You page.

This is the KMK personal recommendation feed. It builds a taste profile from ratings and tag preferences, searches selected catalogue sources, shows source-specific rows, and builds a synthetic Top Picks row from the already-fetched source results.

The Top Picks row is not a separate source and does not run a second crawl.

## For You Source Selection

Current source pipeline:

```text
visible catalogue sources
-> recommendation language filter
-> manual source priority order
-> disabled source exclusion
-> fixed top-3 boosted source selection
-> adaptive batch fill
-> per-source recommendation searches
-> source rows + Top Picks from already-fetched results
```

Important current behavior:

- The recommendation language filter defaults to English.
- Local Source is explicitly excluded by `RecommendationSourceFilter` using source id `0`.
- Disabled sources are intentionally excluded from For You searches.
- Top-three priority sources receive larger caps and are fixed. Boosted status does not transfer if a top source has no matches.
- Adaptive fill searches in batches of 5, can attempt up to 40 sources, and stops when 20 useful source rows are found or the attempt list is exhausted.
- Sources that return no visible results are hidden from the For You UI but still record a last-run status.
- v0.7.25: On screen open or manual refresh, if the device has no internet connection, For You immediately shows a "No internet connection" message with a Retry button instead of attempting source queries. Uses `context.isOnline()`.
- v0.7.26: Minimum chapter count filter — configurable in Recommendation Settings (Off / 5 / 10 / 20 / 50). When set above 0, For You filters out manga whose locally-known chapter count is below the threshold. Manga with no locally-stored chapters (untracked) are never filtered. The filter applies post-fetch. Cache fingerprint includes `minChapterCount` so changing the setting invalidates the cache.

## Top Picks

Top Picks history:

- v0.4.0 added the original compiled row.
- v0.4.1 renamed it from "Combined Picks" to "Top Picks."
- v0.4.2 refined duplicate merging using exact normalized `title + author` OR exact normalized `title + artist`.
- v0.4.3 made the Top Picks header tappable.

Current Top Picks behavior:

- Inline For You row shows up to 20 ranked picks.
- Tapping the header opens `TopPicksScreen`.
- `TopPicksScreen` shows up to 50 ranked candidates from already-fetched For You results.
- No second recommendation crawl is launched for the drill-down screen.
- If For You is still loading, Top Picks detail may show the currently available partial candidate set.

## Ratings And Tags

Ratings:

- `Love`, `Like`, and `Dislike` feed the taste profile.
- Rated manga visibility is configurable.
- Current default behavior is documented as hiding disliked entries only.

Tags:

- Preferred tags increase recommendation scoring and search tag selection.
- Disliked tags reduce scoring.
- Blocked tags hard-filter candidates after fetching.
- Query-time blocked-tag exclusion is implemented as of v0.7.0 (code) / v0.7.20 (tests + documentation). `GenreFilterMapper.buildSearch()` sets `STATE_EXCLUDE` on matching TriState filters before the search query is issued.

## Known Manga Filtering

Known-manga filtering is local DB only.

When "Hide known manga" is enabled, For You hides candidates that are:

- rated,
- in library/favorite,
- started,
- read,
- or present in local history.

As of v0.4.2:

- `hideKnownManga` is included in the profile fingerprint, so changing the setting invalidates incompatible cache entries.
- `loadFromCache()` re-applies the known-manga filter, matching live-search behavior.
- The filter fails open if the DB lookup fails.

## Source Status Diagnostics

As of v0.4.4:

- After each For You run, per-source statuses are persisted to `recommendation_last_source_run_statuses`.
- Recommendation Settings shows the last-run status for each source.
- The status list is headed by a note: "Statuses below are from the last For You refresh."
- Statuses update live when For You finishes while the settings screen is open.
- Current statuses include `Shown`, `NoMatches`, `FilteredOut`, `Error`, `OutsideAttemptLimit`, `HiddenByDuplicateHandling`, and a UI-derived disabled/not-checked state.
- `HiddenByDuplicateHandling` is recorded for sources whose results are fully hidden by cross-source display dedupe after the For You batch loop completes. The adjustment is computed by `adjustStatusesForDedupe()` in `RecommendationStatusAdjuster.kt`.

## Local Source Status

- Local Source is not searched by For You.
- Local Source is excluded intentionally by the language/source filter (`id == 0L`).
- This exclusion remains intentional because Local Source is for local manga files, not a catalogue recommendation engine.

## Cache

The recommendation cache is versioned and fingerprinted. It invalidates when relevant profile inputs change, including selected recommendation languages and the hide-known setting.

The cache stores recommendation rows from source searches. It does not create a full local manga catalogue.

## Candidate Discovery Memory (v0.7.38)

A new local-only table `recommendation_candidate_memory` (SQLDelight migration 56) stores discovered For You candidates between refreshes.

Behavior:
- After each For You page-1 fetch, scored candidates are upserted to memory per `(source_id, url)`.
- On subsequent refreshes, memory is loaded and merged with new results via `RecommendationCandidateMemoryRanker.merge()` which re-scores all candidates against the current profile.
- Additional page discovery: v0.7.39 replaced the v0.7.38 page-probing logic (see Rolling Discovery Progress below).
- Memory is NOT included in backup/sync. It is local-only derived cache.
- Max 500 entries per source; oldest/lowest-seen entries pruned automatically.
- User can clear all memory via Settings → Management → Reset For You discovery history.

## For You Rolling Discovery Progress (v0.7.39)

A second local-only table `recommendation_discovery_progress` (SQLDelight migration 57) tracks ALL evaluated pages per source/query regardless of outcome.

Problem solved: in v0.7.38, the planner only treated "page has candidates in memory" as "page was evaluated." Pages returning empty/filtered/error results were invisible to the planner and got retried on every refresh.

Behavior:
- After page-1 fetch, a progress entry is recorded (status = success/filtered/empty based on result counts).
- After each additional page probe, a progress entry is recorded even if the page returned nothing, was filtered entirely, or errored.
- The planner reads `evaluatedPages` (all recorded pages) from this table. `nextPageToProbe(evaluatedPages)` returns `maxEvaluated + 1`, or null if empty or cap reached.
- Cumulative page cap: 20 pages per source/query across all refreshes.
- Per-refresh new page limit: 1 (unchanged from v0.7.38).
- Resetting discovery history (Settings → Management) now also clears the progress table so discovery restarts from page 1.
- Progress table is NOT in backup/sync. Local-only derived cache.

Constants:
- `MAX_NEW_PAGES_PER_SOURCE_REFRESH = 1`
- `MAX_NEW_CANDIDATES_PER_DISCOVERY_PAGE = 20`
- `MAX_DISCOVERY_PAGE_PER_SOURCE_QUERY = 20`

Status values: `success`, `empty`, `filtered`, `duplicate`, `error`, `unsupported`, `exhausted`.

## Discovery Retry Policy (v0.7.40)

Additional-page probes that fail with transient network errors are now retried with bounded exponential backoff instead of being permanently skipped.

Migration 58 added three nullable/defaulted columns to `recommendation_discovery_progress`:
- `attempt_count INTEGER NOT NULL DEFAULT 0` — how many error attempts have been made for this page
- `next_retry_at INTEGER` — epoch ms after which the next retry is allowed; null means retry immediately
- `failure_kind TEXT` — `"retryable"` or `"permanent"`; null for non-error records

`RecommendationRetryClassifier` (pure object):
- `classify(e)` — `UnknownHostException`, `SocketTimeoutException`, `IOException` → retryable; `UnsupportedOperationException`, HTTP 4xx → permanent; unknown → retryable (fail open)
- `isRetryable(failureKind, attemptCount)` — returns true when retryable and `attemptCount < MAX_ATTEMPTS` (3)
- `nextRetryAt(attemptCount, nowMs)` — exponential: 5 min → 10 min → 20 min, capped at 24 hours

`RecommendationDiscoveryPlanner.nextPageToProbe(progressRecords, nowMs)` replaces the old `nextPageToProbe(evaluatedPages)`:
- Finds frontier page (max recorded page).
- Frontier retryable + due → return frontier (retry).
- Frontier retryable + NOT due → return null (skip until backoff expires).
- Frontier retryable + max attempts exhausted → advance frontier+1.
- Frontier permanent error, success, empty, filtered → advance frontier+1.
- Empty records or frontier at cap → return null.

Additional-page search is bounded to `ADDITIONAL_PAGE_TIMEOUT_MS = 20_000L`. A timeout is recorded as a retryable error.

## Shared Source Selection and Visibility Policy (v0.7.40)

`RecommendationSourceSelector` (pure object) — shared source selection used by both For You and group-seeded recommendations:
- `select(sources, languages, storedOrder, effectiveDisabledIds, maxSources)` wraps `RecommendationSourceFilter` + `RecommendationSourceOrdering` in one call.
- For You: `maxSources = 0` (unlimited, existing adaptive-fill applies separately).
- Group flow: `maxSources = MAX_SOURCES` (5).
- Group flow now uses real language preference (`recommendationLanguages`), real priority order (`storedOrder`), and excludes disliked/disabled sources (`effectiveDisabledIds`).

`RecommendationCandidateVisibilityPolicy` (pure object) — shared visibility filter used by For You and group-seeded recommendations:
- `evaluate(manga, tasteByKey, visibility, seenKeys, knownIds, minChapterCount, chapterCounts, seedMemberKeys): CandidateVisibility`
- Priority order: HIDDEN_SEED_MEMBER → HIDDEN_FAVORITE → HIDDEN_RATED → HIDDEN_SEEN → HIDDEN_KNOWN → HIDDEN_MIN_CHAPTERS → VISIBLE
- `HIDDEN_SEED_MEMBER` fires for group flow only (seed manga never appear as recommendations).
- `HIDDEN_RATED` respects `RatedMangaVisibility` (hide disliked only / hide all rated / show all rated).
- `HIDDEN_KNOWN` fires only when `knownIds` is non-empty (hide-known-manga setting is on).
- `HIDDEN_MIN_CHAPTERS` fires only when `minChapterCount > 0` and a count entry exists; missing counts fail open (VISIBLE).
- Group flow uses batched DB lookups (one `getChapterCounts.await(allIds)`, one `getKnownMangaIds.await(allIds)` after the candidate loop) — no N+1 per candidate.

## Same-Refresh Discovery Merge Fix (v0.7.40)

Fixed two merge short-circuits that caused extra-page discovery candidates to be silently dropped:

1. **Cached path**: `if (resolvedMemory.isEmpty()) cached` was returning the raw cache without merging extra-page candidates. Removed — always calls `RecommendationCandidateMemoryRanker.merge()`.
2. **Live path**: `if (resolvedMemory.isEmpty()) recommendations` was returning page-1 results without merging additional-page results. Removed — always calls merge.

Both paths now use one final merge/rank for all candidates (page-1, extra-page, remembered memory). Score ≤ 0 is consistently excluded.

## Discovery Policy Corrections (v0.7.41)

v0.7.40 introduced the shared source selector and visibility policy foundation, but verification found gaps. v0.7.41 corrects them; the v0.7.40 sections above describe the foundation, and the behavior below is now the authoritative contract.

**Single visibility contract in every path.** `RecommendationCandidateVisibilityPolicy` is now the sole visibility decision-maker for live page-one, extra-page, cached, memory-merge, and group-seeded results:
- `RecommendationCandidateMemoryRanker.merge()` gained `minChapterCount` + `chapterCounts` and calls the policy instead of `shouldHideForYou` + inline favorite/seen/known checks. A candidate hidden by the live min-chapter filter can no longer reappear through discovery memory.
- `loadFromCache()` resolves all cached manga first, batches one known-id lookup and (only when the minimum is active) one chapter-count lookup, and applies the policy to every resolved cached manga. It gained a `minChapterCount` parameter.
- Both merge call sites in `searchSource()` batch chapter counts across cache/new + memory and pass them to `merge()`.
- `discoverAdditionalPage()` (extra-page discovery) now also batch-loads known IDs for its localized candidates when hide-known-manga is enabled, and passes them into the shared policy — the same context used by page-one/cache/memory/group (v0.7.41 known-context follow-up, 2026-07-11). A known-only additional page is filtered to empty before scoring, so it is recorded as empty/filtered in discovery progress and never written to candidate memory, instead of relying only on the final merge to hide it. Page-one and extra-page filtering both go through one new pure top-level function, `filterVisibleCandidates(...)`.
- Fail-open preserved: failed known-id/chapter-count lookups log a warning and use empty data; unknown chapter counts stay visible.

**Group recommendations budget only counts visible results.** `GroupSeededRecommendationsScreenModel.buildRecommendations()` is a chunked collect-then-filter loop: each source/plan chunk is localized + scored (no DB work in the inner loop), then one batched chapter-count and known-id lookup runs for that chunk, then the shared policy is applied. Only VISIBLE candidates enter a new pure `GroupRecommendationLoopPolicy.VisibleResultAccumulator` (dedup by localized id, highest score wins). Hidden candidates (seed member, favorite, rated, seen, known, min-chapter) never consume the target budget. The scan continues until 20 visible candidates are collected or the bounds are exhausted. Hard limits unchanged: 5 sources, 8 raw per source/plan, 12 s search timeout, 5 s localize timeout, 45 s total timeout, cap of 20.

**Truthful, bounded retry state.** In `discoverAdditionalPage()`, a retryable failure that uses its final allowed attempt (attempt count reaches `MAX_ATTEMPTS = 3`) persists `status = STATUS_EXHAUSTED`, `attemptCount = 3`, the retained diagnostic, `failureKind = retryable`, and `nextRetryAt = null`. Earlier retryable failures schedule exponential backoff; permanent failures never schedule a retry. `RecommendationDiscoveryPlanner.nextPageToProbe()` selects the lowest still-pending retryable-error page **before** the page cap, so a due retry of page 20 runs while a new page 21 is never created; a not-yet-due retryable error blocks advancement; exhausted/permanent/unsupported/success/empty/filtered/duplicate records are advanceable. Cancellation is rethrown before any progress write, so it is never recorded as a failed retry.

**Conservative error classification.** `RecommendationRetryClassifier.classify()` is retryable only for `IOException`, `UnknownHostException`, and `SocketTimeoutException`. `UnsupportedOperationException`, HTTP 4xx (checked before `IOException`), and all unknown/runtime exceptions are permanent, so broken extension code is not re-invoked indefinitely. The explicit local probe timeout is classified retryable at the call site.

No database migration was added — `STATUS_EXHAUSTED` already existed on `RecommendationDiscoveryProgress`.

## Source Evidence Redesign (v0.7.42)

Per the approved plan (`KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_PLAN.md` §6, decisions D1–D4),
Source Evaluation no longer pools Popular/Latest catalogue samples with tag-search results into
one score and one verdict:

- **Catalogue fit only.** `SourceEvaluationScorer` now scores exclusively from Popular/Latest
  samples. The scorer's own tag-search probe (previously up to 3 calls per source inside
  `SourceEvaluationRunner.probeAndScore()`) was removed entirely — search compatibility is now
  measured solely by the separate, already-existing `SourceRecommendationFitProbe` /
  `SourceRecommendationFit` record.
- **Shared taste-matching contract.** Catalogue-fit scoring now scores each sampled item with the
  real `PersonalRecommendationScorer` (the same function For You uses), instead of the previous
  ad-hoc set-membership tag matching. Blocked-tag handling is a real hard exclusion per item, not a
  soft score penalty. `SourceEvaluationScorer.score()` gained an `aliasMap` parameter, loaded once
  per batch in `SourceEvaluationRunner.start()`.
- **Metadata confidence.** A new `catalogueMetadataConfidence` field (`HIGH`/`MODERATE`/`LOW`/`UNKNOWN`)
  on `SourceEvaluation` records how much of the sampled catalogue evidence actually had genre tags,
  independent of the fit score — Popular/Latest pages frequently omit genre (the same issue v0.7.13
  fixed on the search side), so missing metadata must not silently read as "bad fit."
- **Fail-open eligibility.** `SourceRecommendationFitEligibility.check()` now admits a source for
  the search-compatibility probe when the catalogue verdict is STRONG_FIT/WORTH_TRYING **or** when
  catalogue confidence is LOW/UNKNOWN (inconclusive evidence). EXPLICIT_HEAVY/ECCHI_HEAVY/ERROR/REJECTED
  remain excluded unconditionally regardless of confidence.
- **Staleness parity.** `SourceEvaluationKeys.CURRENT_VERSION` bumped `1 → 2` (catalogue-fit
  semantics changed, so every existing STRONG_FIT/WORTH_TRYING verdict is now correctly treated as
  stale by the pre-existing `SourceEvaluationCandidateFilter.isStale()`). `SourceRecommendationFit`
  gained its own independent `evaluationVersion`/`expiresAt` columns and `CURRENT_VERSION = 1`
  constant (new migration 60).
- **Honest labeling.** UI strings that read "Recommendations: Good/Great" (implying the source's own
  recommendation feature was measured) were relabeled to "For You search: Good/Great" and similar —
  the section title, sort label, and running-status text. Resource IDs were kept stable; only the
  English text changed.
- **Two new migrations**: 59 (`source_evaluation.catalogue_metadata_confidence`), 60
  (`source_recommendation_fit.evaluation_version`, `.expires_at`).

See `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md` for the full file list.

### v0.7.42-fix1 — manual queue/diagnostics and row subtitle corrected

`SourceRecommendationFit.evaluationVersion`/`expiresAt` and `SourceRecommendationFitEligibility`
existed since v0.7.42 but were only wired into the automatic evaluation path. v0.7.42-fix1 wired them
into every remaining consumer:

- `SourceRecommendationFitEligibility` gained `isProbeEligible(evaluation)` and
  `isFitCurrent(fit, now)` — the single shared contract for "should this source be probed" and "is
  this fit result still valid."
- `SourceRecommendationQualityQueue.compute()` (manual "Check search compatibility"/"Re-check all"
  actions) now uses `isProbeEligible`/`isFitCurrent` instead of a hardcoded
  `{STRONG_FIT, WORTH_TRYING}` set and mere fit-presence check. A WEAK/NEUTRAL catalogue verdict with
  LOW/UNKNOWN metadata confidence is now correctly queued; a missing, older-version, or expired fit is
  now correctly treated as "missing," not "checked."
- `SourceRecommendationQualityDiagnostics.compute()` uses the same two functions, so its counts can
  never disagree with the queue. A stale fit contributes to `notCheckedCount`, not to the
  Good/Weak/Error/No-results buckets.
- The Source Evaluation row subtitle no longer shows `search N%` (a fabricated figure —
  `searchReliabilityScore` is always `0.0` as of v0.7.42). It shows catalogue metadata confidence
  instead, via new KMR strings (`source_evaluation_catalogue_row_subtitle` and four
  `source_evaluation_metadata_confidence_*` labels). The separate `For You search: ...` line is
  unchanged.

See `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md` for the full file list,
confirmed findings, and verification.

### v0.7.42-fix2 — one shared display policy for state, sorting, and targeted rechecks

fix1 unified automatic evaluation, the manual queue, diagnostics, and fit staleness — but the
remaining presentation/action/sort paths (row labels, the two catalogue sort comparators, and the
action buttons) still used ad-hoc logic. fix2 makes every one of them resolve through one shared,
pure, Android-free policy:

- **`SourceRecommendationFitDisplayPolicy`** (new) resolves `(SourceEvaluation, SourceRecommendationFit?,
  now)` to exactly one `CompatibilityDisplayState`: `INELIGIBLE`, `NOT_CHECKED`, `OUTDATED`, `GREAT`,
  `GOOD`, `MIXED`, `WEAK`, `NO_MATCHES`, or `ERROR`. It reuses `SourceRecommendationFitEligibility
  .isProbeEligible`/`.isFitCurrent` — no new score, no duplicated logic. `compatibilityRank(state)`
  encodes the canonical seven-bucket sort order: current GREAT/GOOD/MIXED → WEAK → NO_MATCHES → ERROR
  → OUTDATED → NOT_CHECKED → INELIGIBLE.
- **Queue** (`SourceRecommendationQualityQueue.QueueResult`) gained a fourth bucket:
  `outdatedPromising` (eligible + stale fit), distinct from `missingPromising` (eligible + no fit).
  The four buckets (missing, outdated, checked, ineligible) are mutually exclusive and collectively
  exhaustive.
- **Actions**: the normal check still targets missing only; a new
  `recheckOutdatedRecommendationQuality()` targets outdated only; "Re-check all" now correctly targets
  missing + outdated + checked (previously it could silently miss outdated rows). All three share one
  concurrency/empty-target guard.
- **Row labels**: `EvaluationResultRow` now shows "For You search: Not checked" (eligible, no fit),
  "For You search: Outdated - recheck" (eligible, stale fit — new), the existing outcome label
  (eligible, current fit), or no compatibility label at all (ineligible) — resolved via the shared
  policy instead of a separate hardcoded STRONG_FIT/WORTH_TRYING check. A stale fit's error-kind badge
  and reason-detail text no longer render as if they described a current result.
- **Sorting**: `SortMode.SEARCH_RELIABILITY` (which sorted the retired, always-`0.0`
  `searchReliabilityScore` field) was replaced with `SortMode.FOR_YOU_COMPATIBILITY`, which orders by
  the shared policy's rank. `BEST_FIT` remains catalogue-first (verdict rank, then
  `recommendationFitScore`, then `qualityScore`); its old `searchReliabilityScore` tie-break was
  replaced with a true compatibility tie-break that only distinguishes two rows when **both** have a
  current outcome — an outdated/not-checked/ineligible row is a neutral tie at this step, never a loss.
  `sort()` gained `fitsByEvalKey` and `now` parameters.
- One `now` is captured once per composition in `SourceEvaluationScreen` and threaded into the queue,
  sort, and every row, so they cannot disagree within one render pass.

See `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_IMPLEMENTATION.md` for the full file
list, confirmed findings, and verification.

## KMK-Recs What's New

As of v0.5.1:

- The manga detail rating dropdown includes three new actions: "Love other versions", "Like other versions", and "Dislike other versions".
- Tapping one of these opens `CrossExtensionMatchScreen`, a separate bounded search workflow.
- The matching workflow searches using the current manga title, filtered by recommendation language preference and sorted by source priority order. Local Source is excluded.
- Results are capped at 2 per source. Normal global search is uncapped.
- The origin manga is filtered out before the per-source cap is applied. If a source returns `[origin, A, B, C]`, the screen shows `[A, B]`, not `[A]`.
- The origin manga never appears as a selectable candidate and never counts toward "Selected N of M".
- `toggleSelection()` defensively ignores the origin key even from stale UI events.
- All non-origin candidates are selected by default. The user can deselect wrong candidates before confirming.
- Manual deselection is preserved if results update while loading.
- On confirmation, `SetMangaTasteBatch` writes a taste row for each selected candidate using `(source, url)` identity.
- Does not write any global search preferences.
- Cross-source link groups are fully implemented: `manga_cross_source_link` SQLDelight table (migration 50), domain model, interactors, backup at proto 624, restore, and sync merge. After confirming any cross-extension match (rating/seen/favorite), a link group is written that Loved Manga can use for grouping.
- As of v0.7.1: `CrossExtensionMatchScreen` stores only primitive route args (`modeKey: String`, `ratingValue: Int?`). Mode is reconstructed in `Content()` via `CrossExtensionMatchRouteMode`. This prevents `BadParcelableException` during Android state save.

## Non-Installed Extension Discovery

As of v0.6.1 (hardened from v0.6.0):

- Recommendation Settings includes a "Sources To Try" section at the bottom.
- A source appears only if it has meaningful positive evidence: conservative similarity to an already-installed source name (`SimilarToInstalledSource`).
- Language match, same repo, base URL, and generic content keywords are eligibility filters only -- they no longer score and do not qualify a source on their own.
- `hasMeaningfulEvidence()` gates the scorer: if no `SimilarToInstalledSource` reason is found, the source is excluded.
- Similarity rules: exact normalized name match (score 0.60), containment where both names >= 8 chars (0.50), or distinctive token overlap -- token length >= 5, not a generic word (0.50).
- Generic single-word names (`manga`, `scans`, `manhwa`, etc.) are excluded from both sides of comparison.
- If no qualified suggestions exist, the section shows an honest empty state.
- Suggestions use `SuggestionConfidence.LOW` (score < 0.55) or `MEDIUM` (score >= 0.55). HIGH confidence is reserved for installed-source fit learning.
- Scores are capped at 0.69 to keep non-installed suggestions below the 0.70+ range reserved for post-install evaluation.
- Each suggestion stores the original `Extension.Available` (not a `GetExtensionsByType` synthetic copy with modified `pkgName`). Installing uses `ExtensionManager.installExtension(suggestion.extension)` with the original identity.
- Dismissed suggestion keys are persisted in `SourcePreferences.dismissedNonInstalledRecommendationSources()` as semicolon-separated `"signatureHash|pkgName|sourceId"` strings.
- After install, the extension appears in `installedExtensionsFlow`, and the suggestion is automatically removed from the list.
- Language preference changes now trigger immediate suggestion refresh (5th combine source in `GetNonInstalledSourceSuggestions`).
- The list shows top 5 by default; a "Show N more" toggle expands.
- Installed-source fit learning is deferred -- sources are labeled "Install to test with For You."

## Source Like/Dislike Preferences

As of v0.6.2:

- Recommendation Settings shows thumbs-up / thumbs-down icon buttons on each installed source row and on each Sources To Try suggestion row.
- **Like (installed source)**: marked visually with primary color. No effect on current For You source order.
- **Dislike (installed source)**: status shows "Disliked · excluded from For You" in error color. Source excluded from For You source candidate list. No uninstall.
- **Like (non-installed suggestion)**: suggestion visible even without metadata similarity, scores 0.68 (above normal matches), shows "You liked this source" reason.
- **Dislike (non-installed suggestion)**: suggestion hidden from Sources To Try.
- **Dismiss**: remains separate lightweight "not now" action, not a dislike.
- Key format: installed `i|sourceId`; available `a|signatureHash|pkgName[|sourceId]`.
- Like and dislike are mutually exclusive; tapping the active icon again resets to neutral.
- Preferences stored in `SourcePreferences.likedRecommendationSourceKeys()` and `SourcePreferences.dislikedRecommendationSourceKeys()`.
- Changes trigger immediate recomputation of Sources To Try and invalidate the For You cache.

## Extension Selective Uninstall

As of v0.6.6:

- "Select extensions" overflow action in Browse > Extensions enters selection mode.
- In selection mode: installed/untrusted rows show a `Checkbox`; available rows show normally with no checkbox.
- Active install/update rows (installStep not completed) are not selectable.
- "Uninstall selected (N)" button disabled when N = 0 or bulk uninstall is running.
- "Cancel" exits selection mode and clears selection.
- One confirmation dialog before uninstall begins; user informed Android may ask per extension.
- Uninstalls fire sequentially with 300ms delay to avoid rapid-fire Android intent stacking.
- Back press exits selection mode before clearing search query.
- State: `isExtensionSelectionMode`, `selectedExtensionKeys`, `isBulkUninstallingExtensions`.
- Normal install/update/open/trust/update-all behavior unchanged.

## Sources To Try Selective Install

As of v0.6.5:

- A "Select" TextButton next to "Install visible suggestions" enters selection mode.
- In selection mode, each suggestion row shows a leading `Checkbox` and the card is tappable to toggle.
- "Install selected (N)" is enabled only when N > 0 and no bulk install is running.
- "Cancel" exits selection mode without installing (selection cleared).
- `installSelectedSuggestions()` filters only visible, selected, not-already-installing suggestions before delegating to the safe `installSuggestions()` path.
- State: `isSuggestionSelectionMode: Boolean`, `selectedSuggestionKeys: ImmutableSet<String>`.
- Install/Dismiss buttons hidden in selection mode; Like/Dislike remain functional.

## Explicit Porn/Hentai Source Filter

As of v0.6.7:

- New toggle under Settings > Browse > NSFW content: "Block explicit porn/hentai sources".
- When enabled, `ExplicitSourceClassifier` hides clearly explicit sources from Browse > Sources, Browse > Extensions (available only), and Sources To Try.
- Classifier rules: explicit keywords in name (`hentai`, `porn`, `pururin`, `tsumino`, `8muses`, `hbrowse`, `luscious`, `doujins`, `multporn`, `xxx`, `erotic`, `smut`, `adult comic/manga/manhwa/manhua`) OR `hentai`/`porn` in package name OR known explicit source IDs (NHentai, Pururin, Tsumino, 8Muses, HBrowse, all E-Hentai and ExHentai source IDs).
- Does NOT block: `ecchi`, `nsfw`, `mature`, `lewd`, `adult` alone.
- Installed/untrusted explicit extensions remain fully manageable (update, uninstall unchanged).
- Default `false` -- existing users see no change on update.
- Classifier is a pure object with no Android deps; fully unit-tested.

## KMK-Recs What's New

As of v0.7.42-fix2:

- `KmkRecsReleaseNotes.VERSION_CODE` is `744`.
- `KmkRecsReleaseNotes.VERSION_NAME` is `KMK-Recs v0.7.42-fix2`.

As of v0.7.42-fix1:

- `KmkRecsReleaseNotes.VERSION_CODE` is `743`.
- `KmkRecsReleaseNotes.VERSION_NAME` is `KMK-Recs v0.7.42-fix1`.

As of v0.7.42:

- `KmkRecsReleaseNotes.VERSION_CODE` is `742`.
- `KmkRecsReleaseNotes.VERSION_NAME` is `KMK-Recs v0.7.42`.

As of v0.7.41:

- `KmkRecsReleaseNotes.VERSION_CODE` is `741`.
- `KmkRecsReleaseNotes.VERSION_NAME` is `KMK-Recs v0.7.41`.

As of v0.7.40:

- `KmkRecsReleaseNotes.VERSION_CODE` is `740`.
- `KmkRecsReleaseNotes.VERSION_NAME` is `KMK-Recs v0.7.40`.

As of v0.7.39:

- `KmkRecsReleaseNotes.VERSION_CODE` is `739`.
- `KmkRecsReleaseNotes.VERSION_NAME` is `KMK-Recs v0.7.39`.

As of v0.7.37:

- `KmkRecsReleaseNotes.VERSION_CODE` is `737`.
- `KmkRecsReleaseNotes.VERSION_NAME` is `KMK-Recs v0.7.37`.
- Local KMK-Recs What's New contains user-facing recommendation changes only.
- The local KMK-Recs What's New screen no longer shows the no-op browser button.
- Upstream Komikku What's New behavior remains separate.
- Note: `KmkRecsReleaseNotes.VERSION_CODE` is independent of the Android `versionCode` in `app/build.gradle.kts` (currently 88). KMK feature versions and Android package versions are tracked separately.

## Source Evaluation

As of v0.6.13:

- Source Evaluation is accessible from Recommendation Settings > Source Evaluation.
- Evaluates non-installed extensions one at a time: installs temporarily via Private installer (no global preference change), probes popular/latest/search pages, scores against taste profile, stores verdict, then uninstalls.
- Candidate pool: `GetSourceEvaluationCandidates` draws from the full available extension pool filtered by language/nsfw/disliked/installed. Not the Sources To Try selective list. `GetNonInstalledSourceSuggestions` is unchanged and still used by Sources To Try only.
- `SourceEvaluationCandidateFilter` -- pure object (`buildPool` + `applyOptions`). Handles base pool filtering and option-based filtering separately. `startEvaluation()` uses pre-filtered `state.candidates` directly.
- Skip-already-evaluated filter uses `extensionKey` (`signatureHash|pkgName`) for correct extension-level matching.
- Candidate diagnostics shown on screen: eligible count, evaluated-hidden count, explicit-hidden count.
- Verdicts: `STRONG_FIT` (score 0.90 in Sources To Try), `WORTH_TRYING` (0.75), `EXPLICIT_HEAVY` (0.10, hidden when blockExplicit), `ECCHI_HEAVY` (0.30), `REJECTED` (hidden), `ERROR`, and others.
- Post-install evaluated scores exceed 0.70 (range reserved for evidence-backed sources; metadata-only scorer caps at 0.69).
- Batch sizes: 10, 25, 50, 100. 500/1000 deferred.
- Explicit-heavy and ecchi-heavy verdicts always remain distinct.
- SQLite migration 47.sqm creates the `source_evaluation` table on update from pre-v0.6.8 installs.
- Defensive `.catch` fallbacks on `subscribeAll()` prevent crashes if the table is absent: Sources To Try falls back to metadata-only suggestions; Source Evaluation screen shows empty past-evaluations list.
- Shizuku: setup card with Install/Open/Use/Stop/Uninstall/Refresh buttons; auto-refresh on screen resume; status distinguishes "ready+selected", "selected-but-not-ready", and "ready-not-selected".
- **Private-aware cleanup (v0.6.13):** `SourceEvaluationCleanupPolicy` classifies cleanup action. Private extensions (`isShared=false`) removed silently; system-installed (`isShared=true`) only prompt if `promptHeavyCleanupAllowed=true`. Pre-existing extensions detected before install and skipped.
- **Installer verification (v0.6.13):** `SourceEvaluationInstallerPolicy` now sets `requiresPromptWarning=true` for SHIZUKU (was wrongly `false`). Private mode shows description via typed enum key.
- **Installer policy messages typed (v0.7.17):** `PolicyResult.message: String?` replaced with `messageKey: InstallerPolicyMessage?` (enum with 8 cases). Screen maps each enum case to a KMR string via `toLocalString()` composable extension. This removes all hardcoded English from the installer UI and enables future localization.
- **Prompt-heavy warning dialog (v0.6.13):** Selecting SHIZUKU/CURRENT and starting with batch > 1 shows a confirmation dialog: "Use Private", "Continue anyway", or "Cancel".
- **UI labels (v0.6.13):** Private chip labeled "Private (recommended)". Shizuku card shows "Private is recommended for silent cleanup" when Shizuku selected and Private available; offers "Use Private" action.
- **Diagnostics logging (v0.6.13):** logcat prefix `"KMK SourceEvaluation install:"` on each extension: mode, override, wasPreExisting, post-install isShared, cleanup decision.
- **Timeout resilience (v0.6.14):** All `withTimeout()` calls replaced with `withTimeoutOrNull()` in `SourceEvaluationRunner`. Install timeout (90s), load timeout (20s), popular/latest probe timeout (30s), search probe timeout (25s) are now local per-extension/per-source failures. A slow or hanging source marks that extension/source as an error and evaluation continues. Status is only set to `Cancelled` when the user explicitly presses Cancel. Timeout diagnostics logged with prefix `"KMK SourceEvaluation timeout:"`. `completedCount` now advances for all handled extensions (including failures), so the progress bar reaches 100% when the batch completes.
- **Priority reset safety (v0.6.14):** `Reset priority` action removed from top app bar. "Restore default source order" `TextButton` added near Source Priority section (disabled while dragging). Tapping it opens an `AlertDialog` confirmation before the order is changed.
- **Results stability (v0.6.15):** `SourceEvaluationResultList` pure helper sanitizes (drops blank/duplicate `evaluationKey` rows) and sorts results in Kotlin. SQL `getAll`/`getAllAsFlow` now have `ORDER BY evaluated_at DESC` for deterministic base order. `ScreenModel.onEach` sanitizes before storing. Screen uses `itemsIndexed` with `stableUiKey()` -- no `animateItem()`. Sort dropdown (6 modes: Best fit, Newest, Source name, Extension name, Search reliability, Explicit risk) in the past results section -- **historical: "Search reliability" sorted `SourceEvaluation.searchReliabilityScore`, which v0.7.42 made permanently `0.0`; v0.7.42-fix2 retired that mode and replaced it with "For You compatibility" (see the fix2 section above) -- it is no longer an active or meaningful sort.** `EvaluationResultRow` shows compact score subtitle and truncated error messages.
- **Clear confirmation (v0.6.15):** "Clear all" now shows a confirmation `AlertDialog` before deleting evaluation cache.
- **Crash quarantine (v0.6.16):** `SourceEvaluationRunner` writes a probe marker (`source_evaluation_probe_marker`, id=1 single-row) to SQLite before each risky network call (Downloading, LoadingSources, ProbingPopular, ProbingLatest, ProbingSearch, Cleanup). Marker is cleared in the `finally` block after normal extension completion. On next `SourceEvaluationScreenModel` init, the marker is read; `SourceEvaluationCrashRecoveryPolicy` decides: `MarkUnsafe` (marker < 24h old) -> calls `MarkSourceEvaluationUnsafe` -> clears marker; `ClearStale` (>= 24h) -> clears without marking; `DoNothing` (no marker). Unsafe extensions stored in `source_evaluation_unsafe_source` with crash count, phase, reason. `GetSourceEvaluationCandidates` combines the unsafe sources flow; `SourceEvaluationCandidateFilter.buildPool()` accepts `unsafeExtensionKeys: Set<String>` and skips/counts matching extensions. Migration 48.sqm creates both new tables.
- **Crash quarantine UI (v0.6.16):** On crash recovery: `screenErrorMessage` state shows a dismissable error card. If `unsafeSources` non-empty: quarantine card with "View" and "Clear quarantine" buttons; dialog with per-extension rows and "Remove" action; confirmation dialog for bulk clear. Candidate diagnostics shows `unsafeHiddenCount`.
- **Diagnostics (v0.6.16):** "Copy Diagnostics" TextButton in idle screen. `SourceEvaluationDiagnosticsBuilder` pure helper builds a text report (version, timestamp, options, counts, queue phase, Shizuku state, last error, last probe marker).

## Loved Manga View

As of v0.7.0, updated through v0.7.3:

- Accessible via the heart icon button in the For You tab action bar (between Refresh and Settings).
- Shows all manga with `MangaRating.LOVE` from **currently installed sources only**. Entries from uninstalled sources are hidden from display; their taste rows and cross-source link rows are preserved. Reinstalling a source makes its loved entries visible again.
- `LIKE`, `DISLIKE`, and `SEEN` entries are excluded.
- Source filtering uses `SourceManager.getVisibleCatalogueSources()`. If this call fails, an empty state is shown (fail closed -- no uninstalled entries leak through). Local Source (`id == 0L`) is excluded by the same rule as For You.
- Manga rows are resolved from the local DB via `GetManga.await(mangaId)` with fallback to `await(url, sourceId)`. If unresolved, the title from `MangaTaste` is shown and a placeholder cover is used.
- Tapping a manga navigates to its detail page (`MangaScreen(taste.mangaId, true)`).
- "Group clear duplicates" checkbox at the top of the grid toggles conservative duplicate grouping.

**Duplicate grouping rules -- tiered evidence strategy (as of v0.7.2):**
- Tier 1 (strongest): Confirmed cross-source link group. If two loved manga share the same `group_id` in `manga_cross_source_link`, they group regardless of title or description.
- Tier 2: Exact normalized title + same non-blank author.
- Tier 3: Exact normalized title + same non-blank artist.
- Tier 4: Exact normalized title + exact same description (both >= 50 chars).
- Tier 5: Exact normalized title + highly similar long description (token Jaccard >= 0.85, both >= 80 chars).
- Tier 6: Similar title (Jaccard >= 0.80) + same non-blank author.
- Tier 7: Similar title (Jaccard >= 0.80) + same non-blank artist.
- Never groups by title alone. Blank/weak metadata -> always standalone.
- The first-encountered entry for a group becomes the representative.
- Grouped entries show a "%1$d versions" badge at the top-right corner of the cover.
- No taste rows are altered. Grouping is display-only.
- `LovedMangaScreenModel` loads `manga_cross_source_link` rows on startup and passes `linkGroupId` into the grouper. Fails open with empty map if link loading fails.
- Romanized/translated titles without a cross-source link or supporting author/artist metadata remain standalone.

As of v0.7.14: `LoveSortMode` enum (RECENT, OLDEST, TITLE_AZ, SOURCE) with sort chips. Sort applies before grouping. As of v0.7.15: "No clear duplicates found." feedback shown when grouping is on but nothing was grouped.

As of v0.7.35 (historical -- group recommendations screen/model replaced in v0.7.43, see below):
- `LovedMangaScreenModel` has a `filterRating: MangaRating = MangaRating.LOVE` parameter. The LOVE default preserves existing `LovedMangaScreen` behavior. Generalized filter `filterRatedTastesByInstalledSources(tastes, installedSourceIds, allowedRatings: Set<Int>)` replaces the inline filter; `filterLovedTastesByInstalledSources` delegates to it.
- `BrowsePersonalRecommendationsTab` action bar: thumbs-up (Liked) and thumbs-down (Disliked) buttons added between Loved Manga and Export.
- ~~Long-pressing a Loved/Liked Manga card navigates to `GroupSeededRecommendationsScreen(sourceId, url, primaryTitle)`~~ -- superseded in v0.7.43 (`RecommendsScreen(Args.CrossSourceGroupSeed(...))`).
- ~~`GroupSeededRecommendationsScreen` / `GroupSeededRecommendationsScreenModel` in `exh/recs/group/`~~ -- removed in v0.7.43.

As of v0.7.36:
- `RatedMangaCollectionContent(rating, screenModel)` -- shared composable implementing the full UI for all rating tiers. Defined in `RatedMangaScreen.kt`; called by both `RatedMangaScreen` and `LovedMangaScreen`.
- `RatedMangaScreen(ratingValue: Int)` -- full-featured Voyager screen for LIKE/DISLIKE (same UI as Loved Manga): sort chips, group-duplicates toggle, version badges, link management, export (rating-appropriate filename), and Explore overlay icon for recommendations entry.
- `LovedMangaScreen` -- simplified to thin delegation: creates model with default LOVE rating and calls `RatedMangaCollectionContent`.
- All rating tiers share identical features. Export filenames: `kmk_loved_manga.json`, `kmk_liked_manga.json`, `kmk_disliked_manga.json`.
- Explore icon overlay (TopStart, 14dp, `Icons.Outlined.Explore`) on LOVE/LIKE cards opens the group recommendations route without requiring long-press (as of v0.7.43: `RecommendsScreen(Args.CrossSourceGroupSeed(...))`). DISLIKE cards omit the overlay.
- Long-press on LOVE/LIKE cards uses the same route (unchanged behavior, updated target in v0.7.43). Long-press on DISLIKE cards opens `MangaScreen`.
- `LibraryToolbar` / `LibraryRegularToolbar` gain three optional callbacks: `onClickLovedManga`, `onClickLikedManga`, `onClickDislikedManga`. When non-null, each appears as an overflow menu item. Selection mode toolbar is unchanged.
- `LibraryTab` wires the callbacks: Loved → `LovedMangaScreen()`, Liked → `RatedMangaScreen(LIKE.value)`, Disliked → `RatedMangaScreen(DISLIKE.value)`.

As of v0.7.37 (historical -- superseded by v0.7.43's row-based group recommendations; `resolveLinkedGroupRatingConflicts` and the exclusivity behavior below are still current):
- ~~`GroupSeededRecommendationsScreenModel` constants: `MAX_SOURCES=5`, `RAW_CAP_PER_SOURCE=8`, `TARGET_RESULTS=20`, `SEARCH_TIMEOUT_MS=12s`, `LOCALIZE_TIMEOUT_MS=5s`, `TOTAL_LOAD_TIMEOUT_MS=45s`.~~ -- class removed in v0.7.43.
- ~~Source-aware candidate dedup via `GroupRecommendationLoopPolicy`~~ -- removed in v0.7.43 (unused once the one-grid search flow was replaced; each `RecommendationPagingSource` already dedupes its own results).
- `resolveLinkedGroupRatingConflicts(tastes, linkGroupByKey)` in `LovedMangaSourceFilter.kt`: for confirmed cross-source link groups with mixed ratings, the group member with the latest `updatedAt` wins; all other members are suppressed from display. Standalone entries (no `linkGroupId`) are never suppressed. **Still current.**
- `LovedMangaScreenModel.load()` now loads `linkGroupByKey` before the rating filter, then calls `resolveLinkedGroupRatingConflicts` on all installed-source tastes, then applies the rating filter. This ensures each confirmed cross-source link group appears in exactly one rating tab. **Still current.**

As of v0.7.43:
- Loved/Liked group recommendations now reuse the manga-detail Recommendations page pipeline instead of a
  separate one-grid search. `RatedMangaScreen`'s long-press and Explore-icon actions both navigate to
  `RecommendsScreen(RecommendsScreen.Args.CrossSourceGroupSeed(sourceId, url, primaryTitle))`.
- `RecommendsScreenModel` rebuilds the full `GroupRecommendationSeed` via the existing
  `GroupRecommendationSeedBuilder` (reused unchanged), resolves the primary manga, and calls
  `RecommendationPagingSource.createSources(manga, RecommendationSource(sourceId), groupGenreOverride = seed.tags)`
  -- the same provider/extension row list a single manga's Recommendations page uses. Trackers
  (AniList/MAL/MangaUpdates/MangaDex/Comick) still seed from the primary manga only; only cross-extension
  genre search rows (`CrossExtensionGenreSearchSource`) use the group's combined/weighted tag list via a
  new optional `genreOverride` constructor parameter (`null` for the single-manga path -- unchanged there).
- Results are filtered to exclude every `seed.memberKeys` pair (linked versions never appear as their own
  recommendation) and every exact Not Interested/Seen `(source, url)` key (`SeenRecommendationMangaStore`),
  then ranked by `RecommendationScorer.score(primary, candidate) + GroupSeedRecommendationScorer.score(candidate, seed, aliasMap)`.
- ~~`RecommendationCandidateVisibilityPolicy` (favorite/rated/known/min-chapter) is **not** applied to this
  path~~ -- **superseded in v0.7.44, see below.**
- `GroupSeededRecommendationsScreen.kt`, `GroupSeededRecommendationsScreenModel.kt`, and
  `GroupRecommendationLoopPolicy.kt` (+ its test) were deleted.

As of v0.7.44:
- Cross-extension group rows now try a strict-to-lenient tag attempt chain per source
  (`RecommendationQueryAttemptPolicy.buildTagAttemptChain` inside `CrossExtensionGenreSearchSource`:
  `TOP_TAGS_FILTER` -> `TAG_PAIR`/`SINGLE_STRONGEST_TAG` -> `TEXT_ONLY_TOP_TAGS`, capped at 3), then a
  bounded (max 2) title fallback using `GroupRecommendationSeed.titles` (every linked version's title,
  not just the primary manga's) via a new `titlesOverride` constructor parameter -- `null` for the
  single-manga path, unchanged there.
- `RecommendsScreenModel` now computes eligible cross-extension sources via
  `RecommendationSourceSelector.select(...)` (recommendation languages, stored priority order,
  disabled/disliked sources) instead of raw `sourceManager.getVisibleCatalogueSources()` order, and
  passes them into `RecommendationPagingSource.createSources(..., eligibleCrossExtensionSources = ...)`.
  Scoped to the group-seed path only.
- `RecommendationCandidateVisibilityPolicy` **is now applied** to group candidates (favorite, rated,
  seen/Not Interested, known, min-chapter -- not just seed-member/exact-Seen exclusion as in v0.7.43).
  Context (`tasteByKey`, visibility setting, hide-known-manga, min-chapter-count) is loaded once per
  screen; known-id and chapter-count lookups are batched once per row, not per candidate. Still scoped
  to the group-seed path -- the single-manga Recommendations page still does not apply this policy.
- Group rows now drop candidates with a near-zero combined `RecommendationScorer` +
  `GroupSeedRecommendationScorer` score (`GROUP_RELEVANCE_MIN_SCORE = 0.1`) when the seed has real tag
  evidence, instead of only sorting them last.

**Files:**
- `exh/recs/loved/LovedMangaDuplicateGrouper.kt` -- tiered grouper with `LovedMangaGroupReason` enum and token Jaccard similarity
- `exh/recs/loved/LovedMangaSourceFilter.kt` -- pure installed-source filter helper (v0.7.3); generalized in v0.7.35; `resolveLinkedGroupRatingConflicts` added in v0.7.37
- `exh/recs/loved/LovedMangaScreenModel.kt` -- screen model (Loading/Empty/Error/Success states); `filterRating` parameter added in v0.7.35; cross-source exclusivity resolution added in v0.7.37
- `exh/recs/loved/LovedMangaScreen.kt` -- compatibility route; delegates to `RatedMangaCollectionContent` (v0.7.36)
- `exh/recs/loved/RatedMangaScreen.kt` -- full-featured screen for all rating tiers; contains `RatedMangaCollectionContent` shared composable (v0.7.36); group-recs navigation targets `RecommendsScreen` (v0.7.43)
- `exh/recs/group/GroupRecommendationSeed.kt` -- pure data class (v0.7.35); still used (v0.7.43)
- `exh/recs/group/GroupRecommendationSeedBuilder.kt` -- builds seed from cross-source links + local manga genre rows (v0.7.35); still used, unchanged (v0.7.43)
- `exh/recs/group/GroupSeedTag.kt` -- weighted tag value class (v0.7.38); still used (v0.7.43)
- `exh/recs/group/GroupSeedRecommendationScorer.kt` -- group-tag score component; still used by `RecommendsScreenModel` (v0.7.43)
- `exh/recs/RecommendsScreen.kt` / `exh/recs/RecommendsScreenModel.kt` -- now handle `Args.CrossSourceGroupSeed` in addition to single-manga/merged-source args (v0.7.43)
- `app/src/test/.../LovedMangaDuplicateGrouperTest.kt` -- 38 unit tests
- `app/src/test/.../LovedMangaSourceFilterTest.kt` -- 16 unit tests (9 from v0.7.3; 5 new in v0.7.35: filterRatedTastesByInstalledSources + legacy delegate)
- `app/src/test/.../LovedMangaSortTest.kt` -- 8 unit tests for sort modes (v0.7.15)
- `app/src/test/.../RatedMangaExclusivityTest.kt` -- 9 unit tests for cross-source group rating exclusivity (v0.7.37)

## Source Status Display Order

As of v0.6.20:

- Recommendation Settings shows a non-draggable "Source Status" section below the drag-to-reorder priority list.
- Sources are grouped by last For You run status: **With results** (HasMatches) first, **No results** (no matches) second, **Disliked** last.
- Within each group, sources appear in priority order (user's saved drag order).
- `SourceStatusDisplayOrder` pure helper computes grouping and sort order. `SourceDisplayOrderInput(sourceId, priorityIndex, hasMatches, isDisliked)` is the input per source.
- The priority drag list is unchanged -- users still set priority by dragging. The status section is read-only.

## Not Interested (formerly "Seen") Manga Marker

As of v0.6.20, presentation and journaling corrected 2026-08-07 (KMK Post-Screenshot UI and Not
Interested Implementation Plan, Batch E):

- Manga detail page rating dropdown includes "Not interested" / "Undo not interested" item (below
  the existing "Other versions" divider) -- these string *values* were already correct
  (`KMR.strings.rec_mark_seen`/`rec_clear_seen`); only the presentation-state field/parameter names
  used the legacy "seen" terminology until this pass.
- Also includes "Seen other versions" item (always visible when the callback is set, as of v0.7.1) -- opens `CrossExtensionMatchScreen` with `CrossExtensionMatchMode.MarkSeen`.
- `MarkSeen` mode writes to the `seenRecommendationMangaKeys` preference via `SeenRecommendationMangaStore`. It does not write taste rows.
- Seen entries are stored as semicolon-separated `"sourceId|url"` strings. Pipe-in-URL is handled correctly (only first `|` is the separator).
- Seen manga are **always** filtered from For You regardless of the "Hide known manga" setting.
- `seenMangaCount` is included in the recommendation `profileFingerprint()`, so the cache is invalidated whenever the seen set changes.
- `isNotInterested: Boolean` state in `MangaScreenModel.State.Success` (renamed from `isSeen`) tracks
  Not Interested status for the current manga. Updated live by `markSeen()` / `clearSeen()`.
- **2026-08-07: the detail-page primary rate action now visibly reflects Not Interested.**
  Previously the primary `MangaActionButton`'s title/icon/color derived only from the ordinary
  `MangaRating` value, so a manga marked Not Interested still showed a bare "Rate" button with no
  indication -- the dropdown item existed but nothing on the always-visible button did. New pure
  `exh.recs.loved.MangaPreferencePresentationPolicy.resolve(rating, isNotInterested)` (renamed
  2026-08-07, see below) makes the
  precedence explicit: Not Interested and an ordinary rating are stored independently and can
  coexist, and the primary button shows Not Interested first whenever active, falling through to the
  ordinary rating only when Not Interested is false. No stored-data behavior changed.
- **2026-08-07: `MangaScreenModel.markSeen()`/`clearSeen()` now journal through
  `EvaluationModeJournalRecorder`.** This was previously the only Not Interested writer in the app
  that bypassed the journal (For You bulk selection, the reader completion prompt, and the Not
  Interested destination's own removal action were all already correct). Build-before-write/commit
  -after-success, matching the same contract every other reversible local action uses;
  `EvaluationModeUndoService`'s restore/conflict-detection needed no change since it already branched
  correctly on `FIELD_NOT_INTERESTED` -- it was simply never exercised by this path before. A failed
  or cancelled detail-page write no longer silently updates the button state; a recoverable failure
  reports through the existing `snackbarHostState` channel.
- Backup/restore for seen entries is implemented as of v0.7.28 via `BackupSeenMangaKey` at proto field 626. Restore is additive (union with existing seen set). See "Seen Manga Backup" section below.
- **2026-08-07 (second pass, Batch E2): Not Interested is now a structural peer of Love/Like/Dislike,
  not a bolted-on standalone action.** `exh.recs.loved.MangaPreferenceAction` (new sealed interface:
  `Rating`/`NotInterested`/`Clear`) is a presentation/dispatch-only model shared by every taste
  surface -- it never becomes persisted data. `NotInterestedPresentationPolicy` was renamed to
  `MangaPreferencePresentationPolicy` and extended with `dropdownActions()` (the four actions, one
  shared stable order), `labelFor()`, and `notInterestedToggleLabel()`. `MangaInfoHeader.kt`'s detail
  -page dropdown now renders Love/Like/Dislike/Not Interested from one data-driven loop over
  `dropdownActions()` instead of four independently hand-coded `DropdownMenuItem` blocks, and "Not
  interested in other versions" was moved into the same "other versions" divider group as the other
  three rate-other-versions actions instead of sitting behind its own second divider. The primary
  rate button now also carries Compose `stateDescription` accessibility semantics
  (`MR.strings.selected`/`not_selected`) reflecting `MangaPreferencePresentationPolicy.resolve(...)`.
  **New atomic transition:** selecting Love/Like/Dislike while Not Interested is active
  (`MangaScreenModel.setMangaTaste`) now clears the `SeenRecommendationMangaStore` key and writes the
  new rating together, recorded as one Action History journal entry via the new
  `EvaluationModeJournalRecorder.buildRatingChangeReplacingNotInterested(...)`
  (`changedFields = {FIELD_RATING, FIELD_NOT_INTERESTED}`); `EvaluationModeUndoService.restoreOne`
  needed zero changes since it already restored both fields from one entry generically. `Clear Rating`
  intentionally does not affect Not Interested (independent axis, unchanged). This transition is only
  reachable from the detail page: `LovedMangaScreenModel` has no bulk re-rate action and For You
  always filters out Not Interested manga, so it is deliberately not wired into those two bulk
  surfaces. `NotInterestedMangaScreen.kt` was deliberately NOT given bulk-select/search parity with
  `RatedMangaScreen.kt` in this pass -- no corresponding bulk action exists for Not Interested to
  attach it to; its existing loading/empty/success/error/per-row-removal states are unchanged. See
  `private/docs/audits-and-reports/KMK_POST_SCREENSHOT_UI_AND_NOT_INTERESTED_LEDGER_2026-08-07.md`'s
  "Batch E2 result" entry for the full test/validation record.

## Latest Catalogue Discovery Lane (For You)

As of 2026-08-08 (batch L1 of the Latest-catalogue/exposure packet):

- For You now has a **third discovery lane** beside the personalized tag/text search chain and the
  bounded Popular-catalogue fallback: a bounded **Latest** probe
  (`CatalogueSource.getLatestUpdates(1)`), implemented as
  `BrowsePersonalRecommendationsScreenModel.tryLatestCatalogueLane(...)`.
- **Ordering:** personalized strict-to-lenient chain first → Latest → Popular fallback. Personalized
  relevance stays dominant; Latest only ever runs when the personalized chain produced nothing usable
  and did not itself fail, so it can never displace a personalized result set. Popular is untouched
  for every source where Latest is unsupported, empty, failing, or out of budget.
- **Bounded:** `RecommendationLatestBudgetPolicy` converts a user-configured percentage of the
  refresh's attempted sources into an absolute attempt count, capped at 6 per refresh, one page per
  source, no pagination, no retry. The budget is resolved once per refresh and consumed through an
  `AtomicInteger` because sources in a batch are searched concurrently.
- **Capability handling:** `Source.supportsLatest` is a *self-reported* flag and is used only as a
  cheap pre-filter. Every real outcome is classified by `RecommendationCatalogueLanePolicy` —
  unsupported-capability, unsupported-at-runtime (`UnsupportedOperationException` from a source that
  claimed support), empty, malformed, recoverable error, budget-exhausted, not-eligible. **No source
  is hardcoded.** The call routes through `SourceRuntime.run(..., SourceRuntimeOperation.Latest, ...)`,
  so cancellation and fatal errors propagate while per-source failures stay isolated.
- **No filter is bypassed.** Latest candidates run through the same `processRawCandidates` /
  `mergeFreshAndRememberedCandidates` pipeline as every other candidate: blocked/adult tags, language,
  minimum chapters, known/rated/Not Interested filtering, metadata confidence, enrichment, the
  positive-taste-evidence gate, and cross-source dedup all apply unchanged.
- **Provenance is typed and separate.** `RecommendationDiscoveryLane` records Latest under its own
  `LATEST_CATALOGUE` key in candidate memory and discovery progress. `POPULAR_CATALOGUE` deliberately
  reuses the pre-existing `CATALOGUE_FALLBACK` sentinel so rows already on disk keep their meaning,
  and a missing/unrecognized value reads as legacy/unknown — **never** as Latest. Latest is never
  persisted as a `successfulStrategy`.
- **Setting:** "Explore new releases" on **For You sources** (`recommendationLatestExplorationPercent`,
  supported 0/10/20/30/50%, default 20%). `0` switches the lane off entirely and is the documented
  behavior kill switch. Journalled through the existing preference Action History family, so it is
  undoable like every other recommendation preference, and indexed in Recommendation Settings search.

### Minimum chapter count — conformance repair (2026-08-08)

The existing filter's *behavior* is unchanged (below-threshold hidden, at-or-above visible, unknown
count fails open, lookup failure fails open). What changed is that its **supported values now have a
single owner**: `exh.recs.RecommendationMinChapterCountPolicy` (`0/5/10/20/50`, default `Off`).
Previously the value list was hardcoded in the composable, the preference read was unsanitized, and
`setMinChapterCount()` persisted any `Int` — so a corrupt or out-of-contract value could silently
filter at a threshold the picker never offered and render as an unlabelled raw number. Reads, the
setter, `BrowsePersonalRecommendationsScreenModel`, `RecommendsScreenModel`, the cache fingerprint,
and the settings row now all resolve through that one policy. The summary copy was shortened to match
adjacent settings rows; `SameMangaListPrefRow` and the section placement are unchanged.

### Recommendation Settings quick-access panel — layout repair (2026-08-08)

`EdgeQuickAccessPanel` sizing now comes from the pure, testable
`EdgeQuickAccessPanelLayoutPolicy`. Fixed: the panel no longer reserves a fixed 70% of screen height
with top-packed content (the reported poor centering / weak balance / dead space) — it wraps its
content, is bounded at 90%, centers what fits, and **scrolls** rather than clipping destinations on a
short screen (a latent defect the old unscrollable `Column` had). Item width is responsive
(104dp/128dp at the 600dp breakpoint) instead of a fixed 96dp, the forced 10sp label is now
`labelMedium`, the `maxLines` cap is gone so the longest title wraps, and the selected state carries a
container tint plus an explicit accessibility `stateDescription` instead of colour alone. The handle's
*painted* 28dp width is preserved (deliberate Android edge-back-gesture safety); its *touch* target is
raised to the platform minimum via `minimumInteractiveComponentSize()`. All five destinations,
`toScreen()` back-stack behavior, the scrim, and `BackHandler` are unchanged.

### Not yet implemented from this packet

Local **exposure history** and **soft reranking** (packet Steps 2 and 4) are **not implemented**.
`RecommendationExposurePolicy` and `RecommendationDisplayReranker` exist and are fully unit-tested but
are **not called by any production code**, and no exposure is recorded or persisted. No For You
ordering behavior changed in this pass.

## Source Evaluation Reassessment

As of v0.6.20:

- Source Evaluation screen loads `currentRatedCount` (from `GetMangaTaste.awaitAll().size`) and `reassessmentBaselineCount` (from preferences) on open.
- When `currentRatedCount - reassessmentBaselineCount >= 100`, an `InfoCard` prompt is shown: "N new ratings since last source evaluation. Reassessment is recommended."
- "Reassess sources" button is always shown. Sets `skipAlreadyEvaluated = false`, then starts evaluation. Button labeled "Reassess using current tastes" when below threshold.
- Baseline is updated automatically when evaluation status transitions to `Completed`. Cancelled or failed runs do not update the baseline.
- Preferences: `sourceEvaluationLastReassessmentRatingCount`, `sourceEvaluationLastReassessmentAt`.

## Source Explainability

As of v0.6.20, updated in v0.7.15:

- Each past result row in Source Evaluation shows evidence strength and evaluation freshness appended to the subtitle.
- Evidence strength: Strong (many tag+title matches, high search success, large sample), Moderate, Weak, or Low confidence (minimal data).
- Last evaluated: "Last evaluated today" or "Last evaluated N days ago."
- As of v0.7.15: logic uses a pure `EvidenceStrength` enum classifier (`evidenceStrength()`) and a day-count helper (`lastEvaluatedDaysAgo()`). The composable maps these to KMR strings via `stringResource(...)`. No hardcoded English strings remain.
- Verdict badge labels ("Strong Fit", "Worth Trying", etc.) are also KMR strings as of v0.7.15.

## Management Controls

As of v0.6.20:

- Source Evaluation screen has a collapsible "Source management" section (collapsed by default).
- Three actions, each guarded by a confirmation dialog:
  - **Reset disliked sources**: clears `dislikedRecommendationSourceKeys` preference.
  - **Reset reassessment baseline**: clears the rating count and timestamp preferences.
  - **Clear seen manga**: clears `seenRecommendationMangaKeys` preference.
- `ManagementAction` enum in `SourceEvaluationScreenModel` types the three cases.

## Source Evaluation Extension Version Metadata and Update Detection

As of v0.7.4:

- `source_evaluation` table gained three nullable columns via migration 51: `extension_version_name`, `extension_version_code`, `extension_apk_name`.
- `SourceEvaluationScorer.score()` and `errorRecord()` accept these three optional fields and write them to `SourceEvaluation`.
- `SourceEvaluationRunner` passes `ext.versionName`, `ext.versionCode`, `ext.apkName` from `Extension.Available` to the scorer at all three callsites (score, per-source error, per-extension error).
- `SourceEvaluationUpdatePolicy` pure helper: `detectUpdateStatus(evaluation, available)` compares stored vs available versionCode; `detectForPool(evaluations, available)` uses best stored versionCode. Returns `UPDATED`, `NOT_UPDATED`, `UPDATE_UNKNOWN`, or `NEVER_EVALUATED`.
- `SourceEvaluationCandidateFilter.applyOptions()` gained `onlyUpdatedEvaluated: Boolean = false`. When true, only candidates with confirmed `UPDATED` status pass.
- `SourceEvaluationScreenModel.State` gained `updatedEvaluatedExtensionCount: Int`. Computed in `applyOptionsAndUpdateState()` across all eligible candidates.
- `startReassessUpdated()` action: sets `onlyUpdatedEvaluated = true`, `skipAlreadyEvaluated = false`, re-applies options, launches evaluation if candidates exist, then resets options.
- `SourceEvaluationScreen` shows "N evaluated extension(s) have updates" info card and "Reassess updated extensions" button when count > 0.
- **Not implemented**: actual bounded rec-fit probe execution in `SourceEvaluationRunner`. See `SourceRecommendationFitEligibility` and `SourceRecommendationFitScorer` below.
- Pre-existing test bug fixed: `GetTasteProfileTest.fakeTasteRepo` was missing 7 `CrossSourceMangaLink` abstract method stubs, blocking test compilation.

## Recommendation Fit Helpers

As of v0.7.4 (**historical -- superseded by v0.7.42; see "Source Evidence Redesign (v0.7.42)" and its
fix1/fix2 sections above for the current rule**):

- `SourceRecommendationFitEligibility` -- pure stateless gate. As originally shipped in v0.7.4, this
  checked verdict (`STRONG_FIT` or `WORTH_TRYING` only) and minimum sample count (`MIN_SAMPLE_COUNT = 3`),
  returning `ELIGIBLE`, `INELIGIBLE_VERDICT`, or `INSUFFICIENT_EVIDENCE`. **This STRONG_FIT/WORTH_TRYING-only
  description is no longer accurate.** As of v0.7.42, `check()` also admits a source when catalogue
  metadata confidence is `LOW`/`UNKNOWN` (inconclusive evidence) with minimal sample count -- see
  "Source Evidence Redesign (v0.7.42)" above for the authoritative current rule, and the fix1/fix2
  sections for how that rule is now shared across every consumer (queue, diagnostics, row labels, sort).
- `SourceRecommendationFitScorer` -- pure stateless scorer. Takes `Outcome` (visible candidates, filtered-out count, blocked-tag candidates, matched groups, top picks contribution, no-matches, errors, avg score) and returns a quality score in `[0.0, 1.0]`.

As of v0.7.6, probe execution is live (no longer deferred):

- `SourceRecommendationFitProbe` -- bounded 2-plan probe. Calls `getSearchManga(page=1, ...)` with 30s timeout per plan. Returns `ProbeOutcome` with query stats, candidate counts, match groups, and error info.
- `SourceRecommendationFit` domain model -- stores fitKey (= `"${evaluationKey}::rec_fit"`), evaluationKey, sourceId, extensionPkgName, signatureHash, extensionName, sourceName, lang, evaluatedAt, probe stats, qualityScore, verdict (`RecommendationQualityVerdict`), reasonsJson, errorMessage.
- `RecommendationQualityVerdict` enum -- GREAT, GOOD, MIXED, WEAK, NO_MATCHES, ERROR, TOO_LITTLE_EVIDENCE.
- `GetSourceRecommendationFit` -- interactor to load fits by evaluationKey or as a map.
- `UpsertSourceRecommendationFit` -- interactor to write/update a fit record.

## Source Evaluation Installed-Source Display Filter

As of v0.7.6:

- `SourceEvaluationDisplayFilter` -- pure stateless helper. `filter(evaluations, installedExtensionKeys, showInstalled)` returns `FilterResult(visible: List<SourceEvaluation>, hiddenInstalledCount: Int)`.
- Installed key format: `"${signatureHash}|${extensionPkgName}"`.
- When `showInstalled = false`: evaluations matching installed keys are hidden; `hiddenInstalledCount` equals the number hidden.
- When `showInstalled = true`: all evaluations are visible; `hiddenInstalledCount = 0`.
- `SourceEvaluationScreenModel.State` holds `showInstalled: Boolean` (default `false`) and `hiddenInstalledCount: Int`.
- Screen shows a `FilterChip` with dynamic label: "Hide installed" when `showInstalled = true`, "Show installed" when `showInstalled = false`. Also shows "Hidden installed: N" count chip.

**v0.7.7 toggle fix:** The chip was incorrectly hidden when `showInstalled = true` because the condition was `hiddenInstalledCount > 0 || !state.showInstalled`. Fixed to `hiddenInstalledCount > 0 || state.showInstalled` so the chip always appears when installed rows are visible, allowing the user to hide them again.

## Source Evaluation Recommendation-Quality Probe

As of v0.7.6 (on-demand trigger added in v0.7.7):

- After a Source Evaluation batch completes, `SourceEvaluationRunner` automatically runs `SourceRecommendationFitProbe` for each STRONG_FIT and WORTH_TRYING source in the batch results.
- Results are stored as `SourceRecommendationFit` records via `UpsertSourceRecommendationFit`.
- Each past evaluation row shows a third line: "Recommendations: Great", "Recommendations: Good", etc., derived from `RecommendationQualityVerdict`.
- Non-promising rows (REJECTED, WEAK, EXPLICIT_HEAVY, etc.) show nothing on the third line.
- `SourceEvaluationScreenModel.State` includes `recommendationFitsByEvalKey: Map<String, SourceRecommendationFit>`, loaded from DB on init and after each probe run.

**v0.7.7 additions:**

- Promising rows without a stored `SourceRecommendationFit` now show "Recommendations: Not checked" instead of nothing.
- A "Recommendation Quality" section appears in the screen list above the sort header whenever `totalPromising > 0`. Shows missing count, "Evaluate recommendations" button, and "Re-check all" button.
- `evaluateRecommendationQualityForPromising(reCheckAll: Boolean)` action runs the probe inline for currently installed promising sources without re-running a full source evaluation batch.
- `SourceRecommendationQualityQueue` pure helper partitions evaluations into `missingPromising`, `checkedPromising`, `ineligible`.
- Screen model gains `recQualityRunning: Boolean`, `recQualityProgress: Int`, `recQualityTotal: Int` state fields.

**v0.7.7 follow-up fix (shipped in v0.7.8 APK):**

- `evaluateRecommendationQualityForPromising()` now handles non-installed promising sources. It resolves the extension from `lastCandidatePool.value?.allEligible`, temporarily installs via the configured installer override, probes, persists, and cleans up. Error messages reflect the real failure reason ("Extension not found in available sources", "Install failed or timed out", "Source not found in installed extension", etc.) instead of always writing "Extension not installed or source not found".
- `SourceRecommendationQualityExtensionResolver` -- new pure resolver. `resolve(evaluation, available)` finds the best `Extension.Available` match using 4-step resolution: exact sig+pkg -> pkg-only -> sig+name -> name+lang (unambiguous only). Returns `Found`, `Ambiguous`, or `NotFound`.

**v0.7.10 fix:**

- `loadAvailableExtensionsForRecQuality()` added to `SourceEvaluationScreenModel`. Reads `extensionManager.availableExtensionsFlow.value` (full unfiltered list). Falls back to `lastCandidatePool.value?.allEligible` only if empty. This fixes the root cause: when no evaluation batch had run in the session, `lastCandidatePool` was null and every non-installed promising source got "Extension not found in available sources" ERROR.
- `SourceRecommendationQualityInstalledResolver` -- new pure helper. 4-step installed extension matching: exact sig+pkg -> pkg-only -> sig+name -> name+lang. Returns `Found`, `Ambiguous`, or `NotFound`. Replaces the previous single `find { pkgName + signatureHash }` call.
- `SourceRecommendationQualitySourceResolver` -- new pure helper. 5-step source-within-extension matching: exact source id -> name+lang -> name -> normalized name+lang -> normalized name. Returns `Found`, `Ambiguous`, or `NotFound`. Replaces `findSourceInInstalledExt()` (which had only 2 steps and a nullable return).
- Error messages are now stage-specific at each failure point in the installed and non-installed paths.
- `installedExtensionKeys` in `SourceEvaluationScreenModel` is now reactive: a flow observer on `extensionManager.installedExtensionsFlow` replaces the one-time snapshot. The display filter chip and hidden-count update immediately when extensions install or uninstall.
- `visibleSources` in `RecommendationsSettingsScreenModel` is no longer a static field. All three call sites now call `sourceManager.getVisibleCatalogueSources()` inline. A new reactive observer on `extensionManager.installedExtensionsFlow` calls `refreshVisibleSources()` to update `orderedSources`, `availableLanguages`, and `boostedSourceIds` whenever extensions change.

## Best Version / Chapter Quality Workflow

As of v0.7.9 (polished in v0.7.9, introduced in v0.7.8):

- "Find best version" item in the manga detail rating dropdown (after "Seen other versions") opens `BestVersionCompareScreen(originMangaId: Long)`.
- The screen runs a bounded parallel search across all configured same-language sources, using `SameMangaCandidateSearcher` -- the same multi-query, per-source, capped pattern as cross-extension matching.
- Results cap is configurable (`sameMangaMatchResultsPerSource`, default 2 per source). Preselect behavior is configurable (`sameMangaMatchPreselectResults`, default true).
- The workflow follows a 10-step state machine: LoadingOrigin -> SearchingCandidates -> ConfirmCandidates -> LoadingChapters -> SelectChapter -> LoadingPreview -> ComparePreview -> PreparingMigration -> Done / Error.
- `BestVersionChapterMatcher.findMatch()` finds the closest chapter within +/-1.0 of the origin's chapter_number. `selectDefaultChapter()` picks in-progress > latest-read > latest by number.
- `BestVersionPageSampler.sample()` samples mid-chapter pages from a 30-75% window (skipping first 2 pages when avoidFirstPages=true, never including the last page). Sample size is configurable (2/5/10, default 5).
- Page previews are loaded via `HttpSource.getPageList` + `getImageUrl` and displayed with Coil3 `AsyncImage`.
- Sampled page thumbnails are tappable. Tap opens a fullscreen `FullscreenPagePreviewDialog` (v0.7.9).
- Fullscreen preview supports pinch-to-zoom (max 5x) and pan via `detectTransformGestures`. Closing returns to the comparison screen with all state intact (v0.7.9).
- After the user selects the best candidate, a Migrate / Copy / Cancel dialog calls `MigrateMangaUseCase`. Cancel correctly dismisses the dialog (v0.7.9 fix).
- The dialog guard requires `selectedBestKey` to resolve to a real candidate in `selectedCandidates`. Stale keys auto-clear via `LaunchedEffect` (v0.7.9).
- `dismissMigrationDialog()` sets `selectedBestKey = null` and `isMigrating = false` without clearing any other preview or candidate state (v0.7.9).
- On confirmation, a `MangaSourceQualitySignal` record is written to the new `manga_source_quality_signal` table (migration 53). This table stores origin/selected source+url, chapter number, sample size, sampled page URLs, and a timestamp.
- As of v0.7.16, Best Version quality signals are included in backup and sync via `BackupMangaSourceQualitySignal` at proto field 625. Restore skips duplicates by origin+selected source identity.
- Recommendation Settings has a new "Same manga matching" section with 4 preference items (results per source, preselect, sample size, avoid first pages).
- `SameMangaMatchSettings` is a pure data class with clamp helpers for the two list preferences.
- `CrossExtensionMatchScreenModel` now reads the cap and preselect values from preferences rather than using hardcoded constants.

**New files (best version):**
- `exh/recs/matching/SameMangaMatchSettings.kt`
- `exh/recs/matching/SameMangaCandidateResult.kt`
- `exh/recs/matching/SameMangaCandidateSearcher.kt`
- `exh/recs/bestversion/BestVersionPageSampler.kt`
- `exh/recs/bestversion/BestVersionChapterMatcher.kt`
- `exh/recs/bestversion/BestVersionCompareScreenModel.kt`
- `exh/recs/bestversion/BestVersionCompareScreen.kt`

**New files (quality signal DB):**
- `data/.../manga_source_quality_signal.sq`
- `data/.../migrations/53.sqm`
- `domain/.../taste/model/MangaSourceQualitySignal.kt`
- `domain/.../taste/repository/MangaSourceQualitySignalRepository.kt`
- `domain/.../taste/interactor/GetMangaSourceQualitySignals.kt`
- `domain/.../taste/interactor/UpsertMangaSourceQualitySignal.kt`
- `data/.../taste/MangaSourceQualitySignalRepositoryImpl.kt`

## Recommendation Bundle Export / Import

As of v0.7.5:

- Recommended manga can be exported as a JSON bundle and imported on another device.
- JSON schema identifier: `kmk.recommendation.bundle`, version 1. Validated by `RecommendationBundleValidator`.

**Export surfaces:**
- **For You tab -- Export Top Picks**: share icon in action bar. Exports from `combinedDetailResult` (full scores and matched groups). Filename `kmk_top_picks.json`.
- **For You tab -- Export source row**: long-press a source row header to export that source's results only. Filename `kmk_<source_name>.json`.
- **TopPicksScreen**: share icon in app bar. Exports with reduced metadata (no scores, no groups -- only manga IDs are available at this screen). Filename `kmk_top_picks.json`.
- **Loved Manga screen**: share icon in app bar. Filename `kmk_loved_manga.json`.

**Import entry point:** Settings > Data storage > "Import recommendation bundle" -> file picker (application/json) -> validates -> opens preview screen.

**Preview screen (`RecommendationBundleImportScreen`):**
- Each item resolves to one of: `ReadyToAdd`, `AlreadyInLibrary`, `MissingSource`, `AmbiguousSource`, `SourceInstalledNeedsResolve`, `NeedsManualMatch`, `Unsupported` (Local Source, id=0), `Error`.
- Items with a `MissingSource` state (exactly one candidate extension found) show an "Install" button per missing extension.
- Items with an `AmbiguousSource` state (multiple candidate extensions matched the same package name) show a separate install card per candidate, labeled "Ambiguous match". The user must choose which to install. No silent first-pick.
- Install via `extensionManager.installExtension(ext)`. Items re-resolve on `InstallStep.Installed`.
- Default selection includes all `ReadyToAdd` and `SourceInstalledNeedsResolve` items.
- "Add selected (N)" calls `RecommendationBundleLibraryAdder.addToLibrary(skipDuplicates=true)` for each selected item.
- `SourceInstalledNeedsResolve` items are added via `networkToLocalManga(sManga.toDomainManga(resolvedSourceId))`.
- An add-summary `AlertDialog` shows Added / Already in library / Failed counts.

**Source resolution (3-step):**
1. Exact `sourceId` match.
2. `extensionPkgName` + `sourceName` + `sourceLang` match (handles sourceId changes between fork installs).
3. `extensionSignatureHash` + `sourceName` + `sourceLang` match (handles pkgName changes).

**Duplicate detection rule:** requires title + (author OR artist). Title alone is never flagged as a duplicate.

**New files:**
- `exh/recs/share/RecommendationBundle.kt` -- pure JSON data models
- `exh/recs/share/RecommendationBundleValidator.kt` -- pure validator (max 2 MB / 500 items / 200 sources)
- `exh/recs/share/RecommendationBundleSourceResolver.kt` -- pure source resolution and duplicate detection
- `exh/recs/share/RecommendationBundleExporter.kt` -- bundle builder and URI writer
- `exh/recs/share/RecommendationBundleImporter.kt` -- URI reader, delegates to validator
- `exh/recs/share/RecommendationBundleLibraryAdder.kt` -- adds to library with duplicate + category handling
- `exh/recs/share/RecommendationBundleImportScreenModel.kt` -- resolution, install, and add flows
- `exh/recs/share/RecommendationBundleImportScreen.kt` -- Voyager screen (primitive `uriString: String` constructor)

## Source Evaluation Hardening

As of v0.7.11 (corrected from the incorrectly-versioned "v0.8.0" build), updated in v0.7.16:

- **Pre-run consent dialog**: First use of Source Evaluation (via "Start evaluation", "Reassess updated", or "Continue next batch") requires the user to acknowledge a one-time consent dialog explaining install/probe/cleanup behavior and installer mode differences. Consent is stored in `sourceEvaluationConsentGiven` preference. Once acknowledged, the dialog does not appear again.
- **`PendingConsentAction` enum**: Tracks why the consent dialog was opened (`START_EVALUATION`, `REASSESS_UPDATED`, `CONTINUE_EVALUATION`, `VIEW_ONLY`). `confirmConsent()` dispatches to the correct path based on pending action. `VIEW_ONLY` closes the dialog without starting evaluation.
- **"View evaluation warning" button**: TextButton added to the diagnostics row (next to "Copy Diagnostics") so users can re-read the consent warning at any time without starting an evaluation. Calls `showConsentWarning()` which sets `pendingConsentAction = VIEW_ONLY`.
- **Cleanup outcome warnings in EvaluationSummaryCard**: After a completed/failed/cancelled batch, if any extensions required manual uninstall prompts (`promptRequiredCleanupCount > 0`) or cleanup failed (`cleanupFailedCount > 0`), warning messages are shown in error color, directing the user to Browse > Extensions.
- **`cleanupFailedCount` computed property**: Added to `SourceEvaluationQueueState` alongside existing `promptRequiredCleanupCount`.
- **All offline error messages use KMR string**: `source_evaluation_offline_error` replaces four hardcoded "No internet connection" strings in `startReassessUpdated()`, `startEvaluation()`, `confirmAndStartWithPrompts()`, and `continueEvaluation()`.
- **State-lost error uses KMR string**: `source_evaluation_state_lost_error` replaces hardcoded string in `SourceEvaluationJob.doWork()`.
- **Crash recovery message uses KMR string**: `source_evaluation_crash_recovery_marked_unsafe` replaces hardcoded message in `SourceEvaluationScreenModel.init`.
- **`SourceEvaluationConsentPolicy`**: Pure stateless helper. `isConsentRequired(consentGiven: Boolean): Boolean` -- 3 unit tests.
- **`sourceEvaluationConsentGiven()` preference**: Added to `SourcePreferences`. Key: `"source_evaluation_consent_given"`, default `false`.
- **Process-death behavior**: If the app process is killed before `SourceEvaluationJob.doWork()` starts, `pendingCandidates` / `pendingOptions` (in-memory `@Volatile` fields) become null and the job writes a `source_evaluation_state_lost_error` failure. This is honest recovery via clear failure message -- not durable resume. The user can restart from the screen or use batch continuation.
- **Installer-mode consent reset (v0.7.16)**: switching Source Evaluation to SHIZUKU or CURRENT mode resets `sourceEvaluationConsentGiven`, so the risk notice reappears when the cleanup/security profile changes.
- **Prompt-required cleanup action (v0.7.16)**: `SourceEvaluationRunner` records cleanup status per evaluated extension. When system-installed extensions cannot be silently cleaned up, `EvaluationSummaryCard` shows an "Uninstall N left-behind extension(s)" button wired to `cleanupPromptRequiredExtensions()`.
- **Process-death leftover cleanup warning (v0.7.16)**: startup recovery records a still-installed leftover package in `sourceEvaluationLeftoverPkg`; Source Evaluation then shows a persistent warning with an uninstall action.
- **Migration/proto/OCR backup guard tests (v0.7.16)**: `KmkMigrationTest`, expanded `TasteBackupRoundTripTest`, and `KmkOcrExclusionTest` cover KMK migrations, proto fields 620-625, quality-signal backup, cross-source-link sync payload coverage, and OCR backup exclusion.
- **Bundle import throttle (v0.7.16)**: `RecommendationBundleImportScreenModel.addSelected()` delays 200 ms between add attempts to reduce source/network hammering during large bundle imports.

## Recommendation Settings Reorganization

As of v0.7.12:

- `RecommendationsSettingsScreen` LazyColumn section order changed to: Daily recommendations (language selector at top), Ratings and known manga, Tags, Source priority, Same manga matching (moved up from below Sources To Try), Source status, Management (Sources To Try + cleanup), Experimental -- Source Evaluation (renamed to clarify it's advanced).
- "Daily recommendations" is a new section header grouping the language selector at top.
- "Ratings and known manga" renamed from "Rated manga visibility".
- "Source status" renamed from the previous composite string.
- "Management" is a new section header grouping Sources To Try and cleanup actions.
- "Experimental -- Source Evaluation" renamed from plain "Source Evaluation" to signal it's an advanced/experimental tool.

## Rec-Quality Probe Enrichment and Diagnostics

As of v0.7.13:

**Root cause fixed:** `SourceRecommendationFitProbe` previously called `source.getSearchManga()` and scored raw results immediately via `toDomainManga()`. Most extensions return `SManga` with `genre = null` on search results; `getMangaDetails` is required to populate tags. `PersonalRecommendationScorer` seeing null genre produced score = 0, making nearly all Strong Fit / Worth Trying sources appear as WEAK or NO_MATCHES even when they are good recommenders.

**Bounded enrichment (per plan):**
- After converting raw SManga to domain manga, the probe now calls `source.getMangaDetails(smanga)` for each candidate with no genre metadata.
- Enrichment cap: 5 candidates per plan, 10s timeout per detail call, sequential.
- `needsProbeEnrichment()` = `genre.isNullOrEmpty()` (not `!initialized` -- genre presence is what the scorer actually needs).
- No DB writes from probe -- avoids side-effects from transient probe runs.
- `SourceRecommendationFitProbeOutcome` gained `enrichedCandidateCount` and `weakMetadataCandidateCount` fields.

**Failure classifier (`SourceRecommendationFitFailureClassifier`):**
- Pure object with `SourceRecommendationProbeFailureKind` enum (14 values).
- `classify(errorMessage)`: case-insensitive substring matching on fixed-format error strings from the pre-probe resolution chain and the probe itself.
- `isInstallOrLoadIssue(kind)`: distinguishes infrastructure failures (extension not found, install failed, source not resolved) from search-level failures.

**Diagnostics summary (`SourceRecommendationQualityDiagnostics`):**
- `Summary(checkedCount, notCheckedCount, installLoadIssueCount, searchErrorCount, noResultsCount, weakCount, goodCount)` computed from promising evaluations + their fit records.
- Displayed as a compact label in the Recommendation Quality section when `checkedCount > 0`.

**Reason text persistence:**
- `buildRecQualityFitFromOutcome()` now populates `errorMessage` for all verdicts, not just ERROR:
  - ERROR: first 2 reasons joined, capped at 200 chars.
  - NO_MATCHES: "Search returned results but none had usable genre metadata" (if `weakMetadataCandidateCount > 0`) or "Search returned no results for taste profile tags".
  - WEAK: describes `weakMetadataCandidateCount` (after enrichment) and `blockedTagCandidateCount`.
- Reason text shown in subdued color for WEAK/NO_MATCHES, error color for ERROR.

**New files:**
- `app/.../exh/recs/evaluation/SourceRecommendationFitFailureClassifier.kt`
- `app/.../exh/recs/evaluation/SourceRecommendationQualityDiagnostics.kt`
- `app/src/test/.../SourceRecommendationFitFailureClassifierTest.kt` -- 15 tests
- `app/src/test/.../SourceRecommendationQualityDiagnosticsTest.kt` -- 10 tests

## Rec-Quality Error Transparency

As of v0.7.12:

- `SourceEvaluationScreenModel.buildRecQualityFitFromOutcome()`: when `label == ERROR` and `outcome.reasons` is non-empty, `errorMessage` is now populated from `outcome.reasons.take(2).joinToString("; ").take(200)` instead of always writing `null`.
- `SourceEvaluationScreen`: a second `Text` element below the quality label displays `recFit.errorMessage` in error color when verdict is `ERROR` and the message is non-blank. Uses the `source_evaluation_rec_quality_error_hint` KMR string.
- This surfaces both Scenario A errors (pre-probe: install failure, extension not found) and Scenario B errors (probe-level: `getSearchManga` exceptions) to the user.
- Probe-level error messages have the form `"Plan TOP_TAGS_FILTER: error -- {exception.message.take(60)}"`. Pre-probe errors have descriptive stage-specific strings from the resolver chain.

## Source Fit Stats / Quality Learning

As of v0.7.19:

- After each For You run, rolling per-source fit stats are accumulated in a `SourceFitStats` preference entry. Fields: `runCount`, `shownCount`, `noMatchCount`, `filteredCount`, `errorCount`, `hiddenByDuplicateCount`, `totalVisibleCandidates`, `updatedAt`.
- `SourceFitStats.fitLabel` computes a badge label after >= 3 runs: Great fit, Good fit, Mixed, No matches, Often filtered, Often errors.
- "Suggest priority order based on fit" button appears in Recommendation Settings after >= 3 sources have >= 3 runs. Moves best-fit sources to top; no-data sources remain at bottom.
- Disabled/OutsideAttemptLimit statuses are excluded from accumulation -- only actually-searched sources are counted.
- `SourceFitStatsStore` persists stats in `SourcePreferences.recommendationSourceFitStats()` as a semicolon-separated string of per-source encoded entries. Keys are stable source IDs.
- Updated in v0.7.32: rolling 30-day window added (see below).

**Files:**
- `app/src/main/java/exh/recs/SourceFitStats.kt` -- fit stats data class with merge() and fitLabel
- `app/src/main/java/exh/recs/SourceFitStatsStore.kt` -- persistence and merge logic
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` -- loads stats, computes suggested order
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` -- SourcePriorityItem shows fit label and suggest button

## Quality Signal History / Best Version History

As of v0.7.27:

- Recommendation Settings: new "Best Version History" section in the Management area.
- Shows all past Best Version quality signal records grouped by origin manga. Each entry shows: selected source name, selected title (when different from origin), chapter used, and date confirmed.
- Each record can be deleted individually (deletes only the quality signal -- does not affect library entries, ratings, or cross-source links).
- Action bar has a "Clear all history" button with a confirmation dialog.
- Uses existing `GetMangaSourceQualitySignals` interactor, new `DeleteMangaSourceQualitySignal` interactor.

**Files:**
- `app/src/main/java/exh/recs/settings/QualitySignalHistoryScreenModel.kt`
- `app/src/main/java/exh/recs/settings/QualitySignalHistoryScreen.kt`
- `domain/src/main/java/tachiyomi/domain/taste/interactor/DeleteMangaSourceQualitySignal.kt`

## Seen Manga Backup

As of v0.7.28:

- Komikku backup/restore now includes the "Seen" manga dismissal set (For You).
- Proto message: `BackupSeenMangaKey` at proto field 626 in the root backup message.
- `TasteBackupCreator` reads all keys from `seenRecommendationMangaKeys` preference and serializes to repeated `BackupSeenMangaKey`.
- `TasteBackupRestorer` merges restored keys into the existing preference set (additive union -- dismissals accumulated after backup point are preserved, not cleared).
- Forward compatibility: old backups without proto field 626 restore cleanly with no seen keys (proto default = empty repeated field).
- New test class: `SeenMangaKeyBackupTest` with 6 round-trip tests (single key, multi-key, merge-new, no-duplicate-on-overlap, empty-backup-leaves-existing-unchanged, empty-existing-produces-backup-set).
- Proto field range comment in `Backup.kt`: "Proto numbers 620--629 are reserved for this fork's taste system -- check upstream before reusing."

## For You Quick UX Polish

As of v0.7.29:

- **A1 -- Source status timestamps:** Recommendation Settings source priority list shows "Last checked: X ago" below each source status label when >= 1 run has been recorded. Uses `SourceFitStats.updatedAt` and the system relative-time formatter. Sources with no run history show nothing.
- **A2 -- Pull-to-refresh:** The For You recommendations list supports pull-to-refresh gesture. Pulling down triggers the same `screenModel.refresh()` as the action bar button. The `isLoading` state drives the refresh indicator.
- **A3 -- Loved Manga live updates:** `LovedMangaScreenModel` subscribes to a reactive flow instead of one-shot await on init. Ratings applied on other screens are reflected immediately in the Loved Manga list without requiring navigate-away-and-back.
- **A4 -- Quarantine section collapse/expand:** The quarantine/blocked-packages row in Source Evaluation safety diagnostics can now be collapsed and expanded via a toggle. Collapsed by default when the quarantine list has been reviewed; the count chip remains visible when collapsed.

## Link Group Management UI

As of v0.7.30:

- Loved Manga screen: new "Manage Cross-Source Links" entry in the action bar (link icon).
- Opens a dedicated screen showing all cross-source link groups. Each group lists linked manga versions with source name and title.
- Users can delete a single link from a group (removes only that link entry) or delete the entire group.
- Deleting a link does NOT cascade to ratings, library entries, or taste rows.
- After deletion, the Loved Manga grouper re-runs reactively (A3 change from v0.7.29 makes this automatic).
- New interactor: `DeleteCrossSourceMangaLink` in `domain/src/main/java/tachiyomi/domain/taste/interactor/`.

## Enrichment Cap Configuration

As of v0.7.31:

- Recommendation Settings (advanced section): configurable enrichment cap for the recommendation quality probe.
- Options: 1, 2, 3, 5, 10, 15, 20 candidates per source run (default 5). Boosted sources always get 2x the cap.
- New preference: `SourcePreferences.recommendationEnrichmentCap()` (Int, default 5).
- Higher values give more accurate quality verdicts but increase network calls per run.
- `BrowsePersonalRecommendationsScreenModel` reads the preference at enrichment invocation time instead of using a hardcoded constant.

## Source Fit Stat Improvements

As of v0.7.32 (extends v0.7.19):

- **D1 -- 30-day rolling window:** `SourceFitStats` gained four new fields: `recentRunCount`, `recentShownCount`, `recentErrorCount`, `windowStartAt`. On each `merge()`, if `now - windowStartAt > 30 days`, the recent window resets before incrementing. The `fitLabel` algorithm prefers recent rates when `recentRunCount >= MIN_RUNS_FOR_LABEL`; falls back to all-time rates otherwise. This means improved sources recover their fit label within a month instead of being dragged down by old data forever.
- **D2 -- Top Picks contribution count:** `SourceFitStats` gained `topPicksContributionCount`. After `combinedAccumulator.rank()` is computed, the set of source IDs that contributed to the final deduplicated Top Picks is passed to `SourceFitStatsStore.mergeRun()` as `topPicksContributors: Set<Long>`. The contribution count is surfaced in the source stats data but not yet displayed in the UI.
- Serialization backward-compatible: new fields default to 0 on parse from pre-v0.7.32 data.

## Best Version Fullscreen Preview Improvements

As of v0.7.33 (extends v0.7.9):

- **I1 -- State restoration:** `fullscreenPageUrl`, `fullscreenPageIndex`, and `fullscreenPageTitle` in `BestVersionCompareScreen` are now `rememberSaveable { mutableStateOf(...) }` primitives instead of plain `remember`. Fullscreen dialog state survives screen rotation and back-stack navigation without collapsing.
- **I2 -- Tap-to-close when not zoomed:** A `detectTapGestures` modifier on the fullscreen image closes the dialog when the current zoom scale is <= 1.0. Does not conflict with pinch-to-zoom (taps at zoom > 1× are ignored by the close handler).
- **I3 -- Fit/Crop toggle:** Each thumbnail in the comparison grid has a small icon button overlaid at the top-right. Tapping toggles between `ContentScale.Crop` (default, fills box by cropping) and `ContentScale.Fit` (letter-boxed, shows full cover). State is local per-thumbnail; no preference stored.

## Source Evaluation UX Polish

As of v0.7.34:

- **C1 -- Error category labels:** Source Evaluation rec-quality ERROR rows now show a compact category badge ("Ext not found", "Install failed", "Search timed out", etc.) derived from `SourceRecommendationFitFailureClassifier.classify(errorMessage)`. The raw error message remains accessible for advanced diagnostics.
- **C2 -- Retry after connectivity loss:** When a Source Evaluation run ends with `ConnectivityLost` status and pending candidates remain, a "Retry (N remaining)" button appears in the summary card. Tapping it invokes the existing continue-batch path without resetting completed results. Shows "Start new run" when the run was already complete when connectivity dropped.
- **C3 -- Profile-changed re-check prompt:** When the taste profile (rated manga count) has grown by >= 5 entries since Source Evaluation was last run, a non-blocking banner prompts "Your taste profile has changed -- re-run evaluation for fresh results." The banner is dismissible. The "Re-check all" button still works independently. Foundation preference `sourceEvaluationLastRunRatingCount` was added in v0.7.31.

## Tests

Documented tests as of KMK-Recs v0.7.34 (accumulative -- new tests from each version added):

- `CombinedPicksAccumulatorTest`: 32 tests
- `RecommendationSourceRunStatusStoreTest`: 10 tests
- `AdjustStatusesForDedupeTest`: 9 tests
- `CrossExtensionMatchSelectionTest`: 10 tests
- `CrossExtensionMatchRouteModeTest`: 8 tests
- `LovedMangaDuplicateGrouperTest`: 38 tests
- `LovedMangaSourceFilterTest`: 9 tests (v0.7.3)
- `LovedMangaSortTest`: 8 tests (v0.7.15)
- `NonInstalledSourceSuggestionScorerTest`: 23 tests
- `RecommendationSourcePreferenceStoreTest`: 13 tests
- `ExplicitSourceClassifierTest`: 20 tests
- `SourceEvaluationInstallerPolicyTest`: 8 tests
- `SourceEvaluationCandidateFilterTest`: 25 tests
- `SourceEvaluationCleanupPolicyTest`: 6 tests
- `SourceEvaluationResultListTest`: 13 tests
- `SourceEvaluationCrashRecoveryPolicyTest`: 6 tests
- `SourceEvaluationUnsafeKeysTest`: 5 tests
- `SourceEvaluationUpdatePolicyTest`: 9 tests (v0.7.4)
- `SourceRecommendationFitEligibilityTest`: 10 tests (v0.7.4)
- `SourceRecommendationFitScorerTest`: 10 tests (v0.7.4)
- `SourceStatusDisplayOrderTest`: 8 tests (v0.6.20)
- `SeenRecommendationMangaStoreTest`: 11 tests (v0.6.20)
- `RecommendationBundleSerializationTest`: 8 tests (v0.7.5)
- `RecommendationBundleValidatorTest`: 11 tests (v0.7.5)
- `RecommendationBundleSourceResolverTest`: 11 tests (v0.7.5)
- `RecommendationBundleDuplicatePolicyTest`: 9 tests (v0.7.5)
- `SourceEvaluationDisplayFilterTest`: 10 tests (7 from v0.7.6 + 3 toggle tests from v0.7.7)
- `SourceRecommendationQualityQueueTest`: 8 tests (v0.7.7)
- `SourceRecommendationQualityExtensionResolverTest`: 10 tests (NEW v0.7.7 follow-up)
- `SameMangaMatchSettingsTest`: 18 tests (NEW v0.7.8)
- `BestVersionPageSamplerTest`: 11 tests (NEW v0.7.8)
- `BestVersionChapterMatcherTest`: 11 tests (NEW v0.7.8)
- `BestVersionSelectionPolicyTest`: 13 tests (NEW v0.7.9)
- `SourceEvaluationConsentPolicyTest`: 3 tests (NEW v0.7.11)
- `SourceRecommendationFitProbeTest`: 17 tests (8 from v0.7.6-v0.7.7; 4 new in v0.7.12; 3 new in v0.7.13: NO_MATCHES on empty search results, weak metadata tracking, enrichment turns no-genre result into scored candidate; 2 new in v0.7.35: IO dispatcher enforcement, NetworkOnMainThreadException classification)
- `SourceRecommendationFitFailureClassifierTest`: 15 tests (NEW v0.7.13)
- `SourceRecommendationQualityDiagnosticsTest`: 10 tests (NEW v0.7.13)
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL (267 actionable tasks as of v0.7.15; 8 new LovedMangaSortTest tests added)
- `:app:assembleDebug`: BUILD SUCCESSFUL

Additional tests added after v0.7.16:

- `GenreFilterMapperTest`: 7 tests (NEW v0.7.20 -- query-time blocked-tag exclusion)
- `KmkMigrationTest`: 9 execution tests + 4 structural tests (NEW v0.7.16); expanded v0.7.38 (migration 56); expanded v0.7.39 (range 46..57, 12 files, migration 57 column test)
- `KmkOcrExclusionTest`: 3 structural tests (NEW v0.7.16 -- OCR never in backup/sync)
- `TasteBackupRoundTripTest`: 5 proto field tests 620--625 (expanded v0.7.16), 6 seen-key round-trip tests added v0.7.28
- `SeenMangaKeyBackupTest`: 6 round-trip tests (NEW v0.7.28)
- `SourceFitStatsDecayTest`: added v0.7.32 (30-day window logic)
- `RecommendationBundleAmbiguityResolverTest`: 6 tests (NEW v0.7.17)
- `RecommendationDiscoveryPlannerTest`: 15 tests (NEW v0.7.39 -- evaluatedPages logic, cap boundary, isExhausted; extended v0.7.40 -- retry/backoff behavior, due/not-due, max-attempts advance)
- `RecommendationSourceSelectorTest`: 11 tests (NEW v0.7.40 -- language filtering, priority ordering, disabled/disliked exclusion, maxSources cap)
- `RecommendationCandidateVisibilityPolicyTest`: 13 tests (NEW v0.7.40 -- each visibility reason, priority order, RatedMangaVisibility modes, fail-open for missing chapter counts)
- `GroupRecommendationSourcePolicyTest`: 6 tests (NEW v0.7.40 -- priority ordering, disabled/disliked exclusion, language preference, maxSources cap, empty result)
- `KmkMigrationTest`: expanded v0.7.40 (range 46..58, 13 files, migration 58 column test for attempt_count/next_retry_at/failure_kind)
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL (267 tasks, v0.7.40)

Updated/added in v0.7.41 (no new migration — `STATUS_EXHAUSTED` already existed):

- `RecommendationRetryClassifierTest`: 12 tests (NEW v0.7.41 -- I/O/timeout retryable; HTTP 4xx/unsupported/unknown permanent; isRetryable gating; backoff cap)
- `RecommendationDiscoveryPlannerTest`: 18 tests (v0.7.41 -- added exhausted-advances, not-due-blocks, due-page-20-retry, no-page-21, null-nextRetryAt-due; max-attempts test now uses `STATUS_EXHAUSTED`)
- `RecommendationCandidateMemoryRankerTest`: 13 tests (v0.7.41 -- +3 min-chapter: below hidden, at/above visible, unknown fail-open)
- `GroupRecommendationLoopPolicyTest`: 19 tests (v0.7.41 -- +5 `VisibleResultAccumulator`: only-visible-counts, continue-across-chunks, dedup-highest-score, insertion-order, empty-not-full)
- `RecommendationCandidateVisibilityPolicyTest`: 16 tests (v0.7.41 -- +2 shared-contract determinism / min-chapter consistency)
- `BrowsePersonalRecommendationsFilterTest`: 7 tests (NEW v0.7.41 known-context follow-up -- `filterVisibleCandidates`: known candidate excluded, all-known page filters to empty, empty-knownIds fail-open, min-chapter/seen/rated/favorite rules unchanged alongside the known filter)
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL (267 tasks, v0.7.41 + known-context follow-up); `assembleDebug`: BUILD SUCCESSFUL

Updated/added in v0.7.42 (migrations 59/60 — `source_evaluation.catalogue_metadata_confidence`, `source_recommendation_fit.evaluation_version`/`.expires_at`):

- `SourceEvaluationScorerTest`: 15 tests (NEW v0.7.42 -- catalogue-only scoring, `PersonalRecommendationScorer` reuse per item, blocked-tag hard exclusion, `catalogueMetadataConfidence` tiers, verdict thresholds without a search signal, `evaluationVersion` always current)
- `SourceRecommendationFitEligibilityTest`: 18 tests (v0.7.42 -- 10 original tests still valid via a `HIGH`-confidence default parameter; +8 confidence-based fail-open/unconditional-exclusion tests)
- `KmkMigrationTest`: expanded v0.7.42 (range 46..60, 15 files, migration 59/60 column tests)
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL (267 tasks, v0.7.42); `assembleDebug`: BUILD SUCCESSFUL

Updated in v0.7.42-fix1 (no new migration):

- `SourceRecommendationQualityQueueTest`: 17 tests (rewritten v0.7.42-fix1 -- 8 original behaviors preserved via a `HIGH`-confidence default; +9 new: confidence fail-open eligibility, unconditional blocked-verdict exclusion, stale-version fit, expired fit, boundary-exact expiry, not-yet-expired fit, current fit)
- `SourceRecommendationQualityDiagnosticsTest`: 16 tests (rewritten v0.7.42-fix1 -- 11 original behaviors preserved via a `HIGH`-confidence default; +5 new: low-confidence eligible row matches the queue, confidently-excluded row matches the queue, stale-version fit not scored, expired fit not scored, EXPLICIT_HEAVY excluded regardless of confidence)
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL (267 tasks, v0.7.42-fix1); `spotlessCheck`: BUILD SUCCESSFUL; `assembleDebug`: BUILD SUCCESSFUL

Updated in v0.7.42-fix2 (no new migration):

- `SourceRecommendationFitDisplayPolicyTest`: 27 tests (NEW v0.7.42-fix2 -- fail-open NOT_CHECKED, unconditional INELIGIBLE regardless of confidence/persisted fit, missing/stale-version/exact-expiry/future-expiry staleness, every current verdict mapping, stale ERROR maps to OUTDATED not ERROR, compatibilityRank ordering, isCurrentOutcome)
- `SourceRecommendationQualityQueueTest`: 20 tests (extended v0.7.42-fix2 -- added `outdatedPromising` bucket; tests that previously asserted stale fit = "missing" now assert "outdated"; +3 new: four-buckets-exhaustive-and-exclusive, needsCheckPromising combines missing+outdated only, outdated-only target excludes current/missing)
- `SourceRecommendationQualityDiagnosticsTest`: 18 tests (extended v0.7.42-fix2 -- routed through the shared display policy, no semantic change; +2 new: notCheckedCount agrees with queue missingCount+outdatedCount, outdated ERROR fit not counted as installLoadIssue/searchError)
- `SourceEvaluationResultListTest`: 21 tests (rewritten v0.7.42-fix2 -- all `sort()` calls updated to the new `(evaluations, fitsByEvalKey, mode, now)` signature; SEARCH_RELIABILITY tests removed; +9 new: best-fit never reads searchReliabilityScore (deterministic name tie instead), best-fit compatibility tie-break only after equal catalogue evidence, best-fit does not let compatibility override unequal catalogue evidence, best-fit treats outdated/not-checked as neutral ties, full seven-bucket FOR_YOU_COMPATIBILITY ordering, current outranks stale compatibility, stale/missing never equal weak, current-outcome score-descending ordering)
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL (267 tasks, v0.7.42-fix2); `spotlessCheck`: BUILD SUCCESSFUL; `assembleDebug`: BUILD SUCCESSFUL

Future changes should document exactly which tests were run.

## Batch L7 direct ScreenModel validation (2026-08-09)

The direct ScreenModel harness gap is reduced but not eliminated. The production constructor now
accepts `autoLoad: Boolean = true`; production behavior is unchanged because the default still
starts the initial load. Tests construct the real `BrowsePersonalRecommendationsScreenModel` with
`autoLoad = false` and directly exercise the real tracker-resolution and visible-exposure paths.

Direct evidence now includes known tracked keys, failed lookup -> `TrackedState.Unknown`, cancellation
propagation, one capture per settled generation, loading/empty/partial suppression, and source-aware
`(sourceId, url)` exposure identity. The full source-search assembly, minimum-chapter display path,
realised-majority output, and lifecycle/recreation behavior remain separately classified as
`PARTIAL_VALIDATION` because no direct harness drives the complete 25-collaborator load graph.

Validation recorded for this batch: JDK 17 `:app:compileDebugKotlin`,
`:app:compileDebugUnitTestKotlin`, focused recommendation/migration suites, full
`:app:testDebugUnitTest` (2,592 tests, 0 failures, 0 errors, 1 skipped from 298 XML files),
`spotlessCheck`, and `git diff --check` all passed. The working tree remains intentionally dirty.

## Jump to last-read chapter wiring (2026-08-09)

**Status: DONE_WITH_VALIDATION_GAP.** The existing target policy and toolbar affordance are now
connected through `MangaScreenModel` into both phone and tablet manga layouts. The action is omitted
when no visible read target exists; clicks cancel and replace prior scroll jobs, re-resolve the
chapter identity, and use live list geometry before animating. Stale targets show the localized
unavailable snackbar without changing read state, filters, sorting, or navigation.

## 2026-08-11 settings automation checkpoint

The schedule lifecycle repair is host-validated with a reader-owned canonical
dialog and shared persistence helper; rendered Android Back/task-stack evidence
remains external. The recommendation-settings contract audit corrected a
stale automation state before source edits. The five existing For You settings
categories and shared section/quick-access components are present. The next
gap is separate enable switches and preserved 1-100 values for Latest
exploration and repeat exposure, with defaults 20% and 14 days. This remains
Latest-only and must not disable personalized matching, normal search, or
source evaluation.

JDK 17 compile, 26 focused policy tests, full unit tests (2,618 tests, 0 failures, 0 errors, 1
skipped), Spotless, and `git diff --check` passed. Rendered Compose, theme, TalkBack, and phone/tablet
visual validation remain external and are not claimed.

## 2026-08-11 R1 settings actions

The five recommendation detail destinations now share one app-bar action
contract. Search opens the existing recommendation-settings search screen from
each detail destination. Source Evaluation keeps its existing gated Home/For
You shortcut and root/tab behavior; the other detail screens now expose the
same search affordance without changing their quick-access row, inline Sources
To Try search, recommendation selection, or stored preferences.

Host validation passed with `:app:compileDebugKotlin`, `:app:spotlessCheck`,
and `git diff --check`. Rendered placement and accessibility traversal remain
for the later evidence batch because no Compose/device harness is available in
the repository.

The Advanced copy correction is also host-validated: Evaluation Mode and the
three Debug Fixture summaries now use short settings-style wording while still
stating their build and network boundaries. R2 is the next source batch.

## 2026-08-11 R2 recommendation controls

R2 is host-validated with a rendered-validation gap. Latest exploration now
has a separate enable switch and a retained 1-100% value (default 20%); repeat
title cooldown has a separate enable switch and a retained 1-100 day value
(default 14). Legacy Latest `0` remains disabled when the new key is absent.
Latest gating is isolated from personalized matching, normal search, source
evaluation, filters, and minimum-chapter checks. Cooldown gating is isolated
to display reranking; local exposure recording, pruning, and clear-history
remain available.

The source uses the existing settings switch style plus a bounded slider and
exact numeric-entry dialog. Focused R2 tests are green: 15 Latest policy, 21
exposure policy, and 14 lane-composition tests, with zero failures/errors.
Kotlin compilation, Spotless, and diff checks passed. Rendered layout,
TalkBack, theme, and tablet/phone interaction remain V1 evidence work.

## 2026-08-11 H1 Action History

Action History is now a normal Advanced settings destination rather than an
Evaluation Mode-only screen. The existing seven-family registry, typed undo,
conflict detection, verified follow-up actions, in-memory bounds, and clear
behavior remain unchanged. Taste and library rows carry their journal-owned
manga ID and open that manga when tapped; opening a row never performs Undo.
Rows without a safe target remain truthful view-only or follow-up entries.

Host validation passed: `:app:compileDebugKotlin` exit 0, focused Action History
tests 21/0/0/0 across registry and follow-up XML reports, `:app:spotlessCheck`
exit 0, and `git diff --check` exit 0. Rendered row taps, Back restoration,
TalkBack, theme, and phone/tablet layout remain V1 evidence work. The
developer diagnostic trace is intentionally not claimed: H2A must instrument
real operation boundaries with bounded sanitization before adding that view.

## 2026-08-11 H2A0 structural discovery

The source audit found no separate Developer Options preference, warning, or
public-build acknowledgement boundary. The always-visible Advanced Developer
Tools group is not an equivalent opt-in; only debug fixture rows use
`BuildConfig.DEBUG`. H2A is therefore frozen and H2A0 must add the explicit,
default-off, accessible gate before any diagnostic trace or technical detail
view is implemented. No trace was derived from Action History rows or raw logs.

## 2026-08-11 H2A0 gate result

H2A0 is `DONE_WITH_VALIDATION_GAP`. Advanced settings now has a private,
default-off Developer Options preference with an explicit confirmation warning.
The gate is separate from Evaluation Mode, remains outside ordinary backup and
restore, and leaves `BuildConfig.DEBUG` fixture gating unchanged. The focused
policy XML reports 8 tests with zero failures, errors, or skips; Kotlin compile,
Spotless, and diff checks passed. Rendered Compose/accessibility/theme and
device evidence remain V1 gaps. H2A is the next source batch: audit actual
operation producers before adding a bounded, sanitized, session-only trace.

## 2026-08-11 H2A trace result

H2A is `DONE_WITH_VALIDATION_GAP`. Real journal commit boundaries and Action
History undo/follow-up paths now feed a bounded in-memory diagnostic trace. The
trace records only sanitized labels, phases, outcomes, counts, timestamps, and
write-commit state; it has no persistence, export, raw log, SQL, URL, account,
title, clipboard, or exception payload. Developer Options gates the detail
surface and Evaluation Mode suppresses it. Five focused trace tests passed,
along with Kotlin compilation, Spotless, and diff checks. Rendered detail UI,
process restart, accessibility/theme, device evidence, and complete direct
caller coverage remain V1 gaps. B1 Best Version clarity is next.

## B1 Best Version clarity result (2026-08-11)

B1 is `DONE_WITH_VALIDATION_GAP`. Best Version keeps the per-source result cap
separate from normal global search, uses source-aware candidate identity,
distinguishes unavailable chapters from failed or timed-out previews, and
supports candidate-only retry without retrying skipped unavailable chapters.
Chapter matching now rejects non-finite metadata and deterministically prefers
an exact or near-exact match within the existing tolerance. The owned same-
manga search dispatcher is released when the Best Version screen is disposed.

Focused validation reports 39 tests with zero failures/errors/skips; Kotlin
compile, Spotless, and `git diff --check` pass. Direct searcher-harness and
rendered/device/accessibility/theme validation remain open evidence gaps.

## 2026-08-12 C1 chapter-list validation

C1 is `DONE_WITH_VALIDATION_GAP`. The chapter-list action remains `Jump to last
read` only. Target resolution stays tied to the displayed filtered and sorted
list, stale or unsafe indices show the unavailable state, and both portrait
and tablet paths center only middle targets with valid content on both sides.
First, last, latest, and short-list targets remain safely clamped.

The focused policy suite reports 29 tests with zero skipped, failures, or
errors. Kotlin compilation, Spotless, and `git diff --check` pass. Rendered
positioning, touch target, accessibility, theme/localization, snackbar, and
device evidence remain V1 gaps. E1 cross-feature review is next.

## E1 cross-feature review result - 2026-08-12

The host review is complete with no new source contradiction across
recommendation settings, Latest discovery, exposure ordering, Source
Evaluation, Action History, Best Version, reader schedule, or chapter-list
navigation. The stale lifecycle and adversarial metadata was reconciled with
the recorded implementation checkpoints.

The focused regression run covered 22 suites and 296 tests with zero failures,
errors, or skips. Kotlin compilation and Spotless returned exit 0, and
`git diff --check` returned exit 0. The source status count remains 66, above
the preserved baseline of 24. Rendered phone/tablet, accessibility, theme,
lifecycle, and authorized-device evidence remains V1 work. Missing Google
client configuration, remote image failures, extension linkage failures, and
older dependency-registration failures remain separate runtime conditions.

**Next batch:** `V1_HOST_DEVICE_EVIDENCE_CLOSURE`.

## V2 Action History and Not Interested audit - 2026-08-12

Source inspection confirmed a new reopened gap: `EvaluationModeJournalRecorder`
is intentionally a no-op when Evaluation Mode is disabled, while the app now
labels the destination Action history. Normal-user Not Interested writes and
removals therefore do not appear there. The complete finding, privacy boundary,
undo contract, and implementation batches are recorded in
`private/docs/audits-and-reports/kmk-action-history-not-interested-audit-2026-08-12.md`.

This was the audit-only predecessor to HN1. HN1 is now complete with a host
validation gap. The six taste and Not Interested journal builders in
`EvaluationModeJournalRecorder.kt` now record bounded, local-only inverse
entries for ordinary users as well as Evaluation Mode. The write ordering,
conflict protection, developer-only diagnostic trace, and separate Not
Interested storage remain unchanged.

The focused retry verified 25 tests across the changed journal, taste, and undo
classes with zero failures, errors, or skips. Kotlin compilation, unit-test
compilation, Spotless, and `git diff --check` passed. The first bounded test
attempt timed out and is retained as a tooling exception; the retry completed
successfully. **Next batch:** `V2_ACTION_HISTORY_NOT_INTERESTED_IMPLEMENTATION_HN2`.
Rendered UI, lifecycle, and device validation remain deferred until the coding
batches are complete.

## V2 HN2 result - 2026-08-12

HN2 closed the two remaining Not Interested user-action bypasses. Cross-source
Mark Seen and Source Evaluation Clear Seen now use the shared build-before-
write/commit-after-success Action History boundary. Store-level removal filters
malformed, absent, and duplicate keys and snapshots an existing rating so undo
does not erase unrelated taste state.

Fresh focused XML reports 31 tests with zero failures, errors, or skips.
Compilation, unit-test compilation, Spotless, and `git diff --check` passed.
The adversarial review found and repaired a first-draft null-rating snapshot.
**Next batch:** `V2_ACTION_HISTORY_NOT_INTERESTED_IMPLEMENTATION_HN3` for safe
manga-context navigation and Back behavior. Rendered, lifecycle, and
authorized-device evidence remain deferred until the coding batches close.

## V2 HN3 result - 2026-08-12

HN3 completed host implementation with a validation gap. Action History uses
only a positive journal-owned `contextMangaId` for manga navigation; missing,
zero, and negative IDs remain non-clickable, and display text, source labels,
and URLs are never used as fallback identity. Navigation is scoped to the
summary/time column, while Undo and follow-up controls remain separate. The
existing app-bar Back route is preserved.

Fresh JUnit XML reports 18 tests with zero failures, errors, or skips. The
first compile exposed an internal Compose `weight` import and that defect was
repaired; separate compile, unit-test compile, and Spotless checks passed.
The wrapper timeout after XML generation is retained as a tooling exception.
Next batch: HN4 single/bulk undo and conflict/retry behavior. ADB remains
deferred until the coding batches close.

## V2 HN5 result - 2026-08-12

HN5 closed the residual Action History gate found after the HN4 source-wide
review. A successful Best Version migration now records the generic
`MIGRATION_COMPLETED` event and its correlated private recovery receipt when
Evaluation Mode is off as well as on. Partial failure, not-started,
missing-target, and cancellation paths remain unrecorded, and the existing
non-undoable migration/follow-up contract is unchanged.

Fresh XML for the expanded affected selection: 247 tests, zero failures,
errors, or skips across 23 suites. Kotlin compilation, unit-test compilation,
Spotless, and diff hygiene passed. No ADB or database operation was used.
HN5 is host-validated with the remaining rendered/lifecycle/device gap.
Next coding/evidence batch: V1 host/device evidence closure.

## L1 schedule saved-state reconciliation - 2026-08-12

The schedule saved-state crash repair is host-validated. `ReaderScheduleDialog`
uses a primitive/list saver and malformed-value filtering rather than making
the schedule domain object parcelable. The focused saved-state and schedule
suite reports 65 tests with zero failures, errors, or skips; Kotlin compile,
unit-test compile, Spotless, and diff checks passed. The remaining rendered,
stopped-activity, accessibility/theme, and reader return-stack checks remain
explicit validation gaps. The next source batch is the reader Configure
schedule route and Back contract. Tablet evidence stays deferred until every
source coding batch closes.

## V2 HN4 result - 2026-08-12

HN4 is host-validated with a rendered/device validation gap. Action History
capture is no longer suppressed by Evaluation Mode for safe ordinary-user
writers, including Not Interested, taste, chapters, schedules, library and
favorite changes, groups, covers, Source Evaluation, download deletion,
backup/restore, tracker receipts, extension operations, migration, source
enablement, source preferences, and source-quality marks. Redaction and
Developer Options diagnostic visibility remain intentionally gated.

Fresh XML reports 133 tests with zero failures, errors, or skips across 15
suites. Kotlin compilation, unit-test compilation, Spotless, and
`git diff --check` passed after one import-order repair and one stale test
expectation repair. The next coding batch is HN5; rendered Action History,
accessibility/theme, lifecycle/restart, and authorized-device evidence remain
deferred, and no ADB was used.

## L2 reader schedule route and presentation - 2026-08-12

The reader timer keeps schedule configuration in the current `ReaderActivity`
and opens the shared dialog locally, so Back returns to the reader. The dialog
now has bounded scrolling for long lists, clear mode and time-window grouping,
full-width mode rows, separated edit/delete actions, and an explicit Add
window action. Existing settings navigation and Save-only persistence remain
unchanged. The schedule host suite is 65 tests with zero failures, errors, or
skips; compile, Spotless, and diff checks passed. L2 is host-validated with
rendered, accessibility, theme, stopped-activity, and device gaps. Next source
batch: R3 Source Evaluation and Latest visibility. No ADB yet.

## R3 Source Evaluation Latest visibility - 2026-08-12

Source Evaluation already fetched and stored Latest catalogue counts, but the
expanded evidence details did not show the Popular/Latest split. R3 now
surfaces those existing counts through the pure evidence model and a localized
detail line. No extra network request or recommendation/filter change was
made. The focused source/evaluation/Latest selection reports 106 green tests;
compile, Spotless, and diff checks pass. R3 is host-validated with rendered,
accessibility, theme, and device gaps. R4 is next; ADB remains deferred.
## 2026-08-12 Source-First Repair Restart

The durable workflow has been reopened for a new source-only repair graph.
N0 is the current audit batch; N1-N6 cover Best Version, recommendation
settings, Source Evaluation, preference and Action History parity, background
recovery, and final adversarial source reconciliation. No ADB, APK install,
screenshot, XML pull, or device claim is permitted until N0-N6 close under the
source-repair plan. V1 is deferred evidence work.

N0 is now complete as a source audit. Best Version's per-source cap and
preview timeout/retry path already exist but need N1 discoverability and
progression verification. Recommendation settings already have section
headers, shared actions, numeric entry, switches, and stored values; N2 owns
remaining placement or control gaps. Source Evaluation Latest visibility is
present from R3; N3 owns shared host/search behavior. Not Interested storage,
markers, transitions, and journal builders exist, but a dedicated collection
route is not confirmed; N4 owns that parity work. N5 must separate the known
schedule saved-state crash from any changed background lifecycle defect. The
next source batch is N1. No ADB or device validation is authorized yet.

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

N2 is `DONE / HOST_VALIDATED`. The shared recommendation-settings component
now adds the standard divider before each section header and a localized
accessibility description for the horizontal quick-access destination row.
Four detail settings screens retain that shared row; Source Evaluation keeps
its separate host/search action surface. No preference, matching, persistence,
or navigation contract changed. The focused N2 suite reported 130 tests with
zero failures, errors, or skips; compile, unit-test compile, Spotless, and diff
checks passed. Dirty/untracked work remains preserved at 109 paths.

The aggregate control fallback still reports one contained stale Action History
route expectation and is assigned to N4. No ADB or device work is authorized.
N3 is next for Source Evaluation Latest, host, and search integration.

## 2026-08-12 N3 result and N4 next

N3 is `DONE / HOST_VALIDATED`. Source Evaluation's existing Latest fetch,
filtering, scoring, persistence, Popular/Latest evidence split, and separate
For You search-compatibility probe remain unchanged. The four recommendation
detail screens now share the same top-right For You action as Source Evaluation,
using one root-and-tab navigation helper while preserving Source Evaluation's
hidden-tab condition. Search, quick access, anchors, Back behavior, and
preference consumers remain unchanged.

The focused route contract reported 2 passing tests; the full Source Evaluation
package reported 484 passing tests across 49 XML reports. Evaluation Mode,
recommendation-engine, original-flow, compile, Spotless, and diff checks passed.
The first retry timeout is recorded as a tooling limit. Dirty/untracked work
remains at 109 paths. N4 is next for preference parity and Action History; ADB
remains deferred.

## 2026-08-12 N4 result and N5 next

N4 is `DONE_WITH_VALIDATION_GAP / HOST_VALIDATED`. Not Interested now matches
the rated collection structure with visible selection mode, selected-count
AppBar, select-all, row selection, and confirmed bulk removal. Removal remains
keyed by `(sourceId, url)`, journal-backed, write-before-commit, and compatible
with existing recommendation, backup, and undo behavior. The normal Action
History route audit now matches `ActionHistoryScreen()` and the separate
Developer Options diagnostic gate; Evaluation Mode remains excluded from that
diagnostic surface.

Focused tests, Kotlin compilation, Spotless, control audits, and diff hygiene
passed. Rendered UI, process recreation, accessibility, theme, and tablet proof
remain V1 work. The old schedule parcel crash is already covered by earlier
source repair; missing Google client configuration is an external blocker; and
Reader initial-load failures are assigned to N5 for current-build/source
correlation. No ADB or device operation is authorized yet.

## 2026-08-12 N5 input and source plan

The attached report separates the old schedule parcel crash (already repaired in source), missing
Google Drive client configuration (external), and repeated Reader initial-load failures. N5 owns a
deterministic stale-chapter fallback and privacy-safe Reader failure classification, plus a bounded
background-sync unavailable path when the client asset is absent. No device validation is permitted
until N6 closes the source coding gate.

## 2026-08-12 N5 result

N5 is host-validated. Reader startup now prefers the requested chapter, falls back deterministically
when a restored chapter id is stale, preserves an explicit empty-list failure, and logs only bounded
failure categories. Missing Google client configuration is handled as a background-unavailable
state without repeated asset exceptions; actual Google OAuth remains externally blocked without the
client asset. Ten focused tests, compilation, Spotless, control checks, and diff hygiene passed.
N6 is next; rendered/process-recreation/tablet proof remains deferred and no ADB was used.

## 2026-08-12 N6 active source gate

N6 is running the cross-feature source reconciliation for N1-N5 plus the earlier reader schedule,
Source Evaluation, and Action History work. The gate checks original Komikku compatibility, KMK
integration, privacy, localization, theme, accessibility semantics, process-safe state, backup and
export boundaries, cancellation, and reversible cleanup. No ADB or tablet evidence is allowed until
this source gate closes.

N6 is now host-validated and the source coding gate is closed for V1. The focused six-class rerun
passed 35 tests, and the full suite passed 2,683 tests with zero failures, zero errors, and one
skipped. Compilation, unit-test compilation, Spotless, Action History, control, privacy, original
flow, recommendation, backup, SAF, and fixture-route host audits passed. Tablet/rendered,
process-recreation, theme/accessibility, and real Google client validation remain explicitly open
for the next evidence batch.

The first full source run found six stale tests that still expected successful normal-user actions
to be suppressed when Evaluation Mode was off. The production contract already records bounded
local Action History outside Evaluation Mode, while failed, cancelled, unverified, and missing-target
operations remain non-recording. N6 is repairing the test contract before the final source gate.

## 2026-08-12 V1 preflight

N6 is green and the source coding gate is closed. V1 preflight is currently blocked externally:
`adb devices -l` returned no attached devices, so the authorized tablet R5GL201CAQX was unavailable.
No emulator, install, screenshot, XML pull, log capture, database access, or cleanup was performed.
The route and fixture registries still govern which evidence may be captured when the tablet returns.

V1 is paused after two no-progress preflights because `adb devices -l` remains empty. Resume only
with the authorized tablet identity check; no emulator or source-only rendered claim is acceptable.

## 2026-08-12 V1 For You evidence checkpoint

The authorized tablet became available and passed identity checks. One private For You bundle was
captured in Evaluation Mode with a stable loaded recommendation surface, generic source labels,
matched-taste rows, artwork, and one honest normal source-failure message. The device was restored to
Evaluation Mode off through the app. The bundle is `DONE_WITH_VALIDATION_GAP` for private evidence;
route promotion, crop review, and human publication approval remain separate.

## 2026-08-12 OAuth O0 source ownership

The separate Google Drive source program is active after explicit user resume.
O0 is complete: foreground sign-in, callback activity, token preferences,
background worker, sync/restore, build variants, scopes, and tests were traced.
The confirmed O1 ownership is the direct `client_secrets.json` asset
dependency and missing per-variant configuration boundary; O2 owns the callback
decision. No client JSON, device operation, or real-account proof was used.
O1 is next, and host-first validation remains mandatory.

## 2026-08-12 OAuth O3 current state

O3 is source-complete with a documented external validation gap. The existing
Drive service now uses a shared lifecycle policy for missing configuration,
missing refresh credentials, ready state, revoked authorization, transient
failure, malformed response, and cancellation. Revocation clears stored
credentials; cancellation propagates; other refresh failures fail closed. The
download path no longer exposes provider exception text. Focused tests, debug
compile, Spotless, privacy checks, and diff hygiene passed. O4 is next for
deterministic Drive and background-worker host fixtures; real account, network,
process-death, and rendered evidence remain deferred.

## 2026-08-12 Batch 1 host foundation

Batch 1 is `DONE_WITH_VALIDATION_GAP`. The test-only local validation package
now has resettable synthetic app state and a loopback source/preview fixture
server for every required catalogue surface plus HTTP failure, malformed
response, timeout, cancellation, and cleanup behavior. Six focused tests pass.
No Android/Compose renderer is configured in this repository, so Context,
saved-state navigation, and rendered UI remain deferred under `TOOLING_LIMIT`.
Batch 2 is selected for shared native settings controls; the tooling gap is a
final validation requirement, not a device authorization.

## 2026-08-13 deferred structural follow-up intake

The next documentation-first correction plan explicitly includes the
reader-to-canonical-schedule route and disabled-scheduler prerequisite, Best
Version exact-chapter preview and per-source cap behavior, More-style section
dividers across recommendation settings, shared control contracts, original
Komikku precedent review, cross-feature exception handling, and a full normal
Action History audit. These are not source-complete claims. The controller is
paused, and local validation seams must precede any future device evidence.

## 2026-08-13 Batch 0 source-ownership result

Batch 0 is complete without production-code edits. The reader schedule has
separate Reader and Reader Settings owners; Best Version applies an existing
per-source cap but separates candidate discovery from chapter preview; shared
recommendation settings use common primitives unevenly; and Action History's
seven-family registry still needs writer-after-success and ordinary-user
coverage. Batch 1 is next for local validation seams. The controller remains
device-disabled.

## 2026-08-12 OAuth O4 final checkpoint

O4 is closed with a host-validation gap. The refresh coordinator and bounded
sync-worker result contract are implemented and host-tested. O5 is the next
source batch for adversarial release reconciliation; no ADB, account, client
JSON, or publication operation is authorized.

## 2026-08-12 OAuth O4 final checkpoint

O4 is closed with a host-validation gap. The refresh coordinator and bounded
sync-worker result contract are implemented and host-tested. O5 is the next
source batch for adversarial release reconciliation; no ADB, account, client
JSON, or publication operation is authorized.

## 2026-08-12 OAuth O4 final checkpoint

O4 is closed with a host-validation gap. The refresh coordinator and bounded
sync-worker result contract are implemented and host-tested. O5 is the next
source batch for adversarial release reconciliation; no ADB, account, client
JSON, or publication operation is authorized.

## 2026-08-12 OAuth O4 close

O4 is source-complete with an external validation gap. The existing Google
credential refresh path now runs through a host-testable coordinator covering
success, missing credentials, revocation, transient I/O, malformed responses,
blank tokens, and cancellation. The sync worker uses a bounded retry/failure
policy with WorkManager backoff, while failed Drive refresh still stops before
pull/restore. Focused and combined tests, debug compile, Spotless, privacy, and
diff hygiene passed. O5 is next for adversarial release reconciliation; real
provider, network, process-death, WorkManager runtime, and foreground error
copy remain separate follow-ups.

## 2026-08-12 OAuth O3 close

O3 is source-complete with a documented external validation gap. The existing
Drive service now uses a shared lifecycle policy for missing configuration,
missing refresh credentials, ready state, revoked authorization, transient
failure, malformed response, and cancellation. Revocation clears stored
credentials; cancellation propagates; other refresh failures fail closed. The
download path no longer exposes provider exception text. Focused tests, debug
compile, Spotless, privacy checks, and diff hygiene passed. O4 is next for
deterministic Drive and background-worker host fixtures; real account, network,
process-death, and rendered evidence remain deferred.

## 2026-08-12 OAuth O1 close

OAuth O1 is complete. Google Drive configuration now has a guarded, testable
per-variant boundary with non-secret Gradle metadata, centralized client JSON
loading, explicit invalid-configuration states, and a safe missing-config
foreground result. Host compile, focused tests, formatting, source-boundary
inspection, and diff hygiene passed. O2 owns the Android callback and
authorization lifecycle; no device or real-account validation is implied.

## 2026-08-12 OAuth O2 close

The existing Komikku Google Drive browser authorization and custom callback
remain the compatibility baseline. O2 now checks the exact callback route and
classifies success, provider denial, malformed results, unsupported routes, and
no-result cancellation before token exchange. Token-request failures return
through the existing failure path. Host tests, debug compile, Spotless, route
inventory, privacy scan, and diff hygiene passed. The next source batch is O3
for token expiry, refresh, background sync, sign-out, and lifecycle recovery;
real-account and rendered callback evidence remain deferred.

## 2026-08-12 OAuth O0 source ownership

The separate Google Drive source program is active after explicit user resume.
O0 is complete: foreground sign-in, callback activity, token preferences,
background worker, sync/restore, build variants, scopes, and tests were traced.
The confirmed O1 ownership is the direct `client_secrets.json` asset
dependency and missing per-variant configuration boundary; O2 owns the callback
decision. No client JSON, device operation, or real-account proof was used.
O1 is next, and host-first validation remains mandatory.

## 2026-08-12 OAuth O3 current state

O3 is source-complete with a documented external validation gap. The existing
Drive service now uses a shared lifecycle policy for missing configuration,
missing refresh credentials, ready state, revoked authorization, transient
failure, malformed response, and cancellation. Revocation clears stored
credentials; cancellation propagates; other refresh failures fail closed. The
download path no longer exposes provider exception text. Focused tests, debug
compile, Spotless, privacy checks, and diff hygiene passed. O4 is next for
deterministic Drive and background-worker host fixtures; real account, network,
process-death, and rendered evidence remain deferred.
## 2026-08-13 Batch 2 close

Batch 2 source work is `DONE_WITH_VALIDATION_GAP`. The shared native numeric
control now covers the recommendation result budget, Best Version comparison
cap/sample size, group preview budget, enrichment cap, and minimum chapter
count with policy-owned bounds, exact entry, malformed read/write resolution,
and the existing switch/value preservation behavior. Focused policy and
settings-structure tests (54 tests), debug Kotlin compilation, Spotless, and
diff hygiene passed. A local Compose/Android recreation/rendering harness is
still missing and remains a named `TOOLING_LIMIT`; no ADB or device evidence
was used. B3 is next for Best Version/source behavior and Latest/source
integration.

## 2026-08-13 B3 close

B3 is source-complete with a named host/rendering gap. Best Version initial and
retry previews now share a terminal-state policy, so stale chapter/source
state cannot leave a candidate spinning forever. Latest majority enforcement
also considers sparse non-Latest results. The focused Best Version/Latest,
recommendation model, source-evaluation, exposure, tracker, and memory suites
passed with zero failures, errors, or skips; debug compilation, Spotless, and
diff hygiene passed. The combined broad test command hit the tooling timeout,
but split reruns passed. Direct asynchronous source/network preview and
rendered Android validation remain unverified without a local harness; ADB and
device evidence remain disabled. B4 is next for preference parity and normal
Action History behavior.

## 2026-08-13 B4 close

B4 is source-complete with a named host/rendering gap. The four preference
states remain one shared family, and the Not Interested collection now sends
loaded manga IDs through the existing journal boundary when a taste row is
absent. That keeps ordinary Action History entries navigable back to the
manga. Normal history remains available without Evaluation Mode; developer
trace details stay opt-in, session-only, and redacted.

The final B4 command passed 212 tests across 23 JUnit files with zero failures,
errors, or skips. Debug Kotlin compilation, Spotless, and diff hygiene passed;
the source dirty count is 157. Rendered UI, Android recreation, real-source
network behavior, and device evidence remain unverified or prohibited. B5 is
next for reader schedule, chapter navigation, and lifecycle repair.

## 2026-08-13 B5 close

B5 is source-complete with a named host/rendering gap. Reader Configure schedule
now routes through the existing MainActivity action to the canonical Reader
settings screen, highlights the Configure schedule row, preserves the disabled
enable switch as the prerequisite, and returns to the reader through normal
Back behavior. The duplicate reader-local schedule editor is gone.

Reader schedule saveable state now uses a primitive serialized String with a
defensive legacy-list decoder, preventing ReaderScheduleWindow from entering
Android's stopped-activity parcel payload. Existing schedule persistence/undo,
reader lifecycle hooks, and Jump-to-last-read policy were retained and
source-audited. Focused schedule tests passed 68/0/0/0, the chapter/reader
journal suite passed 36/0/0/0, and the final route contract passed 3/0/0/0.
Compile, Spotless, diff hygiene, and the targeted privacy scan passed. B6 is
next for adversarial integration and source coding-completion reconciliation;
rendered Android, process-death, real-source, and tablet evidence remain
unverified or prohibited.

## 2026-08-13 B6 adversarial integration result

B6 is `DONE_WITH_VALIDATION_GAP` for source and host completion. The active
correction-plan matrix was reconciled against the current owners for shared
settings controls, Best Version/source preview, Latest/source evaluation,
four-state preferences, ordinary Action History, reader schedule,
Jump-to-last-read, and reader/background recovery. No unexplained mismatch
remained and no speculative source change was made.

The focused cross-feature matrix passed 676 tests; the full unit suite passed
2,725 tests with zero failures/errors and one skipped test. Debug Kotlin
compilation, Spotless, changed-source privacy scanning, and diff hygiene
passed. Rendered Compose, Android saved-state/process death, actual
long-background execution, direct real-source/network preview, and tablet
evidence remain unverified or prohibited. B7 is disabled by the user.
