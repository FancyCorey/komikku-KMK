# Extension operation diagrams

## Load isolation

```mermaid
flowchart TD
    Packages["Installed extension packages"] --> Verify["Signature and compatibility checks"]
    Verify --> Safe{"Load allowed?"}
    Safe -->|Yes| Load["Load extension sources"]
    Safe -->|No| Isolate["Record typed local failure"]
    Isolate --> Continue["Continue loading unrelated packages"]
    Load --> Continue
```

One incompatible package produces a scoped outcome while unrelated installed extensions continue through normal loading.

## Install and remove

```mermaid
sequenceDiagram
    actor User
    participant App
    participant Android as Android package flow
    participant History as Action History
    User->>App: Confirm install or removal
    App->>Android: Request supported package operation
    Android-->>App: Success, cancellation, or failure
    App->>History: Record only a confirmed result
    App-->>User: Show bounded outcome and follow-up
```

The app records only a confirmed Android result. Cancellation and ordinary failure remain distinct outcomes.

## Exact-artifact export

```mermaid
flowchart TD
    Select["Select extension packages"] --> Picker["Android document picker"]
    Picker --> Write["Write chosen artifacts"]
    Write --> Verify["Verify exact created document"]
    Verify --> Choice{"Keep or remove created document?"}
    Choice -->|Keep| Done["Leave document in place"]
    Choice -->|Remove| Delete["Delete exact document URI only"]
```

Cleanup retains the exact returned document reference and never searches a folder by filename.

## Source evaluation handoff

```mermaid
flowchart LR
    Suggestion["Sources To Try suggestion"] --> Consent["User chooses install"]
    Consent --> Android["Supported Android install flow"]
    Android --> Installed{"Installed successfully?"}
    Installed -->|Yes| Evaluate["Offer source evaluation"]
    Installed -->|No| Outcome["Cancelled or failed state"]
```

Sources To Try can hand off to installation, but a source enters evaluation only after Android confirms the package is installed.
