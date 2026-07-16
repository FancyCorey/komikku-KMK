# KMK Documentation Hygiene Audit

Date: 2026-06-27

Status: Phase 2 documentation hygiene audit. No files were moved, deleted, archived, or rewritten by this audit. Phase 2 implementation pass is in progress as of 2026-06-27.

Scope: full KMK fork delta from Komikku v1.13.6/current baseline, including KMK-Recs, KMK-OCR, root handoff docs, community docs, recommendation docs, OCR docs, and generated/local artifacts referenced by docs.

## Summary

The repository now has enough internal documentation to continue consolidation, but the documentation is not yet public-ready.

Main issues:

- Current docs are spread across root files, `docs/recommendations/`, `docs/recommendations/archive/`, `docs/ocr/`, and `docs/community/`.
- Many docs are implementation handoff notes rather than public-facing docs.
- Some docs contain stale version references or old "planning" statuses.
- Some docs contain mojibake from mis-decoded punctuation.
- A generated debug APK exists in the repo root.
- The master community phase index currently has one malformed markdown line from a failed PowerShell edit and should be repaired later.

## Documentation Groups

### Community docs

Current paths:

- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_CONSOLIDATION_VALIDATION_AND_HANDOFF.md`
- `docs/community/KMK_PHASE_0_1_CLAUDE_PROMPT.md`
- `docs/community/KMK_PHASE_0_1_SNAPSHOT_AND_FEATURE_CLASSIFICATION_PLAN.md`
- `docs/community/KMK_PHASE_2_REPOSITORY_AND_DOCUMENTATION_HYGIENE_PLAN.md`
- `docs/community/KMK_PHASE_3_4_DATABASE_SECURITY_PRIVACY_AUDIT_PLAN.md`
- `docs/community/KMK_PHASE_5_SOURCE_EVALUATION_HARDENING_IMPLEMENTATION_PLAN.md`
- `docs/community/KMK_PHASE_6_7_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_PLAN.md`
- `docs/community/KMK_PHASE_8_9_BUNDLE_AND_OCR_HARDENING_PLAN.md`
- `docs/community/KMK_PHASE_10_11_ARCHITECTURE_STYLE_TEST_RELEASE_PLAN.md`
- `docs/community/KMK_PHASE_12_PUBLIC_SHARING_PACKAGE_PLAN.md`
- `docs/community/KMK_CONSOLIDATION_SNAPSHOT.md`
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md`
- `docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md`

Classification:

- `current-internal`
- `current-public-candidate` only after condensation

Notes:

- These are useful for Claude/Codex handoff and consolidation.
- They should not be the primary public README.
- The master plan index needs a small markdown cleanup.

### Recommendation docs

Current important paths:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/recommendations/DOCUMENTATION_RULES.md`
- active v0.7.x plans/implementations
- feasibility research docs
- `docs/recommendations/archive/plans/`
- `docs/recommendations/archive/implementations/`

Classification:

- `CURRENT_STATE.md`: `current-internal`, possible source for public summary
- `NEXT_WORK.md`: `current-internal`
- `README.md`: `current-internal`
- v0.7.x implementation notes: `current-internal`
- archived v0.4-v0.6 plans/implementations: `historical-archive-candidate`, already partly organized
- feasibility research: `current-internal`

Notes:

- `CURRENT_STATE.md` is the strongest current technical source of truth for KMK-Recs.
- It still reads as an implementation ledger, not public docs.
- It contains visible mojibake such as corrupted arrows/dashes/comparison symbols in several sections.
- It says all tests pass as of v0.7.9 while the feature version is v0.7.10, so test status should be refreshed.

### OCR docs

Current paths:

- `docs/ocr/README.md`
- `docs/ocr/KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_PLAN.md`
- `docs/ocr/KMK_OCR_V0_1_0_DOWNLOADED_TEXT_SEARCH_IMPLEMENTATION.md`
- `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_PLAN.md`
- `docs/ocr/KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_IMPLEMENTATION.md`

Classification:

- `current-internal`
- `ocr-only`

Notes:

- OCR docs correctly state OCR is separate from KMK-Recs.
- OCR still needs a public-facing privacy warning and release-line explanation.
- OCR implementation notes should not be mixed into normal KMK-Recs release notes.

### Root-level recommendation docs

Observed root-level docs:

- `RECOMMENDATION_HARDENING_IMPLEMENTATION_PLAN.md`
- `RECOMMENDATION_IMPLEMENTATION_AUDIT.md`
- `RECOMMENDATION_QUALITY_EFFICIENCY_IMPLEMENTATION_PLAN.md`
- `RECOMMENDATION_SEARCH_RESEARCH.md`
- `RECOMMENDATION_SOURCE_LANGUAGE_FILTER_PLAN.md`
- `RECOMMENDATION_VERSIONING.md`
- `TASTE_RATING_AND_FOR_YOU_REFINEMENT_PLAN.md`

Classification:

- `historical-archive-candidate`
- `do-not-move-yet`

Recommendation:

- Do not delete.
- Later, move to `docs/internal/history/` or fold into `docs/recommendations/archive/` after confirming no unique current details are lost.

## Generated Or Local Artifact References

Observed:

- `Komikku-v1.13.6-kmk.4.3-debug.apk` â€” stale, untracked at root. Current version is `Komikku-v1.13.6-kmk.7.15-debug.apk`.

Classification:

- `generated-or-local-artifact-reference`

Recommendation:

- Do not commit APK artifacts.
- Add `*.apk` to `.gitignore` to prevent accidental commits. Proposed in Phase 2 implementation plan; awaiting user approval.
- The stale `kmk.4.3` APK should be deleted or moved outside the source tree after explicit user approval.
- Add `/memory/` to `.gitignore` to exclude the AI assistant memory directory. Proposed; awaiting user approval.

## Stale Or Needs-Update Items

| File | Issue | Suggested action |
|---|---|---|
| `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` | malformed markdown index line from failed shell escaping | **RESOLVED 2026-06-27** â€” blank line inserted before "Purpose:" paragraph |
| `docs/recommendations/CURRENT_STATE.md` | test status says v0.7.9 while feature version is v0.7.10 | **RESOLVED 2026-06-27** â€” CURRENT_STATE.md updated to v0.7.15; 267+ unit tests passing per current docs |
| `docs/recommendations/CURRENT_STATE.md` | mojibake in punctuation sequences | **RESOLVED 2026-06-27** â€” confirmed no mojibake sequences remain after v0.7.15 doc update (rg scan clean) |
| root-level recommendation docs | older handoff/planning files outside docs tree | Archive later after uniqueness check |
| OCR docs | v0.1.1 status should be reconciled with actual implementation/build state | Update after OCR audit |

## Mojibake Review

Known affected area:

- `docs/recommendations/CURRENT_STATE.md` â€” **RESOLVED 2026-06-27**

Scan run on 2026-06-27:

```text
rg "Ã¢|Ã‚|Ãƒ" docs/recommendations/CURRENT_STATE.md
rg "Ã¢â‚¬|Ã¢â€ |Ãƒâ€”|Ã‚Â·" docs/
```

No mojibake sequences found in CURRENT_STATE.md. The v0.7.15 doc update (2026-06-27) rewrote the affected sections. Only 3 non-ASCII matches remain in CURRENT_STATE.md â€” all legitimate Unicode: `Â·` (middle dot separator), `Â±` (plus-minus), `Ã—` (multiply sign). These are correct.

The pattern `rg "Ã¢|Ã‚|Ãƒ"` found mojibake only inside `KMK_COMMUNITY_READINESS_AUDIT.md` and `KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_PLAN.md`, where they appear as quoted examples inside code blocks â€” not as actual mojibake in the text. No action needed.

## Public Documentation Readiness

Current docs are not public-ready as-is.

Reasons:

- Too much implementation chronology.
- Too many Claude/Codex-style planning notes.
- Experimental risks are spread across multiple files.
- Source Evaluation and OCR warnings need a concise public explanation.
- Release lines need a plain explanation.
- Known limitations and issue-report guidance are not consolidated.

Next output:

- `docs/community/KMK_PUBLIC_README_DRAFT.md`

## Code Style/Architecture Findings To Carry Forward

From the feature matrix and snapshot:

- Large app-layer orchestration exists under `exh/recs/`.
- Source Evaluation is the highest-risk area and likely needs the most architectural cleanup.
- OCR is separate but currently touches app build metadata.
- Backup/sync/migrations need a dedicated audit before public sharing.
- UI/settings terminology is broad and should be simplified before community users see it.
- User-facing strings must be checked for `KMR`/`i18n-kmk` compliance.

## Recommended Next Actions

1. ~~Create `KMK_PUBLIC_README_DRAFT.md`.~~ **DONE** â€” `docs/community/KMK_PUBLIC_README_DRAFT.md` now exists; version updated to v0.7.15.
2. ~~Create database/security audits (Phase 3/4 outputs: `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`, `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`).~~ **DONE** â€” both files fully written from source code audit 2026-06-27.
3. Create required risk register (Phase 3/4 output: `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md` already exists).
4. ~~Repair malformed index line in `KMK_COMMUNITY_CONSOLIDATION_PHASES.md`.~~ **DONE** â€” blank line added before "Purpose:" section.
5. ~~Apply proposed `.gitignore` additions after user approval (`*.apk`, `/memory/`).~~ **DONE** â€” `*.apk` and `/memory/` added to `.gitignore`.
6. ~~After user approval, delete or move stale `Komikku-v1.13.6-kmk.4.3-debug.apk` from source root.~~ **DONE** â€” file deleted.
7. Later, propose doc archive moves for root-level historical docs but do not move files until approved.

