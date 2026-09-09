# Komikku KMK Experimental Fork

Status: public README draft. This is not the final release README.

This is an experimental personal fork of Komikku based on Komikku `v1.13.6`. It adds a large set of recommendation, cross-extension, source-evaluation, import/export, best-version, and OCR experiments.

This fork is not an official Komikku release. It is not currently upstream-ready. The code and features should be treated as a personal/community beta until the consolidation audits, privacy review, and release checklist are complete.

## Baseline

- Upstream/current baseline used for consolidation: Komikku `v1.13.6`
- Official repository: `https://github.com/komikku-app/komikku`
- Local KMK-Recs documented version: `KMK-Recs v0.7.46`
- OCR: included in the main KMK-Recs build line as of this version (see "KMK-OCR" below) -- not a separate APK

## Build Lines And Package IDs

Two distinct APK lines exist:

| APK line | Package ID | Purpose |
|---|---|---|
| Personal / update-style | `app.komikku` (release) / `app.komikku.dev` (debug) | User's personal install -- replaces/updates over existing Komikku |
| Public test | `app.komikku.kmk` | Community/Reddit sharing -- installs **beside** official Komikku and beside the personal build |

**Important:** The public test APK (`app.komikku.kmk`) is a **separate app** from official Komikku. It does not share app data. You must export a backup from your existing Komikku installation and restore it into Komikku KMK if you want to transfer your library or settings.

Build command for the public test APK:
```
.\gradlew.bat :app:assembleKmkPublicTest
```
Output: `app/build/outputs/apk/kmkPublicTest/app-universal-kmkPublicTest.apk`

Recommended public filename: `Komikku-KMK-PublicTest-v1.13.6-kmk.7.46-debug.apk`

This is a **debug-signed public test build**, not a release-signed official build -- see "Development Status" below.

## Release Lines

### KMK-Recs

The KMK-Recs line contains the recommendation-related features:

- personal For You recommendations,
- manga Love / Like / Dislike ratings,
- tag preferences,
- Top Picks,
- source priority with rolling fit stats and "suggest order" button,
- source status diagnostics with last-checked timestamps,
- Sources To Try,
- source like/dislike,
- cross-extension matching with link group management,
- Loved Manga with live updates and duplicate grouping,
- Best Version comparison with fullscreen preview,
- Best Version History browser,
- backup and restore for Seen manga dismissals,
- recommendation bundle import/export,
- minimum chapter count filter,
- configurable enrichment cap,
- Source Evaluation experiments.

### KMK-OCR

As of `KMK-Recs v0.7.45`+, OCR is **built into the same APK line as KMK-Recs** -- it is not a separate download. Every `KMK-Recs` build (personal and public test) includes OCR. There is no separate `KMK-OCR`-only APK.

OCR reads downloaded manga pages, extracts recognized text, stores that text locally, and lets the user search downloaded pages by text. OCR can increase APK size and may use significant CPU, memory, battery, and storage.

OCR text never leaves the device: it is not included in backups, sync, recommendation bundle export, or diagnostics. See "OCR Downloaded Text Search" below for details and deletion controls.

## Main Features

### Personal Recommendations

The For You page builds local recommendations from:

- manga you Love, Like, or Dislike,
- preferred/disliked/blocked tags,
- source priority,
- recommendation language settings,
- known/seen/read filtering,
- source search results.

Top Picks combines the best results from the currently fetched source rows. It is not a separate source and does not crawl the whole internet.

### Source Controls

The fork adds tools for:

- source priority ordering,
- source status diagnostics,
- source like/dislike,
- recommendation language filtering,
- hiding explicit porn/hentai sources separately from ecchi,
- selecting and installing suggested sources,
- selecting and uninstalling installed extensions.

### Source Evaluation

Source Evaluation is experimental and security-sensitive.

It can temporarily install extensions, load their source definitions, run source/network methods, score their fit against your local taste profile, and then attempt cleanup.

Important limitations:

- extension behavior varies by source,
- some source methods can fail, hang, or crash,
- some cleanup paths may require Android prompts,
- Shizuku/current/private installer behavior must be understood before use,
- this feature should be treated as advanced/experimental.

Back up before experimenting with it.

### Cross-Extension Matching

The fork can search for the same manga across sources and let the user:

- Love other versions,
- Like other versions,
- Dislike other versions,
- mark other versions seen/read,
- favorite other versions,
- create cross-source link groups.

Matching is not perfect. Users must confirm selected matches.

### Loved Manga

Loved Manga shows manga marked Love from currently installed sources. It attempts conservative duplicate grouping using evidence such as cross-source link groups, title/author matches, and description similarity.

The app should not hide entries aggressively when identity is uncertain.

### Best Version / Chapter Quality

The Best Version workflow helps compare pages from the same manga across sources so the user can choose a better-quality source.

It can:

- search for same-manga candidates,
- let the user confirm candidates,
- choose a chapter,
- preview sampled pages,
- zoom/pan preview images,
- prompt for migrate/copy confirmation.

It should never silently migrate without user confirmation.

### Recommendation Bundle Import/Export

Recommendation bundles export recommended manga as JSON so they can be shared or imported elsewhere.

Import is a trust boundary:

- malformed files should fail safely,
- large files should be bounded,
- missing sources should not install automatically,
- users should preview and confirm before adding manga to the library.

### OCR Downloaded Text Search

OCR is a separate experimental feature line.

OCR:

- reads downloaded pages,
- extracts text locally,
- stores searchable text in the app database,
- lets the user search by words or phrases.

Privacy note:

- OCR text can contain manga dialogue or page text.
- It should remain local-only.
- It should not be backed up, synced, exported, or logged unless explicitly redesigned later.

## Known Limitations

- This fork is not upstream-ready.
- The **personal/update-style** build (`app.komikku` release / `app.komikku.dev` debug) intentionally shares its package ID with upstream Komikku -- that is what lets it update/replace an existing Komikku install, the same way any other Komikku fork/build would. This is by design, not a bug.
- This is **not** a blocker for community distribution: the separate **public test** build (`app.komikku.kmk`, built via `:app:assembleKmkPublicTest`) exists specifically so the fork can be shared and installed side-by-side with official Komikku without overwriting it. Use the public test build, not the personal build, for community/Reddit sharing.
- Source Evaluation is experimental and can be source/device dependent.
- OCR can be slow and resource-intensive.
- Cross-extension identity matching is uncertain and requires user confirmation.
- Some documentation is still internal/handoff style.
- Some test/build claims need to be refreshed after the final cleanup.
- Generated APK artifacts should not be committed to source control.

## Safety Notes

Before installing or testing:

- back up your Komikku data before installing any KMK build,
- if testing the public test APK (`app.komikku.kmk`): it installs as a separate app and does not share data with official Komikku -- restore your backup inside Komikku KMK to transfer your library,
- understand whether you are installing the KMK-Recs or KMK-OCR line,
- avoid running Source Evaluation unless you understand temporary extension installation,
- do not import recommendation bundles from untrusted people without previewing them,
- remember that extension sources are third-party code/content.

## Testing Status

The current internal docs list many unit tests for recommendation scoring, source ordering, source evaluation helpers, cross-extension matching, Loved Manga grouping, Best Version helpers, recommendation bundle validation, and OCR helpers.

However, before public sharing, the following still need a formal release checklist:

- clean install,
- update install,
- backup/restore,
- source evaluation private/current/Shizuku modes,
- offline/reconnect behavior,
- OCR with small and large downloaded chapters,
- Best Version migration/copy/cancel,
- phone and tablet checks,
- debug and release builds.

## Reporting Issues

When reporting issues, include:

- APK filename/version,
- KMK-Recs or KMK-OCR line,
- Android version,
- device model,
- whether this was clean install or update,
- feature area,
- source/extension name if relevant,
- steps to reproduce,
- expected behavior,
- actual behavior,
- crash log if available.

Do not publicly paste private OCR text, personal backups, tokens, or sensitive manga URLs.

## Development Status

The fork is currently in consolidation.

The active goal is to:

- document the full KMK delta from Komikku `v1.13.6`,
- classify features,
- audit database/backup/sync behavior,
- audit security/privacy risks,
- harden Source Evaluation and OCR,
- align code with Komikku style,
- create a release checklist,
- prepare honest public docs.

Until that is complete, this should be treated as an experimental fork.

