package exh.recs

// KMK -->
object KmkRecsReleaseNotes {
    const val VERSION_CODE = 726
    const val VERSION_NAME = "KMK-Recs v0.7.26"

    val MARKDOWN = """
        ## KMK-Recs v0.7.26

        - Recommendation Settings: new "Minimum chapter count" filter in the Ratings & Known Manga section. When set above 0, For You hides results whose locally-known chapter count is below the threshold (options: Off, 5, 10, 20, 50). Manga with no locally-known chapters (untracked) are never filtered so you don't miss newly-released series. Changing the setting invalidates the For You cache so results refresh on the next load.

        ## KMK-Recs v0.7.25

        - For You now detects when the device has no internet connection before starting recommendation queries. Instead of silently failing or showing a cascade of per-source errors, the screen shows a clear "No internet connection" message with a Retry button. Tapping Retry re-checks connectivity and resumes normally if online.

        ## KMK-Recs v0.7.20

        - For You blocked-tag filtering is now applied at query time on sources that expose TriState genre filters, not only post-fetch. When a source's filter list contains a TriState entry matching a blocked tag (by name or built-in synonym), it is set to exclude before the query runs. Post-fetch filtering always remains active as the fallback, so no recommendations slip through regardless of filter support.
        - Internal: added 7 tests for the blocked-genre query-time exclusion logic in GenreFilterMapperTest (exclude TriState, no downgrade of INCLUDE entries, no crash on missing filter, no mutation of CheckBox, forceTextOnly bypasses exclusion, built-in synonym matches blocked label, empty filterList safety).
        - Local Source: confirmed keep-excluded. Local Source (id 0) is intentionally excluded from For You source searches. A separate synthetic "Already Known" row based only on local DB metadata remains a future option if there is a clear use case.

        ## KMK-Recs v0.7.19

        - Recommendation Settings: the source fit badge in the Source Priority list now reflects rolling history across For You runs rather than just the most recent run. After at least 3 runs, each source receives a label (Great fit, Good fit, Mixed, No matches, Often filtered, Often errors) based on its reliability, shown-rate, and average visible candidates over time.
        - Recommendation Settings: a "Suggest priority order based on fit" button appears below the source list after at least 3 sources have 3+ run history. Tapping it moves sources with the best rolling fit to the top while leaving no-data sources at the bottom. You can reorder further after applying.
        - Internal: rolling source fit stats (run count, shown/no-match/filtered/error/hidden-by-duplicate counts, total visible candidates, timestamps) are now persisted in a preference after each For You run. The accumulator skips Disabled/OutsideAttemptLimit statuses so only actually-searched sources are counted.

        ## KMK-Recs v0.7.18

        - Source Evaluation: if the device loses connectivity mid-batch, the runner now stops cleanly with a "Connection lost mid-run" status rather than hanging or failing with a generic error. Extensions already completed are saved; the options section re-appears so you can retry when back online.
        - Source Evaluation: extension repositories unavailable at startup (empty available-extension list after candidates load) now surface a non-blocking "Extension list unavailable" info card so the cause of zero candidates is clear.
        - Source Evaluation: the Management section now shows "Clear N dismissed suggestion(s)" and "Clear N disliked suggestion source(s)" buttons when hidden suggestions exist, allowing recovery without navigating to Settings.
        - Source Evaluation: blocked packages that have a newer available version now show a "Newer version available — remove block to test" hint in the Blocked Packages dialog so you can decide whether to re-test.
        - Localization: all remaining hardcoded English strings in the Source Evaluation screen error display have been moved to typed ScreenErrorKey variants backed by KMR string resources (candidate load failure, offline error, crash recovery message).

        ## KMK-Recs v0.7.17

        - Bundle import: when multiple available extensions could match a missing source (same package name, different signing keys), the import preview now shows an "Ambiguous source" badge and lists each candidate with its own install button so you can choose which one to install. Previously the first match was silently picked.
        - Source Evaluation: installer mode descriptions (Private, Shizuku, Current) are now localization strings. The copy has been clarified: Private describes silent internal cleanup; Shizuku explains system-package install and Android uninstall prompts; Current explains confirmation dialogs per install/uninstall.
        - Localization: all remaining hardcoded user-visible English strings in the bundle import and source evaluation installer flows have been moved to KMR string resources.
        - Internal: added 6 tests for the new bundle source ambiguity resolver (resolveAvailableExtension: NotFound when no pkgName, NotFound when no match, Unambiguous on exact pkgName+sigHash, Ambiguous on multiple pkgName+sigHash, pkgName-only fallback when sigHash unmatched, Ambiguous on pkgName-only with multiple extensions).

        ## KMK-Recs v0.7.16

        - Backup: Best Version quality signals (your "Best Version" picks across sources) are now included in backups and sync. Picks restore by origin+selected source identity; duplicates are skipped.
        - Backup/Sync: Fixed cross-source manga link groups missing from device sync payload — they were backed up correctly but not synced between devices.
        - Source Evaluation: switching to Shizuku or Current installer mode now resets the one-time consent so the risk notice re-surfaces when the risk profile changes.
        - Source Evaluation: after a Shizuku or Current-mode run, extensions that could not be cleaned up silently now show an "Uninstall N left-behind extension(s)" button in the summary card so you can trigger the system prompts in one tap without going to Browse > Extensions. Previously the cleanup count was shown but the status was never accurately recorded — both bugs are now fixed.
        - Source Evaluation: if the app was killed mid-evaluation (process death) and a temporary extension was left installed, reopening Source Evaluation now shows a persistent warning banner with an "Uninstall leftover extension" button so you can clean it up without going to Browse > Extensions.
        - Internal: added migration round-trip tests (KmkMigrationTest — 9 execution tests against real in-memory SQLite, 4 structural tests), proto collision tests (TasteBackupRoundTripTest — 5 new tests covering proto fields 620–625), and OCR backup exclusion tests (KmkOcrExclusionTest — 3 structural tests confirming OCR text is never included in backup or sync payloads). Bundle import now throttles metadata fetches at 200ms per item during bulk adds.

        ## KMK-Recs v0.7.15

        - Source Evaluation: evidence strength labels (Strong evidence, Moderate evidence, Weak evidence, Low confidence) and verdict badges (Strong Fit, Worth Trying, Neutral, Weak, Poor Search, Explicit, Ecchi, Rejected, Error, Review) are now localization strings instead of hardcoded English.
        - Source Evaluation: last-evaluated age label (Last evaluated today / Last evaluated N days ago) is now a localization string.
        - Loved Manga: when "Group clear duplicates" is on but no clear duplicates are found, a compact note ("No clear duplicates found.") is shown so it is clear the control is working.

        ## KMK-Recs v0.7.14

        - Recommendation Settings: removed the redundant "Daily recommendations" header above the language selector (the language selector IS the daily rec control).
        - Recommendation Settings: source fit badge labels ("Great fit", "Good fit", "Low fit", etc.) and suggestion expand/collapse buttons are now using localization strings instead of hardcoded English.
        - Recommendation Settings: added a compact hint below the hide-known-manga toggle: "Refresh For You after changing ratings, tags, source preferences, or known-manga settings."
        - Same manga matching: preselect setting summary now notes "Turn off if a source returns too many wrong matches."
        - Source status ordering in Recommendation Settings: verified correct — sources with results, then no results, then disliked. Tests already covered this.
        - Same-manga matching settings verified: Love/Like/Dislike/Seen/Favorite/Best-version searches all read the cap and preselect preferences. Normal global search is not capped.
        - Loved Manga: added sort options (Most recent, Oldest first, Title A–Z, Source). Sort applies before grouping.

        ## KMK-Recs v0.7.13

        - Recommendation Quality checks now enrich raw search results with manga details before scoring. Many extensions return search results without genre/tag metadata; the check now fetches details for up to 5 candidates per query plan so the scorer can see actual tags. This matches how For You already works and prevents good sources from being falsely marked as weak or no-matches.
        - Recommendation Quality checks now show a compact diagnostics summary after running: how many sources were checked, how many had install/load issues, how many had search errors, how many had no results, how many were weak, and how many were good recommenders.
        - Recommendation Quality rows now show a subdued reason for No matches and Weak outcomes (not just Error). For example: "Search returned no results for taste profile tags" or "3 result(s) had no genre even after enrichment".
        - Source Evaluation failure categories are now classified: install/load issues and search errors are counted separately in the diagnostics summary.

        ## KMK-Recs v0.7.12

        - Recommendation Quality error results now show a brief reason in red below the quality label. Previously every failure showed only "Error" with no detail. Now the reason is visible — for example "Install failed or timed out", "Extension not found in available sources", or the specific search error from the probe.
        - Recommendation Settings reorganized per Phase 6/7 plan: Daily recommendations at top (language selector), then Ratings and known manga, Tags, Source priority, Same manga matching (moved up from below Sources To Try), Source status, Management (Sources To Try and cleanup actions), and Experimental — Source Evaluation at the bottom.
        - Source Evaluation section in Recommendation Settings is now labeled "Experimental — Source Evaluation" to clarify it is an advanced tool separate from daily recommendation controls.
        - Fixed: stale v0.8.0 version markers in source comments corrected to v0.7.11.

        ## KMK-Recs v0.7.11

        - Source Evaluation now requires a one-time acknowledgment before the first run. A warning dialog explains that evaluation temporarily installs extension packages, runs network probes, and then attempts to remove them. After acknowledging, the dialog does not appear again.
        - "View evaluation warning" button (next to Copy Diagnostics in Source Evaluation) lets you re-read the warning at any time without starting an evaluation. Tapping "I understand, continue" from this path never starts evaluation.
        - After an evaluation batch completes, the summary now shows a warning in red if any extensions could not be cleaned up automatically and need a manual uninstall from Browse > Extensions.
        - "Reassess updated extensions" and "Continue next batch" now show the same first-run consent dialog before starting, so the warning is always seen before any install/probe work begins.
        - Fixed: confirming the warning from "View evaluation warning" no longer accidentally starts evaluation.

        ## KMK-Recs v0.7.10

        - Fixed Recommendation Quality checks for Strong Fit and Worth Trying sources. Previously, running "Evaluate recommendations" when no evaluation batch had been run in the current session caused every non-installed promising source to immediately become an Error with "Extension not found in available sources". Now the full available extension list is loaded directly from the extension manager instead of relying on the screen-local candidate pool.
        - Non-installed promising sources are now resolved from the complete available extension repository, temporarily installed, probed, and cleaned up as intended.
        - Installed extension matching is now more robust: the app tries exact sig+pkg, then pkg-only, then sig+name, then name+lang before reporting an error, so extensions that had a signing key change are still found.
        - Source lookup within an installed extension is now more robust: the app tries exact source id, then name+lang, then name, then normalized name+lang, then normalized name before reporting an error.
        - Error messages now identify the specific stage that failed (available list unavailable / extension not found / extension match ambiguous / install failed / source not found after install / etc.) instead of collapsing all failures into a generic error.

        ## KMK-Recs v0.7.9

        - Fixed the Cancel button in the Best Version migration confirmation dialog. Pressing Cancel now correctly closes the dialog and returns to the comparison screen. Previously, Cancel set a fake sentinel key that kept the dialog stuck open.
        - Sampled page thumbnails in the Best Version preview are now tappable. Tap any thumbnail to open it fullscreen.
        - Fullscreen page preview supports pinch-to-zoom and pan. Close with the close button or back.
        - Closing fullscreen preview returns to the comparison screen with all previews, candidates, and selected chapter intact.
        - Added defensive state handling: if a selected Best Version key no longer resolves to a real candidate, the dialog is dismissed automatically rather than rendering an empty or broken state.

        ## KMK-Recs v0.7.8

        - Recommendation Quality checks now work for promising sources (Strong Fit, Worth Trying) that are not currently installed. The app temporarily loads the extension to run the check, then cleans it up silently.
        - Errors in the Recommendation Quality section now reflect the real failure reason — install failure, source not found, probe error — instead of marking every non-installed source as "Error".
        - Source Evaluation installed-extension display now updates immediately after extensions are installed or uninstalled, without needing to reopen the screen.
        - Recommendation Settings source priority list now updates immediately after extensions are installed or uninstalled, without needing to restart the app.
        - Added Find best version in the rating menu on manga detail pages. Use it to search for the same manga across installed sources, visually compare sampled chapter pages, and migrate or copy to the better version.
        - Choose a chapter and preview a sample of pages side by side from each candidate source before deciding.
        - Migrate or copy to the selected better version — chapter history, categories, tracking, and downloads are preserved using Komikku's existing migration behavior.
        - Configure how many results to show per source for same-manga searches (1, 2, 5, or 10) in Recommendation Settings > Same manga matching. This applies to Love/Like/Dislike/Seen/Favorite/Best-version searches. Normal global search is unaffected.
        - Configure whether same-manga results start selected or unselected by default.
        - Configure preview sample size (2, 5, or 10 pages) and whether early pages are skipped in the preview.

        ## KMK-Recs v0.7.7

        - Installed evaluation rows can now be shown or hidden without reopening Source Evaluation. When installed rows are hidden, the toggle shows "Show installed". When installed rows are visible, the toggle shows "Hide installed". Tapping it again immediately hides installed rows.
        - Promising sources (Strong Fit and Worth Trying) now have a visible Recommendation Quality section in Source Evaluation. The section shows how many promising sources have not yet been checked and provides an "Evaluate recommendations" button to run recommendation-quality checks for those sources directly from the screen. A "Re-check all" option re-runs checks for all promising sources including already-checked ones.
        - Recommendation-quality results are now easier to see. Promising rows without a result show "Recommendations: Not checked" so it is clear a check is available but has not run yet.

        ## KMK-Recs v0.7.6

        - Source Evaluation batches now support continuation. After a batch completes, a "Continue next batch (N remaining)" button appears to evaluate the next slice without restarting from scratch. Progress is stored per filter fingerprint and expires after 7 days.
        - Past evaluation results now hide extensions that are currently installed by default. Use "Show installed" to reveal them. A "Hidden installed: N" count shows how many are hidden.
        - Source Evaluation now runs a second-stage recommendation-quality probe for STRONG_FIT and WORTH_TRYING sources. The probe queries each source with your top taste tags, scores the raw results against your profile, and stores a verdict (Great / Good / Mixed / Weak / No matches / Error / Too little evidence). Results appear as a third line on each past evaluation row.

        ## KMK-Recs v0.7.5

        - Export Top Picks, For You source rows, and Loved Manga as a shareable JSON bundle. Use the menu in the For You tab, Top Picks screen, or Loved Manga screen.
        - Import a recommendation bundle from a JSON file. Go to Settings > Data storage > Import recommendation bundle.
        - The import preview shows each manga's status: ready to add, already in library, missing source, or needing manual review.
        - If the bundle requires an extension that is not installed, Komikku offers to install it directly from the import preview.
        - Add selected manga to your library directly from the import preview. Duplicate and category behavior matches Komikku's existing library flows.

        ## KMK-Recs v0.7.4

        - Source Evaluation now records which extension version was installed at evaluation time. If a new version of the extension is released, the Source Evaluation screen shows a notice with the count of updated extensions and a "Reassess updated extensions" button to re-evaluate them.
        - Added SourceRecommendationFitEligibility and SourceRecommendationFitScorer pure helpers. These enable future bounded recommendation-quality probing for STRONG_FIT and WORTH_TRYING sources only. Actual probe execution is deferred.
        - Fixed stale documentation: Favorite other versions and alternate-title cross-extension matching were incorrectly marked as deferred. Both are fully implemented as of v0.7.0.

        ## KMK-Recs v0.7.3

        - Loved Manga now hides entries from sources that are no longer installed.

        ## KMK-Recs v0.7.2

        - Improved Loved Manga duplicate grouping by using confirmed matching versions first.
        - Group clear duplicates now handles more same-manga versions while avoiding title-only merges.

        ## KMK-Recs v0.7.1

        - Fixed a crash when opening Seen other versions from manga details.
        - Seen other versions now remains available after marking the current manga as seen.

        ## KMK-Recs v0.7.0

        - Added Loved Manga view: tap the heart icon in the For You tab to see all manga you have rated Love, sorted by most recently loved.
        - Enable "Group clear duplicates" in the Loved Manga view to collapse the same manga from multiple sources into one entry. Grouping uses exact title and description matching — no taste ratings are deleted or merged.
        - A version count badge shows on grouped entries when multiple sources have the same manga.

        ## KMK-Recs v0.6.20

        - Recommendation Settings now shows source status grouped as: sources with results first, sources with no results below them, and disliked sources last.
        - Source Evaluation now tracks how many manga ratings have been added since the last evaluation. When 100 or more new ratings are counted, a prompt appears recommending reassessment.
        - Added "Reassess sources" button in Source Evaluation to re-evaluate sources against your current taste profile at any time.
        - Source Evaluation past results now show evidence strength (Strong, Moderate, Weak, Low confidence) and how long ago each source was last evaluated.
        - Added a Source management section in Source Evaluation with options to reset disliked sources, reset the reassessment baseline, and clear the seen manga list.
        - Added "Mark as seen" action to the rating menu on manga detail pages. Marking a manga as seen removes it from For You recommendations without affecting your taste ratings.
        - Added "Seen other versions" action in the rating menu to mark alternate versions of a manga across extensions as seen using the same cross-extension matching workflow.
        - Marking manga as seen invalidates the For You recommendation cache so changes take effect on the next refresh.

        ## KMK-Recs v0.6.19

        - Source Evaluation now continues running in the background after leaving the screen. A foreground notification shows the current extension being evaluated. Tapping the notification opens the Source Evaluation screen directly.
        - Shizuku setup controls are now hidden by default when using Private (recommended) or Current installer. They appear automatically when Shizuku mode is selected, and can be expanded with a "Shizuku setup" link.
        - Quarantined extensions and blocked extension packages are no longer shown as prominent cards at the top of Source Evaluation. They are now accessible via a compact "Safety diagnostics" section below the evaluation options.
        - Added a low-confidence warning when the taste profile does not have enough rated manga or tags for reliable personalized scoring. Evaluation still runs, but source-fit scores may be less accurate.
        - Candidate count now shows "N unassessed extensions remaining" when skip-already-evaluated is on, making it clear how many extensions still need evaluation.
        - Starting evaluation now shows an error message when the device has no internet connection instead of silently doing nothing.

        ## KMK-Recs v0.6.18

        - Added package-level extension load quarantine: known crash-causing extensions are now blocked before their code is loaded into Komikku, preventing native SIGSEGV crashes at startup.
        - Digital Comic Museum is now blocked from loading (removable from Source Evaluation > Blocked Extensions if you want to re-enable it after uninstalling and reinstalling a fixed version).
        - New "Blocked Extensions" card in Source Evaluation shows which packages are blocked, with "Allow again" option per-package and "Allow all" to remove all blocks.
        - Source Evaluation diagnostics now include blocked package count and static guard status.

        ## KMK-Recs v0.6.17

        - Source Evaluation crash recovery now runs during app startup, so a crashing extension can be quarantined before you reopen the Source Evaluation screen.
        - Added a removable quarantine seed for Digital Comic Museum, based on repeated native crash logs showing it in the fatal network stack.
        - Improved unsafe-extension guidance: the Quarantined Extensions dialog now explains that if a quarantined installed extension still crashes Komikku outside Source Evaluation, you should uninstall or disable it manually from the Extensions screen.

        ## KMK-Recs v0.6.16

        - Source Evaluation now survives fatal native crashes (SIGSEGV / stack overflow) from extension code. A probe marker is written to SQLite before each risky network call; if the process dies, the marker is detected on next screen open, the extension is quarantined, and the marker is cleared.
        - Quarantined extensions are automatically skipped in future Source Evaluation candidate selection and shown in a "Quarantined Extensions" card on the Source Evaluation screen.
        - You can view, individually remove, or clear all quarantined extensions from the Source Evaluation screen.
        - Added "Copy Diagnostics" button to Source Evaluation for sharing state info when investigating issues.
        - Candidate diagnostics now shows how many extensions are hidden due to quarantine.

        ## KMK-Recs v0.6.15

        - Fixed Source Evaluation results stability after larger evaluation batches (50+ sources). Malformed or duplicate result rows are now sanitized before rendering, and stable Compose keys prevent list identity conflicts.
        - Added sorting for Source Evaluation past results: Best fit (default), Newest, Source name, Extension name, Search reliability, and Explicit risk.
        - Improved Source Evaluation result rows: compact score info (fit %, search %) shown as subtitle; error messages are truncated to prevent layout issues.
        - "Clear all" on Source Evaluation past results now asks for confirmation before deleting. The dialog clarifies that only evaluation cache is cleared.

        ## KMK-Recs v0.6.14

        - Source Evaluation now treats slow source timeouts as per-source failures instead of cancelling the whole run. A single hanging source (such as Asia2) will be marked as an error and skipped; evaluation continues with the next candidate.
        - Source priority reset is now protected by a confirmation dialog and moved out of the top bar. Tap "Restore default source order" near the Source Priority section and confirm before the order is changed.

        ## KMK-Recs v0.6.13

        - Source Evaluation now uses Private installer for silent cleanup by default. Evaluated extensions are temporarily stored inside Komikku and removed automatically without any Android uninstall prompt.
        - Extensions already installed before evaluation started are detected and skipped rather than accidentally uninstalled during cleanup.
        - Choosing Shizuku or Current installer (which system-installs extensions) now shows a warning dialog before starting, with options to switch to Private or continue with Android prompts enabled.
        - Private installer chip is now labeled "Private (recommended)" to make the recommended choice clear.
        - Shizuku setup card shows "Shizuku is ready, but Private is recommended for silent cleanup" when Shizuku is selected and Private is available, and offers a "Use Private" action to switch.
        - Added diagnostic logging (logcat tag "KMK SourceEvaluation install:") to help verify install and cleanup paths when investigating prompt behavior.

        ## KMK-Recs v0.6.12

        - Source Evaluation now evaluates the broad pool of available non-installed extensions matching your language and preferences, instead of only the small Sources To Try candidates list.
        - Fixed skip-already-evaluated filter: now correctly matches evaluations at extension level rather than source level, so evaluated extensions are properly hidden.
        - Candidate diagnostics: shows how many extensions are eligible, how many are hidden due to already-evaluated or explicit-content filters.
        - Shizuku UX improvements: status now distinguishes "selected and ready" from "selected but not ready yet"; Refresh status button lets you force a state re-read; screen auto-refreshes Shizuku state on resume.

        ## KMK-Recs v0.6.11

        - Added Shizuku setup controls to Source Evaluation: install/open shortcuts, temporary use for evaluation, stop-using action, and uninstall shortcut through Android.
        - Source Evaluation now reads real Shizuku status (installed, running, permission granted) instead of always treating Shizuku as unavailable.

        ## KMK-Recs v0.6.10

        - Fixed a crash when opening Source Evaluation from Recommendation Settings.

        ## KMK-Recs v0.6.9

        - Hotfix: updating from a pre-v0.6.8 APK no longer crashes For You / Recommendation Settings with "no such table: source_evaluation". A missing SQLite migration (47.sqm) has been added to create the source_evaluation table on upgrade.
        - Added defensive fallback: if source_evaluation is temporarily unavailable, Sources To Try renders using metadata-only suggestions instead of crashing. The fallback logs the error and emits an empty evaluation list.
        - No evaluation data is deleted. No uninstall/reinstall required.

        ## KMK-Recs v0.6.8

        - Added Source Evaluation: temporarily install non-installed extensions one at a time, probe their content (popular, latest, and search results), score how well they match your taste profile, and store the verdict in the database.
        - Source evaluation verdicts are now used by Sources To Try: strong-fit sources appear at the top (score 0.90), worth-trying sources at 0.75, explicit-heavy sources are filtered when block-explicit is on, and rejected sources are hidden.
        - Evaluation uses Private installer by default so install and cleanup are silent. Shizuku and Current installer are also supported with appropriate batch-size limits.
        - The installer override is temporary — your global extension installer preference is never changed by a Source Evaluation run.
        - Batch sizes: 10, 25, 50, and 100 (100 shows a warning). 500/1000 batches are not yet implemented.
        - Explicit-heavy and ecchi-heavy verdicts remain distinct — they are never merged.
        - Open Source Evaluation from Recommendation Settings > Source Evaluation.

        ## KMK-Recs v0.6.7

        - Added "Block explicit porn/hentai sources" toggle under Settings > Browse > NSFW content.
        - When enabled, clearly explicit sources (NHentai, E-Hentai, ExHentai, Pururin, Tsumino, 8Muses, HBrowse, HentaiFox, and others identified by name or package) are hidden from Browse > Sources, Browse > Extensions (available), and Sources To Try.
        - Ecchi-only sources are not blocked by this setting. Only sources with explicit hentai/porn keywords or known explicit IDs are affected.
        - Installed explicit extensions remain visible and manageable so they can be updated or uninstalled.
        - The setting is off by default; existing users are not surprised by hidden sources on update.

        ## KMK-Recs v0.6.6

        - Added selective uninstall to Browse > Extensions: tap "Select extensions" in the overflow menu to enter selection mode, choose installed/untrusted extensions, then tap "Uninstall selected (N)" to uninstall them.
        - A confirmation dialog shows how many extensions will be uninstalled before proceeding. Android may ask you to confirm each uninstall separately.
        - Available (non-installed) extensions and extensions actively downloading/installing cannot be selected.
        - Cancel exits selection mode and clears the selection without uninstalling.
        - Normal extension install, update, open, trust, and update-all behavior is unchanged.

        ## KMK-Recs v0.6.5

        - Added selective install to Sources To Try: tap Select to enter selection mode, choose specific suggested sources, then tap Install selected (N) to install only those.
        - Cancel exits selection mode and clears the selection.
        - Install visible suggestions remains available as the quick action.

        ## KMK-Recs v0.6.4

        - Fixed "Install visible suggestions" in Sources To Try: all visible suggestions now install reliably instead of stopping after the first one or two.

        ## KMK-Recs v0.6.3

        - Added "Install visible suggestions" button in Sources To Try to install all currently visible suggestions in one tap.
        - Individual install buttons are disabled while an install is in progress for that suggestion.
        - Clarified Like/Dislike accessibility labels: installed source thumbs are labeled "Like for For You" / "Dislike for For You"; suggestion thumbs are labeled "Like source suggestion" / "Dislike source suggestion."
        - Added scope note below Sources To Try explaining the difference: installed source dislikes affect For You only; Sources To Try dislikes hide future suggestions.
        - Source priority ordering persistence verified: ordering tests extended to cover malformed-id robustness.

        ## KMK-Recs v0.6.2

        - Added Like and Dislike controls to installed source rows and Sources To Try suggestion rows in Recommendation Settings.
        - Liked non-installed sources appear in Sources To Try even without metadata similarity, and rank above neutral suggestions.
        - Disliked non-installed sources are hidden from Sources To Try.
        - Disliked installed sources are excluded from For You recommendation searches (thumbs down = avoided, not uninstalled).
        - Dismiss remains separate from Dislike: dismiss means "not now," dislike means "avoid long-term."
        - Liked/disliked preferences persist across restarts. Changes immediately update Sources To Try and affect the next For You run.

        ## KMK-Recs v0.6.1

        - Sources To Try is now selective: a source only appears when it has meaningful similarity to one of your already-installed sources.
        - Language, repo, base URL, and generic keywords no longer qualify a source on their own.
        - If no strong suggestions exist, the section shows an honest empty state instead of listing all available extensions.
        - Language preference changes now trigger immediate suggestion refresh.

        ## KMK-Recs v0.6.0

        - Added Sources To Try in Recommendation Settings: suggests non-installed extensions worth trying for For You based on your recommendation language and installed source metadata.
        - Suggestions are labeled Potential fit or Worth trying — not proven good until installed and tested with For You.
        - Each suggestion shows the reason it was surfaced and has Install and Dismiss actions.
        - Install uses the existing extension install flow. After install, the source disappears from suggestions automatically.
        - Dismissed suggestions stay hidden until you refresh extensions.

        ## KMK-Recs v0.5.1

        - Reduced matching results to 2 per source to keep the confirmation list tighter and reduce wrong-match risk.
        - The manga you are rating no longer appears as a candidate in the matching list.

        ## KMK-Recs v0.5.0

        - Added Love other versions, Like other versions, and Dislike other versions actions to the rating menu on manga detail pages.
        - Searching for matching versions is capped per source so the confirmation list stays manageable, while normal global search remains unchanged.
        - Sources are filtered by your recommendation language preference and sorted by source priority order.

        ## KMK-Recs v0.4.4

        - Recommendation Settings now shows when a source\'s results were hidden by duplicate handling, rather than showing it simply as matched.
        - Source statuses in Recommendation Settings now update automatically when For You finishes running.
        - Source status list now clearly labels statuses as coming from the last For You refresh.
        - Top Picks drill-down now shows a loading message when opened while For You is still running, and an empty-state message when no results are available yet.

        ## KMK-Recs v0.4.3

        - For You now keeps filling from your source priority list so empty sources do not take up final recommendation slots.
        - Recommendation Settings now shows the latest For You status for each source, including no matches, filtered results, errors, and sources not yet reached.
        - Top Picks can now be opened to view up to 50 ranked recommendations.
        - Local KMK-Recs What\'s New now only shows user-facing recommendation changes.

        ## KMK-Recs v0.4.2

        - Top Picks duplicate matching now merges on exact title + author OR exact title + artist independently, so entries are correctly unified even when only one contributor field is shared across sources.
        - Hide known manga setting is now consistent with cached recommendations: changing the setting invalidates the cache and cached rows are filtered the same way as fresh results.
        - Added local KMK-Recs What\'s New notes (this screen).
    """.trimIndent()
}
// KMK <--
