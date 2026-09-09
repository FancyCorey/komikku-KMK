# Non-Installed Extension Discovery Hardening Implementation

Date: 2026-06-16

Status: implemented as KMK-Recs v0.6.1.

## What Was Fixed

The v0.6.0 "Sources To Try" scorer treated language match, same repo, base URL, and generic content keywords as positive scoring signals. In practice every eligible English extension appeared as a suggestion, making the section behave like a filtered extension browser rather than a selective recommendation feature.

## Core Change

`NonInstalledSourceSuggestionScorer` was rewritten to use an evidence gate:

- `score()` now returns `null` when no meaningful evidence is found.
- `hasMeaningfulEvidence()` checks for `SimilarToInstalledSource` â€” the only accepted evidence for v0.6.1.
- Sources that pass language/NSFW/installed/untrusted/dismissed filters but have no similarity to an installed source are excluded (return null).

## Signals Reclassified

| Signal | v0.6.0 | v0.6.1 |
|---|---|---|
| Language match | +0.20, LanguageMatch reason | Eligibility filter only |
| Same repo | +0.10, SameRepoAsInstalledSources reason | Not evidence, not shown |
| Base URL | +0.05 | Not evidence |
| Generic keywords | +0.05 | Not evidence |
| SimilarToInstalledSource | +0.10 | Required evidence, +0.50 or +0.60 |
| NeedsTesting | Always shown | Shown only after source qualifies |

## Similarity Rules

1. Exact normalized name match â†’ score 0.60 (MEDIUM confidence).
2. Containment where both normalized names â‰¥ 8 chars â†’ score 0.50 (LOW confidence).
3. Distinctive token overlap: at least one shared token of length â‰¥ 5 not in GENERIC_TOKENS â†’ score 0.50 (LOW confidence).

Generic single-word names (`manga`, `scans`, `scan`, `manhwa`, `webtoon`, `comics`, `comic`, `source`) are excluded from both sides of the comparison.

`distinctiveTokens()` uses the ORIGINAL (non-normalized) name so that word boundaries are preserved for splitting. Example: "Asura Scans" â†’ token "asura" (scans is generic). If pre-normalized: "asurascans" â†’ single token "asurascans" which is a different match.

## Interactor Change

`GetNonInstalledSourceSuggestions` now uses a 5-argument `combine()` that includes `sourcePreferences.recommendationSourceLanguages().changes()`. Language preference changes now trigger immediate suggestion recomputation.

## Confidence Threshold

Threshold adjusted from â‰¥ 0.35 to â‰¥ 0.55 to match the new score range. Exact match (0.60) â†’ MEDIUM. Similarity match (0.50) â†’ LOW.

## Empty State

Updated `rec_sources_to_try_empty` string to:

```text
No strong source suggestions yet. Install and use more sources to improve suggestions.
```

## Files Changed

- `app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt` â€” complete rewrite
- `app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt` â€” added 5th combine source
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” VERSION_CODE=601, v0.6.1 notes
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” updated rec_sources_to_try_empty
- `app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt` â€” rewritten (18 tests)

## Tests

Replaced 2 bad tests, updated 3 existing tests, added 6 new tests.

Key new tests:
- `language-only source is excluded from suggestions`
- `same-repo-only source is excluded from suggestions`
- `generic-keyword-only source is excluded from suggestions`
- `base-url-only source is excluded from suggestions`
- `regression - ordinary english sources with no installed hints produce empty list`
- `similar installed source creates suggestion`
- `needs testing alone does not qualify a suggestion`
- `distinctiveTokens filters generic words and short tokens`

```text
./gradlew :app:testDebugUnitTest --tests "*NonInstalledSource*" â†’ BUILD SUCCESSFUL, 18 tests PASSED
./gradlew :app:testDebugUnitTest â†’ BUILD SUCCESSFUL
./gradlew :app:assembleDebug â†’ BUILD SUCCESSFUL
```

## APK

`Komikku-v1.13.6-kmk.6.1-debug.apk`

## Deferred

- Installed-source fit learning (will improve suggestions when implemented)
- Source-family profiles
- Post-install pending evaluation
- Automatic source priority suggestions

