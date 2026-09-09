package exh.recs

// KMK -->
object KmkRecsReleaseNotes {
    const val VERSION_CODE = 785
    const val VERSION_NAME = "KMK-Recs v0.8.21-fix2"
    const val DISPLAY_VERSION_NAME = "Komikku FC v0.8.21-fix2"

    /** Projects the retained historical notes into the current public product name. */
    fun displayMarkdown(): String = MARKDOWN.replace("KMK-Recs", "Komikku FC")

    // KMK v0.8.10-fix9: every KMK-Recs entry, including every historical entry back to v0.4.2, now
    // follows the same official Komikku changelog structure -- version heading, one short summary
    // paragraph, "What's Changed" heading, and New/Improve/Fix sub-headings with bold area-label
    // bullets -- rendered by WhatsNewScreen's existing MarkdownRender (GFMFlavourDescriptor), the
    // same renderer used for the real official upstream changelog preview. Historical entries were
    // previously left in their original flat-bullet format (a v0.8.9/v0.8.10 scope decision); the
    // user later asked for every entry to be converted. The conversion is lossless: every version
    // heading, and every individual bullet's content, was preserved -- only reorganized into
    // categories and reworded where a bullet had a redundant leading label (e.g. "Fix: ...", "New:
    // ...") that is now expressed by its category heading instead. See the v0.8.10-fix9
    // implementation report for the exact conversion approach and verification.
    val MARKDOWN = """
        ## KMK-Recs v0.8.21-fix2

        Local tracking gains a full status/list workflow, and rating actions across the reader completion prompt, manga detail, and Rated Manga are fenced against duplicate taps.

        #### What's Changed

        ##### New
        - **Local tracking status/list workflow:** Track locally now opens a status list (Reading, Plan to read, On hold, Completed, Dropped) instead of only toggling on and off. Changing status is reversible; Remove local tracking is a separate, explicit action from the same dialog.

        ##### Improve
        - **Komikku FC naming:** the app's own display name and repository README now read Komikku FC, alongside the What's New screen's existing forward-facing naming.

        ##### Fix
        - **Duplicate-tap protection:** rating actions (Love/Like/Dislike/Not interested) on the reader completion prompt, the manga detail page, and Rated Manga's bulk-rating action now ignore a second tap while the first is still being saved, so a fast double-tap can no longer record two ratings or leave the screen in an inconsistent state.

        ## KMK-Recs v0.8.21

        Focused recommendation controls, forward-facing Komikku FC naming, native-library packaging compatibility for current Android devices, and a complete status record for the accepted Komikku FC change set.

        #### What's Changed

        ##### New
        - **For You focus:** temporarily focus recommendations on one or more existing genre groups from the For You surface, then clear the focus or explicitly show all results again. Durable taste, Not Interested, source, language, quality, library, and exposure rules remain authoritative.

        ##### Improve
        - **Accepted current scope:** the release record covers the shared Love/Like/Dislike/Not interested preference family, recommendation eligibility and correction, contextual settings navigation, Best Version image reliability, delayed-action acknowledgement and duplicate fencing, search-action clarity, extension chapter recovery, cross-source identity and preference carryover, tag and metadata diagnostics, same-number chapter-line continuity, and the applicable reusable review surfaces. Each item remains subject to its registered host, runtime, privacy, accessibility, localization, or human gate; this list is scope traceability, not a claim that an unopened gate has shipped.
        - **Documentation and comparison:** the accepted Komikku FC naming, plain-language documentation, upstream/intended/actual comparison, and diagram-readability work are recorded with their retained-scope and review status rather than being silently omitted from the release record.

        ##### Planned
        - **Local internal tracker:** the accepted AniList/MAL-style local tracker proposal remains a separately contracted feature. It requires explicit local statuses and lists, distinction from release-status groups and Library membership, opt-in grouping behavior, source relinking and confidence, backup/restore/migration/privacy rules, and explicit non-automatic external-tracker synchronization before it can be described as shipped.
        - **Focused recommendation modes and expanded review modes:** temporary genre/theme/tag/mood focus, exclusions, explainable ranking, and reusable view modes remain contract- and applicability-gated where the current build does not yet provide the complete behavior.

        ##### Fix
        - **Android compatibility:** rebuilt the bundled native libraries with 16 KB ELF load-segment alignment and verified the universal APK with the deterministic alignment checker.
        - **What's New:** the current feature release and historical rendered entries now use the forward-facing Komikku FC name while retaining technical compatibility identifiers internally.

        ## KMK-Recs v0.8.20-fix5

        A visual polish follow-up that adds theme-aware empty-state illustrations to the recommendation, source-evaluation, action-history, best-version, and reader-schedule flows.

        #### What's Changed

        ##### New
        - **Empty states:** added compact, theme-aware illustrations for For You, Source Evaluation, Action History, Find Best Version, and Reader Schedule without changing the underlying actions or state behavior.

        ## KMK-Recs v0.8.20-fix4

        A corrective follow-up that makes tracker changes visible in Evaluation Mode Action History, tightens embedded WebView URL handling, and documents the remaining externally irreversible operations honestly.

        #### What's Changed

        ##### New
        - **Action History:** successful tracker status, score, and chapter-progress changes now appear as visibility-only entries with a guarded action to send the previous value back through the tracker service when the manga, track, and login state are still available.

        ##### Fix
        - **Tracker writes:** ordinary remote failures now propagate after the existing error feedback, so Action History never records a write that did not complete.
        - **WebView:** navigation now accepts only parsed `http` and `https` URLs and rejects local, JavaScript, intent, and scheme-prefix lookalike URLs.
        - **Action History consistency:** receipt privacy fields and download deletion results are now tested and recorded only after confirmed file deletion.

        ## KMK-Recs v0.8.20-fix3

        A corrective follow-up that makes schedule deletion explicit, keeps Evaluation Mode redaction consistent in Source Priority summaries, and exposes the metadata and tag evidence observed for each evaluated source.

        #### What's Changed

        ##### Fix
        - **Reader schedule:** removing a time window now requires confirmation, preventing an accidental tap from immediately changing the saved draft.
        - **Evaluation Mode:** the Source Priority summary now uses the same generic source label as its rows while preserving the enabled-source count.
        - **Source diagnostics:** Management and diagnostics now shows the latest per-source metadata coverage, enrichment, confidence, and tag-evidence counts without exposing raw sampled tags or source-specific private data.

        ## KMK-Recs v0.8.20-fix2

        A corrective follow-up that keeps Source Evaluation's reassessment counts aligned with the sources the current run can actually process, and makes successful user-initiated extension installs visible in Evaluation Mode Action History across all supported install routes.

        #### What's Changed

        ##### Fix
        - **Source Evaluation:** completion reconciliation now uses the same actionable stale-source queue as "Reassess outdated", including the current explicit-source setting, so excluded sources cannot inflate the work count or make completion state disagree with the action.
        - **Action History:** successful extension installs from Browse > Extensions, Sources To Try, recommendation bundle import, and Source Evaluation are now recorded consistently as visibility-only events when Evaluation Mode is enabled. Failed, cancelled, and temporary evaluation installs remain absent.

        ## KMK-Recs v0.8.20-fix1

        A narrowly scoped fix pass addressing three defects found during final code validation of v0.8.20: manga detail ratings now appear in Action History, Source Evaluation no longer shows a confusing "skipped sources" warning next to its reassess action, and completed migrations/extension installs are now visible (but not falsely undoable) in Action History.

        #### What's Changed

        ##### Fix
        - **Action History:** rating or clearing a rating from the manga detail page (not just For You/Loved/Liked/Disliked) now appears in Evaluation Mode's Action History with a working Undo, matching every other rating surface.
        - **Source Evaluation:** removed the pre-run "N skipped source(s)" disclosure next to "Reassess outdated" -- the action already only ever counted actionable sources, so the disclosure only ever described sources the action was never going to touch, which read as a warning even though nothing was blocked.
        - **Action History:** a completed Best Version migration or a confirmed source install is now recorded as a visibility-only Action History entry, clearly labeled as not undoable (migrations and installs have no safe automatic inverse -- this does not add a fake Undo).

        ## KMK-Recs v0.8.20

        Small correctness and test-hygiene fixes found during a full remediation audit of the recommendation feature: one unlocalized button and one flaky test.

        #### What's Changed

        ##### Fix
        - **Find Best Version:** the error screen's "Back" button (shown when the origin manga can't be loaded, or the selected version has no chapters) is now localized like every other button on the screen, instead of always showing English text.
        - **Tests:** `LinkedVersionListBuilderTest` no longer fails intermittently depending on which other test classes ran first in the same test process.

        ## KMK-Recs v0.8.19

        A new developer-only "Evaluation Mode" setting relabels source, extension, and repository names (and preferred/blocked tag labels) with generic placeholders everywhere they're shown, so screenshots and screen recordings can be captured without exposing private source or repository identity. Manga titles are never affected.

        #### What's Changed

        ##### New
        - **Settings > Advanced > Developer tools:** a new "Evaluation Mode" switch. While enabled, source names, extension icons, extension-repository names, and preferred/blocked tag labels are shown as generic placeholders ("Source A", "Repo 1", "Like Tag 1", ...) across Browse, Extensions, extension-repository management, manga detail, OCR search, Recommends, For You, Source Evaluation, Rated collections, and Find Best Version. Manga titles, including disliked-manga titles, are never changed. This is purely a display-layer toggle -- it never alters stored data, network behavior, or any source/repository management action itself.

        ## KMK-Recs v0.8.18-fix1

        A follow-up to v0.8.18: manga detail's Copy link is reachable again from the "More" menu, extension export no longer runs on the UI thread, and the right-edge quick-access panel moved from Recommendation Settings onto For You and Loved/Liked/Disliked, where it now also reaches Source Evaluation's Home/For You action.

        #### What's Changed

        ##### New
        - **Manga detail:** "Copy link" is back as an explicit "More" menu action -- it was reachable by long-press before v0.8.18 moved WebView into the overflow menu, which had no long-press affordance.
        - **Source Evaluation:** a "Go to For You" app-bar action returns you straight to the For You tab.
        - **For You / Loved / Liked / Disliked:** a right-edge quick-access panel now lets you jump between these four surfaces without backing out through Browse.

        ##### Improve
        - **Extensions:** exporting one or several installed extensions now runs its file-copy work off the main thread, so a large export can no longer freeze the UI.
        - **Recommendation Settings:** the right-edge quick-access panel was removed from every settings detail screen -- the existing quick-access row remains there; the panel now lives only on the browsing/collection surfaces above.

        ## KMK-Recs v0.8.18

        Best Version comparison now includes your current version as a baseline you can keep, unavailable-chapter sources no longer spin forever, and full-screen previews respect your own reading-mode preferences. Source Evaluation and manga detail are less cluttered, extensions can now be exported for backup/sharing, and Recommendation Settings gained a right-edge quick-access panel.

        #### What's Changed

        ##### New
        - **Find Best Version:** your current version now appears in the comparison as a clearly labeled baseline, selected by default -- choosing it just keeps what you already have, with no migration performed.
        - **Extensions:** installed extensions can now be exported as a file, from Extension Details (single) or from a new selection mode on the Extensions page (multiple, as one zip). Only the extension file itself is included -- no ratings, history, or other personal data.
        - **Recommendation Settings:** a compact right-edge quick-access panel is now available on every detail screen, alongside the existing quick-access row -- tap the edge handle to jump straight to another section.

        ##### Improve
        - **Find Best Version:** a candidate whose chapter is already known to be unavailable is now clearly marked and skipped instead of spinning forever or trying to load anyway.
        - **Find Best Version:** the full-screen comparison view now takes your own reader display preferences (webtoon-style side padding) into account for a fairer comparison.
        - **Source Evaluation:** removed the top-right "Sources to try" shortcut -- it's now redundant with the Recommendation Settings quick-access row already on this screen.
        - **Manga detail:** WebView, Merge, and Find best version moved into one "More" menu instead of crowding the action row as equal-weight buttons; every action is still one tap away.

        ## KMK-Recs v0.8.17-fix1

        A live-device follow-up: For You's selection bar gained a working Clear Rating, real success/failure feedback, and a route to rate other versions after a single rating; Source Evaluation's Details/Errors/Install actions and For You's action buttons now line up consistently; Recommendation Settings detail screens gained a quick-access row to jump between them; and Find Best Version no longer hangs on a slow source, explains its progress more accurately, and lets a failed candidate be retried on its own.

        #### What's Changed

        ##### New
        - **For You:** selecting manga now has a Clear Rating action, alongside Love/Like/Dislike/Not interested.
        - **For You:** after rating exactly one selected manga, you can continue straight into rating its other versions on different sources, the same flow already available from manga detail and Rated collections.
        - **Recommendation Settings:** every detail screen (For You sources, Taste and filters, Source Evaluation, Sources to try, Management and diagnostics) now has a quick-access row near the top, so you can jump between them without backing out to the index.
        - **Find Best Version:** a failed or timed-out candidate can now be retried on its own, without restarting the whole comparison.

        ##### Improve
        - **For You:** applying Love/Like/Dislike/Not interested/Clear Rating now shows a short confirmation, and a failure is never silently swallowed.
        - **For You:** the selection bar's action buttons now line up consistently, instead of drifting depending on label length.
        - **Source Evaluation:** the `Details`/`Errors`/`Install` row actions now use one consistent layout, fixing `Install` appearing visually disconnected from the other two.
        - **Find Best Version:** the "N/N pages loaded" summary was rewritten to "Prepared N of N preview samples", so it no longer implies every preview image itself rendered successfully.

        ##### Fix
        - **Find Best Version:** a single slow or hanging source could previously leave the whole comparison screen stuck on a loading spinner, even after every other source's preview had already finished. Each candidate now has a bounded timeout, so the screen always reaches a usable state.
        - **Reader:** after rating a manga from the "finished latest chapter" prompt, a continuation offering to rate other versions is now shown reliably, with wording that doesn't claim confirmed other versions exist unless they do.

        ## KMK-Recs v0.8.17

        A For You diagnostics pass and Komikku v1.14.1 upstream reconciliation: source status lines in For You sources now explain themselves on tap instead of relying on one dense line, several universal-UI surfaces were re-audited for readability, and the app picks up Komikku's delegated-source loading fix, chapter-hash download-folder migration, and repository URL fix.

        #### What's Changed

        ##### Improve
        - **For You sources:** each source's status line (for example "No matches" or "Filtered out") can now be tapped to see a full plain-language explanation of why — for example, that a search returned nothing at all versus that results were found but excluded by ratings, blocked tags, or a missing positive taste match. The compact line itself is now shorter; the fuller detail moved behind the tap instead of being crammed into one line.
        - Re-audited Sources to Try, Rated manga collections, Group recommendations, and Best Version against the universal readability/interaction standard — all confirmed already compliant (concise rows, one visible action plus overflow, official selection-mode components). No changes were needed.

        ##### Fix
        - Picked up Komikku v1.14.1: delegated source loading (used by Pururin, MangaDex, NHentai, LANraragi, and 8Muses) now matches upstream's corrected logic; extension repository URLs no longer get a duplicated `/repo.json` suffix; and a one-time migration corrects any download folder naming affected by the chapter-URL-hash preference for existing installs.

        ## KMK-Recs v0.8.16-fix1

        A UI readability and responsive-polish pass over v0.8.16: Source Evaluation rows and reassessment wording are clearer, For You's selection bar adapts to phone width, the For You preview is now full-screen, Recommendation Settings subtitles are simpler, and Find Best Version previews now load real source-aware images instead of sometimes showing broken placeholders.

        #### What's Changed

        ##### Improve
        - **Source Evaluation:** past-evaluation rows are easier to read at a glance — the status line and the `Details`/`Errors`/`Install` actions are larger and now sit together in one row that wraps cleanly on narrow screens instead of always stacking.
        - **Source Evaluation:** the "Reassess outdated" completion message no longer reads as full success when sources were skipped — it now says how many were reassessed and how many were skipped as two separate, clearer sentences. The skipped-sources note is now labeled "N skipped source(s)".
        - **For You:** the selection action bar now adapts to screen width — Love/Like/Dislike stay directly visible, and Not interested/Find best version/Open move into a "More" menu on narrower screens instead of crowding six buttons into one row. Every action is still available; none were removed.
        - **For You sources:** "Preview For You" now opens as a full-screen, read-only preview instead of a small capped dialog, closer to how the real For You page looks and scrolls.
        - **Recommendation Settings:** index subtitles are shorter and describe what each section does for you, instead of describing implementation details (for example, Source Evaluation's subtitle no longer talks about temporarily installing extensions — that detail still lives inside the Source Evaluation screen itself).

        ##### Fix
        - **Find Best Version:** preview images now load through the same source-aware page-preview path the rest of the app already uses, instead of loading a raw image link directly. This fixes candidates that previously reported "pages loaded" while actually showing broken image placeholders; a page that still can't load now shows a clear "Preview failed" message instead of a silent broken icon.

        ## KMK-Recs v0.8.16

        Best Version and For You polish: "Find best version" is now its own manga action instead of living inside the Love/Like/Dislike dropdown, Best Version previews clearly show which source each candidate is from with a full-screen compare view, migrating to a new version now opens that version instead of the old one, For You gained long-press multi-select, and older What's New entries collapse by default so the changelog stays readable.

        #### What's Changed

        ##### New
        - **Manga detail:** "Find best version" is now a separate, always-visible action next to the other manga actions instead of a hidden entry inside the Love/Like/Dislike dropdown — it's a source-quality comparison workflow, not a rating.
        - **Best Version:** candidate rows during confirmation, chapter selection, and preview comparison now show the source name alongside the title, so it's always clear which extension a candidate came from.
        - **Best Version:** each candidate's page preview has a full-screen, read-only comparison view (source name, title, and every sampled page in one scrollable view) in addition to the existing single-page zoom.
        - **For You:** long-pressing a manga card now enters selection mode. While selecting, tapping other cards toggles them, and a bottom bar offers Love/Like/Dislike, Not interested, and (with exactly one card selected) Find best version and Open — for both Top Picks and per-source rows.
        - **KMK What's New:** the current version family stays expanded, older families (v0.7.x, v0.6.x, ...) are now collapsed by default with a short summary, and expanding one reveals the full historical entries exactly as before — nothing was deleted or reformatted.

        ##### Fix
        - **Best Version:** after a migration or copy finishes, tapping Done now opens the manga you migrated to instead of returning to the old entry. If the new manga can't be resolved for some reason, it falls back to closing the screen instead of crashing or navigating incorrectly.

        ## KMK-Recs v0.8.15-fix1

        A second Source Evaluation reassessment fix, and the Details/Errors/Install action model: "Reassess outdated" no longer counts blocked/explicit sources as actionable work, and each past-evaluation row now has clear Details, Errors, and (when safe) Install actions.

        #### What's Changed

        ##### New
        - **Source Evaluation:** each past-evaluation row can now show up to three clear actions — `Details` (full catalogue evidence), `Errors` (only shown when there's actually error information), and `Install` (only shown when the source can genuinely be installed directly — never for an already-installed, blocked/quarantined, incompatible, or no-longer-available source). Install reuses the same installer path and safety prompts used everywhere else in the app.
        - **Source Evaluation:** a page-level "Sources to try" shortcut opens the existing curated install/rating screen directly, so acting on a Strong Fit or Worth Trying source doesn't require backing out of Source Evaluation first.

        ##### Fix
        - **Source Evaluation:** fixed a second cause of the "Reassess outdated" advancing without any database change — the stale-reassessment queue could include sources blocked by the explicit-content filter; the runner correctly skipped them without writing anything, but still advanced the reassessment progress past them. The actionable count and action now only ever include sources the current run can genuinely process; a blocked/excluded source can no longer count as completed work.
        - **Source Evaluation:** if the internal step that clears out a source's old outdated results fails, that source is no longer silently counted as handled — it stays available for the next reassessment attempt, and a short in-app note explains that some sources couldn't be fully updated.

        ## KMK-Recs v0.8.15

        Source Evaluation reassessment fix and a readability pass: "Reassess outdated" now does real work or explains why it can't, past-evaluation rows are easier to scan, and several Recommendation Settings summaries are shorter and clearer.

        #### What's Changed

        ##### Fix
        - **Source Evaluation:** "Reassess outdated" could report "Evaluation completed" without actually changing anything — the underlying database write wasn't guaranteed to finish before the run was marked done, and a source that couldn't be installed or loaded couldn't replace its old outdated results with a current one. Both are fixed: every result is now saved before a run finishes, and a source-level failure now correctly clears out its old outdated entries. A run that truly couldn't process anything now says so plainly instead of claiming success.

        ##### Improve
        - **Source Evaluation:** past-evaluation rows show a short status line (fit plus when it was last checked) instead of a long line packing in every technical detail at once. The full details (catalogue fit percent, metadata confidence, evidence strength) are still available by expanding the row.
        - **Recommendation Settings:** several summaries (same-manga matching, enrichment amount, group preview size) were shortened to plainer, first-glance wording, with the more technical detail still included. Two summaries that displayed incorrectly on some devices ("1–2" and "2×") were corrected.
        - Added a running readability audit covering every KMK-added Recommendation Settings and For You surface, to track what's been reviewed and what's still pending.

        ## KMK-Recs v0.8.14-fix1

        Recommendation Settings structural completion: "Sources and languages" is now "For You sources" with languages moved to Management and diagnostics, the For You preview shows actual manga covers and titles, and Source Evaluation's outdated-reassessment display is clearer.

        #### What's Changed

        ##### New
        - **For You sources:** "Preview For You" now shows a real read-only snapshot of your last For You refresh — Top Picks and source rows with manga covers and titles, not just source order and status. Tapping a card does nothing; it never searches sources, uses the network beyond loading the already-cached covers, or opens a manga.

        ##### Improve
        - **Recommendation Settings:** the "Sources and languages" screen is renamed "For You sources" and no longer includes language selection — language now lives in Management and diagnostics, since it affects the whole recommendation system, not just source ordering. Every language setting is preserved, only relocated. The Preview For You action moved to the top of the screen.
        - **Recommendation Settings search:** "language" now opens Management and diagnostics; category-level search results no longer show a subtitle that just repeats the row's own title (for example "Taste and filters" under "Taste and filters").
        - **Source Evaluation:** the "Reassess outdated" action now appears before the excluded-sources note instead of after it, and that note is now a collapsed "N outdated source(s) outside this run" disclosure instead of an always-visible card, so it no longer reads as if it were blocking the reassess action.
        - **Source Evaluation:** the "Show details" / "Hide details" toggles on past-evaluation rows, and the row overflow menu, now use a normal-size tap target instead of a small icon-only one.

        ## KMK-Recs v0.8.14

        Recommendation Settings structural cleanup: "For You" and "Matching and versions" are no longer separate destinations, and Source Evaluation's stale-reassessment reporting and layout are clearer.

        #### What's Changed

        ##### Improve
        - **Recommendation Settings:** the index is now exactly five sections — Sources and languages, Taste and filters, Source Evaluation, Sources to try, and Management and diagnostics. "For You" and "Matching and versions" are gone as separate destinations; every control they had (rating visibility, hide known manga, minimum chapter count, find other versions, Best Version preview, group preview budget) moved into Taste and filters or Management and diagnostics. Nothing was removed, and search still finds every control in its new place.
        - **Source Evaluation:** a past-evaluation row that shows "Outdated" now says whether it can actually be reassessed right now ("reassess needed") or is outside the current run ("not included in this run", e.g. because you've since installed it or changed your language filter) — it no longer suggests an action the reassess button can't perform. The completion message after a reassessment run now states exactly how many outdated sources were left outside the run and why.
        - **Source Evaluation:** the first screen is less cluttered — batch size stays visible, but the skip/include-explicit toggles, candidate counts, and installer-mode selector are now tucked behind "More setup options" / "Installer details" disclosures. The installer selector still shows automatically whenever the installer isn't ready, so setup problems are never hidden.
        - **Source Evaluation:** a few technical terms ("probe", "eligible") in primary screen text were replaced with plainer wording ("test", "ready to test").

        ## KMK-Recs v0.8.13-fix1

        Recommendation Settings and Source Evaluation clarity fixes: search no longer flickers, settings are grouped more intuitively, a read-only For You preview was added, and stale source evaluation reporting is now truthful.

        #### What's Changed

        ##### New
        - **Source Priority:** added a read-only "Preview For You layout" action showing source order, enabled/liked/disliked/boosted state, and the last For You status for each source, without leaving settings or making any network request.

        ##### Improve
        - **Recommendation Settings search:** results no longer briefly dim/fade while typing. Category and control search results are now visually distinguishable, and searches like "fil" no longer return several identical-looking Source Evaluation rows.
        - **Recommendation Settings:** language selection moved from For You to Source Priority, since it controls which sources can appear in For You at all. Group recommendation preview limits moved from For You to Matching and versions, since they control group-recommendation previews, not the main For You page. Every previously available language and setting is still present, only relocated.
        - **Source Evaluation:** the stale/outdated reassessment button and count now always describe only what can actually be reassessed. The completion message after a reassessment run now says when some outdated sources remain excluded, instead of reading as if everything had been processed.
        - **Source Evaluation:** quarantine/blocked diagnostics are now collapsed by default to reduce clutter, while the counts stay visible and one tap away.

        ## KMK-Recs v0.8.13

        For You recommendation quality fix: sources no longer get stuck showing "No matches" because of an old search strategy, and generic or unrelated results are reduced.

        #### What's Changed

        ##### Improve
        - **For You:** a source is no longer permanently stuck on the least reliable search strategy after it previously worked once but has since stopped finding anything. For You now tries a fresh strict-to-lenient search chain instead of repeating the same fruitless search every time.
        - **For You:** stopped repeatedly checking extra pages for a broad text search that already found nothing on the first page, so a source with no matches reports that faster instead of quietly working through more pages first.
        - **For You:** a manga now needs an actual matching preferred or liked tag to appear in For You. A source you generally like is still ranked higher when it also has a real tag match, but liking a source alone no longer makes an otherwise unrelated manga show up.
        - **For You:** the source status line for "No matches" and "Filtered" now explains more clearly why a source didn't show results.

        ## KMK-Recs v0.8.12-fix1

        Corrective follow-up to v0.8.12: a serious app error during For You recommendation loading could be silently hidden instead of being reported.

        #### What's Changed

        ##### Fix
        - **For You:** a rare, serious device error (such as the app running critically low on memory) while loading recommendations from a source, or while trying the new Popular-catalogue fallback, could previously be silently treated as "no matches" instead of being surfaced. Recoverable per-source problems (a broken or incompatible extension, for example) are still contained the same way as before — only genuinely serious errors are no longer hidden.

        ## KMK-Recs v0.8.12

        Recommendation Settings structural follow-up: language selector fix, another settings move, clearer counts and errors, and a targeted For You false no-match fix.

        #### What's Changed

        ##### New
        - **Recommendation Settings:** Same Manga Matching and Best Version Preview moved out of Source Priority into their own "Matching and versions" destination, so Source Priority is only about ranking and enabling sources.
        - **Taste and Tags:** stored tag preferences (Preferred/Blocked/Disliked/Other) now reveal 10 more at a time with a distinct "Show all" action, instead of one "Show more" that revealed everything at once.

        ##### Improve
        - **Recommendation Settings:** the language chip list is no longer limited to languages your currently installed sources happen to use — your selected languages and any language an available (not yet installed) extension supports also stay visible, so a selected non-English language never silently disappears from the list.
        - **Taste Suggestions:** rating-derived Preferred/Blocked suggestion groups now use the same bounded "Show 10 more / Show all / Show fewer" reveal as stored tag preferences, instead of revealing every suggestion at once.
        - **Source Evaluation:** the stale-reassessment button and remaining count already reflected only the actionable (workable) rows, not the total outdated count — this pass confirmed that behavior in code and added a small test-covered helper so the completion message can never be shown just because the remaining rows happen to be unreachable.
        - **For You / Matching and versions:** row errors from a broken or incompatible extension no longer show a raw internal wrapper class name (e.g. "RecoverableSourceRuntimeException: ...") — they show the same plain, non-fatal message every other classified source error already used.

        ##### Fix
        - **For You:** a source whose text search only matches manga titles (not genres) could report "No matches" for every tag-based search attempt even though its Popular catalogue actually has matching manga — the same manga a user would find by browsing manually. For You now gives the source's Popular catalogue one bounded, single-page chance before reporting no matches, going through the exact same blocked/adult/known/rated/seen/min-chapter filtering as every other result.

        ## KMK-Recs v0.8.11

        Recommendation Settings navigation cleanup and UI/layout polish across the Komikku fork's added screens.

        #### What's Changed

        ##### Improve
        - **Recommendation Settings:** the "Source Evaluation" and "Background, network, and installer behavior" entries opened the exact same screen — the duplicate entry is gone, and Source Evaluation search results no longer repeat the same title/summary for different controls.
        - **Recommendation Settings:** ordinary toggle, list, and action rows now match the rest of Komikku's settings screens.
        - **Taste and Tags:** rating-derived tag suggestions are now grouped into Preferred and Blocked sections, each showing up to 10 with Show more / Show fewer, so Blocked suggestions are no longer buried behind a long Preferred list.
        - **Sources To Try:** each suggestion card now keeps Install as the one visible action; Dismiss, like/dislike, and quality marks moved into a single overflow menu so cards no longer crowd phone screens.
        - **Source Priority:** each row now keeps the drag handle, source name, and enable switch visible; like/dislike for For You moved into an overflow menu.
        - **Source Evaluation:** the screen is now organized into named sections (Run evaluation, Installer and cleanup, Reassessment, For You search compatibility, Diagnostics and recovery) instead of one long mixed page, and For You search compatibility now explains how it differs from catalogue evaluation.
        - **For You:** Loved/Liked/Disliked are now grouped under one "Rated manga" menu and Export Top Picks moved to the overflow menu, so the top bar has fewer always-visible icons. Refresh and Settings remain immediately visible; nothing was removed or hidden behind long-press.

        ##### Fix
        - **Loved/Liked/Disliked:** selecting multiple versions and choosing "Group" could appear to do nothing — the versions were actually grouped, but the screen didn't refresh to show it until something unrelated (like rating another manga) happened to reload the list. Grouping now updates the display immediately and shows a confirmation message.

        ## KMK-Recs v0.8.10-fix9

        Historical changelog cleanup, UI conformance polish, and source-runtime hygiene after the fix8 stability pass.

        #### What's Changed

        ##### New
        - **Taste and Tags:** Preferred, Disliked, and Blocked tags are now grouped into clearly separated sections instead of one continuous list, so Blocked tags are no longer buried behind a long Preferred list. Each group shows up to 10 tags with Show more / Show fewer.

        ##### Improve
        - **What's New:** every historical KMK-Recs entry now uses the same What's Changed / New / Improve / Fix structure as recent releases, instead of the older flat bullet-list format. No entry, version, or detail was removed — only reorganized for readability.

        ##### Fix
        - **Source runtime:** a Suwayomi tracking client read that bypassed the shared source-runtime safety boundary is now guarded the same way other source client/header reads are, so a broken source can no longer crash Suwayomi sync.
        - **Source runtime:** a few background migration and library-update paths that caught every error too broadly now correctly let fatal app/VM errors through instead of silently swallowing them, while still isolating ordinary per-item failures.

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

        #### What's Changed

        ##### New
        - after you finish the latest available chapter of a manga, an optional prompt offers Love/Like/Dislike/Not Interested/dismiss. Rating never fires twice for the same completion, and never appears while the reading schedule is restricting you.
        - after rating from that prompt, if the manga has confirmed other versions on different sources, you're offered the chance to rate those too, using the same version-matching tool already used elsewhere.

        ##### Improve
        - Recommendation Settings now opens to a concise index of sections (For You, Source Priority, Taste and Tags, Source Evaluation, Sources To Try, Background/Installer, Management) instead of one long screen. Source Evaluation opens directly from the index. No preference or existing setting behavior changed — this is navigation only.

        ##### Fix
        - (v0.8.7-fix1) the reading schedule could grant a fresh reading allowance every time you left a restricted reader and opened a different manga — restriction was never actually enforced across reader sessions, only shown as a toast. Reading is now genuinely blocked, immediately, for a reader opened while restricted; only a reader that was already open when a restriction begins may finish its current chapter, with no extra chapter and no way to bypass it via manual chapter selection, deep links, rotation, or backgrounding.
        - Source Evaluation could show sources as "Outdated — reassess needed" that "Continue reassessing outdated" would never actually process (for example, because you'd since installed that source, changed your language filter, or disliked it) — silently doing nothing instead of explaining why. This now shows a clear explanation instead of a confusing no-op.

        ## KMK-Recs v0.8.7

        Reading schedule fix, and rated/settings UI refinements.

        #### What's Changed

        ##### New
        - the reading schedule time picker now follows your device's 12-hour or 24-hour display preference instead of always showing 24-hour time.
        - you can now add a "Whole day" window instead of only a specific time range.
        - reading schedule windows can now be edited in place (pencil icon) instead of only deleted and re-added.
        - the Rated (Loved/Liked/Disliked) bulk-selection bar's More menu now offers "Select all in group" when your current selection belongs to one confirmed group, not only from the per-item menu.
        - Clear Rating and Mark Not Interested (bulk selection) now show an Undo option right after you confirm them.

        ##### Improve
        - in the reading schedule dialog, Save commits your changes and Cancel (or tapping outside/back) discards them — tapping outside no longer accidentally saves a half-finished edit.
        - Daily recommendations, Ratings and Known Manga, Tags, Source Priority, Same-Manga Matching, and Sources To Try in Recommendation Settings now show a one-line summary of their current state (selected languages, visibility mode and chapter minimum, preferred/blocked tag counts, enabled source count and top source, results-per-source and preselect setting, suggestion count) so you can see what's configured without expanding every section.

        ##### Fix
        - the reading schedule's "Add window" flow could silently do nothing after you picked days — no time picker would appear. This is fixed: selecting weekdays now reliably opens the time picker.

        ## KMK-Recs v0.8.6

        Group recommendation loading performance and a configurable preview size.

        #### What's Changed

        ##### New
        - "Initial results per extension" setting in Recommendation Settings (5/10/15/20/30, default 10) controls how many manga cards each extension shows in a group (Loved/Liked/Rated group) recommendation preview. Open a source row to keep loading more through the existing full search.

        ##### Improve
        - group recommendation source rows now run with a bounded number active at once instead of starting every eligible extension simultaneously, so results appear progressively instead of all-or-nothing.
        - a single slow or failing extension in a group recommendation now times out and shows its own row error instead of the possibility of stalling the screen.
        - This setting is scoped to group recommendation previews only — it does not change For You's existing "Results per source" setting, and normal global search remains completely unaffected and uncapped.

        ##### Fix
        - leaving the recommendations screen mid-load now reliably cancels the in-flight search instead of continuing in the background.
        - cancelling a group recommendation load (navigating away) is no longer occasionally shown as a row error.

        ## KMK-Recs v0.8.5

        Optional reading schedule.

        #### What's Changed

        ##### New
        - an optional reading schedule (Settings > Reader > Reading schedule) that can restrict or allow reading during chosen days and times, entirely inside this app.
        - add or remove any number of recurring time windows, each covering one or more days of the week, using the same time picker already used elsewhere in the app.

        ##### Improve
        - like the active-reading timer, a reading-schedule window never interrupts a chapter you're already reading — it lets you finish the chapter first.

        ## KMK-Recs v0.8.4

        Active-reading timer.

        #### What's Changed

        ##### New
        - a reading timer in the reader (tap the timer icon in the bottom bar). Choose 15, 30, or 60 minutes, or set a custom duration.
        - optional warnings before time is up, and a choice to finish your current chapter — optionally one extra chapter — instead of being cut off mid-story.

        ##### Improve
        - manual chapter selection and going back to the previous chapter never count toward the one-extra-chapter allowance; only continuing forward naturally after time is up does.
        - the timer only counts while you're actively reading — it pauses automatically the moment you leave and resumes when you come back, unless you paused it yourself.

        ## KMK-Recs v0.8.3

        Recommendation Settings reorganized.

        #### What's Changed

        ##### Improve
        - Recommendation Settings is now organized into For You behavior, Source priority, Source evaluation, Source management, and Discovery/cache management sections, matching the rest of the app's settings layout.

        ## KMK-Recs v0.8.2

        Configurable For You results per source.

        #### What's Changed

        ##### New
        - For You now has a "Results per source" setting (5/10/15/20/30, default 10) in Recommendation Settings.

        ##### Improve
        - your top 3 boosted sources always show at least 20 results, even if you pick a smaller number for other sources.

        ## KMK-Recs v0.8.1-fix4

        Source quality marks and final cleanup.

        #### What's Changed

        ##### New
        - you can now mark a source as poor or too explicit as a source/library, separate from your For You recommendation preference. Use it for sources whose overall library is bad, misleading, or too lewd/hentai-heavy — even if some individual results looked fine.
        - sources marked poor or too explicit are hidden from Sources To Try and Source Evaluation by default, and no longer feed For You once installed. Nothing is deleted — past evaluation history is preserved and can be shown again with "Show disliked sources".
        - a "Clear source quality marks" recovery action in Recommendation Settings removes every source/library mark at once.

        ##### Improve
        - What's New and other in-app text no longer reference build-channel wording; XML string comments and documentation were normalized.

        ##### Fix
        - after a stale/outdated Source Evaluation reassessment finishes, the screen now shows a clear "Outdated reassessment complete" message instead of just quietly removing the action.

        ## KMK-Recs v0.8.1-fix3

        Source Evaluation continuation fix.

        #### What's Changed

        ##### Improve
        - the unassessed queue and the outdated-reassessment queue track progress independently — switching between them, or changing batch size, never discards either queue's progress. A "Restart outdated reassessment" action lets you explicitly start that queue over from the beginning.

        ##### Fix
        - after a Source Evaluation batch finished reassessing sources, rows still marked "Outdated — reassess needed" could no longer be continued into — the screen reported 0 unassessed extensions remaining even though outdated rows were still visible in the results list.
        - reassessing stale/outdated sources is now a separate, first-class queue with its own "Reassess outdated (N)" / "Continue reassessing outdated (N remaining)" action and its own progress cursor, so it can no longer be silently absorbed into the "already evaluated" count.

        ## KMK-Recs v0.8.1-fix2

        Version visibility and sync validation.

        #### What's Changed

        ##### Fix
        - opening Loved Manga no longer crashes with a "GetCrossSourceGroupPrimary" dependency error. The three primary-version interactors added in v0.8.0 were never registered, so any screen that needed them (Loved Manga, Linked Versions, backup, restore) could crash.
        - the KMK-Recs What's new dialog and version number are now shown reliably, and never suppressed by the regular Komikku update dialog on the same launch.
        - syncing your chosen primary version between devices now rejects malformed rows the same way restoring a backup already did.

        ## KMK-Recs v0.8.1-fix1

        Rated Manga and Source Evaluation polish.

        #### What's Changed

        ##### New
        - Source Evaluation rows can show an expandable "Details" section — enrichment counts, metadata sample counts, and liked/disliked/blocked/adult-risk counts — explaining why a source got its verdict.

        ##### Improve
        - the linked-version list now explains that the star sets the primary version, since that action isn't in the card menu directly.

        ##### Fix
        - removing a linked version from the version list now asks for confirmation first, matching the same safeguard already used elsewhere.
        - your chosen primary version per group is now included in backup, restore, and sync — it used to be lost when restoring or syncing.
        - pressing "Select" in Loved/Liked/Disliked now enters selection mode without selecting anything. Long-press still selects the item you pressed.

        ## KMK-Recs v0.8.0

        Rated Manga bulk selection and group actions.

        #### What's Changed

        ##### New
        - selection mode adds a bottom action bar — Change rating, Clear rating, Group, and More (Mark not interested, Remove from group).
        - each card has a menu with Recommendation, Rating, and Group actions, including "See group recommendations" (only shown for confirmed linked groups with 2+ versions), "Find other versions", and "Favorite other versions".
        - a focused "Linked versions" screen shows every version in a confirmed group — source, language, title, rating, favorite status, installed/missing status, last updated, and which one is the primary version.
        - you can set a primary version per group, which controls the cover/title shown in the rated list. Recommendations still use the whole group's metadata, not just the primary version.
        - Merge Selected Into Group, Remove From Group, and Ungroup actions, all confirmed before running. Merging never happens automatically by title — only by manual selection.

        ##### Improve
        - long-press in Loved / Liked / Disliked now enters bulk selection instead of opening recommendations. A "Select" button in the app bar does the same thing.
        - clearing a rating never deletes the manga, favorites, history, or version links.

        ## KMK-Recs v0.7.47

        Source Evaluation tag enrichment and scoring fix.

        #### What's Changed

        ##### Improve
        - Source Evaluation scoring is now version 3; all previous results are treated as outdated until reassessed.

        ##### Fix
        - Source Evaluation now fetches full manga details for a bounded set of catalogue samples that are missing tags on the list page, instead of scoring sources only on what Popular/Latest happen to expose. Sources that were previously marked weak just because their list pages omitted tags are re-evaluated fairly.
        - sources with too little usable tag evidence are now shown as "needs manual review" instead of being confidently marked weak.
        - sources with a mix of liked tags and blocked/adult tags (BL/GL/adult/explicit signals) can no longer reach "Strong fit" purely because of broad positive tags — blocked and adult-risk evidence now gates the verdict.
        - source evaluation results scored under older app versions are now clearly shown as outdated and no longer sort above current, freshly-checked results.

        ## KMK-Recs v0.7.46

        Polish and OCR safety cleanup.

        #### What's Changed

        ##### Improve
        - OCR errors are now shown as safer, clearer messages instead of raw technical text.
        - OCR page-error logs no longer include manga titles or chapter names.
        - OCR index cleanup controls are easier to find — you can now clear OCR text for a single chapter or manga right from a search result, plus a quick way to clear empty/failed rows.
        - Source Evaluation error rows show clearer, translated messages instead of raw technical text.
        - Documentation and versioning were refreshed.

        ## KMK-Recs v0.7.45

        Final v0.7 release. This closes the v0.7 feature line and hardens the fork for everyday use.

        #### What's Changed

        ##### Improve
        - Loved/Liked/Disliked Rated Manga views now show grouped (duplicate-collapsed) display by default. You can still switch to a flat list, and that choice now survives background refreshes.
        - Recommendation Settings now shows how often each source has contributed to your Top Picks (e.g. "Great fit · 5"), when it has contributed at least once.
        - Clarified that OCR ships in the same build as KMK-Recs (it was never actually a separate build, despite older docs saying so) — this is now documented accurately, including its local-only storage and backup/export exclusion.

        ##### Fix
        - checking a non-installed source's For You search compatibility now requires the Private installer. If Private isn't available, those checks are skipped with a clear message instead of silently using Shizuku/Current, where a leftover extension could previously go unnoticed.
        - Source Evaluation error rows no longer show raw internal exception text — errors are now classified into a small set of clear categories (network unavailable, timed out, unsupported, internal error).

        ## KMK-Recs v0.7.44


        #### What's Changed

        ##### New
        - Added test coverage for the shared query-attempt policy and for the background For You search compatibility job's conflict-guard decision.

        ##### Improve
        - Loved/Liked group recommendations now actually search using every linked version's combined tags and titles, not just the primary version's — a source that can't match the primary version's exact tags now falls back through progressively looser tag attempts, then a title search across every linked version, before giving up on that row.
        - group recommendations now honor the same source language/priority/disabled/disliked-source rules as For You, and the same favorite/rated/Not Interested/known/min-chapter visibility rules — previously they only excluded exact duplicates and Not Interested titles.
        - Source Evaluation rows are more compact — detailed error/reason text is now collapsed behind a "Show details" toggle instead of always taking up space, and the remaining hardcoded English error labels are now translatable. The Shizuku setup card's action buttons and the compatibility-check action buttons now wrap instead of crowding on narrow phones.
        - Loved/Liked group recommendations ("Recommendations from this") now use the same provider/extension row layout as a single manga's Recommendations page, seeded by every confirmed linked version of that title instead of just one. Cross-extension genre search rows now search by the group's combined tags, not one version's tags alone. Versions already in the group never appear as recommendations. The old single-grid group recommendations screen was removed.
        - "Mark as seen" is renamed "Not interested" to honestly describe what it does — the title still stays hidden from For You and group recommendations, but similar manga are now also mildly deprioritized (much less strongly than Dislike). Your existing seen list is unaffected; nothing needs to be re-marked.

        ##### Fix
        - `RecommendationCandidateVisibilityPolicyTest` and two other test classes that build a favorite manga no longer fail with an unrelated Injekt error — the whole recommendation test suite is clean again.
        - For You no longer gives up on a source after one overly strict tag search — it now tries up to three progressively looser attempts (same shared policy grouped recommendations use) before marking a source as having no results, and no longer "locks in" a search strategy that produced zero results.
        - "For You search compatibility" checks (missing/outdated/re-check all) now run as a background job, the same way full Source Evaluation does. Leaving the Source Evaluation screen no longer stops a check in progress — it keeps running, shows its own notification with progress, and you can tap the notification or reopen Source Evaluation to see current progress or cancel it.
        - Source Evaluation and For You search compatibility can no longer run at the same time — starting one while the other is active now shows a clear message instead of letting them race over the same temporary extension installs.

        ## KMK-Recs v0.7.42-fix2


        #### What's Changed

        ##### New
        - a "Recheck outdated" action lets you re-check only sources with stale results, without re-running every source like "Re-check all" does.

        ##### Fix
        - Source Evaluation's sort menu no longer offers "Search reliability" — that sort ordered a field the app never actually measures. It's replaced with "For You compatibility", which truthfully orders sources by their real search-compatibility result: current good results first, then weak, then no-matches, then errors, then outdated results, then not-yet-checked, then ineligible sources last.
        - a source whose search-compatibility result is out of date (an older scoring version, or expired) now clearly shows "Outdated - recheck" instead of silently displaying its old result as if it were still current.
        - "Re-check all" now actually rechecks every eligible source, including ones with outdated results — previously it could silently skip them.
        - Best Fit sort no longer uses the retired "search reliability" figure as a tie-breaker; it now uses the real current search-compatibility result as a true tie-breaker, only after catalogue fit is equal.

        ## KMK-Recs v0.7.42-fix1


        #### What's Changed

        ##### Fix
        - the manual "Check search compatibility" / "Re-check all" actions and diagnostics counts in Source Evaluation now use the exact same eligibility rules as automatic evaluation. A source with inconclusive catalogue evidence is no longer skipped by the manual check just because its individual verdict looked unfavorable.
        - search compatibility results computed before this update are now automatically re-checked instead of silently being treated as still current.
        - Source Evaluation rows no longer show a misleading "search 0%" figure — that field was never a real search measurement. Rows now show catalogue metadata confidence instead.

        ## KMK-Recs v0.7.42


        #### What's Changed

        ##### Improve
        - Source Evaluation now scores catalogue fit (Popular/Latest samples) separately from search compatibility, instead of pooling both into one number. The redundant, less accurate tag-search probe that used to run inside Source Evaluation was removed — search compatibility is measured only by the dedicated probe, and its results are now labeled "For You search: Good/Great/…" instead of the misleading "Recommendations: Good/Great/…".
        - Catalogue-fit matching now uses the exact same taste-matching logic as For You itself, including tag aliases and blocked-tag hard exclusion, instead of a simpler approximation.
        - A source with sparse Popular/Latest tag metadata is no longer penalized as a poor fit — a new confidence signal tracks how much usable tag data was actually sampled, and a source with inconclusive catalogue evidence is still checked for search compatibility rather than being silently skipped.
        - Existing Source Evaluation results are automatically treated as outdated and eligible for reassessment, since the scoring rules changed.

        ## KMK-Recs v0.7.41


        #### What's Changed

        ##### Improve
        - Cancellation is never recorded as a failed retry.

        ##### Fix
        - cached and remembered candidates now obey exactly the same visibility rules as live results. A manga hidden by the minimum-chapter filter, or that is known/rated/seen/favorited/disliked, can no longer reappear just because it came from the cache or discovery memory.
        - group-seeded recommendations no longer run short. Hidden candidates (already in library, rated, seen, below the chapter minimum, or part of the seed group) no longer use up the 20-result budget — the search keeps scanning bounded chunks until it collects 20 visible results or exhausts its safe limits.
        - discovery retry state is now truthful. A page that used up all its retries is recorded as permanently exhausted (keeping its diagnostic) instead of a generic error, and a due retry of the final page (page 20) can run while a brand-new page past the cap (page 21) is never created.
        - unknown extension errors are now treated as permanent rather than retried forever. Only genuine connectivity, I/O, and timeout failures are retried; HTTP 4xx and unexpected extension crashes stop after one record.

        ## KMK-Recs v0.7.40


        #### What's Changed

        ##### Improve
        - Discovery retry: transient network failures (timeout, no connection, I/O errors) on additional-page probes now record retry metadata (attempt count, next-retry timestamp, failure kind) and are retried with bounded exponential backoff (5 min → 10 min → 20 min, capped at 24 hours, max 3 attempts). HTTP 4xx errors and UnsupportedOperation are recorded as permanent and not retried.
        - Discovery timeout: additional-page probes are now bounded to 20 seconds. A timeout is recorded as a retryable failure so the page is retried on the next refresh.
        - Group-seeded recommendations now use the user's real language preference, source priority order, and liked/disliked source exclusions — the same source selection logic that For You uses — instead of always searching all English sources without priority or exclusions.
        - Group-seeded recommendations now apply the full For You visibility filter (favorites hidden, rated hidden per visibility setting, seen manga hidden, known manga hidden when enabled, min-chapter threshold) using a shared policy object.
        - For You and group-seeded recommendations now share `RecommendationSourceSelector` and `RecommendationCandidateVisibilityPolicy` so both flows produce consistent results.

        ##### Fix
        - extra-page discovery candidates were dropped when For You memory was empty — they are now merged and ranked with the same path as page-1 and remembered candidates, so no valid candidates are silently lost.
        - the same merge defect applied to the cached path — the early-return short-circuit on empty memory has been removed from both paths.

        ## KMK-Recs v0.7.39


        #### What's Changed

        ##### Improve
        - For You rolling discovery: each refresh now tracks which pages were evaluated in a new `recommendation_discovery_progress` table (migration 57). Empty, filtered, and error pages are recorded so they are not retried on every refresh — only unevaluated pages are probed.
        - Discovery advances progressively: after page 1 is evaluated, page 2 is probed on the next refresh; after page 2, page 3; and so on up to 20 pages per source/query before the discovery path is considered exhausted.
        - Reset For You discovery history now also clears the progress table so discovery restarts from page 1 on the next refresh.
        - Rated group recommendations remain unchanged (full cross-source group seeding was already implemented in v0.7.38 via GroupRecommendationSeedBuilder).

        ## KMK-Recs v0.7.38


        #### What's Changed

        ##### Improve
        - For You discovery memory: discovered candidate manga are now remembered between refreshes. On the next For You refresh, remembered candidates are merged with newly discovered ones and re-ranked against the current taste profile — strong past candidates are not lost just because they didn't appear in the latest batch.
        - Additional page discovery: after page-1 results are recorded, For You now probes page 2 (and page 3 on subsequent refreshes) to expand the candidate pool beyond what a single search page returns. Each additional page is bounded to 20 new candidates.
        - Group-seeded recommendations: the recommendation seed is now built from ALL confirmed linked versions of a manga, not just the one being viewed. Sparse group members (fewer than 2 local genres) are enriched with live metadata — bounded to 8 members, 5 seconds per member, 20 seconds total — before the weighted tag seed is computed. Tags contributed by more group members receive higher weight in scoring.
        - Group seed scoring: a new GroupSeedRecommendationScorer adds a seed-tag score on top of the personal taste score — tags that appear across 2+ group members score 1.0 (strong match), single-member tags score 0.3 (weak match), both scaled by the tag's group weight.
        - Reset For You discovery history: new button in Recommendation Settings → Management clears all remembered For You candidates without affecting ratings, seen manga, or source settings.

        ## KMK-Recs v0.7.37


        #### What's Changed

        ##### Improve
        - Group-seeded recommendations no longer stay on the loading spinner indefinitely. The total load time is now bounded by a 45-second screen timeout; if time runs out with partial results they are shown, and if none are found the screen shows the empty state.
        - Localization of each candidate (NetworkToLocalManga) now has a 5-second per-candidate timeout. Candidates that take too long are skipped without blocking subsequent results.
        - The screen now stops early once 20 results are collected rather than continuing to query all remaining sources and plans.
        - Reduced source and candidate caps to values appropriate for a quick single-manga drill-down: 5 sources max, 8 raw candidates per source.
        - Source-aware deduplication: candidates are now keyed by (sourceId, url) pairs instead of url alone, so the same path on different sources is no longer incorrectly merged.
        - Cross-source link group rating exclusivity: if a confirmed linked group contains members with different ratings (e.g. one version Loved, another Liked), the group now appears in only one rating tab — the tab that matches the most-recently-updated member's rating. Previously, both tabs could show the same grouped manga.
        - CancellationException is now re-thrown inside the per-source try/catch so navigating away from the screen correctly cancels the in-flight recommendation load.

        ## KMK-Recs v0.7.36


        #### What's Changed

        ##### Improve
        - Rated Manga UI parity: Liked and Disliked manga screens now share the full Loved Manga feature set — sort chips, group-duplicates toggle, version badges, cross-source link management, and export. Export filenames are rating-appropriate (kmk_liked_manga.json, kmk_disliked_manga.json).
        - Discoverability: each Loved/Liked card shows a small Explore icon overlay (top-left) that opens "Recommendations from this" without requiring a long-press. Long-press still works as a shortcut. Disliked cards omit the overlay to avoid confusing semantics.
        - Library toolbar shortcuts: Loved Manga, Liked Manga, and Disliked Manga are now accessible from the Library overflow menu, not only from Browse > For You.

        ##### Fix
        - Crash fix: group-seeded recommendations no longer crash when tapping a result. All candidates are now localized via NetworkToLocalManga before being shown, so MangaScreen always opens a valid local DB id. Candidates that fail localization are silently skipped.

        ## KMK-Recs v0.7.35


        #### What's Changed

        ##### Improve
        - Rated Manga: "Liked" and "Disliked" views accessible from the For You toolbar (thumbs-up and thumbs-down icons). Show manga rated LIKE or DISLIKE respectively; same installed-source filter and grid as Loved Manga.
        - Group-seeded recommendations: long-press any Loved Manga card to open "Recommendations from [title]" — searches installed sources using the seed manga's genre tags (boosted in the taste profile), filters out the seed group members from results.

        ##### Fix
        - `NetworkOnMainThreadException` crash in the recommendation-quality probe — all source calls (`getFilterList`, `getSearchManga`, `getMangaDetails`) now run on the IO dispatcher. The exception is classified as an internal probe error and does not reduce source quality scores.

        ## KMK-Recs v0.7.34


        #### What's Changed

        ##### Improve
        - Source Evaluation: per-source error category labels on rec-quality ERROR rows (e.g. "Ext not found", "Install failed", "Search timed out") so the failure type is visible without expanding the error detail (C1).
        - Source Evaluation: "Retry" button in the post-run summary card when the run ended due to connectivity loss, allowing one-tap restart without resetting results (C2).
        - Source Evaluation: profile-changed banner shown when the taste profile has grown by 5+ entries since evaluation was last run, prompting a re-run for fresh results (C3).

        ## KMK-Recs v0.7.33


        #### What's Changed

        ##### Improve
        - Best Version compare: fullscreen page dialog state survives screen rotation and back-stack navigation via three rememberSaveable primitives (I1).
        - Best Version compare: tap to dismiss fullscreen preview when zoomed at 1× (I2).
        - Best Version compare: per-thumbnail Fit/Crop toggle button using ContentScale.Fit or ContentScale.Crop so narrow covers display correctly without cropping (I3).

        ## KMK-Recs v0.7.32


        #### What's Changed

        ##### Improve
        - For You source stats: 30-day rolling window for fit labels — recent run rates are preferred over all-time rates when at least 3 runs have occurred in the last 30 days, giving more accurate Great/Good/Mixed/Error labels as source quality changes over time (D1).
        - For You source stats: tracks how many times each source contributed manga to the final Top Picks row, surfaced in the SourceFitStats as topPicksContributionCount (D2).

        ## KMK-Recs v0.7.31


        #### What's Changed

        ##### Improve
        - Recommendation Settings: configurable enrichment cap — the number of candidate manga per source enriched with full metadata can now be set to 1/2/3/5/10/15/20 (default 5). Boosted sources always get 2× the cap. Higher values are more accurate but slower (J).

        ## KMK-Recs v0.7.30


        #### What's Changed

        ##### Improve
        - Loved Manga: new "Manage Cross-Source Links" screen accessible from the app bar, showing all cross-source link groups with expand/collapse and per-link or whole-group delete (B).

        ## KMK-Recs v0.7.29


        #### What's Changed

        ##### Improve
        - Recommendation Settings source priority list: each source now shows a "Last checked: X ago" timestamp below the status label when at least one run has been recorded, using the system's relative-time formatter (A1).
        - For You screen: pull-to-refresh gesture on the recommendations list — pulling down restarts the For You run with the same settings (A2).
        - Loved Manga screen: live updates when your Loved Manga list changes in the database — ratings applied on other screens are reflected immediately without requiring a manual refresh (A3).
        - Source Evaluation safety diagnostics: quarantine/blocked-package row can now be collapsed/expanded by the user (A4).

        ## KMK-Recs v0.7.28


        #### What's Changed

        ##### Improve
        - Backup/restore: For You "Seen" dismissals are now included in Komikku backups (proto field 626). The restore is additive — any dismissals accumulated after the backup was created are preserved, never cleared. Old backups without the new field restore cleanly with no seen keys (proto default = empty list). 6 round-trip tests added in SeenMangaKeyBackupTest covering: single-key round-trip, multi-key round-trip, merge-new-keys, no-duplicate-on-overlap, empty-backup-leaves-existing-unchanged, empty-existing-produces-backup-set.

        ## KMK-Recs v0.7.27


        #### What's Changed

        ##### Improve
        - Recommendation Settings: new "Best Version History" browser in the Management section. Shows all past Best Version decisions grouped by the origin manga, with the selected source name, selected title (when different from origin), chapter used, and date confirmed. Each record can be deleted individually (removes only the quality signal — does not affect library entries, ratings, or cross-source links). The action bar has a "Clear all history" button with a confirmation dialog.

        ## KMK-Recs v0.7.26


        #### What's Changed

        ##### Improve
        - Recommendation Settings: new "Minimum chapter count" filter in the Ratings & Known Manga section. When set above 0, For You hides results whose locally-known chapter count is below the threshold (options: Off, 5, 10, 20, 50). Manga with no locally-known chapters (untracked) are never filtered so you don't miss newly-released series. Changing the setting invalidates the For You cache so results refresh on the next load.

        ## KMK-Recs v0.7.25


        #### What's Changed

        ##### Improve
        - For You now detects when the device has no internet connection before starting recommendation queries. Instead of silently failing or showing a cascade of per-source errors, the screen shows a clear "No internet connection" message with a Retry button. Tapping Retry re-checks connectivity and resumes normally if online.

        ## KMK-Recs v0.7.20


        #### What's Changed

        ##### Improve
        - For You blocked-tag filtering is now applied at query time on sources that expose TriState genre filters, not only post-fetch. When a source's filter list contains a TriState entry matching a blocked tag (by name or built-in synonym), it is set to exclude before the query runs. Post-fetch filtering always remains active as the fallback, so no recommendations slip through regardless of filter support.
        - Internal: added 7 tests for the blocked-genre query-time exclusion logic in GenreFilterMapperTest (exclude TriState, no downgrade of INCLUDE entries, no crash on missing filter, no mutation of CheckBox, forceTextOnly bypasses exclusion, built-in synonym matches blocked label, empty filterList safety).
        - Local Source: confirmed keep-excluded. Local Source (id 0) is intentionally excluded from For You source searches. A separate synthetic "Already Known" row based only on local DB metadata remains a future option if there is a clear use case.

        ## KMK-Recs v0.7.19


        #### What's Changed

        ##### Improve
        - Recommendation Settings: the source fit badge in the Source Priority list now reflects rolling history across For You runs rather than just the most recent run. After at least 3 runs, each source receives a label (Great fit, Good fit, Mixed, No matches, Often filtered, Often errors) based on its reliability, shown-rate, and average visible candidates over time.
        - Recommendation Settings: a "Suggest priority order based on fit" button appears below the source list after at least 3 sources have 3+ run history. Tapping it moves sources with the best rolling fit to the top while leaving no-data sources at the bottom. You can reorder further after applying.
        - Internal: rolling source fit stats (run count, shown/no-match/filtered/error/hidden-by-duplicate counts, total visible candidates, timestamps) are now persisted in a preference after each For You run. The accumulator skips Disabled/OutsideAttemptLimit statuses so only actually-searched sources are counted.

        ## KMK-Recs v0.7.18


        #### What's Changed

        ##### Improve
        - Source Evaluation: if the device loses connectivity mid-batch, the runner now stops cleanly with a "Connection lost mid-run" status rather than hanging or failing with a generic error. Extensions already completed are saved; the options section re-appears so you can retry when back online.
        - Source Evaluation: extension repositories unavailable at startup (empty available-extension list after candidates load) now surface a non-blocking "Extension list unavailable" info card so the cause of zero candidates is clear.
        - Source Evaluation: the Management section now shows "Clear N dismissed suggestion(s)" and "Clear N disliked suggestion source(s)" buttons when hidden suggestions exist, allowing recovery without navigating to Settings.
        - Source Evaluation: blocked packages that have a newer available version now show a "Newer version available — remove block to test" hint in the Blocked Packages dialog so you can decide whether to re-test.
        - Localization: all remaining hardcoded English strings in the Source Evaluation screen error display have been moved to typed ScreenErrorKey variants backed by KMR string resources (candidate load failure, offline error, crash recovery message).

        ## KMK-Recs v0.7.17


        #### What's Changed

        ##### Improve
        - Bundle import: when multiple available extensions could match a missing source (same package name, different signing keys), the import preview now shows an "Ambiguous source" badge and lists each candidate with its own install button so you can choose which one to install. Previously the first match was silently picked.
        - Source Evaluation: installer mode descriptions (Private, Shizuku, Current) are now localization strings. The copy has been clarified: Private describes silent internal cleanup; Shizuku explains system-package install and Android uninstall prompts; Current explains confirmation dialogs per install/uninstall.
        - Localization: all remaining hardcoded user-visible English strings in the bundle import and source evaluation installer flows have been moved to KMR string resources.
        - Internal: added 6 tests for the new bundle source ambiguity resolver (resolveAvailableExtension: NotFound when no pkgName, NotFound when no match, Unambiguous on exact pkgName+sigHash, Ambiguous on multiple pkgName+sigHash, pkgName-only fallback when sigHash unmatched, Ambiguous on pkgName-only with multiple extensions).

        ## KMK-Recs v0.7.16


        #### What's Changed

        ##### Improve
        - Backup: Best Version quality signals (your "Best Version" picks across sources) are now included in backups and sync. Picks restore by origin+selected source identity; duplicates are skipped.
        - Backup/Sync: Fixed cross-source manga link groups missing from device sync payload — they were backed up correctly but not synced between devices.
        - Source Evaluation: switching to Shizuku or Current installer mode now resets the one-time consent so the risk notice re-surfaces when the risk profile changes.
        - Source Evaluation: after a Shizuku or Current-mode run, extensions that could not be cleaned up silently now show an "Uninstall N left-behind extension(s)" button in the summary card so you can trigger the system prompts in one tap without going to Browse > Extensions. Previously the cleanup count was shown but the status was never accurately recorded — both bugs are now fixed.
        - Source Evaluation: if the app was killed mid-evaluation (process death) and a temporary extension was left installed, reopening Source Evaluation now shows a persistent warning banner with an "Uninstall leftover extension" button so you can clean it up without going to Browse > Extensions.
        - Internal: added migration round-trip tests (KmkMigrationTest — 9 execution tests against real in-memory SQLite, 4 structural tests), proto collision tests (TasteBackupRoundTripTest — 5 new tests covering proto fields 620–625), and OCR backup exclusion tests (KmkOcrExclusionTest — 3 structural tests confirming OCR text is never included in backup or sync payloads). Bundle import now throttles metadata fetches at 200ms per item during bulk adds.

        ## KMK-Recs v0.7.15


        #### What's Changed

        ##### Improve
        - Source Evaluation: evidence strength labels (Strong evidence, Moderate evidence, Weak evidence, Low confidence) and verdict badges (Strong Fit, Worth Trying, Neutral, Weak, Poor Search, Explicit, Ecchi, Rejected, Error, Review) are now localization strings instead of hardcoded English.
        - Source Evaluation: last-evaluated age label (Last evaluated today / Last evaluated N days ago) is now a localization string.
        - Loved Manga: when "Group clear duplicates" is on but no clear duplicates are found, a compact note ("No clear duplicates found.") is shown so it is clear the control is working.

        ## KMK-Recs v0.7.14


        #### What's Changed

        ##### Improve
        - Recommendation Settings: removed the redundant "Daily recommendations" header above the language selector (the language selector IS the daily rec control).
        - Recommendation Settings: source fit badge labels ("Great fit", "Good fit", "Low fit", etc.) and suggestion expand/collapse buttons are now using localization strings instead of hardcoded English.
        - Recommendation Settings: added a compact hint below the hide-known-manga toggle: "Refresh For You after changing ratings, tags, source preferences, or known-manga settings."
        - Same manga matching: preselect setting summary now notes "Turn off if a source returns too many wrong matches."
        - Source status ordering in Recommendation Settings: verified correct — sources with results, then no results, then disliked. Tests already covered this.
        - Same-manga matching settings verified: Love/Like/Dislike/Seen/Favorite/Best-version searches all read the cap and preselect preferences. Normal global search is not capped.
        - Loved Manga: added sort options (Most recent, Oldest first, Title A–Z, Source). Sort applies before grouping.

        ## KMK-Recs v0.7.13


        #### What's Changed

        ##### Improve
        - Recommendation Quality checks now enrich raw search results with manga details before scoring. Many extensions return search results without genre/tag metadata; the check now fetches details for up to 5 candidates per query plan so the scorer can see actual tags. This matches how For You already works and prevents good sources from being falsely marked as weak or no-matches.
        - Recommendation Quality checks now show a compact diagnostics summary after running: how many sources were checked, how many had install/load issues, how many had search errors, how many had no results, how many were weak, and how many were good recommenders.
        - Recommendation Quality rows now show a subdued reason for No matches and Weak outcomes (not just Error). For example: "Search returned no results for taste profile tags" or "3 result(s) had no genre even after enrichment".
        - Source Evaluation failure categories are now classified: install/load issues and search errors are counted separately in the diagnostics summary.

        ## KMK-Recs v0.7.12


        #### What's Changed

        ##### Improve
        - Recommendation Quality error results now show a brief reason in red below the quality label. Previously every failure showed only "Error" with no detail. Now the reason is visible — for example "Install failed or timed out", "Extension not found in available sources", or the specific search error from the probe.
        - Recommendation Settings reorganized per Phase 6/7 plan: Daily recommendations at top (language selector), then Ratings and known manga, Tags, Source priority, Same manga matching (moved up from below Sources To Try), Source status, Management (Sources To Try and cleanup actions), and Experimental — Source Evaluation at the bottom.
        - Source Evaluation section in Recommendation Settings is now labeled "Experimental — Source Evaluation" to clarify it is an advanced tool separate from daily recommendation controls.

        ##### Fix
        - Fixed: stale v0.8.0 version markers in source comments corrected to v0.7.11.

        ## KMK-Recs v0.7.11


        #### What's Changed

        ##### Improve
        - Source Evaluation now requires a one-time acknowledgment before the first run. A warning dialog explains that evaluation temporarily installs extension packages, runs network probes, and then attempts to remove them. After acknowledging, the dialog does not appear again.
        - "View evaluation warning" button (next to Copy Diagnostics in Source Evaluation) lets you re-read the warning at any time without starting an evaluation. Tapping "I understand, continue" from this path never starts evaluation.
        - After an evaluation batch completes, the summary now shows a warning in red if any extensions could not be cleaned up automatically and need a manual uninstall from Browse > Extensions.
        - "Reassess updated extensions" and "Continue next batch" now show the same first-run consent dialog before starting, so the warning is always seen before any install/probe work begins.

        ##### Fix
        - Fixed: confirming the warning from "View evaluation warning" no longer accidentally starts evaluation.

        ## KMK-Recs v0.7.10


        #### What's Changed

        ##### Improve
        - Non-installed promising sources are now resolved from the complete available extension repository, temporarily installed, probed, and cleaned up as intended.
        - Installed extension matching is now more robust: the app tries exact sig+pkg, then pkg-only, then sig+name, then name+lang before reporting an error, so extensions that had a signing key change are still found.
        - Source lookup within an installed extension is now more robust: the app tries exact source id, then name+lang, then name, then normalized name+lang, then normalized name before reporting an error.
        - Error messages now identify the specific stage that failed (available list unavailable / extension not found / extension match ambiguous / install failed / source not found after install / etc.) instead of collapsing all failures into a generic error.

        ##### Fix
        - Fixed Recommendation Quality checks for Strong Fit and Worth Trying sources. Previously, running "Evaluate recommendations" when no evaluation batch had been run in the current session caused every non-installed promising source to immediately become an Error with "Extension not found in available sources". Now the full available extension list is loaded directly from the extension manager instead of relying on the screen-local candidate pool.

        ## KMK-Recs v0.7.9


        #### What's Changed

        ##### New
        - Added defensive state handling: if a selected Best Version key no longer resolves to a real candidate, the dialog is dismissed automatically rather than rendering an empty or broken state.

        ##### Improve
        - Sampled page thumbnails in the Best Version preview are now tappable. Tap any thumbnail to open it fullscreen.
        - Fullscreen page preview supports pinch-to-zoom and pan. Close with the close button or back.
        - Closing fullscreen preview returns to the comparison screen with all previews, candidates, and selected chapter intact.

        ##### Fix
        - Fixed the Cancel button in the Best Version migration confirmation dialog. Pressing Cancel now correctly closes the dialog and returns to the comparison screen. Previously, Cancel set a fake sentinel key that kept the dialog stuck open.

        ## KMK-Recs v0.7.8


        #### What's Changed

        ##### New
        - Added Find best version in the rating menu on manga detail pages. Use it to search for the same manga across installed sources, visually compare sampled chapter pages, and migrate or copy to the better version.

        ##### Improve
        - Recommendation Quality checks now work for promising sources (Strong Fit, Worth Trying) that are not currently installed. The app temporarily loads the extension to run the check, then cleans it up silently.
        - Errors in the Recommendation Quality section now reflect the real failure reason — install failure, source not found, probe error — instead of marking every non-installed source as "Error".
        - Source Evaluation installed-extension display now updates immediately after extensions are installed or uninstalled, without needing to reopen the screen.
        - Recommendation Settings source priority list now updates immediately after extensions are installed or uninstalled, without needing to restart the app.
        - Choose a chapter and preview a sample of pages side by side from each candidate source before deciding.
        - Migrate or copy to the selected better version — chapter history, categories, tracking, and downloads are preserved using Komikku's existing migration behavior.
        - Configure how many results to show per source for same-manga searches (1, 2, 5, or 10) in Recommendation Settings > Same manga matching. This applies to Love/Like/Dislike/Seen/Favorite/Best-version searches. Normal global search is unaffected.
        - Configure whether same-manga results start selected or unselected by default.
        - Configure preview sample size (2, 5, or 10 pages) and whether early pages are skipped in the preview.

        ## KMK-Recs v0.7.7


        #### What's Changed

        ##### Improve
        - Installed evaluation rows can now be shown or hidden without reopening Source Evaluation. When installed rows are hidden, the toggle shows "Show installed". When installed rows are visible, the toggle shows "Hide installed". Tapping it again immediately hides installed rows.
        - Promising sources (Strong Fit and Worth Trying) now have a visible Recommendation Quality section in Source Evaluation. The section shows how many promising sources have not yet been checked and provides an "Evaluate recommendations" button to run recommendation-quality checks for those sources directly from the screen. A "Re-check all" option re-runs checks for all promising sources including already-checked ones.
        - Recommendation-quality results are now easier to see. Promising rows without a result show "Recommendations: Not checked" so it is clear a check is available but has not run yet.

        ## KMK-Recs v0.7.6


        #### What's Changed

        ##### Improve
        - Source Evaluation batches now support continuation. After a batch completes, a "Continue next batch (N remaining)" button appears to evaluate the next slice without restarting from scratch. Progress is stored per filter fingerprint and expires after 7 days.
        - Past evaluation results now hide extensions that are currently installed by default. Use "Show installed" to reveal them. A "Hidden installed: N" count shows how many are hidden.
        - Source Evaluation now runs a second-stage recommendation-quality probe for STRONG_FIT and WORTH_TRYING sources. The probe queries each source with your top taste tags, scores the raw results against your profile, and stores a verdict (Great / Good / Mixed / Weak / No matches / Error / Too little evidence). Results appear as a third line on each past evaluation row.

        ## KMK-Recs v0.7.5


        #### What's Changed

        ##### New
        - Add selected manga to your library directly from the import preview. Duplicate and category behavior matches Komikku's existing library flows.

        ##### Improve
        - Export Top Picks, For You source rows, and Loved Manga as a shareable JSON bundle. Use the menu in the For You tab, Top Picks screen, or Loved Manga screen.
        - Import a recommendation bundle from a JSON file. Go to Settings > Data storage > Import recommendation bundle.
        - The import preview shows each manga's status: ready to add, already in library, missing source, or needing manual review.
        - If the bundle requires an extension that is not installed, Komikku offers to install it directly from the import preview.

        ## KMK-Recs v0.7.4


        #### What's Changed

        ##### New
        - Added SourceRecommendationFitEligibility and SourceRecommendationFitScorer pure helpers. These enable future bounded recommendation-quality probing for STRONG_FIT and WORTH_TRYING sources only. Actual probe execution is deferred.

        ##### Improve
        - Source Evaluation now records which extension version was installed at evaluation time. If a new version of the extension is released, the Source Evaluation screen shows a notice with the count of updated extensions and a "Reassess updated extensions" button to re-evaluate them.

        ##### Fix
        - Fixed stale documentation: Favorite other versions and alternate-title cross-extension matching were incorrectly marked as deferred. Both are fully implemented as of v0.7.0.

        ## KMK-Recs v0.7.3


        #### What's Changed

        ##### Improve
        - Loved Manga now hides entries from sources that are no longer installed.

        ## KMK-Recs v0.7.2


        #### What's Changed

        ##### Improve
        - Improved Loved Manga duplicate grouping by using confirmed matching versions first.
        - Group clear duplicates now handles more same-manga versions while avoiding title-only merges.

        ## KMK-Recs v0.7.1


        #### What's Changed

        ##### Improve
        - Seen other versions now remains available after marking the current manga as seen.

        ##### Fix
        - Fixed a crash when opening Seen other versions from manga details.

        ## KMK-Recs v0.7.0


        #### What's Changed

        ##### New
        - Added Loved Manga view: tap the heart icon in the For You tab to see all manga you have rated Love, sorted by most recently loved.

        ##### Improve
        - Enable "Group clear duplicates" in the Loved Manga view to collapse the same manga from multiple sources into one entry. Grouping uses exact title and description matching — no taste ratings are deleted or merged.
        - A version count badge shows on grouped entries when multiple sources have the same manga.

        ## KMK-Recs v0.6.20


        #### What's Changed

        ##### New
        - Added "Reassess sources" button in Source Evaluation to re-evaluate sources against your current taste profile at any time.
        - Added a Source management section in Source Evaluation with options to reset disliked sources, reset the reassessment baseline, and clear the seen manga list.
        - Added "Mark as seen" action to the rating menu on manga detail pages. Marking a manga as seen removes it from For You recommendations without affecting your taste ratings.
        - Added "Seen other versions" action in the rating menu to mark alternate versions of a manga across extensions as seen using the same cross-extension matching workflow.

        ##### Improve
        - Recommendation Settings now shows source status grouped as: sources with results first, sources with no results below them, and disliked sources last.
        - Source Evaluation now tracks how many manga ratings have been added since the last evaluation. When 100 or more new ratings are counted, a prompt appears recommending reassessment.
        - Source Evaluation past results now show evidence strength (Strong, Moderate, Weak, Low confidence) and how long ago each source was last evaluated.
        - Marking manga as seen invalidates the For You recommendation cache so changes take effect on the next refresh.

        ## KMK-Recs v0.6.19


        #### What's Changed

        ##### New
        - Added a low-confidence warning when the taste profile does not have enough rated manga or tags for reliable personalized scoring. Evaluation still runs, but source-fit scores may be less accurate.

        ##### Improve
        - Source Evaluation now continues running in the background after leaving the screen. A foreground notification shows the current extension being evaluated. Tapping the notification opens the Source Evaluation screen directly.
        - Shizuku setup controls are now hidden by default when using Private (recommended) or Current installer. They appear automatically when Shizuku mode is selected, and can be expanded with a "Shizuku setup" link.
        - Quarantined extensions and blocked extension packages are no longer shown as prominent cards at the top of Source Evaluation. They are now accessible via a compact "Safety diagnostics" section below the evaluation options.
        - Candidate count now shows "N unassessed extensions remaining" when skip-already-evaluated is on, making it clear how many extensions still need evaluation.
        - Starting evaluation now shows an error message when the device has no internet connection instead of silently doing nothing.

        ## KMK-Recs v0.6.18


        #### What's Changed

        ##### New
        - Added package-level extension load quarantine: known crash-causing extensions are now blocked before their code is loaded into Komikku, preventing native SIGSEGV crashes at startup.
        - New "Blocked Extensions" card in Source Evaluation shows which packages are blocked, with "Allow again" option per-package and "Allow all" to remove all blocks.

        ##### Improve
        - Digital Comic Museum is now blocked from loading (removable from Source Evaluation > Blocked Extensions if you want to re-enable it after uninstalling and reinstalling a fixed version).
        - Source Evaluation diagnostics now include blocked package count and static guard status.

        ## KMK-Recs v0.6.17


        #### What's Changed

        ##### New
        - Added a removable quarantine seed for Digital Comic Museum, based on repeated native crash logs showing it in the fatal network stack.

        ##### Improve
        - Source Evaluation crash recovery now runs during app startup, so a crashing extension can be quarantined before you reopen the Source Evaluation screen.
        - Improved unsafe-extension guidance: the Quarantined Extensions dialog now explains that if a quarantined installed extension still crashes Komikku outside Source Evaluation, you should uninstall or disable it manually from the Extensions screen.

        ## KMK-Recs v0.6.16


        #### What's Changed

        ##### New
        - Added "Copy Diagnostics" button to Source Evaluation for sharing state info when investigating issues.

        ##### Improve
        - Source Evaluation now survives fatal native crashes (SIGSEGV / stack overflow) from extension code. A probe marker is written to SQLite before each risky network call; if the process dies, the marker is detected on next screen open, the extension is quarantined, and the marker is cleared.
        - Quarantined extensions are automatically skipped in future Source Evaluation candidate selection and shown in a "Quarantined Extensions" card on the Source Evaluation screen.
        - You can view, individually remove, or clear all quarantined extensions from the Source Evaluation screen.
        - Candidate diagnostics now shows how many extensions are hidden due to quarantine.

        ## KMK-Recs v0.6.15


        #### What's Changed

        ##### New
        - Added sorting for Source Evaluation past results: Best fit (default), Newest, Source name, Extension name, Search reliability, and Explicit risk.

        ##### Improve
        - Improved Source Evaluation result rows: compact score info (fit %, search %) shown as subtitle; error messages are truncated to prevent layout issues.
        - "Clear all" on Source Evaluation past results now asks for confirmation before deleting. The dialog clarifies that only evaluation cache is cleared.

        ##### Fix
        - Fixed Source Evaluation results stability after larger evaluation batches (50+ sources). Malformed or duplicate result rows are now sanitized before rendering, and stable Compose keys prevent list identity conflicts.

        ## KMK-Recs v0.6.14


        #### What's Changed

        ##### Improve
        - Source Evaluation now treats slow source timeouts as per-source failures instead of cancelling the whole run. A single hanging source (such as Asia2) will be marked as an error and skipped; evaluation continues with the next candidate.
        - Source priority reset is now protected by a confirmation dialog and moved out of the top bar. Tap "Restore default source order" near the Source Priority section and confirm before the order is changed.

        ## KMK-Recs v0.6.13


        #### What's Changed

        ##### New
        - Added diagnostic logging (logcat tag "KMK SourceEvaluation install:") to help verify install and cleanup paths when investigating prompt behavior.

        ##### Improve
        - Source Evaluation now uses Private installer for silent cleanup by default. Evaluated extensions are temporarily stored inside Komikku and removed automatically without any Android uninstall prompt.
        - Extensions already installed before evaluation started are detected and skipped rather than accidentally uninstalled during cleanup.
        - Choosing Shizuku or Current installer (which system-installs extensions) now shows a warning dialog before starting, with options to switch to Private or continue with Android prompts enabled.
        - Private installer chip is now labeled "Private (recommended)" to make the recommended choice clear.
        - Shizuku setup card shows "Shizuku is ready, but Private is recommended for silent cleanup" when Shizuku is selected and Private is available, and offers a "Use Private" action to switch.

        ## KMK-Recs v0.6.12


        #### What's Changed

        ##### Improve
        - Source Evaluation now evaluates the broad pool of available non-installed extensions matching your language and preferences, instead of only the small Sources To Try candidates list.
        - Candidate diagnostics: shows how many extensions are eligible, how many are hidden due to already-evaluated or explicit-content filters.
        - Shizuku UX improvements: status now distinguishes "selected and ready" from "selected but not ready yet"; Refresh status button lets you force a state re-read; screen auto-refreshes Shizuku state on resume.

        ##### Fix
        - Fixed skip-already-evaluated filter: now correctly matches evaluations at extension level rather than source level, so evaluated extensions are properly hidden.

        ## KMK-Recs v0.6.11


        #### What's Changed

        ##### New
        - Added Shizuku setup controls to Source Evaluation: install/open shortcuts, temporary use for evaluation, stop-using action, and uninstall shortcut through Android.

        ##### Improve
        - Source Evaluation now reads real Shizuku status (installed, running, permission granted) instead of always treating Shizuku as unavailable.

        ## KMK-Recs v0.6.10


        #### What's Changed

        ##### Fix
        - Fixed a crash when opening Source Evaluation from Recommendation Settings.

        ## KMK-Recs v0.6.9


        #### What's Changed

        ##### New
        - Added defensive fallback: if source_evaluation is temporarily unavailable, Sources To Try renders using metadata-only suggestions instead of crashing. The fallback logs the error and emits an empty evaluation list.

        ##### Improve
        - No evaluation data is deleted. No uninstall/reinstall required.

        ##### Fix
        - updating from a pre-v0.6.8 APK no longer crashes For You / Recommendation Settings with "no such table: source_evaluation". A missing SQLite migration (47.sqm) has been added to create the source_evaluation table on upgrade.

        ## KMK-Recs v0.6.8


        #### What's Changed

        ##### New
        - Added Source Evaluation: temporarily install non-installed extensions one at a time, probe their content (popular, latest, and search results), score how well they match your taste profile, and store the verdict in the database.

        ##### Improve
        - Source evaluation verdicts are now used by Sources To Try: strong-fit sources appear at the top (score 0.90), worth-trying sources at 0.75, explicit-heavy sources are filtered when block-explicit is on, and rejected sources are hidden.
        - Evaluation uses Private installer by default so install and cleanup are silent. Shizuku and Current installer are also supported with appropriate batch-size limits.
        - The installer override is temporary — your global extension installer preference is never changed by a Source Evaluation run.
        - Batch sizes: 10, 25, 50, and 100 (100 shows a warning). 500/1000 batches are not yet implemented.
        - Explicit-heavy and ecchi-heavy verdicts remain distinct — they are never merged.
        - Open Source Evaluation from Recommendation Settings > Source Evaluation.

        ## KMK-Recs v0.6.7


        #### What's Changed

        ##### New
        - Added "Block explicit porn/hentai sources" toggle under Settings > Browse > NSFW content.

        ##### Improve
        - When enabled, clearly explicit sources (NHentai, E-Hentai, ExHentai, Pururin, Tsumino, 8Muses, HBrowse, HentaiFox, and others identified by name or package) are hidden from Browse > Sources, Browse > Extensions (available), and Sources To Try.
        - Ecchi-only sources are not blocked by this setting. Only sources with explicit hentai/porn keywords or known explicit IDs are affected.
        - Installed explicit extensions remain visible and manageable so they can be updated or uninstalled.
        - The setting is off by default; existing users are not surprised by hidden sources on update.

        ## KMK-Recs v0.6.6


        #### What's Changed

        ##### New
        - Added selective uninstall to Browse > Extensions: tap "Select extensions" in the overflow menu to enter selection mode, choose installed/untrusted extensions, then tap "Uninstall selected (N)" to uninstall them.

        ##### Improve
        - A confirmation dialog shows how many extensions will be uninstalled before proceeding. Android may ask you to confirm each uninstall separately.
        - Available (non-installed) extensions and extensions actively downloading/installing cannot be selected.
        - Cancel exits selection mode and clears the selection without uninstalling.
        - Normal extension install, update, open, trust, and update-all behavior is unchanged.

        ## KMK-Recs v0.6.5


        #### What's Changed

        ##### New
        - Added selective install to Sources To Try: tap Select to enter selection mode, choose specific suggested sources, then tap Install selected (N) to install only those.

        ##### Improve
        - Cancel exits selection mode and clears the selection.
        - Install visible suggestions remains available as the quick action.

        ## KMK-Recs v0.6.4


        #### What's Changed

        ##### Fix
        - Fixed "Install visible suggestions" in Sources To Try: all visible suggestions now install reliably instead of stopping after the first one or two.

        ## KMK-Recs v0.6.3


        #### What's Changed

        ##### New
        - Added "Install visible suggestions" button in Sources To Try to install all currently visible suggestions in one tap.
        - Added scope note below Sources To Try explaining the difference: installed source dislikes affect For You only; Sources To Try dislikes hide future suggestions.

        ##### Improve
        - Individual install buttons are disabled while an install is in progress for that suggestion.
        - Clarified Like/Dislike accessibility labels: installed source thumbs are labeled "Like for For You" / "Dislike for For You"; suggestion thumbs are labeled "Like source suggestion" / "Dislike source suggestion."
        - Source priority ordering persistence verified: ordering tests extended to cover malformed-id robustness.

        ## KMK-Recs v0.6.2


        #### What's Changed

        ##### New
        - Added Like and Dislike controls to installed source rows and Sources To Try suggestion rows in Recommendation Settings.

        ##### Improve
        - Liked non-installed sources appear in Sources To Try even without metadata similarity, and rank above neutral suggestions.
        - Disliked non-installed sources are hidden from Sources To Try.
        - Disliked installed sources are excluded from For You recommendation searches (thumbs down = avoided, not uninstalled).
        - Dismiss remains separate from Dislike: dismiss means "not now," dislike means "avoid long-term."
        - Liked/disliked preferences persist across restarts. Changes immediately update Sources To Try and affect the next For You run.

        ## KMK-Recs v0.6.1


        #### What's Changed

        ##### Improve
        - Sources To Try is now selective: a source only appears when it has meaningful similarity to one of your already-installed sources.
        - Language, repo, base URL, and generic keywords no longer qualify a source on their own.
        - If no strong suggestions exist, the section shows an honest empty state instead of listing all available extensions.
        - Language preference changes now trigger immediate suggestion refresh.

        ## KMK-Recs v0.6.0


        #### What's Changed

        ##### New
        - Added Sources To Try in Recommendation Settings: suggests non-installed extensions worth trying for For You based on your recommendation language and installed source metadata.

        ##### Improve
        - Suggestions are labeled Potential fit or Worth trying — not proven good until installed and tested with For You.
        - Each suggestion shows the reason it was surfaced and has Install and Dismiss actions.
        - Install uses the existing extension install flow. After install, the source disappears from suggestions automatically.
        - Dismissed suggestions stay hidden until you refresh extensions.

        ## KMK-Recs v0.5.1


        #### What's Changed

        ##### Improve
        - Reduced matching results to 2 per source to keep the confirmation list tighter and reduce wrong-match risk.
        - The manga you are rating no longer appears as a candidate in the matching list.

        ## KMK-Recs v0.5.0


        #### What's Changed

        ##### New
        - Added Love other versions, Like other versions, and Dislike other versions actions to the rating menu on manga detail pages.

        ##### Improve
        - Searching for matching versions is capped per source so the confirmation list stays manageable, while normal global search remains unchanged.
        - Sources are filtered by your recommendation language preference and sorted by source priority order.

        ## KMK-Recs v0.4.4


        #### What's Changed

        ##### Improve
        - Recommendation Settings now shows when a source\'s results were hidden by duplicate handling, rather than showing it simply as matched.
        - Source statuses in Recommendation Settings now update automatically when For You finishes running.
        - Source status list now clearly labels statuses as coming from the last For You refresh.
        - Top Picks drill-down now shows a loading message when opened while For You is still running, and an empty-state message when no results are available yet.

        ## KMK-Recs v0.4.3


        #### What's Changed

        ##### Improve
        - For You now keeps filling from your source priority list so empty sources do not take up final recommendation slots.
        - Recommendation Settings now shows the latest For You status for each source, including no matches, filtered results, errors, and sources not yet reached.
        - Top Picks can now be opened to view up to 50 ranked recommendations.
        - Local KMK-Recs What\'s New now only shows user-facing recommendation changes.

        ## KMK-Recs v0.4.2


        #### What's Changed

        ##### New
        - Added local KMK-Recs What\'s New notes (this screen).

        ##### Improve
        - Top Picks duplicate matching now merges on exact title + author OR exact title + artist independently, so entries are correctly unified even when only one contributor field is shared across sources.
        - Hide known manga setting is now consistent with cached recommendations: changing the setting invalidates the cache and cached rows are filtered the same way as fresh results.

    """.trimIndent()
}
// KMK <--
