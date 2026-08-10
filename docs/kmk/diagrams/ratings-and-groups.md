# Ratings and manga-group diagrams

These diagrams cover Love, Like, Dislike, Not Interested, linked versions, group maintenance, and reversible preference changes.

## Preference states

```mermaid
stateDiagram-v2
    [*] --> Neutral
    Neutral --> Love
    Neutral --> Like
    Neutral --> Dislike
    Neutral --> NotInterested: Not interested
    Love --> Neutral: Clear rating
    Like --> Neutral: Clear rating
    Dislike --> Neutral: Clear rating
    NotInterested --> Neutral: Undo
    NotInterested --> Love: Choose Love
    NotInterested --> Like: Choose Like
    NotInterested --> Dislike: Choose Dislike
```

Not Interested is saved in the same way as Love, Like, and Dislike. Replacing it with another preference is recorded as one change.

## Reversible preference write

```mermaid
sequenceDiagram
    actor User
    participant Screen as Manga screen
    participant Model as Manga screen model
    participant History as Action History recorder
    participant Store as Preference store

    User->>Screen: Choose or clear a preference
    Screen->>Model: Send preference action
    Model->>History: Prepare previous and requested values
    Model->>Store: Save requested value
    alt Save succeeds
        Store-->>Model: Saved
        Model->>History: Commit history entry
        Model-->>Screen: Show new marker
    else Save fails or is cancelled
        Store-->>Model: Failure or cancellation
        Model->>History: Discard prepared entry
        Model-->>Screen: Keep previous state
    end
```

History is committed only after storage succeeds. Cancellation does not become a successful action.

## Create or merge a group

```mermaid
flowchart TD
    Select["Select two or more manga versions"] --> Resolve{"Selection is unambiguous?"}
    Resolve -->|No| Explain["Explain the conflict"]
    Resolve -->|Yes| Confirm["Confirm grouping"]
    Confirm --> Load["Load existing groups and links"]
    Load --> Plan["Plan target group and link changes"]
    Plan --> Persist["Save links and primary version"]
    Persist --> Display["Refresh grouped display"]
```

The app asks for confirmation before combining separate versions or existing groups.

## Maintain a group

```mermaid
flowchart LR
    Group["Linked manga group"] --> Primary["Choose primary version"]
    Group --> Remove["Remove one version"]
    Group --> Ungroup["Ungroup all versions"]
    Group --> Merge["Merge with another group"]
    Remove --> Valid{"At least two versions remain?"}
    Valid -->|Yes| Group
    Valid -->|No| Separate["Return to separate manga"]
    Ungroup --> Separate
    Merge --> Group
```

Removing a version does not silently leave an invalid one-item group.

## Grouped display

```mermaid
flowchart LR
    Stored["Ratings, links, and primary version"] --> Build["Build display groups"]
    Build --> Dedupe["Remove duplicate versions"]
    Dedupe --> Sort["Apply collection and sort settings"]
    Sort --> Collections["Loved, Liked, Disliked, and Not Interested"]
```

Collections come from the saved preferences and linked versions, so they stay consistent across screens.
