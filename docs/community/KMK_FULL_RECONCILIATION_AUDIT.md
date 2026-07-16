# KMK Full Reconciliation Audit

**Date:** 2026-06-29
**Auditor:** Claude Code (audit-only pass â€” no code changed)
**Scope:** Full fork delta, all features, all docs, all versions
**Baseline:** Komikku v1.13.6 (upstream HEAD ~`582ea3e`), KMK-Recs v0.7.34

> This is an audit report. It records what is true in the codebase today.
> It does not recommend immediate action on everything; it prioritizes.
> Sections 11 and 12 contain the verdict and the prioritized cleanup plan.

---

## Section 1 â€” Current Observed Version State

### App-level version (`app/build.gradle.kts`)

| Field | Value | Note |
|---|---|---|
| `versionCode` | 88 | Comment says `// KMK-Recs v0.7.16` â€” **stale by 18 minor versions** |
| `versionName` | `"1.13.6"` | Tracks upstream Komikku; correct |
| `applicationId` | `"app.komikku"` | **Same as upstream Komikku** â€” collision risk for community distribution |
| Debug suffix | `.dev` | Correct |

The versionCode comment `// KMK-Recs v0.7.16` has not been updated since versionCode was 88. The actual KMK-Recs release line is now at v0.7.34. The comment is stale, but versionCode itself is a binary integer and not directly user-visible. The stale comment is misleading for future rebases.

### KMK-Recs internal version (`KmkRecsReleaseNotes.kt`)

| Field | Value |
|---|---|
| `VERSION_CODE` | 734 |
| `VERSION_NAME` | `"KMK-Recs v0.7.34"` |

This is the authoritative KMK-Recs version and is correct as of the last implementation session. The in-app "What's New" sheet covers v0.7.0 through v0.7.34 with release notes entries.

### Upstream baseline

- Komikku v1.13.6 is the upstream baseline, confirmed from release evidence (tag `v1.13.6`, commit `9030e81`, released 2026-05-19)
- Local working tree is on branch `docs/recommendation-search-research`
- No local git tags are present
- The fork has not been git-initialized with KMK-specific commits or branching (confirmed from memory: version control strategy is deferred)

### Consolidated version summary

| Source | Version claim | Staleness |
|---|---|---|
| `app/build.gradle.kts` comment | v0.7.16 | **Critical stale** (18 versions behind) |
| `KmkRecsReleaseNotes.kt` | v0.7.34 | Current |
| `CURRENT_STATE.md` | v0.7.26 | **8 versions behind** |
| `NEXT_WORK.md` | v0.7.25â€“v0.7.26 | **8â€“9 versions behind** |
| `KMK_PUBLIC_README_DRAFT.md` | v0.7.15 | **19 versions behind** |
| Polish plan (Aâ€“J) | v0.7.21â€“v0.7.30 (planned) | **All wrong â€” actual A=v0.7.29, J=v0.7.34** |

---

## Section 2 â€” Implementation Documentation Coverage

### Coverage by feature area

| Feature Area | Plan Doc | Implementation Doc | Status |
|---|---|---|---|
| Taste profile (ratings, tags, aliases) | Phases 1â€“3 master plan | Phase 1â€“3 impl docs | Complete through v0.7.20 |
| For You recommendations | v0.4.xâ€“v0.6.x plans | v0.4.xâ€“v0.6.x impl docs | Complete |
| Source Evaluation (core) | v0.6.8 plan + detailed impl plan | v0.6.8 impl doc | Complete |
| Source Evaluation (crash quarantine) | v0.6.16â€“v0.6.18 plans | v0.6.16â€“v0.6.18 impl docs | Complete |
| Source Evaluation (background exec) | v0.6.19 plan | v0.6.19 impl + followup impl | Complete |
| Source Evaluation (reassessment) | v0.6.20 plan | v0.6.20 impl | Complete |
| Source Evaluation (hardening) | v0.7.11â€“v0.7.13 plans | v0.7.11â€“v0.7.13 impl docs | Complete |
| Loved Manga / cross-source matching | v0.7.0â€“v0.7.3 (no separate plan found) | v0.7.0â€“v0.7.3 impl docs | Complete |
| Best Version / visual quality | v0.7.6â€“v0.7.9 plans | v0.7.6â€“v0.7.9 impl docs | Complete |
| Recommendation Quality (fit scoring) | v0.7.4, v0.7.6â€“v0.7.7 plans | Corresponding impl docs | Complete |
| Bundle import/export | v0.7.5 plan | v0.7.5 impl + hardening addendum | Complete |
| UX formatting / settings | v0.7.14 plan | v0.7.14 impl | Complete |
| Phase 6/7 cleanup & alignment | v0.7.15 plan | v0.7.15 impl | Complete |
| **Polish phases Aâ€“J (v0.7.29â€“v0.7.34)** | Polish plan (versions wrong) | **No implementation doc** | **Gap** |
| OCR feature | `docs/ocr/README.md` | Build-separate; impl not tracked in docs/ | Partial |
| Source priority / fit stats | No dedicated plan | Inline with For You releases | Partial |
| Database/backup/sync | DB audit doc | DB audit doc (serves as impl doc) | Present |
| Security/privacy | Security review doc | Security review doc (serves as audit) | Present |
| Community consolidation | 12-phase consolidation plan | Per-phase plan docs | Phases 0â€“7 complete |

### Missing implementation docs

- **Phases Aâ€“J (v0.7.29â€“v0.7.34):** The six polish phases completed in this planning cycle have no implementation record. The polish plan document exists but has incorrect version numbers. This is the only major documentation gap in the implementation record.
- **KMK-OCR implementation:** OCR is in the codebase but its implementation history is not captured in `docs/recommendations/`. OCR has its own directory (`docs/ocr/`) which was not fully audited in this pass.

---

## Section 3 â€” Feature Reality Matrix

Features are classified by community readiness: **Stable**, **Experimental**, or **Not Ready**.

| Feature | Implemented | Documented | Community Readiness | Notes |
|---|---|---|---|---|
| Manga taste ratings (Love/Like/Dislike) | Yes | Yes | Stable | Core feature, backed up, synced |
| Tag preferences (Block/Dislike/Prefer) | Yes | Yes | Stable | Backed up, synced |
| Tag aliases | Yes | Yes | Stable | 11 built-in aliases seeded on install |
| Rated manga visibility filter | Yes | Yes | Stable | |
| For You recommendations (cross-extension) | Yes | Yes | Stable | Opt-in; no unexpected network calls |
| Recommendation source priority ordering | Yes | Yes | Stable | Drag-to-reorder UI exists |
| Source fit stats (rolling per-source stats) | Yes | Partial | Stable | 16 serialized fields; backward-compat guards |
| Seen/read markers | Yes | Yes | Stable (ephemeral) | Intentionally not backed up |
| Loved Manga view | Yes | Yes | Stable | |
| Cross-source link groups | Yes | Yes | Stable | Backed up (proto 624), sync added v0.7.16 |
| Cross-source link group management UI | **Yes (B: v0.7.30)** | No impl doc | Experimental | Added this session; no separate doc |
| Recommendation bundle import/export | Yes | Yes | Experimental | 2 MB/500-item limits; adequate validation |
| Best Version visual quality | Yes | Yes | Experimental | Needs QA verification of migration/copy |
| Source Evaluation (Private mode) | Yes | Yes | Experimental | Significant security surface; see Section 8 |
| Source Evaluation (Shizuku/Current mode) | Yes | Yes | Experimental | Higher security risk; prompt-required cleanup |
| Source Evaluation (error labels C1) | **Yes (C1: v0.7.31)** | No impl doc | Experimental | Added this session |
| Source Evaluation (retry after connectivity loss C2) | **Yes (C2: v0.7.31)** | No impl doc | Experimental | Added this session |
| Source Evaluation (profile-changed prompt C3) | **Yes (C3: v0.7.31)** | No impl doc | Experimental | Added this session |
| Source Evaluation (last-checked timestamp A1) | **Yes (A1: v0.7.29)** | No impl doc | Experimental | Added this session |
| Enrichment cap (configurable, J: v0.7.34) | **Yes (J: v0.7.34)** | No impl doc | Stable | Default 5; boosted sources get 2Ã— |
| OCR text search | Yes | Partial | Not Ready | Private, local-only; separate build |
| Explicit source filter | Yes | Yes | Stable | Documented as best-effort |
| Recommendation bundle export | Yes | Yes | Experimental | |
| Reassessment (profile change prompt) | Yes | Yes | Stable | |
| Min chapter count filter (v0.7.26) | Yes | No impl doc | Stable | Simple preference filter |
| Safety diagnostics (quarantine UI) | Yes | Yes | Experimental | |
| Startup crash recovery | Yes | Yes | Experimental | |
| Per-manga OCR deletion | API exists | Not confirmed in UI | Not Ready | SEC-08 gap; not verified |

---

## Section 4 â€” Deferred/Remaining Work Reconciliation

### `NEXT_WORK.md` status (last updated ~v0.7.25/v0.7.26)

The file is stale at approximately v0.7.26. Many items marked as deferred have since been completed. The following is a reconciliation pass:

| Category | NEXT_WORK.md says | Actual state |
|---|---|---|
| Error category labels in Source Evaluation | Listed as deferred | **Complete â€” C1 v0.7.31** |
| Retry after connectivity loss | Listed as deferred | **Complete â€” C2 v0.7.31** |
| Profile-changed re-check prompt | Listed as deferred | **Complete â€” C3 v0.7.31** |
| Cross-source link group management UI | Listed as deferred | **Complete â€” B v0.7.30** |
| Source priority last-checked timestamp | Listed as deferred | **Complete â€” A1 v0.7.29** |
| Enrichment cap (configurable) | Listed as deferred | **Complete â€” J v0.7.34** |
| Fit stat time decay | Listed as deferred or future | **Still deferred (D not implemented)** |
| Fullscreen preview (rememberSaveable, etc.) | Listed as deferred | **Still deferred (I not implemented)** |
| Min chapter count filter | Listed as desired | **Complete â€” v0.7.26 (pre-session)** |
| Pull-to-refresh in Source Evaluation | Listed or implied | Status unclear â€” not confirmed in this audit |

**Conclusion:** `NEXT_WORK.md` is significantly stale and should be rewritten or archived. Items that have not been implemented yet:
- Phase D (v0.7.32): Fit stat time decay and Top Picks contribution
- Phase I (v0.7.33): Fullscreen preview rememberSaveable, tap-to-close, Fit toggle

### `KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md` reconciliation

This document planned phases Aâ€“J with version numbers v0.7.21â€“v0.7.30. The actual implementation shifted by +8 minor versions due to intermediate work:

| Phase | Planned version | Actual shipped version |
|---|---|---|
| A | v0.7.21 | v0.7.29 |
| B | v0.7.22 | v0.7.30 |
| C | v0.7.23 | v0.7.31 |
| D | v0.7.24 | **Not yet implemented** |
| I | v0.7.25 | **Not yet implemented** |
| J | v0.7.26 | v0.7.34 |

Phases D and I are the only phases in the original Aâ€“J plan that remain unimplemented.

### `KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md` status

All 7 master phases are complete as of v0.7.20 (confirmed in doc, dated 2026-06-20). The master plan baseline was v0.6.20 and tracked through v0.7.20. This document is accurate.

---

## Section 5 â€” Versioning and Release Hygiene Audit

### Version numbering system

| Component | Format | Example | Who controls |
|---|---|---|---|
| Upstream base | Komikku `v{major}.{minor}.{patch}` | `1.13.6` | Upstream project |
| App versionCode | Integer, incremented per KMK APK | 88 | `app/build.gradle.kts` |
| App versionName | Tracks upstream | `"1.13.6"` | `app/build.gradle.kts` |
| KMK-Recs version | `v0.{minor}.{patch}` | `v0.7.34` | `KmkRecsReleaseNotes.kt` |
| APK filename | `Komikku-v{base}-kmk.{minor}.{patch}-debug.apk` | `Komikku-v1.13.6-kmk.7.34-debug.apk` | Manual convention |

### Hygiene findings

**Critical â€” stale `app/build.gradle.kts` comment:**
Line 31: `versionCode = 88 // KMK-Recs v0.7.16`. The current KMK-Recs version is v0.7.34. This comment is 18 minor versions stale. The versionCode integer itself (88) is correct for the last build; only the comment is wrong.

**High â€” versionCode increment tracking:**
versionCode has been incremented from 81 (OCR snapshot) to 88. There is no changelog or commit history tracking which versionCode corresponds to which KMK-Recs version. The only record is `KmkRecsReleaseNotes.VERSION_CODE`, which currently holds 734 (the KMK-Recs internal version number, not the build versionCode). These two numbers are separate values with no enforced relationship.

**Medium â€” no git tags:**
No local git tags mark KMK releases. APK filenames serve as the sole durable release record. For community distribution, git tags would allow `git describe` to generate version strings and enable bisect/revert workflows.

**Medium â€” phase version plan wrong by 8:**
`KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md` uses version numbers 8 minor versions lower than what was actually shipped. Anyone reading this doc gets incorrect expectations about which APK contains which feature.

**Low â€” KMR release notes cover versions not present in any other doc:**
`KmkRecsReleaseNotes.kt` has entries for v0.7.29 through v0.7.34. These are the only record of the changes in those versions. The implementation docs directory has no corresponding plan/impl files for these phases.

---

## Section 6 â€” Komikku Style/Architecture Alignment Audit

### KMK marker discipline

All KMK-specific code bodies are wrapped in `// KMK -->` / `// KMK <--` markers, consistently applied throughout the codebase. Imports are correctly excluded from markers (imports appear above the marker blocks). This convention is followed across all files audited.

### KMR string discipline

KMK-specific strings are in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` only. No hardcoded user-facing strings were added directly to composables without a `KMR.strings.*` lookup (within the scope of this audit's code reading).

### Composable size and structure

- `SourceEvaluationScreen.kt` is large (1500+ lines) but organized into distinct composable functions (`EvaluationSummaryCard`, `EvaluationResultRow`, `SafetyDiagnosticsRow`, etc.). The structure follows Komikku/Voyager patterns.
- `SourceEvaluationScreenModel.kt` is large but uses a single `State` data class + `mutableState`, following Komikku's standard screen model pattern.
- No architectural anti-patterns (global mutable state, static singletons outside of DI, direct database access from composables) were observed in the files reviewed.

### Injekt DI usage

KMK features use `Injekt.get<T>()` consistently in the same positions Komikku uses it (screen model constructors and lazy initializers). No DI violations were observed.

### Upstream pattern alignment

| Pattern | Komikku uses | KMK follows |
|---|---|---|
| Voyager Navigator | Yes | Yes |
| `ScreenModel` + `mutableState` | Yes | Yes |
| SQLDelight for database | Yes | Yes |
| Moko Resources for strings | Yes | Yes (separate `i18n-kmk` module) |
| Proto backup | Yes | Yes (fields 620â€“629) |
| `PreferenceStore` for settings | Yes | Yes |
| `// KMK -->` markers | N/A | Consistent |

### Style deviations noted

- Migration 54's `CREATE INDEX` statements lack `IF NOT EXISTS` (DB-05). Minor, but deviates from every other KMK migration.
- `SourcePreferences.kt` has two stray `// KMK <--` at lines 233 and 248 that appear to close blocks opened elsewhere without a corresponding open marker at that indent level. This is likely from incremental editing and should be cleaned up.
- `EvaluationResultRow` error category labels (C1) use hardcoded English strings in a `when` block rather than KMR string resources per kind. This is a deliberate tradeoff (avoids 14 new string keys); acceptable for experimental UI but should be revisited before community release.

---

## Section 7 â€” Database/Backup/Sync/Migration Audit

*Full detail in `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`. Summary here.*

### Table inventory (KMK additions)

| Table | Classification | Backed up | Synced | Status |
|---|---|---|---|---|
| `manga_taste` | durable-user-data | Yes (proto 620) | Yes | Clean |
| `tag_taste` | durable-user-data | Yes (proto 621) | Yes | Clean |
| `tag_alias` | durable-user-data | Yes (proto 622) | Yes (first-wins) | Minor: no timestamp merge (DB-06) |
| `recommendation_cache` | derived-cache | No (intentional) | No | Clean |
| `recommendation_disabled_source` | durable-user-data | Yes (proto 623) | Yes | Clean |
| `source_evaluation` | diagnostic-cache | No (intentional) | No | Clean |
| `source_evaluation_probe_marker` | diagnostic-cache | No (intentional) | No | Clean |
| `source_evaluation_unsafe_source` | diagnostic-cache | No (intentional) | No | Clean |
| `unsafe_extension_package` | installer-state | No (intentional) | No | Clean |
| `manga_cross_source_link` | durable-user-data | Yes (proto 624) | **Fixed v0.7.16** | Clean |
| `source_recommendation_fit` | diagnostic-cache | No (intentional) | No | Clean |
| `manga_source_quality_signal` | durable-user-data | **Fixed v0.7.16** (proto 625) | No | Clean |
| `ocr_indexed_page` | privacy-sensitive-local-only | No (correct) | No (correct) | Clean |

### Migration audit (46â€“55)

All 10 KMK migrations use `IF NOT EXISTS` guards except migration 54's three `CREATE INDEX` statements (DB-05). Migration 55 includes a safe data backfill with idempotent conditions. `KmkMigrationTest` provides automated schema consistency testing.

### Open gaps

| ID | Finding | Severity | Recommended action |
|---|---|---|---|
| DB-03 | Migration numbering collision: KMK 46â€“55 could collide with any future upstream migration â‰¥46 | High | Add a comment at top of migrations dir documenting fork boundary |
| DB-05 | Migration 54 `CREATE INDEX` lacks `IF NOT EXISTS` | Low | Fix on next migration file touch |
| DB-06 | Tag alias and disabled-source sync uses first-occurrence merge (no timestamp) | Low | Add `updatedAt` to `BackupTagAlias` in Phase 11 |
| DB-09 | Source priority / liked-disliked sources / rec language filter: backup coverage unclear | Medium | Verify preference key coverage in `BackupOptions.appPreferences` |
| DB-10 | Proto field collision if ever rebased upstream | Medium | Verify upstream proto fields before every rebase |

---

## Section 8 â€” Security/Privacy/Risk Audit

*Full detail in `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`. Summary here.*

### Mitigated risks (as of v0.7.16)

| ID | Area | Resolution |
|---|---|---|
| SEC-01 | Shizuku extension left behind on process death | Mitigated â€” probe marker triggers quarantine; leftover pkg surfaced with uninstall action |
| SEC-02 | Prompt-required cleanup not surfaced | Mitigated â€” post-batch summary includes in-screen cleanup action |
| SEC-03 | Consent not re-triggered on installer mode change | Mitigated â€” switching to SHIZUKU/CURRENT resets consent |
| SEC-06 | No OCR pre-indexing privacy warning | Mitigated â€” confirmation text discloses local storage |
| DB-01 | Best Version quality signals not backed up | Mitigated â€” proto 625 added in v0.7.16 |
| DB-02 | Cross-source link groups missing from sync payload | Mitigated â€” added to SyncManager in v0.7.16 |

### Remaining open risks

| ID | Area | Severity | Finding |
|---|---|---|---|
| SEC-04 | `SourceEvaluationJobState` in-memory only | High | Process death causes graceful (visible) failure, not silent state loss. Extension cleanup may be incomplete â€” SEC-01 covers the install-left-behind case. Not currently fixable without WorkManager data serialization. |
| SEC-05 | 24-hour stale marker threshold | Low | Device suspended >24h mid-evaluation: crash marker cleared without quarantine. Edge case. |
| SEC-07 | OCR text stored in plaintext | Low | Acceptable on unrooted devices (app sandbox). Known and documented. |
| SEC-08 | Per-manga OCR deletion UI not confirmed accessible from library/manga detail | Low | Deletion API exists; surface-level audit did not confirm a UI entry point. |
| SEC-09 | Bundle import: up to 500 items, 200ms delay per item, no user-facing network warning | Medium | Partially mitigated. A network-impact warning is still missing from the import screen. |
| SEC-10 | Missing-source bundle install uses global installer preference | Low | Consistent with normal extension install UX. Acceptable. |

### Diagnostics export safety

The "Copy Diagnostics" clipboard export does **not** include: manga titles, OCR text, ratings, taste profile weights, bundle contents, auth tokens, or credentials. Risk level: low. Safe for issue reports.

### OCR privacy posture

`ocr_indexed_page` is correctly excluded from all backup, sync, and export paths. The table can grow proportionally with indexed chapter count. Per-manga deletion API exists. A full privacy audit of the OCR UI entry points (per-manga deletion from library/manga detail) is still pending (SEC-08).

---

## Section 9 â€” Encoding/Public Text Audit

### Mojibake in documentation

Two files contain corrupted UTF-8 characters (appearing as Windows-1252 / Latin-1 misencoding):

| File | Examples of corrupted sequences |
|---|---|
| `docs/recommendations/NEXT_WORK.md` | `Ã¢â‚¬"` (em dash), `Ãƒâ€”` (multiplication sign), `Ã¢Å“"` (check mark) |
| `docs/recommendations/CURRENT_STATE.md` | Same pattern throughout; widespread |

These files were likely saved with the wrong encoding at some point. The corrupted characters appear throughout both files. The mojibake is cosmetic (docs are still readable with effort) but unprofessional for community-facing documentation.

**No mojibake was found in app code.** The `i18n-kmk` strings.xml and all Kotlin source files audited are clean.

### AI/Codex workflow references

A search for "Claude", "Codex", and "AI workflow" in the docs directory returned 67+ files. All occurrences are in internal planning/implementation documentation. **No AI workflow references were found in app code (`exh/`, `app/src/`, etc.).**

The internal docs contain phrases like "Claude-generated", "AI workflow context", "Codex session", etc. These are appropriate for internal documentation but should be reviewed before any docs are published as community-facing resources.

### Public README draft (`KMK_PUBLIC_README_DRAFT.md`)

The draft references `KMK-Recs v0.7.15` â€” 19 minor versions behind the current v0.7.34. This document cannot be published without a full rewrite to:
1. Update version references
2. Remove or anonymize AI workflow context in the issue template
3. Update the feature list to reflect the current state (Best Version, Source Evaluation hardening, etc.)

### App-visible strings

All app-visible strings go through `KMR.strings.*` (moko-resources). The `i18n-kmk/base/strings.xml` file contains only `<string name="...">...</string>` entries with no encoding issues. No hardcoded user-visible text was found in the composables audited. The C1 error category labels are the only exception: they use hardcoded English inside a `when` block in `EvaluationResultRow`, flagged as acceptable for now but noted in Section 6.

---

## Section 10 â€” Test Coverage and Verification Audit

### KMK test files

Approximately 51 test files exist under `app/src/test/.../exh/recs/`:

| Category | Count (approx.) | Coverage |
|---|---|---|
| Taste model tests | ~10 | Rating, tag preference, alias normalization |
| Recommendation scorer tests | ~8 | Score computation, fit threshold |
| Source evaluation tests | ~12 | Queue state, probe marker, verdict logic |
| Migration test | 1 (`KmkMigrationTest`) | Structural schema consistency across all 10 KMK migrations |
| Backup/restore round-trip tests | ~5 | Proto 620â€“625 serialization/deserialization |
| Classifier tests | ~3 | `SourceRecommendationFitFailureClassifier`, `ExplicitSourceClassifier` |
| Store tests | ~8 | `SourceFitStatsStore`, `SeenMangaStore`, etc. |
| Other | ~4 | Misc. |

### Coverage gaps

| Area | Test presence | Gap severity |
|---|---|---|
| UI/composable behavior | None (standard for Android) | Acceptable â€” UI tested manually |
| Source Evaluation end-to-end | No integration test | High â€” complex lifecycle, no automated coverage of install/probe/uninstall flow |
| Sync merge correctness | Partial (round-trip only) | Medium â€” `mergeMangaTasteLists` logic has no direct unit test |
| Tag alias first-wins merge | Not confirmed | Low â€” edge case |
| OCR deletion from UI | Not confirmed | Low â€” pending SEC-08 |
| C1/C2/C3 (new this session) | None added | Low â€” new features, simple logic |

### What the tests do NOT cover

Per the audit doc: no automated clean-vs-upgraded schema consistency tests existed prior to v0.7.16. `KmkMigrationTest` was added in v0.7.16 and covers the structural constraint check. Historical crash from a missing table (R-012 in the risk register) is now guarded.

---

## Section 11 â€” Publishing Readiness Verdict

### Verdict: **Not Ready for Community Distribution**

The codebase is technically functional and stable for personal use. It is not ready for community distribution in its current form. The blocking issues are:

**Blocker 1 â€” applicationId collision (CRITICAL):**
`applicationId = "app.komikku"` matches upstream Komikku. If the KMK APK is installed on a device with Komikku installed, it will conflict. Community distribution requires a distinct applicationId (e.g., `app.komikku.kmk` or `eu.kanade.tachiyomi.kmk`). Changing applicationId also changes the package name, breaking existing installations.

**Blocker 2 â€” No versioning infrastructure:**
No git tags, no branching strategy, no automated release pipeline. APK filenames are the only durable release record. Community users have no reliable way to check their version or report reproducible issues without a git tag or release page.

**Blocker 3 â€” Public README is severely stale:**
`KMK_PUBLIC_README_DRAFT.md` references v0.7.15 and has not been updated in ~19 minor versions. Cannot be used as-is.

**Non-blocking issues (should fix before wide community release):**

| Issue | Severity | Required for community? |
|---|---|---|
| Mojibake in NEXT_WORK.md and CURRENT_STATE.md | Medium | No (internal docs) |
| AI/Codex references in 67+ docs files | Medium | Only if docs are published |
| `app/build.gradle.kts` comment stale | Low | No (cosmetic) |
| stale phase version plan doc | Medium | No (internal) |
| DB-03 migration collision risk | High | Ongoing vigilance only |
| SEC-04 in-memory job state | High | Documented, acceptable |
| SEC-08 per-manga OCR deletion UI | Low | No (OCR is separate build) |
| Phase Aâ€“J implementation docs missing | Low | No (internal) |

**Summary:** The code quality and feature completeness are high. The blockers are infrastructure (applicationId, versioning, README), not code correctness.

---

## Section 12 â€” Recommended Cleanup Implementation Plan

Ordered by impact. Work in Phase 11 of the community consolidation plan, or sooner for the blockers.

### Priority 1 â€” Pre-community blockers (do before any external sharing)

1. **Decide applicationId strategy.** Options:
   - Keep `"app.komikku"` and forbid community distribution (current implicit choice)
   - Change to `"app.komikku.kmk"` â€” requires migration notes for existing installs
   - Change to something else
   This is a user decision; do not implement without explicit direction.

2. **Initialize git and establish branching strategy.**
   See `docs/version_control_future.md` memory entry. Create initial commit, set up `main` (stable) and `dev` branches, add KMK-specific `.gitignore` entries if needed.

3. **Rewrite `KMK_PUBLIC_README_DRAFT.md`.**
   Update version references to v0.7.34, update feature list, remove or frame AI-workflow references for public consumption, update issue template diagnostics instructions.

### Priority 2 â€” Documentation hygiene (before publishing any docs)

4. **Fix encoding in `NEXT_WORK.md` and `CURRENT_STATE.md`.**
   Re-save both files as UTF-8, replace corrupted sequences with their intended characters (em dashes, check marks, etc.). Or archive both and replace with a single current-state snapshot.

5. **Archive or rewrite `NEXT_WORK.md`.**
   Most of its deferred items are now complete. Either update it to reflect only Phases D and I as remaining, or archive it and write a fresh `REMAINING_WORK.md`.

6. **Update `KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md`.**
   The phase-to-version mapping is wrong by 8 minor versions throughout. Update the table to reflect actual shipped versions (A=v0.7.29 through J=v0.7.34, with D and I still pending).

7. **Fix `app/build.gradle.kts` comment.**
   Change `// KMK-Recs v0.7.16` to `// KMK-Recs v0.7.34` (or to a policy comment about how to track this, since the comment will always fall behind).

8. **Write Phase Aâ€“J implementation doc.**
   A single `KMK_RECS_V0_7_29_V0_7_34_POLISH_PHASES_IMPLEMENTATION.md` covering what was implemented in each of the six completed polish phases (A=v0.7.29, B=v0.7.30, C=v0.7.31, J=v0.7.34, and the earlier phases). This preserves the implementation record for future reference.

### Priority 3 â€” Database and backup hygiene (Phase 11)

9. **Add migration boundary comment.**
   Add a comment at the top of `app/src/main/sqldelight/migrations/` (or as a `migrations/README.md`) documenting: "KMK occupies migrations 46â€“55. Upstream Komikku v1.13.6 ends at 45. Check upstream migration count before every rebase."

10. **Fix migration 54 `CREATE INDEX` guards.**
    Add `IF NOT EXISTS` to the three `CREATE INDEX` statements in `54.sqm` on the next migration file touch.

11. **Add `updatedAt` to `BackupTagAlias` (DB-06).**
    Enables timestamp-based merge instead of first-occurrence. Low urgency but correct.

12. **Verify preference backup coverage (DB-09).**
    Check whether source priority order, liked/disliked source keys, and recommendation language filter are covered by `BackupOptions.appPreferences`. Document the finding; add explicit backup preference keys if critical settings are not covered.

### Priority 4 â€” Security/privacy improvements (Phase 8/9 follow-up)

13. **Verify per-manga OCR deletion UI (SEC-08).**
    Check whether `OcrIndexRepository.deleteByManga()` is surfaced from the manga detail screen or library long-press. Add an entry point if missing.

14. **Add bundle import network-impact warning (SEC-09).**
    A short informational note in the import screen: "Adding N items to your library will trigger metadata fetches from your installed sources."

15. **Add a prose comment to `Backup.kt` proto field block (DB-10).**
    Document the KMK proto range (620â€“629) and upstream baseline (fields through ~61x as of v1.13.6). Prevents silent collision on next rebase.

### Priority 5 â€” Style/code hygiene (low urgency)

16. **Clean up stray `// KMK <--` markers in `SourcePreferences.kt`.**
    Lines 233 and 248 have closing markers that don't visually match an opening at the same level. Harmless but confusing.

17. **Audit C1 error category labels for KMR strings.**
    The hardcoded English strings in `EvaluationResultRow`'s `when` block (C1 implementation) are acceptable for now but should be moved to KMR string resources if Source Evaluation is ever localized or if these labels change.

18. **Update CURRENT_STATE.md to v0.7.34.**
    The file is 8 versions stale and contains mojibake. Rewrite (not incremental update) is recommended given the scope of changes.

---

*End of audit. No code was modified in this pass.*

