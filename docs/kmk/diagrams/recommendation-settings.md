# Recommendation-settings diagrams

Recommendation settings use Komikku's established list, section, search, and navigation patterns.

## Navigation map

```mermaid
flowchart TD
    Entry["Recommendation settings"] --> ForYou["For You sources"]
    Entry --> Taste["Taste and filters"]
    Entry --> Evaluation["Source Evaluation"]
    Entry --> Try["Sources to try"]
    Entry --> Management["Management and diagnostics"]
    Entry --> Search["Search settings"]
```

The five visual sections group related controls and keep the first page short enough to scan.

## Settings affect features

```mermaid
flowchart LR
    Settings["Validated saved settings"] --> ForYou["For You retrieval and ranking"]
    Settings --> Evaluation["Source Evaluation"]
    Settings --> Suggestions["Sources to try"]
    Settings --> Matching["Cross-source matching"]
    Settings --> Diagnostics["Management and diagnostics"]
```

Values are checked before use. Invalid stored numbers fall back to safe limits instead of reaching feature policies unchanged.

## Search for a setting

```mermaid
sequenceDiagram
    actor User
    participant Search as Settings search
    participant Index as Search index
    participant Screen as Destination screen
    participant Store as Saved settings

    User->>Search: Enter words from a setting name or summary
    Search->>Index: Normalize and rank the query
    Index-->>Search: Matching destinations
    Search-->>User: Show section, title, and summary
    User->>Search: Select a result
    Search->>Screen: Open the destination and anchor
    Screen->>Store: Read current value
    Store-->>Screen: Validated setting state
```

Search results explain both the setting and the section that contains it.

## Quick-access panel

```mermaid
stateDiagram-v2
    [*] --> Closed
    Closed --> Open: Select edge handle
    Open --> Destination: Select feature shortcut
    Destination --> Closed: Open destination
    Open --> Closed: Select outside or press Back
```

The tablet quick-access panel provides the same destinations as the main settings index. Its handle and rows use stable touch targets and dismiss through normal Back behavior.
