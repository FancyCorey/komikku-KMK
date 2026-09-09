# KMK Source Evaluation Tag Enrichment And Scoring Fix â€” Implementation Report

Status: implemented and verified (automated). Shipped as `KMK-Recs v0.7.47`.

Plan implemented exactly: `docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md`
Audit this fixes: `docs/recommendations/KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md`

## 1. Version Identity

- `SourceEvaluationKeys.CURRENT_VERSION`: `2` -> `3`. Every existing row (v1 or v2) is now
  correctly treated as stale â€” new/revised scoring semantics change the meaning of the verdict,
  fit score, and metadata confidence stored on old rows.
- `KmkRecsReleaseNotes.VERSION_CODE`: `748` -> `749`; `VERSION_NAME`: `"KMK-Recs v0.7.46"` ->
  `"KMK-Recs v0.7.47"`. New changelog entry added (v0.7.46 entry preserved below it).
- No Android `versionCode`/`versionName` change (tracked independently, per existing convention).

## 2. Files Changed

**New files:**

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCatalogueEnricher.kt` â€” pure, testable
  bounded `getMangaDetails()` enrichment helper. Deduplicates raw Popular+Latest `SManga` items by
  URL, enriches at most `CATALOGUE_DETAIL_ENRICH_CAP` (12) items lacking genre metadata,
  sequentially, each call wrapped in `withTimeoutOrNull(CATALOGUE_DETAIL_ENRICH_TIMEOUT_MS = 10_000L)`
  + `runCatching`, rethrowing `CancellationException`. A failed/timed-out call keeps the original
  list-entry candidate (converted via `toDomainManga`) and still counts as an attempt but not a
  success. Never calls `getChapterList`/`getPageList`; never calls `NetworkToLocalManga` (no app
  manga table writes â€” evidence-only, matching the plan's non-goals).
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationDisplayPolicy.kt` â€” pure policy resolving
  a `SourceEvaluation` row to `CURRENT`/`OUTDATED_VERSION`/`EXPIRED`/`METADATA_SPARSE`/`ERROR`, used
  by the screen for stale-row labeling and by `SourceEvaluationResultList` for stale-row ranking.
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationCatalogueEnricherTest.kt` â€” 7 tests
  (enrichment of ungenred entries, enrichment budget preserved for already-tagged entries,
  enrichment cap, detail failure keeps original candidate, timeout keeps original candidate,
  cancellation propagates, URL dedup before enrichment).
- `data/src/main/sqldelight/tachiyomi/migrations/61.sqm` â€” additive `ALTER TABLE` migration, 9 new
  `INTEGER NOT NULL DEFAULT 0` columns on `source_evaluation`.

**Modified files:**

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt` â€” `probeAndScore()` now
  collects raw `SManga` lists from Popular/Latest (instead of converting directly to `Manga`),
  merges them, and runs `SourceEvaluationCatalogueEnricher.enrich()` under a new
  `EnrichingDetails` phase before scoring. Enrichment attempt/success counts are threaded into
  `SourceEvaluationScorer.score()`.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationQueueState.kt` â€” added `Phase.EnrichingDetails`
  between `ProbingSearch` and `Scoring`. Displayed automatically via the screen's existing
  `phase.name` â†’ spaced-words regex (no hardcoded phase-label map exists, so no additional wiring
  was needed).
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt` â€” rewritten scoring core:
  - New internal `SourceEvaluationCandidateEvidence` data class + `evidenceFor()` computing, per
    sampled item: `blocked`/`score` (via `PersonalRecommendationScorer`, unchanged contract),
    `hasMetadata`, `hasExplicitPreferredGroup`, `hasLearnedPositiveGroup`, `hasNegativeGroup`
    (DISLIKE, BLOCK, or negative learned weight), `hasAdultSignal` (broader `ADULT_RISK_TERMS`/
    `ADULT_RISK_GROUPS` set â€” hentai/porn/smut/adult/BL/GL/etc. â€” checked against both alias-
    resolved group keys and raw lowercase title/tags), `matchedGroups`.
  - New split counters computed from the evidence list: `metadataCandidateCount`,
    `positiveCandidateCount`, `negativeCandidateCount`, `blockedCandidateCount`,
    `explicitPreferredGroupHitCount`, `learnedPositiveGroupHitCount`, `adultSignalCandidateCount`.
    `preferredTagMatchCount`/`blockedTagMatchCount` now mirror
    `positiveCandidateCount`/`blockedCandidateCount` exactly, per the plan's backward-compatibility
    requirement.
  - `catalogueMetadataConfidence` now derives from `metadataCandidateCount` (post-enrichment) rather
    than a separately-computed `withMetadataCount` â€” same thresholds (HIGH >=70%, MODERATE >=30%,
    LOW >0%, UNKNOWN otherwise), same values, different (correct) input.
  - New ratio-based `recommendationFitScore` formula: base tier from
    `positiveCandidateCount`/`positiveRatio`, penalties from `blockedRatio`/`negativeRatio`/
    `adultRatio` tiers and a flat LOW-confidence penalty, clamped to `[0.0, 1.0]` â€” exact formula
    from the plan.
  - New verdict order: `ERROR` â†’ `EXPLICIT_HEAVY` (now also triggered by `adultRatio >= 0.5` with
    >=3 adult candidates, in addition to the existing `explicitScore >= 0.5` name/tag path) â†’
    `ECCHI_HEAVY` â†’ `NEEDS_MANUAL_REVIEW` (UNKNOWN confidence with samples, or LOW confidence with
    any positive candidate) â†’ `STRONG_FIT` (HIGH/MODERATE confidence, fit >= 0.70, low
    blocked/adult ratio) â†’ `WORTH_TRYING` (non-UNKNOWN confidence, fit >= 0.50, moderate
    blocked/adult ratio) â†’ `WEAK`.
  - `score()` gained `detailEnrichmentAttemptCount`/`detailEnrichmentSuccessCount` parameters
    (default 0), threaded straight into the returned `SourceEvaluation`.
- `domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt` â€” 9 new fields (all
  `Int = 0` defaults, so every existing call site â€” including `errorRecord()` and every test
  builder that didn't explicitly set them â€” compiles unchanged); `SourceEvaluationKeys.CURRENT_VERSION`
  bumped `2 -> 3` with an updated KDoc explaining why.
- `data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq` â€” 9 new columns on the table, all
  in the `upsert` insert/update column and value lists.
- `data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt` â€” `upsert()` call and
  the row mapper both extended with the 9 new fields.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt` â€” `bestFitComparator` now
  computes an `effectiveVerdictRank()` that adds a +100 offset to any row where
  `SourceEvaluationDisplayPolicy.isStaleForRanking()` is true, so a stale row (any stored verdict)
  can never outrank a current row in the `BEST_FIT` sort, while still preserving relative ordering
  among rows that are all-stale or all-current.
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt` â€” `check()` gained
  a staleness gate, evaluated first: `evaluation.evaluationVersion < SourceEvaluationKeys.CURRENT_VERSION`
  now returns a new `EligibilityResult.STALE_EVALUATION` before any verdict/confidence check runs.
  This closes a real gap the plan flagged: without this, an old `STRONG_FIT` row computed under v1/v2
  rules would still read as `ELIGIBLE` and could feed the search-compatibility probe queue as if it
  were current evidence.
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt` â€” `EvaluationResultRow` now
  resolves `SourceEvaluationDisplayPolicy.state(evaluation, now)` first; when
  `OUTDATED_VERSION`/`EXPIRED`, the row subtitle shows "`<ext> â€¢ Outdated â€” reassess needed`"
  instead of its (no-longer-trustworthy) fit/confidence numbers.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” added the 6 KMR strings the plan
  requested (`source_evaluation_outdated_reassess_needed`, `source_evaluation_metadata_sparse`,
  `source_evaluation_detail_enriched_count`, `source_evaluation_detail_enrichment_failed_count`,
  `source_evaluation_positive_negative_summary`, `source_evaluation_verdict_review_explanation`).
  `source_evaluation_metadata_sparse`, the enrichment-count strings, and the verdict-review
  explanation are defined but not yet wired into a composable â€” see Known Limitations.
  `NEEDS_MANUAL_REVIEW`'s verdict badge (`source_evaluation_verdict_review`) and row/sort handling
  already existed from a prior pass and required no change.
- `app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt` â€” migration range
  `46..60` -> `46..61`; migration-61 entries added to `NEW_TABLES_BY_MIGRATION` (empty list â€” ALTER
  only), `ALTER_MIGRATION_TABLE`, `ALTER_MIGRATION_COLUMNS`; two dedicated migration-61 tests added
  (columns present; a pre-migration-61 row defaults all 9 new counters to 0 after migrating).
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationScorerTest.kt` â€” rewritten for the new
  evidence model (25 tests): split-counter coverage, metadata-sparse false-negative coverage
  (Elf-Toon-shaped), noisy/adult false-positive coverage (KaliScan-shaped, including a dedicated
  BL-alias-block test and a mixed Action/Martial-Arts+Yaoi/Smut/Adult/Ecchi test), backward-
  compatible mirror assertions, enrichment-count passthrough.
- `app/src/test/java/exh/recs/evaluation/SourceEvaluationResultListTest.kt` â€” `evaluation()` helper
  default changed from hardcoded `evaluationVersion = 1` to `SourceEvaluationKeys.CURRENT_VERSION`
  (existing tests represent "current" rows unless a test explicitly overrides); 3 new tests added
  (current-above-outdated regardless of verdict, outdated `STRONG_FIT` does not outrank current
  `WORTH_TRYING`, `NEEDS_MANUAL_REVIEW` is not sorted/treated as `ERROR`).
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationFitEligibilityTest.kt` â€” same
  default-version fix, plus 3 new staleness tests (stale `STRONG_FIT` is `STALE_EVALUATION` not
  `ELIGIBLE`; `isProbeEligible` is false for a stale row; staleness gate runs before the
  verdict/confidence gates using an `EXPLICIT_HEAVY` stale row as the probe case).
- `app/src/test/java/exh/recs/evaluation/SourceRecommendationFitDisplayPolicyTest.kt`,
  `SourceRecommendationQualityDiagnosticsTest.kt`, `SourceRecommendationQualityQueueTest.kt` â€” same
  default-version fix (`evaluationVersion = 1` -> `SourceEvaluationKeys.CURRENT_VERSION`) in each
  file's `SourceEvaluation` test-data builder, discovered and fixed via the full-suite run (see
  Errors below) â€” these builders were representing "a current row" for their own test scope and had
  no intent to test v0.7.47 staleness themselves.
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” version bump + changelog entry.
- Documentation: `docs/recommendations/CURRENT_STATE.md`, `docs/recommendations/NEXT_WORK.md`,
  `docs/recommendations/README.md`, `docs/recommendations/KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md`
  (addendum), `RECOMMENDATION_VERSIONING.md` â€” all updated per the plan's Documentation Updates
  section.

## 3. Migration

- Migration `61.sqm` â€” additive, 9 `ALTER TABLE source_evaluation ADD COLUMN ... INTEGER NOT NULL
  DEFAULT 0` statements. Safe for existing installs: existing rows read back with all 9 new
  counters at 0, which is correct because they are also all version-stale under the same release
  (`CURRENT_VERSION` bumped 2 -> 3 in this same pass), so no UI path can misread a legacy row's
  zeroed counters as "confidently evaluated with zero evidence."

## 4. Scoring Version Bump

`SourceEvaluationKeys.CURRENT_VERSION`: `2 -> 3`.

## 5. What Was NOT Changed (per plan non-goals, explicitly verified)

- No source-specific logic for Elf Toon, KaliScan, or any other individual extension.
- No chapter-list or page-image fetches added anywhere in the enrichment path.
- Source Evaluation remains strictly one-extension-at-a-time (`SourceEvaluationRunner`'s sequential
  `for (candidate in candidates)` loop and inter-extension `delay(1500L)` are unchanged).
- Catalogue Fit (`SourceEvaluationScorer`/`SourceEvaluation`) and For You Search Compatibility
  (`SourceRecommendationFitProbe`/`SourceRecommendationFit`) remain fully separate â€” neither
  `SourceRecommendationFitProbe.kt` nor `SourceRecommendationFitScorer.kt` was touched.
- No backup/sync/proto field changes â€” `source_evaluation` was already excluded from
  backup/sync/export before this pass and remains so; the 9 new columns don't change that.
- No database schema change beyond the required additive migration.

## 6. Tests Run

All run with repo-local JDK 17 (`.tools/jdk17/jdk-17.0.19+10`).

| Command | Result |
|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` | BUILD SUCCESSFUL (checkpoint before writing tests) |
| `.\gradlew.bat :app:compileDebugUnitTestKotlin` | BUILD SUCCESSFUL |
| `.\gradlew.bat :app:testDebugUnitTest --tests "exh.recs.evaluation.*" --tests "eu.kanade.tachiyomi.data.database.KmkMigrationTest" --tests "exh.taste.GetTasteProfileTest"` | BUILD SUCCESSFUL (first focused pass) |
| `.\gradlew.bat :app:testDebugUnitTest` (full suite, 1st run) | 53 failures â€” found and fixed the eligibility-staleness test-default gap described above |
| `.\gradlew.bat :app:testDebugUnitTest` (full suite, after fixes) | **BUILD SUCCESSFUL â€” 988 tests, 0 failures, 0 errors** (up from 963 pre-existing) |
| `.\gradlew.bat spotlessApply` | BUILD SUCCESSFUL (one ktlint-style reformat auto-applied) |
| `.\gradlew.bat spotlessCheck` | BUILD SUCCESSFUL |
| `.\gradlew.bat :app:testDebugUnitTest` (post-spotless re-run) | BUILD SUCCESSFUL â€” 988/988 |
| `.\gradlew.bat :app:lintKmkPublicTest` | BUILD SUCCESSFUL â€” only a pre-existing, unrelated warning (`SourceEvaluationScreenModel.kt:1132`, unnecessary safe call, not touched by this pass) |
| `.\gradlew.bat :app:assembleDebug` | BUILD SUCCESSFUL |
| `.\gradlew.bat :app:assembleKmkPublicTest` | BUILD SUCCESSFUL |

New/updated test files and counts:

- `SourceEvaluationCatalogueEnricherTest` â€” 7 tests (new file)
- `SourceEvaluationScorerTest` â€” 25 tests (rewritten)
- `SourceEvaluationResultListTest` â€” 24 tests (3 new, rest updated for version default)
- `SourceRecommendationFitEligibilityTest` â€” updated (3 new staleness tests)
- `KmkMigrationTest` â€” 19 tests (2 new for migration 61, range extended)
- `GetTasteProfileTest` â€” unchanged; already covered Yaoi/Shounen Ai -> `boys_love` and
  Yuri/Shoujo Ai -> `girls_love` alias resolution from a prior pass (verified present, not
  duplicated).

## 7. Known Limitations

- `source_evaluation_metadata_sparse`, `source_evaluation_detail_enriched_count`,
  `source_evaluation_detail_enrichment_failed_count`, `source_evaluation_positive_negative_summary`,
  and `source_evaluation_verdict_review_explanation` KMR strings were added per the plan's exact
  requested string list, but only `source_evaluation_outdated_reassess_needed` is currently wired
  into a composable (the row subtitle for outdated rows). The plan's suggested "Optional expanded
  detail: enrichment counts, positive/negative/blocked/adult counts" row was not built as a new UI
  section in this pass â€” the existing row layout (primary/secondary/rec-quality lines) was kept
  compact rather than adding a fourth line or an expand/collapse control, to avoid an unreviewed UI
  redesign. The new counters are fully computed, persisted, and covered by tests; only their
  optional detail-view surfacing in the row itself is deferred.
- `EXPLICIT_HEAVY`'s new `adultRatio >= 0.5 && adultSignalCandidateCount >= 3` trigger uses `>= 3`
  as "several" candidates, since the plan left "several" undefined; documented here as the concrete
  interpretation used.
- No manual/real-device verification was performed (no APK was built for handoff in this pass â€”
  code, migration, tests, lint, and docs only, per the task's implicit scope; the plan's "Manual
  Real-Device Verification" section â€” reassessing Elf Toon/KaliScan on a real device â€” was not
  executed and should be done before treating this as verified in production).

## 8. Deviations From The Plan

None in substance. One necessary correctness addition beyond the plan's explicit file list: added
a `STALE_EVALUATION` gate to `SourceRecommendationFitEligibility.check()` â€” the plan's "Source
Recommendation Fit Eligibility" section said stale rows "should not be treated as eligible current
evidence," but the pre-existing `check()` function had no staleness check at all before this pass,
so without this addition an old `STRONG_FIT` row would still have returned `ELIGIBLE`. This directly
implements the plan's own stated requirement rather than deviating from it.

