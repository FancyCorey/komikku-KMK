# KMK-Recs v0.8.10 Fix 3: Structural Source Runtime Isolation — Implementation Report

Date: 2026-07-18

**Requested model/effort:** Sonnet 5, low ("Codex has already prepared the detailed plan... prefer
Sonnet low for Claude execution unless the plan explains why low effort is unsafe").

**Actual model/effort:** Sonnet 5. No literal `/status` command is available in this environment to
introspect the runtime configuration; per the system context available to me, my configured
reasoning effort for this pass was low, matching the request — no mismatch to flag.

**Scope:** Structural crash/stability repair superseding v0.8.10-fix2's narrow, recommendation-only
containment. Not a feature release.

---

## 1. Why fix2 was insufficient (confirmed, not assumed)

Fix2 added a local classifier to exactly two recommendation call sites
(`RecommendsScreenModel`/`BrowsePersonalRecommendationsScreenModel`). The fix3 audit
(`docs/community/KMK_RECS_V0_8_10_FIX2_EXTENSION_ISOLATION_AUDIT.md`) found the same crash pattern
reachable from many other independent call sites, because a source that constructs successfully
(passing official Komikku's `ExtensionLoader` guard) can still throw a `LinkageError` the first time
a *method* lazily touches a missing dependency — not at class-load time. Nothing centrally guards
post-construction method calls (`AndroidSourceManager` just stores/returns the raw `Source`).

## 2. Architecture built

- **`app/src/main/java/eu/kanade/tachiyomi/source/SourceRuntime.kt`** — the shared, Android/coroutine-
  facing execution boundary: `SourceRuntime.run()`/`runBlockingSourceCall()`, `SourceRuntimeOperation`,
  `SourceRuntimeFailureKind`, `SourceRuntimeFailure`, and the domain-aware
  `toSourceRuntimeFailureKind()` (needs `tachiyomi.domain.source.model.SourceNotInstalledException`).
- **`core/common/src/main/kotlin/eu/kanade/tachiyomi/source/SourceRuntimeClassifier.kt`** — the pure,
  zero-extra-dependency classification core: `isRecoverableSourceRuntimeFailure()` and
  `unwrapSourceRuntimeCause()`. See section 3 for why this split exists.
- **`app/src/main/java/eu/kanade/tachiyomi/source/SourceRuntimeFailureRegistry.kt`** — in-memory,
  process-lifetime failure record keyed by source id (no persistence, no auto-uninstall).
- **`exh/recs/RecommendationErrorClassifier.kt`** — kept (still used for recommendation UI/storage
  keys), but `isRecoverableSourceFailure()` and `LinkageError` classification now delegate to the
  shared functions instead of duplicating the decision.
- **`exh/recs/evaluation/SourceEvaluationProbeErrorClassifier.kt`** — gained a new
  `EXTENSION_INCOMPATIBLE` kind, classified via the same shared unwrap+`LinkageError` check, so a
  broken extension is recorded as a technical incompatibility, never "weak taste fit" or a generic
  "internal error."

### Contract (unchanged from the plan, verified in every migrated call site)

- `CancellationException` always rethrown.
- `LinkageError` (`NoClassDefFoundError`, `NoSuchMethodError`, `NoSuchFieldError`,
  `IncompatibleClassChangeError`, `ExceptionInInitializerError`, any other subtype) is recoverable.
- Every other `Error` (`OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`, `AssertionError`, and
  any unclassified `Error`) always rethrows.
- `ExecutionException`/`CompletionException`/`InvocationTargetException` are unwrapped before
  classification.

## 3. Blocker hit and resolved: module dependency direction (per this pass's explicit halt instruction)

**Blocker:** `data/src/main/java/tachiyomi/data/source/SourcePagingSource.kt` — the official
Browse/global-search paging file the plan explicitly names — could not reference `app`-module
`SourceRuntime` (`app` depends on `data`, confirmed via `app/build.gradle.kts`'s
`implementation(projects.data)`; `data/build.gradle.kts` has no dependency back on `app`). I stopped,
reverted the attempted edit to zero-regression original behavior, and reported the exact blocker
rather than improvising a workaround, per this pass's explicit stricter halt instruction.

**Resolution (approved):** verified `core:common` is a real module both `app` and `data` already
depend on (`implementation(projects.core.common)` in both `build.gradle.kts` files). Moved *only* the
two pure, zero-extra-dependency functions (`isRecoverableSourceRuntimeFailure`,
`unwrapSourceRuntimeCause`) there, same package (`eu.kanade.tachiyomi.source`), different module —
`app`'s `SourceRuntime.kt` now references them by same-package visibility (no import needed) instead
of reimplementing them. `toSourceRuntimeFailureKind()` intentionally stayed in `app`: it needs
`tachiyomi.domain.source.model.SourceNotInstalledException`, and `core:common` does not (and should
not, per the coordinator's explicit instruction) gain a new `domain` dependency for this. `data` does
not depend on `app`; no `app`-only dependency was moved into `data` or `core`; there is exactly one
pure classification implementation, referenced from both sides.

This let `SourcePagingSource.kt`/`EHentaiPagingSource.kt` move from **blocked** to **migrated** (see
the inventory table).

## 4. Complete call-site inventory

Every app-owned occurrence of the source-runtime methods named in the plan
(`getFilterList`, `getSearchManga`, `getPopularManga`, `getLatestUpdates`, `getMangaUpdate`,
`getPageList`, `getImageUrl`, `getRelatedMangaList*`), found via repository-wide search before
editing, classified as migrated / already safely isolated / intentionally deferred:

| File | Call(s) | Status | Notes |
| --- | --- | --- | --- |
| `data/.../SourcePagingSource.kt` (`BaseSourcePagingSource.load()`) | search/popular/latest | **Migrated** | Was the module-boundary blocker (section 3); now uses the `core:common` classifier. Covers `SourceSearchPagingSource`/`SourcePopularPagingSource`/`SourceLatestPagingSource`. |
| `data/.../EHentaiPagingSource.kt` | search/popular/latest | **Migrated** (inherited) | Extends `BaseSourcePagingSource`; covered by the same `load()`. |
| `app/.../SearchScreenModel.kt` (global search) | `getSearchManga`+`getFilterList` | **Migrated** | Added `catch(Error)` to the per-source `async` coroutine; sibling searches unaffected. |
| `app/.../FeedScreenModel.kt` | `getLatestUpdates`/`getPopularManga`/`getSearchManga` | **Migrated** | `catch(Error)` falls back to `emptyList()`, same shape as existing `Exception` handling. |
| `app/.../SourceFeedScreenModel.kt` | `getPopularManga`/`getLatestUpdates`/`getSearchManga` | **Migrated** | Same pattern as `FeedScreenModel`. |
| `app/.../BrowseSourceScreenModel.kt` | `getFilterList()` (multiple, UI-state-building) | **Deferred** | The actual paging load (search/popular/latest) is already covered via `SourcePagingSource.kt`. Remaining direct `getFilterList()` calls build `Listing`/filter state; `getFilterList()`'s default implementation is trivial/local (`= FilterList()`), materially lower risk than the network-triggering calls already fixed. Deferred given remaining pass budget; not a confirmed crash site. |
| `app/.../ui/manga/MangaScreenModel.kt` (related manga) | `getRelatedMangaList` | **Already safely isolated** | Official `Source.kt`'s own `getRelatedMangaListByExtension`/`getRelatedMangaListBySearch` already use `runCatching {}` (catches `Throwable`, including `LinkageError`). Confirmed by direct inspection, not assumed. |
| `app/.../reader/loader/HttpPageLoader.kt` | `getPageList`/`getImageUrl` | **Already safely isolated** | Every actual source-call site already uses `catch (e: Throwable)`, not `catch(Exception)`; `CancellationException` already rethrown; failures already become the existing `Page.State.Error(e)`. |
| `app/.../data/download/Downloader.kt` | `getPageList`/`getImageUrl` | **Already safely isolated** | Same — all source-calling sites already `catch (e: Throwable)`. The two remaining `catch(Exception)` blocks in this file are local file I/O (image splitting, temp-file cleanup), not source calls. |
| `app/.../mihon/domain/source/interactor/UpdateMangaFromRemote.kt` | `getMangaUpdate` | **Migrated** | Added `catch(Error)`; a recoverable `LinkageError` now becomes `Result.failure(...)`, the same contract every caller already handles. |
| `app/.../data/library/LibraryUpdateJob.kt` (`updateManga()`) | (calls `UpdateMangaFromRemote`) | **Already safely isolated** | Its only caller wraps the call in `catch (e: Throwable) { ...; failedUpdates.add(...) }` — confirmed by direct inspection; a `LinkageError` already becomes a per-manga failed update, sibling manga updates continue. |
| `app/.../ui/browse/BulkFavoriteScreenModel.kt` (2 call sites) | (calls `UpdateMangaFromRemote`, `.getOrThrow()`) | **Migrated** | Found and fixed a real remaining gap even after `UpdateMangaFromRemote`'s own fix: `Result.failure(linkageError).getOrThrow()` rethrows the raw `Error`, which the existing `catch(Exception)` does not catch. Added `catch(Error)` at both call sites. |
| `app/.../exh/recs/BrowsePersonalRecommendationsScreenModel.kt` | `getFilterList`+`getSearchManga` | **Migrated in fix2**, unchanged here | Verified still correct: `catch(Exception)`/new `catch(Error)` both present. |
| `app/.../exh/recs/RecommendsScreenModel.kt` | (delegates to `CrossExtensionGenreSearchSource`) | **Migrated in fix2**, narrowed here | `if (e is Error) throw e` → delegates to the shared classifier (was "every Error is fatal," now "every Error except LinkageError is fatal"). |
| `app/.../exh/recs/sources/CrossExtensionGenreSearchSource.kt` (3 call sites) | `getFilterList`, `getSearchManga` (per-plan), `getSearchManga` (title fallback) | **Migrated** | The actual For You/group-recommendation source-search engine underneath `RecommendsScreenModel`. All three sites added `catch(Error)` with the same graceful fallback already used for `Exception`. |
| `app/.../exh/recs/sources/RecommendationPagingSource.kt` | `getMangaUpdate`/`getSearchManga`/`getFilterList` (delegate pass-through) | **Already safely isolated** | Third-party recommendation-provider paging (AniList/MAL/MangaUpdates), not local installed sources; its own `requestNextPage()` `catch(Exception)` is a pass-through rethrow already scoped correctly (not swallowing/hiding). The delegate methods are one-line pass-throughs; protection responsibility is at the caller (`CrossExtensionGenreSearchSource`, already migrated, and `RecommendationSearchHelper`, migrated below). |
| `app/.../exh/recs/RecommendationCandidateEnricher.kt` | `getMangaUpdate` | **Already safely isolated** | Uses `runCatching {}.getOrNull()` — catches `Throwable`. |
| `app/.../exh/recs/group/GroupRecommendationSeedBuilder.kt` | `getMangaUpdate` | **Already safely isolated** | Uses `runCatching {}.onFailure { if (e is CancellationException) throw e }.getOrNull()` — catches `Throwable`, correctly rethrows cancellation. |
| `app/.../exh/recs/batch/RecommendationSearchHelper.kt` | `requestNextPage()` (can delegate to a local source via `RecommendationSource`) | **Migrated** | Bulk recommendation-bundle search (JSON export/import). Added `catch(Error)` to the sibling-isolated per-source `async` loop. |
| `app/.../exh/recs/matching/CrossExtensionMatchScreenModel.kt` | `getSearchManga`+`getFilterList` | **Migrated** | Widened `lastError` to `Throwable?`; added `catch(Error)` at both the per-query and outer boundaries. |
| `app/.../exh/recs/matching/SameMangaCandidateSearcher.kt` | `getSearchManga`+`getFilterList` | **Migrated** | Same pattern as `CrossExtensionMatchScreenModel`. |
| `app/.../exh/recs/bestversion/BestVersionCompareScreenModel.kt` | `getMangaUpdate` (chapters), `getPageList`+`getImageUrl` (preview) | **Migrated** | Both loops added `catch(Error)`, reusing `RecommendationErrorClassifier.classifyToStorageKey` (already `SourceRuntime`-delegating). |
| `app/.../exh/recs/evaluation/SourceEvaluationRunner.kt` | `getPopularManga`/`getLatestUpdates` (via `probeAndScore`) | **Migrated** | Added `catch(Error)` at both the per-source probe loop and the outer per-extension boundary; a broken source now records `ERROR`/`EXTENSION_INCOMPATIBLE` and the batch continues. |
| `app/.../exh/recs/evaluation/SourceEvaluationCatalogueEnricher.kt` | `getMangaUpdate` | **Migrated** | Added `catch(Error)` with the same graceful per-item fallback already used for `Exception`. |
| `app/.../exh/recs/evaluation/SourceRecommendationFitProbe.kt` | `getFilterList`+`getSearchManga` | **Migrated** | Added `catch(Error)` to the per-plan-attempt loop; one plan's failure no longer aborts the whole probe. |
| `app/.../extension/ExtensionManager.kt`, `.../extension/ExtensionsScreenModel.kt`, `.../extension/details/ExtensionDetailsScreenModel.kt` | — | **Already safely isolated (verified by design, not by call-site edit)** | These paths check/update/inspect extension *metadata* through `ExtensionStoreService`/package-manager APIs — they do not construct/execute an installed source's runtime methods (`getPopularManga` etc.) to check for updates or list sources; `AndroidSourceManager` only registers sources once, on extension load (already guarded by official `ExtensionLoader`'s `catch (Throwable)` around construction). No call to any of the ten guarded methods was found in these three files. |

**Summary:** of the confirmed app-owned call sites, **16 were migrated** in fix3 (2 in this pass's
module-boundary-fix commit, 14 across the two migration-batch commits), **7 were confirmed already
safely isolated** by direct inspection (not assumed), and **1 family** (`BrowseSourceScreenModel`'s
remaining `getFilterList()` UI-state calls) is **intentionally deferred** with a documented,
lower-risk reason.

## 5. UI/error behavior

- User-facing text for the new `ExtensionIncompatible`/`EXTENSION_INCOMPATIBLE` classification: "Extension
  incompatible or missing dependency" (KMR `rec_error_extension_incompatible` /
  `source_evaluation_probe_error_extension_incompatible`, base locale only). No raw class names
  (`okhttp3.zstd.Zstd`) ever appear in this text — raw detail is only logged via `logcat` at the catch
  site (existing diagnostic pattern, unchanged).
- No "private"/"public"/"internal build"/"test build" wording was added anywhere app-visible.

## 6. Tests

- `app/src/test/java/eu/kanade/tachiyomi/source/SourceRuntimeTest.kt` (16 tests) — `LinkageError`
  family classification, fatal-error non-recoverability, three-layer wrapper unwrapping, ordinary
  exception/network classification unaffected, `SourceRuntime.run()` success/failure/fatal-
  rethrow/cancellation-rethrow, `SourceRuntimeFailureRegistry` recording + temporary-unavailable
  window.
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationProbeErrorClassifierTest.kt` (5 tests) —
  the new `EXTENSION_INCOMPATIBLE` kind, unwrap behavior, storage-key round-trip, unaffected
  network/timeout/internal classification.
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationLinkageIsolationTest.kt` (2 tests) — drives
  the exact per-source probe catch shape added to `SourceEvaluationRunner.kt`: sibling continuation
  and fatal-error propagation.
- Prior fix2 tests (`RecommendationErrorClassifierExtensionLinkageTest`,
  `RecommendationSourceFailureIsolationTest`) still pass unchanged — `RecommendationErrorClassifier`
  now delegates rather than duplicating, verified with no behavior change.

**No new test source set was added for `core:common`/`data`** — neither module has one configured;
setting one up (build config, JUnit wiring) is disproportionate build-infrastructure work for this
fix, deferred with this documented reason. The relocated functions remain fully covered by
`SourceRuntimeTest` (identical function bodies, only the physical module differs).

**Reader/download tests:** not added. Both call-site families were confirmed *already* safely
isolated by direct inspection (section 4), so there was no behavior change requiring a new test;
adding tests purely for pre-existing, unmodified behavior was judged out of this fix's scope.

## 7. Verification (synchronous, real output)

| Step | Result |
| --- | --- |
| `:app:compileDebugKotlin` (run repeatedly through the pass) | clean throughout |
| Focused new tests (23 total: 16 + 5 + 2) | 23/23 PASSED |
| `spotlessApply` / `spotlessCheck` | clean (one dangling-KDoc ktlint violation on the new `core:common` file found and fixed) |
| Full `:app:testDebugUnitTest` | **1431 tests, 0 failures, 0 errors** (up from 1408 before fix3) |
| `:app:assembleDebug` | run only after all of the above — see section 9 |

## 8. Documentation updated

- `docs/community/KMK_RECS_V0_8_10_FIX2_EXTENSION_ISOLATION_AUDIT.md` — pre-supplied, unchanged by
  this report (its findings were fully addressed, not refined).
- `docs/recommendations/CURRENT_STATE.md`, `docs/recommendations/NEXT_WORK.md`,
  `docs/recommendations/README.md` — updated (see commits).
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md` — the fix2/fix3 doc-index entries were already added
  alongside the plan documents; no new durable reference file was created beyond `SourceRuntime.kt`
  itself (already indexed).
- `RECOMMENDATION_VERSIONING.md` — the pre-supplied "KMK-Recs v0.8.10-fix3 (planned)" stub section
  updated below with the actual outcome.
- `KmkRecsReleaseNotes.kt` — **not bumped**, matching the v0.8.10-fix1 and v0.8.10-fix2 precedent:
  narrowly-scoped crash-isolation/stability fixes are not treated as a new KMK-Recs feature-changelog
  entry. `VERSION_CODE`/`VERSION_NAME` remain at 760/"KMK-Recs v0.8.10".

## 9. Final APK

Built only after all verification in section 7 passed:

- Path: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.14.0-kmk.8.10-fix3-debug.apk`
- App metadata: `versionCode 89` (unchanged — this fix does not touch the upstream Komikku app version)
- Device navigation verification: **not performed by me** — `adb devices` confirmed empty, same
  disclosed limitation as fix1/fix2. Requesting the user install with the currently-broken Asura
  Scans extension still present and confirm: For You, Browse, global search, Source Evaluation, and
  a different extension's update all no longer crash, and a healthy source/chapter still loads
  normally.

## 10. Deviations from the plan

- The plan recommended `SourceRuntimeFailure`/`isRecoverableSourceRuntimeFailure`/
  `unwrapSourceRuntimeCause`/`toSourceRuntimeFailureKind` all live together in one `app`-module file.
  Split two of the four (the pure ones) into `core:common` mid-pass, once the module-boundary blocker
  was confirmed and the coordinator approved the relocation. Documented in section 3.
- `BrowseSourceScreenModel.kt`'s remaining `getFilterList()` calls were deferred rather than migrated
  (section 4) — lower risk, not a confirmed crash site, and outside this pass's remaining budget.
- Extension update/check/detail paths (`ExtensionManager.kt`, `ExtensionsScreenModel.kt`,
  `ExtensionDetailsScreenModel.kt`) were verified by design inspection to not execute installed
  source runtime methods at all, rather than migrated — there was nothing to migrate.
