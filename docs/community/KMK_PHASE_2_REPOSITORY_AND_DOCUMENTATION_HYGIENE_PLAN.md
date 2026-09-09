# KMK Phase 2 Repository And Documentation Hygiene Plan

Date: 2026-06-26

Status: planning. No repository cleanup, file deletion, file moves, or app code changes are approved by this plan.

Target implementation/audit pass: Claude Code, after Phase 0-1 has produced the snapshot and feature classification matrix and after user approval.

Related documents:

- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_PHASE_0_1_SNAPSHOT_AND_FEATURE_CLASSIFICATION_PLAN.md`
- `docs/community/KMK_CONSOLIDATION_SNAPSHOT.md` once created
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` once created
- `docs/recommendations/README.md`
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

Phase 2 makes the repository and documentation understandable before deeper code refactors begin.

The current fork has many useful internal planning notes, implementation reports, handoff prompts, version references, generated APKs, and experimental feature docs. That is good for development, but it is not good for community sharing. A new reader should be able to understand:

- what the fork is,
- what features are stable personal features,
- what features are experimental,
- what belongs only to the OCR APK line,
- what data/privacy risks exist,
- what should not be committed or shared as source,
- which docs are current,
- which docs are historical.

This phase is mostly documentation organization and repo hygiene planning. Any destructive cleanup must be explicitly approved before execution.

## Dependency On Phase 0-1

Do not start Phase 2 implementation until these files exist:

```text
docs/community/KMK_CONSOLIDATION_SNAPSHOT.md
docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md
```

Phase 2 should use those files as the source of truth for:

- current feature versions,
- current dirty/untracked categories,
- generated artifacts,
- current docs,
- stale docs,
- feature classifications,
- code-alignment mismatches,
- OCR/KMK-Recs separation.

If Phase 0-1 was not completed, stop and ask the user to run Phase 0-1 first.

## Hard Rules

1. Do not make app source code changes in this phase unless the user explicitly expands the task.
2. Do not delete generated APKs, docs, or source files without explicit user approval.
3. Do not move historical implementation notes without first listing exactly what will move and where.
4. Do not rewrite current documentation in a way that loses implementation details.
5. Do not remove AI handoff details until they have a clear internal/archive destination.
6. Do not assume Komikku's public README, changelog, release, or contribution style. Verify the current local/upstream pattern first.
7. If public-facing docs conflict with current Komikku style, document the mismatch and prefer a conservative draft.
8. Keep KMK-Recs and KMK-OCR documented as separate lines unless the user decides otherwise.
9. Keep Source Evaluation and OCR clearly marked experimental.
10. Keep internal docs separate from public-facing docs.

## Official/Current Komikku Pattern Checks

Before writing public-facing docs, Claude must verify the local/current patterns for:

- root `README.md` structure,
- any existing contribution or issue-report files,
- any existing release note/changelog format,
- existing Komikku updater or What's New wording style,
- existing docs folder organization,
- existing `.gitignore` artifact patterns,
- existing generated APK/build output locations,
- existing string/resource rules from `AGENTS.md`,
- existing fork marker conventions.

If local evidence is not enough, Claude may use official/current Komikku or upstream/community references and must document links used.

No public-facing claim should be written as if this is upstream-ready. The docs should present the fork as an experimental personal fork unless later phases prove otherwise.

## Phase 2 Outputs

Primary output:

```text
docs/community/KMK_PUBLIC_README_DRAFT.md
```

Recommended supporting output:

```text
docs/community/KMK_DOCUMENTATION_HYGIENE_AUDIT.md
```

Optional output if needed:

```text
docs/community/KMK_DOC_ARCHIVE_PROPOSAL.md
```

Do not create a root `README-KMK-FORK.md` until the user approves replacing or adding a public-facing root file. Use `KMK_PUBLIC_README_DRAFT.md` first.

## Documentation Hygiene Audit

`KMK_DOCUMENTATION_HYGIENE_AUDIT.md` should list the documentation state without immediately moving anything.

It should include:

- current docs directory map,
- current recommendation docs,
- current OCR docs,
- current community docs,
- docs that appear current,
- docs that appear historical but useful,
- docs that appear stale or superseded,
- docs with outdated version references,
- docs with mojibake or encoding damage,
- docs that include internal AI/Claude/Codex handoff details,
- docs that are safe for public readers,
- docs that should remain internal,
- docs that should be archived later,
- docs that should be summarized rather than deleted.

Use categories:

- `current-public-candidate`
- `current-internal`
- `historical-archive-candidate`
- `stale-needs-update`
- `generated-or-local-artifact-reference`
- `do-not-move-yet`

For every doc marked stale or archive-candidate, include:

- current path,
- reason,
- replacement/current source of truth,
- whether it can be moved safely,
- whether the user must approve.

## Public README Draft

`KMK_PUBLIC_README_DRAFT.md` should be a community-facing draft, not an implementation log.

It should include:

- short description of the fork,
- clear statement that this is an experimental personal fork,
- relationship to Komikku/Mihon/TachiyomiSY without overclaiming official support,
- feature overview grouped by stability:
  - stable personal features,
  - experimental recommendation features,
  - source evaluation features,
  - cross-extension/best-version tools,
  - import/export tools,
  - OCR-only features,
- installation/update caution,
- backup warning,
- source evaluation warning,
- OCR privacy/storage warning,
- known limitations,
- testing status summary from docs only,
- how to report issues later,
- what is not upstream-ready,
- branch/release line explanation:
  - KMK-Recs line,
  - KMK-OCR line.

It should not include:

- Claude prompts,
- Codex workflow notes,
- internal implementation chronology,
- giant file lists,
- claims that all features are community-ready,
- claims that source evaluation or OCR are safe for every device,
- claims that this can be cleanly merged upstream as-is.

## Generated Artifact And Git Hygiene Review

Using Phase 0 snapshot and read-only worktree checks, identify:

- APKs in the source tree,
- generated build outputs,
- downloaded crash logs,
- temporary files,
- local NAS/upload helper files,
- generated screenshots,
- files that probably belong outside source control,
- patterns missing from `.gitignore`.

Do not delete anything in this phase unless the user separately approves.

If `.gitignore` changes are needed, this plan should recommend exact patterns, but not apply them unless explicitly approved.

The audit should distinguish:

- files safe to remove from the repo workspace,
- files safe to ignore in git,
- files that may be important local artifacts,
- files that need user confirmation.

## Documentation Organization Proposal

If the docs are too noisy, propose a later archive structure. Do not move files yet.

Recommended structure to consider:

```text
docs/community/
docs/recommendations/
docs/ocr/
docs/security/
docs/database/
docs/ux/
docs/testing/
docs/architecture/
docs/internal/history/
```

Possible archive rules:

- current state docs stay in their feature folder,
- implementation plans that are superseded move to `docs/internal/history/`,
- active phase plans stay in `docs/community/`,
- public-facing docs stay short and avoid AI workflow language,
- detailed implementation notes remain available but not front-and-center.

Claude should not apply this structure yet unless the user approves.

## Mojibake And Formatting Review

The audit should search docs and visible strings for common mojibake patterns caused by mis-decoded Unicode punctuation.

Examples to look for include corrupted forms of:

- em dash and en dash,
- right arrow,
- middle dot,
- multiplication sign,
- greater-than-or-equal and less-than-or-equal signs,
- curly quotation marks.

Claude should search for suspicious byte-pattern text such as `â`, `Â`, and `Ã` in docs and visible strings, then inspect matches manually before recommending fixes.

For this phase:

- list affected files,
- count or sample affected lines,
- identify whether the damage is docs-only or user-visible UI,
- propose whether to fix in Phase 2 or defer to later code/string cleanup.

Do not edit user-visible strings without confirming they belong in `i18n-kmk` base resources and without user approval.

## Version Reference Cleanup Review

Identify stale references to:

- old KMK-Recs versions,
- old OCR versions,
- old APK names,
- old implementation statuses,
- docs that say `planning` even though implemented,
- docs that say features are deferred when now implemented,
- docs that mention generated APKs as current handoff when they are no longer current.

Recommend exact files to update later.

Do not blindly replace version numbers globally. Version history may be valid in historical implementation notes.

## Code Alignment Notes

Phase 2 does not refactor code, but it should carry forward Phase 0-1's code alignment findings.

The documentation hygiene audit should include a short section:

```text
## Code Style/Architecture Findings To Carry Forward
```

This section should summarize mismatches found in the feature classification matrix:

- code that looks unlike nearby Komikku patterns,
- oversized files or screen models,
- hardcoded strings,
- noisy comments,
- inconsistent error handling,
- broad catch blocks,
- package/module placement concerns,
- migration/proto style concerns.

No code changes should be made in Phase 2 to fix these. They belong to later architecture/style phases.

## Acceptance Criteria

Phase 2 planning/implementation is complete only if:

- `KMK_DOCUMENTATION_HYGIENE_AUDIT.md` exists.
- `KMK_PUBLIC_README_DRAFT.md` exists.
- No app source code was changed.
- No files were deleted or moved without explicit user approval.
- Generated/local artifacts are identified.
- Stale docs are classified rather than blindly removed.
- Mojibake and stale version references are listed.
- Public-facing docs clearly label experimental features.
- OCR remains clearly separate from normal KMK-Recs.
- Source Evaluation remains clearly experimental/security-sensitive.
- The draft avoids internal AI workflow language.
- Any proposed `.gitignore` or archive changes are listed for user approval before execution.

## Summary Claude Should Provide

When finished, Claude should report:

- what docs it reviewed,
- what generated/local artifacts it found,
- which docs are current,
- which docs are stale,
- which docs should be archived later,
- what public README draft it created,
- what version/mojibake issues remain,
- whether it changed anything besides markdown,
- what should happen in Phase 3.

## Important Reminder For Claude

This phase is repo/documentation hygiene, not app implementation.

If you discover a serious app bug while reading, record it in the hygiene audit or carry-forward notes. Do not fix it in this phase.




