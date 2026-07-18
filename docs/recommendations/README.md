# KMK Personal Recommendations Documentation Index

Date: 2026-06-22

Status: central navigation file for the Komikku personal recommendation fork work.

## Purpose

This folder is the stable starting point for Codex, Claude Code, or a human reviewer before recommendation work. It now keeps only current/active documents at the top level. Superseded plans and implementation reports are archived under `archive/`.

## Required Reading Order

1. `CURRENT_STATE.md`
   - Current implemented behavior.
   - Known implementation facts.
   - The distinction between Browse > For You and manga-detail recommendations.

2. `NEXT_WORK.md`
   - Known bugs.
   - Approved or discussed future work.
   - Items that need a separate implementation plan before coding.

3. `DOCUMENTATION_RULES.md`
   - Required documentation discipline for every future implementation pass.

4. `SOURCE_LIST_DIAGNOSIS.md`
   - Source Priority vs For You source mismatch context.
   - Local Source and combined-row behavior context.

5. Root audit/version files:
   - `../../RECOMMENDATION_IMPLEMENTATION_AUDIT.md`
   - `../../RECOMMENDATION_VERSIONING.md`

## Active Planning Files

| File | Purpose | Status |
| --- | --- | --- |
| `../community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_PLAN.md` | v0.8.10-fix3 active plan: structural source-runtime isolation for extension-originated failures. Creates one shared source-operation boundary so a broken installed extension dependency, such as Asura Scans missing `okhttp3.zstd.Zstd`, cannot crash Browse, For You, Source Evaluation, global search, reader/download, library update, or other unrelated flows. | Planned — next implementation target after the narrow fix2 patch |
| `../community/KMK_RECS_V0_8_10_FIX2_ASURA_EXTENSION_LINKAGE_CRASH_PLAN.md` | v0.8.10-fix2 plan: narrowly-scoped crash fix isolating a broken/incompletely-packaged extension's `LinkageError` (confirmed: Asura Scans `NoClassDefFoundError: okhttp3.zstd.Zstd`) as a per-source recoverable failure instead of crashing the whole For You/group-recommendation load. | Implemented — see `../community/KMK_RECS_V0_8_10_FIX2_IMPLEMENTATION.md` |
| `../community/KMK_RECS_V0_8_10_FIX1_PHASE_0_DI_REGISTRATION_CRASH_FIX_PLAN.md` | v0.8.10-fix1 crash-fix plan: missing `UpdateMangaFromRemote` Injekt registration causing an application-wide crash on any Browse/manga-update bulk-selection flow. | Implemented — see `../community/KMK_RECS_V0_8_10_FIX1_CRASH_FIX_IMPLEMENTATION.md` |
| `../community/KMK_RECS_V0_8_10_FIX1_FULL_KOMIKKU_CONFORMANCE_AND_STABILITY_IMPLEMENTATION_PLAN.md` | v0.8.10-fix1 plan: full Komikku UI/architecture conformance pass across every KMK-added surface (Recommendation Settings, Source Evaluation, For You/Browse, rated collections, reader/timer/schedule/OCR), lossless historical What's New conversion (supersedes the v0.8.10 Phase G "keep-as-is" decision), and mandatory application-wide crash investigation. Gated, multi-phase (0-8). | On hold pending user confirmation of the fix1/fix2 crash repairs — see `CURRENT_STATE.md`'s v0.8.10-fix1 status |
| `../community/KMK_RECS_V0_8_10_FIX1_RELEASE_ASSURANCE_ADDENDUM.md` | v0.8.10-fix1 addendum: dependency/license audit, reproducible build documentation, performance checks, upgrade/rollback tests, deterministic edge-case fixtures, privacy-safe crash-log export decision. | In progress alongside the main v0.8.10-fix1 plan |
| `../community/KMK_RECS_V0_8_10_FIX1_FINAL_RELEASE_SAFETY_ADDENDUM.md` | v0.8.10-fix1 addendum: final release-safety gates before handoff. | In progress alongside the main v0.8.10-fix1 plan |
| `KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md` | Master plan for v0.8.2-v0.8.5: authoritative execution order and phase gates for the coordinated For You results budget, Recommendation Settings reorganization, active-reading timer, and optional reading schedule work. | Implemented in v0.8.5 |
| `KMK_RECS_V0_8_2_FOR_YOU_DISPLAY_IMPLEMENTATION_PLAN.md` | v0.8.2 plan: user-configurable For You results-per-source budget (5/10/15/20/30), cache fingerprint inclusion, boosted-row floor. | Implemented in v0.8.2 |
| `KMK_RECS_V0_8_3_RECOMMENDATION_UI_REFINEMENT_IMPLEMENTATION_PLAN.md` | v0.8.3 plan: Recommendation Settings section reorganization and For You/Source Evaluation/rated-collection UI refinement without scoring changes. | Implemented in v0.8.3 |
| `KMK_RECS_V0_8_4_READING_TIMER_IMPLEMENTATION_PLAN.md` | v0.8.4 plan: pure reducer-based active-reading timer integrated at the ReaderActivity/ReaderViewModel lifecycle boundary. | Implemented in v0.8.4 |
| `KMK_RECS_V0_8_5_READING_SCHEDULE_IMPLEMENTATION_PLAN.md` | v0.8.5 plan: optional local reading schedule (day/time windows), reader-only, reusing the timer's grace mechanism. | Implemented in v0.8.5 |
| `KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_PLAN.md` | v0.8.1-fix4 final corrective plan: clean app-facing private/public wording, normalize Source Evaluation XML comments/docs, add stale-queue completion feedback, and add a separate source/catalogue-quality dislike path for poor, lewd, or explicit-heavy sources beyond For You-only dislike. | Implemented in v0.8.1-fix4 |
| `KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.md` | v0.8.1-fix3 corrective plan for Source Evaluation continuation: after a working 100-source reassessment batch, stale/outdated rows must remain actionable and continue into the next batch instead of being hidden as already evaluated with `0 sources`; also guards against leaking internal private/public build wording into app UI. | Implemented in v0.8.1-fix3 |
| `KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_PLAN.md` | v0.8.1-fix2 private corrective plan for the v0.8.1-fix1 Loved Manga Injekt crash, KMK-Recs What's New/version metadata visibility, group-primary sync validation parity with restore, documentation/versioning cleanup, and named private APK handoff. | Implemented in v0.8.1-fix2 |
| `KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_PLAN.md` | v0.8.1-fix1 corrective plan for linked-version remove confirmation, group-primary backup/sync, empty selection-mode entry, Set Primary access wording, and Source Evaluation evidence explainability. | Implemented in v0.8.1-fix1 |
| `KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_PLAN.md` | v0.8.0 private plan for Loved/Liked/Disliked manga bulk selection, explicit item recommendation menu entries, linked-version list, manual grouping actions, and user-selected primary versions. | Implemented in v0.8.0 |
| `KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md` | Exact implementation plan for fixing Source Evaluation false weak/false strong results via bounded `getMangaDetails()` catalogue enrichment, v3 source-evaluation rows, split evidence counters, stale-row handling, and tests. | Implemented in v0.7.47 |
| `KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_PLAN.md` | Corrective plan for v0.7.44: create one shared strict-to-lenient recommendation query policy for For You and Loved/Liked grouped recommendations, repair group source/visibility parity, fix active visibility-policy tests, and add focused Source Recommendation Quality coverage. | Implemented in v0.7.44 |
| `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_PLAN.md` | Corrective follow-up for v0.7.42: make manual For You search compatibility queue and diagnostics use `SourceRecommendationFitEligibility`, treat stale `SourceRecommendationFit` rows as missing, remove misleading `search 0%` catalogue subtitle, and reconcile stale v0.7.41/v0.7.42 docs. | Implemented in v0.7.42-fix1 |
| `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_PLAN.md` | Corrective follow-up for v0.7.42-fix1: replace the retired Search Reliability sort, centralize compatibility display states, expose a bounded Recheck outdated action, and make row labels/sorting/actions truthfully distinguish current, stale, missing, and ineligible compatibility evidence. | Implemented in v0.7.42-fix2 |
| `KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_PLAN.md` | Coordinated plan for v0.7.43: make For You search compatibility checks run as a real background job, replace Loved/Liked group recommendation grid with the normal per-provider/per-extension Recommendations page seeded by all linked versions, reframe Seen as Not Interested/Skip if it becomes a mild negative signal, and clean up duplicate/stale recommendation code/docs. | Implemented in v0.7.43 |
| `KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_IMPLEMENTATION.md` | Implementation report for v0.7.43: `SourceRecommendationQualityJob` background job, `RecommendsScreen.Args.CrossSourceGroupSeed` row-based group recommendations, Not Interested mild-negative scoring, deletions, deviations. | Shipped |
| `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_PLAN.md` | Audit + design for splitting Source Evaluation's pooled catalogue+search score into distinct Catalogue Fit / For You Search Compatibility layers, a shared taste-affinity contract, metadata confidence, and staleness/versioning parity for `source_recommendation_fit`. Grounded in a code audit of `SourceEvaluationScorer`, `SourceEvaluationRunner`, `SourceRecommendationFitProbe`, and both `.sq` schemas. Decisions D1-D4 approved 2026-07-12. | Implemented in v0.7.42 |
| `KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_AND_SOURCE_EVIDENCE_ROADMAP_PLAN.md` | Corrective release for v0.7.40 gaps: single visibility contract across live/cache/memory/group, group budget counts only visible results, truthful STATUS_EXHAUSTED retry + due-page-20/no-page-21 planner, conservative unknown-error classification. Section 7 records the v0.7.42 source-evidence redesign as planning scope only. | Sections 2-4 implemented in v0.7.41; section 7 handed off to `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_PLAN.md` |
| `KMK_RECS_V0_7_40_FOR_YOU_AND_RATED_GROUP_POLICY_FOUNDATION_IMPLEMENTATION_PLAN.md` | Foundation: same-refresh merge fix, typed discovery retry policy, shared RecommendationSourceSelector + RecommendationCandidateVisibilityPolicy adopted in For You and group flows | Implemented in v0.7.40 (gaps corrected in v0.7.41) |
| `KMK_RECS_V0_7_39_FOR_YOU_ROLLING_DISCOVERY_AND_RATED_GROUP_RECOMMENDATIONS_PLAN.md` | For You rolling discovery progress (migration 57, empty/filtered/error pages tracked), rated group recommendations (Track B already done) | Implemented in v0.7.39 |
| `KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_AND_GROUP_SEED_ENRICHMENT_PLAN.md` | For You candidate discovery memory + group-seeded recommendation enrichment from all linked versions | Implemented in v0.7.38 |
| `KMK_RECS_V0_7_37_GROUP_SEEDED_RECOMMENDATIONS_LOADING_FIX_PLAN.md` | Follow-up to v0.7.36: bound group-seeded recommendation runtime, add localization/search timeouts, emit partial/empty results instead of indefinite loading, and keep MangaScreen-safe localized results | Implemented in v0.7.37 |
| `KMK_RECS_V0_7_36_RATED_MANGA_UI_PARITY_AND_GROUP_RECS_CRASH_FIX_PLAN.md` | Follow-up to v0.7.35: refactor Loved/Liked/Disliked manga into one reusable rated-manga collection UI, add Library toolbar shortcuts, make Recommendations from this discoverable, and fix group-seeded recommendation result crashes by localizing results before opening MangaScreen | Implemented in v0.7.36 |
| `KMK_RECS_V0_7_10_RECOMMENDATION_QUALITY_STRONG_FIT_ERROR_FIX_PLAN.md` | Fix Source Evaluation Recommendation Quality checks for Strong Fit/Worth Trying sources so non-installed promising sources resolve, temporarily install, probe, and clean up instead of all becoming errors | Implemented in v0.7.10 |
| `KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_PLAN.md` | Generalize Loved Manga into a Rated Manga view for Loved/Liked/Disliked manga and add recommendations from existing cross-source groups without duplicating Seen/title-specific dislike | Implemented in v0.7.35 |
| `KMK_RECS_V0_7_9_BEST_VERSION_DIALOG_AND_PREVIEW_POLISH_PLAN.md` | Fix Best Version migration dialog Cancel dismissal, add fullscreen zoomable page preview, and verify same-manga preselect behavior | Implemented in v0.7.9 |
| `KMK_RECS_V0_7_3_LOVED_MANGA_INSTALLED_SOURCE_FILTER_PLAN.md` | Hide Loved Manga entries from sources/extensions that are no longer installed, without deleting taste/link data | Implemented in v0.7.3 |
| `KMK_RECS_V0_7_2_LOVED_MANGA_SMART_GROUPING_AND_LINK_USAGE_PLAN.md` | Improve Loved Manga grouping using confirmed cross-source links first and conservative metadata fallback | Implemented in v0.7.2 |
| `KMK_RECS_V0_7_1_CROSS_EXTENSION_MATCH_STATE_AND_SEEN_MENU_FIX_PLAN.md` | Fix confirmed MarkSeen/Favorite matching screen state-save crash and Seen other versions menu visibility | Implemented in v0.7.1 |
| `KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md` | Master roadmap for remaining deferred recommendation features, grouped by implementation phase | Active master plan; do not implement without phase approval |
| `KMK_RECS_STAGED_SETTINGS_AND_MATCHING_IMPROVEMENTS_PLAN.md` | Deferred staged work: cross-extension alternate-title matching and Loved Manga view | Partially implemented; remaining deferred |
| `KMK_RECS_V0_5_0_CROSS_EXTENSION_MATCHING_PLAN.md` | Deferred favorite mode and cross-source link groups for the cross-extension matching workflow | Partially implemented; rating workflow shipped |

## Active Research References

| File | Purpose | Status |
| --- | --- | --- |
| `KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md` | Complete real-device/source-code audit of Source Evaluation tag retrieval, catalogue-fit scoring, stale DB rows, recommendation-quality evidence, and next structural fixes. | Current reference |
| `CROSS_EXTENSION_IDENTITY_FEASIBILITY_RESEARCH.md` | Feasibility of same-manga identity across extensions, rating/favorite sync, and staged rollout options | Current reference |
| `INSTALLED_SOURCE_FIT_FEASIBILITY_RESEARCH.md` | Feasibility of learning which installed sources are useful/reliable recommendation sources | Current reference |

## Latest Implementation Reports Kept At Top Level

Note: `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md` (outside this folder)
covers the 9-phase reconciliation of the whole KMK fork against the official Komikku v1.14.0 tag
(app `versionName` 1.13.6 → 1.14.0, `versionCode` 88 → 89). It is not a KMK-Recs feature release —
no new recommendation capability was added, so the KMK-Recs feature label below stays at v0.8.9 —
but it verified that this recommendation system's v0.8.9 state (What's New renderer, all 76+
historical entries, Recommendation Settings search) survived the reconciliation intact. See that
report for the full file-by-file account.

| File | Purpose | Status |
| --- | --- | --- |
| `KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_IMPLEMENTATION.md` | v0.8.9 implementation report: confirmed the existing What's New renderer already supports the official Komikku New/Improve/Fix changelog structure (no renderer changes needed), added the v0.8.9 entry in that structure while preserving all 76 historical entries individually; built a new parallel, ranked, read-only Recommendation Settings search index + screen (7 category-level destinations) since Recommendation Settings screens aren't built on the official Preference/SearchableSettings DSL the main Settings search reuses. 25 new tests. No migration. | Implemented and verified — see report for documented scope decisions and manual-QA list |
| `KMK_RECS_V0_8_7_FIX1_AND_V0_8_8_IMPLEMENTATION.md` | v0.8.8 implementation report (ships v0.8.7-fix1 + v0.8.8 together): reading-schedule enforcement fix (found restriction was toast-only with no actual enforcement; built real session-bound entitlement + navigation gates + block overlay), chapter-completion rating prompt (latest-chapter-only, dedup guard, reuses exclusive rating + cross-extension matching), Recommendation Settings index screen (Evaluation routed directly; other categories unchanged, split/scroll-to-section declined), outdated-evaluation reassessment root-cause fix (display-vs-work eligibility reconciliation, 250-synthetic-source test suite). 36 new tests across three test files. No migration. | Implemented and verified — see report for documented scope decisions and manual-QA list |
| `KMK_RECS_V0_8_7_RATED_UI_AND_RECOMMENDATION_SETTINGS_REFINEMENT_IMPLEMENTATION.md` | v0.8.7 implementation report covering both plans, across two passes: Reading Schedule dialog root-cause fix (unsafe Activity cast, forced 24h format, no whole-day support, inconsistent Cancel/dismiss, no real Edit — all fixed, 11 new tests) and Rated UI/Recommendation Settings refinement (Select-all-in-group bottom-bar action, 6-of-9 settings section summaries, Undo for 2 of 5 bulk actions — 15 new tests). Expand/collapse for settings sections explicitly attempted and declined as too risky without device verification. No migration (schedule window serialization is backward compatible). | Reading Schedule fix: complete. Rated UI/Settings: partial — see report for itemized gaps |
| `KMK_RECS_V0_8_6_SEARCH_PERFORMANCE_AND_LOADING_IMPLEMENTATION.md` | v0.8.6 implementation report: `GroupPreviewBudgetPolicy`/`RecommendationLoadContext`/`GroupPreviewCache` (wired into the load path)/`GroupPreviewLoadCoordinator`/`GenerationGuard`, bounded (4-permit) group-recommendation concurrency, 20s per-row timeout, `CancellationException` propagation fix, `screenModelScope` lifecycle fix, diagnostics logging, "Initial results per extension" setting. 24 new tests across two passes. No migration. | Implemented, unit-tested; no physical-device QA — see report |
| `KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md` | v0.8.2-v0.8.5 combined implementation report: `ForYouResultBudgetPolicy`, Recommendation Settings reorganization, `eu.kanade.tachiyomi.ui.reader.timer` (pure reducer-based active-reading timer), `eu.kanade.tachiyomi.ui.reader.schedule` (pure reading-schedule resolver reusing the timer's grace mechanism). 78 new tests. No migration. | Implemented and verified |
| `KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_IMPLEMENTATION.md` | v0.8.1-fix4 implementation report: new `SourceQualityMarkPolicy` and two-axis source feedback model (`likedSourceQualityKeys`/`dislikedSourceQualityKeys`/`explicitSourceQualityKeys`, separate from recommendation-behavior dislike), filtering in Sources To Try/Source Evaluation/For You, "Show disliked sources" recovery toggle, stale-queue completion feedback, app-facing build-channel wording cleanup, XML comment normalization. No migration. | Implemented and verified |
| `KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_IMPLEMENTATION.md` | v0.8.1-fix3 implementation report: new `SourceEvaluationCandidateQueuePolicy.staleCandidates()`, first-class stale/outdated reassessment queue (`state.staleCandidates`/`continuationCursorStale`/`canContinueStale`/`remainingStaleCandidateCount`, own preference-backed cursor slot, `|queue=stale` fingerprint suffix), `startOrContinueStaleReassessment()`/`restartStaleReassessment()` actions, compact UI button. No migration. | Implemented and verified |
| `KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_IMPLEMENTATION.md` | v0.8.1-fix2 implementation report (private corrective follow-up): fixed the v0.8.1-fix1 Loved Manga `GetCrossSourceGroupPrimary` Injekt crash (missing `KMKDomainModule` registrations), `KmkRecsWhatsNewPolicy` for KMK What's New sequencing, group-primary sync validation hardened to match restore. | Implemented and verified |
| `KMK_RECS_V0_8_1_FIX1_RATED_MANGA_AND_SOURCE_EVALUATION_POLISH_IMPLEMENTATION.md` | v0.8.1-fix1 implementation report (private corrective follow-up): linked-version remove confirmation, group-primary backup/restore/sync at proto 627, empty-selection "Select" action, Set Primary access clarification, Source Evaluation expandable evidence details. | Implemented and verified |
| `KMK_RECS_V0_8_0_RATED_MANGA_BULK_SELECTION_AND_GROUP_ACTIONS_IMPLEMENTATION.md` | v0.8.0 implementation report (private feature): Rated Manga bulk selection mode, per-item Recommendation/Rating/Group action menu, `LinkedVersionListScreen`, user-selected primary version (migration 62, `manga_cross_source_group_primary`), manual-selection-only `RatedGroupMergePlanner`, confirmation dialogs, tests. | Implemented and verified |
| `KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_IMPLEMENTATION.md` | v0.7.47 implementation report: bounded `getMangaDetails()` catalogue enrichment (`SourceEvaluationCatalogueEnricher`), split positive/negative/blocked/adult/metadata evidence counters (migration 61), revised fit-score formula and verdict order (`NEEDS_MANUAL_REVIEW` for metadata-sparse evidence, blocked/adult-risk gating on `STRONG_FIT`), `SourceEvaluationKeys.CURRENT_VERSION` 2->3, `SourceEvaluationDisplayPolicy` for stale-row labeling/ranking, `STALE_EVALUATION` eligibility result. | Implemented and verified |
| `KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_IMPLEMENTATION.md` | v0.7.44 implementation report: `RecommendationQueryAttemptPolicy`, group source-selection/visibility parity, `RecommendationQueryPlanner` fallback-chain widening, `TestInjektSupport` test fix (3 pre-existing + 2 latent failures), `SourceRecommendationQualityJobConflictPolicy`, phone UI density pass, deviations. No migration. | Implemented and verified |
| `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_IMPLEMENTATION.md` | v0.7.42-fix2 implementation report: new `SourceRecommendationFitDisplayPolicy` (INELIGIBLE/NOT_CHECKED/OUTDATED/GREAT/GOOD/MIXED/WEAK/NO_MATCHES/ERROR) shared by the queue (new `outdatedPromising` bucket), diagnostics, row labels, and sort; retired `SortMode.SEARCH_RELIABILITY` for `FOR_YOU_COMPATIBILITY`; `BEST_FIT` no longer reads the retired field; new `recheckOutdatedRecommendationQuality()` action. No migration. | Implemented and verified |
| `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md` | v0.7.42-fix1 implementation report: `SourceRecommendationFitEligibility` gained `isProbeEligible`/`isFitCurrent`; `SourceRecommendationQualityQueue` and `SourceRecommendationQualityDiagnostics` now share that contract instead of a hardcoded STRONG_FIT/WORTH_TRYING gate; Source Evaluation row subtitle no longer shows fabricated `search 0%`; v0.7.41 doc wording corrected. No migration. | Implemented and verified |
| `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md` | v0.7.42 implementation report: catalogue-only SourceEvaluationScorer reusing PersonalRecommendationScorer per item, removed the scorer's own search probe, catalogueMetadataConfidence signal, fail-open SourceRecommendationFitEligibility, staleness parity (migrations 59/60, CURRENT_VERSION 1->2), honest "For You search" UI labels. | Implemented and verified (queue/diagnostics gap fixed in v0.7.42-fix1) |
| `KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md` | v0.7.41 implementation report: single visibility contract in memory ranker + loadFromCache with min-chapter parity, group chunked collect-then-filter with visible-only budget (VisibleResultAccumulator), STATUS_EXHAUSTED terminal retry + planner cap/retry ordering, conservative RecommendationRetryClassifier, cancellation never recorded. No migration (STATUS_EXHAUSTED pre-existing). | Implemented |
| `KMK_RECS_V0_7_40_FOR_YOU_AND_RATED_GROUP_POLICY_FOUNDATION_IMPLEMENTATION.md` | v0.7.40 implementation report: same-refresh merge fix, migration 58 retry metadata, shared source selector + visibility policy foundation (gaps later corrected in v0.7.41) | Implemented |
| `KMK_RECS_V0_7_39_FOR_YOU_ROLLING_DISCOVERY_AND_RATED_GROUP_RECOMMENDATIONS_IMPLEMENTATION.md` | v0.7.39 implementation report: For You rolling discovery progress (migration 57, evaluatedPages planner, empty/filtered/error pages tracked, cumulative 20-page cap, progress cleared on history reset), Track B verification (group seeding already complete) | Implemented |
| `KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_AND_GROUP_SEED_ENRICHMENT_IMPLEMENTATION.md` | v0.7.38 implementation report: For You candidate discovery memory (migration 56, Store/Ranker/Planner), additional page discovery, GroupSeedTag/GroupSeedRecommendationScorer, GroupRecommendationSeedBuilder enrichment from all linked versions, Reset discovery history UI | Implemented |
| `KMK_RECS_V0_7_37_GROUP_SEEDED_RECOMMENDATIONS_LOADING_FIX_IMPLEMENTATION.md` | v0.7.37 implementation report: group-seeded recommendation bounded runtime (total/per-candidate/search timeouts), source-aware dedup, TARGET_RESULTS early exit, cross-source link group rating exclusivity, CancellationException propagation fix | Implemented |
| `KMK_RECS_V0_7_36_RATED_MANGA_UI_PARITY_AND_GROUP_RECS_CRASH_FIX_IMPLEMENTATION.md` | v0.7.36 implementation report: Rated Manga UI parity (shared RatedMangaCollectionContent composable, full features for LIKE/DISLIKE), Library toolbar shortcuts, group-seeded recommendation crash fix (NetworkToLocalManga), discoverability Explore overlay | Implemented |
| `KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_IMPLEMENTATION.md` | v0.7.35 implementation report: IO dispatcher fix for SourceRecommendationFitProbe (NetworkOnMainThreadException), Liked/Disliked manga entry points (RatedMangaScreen), group-seeded recommendations via long-press on Loved Manga | Implemented |
| `KMK_RECS_V0_7_10_RECOMMENDATION_QUALITY_STRONG_FIT_ERROR_FIX_IMPLEMENTATION.md` | v0.7.10 implementation report: fix Recommendation Quality strong-fit error (lastCandidatePool empty/stale), new installed/source resolvers, 17 new tests | Implemented |
| `KMK_RECS_V0_7_9_BEST_VERSION_DIALOG_AND_PREVIEW_POLISH_IMPLEMENTATION.md` | v0.7.9 implementation report: Best Version dialog Cancel fix, fullscreen zoomable page preview, defensive state guard, 13 new tests | Implemented |
| `KMK_RECS_V0_7_8_BEST_VERSION_CHAPTER_QUALITY_IMPLEMENTATION.md` | v0.7.8 implementation report: "Find best version" workflow, same-manga candidate search, chapter matching, page sampling, quality signal DB (migration 53), 40 new tests | Implemented |
| `KMK_RECS_V0_7_7_RECOMMENDATION_QUALITY_ON_DEMAND_FIX_IMPLEMENTATION.md` | v0.7.7 follow-up fix (in v0.7.8 APK): on-demand rec-quality probe for non-installed sources, SourceRecommendationQualityExtensionResolver, reactive installedExtensionKeys, reactive visibleSources, 10 new tests | Implemented |
| `KMK_RECS_V0_7_7_SOURCE_EVALUATION_FOLLOWUP_AND_BEST_VERSION_IMPLEMENTATION.md` | v0.7.7 implementation report: 5 follow-up fixes for toggle reversibility, rec-quality section, "Not checked" label, on-demand probe action, composable context fix | Implemented |
| `KMK_RECS_V0_7_5_RECOMMENDATION_JSON_EXPORT_IMPORT_IMPLEMENTATION.md` | v0.7.5 implementation report: JSON bundle export/import, 4 new pure files, import preview screen, 39 new tests | Implemented |
| `KMK_RECS_V0_7_4_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_IMPLEMENTATION.md` | v0.7.4 implementation report: extension version metadata, update reassessment detection, SourceRecommendationFitEligibility/Scorer, doc audit | Implemented |
| `KMK_RECS_V0_7_3_LOVED_MANGA_INSTALLED_SOURCE_FILTER_IMPLEMENTATION.md` | v0.7.3 implementation report: installed-source filter for Loved Manga, pure helper, fail-safe behavior | Implemented |
| `KMK_RECS_V0_7_2_LOVED_MANGA_SMART_GROUPING_AND_LINK_USAGE_IMPLEMENTATION.md` | v0.7.2 implementation report: tiered Loved Manga grouping (cross-source links first, conservative metadata fallback), cross-source link group doc audit | Implemented |
| `KMK_RECS_V0_7_1_CROSS_EXTENSION_MATCH_STATE_AND_SEEN_MENU_FIX_IMPLEMENTATION.md` | v0.7.1 implementation report: CrossExtensionMatchScreen state-save crash fix (primitive route args) and Seen other versions menu visibility fix | Implemented |
| `KMK_RECS_V0_7_0_LOVED_MANGA_VIEW_IMPLEMENTATION.md` | v0.7.0 implementation report: Loved Manga view with conservative duplicate grouping | Implemented |
| `KMK_RECS_V0_6_20_SOURCE_ORDER_REASSESSMENT_AND_DOC_CLEANUP_IMPLEMENTATION.md` | v0.6.20 implementation report: source status display ordering, reassessment baseline tracking, source explainability, management controls, seen manga marker | Implemented |
| `KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_IMPLEMENTATION.md` | v0.6.19 implementation report: taste confidence warning, Shizuku UX cleanup, safety diagnostics demotion, WorkManager background execution | Implemented |
| `KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_FOLLOWUP_IMPLEMENTATION.md` | v0.6.19 follow-up report: notification deep link, unassessed remaining count wording, offline start guard, installed-exclusion tests | Implemented |

## Archive Layout

Historical files are preserved instead of deleted:

| Folder | Contents |
| --- | --- |
| `archive/plans/` | Superseded implementation plans and old planning variants |
| `archive/implementations/` | Older implementation reports |
| `archive/research/` | Completed research notes that are not active top-level references |

Use archive files for historical context only. Current behavior should be verified from `CURRENT_STATE.md`, latest implementation reports, and source code.

## Current Rule

Before creating a new implementation plan, first check whether the topic is already covered by:

- `CURRENT_STATE.md`,
- `NEXT_WORK.md`,
- `RECOMMENDATION_IMPLEMENTATION_AUDIT.md`,
- `RECOMMENDATION_VERSIONING.md`,
- the active planning files listed above.

If a future implementation changes recommendation behavior, update the relevant markdown in the same session.









