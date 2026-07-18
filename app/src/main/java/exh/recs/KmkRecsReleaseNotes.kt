package exh.recs

// KMK -->
object KmkRecsReleaseNotes {
    const val VERSION_CODE = 760
    const val VERSION_NAME = "KMK-Recs v0.8.10"

    // KMK v0.8.9: entries from this version onward follow the official Komikku changelog structure
    // (version heading, short summary, "What's Changed" heading, New/Improve/Fix sub-headings with
    // bold area-label bullets) instead of a flat bullet list — see WhatsNewScreen.kt's MarkdownRender
    // (GFMFlavourDescriptor), which already fully supports this exact structure (it's the same
    // renderer used for the real official upstream changelog preview). Every entry below v0.8.9 is
    // preserved exactly as it was written, in its original flat-bullet format — retroactively
    // rewriting 40+ historical entries into the new structure was judged a large, error-prone
    // content-rewrite disproportionate to a formatting change, and out of scope for this pass; see
    // the v0.8.9 implementation report for this documented scope decision. v0.8.10 Phase G
    // reconfirmed this decision (see docs/recommendations/CURRENT_STATE.md) rather than revisiting it.
    val MARKDOWN = """
        ## KMK-Recs v0.8.10

        Deferred reader rating prompt, searchable settings and rated collections, taste suggestions and diagnostics, and Source Evaluation/backup hardening.

        #### What's Changed

        ##### New
        - **Taste and Tags:** a new "Suggestions from your ratings" section proposes tags to prefer or block, based on genres shared by manga you've rated Love/Like/Dislike. A tag needs at least 3 of your own rated manga behind it before it's suggested — a single Dislike never turns into a blocked-tag suggestion. Adding a suggestion uses the same add/edit tag action already available, so it's fully reversible.
        - **Management and Diagnostics:** a new "Taste diagnostics" section shows your rating counts, how many of your rated manga back each of your already-set tag preferences, and a plain-language summary of which signals are currently used (ratings, genres, explicit tag preferences) and which are not (reading history, extension content).
        - **Settings:** Recommendation Settings search now covers every individual control with a stable target, not just section-level entries.
        - **Loved/Liked/Disliked:** the Rated collections screen can now be searched by title or source.
        - **Sources To Try:** can now be searched, and sorted by best fit, name, or language.

        ##### Improve
        - **Reader:** the completion rating prompt (Love/Like/Dislike/Not Interested) now waits until you actually leave the reader instead of interrupting mid-read.
        - **Sources To Try:** every suggestion reason now shows real explanatory text, including an explicit "not enough evidence yet" state instead of ever showing nothing.
        - **Source Evaluation:** the "Evaluation completed" summary no longer lingers indefinitely — it's cleared once you leave the screen. A still-running evaluation is unaffected and keeps reporting progress normally.

        ##### Fix
        - **Backup restore:** a truncated or corrupted backup file (including a truncated gzip-compressed one, or a near-empty file) no longer crashes the restore — it now shows the same clear "invalid backup" message as other malformed-backup cases.

        ## KMK-Recs v0.8.9

        Official-style What's New formatting, and search for Recommendation Settings.

        #### What's Changed

        ##### New
        - **Settings:** Recommendation Settings now has a search action — search across For You, Source Priority, Taste and Tags, Source Evaluation, Sources To Try, installer/background behavior, and Management/Diagnostics, and jump straight to the right screen.
        - **What's New:** this changelog now follows the same New/Improve/Fix structure as the main Komikku changelog, going forward. Every past KMK-Recs entry remains available exactly as originally written.

        ##### Improve
        - **Settings:** search results show a category label so similarly named settings in different sections stay easy to tell apart, and a setting that's currently unavailable is shown as unavailable rather than left out of results silently.

        ## KMK-Recs v0.8.8

        Reading schedule enforcement fix (v0.8.7-fix1), a chapter-completion rating prompt, a Recommendation Settings index, and an outdated-evaluation fix.

        - Fix (v0.8.7-fix1): the reading schedule could grant a fresh reading allowance every time you left a restricted reader and opened a different manga — restriction was never actually enforced across reader sessions, only shown as a toast. Reading is now genuinely blocked, immediately, for a reader opened while restricted; only a reader that was already open when a restriction begins may finish its current chapter, with no extra chapter and no way to bypass it via manual chapter selection, deep links, rotation, or backgrounding.
        - New: after you finish the latest available chapter of a manga, an optional prompt offers Love/Like/Dislike/Not Interested/dismiss. Rating never fires twice for the same completion, and never appears while the reading schedule is restricting you.
        - New: after rating from that prompt, if the manga has confirmed other versions on different sources, you're offered the chance to rate those too, using the same version-matching tool already used elsewhere.
        - Change: Recommendation Settings now opens to a concise index of sections (For You, Source Priority, Taste and Tags, Source Evaluation, Sources To Try, Background/Installer, Management) instead of one long screen. Source Evaluation opens directly from the index. No preference or existing setting behavior changed — this is navigation only.
        - Fix: Source Evaluation could show sources as "Outdated — reassess needed" that "Continue reassessing outdated" would never actually process (for example, because you'd since installed that source, changed your language filter, or disliked it) — silently doing nothing instead of explaining why. This now shows a clear explanation instead of a confusing no-op.

        ## KMK-Recs v0.8.7

        Reading schedule fix, and rated/settings UI refinements.

        - Fix: the reading schedule's "Add window" flow could silently do nothing after you picked days — no time picker would appear. This is fixed: selecting weekdays now reliably opens the time picker.
        - New: the reading schedule time picker now follows your device's 12-hour or 24-hour display preference instead of always showing 24-hour time.
        - New: you can now add a "Whole day" window instead of only a specific time range.
        - New: reading schedule windows can now be edited in place (pencil icon) instead of only deleted and re-added.
        - Change: in the reading schedule dialog, Save commits your changes and Cancel (or tapping outside/back) discards them — tapping outside no longer accidentally saves a half-finished edit.
        - New: the Rated (Loved/Liked/Disliked) bulk-selection bar's More menu now offers "Select all in group" when your current selection belongs to one confirmed group, not only from the per-item menu.
        - New: Clear Rating and Mark Not Interested (bulk selection) now show an Undo option right after you confirm them.
        - Change: Daily recommendations, Ratings and Known Manga, Tags, Source Priority, Same-Manga Matching, and Sources To Try in Recommendation Settings now show a one-line summary of their current state (selected languages, visibility mode and chapter minimum, preferred/blocked tag counts, enabled source count and top source, results-per-source and preselect setting, suggestion count) so you can see what's configured without expanding every section.

        ## KMK-Recs v0.8.6

        Group recommendation loading performance and a configurable preview size.

        - New: "Initial results per extension" setting in Recommendation Settings (5/10/15/20/30, default 10) controls how many manga cards each extension shows in a group (Loved/Liked/Rated group) recommendation preview. Open a source row to keep loading more through the existing full search.
        - Change: group recommendation source rows now run with a bounded number active at once instead of starting every eligible extension simultaneously, so results appear progressively instead of all-or-nothing.
        - Change: a single slow or failing extension in a group recommendation now times out and shows its own row error instead of the possibility of stalling the screen.
        - Fix: leaving the recommendations screen mid-load now reliably cancels the in-flight search instead of continuing in the background.
        - Fix: cancelling a group recommendation load (navigating away) is no longer occasionally shown as a row error.
        - This setting is scoped to group recommendation previews only — it does not change For You's existing "Results per source" setting, and normal global search remains completely unaffected and uncapped.

        ## KMK-Recs v0.8.5

        Optional reading schedule.

        - New: an optional reading schedule (Settings > Reader > Reading schedule) that can restrict or allow reading during chosen days and times, entirely inside this app.
        - New: add or remove any number of recurring time windows, each covering one or more days of the week, using the same time picker already used elsewhere in the app.
        - Change: like the active-reading timer, a reading-schedule window never interrupts a chapter you're already reading — it lets you finish the chapter first.

        ## KMK-Recs v0.8.4

        Active-reading timer.

        - New: a reading timer in the reader (tap the timer icon in the bottom bar). Choose 15, 30, or 60 minutes, or set a custom duration.
        - New: optional warnings before time is up, and a choice to finish your current chapter — optionally one extra chapter — instead of being cut off mid-story.
        - Change: manual chapter selection and going back to the previous chapter never count toward the one-extra-chapter allowance; only continuing forward naturally after time is up does.
        - Change: the timer only counts while you're actively reading — it pauses automatically the moment you leave and resumes when you come back, unless you paused it yourself.

        ## KMK-Recs v0.8.3

        Recommendation Settings reorganized.

        - Change: Recommendation Settings is now organized into For You behavior, Source priority, Source evaluation, Source management, and Discovery/cache management sections, matching the rest of the app's settings layout.

        ## KMK-Recs v0.8.2

        Configurable For You results per source.

        - New: For You now has a "Results per source" setting (5/10/15/20/30, default 10) in Recommendation Settings.
        - Change: your top 3 boosted sources always show at least 20 results, even if you pick a smaller number for other sources.

        ## KMK-Recs v0.8.1-fix4

        Source quality marks and final cleanup.

        - New: you can now mark a source as poor or too explicit as a source/library, separate from your For You recommendation preference. Use it for sources whose overall library is bad, misleading, or too lewd/hentai-heavy — even if some individual results looked fine.
        - New: sources marked poor or too explicit are hidden from Sources To Try and Source Evaluation by default, and no longer feed For You once installed. Nothing is deleted — past evaluation history is preserved and can be shown again with "Show disliked sources".
        - New: a "Clear source quality marks" recovery action in Recommendation Settings removes every source/library mark at once.
        - Fix: after a stale/outdated Source Evaluation reassessment finishes, the screen now shows a clear "Outdated reassessment complete" message instead of just quietly removing the action.
        - Cleanup: What's New and other in-app text no longer reference build-channel wording; XML string comments and documentation were normalized.

        ## KMK-Recs v0.8.1-fix3

        Source Evaluation continuation fix.

        - Fix: after a Source Evaluation batch finished reassessing sources, rows still marked "Outdated — reassess needed" could no longer be continued into — the screen reported 0 unassessed extensions remaining even though outdated rows were still visible in the results list.
        - Fix: reassessing stale/outdated sources is now a separate, first-class queue with its own "Reassess outdated (N)" / "Continue reassessing outdated (N remaining)" action and its own progress cursor, so it can no longer be silently absorbed into the "already evaluated" count.
        - Change: the unassessed queue and the outdated-reassessment queue track progress independently — switching between them, or changing batch size, never discards either queue's progress. A "Restart outdated reassessment" action lets you explicitly start that queue over from the beginning.

        ## KMK-Recs v0.8.1-fix2

        Version visibility and sync validation.

        - Fix: opening Loved Manga no longer crashes with a "GetCrossSourceGroupPrimary" dependency error. The three primary-version interactors added in v0.8.0 were never registered, so any screen that needed them (Loved Manga, Linked Versions, backup, restore) could crash.
        - Fix: the KMK-Recs What's new dialog and version number are now shown reliably, and never suppressed by the regular Komikku update dialog on the same launch.
        - Fix: syncing your chosen primary version between devices now rejects malformed rows the same way restoring a backup already did.

        ## KMK-Recs v0.8.1-fix1

        Rated Manga and Source Evaluation polish.

        - Fix: removing a linked version from the version list now asks for confirmation first, matching the same safeguard already used elsewhere.
        - Fix: your chosen primary version per group is now included in backup, restore, and sync — it used to be lost when restoring or syncing.
        - Fix: pressing "Select" in Loved/Liked/Disliked now enters selection mode without selecting anything. Long-press still selects the item you pressed.
        - Change: the linked-version list now explains that the star sets the primary version, since that action isn't in the card menu directly.
        - New: Source Evaluation rows can show an expandable "Details" section — enrichment counts, metadata sample counts, and liked/disliked/blocked/adult-risk counts — explaining why a source got its verdict.

        ## KMK-Recs v0.8.0

        Rated Manga bulk selection and group actions.

        - Change: long-press in Loved / Liked / Disliked now enters bulk selection instead of opening recommendations. A "Select" button in the app bar does the same thing.
        - New: selection mode adds a bottom action bar — Change rating, Clear rating, Group, and More (Mark not interested, Remove from group).
        - New: each card has a menu with Recommendation, Rating, and Group actions, including "See group recommendations" (only shown for confirmed linked groups with 2+ versions), "Find other versions", and "Favorite other versions".
        - New: a focused "Linked versions" screen shows every version in a confirmed group — source, language, title, rating, favorite status, installed/missing status, last updated, and which one is the primary version.
        - New: you can set a primary version per group, which controls the cover/title shown in the rated list. Recommendations still use the whole group's metadata, not just the primary version.
        - New: Merge Selected Into Group, Remove From Group, and Ungroup actions, all confirmed before running. Merging never happens automatically by title — only by manual selection.
        - Safety: clearing a rating never deletes the manga, favorites, history, or version links.

        ## KMK-Recs v0.7.47

        Source Evaluation tag enrichment and scoring fix.

        - Fix: Source Evaluation now fetches full manga details for a bounded set of catalogue samples that are missing tags on the list page, instead of scoring sources only on what Popular/Latest happen to expose. Sources that were previously marked weak just because their list pages omitted tags are re-evaluated fairly.
        - Fix: sources with too little usable tag evidence are now shown as "needs manual review" instead of being confidently marked weak.
        - Fix: sources with a mix of liked tags and blocked/adult tags (BL/GL/adult/explicit signals) can no longer reach "Strong fit" purely because of broad positive tags — blocked and adult-risk evidence now gates the verdict.
        - Fix: source evaluation results scored under older app versions are now clearly shown as outdated and no longer sort above current, freshly-checked results.
        - Change: Source Evaluation scoring is now version 3; all previous results are treated as outdated until reassessed.

        ## KMK-Recs v0.7.46

        Polish and OCR safety cleanup.

        - OCR errors are now shown as safer, clearer messages instead of raw technical text.
        - OCR page-error logs no longer include manga titles or chapter names.
        - OCR index cleanup controls are easier to find — you can now clear OCR text for a single chapter or manga right from a search result, plus a quick way to clear empty/failed rows.
        - Source Evaluation error rows show clearer, translated messages instead of raw technical text.
        - Documentation and versioning were refreshed.

        ## KMK-Recs v0.7.45

        Final v0.7 release. This closes the v0.7 feature line and hardens the fork for everyday use.

        - Change: Loved/Liked/Disliked Rated Manga views now show grouped (duplicate-collapsed) display by default. You can still switch to a flat list, and that choice now survives background refreshes.
        - Change: Recommendation Settings now shows how often each source has contributed to your Top Picks (e.g. "Great fit · 5"), when it has contributed at least once.
        - Fix: checking a non-installed source's For You search compatibility now requires the Private installer. If Private isn't available, those checks are skipped with a clear message instead of silently using Shizuku/Current, where a leftover extension could previously go unnoticed.
        - Fix: Source Evaluation error rows no longer show raw internal exception text — errors are now classified into a small set of clear categories (network unavailable, timed out, unsupported, internal error).
        - Clarified that OCR ships in the same build as KMK-Recs (it was never actually a separate build, despite older docs saying so) — this is now documented accurately, including its local-only storage and backup/export exclusion.

        ## KMK-Recs v0.7.44

        - Fix: `RecommendationCandidateVisibilityPolicyTest` and two other test classes that build a favorite manga no longer fail with an unrelated Injekt error — the whole recommendation test suite is clean again.
        - Change: Loved/Liked group recommendations now actually search using every linked version's combined tags and titles, not just the primary version's — a source that can't match the primary version's exact tags now falls back through progressively looser tag attempts, then a title search across every linked version, before giving up on that row.
        - Change: group recommendations now honor the same source language/priority/disabled/disliked-source rules as For You, and the same favorite/rated/Not Interested/known/min-chapter visibility rules — previously they only excluded exact duplicates and Not Interested titles.
        - Fix: For You no longer gives up on a source after one overly strict tag search — it now tries up to three progressively looser attempts (same shared policy grouped recommendations use) before marking a source as having no results, and no longer "locks in" a search strategy that produced zero results.
        - Change: Source Evaluation rows are more compact — detailed error/reason text is now collapsed behind a "Show details" toggle instead of always taking up space, and the remaining hardcoded English error labels are now translatable. The Shizuku setup card's action buttons and the compatibility-check action buttons now wrap instead of crowding on narrow phones.
        - Added test coverage for the shared query-attempt policy and for the background For You search compatibility job's conflict-guard decision.

        - Fix: "For You search compatibility" checks (missing/outdated/re-check all) now run as a background job, the same way full Source Evaluation does. Leaving the Source Evaluation screen no longer stops a check in progress — it keeps running, shows its own notification with progress, and you can tap the notification or reopen Source Evaluation to see current progress or cancel it.
        - Fix: Source Evaluation and For You search compatibility can no longer run at the same time — starting one while the other is active now shows a clear message instead of letting them race over the same temporary extension installs.
        - Change: Loved/Liked group recommendations ("Recommendations from this") now use the same provider/extension row layout as a single manga's Recommendations page, seeded by every confirmed linked version of that title instead of just one. Cross-extension genre search rows now search by the group's combined tags, not one version's tags alone. Versions already in the group never appear as recommendations. The old single-grid group recommendations screen was removed.
        - Change: "Mark as seen" is renamed "Not interested" to honestly describe what it does — the title still stays hidden from For You and group recommendations, but similar manga are now also mildly deprioritized (much less strongly than Dislike). Your existing seen list is unaffected; nothing needs to be re-marked.

        ## KMK-Recs v0.7.42-fix2

        - Fix: Source Evaluation's sort menu no longer offers "Search reliability" — that sort ordered a field the app never actually measures. It's replaced with "For You compatibility", which truthfully orders sources by their real search-compatibility result: current good results first, then weak, then no-matches, then errors, then outdated results, then not-yet-checked, then ineligible sources last.
        - Fix: a source whose search-compatibility result is out of date (an older scoring version, or expired) now clearly shows "Outdated - recheck" instead of silently displaying its old result as if it were still current.
        - New: a "Recheck outdated" action lets you re-check only sources with stale results, without re-running every source like "Re-check all" does.
        - Fix: "Re-check all" now actually rechecks every eligible source, including ones with outdated results — previously it could silently skip them.
        - Fix: Best Fit sort no longer uses the retired "search reliability" figure as a tie-breaker; it now uses the real current search-compatibility result as a true tie-breaker, only after catalogue fit is equal.

        ## KMK-Recs v0.7.42-fix1

        - Fix: the manual "Check search compatibility" / "Re-check all" actions and diagnostics counts in Source Evaluation now use the exact same eligibility rules as automatic evaluation. A source with inconclusive catalogue evidence is no longer skipped by the manual check just because its individual verdict looked unfavorable.
        - Fix: search compatibility results computed before this update are now automatically re-checked instead of silently being treated as still current.
        - Fix: Source Evaluation rows no longer show a misleading "search 0%" figure — that field was never a real search measurement. Rows now show catalogue metadata confidence instead.

        ## KMK-Recs v0.7.42

        - Source Evaluation now scores catalogue fit (Popular/Latest samples) separately from search compatibility, instead of pooling both into one number. The redundant, less accurate tag-search probe that used to run inside Source Evaluation was removed — search compatibility is measured only by the dedicated probe, and its results are now labeled "For You search: Good/Great/…" instead of the misleading "Recommendations: Good/Great/…".
        - Catalogue-fit matching now uses the exact same taste-matching logic as For You itself, including tag aliases and blocked-tag hard exclusion, instead of a simpler approximation.
        - A source with sparse Popular/Latest tag metadata is no longer penalized as a poor fit — a new confidence signal tracks how much usable tag data was actually sampled, and a source with inconclusive catalogue evidence is still checked for search compatibility rather than being silently skipped.
        - Existing Source Evaluation results are automatically treated as outdated and eligible for reassessment, since the scoring rules changed.

        ## KMK-Recs v0.7.41

        - Fix: cached and remembered candidates now obey exactly the same visibility rules as live results. A manga hidden by the minimum-chapter filter, or that is known/rated/seen/favorited/disliked, can no longer reappear just because it came from the cache or discovery memory.
        - Fix: group-seeded recommendations no longer run short. Hidden candidates (already in library, rated, seen, below the chapter minimum, or part of the seed group) no longer use up the 20-result budget — the search keeps scanning bounded chunks until it collects 20 visible results or exhausts its safe limits.
        - Fix: discovery retry state is now truthful. A page that used up all its retries is recorded as permanently exhausted (keeping its diagnostic) instead of a generic error, and a due retry of the final page (page 20) can run while a brand-new page past the cap (page 21) is never created.
        - Fix: unknown extension errors are now treated as permanent rather than retried forever. Only genuine connectivity, I/O, and timeout failures are retried; HTTP 4xx and unexpected extension crashes stop after one record.
        - Cancellation is never recorded as a failed retry.

        ## KMK-Recs v0.7.40

        - Fix: extra-page discovery candidates were dropped when For You memory was empty — they are now merged and ranked with the same path as page-1 and remembered candidates, so no valid candidates are silently lost.
        - Fix: the same merge defect applied to the cached path — the early-return short-circuit on empty memory has been removed from both paths.
        - Discovery retry: transient network failures (timeout, no connection, I/O errors) on additional-page probes now record retry metadata (attempt count, next-retry timestamp, failure kind) and are retried with bounded exponential backoff (5 min → 10 min → 20 min, capped at 24 hours, max 3 attempts). HTTP 4xx errors and UnsupportedOperation are recorded as permanent and not retried.
        - Discovery timeout: additional-page probes are now bounded to 20 seconds. A timeout is recorded as a retryable failure so the page is retried on the next refresh.
        - Group-seeded recommendations now use the user's real language preference, source priority order, and liked/disliked source exclusions — the same source selection logic that For You uses — instead of always searching all English sources without priority or exclusions.
        - Group-seeded recommendations now apply the full For You visibility filter (favorites hidden, rated hidden per visibility setting, seen manga hidden, known manga hidden when enabled, min-chapter threshold) using a shared policy object.
        - For You and group-seeded recommendations now share `RecommendationSourceSelector` and `RecommendationCandidateVisibilityPolicy` so both flows produce consistent results.

        ## KMK-Recs v0.7.39

        - For You rolling discovery: each refresh now tracks which pages were evaluated in a new `recommendation_discovery_progress` table (migration 57). Empty, filtered, and error pages are recorded so they are not retried on every refresh — only unevaluated pages are probed.
        - Discovery advances progressively: after page 1 is evaluated, page 2 is probed on the next refresh; after page 2, page 3; and so on up to 20 pages per source/query before the discovery path is considered exhausted.
        - Reset For You discovery history now also clears the progress table so discovery restarts from page 1 on the next refresh.
        - Rated group recommendations remain unchanged (full cross-source group seeding was already implemented in v0.7.38 via GroupRecommendationSeedBuilder).

        ## KMK-Recs v0.7.38

        - For You discovery memory: discovered candidate manga are now remembered between refreshes. On the next For You refresh, remembered candidates are merged with newly discovered ones and re-ranked against the current taste profile — strong past candidates are not lost just because they didn't appear in the latest batch.
        - Additional page discovery: after page-1 results are recorded, For You now probes page 2 (and page 3 on subsequent refreshes) to expand the candidate pool beyond what a single search page returns. Each additional page is bounded to 20 new candidates.
        - Group-seeded recommendations: the recommendation seed is now built from ALL confirmed linked versions of a manga, not just the one being viewed. Sparse group members (fewer than 2 local genres) are enriched with live metadata — bounded to 8 members, 5 seconds per member, 20 seconds total — before the weighted tag seed is computed. Tags contributed by more group members receive higher weight in scoring.
        - Group seed scoring: a new GroupSeedRecommendationScorer adds a seed-tag score on top of the personal taste score — tags that appear across 2+ group members score 1.0 (strong match), single-member tags score 0.3 (weak match), both scaled by the tag's group weight.
        - Reset For You discovery history: new button in Recommendation Settings → Management clears all remembered For You candidates without affecting ratings, seen manga, or source settings.

        ## KMK-Recs v0.7.37

        - Group-seeded recommendations no longer stay on the loading spinner indefinitely. The total load time is now bounded by a 45-second screen timeout; if time runs out with partial results they are shown, and if none are found the screen shows the empty state.
        - Localization of each candidate (NetworkToLocalManga) now has a 5-second per-candidate timeout. Candidates that take too long are skipped without blocking subsequent results.
        - The screen now stops early once 20 results are collected rather than continuing to query all remaining sources and plans.
        - Reduced source and candidate caps to values appropriate for a quick single-manga drill-down: 5 sources max, 8 raw candidates per source.
        - Source-aware deduplication: candidates are now keyed by (sourceId, url) pairs instead of url alone, so the same path on different sources is no longer incorrectly merged.
        - Cross-source link group rating exclusivity: if a confirmed linked group contains members with different ratings (e.g. one version Loved, another Liked), the group now appears in only one rating tab — the tab that matches the most-recently-updated member's rating. Previously, both tabs could show the same grouped manga.
        - CancellationException is now re-thrown inside the per-source try/catch so navigating away from the screen correctly cancels the in-flight recommendation load.

        ## KMK-Recs v0.7.36

        - Rated Manga UI parity: Liked and Disliked manga screens now share the full Loved Manga feature set — sort chips, group-duplicates toggle, version badges, cross-source link management, and export. Export filenames are rating-appropriate (kmk_liked_manga.json, kmk_disliked_manga.json).
        - Discoverability: each Loved/Liked card shows a small Explore icon overlay (top-left) that opens "Recommendations from this" without requiring a long-press. Long-press still works as a shortcut. Disliked cards omit the overlay to avoid confusing semantics.
        - Library toolbar shortcuts: Loved Manga, Liked Manga, and Disliked Manga are now accessible from the Library overflow menu, not only from Browse > For You.
        - Crash fix: group-seeded recommendations no longer crash when tapping a result. All candidates are now localized via NetworkToLocalManga before being shown, so MangaScreen always opens a valid local DB id. Candidates that fail localization are silently skipped.

        ## KMK-Recs v0.7.35

        - Fix: `NetworkOnMainThreadException` crash in the recommendation-quality probe — all source calls (`getFilterList`, `getSearchManga`, `getMangaDetails`) now run on the IO dispatcher. The exception is classified as an internal probe error and does not reduce source quality scores.
        - Rated Manga: "Liked" and "Disliked" views accessible from the For You toolbar (thumbs-up and thumbs-down icons). Show manga rated LIKE or DISLIKE respectively; same installed-source filter and grid as Loved Manga.
        - Group-seeded recommendations: long-press any Loved Manga card to open "Recommendations from [title]" — searches installed sources using the seed manga's genre tags (boosted in the taste profile), filters out the seed group members from results.

        ## KMK-Recs v0.7.34

        - Source Evaluation: per-source error category labels on rec-quality ERROR rows (e.g. "Ext not found", "Install failed", "Search timed out") so the failure type is visible without expanding the error detail (C1).
        - Source Evaluation: "Retry" button in the post-run summary card when the run ended due to connectivity loss, allowing one-tap restart without resetting results (C2).
        - Source Evaluation: profile-changed banner shown when the taste profile has grown by 5+ entries since evaluation was last run, prompting a re-run for fresh results (C3).

        ## KMK-Recs v0.7.33

        - Best Version compare: fullscreen page dialog state survives screen rotation and back-stack navigation via three rememberSaveable primitives (I1).
        - Best Version compare: tap to dismiss fullscreen preview when zoomed at 1× (I2).
        - Best Version compare: per-thumbnail Fit/Crop toggle button using ContentScale.Fit or ContentScale.Crop so narrow covers display correctly without cropping (I3).

        ## KMK-Recs v0.7.32

        - For You source stats: 30-day rolling window for fit labels — recent run rates are preferred over all-time rates when at least 3 runs have occurred in the last 30 days, giving more accurate Great/Good/Mixed/Error labels as source quality changes over time (D1).
        - For You source stats: tracks how many times each source contributed manga to the final Top Picks row, surfaced in the SourceFitStats as topPicksContributionCount (D2).

        ## KMK-Recs v0.7.31

        - Recommendation Settings: configurable enrichment cap — the number of candidate manga per source enriched with full metadata can now be set to 1/2/3/5/10/15/20 (default 5). Boosted sources always get 2× the cap. Higher values are more accurate but slower (J).

        ## KMK-Recs v0.7.30

        - Loved Manga: new "Manage Cross-Source Links" screen accessible from the app bar, showing all cross-source link groups with expand/collapse and per-link or whole-group delete (B).

        ## KMK-Recs v0.7.29

        - Recommendation Settings source priority list: each source now shows a "Last checked: X ago" timestamp below the status label when at least one run has been recorded, using the system's relative-time formatter (A1).
        - For You screen: pull-to-refresh gesture on the recommendations list — pulling down restarts the For You run with the same settings (A2).
        - Loved Manga screen: live updates when your Loved Manga list changes in the database — ratings applied on other screens are reflected immediately without requiring a manual refresh (A3).
        - Source Evaluation safety diagnostics: quarantine/blocked-package row can now be collapsed/expanded by the user (A4).

        ## KMK-Recs v0.7.28

        - Backup/restore: For You "Seen" dismissals are now included in Komikku backups (proto field 626). The restore is additive — any dismissals accumulated after the backup was created are preserved, never cleared. Old backups without the new field restore cleanly with no seen keys (proto default = empty list). 6 round-trip tests added in SeenMangaKeyBackupTest covering: single-key round-trip, multi-key round-trip, merge-new-keys, no-duplicate-on-overlap, empty-backup-leaves-existing-unchanged, empty-existing-produces-backup-set.

        ## KMK-Recs v0.7.27

        - Recommendation Settings: new "Best Version History" browser in the Management section. Shows all past Best Version decisions grouped by the origin manga, with the selected source name, selected title (when different from origin), chapter used, and date confirmed. Each record can be deleted individually (removes only the quality signal — does not affect library entries, ratings, or cross-source links). The action bar has a "Clear all history" button with a confirmation dialog.

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
