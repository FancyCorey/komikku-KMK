# KMK documentation

KMK extends Komikku with personalized and group discovery, Top Picks and recommendation bundles, source evaluation and diagnostics, cross-source comparison, reversible preferences, local OCR search, safer export operations, backup portability, and optional reader tools. This page is the starting point for the public documentation.

## Choose what you need

| Goal | Start here | What it contains |
| --- | --- | --- |
| Use KMK | [User guide](user-guide.md) | Step-by-step routes, expected results, limitations, and troubleshooting. |
| Understand a feature | [Feature guides](feature-guides/README.md) | Detailed, plain-language guides to what each feature does, how to use it, important states, privacy boundaries, and the responsible code. |
| Tour the main screens | [Visual feature guide](visual-guide/README.md) | Reviewed app images arranged by workflow, with captions that explain the controls and results shown. |
| Understand the whole system | [How KMK works](how-kmk-works.md) | Ownership, data flow, source isolation, storage, Android integration, and compatibility with Komikku. |
| Find a feature owner | [Feature and code map](feature-and-code-map.md) | A compact map from each feature family to its screens, states, and implementation files. |
| Build or contribute | [Build and verification](build-and-verify.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md) | Local build requirements, validation commands, issue guidance, and contribution expectations. |
| Prepare a release | [Release channels](release-channels.md) | Public release and private development-build boundaries, signing, updater behavior, and publication checks. |

## Policies and reference material

- [Privacy and data](privacy-and-data.md) explains local storage, network access, exports, backups, and screenshot sharing.
- [Security and integration](security-and-integration.md) explains input checks, isolated failures, cancellation, and bounded local changes.
- [Third-party components](third-party-components.md) records additional KMK dependencies and their terms.
- [Release notes](release-notes.md) summarizes the current public feature set and compatibility boundary.
- [Technical reference](technical-reference/README.md) explains the XML files used to map features and reviewed public images.
- [Feature reference (XML)](technical-reference/feature-reference.xml) lists routes, states, privacy rules, and implementation owners in a machine-readable form.
- [Screenshot manifest (XML)](technical-reference/screenshot-manifest.xml) lists every public screenshot, its review result, and its SHA-256 hash.
- [Security policy](../../SECURITY.md) explains how to report a vulnerability without publishing sensitive information.

## How the documentation is organized

The `feature-guides/` directory contains task-oriented explanations of KMK's user-facing capabilities. Each page explains the problem being solved, the normal workflow, important states and limits, privacy behavior, and the source files that implement it. The `visual-guide/` directory is a guided tour of reviewed screens, not a raw screenshot archive. The `technical-reference/` directory contains XML indexes for maintainers and automated checks; it complements the readable guides instead of replacing them.

Public filenames use lowercase words separated by hyphens. This keeps links readable, avoids spaces that require URL encoding, and matches the convention used throughout this documentation.

## Design principles

- Library, Browse, Reader, Settings, backup, tracking, and extension features continue to use Komikku's existing screens and internal flows.
- KMK keeps one extension failure from breaking unrelated screens and saves data through Komikku's existing database and settings.
- Cancellation remains cancellation. A source, network, or extension failure is isolated to the operation that encountered it.
- Evaluation Mode changes visible names in screenshots; it does not change saved identifiers, user data, source requests, or the item an action affects.
- Reversible actions record the previous value before a change and keep that record only when the change succeeds.
- The public fork uses its own package name, launcher name, update source, release page, and issue tracker while keeping the original Komikku artwork.

## Screenshots and privacy

The visual guide includes discovery, settings, source-quality, cross-source matching, and Best Version workflows. Evaluation Mode replaces source names with neutral labels while leaving the manga artwork and feature result understandable. When a current capture would reveal personal preferences, reading history, account information, page content, or device configuration, the relevant feature guide explains the workflow without publishing that capture.
