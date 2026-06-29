# KMK-Recs v0.7.7 Source Evaluation Follow-Up And Best Version Addendum

Date: 2026-06-22

Status: active addendum for the next implementation pass. Use this alongside `KMK_RECS_V0_7_7_BEST_VERSION_VISUAL_QUALITY_MIGRATION_PLAN.md`.

## Version Clarification

`KMK-Recs v0.7.6` was the Source Evaluation Continuation and Recommendation Quality implementation.

`KMK-Recs v0.7.7` must include the Source Evaluation follow-up fixes discovered during tablet testing. If the scope remains manageable, v0.7.7 may also continue into the Best Version / Visual Quality Migration plan. If scope becomes too large, prioritize the Source Evaluation fixes first.

## Required Fix 1: Show Installed / Hide Installed Toggle

### Observed Problem

In Source Evaluation past results:

- `Show installed` reveals installed evaluation rows.
- After enabling it, there is no clear option to hide installed rows again.
- The user has to leave Source Evaluation and come back for installed rows to be hidden again.

### Expected Behavior

- Installed evaluations are hidden by default.
- When installed rows are hidden, the control should say `Show installed`.
- When installed rows are visible, the same control should say `Hide installed` or behave as a clearly reversible switch.
- Tapping the control again should immediately hide installed rows.
- The screen should not need to be reopened.
- This remains display-only filtering. Do not delete installed evaluation rows.

### Implementation Notes

Audit:

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt
app/src/main/java/exh/recs/evaluation/SourceEvaluationDisplayFilter.kt
```

Likely areas:

- `SourceEvaluationScreenModel.setShowInstalled(show: Boolean)`
- `SourceEvaluationScreenModel.applyDisplayFilter()`
- the chip/row rendering `source_evaluation_show_installed`

Fix requirements:

- Keep `showInstalled` as source of truth.
- Recompute `filteredEvaluations` immediately when toggled.
- Add `source_evaluation_hide_installed` string if needed.
- Make hidden count update immediately.
- If installed extension keys change while the screen is open, recompute safely.

### Tests

Add/update tests:

- `showInstalled=false` hides installed rows.
- `showInstalled=true` shows installed rows.
- toggling true -> false hides installed rows immediately.
- hidden count updates.
- UI label or switch state clearly changes.

## Required Fix 2: Visible Recommendation-Quality Workflow

### Observed Problem

The v0.7.6 plan intended a second-stage recommendation-quality evaluation for only promising sources:

```text
STRONG_FIT
WORTH_TRYING
```

Current implementation appears to:

- run the probe automatically during normal evaluation for eligible sources;
- persist `SourceRecommendationFit`;
- display the result as an inline row label such as `Recommendations: Good`;
- not expose a clear second-stage row/section/action for promising sources;
- not give an obvious way to run recommendation-quality checks later for older promising evaluations.

The user expected a visible second-stage workflow, not only a buried inline label.

### Expected Product Behavior

Source fit and recommendation quality must remain separate:

```text
Source fit: Strong Fit
Recommendations: Good
```

The app should make it clear that:

- `Strong Fit` / `Worth Trying` means the source has content aligned with the user's taste.
- Recommendation quality means the source produces useful For You-like recommendation results.
- A source can be `Strong Fit` while having `Recommendations: Mixed` or `Recommendations: Weak`.

### Required UI

Add a visible section in Source Evaluation, for example:

```text
Recommendation Quality
Promising sources without checks: N
[Evaluate recommendations]
```

or:

```text
Promising sources needing recommendation check
- Source A · Strong Fit · [Check recommendations]
- Source B · Worth Trying · [Check recommendations]
```

Minimum acceptable UX:

- A visible `Recommendation Quality` section.
- Count of promising evaluations without recommendation-quality results.
- Button to evaluate recommendations for missing promising sources.
- Optional button to re-check all promising sources.
- Row labels still show final results:

```text
Recommendations: Great
Recommendations: Good
Recommendations: Mixed
Recommendations: Weak
Recommendations: No matches
Recommendations: Error
Recommendations: Too little evidence
```

### Scope Rules

Do not run recommendation-quality checks for all sources.

Only include:

```text
STRONG_FIT
WORTH_TRYING
```

Exclude:

```text
WEAK
REJECTED
ERROR
EXPLICIT_HEAVY
ECCHI_HEAVY
NEEDS_MANUAL_REVIEW
```

unless future requirements explicitly change this.

### Recommended Implementation

Preferred:

1. Add a helper that identifies promising evaluations without recommendation-quality rows:

```text
SourceRecommendationQualityQueue
```

Inputs:

- all evaluations,
- existing `SourceRecommendationFit` rows,
- installed/available extension metadata if needed.

Outputs:

- missing promising rows,
- already checked promising rows,
- ineligible rows.

2. Add a screen model action:

```kotlin
fun evaluateRecommendationQualityForPromising()
```

or:

```kotlin
fun evaluateRecommendationQualityFor(evaluationKeys: Set<String>)
```

3. Reuse the existing bounded probe pieces:

```text
SourceRecommendationFitEligibility
SourceRecommendationFitProbe
SourceRecommendationFitScorer
UpsertSourceRecommendationFit
```

4. If the source is no longer installed, use the same temporary install / private cleanup behavior used by Source Evaluation where safe.

5. If temporary reinstall for only rec-quality probing is too risky, implement a narrower action that requeues/reassesses promising sources only and documents the limitation.

### Important Behavior

- The recommendation-quality action must not re-evaluate every source.
- It must not make source fit worse.
- It should be bounded by the same safe probe limits from v0.7.6.
- It should show progress/error state.
- If an extension/source cannot be loaded anymore, show a safe error row/state.
- Existing old promising evaluations should be eligible for checking later.

### Tests

Add tests:

- promising rows without fit are counted.
- promising rows with fit are not counted as missing.
- non-promising rows are excluded.
- action targets only missing promising rows.
- re-check-all mode targets promising rows even if a fit exists.
- failed source/extension produces safe error state.
- persisted fit appears in `recommendationFitsByEvalKey`.

## Required Fix 3: Recommendation-Quality Result Visibility

### Observed Problem

Recommendation-quality results exist as a small third line on evaluation rows, but the user did not notice them and expected a separate second-stage area.

### Expected Behavior

- Keep row-level result labels.
- Make the label easier to notice, for example as a compact chip or clearly separated subtitle line.
- Add the visible `Recommendation Quality` section so users know whether checks exist or are missing.
- If a promising row has no recommendation-quality result, either:
  - show it in the new `Recommendation Quality` section, or
  - show a subtle `Recommendations: Not checked` label.

Do not clutter every row with large UI.

## Interaction With Best Version v0.7.7 Plan

The Best Version / Visual Quality Migration plan remains valid, but this addendum should be handled first because it fixes the current installed APK behavior.

Recommended v0.7.7 order:

1. Fix `Show installed` / `Hide installed` toggle.
2. Add visible recommendation-quality workflow for promising sources.
3. Improve recommendation-quality result visibility.
4. Only then proceed to Best Version / Visual Quality Migration if scope remains safe.

## Documentation After Implementation

Claude must create or update the v0.7.7 implementation report to include these follow-up fixes:

```text
docs/recommendations/KMK_RECS_V0_7_7_SOURCE_EVALUATION_FOLLOWUP_AND_BEST_VERSION_IMPLEMENTATION.md
```

Also update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

What's New should mention only user-facing changes:

- installed evaluation rows can now be shown/hidden without reopening Source Evaluation;
- promising sources now have a visible recommendation-quality check workflow;
- recommendation-quality results are easier to see.

Do not mention markdown/documentation cleanup in What's New.
