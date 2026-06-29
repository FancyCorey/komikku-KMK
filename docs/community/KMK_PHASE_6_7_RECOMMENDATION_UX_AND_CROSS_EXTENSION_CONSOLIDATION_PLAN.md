# KMK Phase 6-7 Recommendation UX, Cross-Extension, Loved Manga, And Best Version Consolidation Plan

Date: 2026-06-26

Status: planning. Implementation is not approved until the user explicitly approves this phase.

Target implementation pass: Claude Code, after Phase 0-5 outputs exist and after user approval.

Related documents:

- `docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md`
- `docs/community/KMK_COMMUNITY_READINESS_AUDIT.md`
- `docs/community/KMK_FEATURE_CLASSIFICATION_MATRIX.md` once created
- `docs/recommendations/CURRENT_STATE.md`
- `docs/recommendations/NEXT_WORK.md`
- `docs/recommendations/KMK_RECS_SOURCE_EVALUATION_COMMUNITY_HARDENING_IMPLEMENTATION.md` once created
- `AGENTS.md`


## Baseline Scope

All audits, plans, and implementation passes must evaluate the full KMK fork delta against the current/latest Komikku baseline, not only the most recent KMK change set.

Claude must treat the scope as: everything added, changed, removed, or behaviorally affected since the latest/current Komikku upstream baseline available in this repository or verified from current upstream references. This includes source code, migrations, database schema, backup/sync behavior, preferences, UI strings, settings, extension handling, docs, Gradle/dependencies, tests, generated artifacts, and release/version naming.

Before making conclusions, Claude must identify what baseline it used:

- local upstream/latest Komikku commit or branch, if available,
- current app version/build metadata in this repo,
- official/current Komikku reference if local evidence is insufficient.

If Claude cannot determine the exact upstream baseline, it must say so clearly and proceed by comparing KMK-marked and newly added fork files against the nearest local Komikku/Mihon/TachiyomiSY patterns. Do not narrow the audit to only the latest KMK version unless the user explicitly asks for that.
## Purpose

Phase 6 consolidates the everyday recommendation experience so it feels like one coherent system instead of a pile of separate experiments.

Phase 7 consolidates workflows that try to identify equivalent manga across sources:

- rating other versions,
- favoriting other versions,
- marking other versions seen/read,
- Loved Manga duplicate grouping,
- cross-source link groups,
- Best Version / chapter quality comparison,
- migration/copy confirmation,
- source quality signals.

These phases are grouped because users experience them together. A user who rates manga expects For You, Top Picks, Loved Manga, same-manga matching, and Best Version to speak the same language.

## Hard Rules

1. Do not change recommendation scoring unless the plan explicitly says so.
2. Do not auto-merge, auto-migrate, auto-favorite, or auto-mark manga without user confirmation.
3. Do not pretend same-manga matching is perfect.
4. Do not hide duplicates aggressively without explainable evidence.
5. Do not add hardcoded user-facing strings; use `KMR` in `i18n-kmk` base resources.
6. Verify current Komikku UI and settings patterns before changing screens.
7. Preserve normal global search behavior. Any caps or selection defaults apply only to bounded matching workflows.
8. Keep Source Evaluation advanced/experimental controls separate from normal daily recommendation controls.
9. Keep OCR out of this phase.
10. Keep all changes small enough to test and review.

## Main Code Areas

Claude should inspect and limit changes mainly to:

```text
app/src/main/java/exh/recs/
app/src/main/java/exh/recs/settings/
app/src/main/java/exh/recs/matching/
app/src/main/java/exh/recs/loved/
app/src/main/java/exh/recs/bestversion/
app/src/main/java/exh/recs/share/
app/src/main/java/eu/kanade/tachiyomi/ui/browse/
app/src/main/java/eu/kanade/tachiyomi/ui/manga/
domain/src/main/java/tachiyomi/domain/taste/
data/src/main/java/tachiyomi/data/taste/
i18n-kmk/src/commonMain/moko-resources/base/
app/src/test/java/exh/recs/
```

## Phase 6 Required Changes: Recommendation Core And UX

### 1. Recommendation Settings Reorganization

Problem:

Recommendation Settings now contains daily controls, advanced controls, source evaluation tools, same-manga settings, source priority, source status, and management actions. It can feel crowded and confusing.

Implementation:

- Compare current Recommendation Settings to nearby Komikku settings screens.
- Split controls into clear sections:
  - Daily recommendations
  - Ratings and known manga
  - Tags
  - Source priority
  - Same manga matching
  - Source status
  - Experimental source evaluation
  - Management
- Keep dangerous/experimental controls visually lower or behind an advanced section.
- Ensure section headings and summaries explain the difference between:
  - liking manga,
  - liking a source,
  - seen/read,
  - source quality,
  - recommendation quality.

Constraints:

- Do not remove existing controls.
- Do not reset user preferences.
- Use existing Komikku preference components where possible.

### 2. Terminology Cleanup

Problem:

The app uses overlapping ideas: Love, Like, Dislike, Favorite, Seen, Read, Known, Source Like, Source Dislike, Top Picks, Strong Fit, Worth Trying, Recommendation Quality.

Implementation:

- Create consistent wording for:
  - manga rating,
  - source preference,
  - recommendation quality,
  - source library fit,
  - seen/read marker,
  - known manga filtering,
  - Top Picks.
- Update UI strings only where wording is confusing.
- Do not change database enum names unless absolutely necessary.

Expected direction:

- Manga: Love / Like / Dislike / Seen.
- Source: Prefer source / Avoid source.
- Source Evaluation: Library fit = Strong Fit / Worth Trying / Weak / Rejected.
- Recommendation probe: Recommendation quality = Great / Good / Mixed / Weak / No matches / Error / Not checked.
- Top Picks: highest ranked recommendations across currently fetched source rows.

### 3. For You Row Ordering And Status Clarity

Implementation:

- Ensure sources with matches sort above no-match sources in settings/source status views.
- Ensure no-match sources sort above disliked/disabled sources.
- For You page itself should hide empty rows unless user enters a diagnostic/status view.
- Status messaging should make clear whether a source:
  - was not attempted,
  - was outside the attempt limit,
  - returned no matches,
  - returned results that were filtered,
  - was hidden by duplicate handling,
  - is disliked/disabled,
  - is errored.

Tests:

- Update/add pure sort/status tests.

### 4. Known Manga And Seen/Read Handling

Implementation:

- Keep disliked manga hidden from For You.
- Keep seen/read manga removable from recommendations.
- Ensure "Seen" is available as a neutral state distinct from Like/Dislike.
- Ensure "Mark other versions as seen" remains available where relevant even after marking the current manga seen, unless all linked versions are handled.
- Confirm backup/sync behavior from Phase 3 before promising persistence across devices.

Tests:

- Store/parser tests for seen keys.
- UI state tests if available through pure helpers.

### 5. Loved Manga View

Implementation:

- Ensure Loved Manga only shows manga from currently installed sources.
- Keep uninstalled-source taste rows preserved in storage, but hidden from the view.
- Group obvious duplicates conservatively.
- Use cross-source link groups as strongest grouping evidence.
- Provide a clear explanation or subtitle when grouping is applied.
- If a manual "clear duplicates" action exists, make sure it actually performs the intended grouping or explain why no changes occurred.

Duplicate grouping rule direction:

- Strong evidence:
  - existing cross-source link group,
  - exact normalized title plus matching author/artist,
  - exact normalized title plus highly similar description/intro.
- Avoid title-only merging.
- Do not merge merely because genres overlap.

Tests:

- Loved Manga source filter tests.
- Duplicate grouper tests including installed-only cases.

## Phase 7 Required Changes: Cross-Extension And Best Version

### 6. Same-Manga Matching Settings

Implementation:

- Keep normal global search uncapped.
- Apply per-source caps only to bounded workflows:
  - rating other versions,
  - seen/read other versions,
  - favorite other versions,
  - Best Version candidates.
- Provide settings for:
  - results per source: 1, 2, 5, 10,
  - preselect matches by default: on/off.
- Default should remain selected by default unless user changes it.
- If preselect is off, user manually selects true matches instead of deselecting false matches.

Tests:

- SameMangaMatchSettings clamp/default tests.
- Cross-extension selection default tests.

### 7. Cross-Extension Matching Route Safety

Implementation:

- Confirm all Voyager screens used in matching pass primitive route arguments or otherwise Android-safe state.
- Ensure no mode object causes `BadParcelableException` during state save.
- Add/keep tests for route-mode reconstruction.
- Apply same pattern to new matching flows if any still pass objects directly.

### 8. Cross-Source Link Group Management

Problem:

The app can create cross-source link groups, but users need a way to inspect or repair them if wrong matches were selected.

Implementation:

- Add or plan a simple link group management UI reachable from relevant screens:
  - manga detail,
  - Loved Manga,
  - recommendation settings advanced section.
- At minimum, allow viewing linked versions for a manga and removing incorrect links.
- Do not auto-delete taste rows when a link is removed.
- Do not auto-unfavorite/unrate linked manga unless user explicitly performs that action.

Tests:

- Link group repository/interactor tests if not already covered.
- Pure grouping tests using link groups.

### 9. Best Version Candidate Review

Implementation:

- Keep user confirmation central.
- Candidate selection screen must support deselecting wrong manga.
- Respect results-per-source and preselect settings.
- Do not use first few pages by default because source ads can skew preview.
- Default chapter should be latest read/in-progress when possible, but user can change chapter before preview.
- Sample size should remain configurable.

### 10. Best Version Preview And Cancel Safety

Implementation:

- Fullscreen preview should open when tapping a page image.
- Fullscreen preview should support pinch-to-zoom and pan.
- Tapping/closing returns to the same comparison state.
- Selecting "best version" opens migration/copy confirmation.
- Cancel must dismiss the confirmation and return to the preview screen, not freeze the workflow.
- Stale selected keys must be cleared safely.
- Copy/migrate must require explicit confirmation.

Tests:

- Pure selection/cancel policy tests.
- Manual QA for cancel, rotate/background if relevant, and returning to preview.

### 11. Source Quality Signal

Implementation:

- Keep writing source quality signal only after user selects a best version.
- Store enough detail to learn later:
  - origin source,
  - selected source,
  - manga URLs,
  - chapter number,
  - sample size,
  - sampled page URLs or safe identifiers,
  - timestamp.
- Do not use quality signal to automatically rank recommendations in this phase unless already implemented and documented.
- Clearly mark future usage as latent/deferred.

### 12. Recommendation Cache And Refresh Behavior

Implementation:

- Ensure changes to ratings, seen state, source preference, matching settings, and tag preferences invalidate or refresh only the relevant recommendation cache.
- Avoid unnecessary full recomputation.
- Keep cached source rows honest: if a setting changes, do not show stale results as if freshly scored.

Tests:

- Fingerprint/cache invalidation tests where pure.

## Documentation Updates

Claude must update:

```text
docs/recommendations/CURRENT_STATE.md
docs/recommendations/NEXT_WORK.md
docs/community/KMK_COMMUNITY_CONSOLIDATION_PHASES.md
```

Also create:

```text
docs/recommendations/KMK_RECS_RECOMMENDATION_UX_AND_CROSS_EXTENSION_CONSOLIDATION_IMPLEMENTATION.md
```

The implementation note must include:

- files changed,
- UI changes,
- behavior changes,
- settings added/changed,
- database changes if any,
- tests added/run,
- manual QA steps,
- remaining deferred items.

## Validation

Run at minimum:

```text
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
```

If SQLDelight changes are made:

```text
./gradlew :data:generateSqlDelightInterface
```

If a command cannot be run, document why.

## Acceptance Criteria

This phase is complete only if:

- Recommendation Settings is clearer and follows Komikku patterns better.
- Normal controls are visually distinct from advanced/experimental controls.
- Terminology is consistent.
- For You/source status sorting is understandable.
- Seen/read behavior is neutral and does not remove needed "other versions" actions prematurely.
- Loved Manga only displays installed-source entries.
- Duplicate grouping is conservative and explainable.
- Same-manga matching settings apply to cross-extension workflows, not normal global search.
- Cross-extension route args are Android state-save safe.
- Link groups can be inspected or a concrete later UI plan exists.
- Best Version preview/cancel/confirmation flows are safe.
- Source quality signals remain user-confirmed and non-automatic unless documented.
- Docs and tests are updated.

## Summary Claude Should Provide

Claude should report:

- which Komikku UI/settings patterns were checked,
- what UX was simplified,
- what wording changed,
- what matching behavior changed,
- whether normal global search was preserved,
- what tests passed,
- what manual QA remains,
- what remains experimental.


