# KMK-Recs v0.8.10 Fix 2: Extension Isolation Audit

**Status:** Audit complete; implementation still incomplete
**Audit input:** `C:\Users\USER\Downloads\komikku_crash_logs_4.txt`
**Build observed:** Komikku `1.14.0-33`, commit `6b92c90ec`
**Follow-up plan:** `docs/community/KMK_RECS_V0_8_10_FIX3_STRUCTURAL_SOURCE_RUNTIME_ISOLATION_PLAN.md`

## Executive finding

The Asura Scans failure is not limited to the visible Asura screen. The installed extension references `okhttp3.zstd.Zstd`, but the class is absent from the extension APK. The failure occurs through `ChildFirstPathClassLoader` while the extension lazily creates its HTTP client.

The current source contains a shared `RecommendationErrorClassifier` and applies it to two recommendation paths:

- `RecommendsScreenModel` group-preview rows
- `BrowsePersonalRecommendationsScreenModel` For You per-source searches

That does not cover all extension execution paths. Several other flows still catch only `Exception`, and some source setup calls are outside a catch entirely. `NoClassDefFoundError` extends `LinkageError`, which extends `Error`, so those paths still allow the failure to escape and crash a worker or screen.

## What the new log proves

The same linkage failure repeats at approximately 06:31, 06:34, 06:35, and 07:30 on multiple `DefaultDispatcher` workers. This proves repeated independent source invocations, but the obfuscated extension stack does not identify the initiating UI screen for each occurrence. It supports a global isolation defect, but it does not by itself prove that every crash began in Browse, extension update checking, or For You.

The historical `GetCrossSourceGroupPrimary` and `UpdateMangaFromRemote` crashes in the same file are older entries and are not the current Zstd root cause.

## Lifecycle findings

### Extension loading is not the whole failure

`ExtensionLoader` catches `Throwable` while constructing extension source classes (`ExtensionLoader.kt` around lines 347-373). `ExtensionManager.initExtensions()` stores only successful loaded extensions. This protects constructor-time class failures.

`AndroidSourceManager` then eagerly converts registered extension sources into internal sources (`AndroidSourceManager.kt` around lines 79-128), but this registration does not provide a runtime guard around later source method calls. `SourceManager.get()` simply returns the source object (`AndroidSourceManager.kt` around lines 201-239).

The crash therefore occurs after successful registration, when a source method or lazy extension client is invoked.

### Confirmed protected paths

1. `RecommendsScreenModel.kt:436-450` now classifies recoverable linkage failures and renders a source error row.
2. `BrowsePersonalRecommendationsScreenModel.kt:736-748` now does the same for the literal For You loop.

These changes are correct but insufficient to protect the rest of the app.

### Confirmed uncovered or incompletely protected paths

1. `CrossExtensionMatchScreenModel.kt:159-185` calls `source.getSearchManga()` but catches only `Exception`.
2. `SameMangaCandidateSearcher.kt:88-110` calls `source.getSearchManga()` but catches only `Exception`.
3. `CrossExtensionGenreSearchSource.kt:113-145` and `179-189` call source filters/search methods but catch only `Exception`.
4. `RecommendationSearchHelper.kt:104-145` runs recommendation searches and catches only `Exception` inside sibling jobs.
5. `SourceRecommendationFitProbe.kt:107-236` calls `getFilterList`, `getSearchManga`, and metadata enrichment while catching only `Exception`.
6. `SourceEvaluationRunner.kt:288-318` probes catalogue sources while catching only `Exception`; its outer batch catches only `Exception` as well.
7. `SearchScreenModel.kt:192-210` catches only `Exception` around global-search source calls.
8. `SourceFeedScreenModel.kt:242-253` catches only `Exception` around Popular, Latest, and saved-search calls.
9. `FeedScreenModel.kt:282-301` catches only `Exception` around feed source calls.
10. `BrowseSourceScreenModel.kt:161-175`, `295`, `335`, `358`, and `627-668` calls source filter APIs outside a uniform linkage-failure boundary. Its add-to-library update path around `427-438` catches only `Exception`.
11. `UpdateMangaFromRemote.kt:90-138` invokes `source.getMangaUpdate()` and catches only `Exception`. Any library update, manga refresh, bulk favorite, or related caller can therefore propagate `NoClassDefFoundError`.
12. `BulkFavoriteScreenModel.kt:249-269` and its later update path call `UpdateMangaFromRemote`; its catches are `Exception`-only.
13. `LibraryUpdateJob.kt:533-542` calls `UpdateMangaFromRemote(...).getOrThrow()`. A failed source can abort the update job unless the interactor or job boundary classifies linkage failures.
14. Reader, download, manga-detail, migration, and page-loading paths invoke source operations through `SourceManager` or `UpdateMangaFromRemote`. They require a deliberate policy decision and targeted tests; the current log does not prove each one is the immediate caller.

## Why Fix 2 still crashes

The classifier itself is reasonable:

```kotlin
e !is Error || e is LinkageError
```

But it is applied only where callers explicitly invoke it. There is no single runtime source-execution boundary shared by every source call. Consequently, any operation that reaches Asura through an uncovered path still receives the `NoClassDefFoundError`, and the coroutine worker terminates.

The existing unit tests prove the classifier's decision and simulate recommendation sibling isolation. They do not prove isolation through global search, source feeds, source evaluation, library updates, bulk favorite, migration, or reader paths. They also do not reproduce a real extension classloader failure through the installed APK.

## Recommended resolution direction

1. Define one shared source-execution failure policy for extension-originated calls.
2. Preserve cancellation propagation and continue to rethrow genuinely fatal VM errors.
3. Convert `LinkageError` into a source-scoped unavailable/incompatible result at every network/source execution boundary.
4. Ensure sibling coroutines use supervisor-style isolation where appropriate; `awaitAll()` must not cancel all siblings because one source failed.
5. Record the failed extension package/source identity in the existing quarantine or unavailable-source mechanism, with bounded repeat logging.
6. Keep source registration lazy/safe and avoid adding a global eager `getClient()` call merely to detect the problem.
7. Make update jobs, Browse, global search, feeds, source evaluation, For You, group recommendations, matching, migration, and bulk favorite explicitly covered by tests or document why a path cannot invoke extension code.
8. Add a real-device verification procedure using the installed broken Asura package, followed by verification that a healthy source still loads and that unrelated extension updates complete.

## Audit conclusion

This is a genuine application-wide extension-isolation gap. It is not evidence that the Zstd dependency should be bundled into the main app, and it is not fixed by protecting only For You and group recommendations. The next implementation must be a shared runtime isolation repair with an explicit call-site inventory and real-device verification.

No source code was changed during this audit.
