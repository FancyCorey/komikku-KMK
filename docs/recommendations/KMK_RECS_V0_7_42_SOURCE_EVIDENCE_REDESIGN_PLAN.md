# KMK-Recs v0.7.42 Source Evidence Redesign Plan

Date: 2026-07-11

Status: IMPLEMENTED and VERIFIED (2026-07-12) per decisions D1â€“D4 in Â§6. APK
`Komikku-v1.13.6-kmk.7.42-debug.apk`; spotlessApply/spotlessCheck/:app:testDebugUnitTest/assembleDebug
all passed on JDK 17.0.19. See `KMK_RECS_V0_7_42_SOURCE_EVIDENCE_REDESIGN_IMPLEMENTATION.md` for full
detail.

Version family: KMK-Recs `v0.7.42`.

Parent: `docs/recommendations/KMK_RECS_V0_7_41_DISCOVERY_POLICY_CORRECTIONS_AND_SOURCE_EVIDENCE_ROADMAP_PLAN.md` Â§7.

## 1. Purpose

v0.7.41 made the For You/rated-group candidate visibility contract internally consistent. This plan is
the required follow-on audit + design for Source Evaluation, so it stops pooling unrelated evidence into
one score and one misleading verdict label.

This document is audit + design only. No code changes are proposed to be made from this document alone.

## 2. Audit Findings (grounded in current code, read 2026-07-11)

### 2.1 `SourceEvaluationScorer.score()` pools three unrelated probes into one verdict

File: `app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt`

- `sampleCount = sampledTitles.size` is a single count built from `popularCount` items (up to 15),
  `latestCount` items (up to 10), and up to 3 `searchCount` probes (up to 8 each) â€” all appended into
  one `sampledTitles`/`sampledTags` list with no per-source-of-evidence separation.
- `qualityScore` (line 115) is a step function purely on `sampleCount`, so a source with 15 Popular
  items and zero successful searches scores identically to a source with 5 Popular + 10 search results.
- `recommendationFitScore` (line 131) is computed from `preferredTagMatchCount`/`blockedTagMatchCount`
  over the same pooled `sampledTags`, so it cannot say whether the fit signal came from the catalogue
  (Popular/Latest â€” what the source shows everyone) or from tag-targeted search (closer to what For You
  actually does).
- `verdict` (line 167) â€” `STRONG_FIT`/`WORTH_TRYING` â€” is a single label derived from this pooled mixture,
  then shown in Sources To Try and Recommendation Settings as if it were one coherent measurement.
- `likedTitleMatchCount` (line 109) is hardcoded to `0` with a comment admitting it is a "safe default" â€”
  a dead/always-zero field that should either be implemented or removed, not left silently inert.

### 2.2 `SourceRecommendationFitProbe` is already a mostly-separate retrieval-compatibility probe â€” but mislabeled

File: `app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt`

This probe already does the right *kind* of measurement for Â§7.2's "For You retrieval compatibility"
layer: it runs the actual `RecommendationQueryPlanner` plans against the source, scores results with the
real `PersonalRecommendationScorer`, and records `queryCount`/`querySuccessCount`/`visibleCandidateCount`/
`avgCandidateScore` into a separate `SourceRecommendationFit` record â€” already a different DB row from
`SourceEvaluation`. This is a solid foundation; it does not need to be rebuilt, only correctly labeled and
versioned (see 2.4, 2.5).

Problem: it is presented to the user as "Recommendations: Good" / "Recommendations: Great" (confirmed
strings, `i18n-kmk/src/commonMain/moko-resources/base/strings.xml:575-577`,
`source_evaluation_rec_quality_label` = `"Recommendations: %1$s"`). This literally claims the source's own
recommendation feature was measured. It was not â€” only whether the source can retrieve results for the
user's tags via KMK's own search-based query strategy. This is exactly the mislabeling Â§7.6 calls out.

### 2.3 Divergent, weaker taste-affinity resolution in `SourceEvaluationScorer`

`SourceEvaluationScorer.score()` builds `normalizedPreferred` from `tasteProfile.learnedTagWeights`
filtered to `> 0` only (lines 89-93), lowercased directly â€” no alias resolution via `GetTagAliases`, no
`explicitTagPreferences` (`TagPreference.PREFER`/`DISLIKE`), no `normalizeTag()` consistency with how
`PersonalRecommendationScorer.score()` resolves candidate groups. `PersonalRecommendationScorer` (used by
For You, group recommendations, and â€” already â€” `SourceRecommendationFitProbe`) is the actual production
taste-matching contract; `SourceEvaluationScorer` reimplements a simpler, divergent approximation for the
pooled catalogue+search score. This means the Popular/Latest scoring layer and the For You layer can
disagree about the same source for reasons that have nothing to do with the source itself.

### 2.4 `source_evaluation` already has staleness/versioning; `source_recommendation_fit` does not

- `source_evaluation.sq` has `evaluation_version INTEGER NOT NULL` and `expires_at INTEGER` columns.
  `domain/.../SourceEvaluation.kt` defines `SourceEvaluationKeys.CURRENT_VERSION = 1`.
  `SourceEvaluationCandidateFilter.isStale(evaluations, now)` (line 171) already treats a record stale
  when `expiresAt <= now` OR `evaluationVersion < CURRENT_VERSION` â€” this exists and works today.
- `SourceEvaluationUpdatePolicy` already detects extension-version-bump reassessment need
  (`UPDATED`/`NOT_UPDATED`/`UPDATE_UNKNOWN`/`NEVER_EVALUATED`) using `extensionVersionCode` stored on the
  evaluation (added v0.7.4). Already wired into the UI ("N evaluated extension(s) have updates").
- Taste-profile-drift reassessment prompt already exists (v0.7.34, `sourceEvaluationLastRunRatingCount`).
- **Gap:** `source_recommendation_fit.sq` has **no** `evaluation_version` or `expires_at` column at all
  (confirmed by reading the full schema â€” 24 columns, neither present). A `SourceRecommendationFit` record
  can never be detected as stale by scoring-logic version or extension update the way `SourceEvaluation`
  already can. This is the concrete, uncontroversial migration-worthy gap â€” the rest of Â§7's "staleness"
  requirement is largely infrastructure that already exists for the catalogue-evaluation side and needs to
  be extended to the retrieval-compatibility side, not invented from scratch.

### 2.5 Naming: "Strong Fit" pools catalogue fit; retrieval compatibility uses a source-native framing

Confirmed strings (`i18n-kmk/.../base/strings.xml:480-485`): `source_evaluation_verdict_strong_fit` =
"Strong Fit", `..._worth_trying` = "Worth Trying" (pooled catalogue+search verdict from 2.1), alongside the
separate `source_evaluation_rec_quality_*` = "Recommendations: Good/Great/â€¦" strings from 2.2. Users
currently see two different labels for two different measurements, but neither name tells them which
layer they are looking at, and the second implies more than KMK actually measured.

## 3. Design (per parent plan Â§7, made concrete against the audit above)

### A. Catalogue fit (Popular/Latest only)

- `SourceEvaluationScorer` splits its single pooled sample into two independent groups: catalogue
  samples (`popularCount` + `latestCount` items) and search samples (existing `searchCount`/
  `searchSuccessCount` probes), each with its own tag-match/quality/confidence computation.
- Catalogue-fit score reflects only Popular/Latest metadata match against taste â€” no search involved.
- Rename the existing pooled `recommendationFitScore`/`verdict` fields to make clear they describe
  catalogue fit once search is split out (see Â§5 versioning â€” this is a scoring semantics change, not
  just a rename, so `evaluation_version` must bump).

### B. For You retrieval compatibility (already `SourceRecommendationFitProbe`)

- Keep `SourceRecommendationFitProbe` as-is structurally (it is already query-plan-driven and uses the
  real `PersonalRecommendationScorer`). Re-label its output in the UI as "For You search compatibility"
  or similarly explicit wording â€” never "Recommendations: X" â€” so users cannot read it as source-native
  recommendation quality. Update all `source_evaluation_rec_quality_*` strings and any surrounding
  copy/comments that use "Recommendations:" framing.
- Consider whether `SourceEvaluationScorer`'s existing `searchCount`/`searchSuccessCount` probe (a
  cruder, taste-tag-driven search already run during the same evaluation pass, per 2.1) should be
  retired in favor of relying entirely on `SourceRecommendationFitProbe` for the search-compatibility
  signal, to avoid running two different search-based probes with two different taste-matching
  implementations against the same source. Needs a decision before implementation (see Â§7 open questions).

### C. Shared taste affinity contract

- Extract the taste-matching logic already used correctly by `PersonalRecommendationScorer` (alias
  resolution, `normalizeTag()`, `explicitTagPreferences` PREFER/DISLIKE, `learnedTagWeights`) into a form
  both For You and `SourceEvaluationScorer`'s catalogue-fit computation can call, so a source's catalogue
  tags are matched against taste exactly the way a For You candidate's tags would be. `SourceEvaluationScorer`
  currently has no `aliasMap` parameter at all â€” it never resolves aliases; `SourceRecommendationFitProbe`
  already receives `aliasMap` via `GetTagAliases` and should be the reference implementation to converge on.
- Remove or actually implement the always-zero `likedTitleMatchCount` field â€” do not ship a field that
  exists only to always read 0.

### D. Metadata confidence

- Track, per catalogue-fit computation, how many sampled items had usable genre/tag metadata versus how
  many were metadata-empty, and surface a confidence tier (e.g. High/Moderate/Low) distinct from the fit
  score itself â€” missing metadata must not silently read as "no matching tags = low fit."
  `SourceRecommendationFitProbe` already tracks an analogous signal (`weakMetadataCandidateCount`,
  `enrichedCandidateCount`) for the search-compatibility side (v0.7.13); the catalogue-fit side has no
  equivalent today and should follow the same shape for consistency.

### E. Staleness / reassessment versioning

- Add `evaluation_version INTEGER NOT NULL DEFAULT 0` and `expires_at INTEGER` to
  `source_recommendation_fit` (new SQLDelight migration; exact number TBD at implementation time â€” check
  the current highest `.sqm` file first, do not hardcode a number in this plan). Introduce
  `SourceRecommendationFit.CURRENT_VERSION` mirroring `SourceEvaluationKeys.CURRENT_VERSION`, and extend
  `SourceEvaluationCandidateFilter.isStale(...)`-equivalent logic (or a parallel helper) to cover fit
  records the same way it already covers evaluation records.
- Bump `SourceEvaluationKeys.CURRENT_VERSION` to `2` once the catalogue-fit scoring semantics in Â§3.A
  change, so old `STRONG_FIT`/`WORTH_TRYING` verdicts computed under the pooled-scoring rules are
  correctly treated as stale and eligible for "Reassess sources" rather than silently reinterpreted under
  new rules with an old label.
- Extension-version-bump detection (`SourceEvaluationUpdatePolicy`) and taste-profile-drift reassessment
  already work correctly today and need no redesign â€” only confirmation that the split scoring functions
  in Â§3.A/Â§3.B still populate the fields those policies read (`extensionVersionCode`, evaluation counts).

### F. Naming / UI

- Introduce distinct, explicit labels: "Catalogue fit" (Popular/Latest match), "For You search
  compatibility" (the renamed `SourceRecommendationFitProbe` output), reserving any future "Recommendation
  quality" language strictly for real outcome-based evidence (Â§8 of the parent plan, not in scope here).
- Update all four call sites currently reading "Strong Fit"/"Worth Trying"/"Recommendations: X" in
  `SourceEvaluationScreen.kt`, Sources To Try suggestion reason strings
  (`rec_suggestion_reason_evaluated_strong_fit` etc.), and any settings copy that implies "Strong Fit"
  already accounts for search compatibility.

## 4. Exact Target Files (from the audit, not guessed)

```text
app/src/main/java/exh/recs/evaluation/SourceEvaluationScorer.kt         (Â§3.A, Â§3.C, Â§3.D)
app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt         (Â§3.A/B wiring â€” probeAndScore())
app/src/main/java/exh/recs/evaluation/SourceRecommendationFitProbe.kt   (Â§3.B â€” relabel only, logic largely stable)
app/src/main/java/exh/recs/evaluation/SourceEvaluationCandidateFilter.kt (Â§3.E â€” isStale() extension)
app/src/main/java/exh/recs/evaluation/SourceEvaluationUpdatePolicy.kt   (verify unaffected by Â§3.A rename)
app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt         (Â§3.F â€” label call sites)
domain/src/main/java/tachiyomi/domain/taste/model/SourceEvaluation.kt          (CURRENT_VERSION bump, Â§3.A field changes)
domain/src/main/java/tachiyomi/domain/taste/model/SourceRecommendationFit.kt   (new version/expiry fields)
data/src/main/sqldelight/tachiyomi/data/source_evaluation.sq            (column changes if Â§3.A splits fields)
data/src/main/sqldelight/tachiyomi/data/source_recommendation_fit.sq    (new migration: version + expiry)
i18n-kmk/src/commonMain/moko-resources/base/strings.xml                 (Â§3.F relabeling)
app/src/main/java/exh/recs/PersonalRecommendationScorer.kt              (Â§3.C â€” reference contract, read-only unless extraction requires a shared home)
```

## 5. Explicit Non-Goals (unchanged from parent plan Â§7, restated for this plan's scope)

- Website-specific recommendation adapters.
- Universal publication-date, remote chapter-count, or image-quality claims.
- Background crawling of whole catalogues.
- Evaluating every source recommendation for every manga.
- New automatic preference changes from browsing behavior.
- Local outcome learning (exposures/opens/dismissals) â€” parent plan Â§8, a later phase after this one.

## 6. Decisions (resolved 2026-07-12 â€” binding for implementation)

### D1 (resolves former open question 1): Remove `SourceEvaluationScorer`'s own search probe

`SourceEvaluationRunner.probeAndScore()` currently runs its own up-to-3 search calls (weaker,
divergent taste matching, Â§2.3) purely to feed the pooled verdict, and then `SourceRecommendationFitProbe`
runs a second, better-built search probe for eligible sources. Running two different search
implementations against the same source is both wasteful and internally inconsistent.

**Decision:** remove the scorer's search probe entirely (`searchCount`/`searchSuccessCount` probing loop
in `probeAndScore()`). Catalogue fit (Popular/Latest only) becomes the sole basis for the initial
`SourceEvaluation` verdict; `SourceRecommendationFitProbe` becomes the only search-based signal in the
system.

**Consequence requiring a new signal:** gating `SourceRecommendationFitProbe` eligibility on the catalogue
verdict alone would systematically under-promote sources whose Popular/Latest pages carry no genre
metadata (the same problem v0.7.13 already fixed on the search side via enrichment â€” catalogue samples get
no equivalent enrichment pass). To avoid silently losing recall, `SourceEvaluation` gains a
`catalogueMetadataConfidence` field (`HIGH`/`MODERATE`/`LOW`/`UNKNOWN`, based on the fraction of sampled
catalogue items with non-empty genre). `SourceRecommendationFitEligibility.check()` is rewritten to admit a
source when catalogue verdict is `STRONG_FIT`/`WORTH_TRYING` **or** confidence is `LOW`/`UNKNOWN`
(fail-open when catalogue evidence is inconclusive), while still excluding
`EXPLICIT_HEAVY`/`ECCHI_HEAVY`/`ERROR`/`REJECTED` and requiring at least one successful catalogue sample.

### D2 (resolves former open question 2): Migration numbers

Verified against the repo (`data/src/main/sqldelight/tachiyomi/migrations/*.sqm`): 58 files exist, highest
is `58.sqm`, no `59.sqm` exists. Following this repo's one-change-per-`.sqm` convention (56/57/58 each did
exactly one thing):

- **Migration 59**: `source_evaluation` gains `catalogue_metadata_confidence TEXT NOT NULL DEFAULT 'unknown'`
  (backing D1's new signal).
- **Migration 60**: `source_recommendation_fit` gains `evaluation_version INTEGER NOT NULL DEFAULT 0` and
  `expires_at INTEGER` (Â§3.E staleness parity).

Re-verify the highest `.sqm` number immediately before implementation in case other local work has added
migrations since this plan was written.

### D3 (resolves former open question 3): Version bumps are decoupled, each tied to its own table's change

- `SourceEvaluationKeys.CURRENT_VERSION`: `1 â†’ 2`. Catalogue-fit computation semantics genuinely change
  (no longer pooled with search per D1, plus the D4 taste-matching change), so every existing
  `STRONG_FIT`/`WORTH_TRYING` verdict must be treated as stale. `SourceEvaluationCandidateFilter.isStale()`
  already compares `evaluationVersion < CURRENT_VERSION` â€” this triggers correctly for free once the
  constant bumps, no other change needed there.
- `SourceRecommendationFit` gets its own new `CURRENT_VERSION = 1` (first versioned release for that
  table). Migration 60 defaults existing rows to `0`, so they are automatically stale under a new
  `isStale()`-equivalent check for fit records â€” no manual backfill, no coupling to
  `SourceEvaluationKeys.CURRENT_VERSION`. The two tables represent independent evidence types and must be
  able to version independently in the future.

### D4 (resolves former open question 4): No new shared object â€” reuse `PersonalRecommendationScorer.score()` directly

`SourceRecommendationFitProbe` already does this correctly for search results:
`smanga.toDomainManga(source.id)` â†’ `PersonalRecommendationScorer.score(candidate, tasteProfile, aliasMap)`.

**Decision:** make catalogue-fit scoring do the same per sampled Popular/Latest item, replacing the ad-hoc
`normalizedPreferred`/`normalizedBlocked` set-membership logic in `SourceEvaluationScorer` (Â§2.3). This
requires threading `aliasMap` (from the already-injected `GetTagAliases` in `SourceEvaluationRunner`) into
`SourceEvaluationScorer.score(...)` as a new parameter, and changes blocked-tag handling from a soft
`fitPenalty` to `PersonalRecommendationScorer`'s real hard block per item â€” aligning catalogue fit with how
For You already treats blocked tags. No new shared object is introduced; this is the smallest diff that
removes the taste-matching divergence, consistent with "avoid introducing another parallel policy system."

### Consequences for Â§3/Â§4 requiring no further decision

- `searchCount`/`searchSuccessCount`/`searchReliabilityScore` columns remain in `source_evaluation` for
  backward-compatible reads of historical rows, but the runner stops populating them meaningfully
  (`SourceEvaluationScorer.score()` always writes `0`/`0`/neutral for these three going forward, with a
  KDoc note marking them superseded by `catalogueMetadataConfidence` + `SourceRecommendationFit`). No
  column removal â€” SQLite `DROP COLUMN` support/SQLDelight compatibility is not worth the risk for a
  three-column deprecation.
- `likedTitleMatchCount` (Â§2.1, always-zero dead field): removed from computation entirely, left as a
  written-as-`0` column for the same backward-compatibility reason as above, with an updated KDoc rather
  than a schema change.

## 7. Entry Criteria Status

Per parent plan Â§7 entry criteria: `SourceEvaluationRunner`, `SourceEvaluationScorer`,
`SourceRecommendationFitProbe`, `source_evaluation.sq`, and `source_recommendation_fit.sq` have been
audited (Â§2) against the target model, and all four open design questions were resolved as decisions D1â€“D4
(Â§6) on 2026-07-12. This plan is APPROVED for implementation per `DOCUMENTATION_RULES.md`.

