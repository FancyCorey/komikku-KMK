# How KMK features work together

These diagrams show how the feature pages share the app, save data, and keep private information within the boundaries chosen by the user.

## What this page covers

This overview connects the feature-specific pages. It shows which work belongs to Komikku screens and screen models, which work crosses into installed extensions or Android, and which state remains in local repositories, settings, and the database.

KMK is an extension of Komikku, not a parallel application inside it. Existing Komikku screens, navigation, database ownership, workers, backup framework, extension manager, reader, and Android integrations remain responsible for their established behavior. KMK adds focused policies and presentation around recommendation discovery, cross-source decisions, reversible preferences, local OCR, and optional reader controls.

## A practical way to read the architecture

- **Screens and screen models** own visible state, navigation, loading, retry, and lifecycle behavior.
- **Interactors and policies** decide eligibility, ranking, validation, and transitions without depending on presentation details.
- **Repositories and preferences** save durable local state through established app storage.
- **Runtime boundaries** isolate extension and network failures and preserve cancellation.
- **Android-owned flows** remain responsible for package installation, document selection, and other system-mediated actions.
- **Documentation references** connect each user-facing route to its main implementation owner.

This separation allows a recommendation policy to change without rewriting Browse, or a new reader action to reuse the existing manga and chapter models instead of duplicating them.

## In-app change history

KMK maintains its own What's New history because the fork's feature releases do not replace Komikku's upstream release notes. The current version family opens expanded, older families collapse behind short summaries, and the complete historical entries remain available. Acknowledging the screen records only the latest KMK feature version seen; it does not suppress Komikku's separate update information.

## System context

```mermaid
flowchart LR
    User["Reader or library user"] --> App["Komikku with KMK features"]
    App --> Extensions["Installed source extensions"]
    Extensions --> Networks["Source services and repositories"]
    App --> Storage["Local database and app storage"]
    App --> Android["Android lifecycle and document APIs"]
```

KMK runs inside the Android app and uses installed extensions. It does not add a separate KMK server.

## Responsibilities

```mermaid
flowchart TD
    UI["Screens and navigation"] --> Owners["Screen models and reader state"]
    Owners --> Local["Preferences, reader controls, and OCR"]
    Owners --> SourceWork["For You, evaluation, suggestions, and matching"]
    Local --> Persistence["Repositories, settings, and database"]
    SourceWork --> Runtime["Guarded source runtime"]
    Runtime --> Extensions["Installed source extensions"]
    SourceWork --> Persistence
```

Screens decide what to display, policy classes make feature decisions, and repositories or settings save data that must survive a restart.

## User action to saved result

```mermaid
sequenceDiagram
    actor User
    participant Screen as Feature screen
    participant Model as Screen model
    participant Policy as Feature policy
    participant Runtime as Source or local boundary
    participant Store as Repository or setting
    participant DB as Local persistence

    User->>Screen: Perform an action
    Screen->>Model: Send validated intent
    Model->>Policy: Apply feature rules
    Policy->>Runtime: Run guarded work
    Runtime-->>Policy: Result, failure, or cancel
    Policy->>Store: Prepare accepted update
    Store->>DB: Save state
    DB-->>Store: Confirm write
    Store-->>Model: Updated state
    Model-->>Screen: Reconcile visible result
    Screen-->>User: Show result or explanation
```

The visible result follows confirmed state. A failed or cancelled write does not become a successful screen message.

## Persistence and migration

```mermaid
flowchart LR
    Intent["User intent or accepted source result"] --> Domain["Validated domain state"]
    Domain --> Mapping["Repository or setting mapping"]
    Mapping --> Database["Database or preference records"]
    Migration["Registered database migration"] --> Database
    Backup["Supported backup and restore mapping"] --> Database
    Database --> State["Current feature state"]
    State --> UI["Screen display"]
```

New stored data uses registered migrations and explicit backup behavior where the feature supports backup and restore.

## Privacy boundaries

```mermaid
flowchart TD
    App["Komikku KMK"] --> Local["Local ratings, history, settings, and OCR index"]
    App --> Sources["Catalogue requests through installed extensions"]
    App --> Display["Optional neutral labels in Evaluation Mode"]
    App --> Documents["User-directed exports and backups through Android"]
```

KMK has no separate recommendation account or server. Local preference and reading data stays in the app unless the user explicitly includes supported data in a backup or export. Evaluation Mode affects only what is displayed; it does not redirect requests or change which source or manga an action targets.

## Implementation reference

| Layer | Representative source |
| --- | --- |
| Android entry point and app navigation | [`MainActivity`](../../../app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt) |
| Recommendation screen ownership | [`BrowsePersonalRecommendationsScreenModel`](../../../app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt) |
| Reader ownership | [`ReaderViewModel`](../../../app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt) |
| Guarded extension boundary | [`SourceRuntime`](../../../app/src/main/java/eu/kanade/tachiyomi/source/SourceRuntime.kt) |
| Local persistence | Database schema and migrations under [`data`](../../../data/) together with focused repositories such as [`OcrIndexRepository`](../../../app/src/main/java/exh/ocr/OcrIndexRepository.kt) |

These links are representative ownership points, not a complete class inventory. The [feature and code map](../feature-and-code-map.md) provides the direct owner for each public feature.
