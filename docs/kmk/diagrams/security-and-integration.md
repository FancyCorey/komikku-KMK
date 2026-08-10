# Security and integration diagrams

## Navigation validation

```mermaid
flowchart TD
    Input["External URI or WebView navigation"] --> Parse["Parse complete URI"]
    Parse --> Allowed{"Allowed scheme and route?"}
    Allowed -->|No| Reject["Reject without navigation"]
    Allowed -->|Yes| Sanitize["Check the destination details"]
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

Cancelling an action stops it normally. Other failures stay with the action that caused them.

## Reversible local change

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
    Recorder->>History: Save undo record
    History-->>UI: Offer guarded restore
```

The app does not add an undo record when a change fails. Undo also refuses to overwrite a newer value.

## Clean up one document

```mermaid
flowchart TD
    Picker["Android document picker"] --> URI["Returned document URI"]
    URI --> Write["Write and verify document"]
    Write --> Keep{"User cleanup choice"}
    Keep -->|Keep| End["Keep document"]
    Keep -->|Remove| Exact["Delete returned URI only"]
    Exact --> Result["Report verified outcome"]
```

Cleanup removes only the document created by the current action. It never searches and clears a whole folder.
