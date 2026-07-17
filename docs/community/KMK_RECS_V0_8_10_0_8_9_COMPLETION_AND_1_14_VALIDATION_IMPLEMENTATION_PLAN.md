# KMK-Recs v0.8.10 — v0.8.9 Completion And Komikku 1.14 Validation Plan

**Status:** Planning only; no implementation approved or performed by this document.

**Target:** Complete the approved but incomplete v0.8.9 requirements, fix confirmed defects found after the Komikku 1.14.0 reconciliation, validate the upstream bridge, and produce one final development handoff build.

**Upstream base:** Official Komikku 1.14.0.

**KMK feature line:** `KMK-Recs v0.8.10` corrective/completion release. The upstream app version and KMK feature version remain separate. The app must not display development-channel wording such as private, public, internal, or test build.

**Final-build rule:** Claude may implement and verify the phases incrementally, but must not create or hand off the final APK until every required phase, test, documentation update, and release gate in this plan is complete. Intermediate debug builds may be used only for phase validation and must not be presented as the final handoff.

## 1. Purpose And Scope

This plan is based on the live code audit, not only on historical plans. The current tree contains a substantial Komikku 1.14 reconciliation and the v0.8.9 What's New/settings-search implementation, but the combined v0.8.9 request is incomplete and several release-verification gaps remain.

The work must:

- preserve the upstream Komikku 1.14 behavior already reconciled;
- preserve the existing recommendation, rating, grouping, source-evaluation, OCR, reader-timer, and reading-schedule behavior unless a listed defect requires a change;
- complete only the missing or defective requirements confirmed by code inspection;
- avoid recreating already-existing systems;
- keep all user-visible strings in the correct KMK resource ownership;
- keep the existing upstream app version and KMK feature versioning rules consistent;
- validate the final result on a real device where automated tests cannot prove behavior.

### Explicit non-goals

- Do not redo the entire Komikku 1.14 merge.
- Do not replace the official What's New Markdown renderer.
- Do not rewrite Recommendation Settings onto the upstream Preference DSL merely to avoid a focused KMK search index.
- Do not add a new rating model for title-specific dislike; existing Not Interested/Seen behavior remains the title-specific exclusion path unless live code proves otherwise.
- Do not change normal global search result limits.
- Do not add automatic title-based grouping or merge behavior.
- Do not expose model names, build-channel terms, internal workflow terminology, or implementation-plan language in the app.
- Do not add analytics, telemetry, or remote collection.

## 2. Required Preflight Before Editing

Claude must read, in order:

1. `docs/IMPLEMENTATION_PLAN_STANDARD.md`.
2. `docs/KMK_MARKDOWN_ENCYCLOPEDIA.md`.
3. `docs/recommendations/README.md`.
4. `docs/recommendations/CURRENT_STATE.md`.
5. `docs/recommendations/NEXT_WORK.md`.
6. `RECOMMENDATION_VERSIONING.md`.
7. `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_AUDIT.md`.
8. `docs/community/KMK_UPSTREAM_1_14_DETAILED_RECONCILIATION_PLAN.md`.
9. `docs/community/KMK_UPSTREAM_1_14_RECONCILIATION_IMPLEMENTATION.md`.
10. `docs/community/KMK_RECS_V0_8_9_POST_IMPLEMENTATION_EVALUATION.md`.
11. `docs/recommendations/KMK_RECS_V0_8_9_WHATS_NEW_AND_RECOMMENDATION_SETTINGS_SEARCH_PLAN.md` and its implementation report.

Then verify against the live tree:

- `app/build.gradle.kts` and `KmkRecsReleaseNotes.kt`;
- `RecommendationSettingsIndexScreen.kt`, `RecommendationSettingsSearchIndex.kt`, and `RecommendationSettingsSearchScreen.kt`;
- `ReaderActivity.kt`, `ReaderViewModel.kt`, and `LatestChapterCompletionPolicy.kt`;
- `SourceEvaluationScreen.kt`, `SourceEvaluationScreenModel.kt`, `SourceEvaluationOutdatedReconciliation.kt`, and the source-evaluation queue/diagnostics helpers;
- `RatedMangaScreen.kt`, `LovedMangaScreenModel.kt`, `LovedMangaDuplicateGrouper.kt`, and the rated selection helpers;
- `RecommendationNonInstalledDiscoverySettingsScreen.kt` and its screen model/scorer;
- `BrowsePersonalRecommendationsTab.kt` and the Library tab quick-access integration;
- `BackupDecoder.kt`, migration `63.sqm`, memo adapters, backup/restore models, and sync code;
- every existing test named by the implementation reports.

Before editing, Claude must write a preflight table with columns: requirement, current code path, current behavior, evidence, planned phase, and status. If the live tree differs from this plan, stop that phase, document the discrepancy, and update the plan before changing code.

## 3. Claude Execution Assignment

### Phase assignments

| Phase | Recommended model | Effort | Reason |
| --- | --- | --- | --- |
| Preflight and 1.14 compatibility audit | Opus | high or xhigh | Requires cross-version comparison, migration review, and conflict detection. |
| Rating prompt lifecycle fix | Sonnet | high | Focused implementation with reader lifecycle and navigation risk. |
| Recommendation Settings search completion | Opus | high | Requires stable anchors, navigation state, and responsive UI design across custom screens. |
| Rated/Sources To Try search and source-evaluation UI completion | Sonnet | high | Multi-screen Compose work with shared filtering/action behavior. |
| Taste suggestions and diagnostics | Sonnet | high | Requires local aggregation, privacy-safe persistence/read paths, and explainability. |
| Backup decoder hardening and migration regression work | Opus | high or xhigh | Data integrity and malformed-input behavior are intelligence-sensitive. |
| 1.14 compatibility validation and final release review | Opus | high or xhigh | Requires final cross-system review and release-risk analysis. |
| Mechanical documentation/version updates | Sonnet | medium | Bounded after code and test results are final. |

If Claude is operating in one plan-first/execution workflow, use `opusplan`. Otherwise use separate sessions with the assignment above. At the beginning of each phase, verify the active model and effort with `/status` or the visible Claude Code model indicator. Record the actual model and effort in the implementation report.

## 4. Phase A — Correct Chapter-Completion Rating Prompt Lifecycle

### Current issue

`LatestChapterCompletionPolicy` correctly detects genuine completion of the latest available chapter, but `ReaderViewModel.maybeShowChapterCompletionRatingPrompt()` currently places the rating dialog into reader state immediately after completion. The requirement is to finish the chapter first and show the prompt when the user exits the reader, without repeatedly prompting for the same completion.

### Required behavior

- Reaching the final page of the latest available chapter records an eligible completion event, but does not interrupt the reader with the rating dialog.
- Pressing Back or otherwise leaving the reader after that completion presents the rating prompt at the correct exit boundary.
- Leaving the reader without completing the latest chapter does not show the prompt.
- The prompt remains dismissible at every step.
- Rating commits exactly once through the existing exclusive `SetMangaTaste` path.
- The optional “rate other versions” step appears only after a successful rating and only when a confirmed cross-source group exists.
- Choosing No, Close, Back, or outside dismissal returns to the manga screen that would normally receive the reader result; it must not unexpectedly jump to the library or reopen the reader.
- Rotation, process recreation, repeated page callbacks, manual chapter selection, previous-chapter navigation, schedule blocking, and reader errors must not duplicate or incorrectly trigger the prompt.

### Code work

Inspect and modify only the current reader completion state/event path. Prefer a pure reducer/policy for the pending completion prompt state. Do not store screen objects, `CrossExtensionMatchMode`, or other non-Parcelable objects in Bundles/intents. Use primitive extras only for the existing cross-extension route.

Add or extend tests for:

- genuine latest completion;
- last page versus page-not-yet-finished;
- repeated page callbacks;
- manual chapter navigation;
- previous chapter and non-latest chapter;
- schedule-blocked reader;
- Back/exit transition;
- dismiss at rating step;
- dismiss at other-versions step;
- rating exclusivity and no duplicate database writes;
- recreation of pending completion state where supported by the existing lifecycle architecture.

## 5. Phase B — Complete Recommendation Settings Search

### Current issue

The current search indexes seven category destinations, but it does not index individual controls or provide stable in-screen anchors. The attached requirements expect searches for source priority, tags, evaluation, reassessment, installer behavior, cache, diagnostics, matching, and related controls to land at the relevant control rather than merely opening a broad category screen.

### Required behavior

- Preserve the current category-level search as a fallback.
- Add a stable entry for every meaningful control, subsection, or action that a user could reasonably search for.
- Each result must contain a stable destination identifier and optional anchor identifier; never use a fragile list index as the anchor.
- Selecting a result opens the correct screen and scrolls/highlights the target subsection when that screen supports it.
- If a control is unavailable because of installer mode, device capability, source state, or feature state, show it as unavailable with an explanation rather than silently omitting it.
- Empty query behavior must match the intended official settings-search behavior and must not flicker or crossfade results incorrectly.
- Query updates must be stable under rapid typing, recomposition, rotation, and back navigation.
- Search must not mutate preferences or start network/database work merely to display results.
- Results must be capped only for the settings-search display; do not alter source search or global search limits.

### Exact areas to index

At minimum, index:

- For You visibility, hide-known, Not Interested, minimum chapter count, result budgets, refresh/discovery controls;
- source priority ordering, top-three behavior, language filtering, source quality feedback, and reset/reorder actions;
- preferred, blocked, and aliased tags;
- Source Evaluation batch size, continuation, stale reassessment, recommendation compatibility, diagnostics, quarantine, and installer mode;
- Sources To Try filters, sorting, explicit-content handling, installation, and hidden-source recovery;
- same-manga matching, result limits, selection defaults, group management, and Best Version entry points;
- background/network/installer behavior, Private/Shizuku availability, retry, cancellation, and cleanup;
- cache/discovery reset and management diagnostics;
- backup/sync-related recommendation management where a real destination exists.

### Tests

Add pure index/anchor tests for every destination category, duplicate titles, unavailable entries, ranking tiers, punctuation normalization, stable ordering, and anchor identity. Add navigation/state tests for result selection and scroll-target dispatch. If Compose UI tests remain unavailable, create deterministic destination/anchor contract tests and record manual phone/tablet verification as required.

## 6. Phase C — Searchable Rated Manga Collections

### Required behavior

Loved, Liked, and Disliked screens must share the existing `RatedMangaCollectionContent`/`LovedMangaScreenModel` architecture rather than introducing three search implementations.

- Add a search field or the existing library-compatible search affordance to all three rating screens.
- Filter the already loaded, installed-source display items locally; do not issue a network search.
- Match title, alternate title data already available, source name, and group/version metadata where safely available.
- Preserve sort mode, duplicate grouping, confirmed-link grouping, selection mode, bulk actions, export, source linking, recommendations, and group management while a query is active.
- Search state must survive recomposition and selection-mode changes and clear only on explicit clear/navigation policy.
- Empty results must be distinct from loading and load failure.
- Do not expand the Loved Manga list to uninstalled sources.
- Do not auto-merge similar titles merely because search found them.

Add tests for all rating values, query normalization, no-match state, selection under filtering, select-all scope, grouping, rating exclusivity, and clearing the query.

## 7. Phase D — Sources To Try Search, Explanation, Sorting, And Direct Action

### Required behavior

Sources To Try must remain focused on eligible, non-installed suggestions. Add:

- local search by extension/source name, language, repository, and stable package identity;
- sorting/filtering by fit verdict, catalogue fit, recommendation compatibility, evidence confidence, freshness, language, and explicit-risk state where data exists;
- a details expansion that explains the actual contributing signals and their confidence, including “not enough evidence” rather than inventing certainty;
- direct install or navigation to the extension-management action from Source Evaluation when the source is eligible;
- consistent handling when a source becomes installed, disabled, blocked, quarantined, language-filtered, or unavailable while the screen is open;
- no per-extension result limit changes to normal global search.

Reuse the existing source evaluation display policy and suggestion scorer. Do not duplicate verdict logic in the UI. Add tests proving that Source Evaluation and Sources To Try display the same verdict and eligibility for the same row.

## 8. Phase E — Taste Suggestions And Useful Diagnostics

### Taste suggestions

Build a pure local aggregation over existing rated manga metadata:

- preferred suggestions come from tags/genres on Love and Like items;
- blocked suggestions come from Dislike and explicit source/tag data only when the current preference semantics support that inference;
- normalize aliases using the existing alias system;
- exclude already preferred/blocked tags;
- require a minimum evidence count and display the evidence count;
- never infer a blocked tag from one isolated Dislike without sufficient evidence;
- show a clear insufficient-data state when the user has too few rated items or too little metadata;
- adding a suggestion must use the existing tag-preference mutation path and remain reversible.

### Diagnostics

Add a privacy-safe local diagnostics view only where data already exists:

- top preferred tags and evidence counts;
- top blocked tags and evidence counts;
- metadata-confidence and sample-count summary;
- rating counts and source-quality feedback counts;
- explanation of which signals are currently used and which are unavailable.

Do not expose raw URLs, cookies, extension internals, or full private content. Add pure aggregation tests, sparse-data tests, alias tests, rating-conflict tests, and privacy-sensitive logging checks.

## 9. Phase F — Source Evaluation UI And State Corrections

Audit and correct the current Source Evaluation screen without removing diagnostics that are needed for recovery.

- Keep primary actions visually prominent and move verbose technical details behind expandable Details/Diagnostics sections.
- Ensure “Evaluation completed” is cleared when leaving the screen or after the documented timeout; it must not remain stale indefinitely.
- Ensure the outdated queue counts only currently eligible sources and that installed, blocked, quarantined, language-filtered, or otherwise ineligible rows are not actionable in that queue.
- Ensure “Continue reassessing outdated” advances through subsequent batches rather than resetting to the first batch or reporting zero while eligible rows remain.
- Keep unassessed and outdated cursors independent and persist only the state required by the existing continuation design.
- Show explicit explanations for skipped/ineligible rows instead of silently failing.
- Make error rows actionable with retry/copy-safe-diagnostics behavior; never expose uncontrolled raw exception text.
- Ensure no Source Evaluation job conflicts with the background For You compatibility job.
- Verify the installer-mode messaging is shown only when the selected installer mode requires it.

Add reducer/policy tests for queue eligibility, continuation, cancellation, completion message lifecycle, conflict handling, and retry. Add regression tests for the previously reported “visible outdated rows but zero candidates” behavior.

## 10. Phase G — What's New Completion Decision

The current renderer is already the official Komikku Markdown renderer and must remain unchanged.

Claude must make and document one explicit decision:

1. Keep historical entries unchanged and state that official formatting applies from v0.8.9 onward; or
2. Mechanically convert historical entries to the New/Improve/Fix hierarchy while preserving every fact.

If option 2 is chosen, create a parser/validation test or a before/after audit that proves no version, bullet, or user-facing meaning was lost. Do not hand-rewrite 76 entries without an audit. Preserve every separate version entry, including fix versions. Add the v0.8.10 entry only after the implementation is actually complete.

## 11. Phase H — Backup Decoder Hardening

Fix the confirmed pre-existing malformed-backup crash in `BackupDecoder.decode()`:

- catch the actual exception family thrown by truncated-but-well-formed protobuf input, without catching cancellation or masking unrelated programmer errors;
- return the existing invalid-backup failure state/message used by the app;
- preserve compatibility with old backups, unknown future fields, valid KMK fields, and valid 1.14 memo fields;
- do not log raw backup payloads or sensitive manga metadata.

Add tests for valid old backups, valid current backups, unknown fields, truncated input, malformed field values, empty input, cancellation behavior, and error-message safety.

## 12. Phase I — Komikku 1.14 Compatibility Validation

Do not re-merge upstream. Validate the current tree against the implementation report and official 1.14 tag:

- confirm migration history `45–63` is append-only and generated SQLDelight schema matches the current database;
- run a real upgrade test from a pre-1.14 KMK database containing library, ratings, cross-source groups, source preferences, sync preferences, OCR exclusion state, timer/schedule state, and extension repository settings;
- verify extension-store conversion preserves repositories and source identity;
- verify memo insert/update/read/backup/restore/sync paths;
- verify source API compatibility for normal browse, global search, migration, For You, group recommendations, Source Evaluation, and Best Version;
- verify private/Shizuku/root installer behavior and cleanup;
- verify backup/sync proto fields 620–629 remain intact;
- verify OCR, reader timer, schedule enforcement, chapter-completion rating, and group recommendations after the upstream API changes;
- verify 1.14 app What's New separately from KMK-Recs What's New;
- verify debug package/version metadata and APK naming.

Any failure must be classified as regression, pre-existing defect, upstream behavior difference, or test-environment limitation. Do not mark the reconciliation complete merely because compilation passes.

## 13. Phase J — Device, Accessibility, And Release Verification

Use at least one phone-sized and one tablet-sized Android device or emulator. Test:

- fresh install;
- upgrade from the current pre-1.14 KMK APK;
- light/dark themes;
- large font and TalkBack;
- portrait/landscape rotation;
- backgrounding, lock screen, process recreation, and returning from notifications;
- offline/reconnect behavior;
- Source Evaluation batches and continuation;
- For You refresh, group recommendations, rated collections, and all search fields;
- reader completion rating prompt and schedule enforcement;
- extension install/update/uninstall through each supported installer mode;
- backup/restore and sync with old and current data;
- What's New and recommendation-settings navigation.

Record device model, Android version, APK hash, test case, result, and evidence. A missing device must remain an explicit release blocker, not be reported as passed by code inspection.

## 14. Documentation And Versioning Handoff

After all code and tests pass:

- update `CURRENT_STATE.md` with the actual v0.8.10 behavior;
- update `NEXT_WORK.md` and close only items proven complete;
- update `docs/recommendations/README.md`;
- update the encyclopedia if a new reference document or procedure was introduced;
- update `RECOMMENDATION_VERSIONING.md` with the app base version, KMK feature version, version code, and exact APK path;
- update `KmkRecsReleaseNotes.kt` with one v0.8.10 entry using official Komikku-style Markdown;
- update the 1.14 reconciliation report to map every plan phase A–J explicitly;
- correct the stale memo comments in `63.sqm` and any other documentation contradiction;
- write a final implementation report with changed files, tests, device results, deviations, limitations, and rollback notes;
- build exactly one final APK after all phases are complete;
- verify the APK filename matches the embedded app version and KMK feature version.

## 15. Required Verification Commands

Run in this order using the repository's JDK 17 procedure:

1. `spotlessApply`.
2. `spotlessCheck`.
3. Focused pure-policy, search, reader, queue, migration, backup, and aggregation tests.
4. Full `:app:testDebugUnitTest`.
5. Any available Compose/UI/instrumented tests.
6. `:app:assembleDebug` only after all implementation phases and tests pass.
7. APK metadata and SHA-256 verification.

The final report must include the actual test counts from JUnit XML, not only the Gradle success line. If a command cannot run, record why and do not claim it passed.

## 16. Completion Criteria

This plan is complete only when:

- every requirement in the combined 0.8.9 prompt is classified and either implemented or explicitly rejected with user approval;
- the rating prompt is lifecycle-correct and non-repeating;
- Recommendation Settings, rated manga, and Sources To Try searches are functional and tested;
- source evaluation and Sources To Try share truthful eligibility, verdict, and explanation policies;
- tag suggestions and diagnostics handle sparse data safely;
- the malformed-backup crash is fixed and tested;
- Komikku 1.14 upgrade, migration, source, installer, backup, sync, reader, OCR, and recommendation behavior is validated;
- device and accessibility QA is recorded;
- documentation and versioning are internally consistent;
- one final APK is built and handed off with the correct name;
- no user-visible development-channel language has been introduced.

Claude must not provide a completion claim or final APK while any completion criterion is unverified.
