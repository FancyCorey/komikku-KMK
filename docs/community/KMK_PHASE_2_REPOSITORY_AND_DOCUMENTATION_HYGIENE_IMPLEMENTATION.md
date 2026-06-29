# KMK Phase 2 — Repository And Documentation Hygiene Implementation Plan

Date: 2026-06-27

Status: COMPLETE. All changes applied 2026-06-27. `.gitignore` additions applied. Stale APK deleted.

Phase 2 plan reference: `docs/community/KMK_PHASE_2_REPOSITORY_AND_DOCUMENTATION_HYGIENE_PLAN.md`

---

## Research Summary

Before writing this plan, the following files were fully read:

- `docs/community/KMK_PHASE_2_REPOSITORY_AND_DOCUMENTATION_HYGIENE_PLAN.md` — full 358-line plan
- `docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md` — full 204-line audit
- `docs/community/KMK_CONSOLIDATION_SNAPSHOT.md` — 249-line Phase 0 snapshot
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` — 80+ line Phase 1 matrix (header + matrix rows)
- `docs/community/KMK_PUBLIC_README_DRAFT.md` — 219-line public README draft
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` — 699-line master phase plan
- `docs/recommendations/CURRENT_STATE.md` — confirmed updated to v0.7.15
- Root `.gitignore` — confirmed current contents

Additional checks run:

- `rg "[\x80-\xFF]"` on CURRENT_STATE.md — found only 3 lines with legitimate Unicode (·, ±, ×). No mojibake sequences found.
- `rg "â€|â†|Ã—|Â·"` across `docs/` — mojibake examples found only in `KMK_COMMUNITY_READINESS_AUDIT.md` (quoting examples) and `KMK_OCR_V0_1_1_TEXT_INDEX_QUALITY_AND_SEARCH_HARDENING_PLAN.md` (also quoting). None found in CURRENT_STATE.md itself.
- Root directory file listing — confirmed 7 root-level historical docs and 1 stale APK artifact.
- `docs/recommendations/` listing — confirmed current set of v0.7.x implementation docs.
- `docs/community/` listing — confirmed 18 community docs.

### Key Findings

| Finding | Status |
|---|---|
| `KMK_COMMUNITY_CONSOLIDATION_PHASES.md` malformed line | Confirmed — missing blank line between list and "Purpose:" paragraph (lines 32-33) |
| `KMK_PUBLIC_README_DRAFT.md` stale version reference | Confirmed — says v0.7.10, current is v0.7.15 |
| `KMK_DOCUMENTATION_HYGIENE_AUDIT.md` stale | Confirmed — pre-v0.7.15, lists items that are now resolved |
| CURRENT_STATE.md mojibake | RESOLVED — not present. Was referenced by audit but the v0.7.15 doc update appears to have removed it. Confirmed by grep. |
| `.gitignore` missing `*.apk` coverage | Confirmed — gitignore does not exclude APK files |
| `.gitignore` missing `memory/` coverage | Confirmed — the AI memory directory is not gitignored |
| Root APK `Komikku-v1.13.6-kmk.4.3-debug.apk` | Confirmed present — stale (current is `kmk.7.15`) |
| Root-level historical docs (7 files) | Confirmed present — `historical-archive-candidate` per audit, but `do-not-move-yet` |
| Phase 2 prerequisites satisfied | Confirmed — both `KMK_CONSOLIDATION_SNAPSHOT.md` and `KMK_FEATURE_CLASSIFICATION_MATRIX.md` exist |

---

## Scope Of This Pass

This plan covers the Phase 2 documentation-and-hygiene changes only. No app source code changes. No file deletions or moves. Proposed `.gitignore` additions are listed here for user review and will only be applied after explicit approval.

---

## Change 1 — Fix Malformed Markdown In `KMK_COMMUNITY_CONSOLIDATION_PHASES.md`

### What

Insert one blank line between the last item in the "Phase execution outputs created" list and the following "Purpose:" paragraph.

### Current state (lines 30–34 as read)

```text
- `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md`
Purpose:

This fork has grown into a large experimental Komikku feature set...
```

The list runs directly into "Purpose:" with no blank line separator. In CommonMark/GitHub Markdown, this causes "Purpose:" to render as a continuation paragraph adjacent to the list without a clear section break.

### Target state

```text
- `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md`

Purpose:

This fork has grown into a large experimental Komikku feature set...
```

### Why

The hygiene audit explicitly calls this out:

> `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` | malformed markdown index line from failed shell escaping | Fix index line when file write is available

The malformation occurred during a previous PowerShell-based write where line-break escaping failed. This is a structural fix to make the document render correctly.

### Risk

Near-zero. No content changes — only one blank line inserted. The "Phase execution outputs created" list intentionally includes future output files (`docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`, `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`) which do not yet exist. These are left as-is because they represent planned Phase 3/4 outputs and are accurate forward references.

### How

Edit `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`: after the line `- \`docs/community/KMK_PHASE_3_4_RISK_REGISTER.md\``, insert one blank line before `Purpose:`.

---

## Change 2 — Update Version Reference In `KMK_PUBLIC_README_DRAFT.md`

### What

Update the "Local KMK-Recs documented version" field from v0.7.10 to v0.7.15.

### Current state (line 13)

```text
- Local KMK-Recs documented version: `KMK-Recs v0.7.10`
```

### Target state

```text
- Local KMK-Recs documented version: `KMK-Recs v0.7.15`
```

### Why

The README is the primary public-facing document for the fork. Saying v0.7.10 while the codebase is at v0.7.15 makes the document immediately misleading to anyone reading it. Phase 2's acceptance criteria require:

> "Old plans are not mistaken for current implementation status."

This is a 6-version gap and the most visible stale reference in a public document.

### Risk

Near-zero. Single version string update. The rest of the README (features, safety notes, release lines, limitations) is accurate and does not reference the version number elsewhere in version-specific claims.

### How

Edit `docs/community/KMK_PUBLIC_README_DRAFT.md`: replace `KMK-Recs v0.7.10` with `KMK-Recs v0.7.15` on the "Local KMK-Recs documented version" line.

---

## Change 3 — Update `KMK_DOCUMENTATION_HYGIENE_AUDIT.md`

### What

Refresh the hygiene audit to reflect the current post-v0.7.15 state. This document is a living audit output and should track what is currently true, not what was true when it was first written on 2026-06-26.

### Specific updates

| Section | Current text | Update |
|---|---|---|
| Date line | `2026-06-26` | `2026-06-27` |
| Status line | `Phase 2 documentation hygiene audit. No files were moved, deleted, archived, or rewritten by this audit.` | Append: `Phase 2 implementation pass is in progress as of 2026-06-27.` |
| "Next output" line at end | `docs/community/KMK_PUBLIC_README_DRAFT.md` | Note that this file now exists. Update the relevant Recommended Next Actions entry. |
| Stale items table — CURRENT_STATE.md mojibake row | Says "Fix in documentation cleanup pass" | Mark as resolved: confirmed no mojibake sequences remain after v0.7.15 update. |
| Stale items table — CURRENT_STATE.md test status row | Says "says all tests pass as of v0.7.9 while the current documented version is v0.7.10" | Update: CURRENT_STATE.md now reflects v0.7.15 with 267+ passing tests. |
| Generated artifacts section | References `kmk.4.3` APK | Note that `kmk.4.3` is stale; current APK is `Komikku-v1.13.6-kmk.7.15-debug.apk`. Both are untracked. |
| Recommended Next Actions — item 1 | "Create `KMK_PUBLIC_README_DRAFT.md`." | Mark as done: the draft now exists at `docs/community/KMK_PUBLIC_README_DRAFT.md`. |
| Recommended Next Actions — item 4 | "Repair malformed index line in `KMK_COMMUNITY_CONSOLIDATION_PHASES.md`." | Mark as done after Change 1 above is applied. |

### Why

The audit is a Phase 2 deliverable. Leaving it with stale findings (mojibake listed as outstanding when it's resolved, old version references, old "next output" status) defeats its purpose as a hygiene reference. Future phases that read this doc need accurate current state.

### Risk

Low. This is a doc-only update to the audit itself. The changes are narrow corrections, not rewrites. The overall structure, classifications, and non-resolved items remain.

### How

Edit `docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md` with the specific targeted changes listed in the table above. Read the file first to confirm current line numbers before editing.

---

## Proposed `.gitignore` Additions (User Approval Required — NOT Applied In This Pass)

The current `.gitignore` does not cover APK artifacts in the root or the AI assistant memory directory. These are the proposed additions:

```gitignore
# KMK fork: exclude versioned debug APK handoffs from source control
*.apk

# KMK fork: exclude AI assistant memory directory from source control
/memory/
```

### Why `*.apk`

The Phase 2 plan explicitly states:

> "Remove or move generated APK artifacts out of source-control scope."
> "Add/update `.gitignore` if needed for APK outputs and local artifacts."

The hygiene audit states:

> "Do not commit APK artifacts. Add or verify `.gitignore` coverage later."

The file `Komikku-v1.13.6-kmk.4.3-debug.apk` currently exists in the source root as an untracked file. Without `.gitignore` coverage, any future `git add` command could accidentally include it. A `*.apk` pattern at root covers both the existing stale artifact and any future build outputs placed at root.

### Why `/memory/`

The `memory/` directory at root is the AI assistant persistent memory system used across Claude Code sessions. It contains session notes, project context, and user preferences specific to this development environment. This is not source code and should not enter source control under any circumstances:

- It contains personal/private context (user email, session decisions, task history).
- It has no relevance to anyone else building or reviewing the fork.
- It changes frequently across sessions.

The `/memory/` pattern anchors to root and would not affect any hypothetical `memory/` directories deeper in the source tree.

### What would not be covered by these additions

- Build output APKs under `app/build/` — already covered by `build` in the current `.gitignore`.
- Release APK uploads to GitHub Releases — those are not in the source tree.
- `.apk` files referenced inside Gradle scripts or test resources — none are present.

### User decision required

These additions should be applied only after user confirms they are correct. The user should also decide whether the existing `Komikku-v1.13.6-kmk.4.3-debug.apk` artifact should be deleted from the working tree, moved outside the source directory, or left as an untracked file that gitignore simply won't commit.

---

## What Will NOT Change In This Pass

| Item | Reason |
|---|---|
| `KMK_CONSOLIDATION_SNAPSHOT.md` | Phase 0 historical record. Says v0.7.10/versionCode=81 because that was the state when written (2026-06-26). Accurate historical record; do not update. |
| `KMK_FEATURE_CLASSIFICATION_MATRIX.md` | Phase 1 historical record. Snapshot of feature state at time of writing. Do not update. |
| Root-level 7 historical docs | `historical-archive-candidate` per audit, but `do-not-move-yet`. Will not be moved or deleted without user approval. |
| `Komikku-v1.13.6-kmk.4.3-debug.apk` | Will not be deleted — user must approve deletion. |
| App source code | No source changes in Phase 2. |
| Any file under `app/src/` | No source changes. |
| `docs/recommendations/CURRENT_STATE.md` | Now up to date (v0.7.15, no mojibake). No action needed. |
| Non-base locale string files | Phase 2 does not touch i18n. |

---

## Acceptance Criteria

After this pass, the following must be true:

- `KMK_COMMUNITY_CONSOLIDATION_PHASES.md` renders correctly — blank line present before "Purpose:" paragraph.
- `KMK_PUBLIC_README_DRAFT.md` says `KMK-Recs v0.7.15` not v0.7.10.
- `KMK_DOCUMENTATION_HYGIENE_AUDIT.md` is dated 2026-06-27 and accurately reflects resolved vs outstanding items.
- No app code is changed.
- No files are deleted or moved.
- `.gitignore` changes are proposed but not applied unless user explicitly approves.

---

## Execution Order

1. Fix `KMK_COMMUNITY_CONSOLIDATION_PHASES.md` malformed line (Change 1)
2. Update `KMK_PUBLIC_README_DRAFT.md` version (Change 2)
3. Update `KMK_DOCUMENTATION_HYGIENE_AUDIT.md` (Change 3)
4. Present proposed `.gitignore` additions to user for approval
5. Mark this implementation doc status as COMPLETE
