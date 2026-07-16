# KMK-Recs v0.7.44 Shared Query Policy, Group Recommendation Parity, and For You Relevance Plan

Date: 2026-07-12

Status: planning, approved for Claude implementation after user confirmation.

## 1. Purpose

KMK-Recs v0.7.43 moved Loved/Liked group recommendations onto the normal row-based Recommendations screen and moved For You search compatibility checks into a background job. That was the right structural direction, but the implementation left three practical gaps:

1. Group recommendations still behave too much like a single-manga recommendation search. They use grouped tags for the first search, but source selection, fallback queries, candidate filtering, and scoring are not yet aligned with the richer For You recommendation system.
2. For You and group recommendations do not share one truthful query policy. For You has `RecommendationQueryPlanner`; group rows still depend on a single `GenreFilterMapper.buildSearch` attempt inside `CrossExtensionGenreSearchSource`. This creates both empty rows and unrelated rows.
3. The v0.7.43 implementation report documents deviations that should not remain vague: `RecommendationCandidateVisibilityPolicyTest` has failing tests, group recommendations do not use `RecommendationCandidateVisibilityPolicy`, and the new background compatibility job has little new focused test coverage.

This release should be a corrective and consolidation release, not a UI redesign. The goal is to make the recommendation retrieval pipeline more truthful, less strict when a source cannot map tags, less loose when a source returns unrelated search results, and easier to test.

## 2. Release Identity

- Target version: `KMK-Recs v0.7.44`.
- Target APK name: `Komikku-v1.13.6-kmk.7.44-debug.apk`.
- `KmkRecsReleaseNotes.VERSION_CODE` must be incremented monotonically from the current checked-in value. At the time of planning, v0.7.43 used `745`, so v0.7.44 should normally use `746`, but Claude must verify the current file before editing.
- Do not change Komikku's upstream Android `versionName`/`versionCode` unless the existing KMK versioning pattern already requires it.
- Do not build a final APK until every phase below is complete and the required verification commands pass.

## 3. Required Reading Before Coding

Claude must read these files before making code changes:

- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/DOCUMENTATION_RULES.md`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_PLAN.md`
- `docs/recommendations/KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_PLAN.md`
- `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_40_FOR_YOU_AND_RATED_GROUP_POLICY_FOUNDATION_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_39_FOR_YOU_ROLLING_DISCOVERY_AND_RATED_GROUP_RECOMMENDATIONS_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_AND_GROUP_SEED_ENRICHMENT_IMPLEMENTATION.md`

Claude must also inspect these code areas before editing:

- `app/src/main/java/exh/recs/RecommendationQueryPlanner.kt`
- `app/src/main/java/exh/recs/sources/GenreFilterMapper.kt`
- `app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt`
- `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`
- `app/src/main/java/exh/recs/RecommendsScreen.kt`
- `app/src/main/java/exh/recs/RecommendsScreenModel.kt`
- `app/src/main/java/exh/recs/RecommendationScorer.kt`
- `app/src/main/java/exh/recs/RecommendationSourceSelector.kt`
- `app/src/main/java/exh/recs/RecommendationCandidateVisibilityPolicy.kt`
- `app/src/main/java/exh/recs/GroupRecommendationSeedBuilder.kt`
- `app/src/main/java/exh/recs/GroupSeedRecommendationScorer.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityJob.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityRunner.kt`
- `app/src/test/java/exh/recs/RecommendationCandidateVisibilityPolicyTest.kt`
- Existing query planner, genre mapper, group recommendation, source quality, seen/not-interested, and For You tests under `app/src/test/java/exh/recs/`.

## 4. Current Confirmed Findings

### 4.1 Group recommendation retrieval is too single-attempt

`CrossExtensionGenreSearchSource.requestNextPage()` currently:

1. chooses `genreOverride ?: manga.genre.orEmpty()`,
2. calls `catalogueSource.getFilterList()`,
3. calls `GenreFilterMapper.buildSearch(filterList, desiredGenres)`,
4. calls one `catalogueSource.getSearchManga(...)`,
5. enriches at most 10 results,
6. returns the row.

This means grouped Loved/Liked recommendations can fail because the first mapped filter attempt is too strict, or return unrelated results because unmatched tags become a text query that a source interprets poorly. It does not attempt the same strict-to-lenient strategy family For You already uses.

### 4.2 Group recommendations do not fully use source policy

`RecommendationPagingSource.createSources(...)` still obtains cross-extension rows from `sourceManager.getVisibleCatalogueSources().take(MAX_CROSS_EXTENSION_SOURCES)` when cross-extension recommendations are enabled. This does not fully honor the For You recommendation source selector, priority order, disabled/disliked source behavior, language handling, or source scope expectations.

### 4.3 Group recommendations do not fully use candidate visibility policy

The v0.7.43 implementation report explicitly states that group recommendations exclude seed members and exact Not Interested entries, but do not apply `RecommendationCandidateVisibilityPolicy`. That means favorite, rated, known, and minimum-chapter filters can diverge from For You behavior.

v0.7.44 must either:

- apply the shared visibility policy to group recommendations, or
- define a clearly named, tested, documented variant that intentionally differs.

The preferred implementation is to apply the shared policy where the required context can be loaded efficiently, using batched DB lookups and avoiding per-candidate database calls.

### 4.4 v0.7.43 tests are not clean

The focused test command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicyTest*"
```

failed 3 of 16 tests at `RecommendationCandidateVisibilityPolicyTest.kt:27` with an Injekt `GetCustomMangaInfo` binding problem when constructing favorite `Manga` instances. Claude documented this as pre-existing, but v0.7.44 must not leave the full unit suite failing if the failing tests are inside the active KMK recommendation test surface.

Acceptable fixes:

- register the missing test binding in the test setup, or
- construct test manga in a way that avoids triggering the custom-manga-info path, or
- refactor the policy test helper to provide only the fields the policy needs without invoking the favorite custom-info path.

Do not delete the tests. Do not mark them ignored unless the user explicitly approves.

### 4.5 v0.7.43 added background compatibility infrastructure with limited tests

The new `SourceRecommendationQualityJob` and `SourceRecommendationQualityRunner` are structurally good, but v0.7.43 did not add focused tests for queue target selection, state transitions, conflict guards, per-source failure isolation, or cancellation behavior. v0.7.44 should add pure tests wherever feasible, even if WorkManager itself is not unit-tested.

## 5. Implementation Phases

Claude may implement this in internal checkpoints, but must not build the final APK until all phases are complete. Each phase should compile or run focused tests before moving on.

### Phase A: Repair active test failures and stale policy comments

1. Fix `RecommendationCandidateVisibilityPolicyTest` so the test class passes in isolation.
2. Run:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicyTest*"
   ```

3. Update comments in `RecommendationCandidateVisibilityPolicy.kt` and related tests so they do not claim a flow uses the policy unless it actually does after this release.
4. If group recommendations are changed to use the policy later in this release, update comments again to reflect the final state.

Acceptance criteria:

- The focused visibility policy test command passes.
- No stale comment says "live / cache / memory / group all go through this policy" unless that is true after Phase E.

### Phase B: Create one shared query attempt policy

Create or refactor a pure helper around `RecommendationQueryPlanner` so both For You and grouped recommendation rows can describe and execute the same family of query attempts.

Preferred shape:

- Keep `RecommendationQueryPlanner` as the deterministic planner, or create a small companion such as `RecommendationQueryAttemptPolicy`.
- Add a typed attempt/result model that can represent:
  - strategy attempted,
  - tags used,
  - whether filters or text query were used,
  - whether the source did not support useful filters,
  - raw result count,
  - enriched result count,
  - visible/relevant result count,
  - failure kind.

The strict-to-lenient strategy order should support:

1. `TOP_TAGS_FILTER`
2. `TAG_PAIR`
3. `SINGLE_STRONGEST_TAG`
4. `TEXT_ONLY_TOP_TAGS`
5. bounded title/alternate-title fallback for grouped recommendations when tags produce no useful results

Important constraints:

- Do not perform broad crawling.
- Do not increase network calls without a clear cap.
- Keep per-source attempts bounded. The default can still be small, but it should be able to try more than one fallback when the first attempt returns zero or unrelated results.
- Make it deterministic. No randomness.
- Make the pure selection/classification parts unit-testable without Android.

Failure/empty states must distinguish:

- no raw results,
- source/search exception,
- filter unsupported or filter mapping failed,
- raw results found but filtered as unrelated,
- results found but metadata too weak to score confidently,
- cancellation.

Cancellation must not be swallowed.

### Phase C: Apply the shared query policy to group recommendations

Change grouped Loved/Liked recommendations so the row-based Recommendations screen still looks like the normal manga-detail recommendation screen, but its retrieval logic is seeded by the full cross-source group.

Required behavior:

1. `RecommendsScreen.Args.CrossSourceGroupSeed` should still use primitive route args only.
2. `GroupRecommendationSeedBuilder` remains the source of grouped member keys, titles, aliases, and tags.
3. The group recommendation row searches must use grouped weighted tags, not only the primary manga's tags.
4. If grouped tags fail for a source, the row should try the bounded fallback attempts from Phase B.
5. Title fallback should use `GroupRecommendationSeed.titles` or equivalent grouped title data, not only the primary manga title.
6. The row scorer should reduce overreliance on primary-title similarity. Use grouped tags/titles/aliases to decide relevance. If `RecommendationScorer.score(primary, candidate)` remains in use, explain why and add a group-aware correction so a candidate is not punished only because it is not title-similar to the primary localized entry.
7. Exclude exact group seed members.
8. Exclude exact Not Interested entries.
9. Filter or strongly downrank low-relevance/random candidates before display.
10. Emit truthful row diagnostics when all attempts fail or all results are filtered away.

Do not recreate the old deleted `GroupSeededRecommendationsScreen`. The UI should remain the normal provider/source row-based Recommendations page unless a small diagnostic subtitle is needed.

### Phase D: Apply the shared query policy to For You refresh/discovery

For You already uses `RecommendationQueryPlanner`, but v0.7.44 should make it use the shared attempt policy from Phase B so it behaves consistently with grouped recommendations.

Required behavior:

1. When a source returns no useful results for a strict tag/filter attempt, try the next bounded fallback rather than marking the source/page as empty too early.
2. If raw results exist but are unrelated after scoring/filtering, record that as `filtered as unrelated` or equivalent, not as a generic source failure.
3. If a fallback succeeds, persist the successful strategy for that source only when it actually produced useful results.
4. Do not poison rolling discovery progress because one too-strict attempt failed.
5. Respect existing candidate memory, discovery progress, source ordering, visibility settings, known manga hiding, rated visibility, Not Interested handling, and min-chapter filtering.
6. Keep Top Picks and source rows using the same relevance threshold/policy where practical.
7. Do not allow lenient text fallback to flood the page with unrelated manga. Any lenient fallback must still pass relevance scoring before display.

Acceptance criteria:

- For You can discover beyond overly strict first attempts without showing random unrelated rows.
- Sources with only weak metadata show honest status instead of silently looking like good matches.
- Existing rolling discovery behavior from v0.7.39-v0.7.41 remains intact.

### Phase E: Align group recommendation source selection and visibility with For You

Use the existing `RecommendationSourceSelector` or extract a shared source-selection helper so grouped recommendation rows do not simply use raw visible catalogue order.

Required behavior:

1. Honor recommendation languages.
2. Honor disabled/disliked sources.
3. Honor source priority order.
4. Honor the same broad source eligibility rules used by For You, unless intentionally documented.
5. Keep normal single-manga recommendations unchanged unless the same bug exists there and the fix is safe.

Candidate visibility:

1. Apply `RecommendationCandidateVisibilityPolicy` to grouped recommendations if feasible.
2. Batch-load required data:
   - taste rows keyed by source/url,
   - known IDs,
   - seen/not-interested keys,
   - chapter counts for candidate IDs when min chapter count is active,
   - seed member keys.
3. Avoid per-candidate database calls inside tight loops.
4. Add tests for seed member, favorite, rated, not-interested, known, and min-chapter behavior in the grouped path, either through pure helpers or focused model-level helpers.

If any visibility rule cannot be safely applied to group rows, document the specific reason in the implementation report and `CURRENT_STATE.md`.

### Phase F: Strengthen Source Recommendation Quality background coverage

Add focused tests or pure helper coverage around the v0.7.43 background compatibility job behavior.

Focus on testable pieces, not Android WorkManager internals:

- target queue computation,
- conflict guard decisions,
- per-source failure isolation,
- cancellation propagation,
- installed vs temporary extension cleanup decision boundaries,
- empty available-extension-list behavior,
- stale/missing/outdated compatibility target selection.

Also review `SourceRecommendationQualityRunner.loadAvailableExtensions()`:

- If `availableExtensionsFlow.value` can be empty during normal startup, add a safe bounded refresh/wait path using existing extension-manager APIs where available.
- If no safe refresh exists, surface a clear diagnostic and avoid writing misleading source-level errors.

### Phase G: Small-phone UI density and readability pass

The user specifically called out that the recommendation UI can look acceptable on a tablet but become clogged and hard to scan on a phone. This phase must be implemented as part of v0.7.44 unless Claude finds a blocking reason and documents it before building.

This is not a visual redesign. It is a compactness/readability pass that should follow Komikku/Mihon-style list density and Material components already used by the app.

#### G.1 Source Evaluation row compactness

Files to inspect and update:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitDisplayPolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitFailureClassifier.kt`
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Current pain point:

`EvaluationResultRow` can render title, catalogue subtitle, For You search line, error kind, and a two-line plan/error detail. On phone this becomes vertically noisy, especially when many rows show `UnsupportedOperationException`, `NetworkOnMainThreadException`, or long plan names.

Required changes:

1. Keep the first two lines always visible:
   - source display name,
   - compact catalogue/evidence subtitle.
2. Keep the current For You search/compatibility label visible when present, but shorten labels where possible using KMR strings rather than hardcoded English.
3. Collapse detailed rec-quality diagnostics by default on phones or compact width. The row should show a short status such as `Search error`, `No matches`, `Weak`, or `Outdated`, with an expand icon or details affordance to reveal the full diagnostic text.
4. Do not show raw exception spam in the default collapsed row. Full details may be shown after expansion.
5. Replace any hardcoded failure labels in `EvaluationResultRow` with KMR strings if they are user-visible.
6. Preserve accessibility: the expand/collapse control must have meaningful content description if an icon button is used.
7. Do not remove useful diagnostic information; make it opt-in per row instead of always occupying vertical space.
8. Ensure `VerdictBadge` does not force row overflow on narrow screens. If needed, allow the verdict badge to wrap below the title/subtitle block or abbreviate only through localized strings.

Implementation hint:

- Add a small composable such as `ExpandableDiagnosticText` or `SourceEvaluationRowDiagnostics` inside `SourceEvaluationScreen.kt`.
- Use `rememberSaveable(evaluation.evaluationKey)` or equivalent per-row expansion state if a stable key exists. If not, use a stable combination already used for source evaluation rows.
- Prefer `maxLines = 1` for collapsed diagnostics and `maxLines = 4` or scroll/dialog for expanded text.

#### G.2 Source Evaluation control sections

Files to inspect and update:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`

Required changes:

1. Action rows with many `TextButton`s must not form a single cramped horizontal row on phones.
2. Replace long horizontal button rows with `FlowRow`, stacked buttons, or a compact menu where appropriate.
3. `ShizukuSetupCard` currently uses one `Row` for Open / Stop using / Use Private / Uninstall / Refresh. On phones this can overflow or crowd. Convert this action area to a wrapping layout or split primary/secondary actions.
4. Candidate diagnostics and safety diagnostics should remain secondary, not visually dominant.
5. Existing behavior must remain identical; only layout and presentation should change.

#### G.3 Recommendations Settings density

Files to inspect and update:

- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt`
- `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` only if state is needed for expand/collapse.

Required changes:

1. Preferred/blocked tag chips already use `FlowRow`, but the settings page has many sections. Ensure vertical spacing is not excessive on phone and chips have enough vertical spacing not to touch.
2. Long source suggestion rows should not place too many text buttons in one horizontal row. Use wrapped actions or overflow menu if needed.
3. Keep source priority/reorder controls stable and avoid accidental reorder/reset interactions.
4. Do not introduce a landing-page style or card-heavy redesign. Use existing settings row conventions.

#### G.4 Rated Manga and grouped recommendation entry points

Files to inspect and update:

- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`
- `app/src/main/java/exh/recs/components/RecommendsScreen.kt`

Required changes:

1. Loved/Liked/Disliked collection controls should remain usable on phone. `RatedSortRow` currently uses `FilterChip`s in a `Row`; if chips overflow on narrow width, use `FlowRow` or a compact sort menu.
2. Group duplicates toggle, sort controls, share/manage/link actions, and Recommendations-from-this entry point must remain discoverable without requiring long-press.
3. Do not create three separate implementations for Loved/Liked/Disliked. Preserve the reusable rated-manga collection structure.
4. Recommends rows should not show large diagnostic blocks by default. If v0.7.44 adds row diagnostics for no results/search errors, use compact labels with expandable detail.

#### G.5 Phone verification expectations

Claude should verify the UI mentally through Compose constraints and, where possible, by checking layout code for:

- no single horizontal row with many long text actions,
- no unbounded diagnostic text in list rows,
- no hardcoded English user-visible strings introduced by this pass,
- no button text likely to overflow on a 360dp-wide phone,
- no unnecessary cards nested inside cards,
- no tablet-only assumptions.

If screenshot/device testing is not available, Claude must document that limitation in the implementation report and list the exact screens reviewed by code inspection.

Acceptance criteria for this phase:

- Source Evaluation rows are scannable on phone: source name, compact evidence, compact compatibility, verdict.
- Detailed errors are still accessible but not always expanded.
- Shizuku/source-evaluation action rows wrap or stack instead of crowding.
- Rated Manga sort/filter controls do not overflow on narrow screens.
- No user-visible KMK strings are hardcoded.
- Behavior is unchanged except for layout/readability.

### Phase H: Documentation, release notes, and cleanup

1. Add an implementation report after coding:

   `docs/recommendations/KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_IMPLEMENTATION.md`

2. Update:

   - `docs/recommendations/README.md`
   - `docs/recommendations/CURRENT_STATE.md`
   - `docs/recommendations/NEXT_WORK.md`
   - `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
   - `RECOMMENDATION_VERSIONING.md`
   - `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` only if data/backup behavior changes
   - `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` only if network/background/privacy behavior changes
   - `KmkRecsReleaseNotes.kt`

3. Remove dead code or stale strings made obsolete by the new shared query policy.
4. Do not delete historical implementation reports. If a doc is stale, update top-level current docs and archive only if it is a superseded plan.

## 6. Tests Required

Claude must run focused tests first, then full verification.

Focused tests to add/update and run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicyTest*"
.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationQueryPlanner*"
.\gradlew.bat :app:testDebugUnitTest --tests "*GenreFilterMapper*"
.\gradlew.bat :app:testDebugUnitTest --tests "*Group*Recommendation*"
.\gradlew.bat :app:testDebugUnitTest --tests "*SourceRecommendationQuality*"
.\gradlew.bat :app:testDebugUnitTest --tests "*Seen*"
```

If new helpers are added, add matching tests and include them in the focused test pass.

Full verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat spotlessCheck
.\gradlew.bat assembleDebug
```

Use the local JDK pattern already documented in this workspace if needed:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

Do not ship with known unit-test failures unless the user explicitly approves a documented exception.

## 7. Acceptance Criteria

v0.7.44 is complete only when all of the following are true:

- `RecommendationCandidateVisibilityPolicyTest` passes.
- Full `:app:testDebugUnitTest` passes.
- Grouped Loved/Liked recommendations use grouped seed tags/titles/aliases instead of behaving like a one-manga search.
- Grouped recommendation rows use the shared strict-to-lenient query policy.
- Grouped recommendation rows honor source priority/language/disabled/disliked source policy.
- Grouped recommendation candidates are filtered through the shared visibility policy or an explicitly documented/tested variant.
- For You uses the same query-attempt principles and does not mark a source as empty/bad after one overly strict attempt.
- Lenient fallback queries do not flood For You or group rows with unrelated manga.
- Row diagnostics distinguish search errors, no results, unsupported filters, unrelated filtered results, and weak metadata.
- The background Source Recommendation Quality job has improved pure test coverage around its policies.
- Not Interested remains a mild negative signal and exact hide marker; no storage/proto rename is made.
- Docs and release notes reflect the final implemented behavior.
- Phone-density UI fixes are complete for Source Evaluation, Recommendation Settings, Rated Manga controls, and grouped recommendation diagnostics.
- Final APK is built only after all phases are complete.

## 8. Non-Goals

- Do not redesign the For You page UI.
- Do not redesign the whole settings/navigation model; only make the current KMK recommendation surfaces readable on phone.
- Do not recreate the deleted `GroupSeededRecommendationsScreen`.
- Do not add a new database table unless the implementation proves it is necessary and documents why.
- Do not broaden extension/source crawling beyond existing bounded recommendation behavior.
- Do not rename backup fields or proto fields for `seenRecommendationMangaKeys`.
- Do not change the normal global search behavior.
- Do not change OCR branch behavior.

## 9. Claude Handoff Notes

This plan is intentionally strict because the previous release worked structurally but left behavioral gaps and test gaps. Claude should implement in small checkpoints, but the final build must wait until all phases are done. If a phase cannot be implemented safely, Claude should stop, document the exact blocker, and avoid producing a misleading APK.

The highest-priority user-facing fixes are:

1. Grouped Loved/Liked recommendations should feel like the normal recommendation rows, but seeded by every confirmed grouped version of that manga.
2. For You should compare candidates through a shared query policy, not get stuck on one strict search attempt or show unrelated lenient search results.
3. The active test suite must be clean so future releases are not built on shaky ground.





