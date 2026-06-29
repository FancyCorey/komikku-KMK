# KMK-Recs v0.7.2: Loved Manga Smart Grouping And Link Usage — Implementation Report

Date: 2026-06-20

Status: implemented

Feature version: `KMK-Recs v0.7.2`

APK: `Komikku-v1.13.6-kmk.7.2-debug.apk` (VERSION_CODE 720)

## Problem Fixed

The `Group clear duplicates` toggle in the Loved Manga view appeared to do nothing for most users. The old grouper only merged entries when:

- normalized title matched exactly, AND
- normalized description matched exactly, AND
- description was at least 50 characters long.

This was too strict: different sources use different descriptions, have missing descriptions, or have translated vs romanized titles. Cross-extension matching workflow had already written same-manga link groups to the database, but Loved Manga was not using them.

## Cross-Source Link Group Audit

Before implementing, all cross-source link group code was audited. Result: **link groups are fully implemented and were never actually deferred.** The docs incorrectly said they were "deferred to a future version."

Verified implementation:

| Component | Status |
|---|---|
| `data/src/.../manga_cross_source_link.sq` | Fully implemented. SELECT getAll, getByGroupId, getBySourceUrl. Upsert with ON CONFLICT. Delete by (source,url) or group_id. |
| `data/src/.../migrations/50.sqm` | Migration creates table and group index. |
| `domain/.../CrossSourceMangaLink.kt` | Domain model: source, url, groupId, title, createdAt, updatedAt. |
| `GetCrossSourceMangaLinks.kt` | Three methods: `awaitAll()`, `awaitByGroupId()`, `awaitBySourceUrl()`. |
| `UpsertCrossSourceMangaLinks.kt`, `DeleteCrossSourceMangaLink.kt` | Implemented. |
| `CrossExtensionMatchScreenModel.kt` | Writes a link group after every confirmed match (rating, seen, or favorite). |
| `BackupCrossSourceMangaLink.kt` | Proto `@ProtoNumber(624)`. |
| `TasteBackupCreator.kt` | `backupCrossSourceMangaLinks()` exports all links. |
| `TasteRestorer.kt` | `restoreCrossSourceMangaLinks()` upserts per group, preferring newer updatedAt. |
| `SyncService.kt` | `mergeCrossSourceMangaLinks()` merges local and remote by (source, url) key, preferring newer updatedAt. |

**Documentation corrected in this pass:** `CURRENT_STATE.md`, `NEXT_WORK.md` (moved from "Future" deferred to "implemented"), `RECOMMENDATION_VERSIONING.md` planned section updated.

## New Grouping Strategy

`LovedMangaDuplicateGrouper.computeGroups()` now applies a 7-tier ordered evidence strategy:

| Tier | Signal | Threshold |
|---|---|---|
| 1 | Confirmed cross-source link group | same `group_id` in `manga_cross_source_link` |
| 2 | Exact normalized title + same non-blank author | exact string match |
| 3 | Exact normalized title + same non-blank artist | exact string match |
| 4 | Exact normalized title + exact same description | both ≥ 50 chars |
| 5 | Exact normalized title + similar long description | Jaccard ≥ 0.85, both ≥ 80 chars |
| 6 | Similar title + same non-blank author | title Jaccard ≥ 0.80 |
| 7 | Similar title + same non-blank artist | title Jaccard ≥ 0.80 |
| — | No match | standalone entry (never groups by title alone) |

**Never groups by title alone.** Blank/weak metadata is always standalone.

**Romanized/translated titles** (e.g., "Solo Leveling" vs "Only I Level Up") only group when a cross-source link group or strong matching metadata exists. Without those, they remain separate — which is correct for unknown same-manga pairs.

## Implementation Details

### `LovedMangaDuplicateGrouper.kt` (rewritten)

- Added `LovedMangaGroupReason` enum: `LINK_GROUP`, `TITLE_AND_AUTHOR`, `TITLE_AND_ARTIST`, `TITLE_AND_SIMILAR_DESCRIPTION`, `SIMILAR_TITLE_AND_AUTHOR`, `SIMILAR_TITLE_AND_ARTIST`, `STANDALONE`.
- Added `reason: LovedMangaGroupReason` field to `GroupResult`.
- Expanded `GroupInput` to include `source: Long`, `url: String`, `author: String?`, `artist: String?`, `linkGroupId: String?`. All new fields default to null/0 for backward compatibility.
- Tiers 1–4 use `HashMap` fast-path lookups. Tiers 5–7 use an in-order scan list (`unlinkedSlotMeta`) — O(n²) but appropriate for Loved Manga list sizes (typically < 100 entries).
- `normalizeTitle` improved: strips punctuation separators (`-`, `–`, `—`, `:`, `·`, `…`) before collapsing whitespace.
- Added `tokenJaccardSimilarity(a, b)` using word-set intersection/union ratio.

### `LovedMangaScreenModel.kt` (updated)

- Injects `GetCrossSourceMangaLinks`.
- `load()` calls `getCrossSourceMangaLinks.awaitAll()` inside a `runCatching` that fails open with an empty map if link loading fails. The empty-map fallback means Loved Manga always loads even when link storage is unavailable.
- `State.Success` now includes `linkGroupByKey: Map<String, String>` (default `emptyMap()`).
- `buildGroupedItems(entries, linkGroupByKey)` passes all new fields to `GroupInput`: `source`, `url`, `author`, `artist`, `linkGroupId`.

## Files Changed

| File | Change |
|---|---|
| `exh/recs/loved/LovedMangaDuplicateGrouper.kt` | Rewritten — tiered grouping, `LovedMangaGroupReason`, `tokenJaccardSimilarity`, expanded `GroupInput` |
| `exh/recs/loved/LovedMangaScreenModel.kt` | Inject `GetCrossSourceMangaLinks`, pass `linkGroupId` to grouper, add `linkGroupByKey` to `State.Success` |
| `app/src/test/.../LovedMangaDuplicateGrouperTest.kt` | Updated helper, added 22 new tests (34 total) |
| `KmkRecsReleaseNotes.kt` | VERSION_CODE 720, VERSION_NAME v0.7.2, new What's New entries |
| `docs/recommendations/CURRENT_STATE.md` | Version, grouping rules, cross-source link doc correction, test count |
| `docs/recommendations/NEXT_WORK.md` | v0.7.2 bug resolved, cross-source links section updated from "future" to "implemented" |
| `docs/recommendations/README.md` | Plan status updated, implementation report added |
| `RECOMMENDATION_VERSIONING.md` | v0.7.2 entry, planned-section cleanup |

## Tests Run

```
LovedMangaDuplicateGrouperTest — 34 tests, all PASSED

Tests added in v0.7.2 (22 new):
  same link group groups entries even with different titles           PASSED
  same link group groups entries even with different descriptions     PASSED
  different link groups do not merge                                  PASSED
  linked entries keep first-sorted entry as primary                   PASSED
  exact normalized title plus same author groups                      PASSED
  exact normalized title plus same artist groups                      PASSED
  exact normalized title plus different author does not group...      PASSED
  same title with blank author artist and blank description...        PASSED
  exact title plus near-identical long descriptions groups            PASSED
  exact title plus clearly different long descriptions does not group PASSED
  short descriptions do not group by description                      PASSED
  similar title plus same author groups                               PASSED
  similar title plus same artist groups                               PASSED
  similar title without author or artist does not group               PASSED
  translated or romanized title without link group... does not group  PASSED
  title-only duplicate names remain separate                          PASSED
  blank metadata entries remain standalone                            PASSED
  output order follows first occurrence most recent representative    PASSED
  version count equals grouped member count                           PASSED
  State Success with link group map groups entries via linkGroupId    PASSED
  State Success with empty link group map falls back to metadata...   PASSED
  normalizeTitle handles punctuation separators                       PASSED
  tokenJaccardSimilarity returns 1 for identical strings              PASSED
  tokenJaccardSimilarity returns 0 for disjoint strings               PASSED
  tokenJaccardSimilarity returns correct value for partial overlap    PASSED
  similar title threshold requires enough shared tokens               PASSED

:app:testDebugUnitTest --tests "*LovedManga*"  BUILD SUCCESSFUL
:app:testDebugUnitTest                          BUILD SUCCESSFUL
:app:assembleDebug                              BUILD SUCCESSFUL
```

## What's New (user-facing)

```
- Improved Loved Manga duplicate grouping by using confirmed matching versions first.
- Group clear duplicates now handles more same-manga versions while avoiding title-only merges.
```

## Known Limitations and Follow-Ups

- **Romanized/translated titles still do not group automatically** without a cross-source link or shared author/artist. This is intentional. Use "Love/Like/Seen/Favorite other versions" from manga detail to create a confirmed link, which will then make Loved Manga grouping reliable for those entries.
- **No grouping management UI**: users cannot view, inspect, or delete individual cross-source link groups from any screen. Link groups are created automatically and persist silently. A future management screen is documented in `NEXT_WORK.md`.
- **Loved Manga loads once on open**: live grouping updates while the screen is open are still deferred.
- **Backup/restore for grouping toggle state**: still deferred.
- The O(n²) scan in tiers 5–7 is fine for typical loved manga counts but would degrade with very large lists (1000+). Not a concern in practice.

## Deviations from the Plan

None. All plan sections were implemented as specified. The `GroupResult.reason` field is stored but not yet exposed in the UI, as the plan noted this as "optional and acceptable."
