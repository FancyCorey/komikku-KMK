# System-wide architecture diagrams

These diagrams connect the feature pages and show the shared application, storage, and publication boundaries.

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

## Feature ownership

```mermaid
flowchart TD
    UI["Screens and navigation"] --> Taste["Preferences and taste"]
    UI --> Evaluation["Evaluation and source suggestions"]
    UI --> Matching["Groups and Best Version"]
    UI --> Reader["Reader controls"]
    Taste --> Retrieval["For You retrieval and ranking"]
    SourcePolicy["Source order and eligibility"] --> Retrieval
    Runtime["SourceRuntime isolation"] --> Retrieval
    Runtime --> Evaluation
    Runtime --> Matching
    Taste --> Persistence["Repositories, settings, and database"]
    Retrieval --> Persistence
    Evaluation --> Persistence
    Matching --> Persistence
    Reader --> Persistence
```

Screens own presentation, policies own decisions, and repositories or settings own durable state.

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

## Public boundary

```mermaid
flowchart TD
    Source["Reviewed source and tests"] --> Review["Publication review"]
    Docs["Current user, architecture, and reference documentation"] --> Review
    Evidence["Sanitized screenshots and semantic XML"] --> Review
    Review --> Checks{"Paths, privacy, build, tests, diagrams, and hashes pass?"}
    Checks -->|Yes| Public["Public fork"]
    Checks -->|No| Hold["Correct before publication"]
    Raw["Raw logs, device data, accounts, and local paths"] --> Excluded["Excluded from public history"]
```

The publication check applies to both the final files and the Git history that would be pushed.
