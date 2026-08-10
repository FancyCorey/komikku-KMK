<div align="center">

<img width="160" height="160" src="./.github/readme-images/app-icon.png" alt="Komikku logo" />

# Komikku KMK

**An independent, unofficial Komikku fork focused on personal discovery, source evaluation, and reader tools.**

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-0877d2)](LICENSE)
[![Repository](https://img.shields.io/badge/repository-FancyCorey%2Fkomikku--KMK-163c42?logo=github)](https://github.com/FancyCorey/komikku-KMK)

</div>

Komikku KMK is based on [Komikku](https://github.com/komikku-app/komikku), TachiyomiSY, and Mihon. It is maintained independently and is not an official Komikku release. It retains Komikku's original visual identity while using a separate package name, launcher name, update source, issue tracker, and release channel.

## Install

Official KMK builds, when available, are published on this fork's [Releases page](https://github.com/FancyCorey/komikku-KMK/releases). Until then, follow the [build guide](docs/kmk/build-and-verify.md) to make your own. Avoid builds labeled Komikku KMK when they come from somewhere else.

Requires Android 8.0 or higher. The release application ID is `app.komikku.kmk`, so it can coexist with official Komikku.

Report fork-specific problems through this repository's [issue tracker](https://github.com/FancyCorey/komikku-KMK/issues). For upstream Komikku behavior, use the upstream project's support channels.

![screenshots of app](./.github/readme-images/screens.png)

<div align="left">

### KMK additions

This fork adds personalized discovery, source evaluation, cross-source comparison, reversible manga preferences, local OCR search, safer exports, and optional reader controls. The [KMK feature guide](docs/kmk/README.md) includes instructions, diagrams, privacy details, screenshots, and technical references.

![For You recommendations shown in Evaluation Mode](docs/kmk/evidence/screenshots/for-you-evaluation-mode.png)

This is the For You page in Evaluation Mode. Source names are hidden, while covers and recommendation details remain visible so you can see what the page actually recommends.

## Features

### Komikku's unique features:
- `Suggestions` automatically showing source-website's recommendations / suggestions / related to current entry for all sources.
- `Hidden categories` to hide yours things from *nosy* people.
- `Auto theme color` based on each entry's cover for entry View & Reader.
- `App custom theme` with `Color palettes` for endless color lover.
- `Bulk-favorite` multiple entries all at once.
- Source & Language icon on Library & various places. (Some language flags are not really accurate)
- `Feed` now supports **all** sources, with more items (20 for now).
- Fast browsing (for who with large library experiencing slow loading)
- Grouped entries in Update tab (inspired by J2K).
- Update notification with manga cover.
- Auto `2-way sync` progress with trackers.
- Chips for `Saved search` in source browse
- `Panorama cover` showing wide cover in full.
- `Merge multiple` library entries together at same time.
- `Range-selection` for Migration.
- Ability to `enable/disable repo`, with icon.
- `Update Error` screen & migrating them away.
- `to-be-updated` screen: which entries are going to be checked with smart-update?
- `Search for sources` & Quick NSFW sources filter in Extensions, Browse & Migration screen.
- `Feed` backup/restore/sync/re-order.
- Long-click to add/remove single entry to/from library, everywhere.
- Docking Read/Resume button to left/right.
- In-app progress banner shows Library syncing / Backup restoring / Library updating progress.
- Auto-install app update.
- Configurable interval to refresh entries from downloaded storage.
- Forked from SY so everything from SY.
- Always up-to-date with Mihon & SY
- More app themes & better UI, improvements...


<details>
  <summary>Features from Mihon / Tachiyomi</summary>

#### All up-to-date features from Mihon / Tachiyomi (original), include:

* Online reading from a variety of sources
* Local reading of downloaded content
* A configurable reader with multiple viewers, reading directions and other settings.
* Tracker support: [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [Kitsu](https://kitsu.app/), [MangaUpdates](https://mangaupdates.com), [Shikimori](https://shikimori.one), [Bangumi](https://bgm.tv/)
* Categories to organize your library
* Light and dark themes
* Schedule updating your library for new chapters
* Create backups locally to read offline or to your desired cloud service
* Continue reading button in library

</details>

<details>
  <summary>Features from Tachiyomi SY</summary>

#### All features from TachiyomiSY:
* Feed tab, where you can easily view the latest entries or saved search from multiple sources at same time.
* Automatic webtoon detection, allowing the reader to switch to webtoon mode automatically when viewing one
* Manga recommendations, uses MAL and Anilist, as well as Neko Similar Manga for Mangadex manga (Thanks to Az, She11Shocked, Carlos, and Goldbattle)
* Lewd filter, hide the lewd manga in your library when you want to
* Tracking filter, filter your tracked manga so you can see them or see non-tracked manga, made by She11Shocked
* Search tracking status in library, made by She11Shocked
* Custom categories for sources, liked the pinned sources, but you can make your own versions and put any sources in them
* Manga info edit
* Manga Cover view + share and save
* Dynamic Categories, view the library in multiple ways
* Smart background for reading modes like LTR or Vertical, changes the background based on the page color
* Force disable webtoon zoom
* Hentai features enable/disable, in advanced settings
* Quick clean titles
* Source migration, migrate all your manga from one source to another
* Saving searches
* Autoscroll
* Page preload customization
* Customize image cache size
* Batch import of custom sources and featured extensions
* Advanced source settings page, searching, enable/disable all
* Click tag for local search, long click tag for global search
* Merge multiple of the same manga from different sources
* Drag and drop library sorting
* Library search engine, includes exclude, quotes as absolute, and a bunch of other ways to search
* New E-Hentai/ExHentai features, such as language settings and watched list settings
* Enhanced views for internal and integrated sources
* Enhanced usability for internal and delegated sources

Custom sources:
* E-Hentai/ExHentai

Additional features for some extensions, features include custom description, opening in app, batch add to library, and a bunch of other things based on the source:
* 8Muses (EroMuse)
* Mangadex
* NHentai
* Puruin
* LANraragi

</details>

## Issues, Feature Requests and Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

<details><summary>Issues</summary>

Before reporting a new fork issue, review the [KMK guide](docs/kmk/README.md), [release notes](docs/kmk/release-notes.md), and [open issues](https://github.com/FancyCorey/komikku-KMK/issues). Use upstream Komikku support only after confirming the behavior is not specific to KMK.

</details>

<details><summary>Bugs</summary>

* Include version (More → About → Version)
 * If not latest, try updating, it may have already been solved
 * Preview version is equal to the number of commits as seen on the main page
* Include steps to reproduce (if not obvious from description)
* Include screenshot (if needed)
* If it could be device-dependent, try reproducing on another device (if possible)
* Don't group unrelated requests into one issue

Use this fork's [issue forms](https://github.com/FancyCorey/komikku-KMK/issues/new/choose) to submit a bug.

</details>

<details><summary>Feature Requests</summary>

* Write a detailed issue, explaining what it should do or how.
* Include screenshot (if needed).
</details>

<details><summary>Contributing</summary>

See [CONTRIBUTING.md](./CONTRIBUTING.md).
</details>

<details><summary>Code of Conduct</summary>

See [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md).
</details>

<div align="center">

### Credits

Thank you to the Komikku, TachiyomiSY, Mihon, and KMK contributors whose work makes this fork possible.

<a href="https://github.com/komikku-app/komikku/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=komikku-app/komikku" alt="Upstream Komikku contributors" title="Upstream Komikku contributors" width="800"/>
</a>

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

<div align="left">

## License

    Copyright 2015 Javier Tomás

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
