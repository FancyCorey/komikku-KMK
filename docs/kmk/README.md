# KMK feature guide

KMK extends Komikku with personalized discovery, cross-source comparison, reversible preference actions, local OCR search, safer export operations, and optional reader controls. Start with the user guide to use the features. Use the architecture and feature catalog when you need implementation details.

## Documentation map

- [User guide](user-guide.md) gives step-by-step instructions, expected results, and troubleshooting guidance.
- [Public UI evidence](evidence/README.md) shows the approved screenshots and explains what each image demonstrates.
- [Architecture](architecture.md) explains how screens, source extensions, storage, and reader features work together.
- [Feature diagrams](diagrams/README.md) provides detailed flows for every KMK feature domain.
- [Feature catalog](feature-catalog.md) maps each feature family to its implementation owner and possible states.
- [Build and verification](build-and-verify.md) explains how to produce and check a local build.
- [Privacy and data](privacy-and-data.md) explains local storage, network boundaries, exports, backups, and evidence sharing.
- [Security and integration](security-and-integration.md) explains validation, failure isolation, cancellation, and scoped mutation boundaries.
- [Third-party components](third-party-components.md) records the additional KMK dependencies and their terms.
- [Release notes](release-notes.md) summarizes the current public feature set and compatibility boundary.
- [Machine-readable feature contract](evidence/xml/feature-contract.xml) records routes, states, and source ownership.
- [Machine-readable evidence manifest](evidence/xml/evidence-manifest.xml) records artifact purpose, privacy treatment, and hashes.
- [Security policy](../../SECURITY.md) explains how to report a vulnerability without publishing private data.

## Design principles

- Existing Library, Browse, Reader, Settings, backup, tracking, and extension behavior remains owned by Komikku's established screens and domain boundaries.
- KMK screens isolate source-extension failures and store data through the app's established storage layers.
- Cancellation remains cancellation. A source, network, or extension failure is isolated to the operation that encountered it.
- Evaluation Mode changes presentation and evidence labels; it does not change stable identifiers, stored user state, source requests, or action targets.
- Reversible actions record the previous value before a change and keep that record only when the change succeeds.
- The public fork uses its own package name, launcher identity, update source, release page, and issue tracker.

## Evidence scope

The public screenshots include the central For You experience in Evaluation Mode, with source identities replaced by neutral labels. Manga artwork and recommendation context are intentionally visible because they demonstrate the feature's primary result surface. Screens that would expose explicit preference choices, library history, account state, reader content, or unredacted source identities are represented by the XML contract and source links instead.
