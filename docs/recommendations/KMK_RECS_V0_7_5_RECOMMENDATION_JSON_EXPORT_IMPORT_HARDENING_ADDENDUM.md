# KMK-Recs v0.7.5 Recommendation JSON Export / Import Hardening Addendum

Date: 2026-06-22

Status: companion addendum to `KMK_RECS_V0_7_5_RECOMMENDATION_JSON_EXPORT_IMPORT_PLAN.md`. Claude must read this addendum together with the main v0.7.5 plan before implementation.

## Why This Addendum Exists

The main v0.7.5 plan covers the recommendation JSON export/import feature. This addendum makes the deferred-item boundaries, exception handling, and test expectations explicit so Claude does not accidentally:

- reuse stale deferred lists from older planning files,
- reimplement features that Claude already shipped,
- skip edge-case handling around bad JSON, missing sources, missing extensions, or Android state save,
- under-test a feature that touches external files and library mutation.

## Deferred Item Audit

Claude must verify `CURRENT_STATE.md`, `NEXT_WORK.md`, and the latest implementation reports before coding. This audit is based on the documented post-v0.7.4 state.

### Already Implemented And Not Part Of v0.7.5

These were once deferred in older notes but are now documented as implemented. Do not replan or reimplement them for v0.7.5:

- Alternate-title cross-extension matching.
- Favorite other versions.
- Cross-source link groups with backup/restore/sync.
- Loved Manga view.
- Loved Manga conservative duplicate grouping.
- Loved Manga installed-source-only filtering.
- Seen / already-read manga marker.
- Seen other versions.
- Source status display ordering.
- Reassessment prompt after 100 new manga ratings.
- Source Evaluation updated-extension reassessment helpers.
- Pure recommendation-fit eligibility/scoring helpers from v0.7.4.

If code inspection contradicts the docs, Claude must stop and document the mismatch before changing behavior.

### Still Deferred But Not Required For v0.7.5

These remain deferred, but they should not be implemented as part of recommendation JSON export/import unless they directly block the feature:

- Bounded recommendation-fit probe execution in `SourceEvaluationRunner`.
- `rec_fit_probe_score` storage in `source_evaluation`.
- Evidence strength string i18n.
- Manage hidden source suggestions UI.
- Hidden-source state distinct from dislike.
- Backup/restore for seen manga.
- Broader extension repo failure surfacing.
- Mid-run connectivity loss handling beyond current timeout/error behavior.
- Loved Manga sorting options beyond most-recent.
- Loved Manga live updates while the screen is open.
- Loved Manga grouping toggle backup/restore.
- Source status timestamps.
- AniList/tracker known-list cache.
- Minimum chapter count filter.
- Query-time blocked tag exclusion.
- Local Source product decision.

If one of these becomes a blocker, document the blocker and ask for user approval before widening scope.

### Related Systems To Reuse Safely

These are relevant to v0.7.5, but only as dependencies/signals:

- Cross-source link groups should be read during export/import resolution.
- Loved Manga duplicate grouping rules should guide duplicate display and import preview behavior.
- Bulk Favorite/library-add behavior should be reused or extracted so imported manga are added safely.
- Cross-extension matching and smart search can be used for "Find equivalent" when exact source resolution fails.

Reuse does not mean reimplementation.

## Exception Handling Requirements

This feature must be defensive because it handles external JSON files, missing extensions, source mismatches, extension installs, and Android file APIs.

### Export Failure Handling

Export must handle:

- document picker cancellation,
- output stream open failure,
- write failure,
- serialization failure,
- empty recommendation set,
- missing source metadata,
- manga entries with blank optional metadata,
- Top Picks partial-loading state,
- Loved Manga unresolved local manga fallback.

Expected behavior:

- do not crash,
- show a concise user-facing error/snackbar/dialog,
- leave existing recommendation state unchanged,
- log enough diagnostic detail for debugging.

### Import Failure Handling

Import must handle:

- document picker cancellation,
- input stream open failure,
- malformed JSON,
- wrong schema,
- unsupported schema version,
- oversized file,
- too many items,
- unknown/missing source IDs,
- missing extension metadata,
- installed source ID mismatch,
- unavailable extension repo,
- extension install cancellation/failure,
- network failure while resolving/installing,
- manga no longer available at imported URL,
- duplicate already in library,
- partial success where some items import and others fail.

Expected behavior:

- never crash on bad input,
- show a preview whenever any valid items can be recovered,
- classify failed items individually when possible,
- allow retry after missing extension install or connectivity recovery,
- keep unresolved items visible with clear status,
- do not add manga to library until the user confirms selected items.

### Add-To-Library Failure Handling

Adding selected manga must handle:

- duplicate manga detection,
- default category lookup failure,
- category picker cancellation,
- metadata fetch failure,
- chapter fetch failure,
- tracker binding failure,
- source missing after preview,
- user deselecting all items,
- partial import success.

Expected behavior:

- one item failure should not abort all remaining selected items unless the failure is global,
- show import summary: added / skipped / failed,
- preserve duplicate/category behavior from existing Komikku flows,
- do not write taste rows unless explicitly designed and approved.

### Extension Install / Missing Source Failure Handling

For missing sources:

- do not export or import extension APKs,
- only offer install if a matching available extension is found in configured repos,
- if install fails or is cancelled, keep affected items in `Missing source` state,
- after install completes, refresh only affected items where possible,
- if exact source remains unavailable, offer `Find equivalent`.

### State-Save Safety

Do not pass large non-primitive, non-Parcelable, or non-Serializable objects directly through Voyager screen constructors.

This app previously hit state-save crashes around cross-extension matching. The import/export screens must use safe route arguments:

- URI string or persisted lightweight ID only,
- primitive mode/action keys,
- screen model loads large data after navigation,
- no raw bundle object in the screen constructor unless proven safe.

## Expanded Test Requirements

The main v0.7.5 plan already lists core tests. Add these edge-case tests as well:

- Export handles empty lists safely.
- Export handles missing optional source metadata safely.
- Export does not start a second recommendation crawl.
- Import preview handles a mixture of ready, duplicate, missing-source, malformed, and unresolved items in the same file.
- Import preview survives source manager / extension manager failures by showing safe item states.
- Import rejects wrong schema with a clear result.
- Import rejects unsupported schema version with a clear result.
- Import rejects oversized files or excessive item counts.
- Source ID mismatch can fall back to package/source/lang metadata.
- Title-only match is never treated as confirmed duplicate.
- Cross-source link group outranks fuzzy metadata.
- Loved Manga export excludes uninstalled-source entries.
- Add-to-library handles partial failure and reports failed items.
- Missing-extension install cancellation keeps items unresolved and retryable.
- Voyager screen constructors use safe primitive arguments only.

Manual verification additions:

1. Cancel the document picker during export and import; confirm no crash.
2. Import intentionally bad JSON; confirm a clear error and no crash.
3. Import a file with one invalid item and multiple valid items; confirm valid items remain previewable.
4. Disable internet before resolving missing sources; confirm clear error/retry behavior.
5. Add selected manga where one already exists in library; confirm duplicate behavior matches Komikku expectations.
6. Confirm normal global search remains unchanged and uncapped.

Suggested focused test command:

```text
./gradlew :app:testDebugUnitTest --tests "*RecommendationBundle*" --tests "*LovedManga*" --tests "*CrossExtensionMatch*"
```

If implementation extracts shared favorite/library-add logic, also run or add focused tests around that helper.

## Documentation Requirement

The final implementation report must explicitly state:

- which deferred items were verified as already implemented,
- which deferred items remain intentionally out of scope,
- which exception paths were handled,
- which tests were added and run,
- whether import/export was tested with malformed JSON, missing sources, and duplicate manga.

