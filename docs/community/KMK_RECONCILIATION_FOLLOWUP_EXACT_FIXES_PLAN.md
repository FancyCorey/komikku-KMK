# KMK Reconciliation Follow-Up: Exact Remaining Fixes Plan

Date: 2026-06-29
Status: pending user approval
Scope: exact follow-up cleanup after `KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md`

## Purpose

Claude completed the broad reconciliation pass and the code/build verification is reported clean. This follow-up is intentionally smaller.

The purpose is to fix the exact remaining contradictions and encoding leftovers found after rechecking Claude's reconciliation report.

This is not a feature pass. It is not a refactor. It is a precision documentation cleanup and verification pass.

## Current Verified Good State

These items appear correctly completed and should not be reworked:

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` reports `VERSION_CODE = 734` and `VERSION_NAME = "KMK-Recs v0.7.34"`.
- `app/build.gradle.kts` no longer has the stale `// KMK-Recs v0.7.16` comment. It now explains that Android package versioning and KMK feature versioning are separate.
- `data/src/main/sqldelight/tachiyomi/migrations/54.sqm` now uses `CREATE INDEX IF NOT EXISTS` for its OCR indexes.
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt` documents proto numbers `620-629` and includes proto `626` for `backupSeenMangaKeys`.
- Backup/sync code references confirm:
  - `backupCrossSourceMangaLinks` is included in `SyncManager`.
  - `backupMangaSourceQualitySignals` is included in `SyncManager`.
  - `backupSeenMangaKeys` exists in backup create/restore paths.
- Claude reports:
  - `spotlessCheck` passed.
  - `:app:testDebugUnitTest` passed.
  - `assembleDebug` passed.

Do not undo or rework those areas.

## Main Finding

The remaining issue is that the implementation report overstates that all documentation is fully clean/current. The code is in good shape for private beta, but several active docs still contain stale text, contradictions, or mojibake.

The follow-up should fix those exact places and update the implementation report so it no longer overclaims.

## Non-Goals

Do not add new app features.

Do not change recommendation behavior.

Do not change Source Evaluation behavior.

Do not change backup/proto field numbers.

Do not change migration numbers.

Do not change `applicationId`.

Do not create a public release.

Do not initialize git, create tags, or create branches.

Do not delete historical docs.

Do not rewrite every old archived implementation report.

Archived docs may retain historical stale text and mojibake if they are clearly archived. Active docs should be clean.

## Files Claude Must Read First

- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md`
- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION_PLAN.md`
- `docs/community/KMK_FULL_RECONCILIATION_AUDIT.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_PUBLIC_README_DRAFT.md`
- `app/build.gradle.kts`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/sync/SyncManager.kt`
- `data/src/main/sqldelight/tachiyomi/migrations/54.sqm`

## Exact Fix 1: `CURRENT_STATE.md` Mojibake Leftovers

File:

- `docs/recommendations/CURRENT_STATE.md`

Known remaining lines from recheck:

- Around line 450:
  - Current text contains `30Ã¢â‚¬â€œ75%`.
  - Replace with ASCII `30-75%`.

- Around line 735:
  - Current text contains `v0.7.6Ã¢â‚¬â€œv0.7.7`.
  - Replace with ASCII `v0.7.6-v0.7.7`.

Required search:

```bash
rg -n "Ã¢|Ãƒ|Ã‚|ï¿½" docs/recommendations/CURRENT_STATE.md
```

Acceptance:

- No mojibake remains in `CURRENT_STATE.md`.
- Do not alter historical version meanings, only encoding artifacts.

## Exact Fix 2: `KMK_COMMUNITY_CONSOLIDATION_PHASES.md` Stale Phase 6/7 Status

File:

- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`

Known remaining stale line from recheck:

- Around line 345:
  - Current text says:
    - `Status: mostly implemented through KMK-Recs v0.7.16...`
  - This contradicts the new 2026-06-29 update at the top of the same file.

Required change:

- Replace that paragraph with a current status that reflects the completed reconciliation and current active `NEXT_WORK.md`.

Suggested wording:

```text
Status: implemented and reconciled through KMK-Recs v0.7.34 as of 2026-06-29. Settings reorganization, terminology cleanup, Loved Manga sort/no-duplicates feedback, Source Evaluation evidence/verdict i18n, Best Version cancel/fullscreen checks, quality-signal backup/sync, link-group management UI, live Loved Manga updates, Source Evaluation polish, and the v0.7.29-v0.7.34 polish phases are documented. Remaining open items are tracked in docs/recommendations/NEXT_WORK.md.
```

Acceptance:

- No active section in this file implies Phase 6/7 only reached `v0.7.16`.
- Historical references to old versions may remain where clearly historical.

## Exact Fix 3: `KMK_DATABASE_BACKUP_SYNC_AUDIT.md` Contradictions And Mojibake

File:

- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`

The audit document is the biggest remaining problem. It contains both encoding artifacts and statements that contradict the current code.

### 3A: Fix status header

Known current line:

```text
Status: Phase 3 audit, reconciled against code after KMK-Recs v0.7.16...
```

Required:

- Update to say the document was originally a Phase 3 audit, then reconciled again on 2026-06-29 through `KMK-Recs v0.7.34`.
- Do not erase historical context.

Suggested wording:

```text
Status: Phase 3 audit, originally reconciled after KMK-Recs v0.7.16 and rechecked on 2026-06-29 through KMK-Recs v0.7.34. Historical findings remain for traceability; current status rows below mark mitigated, open, or ongoing items.
```

### 3B: Fix migration table stale notes

Known current lines around 61-80 contain mojibake and stale statements:

- `Ã¢â‚¬â€` artifacts in migration descriptions.
- Migration 46 note says no comment declares upstream baseline.
- Risk summary says no migration tests.
- Risk summary says migration 54 lacks `IF NOT EXISTS`.

Required current facts:

- Upstream baseline is documented in this audit/doc set.
- `KmkMigrationTest` exists and mitigates the no-migration-tests risk.
- `54.sqm` now has `CREATE INDEX IF NOT EXISTS` for OCR indexes.

Required edits:

- Replace mojibake em dashes with ASCII `--`.
- Change the migration 46 note so it no longer says there is no comment declaring the baseline, unless Claude verifies no migration-boundary doc exists anywhere.
- Change the DB-04 risk summary from "No migration tests" to "Migration tests added in v0.7.16; keep updated when migrations change."
- Change the DB-05 risk summary from "Migration 54 lacks IF NOT EXISTS" to "Fixed in 2026-06-29 reconciliation pass."

### 3C: Fix proto/sync table contradiction

Known current line around 302:

```text
| 624 | `backupCrossSourceMangaLinks` | Cross-source link groups | Yes (backup only; sync gap) | --- |
```

This is wrong because code shows `backupCrossSourceMangaLinks` in `SyncManager`.

Required replacement:

```text
| 624 | `backupCrossSourceMangaLinks` | Cross-source link groups | Yes (backup + sync) | Sync gap fixed in v0.7.16 |
```

Also ensure proto 626 is present:

```text
| 626 | `backupSeenMangaKeys` | For You seen/read dismissals | Yes (backup) | Added in v0.7.28 |
```

### 3D: Fix DB-09 wording

Known current line around 254:

```text
This was not confirmed in `BackupOptions` -- requires verification (DB-09).
```

Claude's report says DB-09 was documented: these preferences are covered only by general `BackupOptions.appPreferences`.

Required:

- Update wording from "not confirmed" to the actual current conclusion:
  - source priority / liked-disliked sources / recommendation language filters are stored as `SourcePreferences`;
  - they are included only when app preferences are included in backup;
  - if app preferences are disabled, they are not backed up;
  - this remains a known limitation, not an unverified unknown.

### 3E: Fix mojibake across the file

Required search:

```bash
rg -n "Ã¢|Ãƒ|Ã‚|ï¿½" docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md
```

Acceptance:

- No accidental mojibake remains in this active database audit doc.
- If the doc intentionally quotes mojibake examples, label them as examples. Prefer not to quote them here.

## Exact Fix 4: `KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md` Overclaim Correction

File:

- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md`

Current report claims all reconciliation work is done, but the post-check found remaining active-doc issues.

Required:

- Add an addendum section at the end:

```text
## 2026-06-29 Follow-Up Correction

Post-implementation recheck found several residual documentation issues:
- CURRENT_STATE.md still had two mojibake ranges.
- KMK_COMMUNITY_CONSOLIDATION_PHASES.md still had one stale Phase 6/7 v0.7.16 status paragraph.
- KMK_DATABASE_BACKUP_SYNC_AUDIT.md still had stale DB-04/DB-05/DB-09/proto-624 wording and mojibake.

These were corrected in the follow-up pass documented in ...
```

- After Claude implements this plan, complete that addendum with actual result and link this plan.

Acceptance:

- The implementation report no longer overclaims that the first reconciliation pass was perfect.
- It records the follow-up honestly.

## Exact Fix 5: Verify Public README Does Not Overclaim

File:

- `docs/community/KMK_PUBLIC_README_DRAFT.md`

Required checks:

```bash
rg -n "upstream-ready|public release|Ready|not ready|applicationId|app\\.komikku|v0\\.7\\.34|Claude|Codex|ChatGPT|AI workflow" docs/community/KMK_PUBLIC_README_DRAFT.md
```

Required:

- Ensure it says the fork is unofficial.
- Ensure it says community/public release is blocked or limited by `applicationId = "app.komikku"` unless a separate community package id is chosen.
- Ensure it does not claim upstream readiness.
- Ensure it does not expose Claude/Codex workflow details in public-facing text.

Only edit if the checks show a problem.

## Exact Fix 6: Active Docs Encoding Sweep

Run:

```bash
rg -n "Ã¢|Ãƒ|Ã‚|ï¿½" docs/recommendations/CURRENT_STATE.md docs/recommendations/NEXT_WORK.md docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md docs/community/KMK_PUBLIC_README_DRAFT.md docs/community/KMK_PHASE_3_4_RISK_REGISTER.md docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md
```

Required:

- Fix accidental mojibake in active docs.
- Do not chase every archived file.
- Do not treat valid non-English localization files under `i18n-kmk` as mojibake just because they contain accented characters.
- Do not remove historical references from archived docs unless they are active/public-facing.

## Exact Fix 7: Verification

Because this is mostly documentation, full build may not be strictly necessary if only docs change.

However, if Claude changes any code, SQL, Gradle, strings, or tests, it must run:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

If only markdown docs change, run:

```powershell
rg -n "Ã¢|Ãƒ|Ã‚|ï¿½" docs/recommendations/CURRENT_STATE.md docs/recommendations/NEXT_WORK.md docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md docs/community/KMK_PUBLIC_README_DRAFT.md docs/community/KMK_PHASE_3_4_RISK_REGISTER.md docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md
rg -n "mostly implemented through KMK-Recs v0\\.7\\.16|backup only; sync gap|No migration tests|requires verification \\(DB-09\\)|lack `IF NOT EXISTS`|lacks `IF NOT EXISTS`" docs/recommendations/CURRENT_STATE.md docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md
```

If full tests are not run because only docs changed, the final report must say so plainly.

## Final Report Requirements

Claude must update or append to:

- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md`

It must include:

- exact files changed;
- exact stale contradictions fixed;
- exact mojibake leftovers fixed;
- whether any code changed;
- whether Gradle tests/build were rerun;
- final readiness verdict.

Use this verdict language unless new evidence changes it:

```text
Private beta readiness: Ready -- code/build verification from the reconciliation pass remains valid; this follow-up only cleaned residual active documentation unless code changes were made.
Community beta readiness: Partially ready -- applicationId remains app.komikku, so distribution alongside official Komikku is blocked until the user approves a package-id strategy.
Public release readiness: Not ready -- requires package-id/versioning/release process decisions.
Upstream contribution readiness: Not ready -- feature scope remains too broad for direct upstream contribution.
```

## Expected End State

After this follow-up:

- Active docs should no longer contain accidental mojibake.
- Active docs should no longer contradict code about proto 624 sync, proto 626 seen backup, migration tests, or migration 54 index guards.
- `KMK_COMMUNITY_CONSOLIDATION_PHASES.md` should no longer imply the main feature work only reached `v0.7.16`.
- The implementation report should honestly record that a follow-up correction was needed.
- No app behavior should change.


