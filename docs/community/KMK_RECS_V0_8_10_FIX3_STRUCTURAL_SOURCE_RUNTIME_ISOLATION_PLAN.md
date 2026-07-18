# KMK-Recs v0.8.10 Fix 3: Structural Source Runtime Isolation

**Status:** Planned
**Scope:** Structural crash/stability repair, not a feature expansion
**Build naming:** `Komikku-v1.14.0-kmk.8.10-fix3-debug.apk`
**Reason:** v0.8.10-fix2 correctly classified extension `LinkageError` failures in a few recommendation paths, but the real defect is architectural: loaded extension sources are executed from many independent call sites without one shared runtime boundary.

## Claude Execution Assignment

Recommended model: Sonnet 5

Recommended effort: Low, per user preference for token-efficient implementation after Codex has prepared the detailed plan.

Why: this is a broad but mechanically specified stability repair. The plan identifies the runtime boundary to create, the failure policy to preserve, the exact source-operation families to inventory, and the code areas to migrate. Claude should spend its budget implementing and testing, not re-discovering the architecture from scratch.

Token/throughput tradeoff: do not use Opus by default for this pass. If Sonnet 5 low reaches a genuine contradiction in the code, a repeatedly failing test with unclear cause, or an architectural mismatch with this plan, stop and report the exact blocker instead of continuing to burn context.

When to escalate: only escalate model/effort if a specific source API path cannot be safely wrapped without understanding deeper official Komikku/Mihon architecture, or if tests show fatal/cancellation propagation is being changed accidentally.

When to reduce effort: if a call site is clearly a simple direct source operation, migrate it using the shared `SourceRuntime` helper and move on. Do not over-analyze already-classified mechanical replacements.

Required verification: run the source-runtime unit tests, affected recommendation/source-evaluation/search tests, `spotlessCheck`, `:app:testDebugUnitTest`, and `assembleDebug`. Copy the APK to the documented handoff path only after all required verification passes or any skipped verification is explicitly documented.

## Executive Summary

The confirmed real-device crash is:

```text
java.lang.NoClassDefFoundError: Failed resolution of: Lokhttp3/zstd/Zstd;
Caused by: java.lang.ClassNotFoundException: Didn't find class "okhttp3.zstd.Zstd"
Extension: eu.kanade.tachiyomi.extension.en.asurascans
```

This is not just an AsuraScans screen issue. The user observed crashes while loading For You, opening Browse, and updating unrelated extensions. That happens because KMK and Komikku flows can touch installed extension sources globally or in bulk.

The previous fix is a band-aid because it protects only selected callers. The structural fix must create one reusable source-operation guard and migrate every source-runtime operation that can execute extension code.

## Current Code Reality

### What Official Komikku Already Handles

`app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`

- Validates package metadata, signatures, lib version, trust, and NSFW load state.
- Builds the extension `ChildFirstPathClassLoader`.
- Instantiates each source class.
- Catches `Throwable` while constructing sources.
- If construction fails, Komikku returns `LoadResult.Error`.

### What Official Komikku Does Not Centrally Handle

After an extension source is loaded, `AndroidSourceManager` stores the raw `Source` object:

`app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`

- `sourcesMapFlow` holds loaded `Source` instances.
- `get(sourceKey)` returns the raw source.
- `getOnlineSources()`, `getVisibleOnlineSources()`, and `getVisibleSources()` return raw source instances.
- There is no runtime wrapper around later calls such as:
  - `getFilterList()`
  - `getSearchManga()`
  - `getPopularManga()`
  - `getLatestUpdates()`
  - `getMangaUpdate()`
  - `getPageList()`
  - `getImageUrl()`
  - `getRelatedMangaList()`

This is why a source that loads successfully can still crash later when a lazy client, interceptor, parser, or dependency is first touched.

### Why KMK Exposes The Problem More

Official Komikku usually executes a source because the user directly opened or searched that source. KMK added bulk/global features that automatically execute many sources:

- For You rows
- Top Picks / group recommendation previews
- Source Evaluation
- For You search compatibility checks
- source recommendation quality probes
- cross-extension matching
- same-manga matching
- best-version chapter comparison
- source suggestion/evaluation flows

Those features make a single broken installed extension much more likely to be invoked from unrelated-looking screens.

## Source API Methods That Need A Boundary

The public source surface is in:

- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt`
- `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt`

Guard these operations at app call sites:

```kotlin
fun getFilterList(): FilterList
suspend fun getPopularManga(page: Int): MangasPage
suspend fun getLatestUpdates(page: Int): MangasPage
suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage
suspend fun getMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate
suspend fun getPageList(chapter: SChapter): List<Page>
suspend fun getRelatedMangaList(...)
suspend fun getRelatedMangaListByExtension(...)
suspend fun getRelatedMangaListBySearch(...)
open suspend fun HttpSource.getImageUrl(page: Page): String
```

Do not modify the source API signatures. The guard belongs in the app/runtime layer so extension compatibility is not broken.

## Required Architecture

### 1. Add A Shared Source Runtime Guard

Create a new app-layer utility, recommended location:

`app/src/main/java/eu/kanade/tachiyomi/source/SourceRuntime.kt`

If Claude finds an existing official Komikku/Mihon helper with the same purpose, reuse it instead and document the exact file. Do not create duplicate helpers.

The helper should provide:

```kotlin
enum class SourceRuntimeOperation {
    FilterList,
    Popular,
    Latest,
    Search,
    MangaUpdate,
    PageList,
    ImageUrl,
    RelatedManga,
}

sealed class SourceRuntimeFailureKind {
    data object ExtensionIncompatible : SourceRuntimeFailureKind()
    data object Network : SourceRuntimeFailureKind()
    data object Timeout : SourceRuntimeFailureKind()
    data object SourceNotInstalled : SourceRuntimeFailureKind()
    data object Unsupported : SourceRuntimeFailureKind()
    data object Internal : SourceRuntimeFailureKind()
}

data class SourceRuntimeFailure(
    val sourceId: Long,
    val sourceName: String,
    val sourceLang: String,
    val operation: SourceRuntimeOperation,
    val kind: SourceRuntimeFailureKind,
    val throwable: Throwable,
)
```

Required behavior:

- `CancellationException` must always be rethrown.
- `VirtualMachineError`, `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`, and `AssertionError` must be rethrown unless Claude proves the existing app already treats a subtype as recoverable.
- `LinkageError` must be treated as a recoverable source-scoped failure:
  - `NoClassDefFoundError`
  - `NoSuchMethodError`
  - `NoSuchFieldError`
  - `IncompatibleClassChangeError`
  - `ExceptionInInitializerError`
  - generic `LinkageError`
- Wrapped linkage failures must be unwrapped before classification:
  - `java.util.concurrent.ExecutionException`
  - `java.util.concurrent.CompletionException`
  - `java.lang.reflect.InvocationTargetException`
- Do not classify all `Error` as recoverable.
- Do not use a process-level `CoroutineExceptionHandler` as the fix.

Recommended API:

```kotlin
suspend inline fun <T> SourceRuntime.run(
    source: Source,
    operation: SourceRuntimeOperation,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline block: suspend Source.() -> T,
): Result<T>

inline fun <T> SourceRuntime.runBlockingSourceCall(
    source: Source,
    operation: SourceRuntimeOperation,
    block: Source.() -> T,
): Result<T>

fun Throwable.unwrapSourceRuntimeCause(): Throwable
fun Throwable.isRecoverableSourceRuntimeFailure(): Boolean
fun Throwable.toSourceRuntimeFailureKind(): SourceRuntimeFailureKind
```

`run()` should execute on the supplied dispatcher unless the caller is already intentionally controlling threading. The goal is both stability and consistency.

### 2. Keep `RecommendationErrorClassifier`, But Stop Using It As The Universal Boundary

Current file:

`app/src/main/java/exh/recs/RecommendationErrorClassifier.kt`

Do not delete it. It is still useful for recommendation UI/storage keys.

Required change:

- Either delegate its source-failure logic to `SourceRuntime`, or make both use the same lower-level classifier.
- Avoid two divergent policies where recommendations classify `LinkageError` differently from Browse/source evaluation.
- Add unwrap behavior here too if `RecommendationErrorClassifier` remains externally visible.

### 3. Add A Runtime Failure Registry

Create an in-memory registry so the app does not repeatedly hammer a known-broken source during the same process lifetime.

Recommended location:

`app/src/main/java/eu/kanade/tachiyomi/source/SourceRuntimeFailureRegistry.kt`

Required behavior:

- Key by `source.id`.
- Store source name, lang, package if available, operation, kind, first failure time, last failure time, and count.
- Provide:
  - `record(failure: SourceRuntimeFailure)`
  - `get(sourceId: Long)`
  - `isTemporarilyUnavailable(sourceId: Long)`
  - `clear(sourceId: Long)`
  - `clearAll()`
  - `failures: StateFlow<List<...>>` if useful for UI/diagnostics.
- Use a short TTL or process-lifetime suppression for extension-incompatible/linkage failures.
- Do not permanently disable or uninstall an extension without explicit user action.
- Do not abuse Source Evaluation quarantine for general Browse/reader failures unless Claude confirms that repository was designed for global runtime source failures. Source Evaluation quarantine is currently evaluation-specific.

### 4. Do Not Wrap Every Source Object With A Proxy Unless Proven Safe

A raw proxy around every `Source` can break casts such as `HttpSource`, `CatalogueSource`, `EnhancedHttpSource`, delegated sources, source-specific features, and extension APIs.

Preferred approach:

- Add a shared guard/executor.
- Replace direct source method calls at known execution boundaries.
- Leave source registration and source identity unchanged.

If Claude believes a wrapper/proxy is safer, it must first prove:

- `HttpSource` casts still work.
- `EnhancedHttpSource` / SY delegated source behavior still works.
- source preferences and source details still resolve.
- migration, feeds, Browse, reader, and downloads still operate.

## Required Call-Site Migration

Claude must search the repo for all source runtime calls before editing:

```text
source.getFilterList(
source.getSearchManga(
source.getPopularManga(
source.getLatestUpdates(
source.getMangaUpdate(
source.getPageList(
source.getImageUrl(
getRelatedMangaList(
getRelatedMangaListByExtension(
getRelatedMangaListBySearch(
```

Every app-owned call site must be classified as one of:

- migrated to `SourceRuntime`;
- already safely isolated by an equivalent mechanism;
- intentionally not migrated, with a written reason.

### A. Official Browse / Source Paging

Files:

- `data/src/main/java/tachiyomi/data/source/SourcePagingSource.kt`
- `data/src/main/java/tachiyomi/data/source/EHentaiPagingSource.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt`

Required changes:

- In paging sources, wrap `requestNextPage()` source operations via `SourceRuntime.run(...)`.
- Convert recoverable source runtime failures into `LoadResult.Error(failure.throwable)` or the existing screen error state, not process crashes.
- In global search, wrap `source.getFilterList()` and `source.getSearchManga(...)` together so a linkage failure becomes that source row's error.
- Do not let one source's `LinkageError` cancel sibling searches.
- Keep normal cancellation behavior unchanged.

### B. Official Feed / Saved Search

Files:

- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/feed/FeedScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/feed/SourceFeedScreenModel.kt`

Required changes:

- Guard `getLatestUpdates`, `getPopularManga`, saved-search `getSearchManga`, and saved-search `getFilterList`.
- A broken source should show an item/source error and not break the whole feed.

### C. Manga Detail / Related Manga

File:

- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt`

Required changes:

- Guard `state.source.getRelatedMangaList(...)`.
- If the source is incompatible, related manga should become an error/empty related section, not crash the screen.
- Preserve the existing `exceptionHandler` semantics for ordinary related-manga failures.

### D. Reader And Downloads

Files:

- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt`

Required changes:

- Guard `source.getPageList(...)`.
- Guard `source.getImageUrl(...)`.
- For reader: show existing page/chapter load error UI.
- For downloader: fail the affected download/chapter with a normal download error.
- Do not crash the whole app.

### E. Library Update / Manga Refresh / Bulk Favorite

Files:

- `app/src/main/java/eu/kanade/tachiyomi/data/library/UpdateMangaFromRemote.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/BulkFavoriteScreenModel.kt`

Required changes:

- Guard `getMangaUpdate`.
- A broken source should mark that manga/source update as failed and continue unrelated manga/source updates.
- Avoid `.getOrThrow()` patterns that turn a recoverable source failure back into job-level failure unless the surrounding code already catches and reports per-manga failure.

### F. KMK For You / Group Recommendations

Files:

- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/RecommendsScreenModel.kt`
- `app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt`
- `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`
- `app/src/main/java/exh/recs/RecommendationCandidateEnricher.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationSeedBuilder.kt`

Required changes:

- Replace local `catch (Exception)` / `catch (Error)` patches with `SourceRuntime`.
- Guard:
  - filter building,
  - first-page search,
  - additional-page search,
  - title fallback search,
  - detail enrichment,
  - delegate calls in `RecommendationPagingSource`.
- A broken extension should produce one source-row error or skip that source for the active request.
- It must not cancel all other sources or crash For You.

### G. KMK Cross-Extension Matching / Same Manga / Best Version

Files:

- `app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt`
- `app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt`
- `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt`

Required changes:

- Guard matching searches.
- Guard best-version `getMangaUpdate`, `getPageList`, and `getImageUrl`.
- Preserve existing per-candidate/per-source error states.
- A broken candidate/source should be unavailable, not fatal.

### H. KMK Source Evaluation / Recommendation Quality

Files:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCatalogueEnricher.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt`

Required changes:

- Guard Popular, Latest, Search, and detail enrichment calls.
- Runtime `LinkageError` should increment error counters and mark the source/extension as unavailable/incompatible for that evaluation.
- Do not classify a dependency crash as "weak taste fit"; it is a technical incompatibility.
- Continue the batch after a broken source.

### I. Extension Update / Browse Initialization

Files to inspect:

- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`

Required changes:

- Do not eagerly call source clients while checking extension updates.
- If any update/check/detail path touches installed sources, route it through `SourceRuntime`.
- Browse initialization should not execute every extension's runtime client merely to list sources.

## Failure UI / User Messaging

Use existing screen-specific error display wherever possible.

Recommended user-facing wording:

```text
Source unavailable
This source failed to load because the extension is missing a required dependency.
Update or reinstall the extension, or disable it temporarily.
```

Do not show raw class names like `okhttp3.zstd.Zstd` as primary UI text. Raw details may appear in diagnostics/copy logs.

The app must not use internal terms like "private build" or "public build" in app-visible strings.

## Tests Required

Add or update unit tests. Do not rely only on manual testing.

### Source Runtime Tests

New recommended file:

`app/src/test/java/eu/kanade/tachiyomi/source/SourceRuntimeTest.kt`

Test:

- `NoClassDefFoundError` is recoverable and classified as extension incompatible.
- `NoSuchMethodError`, `NoSuchFieldError`, `IncompatibleClassChangeError`, and `ExceptionInInitializerError` are recoverable.
- wrapped linkage failures unwrap correctly.
- `CancellationException` is rethrown.
- `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`, and `AssertionError` are rethrown/not recoverable.
- source identity is preserved in `SourceRuntimeFailure`.

### Global Search / Paging Tests

Add tests proving:

- one source throwing `NoClassDefFoundError` becomes one row/page error;
- sibling source result still succeeds;
- `awaitAll`/parallel search does not cancel siblings because one source is incompatible.

### For You / Group Recommendation Tests

Update existing tests:

- `RecommendationErrorClassifierExtensionLinkageTest`
- `RecommendationSourceFailureIsolationTest`

Add tests for:

- For You first-page source call;
- For You additional-page source call;
- group recommendation title fallback/detail enrichment;
- broken source skipped, healthy source still rendered.

### Source Evaluation Tests

Add tests for:

- Popular throws `NoClassDefFoundError`;
- Latest throws `NoClassDefFoundError`;
- detail enrichment throws `NoClassDefFoundError`;
- evaluation row records technical incompatibility, not weak taste fit;
- batch continues.

### Reader/Download Tests

If practical with existing test infrastructure:

- page list linkage failure produces page/chapter load error;
- downloader marks the chapter failed but process continues.

If not practical, document the reason and include real-device QA steps.

## Real-Device Verification Required

After building the APK, test with the currently broken AsuraScans extension still installed.

Required manual checks:

1. Open For You.
2. Open Browse.
3. Run global search.
4. Open source evaluation.
5. Run For You search compatibility/recommendation quality if available.
6. Update a different extension.
7. Open a healthy source.
8. Open a healthy manga chapter.
9. Confirm AsuraScans itself shows a contained source/extension failure instead of closing the app.
10. Copy diagnostics/crash logs and confirm no process-killing `NoClassDefFoundError`.

## Documentation Updates Required

Update:

- `docs/community/KMK_RECS_V0_8_10_FIX2_EXTENSION_ISOLATION_AUDIT.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

Create:

- `docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md`

The implementation report must list every source runtime call-site found, and whether it was migrated, already safe, or intentionally deferred.

## Verification Commands

Use the repo-local JDK 17 setup documented in `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`.

Run:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat assembleDebug
```

Build output must be copied to:

```text
C:\Users\USER\Downloads\Komikku\private\Komikku-v1.14.0-kmk.8.10-fix3-debug.apk
```

This "private" path is development/handoff terminology only. Do not add "private" or "public" to app-visible UI.

## Acceptance Criteria

The fix is complete only when:

- there is one shared source runtime failure policy;
- all source execution call sites have been inventoried;
- KMK bulk/global flows use the shared boundary;
- official Browse/global search/feed/paging calls use the shared boundary where they execute extension code;
- reader/download/library update paths no longer allow recoverable extension linkage failures to kill the process;
- fatal VM errors still propagate;
- cancellation still propagates;
- tests prove sibling isolation;
- real-device QA with the broken AsuraScans extension confirms For You and Browse no longer crash;
- docs accurately distinguish v0.8.10-fix2 as the partial/narrow patch and v0.8.10-fix3 as the structural repair.
