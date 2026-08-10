# KMK feature diagrams

The architecture page gives a six-diagram overview. The pages below explain each feature domain in more detail. Every diagram is followed by a short explanation, so readers do not need to interpret the diagram alone.

- [For You](for-you.md): retrieval, filtering, ranking, exposure, and refresh.
- [Ratings and manga groups](ratings-and-groups.md): preference states, grouping, maintenance, and reversal.
- [Source Evaluation](source-evaluation.md): queueing, source checks, results, isolated failures, and reassessment.
- [Sources to try and source priority](sources-and-priority.md): suggestions, eligibility, ordering, and source preferences.
- [Find other versions and Best Version](best-version.md): matching, preview preparation, comparison, and migration.
- [Source runtime](source-runtime.md): extension-call isolation, classification, suppression, and batch behavior.
- [Recommendation settings](recommendation-settings.md): navigation, search, saved settings, and quick access.
- [Reader controls](reader.md): loading, completion prompts, schedules, timers, and chapter-list navigation.
- [OCR search](ocr.md): local indexing, page processing, search, navigation, and cleanup.
- [Export, Evaluation Mode, and screenshots](export-and-evidence.md): document creation, cleanup, hidden source names, and screenshot review.
- [Backup and portability](backup-and-portability.md): selection, restore steps, conflict handling, and exclusions.
- [Extension operations](extension-operations.md): loading, package actions, exported-file cleanup, and the move into source evaluation.
- [Security and integration](security-and-integration.md): untrusted input, clear failure types, cancellation, and carefully limited changes.
- [System-wide architecture](system.md): app structure, responsibilities, storage, action flow, and public files.

These pages describe current behavior. They leave out raw device data, temporary test captures, and old designs that no longer apply.
