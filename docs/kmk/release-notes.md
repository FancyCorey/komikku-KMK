# Komikku KMK public release notes

This page summarizes the features included in the current version. The in-app **KMK What's new** screen keeps the detailed history of changes.

## Current KMK feature version

The current in-app feature version is **KMK-Recs v0.8.20-fix5**.

## Included feature families

- Personalized For You rows with eligibility checks, source-aware retrieval, a configurable share of recent catalogue entries, reordering for repeatedly shown cards, and useful partial results.
- Love, Like, Dislike, and Not Interested as equal preference choices, with their own collections, linked-version groups, and conflict-checked undo through Action History.
- Source Evaluation, Sources To Try, source-priority controls, quality diagnostics, and extension-failure isolation.
- Cross-source matching and Best Version comparison through Komikku's existing migration flow.
- Reader timer, local reading schedule, a latest-chapter preference prompt shown after exit, linked-version ratings, and Jump to last read.
- Local OCR indexing and search for downloaded pages, with controls for clearing selected index data and no OCR text in backup or sync.
- KMK backup and restore support for ratings, recommendation settings, links, source quality, and evaluation state.
- Android document-based export that can clean up the exact file it created, plus supported extension package actions.
- Evaluation Mode and privacy-aware diagnostics for screenshots and review.

## Compatibility with Komikku

KMK continues to use Komikku's existing Library, Browse, Reader, Settings, backup, tracking, extension, and migration flows. When possible, a source failure affects only that source. Cancelling an action stops it normally, and the app does not promise to undo changes that already happened in Android or on an outside service.

## Fork identity

Komikku KMK is an independent fork. It keeps the original Komikku artwork, uses the package name `app.komikku.kmk` and launcher name **Komikku KMK**, and checks `FancyCorey/komikku-KMK` for its own updates. Official Komikku releases and support channels remain separate.

## Known limitation

Bulk preference actions made directly from For You are recorded in Action History, but the immediate completion message does not provide an inline **Undo** action. Reversal remains available through Action History when its conflict checks allow it.
