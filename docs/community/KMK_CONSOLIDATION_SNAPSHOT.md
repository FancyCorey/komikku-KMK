# KMK Consolidation Snapshot

Date: 2026-06-26

Status: Phase 0 snapshot. Documentation-only. No cleanup, refactor, or app behavior change is approved by this document.

## Scope

This snapshot covers the full KMK fork delta against the current/latest Komikku baseline, not only the latest KMK change set.

The fork delta includes KMK-Recs, KMK-OCR, database/schema/migration changes, backup/restore/sync changes, extension install/evaluation changes, UI/settings changes, tests, docs, generated artifacts, and release/version naming.

## Baseline Evidence

### Official/current Komikku reference

Verified from the official GitHub releases page on 2026-06-26:

- Repository: `komikku-app/komikku`
- Latest release observed: `Komikku v1.13.6`
- Tag observed: `v1.13.6`
- Release commit shown by GitHub: `9030e81`
- Release date shown: 2026-05-19
- Release asset guidance shown: `Komikku-v1.13.6.apk`
- Source: `https://github.com/komikku-app/komikku/releases`

### Local repository state

Read-only git metadata observed:

- Remote: `origin https://github.com/komikku-app/komikku.git`
- Current branch: `docs/recommendation-search-research`
- Local HEAD: `582ea3e60d93b498c5ca46bbbb450a8b89d41848`
- Local HEAD summary: `582ea3e chore(agent): Add mandatory rules for AI agents covering Git, i18n, and formatting (#1662)`
- Local tags: none returned by `git tag --list`

Because local tags are absent, the baseline should be treated as Komikku `v1.13.6` from official release evidence plus local code-pattern checks.

## App Metadata

From `app/build.gradle.kts`:

- `applicationId = "app.komikku"`
- `versionName = "1.13.6"`
- `versionCode = 81 // KMK OCR v0.1.1`
- debug suffix: `.dev`
- releaseTest suffix: `.rt`
- foss suffix: `.foss`
- preview suffix: `.beta`
- benchmark suffix: `.benchmark`

This indicates the current working tree is based on Komikku `1.13.6`, with OCR version-code metadata present in the main app build file.

## Current KMK Release Lines

### KMK-Recs

From `docs/recommendations/CURRENT_STATE.md`:

- Current documented feature version: `KMK-Recs v0.7.10`
- Current documented APK handoff: `Komikku-v1.13.6-kmk.7.10-debug.apk`

KMK-Recs is the main recommendation feature line.

### KMK-OCR

From `docs/ocr/README.md`:

- OCR is intentionally separate from the main KMK-Recs APK line.
- Existing feature label: `KMK-OCR v0.1.0`
- Planned/hardened feature label: `KMK-OCR v0.1.1`
- Example APK names:
  - `Komikku-v1.13.6-kmk-ocr.0.1-debug.apk`
  - `Komikku-v1.13.6-kmk-ocr.0.1.1-debug.apk`
- Suggested OCR versionCode: `81` or higher because OCR v0.1.0 used `80`

OCR should remain treated as a separate experimental build line unless the user explicitly changes that strategy.

## Dirty Worktree Categories

`git status --short` shows a large mixed fork delta. The output was not a clean feature branch state.

Observed modified categories:

- Gradle/build metadata: `app/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Domain and preferences: `KMKDomainModule.kt`, `SourcePreferences.kt`, `UiPreferences.kt`
- Browse/extension UI: `ExtensionsScreen.kt`, `BrowseTab.kt`, `ExtensionsScreenModel.kt`, `ExtensionsTab.kt`
- Manga UI and model: `MangaScreen.kt`, `MangaScreenModel.kt`, `MangaInfoHeader.kt`
- More/settings/about/What's New UI
- Backup create/restore/models/options
- Sync manager/service
- Extension manager/installer/loader/load result
- Source manager and global search
- Migration list behavior
- Manga repository/domain repository
- SQLDelight `mangas.sq`
- `i18n-kmk` base strings

Observed untracked categories:

- Generated APK: `Komikku-v1.13.6-kmk.4.3-debug.apk`
- Root historical/planning docs:
  - `RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md`
  - `RECOMMENDATION_IMPLEMENTATION_AUDIT.md`
  - `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md`
  - `RECOMMENDATION_SEARCH_RESEARCH.md`
  - `RECOMMENDATION_SOURCE_LANGUAGE_FILTER_PLAN.md`
  - `RECOMMENDATION_VERSIONING.md`
  - `TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md`
- KMK backup models/restorers
- KMK What's New files
- `app/src/main/java/exh/ocr/`
- `app/src/main/java/exh/recs/`
- `app/src/main/java/exh/source/ExplicitSourceClassifier.kt`
- migration/source matching helpers
- tests under `app/src/test/java/eu/`, `app/src/test/java/exh/`, and `app/src/test/java/mihon/feature/`
- `data/src/main/java/tachiyomi/data/taste/`
- KMK/OCR SQLDelight tables
- migrations `46.sqm` through `55.sqm`
- `docs/`
- `domain/src/main/java/tachiyomi/domain/taste/`
- `memory/`

## Generated Or Local Artifacts To Handle Later

Likely generated/local artifacts:

- `Komikku-v1.13.6-kmk.4.3-debug.apk`
- crash logs or extracted logs if present outside the current `rg --files` sample
- NAS/upload helper notes if present in `memory/`
- root-level handoff/planning docs that should probably be archived rather than public-facing

No artifacts were removed in this phase.

## Documentation State

Current documentation groups:

- `docs/community/`: consolidation audit, master phase roadmap, phase plans, validation/handoff prompt
- `docs/recommendations/`: current state, next work, active plans/implementations, research notes, archived plans/implementations
- `docs/ocr/`: OCR README, OCR v0.1.0/v0.1.1 plans and implementation notes
- root-level recommendation docs: older or pre-organization planning/history files

Documentation concerns:

- Many docs are internal/handoff style, not public-facing.
- Some historical docs may still be useful but should not be the first public entry point.
- `docs/recommendations/CURRENT_STATE.md` contains visible mojibake in some punctuation sequences.
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` currently has a malformed index line from an earlier failed PowerShell update and should be cleaned later.
- Public README, issue template, release notes template, and public sharing checklist are planned but not yet created.

## Database And Migration State

Observed KMK/OCR SQLDelight tables include:

- `manga_taste.sq`
- `tag_taste.sq`
- `tag_alias.sq`
- `recommendation_cache.sq`
- `recommendation_disabled_source.sq`
- `source_evaluation.sq`
- `source_evaluation_probe_marker.sq`
- `source_evaluation_unsafe_source.sq`
- `source_recommendation_fit.sq`
- `manga_cross_source_link.sq`
- `manga_source_quality_signal.sq`
- `unsafe_extension_package.sq`
- `ocr_indexed_page.sq`

Observed KMK/OCR migrations:

- `46.sqm` through `55.sqm`

Database review is not complete. Phase 3 must classify each table as durable user data, derived cache, diagnostic cache, privacy-sensitive local-only data, or installer/evaluation state.

## Backup, Restore, And Sync Areas Touched

Observed touched/untracked areas:

- `Backup.kt`
- `BackupCreator.kt`
- `BackupOptions.kt`
- `BackupRestorer.kt`
- `RestoreOptions.kt`
- `TasteBackupCreator.kt`
- `TasteRestorer.kt`
- `BackupMangaTaste.kt`
- `BackupTagTaste.kt`
- `BackupTagAlias.kt`
- `BackupDisabledRecommendationSource.kt`
- `BackupCrossSourceMangaLink.kt`
- `SyncManager.kt`
- `SyncService.kt`

The current docs say some custom data is backed up/restored, including taste rows and cross-source links. Seen/read backup is documented as deferred in `CURRENT_STATE.md`.

Backup/sync correctness is not validated by this snapshot. Phase 3 must audit it.

## Test And Build State From Docs

`docs/recommendations/CURRENT_STATE.md` documents many unit tests and says:

- `:app:testDebugUnitTest`: BUILD SUCCESSFUL
- `:app:assembleDebug`: BUILD SUCCESSFUL

This snapshot did not rerun Gradle tests/builds.

Known test documentation concern:

- `CURRENT_STATE.md` says all tests pass as of KMK-Recs v0.7.9, while the current documented version is v0.7.10. Phase 2 or Phase 11 should update this if newer tests were run.

## Immediate Risk Summary

High-risk areas:

- Source Evaluation temporary install/probe/uninstall.
- Shizuku/current/private installer behavior.
- Extension crash quarantine and native crash protection limits.
- OCR local text storage, CPU/memory/battery impact, and APK size.
- Backup/sync/proto/migration collision risks.
- Recommendation bundle JSON import as untrusted input.
- Large dirty worktree with generated artifacts and many untracked files.
- Public docs not yet separated from internal AI/handoff docs.

Medium-risk areas:

- Cross-extension matching false positives.
- Loved Manga duplicate grouping trust.
- Best Version source image/header behavior and migration/copy safety.
- For You/source status terminology complexity.
- Hardcoded strings or UI patterns that may not align with Komikku style.

## Freeze Recommendation

Until Phase 0-4 outputs are complete:

- Do not add new user-facing features.
- Allow only crash fixes, data-loss fixes, security/privacy fixes, and documentation corrections.
- Keep OCR separate.
- Keep Source Evaluation experimental/security-sensitive.
- Do not publish community builds without clear warnings and a release checklist.

## Next Required Output

The next Phase 0-1 output is:

```text
docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md
```

