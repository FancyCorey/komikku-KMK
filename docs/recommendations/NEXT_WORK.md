# KMK Personal Recommendations Next Work

Date: 2026-07-09 (updated: 2026-07-12 -- v0.8.1-fix2 shipped, version visibility + sync validation)

Status: v0.7 feature line is closed as of v0.7.45; v0.7.46/v0.7.47 were follow-up correctness/hygiene
passes; v0.8.0 was the first private v0.8 feature build (Rated Manga management); v0.8.1-fix1 was a
private corrective/polish follow-up fixing gaps found in v0.8.0/v0.7.47; v0.8.1-fix2 is a further
private corrective follow-up fixing a Loved Manga crash introduced by v0.8.1-fix1's group-primary
interactors (missing Injekt registration), a version-visibility gap, and a sync-validation gap. The
entire v0.8.x line is private-only â€” no public release. Open items only. All polish phases A through J are shipped. v0.7.35 (Rated Manga entry points + group-seeded recommendations) shipped. v0.7.36 (Rated Manga UI parity, Library shortcuts, crash fix, discoverability) shipped. v0.7.37 (group-seeded recommendation bounded runtime, source-aware dedup, cross-source rating exclusivity) shipped. v0.7.38 (For You candidate discovery memory, additional page discovery, group seed enrichment from all linked versions, GroupSeedRecommendationScorer, Reset discovery history) shipped. v0.7.39 (For You rolling discovery progress table, empty/filtered/error pages no longer retried, cumulative 20-page cap, progress cleared on history reset) shipped. v0.7.40 (same-refresh discovery merge fix, typed discovery retry policy with exponential backoff, shared RecommendationSourceSelector adopted in group flow, shared RecommendationCandidateVisibilityPolicy adopted in both flows) shipped. v0.7.41 (discovery policy corrections: single visibility contract across live/cache/memory/group with min-chapter parity, group budget counts only visible results, truthful STATUS_EXHAUSTED retry state + due-page-20/no-page-21 planner fix, conservative unknown-error classification, cancellation never recorded) shipped. v0.7.41 known-context follow-up (extra-page discovery now uses the real hide-known context, matching live/cache/memory/group) shipped. v0.7.42 (Source Evidence Redesign: catalogue-only Source Evaluation scoring, shared PersonalRecommendationScorer taste matching, catalogue metadata confidence, fail-open rec-fit eligibility, staleness parity, honest "For You search" UI labeling) shipped. v0.7.42-fix1 (corrective follow-up: manual queue/diagnostics now share the same eligibility/staleness contract as automatic evaluation; misleading `search 0%` row subtitle replaced with catalogue metadata confidence) shipped. v0.7.42-fix2 (second corrective follow-up: one shared `SourceRecommendationFitDisplayPolicy` now drives queue buckets, diagnostics, row labels, sorting, and targeted rechecks; retired the unusable Search Reliability sort in favor of For You Compatibility; added a targeted Recheck Outdated action; stale fits can no longer display as current) shipped. v0.7.43 (background For You search compatibility job separate from Source Evaluation; Loved/Liked group recommendations replaced with the row-based manga-detail Recommendations pipeline seeded by every confirmed linked version; "Seen" renamed "Not interested" with a mild negative scoring signal in For You, storage unchanged) shipped. v0.7.44 (fixed the 3 pre-existing recommendation test failures for good, plus 2 latent ones; new shared strict-to-lenient query-attempt policy used by both group rows and For You; group recommendations now use the whole linked group's tags/titles, honor source-selection and candidate-visibility policy, and filter out near-zero-relevance candidates; Source Evaluation/Settings phone UI density pass) shipped. Do not implement any item without explicit user approval and a focused implementation plan.

---

## v0.8.9 — OFFICIAL-STYLE WHAT'S NEW STRUCTURE, RECOMMENDATION SETTINGS SEARCH

**Status:** Both features implemented and tested (2026-07-16); no physical device QA performed. See
`KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_IMPLEMENTATION.md` for full detail.

**Done:**
- What's New: confirmed the existing renderer (official `MarkdownRender`/`GFMFlavourDescriptor`)
  already fully supports the official New/Improve/Fix structure — no renderer changes needed. Added
  the v0.8.9 entry in that structure. Every one of the 76 pre-existing historical entries remains
  individually intact.
- Recommendation Settings search: new parallel, pure, ranked search index (7 category-level entries
  covering For You, Source Priority, Taste/Tags, Source Evaluation, Sources To Try, Installer/
  Background, Diagnostics) plus a search screen mirroring the official Settings search UI shape,
  reachable from a new search action in the Recommendation Settings index top bar.

**Not done — follow-up work:**
- Retroactive New/Improve/Fix reformatting of the 76 historical What's New entries — left in their
  original format, a large content-only rewrite judged disproportionate to this pass.
- Per-control search entries with stable in-screen anchors (currently category-level only — every
  result still navigates to the correct screen, just not a specific row within it).
- Robolectric/Compose-UI test infrastructure — still absent; What's New rendering and search UI
  interaction are verified by code inspection and pure-logic tests only.
- Device QA (What's New phone/tablet layout and TalkBack, search phone/tablet/dark-light/TalkBack/
  large-font/rotation) — none of this was executed, no physical device was available.

---

## v0.8.8 — SCHEDULE ENFORCEMENT, RATING PROMPT, SETTINGS INDEX, EVALUATION RECONCILIATION

**Status:** All four items implemented and tested, including the Phase B full settings split
(2026-07-16, gap-closing pass); no physical device QA performed. See
`KMK_RECS_V0_8_7_FIX1_AND_V0_8_8_IMPLEMENTATION.md` for full detail. Ships v0.8.7-fix1 and v0.8.8
together as one release.

**Done:**
- Reading-schedule enforcement actually exists now (it previously did not — restriction was toast-only).
  Session-bound `ReaderScheduleEntitlement`, real gates at every chapter-load entry point, full-screen
  block overlay.
- Chapter-completion rating prompt (latest chapter only, deduplicated, reuses existing exclusive
  rating + cross-extension matching, schedule-aware).
- Recommendation Settings fully split into per-category screens (gap-closing pass): all seven index
  rows now route to distinct destinations — Evaluation and Background/Installer both to
  `SourceEvaluationScreen` (installer settings have no content elsewhere — a confirmed structural
  fact), the other five each to a new dedicated screen extracted verbatim (zero behavior change) from
  the former single `RecommendationsSettingsScreen`, which has been deleted. Scroll-to-section is no
  longer relevant now that each category is its own bounded screen.
- Outdated-evaluation reassessment: traced and fixed the real root cause (display-label vs.
  work-queue eligibility mismatch, not a literal count/work query pair) with a reconciliation policy
  and an explanatory UI banner; 250-synthetic-source test suite proves count/work agreement and
  correct multi-batch continuation.

**Not done — follow-up work:**
- Per-extension exclusion-reason breakdown in the outdated-reconciliation UI (currently aggregate
  counts only).
- Robolectric/Compose-UI test infrastructure — `ReaderViewModel`, `SourceEvaluationScreenModel`, and
  the new Compose screens/dialogs remain untestable end-to-end in this repo's current test setup.
- Device QA (schedule blocking on-device, rating prompt at real chapter boundaries, settings
  navigation on phone/tablet across all seven new/updated screens, multi-batch Source Evaluation
  continuation) — none of this was executed, no physical device was available.

---

## v0.8.7 — READING SCHEDULE FIX COMPLETE; RATED UI/SETTINGS REFINEMENT PARTIAL

**Status:** Reading Schedule dialog root-cause fix fully implemented and tested (2026-07-15). Rated
UI/Recommendation Settings refinement partially implemented — see
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

**Not done — follow-up work:**
- Expand/collapse for Recommendation Settings sections — attempted and explicitly declined: the
  Source Priority section's drag-and-drop reorderable list made this unsafe to verify without a
  physical device. Controls in all 9 sections still always render.
- Summaries for the remaining 3 Recommendation Settings sections (Source Evaluation, Management,
  Experimental).
- Undo for Merge Selected Into Group, Remove From Group, and Ungroup (needs a link-group-graph
  snapshot/restore, not just a rating/flag value).
- Source Priority per-row quality-dislike/explicit-block/reset/details actions — this is new
  functionality (not currently wired per-row at all), not a reorganization of existing actions.
- A deeper line-by-line audit of cross-extension matching and the Best Version workflow screens
  specifically (not yet done in either pass).
- Device QA (phone/tablet layout for both the schedule dialog and the rated/settings screens, 12h/24h
  display, TalkBack) — none of this was executed, no physical device was available.

---

## v0.8.6 — CODE COMPLETE, MANUAL QA PENDING (group recommendation search performance and loading)

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

**Not done — follow-up work:**
- Add a real refresh/reload entry point to `RecommendsScreenModel` (there isn't one today) so
  `GenerationGuard` is exercised by a genuine second generation in production, plus an end-to-end
  test for it.
- Build a fake-dependency test harness for `RecommendsScreenModel` itself so cache-wiring, per-row
  error/timeout rendering, and multi-row state merging can be verified end-to-end (currently only the
  extracted coordinator/guard logic is directly tested; the screen model's own integration is verified
  by code inspection and the passing regression suite, not a dedicated test).
- Device QA (phone+tablet layout, budgets 5/10/20/30 visually, heavy-tag groups, repeated refresh,
  leaving mid-load, network loss/recovery, source expansion, confirming global search stays uncapped,
  confirming the cache actually avoids a redundant network call on-device) — none of this was
  executed, no physical device was available.

---

## v0.8.2-v0.8.5 — SHIPPED (For You results budget, Recommendation Settings reorganization, active-reading timer, optional reading schedule)

**Status:** COMPLETE (2026-07-15). Internal/private handoff build; no public release prepared.
Implemented as one coordinated session per `KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md`
and its four phase plans:

1. **v0.8.2**: `ForYouResultBudgetPolicy` + `SourcePreferences.recommendationResultBudget()` — a
   user-configurable 5/10/15/20/30 (default 10) visible-card budget per ordinary For You source row,
   included in the cache fingerprint, boosted-row floor preserved at 20. No change to source count,
   priority, enrichment, Source Evaluation, Top Picks, or query-attempt limits.
2. **v0.8.3**: Recommendation Settings reordered into For You behavior / Source priority / Source
   evaluation / Source management / Discovery-cache-management sections. Most of this phase's other
   requirements (shared rated-collection UI, non-pinned quarantine controls, For You shortcuts) were
   already satisfied by prior sessions.
3. **v0.8.4**: New `eu.kanade.tachiyomi.ui.reader.timer` package — pure `ReaderTimerReducer` state
   machine (IDLE/RUNNING/PAUSED/CHAPTER_GRACE/EXTRA_CHAPTER_GRACE/EXPIRED), monotonic-clock based,
   `SavedStateHandle`-persisted, lifecycle-bound to `ReaderViewModel`/`ReaderActivity`. Reader
   bottom-bar "Reading timer" icon and dialog (presets/custom duration, warnings, grace options).
4. **v0.8.5**: New `eu.kanade.tachiyomi.ui.reader.schedule` package — pure `ReaderScheduleResolver`
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
manual on-device QA was possible in this environment for reader-lifecycle-dependent behavior —
recorded as unavailable, not claimed as verified.

See `docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md`
for the full file list, test results, and deviations.

---

## v0.8.1-fix4 â€” SHIPPED (Final cleanup + source/library-quality dislike)

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

## v0.8.1-fix2 â€” SHIPPED (Version visibility + sync validation, private corrective follow-up)

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
   showing at once (Compose recomposes reliably once `showChangelog` flips false) â€” this was
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

## v0.8.1-fix1 â€” SHIPPED (Rated Manga + Source Evaluation polish, private corrective follow-up)

**Status:** COMPLETE (2026-07-12). Private-only build; no public release prepared. Implemented per
`KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_PLAN.md`, fixing five gaps found
after v0.8.0/v0.7.47:

1. `LinkedVersionListScreen`'s remove-from-group action now requires confirmation (previously
   called `removeFromGroup()` directly with no dialog).
2. `manga_cross_source_group_primary` is now backed up/restored/synced (proto 627,
   `BackupCrossSourceGroupPrimary`, `TasteRestorer.restoreCrossSourceGroupPrimaries()`,
   `SyncService.mergeCrossSourceGroupPrimariesPure()`) â€” previously durable user data with zero
   backup/sync coverage.
3. The app-bar "Select" action in Loved/Liked/Disliked now enters selection mode without
   auto-selecting the first visible item (`RatedSelectionReducer.enterEmpty()`) â€” previously it
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

## v0.8.0 â€” SHIPPED (Rated Manga bulk selection + group actions, private feature)

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

## v0.7.47 â€” SHIPPED (Source Evaluation tag enrichment and scoring fix)

**Status:** COMPLETE (2026-07-12). Fixes the root-cause evidence-pipeline defect found in
`KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md`: catalogue-fit scoring never enriched
Popular/Latest samples missing genre tags, so sources were often being scored on "does the list
page expose tags?" instead of actual taste fit. Implemented exactly per
`KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md`: bounded `getMangaDetails()`
catalogue enrichment (`SourceEvaluationCatalogueEnricher`, cap 12, 10s timeout, cancellation-safe,
no chapter/page fetches, evidence-only â€” never written to the app manga table); split
positive/negative/blocked/adult/metadata evidence counters (migration 61, 9 new additive columns);
a ratio-based fit-score formula and revised verdict order that routes metadata-sparse evidence to
`NEEDS_MANUAL_REVIEW` instead of a confident `WEAK`, and blocks noisy/adult/blocked-tag-heavy
sources from reaching `STRONG_FIT`; `SourceEvaluationKeys.CURRENT_VERSION` bumped `2 -> 3` so every
existing row is stale; a new `SourceEvaluationDisplayPolicy` for stale-row labeling/ranking; and a
new `STALE_EVALUATION` result on `SourceRecommendationFitEligibility.check()` so a stale catalogue
row can no longer feed the search-compatibility queue as if current. Catalogue fit and For You
search compatibility remain fully separate signals â€” no change to `SourceRecommendationFitProbe` /
`SourceRecommendationFit`. No source-specific hacks were added. See
`docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_IMPLEMENTATION.md` for
the full file list, migration number, and test results.

---

## v0.7.46 â€” SHIPPED (public polish closeout)

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

## v0.7.45 â€” SHIPPED (final v0.7 closure + public release readiness pass)

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

## v0.7.44 â€” SHIPPED (shared query policy, group recs parity, test-suite cleanup, phone UI density)

**Status:** COMPLETE and verified (2026-07-12). Implemented in checkpointed phases (A: test fixes,
B: shared query-attempt policy, C: apply to group recs, D: apply widened chain to For You, E: group
source-selection + visibility policy, F: background-job test coverage, G: phone UI density, H: docs),
each compiled/tested before the next began. `:app:testDebugUnitTest` (949 tests, 0 failures) â†’
`spotlessCheck` â†’ `assembleDebug` all ran on JDK 17.0.19 and passed. APK built and copied to
`Komikku-v1.13.6-kmk.7.44-debug.apk`. `KmkRecsReleaseNotes.VERSION_CODE` = `746`, `VERSION_NAME` =
`"KMK-Recs v0.7.44"`.

**What changed:** Fixed the pre-existing `RecommendationCandidateVisibilityPolicyTest` Injekt failures
(and 2 more latent instances of the same bug) via a shared `TestInjektSupport` test helper â€” no
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

**Explicitly out of scope per instruction:** no MarkSeen/Not Interested behavior changes â€” that was
confirmed already implemented in v0.7.43 and intentionally untouched here.

**Deviations (see the implementation report for full detail):** For You's shared-policy adoption was
implemented by deepening the existing `RecommendationQueryPlanner` rather than swapping in
`RecommendationQueryAttemptPolicy` directly (plan explicitly allowed either shape); the phone UI pass
covered the explicitly-named highest-risk spots rather than an exhaustive review of every button row in
`SourceEvaluationScreen.kt`; no screenshot/device testing was available, verification was by Compose
layout code inspection.

Full detail: `KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_IMPLEMENTATION.md`.

---

## v0.7.43 â€” SHIPPED (background compatibility job, group recs row-based rewrite, Seen -> Not Interested)

**Status:** COMPLETE and verified (2026-07-12). Implemented in three checkpointed phases (A: background
job, B: group recommendations rewrite, C: Seen reframe), each compiled and spotless-checked before the
next began, per the plan's non-negotiable rule against building the final APK before every phase is
done. `spotlessCheck` â†’ focused tests â†’ `:app:testDebugUnitTest` (928 tests; 3 pre-existing/unrelated
failures, see below; 1 skipped; rest pass) â†’ `assembleDebug` all ran on JDK 17.0.19 and passed. APK built
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

## v0.7.42-fix2 â€” SHIPPED (second corrective follow-up to v0.7.42, not a new feature phase)

**Status:** COMPLETE and verified (2026-07-12). A code review after fix1 found the remaining
presentation/action/sort paths (row labels, the Best Fit and Search Reliability sort comparators, and
the action buttons) still used ad-hoc, disagreeing logic even though fix1 correctly unified automatic
evaluation, the queue, diagnostics, and fit staleness. `spotlessApply` â†’ `spotlessCheck` â†’ targeted
policy/queue/diagnostics/sort tests â†’ `:app:testDebugUnitTest` (267 tasks, all pass) â†’ `assembleDebug`
all ran on JDK 17.0.19 and passed. APK built and copied to `Komikku-v1.13.6-kmk.7.42.2-debug.apk`.
`KmkRecsReleaseNotes.VERSION_CODE` = `744`, `VERSION_NAME` = `"KMK-Recs v0.7.42-fix2"`.

**What changed:** New pure `SourceRecommendationFitDisplayPolicy` resolves every source to exactly one
truthful `CompatibilityDisplayState` (INELIGIBLE/NOT_CHECKED/OUTDATED/GREAT/GOOD/MIXED/WEAK/NO_MATCHES/
ERROR) by reusing `SourceRecommendationFitEligibility.isProbeEligible`/`isFitCurrent` â€” no new score,
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

**No open follow-up remains from v0.7.42/fix1** â€” every finding named in the fix2 plan was confirmed
and fixed; see the implementation report for the "already fixed" check performed before editing.

Full detail: `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_IMPLEMENTATION.md`.

---

## v0.7.42-fix1 â€” SHIPPED (corrective follow-up to v0.7.42, not a new feature phase)

**Status:** COMPLETE and verified (2026-07-12). Codex review after the v0.7.42 implementation found that
`SourceRecommendationQualityQueue`, `SourceRecommendationQualityDiagnostics`, and the Source Evaluation
row subtitle still used pre-v0.7.42 assumptions even though the core scorer/schema/tests were
build-healthy. This release fixes those downstream consumers. `spotlessApply` â†’ `spotlessCheck` â†’
targeted queue/diagnostics tests â†’ `:app:testDebugUnitTest` (267 tasks, all pass) â†’ `assembleDebug` all
ran on JDK 17.0.19 and passed. APK built and copied to `Komikku-v1.13.6-kmk.7.42.1-debug.apk`.
`KmkRecsReleaseNotes.VERSION_CODE` = `743`, `VERSION_NAME` = `"KMK-Recs v0.7.42-fix1"`.

**What changed:** `SourceRecommendationFitEligibility` gained `isProbeEligible(evaluation)` and
`isFitCurrent(fit, now)` â€” the single shared contract for probe eligibility and fit staleness.
`SourceRecommendationQualityQueue.compute()` (the manual "Check search compatibility"/"Re-check all"
actions) now uses this contract instead of a hardcoded `{STRONG_FIT, WORTH_TRYING}` set and a
mere fit-presence check, so a WEAK/NEUTRAL catalogue verdict with LOW/UNKNOWN metadata confidence is
correctly queued, and a missing/older-version/expired fit is correctly treated as needing
re-evaluation. `SourceRecommendationQualityDiagnostics.compute()` uses the same contract, so its counts
can never disagree with the queue; a stale fit no longer contributes to the Good/Weak/Error/No-results
buckets. The Source Evaluation row subtitle no longer shows a fabricated `search N%` figure
(`SourceEvaluation.searchReliabilityScore` is intentionally always `0.0` as of v0.7.42) â€” it shows
catalogue metadata confidence instead, via new KMR strings. Stale v0.7.41 documentation wording that
said v0.7.42 was still "planning-only" was corrected to note it was implemented later, without
rewriting history.

**Files:** `SourceRecommendationFitEligibility.kt`, `SourceRecommendationQualityQueue.kt`,
`SourceRecommendationQualityDiagnostics.kt`, `SourceEvaluationScreen.kt`, `i18n-kmk` strings,
`KmkRecsReleaseNotes.kt`, `KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md`, plus
`SourceRecommendationQualityQueueTest.kt` (rewritten, 17 tests) and
`SourceRecommendationQualityDiagnosticsTest.kt` (rewritten, 16 tests). No database migration was added.

**No open follow-up remains from the v0.7.42 base release** â€” the "`SourceRecommendationQualityQueue
.compute()` not updated" limitation noted in the original v0.7.42 shipping note is resolved by this fix.

Full detail: `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md`.

---

## v0.7.42 Source Evidence Redesign â€” SHIPPED

**Status:** COMPLETE and verified (2026-07-12) per the approved plan's decisions D1â€“D4. Gradle was initially blocked by a transient harness safety-classifier outage (11+ retries rejected, including a bare `--version` check, while read-only shell commands worked throughout); a later retry in the same session succeeded. `spotlessApply` â†’ `spotlessCheck` â†’ `:app:testDebugUnitTest` (267 tasks, all pass) â†’ `assembleDebug` all ran on JDK 17.0.19 and passed. APK built and copied to `Komikku-v1.13.6-kmk.7.42-debug.apk`. `KmkRecsReleaseNotes.VERSION_CODE` = `742`.

**What changed:** Source Evaluation no longer pools Popular/Latest catalogue samples with a tag-search probe into one score. `SourceEvaluationScorer` now scores catalogue fit only, reusing the real `PersonalRecommendationScorer` per sampled item (previously an ad-hoc, divergent approximation). The scorer's own search probe was removed from `SourceEvaluationRunner`; search compatibility is measured solely by the pre-existing `SourceRecommendationFitProbe`. A new `catalogueMetadataConfidence` signal on `SourceEvaluation` prevents sparse Popular/Latest metadata from being misread as "bad fit," and `SourceRecommendationFitEligibility` now fails open toward probing when that confidence is low/unknown. `SourceEvaluationKeys.CURRENT_VERSION` bumped 1â†’2; `SourceRecommendationFit` gained its own independent version/expiry columns (migration 60) for staleness parity with `source_evaluation` (migration 59 adds the confidence column). UI strings that said "Recommendations: Good/Great" were relabeled "For You search: Good/Great" (resource IDs unchanged, English text only).

**Files:** `SourceEvaluationScorer.kt`, `SourceEvaluationRunner.kt`, `SourceRecommendationFitEligibility.kt`, `SourceEvaluation.kt`, `SourceRecommendationFit.kt`, `SourceEvaluationRepositoryImpl.kt`, `SourceRecommendationFitRepositoryImpl.kt`, `source_evaluation.sq`, `source_recommendation_fit.sq`, migrations `59.sqm`/`60.sqm`, `i18n-kmk` strings, `KmkRecsReleaseNotes.kt`, plus `SourceEvaluationScorerTest.kt` (new, 15 tests), `SourceRecommendationFitEligibilityTest.kt` (extended, 18 tests), `KmkMigrationTest.kt` (range 46â€“60, 17 tests).

**Deliberately deferred at the time â€” resolved by v0.7.42-fix1 above:** `SourceRecommendationQualityQueue.compute()` was not updated to use the new eligibility logic or fit staleness â€” it still partitioned "promising" sources via a hardcoded `{STRONG_FIT, WORTH_TRYING}` set instead of `SourceRecommendationFitEligibility.check()`, and didn't yet treat a stale `SourceRecommendationFit` as "missing." This mirrored the same "infrastructure exists, not wired into a UI consumer" state `SourceEvaluation.isStale()` already had, kept scoped out at the time to keep the v0.7.42 diff reviewable.

Full detail: `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`.

---

## v0.7.41 Known-Context Gap â€” RESOLVED

**Status:** COMPLETE (2026-07-11). Originally confirmed by Codex review after Claude's v0.7.41 implementation; unit tests passed with the repo-local JDK 17, but one policy-context gap remained. That gap is now fixed and verified.

**Problem (as found):** `BrowsePersonalRecommendationsScreenModel.discoverAdditionalPage()` called `RecommendationCandidateVisibilityPolicy.evaluate(...)` with `knownIds = emptySet()`. The caller's final merge still filtered additional-page candidates, so visible leakage was unlikely, but extra-page progress and candidate memory could treat known-only pages as successful and store candidates that should have been filtered before scoring/progress/memory.

**Fix shipped:** `discoverAdditionalPage()` now takes a `hideKnownManga: Boolean` parameter and batch-loads known IDs for localized extra-page candidates (one `GetKnownRecommendationMangaIds.await(...)` call, fail-open on lookup failure) before calling the shared policy â€” the same known-id context used by live page-one, cache, memory-merge, and group recommendations. `localizedCount`, `filteredCount`, `scoredCount`, `visibleCount`, `progressStatus`, the returned recommendations, and the candidates passed to `memoryStore.upsertBatch(...)` are all derived from the post-filter list, so a known-only additional page is now recorded as empty/filtered, never `STATUS_SUCCESS`, and no known candidates are written to candidate memory. The identical page-one/extra-page filter block was extracted into a new pure `filterVisibleCandidates(...)` top-level function for testability and de-duplication; the redundant post-call known-id re-lookup at the `searchSource()` call site was removed (extra-page candidates are already known-filtered by the time they are returned).

**Files:**
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/test/java/exh/recs/BrowsePersonalRecommendationsFilterTest.kt` (NEW â€” 7 tests)

**Verification:** JDK confirmed Temurin 17.0.19+10. `spotlessApply` / `spotlessCheck` / `:app:testDebugUnitTest` (267 tasks, all pass) / `assembleDebug` all BUILD SUCCESSFUL. See `KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md` Â§ Follow-up for full detail.

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
source fit badge (`"Great fit Â· 5"`) rather than a new row, only when the count is greater than zero.
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

