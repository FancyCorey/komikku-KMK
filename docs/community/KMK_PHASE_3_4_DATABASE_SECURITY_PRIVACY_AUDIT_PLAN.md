# KMK Phase 3-4 Database, Backup, Sync, Security, And Privacy Audit Plan

Date: 2026-06-26

Status: planning. Audit-only unless the user separately approves implementation. No app code, database, migration, backup, sync, or security-flow changes are approved by this plan.

Target implementation/audit pass: Claude Code, after Phase 0-2 documentation exists and after user approval.

Related documents:

- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_PHASE_0_1_SNAPSHOT_AND_FEATURE_CLASSIFICATION_PLAN.md`
- `docs/community/KMK_PHASE_2_REPOSITORY_AND_DOCUMENTATION_HYGIENE_PLAN.md`
- `docs/community/KMK_CONSOLIDATION_SNAPSHOT.md` once created
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` once created
- `docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md` once created
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

Phase 3 audits all custom durable data surfaces: SQLDelight tables, migrations, repositories, preferences, backup fields, restore behavior, and sync merge behavior.

Phase 4 audits security and privacy-sensitive flows: extension evaluation, temporary install/probe/uninstall, installer selection, quarantine, explicit-source filtering, recommendation bundle import/export, OCR local text storage, logs, diagnostics, and any user-facing warnings.

These two phases are grouped because data durability and privacy boundaries overlap. A feature cannot be responsibly shared if it stores user data without clear backup behavior, or if it performs extension/network/install actions without clear consent and cleanup behavior.

## Hard Rules

1. Do not modify SQLDelight schema files in this audit phase.
2. Do not create, delete, reorder, or rewrite migrations in this audit phase.
3. Do not modify backup proto fields or sync merge code in this audit phase.
4. Do not change installer behavior, source evaluation behavior, OCR storage behavior, or JSON import behavior in this audit phase.
5. Do not run destructive database or cleanup commands.
6. Do not assume existing KMK migration or backup patterns are correct because tests pass.
7. Verify current Komikku/Mihon/TachiyomiSY data patterns from local code before recommending changes.
8. If local evidence is insufficient, use official/current upstream references and document links used.
9. Treat OCR text as private user data.
10. Treat extension install/evaluate/uninstall behavior as security-sensitive.
11. Treat recommendation bundle import as an untrusted-file boundary.
12. Separate confirmed facts from risks, recommendations, and unanswered questions.

## Phase Dependencies

Before this phase begins, Claude should confirm these files exist:

```text
docs/community/KMK_CONSOLIDATION_SNAPSHOT.md
docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md
docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md
```

If they do not exist, Claude should either stop and ask the user to run the earlier phase first, or clearly state that this audit is being done with incomplete phase inputs.

## Expected Outputs

Primary outputs:

```text
docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md
docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md
```

If the `docs/database/` or `docs/security/` directories do not exist, Claude may create those directories as documentation containers only.

Required supporting output:

```text
docs/community/KMK_PHASE_3_4_RISK_REGISTER.md
```

## Phase 3: Database, Backup, Sync, And Migration Audit

### Files And Areas To Inspect

Claude should inspect:

```text
data/src/main/sqldelight/tachiyomi/data/
data/src/main/sqldelight/tachiyomi/migrations/
data/src/main/java/tachiyomi/data/taste/
domain/src/main/java/tachiyomi/domain/taste/
app/src/main/java/eu/kanade/tachiyomi/data/backup/
app/src/main/java/eu/kanade/tachiyomi/data/sync/
app/src/main/java/eu/kanade/domain/
app/src/main/java/exh/recs/
app/src/main/java/exh/ocr/
app/src/test/java/exh/
```

Also search for:

```text
manga_taste
tag_taste
tag_alias
recommendation_cache
recommendation_last_source_run_statuses
source_evaluation
source_evaluation_unsafe_source
source_evaluation_probe_marker
source_recommendation_fit
manga_cross_source_link
manga_source_quality_signal
ocr_indexed_page
BackupMangaTaste
BackupTagTaste
BackupTagAlias
BackupDisabledRecommendationSource
CrossSource
Recommendation
Seen
SourceEvaluation
OCR
```

### Database Inventory

`KMK_DATABASE_BACKUP_SYNC_AUDIT.md` should include a table for every KMK/OCR custom table or custom column.

Columns:

- Table or column
- Migration number
- SQLDelight file
- Owning feature
- Data type
- Durable user data or cache
- Contains private/sensitive data
- Should be backed up
- Should be synced
- Should be exported
- Safe to clear
- Current repository/interactors
- Tests found
- Known risks
- Recommended action

Classify data as:

- `durable-user-data`
- `derived-cache`
- `diagnostic-cache`
- `privacy-sensitive-local-only`
- `installer/evaluation-state`
- `unknown-needs-review`

### Migration Audit

Audit migrations from the first KMK custom migration through the latest migration found.

For each migration, document:

- migration number,
- feature introduced,
- tables/columns/indexes created,
- whether corresponding `.sq` schema exists,
- whether a clean install schema and upgraded schema appear consistent,
- whether it includes `IF NOT EXISTS` where appropriate,
- whether it is idempotent enough for the project pattern,
- whether it could collide with upstream migration numbering,
- whether tests cover it,
- whether it handles existing user data safely.

Do not fix migration issues in this phase. Record them.

### Backup And Restore Audit

Identify every KMK feature with durable user data and record whether backup/restore currently covers it.

Required features to check:

- manga ratings,
- tag tastes,
- tag aliases,
- disabled recommendation sources,
- liked/disliked sources,
- source priority/order,
- recommendation language filters,
- seen/read manga markers,
- cross-source link groups,
- loved manga grouping support,
- source evaluation results,
- source recommendation fit results,
- source quality signals,
- recommendation bundle state,
- OCR text index,
- OCR index status.

For each feature, classify backup support:

- `backed-up`
- `intentionally-not-backed-up`
- `preference-backed-through-normal-backup`
- `sync-only`
- `missing-should-back-up`
- `missing-should-not-back-up`
- `unknown`

OCR text should be assumed `intentionally-not-backed-up` unless code proves otherwise.

### Sync Audit

Review sync merge behavior for the same durable feature list.

Document:

- whether sync uses the same backup model,
- whether custom KMK fields are included,
- whether sync can silently drop KMK data,
- whether merge conflicts are resolved predictably,
- whether source priority/settings/preferences survive sync,
- whether OCR text is excluded.

If sync behavior is unclear, mark `unknown-needs-review`.

### Proto/Field Range Audit

Document all KMK backup/proto fields and ranges used.

Include:

- field number,
- message name,
- feature owner,
- whether the field is active,
- collision risk,
- whether comments reserve the range,
- tests covering round-trip behavior.

Do not change field numbers in this phase.

## Phase 4: Security And Privacy Review

### Security-Sensitive Areas To Inspect

Claude should inspect:

```text
app/src/main/java/exh/recs/evaluation/
app/src/main/java/exh/recs/share/
app/src/main/java/exh/recs/bestversion/
app/src/main/java/exh/recs/matching/
app/src/main/java/exh/ocr/
app/src/main/java/eu/kanade/tachiyomi/extension/
app/src/main/java/eu/kanade/tachiyomi/network/
app/src/main/java/eu/kanade/tachiyomi/ui/setting/
```

And search for:

```text
Shizuku
PRIVATE
CURRENT
installExtension
uninstall
cleanup
quarantine
unsafe
diagnostics
copy diagnostics
import recommendation
export recommendation
ocr
text recognition
readBytes
withTimeout
catch (Throwable)
catch (Exception)
```

### Security And Privacy Review Contents

`KMK_SECURITY_AND_PRIVACY_REVIEW.md` should include sections for:

- Source Evaluation temporary install/probe/uninstall.
- Private installer behavior.
- Current installer behavior.
- Shizuku installer behavior.
- Prompt-heavy cleanup and leftovers.
- Crash quarantine and unsafe extensions.
- Explicit porn/hentai source filtering.
- Non-installed extension suggestion/evaluation.
- Recommendation-quality probe.
- Recommendation bundle export.
- Recommendation bundle import.
- Best Version source image loading and migration/copy flow.
- Cross-extension matching and link groups.
- OCR downloaded-page text recognition.
- OCR text storage and deletion.
- Logs and diagnostics.
- Backup/sync privacy implications.
- Network/offline behavior if security/privacy relevant.

### Source Evaluation Risk Questions

Answer these explicitly:

- Does evaluation install code from extension repos?
- Which installer path is safest?
- Which installer path can silently clean up?
- Which installer path can leave prompt-required leftovers?
- Can process death lose evaluation state?
- Can a bad extension crash the app despite try/catch?
- Is quarantine persistent enough to prevent repeated crashes?
- Can quarantine hide safe extensions by mistake?
- Are users warned before evaluation starts?
- Are users told that source methods/network calls will execute?
- Are cleanup failures visible after the run?
- Are installed extensions excluded from non-installed evaluation lists correctly?
- Are disliked/blocked/explicit sources respected before evaluation?

### OCR Risk Questions

Answer these explicitly:

- What OCR dependency is used?
- Does OCR increase APK size?
- Does OCR run only on downloaded pages?
- What exact text is stored?
- Where is text stored?
- Can the user see storage size?
- Can the user delete the index?
- Is OCR text backed up, synced, exported, or logged?
- What happens on huge images?
- What happens on empty OCR output?
- What happens on failed image decode?
- What happens on low-memory devices?
- Are users warned before indexing?
- Is OCR clearly separate from normal KMK-Recs?

### Recommendation Bundle Import/Export Risk Questions

Answer these explicitly:

- What is the schema identifier and version?
- What file size limits exist?
- What item count limits exist?
- Are malformed files rejected safely?
- Are missing sources installed only after explicit user action?
- Are duplicate library additions prevented?
- Is user consent required before adding many manga?
- Can imported data write ratings, seen state, source preferences, or only library entries?
- Are source IDs resolved safely if they changed?
- Can malicious JSON cause memory pressure or UI hangs?

### Logs And Diagnostics Review

Identify whether diagnostics or logs can include:

- manga titles,
- manga URLs,
- source URLs,
- extension package names,
- OCR text,
- user preferences,
- source evaluation errors,
- crash markers,
- imported bundle contents.

Classify each as:

- safe,
- acceptable for explicit user-copy diagnostics,
- sensitive but local,
- should be redacted,
- unknown.

### User-Facing Warning Requirements

For each risky workflow, specify whether it needs:

- first-run warning,
- per-run confirmation,
- advanced/experimental toggle,
- privacy note,
- cleanup warning,
- network warning,
- backup warning,
- disabled-by-default behavior.

Do not implement warnings in this phase; document requirements for later implementation.

## Cross-Phase Risk Register

`KMK_PHASE_3_4_RISK_REGISTER.md` is required. It should list risks in this format:

- Risk ID
- Area
- Severity
- Evidence
- User impact
- Current mitigation
- Missing mitigation
- Recommended phase for fix
- Whether code change is required

Severity values:

- `critical`
- `high`
- `medium`
- `low`
- `unknown`

## Acceptance Criteria

This phase is complete only if:

- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` exists.
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` exists.
- Every custom KMK/OCR table and migration is listed.
- Durable user data vs cache data is clearly classified.
- Backup and sync behavior is classified for every feature.
- OCR text is explicitly reviewed as private local data.
- Source Evaluation is explicitly reviewed as an install/probe/uninstall security-sensitive workflow.
- Recommendation bundle import is explicitly reviewed as an untrusted-file boundary.
- Proto/backup field collision risk is documented.
- Missing tests are listed, not silently ignored.
- No source code, migration, schema, proto, or resource changes were made.
- docs/community/KMK_PHASE_3_4_RISK_REGISTER.md exists.
- Any recommended fixes are assigned to later phases.

## Summary Claude Should Provide

When finished, Claude should summarize:

- which data surfaces were found,
- which data is durable user data,
- which data is cache/diagnostic,
- which data should never be backed up,
- which backup/sync gaps exist,
- which migration risks exist,
- which security/privacy risks are critical or high,
- which warnings/toggles are needed later,
- what was not verified,
- what should happen next.

## Important Reminder For Claude

This is an audit phase.

If you find a migration bug, backup bug, privacy leak, unsafe installer behavior, or serious crash risk, document it clearly and assign it to a later fix phase. Do not patch it in this phase unless the user explicitly changes the task.




