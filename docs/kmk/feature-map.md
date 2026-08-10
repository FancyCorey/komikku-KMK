# Feature map

This page helps contributors move from a visible feature to the code that owns it. It lists what each feature does, its main implementation area, and the screen states that must remain supported. For instructions, use the [user guide](user-guide.md); for behavior and diagrams, use the [feature explanations](features/README.md).

| Feature family | User-visible behavior | Primary implementation owners | Expected states |
| --- | --- | --- | --- |
| [For You](features/for-you.md) | Personalized source rows, exploration candidates, filters, exposure-aware ordering | `exh/recs/BrowsePersonalRecommendationsScreenModel.kt`, `exh/recs/BrowsePersonalRecommendationsTab.kt` | loading, loaded, partial, empty, recoverable error |
| [Ratings and preferences](features/ratings-and-linked-versions.md) | Love, Like, Dislike, Not Interested, collections, reversible transitions | `exh/recs/loved/`, `eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt` | neutral, loved, liked, disliked, not interested |
| [Source Evaluation](features/source-evaluation.md) | Evaluate source fit, continue or reassess work, show short explanations | `exh/recs/evaluation/` | ready, running, partial, completed, cancelled, failed |
| [Sources To Try](features/source-discovery-and-priority.md) | Suggest non-installed sources from taste and evaluation signals | `exh/recs/discovery/`, recommendation settings screen models | loading, suggestions, empty, install screen, unavailable |
| [Cross-source matching](features/cross-source-matching.md) | Find related versions, create links and groups, choose a primary version | `exh/recs/matching/` | searching, candidates, selected, linked, no matches, partial failure |
| [Best Version](features/cross-source-matching.md) | Compare linked versions and continue through Komikku's existing migration flow | `exh/recs/bestversion/` | loading, comparable, insufficient data, confirmed, cancelled, failed |
| [Source runtime](features/source-reliability.md) | Keep one extension failure from breaking unrelated source work and preserve cancellation | `eu/kanade/tachiyomi/source/SourceRuntime.kt` | success, skipped, isolated failure, cancellation |
| [Reader controls](features/reader-tools.md) | Timer, local schedule, completion prompt, linked-version ratings, jump to last read | `eu/kanade/tachiyomi/ui/reader/`, manga screen and toolbar | allowed, restricted, grace, completed, prompt after exit, no read position |
| [OCR search](features/ocr-search.md) | Build and search a local text index for downloaded chapter pages | `exh/ocr/`, `ocr_indexed_page.sq` | no index, indexing, searchable, partial, cancelled, failed |
| [Evaluation Mode](features/evaluation-mode-and-exports.md) | Hide source and repository names without changing app behavior | `exh/util/EvaluationMode*.kt` | disabled, enabled, reversible action available, outside action cannot be undone |
| [Export and diagnostics](features/evaluation-mode-and-exports.md) | Export through Android's document picker and show short explanations without private details | settings/export coordinators and evaluation diagnostic policies | ready, chooser, success, cancelled, failed, cleanup confirmed |
| [Backup and portability](features/backup-and-restore.md) | Preserve supported ratings, recommendation settings, links, and source-quality state in app backups | backup creators/restorers and taste repositories | selected, encoded, restored, partial, failed |
| [Extension operations](features/extension-management.md) | Isolate unsafe extensions, install or remove through Android, and export only the selected packages | extension manager, installers, `ExtensionApkExporter.kt` | ready, consent, success, cancelled, failed, cleanup confirmed |
| [Security and integration](features/security-boundaries.md) | Check outside input, report failures without leaking raw details, preserve cancellation, and limit saved changes | deep-link, WebView, source runtime, export, and Action History code | accepted, rejected, isolated failure, cancelled, confirmed change |

## Settings

Recommendation settings use the app's normal list and section patterns. They are grouped into For You sources, Taste and filters, Source Evaluation, Sources to try, and Management and diagnostics. Ranking and exposure values that users can change are stored as settings and checked before use; they are not hidden in screen code.

## Failure behavior

If one source is unavailable, other rows and screens continue to work. Useful partial results remain visible, cancelling an operation stops it normally, and error messages stay short without revealing private details. A screen that needs a missing extension, network response, tracker account, or Android document provider must say that it is unavailable instead of pretending the action succeeded.

## Known limitation

Bulk preference changes made from For You can be reversed through Action History. The confirmation message does not yet include its own **Undo** button, so the reversal is available but less obvious than it should be.
