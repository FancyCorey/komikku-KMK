# KMK Application-Wide Efficiency And Standardization Audit Plan

Date: 2026-07-11

Status: audit plan only. No feature, cleanup, refactor, migration, or release-build change is authorized by this document.

## Purpose

Perform a controlled, evidence-based audit of the entire Komikku forked application, including all KMK changes since the upstream baseline. The audit must identify:

- universal inefficiencies rather than isolated recommendation issues;
- non-standard implementation patterns that diverge from Komikku's documented architecture;
- duplicated or competing implementations of the same responsibility;
- unbounded network, database, storage, memory, coroutine, and UI work;
- lifecycle, cancellation, error-handling, backup/sync, privacy, and release risks;
- gaps between documentation, tests, and the actual code.

The goal is not to rewrite the app or make it look superficially uniform. The goal is to produce a prioritized, tested remediation backlog that preserves working behavior and aligns new KMK code with the project's documented conventions.

## Authoritative Standards

All findings must be assessed against the repository rather than generic Android preferences.

- `AGENTS.md` is the primary local engineering guide.
- `CONTRIBUTING.md` governs public fork/release expectations.
- `.editorconfig`, Spotless, and ktlint govern formatting.
- Existing official Komikku patterns in the nearest equivalent module take precedence over a newly invented abstraction.
- KMK-only strings use `KMR` from `i18n-kmk/src/commonMain/moko-resources/base/`.
- Dependency direction remains `app -> domain -> source-api`, while `data` implements domain repositories.
- SQLDelight schema changes require new migrations, generated interfaces, and migration verification.
- New KMK behavior remains clearly isolated in `// KMK --> ... // KMK <--` regions without damaging existing SY/EXH blocks.

## Current Baseline and Scope Warning

The repository currently has a large dirty worktree, including tracked modifications and untracked implementation files across recommendations, database migrations, backup/restore, library UI, source evaluation, source/extension handling, and documentation. A current `git diff --name-only` is not a reliable feature inventory by itself because much completed work remains uncommitted.

This makes a snapshot phase mandatory. No audit result should silently treat an uncommitted change as either definitely correct or definitely part of a single release until its provenance and intended version are recorded.

The current For You audit is a component report, not a substitute for this application-wide audit:

- `docs/recommendations/KMK_RECS_FOR_YOU_FILTERING_AND_PERFORMANCE_FEASIBILITY_AUDIT.md`

## Audit Rules

1. Read-only first. Do not make code changes while identifying problems.
2. Every finding needs a code reference, reproducible condition, severity, expected impact, and recommended disposition: fix, defer, accept, or needs device evidence.
3. Do not call something inefficient merely because it uses a coroutine, cache, database, or abstraction. Establish repeated work, blocking behavior, retained data, duplicated state, or measurable user impact.
4. Preserve compatibility boundaries: extension ABI, backup compatibility, existing app data, and current sync semantics.
5. Do not collapse separate systems merely because their names look similar. Shared helpers require proven common semantics.
6. Build and device behavior are separate evidence categories. Static review cannot prove battery, jank, memory pressure, Android lifecycle, or extension-site behavior.
7. Any later implementation is split into focused change sets with their own plan, tests, migration review, and verification. No giant cleanup commit.

## Audit Phases

### Phase A - Baseline, Provenance, and Change Inventory

Objective: establish what the fork actually contains before judging it.

Review:

- current branch, upstream relationship, last known upstream baseline, dirty/untracked files, generated files, and build variants;
- all KMK-marked regions and files changed since the baseline;
- feature-to-version mapping, including whether docs, release notes, migrations, and APK naming agree;
- public/test/private application ID and signing boundaries;
- duplicate or stale documents that conflict with current code.

Deliverables:

- a machine-readable change inventory grouped by subsystem;
- a version/provenance matrix;
- a list of files whose ownership or intended release is ambiguous;
- a baseline verification record using `spotlessCheck`, relevant unit tests, and an appropriate assemble task, subject to available environment.

Exit condition: every later finding can name the version and subsystem it belongs to.

### Phase B - Architecture and Standardization Review

Objective: assess whether KMK additions follow Komikku's actual layering and UI conventions.

Review:

- app/UI, domain/interactor, repository, and SQLDelight responsibilities;
- Injekt registration and ownership; no accidental UI-to-data shortcuts where a domain operation belongs;
- Screen/ScreenModel state ownership, navigation route serialization, lifecycle cancellation, and long-running work placement;
- preference key ownership, typed state, serialization formats, and migration needs;
- KMR/MR/SYMR string placement, hardcoded UI strings, and raw exception text exposed to users;
- KMK marker quality: accurate scope, no misleading version labels, and no interleaving that makes future upstream merges unsafe;
- duplicated screens, models, data classes, scorers, normalizers, or query helpers that could diverge.

Deliverables:

- architecture findings grouped into: required correction, safe refactor candidate, accepted fork divergence, and no-action;
- explicit extraction candidates only where reuse removes real duplicate behavior;
- a list of style-only issues that should wait for touched-file cleanup instead of causing churn.

### Phase C - Runtime Efficiency: Network, Coroutines, and Background Work

Objective: find expensive, repeated, uncancelled, or poorly bounded work.

Review:

- source/extension search, global search, migration, recommendation, evaluation, OCR, image loading, tracker, sync, download, and update paths;
- request fan-out, concurrency caps, source timeouts, retry/backoff policy, cancellation propagation, and work continued after a screen disappears;
- main-thread network/database violations and dispatcher ownership;
- foreground-service/WorkManager expectations for work that should survive navigation or process death;
- duplicate refreshes caused by state collection, recomposition, screen restoration, or preference changes;
- cache hit/miss behavior, invalidation fingerprints, cache bounds, and manual-refresh behavior.

Required measurements where static review is insufficient:

- refresh duration and per-source request count;
- device CPU, battery, memory, and retained-job behavior for source evaluation and OCR;
- cancellation time when leaving a screen;
- recovery after offline/online transitions.

Deliverables:

- a per-workflow cost model and boundedness table;
- a small instrumentation proposal using existing logging conventions, gated for debug/internal use;
- fixes ranked by user impact and device risk.

### Phase D - Database, Cache, Storage, Backup, and Sync Review

Objective: make data lifecycle explicit and prevent silent storage or compatibility regressions.

Review:

- every KMK SQLDelight table, index, migration, mapper, repository, and DI binding;
- retention/pruning for non-library manga, recommendation cache, discovery memory/progress, OCR text index, image/page preview data, and source evaluation records;
- query indexes, N+1 query patterns, large serialized preference values, and full-table scans on UI paths;
- backup proto numbers, backup creation/restoration, sync merge behavior, and intentional local-only derived data;
- schema migration idempotency and forward upgrade paths from older user databases;
- deletion/reset behavior and whether it clears the intended data without touching user library/history.

Deliverables:

- a data-lifecycle matrix: owner, size bound, expiry/pruning, backup, sync, reset path, and privacy sensitivity;
- migration and restoration test gaps;
- required corrections before public sharing.

### Phase E - UI Responsiveness, State, Accessibility, and Error Recovery

Objective: ensure screens remain usable under loading, failure, rotation/process restoration, large lists, and small screens.

Review:

- Compose collection keys, stable state, list reorder/selection behavior, lazy-list work, image loading, and recomposition-sensitive calculations;
- loading, partial-result, empty, offline, retry, cancellation, and error states across KMK screens;
- dialogs and navigation actions that can become stuck or produce non-serializable saved state;
- visibility and discoverability of important actions without relying on hidden long-press-only behavior;
- touch target size, content descriptions, screen-reader labels, text overflow, and tablet/phone layout;
- whether diagnostics expose raw exceptions, source URLs, or internal state unnecessarily.

Deliverables:

- reproducible UI/state failure list;
- targeted screenshot/device test matrix;
- fixes split into behavior-critical and optional polish.

### Phase F - Security, Privacy, and Extension Trust Review

Objective: verify that the fork does not widen risk through temporary extension installation, Shizuku, source evaluation, OCR, imports, or diagnostics.

Review:

- extension repository trust, signature verification, temporary install/uninstall behavior, Shizuku/private installer modes, foreground notifications, and cleanup after failure/process death;
- export/import bundle validation, file size limits, malformed data, ambiguous source resolution, and user consent;
- OCR index content sensitivity, storage disclosure, deletion, backup/sync exclusions, and log redaction;
- network logging and raw exception exposure;
- secret/configuration handling for public/test builds;
- public fork application ID, updater endpoint, telemetry, signing, and crash reporting boundaries.

Deliverables:

- security/privacy findings with exploitability and user-impact assessment;
- mandatory public-release blockers versus acceptable private-beta risks;
- explicit threat-model notes for extension evaluation and content indexing.

### Phase G - Tests, Device Validation, and Release Readiness

Objective: establish that behavior is verified beyond compilation.

Review:

- unit, migration, backup/restore, repository, and screen-model test coverage for every KMK subsystem;
- missing regression tests for previously reported crashes;
- debug/release/public-test build differences;
- Android version, phone/tablet, offline, low-memory, process-death, extension-failure, and upgrade testing;
- Spotless, compile, unit test, and assemble coverage aligned with `AGENTS.md`.

Deliverables:

- a lean verification matrix defining what must run for each change class;
- a device QA checklist;
- release readiness verdicts for personal, private beta, public test, and upstream contribution.

## Initial Evidence Already Established

These are starting points, not final universal findings:

- The active For You manual refresh can be expensive by design: up to 40 source attempts, two query strategies per source, an additional discovery page, candidate localization, and bounded detail enrichment.
- For You localizes network candidates into the local manga database, so discovery has a persistent storage cost beyond its explicit cache tables.
- v0.7.39 has two verified correctness gaps: additional-page candidates can wait until a later refresh to appear, and transient additional-page failures can become permanently skipped progress.
- The current worktree contains a large amount of uncommitted and untracked KMK work, so documentation alone cannot be treated as a complete release inventory.
- `AGENTS.md` supplies clear expected conventions for localization, dependency direction, DI, SQLDelight migrations, markers, formatting, and verification. The audit will assess compliance against those standards.

## What This Audit Will Not Do

- It will not replace all existing code with a new architecture.
- It will not normalize code merely for appearance.
- It will not make source-specific website assumptions without evidence.
- It will not make device-performance claims without device profiling.
- It will not bundle unrelated fixes into one APK merely because the audit discovered them together.

## Recommended Order

Run Phases A through D first. They establish the true code/data baseline and are most likely to reveal issues that make later UI or filter work unsafe. Then run E and F together for user-visible robustness and security. Finish with G once remediation plans are known.

After each phase, create a separate findings document and update the markdown encyclopedia. Only then create focused implementation plans, one subsystem at a time.

