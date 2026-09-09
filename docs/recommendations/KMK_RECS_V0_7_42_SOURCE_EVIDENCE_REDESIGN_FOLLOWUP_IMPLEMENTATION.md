# KMK-Recs v0.7.42 Source Evidence Redesign Follow-Up Implementation

Date: 2026-07-12

Status: COMPLETE and verified.

Version family: KMK-Recs `v0.7.42-fix1` â€” a **corrective follow-up to v0.7.42**, not a new feature
phase. Per `RECOMMENDATION_VERSIONING.md`'s Canonical Versioning Rule: `KmkRecsReleaseNotes.VERSION_CODE
= 743`, `VERSION_NAME = "KMK-Recs v0.7.42-fix1"`, APK `Komikku-v1.13.6-kmk.7.42.1-debug.apk`.

Plan: `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_PLAN.md`.

## Background

Codex review after the v0.7.42 Source Evidence Redesign found that the core scorer/schema/tests were
build-healthy, but several downstream Source Evaluation UI/queue consumers still used pre-v0.7.42
assumptions (hardcoded `STRONG_FIT`/`WORTH_TRYING` gates, no fit-staleness check, a stale `search 0%`
subtitle segment). This is a corrective pass, not a redesign.

## Findings confirmed and fixed

All five findings named in the plan were re-verified against the actual current code before editing,
exactly as the code existed at the start of this session â€” none were already fixed.

### F1 â€” Manual queue used the old hardcoded "promising" rule

Confirmed: `SourceRecommendationQualityQueue.compute()` filtered on a hardcoded
`PROMISING_VERDICTS = {STRONG_FIT, WORTH_TRYING}` set, ignoring `SourceRecommendationFitEligibility`
entirely (that policy existed since v0.7.42 but was never adopted here).

**Fixed:** `SourceRecommendationFitEligibility` gained `isProbeEligible(evaluation): Boolean`
(`check(evaluation) == ELIGIBLE`). The queue now partitions evaluations with
`evaluations.partition { SourceRecommendationFitEligibility.isProbeEligible(it) }` instead of the
hardcoded set. A WEAK/NEUTRAL catalogue verdict with LOW/UNKNOWN metadata confidence and enough
samples is now eligible, matching the automatic evaluation path exactly.

### F2 â€” Stale fit rows were not treated as missing

Confirmed: the queue only checked `fitsByEvalKey[it.evaluationKey] == null`, never inspecting
`SourceRecommendationFit.evaluationVersion`/`expiresAt` (both added in v0.7.42 but never read
anywhere in production code before this fix).

**Fixed:** `SourceRecommendationFitEligibility.isFitCurrent(fit, now = System.currentTimeMillis())`
returns `false` for a missing fit, a fit with `evaluationVersion < SourceRecommendationFit
.CURRENT_VERSION`, or an expired fit (`expiresAt != null && expiresAt <= now`). The queue's
`compute()` gained an injectable `now: Long = System.currentTimeMillis()` parameter and now checks
`isFitCurrent(...)` rather than mere presence.

### F3 â€” Diagnostics duplicated the old promising-source rule

Confirmed: `SourceRecommendationQualityDiagnostics.compute()` independently re-derived
`STRONG_FIT`/`WORTH_TRYING` filtering and counted any present fit as "checked" regardless of staleness
â€” a second, separately-maintained copy of the same bug as F1/F2.

**Fixed:** Diagnostics now calls the same two `SourceRecommendationFitEligibility` functions the queue
uses. `checkedCount`/`notCheckedCount` are derived from `isProbeEligible` + `isFitCurrent`; the
per-evaluation outcome loop skips (`continue`s past) any fit that is not current, so a stale fit no
longer contributes to `goodCount`/`weakCount`/`noResultsCount`/`installLoadIssueCount`/`searchErrorCount`
â€” it only contributes to `notCheckedCount`. `compute()` gained the same `now` parameter as the queue.

### F4 â€” Source Evaluation row subtitle still showed misleading `search 0%`

Confirmed: `EvaluationResultRow` in `SourceEvaluationScreen.kt` built
`"$displayExt â€¢ ${lang.uppercase()} â€¢ fit $fit% â€¢ search $search% â€¢ $evidenceStr â€¢ $lastEvalStr"` using
`evaluation.searchReliabilityScore`, which v0.7.42 made permanently `0.0` (search is measured
separately by `SourceRecommendationFit`).

**Fixed:** The `search $search%` segment and its backing `searchReliabilityScore` read were removed.
The row now shows catalogue metadata confidence instead, via a new KMR-templated subtitle:

```text
{extension} â€¢ {lang} â€¢ catalogue fit {fit}% â€¢ metadata {confidence} â€¢ {evidence} â€¢ {last evaluated}
```

New KMR strings (`i18n-kmk/src/commonMain/moko-resources/base/strings.xml`):
`source_evaluation_metadata_confidence_high/moderate/low/unknown` and
`source_evaluation_catalogue_row_subtitle` (a 6-placeholder template string, so the whole line â€”
including the words "catalogue fit" and "metadata" â€” is translatable, not just the individual labels
interpolated into a hardcoded Kotlin string as the old code did). The separate `For You search:
Good/Great/...` second line (already sourced from `SourceRecommendationFit`, already correctly labeled
as of v0.7.42) is untouched.

### F5 â€” v0.7.41 documentation contradicted v0.7.42's shipped state

Confirmed: `KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md` had three places stating
v0.7.42 "is NOT implemented and remains planning scope" / "is intentionally NOT started" / "remains
planning-only" â€” true when written, stale after v0.7.42 shipped.

**Fixed:** All three occurrences were reworded to state the historical fact ("at the time of v0.7.41,
v0.7.42 had not been started") plus a forward pointer to the actual implementation reports, without
rewriting or deleting any of the v0.7.41 report's own content.

## Shared eligibility/staleness contract (plan Step 2)

Implemented as **design option 1** from the plan (extend `SourceRecommendationFitEligibility`), not a
new standalone object â€” this keeps one authority for "is this source eligible for a search-compatibility
probe" rather than introducing a second parallel policy object, consistent with prior sessions'
direction to avoid duplicate policy systems.

```kotlin
object SourceRecommendationFitEligibility {
    fun check(evaluation: SourceEvaluation): EligibilityResult = /* unchanged from v0.7.42 */

    fun isProbeEligible(evaluation: SourceEvaluation): Boolean =
        check(evaluation) == EligibilityResult.ELIGIBLE

    fun isFitCurrent(fit: SourceRecommendationFit?, now: Long = System.currentTimeMillis()): Boolean {
        if (fit == null || fit.evaluationVersion < SourceRecommendationFit.CURRENT_VERSION) return false
        val expiresAt = fit.expiresAt
        return expiresAt == null || expiresAt > now
    }
}
```

Both `SourceRecommendationQualityQueue.compute()` and `SourceRecommendationQualityDiagnostics.compute()`
call these two functions exclusively â€” no independent re-derivation remains in either file.

**Note on `isFitCurrent` implementation:** the plan's suggested one-liner
(`fit.expiresAt == null || fit.expiresAt > now`) does not compile as written â€” `expiresAt` is a
cross-module public property (declared in `domain`, read from `app`), and Kotlin cannot smart-cast a
cross-module property after a `fit != null` check on a separate boolean expression. This was caught by
the actual `:app:compileDebugKotlin` build (not by manual review) and fixed by capturing `fit.expiresAt`
into a local `val` before the null check, which Kotlin can smart-cast safely.

## Files changed

Production:
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt` â€” added
  `isProbeEligible()`, `isFitCurrent()`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt` â€” F1 + F2, KDoc updated
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityDiagnostics.kt` â€” F3
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” F4 (row subtitle)
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 5 new KMR strings for F4
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” VERSION_CODE 743, VERSION_NAME
  "KMK-Recs v0.7.42-fix1", changelog entry

No `SourceEvaluationScreenModel.kt` change was required â€” both call sites
(`SourceRecommendationQualityQueue.compute(s.evaluations, s.recommendationFitsByEvalKey)` and
`SourceRecommendationQualityDiagnostics.compute(state.value.evaluations, fitsByKey)`) use the new
`now` parameter's default value, so no call-site edit was needed for F1â€“F3.

Documentation:
- `docs/recommendations/KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md` â€” F5
- `RECOMMENDATION_VERSIONING.md` â€” new `### KMK-Recs v0.7.42` entry (backfilled, was missing from the
  version trail) and `### KMK-Recs v0.7.42-fix1` entry
- `docs/recommendations/CURRENT_STATE.md`, `NEXT_WORK.md`, `README.md` â€” see below

Tests:
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityQueueTest.kt` (rewritten â€” 17
  tests: 8 original behaviors preserved with an explicit `HIGH`-confidence default so their original
  "excluded" semantics are unchanged, +9 new: confidence fail-open eligibility, unconditional
  blocked-verdict exclusion, stale-version fit, expired fit, boundary-exact expiry, not-yet-expired
  fit, current fit)
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationQualityDiagnosticsTest.kt` (rewritten â€”
  16 tests: 11 original behaviors preserved the same way, +5 new: low-confidence eligible row matches
  the queue, confidently-excluded row matches the queue, stale-version fit not scored, expired fit not
  scored, EXPLICIT_HEAVY excluded regardless of confidence)

**No database migration was added** â€” F1â€“F5 required no schema change; `evaluation_version`/`expires_at`
already exist on `source_recommendation_fit` (migration 60, shipped in v0.7.42).

## Why existing tests needed a confidence default fix

`SourceEvaluation.catalogueMetadataConfidence` defaults to `UNKNOWN` (added in v0.7.42). Before this
fix, `UNKNOWN` is in `SourceRecommendationFitEligibility`'s fail-open `INCONCLUSIVE_CONFIDENCE` set, so
every pre-existing test fixture that didn't explicitly set a confidence (all of them, since the field
didn't exist when those tests were first written) would have silently become "eligible" once the queue
and diagnostics started calling `isProbeEligible`. This would have broken the pre-existing
"non-promising sources are excluded" assertions for `WEAK`/`POOR_SEARCH` in both rewritten test files.
Both `makeEval()` helpers gained a `confidence: SourceEvaluationMetadataConfidence = HIGH` parameter
("confidently evaluated" â€” the same convention already used in `SourceEvaluationScorerTest`/
`SourceRecommendationFitEligibilityTest` from the base v0.7.42 session) so existing test intent is
preserved unless a test is specifically about confidence-based fail-open behavior.

## UI subtitle pure test â€” not added, documented reason

Plan Step 6.3 says to extract a pure helper "only if it follows project style." The F4 row subtitle
requires `stringResource(...)` calls (Compose-only, `@Composable`-scoped) to resolve both the
confidence label and the templated subtitle string â€” there is no pure, non-Compose function left to
extract once the "search %" segment (a plain numeric computation) is replaced by a translated string
lookup. Per the plan's own guidance ("do not add fragile screenshot tests for this pass"), no UI test
was added for this segment. The absence of `search %`/presence of the confidence template was verified
by direct code review of the composable and the new KMR string content (`source_evaluation
_catalogue_row_subtitle` has no `search` placeholder or literal).

## Verification

JDK confirmed before running Gradle: repo-local Temurin **17.0.19+10** at
`C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10`. `java -version` resolved
to 17.0.19; Java 8 was not used.

| Command | Result |
|---|---|
| `:app:testDebugUnitTest --tests "*.SourceRecommendationQualityQueueTest"` | BUILD SUCCESSFUL (17/17 pass; one real compile error was caught and fixed on the first attempt â€” see the `isFitCurrent` note above) |
| `:app:testDebugUnitTest --tests "*.SourceRecommendationQualityDiagnosticsTest"` | BUILD SUCCESSFUL (16/16 pass) |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL (267 actionable tasks; full suite passes) |
| `:app:spotlessApply` | BUILD SUCCESSFUL |
| `:app:spotlessCheck` | BUILD SUCCESSFUL |
| `:app:assembleDebug` | BUILD SUCCESSFUL |

APK built and copied: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.42.1-debug.apk`
(from `app/build/outputs/apk/debug/app-universal-debug.apk`).

## Release identity (must agree everywhere â€” verified)

| Source | Value |
|---|---|
| `KmkRecsReleaseNotes.VERSION_CODE` | `743` |
| `KmkRecsReleaseNotes.VERSION_NAME` | `"KMK-Recs v0.7.42-fix1"` |
| APK filename | `Komikku-v1.13.6-kmk.7.42.1-debug.apk` |
| This implementation report | `v0.7.42-fix1` |
| `CURRENT_STATE.md` Feature Version | `KMK-Recs v0.7.42-fix1` |
| `NEXT_WORK.md` shipped-version summary | `v0.7.42-fix1` |
| `RECOMMENDATION_VERSIONING.md` | `### KMK-Recs v0.7.42-fix1` entry, APK `Komikku-v1.13.6-kmk.7.42.1-debug.apk`, code `743` |

Android `versionCode`/`versionName` in `app/build.gradle.kts` were **not** modified â€” this is a KMK
local release marker only, per the Canonical Versioning Rule.

## Intentionally deferred / out of scope (per plan Â§3 and Â§8 guardrails)

- No Source Evaluation redesign beyond this corrective pass.
- No new database tables or migrations.
- No `SourceEvaluationScorer` threshold changes â€” no test proved a current bug there.
- No changes to the For You recommendation feed retrieval loop.
- No broader filters, refresh-effort modes, source-scope controls, chapter/update checks, or local
  outcome learning â€” unchanged from the v0.7.42 plan's own non-goals.

