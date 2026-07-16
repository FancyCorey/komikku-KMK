# KMK-Recs v0.8.1-fix4 â€” Final Cleanup And Source Quality Dislike: Implementation Report

Implements `KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_PLAN.md` exactly, as a
corrective/polish pass finishing the remaining v0.8.1 follow-up work. This is an internal/private
handoff build for development; no app-facing string in this pass uses "private build," "public
build," "internal build," "test build," "personal line," or similar release-channel wording
(verified by grep â€” see "Wording Audit" below). The word "private" remains only where it refers to
the pre-existing "Private (recommended)" extension installer mode.

## Files Changed

**New:**
- `app/src/main/java/exh/recs/sourceprefs/SourceQualityMarkPolicy.kt` â€” pure policy (`markPoor`,
  `markExplicit`, `clear`, `clearAll`, `isPoor`, `isExplicit`) for the source/library-quality axis,
  built on the existing `RecommendationSourcePreferenceStore` key format and mutation helpers
  (`like`/`dislike`/`reset`) rather than a duplicate serializer.
- `docs/recommendations/KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_IMPLEMENTATION.md`
  (this file).

**Backend / data:**
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` â€” three new preference
  keys: `likedSourceQualityKeys()`, `dislikedSourceQualityKeys()`, `explicitSourceQualityKeys()`
  (the last is a labeling subset of the second â€” both are members of `dislikedSourceQualityKeys()`;
  `explicitSourceQualityKeys()` marks which of those were flagged specifically "too explicit" rather
  than generically "poor"). No database migration â€” purely preference-backed, per the plan's
  explicit preference for this over a migration.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt` â€” `buildPool()` gained
  a `qualityDislikedKeys` parameter and `CandidatePoolResult.sourceQualityHiddenCount`, filtered and
  counted separately from the existing `dislikedKeys`/`dislikedHiddenCount` (recommendation axis).
- `app/src/main/java/exh/recs/evaluation/GetSourceEvaluationCandidates.kt` â€” reads
  `dislikedSourceQualityKeys()`, reactive via `.changes()`, passed into `buildPool()`.
- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt` â€” `scoreAndFilter()`
  gained a `qualityDislikedKeys` parameter, checked independently of `dislikedKeys` for both
  extension-level and source-level candidates. Hides regardless of `blockExplicit`, since a manual
  source-quality mark is an explicit per-source user judgement, not a heuristic.
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt` â€” subscribes to
  `dislikedSourceQualityKeys()` (added to the existing 5-flow `combine` budget by folding it into
  the disliked+evaluations pairing, now a `Triple`), passes `qualityDislikedKeys` through.
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` /
  `app/src/main/java/exh/recs/RecommendsScreenModel.kt` â€” `effectiveDisabledIds` now also excludes
  installed sources present in `dislikedSourceQualityKeys()` (via
  `RecommendationSourcePreferenceStore.installedSourceIds()`), alongside the existing
  recommendation-dislike exclusion. A source marked poor/too-explicit as a whole no longer feeds For
  You or grouped recommendations once installed.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationDisplayFilter.kt` â€” `filter()` gained
  `qualityDislikedExtensionKeys`/`showSourceQualityDisliked` parameters (defaulted, backward
  compatible) and `FilterResult.hiddenSourceQualityCount`. Past-evaluation rows for a
  since-marked source are hidden by default, never deleted, recoverable via the new toggle.

**ScreenModels:**
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` â€” new `State` fields
  `qualityDislikedSourceKeys`/`qualityExplicitSourceKeys`; new actions
  `markInstalledSourceQualityPoor/Explicit`, `clearInstalledSourceQualityMark`,
  `markAvailableSourceQualityPoor/Explicit`, `clearAvailableSourceQualityMark`,
  `clearAllSourceQualityMarks` (bulk recovery action).
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt` â€” new `State` fields
  `qualityDislikedSourceKeys`/`qualityExplicitSourceKeys`/`showSourceQualityDisliked`/
  `hiddenSourceQualityCount`; new actions `markSourceQualityPoor`, `markSourceQualityExplicit`,
  `clearSourceQualityMark`, `setShowSourceQualityDisliked`. Also: state-derived stale-queue
  completion feedback (see below).

**UI:**
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt` â€” compact overflow menu
  (â‹® icon, `DropdownMenu`) on each Sources To Try suggestion row: "Mark source as poor" / "Mark
  source as too explicit" / "Clear source mark" (shown only when already marked). New "Clear source
  quality marks" management action, shown only when at least one mark exists.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`:
  - Same compact overflow menu added to each past-evaluation row, plus a small "Source disliked" /
    "Marked too explicit" badge when a row is currently marked.
  - New "Show disliked sources" / "Hide disliked sources" `FilterChip` + hidden-count text, mirroring
    the existing "Show installed" pattern exactly.
  - New state-derived completion card: once the stale/outdated reassessment queue empties
    (`staleCandidates.isEmpty()`) after having actually run at least once
    (`continuationCursorStale != null`), shows "Outdated reassessment complete" via `InfoCard`. It
    disappears naturally the moment new stale/outdated work appears (the condition re-evaluates on
    every state update â€” no separate dismiss/suppress flag needed).
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 14 new strings (source-quality axis:
  `source_quality_prefer_for_you`, `avoid_for_you`, `clear_for_you_preference`, `mark_poor`,
  `mark_explicit`, `clear_mark`, `badge_poor`, `badge_explicit`, `show_disliked_sources`,
  `hide_disliked_sources`, `clear_all_marks`, `avoided_for_you_count`, `hidden_mark_count`; stale
  completion: `source_evaluation_stale_reassess_complete`).

**Wording/encoding cleanup:**
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” removed "(private corrective follow-up)"
  from the v0.8.1-fix1/fix2 headers, "(private feature build)" from v0.8.1-fix2's v0.8.0 entry,
  "public/community testing" from the v0.7.45 entry, "Public test documentation" from v0.7.46, "New
  public test build line clarity" from v0.7.45's OCR entry. `VERSION_CODE` 753â†’754, `VERSION_NAME`
  "KMK-Recs v0.8.1-fix3"â†’"KMK-Recs v0.8.1-fix4", new changelog entry (no build-channel wording).
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” `kmk_recs_updated_body` no longer says
  "the private feature changes"; now "the latest changes". Normalized two malformed
  `<!-- KMK --> vX.Y: ... -->` comments (the leading `<!-- KMK -->` closed the comment early, leaving
  the version note as bare unescaped text) to valid single `<!-- KMK vX.Y: ... -->` comments â€” this
  was also load-bearing: one of the newly-added comments in this pass initially contained a literal
  `--` inside its body, which is invalid XML and broke `i18n-kmk:generateMRcommonMain`; fixed during
  verification.

## How The Two Source-Feedback Axes Are Stored

Both axes reuse the exact same key format (`i|<sourceId>` / `a|<signatureHash>|<pkgName>[|<sourceId>]`)
and the exact same `RecommendationSourcePreferenceStore` parse/serialize/like/dislike/reset
functions â€” no duplicate serializer was introduced, per the plan's explicit instruction.

- **Recommendation-behavior axis** (existing, unchanged): `likedRecommendationSourceKeys()` /
  `dislikedRecommendationSourceKeys()`. Answers "do I want this source's For You/recommendation
  rows?"
- **Source/library-quality axis** (new): `likedSourceQualityKeys()` / `dislikedSourceQualityKeys()`
  / `explicitSourceQualityKeys()`. Answers "is this source itself worth showing/suggesting/
  evaluating?" `SourceQualityMarkPolicy` wraps the three sets as one `State(liked, disliked,
  explicit)` value and provides `markPoor`/`markExplicit`/`clear`/`clearAll` as pure functions â€”
  `markExplicit` is `dislike()` plus adding to the `explicit` set; `markPoor` is `dislike()` plus
  removing from `explicit` (so switching a mark from poorâ†’explicit or back never duplicates the
  underlying dislike entry). `clear()` is `reset()` on the liked/disliked pair plus removing from
  `explicit`.

The two axes are stored under entirely separate preference keys and never read each other's values
â€” confirmed by test (`recommendation dislike and source-quality dislike are independent axes`,
`marking source-quality poor does not affect recommendation liked set`, etc. in
`RecommendationSourcePreferenceStoreTest.kt`).

## How Source-Quality Dislike Affects For You, Sources To Try, And Source Evaluation

- **For You / grouped recommendations**: `BrowsePersonalRecommendationsScreenModel` and
  `RecommendsScreenModel` fold `dislikedSourceQualityKeys()`'s installed-source IDs into
  `effectiveDisabledIds` alongside the existing recommendation-dislike installed IDs. A source
  marked poor/too-explicit stops appearing in For You/group recommendation rows the moment it is
  marked, with no separate "avoid in For You" step needed â€” a whole-source quality judgement is
  treated as strictly stronger than a recommendation-only dislike, per the plan's stated reasoning.
- **Sources To Try**: `NonInstalledSourceSuggestionScorer.scoreAndFilter()` excludes a candidate
  whose key is in `qualityDislikedKeys`, checked at both the extension-level and per-source loop,
  independent of `dislikedKeys` and independent of `blockExplicit` â€” a manually-marked source stays
  hidden even when the user has the global "block explicit porn/hentai sources" toggle off, since
  the mark is an explicit per-source judgement, not the heuristic `ExplicitSourceClassifier` check.
- **Source Evaluation candidate pool**: `SourceEvaluationCandidateFilter.buildPool()` excludes a
  quality-disliked extension before it can ever become an `EvaluationCandidate`, counted separately
  in `CandidatePoolResult.sourceQualityHiddenCount` (distinct from `dislikedHiddenCount`,
  `explicitHiddenCount`/`unsafeHiddenCount`, and now surfaced as a distinct diagnostic rather than
  collapsed into "already evaluated, hidden").
- **Past Source Evaluation results**: not deleted. `SourceEvaluationDisplayFilter.filter()` hides
  rows whose extension key is quality-disliked by default (`showSourceQualityDisliked = false`); the
  new "Show disliked sources" toggle reveals them again, mirroring "Show installed" exactly.

## How Poor/Lewd/Explicit Source Marks Are Represented

A single boolean-esque three-state model per source key, stored as membership in two sets:
- **Neutral** (default): key in neither `disliked` nor `explicit`.
- **Poor**: key in `disliked`, not in `explicit`. Row shows a "Source disliked" badge.
- **Too explicit**: key in `disliked` AND `explicit`. Row shows a "Marked too explicit" badge.

There is no separate "hentai/porn-heavy" or "misleading" enum value â€” the plan's examples (bad
library, misleading, too lewd, hentai/porn-heavy) are all *reasons* a user might choose "poor" or
"too explicit," not distinct stored states; a third state would fragment the filtering logic for no
behavioral benefit, since both "poor" and "too explicit" hide identically everywhere. "Too explicit"
exists as a separate flag purely so the badge/label can be more specific than a generic "disliked"
when the user's actual reason was explicitness â€” filtering behavior between the two is identical.

## Stale/Outdated Reassessment Completion Feedback

`SourceEvaluationScreen.kt` now renders an `InfoCard` reading "Outdated reassessment complete" when
`state.staleCandidates.isEmpty() && state.continuationCursorStale != null && !state.queueState
.isRunning`. The `continuationCursorStale != null` guard is what makes this state-derived rather than
a permanent banner: a fresh install or a queue that has never had stale rows shows nothing; the
message appears only after the stale queue has actually been run at least once and is now fully
drained, and disappears again automatically the instant `applyOptionsAndUpdateState()` recomputes a
non-empty `staleCandidates` list (e.g., after the taste profile changes or an evaluation expires).

## Tests

New tests (all passing):
- `SourceQualityMarkPolicy` / axis-independence tests appended to
  `RecommendationSourcePreferenceStoreTest.kt` (9 new tests): markPoor/markExplicit set the right
  flags, switching poorâ†’explicit doesn't duplicate the dislike entry, clearing doesn't touch the
  recommendation axis, liking a key clears any quality mark for that key, `clearAll` resets all
  three sets, and the two axes are independent.
- `NonInstalledSourceSuggestionScorerTest.kt` (+3): source-quality-disliked source excluded;
  excluded even when `blockExplicit = false`; recommendation-disliked and source-quality-disliked
  sources are independently excluded (neither axis suppresses the other's effect).
- `SourceEvaluationCandidateFilterTest.kt` (+3): source-quality-disliked extension excluded and
  counted in `sourceQualityHiddenCount` (not `dislikedHiddenCount`); the two hidden counts tracked
  independently on the same pool; `sourceQualityHiddenCount` is zero with no quality dislikes.
- `SourceEvaluationCandidateQueuePolicyTest.kt` (+1): unassessed queue exhausted (both extensions
  already evaluated) does not prevent the stale queue from being actionable â€” the exact fix3/fix4
  regression scenario, at the pure-function level the ScreenModel's gating logic reads from.
- `SourceEvaluationContinuationPolicyTest.kt` (+2): stale queue reports zero remaining and
  `canContinue = false` once every candidate is completed (completion-state precondition); a failed
  candidate advances the stale cursor identically to a successful one.

## Verification Commands And Results

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat :app:testDebugUnitTest --tests "*SourceEvaluation*"
.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationSourcePreferenceStoreTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*NonInstalledSourceSuggestionScorerTest"
# BUILD SUCCESSFUL â€” all classes passed, including every new test listed above.

.\gradlew.bat spotlessCheck
# Initially FAILED on two files (single-expression onClick lambdas containing two statements,
# reformatted by spotless's ktlint step into multi-line form). Ran `spotlessApply` (mechanical
# formatting only, no logic change) and re-ran spotlessCheck: BUILD SUCCESSFUL.

.\gradlew.bat assembleDebug
# BUILD SUCCESSFUL.
```

```powershell
rg -n "private corrective|private feature|public/community|public test|test build|private feature changes" app\src\main\java i18n-kmk\src\commonMain\moko-resources\base\strings.xml
```
Result: **no matches** (previously matched `KmkRecsReleaseNotes.kt` in 5 places and
`kmk_recs_updated_body` in `strings.xml`; all fixed in this pass). One pre-existing, unrelated
occurrence of the substring "private-only" remains in a code comment in
`SourceEvaluationCleanupPolicy.kt` describing the Private *installer mode* (`context
.isPackageInstalled(pkgName)` semantics) â€” not app-facing text and not build-channel wording, so it
was left as-is per the plan's carve-out for legitimate installer-mode wording.

## Known Limitations / Follow-Ups

- The source-quality overflow menu is per-row only; there is no bulk "mark all visible as poor"
  action (not requested by the plan).
- `SourceEvaluationScreenModel`'s new quality-mark actions call `applyOptionsAndUpdateState()`
  immediately after writing preferences, which is redundant with (but harmless alongside) the
  reactive `GetSourceEvaluationCandidates` pool subscription that will also recompute shortly after
  the preference write lands â€” kept for immediate UI feedback rather than waiting on the flow.
- Mojibake cleanup: grepped `docs/recommendations/KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_
  CONTINUATION_FIX_IMPLEMENTATION.md` and other touched docs for `Ã¢â‚¬â€`/`Ã¢â€ â€™`/`Ãƒâ€”`/`Ã¢â‚¬â€œ` â€” none were
  present (the fix3 doc was authored with correct UTF-8 em-dashes throughout); no changes were
  needed there. The only matches for that byte pattern anywhere in `docs/` are inside the fix4 plan
  document itself, where they appear deliberately inside backticks as illustrative examples of what
  mojibake looks like, not as actual encoding errors.
- No database migration in this pass, as directed â€” the two-axis model is entirely preference-backed.

## Internal Handoff Artifact

Debug APK copied to:

```text
private/Komikku-v1.13.6-kmk.8.1-fix4-debug.apk
```

(universal ABI variant, matching the existing naming pattern for every prior version in that
folder, built via `:app:assembleDebug`.) This is an internal handoff artifact only; the word
"private" here refers solely to the local folder path, never to app-visible text.

