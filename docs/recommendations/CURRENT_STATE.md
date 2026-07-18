# KMK Personal Recommendations Current State

Date: 2026-07-09 (updated: 2026-07-17 -- v0.8.10 corrective/completion release complete: Phases
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

Status: Updated through KMK-Recs v0.8.10 (see "v0.8.10 phase map" below). Previously: v0.8.9
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

## Seen / Already Read Manga Marker

As of v0.6.20:

- Manga detail page rating dropdown includes "Mark as seen" / "Clear seen" item (below the existing "Other versions" divider).
- Also includes "Seen other versions" item (always visible when the callback is set, as of v0.7.1) -- opens `CrossExtensionMatchScreen` with `CrossExtensionMatchMode.MarkSeen`.
- `MarkSeen` mode writes to the `seenRecommendationMangaKeys` preference via `SeenRecommendationMangaStore`. It does not write taste rows.
- Seen entries are stored as semicolon-separated `"sourceId|url"` strings. Pipe-in-URL is handled correctly (only first `|` is the separator).
- Seen manga are **always** filtered from For You regardless of the "Hide known manga" setting.
- `seenMangaCount` is included in the recommendation `profileFingerprint()`, so the cache is invalidated whenever the seen set changes.
- `isSeen: Boolean` state in `MangaScreenModel.State.Success` tracks seen status for the current manga. Updated live by `markSeen()` / `clearSeen()`.
- Backup/restore for seen entries is implemented as of v0.7.28 via `BackupSeenMangaKey` at proto field 626. Restore is additive (union with existing seen set). See "Seen Manga Backup" section below.

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

