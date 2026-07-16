# KMK-Recs v0.7.43 Background Compatibility, Group Recommendations, and Seen Signal Plan

Date: 2026-07-12

Status: implementation plan. Do not implement until the user explicitly asks Claude to implement it.

Version family: `KMK-Recs v0.7.43`. This is a coordinated feature/fix release, not another `v0.7.42-fixN`, because it includes new user-facing behavior beyond correcting v0.7.42 display/sort state.

## 1. Release Identity

Use the canonical rule in `RECOMMENDATION_VERSIONING.md`.

- `KmkRecsReleaseNotes.VERSION_CODE`: next monotonic code after the current checked-in value.
- `KmkRecsReleaseNotes.VERSION_NAME`: `KMK-Recs v0.7.43`.
- APK handoff name: `Komikku-v1.13.6-kmk.7.43-debug.apk`.
- Do not change Android `versionCode` or `versionName` merely for this local KMK feature release marker.
- Preserve historical version records; do not renumber older releases.

Claude must verify the current `VERSION_CODE` before editing and choose the next valid monotonic integer. Do not guess.

## 2. Why This Release Exists

Three related issues are now blocking the recommendations system from feeling coherent:

1. **For You search compatibility does not run like Source Evaluation.**
   Full Source Evaluation is a WorkManager foreground job through `SourceEvaluationJob`, but the manual For You search compatibility action still runs from `SourceEvaluationScreenModel.runRecQualityCheck(...)` using `screenModelScope.launch`. Leaving Source Evaluation can cancel or stop the compatibility run. This makes it feel unreliable and different from the main evaluator.

2. **Loved/Liked group recommendations use a separate weaker screen.**
   Manga-detail recommendations use `RecommendsScreenModel`, `RecommendationPagingSource`, and `CrossExtensionGenreSearchSource`, showing per-provider/per-extension rows. Loved/Liked group recommendations instead use `GroupSeededRecommendationsScreenModel`, a separate one-grid search flow. It builds a group seed and does use group tags, but it does not reuse the normal manga-detail Recommendations page behavior. The user wants recommendations from Loved/Liked groups to feel like the existing manga-detail Recommendations page, but seeded by every confirmed version in the group.

3. **Seen currently sounds neutral, but the desired behavior is mild negative suppression.**
   The current Seen/Already read behavior hides exact manga/version identities from For You and does not alter taste weights. The user now wants this state to mean roughly "I do not want to see this again, and similar results can be slightly deprioritized," while still being much weaker than Dislike. The label must not mislead users.

This plan intentionally includes cleanup requirements so Claude does not leave unused duplicate screens, scorers, or stale docs behind.

## 3. Non-Negotiable Implementation Rule

Claude may implement this in internal steps or multiple passes, but it must not produce the final APK or declare the release complete until all required sections below are implemented, documented, tested, and reconciled.

If a section proves unsafe or infeasible after code inspection, Claude must stop, document the reason in the implementation report, and ask for direction instead of silently dropping it.

## 4. Required Reading Before Code Changes

Read these files before editing:

- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/DOCUMENTATION_RULES.md`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_FOLLOWUP_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_42_SOURCE_EVIDENCE_DISPLAY_AND_SORT_FOLLOWUP_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_35_RATED_MANGA_AND_GROUP_SEEDED_RECOMMENDATIONS_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_36_RATED_MANGA_UI_PARITY_AND_GROUP_RECS_CRASH_FIX_PLAN.md`
- `docs/recommendations/KMK_RECS_V0_7_37_GROUP_SEEDED_RECOMMENDATIONS_LOADING_FIX_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_38_RECOMMENDATION_DISCOVERY_MEMORY_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_40_FOR_YOU_AND_RATED_GROUP_POLICY_FOUNDATION_IMPLEMENTATION.md`
- `docs/recommendations/KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_AND_SOURCE_EVIDENCE_ROADMAP_PLAN.md`
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`

Verify current behavior in code before changing it. A plan or implementation report is not proof.

## 5. Required Code Inspection Targets

### Source Evaluation and For You Compatibility

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationJobState.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationNotifier.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitDisplayPolicy.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityQueue.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityDiagnostics.kt`
- `domain/src/main/java/tachiyomi/domain/taste/model/SourceRecommendationFit.kt`
- `data/src/main/sqldelight/tachiyomi/data/source_recommendation_fit.sq`
- `data/src/main/sqldelight/tachiyomi/migrations/`

### Manga Detail Recommendations and Group Recommendations

- `app/src/main/java/exh/recs/RecommendsScreen.kt`
- `app/src/main/java/exh/recs/RecommendsScreenModel.kt`
- `app/src/main/java/exh/recs/BrowseRecommendsScreen.kt`
- `app/src/main/java/exh/recs/BrowseRecommendsScreenModel.kt`
- `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`
- `app/src/main/java/exh/recs/sources/CrossExtensionGenreSearchSource.kt`
- `app/src/main/java/exh/recs/sources/StaticResultPagingSource.kt`
- `app/src/main/java/exh/recs/RecommendationScorer.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreen.kt`
- `app/src/main/java/exh/recs/group/GroupSeededRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationSeedBuilder.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationSeed.kt`
- `app/src/main/java/exh/recs/group/GroupSeedTag.kt`
- `app/src/main/java/exh/recs/group/GroupSeedRecommendationScorer.kt`
- `app/src/main/java/exh/recs/group/GroupRecommendationLoopPolicy.kt`
- `app/src/main/java/exh/recs/loved/RatedMangaScreen.kt`

### Seen / Not Interested / Taste Policy

- `app/src/main/java/exh/recs/SeenRecommendationMangaStore.kt`
- `app/src/main/java/exh/recs/RecommendationCandidateVisibilityPolicy.kt`
- `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`
- `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt`
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
- taste interactors under `domain/src/main/java/tachiyomi/domain/taste/interactor/`
- taste models under `domain/src/main/java/tachiyomi/domain/taste/model/`
- backup/create/restore paths for Seen keys.

### Strings, Versioning, Tests

- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- relevant tests under `app/src/test/java/exh/recs/`
- `RECOMMENDATION_VERSIONING.md`
- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`

## 6. Phase A: Background For You Search Compatibility Job

### Current Problem

Full Source Evaluation is background-capable:

- It uses `SourceEvaluationJob`, a WorkManager `CoroutineWorker`.
- It exposes foreground notification/progress through `SourceEvaluationNotifier`.
- It lets the screen reconnect through `SourceEvaluationJobState.activeQueueState`.

Manual For You search compatibility is not equivalent:

- `SourceEvaluationScreenModel.evaluateRecommendationQualityForPromising(...)`
- `recheckOutdatedRecommendationQuality()`
- `runRecQualityCheck(...)`

These run in `screenModelScope`. If the Source Evaluation screen model is destroyed when the user navigates away, the compatibility run can stop. This is the behavior the user observed.

### Required Behavior

The user must be able to start checking missing promising sources, recheck outdated promising sources, recheck all eligible promising sources, leave Source Evaluation while the run continues, tap the foreground notification to return to progress, reopen Source Evaluation and see current progress/state, cancel the run, and see per-source errors without the whole run failing.

### Preferred Architecture

Add a separate worker and state object rather than overloading the main source-evaluation worker:

- `SourceRecommendationQualityJob.kt`
- `SourceRecommendationQualityJobState.kt`
- `SourceRecommendationQualityQueueState.kt` or a small typed state model specific to this job.
- `SourceRecommendationQualityNotifier.kt` or extend `SourceEvaluationNotifier` only if doing so stays clean and does not blur the two job types.

Use a unique WorkManager name and tag, for example:

- `TAG_JOB = "SourceRecommendationQualityJob"`
- `UNIQUE_WORK_NAME = "SourceRecommendationQualityJob:active"`

Do not reuse `SourceEvaluationJob:active`.

### Target Persistence

Do not pass full candidate objects through WorkManager input data if the list can exceed WorkManager limits.

Allowed approaches, in order of preference:

1. Persist a bounded target list in a small SQLDelight table keyed by job ID, if process-death recovery is required.
2. Use a process-scoped state object only if the implementation clearly preserves current Source Evaluation precedent and shows a visible "state lost" error after process death.

Because the existing `SourceEvaluationJobState` is process-scoped and documented as non-durable, Claude may choose the same non-durable pattern for v0.7.43 if that is safest. If it does, the UI must clearly fail with a recoverable message after process death and must not silently mark sources as checked.

If Claude adds a table, it must add SQLDelight schema, migration, repository/domain access consistent with existing patterns, and focused migration tests where available.

### Job Execution

Move the compatibility loop out of the screen-model coroutine path into a reusable runner/helper. It must reuse `SourceRecommendationFitProbe`, reuse installed/available extension resolvers currently used by `SourceEvaluationScreenModel.evaluateOneForRecQuality(...)`, continue one source at a time or with equivalent bounds, preserve temporary install/uninstall cleanup behavior, use `Dispatchers.IO` for source calls and installer/repository work, propagate `CancellationException`, catch ordinary source failures per source, write an error `SourceRecommendationFit`, and continue.

### Job Isolation

Source Evaluation and For You compatibility both may temporarily install/uninstall extensions. They must not run temporary extension operations at the same time.

Preferred first implementation: a job-level guard.

- If Source Evaluation is running, compatibility check start should show a KMR message explaining that evaluation is already running.
- If compatibility check is running, full Source Evaluation start should show a KMR message explaining that compatibility checking is already running.

If Claude chooses a mutex instead, document why and test that both jobs cannot interleave installer operations.

### UI Requirements

In `SourceEvaluationScreen`:

- Show compatibility progress separately from full Source Evaluation progress.
- Keep the existing display states from v0.7.42-fix2.
- Disable missing/outdated/all compatibility buttons while the compatibility job is running.
- Show a cancel action if a compatibility run is active.
- Do not show Shizuku warnings unless Shizuku mode is selected.
- Keep labels clear: catalogue/source evaluation vs targeted For You search compatibility.

### Notification Requirements

Compatibility background notification must show progress count, current source name if available, and state; tap back into Source Evaluation; have a cancel action if consistent with existing notification style; use `Notifications` IDs/channels consistently; and avoid colliding with the full Source Evaluation notification ID.

### Tests

Add or update pure tests for target selection, queue state transitions, state-lost behavior if process-scoped state is used, conflict guard against full Source Evaluation, per-source failure isolation, cancellation propagation, and duplicate start prevention.

Worker tests may be limited by Android/WorkManager test infrastructure. If not practical, isolate the runner/policy logic into pure helpers and test those.

## 7. Phase B: Replace Group Grid Recommendations With Group-Seeded Manga-Detail Recommendation Rows

### Current Problem

Normal manga-detail recommendations use `RecommendsScreenModel`, `RecommendationPagingSource.createSources(...)`, and rows from providers/extensions. Loved/Liked group recommendations use `GroupSeededRecommendationsScreenModel`, `GroupRecommendationSeedBuilder`, `GroupSeedRecommendationScorer`, and a single combined grid.

The group path does not feel like the normal recommendation page and can produce poor results. It should reuse the existing recommendation provider/page structure instead of maintaining a parallel system.

### Required Behavior

When the user chooses recommendations from a Loved or Liked rated group:

1. Build a seed from every confirmed linked version of that manga.
2. Use combined/weighted group metadata: titles, genres/tags, source IDs, member keys, and enriched tags when local metadata is sparse.
3. Open a recommendations screen that behaves like the manga-detail Recommendations page: provider rows, extension rows, loading/error/result state per row, and row-header drill-down for extension rows where applicable.
4. Search/scoring should use the group seed, not one arbitrary member.
5. Seed member versions must not appear as recommendations.
6. Known/rated/seen/min-chapter/favorite visibility policy must match For You/group policies already centralized in v0.7.40/v0.7.41.

### Preferred Architecture

Introduce a small recommendation seed abstraction and adapt existing providers around it. Claude does not have to use these exact names, but the implementation must avoid a fake manga hack if it causes confusion or incorrect source IDs.

```kotlin
sealed interface RecommendationSeed {
    val primaryTitle: String
    val titles: List<String>
    val genres: List<String>
    val weightedTags: List<GroupSeedTag>
    val memberKeys: Set<Pair<Long, String>>
}

data class SingleMangaRecommendationSeed(...)
data class CrossSourceGroupRecommendationSeed(...)
```

### Adapt RecommendationPagingSource

Refactor `RecommendationPagingSource` and subclasses carefully:

- Keep current single-manga behavior unchanged.
- Let provider sources access a seed title list and genre list where useful.
- Tracker providers may still need a concrete manga/tracker ID for AniList/MAL/MangaUpdates. If they cannot operate safely for a group seed, either use the primary/local manga as the tracker seed or show only providers that can accept the group seed.
- Cross-extension rows must use the group seed genres/tags, not one manga's tags.
- Existing manga-detail Recommendations page must remain unchanged for normal manga.

### CrossExtensionGenreSearchSource

Refactor or add a group-aware counterpart:

- It should map weighted group tags into `GenreFilterMapper.buildSearch(...)`.
- It should include aliases through `GetTagAliases` if the existing source path can do this cleanly.
- It should enrich top results as today, bounded by the existing cap.
- It should score with a group-aware scorer, not only `RecommendationScorer.score(singleManga, candidate)`.
- It must not show seed member keys as results.
- It must keep `cachedFiltersJson` and `cachedTextQuery` for row-header navigation where possible.

### RecommendsScreenModel

Extend `RecommendsScreen.Args` with a group-seed route, for example:

```kotlin
sealed interface Args {
    data class SingleSourceManga(...)
    data class MergedSourceMangas(...)
    data class CrossSourceGroupSeed(...)
}
```

The group route should build or receive enough seed identity to reconstruct the group in the screen model, avoid serializing large/non-Parcelable objects through Voyager route state, use primitive route args like `sourceId`, `url`, and `primaryTitle`, and avoid the previous `BadParcelableException` pattern from `CrossExtensionMatchMode`.

### RatedMangaScreen Integration

Update `RatedMangaScreen` so Loved and Liked group recommendations navigate to the new group-seeded `RecommendsScreen` route.

Do not rely only on long-press if there is already a discoverable overflow/action pattern. Add a clear action consistent with the existing Rated Manga UI. Keep long-press as a shortcut only if it already exists and does not conflict.

### Cleanup

After the new route is implemented:

- Remove or deprecate `GroupSeededRecommendationsScreen` and `GroupSeededRecommendationsScreenModel` if no references remain.
- Keep pure helper classes only if they are still used.
- `GroupRecommendationSeedBuilder`, `GroupRecommendationSeed`, and `GroupSeedTag` likely stay.
- `GroupSeedRecommendationScorer` may stay if the new row-based path uses it.
- `GroupRecommendationLoopPolicy` should be removed if it becomes unused.
- Run `rg "GroupSeededRecommendations"` and verify no stale references remain.
- Do not leave dead UI screens, stale strings, or unused tests.

### Tests

Add or update tests for seed building from all linked versions, group tag weighting, seed member exclusion, route args safety, grouped-tag cross-extension search, source priority/disabled/disliked source policy, candidate visibility policy, and unchanged normal single-manga Recommendations behavior.

## 8. Phase C: Seen Becomes Mild Negative Only If Renamed/Reframed

### Current Problem

Existing docs describe Seen as neutral: it hides exact manga/version from For You, does not change tag weights, and does not count as rating evidence.

The user now wants this state to mean "I do not want to see this title again, and similar things can be slightly deprioritized," while still much weaker than Dislike. The current label `Seen` is misleading if it becomes negative.

### Required Product Decision

Do not silently make `Seen` negative under the same label.

Preferred implementation: rename/reframe user-facing `Seen` to `Not interested` or `Skip`.

- Meaning: exact title is hidden and similar candidates receive a mild negative influence.
- It is weaker than Dislike.

Alternative: keep internal storage as `SeenRecommendationMangaStore`, but change UI copy to clarify `Not interested`, `Not interested in other versions`, and "Hidden from For You and lightly deprioritizes similar manga".

Do not create a brand-new duplicate title-specific dislike system unless code inspection proves the existing Seen path cannot be safely extended.

### Scoring Semantics

Target scoring intent:

- Love: strong positive.
- Like: positive.
- Not Interested / Skip: mild negative.
- Dislike: strong negative.

The mild negative should not poison the profile as strongly as Dislike. It should be enough to break ties or slightly reduce similar recommendations, not enough to erase broad genres the user otherwise likes.

Suggested initial weight:

- `NOT_INTERESTED_WEIGHT = -0.25` or `-0.35`.
- Dislike remains much stronger, roughly `-1.5` to `-2.0` depending on the existing taste profile model.

Claude must inspect `GetTasteProfile`, `PersonalRecommendationScorer`, candidate-memory ranking, and any tag-weight learning code before choosing the exact number.

### Data Model

Prefer not to add a new backup/proto field if internal storage remains the same Seen key set.

If the UI is renamed but storage remains `seenRecommendationMangaKeys`, update docs to clarify:

- internal name remains Seen for compatibility/history;
- user-facing behavior is Not Interested / Skip;
- backup proto 626 still stores these keys.

If a new preference/database table is added, update backup, restore, sync audit, security/privacy docs, and tests.

### Candidate Visibility

The exact Not Interested/Seen manga must remain hidden from For You and group recommendations.

When scoring similar candidates:

- derive mild negative tag evidence from Not Interested/Seen manga metadata where available;
- apply aliases and blocked tags consistently;
- do not count Not Interested as a full Dislike;
- do not count it toward the reassessment rating threshold unless the user-facing copy says it is a rating. Preferred: do not count it as a rating threshold.

### UI Copy

All visible strings must use KMR resources.

Update copy in manga detail rating/overflow menus, cross-extension matching modes, Rated Manga or For You actions where Seen appears, What's New/release notes, and documentation.

Avoid ambiguous wording. If the behavior is mild-negative, `Seen` alone is not enough.

### Tests

Add/update tests for exact Not Interested manga hidden, Not Interested other versions hidden, mild negative lower than Dislike, Dislike remaining stronger, Like/Love positive behavior, backup/restore round-trip if storage remains proto 626, and docs/tests no longer claiming Seen is neutral after this change.

## 9. Phase D: Cross-Cutting Cleanup and Standardization

Claude must clean up after the feature work.

Required checks:

```powershell
rg -n "GroupSeededRecommendations|Search Reliability|Recommendations: Good|Seen other versions|Mark as seen|For You search compatibility|SourceRecommendationQualityJob|SourceRecommendationFitProbe" app/src/main/java docs i18n-kmk app/src/test
```

For each hit, confirm it is still correct, update stale wording, remove dead code, archive obsolete docs only if they are replaced and not part of current history, and do not delete historical implementation reports merely because behavior evolved.

No scattered unused code should remain: no unused screens, no unused screen models, no unused KMR strings, no unused tests for deleted behavior, and no duplicate scoring policy when one shared helper can be used.

Run `spotlessApply` only if formatting changes are required. Then run `spotlessCheck`.

## 10. Documentation Updates

Create an implementation report:

`docs/recommendations/KMK_RECS_V0_7_43_BACKGROUND_COMPATIBILITY_GROUP_RECS_AND_SEEN_SIGNAL_IMPLEMENTATION.md`

It must include date, release identity, exact code files changed, exact docs changed, behavior changes, data/migration/backup impact, compatibility with old backups/preferences, tests run, build output/APK path, deviations from this plan, and remaining limitations.

Update:

- `docs/recommendations/README.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `RECOMMENDATION_VERSIONING.md`
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` if Seen/Not Interested storage semantics change.
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` if background jobs, notifications, temporary installs, or stored data semantics change.
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`

If group recommendation dead code is removed, update historical/current docs so they do not tell future AI agents to use deleted classes.

## 11. Verification Commands

Use repo-local JDK 17 as documented in `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

Run focused tests first. Exact test names will depend on implementation, but at minimum include:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SourceRecommendationQuality*"
.\gradlew.bat :app:testDebugUnitTest --tests "*SourceEvaluation*"
.\gradlew.bat :app:testDebugUnitTest --tests "*Group*Recommendation*"
.\gradlew.bat :app:testDebugUnitTest --tests "*Seen*"
.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicy*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat spotlessCheck
.\gradlew.bat assembleDebug
```

If any focused wildcard matches no tests, say so and run the nearest real tests. Do not present a no-op test command as proof.

## 12. Acceptance Criteria

1. For You search compatibility checks continue after leaving Source Evaluation.
2. Compatibility checks expose progress, cancellation, failure state, and notification return-to-screen behavior.
3. Full Source Evaluation and compatibility checks cannot conflict over temporary extension installation.
4. Shizuku warnings appear only when Shizuku installer mode is selected.
5. Loved/Liked group recommendations use the normal Recommendations page pattern with provider/extension rows.
6. Group recommendations use all confirmed linked versions as the seed, not one arbitrary manga.
7. Group recommendation results exclude seed members and respect the shared source and candidate visibility policies.
8. The old one-grid `GroupSeededRecommendationsScreen` path is removed or clearly left only if still intentionally used.
9. Seen is not silently made negative under a misleading label. User-facing copy is updated to Not Interested/Skip or equivalent if mild-negative behavior is implemented.
10. Not Interested/Skip has a weaker negative effect than Dislike and still hides exact manga.
11. Documentation, encyclopedia, versioning, current state, and next work are reconciled.
12. No dead duplicate code or unused strings remain.
13. Focused tests, full unit tests, `spotlessCheck`, and `assembleDebug` pass.
14. Claude does not build or hand off the APK until every required phase is complete.

## 13. Claude Guardrails

- Follow official Komikku/KMK formatting and existing architecture.
- Prefer reusing `RecommendsScreenModel`, `RecommendationPagingSource`, `RecommendationSourceSelector`, `RecommendationCandidateVisibilityPolicy`, `GroupRecommendationSeedBuilder`, and existing source-evaluation helpers over creating parallel systems.
- Do not use raw English UI strings.
- Do not add source-specific extension logic unless there is a documented reason.
- Do not broaden network/probe scope silently.
- Preserve `CancellationException` propagation.
- Keep temporary install cleanup explicit and guarded.
- Do not delete historical docs. Only update current docs and archive/replace stale active docs when necessary.
- If an apparently required code path is already implemented, document that verification instead of duplicating it.

