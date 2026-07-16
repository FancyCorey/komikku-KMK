# KMK-Recs v0.8.1-fix2 Version Visibility And Sync Validation â€” Implementation Report

Date: 2026-07-12

Version/build label: `KMK-Recs v0.8.1-fix2`, `KmkRecsReleaseNotes.VERSION_CODE = 752` (bumped from
751).

**This is a PRIVATE build.** No public release was prepared or requested. The public-test
`applicationId` (`app.komikku.kmk`) was not built or touched â€” only the private/personal line
(`app.komikku` / `app.komikku.dev`, `:app:assembleDebug`) was verified and handed off.

Status: implemented and verified (automated: compile, tests, spotless, `assembleDebug`).

Plan implemented: `docs/recommendations/KMK_RECS_V0_8_1_FIX2_VERSION_VISIBILITY_AND_SYNC_VALIDATION_PLAN.md`

## User-Approved Scope

Small private corrective follow-up to v0.8.1-fix1: fix a crash reported via a real crash log
(opening Loved Manga threw `InjektionException` for `GetCrossSourceGroupPrimary`), verify/repair
KMK-Recs What's New version visibility, harden group-primary sync validation to match restore, and
produce a named private APK handoff. Explicitly not a new feature pass; no recommendation scoring,
Source Evaluation scoring, For You ranking, group recommendation, OCR, or Rated Manga bulk-action
changes.

## Files Changed

**New files:**

- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewPolicy.kt` â€”
  pure policy (`hasUnseenChangelog`, `shouldShowKmkDialog`, `seenVersionCodeOnAcknowledge`).
- `app/src/test/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewPolicyTest.kt`
  â€” 8 tests.
- `app/src/test/java/eu/kanade/domain/CrossSourceGroupPrimaryDomainModuleRegistrationTest.kt` â€” 1
  test, regression-guards the specific "forgot to register" class of bug for the three group-primary
  interactors.

**Modified files:**

- `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` â€” **the crash fix.** Added imports for
  `GetCrossSourceGroupPrimary`, `SetCrossSourceGroupPrimary`, `ClearCrossSourceGroupPrimary`, and
  three `addFactory { ... }` registrations placed immediately after the existing cross-source-link
  registrations (`GetCrossSourceMangaLinks`/`UpsertCrossSourceMangaLinks`/`DeleteCrossSourceMangaLink`).
- `app/src/main/java/eu/kanade/tachiyomi/data/sync/service/SyncService.kt` â€” imported
  `CrossSourceGroupPrimaryRestorePolicy` from the restore-restorers package;
  `mergeCrossSourceGroupPrimariesPure()`'s local/remote filters changed from `it.groupId.isNotBlank()`
  to `CrossSourceGroupPrimaryRestorePolicy.isValid(it)`.
- `app/src/test/java/eu/kanade/tachiyomi/data/sync/service/SyncServiceCrossSourceGroupPrimaryMergeTest.kt`
  â€” 3 new tests (`source == 0L` filtered, blank `url` filtered, valid rows still merge after
  invalid rows are filtered out of a mixed batch).
- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` â€” `showKmkChangelog`'s initial
  value now computed via `KmkRecsWhatsNewPolicy.hasUnseenChangelog(...)`; the `else if
  (showKmkChangelog)` branch condition changed to `else if
  (KmkRecsWhatsNewPolicy.shouldShowKmkDialog(showChangelog, showKmkChangelog))`; both mark-seen call
  sites (dismiss, open) now call `KmkRecsWhatsNewPolicy.seenVersionCodeOnAcknowledge(...)`. Added a
  comment explaining why the Komikku/KMK dialogs are sequenced via plain recomposition rather than
  an explicit state machine.
- `app/src/main/java/eu/kanade/tachiyomi/ui/more/KmkRecsWhatsNewScreen.kt` â€” the mark-seen
  preference write moved from directly in the composable body into a `LaunchedEffect(Unit)` block,
  and now calls `KmkRecsWhatsNewPolicy.seenVersionCodeOnAcknowledge(...)` for consistency with
  `MainActivity`'s call sites.
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/KmkRecsWhatsNewDialog.kt` â€”
  added a `text = { ... }` body using the new `kmk_recs_updated_body` KMR string.
- `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` â€” 1 new KMR string:
  `kmk_recs_updated_body`. No hardcoded user-facing text was added.
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` â€” version bump + changelog entry.
- Documentation: `RECOMMENDATION_VERSIONING.md`, `docs/recommendations/CURRENT_STATE.md`,
  `docs/recommendations/NEXT_WORK.md`, `docs/recommendations/README.md`,
  `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`, and this report. `CURRENT_STATE.md` also had a
  pre-existing duplicated heading ("Rated Manga Bulk Selection And Group Actions (v0.8.0)" appeared
  twice consecutively, an artifact from a prior edit) cleaned up while touching that section.

## Behavior Changed

1. **Loved Manga no longer crashes.** Opening Browse > For You > Loved Manga, or a grouped rated
   manga's Linked Versions screen, no longer throws `InjektionException: No registered instance or
   factory for type class tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary`. This also
   transitively fixes any backup/restore path that touches group primaries
   (`TasteBackupCreator.backupCrossSourceGroupPrimaries()`,
   `TasteRestorer.restoreCrossSourceGroupPrimaries()`), which were equally broken by the same
   missing registration.
2. **KMK-Recs What's New visibility is unchanged in outward behavior, but its logic is now named
   and tested.** No functional sequencing bug was found (see Known Limitations/root-cause notes
   below) â€” the dialog show/pending/mark-seen decisions were already correct; they are now backed
   by `KmkRecsWhatsNewPolicy` instead of inline booleans, and one Compose anti-pattern (mark-seen
   writing directly in a composable body) was fixed. The compact dialog now has a one-line body.
3. **Group-primary sync validation matches restore exactly.** A malformed row (e.g. `source == 0L`
   from a corrupted or hand-edited backup) that restore would have silently skipped is now also
   filtered out during sync merge, instead of potentially surviving into the merged sync payload.

## Root-Cause Notes: Why v0.8.1-fix1 "Wasn't Clearly Seen"

Per the plan's own framing, this required verifying the trigger/visibility path rather than
assuming a bug and rebuilding it. Investigation found:

- The private/personal build line is `BUILD_TYPE == "debug"` (`app.komikku.dev`). `MainActivity`'s
  `showChangelog` (the **normal Komikku** changelog) is only ever `true` when
  `(isReleaseBuildType && didMigration) || (isPreviewBuildType && ...)` â€” both conditions require a
  `release` or `preview` build type. For a `debug` build, `showChangelog` is always `false`, so the
  `else if (showKmkChangelog)` branch is reached immediately on first composition. **The
  Komikku-changelog-suppresses-KMK-changelog scenario the plan asked to investigate does not
  actually occur for this private debug build line** â€” it would only be a real risk on a
  release/preview build type, which this project does not currently produce.
- The far more likely explanation, given the crash log provided, is that **the user's session
  crashed** (the `GetCrossSourceGroupPrimary` `InjektionException`) shortly after launch when they
  navigated to Loved Manga â€” an app crash does not un-mark or corrupt the `kmk_recs_last_seen_version_code`
  preference, but if the KMK dialog had already been shown-and-dismissed (or never appeared because
  it was already marked seen from a prior interaction with `KmkRecsWhatsNewScreen` or the About
  entry), a subsequent crash would understandably read as "the update notes weren't clearly seen" â€”
  the crash, not the dialog logic, was the dominant negative experience of that session.
- This report does not claim certainty about the exact user sequence of events (no session replay
  is available), but the code-level investigation is conclusive: no dialog-suppression bug exists
  in this build line today. The policy extraction, `LaunchedEffect` fix, and dialog body addition
  are still valuable correctness/clarity improvements made in this pass regardless.

## Tests Run

All run with repo-local JDK 17 (`.tools/jdk17/jdk-17.0.19+10`).

| Command | Result |
|---|---|
| `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `./gradlew :app:compileDebugUnitTestKotlin` | BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest --tests "*SyncServiceCrossSourceGroupPrimaryMergeTest" --tests "*CrossSourceGroupPrimaryRestorePolicyTest" --tests "*TasteBackupRoundTripTest" --tests "*KmkRecsWhatsNewPolicyTest" --tests "*CrossSourceGroupPrimaryDomainModuleRegistrationTest"` | **BUILD SUCCESSFUL** â€” all targeted tests passed |
| `./gradlew :app:testDebugUnitTest` (full suite) | **BUILD SUCCESSFUL â€” 1058 tests, 0 failures, 0 errors** (up from 1046 pre-existing) |
| `./gradlew spotlessApply` | BUILD SUCCESSFUL (auto-reformatted) |
| `./gradlew spotlessCheck` | BUILD SUCCESSFUL |
| `./gradlew :app:testDebugUnitTest` (post-spotless re-run) | BUILD SUCCESSFUL â€” 1058/1058 |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** (private line only) |

New/updated test counts:

- `CrossSourceGroupPrimaryDomainModuleRegistrationTest` â€” 1 test: all three group-primary
  interactors resolve via `Injekt.get()` once registered the same way `KMKDomainModule` does.
- `KmkRecsWhatsNewPolicyTest` â€” 8 tests: `hasUnseenChangelog` (greater/equal/lower/first-launch-zero),
  `shouldShowKmkDialog` (pending while Komikku dialog showing; shows once it's not; never shows with
  no unseen content regardless of Komikku dialog state), `seenVersionCodeOnAcknowledge`.
- `SyncServiceCrossSourceGroupPrimaryMergeTest` â€” 3 new tests: `source == 0L` filtered from both
  sides; blank `url` filtered from both sides; valid rows still merge normally after invalid rows
  are filtered out of a mixed local+remote batch.

### Existing Regression Tests Re-Run (via the full suite above)

- `CrossSourceGroupPrimaryRestorePolicyTest`, `TasteBackupRoundTripTest` â€” all still passing,
  confirming the shared `isValid()` policy behaves identically for restore and sync.
- Full `:app:testDebugUnitTest` â€” run in full (1058/1058), not skipped.

## APK Output

- **Build output path**: `app/build/outputs/apk/debug/app-universal-debug.apk` (from
  `:app:assembleDebug`, applicationId `app.komikku.dev`).
- **Private handoff path**: `private/Komikku-v1.13.6-kmk.8.1-fix2-debug.apk` â€” copied from the
  build output using the established private naming scheme
  (`Komikku-v1.13.6-kmk.<feature-version>-debug.apk`).

No public-test (`app.komikku.kmk`) build was produced, per the explicit private-build-only
instruction for this pass.

## Known Limitations

- **No manual/real-device verification was performed by the assistant.** All verification here is
  automated (compile, unit tests, spotless, `assembleDebug`). The user's own manual QA checklist
  (install over previous build, confirm Loved Manga/Linked Versions open without crashing, confirm
  About/What's New show v0.8.1-fix2) still needs to be run on the actual tablet that produced the
  original crash log.
- **The exact prior-session sequence that led to "v0.8.1-fix1 wasn't clearly seen" could not be
  definitively reconstructed** â€” see "Root-Cause Notes" above. The code path was verified correct
  for the debug build line; the crash is treated as the most likely explanation, but this is an
  inference, not a confirmed replay.
- **`CrossSourceGroupPrimaryDomainModuleRegistrationTest` does not invoke the real
  `KMKDomainModule.registerInjectables()`.** That module also registers interactors/repositories
  requiring a real Android `DatabaseHandler` and other framework dependencies unavailable in a pure
  JVM unit test, making a full module-registration test impractical. The test instead mirrors the
  exact registration pattern for the three affected types against a minimal fake `TasteRepository`
  â€” this guards against exactly the class of regression that caused this crash (a forgotten
  `addFactory` line) but would not catch every possible `KMKDomainModule` misconfiguration.

## Deviations From The Approved Plan

None in substance.

1. **The plan's "if the normal Komikku dialog can mask the KMK dialog... make the behavior
   deterministic" instruction did not require a code restructure**, because investigation found no
   suppression bug exists for this build line (see Root-Cause Notes). Per the plan's own escape
   hatch ("this pass must verify and repair the trigger/visibility path, not invent a separate
   release-note system"), the fix taken was to extract and test the existing logic
   (`KmkRecsWhatsNewPolicy`) rather than restructure behavior that was already correct.
2. **`CrossSourceGroupPrimaryRestorePolicy.isValid()` was imported directly into `SyncService.kt`**
   rather than extracted to a new neutral shared policy â€” the plan offered both options ("Prefer
   reusing... or extract a neutral shared policy if that is cleaner"). Direct reuse was chosen
   because both files already live under `eu.kanade.tachiyomi.data` in the same Gradle module
   (`internal` visibility in Kotlin is module-scoped, not package-scoped, so this compiles cleanly),
   and `SyncService.kt` already imports several `eu.kanade.tachiyomi.data.backup.models.*` types
   from the sibling backup package â€” importing one more type from `data.backup.restore.restorers`
   is consistent with that existing pattern, not architecturally awkward.

No source-specific hacks were added. No recommendation scoring, Source Evaluation scoring, For You
ranking, group recommendation scoring, OCR, or Rated Manga bulk-action behavior was touched. No
public release was made or requested; the public-test `applicationId` was never built or
referenced.

