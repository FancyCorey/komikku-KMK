# KMK Phase 12 Public Sharing Package Plan

Date: 2026-06-26

Status: planning. Public release packaging is not approved until the user explicitly approves this phase.

Target implementation pass: Claude Code, after Phase 0-11 outputs exist and after user approval.

Related documents:

- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_PUBLIC_README_DRAFT.md` once created
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` once created
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` once created
- `docs/testing/KMK_TEST_AND_RELEASE_CHECKLIST.md` once created
- `docs/recommendations/CURRENT_STATE.md`
- `docs/ocr/README.md`
- `AGENTS.md`

## Baseline Scope

All audits, plans, and implementation passes must evaluate the full KMK fork delta against the current/latest Komikku baseline, not only the most recent KMK change set.

Claude must treat the scope as: everything added, changed, removed, or behaviorally affected since the latest/current Komikku upstream baseline available in this repository or verified from current upstream references. This includes source code, migrations, database schema, backup/sync behavior, preferences, UI strings, settings, extension handling, docs, Gradle/dependencies, tests, generated artifacts, and release/version naming.

Before making conclusions, Claude must identify what baseline it used:

- local upstream/latest Komikku commit or branch, if available,
- current app version/build metadata in this repo,
- official/current Komikku reference if local evidence is insufficient.

If Claude cannot determine the exact upstream baseline, it must say so clearly and proceed by comparing KMK-marked and newly added fork files against the nearest local Komikku/Mihon/TachiyomiSY patterns. Do not narrow the audit to only the latest KMK version unless the user explicitly asks for that.

## Purpose

Phase 12 prepares a responsible public sharing package for the fork after the earlier audits and hardening phases.

This phase does not claim the fork is upstream-ready. It prepares honest public-facing material so testers or community users understand exactly what they are installing, what is experimental, what data is touched, what is not supported, and how to report issues.

## Hard Rules

1. Do not publish or push anything publicly unless the user explicitly asks.
2. Do not claim official Komikku support.
3. Do not claim upstream merge readiness.
4. Do not hide experimental/security/privacy warnings.
5. Do not include internal Claude/Codex handoff prompts in public-facing docs.
6. Do not include OCR in the normal KMK-Recs release notes unless OCR is actually included in that APK line.
7. Do not include generated APKs in source control.
8. Verify current Komikku README, release, changelog, and issue-report style before drafting final public docs.
9. Keep release notes user-facing, not implementation-log-facing.
10. Clearly separate KMK-Recs and KMK-OCR release lines.

## Required Outputs

Create or finalize:

```text
README-KMK-FORK.md
docs/community/KMK_PUBLIC_RELEASE_NOTES_TEMPLATE.md
docs/community/KMK_ISSUE_REPORT_TEMPLATE.md
docs/community/KMK_PUBLIC_SHARING_CHECKLIST.md
```

Only create a root-level README file if the user approves that placement. If not approved, keep it as:

```text
docs/community/KMK_PUBLIC_README_DRAFT.md
```

## Public README Requirements

The public README must include:

- fork name and purpose,
- relationship to Komikku/Mihon/TachiyomiSY,
- clear experimental disclaimer,
- install/update warning,
- backup-before-install warning,
- feature overview grouped by stability,
- KMK-Recs feature list,
- Source Evaluation warning,
- OCR branch warning,
- recommendation bundle warning,
- known limitations,
- testing status,
- issue reporting instructions,
- privacy notes,
- not-upstream-ready statement.

Feature groups:

- stable personal features,
- experimental recommendation features,
- experimental source evaluation features,
- cross-extension and best-version tools,
- recommendation bundle import/export,
- OCR-only features.

Do not include:

- huge implementation chronology,
- raw Claude prompts,
- internal AI workflow notes,
- generated debug APK paths,
- claims that all devices/sources work.

## Release Notes Template

`KMK_PUBLIC_RELEASE_NOTES_TEMPLATE.md` should include sections:

- Version
- APK line:
  - KMK-Recs
  - KMK-OCR
- Based on Komikku version/baseline
- Added
- Changed
- Fixed
- Experimental
- Known issues
- Data/privacy notes
- Backup/update notes
- Tested on
- Checks run
- Download/install notes

The template must remind the writer:

- only include user-facing features,
- do not mention development-only docs as features,
- do not mix OCR notes into non-OCR APKs,
- do not claim release status beyond tests actually run.

## Issue Report Template

`KMK_ISSUE_REPORT_TEMPLATE.md` should request:

- APK filename/version,
- KMK-Recs or KMK-OCR line,
- install type:
  - clean install,
  - update,
  - restore from backup,
- Android version,
- device model,
- source/extension name if relevant,
- feature area:
  - For You,
  - Source Evaluation,
  - Cross-extension matching,
  - Loved Manga,
  - Best Version,
  - Recommendation Bundle,
  - OCR,
  - Backup/Restore,
  - Extension install/uninstall,
- steps to reproduce,
- expected behavior,
- actual behavior,
- screenshots if safe,
- crash log if available,
- whether source evaluation was running,
- whether OCR indexing was running,
- whether Shizuku/current/private installer was used,
- whether internet connection changed.

Privacy note:

- warn users not to paste private OCR text, private manga URLs, personal tokens, or full backups publicly.

## Public Sharing Checklist

`KMK_PUBLIC_SHARING_CHECKLIST.md` should include:

- Phase 0-11 completed or intentionally skipped,
- current baseline identified,
- generated artifacts removed from source-control scope,
- docs updated,
- warnings included,
- backup/restore checked,
- migration checked,
- tests run,
- release build preferred,
- APK filename verified,
- KMK-Recs/OCR line verified,
- known issues listed,
- issue template ready,
- rollback plan documented.

## APK And Version Naming

Use the current project/versioning pattern verified from repo docs and build files.

The docs should explain:

- app version,
- KMK feature version,
- debug vs release,
- OCR vs non-OCR,
- update compatibility expectations.

Do not invent a new naming scheme if Komikku/KMK already has one. If the current scheme is inconsistent, document the inconsistency and propose a correction for user approval.

## Security And Privacy Disclosure

The public docs must plainly disclose:

- Source Evaluation can temporarily install and execute extension source methods.
- Some cleanup paths may require Android prompts.
- OCR stores recognized text locally.
- Recommendation bundle import reads untrusted JSON and should be previewed before adding.
- Extension repos and sources are third-party content.
- Users should back up before installing an experimental fork.

## Final Verification Before Sharing

Before saying a release is ready, Claude must confirm:

```text
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

For a public/beta release, prefer a release or preview build task verified from current Komikku patterns.

If any command cannot run, the release notes must say that.

## Acceptance Criteria

This phase is complete only if:

- public README/draft exists,
- release notes template exists,
- issue report template exists,
- public sharing checklist exists,
- experimental warnings are clear,
- KMK-Recs and KMK-OCR are separate,
- docs do not read like AI handoff notes,
- no generated APK is committed,
- release claims match actual tests/builds,
- user approves any public publication separately.

## Summary Claude Should Provide

Claude should report:

- what public docs were created,
- what baseline was used,
- what warnings are included,
- what release line separation says,
- what tests/builds were verified,
- what still blocks public sharing,
- whether any files outside docs were changed.

