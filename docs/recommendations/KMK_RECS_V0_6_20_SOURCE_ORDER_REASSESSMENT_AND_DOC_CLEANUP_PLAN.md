# KMK-Recs v0.6.20: Source Ordering, Reassessment, Diagnostics, and Documentation Cleanup Plan

Date: 2026-06-20

Status: implementation plan, awaiting user approval before coding

Feature version: KMK-Recs v0.6.20

Expected APK name: `Komikku-v1.13.6-kmk.6.20-debug.apk`

## Summary

v0.6.20 should tighten the recommendation/source-evaluation experience after the v0.6.19 background/offline work. The goal is not to add another heavy evaluation system. The goal is to make existing source information easier to trust, easier to maintain, and easier to recover from when the user's tastes change.

This plan covers seven related changes:

1. For You source row ordering: sources with matches first, no-match sources below them, disliked sources last.
2. Manual source reassessment: show a recommendation after about 100 new manga ratings, but keep reassessment user-triggered.
3. Source result explainability: last evaluated, evidence strength, and clearer no-match/error reasons.
4. Preference semantics: keep disliked source, hidden source, quarantine/block, and evaluation cache as separate concepts.
5. Reset/management controls: add a controlled reset section for source decisions.
6. Neutral read/seen marker: let the user remove already-read recommendation manga without treating them as liked or disliked.
7. Documentation cleanup: move superseded docs out of the active folder without deleting history.

## Current Code And Documentation Findings

### Source Status Infrastructure

Relevant files:

- `docs/recommendations/CURRENT_STATE.md`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt`
- `app/src/main/java/exh/recs/sourceprefs/RecommendationSourcePreferenceStore.kt`
- `domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt`
- `domain/src/main/java/tachiyomi/domain/taste/model/TasteProfileConfidence.kt`

Current documented behavior:

- For You stores per-source last-run statuses in `recommendation_last_source_run_statuses`.
- Existing status values include `Shown`, `NoMatches`, `FilteredOut`, `Error`, `OutsideAttemptLimit`, `HiddenByDuplicateHandling`, and disabled/not-checked UI state.
- Source Evaluation stores source/extension verdicts, scores, timestamps, sample counts, and error message fields.
- Source preferences already have liked/disliked key sets.
- Disliked sources are excluded from For You.

### Source Evaluation Status

Relevant files:

- `docs/recommendations/KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_6_19_SOURCE_EVALUATION_UX_AND_BACKGROUND_EXECUTION_FOLLOWUP_IMPLEMENTATION.md`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt`

Current implemented behavior appears to include:

- taste confidence warning;
- Shizuku setup demotion;
- safety diagnostics demotion;
- WorkManager/background Source Evaluation;
- notification deep link follow-up;
- unassessed remaining count follow-up;
- offline start guard follow-up;
- installed-exclusion tests follow-up.

The next pass should build on these rather than duplicate them.

### Documentation State

The recommendation docs folder contains many completed plan files and implementation reports. This history is useful, but the active folder is crowded. The README already separates active, implemented, and historical files, but old plans still sit in the main folder.

Cleanup should make the active folder easier to scan without losing implementation history.

## Goals

1. Sort For You source rows by usefulness state:
   - sources with matches/results first;
   - sources with no matches below matching sources;
   - explicitly disliked sources below no-match sources.
2. Preserve the user's saved source priority order inside each group.
3. Keep this ordering as a display/UI ordering rule, not a source quality scoring rule.
4. Add manual source reassessment from Source Evaluation.
5. Track enough rating-change state to recommend reassessment after about 100 new manga ratings since the last source-evaluation baseline.
6. Let users trigger reassessment at any time from Source Evaluation.
7. Show last evaluated and evidence-strength details for source evaluation results.
8. Make no-match sources explain why they had no usable recommendations when the reason is known.
9. Separate disliked source, hidden source, blocked/quarantined source, and evaluation cache semantics.
10. Add a careful source-decision reset/management section.
11. Add neutral Seen/Already read manga removal, including Seen/Already read other versions through the cross-extension matching workflow.
12. Move superseded planning docs out of the active docs folder into an archive area, preserving history and links.
13. Keep all work under the v0.6 source/recommendation-source workstream.
## Non-Goals

- Do not automatically reassess sources in the background just because the user rated 100 manga.
- Do not make no-match sources count as bad sources.
- Do not automatically undo user dislikes.
- Do not collapse disliked/hidden/blocked/quarantined concepts into one setting.
- Do not delete historical documentation permanently unless the user explicitly approves a final deletion list.
- Do not redesign the For You page.
- Do not add a new extension evaluation crawler.
- Do not change manga recommendation scoring except where display ordering requires already-known source row state.

## Part 1: For You Source Row Ordering

### Required Behavior

For You source rows should sort in this order:

1. `HasMatches`
2. `NoMatches`
3. `Disliked`

Within each group, preserve the user's saved source priority order.

This means:

- A high-priority source that returns recommendations appears above lower-priority sources that also return recommendations.
- A high-priority source with no matches appears below all sources with matches, but above disliked sources.
- Disliked sources sort last if they are shown at all.

### Display Rule, Not Scoring Rule

This must not write a worse evaluation score for no-match sources. No-match can mean:

- the current taste profile is too narrow;
- the source search failed temporarily;
- the source has catalog data but no relevant current results;
- blocked tags or known-manga filtering removed everything;
- network conditions or source behavior caused an empty result.

No-match should affect row placement only.

### Disliked Source Visibility

Current behavior excludes disliked sources from For You. If that remains the implementation, the sort rule only matters in settings/source status lists that include disliked sources.

Recommended behavior:

- For You feed: keep disliked sources hidden by default.
- Recommendation Settings / source status list: show disliked sources at the bottom, clearly labeled `Disliked`.
- Source Evaluation: allow disliked sources to remain excluded unless the user explicitly chooses to reassess/include them.

Claude should inspect the current For You row construction before deciding exactly where the sort applies.

### Suggested Implementation

Add a pure display-order helper, for example:

```kotlin
data class SourceDisplayOrderInput(
    val sourceId: Long,
    val priorityIndex: Int,
    val hasMatches: Boolean,
    val noMatches: Boolean,
    val isDisliked: Boolean,
)
```

Sort key:

```text
stateGroupOrder, priorityIndex, sourceName/sourceId stable tie-breaker
```

Where:

```text
0 = has matches
1 = no matches
2 = disliked
```

Do not rely on source name alphabetical order except as a final deterministic tie-breaker.

### Tests

Add unit tests covering:

- matches sort before no-match;
- no-match sorts before disliked;
- priority order is preserved inside the matches group;
- priority order is preserved inside the no-match group;
- priority order is preserved inside the disliked group;
- empty/error rows do not crash the sorter.

## Part 2: Manual Source Reassessment After Taste Changes

### Product Behavior

Source Evaluation should track whether the user's taste profile has changed enough to justify reassessing sources.

User rule:

- After about **100 new manga ratings** since the last reassessment baseline, show that source reassessment is recommended.
- The user can also press a **Reassess sources** button at any time.
- Reassessment should be manual. Do not start it automatically.

### Why Manual

Source Evaluation is expensive because it may temporarily install, probe, and uninstall extensions. Automatic reassessment after every taste change would waste network, battery, storage churn, and time.

Manual reassessment gives the user control while still surfacing when existing source scores may be stale.

### Suggested State

Add lightweight persisted state, probably in preferences unless there is already a better Source Evaluation state table:

```text
sourceEvaluationLastReassessmentRatingCount
sourceEvaluationLastReassessmentAt
sourceEvaluationDismissedReassessmentPromptAt optional
```

Use current total rated manga count from the local taste database/profile.

Suggested derived fields:

```text
currentRatedMangaCount
ratingsSinceLastReassessment = currentRatedMangaCount - baselineRatingCount
shouldSuggestReassessment = ratingsSinceLastReassessment >= 100
```

### Reassess Button

In Source Evaluation:

- Add `Reassess sources` button.
- It should be available even before 100 new ratings.
- If no reassessment is recommended, show neutral text such as `Reassess using current tastes`.
- If 100+ new ratings exist, show an inline note such as `100+ new ratings since the last source evaluation. Reassessment is recommended.`

### What Reassessment Does

Recommended behavior:

- It should not blindly clear everything without confirmation.
- It should offer an explicit mode:
  - `Evaluate new/unassessed sources only`
  - `Reassess previously evaluated sources too`
- For the first implementation, the button can set `skipAlreadyEvaluated = false` or enable `reEvaluateStale`, then start the normal Source Evaluation flow.
- After the reassessment run completes successfully, update the rating-count baseline.

### Tests

Add tests for:

- fewer than 100 new ratings does not recommend reassessment;
- exactly/over 100 recommends reassessment;
- manual reassess button remains available regardless of count;
- completed reassessment updates the baseline;
- failed/cancelled reassessment does not update the baseline unless explicitly chosen.

## Part 3: Source Explainability

### Last Evaluated

Source Evaluation results should show compact `Last evaluated` information where useful.

Recommended locations:

- Source Evaluation past results rows;
- Source Evaluation detail/expanded row if a compact row is too crowded;
- Recommendation Settings source status list if the source has a matching evaluation record.

Suggested display:

- `Last evaluated today`
- `Last evaluated 6 days ago`
- `Last evaluated 2026-06-20` if relative formatting is unavailable

### Evidence Strength

Show evidence strength separately from score.

Examples:

- `Strong evidence`
- `Moderate evidence`
- `Weak evidence`
- `Low confidence`

Evidence strength should consider already-stored fields where possible:

- `sampleCount`
- `popularCount`
- `latestCount`
- `searchCount`
- `searchSuccessCount`
- taste profile confidence
- number of matching preferred/blocked/disliked tags

Do not overstate confidence when sample count is low or taste evidence is weak.

### No-Match Reasons

When a source has no usable For You recommendations, show the reason if it is known.

Possible reasons:

- `No matching results`
- `Filtered by blocked tags`
- `Filtered as known/rated/read`
- `Hidden by duplicate handling`
- `Outside attempt limit`
- `Source error`
- `Offline during refresh`
- `Language disabled`
- `Explicit source blocked`

Use existing last-run status values where possible. Do not invent reasons the system cannot actually distinguish.

### Tests

Add tests for the pure reason-mapping helper if one is created.

## Part 4: Separate Preference Semantics

### Required Distinctions

Keep these concepts separate:

1. **Disliked source**
   - User actively does not want this source in recommendations.
   - Should be excluded from For You and sorted last in management/status views.
2. **Hidden source**
   - User does not want to see this source right now, but does not necessarily dislike it.
   - Should be reversible from a management screen.
3. **No matches**
   - Source returned no usable recommendations in the last run.
   - Not a negative preference.
4. **Quarantined/blocked source or package**
   - Safety/crash/explicit/system protection.
   - Must remain separate from taste preferences.
5. **Evaluation cache**
   - Generated analysis data.
   - Can be cleared/rebuilt without changing user likes/dislikes.

### Implementation Guidance

If hidden-source state does not currently exist, do not overload disliked state to mean hidden.

Options:

- Defer hidden-source state but document it.
- Or add a new preference set such as `hiddenRecommendationSourceKeys()`.

If adding hidden sources, key format should mirror `RecommendationSourcePreferenceStore` and be pure/tested.

## Part 5: Reset / Management Controls

### Required Behavior

Add a controlled management/reset section in Source Evaluation or Recommendation Settings.

It should not be a dangerous top-of-screen button. It should be placed in a secondary/advanced area and use confirmations for destructive actions.

Recommended actions:

- Reset disliked sources
- Reset hidden sources, if hidden-source state exists
- Clear source evaluations
- Clear quarantined source diagnostics
- Clear blocked extension diagnostics, with strong warning
- Reset reassessment baseline

### Safety Requirements

- Do not reset source priority order accidentally.
- Do not remove installed extensions.
- Do not clear manga ratings unless the user is in a separate taste/rating management flow.
- Every destructive clear action should have a confirmation dialog.
- Label what each reset affects and what it does not affect.


## Part 6: Neutral Read/Seen Marker For Recommendation Manga

### Problem

Some recommendation results are manga the user has already read or already knows, but does not necessarily like or dislike. Marking those as `Like` or `Dislike` would distort the taste profile. Leaving them unmarked means they can keep reappearing in recommendations.

### Required Behavior

Add a neutral manga-level action such as:

- `Read`
- or `Seen`
- or `Mark as read/seen`

Recommended label: `Seen` or `Already read`.

This should mean:

- remove this manga from For You recommendations when known-manga filtering is enabled;
- do not increase liked tag weights;
- do not increase disliked tag weights;
- do not affect source quality negatively;
- do not add the manga to the library;
- do not mark chapters as read;
- do not imply the user liked or disliked the work.

### Better Model Than Rating

This should not be implemented as a fourth rating beside Love/Like/Dislike if that would feed into the taste model. It should be a separate neutral exclusion state.

Recommended domain name:

```text
KnownMangaOverride
SeenManga
RecommendationSeenManga
```

Possible identity:

```text
source + url
```

Optional display fields:

```text
title
thumbnailUrl
markedAt
```

### UI Placement

Add the action where recommendation result actions already exist:

- For You manga cards / overflow actions;
- Top Picks detail rows/cards;
- manga detail rating/dropdown area if the manga is opened from a recommendation.

Do not put this under source-level preferences. It is about a manga item, not the source.


### Seen Other Versions

Add a neutral cross-extension action equivalent to the existing rating workflow actions:

- `Seen other versions`
- or `Already read other versions`

This should use the same general matching screen pattern as:

- `Love other versions`
- `Like other versions`
- `Dislike other versions`

Required behavior:

- Start from the current manga.
- Search across eligible sources using the existing bounded cross-extension matching workflow.
- Keep the normal global search behavior unchanged and uncapped.
- Apply only the matching-workflow cap/filters.
- Select all candidate matches by default, as with rating-other-versions.
- Let the user deselect wrong matches.
- On confirmation, write neutral Seen/Already read records for the selected source/url identities.
- Do not write Love/Like/Dislike taste rows.
- Do not change tag weights.
- Do not count these as new manga ratings for the 100-rating reassessment threshold.

Recommended implementation:

- Reuse `CrossExtensionMatchScreen` if it is generic enough, or extract a shared selection screen model/action type.
- Add a new action type such as:

```kotlin
sealed interface CrossExtensionMatchAction {
    data class Rate(val rating: MangaRating) : CrossExtensionMatchAction
    data object MarkSeen : CrossExtensionMatchAction
}
```

- Keep the existing rating behavior unchanged.
- Route `MarkSeen` confirmation to the new Seen repository/interactor instead of `SetMangaTasteBatch`.

Tests:

- Seen-other-versions opens the matching workflow without changing normal global search limits.
- Candidates are selected by default.
- Deselected candidates are not marked seen.
- Confirm writes Seen records, not taste rows.
- Seen records hide those versions from For You.

### Filtering

Update known-manga filtering so `Seen` manga are treated like locally known manga:

```text
rated OR in library OR started/read/history OR manually seen
```

If `hideKnownManga` is off, decide whether manually seen entries should still hide. Recommended:

- `Seen` should always hide from For You, because it is an explicit removal action.
- If that is too strong, add a setting later. Do not add extra settings in the first pass unless needed.

### Reset / Management

Add this to the reset/management section:

- `Clear seen recommendation manga`

Use a confirmation dialog.

### Backup / Restore

If the state is stored in SQLDelight and expected to persist long-term, include backup/restore in a later pass unless easy. For v0.6.20, it is acceptable to document backup/restore as deferred if implementation risk is high.

### Tests

Add tests proving:

- marking a manga as Seen excludes it from For You;
- Seen does not alter liked/disliked tag weights;
- Seen does not count toward reassessment rating threshold;
- clearing Seen allows the manga to appear again if it otherwise matches;
- source/url identity works across duplicate titles from different sources;
- Seen-other-versions writes neutral Seen records for selected matches and does not write taste ratings.

## Part 7: Documentation Cleanup

### Problem

The `docs/recommendations` folder contains many completed plans and implementation reports. This is good for audit history, but it makes current planning harder because active and old files sit together.

### Required Behavior

Clean the docs so future agents can quickly tell:

- what is current behavior;
- what is active planning;
- what is implemented history;
- what is archived historical context.

### Recommended Cleanup Strategy

Do not permanently delete history in this pass. Instead:

1. Create archive folders:

```text
docs/recommendations/archive/plans
docs/recommendations/archive/implementations
docs/recommendations/archive/research
```

2. Move superseded implemented plan files into `archive/plans`.
3. Move old implementation reports into `archive/implementations`.
4. Move completed research notes into `archive/research` if they are not active references.
5. Keep these files in the main folder:
   - `README.md`
   - `NEXT_WORK.md`
   - `CURRENT_STATE.md`
   - `DOCUMENTATION_RULES.md`
   - current active plan(s), including this v0.6.20 plan
   - current implementation report(s) for the latest APK
   - any research still actively referenced by NEXT_WORK
6. Update `README.md` with the new archive layout.
7. Update links after moving files.
8. If a moved file is still referenced by a current doc, either update the link or leave a one-line redirect stub at the old path.

### Candidate Files To Archive

Claude should verify references before moving. Likely archive candidates include older implemented plan/report pairs such as:

- v0.4.x For You / Top Picks plans and implementation reports
- v0.5.0-v0.5.1 cross-extension matching implementation reports once current references point to archive
- v0.6.0-v0.6.18 old source/suggestion/evaluation implementation reports
- superseded v0.6.8 detailed/queue/installer plan variants
- superseded non-installed discovery hardening plans

Do not archive `CURRENT_STATE.md`, `NEXT_WORK.md`, `DOCUMENTATION_RULES.md`, or `README.md`.

Do not permanently delete files unless the user separately approves an exact deletion list.

## Suggested Implementation Order

1. Add/verify pure For You/source-status display ordering helper.
2. Wire helper into source status / For You row presentation without changing scoring.
3. Add reassessment baseline state and Source Evaluation UI prompt/button.
4. Add last evaluated/evidence strength/no-match reason presentation.
5. Add or defer hidden-source state explicitly; do not overload disliked.
6. Add neutral Seen/Already read manga marker and wire it into known-manga filtering.
7. Add reset/management controls with confirmations, including clearing Seen recommendation manga.
8. Add tests for sort order, reassessment threshold, reason/evidence helpers, and Seen filtering.
9. Perform documentation cleanup/archiving last, after code behavior is known.
10. Update current docs and versioning.

## Testing Plan

### Unit Tests

Add tests for:

- source display ordering groups: matches, no matches, disliked;
- priority preservation inside each group;
- reassessment threshold under/at/over 100 new ratings;
- reassessment baseline update only after successful reassessment;
- no-match reason mapping;
- evidence-strength mapping from sample/search/taste-confidence data;
- reset preference helpers if new preference sets are added;
- Seen/Already read manga exclusion does not affect taste weights or reassessment rating count;
- Seen-other-versions uses the cross-extension matching workflow without changing normal global search behavior.

### Manual Tests

1. Set source priority order with at least one source that returns matches and one that returns no matches.
2. Refresh For You.
3. Confirm matching sources show above no-match sources.
4. Dislike a source.
5. Confirm disliked source is excluded from For You or appears last only in management/status views.
6. Add enough manga ratings or simulate the threshold in test state.
7. Open Source Evaluation and confirm reassessment recommendation appears at about 100 new ratings.
8. Press Reassess sources and confirm it uses the normal Source Evaluation flow.
9. Confirm last evaluated/evidence strength/no-match reasons display correctly.
10. Mark a recommendation manga as Seen/Already read and confirm it disappears from For You without changing Love/Like/Dislike taste scoring.
11. Use Seen/Already read other versions, deselect one candidate, confirm, and verify only selected versions are hidden from For You.
12. Use reset controls and confirm each action only clears the intended state.
13. Confirm docs index links still work after archive cleanup.

## Documentation Requirements

After implementation, Claude must create:

`docs/recommendations/KMK_RECS_V0_6_20_SOURCE_ORDER_REASSESSMENT_AND_DOC_CLEANUP_IMPLEMENTATION.md`

The implementation report must include:

- exact files changed;
- whether hidden-source state was implemented or explicitly deferred;
- whether Seen/Already read manga state was implemented and whether backup/restore was included or deferred;
- whether Seen/Already read other versions was implemented using the cross-extension matching workflow;
- how reassessment baseline is stored;
- tests run and results;
- APK name/path;
- documentation files moved/archived;
- any links/stubs updated;
- known limitations.

Also update:

- `docs/recommendations/README.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/CURRENT_STATE.md`
- `RECOMMENDATION_VERSIONING.md`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

User-facing What's New should mention only app-facing changes, not internal documentation reorganization.

## Recommendation

Proceed with this pass after approval. The highest-value items are the source row ordering and reassessment prompt/button. They make the system easier to trust without increasing crawler load. The documentation cleanup should happen, but as archiving rather than deletion, because the historical implementation notes are still useful for future debugging.







