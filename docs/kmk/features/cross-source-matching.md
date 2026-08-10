# Cross-source matching and Best Version

These diagrams follow a manga from cross-source matching through comparison and the existing Komikku migration flow.

## Where you find it

Open a manga and choose **Find other versions** from its actions. After compatible versions are linked and enough comparison data exists, **Best Version** can compare them before handing an accepted change to Komikku's migration flow.

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

## Implementation reference

| Responsibility | Source |
| --- | --- |
| Present matching versions and selection across sources | [`CrossExtensionMatchScreen`](../../../app/src/main/java/exh/recs/matching/CrossExtensionMatchScreen.kt) |
| Prepare comparable candidates and own Best Version state | [`BestVersionCompareScreenModel`](../../../app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt) |
| Continue through Komikku's established migration workflow | [`migrating` package](../../../app/src/main/java/eu/kanade/tachiyomi/ui/browse/migration/manga/) |

KMK owns matching, linking, and comparison. When the reader chooses another version, the existing Komikku migration flow remains responsible for the actual library migration.
