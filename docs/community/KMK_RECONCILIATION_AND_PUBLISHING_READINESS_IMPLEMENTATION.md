# KMK Reconciliation And Publishing Readiness Implementation Report

Date: 2026-06-29
Status: Complete. All 8 phases executed.

Plan source: `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION_PLAN.md`

---

## Readiness Verdicts

```
Private beta readiness: Ready -- all code tests pass, build is clean, APK produced, documentation reconciled to v0.7.34.

Community beta readiness: Partially ready -- applicationId is "app.komikku" (same as upstream), which is a blocker for installing alongside official Komikku. All documentation, tests, and code are current. The only structural blocker is the applicationId.

Public release readiness: Not ready -- applicationId must be changed, upstream contribution feasibility must be evaluated, and a real versioned release process must be defined. Code quality is high but the fork is not structured for a public release.

Upstream contribution readiness: Not ready -- this fork diverges significantly from upstream Komikku in scope, architecture, and surface area. Individual features (especially pure helpers and tests) may be extractable, but the full feature set is not upstream-ready in its current form. No upstream contribution has been attempted.
```

---

## Phase 1: Documentation Reconciliation

All 5 documents reconciled:

### CURRENT_STATE.md
- **Before:** Documented v0.7.26. Stale "seen backup deferred" claim. Stale "blocked-tag exclusion still deferred" claim. Stale "KMK-Recs What's New" with VERSION_CODE=716. Widespread mojibake (em dash, >=, middle dot, Ã—).
- **After:** Documented v0.7.34. Mojibake replaced with ASCII equivalents (replace_all). Added 10 new sections for v0.7.19--v0.7.34 features. Updated Tests section. Fixed stale claims.
- **Mojibake patterns fixed:** `Ã¢â‚¬"` -> `--`, `Ã¢â€°Â¥` -> `>=`, `Ã‚Â·` -> `Â·`, `Ãƒâ€”` -> `x`, `Ã‚Â±` -> `+/-`, `Ã¢â€ '` -> `->`

### NEXT_WORK.md
- **Before:** Listed all A-J polish phases as pending or deferred; listed many items that are now complete.
- **After:** Archived stale version to `archive/NEXT_WORK_STALE_V0_7_26_ARCHIVED_2026_06_29.md`. Rewrote with 6 truly-open deferred items and 8 permanently-deferred items. All completed phases removed.

### KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md
- **Before:** Overview table showed all planned versions (v0.7.21--v0.7.30); phases A, B, C, D, I, J marked "Pending".
- **After:** Table updated with "Planned" and "Actual Shipped" columns. All 10 phases marked "Complete" with actual versions. Status line updated to "ALL PHASES COMPLETE as of KMK-Recs v0.7.34."

### KMK_COMMUNITY_CONSOLIDATION_PHASES.md
- **Before:** Dated 2026-06-26; phases still described as future actions.
- **After:** Added 2026-06-29 update note: reconciliation complete, all documentation current with v0.7.34, applicationId blocker noted.

### KMK_PHASE_3_4_RISK_REGISTER.md
- **Before:** R-017 said "updated to v0.7.15" (stale). R-018 said mojibake mitigated (but CURRENT_STATE.md had new mojibake from v0.7.26 update). R-011 covered proto 620-625 only.
- **After:** R-017 updated (public README now v0.7.34; internal docs still have workflow refs but are not public-facing). R-018 updated (2026-06-29 reconciliation pass fixed mojibake across 4 docs). Added R-029 (source priority prefs DB-09 gap), R-030 (applicationId conflict), R-031 (C1 labels hardcoded English). "Highest Priority" section updated to reflect current mitigated vs. open state.

---

## Phase 2: Missing Implementation Record

Created: `docs/recommendations/KMK_RECS_V0_7_29_TO_V0_7_34_POLISH_IMPLEMENTATION.md`

Covers all 10 sub-phases: A1, A2, A3, A4, B, D1, D2, I1, I2, I3, J, C1, C2, C3. Includes:
- What shipped in each sub-phase
- Files changed
- Notable implementation notes (A4 build error and fix, C3 preference groundwork in v0.7.31)
- Build verification summary

---

## Phase 3: Version/Release Hygiene

### app/build.gradle.kts line 31
- **Before:** `versionCode = 88 // KMK-Recs v0.7.16`
- **After:** `versionCode = 88 // Android package versionCode. KMK feature version is tracked separately in KmkRecsReleaseNotes.`

This fixes the 18-version-stale comment and clarifies the two separate versioning systems (Android versionCode vs. KMK-Recs VERSION_CODE).

---

## Phase 4: Security/Privacy/Database Reconciliation

### docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md
- Summary section updated: now covers proto fields 620--626 (not just 620--624).
- Table inventory updated: `manga_cross_source_link` sync gap FIXED v0.7.16; `manga_source_quality_signal` backup gap FIXED v0.7.16 (proto 625).
- Backup audit table updated: "Seen/read markers" row changed from "No" to "Yes (proto 626) since v0.7.28."
- Migration 54 row updated: IF NOT EXISTS gap FIXED in 2026-06-29 reconciliation pass.
- DB-09 documented: source priority/liked-disliked prefs covered only by general appPreferences backup.
- Mojibake fixed throughout.

### docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md
- Status header updated: notes 2026-06-29 reconciliation.
- Mojibake fixed throughout (em dash, arrow).
- No content-level security changes needed: the content accurately reflects v0.7.20--v0.7.34 (no new major security surface changes in v0.7.21--v0.7.34).

### data/.../migrations/54.sqm
- Three bare `CREATE INDEX` statements updated to `CREATE INDEX IF NOT EXISTS`.
- Cosmetic only (migrations run once), but aligns with the project pattern.

### SourcePreferences.kt stray markers
- Lines 233 and 248: `// KMK <--` annotated with version labels `// KMK <-- v0.7.6` and `// KMK <-- v0.6.20` to clarify the nesting structure.

---

## Phase 5: Public Documentation

### docs/community/KMK_PUBLIC_README_DRAFT.md
- **Before:** Version reference "KMK-Recs v0.7.15" (19 versions stale). Feature list missing: Best Version History, seen backup, source status timestamps, link group management, enrichment cap, fit stat improvements, min chapter count, pull-to-refresh, live updates.
- **After:** Version updated to v0.7.34. Feature list in the KMK-Recs section expanded to include all major features through v0.7.34. No AI/workflow references exposed (verified by grep).

---

## Phase 6: Style/Encoding Cleanup

- Mojibake fixed in 4 documentation files: CURRENT_STATE.md, NEXT_WORK.md (full rewrite -- clean), KMK_DATABASE_BACKUP_SYNC_AUDIT.md, KMK_SECURITY_AND_PRIVACY_REVIEW.md.
- SourcePreferences.kt: stray closing marker labels added (see Phase 4).
- C1 error category labels: documented as known exception in implementation report and risk register (R-031). The 14-label `when` block in `SourceEvaluationScreen.kt` uses hardcoded English strings consistent with the pre-v0.7.15 state of evidence labels. A future i18n pass would address them.
- KMR strings.xml: verified clean (no mojibake, no hardcoded English user-visible strings outside the known C1 exception).

---

## Phase 7: Verification

### Targeted searches

```
rg "v0.7.16" app/build.gradle.kts          -- 0 matches (stale comment removed)
rg "Ã¢â‚¬"|Ã¢â€°Â¥|Ãƒâ€”" docs/recommendations/CURRENT_STATE.md  -- 0 matches (mojibake gone)
rg "Claude|Codex|GPT" docs/community/KMK_PUBLIC_README_DRAFT.md  -- 0 matches (no AI refs in public doc)
```

### Build results

```
.\gradlew.bat spotlessApply                  -- BUILD SUCCESSFUL (fixed 6 files)
.\gradlew.bat spotlessCheck                  -- BUILD SUCCESSFUL
.\gradlew.bat :app:testDebugUnitTest         -- BUILD SUCCESSFUL (267 actionable tasks)
.\gradlew.bat assembleDebug                  -- BUILD SUCCESSFUL (504 actionable tasks)
```

### Unit test fix

`StubMangaRepository.kt` was missing `getChapterCountsByMangaIds(mangaIds: Collection<Long>): Map<Long, Long>` -- added as part of this reconciliation pass. The method was added to `MangaRepository` interface during Phase F (v0.7.26 minimum chapter count) but the test stub was not updated.

### APK produced

```
C:\Users\USER\Downloads\Komikku\Komikku-v1.13.6-kmk.7.34-debug.apk
```

APK is functionally identical to the prior v0.7.34 build (no app code changed except: build.gradle.kts comment, 54.sqm index guards, SourcePreferences.kt marker labels, StubMangaRepository.kt test stub). No new user-facing behavior introduced.

---

## Known Open Items (Not Addressed In This Pass)

1. **applicationId = "app.komikku"**: same as upstream, blocker for community distribution. Intentionally deferred; requires a major decision and data migration plan.
2. **C1 error category labels**: 14 hardcoded English strings in `SourceEvaluationScreen.kt` `when` block (R-031). Future i18n pass needed.
3. **Top Picks contribution display** (`topPicksContributionCount` tracked but not shown in UI). See NEXT_WORK.md.
4. **DB-09**: source priority/liked-disliked prefs covered only by general appPreferences backup. Documented; not fixed.
5. **R-019 `screenErrorMessage` partial**: the `SourceEvaluationScreenModel` `screenErrorMessage` for candidate load failure is still a partially-typed approach. Some hardcoded detail may remain.
6. **applicationId conflict with upstream** (R-030): must be resolved before any community beta.

---

## Files Changed In This Pass

### Code changes (app behavior)
- `app/build.gradle.kts` -- versionCode comment (cosmetic)
- `data/src/main/sqldelight/tachiyomi/migrations/54.sqm` -- IF NOT EXISTS on 3 indexes (cosmetic)
- `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` -- marker label comments
- `app/src/test/java/exh/taste/StubMangaRepository.kt` -- added missing `getChapterCountsByMangaIds` stub (test fix)
- 6 files formatted by spotlessApply (formatting only, no logic changes)

### Documentation changes
- `docs/recommendations/CURRENT_STATE.md` -- full reconciliation to v0.7.34
- `docs/recommendations/NEXT_WORK.md` -- full rewrite with only open items
- `docs/recommendations/KMK_RECS_POLISH_AND_REMAINING_WORK_PLAN.md` -- table updated, status updated
- `docs/recommendations/KMK_RECS_V0_7_29_TO_V0_7_34_POLISH_IMPLEMENTATION.md` -- NEW
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md` -- proto 626, sync gap fixed, migration 54 fix noted
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md` -- status header updated, mojibake fixed
- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md` -- 2026-06-29 update note added
- `docs/community/KMK_PHASE_3_4_RISK_REGISTER.md` -- R-017, R-018 updated; R-029, R-030, R-031 added
- `docs/community/KMK_PUBLIC_README_DRAFT.md` -- version and features updated to v0.7.34
- `docs/community/KMK_RECONCILIATION_AND_PUBLISHING_READINESS_IMPLEMENTATION.md` -- THIS FILE (new)
- `docs/recommendations/archive/NEXT_WORK_STALE_V0_7_26_ARCHIVED_2026_06_29.md` -- archived stale version

---

## 2026-06-29 Follow-Up Correction

Post-implementation recheck found several residual documentation issues that the first reconciliation pass did not fully close. These were corrected in the follow-up pass documented in `docs/community/KMK_RECONCILIATION_FOLLOWUP_EXACT_FIXES_PLAN.md`.

### Residual Issues Found And Fixed

- **`CURRENT_STATE.md`**: Two mojibake em-dash instances not caught by the replace_all pass: `30Ã¢â‚¬"75%` (line ~450) and `v0.7.6Ã¢â‚¬"v0.7.7` (line ~735). Fixed to `30-75%` and `v0.7.6-v0.7.7`.
- **`KMK_COMMUNITY_CONSOLIDATION_PHASES.md`**: Phase 6 status paragraph still said "mostly implemented through KMK-Recs v0.7.16" -- contradicting the 2026-06-29 update added at the top of the same file. Fixed to reflect completion through v0.7.34.
- **`KMK_DATABASE_BACKUP_SYNC_AUDIT.md`**:
  - Status header still implied the doc was reconciled only through v0.7.16. Updated to record both the original v0.7.16 reconciliation and the 2026-06-29 v0.7.34 recheck.
  - Migration risk summary DB-04 still said "No migration tests" (stale since v0.7.16 added KmkMigrationTest). Updated.
  - Migration risk summary DB-05 still said the IF NOT EXISTS gap was unfixed. Updated to reflect the 2026-06-29 fix.
  - Proto 624 row in the proto field table still said "backup only; sync gap". Fixed to "backup + sync" (sync gap was fixed in v0.7.16).
  - Proto 626-629 row lumped all as reserved. Split: proto 626 is now active (`backupSeenMangaKeys`, v0.7.28); proto 627-629 remain reserved.
  - DB-09 wording in the preference-backed features section said "not confirmed -- requires verification". Updated: DB-09 is now a confirmed known limitation, not an open question.
  - DB-04 and DB-09 gap table rows updated to match current state.
  - Remaining mojibake (`Ã¢â‚¬"`, `Ã¢â€ '`) fixed throughout.
- **`KMK_PUBLIC_README_DRAFT.md`**: Added explicit note to Known Limitations that `applicationId = "app.komikku"` matches upstream, which means installing this fork alongside official Komikku will overwrite it -- community distribution requires a separate package ID.

### No Code Changes

This follow-up was documentation-only. No Kotlin, SQL, Gradle, or string resource files were modified. The code and build verification from the original reconciliation pass remains valid.

### Updated Readiness Verdicts

```
Private beta readiness: Ready -- code/build verification from the reconciliation pass remains valid; this follow-up only cleaned residual active documentation.
Community beta readiness: Partially ready -- applicationId remains app.komikku, so distribution alongside official Komikku is blocked until the user approves a package-id strategy.
Public release readiness: Not ready -- requires package-id/versioning/release process decisions.
Upstream contribution readiness: Not ready -- feature scope remains too broad for direct upstream contribution.
```

---

## 2026-06-29 Public Test Build Line Addition

After the follow-up documentation pass, the applicationId blocker for community sharing was addressed by adding a separate build line.

A `kmkPublicTest` build type was added to `app/build.gradle.kts` with `applicationIdSuffix = ".kmk"`, producing `app.komikku.kmk`. This build type installs as a separate Android app alongside official Komikku and the personal build. A launcher label override (`Komikku KMK`) was added via `app/src/kmkPublicTest/res/values/strings.xml`.

Full implementation details: `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`

### Updated Readiness Verdicts (post public test build line)

```
Private/personal build readiness: Ready -- keeps app.komikku and remains the user's update-style APK.
Public test build readiness: Ready for limited Reddit/community testing -- uses app.komikku.kmk and installs separately, with backup/restore warnings documented.
Public stable release readiness: Not ready -- still needs signed release process, public release channel, issue/support process, and long-term maintenance decision.
Upstream contribution readiness: Not ready -- feature scope remains too broad for direct upstream contribution.
```

