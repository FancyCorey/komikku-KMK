# For You diagrams

These diagrams explain how For You gathers recommendations, applies user settings, adds controlled variety, and remembers which cards have already been shown.

## Feature overview

```mermaid
flowchart LR
    Screen["For You screen"] --> Model["Recommendation screen model"]
    Model --> Retrieval["Guarded source retrieval"]
    Retrieval --> Eligibility["Eligibility and preference checks"]
    Eligibility --> Ranking["Personalized and recent-catalogue ranking"]
    Ranking --> Rows["Rows, status, and explanations"]
```

The screen model coordinates the work. Source failures are contained before candidates reach filtering and ranking.

## Retrieval and display

```mermaid
sequenceDiagram
    actor User
    participant Screen as For You screen
    participant Model as Screen model
    participant Settings as Settings and preferences
    participant Memory as Cache and exposure memory
    participant Runtime as Source runtime
    participant Policy as Filtering and ranking

    User->>Screen: Open or refresh For You
    Screen->>Model: Request recommendations
    Model->>Settings: Read languages, filters, limits, and source order
    Model->>Memory: Read cached candidates and visible-card history
    loop Eligible sources
        Model->>Runtime: Request search or recent catalogue candidates
        Runtime-->>Model: Candidates or a source-local failure
    end
    Model->>Policy: Filter, merge, diversify, and rank
    Policy-->>Model: Visible rows and explanations
    Model->>Memory: Save cache, progress, and visible exposure
    Model-->>Screen: Loaded, partial, empty, or error state
```

Each source is handled independently. One failing source can produce a row-level explanation without discarding successful rows.

## Candidate eligibility

```mermaid
flowchart TD
    Candidate["Candidate discovered"] --> Rules{"Pass language, source, genre, exclusion, and chapter rules?"}
    Rules -->|No| Exclude["Exclude candidate"]
    Rules -->|Yes| Enrich["Load available metadata"]
    Enrich --> Evidence{"Enough information to evaluate?"}
    Evidence -->|No| Skip["Skip unsupported candidate"]
    Evidence -->|Yes| Lane{"Personalized match or recent exploration?"}
    Lane --> Personalized["Personalized lane"]
    Lane --> Recent["Recent-catalogue lane"]
    Personalized --> Merge["Enforce personalized majority when possible"]
    Recent --> Merge
    Merge --> Visible["Visible ranked candidate"]
```

Recent-catalogue entries add variety but never bypass the normal safety and preference rules.

## Exposure-aware refresh

```mermaid
flowchart TD
    Visible["Card enters a visible result"] --> Record["Record source, manga, and time"]
    Record --> Window{"Untouched for the configured window?"}
    Window -->|No| Keep["Keep normal rank"]
    Window -->|Yes| Exempt{"In library, rated, or known tracked?"}
    Exempt -->|Yes| Keep
    Exempt -->|No| Lower["Move lower in the same result set"]
    Lower --> Refresh["Allow other eligible cards to move up"]
```

Exposure memory changes ordering only. It does not delete manga or override library, preference, or verified tracking state.
