# Claude Code Prompt: KMK Phase 0-1 Snapshot And Feature Classification

Use this prompt in Claude Code for the first consolidation execution pass.

```text
You are working in the Komikku fork workspace. This is a consolidation/audit task, not a feature implementation task.

Important: do not make app code changes. Do not delete, move, archive, or rename files. Do not refactor source code. Do not change migrations, backup models, sync code, resources, Gradle files, or tests. This pass is documentation and classification only.

Read these files first:

- AGENTS.md
- docs/community/KMK_COMMUNITY_READINESS_AUDIT.md
- docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md
- docs/community/KMK_CONSOLIDATION_VALIDATION_AND_HANDOFF.md
- docs/community/KMK_PHASE_0_1_SNAPSHOT_AND_FEATURE_CLASSIFICATION_PLAN.md
- docs/recommendations/README.md
- docs/recommendations/CURRENT_STATE.md
- docs/recommendations/NEXT_WORK.md
- docs/recommendations/DOCUMENTATION_RULES.md
- docs/ocr/README.md
- docs/ocr/KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_IMPLEMENTATION.md
- docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_IMPLEMENTATION.md
- app/build.gradle.kts
- gradle/libs.versions.toml

Scope:

Evaluate the full KMK fork delta against the current/latest Komikku baseline, not only the latest KMK change set.

Use this known baseline evidence unless local checks prove otherwise:

- Official repo: https://github.com/komikku-app/komikku
- Latest release observed: Komikku v1.13.6
- Tag observed: v1.13.6
- Release commit observed on GitHub: 9030e81
- Local remote: origin https://github.com/komikku-app/komikku.git
- Local branch observed: docs/recommendation-search-research
- Local HEAD observed: 582ea3e60d93b498c5ca46bbbb450a8b89d41848
- Local app versionName observed: 1.13.6
- Local app versionCode observed: 81 // KMK OCR v0.1.1
- Current documented KMK-Recs version: v0.7.10
- Current OCR docs mention KMK-OCR v0.1.0/v0.1.1 as a separate APK line

You must still verify local evidence yourself with read-only commands where possible:

- git remote -v
- git branch --show-current
- git rev-parse HEAD
- git log -1 --oneline
- git status --short
- git diff --name-only
- rg --files docs
- rg --files app/src/main/java/exh/recs app/src/main/java/exh/ocr domain/src/main/java/tachiyomi/domain/taste data/src/main/java/tachiyomi/data/taste app/src/test/java/exh

If a command fails, document the limitation.

Create these two files:

1. docs/community/KMK_CONSOLIDATION_SNAPSHOT.md
2. docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md

File 1: KMK_CONSOLIDATION_SNAPSHOT.md

Include:

- snapshot date,
- branch and HEAD,
- remote,
- official/current Komikku baseline evidence,
- current app metadata,
- current KMK-Recs documented version,
- current KMK-OCR documented version,
- whether KMK-Recs and KMK-OCR appear separated,
- dirty worktree categories,
- generated artifacts/APKs/logs/local files that should not be committed,
- current docs status,
- current database/migration status,
- current backup/sync/proto areas touched,
- current test/build status from docs,
- immediate risks,
- explicit freeze recommendation.

File 2: KMK_FEATURE_CLASSIFICATION_MATRIX.md

Create a matrix covering every KMK feature area, including at minimum:

- For You recommendations
- Top Picks
- Manga taste rating: Love, Like, Dislike
- Seen/read manga filtering
- Tag preferences
- Tag aliases
- Source priority
- Recommendation language filter
- Source status diagnostics
- Sources To Try
- Source like/dislike
- Explicit porn/hentai source filter
- Sources To Try selective install
- Extension selective uninstall
- Source Evaluation
- Source Evaluation quarantine/unsafe handling
- Source Evaluation background execution
- Source Evaluation update reassessment
- Source Evaluation recommendation-quality probe
- Source Evaluation private/current/Shizuku installer handling
- Cross-extension rating matching
- Cross-extension favorite matching
- Cross-extension seen/read matching
- Cross-source link groups
- Loved Manga view
- Loved Manga duplicate grouping
- Best Version / chapter quality workflow
- Best Version page preview and migration/copy confirmation
- Source quality signal table
- Recommendation bundle export/import
- Recommendation bundle missing-source handling
- KMK What's New / release notes
- Backup/restore/sync additions
- OCR downloaded-text search
- OCR text index storage/cleanup
- OCR search quality and status diagnostics
- Extension repo/default repo changes if present
- Browse tab or settings visibility changes if present

Use these matrix columns:

- Feature area
- User-facing name
- Current status
- Entry points
- Main app files
- Domain/data files
- SQL tables and migrations
- Preferences
- Backup/sync behavior
- Network behavior
- Extension install/uninstall behavior
- Privacy/security risk
- UI/UX risk
- Tests
- Known gaps
- Komikku official/current pattern checks required
- Existing code alignment issues
- Formatting/style cleanup required
- Recommended classification
- Next phase owner

Use these classification labels:

- stable-personal
- experimental-personal
- community-candidate-after-cleanup
- community-candidate-small-extract
- ocr-only
- internal-diagnostic
- remove-or-defer
- needs-official-pattern-check

Very important:

For every feature, document whether the already-written KMK code appears to align with Komikku's local/current style for package placement, module boundaries, Screen/ScreenModel shape, Voyager route args, Compose UI, settings rows, string resources, coroutine usage, error models, SQLDelight style, backup/sync style, tests, and KMK markers.

If you find mismatches, record them. Do not fix them in this pass.

Do not mark any feature community-ready without evidence.

Treat Source Evaluation as experimental/security-sensitive unless the evidence clearly proves otherwise.

Treat OCR as OCR-only/experimental unless the user explicitly changes release strategy.

At the end, summarize:

- what baseline you used,
- what files you created,
- which features look stable personal,
- which features are experimental,
- which features may become community candidates after cleanup,
- which features should remain OCR-only,
- which generated/local artifacts should not be committed,
- what official Komikku patterns still need verification,
- what Phase 2 should do next.
```
