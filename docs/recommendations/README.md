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
| `KMK_RECS_V0_7_10_RECOMMENDATION_QUALITY_STRONG_FIT_ERROR_FIX_PLAN.md` | Fix Source Evaluation Recommendation Quality checks for Strong Fit/Worth Trying sources so non-installed promising sources resolve, temporarily install, probe, and clean up instead of all becoming errors | Implemented in v0.7.10 |
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
| `CROSS_EXTENSION_IDENTITY_FEASIBILITY_RESEARCH.md` | Feasibility of same-manga identity across extensions, rating/favorite sync, and staged rollout options | Current reference |
| `INSTALLED_SOURCE_FIT_FEASIBILITY_RESEARCH.md` | Feasibility of learning which installed sources are useful/reliable recommendation sources | Current reference |

## Latest Implementation Reports Kept At Top Level

| File | Purpose | Status |
| --- | --- | --- |
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


