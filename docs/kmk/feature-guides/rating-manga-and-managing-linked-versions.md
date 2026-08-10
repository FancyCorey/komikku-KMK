# Rating manga and managing linked versions

These diagrams cover Love, Like, Dislike, Not Interested, linked versions, group maintenance, and reversible preference changes.

## Where you find it

Use the preference action on a manga page to choose Love, Like, Dislike, or Not Interested. The For You menu opens the corresponding collections, while linked-version actions can apply a preference to selected matching versions.

## One preference model, four visible choices

Love, Like, Dislike, and Not Interested are peers in the manga preference interface. Each has a distinct label, icon, selected treatment, collection, and reversible transition. Not Interested is not hidden as an unrelated “seen” flag: selecting it replaces the neutral Rate affordance, clearing it is explicit, and selecting another rating performs one coherent preference change.

The collections help the reader review past choices. A manga belongs to the collection matching its current preference, while linked versions remain separate records unless the reader deliberately applies a choice to additional versions.

## What Action History can restore

Before a supported local preference write, KMK records the previous value. The history entry is committed only after the write succeeds. Undo first checks that the current value still matches the action being reversed; if another change occurred later, the app reports a conflict instead of overwriting the newer choice.

Actions owned by Android, a remote tracker, or another outside service are not described as fully reversible local actions. The history screen distinguishes those boundaries rather than presenting every event as if the app could restore it.

## Linked-version behavior

Linked versions group manga that the reader has confirmed as related. The group can identify a primary version for display and comparison, but grouping does not merge source records or erase their separate chapter lists. Adding, removing, or changing the primary member validates the group before saving so an invalid reference is not silently retained.

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

## Implementation reference

| Responsibility | Source |
| --- | --- |
| Define the visible Love, Like, Dislike, and Not Interested peer states | [`MangaPreferencePresentationPolicy`](../../../app/src/main/java/exh/recs/loved/MangaPreferencePresentationPolicy.kt) |
| Apply preference changes from the manga screen | [`MangaScreenModel`](../../../app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt) |
| Record reversible local changes before and after a successful write | [`EvaluationModeJournalRecorder`](../../../app/src/main/java/exh/util/EvaluationModeJournalRecorder.kt) |
| Restore a supported change only when the current value still matches | [`EvaluationModeUndoService`](../../../app/src/main/java/exh/util/EvaluationModeUndoService.kt) |

Not Interested is treated as a visible preference alongside the three rating levels. It has its own collection and marker, participates in cross-version actions, and uses the same conflict-aware undo boundary.
