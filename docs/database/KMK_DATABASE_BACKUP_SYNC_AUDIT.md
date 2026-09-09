# KMK Database, Backup, And Sync Audit

Date: 2026-06-27

Status: Phase 3 audit, originally reconciled after KMK-Recs v0.7.16 and rechecked on 2026-06-29 through KMK-Recs v0.7.34. Historical findings remain for traceability; current status rows below mark mitigated, open, or ongoing items.

Baseline: Komikku v1.13.6. Upstream ends at migration 45. KMK adds migrations 46--55.

Related: `docs/community/KMK_PHASE_3_4_DATABASE_SECURITY_PRIVACY_AUDIT_PLAN.md`

---

## Summary

The KMK fork adds 15 custom SQLDelight tables across 12 migrations (46--57). Five tables hold durable user data. Ten tables hold cache, evaluation state, or installer state that is intentionally ephemeral.

Backup coverage is solid for the taste profile via proto fields 620--626. All previously identified backup gaps are now closed:

- **DB-01 mitigated v0.7.16**: `manga_source_quality_signal` is backed up/restored/synced through `BackupMangaSourceQualitySignal` at proto 625.
- **DB-02 mitigated v0.7.16**: `backupCrossSourceMangaLinks` is included in the v0.7.16 `SyncManager` sync payload.
- **Seen manga backup gap closed v0.7.28**: “Seen” manga dismissal keys (previously preference-only) are now backed up at proto 626 via `BackupSeenMangaKey`. Restore is additive. See Backup And Restore Audit section.
- **Group primary version backup gap closed v0.8.1-fix1**: `manga_cross_source_group_primary` (added v0.8.0, migration 62) was durable user data with no backup/restore/sync coverage until this pass. Now backed up/restored/synced at proto 627 via `BackupCrossSourceGroupPrimary`.
- **Group primary sync validation hardened v0.8.1-fix2**: sync merge previously only filtered blank `groupId` rows, looser than restore's `CrossSourceGroupPrimaryRestorePolicy.isValid()` (blank `groupId`, `source == 0L`, blank `url`). Both paths now use the same rule.
- **v0.8.1-fix1 DI regression fixed v0.8.1-fix2 (not a database issue, noted here for traceability)**: the three group-primary interactors (`GetCrossSourceGroupPrimary`, `SetCrossSourceGroupPrimary`, `ClearCrossSourceGroupPrimary`) that `TasteBackupCreator`/`TasteRestorer`/`LovedMangaScreenModel`/`LinkedVersionListScreenModel` depend on were never registered in `KMKDomainModule`, causing an `InjektionException` crash whenever any of those classes were constructed (most visibly, opening Loved Manga). Fixed by registering all three factories in `KMKDomainModule` alongside the existing cross-source-link registrations.

One known medium-severity gap remains (DB-09): source priority order, liked/disliked source keys, and recommendation language preference are stored in `SourcePreferences` as standard preference-store entries. They are only backed up when the user enables `BackupOptions.appPreferences`. There is no KMK-specific backup for these preferences. This is an acceptable known gap -- the data is recoverable by reconfiguring settings.

The migration numbering collision risk is real: KMK occupies 46--55, upstream Komikku v1.13.6 ends at 45, and there is no guard or comment documenting this boundary.

---

## Table Inventory

### Classification Key

- `durable-user-data`: Explicit user decisions that should survive backup/restore.
- `derived-cache`: Re-computable from network or device state; OK to lose.
- `diagnostic-cache`: Crash/probe/evaluation records; safe to clear.
- `installer-evaluation-state`: Quarantine/block records; persist across restarts, not backed up.
- `privacy-sensitive-local-only`: Contains private user content; must not be backed up/synced/exported.

### Inventory

| Table | Migration | .sq File | Feature | Classification | Backed Up | Synced | Safe To Clear |
|---|---|---|---|---|---|---|---|
| `manga_taste` | 46 | `manga_taste.sq` | Manga ratings (Love/Like/Dislike) | durable-user-data | Yes (proto 620) | Yes | No |
| `tag_taste` | 46 | `tag_taste.sq` | Tag preferences (Block/Dislike/Prefer) | durable-user-data | Yes (proto 621) | Yes | No |
| `tag_alias` | 46 | `tag_alias.sq` | Tag grouping aliases | durable-user-data | Yes (proto 622) | Yes (first-wins, no timestamp) | No |
| `recommendation_cache` | 46 | `recommendation_cache.sq` | For You source result cache (TTL) | derived-cache | No | No | Yes |
| `recommendation_disabled_source` | 46 | `recommendation_disabled_source.sq` | User-disabled For You sources | durable-user-data | Yes (proto 623) | Yes (first-wins, no timestamp) | No |
| `source_evaluation` | 47+51 | `source_evaluation.sq` | Source evaluation scores/verdicts | diagnostic-cache | No | No | Yes |
| `source_evaluation_probe_marker` | 48 | `source_evaluation_probe_marker.sq` | Crash detection watchdog (singleton id=1) | diagnostic-cache | No | No | Yes |
| `source_evaluation_unsafe_source` | 48 | `source_evaluation_unsafe_source.sq` | Per-probe crash quarantine records | diagnostic-cache | No | No | Yes |
| `unsafe_extension_package` | 49 | `unsafe_extension_package.sq` | Package-level load block | installer-evaluation-state | No | No | User decision (`removable` flag) |
| `manga_cross_source_link` | 50 | `manga_cross_source_link.sq` | Cross-source identity link groups | durable-user-data | Yes (proto 624) | **Fixed v0.7.16** -- now in sync payload | No |
| `source_recommendation_fit` | 52 | `source_recommendation_fit.sq` | Recommendation-quality probe results | diagnostic-cache | No | No | Yes |
| `manga_source_quality_signal` | 53 | `manga_source_quality_signal.sq` | Best Version confirmed picks | durable-user-data | **Yes (proto 625) since v0.7.16** | No | No |
| `ocr_indexed_page` | 54+55 | `ocr_indexed_page.sq` | Recognized manga page text | privacy-sensitive-local-only | No | No | Yes (delete by manga/chapter/all) |
| `recommendation_candidate_memory` | 56 | `recommendation_candidate_memory.sq` | For You discovered candidate cache | local-only-derived-cache | **No** | **No** | Yes (clearable by user via Reset discovery history) |
| `recommendation_discovery_progress` | 57 | `recommendation_discovery_progress.sq` | For You page-evaluation progress tracker | local-only-derived-cache | **No** | **No** | Yes (cleared together with candidate memory via Reset discovery history) |
| `manga_cross_source_group_primary` | 62 | `manga_cross_source_group_primary.sq` | User-selected primary version per confirmed link group | durable-user-data | **Yes (proto 627) since v0.8.1-fix1** | **Yes (`SyncService.mergeCrossSourceGroupPrimariesPure`) since v0.8.1-fix1; validation matched to restore's `isValid()` rule in v0.8.1-fix2** | No (`Ungroup`/`Remove From Group` clear it via `ClearCrossSourceGroupPrimary`) |

---

## Migration Audit (46--56)

**Upstream baseline confirmed:** Migration 45 adds performance indexes only -- `idx_mangas_source`, `idx_chapters_url`, `idx_mangas_categories_*`, `idx_excluded_scanlators_*`, `idx_history_last_read`, `idx_manga_sync_sync_id_remote_id`. No tables created. All upstream statements use `IF NOT EXISTS` or `DROP IF EXISTS`.

| Num | Feature | Tables Created | Columns Added | IF NOT EXISTS | Modifies Existing | Notes |
|---|---|---|---|---|---|---|
| 46 | Taste profile | `manga_taste`, `tag_taste`, `tag_alias`, `recommendation_cache`, `recommendation_disabled_source` | -- | Yes (all tables + indexes) | No | Data seed: `INSERT OR IGNORE INTO tag_alias` -- 11 built-in alias rows (Yuri/GL, BL/Yaoi, Sci-Fi variants, etc.). No comment declaring upstream baseline was 45. |
| 47 | Source evaluation tables | `source_evaluation` | -- | Yes (table + index) | No | Header comment: "v0.6.8 added table but no migration" -- catch-up migration for existing installs. Safe. |
| 48 | Crash quarantine | `source_evaluation_probe_marker`, `source_evaluation_unsafe_source` | -- | Yes (table + index) | No | Clean. |
| 49 | Extension load quarantine | `unsafe_extension_package` | -- | Yes (table, no index) | No | Clean. |
| 50 | Cross-source link groups | `manga_cross_source_link` | -- | Yes (table + index) | No | Clean. |
| 51 | Source eval version columns | -- | `extension_version_name TEXT`, `extension_version_code INTEGER`, `extension_apk_name TEXT` -> `source_evaluation` | n/a (ALTER TABLE, standard) | Yes | Re-running would error ("duplicate column") but migrations run once. Normal pattern. |
| 52 | Recommendation-quality probe | `source_recommendation_fit` | -- | Yes (table only) | No | Indexes NOT in migration file -- present only in `.sq` schema. On fresh install SQLDelight creates them from schema; on upgrade they may rely on schema reconciliation. |
| 53 | Best Version quality signals | `manga_source_quality_signal` | -- | Yes (table only) | No | Same index-omission pattern as migration 52. |
| 54 | OCR text index | `ocr_indexed_page` | -- | Yes (table); **Fixed 2026-06-29** (three indexes now have `IF NOT EXISTS`) | No | Previously deviated from project pattern. Fixed in reconciliation pass by adding IF NOT EXISTS to all three CREATE INDEX statements. Migrations run once so no behavior change for existing installs. |
| 55 | OCR status columns | -- | `recognized_text_length INTEGER NOT NULL DEFAULT 0`, `recognized_word_count INTEGER NOT NULL DEFAULT 0`, `ocr_status TEXT NOT NULL DEFAULT 'success'` -> `ocr_indexed_page` | n/a (ALTER TABLE) | Yes | Includes safe data backfill: sets `ocr_status = 'failed'` where `error_message IS NOT NULL`, `'empty'` where `raw_text = '' AND error_message IS NULL`. Idempotent conditions. |
| 56 | For You discovery memory | `recommendation_candidate_memory` | -- | Yes (table + 4 indexes) | No | Local-only derived cache. NOT in backup/sync. User-clearable. 18 columns, PK=(source_id, url). `upsert` uses `ON CONFLICT DO UPDATE SET ... page = MAX(page, excluded.page)`. |
| 57 | For You discovery progress | `recommendation_discovery_progress` | -- | Yes (table + 2 indexes) | No | Local-only derived cache. NOT in backup/sync. Cleared together with candidate memory via Reset discovery history. 14 columns, PK=(source_id, query_signature, page). Tracks ALL evaluated page outcomes (success/empty/filtered/error/etc). |

### Migration Risk Summary

- **Collision risk (high -- DB-03)**: KMK occupies 46--57. Upstream v1.13.6 ends at 45. No comment or naming convention documents this. Any future upstream migration >=46 will collide. Must track upstream migration count at each Komikku rebase.
- **Migration tests (DB-04)**: Migration tests added in v0.7.16 (`KmkMigrationTest`); updated in v0.7.38 to cover migration 56; updated in v0.7.39 to cover migration 57. Range is now 46..57 (12 files).
- **Migration 54 index guard deviation (DB-05)**: Fixed in 2026-06-29 reconciliation pass -- all three OCR indexes now use `CREATE INDEX IF NOT EXISTS`.

---

## Schema Reference (Key Details)

### `manga_taste`

```sql
manga_id  INTEGER NOT NULL  PRIMARY KEY
           REFERENCES mangas(_id) ON DELETE CASCADE
source     INTEGER NOT NULL
url        TEXT    NOT NULL
title      TEXT    NOT NULL
rating     INTEGER NOT NULL  -- MangaRating: DISLIKE=-1, LIKE=1, LOVE=2
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
UNIQUE(source, url)
INDEX(rating)
```

### `tag_taste`

```sql
normalized_tag TEXT NOT NULL PRIMARY KEY
display_name   TEXT NOT NULL
preference     INTEGER NOT NULL  -- TagPreference: BLOCK=-2, DISLIKE=-1, PREFER=1
created_at     INTEGER NOT NULL
updated_at     INTEGER NOT NULL
INDEX(preference)
```

### `tag_alias`

```sql
alias            TEXT NOT NULL PRIMARY KEY
normalized_alias TEXT NOT NULL
group_key        TEXT NOT NULL
display_name     TEXT NOT NULL
INDEX(group_key), INDEX(normalized_alias)
```

Seeded on first install with 11 built-in aliases: Yuri/GL, Boys' Love/BL/Yaoi, Sci-Fi/Science Fiction, Isekai/Reincarnation, Mecha/Robot, Historical/Period/Dynasty/Ancient, Harem variants, Slice of Life, Full Color, Long Strip, Webtoon/Manhwa/Manhua.

### `recommendation_cache`

```sql
cache_key           TEXT    NOT NULL PRIMARY KEY  -- profile_fingerprint+source+query composite
source_id           INTEGER NOT NULL
profile_fingerprint TEXT    NOT NULL
query_key           TEXT    NOT NULL
result_manga_ids    TEXT    NOT NULL  -- JSON array
result_scores       TEXT              -- nullable
result_reasons      TEXT              -- nullable
created_at          INTEGER NOT NULL
expires_at          INTEGER NOT NULL
INDEX(source_id), INDEX(expires_at)
```

Cleared by TTL via `deleteExpired`. Purely derived -- no user decisions stored.

### `source_evaluation` (selected columns)

```sql
evaluation_key           TEXT    NOT NULL PRIMARY KEY  -- pkg + sig hash
extension_pkg_name       TEXT    NOT NULL
signature_hash           TEXT    NOT NULL
verdict                  TEXT    NOT NULL  -- SourceEvaluationVerdict enum
quality_score            REAL    NOT NULL
recommendation_fit_score REAL    NOT NULL
search_reliability_score REAL    NOT NULL
explicit_score           REAL    NOT NULL
ecchi_score              REAL    NOT NULL
evaluated_at             INTEGER NOT NULL
sampled_titles_json      TEXT              -- nullable
sampled_tags_json        TEXT              -- nullable
error_message            TEXT              -- nullable
extension_version_name   TEXT              -- nullable, added migration 51
extension_version_code   INTEGER           -- nullable, added migration 51
extension_apk_name       TEXT              -- nullable, added migration 51
```

### `source_evaluation_probe_marker`

Single-row sentinel (always upserted with `id = 1`). Written before risky operations; cleared after evaluation completes or on startup recovery. Never has more than one row.

### `manga_cross_source_link`

```sql
source     INTEGER NOT NULL
url        TEXT    NOT NULL
group_id   TEXT    NOT NULL  -- UUID shared across all versions of same manga
title      TEXT    NOT NULL
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
PRIMARY KEY (source, url)
INDEX(group_id)
```

### `manga_source_quality_signal`

```sql
id                      INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT
origin_source_id        INTEGER NOT NULL
origin_url              TEXT    NOT NULL
origin_title            TEXT    NOT NULL
selected_source_id      INTEGER NOT NULL
selected_url            TEXT    NOT NULL
selected_title          TEXT    NOT NULL
selected_source_name    TEXT    NOT NULL
compared_candidates_json TEXT   NOT NULL DEFAULT '[]'  -- JSON
chapter_number          REAL              -- nullable
chapter_name            TEXT    NOT NULL DEFAULT ''
sample_size             INTEGER NOT NULL DEFAULT 5
sampled_pages_json      TEXT    NOT NULL DEFAULT '[]'  -- JSON; may contain ephemeral page URLs
selected_at             INTEGER NOT NULL
quality_signal_version  INTEGER NOT NULL DEFAULT 1
INDEX(origin_source_id, origin_url)
INDEX(selected_source_id, selected_url)
```

`sampled_pages_json` may contain chapter page URLs. These are ephemeral -- URL validity is source-dependent and not guaranteed to persist. Do not use these URLs as stable references.

### `ocr_indexed_page` (selected columns)

```sql
id               INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT
manga_id         INTEGER NOT NULL
source_id        INTEGER NOT NULL
manga_url        TEXT    NOT NULL
manga_title      TEXT    NOT NULL       -- personal library content
chapter_id       INTEGER NOT NULL
chapter_url      TEXT    NOT NULL
chapter_name     TEXT    NOT NULL       -- personal library content
page_index       INTEGER NOT NULL
page_identity    TEXT    NOT NULL
raw_text         TEXT    NOT NULL       -- OCR recognized text from page
normalized_text  TEXT    NOT NULL       -- search-normalized form of raw_text
indexed_at       INTEGER NOT NULL
error_message    TEXT                   -- nullable
recognized_text_length INTEGER NOT NULL DEFAULT 0   -- added migration 55
recognized_word_count  INTEGER NOT NULL DEFAULT 0   -- added migration 55
ocr_status       TEXT    NOT NULL DEFAULT 'success' -- added migration 55; values: success/failed/empty
UNIQUE(chapter_id, page_index, page_identity, engine_key, engine_version)
```

`raw_text` and `normalized_text` contain the actual recognized text content of manga pages. This is private user data. Must not appear in backup, sync, export, or logs. See security review for full OCR privacy analysis.

---

## Backup And Restore Audit

### Backup Models (Confirmed)

| Feature | Backed Up | Backup Model | Proto# | Notes |
|---|---|---|---|---|
| Manga ratings | Yes | `BackupMangaTaste` | 620 | mangaId(1), source(2), url(3), title(4), rating(5), updatedAt(6). `createdAt` reconstructed from existing or now on restore. Latest-wins by `updatedAt`. |
| Tag preferences | Yes | `BackupTagTaste` | 621 | displayName(1), preference(2), updatedAt(3). `normalizedTag` NOT backed up -- re-derived from `displayName.normalizeTag()` on restore. |
| Tag aliases | Yes | `BackupTagAlias` | 622 | alias(1), groupKey(2), displayName(3). `normalizedAlias` NOT backed up -- re-derived on restore. Insert-only restore (skips if normalized alias already exists). |
| Disabled For You sources | Yes | `BackupDisabledRecommendationSource` | 623 | sourceId(1). Insert-only restore. |
| Cross-source link groups | Yes | `BackupCrossSourceMangaLink` | 624 | source(1), url(2), groupId(3), title(4), updatedAt(5). `createdAt` reconstructed. Latest-wins by `updatedAt`, grouped by `groupId`. Included in sync as of v0.7.16. |
| Seen/read markers | **Yes (v0.7.28)** | `BackupSeenMangaKey` | **626** | Stored as semicolon-separated preference string in `SourcePreferences.seenRecommendationMangaKeys()`. Backed up via proto 626 `BackupSeenMangaKey` as of v0.7.28. Restore is additive (union with existing set). Old backups without field 626 restore cleanly with no seen keys. **v0.7.43:** user-facing meaning/copy changed to "Not interested" (mild negative signal in For You scoring, in addition to the existing exact-title hiding), but the storage key set, format, and proto field number are unchanged -- a backup made before or after v0.7.43 restores identically. |
| Best Version quality signals | Yes | `BackupMangaSourceQualitySignal` | 625 | Implemented in v0.7.16. Stores origin/selected source identity, titles, source name, chapter/sample metadata, sampled page JSON, selectedAt, and quality signal version. |
| Cross-source group primary version | **Yes (v0.8.1-fix1)** | `BackupCrossSourceGroupPrimary` | **627** | groupId(1), source(2), url(3), updatedAt(4). Restored after `backupCrossSourceMangaLinks` (`TasteRestorer.restoreCrossSourceGroupPrimaries`, called after `restoreCrossSourceMangaLinks` in `BackupRestorer`). Latest-wins by `updatedAt`, keyed by `groupId`. Invalid rows (blank groupId, `source == 0`, blank url) are skipped. Included in sync as of v0.8.1-fix1. |
| Source evaluation results | No | -- | -- | Intentional -- diagnostic/cache, re-runnable. |
| Source recommendation fit | No | -- | -- | Intentional -- diagnostic/cache. |
| Recommendation cache | No | -- | -- | Intentional -- TTL cache, re-derivable. |
| OCR text index | No | -- | -- | Correct -- local-only private data, must not be backed up. |
| Crash quarantine / probe markers | No | -- | -- | Intentional -- diagnostic state, re-derived from next run or seed list. |
| Extension load quarantine | No | -- | -- | Intentional -- re-seeded from `KnownUnsafeExtensionPackages` at startup. |

### Preference-Backed Features

`BackupOptions.tasteProfile = true` (index 12) covers the five backed-up tables above. `RestoreOptions.tasteProfile = true` (index 6) with `getOrElse(6) { true }` for backward compatibility.

Source priority order, liked/disliked source IDs, and recommendation language filters are stored as standard `SourcePreferences` entries. These are covered only if the user enables preference backup in `BackupOptions.appPreferences`. If disabled, these settings are lost on restore. There is no KMK-specific backup for these preferences. This is a known limitation (DB-09) -- the data is recoverable by reconfiguring settings.

### Restore Dependency Order

`TasteRestorer` restores manga taste rows by resolving `mangaId` via `(url, source)` lookup from the manga table. Manga entries must be restored before taste entries -- this is the standard Komikku backup restore order. No ordering issue observed.

---

## Sync Audit

### KMK Sync Additions Confirmed

Both `SyncService.kt` and `SyncManager.kt` have KMK additions.

| Feature | Synced | Merge Strategy | Gap |
|---|---|---|---|
| Manga ratings | Yes | `mergeMangaTasteLists()` -- latest-wins by `updatedAt`, keyed on `source|url` | None |
| Tag preferences | Yes | `mergeTagTasteLists()` -- latest-wins by `updatedAt`, keyed on `displayName.lowercase().trim()` | Edge case: two devices with different display-name casing create duplicates |
| Tag aliases | Yes | `distinctBy { it.alias.lowercase().trim() }` -- first-occurrence, no timestamp | No timestamp: newer alias from one device may be silently discarded (DB-06) |
| Disabled sources | Yes | `distinctBy { it.sourceId }` -- first-occurrence, no timestamp | No timestamp merge risk |
| Cross-source link groups | Yes | `SyncService.mergeCrossSourceMangaLinks()` | Fixed in v0.7.16: `SyncManager` includes `backupCrossSourceMangaLinks` in the sync backup payload. |
| Cross-source group primary version | **Yes (v0.8.1-fix1, validation hardened v0.8.1-fix2)** | `SyncService.mergeCrossSourceGroupPrimariesPure()` -- latest-wins by `updatedAt`, keyed by `groupId`; invalid rows filtered via `CrossSourceGroupPrimaryRestorePolicy.isValid()` (blank `groupId`, `source == 0L`, or blank `url` -- same rule restore uses, as of v0.8.1-fix2; v0.8.1-fix1 only filtered blank `groupId`) | None known |
| Recommendation cache | No | n/a | Intentional |
| Seen/read markers | No | n/a | Intentional |
| Source evaluation | No | n/a | Intentional |
| OCR text | No | n/a | Correct -- must not sync |

### Former Sync Gap (DB-02) - Fixed In v0.7.16

`SyncManager.kt` constructs the sync backup with these fields:
```
backupFeeds, backupMangaTastes, backupTagTastes, backupTagAliases, backupDisabledRecommendationSources
```

`backupCrossSourceMangaLinks` was absent from this list in the original audit. v0.7.16 adds it to the `SyncManager` sync backup construction block.

---

## Proto And Field Range Audit

### KMK Proto Fields In `Backup.kt`

| Proto# | Name | Feature | Active | Notes |
|---|---|---|---|---|
| 610 | `backupFeeds` | Existing Komikku feeds/saved searches | Yes | Not KMK-introduced; present in upstream |
| 620 | `backupMangaTastes` | Manga ratings | Yes | -- |
| 621 | `backupTagTastes` | Tag preferences | Yes | -- |
| 622 | `backupTagAliases` | Tag aliases | Yes | -- |
| 623 | `backupDisabledRecommendationSources` | Disabled For You sources | Yes | -- |
| 624 | `backupCrossSourceMangaLinks` | Cross-source link groups | Yes (backup + sync) | Sync gap fixed in v0.7.16 |
| 625 | `backupMangaSourceQualitySignals` | Best Version quality signals | Yes | Added in v0.7.16 |
| 626 | `backupSeenMangaKeys` | For You seen/read dismissals | Yes (backup) | Added in v0.7.28 |
| 627 | `backupCrossSourceGroupPrimaries` | User-selected primary version per confirmed link group | Yes (backup + sync) | Added in v0.8.1-fix1 |
| 628--629 | (reserved) | Available for future KMK features | No | Reserve comment in code |

### Collision Risk

Range 620--629 is explicitly reserved in code comments for this fork's taste system. If upstream Komikku adds proto fields at 620--629, they will collide. The reserve comment is in source code, not in a shared `.proto` schema file. If this fork is ever rebased or PRed upstream, field number conflicts must be resolved before merge.

Recommended action (Phase 11): add a prose comment at the top of `Backup.kt` documenting: "KMK taste system occupies proto fields 620--629. Upstream baseline for v1.13.6 ends at approximately field 6xx. Verify before rebase."

---

## Gaps And Risks

| ID | Area | Severity | Finding | Recommended Action | Phase |
|---|---|---|---|---|---|
| DB-01 | `manga_source_quality_signal` has no backup | High | **Mitigated v0.7.16.** User-confirmed Best Version picks are now backed up/restored/synced through `BackupMangaSourceQualitySignal` proto 625. | Keep round-trip tests in place | Complete |
| DB-02 | `backupCrossSourceMangaLinks` absent from sync payload | Medium | **Mitigated v0.7.16.** `SyncManager` includes cross-source links in sync backup payload. | Keep sync payload tests/audit coverage in place | Complete |
| DB-03 | Migration numbering collision risk | High | KMK 46--55 could collide with any upstream Komikku migration >=46. No comment documents the fork boundary. | Add comment at top of migrations directory | Phase 11 or immediate doc fix |
| DB-04 | Migration tests | High | **Mitigated v0.7.16.** `KmkMigrationTest` executes KMK migrations against in-memory SQLite and checks structural constraints. Originally: no automated tests existed. | Keep tests updated when migrations are added | Complete |
| DB-05 | Migration 54 indexes omit `IF NOT EXISTS` | Low | **Fixed 2026-06-29 reconciliation pass.** All three OCR indexes now use `CREATE INDEX IF NOT EXISTS`. Migrations run once so no behavior change for existing installs. | None | Complete |
| DB-06 | Tag alias and disabled-source sync merge is timestamp-free | Low | First-occurrence wins -- newer custom alias or disabled source from one device could be lost. | Add `updatedAt` to `BackupTagAlias` for timestamp merge | Phase 3 or 11 |
| DB-07 | `SeenRecommendationMangaStore` is preference-only, not backed up | Low | Intentional per docs, but no code comment confirms it. | Add comment to the store class | Phase 10 |
| DB-08 | `manga_source_quality_signal.sampled_pages_json` may contain ephemeral URLs | Low | Page URLs from source chapters may expire. Informational field; do not use as stable references. | Document in code | Phase 10 |
| DB-09 | Source priority / liked-disliked sources / rec language filter not in dedicated backup | Medium | Confirmed: these are standard `SourcePreferences` entries covered only when `BackupOptions.appPreferences` is enabled. If disabled, settings lost on restore. Known limitation -- data recoverable by reconfiguration. | Document; consider explicit KMK preference backup in a future pass | Ongoing |
| DB-10 | Proto field collision if fork rebased to upstream | Medium | Partially mitigated in v0.7.16 by proto round-trip tests for fields 620-625. Rebase vigilance still required. | Verify upstream proto fields before every rebase | Ongoing |

