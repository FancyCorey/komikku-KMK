# KMK-Recs v0.8.10-fix1 — Crash Fix Implementation Report (Phase 0/1, DI Registration)

Date: 2026-07-18

**Scope note:** This is a focused implementation report for the confirmed application-wide crash
fix only. It is **not** the full v0.8.10-fix1 implementation report — Phase 2 (Recommendation
Settings/Source Evaluation conformance) and all later phases have **not** started.

Actual model/effort: **Opus, high effort** — continuing the same session as the Phase 0 gate work
(`36a7b1658`), per the plan's Phase 0/1 model recommendation.

---

## 1. The confirmed root cause

`mihon.domain.source.interactor.UpdateMangaFromRemote` was **never registered** in
`app/src/main/java/eu/kanade/domain/DomainModule.kt`. Verified directly against source before
touching anything:

```
grep -n "UpdateMangaFromRemote" app/src/main/java/eu/kanade/domain/DomainModule.kt
```
returned **no matches** prior to this fix.

`BulkFavoriteScreenModel`'s constructor resolves it as a default-argument `Injekt.get()`, which is
evaluated **at construction time**:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/browse/BulkFavoriteScreenModel.kt:60
private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
```

Because the type was unregistered, `Injekt.get<UpdateMangaFromRemote>()` threw
`InjektionException("No registered instance or factory for: mihon.domain.source.interactor.UpdateMangaFromRemote")`
the instant any code constructed a `BulkFavoriteScreenModel()` — which happens for the bulk-selection
flow reachable from Browse (`BulkFavoriteScreenModel.kt:60` → `BrowseTab.kt:90` → `HomeScreen`, per
the supplied device stack trace), and by extension anywhere that same navigation graph is entered.
This matches the reported pattern exactly: pure-preference Settings never constructs this
screen model, so it was unaffected, while Library/Browse/manga-detail/Global-Search flows that
route through `HomeScreen`'s bulk-selection surface did.

This is precisely the class of defect my Phase 0 static crash investigation (`36a7b1658`) was
structured to find but could not, without a device, distinguish from the several other structural
vectors I ruled out (Injekt *module* registration, `Manga` serialization, migration 63, Voyager arg
safety, source casts) — all of which were genuinely sound and remain unmodified. The actual defect
was a single missing `addFactory` line, not any of those.

### 1.1 Verified constructor shape before writing the fix

Read the real `UpdateMangaFromRemote.kt` constructor directly (not assumed):

```kotlin
class UpdateMangaFromRemote(
    private val sourceManager: SourceManager,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val coverCache: CoverCache,
    private val libraryPreferences: LibraryPreferences,
    private val downloadManager: DownloadManager,
)
```

Seven parameters, exactly matching the coordinator-proposed `get()` × 7 shape — confirmed correct,
no adjustment needed.

## 2. The fix

**File changed:** `app/src/main/java/eu/kanade/domain/DomainModule.kt`.

- Added `import mihon.domain.source.interactor.UpdateMangaFromRemote`.
- Added the registration immediately after the pre-existing `FilterChaptersForDownload` line (the
  same neighborhood as `SyncChaptersWithSource`, one of `UpdateMangaFromRemote`'s own dependencies,
  already registered there):

```kotlin
addFactory {
    UpdateMangaFromRemote(
        get(),
        get(),
        get(),
        get(),
        get(),
        get(),
        get(),
    )
}
```

Placed in the normal upstream `DomainModule` (not `KMKDomainModule`) as instructed — this is an
upstream Mihon interactor, not a KMK addition. No nullable lookup, lazy fallback, or broad exception
handler was used; this is the real registration. `KMKDomainModule`'s existing
`GetCrossSourceGroupPrimary`/`SetCrossSourceGroupPrimary`/`ClearCrossSourceGroupPrimary`
registrations were **not modified** — a new regression test (below) proves they still resolve.

## 3. Tests added

**File:** `app/src/test/java/eu/kanade/domain/UpdateMangaFromRemoteRegistrationTest.kt` (4 tests, all
using a real `Injekt` instance with mocked leaf dependencies — no Android/Robolectric needed since
this project doesn't use it).

1. `UpdateMangaFromRemote resolves through Injekt once its seven dependencies are bound` — proves
   the exact registration shape added to `DomainModule.kt` (mirrored, since importing the real
   `DomainModule` wholesale would pull in dozens of other Android-Context-dependent factories this
   environment can't satisfy) successfully resolves.
2. `all seven UpdateMangaFromRemote constructor dependencies resolve independently` — proves
   `SourceManager`, `ChapterRepository`, `MangaRepository`, `SyncChaptersWithSource`, `CoverCache`,
   `LibraryPreferences`, `DownloadManager` all resolve without throwing.
3. `BulkFavoriteScreenModel constructs successfully once UpdateMangaFromRemote is registered` — the
   most direct regression test: constructs a real `BulkFavoriteScreenModel()` with **all** of its
   default-arg dependencies bound (mocked), reproducing the exact crashing construction path from the
   device stack trace and proving it no longer throws.
4. `the pre-existing cross-source group-primary registrations this fix must not disturb still
   resolve` — regression check confirming `GetCrossSourceGroupPrimary`/`SetCrossSourceGroupPrimary`/
   `ClearCrossSourceGroupPrimary` (the v0.8.1-fix2 registrations this fix was explicitly told not to
   touch) still resolve correctly; `KMKDomainModule.kt` was not edited in this pass.

## 4. Verification run (synchronous, real output)

| Step | Result |
| --- | --- |
| Focused test (`UpdateMangaFromRemoteRegistrationTest`) | 4/4 PASSED |
| Focused Browse/recommendation-adjacent tests (`BrowsePersonalRecommendationsFilterTest`, `SourceMatchScorerTest`) | all PASSED (no regressions from the `DomainModule.kt` change) |
| `spotlessApply` / `spotlessCheck` | clean |
| Full `:app:testDebugUnitTest` | **1389 tests, 0 failures, 0 errors** (up from 1385 before this fix — the 4 new tests) |
| `:app:assembleDebug` | BUILD SUCCESSFUL |

## 5. Device navigation verification — **not performed by me; explicit device-access limitation**

I do **not** have device or `adb` access in this environment. I re-ran `adb devices` immediately
before this report and it returned an empty device list, same as during Phase 0. I have not
installed or navigated the app, and I am not claiming to have done so.

**An intermediate validation APK was built** (per "intermediate builds are validation artifacts
only" — this is explicitly not the final v0.8.10-fix1 handoff APK):

- Path: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.14.0-kmk.8.10-fix1-crashfix-validation-debug.apk`
- Size: 171,383,123 bytes
- SHA-256: `411a5d4097fd57bc845196effb77df4ea1146a75c70735bafd9575f8a91e595c`

**Requesting user confirmation:** please install this APK and navigate to Library, Browse, For You,
Global Search, manga detail, reader, Source Evaluation, and Loved/Liked/Disliked, and confirm whether
the crash is resolved and whether any other navigation path still fails. I cannot substitute my own
static confidence for that confirmation.

## 6. Explicitly out of scope for this pass

Per the coordinator's instruction, the SQLite database-lock messages reportedly also seen on-device
were **not investigated or fixed** in this pass — they are not the cause of the fatal DI exception
addressed here. This is recorded as a **separate, open follow-up item** for a future phase, not
resolved and not silently dropped.

## 7. What this pass does NOT claim

- This does **not** claim the full v0.8.10-fix1 plan is complete. Phase 2 (Recommendation Settings/
  Source Evaluation official-DSL conformance) and every phase after it are **unstarted**.
- This does **not** claim the crash is fixed on a real device — only that the specific, confirmed,
  reproducible defect (the missing `UpdateMangaFromRemote` registration causing
  `BulkFavoriteScreenModel` construction to throw) is fixed in code, verified by a test that
  constructs the exact real failing object graph, and does not crash. Real-device confirmation is
  requested, not assumed.
- The SQLite lock issue remains **unresolved and deferred**, not fixed.

## 8. Files changed

- `app/src/main/java/eu/kanade/domain/DomainModule.kt` (modified — added import + registration)
- `app/src/test/java/eu/kanade/domain/UpdateMangaFromRemoteRegistrationTest.kt` (added — 4 tests)

No other production files were touched in this pass.
