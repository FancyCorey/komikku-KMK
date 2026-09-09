# KMK-Recs v0.8.9 Post-Implementation Evaluation

**Date:** 2026-07-16  
**Scope:** Review of Claude's reported v0.8.9 implementation before official Komikku 1.14.0 reconciliation  
**APK reported:** `private/Komikku-v1.13.6-kmk.8.9-debug.apk`  
**Status:** Partially complete; suitable as a development baseline, not yet a complete implementation of the v0.8.9 plan and not 1.14.0-based.

## Verified From The Handoff Report

- The What's New surface reuses the official Komikku/Mihon Markdown renderer.
- The new v0.8.9 entry uses the intended `New`, `Improve`, and `Fix` hierarchy.
- Historical entries remain individually preserved.
- Recommendation Settings search has ranked matching and synonym handling.
- A punctuation-normalization issue was found and corrected during implementation.
- Claude reports `spotlessCheck`, the full test suite, and `assembleDebug` passing.

These build/test claims still require reproduction in a JDK 17+ environment before release sign-off.

## Incomplete Requirements

### R-089-01: Recommendation Settings search is category-only

The implementation indexes the seven top-level Recommendation Settings destinations, but does not index individual controls or provide stable in-screen anchors.

The approved plan requires searchability for controls including, at minimum:

- source priority and reorder behavior;
- language filters;
- preferred, blocked, and aliased tags;
- result limits and source-row budgets;
- hide-known and not-interested behavior;
- source evaluation, reassessment, continuation, and recommendation compatibility;
- installer mode and Shizuku behavior;
- Sources To Try;
- group matching and same-manga results;
- cache/discovery reset;
- diagnostics, backup, and sync-related management.

The current implementation therefore provides useful section navigation but not the full requested settings search. This must remain open and must be reconciled with the 1.14 settings/navigation changes rather than implemented blindly before the upstream merge.

### R-089-02: Historical What's New formatting remains mixed

The new v0.8.9 entry follows the official structure, but the prior 76 entries were preserved without retroactive formatting. This is defensible for history preservation, but it leaves the overall page visually inconsistent with the requested official-Komikku-style presentation.

Before final release, decide and document one of these approaches:

1. Keep old entries unchanged and clearly scope the new format to future entries; or
2. Convert historical entries mechanically and safely to the same hierarchy while preserving their exact user-facing meaning.

The decision must consider translation, Markdown-renderer support, long-page performance, and historical accuracy. Do not manually rewrite historical content without a before/after audit.

## 1.14.0 Compatibility State

The reported APK is still based on Android version `1.13.6`. The v0.8.9 implementation did not integrate official Komikku 1.14.0, which is expected because that was a separate reconciliation task.

The following blockers remain unchanged:

- SQLDelight migration-number collision at migrations `45` and `46`.
- Missing official 1.14 database indexes in the current migration history.
- Extension repository/store model divergence.
- Official extension install/update/ANR fixes not yet reconciled with KMK evaluation install/cleanup.
- Source API and networking changes not yet reconciled with recommendation/source-evaluation code.
- Official backup/sync changes not yet reconciled with KMK proto fields.
- Reader, migration, selection, tracker, logging, and lifecycle changes not yet reconciled.

The current APK must not be described as an official-Komikku-1.14-compatible build.

## Required Carry-Forward Work

Before the 1.14 implementation prompt is issued, Claude's work must account for:

1. The category-only Recommendation Settings search and its future control-level index/anchor design.
2. The historical What's New formatting decision.
3. The v0.8.9 renderer and search code as custom changes that must survive the upstream merge.
4. The v0.8.9 tests and release-note entry as part of the upstream reconciliation regression suite.
5. APK metadata changing from the current `1.13.6` base only after the source and database reconciliation is complete.

## Release Assessment

**v0.8.9 development handoff:** Conditionally acceptable if the reported build/tests are reproduced and the known gaps are accepted.  
**Complete v0.8.9 plan:** Not complete because control-level settings search and the historical formatting decision remain open.  
**Komikku 1.14.0 compatibility:** Not ready.  
**Public/community release:** Not ready until the migration bridge, upstream reconciliation, device QA, and documentation gates are complete.
