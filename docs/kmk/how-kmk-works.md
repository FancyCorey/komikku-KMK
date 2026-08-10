# How KMK works

This page explains how KMK fits into Komikku. Read it when you want to understand which part of the app owns a feature, where data is stored, or how failures are contained. For instructions, use the [user guide](user-guide.md). For a closer look at one feature, use the [feature explanations](features/README.md).

## System boundary

```mermaid
flowchart LR
    User["Reader"] --> App["Komikku Android app"]
    App --> Library["Library, history, and reader"]
    App --> Discovery["KMK discovery and preference features"]
    Discovery --> Runtime["Guarded source runtime"]
    Runtime --> Extensions["Installed source extensions"]
    Library --> Store["Repositories, preferences, and SQLDelight"]
    Discovery --> Store
    App --> Android["Android lifecycle, storage, and document APIs"]
```

Komikku continues to own navigation, the library, the reader, downloads, tracking, backup, and extension loading. KMK adds discovery and preference features inside those existing parts of the app. Installed extensions can access their own online services; KMK does not add a separate server.

## Where features live

```mermaid
flowchart TD
    Screens["Screens and navigation"] --> Owners["Screen models and reader state"]
    Owners --> Local["Preferences, reader tools, and OCR"]
    Owners --> SourceWork["For You, evaluation, suggestions, and matching"]
    Local --> Storage["Repositories, settings, and database"]
    SourceWork --> Runtime["Guarded source runtime"]
    Runtime --> Extensions["Installed source extensions"]
    SourceWork --> Storage
```

Screens display information and handle navigation. Screen models hold the current feature state and coordinate the work behind each screen. `SourceRuntime` prevents one failing extension from breaking unrelated work and preserves cancellation. Data is stored through Komikku's repositories, settings, and database migrations.

## Recommendation flow

```mermaid
sequenceDiagram
    actor User
    participant Screen as For You screen
    participant Model as Recommendation screen model
    participant Policy as Eligibility and ranking policies
    participant Runtime as SourceRuntime
    participant Source as Installed source
    participant Memory as Exposure and preference stores

    User->>Screen: Open or refresh For You
    Screen->>Model: Request visible rows
    Model->>Memory: Read ratings, exclusions, exposure, and settings
    Model->>Runtime: Request guarded source candidates
    Runtime->>Source: Search, latest, or supported catalogue request
    Source-->>Runtime: Candidates or local failure
    Runtime-->>Model: Isolated result
    Model->>Policy: Filter, merge, diversify, and rerank
    Policy-->>Model: Stable visible result
    Model->>Memory: Record visible exposure
    Model-->>Screen: Loaded, empty, partial, or error state
```

Personalized matches remain the majority when enough suitable results exist. A smaller set of recent catalogue entries can add variety, but those entries must still pass language, genre, minimum-chapter, exclusion, and source checks. If a card remains visible and untouched for the configured number of days, the app moves it lower instead of deleting it. Manga in the library, manga with a preference, and known tracked manga keep their position when the app can verify that state safely.

## Reversible preference actions

```mermaid
flowchart TD
    Neutral["Neutral"] --> Choose{"Preference action"}
    Choose --> Love["Love"]
    Choose --> Like["Like"]
    Choose --> Dislike["Dislike"]
    Choose --> NotInterested["Not Interested"]
    Love --> Rated["Visible rating state"]
    Like --> Rated
    Dislike --> Rated
    NotInterested --> Hidden["Visible Not Interested state"]
    Rated --> Clear["Clear rating"]
    Hidden --> Undo["Undo not interested"]
    Hidden --> Replace["Choose a rating"]
    Clear --> Neutral
    Undo --> Neutral
    Replace --> Rated
```

Not Interested behaves like the other preference choices. It has its own marker and collection, and it can be cleared or replaced by Love, Like, or Dislike. Before a supported change, Action History records the previous value. It keeps that record only when the change succeeds, so the previous state can be restored unless a newer change would be overwritten.

## Reader lifecycle

```mermaid
flowchart TD
    Open["Open reader"] --> Resolve["Resolve optional local schedule"]
    Resolve --> Allowed{"Reading allowed?"}
    Allowed -->|Yes| Read["Read current chapter"]
    Allowed -->|No| Block["Show blocked state"]
    Read --> Complete{"Genuine latest-chapter completion?"}
    Complete -->|No| Continue["Continue or exit normally"]
    Complete -->|Yes| Defer["Defer rating prompt until reader exit"]
    Defer --> Choice["Love, Like, Dislike, Not interested, or skip"]
    Choice --> Offer["Option to rate linked versions"]
    Offer --> Exit["Return through normal navigation"]
```

The schedule is local, optional, and off by default. The reader checks it when it opens and whenever the app returns to the foreground. Moving to another chapter cannot bypass a restriction. The completion prompt appears only after you finish the latest available chapter, and it waits until you exit so it does not interrupt reading. The manga toolbar also shows **Jump to last read** when a valid reading position exists.

## Privacy and data boundaries

```mermaid
flowchart TD
    App["Komikku KMK"] --> Local["Ratings, history, settings, and OCR index stay on the device"]
    App --> Sources["Online catalogue work goes through installed source extensions"]
    App --> Sharing["Evaluation Mode can hide source labels before a screenshot is shared"]
    App --> Files["Exports and backups use Android's user-chosen document destination"]
```

KMK does not add a separate account or recommendation server. Ratings, recommendation settings, reading history, Action History, and the OCR index are stored locally. Installed source extensions still handle their own online catalogue requests.

Evaluation Mode changes visible source labels without changing saved identifiers, requests, or actions. OCR text can be rebuilt from downloaded pages, so it is left out of backup and sync and can be cleared without deleting those pages. Backups can include KMK data that is harder to recreate, such as ratings, recommendation settings, linked versions, and source evaluations.
