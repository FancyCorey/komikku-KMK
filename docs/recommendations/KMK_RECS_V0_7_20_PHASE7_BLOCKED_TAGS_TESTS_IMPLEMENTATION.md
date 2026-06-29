# KMK-Recs v0.7.20 — Phase 7 Blocked Tags Tests + Local Source Decision

Date: 2026-06-28

Status: implemented and tested.

## Summary

Phase 7 of the deferred feature plan. The query-time blocked tag exclusion logic was already implemented in v0.7.0. This release adds the required tests and closes out the local source decision.

---

## Query-Time Blocked Tag Exclusion

### What was already in place (v0.7.0)

`GenreFilterMapper.buildSearch()` takes a `blockedGenres: List<String>` parameter. When not in `forceTextOnly` mode and blocked genres are provided, the mapper iterates the filter list looking for `Filter.Group<*>` containing `Filter.TriState` children:

- If the TriState name matches a blocked genre (by normalized name or `BUILT_IN_SYNONYMS` synonym)
- AND the TriState is currently `STATE_IGNORE` (not already set to INCLUDE by the desired-genre pass)
- → sets it to `STATE_EXCLUDE`

Post-fetch `PersonalRecommendationScorer` blocking always runs as fallback regardless of filter-push outcome.

Call site in `BrowsePersonalRecommendationsScreenModel.searchSource()`:
```kotlin
val searchParams = GenreFilterMapper.buildSearch(
    filterList,
    plan.tags,
    aliasCandidates,
    plan.forceTextOnly,
    // KMK --> v0.7.0: Phase 7 — push blocked tags as exclusion filters
    blockedGenres = profile.blockedGroups.toList(),
    // KMK <--
)
```

### What v0.7.20 added

7 unit tests in `GenreFilterMapperTest`:

| Test | What it verifies |
|---|---|
| `blocked genre triState in group is set to STATE_EXCLUDE` | Basic exclusion path works |
| `blocked genre does not downgrade a filter already set to STATE_INCLUDE` | INCLUDE wins over EXCLUDE — code guards with `child.state == STATE_IGNORE` |
| `blocked genre with no matching filter causes no crash and no mutation` | Unknown blocked genre silently skipped |
| `blocked genre skips CheckBox filters` | Only TriState has exclusion semantics; CheckBox unchanged |
| `forceTextOnly skips blocked genre filter application` | `!forceTextOnly &&` guard works |
| `built-in synonym matches blocked filter label for exclusion` | "boys love" blocks "Yaoi" filter via `BUILT_IN_SYNONYMS` |
| `empty filterList with blocked genres causes no crash` | Safety against completely empty filter list |

Test count: 23 total (7 new + 16 existing, all PASS).

---

## Local Source Decision

Local Source (id `0`) is **kept excluded** from For You source searches.

Rationale:
- Local Source does not expose a catalogue search API compatible with the recommendation query planner
- Searching Local Source like a web catalogue would require special-casing that diverges from the extension contract
- A future "Already Known / Local" synthetic row based on local DB metadata would be a separate UI surface, not a source search

No code changes required. Decision documented here and in NEXT_WORK.md.

---

## Files Changed

- `app/src/test/java/exh/recs/sources/GenreFilterMapperTest.kt` — 7 new blocked-genre tests
- `app/src/main/java/exh/recs/KmkRecsReleaseNotes.kt` — v0.7.20 entry
- Documentation updates: CURRENT_STATE.md, NEXT_WORK.md, master plan, risk register, security review

---

## Test Results

```
BUILD SUCCESSFUL
GenreFilterMapperTest — 23/23 PASSED
:app:testDebugUnitTest — all existing tests PASSED
:app:assembleDebug — BUILD SUCCESSFUL
```

## APK Naming

`Komikku-v1.13.6-kmk.7.20-debug.apk`
