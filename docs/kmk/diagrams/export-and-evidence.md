# Export, Evaluation Mode, and screenshot diagrams

These diagrams explain how the app creates and cleans up exported files, hides source names in Evaluation Mode, and decides which screenshots are safe to publish.

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
    Screen->>Exporter: Start writing
    Exporter->>Source: Read selected export input
    Exporter->>Document: Write output
    Document-->>Exporter: Success, partial output, or failure
    Exporter-->>Screen: Result and exact cleanup reference
    Screen-->>User: Keep or remove the created document
```

Android creates the destination before the app writes to it. The app therefore keeps an exact reference for cleanup after failed or partial writes.

## Clean up the created file

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

## What Evaluation Mode changes

```mermaid
flowchart TD
    Stored["Real saved application data"] --> Toggle{"Evaluation Mode enabled?"}
    Toggle -->|No| Normal["Normal display labels"]
    Toggle -->|Yes| Formatter["Evaluation Mode formatter"]
    Formatter --> Neutral["Neutral source, extension, repository, and tag labels"]
    Stored --> Identity["Stable IDs and action targets"]
    Identity --> Behavior["Storage, source requests, and actions remain unchanged"]
```

Evaluation Mode changes visible labels only. It helps with screenshots but does not create a separate set of app data.

## Review a screenshot

```mermaid
flowchart TD
    Feature["Feature ready to show"] --> Mode["Enable Evaluation Mode when source labels are visible"]
    Mode --> Capture["Capture the app screen"]
    Capture --> Inspect["Check status bar, account, source, title preference, and path details"]
    Inspect --> Private{"Private detail remains?"}
    Private -->|Yes| Exclude["Crop, replace, or exclude capture"]
    Private -->|No| Hash["Record purpose, privacy check, and hash"]
    Exclude --> Inspect
    Hash --> Public["Approved public screenshot"]
```

Only screenshots that pass the documented privacy check are included in the public guide.
