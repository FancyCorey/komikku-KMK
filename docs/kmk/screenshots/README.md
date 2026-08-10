# Reviewed screenshots

These screenshots show the main KMK screens on a tablet. Each image is cropped to the app and checked for private information. The captions point out the important parts of each screen.

## For You

![For You page with personalized manga rows, matching tags, and anonymized source labels](for-you-evaluation-mode.png)

At the top of For You, you can see topic shortcuts, personalized rows, matching tags, and manga cards. Evaluation Mode hides source names, while the covers and titles remain visible so the recommendations are still meaningful.

## Browse compatibility

![Browse page with neutral source labels while Evaluation Mode is enabled](browse-evaluation-mode.png)

Evaluation Mode also hides source names in Browse. The rest of Komikku's navigation stays the same.

## Recommendation settings

![Recommendation settings page divided into clear feature sections](recommendation-settings.png)

The settings page groups related controls into For You sources, taste and filters, source evaluation, sources to try, and management and diagnostics. Each row leads to a focused settings page.

## Management and diagnostics

![Management and diagnostics settings with grouped controls and short summaries](management-diagnostics.png)

This page keeps maintenance and diagnostic controls together. Its summaries show the current settings without exposing manga titles, source names, account details, or local paths.

## Source Evaluation

![Source Evaluation page showing progress counts, warnings, and reassessment controls](source-evaluation.png)

This screenshot shows Source Evaluation partway through a run, with progress, readiness information, and reassessment actions. It uses general categories instead of raw source names or error messages.

## Sources to try

![Sources to try with ranked suggestions and neutral source labels](sources-to-try-evaluation-mode.png)

The populated list shows ranking, sorting, installation, and selection controls. Evaluation Mode replaces the source names with neutral labels.

## Preferences across versions

| Not Interested | Linked-version selection |
| --- | --- |
| ![Not Interested selection across matching versions](not-interested-other-versions.png) | ![Matching manga grouped under neutral source labels](linked-versions-evaluation-mode.png) |

These screens show how a preference can be applied across matching versions. The manga artwork and titles are retained to make the result understandable, while source names remain hidden.

These screenshots leave out specific preference choices, reading history, account information, reader pages, and menus that could reveal private manga activity.

## Screenshot coverage

The public guide uses a screenshot when the screen can explain the feature without exposing private activity or device configuration. When a useful screen cannot meet that rule, its feature explanation provides the flow and written steps instead. This keeps an omitted screenshot from looking like an undocumented feature.

| Feature area | Public visual | Why |
| --- | --- | --- |
| For You | [Screenshot](for-you-evaluation-mode.png) | Evaluation Mode hides source names while keeping the recommendations visible. |
| Recommendation settings | [Screenshot](recommendation-settings.png) | The settings index contains no account, manga, source, or storage details. |
| Management and diagnostics | [Screenshot](management-diagnostics.png) | Only grouped controls and short, non-identifying summaries are shown. |
| Source Evaluation | [Screenshot](source-evaluation.png) | The reviewed state contains aggregate progress and no raw source errors. |
| Browse in Evaluation Mode | [Screenshot](browse-evaluation-mode.png) | Source labels are neutralized without changing the normal Browse layout. |
| Ratings and Not Interested | [Screenshot](not-interested-other-versions.png) and [feature explanation](../features/ratings-and-linked-versions.md) | The reviewed example uses neutral source labels and approved manga artwork to show the preference clearly. |
| Sources to try | [Screenshot](sources-to-try-evaluation-mode.png) and [feature explanation](../features/source-discovery-and-priority.md) | Evaluation Mode replaces the populated list's source identities with neutral labels. |
| Find other versions | [Screenshot](linked-versions-evaluation-mode.png) and [feature explanation](../features/cross-source-matching.md) | The reviewed selection screen keeps the manga matches visible and hides source names. |
| Best Version comparison | [Feature explanation](../features/cross-source-matching.md) | The available history and comparison captures contain private manga and reading-history context. |
| Reader controls, schedule, completion rating, and Jump to last read | [Feature explanation](../features/reader-tools.md) | Reader pages, chapter names, and progress are private reading history. A current, cropped, generic schedule capture has not been approved. |
| Action History | [Feature explanation](../features/ratings-and-linked-versions.md) | A populated screen can reveal personal actions, while an empty screen would not explain how restoration and conflict checks work. |
| Export and cleanup | [Feature explanation](../features/evaluation-mode-and-exports.md) | Extension identity and Android document-provider details can appear before or after the warning. |
| OCR Search Downloads | [Feature explanation](../features/ocr-search.md) | Recognized text, manga names, chapters, and page context are private content. |
| Backup and restore | [Feature explanation](../features/backup-and-restore.md) | Destinations and restored library details can expose storage and reading information. |
| Extension operations | [Feature explanation](../features/extension-management.md) | Installed packages, repositories, and source configuration identify the user's setup. |
| Security and integration boundaries | [Feature explanation](../features/security-boundaries.md) | These behaviors are better demonstrated by bounded flows and tests than by publishing hostile inputs or private route values. |

A new screenshot is not included until it is cropped to the app, reviewed for private information, hashed, added to the XML manifest, and linked from the relevant guide section. Empty, loading, test-fixture, and outdated screens are not substitutes for the feature's normal state.

## Machine-readable reference

The XML files in [`reference/`](../reference/) list each feature's screens, possible states, related code, privacy rules, and screenshot hashes. They are written references, not raw Android screen dumps, and they do not contain coordinates, device identifiers, manga titles, source names, URLs, account data, or local paths.

## Maintenance rule

Update the matching XML entry when a screen, visible control, privacy rule, or screenshot changes. A code change does not need a new screenshot when the screen still looks and behaves the same, but its links to the code must remain accurate.
