# Non-Installed Extension Discovery Hardening Plan

Date: 2026-06-16

Status: planning only. Do not implement until the user approves this plan and requests a Claude Code prompt.

Target feature version: `KMK-Recs v0.6.1`

## Problem

The v0.6.0 "Sources To Try" feature recommends too many extensions. In practice, it behaves like a filtered extension list rather than a selective recommendation feature.

The current scorer gives every eligible non-installed source a suggestion because every language-matching source receives:

```text
+0.20 LanguageMatch
```

and many sources also receive:

```text
+0.05 Has base URL
+0.05 Generic keyword such as scans/manga/manhwa
+0.10 Same repo as installed source
```

This means almost every English source from the same repo can appear, even without meaningful evidence that it fits the user.

## Current Code Verified

Relevant files inspected:

```text
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestionScorer.kt
app/src/main/java/exh/recs/discovery/GetNonInstalledSourceSuggestions.kt
app/src/main/java/exh/recs/discovery/NonInstalledSourceSuggestion.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt
app/src/main/java/exh/recs/settings/RecommendationsSettingsScreen.kt
app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt
docs/recommendations/NON_INSTALLED_EXTENSION_DISCOVERY_IMPLEMENTATION.md
```

Current `NonInstalledSourceSuggestionScorer` behavior:

- language match is both a filter and a positive scoring reason,
- same repo adds score,
- source name similarity adds score,
- non-empty base URL adds score,
- generic content keywords add score,
- every eligible source gets `NeedsTesting`,
- there is no minimum evidence threshold,
- results are sorted but not filtered after scoring.

Current tests lock in the problematic behavior:

- `language match creates at least a low-confidence suggestion`,
- `same repo as installed sources increases score and adds reason`.

Those tests must be replaced.

## Product Correction

The feature should mean:

```text
Sources that have a specific reason to be worth trying.
```

It should not mean:

```text
All available English sources.
```

Language, NSFW status, installation status, untrusted status, and dismissal status are eligibility filters. They are not recommendation evidence.

## Corrected User-Facing Behavior

### Before

Any eligible source can appear because it matches recommendation language.

### After

A source appears only if it has at least one meaningful positive evidence reason beyond:

- language match,
- repo match,
- base URL existence,
- generic content words,
- needs testing.

If no source has meaningful evidence, the section should show an honest empty state:

```text
No strong source suggestions yet.
```

or:

```text
No strong suggestions yet. Install and use more sources to improve suggestions.
```

## Meaningful Evidence For v0.6.1

Since installed-source fit learning does not exist yet, the only meaningful generic evidence currently available is conservative similarity to an already installed source.

For v0.6.1, a non-installed source should be shown only if it has:

```text
SimilarToInstalledSource
```

This is intentionally strict.

Future versions can add stronger evidence from installed-source fit stats, source-family profiles, or user-approved source categories.

## Signals To Reclassify

### Language Match

Current:

- Adds `+0.20`
- Adds visible `LanguageMatch` reason
- Allows source to appear

New:

- Eligibility filter only.
- Do not include as a displayed reason.
- Do not add score.

### Same Repo

Current:

- Adds `+0.10`
- Adds reason

New:

- At most a tie-breaker or secondary context.
- Does not qualify a suggestion by itself.
- Prefer removing from visible reasons for v0.6.1.

Reason: Most extensions likely come from the same repo, especially Keiyoushi, so this is not meaningful recommendation evidence.

### Base URL

Current:

- Adds `+0.05`

New:

- No score.
- It is ordinary metadata, not evidence.

### Generic Content Keywords

Current:

- `scans`, `scan`, `manga`, `manhwa`, `webtoon`, `comics` add `+0.05`.

New:

- No score.
- No qualification.

Reason: these words are too common and cause broad noisy suggestions.

### Needs Testing

Current:

- Always shown as a reason.

New:

- Can remain as secondary disclaimer text only after a source qualifies.
- Must not count as evidence.

## Corrected Scoring Model

Use a score mainly for ordering qualified suggestions, not for deciding that language-only sources should appear.

Recommended constants:

```kotlin
private const val SCORE_SIMILAR_SOURCE_NAME = 0.50
private const val SCORE_EXACT_SOURCE_NAME = 0.60
private const val SCORE_SAME_REPO_TIEBREAKER = 0.02
private const val SCORE_CAP = 0.69
private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55
```

or similar.

The important part is not the exact numbers. The important part is:

- no score for language,
- no score for base URL,
- no score for generic keywords,
- same repo cannot qualify alone,
- at least one meaningful evidence reason is required.

Suggested helper:

```kotlin
private fun hasMeaningfulEvidence(reasons: List<NonInstalledSuggestionReason>): Boolean {
    return reasons.any { it is NonInstalledSuggestionReason.SimilarToInstalledSource }
}
```

If false, return `null` from scoring and do not include the source.

## Better Similarity Rule

Current similarity:

```kotlin
candidate == installed ||
candidate contains installed ||
installed contains candidate
```

This can be too broad if short/common names are involved.

Recommended v0.6.1 behavior:

1. Normalize source names.
2. Ignore very generic installed names.
3. Require:
   - exact normalized name match, or
   - containment where both names are at least 8 characters, or
   - token overlap with at least one distinctive token of length >= 5.

Examples that may qualify:

```text
Asura Scans -> AsuraScans
MangaFire -> MangaFire EN / MangaFire variant
QI Scans -> QIScans
```

Examples that should not qualify:

```text
Manga -> Manga Something
Scans -> Random Scans
Comics -> Any Comics
```

## Model Changes

### `NonInstalledSuggestionReason`

Current:

```kotlin
LanguageMatch
SimilarToInstalledSource
SameRepoAsInstalledSources
NeedsTesting
```

Recommended:

- Keep `SimilarToInstalledSource`.
- Keep `NeedsTesting`.
- Remove visible use of `LanguageMatch`.
- Remove visible use of `SameRepoAsInstalledSources`, or keep it internal only if avoiding larger churn.

To minimize code churn, Claude may leave the classes in place but must not generate them as primary reasons in v0.6.1.

## UI Changes

### Sources To Try Section

Keep the section in Recommendation Settings.

Recommended display behavior:

- Show top 5 qualified suggestions by default.
- Expand only if there are more than 5 qualified suggestions.
- If zero qualified suggestions, show a clear empty state.

Update empty string if needed:

```text
No strong source suggestions yet. Install and use more sources to improve suggestions.
```

Do not show the section as a second extension browser.

### Reason Text

Current reasons can say language match, same repo, similar source, needs testing.

New preferred reason display:

```text
Similar to an installed source
Install to test recommendation quality
```

Do not show:

```text
Matches recommendation language
Same repo as installed sources
```

Those are too weak.

## Interactor Changes

### `GetNonInstalledSourceSuggestions`

Current issue:

It subscribes to:

- available extensions,
- installed extensions,
- untrusted extensions,
- dismissed suggestions.

It reads `recommendationSourceLanguages()` and `showNsfwSource()` with `.get()`, so language/NSFW changes may not trigger immediate recomputation.

For v0.6.1, add language and NSFW preference flows to the `combine`, if practical:

```kotlin
sourcePreferences.recommendationSourceLanguages().changes()
sourcePreferences.showNsfwSource().changes()
```

This was documented as deferred in v0.6.0. It is a good small correction while touching this code.

If adding `showNsfwSource().changes()` is awkward because of preference type behavior, at least add recommendation language changes.

## Tests To Change

Update:

```text
app/src/test/java/exh/recs/discovery/NonInstalledSourceSuggestionScorerTest.kt
```

### Remove Or Replace Current Bad Tests

Replace:

```text
language match creates at least a low-confidence suggestion
```

with:

```text
language-only source is excluded from suggestions
```

Replace:

```text
same repo as installed sources increases score and adds reason
```

with:

```text
same-repo-only source is excluded from suggestions
```

Replace or adjust:

```text
suggestions are sorted by score descending then name ascending
```

so both sources have meaningful evidence; do not rely on same-repo-only score.

### Add New Tests

Add tests:

1. `language-only source is excluded`.
2. `same-repo-only source is excluded`.
3. `generic-keyword-only source is excluded`.
4. `base-url-only source is excluded`.
5. `similar installed source creates suggestion`.
6. `needs testing alone does not qualify suggestion`.
7. `qualified suggestion keeps original extension identity`.
8. `suggestions sort by meaningful score then name`.
9. `all pre-existing filters still work`: installed, untrusted, language mismatch, NSFW disabled, dismissed.
10. `no high confidence before install` still passes.

### Test Exact Problem

Add a test with several ordinary English sources:

```kotlin
available = listOf(
    "Random Scans",
    "Generic Manga",
    "Another Comics",
)
installedHints = emptyList()
```

Expected:

```kotlin
emptyList()
```

This catches the "every extension is recommended" regression directly.

## Documentation Updates

Create implementation report:

```text
docs/recommendations/NON_INSTALLED_EXTENSION_DISCOVERY_HARDENING_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
docs/recommendations/NON_INSTALLED_EXTENSION_DISCOVERY_IMPLEMENTATION.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Docs should say:

- v0.6.0 implemented Sources To Try but was too broad,
- v0.6.1 makes the section selective,
- language/repo/base URL/generic keywords are no longer enough,
- only sources with meaningful similarity evidence are shown,
- if no meaningful suggestions exist, the section shows an honest empty state,
- installed-source fit learning remains future work and will improve this feature later.

## Versioning

Use:

```text
KMK-Recs v0.6.1
```

Expected APK:

```text
Komikku-v1.13.6-kmk.6.1-debug.apk
```

Only claim the APK exists if it is built and copied/renamed.

## Validation Commands

Run targeted tests:

```text
./gradlew :app:testDebugUnitTest --tests "*NonInstalledSource*"
```

Run full unit tests if practical:

```text
./gradlew :app:testDebugUnitTest
```

Build debug APK if practical:

```text
./gradlew :app:assembleDebug
```

## Manual Verification

On device/tablet:

1. Open Recommendation Settings.
2. Confirm Sources To Try no longer lists every available English source.
3. Confirm generic English sources do not appear just because they are English.
4. Confirm sources with names strongly similar to installed sources may appear.
5. Confirm empty state appears when no strong suggestions exist.
6. Confirm Dismiss still removes a suggestion.
7. Confirm Install still uses the existing extension installer.
8. Confirm installed suggestions disappear after installation.
9. Confirm normal Extensions screen still lists all available extensions.
10. Confirm normal Browse > For You behavior is unchanged.

## Deferred Work

Keep deferred:

- installed-source fit learning,
- source-family profiles,
- curated website/source knowledge,
- post-install pending evaluation,
- automatic source priority suggestions,
- non-installed website crawling,
- source-specific recommendation extraction.

## Summary For Claude

Fix v0.6.0 Sources To Try being too broad.

Do not merely lower a threshold.

Change the meaning of the scorer:

- language is a filter, not evidence;
- same repo is not evidence;
- base URL is not evidence;
- generic keywords are not evidence;
- `NeedsTesting` is a disclaimer, not evidence;
- only show a non-installed source when it has meaningful positive evidence, currently conservative similarity to an installed source.

Update tests so language-only and same-repo-only sources are excluded. Update docs and release notes as KMK-Recs v0.6.1.

