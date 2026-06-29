# KMK Phase 10-11 Architecture, Code Style, Testing, Device QA, And Release Readiness Plan

Date: 2026-06-26

Status: planning. Implementation is not approved until the user explicitly approves this phase.

Target implementation pass: Claude Code, after Phase 0-9 outputs exist and after user approval.

Related documents:

- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` once created
- `docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md` once created
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` once created
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` once created
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

Phase 10 refactors the KMK implementation so it better matches Komikku/Mihon/Tachiyomi-style architecture, naming, module boundaries, coroutine patterns, error handling, and string conventions.

Phase 11 creates a repeatable verification process for builds, unit tests, migration/backup tests, manual device QA, and release readiness.

The goal is not to add features. The goal is to make the existing fork code more maintainable, reviewable, and safer to share.

## Hard Rules

1. Do not change feature behavior unless the refactor requires it and the behavior change is documented.
2. Do not combine unrelated refactors into one giant rewrite.
3. Do not move code across modules unless dependency direction remains valid.
4. Do not remove `// KMK -->` markers without preserving equivalent fork traceability.
5. Do not add Komikku-specific strings outside `i18n-kmk` base resources.
6. Do not edit non-base locale strings.
7. Do not suppress errors just to make tests pass.
8. Do not remove tests unless they are obsolete and replaced with equivalent or better coverage.
9. Do not make OCR part of the default recommendation release line.
10. Verify current Komikku patterns before changing architecture/style.

## Main Code Areas

Claude should use the Phase 0-9 audit outputs to identify exact targets. Likely areas:

```text
app/src/main/java/exh/recs/
app/src/main/java/exh/ocr/
app/src/main/java/eu/kanade/tachiyomi/ui/browse/
app/src/main/java/eu/kanade/tachiyomi/ui/manga/
app/src/main/java/eu/kanade/tachiyomi/ui/setting/
domain/src/main/java/tachiyomi/domain/taste/
data/src/main/java/tachiyomi/data/taste/
data/src/main/sqldelight/tachiyomi/
i18n-kmk/src/commonMain/moko-resources/base/
app/src/test/java/exh/
```

## Phase 10 Required Changes: Architecture And Style

### 1. Refactor Target List

Before code edits, create a short markdown implementation target list:

```text
docs/architecture/KMK_ARCHITECTURE_REFACTOR_TARGETS.md
```

For each target:

- file/class,
- problem,
- current Komikku/local pattern,
- intended change,
- risk,
- tests required.

Do not refactor files that are not listed.

### 2. Oversized ScreenModel Reduction

Implementation:

- Identify ScreenModels that orchestrate UI, DB, source manager, extension manager, network, and scoring all at once.
- Extract pure logic to helper/policy classes only where it reduces real complexity.
- Keep side-effect code explicit and close to the boundary that owns it.
- Preserve existing behavior.

Expected candidates:

- Source Evaluation screen model,
- Best Version screen model,
- Recommendation Settings screen model,
- Recommendation Bundle import screen model,
- OCR indexing/search screen model.

### 3. Pure Logic Extraction

Implementation:

- Move scoring, filtering, grouping, sorting, status mapping, route-mode mapping, and validation into pure helpers where not already done.
- Add unit tests for extracted helpers.
- Avoid new global singleton state.
- Avoid Android dependencies in pure helpers.

### 4. Error/Result Model Cleanup

Implementation:

- Replace vague strings or generic exception paths with typed result/error models where practical.
- Keep user-facing messages in UI layer/string resources.
- Keep diagnostic detail available without leaking private data.
- Avoid broad `catch (Throwable)` unless specifically required to isolate extension/plugin crashes; document every case.

### 5. Coroutine And Lifecycle Alignment

Implementation:

- Compare current usage to nearby Komikku patterns:
  - `screenModelScope`,
  - `ioCoroutineScope`,
  - `launchIO`,
  - `withIOContext`,
  - WorkManager,
  - foreground notifications.
- Remove fire-and-forget DB writes where completion matters.
- Avoid UI state updates from stale jobs after cancellation.
- Ensure cancellation is respected in long-running flows.

### 6. String And Resource Cleanup

Implementation:

- Search KMK code for hardcoded user-facing strings.
- Move Komikku-specific strings to `i18n-kmk/src/commonMain/moko-resources/base/`.
- Use `KMR`.
- Do not edit translated locales.
- Fix mojibake in visible KMK strings.

### 7. Comment And Marker Cleanup

Implementation:

- Keep necessary `// KMK -->` / `// KMK <--` markers.
- Remove noisy AI-ish comments that narrate obvious code.
- Keep comments that explain security, migration, backup, or extension-boundary reasons.
- Do not erase historical context that future maintainers need.

### 8. Package And Module Boundary Review

Implementation:

- Keep app/UI code in app.
- Keep domain models/interactors in domain.
- Keep repositories/data implementation in data.
- Do not make domain depend on app.
- Avoid adding extension-manager dependencies to pure domain code.
- If a feature cannot be cleanly moved yet, document why.

## Phase 11 Required Changes: Testing, Device QA, Release Readiness

### 9. Test Checklist Document

Create:

```text
docs/testing/KMK_TEST_AND_RELEASE_CHECKLIST.md
```

Include:

- required local commands,
- release-build commands,
- clean install checklist,
- update install checklist,
- backup/restore checklist,
- sync checklist if sync is used,
- source evaluation checklist,
- recommendation flow checklist,
- cross-extension matching checklist,
- Best Version checklist,
- recommendation bundle import/export checklist,
- OCR checklist,
- offline/reconnect checklist,
- phone/tablet checklist,
- known unautomated risks.

### 10. Automated Test Gap Closure

Use Phase 3-9 audits to add tests where most needed.

Priority:

- migration tests for KMK migrations,
- backup/restore round-trip tests,
- recommendation bundle validation/import tests,
- source evaluation candidate/batch continuation tests,
- source evaluation cleanup/error mapping tests,
- cross-extension route-mode tests,
- Loved Manga grouping/filtering tests,
- Best Version selection/cancel tests,
- OCR status/search/ranker tests.

Do not write brittle UI tests unless the project already has a clear pattern.

### 11. Build And Formatting Verification

Run:

```text
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If making release-readiness claims, also run the appropriate release/preview task from current Komikku patterns.

If SQLDelight changes are made:

```text
./gradlew :data:generateSqlDelightInterface
```

Document failures honestly.

### 12. Manual QA Script

Create or update a manual QA checklist with exact user flows:

- rate manga Love/Like/Dislike/Seen,
- refresh For You,
- use Top Picks,
- source priority reorder,
- source evaluation private mode,
- source evaluation recommendation-quality probe,
- install/uninstall selected suggestions/extensions,
- cross-extension love/seen/favorite,
- Loved Manga duplicate grouping,
- Best Version preview, zoom, cancel, migrate/copy,
- recommendation bundle export/import,
- OCR index/search/clear,
- offline/reconnect,
- backup/restore,
- update install over previous APK.

## Documentation Updates

Create:

```text
docs/architecture/KMK_ARCHITECTURE_AND_STYLE_REFACTOR_IMPLEMENTATION.md
docs/testing/KMK_TEST_AND_RELEASE_CHECKLIST.md
```

Update:

```text
docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md
docs/recommendations/CURRENT_STATE.md
docs/ocr/README.md
```

Only update current-state docs with factual results.

## Acceptance Criteria

This phase is complete only if:

- Refactor targets were listed before refactoring.
- Major KMK code paths align better with local Komikku patterns.
- Hardcoded KMK UI strings are reduced or eliminated.
- Error handling is more typed and explainable.
- Long-running flows respect cancellation better.
- Pure logic has unit coverage.
- Test/release checklist exists.
- Required Gradle checks were run or limitations documented.
- OCR remains separated.
- Behavior changes, if any, are documented.

## Summary Claude Should Provide

Claude should report:

- baseline used,
- Komikku patterns checked,
- files refactored,
- behavior preserved or changed,
- tests added,
- commands run,
- failures/limitations,
- manual QA still needed,
- what remains not community-ready.
