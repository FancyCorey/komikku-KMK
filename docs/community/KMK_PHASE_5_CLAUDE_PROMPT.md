# Claude Code Prompt: KMK Phase 5 Source Evaluation Hardening

Use this prompt in Claude Code after the user approves Phase 5 implementation.

```text
You are working in the Komikku KMK fork workspace. Implement Phase 5 only: Source Evaluation hardening.

Do not work on OCR, Recommendation Bundle import/export, Best Version, Loved Manga, general recommendation UX, architecture-wide refactors, public release docs, or unrelated cleanup unless directly required for Source Evaluation hardening.

Scope baseline:

Evaluate and modify the full KMK Source Evaluation delta against current/latest Komikku v1.13.6 patterns, not only the latest KMK change. Verify local patterns before editing.

Read these files first:

- AGENTS.md
- docs/community/KMK_CONSOLIDATION_SNAPSHOT.md
- docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md
- docs/community/KMK_DATABASE_BACKUP_SYNC_AUDIT.md if path exists, otherwise docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md
- docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md
- docs/community/KMK_PHASE_3_4_RISK_REGISTER.md
- docs/community/KMK_PHASE_5_SOURCE_EVALUATION_HARDENING_IMPLEMENTATION_PLAN.md
- docs/recommendations/CURRENT_STATE.md
- docs/recommendations/NEXT_WORK.md

Primary implementation goals:

1. Add explicit Source Evaluation consent/warning before evaluation starts.
   - Explain that evaluation can temporarily install extensions, load source definitions, run source/network methods, score results, and attempt cleanup.
   - Explain Private vs Current vs Shizuku behavior using verified current code behavior.
   - Explain cleanup may require Android prompts depending on installer mode.
   - Use KMR strings in i18n-kmk base resources. No hardcoded user-facing strings.
   - Provide a way to view the warning again.

2. Make background/process-death behavior durable or honestly recoverable.
   - Inspect SourceEvaluationJob, SourceEvaluationJobState, SourceEvaluationQueueState, notifier, and screen model.
   - If queue/options can be persisted safely, persist enough to recover.
   - If not, prevent pretending it can recover and show a clear recoverable state-lost message.
   - Notification tap should return to the Source Evaluation/progress screen.

3. Add cleanup outcome visibility.
   - Record or display per-extension cleanup outcome:
     cleaned_up_silently, pre_existing_extension_left_alone, prompt_required, cleanup_failed, not_attempted_due_to_error, unknown.
   - If DB changes are needed, follow SQLDelight migration style and add tests.
   - If session-only is safer for now, document that limitation clearly.
   - Show cleanup failures/prompt-required leftovers in Source Evaluation UI.

4. Fix installed-extension filtering and state clarity.
   - Source Evaluation suggestion/evaluation lists should exclude currently installed extensions by default unless user explicitly chooses to show installed.
   - The show/hide installed toggle must work immediately without leaving and re-entering the screen.
   - Installed keys should be reactive to installedExtensionsFlow, not stale snapshots.
   - Show clear counts: not installed + unevaluated, hidden installed, hidden unsafe/quarantined, hidden explicit, skipped already evaluated.

5. Fix evaluation cursor and batch continuation.
   - Evaluation should not be trapped in the first 10/25/50/100 candidates.
   - Each subsequent batch should continue from remaining eligible unevaluated candidates when skip-already-evaluated is enabled.
   - Reassessment should intentionally re-evaluate when requested, but normal evaluation should progress.
   - Add/update pure tests for candidate continuation.

6. Harden recommendation-quality probe for Strong Fit / Worth Trying sources.
   - Only probe eligible promising sources unless user explicitly re-checks all.
   - For non-installed promising sources: resolve available extension, install using safe evaluation path, resolve installed source, run bounded probe, persist result, cleanup using same cleanup policy.
   - Fail with stage-specific error messages, not generic ERROR.
   - Recommendation-quality failure must not lower normal source/library fit score.

7. Improve quarantine/unsafe UI.
   - Quarantine/blocked sections should not dominate the top of the screen except after a fresh crash recovery event.
   - Provide collapsed view/counts and explicit Show quarantined / Show blocked controls.
   - Clear/remove actions require confirmation.

8. Standardize Source Evaluation error categories.
   Include at least:
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

9. Improve network-loss handling for Source Evaluation.
   - Offline/no-network should not crash or poison state.
   - Source/network failures should be per-source/per-extension failures where possible.
   - UI should offer retry/refresh when appropriate.

Main code areas to inspect and modify only as needed:

- app/src/main/java/exh/recs/evaluation/
- app/src/main/java/exh/recs/settings/
- app/src/main/java/exh/recs/sourceprefs/
- app/src/main/java/eu/kanade/tachiyomi/extension/
- app/src/main/java/eu/kanade/tachiyomi/extension/util/
- app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt
- domain/src/main/java/tachiyomi/domain/taste/
- data/src/main/java/tachiyomi/data/taste/
- data/src/main/sqldelight/tachiyomi/data/
- data/src/main/sqldelight/tachiyomi/migrations/
- i18n-kmk/src/commonMain/moko-resources/base/
- app/src/test/java/exh/recs/evaluation/

Do not touch unrelated features unless compilation requires a narrow integration fix.

Documentation updates required after implementation:

- docs/recommendations/CURRENT_STATE.md
- docs/recommendations/NEXT_WORK.md
- docs/community/KMK_PHASE_3_4_RISK_REGISTER.md
- create docs/recommendations/KMK_RECS_SOURCE_EVALUATION_COMMUNITY_HARDENING_IMPLEMENTATION.md

The implementation note must include:

- baseline/patterns checked,
- files changed,
- behavior changed,
- database/migration changes if any,
- strings/resources changed,
- tests added/updated,
- commands run,
- manual QA steps,
- remaining risks.

Validation commands:

Run at minimum:

- ./gradlew spotlessApply
- ./gradlew spotlessCheck
- ./gradlew :app:testDebugUnitTest
- ./gradlew assembleDebug

If SQLDelight changes are made, also run:

- ./gradlew :data:generateSqlDelightInterface

If any command cannot run, document why.

Acceptance criteria:

- Source Evaluation warning/consent exists and is user-accessible.
- Process-death/background behavior is durable or honestly recoverable.
- Cleanup outcomes are visible.
- Installed extensions do not clutter not-installed evaluation lists by default.
- Batch continuation can proceed beyond the first selected batch.
- Recommendation-quality probe works for eligible non-installed promising sources with install/probe/cleanup.
- Quarantine/unsafe sections are not visually dominant by default.
- Error messages are stage-specific.
- Network loss does not crash or poison evaluation state.
- Docs/tests are updated.
- No unrelated features were changed.
```

