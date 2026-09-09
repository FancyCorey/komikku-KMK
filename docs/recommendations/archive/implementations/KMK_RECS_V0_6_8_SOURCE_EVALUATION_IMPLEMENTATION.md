# KMK-Recs v0.6.8: Source Evaluation â€” Implementation Notes

## Overview

v0.6.8 adds a bounded one-at-a-time source evaluation system. Non-installed extensions are
temporarily installed, probed (popular, latest, and search pages), scored against the user's
taste profile, and immediately cleaned up. Verdicts are stored in SQLDelight and fed back into
the Sources To Try ranking so evaluated sources receive evidence-based scores above 0.70.

---

## Architecture

### Storage layer (`data/`)

**`data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`**
- Table `source_evaluation` with 35 columns; `evaluation_key TEXT NOT NULL PRIMARY KEY`
- `evaluation_key` = `signatureHash|pkgName|sourceId` (or `signatureHash|pkgName` for no-source exts)
- Indexes on `source_id`, `extension_pkg_name`, `verdict`, `evaluated_at`
- Queries: `getAll`, `getAllAsFlow`, `getByKey`, `getBySourceId`, `getByPackage`, `getRecent`,
  `upsert` (ON CONFLICT DO UPDATE), `deleteByKey`, `deleteByPackage`, `deleteAll`

**`data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt`**
- Implements `SourceEvaluationRepository`
- `sourceEvaluationMapper` maps all 35 DB columns to `SourceEvaluation`
- Int/Boolean stored as Long in SQLite; converted in mapper

### Domain layer (`domain/`)

**`domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt`**
- `SourceEvaluationVerdict` enum: `STRONG_FIT`, `WORTH_TRYING`, `NEUTRAL`, `WEAK`,
  `POOR_SEARCH`, `EXPLICIT_HEAVY`, `ECCHI_HEAVY`, `REJECTED`, `ERROR`, `NEEDS_MANUAL_REVIEW`
- `SourceEvaluation` data class (all probe metrics, scores, verdict, sampled titles/tags)
- `SourceEvaluationKeys.CURRENT_VERSION = 1`; `buildKey()` constructs evaluation key

**`domain/src/main/java/tachiyomi/domain/taste/repository/SourceEvaluationRepository.kt`**
- Interface with `getAll`, `getAllAsFlow`, `getByKey`, `getBySourceId`, `getByPackage`,
  `upsert`, `deleteByKey`, `deleteByPackage`, `deleteAll`

**Interactors:**
- `GetSourceEvaluations`: `awaitAll()`, `subscribeAll()`, `awaitByPackage()`
- `GetSourceEvaluation`: `await(key)`, `awaitBySourceId()`
- `UpsertSourceEvaluation`: `await(evaluation)`
- `DeleteSourceEvaluation`: `awaitByKey()`, `awaitByPackage()`
- `ClearSourceEvaluations`: `await()`

All registered in `KMKDomainModule`.

### Evaluation engine (`app/.../exh/recs/evaluation/`)

**`SourceEvaluationInstallerPolicy`** â€” pure object, no Android deps
- `InstallerMode`: `CURRENT`, `PRIVATE`, `SHIZUKU`
- `validate(...)` returns `PolicyResult` with readiness, max batch size, cleanup silence, message
- PRIVATE: max 100, cleanup silent; SHIZUKU: max 50, cleanup NOT silent; CURRENT: delegates
- `effectiveInstallerOverride()` â†’ `BasePreferences.ExtensionInstaller?` for Option A threading
- `recommendDefaultMode()` â†’ prefers PRIVATE > SHIZUKU > CURRENT

**`SourceEvaluationQueueState`** â€” observable UI state
- `Status`: Idle/Running/Cancelling/Completed/Cancelled/Failed
- `Phase`: Idle/Downloading/Installing/LoadingSources/ProbingPopular/ProbingLatest/ProbingSearch/Scoring/Cleanup
- `EvaluationResult` per source; computed properties for verdict counts

**`SourceEvaluationOptions`** â€” run configuration (installerMode, batchSize, skip flags)

**`EvaluationCandidate`** â€” wrapper for `Extension.Available` + priority rank

**`SourceEvaluationScorer`** â€” pure stateless scorer, no Android deps
- Probes explicit signals in sampled titles/tags (`EXPLICIT_TAG_TERMS`, `ECCHI_TAG_TERMS`)
- Uses `ExplicitSourceClassifier.isExplicitName/isExplicitPackageName` for name bias
- Uses `normalizeTag()` for tag matching against taste profile
- Scores: `qualityScore`, `searchReliabilityScore`, `recommendationFitScore`, `explicitScore`, `ecchiScore`
- Verdict logic: explicit â‰¥ 0.5 â†’ `EXPLICIT_HEAVY`; ecchi â‰¥ 0.5 AND explicit < 0.3 â†’ `ECCHI_HEAVY`;
  fitScore â‰¥ 0.70 AND quality â‰¥ 0.5 AND search â‰¥ 0.4 â†’ `STRONG_FIT`; fit â‰¥ 0.50 AND quality â‰¥ 0.3 â†’ `WORTH_TRYING`
- Post-install scores exceed 0.70 (pre-install metadata scorer caps at 0.69)
- `errorRecord(...)` helper for install/load failures

**`SourceEvaluationRunner`** â€” Kotlin coroutine orchestrator
- `start(candidates, options)` runs sequentially via a `supervisorScope` loop (never parallel)
- Per extension: download (timeout 90s) â†’ install wait â†’ load sources (20s) â†’ probe each source â†’ score â†’ persist â†’ cleanup
- Per source: popular (30s) â†’ latest (30s) â†’ 3 search probes (25s each) â†’ score
- Cleanup via `extensionManager.uninstallExtension()` â€” for private-installed extensions this is
  silent (`ExtensionLoader.uninstallPrivateExtension` + `ExtensionInstallReceiver.notifyRemoved`)
- Installer override: `installerOverride` param threaded through `ExtensionManager.installExtension`
  â†’ `ExtensionInstaller.downloadAndInstall` â†’ `installApk`. Global preference never mutated.

**`SourceEvaluationScreenModel`** â€” Voyager StateScreenModel
- Observes evaluations via `GetSourceEvaluations.subscribeAll()`
- Loads candidates from `GetNonInstalledSourceSuggestions.subscribe()`
- Resolves installer policy (PRIVATE availability, Shizuku package presence)
- `startEvaluation()` creates `SourceEvaluationRunner` and mirrors its state

**`SourceEvaluationScreen`** â€” Voyager Screen
- Options: batch size (10/25/50/100), installer mode, skip-already-evaluated, include-explicit
- Progress card (phase label, extension/source name, progress bar, cancel button)
- Summary card (verdict counts on completion/cancel/fail)
- Past evaluations list with `VerdictBadge` per verdict type

### Installer override (Option A)

`ExtensionManager.installExtension` and `ExtensionInstaller.downloadAndInstall`/`installApk`
all accept `installerOverride: BasePreferences.ExtensionInstaller? = null`. When `null` (default),
the user's global preference is used as before. Evaluation runner passes a non-null override only
during evaluation; normal installs and updates are unchanged.

### Sources To Try integration

**`NonInstalledSourceSuggestion.kt`** â€” four new reason variants inside `// KMK -->` marker:
- `EvaluatedStrongFit`, `EvaluatedWorthTrying`, `EvaluatedExplicitHeavy`, `EvaluatedEcchiHeavy`

**`NonInstalledSourceSuggestionScorer.scoreAndFilter()`** â€” new `evaluations` param
- `REJECTED` verdict â†’ excluded from suggestions
- `EXPLICIT_HEAVY` verdict + blockExplicit on â†’ excluded
- `STRONG_FIT` â†’ score 0.90; `WORTH_TRYING` â†’ 0.75; `EXPLICIT_HEAVY` â†’ 0.10; `ECCHI_HEAVY` â†’ 0.30
- Evaluated weak/neutral/poor-search fall through to metadata-only scoring

**`GetNonInstalledSourceSuggestions.subscribe()`** â€” adds evaluation data as 6th reactive source
- `kotlinx.coroutines.combine` supports max 5 type-safe sources; 6th (evaluations flow) is paired
  with `dislikedPref.changes()` via a nested `combine` to stay within the limit

**`RecommendationsSettingsScreen.kt`** â€” `when` on `NonInstalledSuggestionReason` extended:
- `EvaluatedStrongFit` â†’ `rec_suggestion_reason_evaluated_strong_fit`
- `EvaluatedWorthTrying` â†’ `rec_suggestion_reason_evaluated_worth_trying`
- `EvaluatedExplicitHeavy`, `EvaluatedEcchiHeavy` â†’ `null` (hidden from reason chips)
- "Source Evaluation" entry section added after Sources To Try (header + button + description)

---

## Constraints and Known Limitations

- **Shizuku binder check**: `SourceEvaluationScreenModel` only checks whether Shizuku is installed
  (package presence); `shizukuBinderAlive` is hardcoded `false` because importing the Shizuku API
  would add a dependency. Shizuku mode validation correctly returns UNAVAILABLE when alive=false.
  To fully support Shizuku evaluation mode, add `rikka.shizuku:api` dependency and wire the binder check.
- **System-package cleanup**: When an extension is NOT installed privately, `uninstallExtension`
  starts an Android system-uninstall intent which shows a user-facing dialog. For this reason,
  PRIVATE installer (silent cleanup) is the recommended and default evaluation mode.
- **No 500/1000-source batches**: Batch sizes are capped at 10/25/50/100 per the requirements.
  Large-scale evaluation (500+) is deferred to a future version.
- **Search queries from taste profile**: Uses top-weighted tag names as search terms. If the taste
  profile is empty (new user), no search probes are run; quality score reflects popular/latest only.

---

## Files Changed/Created

| File | Status |
|------|--------|
| `data/.../source_evaluation.sq` | NEW |
| `data/.../SourceEvaluationRepositoryImpl.kt` | NEW |
| `domain/.../SourceEvaluation.kt` | NEW |
| `domain/.../SourceEvaluationRepository.kt` | NEW |
| `domain/.../interactor/GetSourceEvaluations.kt` | NEW |
| `domain/.../interactor/GetSourceEvaluation.kt` | NEW |
| `domain/.../interactor/UpsertSourceEvaluation.kt` | NEW |
| `domain/.../interactor/DeleteSourceEvaluation.kt` | NEW |
| `domain/.../interactor/ClearSourceEvaluations.kt` | NEW |
| `app/.../KMKDomainModule.kt` | MODIFIED |
| `app/.../ExtensionInstaller.kt` | MODIFIED (Option A override) |
| `app/.../ExtensionManager.kt` | MODIFIED (Option A override) |
| `app/.../evaluation/SourceEvaluationInstallerPolicy.kt` | NEW |
| `app/.../evaluation/SourceEvaluationQueueState.kt` | NEW |
| `app/.../evaluation/SourceEvaluationScorer.kt` | NEW |
| `app/.../evaluation/SourceEvaluationRunner.kt` | NEW |
| `app/.../evaluation/SourceEvaluationScreenModel.kt` | NEW |
| `app/.../evaluation/SourceEvaluationScreen.kt` | NEW |
| `app/.../discovery/NonInstalledSourceSuggestion.kt` | MODIFIED (4 new reasons) |
| `app/.../discovery/NonInstalledSourceSuggestionScorer.kt` | MODIFIED (evaluations param) |
| `app/.../discovery/GetNonInstalledSourceSuggestions.kt` | MODIFIED (6th combine source) |
| `app/.../settings/RecommendationsSettingsScreen.kt` | MODIFIED (new section + when branches) |
| `app/.../KmkRecsReleaseNotes.kt` | MODIFIED (v0.6.8) |
| `i18n-kmk/.../base/strings.xml` | MODIFIED (evaluation strings) |

