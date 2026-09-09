# KMK Phase 8-9 Recommendation Bundle And OCR Hardening Plan

Date: 2026-06-26

Status: planning. Implementation is not approved until the user explicitly approves this phase.

Target implementation pass: Claude Code, after Phase 0-7 outputs exist and after user approval.

Related documents:

- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` once created
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` once created
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` once created
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/ocr/README.md`
- `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_IMPLEMENTATION.md`
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

Phase 8 hardens recommendation bundle export/import because importing JSON is an untrusted-file boundary. Even if the JSON format is simple, it can still create bad outcomes: large-memory reads, malformed files, missing-source confusion, duplicate library entries, unexpected installs, or misleading metadata.

Phase 9 hardens OCR because OCR stores local searchable text from downloaded manga and adds a heavy ML dependency. OCR must remain separate from the normal KMK-Recs build line unless the user explicitly changes that release strategy.

These phases are grouped because both features involve user trust, local data, and privacy expectations.

## Hard Rules

1. Do not merge OCR into the normal KMK-Recs line.
2. Do not back up, sync, export, or share OCR text unless the user explicitly approves a future design.
3. Do not install missing extensions during recommendation bundle import without explicit user action.
4. Do not add imported manga to the library without a preview and explicit confirmation.
5. Do not trust JSON input just because it has the expected schema name.
6. Do not load unbounded JSON files into memory without size checks.
7. Do not add OCR indexing as an always-on background feature.
8. Do not run OCR over all downloads automatically after install.
9. Do not hardcode user-facing strings; use `KMR` in `i18n-kmk` base resources.
10. Verify current Komikku file-picker, import/export, data-storage, and warning-dialog patterns before editing UI.

## Main Code Areas

Claude should inspect and limit changes mainly to:

```text
app/src/main/java/exh/recs/share/
app/src/main/java/exh/recs/
app/src/main/java/exh/ocr/
app/src/main/java/eu/kanade/tachiyomi/ui/setting/
app/src/main/java/eu/kanade/tachiyomi/data/backup/
app/src/main/java/eu/kanade/tachiyomi/data/sync/
domain/src/main/java/tachiyomi/domain/taste/
data/src/main/java/tachiyomi/data/taste/
data/src/main/sqldelight/tachiyomi/data/
i18n-kmk/src/commonMain/moko-resources/base/
app/src/test/java/exh/recs/
app/src/test/java/exh/ocr/
```

Only touch Gradle/dependency files if the OCR release-line separation needs a documented correction.

## Phase 8 Required Changes: Recommendation Bundle Import/Export

### 1. Public Bundle Schema Documentation

Problem:

The bundle format exists, but community users and future maintainers need a concise schema document.

Implementation:

- Create:

```text
docs/recommendations/KMK_RECS_RECOMMENDATION_BUNDLE_SCHEMA.md
```

- Document:
  - schema id,
  - schema version,
  - required fields,
  - optional fields,
  - size limits,
  - item limits,
  - source metadata fields,
  - what import can and cannot do,
  - missing-source behavior,
  - duplicate behavior,
  - privacy notes.

### 2. Import Validation Hardening

Implementation:

- Verify existing validator enforces:
  - max file size,
  - max item count,
  - max source count,
  - max string lengths for titles, URLs, descriptions, reasons, and metadata,
  - schema id,
  - schema version,
  - no unknown version accepted as compatible unless explicitly intended.
- Add missing bounds if absent.
- Ensure malformed JSON returns a safe user-facing error, not a crash.
- Ensure validation happens before expensive resolution.

Tests:

- Add tests for:
  - oversized file,
  - too many items,
  - too many sources,
  - long strings,
  - wrong schema id,
  - unsupported version,
  - malformed JSON,
  - empty bundle.

### 3. Import Preview And Consent

Implementation:

- Ensure import always opens a preview before adding anything.
- Preview should show:
  - ready to add,
  - already in library,
  - missing source,
  - installed source needs resolve,
  - needs manual match,
  - unsupported local source,
  - error.
- Default selection should include only safe ready states.
- Missing source install must be per-source explicit action, not automatic.
- Add a summary confirmation before adding many manga if existing UI pattern supports it.
- After add, show added/already/failed counts.

Tests:

- Preview state tests if pure resolver supports it.
- Library adder tests for duplicate skipping.

### 4. Missing Source Handling

Implementation:

- If a source is missing:
  - show extension/source name,
  - show repo/signature/package info when available,
  - offer install only if a matching available extension exists,
  - re-resolve after install.
- If multiple possible extensions match, do not auto-pick.
- Do not export extension APKs. Bundle should reference sources, not package extension binaries.

### 5. Export Scope Clarity

Implementation:

- Export surfaces should clearly state what they export:
  - Top Picks,
  - source row,
  - Loved Manga,
  - selected recommendations if implemented.
- Export should include enough metadata to resolve sources later, but should not include private OCR text or unrelated preferences.
- If export includes scores/reasons, document that they are advisory and local to the exporting user's taste profile.

### 6. Bundle Import/Export Documentation Update

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
```

Create:

```text
docs/recommendations/KMK_RECS_RECOMMENDATION_BUNDLE_HARDENING_IMPLEMENTATION.md
```

Include files changed, behavior changed, tests run, and remaining limitations.

## Phase 9 Required Changes: OCR Isolation And Privacy Hardening

### 7. Confirm OCR Release-Line Separation

Problem:

OCR should remain a separate experimental APK/branch line, not silently become part of normal KMK-Recs.

Implementation:

- Verify current Gradle/app version docs identify OCR separately.
- Verify docs clearly distinguish:
  - KMK-Recs standard line,
  - KMK-OCR experimental line.
- If docs blur the two, update docs.
- If Gradle currently forces OCR dependency into all builds, document the issue and either:
  - leave for user-approved architecture phase, or
  - implement a clearly scoped build-flavor/dependency separation only if user approval specifically includes it.

Do not restructure build flavors without explicit approval.

### 8. OCR Privacy Warning Before Indexing

Implementation:

- Add a first-run or pre-index warning before OCR indexing begins.
- Warning must say:
  - OCR reads downloaded chapter pages,
  - recognized text is stored locally in the app database,
  - text can be searched later,
  - storage can be cleared,
  - OCR may be slow and battery/CPU intensive,
  - OCR quality depends on image quality/language/font,
  - OCR is experimental.
- Store acknowledgement only if consistent with local settings patterns.
- Provide a way to view the privacy note again.

Constraints:

- Use `KMR` strings.
- Do not imply OCR sends text to a server unless code proves that it does.

### 9. OCR Storage Visibility And Cleanup

Implementation:

- Ensure OCR settings/search screen shows:
  - indexed manga count,
  - indexed chapter count if available,
  - indexed page count,
  - approximate storage used,
  - last indexed time if available,
  - clear/delete index action.
- Clear action must require confirmation.
- After clearing, search results and counts must refresh.

Tests:

- Repository/status tests for count and clear behavior.

### 10. OCR Status Semantics

Problem:

Users need to know if OCR found no text, failed decoding, skipped huge pages, or was not indexed.

Implementation:

- Distinguish statuses:
  - not indexed,
  - indexed with text,
  - indexed but no text found,
  - decode failed,
  - OCR failed,
  - skipped due to size/memory limit,
  - cancelled,
  - unknown error.
- Search results should not silently hide failures as if no page matched.
- Index summary should show failures/skips separately from successful empty pages.

Tests:

- OCR status mapping tests.
- Search result tests for indexed/no-text/failure cases.

### 11. OCR Work Batching And Limits

Implementation:

- OCR indexing should support small batches so users can index manageable sets.
- Do not force all downloaded manga/chapters at once.
- Provide clear progress:
  - manga,
  - chapter,
  - page,
  - completed count,
  - failures.
- Allow cancel.
- On cancel, keep completed indexed pages and mark remaining work incomplete.
- Avoid reading too many full images into memory at once.

If existing implementation reads page streams with `readBytes()`, document whether it is still acceptable for now or add guardrails:

- max image byte size,
- max decoded bitmap dimensions,
- tile long pages,
- skip or fail gracefully on huge images.

### 12. OCR Search Quality

Implementation:

- Search indexed text using a forgiving local search:
  - exact phrase boost,
  - all-term match,
  - most-term match,
  - normalized punctuation/case,
  - result snippets,
  - manga/source/chapter/page context.
- Results should lead the user to:
  - manga,
  - source/extension,
  - chapter,
  - page if possible.
- Do not keep OCR images after text extraction; use existing downloaded pages as source of truth.

Tests:

- Ranker tests for exact phrase, partial phrase, noisy punctuation, and multi-word search.

### 13. OCR Backup/Sync/Export Exclusion

Implementation:

- Verify OCR text index is not included in backup, sync, recommendation bundle export, or diagnostics.
- If any path includes OCR text, remove it unless user explicitly approved sharing.
- Document OCR text as local-only private data.

### 14. OCR Documentation Update

Update:

```text
docs/ocr/README.md
docs/recommendations/CURRENT_STATE.md
docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md
```

Create:

```text
docs/ocr/KMK_OCR_PRIVACY_AND_RELEASE_HARDENING_IMPLEMENTATION.md
```

Include:

- release-line status,
- dependency notes,
- privacy behavior,
- storage behavior,
- clear/delete behavior,
- status semantics,
- tests run,
- remaining risks.

## Validation

Run at minimum:

```text
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If OCR build line differs from normal build line, also document which APK/build task was used.

If SQLDelight changes are made:

```text
./gradlew :data:generateSqlDelightInterface
```

If a command cannot be run, document why.

## Acceptance Criteria

This phase is complete only if:

- Recommendation bundle schema is documented.
- Import validation is bounded and tested.
- Import preview requires explicit user confirmation before library additions.
- Missing-source install remains explicit and non-automatic.
- Export does not include OCR text or unrelated private state.
- OCR remains documented as separate/experimental.
- OCR warns users before indexing.
- OCR storage usage and clear controls are visible.
- OCR status distinguishes no text from failure.
- OCR indexing is bounded/cancellable or clearly documented if not.
- OCR text remains local-only and excluded from backup/sync/export.
- Docs and tests are updated.

## Summary Claude Should Provide

Claude should report:

- what bundle validation changed,
- what import/export behavior changed,
- what OCR privacy warnings changed,
- whether OCR remains separate,
- whether any database/schema changes were made,
- what tests passed,
- what manual QA remains,
- what risks remain.



