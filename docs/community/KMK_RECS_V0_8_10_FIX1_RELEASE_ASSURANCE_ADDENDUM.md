# KMK-Recs v0.8.10-fix1 - Release Assurance Addendum

**Status:** Planning only. This addendum is part of
`KMK_RECS_V0_8_10_FIX1_FULL_KOMIKKU_CONFORMANCE_AND_STABILITY_IMPLEMENTATION_PLAN.md`.

Claude must read and apply this addendum together with the master plan. It adds release-assurance
requirements that were not sufficiently explicit in the original plan.

## 1. Official Upstream Divergence Guard

Before changing UI or architecture, compare every KMK-modified file against the official Komikku
1.14.0 baseline and classify the difference as:

- required upstream behavior;
- intentional KMK feature behavior;
- mechanical compatibility adaptation;
- stale or accidental divergence;
- unknown and requiring review.

Pay particular attention to:

- `app/src/main/java/eu/kanade/presentation/**`;
- `app/src/main/java/eu/kanade/tachiyomi/ui/**`;
- `domain/src/main/java/**`;
- `app/src/main/java/eu/kanade/tachiyomi/data/**`;
- `app/src/main/java/exh/recs/**`;
- `app/src/commonMain/moko-resources/**` and KMK resource ownership;
- database migrations and backup/proto models.

Do not overwrite KMK behavior with upstream code blindly. For every retained divergence, record the
file, symbol, reason, user-visible effect, and regression test. For every accidental divergence,
restore the official pattern and reapply only the necessary KMK hook using the repository's marker
convention.

Add a repeatable diff/inventory command or documented procedure to the implementation report so a
future Komikku upgrade can detect newly divergent files before implementation begins.

## 2. Startup And Navigation Crash Diagnostics

The reported crash outside Settings must be diagnosable even if it cannot initially be reproduced.
Inspect the first process and navigation boundary and ensure the application records a bounded,
privacy-safe diagnostic state containing:

- route/screen identifier;
- app and KMK feature versions;
- migration/database initialization state;
- whether the failure occurred during Injekt registration, screen-model creation, composition, or
  network/database loading;
- exception type and sanitized message;
- whether the failure occurred after process recreation or restored navigation state.

Do not log credentials, OCR text, full manga descriptions, tokens, or unnecessary URLs. Do not leave
diagnostics permanently enabled at verbose level. The UI must show a recoverable error state when the
existing Komikku architecture supports it, and must offer retry/restart behavior without swallowing
the original cause.

Add a regression test for the discovered failure boundary. If the crash remains unreproducible,
document the exact routes tested, the diagnostic coverage added, and the remaining device-only gate.

## 3. Performance Baseline And Regression Checks

Measure before and after the fix on representative data. Cover:

- For You initial render and refresh;
- one group recommendation with many linked versions;
- Source Evaluation screen opening and result-list rendering;
- one 100-source evaluation batch;
- Rated/Loved/Liked/Disliked collection opening, search, grouping, and sorting;
- Recommendation Settings search;
- What's New rendering with all historical entries.

Inspect for:

- database queries repeated on every recomposition;
- source-name or metadata lookups performed inside item composition without memoization;
- network calls on the main dispatcher;
- unbounded `launch`, `async`, `flatMapMerge`, or nested enrichment requests;
- unstable lazy-list keys;
- `LaunchedEffect` keys that restart work unnecessarily;
- large Markdown or JSON parsing on the main thread;
- cache keys that omit relevant preference/state inputs;
- screen-model jobs that survive after their screen should be gone.

Use existing bounded concurrency, cache, dispatcher, and cancellation helpers. Add a pure policy or
focused test for every performance fix. Do not optimize by reducing results, removing features, or
silently skipping sources unless the existing preference/policy explicitly permits it.

## 4. Upgrade And Data-Preservation Matrix

Verify all of the following with a database containing representative real-shaped data:

1. Fresh install of the v0.8.10-fix1 APK.
2. Upgrade from the current v0.8.10 APK.
3. App restart after upgrade.
4. Process death and restored navigation after upgrade.
5. Reinstall/update path with existing app data preserved.
6. Backup creation before upgrade and restore after upgrade.

Confirm preservation of:

- library manga, chapters, read state, and history;
- Love/Like/Dislike/Not Interested ratings;
- confirmed cross-source links and group primary versions;
- source priority, source language, source quality, blocked/disliked sources, and evaluation cursors;
- For You discovery memory, cache policy, and taste/tag preferences;
- OCR index/exclusion state;
- reader timer and reading schedule state;
- extension repositories and source preferences;
- sync credentials and sync preference keys.

Any intentionally non-migrated item must be documented with a user-facing consequence and a test.
No migration may silently delete or reset data. Add a combined upgrade test if the existing test
infrastructure permits it; otherwise document exactly why a real-device check is required.

## 5. Visual Regression Evidence

For each major KMK surface, capture or manually verify a visual checklist on phone and tablet:

- Recommendation Settings index and every destination;
- Source Evaluation options, progress, rows, errors, quarantine/blocked controls, and history;
- For You rows and loading/empty/error states;
- Sources To Try;
- Loved/Liked/Disliked collections and selection mode;
- group recommendations;
- Best Version preview and migration dialog;
- reader rating prompt, timer, and schedule dialogs;
- KMK What's New with historical entries.

Repeat in light theme, dark theme, portrait, landscape, and large font scale. Verify no clipping,
overlap, horizontal overflow, unreadable contrast, hidden primary action, or controls below the
system gesture/navigation area. Verify minimum touch targets and content descriptions with TalkBack
or the available accessibility inspection tool.

Use existing Komikku typography, spacing, color, icon, dialog, top-bar, and list components. Record
screens that cannot be tested in the implementation report rather than claiming visual conformance.

## 6. Documentation Index Completion

Before the final handoff, update and verify:

- `docs/recommendations/README.md` with links to the master plan, this addendum, and the final report;
- `docs/recommendations/NEXT_WORK.md` with the approved v0.8.10-fix1 status and only genuinely open
  work;
- `docs/recommendations/CURRENT_STATE.md` with verified behavior, test totals, device limitations,
  and final version identity;
- `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md` with this plan/addendum and the exact source-map procedure;
- `RECOMMENDATION_VERSIONING.md` with the final local feature label, monotonic release-note code,
  upstream app version, and APK filename.

Check all links, headings, status labels, version numbers, and APK paths. No document may say a phase
is complete if it was only planned, and no completed phase may remain described as planning-only.

## 7. Addendum Completion Gate

Claude may report v0.8.10-fix1 complete only when:

- upstream divergence has been classified;
- the crash has a root-cause fix or a documented, instrumented device-only blocker;
- performance checks show no new unbounded work;
- upgrade/data preservation checks pass;
- visual/accessibility checks are recorded;
- documentation indexes and version metadata agree;
- the final APK is produced only after the master plan and this addendum both pass.
