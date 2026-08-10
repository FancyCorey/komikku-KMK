# KMK documentation

KMK extends Komikku with personalized discovery, source evaluation, cross-source comparison, reversible preferences, local OCR search, safer export operations, and optional reader tools. This page is the starting point for the public documentation.

## Choose what you need

| Goal | Start here | What it contains |
| --- | --- | --- |
| Use KMK | [User guide](user-guide.md) | Step-by-step routes, expected results, limitations, and troubleshooting. |
| Understand a feature | [Feature explanations](features/README.md) | Plain-language behavior, diagrams, important states, privacy boundaries, and links to the responsible code. |
| See the app | [Reviewed screenshots](screenshots/README.md) | Current screenshots, what each one demonstrates, and why some private screens are described without an image. |
| Understand the whole system | [How KMK works](how-kmk-works.md) | Ownership, data flow, source isolation, storage, Android integration, and compatibility with Komikku. |
| Find a feature owner | [Feature map](feature-map.md) | A compact map from each feature family to its screens, states, and implementation files. |
| Build or contribute | [Build and verification](build-and-verify.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md) | Local build requirements, validation commands, issue guidance, and contribution expectations. |

## Policies and reference material

- [Privacy and data](privacy-and-data.md) explains local storage, network access, exports, backups, and screenshot sharing.
- [Security and integration](security-and-integration.md) explains input checks, isolated failures, cancellation, and bounded local changes.
- [Third-party components](third-party-components.md) records additional KMK dependencies and their terms.
- [Release notes](release-notes.md) summarizes the current public feature set and compatibility boundary.
- [Machine-readable reference](reference/README.md) explains the XML files used to map features and reviewed screenshots.
- [Feature reference (XML)](reference/feature-reference.xml) lists routes, states, privacy rules, and implementation owners in a machine-readable form.
- [Screenshot manifest (XML)](reference/screenshot-manifest.xml) lists every public screenshot, its review result, and its SHA-256 hash.
- [Security policy](../../SECURITY.md) explains how to report a vulnerability without publishing sensitive information.

## How the documentation is organized

The `features/` directory contains feature explanations, not design drafts. Each page states what the feature does, where it appears, how its important paths behave, and which source files implement it. The `screenshots/` directory contains only reviewed public images and their guide. The `reference/` directory contains machine-readable XML that supports documentation and release checks; it is not a substitute for the user guide.

Public filenames use lowercase words separated by hyphens. This keeps links readable, avoids spaces that require URL encoding, and matches the convention used throughout this documentation.

## Design principles

- Library, Browse, Reader, Settings, backup, tracking, and extension features continue to use Komikku's existing screens and internal flows.
- KMK keeps one extension failure from breaking unrelated screens and saves data through Komikku's existing database and settings.
- Cancellation remains cancellation. A source, network, or extension failure is isolated to the operation that encountered it.
- Evaluation Mode changes visible names in screenshots; it does not change saved identifiers, user data, source requests, or the item an action affects.
- Reversible actions record the previous value before a change and keep that record only when the change succeeds.
- The public fork uses its own package name, launcher name, update source, release page, and issue tracker while keeping the original Komikku artwork.

## Screenshots and privacy

The screenshots show the For You page in Evaluation Mode, which replaces source names with neutral labels. Covers and recommendation details remain visible because they are part of the feature being shown. When a screen would reveal personal preferences, library activity, account information, reader content, or actual source names, the guide uses a diagram and written explanation instead.
