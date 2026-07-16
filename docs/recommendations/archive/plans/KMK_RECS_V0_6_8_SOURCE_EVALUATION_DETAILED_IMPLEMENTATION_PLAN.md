# KMK-Recs v0.6.8 Source Evaluation Detailed Implementation Plan

Status: planning. Do not implement until the user explicitly approves or provides this plan to Claude for implementation.

Date: 2026-06-19

Related planning files:

- `KMK_RECS_V0_6_8_SOURCE_EVALUATION_QUEUE_PLAN.md`
- `KMK_RECS_V0_6_8_EVALUATION_INSTALLER_OVERRIDE_PLAN.md`

This document is the code-aware implementation plan for the source evaluation feature. It supersedes the older v0.6.8 planning notes for implementation purposes, while preserving those files as background research.

## Goal

Build a bounded, one-at-a-time source evaluation system that can temporarily install non-installed extension candidates, inspect the actual sources and manga results exposed by those extensions, store summarized local evidence, then use that evidence to improve:

- Sources To Try recommendations;
- explicit porn/hentai blocking;
- installed source prioritization;
- user decisions about which extensions are worth installing, liking, disliking, or rejecting.

The purpose is to move beyond name/package heuristics. The current non-installed recommendation system cannot know whether a source is good until the extension is installed and sampled.

## Confirmed Current Code Context

### Existing Non-Installed Suggestions Are Metadata-Only

Current file:

`app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt`

Current behavior:

- Source appears only with meaningful positive evidence, mostly conservative similarity to installed source names.
- User-liked available sources bypass the evidence gate.
- Scores are capped at `0.69`; `0.70+` is reserved for future post-install evidence.
- Language, repo, base URL, and generic content keywords are eligibility filters only.
- Explicit blocking uses `ExplicitSourceClassifier.isExplicitExtension(ext)`.

Implementation implication:

- Do not try to solve source quality only by improving `NonInstalledSourceSuggestionScorer`.
- Keep it as the fast unevaluated-source heuristic.
- Add evaluated-source evidence as a new scoring layer above it.

### Existing Explicit Blocking Is Conservative

Current file:

`app/src/main/java/exh/source/ExplicitSourceClassifier.kt`

Current behavior:

- Blocks obvious explicit porn/hentai sources by known source IDs, strong explicit name substrings, and package substrings.
- Does not block `ecchi`, `nsfw`, `mature`, `lewd`, or `adult` alone.
- Browse > Sources, Browse > Extensions available list, and Sources To Try already consult this setting.

Implementation implication:

- Do not replace this classifier.
- Add content-based evaluation evidence as a second layer when available.
- Keep explicit-heavy and ecchi-heavy as separate outcomes.

### Existing Installer Path

Current files:

- `app/src/main/java/eu/kanade/domain/base/BasePreferences.kt`
- `app/src/main/java/eu/kanade/domain/base/ExtensionInstallerPreference.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`
- `app/src/main/java/eu/kanade/tachiyomi/extension/installer/ShizukuInstaller.kt`

Existing installer modes:

```kotlin
LEGACY
PACKAGEINSTALLER
SHIZUKU
PRIVATE
```

Important observed behavior:

- `ExtensionManager.installExtension(extension)` currently calls `installer.downloadAndInstall(api.getApkUrl(extension), extension)`.
- `ExtensionInstaller.downloadAndInstall()` currently reads the global `basePreferences.extensionInstaller()` during install.
- `PRIVATE` installs by copying the APK into Komikku private extension storage through `ExtensionLoader.installPrivateExtensionFile(context, tempFile)`.
- Private extension removal can be direct through `ExtensionLoader.uninstallPrivateExtension(context, pkgName)` plus extension removal notification.
- Normal system-installed extensions uninstall through Android `Intent.ACTION_UNINSTALL_PACKAGE`.
- Shizuku install exists through `ShizukuInstaller`.
- A Shizuku uninstall path was not found in the current installer implementation.

Implementation implication:

- The evaluation queue should prefer `PRIVATE` mode because it supports temporary install/evaluate/remove without Android uninstall prompts.
- Shizuku can smooth installation, but automatic cleanup may still require new uninstall support or user/system uninstall prompts.
- The app must not attempt to stop Shizuku, revoke Shizuku permission, or uninstall Shizuku. It can only stop using Shizuku for this evaluation run.

### Existing Extension Loading

Current file:

`app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt`

Important observed behavior:

- Private extensions are stored under `context.filesDir/exts`.
- `installPrivateExtensionFile()` copies a validated extension APK there and notifies added/replaced.
- `uninstallPrivateExtension()` deletes the private extension file.
- `loadExtensions()` loads shared and private extension packages, preferring shared if the shared version code is higher or equal.
- Extensions can expose multiple `CatalogueSource` instances.

Implementation implication:

- Evaluation should install one extension, wait for `ExtensionManager.installedExtensionsFlow` to expose it, probe its `CatalogueSource` entries, then remove it.
- If the same package is already shared/system-installed, do not treat it as a safe temporary candidate; skip it or mark as already installed.

### Existing Source APIs

Current file:

`source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt`

Useful APIs:

```kotlin
suspend fun getPopularManga(page: Int): MangasPage
suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage
suspend fun getLatestUpdates(page: Int): MangasPage
fun getFilterList(): FilterList
val supportsLatest: Boolean
suspend fun getRelatedMangaList(...)
```

Current paging helpers:

`data/src/main/java/tachiyomi/data/source/SourcePagingSource.kt`

Implementation implication:

- Evaluation should call source APIs directly in a bounded worker, not through UI paging.
- Use `getPopularManga(1)`, `getLatestUpdates(1)` only when supported, and a very small number of `getSearchManga(1, query, source.getFilterList())` probes.
- Avoid full catalogue crawling.
- Avoid `getMangaDetails()` except for a tiny capped sample, because details fetches are expensive and source-dependent.

### Existing Database Pattern

Current SQLDelight location:

`data/src/main/sqldelight/tachiyomi/data`

Existing KMK tables:

- `manga_taste.sq`
- `tag_taste.sq`
- `tag_alias.sq`
- `recommendation_cache.sq`
- `recommendation_disabled_source.sq`

Existing repository pattern:

- Domain interfaces under `domain/src/main/java/tachiyomi/domain/taste/repository`
- Domain models/interactors under `domain/src/main/java/tachiyomi/domain/taste`
- Data implementations under `data/src/main/java/tachiyomi/data/taste`
- Dependency registration in `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`

Implementation implication:

- Source evaluation should be stored in SQLDelight using the same pattern.
- Do not store evaluation records only in preferences.
- This data is local analysis cache and can be regenerated; backup/restore can be deferred unless the user later wants it.

## Major Design Decision

The system must evaluate extensions sequentially.

Batch size means:

```text
number of extension candidates to evaluate in this run
```

Batch size does not mean:

```text
number of extensions to install at once
```

Default behavior should be:

```text
install one extension -> load sources -> probe -> score -> store -> cleanup -> next extension
```

## Versioning

Use:

```text
KMK-Recs v0.6.8
```

Reason:

- This continues the v0.6 extension/source-management track.
- Do not jump to a new major version because this is still part of source discovery, Sources To Try, source like/dislike, and explicit source filtering.

Expected APK naming pattern, if this is the next produced debug build:

```text
Komikku-v1.13.6-kmk.6.8-debug.apk
```

Only use the exact APK name after confirming the current app metadata/versioning.

## Implementation Phases

### Phase 1: Data Model

Add SQLDelight file:

`data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`

Suggested table:

```sql
CREATE TABLE source_evaluation (
    evaluation_key TEXT NOT NULL PRIMARY KEY,
    source_id INTEGER,
    extension_pkg_name TEXT NOT NULL,
    signature_hash TEXT NOT NULL,
    extension_name TEXT NOT NULL,
    source_name TEXT NOT NULL,
    lang TEXT NOT NULL,
    base_url TEXT,
    repo_name TEXT,
    repo_url TEXT,
    source_count INTEGER NOT NULL,
    is_nsfw INTEGER NOT NULL,
    evaluation_version INTEGER NOT NULL,
    evaluated_at INTEGER NOT NULL,
    expires_at INTEGER,
    sample_count INTEGER NOT NULL,
    popular_count INTEGER NOT NULL,
    latest_count INTEGER NOT NULL,
    search_count INTEGER NOT NULL,
    search_success_count INTEGER NOT NULL,
    detail_count INTEGER NOT NULL,
    liked_title_match_count INTEGER NOT NULL,
    preferred_tag_match_count INTEGER NOT NULL,
    blocked_tag_match_count INTEGER NOT NULL,
    explicit_signal_count INTEGER NOT NULL,
    ecchi_signal_count INTEGER NOT NULL,
    duplicate_signal_count INTEGER NOT NULL,
    error_count INTEGER NOT NULL,
    quality_score REAL NOT NULL,
    recommendation_fit_score REAL NOT NULL,
    search_reliability_score REAL NOT NULL,
    explicit_score REAL NOT NULL,
    ecchi_score REAL NOT NULL,
    verdict TEXT NOT NULL,
    reasons_json TEXT,
    sampled_titles_json TEXT,
    sampled_tags_json TEXT,
    error_message TEXT
);

CREATE INDEX source_evaluation_source_id_index ON source_evaluation(source_id);
CREATE INDEX source_evaluation_pkg_index ON source_evaluation(extension_pkg_name);
CREATE INDEX source_evaluation_verdict_index ON source_evaluation(verdict);
CREATE INDEX source_evaluation_evaluated_at_index ON source_evaluation(evaluated_at);
```

Suggested key:

```text
signatureHash|pkgName|sourceId
```

When `sourceId` is unavailable before load, use an extension-level pending key, then write source-level records after load.

Add queries:

- `getAll`
- `getByKey`
- `getBySourceId`
- `getByPackage`
- `getRecent`
- `getStrongFits`
- `getRejected`
- `upsert`
- `deleteByKey`
- `deleteByPackage`
- `deleteExpired`
- `deleteAll`

Add domain model:

`domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt`

Add enum-like sealed/value model:

`SourceEvaluationVerdict`

Suggested verdict values:

- `strong_fit`
- `worth_trying`
- `neutral`
- `weak`
- `poor_search`
- `explicit_heavy`
- `ecchi_heavy`
- `rejected`
- `error`
- `needs_manual_review`

Add repository:

`domain/src/main/java/tachiyomi/domain/taste/repository/SourceEvaluationRepository.kt`

Add implementation:

`data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt`

Register in:

`app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`

Suggested interactors:

- `GetSourceEvaluations`
- `GetSourceEvaluation`
- `UpsertSourceEvaluation`
- `DeleteSourceEvaluation`
- `ClearSourceEvaluations`

### Phase 2: Installer Override Support

Preferred implementation: true temporary installer override.

Modify:

`app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt`

Add optional parameter:

```kotlin
fun installExtension(
    extension: Extension.Available,
    installerOverride: BasePreferences.ExtensionInstaller? = null,
): Flow<InstallStep>
```

Modify:

`app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt`

Add optional parameter:

```kotlin
fun downloadAndInstall(
    url: String,
    extension: Extension,
    installerOverride: BasePreferences.ExtensionInstaller? = null,
): Flow<InstallStep>
```

Thread the effective installer into `installApk`:

```kotlin
private fun installApk(
    downloadId: Long,
    tempFile: File,
    installer: BasePreferences.ExtensionInstaller,
)
```

Effective installer:

```kotlin
val effectiveInstaller = installerOverride ?: extensionInstaller.get()
```

Important:

- Normal install/update behavior must remain unchanged.
- The override must only affect evaluation-run installs.
- Do not mutate global `basePreferences.extensionInstaller()` for the preferred implementation.

Fallback only if temporary override is too invasive:

- Store previous global installer.
- Set global installer for the evaluation run.
- Restore in `finally`.
- Persist enough state to restore after app restart.

This fallback is less desirable and should be documented if used.

### Phase 3: Installer Policy Helper

Add pure helper:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationInstallerPolicy.kt`

Responsibilities:

- Determine available evaluation installer modes.
- Recommend default evaluation installer.
- Validate requested mode.
- Compute max recommended batch size.
- Decide whether warning is required.

Inputs:

- current installer preference;
- available entries from `ExtensionInstallerPreference.entries`;
- whether Shizuku is installed;
- whether Shizuku binder is available;
- whether Shizuku permission is granted;
- requested batch size.

Policy:

- `PRIVATE`: preferred, allow 10/25/50/100.
- `SHIZUKU`: allowed when installed/running/permission granted; allow 10/25/50, 100 with warning.
- `PACKAGEINSTALLER` / `LEGACY`: allow 5/10 only by default and warn about repeated prompts.

Clarification:

- "Disable Shizuku after done" means Komikku stops using Shizuku for the evaluation run.
- Do not stop Shizuku itself.

### Phase 4: Evaluation Worker

Add package:

`app/src/main/java/exh/recs/evaluation`

Core classes:

- `SourceEvaluationRunner`
- `SourceEvaluationQueueState`
- `SourceEvaluationCandidateSelector`
- `SourceEvaluationProbeRunner`
- `SourceEvaluationScorer`
- `SourceEvaluationCleanup`

Evaluation algorithm:

```text
for each candidate in selected batch:
    mark candidate downloading
    install extension with evaluation installer override
    wait for InstallStep.Installed or timeout
    wait for ExtensionManager.installedExtensionsFlow to expose matching pkgName/signatureHash
    collect catalogue sources from the installed extension
    for each eligible source:
        probe source with strict limits
        score source
        upsert source_evaluation row
    cleanup trial extension
    wait for ExtensionManager.installedExtensionsFlow to stop exposing trial extension if cleanup removed it
    continue
```

Candidate identity:

```text
signatureHash|pkgName
```

Source identity:

```text
signatureHash|pkgName|sourceId
```

Use `supervisorScope` so one failed source does not abort the whole extension batch.

Use `withTimeout` around:

- install wait;
- extension-manager exposure wait;
- each popular/latest/search probe;
- cleanup wait.

Recommended first-pass timeout values:

- install: 90 seconds;
- extension load: 20 seconds;
- per source probe: 20-30 seconds;
- cleanup: 20 seconds.

These can be adjusted after tablet testing.

### Phase 5: Candidate Selection

Candidate source:

- `ExtensionManager.availableExtensionsFlow`
- Existing non-installed suggestions from `GetNonInstalledSourceSuggestions`

Eligibility:

- not already installed;
- not untrusted;
- language matches recommendation languages;
- not explicitly disliked by available source key unless user chooses to include disliked;
- not recently evaluated unless "re-evaluate" is selected;
- if explicit block is enabled, skip name/package explicit candidates unless the user chooses an explicit-classification audit mode.

Priority:

1. user-liked available suggestions;
2. existing Sources To Try suggestions;
3. unevaluated extensions matching recommendation language;
4. stale evaluations;
5. low-confidence metadata matches.

Do not start with all 500/1000 candidates in the first version. Support:

- 10
- 25
- 50
- 100 with warning

Custom or 500/1000 batches should be deferred until pause/resume and cleanup have proven stable.

### Phase 6: Probe Strategy

For each `CatalogueSource`, collect:

- source id;
- source name;
- language;
- base URL if source type exposes it;
- `supportsLatest`;
- `isNsfw` from extension;
- source count in extension.

Run bounded probes:

1. Popular sample:
   - call `getPopularManga(1)`;
   - cap to first 10-20 returned items.

2. Latest sample:
   - only if `supportsLatest`;
   - call `getLatestUpdates(1)`;
   - cap to first 10-20 returned items.

3. Search probes:
   - derive 3-5 probes from taste profile:
     - loved titles;
     - liked titles;
     - preferred tag terms if useful as search text;
   - call `getSearchManga(1, query, source.getFilterList())`;
   - cap to first 5-10 returned items per query.

4. Detail probes:
   - optional and tiny;
   - fetch details for at most 3 candidates per source;
   - use only when listing/search result has missing tags/description and source looks promising.

Avoid:

- page 2+ crawling;
- chapter fetching;
- downloading images;
- full metadata enrichment for every result;
- per-source custom logic in the first version.

### Phase 7: Scoring

Add:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt`

Inputs:

- sampled titles;
- sampled descriptions;
- sampled genres/tags;
- source metadata;
- taste profile from `GetTasteProfile`;
- liked/disliked source preferences;
- explicit block preference.

Signals:

- `likedTitleMatchCount`: exact/normalized overlap with loved/liked manga titles.
- `preferredTagMatchCount`: sampled tags match preferred tag groups/aliases.
- `blockedTagMatchCount`: sampled tags match blocked/disliked tags.
- `explicitSignalCount`: hentai/porn signals from tags/title/description/source.
- `ecchiSignalCount`: ecchi/lewd/mature signals that are not explicit porn/hentai.
- `searchSuccessCount`: search probes returning plausible results.
- `sampleCount`: usable sampled manga count.
- `errorCount`: failed probes.

Scoring guidance:

- `recommendation_fit_score`: user taste match.
- `quality_score`: sample availability and successful probe behavior.
- `search_reliability_score`: successful search probes and sane result counts.
- `explicit_score`: explicit porn/hentai likelihood.
- `ecchi_score`: ecchi/adult-ish but not explicit likelihood.

Verdict rules:

- `explicit_heavy`: high explicit score, especially repeated explicit tags/keywords.
- `ecchi_heavy`: ecchi score high but explicit score low.
- `strong_fit`: strong taste match, low explicit block conflict, usable search.
- `worth_trying`: moderate fit and usable search.
- `poor_search`: repeated search/probe failures.
- `weak`: low fit but not harmful.
- `needs_manual_review`: ambiguous explicit/ecchi or duplicate/weird results.
- `error`: install/load/probe failure prevents evaluation.

Important:

- Do not let ecchi trigger explicit-heavy.
- Do not treat one accidental explicit word as enough for source-level explicit-heavy.
- Store reasons so UI can explain the verdict.

### Phase 8: UI

Preferred location:

Recommendation Settings > Sources To Try > `Source evaluation`

Add a nested screen instead of putting all controls in the main settings list.

New screen:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`

New screen model:

`app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`

Controls:

- Evaluation installer:
  - current installer;
  - private for this run;
  - Shizuku for this run.
- Batch count:
  - 10;
  - 25;
  - 50;
  - 100 with warning.
- Skip already evaluated.
- Re-evaluate stale.
- Include explicit candidates for audit, off by default.
- Wi-Fi-only if easy; otherwise defer.
- Start.
- Pause/cancel if implemented.

Progress:

- current extension;
- current source;
- phase:
  - downloading;
  - installing;
  - loading;
  - probing popular;
  - probing latest;
  - probing search;
  - scoring;
  - cleanup;
- completed count;
- failed count;
- skipped count.

Results:

- strong fit;
- worth trying;
- explicit-heavy;
- ecchi-heavy;
- weak/poor search;
- errors.

Source actions:

- like source;
- dislike source for recommendations;
- reject source entirely;
- install extension;
- reset evaluation.

### Phase 9: Integration With Sources To Try

Modify:

`app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt`

and/or

`app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt`

Integration rules:

- If source has an evaluation record:
  - use `recommendation_fit_score`, `quality_score`, `search_reliability_score`, and verdict to rank it;
  - allow scores above `0.70` only for evaluated positive evidence;
  - hide `rejected`, `poor_search` if user chooses, and `explicit_heavy` when explicit block enabled;
  - show reasons such as `Evaluated: strong fit`, `Search worked`, `Matched preferred tags`, `Explicit-heavy`.
- If no evaluation record:
  - keep current metadata-only behavior;
  - show `Needs testing`.

Do not remove existing user like/dislike behavior.

### Phase 10: Integration With Explicit Blocking

Current explicit filtering call sites:

- `app/src/main/java/eu/kanade/domain/extension/interactor/GetExtensionsByType.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt`

First implementation:

- Keep current name/package/source ID classifier in these fast paths.
- For Sources To Try, combine classifier + stored evaluation verdict.
- For Browse > Sources installed source list, only use stored evaluation if already available and cheap to query in that path.

Recommended first integration:

- Sources To Try hides evaluated `explicit_heavy` when explicit block is enabled.
- For You excludes evaluated `explicit_heavy` sources when explicit block is enabled.
- Browse > Extensions available continues to use name/package classifier until a performant evaluation lookup design is added.

Do not block ecchi-heavy with the porn/hentai setting.

### Phase 11: Cleanup Contract

Preferred cleanup for temporary evaluation:

```kotlin
ExtensionLoader.uninstallPrivateExtension(context, pkgName)
ExtensionInstallReceiver.notifyRemoved(context, pkgName)
```

Only safe when the evaluation installed privately and there is no shared/system install with the same package name that should remain.

If installed through system/Shizuku:

- current `ExtensionInstaller.uninstallApk(pkgName)` starts Android uninstall UI;
- do not assume silent uninstall exists;
- warn user before running large batches;
- consider deferring Shizuku/system batch evaluation until Shizuku uninstall support is implemented.

Add cleanup result to evaluation state:

- `cleanup_success`
- `cleanup_prompted`
- `cleanup_failed`
- `cleanup_skipped_already_installed`

If cleanup fails:

- stop large batch by default;
- show clear message;
- do not continue installing many trial extensions.

### Phase 12: Failure Handling

Every extension candidate must end with a record/state:

- skipped already installed;
- skipped language;
- skipped explicit heuristic;
- install failed;
- load failed;
- no catalogue sources;
- evaluated;
- cleanup failed;
- cancelled.

One source failure should not fail the whole extension.

One extension failure should not fail the whole batch unless:

- installer unavailable;
- repeated cleanup failure;
- Shizuku permission revoked;
- storage/cache write failure.

### Phase 13: Tests

Add pure/unit tests where possible:

1. `SourceEvaluationInstallerPolicyTest`
   - private allows larger batches;
   - Shizuku requires availability/permission;
   - package/legacy warns and limits;
   - global preference is not required for override policy.

2. `SourceEvaluationScorerTest`
   - preferred tag/title matches increase fit;
   - blocked tags reduce fit;
   - explicit-heavy and ecchi-heavy remain separate;
   - repeated errors produce poor_search/error verdict;
   - evaluated positive scores can exceed 0.70.

3. `SourceEvaluationRepositoryTest` if local DB test pattern exists.

4. `NonInstalledSourceSuggestionScorerTest` updates:
   - evaluated strong fit outranks metadata-only suggestions;
   - evaluated explicit-heavy is hidden when explicit block enabled;
   - unevaluated metadata behavior remains unchanged.

Android/integration tests may be difficult. If so, document manual QA thoroughly.

Manual QA:

- Start evaluation with Private installer for 10 candidates.
- Confirm only one extension is processed at a time.
- Confirm temp extension disappears after cleanup.
- Confirm source evaluation records show in UI.
- Confirm Sources To Try changes ranking based on evaluated evidence.
- Confirm explicit-heavy evaluated source hides when block setting enabled.
- Confirm ecchi-heavy is not hidden by porn/hentai block.
- Confirm cancel does not leave UI stuck.
- Confirm normal global search, normal extension install, update all, selective install, selective uninstall still work.

## What Not To Implement In First Pass

Do not implement yet:

- 500/1000 evaluation runs;
- full pause/resume across app restarts;
- full local catalogue database for all sampled manga;
- aggressive duplicate or alias learning from evaluated sources;
- source-specific website scrapers;
- silent Shizuku uninstall unless a safe existing path is confirmed or built deliberately;
- backup/restore of evaluation cache.

These can be future v0.6.x items after the first version proves stable.

## Documentation Requirements For Claude

When implementing, Claude must create:

`docs/recommendations/KMK_RECS_V0_6_8_SOURCE_EVALUATION_IMPLEMENTATION.md`

It must also update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `RECOMMENDATION_VERSIONING.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

The implementation report must include:

- exact files changed;
- exact database schema added;
- installer override approach used;
- whether Private evaluation install works;
- whether Shizuku cleanup is automatic or prompt-based;
- evaluation limits used;
- scoring/verdict rules;
- what was deferred;
- test commands run;
- APK name produced.

## Acceptance Criteria

This feature is acceptable only if:

- it processes evaluation candidates one extension at a time;
- it does not install large groups in parallel;
- it prefers Private installer for temporary evaluation;
- it does not silently change the user's normal installer preference;
- if global preference switching is used as fallback, it restores the previous setting on success, failure, and cancellation;
- it clearly checks and explains Shizuku availability/permission;
- it stores source evaluation records in SQLDelight;
- it separates explicit-heavy and ecchi-heavy;
- it integrates evaluated evidence into Sources To Try;
- it uses evaluated explicit-heavy evidence where safe;
- it cleans up trial extensions safely;
- failures are isolated and documented;
- normal extension install/update/uninstall/global search behavior remains unchanged;
- documentation and release notes are updated under `KMK-Recs v0.6.8`.

## Recommendation

This feature is feasible, but only as a staged, bounded evaluator.

Best first implementation:

1. Add SQLDelight source evaluation storage.
2. Add temporary installer override.
3. Build a nested Source Evaluation screen.
4. Evaluate 10/25/50 candidates sequentially using Private installer by default.
5. Store source-level verdicts from tiny popular/latest/search probes.
6. Use evaluated records to improve Sources To Try and explicit-heavy filtering.

Do not attempt a universal 500/1000-source auditor in the first release.


