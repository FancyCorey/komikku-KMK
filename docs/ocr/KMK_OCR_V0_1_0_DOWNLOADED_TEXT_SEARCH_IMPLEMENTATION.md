# KMK-OCR v0.1.0 â€” Downloaded Text Search: Implementation Notes

> Branch/APK line: **KMK-OCR** (separate from KMK-Recs)
> APK: `Komikku-v1.13.6-kmk-ocr.0.1-debug.apk`
> versionCode: 80

---

## What this is

Adds a local, on-device OCR feature that lets the user index and search the text content of already-downloaded manga chapters. Accessed via **More â†’ Search Downloads (OCR)**.

Key constraints:
- Local/on-device only. No network OCR, no telemetry.
- Only scans downloaded chapters. Never fetches or downloads anything.
- Does NOT run automatically. User must explicitly trigger indexing.
- Pages and OCR text are never uploaded anywhere.

---

## OCR Engine

Google ML Kit Text Recognition v2, bundled Latin model.

- Dependency: `com.google.mlkit:text-recognition:16.0.1` (see `gradle/libs.versions.toml`)
- Engine key: `mlkit-latin`
- Engine version: `16.0.1`
- Language support: Latin-script languages (English, French, Spanish, German, etc.)
- Images are downsampled to 1280px max dimension before recognition (`RGB_565` config)

---

## Architecture

### Files added (all in `app/src/main/java/exh/ocr/`)

| File | Purpose |
|------|---------|
| `OcrTextRecognizer.kt` | Interface + `MlKitLatinOcrTextRecognizer` implementation |
| `OcrSearchQueryNormalizer.kt` | Lowercases, strips punctuation, builds match snippets |
| `OcrSearchResult.kt` | `OcrSearchResult` + `OcrIndexStats` data classes |
| `OcrDownloadPageProvider.kt` | Enumerates downloaded pages; handles both directory and `.cbz` archive chapters |
| `OcrIndexRepository.kt` | DB read/write via `DatabaseHandler` + SQLDelight |
| `OcrIndexService.kt` | Core indexing loop with skip-unchanged logic |
| `OcrIndexWorker.kt` | `CoroutineWorker` â€” runs indexing as a foreground WorkManager job |
| `OcrJobState.kt` | Singleton `StateFlow` for live progress (shared between Worker and Screen) |
| `OcrNotifier.kt` | Progress and completion notifications on `CHANNEL_OCR_INDEXING` |
| `OcrSearchScreenModel.kt` | `StateScreenModel` for the search UI |
| `OcrSearchScreen.kt` | Voyager screen â€” search field, stats, progress, action buttons, results |

### Database

Migration: `data/src/main/sqldelight/tachiyomi/migrations/54.sqm`
Table + queries: `data/src/main/sqldelight/tachiyomi/data/ocr_indexed_page.sq`

Table `ocr_indexed_page` stores one row per indexed page. Key columns:
- `manga_id`, `chapter_id`, `page_index` â€” location
- `page_identity` â€” URI or archive-path string; used to detect unchanged pages
- `engine_key`, `engine_version` â€” OCR engine identity
- `raw_text`, `normalized_text` â€” OCR output; normalized is lowercased with punctuation removed
- `error_message` â€” non-null when the page failed; excluded from search results

Unique constraint: `(chapter_id, page_index, page_identity, engine_key, engine_version)` â€” `upsertPage` uses `ON CONFLICT â€¦ DO UPDATE`.

Search query: `WHERE normalized_text LIKE '%' || :query || '%' AND error_message IS NULL`

### Skip-unchanged logic

Before OCR-ing a page, `OcrIndexService` calls `getExistingIdentity(chapterId, pageIndex, engineKey)`. If the stored `page_identity` matches the current page's identity AND the stored `engine_version` matches the current engine version, the page is skipped. This makes re-indexing fast.

### Archive chapter handling

For `.cbz` chapters, entry names are collected inside `archiveReader.useEntries {}` (the reader is closed when the block exits). Each page's `streamProvider` lambda re-opens the archive to get a fresh stream. This avoids using a closed reader.

### Notification

Channel: `CHANNEL_OCR_INDEXING = "ocr_indexing_channel"` (low importance, no badge)
IDs: `ID_OCR_INDEX_PROGRESS = -901`, `ID_OCR_INDEX_COMPLETE = -902`

There is no cancel button in the notification. Cancel is available from the Search Downloads screen.

### Deep link

`Constants.OPEN_OCR_SEARCH = "eu.kanade.tachiyomi.OPEN_OCR_SEARCH"` â€” handled in `MainActivity`, opens `OcrSearchScreen`.

---

## Entry points

- **More screen**: `MoreScreen.kt` â€” "Search Downloads (OCR)" item with `Icons.Outlined.Search`
- **MoreTab**: `MoreTab.kt` â€” routes to `OcrSearchScreen()`
- **MainActivity deep link**: handles `OPEN_OCR_SEARCH` action from the completion notification

---

## DI

`OcrIndexRepository` is registered in `KMKDomainModule` as a singleton:
```kotlin
addSingletonFactory { OcrIndexRepository(get()) }
```

`OcrIndexWorker` uses `CoroutineWorker` and is registered with WorkManager automatically via manifest entry (no manual factory needed for this pattern).

---

## Tests

`app/src/test/java/exh/ocr/OcrSearchQueryNormalizerTest.kt` â€” 8 unit tests covering `normalize()` and `buildSnippet()`.

---

## Build notes

- versionCode `80` (must be > 79, the previous KMK-Recs build)
- `gradle.properties` has `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` in `org.gradle.jvmargs` â€” required for the bundled JDK17 to trust Google/Maven certificate roots on Windows

---

## What OCR does NOT do

- No automatic/background indexing on app launch
- No cloud/server OCR
- No indexing of chapters that aren't downloaded
- No support for CJK, Arabic, Cyrillic, or other non-Latin scripts (ML Kit Latin model only)
- No full-text export or sync

