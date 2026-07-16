# KMK Consolidation Validation And Handoff

Date: 2026-06-26

Status: validation/handoff document. This document records what still needs to be checked and gives Claude a concrete first execution scope. It does not approve app code changes.

## Current Baseline Evidence

The consolidation effort must compare the full KMK fork delta against current/latest Komikku, not only the latest KMK feature pass.

### Official/current external baseline evidence

Verified from the official GitHub releases page on 2026-06-26:

- Repository: `komikku-app/komikku`
- Latest release shown: `Komikku v1.13.6`
- Tag: `v1.13.6`
- Release commit shown by GitHub: `9030e81`
- Release date shown: 2026-05-19
- Release asset guidance shown: `Komikku-v1.13.6.apk`
- Source: `https://github.com/komikku-app/komikku/releases`

Claude must still verify the local baseline against repo state before making conclusions.

### Local repository evidence

Read-only git metadata from the local workspace:

- Remote: `origin https://github.com/komikku-app/komikku.git`
- Current branch: `docs/recommendation-search-research`
- Local HEAD: `582ea3e60d93b498c5ca46bbbb450a8b89d41848`
- Local HEAD summary: `582ea3e chore(agent): Add mandatory rules for AI agents covering Git, i18n, and formatting (#1662)`
- Local tags: `git tag --list` returned no tags in this workspace.

This means Claude should not assume local tags identify the baseline. It should compare against:

1. the official release evidence above,
2. local app metadata,
3. local Komikku/Mihon/TachiyomiSY coding patterns,
4. remote/upstream references only where necessary.

### Local app metadata evidence

From `app/build.gradle.kts`:

- `applicationId = "app.komikku"`
- `versionCode = 81 // KMK OCR v0.1.1`
- `versionName = "1.13.6"`
- debug suffix uses `.dev`
- releaseTest suffix uses `.rt`
- foss suffix uses `.foss`
- preview suffix uses `.beta`
- benchmark suffix uses `.benchmark`

Current app metadata is therefore based on Komikku `1.13.6`, with OCR version-code/comment modifications present in the working tree.

### Current documented KMK feature lines

From current docs:

- `docs/recommendations/CURRENT_STATE.md` says current documented recommendation feature version is `KMK-Recs v0.7.10`.
- Documented recommendation APK handoff: `Komikku-v1.13.6-kmk.7.10-debug.apk`.
- `docs/ocr/README.md` says OCR is intentionally separate from the main KMK-Recs APK line.
- OCR docs refer to `KMK-OCR v0.1.0` and planned/hardened `KMK-OCR v0.1.1`.
- OCR APK naming examples include `Komikku-v1.13.6-kmk-ocr.0.1-debug.apk` and `Komikku-v1.13.6-kmk-ocr.0.1.1-debug.apk`.
- OCR suggested versionCode is `81` or higher because OCR v0.1.0 used `80`.

## Dirty Worktree Categories Observed

`git status --short` shows a large full-fork delta. It must be classified by Phase 0-1 before cleanup.

Observed categories:

- modified Gradle/build metadata,
- modified domain modules and preferences,
- modified browse/manga/more/settings UI,
- modified backup/create/restore models and options,
- modified sync manager/service,
- modified extension manager/loader/installer behavior,
- modified source manager and global search behavior,
- modified migration screen behavior,
- modified manga repository/domain repository,
- modified SQLDelight `mangas.sq`,
- modified `i18n-kmk` base strings,
- generated debug APK in repo root: `Komikku-v1.13.6-kmk.4.3-debug.apk`,
- untracked historical recommendation planning docs at repo root,
- untracked KMK backup models/restorers,
- untracked KMK What's New files,
- untracked OCR package under `app/src/main/java/exh/ocr/`,
- untracked KMK recommendation packages under `app/src/main/java/exh/recs/`,
- untracked explicit source classifier,
- untracked migration/source match helper,
- untracked tests under `app/src/test/java/`,
- untracked taste data/domain layers,
- untracked SQLDelight KMK/OCR tables,
- untracked migrations `46.sqm` through `55.sqm`,
- untracked `docs/`,
- untracked `memory/`.

This reinforces that the consolidation scope is the full KMK fork delta, not the latest KMK version only.

## Required Next Documents

The phase plans exist, but these execution outputs still need to be created by Claude in order:

1. `docs/community/KMK_CONSOLIDATION_SNAPSHOT.md`
2. `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md`
3. `docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md`
4. `docs/community/KMK_PUBLIC_README_DRAFT.md`
5. `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`
6. `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`
7. `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md`
8. later phase implementation notes only after user approval.

## Risk Register Is Required

The previous Phase 3-4 plan described the risk register as optional. It should be treated as required before community sharing.

Required file:

```text
docs/community/KMK_PHASE_3_4_RISK_REGISTER.md
```

Minimum risk areas:

- Source Evaluation temporary install/probe/uninstall,
- Source Evaluation process death and cleanup leftovers,
- Shizuku/current/private installer behavior,
- extension crash/native crash quarantine,
- OCR local text privacy and storage,
- OCR memory/CPU/battery risk,
- recommendation bundle JSON import boundary,
- backup/sync/proto/migration collision risks,
- generated artifacts in source tree,
- hardcoded or misplaced strings,
- public release claims exceeding tested reality.

## Immediate Claude Execution Order

Do not give Claude all phases at once.

First Claude execution should be Phase 0-1 only:

- read the phase plans,
- identify the baseline,
- create the snapshot,
- create the feature classification matrix,
- do not move/delete/refactor code,
- do not fix docs except the two requested outputs unless necessary to link them.

After Claude finishes Phase 0-1, Codex should inspect those outputs before moving to Phase 2.

## Do Not Forget

Claude must treat every document and plan as applying to the full fork delta from Komikku v1.13.6/current baseline, including all KMK-Recs, KMK-OCR, database, backup, sync, extension, UI, string, test, docs, and generated-artifact changes.

