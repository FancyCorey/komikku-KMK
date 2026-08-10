# Source Evaluation diagrams

Source Evaluation checks whether an installed source can provide useful, compatible recommendation data. It saves short verdicts and diagnostics instead of raw errors.

## Evaluation overview

```mermaid
flowchart LR
    Settings["Source Evaluation settings"] --> Queue["Eligible source queue"]
    Queue --> Probes["Popular, recent, search, and metadata probes"]
    Probes --> Evidence["Evidence and scoring"]
    Evidence --> Verdict["Saved verdict and short diagnostics"]
```

Evaluation is bounded by the selected batch size and can continue after the screen is closed.

## Evaluate one source

```mermaid
flowchart TD
    Load["Resolve installed extension"] --> Open["Open source"]
    Open --> Catalogue["Read supported catalogue pages"]
    Catalogue --> Search["Check recommendation-search compatibility"]
    Search --> Metadata["Load missing metadata within limits"]
    Metadata --> Score["Score quality, fit, and confidence"]
    Score --> Save["Save verdict"]
    Save --> Cleanup["Release temporary source work"]
```

Catalogue quality and recommendation-search compatibility are separate signals. A source can succeed at one and fail at the other.

## Continue or reassess

```mermaid
flowchart TD
    Build["Build eligible queue"] --> Fingerprint["Compare current filters and extensions"]
    Fingerprint --> Mode{"New work or stale work?"}
    Mode -->|New| Cursor["Resume saved new-source cursor"]
    Mode -->|Stale| Restart["Start explicit reassessment queue"]
    Cursor --> Batch["Take next bounded batch"]
    Restart --> Batch
    Batch --> Persist["Save each completed result and cursor"]
    Persist --> Remaining["Show remaining actionable count"]
    Remaining --> Batch
```

Changing filters or installed extensions does not silently mix old and new evaluation assumptions.

## Failure isolation

```mermaid
flowchart TD
    Operation["Source or extension operation"] --> Result{"Operation succeeds?"}
    Result -->|Yes| Save["Score and save source result"]
    Result -->|No| Kind{"Cancellation, fatal error, or recoverable source failure?"}
    Kind -->|Cancellation or fatal| Rethrow["Stop or rethrow"]
    Kind -->|Recoverable| Record["Record general failure category"]
    Record --> Continue["Continue with next eligible source"]
```

Recoverable failures remain local to one source. Cancellation is never converted into a successful result.

## Evidence to verdict

```mermaid
flowchart LR
    Samples["Catalogue and search samples"] --> Summary["Evidence summary"]
    Metadata["Metadata completeness"] --> Summary
    Fit["Taste and recommendation fit"] --> Summary
    Safety["Extension and runtime safety"] --> Summary
    Summary --> Confidence{"Enough evidence?"}
    Confidence -->|Yes| Verdict["Useful, weak, or unsuitable"]
    Confidence -->|No| Partial["Partial or unavailable"]
```

The visible result explains confidence and category without exposing requests, credentials, or exception text.
