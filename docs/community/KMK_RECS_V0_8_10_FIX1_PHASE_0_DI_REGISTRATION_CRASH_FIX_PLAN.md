# KMK-Recs v0.8.10-fix1 - Phase 0 DI Registration Crash Fix

**Status:** Planning only. No code changes are authorized by this document.

**Parent plan:** `KMK_RECS_V0_8_10_FIX1_FULL_KOMIKKU_CONFORMANCE_AND_STABILITY_IMPLEMENTATION_PLAN.md`

**Purpose:** Fix the confirmed fatal application crash caused by a missing Injekt registration.
This phase must be completed and verified before any UI-conformance or historical What's New work.

## 1. Claude Execution Assignment

```text
Recommended model: Sonnet 5
Recommended effort: low
Task shape: one narrow dependency-injection bug fix with focused regression tests.
Do not use broad agent research, repository-wide redesign, or an automated loop.
Do not add a model directive to the implementation prompt; select Sonnet 5 Low separately in Claude.
```

The implementation prompt must tell Claude to stop after this phase's tests and report. It must not
start the full v0.8.10-fix1 UI-conformance plan in the same session.

## 2. Confirmed Crash Evidence

The supplied crash log repeatedly reports:

```text
uy.kohesive.injekt.api.InjektionException:
No registered instance or factory for type class
mihon.domain.source.interactor.UpdateMangaFromRemote
```

The first meaningful application frame is:

```text
eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
    BulkFavoriteScreenModel.kt:60
eu.kanade.tachiyomi.ui.browse.BrowseTab
    BrowseTab.kt:90
```

This is a fatal main-thread construction failure during Compose navigation. It is not a network,
WebView, SQLite-content, recommendation-scoring, or UI-spacing problem.

The interactor exists at:

```text
app/src/main/java/mihon/domain/source/interactor/UpdateMangaFromRemote.kt
```

`BulkFavoriteScreenModel` requests it through:

```kotlin
private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get()
```

The current `DomainModule` imports and registers many domain interactors but does not register
`UpdateMangaFromRemote`. The historical Komikku/TachiyomiX registration found in repository history
is:

```kotlin
addFactory { UpdateMangaFromRemote(get(), get(), get(), get(), get(), get(), get()) }
```

The constructor dependency order must be verified against the live constructor before editing:

1. `SourceManager`;
2. `ChapterRepository`;
3. `MangaRepository`;
4. `SyncChaptersWithSource`;
5. `CoverCache`;
6. `LibraryPreferences`;
7. `DownloadManager`.

Do not change the interactor implementation or constructor unless compilation proves that the live
Komikku 1.14.0 code differs from this verified order.

## 3. Exact Implementation

Modify only the dependency-injection area initially:

```text
app/src/main/java/eu/kanade/domain/DomainModule.kt
```

Required changes:

1. Add:

```kotlin
import mihon.domain.source.interactor.UpdateMangaFromRemote
```

2. Inside `DomainModule.registerInjectables()`, add exactly one factory registration near the other
   source/manga domain registrations:

```kotlin
addFactory { UpdateMangaFromRemote(get(), get(), get(), get(), get(), get(), get()) }
```

3. Do not add the registration to `KMKDomainModule`; this is an upstream domain interactor and
   belongs in `DomainModule`.

4. Do not replace `Injekt.get()` with nullable lookup, lazy fallback, or a catch-all exception.
   Missing required dependencies must be fixed at the module boundary.

## 4. Registration Audit In The Same Phase

After adding the known registration, perform a bounded audit of screen models instantiated from the
main navigation path. Compare every `Injekt.get<T>()` or `injectLazy<T>()` used by these consumers
against `DomainModule`, `KMKDomainModule`, `SYDomainModule`, and other imported modules:

- `BulkFavoriteScreenModel`;
- `BrowseTab`;
- `GlobalSearchScreen`;
- `SourceFeedScreen`;
- `BrowseSourceScreen`;
- `MigrateSearchScreen`;
- `MigrateSourceSearchScreen`;
- `MangaScreen`;
- `RecommendsScreen`;
- `RelatedMangasScreen`;
- `LovedMangaScreenModel`;
- `LinkedVersionListScreenModel`;
- `TasteBackupCreator` and `TasteRestorer`;
- `ReaderViewModel`.

This is an audit for missing registrations only. Do not refactor the DI architecture or rename
modules in this phase. If another missing required registration is found, add it to this same
focused fix only when the failure is proven and the constructor wiring is unambiguous. Document
each additional registration separately.

## 5. Required Tests

Create or extend a focused test under:

```text
app/src/test/java/eu/kanade/domain/
```

The test must:

- create an isolated Injekt registrar/test module with the same `UpdateMangaFromRemote` factory;
- register constructor dependencies using test doubles or existing repository test helpers;
- assert that `Injekt.get<UpdateMangaFromRemote>()` resolves without throwing;
- assert that the exact constructor dependencies are wired in the correct order;
- cover all additional missing registrations found by the bounded audit.

If the project has a production-module registration harness, prefer it over duplicating a fake
module. The test must not depend on the user's database, installed extensions, network, or device.

Also add a construction smoke test for `BulkFavoriteScreenModel` if the existing test infrastructure
can provide its dependencies. If that is not practical, document why and rely on the registration
test plus real-device navigation.

## 6. Verification Sequence

Run only the focused checks first:

1. `spotlessApply` if needed;
2. `spotlessCheck`;
3. the new DI registration test;
4. existing domain/module registration tests;
5. relevant Browse/Manga/Global Search tests;
6. `:app:testDebugUnitTest`;
7. `assembleDebug`.

On the tablet/device, install the resulting intermediate APK and verify:

- app startup;
- Library;
- Browse;
- For You;
- Global Search;
- manga detail;
- reader;
- Source Evaluation;
- Loved/Liked/Disliked collections;
- What's New;
- navigating away and back repeatedly;
- process restart followed by the same navigation sequence.

The original `UpdateMangaFromRemote` fatal exception must not recur. The older
`GetCrossSourceGroupPrimary` registration must remain intact and must not regress.

## 7. SQLite Lock Handling

The log also contains:

```text
SQLite Error : 3850
database is locked
PRAGMA journal_mode=TRUNCATE
```

This is not the cause of the fatal DI crash and must not be mixed into this registration fix.
Record it as a separate follow-up diagnostic unless a focused reproduction proves that it occurs
before the DI failure and prevents startup. Do not change database locking behavior in this phase.

## 8. Documentation And Handoff

After the focused fix is verified, create an implementation report containing:

- exact root cause;
- exact files changed;
- all registrations audited;
- tests and commands run;
- device navigation results;
- whether the SQLite lock remains open;
- requested and actual Sonnet 5 effort;
- APK path and version if an intermediate build is produced;
- remaining v0.8.10-fix1 work.

Do not mark the entire v0.8.10-fix1 plan complete. Do not implement UI conformance, What's New
conversion, performance work, or release-readiness work in this phase.
