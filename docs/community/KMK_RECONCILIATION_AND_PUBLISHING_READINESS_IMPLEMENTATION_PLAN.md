# KMK Reconciliation And Publishing Readiness Implementation Plan

Date: 2026-06-29
Status: implementation plan pending user approval
Source audit: `docs/community/KMK_FULL_RECONCILIATION_AUDIT.md`
Target pass: consolidation, documentation hygiene, release hygiene, and publish-readiness verification

## Purpose

This plan converts the full reconciliation audit into an implementation-ready cleanup pass for Claude Code.

The goal is not to add new manga-reader features. The goal is to bring the repository, documentation, version metadata, public-facing materials, and verification records back into alignment with the actual current code state.

The audit establishes that the app-side KMK release notes are at `KMK-Recs v0.7.34`, while several planning and state documents are stale, some release/version comments are misleading, and public-sharing materials are not ready.

## Non-Goals

Do not implement new recommendation features.

Do not redesign Source Evaluation, For You, Best Version, Loved Manga, OCR, bundle import/export, or extension handling.

Do not change `applicationId` unless the user explicitly approves that separate breaking change.

Do not initialize git, create tags, create branches, or rewrite git history unless the user explicitly approves repository version-control setup.

Do not delete historical documentation unless the plan explicitly says to archive it and the user approves that cleanup.

Do not claim community readiness until the checklist in this plan is complete and verified.

## Audit Findings To Treat As Source Of Truth

Claude must read the full audit first:

- `docs/community/KMK_FULL_RECONCILIATION_AUDIT.md`

The implementation must preserve these core conclusions:

- Current KMK feature version is `KMK-Recs v0.7.34` in `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`.
- `app/build.gradle.kts` still contains a stale `// KMK-Recs v0.7.16` comment near `versionCode = 88`.
- `CURRENT_STATE.md` and `NEXT_WORK.md` are stale around `v0.7.25`/`v0.7.26`.
- `KMK_PUBLIC_README_DRAFT.md` is stale around `v0.7.15`.
- `KMK_COMMUNITY_CONSOLIDATION_PHASES.md`, `KMK_SECURITY_AND_PRIVACY_REVIEW.md`, and `KMK_DATABASE_BACKUP_SYNC_AUDIT.md` lag behind the latest release notes in places.
- Polish phases A-J were planned with incorrect version numbers. Actual shipped versions are A=`v0.7.29`, B=`v0.7.30`, C=`v0.7.31`, J=`v0.7.34`; D and I remain unimplemented unless code inspection proves otherwise.
- There is no implementation report covering the actual `v0.7.29` through `v0.7.34` polish work.
- `CURRENT_STATE.md` and `NEXT_WORK.md` contain mojibake/corrupted characters.
- Internal AI/Codex/Claude docs exist and must be separated from public-facing docs before community sharing.
- The app is suitable for private beta, but not yet community/public distribution.

## Phase 1: Documentation Reconciliation

### Goal

Make the active documentation accurately reflect the current code state through `KMK-Recs v0.7.34`.

### Files To Read First

- `docs/community/KMK_FULL_RECONCILIATION_AUDIT.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md`
- `docs/recommendations/KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md`
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md`
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

### Required Changes

1. Rewrite or heavily reconcile `docs/recommendations/CURRENT_STATE.md`.
   - It must state the current documented feature version as `KMK-Recs v0.7.34`.
   - It must include the post-`v0.7.26` features from release notes:
     - Best Version History browser (`v0.7.27`)
     - For You Seen backup/restore (`v0.7.28`)
     - Source priority Last checked timestamp (`v0.7.29`)
     - For You pull-to-refresh (`v0.7.29`)
     - Loved Manga live updates (`v0.7.29`)
     - Source Evaluation quarantine/blocked row collapse (`v0.7.29`)
     - Cross-source link group management UI (`v0.7.30`)
     - Recommendation enrichment cap setting (`v0.7.31`)
     - Installed source rolling fit stats / Top Picks contribution tracking (`v0.7.32`) if code confirms it exists
     - Best Version fullscreen dialog save/restore and preview polish (`v0.7.33`) if code confirms it exists
     - Source Evaluation per-source error category labels, retry after connectivity loss, and profile-changed banner (`v0.7.34`)
   - It must remove or correct stale statements that say current docs stop at `v0.7.16`, `v0.7.20`, or `v0.7.26`.
   - It must fix mojibake instead of preserving corrupted characters.

2. Replace or archive `docs/recommendations/NEXT_WORK.md`.
   - The current file is too stale to remain the active planning queue.
   - Preferred approach: create a clean current `NEXT_WORK.md` containing only still-open items.
   - If historical details are useful, move the current stale version to `docs/recommendations/archive/` before writing the new version.
   - The new active next-work file must clearly separate:
     - truly remaining work,
     - optional future improvements,
     - blocked user decisions,
     - community-readiness tasks,
     - OCR-only tasks.

3. Reconcile `docs/recommendations/KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md`.
   - Correct the A-J version mapping to actual shipped versions.
   - Mark completed phases with evidence.
   - Mark D and I as remaining only if code inspection confirms they are still missing.
   - If release notes claim D/I are implemented, inspect code and update the audit conclusion accordingly.

4. Update `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`.
   - It must no longer imply Phase 6/7 status only through `v0.7.16`.
   - It must reflect the current audit verdict:
     - private beta: yes
     - community/public distribution: not ready
     - upstream contribution: not ready
   - It must point to the full reconciliation audit and this implementation plan.

5. Update `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md`.
   - Preserve historical risks.
   - Update risk statuses using the audit.
   - Ensure R-017 public docs exposure remains open until public docs are actually cleaned.
   - Ensure R-022 architecture cleanup remains open unless a real architecture refactor/audit has been completed.
   - Ensure SEC/DB references align with the security/database docs after this pass.

### Acceptance Criteria

- No active state document claims the current feature version is older than `v0.7.34`.
- No active planning document lists completed `v0.7.29`-`v0.7.34` work as still deferred.
- `CURRENT_STATE.md` and `NEXT_WORK.md` no longer contain mojibake.
- Remaining work is smaller, current, and actionable.

## Phase 2: Missing Implementation Record For v0.7.29-v0.7.34

### Goal

Create the missing implementation record for the recent polish phases.

### Required New File

Create:

- `docs/recommendations/KMK_RECS_V0_7_29_TO_V0_7_34_POLISH_IMPLEMENTATION.md`

### Required Content

The document must include:

- Status: implemented/audited based on code evidence
- Scope: polish phases after `v0.7.26`
- Version mapping:
  - `v0.7.27`: Best Version History browser
  - `v0.7.28`: For You Seen backup/restore
  - `v0.7.29`: Source priority timestamp, For You pull-to-refresh, Loved Manga live updates, quarantine/blocked collapse
  - `v0.7.30`: Cross-source link group management UI
  - `v0.7.31`: Recommendation enrichment cap setting
  - `v0.7.32`: Installed source fit stats / Top Picks contribution tracking, if code confirms
  - `v0.7.33`: Best Version fullscreen dialog state and preview polish, if code confirms
  - `v0.7.34`: Source Evaluation error categories, connectivity retry, profile-change banner
- Files changed or touched, based on code inspection.
- Tests present for each feature.
- Test gaps.
- Known limitations.
- Whether each item is stable, experimental, or public-blocking.

### Important Verification

Do not simply copy release notes. Inspect code and tests.

If a release note claims a feature exists but the implementation cannot be found, document it as:

`Release note claims implemented; code evidence not found in this audit.`

## Phase 3: Version And Release Hygiene

### Goal

Make version metadata less misleading without forcing a breaking package rename.

### Required Reads

- `app/build.gradle.kts`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `docs/recommendations/RECOMMENDATION_VERSIONING.md` if present
- `docs/community/KMK_FULL_RECONCILIATION_AUDIT.md`
- Any APK naming docs found by searching for `Komikku-v1.13.6-kmk`

### Required Changes

1. Fix the stale `app/build.gradle.kts` comment.
   - Replace `// KMK-Recs v0.7.16` with a policy comment that will not become stale immediately.
   - Preferred wording:
     - `// Android package versionCode. KMK feature version is tracked separately in KmkRecsReleaseNotes.`
   - Do not change `applicationId`.
   - Do not change `versionName`.
   - Only change `versionCode` if the current build needs to install over the previous APK and the user explicitly approved a new build. Otherwise document the decision.

2. Document the difference between:
   - Android `versionCode`
   - Android `versionName`
   - `KmkRecsReleaseNotes.VERSION_CODE`
   - `KmkRecsReleaseNotes.VERSION_NAME`
   - APK filename convention

3. Update versioning docs to reflect current `v0.7.34` and current APK naming.

4. Add a release checklist section somewhere public-facing or semi-public:
   - bump Android `versionCode` only when creating an APK intended to update installed devices;
   - update `KmkRecsReleaseNotes`;
   - update `CURRENT_STATE.md`;
   - add/update implementation report;
   - run verification;
   - record APK filename.

### Acceptance Criteria

- No stale `v0.7.16` build comment remains in `app/build.gradle.kts`.
- Docs explain why Android version code and KMK version code differ.
- Future Claude/Codex runs have a clear versioning rule.

## Phase 4: Security, Privacy, Database, Backup, And Sync Reconciliation

### Goal

Bring the audit/security/database docs in line with current code and add small low-risk documentation/code comments where the audit identified missing guardrails.

### Required Changes

1. Update `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`.
   - Reconcile through `v0.7.34`.
   - Keep Source Evaluation risk honest.
   - Keep Shizuku/current modes marked as higher-risk than Private mode.
   - Preserve the conclusion that Source Evaluation is experimental.
   - Preserve OCR plaintext storage warning.

2. Update `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`.
   - Reconcile through `v0.7.34`.
   - Correct stale statements about seen state if For You Seen backup/restore exists at proto 626.
   - Correct stale statements about source quality signals or cross-source links if already fixed.
   - Update proto field range from `620-625` to the actual current range if proto 626 is implemented.

3. Add migration boundary documentation.
   - Preferred low-risk file: `data/src/main/sqldelight/migrations/README.md` or the actual migrations directory if path differs.
   - Content must state:
     - upstream Komikku v1.13.6 migration baseline ends at 45;
     - KMK currently occupies migrations 46-55;
     - rebase must check upstream migrations before adding or applying future KMK migrations.

4. Add or update a proto field range comment in the backup model source.
   - Locate the KMK backup fields around proto 620+.
   - Document that KMK currently reserves/uses the applicable range, including proto 626 if present.
   - Do not renumber any existing fields.

5. Verify DB-09.
   - Check whether source priority, liked/disliked source preference, hidden/non-installed suggestion state, and recommendation language preferences are covered by app preference backup.
   - If they are covered by standard preference backup, document that.
   - If not covered, do not create a new backup schema unless clearly low-risk; document the gap and propose a follow-up.

6. Investigate migration 54 index guards.
   - If changing `54.sqm` is safe and does not create migration ordering risk, add `IF NOT EXISTS` to its `CREATE INDEX` statements.
   - If changing old migrations is not acceptable in this project style, leave code unchanged and document why.

### Acceptance Criteria

- Security and DB docs no longer contradict implemented backup/sync/proto behavior.
- Migration/proto boundary is documented in code or adjacent docs.
- No backup field numbers are changed.
- No destructive migration changes are made.

## Phase 5: Public Documentation And Community Readiness Package

### Goal

Prepare honest public-facing documentation without pretending the fork is upstream-ready.

### Required Reads

- `docs/community/KMK_PUBLIC_README_DRAFT.md`
- `README.md`
- `CONTRIBUTING.md` if present
- `AGENTS.md`
- `docs/community/KMK_FULL_RECONCILIATION_AUDIT.md`

### Required Changes

1. Rewrite `docs/community/KMK_PUBLIC_README_DRAFT.md`.
   - Update it to `KMK-Recs v0.7.34`.
   - Clearly state this is an unofficial Komikku fork.
   - Clearly state the app is not upstream-ready.
   - Separate stable, experimental, and OCR-only features.
   - Do not use internal AI workflow language in public-facing sections.
   - Keep security warnings for Source Evaluation and OCR.

2. Create or update an issue template draft.
   - It should ask for:
     - APK filename/version;
     - Android version/device;
     - whether the issue involves Source Evaluation, OCR, Best Version, For You, backup/restore, or extension install;
     - crash log if available;
     - steps to reproduce;
     - whether Shizuku/current/private installer mode was used.
   - It must not ask users to share private OCR text, private backups, auth tokens, or sensitive URLs.

3. Create a public known-limits section.
   - Source Evaluation executes extension code and is experimental.
   - Shizuku/current modes may require Android prompts.
   - OCR stores recognized text locally and should remain optional/separate.
   - Best Version page previews are source-dependent and may fail if sources require special headers/cookies.
   - Recommendation quality depends on installed/available extension behavior.

4. Archive internal AI workflow docs only if approved.
   - If archiving is approved, move implementation prompts, Claude prompts, and planning handoff docs to an internal archive path.
   - Do not delete them.
   - If not approved, add an index explaining which docs are internal and which docs are public.

### Acceptance Criteria

- Public README draft is current and honest.
- Public docs do not rely on internal Claude/Codex workflow details.
- Known limitations and security boundaries are clearly stated.

## Phase 6: Style, String, And Encoding Cleanup

### Goal

Clean the most visible style issues while avoiding broad refactors.

### Required Changes

1. Fix mojibake in active docs.
   - At minimum:
     - `docs/recommendations/CURRENT_STATE.md`
     - `docs/recommendations/NEXT_WORK.md`
   - Search for:
     - `Ã¢`
     - `Ãƒ`
     - `Ã‚`
     - `ï¿½`
   - Replace with ASCII equivalents when possible to reduce future encoding churn.

2. Audit KMK app-visible strings.
   - Search `app/src/main/java/exh/recs` and `app/src/main/java/exh/ocr` for hardcoded user-facing English.
   - Move any confirmed user-facing strings to `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`.
   - Do not move log/debug-only strings unless they are shown to users.

3. Check the C1 Source Evaluation error category labels.
   - If they are user-visible and hardcoded, move them to KMR strings or document why they remain hardcoded.

4. Clean obvious marker issues.
   - Inspect `SourcePreferences.kt` for stray `// KMK <--` markers mentioned in the audit.
   - Fix only marker/comment structure, not behavior.

5. Avoid broad architecture refactors in this pass.
   - If `exh/recs` needs architecture cleanup, create a separate architecture cleanup plan rather than mixing it into this release hygiene pass.

### Acceptance Criteria

- Active docs have no mojibake.
- User-visible KMK strings are KMR-backed unless explicitly documented as an exception.
- No behavior changes beyond low-risk comment/string cleanup.

## Phase 7: Verification

### Required Commands

Run, in this order:

```bash
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If Windows requires Gradle wrapper syntax, use the project-standard equivalent:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

### Additional Targeted Checks

Run targeted searches after edits:

```bash
rg -n "v0\.7\.16|v0\.7\.20|v0\.7\.25|v0\.7\.26" docs app/build.gradle.kts
rg -n "Ã¢|Ãƒ|Ã‚|ï¿½" docs app/src/main/java i18n-kmk
rg -n "Claude|Codex|ChatGPT|AI workflow|handoff prompt" docs/community docs/recommendations
rg -n "KMK-Recs v0\.7\.34|VERSION_CODE|versionCode" app docs
```

For the old-version search, do not blindly remove historical references. Historical implementation reports may correctly mention old versions. Only active state/current/public/versioning docs must be updated.

### Required Test Report

Claude must document:

- commands run;
- success/failure;
- failures and root causes;
- whether failures are pre-existing;
- whether any tests were skipped and why.

## Phase 8: Final Implementation Report

### Required New File

Create:

- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md`

### Required Content

The report must include:

- summary of changes;
- files changed;
- stale docs fixed;
- versioning decisions made;
- public-readiness status after changes;
- remaining blockers;
- verification commands and results;
- explicit statement that no new user-facing recommendation features were added.

### Final Verdict Format

Use this exact structure:

```text
Private beta readiness: Ready / Not ready / Partially ready
Community beta readiness: Ready / Not ready / Partially ready
Public release readiness: Ready / Not ready / Partially ready
Upstream contribution readiness: Ready / Not ready / Partially ready
```

Each line must include a one-sentence reason.

## User Decisions That Must Not Be Assumed

Claude must stop and ask before doing any of these:

- Changing `applicationId`.
- Creating git tags or branches.
- Deleting documentation.
- Moving internal docs outside `docs/` if the move could break existing references.
- Changing backup/proto field numbers.
- Changing migration numbering.
- Making OCR part of the standard release line.
- Creating a release APK for public distribution.

## Expected Final State

After this plan is implemented:

- The repository should accurately describe itself as a private/community-beta fork, not a publish-ready upstream-quality fork.
- Active docs should reflect `KMK-Recs v0.7.34`.
- Missing recent implementation history should be documented.
- Stale version comments should be corrected.
- Mojibake should be removed from active docs.
- Public README draft should be current and honest.
- Security/database docs should not contradict implemented backup/sync behavior.
- Verification results should be recorded.


