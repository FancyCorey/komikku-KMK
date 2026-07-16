# KMK-Recs v0.7 Final Public Release Readiness Plan

Date: 2026-07-12  
Target line: `KMK-Recs v0.7.x` finalization  
Recommended implementation version: `KMK-Recs v0.7.45` unless Claude finds that a larger split is required  
Status: planning only; no app code has been changed by this plan

## Purpose

This plan is the final v0.7 public-readiness pass for the Komikku KMK fork.

The goal is not just to make the APK build or work on one device. The goal is to bring the fork close to a professional public branch standard:

- understandable architecture;
- Komikku-aligned coding style and UI behavior;
- accurate public documentation;
- clear security and privacy boundaries;
- reliable install/update/package behavior;
- no misleading release notes or stale version claims;
- no hidden experimental behavior that surprises users;
- enough automated and manual verification that another developer can review, test, and build on this work.

Claude must treat this as a release-readiness hardening pass, not a feature-expansion pass. No new user-facing features should be added unless they directly fix a public-release blocker documented here.

## Current Verified State

The latest checked release-note state is:

- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
  - `VERSION_NAME = "KMK-Recs v0.7.44"`
  - `VERSION_CODE = 746`

The latest verified build command succeeded:

```powershell
$env:JAVA_HOME='C:\Users\USER\Downloads\Komikku\komikku-source\.tools\jdk17\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat spotlessCheck :app:testDebugUnitTest :app:assembleKmkPublicTest
```

Verified result:

- `spotlessCheck` passed.
- `:app:testDebugUnitTest` passed.
- `:app:assembleKmkPublicTest` passed.
- `app/build/outputs/apk/kmkPublicTest/output-metadata.json` reports `applicationId = "app.komikku.kmk"`.

Known APK files outside the build directory:

- Personal/private latest: `C:\Users\USER\Downloads\Komikku\private\Komikku-v1.13.6-kmk.7.44-debug.apk`
- Public test stale: `C:\Users\USER\Downloads\Komikku\public\Komikku-KMK-PublicTest-v1.13.6-kmk.7.34-debug.apk`

The public test artifact needs to be rebuilt/copied/renamed after this plan is complete.

## Public Readiness Verdict Before This Plan

Current state is suitable for a private beta and internal testing.

It is not yet suitable to present as a polished public branch or official-style public release because:

1. public-facing docs are stale or contradictory;
2. OCR inclusion does not match older OCR docs that say OCR is separate;
3. Source Recommendation Quality cleanup still has prompt-required edge cases that are weaker than normal Source Evaluation cleanup;
4. security/privacy docs contain contradictions;
5. public APK naming and artifact handoff are not current;
6. some remaining exception/error paths expose raw exception messages or bare source exceptions;
7. there has not been a final public release checklist pass against phone/tablet UI, backup/restore, installer modes, source evaluation, OCR, recommendation bundle import, and update behavior.

## Non-Goals

Do not add broad new recommendation algorithms in this pass.

Do not redesign the app.

Do not merge OCR deeper into recommendations.

Do not change official upstream Komikku behavior unless required to isolate KMK behavior safely.

Do not change the personal/update-style package identity unless the user explicitly requests it.

Do not build the final public APK until every phase in this plan is complete and verification passes.

## Required Reading Before Implementation

Claude must read these files before editing code:

- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`
- `docs/recommendations/DOCUMENTATION_RULES.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/KMK_RECS_V0_7_CLOSURE_AUDIT.md`
- `docs/recommendations/KMK_RECS_V0_7_44_SHARED_QUERY_POLICY_AND_GROUP_RECS_FIX_IMPLEMENTATION.md`
- `docs/community/KMK_PUBLIC_README_DRAFT.md`
- `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`
- `docs/security/KMK_SECURITY_AND_PRIVACY_REVIEW.md`
- `docs/database/KMK_DATABASE_BACKUP_SYNC_AUDIT.md`
- `docs/ocr/README.md`

Claude must also inspect the current code before changing behavior:

- `app/build.gradle.kts`
- `app/src/kmkPublicTest/res/values/strings.xml`
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationRunner.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityRunner.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt`
- `app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityJob.kt`
- `app/src/main/java/exh/recs/evaluation/SourceRecommendationQualityJobState.kt`
- `app/src/main/java/exh/recs/sources/RecommendationPagingSource.kt`
- OCR screens/repositories under `app/src/main/java/exh/ocr/` if present
- SQLDelight files under `data/src/main/sqldelight/tachiyomi/data/`

## Phase 1: Public Documentation Reconciliation

Make every public-facing document truthful for the current build line.

Claude must update:

- `docs/community/KMK_PUBLIC_README_DRAFT.md`
- `docs/community/KMK_PUBLIC_TEST_BUILD_LINE_IMPLEMENTATION.md`
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`

Required outcomes:

- public docs say the current KMK-Recs version is `KMK-Recs v0.7.45` after implementation;
- public docs stop referring to the current public build as `v0.7.34`;
- public docs distinguish personal `app.komikku`, debug `app.komikku.dev`, and public test `app.komikku.kmk`;
- public docs no longer claim the public test build collides with official Komikku;
- backup/restore expectations are clear;
- OCR inclusion or exclusion is explained after Phase 3.

## Phase 2: Source Evaluation And Recommendation-Quality Cleanup Safety

Normal Source Evaluation has cleanup status, prompt-required count, visible cleanup action, probe marker/startup recovery, and leftover extension warning.

Source Recommendation Quality / For You compatibility probing is newer. Code inspection showed:

- `SourceRecommendationQualityRunner.cleanupExtension(...)` calls `SourceEvaluationCleanupPolicy`;
- `RemovePrivateSilently` removes private-installed extensions;
- `PromptRequired` currently logs and skips system-installed cleanup;
- prompt-required leftovers are not surfaced with the same strength as normal Source Evaluation.

Claude must fix this before public release.

Preferred design:

- force non-installed recommendation-quality probes to use Private installer if available;
- if Private is unavailable, do not run non-installed temporary extension probes;
- show a clear user-facing message explaining that Private mode is required for this public-safe workflow;
- allow installed-source checks because they do not temporarily install anything.

Alternative design:

- if Claude determines Shizuku/Current must remain available, match normal Source Evaluation cleanup UX fully:
  - cleanup status per target;
  - prompt-required count;
  - visible cleanup action;
  - persisted leftover warning if process death leaves a package installed;
  - tests equivalent to Source Evaluation cleanup tests.

Do not leave "PromptRequired -> log only" as public behavior.

## Phase 3: OCR Build-Line Decision And Privacy Reconciliation

`docs/ocr/README.md` says OCR is intentionally separate from the main KMK-Recs APK line.

Current code inspection shows:

- `app/build.gradle.kts` includes `implementation(libs.mlkit.text.recognition)` in the main dependency graph;
- OCR tables and tests exist in the active app.

Claude must inspect and document:

- whether OCR UI is reachable in `kmkPublicTest`;
- whether ML Kit is included in `kmkPublicTest`;
- whether OCR migrations are included for all builds;
- whether OCR is intended to ship in the public build.

Then choose one:

Option A, OCR included:

- update OCR docs, public README, and security review;
- verify pre-indexing privacy warning mentions local plaintext recognized text storage;
- verify OCR text is not backed up, synced, bundle-exported, or diagnostics-exported;
- verify index deletion controls are discoverable enough.

Option B, OCR split:

- gate OCR dependency/UI out of `kmkPublicTest` unless explicitly building an OCR variant;
- keep migrations safe for existing installs;
- document the OCR-specific build command and APK naming separately.

Do not leave the current mixed state undocumented.

## Phase 4: Exception Handling And User-Facing Error Hygiene

Some KMK paths still assign raw `e.message` into UI state or throw bare `UnsupportedOperationException`.

Observed examples:

- `SourceRecommendationQualityRunner.kt`
- `SourceRecommendationQualityJob.kt`
- `SourceEvaluationJob.kt`
- `SourceEvaluationStartupRecovery.kt`
- `SourceEvaluationRunner.kt`
- `RecommendationPagingSource.kt`

Claude must classify public-facing errors into stable KMR/i18n strings:

- network unavailable;
- source unsupported;
- source returned no results;
- source metadata unavailable;
- extension install unavailable;
- extension cleanup required;
- internal error with diagnostics available.

Raw exception text can remain in logs/diagnostics only when it does not leak OCR text, credentials, personal backup content, or sensitive manga page text.

Audit KMK-owned public-facing paths in:

- `app/src/main/java/exh/recs/evaluation/`
- `app/src/main/java/exh/recs/sources/`
- `app/src/main/java/exh/recs/share/`
- `app/src/main/java/exh/recs/bestversion/`
- `app/src/main/java/exh/recs/loved/`
- OCR package if OCR ships.

## Phase 5: Komikku Style, Architecture, And Maintainability Pass

Claude must make KMK code read like a deliberate fork feature, not scattered AI-generated patchwork.

Standards:

- prefer existing Komikku/Mihon patterns: screen model, interactor, repository, SQLDelight query, KMR strings;
- keep KMK behavior isolated under `exh.recs` or explicitly marked touched integration points;
- avoid duplicate systems for the same concept;
- extract pure helpers where they reduce orchestration complexity and can be tested;
- keep coroutine dispatching explicit for network/source calls;
- respect cancellation;
- do not keep background work in screen scope if it is expected to continue off-screen;
- no hardcoded user-visible English strings in KMK UI;
- no obsolete `// KMK -->` comments with wrong version labels;
- no stale implementation plan claims contradicted by code.

Refactor only if low-risk. If risky, document as deferred to v0.8+.

## Phase 6: UI/UX Public Polish Pass

Review:

- For You tab;
- Recommendations settings;
- Source Evaluation;
- Rated Manga / Loved / Liked / Disliked views;
- group recommendations from rated manga;
- best version comparison;
- recommendation bundle import/export;
- OCR search/indexing if included;
- What's New / KMK release notes;
- public test build launcher label and app identity.

Requirements:

- no essential action should rely only on hidden long-press behavior;
- phone screens must not be clogged by oversized diagnostics;
- error details should be expandable/copyable, not permanently noisy;
- security-sensitive workflows must warn before action;
- destructive/bulk actions must confirm;
- Source Evaluation and recommendation-quality checks must explain temporary extension execution;
- public build must be clearly identifiable as not official upstream Komikku.

## Phase 7: Release Package And Update Behavior

For public sharing, use `kmkPublicTest`.

Claude must verify:

- `kmkPublicTest` application ID is `app.komikku.kmk`;
- launcher label is `Komikku KMK`;
- official Komikku is not overwritten;
- personal KMK build is not overwritten;
- update checker is not accidentally checking official upstream Komikku releases for the KMK public test build;
- if updater is disabled by default, public docs say so.

Recommended final public test APK name:

```text
Komikku-KMK-PublicTest-v1.13.6-kmk.7.45-debug.apk
```

If Claude increments beyond v0.7.45 due multiple required fixes, use the actual final KMK version.

## Phase 8: Verification Matrix

Claude must not build the final public APK until all relevant checks pass.

Required automated checks:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleKmkPublicTest
```

If time allows:

```powershell
.\gradlew.bat :app:lintKmkPublicTest
```

Required static checks:

```powershell
rg -n "v0\.7\.34|kmk\.7\.34|KMK-Recs v0\.7\.34" docs app
rg -n "app\.komikku\" -- the same as upstream|applicationId.*same as upstream" docs/community docs/recommendations docs/security
rg -n "Text\(\"|Text\(text = \"" app/src/main/java/exh/recs app/src/main/java/exh/ocr
rg -n "errorMessage = e\.message|UnsupportedOperationException|NetworkOnMainThreadException" app/src/main/java/exh/recs app/src/main/java/exh/ocr
rg -n "raw_text|normalized_text|ocr_indexed_page" app docs
rg -n "PromptRequired|cleanup required|leftover" app/src/main/java/exh/recs/evaluation docs
```

Historical references may remain in historical docs, but active public docs must not present stale facts as current.

Manual QA checklist:

- clean install public test build beside official/personal Komikku;
- backup restore into public test build;
- first launch shows appropriate KMK What's New;
- For You loads without crashing;
- Source Evaluation opens, starts, cancels, completes, and cleans up;
- recommendation-quality check starts, runs in background, cancels, completes, and handles cleanup safely;
- Shizuku/Current warning is clear if exposed;
- Rated Manga screens show Loved/Liked/Disliked with shared feature parity;
- grouped recommendations open and return results or honest no-result states;
- best version comparison cancel/fullscreen/migration paths still work;
- bundle import/export validates malformed, oversized, missing-source, and ambiguous-source cases;
- OCR indexing/search/delete flows if OCR is included;
- internet loss and recovery for extension/search workflows;
- app restart after evaluation/recommendation-quality job;
- crash diagnostics do not expose OCR text or credentials.

## Phase 9: Documentation After Implementation

Claude must write:

```text
docs/community/KMK_V0_7_FINAL_PUBLIC_RELEASE_READINESS_IMPLEMENTATION.md
```

The report must include:

- exact version chosen;
- files changed;
- whether OCR is included or split;
- source-evaluation/recommendation-quality cleanup decision;
- public package identity;
- public APK path/name;
- tests run and results;
- manual QA performed and results;
- remaining known limitations;
- what is deferred to v0.8+.

## Release Readiness Bar

This plan is complete only when the fork can honestly be described as:

- buildable from source;
- installable beside official Komikku for public testing;
- documented accurately;
- not hiding known privacy/security risks;
- not leaving temporary extensions installed silently;
- not claiming OCR is separate if it is included;
- not claiming a stale version in public docs;
- not showing raw implementation exceptions as normal UI;
- not requiring the maintainer to personally explain hidden behavior to every tester.

Claude may implement this plan in multiple phases/prompts if needed, but must not produce the final APK until all phases are complete.

