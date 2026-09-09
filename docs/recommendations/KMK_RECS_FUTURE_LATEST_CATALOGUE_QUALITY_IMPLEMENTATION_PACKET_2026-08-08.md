# KMK Future Latest Catalogue and For You Reordering Implementation Packet

Date: 2026-08-08 (updated 2026-08-08, batch L2)
Status: `complete for all coded steps (0-7)` — batch L2 closed Steps 2 and 4, which batch L1 had left
`planned`. One non-code item remains open: Domain D rendered/visual validation of the quick-access
panel is `BLOCKED_EXTERNAL` (no Compose UI/screenshot test infrastructure exists in this repository).
Authorization: implementation of the above was authorized and performed on 2026-08-08. No migration,
APK installation, or device work was performed or is authorized by this packet.
Owning proposal: `KMK_RECS_FUTURE_LATEST_CATALOGUE_QUALITY_AND_NOVELTY_PLAN_2026-08-08.md`
Implementation ledger: `private/docs/audits-and-reports/KMK_LATEST_CATALOGUE_AND_EXPOSURE_IMPLEMENTATION_LEDGER_2026-08-08.md`

## Batch L1 result (2026-08-08)

| Packet step | Status | Notes |
| --- | --- | --- |
| Step 0 — reconcile and challenge | `complete` | Audit, ownership classification, transition matrix, and an adversarial challenge pass with a recorded surviving failure scenario are in the ledger, written before any edit. |
| Step 1 — pure policies | `complete` | `RecommendationDiscoveryLane`, `RecommendationCatalogueLanePolicy`, `RecommendationLatestBudgetPolicy`, `RecommendationExposurePolicy`, `RecommendationDisplayReranker`. |
| Step 2 — exposure persistence | `planned` | **Not started.** No `.sq`, no migration, no repository/interactors/DI. `64.sqm` re-verified as the next free number. |
| Step 3 — Latest discovery | `complete` | `tryLatestCatalogueLane(...)`, bounded per-refresh budget, typed provenance, all hard filters preserved, Popular fallback preserved. |
| Step 4 — exposure capture + reranking | `planned` | **Not started**, blocked by Step 2. The exposure policy and reranker are unit-tested but called by no production code. |
| Step 5 — configurable controls | `partial` | Latest exploration share shipped (preference, validated setter, For You sources row, localized strings, search index, Action History journalling). Exposure-window preference declared but intentionally has **no UI row** until Steps 2/4 land. |
| Step 5A — minimum-chapter conformance | `complete` | Central `RecommendationMinChapterCountPolicy`; validated reads and writes; all consumers unified; existing behavior contract proven unchanged; copy shortened; widget/section unchanged. |
| Step 6 — quick-access panel repair | `complete` | `EdgeQuickAccessPanelLayoutPolicy` + panel rewrite. Destinations, navigation, scrim, `BackHandler`, and the 28dp painted handle preserved. |
| Step 7 — documentation | `complete` | Ledger, `CURRENT_STATE.md`, `NEXT_WORK.md`, `README.md`, and this packet updated. |

**Validation actually run (JDK 17, host only):** `spotlessApply` then `spotlessCheck` — clean;
`:app:testDebugUnitTest` — **2482 tests, 1 skipped, 0 failures, 0 errors** (89 new tests in 6 new
files); `:app:assembleDebug` — successful; `git diff --check` — clean. No device, emulator, APK
install, database mutation, commit, push, or security-scan re-run was performed.

**Deliberately not claimed:** visual/screenshot evidence from a current build, device interaction
testing, and any control-plane state promotion. The control plane remains terminal `BLOCKED` for its
documented device/publication gates and the source worktree is dirty, so no certificate is issued.

## Batch L2 result (2026-08-08) — structural completion pass

| Packet step | Status | Notes |
| --- | --- | --- |
| Step 2 — exposure persistence | `complete` | New `recommendation_exposure` `.sq` + migration, `RecommendationExposureRepositoryImpl` (real SQLDelight-backed), 4 interactors (Get/Record/Prune/Clear), `KMKDomainModule` registration. Confirmed excluded from the backup allowlist (grep-verified). |
| Step 4 — exposure capture + reranking | `complete` | `recordVisibleExposure()` fires from a `LaunchedEffect(resultGeneration, allDone)` gated by `RecommendationExposureCapturePolicy.shouldRecord()` (once per settled generation, never on recomposition). Reranking runs inside `RecommendationCandidateMemoryRanker.merge()` **before** `.take(limit)` — the structural requirement for promoting less-exposed candidates into a capped row. |
| Step 5 (remainder) — exposure-window control | `complete` | New "Repeat title cooldown" settings row (7/14/30 days, default 14) now controls real reranking behavior, closing the "declared preference with no UI row" gap L1 left open. |
| Domain A (structural correction, not in the original packet) — Latest lane fallback-only bug | `complete` | The original Step 3 implementation still gated Latest behind `shouldAttempt(hadRawResults, ...)`, which never ran once personalized results existed. Fixed with `tryAdditiveLatestAugmentation()`, sharing the existing budget pool, capped at `MAX_ADDITIVE_SLOTS_PER_SOURCE = 2` (worst case 40% of a row) so personalized results stay the majority in every supported configuration. |
| Step 5A (remainder) — pipeline-level proof | `complete` | Prior coverage was policy-unit-tests-only. Added 4 tests proving a real persisted threshold changes actual `merge()` output. |
| Step 6 (revalidation) — quick-access panel | `not re-validated visually` | Code unchanged since L1; re-confirmed via grep that no Compose UI/screenshot test infrastructure exists anywhere in this repository. Rendered appearance remains unverified — `BLOCKED_EXTERNAL`, not silently claimed complete. |
| Step 7 — documentation | `complete` | Ledger's Domain A-E status table, `CURRENT_STATE.md`, `NEXT_WORK.md`, `README.md`, and this packet all updated with new dated entries (append-only — L1 records left unchanged). |

**Validation actually run (JDK 17, host only), batch L2:** `:data:compileDebugKotlin`/
`:app:compileDebugKotlin`/`:app:compileDebugUnitTestKotlin` confirmed to actually re-execute (not
merely up-to-date) after fixing a real `awaitList`/`Query<T>` compile bug found mid-pass;
`:app:testDebugUnitTest` — **2517 tests, 0 failures, 0 errors, 1 skipped**; `spotlessCheck` — clean;
`git diff --check` — clean (one CRLF-normalization notice, not an error). No device, emulator, APK
install, database mutation, commit, or push was performed. Full detail, including the Domain A-E status
table and the do-not-report-completion checklist: the implementation ledger linked above.

**Still not claimed:** visual/rendered validation of the quick-access panel, and any control-plane
state promotion. The git worktree remains dirty; nothing in this pass was committed or pushed.

## Batch L5 result (2026-08-09) — tracker fail-safety + migration 64 coverage

| Defect | Fix | Status |
| --- | --- | --- |
| A failed tracker lookup was indistinguishable from "nothing is tracked", so the reranker could demote a title the user actively tracks. Root enabler: `GetTracks.await()` swallows its own errors and returns an empty map. | New additive `GetTracks.awaitOrNull()` (returns null on failure; `await()` unchanged, both existing callers verified untouched) feeding a new `RecommendationDisplayReranker.TrackedState` tri-state. `rerank()` returns the input unchanged when tracker state is `Unknown`. `InteractionSignals.tracked` defaults to `Unknown` so a forgetful caller gets the safe path. | `complete` |
| `64.sqm` had no test coverage at all | New `KmkMigration64ExposureTest` (14): table, all six columns, composite `(source_id, url)` PK via `PRAGMA` pk ordinals, both indexes, idempotency, real 46..62 upgrade preserving unrelated tables and rows, fresh `Database.Schema.create` path, and repository insert/increment/prune/clear shapes. All in-memory. | `complete` |
| `64.sqm` used bare `CREATE TABLE`/`CREATE INDEX`, violating the project's `IF NOT EXISTS` idempotency contract (surfaced only once coverage was added) | `IF NOT EXISTS` added to the table and both indexes; still additive-only | `complete` |

**Validation (batch L5):** `:app:testDebugUnitTest` — **2574 tests, 0 failures, 0 errors, 1 skipped**
(baseline 2545, +29); compile, focused tracker/migration/exposure/merge/settings suites, `spotlessCheck`,
and `git diff --check` all exit 0. **Working tree dirty: 378 paths** (baseline 376; delta is exactly the
two new test files). Nothing committed, pushed, or cleaned.

**`PARTIAL_VALIDATION`:** direct `BrowsePersonalRecommendationsScreenModel` harness coverage. Blockers:
no Robolectric in the project, the constructor needs a real Android `Application`
(`context.isOnline()`/`context.toast(...)`), and `init` starts a full load on construction. Not attempted
rather than faked.

**Still `BLOCKED_EXTERNAL`:** Step 6 rendered/visual validation — no Compose UI or screenshot
infrastructure exists in this repository.

## Batch L3 result (2026-08-09) — structural repair pass

A re-audit of the **current source** (not the L2 summary) found four defects L2 had reported complete.
All four are fixed; the L2 table above is retained unchanged as historical record.

| Defect | Fix | Status |
| --- | --- | --- |
| Personalized majority was only argued via `latestSlots / displayLimit`, which fails when the personalized lane is sparse (1 personalized + 2 Latest = 2/3 Latest) | `PersonalRecommendation.lane` carries provenance through the merge; new `RecommendationLatestBudgetPolicy.enforcePersonalizedMajority()` decides the final composition from realised per-lane counts at the cap | `complete` |
| Exposure identity collapsed to url-only, so two sources sharing a relative url cross-penalised each other | `RecommendationDisplayReranker.ExposureKey(sourceId, url)` used end-to-end | `complete` |
| Tracker exemption hardcoded `isTracked = false` | Real batched `GetTracks.await(ids)` lookup via `resolveTrackedExposureKeys()`, fails open to "no exemption" | `complete` |
| `clearExposureHistory()` had no UI caller, no confirmation, no outcome handling | "Clear repeat history" settings row + confirmation dialog + `clearExposureHistoryNow()` with typed success/failure and cancellation rethrow | `complete` |

**Validation (batch L3):** `:app:testDebugUnitTest` — **2545 tests, 0 failures, 0 errors, 1 skipped**
(baseline 2517, so +28 net new); `spotlessCheck` clean; `:app:assembleDebug` BUILD SUCCESSFUL;
`git diff --check` clean. **Working tree dirty: 376 paths** (baseline 375; delta is exactly the one new
test file). Nothing committed, pushed, or cleaned.

**Still `BLOCKED_EXTERNAL`:** Step 6's rendered/visual validation. No Compose UI or screenshot test
infrastructure exists in this repository and none was added to manufacture a pass.

## Objective and User Outcome

Improve For You discovery without treating unfamiliarity as dislike. Personalized matches remain
the dominant result lane. A bounded Latest-catalogue lane adds controlled exploration for every
source that reliably supports `CatalogueSource.getLatestUpdates(page)`. Candidates that are visible
repeatedly for 14 days without being opened, rated, added to the library, or locally associated
with a tracker are softly moved lower in their source/topic listing. They remain available and may
rise again after exposure decay or explicit interaction.

The user must continue to see the same source/topic structure, existing filters, minimum chapter
rules, source quality boundaries, and recognizable Komikku interaction patterns.

## Settled Decisions

- Exposure window default: 14 days.
- Exposure event: a card present in a loaded, visible result state; fetch-only data is not exposed.
- Ignored is not dislike and must not change taste, blocklists, or hard eligibility.
- Latest is additive and source-capability-dependent, never a universal replacement for Popular or
  personalized search.
- Latest candidates receive a small bounded exploration share, but all existing filters still run.
- Repeated untouched candidates are reordered downward, never deleted or permanently hidden.
- Personalized relevance remains dominant over novelty-only candidates.
- Provenance is primarily a Source Evaluation/detail diagnostic, not a badge on every For You card.
- The quick-access panel visual repair is in scope as a separate UI workstream, not part of scoring.
- Settings must use the existing Komikku preference-row, section-header, copy-length, localization,
  theme, and accessibility conventions; a setting is not complete merely because a preference key
  exists.

## Current Repository Ground Truth

Verified during the planning audit:

| Surface | Current file/symbol | Current behavior | Classification |
| --- | --- | --- | --- |
| Source capability | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt`, `getPopularManga`, `getLatestUpdates` | Suspend Popular and Latest APIs already exist; unsupported implementations can throw | upstream-protected/shared integration |
| Source transport | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt`, `fetchLatestManga` path and parser hooks | Existing source-runtime transport supports Latest for sources that implement it | upstream-protected |
| For You owner | `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt`, `refresh`, `searchSource`, `processRawCandidates`, `mergeFreshAndRememberedCandidates`, `discoverAdditionalPage`, `persistForYouPreviewSnapshot` | Owns source batching, query attempts, catalogue fallback, filters, scoring, candidate memory, Top Picks, and preview state | user-owned/shared integration |
| Hard visibility | `app/src/main/java/exh/recs/RecommendationCandidateVisibilityPolicy.kt`, `filterVisibleCandidates` | Applies favorite, rating, explicit Seen, known, and minimum-chapter rules | user-owned; preserve |
| Candidate memory | `domain/.../RecommendationCandidateMemory.kt`, `data/src/main/java/tachiyomi/data/taste/RecommendationCandidateMemoryRepositoryImpl.kt`, `app/.../RecommendationCandidateMemoryStore.kt` | Local-only derived cache; current `lastSeenAt` means candidate-memory update time, not UI exposure | user-owned/shared integration |
| Candidate ranking | `app/src/main/java/exh/recs/memory/RecommendationCandidateMemoryRanker.kt`, `PersonalRecommendationScorer`, `CombinedPicksAccumulator.kt` | Re-scores candidates and combines source contributions | user-owned/shared integration |
| Discovery progress | `app/src/main/java/exh/recs/memory/RecommendationDiscoveryPlanner.kt`, `RecommendationDiscoveryProgressStore.kt` | Tracks pages, retries, exhaustion, and query progress | user-owned; preserve |
| Preferences | `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` | Owns For You languages, source order, visibility, chapter minimum, enrichment cap, result budget, and source preferences | user-owned |
| Persistence schema | `data/src/main/sqldelight/tachiyomi/data/recommendation_candidate_memory.sq`, `recommendation_discovery_progress.sq`, migrations currently through `63.sqm` | Existing local-only recommendation tables and migration chain | user-owned/shared integration |
| DI | `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` | Registers current candidate-memory/progress repositories and interactors | user-owned/shared integration |
| For You UI | `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt` | Renders loaded/error/empty states and invokes the quick-access panel | user-owned/shared integration |
| Quick-access UI | `app/src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt`, `RecommendationSettingsQuickAccessPanel`, `EdgeQuickAccessPanel` | Shared right-edge panel with five settings destinations; currently narrow 96dp labels, 10sp text, 64dp handle, 70% height | user-owned/shared integration |
| Settings | `RecommendationSettingsIndexScreen.kt`, `RecommendationSourcePrioritySettingsScreen.kt`, `RecommendationTasteTagsSettingsScreen.kt`, `RecommendationDiagnosticsSettingsScreen.kt` | Existing settings ownership and navigation for future exposure/diversity controls | user-owned/shared integration |

### Existing minimum-chapter setting assessment

The current minimum-chapter behavior is **implemented and substantially tested, but not fully
validated for publication**:

- `RecommendationTasteTagsSettingsScreen.kt` presents `Off/5/10/20/50` through the existing
  `SameMangaListPrefRow` and places the control under Taste and filters.
- `RecommendationsSettingsScreenModel.setMinChapterCount()` persists the raw integer without a
  central supported-value sanitizer.
- `BrowsePersonalRecommendationsScreenModel.processRawCandidates()` and
  `mergeFreshAndRememberedCandidates()` load local chapter counts and pass them to the shared
  `RecommendationCandidateVisibilityPolicy`.
- The shared policy correctly hides a known count below the threshold, keeps an exact-threshold
  count, and fails open for unknown counts. Existing tests cover those cases and memory/filter
  parity, but not malformed persisted values, setter validation, or every UI/pipeline call site.
- The current summary copy is longer than the surrounding compact settings vocabulary and the
  numeric labels are not yet proven against the full localization/formatting standard.

Therefore the implementation packet must include a minimum-chapter conformance repair/audit before
Latest work is considered complete.

The existing `RecommendationCandidateMemory.lastSeenAt` must not be silently repurposed as UI
exposure. It is updated when candidate memory is written and does not prove that a user saw a card.

## Ownership and Non-Goals

### Ownership

One future implementation batch owns:

1. Latest lane capability/provenance and bounded discovery.
2. Local exposure persistence and soft reranking.
3. User-facing exposure/diversity settings.
4. Quick-access panel visual conformance and its regression evidence.

### Non-goals

- No machine-learning model, remote profile, telemetry, or server-side user history.
- No inference that an ignored title is disliked.
- No removal of candidates solely due to exposure.
- No change to explicit Not Interested, Love, Like, Dislike, blocked-tag, language, library, or
  tracker semantics.
- No Popular replacement, global Latest assumption, or source-specific hardcoding of Vortex.
- No network call from a settings preview or diagnostics screen.
- No action-history entry for passive exposure; viewing is not an undoable mutation.
- No backup/sync inclusion of local exposure history unless a separate approved privacy contract is
  created.

## Proposed Types and Storage Contract

### New domain types

Add pure, testable domain types in `domain/src/main/java/tachiyomi/domain/taste/model/`:

- `RecommendationDiscoveryLane`: `PERSONALIZED`, `LATEST_CATALOGUE`, `POPULAR_CATALOGUE`, and
  existing detail-recommendation provenance if the audit confirms it belongs in the shared model.
- `RecommendationExposure`: source ID, stable source URL, optional local manga ID, first exposed
  timestamp, last exposed timestamp, exposure count, last meaningful interaction timestamp, and
  lane/provenance summary. Raw titles and thumbnail URLs are not required for exposure policy.
- `RecommendationExposureRepository` in `domain/.../taste/repository/` with get-by-keys,
  upsert-batch, prune-expired, clear-all, and delete-by-source operations.

Add app-layer policy types in `app/src/main/java/exh/recs/`:

- `RecommendationExposurePolicy.kt`: validates timestamps, computes the 14-day window, distinguishes
  exposure from interaction, and returns a bounded soft penalty without network/database work.
- `RecommendationCatalogueLanePolicy.kt`: checks source capability, schedules at most one bounded
  Latest page per eligible source/refresh, records unsupported/empty/error outcomes, and preserves
  Popular fallback.
- `RecommendationDisplayReranker.kt`: stable, deterministic, display-only reorder after hard
  filtering and personal scoring. It must not mutate the base relevance score or eligibility.

### Storage

Add `data/src/main/sqldelight/tachiyomi/data/recommendation_exposure.sq` as a local-only derived
table. The next migration is `64.sqm` only if no newer migration exists when implementation starts;
otherwise use the next unused migration number. The table should have:

- primary key `(source_id, url)`;
- `manga_id` nullable;
- `first_exposed_at`, `last_exposed_at`, `exposure_count`;
- `last_interaction_at` nullable;
- `last_lane` or a compact lane bitset only if diagnostics genuinely needs it;
- indexes for `last_exposed_at` and source.

Do not store raw result snapshots, query strings, thumbnail URLs, credentials, tracker data, or
source/account identifiers beyond the existing local source/url identity needed for the feature.
Exclude the table from backup/sync/OCR/export by default, matching the existing candidate-memory
local-only contract. Add explicit clear/prune ownership and rollback notes.

Implement the repository in `data/src/main/java/tachiyomi/data/taste/RecommendationExposureRepositoryImpl.kt`,
add interactors beside the existing recommendation candidate-memory interactors, and register them
in `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt`. A failed exposure write must not fail or
change the visible For You result.

## Exact Implementation Sequence

### Step 0: Reconcile and challenge before edits

1. Re-read this packet, `CURRENT_STATE.md`, `NEXT_WORK.md`, documentation rules, and the current
   recommendation decision register.
2. Recheck Git status from `komikku-source`; isolate unrelated dirty/untracked files.
3. Confirm the current migration number, current source API version, and the actual mounted panel
   layout before touching any file.
4. Write the challenge result to the implementation ledger. At minimum challenge unsupported
   Latest sources, repeated Compose exposure writes, stale local IDs, source URL changes, offline
   fallback, candidate jitter, migration rollback, export leakage, and panel tap-target regressions.
5. Stop for a decision if any of the settled decisions or existing hard-filter semantics would need
   to change.

### Step 1: Implement pure policies first

1. Add `RecommendationDiscoveryLane` and lane serialization constants; keep existing query strategy
   serialization backward-compatible.
2. Add `RecommendationExposurePolicy` with deterministic functions for:
   - `isVisibleExposureEvent(isLoading, hasLoadedState, candidateCount)`;
   - `isWithinWindow(now, lastExposedAt, windowDays)`;
   - `isUntouched(exposure, isInLibrary, isRated, isTracked)`;
   - `softPenalty(repeatedExposureCount, age, window)`;
   - stable reranking tie-break data.
3. Add `RecommendationCatalogueLanePolicy` with no I/O. It must distinguish capability absent,
   unsupported, empty, successful, malformed, cancelled, and recoverable error.
4. Add `RecommendationDisplayReranker` with these invariants:
   - hard-filtered candidates never re-enter;
   - base `PersonalRecommendation.score` remains unchanged;
   - a strong personalized match cannot be displaced by novelty alone;
   - the output is a permutation of the accepted input, never a deletion;
   - equal inputs and timestamps produce equal order;
   - source/topic grouping and row caps remain valid.

### Step 2: Add local exposure persistence

1. Add the SQL table, indexes, queries, migration, domain model, repository, interactors, DI, and
   migration structural tests.
2. Keep exposure writes best-effort and cancellation-safe: rethrow `CancellationException`, swallow
   only the bounded persistence failure after logging a sanitized category.
3. Add prune behavior for records older than the configured retention plus a bounded grace period.
   Prune must be idempotent and must not touch manga, taste, library, tracker, or source tables.
4. Add a clear-exposure action only in Management and diagnostics if the existing settings ownership
   and privacy wording support it. It must report that it clears ordering history, not ratings.

### Step 3: Integrate Latest discovery

1. Add a lane loader beside `RecommendationCatalogueFallbackPolicy` that accepts a `CatalogueSource`
   and calls `getLatestUpdates(1)` through the existing `SourceRuntime` boundary. Never call the
   deprecated Observable API when the suspend API is available.
2. Treat `UnsupportedOperationException`, missing capability, malformed `MangasPage`, cancellation,
   offline state, and source runtime failures according to the existing per-source failure policy.
3. Extend the source outcome/provenance data in `BrowsePersonalRecommendationsScreenModel` so
   Latest, Popular, and personalized results remain distinguishable in memory and diagnostics.
4. In `searchSource`, preserve the current strict-to-lenient personalized query chain first. Add one
   bounded Latest page for eligible sources as an additive exploration pool, then retain the current
   Popular catalogue fallback. Do not increase network work without an explicit budget policy.
5. Apply `processRawCandidates` and the existing visibility policy to Latest exactly as for other
   candidates. Latest must not bypass blocked tags, minimum chapters, known/rated/seen filtering,
   metadata confidence, or source runtime isolation.
6. Include lane/provenance in candidate-memory and cache fingerprints so a Popular result cannot be
   mislabeled as Latest. Preserve old rows by treating missing provenance as legacy/unknown, not as
   Latest.
7. Feed the accepted, reranked per-source results to `CombinedPicksAccumulator` and the existing
   preview snapshot so Top Picks and settings preview reflect the same final ordering.

### Step 4: Integrate exposure capture and soft reranking

1. Add `GetRecommendationExposure` and `RecordRecommendationExposure` to
   `BrowsePersonalRecommendationsScreenModel` or its existing injected store boundary; keep storage
   ownership outside the composable.
2. In `BrowsePersonalRecommendationsTab.kt`, trigger one exposure batch only after the loaded state
   contains visible cards. Key the effect by a stable refresh/result generation, not by Compose
   recomposition, so the same card is not counted dozens of times.
3. Skip exposure writes for loading, empty, error, wrong-route, or Evaluation Mode states unless a
   later approved policy explicitly defines evaluation exposure. No raw UI XML, screenshot, URL, or
   title dump may be written.
4. Load exposure summaries once per refresh and pass them into the display reranker. Do not perform
   one database query per card.
5. Rerank after hard visibility, localization, enrichment, personal scoring, and cross-source
   deduplication, but before the final per-source row cap and Top Picks accumulation. Preserve a
   minimum exploration reserve so an old candidate cannot occupy every visible slot.
6. Record actual interaction separately through existing rating/library/tracker state reads. Do not
   synthesize a negative action from no interaction.
7. Ensure a 14-day expiration removes only the soft penalty. It must not remove the candidate from
   memory or hard eligibility.

### Step 5: Add configurable controls

Extend `SourcePreferences.kt` with validated preferences and explicit defaults:

- exposure window days: default `14`, supported values initially `7/14/30`;
- Latest exploration budget: proposed default `20%`, exact choices decision-gated after local
  fixture calibration;
- optional diversity mode: default balanced/compatible with existing ranking, only if a separate
  setting is justified by the visual audit.

Place controls under the existing Recommendation Settings ownership, likely
`RecommendationSourcePrioritySettingsScreen.kt` or the existing taste/filters/For You source
surface after inspecting the current section boundaries. Do not create a new settings destination
just for two controls. Add localized strings, summaries, theme behavior, accessibility semantics,
malformed-preference fallback, and search-index entries.

### Step 5A: Harden the existing minimum-chapter setting and settings language

This is an in-scope prerequisite, not an optional cleanup:

1. Add `app/src/main/java/exh/recs/RecommendationMinChapterCountPolicy.kt` as the single pure
   contract for supported values (`0`, `5`, `10`, `20`, `50`), default resolution, malformed-value
   fallback, and display-label selection. Do not scatter the list across screens.
2. Make `SourcePreferences.recommendationMinChapterCount()` or every established read boundary use
   the policy's sanitized value. Make `RecommendationsSettingsScreenModel.setMinChapterCount()`
   persist only a supported value and update state with that same resolved value.
3. Apply the resolved value consistently in `BrowsePersonalRecommendationsScreenModel`,
   `RecommendsScreenModel`, cache/fingerprint construction, and any search/preview consumer found by
   the intake audit. A changed threshold must invalidate/recompute the relevant result path.
4. Preserve the current behavior contract: a locally known count below the threshold is hidden, a
   count at or above it is visible, an unknown count is not filtered, and a lookup failure fails
   open without crashing or silently hiding all candidates.
5. Keep `SameMangaListPrefRow`, `SectionHeader`, and existing preference widgets. Do not replace
   the setting with a custom slider or a new card style. Use concise localized strings and normal
   value labels (`Off`, `5`, `10`, `20`, `50`) through resources rather than raw long interpolated
   copy. Validate narrow-width wrapping and accessibility announcements.
6. Add a settings-copy audit for every new exposure/diversity control and the existing chapter
   setting: title length, one-sentence summary, value label, section placement, search index,
   localization fallback, theme contrast, and consistency with adjacent More > Settings rows.
7. Do not hard-code the 14-day value, exploration percentage, or copy in composables. Defaults and
   allowed values belong to typed policies/preferences; user-facing text belongs to localized
   resources; summaries read the resolved state.

### Step 6: Repair the quick-access panel as a separate UI workstream

Use current-build screenshots and the existing `EdgeQuickAccessPanel` in
`RecommendationSettingsSharedComponents.kt` as the ownership boundary. Inspect the panel on narrow
and wide tablet/phone layouts before choosing values. The repair must:

1. Replace the visually cramped fixed 96dp/10sp arrangement with a stable responsive width and
   text style derived from existing theme tokens.
2. Preserve all five destinations and `toScreen()` back-stack behavior.
3. Improve centering, spacing, empty balance, selected-state contrast, and text wrapping without
   hiding the longest label.
4. Provide a clear accessible name/state for the handle and every destination.
5. Keep the 28dp handle/back-gesture safety decision unless target evidence proves it is the root
   cause; do not introduce blind swipe coordinates.
6. Add a pure layout policy test and current-build visual evidence for For You, Source Evaluation,
   Sources To Try, Management and diagnostics, narrow phone, and tablet widths.

This UI workstream must not be used as evidence that recommendation ranking changed.

### Step 7: Documentation and release reconciliation

Update the owning recommendation plan, `CURRENT_STATE.md`, `NEXT_WORK.md`, `README.md`, release
notes, decision register, change/rework register, and validation record. Document that exposure
history is local-only, not backup/sync, not Action History, and not explicit negative feedback.
Record any source capability limitations and do not claim all sources support Latest.

## Exact File Mapping

| File | Required change | Delete/move | Risk |
| --- | --- | --- | --- |
| `source-api/.../CatalogueSource.kt` | Audit only unless a capability contract defect is proven | none by default | upstream compatibility |
| `app/src/main/java/exh/recs/BrowsePersonalRecommendationsScreenModel.kt` | Integrate lane loading, exposure summaries, reranking, preview/Top Picks consistency | none | high shared-flow risk |
| `app/src/main/java/exh/recs/BrowsePersonalRecommendationsTab.kt` | Record loaded visible exposures once per stable refresh generation | none | lifecycle/recomposition risk |
| `app/src/main/java/exh/recs/RecommendationCandidateVisibilityPolicy.kt` | Preserve; only extend if a lane-aware diagnostic reason is proven necessary | none | hard-filter regression |
| `app/src/main/java/exh/recs/RecommendationCatalogueFallbackPolicy.kt` | Preserve Popular fallback; compose with new lane policy | none | source behavior |
| `app/src/main/java/exh/recs/RecommendationDiscoveryLane.kt` | add | none | serialization compatibility |
| `app/src/main/java/exh/recs/RecommendationCatalogueLanePolicy.kt` | add pure policy | none | capability semantics |
| `app/src/main/java/exh/recs/RecommendationExposurePolicy.kt` | add pure policy | none | ranking semantics |
| `app/src/main/java/exh/recs/RecommendationDisplayReranker.kt` | add pure display reorder | none | result ordering |
| `app/src/main/java/exh/recs/memory/*` | Add safe exposure store adapter; preserve candidate memory semantics | none | persistence boundary |
| `domain/src/main/java/tachiyomi/domain/taste/model/*` | add exposure model/lane types | none | domain API |
| `domain/src/main/java/tachiyomi/domain/taste/repository/*` | add exposure repository | none | ownership |
| `domain/src/main/java/tachiyomi/domain/taste/interactor/*` | add get/record/prune/clear interactors | none | DI/test coverage |
| `data/src/main/sqldelight/tachiyomi/data/recommendation_exposure.sq` | add local-only table and queries | none | migration/export |
| `data/src/main/sqldelight/tachiyomi/migrations/64.sqm` | additive migration if still next | none | schema rollback |
| `data/src/main/java/tachiyomi/data/taste/*` | add repository implementation | none | SQL mapping |
| `app/src/main/java/eu/kanade/domain/KMKDomainModule.kt` | register new repository/interactors | none | startup DI |
| `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` | add validated exposure/diversity preferences | none | settings compatibility |
| `app/src/main/java/exh/recs/RecommendationMinChapterCountPolicy.kt` | add one supported-value/default/label policy for the existing chapter filter | none | settings/pipeline parity |
| `app/src/main/java/exh/recs/settings/RecommendationsSettingsScreenModel.kt` | sanitize chapter-setting writes and new preference writes | none | preference/undo compatibility |
| `app/src/main/java/exh/recs/settings/RecommendationSettingsSharedComponents.kt` | repair shared panel layout only after visual audit | none | all panel routes |
| `app/src/main/java/exh/recs/settings/Recommendation*SettingsScreen.kt` | place controls in existing owner screen after audit | none | navigation/localization |
| `app/src/test/java/exh/recs/*` | add pure policy, lane, reranker, lifecycle, and compatibility tests | none | test completeness |
| `data/src/test/*`, `domain/src/test/*` | add schema/repository round-trip and migration tests where local patterns require | none | persistence integrity |

The migration number, settings owner, and any generated SQLDelight files must be revalidated at
implementation start. Generated files are never hand-edited.

## Required Test Matrix

### Pure policy tests

- 14-day boundary, future/negative timestamps, expiration, repeated exposure, and stable ties.
- Exposed-but-uninteracted is not dislike; explicit Not Interested/dislike remains hard behavior.
- Library/rating/tracker combinations: each interaction state is separate.
- Strong personalized result outranks novelty-only result; Latest cannot bypass hard filters.
- Latest candidate remains in output after soft penalty; output is a permutation, not a deletion.
- Source/topic quotas, per-row cap, Top Picks cap, and cross-source deduplication remain deterministic.

### Minimum-chapter conformance tests

- Supported values resolve exactly to `Off/5/10/20/50` and the default is `Off` unless the existing
  product contract is deliberately changed.
- Negative, unsupported, oversized, and malformed persisted values resolve to the documented safe
  default and never appear as an unformatted value in the UI.
- Setter validation, undo journaling, cache fingerprinting, Browse, memory merge, preview, and
  other recommendation consumers all use the same resolved threshold.
- A known count below 20 is hidden when 20 is selected; 20 and higher remain visible; unknown and
  lookup-failure counts fail open exactly as the existing contract states.
- Changing 20 to another supported value causes the next For You refresh to use the new threshold.

### Source/capability tests

- CatalogueSource with Latest succeeds.
- Latest unsupported, empty, malformed, offline, recoverable failure, and cancellation.
- Sources without Latest continue through personalized and Popular paths.
- Latest is called at the bounded budget, not once per recomposition or page without progress.
- Popular and Latest provenance remain distinct in cache/memory/diagnostics.

### Persistence tests

- Additive migration from every supported schema version.
- Upsert idempotency, batch failure isolation, cancellation rethrow, prune expiry, clear-only-exposure.
- No backup/sync/OCR/export inclusion.
- Source URL/local ID changes do not corrupt unrelated manga or taste rows.

### UI/lifecycle tests

- Exposure records only loaded visible cards once per result generation.
- Loading, empty, error, offline, route leave, recreation, process restart, and refresh behavior.
- Settings defaults, malformed preference fallback, localization, theme, accessibility semantics.
- Minimum-chapter row matches adjacent More > Settings formatting, stays readable at narrow widths,
  and uses localized title, summary, and value labels without unusually long explanatory copy.
- Quick-access panel narrow phone/tablet layout, longest label, selected state, back/scrim behavior,
  handle semantics, and no overlap with system back gesture.

### Complete validation

Use the repository's existing sequence, with JDK 17:

```text
spotlessApply or the established formatting command
spotlessCheck
:app:testDebugUnitTest
:app:assembleDebug
control-plane validation required by the current ledger
git diff --check
```

Do not claim release security closure from this feature pass. Reuse unchanged Semgrep/OSV and
documented CodeQL/MobSF limits unless a real input/environment change occurs.

## Visual Evidence Checklist

Generate screenshots from the current build only after source validation. Capture For You in loaded,
empty, loading, error/offline, and mixed personalized/Latest states; source/topic listing reorder;
settings controls; Source Evaluation provenance; and the repaired quick-access panel at narrow phone
and tablet widths. Sanitize source names, URLs, SAF paths, account identifiers, private titles, raw
logs, and local paths before any non-private use. Screenshots are supporting evidence, not a
substitute for interaction tests.

## Rollback and Recovery

- Source rollback: revert the coherent code batch and install no APK until host validation passes.
- Schema rollback: use the repository's supported additive migration policy; do not delete or reset
  the exposure table in place. A failed migration blocks the batch.
- Data rollback: clear only exposure rows through the owned clear interactor; never clear ratings,
  library, tracker, manga, candidate memory, or source preferences.
- Behavior rollback: disable Latest lane and soft reranking via validated preferences if the final
  design includes kill switches; the default fallback must remain existing personalized + Popular.
- Visual rollback: restore the shared panel layout through the same owning file, preserving route
  destinations and back-stack semantics.
- No device or database mutation is authorized by this planning packet.

## Independent Challenge Review

Required before `ready`:

- Try to make Latest displace a strongly personalized result.
- Try to cause a source with no Latest API to fail the whole For You refresh.
- Try to count one visible card repeatedly through Compose recomposition.
- Try to leak exposure history through backup, export, diagnostics, or logs.
- Try to make a soft penalty become a permanent block.
- Try to make stable ordering jitter across identical refreshes.
- Try to break the longest quick-access label or the Android back gesture.

The packet remains `decision-gated` until these challenges have evidence-backed answers and the user
explicitly authorizes implementation.

## Completion Definition

This packet is complete only when the exact implementation, tests, current-build visual evidence,
accessibility checks, privacy/storage/migration review, rollback record, and durable recommendation
records agree. `Code written`, `tests passed`, or `Latest fetched once` alone are insufficient.

## Batch L7 validation reconciliation (2026-08-09)

The real generated 63 -> 64 migration path and the tracker tri-state chain are validated. The
ScreenModel now has a production-compatible `autoLoad` seam and direct tests for tracker assembly
and settled exposure capture. The full source-search assembly, direct minimum-chapter display
validation, realised-majority output through the ScreenModel, and rendered Compose evidence remain
open as `PARTIAL_VALIDATION` or `BLOCKED_EXTERNAL` where applicable. This packet is not being marked
complete or publication-ready from host tests alone.
