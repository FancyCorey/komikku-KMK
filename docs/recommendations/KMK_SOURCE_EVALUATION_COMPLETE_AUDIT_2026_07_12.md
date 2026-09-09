# KMK Source Evaluation Complete Audit - 2026-07-12

Status: audit only, no implementation changes.

**Addendum (v0.7.47):** The fix architecture recommended below was implemented in
`KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_PLAN.md` and shipped as
`KMK-Recs v0.7.47`. See
`docs/recommendations/KMK_SOURCE_EVALUATION_TAG_ENRICHMENT_AND_SCORING_FIX_IMPLEMENTATION.md` for
the full implementation report (files changed, migration 61, scoring-version bump 2 -> 3, tests,
known limitations). This audit document is retained as historical background/evidence, not as a
description of current behavior.

Scope:
- How KMK can actually obtain manga tags from extensions.
- How current source evaluation and recommendation scoring use those tags.
- What the live tablet database shows about the current app state.
- What is currently working, misleading, stale, or missing.
- What should be used as the reference frame for the next implementation plan.

Private data note:
The local diagnostic SQLite snapshots pulled from `app.komikku.dev` are private user data. They were used only to measure system behavior. Do not commit them, publish them, or paste full contents into public docs.

## Executive Summary

The current source-evaluation system is not reliable enough to decide whether a non-installed source is truly good or weak for the user's taste.

The main reason is not that tag matching is impossible. Komikku already has a natural way to obtain full tags: call `source.getMangaDetails(smanga)`, the same method used when opening manga details, updating library metadata, migration, and recommendation candidate enrichment.

The problem is that `SourceEvaluationRunner.probeAndScore()` currently samples Popular and Latest pages and converts their list entries directly to domain manga:

- `source.getPopularManga(1)` -> `page.mangas.take(15)` -> `toDomainManga(source.id)`
- `source.getLatestUpdates(1)` -> `page.mangas.take(10)` -> `toDomainManga(source.id)`

It does not do a bounded `getMangaDetails()` enrichment pass for those catalogue samples before scoring them. Many extensions return titles and thumbnails in Popular/Latest but no genres/tags until the manga detail page is fetched. This means the app is often evaluating "does the list page expose tags?" instead of "does this source contain manga matching my taste?"

The live database confirms this:

- `source_evaluation` rows: 416
- Rows with sampled tags: 29
- Rows missing sampled tags: 387
- Tagged percentage across all rows: 6.97%
- Non-error rows: 312
- Non-error rows with sampled tags: 29
- Tagged percentage among non-error rows: 9.29%

That explains why sources that look good when manually opened can be marked weak, and why some noisy/adult sources can be marked strong or worth trying when they happen to expose broad tags on list pages.

## Live Diagnostic Evidence

Diagnostic snapshot:
- Database: `tachiyomi_kmk_debug_diagnostic_latest.db`
- App ID: `app.komikku.dev`
- Device: connected Android tablet via ADB at time of audit.

### Version Staleness

The live source-evaluation table is mixed across scoring versions:

```text
evaluation_version=1, catalogue_metadata_confidence=unknown: 413 rows
evaluation_version=2, catalogue_metadata_confidence=high: 1 row
evaluation_version=2, catalogue_metadata_confidence=unknown: 2 rows
```

Only 3 of 416 rows were recomputed with the current `SourceEvaluationKeys.CURRENT_VERSION = 2` logic. This means most visible source-evaluation decisions are stale. Any UI or sorting path that still treats v1 rows as current will show misleading results.

### Metadata Coverage By Verdict

Current live DB distribution:

```text
error:          104 rows, 100.0% missing sampled tags
explicit_heavy:  4 rows,  75.0% missing sampled tags
poor_search:    77 rows, 100.0% missing sampled tags
strong_fit:      5 rows,   0.0% missing sampled tags
weak:          205 rows,  99.0% missing sampled tags
worth_trying:   21 rows,   0.0% missing sampled tags
```

Interpretation:
- The system heavily rewards sources whose list pages expose tags.
- It heavily penalizes or weak-labels sources whose list pages omit tags.
- This is not the same thing as actual source quality.

### Elf Toon vs KaliScan

This pair demonstrates the mismatch the user reported.

Elf Toon:

```text
source_name: Elf Toon
verdict: weak
evaluation_version: 1
catalogue_metadata_confidence: unknown
sample_count: 28
preferred_tag_match_count: 0
blocked_tag_match_count: 0
explicit_signal_count: 0
ecchi_signal_count: 0
recommendation_fit_score: 0.35
sampled_tags_json: null
```

The evaluator collected 28 catalogue titles but stored no tags, so it had no taste evidence. If the user can manually open Elf Toon manga and see tags, the likely cause is that those tags are available through manga detail pages, not through the Popular/Latest list entries being scored.

KaliScan:

```text
source_name: KaliScan
verdict: strong_fit
evaluation_version: 1
catalogue_metadata_confidence: unknown
sample_count: 49
preferred_tag_match_count: 9
blocked_tag_match_count: 0
explicit_signal_count: 2
ecchi_signal_count: 2
recommendation_fit_score: 0.85
sampled_tags_json: Drama|Mature|Romance|Smut|Comedy|Shounen ai|Yaoi|Webtoons|Adult|Action|Martial arts|Shounen|Ecchi|Supernatural
```

KaliScan received positive fit credit because it had tags such as Action and Martial Arts, but it also contained tags the user generally does not want, including Yaoi, Shounen ai, Smut, Adult, Mature, and Ecchi. The current v1 row does not show blocked matches. Under current code, aliases should be canonicalized, but this row is stale v1 and should not be trusted as proof current v2 blocking worked.

Still, the example exposes two structural issues:
- positive broad tags can outweigh noisy/adult signals if not gated correctly;
- rows from older scoring versions remain visible and confusing unless reassessed or hidden as stale.

## How Tags Can Actually Be Obtained

Komikku/Tachiyomi-style sources expose several different levels of metadata.

### 1. Popular/Latest/Search list entries

Methods:
- `CatalogueSource.getPopularManga(page)`
- `CatalogueSource.getLatestUpdates(page)`
- `CatalogueSource.getSearchManga(page, query, filters)`

These usually return `SManga` list entries. Depending on the extension, these entries may contain:
- title
- URL
- thumbnail
- sometimes status
- sometimes genre/tags

They often do not contain full genre metadata.

This is exactly where current source evaluation is weak: it samples list entries and scores them as if missing tags means no taste match.

### 2. Manga detail page enrichment

Method:
- `Source.getMangaDetails(manga: SManga): SManga`

This is the main natural mechanism to get full manga metadata from an extension. Existing app paths already use it:

- `MangaScreenModel.fetchMangaFromSource()` calls `state.source.getMangaDetails(state.manga.toSManga())`.
- `LibraryUpdateJob` calls `source.getMangaDetails(manga.toSManga())`.
- Migration code calls `getMangaDetails()` before transferring to a new source.
- `RecommendationCandidateEnricher` uses bounded `getMangaDetails()` calls for weak recommendation candidates.
- `SourceRecommendationFitProbe` already enriches search results with `getMangaDetails()` for up to 5 candidates per query plan.

Conclusion:
Tag retrieval is feasible, but source evaluation must add a bounded detail-enrichment stage for catalogue samples. It should not depend only on list entries.

### 3. Source filters

Some sources expose genre filters through `getFilterList()`. These can help with query-time inclusion/exclusion, but they are not reliable evidence that a particular manga has a tag. Filters describe what a source can search by, not necessarily what each result contains.

Filters should support search strategy, not replace per-manga metadata.

## Current Scoring Behavior

### Taste profile generation

`GetTasteProfile` builds:

- learned tag weights from rated manga genres;
- explicit tag preferences from `tag_taste`;
- source affinity from liked/loved/disliked source history;
- blocked groups from `TagPreference.BLOCK`.

Important correction:
Explicit tag preferences are canonicalized through aliases. `GetTasteProfile` maps `tag_taste.normalizedTag` through `tag_alias` group keys. For example, Yaoi and Shounen Ai can resolve to `boys_love`, and Yuri/Shoujo Ai can resolve to `girls_love`.

`TagPreference` values:

```text
BLOCK = -2
DISLIKE = -1
PREFER = 1
```

The user's live `tag_taste` table includes:

```text
PREFER: Action, Adventure, Martial Arts, Psychology
BLOCK: Josei, Shoujo, Shoujo Ai, Yaoi, Yuri
```

Relevant aliases exist for:

```text
Yaoi, Shounen Ai, Boys Love, BL -> boys_love
Yuri, Shoujo Ai, Girls Love, GL -> girls_love
```

### Per-manga scoring

`PersonalRecommendationScorer`:

- resolves candidate genres through the alias map;
- hard-blocks candidates if any genre group is in `profile.blockedGroups`;
- adds +3 for explicit preferred tags;
- adds -2 for explicit disliked tags;
- adds learned tag weights;
- adds weak source affinity.

This scorer can work well if it receives real tags. The main issue is whether source evaluation gives it real tags.

### Source-evaluation scoring

`SourceEvaluationScorer`:

- uses `catalogueSamples.flatMap { it.genre.orEmpty() }`;
- scores each sample with `PersonalRecommendationScorer`;
- stores `blockedTagMatchCount = scoredCatalogue.count { it.blocked }`;
- stores `preferredTagMatchCount = scoredCatalogue.count { !it.blocked && it.score > 0.0 }`.

The name `preferredTagMatchCount` is misleading. It is not a count of preferred tags. It is the count of catalogue manga whose total personal score is positive. A manga can score positive because of broad liked tags or learned weights, even if the source is otherwise noisy.

This should be renamed or split in a future migration:

- `positive_candidate_count`
- `preferred_tag_hit_count`
- `learned_tag_hit_count`
- `blocked_candidate_count`
- `negative_candidate_count`
- `adult_signal_candidate_count`

### Explicit/ecchi detection

Current explicit tag terms include:

```text
hentai, porn, pornographic, explicit, yaoi, yuri, adult content, erotic,
erotica, smut, hentai manga, uncensored, nsfw, 18+, xxx
```

Current ecchi terms include:

```text
ecchi, mature, lewd, fan service, fanservice, risque, suggestive,
semi-explicit, sensual, sexy, sexual
```

Potential problem:
- `Adult` alone is not treated as explicit; only `adult content` is.
- `Shounen ai`, `Boys Love`, `Girls Love`, `Shoujo ai` are not directly explicit terms, though they can be blocked through aliases if the taste profile is current and the row is reassessed.
- Broad positive tags can still make a mixed source look good if only some candidates hit blocked groups.

## Current Recommendation-Quality Probe

`SourceRecommendationFitProbe` is better designed than catalogue source evaluation in one important way: it already does bounded detail enrichment.

It:

- builds up to 2 query plans from top taste tags;
- calls `source.getFilterList()` on IO;
- calls `source.getSearchManga()` on IO;
- takes up to 20 raw results per plan;
- enriches up to 5 candidates per plan with `getMangaDetails()`;
- scores enriched results with `PersonalRecommendationScorer`;
- classifies outcomes as great/good/mixed/weak/no_matches/error.

Live DB distribution:

```text
error:      32 rows
good:        5 rows
great:      19 rows
mixed:       1 row
no_matches:125 rows
weak:      121 rows
```

The recommendation-quality probe is still limited by source search behavior. If a source cannot search well by the generated tag plans, it may score weak even if its catalogue has useful manga. Therefore catalogue fit and For You search compatibility should remain separate signals:

- Catalogue fit: "Does this source contain things I probably like?"
- For You search compatibility: "Can the app reliably retrieve recommendations from this source using automated search/filter plans?"

## What Is Wrong Or Not Working As Intended

### 1. Catalogue source evaluation does not enrich tags

Severity: high.

The source-evaluation path does not call `getMangaDetails()` for Popular/Latest samples. This is the direct cause of many false weak labels.

### 2. Most live source-evaluation rows are stale

Severity: high.

413 of 416 live rows are `evaluation_version = 1` while current code uses version 2. The UI must not present stale rows as if they were final current evidence.

### 3. Metadata confidence is not meaningful for stale rows

Severity: medium-high.

Most tagged rows still show `catalogue_metadata_confidence = unknown` because they were written before the v2 confidence field was populated. The DB should either reassess stale rows or surface them as stale/outdated.

### 4. `preferredTagMatchCount` is misleading

Severity: medium-high.

The field counts positive-scoring manga, not preferred tag matches. It can overstate quality when broad positive weights appear beside unwanted themes.

### 5. Positive scoring can overrate noisy adult/mixed sources

Severity: medium-high.

Sources with Action/Martial Arts plus Yaoi/Smut/Adult/Ecchi can still look strong if the negative signals are not hard-blocked or if old rows were scored before current alias/block logic.

### 6. Explicit/ecchi classification is incomplete

Severity: medium.

Terms such as `Adult`, `Boys Love`, `Girls Love`, `Shounen ai`, and `Shoujo ai` need clearer handling. They should not all be treated identically, but the current source-evaluation risk labels do not fully reflect the user's explicit preferences.

### 7. Source recommendations conflate catalogue quality and retrievability

Severity: medium.

A source can be good but hard to automate. Another source can be easy to search but contain poor manga. The app now separates some of this, but the UI and stale rows still make it easy to misread the verdict.

### 8. Diagnostic DB files need protection

Severity: privacy housekeeping.

The pulled database snapshots are private and should be ignored locally. Ensure future cleanup adds patterns for `tachiyomi_kmk_debug_diagnostic*.db*` to `.gitignore` or keeps them outside the repo.

### 9. Focused Gradle tests could not be run in this shell

Severity: environment blocker, not app-code proof.

Attempted focused tests:

```text
.\gradlew.bat :app:testDebugUnitTest
  --tests "exh.recs.evaluation.SourceEvaluationScorerTest"
  --tests "exh.recs.evaluation.SourceRecommendationFitProbeTest"
  --tests "exh.recs.PersonalRecommendationScorerTest"
  --tests "exh.taste.GetTasteProfileTest"
  --tests "exh.recs.memory.RecommendationCandidateMemoryRankerTest"
```

Result:

```text
Gradle requires JVM 17 or later to run. Current shell is using Java 8:
C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe
java version "1.8.0_461"
```

The audit therefore uses code inspection and live DB diagnostics, not a fresh passing test run.

## Recommended Fix Architecture

### Phase A: Make catalogue evidence real

Add bounded detail enrichment to `SourceEvaluationRunner.probeAndScore()` before scoring:

1. Fetch Popular page 1 and Latest page 1 as currently done.
2. Deduplicate by URL.
3. Select a bounded sample for detail enrichment:
   - recommended cap: 10-15 total manga per source, or 5 Popular + 5 Latest minimum;
   - skip enrichment for samples that already have genre metadata;
   - sequential per source;
   - each `getMangaDetails()` call has a timeout;
   - failures keep the list-entry version and increment an enrichment failure counter.
4. Score enriched samples.
5. Persist both list-level and detail-level metadata counts:
   - listSampleCount
   - detailAttemptCount
   - detailSuccessCount
   - metadataCandidateCount
   - metadataConfidence

Do not fetch chapter lists or page images during source evaluation.

### Phase B: Treat stale evidence honestly

Rows with `evaluation_version < SourceEvaluationKeys.CURRENT_VERSION` should be:

- hidden from final "strong/worth/weak" recommendation labels, or
- displayed as "Outdated - reassess needed", or
- automatically queued for reassessment when the user opens Source Evaluation.

The current state, where v1 rows look like current verdicts, is misleading.

### Phase C: Split evidence fields

Avoid relying on `preferredTagMatchCount` as a proxy for "source has tags I like."

Future source-evaluation result should distinguish:

- candidate count;
- candidates with any metadata;
- candidates with explicit preferred tags;
- candidates with learned-positive tags;
- candidates with soft disliked tags;
- candidates with hard blocked tags;
- candidates with adult/explicit signals;
- candidates with ecchi/mature signals;
- neutral candidates;
- detail enrichment failures.

Then verdicts can be based on multiple gates instead of one positive total.

### Phase D: Make adult/BL/GL policy explicit

The app already has separate user preferences for tags and explicit-source blocking. Source evaluation should use both:

- `TagPreference.BLOCK` should hard-block matching candidate groups.
- `TagPreference.DISLIKE` should lower fit without fully excluding.
- explicit/porn/hentai source blocking should remain separate from ecchi.
- Boys Love/Girls Love aliases should be respected as user tag blocks if the user blocks Yaoi/Yuri/Shounen Ai/Shoujo Ai.
- `Adult`, `Smut`, `Mature`, and `Ecchi` need clearer classification and UI wording.

### Phase E: Keep catalogue fit and For You compatibility separate

Do not collapse these into one score.

Recommended labels:

- Catalogue fit: Great / Good / Mixed / Weak / Metadata sparse / Outdated / Error
- For You search: Great / Good / Mixed / Weak / No matches / Error / Not checked

This would prevent "good library, bad recommender" and "bad library, easy search" from being confused.

### Phase F: Improve fallback for metadata-sparse sources

If detail enrichment still yields no tags, do not automatically call the source weak. Instead:

- mark it as `metadata_sparse`;
- optionally use title affinity as a weak fallback;
- use user-installed outcomes over time;
- let the user manually promote/demote the source.

This avoids punishing sources like Elf Toon when they are useful but metadata is hard to extract.

## Test Plan For Next Implementation

Required unit tests:

1. Catalogue detail enrichment:
   - fake source returns Popular/Latest list entries without genres;
   - `getMangaDetails()` returns genres;
   - scorer uses enriched genres and produces non-weak fit.

2. Enrichment timeout/failure:
   - fake source throws or times out in `getMangaDetails()`;
   - evaluation completes without crashing;
   - metadata confidence is low/unknown;
   - row is not misrepresented as a confident weak fit.

3. Blocked alias handling:
   - profile blocks Yaoi;
   - alias maps Yaoi/Shounen Ai to `boys_love`;
   - candidate with Shounen Ai is blocked.

4. Mixed-source false positive:
   - source has Action/Martial Arts plus Yaoi/Smut/Adult/Ecchi;
   - positive tags alone must not produce `strong_fit` when blocked or adult risk is high.

5. Metadata confidence persistence:
   - upsert writes `catalogue_metadata_confidence`;
   - repository reads the same value back.

6. Stale row display:
   - v1 rows are shown as outdated or excluded from current rankings;
   - v2 rows are treated as current.

7. Recommendation-fit separation:
   - catalogue fit remains independent from `SourceRecommendationFitProbe`;
   - a source can be catalogue-good but For You-search weak without overwriting catalogue verdict.

Manual real-device checks:

1. Reassess Elf Toon after enrichment.
2. Confirm sampled tags appear if detail pages expose them.
3. Reassess KaliScan and verify BL/GL/adult signals are not overrated.
4. Confirm Source Evaluation UI shows stale/outdated rows clearly.
5. Confirm no extension install/uninstall loop is left running after cancellation.

## Bottom Line

This feature is feasible, but the current source-evaluation evidence model is incomplete.

The next implementation should not try to patch individual source names. It should fix the evidence pipeline:

1. get real tags with bounded `getMangaDetails()` enrichment;
2. invalidate or clearly mark stale v1 evaluations;
3. split positive, negative, blocked, explicit, and metadata-confidence signals;
4. keep catalogue fit separate from For You search compatibility;
5. add tests that prove metadata-sparse and mixed adult sources are handled honestly.

Until that is done, Source Evaluation should be treated as experimental guidance, not a reliable source-ranking system.

