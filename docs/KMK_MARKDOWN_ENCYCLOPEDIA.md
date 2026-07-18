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
| For You recommendations | `docs/recommendations/CURRENT_STATE.md` Recommendation Systems sections | `TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md` and current implementation reports |
| Loved/Rated manga and cross-source grouping | `docs/recommendations/CURRENT_STATE.md` Cross-Extension and Rated Manga sections | `KMK_RECS_V0_7_0...`, `V0_7_1...`, `V0_7_2...`, `V0_7_3...`, `V0_7_35...` |
| Best Version / chapter quality | `docs/recommendations/CHAPTER_IMAGE_QUALITY_GLOBAL_SEARCH_FEASIBILITY_RESEARCH.md` | `KMK_RECS_V0_7_8...`, `KMK_RECS_V0_7_9...` |
| OCR downloaded text search | `docs/ocr/README.md` | OCR v0.1.0/v0.1.1 plans and implementation reports |
| Database, backup, sync, proto, migrations | `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` | SQLDelight migrations and backup model code |
| Security and privacy | `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` | Relevant feature docs and community risk register |
| Community/public sharing readiness | `docs/community/KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_PLAN.md` (current closeout pass, v0.7.46) | `KMK_V0_7_46_PUBLIC_POLISH_CLOSEOUT_IMPLEMENTATION.md`; foundation docs: `KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_PLAN.md` + `_AMENDMENT.md` + `_IMPLEMENTATION.md` (v0.7.45), public README draft, public test build line docs, `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` for earlier history |
| Official Komikku alignment | `AGENTS.md`, `CONTRIBUTING.md`, `README.md` | Phase 10/11 architecture/style/test/release docs |
| Official Komikku 1.14.0 reconciliation | `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md` (complete, 9/9 phases) | `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_AUDIT.md`, `docs/community/KMK_UPSTREAM_1_14_DETAILED_RECONCILIATION_PLAN.md`, official git tags `v1.13.6` and `v1.14.0`, current source code |
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
| `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_AUDIT.md` | Read-only evidence and release blockers found while comparing the current KMK tree with official Komikku 1.14.0. Historical — see the implementation report below for final status. |
| `docs/community/KMK_UPSTREAM_1_14_DETAILED_RECONCILIATION_PLAN.md` | Code-level reconciliation sequence, exact file areas, migration bridge, compatibility checks, and required tests for moving the KMK fork onto official 1.14.0. Historical — see the implementation report below for final status. |
| `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md` | Full 9-phase implementation report: every file changed, DB/preferences/proto changes, tests run, deviations from plan, follow-up work. App `versionName`/`versionCode` now 1.14.0/89. |
| `docs/community/KMK_RECS_V0_8_10_FIX1_FULL_KOMIKKU_CONFORMANCE_AND_STABILITY_IMPLEMENTATION_PLAN.md` | **Current in-progress work.** v0.8.10-fix1: full Komikku UI/architecture conformance pass across every KMK-added surface, lossless historical What's New conversion (supersedes the v0.8.10 Phase G "keep-as-is" decision), mandatory application-wide crash investigation. Gated Phase 0-8 plan. |
| `docs/community/KMK_RECS_V0_8_10_FIX1_RELEASE_ASSURANCE_ADDENDUM.md` | v0.8.10-fix1 addendum: dependency/license audit, reproducible build docs, performance checks, upgrade/rollback tests, edge-case fixtures, crash-log export decision. |
| `docs/community/KMK_RECS_V0_8_10_FIX1_FINAL_RELEASE_SAFETY_ADDENDUM.md` | v0.8.10-fix1 addendum: final release-safety gates before handoff. |
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
| `docs/recommendations/KMK_RECS_V0_8_4_READING_TIMER_IMPLEMENTATION_PLAN.md` | v0.8.4 plan for the active-reading timer. Implemented — see `eu.kanade.tachiyomi.ui.reader.timer` (`ReaderTimerReducer`, `ReaderTimerCoordinator`, `ReaderTimerStateCodec`). |
| `docs/recommendations/KMK_RECS_V0_8_5_READING_SCHEDULE_IMPLEMENTATION_PLAN.md` | v0.8.5 plan for the optional reading schedule. Implemented — see `eu.kanade.tachiyomi.ui.reader.schedule` (`ReaderScheduleResolver`, `ReaderScheduleStore`). |
| `docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_FOR_YOU_UI_AND_READING_TIMER_IMPLEMENTATION.md` | Combined implementation report for all four v0.8.2-v0.8.5 phases: files changed, tests, deviations, known limitations. |

### Source Evaluation / Non-Installed Extensions / Source Quality

| File | Use It For |
| --- | --- |
| `docs/recommendations/CURRENT_STATE.md` | Current Source Evaluation behavior and status. |
| `docs/recommendations/KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_PLAN.md` | v0.8.1-fix3 plan for fixing Source Evaluation continuation after a successful first reassessment batch: stale/outdated rows must remain actionable and continue in later batches instead of being hidden as already evaluated. Implemented. |
| `docs/recommendations/KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_PLAN.md` | v0.8.1-fix4 final cleanup/source-quality dislike plan: app-facing wording cleanup, stale reassessment completion feedback, and source/catalogue-quality dislike for poor, lewd, or explicit-heavy sources. Implemented â€” see `SourceQualityMarkPolicy`, `SourcePreferences.dislikedSourceQualityKeys()`. |
| `docs/recommendations/KMK_RECS_V0_8_2_FOR_YOU_DISPLAY_IMPLEMENTATION_PLAN.md` | v0.8.2 plan for the configurable For You results-per-source budget. Implemented — see `ForYouResultBudgetPolicy`, `SourcePreferences.recommendationResultBudget()`. |
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
| `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md` | Publishing-readiness implementation report. |
| `docs/community/KMK_RECONCILIATION_FOLLOWUP_EXACT_FIXES_PLAN.md` | Exact follow-up fixes after reconciliation. |
| `docs/community/KMK_PUBLIC_README_DRAFT.md` | Public-facing readme draft. |
| `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION_PLAN.md` | Separate public test build line plan. |
| `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md` | Public test build line implementation report. |
| `docs/community/KMK_PHASE_12_PUBLIC_SHARING_PACKAGE_PLAN.md` | Public sharing package plan. |

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
| `docs/recommendations/KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_IMPLEMENTATION.md` | v0.8.9 implementation report — see `exh.recs.KmkRecsReleaseNotes` (Markdown format kept, renderer already GFM-capable) and `exh.recs.settings.RecommendationSettingsSearchIndex`/`RecommendationSettingsSearchScreen`. Implemented. |
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

Every plan must include a phase-specific Claude model and effort assignment. Use Sonnet high for ordinary implementation, Sonnet medium only for tightly specified throughput-oriented work, Opus high/xhigh for migrations, lifecycle, security, concurrency, architecture, and difficult audits, and Fable high/xhigh only for unusually large autonomous phases. Use `opusplan` for a plan-first/execute-second workflow. Record the reason, token tradeoff, escalation rule, actual model/effort used, and required verification. The authoritative decision matrix is section 3A of `docs/IMPLEMENTATION_PLAN_STANDARD.md`.

Current detailed examples:

- docs/recommendations/KMK_RECS_V0_8_2_TO_V0_8_5_MASTER_IMPLEMENTATION_PLAN.md
- docs/recommendations/KMK_RECS_V0_8_2_FOR_YOU_DISPLAY_IMPLEMENTATION_PLAN.md
- docs/recommendations/KMK_RECS_V0_8_3_RECOMMENDATION_UI_REFINEMENT_IMPLEMENTATION_PLAN.md
- docs/recommendations/KMK_RECS_V0_8_4_READING_TIMER_IMPLEMENTATION_PLAN.md
- docs/recommendations/KMK_RECS_V0_8_5_READING_SCHEDULE_IMPLEMENTATION_PLAN.md

The standard is living documentation. When later work establishes a repeatable planning, testing, security, documentation, or handoff practice, update the standard and this encyclopedia entry instead of leaving that practice implicit.



