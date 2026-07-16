# KMK-Recs v0.8.1-fix2 Version Visibility And Sync Validation Plan

Date: 2026-07-12

Status: implementation plan, awaiting user approval before coding.

Build line: **PRIVATE build only**. Do not prepare or switch to the public-test `applicationId`. After successful verification, copy/rename the debug APK into the private handoff folder using the established private naming scheme and document that exact path in the implementation report and `RECOMMENDATION_VERSIONING.md`.

## Purpose

This is a small corrective follow-up to `KMK-Recs v0.8.1-fix1`. It should not add new recommendation features. It should tighten the private build/version workflow and close one backup/sync validation inconsistency found during Codex review.

The user confirmed that `KMK-Recs v0.8.0` What's New notes are visible in the app, but `KMK-Recs v0.8.1-fix1` was not clearly seen. The current code already contains the v0.8.1-fix1 notes in `KmkRecsReleaseNotes.kt`, and the About screen already exposes a manual `KMK-Recs What's new` entry with the current KMK version as subtitle. Therefore this pass must **verify and repair the trigger/visibility path**, not invent a separate release-note system.

## Scope

1. Verify and fix KMK-Recs What's New visibility for v0.8.1-fix1 and the new v0.8.1-fix2 label.
2. Make KMK-Recs version metadata clearly visible in the existing app version/about area.
3. Harden sync merge validation for `BackupCrossSourceGroupPrimary` so it matches restore validation.
4. Strengthen tests around the above.
5. Update docs/versioning and produce a named private APK handoff.

## Non-Goals

- Do not change recommendation scoring, Source Evaluation scoring, For You ranking, group recommendations, OCR, or rated manga bulk actions.
- Do not change the public-test application id or produce a public build.
- Do not reset the user's `kmk_recs_last_seen_version_code` preference globally or force repeat popups for users who already dismissed the current version.
- Do not add a second, duplicate What's New system. Reuse the current Komikku/KMK About and dialog patterns.

## Current Code Facts

### KMK release notes

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
  - Current values:
    - `VERSION_CODE = 751`
    - `VERSION_NAME = "KMK-Recs v0.8.1-fix1"`
  - `MARKDOWN` already begins with `## KMK-Recs v0.8.1-fix1`, followed by `## KMK-Recs v0.8.0`.

### Automatic KMK What's New popup

- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
  - Uses:
    - `Preference.appStateKey("kmk_recs_last_seen_version_code")`
    - `KmkRecsReleaseNotes.VERSION_CODE > kmkRecsLastSeenVersion.get()`
  - Shows `KmkRecsWhatsNewDialog` only in the `else if (showKmkChangelog)` branch after the normal Komikku `showChangelog` branch.
  - Dismiss/open actions set `kmk_recs_last_seen_version_code` to the current `KmkRecsReleaseNotes.VERSION_CODE`.

### Manual KMK What's New entry

- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt`
  - Already includes a `TextPreferenceWidget`:
    - title: `KMR.strings.kmk_recs_whats_new`
    - subtitle: `KmkRecsReleaseNotes.VERSION_NAME`
    - click: `navigator.push(KmkRecsWhatsNewScreen())`

- `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt`
  - Marks KMK notes as seen when opened.
  - Uses `WhatsNewScreen` with:
    - `currentVersion = KmkRecsReleaseNotes.VERSION_NAME`
    - `versionName = KmkRecsReleaseNotes.VERSION_NAME`
    - `changelogInfo = KmkRecsReleaseNotes.MARKDOWN`

### KMK dialog

- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt`
  - Shows only a compact dialog title and buttons.
  - The detailed notes are opened only through the secondary `KMK-Recs What's new` action.

### Group primary backup/sync validation

- Restore validation:
  - `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/CrossSourceGroupPrimaryRestorePolicy.kt`
  - `isValid(primary)` requires:
    - `groupId.isNotBlank()`
    - `source != 0L`
    - `url.isNotBlank()`

- Sync merge validation:
  - `app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt`
  - `mergeCrossSourceGroupPrimariesPure()` currently filters only blank `groupId` rows before merging.
  - This is looser than restore and should be corrected.

## Required Implementation

### Part A - KMK version visibility and What's New behavior

1. Bump the KMK private corrective version:
   - `KmkRecsReleaseNotes.VERSION_CODE`: next private code after `751`.
   - `KmkRecsReleaseNotes.VERSION_NAME`: `KMK-Recs v0.8.1-fix2`.
   - Add a new top changelog section:
     - `## KMK-Recs v0.8.1-fix2`
     - Mention:
       - KMK version/What's New visibility correction.
       - Group-primary sync validation hardening.
       - Private build handoff/process tightening.

2. Verify the automatic popup path:
   - Inspect `MainActivity.kt` and confirm whether the `else if (showKmkChangelog)` ordering can suppress KMK notes when the normal Komikku changelog is also shown.
   - If the normal Komikku dialog can mask the KMK dialog for the same launch, make the behavior deterministic without stacking confusing dialogs:
     - Preferred: after the normal Komikku changelog is dismissed or opened, allow the KMK dialog to show if `KmkRecsReleaseNotes.VERSION_CODE > kmkRecsLastSeenVersion.get()`.
     - Keep the UI calm: do not show both dialogs at the same exact time.
     - Do not mark KMK notes as seen unless the KMK dialog is dismissed/opened, or `KmkRecsWhatsNewScreen` is opened manually.
   - Add comments only where necessary to explain why the normal Komikku changelog and KMK changelog are sequenced separately.

3. Preserve the manual About entry:
   - Keep `AboutScreen.kt`'s `KMK-Recs What's new` row.
   - Its subtitle must show `KmkRecsReleaseNotes.VERSION_NAME`, so the installed private KMK version is visible even if the automatic popup was dismissed.
   - If needed, adjust the label/subtitle only through `i18n-kmk` strings; no hardcoded user-facing text.

4. Improve the compact popup if useful, but do not overbuild:
   - If the dialog currently feels too ambiguous, it may include a short text body such as "A KMK-Recs update is available. Open What's new to view the private feature changes." Use KMR strings.
   - The detailed changelog should remain in `KmkRecsWhatsNewScreen`, not inside the dialog.

5. Add/adjust tests where practical:
   - Prefer a pure helper for deciding KMK changelog visibility/sequence if `MainActivity.kt` logic is otherwise hard to test.
   - Suggested helper: `KmkRecsWhatsNewPolicy`.
   - Cases to cover:
     - KMK version greater than last-seen => should show.
     - KMK version equal/lower than last-seen => should not show.
     - Normal Komikku changelog active and KMK changelog pending => KMK remains pending, not marked seen prematurely.
     - Opening/dismissing KMK marks the current KMK version seen.
   - Do not add a UI instrumentation test unless the project already has a light pattern for this exact app-start dialog path.

### v0.8.1-fix1 crash log

User-provided crash log: `C:\Users\USER\Downloads\komikku_crash_logs.txt`.

Crash action: opening Loved Manga from the For You page in the latest v0.8.1-fix1 private APK.

Top exception:

```text
uy.kohesive.injekt.api.InjektionException:
No registered instance or factory for type class tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
```

Relevant stack:

```text
exh.recs.loved.LovedMangaScreenModel.<init>(LovedMangaScreenModel.kt:473)
exh.recs.loved.LovedMangaScreen.Content(LovedMangaScreen.kt:18)
```

Code audit finding:

- `LovedMangaScreenModel` constructor now requests:
  - `GetCrossSourceGroupPrimary`
  - `SetCrossSourceGroupPrimary`
  - `ClearCrossSourceGroupPrimary`
- `LinkedVersionListScreenModel`, `TasteBackupCreator`, and `TasteRestorer` also request at least `GetCrossSourceGroupPrimary`.
- The interactor files exist under `domain/src/main/java/tachiyomi/domain/taste/interactor/`.
- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` registers cross-source manga link interactors:
  - `GetCrossSourceMangaLinks`
  - `UpsertCrossSourceMangaLinks`
  - `DeleteCrossSourceMangaLink`
- But it does **not** register the new group-primary interactors:
  - `GetCrossSourceGroupPrimary`
  - `SetCrossSourceGroupPrimary`
  - `ClearCrossSourceGroupPrimary`

Therefore this is not a data problem. It is a missing Injekt registration introduced by the v0.8.0/v0.8.1 group-primary work.

### Part B - Group-primary sync validation hardening

0. Fix missing dependency registration first:
   - Update `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`.
   - Add imports:
     - `tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary`
     - `tachiyomi.domain.taste.interactor.SetCrossSourceGroupPrimary`
     - `tachiyomi.domain.taste.interactor.ClearCrossSourceGroupPrimary`
   - Register factories near the existing cross-source link group registrations:

     ```kotlin
     addFactory { GetCrossSourceGroupPrimary(get()) }
     addFactory { SetCrossSourceGroupPrimary(get()) }
     addFactory { ClearCrossSourceGroupPrimary(get()) }
     ```

   - This must happen before any final build because it fixes the direct Loved Manga crash and also protects:
     - `LinkedVersionListScreenModel`
     - `TasteBackupCreator.backupCrossSourceGroupPrimaries()`
     - `TasteRestorer.restoreCrossSourceGroupPrimaries()`

1. Add a DI regression test if the project has a suitable module-registration test pattern:
   - At minimum, test that after `KMKDomainModule` registration, Injekt can resolve:
     - `GetCrossSourceGroupPrimary`
     - `SetCrossSourceGroupPrimary`
     - `ClearCrossSourceGroupPrimary`
   - If a full Injekt module test is too heavy or brittle, document why and add a focused construction test for `LovedMangaScreenModel` using explicit fake dependencies or a lighter pattern already present in `app/src/test/java/exh/recs/TestInjektSupport.kt`.
   - Do not leave this as manual-only if a practical unit test can be added.


1. Update `SyncService.mergeCrossSourceGroupPrimariesPure()`:
   - Replace the current blank-`groupId`-only filter with the same validity rule used by restore:
     - `CrossSourceGroupPrimaryRestorePolicy.isValid(it)`
   - If importing a restore package object into sync feels architecturally awkward, extract the validity check to a neutral small helper/model policy and update both restore and sync to use it.
   - Keep the current merge behavior otherwise:
     - merge by `groupId`
     - newer `updatedAt` wins
     - equal `updatedAt` keeps local

2. Extend `SyncServiceCrossSourceGroupPrimaryMergeTest`:
   - Existing blank-`groupId` invalid-row test should remain.
   - Add explicit tests for:
     - `source == 0L` rows are filtered.
     - blank `url` rows are filtered.
     - valid rows still merge normally after invalid rows are filtered.

3. Ensure restore tests still pass:
   - `CrossSourceGroupPrimaryRestorePolicyTest`
   - `TasteBackupRoundTripTest`

### Part C - Documentation and private APK handoff

1. Update docs:
   - `RECOMMENDATION_VERSIONING.md`
     - Add `KMK-Recs v0.8.1-fix2`.
     - Include the private APK filename/path.
     - State this is private-only.
   - `docs/recommendations/CURRENT_STATE.md`
     - Update the status to v0.8.1-fix2.
     - Mention that the About screen exposes `KMK-Recs VERSION_NAME` as installed KMK metadata.
   - `docs/recommendations/NEXT_WORK.md`
     - Remove or update any item claiming v0.8.1 notes visibility or sync validation is still pending.
   - `docs/recommendations/README.md`
     - Add this plan and the implementation report to the current top-level lists once implemented.
   - `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`
     - Update proto 627 row or notes to state sync now uses the same validity rule as restore.

2. Create implementation report:
   - `docs/recommendations/KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_IMPLEMENTATION.md`
   - Must include:
     - Date
     - Version/build label and `KmkRecsReleaseNotes.VERSION_CODE`
     - User-approved scope
     - Files changed
     - Behavior changed
     - Tests run
     - APK output path
     - Private handoff APK path
     - Known limitations
     - Deviations from this plan

3. Build and private APK handoff:
   - Run private/debug build only.
   - Copy/rename the resulting APK to the private handoff folder using the established private naming scheme.
   - Do not produce public-test APK unless explicitly asked in a separate user request.

## Required Verification

Run at minimum:

```text
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest --tests "*SyncServiceCrossSourceGroupPrimaryMergeTest" --tests "*CrossSourceGroupPrimaryRestorePolicyTest" --tests "*TasteBackupRoundTripTest"
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If a focused KMK What's New helper test or DI registration test is added, include it in the targeted test command too.

Manual QA checklist for the user/tablet:

1. Install the private v0.8.1-fix2 APK over the previous private build.
2. Open the app.
3. Confirm either:
   - the KMK-Recs update dialog appears for v0.8.1-fix2, or
   - if it was already marked seen during testing, About still shows `KMK-Recs v0.8.1-fix2`.
4. Go to More/Settings > About.
5. Confirm `KMK-Recs What's new` subtitle shows `KMK-Recs v0.8.1-fix2`.
6. Tap it and confirm the top section is `KMK-Recs v0.8.1-fix2`, followed by v0.8.1-fix1 and v0.8.0.
7. From Browse > For You, tap Loved Manga and confirm the screen opens without the `GetCrossSourceGroupPrimary` Injekt crash.
8. Open a grouped rated manga's Linked Versions screen and confirm it also opens without dependency-registration errors.

## Acceptance Criteria

- `KmkRecsReleaseNotes.VERSION_NAME` shows `KMK-Recs v0.8.1-fix2`.
- The manual About entry clearly exposes the current KMK-Recs version.
- The automatic KMK What's New dialog is not accidentally suppressed forever by the normal Komikku changelog path.
- Opening/dismissing KMK What's New marks only the KMK-Recs version as seen.
- Loved Manga opens without `No registered instance or factory for type class tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary`.
- The three group-primary interactors are registered in `KMKDomainModule`.
- Sync merge filters invalid primary rows consistently with restore validation:
  - blank `groupId`
  - `source == 0L`
  - blank `url`
- Tests pass.
- A private debug APK is built and copied to the private handoff folder.
- Implementation report and versioning docs record the private APK path.

