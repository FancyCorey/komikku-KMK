# System-wide architecture diagrams

These diagrams show how the feature pages share the app, save data, and keep private information within the boundaries chosen by the user.

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
    Policy->>Runtime: Request guarded work when needed
    Runtime-->>Policy: Result, local failure, or cancellation
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
