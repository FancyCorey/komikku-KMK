# Sources to try and source-priority diagrams

These flows explain how the app suggests additional sources and how source preferences influence For You.

## Sources to try

```mermaid
flowchart LR
    Available["Available extension records"] --> Suggestions["Source suggestion policy"]
    Evaluation["Evaluation and recommendation-fit data"] --> Suggestions
    Taste["Language and taste settings"] --> Suggestions
    Suggestions --> List["Compatible non-installed sources"]
    List --> Handoff["Open normal extension installation"]
    Handoff --> Installed["Installed sources"]
```

Suggestions do not install anything automatically. The normal extension flow remains responsible for confirmation and installation.

## Suggestion decision

```mermaid
flowchart TD
    Candidate["Candidate source"] --> Installed{"Already installed?"}
    Installed -->|Yes| Hide["Do not suggest"]
    Installed -->|No| Eligible{"Language, safety, and repository checks pass?"}
    Eligible -->|No| Unavailable["Hide or explain unavailable state"]
    Eligible -->|Yes| Evidence["Apply evaluation and taste signals"]
    Evidence --> Order["Order suggestions"]
    Order --> Show["Show source suggestion"]
```

Unavailable repository or network data produces an honest unavailable state rather than sample release data.

## Source priority in For You

```mermaid
flowchart TD
    Visible["Installed and visible sources"] --> Language["Selected languages"]
    Language --> Enabled["Enabled recommendation sources"]
    Enabled --> Order["Stored source order"]
    Order --> Preference["Source preference and quality signals"]
    Preference --> Retrieval["For You source retrieval"]
```

Source order affects retrieval priority but does not bypass candidate-level filters.

## Change source preferences

```mermaid
flowchart LR
    Row["Source settings row"] --> Enable["Enable or disable"]
    Row --> Reorder["Move priority"]
    Row --> Preference["Like or dislike source"]
    Row --> Quality["Mark explicit or poor quality"]
    Enable --> Save["Save source preference state"]
    Reorder --> Save
    Preference --> Save
    Quality --> Save
    Save --> Refresh["Recalculate affected recommendation state"]
```

The settings remain user-configurable and are validated before recommendation policies use them.
