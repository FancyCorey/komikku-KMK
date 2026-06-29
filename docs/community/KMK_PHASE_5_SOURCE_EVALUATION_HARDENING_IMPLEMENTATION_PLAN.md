# KMK Phase 5 Source Evaluation Hardening Implementation Plan

Date: 2026-06-26

Status: planning. Implementation is not approved until the user explicitly approves this phase.

Target implementation pass: Claude Code, after Phase 0-4 outputs exist and after user approval.

Related documents:

- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_PHASE_0_1_SNAPSHOT_AND_FEATURE_CLASSIFICATION_PLAN.md`
- `docs/community/KMK_PHASE_2_REPOSITORY_AND_DOCUMENTATION_HYGIENE_PLAN.md`
- `docs/community/KMK_PHASE_3_4_DATABASE_SECURITY_PRIVACY_AUDIT_PLAN.md`
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` once created
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` once created
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` once created
- `docs/recommendations/CURRENT_STATE.md`
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

This phase hardens Source Evaluation enough that it can remain as an experimental feature without destabilizing the rest of the app.

Source Evaluation is the highest-risk KMK-Recs subsystem because it can:

- inspect available extension repos,
- temporarily install extensions,
- load extension sources,
- execute source methods and network calls,
- score source quality,
- probe recommendation quality,
- uninstall temporary extensions,
- quarantine unsafe extension evaluations,
- run in or near background execution paths.

The goal is not to make Source Evaluation upstream-ready. The goal is to make it bounded, understandable, recoverable, and honest.

## Pre-Implementation Requirements

Before Claude changes code, it must verify the Phase 3-4 audit outputs and summarize the relevant findings in the implementation report.

Required files:

```text
docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md
docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md
```

If these files do not exist, Claude must stop and ask the user whether to proceed anyway.

Claude must also verify current Komikku/local patterns before editing:

- extension install/uninstall UI,
- extension installer preference handling,
- WorkManager or foreground job patterns,
- notification tap/deep-link patterns,
- error/result display in settings screens,
- SQLDelight migration style if database changes are needed,
- `KMR`/`i18n-kmk` string style.

## Non-Goals

Do not:

- redesign all Recommendation Settings,
- change For You scoring,
- change OCR,
- change Best Version,
- change recommendation bundle import/export,
- add new extension repositories,
- make Source Evaluation default-on,
- remove existing user data,
- delete quarantine records automatically,
- rewrite unrelated extension manager code.

## Implementation Areas

Claude should inspect and limit changes mainly to:

```text
app/src/main/java/exh/recs/evaluation/
app/src/main/java/exh/recs/settings/
app/src/main/java/exh/recs/source/
app/src/main/java/eu/kanade/tachiyomi/extension/
app/src/main/java/eu/kanade/tachiyomi/ui/setting/
domain/src/main/java/tachiyomi/domain/taste/
data/src/main/java/tachiyomi/data/taste/
data/src/main/sqldelight/tachiyomi/data/
data/src/main/sqldelight/tachiyomi/migrations/
i18n-kmk/src/commonMain/moko-resources/base/
app/src/test/java/exh/recs/
```

Only touch database/migration files if the audit proves a durable-state fix is required.

## Required Changes

### 1. Explicit Experimental Consent Before Evaluation

Problem:

Source Evaluation can install and execute extension source code. Community users need a clear consent step.

Implementation:

- Add a first-run or pre-run consent dialog/card before starting Source Evaluation.
- The warning must state that evaluation may temporarily install extensions, run source/network methods, and then attempt cleanup.
- The warning must explain that some installer modes may require Android prompts for cleanup.
- The warning must distinguish Private, Current, and Shizuku behavior using verified current behavior from code.
- Store consent in a preference only if existing settings patterns support it.
- Provide a way to view the warning again from Source Evaluation settings.

Constraints:

- Use `KMR` strings in `i18n-kmk` base resources.
- Do not use hardcoded user-facing strings.
- Do not overstate safety.

Tests:

- Add a pure policy/state test if consent logic is extracted.
- Manual QA instructions must include first-run, accepted, and warning-review flows.

### 2. Durable Or Honest Background Evaluation State

Problem:

The earlier audit flagged that job inputs may live in memory. If process death occurs, evaluation can fail or lose state.

Implementation decision:

Choose one of these after code inspection:

- Preferred if feasible: persist the evaluation queue/options enough for WorkManager/process recovery.
- Acceptable if persistence is too large: prevent background execution from pretending it can survive process death, and show a clear "evaluation state was lost, restart evaluation" state.

Required behavior:

- Evaluation must not silently fail if the app process is killed.
- Notification tap must return to the Source Evaluation/progress screen.
- If state is lost, show a user-facing recoverable error.
- Do not continue with a partially unknown queue.

Tests:

- Add pure state restoration tests if queue serialization/policy is extracted.
- Add manual QA steps for app background, app close, and notification tap.

### 3. Cleanup Outcome Ledger

Problem:

Temporary extension cleanup can fail or require prompts. Users need a clear post-run result.

Implementation:

- Record cleanup outcome per evaluated extension:
  - `cleaned_up_silently`
  - `pre_existing_extension_left_alone`
  - `prompt_required`
  - `cleanup_failed`
  - `not_attempted_due_to_error`
  - `unknown`
- If a DB change is required, follow current migration style and add tests.
- If DB change is not required, store this as current-session diagnostics but explain limitation.
- Show cleanup failures or prompt-required leftovers in Source Evaluation UI.
- Provide a manual action link/button only if it follows existing extension management patterns.

Constraints:

- Do not uninstall anything automatically outside the existing evaluated-extension cleanup path.
- Do not hide cleanup failures behind generic "completed" states.

### 4. Installed Extension Filtering And Status Clarity

Problem:

Installed extensions sometimes appear where the user expects only not-installed suggestions/evaluations, taking visual space and creating confusion.

Implementation:

- Ensure Source Evaluation suggestions/default list excludes currently installed extensions unless the user explicitly selects a "show installed" style option.
- Ensure the show/hide installed toggle works immediately without requiring leaving/re-entering the screen.
- Ensure installed state is based on reactive installed extension flow, not stale one-time snapshots.
- Make counts clear:
  - not installed and unevaluated,
  - hidden installed,
  - hidden unsafe/quarantined,
  - hidden explicit,
  - skipped already evaluated.

Tests:

- Unit-test the display filter if pure.
- Include installed/uninstalled reactive update in manual QA.

### 5. Evaluation Cursor And Batch Continuation

Problem:

Evaluation/reassessment should not be trapped in the first 10/25/50/100 candidates forever.

Implementation:

- Review candidate ordering and skip/evaluated filters.
- Ensure each batch can continue from the next eligible unevaluated candidate.
- Add a visible "remaining not installed and unevaluated" count that updates after each completed batch.
- Add clear behavior for "skip already evaluated" on/off.
- Reassessment should be able to process updated evaluated extensions and then continue later.

Tests:

- Pure tests for candidate selection over multiple batches.
- Test that batch 1 and batch 2 do not evaluate the exact same set unless reassessment is intentionally selected.

### 6. Recommendation-Quality Probe Reliability

Problem:

Promising sources marked Strong Fit or Worth Trying need a second recommendation-quality probe, but previous versions produced all-error results when non-installed extension resolution/install/probe was incomplete.

Implementation:

- For non-installed promising evaluations:
  1. Resolve the available extension using the current resolver.
  2. Temporarily install it using the same safe/private evaluation installer path.
  3. Resolve the installed extension and source.
  4. Run bounded recommendation-quality probes only for that source.
  5. Persist fit result with detailed success/error reason.
  6. Cleanup using the same cleanup policy as normal evaluation.
- Do not probe every source, only eligible Strong Fit/Worth Trying sources unless user explicitly re-checks.
- Do not make recommendation-quality failure lower the source's normal library/source score. It is a separate signal.
- Show one of:
  - Recommendations: Great
  - Recommendations: Good
  - Recommendations: Mixed
  - Recommendations: Weak
  - Recommendations: No matches
  - Recommendations: Error
  - Recommendations: Not checked
  - Recommendations: Too little evidence

Tests:

- Resolver tests for available extension resolution.
- Installed source resolver tests.
- Queue partition tests.
- Error mapping tests.

### 7. Crash/Unsafe Quarantine UX

Problem:

Unsafe/quarantined sources should not dominate the top of the UI, but they must remain inspectable.

Implementation:

- Keep quarantine and blocked/unsafe sections collapsed or behind explicit controls by default.
- Do not show quarantine/blocked extension cards at the very top unless a fresh crash recovery event needs immediate attention.
- Provide:
  - "Show quarantined sources"
  - "Show blocked/unsafe extensions"
  - clear counts
  - reset/remove actions guarded by confirmation.
- If the app detected a recent crash marker, show a concise alert with action to view details.

Tests:

- Pure display/state tests if extracted.
- Manual QA: fresh crash marker vs normal no-crash state.

### 8. Source Evaluation Error Model

Problem:

Errors are currently spread across source evaluation, installer, probe, cleanup, and UI paths.

Implementation:

- Standardize source evaluation error categories:
  - install timeout
  - install failed
  - source load timeout
  - source not found
  - popular probe failed
  - latest probe failed
  - search probe failed
  - recommendation probe failed
  - cleanup prompt required
  - cleanup failed
  - unsafe/quarantined
  - network unavailable
  - process state lost
  - unknown internal error
- Map errors to user-facing short messages and diagnostic detail.
- Avoid writing every failure as generic ERROR with no actionable cause.

Constraints:

- Use `KMR` strings for UI.
- Keep detailed diagnostics copyable, but avoid leaking private OCR text or unnecessary manga data.

### 9. Network Loss Handling For Evaluation

Problem:

Evaluation and online searches should fail gracefully when network is unavailable and recover after reconnection.

Implementation:

- Before starting a batch, detect obvious offline/no network state if existing app utilities support it.
- During source/network probes, catch network unavailable/timeouts as per-source failures, not whole-app crashes.
- UI should offer retry/refresh after connection returns.
- Do not require fully exiting and reopening the app to recover online functionality.

Tests:

- Unit-test error mapping where possible.
- Manual QA for Wi-Fi off/on during evaluation.

### 10. Documentation Updates

Claude must update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md
```

Only update them with factual implementation results.

Also create:

```text
docs/recommendations/KMK_RECS_SOURCE_EVALUATION_COMMUNITY_HARDENING_IMPLEMENTATION.md
```

The implementation note must include:

- files changed,
- behavior changed,
- database changes if any,
- tests added/run,
- risks remaining,
- manual QA steps.

## Validation

Run, at minimum:

```text
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If the environment cannot run one of these, document why.

If SQLDelight files change, also run or trigger:

```text
./gradlew :data:generateSqlDelightInterface
```

## Acceptance Criteria

This phase is complete only if:

- Source Evaluation has an explicit consent/warning flow.
- Background/process-death behavior is either durable or honestly recoverable.
- Cleanup outcomes are visible and actionable.
- Installed extensions do not clutter not-installed evaluation lists by default.
- Evaluation batches can continue beyond the first selected batch.
- Recommendation-quality probe works for eligible non-installed Strong Fit/Worth Trying sources using install/probe/cleanup.
- Quarantine/unsafe sections are not visually dominant by default.
- Error categories are more specific than generic ERROR.
- Network loss does not crash or poison state.
- Docs are updated.
- Tests/builds are run or limitations are documented.

## Summary Claude Should Provide

Claude should report:

- which official/current Komikku patterns were checked,
- what code was changed,
- what user-facing behavior changed,
- whether database/migration changes were made,
- whether backup/sync behavior changed,
- what tests passed,
- what manual QA is still needed,
- what remains experimental.


