# KMK-Recs v0.8.10-fix4: Complete Source-Runtime Isolation — Implementation Report

## Why this fix exists

`v0.8.10-fix3` created the shared `eu.kanade.tachiyomi.source.SourceRuntime` execution boundary and
migrated many direct source-method call sites to it, but live-device testing (Codex, wireless
debugging on a Samsung SM-X520) proved coverage was incomplete: after installing
`Komikku-v1.14.0-kmk.8.10-fix3-debug.apk` with the AsuraScans extension installed, the app still
crashed with `NoClassDefFoundError: Failed resolution of: Lokhttp3/zstd/Zstd;` when opening For You,
Browse/source screens, manga recommendations, or For You settings. The captured logcat evidence is
`kmk_fix3_live_crash_logcat.txt` (referenced by the coordinator; not reproduced here).

Direct re-inspection of the actual code (not the fix3 report's characterization of it) found the
real, confirmed root cause: `BrowseSourceScreenModel.kt`'s `init` block called
`source.getFilterList()` with **zero** try/catch — not the "lower risk, not a confirmed crash site"
fix3's implementation report characterized it as, but a fully unguarded call that crashes on the
very first frame any Browse/source screen renders. This invalidated fix3's explicit deferral
decision for that file.

Fix4's plan (`docs/community/KMK_RECS_V0_8_10_FIX4_COMPLETE_SOURCE_RUNTIME_ISOLATION_PLAN.md`)
listed the remaining call sites to migrate. Per the coordinator's explicit instruction, this
implementation worked from that list directly rather than re-auditing the whole repository; where a
listed file's real code differed from what the plan assumed, that discrepancy is documented in the
relevant section below.

## What changed, file by file

### Plan section: Browse/global-search/feed screens

- **`app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreenModel.kt`** —
  the confirmed root cause. Added a `safeFilterList()` helper that wraps
  `SourceRuntime.runBlockingSourceCall(source, SourceRuntimeOperation.FilterList) { getFilterList() }`
  and falls back to an empty `FilterList()` on failure. Replaced all 11 unguarded/raw call sites
  (`init` block ×2, `resetFilters()`, `search()`, `searchGenre()`, `reloadSavedSearches()`,
  `onSavedSearch()` ×3, `saveSearch()`).
- **`app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt`** —
  replaced the fix3 `catch(Exception)`/`catch(Error)` pair around `getSearchManga(...)` with
  `SourceRuntime.run(source, SourceRuntimeOperation.Search, coroutineDispatcher) { ... }.fold(...)`.
- **`app/src/main/java/eu/kanade/tachiyomi/ui/browse/feed/FeedScreenModel.kt`** and
  **`app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/feed/SourceFeedScreenModel.kt`** —
  main `getPopularManga`/`getLatestUpdates`/`getSearchManga` dispatch converted to
  `SourceRuntime.run(...)`. Each file's local `getFilterList(savedSearch, source)` helper already
  used `runCatching {}` and was confirmed safe as-is (left unchanged; `runCatching` there does not
  need registry recording since it is a pure-fallback helper called from inside the now-migrated
  main call, which itself records the outer failure).

### Plan section 4: recommendation call sites

- **`app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`** — inner
  `getFilterList()`, main `getSearchManga`, and `discoverAdditionalPage()`'s `getSearchManga` all
  converted to `SourceRuntime.run(...)`. The outer `catch(Error)` is kept as a documented defensive
  backstop, not the structural boundary (it can no longer be reached by a source-call `Error` since
  `SourceRuntime.run` never rethrows a recoverable one).
- **`app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt`** — all 4 call sites
  (`getFilterList()` in `tryAttempt()`, `getSearchManga()` in `tryAttempt()`, `getSearchManga()` in
  `runTitleFallback()`, `getMangaUpdate()` in `enrichTopResults()`) converted to `SourceRuntime.run`.
  The `NoResultsException` special-case (must not flip `exceptionOccurred`) and the `runTitleFallback`
  loop's non-local `continue` inside the inline `getOrElse` lambda were preserved exactly.
- **`app/src/main/java/exh/recs/RecommendationCandidateEnricher.kt`** — `getMangaUpdate()`
  enrichment converted from `runCatching` to `SourceRuntime.run` (the plan explicitly states
  `runCatching` alone does not record the failure registry, even though it is `Throwable`-safe).
- **`app/src/main/java/exh/recs/group/GroupRecommendationSeedBuilder.kt`** — per-member
  `getMangaUpdate()` enrichment converted the same way, replacing a hand-rolled
  `if (e is CancellationException) throw e` with the guarantee `SourceRuntime.run` already provides.
- **`app/src/main/java/exh/recs/RecommendsScreenModel.kt`** — **discrepancy from the plan's implied
  shape**: the GROUP_PREVIEW row wraps `recSource.requestNextPage(1)`, a polymorphic call across
  several `PagingSource` implementations (`CrossExtensionGenreSearchSource`, `RecommendationPagingSource`
  → data-module `SourcePagingSource`, `StaticResultPagingSource`, `TrackerRecommendationPagingSource`),
  not a single raw `Source` method — so it cannot itself be wrapped in `SourceRuntime.run()`. Each of
  those implementations already routes its own direct `Source` calls through `SourceRuntime.run()`
  (app-module) or the shared `core:common` classifier (`SourcePagingSource`, which is in the `data`
  module and cannot depend on `app`'s `SourceRuntime` object — the same module-direction blocker fix3
  resolved for that exact file). The outer catch here was changed to call the shared `core:common`
  classifier functions (`unwrapSourceRuntimeCause().isRecoverableSourceRuntimeFailure()`) directly
  instead of through the `RecommendationErrorClassifier` wrapper, matching the plan's preference for
  direct classifier usage over a delegation indirection, without pretending this site is a raw
  `Source` call site it is not.

### Plan section 5: Source Evaluation

- **`app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`** — `getPopularManga(1)` and
  `getLatestUpdates(1)` probe calls converted from `withTimeoutOrNull` + `catch(Exception)` to
  `withTimeoutOrNull` wrapping `SourceRuntime.run(..., Popular/Latest)`. The existing per-source
  `catch(Error)` (added in fix3) and the extension-level `catch(Error)` are both left in place as
  defensive backstops for anything the two now-migrated calls don't cover (e.g. an `Error` from
  `installedExt.sources.filterIsInstance<CatalogueSource>()` or the install-timeout branch).
  **Verified `SourceEvaluationProbeErrorClassifier.classify()`** already maps an unwrapped
  `LinkageError` to `SourceEvaluationProbeErrorKind.EXTENSION_INCOMPATIBLE`, confirmed correct and
  unchanged — a broken extension is recorded as a technical incompatibility, never a weak taste-fit
  score.
- **`app/src/main/java/exh/recs/evaluation/SourceEvaluationCatalogueEnricher.kt`** — the
  `getMangaUpdate()` detail-enrichment call converted from `catch(CancellationException)/
  catch(Exception)/catch(Error)` to `SourceRuntime.run`, same fallback (keep the raw list-entry
  candidate, still count as an attempt not a success).
- **`app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt`** —
  `getFilterList()`, `getSearchManga()`, and the per-candidate `getMangaUpdate()` enrichment call all
  converted to `SourceRuntime.run`, preserving each site's exact fallback (empty `FilterList`,
  per-plan "timed out"/"error — <label>" reason + `errorCount++` via a non-local `continue` inside an
  inline `getOrElse` lambda, skip enrichment on failure). The fix3 `catch(Error)` at the bottom of the
  per-plan loop is now dead code (every source call in the loop resolves its own `LinkageError`
  locally) and was removed with an inline comment explaining why.
- **`app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityRunner.kt`** — inspected, no
  changes. It has no direct `Source` calls of its own; it delegates entirely to
  `SourceRecommendationFitProbe.probe()` (migrated above), whose own doc/contract states it never
  throws a recoverable failure past its `Result` boundary.

### Plan section 6: cross-extension matching / Best Version / batch search

- **`app/src/main/java/exh/recs/matching/SameMangaCandidateSearcher.kt`** and
  **`app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt`** — the per-query
  `getSearchManga(1, query, getFilterList())` call converted to `SourceRuntime.run(..., Search)`,
  preserving the exact per-query-not-per-source failure bookkeeping (`lastError`, sibling queries and
  sibling sources keep running). The outer `catch(Error)` in both files is left in place as a
  defensive backstop for non-source-call `Error`s in the same `try` block.
- **`app/src/main/java/exh/recs/bestversion/BestVersionCompareScreenModel.kt`** — two sites:
  (1) `getMangaUpdate(...).chapters` in `loadChapters()`, now
  `SourceRuntime.run(..., MangaUpdate).fold(...)`; (2) `getPageList(candidateChapter)` and the nested
  `getImageUrl(page)` fallback in `startPreview()`, now two `SourceRuntime.run` calls. **Note**:
  `getImageUrl` is declared on `HttpSource`, not `Source`; since `SourceRuntime.run`'s lambda receiver
  type is fixed to `Source` by its own signature, the block casts explicitly:
  `(this as HttpSource).getImageUrl(page)` (the outer `source` variable is already resolved as
  `HttpSource` at the call site via `as? HttpSource`, so the cast is safe). Both sites keep the
  existing `RecommendationErrorClassifier.classifyToStorageKey(...)` storage-key mapping on failure.
- **`app/src/main/java/exh/recs/batch/RecommendationSearchHelper.kt`** — inspected, no changes. Its
  `catch(Error)` wraps `source.requestNextPage(1)`, the same polymorphic-PagingSource situation as
  `RecommendsScreenModel.kt` above, and it already calls the shared `core:common` classifier functions
  directly rather than through a wrapper — already matches the plan's preferred pattern.

### Sections 7–8: re-checked, no changes (document-only per the plan)

- **`HttpPageLoader.kt` / `ChapterLoader.kt` / `Downloader.kt`** — every `getPageList()`/
  `getImageUrl()` call site is already guarded by a broad `catch (e: Throwable)` that correctly
  excludes/rethrows `CancellationException` and produces the right isolation granularity for its
  layer: per-page in `HttpPageLoader.internalLoadPage()`, per-chapter in `ChapterLoader.loadChapter()`
  and `Downloader.downloadChapter()`, per-download-job in `Downloader.launchDownloadJob()`. A raw
  `LinkageError` from `source.getPageList()`/`getImageUrl()` is therefore already converted into a
  chapter/page/download-scoped error state rather than crashing the reader or the whole download
  queue — functionally equivalent to what `SourceRuntime.run()` provides. Per the plan's explicit
  instruction not to force a rewrite when an equivalent mechanism already exists, these files are
  unchanged.
- **`ExtensionManager.kt` / `ExtensionsScreenModel.kt` / `ExtensionDetailsScreenModel.kt`** — grepped
  for any direct `Source` method call; none found. These files operate at the extension-package level
  (install/uninstall/trust/enable) and never invoke the `Source` interface directly, so there is no
  source-runtime boundary for them to be missing.

## Tests added

- `app/src/test/java/eu/kanade/tachiyomi/source/SourceRuntimeTest.kt` — new test using a
  `FakeSource` whose `client` property is `by lazy { throw NoClassDefFoundError(...) }`, read from
  inside the overridden `getPopularManga()` body. This mirrors AsuraScans' actual crash shape (a
  lazy-init failure triggered from inside a method body, not a top-level `throw` as the first
  statement) and confirms `SourceRuntime.run()` still classifies/records it correctly, and that a
  second sibling call sees the same classification (not a one-shot lazy-property quirk).
- `app/src/test/java/exh/recs/RecommendationSourceFailureIsolationTest.kt` — new test driving the
  real `SourceRuntime.run()` boundary through a sequential per-candidate enrichment loop shaped like
  `RecommendationCandidateEnricher.enrich()` — proves one candidate's `LinkageError` does not stop
  enrichment of the next, and that `SourceRuntimeFailureRegistry` records the failure.
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationLinkageIsolationTest.kt` — new test driving
  `SourceRuntime.run()` across multiple sources' Popular probes (the actual
  `SourceEvaluationRunner` per-source loop shape) — proves one source's linkage failure does not
  block sibling sources in the same batch, and that only the failing source is recorded in
  `SourceRuntimeFailureRegistry`.

Every new test proves sibling isolation using the real `SourceRuntime.run()` call, not only a
classifier-level assertion, per the plan's explicit requirement.

## Verification

- `./gradlew :app:compileDebugKotlin` — run after each section's edits (sections 4, 5, 6), all
  `BUILD SUCCESSFUL`.
- `./gradlew :app:compileDebugUnitTestKotlin` — `BUILD SUCCESSFUL` after adding the three test files.
- `./gradlew :app:testDebugUnitTest --tests "eu.kanade.tachiyomi.source.SourceRuntimeTest" --tests
  "exh.recs.RecommendationSourceFailureIsolationTest" --tests
  "exh.recs.evaluation.SourceEvaluationLinkageIsolationTest"` — 24/24 tests passed.
- Final full verification (`spotlessCheck`, full `:app:testDebugUnitTest`, `:app:assembleDebug`) is
  recorded in the "Final verification" section below.

## No device access

As in every prior fix this session, this environment has no `adb`/device access — confirmed empty
via `adb devices`. All verification here is static (compile + JVM unit tests). The coordinator's
live-device evidence from fix3 is the only real-device signal available; this fix cannot itself
re-verify against a physical device.

## Requested vs. actual model/effort

Requested: continue the fix4 implementation "same discipline as this turn" (real compile checks,
incremental commits). Actual: Claude Sonnet 5, standard effort, executed synchronously across
sections 4–8, tests, and documentation in one continuous session, with a real `:app:compileDebugKotlin`
run after each section and 5 incremental commits (`f990d9a15`, `a5143ab38`, `9eb5a42c2`,
`826d82ac3`, plus this documentation commit) rather than one large batched commit at the end.

## Versioning decision

Per the pre-supplied `RECOMMENDATION_VERSIONING.md` fix4 entry (reviewed before writing this report)
and the fix1/fix2/fix3 precedent: this is a crash-isolation/stability pass with no user-visible
feature change, so `KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` remain unchanged at `760`/
`"KMK-Recs v0.8.10"` (confirmed by reading `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
directly — no discrepancy between the pre-supplied doc entry and the actual code). No new
What's New entry was added, consistent with the plan's instruction not to bump release notes unless
the existing precedent requires it. The APK filename alone carries the `fix4` suffix.

## APK handoff

APK path, SHA-256 hash, and `aapt dump badging` metadata are recorded in the final verification
section below, added once `spotlessCheck` / full `:app:testDebugUnitTest` / `:app:assembleDebug` all
pass.
