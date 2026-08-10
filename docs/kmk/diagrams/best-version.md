# Find other versions and Best Version diagrams

These diagrams follow a manga from cross-source matching through comparison and the existing Komikku migration flow.

## Matching and comparison overview

```mermaid
flowchart LR
    Origin["Current manga"] --> Search["Find other versions"]
    Search --> Review["User reviews candidates"]
    Review --> Links["Confirmed linked versions"]
    Links --> Compare["Chapter and preview comparison"]
    Compare --> Choice["Keep current or choose another version"]
    Choice --> Migration["Komikku migration flow"]
```

The user confirms matches before they become linked versions.

## Prepare comparable versions

```mermaid
flowchart TD
    Origin["Load current manga"] --> Candidates["Search guarded source candidates"]
    Candidates --> Select["User selects correct versions"]
    Select --> Chapters["Load chapter lists"]
    Chapters --> Match["Resolve comparable chapter samples"]
    Match --> Available{"Comparable sample exists?"}
    Available -->|Yes| Ready["Version ready for comparison"]
    Available -->|No| Missing["Show unavailable reason"]
```

A missing chapter or preview is displayed as unavailable; it is not treated as a failed migration.

## Preview state

```mermaid
stateDiagram-v2
    [*] --> Checking
    Checking --> Unavailable: No matching chapter
    Checking --> Loading: Matching chapter found
    Loading --> Loaded: Pages fetched
    Loading --> RetryableError: Fetch failed
    RetryableError --> Loading: Retry
    Loaded --> [*]
    Unavailable --> [*]
```

Each linked version tracks its own preview. If one preview fails, the other comparisons remain visible.

## Migration result

```mermaid
flowchart TD
    Choose["Choose preferred version"] --> Confirm["Confirm migration"]
    Confirm --> Execute["Run existing migration steps"]
    Execute --> Result{"Target manga created?"}
    Result -->|Yes| Open["Open migrated manga"]
    Result -->|No, current kept| Kept["Report current version kept"]
    Execute -->|Step fails| Failed["Report completed and failed steps"]
```

The result clearly separates a successful migration, a decision to keep the current version, and a failure. It does not promise to undo changes that already finished outside this step.
