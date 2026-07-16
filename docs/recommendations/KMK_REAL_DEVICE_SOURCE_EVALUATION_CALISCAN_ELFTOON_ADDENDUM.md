# KMK Source Evaluation Addendum: KaliScan vs Elf Toon

Date: 2026-07-12

Status: follow-up diagnostic to `KMK_REAL_DEVICE_SOURCE_EVALUATION_DIAGNOSTIC.md`.

Scope: explain why KaliScan/Caliscans can be ranked as a strong/great fit while Elf Toon is ranked weak, even though the user's real-world experience is the opposite.

## Short Answer

The current source evaluation is producing both false negatives and false positives.

Elf Toon is a false negative:

- It has titles that match the user's taste in practice.
- It exposes little or no usable genre/tag metadata during evaluation.
- The scorer cannot recognize the fit, so it marks the source weak.

KaliScan is a false positive:

- It exposes many tags, so it is easy for the scorer to score.
- It contains some preferred tags, such as `Action` and `Martial Arts`.
- It also exposes disliked/adult-romance tags such as `Yaoi`, `Shounen ai`, `Smut`, `Adult`, and `Ecchi`.
- Those disliked tags did not suppress the score enough, and likely did not match the user's stored dislike keys after alias normalization.

The practical bug is not simply "lower or raise thresholds." The evidence model is judging sources by "how easily the current scorer can read tags," not by "how good the source actually is for the user."

## Real-Device Database Evidence

### Elf Toon

| Field | Value |
| --- | --- |
| `extension_pkg_name` | `eu.kanade.tachiyomi.extension.en.elftoon` |
| `source_name` | `Elf Toon` |
| `base_url` | `https://elftoon.com` |
| `sample_count` | 28 |
| `popular_count` | 15 |
| `latest_count` | 10 |
| `preferred_tag_match_count` | 0 |
| `blocked_tag_match_count` | 0 |
| `quality_score` | 0.9 |
| `recommendation_fit_score` | 0.35 |
| `catalogue_metadata_confidence` | `unknown` |
| `sampled_tags_json` | `null` |
| `verdict` | `weak` |

Elf Toon sampled titles included many progression/cultivation/system/action-style titles, but because no sampled tags were available, the scorer had no positive tag matches.

Its recommendation-quality probe found 15 raw results in one plan, but all were filtered because they still had no genre metadata after enrichment:

> `Plan TAG_PAIR: 15 results, all had no genre metadata`

### KaliScan / Caliscans

| Field | Value |
| --- | --- |
| `extension_pkg_name` | `eu.kanade.tachiyomi.extension.en.kaliscancom` |
| `source_name` | `KaliScan` |
| `base_url` | `https://kaliscan.com` |
| `sample_count` | 49 |
| `popular_count` | 15 |
| `latest_count` | 10 |
| `preferred_tag_match_count` | 9 |
| `blocked_tag_match_count` | 0 |
| `explicit_signal_count` | 2 |
| `ecchi_signal_count` | 2 |
| `quality_score` | 0.9 |
| `recommendation_fit_score` | 0.85 |
| `search_reliability_score` | 1.0 |
| `catalogue_metadata_confidence` | `unknown` |
| `verdict` | `strong_fit` |

KaliScan sampled titles/tags included:

- titles: `Painter of the Night`, `Jinx`, `Love Is An Illusion`, `The Titan's Bride`, `Secret Class`, etc.
- tags: `Drama`, `Mature`, `Romance`, `Smut`, `Comedy`, `Shounen ai`, `Yaoi`, `Webtoons`, `Adult`, `Action`, `Martial arts`, `Shounen`, `Ecchi`, `Supernatural`.

The source was scored as strong because it had 9 positive tag matches and 0 blocked matches. That is clearly not aligned with the user's stated preference.

## Stored Preference Evidence

The diagnostic database contained these explicit tag preferences:

| Tag | Preference |
| --- | ---: |
| `Action` | `1` |
| `Adventure` | `1` |
| `Martial Arts` | `1` |
| `Psychology` | `1` |
| `Josei` | `-2` |
| `Shoujo` | `-2` |
| `Shoujo Ai` | `-2` |
| `Yaoi` | `-2` |
| `Yuri` | `-2` |

Relevant aliases were also present:

| Alias | Group key |
| --- | --- |
| `BL` | `boys_love` |
| `Boys Love` | `boys_love` |
| `Shounen Ai` | `boys_love` |
| `Yaoi` | `boys_love` |
| `Girls Love` | `girls_love` |
| `GL` | `girls_love` |
| `Shoujo Ai` | `girls_love` |
| `Yuri` | `girls_love` |

This points to a likely key-space mismatch:

1. Candidate tags are alias-resolved into canonical groups such as `boys_love` and `girls_love`.
2. User preferences may remain stored as normalized display tags such as `yaoi`, `yuri`, and `shoujo_ai`.
3. `PersonalRecommendationScorer` checks the candidate's resolved group against `profile.explicitTagPreferences`.
4. If the profile preferences are not canonicalized into the same alias groups, the scorer misses the negative preference.

In plain terms: the app can know that `Yaoi` maps to `boys_love`, and the user can dislike `Yaoi`, but if the candidate is checked as `boys_love` while the preference remains `yaoi`, the dislike does not apply.

## What Is Actually Happening

### Why Elf Toon is underrated

Elf Toon has a relevant catalogue, but the scorer cannot read enough tags from the sampled entries.

Because the source evaluation scorer mostly scores tags, the absence of tags becomes equivalent to "no match." That is wrong for sources where titles/premises carry the useful signal.

### Why KaliScan is overrated

KaliScan has readable tags, so the scorer can give it points. It contains a few positive tags, and those positive tags are counted.

But the negative side is too weak:

- disliked aliases appear not to be canonicalized consistently;
- `Smut`, `Adult`, and `Mature` are not necessarily tied to the user's explicit dislikes;
- explicit/ecchi signal counts are treated as ratio-style source-level signals and do not override the strong-fit score unless high enough;
- `blocked_tag_match_count` stayed at 0 even though the source had tags the user dislikes.

## Recommended Fix Direction

### 1. Canonicalize explicit tag preferences through aliases

Fix the taste-profile construction layer so explicit preferences and candidate tags live in the same key space.

If candidate tags resolve through aliases, preferences must also resolve through aliases.

Required examples:

- user dislikes `Yaoi`; candidate has `Yaoi`; scorer penalizes or blocks it;
- user dislikes `Yaoi`; candidate has `Shounen ai`; scorer penalizes or blocks it through `boys_love`;
- user dislikes `Yuri`; candidate has `Girls Love`; scorer penalizes or blocks it through `girls_love`;
- user dislikes `Shoujo Ai`; candidate has `GL`; scorer penalizes or blocks it through `girls_love`.

### 2. Separate disliked-tag count from blocked-tag count

The current row records `blocked_tag_match_count`, but the user has both disliked and blocked-style preferences.

Add or compute:

- `disliked_tag_match_count`
- `disliked_tag_ratio`
- `positive_tag_match_count`
- `positive_to_negative_balance`

This would prevent a source from becoming `Strong Fit` just because it has some action/martial tags while many sampled entries also match disliked categories.

### 3. Require Strong Fit to pass a negative-preference gate

A source should not become `Strong Fit` if:

- its sampled tags include user-disliked groups above a threshold;
- explicit/porn/hentai/yaoi/yuri categories are repeatedly present and the user dislikes them;
- positive matches are mostly generic tags such as `Action` while negative tags are specific strong dislikes.

Suggested rule:

> Strong Fit requires positive evidence and low negative evidence.

### 4. Treat metadata-sparse useful sources separately

Elf Toon should not be ranked below genuinely poor sources just because its tags are missing.

Add an honest state such as:

- `Promising, sparse metadata`
- `Needs Review`
- `Metadata limited`

Use title affinity and user outcome learning as secondary evidence.

### 5. Add lightweight title-affinity fallback

For metadata-sparse sources only, compute a bounded title-theme score from sampled titles and liked/loved title tokens.

This should not overpower explicit blocked/disliked tags, and it should not become an AI/network feature.

### 6. Show evidence in the UI

The Source Evaluation detail row should show why a source scored the way it did:

- positive tags matched;
- disliked/blocked tags matched;
- sampled title examples;
- metadata confidence;
- raw vs visible recommendation-quality candidates;
- "all results lacked genre metadata" where applicable.

## Implementation Target

This should be handled in a future `v0.8.x` source-evaluation correction plan.

It should not be a single threshold tweak. The correct fix is to make source evaluation use a truthful evidence model:

1. positive tag evidence;
2. negative tag evidence;
3. metadata confidence;
4. title/theme fallback for sparse sources;
5. recommendation retrieval compatibility;
6. local user outcome learning.

## Diagnostic Scripts Created

Local read-only helper scripts were added:

- `scripts/kmk_source_pair_diagnostic.py`
- `scripts/kmk_taste_preference_diagnostic.py`

They read the local diagnostic database only and should be treated as internal diagnostic tooling.


