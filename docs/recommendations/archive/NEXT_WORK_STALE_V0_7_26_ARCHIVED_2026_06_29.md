# KMK Personal Recommendations Next Work

Date: 2026-06-28 (updated: 2026-06-28 — v0.7.25 and v0.7.26 complete)

Status: planning queue. Updated after v0.7.26 implementation. Do not implement any item without explicit user approval and a focused implementation plan.

## Confirmed Bugs Or Inconsistencies

None currently confirmed. See Discussed Future Features below.

## Phase 6/7 Deferred Items (Deferred from v0.7.12, partially implemented in v0.7.14)

See plan: `KMK_PHASE_6_7_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_PLAN.md`

Partially implemented in KMK-Recs v0.7.12 and v0.7.14. Remaining items:

- **Terminology cleanup (done in v0.7.14 and v0.7.15)**: Source fit badge labels, suggestion expand/collapse, preselect summary, evidence strength labels, last-evaluated labels, and verdict badge labels all extracted to KMR. No hardcoded English user-facing strings remain in touched UI. âœ“ Complete.
- **Loved Manga sort (done in v0.7.14 and v0.7.15)**: Sort options implemented (Most recent, Oldest first, Title Aâ€“Z, Source). 8 sort mode unit tests added in v0.7.15. Remaining: live updates while screen is open (load once at open time).
- **For You row ordering clarity (verified in v0.7.14)**: `SourceStatusDisplayOrder` confirmed correct with 8 existing tests. âœ“ Complete.
- **Same-manga matching settings verification (verified in v0.7.14)**: Both bounded workflows confirmed correct. âœ“ Complete.
- **Recommendation cache staleness hint (done in v0.7.14)**: âœ“ Complete.
- **Known manga / Seen handling (verified in v0.7.15)**: Seen filtering confirmed correct â€” `seenRecommendationMangaKeys` always filtered from For You; "Seen other versions" available after marking current manga seen (v0.7.1 fix). SeenRecommendationMangaStoreTest: 11 tests pass. âœ“ Complete.
- **Cross-extension link group management UI**: Deferred. Route safety confirmed OK. Management UI for viewing/deleting/naming link groups deferred to a future pass.
- **Best Version cancel safety (verified in v0.7.15)**: Fixed in v0.7.9. Confirmed: Cancel sets `selectedBestKey = null` + `isMigrating = false` via `dismissMigrationDialog()`. âœ“ Complete.
- **Best Version fullscreen preview (verified in v0.7.15)**: Implemented in v0.7.9. Confirmed: pinch-to-zoom (max 5Ã—) + pan via `detectTransformGestures` in `FullscreenPagePreviewDialog`. âœ“ Complete.
- **Source quality signal (resolved in v0.7.15)**: No `confirmed_quality` column needed â€” every insert to `manga_source_quality_signal` is user-confirmed by the Best Version workflow. Doc cleanup only. âœ“ Complete.

## Source Evaluation Hardening (Phase 5) â€” Deferred Items

Implemented in KMK-Recs v0.7.11 (initial strings/cleanup in Phase 5, corrected consent flow in v0.7.11). See `KMK_RECS_SOURCE_EVALUATION_COMMUNITY_HARDENING_IMPLEMENTATION.md` and `KMK_RECS_V0_7_11_SOURCE_EVALUATION_HARDENING_CORRECTION_IMPLEMENTATION.md`.

Deferred from v0.7.11:

- **Per-source error category standardization**: Install timeout, source not found, network unavailable, and other failure categories are surfaced as error messages but not yet classified/displayed by category in the UI. A future pass could add labeled error categories to past result rows.
- **Network-loss retry/refresh UI**: **MITIGATED v0.7.18** — `SourceEvaluationRunner` now checks `context.isOnline()` at the start of each candidate iteration and stops cleanly with `ConnectivityLost` status. Summary card surfaces the message "Connection lost mid-run. Completed extensions are saved. Retry when online." Re-run is manual (user presses start again). A future pass could add a one-tap Retry button that resumes from where the batch stopped.
- **Evidence strength strings full i18n hookup**: Done in v0.7.15. `evidenceStrengthLabel()` replaced with `EvidenceStrength` enum + `evidenceStrength()` classifier; `lastEvaluatedLabel()` replaced with `lastEvaluatedDaysAgo()`. Composable maps to KMR strings. âœ“ Complete.
- **Quarantine collapsed count chip expansion**: The quarantine row is in `SafetyDiagnosticsRow` and is not dominant, but it is always expanded when there is content. A future pass could add a collapsed/expanded toggle.

## Discussed Future Features

### Master deferred feature roadmap

See plan: `KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md`

This master plan consolidates the remaining deferred features into implementation phases. Use it as the primary roadmap before creating any new feature-specific prompt for Claude Code.

### Staged settings and matching improvements

See plan: `KMK_RECS_STAGED_SETTINGS_AND_MATCHING_IMPROVEMENTS_PLAN.md`

Planning conclusion: items were staged. Source/settings work shipped as `KMK-Recs v0.6.3` (bulk install, semantics cleanup, ordering verification). Loved Manga view shipped as `KMK-Recs v0.7.0`.

**Alternate-title cross-extension matching is fully implemented** (previously listed here as deferred as `KMK-Recs v0.5.2`). `CrossExtensionMatchQueryPlanner.buildQueries()` produces up to 3 deduplicated queries â€” title, ogTitle (if different), and a bracket-stripped variant â€” and multi-query search with per-source result merging runs in `CrossExtensionMatchScreenModel.init`. No remaining work.

No remaining deferred items in this area.

### Loved Manga view

Implemented in KMK-Recs v0.7.0. See `KMK_RECS_V0_7_0_LOVED_MANGA_VIEW_IMPLEMENTATION.md`.

Entry point: heart icon button in the For You tab action bar.

Deferred from v0.7.0:
- Sorting options beyond "most recently loved" (by title, by source, oldest first).
- Live updates while the screen is open (currently loads once at open time).
- Backup/restore for the grouping toggle state.

### Favorite mode for cross-extension matching

**Implemented in KMK-Recs v0.7.0 (Phase 3).** See `KMK_RECS_V0_7_0_LOVED_MANGA_VIEW_IMPLEMENTATION.md`.

`CrossExtensionMatchMode.Favorite` is fully implemented in `CrossExtensionMatchScreenModel`. The `applyRating()` Favorite branch adds selected manga to the library using `updateManga` + `setMangaCategories`, then writes a cross-source link group. The route mode string `"favorite"` is registered in `CrossExtensionMatchRouteMode`. No further extraction from `BulkFavoriteScreenModel` is needed.

### Cross-source link groups

Implemented in KMK-Recs v0.7.0 (Phase 4) and audited in v0.7.2. Link groups are fully stored (`manga_cross_source_link` table, migration 50), backed up at proto 624, restored, and sync-merged. `CrossExtensionMatchScreenModel` writes a new link group after every confirmed match action (rating/seen/favorite). `LovedMangaScreenModel` now loads link groups and passes them to the grouper as the highest-priority grouping signal.

Remaining future work:
- Allow users to view, inspect, or delete individual link groups from a management UI.
- Use link groups to skip re-search in future rating/favorite actions (auto-suggest already-linked versions).
- See plan: `KMK_RECS_V0_5_0_CROSS_EXTENSION_MATCHING_PLAN.md` Phase 4 remainder
- See research: `CROSS_EXTENSION_IDENTITY_FEASIBILITY_RESEARCH.md`

### Source status timestamps

Recommendation Settings shows per-source statuses from the last For You run, but a user may not know how old a status is.

Future polish: show a compact "Last checked" time when useful.

### AniList / tracker known-list cache

**PERMANENTLY DEFERRED.** Known-manga filter uses only local DB data. AniList/tracker cache is not needed.

### Minimum chapter count filter

**IMPLEMENTED in KMK-Recs v0.7.26.** New "Minimum chapter count" preference (Off / 5 / 10 / 20 / 50) in Recommendation Settings → Ratings & Known Manga. Post-fetch filter applied after seen-key exclusion. Manga with no locally-known chapters are not filtered (fail-open). Cache fingerprint includes `minChapterCount`.

### For You offline / connection loss handling

**IMPLEMENTED in KMK-Recs v0.7.25.** On load or manual refresh, if `context.isOnline()` is false, the screen immediately shows a "No internet connection" `EmptyScreen` with a Retry action. Uses `Injekt.get<Application>()` for context injection in `BrowsePersonalRecommendationsScreenModel`.

### Local Source support

Local Source is currently excluded from For You. Adding Local Source support would require deciding what Local Source should mean in a recommendation feed:

- locally stored manga only,
- library/history manga only,
- downloaded manga only,
- or a synthetic row based on cached metadata.

This needs product clarification before implementation.

### Query-time blocked tag exclusion

**IMPLEMENTED in KMK-Recs v0.7.0 (code) / v0.7.20 (tests + documentation). See `KMK_RECS_V0_7_20_PHASE7_BLOCKED_TAGS_TESTS_IMPLEMENTATION.md`.**

`GenreFilterMapper.buildSearch()` takes `blockedGenres` and sets `STATE_EXCLUDE` on any matching `Filter.TriState` entry in a `Filter.Group` that is currently `STATE_IGNORE`. Only TriState supports exclusion — CheckBox, Select, AutoComplete are skipped. Post-fetch `PersonalRecommendationScorer` blocking always runs as fallback. 7 unit tests in `GenreFilterMapperTest` cover: exclude TriState, no INCLUDE downgrade, no crash on unknown filter, CheckBox skipped, forceTextOnly bypasses exclusion, built-in synonym match, empty filterList safety.

### Pull-to-refresh

The refresh button exists. Pull-to-refresh is deferred polish.

### Source quality learning

**IMPLEMENTED in KMK-Recs v0.7.19 (Phase 6).** See `KMK_RECS_V0_7_19_INSTALLED_SOURCE_FIT_IMPLEMENTATION.md`.

Rolling per-source fit stats (run count, shown/no-match/filtered/error/hidden-by-duplicate counts, total visible candidates, timestamps) are accumulated in a preference after each For You run. Fit labels (Great fit, Good fit, Mixed, No matches, Often filtered, Often errors) shown as badges in Recommendation Settings after ≥3 runs. "Suggest priority order based on fit" button appears after ≥3 sources have ≥3 run history.

Remaining future work:
- Top Picks contribution count: currently not tracked at per-source granularity (accumulator merges across sources). Could track which sourceIds contributed to final Top Picks.
- Liked/Loved/Disliked candidate counts: would require a later-rating cross-join against the manga that appeared in each source row. Not tracked at run time.
- Fit stat decay over time: currently pure accumulation with no recency weighting. Could add exponential decay in a future pass.

### Non-installed extension discovery

Goal: suggest available but not installed sources that may be worth trying for recommendations.

See archived plan: `archive/plans/NON_INSTALLED_EXTENSION_DISCOVERY_PLAN.md`
See archived correction plan: `archive/plans/NON_INSTALLED_EXTENSION_DISCOVERY_HARDENING_PLAN.md`

Planning conclusion: this is feasible only as low-confidence metadata-based discovery before install. The app can inspect available extension/source metadata such as source name, language, base URL, repo, package, and NSFW flag, but it cannot search or evaluate live catalog quality until the extension is installed. Suggestions should be labeled "Sources To Try" or "Potential fit," should never auto-install, and should hand off to real installed-source evaluation after install.

v0.6.0 was too broad (language/repo/base URL/keywords treated as evidence). v0.6.1 hardened the scorer: only `SimilarToInstalledSource` qualifies a suggestion. Sources with only language/repo/keyword signals are excluded. Empty state shown when no qualified suggestions exist.

### Source like/dislike preferences

Implemented in v0.6.2. See archived implementation report: `archive/implementations/SOURCE_PREFERENCE_LIKE_DISLIKE_IMPLEMENTATION.md`.

Remaining deferred: "Manage hidden source suggestions" UI for users to undo a non-installed source dislike. Currently disliked non-installed sources have no reset path from the UI (they can only be reset by clearing app preferences or a future management screen).


### Source ordering, reassessment, diagnostics, and docs cleanup

Implemented in KMK-Recs v0.6.20. See `KMK_RECS_V0_6_20_SOURCE_ORDER_REASSESSMENT_AND_DOC_CLEANUP_IMPLEMENTATION.md`.

Implemented scope:
- For You/source-status display ordering in Recommendation Settings (Has Results / No Results / Disliked groups).
- Manual Source Evaluation reassessment button; prompt appears after 100 new manga ratings since baseline; baseline updated automatically on evaluation completion.
- Evidence strength labels (Strong / Moderate / Weak / Low confidence) and last-evaluated age shown in Source Evaluation past results.
- Seen/Already read manga marker on manga detail page; seen entries always filtered from For You; cache invalidated on seen-set changes.
- "Seen other versions" cross-extension flow via `CrossExtensionMatchMode.MarkSeen`.
- Source management section (Reset disliked sources / Reset reassessment baseline / Clear seen manga) with confirmation dialogs.

Deferred from v0.6.20:
- **Hidden-source state**: A "hide temporarily, don't dislike" concept distinct from dislike. Requires new key format and UI. Deferred to a future pass.
- **Backup/restore for seen manga**: Seen state is stored in preferences only. SQLDelight + proto backup deferred.
- **Evidence strength strings i18n**: The `source_evaluation_evidence_*` keys are in `strings.xml` but the labels in `SourceEvaluationScreen.kt` are currently hardcoded English. Full string resource hookup deferred.

### Source Evaluation UX and background execution

Implemented in KMK-Recs v0.6.19. See `KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_IMPLEMENTATION.md`.

Follow-up requirements also implemented: notification deep link, unassessed remaining count wording, offline start guard, installed-exclusion tests. See `KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_FOLLOWUP_IMPLEMENTATION.md`.

Deferred from follow-up:
- Broader repo failure surfacing (ExtensionApi.getExtensions() still returns emptyList() per repo on error; no UI)
- Mid-run connectivity loss handling beyond existing per-extension timeout/error

### Source Evaluation update reassessment and recommendation fit

Implemented in KMK-Recs v0.7.4. See `KMK_RECS_V0_7_4_SOURCE_EVALUATION_UPDATE_REASSESSMENT_AND_RECOMMENDATION_FIT_IMPLEMENTATION.md`.

Implemented scope:
- Extension version metadata (`extensionVersionName`, `extensionVersionCode`, `extensionApkName`) stored in `source_evaluation` table via migration 51.
- `SourceEvaluationUpdatePolicy` pure helper: detects UPDATED / NOT_UPDATED / UPDATE_UNKNOWN / NEVER_EVALUATED per extension key.
- `onlyUpdatedEvaluated` option in `SourceEvaluationCandidateFilter.applyOptions()`: restricts a run to extensions that have been updated since their last evaluation.
- "N evaluated extensions have updates" notice + "Reassess updated extensions" button in `SourceEvaluationScreen`.
- `SourceRecommendationFitEligibility` pure helper: gates STRONG_FIT/WORTH_TRYING with minimum sample count.
- `SourceRecommendationFitScorer` pure helper: computes quality score from probe outcome signals.

Deferred from v0.7.4:
- **Bounded rec-fit probe execution**: Implemented in v0.7.6. `SourceRecommendationFitProbe` now runs after each Source Evaluation batch for STRONG_FIT and WORTH_TRYING sources.
- **Storing rec-fit score in source_evaluation**: Resolved via a separate `source_recommendation_fit` table rather than adding a column to `source_evaluation`. Domain model is `SourceRecommendationFit`.
- **Evidence strings i18n**: Done in v0.7.15. âœ“ Complete.

### Source Evaluation installed filter, batch continuation, and recommendation-quality probe

Implemented in KMK-Recs v0.7.6.

Implemented scope:
- Batch continuation: "Continue next batch (N remaining)" button after each completed batch. Progress stored per filter fingerprint; expires after 7 days.
- `SourceEvaluationDisplayFilter` pure helper: hides installed extensions from past results by default; "Show installed" chip reveals them; "Hidden installed: N" count shown.
- Second-stage recommendation-quality probe: `SourceRecommendationFitProbe` runs for STRONG_FIT/WORTH_TRYING sources after each batch. Results stored in `source_recommendation_fit` table via `UpsertSourceRecommendationFit`. Past result rows show a third line with the verdict.
- `SourceRecommendationFit` domain model, `RecommendationQualityVerdict` enum, `GetSourceRecommendationFit`, `UpsertSourceRecommendationFit` interactors added.

Deferred from v0.7.6:
- **Show/Hide installed toggle reversibility**: Fixed in v0.7.7.
- **On-demand recommendation-quality evaluation from screen**: Added in v0.7.7.
- **"Not checked" label for promising rows**: Added in v0.7.7.

### Source Evaluation follow-up: toggle fix, rec-quality workflow, "Not checked" label

Implemented in KMK-Recs v0.7.7. See `KMK_RECS_V0_7_7_SOURCE_EVALUATION_FOLLOWUP_AND_BEST_VERSION_IMPLEMENTATION.md`.

Implemented scope:
- Show/Hide installed toggle always reversible (condition fix in screen).
- "Recommendation Quality" section added to Source Evaluation screen for promising sources.
- `evaluateRecommendationQualityForPromising(reCheckAll: Boolean)` action in screen model.
- `SourceRecommendationQualityQueue` pure helper partitions evaluations into missing/checked/ineligible.
- "Recommendations: Not checked" label shown for promising rows without a rec-quality result.
- `remember` call for queue moved outside `LazyColumn` to fix composable context error.

Deferred from v0.7.7 (original):
- **Re-run rec-quality probe after taste profile changes**: There is no automatic trigger when the user's taste profile updates. The "Re-check all" button requires manual action.
- **Evidence strings i18n**: Done in v0.7.15. âœ“ Complete.

Deferred from v0.7.7 follow-up fix (shipped in v0.7.8 APK):
- **Cleanup for PromptRequired extensions**: Partially mitigated in v0.7.16 for normal Source Evaluation batch summaries and process-death leftovers via in-screen uninstall actions. On-demand rec-quality probe cleanup should still be rechecked separately because it has a different inline path.

### Best version / chapter quality compare

Implemented in KMK-Recs v0.7.8. See `KMK_RECS_V0_7_8_BEST_VERSION_CHAPTER_QUALITY_IMPLEMENTATION.md`.

Implemented scope:
- "Find best version" entry point in manga detail rating dropdown.
- Bounded same-manga candidate search with configurable cap and preselect behavior.
- `BestVersionCompareScreen`: search â†’ confirm candidates â†’ load chapters â†’ select chapter â†’ preview sampled pages â†’ migrate/copy.
- 4 new preferences for same-manga matching and preview settings.
- "Same manga matching" section in Recommendation Settings.
- SQLDelight `manga_source_quality_signal` table (migration 53) for local quality signal persistence.
- Full domain/data layer for quality signals.
- Pure helpers: `BestVersionPageSampler`, `BestVersionChapterMatcher`, `SameMangaCandidateSearcher`, `SameMangaMatchSettings`.
- `CrossExtensionMatchScreenModel` reads cap and preselect from preferences.
- Tests: 18 + 11 + 11 = 40 new tests, all PASSED.

Deferred from v0.7.8:
- **Quality signal display UI**: `manga_source_quality_signal` records are written but not browsable from any UI. A future pass could show past quality decisions.
- **Automatic re-search trigger**: No automatic trigger to re-run when a higher-quality version is suspected after source reassessment.
- **Full `SameMangaCandidateSearcher` adoption**: `CrossExtensionMatchScreenModel` was updated with inline preference reads rather than delegating its `search()` path to the new class.
- **Quality signal backup/restore**: Implemented in v0.7.16 via `BackupMangaSourceQualitySignal` proto 625. Complete.
- **Network image loading fallback**: No workaround if a source requires special headers for image URLs in preview loading.

### Rec-Quality Probe Enrichment (v0.7.13)

Implemented in KMK-Recs v0.7.13. See `KMK_RECS_V0_7_13_SOURCE_EVALUATION_RECOMMENDATION_QUALITY_FUNCTIONAL_FIX_IMPLEMENTATION.md`.

Implemented scope:
- Bounded metadata enrichment in `SourceRecommendationFitProbe`: calls `getMangaDetails` for up to 5 candidates per plan with no genre, 10s timeout per call, sequential.
- `SourceRecommendationFitProbeOutcome` gained `enrichedCandidateCount` and `weakMetadataCandidateCount`.
- `SourceRecommendationFitFailureClassifier`: pure classifier with 14-value enum, maps error strings to stable categories.
- `SourceRecommendationQualityDiagnostics`: compact summary (checked/not-checked/install-load/search-error/no-results/weak/good counts).
- `buildRecQualityFitFromOutcome()`: populates `errorMessage` for WEAK and NO_MATCHES verdicts (not just ERROR).
- UI: diagnostics label in Recommendation Quality section; WEAK/NO_MATCHES reason text shown in subdued color.
- 25 new tests across 2 new test files + 3 new tests in probe test file.

Deferred from v0.7.13:
- **Enrichment cap increase**: Cap is 5 per plan. If a source consistently has 5+ genre-less results, further enrichment is cut off. A future pass could increase the cap or make it configurable.
- **Enrichment timeout resilience**: Per-call 10s timeout means slow sources may partially enrich. No retry; failure silently falls back to original domain manga.
- **Persistent enrichment cache**: Enriched results are discarded after the probe; they are not written to the local DB. A future pass could share enriched data with For You or the local manga cache.
- **Diagnostics string localization**: `source_evaluation_rec_quality_diagnostics` is English-only in the base locale. Full i18n deferred.

Deferred from v0.7.9:
- **Fullscreen preview rotation/state restoration**: `fullscreenPage` is local UI state and is not saved across process death or configuration changes. Reopen fullscreen manually after rotation.
- **Thumbnail ContentScale.Fit option**: Thumbnails use `ContentScale.Crop` for compact comparison. Some sources may crop important details. Could be made configurable in a future pass.
- **Tap-to-close in fullscreen when not zoomed**: Single-tap-to-close was deferred in favor of stability (pinch + tap gestures can conflict). Users close via the close icon or back.

### Bundle import and string audit (v0.7.17)

Implemented in KMK-Recs v0.7.17. See `KMK_RECS_V0_7_17_BUNDLE_SHIZUKU_STRING_AUDIT_IMPLEMENTATION.md`.

Implemented scope:
- **R-010**: `AvailableExtensionResolution` sealed interface (Unambiguous/Ambiguous/NotFound) added to `RecommendationBundleSourceResolver`. `resolveAvailableExtension()` uses 2-step matching: pkgName+sigHash first, then pkgName-only. Both steps use filter (not firstOrNull), so multiple matches surface as Ambiguous. `AmbiguousSource(candidates)` added to `RecommendationImportItemState`. Import screen shows a separate install card per ambiguous candidate.
- **R-005/R-019**: `PolicyResult.message: String?` replaced with `messageKey: InstallerPolicyMessage?` (typed enum, 8 cases). Screen resolves to KMR strings via `toLocalString()` composable extension. `State.LoadError(message: String)` replaced with `State.LoadError(error: LoadErrorKey)` (sealed interface, 6 variants). Screen resolves to KMR strings via `LoadErrorKey.toLocalString()`.
- 15 new KMR strings (2 ambiguous source, 8 installer policy, 7 bundle load error).
- 6 new tests for `resolveAvailableExtension()`.

Deferred from v0.7.17:
- **R-019 screenErrorMessage**: `SourceEvaluationScreenModel.screenErrorMessage = "Failed to load candidates: ${e.message}"` is still a hardcoded English string (contains exception detail). Converting it would require a `ScreenErrorKey` sealed interface pattern similar to `LoadErrorKey`. Deferred.
- **R-022**: Architecture cleanup deferred.
- **Shizuku prompt-flow UX (R-005 partial)**: The prompt warning dialog text (`source_evaluation_prompt_warning_message`) already uses a KMR string but could be made clearer. Deferred.


