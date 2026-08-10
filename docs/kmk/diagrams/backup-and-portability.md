# Backup and portability diagrams

## Backup selection

```mermaid
flowchart TD
    Start["Create backup"] --> Options["Choose Komikku and KMK data options"]
    Options --> Core["Library and app settings"]
    Options --> Taste["Ratings, tags, and recommendation settings"]
    Options --> Links["Linked versions and group primary"]
    Options --> Quality["Source evaluation and quality state"]
    Core --> Encode["Encode supported records"]
    Taste --> Encode
    Links --> Encode
    Quality --> Encode
```

KMK state is included through explicit backup options and established backup encoding, not a second independent archive format.

## Restore ownership

```mermaid
sequenceDiagram
    participant Job as Restore job
    participant Decoder
    participant Core as Komikku restorers
    participant KMK as KMK taste restorer
    participant Result as Restore outcome
    Job->>Decoder: Decode supported backup
    Decoder->>Core: Restore established app data
    Decoder->>KMK: Restore selected KMK records
    Core-->>Result: Counts and failures
    KMK-->>Result: Counts and failures
    Result-->>Job: Complete, partial, or failed
```

Core and KMK restorers contribute to one truthful result, so a partial restore cannot be presented as complete.

## Conflict-safe group restoration

```mermaid
flowchart TD
    Record["Linked-version record"] --> Resolve["Resolve current manga records"]
    Resolve --> Valid{"All referenced members valid?"}
    Valid -->|No| Skip["Report skipped record"]
    Valid -->|Yes| Restore["Restore links and primary selection"]
    Restore --> Result["Count restored state"]
```

Linked-version state is restored only after current records resolve consistently. Invalid references are skipped and reported.

## Deliberate exclusions

```mermaid
flowchart LR
    OCR["OCR page text"] -. excluded .-> Backup["Backup"]
    History["Transient action history"] -. not external rollback .-> Backup
    Credentials["Account credentials"] -. never copied by KMK .-> Backup
    Rebuild["Downloaded pages"] --> OCR
```

Regenerable or externally owned data is deliberately outside KMK backup ownership.
