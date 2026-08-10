# Export, Evaluation Mode, and evidence diagrams

These diagrams explain safe document creation, exact-artifact cleanup, anonymized presentation, and the public evidence boundary.

## Export sequence

```mermaid
sequenceDiagram
    actor User
    participant Screen as Export screen
    participant Picker as Android document picker
    participant Exporter as Export coordinator
    participant Source as Export input
    participant Document as Created document

    User->>Screen: Choose export
    Screen->>Picker: Request destination
    Picker-->>Screen: Created document reference or cancellation
    Screen->>Exporter: Begin bounded write
    Exporter->>Source: Read selected export input
    Exporter->>Document: Write output
    Document-->>Exporter: Success, partial output, or failure
    Exporter-->>Screen: Result and exact cleanup reference
    Screen-->>User: Keep or remove the created document
```

Android creates the destination before the app writes to it. The app therefore keeps an exact reference for cleanup after failed or partial writes.

## Exact-artifact cleanup

```mermaid
flowchart TD
    Reserved["Document destination created"] --> Write["Attempt export write"]
    Write --> Outcome{"Write outcome"}
    Outcome -->|Success| Offer["Offer Keep or Remove"]
    Outcome -->|Partial or failed| Offer
    Outcome -->|User cancelled before destination| End["No cleanup needed"]
    Offer -->|Keep| Clear["Forget cleanup reference"]
    Offer -->|Remove| Delete["Delete only the recorded document"]
    Delete --> Result{"Deletion succeeds?"}
    Result -->|Yes| Clear
    Result -->|No| Retain["Keep reference and explain failure"]
```

Cleanup never searches a folder or deletes unrelated files.

## Evaluation Mode boundary

```mermaid
flowchart TD
    Stored["Real saved application data"] --> Toggle{"Evaluation Mode enabled?"}
    Toggle -->|No| Normal["Normal display labels"]
    Toggle -->|Yes| Formatter["Evaluation Mode formatter"]
    Formatter --> Neutral["Neutral source, extension, repository, and tag labels"]
    Stored --> Identity["Stable IDs and action targets"]
    Identity --> Behavior["Storage, source requests, and actions remain unchanged"]
```

Evaluation Mode changes presentation only. It is a screenshot aid, not a separate data mode.

## Evidence review

```mermaid
flowchart TD
    Feature["Verified feature state"] --> Mode["Enable Evaluation Mode when source labels are visible"]
    Mode --> Capture["Capture the app surface"]
    Capture --> Inspect["Check status bar, account, source, title preference, and path details"]
    Inspect --> Private{"Private detail remains?"}
    Private -->|Yes| Exclude["Crop, replace, or exclude capture"]
    Private -->|No| Hash["Record purpose, privacy treatment, and hash"]
    Exclude --> Inspect
    Hash --> Public["Approved public evidence"]
```

The public evidence set includes only images whose visible content matches the documented privacy treatment.
