# Komikku KMK public release notes

This page summarizes the feature set represented by the current public source tree. The in-app **KMK What's new** screen remains the detailed chronological record.

## Current KMK feature version

The current in-app feature version is **KMK-Recs v0.8.20-fix5**.

## Included feature families

- Personalized For You rows with eligibility checks, source-aware retrieval, a bounded recent-catalogue lane, exposure-aware reordering, and partial-result handling.
- Love, Like, Dislike, and Not Interested as peer preference states, with collections, linked-version grouping, and guarded Action History restoration.
- Source Evaluation, Sources To Try, source-priority controls, quality diagnostics, and extension-failure isolation.
- Cross-source matching and Best Version comparison through Komikku's established migration flow.
- Reader timer, local reading schedule, deferred latest-chapter preference prompt, linked-version rating handoff, and Jump to last read.
- Local OCR indexing and search for downloaded pages, with scoped cleanup and backup/sync exclusion.
- KMK backup and restore support for ratings, recommendation settings, links, source quality, and evaluation state.
- Android document-based export with exact-artifact cleanup, plus supported extension package operations.
- Evaluation Mode and privacy-bounded diagnostics for review and evidence capture.

## Compatibility boundary

KMK retains Komikku's established Library, Browse, Reader, Settings, backup, tracking, source-extension, and migration ownership. Source-specific failures are isolated where possible, cancellation remains cancellation, and completed remote or Android package effects are not described as locally reversible.

## Fork identity

Komikku KMK is an independent fork. Its release package is `app.komikku.kmk`, its launcher identity is distinct, and its updater targets `FancyCorey/komikku-KMK`. Official Komikku releases and support channels remain upstream resources and are not presented as KMK releases.

## Known limitation

Bulk preference actions made directly from For You are recorded in Action History, but the immediate completion message does not provide an inline **Undo** action. Reversal remains available through Action History when its conflict checks allow it.
