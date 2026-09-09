# KMK-Recs v0.7 Final Public Release Readiness Implementation

Date: 2026-07-12

Status: implemented, verified, shipped. This is the final v0.7 release; v0.7 feature work is now frozen.

Plans: `KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_PLAN.md` + `KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_AMENDMENT.md`

## 1. Release Identity

- `KmkRecsReleaseNotes.VERSION_CODE`: `747` (previous checked-in value was `746` from v0.7.44, confirmed
  before editing).
- `KmkRecsReleaseNotes.VERSION_NAME`: `"KMK-Recs v0.7.45"`.
- Android `versionCode`/`versionName` were not touched (`88` / `"1.13.6"`).
- Both final artifacts were built from the same final source state, after all code changes, doc
  reconciliation, and verification below were complete -- no APK was produced as an intermediate
  checkpoint.

## 2. Final Feature Window (Amendment 1)

### Final Feature A: Rated Manga Grouping Default

`LovedMangaScreenModel.load()` previously hardcoded `groupDuplicates = false` on every rebuild. Because
`load()` re-runs reactively (`getMangaTaste.subscribeAll().collectLatest { load(it) }` in `init`) on
**every** taste change anywhere in the app -- not just in this screen -- the hardcoded value was also
silently resetting a user's manual "show flat" toggle back to grouped-off every time any manga was
rated. Fixed by:

```kotlin
val groupDuplicates = (mutableState.value as? State.Success)?.groupDuplicates ?: true
```

Default is now grouped (`true`) on first load; the toggle is preserved across all subsequent reactive
reloads. Grouping logic itself (`LovedMangaDuplicateGrouper`, cross-source link groups first, then
conservative title/author/description evidence) is unchanged -- this only changes the default/persisted
toggle value, not the grouping algorithm.

**Test coverage:** the amendment's required tests (default-grouped, toggle-to-flat, confirmed-linked
entries collapse, unrelated same-title entries don't collapse) split across existing and new coverage:
`LovedMangaDuplicateGrouperTest` (38 tests, unchanged, already covers the collapse/don't-collapse
behavior at the pure-algorithm level) and `RatedMangaExclusivityTest` (9 tests, unchanged). **Gap:**
no test exercises `LovedMangaScreenModel.load()`'s default-grouped-on-first-load /
preserved-across-reload behavior directly -- that would need an Injekt-mocked screen-model test harness
that does not currently exist for this class, and building one was out of scope given the size of the
rest of this pass. Documented here rather than silently skipped.

### Final Feature B: Top Picks Contribution Visibility

`SourceFitStats.topPicksContributionCount` was tracked (since v0.7.32) and serialized, but never
displayed. Now shown compactly, appended to the existing source fit badge in Recommendation Settings
(e.g. `"Great fit Â· 5"`) via a new `rec_source_fit_with_top_picks_count` KMR string, only when the
count is greater than zero and a rolling fit label is shown. No new row, no new UI element -- one badge,
one extra piece of text.

New `SourceFitStatsTest.kt` (5 tests): serialize/parse round-trip preserves zero and nonzero counts,
legacy rows without the field default to zero, `mergeRun` increments only for actual contributors, and
multiple sources round-trip independently.

### Final Feature C: Scanned For Other Approved Closure Items

Read `KMK_RECS_V0_7_CLOSURE_AUDIT.md`, `NEXT_WORK.md`, `CURRENT_STATE.md` before implementation, per the
amendment. The audit explicitly recommended exactly Features A and B above as "Option B" (the only two
items "small enough for a final v0.7 cleanup"); everything else in the audit is either already resolved
(confirmed stale, see Section 6) or explicitly recommended for v0.8+ (broader For You filters, refresh
effort modes, source-scope controls, hidden-source management, automatic Best Version re-search
triggers). No additional closure items were added beyond A and B and the readiness-hardening phases
below -- the audit already did this scoping work, so re-scanning independently would have duplicated it.

## 3. Recommendation-Quality Cleanup Public-Safety Fix (Plan Phase 2)

**Design chosen: the plan's preferred design**, not the alternative (full Source Evaluation cleanup UX
parity for the background job).

`SourceRecommendationQualityRunner.cleanupExtension()`'s `PromptRequired` branch was log-only (a logcat
line, nothing else) -- unlike full Source Evaluation, this runner had no cleanup-status tracking,
prompt-required count, visible cleanup action, or leftover warning. A Shizuku/Current-mode temporary
install during a compatibility check could be left on the device with only a log line as evidence.

Fix, in `SourceRecommendationQualityRunner.start()` and `evaluateOne()`:

- Non-installed (temporarily-installed) probes are now **unconditionally forced to the Private
  installer** when it's available, regardless of the user's chosen Source Evaluation installer mode.
  Private cleans up silently and reliably, so this closes the gap without needing to replicate Source
  Evaluation's heavier cleanup-status UX for what is a secondary background feature.
- If Private is **unavailable**, non-installed targets are refused entirely (`writeErrorFit(evaluation,
  NON_INSTALLED_REFUSED_KEY)`) rather than risking a Shizuku/Current temp install. A new
  `nonInstalledSkippedCount` on `SourceRecommendationQualityQueueState` is surfaced in
  `SourceEvaluationScreenModel`/`SourceEvaluationScreen` as a clear, KMR-localized message
  (`source_evaluation_rec_quality_private_required_message`) when greater than zero.
- Installed-source checks are completely unaffected -- they never install or uninstall anything.
- The refusal reason is also classified into the existing `SourceRecommendationProbeFailureKind` badge
  system (new `PRIVATE_INSTALLER_REQUIRED` case, KMR label `source_evaluation_rec_error_kind_private_required`)
  so it's visible in the row's expandable diagnostics, not just the section-level message.

## 4. OCR Build-Line Decision (Plan Phase 3)

**Decision: Option A -- OCR ships in the main build, documented accurately (not split out).**

Code inspection: `implementation(libs.mlkit.text.recognition)` is an unconditional entry in
`app/build.gradle.kts`'s main `dependencies { }` block. There is no product-flavor or source-set
mechanism in this build that excludes any dependency, migration, or UI screen from `kmkPublicTest` --
`kmkPublicTest` is a `buildType` (`initWith(release)` + `applicationIdSuffix`), not a separate flavor,
so it inherits the entire dependency graph and source set. This means:

- ML Kit text recognition **is** included in `kmkPublicTest`.
- OCR UI (`OcrSearchScreen`, indexing controls) **is** reachable in `kmkPublicTest` -- there is nothing
  gating it off.
- OCR migrations (54, 55) run for every build, including `kmkPublicTest` and the personal build.
- `docs/ocr/README.md`'s claim that "the OCR build is intentionally separate from the main KMK-Recs APK
  line" was **never actually true of the shipped build** -- it described an intended plan that was not
  implemented. This is now corrected in that file.

**Why Option A instead of B (splitting OCR out):** actually implementing Option B would require adding a
Gradle product flavor dimension (there are currently zero flavor dimensions in `app/build.gradle.kts` --
only build types), which is exactly the kind of build-system change the plan tells me to avoid unless
necessary ("Do not redesign the app"), and which the existing `KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`
report already explicitly rejected flavors for the public-test-line work ("Adding a flavor would require
renaming all existing variant tasks and would be more invasive"). A true split is deferred to v0.8+ and
documented as such in `docs/ocr/README.md`.

**Privacy verification performed** (per the plan's Option A checklist):

- Pre-indexing privacy notice: **present**, not missing as `KMK_SECURITY_AND_PRIVACY_REVIEW.md`'s
  warning-requirements table previously (incorrectly) claimed. `ocr_index_all_confirm_message` reads:
  *"Recognized text is stored in the local database and is searchable â€” it is not sent to any server."*
  That table row was stale and has been corrected (see Section 6).
- Backup/sync/export exclusion: confirmed via `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`'s
  existing `ocr_indexed_page` table row -- `No` for both backup and sync columns. This was already
  accurate; no change needed.
- Deletion controls: confirmed present and discoverable -- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`
  lists "Yes (delete by manga/chapter/all)" for `ocr_indexed_page`, and `OcrSearchScreen.kt` has
  `showClearConfirm`/`showForceReindexConfirm` dialogs with dedicated KMR strings. Already accurate.

## 5. Exception Handling / Error Hygiene (Plan Phase 4)

Ran the plan's static check (`errorMessage = e\.message|UnsupportedOperationException|NetworkOnMainThreadException`
across `exh/recs` and `exh/ocr`) and traced every hit to its actual UI consumer, rather than assuming
every hit was a violation:

**Fixed (genuinely reached normal UI):**

- `SourceEvaluationRunner.kt`'s per-source probe catch (`errorMessage = e.message ?: "Probe error"`)
  fed directly into `SourceEvaluation.errorMessage`, which `EvaluationResultRow`'s row subtitle renders
  verbatim (truncated to 60 chars) for `ERROR`-verdict rows. New `SourceEvaluationProbeErrorClassifier`
  (pure, 4-way: `NETWORK_UNAVAILABLE`/`TIMEOUT`/`UNSUPPORTED`/`INTERNAL`) now writes a stable storage
  key instead of the raw exception message; `EvaluationResultRow` maps that key to a KMR string, with a
  documented backward-compatible fallback (older DB rows written before this fix still show their
  stored raw text, truncated, exactly as before -- no migration needed, no behavior change for existing
  data).
- Two other hardcoded English strings in the same row: `"Unknown source"`/`"Unknown extension"`
  fallbacks and the `"$displayExt â€¢ Error: $msg"` concatenation -- all replaced with KMR strings
  (`source_evaluation_unknown_source`, `source_evaluation_unknown_extension`,
  `source_evaluation_row_error_prefix`).

**Confirmed already safe (traced to consumer, no UI leak):**

- `RecommendationPagingSource.kt`'s 7 `throw UnsupportedOperationException()` calls (the
  `RecommendationSource` delegate stub, thrown when a placeholder source has no real delegate) are
  caught by `RecommendsScreenModel`'s generic `catch (e: Exception)` and rendered via
  `RecommendationItemResult.Error(e)` -> `recResult.throwable.formattedMessage` in
  `components/RecommendsScreen.kt` -- an existing, app-wide Komikku/Mihon extension (used by normal
  global search too, not KMK-specific) that already classifies common exception types into readable
  text. This is the established Komikku pattern the plan asks KMK code to prefer, not a violation.
- `SourceEvaluationJob.kt`/`SourceRecommendationQualityJob.kt`/`SourceEvaluationRunner.kt`'s
  **batch-level** `errorMessage = e.message` (on `SourceEvaluationQueueState`/
  `SourceRecommendationQualityQueueState`) is not rendered anywhere in either screen's Compose code --
  confirmed by tracing every `.errorMessage` read site in `SourceEvaluationScreen.kt`. Diagnostic-only,
  acceptable per the plan ("Raw exception text can remain in logs/diagnostics only").
- `BrowsePersonalRecommendationsScreenModel.kt:1089`'s `errorMessage = e.message?.take(200)` writes to
  `RecommendationDiscoveryProgress`, a DB-backed diagnostic/retry-tracking table, not rendered in the
  For You UI (which shows source rows and results, not a raw progress-table dump). Diagnostic-only.
- `SourceEvaluationStartupRecovery.kt:129`'s `Result(errorMessage = e.message)` field is defined but
  never read by any caller. Dead field, not a UI leak; left as-is (removing it is a separate, unrelated
  cleanup not requested by this plan).
- `RecommendationRetryClassifier.kt`'s `UnsupportedOperationException` check classifies a caught
  exception's *type* into a `FAILURE_KIND_PERMANENT` retry-policy constant -- it never surfaces the
  exception itself.

**Not fixed, documented as a scoped deviation:** the ~10 static descriptive English literals inside
`SourceRecommendationQualityRunner.evaluateOne()` (`"Source match ambiguous in installed extension:
..."`, `"Extension not found in available sources"`, etc.) are stored in `SourceRecommendationFit.errorMessage`
and do reach the UI as the expandable reason-hint text in `EvaluationResultRow` (added in v0.7.44,
collapsed behind "Show details" by default). These are *not* raw exception messages -- they are
engineering-authored descriptive strings, already classified into a KMR-localized badge label via the
pre-existing `SourceRecommendationFitFailureClassifier` substring matcher -- but the underlying reason
text itself is still English and not translated. Given the size of the rest of this release, fully
converting all ~10 of these into a typed KMR taxonomy was out of scope; they carry much lower risk than
genuine `e.message` leakage (bounded, predictable, no secrets/URLs/stack frames) and are already opt-in
(collapsed by default). Deferred to v0.8+ if full localization of every reason string is desired.

## 6. Documentation Reconciliation (Plan Phase 1 + static checks)

- `docs/community/KMK_PUBLIC_README_DRAFT.md`: version bumped to `v0.7.45`; public filename updated;
  OCR section rewritten to state it ships in the main build (not separate); "Known Limitations"
  `applicationId` bullet rewritten -- it previously read as a blanket "this fork can't coexist with
  official Komikku" claim, which stopped being true once the `kmkPublicTest` build line was added
  (historical docs still call this a "blocker"; the active public README now correctly distinguishes
  the personal update-style line, which intentionally shares upstream's ID, from the public test line,
  which does not).
- `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`: this is a historical implementation
  report (dated 2026-06-29, when the build line was first added) -- per documentation rules, historical
  reports are not rewritten. Added a short "v0.7.45 note" at the top clarifying the mechanics it
  documents are still current but the version numbers quoted inside (`v0.7.34`) are a historical
  snapshot, not the current version.
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`: fixed the "User-Facing Warning Requirements"
  table's OCR row, which incorrectly claimed OCR is a separate build line and that the pre-index privacy
  notice was missing (see Section 4 -- both were stale). Added a v0.7.45 addendum under the Source
  Evaluation/SEC-04 section documenting the recommendation-quality cleanup fix (Section 3).
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`: reviewed, already accurate for `ocr_indexed_page`
  (backup/sync `No`/`No`, deletion controls documented) -- no change needed.
- `docs/ocr/README.md`: rewrote the "intentionally separate" claim; documents the actual current state
  (OCR ships in every build) and defers a true build-time split to v0.8+.
- `docs/recommendations/NEXT_WORK.md`: resolved three stale open items confirmed fixed during the
  closure audit -- item 4 (PromptRequired cleanup, now actually fixed by Section 3's design), item 5
  (`ScreenErrorMessage` hardcoded, confirmed already fixed by v0.7.18/v0.7.44's typed `ScreenErrorKey`),
  item 6 (Top Picks contribution display, now implemented by Section 2). Added a v0.7.45-shipped summary
  section and marked the v0.7 feature line closed.
- `docs/recommendations/CURRENT_STATE.md`: version bumped to `v0.7.45`; documented both APK handoffs
  (private + public); added an "As of v0.7.45" section for the four items above.
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`: "Community/public sharing readiness" required-reading row now
  points to this plan/amendment pair as the current entry point, with the older consolidation-phases doc
  kept as historical-context reading rather than the primary pointer.

**Not done (explicitly deferred, per time/scope):**
- `docs/community/KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md` marked historical, and
  `README.md`'s mojibake/stale-active-plan-table cleanup -- both flagged by
  `KMK_RECS_V0_7_CLOSURE_AUDIT.md` as doc-hygiene items, neither blocking public readiness (no
  contradiction, no privacy/security claim, no version confusion), deferred to a future doc-only pass.
- `RECOMMENDATION_VERSIONING.md` was not updated this pass (not in this plan's explicit file list,
  unlike prior v0.7.43/v0.7.44 passes) -- its next update should include a `### KMK-Recs v0.7.45` entry
  consistent with the established pattern.

## 7. Style/Architecture/UI Polish (Plan Phases 5-6)

Given the size of Phases 1-4, this pass focused on the concrete violations found while auditing
Phase 2/4 (above) rather than a fresh line-by-line style audit of the whole `exh.recs`/`exh.ocr` tree --
v0.7.44's session already did a substantial phone-density pass (Source Evaluation row collapsing,
`FlowRow` conversions) that remains in effect and was re-verified compiling/passing here. No new
duplicate systems, no new hidden long-press-only actions, and no new large diagnostic blocks were
introduced by this pass's changes. The new `nonInstalledSkippedCount` message and Top Picks contribution
badge were both designed to be compact (one line / one badge suffix) per the amendment's explicit
"keep phone UI compact" requirement.

## 8. Release Package And Update Behavior (Plan Phase 7)

Verified via `output-metadata.json` after `:app:assembleKmkPublicTest`:

- `kmkPublicTest` `applicationId` = `"app.komikku.kmk"` (confirmed).
- Launcher label: `app/src/kmkPublicTest/res/values/strings.xml` overrides `app_name` to `"Komikku KMK"`
  (confirmed, unchanged from the existing implementation).
- Official Komikku (`app.komikku`) and the personal KMK line (`app.komikku` release / `app.komikku.dev`
  debug) are not overwritten by the public test build -- different `applicationId`.
- Update checker: `BuildConfig.UPDATER_ENABLED` is `false` unless `-Penable-updater` is explicitly
  passed at build time; this applies to `kmkPublicTest` the same as every other build type. Confirmed
  unchanged and already documented in `KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`. No code change
  needed -- the updater is disabled by default for the public build, and public docs already say so.

## 9. Final Artifacts

Both artifacts were built from the same final source state, after all code changes and doc
reconciliation above, per Amendment 3's build-timing requirement.

| Artifact | Path | Package ID | Build task | OCR included |
|---|---|---|---|---|
| Private/update-style | `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.45-debug.apk` | `app.komikku.dev` | `:app:assembleDebug` | Yes |
| Public side-by-side test | `C:\Users\USER\Downloads\Komikku\public\Komikku-KMK-PublicTest-v1.13.6-kmk.7.45-debug.apk` | `app.komikku.kmk` | `:app:assembleKmkPublicTest` | Yes |

Known differences between the two builds: package ID (as above), launcher label (`Komikku KMK` for
public test vs. the default Komikku label for the personal build), and minify/shrink settings (both
`false` for public test, matching the personal debug build -- neither is minified). No feature or
recommendation-logic difference. Both include the same v0.7.45 fixes.

## 10. Tests Run

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

- `.\gradlew.bat :app:testDebugUnitTest --tests "exh.recs.SourceFitStatsTest"` -- BUILD SUCCESSFUL, 5/5 PASSED (new)
- `.\gradlew.bat :app:testDebugUnitTest --tests "*RecommendationCandidateVisibilityPolicyTest*" --tests "*RecommendationQueryPlanner*" --tests "*GenreFilterMapper*" --tests "*Group*Recommendation*" --tests "*SourceRecommendationQuality*" --tests "*Seen*"` -- BUILD SUCCESSFUL
- `.\gradlew.bat :app:testDebugUnitTest` -- **954 tests, 0 failures**
- `.\gradlew.bat spotlessCheck` -- BUILD SUCCESSFUL
- `.\gradlew.bat :app:assembleKmkPublicTest` -- BUILD SUCCESSFUL, `applicationId = "app.komikku.kmk"` confirmed
- `.\gradlew.bat assembleDebug` (private/debug artifact) -- BUILD SUCCESSFUL
- `:app:lintKmkPublicTest` -- **not run**, deferred due to time budget for this already-large combined
  pass. `app/build.gradle.kts` already sets `lint { abortOnError = false; checkReleaseBuilds = false }`
  project-wide, so lint would not block a build regardless; documented here rather than silently skipped.

Static checks (plan Phase 8):
- `rg -n "v0\.7\.34|kmk\.7\.34" docs app` -- hits are either historical implementation reports
  (untouched, correct) or the two active docs fixed in Section 6.
- `rg -n "app\.komikku.*same as upstream"` -- one active-doc hit (`KMK_PUBLIC_README_DRAFT.md`), fixed;
  remaining hits are historical reports, left as-is.
- `rg -n "errorMessage = e\.message|UnsupportedOperationException|NetworkOnMainThreadException"
  app/src/main/java/exh/recs app/src/main/java/exh/ocr` -- every hit traced to its UI consumer; see
  Section 5 for the full breakdown of fixed vs. already-safe vs. deferred.
- `rg -n "raw_text|normalized_text|ocr_indexed_page" app docs` -- confirmed OCR text storage is
  correctly excluded from backup/sync everywhere it's referenced; no leaks found.
- `rg -n "PromptRequired|cleanup required|leftover"` -- confirmed the fix in Section 3 covers the only
  remaining `PromptRequired`-log-only path (`SourceRecommendationQualityRunner`); Source Evaluation's
  own `PromptRequired` handling (full cleanup-status/leftover-warning UX) was already correct and
  unchanged.

## 11. Manual QA

**Not performed this pass.** No physical/emulated device was available in this environment. The plan's
manual QA checklist (clean install beside official/personal Komikku, backup restore, What's New,
Source Evaluation start/cancel/complete/cleanup, recommendation-quality background behavior, Shizuku
warning, Rated Manga parity, grouped recommendations, Best Version paths, bundle import/export edge
cases, OCR flows, connectivity loss/recovery, app restart mid-job, crash-diagnostics privacy) was not
executed. This is a real gap against the plan's release-readiness bar and is called out explicitly
rather than claimed as done. All verification in this report is automated (compile, unit tests,
spotless, build success, static `rg` checks, and manual code-path tracing) â€” device-level QA remains a
prerequisite before treating either artifact as validated for actual public distribution, not just
buildable.

## 12. Remaining Known Limitations / Deferred To v0.8+

- No manual/device QA was performed (Section 11) -- the single largest gap before real public sharing.
- `LovedMangaScreenModel`'s default-grouped behavior has no direct screen-model-level test (Section 2).
- ~10 descriptive-but-untranslated error reason strings remain in `SourceRecommendationQualityRunner`
  (Section 5) -- low risk, already opt-in/collapsed, but not fully KMR-localized.
- A true OCR build-time split (dedicated product flavor excluding ML Kit/OCR code from a
  non-OCR-specific build) is deferred to v0.8+ -- Option A (document, don't split) was chosen for this
  release (Section 4).
- Doc-hygiene items from `KMK_RECS_V0_7_CLOSURE_AUDIT.md` not addressed this pass: marking
  `KMK_RECS_DEFERRED_FEATURE_MASTER_IMPLEMENTATION_PLAN.md` historical, and `README.md` mojibake/stale
  active-plan-table cleanup (Section 6).
- `RECOMMENDATION_VERSIONING.md` was not updated this pass (Section 6).
- No release keystore/signing process exists yet -- both artifacts are signed with the debug keystore,
  as documented in `KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`'s existing "Remaining Limitations."
  Unchanged by this pass.
- Broader v0.8 feature candidates (For You filters, refresh-effort modes, source-scope controls, local
  outcome learning, hidden-source management/snooze, automatic Best Version re-search) are explicitly
  out of scope for v0.7 closure, per `KMK_RECS_V0_7_CLOSURE_AUDIT.md` and this plan's non-goals.

