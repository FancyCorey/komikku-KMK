# KMK Source Evaluation Tag Enrichment And Scoring Fix Plan

Status: implementation plan only.

Depends on:
- `docs/recommendations/KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md`

Goal:
Fix Source Evaluation so it evaluates sources using real manga metadata where possible, treats stale/low-confidence evidence honestly, and stops overrating or underrating sources because of missing list-page tags.

Non-goals:
- Do not add source-specific hacks for Elf Toon, KaliScan, or any other individual extension.
- Do not fetch chapter lists or page images.
- Do not merge Catalogue Fit and For You Search Compatibility back into one score.
- Do not make full installation/evaluation parallel; keep one-extension-at-a-time behavior.

## Current Problem To Fix

The audit found three root problems:

1. `SourceEvaluationRunner.probeAndScore()` samples `getPopularManga(1)` and `getLatestUpdates(1)` but does not call `getMangaDetails()` before scoring.
2. Most live rows are stale (`evaluation_version = 1` while current code expects version 2), but they can still be displayed as if current.
3. `SourceEvaluationScorer` counts positive-scoring candidates as `preferredTagMatchCount`, which makes mixed/noisy sources look stronger than they really are.

The exact fix is:

- enrich bounded Popular/Latest catalogue samples through `getMangaDetails()`;
- add a small set of persistent evidence counters for metadata enrichment and positive/negative candidate evidence;
- bump source-evaluation version so all old rows become stale;
- update sorting/display/eligibility to treat stale rows and metadata-sparse rows honestly;
- add tests for metadata-sparse false negatives and noisy/adult false positives.

## Versioning

Use the next private KMK-Recs version unless the current branch already selected a newer one.

Required source-evaluation version change:

- `SourceEvaluationKeys.CURRENT_VERSION`: bump from `2` to `3`.
- Reason: scoring semantics change from "list-entry catalogue fit" to "detail-enriched catalogue evidence with split positive/negative/metadata counters."

Required migration:

- Add migration `61.sqm` if unused.
- If migration 61 already exists, use the next available migration number.
- The migration must be additive and safe for existing installs.

## Database / Domain Model Changes

Files:

- `domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt`
- `data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq`
- `data/src/main/sqldelight/tachiyomi/migrations/<next>.sqm`
- `data/src/main/java/tachiyomi/data/taste/SourceEvaluationRepositoryImpl.kt`
- `app/src/test/java/eu/kanade/tachiyomi/data/database/KmkMigrationTest.kt`

Add these columns to `source_evaluation`:

```sql
detail_enrichment_attempt_count INTEGER NOT NULL DEFAULT 0;
detail_enrichment_success_count INTEGER NOT NULL DEFAULT 0;
metadata_candidate_count INTEGER NOT NULL DEFAULT 0;
positive_candidate_count INTEGER NOT NULL DEFAULT 0;
negative_candidate_count INTEGER NOT NULL DEFAULT 0;
explicit_preferred_group_hit_count INTEGER NOT NULL DEFAULT 0;
learned_positive_group_hit_count INTEGER NOT NULL DEFAULT 0;
blocked_candidate_count INTEGER NOT NULL DEFAULT 0;
adult_signal_candidate_count INTEGER NOT NULL DEFAULT 0;
```

Reasoning:

- `detail_enrichment_attempt_count`: how many candidates were sent to `getMangaDetails()`.
- `detail_enrichment_success_count`: how many detail calls returned successfully.
- `metadata_candidate_count`: how many final samples had usable genres/tags after enrichment.
- `positive_candidate_count`: how many final samples produced positive taste evidence.
- `negative_candidate_count`: how many final samples produced disliked/negative evidence.
- `explicit_preferred_group_hit_count`: explicit user-preferred tag hits only.
- `learned_positive_group_hit_count`: learned positive tag hits only.
- `blocked_candidate_count`: hard-blocked candidates after alias resolution.
- `adult_signal_candidate_count`: candidates containing adult/explicit/BL/GL/smut-like tags or titles before hard filtering.

Keep old columns for compatibility:

- `preferred_tag_match_count` should continue to be written, but after this change it should mirror `positive_candidate_count` for backward compatibility.
- `blocked_tag_match_count` should mirror `blocked_candidate_count`.
- `explicit_signal_count` and `ecchi_signal_count` can remain signal-level counts, but `adult_signal_candidate_count` gives a candidate-level risk counter.

Update:

- SQLDelight table schema.
- `upsert` insert/update argument list.
- repository mapper.
- `SourceEvaluation` data class constructor.
- all test helper builders that construct `SourceEvaluation`.

## Catalogue Detail Enrichment

File:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`

Add constants:

```kotlin
private const val CATALOGUE_DETAIL_ENRICH_CAP = 12
private const val CATALOGUE_DETAIL_ENRICH_TIMEOUT_MS = 10_000L
```

Add a private result model near `SourceEvaluationRunner`:

```kotlin
private data class CatalogueProbeSamples(
    val samples: List<Manga>,
    val detailAttempts: Int,
    val detailSuccesses: Int,
)
```

Add helper:

```kotlin
private suspend fun enrichCatalogueSamples(
    source: CatalogueSource,
    rawItems: List<SManga>,
): CatalogueProbeSamples
```

Behavior:

1. Deduplicate raw items by URL while preserving order.
2. Convert every raw item to domain manga.
3. Only call `getMangaDetails()` when the list-entry manga has no genre metadata.
4. Enrich at most `CATALOGUE_DETAIL_ENRICH_CAP` candidates per source evaluation.
5. Each detail call must be:
   - sequential;
   - wrapped in `withTimeoutOrNull(CATALOGUE_DETAIL_ENRICH_TIMEOUT_MS)`;
   - run from the existing IO context;
   - guarded with `runCatching`;
   - cancellation-aware (`CancellationException` must be rethrown).
6. A failed detail call keeps the original candidate.
7. A successful detail call replaces the candidate with `details.toDomainManga(source.id)`.

Modify `probeAndScore()`:

1. Collect raw `SManga` from Popular and Latest separately.
2. Keep `popularCount` and `latestCount` as raw list counts.
3. Merge Popular + Latest, dedupe by URL, and call `enrichCatalogueSamples()`.
4. Pass `detailAttempts` and `detailSuccesses` into `SourceEvaluationScorer.score()`.
5. Keep crash-quarantine markers around Popular/Latest calls as they already exist.
6. Add a phase if useful:
   - either reuse `Scoring`, or add `EnrichingDetails` to `SourceEvaluationQueueState.Phase`.
   - If adding a phase, update all enum display strings/tests.

Do not persist enriched manga into the app manga table during source evaluation. This is evidence-only, not library mutation.

## Scoring Fix

File:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt`

Change `score()` signature:

```kotlin
detailEnrichmentAttemptCount: Int = 0,
detailEnrichmentSuccessCount: Int = 0,
```

Add an internal pure breakdown helper, either inside `SourceEvaluationScorer` or as a separate file:

```kotlin
internal data class SourceEvaluationCandidateEvidence(
    val blocked: Boolean,
    val score: Double,
    val hasMetadata: Boolean,
    val hasExplicitPreferredGroup: Boolean,
    val hasLearnedPositiveGroup: Boolean,
    val hasNegativeGroup: Boolean,
    val hasAdultSignal: Boolean,
    val matchedGroups: Set<String>,
)
```

This helper must use the same alias normalization contract as `PersonalRecommendationScorer`:

- normalize raw genre with `normalizeTag()`;
- resolve through `aliasMap`;
- compare group keys against:
  - `tasteProfile.blockedGroups`;
  - `tasteProfile.explicitTagPreferences`;
  - `tasteProfile.learnedTagWeights`.

Rules:

- `blocked = any group in tasteProfile.blockedGroups`.
- `hasExplicitPreferredGroup = any explicit preference == TagPreference.PREFER.value`.
- `hasNegativeGroup = any explicit preference == TagPreference.DISLIKE.value OR BLOCK OR learned weight < 0`.
- `hasLearnedPositiveGroup = any learned weight > 0`.
- `score` should still use `PersonalRecommendationScorer.score()` to avoid diverging from For You ranking.
- `hasAdultSignal` should check both canonical group keys and raw lowercase tags/titles.

Adult/risk signals should include, at minimum:

```text
hentai, porn, pornographic, explicit, adult, adult content, erotic, erotica,
smut, uncensored, nsfw, 18+, xxx, yaoi, yuri, boys love, girls love,
shounen ai, shoujo ai
```

Keep ecchi separate:

```text
ecchi, mature, lewd, fan service, fanservice, risque, suggestive,
semi-explicit, sensual, sexy, sexual
```

Important: do not automatically treat all ecchi-only sources as porn/hentai. The user wants explicit porn/hentai blocking separate from ecchi.

### Revised Evidence Counts

Compute:

```kotlin
val metadataCandidateCount = evidence.count { it.hasMetadata }
val positiveCandidateCount = evidence.count { !it.blocked && it.score > 0.0 && (it.hasExplicitPreferredGroup || it.hasLearnedPositiveGroup) }
val negativeCandidateCount = evidence.count { it.hasNegativeGroup }
val blockedCandidateCount = evidence.count { it.blocked }
val explicitPreferredGroupHitCount = evidence.count { it.hasExplicitPreferredGroup }
val learnedPositiveGroupHitCount = evidence.count { it.hasLearnedPositiveGroup }
val adultSignalCandidateCount = evidence.count { it.hasAdultSignal }
```

Then:

- write `preferredTagMatchCount = positiveCandidateCount`;
- write `blockedTagMatchCount = blockedCandidateCount`.

### Revised Metadata Confidence

Use `metadataCandidateCount / sampleCount`, not raw `sampledTags` alone.

Thresholds can stay:

- `HIGH`: >= 70%
- `MODERATE`: >= 30%
- `LOW`: > 0%
- `UNKNOWN`: 0 or no samples

### Revised Fit Score

Do not let a source become `STRONG_FIT` only because a few broad positive tags appeared.

Suggested formula:

```kotlin
val metadataRatio = metadataCandidateCount.toDouble() / sampleCount
val positiveRatio = positiveCandidateCount.toDouble() / metadataCandidateCount
val negativeRatio = negativeCandidateCount.toDouble() / metadataCandidateCount
val blockedRatio = blockedCandidateCount.toDouble() / metadataCandidateCount
val adultRatio = adultSignalCandidateCount.toDouble() / metadataCandidateCount
```

Guard against zero denominators.

Suggested `recommendationFitScore`:

```text
base:
  positiveCandidateCount >= 8 or positiveRatio >= 0.35 -> 0.85
  positiveCandidateCount >= 4 or positiveRatio >= 0.20 -> 0.70
  positiveCandidateCount >= 2 or positiveRatio >= 0.10 -> 0.55
  else -> 0.35

penalties:
  blockedRatio >= 0.25 -> -0.35
  blockedRatio >= 0.10 -> -0.20
  negativeRatio >= 0.40 -> -0.25
  negativeRatio >= 0.25 -> -0.15
  adultRatio >= 0.35 -> -0.25
  adultRatio >= 0.20 -> -0.15
  metadataConfidence LOW -> -0.10
  metadataConfidence UNKNOWN -> do not call strong/worth, regardless of numeric score
```

Clamp final score to `[0.0, 1.0]`.

### Revised Verdicts

Use this order:

1. `ERROR` if `errorCount > 0 && sampleCount == 0`.
2. `EXPLICIT_HEAVY` if `explicitScore >= 0.5` OR `adultRatio >= 0.5` with several adult candidates.
3. `ECCHI_HEAVY` if `ecchiScore >= 0.5 && explicitScore < 0.3`.
4. `NEEDS_MANUAL_REVIEW` if `sampleCount > 0 && metadataConfidence == UNKNOWN`.
5. `NEEDS_MANUAL_REVIEW` if `metadataConfidence == LOW && positiveCandidateCount > 0`.
6. `STRONG_FIT` only if:
   - metadata confidence is `HIGH` or `MODERATE`;
   - final fit score >= 0.70;
   - blocked ratio is low;
   - adult ratio is not high.
7. `WORTH_TRYING` only if:
   - metadata confidence is not `UNKNOWN`;
   - final fit score >= 0.50;
   - blocked/adult risk is not high.
8. `WEAK` otherwise.

This makes Elf Toon-like sources become `NEEDS_MANUAL_REVIEW` or metadata-sparse until enriched, instead of confidently weak. It makes KaliScan-like sources lose strong status when blocked/adult/noisy candidate ratios are high.

## Stale Row Handling

Files:

- `app/src/main/java/exh/recs/evaluation/SourceEvaluationResultList.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt`
- KMR strings in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`

Add helper:

```kotlin
internal object SourceEvaluationDisplayPolicy {
    fun isCurrent(evaluation: SourceEvaluation, now: Long = System.currentTimeMillis()): Boolean
    fun state(evaluation: SourceEvaluation, now: Long = System.currentTimeMillis()): SourceEvaluationDisplayState
}
```

States:

```kotlin
CURRENT
OUTDATED_VERSION
EXPIRED
METADATA_SPARSE
ERROR
```

Rules:

- `OUTDATED_VERSION` if `evaluation.evaluationVersion < SourceEvaluationKeys.CURRENT_VERSION`.
- `EXPIRED` if `expiresAt != null && expiresAt <= now`.
- `METADATA_SPARSE` if current row has LOW or UNKNOWN confidence and sample count > 0.

UI:

- Show stale rows as "Outdated - reassess needed".
- Do not let old `strong_fit` or `worth_trying` rows sort above current rows.
- In `BEST_FIT` sort, current strong/worth rows outrank stale rows.
- In `FOR_YOU_COMPATIBILITY`, current fit remains separate, but stale source-evaluation rows should be visually obvious.

Candidate filtering:

- `skipAlreadyEvaluated` should not hide outdated rows forever.
- If `evaluationVersion < CURRENT_VERSION`, the source should be eligible for reassessment when `reEvaluateStale` is enabled.
- Consider treating version-stale rows as stale even if `expiresAt` is null.

## Source Recommendation Fit Eligibility

File:

- `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitEligibility.kt`

Update eligibility:

- `STRONG_FIT` and `WORTH_TRYING` current rows stay eligible.
- `NEEDS_MANUAL_REVIEW` with low/unknown metadata may be eligible for For You search compatibility if sample count >= 1.
- `EXPLICIT_HEAVY`, `ECCHI_HEAVY`, `ERROR`, `REJECTED` remain ineligible.
- Stale source-evaluation rows should not be treated as eligible current evidence. They should be reassessed first unless the explicit user action is "check compatibility anyway".

This prevents stale v1/v2 rows from feeding the recommendation-quality queue as if current.

## Strings / UI Wording

Add KMR strings:

- source_evaluation_outdated_reassess_needed
- source_evaluation_metadata_sparse
- source_evaluation_detail_enriched_count
- source_evaluation_detail_enrichment_failed_count
- source_evaluation_positive_negative_summary
- source_evaluation_verdict_review_explanation

Use concise row text. Phone UI must not become crowded:

- Primary row: source name, source/lang, verdict badge.
- Secondary row: catalogue fit + metadata confidence.
- Optional expanded detail: enrichment counts, positive/negative/blocked/adult counts.

Do not show raw exception traces in the row.

## Documentation Updates

Update:

- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/README.md`
- `docs/recommendations/KMK_SOURCE_EVALUATION_COMPLETE_AUDIT_2026_07_12.md` with an addendum linking to the implementation report after coding.
- `RECOMMENDATION_VERSIONING.md`

Create implementation report after coding:

- `docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_IMPLEMENTATION.md`

The report must include:

- files changed;
- migration number;
- scoring version bump;
- tests run;
- known limitations;
- whether real-device reassessment should be manually triggered after install.

## Tests

Add or update unit tests.

Required tests:

### `SourceEvaluationRunner` / enrichment

If direct runner testing is difficult, extract enrichment into a pure/testable helper:

- `SourceEvaluationCatalogueEnricher`
- fake `CatalogueSource`

Tests:

1. List entries without genres are enriched through `getMangaDetails()`.
2. List entries with genres do not spend enrichment budget.
3. Enrichment cap is respected.
4. Detail failure keeps original candidate and does not abort evaluation.
5. Timeout result keeps original candidate and increments attempt but not success.
6. Cancellation propagates.

### `SourceEvaluationScorerTest`

Add tests:

1. Metadata-sparse source with many samples and no tags returns `NEEDS_MANUAL_REVIEW`, not confident `WEAK`.
2. Detail-enriched samples with user-preferred tags can become `STRONG_FIT`.
3. Source with Action/Martial Arts plus blocked BL/GL aliases does not become `STRONG_FIT`.
4. `Adult`/`Smut`/`Mature`/`Ecchi` are counted separately enough to avoid false porn/ecchi merging.
5. `preferredTagMatchCount` mirrors `positiveCandidateCount` for compatibility.
6. `blockedTagMatchCount` mirrors `blockedCandidateCount`.
7. Version is `SourceEvaluationKeys.CURRENT_VERSION`.

### `GetTasteProfileTest`

Confirm:

1. `Yaoi` BLOCK becomes `boys_love` blocked group.
2. `Shounen Ai` candidate resolves to `boys_love`.
3. `Yuri`/`Shoujo Ai` resolve to `girls_love`.

### `SourceEvaluationResultListTest`

Confirm:

1. Current rows sort above outdated rows.
2. Outdated `strong_fit` does not outrank current `worth_trying`.
3. Metadata-sparse rows are not hidden as errors.

### Migration Tests

Update `KmkMigrationTest`:

1. Migrating from the previous DB version creates all new columns.
2. Existing rows default counts to zero.
3. Existing rows remain readable.

## Manual Real-Device Verification

After build/install:

1. Open Source Evaluation.
2. Clear or reassess stale source evaluations.
3. Evaluate a small batch that includes Elf Toon if available.
4. Confirm Elf Toon no longer becomes weak only because list entries had no tags.
5. Evaluate/reassess KaliScan if available.
6. Confirm BL/GL/adult/noisy signals prevent a confident strong label when they dominate.
7. Confirm current rows are version 3 in diagnostic DB.
8. Confirm old rows show as outdated or are queued for reassessment.
9. Confirm For You Search Compatibility remains a separate label.

## Acceptance Criteria

The implementation is acceptable only if:

- source evaluation uses bounded detail enrichment before scoring;
- stale v1/v2 rows no longer appear as current trustworthy verdicts;
- metadata-sparse evidence is labeled as inconclusive/review-needed rather than confidently weak;
- noisy/adult/blocked-tag-heavy sources cannot become strong only due to broad positive tags;
- catalogue fit and For You search compatibility remain separate;
- tests cover the false-negative and false-positive cases from the audit;
- all new user-visible strings use KMR resources;
- no private diagnostic DB files are committed.

