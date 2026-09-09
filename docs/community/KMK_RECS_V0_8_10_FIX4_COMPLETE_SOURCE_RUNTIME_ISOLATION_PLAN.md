# KMK-Recs v0.8.10-fix4 - Complete Source Runtime Isolation Plan

Date: 2026-07-18

Status: planned corrective follow-up. This supersedes the "fix3 complete" assumption only for the
real-device Asura/Zstd crash path. It does not undo fix3; it completes the source-call boundary that
fix3 introduced.

## Claude execution assignment

Recommended model: Sonnet
Recommended effort: low

Why this assignment fits this plan: Codex captured the live device failure and mapped the remaining
direct source-call families. The work should be mechanical and test-driven: replace direct extension
method execution with the existing shared `SourceRuntime` boundary, then verify.

Token/throughput tradeoff: use Sonnet low to preserve quota. Do not spend the session re-auditing the
whole repository unless a listed file contradicts this plan.

When to escalate: only if a listed call site cannot use `SourceRuntime` because of a real module
dependency boundary, or if a test exposes a different root cause.

When to reduce effort safely: not applicable; low is already the requested low-cost execution mode.

Required verification before continuing: focused runtime-isolation tests, `spotlessCheck`,
`:app:testDebugUnitTest`, `:app:assembleDebug`, and an explicit implementation report. If a connected
device is available, run the manual ADB verification steps in this plan before declaring the fix
validated on-device.

## User problem

After installing `Komikku-v1.14.0-kmk.8.10-fix3-debug.apk`, the app still crashes when:

- opening the For You page;
- opening manga recommendations;
- opening any installed extension;
- opening For You settings or source-related screens.

Manga reading itself can still work. This means the crash is tied to source/recommendation/browse
runtime initialization rather than the whole app process.

## Confirmed live-device evidence

Codex connected to the user's Samsung SM-X520 over wireless debugging and captured a fresh live log
after fix3 was installed.

Captured file:

```text
C:\Users\USER\Downloads\Komikku\kmk_fix3_live_crash_logcat.txt
```

Relevant fresh crash section:

```text
07-18 10:26:30.846 E GlobalExceptionHandler:
java.lang.NoClassDefFoundError: Failed resolution of: Lokhttp3/zstd/Zstd;
    at d1.c(r8-map-id-4452a019d06efc1ca122d348c245bef3ae3f69106319c7d23252d92b312a8ee6:315)
    at t0.invoke(...:3)
    at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:86)
    at d1.getClient(...:3)
    at e.invokeSuspend(...:33)
Suppressed:
java.lang.NoClassDefFoundError: Failed resolution of: Lokhttp3/zstd/Zstd;
    at d.invokeSuspend(...:30)
Caused by:
java.lang.ClassNotFoundException: Didn't find class "okhttp3.zstd.Zstd" on path:
/data/app/.../eu.kanade.tachiyomi.extension.en.asurascans.../base.apk
```

Interpretation:

- The broken extension is still AsuraScans.
- The missing dependency is still `okhttp3.zstd.Zstd`.
- The error is thrown from lazy `client` initialization after the extension has already loaded.
- It is logged by `GlobalExceptionHandler`, then the app enters `CrashActivity`, so the failure is not
  contained by the current runtime boundary.
- The suppressed second coroutine frame means at least two sibling source calls can fail together.

## Why fix3 was incomplete

Fix3 introduced:

- `app/src/main/java/eu/kanade/tachiyomi/source/SourceRuntime.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/SourceRuntimeFailureRegistry.kt`
- `core/common/src/main/kotlin/eu/kanade/tachiyomi/source/SourceRuntimeClassifier.kt`

But code inspection after the live crash shows many high-risk call sites still execute extension
methods directly. Some have local `catch (Error)` branches, some use `runCatching`, and some still
only catch `Exception`. That means the application still relies on scattered local containment instead
of enforcing one source runtime boundary.

The fix3 report also explicitly deferred this family:

```text
BrowseSourceScreenModel.kt remaining getFilterList() calls were intentionally deferred as lower-risk.
```

The user's live testing now invalidates that deferral: opening any extension still crashes, so Browse
source UI-state/filter initialization must be treated as confirmed affected.

## Target behavior

One broken installed source must not crash unrelated app areas.

If AsuraScans or any other extension throws a recoverable runtime failure such as
`NoClassDefFoundError`, the app must:

- record a source-scoped failure through `SourceRuntimeFailureRegistry`;
- classify it as `ExtensionIncompatible`;
- show the affected source as unavailable/error where relevant;
- skip it in For You, grouped recommendations, source evaluation, matching, feeds, and Browse;
- allow sibling sources and unrelated screens to continue;
- preserve `CancellationException`;
- preserve fatal VM errors such as `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`, and
  `AssertionError`.

The app must not:

- add `okhttp3.zstd.Zstd` to the main app as a workaround;
- auto-uninstall AsuraScans;
- catch every `Throwable` and swallow fatal errors;
- show raw missing-class paths to normal users;
- add app-visible wording such as "private", "public", "internal build", or "test build".

## Implementation rule

Prefer this pattern for every app-module call into an installed `Source`/`CatalogueSource` method:

```kotlin
val result = SourceRuntime.run(source, SourceRuntimeOperation.Search) {
    (this as CatalogueSource).getSearchManga(page, query, filters)
}
```

Use `getOrElse`, `fold`, or explicit `isFailure` handling to convert recoverable source-runtime
failures into the screen's existing error/empty/unavailable state.

Do not leave a direct source call plus a nearby local `catch (Error)` if the file can use
`SourceRuntime`. Local catch branches are allowed only in modules that cannot depend on `app`.

For synchronous non-suspend calls such as filter-list construction inside a Compose/screen-model state
path, use:

```kotlin
SourceRuntime.runBlockingSourceCall(source, SourceRuntimeOperation.FilterList) {
    (this as CatalogueSource).getFilterList()
}
```

Use `FilterList()` as a fallback only when the source-runtime failure is recoverable and the existing
screen can still render safely with empty filters.

## Exact files and call sites to fix

### 1. Browse source screen - confirmed missed crash path

File:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreenModel.kt
```

Current direct calls:

- line about 169: `Listing.Search(query, source.getFilterList())`
- line about 174: `filters = source.getFilterList()`
- line about 203: `getExhSavedSearch.subscribe(source.id, source::getFilterList)`
- line about 295: `setFilters(source.getFilterList())`
- line about 335: `Listing.Search(query = null, filters = source.getFilterList())`
- line about 358: `val defaultFilters = source.getFilterList()`
- line about 617: `getExhSavedSearch.awaitOne(loadedSearch.id, source::getFilterList)`
- line about 627: `search.filterList != null && search.filterList == source.getFilterList()`
- line about 632: `?: source.getFilterList()`
- line about 667: `state.value.filters.ifEmpty { source.getFilterList() }`

Required change:

- Add a private helper on the screen model:

```kotlin
private fun safeFilterList(): FilterList {
    return SourceRuntime.runBlockingSourceCall(source, SourceRuntimeOperation.FilterList) {
        (this as CatalogueSource).getFilterList()
    }.getOrElse { throwable ->
        logcat(LogPriority.WARN, throwable) {
            "BrowseSourceScreenModel[${source.name}]: getFilterList failed"
        }
        FilterList()
    }
}
```

- If the file's `source` property is already typed as `CatalogueSource`, avoid unnecessary cast.
- Replace every `source.getFilterList()` and every `source::getFilterList` callback in this file with
  `safeFilterList()` or a lambda `{ safeFilterList() }`.
- Do not change the paging behavior unless needed; `SourcePagingSource` should remain the paging-load
  boundary for search/popular/latest.
- Preserve existing saved-search behavior. The only change is that broken source filter loading
  returns empty filters and records a runtime failure instead of crashing.

Test:

- Add or update a unit test for `BrowseSourceScreenModel` or a small extracted helper if direct screen
  model testing is too heavy.
- The test must prove a `NoClassDefFoundError` from filter-list retrieval is recoverable and yields
  `FilterList()` without throwing.

### 2. Global search

File:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt
```

Current direct call:

```kotlin
source.getSearchManga(1, query.sanitize(), source.getFilterList())
```

Required change:

- Replace with a `SourceRuntime.run(source, SourceRuntimeOperation.Search)` block that performs both
  filter retrieval and search for that source.
- If the result is failure, keep the existing per-source empty/error result behavior and sibling
  searches continue.
- Remove redundant local source-runtime catch branches where the `SourceRuntime` result handles them.

### 3. Source feed screen and general feed

Files:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/feed/SourceFeedScreenModel.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/feed/FeedScreenModel.kt
```

Current direct calls:

- `setFilters(source.getFilterList())`
- `source.getPopularManga(1)`
- `source.getLatestUpdates(1)`
- `source.getSearchManga(...)`
- `source::getFilterList`
- saved-search helper `getFilterList(...)` with `runCatching { source.getFilterList() }`

Required change:

- Add file-local helpers for safe popular, latest, search, and filter-list operations using
  `SourceRuntime`.
- Keep existing user-visible feed behavior: failed source rows show empty/error state, not a process
  crash.
- Preserve cancellation by relying on `SourceRuntime`.
- Replace `source::getFilterList` with a safe lambda.

### 4. For You and group recommendation source execution

Files:

```text
app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt
app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt
app/src/main/java/exh/recs/RecommendsScreenModel.kt
app/src/main/java/exh/recs/RecommendationCandidateEnricher.kt
app/src/main/java/exh/recs/group/GroupRecommendationSeedBuilder.kt
```

Current direct calls:

- `BrowsePersonalRecommendationsScreenModel.kt`:
  - `source.getFilterList()`
  - `source.getSearchManga(...)`
  - `source.getSearchManga(nextPage, ...)`
- `CrossExtensionGenreSearchSource.kt`:
  - `catalogueSource.getFilterList()`
  - `catalogueSource.getSearchManga(...)`
  - `catalogueSource.getMangaUpdate(...)` inside `runCatching`
- `RecommendationCandidateEnricher.kt`:
  - `source.getMangaUpdate(...)` inside `runCatching`
- `GroupRecommendationSeedBuilder.kt`:
  - `source.getMangaUpdate(...)` inside nested `runCatching`

Required change:

- Convert every listed extension call to `SourceRuntime.run`.
- Do not rely on `runCatching` as the source boundary because it does not record the failure registry.
- Keep existing behavior:
  - failed filter list falls back to `FilterList()`;
  - failed tag attempt moves to the next more-lenient attempt;
  - failed title fallback moves to the next title;
  - failed enrichment logs and leaves item unenriched;
  - no one failed extension cancels the whole group recommendation.

Tests:

- Extend `RecommendationSourceFailureIsolationTest` so a fake recommendation source whose
  `getFilterList`, `getSearchManga`, or `getMangaUpdate` throws `NoClassDefFoundError` results in a
  per-source error/no-result row and does not cancel sibling source results.

### 5. Source evaluation and recommendation-quality probes

Files:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationCatalogueEnricher.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt
app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityRunner.kt
```

Current direct calls:

- `SourceEvaluationRunner.kt`:
  - `source.getPopularManga(1)` only catches `Exception`
  - `source.getLatestUpdates(1)` only catches `Exception`
- `SourceEvaluationCatalogueEnricher.kt`:
  - `source.getMangaUpdate(...)`
- `SourceRecommendationFitProbe.kt`:
  - `source.getFilterList()`
  - `source.getSearchManga(...)`
  - `source.getMangaUpdate(...)`

Required change:

- Use `SourceRuntime.run` for all these calls.
- If a recoverable source-runtime failure occurs:
  - increment the per-source error count;
  - classify as `SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE`;
  - persist a technical error, not a weak taste-fit verdict;
  - continue evaluating other sources/extensions.
- Keep existing timeouts. Wrap the `SourceRuntime.run` call inside `withTimeoutOrNull` or vice versa
  carefully so cancellation/timeout still behaves as before.

Tests:

- Extend `SourceEvaluationLinkageIsolationTest` to cover popular probe, latest probe, enrichment,
  sibling continuation, and fatal `OutOfMemoryError` propagation.

### 6. Matching, Best Version, and batch helpers

Files:

```text
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt
app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt
app/src/main/java/exh/recs/batch/RecommendationSearchHelper.kt
```

Fix3 added local `catch(Error)` protection here, but the durable standard is `SourceRuntime`.

Required change:

- Convert source calls to `SourceRuntime.run`.
- Preserve existing behavior:
  - matching skips broken source rows;
  - Best Version skips broken preview/detail sources and keeps healthy sources;
  - batch search records per-source failure and continues;
  - fatal VM errors still propagate.

### 7. Reader and downloader check

Files:

```text
app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt
app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt
```

Fix3 reported these as already safely isolated by `catch(Throwable)`.

Required action:

- Re-check them, but do not rewrite unless a source call still lacks cancellation preservation or
  source-scoped error conversion.
- If they remain safe, document that in the fix4 implementation report and do not churn these files.

### 8. Extension update/check path

Files to inspect, not necessarily edit:

```text
app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/ExtensionsScreenModel.kt
app/src/main/java/eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreenModel.kt
```

User observed crashes while updating unrelated extensions. Fix3 claimed update/check only touches
metadata and should not execute source methods.

Required action:

- Re-check this claim against code.
- If extension update/check indirectly refreshes source registrations or reads installed source
  runtime data, route that call through source isolation.
- If it truly does not execute source methods, document that the observed crash was likely caused by a
  concurrent background source/recommendation load rather than the update action itself.

## Tests to add or update

Minimum required test coverage:

- `SourceRuntimeTest`: add one test proving `NoClassDefFoundError` from a lazy `client`-like property
  is classified and recorded.
- `RecommendationSourceFailureIsolationTest`: cover failure from filter list, search, and enrichment.
- `SourceEvaluationLinkageIsolationTest`: cover popular/latest/enrichment failures and sibling
  continuation.
- Add a Browse-source filter-list regression test, either direct screen-model test or extracted helper
  test.
- Add a global-search/feed source-runtime isolation test if existing test infrastructure allows it.

Every test must prove sibling isolation where a sibling exists. A classifier-only test is not enough.

## Documentation updates

Update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `RECOMMENDATION_VERSIONING.md`
- create `docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_IMPLEMENTATION.md`

Required documentation wording:

- fix3 introduced the boundary but real-device verification showed incomplete coverage;
- fix4 completed the remaining source execution paths;
- state exactly which call sites were changed, which were inspected and left unchanged, and why;
- include requested/actual Claude model and effort;
- include the APK path and hash.

Do not add a new app-visible What's New entry unless the existing v0.8.10 fix precedent requires it.
If release notes are not bumped, document why.

## Versioning and handoff

Feature line:

```text
KMK-Recs v0.8.10-fix4
```

App-visible `KmkRecsReleaseNotes.VERSION_CODE` and `VERSION_NAME` should remain unchanged unless the
existing v0.8.10 fix convention says otherwise.

Handoff APK must be copied to:

```text
C:\Users\USER\Downloads\Komikku\private\Komikku-v1.14.0-kmk.8.10-fix4-debug.apk
```

Do not use "private", "public", "internal", or "test build" in app-visible UI strings.

## Verification commands

Run after implementation:

```text
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If the connected tablet is available, also run/confirm:

```text
adb devices
adb logcat -c
```

Then install the fix4 APK with AsuraScans still installed and verify:

1. Open For You page.
2. Open manga page -> recommendations.
3. Open Browse.
4. Open several installed extensions, including any healthy extension and AsuraScans if still present.
5. Open For You settings.
6. Run Source Evaluation or For You compatibility check against at least one source batch.
7. Update an unrelated extension.
8. Confirm no `NoClassDefFoundError: okhttp3.zstd.Zstd` reaches `GlobalExceptionHandler` or
   `CrashActivity`.

If device verification cannot be performed by Claude, the implementation report must say so clearly
and provide exact user verification steps.

## Completion gate

Claude may report this fix complete only when:

- all direct app-module source-method calls in the files listed above are either migrated to
  `SourceRuntime` or explicitly documented as safe with code evidence;
- the previously deferred `BrowseSourceScreenModel.getFilterList()` family is fixed;
- source evaluation no longer records broken extensions as weak taste fits;
- tests prove sibling isolation and fatal-error preservation;
- docs are reconciled so no file still implies fix3 is fully device-proven;
- the APK is built and copied to the exact handoff path.
