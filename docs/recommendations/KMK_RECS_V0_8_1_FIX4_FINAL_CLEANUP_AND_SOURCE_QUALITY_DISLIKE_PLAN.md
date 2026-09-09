# KMK-Recs v0.8.1-fix4 Final Cleanup And Source Quality Dislike Plan

Date: 2026-07-12

Status: approved planning document. Do not implement until handed to Claude Code with the matching prompt.

Target build: `KMK-Recs v0.8.1-fix4`

Build channel note: this is an internal/private APK handoff. This phrase is for development documentation and file handoff only. Do not add the words private, public, internal, community, personal, or test build to app-facing strings, What's New text, dialogs, settings labels, or release-note text unless the text is describing a real user-facing feature with that name. The existing extension installer mode label `Private (recommended)` is allowed because it describes the Android extension installer mode, not the build channel.

## Goal

Finish the remaining v0.8.1 follow-up work before moving on:

1. Clean up the app-facing wording and documentation defects found after v0.8.1-fix3.
2. Add an explicit source/catalogue-quality dislike path, separate from the existing For You recommendation-source dislike.
3. Let users mark a source as poor overall because the library is bad, too lewd, hentai/porn-heavy, misleading, or otherwise not worth recommending, even when the issue is not only the For You row quality.
4. Keep source feedback understandable by preserving the distinction between:
   - For You / recommendation behavior feedback.
   - Overall source/catalogue feedback.
5. Verify that Source Evaluation stale continuation still works after the new filtering states are added.

This is a corrective/polish pass, not a broad feature redesign.

## Current Findings

### v0.8.1-fix3 Works, But Has Cleanup Gaps

Claude implemented the v0.8.1-fix3 stale/outdated Source Evaluation continuation fix:

- `SourceEvaluationCandidateQueuePolicy.staleCandidates()`
- `SourcePreferences.sourceEvaluationContinuationCursorStale()`
- `SourceEvaluationScreenModel` stale queue state/actions
- `SourceEvaluationJobState.pendingIsStaleRun`
- stale queue UI actions
- targeted Source Evaluation tests

Codex verified:

- Source Evaluation targeted unit tests passed.
- `spotlessCheck` passed.
- Claude reported `assembleDebug` passed and produced:
  `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.8.1-fix3-debug.apk`

Remaining fix3 cleanup:

- Some app-facing release-note/update strings still say private/public/community/test-build wording.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` has malformed-looking KMK XML comments around the stale reassessment strings.
- `KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_IMPLEMENTATION.md` contains mojibake examples such as `Ã¢â‚¬â€` / `Ã¢â€ â€™`.
- The stale reassessment UI should clearly indicate completion after all stale rows are processed, rather than only removing the action.
- Tests should explicitly cover the case where `unassessed == 0` but `stale/outdated > 0`.

### Existing Source Preference Is Too Narrow

Current source preference persistence:

- `SourcePreferences.likedRecommendationSourceKeys()`
- `SourcePreferences.dislikedRecommendationSourceKeys()`
- `RecommendationSourcePreferenceStore`

Current key format:

- Installed source: `i|<sourceId>`
- Available source: `a|<signatureHash>|<pkgName>|<sourceId>`
- Available extension: `a|<signatureHash>|<pkgName>`

Current usages:

- `BrowsePersonalRecommendationsScreenModel` excludes disliked installed source IDs from For You source selection.
- `RecommendsScreenModel` excludes disliked installed sources from recommendation flows.
- `RecommendationsSettingsScreenModel` lets the settings UI like/dislike sources.
- `GetNonInstalledSourceSuggestions` filters disliked available sources from Sources To Try.
- `GetSourceEvaluationCandidates` filters disliked available extensions from Source Evaluation candidate pools.

Problem:

The existing naming and UI mostly imply recommendation behavior feedback. The user now wants a stronger and broader action: dislike a source as a source/catalogue, for example because its library is bad, low-quality, misleading, too lewd, porn/hentai-heavy, or otherwise not worth trying. This should not be conflated with "this source's For You results are weak."

## Desired Source Feedback Model

Add a clear two-axis model:

1. **Recommendation preference**
   - Existing behavior.
   - Answers: "Do I want this source used in For You/recommendation rows?"
   - Existing keys can remain as `likedRecommendationSourceKeys` / `dislikedRecommendationSourceKeys`.

2. **Catalogue/source preference**
   - New behavior.
   - Answers: "Do I consider this source itself worth showing/suggesting/evaluating?"
   - Used for sources with poor libraries, bad catalogue quality, lewd-heavy catalogues, hentai/porn-heavy catalogues, or other overall source-quality problems.
   - Should hide/deprioritize the source from Sources To Try and Source Evaluation candidate queues by default.

Do not overload a single "dislike" everywhere without explaining the scope. The user must be able to understand whether they disliked recommendation behavior or the whole source.

## Data Model Plan

Prefer a preference-backed implementation first, matching the existing source preference store. Avoid a database migration unless Claude finds a strong reason.

### Add SourcePreferences Keys

In `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`, add:

```kotlin
/** Semicolon-separated liked catalogue/source quality keys. Same key format as RecommendationSourcePreferenceStore. */
fun likedCatalogueSourceKeys() = preferenceStore.getString("liked_catalogue_source_keys", "")

/** Semicolon-separated disliked catalogue/source quality keys. Same key format as RecommendationSourcePreferenceStore. */
fun dislikedCatalogueSourceKeys() = preferenceStore.getString("disliked_catalogue_source_keys", "")
```

If Claude prefers a more precise name, use `likedSourceQualityKeys()` / `dislikedSourceQualityKeys()`, but be consistent in code and docs. The user-facing wording should be plain: "source quality" or "library quality", not "catalogue" if that feels technical.

### Reuse Or Extend RecommendationSourcePreferenceStore

Keep the existing key serializer rather than creating a second duplicate serializer. Either:

- Rename the comments in `RecommendationSourcePreferenceStore` to describe generic source preference keys, while preserving method names if changing names would cause churn; or
- Add a small wrapper/alias object such as `SourcePreferenceKeyStore` and have `RecommendationSourcePreferenceStore` delegate to it.

Do not change the stored key format.

Add tests to ensure:

- installed and available keys round-trip unchanged.
- a key cannot be both liked and disliked within the same axis.
- recommendation dislikes and catalogue/source dislikes are independent axes.

## Filtering Rules

### For You / Recommendation Rows

Keep existing behavior:

- `dislikedRecommendationSourceKeys()` continues to exclude installed sources from For You and grouped recommendation source selection.
- `dislikedCatalogueSourceKeys()` should also be respected by For You only when the source is installed and the user explicitly disliked the source/library overall.

Reason:

If the user says a source is bad or too lewd overall, For You should not keep using it. This is stronger than a recommendation-only dislike.

Implementation points:

- `BrowsePersonalRecommendationsScreenModel`
- `RecommendsScreenModel`
- any shared source selector introduced in v0.7.40-v0.7.44, if it centralizes disliked source exclusion

### Sources To Try

`GetNonInstalledSourceSuggestions` should filter both:

- `dislikedRecommendationSourceKeys()` for existing behavior, if the user disliked the suggestion/recommendation context.
- `dislikedCatalogueSourceKeys()` for source/library quality dislike.

Additional explicit/lewd handling:

- If `blockExplicitPornHentaiSources()` is enabled, continue to hide explicit porn/hentai candidates using `ExplicitSourceClassifier`.
- If the user manually marks a source as "Poor library / too lewd", it should be hidden even when the global explicit filter is off.
- Ecchi-only sources should not be auto-blocked by the explicit filter, but the user may manually dislike them as source quality if they personally do not want them.

### Source Evaluation Candidate Pool

`GetSourceEvaluationCandidates` / `SourceEvaluationCandidateFilter.buildPool()` should filter both:

- recommendation-disliked available keys where appropriate.
- catalogue/source-disliked available keys.

The diagnostic counts should separate them so the UI can explain:

- hidden because disliked for recommendation behavior,
- hidden because disliked as source/library,
- hidden because explicit/porn/hentai filter,
- hidden because unsafe/quarantined.

Do not collapse all hidden sources into one opaque count.

### Past Evaluations Display

Past evaluations may still include rows for sources now disliked as source/library. The default UI should avoid wasting space, but recovery must be possible.

Recommended behavior:

- Hide source-quality-disliked rows by default from suggested/evaluation action lists.
- In Past Evaluations, either:
  - keep them visible with a small "Source disliked" badge and sorted below normal rows; or
  - hide them behind a "Show disliked sources" toggle.

Given the user's UI preference, prefer hiding them behind a compact toggle in Source management rather than placing warning sections at the top.

## UI Plan

### Recommendation Settings

Where source status or Sources To Try rows currently expose like/dislike:

- Preserve existing For You/recommendation source like/dislike actions.
- Add a clearly separate action for source/library quality:
  - "Mark source as poor"
  - "Mark source as too explicit"
  - "Clear source quality mark"

Keep this compact. On phone UI, avoid adding several always-visible buttons per row. Use a row overflow menu or secondary action sheet if necessary.

Suggested labels:

- Recommendation behavior:
  - "Prefer in For You"
  - "Avoid in For You"
  - "Clear For You preference"
- Source/library quality:
  - "Mark source as poor"
  - "Mark source as too explicit"
  - "Clear source mark"

Use KMR strings for all user-facing text.

### Source Evaluation

Add source-quality actions to each visible evaluation row, preferably in the same row menu/actions area used for existing management:

- "Mark source as poor"
- "Mark source as too explicit"
- "Clear source mark" when already marked

For a row marked poor/lewd:

- It should be excluded from future Source Evaluation candidate queues by default.
- It should be excluded from Sources To Try by default.
- It should not be deleted from the evaluation database. Keep historical evidence.

### Management / Recovery

Add management actions:

- "Clear source quality marks"
- Optionally show counts:
  - `N avoided For You source(s)`
  - `N hidden source-quality mark(s)`

These must be in a collapsible/low-prominence Source management area, not pinned at the top.

## v0.8.1-fix3 Cleanup Plan

### Remove App-Facing Build Channel Wording

Fix app-facing strings and release notes:

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Remove wording such as:

- private corrective follow-up
- private feature build
- public test documentation
- public/community testing
- private feature changes

Replace with user-facing feature wording:

- "Version visibility and sync validation."
- "Rated Manga and Source Evaluation polish."
- "Final v0.7 feature-line polish."
- "Open What's new to view the latest KMK-Recs changes."

Do not remove developer documentation references where they are explicitly documentation-only. But app-facing release notes and app strings must not contain build-channel labels.

### Normalize XML Comments

In `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`, replace malformed-looking comments like:

```xml
<!-- KMK --> v0.8.1-fix3: ...
```

with valid comments:

```xml
<!-- KMK v0.8.1-fix3: ... -->
```

Do this near the Source Evaluation stale reassessment strings and any adjacent KMK comments that have the same pattern.

### Fix Documentation Encoding

In:

- `docs/recommendations/KMK_RECS_V0_8_1_FIX3_SOURCE_EVALUATION_CONTINUATION_FIX_IMPLEMENTATION.md`
- any touched current-state/index docs

Replace mojibake artifacts:

- `Ã¢â‚¬â€` -> `--` or `-`
- `Ã¢â€ â€™` -> `->`
- `Ãƒâ€”` -> `x`
- `1Ã¢â‚¬â€œ2` -> `1-2`

Use ASCII unless the file already deliberately uses Unicode.

### Stale Queue Completion Feedback

After a stale/outdated reassessment queue is fully processed:

- The UI should show a compact completion message such as "Outdated reassessment complete" or "No outdated sources remaining".
- The message should be state-derived, not a permanent noisy banner.
- It should disappear naturally when new stale/outdated work appears.

Add KMR string(s).

## Tests Required

### Existing Verification

Run:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest --tests "*SourceEvaluation*"
.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationSourcePreferenceStoreTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*NonInstalledSourceSuggestionScorerTest"
.\gradlew.bat spotlessCheck
.\gradlew.bat assembleDebug
```

If any focused test selector misses new tests because of naming, run the closest package-level or full unit test command.

### New/Updated Unit Tests

Add tests for:

1. **Source preference axes**
   - Recommendation dislike and source-quality dislike are independent.
   - Liking a source in one axis does not clear the other axis unless explicitly designed and documented.
   - Clearing source-quality mark works without clearing For You preference.

2. **Sources To Try**
   - source-quality-disliked available sources are hidden.
   - recommendation-disliked available sources remain hidden per existing behavior.
   - explicit filter and manual lewd/poor mark are separate.
   - a source manually marked too explicit is hidden even when global explicit filter is off.

3. **Source Evaluation candidate filtering**
   - source-quality-disliked available extensions do not enter candidate pool.
   - diagnostic counts distinguish recommendation-disliked vs source-quality-disliked vs explicit-hidden.
   - unassessed candidate count excludes source-quality-disliked sources.

4. **Stale reassessment continuation**
   - `unassessed == 0` and `stale > 0` enables stale reassessment.
   - stale cursor advances after failed candidates as well as successful candidates.
   - stale completion state is visible when remaining stale count reaches zero.

5. **Release-note wording**
   - Add a pure/string-level test if existing release-note tests exist.
   - At minimum grep during verification:

```powershell
rg -n "private corrective|private feature|public/community|public test|test build|private feature changes" app\src\main\java i18n-kmk\src\commonMain\moko-resources\base\strings.xml
```

This grep should return no app-facing matches except legitimate extension installer mode strings.

## Documentation Updates Required

Update these files:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `RECOMMENDATION_VERSIONING.md` if APK/version handoff changes
- New implementation report:
  `docs/recommendations/KMK_RECS_V0_8_1_FIX4_FINAL_CLEANUP_AND_SOURCE_QUALITY_DISLIKE_IMPLEMENTATION.md`

The implementation report must include:

- exact files changed,
- how the two source-feedback axes are stored,
- how source-quality dislikes affect For You, Sources To Try, and Source Evaluation,
- how poor library vs lewd/explicit manual source marks are represented,
- all tests run,
- APK path,
- known limitations.

## APK Handoff

After `assembleDebug` succeeds, copy the APK to:

```text
C:\Users\USER\Downloads\Komikku\private\
```

Use the standard debug naming convention. Expected pattern:

```text
Komikku-v1.13.6-kmk.8.1-fix4-debug.apk
```

Do not put "private" or "public" into any app-visible UI text. The folder path is only for local handoff.

## Acceptance Criteria

This pass is complete when:

1. Source Evaluation stale/outdated continuation still works and has an understandable completion state.
2. Users can mark a source as poor/lewd/too explicit as a source-quality judgement, not only as a For You dislike.
3. Source-quality-disliked sources are hidden/deprioritized from Sources To Try and Source Evaluation by default.
4. Existing For You source dislike still works and remains semantically separate.
5. Recovery/clear actions exist for source-quality marks.
6. App-facing text does not expose private/public/internal/test-build wording.
7. XML comments and docs are clean.
8. Focused tests, `spotlessCheck`, and `assembleDebug` pass.
9. A v0.8.1-fix4 implementation report is written.
10. The APK is copied to the private handoff folder.


