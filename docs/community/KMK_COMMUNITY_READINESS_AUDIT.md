# Komikku KMK Fork Community Readiness Audit

Date: 2026-06-26

Status: audit / consolidation planning. No implementation is approved by this document.

Scope: KMK-Recs recommendation features, source evaluation, cross-extension matching, best-version workflow, recommendation JSON import/export, OCR branch work, database/backup/sync changes, extension installer/evaluation changes, documentation, and community-sharing readiness.

Primary question: what must be checked, cleaned, rewritten, isolated, or removed before this fork can be shared with the wider Komikku/Mihon/Tachiyomi-style community in a responsible way?

---

## Executive Summary

The fork contains several genuinely useful ideas:

- Taste-based personal recommendations.
- Source priority and source evaluation.
- Cross-extension rating/seen/favorite workflows.
- Loved Manga view and duplicate grouping.
- Recommendation bundle export/import.
- Best-version visual chapter comparison.
- OCR downloaded-text search as a separate APK line.

However, the current state is not community-ready. It is a large experimental fork with many AI-assisted feature passes layered over an already complex Komikku/TachiyomiSY/Mihon codebase. The biggest concern is not one single bug; it is consolidation risk. The fork needs a deliberate stabilization phase before public sharing.

High-level conclusion:

- Keep the ideas.
- Do not present the current code as upstream-ready.
- Freeze new feature work temporarily.
- Audit and harden by subsystem.
- Split personal/experimental features from anything intended for community contribution.
- Treat OCR as a separate experimental branch/APK unless the community explicitly wants it.

Recommended public posture:

- Share as an experimental personal fork only after cleanup.
- Do not claim upstream compatibility.
- Do not submit broad patches upstream until features are broken into small, reviewable, style-aligned changes.

---

## Evidence Reviewed

Documentation reviewed:

- `AGENTS.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/DOCUMENTATION_RULES.md`
- `RECOMMENDATION_IMPLEMENTATION_AUDIT.md`
- `docs/ocr/README.md`
- `docs/ocr/KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_IMPLEMENTATION.md`
- `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_IMPLEMENTATION.md`

Representative source reviewed:

- `app/src/main/java/exh/recs/`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt`
- `app/src/main/java/exh/recs/share/RecommendationBundleImporter.kt`
- `app/src/main/java/exh/recs/share/RecommendationBundleValidator.kt`
- `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt`
- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt`
- `app/src/main/java/exh/ocr/OcrTextRecognizer.kt`
- `app/src/main/java/exh/ocr/OcrIndexService.kt`
- SQLDelight KMK/OCR tables and migrations 46-55.
- `app/build.gradle.kts` and `gradle/libs.versions.toml` for OCR dependency/versioning.

Git/worktree observation:

- `git status --short` shows a very large dirty/untracked working tree with many KMK files, generated APK output, docs, migrations, source changes, tests, and OCR files not represented as a clean feature branch history.

This audit is not a complete line-by-line security review. It is a first consolidation audit to identify major blockers and next review phases.

---

## Current Feature Inventory

### KMK-Recs Core

Implemented areas:

- `manga_taste`, `tag_taste`, `tag_alias`, `recommendation_cache`, disabled sources.
- For You feed under Browse.
- Taste profile scoring from Love/Like/Dislike and tag preferences.
- Recommendation source language filtering and source priority.
- Top Picks row and detail screen.
- Known manga filtering and seen/read markers.

Community-readiness status: promising but requires architecture/style cleanup and stronger public-facing documentation.

### Source Evaluation And Non-Installed Extension Discovery

Implemented areas:

- Metadata-based Sources To Try.
- Like/dislike source preferences.
- Source Evaluation screen.
- Temporary install/probe/score/uninstall workflow.
- Private installer recommendation and Shizuku/current installer handling.
- Quarantine/unsafe extension tables.
- Background WorkManager job.
- Recommendation-quality probe for Strong Fit/Worth Trying sources.

Community-readiness status: high-risk experimental feature. Needs the deepest security, lifecycle, and UX review before public sharing.

### Cross-Extension Matching

Implemented areas:

- Love/Like/Dislike other versions.
- Mark seen other versions.
- Favorite other versions.
- Persistent cross-source link groups.
- Primitive Voyager route args to avoid Android state-save crash.

Community-readiness status: useful and probably closer to shippable than source evaluation, but needs UX polish, link-management UI, and compatibility testing.

### Loved Manga View

Implemented areas:

- Heart action in For You.
- Installed-source-only display.
- Conservative duplicate grouping.
- Cross-source link group priority.

Community-readiness status: useful. Main gaps are management controls, live refresh, backup/restore for view-specific state, and user trust around grouping.

### Best Version / Chapter Quality

Implemented areas:

- Find best version workflow.
- Same-manga candidate search.
- Chapter matching.
- Mid-chapter page sampling.
- Fullscreen zoom preview.
- Migration/copy confirmation.
- Source quality signal persistence.

Community-readiness status: valuable but network-heavy and source-fragile. Needs careful review around headers, source-specific image loading, cancellation, migration safety, and false-match UX.

### Recommendation Bundle Export / Import

Implemented areas:

- JSON bundle schema and validation.
- Export Top Picks/source rows/loved manga.
- Import preview and add selected.
- Source resolution and duplicate checks.

Community-readiness status: promising, but must be reviewed for file-size handling, trust boundaries, malicious JSON behavior, duplicate safety, and user consent around installing missing sources.

### OCR Branch

Implemented areas:

- Separate OCR documentation/APK line.
- ML Kit Latin text recognition dependency.
- Downloaded-page indexing.
- v0.1.1 long-page tiling and smarter search.
- OCR text index stats and cleanup.

Community-readiness status: should remain separate. OCR adds APK size, CPU/battery load, local text privacy concerns, and device-specific memory risk.

---

## Major Blockers Before Community Sharing

### 1. Repository Hygiene Is Not Ready

Severity: High

Observation:

- The worktree contains a very large number of modified and untracked files.
- A generated APK appears in the repo root: `Komikku-v1.13.6-kmk.4.3-debug.apk`.
- `AGENTS.md` says feature work should happen on a feature branch, with formatting and build verification before completion.
- The current state is hard to review as a clean sequence of changes.

Risk:

- Community reviewers cannot tell what changed, why it changed, or which files belong to which feature.
- Generated artifacts may accidentally be committed.
- Upstream merges become painful.

Required cleanup:

- Create a clean feature branch or multiple branches by subsystem.
- Remove APK artifacts from the working tree before public commits.
- Split OCR and KMK-Recs if they are meant to be separate deliverables.
- Create reviewable commits by feature area.
- Add a root-level community README explaining experimental status.

### 2. Architecture Boundaries Are Too Blurry

Severity: High

Observation:

- Much of the feature logic lives under `app/src/main/java/exh/recs/` and directly coordinates UI, source manager behavior, extension manager behavior, network calls, domain writes, and preferences.
- Source evaluation, recommendation scoring, source discovery, and best-version migration are all in the same broad feature namespace.
- Some new behavior modifies upstream-facing files across backup, sync, source manager, extension loader/installer, browse UI, manga UI, settings, notifications, and main activity.

Risk:

- Hard to upstream or maintain.
- Hard to reason about lifecycle and side effects.
- Bugs in experimental features can destabilize unrelated browse/source behavior.

Required cleanup:

- Define subsystem boundaries:
  - recommendations/taste
  - source evaluation
  - cross-extension matching
  - best-version comparison
  - OCR
  - import/export
- Move reusable pure logic into domain-style or pure helper classes.
- Keep Android/UI/extension manager side effects thin and explicit.
- Avoid large screen models that orchestrate many unrelated responsibilities.

### 3. Source Evaluation Is Security- And Stability-Sensitive

Severity: Critical

Observation:

- `SourceEvaluationRunner` temporarily installs available extensions, probes popular/latest/search, scores, and tries to clean up.
- The runner supports installer overrides and private/Shizuku/current installer modes.
- Cleanup behavior depends on whether an extension is private/shared/pre-existing and whether prompt-heavy cleanup is allowed.
- `SourceEvaluationJob` uses in-memory `SourceEvaluationJobState.pendingCandidates` and `pendingOptions`; if the process restarts, the job fails with state-lost messaging.
- Crash quarantine records probe markers before risky phases.

Risks:

- Extension installation/uninstallation is one of the most sensitive surfaces in the app.
- A community user may not understand that evaluation can install code, run extension source methods, and then uninstall.
- Non-private/system-installed temporary extensions may remain installed if cleanup requires prompts.
- Process death can lose in-memory queue state.
- Extensions can crash native/JIT/network layers, as seen previously with SIGSEGV/stack overflow crashes.

Required cleanup:

- Add a very explicit user consent screen before source evaluation.
- Default to the safest installer mode and document limitations.
- Persist job inputs or prevent WorkManager from starting when state cannot survive process death.
- Make cleanup outcomes visible and actionable after every run.
- Add a post-run cleanup queue for extensions that could not be silently removed.
- Add a security/privacy document explaining what source evaluation does.
- Consider keeping this feature behind an experimental toggle.

### 4. OCR Must Remain Separate Until It Has A Privacy And Device-Safety Review

Severity: High

Observation:

- `app/build.gradle.kts` includes `implementation(libs.mlkit.text.recognition)` and versionCode `81 // KMK OCR v0.1.1`.
- OCR stores dialogue text from downloaded manga in `ocr_indexed_page`.
- `OcrTextRecognizer` still reads each page stream into memory with `readBytes()` before decoding.
- v0.1.1 improves tiling and status semantics, but image/OCR work is inherently CPU/memory-heavy.
- OCR text is private local user data.

Risks:

- Larger APK and dependency concerns.
- Device-specific memory problems on large pages or low-RAM devices.
- Local text index can contain sensitive reading content.
- Users may not understand that OCR stores searchable text even though it does not store images.

Required cleanup:

- Keep OCR in a separate branch/APK unless explicitly accepted into main fork.
- Add an OCR privacy note in-app before indexing.
- Add storage size and clear controls, which v0.1.1 already started.
- Add stronger diagnostics for pages that decode/OCR to empty due to OOM or model failure.
- Consider streaming/temp-file decoding instead of `readBytes()` for very large pages.
- Add device testing on low-memory phones and tablets.

### 5. Database And Backup Changes Need A Dedicated Compatibility Review

Severity: High

Observation:

- New migrations 46-55 add many SQLDelight tables and columns:
  - taste profile tables
  - recommendation cache
  - source evaluation
  - unsafe/quarantine tables
  - cross-source link groups
  - source recommendation fit
  - source quality signal
  - OCR index table and OCR status columns
- Backup proto fields were extended for taste data and cross-source links.
- OCR text is intentionally not backed up/exported.

Risks:

- Migration collisions with upstream Komikku/Mihon changes.
- Missing-table crashes were previously encountered.
- Proto field collisions if upstream or fork adds fields in the same range.
- Sync/restore behavior may diverge from backup behavior.
- Some preference-backed state, such as seen manga or source priority, may not be equally represented in backup.

Required cleanup:

- Create a migration compatibility matrix from a clean upstream DB through every KMK migration.
- Add migration tests where possible.
- Document reserved proto ranges and conflicts.
- Confirm backup/restore/sync for every user-visible state:
  - ratings
  - tag tastes
  - source disabled/liked/disliked
  - seen/read markers
  - cross-source links
  - source priority/order/languages
  - source evaluation data, if intended
  - source quality signals
- Decide which tables are cache-only and should never sync/backup.

### 6. UI/UX Is Feature-Rich But Not Yet Komikku-Polished

Severity: Medium-High

Observation:

- Features are spread across Browse, manga detail, recommendation settings, source evaluation, extension pages, data settings, More/OCR, and What's New.
- Several workflows were added quickly in response to bugs and user testing.
- The app now has many concepts: Like, Love, Dislike, Seen, Favorite other versions, Best Version, Top Picks, Sources To Try, Source Evaluation, Recommendation Quality, hidden installed evaluations, explicit source block, OCR search.

Risks:

- Users may not understand the difference between source quality, recommendation quality, source preference, manga rating, and seen/read state.
- Experimental controls can overwhelm regular browse settings.
- Some UI may use hardcoded English labels or not fully match Komikku's preference-screen patterns.

Required cleanup:

- Create a UX map of every entry point and user action.
- Decide which features are mainline vs advanced/experimental.
- Move dangerous or advanced tools under clearly labeled advanced/experimental sections.
- Ensure all user-facing strings are in `i18n-kmk` base resources.
- Remove development-only notes from What's New.
- Align settings rows, dialogs, chips, and screens with existing Komikku/Mihon patterns.

### 7. Error Handling Is Improved But Still Inconsistent

Severity: Medium-High

Examples:

- `SourceEvaluationRunner.recordExtensionError()` writes DB records in `scope.launch { ... }` rather than awaiting the write in the current flow. This can race with state updates or cancellation.
- `SourceEvaluationJob` cannot recover job inputs after process death because candidates/options are in memory.
- OCR catches stream read exceptions and returns empty text, which may hide real decode/file errors from the user.
- OCR catches some `OutOfMemoryError` paths and records empty rather than failed in some cases.
- Best-version preview catches per-candidate network errors, but source-specific headers/image loading issues remain deferred.

Risks:

- Errors become invisible or misleading.
- Users see empty/no-results states instead of actionable diagnostics.
- Background work can report failure without enough context to fix it.

Required cleanup:

- Standardize error/result models per subsystem.
- Distinguish user-cancelled, network timeout, source unsupported, extension crash, decode/OCR empty, and internal app bug.
- Avoid broad catch blocks that convert serious failures into empty success states.
- Ensure every long-running worker has durable inputs or an honest foreground-only model.

### 8. AI-Generated Style And Documentation Artifacts Need Cleanup

Severity: Medium

Observation:

- Many docs are extremely detailed and useful for handoff, but not all are suitable for community-facing documentation.
- Some files contain mojibake such as `â€”`, `â†’`, `Â·`, or `Ã—` from encoding issues.
- Inline comments like `// KMK --> v0.x` are helpful for fork tracking but can become noisy.
- Some plans/implementation reports are historical rather than current.

Risks:

- The code and docs visibly read as iterative AI output.
- Community reviewers may lose confidence even where behavior is useful.
- Important current facts can be buried under dozens of plan documents.

Required cleanup:

- Keep internal implementation notes, but create a concise public-facing feature guide.
- Fix mojibake across docs and UI strings.
- Archive or collapse old plan files further.
- Replace excessive comments with clear names and normal local conventions.
- Add architecture diagrams or short subsystem summaries rather than giant chronological logs.

### 9. Test Coverage Is Good For Pure Helpers But Incomplete For Integration Risk

Severity: Medium

Observation:

- There are many unit tests for pure helpers: scoring, ordering, matching, grouping, OCR ranker/tile planner, etc.
- The docs report successful app unit tests and debug builds for several versions.
- Integration-heavy behavior is harder to test: extension install/uninstall, WorkManager, source network behavior, migration/restore, reader migration, image loading, OCR on real pages.

Risks:

- Pure tests pass while device behavior still fails.
- Extension behavior varies across repositories and Android versions.
- OCR and best-version features depend heavily on real downloaded pages/source image behavior.

Required cleanup:

- Add a test matrix document:
  - Android versions
  - phones/tablets
  - debug/release builds
  - private/current/Shizuku installer modes
  - offline/online transitions
  - clean install/update/restore
- Add migration/backup round-trip tests for all custom data.
- Add manual QA scripts for extension evaluation and best-version flows.
- Prefer release-build tablet testing before community handoff.

---

## Community Contribution Fit

### Likely Not Suitable For Direct Upstream Submission

These should not be submitted upstream as-is:

- Full source evaluation with temporary extension install/uninstall.
- OCR branch with ML Kit dependency.
- Entire KMK-Recs feature set as one large patch.
- Best-version migration workflow in its current experimental form.

Why:

- Too broad.
- Too many policy/security implications.
- Too difficult to review.
- Too opinionated/personalized.

### Possible Candidates For Smaller Community Features

With cleanup, these may be easier to share:

- Conservative duplicate grouping for Loved Manga.
- Source priority display/order fixes.
- Safer cross-extension matching route-state fix.
- Recommendation bundle import/export as a standalone opt-in feature.
- Best-version page sampler/chapter matcher as pure helpers, if separated from migration UI.
- OCR as an optional experimental branch, not main app.

### Must Be Presented As Experimental

These can be shared, but should be labeled experimental:

- Source Evaluation.
- Non-installed extension recommendation.
- Recommendation-quality probing.
- Best Version visual comparison.
- OCR downloaded-text search.

---

## Subsystem Audit Checklist

### Recommendation Core

Needs review:

- Does For You respect source priority visually and functionally?
- Are caches invalidated for every relevant preference/taste change?
- Are rated/seen/favorite/library filters consistent?
- Are result rows reproducible and explainable?
- Are blocked tags only post-fetch, and is that documented honestly?
- Are source/network failures visible without spamming users?

Outcome target:

- Stable enough for personal/community beta once UI and docs are simplified.

### Source Evaluation

Needs review:

- Every install/uninstall path.
- Private installer behavior.
- Shizuku/current installer warnings.
- Process-death behavior.
- Cleanup failure behavior.
- Unsafe/quarantine reset behavior.
- Reassessment cursor behavior.
- Recommendation-quality probe behavior for installed and non-installed sources.
- Network and extension crash resilience.

Outcome target:

- Keep behind an experimental setting until proven across devices.

### Cross-Extension Matching

Needs review:

- Route state across process death/rotation.
- Alternate title query quality.
- Default selected vs default unselected preference.
- Link group creation and duplicate management.
- Link group delete/inspect UI.
- Exact behavior for seen/read vs rating vs favorite.

Outcome target:

- Could become a stable advanced feature after polish.

### Best Version

Needs review:

- Candidate false positives.
- Source-specific image headers/referrers.
- Cancellation and stale state.
- Migration/copy safety.
- Quality signal usefulness.
- Fullscreen preview behavior on rotation/small screens.

Outcome target:

- Experimental but useful. Should not auto-migrate; user confirmation must remain central.

### Recommendation Bundle Import/Export

Needs review:

- JSON schema documentation.
- Malicious/bad JSON behavior.
- File size and memory handling.
- Source resolution mismatch handling.
- User consent before adding many manga.
- Missing extension install prompts.

Outcome target:

- Potentially community-shareable after security review.

### OCR

Needs review:

- Memory use with very large downloaded pages.
- OCR status semantics.
- Storage size reporting.
- Clear/delete behavior.
- No accidental backup/sync/export of OCR text.
- Latin-only limitations explained.
- Release APK size impact.

Outcome target:

- Separate optional APK/branch, not default.

---

## Recommended Consolidation Roadmap

### Phase 0: Freeze New Features

Goal: stop the feature expansion loop long enough to stabilize.

Actions:

- No new feature implementation until audit follow-ups are prioritized.
- Only allow crash fixes, data-loss fixes, security fixes, and documentation corrections.

### Phase 1: Repository And Documentation Hygiene

Actions:

- Remove APK artifacts from tracked/untracked source tree.
- Create a clean branch for the current fork snapshot.
- Create one public-facing `README-KMK-FORK.md` summarizing features and experimental status.
- Keep detailed AI/handoff docs under `docs/internal/` or equivalent.
- Fix mojibake in current docs and key UI strings.
- Update versioning docs so KMK-Recs and KMK-OCR are clearly separate.

### Phase 2: Feature Classification

Classify every feature as one of:

- Stable personal feature.
- Experimental personal feature.
- Community candidate after cleanup.
- OCR-only branch feature.
- Internal diagnostic/developer feature.
- Remove/defer.

Create a matrix with:

- feature name
- entry points
- tables/preferences touched
- backup/sync behavior
- network behavior
- security/privacy risk
- test coverage
- status

### Phase 3: Security And Privacy Review

Focus areas:

- Source evaluation installer/cleanup behavior.
- Shizuku/current/private installer modes.
- Extension quarantine and known unsafe seeds.
- OCR text storage and deletion.
- Recommendation JSON import/export.
- Backup/sync of taste/link data.

Output:

- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`
- Include user-facing warnings that should be added in-app.

### Phase 4: Database, Backup, And Migration Review

Focus areas:

- Migrations 46-55.
- SQLDelight schema consistency.
- Proto field ranges.
- Backup/restore/sync coverage.
- Cache-only vs durable user data classification.

Output:

- `docs/database/KMK_DATABASE_AND_BACKUP_AUDIT.md`

### Phase 5: Architecture Refactor Plan

Focus areas:

- Reduce large app-layer orchestration.
- Move pure logic into testable helpers/domain.
- Make background jobs durable or intentionally foreground-only.
- Clarify feature flags and experimental toggles.
- Separate OCR branch/dependency.

Output:

- `docs/architecture/KMK_ARCHITECTURE_CONSOLIDATION_PLAN.md`

### Phase 6: UX Simplification Plan

Focus areas:

- Settings organization.
- Advanced/experimental feature placement.
- User-facing terminology.
- What's New quality.
- Warnings/empty states/errors.

Output:

- `docs/ux/KMK_UX_CONSOLIDATION_PLAN.md`

### Phase 7: Test And Release Checklist

Focus areas:

- Unit tests.
- Migration tests.
- Backup/restore tests.
- Manual device QA.
- Release build performance.
- Tablet/phone coverage.

Output:

- `docs/testing/KMK_TEST_AND_RELEASE_CHECKLIST.md`

---

## Immediate High-Priority Follow-Ups

These should be handled before any public sharing:

1. Clean the worktree and remove generated APK artifacts from source-control scope.
2. Decide whether OCR remains a separate branch/APK. Recommendation: yes.
3. Create a feature classification matrix.
4. Review source evaluation installer/cleanup behavior as a security-sensitive subsystem.
5. Review all custom SQL migrations and backup/proto fields.
6. Fix mojibake in docs and any visible UI strings.
7. Create a public-facing experimental fork README with honest warnings.
8. Run `spotlessApply`, `spotlessCheck`, and a debug/release build after cleanup.
9. Do not ask Claude to "make it community-ready" in one prompt. Give Claude one audit/refactor phase at a time.

---

## Suggested Next Prompt Direction

The next Claude/Codex task should not implement code yet. It should create the feature classification matrix described in Phase 2.

Recommended next artifact:

```text
docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md
```

That matrix should enumerate each KMK feature and record:

- feature area
- source files
- entry points
- database tables
- preferences
- backup/sync behavior
- network behavior
- security/privacy risk
- tests
- known gaps
- community readiness verdict

Only after that matrix exists should code cleanup begin.

