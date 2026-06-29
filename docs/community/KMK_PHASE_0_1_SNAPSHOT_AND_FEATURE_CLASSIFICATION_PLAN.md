# KMK Phase 0-1 Snapshot And Feature Classification Plan

Date: 2026-06-26

Status: planning. Documentation-only. No app code changes are approved by this plan.

Target implementation/audit pass: Claude Code, after user approval.

Related documents:

- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
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

This plan covers the first consolidation pass before any community-facing cleanup or refactor work.

The goal is not to change behavior. The goal is to make the current fork state clear enough that later phases can be done safely.

Phase 0 creates a stable snapshot of the current repo, release lines, feature versions, generated artifacts, and known risks.

Phase 1 creates a feature classification matrix that lists every KMK feature, where it lives, what it touches, what risks it carries, whether it is stable enough for personal use, and whether it has any realistic path toward community sharing.

## Hard Rules

1. Do not make app code changes in this phase.
2. Do not delete, move, archive, or rename files in this phase unless the user explicitly approves that exact action.
3. Do not assume Komikku's expected format, architecture, UI pattern, versioning pattern, contribution style, string location, build convention, or release practice.
4. Verify Komikku patterns from current repo files first.
5. If local evidence is insufficient, use official/current Komikku or upstream/community references and document the links used.
6. If KMK code conflicts with Komikku's current local or official pattern, document the conflict instead of silently rewriting it.
7. Review existing KMK code against Komikku's current formatting, naming, package, architecture, string-resource, navigation, coroutine, error-handling, database, migration, backup, and UI conventions.
8. Flag every visible mismatch so later cleanup can align the previous lines of code with Komikku's style before any community sharing.
9. Do not automatically rewrite mismatched code in this phase. Record the mismatch, affected files, expected Komikku pattern, and recommended later phase.
10. Treat KMK-Recs and KMK-OCR as separate release lines unless the user explicitly decides otherwise.
11. Treat Source Evaluation as experimental and security-sensitive.
12. Treat OCR as experimental, privacy-sensitive, and separate from the standard recommendation APK line.
13. Every conclusion must distinguish between verified facts, local inference, and unanswered questions.

## Phase 0 Scope: Freeze And Snapshot

Phase 0 should create:

```text
docs/community/KMK_CONSOLIDATION_SNAPSHOT.md
```

This file should document the current state before cleanup begins.

### Phase 0 Inputs To Read

Claude must read at minimum:

- `AGENTS.md`
- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/DOCUMENTATION_RULES.md`
- `docs/ocr/README.md`
- `docs/ocr/KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_IMPLEMENTATION.md`
- `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_IMPLEMENTATION.md`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `data/src/main/sqldelight/tachiyomi/migrations/`
- `data/src/main/sqldelight/tachiyomi/data/`

Claude should also inspect the current worktree with read-only commands such as:

```text
git status --short
git branch --show-current
git diff --name-only
rg --files docs
rg --files app/src/main/java/exh/recs app/src/main/java/exh/ocr domain/src/main/java/tachiyomi/domain/taste data/src/main/java/tachiyomi/data/taste app/src/test/java/exh
```

If a command is unavailable or blocked, document that limitation instead of guessing.

### Snapshot Contents

`KMK_CONSOLIDATION_SNAPSHOT.md` should include:

- Snapshot date and local branch name.
- Current app version information from the repo.
- Current documented KMK-Recs version.
- Current documented KMK-OCR version.
- Current documented APK naming examples.
- Whether the repo currently appears to be one mixed feature line or separated release lines.
- Working tree categories:
  - modified source files,
  - modified docs,
  - new/untracked source files,
  - new/untracked tests,
  - generated APKs or build artifacts,
  - local-only files that should not be committed.
- Current feature-doc status:
  - recommendation docs,
  - OCR docs,
  - community consolidation docs,
  - stale or historical plans that may need archive later.
- Current database/migration status:
  - latest migration number found,
  - KMK/OCR tables visible in SQLDelight files,
  - backup/proto areas touched by KMK features, if identifiable from docs.
- Current test/build status from documentation only.
- Known immediate risks:
  - crash-prone source evaluation paths,
  - OCR privacy/storage/device risk,
  - backup/sync/migration compatibility risk,
  - generated APK artifacts,
  - stale docs,
  - UI/UX complexity,
  - source installer behavior.
- Explicit freeze recommendation:
  - no new feature work except crash fixes, data-loss fixes, security fixes, and documentation corrections.

### Snapshot Non-Goals

Do not:

- rewrite docs,
- clean generated files,
- change Gradle,
- change source code,
- change migrations,
- change backup schemas,
- run broad formatting,
- create release builds,
- archive historical plans.

Those are later phases.

## Phase 1 Scope: Feature Classification Matrix

Phase 1 should create:

```text
docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md
```

This file should become the index for all later cleanup phases.

### Classification Labels

Use these labels consistently:

- `stable-personal`: works well enough for personal use, but not necessarily community-ready.
- `experimental-personal`: useful but still risky, rough, or device/source-dependent.
- `community-candidate-after-cleanup`: possible public/community feature after style, test, security, and UX cleanup.
- `community-candidate-small-extract`: a smaller part of the feature may be shareable even if the full system is not.
- `ocr-only`: belongs only to the OCR branch/APK line.
- `internal-diagnostic`: useful for debugging or power users, not normal public UI.
- `remove-or-defer`: likely too risky, stale, or not useful enough right now.
- `needs-official-pattern-check`: cannot be classified confidently until Komikku's current pattern is verified.

A feature may have more than one label.

### Matrix Columns

The matrix should include these columns:

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

Keep entries concise, but do not hide uncertainty.

### Required Feature Rows

The matrix must include at least:

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
- Extension repo/default repo changes, if present
- Browse tab or settings visibility changes, if present

If Claude finds additional KMK features not listed here, add them rather than forcing them into an existing row.

### Required Source Areas To Inspect

Claude should inspect the following paths enough to classify features accurately:

```text
app/src/main/java/exh/recs/
app/src/main/java/exh/ocr/
app/src/main/java/eu/kanade/tachiyomi/ui/browse/
app/src/main/java/eu/kanade/tachiyomi/ui/manga/
app/src/main/java/eu/kanade/tachiyomi/ui/setting/
app/src/main/java/eu/kanade/tachiyomi/extension/
app/src/main/java/eu/kanade/domain/
domain/src/main/java/tachiyomi/domain/taste/
data/src/main/java/tachiyomi/data/taste/
data/src/main/sqldelight/tachiyomi/data/
data/src/main/sqldelight/tachiyomi/migrations/
app/src/test/java/exh/
i18n-kmk/src/commonMain/moko-resources/base/
```

Also inspect backup/sync files that are referenced by docs or search results for:

```text
BackupMangaTaste
BackupTagTaste
BackupTagAlias
BackupDisabledRecommendationSource
CrossSource
source_evaluation
ocr
recommendation
```

### Official Komikku Pattern Checks

For every feature that may become community-facing, the matrix must state which Komikku pattern needs verification before cleanup.

Examples:

- Settings screens: compare against current Komikku preference/settings screen organization.
- Strings: verify `KMR` and `i18n-kmk` base-resource usage for Komikku-only strings.
- Navigation: verify Voyager route args follow current local style and Android state-save safety.
- Database: verify SQLDelight migration style and current migration numbering.
- Backup: verify existing backup model/proto extension style and reserved field collision risk.
- Extension install/uninstall: verify existing extension manager and updater UI patterns.
- Notifications/background jobs: verify current WorkManager/notification style before changing source evaluation jobs.
- What's New: verify current Komikku local/upstream release note pattern before adding KMK release notes.
- Public docs: verify current Komikku README/release style before writing community docs.

If the pattern is not obvious locally, mark `needs-official-pattern-check`.

## Expected Outputs

At the end of this phase, Claude should have created:

```text
docs/community/KMK_CONSOLIDATION_SNAPSHOT.md
docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md
```

Claude may update `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` only if it needs to add a short pointer to these new files. It should not rewrite the master plan.

## Acceptance Criteria

This phase is complete only if:

- No app source code was changed.
- No source files, migrations, resources, or tests were moved or deleted.
- `KMK_CONSOLIDATION_SNAPSHOT.md` records current repo/app/docs/build state.
- `KMK_FEATURE_CLASSIFICATION_MATRIX.md` covers every major KMK feature area.
- Source Evaluation is explicitly labeled experimental/security-sensitive unless strong evidence says otherwise.
- OCR is explicitly labeled OCR-only/experimental unless the user changes release strategy.
- Each community-readiness claim is evidence-based.
- Every uncertain claim is marked as uncertain.
- Every feature row lists the official/current Komikku pattern checks needed before cleanup.
- Generated APKs or local artifacts are identified, not removed.
- Later phases can use the matrix as their starting index.

## Suggested Summary For User Review

When Claude finishes, it should summarize:

- what current state it found,
- which features are stable personal features,
- which features are experimental,
- which features might be community candidates after cleanup,
- which features should stay OCR-only,
- which files/artifacts look generated or not suitable for commit,
- which official Komikku patterns still need verification,
- what phase should happen next.

## Important Reminder For Claude

This is a consolidation audit phase, not an implementation phase.

If you discover a bug, stale file, generated APK, security concern, or broken documentation link, record it in the snapshot or matrix. Do not fix it unless the user explicitly approves a later implementation phase.




