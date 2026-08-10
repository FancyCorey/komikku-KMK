# KMK feature guide

KMK extends Komikku with personalized discovery, cross-source comparison, reversible preference actions, local OCR search, safer export operations, and optional reader controls. Start with the user guide to use the features. Use the architecture and feature catalog when you need implementation details.

## Documentation map

- [User guide](user-guide.md) gives step-by-step instructions, expected results, and troubleshooting guidance.
- [Screenshots](evidence/README.md) shows the reviewed app screens and explains what to look for in each one.
- [Architecture](architecture.md) explains how screens, source extensions, storage, and reader features work together.
- [Feature diagrams](diagrams/README.md) explain how each group of KMK features works.
- [Feature catalog](feature-catalog.md) maps each feature family to its implementation owner and possible states.
- [Build and verification](build-and-verify.md) explains how to produce and check a local build.
- [Privacy and data](privacy-and-data.md) explains local storage, network access, exports, backups, and safe screenshot sharing.
- [Security and integration](security-and-integration.md) explains input checks, isolated failures, cancellation, and safe changes to local data.
- [Third-party components](third-party-components.md) records the additional KMK dependencies and their terms.
- [Release notes](release-notes.md) summarizes the current public feature set and compatibility boundary.
- [Feature reference (XML)](evidence/xml/feature-contract.xml) lists screens, states, and the code responsible for them.
- [Screenshot manifest (XML)](evidence/xml/evidence-manifest.xml) lists each public file, its privacy review, and its hash.
- [Security policy](../../SECURITY.md) explains how to report a vulnerability without publishing private data.

## Design principles

- Library, Browse, Reader, Settings, backup, tracking, and extension features continue to use Komikku's existing screens and internal flows.
- KMK keeps one extension failure from breaking unrelated screens and saves data through Komikku's existing database and settings.
- Cancellation remains cancellation. A source, network, or extension failure is isolated to the operation that encountered it.
- Evaluation Mode changes visible names in screenshots; it does not change saved identifiers, user data, source requests, or the item an action affects.
- Reversible actions record the previous value before a change and keep that record only when the change succeeds.
- The public fork uses its own package name, launcher name, update source, release page, and issue tracker while keeping the original Komikku artwork.

## Screenshots and privacy

The screenshots show the For You page in Evaluation Mode, which replaces source names with neutral labels. Covers and recommendation details stay visible so you can understand what the feature recommends. When a screen would reveal personal preferences, library activity, account information, reader content, or actual source names, the guide uses a diagram and written explanation instead.
