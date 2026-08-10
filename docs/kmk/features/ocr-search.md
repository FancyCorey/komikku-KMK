# OCR search for downloads

OCR Search Downloads builds a local text index from downloaded pages, then lets the reader search that text and return to the matching manga, chapter, and page context.

## Where you find it

Open **OCR Search Downloads** from the app's search tools. Index controls choose the current manga or all downloaded manga; result and cleanup controls stay within the same feature.

## Entry and indexing

```mermaid
flowchart TD
    Open["Open OCR Search Downloads"] --> Scope{"Choose indexing scope"}
    Scope -->|Current manga| Current["Enumerate downloaded pages for one manga"]
    Scope -->|All downloads| All["Confirm broad local scan"]
    Current --> Worker["Start cancellable background work"]
    All --> Worker
    Worker --> Notice["Show progress notification"]
```

The broad scan requires confirmation because it can use significant time, storage, CPU, and battery. Both scopes use the same cancellable worker path.

## Page processing

```mermaid
sequenceDiagram
    participant Worker
    participant Files as Download page provider
    participant OCR as On-device recognizer
    participant Index as Local OCR index
    Worker->>Files: Read one downloaded page
    Files-->>Worker: Image bytes or typed file error
    Worker->>OCR: Recognize text on device
    OCR-->>Worker: Text, empty result, or typed recognition error
    Worker->>Index: Store short status and recognized text
```

Each page receives a success, empty, or clear failure status. Diagnostics do not include raw error messages or page identities.

## Search and navigation

```mermaid
flowchart LR
    Query["Normalized search words"] --> Index["Local index query"]
    Index --> Rank["Exact, all-word, and partial ranking"]
    Rank --> Results["Manga, chapter, and page results"]
    Results --> Reader["Open the matching reading context"]
```

Search gives stronger word matches a higher position and keeps enough page information to open the correct place in the reader.

## Privacy and cleanup

```mermaid
flowchart TD
    Text["Recognized page text"] --> Local["Local database only"]
    Local --> Search["On-device search"]
    Local -. excluded .-> Backup["Backup and sync payloads"]
    Local --> Clear{"Cleanup choice"}
    Clear --> Chapter["One chapter"]
    Clear --> Manga["One manga"]
    Clear --> Failed["Empty, failed, or old rows"]
    Clear --> All["Entire OCR index"]
```

The index is local and regenerable. Cleanup changes only index rows and does not delete the downloaded page files.

## Implementation reference

| Responsibility | Source |
| --- | --- |
| Own index, search, cancellation, result, and cleanup state | [`OcrSearchScreenModel`](../../../app/src/main/java/exh/ocr/OcrSearchScreenModel.kt) |
| Present indexing progress, search results, and cleanup choices | [`OcrSearchScreen`](../../../app/src/main/java/exh/ocr/OcrSearchScreen.kt) |
| Store and query searchable page records locally | [`OcrIndexRepository`](../../../app/src/main/java/exh/ocr/OcrIndexRepository.kt) |

Recognized text remains on the device and is excluded from KMK backup and sync. Cleanup removes index records, not manga pages or downloaded files.
