# KMK-Recs v0.7.2: Loved Manga Smart Grouping And Link Usage Plan

Date: 2026-06-20

Status: implementation plan, awaiting user approval before coding

Feature version: `KMK-Recs v0.7.2`

Expected APK name: `Komikku-v1.13.6-kmk.7.2-debug.apk`

## Summary

The current Loved Manga `Group clear duplicates` toggle can appear to do nothing because the grouping rule is too strict and does not use the app's stronger same-manga evidence.

Current implementation groups only when:

```text
normalized title exactly matches
AND normalized description exactly matches
AND description length >= 50
```

This does not handle real cross-source cases such as:

- romanized title vs translated title,
- Korean/Japanese/original title vs English title,
- slightly different localized titles,
- different source descriptions for the same manga,
- missing descriptions,
- versions already confirmed through the cross-extension matching workflow.

The proper fix is not title-only grouping. The proper fix is a tiered, evidence-based grouping system:

1. Use persistent cross-source link groups first, because those represent user-confirmed same-manga identity.
2. Then use conservative metadata grouping where evidence is strong enough.
3. Never group by title alone.
4. Keep grouping display-only; never delete or merge taste rows.

## Important Documentation/Code Mismatch

Before implementing, Claude must resolve a current documentation mismatch.

Some docs still say cross-source link groups are deferred:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/KMK_RECS_V0_7_1_CROSS_EXTENSION_MATCH_STATE_AND_SEEN_MENU_FIX_IMPLEMENTATION.md
```

But the code already contains cross-source link group infrastructure:

```text
data/src/main/sqldelight/tachiyomi/data/manga_cross_source_link.sq
data/src/main/sqldelight/tachiyomi/migrations/50.sqm
domain/src/main/java/tachiyomi/domain/taste/model/CrossSourceMangaLink.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/GetCrossSourceMangaLinks.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertCrossSourceMangaLinks.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/DeleteCrossSourceMangaLink.kt
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupCrossSourceMangaLink.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/TasteBackupCreator.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/TasteRestorer.kt
app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt
```

`CrossExtensionMatchScreenModel` also writes a new link group after applying selected rating/seen/favorite matches.

Claude must verify the current implementation and update docs accordingly. Do not continue saying link groups are deferred if they are implemented. If the implementation is incomplete or unsafe, document exactly what exists and what remains incomplete.

## User Problem

The user reports:

- In Loved Manga, selecting `Group clear duplicates` appears to do nothing.
- The user wants grouping to recognize equivalent manga, not merely exact same-name manga.
- The user specifically asked whether romanized vs translated title and already-found duplicates are supported.

Correct answer from the current implementation:

- Romanized/translated title equivalence is not supported by the current grouper.
- Already-found duplicate/same-manga relationships are not used by Loved Manga grouping.
- However, current code appears to have cross-source link-group storage available, so Loved Manga should use it.

## Goals

1. Make Loved Manga duplicate grouping visibly useful when same-manga evidence exists.
2. Use persistent cross-source link groups as the strongest grouping signal.
3. Add smarter conservative metadata fallback grouping.
4. Avoid unsafe title-only merging.
5. Preserve all taste rows; grouping remains display-only.
6. Keep Loved Manga sorting stable and understandable.
7. Reconcile documentation with actual cross-source link implementation state.
8. Add tests proving the new grouping behavior.

## Non-Goals

- Do not delete or merge manga/taste rows.
- Do not make a global automatic same-manga identity system.
- Do not run network searches from Loved Manga just to group duplicates.
- Do not fetch manga details in bulk from source websites.
- Do not change For You scoring.
- Do not change normal global search.
- Do not change cross-extension matching caps.
- Do not implement unrelated deferred roadmap phases.

## Current Loved Manga Code

Relevant files:

```text
app/src/main/java/exh/recs/loved/LovedMangaScreen.kt
app/src/main/java/exh/recs/loved/LovedMangaScreenModel.kt
app/src/main/java/exh/recs/loved/LovedMangaDuplicateGrouper.kt
app/src/test/java/exh/recs/loved/LovedMangaDuplicateGrouperTest.kt
```

Current `LovedMangaScreenModel`:

- loads all tastes through `GetMangaTaste.awaitAll()`;
- filters `rating == MangaRating.LOVE.value`;
- resolves local manga through `GetManga.await(mangaId)` or `GetManga.await(url, source)`;
- toggles `groupDuplicates`;
- uses `LovedMangaDuplicateGrouper.computeGroups(inputs)` only when grouping is enabled.

Current grouper input:

```kotlin
LovedMangaDuplicateGrouper.GroupInput(
    key = "${entry.taste.source}|${entry.taste.url}",
    title = entry.manga?.title ?: entry.taste.title,
    description = entry.manga?.description.orEmpty(),
)
```

Current limitation:

- no source/url parsed identity object;
- no cross-source link group;
- no author/artist;
- no title similarity;
- no alternate titles;
- no reason/confidence output.

## Current Cross-Source Link Code To Verify

Claude must inspect:

```text
domain/src/main/java/tachiyomi/domain/taste/model/CrossSourceMangaLink.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/GetCrossSourceMangaLinks.kt
domain/src/main/java/tachiyomi/domain/taste/interactor/UpsertCrossSourceMangaLinks.kt
domain/src/main/java/tachiyomi/domain/taste/repository/TasteRepository.kt
data/src/main/sqldelight/tachiyomi/data/manga_cross_source_link.sq
data/src/main/sqldelight/tachiyomi/migrations/50.sqm
app/src/main/java/exh/recs/matching/CrossExtensionMatchScreenModel.kt
app/src/main/java/eu/kanade/domain/KMKDomainModule.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupCrossSourceMangaLink.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/TasteBackupCreator.kt
app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/TasteRestorer.kt
app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt
```

Questions Claude must answer in the implementation report:

1. Are cross-source links fully implemented in SQLDelight/domain/repository?
2. Are links written after rating/seen/favorite other-version confirmation?
3. Are links backed up at proto `624`?
4. Are links restored safely?
5. Are links merged by sync?
6. Do docs currently contradict code?
7. What, if anything, remains incomplete?

## Required Behavior

### Grouping Priority

Loved Manga grouping should use this ordered strategy:

1. **Confirmed link group**
   - If a loved manga has a `(source, url)` entry in `manga_cross_source_link`, group it with other loved manga in the same `group_id`.
   - This should work even if titles/descriptions differ.
   - This is the strongest signal because it comes from user-confirmed matching.

2. **Exact stable metadata**
   - If no link group applies, group by exact normalized title plus exact normalized author or artist when available.
   - Author/artist must be non-blank and sufficiently meaningful.

3. **Exact title + similar description**
   - If no link group or author/artist match applies, group by exact normalized title plus high description similarity.
   - Descriptions must be non-blank and long enough.
   - Similarity should tolerate minor source wording differences, punctuation, whitespace, and short boilerplate changes.

4. **High title similarity + same author/artist**
   - If titles are slightly different but author/artist match strongly, group only if title similarity is high.
   - This supports small translated/romanized/localized title differences when another strong field agrees.

5. **Do not group**
   - Do not group title-only matches.
   - Do not group two entries just because descriptions are similar if titles are unrelated.
   - Do not group when all supporting metadata is blank.

### Group Reasons

The grouper should return a reason or confidence category internally, even if the UI does not expose it yet.

Suggested reasons:

```kotlin
enum class LovedMangaGroupReason {
    LINK_GROUP,
    TITLE_AND_AUTHOR,
    TITLE_AND_ARTIST,
    TITLE_AND_SIMILAR_DESCRIPTION,
    SIMILAR_TITLE_AND_AUTHOR,
    SIMILAR_TITLE_AND_ARTIST,
    STANDALONE,
}
```

This makes tests and future UI explainability clearer.

### UI Behavior

Keep UI simple for v0.7.2:

- `Group clear duplicates` toggle remains.
- Grouped entries continue showing `%1$d versions`.
- No separate confidence UI is required in this pass.

Optional but acceptable if cheap:

- show a small reason in debug/log only, not visible UI.

### Sorting

The grouped list should preserve current recency behavior:

- loved entries are initially sorted by `MangaTaste.updatedAt DESC`;
- each group representative should be the most recently loved entry in that group;
- group order should follow the representative's recency.

Do not reorder groups alphabetically unless the user later requests sorting options.

## Data Model Changes

No new SQL table should be needed if `manga_cross_source_link` already exists.

If cross-source link storage is incomplete, Claude should not invent a second table. It should complete or use the existing table.

Loved Manga should load all cross-source links through:

```kotlin
GetCrossSourceMangaLinks.awaitAll()
```

or an equivalent existing interactor.

Inject it into `LovedMangaScreenModel`.

## Suggested Implementation

### 1. Expand Loved Manga Entry Data

Update `LovedMangaDuplicateGrouper.GroupInput` to include:

```kotlin
data class GroupInput(
    val key: String,
    val source: Long,
    val url: String,
    val title: String,
    val description: String,
    val author: String?,
    val artist: String?,
    val linkGroupId: String?,
)
```

Use:

- `entry.manga?.author`
- `entry.manga?.artist`
- `entry.manga?.description`
- cross-source link map keyed by `"source|url"` or a typed key.

### 2. Build Link Map In Screen Model

In `LovedMangaScreenModel.load()`:

1. load all loved taste entries;
2. resolve local manga as today;
3. load all cross-source links;
4. create map:

```kotlin
val linkGroupByKey: Map<String, String>
```

where key is stable `(source, url)`.

5. pass `linkGroupId` into grouper input.

If link loading fails:

- fail open by using empty link map;
- do not fail the whole Loved Manga screen unless the failure is fatal;
- optionally log error.

### 3. Create Smarter Grouper

Modify `LovedMangaDuplicateGrouper.computeGroups()` to apply priority strategy.

Recommended internal process:

1. First group all inputs with non-blank `linkGroupId`.
2. Remove linked inputs from metadata fallback pool.
3. Process remaining entries in original sorted order.
4. For each entry, attempt to merge into an existing metadata group only if safe:
   - exact title + author;
   - exact title + artist;
   - exact title + similar description;
   - high title similarity + author;
   - high title similarity + artist.
5. Otherwise create standalone group.

### 4. Normalization Helpers

Current normalization only lowercases and collapses whitespace. Improve conservatively:

```kotlin
normalizeTitle:
- lowercase
- trim
- remove punctuation-like separators
- collapse whitespace
- remove bracketed source/version noise if safe
- optionally strip common leading articles: "the", "a", "an" only if this does not create blank
```

Do not aggressively remove meaningful words.

For descriptions:

```kotlin
normalizeDescription:
- lowercase
- trim
- collapse whitespace
- remove repeated punctuation
- optionally remove common boilerplate phrases only if obviously generic
```

### 5. Similarity Functions

Use simple deterministic pure functions; do not add a heavy dependency.

Suggested title similarity:

```text
token Jaccard similarity >= 0.80
OR normalized Levenshtein-like ratio >= 0.88 if a local utility exists
```

If no Levenshtein utility exists, use token similarity only.

Suggested description similarity:

```text
token Jaccard similarity >= 0.85
AND both normalized descriptions length >= 80
```

This handles minor wording differences without grouping unrelated entries.

### 6. Unsafe Cases

Never group:

- same title only with blank author/artist/description;
- same generic title such as "Solo", "Hero", "Return", "Player" with no support;
- similar description with different unrelated titles;
- entries where title normalization becomes blank.

Consider a small generic-title guard:

```kotlin
private val GENERIC_TITLES = setOf("hero", "player", "return", "solo", "villain", ...)
```

But avoid overbuilding unless tests need it.

## Tests

Update:

```text
app/src/test/java/exh/recs/loved/LovedMangaDuplicateGrouperTest.kt
```

Required tests:

### Link Group Tests

- same link group groups entries even with different titles.
- same link group groups entries even with different descriptions.
- different link groups do not group.
- linked entries keep the most recent/first sorted entry as primary.

### Metadata Strong Match Tests

- exact normalized title + same author groups.
- exact normalized title + same artist groups.
- exact normalized title + different author does not group unless description strongly matches.
- same title + blank author/artist + blank description does not group.

### Description Similarity Tests

- exact title + near-identical long descriptions groups.
- exact title + clearly different long descriptions does not group.
- short descriptions do not group by description.

### Slight Title Difference Tests

- similar title + same author groups.
- similar title + same artist groups.
- similar title without author/artist does not group.
- translated/romanized title without link group or supporting metadata does not group.

### Safety Tests

- title-only duplicate names remain separate.
- blank metadata remains standalone.
- output order follows first occurrence / most recent representative.
- version count equals grouped member count.

### Screen Model Tests

If practical, add a screen-model/pure builder test proving:

- link groups from `GetCrossSourceMangaLinks` are passed into grouper inputs.

If screen-model testing is awkward, extract a pure builder helper and test that.

## Manual Verification

1. Love two manga from different sources that were previously confirmed as same manga through `Love/Like/Seen/Favorite other versions`.
2. Open Loved Manga.
3. Enable `Group clear duplicates`.
4. Confirm those entries collapse into one card with an `N versions` badge even if title/description differ.
5. Love two unrelated manga with the same title but different author/description.
6. Confirm they do not group.
7. Love two same-title entries with same author.
8. Confirm they group.
9. Disable grouping.
10. Confirm all loved entries show individually again.

## Documentation Requirements

After implementation, create:

```text
docs/recommendations/KMK_RECS_V0_7_2_LOVED_MANGA_SMART_GROUPING_AND_LINK_USAGE_IMPLEMENTATION.md
```

Update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/recommendations/README.md
RECOMMENDATION_VERSIONING.md
app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt
```

Documentation must explicitly state:

- Loved Manga grouping now uses confirmed cross-source links first.
- Metadata fallback is conservative and does not group by title alone.
- Romanized/translated titles are reliably grouped only when link groups exist or strong supporting metadata exists.
- Cross-source link implementation state has been audited and docs have been corrected.

If cross-source link groups are confirmed implemented, update docs that incorrectly say they are deferred.

## What's New

User-facing What's New should mention only app behavior:

```text
- Improved Loved Manga duplicate grouping by using confirmed matching versions first.
- Group clear duplicates now handles more same-manga versions while avoiding title-only merges.
```

Do not mention internal SQLDelight tables, proto numbers, or documentation cleanup in What's New.

## Risks

### False Positive Grouping

Risk:

- grouping distinct manga with similar names.

Mitigation:

- never group by title alone;
- link groups first;
- require author/artist or long similar description for metadata fallback.

### False Negative Grouping

Risk:

- romanized/translated titles still do not group if no link or supporting metadata exists.

Mitigation:

- document that user-confirmed cross-source links are the reliable path;
- encourage cross-extension matching actions to create links.

### Documentation Drift

Risk:

- docs already conflict with code about link groups.

Mitigation:

- audit link implementation and update docs in this pass.

## Recommendation

Proceed with v0.7.2 after approval. This should be a Loved Manga stabilization pass, not a broad recommendation rewrite. The key improvement is to use confirmed cross-source link groups as primary identity evidence, then apply conservative metadata fallback for cases where no link exists.

