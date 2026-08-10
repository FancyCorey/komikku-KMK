# Security and integration diagrams

## Navigation validation

```mermaid
flowchart TD
    Input["External URI or WebView navigation"] --> Parse["Parse complete URI"]
    Parse --> Allowed{"Allowed scheme and route?"}
    Allowed -->|No| Reject["Reject without navigation"]
    Allowed -->|Yes| Sanitize["Resolve bounded route arguments"]
    Sanitize --> Navigate["Open owned destination"]
```

Malformed values and scheme-prefix lookalikes do not reach application routes.

## Failure isolation

```mermaid
flowchart LR
    Call["Source or extension call"] --> Boundary["Guarded runtime boundary"]
    Boundary --> Success["Typed success"]
    Boundary --> Failure["Typed isolated failure"]
    Boundary --> Cancel["Rethrow cancellation"]
    Failure --> Continue["Continue unrelated operations"]
```

Cancellation keeps its control-flow meaning, while ordinary failures remain local to the operation that produced them.

## Reversible local mutation

```mermaid
sequenceDiagram
    participant UI
    participant Recorder
    participant Store
    participant History
    UI->>Recorder: Request supported change
    Recorder->>Store: Read previous value
    Recorder->>Store: Apply change
    Store-->>Recorder: Confirm success
    Recorder->>History: Commit bounded receipt
    History-->>UI: Offer guarded restore
```

A receipt is never committed for a failed write, and restoration refuses to overwrite a newer conflicting value.

## Scoped document cleanup

```mermaid
flowchart TD
    Picker["Android document picker"] --> URI["Returned document URI"]
    URI --> Write["Write and verify artifact"]
    Write --> Keep{"User cleanup choice"}
    Keep -->|Keep| End["Retain artifact"]
    Keep -->|Remove| Exact["Delete returned URI only"]
    Exact --> Result["Report verified outcome"]
```

Cleanup operates on the exact artifact created by the current operation and never performs a broad folder sweep.
