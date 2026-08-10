# Komikku KMK public release notes

This page summarizes the features included in the current version. The in-app **KMK What's new** screen keeps the detailed history of changes.

## Current KMK feature version

The current in-app feature version is **KMK-Recs v0.8.20-fix5**.

## Included feature families

- Personalized For You and Top Picks views with hard eligibility checks, source-aware partial results, configurable recent-catalogue exploration, exposure-aware reordering, long-press bulk preferences, and per-source status explanations.
- Group recommendations for rated or linked manga, with bounded concurrent source work, progressive rows, configurable preview size, and independent timeout or failure states.
- Recommendation bundle export and reviewed import, including source resolution, validation, and an explicit library-add step.
- Searchable, sectioned Recommendation Settings covering taste and tags, known or rated manga, minimum chapters, source priority, matching, result limits, discovery, cache, and maintenance.
- Rating-derived tag suggestions, taste diagnostics, source-quality marks and history, Source Evaluation continuation and reassessment, and searchable or sortable Sources to Try.
- Love, Like, Dislike, and Not Interested as equal preference choices, with searchable collections, bulk actions, linked-version groups, primary versions, and conflict-checked Action History undo.
- Cross-source matching and Best Version comparison with chapter selection, independent preview retry, full-screen samples, a keep-current baseline, and a separate handoff to Komikku's migration flow.
- Active-reading timer, recurring reading schedule, deferred completion preference and linked-version rating, and Jump to last read.
- Local, cancellable OCR indexing and search for downloaded pages, with scoped cleanup and no recognized text in backup or sync.
- KMK backup and restore support for ratings, recommendation settings, linked groups and primaries, source quality, and evaluation state.
- Android document-based recommendation and extension export with exact-created-file cleanup, plus supported package install and removal operations.
- Evaluation Mode, privacy-aware diagnostics, reversible and visibility-only Action History records, guarded navigation, and isolated extension failures.
- A grouped in-app KMK change history that remains separate from Komikku's own release notes.

## Compatibility with Komikku

KMK continues to use Komikku's existing Library, Browse, Reader, Settings, backup, tracking, extension, and migration flows. When possible, a source failure affects only that source. Cancelling an action stops it normally, and the app does not promise to undo changes that already happened in Android or on an outside service.

## Fork identity

Komikku KMK is an independent fork. It keeps the original Komikku artwork, uses the package name `app.komikku.kmk` and launcher name **Komikku KMK**, and checks `FancyCorey/komikku-KMK` for its own updates. Official Komikku releases and support channels remain separate.

## Known limitation

Bulk preference actions made directly from For You are recorded in Action History, but the immediate completion message does not provide an inline **Undo** action. Reversal remains available through Action History when its conflict checks allow it.
