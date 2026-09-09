# KMK-Recs v0.8.10-fix2 — Extension Linkage Failure Isolation — Implementation Report

Date: 2026-07-18

**Scope note:** narrowly-scoped crash fix only, same discipline as fix1. Does **not** start Phase 2+
of the broader v0.8.10-fix1 conformance plan, which remains on hold.

Actual model/effort: **Opus, high effort** — continuing the same session as fix1.

---

## 1. Confirmed shared boundary (verified before editing, per the plan's instruction)

The plan explicitly warned not to assume the GROUP_PREVIEW catch in `RecommendsScreenModel.kt` is
the only path used by the For You page. Verified directly:

- `RecommendsScreenModel.kt` (line ~436, before this fix): the **only** catch block in the file,
  shared by both the group-preview and single-manga/merged-row code paths (`isGroupPreview` only
  branches the try body, not the catch). Already `catch (e: Throwable) { if (e is Error) throw e;
  ... }` — i.e. it already distinguished `Error` from `Exception`, but classified **every** `Error`
  as fatal.
- `BrowsePersonalRecommendationsScreenModel.kt` (line ~731, before this fix): the actual "For You"
  tab's per-source search loop (`searchSource()`, called from `batch.map { source -> async {
  searchSource(...) } }.awaitAll()`). This one only had `catch (e: Exception)` — **no `Error` handling
  at all**. An uncaught `Error` here would propagate out of the `async {}` block, through
  `.awaitAll()`, and crash the load (and, per the reported symptom, the process).

Both are real, distinct call sites in the actual "shared recommendation/source-request boundary." Both were fixed.

## 2. The fix

**New/extended pure classifier** — `app/src/main/java/exh/recs/RecommendationErrorClassifier.kt`
(the existing v0.7.46 classifier already used by Best Version comparison and recommendation bundle
import — extended, not replaced, per "reuse the existing per-source error state, do not invent a new
error-state mechanism"):

- New `RecommendationErrorKind.ExtensionIncompatible` value.
- `RecommendationErrorClassifier.classify()` now maps any `LinkageError` to it.
- New `RecommendationErrorClassifier.isRecoverableSourceFailure(e: Throwable): Boolean = e !is Error
  || e is LinkageError`. `NoClassDefFoundError`, `NoSuchMethodError`, `NoSuchFieldError`,
  `IncompatibleClassChangeError`, `UnsatisfiedLinkError`, and any other `LinkageError` subtype are all
  covered by the single `is LinkageError` check (they are all `LinkageError` subtypes in the JDK).
  `OutOfMemoryError`, `StackOverflowError`, and any other non-`LinkageError` `Error` remain fatal.

**Call-site changes:**

- `RecommendsScreenModel.kt`: `if (e is Error) throw e` → `if
  (!RecommendationErrorClassifier.isRecoverableSourceFailure(e)) throw e`. The `catch (e:
  CancellationException) { throw e }` clause above it, and the `generationGuard.isCurrent(myGeneration)`
  check before publishing the error row, are both **unmodified**.
- `BrowsePersonalRecommendationsScreenModel.kt`: `var lastError` widened from `Exception?` to
  `Throwable?` (the sealed `PersonalRecommendationResult.Error(val throwable: Throwable)` already
  accepted any `Throwable`, so no further ripple). Added a new `catch (e: Error) { if
  (!RecommendationErrorClassifier.isRecoverableSourceFailure(e)) throw e; lastError = e }` clause
  alongside the pre-existing `catch (e: Exception) { lastError = e }`. `Error` and `Exception` are
  disjoint types, so clause order between them doesn't matter for correctness.

**Sanitized diagnostic category (no raw stack trace in UI text):** both rendering sites
(`BrowsePersonalRecommendationsTab.kt` and `RecommendsScreen.kt`) already funnel through the **one**
official, shared `eu.kanade.presentation.util.ExceptionFormatter.kt`'s `Throwable.formattedMessage`.
Before this fix, an unclassified `Error` fell through to `"$className: $message"` — for the real
crash this would have rendered literally as `"NoClassDefFoundError: Failed resolution of:
Lokhttp3/zstd/Zstd;"` in a normal error row. Added one `is LinkageError -> return
context.stringResource(KMR.strings.rec_error_extension_incompatible)` branch to that single shared
formatter (fixing both KMK render sites, and incidentally any other future/vanilla use of the same
official utility, at the one place it lives — not a new UI mechanism).

New KMR string (base locale only): `rec_error_extension_incompatible` = "Extension incompatible or
missing dependency".

## 3. Quarantine-mechanism decision (plan step 6)

The plan asked me to check whether the existing extension-quarantine mechanism (`KnownUnsafeExtensionPackages`
/ `SourceEvaluationSafetyRepository` / `UnsafeExtensionPackageRepository`) "supports runtime failures"
before deciding whether to wire into it or just report a per-load error state.

**Decision: do not wire into it.** That mechanism is purpose-built for Source Evaluation's own
crash-quarantine pipeline — it exists specifically to record extensions that caused a native-level
crash (SIGSEGV) *during active Source Evaluation probing*, persisted across app restarts, and gates
whether Source Evaluation will attempt to probe that extension again. Wiring a For You/group-recommendation
`LinkageError` into that same store would:

- conflate two structurally different failure classes (a native crash during a deliberate probe vs.
  a class-loading failure during ordinary recommendation search), and
- have an unrequested side effect: it would also quarantine the source from Source Evaluation's
  probing, which the plan's non-goals explicitly forbid ("Do not touch ... unrelated UI" /
  "narrowly-scoped fix").

Instead, the fix reuses the **existing per-load error-state mechanism already used for every other
recoverable source failure** (network errors, timeouts, etc.) — `RecommendationItemResult.Error` /
`PersonalRecommendationResult.Error`, exactly the same terminal state a normal exception already
produces. This satisfies "prevent immediate retry loops for the failed source during the active
load" for free: once a source's request is classified as failed (Exception or a recoverable
`LinkageError`), the existing control flow (the `plans` for-loop in `BrowsePersonalRecommendationsScreenModel`,
or the single request in `RecommendsScreenModel`) does not retry that source again within the same
load — identical to how it already handles a normal network exception. No new quarantine store was
invented; no existing one was modified.

## 4. Tests added (19 new)

**`app/src/test/java/exh/recs/RecommendationErrorClassifierExtensionLinkageTest.kt`** (14 tests):
classification of `NoClassDefFoundError` (the real confirmed cause), `NoSuchMethodError`,
`NoSuchFieldError`, `IncompatibleClassChangeError`, `UnsatisfiedLinkError`, and a generic
user-defined `LinkageError` subtype as recoverable; `OutOfMemoryError`, `StackOverflowError`, and an
unrelated non-linkage `AssertionError` proven still fatal (never downgraded); ordinary `Exception`/
`IOException`/`CancellationException` classification unaffected by the new branch; the storage key
never contains the raw exception message/class-resolution detail; the KMR resource resolves to the
real registered string.

**`app/src/test/java/exh/recs/RecommendationSourceFailureIsolationTest.kt`** (5 tests): since
`RecommendsScreenModel`/`BrowsePersonalRecommendationsScreenModel` are heavy `StateScreenModel`s with
many Android/Injekt dependencies this pure-JVM environment cannot construct (no Robolectric), these
tests drive the *exact* control-flow shape both production catch blocks use — real
`async`/`awaitAll`, real `CancellationException`, real thrown `Error`s — against a small harness that
mirrors the production catch-clause pattern line-for-line:

- one source's recoverable linkage failure becomes an error row while sibling sources still succeed;
- multiple sibling sources can each independently fail with their own linkage error;
- a genuinely fatal `OutOfMemoryError` still propagates out of `awaitAll()`, never downgraded;
- `CancellationException` is never converted into a per-source error row;
- a cancelled parent job's sibling work does not publish stale results.

## 5. Verification (synchronous, real output)

| Step | Result |
| --- | --- |
| `:app:compileDebugKotlin` | clean (two pre-existing `RecommendationErrorKind` exhaustive `when` sites needed the new `ExtensionIncompatible` branch — `RecommendationErrorClassifier.kt` itself and `BestVersionCompareScreen.kt`; both fixed) |
| Focused tests (34 total: 14 + 5 new plus re-verifying no regression) | 19/19 new PASSED |
| `spotlessApply` / `spotlessCheck` | clean |
| Full `:app:testDebugUnitTest` | **1408 tests, 0 failures, 0 errors** (up from 1389 before this fix) |
| `:app:assembleDebug` | BUILD SUCCESSFUL |

## 6. Non-goals honored

- Recommendation ranking/scoring/taste logic: untouched.
- Normal global search limits: untouched (this fix only touches the recommendation-specific
  `RecommendsScreenModel`/`BrowsePersonalRecommendationsScreenModel` catch blocks, plus the shared
  official exception formatter which is additive-only — a new `is LinkageError` branch, no existing
  branch changed).
- No new HTTP dependency added; Asura's own broken packaging is not "fixed" — its failure is now
  correctly isolated instead of crashing the app.
- No raw stack trace in UI text (section 2/4).
- No development-channel wording anywhere app-visible.
- No broad process-level catch; `CancellationException` and non-linkage `Error`s still propagate
  exactly as before.

## 7. Versioning decision

`KmkRecsReleaseNotes.VERSION_CODE`/`VERSION_NAME` were **not** bumped for this pass, matching the
v0.8.10-fix1 precedent: a narrowly-scoped DI/crash-isolation fix is not treated as a new KMK-Recs
feature-changelog entry. `app/build.gradle.kts` `versionName`/`versionCode` (1.14.0/89) are also
unchanged — this fix does not touch the upstream Komikku app version.

## 8. Files changed

- `app/src/main/java/exh/recs/RecommendationErrorClassifier.kt` (modified — new kind + classifier function)
- `app/src/main/java/exh/recs/RecommendsScreenModel.kt` (modified — narrowed Error rethrow condition)
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` (modified — widened `lastError`, added `catch (e: Error)`)
- `app/src/main/java/exh/recs/bestversion/BestVersionCompareScreen.kt` (modified — exhaustive `when` needed the new case)
- `app/src/main/java/eu/kanade/presentation/util/ExceptionFormatter.kt` (modified — sanitized `LinkageError` message branch)
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` (modified — new `rec_error_extension_incompatible` string)
- `app/src/test/java/exh/recs/RecommendationErrorClassifierExtensionLinkageTest.kt` (added — 14 tests)
- `app/src/test/java/exh/recs/RecommendationSourceFailureIsolationTest.kt` (added — 5 tests)
- `docs/recommendations/CURRENT_STATE.md`, `docs/recommendations/README.md` (modified — index/status updates)

## 9. Final APK

Built only after all verification above passed:

- Path: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.14.0-kmk.8.10-fix2-debug.apk`
- Size: 171,383,907 bytes
- SHA-256: `fb10fca44b6c703ee65f6ac1ac7d9b48bb5f1ca0540fdaba2b21f1692acd610b`
- App metadata (via `aapt dump badging`): `package: name='app.komikku.dev' versionCode='89'
  versionName='1.14.0-33'` — `versionCode`/base `versionName` unchanged from fix1/Phase I; the `-33`
  build-count suffix is auto-generated and simply reflects additional commits since the last build,
  not a version change.
- Device navigation verification: **not performed by me** — no `adb`/device access in this
  environment (`adb devices` returns empty, same disclosed limitation as fix1). Requesting the user
  install and confirm the Asura For You crash no longer reproduces, and that Library/Browse/manga
  detail/reader/Source Evaluation/Loved-Liked-Disliked/What's New all remain reachable.
