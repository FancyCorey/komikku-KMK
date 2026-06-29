# KMK-Recs v0.6.17: Startup Crash Recovery and Known Unsafe Source Seed Plan

Date: 2026-06-20

Feature version: KMK-Recs v0.6.17

Status: planning. Do not implement until approved by the user.

## Goal

Fix the remaining crash loop where Komikku can close about 30-60 seconds after app startup before the user reaches Source Evaluation.

This is a focused hotfix on top of v0.6.16. It must preserve the v0.6.16 probe-marker quarantine system, but move the recovery point earlier and add a narrow known-unsafe seed for the repeatedly crashing DigitalComicMuseum extension.

Target APK:

`Komikku-v1.13.6-kmk.6.17-debug.apk`

## Why v0.6.17 Is Needed

v0.6.16 implemented:

- `source_evaluation_probe_marker` table;
- `source_evaluation_unsafe_source` table;
- marker writes before risky Source Evaluation probe phases;
- unsafe-source candidate filtering;
- Source Evaluation diagnostics UI;
- recovery on `SourceEvaluationScreenModel` initialization.

That is useful, but the fourth crash report shows a new operational problem:

- the app can crash shortly after launch;
- the user may not have time to navigate to Source Evaluation;
- therefore screen-open recovery is too late.

The crash remains a native process crash:

```text
Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR)
Cause: stack pointer is not in a rw map; likely due to stack overflow.
Thread: DefaultDispatch
OkHttp / HttpLoggingInterceptor / IgnoreGzipInterceptor
DigitalComicMuseum
```

This cannot be fixed with ordinary `try/catch`.

## New Crash Evidence

Fourth reported crash:

```text
App ID: app.komikku.dev
App version: 1.13.6-1 (582ea3e, 79, 2026-06-20T09:16:09Z)
Android version: 16 (SDK 36)
Device: Samsung SM-X520
Process uptime: 35s
Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR)
Cause: stack pointer is not in a rw map; likely due to stack overflow.
```

Backtrace points to:

```text
okhttp3.internal.http.CallServerInterceptor.intercept
okhttp3.logging.HttpLoggingInterceptor.intercept
eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor.intercept
eu.kanade.tachiyomi.extension.en.digitalcomicmuseum.DigitalComicMuseum.b
```

Earlier same-day crash shows the same SIGSEGV / stack-overflow pattern.

There is also a preceding image-loader error:

```text
RealImageLoader: Failed - MangaCover(... qiscans ... webp)
java.lang.IllegalStateException: Unbalanced enter/exit
at okio.AsyncTimeout.enter
...
TachiyomiImageDecoder$Factory.isApplicable
```

Do not treat the image-loader line as the primary root cause unless later logs prove it. The fatal native stack repeatedly points to DigitalComicMuseum/network interception.

## Existing v0.6.16 Implementation Gap

Current implementation report says:

> On the next screen open, read the record. If recent (< 24h), mark the extension unsafe, then clear the record.

Current code confirms recovery lives in:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

inside `init`.

That means recovery happens only after entering Source Evaluation. This is not early enough for the reported startup crash loop.

## Required v0.6.17 Changes

### 1. Create Startup Recovery Helper

Create a reusable helper/interactor that can run outside `SourceEvaluationScreenModel`.

Suggested file:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationStartupRecovery.kt`

Responsibilities:

1. Read the existing probe marker.
2. Apply `SourceEvaluationCrashRecoveryPolicy.decide(marker, now)`.
3. If `MarkUnsafe`, call `MarkSourceEvaluationUnsafe` and clear marker.
4. If `ClearStale`, clear marker only.
5. If `DoNothing`, no-op.
6. Seed known unsafe sources if applicable.
7. Never crash app startup.

It should catch all normal exceptions internally and log them with:

```text
KMK SourceEvaluation startup recovery:
```

Return a lightweight result for diagnostics/logging:

```kotlin
data class StartupRecoveryResult(
    val markerRecovered: Boolean,
    val markerClearedStale: Boolean,
    val seededUnsafeCount: Int,
    val errorMessage: String? = null,
)
```

If adding this result is too much, logging-only is acceptable, but the helper must remain testable where practical.

### 2. Run Recovery During App Startup

Integrate in:

`app/src/main/java/eu/kanade/tachiyomi/App.kt`

Current order:

1. `patchInjekt()`
2. telemetry/global exception setup
3. imports `PreferenceModule`, `AppModule`, `DomainModule`, `KMKDomainModule`, etc.
4. logging setup
5. other app startup flows

The recovery must run after Injekt modules are registered and database/repositories can be resolved.

Recommended location:

- after `Injekt.importModule(KMKDomainModule())` and related modules are imported;
- after logging is initialized if possible;
- before optional work such as sync-on-start, widgets, or any recommendation/source-evaluation background behavior.

Because startup must not block or crash:

- launch on `ProcessLifecycleOwner.get().lifecycleScope` or an application coroutine scope already used in `App.kt`;
- run on `Dispatchers.IO` if database calls are blocking/suspending;
- wrap with `runCatching`/try-catch;
- log failures and continue.

Do not make startup recovery depend on Compose, Voyager, or Source Evaluation UI.

### 3. Keep Screen-Open Recovery, But Reuse Helper

Do not duplicate recovery logic.

Refactor `SourceEvaluationScreenModel` so its existing recovery block uses the new helper or a shared interactor.

Expected behavior:

- startup recovery normally handles markers first;
- screen-open recovery remains as a second safety net;
- both paths use the same policy and reason strings;
- duplicate marking should not create duplicate unsafe records. Existing `markUnsafeFromProbe` should increment if same unsafe key exists, so avoid running both paths concurrently if practical.

If both startup and screen recovery run, ensure the marker is cleared after first success, so the second path becomes `DoNothing`.

### 4. Add Known-Unsafe Seed for DigitalComicMuseum

The crash logs repeatedly identify:

```text
eu.kanade.tachiyomi.extension.en.digitalcomicmuseum.DigitalComicMuseum
```

Likely package:

```text
eu.kanade.tachiyomi.extension.en.digitalcomicmuseum
```

Claude must verify the package from available/installed extension metadata before using it.

Add a narrowly scoped, removable unsafe seed.

Suggested helper:

`SourceEvaluationKnownUnsafeSeeds.kt`

Fields:

```kotlin
data class KnownUnsafeSeed(
    val pkgName: String,
    val extensionName: String,
    val reason: String,
)
```

Seed:

- pkgName: verified DigitalComicMuseum package
- extensionName: `DigitalComicMuseum`
- reason:

```text
Known native crash suspect from user log: DigitalComicMuseum caused SIGSEGV/stack overflow during network interception.
```

Behavior:

- startup recovery checks installed and available extensions;
- if DigitalComicMuseum package is present and no unsafe record exists for the extension key, create one;
- if signature hash is available from extension metadata, use it;
- if signature hash cannot be found, do not create a fake malformed unsafe record. Log that seed could not be applied.

The seed must be:

- visible in unsafe/quarantine UI;
- removable by the user;
- not a permanent invisible blocklist;
- used only for Source Evaluation candidate filtering.

### 5. Handle Installed Unsafe Extension Messaging

v0.6.16 unsafe filtering mainly protects Source Evaluation candidates.

The new crash can happen after startup, possibly because an installed extension or background image/network path triggers the same bad code.

Add UI/diagnostic wording:

- unsafe/quarantined sources are skipped by Source Evaluation;
- if an installed quarantined extension still crashes Komikku outside Source Evaluation, the user should uninstall or disable that extension manually.

Do not automatically uninstall anything.

Do not hide installed extensions from the normal Extensions screen. The user needs to manage/uninstall them.

### 6. Update Source Evaluation Diagnostics

Add startup recovery fields to diagnostics if practical:

- last startup recovery ran: yes/no;
- marker recovered at startup: yes/no;
- known unsafe seed applied count;
- DigitalComicMuseum unsafe status if present.

If storing this state is too much for one patch, at minimum log it with prefix:

```text
KMK SourceEvaluation startup recovery:
```

### 7. Optional: Image Loader Note Only

The log includes:

```text
RealImageLoader ... IllegalStateException: Unbalanced enter/exit
```

Do not conflate this with the fatal crash unless repeated fatal stacks point at Coil/TachiyomiImageDecoder without DigitalComicMuseum.

Recommended handling for v0.6.17:

- mention it in implementation notes as observed but not the primary fatal stack;
- do not change image loading in this patch unless Claude finds an obvious local bug.

If Claude finds a simple safe guard in `TachiyomiImageDecoder.Factory.isApplicable` or `CbzCrypto.detectCoverImageArchive`, document it separately and keep it minimal.

## Files Likely To Change

### App Startup

- `app/src/main/java/eu/kanade/tachiyomi/App.kt`

Add startup recovery after Injekt module registration and before optional startup work.

### Source Evaluation Recovery

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationStartupRecovery.kt` (new)
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationKnownUnsafeSeeds.kt` (new if useful)
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

Refactor screen recovery to use shared startup recovery helper or shared underlying interactor.

### Domain/Data

Likely no new database table is required. Use existing v0.6.16 tables:

- `source_evaluation_probe_marker`
- `source_evaluation_unsafe_source`

Only add migration if Claude determines a missing column is required. Prefer no schema change for this hotfix.

### UI/Strings

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Add wording that installed unsafe extensions may need manual uninstall if they crash outside Source Evaluation.

### Version/Docs

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- new implementation report after coding:
  `docs/recommendations/KMK_RECS_V0_6_17_SOURCE_EVALUATION_STARTUP_RECOVERY_AND_KNOWN_UNSAFE_SEED_IMPLEMENTATION.md`

## Versioning

Bump to:

```kotlin
VERSION_CODE = 617
VERSION_NAME = "KMK-Recs v0.6.17"
```

APK:

```text
Komikku-v1.13.6-kmk.6.17-debug.apk
```

What's New should be user-facing only:

```markdown
## KMK-Recs v0.6.17

- Source Evaluation crash recovery now runs during app startup, so suspected crashing extensions can be quarantined before you reopen the Source Evaluation screen.
- Added a removable quarantine seed for DigitalComicMuseum, based on repeated native crash logs showing it in the fatal network stack.
- Improved unsafe-extension guidance when a quarantined installed extension may need to be manually uninstalled.
```

## Tests

Add or update tests where practical.

### SourceEvaluationStartupRecoveryTest

If helper is pure enough or can be tested with fakes:

- recent marker is marked unsafe and cleared;
- stale marker is cleared only;
- no marker does nothing;
- recovery catches repository failure and returns/logs error instead of throwing;
- known unsafe seed is applied when matching extension metadata exists;
- seed is not applied when signature hash cannot be resolved.

### SourceEvaluationKnownUnsafeSeedsTest

- DigitalComicMuseum package/class metadata matches expected package;
- seed reason is non-empty;
- seed is removable/not permanent by design.

If tests require too many Android dependencies, extract pure policy decisions into a pure helper and test that.

Existing tests must still pass:

- `SourceEvaluationCrashRecoveryPolicyTest`
- `SourceEvaluationUnsafeKeysTest`
- `SourceEvaluationCandidateFilterTest`
- `SourceEvaluationResultListTest`

## Build Commands

Run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Fix failures before handing off.

## Manual Verification

### Crash Loop Break Test

1. Install v0.6.17 over v0.6.16/v0.6.15.
2. Open Komikku.
3. Do not navigate anywhere.
4. Wait 60 seconds.
5. Confirm the app does not close.

### Startup Recovery Test

1. Open Komikku.
2. Confirm startup recovery logs appear with prefix:

```text
KMK SourceEvaluation startup recovery:
```

3. If a recent probe marker exists, confirm it is marked unsafe and cleared.
4. If no marker exists, confirm recovery no-ops safely.

### DigitalComicMuseum Seed Test

1. Ensure DigitalComicMuseum is installed or present in available extension metadata.
2. Launch Komikku.
3. Confirm it is added to unsafe/quarantine list if not already present.
4. Confirm it appears in Source Evaluation unsafe UI.
5. Confirm it can be removed manually.
6. Confirm Source Evaluation skips it by default.

### Source Evaluation Test

1. Open Recommendation settings > Source Evaluation.
2. Wait 30 seconds.
3. Confirm app does not close.
4. Run a small Private evaluation batch.
5. Confirm normal failures/timeouts do not create false unsafe marks.
6. Confirm unsafe hidden count displays when applicable.

### Installed Unsafe Guidance Test

1. If DigitalComicMuseum is installed and quarantined, Source Evaluation UI should indicate quarantine affects Source Evaluation.
2. It should also explain that if an installed extension crashes Komikku outside Source Evaluation, the user should uninstall/disable that extension manually.

## Recommendation

Implement v0.6.17 as a focused hotfix before continuing recommendation feature work.

v0.6.16 created the right quarantine infrastructure, but recovery happens too late for the current crash loop. Moving recovery to startup and seeding the repeated DigitalComicMuseum crash suspect should prevent the app from repeatedly dying before the user can reach Source Evaluation.
