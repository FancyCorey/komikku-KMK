# KMK feature guides

These guides explain KMK by user goal rather than by internal module name. Each page describes what the feature is for, where to find it, how its normal and exceptional paths behave, what data it changes, and which source files own the behavior. The diagrams summarize a flow; the prose around them provides the detail needed to understand and maintain it.

## Discovery and recommendations

- [Personalized recommendations and discovery](personalized-recommendations-and-discovery.md): how For You builds familiar and exploratory rows, applies hard filters, rotates repeatedly exposed titles, supports Top Picks and bulk actions, loads group recommendations, and shares validated recommendation bundles.
- [Configuring personalized recommendations](configuring-personalized-recommendations.md): where recommendation controls live, how they are grouped, and which visible results they affect.
- [Evaluating recommendation source quality](evaluating-recommendation-source-quality.md): how Source Evaluation checks sources, preserves partial progress, explains failures, supports reassessment, and connects to taste and source-quality diagnostics.
- [Finding and prioritizing sources](finding-and-prioritizing-sources.md): how Sources to Try ranks options, hands installation to Android, and lets the reader control source priority.

## Manga preferences and cross-source tools

- [Rating manga and managing linked versions](rating-manga-and-managing-linked-versions.md): Love, Like, Dislike, Not Interested, preference collections, linked versions, and reversible changes.
- [Finding and comparing manga versions](finding-and-comparing-manga-versions.md): finding related editions, confirming matches, comparing chapters and previews, and handing a chosen version to migration.

## Reader and local tools

- [Reading schedule, completion, and chapter navigation](reading-schedule-completion-and-chapter-navigation.md): chapter loading, completion preferences, timer and schedule controls, and Jump to last read.
- [Searching downloaded pages with OCR](searching-downloaded-pages-with-ocr.md): local page indexing, search ranking, reader navigation, cancellation, and index cleanup.
- [Preserving KMK data in Komikku backups](preserving-kmk-data-in-backups.md): which recommendation and preference records join a backup, how restore reports partial results, and what remains excluded.

## Source, extension, and safety boundaries

- [Handling source and extension failures](handling-source-and-extension-failures.md): extension isolation, cancellation, retry, and mixed-result batches.
- [Managing and isolating extensions](managing-and-isolating-extensions.md): package loading, install and remove operations, exported-file cleanup, and the handoff into source evaluation.
- [Sharing screenshots and exported files safely](sharing-screenshots-and-exported-files-safely.md): neutral labels, Android document creation, exact-file cleanup, and public-image review.
- [Validating links, actions, and file cleanup](validating-links-actions-and-file-cleanup.md): untrusted input, explicit failure states, cancellation, and bounded local changes.
- [How KMK features work together](how-kmk-features-work-together.md): app responsibilities, storage, action flow, Android integration, and privacy boundaries.

Use the [user guide](../user-guide.md) for instructions and the [feature map](../feature-and-code-map.md) for a compact implementation index.
