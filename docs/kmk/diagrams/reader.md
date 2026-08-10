# Reader-control diagrams

These diagrams cover chapter loading, completion preferences, the optional schedule and timer, and Jump to last read.

## Chapter reading sequence

```mermaid
sequenceDiagram
    actor User
    participant Activity as Reader activity
    participant Model as Reader view model
    participant Loader as Page loader
    participant Viewer as Reader viewer

    User->>Activity: Open chapter
    Activity->>Model: Load chapter and reader settings
    Model->>Loader: Request page list and pages
    Loader-->>Model: Pages or page-local failure
    Model->>Viewer: Apply pages and viewer configuration
    Viewer-->>User: Render readable content
    User->>Viewer: Navigate pages
    Viewer-->>Model: Report progress
    Model-->>Activity: Update chapter state and controls
```

Page failures remain local to the affected load while reader progress continues through the existing Komikku ownership path.

## Completion preference

```mermaid
flowchart TD
    Progress["Reader reaches final page"] --> Genuine{"Latest available chapter and valid completion?"}
    Genuine -->|No| Normal["Continue or exit normally"]
    Genuine -->|Yes| Pending["Store pending completion prompt"]
    Pending --> Exit["Reader exit requested"]
    Exit --> Prompt["Show clear preference choices"]
    Prompt -->|Love, Like, Dislike, or Not Interested| Save["Save selected preference"]
    Prompt -->|Cancel or Back| Return["Return without a preference change"]
    Save --> Linked{"Linked versions available?"}
    Linked -->|Yes| Offer["Offer linked-version rating"]
    Linked -->|No| Return
    Offer --> Return
```

The prompt appears after reader exit, not over the final page. Cancel and confirmation remain separate actions.

## Reader settings flow

```mermaid
flowchart LR
    Settings["Reader settings"] --> Preferences["Reader preferences"]
    Preferences --> Model["Reader settings model"]
    Model --> Config["Viewer configuration"]
    Config --> Viewer["Pager or webtoon viewer"]
    Viewer --> Controls["Reader controls and indicators"]
```

Optional KMK controls extend the existing settings and viewer configuration rather than replacing them.

## Timer and schedule enforcement

```mermaid
flowchart TD
    Request["Open reader or move chapter"] --> Resolve["Resolve current schedule window"]
    Resolve --> Allowed{"Reading permitted?"}
    Allowed -->|Yes| Open["Open or continue chapter"]
    Allowed -->|Current chapter grace| Grace["Allow only the current chapter"]
    Allowed -->|No| Block["Block new reading and explain why"]
    Open --> Timer["Track foreground reading time"]
    Grace --> Timer
    Timer --> Expired{"Timer or schedule window expires?"}
    Expired -->|No| Open
    Expired -->|Yes| Block
```

Alternate chapter navigation cannot bypass a restriction. The schedule remains local and off by default.

## Jump to last read

```mermaid
flowchart TD
    Manga["Open manga details"] --> Resolve["Resolve last-read chapter from saved chapter state"]
    Resolve --> Target{"Valid visible target exists?"}
    Target -->|No| Unavailable["Hide or disable jump action"]
    Target -->|Yes| Action["Show Jump to last read"]
    Action --> Scroll["Scroll chapter list to target"]
    Scroll --> Preserve["Keep read, bookmark, and download state unchanged"]
```

Jump to last read changes only the list position. It does not open a chapter or modify reading history.
