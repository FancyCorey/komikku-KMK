# Screenshots

These screenshots show the main KMK screens on a tablet. Each image is cropped to the app and checked for private information. The captions point out the important parts of each screen.

## For You

![For You page with personalized manga rows, matching tags, and anonymized source labels](screenshots/for-you-evaluation-mode.png)

At the top of For You, you can see topic shortcuts, personalized rows, matching tags, and manga cards. Evaluation Mode hides source names, while the covers and titles remain visible so the recommendations are still meaningful.

## Browse compatibility

![Browse page with neutral source labels while Evaluation Mode is enabled](screenshots/browse-evaluation-mode.png)

Evaluation Mode also hides source names in Browse. The rest of Komikku's navigation stays the same.

## Recommendation settings

![Recommendation settings page divided into clear feature sections](screenshots/recommendation-settings.png)

The settings page groups related controls into For You sources, taste and filters, source evaluation, sources to try, and management and diagnostics. Each row leads to a focused settings page.

## Management and diagnostics

![Management and diagnostics settings with grouped controls and short summaries](screenshots/management-diagnostics.png)

This page keeps maintenance and diagnostic controls together. Its summaries show the current settings without exposing manga titles, source names, account details, or local paths.

## Source Evaluation

![Source Evaluation page showing progress counts, warnings, and reassessment controls](screenshots/source-evaluation.png)

This screenshot shows Source Evaluation partway through a run, with progress, readiness information, and reassessment actions. It uses general categories instead of raw source names or error messages.

These screenshots leave out specific preference choices, reading history, account information, reader pages, and menus that could reveal private manga activity.

## XML reference files

The XML files in `xml/` list each feature's screens, possible states, related code, privacy rules, and screenshot hashes. They are written references, not raw Android screen dumps, and they do not contain coordinates, device identifiers, manga titles, source names, URLs, account data, or local paths.

## Maintenance rule

Update the matching XML entry when a screen, visible control, privacy rule, or screenshot changes. A code change does not need a new screenshot when the screen still looks and behaves the same, but its links to the code must remain accurate.
