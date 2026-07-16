# KMK-Recs v0.6.16: Source Evaluation Crash Quarantine and Diagnostics Plan

Date: 2026-06-20

Feature version: KMK-Recs v0.6.16

Status: planning. Do not implement until approved by the user.

## Goal

Fix the Source Evaluation workflow so that one bad extension cannot repeatedly kill Komikku during evaluation, and add Source Evaluation-specific diagnostics so future failures are visible and actionable.

This should be implemented as one patch and one APK:

`Komikku-v1.13.6-kmk.6.16-debug.apk`

Do not split crash quarantine and diagnostics into separate APKs.

## User Report

After running Source Evaluation successfully, the user opened:

Recommendation settings > Source Evaluation

The app stayed open for roughly 15 seconds and then closed without showing Komikku's crash screen.

The user later provided logcat output:

```text
Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR)
Cause: stack pointer is not in a rw map; likely due to stack overflow.
tid: DefaultDispatch
okhttp3...
HttpLoggingInterceptor.intercept
eu.kanade.tachiyomi.extension.en.digitalcomicmuseum.DigitalComicMuseum
a.intercept
```

This points to an extension/network/interceptor path causing a native fatal crash while Source Evaluation is probing a source. The visible culprit in the provided log is:

```text
eu.kanade.tachiyomi.extension.en.digitalcomicmuseum.DigitalComicMuseum
```

## Important Diagnosis

This is not a normal Kotlin exception.

It is a process-level native fatal crash:

- `SIGSEGV`
- likely stack overflow
- happening on `DefaultDispatch`
- inside OkHttp / extension code

Normal `try/catch` cannot reliably catch this. Once Android delivers `SIGSEGV`, the process is killed. Komikku's `GlobalExceptionHandler` does not necessarily run, which explains why the crash screen did not appear.

Private extension installation helps with install/cleanup isolation. It does not sandbox extension code execution. Once a private extension is loaded, its source code still runs inside Komikku's process.

Therefore the correct fix is not broad try/catch everywhere. The correct fix is:

1. record which extension/source is being probed before touching it;
2. detect unfinished probe markers after restart;
3. mark that extension/source as unsafe;
4. skip unsafe entries by default;
5. expose diagnostics and recovery controls in the Source Evaluation UI.

## Scope

This patch includes two linked pieces:

1. **Crash Quarantine**
   - Persist currently-probing extension/source/phase.
   - On next app/screen start, if the previous probe marker was not cleared, treat it as a suspected fatal crash.
   - Record the extension/source as unsafe for Source Evaluation.
   - Skip unsafe entries by default.

2. **Source Evaluation Diagnostics and Error Containment**
   - Add Source Evaluation-local error state and diagnostic logging.
   - Show UI-visible errors instead of silent failure where failures are catchable.
   - Provide a way to copy or dump Source Evaluation diagnostics.

Do not change:

- For You recommendation scoring;
- Sources To Try scoring;
- manga rating behavior;
- cross-extension rating matching;
- general extension install behavior outside Source Evaluation;
- normal global search.

## Existing Relevant Code

### Source Evaluation Screen

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

Currently handles:

- batch size;
- installer mode;
- Shizuku setup card;
- skip/include-explicit options;
- candidate diagnostics;
- start/cancel evaluation;
- past result sorting from v0.6.15.

### Source Evaluation Screen Model

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

Current high-risk paths:

- `getSourceEvaluations.subscribeAll()`
- `getSourceEvaluationCandidates.subscribe()`
- `applyOptionsAndUpdateState()`
- `refreshInstallerPolicy()`
- `launchEvaluation()`
- `clearSourceEvaluations.await()`

The flow `.catch {}` blocks help for normal exceptions, but do not cover native fatal crashes and do not provide enough in-app diagnostics.

### Source Evaluation Runner

`app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`

This is the critical crash-quarantine integration point.

Before each risky operation, the runner should write a probe marker. After the operation completes or fails normally, it should clear or advance the marker.

Risky phases include:

- installing/loading an extension;
- loading catalogue sources;
- calling `source.getPopularManga(1)`;
- calling `source.getLatestUpdates(1)`;
- calling `source.getSearchManga(...)`;
- cleanup/unload.

The fatal crash log specifically points to a network call during probing.

### Candidate Provider

`app/src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt`

Currently builds a candidate pool from:

- available extensions;
- installed extensions;
- untrusted extensions;
- recommendation language preferences;
- NSFW/explicit preferences;
- disliked source keys;
- source evaluation records.

This provider should be extended to also exclude unsafe/quarantined extensions by default.

### Source Evaluation Storage

Current source evaluation table:

`data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`

This stores evaluation results and error rows. It should not be overloaded for crash quarantine unless the resulting model remains clear.

Recommendation:

Create a separate table for fatal-crash quarantine/probe markers.

## Data Model Plan

### New Table: `source_evaluation_probe_marker`

Purpose:

Record the one source/extension currently being evaluated so a process death can be attributed after restart.

Suggested SQLDelight file:

`data/src/main/sqldelight/tachiyomi/data/source_evaluation_probe_marker.sq`

Table:

```sql
CREATE TABLE source_evaluation_probe_marker (
    id INTEGER NOT NULL PRIMARY KEY,
    evaluation_key TEXT,
    extension_pkg_name TEXT NOT NULL,
    signature_hash TEXT NOT NULL,
    extension_name TEXT NOT NULL,
    source_id INTEGER,
    source_name TEXT,
    lang TEXT,
    phase TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    batch_id TEXT
);
```

Use a single-row marker:

- `id = 1`
- overwritten as the runner advances.

Queries:

```sql
get:
SELECT *
FROM source_evaluation_probe_marker
WHERE id = 1;

upsert:
INSERT INTO source_evaluation_probe_marker(...)
VALUES (...)
ON CONFLICT(id) DO UPDATE SET ...;

clear:
DELETE FROM source_evaluation_probe_marker
WHERE id = 1;
```

### New Table: `source_evaluation_unsafe_source`

Purpose:

Persist extensions/sources suspected of causing fatal Source Evaluation crashes.

Suggested SQLDelight file:

`data/src/main/sqldelight/tachiyomi/data/source_evaluation_unsafe_source.sq`

Table:

```sql
CREATE TABLE source_evaluation_unsafe_source (
    unsafe_key TEXT NOT NULL PRIMARY KEY,
    evaluation_key TEXT,
    extension_pkg_name TEXT NOT NULL,
    signature_hash TEXT NOT NULL,
    extension_name TEXT NOT NULL,
    source_id INTEGER,
    source_name TEXT,
    lang TEXT,
    phase TEXT NOT NULL,
    reason TEXT NOT NULL,
    crash_count INTEGER NOT NULL,
    first_seen_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    last_batch_id TEXT
);
```

`unsafe_key` should be stable:

```text
signatureHash|pkgName|sourceId
```

If `sourceId` is not available:

```text
signatureHash|pkgName
```

Queries:

- `getAll`
- `getAllAsFlow`
- `getByKey`
- `upsertCrash`
- `deleteByKey`
- `deleteAll`

When upserting an already unsafe item:

- increment `crash_count`;
- update `last_seen_at`;
- update `phase`, `reason`, `last_batch_id`;
- preserve `first_seen_at`.

### Migration

Add a new migration file with the next migration number after the current latest.

Claude must inspect:

`data/src/main/sqldelight/tachiyomi/migrations`

and create the next numbered `.sqm`.

Migration must use `CREATE TABLE IF NOT EXISTS` and `CREATE INDEX IF NOT EXISTS` where appropriate.

Suggested indexes:

```sql
CREATE INDEX IF NOT EXISTS source_evaluation_unsafe_pkg_index
ON source_evaluation_unsafe_source(extension_pkg_name);

CREATE INDEX IF NOT EXISTS source_evaluation_unsafe_seen_index
ON source_evaluation_unsafe_source(last_seen_at);
```

## Domain/Data Layer Plan

### New Domain Models

Create:

`domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluationProbeMarker.kt`

`domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluationUnsafeSource.kt`

Fields should map directly to SQL.

Add helper key builders:

```kotlin
object SourceEvaluationUnsafeKeys {
    fun build(signatureHash: String, pkgName: String, sourceId: Long?): String
}
```

Reuse `SourceEvaluationKeys.buildKey(...)` where appropriate for evaluation key.

### New Repository

Create:

`domain/src/main/java/tachiyomi/domain/taste/repository/SourceEvaluationSafetyRepository.kt`

Methods:

```kotlin
interface SourceEvaluationSafetyRepository {
    fun getUnsafeAsFlow(): Flow<List<SourceEvaluationUnsafeSource>>
    suspend fun getUnsafe(): List<SourceEvaluationUnsafeSource>
    suspend fun getProbeMarker(): SourceEvaluationProbeMarker?
    suspend fun upsertProbeMarker(marker: SourceEvaluationProbeMarker)
    suspend fun clearProbeMarker()
    suspend fun markUnsafeFromProbe(marker: SourceEvaluationProbeMarker, reason: String, now: Long)
    suspend fun deleteUnsafe(key: String)
    suspend fun clearUnsafe()
}
```

Data implementation:

`data/src/main/java/tachiyomi/data/taste/SourceEvaluationSafetyRepositoryImpl.kt`

Register in the same module where `SourceEvaluationRepositoryImpl` is registered.

### New Interactors

Create:

- `GetSourceEvaluationUnsafeSources`
- `GetSourceEvaluationProbeMarker`
- `UpsertSourceEvaluationProbeMarker`
- `ClearSourceEvaluationProbeMarker`
- `MarkSourceEvaluationUnsafe`
- `DeleteSourceEvaluationUnsafe`
- `ClearSourceEvaluationUnsafe`

Register them with Injekt.

## Crash Recovery Flow

### On Source Evaluation Screen Init

In:

`SourceEvaluationScreenModel.init`

Before or alongside candidate loading:

1. read probe marker;
2. if marker exists and is recent enough, mark it unsafe;
3. clear marker;
4. log:

```text
KMK SourceEvaluation fatal-suspect: detected unfinished probe marker ...
```

Suggested recency threshold:

- default: 24 hours;
- if older, clear marker without marking unsafe.

Reason string:

```text
App exited while Source Evaluation was probing this source. Possible native crash or process kill.
```

This handles the provided DigitalComicMuseum case after the app restarts and the user opens Source Evaluation.

### On App Startup

Optional but recommended:

Run the same recovery check once during app startup if there is a clean place to do it without broad architectural changes.

If startup integration is too invasive, screen-open recovery is acceptable for v0.6.16 because the user naturally returns to Source Evaluation to continue.

### During Runner Execution

In `SourceEvaluationRunner`, write/update marker:

- before evaluating each extension;
- before loading sources;
- before each source probe phase;
- before cleanup if cleanup can touch extension code.

Clear marker:

- after the extension finishes normally;
- after normal caught failure is recorded;
- after user cancellation is processed.

Do not clear marker before risky source code returns. The marker must survive fatal process death.

### Source-Level vs Extension-Level Quarantine

Use source-level quarantine when `sourceId` is available.

Use extension-level quarantine when only extension metadata is available.

For a crash during source probe, mark that source unsafe.

For a crash during install/load before source ID is known, mark the extension unsafe.

Candidate filtering should skip an extension if:

- the extension-level unsafe key exists; or
- all catalogue sources from that extension are known unsafe, where that information is available.

Because non-installed candidates usually only know extension metadata before loading, the practical v0.6.16 default is:

- if any unsafe record exists for the same `signatureHash|pkgName`, skip the extension candidate by default.

This is conservative but appropriate for process-fatal failures.

## Known-Bad Initial Handling

The provided log points to:

```text
eu.kanade.tachiyomi.extension.en.digitalcomicmuseum.DigitalComicMuseum
```

Do not hardcode a permanent blocklist unless needed.

Recommended:

- rely on marker-based detection first;
- optionally add a temporary one-time known-bad seed only if current users are stuck in a crash loop before a marker can be recorded.

If adding a seed, make it visible and removable in the unsafe sources UI. Do not silently hardcode it forever.

## Candidate Filtering Integration

Update:

`GetSourceEvaluationCandidates.kt`

Add `GetSourceEvaluationUnsafeSources` or repository flow to its combine.

Candidate provider should exclude unsafe extension keys by default.

Add counts to diagnostics:

```kotlin
unsafeHiddenCount: Int
```

Update:

`SourceEvaluationCandidateFilter.CandidatePoolResult`

Add:

```kotlin
unsafeExtensionKeys: Set<String>
unsafeHiddenCount: Int
```

Update build/apply filtering to count unsafe-hidden candidates separately.

## Source Evaluation ScreenModel Diagnostics Plan

Add local state fields:

```kotlin
val screenErrorMessage: String? = null
val lastDiagnosticText: String? = null
val unsafeSources: List<SourceEvaluationUnsafeSource> = emptyList()
```

Add methods:

- `retryCandidateLoading()`
- `clearScreenError()`
- `copyDiagnosticsToClipboard()`
- `clearUnsafeSources()`
- `deleteUnsafeSource(key)`
- `retryUnsafeSourcesOnce()` or `setIncludeUnsafeForNextRun(enabled)`

Recommended first pass:

- allow clear unsafe;
- allow delete individual unsafe;
- do not auto-retry unsafe in normal runs.

### Diagnostic Text

Build a text block containing:

- KMK-Recs version;
- timestamp;
- selected installer mode;
- batch size;
- skip/include-explicit flags;
- candidate count;
- evaluation result count;
- unsafe source count;
- last queue status/phase;
- current extension/source if any;
- Shizuku state;
- last screen error stack trace if present;
- last probe marker if present.

Use existing `copyToClipboard` helper if available:

`eu.kanade.tachiyomi.util.system.copyToClipboard`

## Source Evaluation Error Containment Plan

Wrap targeted paths with `runCatching` or helper functions.

Do not add broad app-wide catches.

### `refreshInstallerPolicy()`

Current risk:

- `basePreferences.extensionInstaller().get()`
- `basePreferences.extensionInstaller().entries`
- `ShizukuSetupHelper.readState(context)`
- policy validation

Wrap the method:

- on failure, log with `KMK SourceEvaluation error: refreshInstallerPolicy`;
- update `screenErrorMessage`;
- set safe fallback policy:
  - installer mode Private if possible;
  - Shizuku state false/false/false;
  - readiness unavailable if policy cannot be computed.

### `applyOptionsAndUpdateState()`

Wrap:

- `SourceEvaluationCandidateFilter.applyOptions(...)`
- state update mapping

On failure:

- log;
- set candidates empty;
- show screen error;
- keep existing evaluations visible.

### Candidate Flow Transform

In `GetSourceEvaluationCandidates.subscribe()`:

Wrap the combine body.

If buildPool fails:

- log;
- emit an empty `CandidatePoolResult`;
- include an error reason if model is extended to carry it.

### Evaluation Result Flow

Current v0.6.15 sanitizes rows.

Add:

- log count of dropped rows when `sanitize` removes rows;
- catch unexpected sorting failures in screen/model and show unsorted sanitized list instead.

### Clear Actions

Wrap:

- `clearSourceEvaluations.await()`
- `clearUnsafeSources`
- `deleteUnsafeSource`

On failure:

- show screen error;
- do not crash.

### Runner State Collection

In `launchEvaluation()`:

The runner state flow should have `.catch {}` before `.launchIn(screenModelScope)`.

On catch:

- show screen error;
- set queue state to failed with error message.

## Source Evaluation UI Plan

### Error Card

Add near top of Source Evaluation screen, below progress/summary or above options:

Title:

```text
Source Evaluation error
```

Body:

Use `state.screenErrorMessage`.

Actions:

- `Retry`
- `Copy diagnostics`
- `Dismiss`

Do not make the whole screen unusable if an error exists. Past results should remain visible.

### Unsafe Sources Card

Show if `state.unsafeSources.isNotEmpty()`.

Content:

```text
Unsafe sources skipped: N
These sources previously caused Source Evaluation to stop unexpectedly and are skipped by default.
```

Actions:

- `View`
- `Clear`
- `Copy diagnostics`

### Unsafe Sources Dialog or Section

Display list:

- extension name;
- source name if available;
- lang;
- phase;
- crash count;
- last seen date/time;
- reason.

Actions per row:

- remove unsafe mark.

Bulk action:

- clear all unsafe marks, confirmation required.

Retry behavior:

- do not include unsafe sources by default;
- if adding retry, make it explicit and one-run-only.

### Candidate Diagnostics Row

Update:

- show unsafe-hidden count if > 0.

Example:

```text
3 unsafe sources skipped
```

## Strings

Add under `<!-- KMK v0.6.16 -->` in:

`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Suggested strings:

- `source_evaluation_error_title`
- `source_evaluation_error_dismiss`
- `source_evaluation_error_retry`
- `source_evaluation_copy_diagnostics`
- `source_evaluation_diagnostics_copied`
- `source_evaluation_unsafe_title`
- `source_evaluation_unsafe_summary`
- `source_evaluation_unsafe_view`
- `source_evaluation_unsafe_clear`
- `source_evaluation_unsafe_clear_title`
- `source_evaluation_unsafe_clear_message`
- `source_evaluation_unsafe_remove`
- `source_evaluation_unsafe_count`
- `source_evaluation_candidates_unsafe_hidden`

Keep wording user-facing. Do not mention internal docs.

## Backup/Restore Note

Do not add unsafe-source quarantine to backup in this patch.

Reason:

- unsafe marks are device/build/extension-version specific;
- a source that crashes on one device or extension version may not crash elsewhere;
- carrying quarantine through backup could incorrectly block a source after reinstall or extension update.

Backup/restore coverage should be handled in a separate later plan.

## Documentation Updates

Update:

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`

Create implementation report after coding:

`docs/recommendations/KMK_RECS_V0_6_16_SOURCE_EVALUATION_CRASH_QUARANTINE_AND_DIAGNOSTICS_IMPLEMENTATION.md`

Release notes should mention only user-facing changes:

```markdown
## KMK-Recs v0.6.16

- Added Source Evaluation crash quarantine. Sources suspected of killing the app during evaluation are now skipped by default.
- Added Source Evaluation diagnostics so evaluation errors can be copied and investigated.
- Added unsafe-source controls for viewing, clearing, or removing unsafe marks.
```

## Tests

Add pure unit tests where possible.

Suggested tests:

### SourceEvaluationUnsafeKeysTest

- source-level key includes source ID;
- extension-level key omits source ID;
- keys are stable.

### SourceEvaluationCandidateFilterTest updates

Add tests:

- unsafe extension is hidden;
- unsafe hidden count increments;
- unsafe filtering is independent of explicit filtering;
- disliked and unsafe counts do not double-count the same hidden item if implementation makes one precedence choice.

### SourceEvaluationCrashRecoveryPolicyTest

If implementing a pure helper:

`SourceEvaluationCrashRecoveryPolicy`

Test:

- recent unfinished marker becomes unsafe;
- old marker is cleared but not marked unsafe;
- null marker does nothing;
- reason string is stable enough for diagnostics.

### SourceEvaluationDiagnosticsTest

If diagnostic builder is pure:

- includes candidate count;
- includes unsafe count;
- includes queue phase;
- includes last error text.

## Build Commands

Claude should run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Fix failures before handing off.

Copy final APK using current naming pattern:

```text
Komikku-v1.13.6-kmk.6.16-debug.apk
```

## Manual Verification

### Basic Open Test

1. Install v0.6.16.
2. Open Recommendation settings > Source Evaluation.
3. Wait 30 seconds.
4. Confirm app does not close.
5. Confirm diagnostics button/action is available.

### Existing Unsafe Detection Test

If the user already has an unfinished marker from previous crash, opening Source Evaluation should:

1. detect marker;
2. mark suspected source unsafe;
3. clear marker;
4. show unsafe skipped count/card.

If no marker exists, this path cannot be manually verified without intentionally crashing during a probe.

### Evaluation Test

1. Run batch 10 with Private.
2. Confirm marker updates do not block normal evaluation.
3. Confirm batch completes.
4. Confirm no unsafe mark is created for normally failing/timeouting sources.

### Known Problem Source Test

If DigitalComicMuseum appears in evaluation candidates:

1. run evaluation until it is reached;
2. if the app crashes, reopen Source Evaluation;
3. confirm it is marked unsafe;
4. confirm future evaluations skip it.

If it is already marked unsafe, confirm it is skipped without crash.

### Diagnostics Test

1. Trigger a catchable failure or use an existing error state.
2. Tap Copy diagnostics.
3. Paste into a text field.
4. Confirm it includes:
   - version;
   - candidate count;
   - unsafe count;
   - queue state;
   - last error if any.

## Risk and Mitigation

### Risk: False Positive Quarantine

An app could be killed by Android memory pressure while a source marker is active.

Mitigation:

- use clear user-facing wording: "suspected unsafe";
- allow removing unsafe marks;
- use recency threshold;
- only mark when marker exists and was not cleared.

### Risk: Database Migration Issues

New tables require migration.

Mitigation:

- add SQLDelight `.sq` definitions;
- add matching `.sqm` migration with `IF NOT EXISTS`;
- add flow `.catch` fallbacks for unsafe-source reads.

### Risk: Too Much UI Complexity

Unsafe-source controls could clutter the Source Evaluation screen.

Mitigation:

- show unsafe card only when unsafe entries exist;
- keep detailed list in dialog or expandable section.

### Risk: Diagnostics Exposes Too Much

Diagnostics may include extension/source names and internal state.

Mitigation:

- only copy when user explicitly taps Copy diagnostics;
- do not auto-upload anything.

## Recommendation

Implement v0.6.16 as the next patch before doing more source discovery or scoring work.

The provided log proves Source Evaluation can execute third-party extension code that kills the whole Komikku process. Since this is not catchable with normal Kotlin error handling, the app needs persistent crash recovery and quarantine around the extension currently being probed. Diagnostics should be included in the same APK so future failures can be diagnosed without guessing.

