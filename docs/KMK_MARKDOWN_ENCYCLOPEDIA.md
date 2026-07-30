# KMK Markdown Encyclopedia

Date: 2026-07-15
Status: central documentation map for humans, Codex, Claude Code, and future AI agents. This is an index and orientation guide, not an implementation plan.

## Purpose

This file explains where to look before changing, auditing, testing, or understanding the Komikku KMK fork. It exists because the project now has many planning, implementation, audit, OCR, source evaluation, recommendation, security, database, and community-readiness documents.

Use this file to find the right document quickly. Do not treat it as proof that a feature exists in code. For implementation truth, always check the current-state documents and the actual source code.

## First Rule

Do not assume a feature exists because it appears in a plan. Verify in this order:

1. Current state documentation.
2. Latest implementation report.
3. Actual source code.
4. Tests and build output.

## Required Reading By Task Type

| Task | Start Here | Then Check |
| --- | --- | --- |
| Any KMK recommendation implementation | `docs/recommendations/README.md`, `docs/recommendations/CURRENT_STATE.md`, `docs/recommendations/NEXT_WORK.md`, `docs/recommendations/DOCUMENTATION_RULES.md` | The focused plan or implementation report for that feature |
| Any Claude/Codex prompt | `docs/recommendations/DOCUMENTATION_RULES.md` | Current plan, adjacent implementation reports, and code paths |
| Current app behavior | `docs/recommendations/CURRENT_STATE.md` | Relevant implementation report and source code |
| Open, deferred, or next work | `docs/recommendations/NEXT_WORK.md` | Community phase docs if the work is about publishing/readiness |
| Version/build naming | `RECOMMENDATION_VERSIONING.md` | `app/build.gradle.kts`, `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` |
| Source Evaluation | `docs/recommendations/CURRENT_STATE.md` Source Evaluation sections | `KMK_RECS_V0_6_19...` through `KMK_RECS_V0_7_35...` docs |
| Source Evaluation upstream/community proposal | `docs/community/KMK_SOURCE_EVALUATION_UPSTREAM_PR_READINESS_PLAN.md` | Use before writing a GitHub issue, draft PR, Discord/Reddit explanation, or screenshot package for source-fit/source-evaluation feedback |
| For You recommendations | `docs/recommendations/CURRENT_STATE.md` Recommendation Systems sections | `TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md` and current implementation reports |
| Loved/Rated manga and cross-source grouping | `docs/recommendations/CURRENT_STATE.md` Cross-Extension and Rated Manga sections | `KMK_RECS_V0_7_0...`, `V0_7_1...`, `V0_7_2...`, `V0_7_3...`, `V0_7_35...` |
| Best Version / chapter quality | `docs/recommendations/CHAPTER_IMAGE_QUALITY_GLOBAL_SEARCH_FEASIBILITY_RESEARCH.md` | `KMK_RECS_V0_7_8...`, `KMK_RECS_V0_7_9...` |
| OCR downloaded text search | `docs/ocr/README.md` | OCR v0.1.0/v0.1.1 plans and implementation reports |
| Database, backup, sync, proto, migrations | `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` | SQLDelight migrations and backup model code |
| Security and privacy | `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` | Relevant feature docs and community risk register |
| Community/public sharing readiness | `docs/community/KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_PLAN.md` (current closeout pass, v0.7.46) | `KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_IMPLEMENTATION.md`; foundation docs: `KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_PLAN.md` + `_AMENDMENT.md` + `_IMPLEMENTATION.md` (v0.7.45), public README draft, public test build line docs, `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` for earlier history |
| Official Komikku alignment | `AGENTS.md`, `CONTRIBUTING.md`, `README.md` | Phase 10/11 architecture/style/test/release docs |
| Current Komikku-standard alignment implementation handoff | `docs/community/KMK_CURRENT_KOMIKKU_STANDARD_ALIGNMENT_IMPLEMENTATION_PLAN_2026-07-23.md` | Exact source-runtime inventory, atomic Source Evaluation error-row replacement, resource/search checks, toolchain notes, and gated identity decisions; private implementation planning only |
| Official Komikku 1.14.0 reconciliation | `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md` (complete, 9/9 phases) | `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_AUDIT.md`, `docs/community/KMK_UPSTREAM_1_14_DETAILED_RECONCILIATION_PLAN.md`, official git tags `v1.13.6` and `v1.14.0`, current source code |
| Extension/source runtime crashes | `docs/community/KMK_RECS_V0_8_10_FIX2_EXTENSION_ISOLATION_AUDIT.md`, `docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_PLAN.md`, `docs/community/KMK_RECS_V0_8_10_FIX5_SOURCE_CLIENT_HEALTH_AND_RECOVERY_PLAN.md`, `docs/community/KMK_RECS_V0_8_10_FIX6_REMAINING_SOURCE_RUNTIME_ISOLATION_PLAN.md`, `docs/community/KMK_RECS_V0_8_10_FIX7_SOURCE_RUNTIME_RETHROW_CONTAINMENT_PLAN.md`, `docs/community/KMK_RECS_V0_8_10_FIX8_ZSTD_AND_RUNTIME_SUPPRESSION_IMPLEMENTATION.md`, `docs/community/KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_IMPLEMENTATION.md` | Source execution call sites, `AndroidSourceManager`, source API methods/properties, source `client`/`headers`, source `getFilterList()`, source-runtime `Result.getOrThrow()` rethrows, cover/preview fetchers, feed/source-feed/saved-search paths, recommendation/source-evaluation/global-search/reader/download/library update paths, `okhttp-zstd` dependency, enforced source-runtime suppression, `SuwayomiApi.kt` safe client access, `rethrowIfFatal()` fatal-error hygiene helper |
| Claude Code delegation / teammate behavior | `docs/community/CLAUDE_CODE_DELEGATION_POLICY_REFERENCE.md` | User-level Claude settings at `C:\Users\USER\.claude\settings.json` and instructions at `C:\Users\USER\.claude\CLAUDE.md`; check `/status` and `/doctor` in Claude Code |
| Post-fix8 workflow retrospective | `docs/community/KMK_POST_FIX8_SOURCE_RUNTIME_AND_CLAUDE_WORKFLOW_RETROSPECTIVE.md` | Use before planning further v0.8.9/v0.8.10 cleanup; records why repeated source-runtime fixes happened, why the final layered fix worked, and how future Claude prompts should avoid agentic/delegated drift |
| v0.8.10-fix9 deferred polish/conformance | `docs/community/KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | Historical What's New conversion (done), Taste and Tags grouping (done), source-runtime hygiene (done); broader Komikku settings-widget conformance and the search-flicker item deferred with documented reasoning � see report |
| v0.8.11 UI/navigation standardization | `docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_IMPLEMENTATION.md` (plan: `..._IMPLEMENTATION_PLAN.md`, audit: `..._AUDIT_PLAN.md`) | All phases (A-H) implemented: Recommendation Settings destination dedupe/search cleanup, Komikku widget conformance, Taste Suggestions grouping, Sources To Try density, Source Priority cleanup, Source Evaluation sectioning, For You top-bar quick access, structural interaction/functionality audit, and the Loved/Liked/Disliked multi-select grouping bug fix |
| v0.8.12 Recommendation Settings structural follow-up | `docs/community/KMK_RECS_V0_8_12_RECOMMENDATION_SETTINGS_STRUCTURAL_FOLLOWUP_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | All workstreams (A-G) implemented after v0.8.11 device review: language availability policy, Matching and versions settings destination, confirmed-already-correct Source Evaluation outdated reassessment counts (plan-vs-code discrepancy documented), progressive tag/suggestion reveal, search destination regressions verified, user-readable source/recommendation row errors, SourceRuntime regression guard confirmed intact, and targeted For You false no-matches fix (bounded Popular-catalogue fallback) |
| v0.8.12-fix1 fatal-error containment correction | `docs/community/KMK_RECS_V0_8_12_FIX1_FATAL_ERROR_CONTAINMENT_IMPLEMENTATION.md` | Fixed two `catch (e: Error)` blocks in `BrowsePersonalRecommendationsScreenModel.searchSource()` (main tag-search loop + v0.8.12 catalogue fallback) that swallowed every `Error` subtype unconditionally instead of only recoverable per-source failures; both now route through the shared `rethrowIfFatal()` helper so fatal VM errors can no longer be silently hidden. Every other `catch (e: Error)` site in the recommendation codebase audited and confirmed already correct |
| v0.8.13 For You strategy recovery and relevance | `docs/community/KMK_RECS_V0_8_13_FOR_YOU_STRATEGY_RECOVERY_AND_RELEVANCE_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | All phases (A-I) implemented after a live-device audit (the affected source): `RecommendationStrategyRecoveryPolicy` recovers sources trapped by a stale persisted `TEXT_ONLY_TOP_TAGS` strategy and actively forgets a disproved strategy; `RecommendationQueryPlanner.buildPlans` rewritten to rotate the shared `RecommendationQueryAttemptPolicy` chain; `RecommendationAdditionalPagePolicy` stops wasted extra-page crawling; `PersonalRecommendationScorer.rankCandidates`'s `requirePositiveTasteEvidence` gate (applied to memory merge too) stops source-affinity-only candidates from being eligible; catalogue fallback now records itself under a named `CATALOGUE_FALLBACK` constant; `searchSource()` split into 5 helpers |
| v0.8.13-fix1 Recommendation Settings and Source Evaluation UX | `docs/community/KMK_RECS_V0_8_13_FIX1_RECOMMENDATION_SETTINGS_AND_EVALUATION_UX_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | All phases (A-G) implemented: search-flicker fix (removed `produceState`/`Crossfade`, synchronous `remember`), search-result category/control row distinction, language selector moved to Source Priority, group-preview budget moved to Matching and versions, read-only "Preview For You layout" dialog, truthful stale-reassessment completion states, Source Evaluation quarantine/blocked diagnostics collapsed by default, and v0.8.13 residual completion (searchSource size verified, encyclopedia duplicate rows merged) |
| Structural UX correction audit | `docs/community/KMK_RECS_STRUCTURAL_UX_CORRECTION_AUDIT.md` | Post-v0.8.13-fix1 audit clarifying that the user's request is structural, not local copy polish: Recommendation Settings should not keep unclear For You/Matching buckets, Source Evaluation needs actionable-vs-non-actionable stale reassessment separation, and technical wording/visual density need a shared KMK UI standard before the next implementation plan. |
| v0.8.14 Recommendation Settings structural UX correction | `docs/community/KMK_RECS_V0_8_14_RECOMMENDATION_SETTINGS_STRUCTURAL_UX_IMPLEMENTATION.md` (plan: `..._PLAN.md`, audit: `KMK_RECS_STRUCTURAL_UX_CORRECTION_AUDIT.md`) | All phases (A-G) implemented: Recommendation Settings index rebuilt to exactly five sections (Sources and languages, Taste and filters, Source Evaluation, Sources to try, Management and diagnostics); `For You`/`Matching and versions` retired as top-level destinations (files deleted, controls moved verbatim, zero preference-behavior change); Source Evaluation stale-reassessment completion message and per-row labels now correctly distinguish actionable from excluded/unreachable outdated sources (`EvaluationResultRow.isActionableOutdated`); Source Evaluation first-view setup clutter (skip/explicit toggles, candidate diagnostics, installer-mode selector) collapsed behind disclosures by default, installer mode force-shown when not ready; primary Source Evaluation copy audited for technical wording ("probe" → "test", "eligible" → plain wording) |
| v0.8.14 Recommendation Settings structural UX plan | `docs/community/KMK_RECS_V0_8_14_RECOMMENDATION_SETTINGS_STRUCTURAL_UX_PLAN.md` | Approved simpler five-section Recommendation Settings structure: Sources and languages, Taste and filters, Source Evaluation, Sources to try, Management and diagnostics. Removes For You and Matching/versions as top-level settings destinations, moves their controls, fixes actionable-vs-excluded outdated reassessment, reduces Source Evaluation visual fog, and defines tests/device QA. |
| v0.8.14 live-device structural UX follow-up | `docs/community/KMK_RECS_V0_8_14_LIVE_DEVICE_STRUCTURAL_UX_FOLLOWUP.md` | ADB review (Samsung SM-X520) after v0.8.14 confirmed the "Sources and languages" naming/language-bundling and the status-only For You preview were not what the user intended, and that the Source Evaluation excluded-sources note still read as blocking the reassess action. Produced the v0.8.14-fix1 plan below. |
| v0.8.14-fix1 Recommendation Settings structural completion | `docs/community/KMK_RECS_V0_8_14_FIX1_RECOMMENDATION_SETTINGS_STRUCTURAL_COMPLETION_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | All phases (A-F) implemented: "Sources and languages" renamed "For You sources", language selection moved to Management and diagnostics (`RecommendationDiagnosticsSettingsScreen`'s new "Recommendation languages" section, same preference key); the status-only For You preview replaced with a real read-only manga snapshot (`RecommendationForYouPreviewSnapshotStore`, new `recommendation_for_you_preview_snapshot` preference, delimited-string format, capped rows/manga, built by `BrowsePersonalRecommendationsScreenModel` after a run with visible results and read by `RecommendationsSettingsScreenModel`) showing Top Picks/source rows with covers and titles, never triggering a source search, manga/source navigation, or install/update action; Source Evaluation's `Reassess outdated` action reordered ahead of the excluded-sources note, which is now a collapsed `N outdated source(s) outside this run` disclosure instead of an always-visible card; Recommendation Settings search no longer shows a category-level subtitle identical to its own title; stale references to deleted settings screens audited (only historical/"retired" comments remain); two Source Evaluation tap targets (`Show details`/`Hide details` toggle, row overflow menu) widened from 16-28dp icon-only to normal size |
| v0.8.14 live-device structural UX follow-up | `docs/community/KMK_RECS_V0_8_14_LIVE_DEVICE_STRUCTURAL_UX_FOLLOWUP.md` | ADB-confirmed residual UX issues and clarified next-fix requirements for For You sources, language placement, read-only For You preview, Source Evaluation outdated reassessment, search subtitles, stale references, and structural clutter cleanup. |
| v0.8.14-fix1 Recommendation Settings structural completion plan | `docs/community/KMK_RECS_V0_8_14_FIX1_RECOMMENDATION_SETTINGS_STRUCTURAL_COMPLETION_PLAN.md` | Executable handoff plan for completing the v0.8.14 structural UX correction: rename Sources and languages to For You sources, move languages to management, replace the source-status preview with a real read-only For You manga snapshot, fix outdated reassessment presentation, clean search subtitles/stale references, and apply structural clutter cleanup. |
| v0.8.15 Source Evaluation reassessment and universal UI readability plan | `docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_PLAN.md` | ADB-backed handoff plan for the still-broken `Reassess outdated` flow: live DB/logcat evidence shows a false `Evaluation completed` state while stale source rows remain unchanged. Also defines the next structural KMK UI readability pass: compact first-view rows, details behind disclosure/help, clearer summaries, normal tap targets, and an audit across all KMK-added recommendation/rating/source/Best Version surfaces. |
| v0.8.15 Source Evaluation reassessment and universal UI readability | `docs/community/KMK_RECS_V0_8_15_SOURCE_EVALUATION_REASSESSMENT_AND_UNIVERSAL_UI_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | All phases (A-C) implemented: root-cause fix for the live-device false `Evaluation completed` bug -- `SourceEvaluationRunner.recordExtensionError()` changed from a fire-and-forget `scope.launch` write to `suspend`+awaited, and now deletes existing `source_evaluation` rows for the failing package/signature (`SourceEvaluationRepository.deleteByPackage`, no schema change) before upserting the one current extension-level error row, so it correctly replaces stale per-source rows instead of leaving them untouched; the runner's completed-candidate tracking moved from "handed to the runner" to "durably wrote something" (new pure `SourceEvaluationRunCompletionPolicy`), and a batch with candidates but zero durable writes now resolves to a new `SourceEvaluationQueueState.Status.NoActionableWork` terminal status instead of the generic `Completed`. Source Evaluation row subtitles compacted (dense multi-fact line -> `"<verdict> • Last evaluated ..."`, raw facts moved into the existing expandable evidence details); three Recommendation Settings summaries shortened; two mojibake'd strings corrected; new living `KMK_RECS_UNIVERSAL_UI_READABILITY_AUDIT.md` covering every KMK-added surface. 8 new tests. No migration. |
| v0.8.15-fix1 Source Evaluation stale queue and row readability | `docs/community/KMK_RECS_V0_8_15_FIX1_SOURCE_EVALUATION_STALE_QUEUE_AND_ROW_READABILITY_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | All fixes (A-G) implemented: closed the second cause of the live-device false-progress bug -- `SourceEvaluationCandidateQueuePolicy.staleCandidates(...)` gained `includeExplicit` and now filters blocked-explicit extensions out of the actionable stale list itself (mirroring `applyOptions`), so they never reach the runner; the runner's cursor-tracking set (kept as `_completedCandidateKeys` but contract-narrowed to durable writes only) is now populated only by confirmed durable writes, never by the deliberate explicit-skip branch. `recordExtensionError()`'s delete-before-upsert step is now failure-aware (`Boolean` return, `reconciliationFailedCount` + in-app note on failure) instead of silently treating a failed delete as durable. New `SourceEvaluationRowActionPolicy` backs the clarified per-row `Details`/`Errors` (renamed from generic "Show details", now also covers catalogue errors)/`Install` (new, reuses `extensionManager.installExtension(...)`, only shown when not installed/blocked/unavailable) action model, plus a page-level "Sources to try" app-bar shortcut to the existing `RecommendationNonInstalledDiscoverySettingsScreen`. 13 new tests. No migration. |
| v0.8.15-fix1 Source Evaluation stale queue and row readability plan | `docs/community/KMK_RECS_V0_8_15_FIX1_SOURCE_EVALUATION_STALE_QUEUE_AND_ROW_READABILITY_PLAN.md` | Live-device follow-up proving v0.8.15 did not fully close stale reassessment: the UI advanced from `Reassess outdated (25)` to `Continue reassessing outdated (15 remaining)` and reported `Evaluation completed`, but `source_evaluation` still had the same 48 stale rows. Root cause: `SourceEvaluationCandidateQueuePolicy.staleCandidates()` uses `pool.allEligible`, then `SourceEvaluationRunner` skips explicit candidates without writing while still advancing `_completedCandidateKeys`. Plan requires actionable-only stale candidates, durable-write-vs-cursor separation, `recordExtensionError()` delete-failure hardening, focused tests, Source Evaluation row readability/tap-target cleanup, per-row `Details`/`Errors`/`Install` actions, and a page-level shortcut to the existing Sources to try install/rating screen. |
| v0.8.16 Interaction, changelog, and Best Version polish | `docs/community/KMK_RECS_V0_8_16_INTERACTION_CHANGELOG_AND_BEST_VERSION_POLISH_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | All phases (A-G) implemented: Find best version moved out of the taste/seen dropdown into its own `MangaActionButton`, gated only on `onFindBestVersionClicked != null`; Best Version candidate rows show source names, new `FullscreenCandidatePreviewDialog` shows every sampled page for one candidate at once (read-only); Best Version Done navigates to the migrated/copied target manga via new pure `BestVersionMigrationCompletionPolicy` (falls back to `pop()` if unresolved); For You gained long-press multi-select (`ForYouSelectionPolicy`) with a bottom bar for bulk Love/Like/Dislike/Not-interested and single-selection-only Find best version/Open (bulk add-to-library deliberately deferred, documented); KMK What's New groups entries by version family via new pure `KmkRecsReleaseNotesGroupingPolicy`, current family expanded, older families collapsed with a summary, every historical entry preserved; `SourceEvaluationContinuationPolicyTest`'s stale "unconditionally on handoff" comment corrected, and `recordExtensionError()`'s delete+upsert decision extracted into a new directly-tested pure `SourceEvaluationExtensionErrorReconciliationPolicy`. 21 new tests. No migration. |
| v0.8.16 Interaction, changelog, and Best Version polish plan | `docs/community/KMK_RECS_V0_8_16_INTERACTION_CHANGELOG_AND_BEST_VERSION_POLISH_PLAN.md` | Executable plan: separate Find Best Version from rating/seen actions, add source labels/full-screen inspection/target navigation to Best Version, add For You long-press manga selection with real actions, collapse older KMK What's New version families by default while preserving every historical entry, and close Source Evaluation doc/test hygiene. |
| v0.8.17 Universal UI, For You quality, and Komikku 1.14.1 | `docs/community/KMK_RECS_V0_8_17_UNIVERSAL_UI_FOR_YOU_QUALITY_AND_1_14_1_IMPLEMENTATION.md` (plan: `..._PLAN.md`; working-tree baseline: `KMK_WORKING_TREE_HYGIENE_AND_BASELINE.md`) | **All four phases (A-D) complete.** Phase A: documented and classified the entire 152-path uncommitted working tree (every v0.8.10-fix5 through v0.8.16-fix1 pass, ~40 sessions, never committed) as implemented work, no destructive git operation performed. Phase B: re-audited Sources to Try, Rated manga collections, Group recommendations, and Best Version -- all confirmed already compliant, no code changes; `RecommendationDiagnosticsSettingsScreen` density deliberately deferred (collapsing sections would break `ScrollToAnchorEffect`'s search-anchor scrolling shared by every Recommendation Settings screen -- a real structural blocker, not a shallow tweak). Phase C: added a tap-to-explain `AlertDialog` to each For You source's status line, driven by new pure `RecommendationSourceStatusExplanationPolicy`, built entirely on the pre-existing, already-correct `RecommendationSourceStatus` diagnostic distinction (`NoMatches` vs `FilteredOut` vs 5 others) -- no new pipeline instrumentation or scoring change; compact status strings shortened, fuller explanations moved to new `rec_source_status_explain_*` strings shown on tap. Phase D: reconciled official Komikku `v1.14.0..v1.14.1` (4 commits, 14 files, no schema/proto changes) -- `AndroidSourceManager.DELEGATED_SOURCES` restructured Map→List with corrected delegated-source matching (upstream PR #1797, the real fix), Pururin moved package/source-id, two `/repo.json`-suffix-duplication bugs fixed, new `ChapterUrlHashMigration`/`DisabledRepoMigration` added at KMK's own next versionCode (`90`, not upstream's original numbers, after confirming how `Migrator`'s version-gating works), app version bumped `1.14.0`/`89` → `1.14.1`/`90`. 11 new tests total (`DelegatedSourceResolutionTest`, `RecommendationSourceStatusExplanationPolicyTest`). `KmkRecsReleaseNotes` bumped `772`/`"v0.8.16-fix1"` → `773`/`"v0.8.17"` (Phase C is a genuine user-facing recommendation change). Final APK: `Komikku-v1.14.1-kmk.8.17-debug.apk`. |
| v0.8.16-fix1 UI readability and responsive polish | `docs/community/KMK_RECS_V0_8_16_FIX1_UI_READABILITY_AND_RESPONSIVE_POLISH_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | All phases (A-G) implemented: Source Evaluation reassessment completion copy split into reassessed-vs-skipped sentences, skipped-sources disclosure relabeled "N skipped source(s)" (`SourceEvaluationStaleCompletionDisplayPolicy`'s existing three-state model was already correct -- copy-only fix); `EvaluationResultRow` subtitle bumped `bodySmall`→`bodyMedium`, `Details`/`Errors`/`Install` merged into one wrapping `FlowRow`; new pure `ForYouSelectionActionLayoutPolicy` makes the For You selection bottom bar width-aware (compact width moves Not interested/Find best version/Open into a "More" overflow, nothing removed); "Preview For You" rebuilt from a 420dp-capped `AlertDialog` into a full-screen `Dialog`+`Scaffold`/`AppBar`, still strictly read-only; Recommendation Settings index/search subtitles simplified (new `rec_settings_index_evaluation_summary` replaces the reused installer-implementation-detail text on the Source Evaluation row); **Best Version preview images fixed structurally** -- `SampledPage` now carries `PagePreview(index, imageUrl, source)` instead of a raw URL string, all three preview surfaces route through `SubcomposeAsyncImage`/`PagePreviewFetcher`/`SourceRuntime` instead of bypassing it, new pure `BestVersionPreviewOutcomePolicy` prevents a false-success `Loaded` row when zero pages are usable. 7 new tests. No migration, no scoring/ranking/queue changes. |
| v0.8.17 universal UI/action, For You quality, and Komikku 1.14.1 plan | `docs/community/KMK_RECS_V0_8_17_UNIVERSAL_UI_FOR_YOU_QUALITY_AND_1_14_1_PLAN.md` | Implemented by the v0.8.17 report above. Use it for original scope/rationale, not as pending work. |
| v0.8.17-fix1 Live-Device UI/Action and Best Version | `docs/community/KMK_RECS_V0_8_17_FIX1_LIVE_DEVICE_UI_ACTION_AND_BEST_VERSION_IMPLEMENTATION.md` (plan: `..._PLAN.md`) | **All seven phases (A-G) implemented.** Phase A: For You selection gained Clear Rating, real success/failure toast feedback (`BulkTasteActionOutcome`, previously fire-and-forget with swallowed errors), and a shared `ForYouActionButton` fixing bottom-bar alignment. Phase B: single-selection For You rating offers a `CrossExtensionMatchScreen` rate-other-versions continuation; the reader's chapter-completion prompt no longer requires a pre-confirmed cross-source group to offer this step (wording distinguishes confirmed-group vs. will-search cases). Phase C: new shared `SourceEvaluationRowAction` composable fixes `Details`/`Errors`/`Install` alignment (layout-only, policy untouched). Phase D: new `RecommendationSettingsQuickAccessRow` lets users jump between the five Recommendation Settings detail screens without backing out to the index (`navigator.replace(...)`, `ScrollToAnchorEffect` unaffected -- confirmed via re-run anchor-key tests). Phase E: Best Version candidates get a bounded 25s timeout (`withTimeoutOrNull`, new shared `previewOneCandidate()`), a per-candidate Retry action, and the "N/N pages loaded" string was actually corrected to "Prepared N of N preview samples" (a prior pass's comment had claimed this was already fixed, but the string text hadn't changed). Phase F: guardrail cross-check found Top Picks and Rated collections already compliant, no other new defects. `KmkRecsReleaseNotes` bumped `773`/`"v0.8.17"` → `774`/`"v0.8.17-fix1"`. No scoring changes, no upstream reconciliation, no schema changes. Final APK: `Komikku-v1.14.1-kmk.8.17-fix1-debug.apk`. |
| v0.8.17-fix1 live-device UI/action and Best Version plan | `docs/community/KMK_RECS_V0_8_17_FIX1_LIVE_DEVICE_UI_ACTION_AND_BEST_VERSION_PLAN.md` | Implemented by the v0.8.17-fix1 report above. Use it for original scope/rationale, not as pending work. |
| v0.8.17-fix2 Best Version origin/unavailable chapter and reader preview plan | `docs/community/KMK_RECS_V0_8_17_FIX2_BEST_VERSION_ORIGIN_UNAVAILABLE_AND_READER_PREVIEW_PLAN.md` | Source-material plan, never implemented standalone -- its contents were carried forward into and implemented by v0.8.18 (Phases B and C). |
| v0.8.18 Consolidated (Source Evaluation, Best Version, manga detail, extension export, edge panel) | `docs/community/KMK_RECS_V0_8_18_CONSOLIDATED_IMPLEMENTATION.md` (plan: `..._PLAN.md`; source material: `KMK_RECS_V0_8_18_ROLLING_FOLLOWUP_PLAN.md`, `KMK_RECS_V0_8_17_FIX2_BEST_VERSION_ORIGIN_UNAVAILABLE_AND_READER_PREVIEW_PLAN.md`) | **All six phases (A-F) implemented.** Phase A: removed `SourceEvaluationScreen`'s redundant top-right "Sources to try" shortcut (superseded by the v0.8.17-fix1 quick-access row); no safe Home/For You replacement route existed, documented as a deliberate non-fix. Phase B: `BestVersionCompareScreenModel.State` gained `compareCandidates` (origin always first, unaffected by `toggleSelection()`'s existing origin guard) and `originKey`; origin's chapter comes directly from already-fetched local `originChapters` (never searched through extensions); new `keepCurrentVersion()` finalizes immediately when origin is selected as best (no `migrateMangaUseCase` call, no migrate/copy dialog -- that dialog's lookup stays scoped to the unchanged `selectedCandidates`); new `CandidatePreviewState.Skipped` marks an already-`Unavailable` chapter immediately, never sending it to page-list fetching or leaving it as an indefinite spinner. Phase C: new pure `BestVersionReaderPreviewPolicy` maps `ReaderPreferences.defaultReadingMode()`/`webtoonSidePadding()` to side-padding applied only in the full-screen candidate comparison when the user's own reading mode is webtoon-style -- no reader lifecycle/history/timers/Discord/menus reused. Phase D: `MangaActionRow`'s WebView/Merge/Find best version moved into one "More" overflow menu. Phase E: new `ExtensionApkExporter` copies raw installed-extension APK/archive bytes unchanged via SAF -- single export (Extension Details overflow) and multi export (Extensions page selection mode, one zip with a non-sensitive manifest, no personal data). Phase F: new `RecommendationSettingsEdgeQuickAccessPanel` (28dp tap-to-open right-edge handle, narrow enough to avoid Android's edge-swipe-back gesture) sharing the same destination registry as the existing quick-access row, added to all five detail screens alongside it (kept, not replaced). 16 new tests total (`BestVersionOriginAndUnavailablePreviewTest`, `BestVersionReaderPreviewPolicyTest`, `ExtensionApkExporterTest`). `KmkRecsReleaseNotes` bumped `774`/`"v0.8.17-fix1"` → `775`/`"v0.8.18"`. App version intentionally unchanged (`1.14.1`/`90`). |
| KMK recommendation-screen interaction standard | `docs/community/KMK_RECS_INTERACTION_FUNCTIONALITY_AUDIT.md` | Defines the long-press/selection/bulk-action/search/grouping standard for KMK-added screens and audits current screens against it; read before adding selection/bulk-action behavior to any KMK recommendation screen |
| Source Evaluation upstream/community PR readiness | `docs/community/KMK_SOURCE_EVALUATION_UPSTREAM_PR_READINESS_PLAN.md` | Community-facing plan for presenting Source Evaluation/source-fit upstream: explains source-filter/tag/name matching, no-filter fallback behavior, privacy/screenshot censorship, issue-vs-PR strategy, and why this must be proposed as a small source-fit slice rather than the whole KMK fork. |
| Historical rationale | `docs/recommendations/archive/` | Prefer active docs first; archive files explain earlier decisions |

## Current Source Of Truth Files

| File | Purpose |
| --- | --- |
| `docs/recommendations/CURRENT_STATE.md` | Main living description of what the KMK recommendation system currently does. Read before assuming behavior. |
| `docs/recommendations/NEXT_WORK.md` | Current open work, deferred items, and known follow-ups. |
| `docs/recommendations/README.md` | Index for recommendation planning and implementation docs. |
| `docs/recommendations/DOCUMENTATION_RULES.md` | Rules for implementation reports, required reading, version notes, and documentation hygiene. |
| `RECOMMENDATION_VERSIONING.md` | APK naming, KMK versioning, and release-note rules. |
| `RECOMMENDATION_IMPLEMENTATION_AUDIT.md` | Early audit of implemented recommendation behavior and mismatches. Historical but still useful for origin context. |
| `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` | Database, backup, sync, proto, and migration audit. |
| `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` | Security/privacy review for recommendation, source evaluation, OCR, bundle sharing, and related data. |
| `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` | Phased community-readiness roadmap and consolidation status. |
| `docs/community/KMK_PUBLIC_README_DRAFT.md` | Draft public-facing readme for sharing the fork/build. |
| `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_AUDIT.md` | Read-only evidence and release blockers found while comparing the current KMK tree with official Komikku 1.14.0. Historical � see the implementation report below for final status. |
| `docs/community/KMK_UPSTREAM_1_14_DETAILED_RECONCILIATION_PLAN.md` | Code-level reconciliation sequence, exact file areas, migration bridge, compatibility checks, and required tests for moving the KMK fork onto official 1.14.0. Historical � see the implementation report below for final status. |
| `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md` | Full 9-phase implementation report: every file changed, DB/preferences/proto changes, tests run, deviations from plan, follow-up work. App `versionName`/`versionCode` now 1.14.0/89. |
| `docs/community/KMK_RECS_V0_8_10_FIX1_FULL_KOMIKKU_CONFORMANCE_AND_STABILITY_IMPLEMENTATION_PLAN.md` | Historical broad conformance/stability plan. Superseded for the current next handoff by the narrower post-fix8 `KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_PLAN.md`; keep for background only. |
| `docs/community/KMK_RECS_V0_8_10_FIX1_RELEASE_ASSURANCE_ADDENDUM.md` | v0.8.10-fix1 addendum: dependency/license audit, reproducible build docs, performance checks, upgrade/rollback tests, edge-case fixtures, crash-log export decision. |
| `docs/community/KMK_RECS_V0_8_10_FIX1_FINAL_RELEASE_SAFETY_ADDENDUM.md` | v0.8.10-fix1 addendum: final release-safety gates before handoff. |
| `docs/community/KMK_RECS_V0_8_10_FIX2_EXTENSION_ISOLATION_AUDIT.md` | Audit proving the Asura/Zstd `LinkageError` problem is a structural source-runtime isolation gap, not only an Asura screen issue. |
| `docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_PLAN.md` | v0.8.10-fix3: created the shared source-runtime boundary and migrated many source calls. Historical after live-device QA showed incomplete coverage. |
| `docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_PLAN.md` | v0.8.10-fix4 plan: live-device follow-up after fix3 still crashed from AsuraScans `okhttp3.zstd.Zstd`; completes remaining direct source-call migrations, especially `BrowseSourceScreenModel.getFilterList()` and For You/group/feed/evaluation paths. |
| `docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` | v0.8.10-fix4 implementation report: confirmed root cause (`BrowseSourceScreenModel.kt`'s unguarded `getFilterList()`), full call-site inventory, sections 7-8 re-check findings, new sibling-isolation tests, final verification and APK hash. Superseded as "current stable state" by fix5 (see correction note at the top of this file) -- covers source-*method*-call isolation only, not the source-property/image-fetch category fix5 closed. |
| `docs/community/KMK_RECS_V0_8_10_FIX5_SOURCE_CLIENT_HEALTH_AND_RECOVERY_PLAN.md` | v0.8.10-fix5 plan for the remaining live-device Asura/Zstd crash category: direct source `client`/`headers` access in cover/preview loading, runtime health diagnostics, and user-approved recovery actions. |
| `docs/community/KMK_RECS_V0_8_10_FIX5_SOURCE_CLIENT_HEALTH_AND_RECOVERY_IMPLEMENTATION.md` | v0.8.10-fix5 implementation report: new `SourceRuntimeOperation.Client`/`Headers`/`CoverImage`/`PreviewImage`, `HttpSource.safeClientOrNull()`/`safeHeadersOrNull()` accessors, `MangaCoverFetcher`/`PagePreviewFetcher` migration, `SourceRuntimeHealthReporter` + Source Evaluation recovery-actions dialog, batch-scoped proactive skip in `CrossExtensionGenreSearchSource`, 6 new tests, deferred call-site families with reasons, final verification and APK hash. Superseded as "current stable state" by fix6. |
| `docs/community/KMK_RECS_V0_8_10_FIX6_REMAINING_SOURCE_RUNTIME_ISOLATION_PLAN.md` | v0.8.10-fix6 plan after `komikku_crash_logs_7.txt`: remaining source-runtime crash paths still using direct `getFilterList()`, feed/source-feed saved-search callbacks, smart-source migration search, delegated `RecommendationSource` methods, and WebView source headers. |
| `docs/community/KMK_RECS_V0_8_10_FIX6_REMAINING_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md` | v0.8.10-fix6 implementation report: `SourceFeedScreenModel`/`FeedScreenModel`/`SmartSourceSearchEngine`/`RecommendationSource` delegate wrapper/`WebViewScreenModel` migrated to `SourceRuntime`; re-audit found and fixed 2 more genuine holes (`HttpPageLoader.getPages()` fallback, a duplicate `WebViewActivity.kt` headers pattern); full audit-command classification table; `SuwayomiApi.kt` deferred with reasoning; 3 new tests; final verification and APK hash. Superseded as "current stable state" by fix7. |
| `docs/community/KMK_RECS_V0_8_10_FIX7_SOURCE_RUNTIME_RETHROW_CONTAINMENT_PLAN.md` | v0.8.10-fix7 plan after `2026-07-18-debug.txt`: fixes the remaining AsuraScans/Zstd crash class where source-runtime failures are recorded but then rethrown as raw `Error` through `.getOrThrow()` into UI/background paths that only treat `Exception` as non-fatal. |
| `docs/community/KMK_RECS_V0_8_10_FIX7_SOURCE_RUNTIME_RETHROW_CONTAINMENT_IMPLEMENTATION.md` | v0.8.10-fix7 implementation report: new `RecoverableSourceRuntimeException`/`getOrThrowSourceRuntimeException()` (with a real defect in the plan's own provided code -- a missing `CancellationException` guard -- found and corrected); migrated the plan's 4 named locations; required audit found and fixed 6 more genuine holes (`MergedSource.kt` x2, `BrowseSourceScreenModel.kt`, `MangaScreenModel.kt`, `MigrationListScreenModel.kt` x3, `GalleryAdder.kt`); 2 pre-existing broader-scope error-handling gaps disclosed, not fixed; 4 new tests; final verification and APK hash. Superseded as "current stable state" by fix8. |
| `docs/community/KMK_RECS_V0_8_10_FIX8_ZSTD_AND_RUNTIME_SUPPRESSION_IMPLEMENTATION.md` | **Current stable state.** v0.8.10-fix8 implementation report: added `okhttp-zstd` to the `okhttp` version-catalog bundle (fixes the actual missing-classpath root cause for AsuraScans); enforced `SourceRuntimeFailureRegistry` suppression in `SourceRuntime.run()`/`runBlockingSourceCall()` (previously advisory-only) via new `SourceTemporarilyUnavailableException`; `safeClientOrNull()`/`safeHeadersOrNull()`/cover-preview fetchers needed no code change since they already delegate through `SourceRuntime`; re-audit of ~19 named touch-point files found all already covered by fix1-fix7; 3 pre-existing tests corrected for the new enforced-suppression behavior (repeated-failure count, lazy-client re-touch, same-source mid-batch suppression); 8 new tests; final verification and APK hash. |
| `docs/community/KMK_RECS_V0_8_10_FIX9_DEFERRED_POLISH_AND_CONFORMANCE_PLAN.md` | Historical v0.8.10-fix9 plan. Partially implemented; see the implementation report for delivered/deferred scope. |
| `docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_IMPLEMENTATION_PLAN.md` | Current v0.8.11 executable implementation plan. Converts the screenshot/code audit into exact file-level phases: Recommendation Settings index/search ownership, official Komikku settings-widget conformance, Taste Suggestions grouping/capping, Sources To Try action-density cleanup, Source Priority section cleanup, Source Evaluation sectioning, For You top-bar quick access, documentation/versioning, and verification gates. |
| `docs/community/KMK_RECS_V0_8_11_UI_NAVIGATION_STANDARDIZATION_AUDIT_PLAN.md` | v0.8.11 audit-first evidence file. Captures the screenshot findings, source inspection, duplicated Source Evaluation destination evidence, Komikku widget comparison, and phase split used by the implementation plan above. |
| `docs/community/CLAUDE_CODE_DELEGATION_POLICY_REFERENCE.md` | Reference for the local Claude Code delegation controls: user-level settings, user instructions, expected behavior, limitations, validation, rollback, and future prompt guidance. |
| `docs/community/KMK_POST_FIX8_SOURCE_RUNTIME_AND_CLAUDE_WORKFLOW_RETROSPECTIVE.md` | Post-fix8 retrospective: documents the two-layer source-runtime root cause, why repeated narrow fixes happened, why the final fix worked, future source-runtime rules, future Claude prompt rules, and the current follow-up candidates after fix8. |
| `docs/ocr/README.md` | OCR feature index and branch-specific OCR documentation entry point. |

## Constant Procedures

### Before Coding

1. Read `docs/recommendations/README.md`.
2. Read `docs/recommendations/CURRENT_STATE.md`.
3. Read `docs/recommendations/NEXT_WORK.md`.
4. Read `docs/recommendations/DOCUMENTATION_RULES.md`.
5. Read `RECOMMENDATION_VERSIONING.md` if an APK, release note, app ID, or version change is involved.
6. Read the focused plan or implementation report for the subject being changed.
7. Verify relevant behavior in source code before editing.
8. For an upstream upgrade, read the official-version audit and detailed reconciliation plan before touching Gradle, migrations, extension loading, source APIs, backup/sync, reader, or recommendation code.

### After Coding

1. Create or update the focused implementation report.
2. Update `docs/recommendations/CURRENT_STATE.md` when behavior changes.
3. Update `docs/recommendations/NEXT_WORK.md` when open work changes.
4. Update `docs/recommendations/README.md` when new docs are added.
5. Update `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` for user-visible changes.
6. Update `RECOMMENDATION_VERSIONING.md` when an APK is handed off.
7. Run focused tests first, then broader tests/build as appropriate.
8. Record tests run, build output, known limitations, and follow-ups.

### Gradle Verification On This Workstation

The system `java` command may resolve to Java 8, which cannot run Komikku's Gradle build. The repository has a local JDK 17 under `.tools/jdk17/jdk-17.0.19+10`. When verifying builds or tests from PowerShell, set `JAVA_HOME` and prepend its `bin` directory for that command session:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest
```

Use the same environment setup for `spotlessApply`, `spotlessCheck`, and `assembleDebug`. If Gradle reports "requires JVM 17 or later" then the command is still using Java 8 and the verification result is not valid.
### Implementation Report Required Fields

Implementation reports should include:

- Date.
- Version/build number.
- Scope.
- Goal.
- Files changed.
- Behavior changed.
- Tests run.
- APK/build output, when applicable.
- Known limitations.
- Follow-ups.
- Deviations from the plan.

## Subject Encyclopedia

### Recommendation Core / For You / Top Picks

| File | Use It For |
| --- | --- |
| `docs/recommendations/CURRENT_STATE.md` | Current For You, Top Picks, recommendation rows, ratings, and source evaluation behavior. |
| `TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md` | Original refinement logic for ratings and For You behavior. |
| `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md` | Early quality/efficiency recommendations and rationale. |
| `RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md` | Robustness and hardening plan for recommendation behavior. |
| `RECOMMENDATION_SEARCH_RESEARCH.md` | Search/recommendation research context. |
| `RECOMMENDATION_SOURCE_LANGUAGE_FILTER_PLAN.md` | Language filtering plan and reasoning. |
| `docs/recommendations/archive/plans/TOP_PICKS_FILTERING_AND_EXCEPTION_HANDLING_PLAN.md` | Historical Top Picks filtering and exception plan. |
| `docs/recommendations/archive/implementations/TOP_PICKS_FILTERING_AND_EXCEPTION_HANDLING_IMPLEMENTATION.md` | Historical Top Picks implementation details. |
| `docs/recommendations/archive/plans/FOR_YOU_SOURCE_ORDER_AND_COMBINED_ROW_PLAN.md` | Historical For You row order and combined row plan. |
| `docs/recommendations/archive/implementations/FOR_YOU_SOURCE_ORDER_AND_COMBINED_ROW_IMPLEMENTATION.md` | Historical For You row order implementation details. |

| `docs/recommendations/KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_PLAN.md` | v0.7.38 candidate memory + additional-page discovery plan. |
| `docs/recommendations/KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_IMPLEMENTATION.md` | v0.7.38 implementation report. |
| `docs/recommendations/KMK_RECS_V0_7_39_FOR_YOU_ROLLING_DISCOVERY_AND_RATED_GROUP_POLISH_PLAN.md` | v0.7.39 rolling discovery progress table plan. |
| `docs/recommendations/KMK_RECS_V0_7_39_FOR_YOU_ROLLING_DISCOVERY_AND_RATED_GROUP_POLISH_IMPLEMENTATION.md` | v0.7.39 implementation report. |
| `docs/recommendations/KMK_RECS_V0_7_40_FOR_YOU_AND_RATED_GROUP_POLICY_FOUNDATION_IMPLEMENTATION_PLAN.md` | v0.7.40 plan: merge fix, retry policy, shared source selector and visibility policy. |
| `docs/recommendations/KMK_RECS_V0_7_40_FOR_YOU_AND_RATED_GROUP_POLICY_FOUNDATION_IMPLEMENTATION.md` | v0.7.40 implementation report. |
| `docs/recommendations/KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_PLAN.md` | v0.7.43 plan: background For You search compatibility job, normal recommendation-row UX for Loved/Liked group seeds, and Seen/Not Interested mild-negative semantics. |
| `docs/recommendations/KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_PLAN.md` | v0.7.44 plan: shared strict-to-lenient query policy for For You and Loved/Liked group recommendations, group source/visibility parity, visibility-policy test repair, and Source Recommendation Quality test coverage. |

### Ratings, Seen, Loved/Rated Manga, Cross-Extension Matching

| File | Use It For |
| --- | --- |
| `docs/recommendations/KMK_RECS_V0_7_0_LOVED_MANGA_VIEW_IMPLEMENTATION.md` | Loved Manga section origin. |
| `docs/recommendations/KMK_RECS_V0_7_1_CROSS_EXTENSION_MATCH_STATE_AND_SEEN_MENU_FIX_PLAN.md` | Cross-extension matching state and Seen menu fix plan. |
| `docs/recommendations/KMK_RECS_V0_7_1_CROSS_EXTENSION_MATCH_STATE_AND_SEEN_MENU_FIX_IMPLEMENTATION.md` | Implemented Seen and match-state behavior. |
| `docs/recommendations/KMK_RECS_V0_7_2_LOVED_MANGA_SMART_GROUPING_AND_LINK_USAGE_PLAN.md` | Smart grouping and link usage plan for Loved Manga. |
| `docs/recommendations/KMK_RECS_V0_7_2_LOVED_MANGA_SMART_GROUPING_AND_LINK_USAGE_IMPLEMENTATION.md` | Implemented grouping/link usage details. |
| `docs/recommendations/KMK_RECS_V0_7_3_LOVED_MANGA_INSTALLED_SOURCE_FILTER_PLAN.md` | Plan to filter Loved Manga to installed sources. |
| `docs/recommendations/KMK_RECS_V0_7_3_LOVED_MANGA_INSTALLED_SOURCE_FILTER_IMPLEMENTATION.md` | Implemented installed-source filter behavior. |
| `docs/recommendations/KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_PLAN.md` | Rated Manga, Loved/Liked/Disliked entry points, and group-seeded recommendation plan. |
| `docs/recommendations/KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_IMPLEMENTATION.md` | Latest implementation report for Rated Manga and group-seeded recommendations. |
| `docs/recommendations/KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_PLAN.md` | Current plan to replace the separate group recommendation grid with the existing manga-detail Recommendations page pattern, seeded by all confirmed linked versions. |
| `docs/recommendations/KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_PLAN.md` | Follow-up plan to make the row-based grouped recommendation flow use grouped tags/titles, shared source policy, shared visibility policy, and strict-to-lenient query fallbacks rather than one filter-mapped search attempt. |
| `docs/recommendations/KMK_RECS_V0_5_0_CROSS_EXTENSION_MATCHING_PLAN.md` | Original cross-extension matching plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_5_0_CROSS_EXTENSION_MATCHING_IMPLEMENTATION.md` | Original implemented matching details. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_5_1_MATCHING_CAP_AND_ORIGIN_FILTER_PLAN.md` | Match cap and origin filtering plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_5_1_MATCHING_CAP_AND_ORIGIN_FILTER_IMPLEMENTATION.md` | Match cap and origin filtering implementation. |

### Reader Active-Reading Timer / Reading Schedule (v0.8.4-v0.8.5)

| File | Use It For |
| --- | --- |
| `docs/recommendations/KMK_RECS_V0_8_4_READING_TIMER_IMPLEMENTATION_PLAN.md` | v0.8.4 plan for the active-reading timer. Implemented � see `eu.kanade.tachiyomi.ui.reader.timer` (`ReaderTimerReducer`, `ReaderTimerCoordinator`, `ReaderTimerStateCodec`). |
| `docs/recommendations/KMK_RECS_V0_8_5_READING_SCHEDULE_IMPLEMENTATION_PLAN.md` | v0.8.5 plan for the optional reading schedule. Implemented � see `eu.kanade.tachiyomi.ui.reader.schedule` (`ReaderScheduleResolver`, `ReaderScheduleStore`). |
| `docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md` | Combined implementation report for all four v0.8.2-v0.8.5 phases: files changed, tests, deviations, known limitations. |

### Source Evaluation / Non-Installed Extensions / Source Quality

| File | Use It For |
| --- | --- |
| `docs/recommendations/CURRENT_STATE.md` | Current Source Evaluation behavior and status. |
| `docs/recommendations/KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.md` | v0.8.1-fix3 plan for fixing Source Evaluation continuation after a successful first reassessment batch: stale/outdated rows must remain actionable and continue in later batches instead of being hidden as already evaluated. Implemented. |
| `docs/recommendations/KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_PLAN.md` | v0.8.1-fix4 final cleanup/source-quality dislike plan: app-facing wording cleanup, stale reassessment completion feedback, and source/catalogue-quality dislike for poor, lewd, or explicit-heavy sources. Implemented — see `SourceQualityMarkPolicy`, `SourcePreferences.dislikedSourceQualityKeys()`. |
| `docs/recommendations/KMK_RECS_V0_8_2_FOR_YOU_DISPLAY_IMPLEMENTATION_PLAN.md` | v0.8.2 plan for the configurable For You results-per-source budget. Implemented � see `ForYouResultBudgetPolicy`, `SourcePreferences.recommendationResultBudget()`. |
| `docs/recommendations/KMK_RECS_V0_8_3_RECOMMENDATION_UI_REFINEMENT_IMPLEMENTATION_PLAN.md` | v0.8.3 plan for Recommendation Settings reorganization and UI refinement without scoring changes. Implemented. |
| `docs/recommendations/INSTALLED_SOURCE_FIT_FEASIBILITY_RESEARCH.md` | Research on evaluating installed source fit. |
| `docs/recommendations/SOURCE_LIST_DIAGNOSIS.md` | Diagnostics for source list behavior. |
| `docs/recommendations/KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_IMPLEMENTATION.md` | Background execution and UX implementation. |
| `docs/recommendations/KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_FOLLOWUP_IMPLEMENTATION.md` | Follow-up implementation for v0.6.19. |
| `docs/recommendations/KMK_RECS_V0_6_20_SOURCE_ORDER_REASSESSMENT_AND_DOC_CLEANUP_PLAN.md` | Source order, reassessment, and doc cleanup plan. |
| `docs/recommendations/KMK_RECS_V0_6_20_SOURCE_ORDER_REASSESSMENT_AND_DOC_CLEANUP_IMPLEMENTATION.md` | Implemented v0.6.20 behavior. |
| `docs/recommendations/KMK_RECS_V0_6_21_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_PLAN.md` | Update reassessment and recommendation-fit plan. |
| `docs/recommendations/KMK_RECS_V0_6_22_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md` | Continuation plan for recommendation quality. |
| `docs/recommendations/KMK_RECS_V0_7_4_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_IMPLEMENTATION.md` | Implemented source update reassessment and recommendation fit. |
| `docs/recommendations/KMK_RECS_V0_7_6_SOURCE_EVALUATION_CONTINUATION_AND_RECOMMENDATION_QUALITY_PLAN.md` | Source evaluation continuation and recommendation-quality plan. |
| `docs/recommendations/KMK_RECS_V0_7_7_RECOMMENDATION_QUALITY_ON_DEMAND_FIX_PLAN.md` | On-demand recommendation-quality fix plan. |
| `docs/recommendations/KMK_RECS_V0_7_7_RECOMMENDATION_QUALITY_ON_DEMAND_FIX_IMPLEMENTATION.md` | On-demand recommendation-quality fix implementation. |
| `docs/recommendations/KMK_RECS_V0_7_10_RECOMMENDATION_QUALITY_STRONG_FIT_ERROR_FIX_PLAN.md` | Strong Fit/Worth Trying recommendation-quality error fix plan. |
| `docs/recommendations/KMK_RECS_V0_7_10_RECOMMENDATION_QUALITY_STRONG_FIT_ERROR_FIX_IMPLEMENTATION.md` | Implemented v0.7.10 fix. |
| `docs/recommendations/KMK_RECS_V0_7_13_SOURCE_EVALUATION_RECOMMENDATION_QUALITY_FUNCTIONAL_FIX_PLAN.md` | Functional recommendation-quality fix plan. |
| `docs/recommendations/KMK_RECS_V0_7_13_SOURCE_EVALUATION_RECOMMENDATION_QUALITY_FUNCTIONAL_FIX_IMPLEMENTATION.md` | Implemented functional fix. |
| `docs/recommendations/KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_PLAN.md` | Current plan to move manual For You search compatibility checks out of screen-model scope and into a background job with progress/notification/cancel handling. |
| `docs/recommendations/KMK_RECS_V0_7_18_SOURCE_EVAL_ROBUSTNESS_IMPLEMENTATION.md` | Robustness implementation for Source Evaluation. |
| `docs/recommendations/KMK_RECS_V0_7_19_INSTALLED_SOURCE_FIT_IMPLEMENTATION.md` | Installed source fit implementation. |
| `docs/recommendations/KMK_RECS_SOURCE_EVALUATION_COMMUNITY_HARDENING_IMPLEMENTATION.md` | Community-readiness hardening for Source Evaluation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_8_SOURCE_EVALUATION_DETAILED_IMPLEMENTATION_PLAN.md` | Historical detailed source evaluation plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_8_SOURCE_EVALUATION_IMPLEMENTATION.md` | Historical source evaluation implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_9_SOURCE_EVALUATION_MIGRATION_CRASH_FIX_PLAN.md` | Historical migration crash fix plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_9_SOURCE_EVALUATION_MIGRATION_CRASH_FIX_IMPLEMENTATION.md` | Historical migration crash fix implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_10_SOURCE_EVALUATION_DI_CRASH_FIX_PLAN.md` | Historical DI crash fix plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_10_SOURCE_EVALUATION_DI_CRASH_FIX_IMPLEMENTATION.md` | Historical DI crash fix implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_11_SHIZUKU_SETUP_AND_TEMPORARY_USE_PLAN.md` | Historical Shizuku setup plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_11_SHIZUKU_SETUP_AND_TEMPORARY_USE_IMPLEMENTATION.md` | Historical Shizuku setup implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_12_SOURCE_EVALUATION_CANDIDATE_POOL_AND_SHIZUKU_UX_PLAN.md` | Candidate pool and Shizuku UX plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_12_SOURCE_EVALUATION_CANDIDATE_POOL_AND_SHIZUKU_UX_IMPLEMENTATION.md` | Candidate pool and Shizuku UX implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_13_SOURCE_EVALUATION_PRIVATE_INSTALL_VERIFICATION_AND_CLEANUP_PLAN.md` | Private installer verification/cleanup plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_13_SOURCE_EVALUATION_PRIVATE_INSTALL_VERIFICATION_AND_CLEANUP_IMPLEMENTATION.md` | Private installer verification/cleanup implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_14_TIMEOUT_RESILIENCE_AND_PRIORITY_RESET_SAFETY_PLAN.md` | Timeout and priority reset safety plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_14_TIMEOUT_RESILIENCE_AND_PRIORITY_RESET_SAFETY_IMPLEMENTATION.md` | Timeout and reset safety implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_15_SOURCE_EVALUATION_RESULTS_STABILITY_AND_SORT_PLAN.md` | Results stability and sorting plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_15_SOURCE_EVALUATION_RESULTS_STABILITY_AND_SORT_IMPLEMENTATION.md` | Results stability and sorting implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_16_SOURCE_EVALUATION_CRASH_QUARANTINE_AND_DIAGNOSTICS_PLAN.md` | Crash quarantine and diagnostics plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_16_SOURCE_EVALUATION_CRASH_QUARANTINE_AND_DIAGNOSTICS_IMPLEMENTATION.md` | Crash quarantine and diagnostics implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_17_SOURCE_EVALUATION_STARTUP_RECOVERY_AND_KNOWN_UNSAFE_SEED_PLAN.md` | Startup recovery and unsafe seed plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_17_SOURCE_EVALUATION_STARTUP_RECOVERY_AND_KNOWN_UNSAFE_SEED_IMPLEMENTATION.md` | Startup recovery implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_18_EXTENSION_LOAD_QUARANTINE_AND_STARTUP_SAFETY_PLAN.md` | Extension quarantine and startup safety plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_18_EXTENSION_LOAD_QUARANTINE_AND_STARTUP_SAFETY_IMPLEMENTATION.md` | Extension quarantine implementation. |

### Sources To Try / Source Preferences / Explicit Sources

| File | Use It For |
| --- | --- |
| `docs/recommendations/archive/plans/NON_INSTALLED_EXTENSION_DISCOVERY_PLAN.md` | Historical non-installed extension discovery plan. |
| `docs/recommendations/archive/implementations/NON_INSTALLED_EXTENSION_DISCOVERY_IMPLEMENTATION.md` | Historical non-installed extension discovery implementation. |
| `docs/recommendations/archive/plans/NON_INSTALLED_EXTENSION_DISCOVERY_HARDENING_PLAN.md` | Historical hardening plan for non-installed discovery. |
| `docs/recommendations/archive/implementations/NON_INSTALLED_EXTENSION_DISCOVERY_HARDENING_IMPLEMENTATION.md` | Historical hardening implementation. |
| `docs/recommendations/archive/plans/SOURCE_PREFERENCE_LIKE_DISLIKE_PLAN.md` | Source like/dislike plan. |
| `docs/recommendations/archive/implementations/SOURCE_PREFERENCE_LIKE_DISLIKE_IMPLEMENTATION.md` | Source like/dislike implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_5_SOURCES_TO_TRY_SELECTIVE_INSTALL_PLAN.md` | Selective install plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_5_SOURCES_TO_TRY_SELECTIVE_INSTALL_IMPLEMENTATION.md` | Selective install implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_6_EXTENSION_SELECTIVE_UNINSTALL_PLAN.md` | Selective uninstall plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_6_EXTENSION_SELECTIVE_UNINSTALL_IMPLEMENTATION.md` | Selective uninstall implementation. |
| `docs/recommendations/archive/plans/KMK_RECS_V0_6_7_EXPLICIT_SOURCE_FILTER_PLAN.md` | Explicit source filter plan. |
| `docs/recommendations/archive/implementations/KMK_RECS_V0_6_7_EXPLICIT_SOURCE_FILTER_IMPLEMENTATION.md` | Explicit source filter implementation. |

### Best Version / Chapter Image Quality / Migration Quality Signals

| File | Use It For |
| --- | --- |
| `docs/recommendations/CHAPTER_IMAGE_QUALITY_GLOBAL_SEARCH_FEASIBILITY_RESEARCH.md` | Research on finding best quality chapters across sources. |
| `docs/recommendations/KMK_RECS_V0_5_5_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md` | Earlier Best Version visual quality migration plan. |
| `docs/recommendations/KMK_RECS_V0_7_6_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md` | v0.7.6 Best Version plan. |
| `docs/recommendations/KMK_RECS_V0_7_7_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md` | v0.7.7 Best Version plan. |
| `docs/recommendations/KMK_RECS_V0_7_7_SOURCE_EVALUATION_FOLLOWUP_AND_BEST_VERSION_ADDENDUM.md` | Addendum tying Source Evaluation follow-up and Best Version. |
| `docs/recommendations/KMK_RECS_V0_7_7_SOURCE_EVALUATION_FOLLOWUP_AND_BEST_VERSION_IMPLEMENTATION.md` | Implementation report for the v0.7.7 follow-up. |
| `docs/recommendations/KMK_RECS_V0_7_8_BEST_VERSION_CHAPTER_QUALITY_IMPLEMENTATION_PLAN.md` | v0.7.8 Best Version chapter quality plan. |
| `docs/recommendations/KMK_RECS_V0_7_8_BEST_VERSION_CHAPTER_QUALITY_IMPLEMENTATION.md` | v0.7.8 implementation report. |
| `docs/recommendations/KMK_RECS_V0_7_9_BEST_VERSION_DIALOG_AND_PREVIEW_POLISH_PLAN.md` | Dialog cancel and preview zoom polish plan. |
| `docs/recommendations/KMK_RECS_V0_7_9_BEST_VERSION_DIALOG_AND_PREVIEW_POLISH_IMPLEMENTATION.md` | Dialog/preview polish implementation report. |

### Bundle Export / Import / Sharing Recommendations

| File | Use It For |
| --- | --- |
| `docs/recommendations/KMK_RECS_V0_7_5_RECOMMENDATION_JSON_EXPORT_IMPORT_PLAN.md` | Recommendation JSON export/import plan. |
| `docs/recommendations/KMK_RECS_V0_7_5_RECOMMENDATION_JSON_EXPORT_IMPORT_IMPLEMENTATION.md` | Implemented export/import behavior. |
| `docs/recommendations/KMK_RECS_V0_7_5_RECOMMENDATION_JSON_EXPORT_IMPORT_HARDENING_ADDENDUM.md` | Hardening addendum for JSON export/import. |
| `docs/community/KMK_PHASE_8_9_BUNDLE_AND_OCR_HARDENING_PLAN.md` | Community hardening for bundle/OCR work. |
| `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` | Security/privacy risks for sharing and bundle data. |

### OCR / Downloaded Text Search

| File | Use It For |
| --- | --- |
| `docs/ocr/README.md` | OCR entry point and current OCR documentation index. |
| `docs/ocr/KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_PLAN.md` | Initial OCR downloaded text search plan. |
| `docs/ocr/KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_IMPLEMENTATION.md` | Initial OCR implementation report. |
| `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_PLAN.md` | OCR database/text quality/search hardening plan. |
| `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_IMPLEMENTATION.md` | OCR hardening implementation report. |
| `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` | OCR security/privacy and storage considerations. |

### Database / Backup / Sync / Proto / Migrations

| File | Use It For |
| --- | --- |
| `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` | Main backup/sync/proto/migration audit. |
| `data/src/main/sqldelight/tachiyomi/migrations/README.md` | SQLDelight migration notes. |
| `docs/community/KMK_PHASE_3_4_DATABASE_SECURITY_PRIVACY_AUDIT_PLAN.md` | Database/security/privacy phase plan. |
| `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md` | Risk register for database, sync, and privacy concerns. |
| `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` | Data security and privacy review. |

### Community Readiness / Public Sharing / Audits

| File | Use It For |
| --- | --- |
| `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` | Main phased roadmap for community readiness. |
| `docs/community/KMK_CONSOLIDATION_SNAPSHOT.md` | Snapshot of project consolidation state. |
| `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` | Classification of features and readiness. |
| `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md` | Community readiness audit. |
| `docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md` | Documentation hygiene audit. |
| `docs/community/KMK_CONSOLIDATION_VALIDATION_AND_HANDOFF.md` | Validation and handoff notes. |
| `docs/community/KMK_FULL_RECONCILIATION_AUDIT.md` | Full reconciliation audit. |
| `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION_PLAN.md` | Publishing-readiness implementation plan. |
| `docs/community/KMK_KOMIKKU_CONVENTION_COMPLIANCE_AUDIT_2026-07-22.md` | Upstream-convention compliance audit for the private fork; extraction blockers by priority. |
| `docs/community/KMK_PRIVATE_CONVENTION_CORRECTION_PASS_2026-07-22.md` | First convention-correction implementation plan (MdList SourceRuntime bypass, reconciliation sequence extraction). |
| `docs/community/KMK_PRIVATE_CONVENTION_CORRECTION_PASS_REVIEW_2026-07-23.md` | Independent review of the 2026-07-22 pass; identified the two P1 gaps closed by the 2026-07-23 alignment pass. |
| `docs/community/KMK_CURRENT_KOMIKKU_STANDARD_ALIGNMENT_IMPLEMENTATION_PLAN_2026-07-23.md` | Implementation plan closing the atomic-replacement and persistence-test gaps; see `CURRENT_STATE.md` for the executed result. |
| `docs/community/KMK_EVALUATION_MODE_UNDO_JOURNAL.md` | Evaluation Mode Action Undo Journal: bounded in-memory undo for test rating/Not-Interested actions; persistence/scope/conflict-behavior decision record. |
| `docs/community/KMK_SOURCE_RUNTIME_EXHAUSTIVE_INVENTORY_2026-07-23.md` | Line-level classification of active extension/source execution sites, lower-layer boundaries, source implementations, newly fixed bypasses, and verification limits. |
| `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md` | Publishing-readiness implementation report. |
| `docs/community/KMK_RECONCILIATION_FOLLOWUP_EXACT_FIXES_PLAN.md` | Exact follow-up fixes after reconciliation. |
| `docs/community/KMK_PUBLIC_README_DRAFT.md` | Public-facing readme draft. |
| `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION_PLAN.md` | Separate public test build line plan. |
| `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md` | Public test build line implementation report. |
| `docs/community/KMK_PHASE_12_PUBLIC_SHARING_PACKAGE_PLAN.md` | Public sharing package plan. |
| `docs/community/KMK_SOURCE_EVALUATION_PUBLIC_FEATURE_BRIEF.md` | Public/upstream Source Evaluation ("source-fit") feature brief: sanitized, no private source/repo names, ready for a GitHub issue/discussion/draft-PR/Discord post once screenshots are attached and censored per its own rules. |
| `docs/community/KMK_RECS_V0_8_18_FIX1_PRIVATE_NEXT_WORK_IMPLEMENTATION_PLAN.md` | Private, implementation-grade plan for the v0.8.18-fix1 next-fix queue (numeric settings sliders, extension export IO dispatcher, WebView copy-link wiring, quick-access panel relocation, Source Evaluation Home/For You navigation, universal UI audit triage, For You scoring deferral, upstream-PR-base note). Not for public posting. |
| `docs/community/KMK_RECS_V0_8_18_FIX1_PRIVATE_NEXT_WORK_IMPLEMENTATION.md` | Implementation report for v0.8.18-fix1: exact files changed, behavior changed, tests run, and known limitations for the plan above -- including a "Post-Implementation Correction" section for a crash regression found and fixed during live-device QA. Not for public posting. |
| `docs/community/KMK_RECS_V0_8_18_FIX1_DEVICE_QA.md` | Live-device QA results for v0.8.18-fix1: per-item verification of Copy link, extension export, the relocated quick-access panel, and Source Evaluation's Go-to-For-You action, plus the crash regression this pass found and fixed. Not for public posting. |
| `docs/community/KMK_SOURCE_EVALUATION_SCREENSHOT_EVIDENCE_WORKFLOW.md` | Repeatable screenshot capture + manual sanitization workflow for the public source-fit evidence package (raw/sanitized folder split, captioning rules); pairs with `scripts/kmk_capture_source_fit_evidence.ps1`. Safe to reference publicly -- contains no private names itself. |
| `docs/community/KMK_CLAUDE_ADB_AND_SCREENSHOT_WORKFLOW_POLICY.md` | Effective 2026-07-21: Claude no longer drives ADB to capture screenshots -- the user supplies them directly, or Claude writes a verification-request document (with a ready-to-paste Codex prompt) for another agent to run on-device. Building/installing the app remains a narrow, per-instance exception. Not for public posting. |
| `docs/community/KMK_CLAUDE_SESSION_HANDOFF_FOR_CODEX.md` | Exact, self-contained record of one full Claude session: public source-fit evidence/PR-prep work, the separate full-feature screenshot documentation effort, and the real "Evaluation Mode" code feature (developer setting that obfuscates source/repo names, icons, and preferred/blocked tags for screenshots -- manga titles are never touched, including disliked ones) -- exact files touched, what's verified vs. not, and a prioritized what's-left list with a ready Codex regression-check prompt. Not for public posting. |

### Architecture / Style / Official Komikku Alignment

| File | Use It For |
| --- | --- |
| `AGENTS.md` | Agent/code style rules for the repository. |
| `CONTRIBUTING.md` | Official contribution expectations. |
| `README.md` | Project overview and official project positioning. |
| `docs/community/KMK_PHASE_10_11_ARCHITECTURE_STYLE_TEST_RELEASE_PLAN.md` | Architecture, style, test, and release readiness plan. |
| `docs/recommendations/KMK_RECS_V0_7_15_PHASE_6_7_CLEANUP_AND_KOMIKKU_ALIGNMENT_PLAN.md` | Komikku alignment cleanup plan. |
| `docs/recommendations/KMK_RECS_V0_7_15_PHASE_6_7_CLEANUP_AND_KOMIKKU_ALIGNMENT_IMPLEMENTATION.md` | Komikku alignment implementation report. |
| `docs/recommendations/KMK_RECS_V0_7_14_RECOMMENDATION_UX_FORMATTING_AND_SETTINGS_CONSOLIDATION_PLAN.md` | UI/UX formatting and settings consolidation plan. |
| `docs/recommendations/KMK_RECS_V0_7_14_RECOMMENDATION_UX_FORMATTING_AND_SETTINGS_CONSOLIDATION_IMPLEMENTATION.md` | UI/UX formatting and settings consolidation implementation. |

### Release Notes / Versioning / APK Handoff

| File | Use It For |
| --- | --- |
| `RECOMMENDATION_VERSIONING.md` | Main KMK versioning and APK naming source. |
| `docs/recommendations/DOCUMENTATION_RULES.md` | Documentation/version update requirements. |
| `docs/recommendations/CURRENT_STATE.md` | Current feature version summary. |
| `docs/recommendations/KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_PLAN.md` | v0.8.9 plan: official-style What's New structure, Recommendation Settings search. |
| `docs/recommendations/KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_IMPLEMENTATION.md` | v0.8.9 implementation report � see `exh.recs.KmkRecsReleaseNotes` (Markdown format kept, renderer already GFM-capable) and `exh.recs.settings.RecommendationSettingsSearchIndex`/`RecommendationSettingsSearchScreen`. Implemented. |
| `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION_PLAN.md` | Separate public test build identity/version planning. |
| `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md` | Public test build implementation details. |

## Archive Rules

Archive files are historical. They explain why earlier choices were made, but they do not override the current state.

Use archive files for:

- Old rationale.
- Previous bug context.
- Planned-versus-implemented comparisons.
- Understanding how a feature evolved.

Prefer active files for:

- Current behavior.
- Current open work.
- Current versioning.
- Current public/community status.

Archive locations:

- `docs/recommendations/archive/plans/`
- `docs/recommendations/archive/implementations/`
- `docs/recommendations/archive/research/`
- `docs/recommendations/archive/NEXT_WORK_STALE_V0_7_26_ARCHIVED_2026_06_29.md`

## Root Markdown Files

| File | Purpose |
| --- | --- |
| `README.md` | Upstream/project overview. |
| `AGENTS.md` | Repository-specific coding and agent instructions. |
| `CONTRIBUTING.md` | Contribution rules and expectations. |
| `CODE_OF_CONDUCT.md` | Community conduct rules. |
| `CHANGELOG.md` | Project changelog. |
| `RECOMMENDATION_VERSIONING.md` | KMK recommendation build/versioning rules. |
| `RECOMMENDATION_IMPLEMENTATION_AUDIT.md` | Early recommendation implementation audit. |
| `RECOMMENDATION_SEARCH_RESEARCH.md` | Search/recommendation research. |
| `RECOMMENDATION_SOURCE_LANGUAGE_FILTER_PLAN.md` | Source language filter plan. |
| `RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md` | Recommendation hardening plan. |
| `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md` | Recommendation quality/efficiency plan. |
| `TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md` | Taste rating and For You refinement plan. |

## Quick Lookup By Question

| Question | Look Here |
| --- | --- |
| What version are we on? | `docs/recommendations/CURRENT_STATE.md`, `RECOMMENDATION_VERSIONING.md`, release notes code |
| What should Claude read first? | `docs/recommendations/README.md`, `CURRENT_STATE.md`, `NEXT_WORK.md`, `DOCUMENTATION_RULES.md`, focused plan |
| What still needs work? | `docs/recommendations/NEXT_WORK.md` |
| How do I run Gradle here without Java 8 failing? | This encyclopedia's "Gradle Verification On This Workstation" section |
| How do ratings affect recommendations? | `docs/recommendations/CURRENT_STATE.md`, `TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md`, taste profile code |
| What does Seen do? | `docs/recommendations/CURRENT_STATE.md`, v0.6.20/v0.7.1 docs, seen backup docs |
| How are For You sources ordered? | `docs/recommendations/CURRENT_STATE.md`, source evaluation docs |
| How does Top Picks work? | `docs/recommendations/CURRENT_STATE.md`, Top Picks archive plan/implementation |
| Why are Source Evaluation recommendation rows failing? | `docs/recommendations/CURRENT_STATE.md`, v0.7.10/v0.7.13/v0.7.35 docs, source probe code |
| How do non-installed source suggestions work? | `docs/recommendations/CURRENT_STATE.md`, non-installed discovery archive docs |
| How are explicit/hentai sources handled? | `docs/recommendations/CURRENT_STATE.md`, explicit source filter docs, security review |
| How does Best Version work? | `docs/recommendations/KMK_RECS_V0_7_8...`, `KMK_RECS_V0_7_9...`, chapter image quality research |
| How does OCR work? | `docs/ocr/README.md` |
| Is OCR included in normal builds? | `docs/ocr/README.md`, OCR implementation docs, security review |
| What is backed up/synced? | `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` |
| What proto fields are used/reserved? | `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` |
| Is this community-ready? | `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`, readiness audits, public README draft |
| Is it safe to publish? | `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`, community readiness docs, public test build docs |
| Where are old plans? | `docs/recommendations/archive/plans/` |
| Where are old implementation reports? | `docs/recommendations/archive/implementations/` |

## Maintenance Rules For This Encyclopedia

Update this file when:

- A new documentation folder is added.
- A new major subject area is introduced.
- A new plan or implementation report becomes the reference document for a feature.
- A document is archived, renamed, or replaced.
- Public/community readiness status changes.
- Versioning or documentation procedures change.

Do not update this file for every small implementation report unless that report becomes a reference document. Use `docs/recommendations/README.md` for detailed per-version listings.






## Implementation Planning Standard

Authoritative guide: docs/IMPLEMENTATION_PLAN_STANDARD.md

Use this file for every future implementation plan. It defines the required repository research, current-behavior mapping, exact file and symbol planning, phase sizing, UI/lifecycle/performance review, persistence and migration analysis, security/privacy review, exception handling, test matrix, documentation, and Claude execution contract.

Every plan must include a phase-specific Claude model and effort assignment. Use risk-based defaults
from section 3A of `docs/IMPLEMENTATION_PLAN_STANDARD.md`, but honor the current project preference:
after Codex has already done the audit and written an exact implementation-ready plan, prefer Sonnet
low for Claude execution unless the plan explains why low effort is unsafe. Record the reason, token
tradeoff, escalation rule, actual model/effort used, and required verification.

Every plan must also document whether the issue is local or structural. If several screens or jobs
fail through one shared external boundary, such as extension source execution, network calls,
backup decoding, tracker calls, installer/service calls, migrations, reader page loading, or
background work, plan the shared boundary/policy first. Local catches are only acceptable as
documented containment; they are not the final fix unless the plan proves there is no shared contract
to repair.

When user intent affects the implementation contract, ask narrow clarification questions before
finalizing the plan and record the answer as a decision. Future implementers should not have to infer
whether a button closes a dialog or exits a workflow, whether a rating affects similar manga, whether
work continues in the background, or which source-feedback axis is being changed.

Current detailed examples:

- docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md
- docs/recommendations/KMK_RECS_V0_8_2_FOR_YOU_DISPLAY_IMPLEMENTATION_PLAN.md
- docs/recommendations/KMK_RECS_V0_8_3_RECOMMENDATION_UI_REFINEMENT_IMPLEMENTATION_PLAN.md
- docs/recommendations/KMK_RECS_V0_8_4_READING_TIMER_IMPLEMENTATION_PLAN.md
- docs/recommendations/KMK_RECS_V0_8_5_READING_SCHEDULE_IMPLEMENTATION_PLAN.md

The standard is living documentation. When later work establishes a repeatable planning, testing, security, documentation, or handoff practice, update the standard and this encyclopedia entry instead of leaving that practice implicit.
