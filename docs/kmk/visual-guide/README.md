# Visual guide to KMK

This tour follows KMK's main workflows on a tablet. Each section explains what the screen is for, what its controls do, and where to find more detailed steps.

## For You

![For You page with personalized manga rows, matching tags, and anonymized source labels](for-you-evaluation-mode.png)

At the top of For You, you can see topic shortcuts, personalized rows, matching tags, and manga cards. Evaluation Mode hides source names, while the covers and titles remain visible so the recommendations are still meaningful.

## Top Picks and preference actions

![Top Picks with selected manga and the complete preference action set](top-picks-preference-actions.png)

Long-pressing a card starts selection mode. Love, Like, and Dislike remain directly available, while More contains Not Interested and Clear rating. This keeps the four preference choices in one workflow instead of presenting Not Interested as a separate feature.

## Manga preferences and collections

![Manga preference actions with Not Interested selected for another version](not-interested-other-versions.png)

Love, Like, Dislike, and Not Interested are peer preferences. The selected state is shown on the manga action surface, and each preference has a matching collection or filter. Clearing Not Interested is an explicit reversible action; it is not treated as an absence of a rating. The Top Picks image above shows the bulk-action surface, while this detail image shows the state on a manga-related screen.

## Related manga

![Related manga results grouped under neutral source labels](related-manga-results.png)

Related-manga results load in separate source rows. A short or unavailable row does not remove useful results returned by the other sources.

## Recommendations from a confirmed group

![Group recommendations built from a confirmed multi-version manga group with neutral source labels](group-recommendations.png)

A confirmed linked group can seed its own recommendation search. Community suggestions and extension searches remain separated into source lanes, so a sparse or unavailable lane does not erase the useful results from other group members.

## Share recommendation bundles

![For You with the Export Top Picks action open](recommendation-bundle-export.png)

The For You overflow menu exports the current Top Picks as a recommendation bundle. Individual source rows and rated collections provide the same kind of export from their own menus. Import opens a review step first; it does not add manga to the library until the reader confirms the reviewed selection.

## Browse compatibility

![Browse page with neutral source labels while Evaluation Mode is enabled](browse-evaluation-mode.png)

Evaluation Mode also hides source names in Browse. The rest of Komikku's navigation stays the same.

## Evaluation Mode and safe review

Evaluation Mode changes presentation labels used for review images, including source, extension, repository, and selected taste labels. It does not change saved manga, requests, identifiers, or actions. The For You and Browse images in this guide show the result: the recommendation or browse context remains understandable while identity-bearing labels are neutralized. The review process and its limits are documented in [Sharing screenshots and exported files safely](../feature-guides/sharing-screenshots-and-exported-files-safely.md).

## Recommendation settings

![Recommendation settings page divided into clear feature sections](recommendation-settings.png)

The settings page groups related controls into For You sources, taste and filters, source evaluation, sources to try, and management and diagnostics. Each row leads to a focused settings page.

## Taste and filters

![Taste and filters with known-manga controls, minimum chapters, preferred tags, and blocked tags](taste-and-filters.png)

This page controls which known or rated manga remain eligible, the minimum chapter count, preferred and blocked tags, and suggestions learned from saved ratings. The controls are ordinary settings rather than hidden ranking constants.

## Management and diagnostics

![Management and diagnostics settings with grouped controls and short summaries](management-diagnostics.png)

This page keeps maintenance and diagnostic controls together. Its summaries use short, neutral descriptions of the current settings.

## Source-quality diagnostics

![Expanded source-quality diagnostics with neutral source labels and aggregate observations](source-quality-diagnostics.png)

The expanded view shows sample counts, metadata coverage, match counts, blocked candidates, and confidence using neutral source labels. Each source can be expanded independently, so a weak or incomplete observation remains distinguishable from a successful evaluation.

## Source Evaluation

![Source Evaluation page showing progress counts, warnings, and reassessment controls](source-evaluation.png)

This screenshot shows Source Evaluation partway through a run, with progress, readiness information, and reassessment actions. It uses general categories instead of raw source names or error messages.

## Sources to try

![Sources to try with ranked suggestions and neutral source labels](sources-to-try-evaluation-mode.png)

The populated list shows ranking, sorting, installation, and selection controls. Evaluation Mode replaces the source names with neutral labels.

## Linked versions

![Matching manga grouped under neutral source labels](linked-versions-evaluation-mode.png)

This screen shows confirmed matching manga before the reader links versions or applies an action. Love, Like, Dislike, and Not Interested form one preference system; the Top Picks selection screen presents all four together instead of using one preference as a stand-in for the other three.

## Compare versions before migrating

| Choose comparable chapters | Review page previews |
| --- | --- |
| ![Best Version comparison with chapter choices for each neutralized source](best-version-chapter-selection.png) | ![Best Version preview with comparable pages from neutralized sources](best-version-preview-comparison.png) |

Best Version starts from versions the reader has already confirmed as related. The first screen lets the reader choose comparable chapter samples. The second keeps each preview independent, so a missing or failed preview does not erase the other comparison results. Choosing a different version continues through Komikku's existing migration flow; the comparison itself does not silently change the library. See [Finding and comparing manga versions](../feature-guides/finding-and-comparing-manga-versions.md) for the complete flow.

## Reading schedule

![Reading schedule dialog with recurring time windows](reading-schedule.png)

The schedule can block reading during selected windows or allow reading only inside them. Existing windows have explicit edit and remove controls, and changes are not applied until the reader saves the dialog.

Reader controls also include the optional timer and the completion handoff. The timer can stop or restrict reading without changing chapter progress. The reader's clock or schedule action opens the same schedule configuration described above, so the setting is discoverable from the place where it matters.

## Jump to last read

Long chapter lists have a Jump to last read action in the manga chapter toolbar. When a saved read position exists, it scrolls to that chapter without opening it or changing progress. When no position exists, the action explains that there is nothing to jump to. Chapter names and reading history are intentionally explained in text rather than shown in a public image.

## Completion preference

| Choose a preference | Continue to matching versions |
| --- | --- |
| ![Reader completion prompt offering Love, Like, Dislike, and Not Interested](reader-completion-preference.png) | ![Follow-up asking whether matching versions should receive the same preference](reader-linked-version-follow-up.png) |

After the final chapter, the reader may choose Love, Like, Dislike, or Not Interested, or close the prompt without changing the manga. A second, separate question controls whether the same preference should be considered for confirmed matching versions.

## Search downloaded pages with OCR

![OCR Search Downloads showing an indexed local library and its indexing controls](ocr-search-downloads.png)

The index summary shows how many downloaded pages have been processed and how much space the local text index uses. Readers can index all downloads, force a fresh pass, or clear index records without deleting the downloaded pages themselves. Search results open the matching reading context.

## Undo a supported local action

![Action History showing a completed manga preference change with an Undo action](action-history-undo.png)

Action History records a supported local change only after it succeeds. The Undo button restores the previous value when the current state still matches the recorded action. This captured change was undone through the same screen, and the app confirmed that the manga was restored.

## Extension export

![Extension export confirmation explaining what the file contains](extension-export-confirmation.png)

The confirmation explains that an extension package is executable code and that ratings, history, and other app data are not included. Android's document flow chooses the destination after the reader confirms.

## Extension operations and failure isolation

Extension operations cover loading, install consent, cancellation, removal, export, and cleanup confirmation. Android owns the package and document boundaries; KMK does not silently install an extension or delete an unrelated file. If one extension fails or is cancelled, the remaining source work keeps its own result and can be retried. The export image above shows the public consent boundary; the complete operational flow is in [Managing and isolating extensions](../feature-guides/managing-and-isolating-extensions.md) and [Handling source and extension failures](../feature-guides/handling-source-and-extension-failures.md).

## Back up and restore app data

![Data and storage settings with backup, restore, scheduling, and privacy guidance](backup-and-restore.png)

The Data and storage page keeps manual backup, restore, automatic frequency, last-backup status, and restore progress controls together. It reports only that a storage location is configured in this view, then gives a separate warning that backup files may contain sensitive data.

KMK's backup additions travel through Komikku's normal backup flow. Supported preference values, recommendation settings, linked-version groups, primary-version choices, source-quality observations, and source-evaluation state are included when selected. OCR text, temporary review state, and unsupported external account data remain outside the backup contract. Restore reports partial or failed records instead of presenting an incomplete restore as successful. See [Preserving KMK data in Komikku backups](../feature-guides/preserving-kmk-data-in-backups.md).

## Review KMK changes in the app

![KMK What's New showing the current grouped change history](kmk-whats-new.png)

KMK What's New groups the current and earlier fork releases, summarizes each release, and separates new behavior from fixes. The current version is shown at the top so readers can tell which notes apply to the installed build.

## Source runtime and integration boundaries

Source runtime handling keeps ordinary extension failures, cancellation, and retry separate from successful source rows. Deep links, WebView transitions, document exports, tracker boundaries, and local mutations are validated before they are accepted. Errors stay short and useful without displaying raw URLs, local paths, credentials, exception objects, or private response content. These are interaction and safety guarantees rather than useful screenshot subjects, so their complete success, rejection, cancellation, and cleanup paths are documented in [Validating links, actions, and file cleanup](../feature-guides/validating-links-actions-and-file-cleanup.md).

## Complete feature-family coverage

The visual guide follows the full KMK feature contract. A family is listed here even when its safest public representation is a written flow rather than a screenshot.

| Contract family | Coverage in this guide |
| --- | --- |
| For You | [For You](#for-you) |
| Top Picks and recommendation bundles | [Top Picks and preference actions](#top-picks-and-preference-actions) and [Share recommendation bundles](#share-recommendation-bundles) |
| Group recommendations | [Recommendations from a confirmed group](#recommendations-from-a-confirmed-group) |
| Recommendation settings | [Recommendation settings](#recommendation-settings) |
| Source Evaluation | [Source Evaluation](#source-evaluation) |
| Taste and source-quality diagnostics | [Taste and filters](#taste-and-filters), [Management and diagnostics](#management-and-diagnostics), and [Source-quality diagnostics](#source-quality-diagnostics) |
| Sources to try | [Sources to try](#sources-to-try) |
| Manga preferences | [Manga preferences and collections](#manga-preferences-and-collections) and [Top Picks and preference actions](#top-picks-and-preference-actions) |
| Cross-source matching | [Linked versions](#linked-versions) |
| Best Version | [Compare versions before migrating](#compare-versions-before-migrating) |
| Reader controls | [Reading schedule](#reading-schedule) and [Completion preference](#completion-preference) |
| Jump to last read | [Jump to last read](#jump-to-last-read), written flow only because chapter history is private |
| Evaluation Mode | [Evaluation Mode and safe review](#evaluation-mode-and-safe-review) |
| Action History | [Undo a supported local action](#undo-a-supported-local-action) |
| Extension export | [Extension export](#extension-export) |
| OCR search | [Search downloaded pages with OCR](#search-downloaded-pages-with-ocr) |
| Backup portability | [Back up and restore app data](#back-up-and-restore-app-data) |
| Extension operations | [Extension operations and failure isolation](#extension-operations-and-failure-isolation) |
| Security and integration | [Source runtime and integration boundaries](#source-runtime-and-integration-boundaries), written flow only |
| KMK change history | [Review KMK changes in the app](#review-kmk-changes-in-the-app) |

Evaluation Mode replaces source identities with neutral labels. Account information, device identifiers, raw URLs, and local paths are omitted. Manga artwork, titles, page text, and reading context remain visible where they help explain a feature.

## Screenshot coverage

Use this table to find a screen or the written steps for each feature area.

| Feature area | Where to look | What it shows |
| --- | --- | --- |
| For You | [Screenshot](for-you-evaluation-mode.png) | Evaluation Mode hides source names while keeping the recommendations visible. |
| Top Picks and bulk preference selection | [Screenshot](top-picks-preference-actions.png) | Selection mode shows the complete preference action surface. |
| Recommendation bundle sharing | [Screenshot](recommendation-bundle-export.png) | The Top Picks export entry point. The feature guide separately explains import review and the explicit library-add step. |
| Related and group-seeded recommendations | [Related-manga screenshot](related-manga-results.png) and [group-seeded screenshot](group-recommendations.png) | The difference between ordinary related results and recommendations seeded by a confirmed multi-version group. |
| Recommendation settings | [Screenshot](recommendation-settings.png) | The settings index contains no account, manga, source, or storage details. |
| Management and diagnostics | [Screenshot](management-diagnostics.png) | Only grouped controls and short, non-identifying summaries are shown. |
| Taste and filters | [Screenshot](taste-and-filters.png) | Preference and blocking controls with neutral tag examples. |
| Source-quality diagnostics | [Screenshot](source-quality-diagnostics.png) | The expanded view shows aggregate evidence and per-source detail with neutral labels. |
| Source Evaluation | [Screenshot](source-evaluation.png) | Aggregate progress and summarized source outcomes. |
| Browse in Evaluation Mode | [Screenshot](browse-evaluation-mode.png) | Source labels are neutralized without changing the normal Browse layout. |
| Love, Like, Dislike, and Not Interested | [Top Picks selection](top-picks-preference-actions.png) | The shared action surface shows Love, Like, Dislike, Not Interested, and Clear rating together. A matched collection-state set may be added later as supporting evidence. |
| Sources to try | [Screenshot](sources-to-try-evaluation-mode.png) and [feature explanation](../feature-guides/finding-and-prioritizing-sources.md) | Evaluation Mode replaces the populated list's source identities with neutral labels. |
| Find other versions | [Screenshot](linked-versions-evaluation-mode.png) and [feature explanation](../feature-guides/finding-and-comparing-manga-versions.md) | Manga matches with source names replaced by neutral labels. |
| Best Version comparison | [Chapter selection](best-version-chapter-selection.png), [preview comparison](best-version-preview-comparison.png), and [feature guide](../feature-guides/finding-and-comparing-manga-versions.md) | Chapter and page comparisons with neutral source labels. |
| Reading schedule | [Screenshot](reading-schedule.png) | The configuration dialog shows recurrence, editing, deletion, cancellation, and save controls. |
| Reader completion rating and linked-version follow-up | [Preference prompt](reader-completion-preference.png) and [matching-version follow-up](reader-linked-version-follow-up.png) | The two prompts keep the local preference decision separate from the cross-source continuation. |
| Reader timer and Jump to last read | [Reader guide](../feature-guides/reading-schedule-completion-and-chapter-navigation.md) | The Reader tools family is represented visually by schedule and completion screens; the guide gives the timer and chapter-jump steps. |
| Action History | [Screenshot](action-history-undo.png) | A completed local preference change and its Undo action. |
| Extension export | [Screenshot](extension-export-confirmation.png) | The executable-file boundary and the app data excluded from the export. |
| Exact-file cleanup | [Export guide](../feature-guides/sharing-screenshots-and-exported-files-safely.md) | The export family is represented visually by its consent screen; the guide explains the exact-document cleanup result and its limits. |
| OCR Search Downloads | [Screenshot](ocr-search-downloads.png) and [feature guide](../feature-guides/searching-downloaded-pages-with-ocr.md) | Indexed-page totals, storage use, indexing options, and cleanup controls. |
| Backup and restore | [Screenshot](backup-and-restore.png) | Backup and restore controls, scheduling, status, warnings, and progress settings. |
| KMK What's New | [Screenshot](kmk-whats-new.png) | The installed release, grouped history, summaries, and new/fix sections. |
| Extension operations | [Export confirmation](extension-export-confirmation.png) | The package boundary and the app data excluded from an export. |
| Security and integration boundaries | [Feature explanation](../feature-guides/validating-links-actions-and-file-cleanup.md) | Written explanations of validation, rejection, cancellation, and cleanup behavior. |

Screenshots show current, loaded feature states and are cropped to the app. Empty, loading, sample-only, and outdated screens are not used to represent normal behavior.

## Technical reference

The XML files in [`technical-reference/`](../technical-reference/) list each feature's screens, possible states, related code, privacy rules, and screenshot hashes. They are written references, not raw Android screen dumps, and they do not contain coordinates, device identifiers, manga titles, source names, URLs, account data, or local paths.

## Maintenance rule

Update the matching XML entry when a screen, visible control, privacy rule, or screenshot changes. A code change does not need a new screenshot when the screen still looks and behaves the same, but its links to the code must remain accurate.
