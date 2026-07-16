# KMK Fork Community Consolidation Phases

Date: 2026-06-26 (updated 2026-06-29 -- v0.7.34 reconciliation note added)

Status: master phased consolidation plan. This document organizes the cleanup and community-readiness work. It does not approve code changes by itself.

**2026-06-29 update:** Phases 0-7 were completed through approximately v0.7.20. A reconciliation and publishing-readiness audit was performed on 2026-06-29 to bring all documentation current with code through KMK-Recs v0.7.34. See:
- `docs/community/KMK_FULL_RECONCILIATION_AUDIT.md` -- full audit findings
- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION_PLAN.md` -- reconciliation implementation plan
- `docs/recommendations/CURRENT_STATE.md` -- now reflects v0.7.34

Current private beta readiness as of 2026-06-29: applicationId is `app.komikku` (same as upstream) -- this is a blocker for community distribution unless changed. All documentation, tests, and code are in the best state since the project began.

Related audit:

- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`

Detailed phase plans and handoff documents created:

- `docs/community/KMK_CONSOLIDATION_VALIDATION_AND_HANDOFF.md`
- `docs/community/KMK_PHASE_0_1_CLAUDE_PROMPT.md`
- `docs/community/KMK_PHASE_0_1_SNAPSHOT_AND_FEATURE_CLASSIFICATION_PLAN.md`
- `docs/community/KMK_PHASE_2_REPOSITORY_AND_DOCUMENTATION_HYGIENE_PLAN.md`
- `docs/community/KMK_PHASE_3_4_DATABASE_SECURITY_PRIVACY_AUDIT_PLAN.md`
- `docs/community/KMK_PHASE_5_SOURCE_EVALUATION_HARDENING_IMPLEMENTATION_PLAN.md`
- `docs/community/KMK_PHASE_6_7_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_PLAN.md`
- `docs/community/KMK_PHASE_8_9_BUNDLE_AND_OCR_HARDENING_PLAN.md`
- `docs/community/KMK_PHASE_10_11_ARCHITECTURE_STYLE_TEST_RELEASE_PLAN.md`
- `docs/community/KMK_PHASE_12_PUBLIC_SHARING_PACKAGE_PLAN.md`

Phase execution outputs created:

- `docs/community/KMK_CONSOLIDATION_SNAPSHOT.md`
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md`
- `docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md`
- `docs/community/KMK_PUBLIC_README_DRAFT.md`
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`
- `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md`

Purpose:

This fork has grown into a large experimental Komikku feature set with KMK-Recs, source evaluation, cross-extension matching, best-version tools, recommendation bundle import/export, and a separate OCR APK line. The next stage should not be more feature expansion. The next stage should be consolidation: document, classify, harden, simplify, and then only later decide what is worth sharing publicly.

This document defines the phases. Each phase should later receive its own detailed implementation or audit plan before Claude Code changes anything.

---

## Operating Rules For All Phases

1. Do not assume Komikku's expected format, architecture, UI pattern, versioning pattern, contribution style, string location, build convention, or release practice. Verify it first from current repo files, existing Komikku code patterns, official Komikku documentation, or current upstream/community references where local evidence is insufficient.
2. If local KMK code conflicts with official/current Komikku style, document the conflict before proposing changes.
3. Do not implement new user-facing features during consolidation unless the user explicitly approves them.
4. Each phase must produce or update markdown documentation before code changes are considered complete.
5. Each phase should be small enough for Claude Code to execute without mixing concerns.
6. Each phase should end with a clear summary of:
   - what was checked,
   - what was changed,
   - what remains risky,
   - what tests were run,
   - what should happen next.
7. KMK-Recs and KMK-OCR must remain documented as separate lines unless the user explicitly decides otherwise.
8. OCR must remain treated as experimental and separate until privacy, APK size, and device-memory risks are reviewed.
9. Source Evaluation must remain treated as experimental/security-sensitive until installer and cleanup behavior are reviewed.
10. Avoid broad prompts like `make it community ready`. Use one phase at a time.

---

## Recommended Workflow

Use this flow for each phase:

1. Codex creates or expands a phase-specific plan.
2. User reviews and approves the summary.
3. Codex provides a Claude Code prompt for that phase only.
4. Claude implements or audits that phase.
5. Codex inspects Claude's result.
6. User approves moving to the next phase.

This keeps the work from drifting and prevents Claude from mixing unrelated cleanup tasks.

---

## Phase 0 - Freeze And Snapshot

Status: recommended first action.

Goal:

Stop feature expansion and create a stable reference point for the current fork state.

Why this matters:

The current worktree is large and dirty. Many features, migrations, docs, tests, and APK artifacts exist together. Before cleanup, we need to know what snapshot we are cleaning.

Main tasks:

- Freeze new feature work except crash, data-loss, security, and documentation fixes.
- Record current app state, current APKs, current feature versions, and known branches.
- Identify generated artifacts that should not be committed, such as debug APKs in the source tree.
- Confirm whether the current working tree should become one snapshot branch or be split into separate branches.
- Decide whether OCR continues as a separate branch/APK. Current recommendation: yes.

Primary outputs:

- `docs/community/KMK_CONSOLIDATION_SNAPSHOT.md`
- Optional: `docs/community/KMK_BRANCHING_AND_RELEASE_LINES.md`

Claude should not do major code cleanup in this phase. This is a documentation and repo-state phase.

Acceptance criteria:

- Current feature versions are recorded.
- Current dirty/untracked categories are listed.
- OCR and KMK-Recs release lines are clearly distinguished.
- Generated artifacts to remove or ignore are identified.

---

## Phase 1 - Feature Classification Matrix

Status: highest-priority next documentation phase.

Goal:

List every KMK feature and classify its community readiness.

Why this matters:

Before refactoring anything, we need to know what exists, where it lives, what data it touches, what risk it carries, and whether it should be stable, experimental, separated, or removed.

Main tasks:

- Build a matrix of all KMK feature areas:
  - For You recommendations.
  - Top Picks.
  - manga rating/taste profile.
  - tag preferences and aliases.
  - source priority/language filtering.
  - Sources To Try.
  - source like/dislike.
  - explicit source filter.
  - source evaluation.
  - recommendation-quality probe.
  - cross-extension rating/seen/favorite matching.
  - cross-source link groups.
  - Loved Manga view.
  - best-version/chapter-quality workflow.
  - recommendation bundle export/import.
  - OCR downloaded-text search.
  - backup/restore/sync additions.
  - extension install/uninstall changes.
  - KMK What's New and release notes.
- For each feature, identify:
  - entry points,
  - main files,
  - database tables,
  - preferences,
  - backup/sync behavior,
  - network behavior,
  - install/uninstall behavior,
  - privacy/security risk,
  - tests,
  - known gaps,
  - recommended status.

Classification labels:

- Stable personal feature.
- Experimental personal feature.
- Community candidate after cleanup.
- OCR-only branch feature.
- Internal diagnostic/developer feature.
- Remove/defer.

Primary output:

- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md`

Acceptance criteria:

- Every major KMK feature is represented.
- No feature is marked community-ready without evidence.
- Source Evaluation and OCR are explicitly labeled experimental unless proven otherwise.
- Matrix can be used as the index for all later phases.

---

## Phase 2 - Repository Hygiene And Documentation Cleanup

Goal:

Make the repository understandable and shareable before code refactors begin.

Why this matters:

The current repo contains many docs, implementation notes, generated artifacts, old plans, and version references. This is useful internally but confusing publicly.

Main tasks:

- Remove or move generated APK artifacts out of source-control scope.
- Add/update `.gitignore` if needed for APK outputs and local artifacts.
- Create a public-facing fork README.
- Move old AI handoff/planning documents into an internal/history area if they are not current.
- Keep current docs discoverable.
- Fix stale version references.
- Fix mojibake in docs and visible strings where safe.
- Separate KMK-Recs docs from KMK-OCR docs clearly.
- Confirm `docs/recommendations/README.md`, `docs/ocr/README.md`, and community docs point to the right current files.

Primary outputs:

- `README-KMK-FORK.md` or `docs/community/KMK_PUBLIC_README_DRAFT.md`
- Updated `docs/community/` index if needed.
- Updated docs indexes.

Acceptance criteria:

- A human can understand what the fork does without reading every implementation note.
- Experimental features are labeled honestly.
- Old plans are not mistaken for current implementation status.
- Generated artifacts are identified or removed from source-control scope.

---

## Phase 3 - Database, Backup, Sync, And Migration Audit

Goal:

Verify that all custom tables, migrations, backup fields, and sync behavior are safe, documented, and internally consistent.

Why this matters:

The fork adds many durable data surfaces. Bad migrations or backup mismatches can cause silent data loss, crashes, or future upstream merge conflicts.

Main tasks:

- Audit migrations 46-55.
- Compare SQLDelight `.sq` schema with migration files.
- Identify cache-only vs durable-user-data tables.
- Audit backup proto fields and reserved field ranges.
- Confirm backup/restore coverage for:
  - manga tastes,
  - tag tastes,
  - tag aliases,
  - disabled recommendation sources,
  - cross-source link groups,
  - seen/read state,
  - source priority/languages,
  - source like/dislike,
  - source evaluation data,
  - recommendation fit data,
  - source quality signals,
  - OCR text index.
- Decide which data should never be backed up, especially OCR text and cache data.
- Review sync merge behavior.
- Add or recommend tests for migration and backup round trips.

Primary output:

- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`

Acceptance criteria:

- Every custom table is classified.
- Every backup/sync-supported feature is documented.
- Every unsupported state is intentionally unsupported, not accidentally missing.
- Migration/proto collision risks are documented.

---

## Phase 4 - Security And Privacy Review

Goal:

Review features that install code, handle extension repositories, store private user data, import JSON, or run heavy local analysis.

Why this matters:

Community users need to understand and trust what the app is doing. Source Evaluation and OCR are powerful but sensitive.

Main tasks:

- Review Source Evaluation temporary install/probe/uninstall behavior.
- Review Private, Current, and Shizuku installer paths.
- Review cleanup outcomes and prompt-required leftovers.
- Review unsafe extension quarantine and known unsafe seeds.
- Review explicit porn/hentai filtering limitations.
- Review recommendation bundle import/export trust boundaries.
- Review OCR text storage and deletion.
- Review local logs and diagnostics for possible sensitive data leakage.
- Determine which features need in-app warnings or experimental toggles.
- Decide whether any feature should be disabled by default for public builds.

Primary output:

- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`

Acceptance criteria:

- Every security-sensitive workflow has risks and mitigations documented.
- Source Evaluation consent/warning requirements are clear.
- OCR privacy expectations are clear.
- JSON import/export trust limits are clear.

---

## Phase 5 - Source Evaluation Hardening Plan

Goal:

Turn Source Evaluation from a personal experimental tool into a safer, better-bounded experimental feature.

Why this matters:

Source Evaluation is the riskiest feature because it can temporarily install extensions and execute source methods.

Main tasks:

- Review Komikku official/current extension installer UX and implementation patterns before changing installer-mode selection or warnings.
- Make WorkManager state durable or avoid background execution when state cannot survive process death.
- Ensure temporary extensions are cleaned up reliably or listed for manual cleanup.
- Make cleanup failures visible and actionable.
- Review crash quarantine logic and unsafe-source recovery.
- Review recommendation-quality probe install/probe/uninstall path.
- Add clearer status and error diagnostics.
- Ensure installed extensions never appear as non-installed recommendations unless intentionally shown.
- Ensure evaluation batches can continue beyond the first 10/25/50/100 without losing the cursor.

Primary output before code:

- `docs/recommendations/KMK_RECS_SOURCE_EVALUATION_COMMUNITY_HARDENING_PLAN.md`

Acceptance criteria after eventual implementation:

- Users understand exactly what evaluation will do before it starts.
- Process death is handled honestly.
- Prompt-heavy cleanup is not hidden.
- Evaluation failures do not silently poison future state.

---

## Phase 6 - Recommendation Core And UX Consolidation

Status: implemented and reconciled through KMK-Recs v0.7.34 as of 2026-06-29. Settings reorganization, terminology cleanup, Loved Manga sort/no-duplicates feedback, Source Evaluation evidence/verdict i18n, Best Version cancel/fullscreen checks, quality-signal backup/sync, link-group management UI, live Loved Manga updates, Source Evaluation polish, and the v0.7.29-v0.7.34 polish phases are documented. Remaining open items are tracked in `docs/recommendations/NEXT_WORK.md`.

Goal:

Make the personal recommendation experience coherent, understandable, and closer to Komikku's UI patterns.

Why this matters:

The app now has many concepts: rating, seen, source preference, source evaluation, recommendation quality, Top Picks, Loved Manga, source priority, tags, known-manga filtering. These need to feel like one system.

Main tasks:

- Map every recommendation entry point.
- Compare Recommendation Settings against current Komikku settings/preference UI patterns before simplifying organization.
- Separate normal user controls from experimental/advanced controls.
- Review terminology:
  - source quality vs recommendation quality,
  - like source vs like manga,
  - seen vs read vs rated,
  - Top Picks vs source rows.
- Review For You result ordering, source status display, empty states, and cache behavior.
- Review known-manga filtering and seen/read behavior.
- Review tag preferences and blocked tags messaging.
- Ensure strings are in `i18n-kmk` base resources.

Primary output before code:

- `docs/ux/KMK_RECS_UX_CONSOLIDATION_PLAN.md`

Acceptance criteria after eventual implementation:

- A new user can understand the recommendation controls without reading docs.
- Dangerous/experimental settings are not mixed with normal daily controls.
- UI follows existing Komikku preference and screen patterns.

---

## Phase 7 - Cross-Extension Matching, Loved Manga, And Best Version Consolidation

Goal:

Review and harden workflows that try to identify equivalent manga across extensions.

Why this matters:

Cross-extension identity is useful but inherently uncertain. The app must avoid pretending it can perfectly know same-manga identity.

Main tasks:

- Review same-manga query strategy.
- Review selected-by-default vs unselected-by-default preference behavior.
- Review link group creation and grouping rules.
- Add or plan UI for viewing/deleting link groups.
- Review Loved Manga duplicate grouping and installed-source filtering.
- Review Best Version candidate false positives.
- Review chapter matching and page sampling.
- Review migration/copy confirmation behavior.
- Review source quality signal persistence and whether it should influence future recommendations.

Primary output before code:

- `docs/recommendations/KMK_RECS_CROSS_EXTENSION_AND_BEST_VERSION_CONSOLIDATION_PLAN.md`

Acceptance criteria after eventual implementation:

- User confirmation remains central.
- Duplicate grouping is conservative and explainable.
- Link groups can be inspected or repaired.
- Best Version never silently migrates without clear confirmation.

---

## Phase 8 - Recommendation Bundle Import/Export Review

Goal:

Harden JSON recommendation sharing before community use.

Why this matters:

Importing user-provided files is a trust boundary. Even simple JSON can cause UX confusion, memory pressure, unexpected installs, duplicate library entries, or unsafe assumptions.

Main tasks:

- Review schema and validation limits.
- Review file reading and memory behavior.
- Review source resolution fallbacks.
- Review missing-source install prompts.
- Review duplicate detection.
- Review user consent before adding many manga.
- Decide whether exported bundles should include scores/reasons/source metadata.
- Create a public schema description.

Primary output before code:

- `docs/recommendations/KMK_RECS_RECOMMENDATION_BUNDLE_HARDENING_PLAN.md`

Acceptance criteria after eventual implementation:

- Malformed files fail safely.
- Large files are bounded.
- Users know what will be added before anything is added.
- Missing extensions are never installed without explicit action.

---

## Phase 9 - OCR Isolation And Privacy Hardening

Goal:

Keep OCR useful while making it safe, honest, and clearly separate from the normal app line.

Why this matters:

OCR stores text from downloaded manga and adds a heavy ML dependency. It should not quietly become part of the normal recommendation APK.

Main tasks:

- Confirm OCR branch/APK separation.
- Review ML Kit dependency impact on APK size.
- Review OCR text storage, deletion, and backup exclusion.
- Review memory behavior for huge pages and archives.
- Improve diagnostics for empty OCR results vs failed OCR results.
- Review index limits and battery/CPU messaging.
- Add in-app privacy explanation before indexing.
- Decide whether OCR should support optional language packs later.

Primary output before code:

- `docs/ocr/KMK_OCR_PRIVACY_AND_RELEASE_HARDENING_PLAN.md`

Acceptance criteria after eventual implementation:

- OCR cannot be mistaken for a default KMK-Recs feature.
- Users know that text is stored locally and can delete it.
- Large-device and low-memory risks are documented.

---

## Phase 10 - Architecture And Code Style Refactor

Goal:

Reduce AI-ish code shape and align the implementation more closely with Komikku/Mihon/Tachiyomi style.

Why this matters:

Even working code can be hard to review if it looks bolted on, over-commented, or architecturally out of place.

Main tasks:

- Identify oversized screen models.
- Identify app-layer classes doing domain/data/extension orchestration directly.
- Extract pure logic where it helps.
- Reduce noisy comments while preserving important KMK fork markers.
- Standardize error/result models.
- Align coroutine usage with existing patterns.
- Avoid creating new global singleton state unless necessary.
- Review use of broad `catch (Exception)` or `catch (Throwable)`.
- Confirm `KMR`/`i18n-kmk` usage for KMK strings.
- Run formatting after changes.

Primary output before code:

- `docs/architecture/KMK_ARCHITECTURE_AND_STYLE_REFACTOR_PLAN.md`

Acceptance criteria after eventual implementation:

- Code is easier for a Komikku/Mihon reviewer to follow.
- Feature boundaries are clearer.
- Comments explain why, not every step of what.
- Formatting and lint pass.

---

## Phase 11 - Testing, Device QA, And Release Readiness

Goal:

Create a repeatable confidence process before sharing builds publicly.

Why this matters:

Pure unit tests are helpful, but this fork depends heavily on Android lifecycle, extension behavior, network behavior, WorkManager, backup/restore, migration, and reader flows.

Main tasks:

- Create test matrix for:
  - debug and release builds,
  - phone and tablet,
  - clean install and update install,
  - backup and restore,
  - sync if used,
  - source evaluation private/current/Shizuku modes,
  - offline and reconnect behavior,
  - OCR with small/large downloaded chapters,
  - best-version migration/cancel/copy flows.
- Identify automated tests to add:
  - migration tests,
  - backup round-trip tests,
  - source preference tests,
  - JSON import tests,
  - OCR index status tests.
- Define minimum commands before release:
  - `spotlessApply`
  - `spotlessCheck`
  - `testDebugUnitTest` or appropriate test task
  - `assembleDebug`
  - release build if sharing outside personal testing.

Primary output:

- `docs/testing/KMK_TEST_AND_RELEASE_CHECKLIST.md`

Acceptance criteria:

- Every public APK has documented tests/builds.
- Known untested areas are disclosed.
- Release builds are preferred for community testing.

---

## Phase 12 - Public Sharing Package

Goal:

Prepare the fork for responsible community sharing after the earlier audits and hardening passes.

Why this matters:

Sharing does not just mean uploading an APK. It needs honest docs, warnings, install instructions, known limitations, and a way for users to report issues.

Main tasks:

- Check current Komikku public release/readme/changelog style before writing the public feature summary.
- Write install/update instructions.
- Write data/backup warning.
- Write experimental feature warnings.
- Write OCR branch warning if OCR is shared.
- Write known issues.
- Write issue-report template.
- Decide release naming:
  - KMK-Recs build line.
  - KMK-OCR build line.
- Remove internal-only AI workflow notes from public-facing docs.

Primary outputs:

- `README-KMK-FORK.md`
- `docs/community/KMK_PUBLIC_RELEASE_NOTES_TEMPLATE.md`
- `docs/community/KMK_ISSUE_REPORT_TEMPLATE.md`

Acceptance criteria:

- A community user can understand what they are installing.
- Experimental risks are disclosed.
- Internal planning docs are not the first thing users see.

---

## Suggested Phase Grouping For Claude

The phases should not all be given to Claude at once.

Recommended grouping:

### Claude Pass A

- Phase 0: Freeze and snapshot.
- Phase 1: Feature classification matrix.

Reason: documentation-only, creates the index for everything else.

### Claude Pass B

- Phase 2: Repository and documentation hygiene.

Reason: cleanup should happen after the matrix exists.

### Claude Pass C

- Phase 3: Database/backup/sync audit.
- Phase 4: Security/privacy review.

Reason: these are high-risk audits that inform later code changes.

### Claude Pass D

- Phase 5: Source Evaluation hardening plan.

Reason: highest-risk implementation area; should be isolated.

### Claude Pass E

- Phase 6: Recommendation core and UX consolidation.
- Phase 7: Cross-extension/Loved Manga/Best Version consolidation.

Reason: user-facing recommendation behavior should be reviewed together, but implementation can still be split.

### Claude Pass F

- Phase 8: Recommendation bundle hardening.
- Phase 9: OCR isolation/privacy hardening.

Reason: both involve trust/privacy boundaries, but code should remain separate.

### Claude Pass G

- Phase 10: Architecture/style refactor.
- Phase 11: Testing/release readiness.

Reason: style refactor should happen once security/data decisions are known.

### Claude Pass H

- Phase 12: Public sharing package.

Reason: public docs should be last, after we know what is actually stable.

---

## Immediate Next Step

The next detailed document should be Phase 0 and Phase 1 combined, because they are documentation-first and unblock everything else.

Recommended next file:

```text
docs/community/KMK_PHASE_0_1_SNAPSHOT_AND_FEATURE_CLASSIFICATION_PLAN.md
```

That plan should instruct Claude to:

- inspect current docs and source files,
- record the current repo/app state,
- produce `KMK_CONSOLIDATION_SNAPSHOT.md`,
- produce `KMK_FEATURE_CLASSIFICATION_MATRIX.md`,
- avoid app code changes,
- avoid deleting files unless explicitly approved,
- report any uncertainty rather than guessing.





## Baseline Scope

All audits, plans, and implementation passes must evaluate the full KMK fork delta against the current/latest Komikku baseline, not only the most recent KMK change set.

Claude must treat the scope as: everything added, changed, removed, or behaviorally affected since the latest/current Komikku upstream baseline available in this repository or verified from current upstream references. This includes source code, migrations, database schema, backup/sync behavior, preferences, UI strings, settings, extension handling, docs, Gradle/dependencies, tests, generated artifacts, and release/version naming.

Before making conclusions, Claude must identify what baseline it used:

- local upstream/latest Komikku commit or branch, if available,
- current app version/build metadata in this repo,
- official/current Komikku reference if local evidence is insufficient.

If Claude cannot determine the exact upstream baseline, it must say so clearly and proceed by comparing KMK-marked and newly added fork files against the nearest local Komikku/Mihon/TachiyomiSY patterns. Do not narrow the audit to only the latest KMK version unless the user explicitly asks for that.





