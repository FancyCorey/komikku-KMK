# KMK Personal Recommendations Current State

Date: 2026-06-28 (updated: 2026-06-28 — v0.7.25 offline handling + v0.7.26 min chapter filter)

Status: updated after v0.7.26 implementation.

## Feature Version

Current documented feature version:

```text
KMK-Recs v0.7.26
```

Current documented APK handoff:

```text
Komikku-v1.13.6-kmk.7.26-debug.apk
```

## Recommendation Systems

The app currently has two separate recommendation systems.

### Manga Detail Recommendations

Location: manga detail page Recommendations tab.

This uses recommendation providers such as tracker/provider recommendations and cross-extension genre search. The cross-extension logic is implemented through `CrossExtensionGenreSearchSource` and related manga-detail recommendation paging code.

This system is separate from Browse > For You.

### Browse > For You

Location: Browse tab For You page.

This is the KMK personal recommendation feed. It builds a taste profile from ratings and tag preferences, searches selected catalogue sources, shows source-specific rows, and builds a synthetic Top Picks row from the already-fetched source results.

The Top Picks row is not a separate source and does not run a second crawl.

## For You Source Selection

Current source pipeline:

```text
visible catalogue sources
-> recommendation language filter
-> manual source priority order
-> disabled source exclusion
-> fixed top-3 boosted source selection
-> adaptive batch fill
-> per-source recommendation searches
-> source rows + Top Picks from already-fetched results
```

Important current behavior:

- The recommendation language filter defaults to English.
- Local Source is explicitly excluded by `RecommendationSourceFilter` using source id `0`.
- Disabled sources are intentionally excluded from For You searches.
- Top-three priority sources receive larger caps and are fixed. Boosted status does not transfer if a top source has no matches.
- Adaptive fill searches in batches of 5, can attempt up to 40 sources, and stops when 20 useful source rows are found or the attempt list is exhausted.
- Sources that return no visible results are hidden from the For You UI but still record a last-run status.
- v0.7.25: On screen open or manual refresh, if the device has no internet connection, For You immediately shows a "No internet connection" message with a Retry button instead of attempting source queries. Uses `context.isOnline()`.
- v0.7.26: Minimum chapter count filter — configurable in Recommendation Settings (Off / 5 / 10 / 20 / 50). When set above 0, For You filters out manga whose locally-known chapter count is below the threshold. Manga with no locally-stored chapters (untracked) are never filtered. The filter applies post-fetch. Cache fingerprint includes `minChapterCount` so changing the setting invalidates the cache.

## Top Picks

Top Picks history:

- v0.4.0 added the original compiled row.
- v0.4.1 renamed it from "Combined Picks" to "Top Picks."
- v0.4.2 refined duplicate merging using exact normalized `title + author` OR exact normalized `title + artist`.
- v0.4.3 made the Top Picks header tappable.

Current Top Picks behavior:

- Inline For You row shows up to 20 ranked picks.
- Tapping the header opens `TopPicksScreen`.
- `TopPicksScreen` shows up to 50 ranked candidates from already-fetched For You results.
- No second recommendation crawl is launched for the drill-down screen.
- If For You is still loading, Top Picks detail may show the currently available partial candidate set.

## Ratings And Tags

Ratings:

- `Love`, `Like`, and `Dislike` feed the taste profile.
- Rated manga visibility is configurable.
- Current default behavior is documented as hiding disliked entries only.

Tags:

- Preferred tags increase recommendation scoring and search tag selection.
- Disliked tags reduce scoring.
- Blocked tags hard-filter candidates after fetching.
- Query-time blocked-tag exclusion is still deferred.

## Known Manga Filtering

Known-manga filtering is local DB only.

When "Hide known manga" is enabled, For You hides candidates that are:

- rated,
- in library/favorite,
- started,
- read,
- or present in local history.

As of v0.4.2:

- `hideKnownManga` is included in the profile fingerprint, so changing the setting invalidates incompatible cache entries.
- `loadFromCache()` re-applies the known-manga filter, matching live-search behavior.
- The filter fails open if the DB lookup fails.

## Source Status Diagnostics

As of v0.4.4:

- After each For You run, per-source statuses are persisted to `recommendation_last_source_run_statuses`.
- Recommendation Settings shows the last-run status for each source.
- The status list is headed by a note: "Statuses below are from the last For You refresh."
- Statuses update live when For You finishes while the settings screen is open.
- Current statuses include `Shown`, `NoMatches`, `FilteredOut`, `Error`, `OutsideAttemptLimit`, `HiddenByDuplicateHandling`, and a UI-derived disabled/not-checked state.
- `HiddenByDuplicateHandling` is recorded for sources whose results are fully hidden by cross-source display dedupe after the For You batch loop completes. The adjustment is computed by `adjustStatusesForDedupe()` in `RecommendationStatusAdjuster.kt`.

## Local Source Status

- Local Source is not searched by For You.
- Local Source is excluded intentionally by the language/source filter (`id == 0L`).
- This exclusion remains intentional because Local Source is for local manga files, not a catalogue recommendation engine.

## Cache

The recommendation cache is versioned and fingerprinted. It invalidates when relevant profile inputs change, including selected recommendation languages and the hide-known setting.

The cache stores recommendation rows from source searches. It does not create a full local manga catalogue.

## Cross-Extension Matching

As of v0.5.1:

- The manga detail rating dropdown includes three new actions: "Love other versions", "Like other versions", and "Dislike other versions".
- Tapping one of these opens `CrossExtensionMatchScreen`, a separate bounded search workflow.
- The matching workflow searches using the current manga title, filtered by recommendation language preference and sorted by source priority order. Local Source is excluded.
- Results are capped at 2 per source. Normal global search is uncapped.
- The origin manga is filtered out before the per-source cap is applied. If a source returns `[origin, A, B, C]`, the screen shows `[A, B]`, not `[A]`.
- The origin manga never appears as a selectable candidate and never counts toward "Selected N of M".
- `toggleSelection()` defensively ignores the origin key even from stale UI events.
- All non-origin candidates are selected by default. The user can deselect wrong candidates before confirming.
- Manual deselection is preserved if results update while loading.
- On confirmation, `SetMangaTasteBatch` writes a taste row for each selected candidate using `(source, url)` identity.
- Does not write any global search preferences.
- Cross-source link groups are fully implemented: `manga_cross_source_link` SQLDelight table (migration 50), domain model, interactors, backup at proto 624, restore, and sync merge. After confirming any cross-extension match (rating/seen/favorite), a link group is written that Loved Manga can use for grouping.
- As of v0.7.1: `CrossExtensionMatchScreen` stores only primitive route args (`modeKey: String`, `ratingValue: Int?`). Mode is reconstructed in `Content()` via `CrossExtensionMatchRouteMode`. This prevents `BadParcelableException` during Android state save.

## Non-Installed Extension Discovery

As of v0.6.1 (hardened from v0.6.0):

- Recommendation Settings includes a "Sources To Try" section at the bottom.
- A source appears only if it has meaningful positive evidence: conservative similarity to an already-installed source name (`SimilarToInstalledSource`).
- Language match, same repo, base URL, and generic content keywords are eligibility filters only â€” they no longer score and do not qualify a source on their own.
- `hasMeaningfulEvidence()` gates the scorer: if no `SimilarToInstalledSource` reason is found, the source is excluded.
- Similarity rules: exact normalized name match (score 0.60), containment where both names â‰¥ 8 chars (0.50), or distinctive token overlap â€” token length â‰¥ 5, not a generic word (0.50).
- Generic single-word names (`manga`, `scans`, `manhwa`, etc.) are excluded from both sides of comparison.
- If no qualified suggestions exist, the section shows an honest empty state.
- Suggestions use `SuggestionConfidence.LOW` (score < 0.55) or `MEDIUM` (score â‰¥ 0.55). HIGH confidence is reserved for installed-source fit learning.
- Scores are capped at 0.69 to keep non-installed suggestions below the 0.70+ range reserved for post-install evaluation.
- Each suggestion stores the original `Extension.Available` (not a `GetExtensionsByType` synthetic copy with modified `pkgName`). Installing uses `ExtensionManager.installExtension(suggestion.extension)` with the original identity.
- Dismissed suggestion keys are persisted in `SourcePreferences.dismissedNonInstalledRecommendationSources()` as semicolon-separated `"signatureHash|pkgName|sourceId"` strings.
- After install, the extension appears in `installedExtensionsFlow`, and the suggestion is automatically removed from the list.
- Language preference changes now trigger immediate suggestion refresh (5th combine source in `GetNonInstalledSourceSuggestions`).
- The list shows top 5 by default; a "Show N more" toggle expands.
- Installed-source fit learning is deferred â€” sources are labeled "Install to test with For You."

## Source Like/Dislike Preferences

As of v0.6.2:

- Recommendation Settings shows thumbs-up / thumbs-down icon buttons on each installed source row and on each Sources To Try suggestion row.
- **Like (installed source)**: marked visually with primary color. No effect on current For You source order.
- **Dislike (installed source)**: status shows "Disliked Â· excluded from For You" in error color. Source excluded from For You source candidate list. No uninstall.
- **Like (non-installed suggestion)**: suggestion visible even without metadata similarity, scores 0.68 (above normal matches), shows "You liked this source" reason.
- **Dislike (non-installed suggestion)**: suggestion hidden from Sources To Try.
- **Dismiss**: remains separate lightweight "not now" action, not a dislike.
- Key format: installed `i|sourceId`; available `a|signatureHash|pkgName[|sourceId]`.
- Like and dislike are mutually exclusive; tapping the active icon again resets to neutral.
- Preferences stored in `SourcePreferences.likedRecommendationSourceKeys()` and `SourcePreferences.dislikedRecommendationSourceKeys()`.
- Changes trigger immediate recomputation of Sources To Try and invalidate the For You cache.

## Extension Selective Uninstall

As of v0.6.6:

- "Select extensions" overflow action in Browse > Extensions enters selection mode.
- In selection mode: installed/untrusted rows show a `Checkbox`; available rows show normally with no checkbox.
- Active install/update rows (installStep not completed) are not selectable.
- "Uninstall selected (N)" button disabled when N = 0 or bulk uninstall is running.
- "Cancel" exits selection mode and clears selection.
- One confirmation dialog before uninstall begins; user informed Android may ask per extension.
- Uninstalls fire sequentially with 300ms delay to avoid rapid-fire Android intent stacking.
- Back press exits selection mode before clearing search query.
- State: `isExtensionSelectionMode`, `selectedExtensionKeys`, `isBulkUninstallingExtensions`.
- Normal install/update/open/trust/update-all behavior unchanged.

## Sources To Try Selective Install

As of v0.6.5:

- A "Select" TextButton next to "Install visible suggestions" enters selection mode.
- In selection mode, each suggestion row shows a leading `Checkbox` and the card is tappable to toggle.
- "Install selected (N)" is enabled only when N > 0 and no bulk install is running.
- "Cancel" exits selection mode without installing (selection cleared).
- `installSelectedSuggestions()` filters only visible, selected, not-already-installing suggestions before delegating to the safe `installSuggestions()` path.
- State: `isSuggestionSelectionMode: Boolean`, `selectedSuggestionKeys: ImmutableSet<String>`.
- Install/Dismiss buttons hidden in selection mode; Like/Dislike remain functional.

## Explicit Porn/Hentai Source Filter

As of v0.6.7:

- New toggle under Settings > Browse > NSFW content: "Block explicit porn/hentai sources".
- When enabled, `ExplicitSourceClassifier` hides clearly explicit sources from Browse > Sources, Browse > Extensions (available only), and Sources To Try.
- Classifier rules: explicit keywords in name (`hentai`, `porn`, `pururin`, `tsumino`, `8muses`, `hbrowse`, `luscious`, `doujins`, `multporn`, `xxx`, `erotic`, `smut`, `adult comic/manga/manhwa/manhua`) OR `hentai`/`porn` in package name OR known explicit source IDs (NHentai, Pururin, Tsumino, 8Muses, HBrowse, all E-Hentai and ExHentai source IDs).
- Does NOT block: `ecchi`, `nsfw`, `mature`, `lewd`, `adult` alone.
- Installed/untrusted explicit extensions remain fully manageable (update, uninstall unchanged).
- Default `false` â€” existing users see no change on update.
- Classifier is a pure object with no Android deps; fully unit-tested.

## KMK-Recs What's New

As of v0.6.7:

- `KmkRecsReleaseNotes.VERSION_CODE` is `716`.
- `KmkRecsReleaseNotes.VERSION_NAME` is `KMK-Recs v0.7.16`.
- Local KMK-Recs What's New contains user-facing recommendation changes only.
- The local KMK-Recs What's New screen no longer shows the no-op browser button.
- Upstream Komikku What's New behavior remains separate.

## Source Evaluation

As of v0.6.13:

- Source Evaluation is accessible from Recommendation Settings > Source Evaluation.
- Evaluates non-installed extensions one at a time: installs temporarily via Private installer (no global preference change), probes popular/latest/search pages, scores against taste profile, stores verdict, then uninstalls.
- Candidate pool: `GetSourceEvaluationCandidates` draws from the full available extension pool filtered by language/nsfw/disliked/installed. Not the Sources To Try selective list. `GetNonInstalledSourceSuggestions` is unchanged and still used by Sources To Try only.
- `SourceEvaluationCandidateFilter` â€” pure object (`buildPool` + `applyOptions`). Handles base pool filtering and option-based filtering separately. `startEvaluation()` uses pre-filtered `state.candidates` directly.
- Skip-already-evaluated filter uses `extensionKey` (`signatureHash|pkgName`) for correct extension-level matching.
- Candidate diagnostics shown on screen: eligible count, evaluated-hidden count, explicit-hidden count.
- Verdicts: `STRONG_FIT` (score 0.90 in Sources To Try), `WORTH_TRYING` (0.75), `EXPLICIT_HEAVY` (0.10, hidden when blockExplicit), `ECCHI_HEAVY` (0.30), `REJECTED` (hidden), `ERROR`, and others.
- Post-install evaluated scores exceed 0.70 (range reserved for evidence-backed sources; metadata-only scorer caps at 0.69).
- Batch sizes: 10, 25, 50, 100. 500/1000 deferred.
- Explicit-heavy and ecchi-heavy verdicts always remain distinct.
- SQLite migration 47.sqm creates the `source_evaluation` table on update from pre-v0.6.8 installs.
- Defensive `.catch` fallbacks on `subscribeAll()` prevent crashes if the table is absent: Sources To Try falls back to metadata-only suggestions; Source Evaluation screen shows empty past-evaluations list.
- Shizuku: setup card with Install/Open/Use/Stop/Uninstall/Refresh buttons; auto-refresh on screen resume; status distinguishes "ready+selected", "selected-but-not-ready", and "ready-not-selected".
- **Private-aware cleanup (v0.6.13):** `SourceEvaluationCleanupPolicy` classifies cleanup action. Private extensions (`isShared=false`) removed silently; system-installed (`isShared=true`) only prompt if `promptHeavyCleanupAllowed=true`. Pre-existing extensions detected before install and skipped.
- **Installer verification (v0.6.13):** `SourceEvaluationInstallerPolicy` now sets `requiresPromptWarning=true` for SHIZUKU (was wrongly `false`). Private mode shows description via typed enum key.
- **Installer policy messages typed (v0.7.17):** `PolicyResult.message: String?` replaced with `messageKey: InstallerPolicyMessage?` (enum with 8 cases). Screen maps each enum case to a KMR string via `toLocalString()` composable extension. This removes all hardcoded English from the installer UI and enables future localization.
- **Prompt-heavy warning dialog (v0.6.13):** Selecting SHIZUKU/CURRENT and starting with batch > 1 shows a confirmation dialog: "Use Private", "Continue anyway", or "Cancel".
- **UI labels (v0.6.13):** Private chip labeled "Private (recommended)". Shizuku card shows "Private is recommended for silent cleanup" when Shizuku selected and Private available; offers "Use Private" action.
- **Diagnostics logging (v0.6.13):** logcat prefix `"KMK SourceEvaluation install:"` on each extension: mode, override, wasPreExisting, post-install isShared, cleanup decision.
- **Timeout resilience (v0.6.14):** All `withTimeout()` calls replaced with `withTimeoutOrNull()` in `SourceEvaluationRunner`. Install timeout (90s), load timeout (20s), popular/latest probe timeout (30s), search probe timeout (25s) are now local per-extension/per-source failures. A slow or hanging source marks that extension/source as an error and evaluation continues. Status is only set to `Cancelled` when the user explicitly presses Cancel. Timeout diagnostics logged with prefix `"KMK SourceEvaluation timeout:"`. `completedCount` now advances for all handled extensions (including failures), so the progress bar reaches 100% when the batch completes.
- **Priority reset safety (v0.6.14):** `Reset priority` action removed from top app bar. "Restore default source order" `TextButton` added near Source Priority section (disabled while dragging). Tapping it opens an `AlertDialog` confirmation before the order is changed.
- **Results stability (v0.6.15):** `SourceEvaluationResultList` pure helper sanitizes (drops blank/duplicate `evaluationKey` rows) and sorts results in Kotlin. SQL `getAll`/`getAllAsFlow` now have `ORDER BY evaluated_at DESC` for deterministic base order. `ScreenModel.onEach` sanitizes before storing. Screen uses `itemsIndexed` with `stableUiKey()` â€” no `animateItem()`. Sort dropdown (6 modes: Best fit, Newest, Source name, Extension name, Search reliability, Explicit risk) in the past results section. `EvaluationResultRow` shows compact score subtitle and truncated error messages.
- **Clear confirmation (v0.6.15):** "Clear all" now shows a confirmation `AlertDialog` before deleting evaluation cache.
- **Crash quarantine (v0.6.16):** `SourceEvaluationRunner` writes a probe marker (`source_evaluation_probe_marker`, id=1 single-row) to SQLite before each risky network call (Downloading, LoadingSources, ProbingPopular, ProbingLatest, ProbingSearch, Cleanup). Marker is cleared in the `finally` block after normal extension completion. On next `SourceEvaluationScreenModel` init, the marker is read; `SourceEvaluationCrashRecoveryPolicy` decides: `MarkUnsafe` (marker < 24h old) â†’ calls `MarkSourceEvaluationUnsafe` â†’ clears marker; `ClearStale` (â‰¥ 24h) â†’ clears without marking; `DoNothing` (no marker). Unsafe extensions stored in `source_evaluation_unsafe_source` with crash count, phase, reason. `GetSourceEvaluationCandidates` combines the unsafe sources flow; `SourceEvaluationCandidateFilter.buildPool()` accepts `unsafeExtensionKeys: Set<String>` and skips/counts matching extensions. Migration 48.sqm creates both new tables.
- **Crash quarantine UI (v0.6.16):** On crash recovery: `screenErrorMessage` state shows a dismissable error card. If `unsafeSources` non-empty: quarantine card with "View" and "Clear quarantine" buttons; dialog with per-extension rows and "Remove" action; confirmation dialog for bulk clear. Candidate diagnostics shows `unsafeHiddenCount`.
- **Diagnostics (v0.6.16):** "Copy Diagnostics" TextButton in idle screen. `SourceEvaluationDiagnosticsBuilder` pure helper builds a text report (version, timestamp, options, counts, queue phase, Shizuku state, last error, last probe marker).

## Loved Manga View

As of v0.7.0, updated through v0.7.3:

- Accessible via the heart icon button in the For You tab action bar (between Refresh and Settings).
- Shows all manga with `MangaRating.LOVE` from **currently installed sources only**. Entries from uninstalled sources are hidden from display; their taste rows and cross-source link rows are preserved. Reinstalling a source makes its loved entries visible again.
- `LIKE`, `DISLIKE`, and `SEEN` entries are excluded.
- Source filtering uses `SourceManager.getVisibleCatalogueSources()`. If this call fails, an empty state is shown (fail closed â€” no uninstalled entries leak through). Local Source (`id == 0L`) is excluded by the same rule as For You.
- Manga rows are resolved from the local DB via `GetManga.await(mangaId)` with fallback to `await(url, sourceId)`. If unresolved, the title from `MangaTaste` is shown and a placeholder cover is used.
- Tapping a manga navigates to its detail page (`MangaScreen(taste.mangaId, true)`).
- "Group clear duplicates" checkbox at the top of the grid toggles conservative duplicate grouping.

**Duplicate grouping rules â€” tiered evidence strategy (as of v0.7.2):**
- Tier 1 (strongest): Confirmed cross-source link group. If two loved manga share the same `group_id` in `manga_cross_source_link`, they group regardless of title or description.
- Tier 2: Exact normalized title + same non-blank author.
- Tier 3: Exact normalized title + same non-blank artist.
- Tier 4: Exact normalized title + exact same description (both â‰¥ 50 chars).
- Tier 5: Exact normalized title + highly similar long description (token Jaccard â‰¥ 0.85, both â‰¥ 80 chars).
- Tier 6: Similar title (Jaccard â‰¥ 0.80) + same non-blank author.
- Tier 7: Similar title (Jaccard â‰¥ 0.80) + same non-blank artist.
- Never groups by title alone. Blank/weak metadata â†’ always standalone.
- The first-encountered entry for a group becomes the representative.
- Grouped entries show a "%1$d versions" badge at the top-right corner of the cover.
- No taste rows are altered. Grouping is display-only.
- `LovedMangaScreenModel` loads `manga_cross_source_link` rows on startup and passes `linkGroupId` into the grouper. Fails open with empty map if link loading fails.
- Romanized/translated titles without a cross-source link or supporting author/artist metadata remain standalone.

As of v0.7.14: `LoveSortMode` enum (RECENT, OLDEST, TITLE_AZ, SOURCE) with sort chips. Sort applies before grouping. As of v0.7.15: "No clear duplicates found." feedback shown when grouping is on but nothing was grouped.

**Files:**
- `exh/recs/loved/LovedMangaDuplicateGrouper.kt` â€” tiered grouper with `LovedMangaGroupReason` enum and token Jaccard similarity
- `exh/recs/loved/LovedMangaSourceFilter.kt` â€” pure installed-source filter helper (v0.7.3)
- `exh/recs/loved/LovedMangaScreenModel.kt` â€” screen model (Loading/Empty/Error/Success states); injects `GetCrossSourceMangaLinks` and `SourceManager`; `LoveSortMode` enum and `sortEntries()` pure helper (v0.7.14)
- `exh/recs/loved/LovedMangaScreen.kt` â€” Voyager screen with grid, toggle row, sort chips, version badge, and no-duplicates feedback
- `app/src/test/.../LovedMangaDuplicateGrouperTest.kt` â€” 38 unit tests
- `app/src/test/.../LovedMangaSourceFilterTest.kt` â€” 9 unit tests (v0.7.3)
- `app/src/test/.../LovedMangaSortTest.kt` â€” 8 unit tests for sort modes (v0.7.15)

## Source Status Display Order

As of v0.6.20:

- Recommendation Settings shows a non-draggable "Source Status" section below the drag-to-reorder priority list.
- Sources are grouped by last For You run status: **With results** (HasMatches) first, **No results** (no matches) second, **Disliked** last.
- Within each group, sources appear in priority order (user's saved drag order).
- `SourceStatusDisplayOrder` pure helper computes grouping and sort order. `SourceDisplayOrderInput(sourceId, priorityIndex, hasMatches, isDisliked)` is the input per source.
- The priority drag list is unchanged â€” users still set priority by dragging. The status section is read-only.

## Seen / Already Read Manga Marker

As of v0.6.20:

- Manga detail page rating dropdown includes "Mark as seen" / "Clear seen" item (below the existing "Other versions" divider).
- Also includes "Seen other versions" item (always visible when the callback is set, as of v0.7.1) â€” opens `CrossExtensionMatchScreen` with `CrossExtensionMatchMode.MarkSeen`.
- `MarkSeen` mode writes to the `seenRecommendationMangaKeys` preference via `SeenRecommendationMangaStore`. It does not write taste rows.
- Seen entries are stored as semicolon-separated `"sourceId|url"` strings. Pipe-in-URL is handled correctly (only first `|` is the separator).
- Seen manga are **always** filtered from For You regardless of the "Hide known manga" setting.
- `seenMangaCount` is included in the recommendation `profileFingerprint()`, so the cache is invalidated whenever the seen set changes.
- `isSeen: Boolean` state in `MangaScreenModel.State.Success` tracks seen status for the current manga. Updated live by `markSeen()` / `clearSeen()`.
- Backup/restore for seen entries is deferred.

## Source Evaluation Reassessment

As of v0.6.20:

- Source Evaluation screen loads `currentRatedCount` (from `GetMangaTaste.awaitAll().size`) and `reassessmentBaselineCount` (from preferences) on open.
- When `currentRatedCount - reassessmentBaselineCount >= 100`, an `InfoCard` prompt is shown: "N new ratings since last source evaluation. Reassessment is recommended."
- "Reassess sources" button is always shown. Sets `skipAlreadyEvaluated = false`, then starts evaluation. Button labeled "Reassess using current tastes" when below threshold.
- Baseline is updated automatically when evaluation status transitions to `Completed`. Cancelled or failed runs do not update the baseline.
- Preferences: `sourceEvaluationLastReassessmentRatingCount`, `sourceEvaluationLastReassessmentAt`.

## Source Explainability

As of v0.6.20, updated in v0.7.15:

- Each past result row in Source Evaluation shows evidence strength and evaluation freshness appended to the subtitle.
- Evidence strength: Strong (many tag+title matches, high search success, large sample), Moderate, Weak, or Low confidence (minimal data).
- Last evaluated: "Last evaluated today" or "Last evaluated N days ago."
- As of v0.7.15: logic uses a pure `EvidenceStrength` enum classifier (`evidenceStrength()`) and a day-count helper (`lastEvaluatedDaysAgo()`). The composable maps these to KMR strings via `stringResource(...)`. No hardcoded English strings remain.
- Verdict badge labels ("Strong Fit", "Worth Trying", etc.) are also KMR strings as of v0.7.15.

## Management Controls

As of v0.6.20:

- Source Evaluation screen has a collapsible "Source management" section (collapsed by default).
- Three actions, each guarded by a confirmation dialog:
  - **Reset disliked sources**: clears `dislikedRecommendationSourceKeys` preference.
  - **Reset reassessment baseline**: clears the rating count and timestamp preferences.
  - **Clear seen manga**: clears `seenRecommendationMangaKeys` preference.
- `ManagementAction` enum in `SourceEvaluationScreenModel` types the three cases.

## Source Evaluation Extension Version Metadata and Update Detection

As of v0.7.4:

- `source_evaluation` table gained three nullable columns via migration 51: `extension_version_name`, `extension_version_code`, `extension_apk_name`.
- `SourceEvaluationScorer.score()` and `errorRecord()` accept these three optional fields and write them to `SourceEvaluation`.
- `SourceEvaluationRunner` passes `ext.versionName`, `ext.versionCode`, `ext.apkName` from `Extension.Available` to the scorer at all three callsites (score, per-source error, per-extension error).
- `SourceEvaluationUpdatePolicy` pure helper: `detectUpdateStatus(evaluation, available)` compares stored vs available versionCode; `detectForPool(evaluations, available)` uses best stored versionCode. Returns `UPDATED`, `NOT_UPDATED`, `UPDATE_UNKNOWN`, or `NEVER_EVALUATED`.
- `SourceEvaluationCandidateFilter.applyOptions()` gained `onlyUpdatedEvaluated: Boolean = false`. When true, only candidates with confirmed `UPDATED` status pass.
- `SourceEvaluationScreenModel.State` gained `updatedEvaluatedExtensionCount: Int`. Computed in `applyOptionsAndUpdateState()` across all eligible candidates.
- `startReassessUpdated()` action: sets `onlyUpdatedEvaluated = true`, `skipAlreadyEvaluated = false`, re-applies options, launches evaluation if candidates exist, then resets options.
- `SourceEvaluationScreen` shows "N evaluated extension(s) have updates" info card and "Reassess updated extensions" button when count > 0.
- **Not implemented**: actual bounded rec-fit probe execution in `SourceEvaluationRunner`. See `SourceRecommendationFitEligibility` and `SourceRecommendationFitScorer` below.
- Pre-existing test bug fixed: `GetTasteProfileTest.fakeTasteRepo` was missing 7 `CrossSourceMangaLink` abstract method stubs, blocking test compilation.

## Recommendation Fit Helpers

As of v0.7.4:

- `SourceRecommendationFitEligibility` â€” pure stateless gate. Checks verdict (`STRONG_FIT` or `WORTH_TRYING` only) and minimum sample count (`MIN_SAMPLE_COUNT = 3`). Returns `ELIGIBLE`, `INELIGIBLE_VERDICT`, or `INSUFFICIENT_EVIDENCE`.
- `SourceRecommendationFitScorer` â€” pure stateless scorer. Takes `Outcome` (visible candidates, filtered-out count, blocked-tag candidates, matched groups, top picks contribution, no-matches, errors, avg score) and returns a quality score in `[0.0, 1.0]`.

As of v0.7.6, probe execution is live (no longer deferred):

- `SourceRecommendationFitProbe` â€” bounded 2-plan probe. Calls `getSearchManga(page=1, ...)` with 30s timeout per plan. Returns `ProbeOutcome` with query stats, candidate counts, match groups, and error info.
- `SourceRecommendationFit` domain model â€” stores fitKey (= `"${evaluationKey}::rec_fit"`), evaluationKey, sourceId, extensionPkgName, signatureHash, extensionName, sourceName, lang, evaluatedAt, probe stats, qualityScore, verdict (`RecommendationQualityVerdict`), reasonsJson, errorMessage.
- `RecommendationQualityVerdict` enum â€” GREAT, GOOD, MIXED, WEAK, NO_MATCHES, ERROR, TOO_LITTLE_EVIDENCE.
- `GetSourceRecommendationFit` â€” interactor to load fits by evaluationKey or as a map.
- `UpsertSourceRecommendationFit` â€” interactor to write/update a fit record.

## Source Evaluation Installed-Source Display Filter

As of v0.7.6:

- `SourceEvaluationDisplayFilter` â€” pure stateless helper. `filter(evaluations, installedExtensionKeys, showInstalled)` returns `FilterResult(visible: List<SourceEvaluation>, hiddenInstalledCount: Int)`.
- Installed key format: `"${signatureHash}|${extensionPkgName}"`.
- When `showInstalled = false`: evaluations matching installed keys are hidden; `hiddenInstalledCount` equals the number hidden.
- When `showInstalled = true`: all evaluations are visible; `hiddenInstalledCount = 0`.
- `SourceEvaluationScreenModel.State` holds `showInstalled: Boolean` (default `false`) and `hiddenInstalledCount: Int`.
- Screen shows a `FilterChip` with dynamic label: "Hide installed" when `showInstalled = true`, "Show installed" when `showInstalled = false`. Also shows "Hidden installed: N" count chip.

**v0.7.7 toggle fix:** The chip was incorrectly hidden when `showInstalled = true` because the condition was `hiddenInstalledCount > 0 || !state.showInstalled`. Fixed to `hiddenInstalledCount > 0 || state.showInstalled` so the chip always appears when installed rows are visible, allowing the user to hide them again.

## Source Evaluation Recommendation-Quality Probe

As of v0.7.6 (on-demand trigger added in v0.7.7):

- After a Source Evaluation batch completes, `SourceEvaluationRunner` automatically runs `SourceRecommendationFitProbe` for each STRONG_FIT and WORTH_TRYING source in the batch results.
- Results are stored as `SourceRecommendationFit` records via `UpsertSourceRecommendationFit`.
- Each past evaluation row shows a third line: "Recommendations: Great", "Recommendations: Good", etc., derived from `RecommendationQualityVerdict`.
- Non-promising rows (REJECTED, WEAK, EXPLICIT_HEAVY, etc.) show nothing on the third line.
- `SourceEvaluationScreenModel.State` includes `recommendationFitsByEvalKey: Map<String, SourceRecommendationFit>`, loaded from DB on init and after each probe run.

**v0.7.7 additions:**

- Promising rows without a stored `SourceRecommendationFit` now show "Recommendations: Not checked" instead of nothing.
- A "Recommendation Quality" section appears in the screen list above the sort header whenever `totalPromising > 0`. Shows missing count, "Evaluate recommendations" button, and "Re-check all" button.
- `evaluateRecommendationQualityForPromising(reCheckAll: Boolean)` action runs the probe inline for currently installed promising sources without re-running a full source evaluation batch.
- `SourceRecommendationQualityQueue` pure helper partitions evaluations into `missingPromising`, `checkedPromising`, `ineligible`.
- Screen model gains `recQualityRunning: Boolean`, `recQualityProgress: Int`, `recQualityTotal: Int` state fields.

**v0.7.7 follow-up fix (shipped in v0.7.8 APK):**

- `evaluateRecommendationQualityForPromising()` now handles non-installed promising sources. It resolves the extension from `lastCandidatePool.value?.allEligible`, temporarily installs via the configured installer override, probes, persists, and cleans up. Error messages reflect the real failure reason ("Extension not found in available sources", "Install failed or timed out", "Source not found in installed extension", etc.) instead of always writing "Extension not installed or source not found".
- `SourceRecommendationQualityExtensionResolver` â€” new pure resolver. `resolve(evaluation, available)` finds the best `Extension.Available` match using 4-step resolution: exact sig+pkg â†’ pkg-only â†’ sig+name â†’ name+lang (unambiguous only). Returns `Found`, `Ambiguous`, or `NotFound`.

**v0.7.10 fix:**

- `loadAvailableExtensionsForRecQuality()` added to `SourceEvaluationScreenModel`. Reads `extensionManager.availableExtensionsFlow.value` (full unfiltered list). Falls back to `lastCandidatePool.value?.allEligible` only if empty. This fixes the root cause: when no evaluation batch had run in the session, `lastCandidatePool` was null and every non-installed promising source got "Extension not found in available sources" ERROR.
- `SourceRecommendationQualityInstalledResolver` â€” new pure helper. 4-step installed extension matching: exact sig+pkg â†’ pkg-only â†’ sig+name â†’ name+lang. Returns `Found`, `Ambiguous`, or `NotFound`. Replaces the previous single `find { pkgName + signatureHash }` call.
- `SourceRecommendationQualitySourceResolver` â€” new pure helper. 5-step source-within-extension matching: exact source id â†’ name+lang â†’ name â†’ normalized name+lang â†’ normalized name. Returns `Found`, `Ambiguous`, or `NotFound`. Replaces `findSourceInInstalledExt()` (which had only 2 steps and a nullable return).
- Error messages are now stage-specific at each failure point in the installed and non-installed paths.
- `installedExtensionKeys` in `SourceEvaluationScreenModel` is now reactive: a flow observer on `extensionManager.installedExtensionsFlow` replaces the one-time snapshot. The display filter chip and hidden-count update immediately when extensions install or uninstall.
- `visibleSources` in `RecommendationsSettingsScreenModel` is no longer a static field. All three call sites now call `sourceManager.getVisibleCatalogueSources()` inline. A new reactive observer on `extensionManager.installedExtensionsFlow` calls `refreshVisibleSources()` to update `orderedSources`, `availableLanguages`, and `boostedSourceIds` whenever extensions change.

## Best Version / Chapter Quality Workflow

As of v0.7.9 (polished in v0.7.9, introduced in v0.7.8):

- "Find best version" item in the manga detail rating dropdown (after "Seen other versions") opens `BestVersionCompareScreen(originMangaId: Long)`.
- The screen runs a bounded parallel search across all configured same-language sources, using `SameMangaCandidateSearcher` â€” the same multi-query, per-source, capped pattern as cross-extension matching.
- Results cap is configurable (`sameMangaMatchResultsPerSource`, default 2 per source). Preselect behavior is configurable (`sameMangaMatchPreselectResults`, default true).
- The workflow follows a 10-step state machine: LoadingOrigin â†’ SearchingCandidates â†’ ConfirmCandidates â†’ LoadingChapters â†’ SelectChapter â†’ LoadingPreview â†’ ComparePreview â†’ PreparingMigration â†’ Done / Error.
- `BestVersionChapterMatcher.findMatch()` finds the closest chapter within Â±1.0 of the origin's chapter_number. `selectDefaultChapter()` picks in-progress > latest-read > latest by number.
- `BestVersionPageSampler.sample()` samples mid-chapter pages from a 30â€“75% window (skipping first 2 pages when avoidFirstPages=true, never including the last page). Sample size is configurable (2/5/10, default 5).
- Page previews are loaded via `HttpSource.getPageList` + `getImageUrl` and displayed with Coil3 `AsyncImage`.
- Sampled page thumbnails are tappable. Tap opens a fullscreen `FullscreenPagePreviewDialog` (v0.7.9).
- Fullscreen preview supports pinch-to-zoom (max 5Ã—) and pan via `detectTransformGestures`. Closing returns to the comparison screen with all state intact (v0.7.9).
- After the user selects the best candidate, a Migrate / Copy / Cancel dialog calls `MigrateMangaUseCase`. Cancel correctly dismisses the dialog (v0.7.9 fix).
- The dialog guard requires `selectedBestKey` to resolve to a real candidate in `selectedCandidates`. Stale keys auto-clear via `LaunchedEffect` (v0.7.9).
- `dismissMigrationDialog()` sets `selectedBestKey = null` and `isMigrating = false` without clearing any other preview or candidate state (v0.7.9).
- On confirmation, a `MangaSourceQualitySignal` record is written to the new `manga_source_quality_signal` table (migration 53). This table stores origin/selected source+url, chapter number, sample size, sampled page URLs, and a timestamp.
- As of v0.7.16, Best Version quality signals are included in backup and sync via `BackupMangaSourceQualitySignal` at proto field 625. Restore skips duplicates by origin+selected source identity.
- Recommendation Settings has a new "Same manga matching" section with 4 preference items (results per source, preselect, sample size, avoid first pages).
- `SameMangaMatchSettings` is a pure data class with clamp helpers for the two list preferences.
- `CrossExtensionMatchScreenModel` now reads the cap and preselect values from preferences rather than using hardcoded constants.

**New files (best version):**
- `exh/recs/matching/SameMangaMatchSettings.kt`
- `exh/recs/matching/SameMangaCandidateResult.kt`
- `exh/recs/matching/SameMangaCandidateSearcher.kt`
- `exh/recs/bestversion/BestVersionPageSampler.kt`
- `exh/recs/bestversion/BestVersionChapterMatcher.kt`
- `exh/recs/bestversion/BestVersionCompareScreenModel.kt`
- `exh/recs/bestversion/BestVersionCompareScreen.kt`

**New files (quality signal DB):**
- `data/.../manga_source_quality_signal.sq`
- `data/.../migrations/53.sqm`
- `domain/.../taste/model/MangaSourceQualitySignal.kt`
- `domain/.../taste/repository/MangaSourceQualitySignalRepository.kt`
- `domain/.../taste/interactor/GetMangaSourceQualitySignals.kt`
- `domain/.../taste/interactor/UpsertMangaSourceQualitySignal.kt`
- `data/.../taste/MangaSourceQualitySignalRepositoryImpl.kt`

## Recommendation Bundle Export / Import

As of v0.7.5:

- Recommended manga can be exported as a JSON bundle and imported on another device.
- JSON schema identifier: `kmk.recommendation.bundle`, version 1. Validated by `RecommendationBundleValidator`.

**Export surfaces:**
- **For You tab â€” Export Top Picks**: share icon in action bar. Exports from `combinedDetailResult` (full scores and matched groups). Filename `kmk_top_picks.json`.
- **For You tab â€” Export source row**: long-press a source row header to export that source's results only. Filename `kmk_<source_name>.json`.
- **TopPicksScreen**: share icon in app bar. Exports with reduced metadata (no scores, no groups â€” only manga IDs are available at this screen). Filename `kmk_top_picks.json`.
- **Loved Manga screen**: share icon in app bar. Filename `kmk_loved_manga.json`.

**Import entry point:** Settings > Data storage > "Import recommendation bundle" â†’ file picker (application/json) â†’ validates â†’ opens preview screen.

**Preview screen (`RecommendationBundleImportScreen`):**
- Each item resolves to one of: `ReadyToAdd`, `AlreadyInLibrary`, `MissingSource`, `AmbiguousSource`, `SourceInstalledNeedsResolve`, `NeedsManualMatch`, `Unsupported` (Local Source, id=0), `Error`.
- Items with a `MissingSource` state (exactly one candidate extension found) show an "Install" button per missing extension.
- Items with an `AmbiguousSource` state (multiple candidate extensions matched the same package name) show a separate install card per candidate, labeled "Ambiguous match". The user must choose which to install. No silent first-pick.
- Install via `extensionManager.installExtension(ext)`. Items re-resolve on `InstallStep.Installed`.
- Default selection includes all `ReadyToAdd` and `SourceInstalledNeedsResolve` items.
- "Add selected (N)" calls `RecommendationBundleLibraryAdder.addToLibrary(skipDuplicates=true)` for each selected item.
- `SourceInstalledNeedsResolve` items are added via `networkToLocalManga(sManga.toDomainManga(resolvedSourceId))`.
- An add-summary `AlertDialog` shows Added / Already in library / Failed counts.

**Source resolution (3-step):**
1. Exact `sourceId` match.
2. `extensionPkgName` + `sourceName` + `sourceLang` match (handles sourceId changes between fork installs).
3. `extensionSignatureHash` + `sourceName` + `sourceLang` match (handles pkgName changes).

**Duplicate detection rule:** requires title + (author OR artist). Title alone is never flagged as a duplicate.

**New files:**
- `exh/recs/share/RecommendationBundle.kt` â€” pure JSON data models
- `exh/recs/share/RecommendationBundleValidator.kt` â€” pure validator (max 2 MB / 500 items / 200 sources)
- `exh/recs/share/RecommendationBundleSourceResolver.kt` â€” pure source resolution and duplicate detection
- `exh/recs/share/RecommendationBundleExporter.kt` â€” bundle builder and URI writer
- `exh/recs/share/RecommendationBundleImporter.kt` â€” URI reader, delegates to validator
- `exh/recs/share/RecommendationBundleLibraryAdder.kt` â€” adds to library with duplicate + category handling
- `exh/recs/share/RecommendationBundleImportScreenModel.kt` â€” resolution, install, and add flows
- `exh/recs/share/RecommendationBundleImportScreen.kt` â€” Voyager screen (primitive `uriString: String` constructor)

## Source Evaluation Hardening

As of v0.7.11 (corrected from the incorrectly-versioned "v0.8.0" build), updated in v0.7.16:

- **Pre-run consent dialog**: First use of Source Evaluation (via "Start evaluation", "Reassess updated", or "Continue next batch") requires the user to acknowledge a one-time consent dialog explaining install/probe/cleanup behavior and installer mode differences. Consent is stored in `sourceEvaluationConsentGiven` preference. Once acknowledged, the dialog does not appear again.
- **`PendingConsentAction` enum**: Tracks why the consent dialog was opened (`START_EVALUATION`, `REASSESS_UPDATED`, `CONTINUE_EVALUATION`, `VIEW_ONLY`). `confirmConsent()` dispatches to the correct path based on pending action. `VIEW_ONLY` closes the dialog without starting evaluation.
- **"View evaluation warning" button**: TextButton added to the diagnostics row (next to "Copy Diagnostics") so users can re-read the consent warning at any time without starting an evaluation. Calls `showConsentWarning()` which sets `pendingConsentAction = VIEW_ONLY`.
- **Cleanup outcome warnings in EvaluationSummaryCard**: After a completed/failed/cancelled batch, if any extensions required manual uninstall prompts (`promptRequiredCleanupCount > 0`) or cleanup failed (`cleanupFailedCount > 0`), warning messages are shown in error color, directing the user to Browse > Extensions.
- **`cleanupFailedCount` computed property**: Added to `SourceEvaluationQueueState` alongside existing `promptRequiredCleanupCount`.
- **All offline error messages use KMR string**: `source_evaluation_offline_error` replaces four hardcoded "No internet connection" strings in `startReassessUpdated()`, `startEvaluation()`, `confirmAndStartWithPrompts()`, and `continueEvaluation()`.
- **State-lost error uses KMR string**: `source_evaluation_state_lost_error` replaces hardcoded string in `SourceEvaluationJob.doWork()`.
- **Crash recovery message uses KMR string**: `source_evaluation_crash_recovery_marked_unsafe` replaces hardcoded message in `SourceEvaluationScreenModel.init`.
- **`SourceEvaluationConsentPolicy`**: Pure stateless helper. `isConsentRequired(consentGiven: Boolean): Boolean` â€” 3 unit tests.
- **`sourceEvaluationConsentGiven()` preference**: Added to `SourcePreferences`. Key: `"source_evaluation_consent_given"`, default `false`.
- **Process-death behavior**: If the app process is killed before `SourceEvaluationJob.doWork()` starts, `pendingCandidates` / `pendingOptions` (in-memory `@Volatile` fields) become null and the job writes a `source_evaluation_state_lost_error` failure. This is honest recovery via clear failure message â€” not durable resume. The user can restart from the screen or use batch continuation.
- **Installer-mode consent reset (v0.7.16)**: switching Source Evaluation to SHIZUKU or CURRENT mode resets `sourceEvaluationConsentGiven`, so the risk notice reappears when the cleanup/security profile changes.
- **Prompt-required cleanup action (v0.7.16)**: `SourceEvaluationRunner` records cleanup status per evaluated extension. When system-installed extensions cannot be silently cleaned up, `EvaluationSummaryCard` shows an "Uninstall N left-behind extension(s)" button wired to `cleanupPromptRequiredExtensions()`.
- **Process-death leftover cleanup warning (v0.7.16)**: startup recovery records a still-installed leftover package in `sourceEvaluationLeftoverPkg`; Source Evaluation then shows a persistent warning with an uninstall action.
- **Migration/proto/OCR backup guard tests (v0.7.16)**: `KmkMigrationTest`, expanded `TasteBackupRoundTripTest`, and `KmkOcrExclusionTest` cover KMK migrations, proto fields 620-625, quality-signal backup, cross-source-link sync payload coverage, and OCR backup exclusion.
- **Bundle import throttle (v0.7.16)**: `RecommendationBundleImportScreenModel.addSelected()` delays 200 ms between add attempts to reduce source/network hammering during large bundle imports.

## Recommendation Settings Reorganization

As of v0.7.12:

- `RecommendationsSettingsScreen` LazyColumn section order changed to: Daily recommendations (language selector at top), Ratings and known manga, Tags, Source priority, Same manga matching (moved up from below Sources To Try), Source status, Management (Sources To Try + cleanup), Experimental â€” Source Evaluation (renamed to clarify it's advanced).
- "Daily recommendations" is a new section header grouping the language selector at top.
- "Ratings and known manga" renamed from "Rated manga visibility".
- "Source status" renamed from the previous composite string.
- "Management" is a new section header grouping Sources To Try and cleanup actions.
- "Experimental â€” Source Evaluation" renamed from plain "Source Evaluation" to signal it's an advanced/experimental tool.

## Rec-Quality Probe Enrichment and Diagnostics

As of v0.7.13:

**Root cause fixed:** `SourceRecommendationFitProbe` previously called `source.getSearchManga()` and scored raw results immediately via `toDomainManga()`. Most extensions return `SManga` with `genre = null` on search results; `getMangaDetails` is required to populate tags. `PersonalRecommendationScorer` seeing null genre produced score = 0, making nearly all Strong Fit / Worth Trying sources appear as WEAK or NO_MATCHES even when they are good recommenders.

**Bounded enrichment (per plan):**
- After converting raw SManga to domain manga, the probe now calls `source.getMangaDetails(smanga)` for each candidate with no genre metadata.
- Enrichment cap: 5 candidates per plan, 10s timeout per detail call, sequential.
- `needsProbeEnrichment()` = `genre.isNullOrEmpty()` (not `!initialized` â€” genre presence is what the scorer actually needs).
- No DB writes from probe â€” avoids side-effects from transient probe runs.
- `SourceRecommendationFitProbeOutcome` gained `enrichedCandidateCount` and `weakMetadataCandidateCount` fields.

**Failure classifier (`SourceRecommendationFitFailureClassifier`):**
- Pure object with `SourceRecommendationProbeFailureKind` enum (14 values).
- `classify(errorMessage)`: case-insensitive substring matching on fixed-format error strings from the pre-probe resolution chain and the probe itself.
- `isInstallOrLoadIssue(kind)`: distinguishes infrastructure failures (extension not found, install failed, source not resolved) from search-level failures.

**Diagnostics summary (`SourceRecommendationQualityDiagnostics`):**
- `Summary(checkedCount, notCheckedCount, installLoadIssueCount, searchErrorCount, noResultsCount, weakCount, goodCount)` computed from promising evaluations + their fit records.
- Displayed as a compact label in the Recommendation Quality section when `checkedCount > 0`.

**Reason text persistence:**
- `buildRecQualityFitFromOutcome()` now populates `errorMessage` for all verdicts, not just ERROR:
  - ERROR: first 2 reasons joined, capped at 200 chars.
  - NO_MATCHES: "Search returned results but none had usable genre metadata" (if `weakMetadataCandidateCount > 0`) or "Search returned no results for taste profile tags".
  - WEAK: describes `weakMetadataCandidateCount` (after enrichment) and `blockedTagCandidateCount`.
- Reason text shown in subdued color for WEAK/NO_MATCHES, error color for ERROR.

**New files:**
- `app/.../exh/recs/evaluation/SourceRecommendationFitFailureClassifier.kt`
- `app/.../exh/recs/evaluation/SourceRecommendationQualityDiagnostics.kt`
- `app/src/test/.../SourceRecommendationFitFailureClassifierTest.kt` â€” 15 tests
- `app/src/test/.../SourceRecommendationQualityDiagnosticsTest.kt` â€” 10 tests

## Rec-Quality Error Transparency

As of v0.7.12:

- `SourceEvaluationScreenModel.buildRecQualityFitFromOutcome()`: when `label == ERROR` and `outcome.reasons` is non-empty, `errorMessage` is now populated from `outcome.reasons.take(2).joinToString("; ").take(200)` instead of always writing `null`.
- `SourceEvaluationScreen`: a second `Text` element below the quality label displays `recFit.errorMessage` in error color when verdict is `ERROR` and the message is non-blank. Uses the `source_evaluation_rec_quality_error_hint` KMR string.
- This surfaces both Scenario A errors (pre-probe: install failure, extension not found) and Scenario B errors (probe-level: `getSearchManga` exceptions) to the user.
- Probe-level error messages have the form `"Plan TOP_TAGS_FILTER: error â€” {exception.message.take(60)}"`. Pre-probe errors have descriptive stage-specific strings from the resolver chain.

## Tests

Documented tests as of KMK-Recs v0.7.16:

- `CombinedPicksAccumulatorTest`: 32 tests
- `RecommendationSourceRunStatusStoreTest`: 10 tests
- `AdjustStatusesForDedupeTest`: 9 tests
- `CrossExtensionMatchSelectionTest`: 10 tests
- `CrossExtensionMatchRouteModeTest`: 8 tests
- `LovedMangaDuplicateGrouperTest`: 38 tests
- `LovedMangaSourceFilterTest`: 9 tests (v0.7.3)
- `LovedMangaSortTest`: 8 tests (v0.7.15)
- `NonInstalledSourceSuggestionScorerTest`: 23 tests
- `RecommendationSourcePreferenceStoreTest`: 13 tests
- `ExplicitSourceClassifierTest`: 20 tests
- `SourceEvaluationInstallerPolicyTest`: 8 tests
- `SourceEvaluationCandidateFilterTest`: 25 tests
- `SourceEvaluationCleanupPolicyTest`: 6 tests
- `SourceEvaluationResultListTest`: 13 tests
- `SourceEvaluationCrashRecoveryPolicyTest`: 6 tests
- `SourceEvaluationUnsafeKeysTest`: 5 tests
- `SourceEvaluationUpdatePolicyTest`: 9 tests (v0.7.4)
- `SourceRecommendationFitEligibilityTest`: 10 tests (v0.7.4)
- `SourceRecommendationFitScorerTest`: 10 tests (v0.7.4)
- `SourceStatusDisplayOrderTest`: 8 tests (v0.6.20)
- `SeenRecommendationMangaStoreTest`: 11 tests (v0.6.20)
- `RecommendationBundleSerializationTest`: 8 tests (v0.7.5)
- `RecommendationBundleValidatorTest`: 11 tests (v0.7.5)
- `RecommendationBundleSourceResolverTest`: 11 tests (v0.7.5)
- `RecommendationBundleDuplicatePolicyTest`: 9 tests (v0.7.5)
- `SourceEvaluationDisplayFilterTest`: 10 tests (7 from v0.7.6 + 3 toggle tests from v0.7.7)
- `SourceRecommendationQualityQueueTest`: 8 tests (v0.7.7)
- `SourceRecommendationQualityExtensionResolverTest`: 10 tests (NEW v0.7.7 follow-up)
- `SameMangaMatchSettingsTest`: 18 tests (NEW v0.7.8)
- `BestVersionPageSamplerTest`: 11 tests (NEW v0.7.8)
- `BestVersionChapterMatcherTest`: 11 tests (NEW v0.7.8)
- `BestVersionSelectionPolicyTest`: 13 tests (NEW v0.7.9)
- `SourceEvaluationConsentPolicyTest`: 3 tests (NEW v0.7.11)
- `SourceRecommendationFitProbeTest`: 15 tests (8 from v0.7.6â€“v0.7.7; 4 new in v0.7.12; 3 new in v0.7.13: NO_MATCHES on empty search results, weak metadata tracking, enrichment turns no-genre result into scored candidate)
- `SourceRecommendationFitFailureClassifierTest`: 15 tests (NEW v0.7.13)
- `SourceRecommendationQualityDiagnosticsTest`: 10 tests (NEW v0.7.13)
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL (267 actionable tasks as of v0.7.15; 8 new LovedMangaSortTest tests added)
- `:app:assembleDebug`: BUILD SUCCESSFUL

Future changes should document exactly which tests were run.
