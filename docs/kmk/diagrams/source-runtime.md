# Source runtime diagrams

The source runtime is the boundary between KMK features and installed extensions. It keeps one extension failure from breaking unrelated work.

## Runtime boundary

```mermaid
flowchart LR
    Caller["Feature requesting source work"] --> Runtime["SourceRuntime boundary"]
    Runtime --> Operation["Guarded extension operation"]
    Operation --> Result["Local result or status"]
    Runtime --> Registry["Failure registry"]
    Registry --> Result
```

Feature screens receive a result they can explain instead of handling raw extension failures directly.

## Failure classification

```mermaid
flowchart TD
    Throw["Operation throws"] --> Unwrap["Unwrap known wrapper causes"]
    Unwrap --> Kind{"What kind of failure is it?"}
    Kind -->|Cancellation| Cancel["Rethrow cancellation"]
    Kind -->|Fatal programming or system error| Fatal["Rethrow fatal error"]
    Kind -->|Recoverable source failure| Local["Record and return local failure"]
```

Cancellation and fatal errors are not disguised as empty or successful source results.

## Temporary suppression and retry

```mermaid
stateDiagram-v2
    [*] --> Available
    Available --> Suppressed: Recoverable initialization failure
    Suppressed --> Unavailable: Repeated request during suppression
    Unavailable --> Suppressed: No environment change
    Suppressed --> Available: User retries or failure is cleared
    Available --> [*]
```

Temporary suppression prevents repeated expensive initialization failures while leaving a clear retry path.

## Batch isolation

```mermaid
flowchart TD
    Batch["Start source batch"] --> One["Guard one source operation"]
    One --> Success{"Source succeeds?"}
    Success -->|Yes| Add["Add source results"]
    Success -->|No| Error["Add source-local explanation"]
    Add --> More{"More sources?"}
    Error --> More
    More -->|Yes| One
    More -->|No| Return["Return successful rows and local errors"]
```

The final batch state can contain both useful results and honest per-source failures.
