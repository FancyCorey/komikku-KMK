# KMK-Recs v0.7.10 Recommendation Quality Strong-Fit Error Fix Implementation

Date: 2026-06-23

Feature version / build label: KMK-Recs v0.7.10 — VERSION_CODE 800

User-approved scope: implement `docs/recommendations/KMK_RECS_V0_7_10_RECOMMENDATION_QUALITY_STRONG_FIT_ERROR_FIX_PLAN.md` strictly as scoped.

## Goal

Fix the Source Evaluation screen's **Recommendation Quality** action for sources already classified as `STRONG_FIT` or `WORTH_TRYING`.

Root cause: `evaluateRecommendationQualityForPromising()` resolved available extensions from:

```kotlin
val availableExtensions = lastCandidatePool.value?.allEligible?.map { it.extension }
    ?: emptyList()
```

`lastCandidatePool` is null when no evaluation batch has been run in the current screen session. This made every non-installed promising source fail at `SourceRecommendationQualityExtensionResolver.resolve()` and write "Extension not found in available sources" as a `RecommendationQualityVerdict.ERROR`.

## Files Changed

### Modified

| File | Change |
| --- | --- |
| `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` | Added `loadAvailableExtensionsForRecQuality()`; replaced `lastCandidatePool` line in `evaluateRecommendationQualityForPromising()`; rewrote `evaluateOneForRecQuality()` to use new resolvers; removed `findSourceInInstalledExt()` |
| `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` | `VERSION_CODE=800`, `VERSION_NAME="KMK-Recs v0.7.10"`, 5 new bullets |

### Created

| File | Purpose |
| --- | --- |
| `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityInstalledResolver.kt` | 4-step installed extension resolver |
| `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualitySourceResolver.kt` | 5-step source-within-extension resolver |
| `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityInstalledResolverTest.kt` | 9 unit tests for installed resolver |
| `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualitySourceResolverTest.kt` | 8 unit tests for source resolver |

## Behavior Changed

### Fix: Available Extensions Source

**Before (v0.7.9 and prior):**

```kotlin
val availableExtensions = lastCandidatePool.value?.allEligible?.map { it.extension }
    ?: emptyList()
```

If the user tapped "Evaluate recommendations" without first running a source evaluation batch in the current session, `lastCandidatePool.value` was null. Every non-installed promising source would fail the `SourceRecommendationQualityExtensionResolver.resolve()` call and become an ERROR.

**After (v0.7.10):**

```kotlin
val availableExtensions = loadAvailableExtensionsForRecQuality()
```

`loadAvailableExtensionsForRecQuality()` reads `extensionManager.availableExtensionsFlow.value` — the same full unfiltered available extension list used by `GetSourceEvaluationCandidates`. This is populated whenever `availableExtensionsFlow` has been subscribed to (which happens at screen open, since `getSourceEvaluationCandidates.subscribe()` runs in `init`). Falls back to `lastCandidatePool.value?.allEligible` only if the flow returns empty.

### New: `SourceRecommendationQualityInstalledResolver`

Replaces the previous single `find { ext.pkgName == ... && ext.signatureHash == ... }` with a 4-step fallback chain:

1. Exact signatureHash + pkgName
2. pkgName only (tolerates signing key rotation)
3. signatureHash + extension name
4. Extension name + lang (unambiguous only)

Returns `Found(Extension.Installed)`, `Ambiguous(matchCount, reason)`, or `NotFound`. The caller chooses the error message at each result type. Ambiguous results are never guessed.

### New: `SourceRecommendationQualitySourceResolver`

Replaces `findSourceInInstalledExt()` (2-step nullable return) with a 5-step typed result:

1. Exact source id
2. Exact source name + evaluation lang
3. Exact source name (unambiguous only)
4. Normalized source name + lang (unambiguous only)
5. Normalized source name (unambiguous only)

Returns `Found(CatalogueSource)`, `Ambiguous(reason)`, or `NotFound`. Tolerates source id drift after extension updates while still refusing to guess when names collide.

### New: Stage-Specific Error Messages

| Stage | Error message |
| --- | --- |
| No available extensions | `Available extension list unavailable` |
| Available extension not found | `Extension not found in available sources` |
| Available extension ambiguous | `Extension match ambiguous: <reason>` |
| Installed extension ambiguous | `Installed extension match ambiguous: <reason>` |
| Install failed | `Install failed or timed out` |
| Installed extension did not load | `Installed extension did not load` |
| Source not found (installed path) | `Source not found in installed extension` |
| Source ambiguous (installed path) | `Source match ambiguous in installed extension: <reason>` |
| Source not found (after temp install) | `Source not found after install` |
| Source ambiguous (after temp install) | `Source match ambiguous after install: <reason>` |

### Preserved: Per-Source Error Isolation

The `for ((index, evaluation) in targets.withIndex())` loop with individual `try/catch` is unchanged. A single source failure does not cancel the rest of the run.

### Preserved: Temporary Install/Cleanup Path

The non-installed path is unchanged:
- `SourceEvaluationInstallerPolicy.effectiveInstallerOverride()` selects the installer.
- `extensionManager.installExtension(availableExt, installerOverride)` installs.
- `withTimeoutOrNull(90_000L)` guards install; `withTimeoutOrNull(20_000L)` guards load.
- `SourceEvaluationCleanupPolicy.cleanupDecision(preExistingInstalled = false, ...)` makes the uninstall decision.
- Cleanup is always called via `finally`.

## Tests Run

| Test class | Count | Result |
| --- | --- | --- |
| `SourceRecommendationQualityInstalledResolverTest` | 9 | PASSED (new) |
| `SourceRecommendationQualitySourceResolverTest` | 8 | PASSED (new) |
| `SourceRecommendationQualityExtensionResolverTest` | 10 | PASSED |
| `SourceRecommendationQualityQueueTest` | 6 | PASSED |
| `SourceRecommendationFitProbeTest` | 10 | PASSED |
| `:app:testDebugUnitTest` (full suite) | — | BUILD SUCCESSFUL |

## APK / Build Output

- Build: `:app:assembleDebug` — BUILD SUCCESSFUL in 56s
- Source APK: `app/build/outputs/apk/debug/app-universal-debug.apk`
- Handoff APK: `C:\Users\USER\Downloads\Komikku\Komikku-v1.13.6-kmk.7.10-debug.apk`
- Installs over v0.7.9 APK (VERSION_CODE 800 > 790)

## Known Limitations

- **PromptRequired cleanup**: Extensions temporarily installed using SHIZUKU or CURRENT installer (which system-installs) cannot be silently uninstalled. `SourceEvaluationCleanupPolicy` returns `PromptRequired` for these; the rec-quality probe logs the skip and leaves the extension installed. This is the same behavior as v0.7.9 and is deferred as before.
- **Re-run on taste profile change**: No automatic trigger when the taste profile updates. The "Re-check all" button requires manual action. Deferred from v0.7.7.
- **Evidence strings i18n**: `source_evaluation_evidence_*` keys in `strings.xml` are still hardcoded English in `SourceEvaluationScreen.kt`. Deferred from v0.7.4.

## Deviations from Approved Plan

- **Part 2 "freshly rebuilt candidate pool" fallback not implemented**: The plan listed a second fallback (rebuild candidates using `GetSourceEvaluationCandidates` before option-level filters) between the `availableExtensionsFlow` read and the `lastCandidatePool` fallback. This was omitted because `availableExtensionsFlow.value` is the same source `GetSourceEvaluationCandidates` uses, making the intermediate fallback redundant. If `availableExtensionsFlow` has data, the resolver already has everything it needs.
- **Part 7 top-level UI transient message not added**: The plan mentioned optionally showing a screen-level snackbar when all targets fail at the same early stage. The per-source error rows are already diagnostic (each shows its specific error message), so no additional UI surface was added.

## Follow-Up Recommendations

See `NEXT_WORK.md` for deferred items (PromptRequired cleanup notification, re-run on profile change, evidence strings i18n).
